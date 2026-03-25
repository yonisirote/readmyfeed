package com.yonisirote.readmyfeed.shell

import com.yonisirote.readmyfeed.providers.FeedProvider

import org.junit.Assert.assertEquals
import org.junit.Test

class AppScreenTest {
  @Test
  fun availableXProviderWithoutStoredSessionRoutesToConnectScreen() {
    val screen = resolveHomeSelectionScreen(
      provider = FeedProvider.X,
      hasStoredSession = false,
    )

    assertEquals(
      AppScreen.XScreen(XDestination.CONNECT),
      screen,
    )
  }

  @Test
  fun availableXProviderWithStoredSessionRoutesToContentList() {
    val screen = resolveHomeSelectionScreen(
      provider = FeedProvider.X,
      hasStoredSession = true,
    )

    assertEquals(
      AppScreen.XScreen(XDestination.CONTENT_LIST),
      screen,
    )
  }

  @Test
  fun telegramProviderWithoutStoredSessionRoutesToConnectScreen() {
    val screen = resolveHomeSelectionScreen(
      provider = FeedProvider.TELEGRAM,
      hasStoredSession = false,
    )

    assertEquals(
      AppScreen.TelegramScreen(TelegramDestination.CONNECT),
      screen,
    )
  }

  @Test
  fun telegramProviderWithStoredSessionRoutesToChatList() {
    val screen = resolveHomeSelectionScreen(
      provider = FeedProvider.TELEGRAM,
      hasStoredSession = true,
    )

    assertEquals(
      AppScreen.TelegramScreen(TelegramDestination.CHAT_LIST),
      screen,
    )
  }

  @Test
  fun unavailableProviderRoutesBackHome() {
    val screen = resolveHomeSelectionScreen(
      provider = FeedProvider.WHATSAPP,
      hasStoredSession = true,
    )

    assertEquals(AppScreen.Home, screen)
  }
}
