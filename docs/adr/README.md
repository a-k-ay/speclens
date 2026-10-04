# Architecture Decision Records

Short records of decisions that shaped SpecLens: the context, what was decided, and why.

| # | Decision | Status |
|---|---|---|
| [0001](0001-gemini-via-spring-ai-google-genai.md) | Call Gemini through Spring AI's native Google GenAI integration | Accepted |
| [0002](0002-plain-jdbc-instead-of-jpa.md) | Plain SQL with JdbcClient instead of JPA | Accepted |
| [0003](0003-embedding-model-and-dimensions.md) | gemini-embedding-2 at 768 dimensions, HNSW index, asymmetric prefixes | Accepted |
| [0004](0004-page-bounded-chunking.md) | Page-bounded chunks of about 1,000 characters with 150-character overlap | Accepted |
| [0005](0005-hybrid-retrieval-with-rrf.md) | Hybrid retrieval (pgvector + full-text) merged with Reciprocal Rank Fusion | Accepted |
| [0006](0006-refusal-threshold.md) | Three-layer refusal, with a similarity threshold of 0.58 chosen from the eval set | Accepted |
| [0007](0007-document-glossary.md) | Carry abbreviation definitions from the document into the prompt | Accepted |
| [0008](0008-show-the-original-page.md) | Keep the original file and show the cited PDF page as an image | Accepted |
| [0009](0009-chat-history-in-the-browser.md) | Chat history stays in the browser; read-only public demo | Accepted |
| [0011](0011-mock-tracker-behind-http.md) | A mock project tracker, called over a real HTTP boundary | Accepted |
| [0012](0012-read-only-tools-and-failure-handling.md) | Read-only tracker tools, validated arguments, and graceful failure | Accepted |
