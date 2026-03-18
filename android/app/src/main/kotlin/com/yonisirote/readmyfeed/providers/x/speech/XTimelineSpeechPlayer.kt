package com.yonisirote.readmyfeed.providers.x.speech

import com.yonisirote.readmyfeed.tts.SpeakablePlaybackController
import com.yonisirote.readmyfeed.tts.TtsPlaybackSummary
import com.yonisirote.readmyfeed.tts.TtsService
import com.yonisirote.readmyfeed.providers.x.timeline.XTimelineItem

typealias XTimelineSpeechPlaybackSummary = TtsPlaybackSummary

class XTimelineSpeechPlayer(
  ttsService: TtsService,
  private val playbackRate: Float = 0.95f,
) {
  private val playbackController = SpeakablePlaybackController<XTimelineItem>(
    ttsService = ttsService,
    playbackRate = playbackRate,
  )

  suspend fun speak(
    items: List<XTimelineItem>,
    onItemStart: ((item: XTimelineItem, index: Int, total: Int) -> Unit)? = null,
  ): XTimelineSpeechPlaybackSummary {
    return playbackController.speak(
      entries = items.map { item -> item.toSpeakableEntry() },
      onItemStart = onItemStart,
    )
  }

  fun hasSpeakableItems(items: List<XTimelineItem>): Boolean {
    return playbackController.hasSpeakableItems(
      items.map { item -> item.toSpeakableEntry() },
    )
  }

  fun stop() {
    playbackController.stop()
  }

  fun shutdown() {
    playbackController.shutdown()
  }
}
