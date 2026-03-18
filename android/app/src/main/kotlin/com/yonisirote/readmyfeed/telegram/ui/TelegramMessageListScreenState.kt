package com.yonisirote.readmyfeed.telegram.ui

import com.yonisirote.readmyfeed.telegram.client.TelegramClientSnapshot

enum class TelegramMessageListStage {
  SELECT_CHAT,
  LOADING,
  EMPTY,
  ERROR,
  READY,
}

data class TelegramMessageListScreenState(
  val stage: TelegramMessageListStage,
  val errorMessage: String? = null,
)

fun resolveTelegramMessageListScreenState(snapshot: TelegramClientSnapshot): TelegramMessageListScreenState {
  val stage = when {
    snapshot.selectedChatPreview == null -> TelegramMessageListStage.SELECT_CHAT
    snapshot.selectedChatMessages.isNotEmpty() -> TelegramMessageListStage.READY
    !snapshot.chatMessagesError?.message.isNullOrBlank() -> TelegramMessageListStage.ERROR
    snapshot.isChatMessagesLoading || !snapshot.hasLoadedChatMessages -> TelegramMessageListStage.LOADING
    else -> TelegramMessageListStage.EMPTY
  }

  return TelegramMessageListScreenState(
    stage = stage,
    errorMessage = snapshot.chatMessagesError?.message,
  )
}
