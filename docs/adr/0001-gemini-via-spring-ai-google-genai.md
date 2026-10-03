# ADR 0001: Call Gemini through Spring AI's native Google GenAI integration

- **Status:** Accepted
- **Date:** 2026-10-03

## Context

SpecLens needs two model capabilities: text embeddings (for retrieval) and chat
(for grounded answers and user-story generation). Gemini provides both. There are
three ways to reach it from Spring:

1. Spring AI's native Google GenAI starters (`spring-ai-starter-model-google-genai`,
   `spring-ai-starter-model-google-genai-embedding`), which use Google's official Java SDK.
2. Spring AI's OpenAI starter pointed at Gemini's OpenAI-compatible endpoint.
3. Hand-written REST calls.

## Decision

Use option 1, Spring AI 2.0.1's native Google GenAI starters, against the
**Gemini Developer API with an API key** (not Vertex AI).

- Chat model: `gemini-3.5-flash-lite`, temperature 0.1 (low, because answers should
  quote the documents, not improvise).
- Embedding model: `gemini-embedding-2` at 768 dimensions (see ADR 0003).

## Why

- The native integration exposes Gemini-specific options we need, such as
  `outputDimensionality` for embeddings. The OpenAI-compatible layer is a translation
  layer whose feature coverage lags the native API.
- The SDK sends embedding batches as `batchEmbedContents` with one request per text,
  so each chunk gets its own vector. (I checked the SDK source, because Gemini
  Embedding 2 averages multiple parts of a *single* request into one vector.)
- Spring AI gives a provider-neutral `EmbeddingModel` / `ChatClient` interface, so
  tests can swap in fakes and a future model change stays in configuration.
- An API key is the simplest credential for a free-tier demo. Vertex AI would need a
  GCP project, a service account and billing.

## Consequences

- Spring AI's auto-configuration refuses to start without an API key. The `test`
  profile sets a placeholder key, turns the real model beans off
  (`spring.ai.model.embedding.text=none`, `spring.ai.model.chat=none`) and registers a
  deterministic `FakeEmbeddingModel`. CI never calls Gemini.
- Spring AI 2.0.1 does not send a task type for Gemini embeddings, and
  `gemini-embedding-2` doesn't accept one anyway. The retrieval task is written into the
  input text instead (see ADR 0003), inside our `EmbeddingService`.
- The key lives only in the `GEMINI_API_KEY` environment variable (`.env` locally,
  Render's secret settings in production).
