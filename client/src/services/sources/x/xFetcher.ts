import { fetchXHomeTimeline } from '../../xHome/xHomeTimeline';

import type { FeedPage, XAuth } from '../../feed/feedTypes';

import { mapXSampleToFeedItem } from './xMapper';

export const fetchXPage = async (
  auth: XAuth,
  count: number,
  cursor?: string,
): Promise<FeedPage> => {
  const result = await fetchXHomeTimeline({
    auth,
    count,
    cursor,
  });

  return {
    items: result.tweetSamples.map(mapXSampleToFeedItem),
    cursor: result.nextCursor,
    rawCount: result.entriesCount,
  };
};
