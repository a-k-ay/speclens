package dev.akshita.speclens.document;

import java.time.Duration;

import dev.akshita.speclens.ingest.TextExtractor;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/documents/{id}/pages/{page}/image returns that PDF page as a PNG. */
@RestController
public class PageImageController {

	private final DocumentRepository documents;
	private final PageRenderer renderer;

	public PageImageController(DocumentRepository documents, PageRenderer renderer) {
		this.documents = documents;
		this.renderer = renderer;
	}

	@GetMapping("/api/documents/{documentId}/pages/{page}/image")
	public ResponseEntity<byte[]> pageImage(@PathVariable long documentId, @PathVariable int page) {
		DocumentRepository.StoredFile file = documents.findFile(documentId)
				.orElseThrow(() -> new PreviewNotAvailableException(
						"No original file is stored for this document (it was uploaded before page previews existed)"));
		if (!TextExtractor.Format.PDF.contentType.equals(file.contentType())) {
			throw new PreviewNotAvailableException("Page previews are available for PDF files only");
		}
		return ResponseEntity.ok()
				// A document never changes after upload, so browsers may cache its pages.
				.cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
				.contentType(MediaType.IMAGE_PNG)
				.body(renderer.renderPng(file.bytes(), page));
	}

}
