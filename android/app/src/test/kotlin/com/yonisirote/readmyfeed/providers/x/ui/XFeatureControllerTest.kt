package com.yonisirote.readmyfeed.providers.x.ui

import androidx.appcompat.app.AppCompatActivity
import com.yonisirote.readmyfeed.R
import com.yonisirote.readmyfeed.databinding.ActivityMainBinding
import com.yonisirote.readmyfeed.providers.x.auth.XAuthCaptureResult
import com.yonisirote.readmyfeed.providers.x.auth.XAuthService
import com.yonisirote.readmyfeed.providers.x.auth.XCookieReadResult
import com.yonisirote.readmyfeed.providers.x.auth.XCookieReader
import com.yonisirote.readmyfeed.providers.x.auth.XLoginCaptureCoordinator
import com.yonisirote.readmyfeed.providers.x.auth.XSessionStore
import com.yonisirote.readmyfeed.providers.x.speech.XTimelineSpeechPlayer
import com.yonisirote.readmyfeed.providers.x.timeline.XTimelineService
import com.yonisirote.readmyfeed.shell.AppScreen
import com.yonisirote.readmyfeed.shell.AppScreenHost
import com.yonisirote.readmyfeed.shell.TelegramDestination
import com.yonisirote.readmyfeed.shell.XDestination
import com.yonisirote.readmyfeed.tts.TtsEngine
import com.yonisirote.readmyfeed.tts.TtsService
import com.yonisirote.readmyfeed.tts.TtsSpeakOptions
import com.yonisirote.readmyfeed.tts.TtsVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class XFeatureControllerTest {
  @Test
  fun supportsConnectAndContentListScreens() {
    val setup = buildController()

    assertTrue(setup.controller.supports(AppScreen.XScreen(XDestination.CONNECT)))
    assertTrue(setup.controller.supports(AppScreen.XScreen(XDestination.CONTENT_LIST)))
    assertFalse(setup.controller.supports(AppScreen.TelegramScreen(TelegramDestination.CHAT_LIST)))
  }

  @Test
  fun renderShowsOnlyRequestedXScreen() {
    val setup = buildController()

    setup.controller.render(AppScreen.XScreen(XDestination.CONNECT))
    assertTrue(setup.binding.xSignInScreen.isShown)
    assertFalse(setup.binding.feedScreen.isShown)

    setup.controller.render(AppScreen.XScreen(XDestination.CONTENT_LIST))
    assertTrue(setup.binding.feedScreen.isShown)
    assertFalse(setup.binding.xSignInScreen.isShown)
  }

  @Test
  fun handleBackPressFromContentListReturnsHome() {
    val setup = buildController()
    setup.controller.render(AppScreen.XScreen(XDestination.CONTENT_LIST))

    assertTrue(setup.controller.handleBackPress())
    assertEquals(
      AppScreen.Home,
      setup.screenHost.shownScreens.last(),
    )
  }

  private fun buildController(): ControllerSetup {
    val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
    activity.setTheme(R.style.Theme_ReadMyFeed)
    val binding = ActivityMainBinding.inflate(activity.layoutInflater)
    activity.setContentView(binding.root)
    val screenHost = RecordingScreenHost()
    val authService = XAuthService(
      cookieReader = FakeXCookieReader(),
      sessionStore = FakeXSessionStore(),
    )
    val ttsService = TtsService(FakeTtsEngine())
    val controller = XFeatureController(
      activity = activity,
      binding = binding,
      screenHost = screenHost,
      dependenciesFactory = {
        XFeatureDependencies(
          authService = authService,
          captureCoordinator = XLoginCaptureCoordinator(authService),
          timelineService = XTimelineService(authService),
          timelineSpeechPlayer = XTimelineSpeechPlayer(ttsService),
          feedAdapter = XTimelineFeedAdapter(),
        )
      },
    )
    assertTrue(controller.initialize())

    return ControllerSetup(
      activity = activity,
      binding = binding,
      screenHost = screenHost,
      controller = controller,
    )
  }

  private data class ControllerSetup(
    val activity: AppCompatActivity,
    val binding: ActivityMainBinding,
    val screenHost: RecordingScreenHost,
    val controller: XFeatureController,
  )

  private class RecordingScreenHost : AppScreenHost {
    val shownScreens = mutableListOf<AppScreen>()

    override fun showScreen(screen: AppScreen) {
      shownScreens += screen
    }
  }

  private class FakeXCookieReader : XCookieReader {
    override fun readCookies(): XCookieReadResult {
      return XCookieReadResult(
        cookies = emptyMap(),
        missingRequired = emptyList(),
        missingOptional = emptyList(),
        hasRequired = false,
      )
    }
  }

  private class FakeXSessionStore : XSessionStore {
    private var cookieString: String? = null

    override fun get(): String? {
      return cookieString
    }

    override fun set(cookieString: String) {
      this.cookieString = cookieString
    }

    override fun clear() {
      cookieString = null
    }
  }

  private class FakeTtsEngine : TtsEngine {
    override suspend fun initialize() = Unit

    override fun voices(): List<TtsVoice> = emptyList()

    override suspend fun speak(text: String, options: TtsSpeakOptions) = Unit

    override fun stop() = Unit

    override fun shutdown() = Unit
  }
}
