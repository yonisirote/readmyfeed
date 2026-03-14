# AGENTS.md

## Project Snapshot

- `readmyfeed` is now a native Android app written in Kotlin.
- The active app code lives under `android/`.
- The first migration target is the X/Twitter stack: session handling, timeline fetching, parsing, and native UI wiring.
- The old Expo implementation lives on the `expo-version` branch and should be treated as reference material, not active runtime code.

## Key Directories

- `android/app/src/main/kotlin/com/yonisirote/readmyfeed/`: app entry points and Android-native code.
- `android/app/src/main/kotlin/com/yonisirote/readmyfeed/x/auth/`: X session and cookie helpers.
- `android/app/src/main/kotlin/com/yonisirote/readmyfeed/x/timeline/`: X request building, parsing, and pagination.
- `android/app/src/test/kotlin/`: JVM unit tests for migrated Kotlin logic.

## Build / Test Commands

```bash
cd android
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## General Style

- Use Kotlin for app logic.
- Prefer small focused files and explicit data models.
- Keep Android framework code thin; move business logic into plain Kotlin classes where practical.
- Add comments only when a block is non-obvious.
- Default to ASCII unless a file already uses non-ASCII and it is required.

## Architecture Conventions

- Keep X auth/session concerns separate from timeline networking/parsing.
- Treat X payloads as unstable and untrusted; parse defensively.
- Prefer app-owned models over passing raw JSON through the app.
- Avoid coupling playback/UI concerns into the core migration layer.
- Defer timing-sensitive WebView auth logic unless the task explicitly requires it.

## Error Handling

- Prefer feature-specific exception types with a `code` and optional `context` payload.
- Do not log or persist raw secrets beyond what the session layer explicitly stores.
- Convert low-level network and parsing failures into X-domain errors near the boundary.

## Testing Guidance

- Prefer JVM unit tests for parser, request, and pagination logic.
- Port the existing X parser behavior tests as migration contracts.
- Run the smallest useful Gradle test task for the files you changed, then run `./gradlew assembleDebug` after meaningful Android edits.

## Before Finishing

- Call out any major migration pieces that remain intentionally deferred.
- Mention any checks you did not run.
