import * as Speech from 'expo-speech';

import type { XHomeTimelineTweetSample } from '../xHome/xHomeTypes';

const collapseWhitespace = (value: string): string => value.replace(/\s+/g, ' ').trim();

const stripUrls = (value: string): string => value.replace(/https?:\/\/\S+/g, '').trim();

const normalizeHandle = (handle?: string): string => {
  if (!handle) return 'Unknown user';
  const trimmed = handle.trim();
  if (!trimmed) return 'Unknown user';
  return trimmed.startsWith('@') ? trimmed : `@${trimmed}`;
};

export const buildTweetSpeechText = (tweet: XHomeTimelineTweetSample): string => {
  const handle = normalizeHandle(tweet.user);
  const rawText = tweet.text ? stripUrls(tweet.text) : '';
  const cleanedText = rawText ? collapseWhitespace(rawText) : '';
  const body = cleanedText || 'No text available.';
  return `${handle} says: ${body}`;
};

export const speakText = (text: string, options?: Speech.SpeechOptions): void => {
  Speech.speak(text, options);
};

export const stopSpeech = (): void => {
  Speech.stop();
};
