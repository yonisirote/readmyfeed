package com.yonisirote.readmyfeed.x.timeline

import com.yonisirote.readmyfeed.x.auth.XAuthService
import okhttp3.OkHttpClient
import okhttp3.Request

class XTimelineService(
  private val authService: XAuthService,
  private val httpClient: OkHttpClient = OkHttpClient(),
) {
  fun fetchFollowingTimeline(request: XFollowingTimelineRequest = XFollowingTimelineRequest()): XFollowingTimelineBatch {
    val cookieString = request.cookieString ?: authService.loadStoredSession() ?: throw XTimelineException(
      message = "No X session found. Please connect your account again.",
      code = XTimelineErrorCodes.SESSION_MISSING,
    )

    val httpRequest = buildTimelineHttpRequest(request, cookieString)
    val okHttpRequest = Request.Builder()
      .url(httpRequest.url)
      .get()
      .apply {
        for ((name, value) in httpRequest.headers) {
          addHeader(name, value)
        }
      }
      .build()

    val response = try {
      httpClient.newCall(okHttpRequest).execute()
    } catch (error: Exception) {
      throw XTimelineException(
        message = "Failed to fetch following timeline.",
        code = XTimelineErrorCodes.REQUEST_FAILED,
        context = mapOf("cause" to (error.message ?: error::class.java.simpleName)),
        cause = error,
      )
    }

    response.use { httpResponse ->
      val body = httpResponse.body?.string().orEmpty()
      if (!httpResponse.isSuccessful) {
        throw XTimelineException(
          message = "Failed to fetch following timeline (status ${httpResponse.code}).",
          code = XTimelineErrorCodes.REQUEST_FAILED,
          context = mapOf(
            "status" to httpResponse.code,
            "body" to summarizeBody(body),
          ),
        )
      }

      return parseXFollowingTimelineResponse(body)
    }
  }
}

private fun summarizeBody(body: String): String {
  val trimmed = body.replace(Regex("\\s+"), " ").trim()
  return if (trimmed.length <= 240) {
    trimmed
  } else {
    trimmed.take(240) + "..."
  }
}
