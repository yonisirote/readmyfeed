package com.yonisirote.readmyfeed.x.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XTimelinePaginationTest {
  private fun createTimelineItem(id: String): XTimelineItem {
    return XTimelineItem(
      id = id,
      text = "Post $id",
      createdAt = "2025-02-20T12:34:56Z",
      authorName = "Alice",
      authorHandle = "alice",
      lang = "en",
      replyTo = "",
      quoteCount = 0,
      replyCount = 0,
      retweetCount = 0,
      likeCount = 0,
      viewCount = null,
      isRetweet = false,
      isQuote = false,
      quotedText = "",
      quotedLang = "",
      quotedAuthorHandle = "",
      quotedMedia = emptyList(),
      url = "https://x.com/alice/status/$id",
      media = emptyList(),
    )
  }

  @Test
  fun mergesTimelinePagesWithoutDuplicates() {
    val merged = mergeTimelineItems(
      listOf(createTimelineItem("1"), createTimelineItem("2")),
      listOf(createTimelineItem("2"), createTimelineItem("3")),
    )

    assertEquals(listOf("1", "2", "3"), merged.map { it.id })
  }

  @Test
  fun requestsPrefetchNearEndOfLoadedItems() {
    assertTrue(
      shouldPrefetchTimeline(
        currentIndex = 35,
        loadedCount = 40,
        nextCursor = "cursor-123",
        isFetchingMore = false,
      ),
    )
  }

  @Test
  fun doesNotPrefetchWithoutNextPageOrDuringInFlightFetch() {
    assertFalse(
      shouldPrefetchTimeline(
        currentIndex = 40 - TIMELINE_PREFETCH_THRESHOLD,
        loadedCount = 40,
        nextCursor = null,
        isFetchingMore = false,
      ),
    )

    assertFalse(
      shouldPrefetchTimeline(
        currentIndex = 40 - TIMELINE_PREFETCH_THRESHOLD,
        loadedCount = 40,
        nextCursor = "cursor-123",
        isFetchingMore = true,
      ),
    )
  }
}
