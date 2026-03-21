package com.yonisirote.readmyfeed.providers.x.timeline

private val sessionInvalidStatuses: Set<Int> = setOf(401, 403)

fun shouldClearXTimelineSession(error: XTimelineException): Boolean {
  // X often reports expired cookie sessions as 403, not only as an explicit auth error.
  return error.code == XTimelineErrorCodes.SESSION_MISSING ||
    error.code == XTimelineErrorCodes.COOKIE_INVALID ||
    (error.code == XTimelineErrorCodes.REQUEST_FAILED &&
      (error.context["status"] as? Int) in sessionInvalidStatuses)
}
