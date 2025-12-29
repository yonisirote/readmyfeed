import { pageThroughFeed } from './services/feedPaging.js';

function makeMockFeed(pages: number) {
  const cursors = Array.from({ length: pages }, (_v, i) => `cursor_${i + 1}`);

  return async (cursor?: string) => {
    // First page: cursor is undefined
    const index = cursor ? cursors.indexOf(cursor) + 1 : 0;

    // End of feed
    if (index < 0 || index >= pages) {
      return {
        tweets: [],
        nextCursor: undefined,
        hasMore: false,
      };
    }

    const tweetBase = index * 3;
    const tweets = Array.from({ length: 3 }, (_v, t) => ({
      id: String(tweetBase + t + 1),
      text: `tweet ${tweetBase + t + 1}`,
    }));

    const nextCursor = index + 1 < pages ? cursors[index] : undefined;

    return {
      tweets,
      nextCursor,
      hasMore: Boolean(nextCursor),
    };
  };
}

async function main() {
  const fetcher = makeMockFeed(4);
  const result = await pageThroughFeed(fetcher, { maxPages: 10 });

  console.log(result);
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
