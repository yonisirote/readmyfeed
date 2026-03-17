package com.yonisirote.readmyfeed

import com.yonisirote.readmyfeed.x.timeline.XTimelineErrorCodes
import com.yonisirote.readmyfeed.x.timeline.XTimelineException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedScreenStateTest {
  @Test
  fun emptyFeedDuringInitialFetchShowsFetchingState() {
    val summary = resolveFeedSummaryModel(
      itemCount = 0,
      hasStoredSession = true,
      nextCursor = null,
      isFetchingInitial = true,
      isFetchingMore = false,
      lastLoadErrorMessage = null,
    )

    assertEquals(FeedSummaryState.FETCHING, summary.state)
  }

  @Test
  fun emptyFeedAfterCompletedFetchShowsEmptyState() {
    val summary = resolveFeedSummaryModel(
      itemCount = 0,
      hasStoredSession = true,
      nextCursor = null,
      isFetchingInitial = false,
      isFetchingMore = false,
      lastLoadErrorMessage = null,
    )

    assertEquals(FeedSummaryState.EMPTY, summary.state)
  }

  @Test
  fun emptyFeedAfterFailureShowsErrorState() {
    val summary = resolveFeedSummaryModel(
      itemCount = 0,
      hasStoredSession = false,
      nextCursor = null,
      isFetchingInitial = false,
      isFetchingMore = false,
      lastLoadErrorMessage = "Connected to X but failed to load the following feed.",
    )

    assertEquals(FeedSummaryState.ERROR, summary.state)
    assertEquals("Connected to X but failed to load the following feed.", summary.errorMessage)
  }

  @Test
  fun loadedFeedWithoutSessionShowsReconnectState() {
    val summary = resolveFeedSummaryModel(
      itemCount = 5,
      hasStoredSession = false,
      nextCursor = "cursor-1",
      isFetchingInitial = false,
      isFetchingMore = false,
      lastLoadErrorMessage = "Failed to fetch following timeline (status 401).",
    )

    assertEquals(FeedSummaryState.RECONNECT, summary.state)
  }

  @Test
  fun loadMoreRequiresStoredSession() {
    assertFalse(
      canLoadMoreTimeline(
        hasStoredSession = false,
        nextCursor = "cursor-1",
        isFetchingInitial = false,
        isFetchingMore = false,
      ),
    )
    assertFalse(
      shouldShowLoadMoreButton(
        hasStoredSession = false,
        nextCursor = "cursor-1",
        isFetchingMore = false,
      ),
    )
    assertFalse(
      shouldEnableLoadMoreButton(
        hasStoredSession = false,
        nextCursor = "cursor-1",
        isBusy = false,
      ),
    )
  }

  @Test
  fun unauthorizedTimelineErrorsClearStoredSession() {
    assertTrue(
      shouldClearTimelineSession(
        XTimelineException(
          message = "Failed to fetch following timeline (status 401).",
          code = XTimelineErrorCodes.REQUEST_FAILED,
          context = mapOf("status" to 401),
        ),
      ),
    )
    assertTrue(
      shouldClearTimelineSession(
        XTimelineException(
          message = "Invalid cookie.",
          code = XTimelineErrorCodes.COOKIE_INVALID,
        ),
      ),
    )
    assertFalse(
      shouldClearTimelineSession(
        XTimelineException(
          message = "Server error.",
          code = XTimelineErrorCodes.REQUEST_FAILED,
          context = mapOf("status" to 500),
        ),
      ),
    )
  }
}
