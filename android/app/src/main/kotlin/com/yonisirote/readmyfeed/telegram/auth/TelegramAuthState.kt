package com.yonisirote.readmyfeed.telegram.auth

enum class TelegramAuthCodeDeliveryMethodKind {
  TELEGRAM_MESSAGE,
  SMS,
  SMS_WORD,
  SMS_PHRASE,
  CALL,
  FLASH_CALL,
  MISSED_CALL,
  FRAGMENT,
  FIREBASE_ANDROID,
  FIREBASE_IOS,
  UNKNOWN,
}

data class TelegramAuthCodeDeliveryMethod(
  val kind: TelegramAuthCodeDeliveryMethodKind,
  val length: Int? = null,
  val hint: String? = null,
)

sealed interface TelegramEmailResetState {
  data class Available(
    val waitPeriodSeconds: Int,
  ) : TelegramEmailResetState

  data class Pending(
    val resetInSeconds: Int,
  ) : TelegramEmailResetState
}

sealed interface TelegramAuthState {
  data object NotStarted : TelegramAuthState

  data object WaitTdlibParameters : TelegramAuthState

  data object WaitPhoneNumber : TelegramAuthState

  data class WaitPremiumPurchase(
    val storeProductId: String,
    val supportEmailAddress: String,
    val supportEmailSubject: String,
  ) : TelegramAuthState

  data class WaitEmailAddress(
    val allowAppleId: Boolean,
    val allowGoogleId: Boolean,
  ) : TelegramAuthState

  data class WaitEmailCode(
    val emailAddressPattern: String,
    val codeLength: Int,
    val resetState: TelegramEmailResetState?,
  ) : TelegramAuthState

  data class WaitCode(
    val phoneNumber: String,
    val deliveryMethod: TelegramAuthCodeDeliveryMethod?,
    val nextDeliveryMethod: TelegramAuthCodeDeliveryMethod?,
    val timeoutSeconds: Int,
  ) : TelegramAuthState

  data class WaitQrConfirmation(
    val link: String,
  ) : TelegramAuthState

  data object WaitRegistration : TelegramAuthState

  data class WaitPassword(
    val passwordHint: String,
    val hasRecoveryEmailAddress: Boolean,
    val recoveryEmailAddressPattern: String,
  ) : TelegramAuthState

  data object Ready : TelegramAuthState

  data object LoggingOut : TelegramAuthState

  data object Closing : TelegramAuthState

  data object Closed : TelegramAuthState

  data class Unknown(
    val constructorId: Int,
  ) : TelegramAuthState
}
