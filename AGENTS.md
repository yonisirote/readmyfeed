# AGENTS.md (ReadMyFeed)

This file is guidance for agentic coding assistants working in this repo.
It is intended to help autonomous coding agents make safe, consistent changes.

## Repo layout

- Root contains documentation and top-level scripts.
- `client/` is an Expo (React Native) app and the only active package.
- `client/src/services/xHome/` owns the X home timeline fetch flow.
- There is no server package in the main branch; fetching happens client-side.

### Key paths

- `client/App.tsx`: Expo entry point and UI shell.
- `client/src/services/auth/xLoginSession.ts`: WebView login + cookie resolution.
- `client/src/services/xHome/xHomeCookies.ts`: Cookie parsing + header helpers.
- `client/src/services/xHome/`: Timeline fetcher, parsing, and config helpers.
- `client/src/types/`: Custom module declarations (e.g. `base-64`).

## Setup

- Install deps (client): `npm --prefix client install`
- Environment:
  - Root `.env` is optional; X login is handled in-app via WebView.
  - Never commit secrets (cookies, credentials JSON, tokens).

## Build / run / test

All of these commands are run from the repo root unless noted.

- For Expo-related issues or feature guidance, consult: https://docs.expo.dev/llms.txt

### Development app

- Start Expo dev server: `npm --prefix client run start`
- Android: `npm --prefix client run android`
- iOS: `npm --prefix client run ios`
- Web preview: `npm --prefix client run web`

### Tests

- Run all tests: `npm --prefix client test`
- Run a single test file:
  - `npm --prefix client test -- xHome/xTimelineParsing.test.ts`
- Run a single test name:
  - `npm --prefix client test -- -t "parses entries"`

### Build / lint / typecheck

- No ESLint/Prettier configuration is present.
- No dedicated typecheck script is present; TypeScript is `strict: true`.
- APK build (EAS, from `client/`): `npx eas build -p android --profile preview`
- If you introduce linting or formatting tooling, keep it scoped and avoid
  reformatting unrelated files.

## TypeScript / module system

- TypeScript is `strict: true` (`client/tsconfig.json`).
- For React Native/Metro, keep extensionless local imports.
- Use `import type` for type-only imports.
- Shared types for the X timeline flow live in `client/src/services/xHome/xHomeTypes.ts`.

## Code style guidelines

### Imports

- Group imports with a blank line between:
  1. External packages
  2. Internal modules (relative imports)
- Keep import order stable within groups.
- Prefer local relative imports for app modules.

### Formatting

- Indentation: 2 spaces.
- Quotes: single quotes.
- Semicolons: required.
- Trailing commas in multiline objects and arrays.
- Blank line between import groups and logical blocks.
- Avoid drive-by whitespace or reformat-only changes.

### Types

- Prefer explicit return types on exported functions.
- Use type aliases for domain shapes (`XHomeTimelineResult`, etc.).
- Avoid `any` unless bridging external data; validate at the boundary.
- Add new module declarations under `client/src/types/` when needed.

### Naming conventions

- Files: `camelCase.ts` for utilities/services; `*.test.ts` for tests.
- Folders: `camelCase` (e.g. `xHome`).
- Functions/values: `camelCase`.
- Types: `PascalCase`.
- Constants: `SCREAMING_SNAKE_CASE` (e.g. `X_HOME_CONFIG`).
- Boolean names: `hasMore`, `isEnabled`, `shouldFetch`, etc.

### Error handling

- Use `try/catch` around fetch boundaries and JSON parsing.
- Throw readable errors with concise context.
- Do not log secrets (API keys, cookies, auth headers).
- Use `error instanceof Error ? error.message : String(error)`.

### Logging

- Use concise, grep-friendly logs with a module prefix (e.g. `[rmf]`).
- Avoid logging entire payloads from X.

### Networking

- Use the Expo FileSystem download path for requests requiring cookie headers.
- Keep request constants in `X_HOME_CONFIG` and reuse helpers for URLs/cookies.
- Do not log cookie values or auth tokens.

### Pagination contract

- `fetchXHomeTimeline` returns `entriesCount`, `tweetsCount`, `nextCursor`,
  and `tweetSamples`.
- Clients should de-dupe tweets by `id` across pages.

### React Native UI

- Preserve the existing theme/typography choices in `client/App.tsx`.
- Prefer `StyleSheet.create` for styles over inline objects.

### Testing notes

- Jest tests live next to the source in `client/src/services/xHome/`.
- Prefer focused unit tests for parsing and extraction logic.
- Keep fixtures minimal; avoid logging full payloads in tests.

## Dependency changes

- Update `client/package.json` and `client/package-lock.json` together.
- Avoid adding dependencies unless needed for the requested change.
- Keep Expo/React Native versions aligned with existing constraints.

## Cursor / Copilot rules

- No `.cursor/rules/`, `.cursorrules`, or `.github/copilot-instructions.md`
  were found in this repo.

## Quick checklist for changes

- Keep TypeScript strict and tests passing (`npm --prefix client test`).
- Update docs when behavior or commands change.
- Update `client/package-lock.json` when dependencies change.
- Avoid reformatting unrelated files.
