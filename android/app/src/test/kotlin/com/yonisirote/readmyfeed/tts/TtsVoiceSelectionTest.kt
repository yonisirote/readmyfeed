package com.yonisirote.readmyfeed.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TtsVoiceSelectionTest {
  @Test
  fun prefersLocalHebrewVoiceOverPlaceholderLanguageEntry() {
    val voices = listOf(
      TtsVoice(
        identifier = "he-il-language",
        language = "iw-IL",
        quality = TtsVoiceQuality.DEFAULT,
      ),
      TtsVoice(
        identifier = "he-il-heb-local",
        language = "iw-IL",
        quality = TtsVoiceQuality.DEFAULT,
      ),
    )

    val matchedVoice = findBestVoiceForLanguage(voices, "he-IL")

    assertNotNull(matchedVoice)
    assertEquals("he-il-heb-local", matchedVoice?.identifier)
    assertEquals("iw-IL", matchedVoice?.language)
  }

  @Test
  fun prefersExactEnglishRegionVoiceOverPrimaryLanguageFallback() {
    val voices = listOf(
      TtsVoice(
        identifier = "australian-voice",
        language = "en-AU",
        quality = TtsVoiceQuality.DEFAULT,
      ),
      TtsVoice(
        identifier = "us-voice",
        language = "en-US",
        quality = TtsVoiceQuality.DEFAULT,
      ),
    )

    val matchedVoice = findBestVoiceForLanguage(voices, "en-US")

    assertNotNull(matchedVoice)
    assertEquals("us-voice", matchedVoice?.identifier)
  }

  @Test
  fun returnsNullWhenRequestedLanguageIsMissing() {
    val voices = listOf(
      TtsVoice(
        identifier = "english-voice",
        language = "en-US",
        quality = TtsVoiceQuality.DEFAULT,
      ),
    )

    assertNull(findBestVoiceForLanguage(voices, "fr-FR"))
  }
}
