package dev.akshita.speclens.web;

import dev.akshita.speclens.project.ProjectNotFoundException;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions into RFC 7807 problem+json responses. Extending
 * ResponseEntityExceptionHandler also covers validation errors (400) in the same format.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(ProjectNotFoundException.class)
	ProblemDetail notFound(ProjectNotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	// Unique-constraint violation from Postgres, translated by Spring JDBC.
	@ExceptionHandler(DuplicateKeyException.class)
	ProblemDetail conflict(DuplicateKeyException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "A project with that name already exists");
	}

}
