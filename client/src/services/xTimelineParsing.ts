import type { XHomeTimelineTweetSample } from './xHomeTypes';

export type TimelineParseResult = {
  entries: any[];
  nextCursor?: string;
  tweetEntries: any[];
};

const safeBodyPrefix = (text: string, maxLen = 800): string => {
  const cleaned = text.replaceAll(/\s+/g, ' ').trim();
  return cleaned.slice(0, maxLen);
};

export const parseTimeline = (data: unknown): TimelineParseResult => {
  if (!data || typeof data !== 'object') {
    return { entries: [], tweetEntries: [] };
  }

  const instructions: any[] =
    (data as any)?.data?.home?.home_timeline_urt?.instructions ?? (data as any)?.data?.home?.timeline?.instructions ?? [];

  const entries: any[] = [];
  for (const inst of instructions) {
    if (inst?.type === 'TimelineAddEntries' && Array.isArray(inst.entries)) {
      entries.push(...inst.entries);
    }
  }

  const cursorEntry = entries.find((e) => String(e?.entryId ?? '').startsWith('cursor-bottom'));
  const next = cursorEntry?.content?.value ?? cursorEntry?.content?.itemContent?.value;

  const tweetEntries = entries.filter((e) => e?.content?.itemContent?.tweet_results);
  return { entries, nextCursor: typeof next === 'string' ? next : undefined, tweetEntries };
};

const unwrapTweetResult = (result: any): any | undefined => {
  if (!result) return undefined;
  if (result.tweet) return result.tweet;
  if (result.result) return result.result;
  return result;
};

const resolveUserScreenName = (tweetResult: any): string | undefined => {
  const userResult = tweetResult?.core?.user_results?.result;
  const legacy = userResult?.legacy ?? userResult?.result?.legacy ?? userResult?.user?.legacy;
  const candidate =
    userResult?.core?.screen_name ??
    legacy?.screen_name ??
    userResult?.screen_name ??
    userResult?.legacy?.screen_name ??
    tweetResult?.core?.user_results?.legacy?.screen_name;

  return typeof candidate === 'string' ? candidate : undefined;
};

export const toTweetSample = (tweetResult: any): XHomeTimelineTweetSample => {
  const resolved = unwrapTweetResult(tweetResult);
  const retweeted = unwrapTweetResult(resolved?.legacy?.retweeted_status_result?.result);
  const base = resolved ?? retweeted;

  const id = String(base?.rest_id ?? '');
  const user = resolveUserScreenName(base) ?? resolveUserScreenName(retweeted);
  const text =
    base?.legacy?.full_text ??
    base?.legacy?.text ??
    retweeted?.legacy?.full_text ??
    retweeted?.legacy?.text;

  return {
    id,
    user: typeof user === 'string' ? user : undefined,
    text: typeof text === 'string' ? safeBodyPrefix(text, 160) : undefined,
  };
};
