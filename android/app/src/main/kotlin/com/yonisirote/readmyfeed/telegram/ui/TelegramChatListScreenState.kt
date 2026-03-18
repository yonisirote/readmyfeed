package com.yonisirote.readmyfeed.telegram.ui

import com.yonisirote.readmyfeed.telegram.client.TelegramClientSnapshot

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
