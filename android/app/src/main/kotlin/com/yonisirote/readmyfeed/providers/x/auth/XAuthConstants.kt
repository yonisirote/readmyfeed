package com.yonisirote.readmyfeed.providers.x.auth

const val X_BASE_URL = "https://x.com"
const val X_LOGIN_URL = "$X_BASE_URL/i/flow/login"

// X login cookies can land on both the new x.com host and legacy twitter.com origins.
val X_ALLOWED_ORIGINS = listOf(
  X_BASE_URL,
  "https://twitter.com",
  "https://mobile.twitter.com",
)

// These cookies and their replay order mirror the browser session X expects.
val REQUIRED_X_COOKIES = listOf("auth_token", "ct0", "twid")
val OPTIONAL_X_COOKIES = listOf("kdt")
val COOKIE_ORDER = listOf("auth_token", "ct0", "kdt", "twid")

// These are heuristic post-login destinations, not a full allowlist of valid X routes.
val POST_LOGIN_PATH_HINTS = listOf(
  "/home",
  "/notifications",
  "/messages",
  "/explore",
  "/settings",
  "/compose",
  "/search",
  "/i/bookmarks",
)

internal const val X_SESSION_PREFERENCES_NAME = "x_session_preferences"
internal const val X_SESSION_KEY = "x-auth-session"
