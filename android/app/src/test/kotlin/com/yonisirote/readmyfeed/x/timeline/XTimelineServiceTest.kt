package com.yonisirote.readmyfeed.x.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.IOException

class XTimelineServiceTest {
  @Test
  fun wrapsBodyReadFailuresAsTimelineExceptions() {
    val ioError = IOException("socket closed")

    try {
      readTimelineResponseBody {
        throw ioError
      }
    } catch (error: XTimelineException) {
      assertEquals(XTimelineErrorCodes.REQUEST_FAILED, error.code)
      assertEquals("Failed to read following timeline response.", error.message)
      assertEquals("socket closed", error.context["cause"])
      assertSame(ioError, error.cause)
      return
    }

    throw AssertionError("Expected XTimelineException")
  }
}
