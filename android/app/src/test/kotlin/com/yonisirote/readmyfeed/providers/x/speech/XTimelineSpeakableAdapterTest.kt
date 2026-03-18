package com.yonisirote.readmyfeed.providers.x.speech

import com.yonisirote.readmyfeed.providers.x.timeline.XTimelineItem
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

  @Test
  fun producesPlainTextForRegularPost() {
    val speakable = createTimelineItem(
      text = "Just a regular post",
      isRetweet = false,
      isQuote = false,
    ).toSpeakableItem()

    assertEquals("Just a regular post", speakable.text)
  }

  @Test
  fun addsGenericQuoteLabelWhenQuotedAuthorHandleIsBlank() {
    val speakable = createTimelineItem(
      text = "Look at this",
      isQuote = true,
      quotedText = "some text",
      quotedAuthorHandle = "",
    ).toSpeakableItem()

    assertEquals("Look at this Quote. some text", speakable.text)
  }

  @Test
  fun skipsQuoteBodyWhenQuotedTextIsBlank() {
    val speakable = createTimelineItem(
      text = "My thoughts",
      isQuote = true,
      quotedText = "",
      quotedAuthorHandle = "someone",
    ).toSpeakableItem()

    assertEquals("My thoughts", speakable.text)
  }

  @Test
  fun combinesRetweetPrefixWithPlainText() {
    val speakable = createTimelineItem(
      text = "Great post",
      isRetweet = true,
      isQuote = false,
    ).toSpeakableItem()

    assertEquals("Reposted. Great post", speakable.text)
  }

  @Test
  fun usesAuthorNameOverHandle() {
    val speakable = createTimelineItem(
      authorName = "Bob",
      authorHandle = "bob",
    ).toSpeakableItem()

    assertEquals("Bob", speakable.authorLabel)
  }

  @Test
  fun usesOriginalLang() {
    val speakable = createTimelineItem(lang = "he").toSpeakableItem()

    assertEquals("he", speakable.lang)
  }

  @Test
  fun trimsTextWithWhitespace() {
    val speakable = createTimelineItem(text = "  hello world  ").toSpeakableItem()

    assertEquals("hello world", speakable.text)
  }

  @Test
  fun handlesBlankTextAndBlankAuthor() {
    val speakable = createTimelineItem(
      text = "",
      authorName = "",
      authorHandle = "",
    ).toSpeakableItem()

    assertEquals("", speakable.text)
    assertEquals("", speakable.authorLabel)
  }

  private fun createTimelineItem(
    id: String = "1",
    text: String = "Hello world",
    authorName: String = "Alice",
    authorHandle: String = "alice",
    lang: String = "en",
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
      lang = lang,
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
