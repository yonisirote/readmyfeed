# AGENTS.md

## Scope

- This file is the primary repo instruction set for agentic coding assistants working in `/home/yoni/Workspace/readmyfeed`.
- The active app lives in `android/`; the old Expo app on `expo-version` is reference-only.
- No `.cursorrules`, `.cursor/rules/`, or `.github/copilot-instructions.md` exist in this repo right now.
- If any of those files appear later, treat them as additional rules and keep this file aligned with them.

## Project Snapshot

- `readmyfeed` is a native Android app written in Kotlin.
- The current native X scope includes:
  - encrypted session storage
  - WebView-based X login and cookie capture
  - following timeline request building, parsing, and pagination
  - native feed rendering with `RecyclerView`
  - screen-scoped TTS playback for loaded tweets
- Telegram migration and background playback are still pending.

## Important Paths

- `android/app/src/main/kotlin/com/yonisirote/readmyfeed/shell/MainActivity.kt`: current screen and flow coordinator.
- `android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/x/auth/`: X auth, session, cookie, and login-capture helpers.
- `android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/x/timeline/`: X request, parser, and pagination logic.
- `android/tdlib/`: local TDLib Android library module with generated Java sources and a local JNI regeneration script.
- `android/app/src/main/kotlin/com/yonisirote/readmyfeed/tts/`: shared TTS models, engine, service, and playback helpers.
- `android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/x/speech/`: X-specific speech adapters and playback helpers.
- `android/app/src/main/res/`: layouts, strings, colors, drawables, and themes.
- `android/app/src/test/kotlin/`: JVM unit tests.

## Toolchain

- Android Gradle plugin: `8.7.2`
- Kotlin Android plugin: `2.1.20`
- Java/JVM target: `17`
- `minSdk 26`, `targetSdk 34`, `compileSdk 36`

## Command Guidance

- When using shell tools, prefer setting `workdir` to `android/` instead of chaining `cd`.
- Use the Gradle wrapper: `./gradlew`.
- After a fresh clone, or when TDLib JNI libs are missing, run `./tdlib/regenerate.sh` from `android/` before Gradle tasks that package the app.
- Prefer the smallest command that validates your change.
- Do not run overlapping `./gradlew` commands in parallel against the same `android/` worktree; chain them sequentially, especially after Kotlin file or package moves.
- After meaningful Android code or resource changes, finish with `assembleDebug`.

## Build Commands

- Prepare TDLib JNI libs after a fresh clone: `./tdlib/regenerate.sh`
- Build debug APK: `./gradlew assembleDebug`
- Clean build outputs: `./gradlew clean`
- Show available tasks: `./gradlew tasks`
- Show task help: `./gradlew help --task assembleDebug`

## Lint Commands

- Run Android lint: `./gradlew lintDebug`
- Task help: `./gradlew help --task lintDebug`

## Test Commands

- Run all JVM unit tests: `./gradlew testDebugUnitTest`
- Run one class: `./gradlew testDebugUnitTest --tests 'com.yonisirote.readmyfeed.providers.x.timeline.XTimelineParserTest'`
- Run one method: `./gradlew testDebugUnitTest --tests 'com.yonisirote.readmyfeed.providers.x.timeline.XTimelineParserTest.parsesTimelineTweetsAndNextCursor'`
- Run a package slice: `./gradlew testDebugUnitTest --tests 'com.yonisirote.readmyfeed.tts.*'`
- Run multiple filters: `./gradlew testDebugUnitTest --tests 'com.yonisirote.readmyfeed.providers.x.auth.*' --tests 'com.yonisirote.readmyfeed.providers.x.speech.*'`
- Re-run cached tests: `./gradlew testDebugUnitTest --rerun`
- Stop on first failure: `./gradlew testDebugUnitTest --fail-fast`
- Task help: `./gradlew help --task testDebugUnitTest`

## Useful Current Test Targets

- `com.yonisirote.readmyfeed.providers.x.timeline.XTimelineParserTest`
- `com.yonisirote.readmyfeed.providers.x.timeline.XTimelinePaginationTest`
- `com.yonisirote.readmyfeed.providers.x.auth.XAuthUtilsTest`
- `com.yonisirote.readmyfeed.providers.x.auth.XLoginCaptureCoordinatorTest`
- `com.yonisirote.readmyfeed.tts.TtsServiceTest`
- `com.yonisirote.readmyfeed.providers.x.speech.XTimelineSpeechPlayerTest`

## Expected Verification Flow

- Parser, auth, pagination, or TTS helper change: run the narrowest `testDebugUnitTest --tests ...` command.
- UI, layout, activity, or resource change: run the narrowest relevant tests, then `assembleDebug`.
- If you cannot run a check, say so clearly in the final message.

## Kotlin Style

- Use Kotlin for all app logic.
- Use 2-space indentation, matching the existing codebase.
- Wrap long argument lists instead of cramming them onto one line.
- Use trailing commas in multiline declarations and calls when it improves diffs and matches the surrounding file.
- Prefer `val` over `var`.
- Give public functions and non-trivial top-level helpers explicit return types.
- Prefer data classes for immutable models and plain classes for stateful services.
- Prefer top-level functions for stateless helpers and parsing utilities.
- Avoid wildcard imports.
- Let Kotlin/IDE default import ordering win.

## Naming

- Types, enums, objects, and test classes: `UpperCamelCase`
- Functions, properties, and local variables: `lowerCamelCase`
- Constants: `SCREAMING_SNAKE_CASE`
- Resource names: `snake_case`
- Layout/view ids should be descriptive, e.g. `feedRecyclerView`, `playFeedButton`, `statusTitleTextView`.
- Test names should describe behavior, not implementation trivia.

## Architecture Conventions

- Keep Android framework code thin.
- UI classes should coordinate views, lifecycle, and user actions; move parsing, network, auth, and speech logic into focused classes.
- Keep X auth and session concerns in `x/auth`.
- Keep X request, parsing, and pagination concerns in `x/timeline`.
- Keep shared speech logic in `tts` and platform-specific speech adapters in `x/speech`.
- Prefer composition and mapping adapters over inheritance across services.
- Do not pass raw JSON outside the timeline parsing boundary.
- Treat X payloads as unstable and untrusted; parse defensively and cap traversal work.
- Screen-scoped TTS is the current design. Do not expand it into background playback unless the task explicitly calls for it.

## Types and Nullability

- Model absence explicitly with nullable types only when the value is genuinely optional.
- Use empty strings and lists only when the existing model already follows that pattern and changing it would be breaking.
- Prefer app-owned enums or booleans over stringly typed state.
- Normalize unstable input before it reaches UI code.

## Error Handling

- Prefer feature-specific exceptions with a `code` and optional `context` map.
- Follow existing patterns such as `XAuthException`, `XTimelineException`, and `TtsException`.
- Wrap low-level exceptions near the boundary where they occur.
- Convert transport, parsing, and platform failures into domain errors before they reach UI code.
- Do not log, persist, or expose raw cookies, tokens, or other secrets beyond the session layer.
- If a session is clearly invalid, clear stored session state near the auth/timeline boundary.

## Coroutines and Threading

- Use coroutines for async work.
- UI, WebView, and view-binding updates belong on the main thread.
- Network, parsing, and blocking work should run on `Dispatchers.IO` when appropriate.
- Cancel screen-scoped jobs in lifecycle teardown.
- Do not block the main thread with network or parsing work.

## XML and Resources

- Put user-facing copy in `res/values/strings.xml`.
- Avoid hardcoding user-visible strings in Kotlin unless the text is short-lived and localization is unnecessary.
- Reuse existing colors, drawables, and theme values before adding new ones.
- Keep layout structures readable and ids descriptive.
- Prefer view binding over `findViewById` for active screens.

## Testing Style

- Use JUnit4 under `android/app/src/test/kotlin`.
- Prefer JVM tests over instrumentation tests unless Android framework behavior is the actual subject.
- Keep small fakes and stubs inside the test file when that keeps the test focused.
- Use `runBlocking` for coroutine tests unless the existing file already uses another pattern.
- Add focused contract tests for parser, auth, pagination, adapter, and TTS behavior changes.
- When fixing a bug, add or update the narrowest regression test you can.

## Documentation and References

- Keep `README.md` in sync with the actual native app behavior.
- Treat the `expo-version` branch as behavior reference, not runtime code.
- Port behavior from Expo only when it still fits the native architecture.

## Git and Change Safety

- Do not revert user changes unless explicitly asked.
- Do not use destructive git operations without explicit written instruction.
- Do not amend commits unless explicitly asked.
- If the worktree is dirty, preserve unrelated changes and work around them.

## Before Finishing

- Mention any intentionally deferred migration pieces.
- Mention checks you ran.
- Mention checks you could not run.
- If you changed commands or workflow, update both `AGENTS.md` and `README.md`.
