package com.yonisirote.readmyfeed.providers.x.timeline

import com.yonisirote.readmyfeed.providers.x.auth.parseCookieHeader
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val FOLLOWING_TIMELINE_URL =
  "https://x.com/i/api/graphql/_qO7FJzShSKYWi9gtboE6A/HomeLatestTimeline"

// This is the web client's bearer token, not a per-user secret.
private const val X_WEB_AUTH_BEARER_TOKEN =
  "AAAAAAAAAAAAAAAAAAAAANRILgAAAAAAnNwIzUejRCOuH5E6I8xnZz4puTs%3D1Zv7ttfk8LF81IUq16cHjhLTvJu4FA33AGWWjCpTnA"

private const val DEFAULT_BATCH_SIZE = 40

private val FOLLOWING_FEATURES_JSON = """
  {
    "rweb_video_screen_enabled":false,
    "profile_label_improvements_pcf_label_in_post_enabled":true,
    "responsive_web_profile_redirect_enabled":false,
    "rweb_tipjar_consumption_enabled":true,
    "verified_phone_label_enabled":true,
    "creator_subscriptions_tweet_preview_api_enabled":true,
    "responsive_web_graphql_timeline_navigation_enabled":true,
    "responsive_web_graphql_skip_user_profile_image_extensions_enabled":false,
    "premium_content_api_read_enabled":false,
    "communities_web_enable_tweet_community_results_fetch":true,
    "c9s_tweet_anatomy_moderator_badge_enabled":true,
    "responsive_web_grok_analyze_button_fetch_trends_enabled":false,
    "responsive_web_grok_analyze_post_followups_enabled":true,
    "responsive_web_jetfuel_frame":true,
    "responsive_web_grok_share_attachment_enabled":true,
    "articles_preview_enabled":true,
    "responsive_web_edit_tweet_api_enabled":true,
    "graphql_is_translatable_rweb_tweet_is_translatable_enabled":true,
    "view_counts_everywhere_api_enabled":true,
    "longform_notetweets_consumption_enabled":true,
    "responsive_web_twitter_article_tweet_consumption_enabled":true,
    "tweet_awards_web_tipping_enabled":false,
    "responsive_web_grok_show_grok_translated_post":false,
    "responsive_web_grok_analysis_button_from_backend":true,
    "creator_subscriptions_quote_tweet_preview_enabled":false,
    "freedom_of_speech_not_reach_fetch_enabled":true,
    "standardized_nudges_misinfo":true,
    "tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled":true,
    "longform_notetweets_rich_text_read_enabled":true,
    "longform_notetweets_inline_media_enabled":true,
    "responsive_web_grok_image_annotation_enabled":true,
    "responsive_web_grok_imagine_annotation_enabled":true,
    "responsive_web_grok_community_note_auto_translation_is_enabled":false,
    "responsive_web_enhance_cards_enabled":false
  }
""".trimIndent()

data class XFollowingTimelineRequest(
  val count: Int = DEFAULT_BATCH_SIZE,
  val cursor: String? = null,
  val cookieString: String? = null,
)

data class XTimelineHttpRequest(
  val url: String,
  val headers: Map<String, String>,
)

fun buildFollowingTimelineUrl(count: Int = DEFAULT_BATCH_SIZE, cursor: String? = null): String {
  val variablesJson = buildJsonObject {
    put("count", count)
    put("includePromotedContent", false)
    put("latestControlAvailable", true)
    put("withCommunity", false)
    if (!cursor.isNullOrBlank()) {
      put("cursor", cursor)
    }
  }.toString()

  return FOLLOWING_TIMELINE_URL.toHttpUrl().newBuilder()
    .addQueryParameter("variables", variablesJson)
    .addQueryParameter("features", FOLLOWING_FEATURES_JSON)
    .build()
    .toString()
}

fun buildTimelineHeaders(cookieString: String, csrfToken: String): Map<String, String> {
  return linkedMapOf(
    "accept" to "*/*",
    "authorization" to "Bearer $X_WEB_AUTH_BEARER_TOKEN",
    "cookie" to cookieString,
    "referer" to "https://x.com/home",
    "x-csrf-token" to csrfToken,
    "x-twitter-active-user" to "yes",
    "x-twitter-auth-type" to "OAuth2Session",
    "x-twitter-client-language" to "en",
  )
}

fun buildTimelineHttpRequest(request: XFollowingTimelineRequest, storedCookieString: String): XTimelineHttpRequest {
  val cookieString = request.cookieString ?: storedCookieString
  // X expects the ct0 cookie to be echoed back as the CSRF header on API requests.
  val csrfToken = parseCookieHeader(cookieString)["ct0"].orEmpty()

  if (csrfToken.isBlank()) {
    throw XTimelineException(
      message = "Missing CSRF cookie for X session. Please reconnect your account.",
      code = XTimelineErrorCodes.COOKIE_INVALID,
    )
  }

  return XTimelineHttpRequest(
    url = buildFollowingTimelineUrl(request.count, request.cursor),
    headers = buildTimelineHeaders(cookieString, csrfToken),
  )
}
