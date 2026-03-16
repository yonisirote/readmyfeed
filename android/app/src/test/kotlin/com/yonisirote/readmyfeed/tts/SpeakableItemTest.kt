package com.yonisirote.readmyfeed.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeakableItemTest {
  @Test
  fun prefersHebrewPlaybackWhenTextContainsHebrewCharacters() {
    val item = SpeakableItem(
      text = "Hello שלום",
      authorLabel = "yoni",
      lang = "en",
    )

    assertEquals("he-IL", getSpeakableItemLanguage(item))
  }

  @Test
  fun mapsEnglishContentToEnglishSpeech() {
    val item = SpeakableItem(
      text = "Hello world",
      authorLabel = "yoni",
      lang = "en",
    )

    assertEquals("en-US", getSpeakableItemLanguage(item))
  }

  @Test
  fun formatsAuthorLabelIntoSpeakableText() {
    val item = SpeakableItem(
      text = "Hello world",
      authorLabel = "yoni",
      lang = "en",
    )

    assertEquals("yoni says: Hello world", getSpeakableItemText(item))
  }

  @Test
  fun usesHebrewConnectorWhenTextContainsHebrewCharacters() {
    val item = SpeakableItem(
      text = "שלום עולם",
      authorLabel = "yoni",
      lang = "he",
    )

    assertEquals("yoni אומר: שלום עולם", getSpeakableItemText(item))
  }

  @Test
  fun omitsAuthorPrefixWhenAuthorLabelIsEmpty() {
    val item = SpeakableItem(
      text = "Hello world",
      authorLabel = "",
      lang = "en",
    )

    assertEquals("Hello world", getSpeakableItemText(item))
  }
}
