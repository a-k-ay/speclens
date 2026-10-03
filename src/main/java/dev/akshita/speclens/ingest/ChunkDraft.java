package dev.akshita.speclens.ingest;

/** A chunk before it has an embedding or a database id. */
public record ChunkDraft(int pageNumber, int chunkIndex, String content) {
}
