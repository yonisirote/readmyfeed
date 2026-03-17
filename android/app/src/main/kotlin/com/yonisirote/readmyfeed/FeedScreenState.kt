package com.yonisirote.readmyfeed

import com.yonisirote.readmyfeed.x.timeline.XTimelineErrorCodes
import com.yonisirote.readmyfeed.x.timeline.XTimelineException

private val sessionInvalidStatuses = setOf(401, 403)

enum class FeedSummaryState {
  FETCHING,
  EMPTY,
  ERROR,
  RECONNECT,
  LOADING_MORE,
  READY_MORE,
  READY_END,
}

data class FeedSummaryModel(
  val state: FeedSummaryState,
  val errorMessage: String? = null,
)

fun resolveFeedSummaryModel(
  itemCount: Int,
  hasStoredSession: Boolean,
  nextCursor: String?,
  isFetchingInitial: Boolean,
  isFetchingMore: Boolean,
  lastLoadErrorMessage: String?,
): FeedSummaryModel {
  return when {
    itemCount <= 0 && isFetchingInitial -> FeedSummaryModel(
      state = FeedSummaryState.FETCHING,
    )
    itemCount <= 0 && !lastLoadErrorMessage.isNullOrBlank() -> FeedSummaryModel(
      state = FeedSummaryState.ERROR,
      errorMessage = lastLoadErrorMessage,
    )
    itemCount <= 0 -> FeedSummaryModel(
      state = FeedSummaryState.EMPTY,
    )
    !hasStoredSession -> FeedSummaryModel(
      state = FeedSummaryState.RECONNECT,
    )
    isFetchingMore -> FeedSummaryModel(
      state = FeedSummaryState.LOADING_MORE,
    )
    !nextCursor.isNullOrBlank() -> FeedSummaryModel(
      state = FeedSummaryState.READY_MORE,
    )
    else -> FeedSummaryModel(
      state = FeedSummaryState.READY_END,
    )
  }
}

fun canLoadMoreTimeline(
  hasStoredSession: Boolean,
  nextCursor: String?,
  isFetchingInitial: Boolean,
  isFetchingMore: Boolean,
): Boolean {
  return hasStoredSession && !nextCursor.isNullOrBlank() && !isFetchingInitial && !isFetchingMore
}

fun shouldShowLoadMoreButton(
  hasStoredSession: Boolean,
  nextCursor: String?,
  isFetchingMore: Boolean,
): Boolean {
  return hasStoredSession && !nextCursor.isNullOrBlank() && !isFetchingMore
}

fun shouldEnableLoadMoreButton(
  hasStoredSession: Boolean,
  nextCursor: String?,
  isBusy: Boolean,
): Boolean {
  return hasStoredSession && !isBusy && !nextCursor.isNullOrBlank()
}

fun shouldClearTimelineSession(error: XTimelineException): Boolean {
  return error.code == XTimelineErrorCodes.SESSION_MISSING ||
    error.code == XTimelineErrorCodes.COOKIE_INVALID ||
    (error.code == XTimelineErrorCodes.REQUEST_FAILED &&
      (error.context["status"] as? Int) in sessionInvalidStatuses)
}
