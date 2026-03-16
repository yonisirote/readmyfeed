# ReadMyFeed

`readmyfeed` is now a native Android app written in Kotlin.

The active code lives under `android/`. The old Expo/React Native codebase still exists on the `expo-version` branch and should be treated as reference material only.

## Current Status

- Native Android project lives in `android/`
- X session storage, timeline request building, parsing, and pagination are migrated
- Native WebView login and cookie capture flow are implemented
- Native X feed UI is implemented with `RecyclerView`
- Screen-scoped TTS can read the loaded tweets aloud
- Telegram migration and background playback are still pending

## Development Commands

Run Gradle from `android/`:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

Useful targeted test commands:

```bash
./gradlew testDebugUnitTest --tests 'com.yonisirote.readmyfeed.x.timeline.XTimelineParserTest'
./gradlew testDebugUnitTest --tests 'com.yonisirote.readmyfeed.x.timeline.XTimelineParserTest.parsesTimelineTweetsAndNextCursor'
./gradlew testDebugUnitTest --tests 'com.yonisirote.readmyfeed.tts.*'
```

## Project Layout

```text
android/
  app/
    src/main/kotlin/com/yonisirote/readmyfeed/
      MainActivity.kt      # Native X flow and feed playback screen
      x/auth/              # Session, cookie, WebView capture, and auth helpers
      x/timeline/          # Request building, parser, and pagination
      tts/                 # Native TTS models, engine, service, and helpers
      tts/x/               # X-to-speech adapters and playback helpers
    src/main/res/          # Layouts, strings, drawables, theme resources
    src/test/kotlin/       # JVM unit tests
```

## Notes

- The current X login path is still cookie-based and depends on the embedded WebView flow.
- Username/email + password is the expected path; Google or other SSO flows are not supported here.
- TTS is intentionally screen-scoped right now. There is no foreground-service or background playback flow yet.
- `AGENTS.md` contains repository-specific guidance for coding agents, including style rules and single-test command examples.
