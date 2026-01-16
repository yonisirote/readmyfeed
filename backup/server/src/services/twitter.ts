import { Rettiwt } from 'rettiwt-api';

import { twitterConfig } from '../config/twitter.js';
import { FOLLOWED_FEED_DEFAULT_COUNT } from '../constants/feed.js';
import { createMinIntervalRateLimiter } from '../utils/rateLimit.js';
import { decodeHtmlEntities } from '../utils/text.js';

export type FeedTweet = {
  id: string;
  text: string;
  createdAt?: string;
  user?: {
    id?: string;
    userName?: string;
    fullName?: string;
  };
  url?: string;
  retweetOf?: {
    userName?: string;
    fullName?: string;
  };
};

export type FeedResult = {
  tweets: FeedTweet[];
  nextCursor?: string;
  hasMore: boolean;
};

const rateLimiter = createMinIntervalRateLimiter({ minIntervalMs: twitterConfig.minTimeMsBetweenRequests });

function getClient(): Rettiwt {
  return new Rettiwt({ apiKey: twitterConfig.apiKey });
}

function toFeedTweet(item: any): FeedTweet {
  const tweet = typeof item?.toJSON === 'function' ? item.toJSON() : item;

  const id = String(tweet?.id ?? tweet?.restId ?? tweet?.rest_id ?? '');
  const tweetBy = tweet?.tweetBy;
  const userName = tweetBy?.userName ?? tweetBy?.screen_name;

  const retweetedTweetBy = tweet?.retweetedTweet?.tweetBy;
  const retweetOfUserName = retweetedTweetBy?.userName ?? retweetedTweetBy?.screen_name;

  // If it's a retweet, we want to read the original tweet.
  const textSource = tweet?.retweetedTweet?.fullText ?? tweet?.retweetedTweet?.text ?? tweet?.fullText ?? tweet?.text ?? '';
  const text = decodeHtmlEntities(String(textSource)).trim();

  const retweetOf = retweetedTweetBy
    ? {
        userName: retweetOfUserName ? String(retweetOfUserName) : undefined,
        fullName: retweetedTweetBy?.fullName ? String(retweetedTweetBy.fullName) : undefined,
      }
    : undefined;

  return {
    id,
    text,
    createdAt: tweet?.createdAt ? String(tweet.createdAt) : undefined,
    user: tweetBy
      ? {
          id: tweetBy?.id ? String(tweetBy.id) : undefined,
          userName: userName ? String(userName) : undefined,
          fullName: tweetBy?.fullName ? String(tweetBy.fullName) : undefined,
        }
      : undefined,
    url: tweet?.url ? String(tweet.url) : userName && id ? `https://x.com/${userName}/status/${id}` : undefined,
    retweetOf,
  };
}

export async function getFollowedFeed(options: { count?: number; cursor?: string } = {}): Promise<FeedResult> {
  const client = getClient();

  // Rettiwt's `user.followed()` always returns 35 items.
  // We accept `count` on our API and slice locally.
  const count = options.count ?? FOLLOWED_FEED_DEFAULT_COUNT;
  const cursor = options.cursor;

  await rateLimiter.wait();
  const data: any = await client.user.followed(cursor);

  const list = Array.isArray(data?.list) ? data.list : Array.isArray(data) ? data : [];
  const tweets = list
    .map(toFeedTweet)
    .filter((t: FeedTweet) => t.id && t.text)
    .slice(0, count);

  // CursoredData.next is a string in rettiwt-api.
  const nextCursor = typeof data?.next === 'string' ? data.next : typeof data?.next?.value === 'string' ? data.next.value : undefined;

  // Pagination stop conditions (stateless): no cursor, no results.
  // TODO: Clients should also de-dupe by `id` to be safe.
  const hasMore = Boolean(nextCursor) && tweets.length > 0 && nextCursor !== cursor;

  return {
    tweets,
    nextCursor,
    hasMore,
  };
}
