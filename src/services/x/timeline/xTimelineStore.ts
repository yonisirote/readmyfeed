// New — not from Rettiwt-API. Simple in-memory cache for the latest fetched batch,
// used by the app's UI to avoid re-fetching on component remounts.
import type { XFollowingTimelineBatch } from './xTimelineTypes';

let cachedBatch: XFollowingTimelineBatch | null = null;

export const setXFollowingTimelineBatch = (batch: XFollowingTimelineBatch): void => {
  cachedBatch = batch;
};

export const getXFollowingTimelineBatch = (): XFollowingTimelineBatch | null => {
  return cachedBatch;
};

export const clearXFollowingTimelineBatch = (): void => {
  cachedBatch = null;
};
