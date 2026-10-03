package dev.akshita.speclens.web;

import dev.akshita.speclens.ai.AiUnavailableException;
import dev.akshita.speclens.ingest.DocumentAlreadyExistsException;
import dev.akshita.speclens.ingest.UnsupportedDocumentException;
import dev.akshita.speclens.ingest.UploadsDisabledException;
import dev.akshita.speclens.project.ProjectNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions into RFC 7807 problem+json responses. Extending
 * ResponseEntityExceptionHandler also covers validation errors (400) in the same format.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(ProjectNotFoundException.class)
	ProblemDetail notFound(ProjectNotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(DocumentAlreadyExistsException.class)
	ProblemDetail documentExists(DocumentAlreadyExistsException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
	}

	// Unique-constraint violation from Postgres, translated by Spring JDBC.
	@ExceptionHandler(DuplicateKeyException.class)
	ProblemDetail conflict(DuplicateKeyException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "That name is already taken");
	}

	@ExceptionHandler(UnsupportedDocumentException.class)
	ProblemDetail unsupported(UnsupportedDocumentException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	@ExceptionHandler(UploadsDisabledException.class)
	ProblemDetail uploadsDisabled(UploadsDisabledException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
	}

	@ExceptionHandler(AiUnavailableException.class)
	ProblemDetail aiUnavailable(AiUnavailableException ex) {
		log.warn("AI call failed: {}", ex.getMessage(), ex.getCause());
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
	}

	@Override
	protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, "File is larger than 10 MB");
		return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(body);
	}

}
