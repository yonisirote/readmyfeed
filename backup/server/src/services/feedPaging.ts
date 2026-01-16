import { FEED_PAGINATION_DEFAULT_MAX_PAGES } from '../constants/feed.js';
import type { FeedResult } from './twitter.js';

export type FeedPageFetcher = (cursor?: string) => Promise<Pick<FeedResult, 'tweets' | 'nextCursor' | 'hasMore'>>;

export type PageThroughFeedOptions = {
  maxPages?: number;
};

export type PageThroughFeedResult = {
  pagesFetched: number;
  uniqueTweetCount: number;
  lastCursor?: string;
  stoppedBecause: 'done' | 'max_pages' | 'stalled';
};

export async function pageThroughFeed(fetchPage: FeedPageFetcher, options: PageThroughFeedOptions = {}): Promise<PageThroughFeedResult> {
  const maxPages = options.maxPages ?? FEED_PAGINATION_DEFAULT_MAX_PAGES;

  const seenTweetIds = new Set<string>();
  const seenCursors = new Set<string>();

  let cursor: string | undefined;
  let pagesFetched = 0;

  while (pagesFetched < maxPages) {
    const page = await fetchPage(cursor);
    pagesFetched += 1;

    for (const tweet of page.tweets) {
      if (tweet.id) seenTweetIds.add(tweet.id);
    }

    const nextCursor = page.nextCursor;
    if (!page.hasMore || !nextCursor) {
      return {
        pagesFetched,
        uniqueTweetCount: seenTweetIds.size,
        lastCursor: nextCursor,
        stoppedBecause: 'done',
      };
    }

    if (seenCursors.has(nextCursor)) {
      return {
        pagesFetched,
        uniqueTweetCount: seenTweetIds.size,
        lastCursor: nextCursor,
        stoppedBecause: 'stalled',
      };
    }

    seenCursors.add(nextCursor);
    cursor = nextCursor;
  }

  return {
    pagesFetched,
    uniqueTweetCount: seenTweetIds.size,
    lastCursor: cursor,
    stoppedBecause: 'max_pages',
  };
}
