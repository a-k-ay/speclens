package dev.akshita.speclens.retrieval;

/**
 * A chunk after Reciprocal Rank Fusion. Ranks are 1-based; null means the chunk was not
 * in that search's results.
 */
public record RetrievedChunk(Candidate chunk, Integer vectorRank, Integer keywordRank, double rrfScore) {
}
