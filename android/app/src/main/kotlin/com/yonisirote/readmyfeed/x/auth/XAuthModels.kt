package com.yonisirote.readmyfeed.x.auth

data class XCookieReadResult(
  val cookies: Map<String, String>,
  val missingRequired: List<String>,
  val missingOptional: List<String>,
  val hasRequired: Boolean,
)

data class XAuthSession(
  val cookieString: String,
  val cookieNames: List<String>,
)

data class XAuthCaptureResult(
  val session: XAuthSession?,
  val cookieResult: XCookieReadResult,
)
