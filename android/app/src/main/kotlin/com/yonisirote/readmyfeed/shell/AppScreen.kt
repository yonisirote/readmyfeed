package com.yonisirote.readmyfeed.shell

import com.yonisirote.readmyfeed.providers.FeedProvider

sealed interface ProviderDestination {
  data object Connect : XDestination, TelegramDestination
}

sealed interface XDestination : ProviderDestination {
  data object ContentList : XDestination
}

sealed interface TelegramDestination : ProviderDestination {
  data object ChatList : TelegramDestination
  data object ChatMessages : TelegramDestination
}

sealed interface AppScreen {
  val owner: FeedProvider?

  data object Home : AppScreen {
    override val owner: FeedProvider? = null
  }

  data class XScreen(
    val destination: XDestination,
  ) : AppScreen {
    override val owner: FeedProvider = FeedProvider.X
  }

  data class TelegramScreen(
    val destination: TelegramDestination,
  ) : AppScreen {
    override val owner: FeedProvider = FeedProvider.TELEGRAM
  }
}
