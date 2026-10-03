package dev.akshita.speclens.project;

import java.time.OffsetDateTime;

/** A workspace that owns documents. Every retrieval is scoped to one project. */
public record Project(long id, String name, String description, OffsetDateTime createdAt) {
}
