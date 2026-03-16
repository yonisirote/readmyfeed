package com.yonisirote.readmyfeed.tts

private val placeholderVoicePattern = Regex("[-_]language$")

private fun normalizeLanguageTag(value: String): String {
  return value.replace('_', '-').lowercase()
}

private fun getLanguageCandidates(value: String): List<String> {
  val normalized = normalizeLanguageTag(value)
  val segments = normalized.split('-', limit = 2)
  val primary = segments.firstOrNull().orEmpty()
  val region = segments.getOrNull(1)

  if (primary == "he" || primary == "iw") {
    return if (region.isNullOrBlank()) {
      listOf("he", "iw")
    } else {
      listOf("he-$region", "iw-$region", "he", "iw")
    }
  }

  return if (region.isNullOrBlank()) {
    listOf(primary)
  } else {
    listOf(normalized, primary)
  }
}

private fun getVoicePriority(voice: TtsVoice): Int {
  val identifier = voice.identifier.lowercase()
  var score = 0

  if (identifier.contains("local")) {
    score += 4
  }

  if (voice.quality == TtsVoiceQuality.ENHANCED) {
    score += 2
  }

  if (identifier.contains("default")) {
    score += 1
  }

  if (voice.requiresNetwork || identifier.contains("network")) {
    score -= 1
  }

  if (placeholderVoicePattern.containsMatchIn(identifier)) {
    score -= 5
  }

  return score
}

private fun pickPreferredVoice(voices: List<TtsVoice>): TtsVoice? {
  var preferredVoice: TtsVoice? = null
  var preferredScore = Int.MIN_VALUE

  for (voice in voices) {
    val score = getVoicePriority(voice)
    if (preferredVoice == null || score > preferredScore) {
      preferredVoice = voice
      preferredScore = score
    }
  }

  return preferredVoice
}

fun findBestVoiceForLanguage(voices: List<TtsVoice>, language: String): TtsVoice? {
  for (candidate in getLanguageCandidates(language)) {
    val matchedVoice = pickPreferredVoice(
      voices.filter { normalizeLanguageTag(it.language) == candidate },
    )

    if (matchedVoice != null) {
      return matchedVoice
    }
  }

  return null
}
