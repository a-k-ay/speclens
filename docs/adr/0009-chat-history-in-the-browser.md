# ADR 0009: Chat history stays in the browser; read-only public demo

- **Status:** Accepted
- **Date:** 2026-10-03

## Context

Users want to come back to earlier questions and answers, and to manage their projects
and documents. SpecLens has no user accounts: anyone with the link uses the same app.

## Decision

1. **History is saved in the browser** (`localStorage`, one key per project, last 50
   exchanges), not on the server. Reloading the page restores the conversation; **New chat**
   clears it; **Export** downloads it as a Markdown file with every answer's quoted sources.
2. **Read-only mode** (`UPLOAD_ENABLED=false`, used by the public demo) blocks every change
   on the server, not just in the UI: uploads, creating projects and deleting projects or
   documents all return `403`. The UI hides those controls.
3. Locally (or anywhere editing is enabled), projects and documents can be created and
   deleted from the UI, with a confirmation that states what will be removed.

## Why

- Without accounts, server-side history would show one visitor's questions to every other
  visitor of the public demo. Browser storage is private to that browser by design.
- Export covers "I want to keep this": a file the user owns, readable anywhere.
- Hiding buttons isn't security; the server enforces read-only mode itself, and a test
  checks every write endpoint returns `403`.

## Consequences

- History doesn't follow the user to another browser or device. Server-side history needs
  accounts (authentication, per-user data isolation), which is a non-goal for now.
- Clearing browser data clears the history; Export is the durable copy.
