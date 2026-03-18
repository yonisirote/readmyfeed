package com.yonisirote.readmyfeed.providers.telegram.chats

import org.drinkless.tdlib.TdApi

data class TelegramChatPreview(
  val chatId: Long,
  val title: String,
  val unreadCount: Int,
  val order: Long,
  val lastMessagePreview: String = "",
  val isMarkedAsUnread: Boolean = false,
)

fun TelegramChatPreview.hasUnreadMessages(): Boolean {
  return unreadCount > 0 || isMarkedAsUnread
}

internal fun buildSortedTelegramChatPreviews(chats: Collection<TdApi.Chat>): List<TelegramChatPreview> {
  return chats.mapNotNull(::mapTelegramChatPreview)
    .sortedWith(
      compareByDescending<TelegramChatPreview> { it.order }
        .thenByDescending { it.chatId },
    )
}

internal fun buildTelegramSelectedChatPreview(
  chat: TdApi.Chat,
  fallback: TelegramChatPreview? = null,
): TelegramChatPreview {
  return TelegramChatPreview(
    chatId = chat.id,
    title = chat.title.orEmpty().trim().ifBlank {
      fallback?.title.orEmpty()
    },
    unreadCount = chat.unreadCount.coerceAtLeast(0),
    order = resolveMainChatOrder(chat.positions).takeIf { it > 0L } ?: fallback?.order ?: 0L,
    lastMessagePreview = buildLastMessagePreview(chat.lastMessage).ifBlank {
      fallback?.lastMessagePreview.orEmpty()
    },
    isMarkedAsUnread = chat.isMarkedAsUnread,
  )
}

private fun mapTelegramChatPreview(chat: TdApi.Chat): TelegramChatPreview? {
  val order = resolveMainChatOrder(chat.positions)
  if (order <= 0L) {
    return null
  }

  return TelegramChatPreview(
    chatId = chat.id,
    title = chat.title.orEmpty().trim(),
    unreadCount = chat.unreadCount.coerceAtLeast(0),
    order = order,
    lastMessagePreview = buildLastMessagePreview(chat.lastMessage),
    isMarkedAsUnread = chat.isMarkedAsUnread,
  )
}

private fun resolveMainChatOrder(positions: Array<TdApi.ChatPosition>?): Long {
  return positions.orEmpty()
    .firstOrNull { position -> position.list is TdApi.ChatListMain }
    ?.order ?: 0L
}

private fun buildLastMessagePreview(message: TdApi.Message?): String {
  val rawPreview = when (val content = message?.content) {
    is TdApi.MessageText -> content.text.extractPlainText()
    is TdApi.MessagePhoto -> content.caption.extractPlainText()
    is TdApi.MessageVideo -> content.caption.extractPlainText()
    is TdApi.MessageDocument -> content.caption.extractPlainText()
    is TdApi.MessageAnimation -> content.caption.extractPlainText()
    is TdApi.MessageAudio -> content.caption.extractPlainText()
    else -> ""
  }

  val normalizedPreview = rawPreview
    .replace(TELEGRAM_CHAT_PREVIEW_WHITESPACE, " ")
    .trim()

  return if (normalizedPreview.length <= TELEGRAM_CHAT_PREVIEW_MAX_LENGTH) {
    normalizedPreview
  } else {
    normalizedPreview.take(TELEGRAM_CHAT_PREVIEW_MAX_LENGTH - 3).trimEnd() + "..."
  }
}

private fun TdApi.FormattedText?.extractPlainText(): String {
  return this?.text.orEmpty()
}

private const val TELEGRAM_CHAT_PREVIEW_MAX_LENGTH: Int = 160

private val TELEGRAM_CHAT_PREVIEW_WHITESPACE = Regex("\\s+")
