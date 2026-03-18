package com.yonisirote.readmyfeed.providers.x.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ContentListScreenStateTest {
  @Test
  fun emptyContentDuringInitialFetchShowsFetchingState() {
    val summary = resolveContentListSummaryModel(
      itemCount = 0,
      isConnected = true,
      nextPageToken = null,
      isFetchingInitial = true,
      isFetchingMore = false,
      lastLoadErrorMessage = null,
    )

    assertEquals(ContentListSummaryState.FETCHING, summary.state)
  }

  @Test
  fun emptyContentAfterCompletedFetchShowsEmptyState() {
    val summary = resolveContentListSummaryModel(
      itemCount = 0,
      isConnected = true,
      nextPageToken = null,
      isFetchingInitial = false,
      isFetchingMore = false,
      lastLoadErrorMessage = null,
    )

    assertEquals(ContentListSummaryState.EMPTY, summary.state)
  }

  @Test
  fun emptyContentAfterFailureShowsErrorState() {
    val summary = resolveContentListSummaryModel(
      itemCount = 0,
      isConnected = false,
      nextPageToken = null,
      isFetchingInitial = false,
      isFetchingMore = false,
      lastLoadErrorMessage = "Connected to X but failed to load the following feed.",
    )

    assertEquals(ContentListSummaryState.ERROR, summary.state)
    assertEquals("Connected to X but failed to load the following feed.", summary.errorMessage)
  }

  @Test
  fun loadedContentWithoutConnectionShowsReconnectState() {
    val summary = resolveContentListSummaryModel(
      itemCount = 5,
      isConnected = false,
      nextPageToken = "cursor-1",
      isFetchingInitial = false,
      isFetchingMore = false,
      lastLoadErrorMessage = "Failed to fetch following timeline (status 401).",
    )

    assertEquals(ContentListSummaryState.RECONNECT, summary.state)
  }

  @Test
  fun loadMoreRequiresConnection() {
    assertFalse(
      canLoadMoreContent(
        isConnected = false,
        nextPageToken = "cursor-1",
        isFetchingInitial = false,
        isFetchingMore = false,
      ),
    )
    assertFalse(
      shouldShowLoadMoreContentButton(
        isConnected = false,
        nextPageToken = "cursor-1",
        isFetchingMore = false,
      ),
    )
    assertFalse(
      shouldEnableLoadMoreContentButton(
        isConnected = false,
        nextPageToken = "cursor-1",
        isBusy = false,
      ),
    )
  }
}
