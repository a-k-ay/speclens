# ADR 0007: Carry abbreviation definitions from the document into the prompt

- **Status:** Accepted
- **Date:** 2026-10-03

## Context

Chunking cuts a document into independent passages, and a passage loses context that
lives elsewhere in the same document. In the sample BRD, page 4 defines "electronic proof
of delivery (e-POD)", while page 5 only says "within 24 hours of the e-POD being
uploaded". A question about invoicing "after delivery" retrieves page 5 but not page 4,
so the model sees "e-POD" with no definition.

Requirements documents are full of abbreviations (UAT, SOW, PO, SLA, e-POD) that are
defined once and then used everywhere.

## Decision

1. At upload, extract abbreviation definitions of the form `long form (SHORT)` with the
   Schwartz-Hearst algorithm (Schwartz and Hearst, 2003). Store them per document in
   `glossary_term`.
2. A short form must contain at least two capital letters, so ordinary words in brackets,
   such as "(Northwind)", are ignored.
3. At question time, add the definitions whose abbreviation appears (as a whole token) in
   the question or a retrieved source to the prompt, under "Definitions found in the
   documents", each labelled with the document it came from.
4. The system prompt says abbreviations are explained there; general knowledge may only
   be used to understand terms, never to supply facts.

## Why this approach

- **Grounded:** the definition comes from the client's own document, not the model's
  general knowledge.
- **Cheap and deterministic:** a regex pass at upload, a small indexed query per question,
  no extra model call.
- **Small prompts:** only definitions relevant to the retrieved sources are included.

## Alternatives considered

- **Add neighbouring chunks or the whole page set:** more tokens for every question, and
  the defining page can be far away.
- **Contextual retrieval** (a model writes a context summary for every chunk at upload):
  stronger in general, but one model call per chunk at upload. Worth revisiting for larger
  corpora.
- **Rely on general knowledge:** not grounded. "POD" means different things in different
  domains.

## Consequences

- Definitions written in other forms ("e-POD means...", glossary tables) are not detected.
  Covering those is a possible extension.
- On its own, the glossary did not fix the "after delivery" question with
  `gemini-3.5-flash-lite` (see ADR 0001); it is a context improvement, not a substitute
  for a capable model.
