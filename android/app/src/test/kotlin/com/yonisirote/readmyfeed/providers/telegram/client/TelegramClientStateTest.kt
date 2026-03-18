package com.yonisirote.readmyfeed.providers.telegram.client

import com.yonisirote.readmyfeed.providers.telegram.auth.TelegramAuthState
import com.yonisirote.readmyfeed.providers.telegram.chats.TelegramChatPreview
import com.yonisirote.readmyfeed.providers.telegram.messages.TelegramMessageContentKind
import com.yonisirote.readmyfeed.providers.telegram.messages.TelegramMessageItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramClientStateTest {
  @Test
  fun snapshotDefaultsRepresentNotStartedAndEmptyUiState() {
    val snapshot = TelegramClientSnapshot()

    assertEquals(TelegramAuthState.NotStarted, snapshot.authState)
    assertNull(snapshot.lastError)
    assertNull(snapshot.chatListError)
    assertTrue(snapshot.chatPreviews.isEmpty())
    assertFalse(snapshot.isChatListLoading)
    assertFalse(snapshot.hasLoadedChatList)
    assertFalse(snapshot.hasLoadedAllChats)
    assertNull(snapshot.selectedChatPreview)
    assertTrue(snapshot.selectedChatMessages.isEmpty())
    assertNull(snapshot.chatMessagesError)
    assertFalse(snapshot.isChatMessagesLoading)
    assertFalse(snapshot.hasLoadedChatMessages)
  }

  @Test
  fun snapshotCopyCanTrackIndependentChatListAndMessageState() {
    val chatPreview = TelegramChatPreview(
      chatId = 7L,
      title = "Chat",
      unreadCount = 2,
      order = 99L,
    )
    val messageItem = TelegramMessageItem(
      messageId = 10L,
      chatId = 7L,
      authorLabel = "Alice",
      text = "Hello",
      lang = "en",
      isUnread = true,
      contentKind = TelegramMessageContentKind.TEXT,
    )

    val snapshot = TelegramClientSnapshot().copy(
      authState = TelegramAuthState.Ready,
      chatPreviews = listOf(chatPreview),
      hasLoadedChatList = true,
      selectedChatPreview = chatPreview,
      selectedChatMessages = listOf(messageItem),
      hasLoadedChatMessages = true,
    )

    assertEquals(TelegramAuthState.Ready, snapshot.authState)
    assertEquals(listOf(chatPreview), snapshot.chatPreviews)
    assertEquals(chatPreview, snapshot.selectedChatPreview)
    assertEquals(listOf(messageItem), snapshot.selectedChatMessages)
    assertTrue(snapshot.hasLoadedChatList)
    assertTrue(snapshot.hasLoadedChatMessages)
  }
}
