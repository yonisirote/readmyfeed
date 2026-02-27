import { encode as encodeBase64, decode as decodeBase64 } from 'base-64';

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

export const encodeCookieString = (cookieString: string): string => {
  return encodeBase64(cookieString);
};

export const decodeCookieString = (encodedCookie: string): string => {
  return decodeBase64(encodedCookie);
};

export const createSessionFromCookies = (cookies: XCookieRecord): XAuthSession => {
  const cookieString = buildCookieString(cookies);
  const encodedCookie = encodeCookieString(cookieString);
  return {
    cookieString,
    encodedCookie,
    cookieNames: Object.keys(cookies),
  };
};

export const isXBaseUrl = (url?: string | null): boolean => {
  if (!url) {
    return false;
  }
  return url.startsWith(X_BASE_URL);
};
