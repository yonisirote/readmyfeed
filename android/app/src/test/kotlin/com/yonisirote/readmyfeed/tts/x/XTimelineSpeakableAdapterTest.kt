package com.yonisirote.readmyfeed.tts.x

import com.yonisirote.readmyfeed.x.timeline.XTimelineItem
import org.junit.Assert.assertEquals
import org.junit.Test

class XTimelineSpeakableAdapterTest {
  @Test
  fun prefixesRetweetsAndAppendsQuoteAttribution() {
    val speakable = createTimelineItem(
      text = "Check this out",
      isRetweet = true,
      isQuote = true,
      quotedText = "Quoted post",
      quotedAuthorHandle = "quoted-user",
    ).toSpeakableItem()

    assertEquals("Reposted. Check this out Quoting quoted-user. Quoted post", speakable.text)
    assertEquals("Alice", speakable.authorLabel)
    assertEquals("en", speakable.lang)
  }

  @Test
  fun fallsBackToHandleWhenAuthorNameIsMissing() {
    val speakable = createTimelineItem(authorName = "", authorHandle = "alice").toSpeakableItem()

    assertEquals("alice", speakable.authorLabel)
  }

  private fun createTimelineItem(
    id: String = "1",
    text: String = "Hello world",
    authorName: String = "Alice",
    authorHandle: String = "alice",
    isRetweet: Boolean = false,
    isQuote: Boolean = false,
    quotedText: String = "",
    quotedAuthorHandle: String = "",
  ): XTimelineItem {
    return XTimelineItem(
      id = id,
      text = text,
      createdAt = "2025-02-20T12:34:56Z",
      authorName = authorName,
      authorHandle = authorHandle,
      lang = "en",
      replyTo = "",
      quoteCount = 0,
      replyCount = 0,
      retweetCount = 0,
      likeCount = 0,
      viewCount = null,
      isRetweet = isRetweet,
      isQuote = isQuote,
      quotedText = quotedText,
      quotedLang = "en",
      quotedAuthorHandle = quotedAuthorHandle,
      quotedMedia = emptyList(),
      url = "https://x.com/alice/status/$id",
      media = emptyList(),
    )
  }
}
