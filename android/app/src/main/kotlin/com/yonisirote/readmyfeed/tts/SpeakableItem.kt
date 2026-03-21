package com.yonisirote.readmyfeed.tts

// Map content languages to concrete engine locales we actually want to request.
private val speechLanguageByContentLanguage = mapOf(
  "en" to "en-US",
  "he" to "he-IL",
)

private val hebrewCharacterPattern = Regex("[\\u0590-\\u05FF]")

data class SpeakableItem(
  val text: String,
  val authorLabel: String,
  val lang: String,
)

fun getSpeakableItemLanguage(item: SpeakableItem): String? {
  // Mixed-script posts are often mislabeled, so obvious Hebrew text wins over metadata.
  if (hebrewCharacterPattern.containsMatchIn(item.text)) {
    return "he-IL"
  }

  return speechLanguageByContentLanguage[item.lang.trim().lowercase()]
}

fun getSpeakableItemText(item: SpeakableItem): String {
  if (item.authorLabel.isBlank()) {
    return item.text.trim()
  }

  val connector = if (getSpeakableItemLanguage(item) == "he-IL") {
    "\u05d0\u05d5\u05de\u05e8:"
  } else {
    "says:"
  }

  // Use the resolved speech language here so mixed-script posts still sound natural.
  return "${item.authorLabel.trim()} $connector ${item.text.trim()}".trim()
}
