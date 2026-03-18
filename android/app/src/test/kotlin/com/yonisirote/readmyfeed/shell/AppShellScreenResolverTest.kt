package com.yonisirote.readmyfeed.shell

import com.yonisirote.readmyfeed.providers.FeedProvider

import org.junit.Assert.assertEquals
import org.junit.Test

class AppShellScreenResolverTest {
  @Test
  fun homeScreenAlwaysStaysHome() {
    assertEquals(
      AppScreen.Home,
      normalizeAppShellScreen(
        requestedScreen = AppScreen.Home,
        supportsRequestedScreen = false,
      ),
    )
  }

  @Test
  fun supportedProviderScreenIsKept() {
    val screen = AppScreen.ProviderScreen(
      provider = FeedProvider.X,
      destination = ProviderDestination.CONTENT_LIST,
    )

    assertEquals(
      screen,
      normalizeAppShellScreen(
        requestedScreen = screen,
        supportsRequestedScreen = true,
      ),
    )
  }

  @Test
  fun unsupportedProviderScreenFallsBackToHome() {
    val screen = AppScreen.ProviderScreen(
      provider = FeedProvider.TELEGRAM,
      destination = ProviderDestination.CHAT_LIST,
    )

    assertEquals(
      AppScreen.Home,
      normalizeAppShellScreen(
        requestedScreen = screen,
        supportsRequestedScreen = false,
      ),
    )
  }
}
