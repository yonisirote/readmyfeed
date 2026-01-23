# AGENTS.md (ReadMyFeed)

This file is guidance for agentic coding assistants working in this repo.
It is intended to help autonomous coding agents make safe, consistent changes.

## Repo layout

- Root contains documentation and top-level scripts.
- `client/` is an Expo (React Native) app and the only active package.
- There is no server package in the main branch; fetching happens client-side.

## Setup

- Install deps (client): `npm --prefix client install`
- Environment:
  - Root `.env` is optional; API keys are entered in the app UI when testing fetches.

## Build / run / test

All of these commands are run from the repo root unless noted.

### Development app

- Start Expo dev server: `npm --prefix client run start`
- Android: `npm --prefix client run android`
- iOS: `npm --prefix client run ios`

### Tests

- Jest tests: `npm --prefix client test`

## Build / lint / typecheck

No ESLint/Prettier configuration is currently present in the repo.

If you introduce a lint/format tool as part of a change request, keep it scoped and consistent with existing style; do not reformat unrelated files.

## TypeScript / module system

- TypeScript is `strict: true` (`client/tsconfig.json`). Keep new code strict.
- For React Native/Metro, prefer extensionless local imports (no `.js`).

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

### Error handling

- Prefer `try/catch` around fetch boundaries and surface readable errors.
- Do not log secrets (API keys, cookies).

### Logging

- Use concise, grep-friendly logs with a module prefix (e.g. `[rmf]`).

### Pagination contract

- `fetchXHomeTimeline` returns tweet samples, `nextCursor`, and `hasMore` state.

## Cursor / Copilot rules

- No `.cursor/rules/`, `.cursorrules`, or `.github/copilot-instructions.md` were found in this repo.

## Quick checklist for changes

- Keep TypeScript strict and tests passing (`npm --prefix client test`).
- Avoid reformatting unrelated files.
