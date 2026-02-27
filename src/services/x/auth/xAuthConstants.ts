export const X_BASE_URL = 'https://x.com';
export const X_LOGIN_URL = 'https://x.com/i/flow/login';

export const REQUIRED_X_COOKIES = ['auth_token', 'ct0', 'twid'] as const;
export const OPTIONAL_X_COOKIES = ['kdt'] as const;
export const COOKIE_ORDER = ['auth_token', 'ct0', 'kdt', 'twid'] as const;

export const POST_LOGIN_PATH_HINTS = [
  '/home',
  '/notifications',
  '/messages',
  '/explore',
  '/settings',
  '/compose',
  '/search',
  '/i/bookmarks',
] as const;
