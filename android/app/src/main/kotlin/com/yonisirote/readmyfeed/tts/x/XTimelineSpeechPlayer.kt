package com.yonisirote.readmyfeed.tts.x

import com.yonisirote.readmyfeed.tts.SpeakableItem
import com.yonisirote.readmyfeed.tts.TtsService
import com.yonisirote.readmyfeed.tts.TtsSpeakOptions
import com.yonisirote.readmyfeed.tts.getSpeakableItemLanguage
import com.yonisirote.readmyfeed.tts.getSpeakableItemText
import com.yonisirote.readmyfeed.x.timeline.XTimelineItem

data class XTimelineSpeechPlaybackSummary(
  val totalItems: Int,
  val speakableItems: Int,
  val spokenItems: Int,
  val skippedItems: Int,
)

class XTimelineSpeechPlayer(
  private val ttsService: TtsService,
  private val playbackRate: Float = 0.95f,
) {
  suspend fun speak(
    items: List<XTimelineItem>,
    onItemStart: ((item: XTimelineItem, index: Int, total: Int) -> Unit)? = null,
  ): XTimelineSpeechPlaybackSummary {
    val candidates = items.mapNotNull { item ->
      val speakableItem = item.toSpeakableItem()
      val trimmedText = speakableItem.text.trim()

      if (trimmedText.isBlank()) {
        null
      } else {
        ResolvedSpeechItem(
          timelineItem = item,
          speakableItem = speakableItem.copy(text = trimmedText),
          language = getSpeakableItemLanguage(speakableItem),
        )
      }
    }

    if (candidates.isEmpty()) {
      return XTimelineSpeechPlaybackSummary(
        totalItems = items.size,
        speakableItems = 0,
        spokenItems = 0,
        skippedItems = items.size,
      )
    }

    ttsService.initialize()

    val playableItems = mutableListOf<ResolvedSpeechItem>()
    var skippedItems = items.size - candidates.size

    for (candidate in candidates) {
      val language = candidate.language
      if (!language.isNullOrBlank() && !ttsService.hasLanguageSupport(language)) {
        skippedItems += 1
        continue
      }

      playableItems += candidate
    }

    for ((index, candidate) in playableItems.withIndex()) {
      onItemStart?.invoke(candidate.timelineItem, index + 1, playableItems.size)

      ttsService.speak(
        getSpeakableItemText(candidate.speakableItem),
        TtsSpeakOptions(
          language = candidate.language,
          rate = playbackRate,
        ),
      )
    }

    return XTimelineSpeechPlaybackSummary(
      totalItems = items.size,
      speakableItems = candidates.size,
      spokenItems = playableItems.size,
      skippedItems = skippedItems,
    )
  }

  fun hasSpeakableItems(items: List<XTimelineItem>): Boolean {
    return items.any { item -> item.toSpeakableItem().text.trim().isNotBlank() }
  }

  fun stop() {
    ttsService.stop()
  }

  fun shutdown() {
    ttsService.deinitialize()
  }

  private data class ResolvedSpeechItem(
    val timelineItem: XTimelineItem,
    val speakableItem: SpeakableItem,
    val language: String?,
  )
}
