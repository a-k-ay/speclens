package dev.akshita.speclens.ingest;

public class DocumentAlreadyExistsException extends RuntimeException {

	public DocumentAlreadyExistsException(String filename) {
		super("A document named '" + filename + "' already exists in this project");
	}

}
