package com.yonisirote.readmyfeed.providers.x.timeline

enum class XTimelineMediaType {
  PHOTO,
  VIDEO,
  ANIMATED_GIF,
  UNKNOWN,
}

data class XTimelineMedia(
  val type: XTimelineMediaType,
  val url: String,
  val expandedUrl: String,
  val thumbnailUrl: String? = null,
)

data class XTimelineItem(
  val id: String,
  val text: String,
  val createdAt: String,
  val authorName: String,
  val authorHandle: String,
  val lang: String,
  val replyTo: String,
  val quoteCount: Int,
  val replyCount: Int,
  val retweetCount: Int,
  val likeCount: Int,
  val viewCount: Int?,
  val isRetweet: Boolean,
  val isQuote: Boolean,
  val quotedText: String,
  val quotedLang: String,
  val quotedAuthorHandle: String,
  val quotedMedia: List<XTimelineMedia>,
  val url: String,
  val media: List<XTimelineMedia>,
)

data class XFollowingTimelineBatch(
  val items: List<XTimelineItem>,
  val nextCursor: String?,
)
