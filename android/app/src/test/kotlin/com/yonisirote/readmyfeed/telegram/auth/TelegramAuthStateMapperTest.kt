package com.yonisirote.readmyfeed.telegram.auth

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelegramAuthStateMapperTest {
  @Test
  fun mapsWaitCodeStateWithDeliveryMetadata() {
    val state = TdApi.AuthorizationStateWaitCode(
      TdApi.AuthenticationCodeInfo(
        "+15551234567",
        TdApi.AuthenticationCodeTypeSms(6),
        TdApi.AuthenticationCodeTypeCall(6),
        30,
      ),
    )

    val mapped = mapTelegramAuthorizationState(state)

    assertEquals(
      TelegramAuthState.WaitCode(
        phoneNumber = "+15551234567",
        deliveryMethod = TelegramAuthCodeDeliveryMethod(
          kind = TelegramAuthCodeDeliveryMethodKind.SMS,
          length = 6,
        ),
        nextDeliveryMethod = TelegramAuthCodeDeliveryMethod(
          kind = TelegramAuthCodeDeliveryMethodKind.CALL,
          length = 6,
        ),
        timeoutSeconds = 30,
      ),
      mapped,
    )
  }

  @Test
  fun mapsWaitEmailCodeStateWithResetInfo() {
    val state = TdApi.AuthorizationStateWaitEmailCode(
      false,
      true,
      TdApi.EmailAddressAuthenticationCodeInfo("a***@example.com", 5),
      TdApi.EmailAddressResetStatePending(120),
    )

    val mapped = mapTelegramAuthorizationState(state)

    assertEquals(
      TelegramAuthState.WaitEmailCode(
        emailAddressPattern = "a***@example.com",
        codeLength = 5,
        resetState = TelegramEmailResetState.Pending(resetInSeconds = 120),
      ),
      mapped,
    )
  }

  @Test
  fun mapsPasswordAndQrStates() {
    assertEquals(
      TelegramAuthState.WaitPassword(
        passwordHint = "pets",
        hasRecoveryEmailAddress = true,
        recoveryEmailAddressPattern = "p***@example.com",
      ),
      mapTelegramAuthorizationState(
        TdApi.AuthorizationStateWaitPassword("pets", true, false, "p***@example.com"),
      ),
    )
    assertEquals(
      TelegramAuthState.WaitQrConfirmation(link = "tg://login?token=abc"),
      mapTelegramAuthorizationState(
        TdApi.AuthorizationStateWaitOtherDeviceConfirmation("tg://login?token=abc"),
      ),
    )
  }

  @Test
  fun mapsNullAndUnknownStatesSafely() {
    assertEquals(TelegramAuthState.NotStarted, mapTelegramAuthorizationState(null))

    val unknownState = object : TdApi.AuthorizationState() {
      override fun getConstructor(): Int {
        return 424242
      }
    }

    assertEquals(
      TelegramAuthState.Unknown(constructorId = 424242),
      mapTelegramAuthorizationState(unknownState),
    )
  }

  @Test
  fun mapsDeliveryMethodHints() {
    assertEquals(
      TelegramAuthCodeDeliveryMethod(
        kind = TelegramAuthCodeDeliveryMethodKind.FLASH_CALL,
        hint = "+97255***",
      ),
      mapTelegramAuthCodeDeliveryMethod(TdApi.AuthenticationCodeTypeFlashCall("+97255***")),
    )
    assertEquals(
      TelegramAuthCodeDeliveryMethod(
        kind = TelegramAuthCodeDeliveryMethodKind.MISSED_CALL,
        length = 4,
        hint = "+1555",
      ),
      mapTelegramAuthCodeDeliveryMethod(TdApi.AuthenticationCodeTypeMissedCall("+1555", 4)),
    )
    assertEquals(
      TelegramAuthCodeDeliveryMethod(
        kind = TelegramAuthCodeDeliveryMethodKind.FIREBASE_ANDROID,
        length = 6,
      ),
      mapTelegramAuthCodeDeliveryMethod(
        TdApi.AuthenticationCodeTypeFirebaseAndroid(null, 6),
      ),
    )
    assertNull(mapTelegramAuthCodeDeliveryMethod(null))
  }

  @Test
  fun mapsRemainingAuthorizationStates() {
    assertEquals(
      TelegramAuthState.WaitTdlibParameters,
      mapTelegramAuthorizationState(TdApi.AuthorizationStateWaitTdlibParameters()),
    )
    assertEquals(
      TelegramAuthState.WaitEmailAddress(
        allowAppleId = true,
        allowGoogleId = false,
      ),
      mapTelegramAuthorizationState(TdApi.AuthorizationStateWaitEmailAddress(true, false)),
    )
    assertEquals(
      TelegramAuthState.WaitPremiumPurchase(
        storeProductId = "premium_monthly",
        supportEmailAddress = "help@example.com",
        supportEmailSubject = "Need help",
      ),
      mapTelegramAuthorizationState(
        TdApi.AuthorizationStateWaitPremiumPurchase(
          "premium_monthly",
          "help@example.com",
          "Need help",
        ),
      ),
    )
    assertEquals(
      TelegramAuthState.WaitRegistration,
      mapTelegramAuthorizationState(TdApi.AuthorizationStateWaitRegistration()),
    )
    assertEquals(
      TelegramAuthState.LoggingOut,
      mapTelegramAuthorizationState(TdApi.AuthorizationStateLoggingOut()),
    )
    assertEquals(
      TelegramAuthState.Closing,
      mapTelegramAuthorizationState(TdApi.AuthorizationStateClosing()),
    )
    assertEquals(
      TelegramAuthState.Closed,
      mapTelegramAuthorizationState(TdApi.AuthorizationStateClosed()),
    )
  }

  @Test
  fun mapsAvailableEmailResetState() {
    assertEquals(
      TelegramEmailResetState.Available(waitPeriodSeconds = 3600),
      mapTelegramEmailResetState(TdApi.EmailAddressResetStateAvailable(3600)),
    )
  }

  @Test
  fun mapsAdditionalDeliveryMethods() {
    assertEquals(
      TelegramAuthCodeDeliveryMethod(
        kind = TelegramAuthCodeDeliveryMethodKind.TELEGRAM_MESSAGE,
        length = 5,
      ),
      mapTelegramAuthCodeDeliveryMethod(TdApi.AuthenticationCodeTypeTelegramMessage(5)),
    )
    assertEquals(
      TelegramAuthCodeDeliveryMethod(
        kind = TelegramAuthCodeDeliveryMethodKind.SMS_WORD,
        hint = "W",
      ),
      mapTelegramAuthCodeDeliveryMethod(TdApi.AuthenticationCodeTypeSmsWord("W")),
    )
    assertEquals(
      TelegramAuthCodeDeliveryMethod(
        kind = TelegramAuthCodeDeliveryMethodKind.SMS_PHRASE,
        hint = "hello",
      ),
      mapTelegramAuthCodeDeliveryMethod(TdApi.AuthenticationCodeTypeSmsPhrase("hello")),
    )
    assertEquals(
      TelegramAuthCodeDeliveryMethod(
        kind = TelegramAuthCodeDeliveryMethodKind.FRAGMENT,
        length = 5,
        hint = "https://fragment.com/login",
      ),
      mapTelegramAuthCodeDeliveryMethod(
        TdApi.AuthenticationCodeTypeFragment("https://fragment.com/login", 5),
      ),
    )
    assertEquals(
      TelegramAuthCodeDeliveryMethod(
        kind = TelegramAuthCodeDeliveryMethodKind.FIREBASE_IOS,
        length = 6,
      ),
      mapTelegramAuthCodeDeliveryMethod(TdApi.AuthenticationCodeTypeFirebaseIos("", 0, 6)),
    )

    val unknownType = object : TdApi.AuthenticationCodeType() {
      override fun getConstructor(): Int = 999999
    }
    assertEquals(
      TelegramAuthCodeDeliveryMethod(kind = TelegramAuthCodeDeliveryMethodKind.UNKNOWN),
      mapTelegramAuthCodeDeliveryMethod(unknownType),
    )
  }
}
