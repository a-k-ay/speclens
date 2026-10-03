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

- Chat model: `gemini-3.5-flash-lite` by default (overridable with `GEMINI_CHAT_MODEL`),
  temperature 0 (answers should restate the documents, not improvise, and vary as little
  as possible between runs). See "Update" below for Flash vs Flash-Lite.
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

## Update (2026-10-03): Flash answers paraphrases better, but Flash-Lite stays the default

`gemini-3.5-flash-lite` was too literal for paraphrased business questions. Asked "How soon
after delivery must an invoice be generated?", it refused in 6 of 6 tries, even though the
top sources say "within 48 hours of the e-POD being uploaded". The documents' own wording
("How soon after the e-POD is uploaded...") was answered every time, and so was the
paraphrase when the same prompt and sources were sent to `gemini-3.5-flash`.

Prompt changes alone didn't fix it (an explicit rule to answer with the document's exact
condition still gave 0 of 3), so this is a model-capability limit, not a prompt problem.
Embeddings are unaffected.

However, the free tier allows `gemini-3.5-flash` only **20 requests per day** per project
(quota `GenerateRequestsPerDayPerProjectPerModel-FreeTier`, hit during the first evaluation
run with Flash). That is too few for a public demo or a single evaluation run. So:

- The default stays `gemini-3.5-flash-lite`, and the paraphrased question is kept in the
  golden set as a known hard case rather than reworded to pass.
- `GEMINI_CHAT_MODEL=gemini-3.5-flash` switches models without a code change, for a key on
  a paid tier.

### Final evaluation with Flash-Lite (same day)

With the document glossary (ADR 0007) and the final prompt, Flash-Lite answered the
"after delivery" question correctly in the final evaluation run, citing CR-003, the BRD and
the kickoff notes. Earlier direct probes with a slightly different set of sources refused,
so treat this question as borderline for Flash-Lite rather than fixed.

The same run also showed a limit of citation checking: asked "Will the driver app support
Marathi in Phase 1?", Flash-Lite cited the right page but wrote "deferred to Phase 1"
where the source says Phase 2. A citation proves *where* an answer came from, not that every
sentence restates it correctly. The evaluation's fact check (answer must contain "Phase 2")
is what caught it; a stronger model, or a second verification pass, would reduce such slips.
