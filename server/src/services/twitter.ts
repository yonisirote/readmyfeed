import { Rettiwt } from 'rettiwt-api';

import { twitterConfig } from '../config/twitter.js';

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
};

export type FeedResult = {
  tweets: FeedTweet[];
  nextCursor?: string;
};

function getClient(): Rettiwt {
  return new Rettiwt({ apiKey: twitterConfig.apiKey });
}

function toFeedTweet(item: any): FeedTweet {
  const tweet = typeof item?.toJSON === 'function' ? item.toJSON() : item;

  const id = String(tweet?.id ?? tweet?.restId ?? tweet?.rest_id ?? '');
  const tweetBy = tweet?.tweetBy;
  const userName = tweetBy?.userName ?? tweetBy?.screen_name;

  return {
    id,
    text: String(tweet?.fullText ?? tweet?.text ?? ''),
    createdAt: tweet?.createdAt ? String(tweet.createdAt) : undefined,
    user: tweetBy
      ? {
          id: tweetBy?.id ? String(tweetBy.id) : undefined,
          userName: userName ? String(userName) : undefined,
          fullName: tweetBy?.fullName ? String(tweetBy.fullName) : undefined,
        }
      : undefined,
    url: tweet?.url ? String(tweet.url) : userName && id ? `https://x.com/${userName}/status/${id}` : undefined,
  };
}

export async function getFollowedFeed(options: { count?: number; cursor?: string } = {}): Promise<FeedResult> {
  const client = getClient();

  // Rettiwt's `user.followed()` always returns 35 items.
  // We accept `count` on our API and slice locally.
  const count = options.count ?? 10;
  const cursor = options.cursor;

  const data: any = await client.user.followed(cursor);

  const list = Array.isArray(data?.list) ? data.list : Array.isArray(data) ? data : [];
  const tweets = list.map(toFeedTweet).filter((t) => t.id && t.text).slice(0, count);

  return {
    tweets,
    nextCursor: data?.next?.value,
  };
}
