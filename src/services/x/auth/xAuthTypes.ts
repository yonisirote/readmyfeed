// Cookie field names taken from Rettiwt-API's IAuthCookie interface (types/auth/AuthCookie.ts).
// Change: expressed as a string union type instead of an interface with string properties.
export type XAuthCookieNames = 'auth_token' | 'ct0' | 'kdt' | 'twid';

export type XCookieRecord = Record<string, string>;

export type XCookieReadResult = {
  cookies: XCookieRecord;
  missingRequired: XAuthCookieNames[];
  missingOptional: XAuthCookieNames[];
  hasRequired: boolean;
};

// Analogous to Rettiwt-API's IAuthCredential (types/auth/AuthCredential.ts).
// Change: only stores the cookie string and cookie names — Rettiwt-API's credential
// also tracks authToken, csrfToken, guestToken, and authenticationType, which are
// not needed here since we only do user-auth via WebView cookies.
export type XAuthSession = {
  cookieString: string;
  cookieNames: string[];
};

export type XAuthLoginState = {
  isLoggedInHint: boolean;
  url?: string;
};
