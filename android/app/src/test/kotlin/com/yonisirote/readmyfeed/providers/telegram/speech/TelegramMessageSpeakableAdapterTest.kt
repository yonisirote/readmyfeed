package com.yonisirote.readmyfeed.providers.telegram.speech

import com.yonisirote.readmyfeed.providers.telegram.messages.TelegramMessageContentKind
import com.yonisirote.readmyfeed.providers.telegram.messages.TelegramMessageItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelegramMessageSpeakableAdapterTest {
  @Test
  fun mapsUnreadTextMessageToSpeakableEntry() {
    val message = TelegramMessageItem(
      messageId = 1L,
      chatId = 2L,
      authorLabel = "Alice",
      text = " Hello from Telegram ",
      lang = "en",
      isUnread = true,
      contentKind = TelegramMessageContentKind.TEXT,
    )

    val entry = message.toSpeakableEntry()

    requireNotNull(entry)
    assertEquals("Hello from Telegram", entry.speakableItem.text)
    assertEquals("Alice", entry.speakableItem.authorLabel)
    assertEquals("en", entry.speakableItem.lang)
  }

  @Test
  fun toSpeakableItemKeepsTrimmedMessagePayload() {
    val item = TelegramMessageItem(
      messageId = 7L,
      chatId = 3L,
      authorLabel = "Dana",
      text = "  caption text  ",
      lang = "he",
      isUnread = true,
      contentKind = TelegramMessageContentKind.CAPTION,
    )

    val speakable = item.toSpeakableItem()

    assertEquals("caption text", speakable.text)
    assertEquals("Dana", speakable.authorLabel)
    assertEquals("he", speakable.lang)
  }

  @Test
  fun skipsUnsupportedOrReadMessages() {
    assertNull(
      TelegramMessageItem(
        messageId = 1L,
        chatId = 2L,
        authorLabel = "Alice",
        text = "Hi",
        lang = "en",
        isUnread = false,
        contentKind = TelegramMessageContentKind.TEXT,
      ).toSpeakableEntry(),
    )
    assertNull(
      TelegramMessageItem(
        messageId = 1L,
        chatId = 2L,
        authorLabel = "Alice",
        text = "Hi",
        lang = "en",
        isUnread = true,
        contentKind = TelegramMessageContentKind.UNSUPPORTED,
      ).toSpeakableEntry(),
    )
    assertNull(
      TelegramMessageItem(
        messageId = 1L,
        chatId = 2L,
        authorLabel = "Alice",
        text = "   ",
        lang = "en",
        isUnread = true,
        contentKind = TelegramMessageContentKind.TEXT,
      ).toSpeakableEntry(),
    )
  }
}
