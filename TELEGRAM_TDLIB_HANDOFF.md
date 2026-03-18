# Telegram TDLib Handoff

This note is for the next agent who continues Telegram work in this repo.

## Current app architecture

- The app shell is now provider-oriented.
- X is fully implemented.
- Telegram now has a real provider controller, native auth/connect flow, native main chat list, selected-chat unread loading, and on-screen message playback.
- `android/app/src/main/kotlin/com/yonisirote/readmyfeed/shell/MainActivity.kt` is a thin shell.
- Provider integration goes through:
  - `android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/ProviderFeatureController.kt`
  - `android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/ProviderFeatureRegistry.kt`
  - `android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/ProviderFeatureFactory.kt`
- X is the reference implementation:
  - `android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/x/ui/XFeatureController.kt`
- Telegram provider integration currently centers on:
  - `android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/telegram/ui/TelegramFeatureController.kt`
  - `android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/telegram/client/TelegramClientManager.kt`
  - `android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/telegram/auth/TelegramAuthStateMapper.kt`
- TDLib packaging now lives in:
  - `android/tdlib/`
- Shared TTS is reusable for Telegram:
  - `android/app/src/main/kotlin/com/yonisirote/readmyfeed/tts/SpeakableItem.kt`
  - `android/app/src/main/kotlin/com/yonisirote/readmyfeed/tts/SpeakablePlaybackController.kt`

## Telegram status right now

- Telegram is partially implemented.
- `FeedProvider.TELEGRAM` is now enabled in `android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/FeedProvider.kt`.
- The Telegram home card is clickable and routes into a native Telegram connect screen in `android/app/src/main/res/layout/activity_main.xml`.
- Telegram foundation packages now exist under `android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/telegram/`.
- A local TDLib module is now wired into Gradle at `android/tdlib/`.
- `android/settings.gradle` includes `:tdlib`, and `android/app/build.gradle` depends on it.
- `telegramApiId` and `telegramApiHash` are read from `android/local.properties` and exposed via `BuildConfig.TELEGRAM_API_ID` / `BuildConfig.TELEGRAM_API_HASH`.
- Telegram credential validation is deferred until Telegram client code actually initializes, so unrelated X/debug builds still work on fresh clones.
- A long-lived TDLib wrapper now exists at `android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/telegram/client/TelegramClientManager.kt`.
- Auth-state mapping now exists at `android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/telegram/auth/TelegramAuthStateMapper.kt`.
- A real provider controller now exists at `android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/telegram/ui/TelegramFeatureController.kt`.
- Telegram connect/auth UI now routes phone, email, code, password, QR, QR-to-phone fallback, and ready states through the provider shell.
- The Telegram connected destination is now a real native chat list screen in `android/app/src/main/res/layout/activity_main.xml` (`telegramChatListScreen`).
- `TelegramFeatureController` automatically navigates from connect -> chat list when TDLib reaches `AuthorizationStateReady`.
- `TelegramClientManager` now loads chats with `loadChats` and applies main-list updates for new chats, ordering, titles, last messages, unread counts, and marked-unread state.
- Chat previews are surfaced as sorted `TelegramChatPreview` models and rendered with a native `RecyclerView` adapter.
- While QR login is active, the connect screen now shows a dedicated `Use phone number instead` action that returns the auth flow to phone login without forcing a full restart.
- Chat rows navigate into a selected chat screen that loads unread messages and supports on-screen TTS playback.
- App-owned Telegram foundation models already exist for future work in:
  - `android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/telegram/chats/TelegramChatModels.kt`
  - `android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/telegram/messages/TelegramMessageModels.kt`
  - `android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/telegram/speech/TelegramMessageSpeakableAdapter.kt`

## Credentials / source control status

- `android/local.properties` is already ignored by `.gitignore`.
- It is not tracked by git.
- Verified with:
  - `git ls-files --error-unmatch "android/local.properties"` -> not tracked
  - `git status --short --ignored "android/local.properties"` -> `!! android/local.properties`
- Do not commit `android/local.properties`.
- If Telegram auth fails immediately, verify `telegramApiId` and `telegramApiHash` in `android/local.properties` first.

## Recommendation: do not use a random remote TDLib wrapper

Recommended approach:

- Build upstream TDLib for Android.
- Package it as a local module in this repo, for example `android/tdlib/`.
- Use the Java/JNI interface (`org.drinkless.tdlib.Client` + `TdApi`) rather than inventing a custom network layer.

Why:

- I did not find a trustworthy, current, official Android-ready Maven dependency that cleanly solves packaging.
- The practical path is upstream TDLib Android build output: generated Java sources plus `.so` libraries.

## TDLib exploration already done

- Upstream TDLib repo was cloned temporarily to `/tmp/readmyfeed-tdlib-src` for build and packaging work.
- The packaged module in this repo was built from upstream commit `0ae923c493bceb75433de2682ba8ae29cc7bf88d`.
- Useful upstream files:
  - `/tmp/readmyfeed-tdlib-src/example/android/README.md`
  - `/tmp/readmyfeed-tdlib-src/example/android/build-openssl.sh`
  - `/tmp/readmyfeed-tdlib-src/example/android/build-tdlib.sh`
  - `/tmp/readmyfeed-tdlib-src/example/java/org/drinkless/tdlib/example/Example.java`
- The upstream Android build scripts expect:
  - Android SDK with platform `android-34`
  - CMake `3.22.1`
  - Bash tools including `gperf`, `php`, `perl`, `make`, `tar`, `unzip`, etc.

## Machine state discovered during exploration

- Android SDK exists at `/home/yoni/Android/Sdk`.
- Installed NDK versions:
  - `26.1.10909125`
  - `27.1.12297006`
- Installed CMake version:
  - `/home/yoni/Android/Sdk/cmake/3.22.1`

## TDLib packaging status

- `gperf` and `php` were installed locally so the upstream Android scripts can run.
- `/tmp/readmyfeed-tdlib-src/example/android/check-environment.sh` now passes.
- Upstream `build-openssl.sh` succeeded with Android NDK `26.1.10909125`.
- Upstream `build-tdlib.sh` succeeded for the Java/JNI interface with `c++_static`.
- Packaged TDLib outputs now live in:
  - `android/tdlib/src/main/java/org/drinkless/tdlib/Client.java`
  - `android/tdlib/src/main/java/org/drinkless/tdlib/TdApi.java`
  - local `android/tdlib/src/main/jniLibs/<abi>/libtdjni.so` outputs generated by `android/tdlib/regenerate.sh`
  - `android/tdlib/LICENSE_1_0.txt`

- The JNI libraries are no longer meant to stay tracked in git. Rebuild them locally after a fresh clone with:
  - `cd android && ./tdlib/regenerate.sh`

## Recommended implementation order

1. [done] Fix TDLib native build prerequisites
   - `gperf` and `php` are available locally.
   - The upstream TDLib Android environment check passes.

2. [done] Build TDLib for Android
   - Built with the upstream Android scripts using the Java/JNI interface.
   - OpenSSL and TDLib outputs were generated successfully.

3. [done] Add a local TDLib module
   - Local Gradle module now lives at `android/tdlib/`.
   - Generated Java files and JNI libs are packaged there.
   - The app module already depends on `project(":tdlib")`.

4. [done] Wire credentials into `BuildConfig`
   - `android/app/build.gradle` now reads `telegramApiId` and `telegramApiHash` from `android/local.properties`.
   - They are exposed via `BuildConfig.TELEGRAM_API_ID` and `BuildConfig.TELEGRAM_API_HASH`.
   - Missing or invalid values now surface as a clear app-owned configuration error when Telegram client code is initialized.

5. [done] Add Telegram foundation packages
   - `telegram/client/`
   - `telegram/auth/`
   - `telegram/chats/`
   - `telegram/messages/`
   - `telegram/speech/`
   - `telegram/ui/`

6. [done] Build a long-lived TDLib wrapper first
   - `TelegramClientManager` now lives under `telegram/client/`.
   - Raw TDLib authorization updates are mapped into app-owned state.
   - `MainActivity` still does not talk to TDLib directly.

7. [done] Implement auth flow
   - Map TDLib auth states to screen states:
      - wait for parameters
      - wait for phone number
      - wait for code
      - wait for password
      - ready
      - error
   - The manager supports phone number, email address, email code, auth code, password, QR auth requests, QR-to-phone fallback, logout, and close.
   - `TelegramFeatureController` now wires those manager entry points into the native provider UI.

8. [done] Add `TelegramFeatureController`
   - The controller mirrors the provider-shell integration style without copying the X WebView flow.
   - It is plugged into `ProviderFeatureFactory.kt`.
   - `FeedProvider.TELEGRAM` is now enabled because the controller is real.

9. [done] Add chat list flow
   - Replace `telegramPlaceholderScreen` with a real Telegram chat list UI.
   - Extend the Telegram client layer to load chats and react to TDLib chat updates.
   - Show chats with title and unread count.
   - Sort according to TDLib chat ordering.

10. [done] Add selected chat unread reading
   - On chat selection, fetch recent messages.
   - Derive the unread, speakable subset.
   - Start with text and captions only.

11. [done] Reuse the shared TTS layer
   - Map Telegram messages to `SpeakableItem`.
   - Do not build a second speech engine.

12. Add tests at each boundary
   - auth state mapping
   - chat update handling
   - unread filtering
   - Telegram speech mapping
   - provider-shell integration

## Product defaults that were recommended

- UX can feel similar to X, but the implementation must follow TDLib's auth state machine.
- Initial playback behavior should probably be:
  - read the currently unread messages in the selected chat
  - do not automatically mark them as read in v1 unless explicitly decided later
- First version should ignore complex media/service-only messages and focus on text/caption content.

## Existing repo state to preserve

- There is already an uncommitted multi-provider shell refactor in the worktree.
- The key new files from that refactor include:
  - `android/app/src/main/kotlin/com/yonisirote/readmyfeed/shell/AppScreen.kt`
  - `android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/FeedProvider.kt`
  - `android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/ProviderFeatureController.kt`
  - `android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/ProviderFeatureRegistry.kt`
  - `android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/x/ui/XFeatureController.kt`
- There is now also a committed-in-worktree TDLib packaging module at `android/tdlib/`.
- The Telegram provider controller and connect-screen work now depend on:
  - `android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/telegram/ui/TelegramFeatureController.kt`
  - `android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/telegram/ui/TelegramConnectScreenState.kt`
  - `android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/telegram/client/TelegramClientManager.kt`
- Do not revert those changes unless the user explicitly asks.

## Checks already performed before pausing

- Full JVM unit suite passed earlier during the shell refactor stage.
- `assembleDebug` also passed earlier during the shell refactor stage.
- TDLib environment check now passes.
- Upstream OpenSSL Android build succeeded.
- Upstream TDLib Java/JNI Android build succeeded.
- `assembleDebug` passed after wiring Telegram credentials into `BuildConfig`.
- Focused Telegram JVM tests now cover auth-state mapping, wrapper behavior, connect-screen state, QR fallback behavior, chat update handling, chat list state mapping, chat preview mapping, and Telegram speech mapping.
- `assembleDebug` passes with Telegram provider/controller integration enabled.
- The app now has Telegram chat list loading, selected-chat unread message loading, and shared-TTS playback for loaded unread messages.

## Suggested next action when work resumes

Start with polish and follow-up behavior around the selected-chat message flow.

If the next agent has time for only one chunk, do this:

1. decide whether selected-chat playback should mark messages read or stay read-only
2. add pagination/load-more for older unread-capable Telegram messages if needed
3. support more Telegram content kinds beyond text and captions if product wants them
4. improve message sender resolution for more edge cases (channels, anonymous admins, unavailable senders)
5. keep background playback out unless explicitly chosen later

Only after that should read-state side effects or background playback be added.
