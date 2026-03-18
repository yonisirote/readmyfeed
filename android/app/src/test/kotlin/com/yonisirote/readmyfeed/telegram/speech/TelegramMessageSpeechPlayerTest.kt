package com.yonisirote.readmyfeed.telegram.speech

import com.yonisirote.readmyfeed.telegram.messages.TelegramMessageContentKind
import com.yonisirote.readmyfeed.telegram.messages.TelegramMessageItem
import com.yonisirote.readmyfeed.tts.TtsEngine
import com.yonisirote.readmyfeed.tts.TtsService
import com.yonisirote.readmyfeed.tts.TtsSpeakOptions
import com.yonisirote.readmyfeed.tts.TtsVoice
import com.yonisirote.readmyfeed.tts.TtsVoiceQuality
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramMessageSpeechPlayerTest {
  @Test
  fun speaksUnreadMessagesSequentially() = runBlocking {
    val engine = FakeTtsEngine(
      availableVoices = listOf(
        TtsVoice(
          identifier = "en-us-local",
          language = "en-US",
          quality = TtsVoiceQuality.DEFAULT,
        ),
      ),
    )
    val player = TelegramMessageSpeechPlayer(TtsService(engine))

    val summary = player.speak(
      listOf(
        createMessage(messageId = 1L, text = "Hello"),
        createMessage(messageId = 2L, text = "Another one", authorLabel = "Bob"),
      ),
    )

    assertEquals(2, summary.totalItems)
    assertEquals(2, summary.spokenItems)
    assertEquals(listOf("Alice says: Hello", "Bob says: Another one"), engine.spokenTexts)
  }

  @Test
  fun detectsMissingSpeakableItems() {
    val player = TelegramMessageSpeechPlayer(TtsService(FakeTtsEngine()))

    assertFalse(player.hasSpeakableItems(listOf(createMessage(text = " ", isUnread = false))))
    assertTrue(player.hasSpeakableItems(listOf(createMessage(text = "ready"))))
  }

  @Test
  fun propagatesProgressAndDelegatesStopAndShutdown() = runBlocking {
    val engine = FakeTtsEngine(
      availableVoices = listOf(
        TtsVoice(
          identifier = "en-us-local",
          language = "en-US",
          quality = TtsVoiceQuality.DEFAULT,
        ),
      ),
    )
    val player = TelegramMessageSpeechPlayer(TtsService(engine), playbackRate = 1.3f)
    val callbacks = mutableListOf<Triple<Long, Int, Int>>()

    player.speak(
      listOf(
        createMessage(messageId = 10L, text = "first"),
        createMessage(messageId = 11L, text = "second"),
      ),
    ) { item, index, total ->
      callbacks += Triple(item.messageId, index, total)
    }

    player.stop()
    player.shutdown()

    assertEquals(listOf(Triple(10L, 1, 2), Triple(11L, 2, 2)), callbacks)
    assertTrue(engine.stopCalled)
    assertTrue(engine.shutdownCalled)
    assertEquals(1.3f, engine.lastRate)
  }

  private fun createMessage(
    messageId: Long = 1L,
    text: String = "Hello",
    authorLabel: String = "Alice",
    isUnread: Boolean = true,
  ): TelegramMessageItem {
    return TelegramMessageItem(
      messageId = messageId,
      chatId = 5L,
      authorLabel = authorLabel,
      text = text,
      lang = "en",
      isUnread = isUnread,
      contentKind = TelegramMessageContentKind.TEXT,
    )
  }

  private class FakeTtsEngine(
    private val availableVoices: List<TtsVoice> = emptyList(),
  ) : TtsEngine {
    val spokenTexts = mutableListOf<String>()
    var stopCalled = false
    var shutdownCalled = false
    var lastRate: Float? = null

    override suspend fun initialize() = Unit

    override fun voices(): List<TtsVoice> = availableVoices

    override suspend fun speak(text: String, options: TtsSpeakOptions) {
      spokenTexts += text
      lastRate = options.rate
    }

    override fun stop() {
      stopCalled = true
    }

    override fun shutdown() {
      shutdownCalled = true
    }
  }
}
