package com.yonisirote.readmyfeed.providers.x.ui

import androidx.appcompat.app.AppCompatActivity
import com.yonisirote.readmyfeed.providers.x.auth.AndroidWebViewCookieReader
import com.yonisirote.readmyfeed.providers.x.auth.PreferencesXSessionStore
import com.yonisirote.readmyfeed.providers.x.auth.XAuthService
import com.yonisirote.readmyfeed.providers.x.auth.XLoginCaptureCoordinator
import com.yonisirote.readmyfeed.providers.x.speech.XTimelineSpeechPlayer
import com.yonisirote.readmyfeed.providers.x.timeline.XTimelineService
import com.yonisirote.readmyfeed.tts.AndroidTtsEngine
import com.yonisirote.readmyfeed.tts.TtsService

internal data class XFeatureDependencies(
  val authService: XAuthService,
  val captureCoordinator: XLoginCaptureCoordinator,
  val timelineService: XTimelineService,
  val timelineSpeechPlayer: XTimelineSpeechPlayer,
  val feedAdapter: XTimelineFeedAdapter,
)

internal fun createXFeatureDependencies(activity: AppCompatActivity): XFeatureDependencies {
  val cookieReader = AndroidWebViewCookieReader()
  val sessionStore = PreferencesXSessionStore(activity.applicationContext)
  val authService = XAuthService(cookieReader, sessionStore)
  val ttsService = TtsService(AndroidTtsEngine(activity.applicationContext))

  return XFeatureDependencies(
    authService = authService,
    captureCoordinator = XLoginCaptureCoordinator(authService),
    timelineService = XTimelineService(authService),
    timelineSpeechPlayer = XTimelineSpeechPlayer(ttsService),
    feedAdapter = XTimelineFeedAdapter(),
  )
}
