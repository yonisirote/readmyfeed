package com.yonisirote.readmyfeed.telegram.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class TelegramAuthStateTest {
  @Test
  fun authStateDataClassesRetainConstructorData() {
    val state = TelegramAuthState.WaitPassword(
      passwordHint = "pets",
      hasRecoveryEmailAddress = true,
      recoveryEmailAddressPattern = "p***@example.com",
    )

    assertEquals("pets", state.passwordHint)
    assertEquals(true, state.hasRecoveryEmailAddress)
    assertEquals("p***@example.com", state.recoveryEmailAddressPattern)
  }

  @Test
  fun authCodeDeliveryMethodStoresKindLengthAndHint() {
    val method = TelegramAuthCodeDeliveryMethod(
      kind = TelegramAuthCodeDeliveryMethodKind.FRAGMENT,
      length = 6,
      hint = "fragment.com",
    )

    assertEquals(TelegramAuthCodeDeliveryMethodKind.FRAGMENT, method.kind)
    assertEquals(6, method.length)
    assertEquals("fragment.com", method.hint)
  }
}
