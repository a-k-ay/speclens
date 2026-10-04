package dev.akshita.speclens.tracker;

/** The tracker could not be reached, timed out, or answered with a server error. */
public class TrackerUnavailableException extends RuntimeException {

	public TrackerUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

}
