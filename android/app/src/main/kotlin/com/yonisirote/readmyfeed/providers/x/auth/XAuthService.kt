package com.yonisirote.readmyfeed.providers.x.auth

class XAuthService(
  private val cookieReader: XCookieReader,
  private val sessionStore: XSessionStore,
) {
  fun shouldAttemptCapture(url: String?): Boolean {
    return looksLikeLoggedInUrl(url)
  }

  fun captureSession(): XAuthCaptureResult {
    val cookieResult = cookieReader.readCookies()
    if (!cookieResult.hasRequired) {
      return XAuthCaptureResult(session = null, cookieResult = cookieResult)
    }

    val session = createSessionFromCookies(cookieResult.cookies)
    return XAuthCaptureResult(session = session, cookieResult = cookieResult)
  }

  fun captureAndStoreSession(): XAuthSession {
    val captureResult = captureSession()
    val session = captureResult.session ?: throw XAuthException(
      message = "Missing required cookies",
      code = XAuthErrorCodes.COOKIE_MISSING_REQUIRED,
      context = mapOf(
        "missingRequired" to captureResult.cookieResult.missingRequired,
        "missingOptional" to captureResult.cookieResult.missingOptional,
      ),
    )

    sessionStore.set(session.cookieString)
    return session
  }

  fun storeSession(cookieString: String) {
    sessionStore.set(cookieString)
  }

  fun loadStoredSession(): String? {
    return sessionStore.get()
  }

  fun clearStoredSession() {
    sessionStore.clear()
  }
}
