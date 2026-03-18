# ReadMyFeed

`readmyfeed` is now a native Android app written in Kotlin.

The active code lives under `android/`. The old Expo/React Native codebase still exists on the `expo-version` branch and should be treated as reference material only.

## Current Status

- Native Android project lives in `android/`
- X session storage, timeline request building, parsing, and pagination are migrated
- Native WebView login and cookie capture flow are implemented
- Native X feed UI is implemented with `RecyclerView`
- Screen-scoped TTS can read the loaded tweets aloud
- A local TDLib module lives under `android/tdlib/`; JNI libs are regenerated locally with `./tdlib/regenerate.sh`
- Telegram auth, main chat list loading, selected chat unread message loading, and on-screen TTS playback now work through local TDLib
- Background playback and read-state side effects are still pending

## Development Commands

Run Gradle from `android/`:

```bash
./tdlib/regenerate.sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

On a fresh clone, or after deleting local TDLib binaries, run `./tdlib/regenerate.sh` once before the Gradle tasks above.

Useful targeted test commands:

```bash
./gradlew testDebugUnitTest --tests 'com.yonisirote.readmyfeed.providers.x.timeline.XTimelineParserTest'
./gradlew testDebugUnitTest --tests 'com.yonisirote.readmyfeed.providers.x.timeline.XTimelineParserTest.parsesTimelineTweetsAndNextCursor'
./gradlew testDebugUnitTest --tests 'com.yonisirote.readmyfeed.providers.telegram.client.TelegramClientManagerTest'
./gradlew testDebugUnitTest --tests 'com.yonisirote.readmyfeed.tts.*'
```

## Project Layout

```text
android/
  tdlib/                    # Local TDLib module and JNI regeneration script
  app/
    src/main/kotlin/com/yonisirote/readmyfeed/
      shell/               # Activity and app-shell screen navigation
      providers/           # Provider registry plus X/Telegram features
      tts/                 # Shared TTS models, engine, service, and playback helpers
    src/main/res/          # Layouts, strings, drawables, theme resources
    src/test/kotlin/       # JVM unit tests
```

When validating changes, run Gradle tasks sequentially in the same `android/` worktree. Avoid starting overlapping `./gradlew` commands at the same time, especially after Kotlin file or package moves.

## Notes

- The current X login path is still cookie-based and depends on the embedded WebView flow.
- Username/email + password is the expected path; Google or other SSO flows are not supported here.
- TTS is intentionally screen-scoped right now. There is no foreground-service or background playback flow yet.
- `android/tdlib/src/main/jniLibs/` is local build output from `./tdlib/regenerate.sh` and is intentionally ignored by git.
- `AGENTS.md` contains repository-specific guidance for coding agents, including style rules and single-test command examples.
