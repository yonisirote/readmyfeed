export type ParsedCookies = {
  rawCookieHeader: string;
  csrfToken?: string;
  hasAuthToken: boolean;
  hasKdt: boolean;
  hasTwid: boolean;
};

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
    csrfToken,
    hasAuthToken: Boolean(authToken),
    hasKdt: Boolean(kdt),
    hasTwid: Boolean(twid),
  };
};
