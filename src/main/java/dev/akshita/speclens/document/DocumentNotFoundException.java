package dev.akshita.speclens.document;

public class DocumentNotFoundException extends RuntimeException {

	public DocumentNotFoundException(long id) {
		super("Document " + id + " not found in this project");
	}

}
