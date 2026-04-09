package com.yonisirote.readmyfeed.providers.telegram.ui

import androidx.appcompat.app.AppCompatActivity
import com.yonisirote.readmyfeed.R
import com.yonisirote.readmyfeed.databinding.ActivityMainBinding
import com.yonisirote.readmyfeed.providers.telegram.client.TelegramClientManager
import com.yonisirote.readmyfeed.providers.telegram.client.TelegramTdlibClient
import com.yonisirote.readmyfeed.providers.telegram.client.TelegramTdlibClientFactory
import com.yonisirote.readmyfeed.providers.telegram.client.TelegramTdlibParametersFactory
import com.yonisirote.readmyfeed.shell.AppScreen
import com.yonisirote.readmyfeed.shell.ProviderDestination
import com.yonisirote.readmyfeed.shell.TelegramDestination
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
  fun hideClearsTelegramScreens() {
    val setup = buildController()

    setup.controller.render(AppScreen.TelegramScreen(TelegramDestination.ChatList))
    setup.controller.hide()

    assertFalse(setup.binding.telegramConnectScreen.isShown)
    assertFalse(setup.binding.telegramChatListScreen.isShown)
    assertFalse(setup.binding.telegramChatMessagesScreen.isShown)
  }

  @Test
  fun renderShowsOnlyRequestedTelegramScreen() {
    val setup = buildController()

    setup.controller.render(AppScreen.TelegramScreen(TelegramDestination.ChatList))
    assertTrue(setup.binding.telegramChatListScreen.isShown)
    assertFalse(setup.binding.telegramConnectScreen.isShown)
    assertFalse(setup.binding.telegramChatMessagesScreen.isShown)

    setup.controller.render(AppScreen.TelegramScreen(TelegramDestination.ChatMessages))
    assertTrue(setup.binding.telegramChatMessagesScreen.isShown)
    assertFalse(setup.binding.telegramChatListScreen.isShown)
  }

  @Test
  fun handleBackPressFromChatMessagesReturnsToChatList() {
    val setup = buildController()
    setup.controller.render(AppScreen.TelegramScreen(TelegramDestination.ChatMessages))

    assertTrue(setup.controller.handleBackPress())
    assertEquals(
      AppScreen.TelegramScreen(TelegramDestination.ChatList),
      setup.shownScreens.last(),
    )
  }

  @Test
  fun handleBackPressReturnsFalseWhenInactive() {
    val setup = buildController()

    assertFalse(setup.controller.handleBackPress())
  }

  @Test
  fun openFromHomeWithoutConnectedSessionShowsConnectScreen() {
    val setup = buildController()

    setup.controller.openFromHome()

    assertEquals(
      AppScreen.TelegramScreen(ProviderDestination.Connect),
      setup.shownScreens.last(),
    )
  }

  private fun buildController(): ControllerSetup {
    val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
    activity.setTheme(R.style.Theme_ReadMyFeed)
    val binding = ActivityMainBinding.inflate(activity.layoutInflater)
    activity.setContentView(binding.root)
    val shownScreens = mutableListOf<AppScreen>()
    val clientManager = TelegramClientManager(
      clientFactory = FakeTelegramTdlibClientFactory(),
      parametersFactory = TelegramTdlibParametersFactory { throw UnsupportedOperationException("Unused in test") },
    )

    val controller = TelegramFeatureController(
      activity = activity,
      binding = binding,
      showScreen = { screen -> shownScreens += screen },
      clientManagerFactory = { clientManager },
      ttsServiceFactory = { TtsService(FakeTtsEngine()) },
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
    val controller: TelegramFeatureController,
    val shownScreens: MutableList<AppScreen>,
  )

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
