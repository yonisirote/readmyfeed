package com.yonisirote.readmyfeed.x.auth

import android.webkit.CookieManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

interface XCookieReader {
  fun readCookies(): XCookieReadResult
}

class AndroidWebViewCookieReader(
  private val cookieManager: CookieManager = CookieManager.getInstance(),
  private val origins: List<String> = X_ALLOWED_ORIGINS,
) : XCookieReader {
  override fun readCookies(): XCookieReadResult {
    return try {
      cookieManager.flush()

      // Merge cookies from all origins. The primary origin (first in the list)
      // is applied last so its values take precedence over legacy domains.
      val mergedCookies = linkedMapOf<String, String>()
      for (origin in origins.distinct().reversed()) {
        mergedCookies.putAll(parseCookieHeader(cookieManager.getCookie(origin)))
      }

      evaluateCookies(mergedCookies)
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

suspend fun clearXWebViewCookies(
  cookieManager: CookieManager = CookieManager.getInstance(),
) {
  suspendCancellableCoroutine<Unit> { continuation ->
    cookieManager.removeAllCookies {
      cookieManager.flush()
      if (continuation.isActive) {
        continuation.resume(Unit)
      }
    }
  }
}
