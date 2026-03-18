package com.yonisirote.readmyfeed.providers.telegram.client

import com.yonisirote.readmyfeed.providers.telegram.auth.TelegramAuthState
import com.yonisirote.readmyfeed.providers.telegram.chats.TelegramChatPreview
import com.yonisirote.readmyfeed.providers.telegram.messages.TelegramMessageItem

data class TelegramClientError(
  val code: String,
  val message: String,
  val context: Map<String, Any?> = emptyMap(),
)

data class TelegramClientSnapshot(
  val authState: TelegramAuthState = TelegramAuthState.NotStarted,
  val lastError: TelegramClientError? = null,
  val chatListError: TelegramClientError? = null,
  val chatPreviews: List<TelegramChatPreview> = emptyList(),
  val isChatListLoading: Boolean = false,
  val hasLoadedChatList: Boolean = false,
  val hasLoadedAllChats: Boolean = false,
  val selectedChatPreview: TelegramChatPreview? = null,
  val selectedChatMessages: List<TelegramMessageItem> = emptyList(),
  val chatMessagesError: TelegramClientError? = null,
  val isChatMessagesLoading: Boolean = false,
  val hasLoadedChatMessages: Boolean = false,
)
