import { XAuthService } from '../auth/xAuthService';
import { createXAuthLogger, XAuthLogger } from '../auth/xAuthLogger';
import { decodeCookieString } from '../auth/xAuthUtils';

import { parseXFollowingTimelineResponse } from './xTimelineParser';
import { X_TIMELINE_ERROR_CODES, XTimelineError } from './xTimelineErrors';
import { XFollowingTimelineBatch } from './xTimelineTypes';

const FOLLOWING_TIMELINE_URL =
  'https://x.com/i/api/graphql/_qO7FJzShSKYWi9gtboE6A/HomeLatestTimeline';

const X_WEB_AUTH_BEARER_TOKEN =
  'AAAAAAAAAAAAAAAAAAAAANRILgAAAAAAnNwIzUejRCOuH5E6I8xnZz4puTs%3D1Zv7ttfk8LF81IUq16cHjhLTvJu4FA33AGWWjCpTnA';

const DEFAULT_BATCH_SIZE = 40;

const FOLLOWING_FEATURES = {
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
} as const;

export type XFollowingTimelineRequest = {
  count?: number;
  cursor?: string;
  cookieString?: string;
};

export type XTimelineServiceOptions = {
  logger?: XAuthLogger;
  authService?: XAuthService;
  fetchImpl?: typeof fetch;
};

const parseCookieString = (cookieString: string): Record<string, string> => {
  const cookies: Record<string, string> = {};

  for (const segment of cookieString.split(';')) {
    const trimmed = segment.trim();
    if (!trimmed) {
      continue;
    }

    const separator = trimmed.indexOf('=');
    if (separator <= 0) {
      continue;
    }

    const name = trimmed.slice(0, separator).trim();
    const value = trimmed.slice(separator + 1).trim();

    if (name && value) {
      cookies[name] = value;
    }
  }

  return cookies;
};

const buildFollowingTimelineUrl = ({
  count,
  cursor,
}: {
  count?: number;
  cursor?: string;
}): string => {
  const variables: Record<string, unknown> = {
    count: count,
    includePromotedContent: false,
    latestControlAvailable: true,
    withCommunity: false,
  };

  if (cursor) {
    variables.cursor = cursor;
  }

  const params = new URLSearchParams();
  params.set('variables', JSON.stringify(variables));
  params.set('features', JSON.stringify(FOLLOWING_FEATURES));

  return `${FOLLOWING_TIMELINE_URL}?${params.toString()}`;
};

const buildHeaders = ({
  cookieString,
  csrfToken,
}: {
  cookieString: string;
  csrfToken: string;
}): Record<string, string> => {
  return {
    accept: '*/*',
    authorization: `Bearer ${X_WEB_AUTH_BEARER_TOKEN}`,
    cookie: cookieString,
    referer: 'https://x.com/home',
    'x-csrf-token': csrfToken,
    'x-twitter-active-user': 'yes',
    'x-twitter-auth-type': 'OAuth2Session',
    'x-twitter-client-language': 'en',
  };
};

const summarizeBody = (body: string): string => {
  const trimmed = body.replace(/\s+/g, ' ').trim();
  if (trimmed.length <= 240) {
    return trimmed;
  }

  return `${trimmed.slice(0, 240)}...`;
};

export class XTimelineService {
  private readonly logger: XAuthLogger;
  private readonly authService: XAuthService;
  private readonly fetchImpl: typeof fetch;

  public constructor(options: XTimelineServiceOptions = {}) {
    this.logger = options.logger ?? createXAuthLogger();
    this.authService = options.authService ?? new XAuthService({ logger: this.logger });
    this.fetchImpl = options.fetchImpl ?? fetch;
  }

  private async resolveCookieString(cookieString?: string): Promise<string> {
    if (cookieString) {
      return cookieString;
    }

    const encodedCookie = await this.authService.loadStoredSession();
    if (!encodedCookie) {
      throw new XTimelineError(
        'No X session found. Please connect your account again.',
        X_TIMELINE_ERROR_CODES.SessionMissing,
      );
    }

    try {
      return decodeCookieString(encodedCookie);
    } catch (err) {
      throw new XTimelineError(
        'Stored X session is invalid. Please reconnect your account.',
        X_TIMELINE_ERROR_CODES.CookieInvalid,
        {
          cause: err instanceof Error ? err.message : String(err),
        },
      );
    }
  }

  public async fetchFollowingTimeline(
    request: XFollowingTimelineRequest = {},
  ): Promise<XFollowingTimelineBatch> {
    const cookieString = await this.resolveCookieString(request.cookieString);
    const cookieMap = parseCookieString(cookieString);
    const csrfToken = cookieMap.ct0;

    if (!csrfToken) {
      throw new XTimelineError(
        'Missing CSRF cookie for X session. Please reconnect your account.',
        X_TIMELINE_ERROR_CODES.CookieInvalid,
      );
    }

    const url = buildFollowingTimelineUrl({
      count: request.count ?? DEFAULT_BATCH_SIZE,
      cursor: request.cursor,
    });

    this.logger.info('Fetching X following timeline (native request)', {
      hasCursor: Boolean(request.cursor),
      count: request.count ?? DEFAULT_BATCH_SIZE,
    });

    const response = await this.fetchImpl(url, {
      method: 'GET',
      headers: buildHeaders({ cookieString, csrfToken }),
    });

    const responseBody = await response.text();

    if (!response.ok) {
      throw new XTimelineError(
        `Failed to fetch following timeline (status ${response.status})`,
        X_TIMELINE_ERROR_CODES.RequestFailed,
        {
          status: response.status,
          body: summarizeBody(responseBody),
        },
      );
    }

    let payload: unknown;
    try {
      payload = JSON.parse(responseBody);
    } catch (err) {
      throw new XTimelineError(
        'Timeline response was not valid JSON.',
        X_TIMELINE_ERROR_CODES.ResponseInvalid,
        {
          cause: err instanceof Error ? err.message : String(err),
          body: summarizeBody(responseBody),
        },
      );
    }

    const parsed = parseXFollowingTimelineResponse(payload);

    this.logger.info('Fetched X following timeline batch', {
      count: parsed.items.length,
      hasNextCursor: Boolean(parsed.nextCursor),
    });

    return parsed;
  }
}
