import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

import type { AuthForSource, FeedSource } from '../feed/feedTypes';

const AUTH_KEY_PREFIX = 'rmf.auth.';
const IS_WEB = Platform.OS === 'web';

const buildKey = (source: FeedSource): string => `${AUTH_KEY_PREFIX}${source}`;

const parseAuth = <S extends FeedSource>(value: string): AuthForSource<S> | null => {
  try {
    return JSON.parse(value) as AuthForSource<S>;
  } catch {
    return null;
  }
};

export const saveAuth = async <S extends FeedSource>(
  source: S,
  auth: AuthForSource<S>,
): Promise<void> => {
  if (IS_WEB) return;
  await SecureStore.setItemAsync(buildKey(source), JSON.stringify(auth));
};

export const loadAuth = async <S extends FeedSource>(
  source: S,
): Promise<AuthForSource<S> | null> => {
  if (IS_WEB) return null;
  const stored = await SecureStore.getItemAsync(buildKey(source));
  if (!stored) return null;
  return parseAuth<S>(stored);
};

export const clearAuth = async <S extends FeedSource>(source: S): Promise<void> => {
  if (IS_WEB) return;
  await SecureStore.deleteItemAsync(buildKey(source));
};
