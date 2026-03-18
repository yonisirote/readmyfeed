package com.yonisirote.readmyfeed.telegram.ui

import com.yonisirote.readmyfeed.telegram.auth.TelegramAuthState
import com.yonisirote.readmyfeed.telegram.client.TelegramClientError
import com.yonisirote.readmyfeed.telegram.client.TelegramClientSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class TelegramConnectScreenStateTest {
  @Test
  fun readyStateResolvesToReadyStage() {
    val state = resolveTelegramConnectScreenState(
      TelegramClientSnapshot(authState = TelegramAuthState.Ready),
    )

    assertEquals(TelegramConnectStage.READY, state.stage)
    assertEquals(null, state.errorMessage)
    assertEquals(TelegramQrActionMode.REQUEST_QR, state.qrActionMode)
  }

  @Test
  fun codeStateResolvesToCodeStageAndCarriesError() {
    val state = resolveTelegramConnectScreenState(
      TelegramClientSnapshot(
        authState = TelegramAuthState.WaitCode(
          phoneNumber = "+15551234567",
          deliveryMethod = null,
          nextDeliveryMethod = null,
          timeoutSeconds = 30,
        ),
        lastError = TelegramClientError(
          code = "ERR",
          message = "Wrong code",
        ),
      ),
    )

    assertEquals(TelegramConnectStage.CODE, state.stage)
    assertEquals("Wrong code", state.errorMessage)
  }

  @Test
  fun qrStateSwitchesActionToReturnToPhone() {
    val state = resolveTelegramConnectScreenState(
      TelegramClientSnapshot(
        authState = TelegramAuthState.WaitQrConfirmation(
          link = "tg://login",
        ),
      ),
    )

    assertEquals(TelegramConnectStage.QR_CONFIRMATION, state.stage)
    assertEquals(TelegramQrActionMode.RETURN_TO_PHONE, state.qrActionMode)
  }

  @Test
  fun phoneStateKeepsQrActionInRequestMode() {
    val state = resolveTelegramConnectScreenState(
      TelegramClientSnapshot(
        authState = TelegramAuthState.WaitPhoneNumber,
      ),
    )

    assertEquals(TelegramConnectStage.PHONE_NUMBER, state.stage)
    assertEquals(TelegramQrActionMode.REQUEST_QR, state.qrActionMode)
  }

  @Test
  fun mapsRemainingStages() {
    assertEquals(
      TelegramConnectStage.IDLE,
      resolveTelegramConnectScreenState(TelegramClientSnapshot(authState = TelegramAuthState.NotStarted)).stage,
    )
    assertEquals(
      TelegramConnectStage.INITIALIZING,
      resolveTelegramConnectScreenState(TelegramClientSnapshot(authState = TelegramAuthState.WaitTdlibParameters)).stage,
    )
    assertEquals(
      TelegramConnectStage.INITIALIZING,
      resolveTelegramConnectScreenState(TelegramClientSnapshot(authState = TelegramAuthState.LoggingOut)).stage,
    )
    assertEquals(
      TelegramConnectStage.INITIALIZING,
      resolveTelegramConnectScreenState(TelegramClientSnapshot(authState = TelegramAuthState.Closing)).stage,
    )
    assertEquals(
      TelegramConnectStage.EMAIL_ADDRESS,
      resolveTelegramConnectScreenState(
        TelegramClientSnapshot(
          authState = TelegramAuthState.WaitEmailAddress(
            allowAppleId = true,
            allowGoogleId = false,
          ),
        ),
      ).stage,
    )
    assertEquals(
      TelegramConnectStage.EMAIL_CODE,
      resolveTelegramConnectScreenState(
        TelegramClientSnapshot(
          authState = TelegramAuthState.WaitEmailCode(
            emailAddressPattern = "a***@example.com",
            codeLength = 5,
            resetState = null,
          ),
        ),
      ).stage,
    )
    assertEquals(
      TelegramConnectStage.PASSWORD,
      resolveTelegramConnectScreenState(
        TelegramClientSnapshot(
          authState = TelegramAuthState.WaitPassword(
            passwordHint = "pets",
            hasRecoveryEmailAddress = false,
            recoveryEmailAddressPattern = "",
          ),
        ),
      ).stage,
    )
    assertEquals(
      TelegramConnectStage.BLOCKED,
      resolveTelegramConnectScreenState(TelegramClientSnapshot(authState = TelegramAuthState.WaitRegistration)).stage,
    )
    assertEquals(
      TelegramConnectStage.BLOCKED,
      resolveTelegramConnectScreenState(
        TelegramClientSnapshot(
          authState = TelegramAuthState.WaitPremiumPurchase("prod", "help@example.com", "subject"),
        ),
      ).stage,
    )
    assertEquals(
      TelegramConnectStage.BLOCKED,
      resolveTelegramConnectScreenState(
        TelegramClientSnapshot(authState = TelegramAuthState.Unknown(42)),
      ).stage,
    )
    assertEquals(
      TelegramConnectStage.CLOSED,
      resolveTelegramConnectScreenState(TelegramClientSnapshot(authState = TelegramAuthState.Closed)).stage,
    )
  }
}
