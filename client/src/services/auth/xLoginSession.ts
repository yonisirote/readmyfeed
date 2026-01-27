import CookieManager from '@react-native-cookies/cookies';
import { Platform } from 'react-native';

import type { XAuth } from '../feed/feedTypes';
import {
  parseCookiesFromJar,
  toXAuth,
  type XCookieJar,
} from '../xHome/xHomeCookies';

export type XLoginState = 'idle' | 'in_progress' | 'success' | 'error';

const X_LOGIN_URL = 'https://x.com/i/flow/login';
const X_HOME_URLS = ['https://x.com/home', 'https://twitter.com/home'];

const shouldUseWebKit = (): boolean => Platform.OS === 'ios';
const assertNative = (): void => {
  if (Platform.OS === 'web') {
    throw new Error('X login is not available on web builds.');
  }
};

const mergeCookieJars = (...jars: XCookieJar[]): XCookieJar =>
  jars.reduce((acc, jar) => ({ ...acc, ...jar }), {});

export const getXLoginUrl = (): string => X_LOGIN_URL;

export const isLoginComplete = (url: string): boolean => {
  const normalized = url.toLowerCase();
  return X_HOME_URLS.some((homeUrl) => normalized.startsWith(homeUrl));
};

export const readXCookies = async (): Promise<XCookieJar> => {
  assertNative();
  const useWebKit = shouldUseWebKit();
  if (Platform.OS === 'android') {
    await CookieManager.flush();
  }

  const safeGetCookies = async (url: string): Promise<XCookieJar> => {
    try {
      return (await CookieManager.get(url, useWebKit)) as XCookieJar;
    } catch {
      return {};
    }
  };

  const xCookies = await safeGetCookies('https://x.com');
  const twitterCookies = await safeGetCookies('https://twitter.com');

  return mergeCookieJars(twitterCookies, xCookies);
};

export const clearXCookies = async (): Promise<void> => {
  assertNative();
  const useWebKit = shouldUseWebKit();

  if (Platform.OS !== 'ios') {
    await CookieManager.clearAll(useWebKit);
    return;
  }

  const clearCookiesForDomain = async (url: string): Promise<void> => {
    const cookies = (await CookieManager.get(url, useWebKit)) as XCookieJar;
    const names = Object.keys(cookies);

    await Promise.all(
      names.map((name) => CookieManager.clearByName(url, name, useWebKit)),
    );
  };

  try {
    await clearCookiesForDomain('https://x.com');
    await clearCookiesForDomain('https://twitter.com');
  } catch {
    await CookieManager.clearAll(useWebKit);
  }
};

export const resolveXAuthFromCookies = async (): Promise<XAuth> => {
  assertNative();
  const cookies = await readXCookies();
  const parsed = parseCookiesFromJar(cookies);
  return toXAuth(parsed);
};
