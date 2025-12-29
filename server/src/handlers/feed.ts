import type { RequestHandler } from 'express';

import { FOLLOWED_FEED_DEFAULT_COUNT, FOLLOWED_FEED_MAX_COUNT } from '../constants/feed.js';
import { getFollowedFeed } from '../services/twitter.js';

function clampInt(value: unknown, fallback: number, min: number, max: number): number {
  if (typeof value !== 'number' || !Number.isFinite(value)) return fallback;
  return Math.max(min, Math.min(max, value));
}

export const getFeed: RequestHandler = async (req, res) => {
  try {
    const countRaw = typeof req.query.count === 'string' ? Number(req.query.count) : undefined;
    const count = clampInt(countRaw, FOLLOWED_FEED_DEFAULT_COUNT, 1, FOLLOWED_FEED_MAX_COUNT);

    const cursor = typeof req.query.cursor === 'string' ? req.query.cursor : undefined;

    const data = await getFollowedFeed({ count, cursor });

    res.json({
      tweets: data.tweets,
      nextCursor: data.nextCursor,
    });
  } catch (error) {
    res.status(500).json({
      error: 'failed_to_fetch_feed',
      message: error instanceof Error ? error.message : String(error),
    });
  }
};
