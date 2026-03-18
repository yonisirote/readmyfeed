package com.yonisirote.readmyfeed.providers.x.timeline

const val TIMELINE_PREFETCH_THRESHOLD = 4

fun mergeTimelineItems(
  existingItems: List<XTimelineItem>,
  incomingItems: List<XTimelineItem>,
): List<XTimelineItem> {
  val merged = LinkedHashMap<String, XTimelineItem>()

  for (item in existingItems + incomingItems) {
    if (item.id.isBlank() || merged.containsKey(item.id)) {
      continue
    }

    merged[item.id] = item
  }

  return merged.values.toList()
}

fun shouldPrefetchTimeline(
  currentIndex: Int,
  loadedCount: Int,
  nextCursor: String?,
  isFetchingMore: Boolean,
  threshold: Int = TIMELINE_PREFETCH_THRESHOLD,
): Boolean {
  if (nextCursor.isNullOrBlank() || isFetchingMore || currentIndex < 0 || loadedCount == 0) {
    return false
  }

  return loadedCount - currentIndex - 1 <= threshold
}
