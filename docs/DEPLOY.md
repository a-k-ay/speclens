# Deploying SpecLens (Neon + Render, free tiers)

```
Browser ──HTTPS──> Render web service (Docker, 512 MB) ──TLS──> Neon Postgres + pgvector
                         │
                         └──HTTPS──> Gemini API (embeddings + chat)
```

The public demo runs with **uploads disabled** and the six fictional sample documents
preloaded (`DEMO_SEED=true`). Per-IP and daily rate limits protect the Gemini key.

## 1. Database: Neon

1. Sign in at <https://neon.tech> and create a project, e.g. `speclens`. Pick the region
   closest to your Render region (Singapore is closest to India).
2. Open **Connection details** and switch **off** "Connection pooling" so you get the
   **direct** host (it does *not* contain `-pooler`).
   *Why:* the app sets `hnsw.iterative_scan` once per connection. The pooler runs each
   transaction on whichever server connection is free, so that setting would be lost.
3. Note the host, database, user and password. The JDBC URL looks like:

   ```
   jdbc:postgresql://<host>/<database>?sslmode=require
   ```

You don't need to create tables or enable pgvector by hand: Flyway runs on first start,
and migration V1 runs `CREATE EXTENSION IF NOT EXISTS vector`.

## 2. App: Render

1. Sign in at <https://render.com> with GitHub.
2. **New → Blueprint**, select the `a-k-ay/speclens` repository. Render reads
   [`render.yaml`](../render.yaml) and proposes a free Docker web service.
3. Fill in the secret values it asks for:

   | Key | Value |
   |---|---|
   | `GEMINI_API_KEY` | your SpecLens Gemini key |
   | `DB_URL` | the JDBC URL from step 1 |
   | `DB_USER` | Neon user |
   | `DB_PASSWORD` | Neon password |

   `UPLOAD_ENABLED=false` and `DEMO_SEED=true` are already set by the blueprint.
4. Click **Apply**. The first build takes several minutes (Maven downloads dependencies
   inside Docker).
5. Watch the logs. On first start you should see Flyway apply 3 migrations, then six
   `Ingested '...'` lines and `Demo project 'Northwind Freight (sample client)' ... has 6 documents`.
   Later restarts skip already-loaded files, so no extra Gemini calls.
6. Open the `https://speclens-xxxx.onrender.com` URL. Health check: `/actuator/health`.

## Free-tier behaviour to know (and to mention when you share the link)

- **Cold start:** a free Render service sleeps after about 15 minutes without traffic. The
  first request after that takes roughly a minute while the container and JVM start.
  The README says so.
- **Memory:** the Dockerfile caps the heap at 60% of the container and uses Serial GC and
  C1-only JIT to fit comfortably in 512 MB.
- **Rate limits:** 10 requests per minute and 100 per day per IP, 500 per day overall
  (`RATE_LIMIT_*` environment variables change them).

## Updating

Pushing to `main` runs CI on GitHub and, separately, triggers a Render deploy
(`autoDeploy: true`). Flyway applies any new migrations on startup.

## Rotating the Gemini key

Create a new key in Google AI Studio, update `GEMINI_API_KEY` in Render
(Environment tab; the service restarts), then delete the old key.
