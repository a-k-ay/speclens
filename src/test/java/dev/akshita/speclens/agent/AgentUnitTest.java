package dev.akshita.speclens.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import dev.akshita.speclens.FakeChatModel;
import dev.akshita.speclens.retrieval.Candidate;
import dev.akshita.speclens.retrieval.RetrievedChunk;
import dev.akshita.speclens.tracker.TrackerModels.TestRun;
import dev.akshita.speclens.tracker.TrackerModels.Ticket;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClient;

/** The routing building blocks, without Spring or a database. */
class AgentUnitTest {

	// ---- IntentClassifier ----

	@Test
	void classifierMergesRegexIdsAndDropsInventedOnes() {
		FakeChatModel model = new FakeChatModel();
		// The model "extracts" one real ID, one malformed one, and misses CR-3 entirely.
		model.classifyAs(Intent.TRACEABILITY, 0.8, List.of("BR-8.1", "requirement eight"), List.of());
		IntentClassifier classifier = new IntentClassifier(ChatClient.builder(model));

		IntentClassification c = classifier.classify("Is CR-3 (which changes BR-8.1) built? See LOG-142.");

		assertThat(c.intent()).isEqualTo(Intent.TRACEABILITY);
		assertThat(c.requirementIds()).containsExactly("BR-8.1", "CR-003");
		assertThat(c.ticketIds()).containsExactly("LOG-142");
	}

	@Test
	void classifierFailureMeansDocumentPathWithZeroConfidence() {
		FakeChatModel model = new FakeChatModel();
		model.classifyRaw("not json");

		IntentClassification c = new IntentClassifier(ChatClient.builder(model)).classify("Is BR-7.4 tested?");

		assertThat(c.intent()).isEqualTo(Intent.DOC_QUESTION);
		assertThat(c.confidence()).isZero();
		assertThat(c.requirementIds()).containsExactly("BR-7.4");
	}

	// ---- RequirementFinder ----

	@Test
	void aChangeRequestExpandsToTheRequirementMostOftenMentionedWithIt() {
		// As seen with the real documents: the UAT plan page mentions CR-003 next to several BRs,
		// but only BR-8.1 keeps appearing with CR-003.
		List<RetrievedChunk> sources = List.of(
				chunk("Change Request CR-003\nChange requirement BR-8.1 to 48 hours."),
				chunk("TC-T-01 covers BR-7.1. TC-T-04 covers BR-7.4. TC-I-01 within 48 hours per CR-003 covers BR-8.1 and BR-8.2."),
				chunk("BR-6.4 Cancellation fee."));

		assertThat(RequirementFinder.find(List.of("CR-003"), "Is CR-3 built?", sources))
				.containsExactly("CR-003", "BR-8.1");
	}

	@Test
	void requirementStatementsAreFoundEvenWithoutLineBreaks() {
		// A passage stored as one long line (documents ingested before line breaks were kept).
		List<RetrievedChunk> sources = List.of(chunk("7. Tracking BR-7.1 The driver app shall send GPS every 5 minutes. "
				+ "BR-7.3 Drivers shall capture an e-POD. BR-7.4 When there is no network, the app shall store up to "
				+ "50 e-PODs offline. BR-7.5 Delivered only after the e-POD upload."));

		assertThat(RequirementFinder.find(List.of(), "Has the offline e-POD requirement passed UAT?", sources))
				.containsExactly("BR-7.4");
	}

	@Test
	void withoutIdsTheBestMatchingRequirementLineIsChosen() {
		List<RetrievedChunk> sources = List.of(chunk("""
				7. Tracking and proof of delivery
				BR-7.3 Drivers shall capture an e-POD with a photo and the recipient's signature.
				BR-7.4 When there is no mobile network, the app shall store up to 50 e-PODs offline.
				BR-7.5 A shipment shall be marked Delivered only after its e-POD has been uploaded."""));

		assertThat(RequirementFinder.find(List.of(), "Has the offline e-POD storage passed UAT?", sources))
				.containsExactly("BR-7.4");
	}

	@Test
	void noMatchingLineMeansNoRequirement() {
		assertThat(RequirementFinder.find(List.of(), "Is the weather nice?", List.of(chunk("BR-6.4 Cancellation fee."))))
				.isEmpty();
	}

	// ---- TrackerAnswerCheck ----

	private static final Map<String, Ticket> TICKETS = Map.of(
			"LOG-142", ticket("LOG-142", "Done"), "LOG-171", ticket("LOG-171", "In Progress"));
	private static final Map<String, TestRun> RUNS = Map.of("TC-I-01",
			new TestRun("TC-I-01", "Invoice", List.of("BR-8.1"), "FAIL", "2026-12-22", "QA", "LOG-190", ""));

	@Test
	void correctStatusesPerSentencePass() {
		String answer = "[LOG-142] is Done but uses 24 hours, while [LOG-171] is in progress. [TC-I-01] failed (defect LOG-190).";

		assertThat(TrackerAnswerCheck.problems(answer, TICKETS, RUNS)).isEmpty();
	}

	@Test
	void aWrongStatusForTheNamedTicketIsCaught() {
		assertThat(TrackerAnswerCheck.problems("[LOG-171] is done.", TICKETS, RUNS))
				.singleElement().asString().contains("Done");
	}

	@Test
	void aWrongTestResultIsCaught() {
		assertThat(TrackerAnswerCheck.problems("UAT [TC-I-01] passed.", TICKETS, RUNS)).isNotEmpty();
	}

	@Test
	void negatedClaimsAreCheckedTheOtherWayRound() {
		// Real Gemini output: correct, because TC-I-01 failed.
		assertThat(TrackerAnswerCheck.problems("Therefore, the requirement has not passed UAT [TC-I-01].", TICKETS, RUNS))
				.isEmpty();
		assertThat(TrackerAnswerCheck.problems("[LOG-171] is not done yet.", TICKETS, RUNS)).isEmpty();
		// Wrong: LOG-142 is Done.
		assertThat(TrackerAnswerCheck.problems("[LOG-142] isn't done.", TICKETS, RUNS)).isNotEmpty();
		// Wrong: TC-I-01 did fail.
		assertThat(TrackerAnswerCheck.problems("[TC-I-01] did not fail.", TICKETS, RUNS)).isNotEmpty();
	}

	@Test
	void ticketsAndTestsNoToolReturnedAreCaught() {
		assertThat(TrackerAnswerCheck.problems("See [LOG-999] and [TC-B-01].", TICKETS, RUNS)).hasSize(2);
	}

	@Test
	void statusWordsWithoutATicketInTheSentenceAreNotJudged() {
		// "not done yet" isn't attached to a ticket, so there is nothing to verify.
		assertThat(TrackerAnswerCheck.problems("The change is not done yet. [LOG-171] is In Progress.", TICKETS, RUNS))
				.isEmpty();
	}

	private static RetrievedChunk chunk(String text) {
		return new RetrievedChunk(new Candidate(1, 1, "doc.pdf", 1, text, 0.7), 1, null, 0.01);
	}

	private static Ticket ticket(String key, String status) {
		return new Ticket(key, "Story", "summary", status, "Sprint 9", "Dev", "High", List.of("BR-8.1"), "2027-01-01", "");
	}

}
