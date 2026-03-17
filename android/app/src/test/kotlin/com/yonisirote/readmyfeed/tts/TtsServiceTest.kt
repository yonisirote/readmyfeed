package com.yonisirote.readmyfeed.tts

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsServiceTest {
  @Test
  fun initializesByLoadingAvailableVoicesOnce() = runBlocking {
    val engine = FakeTtsEngine()
    val service = TtsService(engine)

    service.initialize()
    service.initialize()

    assertEquals(1, engine.initializeCount)
  }

  @Test
  fun resolvesHebrewToInstalledVoice() = runBlocking {
    val engine = FakeTtsEngine(
      availableVoices = listOf(
        TtsVoice(
          identifier = "he-il-language",
          language = "iw-IL",
          quality = TtsVoiceQuality.DEFAULT,
        ),
        TtsVoice(
          identifier = "hebrew-voice",
          language = "iw-IL",
          quality = TtsVoiceQuality.DEFAULT,
        ),
      ),
    )
    val service = TtsService(engine)

    service.initialize()
    service.speak("שלום", TtsSpeakOptions(language = "he-IL"))

    assertEquals(1, engine.speakCalls.size)
    assertEquals("iw-IL", engine.speakCalls.single().options.language)
    assertEquals("hebrew-voice", engine.speakCalls.single().options.voice)
  }

  @Test
  fun keepsRequestedLanguageWhenNoInstalledVoiceMatches() = runBlocking {
    val engine = FakeTtsEngine()
    val service = TtsService(engine)

    service.initialize()
    service.speak("Hello", TtsSpeakOptions(language = "en-US"))

    assertEquals("en-US", engine.speakCalls.single().options.language)
  }

  @Test
  fun reportsInstalledLanguageSupport() = runBlocking {
    val engine = FakeTtsEngine(
      availableVoices = listOf(
        TtsVoice(
          identifier = "hebrew-voice",
          language = "iw-IL",
          quality = TtsVoiceQuality.DEFAULT,
        ),
      ),
    )
    val service = TtsService(engine)

    service.initialize()

    assertTrue(service.hasLanguageSupport("he-IL"))
    assertFalse(service.hasLanguageSupport("en-US"))
  }

  @Test
  fun throwsWhenSpeakingBeforeInitialization() = runBlocking {
    val service = TtsService(FakeTtsEngine())

    try {
      service.speak("hello")
    } catch (error: TtsException) {
      assertEquals(TtsErrorCodes.NOT_INITIALIZED, error.code)
      return@runBlocking
    }

    throw AssertionError("Expected TtsException")
  }

  @Test
  fun stopsAndDeinitializesActiveEngine() = runBlocking {
    val engine = FakeTtsEngine()
    val service = TtsService(engine)

    service.initialize()
    service.stop()
    service.deinitialize()

    assertEquals(1, engine.stopCount)
    assertEquals(1, engine.shutdownCount)

    try {
      service.speak("hello")
    } catch (error: TtsException) {
      assertEquals(TtsErrorCodes.NOT_INITIALIZED, error.code)
      return@runBlocking
    }

    throw AssertionError("Expected TtsException after deinitialize")
  }

  @Test
  fun skipsSpeakingEmptyText() = runBlocking {
    val engine = FakeTtsEngine()
    val service = TtsService(engine)

    service.initialize()
    service.speak("")

    assertTrue(engine.speakCalls.isEmpty())
  }

  @Test
  fun skipsSpeakingWhitespaceText() = runBlocking {
    val engine = FakeTtsEngine()
    val service = TtsService(engine)

    service.initialize()
    service.speak("  ")

    assertTrue(engine.speakCalls.isEmpty())
  }

  @Test
  fun trimsTextBeforeSpeaking() = runBlocking {
    val engine = FakeTtsEngine()
    val service = TtsService(engine)

    service.initialize()
    service.speak("  hello  ")

    assertEquals(1, engine.speakCalls.size)
    assertEquals("hello", engine.speakCalls.single().text)
  }

  @Test
  fun shutdownIsIdempotent() = runBlocking {
    val engine = FakeTtsEngine()
    val service = TtsService(engine)

    service.initialize()
    service.shutdown()
    service.shutdown()

    assertEquals(1, engine.shutdownCount)
  }

  @Test
  fun stopBeforeInitializeDoesNotThrow() {
    val service = TtsService(FakeTtsEngine())

    service.stop()
  }

  @Test
  fun shutdownBeforeInitializeDoesNotThrow() {
    val service = TtsService(FakeTtsEngine())

    service.shutdown()
  }

  @Test
  fun hasLanguageSupportThrowsBeforeInit() {
    val service = TtsService(FakeTtsEngine())

    try {
      service.hasLanguageSupport("en-US")
    } catch (error: TtsException) {
      assertEquals(TtsErrorCodes.NOT_INITIALIZED, error.code)
      return
    }

    throw AssertionError("Expected TtsException")
  }

  @Test
  fun preservesVoiceWhenAlreadySet() = runBlocking {
    val engine = FakeTtsEngine(
      availableVoices = listOf(
        TtsVoice(
          identifier = "hebrew-voice",
          language = "iw-IL",
          quality = TtsVoiceQuality.DEFAULT,
        ),
      ),
    )
    val service = TtsService(engine)

    service.initialize()
    service.speak(
      "שלום",
      TtsSpeakOptions(language = "he-IL", voice = "custom-voice"),
    )

    assertEquals(1, engine.speakCalls.size)
    assertEquals("custom-voice", engine.speakCalls.single().options.voice)
    assertEquals("he-IL", engine.speakCalls.single().options.language)
  }

  private class FakeTtsEngine(
    private val availableVoices: List<TtsVoice> = emptyList(),
  ) : TtsEngine {
    var initializeCount: Int = 0
    var stopCount: Int = 0
    var shutdownCount: Int = 0
    val speakCalls = mutableListOf<SpeakCall>()

    override suspend fun initialize() {
      initializeCount += 1
    }

    override fun voices(): List<TtsVoice> {
      return availableVoices
    }

    override suspend fun speak(text: String, options: TtsSpeakOptions) {
      speakCalls += SpeakCall(text, options)
    }

    override fun stop() {
      stopCount += 1
    }

    override fun shutdown() {
      shutdownCount += 1
    }
  }

  data class SpeakCall(
    val text: String,
    val options: TtsSpeakOptions,
  )
}
