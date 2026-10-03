package dev.akshita.speclens.ingest;

import java.util.List;
import java.util.Map;

import dev.akshita.speclens.ai.AiUnavailableException;
import dev.akshita.speclens.ai.EmbeddingService;
import dev.akshita.speclens.document.DocumentRepository;
import dev.akshita.speclens.document.DocumentSummary;
import dev.akshita.speclens.project.ProjectNotFoundException;
import dev.akshita.speclens.project.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Upload pipeline: validate -> extract pages -> chunk (+ find glossary terms) -> embed -> store. */
@Service
public class IngestionService {

	private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

	private final IngestProperties properties;
	private final ProjectRepository projects;
	private final DocumentRepository documents;
	private final TextExtractor extractor;
	private final Chunker chunker;
	private final GlossaryExtractor glossaryExtractor;
	private final EmbeddingService embeddings;
	private final TransactionTemplate transaction;

	public IngestionService(IngestProperties properties, ProjectRepository projects, DocumentRepository documents,
			TextExtractor extractor, Chunker chunker, GlossaryExtractor glossaryExtractor, EmbeddingService embeddings,
			TransactionTemplate transaction) {
		this.properties = properties;
		this.projects = projects;
		this.documents = documents;
		this.extractor = extractor;
		this.chunker = chunker;
		this.glossaryExtractor = glossaryExtractor;
		this.embeddings = embeddings;
		this.transaction = transaction;
	}

	/**
	 * Used by the upload endpoint and by the demo seeder. Whether public uploads are allowed
	 * is checked by the endpoint, not here.
	 */
	public DocumentSummary ingest(long projectId, String originalFilename, byte[] bytes) {
		// 1. Cheap checks first, so a bad file never costs a Gemini call.
		projects.findById(projectId).orElseThrow(() -> new ProjectNotFoundException(projectId));
		String filename = cleanFilename(originalFilename);
		if (documents.exists(projectId, filename)) {
			throw new DocumentAlreadyExistsException(filename);
		}
		TextExtractor.Format format = extractor.detect(filename, bytes);

		// 2. Extract and chunk.
		List<PageText> pages = extractor.extract(format, bytes);
		if (pages.size() > properties.upload().maxPages()) {
			throw new UnsupportedDocumentException("Document has %d pages; the limit is %d"
					.formatted(pages.size(), properties.upload().maxPages()));
		}
		List<ChunkDraft> chunks = chunker.chunk(pages);
		Map<String, String> glossary = glossaryExtractor.extract(pages);
		if (chunks.isEmpty()) {
			throw new UnsupportedDocumentException("No text found. Scanned (image-only) PDFs are not supported");
		}

		// 3. Embed outside any transaction: a slow API call must not hold a DB connection.
		List<float[]> vectors = embed(filename, chunks);

		// 4. Store the document and all its chunks atomically.
		long documentId = transaction.execute(status -> {
			long id = documents.insertDocument(projectId, filename, format.contentType, pages.size());
			documents.insertChunks(id, projectId, chunks, vectors);
			documents.insertGlossary(id, glossary);
			return id;
		});
		log.info("Ingested '{}' into project {}: {} pages, {} chunks, {} glossary terms", filename, projectId,
				pages.size(), chunks.size(), glossary.size());
		return documents.findSummary(documentId);
	}

	private List<float[]> embed(String filename, List<ChunkDraft> chunks) {
		List<float[]> vectors;
		try {
			vectors = embeddings.embedPassages(filename, chunks.stream().map(ChunkDraft::content).toList());
		}
		catch (RuntimeException ex) {
			throw new AiUnavailableException("Embedding service failed; please try again", ex);
		}
		if (vectors.size() != chunks.size()) {
			throw new AiUnavailableException("Embedding service returned %d vectors for %d chunks"
					.formatted(vectors.size(), chunks.size()), null);
		}
		return vectors;
	}

	/** Browsers may send a full path; keep only the file name and drop control characters. */
	static String cleanFilename(String original) {
		String name = original == null ? "" : original;
		name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
		name = name.replaceAll("\\p{Cntrl}", "").strip();
		if (name.length() > 255) {
			name = name.substring(name.length() - 255);
		}
		if (name.isEmpty()) {
			throw new UnsupportedDocumentException("File name is missing");
		}
		return name;
	}

}
