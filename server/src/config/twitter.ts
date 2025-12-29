import { getRequiredEnv } from './env.js';

export const twitterConfig = {
  apiKey: getRequiredEnv('API_KEY'),
};
