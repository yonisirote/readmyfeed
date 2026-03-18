package com.yonisirote.readmyfeed.telegram.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TelegramAuthExceptionTest {
  @Test
  fun preservesMessageCodeContextAndCause() {
    val cause = IllegalArgumentException("bad input")

    val error = TelegramAuthException(
      message = "Phone number is required.",
      code = TelegramAuthErrorCodes.PHONE_NUMBER_REQUIRED,
      context = mapOf("field" to "phone"),
      cause = cause,
    )

    assertEquals("Phone number is required.", error.message)
    assertEquals(TelegramAuthErrorCodes.PHONE_NUMBER_REQUIRED, error.code)
    assertEquals(mapOf("field" to "phone"), error.context)
    assertSame(cause, error.cause)
  }
}
