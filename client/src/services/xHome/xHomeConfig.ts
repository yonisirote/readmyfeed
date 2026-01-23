type XHomeConfig = {
  baseUrl: string;
  timelinePath: string;
  defaultBearerToken: string;
  defaultHeaders: Record<string, string>;
  timelineFeatures: Record<string, boolean>;
};

const baseUrl = 'https://x.com';

// Centralized constants for building X home timeline requests.
export const X_HOME_CONFIG: XHomeConfig = {
  baseUrl,
  timelinePath: '/i/api/graphql/_qO7FJzShSKYWi9gtboE6A/HomeLatestTimeline',
  defaultBearerToken:
    'AAAAAAAAAAAAAAAAAAAAANRILgAAAAAAnNwIzUejRCOuH5E6I8xnZz4puTs%3D1Zv7ttfk8LF81IUq16cHjhLTvJu4FA33AGWWjCpTnA',
  defaultHeaders: {
    Authority: 'x.com',
    'Accept-Language': 'en-US,en;q=0.9',
    'Cache-Control': 'no-cache',
    Referer: baseUrl,
    'User-Agent':
      'Mozilla/5.0 (X11; Linux x86_64; rv:144.0) Gecko/20100101 Firefox/144.0',
    'X-Twitter-Active-User': 'yes',
    'X-Twitter-Client-Language': 'en',
  },
  timelineFeatures: {
    rweb_video_screen_enabled: false,
    profile_label_improvements_pcf_label_in_post_enabled: true,
    responsive_web_profile_redirect_enabled: false,
    rweb_tipjar_consumption_enabled: true,
    verified_phone_label_enabled: true,
    creator_subscriptions_tweet_preview_api_enabled: true,
    responsive_web_graphql_timeline_navigation_enabled: true,
    responsive_web_graphql_skip_user_profile_image_extensions_enabled: false,
    premium_content_api_read_enabled: false,
    communities_web_enable_tweet_community_results_fetch: true,
    c9s_tweet_anatomy_moderator_badge_enabled: true,
    responsive_web_grok_analyze_button_fetch_trends_enabled: false,
    responsive_web_grok_analyze_post_followups_enabled: true,
    responsive_web_jetfuel_frame: true,
    responsive_web_grok_share_attachment_enabled: true,
    articles_preview_enabled: true,
    responsive_web_edit_tweet_api_enabled: true,
    graphql_is_translatable_rweb_tweet_is_translatable_enabled: true,
    view_counts_everywhere_api_enabled: true,
    longform_notetweets_consumption_enabled: true,
    responsive_web_twitter_article_tweet_consumption_enabled: true,
    tweet_awards_web_tipping_enabled: false,
    responsive_web_grok_show_grok_translated_post: false,
    responsive_web_grok_analysis_button_from_backend: true,
    creator_subscriptions_quote_tweet_preview_enabled: false,
    freedom_of_speech_not_reach_fetch_enabled: true,
    standardized_nudges_misinfo: true,
    tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled: true,
    longform_notetweets_rich_text_read_enabled: true,
    longform_notetweets_inline_media_enabled: true,
    responsive_web_grok_image_annotation_enabled: true,
    responsive_web_grok_imagine_annotation_enabled: true,
    responsive_web_grok_community_note_auto_translation_is_enabled: false,
    responsive_web_enhance_cards_enabled: false,
  },
};
