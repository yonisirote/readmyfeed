package com.yonisirote.readmyfeed.telegram.ui

import androidx.appcompat.app.AppCompatActivity
import com.yonisirote.readmyfeed.AppScreen
import com.yonisirote.readmyfeed.AppScreenHost
import com.yonisirote.readmyfeed.FeedProvider
import com.yonisirote.readmyfeed.ProviderDestination
import com.yonisirote.readmyfeed.R
import com.yonisirote.readmyfeed.databinding.ActivityMainBinding
import com.yonisirote.readmyfeed.telegram.client.TelegramClientManager
import com.yonisirote.readmyfeed.telegram.client.TelegramTdlibClient
import com.yonisirote.readmyfeed.telegram.client.TelegramTdlibClientFactory
import com.yonisirote.readmyfeed.telegram.client.TelegramTdlibParametersFactory
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
class TelegramFeatureControllerTest {
  @Test
  fun supportsConnectChatListAndChatMessagesScreens() {
    val setup = buildController()

    assertTrue(
      setup.controller.supports(AppScreen.ProviderScreen(FeedProvider.TELEGRAM, ProviderDestination.CONNECT)),
    )
    assertTrue(
      setup.controller.supports(AppScreen.ProviderScreen(FeedProvider.TELEGRAM, ProviderDestination.CHAT_LIST)),
    )
    assertTrue(
      setup.controller.supports(AppScreen.ProviderScreen(FeedProvider.TELEGRAM, ProviderDestination.CHAT_MESSAGES)),
    )
    assertFalse(
      setup.controller.supports(AppScreen.ProviderScreen(FeedProvider.X, ProviderDestination.CONTENT_LIST)),
    )
  }

  @Test
  fun renderShowsOnlyRequestedTelegramScreen() {
    val setup = buildController()

    setup.controller.render(AppScreen.ProviderScreen(FeedProvider.TELEGRAM, ProviderDestination.CHAT_LIST))
    assertTrue(setup.binding.telegramChatListScreen.isShown)
    assertFalse(setup.binding.telegramConnectScreen.isShown)
    assertFalse(setup.binding.telegramChatMessagesScreen.isShown)

    setup.controller.render(AppScreen.ProviderScreen(FeedProvider.TELEGRAM, ProviderDestination.CHAT_MESSAGES))
    assertTrue(setup.binding.telegramChatMessagesScreen.isShown)
    assertFalse(setup.binding.telegramChatListScreen.isShown)
  }

  @Test
  fun handleBackPressFromChatMessagesReturnsToChatList() {
    val setup = buildController()
    setup.controller.render(AppScreen.ProviderScreen(FeedProvider.TELEGRAM, ProviderDestination.CHAT_MESSAGES))

    assertTrue(setup.controller.handleBackPress())
    assertEquals(
      AppScreen.ProviderScreen(FeedProvider.TELEGRAM, ProviderDestination.CHAT_LIST),
      setup.screenHost.shownScreens.last(),
    )
  }

  private fun buildController(): ControllerSetup {
    val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
    activity.setTheme(R.style.Theme_ReadMyFeed)
    val binding = ActivityMainBinding.inflate(activity.layoutInflater)
    activity.setContentView(binding.root)
    val screenHost = RecordingScreenHost()
    val clientManager = TelegramClientManager(
      clientFactory = FakeTelegramTdlibClientFactory(),
      parametersFactory = TelegramTdlibParametersFactory { throw UnsupportedOperationException("Unused in test") },
    )

    val controller = TelegramFeatureController(
      activity = activity,
      binding = binding,
      screenHost = screenHost,
      clientManagerFactory = { clientManager },
      ttsServiceFactory = { TtsService(FakeTtsEngine()) },
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
    val controller: TelegramFeatureController,
  )

  private class RecordingScreenHost : AppScreenHost {
    val shownScreens = mutableListOf<AppScreen>()

    override fun showScreen(screen: AppScreen) {
      shownScreens += screen
    }
  }

  private class FakeTelegramTdlibClientFactory : TelegramTdlibClientFactory {
    override fun create(
      updateHandler: (org.drinkless.tdlib.TdApi.Object) -> Unit,
      exceptionHandler: (Throwable) -> Unit,
    ): TelegramTdlibClient {
      return object : TelegramTdlibClient {
        override fun send(
          request: org.drinkless.tdlib.TdApi.Function<*>,
          resultHandler: (org.drinkless.tdlib.TdApi.Object) -> Unit,
        ) {
          resultHandler(org.drinkless.tdlib.TdApi.Ok())
        }
      }
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
