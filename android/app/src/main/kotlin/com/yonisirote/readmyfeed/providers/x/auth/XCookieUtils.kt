package com.yonisirote.readmyfeed.providers.x.auth

import java.net.URI

private fun extractOrigin(url: String?): String? {
  if (url.isNullOrBlank()) {
    return null
  }

  return try {
    val parsed = URI(url)
    buildString {
      append(parsed.scheme)
      append("://")
      append(parsed.host)
      if (parsed.port >= 0) {
        append(":")
        append(parsed.port)
      }
    }
  } catch (_: Exception) {
    null
  }
}

fun hasAllowedXOrigin(url: String?): Boolean {
  val origin = extractOrigin(url) ?: return false
  return X_ALLOWED_ORIGINS.contains(origin)
}

fun looksLikeLoggedInUrl(url: String?): Boolean {
  val origin = extractOrigin(url) ?: return false

  return try {
    val parsed = URI(url)
    origin == X_BASE_URL && POST_LOGIN_PATH_HINTS.any { hint -> parsed.path.orEmpty().startsWith(hint) }
  } catch (_: Exception) {
    false
  }
}

fun parseCookieHeader(cookieHeader: String?): Map<String, String> {
  if (cookieHeader.isNullOrBlank()) {
    return emptyMap()
  }

  val cookies = linkedMapOf<String, String>()

  for (segment in cookieHeader.split(';')) {
    val trimmed = segment.trim()
    if (trimmed.isEmpty()) {
      continue
    }

    val separator = trimmed.indexOf('=')
    if (separator <= 0) {
      continue
    }

    val name = trimmed.substring(0, separator).trim()
    val value = trimmed.substring(separator + 1).trim()

    if (name.isNotEmpty() && value.isNotEmpty()) {
      cookies[name] = value
    }
  }

  return cookies
}

fun hasCookieValue(cookies: Map<String, String>, name: String): Boolean {
  return cookies[name]?.trim().isNullOrEmpty().not()
}

fun evaluateCookies(cookies: Map<String, String>): XCookieReadResult {
  val missingRequired = REQUIRED_X_COOKIES.filterNot { name -> hasCookieValue(cookies, name) }
  val missingOptional = OPTIONAL_X_COOKIES.filterNot { name -> hasCookieValue(cookies, name) }

  return XCookieReadResult(
    cookies = cookies,
    missingRequired = missingRequired,
    missingOptional = missingOptional,
    hasRequired = missingRequired.isEmpty(),
  )
}

fun buildCookieString(cookies: Map<String, String>): String {
  val missingRequired = REQUIRED_X_COOKIES.filterNot { name -> hasCookieValue(cookies, name) }
  if (missingRequired.isNotEmpty()) {
    throw XAuthException(
      message = "Missing required cookies",
      code = XAuthErrorCodes.COOKIE_MISSING_REQUIRED,
      context = mapOf("missingRequired" to missingRequired),
    )
  }

  val orderedParts = COOKIE_ORDER.mapNotNull { name ->
    val value = cookies[name]?.trim().orEmpty()
    if (value.isEmpty()) {
      null
    } else {
      "$name=$value"
    }
  }

  if (orderedParts.isEmpty()) {
    throw XAuthException(
      message = "Empty cookie string",
      code = XAuthErrorCodes.COOKIE_STRING_INVALID,
    )
  }

  return orderedParts.joinToString(separator = "; ", postfix = ";")
}

fun createSessionFromCookies(cookies: Map<String, String>): XAuthSession {
  return XAuthSession(
    cookieString = buildCookieString(cookies),
    cookieNames = cookies.keys.sorted(),
  )
}
