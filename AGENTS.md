# AGENTS.md

## Project Snapshot

- `readmyfeed` is an Expo + React Native app using Expo Router.
- App routes live in `app/`; business logic, services, and adapters live in `src/`.
- Main flow: X/Twitter login, cookie capture, following-timeline fetch, and TTS playback.
- TypeScript is strict via `tsconfig.json`.
- Prefer existing Expo/React Native patterns over introducing new frameworks.

## Repo Rule Files

- No repo-local Cursor rules were found in `.cursor/rules/`.
- No `.cursorrules` file was found.
- No Copilot rules were found in `.github/copilot-instructions.md`.
- Treat this file as the repo-local guide unless higher-priority rule files are added later.

## Key Directories

- `app/`: Expo Router screens, layouts, and route entry points.
- `app/(auth)/`: X login and feed screens.
- `src/services/x/auth/`: cookie capture, session storage, auth diagnostics, and WebView helpers.
- `src/services/x/timeline/`: request building, response parsing, feed state, and TTS adapters.
- `src/tts/`: TTS service, voice selection, speakable-item helpers, logger, errors, and types.
- `__tests__/`: Jest tests for screens, parsers, services, and helpers.

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
npm test -- --runTestsByPath __tests__/ttsService.test.ts
# multiple specific test files
npm test -- --runTestsByPath __tests__/ttsService.test.ts __tests__/speakableItem.test.ts
# file pattern
npm test -- --testPathPattern=xTimelineParser
# single named test
npm test -- -t "prefers a local hebrew voice over a generic language entry"
# single named test inside one file
npm test -- --runTestsByPath __tests__/ttsService.test.ts -t "prefers an exact US English voice"
```

- There is no separate `build` script in `package.json`; native build/install flows go through Expo CLI via `npm run android` and `npm run ios`.

## Tooling Notes

- Primary ESLint config is the flat config in `eslint.config.cjs`.
- A legacy `.eslintrc.cjs` also exists; prefer the flat config if guidance conflicts.
- Prettier uses `singleQuote: true`, `trailingComma: 'all'`, and `printWidth: 100`.
- Jest uses `jest-expo` and `@testing-library/jest-native/extend-expect`.
- Babel uses `babel-preset-expo`.
- Husky runs `npx lint-staged` from `.husky/pre-commit`.
- `lint-staged` formats `*.{js,jsx,ts,tsx,json,md,yml,yaml}` and runs ESLint `--fix` on `*.{js,jsx,ts,tsx}`.

## General Style

- Preserve semicolons, single quotes, trailing commas, and Prettier wrapping.
- Default to ASCII unless a file already contains non-ASCII or the content truly requires it.
- Keep modules focused; prefer extracting small helpers over growing broad utility files.
- Do not introduce new UI libraries, state libraries, or folder conventions casually.
- Add comments only when a block is non-obvious or when provenance/adaptation notes matter.

## Imports and Exports

- Use ES module imports everywhere.
- Import external packages before local modules.
- Use relative imports; no TS path aliases are configured.
- Use `import type` for type-only imports when it improves clarity.
- Route files under `app/` default-export the screen component.
- Helpers, services, errors, and types generally use named exports.
- Update folder-level `index.ts` files when adding a new public export.

## Types

- Add explicit types for exported functions, class methods, constructor options, and public helpers.
- Prefer `type` aliases over `interface`; that is the dominant repo pattern.
- Avoid `any`; prefer `unknown`, narrowed unions, or dedicated object shapes.
- Use `as const` for error-code maps and immutable configuration objects.
- Treat third-party and network payloads as untrusted; validate or normalize them before deeper use.
- Use nullable fields only when `null` is a real, meaningful state.

## Naming

- Components, classes, errors, and exported types: `PascalCase`.
- Functions, methods, local variables, and object properties: `camelCase`.
- Stable constants: `SCREAMING_SNAKE_CASE`.
- File names use feature-oriented camelCase, e.g. `xAuthService.ts`, `xTimelineParser.ts`, `ttsService.ts`.
- Error code maps should match patterns like `xAuthErrorCodes`, `ttsErrorCodes`, and `X_TIMELINE_ERROR_CODES`.
- Test files should end in `.test.ts` or `.test.tsx`.

## React / Expo Conventions

- Use functional components.
- Keep hooks near the top of the component body.
- Keep screen-local styles in `StyleSheet.create(...)` at the bottom of the file.
- Use `useMemo` and `useRef` only when identity, lifecycle, or in-flight state actually matters.
- Keep Expo Router paths aligned with file-system routes.
- Keep route files thin and move business logic into `src/`.

## Service / Architecture Conventions

- Business logic belongs in `src/`, not inside route components.
- Services are usually classes with dependency-injected collaborators.
- Constructor options commonly accept `logger`, `store`, `authService`, or `fetchImpl` overrides.
- Keep parsing and normalization separate from request orchestration when practical.
- Prefer deterministic helpers over side-effect-heavy inline logic.
- Reuse existing domain modules before creating new cross-cutting utilities.

## Error Handling

- Prefer feature-specific error classes over raw `Error` or thrown strings.
- Current error classes expose `message`, `code`, and optional `context`; follow that shape.
- Convert low-level failures into domain errors near the boundary where they happen.
- In `catch` blocks, narrow with `instanceof Error` or the relevant domain error class.
- Fall back to `String(err)` when a caught value is not an `Error`.
- Never include full cookie strings, tokens, or other secrets in thrown context or logs.

## Logging

- Prefer feature loggers such as `createXAuthLogger()` and `createTtsLogger()`.
- Keep logging structured; pass payload objects instead of building long string messages.
- Use `debug` for flow detail, `info` for milestones, `warn` for recoverable issues, and `error` for failures.
- Avoid ad hoc `console.*` calls outside shared logger modules.

## Testing Guidance

- Use Jest for unit and component tests.
- Use `@testing-library/react-native` for UI behavior.
- Keep tests behavior-focused and name them around observable outcomes.
- Mock native modules, storage, network, and WebView boundaries at the edges.
- For parser/helper tests, assert normalized outputs rather than internal implementation details.
- Add a focused regression test when fixing a bug, especially around X payload parsing or TTS language selection.
- Prefer the smallest targeted Jest command that covers the change; run full `npm test` for broader edits.

## X Integration Notes

- Be careful with cookies, CSRF tokens, and session persistence.
- Never log sensitive auth material or raw cookie values.
- Preserve existing X session-capture and timeline-parsing behavior unless the task explicitly changes it.
- When touching timeline/auth flows, validate missing-cookie, invalid-JSON, and non-OK response paths.
- Keep adapters and normalization layers explicit when translating X data into app-owned shapes.

## TTS / Hebrew Notes

- `src/tts/` uses `expo-speech`, so language support depends on voices installed on the device.
- Hebrew voices may appear as `iw-IL` even when the requested language is `he-IL`; preserve that normalization logic.
- Prefer concrete/local Hebrew voices over generic placeholder language entries when multiple installed voices match.
- Do not rely on X `item.lang` alone for Hebrew; mixed Hebrew/English content may be labeled as English.
- Preserve the current text-based Hebrew detection in speakable-item helpers.
- For English playback, prefer an exact `en-US` match over falling back to another English locale when a US voice exists.

## Before Finishing

- Run `npm run lint` after meaningful code edits.
- Run the most targeted Jest command that covers the change.
- Run `npm run format` if you changed files that Prettier would reflow or if hooks would likely rewrite them.
- Call out any checks you did not run, especially native/expo validation.

## Commit Hygiene

- Expect pre-commit hooks to format and lint staged files.
- Keep commits scoped to the actual feature or fix.
- Do not commit cookies, tokens, secrets, or generated credentials.
- Avoid incidental refactors unless they are necessary for the requested change.
