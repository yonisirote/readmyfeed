import ClientTransaction from 'x-client-transaction-id';

import { parseTimeline, toTweetSample } from './xTimelineParsing';
import type { XHomeTimelineOptions, XHomeTimelineResult } from './xHomeTypes';

export type { XHomeTimelineTweetSample } from './xHomeTypes';

type ParsedCookies = {
  rawCookieHeader: string;
  csrfToken?: string;
  hasAuthToken: boolean;
  hasKdt: boolean;
  hasTwid: boolean;
};

const X_TIMELINE_PATH = '/i/api/graphql/_qO7FJzShSKYWi9gtboE6A/HomeLatestTimeline';

const DEFAULT_BEARER_TOKEN =
  'AAAAAAAAAAAAAAAAAAAAANRILgAAAAAAnNwIzUejRCOuH5E6I8xnZz4puTs%3D1Zv7ttfk8LF81IUq16cHjhLTvJu4FA33AGWWjCpTnA';

const DEFAULT_HEADERS: Record<string, string> = {
  Authority: 'x.com',
  'Accept-Language': 'en-US,en;q=0.9',
  'Cache-Control': 'no-cache',
  Referer: 'https://x.com',
  'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64; rv:144.0) Gecko/20100101 Firefox/144.0',
  'X-Twitter-Active-User': 'yes',
  'X-Twitter-Client-Language': 'en',
};

const safeBodyPrefix = (text: string, maxLen = 800): string => {
  const cleaned = text.replaceAll(/\s+/g, ' ').trim();
  return cleaned.slice(0, maxLen);
};

const decodeApiKeyToCookies = (key: string): ParsedCookies => {
  const decoded = atob(key.trim());
  const cookiePairs = decoded.split(';');
  const cookieMap = new Map<string, string>();

  for (const pair of cookiePairs) {
    const trimmed = pair.trim();
    if (!trimmed) continue;
    const [name, ...rest] = trimmed.split('=');
    if (!name || rest.length === 0) continue;
    cookieMap.set(name, rest.join('='));
  }

  const authToken = cookieMap.get('auth_token');
  const csrfToken = cookieMap.get('ct0');
  const kdt = cookieMap.get('kdt');
  const twid = cookieMap.get('twid');

  const parts: string[] = [];
  if (authToken) parts.push(`auth_token=${authToken}`);
  if (csrfToken) parts.push(`ct0=${csrfToken}`);
  if (kdt) parts.push(`kdt=${kdt}`);
  if (twid) parts.push(`twid=${twid}`);

  const rawCookieHeader = parts.length ? `${parts.join(';')};` : decoded;

  return {
    rawCookieHeader,
    csrfToken,
    hasAuthToken: Boolean(authToken),
    hasKdt: Boolean(kdt),
    hasTwid: Boolean(twid),
  };
};


type FetchTextResult = {
  status: number;
  text: string;
};

const isReactNative = typeof navigator !== 'undefined' && navigator.product === 'ReactNative';

const fetchText = async (
  url: string,
  headers: Record<string, string>,
  timeoutMs: number,
  log?: (message: string) => void,
): Promise<FetchTextResult> => {
  try {
    if (isReactNative) {
      const FileSystem = await import('expo-file-system');
      const baseDir = FileSystem.cacheDirectory ?? FileSystem.documentDirectory;
      if (!baseDir) {
        throw new Error('Missing Expo file system directory.');
      }

      const tempUri = `${baseDir}rmf-${Date.now()}-${Math.random().toString(16).slice(2)}.txt`;
      let status = 0;
      let text = '';

      try {
        const res = await FileSystem.downloadAsync(url, tempUri, { headers });
        status = res.status ?? 0;
        text = await FileSystem.readAsStringAsync(res.uri);
      } finally {
        await FileSystem.deleteAsync(tempUri, { idempotent: true });
      }

      if (status >= 400) {
        const body = safeBodyPrefix(text);
        log?.(`fetch failed status=${status} url=${url} body=${body}`);
      }

      return { status, text };
    }

    const { default: axios } = await import('axios');
    const response = await axios.get(url, {
      headers,
      timeout: timeoutMs,
      responseType: 'text',
      transformResponse: (data) => data,
      validateStatus: () => true,
    });

    const text = typeof response.data === 'string' ? response.data : JSON.stringify(response.data ?? {});
    if (response.status >= 400) {
      const body = safeBodyPrefix(text);
      log?.(`fetch failed status=${response.status} url=${url} body=${body}`);
    }

    return { status: response.status, text };
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    throw new Error(`Request failed url=${url} error=${message}`);
  }
};


const buildGraphqlUrl = (count: number, cursor?: string): string => {
  const url = new URL(`https://x.com${X_TIMELINE_PATH}`);

  const variables: Record<string, unknown> = {
    count,
    includePromotedContent: false,
    latestControlAvailable: true,
    withCommunity: false,
  };
  if (cursor) variables.cursor = cursor;

  const features: Record<string, boolean> = {
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
  };

  url.searchParams.set('variables', JSON.stringify(variables));
  url.searchParams.set('features', JSON.stringify(features));

  return url.toString();
};

export const fetchXHomeTimeline = async (options: XHomeTimelineOptions): Promise<XHomeTimelineResult> => {
  const apiKey = options.apiKey.trim();
  const count = options.count;
  const cursor = options.cursor;
  const log = options.log;

  if (!apiKey) {
    throw new Error('Missing API key.');
  }

  if (typeof DOMParser !== 'function') {
    throw new Error('DOMParser is not available. Ensure polyfills are loaded.');
  }

  const { rawCookieHeader, csrfToken, hasAuthToken, hasKdt, hasTwid } = decodeApiKeyToCookies(apiKey);

  log?.(`cookie parts auth=${hasAuthToken} kdt=${hasKdt} twid=${hasTwid} csrf=${Boolean(csrfToken)}`);

  log?.('requesting x.com HTML');
  const docRes = await fetchText(
    'https://x.com',
    {
      ...DEFAULT_HEADERS,
      Cookie: rawCookieHeader,
    },
    15000,
    log,
  );

  if (docRes.status !== 200) {
    throw new Error(`Failed to fetch x.com HTML (status=${docRes.status}).`);
  }

  const docHtml = docRes.text;
  if (!docHtml) {
    throw new Error('Failed to fetch x.com HTML (empty response).');
  }

  const parser = new DOMParser();
  const doc = parser.parseFromString(docHtml, 'text/html');

  const bearer = DEFAULT_BEARER_TOKEN;

  log?.('generating x-client-transaction-id');
  const tx = await ClientTransaction.create(doc);
  const transactionId = await tx.generateTransactionId('GET', X_TIMELINE_PATH);

  const url = buildGraphqlUrl(count, cursor);
  log?.(`requesting timeline ${cursor ? 'cursor=set' : 'cursor=none'}`);

  const timelineRes = await fetchText(
    url,
    {
      ...DEFAULT_HEADERS,
      ...(csrfToken ? { 'x-csrf-token': csrfToken } : {}),
      'x-client-transaction-id': transactionId,
      authorization: `Bearer ${bearer}`,
      Cookie: rawCookieHeader,
    },
    15000,
    log,
  );

  const bodyText = timelineRes.text;
  if (timelineRes.status !== 200) {
    throw new Error(`Timeline request failed status=${timelineRes.status} bodyPrefix=${safeBodyPrefix(bodyText)}`);
  }

  let data: unknown;
  try {
    data = JSON.parse(bodyText);
  } catch (error) {
    const msg = error instanceof Error ? error.message : String(error);
    throw new Error(`Failed to parse timeline JSON: ${msg}`);
  }

  const { entries, nextCursor, tweetEntries } = parseTimeline(data);
  const tweetSamples = tweetEntries
    .slice(0, count)
    .map((e) => e?.content?.itemContent?.tweet_results?.result)
    .filter(Boolean)
    .map(toTweetSample)
    .filter((t) => Boolean(t.id));

  return {
    entriesCount: entries.length,
    tweetsCount: tweetEntries.length,
    nextCursor,
    tweetSamples,
  };
};
