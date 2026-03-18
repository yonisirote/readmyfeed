package com.yonisirote.readmyfeed.providers.x.ui

enum class ContentListSummaryState {
  FETCHING,
  EMPTY,
  ERROR,
  RECONNECT,
  LOADING_MORE,
  READY_MORE,
  READY_END,
}

data class ContentListSummaryModel(
  val state: ContentListSummaryState,
  val errorMessage: String? = null,
)

fun resolveContentListSummaryModel(
  itemCount: Int,
  isConnected: Boolean,
  nextPageToken: String?,
  isFetchingInitial: Boolean,
  isFetchingMore: Boolean,
  lastLoadErrorMessage: String?,
): ContentListSummaryModel {
  return when {
    itemCount <= 0 && isFetchingInitial -> ContentListSummaryModel(
      state = ContentListSummaryState.FETCHING,
    )
    itemCount <= 0 && !lastLoadErrorMessage.isNullOrBlank() -> ContentListSummaryModel(
      state = ContentListSummaryState.ERROR,
      errorMessage = lastLoadErrorMessage,
    )
    itemCount <= 0 -> ContentListSummaryModel(
      state = ContentListSummaryState.EMPTY,
    )
    !isConnected -> ContentListSummaryModel(
      state = ContentListSummaryState.RECONNECT,
    )
    isFetchingMore -> ContentListSummaryModel(
      state = ContentListSummaryState.LOADING_MORE,
    )
    !nextPageToken.isNullOrBlank() -> ContentListSummaryModel(
      state = ContentListSummaryState.READY_MORE,
    )
    else -> ContentListSummaryModel(
      state = ContentListSummaryState.READY_END,
    )
  }
}

fun canLoadMoreContent(
  isConnected: Boolean,
  nextPageToken: String?,
  isFetchingInitial: Boolean,
  isFetchingMore: Boolean,
): Boolean {
  return isConnected && !nextPageToken.isNullOrBlank() && !isFetchingInitial && !isFetchingMore
}

fun shouldShowLoadMoreContentButton(
  isConnected: Boolean,
  nextPageToken: String?,
  isFetchingMore: Boolean,
): Boolean {
  return isConnected && !nextPageToken.isNullOrBlank() && !isFetchingMore
}

fun shouldEnableLoadMoreContentButton(
  isConnected: Boolean,
  nextPageToken: String?,
  isBusy: Boolean,
): Boolean {
  return isConnected && !isBusy && !nextPageToken.isNullOrBlank()
}
