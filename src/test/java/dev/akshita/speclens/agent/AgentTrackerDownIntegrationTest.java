package dev.akshita.speclens.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import dev.akshita.speclens.FakeChatModel;
import dev.akshita.speclens.IntegrationTest;
import dev.akshita.speclens.TestDocuments;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** With the tracker unreachable, a traceability question falls back to the documents. */
@IntegrationTest
@TestPropertySource(properties = "speclens.tracker.base-url=http://127.0.0.1:1/rest/api/3")
class AgentTrackerDownIntegrationTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	FakeChatModel chatModel;

	@Test
	void trackerDownGivesADocumentsOnlyAnswerThatSaysSo() throws Exception {
		chatModel.reset();
		String body = mvc.post().uri("/api/projects").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Tracker down\"}").exchange().getResponse()
				.getContentAsString(StandardCharsets.UTF_8);
		long projectId = ((Number) JsonPath.read(body, "$.id")).longValue();
		mvc.post().uri("/api/projects/{id}/documents", projectId).multipart()
				.file(new MockMultipartFile("file", "cr.pdf", "application/pdf",
						TestDocuments.pdf("Change Request CR-003 changes BR-8.1 to 48 hours.")))
				.exchange();
		chatModel.classifyAs(Intent.TRACEABILITY, 0.9, List.of("CR-003"), List.of());
		chatModel.reply("CR-003 changes the invoice window to 48 hours [S1].");

		assertThat(mvc.post().uri("/api/projects/{id}/ask", projectId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"question\": \"Is CR-003 built and tested?\"}"))
				.hasStatus(HttpStatus.OK)
				.bodyJson()
				.hasPathSatisfying("$.answered", v -> v.assertThat().isEqualTo(true))
				.hasPathSatisfying("$.route", v -> v.assertThat().isEqualTo("DOCUMENTS"))
				.hasPathSatisfying("$.notice", v -> v.assertThat().asString()
						.startsWith("Live tracker data is unavailable right now."))
				.hasPathSatisfying("$.toolCalls[0].outcome", v -> v.assertThat().isEqualTo("UNAVAILABLE"));
	}

}
