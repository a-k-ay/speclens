package dev.akshita.speclens.document;

import java.io.IOException;
import java.util.List;

import dev.akshita.speclens.ingest.IngestionService;
import dev.akshita.speclens.ingest.UnsupportedDocumentException;
import dev.akshita.speclens.project.ProjectNotFoundException;
import dev.akshita.speclens.project.ProjectRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/projects/{projectId}/documents")
public class DocumentController {

	private final IngestionService ingestion;
	private final DocumentRepository documents;
	private final ProjectRepository projects;

	public DocumentController(IngestionService ingestion, DocumentRepository documents, ProjectRepository projects) {
		this.ingestion = ingestion;
		this.documents = documents;
		this.projects = projects;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public DocumentSummary upload(@PathVariable long projectId, @RequestParam("file") MultipartFile file)
			throws IOException {
		if (file.isEmpty()) {
			throw new UnsupportedDocumentException("The uploaded file is empty");
		}
		return ingestion.ingest(projectId, file.getOriginalFilename(), file.getBytes());
	}

	@GetMapping
	public List<DocumentSummary> list(@PathVariable long projectId) {
		projects.findById(projectId).orElseThrow(() -> new ProjectNotFoundException(projectId));
		return documents.findByProject(projectId);
	}

}
