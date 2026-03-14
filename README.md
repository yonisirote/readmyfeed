# ReadMyFeed

`readmyfeed` is now an Android-native Kotlin project.

This branch is the clean native migration line. The old Expo codebase lives on `expo-version`.

## Current Status

- Expo/React Native app files were removed from `master`
- Native Android project lives in `android/`
- X core translation has started with Kotlin auth/session helpers, timeline request logic, parser, and pagination utilities
- Native WebView login flow, feed UI wiring, TTS, and Telegram migration are still pending

## Build

```bash
cd android
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## Project Layout

```text
android/
  app/
    src/main/kotlin/com/yonisirote/readmyfeed/
      x/auth/           # Session, cookie, and auth helpers
      x/timeline/       # Timeline request, parser, and pagination
    src/test/kotlin/    # JVM unit tests for migrated Kotlin logic
```

## Migration Notes

- X login capture via Android `WebView` is intentionally deferred because the current flow depends on timing-sensitive cookie capture heuristics.
- The first native pass focuses on the safe core: stored session handling, request construction, payload parsing, and pagination rules.
- After the X core is wired to a native screen, the next planned service migration is Telegram.
