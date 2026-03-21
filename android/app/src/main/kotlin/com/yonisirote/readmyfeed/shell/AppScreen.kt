package com.yonisirote.readmyfeed.shell

import com.yonisirote.readmyfeed.providers.FeedProvider

enum class ProviderDestination {
  CONNECT,
  CONTENT_LIST,
  CHAT_LIST,
  CHAT_MESSAGES,
}

sealed interface AppScreen {
  data object Home : AppScreen

  data class ProviderScreen(
    val provider: FeedProvider,
    val destination: ProviderDestination,
  ) : AppScreen
}

fun resolveHomeSelectionScreen(
  provider: FeedProvider,
  hasStoredSession: Boolean,
): AppScreen {
  // Home cards route to connect or content based on provider availability and session state.
  if (!provider.isAvailable) {
    return AppScreen.Home
  }

  val destination = if (hasStoredSession) {
    provider.connectedDestination
  } else {
    provider.connectDestination
  }

  return AppScreen.ProviderScreen(
    provider = provider,
    destination = destination,
  )
}

fun AppScreen.matchesProviderDestination(
  provider: FeedProvider,
  destination: ProviderDestination,
): Boolean {
  return this is AppScreen.ProviderScreen &&
    this.provider == provider &&
    this.destination == destination
}

fun AppScreen.ProviderScreen.matchesProvider(
  provider: FeedProvider,
  destination: ProviderDestination,
): Boolean {
  return this.provider == provider && this.destination == destination
}
