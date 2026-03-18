package com.yonisirote.readmyfeed.telegram.messages

import com.yonisirote.readmyfeed.telegram.chats.TelegramChatPreview
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramMessageModelsTest {
  @Test
  fun unreadMessagesPreferSupportedTextAndResolveAuthorNames() {
    val chat = TdApi.Chat().apply {
      id = 100L
      title = "Alice Chat"
      type = TdApi.ChatTypePrivate(9L)
      lastReadInboxMessageId = 10L
    }
    val user = TdApi.User().apply {
      id = 9L
      firstName = "Alice"
      lastName = "Reader"
    }

    val items = buildUnreadTelegramMessageItems(
      messages = listOf(
        buildTextMessage(
          chatId = 100L,
          messageId = 11L,
          text = "  hello\nworld  ",
          senderId = TdApi.MessageSenderUser(9L),
        ),
        buildPhotoMessage(
          chatId = 100L,
          messageId = 12L,
          caption = "caption only",
          senderId = TdApi.MessageSenderUser(9L),
        ),
        buildUnsupportedMessage(
          chatId = 100L,
          messageId = 13L,
          senderId = TdApi.MessageSenderUser(9L),
        ),
      ),
      chat = chat,
      usersById = mapOf(9L to user),
      chatsById = emptyMap(),
    )

    assertEquals(listOf(11L, 12L), items.map { it.messageId })
    assertEquals("hello world", items.first().text)
    assertEquals("Alice Reader", items.first().authorLabel)
    assertEquals(TelegramMessageContentKind.TEXT, items.first().contentKind)
    assertEquals(TelegramMessageContentKind.CAPTION, items.last().contentKind)
  }

  @Test
  fun readOrOutgoingMessagesAreExcluded() {
    val chat = TdApi.Chat().apply {
      id = 200L
      title = "Updates"
      lastReadInboxMessageId = 20L
    }

    val items = buildUnreadTelegramMessageItems(
      messages = listOf(
        buildTextMessage(
          chatId = 200L,
          messageId = 19L,
          text = "already read",
          senderId = TdApi.MessageSenderUser(1L),
        ),
        buildTextMessage(
          chatId = 200L,
          messageId = 21L,
          text = "outgoing",
          senderId = TdApi.MessageSenderUser(1L),
          isOutgoing = true,
        ),
      ),
      chat = chat,
      usersById = emptyMap(),
      chatsById = emptyMap(),
    )

    assertTrue(items.isEmpty())
  }

  @Test
  fun fallbackChatPreviewSuppliesTitleWhenChatIsMissing() {
    val items = buildUnreadTelegramMessageItems(
      messages = listOf(
        buildTextMessage(
          chatId = 300L,
          messageId = 30L,
          text = "hi there",
          senderId = TdApi.MessageSenderUser(44L),
        ),
      ),
      chat = null,
      usersById = emptyMap(),
      chatsById = emptyMap(),
      fallbackChatPreview = TelegramChatPreview(
        chatId = 300L,
        title = "Fallback Chat",
        unreadCount = 1,
        order = 1L,
      ),
    )

    assertEquals(1, items.size)
    assertEquals("Fallback Chat", items.single().authorLabel)
  }

  @Test
  fun authorResolutionPrefersSignatureUsernameAndYouFallbacks() {
    val userWithUsername = TdApi.User().apply {
      id = 1L
      usernames = TdApi.Usernames(arrayOf("alice"), emptyArray(), "", emptyArray())
    }
    val senderChat = TdApi.Chat().apply {
      id = 40L
      title = "Announcements"
    }
    val chat = TdApi.Chat().apply {
      id = 400L
      title = "Group"
      type = TdApi.ChatTypeBasicGroup(9L)
      lastReadInboxMessageId = 0L
    }

    val items = buildUnreadTelegramMessageItems(
      messages = listOf(
        buildTextMessage(
          chatId = 400L,
          messageId = 1L,
          text = "sig",
          senderId = TdApi.MessageSenderUser(1L),
        ).apply { authorSignature = "Signed" },
        buildTextMessage(
          chatId = 400L,
          messageId = 2L,
          text = "username",
          senderId = TdApi.MessageSenderUser(1L),
        ),
        buildTextMessage(
          chatId = 400L,
          messageId = 3L,
          text = "outgoing",
          senderId = TdApi.MessageSenderUser(99L),
          isOutgoing = true,
        ),
        buildTextMessage(
          chatId = 400L,
          messageId = 4L,
          text = "chat sender",
          senderId = TdApi.MessageSenderChat(40L),
        ),
      ),
      chat = chat,
      usersById = mapOf(1L to userWithUsername),
      chatsById = mapOf(40L to senderChat),
    )

    assertEquals(listOf("Signed", "@alice", "Announcements"), items.map { it.authorLabel })
  }

  @Test
  fun trimsLanguageAndSupportsVideoCaptionContent() {
    val chat = TdApi.Chat().apply {
      id = 500L
      title = "Clips"
      lastReadInboxMessageId = 0L
    }

    val items = buildUnreadTelegramMessageItems(
      messages = listOf(
        TdApi.Message().apply {
          id = 1L
          chatId = 500L
          senderId = TdApi.MessageSenderUser(2L)
          summaryLanguageCode = " en "
          content = TdApi.MessageVideo(
            null,
            emptyArray(),
            emptyArray(),
            null,
            0,
            TdApi.FormattedText("caption text", emptyArray()),
            false,
            false,
            false,
          )
        },
      ),
      chat = chat,
      usersById = emptyMap(),
      chatsById = emptyMap(),
    )

    assertEquals(1, items.size)
    assertEquals("en", items.single().lang)
    assertEquals(TelegramMessageContentKind.CAPTION, items.single().contentKind)
  }

  private fun buildTextMessage(
    chatId: Long,
    messageId: Long,
    text: String,
    senderId: TdApi.MessageSender,
    isOutgoing: Boolean = false,
  ): TdApi.Message {
    return TdApi.Message().apply {
      id = messageId
      this.chatId = chatId
      this.senderId = senderId
      this.isOutgoing = isOutgoing
      content = TdApi.MessageText(TdApi.FormattedText(text, emptyArray()), null, null)
    }
  }

  private fun buildPhotoMessage(
    chatId: Long,
    messageId: Long,
    caption: String,
    senderId: TdApi.MessageSender,
  ): TdApi.Message {
    return TdApi.Message().apply {
      id = messageId
      this.chatId = chatId
      this.senderId = senderId
      content = TdApi.MessagePhoto(
        null,
        TdApi.FormattedText(caption, emptyArray()),
        false,
        false,
        false,
      )
    }
  }

  private fun buildUnsupportedMessage(
    chatId: Long,
    messageId: Long,
    senderId: TdApi.MessageSender,
  ): TdApi.Message {
    return TdApi.Message().apply {
      id = messageId
      this.chatId = chatId
      this.senderId = senderId
      content = TdApi.MessageSticker(null, false)
    }
  }
}
