package com.yonisirote.readmyfeed.tts.x

import com.yonisirote.readmyfeed.tts.TtsEngine
import com.yonisirote.readmyfeed.tts.TtsService
import com.yonisirote.readmyfeed.tts.TtsSpeakOptions
import com.yonisirote.readmyfeed.tts.TtsVoice
import com.yonisirote.readmyfeed.tts.TtsVoiceQuality
import com.yonisirote.readmyfeed.x.timeline.XTimelineItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XTimelineSpeechPlayerTest {
  @Test
  fun speaksTimelineItemsSequentially() = runBlocking {
    val engine = FakeTtsEngine(
      availableVoices = listOf(
        TtsVoice(
          identifier = "en-us-local",
          language = "en-US",
          quality = TtsVoiceQuality.DEFAULT,
        ),
      ),
    )
    val player = XTimelineSpeechPlayer(TtsService(engine))

    val summary = player.speak(
      listOf(
        createTimelineItem(id = "1", text = "Hello world"),
        createTimelineItem(id = "2", text = "Another post"),
      ),
    )

    assertEquals(2, summary.totalItems)
    assertEquals(2, summary.spokenItems)
    assertEquals(0, summary.skippedItems)
    assertEquals(
      listOf("Alice says: Hello world", "Alice says: Another post"),
      engine.spokenTexts,
    )
  }

  @Test
  fun skipsItemsWhenLanguageSupportIsMissing() = runBlocking {
    val engine = FakeTtsEngine(
      availableVoices = listOf(
        TtsVoice(
          identifier = "en-us-local",
          language = "en-US",
          quality = TtsVoiceQuality.DEFAULT,
        ),
      ),
    )
    val player = XTimelineSpeechPlayer(TtsService(engine))

    val summary = player.speak(
      listOf(
        createTimelineItem(id = "1", text = "Hello world", lang = "en"),
        createTimelineItem(id = "2", text = "shalom", lang = "he"),
      ),
    )

    assertEquals(2, summary.totalItems)
    assertEquals(2, summary.speakableItems)
    assertEquals(1, summary.spokenItems)
    assertEquals(1, summary.skippedItems)
    assertEquals(listOf("Alice says: Hello world"), engine.spokenTexts)
  }

  @Test
  fun detectsWhenNoSpeakableItemsExist() {
    val player = XTimelineSpeechPlayer(TtsService(FakeTtsEngine()))

    assertFalse(player.hasSpeakableItems(listOf(createTimelineItem(text = "   "))))
    assertTrue(player.hasSpeakableItems(listOf(createTimelineItem(text = "Hello world"))))
  }

  private fun createTimelineItem(
    id: String = "1",
    text: String = "Hello world",
    lang: String = "en",
  ): XTimelineItem {
    return XTimelineItem(
      id = id,
      text = text,
      createdAt = "2025-02-20T12:34:56Z",
      authorName = "Alice",
      authorHandle = "alice",
      lang = lang,
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

  private class FakeTtsEngine(
    private val availableVoices: List<TtsVoice> = emptyList(),
  ) : TtsEngine {
    val spokenTexts = mutableListOf<String>()

    override suspend fun initialize() = Unit

    override fun voices(): List<TtsVoice> = availableVoices

    override suspend fun speak(text: String, options: TtsSpeakOptions) {
      spokenTexts += text
    }

    override fun stop() = Unit

    override fun shutdown() = Unit
  }
}
