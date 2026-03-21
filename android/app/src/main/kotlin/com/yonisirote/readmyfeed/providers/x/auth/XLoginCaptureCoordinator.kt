package com.yonisirote.readmyfeed.providers.x.auth

import kotlinx.coroutines.delay

class XLoginCaptureCoordinator(
  private val authService: XAuthService,
) {
  fun shouldCaptureOnNavigation(url: String?): Boolean {
    return authService.shouldAttemptCapture(url)
  }

  fun shouldAttemptFallbackCapture(url: String?): Boolean {
    // Fallback capture accepts any known X origin because post-login redirects are not consistent.
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
        // WebView cookies can lag behind navigation, so only retry the missing-cookie case.
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
