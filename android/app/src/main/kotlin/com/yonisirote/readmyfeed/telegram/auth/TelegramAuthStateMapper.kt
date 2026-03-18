package com.yonisirote.readmyfeed.telegram.auth

import org.drinkless.tdlib.TdApi

fun mapTelegramAuthorizationState(state: TdApi.AuthorizationState?): TelegramAuthState {
  if (state == null) {
    return TelegramAuthState.NotStarted
  }

  return when (state) {
    is TdApi.AuthorizationStateWaitTdlibParameters -> TelegramAuthState.WaitTdlibParameters
    is TdApi.AuthorizationStateWaitPhoneNumber -> TelegramAuthState.WaitPhoneNumber
    is TdApi.AuthorizationStateWaitPremiumPurchase -> TelegramAuthState.WaitPremiumPurchase(
      storeProductId = state.storeProductId.orEmpty(),
      supportEmailAddress = state.supportEmailAddress.orEmpty(),
      supportEmailSubject = state.supportEmailSubject.orEmpty(),
    )
    is TdApi.AuthorizationStateWaitEmailAddress -> TelegramAuthState.WaitEmailAddress(
      allowAppleId = state.allowAppleId,
      allowGoogleId = state.allowGoogleId,
    )
    is TdApi.AuthorizationStateWaitEmailCode -> TelegramAuthState.WaitEmailCode(
      emailAddressPattern = state.codeInfo?.emailAddressPattern.orEmpty(),
      codeLength = state.codeInfo?.length ?: 0,
      resetState = mapTelegramEmailResetState(state.emailAddressResetState),
    )
    is TdApi.AuthorizationStateWaitCode -> TelegramAuthState.WaitCode(
      phoneNumber = state.codeInfo?.phoneNumber.orEmpty(),
      deliveryMethod = mapTelegramAuthCodeDeliveryMethod(state.codeInfo?.type),
      nextDeliveryMethod = mapTelegramAuthCodeDeliveryMethod(state.codeInfo?.nextType),
      timeoutSeconds = state.codeInfo?.timeout ?: 0,
    )
    is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> TelegramAuthState.WaitQrConfirmation(
      link = state.link.orEmpty(),
    )
    is TdApi.AuthorizationStateWaitRegistration -> TelegramAuthState.WaitRegistration
    is TdApi.AuthorizationStateWaitPassword -> TelegramAuthState.WaitPassword(
      passwordHint = state.passwordHint.orEmpty(),
      hasRecoveryEmailAddress = state.hasRecoveryEmailAddress,
      recoveryEmailAddressPattern = state.recoveryEmailAddressPattern.orEmpty(),
    )
    is TdApi.AuthorizationStateReady -> TelegramAuthState.Ready
    is TdApi.AuthorizationStateLoggingOut -> TelegramAuthState.LoggingOut
    is TdApi.AuthorizationStateClosing -> TelegramAuthState.Closing
    is TdApi.AuthorizationStateClosed -> TelegramAuthState.Closed
    else -> TelegramAuthState.Unknown(state.getConstructor())
  }
}

fun mapTelegramAuthCodeDeliveryMethod(type: TdApi.AuthenticationCodeType?): TelegramAuthCodeDeliveryMethod? {
  if (type == null) {
    return null
  }

  return when (type) {
    is TdApi.AuthenticationCodeTypeTelegramMessage -> TelegramAuthCodeDeliveryMethod(
      kind = TelegramAuthCodeDeliveryMethodKind.TELEGRAM_MESSAGE,
      length = type.length,
    )
    is TdApi.AuthenticationCodeTypeSms -> TelegramAuthCodeDeliveryMethod(
      kind = TelegramAuthCodeDeliveryMethodKind.SMS,
      length = type.length,
    )
    is TdApi.AuthenticationCodeTypeSmsWord -> TelegramAuthCodeDeliveryMethod(
      kind = TelegramAuthCodeDeliveryMethodKind.SMS_WORD,
      hint = type.firstLetter.orEmpty(),
    )
    is TdApi.AuthenticationCodeTypeSmsPhrase -> TelegramAuthCodeDeliveryMethod(
      kind = TelegramAuthCodeDeliveryMethodKind.SMS_PHRASE,
      hint = type.firstWord.orEmpty(),
    )
    is TdApi.AuthenticationCodeTypeCall -> TelegramAuthCodeDeliveryMethod(
      kind = TelegramAuthCodeDeliveryMethodKind.CALL,
      length = type.length,
    )
    is TdApi.AuthenticationCodeTypeFlashCall -> TelegramAuthCodeDeliveryMethod(
      kind = TelegramAuthCodeDeliveryMethodKind.FLASH_CALL,
      hint = type.pattern.orEmpty(),
    )
    is TdApi.AuthenticationCodeTypeMissedCall -> TelegramAuthCodeDeliveryMethod(
      kind = TelegramAuthCodeDeliveryMethodKind.MISSED_CALL,
      length = type.length,
      hint = type.phoneNumberPrefix.orEmpty(),
    )
    is TdApi.AuthenticationCodeTypeFragment -> TelegramAuthCodeDeliveryMethod(
      kind = TelegramAuthCodeDeliveryMethodKind.FRAGMENT,
      length = type.length,
      hint = type.url.orEmpty(),
    )
    is TdApi.AuthenticationCodeTypeFirebaseAndroid -> TelegramAuthCodeDeliveryMethod(
      kind = TelegramAuthCodeDeliveryMethodKind.FIREBASE_ANDROID,
      length = type.length,
    )
    is TdApi.AuthenticationCodeTypeFirebaseIos -> TelegramAuthCodeDeliveryMethod(
      kind = TelegramAuthCodeDeliveryMethodKind.FIREBASE_IOS,
      length = type.length,
    )
    else -> TelegramAuthCodeDeliveryMethod(
      kind = TelegramAuthCodeDeliveryMethodKind.UNKNOWN,
    )
  }
}

fun mapTelegramEmailResetState(state: TdApi.EmailAddressResetState?): TelegramEmailResetState? {
  return when (state) {
    is TdApi.EmailAddressResetStateAvailable -> TelegramEmailResetState.Available(
      waitPeriodSeconds = state.waitPeriod,
    )
    is TdApi.EmailAddressResetStatePending -> TelegramEmailResetState.Pending(
      resetInSeconds = state.resetIn,
    )
    null -> null
    else -> null
  }
}
