import ClientTransaction from 'x-client-transaction-id';
import * as FileSystem from 'expo-file-system';

import { decodeApiKeyToCookies } from './xHomeCookies';
import { X_HOME_CONFIG } from './xHomeConfig';
import { buildGraphqlUrl } from './xHomeTimelineUrl';
import type { XHomeTimelineOptions, XHomeTimelineResult } from './xHomeTypes';
import { parseTimeline, toTweetSample } from './xTimelineParsing';

export type { XHomeTimelineTweetSample } from './xHomeTypes';

// Trim large responses before logging error details.
const safeBodyPrefix = (text: string, maxLen = 800): string => {
  const cleaned = text.replaceAll(/\s+/g, ' ').trim();
  return cleaned.slice(0, maxLen);
};

type FetchTextResult = {
  status: number;
  text: string;
};

// Fetch raw text using Expo FileSystem to preserve cookie headers on device.
const fetchText = async (
  url: string,
  headers: Record<string, string>,
  log?: (message: string) => void,
): Promise<FetchTextResult> => {
  try {
    const baseDir = FileSystem.cacheDirectory ?? FileSystem.documentDirectory;
    if (!baseDir) {
      throw new Error('Missing Expo file system directory.');
    }

    const tempUri = `${baseDir}rmf-${Date.now()}-${Math.random().toString(16).slice(2)}.txt`;
    let status = 0;
    let text = '';

    try {
      const res = await FileSystem.downloadAsync(url, tempUri, { headers });
      status = res.status ?? 0;
      text = await FileSystem.readAsStringAsync(res.uri);
    } finally {
      await FileSystem.deleteAsync(tempUri, { idempotent: true });
    }

    if (status >= 400) {
      const body = safeBodyPrefix(text);
      log?.(`fetch failed status=${status} url=${url} body=${body}`);
    }

    return { status, text };
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    throw new Error(`Request failed url=${url} error=${message}`);
  }
};


export const fetchXHomeTimeline = async (
  options: XHomeTimelineOptions,
): Promise<XHomeTimelineResult> => {
  // Orchestrate the HTML probe + GraphQL timeline fetch.
  const apiKey = options.apiKey.trim();
  const count = options.count;
  const cursor = options.cursor;
  const log = options.log;

  const { baseUrl, defaultBearerToken, defaultHeaders, timelinePath } =
    X_HOME_CONFIG;

  if (!apiKey) {
    throw new Error('Missing API key.');
  }

  if (typeof DOMParser !== 'function') {
    throw new Error('DOMParser is not available. Ensure polyfills are loaded.');
  }

  const { rawCookieHeader, csrfToken, hasAuthToken, hasKdt, hasTwid } =
    decodeApiKeyToCookies(apiKey);

  log?.(
    `cookie parts auth=${hasAuthToken} kdt=${hasKdt} twid=${hasTwid} csrf=${Boolean(csrfToken)}`,
  );

  log?.('requesting x.com HTML');
  const docRes = await fetchText(
    baseUrl,
    {
      ...defaultHeaders,
      Cookie: rawCookieHeader,
    },
    log,
  );

  if (docRes.status !== 200) {
    throw new Error(`Failed to fetch x.com HTML (status=${docRes.status}).`);
  }

  const docHtml = docRes.text;
  if (!docHtml) {
    throw new Error('Failed to fetch x.com HTML (empty response).');
  }

  const parser = new DOMParser();
  const doc = parser.parseFromString(docHtml, 'text/html');

  const bearer = defaultBearerToken;

  // X expects a client transaction id generated from the HTML payload.
  log?.('generating x-client-transaction-id');
  const tx = await ClientTransaction.create(doc);
  const transactionId = await tx.generateTransactionId('GET', timelinePath);

  const url = buildGraphqlUrl(count, cursor);
  log?.(`requesting timeline ${cursor ? 'cursor=set' : 'cursor=none'}`);

  const timelineRes = await fetchText(
    url,
    {
      ...defaultHeaders,
      ...(csrfToken ? { 'x-csrf-token': csrfToken } : {}),
      'x-client-transaction-id': transactionId,
      authorization: `Bearer ${bearer}`,
      Cookie: rawCookieHeader,
    },
    log,
  );

  const bodyText = timelineRes.text;
  if (timelineRes.status !== 200) {
    throw new Error(
      `Timeline request failed status=${timelineRes.status} bodyPrefix=${safeBodyPrefix(bodyText)}`,
    );
  }

  let data: unknown;
  try {
    data = JSON.parse(bodyText);
  } catch (error) {
    const msg = error instanceof Error ? error.message : String(error);
    throw new Error(`Failed to parse timeline JSON: ${msg}`);
  }

  const { entries, nextCursor, tweetEntries } = parseTimeline(data);
  const tweetSamples = tweetEntries
    .slice(0, count)
    .map((e) => e?.content?.itemContent?.tweet_results?.result)
    .filter(Boolean)
    .map(toTweetSample)
    .filter((t) => Boolean(t.id));

  return {
    entriesCount: entries.length,
    tweetsCount: tweetEntries.length,
    nextCursor,
    tweetSamples,
  };
};
