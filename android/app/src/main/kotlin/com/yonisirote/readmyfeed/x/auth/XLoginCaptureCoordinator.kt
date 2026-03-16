package com.yonisirote.readmyfeed.x.auth

import kotlinx.coroutines.delay

class XLoginCaptureCoordinator(
  private val authService: XAuthService,
) {
  fun shouldCaptureOnNavigation(url: String?): Boolean {
    return authService.shouldAttemptCapture(url)
  }

  fun shouldAttemptFallbackCapture(url: String?): Boolean {
    return hasAllowedXOrigin(url)
  }

  fun captureAndStoreSessionOnce(): XAuthSession {
    return authService.captureAndStoreSession()
  }

  suspend fun captureAndStoreSessionWithRetry(
    maxAttempts: Int = 3,
    delayMs: Long = 800,
  ): XAuthSession {
    var lastError: XAuthException? = null

    for (attempt in 1..maxAttempts) {
      try {
        return authService.captureAndStoreSession()
      } catch (error: XAuthException) {
        lastError = error
        val shouldRetry = attempt < maxAttempts && error.code == XAuthErrorCodes.COOKIE_MISSING_REQUIRED
        if (shouldRetry) {
          delay(delayMs)
          continue
        }

        throw error
      }
    }

    throw lastError ?: XAuthException(
      message = "Cookie capture exhausted retries",
      code = XAuthErrorCodes.COOKIE_MISSING_REQUIRED,
    )
  }
}
