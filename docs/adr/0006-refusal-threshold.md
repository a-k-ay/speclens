# ADR 0006: Three-layer refusal, with a similarity threshold of 0.58 chosen from the eval set

- **Status:** Accepted
- **Date:** 2026-10-03

## Context

SpecLens must say "Not found in the uploaded documents." instead of inventing an answer.
The obvious approach is a single cosine-similarity cut-off: if the best retrieved passage
isn't similar enough to the question, refuse.

The evaluation (see [EVAL.md](../EVAL.md)) measured the best-source similarity for every
golden-set question with the real Gemini embeddings:

| Questions | min | median | max |
|---|---|---|---|
| Answerable (20) | 0.639 | 0.705 | 0.782 |
| Unanswerable (7) | 0.526 | 0.635 | 0.726 |

The two ranges **overlap**. "How many defects were found during UAT?" scores 0.726 because
the UAT plan is about defects, yet it has no defect counts (UAT hasn't started). Meanwhile
answerable questions such as "Which company will send the SMS messages?" (0.639) and "Will
the driver app support Marathi in Phase 1?" (0.661) score lower. No single threshold
separates them. From the sweep in EVAL.md:

| Threshold | Answerable kept | Unanswerable blocked |
|---|---|---|
| 0.575 | 20/20 | 1/7 |
| 0.650 | 19/20 | 4/7 |
| 0.675 | 17/20 | 6/7 |
| 0.750 | 3/20 | 7/7 |

## Decision

Refuse in three layers, cheapest first. The response's `refusalReason` says which one fired.

1. **`NO_RELEVANT_SOURCES`:** if the best source's cosine similarity is below
   `speclens.retrieval.min-similarity` (**0.58**), refuse **without calling the model**.
2. **`NOT_IN_SOURCES`:** otherwise the model reads the top 5 sources under a grounding
   prompt and replies with the exact refusal sentence when they don't contain the answer.
3. **`UNGROUNDED_ANSWER`:** if the model answers without citing at least one real source
   (`[S1]` to `[S5]`), the server withholds the answer.

**How 0.58 was chosen:** the lowest best-source similarity among answerable questions
(0.639), minus a 0.05 safety margin, rounded down. Refusing a valid question is worse than
letting an off-topic one through, because layers 2 and 3 still catch it, so the threshold
is deliberately conservative.

## Results (from EVAL.md)

- Answerable questions wrongly refused by the threshold: **0/20**.
- Unanswerable questions refused: **7/7**. 1 by the threshold (the off-topic "capital of
  France", 0.526, no model call), 6 by the model.

## Why not a higher threshold

A threshold high enough to block every unanswerable question (0.75) wrongly refuses 17 of
20 valid questions. Similarity measures *topic*, not *whether the answer is present*:
a question about UAT defects is very close in topic to the UAT plan. Deciding whether the
passage actually answers the question needs reading, which is the model's job in layer 2.

## Consequences

- Clearly off-topic questions are refused fast and for free (no model tokens), which also
  limits abuse of the public demo.
- On-topic but unanswerable questions cost one model call before being refused.
- The threshold is tied to the embedding model and dimension. Changing either means
  re-running the evaluation (`./mvnw test -Peval`) and re-choosing the value.
- 20 answerable questions is a small sample. The 0.05 margin is there because the true
  minimum on unseen questions is probably a little lower than measured.
