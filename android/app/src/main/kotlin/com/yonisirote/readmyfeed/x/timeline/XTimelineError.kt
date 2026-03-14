package com.yonisirote.readmyfeed.x.timeline

class XTimelineException(
  message: String,
  val code: String,
  val context: Map<String, Any?> = emptyMap(),
  cause: Throwable? = null,
) : Exception(message, cause)

object XTimelineErrorCodes {
  const val SESSION_MISSING = "X_TIMELINE_SESSION_MISSING"
  const val COOKIE_INVALID = "X_TIMELINE_COOKIE_INVALID"
  const val REQUEST_FAILED = "X_TIMELINE_REQUEST_FAILED"
  const val RESPONSE_INVALID = "X_TIMELINE_RESPONSE_INVALID"
}
