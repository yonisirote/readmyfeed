package com.yonisirote.readmyfeed.providers.telegram.speech

import com.yonisirote.readmyfeed.providers.telegram.messages.TelegramMessageItem
import com.yonisirote.readmyfeed.providers.telegram.messages.isSpeakable
import com.yonisirote.readmyfeed.tts.SpeakableEntry
import com.yonisirote.readmyfeed.tts.SpeakableItem

fun TelegramMessageItem.toSpeakableEntry(): SpeakableEntry<TelegramMessageItem>? {
  if (!isSpeakable()) {
    return null
  }

  return SpeakableEntry(
    source = this,
    speakableItem = toSpeakableItem(),
  )
}

fun TelegramMessageItem.toSpeakableItem(): SpeakableItem {
  return SpeakableItem(
    text = text.trim(),
    authorLabel = authorLabel,
    lang = lang,
  )
}
