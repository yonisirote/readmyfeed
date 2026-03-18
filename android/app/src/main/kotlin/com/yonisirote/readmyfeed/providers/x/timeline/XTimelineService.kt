package com.yonisirote.readmyfeed.providers.x.timeline

import com.yonisirote.readmyfeed.providers.x.auth.XAuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class XTimelineService(
  private val authService: XAuthService,
  private val httpClient: OkHttpClient = OkHttpClient(),
) {
  suspend fun fetchFollowingTimeline(request: XFollowingTimelineRequest = XFollowingTimelineRequest()): XFollowingTimelineBatch {
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
      httpClient.newCall(okHttpRequest).await()
    } catch (error: IOException) {
      throw XTimelineException(
        message = "Failed to fetch following timeline.",
        code = XTimelineErrorCodes.REQUEST_FAILED,
        context = mapOf("cause" to (error.message ?: error::class.java.simpleName)),
        cause = error,
      )
    }

    return withContext(Dispatchers.IO) {
      response.use { httpResponse ->
        val body = readTimelineResponseBody {
          httpResponse.body?.string().orEmpty()
        }
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

        parseXFollowingTimelineResponse(body)
      }
    }
  }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
  continuation.invokeOnCancellation { cancel() }
  enqueue(object : Callback {
    override fun onFailure(call: Call, e: IOException) {
      if (continuation.isActive) continuation.resumeWithException(e)
    }
    override fun onResponse(call: Call, response: Response) {
      if (continuation.isActive) continuation.resume(response)
    }
  })
}

private fun summarizeBody(body: String): String {
  val trimmed = body.replace(Regex("\\s+"), " ").trim()
  return if (trimmed.length <= 240) {
    trimmed
  } else {
    trimmed.take(240) + "..."
  }
}

internal fun readTimelineResponseBody(readBody: () -> String): String {
  return try {
    readBody()
  } catch (error: IOException) {
    throw XTimelineException(
      message = "Failed to read following timeline response.",
      code = XTimelineErrorCodes.REQUEST_FAILED,
      context = mapOf("cause" to (error.message ?: error::class.java.simpleName)),
      cause = error,
    )
  }
}
