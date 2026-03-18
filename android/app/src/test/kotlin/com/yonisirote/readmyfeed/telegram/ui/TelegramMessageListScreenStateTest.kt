package com.yonisirote.readmyfeed.telegram.ui

import com.yonisirote.readmyfeed.telegram.auth.TelegramAuthState
import com.yonisirote.readmyfeed.telegram.chats.TelegramChatPreview
import com.yonisirote.readmyfeed.telegram.client.TelegramClientError
import com.yonisirote.readmyfeed.telegram.client.TelegramClientSnapshot
import com.yonisirote.readmyfeed.telegram.messages.TelegramMessageContentKind
import com.yonisirote.readmyfeed.telegram.messages.TelegramMessageItem
import org.junit.Assert.assertEquals
import org.junit.Test

class TelegramMessageListScreenStateTest {
  @Test
  fun selectChatStateResolvesBeforeSelection() {
    val state = resolveTelegramMessageListScreenState(
      TelegramClientSnapshot(
        authState = TelegramAuthState.Ready,
      ),
    )

    assertEquals(TelegramMessageListStage.SELECT_CHAT, state.stage)
  }

  @Test
  fun loadingStateResolvesWhileMessagesLoad() {
    val state = resolveTelegramMessageListScreenState(
      TelegramClientSnapshot(
        authState = TelegramAuthState.Ready,
        selectedChatPreview = TelegramChatPreview(
          chatId = 1L,
          title = "Chat",
          unreadCount = 1,
          order = 1L,
        ),
        isChatMessagesLoading = true,
      ),
    )

    assertEquals(TelegramMessageListStage.LOADING, state.stage)
  }

  @Test
  fun errorStateResolvesAfterFailedMessageLoad() {
    val state = resolveTelegramMessageListScreenState(
      TelegramClientSnapshot(
        authState = TelegramAuthState.Ready,
        selectedChatPreview = TelegramChatPreview(
          chatId = 1L,
          title = "Chat",
          unreadCount = 1,
          order = 1L,
        ),
        hasLoadedChatMessages = true,
        chatMessagesError = TelegramClientError(
          code = "ERR",
          message = "load failed",
        ),
      ),
    )

    assertEquals(TelegramMessageListStage.ERROR, state.stage)
    assertEquals("load failed", state.errorMessage)
  }

  @Test
  fun readyStateWinsWhenMessagesExist() {
    val state = resolveTelegramMessageListScreenState(
      TelegramClientSnapshot(
        authState = TelegramAuthState.Ready,
        selectedChatPreview = TelegramChatPreview(
          chatId = 1L,
          title = "Chat",
          unreadCount = 1,
          order = 1L,
        ),
        hasLoadedChatMessages = true,
        selectedChatMessages = listOf(
          TelegramMessageItem(
            messageId = 4L,
            chatId = 1L,
            authorLabel = "Alice",
            text = "Hello",
            lang = "en",
            isUnread = true,
            contentKind = TelegramMessageContentKind.TEXT,
          ),
        ),
      ),
    )

    assertEquals(TelegramMessageListStage.READY, state.stage)
  }

  @Test
  fun emptyStateResolvesWhenChatLoadedButUnreadMessagesAreUnsupported() {
    val state = resolveTelegramMessageListScreenState(
      TelegramClientSnapshot(
        authState = TelegramAuthState.Ready,
        selectedChatPreview = TelegramChatPreview(
          chatId = 2L,
          title = "Chat",
          unreadCount = 0,
          order = 10L,
        ),
        hasLoadedChatMessages = true,
      ),
    )

    assertEquals(TelegramMessageListStage.EMPTY, state.stage)
    assertEquals(null, state.errorMessage)
  }
}
