package com.yonisirote.readmyfeed.tts

data class SpeakableEntry<T>(
  val source: T,
  val speakableItem: SpeakableItem,
)

data class TtsPlaybackSummary(
  val totalItems: Int,
  val speakableItems: Int,
  val spokenItems: Int,
  val skippedItems: Int,
)

class SpeakablePlaybackController<T>(
  private val ttsService: TtsService,
  private val playbackRate: Float = 0.95f,
) {
  suspend fun speak(
    entries: List<SpeakableEntry<T>>,
    onItemStart: ((source: T, index: Int, total: Int) -> Unit)? = null,
  ): TtsPlaybackSummary {
    val candidates = entries.mapNotNull { entry ->
      val trimmedText = entry.speakableItem.text.trim()
      if (trimmedText.isBlank()) {
        null
      } else {
        val trimmedSpeakableItem = entry.speakableItem.copy(text = trimmedText)
        ResolvedSpeakableEntry(
          source = entry.source,
          speakableItem = trimmedSpeakableItem,
          language = getSpeakableItemLanguage(trimmedSpeakableItem),
        )
      }
    }

    if (candidates.isEmpty()) {
      return TtsPlaybackSummary(
        totalItems = entries.size,
        speakableItems = 0,
        spokenItems = 0,
        skippedItems = entries.size,
      )
    }

    ttsService.initialize()

    val playableItems = mutableListOf<ResolvedSpeakableEntry<T>>()
    var skippedItems = entries.size - candidates.size

    for (candidate in candidates) {
      val language = candidate.language
      if (!language.isNullOrBlank() && !ttsService.hasLanguageSupport(language)) {
        skippedItems += 1
        continue
      }

      playableItems += candidate
    }

    for ((index, candidate) in playableItems.withIndex()) {
      onItemStart?.invoke(candidate.source, index + 1, playableItems.size)

      ttsService.speak(
        getSpeakableItemText(candidate.speakableItem),
        TtsSpeakOptions(
          language = candidate.language,
          rate = playbackRate,
        ),
      )
    }

    return TtsPlaybackSummary(
      totalItems = entries.size,
      speakableItems = candidates.size,
      spokenItems = playableItems.size,
      skippedItems = skippedItems,
    )
  }

  fun hasSpeakableItems(entries: List<SpeakableEntry<T>>): Boolean {
    return entries.any { entry -> entry.speakableItem.text.trim().isNotBlank() }
  }

  fun stop() {
    ttsService.stop()
  }

  fun shutdown() {
    ttsService.deinitialize()
  }

  private data class ResolvedSpeakableEntry<T>(
    val source: T,
    val speakableItem: SpeakableItem,
    val language: String?,
  )
}
