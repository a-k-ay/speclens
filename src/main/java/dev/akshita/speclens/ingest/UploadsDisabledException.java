package dev.akshita.speclens.ingest;

/** Thrown when speclens.upload.enabled=false (the public demo serves preloaded documents only). */
public class UploadsDisabledException extends RuntimeException {

	public UploadsDisabledException() {
		super("Uploads are disabled on this demo. Try the preloaded sample documents.");
	}

}
