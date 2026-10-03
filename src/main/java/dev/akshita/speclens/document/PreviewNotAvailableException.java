package dev.akshita.speclens.document;

/** No page image can be shown: not a PDF, uploaded before files were stored, or no such page. */
public class PreviewNotAvailableException extends RuntimeException {

	public PreviewNotAvailableException(String message) {
		super(message);
	}

}
