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
