package dev.akshita.speclens.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.akshita.speclens.IntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Full stack: HTTP -> controller -> JdbcClient -> real Postgres (pgvector) in a container. */
@IntegrationTest
class ProjectApiIntegrationTest {

	@Autowired
	MockMvcTester mvc;

	@Test
	void createsProjectAndListsIt() {
		assertThat(post("""
				{"name": "Acme Logistics ERP", "description": "Fictional client"}
				"""))
				.hasStatus(HttpStatus.CREATED)
				.bodyJson().extractingPath("$.name").isEqualTo("Acme Logistics ERP");

		assertThat(mvc.get().uri("/api/projects"))
				.hasStatusOk()
				.bodyJson().extractingPath("$[*].name").asArray().contains("Acme Logistics ERP");
	}

	@Test
	void rejectsDuplicateNameWith409() {
		post("""
				{"name": "Duplicate Co"}
				""").exchange();

		assertThat(post("""
				{"name": "Duplicate Co"}
				""")).hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void rejectsBlankNameWith400() {
		assertThat(post("""
				{"name": "   "}
				""")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void unknownProjectReturns404() {
		assertThat(mvc.get().uri("/api/projects/999999")).hasStatus(HttpStatus.NOT_FOUND);
	}

	private MockMvcTester.MockMvcRequestBuilder post(String json) {
		return mvc.post().uri("/api/projects").contentType(MediaType.APPLICATION_JSON).content(json);
	}

}
