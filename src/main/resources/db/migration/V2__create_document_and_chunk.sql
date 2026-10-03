-- One row per uploaded file.
CREATE TABLE document (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id   BIGINT       NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    filename     VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    page_count   INT          NOT NULL,
    uploaded_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Same file name twice in one project would make citations ambiguous.
    CONSTRAINT uq_document_project_filename UNIQUE (project_id, filename)
);

-- One row per retrievable passage. A chunk never spans two pages, so
-- page_number is exactly the page a citation points to.
CREATE TABLE chunk (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_id BIGINT      NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    -- Denormalised from document so retrieval filters by project without a join.
    project_id  BIGINT      NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    page_number INT         NOT NULL,
    chunk_index INT         NOT NULL,
    content     TEXT        NOT NULL,
    -- gemini-embedding-2 truncated to 768 dims (see docs/adr/0003).
    embedding   vector(768) NOT NULL,
    -- Postgres keeps this in sync with content automatically; used for keyword search.
    content_tsv tsvector    GENERATED ALWAYS AS (to_tsvector('english', content)) STORED,
    CONSTRAINT uq_chunk_document_index UNIQUE (document_id, chunk_index)
);

CREATE INDEX idx_chunk_project ON chunk (project_id);

-- Approximate nearest-neighbour index for cosine distance (the <=> operator).
CREATE INDEX idx_chunk_embedding_hnsw ON chunk USING hnsw (embedding vector_cosine_ops);

-- Inverted index for full-text search (@@ with websearch_to_tsquery).
CREATE INDEX idx_chunk_content_tsv ON chunk USING gin (content_tsv);
