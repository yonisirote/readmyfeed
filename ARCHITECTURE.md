# Architecture

## Kotlin packages

- `android/app/src/main/kotlin/com/yonisirote/readmyfeed/shell/`
  - app-wide navigation and shell coordination
  - `MainActivity`, screen models, and screen normalization helpers
- `android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/`
  - provider registry/factory contracts and provider-specific feature code
  - `providers/x/` contains X auth, timeline, speech, and UI code
  - `providers/telegram/` contains Telegram auth, TDLib client, speech, and UI code
- `android/app/src/main/kotlin/com/yonisirote/readmyfeed/tts/`
  - shared TTS engine, playback controller, and speakable models reused across providers

Tests mirror the same package structure under `android/app/src/test/kotlin/com/yonisirote/readmyfeed/`.

## Resource layout conventions

- `android/app/src/main/res/layout/activity_main.xml`
  - current composition root for home, X, Telegram, and shared loading UI
- `item_x_*.xml`
  - X-specific row/item layouts
- `item_telegram_*.xml`
  - Telegram-specific row/item layouts
- descriptive view ids inside `activity_main.xml`
  - keep screen ownership obvious even while the main screen layout stays in one file

## Placement rules

- If code or UI is used by exactly one provider, keep it inside that provider package.
- If it coordinates cross-provider navigation, keep it in `shell/`.
- If it is reused across providers, keep it shared and explicit, like `tts/`.
- Prefer resource names that show ownership (`screen_x_*`, `screen_telegram_*`) instead of generic names.
