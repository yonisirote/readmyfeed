/// <reference types="node" />

import '../src/polyfills/node';

import { fetchXHomeTimeline } from '../src/services/xHomeTimeline';

const getArg = (flag: string): string | undefined => {
  const index = process.argv.indexOf(flag);
  if (index === -1) return undefined;
  return process.argv[index + 1];
};

const apiKey = getArg('--api-key') ?? process.env.API_KEY;
const countArg = getArg('--count');
const count = countArg ? Number.parseInt(countArg, 10) : 5;

if (!apiKey) {
  console.error('Missing API key. Provide --api-key or set API_KEY.');
  process.exit(1);
}

if (!Number.isFinite(count) || count <= 0) {
  console.error('Invalid --count value.');
  process.exit(1);
}

const run = async () => {
  const result = await fetchXHomeTimeline({
    apiKey,
    count,
    log: (message) => console.log(`[cli] ${message}`),
  });

  console.log(`Fetched entries=${result.entriesCount} tweets=${result.tweetsCount}`);

  result.tweetSamples.forEach((tweet, index) => {
    const header = `${index + 1}. @${tweet.user ?? 'unknown'} — ${tweet.id}`;
    const body = tweet.text ?? '';
    console.log(`${header}\n${body}\n`);
  });
};

run().catch((error) => {
  const message = error instanceof Error ? error.message : String(error);
  console.error(`Fetch failed: ${message}`);
  process.exit(1);
});
