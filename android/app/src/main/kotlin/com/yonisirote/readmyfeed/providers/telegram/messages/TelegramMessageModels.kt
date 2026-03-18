package com.yonisirote.readmyfeed.providers.telegram.messages

import com.yonisirote.readmyfeed.providers.telegram.chats.TelegramChatPreview
import org.drinkless.tdlib.TdApi

enum class TelegramMessageContentKind {
  TEXT,
  CAPTION,
  UNSUPPORTED,
}

data class TelegramMessageItem(
  val messageId: Long,
  val chatId: Long,
  val authorLabel: String,
  val text: String,
  val lang: String,
  val isUnread: Boolean,
  val contentKind: TelegramMessageContentKind,
)

fun TelegramMessageItem.isSpeakable(): Boolean {
  return isUnread &&
    contentKind != TelegramMessageContentKind.UNSUPPORTED &&
    text.trim().isNotBlank()
}

internal fun buildUnreadTelegramMessageItems(
  messages: Collection<TdApi.Message>,
  chat: TdApi.Chat?,
  usersById: Map<Long, TdApi.User>,
  chatsById: Map<Long, TdApi.Chat>,
  fallbackChatPreview: TelegramChatPreview? = null,
): List<TelegramMessageItem> {
  return messages.asSequence()
    .mapNotNull { message ->
      mapTelegramMessageItem(
        message = message,
        chat = chat,
        usersById = usersById,
        chatsById = chatsById,
        fallbackChatPreview = fallbackChatPreview,
      )
    }
    .filter { item -> item.isSpeakable() }
    .sortedBy { item -> item.messageId }
    .toList()
}

private fun mapTelegramMessageItem(
  message: TdApi.Message?,
  chat: TdApi.Chat?,
  usersById: Map<Long, TdApi.User>,
  chatsById: Map<Long, TdApi.Chat>,
  fallbackChatPreview: TelegramChatPreview?,
): TelegramMessageItem? {
  if (message == null) {
    return null
  }

  val content = resolveTelegramMessageContent(message.content)
  val chatTitle = chat?.title.orEmpty().trim().ifBlank {
    fallbackChatPreview?.title.orEmpty().trim()
  }
  val isUnread = !message.isOutgoing && message.id > (chat?.lastReadInboxMessageId ?: 0L)

  return TelegramMessageItem(
    messageId = message.id,
    chatId = message.chatId,
    authorLabel = resolveTelegramMessageAuthorLabel(
      message = message,
      chat = chat,
      chatTitle = chatTitle,
      usersById = usersById,
      chatsById = chatsById,
    ),
    text = content.text,
    lang = message.summaryLanguageCode.orEmpty().trim(),
    isUnread = isUnread,
    contentKind = content.kind,
  )
}

private fun resolveTelegramMessageContent(content: TdApi.MessageContent?): TelegramMessageContent {
  val mapped = when (content) {
    is TdApi.MessageText -> TelegramMessageContent(
      text = content.text.extractPlainText(),
      kind = TelegramMessageContentKind.TEXT,
    )
    is TdApi.MessagePhoto -> TelegramMessageContent(
      text = content.caption.extractPlainText(),
      kind = TelegramMessageContentKind.CAPTION,
    )
    is TdApi.MessageVideo -> TelegramMessageContent(
      text = content.caption.extractPlainText(),
      kind = TelegramMessageContentKind.CAPTION,
    )
    is TdApi.MessageDocument -> TelegramMessageContent(
      text = content.caption.extractPlainText(),
      kind = TelegramMessageContentKind.CAPTION,
    )
    is TdApi.MessageAnimation -> TelegramMessageContent(
      text = content.caption.extractPlainText(),
      kind = TelegramMessageContentKind.CAPTION,
    )
    is TdApi.MessageAudio -> TelegramMessageContent(
      text = content.caption.extractPlainText(),
      kind = TelegramMessageContentKind.CAPTION,
    )
    else -> TelegramMessageContent(
      text = "",
      kind = TelegramMessageContentKind.UNSUPPORTED,
    )
  }

  return mapped.copy(text = mapped.text.normalizeTelegramMessageText())
}

private fun resolveTelegramMessageAuthorLabel(
  message: TdApi.Message,
  chat: TdApi.Chat?,
  chatTitle: String,
  usersById: Map<Long, TdApi.User>,
  chatsById: Map<Long, TdApi.Chat>,
): String {
  val authorSignature = message.authorSignature.orEmpty().trim()
  if (authorSignature.isNotBlank()) {
    return authorSignature
  }

  return when (val sender = message.senderId) {
    is TdApi.MessageSenderUser -> {
      resolveTelegramUserLabel(usersById[sender.userId])
        ?: if (message.isOutgoing) {
          TELEGRAM_OUTGOING_AUTHOR_LABEL
        } else if (chatTitle.isNotBlank() && (chat == null || isSinglePeerChat(chat))) {
          chatTitle
        } else {
          TELEGRAM_UNKNOWN_AUTHOR_LABEL
        }
    }
    is TdApi.MessageSenderChat -> {
      chatsById[sender.chatId]?.title.orEmpty().trim().ifBlank {
        chatTitle.ifBlank {
          if (message.isOutgoing) {
            TELEGRAM_OUTGOING_AUTHOR_LABEL
          } else {
            TELEGRAM_UNKNOWN_AUTHOR_LABEL
          }
        }
      }
    }
    else -> if (message.isOutgoing) {
      TELEGRAM_OUTGOING_AUTHOR_LABEL
    } else {
      TELEGRAM_UNKNOWN_AUTHOR_LABEL
    }
  }
}

private fun resolveTelegramUserLabel(user: TdApi.User?): String? {
  if (user == null) {
    return null
  }

  val fullName = listOf(user.firstName, user.lastName)
    .map { value -> value.orEmpty().trim() }
    .filter { value -> value.isNotBlank() }
    .joinToString(separator = " ")
    .trim()
  if (fullName.isNotBlank()) {
    return fullName
  }

  val username = user.usernames?.activeUsernames.orEmpty()
    .firstOrNull { value -> !value.isNullOrBlank() }
    .orEmpty()
    .trim()
  if (username.isNotBlank()) {
    return "@$username"
  }

  return null
}

private fun TdApi.FormattedText?.extractPlainText(): String {
  return this?.text.orEmpty()
}

private fun String.normalizeTelegramMessageText(): String {
  return replace(TELEGRAM_MESSAGE_WHITESPACE, " ")
    .trim()
}

private fun isSinglePeerChat(chat: TdApi.Chat?): Boolean {
  return chat?.type is TdApi.ChatTypePrivate || chat?.type is TdApi.ChatTypeSecret
}

private data class TelegramMessageContent(
  val text: String,
  val kind: TelegramMessageContentKind,
)

private const val TELEGRAM_OUTGOING_AUTHOR_LABEL: String = "You"

private const val TELEGRAM_UNKNOWN_AUTHOR_LABEL: String = "Unknown sender"

private val TELEGRAM_MESSAGE_WHITESPACE = Regex("\\s+")
