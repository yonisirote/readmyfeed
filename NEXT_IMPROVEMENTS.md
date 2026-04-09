# Next Improvements

Implemented already:

- Make `TelegramClientManager` snapshot publication atomic so interleaved TDLib callbacks cannot overwrite newer state.
- Extract shared playback screen orchestration into `ScreenPlaybackController` and wire X/Telegram screens onto it.

Recommended next improvements:

## 1. Narrow Feature UI Dependencies

- `android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/x/ui/XFeatureController.kt`
- `android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/telegram/ui/TelegramFeatureController.kt`
- `android/app/src/main/kotlin/com/yonisirote/readmyfeed/shell/MainActivity.kt`

Problem:

- Provider controllers receive the full `ActivityMainBinding`, so each feature can reach unrelated views owned by the shell or another provider.

Improve by:

- Passing per-feature view bindings or thin view wrappers instead of the whole activity binding.

Expected payoff:

- Stronger provider boundaries.
- Lower accidental coupling between shell, X, and Telegram screens.
- Smaller controller test setup.

## 3. Reduce Telegram Re-render Fan-out

Scope:

- `android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/telegram/ui/TelegramFeatureController.kt`

Problem:

- Every snapshot update is pushed to all Telegram screen controllers, even when only one visible screen or one state slice changed.

Improve by:

- Deriving smaller render models per screen.
- Rendering the visible screen on every update and only re-rendering hidden screens when their inputs actually changed.

Expected payoff:

- Clearer UI update flow.
- Less incidental coupling to the monolithic snapshot.
- Easier screen-specific testing.

## 4. Extract X Timeline State Coordination

Scope:

- `android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/x/ui/XTimelineScreenController.kt`

Problem:

- The controller owns pagination state, fetch orchestration, error handling, screen navigation, and playback state in one class.

Improve by:

- Extracting a focused timeline state coordinator or reducer for fetch, append, cursor, and reconnect logic.
- Leaving the controller primarily responsible for binding updates and click listeners.

Expected payoff:

- Smaller controller surface area.
- More direct tests around pagination and error transitions.
- Lower risk when changing fetch behavior.

## 5. Longer-term Telegram Decomposition

Scope:

- `android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/telegram/client/TelegramClientManager.kt`

Problem:

- Even with atomic snapshot updates fixed, the class still combines TDLib lifecycle, auth transitions, chat caching, selected-message caching, and snapshot publication.

Improve by:

- Splitting lifecycle/auth/chat/message responsibilities into smaller collaborators once behavior stabilizes.
- Keeping `TelegramClientManager` as a thin composition layer if needed.

Expected payoff:

- Easier reasoning about callback paths.
- Smaller, more focused tests.
- Lower merge-conflict risk in the busiest Telegram file.

## Test Focus For The Next Round

- Add UI-facing playback tests around cancellation/progress edge cases if shared playback orchestration changes again.
- Add Telegram feature-controller tests that verify only the expected screen reacts to snapshot transitions.
- Add X timeline tests around reconnect, append, and playback interruption if state coordination is extracted.
