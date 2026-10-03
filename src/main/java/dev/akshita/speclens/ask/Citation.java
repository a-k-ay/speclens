package dev.akshita.speclens.ask;

/**
 * One source the answer relies on.
 *
 * @param sourceId the marker used in the answer text, e.g. "S1"
 * @param passage the full chunk text, so the UI can show exactly what was used
 * @param similarity cosine similarity between the question and this passage
 */
public record Citation(String sourceId, long documentId, String documentName, int page, String passage,
		double similarity) {
}
