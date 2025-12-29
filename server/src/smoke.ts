import { getFollowedFeed } from './services/twitter.js';

async function main() {
  const result = await getFollowedFeed({ count: 5 });

  console.log(`fetched ${result.tweets.length} tweets`);
  for (const tweet of result.tweets) {
    const user = tweet.user?.userName ? `@${tweet.user.userName}` : '<unknown>';
    console.log(`- ${user}: ${tweet.text.replace(/\s+/g, ' ').trim().slice(0, 140)}`);
    if (tweet.url) console.log(`  ${tweet.url}`);
  }

  if (result.nextCursor) {
    console.log(`nextCursor: ${result.nextCursor}`);
  }
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
