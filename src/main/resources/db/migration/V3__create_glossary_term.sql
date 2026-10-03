-- Abbreviations defined inside a document, e.g. "electronic proof of delivery (e-POD)".
-- A chunk that only says "e-POD" loses that definition, so definitions for abbreviations
-- that appear in the retrieved sources are added to the prompt (see docs/adr/0007).
CREATE TABLE glossary_term (
    document_id BIGINT       NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    short_form  VARCHAR(20)  NOT NULL,
    long_form   VARCHAR(200) NOT NULL,
    PRIMARY KEY (document_id, short_form)
);
