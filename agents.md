# Agents

## Purpose

- Provide fast, accurate guidance for work in this repo.
- Prefer repo conventions over personal defaults.
- Keep this file updated when conventions change.

## Expo reference

- Always consult https://docs.expo.dev/llms.txt for Expo-specific tasks.
- Use Expo docs for WebView, cookies, and native module guidance.

## Repo snapshot

- App entry is `index.ts` with `expo-router/entry`.
- Routes live under `app/` (ex: `app/index.tsx`, `app/_layout.tsx`).
- Tests live under `__tests__/` and use Jest + Testing Library.
- Tooling includes Jest, Prettier, ESLint, Husky, and lint-staged.

## Commands

### Install

- `npm install`

### Dev (Expo)

- `npm run start`
- `npm run start -- --clear`
- `npm run android`
- `npm run ios`
- `npm run web`

### Lint and format

- `npm run lint`
- `npm run format`
- Pre-commit: lint-staged runs `prettier --write` and `eslint --fix`.

### Tests

- `npm test`
- Single file: `npm test -- __tests__/home.test.tsx`
- Single test name: `npm test -- -t "renders the app title"`
- Watch mode: `npm test -- --watch`
- Update snapshots: `npm test -- -u`

### Build

- No build script or `eas.json` is present.
- For web export (if needed): `npx expo export --platform web`
- For native release builds, use Expo/EAS tooling; confirm before adding config.

## Code style

### Formatting

- Prettier rules: single quotes, trailing commas, print width 100.
- Let Prettier decide line breaks; avoid manual formatting tweaks.
- Keep JSX clean and avoid deeply nested inline expressions.

### Imports

- Group imports: external packages, blank line, then local/relative files.
- Keep import lists minimal and remove unused imports promptly.
- Prefer named imports over default when the library supports them.

### Components

- Use function components with default exports.
- Keep components focused; split files when they get large.
- Define `StyleSheet.create` at the bottom of the file.
- Use descriptive style keys (`container`, `title`, `subtitle`).

### TypeScript

- `strict` mode is enabled; avoid `any`.
- Add explicit types for component props and complex return values.
- Prefer `type` for unions/aliases; use `interface` for extendable shapes.
- Use `undefined` checks when a value may be optional.

### Naming

- Components: PascalCase (`HomeScreen`).
- Hooks: camelCase with `use` prefix (`useFoo`).
- Variables/functions: camelCase.
- Constants: camelCase unless truly constant (`MAX_ITEMS`).
- Route files follow Expo Router conventions (`app/index.tsx`, `app/_layout.tsx`).

### Styling

- Use `StyleSheet.create` rather than inline styles when possible.
- Keep styles flat and readable; avoid excessive nesting.
- Prefer platform defaults for fonts unless a design system is added.
- Keep spacing/size values consistent within a screen.

### Error handling

- Use `try/catch` around async calls and side effects.
- Do not swallow errors; log or surface a user-friendly message.
- Prefer early returns on invalid state.
- Keep error messages actionable and consistent.

### Tests

- Use `@testing-library/react-native` and test user-visible behavior.
- Prefer `getByText`/`getByRole` over snapshots for stability.
- Name tests clearly and keep them small and focused.
- Place tests in `__tests__` with `.test.tsx` suffix.

## Expo Router conventions

- Use `Stack` in `app/_layout.tsx` for navigation.
- Keep route components side-effect free at render time.
- Avoid dynamic route patterns unless required and documented.

## File and directory conventions

- `app/`: route components.
- `assets/`: static images and other media.
- `__tests__/`: Jest tests.
- Add new shared components in a dedicated folder (ex: `app/components/`) if needed.

## React Native patterns

- Prefer `View`, `Text`, and `StyleSheet` from `react-native`.
- Avoid heavy work in render; use hooks for side effects.
- Keep layout responsive; test on small and large screens.

## Accessibility

- Provide readable text sizes and sufficient contrast.
- Use accessibility labels/roles for interactive elements.
- Avoid conveying meaning by color alone.

## Performance

- Avoid unnecessary re-renders; memoize if a component grows.
- Keep images optimized and in `assets/`.
- Prefer flat lists and virtualization for long lists.

## Tooling notes

- Jest preset: `jest-expo` (see `jest.config.cjs`).
- Babel preset: `babel-preset-expo` with `expo-router/babel`.
- Prettier config lives in `.prettierrc`.

## Config files

- App config: `app.json`.
- Babel config: `babel.config.cjs`.
- Jest config: `jest.config.cjs`.
- TypeScript config: `tsconfig.json`.
- Package scripts: `package.json`.

## Dependency management

- Use npm (repo has `package-lock.json`).
- Keep dependencies minimal; confirm before adding heavy libraries.
- Prefer Expo-compatible libraries.

## Git hygiene

- Do not edit `.env` or other secret files.
- Keep commits focused; do not commit without user request.
- Do not remove unrelated changes.

## Cursor/Copilot rules

- No `.cursor/rules/`, `.cursorrules`, or `.github/copilot-instructions.md` found.
- If added later, update this file to reflect them.

## When in doubt

- Re-check `package.json` scripts for the latest commands.
- Follow Expo docs for platform-specific behavior.
- Ask if a change touches auth, payments, or external services.
