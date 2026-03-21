package com.yonisirote.readmyfeed.providers.x.timeline

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

  @Test
  fun mergesEmptyExistingWithIncoming() {
    val merged = mergeTimelineItems(
      emptyList(),
      listOf(createTimelineItem("1"), createTimelineItem("2")),
    )

    assertEquals(listOf("1", "2"), merged.map { it.id })
  }

  @Test
  fun mergesExistingWithEmptyIncoming() {
    val merged = mergeTimelineItems(
      listOf(createTimelineItem("1"), createTimelineItem("2")),
      emptyList(),
    )

    assertEquals(listOf("1", "2"), merged.map { it.id })
  }

  @Test
  fun mergesBothEmpty() {
    val merged = mergeTimelineItems(emptyList(), emptyList())

    assertTrue(merged.isEmpty())
  }

  @Test
  fun skipsBlankIds() {
    val merged = mergeTimelineItems(
      listOf(createTimelineItem("1"), createTimelineItem("")),
      listOf(createTimelineItem("  "), createTimelineItem("2")),
    )

    assertEquals(listOf("1", "2"), merged.map { it.id })
  }

  @Test
  fun preservesOrderFromExistingThenIncoming() {
    val merged = mergeTimelineItems(
      listOf(createTimelineItem("a"), createTimelineItem("b")),
      listOf(createTimelineItem("c"), createTimelineItem("d")),
    )

    assertEquals(listOf("a", "b", "c", "d"), merged.map { it.id })
  }

  @Test
  fun doesNotPrefetchWhenFarFromEnd() {
    assertFalse(
      shouldPrefetchTimeline(
        currentIndex = 0,
        loadedCount = 40,
        nextCursor = "cursor-123",
        isFetchingMore = false,
      ),
    )
  }

  @Test
  fun doesNotPrefetchWithNegativeIndex() {
    assertFalse(
      shouldPrefetchTimeline(
        currentIndex = -1,
        loadedCount = 40,
        nextCursor = "cursor-123",
        isFetchingMore = false,
      ),
    )
  }

  @Test
  fun doesNotPrefetchWithZeroLoadedCount() {
    assertFalse(
      shouldPrefetchTimeline(
        currentIndex = 0,
        loadedCount = 0,
        nextCursor = "cursor-123",
        isFetchingMore = false,
      ),
    )
  }

  @Test
  fun prefetchesAtExactThreshold() {
    val loadedCount = 20
    // currentIndex is zero-based, so the exact boundary is one slot earlier than the raw count math.
    val currentIndex = loadedCount - TIMELINE_PREFETCH_THRESHOLD - 1

    assertTrue(
      shouldPrefetchTimeline(
        currentIndex = currentIndex,
        loadedCount = loadedCount,
        nextCursor = "cursor-123",
        isFetchingMore = false,
      ),
    )
  }

  @Test
  fun doesNotPrefetchOneAboveThreshold() {
    val loadedCount = 20
    val currentIndex = loadedCount - TIMELINE_PREFETCH_THRESHOLD - 2

    assertFalse(
      shouldPrefetchTimeline(
        currentIndex = currentIndex,
        loadedCount = loadedCount,
        nextCursor = "cursor-123",
        isFetchingMore = false,
      ),
    )
  }

  @Test
  fun doesNotPrefetchWithBlankCursor() {
    assertFalse(
      shouldPrefetchTimeline(
        currentIndex = 35,
        loadedCount = 40,
        nextCursor = "   ",
        isFetchingMore = false,
      ),
    )
  }
}
