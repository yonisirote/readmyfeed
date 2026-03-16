package com.yonisirote.readmyfeed.x.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XAuthUtilsTest {
  @Test
  fun detectsLoggedInUrlsOnKnownPaths() {
    assertTrue(looksLikeLoggedInUrl("https://x.com/home"))
    assertTrue(looksLikeLoggedInUrl("https://x.com/messages"))
    assertFalse(looksLikeLoggedInUrl("https://twitter.com/home"))
    assertFalse(looksLikeLoggedInUrl("https://x.com/i/flow/login"))
  }

  @Test
  fun detectsAllowedOriginsForFallbackCapture() {
    assertTrue(hasAllowedXOrigin("https://x.com/i/flow/login"))
    assertTrue(hasAllowedXOrigin("https://twitter.com/home"))
    assertFalse(hasAllowedXOrigin("https://example.com/home"))
    assertFalse(hasAllowedXOrigin(null))
  }

  @Test
  fun parsesAndBuildsCookieHeaderInStableOrder() {
    val cookies = parseCookieHeader("twid=u%3D1; ct0=csrf; auth_token=token; kdt=device; extra=ignored")
    val cookieString = buildCookieString(cookies)

    assertEquals("auth_token=token; ct0=csrf; kdt=device; twid=u%3D1;", cookieString)
  }

  @Test
  fun evaluatesMissingRequiredCookies() {
    val result = evaluateCookies(mapOf("ct0" to "csrf"))

    assertFalse(result.hasRequired)
    assertEquals(listOf("auth_token", "twid"), result.missingRequired)
  }
}
