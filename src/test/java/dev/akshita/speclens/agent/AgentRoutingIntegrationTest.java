package dev.akshita.speclens.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import dev.akshita.speclens.FakeChatModel;
import dev.akshita.speclens.FakeChatModel.ToolStep;
import dev.akshita.speclens.HttpIntegrationTest;
import dev.akshita.speclens.TestDocuments;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Classify-then-act end to end: real retrieval in Postgres, real tracker calls over HTTP to
 * the mock tracker, fake models choosing the intent and the reply.
 */
@HttpIntegrationTest
class AgentRoutingIntegrationTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	FakeChatModel chatModel;

	long projectId;

	@BeforeEach
	void setUp() throws Exception {
		chatModel.reset();
		String body = mvc.post().uri("/api/projects").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Agent test " + System.nanoTime() + "\"}")
				.exchange().getResponse().getContentAsString(StandardCharsets.UTF_8);
		projectId = ((Number) JsonPath.read(body, "$.id")).longValue();
		assertThat(mvc.post().uri("/api/projects/{id}/documents", projectId).multipart()
				.file(new MockMultipartFile("file", "cr-003.pdf", "application/pdf", TestDocuments.pdf(
						"Change Request CR-003. Change requirement BR-8.1 so the invoice is generated within "
								+ "48 hours of the e-POD upload, after finance review."))))
				.hasStatus(HttpStatus.CREATED);
	}

	@Test
	void documentQuestionsUseTheDocumentRouteWithNoToolCalls() {
		chatModel.classifyAs(Intent.DOC_QUESTION, 0.9, List.of(), List.of());
		chatModel.reply("Within 48 hours [S1].");

		assertThat(ask("When must the invoice be generated?")).bodyJson()
				.hasPathSatisfying("$.route", v -> v.assertThat().isEqualTo("DOCUMENTS"))
				.hasPathSatisfying("$.intent", v -> v.assertThat().isEqualTo("DOC_QUESTION"))
				.hasPathSatisfying("$.answered", v -> v.assertThat().isEqualTo(true))
				.hasPathSatisfying("$.toolCalls", v -> v.assertThat().asArray().isEmpty());
	}

	@Test
	void lowConfidenceFallsBackToTheDocumentRoute() {
		chatModel.classifyAs(Intent.LIVE_STATUS, 0.3, List.of(), List.of());
		chatModel.reply("Within 48 hours [S1].");

		assertThat(ask("invoice thing?")).bodyJson()
				.hasPathSatisfying("$.route", v -> v.assertThat().isEqualTo("DOCUMENTS"))
				.hasPathSatisfying("$.routedByFallback", v -> v.assertThat().isEqualTo(true))
				.hasPathSatisfying("$.toolCalls", v -> v.assertThat().asArray().isEmpty());
	}

	@Test
	void anUnreadableClassifierReplyFallsBackToTheDocumentRoute() {
		chatModel.classifyRaw("I think this is probably about invoices?");
		chatModel.reply("Within 48 hours [S1].");

		assertThat(ask("When must the invoice be generated?")).hasStatusOk().bodyJson()
				.hasPathSatisfying("$.route", v -> v.assertThat().isEqualTo("DOCUMENTS"))
				.hasPathSatisfying("$.routedByFallback", v -> v.assertThat().isEqualTo(true));
	}

	@Test
	void outOfScopeIsRefusedWithoutAnAnswerCall() {
		chatModel.classifyAs(Intent.OUT_OF_SCOPE, 0.95, List.of(), List.of());

		assertThat(ask("What's the weather in Pune?")).bodyJson()
				.hasPathSatisfying("$.answered", v -> v.assertThat().isEqualTo(false))
				.hasPathSatisfying("$.refusalReason", v -> v.assertThat().isEqualTo("OUT_OF_SCOPE"))
				.hasPathSatisfying("$.route", v -> v.assertThat().isEqualTo("NONE"));
		assertThat(chatModel.answerPrompts()).isEmpty();
	}

	@Test
	void liveStatusLetsTheModelCallTrackerToolsAndCitesTheTicket() {
		chatModel.classifyAs(Intent.LIVE_STATUS, 0.9, List.of(), List.of("LOG-142"));
		chatModel.callTools(new ToolStep("getTicket", "{\"ticketId\": \"LOG-142\"}"));
		chatModel.reply("[LOG-142] is Done: it generates the invoice within 24 hours of the e-POD upload.");

		assertThat(ask("What's the status of LOG-142?")).bodyJson()
				.hasPathSatisfying("$.answered", v -> v.assertThat().isEqualTo(true))
				.hasPathSatisfying("$.route", v -> v.assertThat().isEqualTo("TRACKER"))
				.hasPathSatisfying("$.toolCalls[0].tool", v -> v.assertThat().isEqualTo("getTicket"))
				.hasPathSatisfying("$.toolCalls[0].outcome", v -> v.assertThat().isEqualTo("OK"))
				.hasPathSatisfying("$.trackerRefs", v -> v.assertThat().asArray().containsExactly("LOG-142"));
	}

	@Test
	void aStatusNoToolReturnedIsWithheld() {
		chatModel.classifyAs(Intent.LIVE_STATUS, 0.9, List.of(), List.of("LOG-142"));
		chatModel.callTools(new ToolStep("getTicket", "{\"ticketId\": \"LOG-142\"}"));
		chatModel.reply("[LOG-142] is Blocked.");

		assertThat(ask("What's the status of LOG-142?")).bodyJson()
				.hasPathSatisfying("$.answered", v -> v.assertThat().isEqualTo(false))
				.hasPathSatisfying("$.refusalReason", v -> v.assertThat().isEqualTo("UNVERIFIED_TRACKER_DATA"));
	}

	@Test
	void aTicketNoToolReturnedIsWithheld() {
		chatModel.classifyAs(Intent.LIVE_STATUS, 0.9, List.of(), List.of("LOG-142"));
		chatModel.callTools(new ToolStep("getTicket", "{\"ticketId\": \"LOG-142\"}"));
		chatModel.reply("[LOG-171] is In Progress.");

		assertThat(ask("What's the status of LOG-142?")).bodyJson()
				.hasPathSatisfying("$.refusalReason", v -> v.assertThat().isEqualTo("UNVERIFIED_TRACKER_DATA"));
	}

	@Test
	void traceabilityPlansTrackerCallsAndCombinesDocumentsWithTrackerData() {
		chatModel.classifyAs(Intent.TRACEABILITY, 0.9, List.of("CR-003"), List.of());
		chatModel.reply("CR-003 moved invoicing to 48 hours [S1]. [LOG-142] is Done but implements the old 24-hour rule, "
				+ "while [LOG-171] is In Progress. UAT [TC-I-01] failed.");

		assertThat(ask("Is the 48-hour invoice rule from CR-3 built and tested?")).bodyJson()
				.hasPathSatisfying("$.answered", v -> v.assertThat().isEqualTo(true))
				.hasPathSatisfying("$.route", v -> v.assertThat().isEqualTo("DOCUMENTS_AND_TRACKER"))
				.hasPathSatisfying("$.citations[0].sourceId", v -> v.assertThat().isEqualTo("S1"))
				// CR-003 from the question, plus BR-8.1 because the CR's own page says it changes BR-8.1.
				.hasPathSatisfying("$.toolCalls[*].arguments", v -> v.assertThat().asArray().containsExactly(
						"requirementId=CR-003", "requirementId=CR-003", "requirementId=BR-8.1", "requirementId=BR-8.1"))
				.hasPathSatisfying("$.trackerRefs", v -> v.assertThat().asArray()
						.containsExactly("LOG-142", "LOG-171", "TC-I-01"));

		String prompt = chatModel.answerPrompts().getFirst().getUserMessage().getText();
		assertThat(prompt).contains("<source id=\"S1\"").contains("<tracker>")
				.contains("Ticket LOG-142 (Story) status=Done").contains("UAT TC-I-01").contains("result=FAIL");
	}

	@Test
	void traceabilityCitingAStatusTheTrackerDidNotReturnIsWithheld() {
		chatModel.classifyAs(Intent.TRACEABILITY, 0.9, List.of("CR-003"), List.of());
		chatModel.reply("CR-003 needs 48 hours [S1]. [LOG-171] is Done and [TC-I-01] passed.");

		assertThat(ask("Is CR-003 built and tested?")).bodyJson()
				.hasPathSatisfying("$.answered", v -> v.assertThat().isEqualTo(false))
				.hasPathSatisfying("$.refusalReason", v -> v.assertThat().isEqualTo("UNVERIFIED_TRACKER_DATA"));
	}

	private MockMvcTester.MockMvcRequestBuilder ask(String question) {
		return mvc.post().uri("/api/projects/{id}/ask", projectId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"question\": \"" + question + "\"}");
	}

}
