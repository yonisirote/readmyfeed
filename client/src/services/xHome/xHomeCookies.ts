import type { XAuth } from '../feed/feedTypes';

export type XCookieValue = { value?: string } | string;

export type XCookieJar = Record<string, XCookieValue>;

export type ParsedCookies = {
  rawCookieHeader: string;
  authToken?: string;
  csrfToken?: string;
  kdt?: string;
  twid?: string;
  hasAuthToken: boolean;
  hasKdt: boolean;
  hasTwid: boolean;
};

export type ParsedCookieValues = {
  authToken?: string;
  csrfToken?: string;
  kdt?: string;
  twid?: string;
};

const readCookieValue = (cookie?: XCookieValue): string | undefined => {
  if (!cookie) {
    return undefined;
  }
  if (typeof cookie === 'string') {
    return cookie;
  }
  if (typeof cookie.value === 'string') {
    return cookie.value;
  }
  return undefined;
};

// Convert the encoded API key into a cookie header + metadata.
export const decodeApiKeyToCookies = (key: string): ParsedCookies => {
  const decoded = atob(key.trim());
  const cookiePairs = decoded.split(';');
  const cookieMap = new Map<string, string>();

  for (const pair of cookiePairs) {
    const trimmed = pair.trim();
    if (!trimmed) continue;
    const [name, ...rest] = trimmed.split('=');
    if (!name || rest.length === 0) continue;
    cookieMap.set(name, rest.join('='));
  }

  const authToken = cookieMap.get('auth_token');
  const csrfToken = cookieMap.get('ct0');
  const kdt = cookieMap.get('kdt');
  const twid = cookieMap.get('twid');

  const parts: string[] = [];
  if (authToken) parts.push(`auth_token=${authToken}`);
  if (csrfToken) parts.push(`ct0=${csrfToken}`);
  if (kdt) parts.push(`kdt=${kdt}`);
  if (twid) parts.push(`twid=${twid}`);

  const rawCookieHeader = parts.length ? `${parts.join(';')};` : decoded;

  return {
    rawCookieHeader,
    authToken,
    csrfToken,
    kdt,
    twid,
    hasAuthToken: Boolean(authToken),
    hasKdt: Boolean(kdt),
    hasTwid: Boolean(twid),
  };
};

export const parseCookiesFromJar = (jar: XCookieJar): ParsedCookieValues => ({
  authToken: readCookieValue(jar.auth_token),
  csrfToken: readCookieValue(jar.ct0),
  kdt: readCookieValue(jar.kdt),
  twid: readCookieValue(jar.twid),
});

export const buildCookieHeaderFromAuth = (auth: XAuth): ParsedCookies => {
  const parts: string[] = [];

  if (auth.authToken) {
    parts.push(`auth_token=${auth.authToken}`);
  }

  if (auth.csrfToken) {
    parts.push(`ct0=${auth.csrfToken}`);
  }

  if (auth.kdt) {
    parts.push(`kdt=${auth.kdt}`);
  }

  if (auth.twid) {
    parts.push(`twid=${auth.twid}`);
  }

  return {
    rawCookieHeader: parts.length ? `${parts.join(';')};` : '',
    authToken: auth.authToken,
    csrfToken: auth.csrfToken,
    kdt: auth.kdt,
    twid: auth.twid,
    hasAuthToken: Boolean(auth.authToken),
    hasKdt: Boolean(auth.kdt),
    hasTwid: Boolean(auth.twid),
  };
};

export const toXAuth = (cookies: ParsedCookieValues): XAuth => {
  const authToken = cookies.authToken;
  const csrfToken = cookies.csrfToken;

  if (!authToken || !csrfToken) {
    const missing = [!authToken ? 'auth_token' : null, !csrfToken ? 'ct0' : null]
      .filter(Boolean)
      .join(', ');
    throw new Error(`Missing X cookies: ${missing || 'auth_token, ct0'}.`);
  }

  return {
    authToken,
    csrfToken,
    kdt: cookies.kdt,
    twid: cookies.twid,
  };
};
