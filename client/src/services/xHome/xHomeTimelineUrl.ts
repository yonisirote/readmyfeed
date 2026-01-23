import { X_HOME_CONFIG } from './xHomeConfig';

// Shared variables expected by the X timeline endpoint.
const BASE_TIMELINE_VARIABLES = {
  includePromotedContent: false,
  latestControlAvailable: true,
  withCommunity: false,
};

// Build the timeline GraphQL URL with feature flags and pagination.
export const buildGraphqlUrl = (count: number, cursor?: string): string => {
  const url = new URL(
    `${X_HOME_CONFIG.baseUrl}${X_HOME_CONFIG.timelinePath}`,
  );

  const variables: Record<string, unknown> = {
    ...BASE_TIMELINE_VARIABLES,
    count,
    ...(cursor ? { cursor } : {}),
  };

  url.searchParams.set('variables', JSON.stringify(variables));
  url.searchParams.set(
    'features',
    JSON.stringify(X_HOME_CONFIG.timelineFeatures),
  );

  return url.toString();
};
