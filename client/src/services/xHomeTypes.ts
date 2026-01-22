export type XHomeTimelineOptions = {
  apiKey: string;
  count: number;
  cursor?: string;
  log?: (message: string) => void;
};

export type XHomeTimelineTweetSample = {
  id: string;
  user?: string;
  text?: string;
};

export type XHomeTimelineResult = {
  entriesCount: number;
  tweetsCount: number;
  nextCursor?: string;
  tweetSamples: XHomeTimelineTweetSample[];
};
