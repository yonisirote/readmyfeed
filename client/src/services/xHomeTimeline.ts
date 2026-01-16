import { Http } from '@capacitor-community/http';
import ClientTransaction from 'x-client-transaction-id';

export type XHomeTimelineOptions = {
  apiKey: string;
  count: number;
  cursor?: string;
  log?: (message: string) => void;
};

export type XHomeTimelineTweetSample = {
  id: string;
  user?: string;
  text?: string;
};

export type XHomeTimelineResult = {
  entriesCount: number;
  tweetsCount: number;
  nextCursor?: string;
  tweetSamples: XHomeTimelineTweetSample[];
};

type ParsedCookies = {
  rawCookieHeader: string;
  csrfToken?: string;
};

const X_TIMELINE_PATH = '/i/api/graphql/CRprHpVA12yhsub-KRERIg/HomeLatestTimeline';

const safeBodyPrefix = (text: string, maxLen = 800): string => {
  const cleaned = text.replaceAll(/\s+/g, ' ').trim();
  return cleaned.slice(0, maxLen);
};

const decodeApiKeyToCookies = (key: string): ParsedCookies => {
  const decoded = atob(key.trim());
  const rawCookieHeader = decoded;

  const match = rawCookieHeader.match(/(?:^|;\s*)ct0=([^;]+)/);
  const csrfToken = match?.[1];

  return { rawCookieHeader, csrfToken };
};

const extractBearerFromText = (text: string): string | undefined => {
  // X web client includes an Authorization Bearer token in its JS bundles.
  // Token format tends to be URL-encoded at least for '='.
  const match = text.match(/Bearer\s+([A-Za-z0-9%._~-]+)/);
  return match?.[1];
};

const resolveXUrl = (maybeRelativeUrl: string): string => {
  if (maybeRelativeUrl.startsWith('http://') || maybeRelativeUrl.startsWith('https://')) {
    return maybeRelativeUrl;
  }
  if (maybeRelativeUrl.startsWith('/')) {
    return `https://x.com${maybeRelativeUrl}`;
  }
  return `https://x.com/${maybeRelativeUrl}`;
};

const extractXScriptUrls = (html: string): string[] => {
  const doc = new DOMParser().parseFromString(html, 'text/html');

  const urls = new Set<string>();

  const scriptEls = Array.from(doc.querySelectorAll('script[src]'));
  for (const el of scriptEls) {
    const src = el.getAttribute('src');
    if (!src) continue;
    urls.add(resolveXUrl(src));
  }

  for (const match of html.matchAll(/\b(?:src|href)=(["'])([^"']+\.(?:js|mjs))\1/g)) {
    urls.add(resolveXUrl(match[2] ?? ''));
  }

  return [...urls].filter((u) => u.includes('/assets/') || u.includes('twimg.com'));
};

const fetchBearerTokenFromX = async (rawCookieHeader: string, xHtml: string, log?: (m: string) => void) => {
  const candidates = extractXScriptUrls(xHtml).slice(0, 6);
  log?.(`bearer candidate scripts=${candidates.length}`);

  for (const scriptUrl of candidates) {
    try {
      log?.(`bearer scanning ${scriptUrl}`);
      const res = await Http.get({
        url: scriptUrl,
        headers: {
          Cookie: rawCookieHeader,
        },
        params: {},
        connectTimeout: 15000,
        readTimeout: 15000,
      });

      const jsText = typeof res.data === 'string' ? res.data : '';
      if (!jsText) continue;

      const bearer = extractBearerFromText(jsText);
      if (bearer) {
        log?.(`bearer extracted len=${bearer.length}`);
        return bearer;
      }
    } catch (error) {
      const msg = error instanceof Error ? error.message : String(error);
      log?.(`bearer scan failed: ${msg}`);
    }
  }

  return undefined;
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
    responsive_web_graphql_timeline_navigation_enabled: true,
    rweb_video_screen_enabled: false,
    view_counts_everywhere_api_enabled: true,
    longform_notetweets_consumption_enabled: true,
    responsive_web_jetfuel_frame: false,
    graphql_is_translatable_rweb_tweet_is_translatable_enabled: false,
    tweet_awards_web_tipping_enabled: false,
    longform_notetweets_rich_text_read_enabled: true,
    c9s_tweet_anatomy_moderator_badge_enabled: true,
    premium_content_api_read_enabled: false,
    responsive_web_grok_share_attachment_enabled: false,
    verified_phone_label_enabled: false,
    responsive_web_grok_analysis_button_from_backend: false,
    responsive_web_edit_tweet_api_enabled: false,
    responsive_web_graphql_skip_user_profile_image_extensions_enabled: false,
    profile_label_improvements_pcf_label_in_post_enabled: true,
    articles_preview_enabled: true,
    creator_subscriptions_quote_tweet_preview_enabled: false,
    responsive_web_grok_show_grok_translated_post: false,
    responsive_web_grok_analyze_post_followups_enabled: false,
    longform_notetweets_inline_media_enabled: true,
    communities_web_enable_tweet_community_results_fetch: true,
    creator_subscriptions_tweet_preview_api_enabled: false,
    freedom_of_speech_not_reach_fetch_enabled: false,
    responsive_web_grok_image_annotation_enabled: false,
    responsive_web_enhance_cards_enabled: false,
    tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled: false,
    responsive_web_grok_analyze_button_fetch_trends_enabled: false,
    responsive_web_twitter_article_tweet_consumption_enabled: false,
    standardized_nudges_misinfo: false,
    rweb_tipjar_consumption_enabled: false,
  };

  url.searchParams.set('variables', JSON.stringify(variables));
  url.searchParams.set('features', JSON.stringify(features));

  return url.toString();
};

const parseTimeline = (data: unknown): { entries: any[]; nextCursor?: string; tweetEntries: any[] } => {
  if (!data || typeof data !== 'object') {
    return { entries: [], tweetEntries: [] };
  }

  const instructions: any[] =
    (data as any)?.data?.home?.home_timeline_urt?.instructions ?? (data as any)?.data?.home?.timeline?.instructions ?? [];

  const entries: any[] = [];
  for (const inst of instructions) {
    if (inst?.type === 'TimelineAddEntries' && Array.isArray(inst.entries)) {
      entries.push(...inst.entries);
    }
  }

  const cursorEntry = entries.find((e) => String(e?.entryId ?? '').startsWith('cursor-bottom'));
  const next = cursorEntry?.content?.value ?? cursorEntry?.content?.itemContent?.value;

  const tweetEntries = entries.filter((e) => e?.content?.itemContent?.tweet_results);
  return { entries, nextCursor: typeof next === 'string' ? next : undefined, tweetEntries };
};

const toTweetSample = (tweetResult: any): XHomeTimelineTweetSample => {
  const id = String(tweetResult?.rest_id ?? '');
  const user = tweetResult?.core?.user_results?.result?.legacy?.screen_name;
  const text = tweetResult?.legacy?.full_text ?? tweetResult?.legacy?.text;

  return {
    id,
    user: typeof user === 'string' ? user : undefined,
    text: typeof text === 'string' ? safeBodyPrefix(text, 160) : undefined,
  };
};

export const fetchXHomeTimeline = async (options: XHomeTimelineOptions): Promise<XHomeTimelineResult> => {
  const apiKey = options.apiKey.trim();
  const count = options.count;
  const cursor = options.cursor;
  const log = options.log;

  if (!apiKey) {
    throw new Error('Missing API key.');
  }

  const { rawCookieHeader, csrfToken } = decodeApiKeyToCookies(apiKey);

  log?.('requesting x.com HTML');
  const docRes = await Http.get({
    url: 'https://x.com',
    headers: {
      Cookie: rawCookieHeader,
    },
    params: {},
    connectTimeout: 15000,
    readTimeout: 15000,
  });

  const docHtml = typeof docRes.data === 'string' ? docRes.data : '';
  if (!docHtml) {
    throw new Error('Failed to fetch x.com HTML (empty response).');
  }

  const doc = new DOMParser().parseFromString(docHtml, 'text/html');

  log?.('generating x-client-transaction-id');
  const tx = await ClientTransaction.create(doc);
  const transactionId = await tx.generateTransactionId('GET', X_TIMELINE_PATH);

  log?.('extracting bearer token');
  const bearer = await fetchBearerTokenFromX(rawCookieHeader, docHtml, log);
  if (!bearer) {
    throw new Error('Failed to extract Bearer token from X assets.');
  }

  const url = buildGraphqlUrl(count, cursor);
  log?.(`requesting timeline ${cursor ? 'cursor=set' : 'cursor=none'}`);

  const res = await Http.get({
    url,
    headers: {
      ...(csrfToken ? { 'x-csrf-token': csrfToken } : {}),
      'x-client-transaction-id': transactionId,
      'x-twitter-active-user': 'yes',
      'x-twitter-client-language': 'en',
      authorization: `Bearer ${bearer}`,
      referer: 'https://x.com/',
      Cookie: rawCookieHeader,
    },
    params: {},
    connectTimeout: 15000,
    readTimeout: 15000,
  });

  if (res.status !== 200) {
    const body = typeof res.data === 'string' ? res.data : JSON.stringify(res.data ?? '');
    throw new Error(`Timeline request failed status=${res.status} bodyPrefix=${safeBodyPrefix(body)}`);
  }

  const { entries, nextCursor, tweetEntries } = parseTimeline(res.data);
  const tweetSamples = tweetEntries
    .slice(0, 5)
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
