package com.yonisirote.readmyfeed.x.auth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XLoginCaptureCoordinatorTest {
  @Test
  fun retriesUntilRequiredCookiesAppear() = runBlocking {
    val cookieReader = SequencedCookieReader(
      listOf(
        evaluateCookies(mapOf("ct0" to "csrf")),
        evaluateCookies(mapOf("ct0" to "csrf", "auth_token" to "token")),
        evaluateCookies(mapOf("ct0" to "csrf", "auth_token" to "token", "twid" to "u%3D1")),
      ),
    )
    val sessionStore = InMemorySessionStore()
    val authService = XAuthService(cookieReader, sessionStore)
    val coordinator = XLoginCaptureCoordinator(authService)

    val session = coordinator.captureAndStoreSessionWithRetry(maxAttempts = 3, delayMs = 0)

    assertEquals("auth_token=token; ct0=csrf; twid=u%3D1;", session.cookieString)
    assertEquals(session.cookieString, sessionStore.value)
  }

  @Test
  fun throwsWhenRetriesAreExhausted() = runBlocking {
    val cookieReader = SequencedCookieReader(
      listOf(
        evaluateCookies(mapOf("ct0" to "csrf")),
        evaluateCookies(mapOf("ct0" to "csrf")),
        evaluateCookies(mapOf("ct0" to "csrf")),
      ),
    )
    val authService = XAuthService(cookieReader, InMemorySessionStore())
    val coordinator = XLoginCaptureCoordinator(authService)

    val error = try {
      coordinator.captureAndStoreSessionWithRetry(maxAttempts = 3, delayMs = 0)
      throw AssertionError("Expected XAuthException")
    } catch (error: XAuthException) {
      error
    }

    assertEquals(XAuthErrorCodes.COOKIE_MISSING_REQUIRED, error.code)
    assertTrue((error.context["missingRequired"] as? List<*>)?.contains("auth_token") == true)
  }
}

private class SequencedCookieReader(
  private val results: List<XCookieReadResult>,
) : XCookieReader {
  private var index = 0

  override fun readCookies(): XCookieReadResult {
    val safeIndex = index.coerceAtMost(results.lastIndex)
    index += 1
    return results[safeIndex]
  }
}

private class InMemorySessionStore : XSessionStore {
  var value: String? = null

  override fun get(): String? = value

  override fun set(cookieString: String) {
    value = cookieString
  }

  override fun clear() {
    value = null
  }
}
