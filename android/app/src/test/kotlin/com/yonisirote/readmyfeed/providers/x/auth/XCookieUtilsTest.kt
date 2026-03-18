package com.yonisirote.readmyfeed.providers.x.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XCookieUtilsTest {

  // ── parseCookieHeader ──────────────────────────────────────────────

  @Test
  fun parseCookieHeaderReturnsEmptyMapForNull() {
    assertEquals(emptyMap<String, String>(), parseCookieHeader(null))
  }

  @Test
  fun parseCookieHeaderReturnsEmptyMapForBlankString() {
    assertEquals(emptyMap<String, String>(), parseCookieHeader(""))
    assertEquals(emptyMap<String, String>(), parseCookieHeader("   "))
  }

  @Test
  fun parseCookieHeaderParsesSinglePair() {
    val cookies = parseCookieHeader("auth_token=abc123")
    assertEquals(mapOf("auth_token" to "abc123"), cookies)
  }

  @Test
  fun parseCookieHeaderParsesMultiplePairsWithWhitespace() {
    val cookies = parseCookieHeader("auth_token=abc123;  ct0=csrf ;twid=u%3D1")
    assertEquals(
      mapOf("auth_token" to "abc123", "ct0" to "csrf", "twid" to "u%3D1"),
      cookies,
    )
  }

  @Test
  fun parseCookieHeaderSkipsEntriesWithNoEqualsSign() {
    val cookies = parseCookieHeader("auth_token=abc; invalidentry; ct0=csrf")
    assertEquals(mapOf("auth_token" to "abc", "ct0" to "csrf"), cookies)
  }

  @Test
  fun parseCookieHeaderSkipsEntriesWithEmptyNameOrValue() {
    val cookies = parseCookieHeader("auth_token=abc; good=; =orphan; ct0=csrf")
    assertEquals(mapOf("auth_token" to "abc", "ct0" to "csrf"), cookies)
  }

  @Test
  fun parseCookieHeaderSkipsEntriesWhereEqualsIsAtPositionZero() {
    val cookies = parseCookieHeader("=noname; auth_token=abc")
    assertEquals(mapOf("auth_token" to "abc"), cookies)
  }

  @Test
  fun parseCookieHeaderLastDuplicateWins() {
    val cookies = parseCookieHeader("ct0=first; ct0=second")
    assertEquals(mapOf("ct0" to "second"), cookies)
  }

  @Test
  fun parseCookieHeaderPreservesValuesContainingEqualsSign() {
    val cookies = parseCookieHeader("auth_token=abc=def==; ct0=csrf")
    assertEquals(
      mapOf("auth_token" to "abc=def==", "ct0" to "csrf"),
      cookies,
    )
  }

  // ── hasCookieValue ─────────────────────────────────────────────────

  @Test
  fun hasCookieValueReturnsTrueForNonBlankValue() {
    assertTrue(hasCookieValue(mapOf("ct0" to "csrf"), "ct0"))
  }

  @Test
  fun hasCookieValueReturnsFalseForMissingKey() {
    assertFalse(hasCookieValue(mapOf("ct0" to "csrf"), "auth_token"))
  }

  @Test
  fun hasCookieValueReturnsFalseForBlankValue() {
    assertFalse(hasCookieValue(mapOf("ct0" to "   "), "ct0"))
  }

  @Test
  fun hasCookieValueReturnsFalseForEmptyStringValue() {
    assertFalse(hasCookieValue(mapOf("ct0" to ""), "ct0"))
  }

  // ── evaluateCookies ────────────────────────────────────────────────

  @Test
  fun evaluateCookiesReportsAllPresentWhenComplete() {
    val cookies = mapOf(
      "auth_token" to "token",
      "ct0" to "csrf",
      "twid" to "u%3D1",
      "kdt" to "device",
    )
    val result = evaluateCookies(cookies)

    assertTrue(result.hasRequired)
    assertEquals(emptyList<String>(), result.missingRequired)
    assertEquals(emptyList<String>(), result.missingOptional)
    assertEquals(cookies, result.cookies)
  }

  @Test
  fun evaluateCookiesReportsOptionalMissingWhenOnlyRequiredPresent() {
    val cookies = mapOf(
      "auth_token" to "token",
      "ct0" to "csrf",
      "twid" to "u%3D1",
    )
    val result = evaluateCookies(cookies)

    assertTrue(result.hasRequired)
    assertEquals(emptyList<String>(), result.missingRequired)
    assertEquals(listOf("kdt"), result.missingOptional)
  }

  @Test
  fun evaluateCookiesReportsMissingRequiredCookies() {
    val cookies = mapOf("ct0" to "csrf")
    val result = evaluateCookies(cookies)

    assertFalse(result.hasRequired)
    assertEquals(listOf("auth_token", "twid"), result.missingRequired)
  }

  @Test
  fun evaluateCookiesReportsAllMissingWhenEmpty() {
    val result = evaluateCookies(emptyMap())

    assertFalse(result.hasRequired)
    assertEquals(listOf("auth_token", "ct0", "twid"), result.missingRequired)
    assertEquals(listOf("kdt"), result.missingOptional)
  }

  @Test
  fun evaluateCookiesPreservesExtraCookiesInResult() {
    val cookies = mapOf(
      "auth_token" to "token",
      "ct0" to "csrf",
      "twid" to "u%3D1",
      "extra" to "bonus",
    )
    val result = evaluateCookies(cookies)

    assertTrue(result.hasRequired)
    assertEquals("bonus", result.cookies["extra"])
  }

  // ── buildCookieString ──────────────────────────────────────────────

  @Test
  fun buildCookieStringBuildsInCookieOrderWithTrailingSemicolon() {
    val cookies = mapOf(
      "twid" to "u%3D1",
      "ct0" to "csrf",
      "auth_token" to "token",
      "kdt" to "device",
    )
    assertEquals("auth_token=token; ct0=csrf; kdt=device; twid=u%3D1;", buildCookieString(cookies))
  }

  @Test
  fun buildCookieStringThrowsWhenRequiredCookiesMissing() {
    val cookies = mapOf("ct0" to "csrf")

    val error = try {
      buildCookieString(cookies)
      throw AssertionError("Expected XAuthException")
    } catch (error: XAuthException) {
      error
    }

    assertEquals(XAuthErrorCodes.COOKIE_MISSING_REQUIRED, error.code)
    @Suppress("UNCHECKED_CAST")
    val missing = error.context["missingRequired"] as List<String>
    assertTrue(missing.contains("auth_token"))
    assertTrue(missing.contains("twid"))
  }

  @Test
  fun buildCookieStringOmitsOptionalCookiesWhenMissing() {
    val cookies = mapOf(
      "auth_token" to "token",
      "ct0" to "csrf",
      "twid" to "u%3D1",
    )
    assertEquals("auth_token=token; ct0=csrf; twid=u%3D1;", buildCookieString(cookies))
  }

  @Test
  fun buildCookieStringOmitsOptionalCookiesWhenBlank() {
    val cookies = mapOf(
      "auth_token" to "token",
      "ct0" to "csrf",
      "twid" to "u%3D1",
      "kdt" to "   ",
    )
    assertEquals("auth_token=token; ct0=csrf; twid=u%3D1;", buildCookieString(cookies))
  }

  @Test
  fun buildCookieStringIncludesOptionalCookiesWhenPresent() {
    val cookies = mapOf(
      "auth_token" to "token",
      "ct0" to "csrf",
      "twid" to "u%3D1",
      "kdt" to "device",
    )
    assertEquals("auth_token=token; ct0=csrf; kdt=device; twid=u%3D1;", buildCookieString(cookies))
  }

  // ── createSessionFromCookies ───────────────────────────────────────

  @Test
  fun createSessionFromCookiesBuildsCorrectSessionFields() {
    val cookies = mapOf(
      "twid" to "u%3D1",
      "ct0" to "csrf",
      "auth_token" to "token",
      "kdt" to "device",
    )
    val session = createSessionFromCookies(cookies)

    assertEquals("auth_token=token; ct0=csrf; kdt=device; twid=u%3D1;", session.cookieString)
    assertEquals(listOf("auth_token", "ct0", "kdt", "twid"), session.cookieNames)
  }

  @Test
  fun createSessionFromCookiesThrowsWhenRequiredMissing() {
    val cookies = mapOf("ct0" to "csrf")

    val error = try {
      createSessionFromCookies(cookies)
      throw AssertionError("Expected XAuthException")
    } catch (error: XAuthException) {
      error
    }

    assertEquals(XAuthErrorCodes.COOKIE_MISSING_REQUIRED, error.code)
  }

  // ── hasAllowedXOrigin ──────────────────────────────────────────────

  @Test
  fun hasAllowedXOriginReturnsTrueForXComWithPath() {
    assertTrue(hasAllowedXOrigin("https://x.com/home"))
  }

  @Test
  fun hasAllowedXOriginReturnsTrueForTwitterComWithPath() {
    assertTrue(hasAllowedXOrigin("https://twitter.com/settings"))
  }

  @Test
  fun hasAllowedXOriginReturnsTrueForMobileTwitterComWithPath() {
    assertTrue(hasAllowedXOrigin("https://mobile.twitter.com/home"))
  }

  @Test
  fun hasAllowedXOriginReturnsFalseForUnknownOrigin() {
    assertFalse(hasAllowedXOrigin("https://example.com/home"))
  }

  @Test
  fun hasAllowedXOriginReturnsFalseForNull() {
    assertFalse(hasAllowedXOrigin(null))
  }

  @Test
  fun hasAllowedXOriginReturnsFalseForBlankString() {
    assertFalse(hasAllowedXOrigin(""))
    assertFalse(hasAllowedXOrigin("   "))
  }

  @Test
  fun hasAllowedXOriginReturnsFalseForMalformedUrl() {
    assertFalse(hasAllowedXOrigin("not a url at all"))
  }

  @Test
  fun hasAllowedXOriginReturnsFalseForUrlWithPort() {
    assertFalse(hasAllowedXOrigin("https://x.com:8080/home"))
  }

  // ── looksLikeLoggedInUrl ───────────────────────────────────────────

  @Test
  fun looksLikeLoggedInUrlReturnsTrueForHomePath() {
    assertTrue(looksLikeLoggedInUrl("https://x.com/home"))
  }

  @Test
  fun looksLikeLoggedInUrlReturnsTrueForNotificationsPath() {
    assertTrue(looksLikeLoggedInUrl("https://x.com/notifications"))
  }

  @Test
  fun looksLikeLoggedInUrlReturnsTrueForMessagesPath() {
    assertTrue(looksLikeLoggedInUrl("https://x.com/messages"))
  }

  @Test
  fun looksLikeLoggedInUrlReturnsTrueForExplorePath() {
    assertTrue(looksLikeLoggedInUrl("https://x.com/explore"))
  }

  @Test
  fun looksLikeLoggedInUrlReturnsTrueForSettingsPath() {
    assertTrue(looksLikeLoggedInUrl("https://x.com/settings"))
  }

  @Test
  fun looksLikeLoggedInUrlReturnsTrueForComposePath() {
    assertTrue(looksLikeLoggedInUrl("https://x.com/compose"))
  }

  @Test
  fun looksLikeLoggedInUrlReturnsTrueForSearchPath() {
    assertTrue(looksLikeLoggedInUrl("https://x.com/search"))
  }

  @Test
  fun looksLikeLoggedInUrlReturnsTrueForBookmarksPath() {
    assertTrue(looksLikeLoggedInUrl("https://x.com/i/bookmarks"))
  }

  @Test
  fun looksLikeLoggedInUrlReturnsFalseForTwitterComEvenWithCorrectPath() {
    assertFalse(looksLikeLoggedInUrl("https://twitter.com/home"))
  }

  @Test
  fun looksLikeLoggedInUrlReturnsFalseForLoginUrl() {
    assertFalse(looksLikeLoggedInUrl("https://x.com/i/flow/login"))
  }

  @Test
  fun looksLikeLoggedInUrlReturnsFalseForNull() {
    assertFalse(looksLikeLoggedInUrl(null))
  }

  @Test
  fun looksLikeLoggedInUrlReturnsFalseForBlank() {
    assertFalse(looksLikeLoggedInUrl(""))
    assertFalse(looksLikeLoggedInUrl("   "))
  }

  @Test
  fun looksLikeLoggedInUrlReturnsFalseForUnknownXPath() {
    assertFalse(looksLikeLoggedInUrl("https://x.com/unknown"))
  }

  @Test
  fun looksLikeLoggedInUrlReturnsFalseForNonXOrigin() {
    assertFalse(looksLikeLoggedInUrl("https://example.com/home"))
  }
}
