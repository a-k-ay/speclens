# ADR 0004: Page-bounded chunks of about 1,000 characters with 150-character overlap

- **Status:** Accepted (size and overlap to be confirmed by the evaluation set)
- **Date:** 2026-10-03

## Context

Retrieval works on chunks, not whole documents. Chunk size is a trade-off:

- Too small: a requirement loses its context ("it shall be within 24 hours": what shall?).
- Too large: one vector blends several topics, similarity drops for each of them, and
  the citation snippet stops pointing at the answer.

Every answer must cite a document **and page**, so a chunk must belong to exactly one page.

## Decision

1. Extract text **per page** (PDFBox for PDF; see below for DOCX).
2. Chunk each page separately. **Chunks never cross a page boundary.**
3. Within a page, cut at most every **1,000 characters**, preferring the last sentence end
   (`. ` `? ` `! `) in the second half of the window, then the last space, and only
   hard-cut as a last resort.
4. Consecutive chunks **overlap by about 150 characters**, aligned to a word start.
5. Spaces and tabs within a line collapse to one space and blank lines are dropped, but
   **line breaks are kept**, so table rows and list items stay on separate lines when a
   citation shows the passage. (Originally all whitespace collapsed to one space; a table
   uploaded during testing became one unreadable line.) A line break also counts as a
   word and sentence boundary for cutting.
6. Size and overlap are configuration (`speclens.chunking.*`), validated at startup.

## Why

- Requirements documents are written in short numbered statements and paragraphs.
  About 1,000 characters (200 to 250 English tokens) holds one requirement plus its
  surrounding context, well below the model's 8,192-token input limit.
- 15% overlap means a statement that straddles a cut still appears whole in one chunk.
- Page-bounded chunks make every citation exact. The cost is that a sentence running
  across a page break is split. That's acceptable, because BRDs and SOWs usually break
  pages between sections.
- Characters instead of tokens: there's no local Gemini tokenizer, and a character count
  is predictable and easy to test.

## DOCX page numbers

DOCX files don't store pages; Word computes them at render time. When Word saves a file
it records `w:lastRenderedPageBreak` markers, and SpecLens uses those when present.
Otherwise it falls back to explicit page breaks. So DOCX page numbers mean "as Word last
rendered it" and can differ if the file is opened with other fonts or margins. PDF page
numbers are exact. The demo documents are PDFs.

## Consequences

- Scanned (image-only) PDFs produce no text and are rejected with a clear message.
  OCR is a non-goal.
- If the evaluation shows misses caused by chunking, size and overlap can be retuned in
  config and the documents re-ingested.
