package com.yonisirote.readmyfeed.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

  @Test
  fun returnsNullLanguageForUnknownContentLanguage() {
    val item = SpeakableItem(
      text = "Bonjour le monde",
      authorLabel = "yoni",
      lang = "fr",
    )

    assertNull(getSpeakableItemLanguage(item))
  }

  @Test
  fun mapsHebrewContentLanguageToHebrewSpeech() {
    val item = SpeakableItem(
      text = "שלום עולם",
      authorLabel = "yoni",
      lang = "he",
    )

    assertEquals("he-IL", getSpeakableItemLanguage(item))
  }

  @Test
  fun hebrewCharactersOverrideContentLanguage() {
    val item = SpeakableItem(
      text = "Test שלום",
      authorLabel = "yoni",
      lang = "en",
    )

    assertEquals("he-IL", getSpeakableItemLanguage(item))
  }

  @Test
  fun handlesBlankTextWithAuthor() {
    val item = SpeakableItem(
      text = "   ",
      authorLabel = "yoni",
      lang = "en",
    )

    assertEquals("yoni says:", getSpeakableItemText(item))
  }

  @Test
  fun handlesBothBlankTextAndAuthor() {
    val item = SpeakableItem(
      text = "   ",
      authorLabel = "",
      lang = "en",
    )

    assertEquals("", getSpeakableItemText(item))
  }

  // BUG: getSpeakableItemText does not trim item.text before concatenation
  // with the author prefix, so leading whitespace in text leaks into the
  // spoken output as "yoni says:   Hello world" instead of "yoni says: Hello world".
  @Test
  fun trimsWhitespaceInGetSpeakableItemText() {
    val item = SpeakableItem(
      text = "  Hello world  ",
      authorLabel = "yoni",
      lang = "en",
    )

    // Correct expectation: text should be trimmed before concatenation.
    // The production code currently produces "yoni says:   Hello world"
    // because it does not call .trim() on item.text inside the template.
    assertEquals("yoni says: Hello world", getSpeakableItemText(item))
  }
}
