package com.yonisirote.readmyfeed.providers.telegram.chats

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramChatModelsTest {
  @Test
  fun sortedPreviewsUseMainChatOrderAndTrimMessagePreview() {
    val previews = buildSortedTelegramChatPreviews(
      listOf(
        buildChat(
          chatId = 1L,
          title = "Older",
          unreadCount = 0,
          order = 10L,
          message = buildTextMessage("old"),
        ),
        buildChat(
          chatId = 2L,
          title = "Newest",
          unreadCount = 4,
          order = 99L,
          message = buildDocumentMessage("line 1\nline 2"),
        ),
      ),
    )

    assertEquals(listOf(2L, 1L), previews.map { it.chatId })
    assertEquals("line 1 line 2", previews.first().lastMessagePreview)
    assertTrue(previews.first().hasUnreadMessages())
  }

  @Test
  fun chatsWithoutMainListPositionAreIgnored() {
    val previews = buildSortedTelegramChatPreviews(
      listOf(
        TdApi.Chat().apply {
          id = 5L
          title = "Archive only"
          positions = arrayOf(
            TdApi.ChatPosition(
              TdApi.ChatListArchive(),
              20L,
              false,
              null,
            ),
          )
        },
      ),
    )

    assertTrue(previews.isEmpty())
  }

  @Test
  fun selectedChatPreviewFallsBackWhenOrderOrPreviewAreMissing() {
    val preview = buildTelegramSelectedChatPreview(
      chat = TdApi.Chat().apply {
        id = 9L
        title = "  "
        unreadCount = -5
        isMarkedAsUnread = true
        positions = emptyArray()
        lastMessage = buildTextMessage("")
      },
      fallback = TelegramChatPreview(
        chatId = 9L,
        title = "Fallback title",
        unreadCount = 3,
        order = 88L,
        lastMessagePreview = "Fallback preview",
      ),
    )

    assertEquals("Fallback title", preview.title)
    assertEquals(0, preview.unreadCount)
    assertEquals(88L, preview.order)
    assertEquals("Fallback preview", preview.lastMessagePreview)
    assertTrue(preview.hasUnreadMessages())
  }

  @Test
  fun previewsTruncateLongContentAndSupportExtraCaptionTypes() {
    val longCaption = "word ".repeat(50)
    val previews = buildSortedTelegramChatPreviews(
      listOf(
        buildChat(
          chatId = 4L,
          title = "Audio",
          unreadCount = 1,
          order = 20L,
          message = TdApi.Message().apply {
            content = TdApi.MessageAudio(null, TdApi.FormattedText(longCaption, emptyArray()))
          },
        ),
      ),
    )

    assertEquals(1, previews.size)
    assertTrue(previews.single().lastMessagePreview.endsWith("..."))
  }

  private fun buildChat(
    chatId: Long,
    title: String,
    unreadCount: Int,
    order: Long,
    message: TdApi.Message,
  ): TdApi.Chat {
    return TdApi.Chat().apply {
      id = chatId
      this.title = title
      this.unreadCount = unreadCount
      positions = arrayOf(
        TdApi.ChatPosition(
          TdApi.ChatListMain(),
          order,
          false,
          null,
        ),
      )
      lastMessage = message
      isMarkedAsUnread = unreadCount > 0
    }
  }

  private fun buildTextMessage(text: String): TdApi.Message {
    return TdApi.Message().apply {
      content = TdApi.MessageText(TdApi.FormattedText(text, emptyArray()), null, null)
    }
  }

  private fun buildDocumentMessage(caption: String): TdApi.Message {
    return TdApi.Message().apply {
      content = TdApi.MessageDocument(null, TdApi.FormattedText(caption, emptyArray()))
    }
  }
}
