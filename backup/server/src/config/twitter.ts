import { getNumberEnv, getRequiredEnv } from './env.js';

export const twitterConfig = {
  apiKey: getRequiredEnv('API_KEY'),

  // Lightweight in-process rate limiting to avoid hammering X endpoints.
  // Set to 0 to disable.
  minTimeMsBetweenRequests: getNumberEnv('TWITTER_MIN_TIME_MS', 250),
};
