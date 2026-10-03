# ADR 0008: Keep the original file and show the cited PDF page as an image

- **Status:** Accepted
- **Date:** 2026-10-03

## Context

A citation showed only the extracted text of a chunk. PDFs don't store tables, just text
placed at coordinates, so a table (a bill of quantities, a rate card) came out as one long
run of words. The model coped, but a person checking the citation couldn't read it, and
checking is the whole point of a citation.

## Decision

- Store the original upload in `document_file` (`bytea`, migration V4), separate from
  `document` so listings never load file bytes.
- `GET /api/documents/{id}/pages/{page}/image` renders one PDF page to PNG with PDFBox
  (110 DPI; at most two renders at once, so a 512 MB instance isn't overwhelmed).
  Responses are cacheable for a day because documents never change after upload.
- In the UI, each PDF citation has a **"View page N as in the PDF"** button. The image is
  requested only when clicked, and clicking the image opens it full size.
- DOCX citations keep the text view: rendering Word files needs an office suite.
- Text passages keep line breaks (ADR 0004), which helps both PDF and DOCX.

## Alternatives considered

- **Table detection** (e.g. Tabula) to store tables as structured text: helps answers too,
  but table detection in PDFs is fragile and slow to get right. Possible future work.
- **Object storage** (S3, R2) instead of `bytea`: the right choice at scale, but another
  service and set of credentials for a demo whose files are a few kilobytes to 10 MB.
- **Pre-rendering every page at upload:** wastes storage on pages nobody opens.

## Consequences

- The database grows by roughly the size of each upload; Postgres compresses large values.
- Documents ingested before V4 have no stored file; their citations show text only, and
  the UI explains why if the image can't load.
- Deleting a document or project removes its file (ON DELETE CASCADE).
