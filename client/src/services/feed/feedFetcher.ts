import { fetchXPage } from '../sources/x/xFetcher';

import type { FeedPage, FetchParams } from './feedTypes';

const unsupportedSource = (): never => {
  throw new Error('Unsupported feed source.');
};

export const fetchFeedPage = async (params: FetchParams): Promise<FeedPage> => {
  switch (params.source) {
    case 'x':
      return fetchXPage(params.auth, params.count, params.cursor);
    case 'facebook':
      throw new Error('Facebook feed is not implemented yet.');
    case 'telegram':
      throw new Error('Telegram feed is not implemented yet.');
    default:
      return unsupportedSource();
  }
};
