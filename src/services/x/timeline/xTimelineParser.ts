import {
  XFollowingTimelineBatch,
  XTimelineItem,
  XTimelineMedia,
  XTimelineMediaType,
} from './xTimelineTypes';

// Helpers below (isRecord, asRecord, asString, asNumber, toIsoDate) are new — Rettiwt-API
// uses TypeScript interfaces/casts on the raw JSON instead. These exist because this codebase
// treats the API response as `unknown` and validates at runtime rather than casting.

const isRecord = (value: unknown): value is Record<string, unknown> => {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
};

const asRecord = (value: unknown): Record<string, unknown> | null => {
  return isRecord(value) ? value : null;
};

const asString = (value: unknown): string => {
  return typeof value === 'string' ? value : '';
};

const asNumber = (value: unknown): number => {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }

  if (typeof value === 'string') {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }

  return 0;
};

// Rettiwt-API does `new Date(tweet.legacy.created_at).toISOString()` inline in the Tweet
// constructor. This is the same logic extracted into a safe helper with NaN-checking.
const toIsoDate = (rawDate: unknown): string => {
  if (typeof rawDate !== 'string') {
    return '';
  }

  const parsed = new Date(rawDate);
  if (Number.isNaN(parsed.valueOf())) {
    return '';
  }

  return parsed.toISOString();
};

// Taken from Rettiwt-API's findByFilter (src/helper/JsonUtils.ts).
// Same recursive deep-search logic. Changed: accepts `unknown` instead of `NonNullable<unknown>`
// and uses isRecord guard instead of raw typeof checks for stricter runtime safety.
// Added: soft node/collection limits to prevent pathological deep scans on mobile.
const MAX_FILTER_MATCHES = 1000;
const MAX_FILTER_NODES = 15000;

const findByFilter = <T>(data: unknown, key: string, value: string): T[] => {
  const result: T[] = [];
  let visitedNodes = 0;

  const visit = (node: unknown) => {
    if (visitedNodes >= MAX_FILTER_NODES || result.length >= MAX_FILTER_MATCHES) {
      return;
    }

    visitedNodes += 1;

    if (Array.isArray(node)) {
      for (const item of node) {
        visit(item);
        if (visitedNodes >= MAX_FILTER_NODES || result.length >= MAX_FILTER_MATCHES) {
          return;
        }
      }
      return;
    }

    if (!isRecord(node)) {
      return;
    }

    if (Object.prototype.hasOwnProperty.call(node, key) && node[key] === value) {
      result.push(node as unknown as T);
      if (result.length >= MAX_FILTER_MATCHES) {
        return;
      }
    }

    for (const valueOfKey of Object.values(node)) {
      visit(valueOfKey);
      if (visitedNodes >= MAX_FILTER_NODES || result.length >= MAX_FILTER_MATCHES) {
        return;
      }
    }
  };

  visit(data);
  return result;
};

// Maps raw X API media type strings to XTimelineMediaType.
// Rettiwt-API uses RawMediaType enum -> MediaType enum mapping in TweetMedia constructor.
// Changed: keeps the raw string values directly and adds 'unknown' fallback.
const mediaTypeFromRaw = (value: unknown): XTimelineMediaType => {
  if (value === 'photo' || value === 'video' || value === 'animated_gif') {
    return value;
  }

  return 'unknown';
};

// Based on Rettiwt-API's TweetMedia constructor (src/models/data/Tweet.ts TweetMedia).
// Same approach: photos use media_url_https, videos pick the highest-bitrate mp4 variant.
// Changed: also checks content_type for 'mp4' (Rettiwt-API only checks bitrate).
// GIFs are not special-cased (Rettiwt-API picks variants[0] for GIFs); instead they go
// through the same best-bitrate loop, which works because GIFs typically have one variant.
const extractBestMediaUrl = (
  media: Record<string, unknown>,
): { url: string; thumbnailUrl?: string } => {
  const type = mediaTypeFromRaw(media.type);

  if (type === 'photo') {
    return { url: asString(media.media_url_https) };
  }

  const videoInfo = asRecord(media.video_info);
  const variants = Array.isArray(videoInfo?.variants) ? videoInfo.variants : [];
  let selectedUrl = '';
  let selectedBitrate = -1;

  for (const variant of variants) {
    const record = asRecord(variant);
    if (!record) {
      continue;
    }

    const contentType = asString(record.content_type);
    const url = asString(record.url);
    const bitrate = asNumber(record.bitrate);

    if (!url) {
      continue;
    }

    if (contentType.includes('mp4') && bitrate >= selectedBitrate) {
      selectedUrl = url;
      selectedBitrate = bitrate;
      continue;
    }

    if (!selectedUrl) {
      selectedUrl = url;
    }
  }

  return {
    url: selectedUrl,
    thumbnailUrl: asString(media.media_url_https) || undefined,
  };
};

// Based on Rettiwt-API's Tweet constructor media extraction:
//   `tweet.legacy.extended_entities?.media?.map(m => new TweetMedia(m))`
// Same source path (legacy.extended_entities.media). Changed: adds expandedUrl field,
// filters out entries with no url and no expandedUrl, and conditionally sets thumbnailUrl.
const extractMedia = (legacy: Record<string, unknown>): XTimelineMedia[] => {
  const extendedEntities = asRecord(legacy.extended_entities);
  const mediaEntries = Array.isArray(extendedEntities?.media) ? extendedEntities.media : [];

  return mediaEntries
    .map((entry) => {
      const media = asRecord(entry);
      if (!media) {
        return null;
      }

      const type = mediaTypeFromRaw(media.type);
      const { url, thumbnailUrl } = extractBestMediaUrl(media);
      const expandedUrl = asString(media.expanded_url);

      if (!url && !expandedUrl) {
        return null;
      }

      const item: XTimelineMedia = {
        type,
        url,
        expandedUrl,
      };

      if (thumbnailUrl) {
        item.thumbnailUrl = thumbnailUrl;
      }

      return item;
    })
    .filter((media): media is XTimelineMedia => media !== null);
};

// Based on Rettiwt-API's Tweet.timeline() + Tweet constructor (src/models/data/Tweet.ts).
// The TweetWithVisibilityResults handling mirrors Rettiwt-API's _getQuotedTweet/_getRetweetedTweet.
// Changed: returns a flat XTimelineItem instead of a Tweet class instance.
// Extracts isRetweet/isQuote as booleans instead of nesting full quoted/retweeted Tweet objects.
// Uses note_tweet for long-form text the same way as Rettiwt-API's Tweet constructor.
// URL construction matches Rettiwt-API: `https://x.com/${userName}/status/${id}`.
const extractTweet = (timelineTweetNode: unknown): XTimelineItem | null => {
  const timelineRecord = asRecord(timelineTweetNode);
  const tweetResults = asRecord(timelineRecord?.tweet_results);
  let result = asRecord(tweetResults?.result);

  if (!result) {
    return null;
  }

  // From Rettiwt-API: handles TweetWithVisibilityResults by unwrapping to .tweet
  if (result.__typename === 'TweetWithVisibilityResults') {
    result = asRecord(result.tweet);
  }

  if (!result) {
    return null;
  }

  const legacy = asRecord(result.legacy);
  const id = asString(result.rest_id);
  if (!legacy || !id) {
    return null;
  }

  // From Rettiwt-API Tweet constructor: prefers note_tweet text over legacy.full_text
  const noteTweet = asRecord(result.note_tweet);
  const noteTweetResults = asRecord(noteTweet?.note_tweet_results);
  const noteTweetResult = asRecord(noteTweetResults?.result);

  // From Rettiwt-API: user info extracted via core.user_results.result
  const core = asRecord(result.core);
  const userResults = asRecord(core?.user_results);
  const userResult = asRecord(userResults?.result);
  const userLegacy = asRecord(userResult?.legacy);
  const authorHandle = asString(userLegacy?.screen_name);

  // From Rettiwt-API _getQuotedTweet / _getRetweetedTweet — simplified to boolean flags
  const quotedStatusResult = asRecord(result.quoted_status_result);
  const quotedResult = asRecord(quotedStatusResult?.result);
  const retweetedStatusResult = asRecord(legacy.retweeted_status_result);
  const retweetedResult = asRecord(retweetedStatusResult?.result);
  const retweetedResultTweet = asRecord(retweetedResult?.tweet);

  const isRetweet = Boolean(
    asString(retweetedResult?.rest_id) || asString(retweetedResultTweet?.rest_id),
  );
  const isQuote = Boolean(legacy.is_quote_status || asString(quotedResult?.rest_id));

  let quotedText = '';
  let quotedAuthorHandle = '';
  if (isQuote && quotedResult) {
    const qLegacy = asRecord(quotedResult.legacy);
    const qNoteTweet = asRecord(quotedResult.note_tweet);
    const qNoteResults = asRecord(qNoteTweet?.note_tweet_results);
    const qNoteResult = asRecord(qNoteResults?.result);
    quotedText = asString(qNoteResult?.text) || asString(qLegacy?.full_text);

    const qCore = asRecord(quotedResult.core);
    const qUserResults = asRecord(qCore?.user_results);
    const qUserResult = asRecord(qUserResults?.result);
    const qUserLegacy = asRecord(qUserResult?.legacy);
    quotedAuthorHandle = asString(qUserLegacy?.screen_name);
  }

  return {
    id,
    text: asString(noteTweetResult?.text) || asString(legacy.full_text),
    createdAt: toIsoDate(legacy.created_at),
    authorName: asString(userLegacy?.name),
    authorHandle,
    lang: asString(legacy.lang),
    replyTo: asString(legacy.in_reply_to_status_id_str),
    quoteCount: asNumber(legacy.quote_count),
    replyCount: asNumber(legacy.reply_count),
    retweetCount: asNumber(legacy.retweet_count),
    likeCount: asNumber(legacy.favorite_count),
    viewCount: asString(asRecord(result.views)?.count)
      ? asNumber(asRecord(result.views)?.count)
      : null,
    isRetweet,
    isQuote,
    quotedText,
    quotedAuthorHandle,
    url: authorHandle ? `https://x.com/${authorHandle}/status/${id}` : '',
    media: extractMedia(legacy),
  };
};

// Based on Rettiwt-API's Tweet.timeline() + CursoredData constructor (src/models/data/CursoredData.ts).
// Tweet extraction uses findByFilter(__typename, 'TimelineTweet') — same as Rettiwt-API.
// Cursor extraction uses findByFilter(cursorType, 'Bottom') — same as Rettiwt-API.
// Changed: added deduplication by tweet id (not in Rettiwt-API). Returns null instead of
// empty string when no next cursor exists.
export const parseXFollowingTimelineResponse = (payload: unknown): XFollowingTimelineBatch => {
  const timelineTweetNodes = findByFilter<unknown>(payload, '__typename', 'TimelineTweet');
  const deduped = new Map<string, XTimelineItem>();

  for (const node of timelineTweetNodes) {
    const item = extractTweet(node);
    if (!item || !item.id || deduped.has(item.id)) {
      continue;
    }

    deduped.set(item.id, item);
  }

  const cursors = findByFilter<Record<string, unknown>>(payload, 'cursorType', 'Bottom');
  const nextCursor = asString(cursors[0]?.value) || null;

  return {
    items: Array.from(deduped.values()),
    nextCursor,
  };
};
