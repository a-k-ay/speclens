package dev.akshita.speclens.ask;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

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

/**
 * Layer 1 of refusal: with a threshold no source can reach, every question is refused
 * before the model is called. (Runs in its own Spring context because of the property.)
 */
@IntegrationTest
@TestPropertySource(properties = "speclens.retrieval.min-similarity=0.99")
class AskThresholdIntegrationTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	FakeChatModel chatModel;

	@Test
	void questionBelowThresholdIsRefusedWithoutCallingTheModel() throws Exception {
		chatModel.reset();
		String body = mvc.post().uri("/api/projects").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Threshold test\"}")
				.exchange().getResponse().getContentAsString(StandardCharsets.UTF_8);
		long projectId = ((Number) JsonPath.read(body, "$.id")).longValue();
		assertThat(mvc.post().uri("/api/projects/{id}/documents", projectId).multipart()
				.file(new MockMultipartFile("file", "brd.pdf", "application/pdf",
						TestDocuments.pdf("The system shall generate an invoice within 24 hours."))))
				.hasStatus(HttpStatus.CREATED);

		assertThat(mvc.post().uri("/api/projects/{id}/ask", projectId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"question\": \"When is the invoice generated?\"}"))
				.hasStatusOk()
				.bodyJson()
				.hasPathSatisfying("$.answered", v -> v.assertThat().isEqualTo(false))
				.hasPathSatisfying("$.refusalReason", v -> v.assertThat().isEqualTo("NO_RELEVANT_SOURCES"));
		assertThat(chatModel.answerPrompts()).isEmpty();
	}

}
