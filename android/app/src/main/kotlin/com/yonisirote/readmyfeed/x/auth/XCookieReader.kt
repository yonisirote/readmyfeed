package com.yonisirote.readmyfeed.x.auth

import android.webkit.CookieManager

interface XCookieReader {
  fun readCookies(): XCookieReadResult
}

class AndroidWebViewCookieReader(
  private val cookieManager: CookieManager = CookieManager.getInstance(),
  private val baseUrl: String = X_BASE_URL,
) : XCookieReader {
  override fun readCookies(): XCookieReadResult {
    return try {
      val cookieHeader = cookieManager.getCookie(baseUrl)
      evaluateCookies(parseCookieHeader(cookieHeader))
    } catch (error: Exception) {
      throw XAuthException(
        message = "Failed to read cookies",
        code = XAuthErrorCodes.COOKIE_READ_FAILED,
        context = mapOf("cause" to (error.message ?: error::class.java.simpleName)),
        cause = error,
      )
    }
  }
}
