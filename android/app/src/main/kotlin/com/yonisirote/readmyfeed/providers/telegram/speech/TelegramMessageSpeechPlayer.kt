package com.yonisirote.readmyfeed.providers.telegram.speech

import com.yonisirote.readmyfeed.providers.telegram.messages.TelegramMessageItem
import com.yonisirote.readmyfeed.tts.SpeakablePlaybackController
import com.yonisirote.readmyfeed.tts.TtsPlaybackSummary
import com.yonisirote.readmyfeed.tts.TtsService

typealias TelegramMessageSpeechPlaybackSummary = TtsPlaybackSummary

class TelegramMessageSpeechPlayer(
  ttsService: TtsService,
  private val playbackRate: Float = 0.95f,
) {
  private val playbackController = SpeakablePlaybackController<TelegramMessageItem>(
    ttsService = ttsService,
    playbackRate = playbackRate,
  )

  suspend fun speak(
    items: List<TelegramMessageItem>,
    onItemStart: ((item: TelegramMessageItem, index: Int, total: Int) -> Unit)? = null,
  ): TelegramMessageSpeechPlaybackSummary {
    return playbackController.speak(
      entries = items.mapNotNull { item -> item.toSpeakableEntry() },
      onItemStart = onItemStart,
    )
  }

  fun hasSpeakableItems(items: List<TelegramMessageItem>): Boolean {
    return playbackController.hasSpeakableItems(
      items.mapNotNull { item -> item.toSpeakableEntry() },
    )
  }

  fun stop() {
    playbackController.stop()
  }

  fun shutdown() {
    playbackController.shutdown()
  }
}
