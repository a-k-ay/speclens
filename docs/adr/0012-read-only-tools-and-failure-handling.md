# ADR 0012: Read-only tracker tools, validated arguments, and graceful failure

- **Status:** Accepted
- **Date:** 2026-10-04

## Context

With tool calling, the model chooses which function to call and with what arguments. That
is powerful and risky: a model can invent arguments ("all tickets", "LOG-1; DROP"), loop
on calls, or quote a status no tool returned. The tracker can also be slow or down.

## Decision

1. **Four read-only tools** (Spring AI `@Tool`): `getTicket`, `getTicketsForRequirement`,
   `searchTickets`, `getUatResults`. Nothing can create, update or delete. Write actions
   are a non-goal.
2. **Validate every argument before any HTTP call** (`TrackerIds`): ticket keys must match
   `LOG-<digits>`, requirement IDs `BR-x.y`, `NFR-x` or `CR-nnn`, statuses one of five
   values, sprints `Sprint <n>` or "current". Forms people type are normalised (`cr-3` →
   `CR-003`). Anything else is rejected with a reason the model can read.
3. **Failures are data, not exceptions.** A tool returns `{ error: ... }` instead of
   throwing: `Invalid argument: ...` (REJECTED), or `TRACKER_UNAVAILABLE: live tracker data
   is unavailable right now` after a timeout, connection failure or 5xx (UNAVAILABLE).
   A missing ticket is a normal NOT_FOUND result, and an empty list means "no ticket exists".
4. **Timeouts:** 2 s to connect, 3 s to read.
5. **At most 6 tool calls per question**, then calls are refused.
6. **Every call is recorded** (tool, arguments, outcome, short result, duration) on a
   per-question `TrackerTools` instance, for the UI's "Tool calls" list and the routing
   evaluation.

## Why

- **Least privilege:** answering questions never needs to change the tracker, so the tools
  can't. Even a prompt-injected model has nothing harmful to call.
- **Validation in code, not in the prompt:** a prompt can ask the model to be careful, but
  only code guarantees it. Strict patterns also keep the model from probing arbitrary URLs
  or queries.
- **Readable errors let the model recover** (e.g. retry with `BR-8.1` instead of `8.1`) and
  let the answer say honestly that live data is missing, instead of the whole request
  failing with a 500.
- **The call log** makes the agent's behaviour visible and measurable rather than a black box.

## Consequences

- If the tracker is down, the answer falls back to the documents and says live data is
  unavailable (wired up with routing, ADR 0010).
- The ID patterns encode this project's conventions (`LOG-` keys); another tracker needs its
  own patterns.
- The call cap can cut off a legitimate long investigation; 6 is enough for every
  traceability question in the evaluation set.
