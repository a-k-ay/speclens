package dev.akshita.speclens.demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import dev.akshita.speclens.document.DocumentRepository;
import dev.akshita.speclens.ingest.IngestionService;
import dev.akshita.speclens.project.Project;
import dev.akshita.speclens.project.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Loads the fictional sample documents into the demo project at startup when
 * speclens.demo.seed=true. Idempotent: files already in the project are skipped, so a
 * restart doesn't re-embed anything (and doesn't spend Gemini quota).
 */
@Component
public class DemoSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

	private final DemoProperties properties;
	private final ProjectRepository projects;
	private final DocumentRepository documents;
	private final IngestionService ingestion;

	public DemoSeeder(DemoProperties properties, ProjectRepository projects, DocumentRepository documents,
			IngestionService ingestion) {
		this.properties = properties;
		this.projects = projects;
		this.documents = documents;
		this.ingestion = ingestion;
	}

	@Override
	public void run(ApplicationArguments args) throws IOException {
		if (properties.seed()) {
			seed();
		}
	}

	public void seed() throws IOException {
		Path dir = Path.of(properties.documentsDir());
		if (!Files.isDirectory(dir)) {
			log.warn("Demo seeding skipped: {} is not a directory", dir.toAbsolutePath());
			return;
		}
		Project project = projects.findByName(properties.projectName())
				.orElseGet(() -> projects.create(properties.projectName(),
						"Fictional logistics client used for the public demo"));

		for (Path file : sampleFiles(dir)) {
			String filename = file.getFileName().toString();
			if (documents.exists(project.id(), filename)) {
				continue;
			}
			try {
				ingestion.ingest(project.id(), filename, Files.readAllBytes(file));
			}
			catch (RuntimeException ex) {
				// One bad file (or a Gemini hiccup) shouldn't stop the app from starting.
				log.error("Demo seeding failed for {}: {}", filename, ex.getMessage());
			}
		}
		log.info("Demo project '{}' (id {}) has {} documents", project.name(), project.id(),
				documents.findByProject(project.id()).size());
	}

	private static List<Path> sampleFiles(Path dir) throws IOException {
		try (Stream<Path> files = Files.list(dir)) {
			return files.filter(Files::isRegularFile)
					.filter(f -> {
						String name = f.getFileName().toString().toLowerCase(Locale.ROOT);
						return name.endsWith(".pdf") || name.endsWith(".docx");
					})
					.sorted()
					.toList();
		}
	}

}
