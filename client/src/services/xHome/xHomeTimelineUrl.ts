import { TIMELINE_FEATURES, X_BASE_URL, X_TIMELINE_PATH } from './xHomeConfig';

const BASE_TIMELINE_VARIABLES = {
  includePromotedContent: false,
  latestControlAvailable: true,
  withCommunity: false,
};

export const buildGraphqlUrl = (count: number, cursor?: string): string => {
  const url = new URL(`${X_BASE_URL}${X_TIMELINE_PATH}`);

  const variables: Record<string, unknown> = {
    ...BASE_TIMELINE_VARIABLES,
    count,
    ...(cursor ? { cursor } : {}),
  };

  url.searchParams.set('variables', JSON.stringify(variables));
  url.searchParams.set('features', JSON.stringify(TIMELINE_FEATURES));

  return url.toString();
};
