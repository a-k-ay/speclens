package dev.akshita.speclens.ask;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import com.jayway.jsonpath.JsonPath;
import dev.akshita.speclens.FakeChatModel;
import dev.akshita.speclens.IntegrationTest;
import dev.akshita.speclens.TestDocuments;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Ask flow end to end: real retrieval SQL in Postgres, fake embeddings, fake chat model. */
@IntegrationTest
class AskApiIntegrationTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	FakeChatModel chatModel;

	long projectId;

	@BeforeEach
	void setUp() throws Exception {
		chatModel.reset();
		projectId = createProject();
		upload(projectId, "northwind-brd.pdf", TestDocuments.pdf(
				"Billing. The system shall generate a customer invoice within 24 hours of proof of delivery.",
				"Reporting. Managers shall see a daily dashboard of delayed shipments."));
	}

	@Test
	void answerCitesTheRetrievedDocumentAndPage() {
		chatModel.reply("Invoices are generated within 24 hours of proof of delivery [S1].");

		assertThat(ask(projectId, "When is the customer invoice generated?"))
				.hasStatusOk()
				.bodyJson()
				.hasPathSatisfying("$.answered", v -> v.assertThat().isEqualTo(true))
				.hasPathSatisfying("$.citations[0].sourceId", v -> v.assertThat().isEqualTo("S1"))
				.hasPathSatisfying("$.citations[0].documentName", v -> v.assertThat().isEqualTo("northwind-brd.pdf"))
				// Hybrid retrieval ranked the billing page first, so S1 is page 1.
				.hasPathSatisfying("$.citations[0].page", v -> v.assertThat().isEqualTo(1))
				.hasPathSatisfying("$.citations[0].passage", v -> v.assertThat().asString().contains("invoice"));
	}

	@Test
	void promptContainsRulesSourcesAndQuestion() {
		chatModel.reply("Daily dashboard [S1].");
		ask(projectId, "Which dashboard do managers see?").exchange();

		var prompt = chatModel.answerPrompts().getFirst();
		assertThat(prompt.getSystemMessage().getText()).contains("Answer ONLY from the sources");
		assertThat(prompt.getUserMessage().getText())
				.contains("<source id=\"S1\" document=\"northwind-brd.pdf\"")
				.endsWith("Question: Which dashboard do managers see?");
	}

	@Test
	void abbreviationDefinedOnAnotherPageTravelsWithTheSource() throws Exception {
		long project = createProject();
		upload(project, "glossary.pdf", TestDocuments.pdf(
				"Drivers capture an electronic proof of delivery (e-POD) in the app.",
				"Billing. An invoice is generated within 48 hours of the e-POD upload."));
		chatModel.reply("Within 48 hours [S1].");

		ask(project, "How soon after delivery is the invoice generated?").exchange();

		assertThat(chatModel.answerPrompts().getFirst().getUserMessage().getText())
				.contains("- e-POD: electronic proof of delivery (from glossary.pdf)");
	}

	@Test
	void modelRefusalIsReturnedWithoutCitations() {
		chatModel.reply("Not found in the uploaded documents.");

		assertThat(ask(projectId, "What is the penalty for late delivery?"))
				.hasStatusOk()
				.bodyJson()
				.hasPathSatisfying("$.answered", v -> v.assertThat().isEqualTo(false))
				.hasPathSatisfying("$.refusalReason", v -> v.assertThat().isEqualTo("NOT_IN_SOURCES"))
				.hasPathSatisfying("$.answer", v -> v.assertThat().isEqualTo("Not found in the uploaded documents."))
				.hasPathSatisfying("$.citations", v -> v.assertThat().asArray().isEmpty());
	}

	@Test
	void answerWithoutAnyValidCitationIsSuppressed() {
		// No marker at all, and a marker pointing at a source that doesn't exist.
		for (String reply : new String[] { "Invoices go out in 24 hours.", "Invoices go out in 24 hours [S9]." }) {
			chatModel.reply(reply);

			assertThat(ask(projectId, "When is the invoice sent?"))
					.bodyJson()
					.hasPathSatisfying("$.answered", v -> v.assertThat().isEqualTo(false))
					.hasPathSatisfying("$.refusalReason", v -> v.assertThat().isEqualTo("UNGROUNDED_ANSWER"));
		}
	}

	@Test
	void projectWithNoDocumentsRefusesWithoutCallingTheModel() throws Exception {
		long empty = createProject();

		assertThat(ask(empty, "When is the invoice sent?"))
				.bodyJson()
				.hasPathSatisfying("$.answered", v -> v.assertThat().isEqualTo(false))
				.hasPathSatisfying("$.refusalReason", v -> v.assertThat().isEqualTo("NO_RELEVANT_SOURCES"));
		assertThat(chatModel.answerPrompts()).isEmpty();
	}

	@Test
	void otherProjectsDocumentsAreNeverUsedAsSources() throws Exception {
		long other = createProject();
		upload(other, "secret.pdf", TestDocuments.pdf("Zebra pricing is confidential to the other client."));

		ask(projectId, "What is the zebra pricing?").exchange();

		assertThat(chatModel.answerPrompts().getFirst().getUserMessage().getText())
				.doesNotContain("Zebra").doesNotContain("secret.pdf");
	}

	@Test
	void blankQuestionIs400AndUnknownProjectIs404() {
		assertThat(ask(projectId, "  ")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(ask(999999, "Anything?")).hasStatus(HttpStatus.NOT_FOUND);
	}

	private MockMvcTester.MockMvcRequestBuilder ask(long project, String question) {
		return mvc.post().uri("/api/projects/{id}/ask", project)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"question\": \"" + question + "\"}");
	}

	private long createProject() throws Exception {
		String body = mvc.post().uri("/api/projects").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Ask test " + System.nanoTime() + "\"}")
				.exchange().getResponse().getContentAsString(StandardCharsets.UTF_8);
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private void upload(long project, String filename, byte[] bytes) {
		assertThat(mvc.post().uri("/api/projects/{id}/documents", project).multipart()
				.file(new MockMultipartFile("file", filename, "application/pdf", bytes)))
				.hasStatus(HttpStatus.CREATED);
	}

}
