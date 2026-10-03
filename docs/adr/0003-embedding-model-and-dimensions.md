# ADR 0003: gemini-embedding-2 at 768 dimensions, HNSW index, asymmetric prefixes

- **Status:** Accepted
- **Date:** 2026-10-03

## Context

Each chunk is stored with an embedding in a pgvector column, and the column dimension
is fixed in the schema. `gemini-embedding-2` returns 3,072 dimensions by default and
supports any size from 128 to 3,072 through `outputDimensionality`, with 768, 1,536 and
3,072 recommended. Smaller outputs are renormalised by the API automatically.

pgvector's HNSW index supports at most 2,000 dimensions for the `vector` type.

## Decision

- Model `gemini-embedding-2`, `outputDimensionality = 768`, column `vector(768)`.
- HNSW index with `vector_cosine_ops`; similarity is cosine (`1 - (a <=> b)`).
- Asymmetric input format recommended by Google for retrieval:
  - passages: `title: <document name> | text: <chunk>`
  - questions: `task: question answering | query: <question>`

## Why

- **768, not 3,072:** 3,072 dimensions can't use an HNSW index on `vector`. It could use
  `halfvec`, but that adds complexity for no measured gain. 768 floats is about 3 KB per
  chunk instead of 12 KB, which matters on Neon's free storage. The model is trained so
  that the first dimensions carry most of the meaning (Matryoshka-style), so the quality
  loss from truncating is small. The evaluation set (Feature 6) measures retrieval
  quality at this setting.
- **HNSW over IVFFlat:** HNSW needs no training step, so it can be built on an empty
  table and stays accurate as rows are added. IVFFlat needs data first to pick its
  lists, and its recall drops as data changes unless it is rebuilt.
- **Cosine:** Gemini vectors are normalised, so cosine and dot product rank results the
  same way. Cosine scores (0 to 1 for related text) are easier to reason about when
  choosing the refusal threshold.
- **Prefixes:** the model has no `task_type` parameter. Google's guidance is to write
  the task into the text, and using the training format on both sides improves matching
  a short question to a longer passage. The document title gives each chunk context.

## Consequences

- Changing the model or dimension requires a migration and re-embedding every chunk.
  Vectors from different models can't be compared.
- With a `project_id` filter, an approximate index can return fewer than *k* rows if
  most nearest neighbours belong to other projects. At demo scale Postgres will often
  choose an exact scan anyway. If it becomes a problem, pgvector 0.8's iterative index
  scans (`SET hnsw.iterative_scan = relaxed_order`) fix it.
