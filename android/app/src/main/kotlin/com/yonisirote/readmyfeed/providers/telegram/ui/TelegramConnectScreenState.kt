package com.yonisirote.readmyfeed.providers.telegram.ui

import com.yonisirote.readmyfeed.providers.telegram.auth.TelegramAuthState
import com.yonisirote.readmyfeed.providers.telegram.client.TelegramClientSnapshot

enum class TelegramConnectStage {
  IDLE,
  INITIALIZING,
  PHONE_NUMBER,
  EMAIL_ADDRESS,
  EMAIL_CODE,
  CODE,
  PASSWORD,
  QR_CONFIRMATION,
  READY,
  BLOCKED,
  CLOSED,
}

enum class TelegramQrActionMode {
  REQUEST_QR,
  RETURN_TO_PHONE,
}

data class TelegramConnectScreenState(
  val stage: TelegramConnectStage,
  val errorMessage: String? = null,
  val qrActionMode: TelegramQrActionMode = TelegramQrActionMode.REQUEST_QR,
)

fun resolveTelegramConnectScreenState(snapshot: TelegramClientSnapshot): TelegramConnectScreenState {
  // Collapse fine-grained TDLib auth states into a smaller set of UI stages.
  val stage = when (snapshot.authState) {
    TelegramAuthState.NotStarted -> TelegramConnectStage.IDLE
    TelegramAuthState.WaitTdlibParameters,
    TelegramAuthState.LoggingOut,
    TelegramAuthState.Closing
    -> TelegramConnectStage.INITIALIZING
    TelegramAuthState.WaitPhoneNumber -> TelegramConnectStage.PHONE_NUMBER
    is TelegramAuthState.WaitEmailAddress -> TelegramConnectStage.EMAIL_ADDRESS
    is TelegramAuthState.WaitEmailCode -> TelegramConnectStage.EMAIL_CODE
    is TelegramAuthState.WaitCode -> TelegramConnectStage.CODE
    is TelegramAuthState.WaitPassword -> TelegramConnectStage.PASSWORD
    is TelegramAuthState.WaitQrConfirmation -> TelegramConnectStage.QR_CONFIRMATION
    TelegramAuthState.Ready -> TelegramConnectStage.READY
    TelegramAuthState.WaitRegistration,
    is TelegramAuthState.WaitPremiumPurchase,
    is TelegramAuthState.Unknown
    -> TelegramConnectStage.BLOCKED
    TelegramAuthState.Closed -> TelegramConnectStage.CLOSED
  }

  return TelegramConnectScreenState(
    stage = stage,
    errorMessage = snapshot.lastError?.message,
    qrActionMode = if (snapshot.authState is TelegramAuthState.WaitQrConfirmation) {
      TelegramQrActionMode.RETURN_TO_PHONE
    } else {
      TelegramQrActionMode.REQUEST_QR
    },
  )
}
