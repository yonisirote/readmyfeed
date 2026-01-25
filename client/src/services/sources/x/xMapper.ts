import type { FeedItem } from '../../feed/feedTypes';
import type { XHomeTimelineTweetSample } from '../../xHome/xHomeTypes';

export const mapXSampleToFeedItem = (tweet: XHomeTimelineTweetSample): FeedItem => ({
  id: tweet.id,
  source: 'x',
  authorHandle: tweet.user,
  text: tweet.text ?? '',
});
