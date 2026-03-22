# Code Review — readmyfeed

## Resolved Since Last Review

1. **resolved** — [XFeatureController.kt](android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/x/ui/XFeatureController.kt#L18-L187), [XSignInScreenController.kt](android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/x/ui/XSignInScreenController.kt#L27-L248), [XTimelineScreenController.kt](android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/x/ui/XTimelineScreenController.kt#L28-L399): The old X god class has been split into a thin coordinator plus dedicated sign-in and timeline/speech controllers.

2. **resolved** — [TelegramFeatureController.kt](android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/telegram/ui/TelegramFeatureController.kt#L31-L363), [TelegramConnectScreenController.kt](android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/telegram/ui/TelegramConnectScreenController.kt#L13-L276), [TelegramChatListScreenController.kt](android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/telegram/ui/TelegramChatListScreenController.kt#L12-L106), [TelegramMessageScreenController.kt](android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/telegram/ui/TelegramMessageScreenController.kt#L18-L233): The old Telegram god class has been split into focused screen controllers plus a smaller coordinator.

3. **resolved** — [XFeatureController.kt](android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/x/ui/XFeatureController.kt#L18-L45), [XFeatureDependencies.kt](android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/x/ui/XFeatureDependencies.kt#L13-L34): X dependency construction is now injected through a factory seam instead of being hardcoded inside the controller, so the earlier X testability concern is addressed.

## Remaining Issues (triaged)

1. **lifecycle (high)** — [MainActivity.kt](android/app/src/main/kotlin/com/yonisirote/readmyfeed/shell/MainActivity.kt#L23-L53), [XTimelineScreenController.kt](android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/x/ui/XTimelineScreenController.kt#L41-L49), [TelegramFeatureController.kt](android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/telegram/ui/TelegramFeatureController.kt#L184-L223): **No configuration-change or process-death survival.** Provider state still lives in activity-scoped controllers, `savedInstanceState` is unused, and `onDestroy()` tears everything down on rotation. Users can still lose loaded X feed state, Telegram auth/chat selection, and in-flight playback when the activity is recreated.

2. **concurrency (high)** — [TelegramClientManager.kt](android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/telegram/client/TelegramClientManager.kt#L60-L75), [TelegramClientManager.kt](android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/telegram/client/TelegramClientManager.kt#L103-L111), [TelegramClientManager.kt](android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/telegram/client/TelegramClientManager.kt#L817-L867): **Mixed locking model.** `synchronized(lock)` protects chat/user/message maps and `selectedChatId`, but `snapshotState.value` reads and writes still happen outside that lock. TDLib callbacks can race with snapshot copies, so callers can observe a snapshot built from inconsistent old/new state.

3. **architecture (high)** — [activity_main.xml](android/app/src/main/res/layout/activity_main.xml#L1-L1274): **Monolithic layout.** Home, X sign-in, X feed, Telegram connect, Telegram chat list, Telegram messages, and the loading overlay still live in one 1274-line XML file. Every screen is inflated up front, and future providers will continue to compound the file size and maintenance cost.

4. **resource (high)** — [XTimelineService.kt](android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/x/timeline/XTimelineService.kt#L16-L19), [XFeatureDependencies.kt](android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/x/ui/XFeatureDependencies.kt#L21-L33): **New `OkHttpClient()` per X feature dependency graph.** `XTimelineService` still defaults to a fresh client, and `createXFeatureDependencies()` does not inject a shared singleton. Activity recreation can therefore strand extra OkHttp dispatchers/connection pools.

5. **architecture (medium)** — [XTimelineScreenController.kt](android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/x/ui/XTimelineScreenController.kt#L301-L371) and [TelegramMessageScreenController.kt](android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/telegram/ui/TelegramMessageScreenController.kt#L162-L233): **Near-identical speech playback orchestration.** The duplication is smaller and better isolated now, but both controllers still carry almost the same start/stop/progress/error flow.

6. **reliability (medium)** — [XSignInScreenController.kt](android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/x/ui/XSignInScreenController.kt#L98-L112): `mixedContentMode = MIXED_CONTENT_COMPATIBILITY_MODE` still permits mixed HTTP/HTTPS subresources during X login. This weakens the WebView login surface and should be tightened to `MIXED_CONTENT_NEVER_ALLOW` unless there is a proven compatibility blocker.

7. **security (medium)** — [XTimelineRequest.kt](android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/x/timeline/XTimelineRequest.kt#L11-L13): The X web bearer token is still embedded as a plain constant. It is not a per-user secret, but baking it into the APK makes automated client fingerprinting easier and leaves no room for runtime rotation.

8. **reliability (medium)** — [SpeakablePlaybackController.kt](android/app/src/main/kotlin/com/yonisirote/readmyfeed/tts/SpeakablePlaybackController.kt#L19-L82): `SpeakablePlaybackController.speak()` still has no explicit cancellation checkpoint between items. Cancellation usually propagates through downstream suspensions, but the contract remains implicit and fragile.

9. **design (medium)** — [XTimelineSpeechPlayer.kt](android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/x/speech/XTimelineSpeechPlayer.kt#L19-L32) and [TelegramMessageSpeechPlayer.kt](android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/telegram/speech/TelegramMessageSpeechPlayer.kt#L19-L32): Both speech adapters still eagerly map entire lists into `SpeakableEntry` collections, creating avoidable copies for large timelines/chat histories.

10. **design (low)** — [ProviderFeatureRegistry.kt](android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/ProviderFeatureRegistry.kt#L44-L48): `render()` still broadcasts every screen change to every active provider controller. The current app is small enough that this is not a hot path, but it remains semantically confusing and scales poorly if more providers are added.

11. **design (low)** — [resolveHomeProviderCardState](android/app/src/main/kotlin/com/yonisirote/readmyfeed/shell/MainActivity.kt#L137-L204): The home card state builder still duplicates nearly identical branches for `available=true` and `available=false`, especially for `WHATSAPP` and `FACEBOOK`. This should collapse into shared provider metadata plus a small availability switch.

12. **design (low)** — [TelegramClientManager.kt](android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/telegram/client/TelegramClientManager.kt#L302-L314): `start()`/`close()`/`restart()` semantics are still fuzzy. Callers cannot distinguish first start from restart-after-close, and `close()` followed by `start()` silently resets cached state.

13. **design (low)** — [AppScreen.kt](android/app/src/main/kotlin/com/yonisirote/readmyfeed/shell/AppScreen.kt#L42-L55): `matchesProviderDestination` and `matchesProvider` still duplicate the same comparison logic with different receivers, which adds API surface without real leverage.

14. **reliability (low)** — [XTimelineFeedAdapter.kt](android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/x/ui/XTimelineFeedAdapter.kt#L146-L149): `numberFormatter` and `timelineDateFormatter` still capture `Locale.getDefault()` once at class load, so runtime locale changes are ignored until process restart.

15. **design (low)** — [FeedProvider.kt](android/app/src/main/kotlin/com/yonisirote/readmyfeed/providers/FeedProvider.kt#L5-L30): `FeedProvider.isAvailable` remains a compile-time enum constant, so future runtime feature flags or remote-config rollout would still require a structural refactor.

## Deprioritized / Closed After Re-triage

- The previous X mutable-state race concern is no longer a convincing issue in the current structure. After the controller split, the remaining X state is managed from main-thread UI callbacks and coroutine resumptions, so I do not see a concrete cross-thread race worth keeping on the active list.

- The previous `ProviderFeatureRegistry.handleBackPress()` ordering concern is not currently reproducible. Each controller normalizes unsupported screens to `AppScreen.Home` during `render()`, so only the active provider generally returns `true` on back press.
