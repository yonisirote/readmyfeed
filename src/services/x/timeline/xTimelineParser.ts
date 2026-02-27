import {
  XFollowingTimelineBatch,
  XTimelineItem,
  XTimelineMedia,
  XTimelineMediaType,
} from './xTimelineTypes';

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

const findByFilter = <T>(data: unknown, key: string, value: string): T[] => {
  let result: T[] = [];

  if (Array.isArray(data)) {
    for (const item of data) {
      result = result.concat(findByFilter<T>(item, key, value));
    }
    return result;
  }

  if (!isRecord(data)) {
    return result;
  }

  if (Object.prototype.hasOwnProperty.call(data, key) && data[key] === value) {
    result.push(data as unknown as T);
  }

  for (const valueOfKey of Object.values(data)) {
    result = result.concat(findByFilter<T>(valueOfKey, key, value));
  }

  return result;
};

const mediaTypeFromRaw = (value: unknown): XTimelineMediaType => {
  if (value === 'photo' || value === 'video' || value === 'animated_gif') {
    return value;
  }

  return 'unknown';
};

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

const extractTweet = (timelineTweetNode: unknown): XTimelineItem | null => {
  const timelineRecord = asRecord(timelineTweetNode);
  const tweetResults = asRecord(timelineRecord?.tweet_results);
  let result = asRecord(tweetResults?.result);

  if (!result) {
    return null;
  }

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

  const noteTweet = asRecord(result.note_tweet);
  const noteTweetResults = asRecord(noteTweet?.note_tweet_results);
  const noteTweetResult = asRecord(noteTweetResults?.result);

  const core = asRecord(result.core);
  const userResults = asRecord(core?.user_results);
  const userResult = asRecord(userResults?.result);
  const userLegacy = asRecord(userResult?.legacy);
  const authorHandle = asString(userLegacy?.screen_name);

  const quotedStatusResult = asRecord(result.quoted_status_result);
  const quotedResult = asRecord(quotedStatusResult?.result);
  const retweetedStatusResult = asRecord(legacy.retweeted_status_result);
  const retweetedResult = asRecord(retweetedStatusResult?.result);
  const retweetedResultTweet = asRecord(retweetedResult?.tweet);

  const isRetweet = Boolean(
    asString(retweetedResult?.rest_id) || asString(retweetedResultTweet?.rest_id),
  );
  const isQuote = Boolean(legacy.is_quote_status || asString(quotedResult?.rest_id));

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
    url: authorHandle ? `https://x.com/${authorHandle}/status/${id}` : '',
    media: extractMedia(legacy),
  };
};

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
