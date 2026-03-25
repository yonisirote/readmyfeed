package com.yonisirote.readmyfeed.shell

import com.yonisirote.readmyfeed.providers.FeedProvider

enum class XDestination {
  CONNECT,
  CONTENT_LIST,
}

enum class TelegramDestination {
  CONNECT,
  CHAT_LIST,
  CHAT_MESSAGES,
}

sealed interface AppScreen {
  data object Home : AppScreen

  data class XScreen(
    val destination: XDestination,
  ) : AppScreen

  data class TelegramScreen(
    val destination: TelegramDestination,
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

  return when (provider) {
    FeedProvider.X -> AppScreen.XScreen(
      destination = if (hasStoredSession) XDestination.CONTENT_LIST else XDestination.CONNECT,
    )
    FeedProvider.TELEGRAM -> AppScreen.TelegramScreen(
      destination = if (hasStoredSession) {
        TelegramDestination.CHAT_LIST
      } else {
        TelegramDestination.CONNECT
      },
    )
    FeedProvider.WHATSAPP,
    FeedProvider.FACEBOOK,
    -> AppScreen.Home
  }
}
