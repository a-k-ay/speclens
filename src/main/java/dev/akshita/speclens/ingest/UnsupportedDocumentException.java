package dev.akshita.speclens.ingest;

/** The file can't be ingested: wrong type, corrupt, encrypted, too long or has no text. */
public class UnsupportedDocumentException extends RuntimeException {

	public UnsupportedDocumentException(String message) {
		super(message);
	}

	public UnsupportedDocumentException(String message, Throwable cause) {
		super(message, cause);
	}

}
