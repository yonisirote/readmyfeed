package com.yonisirote.readmyfeed.providers.x.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class XAuthServiceTest {
  private lateinit var cookieReader: StubCookieReader
  private lateinit var sessionStore: FakeSessionStore
  private lateinit var service: XAuthService

  @Before
  fun setUp() {
    cookieReader = StubCookieReader()
    sessionStore = FakeSessionStore()
    service = XAuthService(cookieReader, sessionStore)
  }

  // -- shouldAttemptCapture --

  @Test
  fun shouldAttemptCaptureReturnsTrueForLoggedInUrl() {
    assertTrue(service.shouldAttemptCapture("https://x.com/home"))
  }

  @Test
  fun shouldAttemptCaptureReturnsTrueForOtherLoggedInPaths() {
    assertTrue(service.shouldAttemptCapture("https://x.com/notifications"))
    assertTrue(service.shouldAttemptCapture("https://x.com/messages"))
    assertTrue(service.shouldAttemptCapture("https://x.com/explore"))
    assertTrue(service.shouldAttemptCapture("https://x.com/settings"))
  }

  @Test
  fun shouldAttemptCaptureReturnsFalseForLoginUrl() {
    assertFalse(service.shouldAttemptCapture("https://x.com/i/flow/login"))
  }

  @Test
  fun shouldAttemptCaptureReturnsFalseForNull() {
    assertFalse(service.shouldAttemptCapture(null))
  }

  @Test
  fun shouldAttemptCaptureReturnsFalseForOtherDomains() {
    assertFalse(service.shouldAttemptCapture("https://example.com/home"))
    assertFalse(service.shouldAttemptCapture("https://twitter.com/home"))
  }

  // -- captureSession --

  @Test
  fun captureSessionReturnsSessionWhenAllRequiredCookiesPresent() {
    cookieReader.result = allRequiredCookiesResult()

    val result = service.captureSession()

    assertNotNull(result.session)
    assertEquals(listOf("auth_token", "ct0", "twid"), result.session!!.cookieNames)
  }

  @Test
  fun captureSessionReturnsNullSessionWhenRequiredCookiesMissing() {
    cookieReader.result = missingRequiredCookiesResult()

    val result = service.captureSession()

    assertNull(result.session)
  }

  @Test
  fun captureSessionIncludesCookieResultWhenSessionPresent() {
    val expected = allRequiredCookiesResult()
    cookieReader.result = expected

    val result = service.captureSession()

    assertEquals(expected, result.cookieResult)
  }

  @Test
  fun captureSessionIncludesCookieResultWhenSessionNull() {
    val expected = missingRequiredCookiesResult()
    cookieReader.result = expected

    val result = service.captureSession()

    assertEquals(expected, result.cookieResult)
  }

  @Test(expected = XAuthException::class)
  fun captureSessionPropagatesXAuthExceptionFromCookieReader() {
    cookieReader.exception = XAuthException(
      message = "Failed to read cookies",
      code = XAuthErrorCodes.COOKIE_READ_FAILED,
    )

    service.captureSession()
  }

  // -- captureAndStoreSession --

  @Test
  fun captureAndStoreSessionReturnsSessionWhenAllCookiesPresent() {
    cookieReader.result = allRequiredCookiesResult()

    val session = service.captureAndStoreSession()

    assertEquals("auth_token=token; ct0=csrf; twid=u%3D1;", session.cookieString)
    assertEquals(listOf("auth_token", "ct0", "twid"), session.cookieNames)
  }

  @Test
  fun captureAndStoreSessionStoresSessionInStore() {
    cookieReader.result = allRequiredCookiesResult()

    val session = service.captureAndStoreSession()

    assertEquals(session.cookieString, sessionStore.value)
  }

  @Test
  fun captureAndStoreSessionThrowsWhenRequiredCookiesMissing() {
    cookieReader.result = missingRequiredCookiesResult()

    val error = try {
      service.captureAndStoreSession()
      throw AssertionError("Expected XAuthException")
    } catch (e: XAuthException) {
      e
    }

    assertEquals(XAuthErrorCodes.COOKIE_MISSING_REQUIRED, error.code)
  }

  @Test
  fun captureAndStoreSessionErrorIncludesMissingCookieContext() {
    cookieReader.result = missingRequiredCookiesResult()

    val error = try {
      service.captureAndStoreSession()
      throw AssertionError("Expected XAuthException")
    } catch (e: XAuthException) {
      e
    }

    @Suppress("UNCHECKED_CAST")
    val missingRequired = error.context["missingRequired"] as List<String>
    @Suppress("UNCHECKED_CAST")
    val missingOptional = error.context["missingOptional"] as List<String>

    assertTrue(missingRequired.contains("auth_token"))
    assertTrue(missingRequired.contains("twid"))
    assertEquals(listOf("kdt"), missingOptional)
  }

  // -- storeSession --

  @Test
  fun storeSessionStoresCookieString() {
    service.storeSession("auth_token=abc; ct0=def;")

    assertEquals("auth_token=abc; ct0=def;", sessionStore.value)
  }

  @Test
  fun storeSessionOverwritesPreviousValue() {
    service.storeSession("first")
    service.storeSession("second")

    assertEquals("second", sessionStore.value)
  }

  // -- loadStoredSession --

  @Test
  fun loadStoredSessionReturnsStoredValue() {
    sessionStore.value = "auth_token=token; ct0=csrf;"

    assertEquals("auth_token=token; ct0=csrf;", service.loadStoredSession())
  }

  @Test
  fun loadStoredSessionReturnsNullWhenEmpty() {
    assertNull(service.loadStoredSession())
  }

  // -- clearStoredSession --

  @Test
  fun clearStoredSessionClearsValue() {
    sessionStore.value = "auth_token=token;"

    service.clearStoredSession()

    assertNull(sessionStore.value)
  }

  @Test
  fun loadStoredSessionReturnsNullAfterClear() {
    service.storeSession("auth_token=token;")
    service.clearStoredSession()

    assertNull(service.loadStoredSession())
  }

  // -- Helpers --

  private fun allRequiredCookiesResult(): XCookieReadResult {
    return evaluateCookies(
      mapOf("auth_token" to "token", "ct0" to "csrf", "twid" to "u%3D1"),
    )
  }

  private fun missingRequiredCookiesResult(): XCookieReadResult {
    return evaluateCookies(mapOf("ct0" to "csrf"))
  }
}

private class StubCookieReader : XCookieReader {
  var result: XCookieReadResult = XCookieReadResult(
    cookies = emptyMap(),
    missingRequired = emptyList(),
    missingOptional = emptyList(),
    hasRequired = false,
  )
  var exception: XAuthException? = null

  override fun readCookies(): XCookieReadResult {
    exception?.let { throw it }
    return result
  }
}

private class FakeSessionStore : XSessionStore {
  var value: String? = null

  override fun get(): String? = value

  override fun set(cookieString: String) {
    value = cookieString
  }

  override fun clear() {
    value = null
  }
}
