import {
  COOKIE_ORDER,
  OPTIONAL_X_COOKIES,
  REQUIRED_X_COOKIES,
  X_BASE_URL,
  X_LOGIN_URL,
  POST_LOGIN_PATH_HINTS,
} from './xAuthConstants';
import { X_AUTH_ERROR_CODES, XAuthError } from './xAuthErrors';
import { XAuthSession, XCookieReadResult, XCookieRecord } from './xAuthTypes';

const sanitizeCookieValue = (value: string) => value.trim();

export const isXLoginUrl = (url?: string | null): boolean => {
  if (!url) {
    return false;
  }
  return url.startsWith(X_LOGIN_URL);
};

export const looksLikeLoggedInUrl = (url?: string | null): boolean => {
  if (!url) {
    return false;
  }

  try {
    const parsed = new URL(url);
    if (parsed.origin !== X_BASE_URL) {
      return false;
    }

    return POST_LOGIN_PATH_HINTS.some((hint) => parsed.pathname.startsWith(hint));
  } catch (err) {
    return false;
  }
};

// Based on Rettiwt-API's AuthCookie constructor (models/auth/AuthCookie.ts), which parses
// Cookie[] objects by name. Change: accepts a generic record (from react-native-cookie-manager)
// and normalizes both plain strings and {value: string} shapes into a flat record.
export const normalizeCookieRecord = (cookies: Record<string, unknown>): XCookieRecord => {
  const out: XCookieRecord = {};
  for (const [key, value] of Object.entries(cookies)) {
    if (typeof value === 'string') {
      out[key] = sanitizeCookieValue(value);
    } else if (value && typeof value === 'object' && 'value' in value) {
      const maybeValue = (value as { value?: unknown }).value;
      if (typeof maybeValue === 'string') {
        out[key] = sanitizeCookieValue(maybeValue);
      }
    }
  }

  return out;
};

export const evaluateCookies = (cookies: XCookieRecord): XCookieReadResult => {
  const missingRequired = REQUIRED_X_COOKIES.filter((name) => !cookies[name]);
  const missingOptional = OPTIONAL_X_COOKIES.filter((name) => !cookies[name]);

  return {
    cookies,
    missingRequired: missingRequired as XCookieReadResult['missingRequired'],
    missingOptional: missingOptional as XCookieReadResult['missingOptional'],
    hasRequired: missingRequired.length === 0,
  };
};

// Based on Rettiwt-API's AuthCookie.toString() (models/auth/AuthCookie.ts).
// Change: iterates a fixed COOKIE_ORDER instead of Object.entries, validates
// required cookies before building, and uses "; " delimiters instead of ";".
export const buildCookieString = (cookies: XCookieRecord): string => {
  const missingRequired = REQUIRED_X_COOKIES.filter((name) => !cookies[name]);
  if (missingRequired.length > 0) {
    throw new XAuthError('Missing required cookies', X_AUTH_ERROR_CODES.CookieMissingRequired, {
      missingRequired,
    });
  }

  const parts: string[] = [];
  for (const name of COOKIE_ORDER) {
    const value = cookies[name];
    if (value) {
      parts.push(`${name}=${value}`);
    }
  }

  if (parts.length === 0) {
    throw new XAuthError('Empty cookie string', X_AUTH_ERROR_CODES.CookieStringInvalid);
  }

  return `${parts.join('; ')};`;
};

export const createSessionFromCookies = (cookies: XCookieRecord): XAuthSession => {
  const cookieString = buildCookieString(cookies);
  return {
    cookieString,
    cookieNames: Object.keys(cookies),
  };
};
