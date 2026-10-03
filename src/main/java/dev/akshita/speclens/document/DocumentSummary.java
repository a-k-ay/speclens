package dev.akshita.speclens.document;

import java.time.OffsetDateTime;

public record DocumentSummary(long id, long projectId, String filename, String contentType, int pageCount,
		int chunkCount, OffsetDateTime uploadedAt) {
}
