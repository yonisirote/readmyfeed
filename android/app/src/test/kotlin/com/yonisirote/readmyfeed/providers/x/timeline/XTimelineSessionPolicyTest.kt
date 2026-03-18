package com.yonisirote.readmyfeed.providers.x.timeline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XTimelineSessionPolicyTest {
  @Test
  fun unauthorizedTimelineErrorsClearStoredSession() {
    assertTrue(
      shouldClearXTimelineSession(
        XTimelineException(
          message = "Failed to fetch following timeline (status 401).",
          code = XTimelineErrorCodes.REQUEST_FAILED,
          context = mapOf("status" to 401),
        ),
      ),
    )
    assertTrue(
      shouldClearXTimelineSession(
        XTimelineException(
          message = "Invalid cookie.",
          code = XTimelineErrorCodes.COOKIE_INVALID,
        ),
      ),
    )
    assertFalse(
      shouldClearXTimelineSession(
        XTimelineException(
          message = "Server error.",
          code = XTimelineErrorCodes.REQUEST_FAILED,
          context = mapOf("status" to 500),
        ),
      ),
    )
  }
}
