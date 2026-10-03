package dev.akshita.speclens.demo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.akshita.speclens.IntegrationTest;
import dev.akshita.speclens.TestDocuments;
import dev.akshita.speclens.document.DocumentRepository;
import dev.akshita.speclens.document.DocumentSummary;
import dev.akshita.speclens.document.GlossaryTerm;
import dev.akshita.speclens.project.ProjectRepository;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The public demo: uploads switched off, sample documents seeded at startup. */
@IntegrationTest
@TestPropertySource(properties = { "speclens.upload.enabled=false", "speclens.upload.max-pages=60" })
class DemoModeIntegrationTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	DemoSeeder seeder;

	@Autowired
	DemoProperties demo;

	@Autowired
	ProjectRepository projects;

	@Autowired
	DocumentRepository documents;

	@Test
	void seederLoadsEverySampleOnceEvenWithUploadsDisabled() throws Exception {
		seeder.seed();
		seeder.seed(); // a restart must not duplicate anything

		long projectId = projects.findByName(demo.projectName()).orElseThrow().id();
		assertThat(documents.findByProject(projectId))
				.extracting(DocumentSummary::filename)
				.hasSize(6)
				.contains("Northwind_BRD_v1.2.pdf", "Northwind_Kickoff_Meeting_Notes.docx");
		assertThat(documents.findGlossary(projectId))
				.extracting(GlossaryTerm::shortForm)
				.contains("e-POD", "UAT");
	}

	@Test
	void publicUploadIsRefusedWith403() {
		long projectId = projects.create("Demo upload test", null).id();

		assertThat(mvc.post().uri("/api/projects/{id}/documents", projectId).multipart()
				.file(new MockMultipartFile("file", "a.pdf", "application/pdf", TestDocuments.pdf("x"))))
				.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.get().uri("/api/config"))
				.bodyJson().hasPathSatisfying("$.uploadsEnabled", v -> v.assertThat().isEqualTo(false));
	}

}
