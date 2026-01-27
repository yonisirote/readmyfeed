export type FeedSource = 'x' | 'facebook' | 'telegram';

export type XAuth = {
  authToken: string;
  csrfToken: string;
  kdt?: string;
  twid?: string;
};

export type FacebookAuth = Record<string, never>;

export type TelegramAuth = Record<string, never>;

export type AuthBySource = {
  x: XAuth;
  facebook: FacebookAuth;
  telegram: TelegramAuth;
};

export type AuthForSource<S extends FeedSource> = AuthBySource[S];

export type FeedItem = {
  id: string;
  source: FeedSource;
  authorHandle?: string;
  authorName?: string;
  text: string;
  timestamp?: string;
  url?: string;
  meta?: Record<string, string>;
};

export type FeedPage = {
  items: FeedItem[];
  cursor?: string;
  rawCount?: number;
};

export type FetchParams =
  | {
      source: 'x';
      auth: XAuth;
      count: number;
      cursor?: string;
    }
  | {
      source: 'facebook';
      auth: FacebookAuth;
      count: number;
      cursor?: string;
    }
  | {
      source: 'telegram';
      auth: TelegramAuth;
      count: number;
      cursor?: string;
    };
