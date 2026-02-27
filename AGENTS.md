# AGENTS.md - Developer Guide for ReadMyFeed

## Project Overview

ReadMyFeed is an Expo (React Native) app that connects to social media feeds (currently X/Twitter) and displays them in a unified interface. The architecture uses service classes with dependency injection for testability.

## Build Commands

```bash
# Start development server
npm start                    # or: expo start
npm run android              # Run on Android (requires emulator/device)
npm run ios                  # Run on iOS (requires Xcode)
npm run web                  # Run web version

# Linting and formatting
npm run lint                 # Run ESLint
npm run format               # Run Prettier (writes changes)

# Testing
npm test                     # Run all tests
npm test -- --testPathPattern=<pattern>  # Run specific test file
npm test -- -t "<test name>"              # Run tests matching name
npm test -- --watch                       # Watch mode
```

## Code Style Guidelines

### TypeScript

- Strict mode enabled in `tsconfig.json`
- Always use explicit types for function parameters and return values
- Use `const` by default, `let` only when reassignment is needed
- Avoid `any` - use `unknown` if type is truly unknown

### Naming Conventions

- **Files**: camelCase with feature-based grouping (e.g., `xAuthService.ts`, `xTimelineTypes.ts`)
- **Classes**: PascalCase (e.g., `XAuthService`, `XTimelineError`)
- **Functions/variables**: camelCase
- **Types/interfaces**: PascalCase
- **Constants**: SCREAMING_SNAKE_CASE for values, camelCase for const objects

### Import Order

```typescript
// 1. External libraries
import { useState } from 'react';
import { Link } from 'expo-router';

// 2. Internal services (absolute paths)
import { XAuthService } from 'src/services/x/auth/xAuthService';

// 3. Relative imports within same module
import { createXAuthLogger } from './xAuthLogger';
```

### Service Pattern

Services follow a consistent pattern for dependency injection:

```typescript
export type ServiceOptions = {
  logger?: CustomLogger;
  store?: StorageInterface;
  fetchImpl?: typeof fetch;
};

export class Service {
  private readonly logger: CustomLogger;
  private readonly store: StorageInterface;

  public constructor(options: ServiceOptions = {}) {
    this.logger = options.logger ?? createDefaultLogger();
    this.store = options.store ?? createDefaultStore();
  }
}
```

### Error Handling

Use custom error classes with error codes:

```typescript
export class CustomError extends Error {
  public readonly code: string;
  public readonly context?: Record<string, unknown>;

  public constructor(message: string, code: string, context?: Record<string, unknown>) {
    super(message);
    this.name = 'CustomError';
    this.code = code;
    this.context = context;
  }
}

export const ERROR_CODES = {
  FooFailed: 'ERROR_FOO_FAILED',
  BarMissing: 'ERROR_BAR_MISSING',
} as const;
```

### React Components

- Use functional components with hooks
- Define styles using `StyleSheet.create()` at the bottom of the file
- Extract complex logic into custom hooks
- Use TypeScript props types

```typescript
type Props = {
  title: string;
  onPress: () => void;
};

export function MyComponent({ title, onPress }: Props) {
  return <Button title={title} onPress={onPress} />;
}
```

### Logging

- Inject logger via constructor options
- Use appropriate log levels: `debug`, `info`, `warn`, `error`
- Include relevant context in log messages

### Testing

- Tests live in `__tests__/` directory
- Use `@testing-library/react-native` for component tests
- Use descriptive test names: `describe('Feature', () => { it('should do X', () => {...}) })`
- Mock external dependencies (fetch, secure store, etc.)

### Git Conventions

- Commit messages: concise, imperative mood
- Pre-commit hooks run lint-staged (Prettier + ESLint)
- Never commit secrets (API keys, session tokens go in SecureStore)

## Architecture

```
app/                    # Expo Router screens
  (auth)/               # Auth flow screens
src/
  services/             # Business logic
    x/
      auth/             # X authentication
      timeline/         # X timeline fetching
```

## Common Tasks

### Adding a New Feed (e.g., Telegram)

1. Create service directory: `src/services/telegram/`
2. Create types, errors, logger, service files following existing patterns
3. Add login screen in `app/(auth)/telegram-login.tsx`
4. Add route in `app/_layout.tsx`
5. Add entry point in `app/index.tsx`

### Running a Single Test

```bash
npm test -- --testPathPattern=xTimelineService
npm test -- -t "should fetch timeline"
```

### Adding Dependencies

```bash
npm install <package>   # Production
npm install -D <package>  # Dev dependency
```
