package dev.akshita.speclens.ingest;

/**
 * Thrown for uploads, new projects and deletions when speclens.upload.enabled=false: the
 * public demo is read-only and serves the preloaded sample documents.
 */
public class ReadOnlyModeException extends RuntimeException {

	public ReadOnlyModeException() {
		super("This demo is read-only: uploads, new projects and deletions are switched off. "
				+ "Try the preloaded sample documents.");
	}

}
