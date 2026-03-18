package com.yonisirote.readmyfeed.telegram.ui

import com.yonisirote.readmyfeed.telegram.auth.TelegramAuthState
import com.yonisirote.readmyfeed.telegram.chats.TelegramChatPreview
import com.yonisirote.readmyfeed.telegram.client.TelegramClientError
import com.yonisirote.readmyfeed.telegram.client.TelegramClientSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class TelegramChatListScreenStateTest {
  @Test
  fun loadingStateResolvesBeforeFirstLoadCompletes() {
    val state = resolveTelegramChatListScreenState(
      TelegramClientSnapshot(
        authState = TelegramAuthState.Ready,
        isChatListLoading = true,
      ),
    )

    assertEquals(TelegramChatListStage.LOADING, state.stage)
  }

  @Test
  fun errorStateResolvesAfterFailedLoadWithNoChats() {
    val state = resolveTelegramChatListScreenState(
      TelegramClientSnapshot(
        authState = TelegramAuthState.Ready,
        hasLoadedChatList = true,
        chatListError = TelegramClientError(
          code = "ERR",
          message = "load failed",
        ),
      ),
    )

    assertEquals(TelegramChatListStage.ERROR, state.stage)
    assertEquals("load failed", state.errorMessage)
  }

  @Test
  fun readyStateWinsWhenChatsExist() {
    val state = resolveTelegramChatListScreenState(
      TelegramClientSnapshot(
        authState = TelegramAuthState.Ready,
        hasLoadedChatList = true,
        chatPreviews = listOf(
          TelegramChatPreview(
            chatId = 1L,
            title = "Chat",
            unreadCount = 2,
            order = 10L,
          ),
        ),
      ),
    )

    assertEquals(TelegramChatListStage.READY, state.stage)
  }

  @Test
  fun emptyStateResolvesAfterSuccessfulLoadWithoutChats() {
    val state = resolveTelegramChatListScreenState(
      TelegramClientSnapshot(
        authState = TelegramAuthState.Ready,
        hasLoadedChatList = true,
      ),
    )

    assertEquals(TelegramChatListStage.EMPTY, state.stage)
    assertEquals(null, state.errorMessage)
  }
}
