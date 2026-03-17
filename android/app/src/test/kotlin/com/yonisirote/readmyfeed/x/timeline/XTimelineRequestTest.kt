package com.yonisirote.readmyfeed.x.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XTimelineRequestTest {

  // -- buildFollowingTimelineUrl --

  @Test
  fun urlStartsWithCorrectBaseUrl() {
    val url = buildFollowingTimelineUrl()
    assertTrue(
      url.startsWith("https://x.com/i/api/graphql/_qO7FJzShSKYWi9gtboE6A/HomeLatestTimeline"),
    )
  }

  @Test
  fun urlContainsVariablesWithCount() {
    val url = buildFollowingTimelineUrl(count = 20)
    assertTrue(url.contains("variables="))
    assertTrue(url.contains("%22count%22%3A20"))
  }

  @Test
  fun cursorIncludedWhenProvided() {
    val url = buildFollowingTimelineUrl(cursor = "abc-123")
    assertTrue(url.contains("abc-123"))
  }

  @Test
  fun cursorOmittedWhenNull() {
    val url = buildFollowingTimelineUrl(cursor = null)
    assertFalse(url.contains("cursor"))
  }

  @Test
  fun cursorOmittedWhenBlank() {
    val url = buildFollowingTimelineUrl(cursor = "  ")
    assertFalse(url.contains("%22cursor%22") || url.contains("\"cursor\""))
  }

  @Test
  fun defaultCountIsForty() {
    val url = buildFollowingTimelineUrl()
    assertTrue(url.contains("%22count%22%3A40"))
  }

  // -- buildTimelineHeaders --

  @Test
  fun containsAllExpectedHeaderKeys() {
    val headers = buildTimelineHeaders("cookie-value", "csrf-token")
    val expectedKeys = setOf(
      "accept",
      "authorization",
      "cookie",
      "referer",
      "x-csrf-token",
      "x-twitter-active-user",
      "x-twitter-auth-type",
      "x-twitter-client-language",
    )
    assertEquals(expectedKeys, headers.keys)
  }

  @Test
  fun authorizationContainsBearerPrefix() {
    val headers = buildTimelineHeaders("c", "t")
    assertTrue(headers["authorization"]!!.startsWith("Bearer "))
  }

  @Test
  fun cookieHeaderMatchesInput() {
    val headers = buildTimelineHeaders("my-cookie-string", "tok")
    assertEquals("my-cookie-string", headers["cookie"])
  }

  @Test
  fun csrfTokenIsSetCorrectly() {
    val headers = buildTimelineHeaders("c", "my-csrf-token")
    assertEquals("my-csrf-token", headers["x-csrf-token"])
  }

  @Test
  fun refererIsXHome() {
    val headers = buildTimelineHeaders("c", "t")
    assertEquals("https://x.com/home", headers["referer"])
  }

  // -- buildTimelineHttpRequest --

  @Test
  fun usesRequestCookieStringWhenProvided() {
    val request = XFollowingTimelineRequest(
      cookieString = "ct0=from-request; other=val;",
    )
    val result = buildTimelineHttpRequest(request, storedCookieString = "ct0=from-stored;")
    assertEquals("ct0=from-request; other=val;", result.headers["cookie"])
  }

  @Test
  fun fallsBackToStoredCookieStringWhenRequestCookieIsNull() {
    val request = XFollowingTimelineRequest(cookieString = null)
    val result = buildTimelineHttpRequest(request, storedCookieString = "ct0=stored-token;")
    assertEquals("ct0=stored-token;", result.headers["cookie"])
  }

  @Test
  fun extractsCt0FromCookieStringAsCSrfToken() {
    val request = XFollowingTimelineRequest(
      cookieString = "auth_token=abc; ct0=my-csrf-value; lang=en;",
    )
    val result = buildTimelineHttpRequest(request, storedCookieString = "")
    assertEquals("my-csrf-value", result.headers["x-csrf-token"])
  }

  @Test(expected = XTimelineException::class)
  fun throwsWhenCt0IsMissing() {
    val request = XFollowingTimelineRequest(cookieString = "auth_token=abc;")
    buildTimelineHttpRequest(request, storedCookieString = "no-ct0=here;")
  }

  @Test
  fun throwsWithCookieInvalidCodeWhenCt0Missing() {
    val request = XFollowingTimelineRequest(cookieString = "auth_token=abc;")
    try {
      buildTimelineHttpRequest(request, storedCookieString = "")
      throw AssertionError("Expected XTimelineException")
    } catch (e: XTimelineException) {
      assertEquals(XTimelineErrorCodes.COOKIE_INVALID, e.code)
    }
  }

  @Test(expected = XTimelineException::class)
  fun throwsWhenCookieStringIsEmpty() {
    val request = XFollowingTimelineRequest(cookieString = "")
    buildTimelineHttpRequest(request, storedCookieString = "")
  }

  @Test
  fun constructsCorrectUrlWithCountAndCursor() {
    val request = XFollowingTimelineRequest(count = 25, cursor = "next-page-cursor")
    val result = buildTimelineHttpRequest(request, storedCookieString = "ct0=tok;")
    assertTrue(result.url.contains("next-page-cursor"))
    assertTrue(result.url.contains("%22count%22%3A25"))
  }
}
