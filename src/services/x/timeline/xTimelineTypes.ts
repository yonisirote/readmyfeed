// Based on Rettiwt-API's RawMediaType enum (src/enums/raw/Media.ts) and MediaType enum (src/enums/Media.ts).
// Changed: uses raw X API values ('photo', 'video', 'animated_gif') directly as a union type
// instead of separate raw/display enums. Added 'unknown' fallback not present in Rettiwt-API.
export type XTimelineMediaType = 'photo' | 'video' | 'animated_gif' | 'unknown';

// Based on Rettiwt-API's TweetMedia class (src/models/data/Tweet.ts TweetMedia).
// Changed: flattened from a class to a plain type. Added expandedUrl field not in Rettiwt-API.
// Dropped the media id field that Rettiwt-API includes.
export type XTimelineMedia = {
  type: XTimelineMediaType;
  url: string;
  expandedUrl: string;
  thumbnailUrl?: string;
};

// Based on Rettiwt-API's Tweet class (src/models/data/Tweet.ts).
// Changed: flattened from a class to a plain type. Renamed fields (e.g. fullText -> text,
// tweetBy -> authorName/authorHandle, favorite_count -> likeCount). Dropped entities, bookmarkCount,
// conversationId, and nested quoted/retweetedTweet objects. Added isRetweet/isQuote boolean flags
// instead. viewCount is number|null instead of optional number.
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

// Based on Rettiwt-API's CursoredData class (src/models/data/CursoredData.ts).
// Changed: flattened from a generic class to a plain type. Renamed list -> items, next -> nextCursor.
// nextCursor is string|null instead of empty string for "no more pages".
export type XFollowingTimelineBatch = {
  items: XTimelineItem[];
  nextCursor: string | null;
};
