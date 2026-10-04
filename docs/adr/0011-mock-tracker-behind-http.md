# ADR 0011: A mock project tracker, called over a real HTTP boundary

- **Status:** Accepted
- **Date:** 2026-10-04

## Context

SpecLens v2 answers traceability questions: is a requirement agreed in the documents
actually built and tested? That needs a second, live system: a project tracker such as
Jira. A real Jira needs an account, credentials and a project, which a public demo can't
expose, and fictional demo data has to line up with the six fictional documents.

## Decision

1. **A mock tracker** serves fictional Northwind tickets and UAT runs from a JSON file
   (`mock-tracker/northwind-tracker.json`), read-only, shaped like a small subset of the
   **Jira REST API v3**: `issue/{key}` and `search` return Jira's JSON shape
   (`key`, `fields.status.name`, `fields.labels`, ...).
2. **SpecLens calls it over HTTP** with Spring's `RestClient`, through
   `speclens.tracker.base-url` (`TRACKER_BASE_URL`). The client has its own records for
   Jira's JSON and never imports the mock's classes: it depends only on the HTTP contract.
3. **Same deployable, separate boundary:** the mock lives in the same app under
   `/mock-tracker/**` so the demo stays one free-tier service. When the base URL is blank,
   the client calls this app's own port, so a request really goes out through the network
   stack and back in.

## Simplifications (and how they'd map to real Jira)

| Mock | Real Jira |
|---|---|
| `search?requirement=BR-8.1&status=Blocked&sprint=Sprint 9` | `search?jql=labels = "BR-8.1" AND status = Blocked AND sprint = "Sprint 9"` |
| `fields.labels` holds requirement IDs | Labels, or a custom "Requirement" field / issue links |
| `fields.sprint.name` | `customfield_10020` (sprint) |
| `test-runs?requirement=` | A test-management plugin such as Xray or Zephyr |
| No authentication | API token over HTTPS |

## Why

- **A real network boundary** brings the problems a real integration has (timeouts,
  connection failures, 404 vs 5xx, JSON mapping), so the failure handling is genuine and
  testable.
- **Swappable:** pointing `TRACKER_BASE_URL` at another tracker needs configuration and the
  query mapping above, not a redesign.
- **Credible demo data:** tickets are tagged with the documents' requirement IDs and tell a
  consistent story (snapshot 8 Jan 2027, after UAT), including a planted conflict: CR-003
  moved invoicing to 48 hours, but the 24-hour version is the one marked Done.

## Alternatives considered

- **Query the mock's data directly in Java:** simpler, but no network boundary, so the
  "tracker is down" behaviour would be fake.
- **A separate deployed service:** most realistic, but a second free-tier service doubles
  cold starts. The base URL makes that a configuration change if ever needed.
- **Real Jira Cloud with a demo project:** realistic, but credentials on a public demo and
  data that can drift from the documents.

## Consequences

- The demo always has consistent tracker data; `TRACKER_SIMULATE_OUTAGE=true` makes every
  tracker call return 503 to show the fallback.
- Seed data is hand-written, so tests guard its integrity (ID formats, unique keys, every
  failed UAT run linked to an existing defect).
