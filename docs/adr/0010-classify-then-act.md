# ADR 0010: Classify the question first, then act (instead of a free-running agent)

- **Status:** Accepted
- **Date:** 2026-10-04

## Context

SpecLens v2 can answer from two systems: the documents (what was agreed) and the project
tracker (what was built and tested). Questions need different combinations:

| Question | Needs |
|---|---|
| "What does the BRD say about invoice timing?" | Documents |
| "Which tickets are blocked this sprint?" | Tracker |
| "Is the 48-hour invoice rule from CR-3 built and tested?" | Both |
| "What's the weather?" | Neither (refuse) |

The common "agent" design gives the model every capability as a tool and lets it decide
what to call, in a loop, until it decides it's done.

## Decision

**Classify, then act.**

1. **Classify:** one model call with structured output (Spring AI `.entity(IntentClassification.class)`)
   returns `{intent, confidence, requirementIds, ticketIds, reason}`. IDs a plain regex finds
   in the question are merged in; every ID is validated like a tool argument, and invented or
   malformed ones are dropped.
2. **Route** on the intent, in code:
   - `DOC_QUESTION`: the existing document pipeline, unchanged.
   - `LIVE_STATUS`: the model gets the four read-only tracker tools and **chooses the calls**
     (bounded autonomy: read-only, validated arguments, at most 6 calls).
   - `TRACEABILITY`: **SpecLens plans the calls.** It retrieves the documents, decides which
     requirements the question is about (IDs in the question; a change request expands to the
     BR it changes; otherwise the requirement statement that best matches the question), then
     fetches tickets and UAT results for each and gives the model both the passages and the
     tracker data to write one answer citing pages `[S1]` and tickets `[LOG-142]`.
   - `OUT_OF_SCOPE`: refused without further calls.
3. **Safe defaults:** confidence below 0.6, or a classifier failure, uses the document path
   with its own refusal layers. If the tracker is down, the answer falls back to the
   documents and says live data is unavailable.
4. **Verify before showing:** every ticket key and test id in an answer must have come back
   from a tool, and in each sentence that names a ticket, status words must match that
   ticket's real status (negations such as "has not passed" are checked the other way round).
   Otherwise the answer is withheld (`UNVERIFIED_TRACKER_DATA`).
5. **Visible:** the response carries the intent, confidence, route, every tool call with its
   outcome, and the tracker references, so the UI can show how a question was handled.

## Why not let the model pick tools freely

- **Predictable cost and latency:** a classification plus a fixed number of calls, instead
  of an open-ended loop on a free-tier quota.
- **Testable and measurable:** routing is one labelled decision per question, which an
  evaluation set can score (intent accuracy, correct tools); a free agent's path varies run
  to run.
- **The document path stays exactly as evaluated:** document questions never enter a
  tool loop, so the existing golden-set numbers still describe them.
- **Traceability needs a plan, not exploration:** "find the requirement, then its tickets,
  then its tests" is always the same sequence; writing it in code makes it reliable.
- **Autonomy where it helps:** live-status questions vary ("blocked this sprint", "who owns
  LOG-142"), so there the model chooses among read-only tools.

## Found while testing with the real model

- Gemini cites grouped sources as `[S1, S2]`; the citation parser and UI accept grouped markers.
- A UAT plan page mentions a change request next to many requirements; the CR now expands to
  the requirement mentioned with it most often, not all of them.
- "Has not passed UAT" was wrongly flagged as an invented PASS until negations were handled.

## Consequences

- One extra model call per question (classification).
- A misclassified question takes the wrong route. Low confidence falls back to documents,
  and routing accuracy is measured by the routing evaluation set.
- Questions that need a *sequence* the router doesn't know (e.g. "compare sprint 8 and 9
  velocity") are out of scope for now.
