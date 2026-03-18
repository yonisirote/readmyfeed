package com.yonisirote.readmyfeed.shell

import com.yonisirote.readmyfeed.providers.FeedProvider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppScreenTest {
  @Test
  fun availableProviderWithoutStoredSessionRoutesToConnectScreen() {
    val screen = resolveHomeSelectionScreen(
      provider = FeedProvider.X,
      hasStoredSession = false,
    )

    assertEquals(
      AppScreen.ProviderScreen(
        provider = FeedProvider.X,
        destination = ProviderDestination.CONNECT,
      ),
      screen,
    )
  }

  @Test
  fun availableProviderWithStoredSessionRoutesToConnectedDestination() {
    val screen = resolveHomeSelectionScreen(
      provider = FeedProvider.X,
      hasStoredSession = true,
    )

    assertEquals(
      AppScreen.ProviderScreen(
        provider = FeedProvider.X,
        destination = ProviderDestination.CONTENT_LIST,
      ),
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
      AppScreen.ProviderScreen(
        provider = FeedProvider.TELEGRAM,
        destination = ProviderDestination.CONNECT,
      ),
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
      AppScreen.ProviderScreen(
        provider = FeedProvider.TELEGRAM,
        destination = ProviderDestination.CHAT_LIST,
      ),
      screen,
    )
  }

  @Test
  fun providerScreenMatchesExpectedDestination() {
    val screen = AppScreen.ProviderScreen(
      provider = FeedProvider.X,
      destination = ProviderDestination.CONTENT_LIST,
    )

    assertTrue(screen.matchesProvider(FeedProvider.X, ProviderDestination.CONTENT_LIST))
    assertTrue(screen.matchesProviderDestination(FeedProvider.X, ProviderDestination.CONTENT_LIST))
    assertFalse(screen.matchesProvider(FeedProvider.X, ProviderDestination.CONNECT))
    assertFalse(screen.matchesProviderDestination(FeedProvider.TELEGRAM, ProviderDestination.CHAT_LIST))
  }
}
