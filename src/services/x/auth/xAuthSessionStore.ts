import * as SecureStore from 'expo-secure-store';

import { X_AUTH_ERROR_CODES, XAuthError } from './xAuthErrors';
import { XAuthLogger } from './xAuthLogger';

const SESSION_KEY = 'x-auth-session';

export type XAuthSessionStore = {
  get: () => Promise<string | null>;
  set: (encodedCookie: string) => Promise<void>;
  clear: () => Promise<void>;
};

export const createXAuthSessionStore = (logger?: XAuthLogger): XAuthSessionStore => {
  const get = async () => {
    try {
      const value = await SecureStore.getItemAsync(SESSION_KEY);
      logger?.info('Loaded X session from SecureStore', { found: Boolean(value) });
      return value ?? null;
    } catch (err) {
      logger?.error('Failed to load X session from SecureStore', {
        error: err instanceof Error ? err.message : String(err),
      });
      throw new XAuthError('Failed to read SecureStore', X_AUTH_ERROR_CODES.SecureStoreFailed, {
        cause: err instanceof Error ? err.message : String(err),
      });
    }
  };

  const set = async (encodedCookie: string) => {
    try {
      await SecureStore.setItemAsync(SESSION_KEY, encodedCookie);
      logger?.info('Stored X session in SecureStore', { length: encodedCookie.length });
    } catch (err) {
      logger?.error('Failed to store X session in SecureStore', {
        error: err instanceof Error ? err.message : String(err),
      });
      throw new XAuthError('Failed to write SecureStore', X_AUTH_ERROR_CODES.SecureStoreFailed, {
        cause: err instanceof Error ? err.message : String(err),
      });
    }
  };

  const clear = async () => {
    try {
      await SecureStore.deleteItemAsync(SESSION_KEY);
      logger?.info('Cleared X session from SecureStore');
    } catch (err) {
      logger?.error('Failed to clear X session in SecureStore', {
        error: err instanceof Error ? err.message : String(err),
      });
      throw new XAuthError('Failed to delete SecureStore', X_AUTH_ERROR_CODES.SecureStoreFailed, {
        cause: err instanceof Error ? err.message : String(err),
      });
    }
  };

  return { get, set, clear };
};
