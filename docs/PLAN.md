# Mobile Rettiwt Spike — Summary & Next Steps

## Goal

Build **ReadMyFeed** (a hands‑free reader of your X/Twitter “followed/home” timeline for TTS) with a feed‑fetching pipeline that can run on mobile.

The core product question driving all technical exploration:

> **Can we fetch the followed/home timeline from the mobile app itself (Capacitor/WebView) without relying on a Node server proxy?**

If “yes”: we can potentially operate without a server (or keep it optional).
If “no”: the existing Node server remains the stable path for X‑fetching.

**Current decision:** direct fetch in the Capacitor app is the primary path; the server is kept as a backup under `backup/server/`.

---

## What We’re Trying to Check (Hypothesis Tests)

We’re validating these assumptions in sequence:

1. **Can we generate `x-client-transaction-id` in a browser‑compatible environment?**
   - This header is required by X’s internal GraphQL endpoints (`/i/api/graphql/.../HomeLatestTimeline`).
   - We need to verify the `x-client-transaction-id` package works when `crypto.subtle`, `DOMParser`, `TextEncoder`, and `fetch` are present.

2. **Can a browser/Capacitor WebView make a GraphQL request for followed timeline directly to `https://x.com/i/api/graphql/.../HomeLatestTimeline`?**
   - If yes: we can build a mobile‑native fetcher without Node.
   - If no: we need either:
     - A server proxy (your existing `server/`), or
     - Capacitor native HTTP (bypasses CORS + cookie/header restrictions), or
     - Another workaround.

3. **If we can call it, can we parse entries + cursor reliably?**
   - Extract tweet results (`tweet_results`) and the “bottom cursor” (`cursor-bottom-...`) for pagination.

---

## What We’ve Done So Far (In Detail)

### D) Direct X Fetch From Android (Capacitor Native HTTP)

**Status:** Working in the Capacitor app (`client/`), validated via Android emulator + logcat after refactors.

We have now proven an end-to-end **direct** fetch from the Android app (no Node server proxy needed):

- Fetches `https://x.com` HTML (status `200`) and builds a DOM via `DOMParser`.
- Generates `x-client-transaction-id` via `x-client-transaction-id` (tid length ~94).
- Extracts a valid `authorization: Bearer ...` token by scanning X web JS bundles (e.g. `https://abs.twimg.com/responsive-web/client-web/*.js`).
- Calls GraphQL Home timeline:
  - `GET https://x.com/i/api/graphql/CRprHpVA12yhsub-KRERIg/HomeLatestTimeline`
- Uses native HTTP headers (bypasses browser CORS) including:
  - `Cookie` (decoded from `API_KEY` base64)
  - `x-csrf-token` (from `ct0` cookie)
  - `x-client-transaction-id`
  - `authorization: Bearer <extracted>`
- **Confirmed tweets were fetched** on Android (emulator) by logging the first 5 tweet samples (id + user + text) from the GraphQL response.

Main implementation lives in:
- `client/src/App.tsx` (UI + orchestration)
- `client/src/services/xHomeTimeline.ts` (native X fetch implementation)

---

### A) Backup: Node Server Fetching via `rettiwt-api`

The backend under `backup/server/` is a stable implementation that fetches the followed timeline using `rettiwt-api`:

- **`backup/server/src/services/twitter.ts`**
  - Creates a `Rettiwt` client using `twitterConfig.apiKey` (from env `API_KEY`).
  - Calls `client.user.followed(cursor)` which returns ~35 items.
  - Normalizes items into `FeedTweet` (id, text, createdAt, user, url, retweetOf).
  - Slices to requested `count`, derives `nextCursor`, computes `hasMore`.

- **`backup/server/src/handlers/feedHandler.ts`**
  - Exposes an HTTP handler `getFeed`:
    - Accepts query params: `count`, `cursor`
    - Returns JSON `{ tweets, nextCursor, hasMore }`
    - On error returns `{ error: 'failed_to_fetch_feed', message }`

- **`backup/server/src/routes/feedRoute.ts`**, **`backup/server/src/utils/*`**, **`backup/server/src/config/*`**
  - Route wiring, helpers (rate limiting, HTML entity decode), env config reading.

This path works because Node can run `rettiwt-api`, handle cookies/bearer internally, and never hits browser CORS/credential restrictions.

---

### B) Historical note

We initially tried a browser-only PoC (`mobile-rettiwt-proof/`) but it was blocked by CORS/credential/header restrictions. Once the Capacitor native HTTP approach worked, the browser proof was removed.

---

## What’s Been Proven vs. Not Proven

### Proven

- **You *can* generate `x-client-transaction-id` in a browser/WebView context** using `x-client-transaction-id`:
  - We observed `crypto.subtle available=true` and `Generated tid length=94`.

- **Capacitor native HTTP can make requests from Android and bypass CORS**:
  - We successfully called `http://10.0.2.2:3001/api/feed` and got `Native response status=200` with tweet data.

- **Node server using `rettiwt-api` reliably fetches the followed feed** and returns a clean, normalized response.

### Not Proven (and Likely Blocked in Pure Browser)

- **Calling X’s internal GraphQL feed endpoint directly from browser JS** from a non‑x.com origin (e.g. `http://localhost:5173` or `http://localhost`):
  - CORS preflight fails because X does not send `Access-Control-Allow-Credentials: true`.
  - You cannot set the `Cookie` header in `window.fetch` (browsers disallow it).
  - Custom headers (`authorization`, `x-client-transaction-id`) trigger a preflight that doesn’t allow credentialed cross‑origin requests.

- **Using the placeholder `authorization: Bearer ...` header** in the browser proof:
  - It may not match X’s current bearer rotation; we haven’t validated a working bearer from a real browser session or from `rettiwt-api`.

---

## Next Steps

- Productionize the mobile fetcher (error handling, rate limit handling, pagination, de-dupe by tweet id).
- Build a minimal “read feed” UX (queue, TTS integration, state persistence).
- Keep `backup/server/` buildable as a fallback path.

---

## Current Direction

- Primary path: `client/` (Capacitor app) uses native HTTP to fetch X timeline directly.
- Backup path: `backup/server/` kept for reference and fallback.

---

## Android CLI Guide

Moved to `docs/androidCliGuide.md`.
