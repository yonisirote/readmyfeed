package com.yonisirote.readmyfeed.providers.x.auth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XLoginCaptureCoordinatorTest {
  @Test
  fun retriesUntilRequiredCookiesAppear() = runBlocking {
    // Mimics WebView cookies showing up over a few reads after navigation finishes.
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

  // -- shouldCaptureOnNavigation --

  @Test
  fun capturesOnNavigationToHomePage() {
    val coordinator = buildCoordinator()
    assertTrue(coordinator.shouldCaptureOnNavigation("https://x.com/home"))
  }

  @Test
  fun doesNotCaptureOnLoginPage() {
    val coordinator = buildCoordinator()
    assertFalse(coordinator.shouldCaptureOnNavigation("https://x.com/i/flow/login"))
  }

  @Test
  fun doesNotCaptureOnNull() {
    val coordinator = buildCoordinator()
    assertFalse(coordinator.shouldCaptureOnNavigation(null))
  }

  @Test
  fun doesNotCaptureOnTwitterDomain() {
    val coordinator = buildCoordinator()
    assertFalse(coordinator.shouldCaptureOnNavigation("https://twitter.com/home"))
  }

  // -- shouldAttemptFallbackCapture --

  @Test
  fun allowsFallbackCaptureForXDomain() {
    val coordinator = buildCoordinator()
    assertTrue(coordinator.shouldAttemptFallbackCapture("https://x.com/some/path"))
  }

  @Test
  fun allowsFallbackCaptureForTwitterDomain() {
    val coordinator = buildCoordinator()
    assertTrue(coordinator.shouldAttemptFallbackCapture("https://twitter.com/anything"))
  }

  @Test
  fun allowsFallbackCaptureForMobileTwitter() {
    val coordinator = buildCoordinator()
    assertTrue(coordinator.shouldAttemptFallbackCapture("https://mobile.twitter.com/page"))
  }

  @Test
  fun deniesFallbackCaptureForUnknownDomain() {
    val coordinator = buildCoordinator()
    assertFalse(coordinator.shouldAttemptFallbackCapture("https://example.com"))
  }

  @Test
  fun deniesFallbackCaptureForNull() {
    val coordinator = buildCoordinator()
    assertFalse(coordinator.shouldAttemptFallbackCapture(null))
  }

  // -- captureAndStoreSessionOnce --

  @Test
  fun capturesAndStoresSessionOnce() {
    val allCookies = mapOf("ct0" to "csrf", "auth_token" to "token", "twid" to "u%3D1")
    val cookieReader = SequencedCookieReader(listOf(evaluateCookies(allCookies)))
    val sessionStore = InMemorySessionStore()
    val coordinator = XLoginCaptureCoordinator(XAuthService(cookieReader, sessionStore))

    val session = coordinator.captureAndStoreSessionOnce()

    assertEquals("auth_token=token; ct0=csrf; twid=u%3D1;", session.cookieString)
    assertNotNull(sessionStore.value)
    assertEquals(session.cookieString, sessionStore.value)
  }

  @Test
  fun throwsOnMissingRequiredCookies() {
    val cookieReader = SequencedCookieReader(listOf(evaluateCookies(mapOf("ct0" to "csrf"))))
    val coordinator = XLoginCaptureCoordinator(XAuthService(cookieReader, InMemorySessionStore()))

    val error = try {
      coordinator.captureAndStoreSessionOnce()
      throw AssertionError("Expected XAuthException")
    } catch (error: XAuthException) {
      error
    }

    assertEquals(XAuthErrorCodes.COOKIE_MISSING_REQUIRED, error.code)
  }

  // -- captureAndStoreSessionWithRetry --

  @Test
  fun succeedsOnFirstAttemptWithoutRetry() = runBlocking {
    val allCookies = mapOf("ct0" to "csrf", "auth_token" to "token", "twid" to "u%3D1")
    val cookieReader = SequencedCookieReader(listOf(evaluateCookies(allCookies)))
    val sessionStore = InMemorySessionStore()
    val coordinator = XLoginCaptureCoordinator(XAuthService(cookieReader, sessionStore))

    val session = coordinator.captureAndStoreSessionWithRetry(maxAttempts = 3, delayMs = 0)

    assertEquals("auth_token=token; ct0=csrf; twid=u%3D1;", session.cookieString)
    assertEquals(session.cookieString, sessionStore.value)
  }

  @Test
  fun doesNotRetryOnNonMissingRequiredError() = runBlocking {
    val cookieReader = FailingCookieReader(
      XAuthException(
        message = "Store failed",
        code = XAuthErrorCodes.SESSION_STORE_FAILED,
      ),
    )
    val coordinator = XLoginCaptureCoordinator(XAuthService(cookieReader, InMemorySessionStore()))

    val error = try {
      coordinator.captureAndStoreSessionWithRetry(maxAttempts = 3, delayMs = 0)
      throw AssertionError("Expected XAuthException")
    } catch (error: XAuthException) {
      error
    }

    assertEquals(XAuthErrorCodes.SESSION_STORE_FAILED, error.code)
    assertEquals(1, cookieReader.callCount)
  }

  @Test
  fun singleAttemptWithMaxAttemptsOne() = runBlocking {
    val cookieReader = SequencedCookieReader(listOf(evaluateCookies(mapOf("ct0" to "csrf"))))
    val coordinator = XLoginCaptureCoordinator(XAuthService(cookieReader, InMemorySessionStore()))

    val error = try {
      coordinator.captureAndStoreSessionWithRetry(maxAttempts = 1, delayMs = 0)
      throw AssertionError("Expected XAuthException")
    } catch (error: XAuthException) {
      error
    }

    assertEquals(XAuthErrorCodes.COOKIE_MISSING_REQUIRED, error.code)
  }

  // -- helpers --

  private fun buildCoordinator(): XLoginCaptureCoordinator {
    val cookieReader = SequencedCookieReader(listOf(evaluateCookies(emptyMap())))
    return XLoginCaptureCoordinator(XAuthService(cookieReader, InMemorySessionStore()))
  }
}

private class SequencedCookieReader(
  private val results: List<XCookieReadResult>,
) : XCookieReader {
  private var index = 0

  override fun readCookies(): XCookieReadResult {
    // Keep returning the final snapshot so retry callers can outlive the scripted sequence.
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

private class FailingCookieReader(
  private val error: XAuthException,
) : XCookieReader {
  var callCount = 0

  override fun readCookies(): XCookieReadResult {
    callCount += 1
    throw error
  }
}
