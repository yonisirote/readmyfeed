package com.yonisirote.readmyfeed.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TtsVoiceSelectionTest {
  @Test
  fun prefersLocalHebrewVoiceOverPlaceholderLanguageEntry() {
    // Hebrew often comes back as iw-IL, and *-language voices are usually placeholder entries.
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

  @Test
  fun prefersEnhancedQualityOverDefault() {
    val voices = listOf(
      TtsVoice(
        identifier = "en-default",
        language = "en-US",
        quality = TtsVoiceQuality.DEFAULT,
      ),
      TtsVoice(
        identifier = "en-enhanced",
        language = "en-US",
        quality = TtsVoiceQuality.ENHANCED,
      ),
    )

    val matchedVoice = findBestVoiceForLanguage(voices, "en-US")

    assertNotNull(matchedVoice)
    assertEquals("en-enhanced", matchedVoice?.identifier)
  }

  @Test
  fun prefersLocalVoiceOverNetworkVoice() {
    val voices = listOf(
      TtsVoice(
        identifier = "en-network-voice",
        language = "en-US",
        quality = TtsVoiceQuality.DEFAULT,
        requiresNetwork = true,
      ),
      TtsVoice(
        identifier = "en-local-voice",
        language = "en-US",
        quality = TtsVoiceQuality.DEFAULT,
      ),
    )

    val matchedVoice = findBestVoiceForLanguage(voices, "en-US")

    assertNotNull(matchedVoice)
    assertEquals("en-local-voice", matchedVoice?.identifier)
  }

  @Test
  fun fallsBackToPrimaryLanguageWhenRegionNotFound() {
    val voices = listOf(
      TtsVoice(
        identifier = "french-voice",
        language = "fr",
        quality = TtsVoiceQuality.DEFAULT,
      ),
    )

    val matchedVoice = findBestVoiceForLanguage(voices, "fr-FR")

    assertNotNull(matchedVoice)
    assertEquals("french-voice", matchedVoice?.identifier)
  }

  @Test
  fun handlesHebrewIwAliasWithoutRegion() {
    val voices = listOf(
      TtsVoice(
        identifier = "iw-voice",
        language = "iw",
        quality = TtsVoiceQuality.DEFAULT,
      ),
    )

    val matchedVoice = findBestVoiceForLanguage(voices, "he")

    assertNotNull(matchedVoice)
    assertEquals("iw-voice", matchedVoice?.identifier)
  }

  @Test
  fun returnsNullForEmptyVoiceList() {
    assertNull(findBestVoiceForLanguage(emptyList(), "en-US"))
  }

  @Test
  fun penalizesPlaceholderVoiceNames() {
    val voices = listOf(
      TtsVoice(
        identifier = "en-us-language",
        language = "en-US",
        quality = TtsVoiceQuality.DEFAULT,
      ),
      TtsVoice(
        identifier = "en-us-real",
        language = "en-US",
        quality = TtsVoiceQuality.DEFAULT,
      ),
    )

    val matchedVoice = findBestVoiceForLanguage(voices, "en-US")

    assertNotNull(matchedVoice)
    assertEquals("en-us-real", matchedVoice?.identifier)
  }

  @Test
  fun prefersDefaultNamedVoiceWhenQualityIsTied() {
    val voices = listOf(
      TtsVoice(
        identifier = "en-us-other",
        language = "en-US",
        quality = TtsVoiceQuality.DEFAULT,
      ),
      TtsVoice(
        identifier = "en-us-default",
        language = "en-US",
        quality = TtsVoiceQuality.DEFAULT,
      ),
    )

    val matchedVoice = findBestVoiceForLanguage(voices, "en-US")

    assertNotNull(matchedVoice)
    assertEquals("en-us-default", matchedVoice?.identifier)
  }
}
