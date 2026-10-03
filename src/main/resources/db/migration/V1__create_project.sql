-- pgvector adds the `vector` column type and the <=> (cosine distance) operator.
-- Enabled here so every environment (Docker, Testcontainers, Neon) gets it from Flyway.
CREATE EXTENSION IF NOT EXISTS vector;

-- A project is a workspace: documents and questions always belong to exactly one,
-- and every retrieval query filters by project_id so projects never see each other's data.
CREATE TABLE project (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_project_name UNIQUE (name)
);
