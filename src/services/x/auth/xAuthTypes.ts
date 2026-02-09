export type XAuthCookieNames = 'auth_token' | 'ct0' | 'kdt' | 'twid';

export type XCookieRecord = Record<string, string>;

export type XCookieReadResult = {
  cookies: XCookieRecord;
  missingRequired: XAuthCookieNames[];
  missingOptional: XAuthCookieNames[];
  hasRequired: boolean;
};

export type XAuthSession = {
  cookieString: string;
  encodedCookie: string;
  cookieNames: string[];
};

export type XAuthLoginState = {
  isLoggedInHint: boolean;
  url?: string;
};
