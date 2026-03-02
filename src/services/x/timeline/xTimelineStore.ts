// New — not from Rettiwt-API. Cache the latest fetched batch for UI handoff,
// persisted to secure storage so it survives app restarts.
import * as SecureStore from 'expo-secure-store';
import type { XFollowingTimelineBatch } from './xTimelineTypes';

const TIMELINE_BATCH_KEY = 'x-following-timeline-batch-v1';
let cachedBatch: XFollowingTimelineBatch | null = null;

const isValidBatch = (value: unknown): value is XFollowingTimelineBatch => {
  if (!value || typeof value !== 'object') {
    return false;
  }

  const record = value as Record<string, unknown>;
  if (!Array.isArray(record.items)) {
    return false;
  }

  if (record.nextCursor !== null && typeof record.nextCursor !== 'string') {
    return false;
  }

  return true;
};

export const setXFollowingTimelineBatch = async (batch: XFollowingTimelineBatch): Promise<void> => {
  cachedBatch = batch;
  await SecureStore.setItemAsync(TIMELINE_BATCH_KEY, JSON.stringify(batch));
};

export const loadXFollowingTimelineBatch = async (): Promise<XFollowingTimelineBatch | null> => {
  if (cachedBatch) {
    return cachedBatch;
  }

  const stored = await SecureStore.getItemAsync(TIMELINE_BATCH_KEY);
  if (!stored) {
    return null;
  }

  try {
    const parsed = JSON.parse(stored) as unknown;
    if (!isValidBatch(parsed)) {
      return null;
    }

    cachedBatch = parsed;
    return parsed;
  } catch {
    return null;
  }
};

export const getXFollowingTimelineBatch = (): XFollowingTimelineBatch | null => {
  return cachedBatch;
};

export const clearXFollowingTimelineBatch = async (): Promise<void> => {
  cachedBatch = null;
  await SecureStore.deleteItemAsync(TIMELINE_BATCH_KEY);
};
