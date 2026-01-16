# AGENTS.md (ReadMyFeed)

This file is guidance for agentic coding assistants working in this repo.
It is intended to help autonomous coding agents make safe, consistent changes.

## Repo layout

- Root is a Node.js workspace.
- `server/` is the only workspace package (`@readmyfeed/server`).
- Backend is Express + TypeScript (ESM), built with `tsc`, run in dev with `tsx`.
- “Tests” are repo-specific TypeScript smoke scripts under `server/src/smoke/`.

## Setup

- Install deps (root): `npm install`
- Environment:
  - Create root `.env` (not committed) based on `.env.example`.
  - Required: `API_KEY` (from X Auth Helper)
  - Optional: `PORT` (default in `server/src/config/server.ts`)

## Build / run / test

All of these commands are run from the repo root unless noted.

### Development server

- Start dev server (watches): `npm run dev`
  - Runs workspace script `server:dev` → `tsx watch --env-file=../.env src/index.ts`
  - Health: `GET http://localhost:3001/healthz`
  - Feed API: `GET http://localhost:3001/api/feed?count=5`

### Build

- Build TypeScript (workspace): `npm run build -w server`
- Start built output (workspace): `npm run start -w server`

### Smoke tests (repo-specific)

There is no Jest/Vitest test runner configured. The project uses executable
TypeScript “smoke” scripts under `server/src/smoke/`.

- Live smoke test (hits X via `rettiwt-api`): `npm run smoke`
- Pagination smoke test (mocked): `npm run smoke:pagination:mock`

### Run a “single test”

Because smoke scripts are just TS entrypoints, “single test” means running one
script file directly with `tsx`.

- Run one smoke file:
  - From root: `npx tsx --env-file=./.env server/src/smoke/feed.ts`
  - Or without env file (if not needed): `npx tsx server/src/smoke/feedPaginationMock.ts`

Tips:
- Prefer the mocked pagination smoke when iterating locally.
- Be careful with rate limits when running the live smoke.

## Build / lint / typecheck

No ESLint/Prettier configuration is currently present in the repo.

- Type-check + build (acts as “lint”): `npm run build -w server`

If you introduce a lint/format tool as part of a change request, keep it scoped and consistent with existing style; do not reformat unrelated files.

## TypeScript / module system

- TypeScript is `strict: true` (`server/tsconfig.json`). Keep new code strict.
- ESM is used everywhere (`"type": "module"` in `server/package.json`).
- When importing local files in TS, include the `.js` extension:
  - Good: `import { foo } from './foo.js'`
  - Good: `import type { Foo } from './foo.js'`
  - Avoid: `import { foo } from './foo'`

## Code style guidelines

### Imports

- Group imports with a blank line between:
  1. External packages (e.g. `express`, `cors`)
  2. Internal modules (relative imports)
- Use `import type` for type-only imports.
- Keep import paths consistent with ESM (`.js` extensions).

### Formatting

- Current codebase style:
  - 2 spaces indentation
  - single quotes
  - trailing commas in multiline objects
  - semicolons
  - blank line between import groups and logical blocks
- Match existing local formatting in a file; avoid drive-by whitespace changes.

### Types

- Prefer explicit return types on exported functions.
- Keep types close to the domain (e.g. `FeedTweet`, `FeedResult` in `services/twitter.ts`).
- Avoid `any` unless you are bridging external/unknown library data; if you must
  use `any`, convert/validate at the boundary (e.g. `toFeedTweet(item: any)`).

### Naming conventions

- Files: `camelCase.ts` for utilities and services (matches current repo).
- Exports:
  - `camelCase` for functions/values (`getFeed`, `getFollowedFeed`).
  - `PascalCase` for types (`FeedTweet`).
  - `SCREAMING_SNAKE_CASE` for constants.
- Boolean names: `hasMore`, `isEnabled`, etc.

### API handlers (Express)

- Use `RequestHandler` and make handlers `async` when doing I/O.
- Validate and coerce query parameters defensively:
  - Parse strings → numbers
  - Clamp to allowed ranges (see `clampInt` in `handlers/feedHandler.ts`)
- Return JSON consistently.

### Error handling

- Prefer `try/catch` at request boundaries and return structured errors:
  - Include a stable error code (e.g. `failed_to_fetch_feed`)
  - Include a human message; use `error instanceof Error ? error.message : String(error)`
- Do not leak secrets (API keys, headers) into logs or responses.
- When interacting with external APIs (X/Twitter), handle:
  - rate limiting (see `utils/rateLimit.ts`)
  - empty/invalid responses
  - pagination cursors that don’t advance

### Logging

- Use concise, grep-friendly logs with a module prefix:
  - Example: ``[feed] tweets loaded successfully count=...``
- Avoid logging entire payloads from `rettiwt-api`.

### Pagination contract

`GET /api/feed` returns:
- `tweets`: current page of tweets
- `nextCursor`: cursor string for the next page (if any)
- `hasMore`: whether the client should fetch `nextCursor`

Clients should de-dupe by `tweet.id` across pages.

## Cursor / Copilot rules

- No `.cursor/rules/`, `.cursorrules`, or `.github/copilot-instructions.md` were found in this repo.

## Quick checklist for changes

- Keep ESM import extensions (`.js`) correct.
- Keep TypeScript strict and passing `npm run build -w server`.
- Prefer adding/adjusting smoke scripts for behavior checks.
- Avoid reformatting unrelated files.
