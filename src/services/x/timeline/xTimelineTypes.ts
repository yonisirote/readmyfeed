export type XTimelineMediaType = 'photo' | 'video' | 'animated_gif' | 'unknown';

export type XTimelineMedia = {
  type: XTimelineMediaType;
  url: string;
  expandedUrl: string;
  thumbnailUrl?: string;
};

export type XTimelineItem = {
  id: string;
  text: string;
  createdAt: string;
  authorName: string;
  authorHandle: string;
  lang: string;
  replyTo: string;
  quoteCount: number;
  replyCount: number;
  retweetCount: number;
  likeCount: number;
  viewCount: number | null;
  isRetweet: boolean;
  isQuote: boolean;
  url: string;
  media: XTimelineMedia[];
};

export type XFollowingTimelineBatch = {
  items: XTimelineItem[];
  nextCursor: string | null;
};
