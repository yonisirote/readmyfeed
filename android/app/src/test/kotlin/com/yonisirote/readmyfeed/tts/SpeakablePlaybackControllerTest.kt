package com.yonisirote.readmyfeed.tts

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakablePlaybackControllerTest {
  @Test
  fun speaksEntriesSequentiallyAndReportsProgress() = runBlocking {
    val engine = FakeTtsEngine(
      availableVoices = listOf(
        TtsVoice(
          identifier = "en-us-local",
          language = "en-US",
          quality = TtsVoiceQuality.DEFAULT,
        ),
      ),
    )
    val controller = SpeakablePlaybackController<String>(TtsService(engine))
    val callbacks = mutableListOf<Triple<String, Int, Int>>()

    val summary = controller.speak(
      entries = listOf(
        SpeakableEntry(
          source = "1",
          speakableItem = SpeakableItem(
            text = "Hello world",
            authorLabel = "Alice",
            lang = "en",
          ),
        ),
        SpeakableEntry(
          source = "2",
          speakableItem = SpeakableItem(
            text = "Another post",
            authorLabel = "Bob",
            lang = "en",
          ),
        ),
      ),
      onItemStart = { source, index, total ->
        callbacks += Triple(source, index, total)
      },
    )

    assertEquals(2, summary.totalItems)
    assertEquals(2, summary.speakableItems)
    assertEquals(2, summary.spokenItems)
    assertEquals(0, summary.skippedItems)
    assertEquals(
      listOf("Alice says: Hello world", "Bob says: Another post"),
      engine.spokenTexts,
    )
    assertEquals(
      listOf(Triple("1", 1, 2), Triple("2", 2, 2)),
      callbacks,
    )
  }

  @Test
  fun skipsUnsupportedLanguagesAndBlankEntries() = runBlocking {
    val engine = FakeTtsEngine(
      availableVoices = listOf(
        TtsVoice(
          identifier = "en-us-local",
          language = "en-US",
          quality = TtsVoiceQuality.DEFAULT,
        ),
      ),
    )
    val controller = SpeakablePlaybackController<String>(TtsService(engine))

    val summary = controller.speak(
      entries = listOf(
        SpeakableEntry(
          source = "1",
          speakableItem = SpeakableItem(
            text = "Visible",
            authorLabel = "Alice",
            lang = "en",
          ),
        ),
        SpeakableEntry(
          source = "2",
          speakableItem = SpeakableItem(
            text = "   ",
            authorLabel = "Bob",
            lang = "en",
          ),
        ),
        SpeakableEntry(
          source = "3",
          speakableItem = SpeakableItem(
            text = "shalom",
            authorLabel = "Carol",
            lang = "he",
          ),
        ),
      ),
    )

    assertEquals(3, summary.totalItems)
    assertEquals(2, summary.speakableItems)
    assertEquals(1, summary.spokenItems)
    assertEquals(2, summary.skippedItems)
    assertEquals(listOf("Alice says: Visible"), engine.spokenTexts)
  }

  @Test
  fun detectsWhenSpeakableEntriesExist() {
    val controller = SpeakablePlaybackController<String>(TtsService(FakeTtsEngine()))

    assertFalse(
      controller.hasSpeakableItems(
        listOf(
          SpeakableEntry(
            source = "1",
            speakableItem = SpeakableItem(
              text = "   ",
              authorLabel = "Alice",
              lang = "en",
            ),
          ),
        ),
      ),
    )
    assertTrue(
      controller.hasSpeakableItems(
        listOf(
          SpeakableEntry(
            source = "1",
            speakableItem = SpeakableItem(
              text = "Hello world",
              authorLabel = "Alice",
              lang = "en",
            ),
          ),
        ),
      ),
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
