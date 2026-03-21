package com.yonisirote.readmyfeed.providers.telegram.ui

import com.yonisirote.readmyfeed.providers.telegram.client.TelegramClientSnapshot

enum class TelegramChatListStage {
  LOADING,
  EMPTY,
  ERROR,
  READY,
}

data class TelegramChatListScreenState(
  val stage: TelegramChatListStage,
  val errorMessage: String? = null,
)

fun resolveTelegramChatListScreenState(snapshot: TelegramClientSnapshot): TelegramChatListScreenState {
  // Keep existing previews visible even while a background load or transient error is happening.
  val stage = when {
    snapshot.chatPreviews.isNotEmpty() -> TelegramChatListStage.READY
    !snapshot.chatListError?.message.isNullOrBlank() -> TelegramChatListStage.ERROR
    snapshot.isChatListLoading || !snapshot.hasLoadedChatList -> TelegramChatListStage.LOADING
    else -> TelegramChatListStage.EMPTY
  }

  return TelegramChatListScreenState(
    stage = stage,
    errorMessage = snapshot.chatListError?.message,
  )
}
