package com.yonisirote.readmyfeed.providers.telegram.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TelegramClientExceptionTest {
  @Test
  fun preservesMessageCodeContextAndCause() {
    val cause = IllegalStateException("boom")

    val error = TelegramClientException(
      message = "Failed",
      code = TelegramClientErrorCodes.REQUEST_FAILED,
      context = mapOf("request" to "loadChats"),
      cause = cause,
    )

    assertEquals("Failed", error.message)
    assertEquals(TelegramClientErrorCodes.REQUEST_FAILED, error.code)
    assertEquals(mapOf("request" to "loadChats"), error.context)
    assertSame(cause, error.cause)
  }
}
