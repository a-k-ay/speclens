-- The original uploaded file, kept so a citation can show the real page (tables, layout)
-- instead of only the extracted text. A separate table keeps document listings from ever
-- loading file bytes. Postgres compresses large bytea values automatically (TOAST).
-- Documents ingested before this migration have no row here; the UI then shows text only.
CREATE TABLE document_file (
    document_id BIGINT PRIMARY KEY REFERENCES document (id) ON DELETE CASCADE,
    bytes       BYTEA  NOT NULL
);
