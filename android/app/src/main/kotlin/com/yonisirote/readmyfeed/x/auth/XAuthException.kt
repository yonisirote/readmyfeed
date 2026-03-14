package com.yonisirote.readmyfeed.x.auth

class XAuthException(
  message: String,
  val code: String,
  val context: Map<String, Any?> = emptyMap(),
  cause: Throwable? = null,
) : Exception(message, cause)

object XAuthErrorCodes {
  const val COOKIE_READ_FAILED = "X_AUTH_COOKIE_READ_FAILED"
  const val COOKIE_MISSING_REQUIRED = "X_AUTH_COOKIE_MISSING_REQUIRED"
  const val COOKIE_STRING_INVALID = "X_AUTH_COOKIE_STRING_INVALID"
  const val WEBVIEW_NOT_READY = "X_AUTH_WEBVIEW_NOT_READY"
  const val SESSION_STORE_FAILED = "X_AUTH_SESSION_STORE_FAILED"
}
