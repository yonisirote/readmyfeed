import type { FeedItem } from '../feed/feedTypes';

const collapseWhitespace = (value: string): string => value.replace(/\s+/g, ' ').trim();

const stripUrls = (value: string): string => value.replace(/https?:\/\/\S+/g, '').trim();

const normalizeHandle = (handle?: string): string => {
  if (!handle) return 'Unknown user';
  const trimmed = handle.trim();
  if (!trimmed) return 'Unknown user';
  return trimmed.startsWith('@') ? trimmed : `@${trimmed}`;
};

const buildBody = (text?: string): string => {
  if (!text) return 'No text available.';
  const cleaned = collapseWhitespace(stripUrls(text));
  return cleaned || 'No text available.';
};

const resolveSpeaker = (item: FeedItem): string => {
  if (item.authorName && item.authorName.trim()) {
    return item.authorName.trim();
  }
  if (item.authorHandle && item.authorHandle.trim()) {
    return normalizeHandle(item.authorHandle);
  }
  return 'Unknown author';
};

const buildXSpeechText = (item: FeedItem): string => {
  const handle = normalizeHandle(item.authorHandle);
  return `${handle} says: ${buildBody(item.text)}`;
};

const buildGenericSpeechText = (item: FeedItem): string => {
  return `${resolveSpeaker(item)} says: ${buildBody(item.text)}`;
};

export const buildSpeechText = (item: FeedItem): string => {
  switch (item.source) {
    case 'x':
      return buildXSpeechText(item);
    case 'facebook':
    case 'telegram':
      return buildGenericSpeechText(item);
    default:
      return buildGenericSpeechText(item);
  }
};
