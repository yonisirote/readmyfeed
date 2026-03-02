export const X_BASE_URL = 'https://x.com';
export const X_LOGIN_URL = 'https://x.com/i/flow/login';

// Cookie names derived from Rettiwt-API's IAuthCookie interface (types/auth/AuthCookie.ts)
// and AuthCookie model (models/auth/AuthCookie.ts).
// Change: split into required vs optional — Rettiwt-API treats all four as mandatory,
// but here `kdt` is optional since it isn't always set during WebView login.
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
