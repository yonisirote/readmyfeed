package com.yonisirote.readmyfeed.providers.x.ui

import android.os.Looper
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
import com.yonisirote.readmyfeed.shell.ProviderDestination
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
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
class XFeatureControllerTest {
  @Test
  fun hideClearsXScreens() {
    val setup = buildController()

    setup.controller.render(AppScreen.XScreen(ProviderDestination.Connect))
    setup.controller.hide()

    assertFalse(setup.binding.xSignInScreen.isShown)
    assertFalse(setup.binding.feedScreen.isShown)
  }

  @Test
  fun renderShowsOnlyRequestedXScreen() {
    val setup = buildController()

    setup.controller.render(AppScreen.XScreen(ProviderDestination.Connect))
    assertTrue(setup.binding.xSignInScreen.isShown)
    assertFalse(setup.binding.feedScreen.isShown)

    setup.controller.render(AppScreen.XScreen(XDestination.ContentList))
    assertTrue(setup.binding.feedScreen.isShown)
    assertFalse(setup.binding.xSignInScreen.isShown)
  }

  @Test
  fun handleBackPressFromContentListReturnsHome() {
    val setup = buildController()
    setup.controller.render(AppScreen.XScreen(XDestination.ContentList))

    assertTrue(setup.controller.handleBackPress())
    assertEquals(
      AppScreen.Home,
      setup.shownScreens.last(),
    )
  }

  @Test
  fun handleBackPressReturnsFalseWhenInactive() {
    val setup = buildController()

    assertFalse(setup.controller.handleBackPress())
  }

  @Test
  fun openFromHomeWithoutStoredSessionStartsConnectFlow() {
    val setup = buildController()

    setup.controller.openFromHome()
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    assertEquals(
      AppScreen.XScreen(ProviderDestination.Connect),
      setup.shownScreens.last(),
    )
  }

  @Test
  fun openFromHomeWithStoredSessionShowsContentList() {
    val setup = buildController(storedSession = "auth_token=present")

    setup.controller.openFromHome()

    assertEquals(
      AppScreen.XScreen(XDestination.ContentList),
      setup.shownScreens.last(),
    )
  }

  private fun buildController(storedSession: String? = null): ControllerSetup {
    val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
    activity.setTheme(R.style.Theme_ReadMyFeed)
    val binding = ActivityMainBinding.inflate(activity.layoutInflater)
    activity.setContentView(binding.root)
    val shownScreens = mutableListOf<AppScreen>()
    val authService = XAuthService(
      cookieReader = FakeXCookieReader(),
      sessionStore = FakeXSessionStore(storedSession),
    )
    val ttsService = TtsService(FakeTtsEngine())
    val controller = XFeatureController(
      activity = activity,
      binding = binding,
      showScreen = { screen -> shownScreens += screen },
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
      controller = controller,
      shownScreens = shownScreens,
    )
  }

  private data class ControllerSetup(
    val activity: AppCompatActivity,
    val binding: ActivityMainBinding,
    val controller: XFeatureController,
    val shownScreens: MutableList<AppScreen>,
  )

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

  private class FakeXSessionStore(initialCookieString: String? = null) : XSessionStore {
    private var cookieString: String? = initialCookieString

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
