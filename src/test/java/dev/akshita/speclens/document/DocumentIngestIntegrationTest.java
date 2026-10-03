package dev.akshita.speclens.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import dev.akshita.speclens.IntegrationTest;
import dev.akshita.speclens.TestDocuments;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Upload -> extract -> chunk -> (fake) embed -> Postgres, checked through HTTP and SQL. */
@IntegrationTest
class DocumentIngestIntegrationTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	long projectId;

	@BeforeEach
	void createProject() throws Exception {
		String body = mvc.post().uri("/api/projects").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Ingest test " + System.nanoTime() + "\"}")
				.exchange().getResponse().getContentAsString(StandardCharsets.UTF_8);
		projectId = ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	@Test
	void uploadedPdfIsStoredAsPageAwareChunksWithEmbeddingsAndTsvector() {
		byte[] pdf = TestDocuments.pdf(
				"Northwind Freight BRD. The system shall generate an invoice within 24 hours of delivery.",
				"Reporting. Managers shall see a daily dashboard of delayed shipments.");

		assertThat(upload("northwind-brd.pdf", pdf))
				.hasStatus(HttpStatus.CREATED)
				.bodyJson()
				.hasPathSatisfying("$.pageCount", v -> v.assertThat().isEqualTo(2))
				.hasPathSatisfying("$.chunkCount", v -> v.assertThat().isEqualTo(2))
				.hasPathSatisfying("$.contentType", v -> v.assertThat().isEqualTo("application/pdf"));

		List<Integer> pages = jdbc.sql("""
				SELECT c.page_number FROM chunk c JOIN document d ON d.id = c.document_id
				WHERE d.project_id = :p ORDER BY c.chunk_index
				""").param("p", projectId).query(Integer.class).list();
		assertThat(pages).containsExactly(1, 2);

		Integer dims = jdbc.sql("SELECT vector_dims(embedding) FROM chunk WHERE project_id = :p LIMIT 1")
				.param("p", projectId).query(Integer.class).single();
		assertThat(dims).isEqualTo(768);

		// The generated tsvector column makes the text keyword-searchable with stemming
		// ("invoices" matches "invoice").
		Integer page = jdbc.sql("""
				SELECT page_number FROM chunk
				WHERE project_id = :p AND content_tsv @@ websearch_to_tsquery('english', 'invoices')
				""").param("p", projectId).query(Integer.class).single();
		assertThat(page).isEqualTo(1);
	}

	@Test
	void documentsAreListedPerProject() {
		upload("sow.pdf", TestDocuments.pdf("Statement of work.")).exchange();

		assertThat(mvc.get().uri("/api/projects/{id}/documents", projectId))
				.hasStatusOk()
				.bodyJson().extractingPath("$[*].filename").asArray().containsExactly("sow.pdf");
	}

	@Test
	void sameFilenameTwiceInOneProjectIs409() {
		byte[] pdf = TestDocuments.pdf("Meeting notes.");
		upload("notes.pdf", pdf).exchange();

		assertThat(upload("notes.pdf", pdf)).hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void fileThatIsNotReallyAPdfIs400() {
		assertThat(upload("brd.pdf", "MZ not a pdf".getBytes(StandardCharsets.US_ASCII)))
				.hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void tooManyPagesIs400() {
		// The test profile sets speclens.upload.max-pages=5.
		byte[] sixPages = TestDocuments.pdf("1", "2", "3", "4", "5", "6");

		assertThat(upload("long.pdf", sixPages)).hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson().extractingPath("$.detail").asString().contains("limit is 5");
	}

	@Test
	void uploadToUnknownProjectIs404() {
		assertThat(mvc.post().uri("/api/projects/999999/documents")
				.multipart().file(new MockMultipartFile("file", "a.pdf", "application/pdf", TestDocuments.pdf("x"))))
				.hasStatus(HttpStatus.NOT_FOUND);
	}

	private MockMvcTester.MockMultipartMvcRequestBuilder upload(String filename, byte[] bytes) {
		return mvc.post().uri("/api/projects/{id}/documents", projectId)
				.multipart()
				.file(new MockMultipartFile("file", filename, "application/octet-stream", bytes));
	}

}
