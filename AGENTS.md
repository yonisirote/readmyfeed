# AGENTS.md

## Project Snapshot

- `readmyfeed` is an Expo + React Native app using Expo Router.
- App routes live in `app/`; business logic lives in `src/`.
- Current product focus is X/Twitter auth + following timeline retrieval.
- `src/tts/` contains the app text-to-speech service.
- TypeScript is in strict mode via `tsconfig.json`.

## Repo Rule Files

- No repo-local Cursor rules were found in `.cursor/rules/`.
- No repo-local `.cursorrules` file was found.
- No repo-local Copilot instructions were found in `.github/copilot-instructions.md`.
- If any of those files are added later, treat them as higher-priority repo instructions.

## Key Directories

- `app/`: Expo Router screens and layouts.
- `app/(auth)/`: auth and connected-feed screens.
- `src/services/x/auth/`: cookie capture, session storage, auth helpers.
- `src/services/x/timeline/`: request, parse, and timeline state helpers.
- `src/tts/`: TTS service, logger, types, and errors.
- `__tests__/`: Jest tests.

## Install / Run

```bash
npm install
npm start
npm run android
npm run ios
npm run web
```

## Build / Lint / Test Commands

```bash
# lint
npm run lint

# format the repo
npm run format

# full test suite
npm test

# watch mode
npm test -- --watch

# single test file
npm test -- --runTestsByPath __tests__/xTimelineParser.test.ts

# file pattern
npm test -- --testPathPattern=xTimelineParser

# single named test
npm test -- -t "parses timeline tweets and next cursor"

# single named test inside one file
npm test -- --runTestsByPath __tests__/xTimelineParser.test.ts -t "parses timeline tweets"
```

## Tooling Notes

- ESLint uses `eslint-config-expo` through `eslint.config.cjs`.
- Prettier uses `.prettierrc` with `singleQuote: true`, `trailingComma: 'all'`, `printWidth: 100`.
- Jest uses `jest-expo` through `jest.config.cjs`.
- Tests match `__tests__/**/*.(ts|tsx|js)` and `*.test.*` / `*.spec.*`.
- Husky + `lint-staged` auto-run Prettier on `*.{js,jsx,ts,tsx,json,md,yml,yaml}` and ESLint `--fix` on `*.{js,jsx,ts,tsx}`.

## General Style

- Follow existing TypeScript + React Native patterns; do not introduce a new local style.
- Preserve semicolons, single quotes, and trailing commas.
- Keep files ASCII unless the file already uses non-ASCII or the content clearly requires it.
- Prefer small focused modules over broad utility files.
- Let Prettier handle wrapping instead of hand-formatting wide expressions.

## Imports

- Use ES module imports everywhere.
- Import external packages before local modules.
- Use relative imports; no TS path aliases are configured.
- Use `import type` for type-only imports when helpful.
- Re-export public folder APIs through `index.ts` when a directory already follows that pattern.
- Avoid new default exports in helpers/services unless the local pattern already uses them.

## Types

- Add explicit types for exported functions, class methods, constructor options, and return values.
- Avoid `any`; prefer `unknown`, unions, or dedicated object types.
- Prefer `type` aliases; that is the dominant pattern in this repo.
- Use `as const` for immutable code maps and config objects.
- Normalize third-party payloads into app-owned types before passing them deeper.
- Use nullable fields only when `null` is a meaningful state.

## Naming

- Components, classes, errors, and exported types: `PascalCase`.
- Functions, methods, variables, and object properties: `camelCase`.
- Stable global constants: `SCREAMING_SNAKE_CASE`.
- File names are feature-oriented camelCase like `xAuthService.ts` and `ttsService.ts`.
- Error code maps should match existing patterns like `xAuthErrorCodes`, `ttsErrorCodes`, or `X_TIMELINE_ERROR_CODES`.
- Test files should end in `.test.ts` or `.test.tsx`.

## React / Expo Conventions

- Use functional components.
- Default-export route components from files under `app/`.
- Keep screen-local styles in `StyleSheet.create(...)` at the bottom of the file.
- Keep hooks near the top of the component body.
- Use `useMemo` / `useRef` only when identity, lifecycle, or expensive setup actually matters.
- Keep route strings aligned with Expo Router file paths.
- Preserve the current plain React Native styling approach; do not add a new UI framework casually.

## Service Layer Conventions

- Keep business logic in `src/`, not in route files.
- Services are usually classes with dependency-injected collaborators.
- Constructor options commonly accept logger, store, auth service, or `fetchImpl` overrides.
- Prefer deterministic helpers over side-effect-heavy inline logic.
- Keep parsing separate from request orchestration when practical.
- Update the folder `index.ts` when you add a new public export to a feature area.

## Error Handling

- Prefer feature-specific error classes over raw `Error` or thrown strings.
- Existing error classes expose `message`, `code`, and optional `context`; follow that shape.
- Keep error codes stable, descriptive, and machine-friendly.
- In `catch` blocks, narrow with `instanceof Error` and fall back to `String(err)`.
- Attach useful structured context, but never include full cookie strings or secrets.
- Convert low-level failures into domain errors near the boundary where they occur.

## Logging

- Prefer feature loggers like `createXAuthLogger()` and `createTtsLogger()` over ad hoc console calls.
- Log with structured payloads instead of building long strings.
- Use `debug` for flow detail, `info` for milestones, `warn` for recoverable issues, and `error` for failures.
- Keep payloads serializable and safe.

## Testing Guidance

- Use Jest for unit and component tests.
- Use `@testing-library/react-native` for UI behavior.
- Put tests in `__tests__/` or next to source with `.test.ts` / `.test.tsx` names.
- Prefer behavior-focused test names and assertions.
- Mock network, storage, native modules, and WebView boundaries at the edge.
- Prefer observable outputs and UI state over private implementation details.
- Add a focused regression test for bug fixes when practical.

## X Integration Notes

- Be careful with cookies, tokens, and session persistence.
- Never log sensitive auth material.
- Preserve existing X session capture and timeline parsing behavior unless the task explicitly changes it.
- Validate missing-cookie, invalid-JSON, and non-OK-response paths when touching X integration.

## Before Finishing

- Run `npm run lint` after meaningful edits.
- Run the most targeted Jest command that covers the change; run full `npm test` when impact is broader.
- If formatting may have shifted, ensure Prettier output is clean.
- Call out any checks you did not run, especially native/expo validation.

## Commit Hygiene

- Expect pre-commit hooks to format and lint staged files.
- Do not commit cookies, tokens, secrets, or generated credentials.
- Keep commits scoped to the actual feature or fix.
- Avoid incidental refactors unless they are necessary for the task.
