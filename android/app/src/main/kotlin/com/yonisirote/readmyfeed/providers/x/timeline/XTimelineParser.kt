package com.yonisirote.readmyfeed.providers.x.timeline

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MAX_FILTER_MATCHES = 1000
private const val MAX_FILTER_NODES = 15000

private val parserJson = Json { ignoreUnknownKeys = true }
private val xDateFormatter = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss Z yyyy", Locale.ENGLISH)

fun parseXFollowingTimelineResponse(payload: String): XFollowingTimelineBatch {
  val root = try {
    parserJson.parseToJsonElement(payload)
  } catch (error: Exception) {
    throw XTimelineException(
      message = "Timeline response was not valid JSON.",
      code = XTimelineErrorCodes.RESPONSE_INVALID,
      context = mapOf("cause" to (error.message ?: error::class.java.simpleName)),
      cause = error,
    )
  }

  return parseXFollowingTimelineResponse(root)
}

fun parseXFollowingTimelineResponse(payload: JsonElement): XFollowingTimelineBatch {
  val timelineTweetNodes = findByFilter(payload, "__typename", "TimelineTweet")
  val deduped = LinkedHashMap<String, XTimelineItem>()

  for (node in timelineTweetNodes) {
    val item = extractTweet(node) ?: continue
    if (item.id.isBlank() || deduped.containsKey(item.id)) {
      continue
    }
    deduped[item.id] = item
  }

  return XFollowingTimelineBatch(
    items = deduped.values.toList(),
    nextCursor = extractNextCursor(payload),
  )
}

private fun findByFilter(data: JsonElement, key: String, value: String): List<JsonObject> {
  val result = mutableListOf<JsonObject>()
  var visitedNodes = 0

  fun visit(node: JsonElement?) {
    // X payloads can get very large, so cap the walk before defensive parsing turns expensive.
    if (node == null || visitedNodes >= MAX_FILTER_NODES || result.size >= MAX_FILTER_MATCHES) {
      return
    }

    visitedNodes += 1

    when (node) {
      is JsonArray -> {
        for (item in node) {
          visit(item)
          if (visitedNodes >= MAX_FILTER_NODES || result.size >= MAX_FILTER_MATCHES) {
            return
          }
        }
      }
      is JsonObject -> {
        if (node[key]?.stringValue() == value) {
          result += node
        }

        for (child in node.values) {
          visit(child)
          if (visitedNodes >= MAX_FILTER_NODES || result.size >= MAX_FILTER_MATCHES) {
            return
          }
        }
      }
      else -> Unit
    }
  }

  visit(data)
  return result
}

private fun getTimelineInstructions(payload: JsonElement): List<JsonElement> {
  val root = payload.jsonObjectOrNull()
  val candidates = listOfNotNull(
    root?.getObject("data")?.getObject("home")?.getObject("home_timeline_urt"),
    root?.getObject("home")?.getObject("home_timeline_urt"),
    root?.getObject("home_timeline_urt"),
    root,
  )

  for (candidate in candidates) {
    val instructions = candidate["instructions"].jsonArrayOrEmpty()
    if (instructions.isNotEmpty()) {
      return instructions
    }
  }

  return emptyList()
}

private fun getInstructionEntries(instruction: JsonObject): List<JsonObject> {
  val entries = instruction["entries"].jsonArrayOrEmpty().mapNotNull { it.jsonObjectOrNull() }.toMutableList()
  instruction["entry"].jsonObjectOrNull()?.let(entries::add)
  return entries
}

private fun extractBottomCursorFromNode(node: JsonElement?): String {
  val record = node.jsonObjectOrNull() ?: return ""
  val cursorType = record["cursorType"].stringValue()
  val value = record["value"].stringValue()
  if (cursorType == "Bottom" && value.isNotBlank()) {
    return value
  }

  val nestedContent = extractBottomCursorFromNode(record["content"])
  if (nestedContent.isNotBlank()) {
    return nestedContent
  }

  return extractBottomCursorFromNode(record["itemContent"])
}

private fun extractNextCursor(payload: JsonElement): String? {
  val instructions = getTimelineInstructions(payload)

  // The newest bottom cursor usually appears near the end of the instruction list.
  for (instructionIndex in instructions.indices.reversed()) {
    val instruction = instructions[instructionIndex].jsonObjectOrNull() ?: continue
    val entries = getInstructionEntries(instruction)
    for (entryIndex in entries.indices.reversed()) {
      val cursor = extractBottomCursorFromNode(entries[entryIndex])
      if (cursor.isNotBlank()) {
        return cursor
      }
    }
  }

  return findByFilter(payload, "cursorType", "Bottom").firstOrNull()?.get("value")?.stringValue()?.takeIf { it.isNotBlank() }
}

private fun extractTweet(timelineTweetNode: JsonObject): XTimelineItem? {
  val tweetResults = timelineTweetNode.getObject("tweet_results")
  // X wraps tweets in a few result shapes, so normalize that before reading legacy fields.
  val result = unwrapTweetResult(tweetResults?.getObject("result")) ?: return null
  val legacy = result.getObject("legacy") ?: return null
  val id = result["rest_id"].stringValue()
  if (id.isBlank()) {
    return null
  }

  val noteTweetResult = result.getObject("note_tweet")
    ?.getObject("note_tweet_results")
    ?.getObject("result")

  val core = result.getObject("core")
  val userResult = core?.getObject("user_results")?.getObject("result")
  val userLegacy = userResult?.getObject("legacy")
  val userCore = userResult?.getObject("core")
  val authorHandle = userCore?.get("screen_name").stringValue().ifBlank {
    userLegacy?.get("screen_name").stringValue()
  }

  val quotedResult = unwrapTweetResult(
    result.getObject("quoted_status_result")?.getObject("result"),
  )

  val retweetedResult = legacy.getObject("retweeted_status_result")?.getObject("result")
  val retweetedTweet = retweetedResult?.getObject("tweet")
  val isRetweet = retweetedResult?.get("rest_id").stringValue().isNotBlank() ||
    retweetedTweet?.get("rest_id").stringValue().isNotBlank()
  val isQuote = legacy["is_quote_status"].booleanValue() || quotedResult?.get("rest_id").stringValue().isNotBlank()

  val quotedLegacy = quotedResult?.getObject("legacy")
  val quotedNoteResult = quotedResult?.getObject("note_tweet")
    ?.getObject("note_tweet_results")
    ?.getObject("result")
  val quotedUserResult = quotedResult?.getObject("core")
    ?.getObject("user_results")
    ?.getObject("result")
  val quotedUserLegacy = quotedUserResult?.getObject("legacy")
  val quotedUserCore = quotedUserResult?.getObject("core")

  val quotedAuthorHandle = quotedUserCore?.get("screen_name").stringValue().ifBlank {
    quotedUserLegacy?.get("screen_name").stringValue()
  }

  return XTimelineItem(
    id = id,
    text = noteTweetResult?.get("text").stringValue().ifBlank { legacy["full_text"].stringValue() },
    createdAt = toIsoDate(legacy["created_at"].stringValue()),
    authorName = userCore?.get("name").stringValue().ifBlank { userLegacy?.get("name").stringValue() },
    authorHandle = authorHandle,
    lang = legacy["lang"].stringValue(),
    replyTo = legacy["in_reply_to_status_id_str"].stringValue(),
    quoteCount = legacy["quote_count"].intValue(),
    replyCount = legacy["reply_count"].intValue(),
    retweetCount = legacy["retweet_count"].intValue(),
    likeCount = legacy["favorite_count"].intValue(),
    viewCount = result.getObject("views")?.get("count")?.intValueOrNull(),
    isRetweet = isRetweet,
    isQuote = isQuote,
    quotedText = quotedNoteResult?.get("text").stringValue().ifBlank { quotedLegacy?.get("full_text").stringValue() },
    quotedLang = quotedLegacy?.get("lang").stringValue(),
    quotedAuthorHandle = quotedAuthorHandle,
    quotedMedia = extractMedia(quotedLegacy),
    url = if (authorHandle.isBlank()) "" else "https://x.com/$authorHandle/status/$id",
    media = extractMedia(legacy),
  )
}

private fun extractMedia(legacy: JsonObject?): List<XTimelineMedia> {
  val mediaEntries = legacy?.getObject("extended_entities")?.get("media").jsonArrayOrEmpty()

  return mediaEntries.mapNotNull { entry ->
    val media = entry.jsonObjectOrNull() ?: return@mapNotNull null
    val type = mediaTypeFromRaw(media["type"].stringValue())
    val mediaUrl = extractBestMediaUrl(media)
    val expandedUrl = media["expanded_url"].stringValue()

    if (mediaUrl.first.isBlank() && expandedUrl.isBlank()) {
      return@mapNotNull null
    }

    XTimelineMedia(
      type = type,
      url = mediaUrl.first,
      expandedUrl = expandedUrl,
      thumbnailUrl = mediaUrl.second,
    )
  }
}

private fun extractBestMediaUrl(media: JsonObject): Pair<String, String?> {
  return when (mediaTypeFromRaw(media["type"].stringValue())) {
    XTimelineMediaType.PHOTO -> media["media_url_https"].stringValue() to null
    else -> {
      val variants = media.getObject("video_info")?.get("variants").jsonArrayOrEmpty()
      var selectedUrl = ""
      var selectedBitrate = -1

      for (variantElement in variants) {
        val variant = variantElement.jsonObjectOrNull() ?: continue
        val contentType = variant["content_type"].stringValue()
        val url = variant["url"].stringValue()
        val bitrate = variant["bitrate"].intValue()

        if (url.isBlank()) {
          continue
        }

        if (contentType.contains("mp4") && bitrate >= selectedBitrate) {
          selectedUrl = url
          selectedBitrate = bitrate
          continue
        }

        if (selectedUrl.isBlank()) {
          selectedUrl = url
        }
      }

      selectedUrl to media["media_url_https"].stringValue().ifBlank { null }
    }
  }
}

private fun mediaTypeFromRaw(value: String): XTimelineMediaType {
  return when (value) {
    "photo" -> XTimelineMediaType.PHOTO
    "video" -> XTimelineMediaType.VIDEO
    "animated_gif" -> XTimelineMediaType.ANIMATED_GIF
    else -> XTimelineMediaType.UNKNOWN
  }
}

private fun unwrapTweetResult(value: JsonObject?): JsonObject? {
  if (value == null) {
    return null
  }

  return if (value["__typename"].stringValue() == "TweetWithVisibilityResults") {
    value.getObject("tweet")
  } else {
    value
  }
}

private fun toIsoDate(rawDate: String): String {
  if (rawDate.isBlank()) {
    return ""
  }

  return try {
    OffsetDateTime.parse(rawDate, xDateFormatter).toInstant().toString()
  } catch (_: Exception) {
    try {
      Instant.parse(rawDate).toString()
    } catch (_: Exception) {
      ""
    }
  }
}

private fun JsonElement?.jsonObjectOrNull(): JsonObject? = this as? JsonObject

private fun JsonElement?.jsonArrayOrEmpty(): JsonArray = this as? JsonArray ?: JsonArray(emptyList())

private fun JsonObject.getObject(key: String): JsonObject? = this[key].jsonObjectOrNull()

private fun JsonElement?.stringValue(): String {
  return (this as? JsonPrimitive)?.contentOrNull.orEmpty()
}

private fun JsonElement?.intValue(): Int {
  return (this as? JsonPrimitive)?.intOrNull ?: stringValue().toIntOrNull() ?: 0
}

private fun JsonElement?.intValueOrNull(): Int? {
  return (this as? JsonPrimitive)?.intOrNull ?: stringValue().toIntOrNull()
}

private fun JsonElement?.booleanValue(): Boolean {
  return (this as? JsonPrimitive)?.booleanOrNull ?: false
}
