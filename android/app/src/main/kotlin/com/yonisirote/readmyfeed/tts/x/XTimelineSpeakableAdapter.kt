package com.yonisirote.readmyfeed.tts.x

import com.yonisirote.readmyfeed.tts.SpeakableItem
import com.yonisirote.readmyfeed.x.timeline.XTimelineItem

fun XTimelineItem.toSpeakableItem(): SpeakableItem {
  val prefix = if (isRetweet) {
    "Reposted. "
  } else {
    ""
  }

  val body = if (isQuote && quotedText.isNotBlank()) {
    val quoteAttribution = if (quotedAuthorHandle.isNotBlank()) {
      "Quoting $quotedAuthorHandle. "
    } else {
      "Quote. "
    }

    "${text.trim()} $quoteAttribution${quotedText.trim()}".trim()
  } else {
    text.trim()
  }

  return SpeakableItem(
    text = "$prefix$body".trim(),
    authorLabel = authorName.ifBlank { authorHandle },
    lang = lang,
  )
}
