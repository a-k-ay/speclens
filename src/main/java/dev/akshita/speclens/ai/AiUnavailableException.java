package dev.akshita.speclens.ai;

/** Gemini failed or returned something unusable (rate limit, outage, bad response). */
public class AiUnavailableException extends RuntimeException {

	public AiUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

}
