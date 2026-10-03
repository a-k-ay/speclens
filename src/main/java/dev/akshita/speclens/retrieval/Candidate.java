package dev.akshita.speclens.retrieval;

/**
 * A chunk returned by one search (vector or keyword). {@code similarity} is always the
 * cosine similarity to the question, even for keyword hits, so later steps (the refusal
 * threshold) can compare every candidate on the same scale.
 */
public record Candidate(long chunkId, long documentId, String documentName, int pageNumber, String content,
		double similarity) {
}
