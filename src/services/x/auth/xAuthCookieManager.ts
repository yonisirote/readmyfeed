import CookieManager from '@preeternal/react-native-cookie-manager';

import { X_BASE_URL } from './xAuthConstants';
import { X_AUTH_ERROR_CODES, XAuthError } from './xAuthErrors';
import { XAuthLogger } from './xAuthLogger';
import { XCookieReadResult, XCookieRecord } from './xAuthTypes';
import { evaluateCookies, normalizeCookieRecord } from './xAuthUtils';

export type XCookieReadOptions = {
  useWebViewStore?: boolean;
  fallbackToShared?: boolean;
  logger?: XAuthLogger;
};

export const readXCookies = async (
  options: XCookieReadOptions = {},
): Promise<XCookieReadResult> => {
  const { useWebViewStore = true, fallbackToShared = true, logger } = options;
  logger?.info('Reading X cookies', { domain: X_BASE_URL, useWebViewStore });

  try {
    const cookieJar = await CookieManager.get(X_BASE_URL, useWebViewStore);
    const normalizedByDomain = normalizeCookieRecord(cookieJar as Record<string, unknown>);

    let result = evaluateCookies(normalizedByDomain);

    if (!result.hasRequired && fallbackToShared && useWebViewStore) {
      logger?.warn('Missing required cookies in WebView store. Trying shared store.', {
        missingRequired: result.missingRequired,
      });
      const sharedJar = await CookieManager.get(X_BASE_URL, false);
      const sharedCookies = normalizeCookieRecord(sharedJar as Record<string, unknown>);
      const merged = { ...sharedCookies, ...normalizedByDomain };
      result = evaluateCookies(merged);
      logger?.info('Merged cookies from shared store', {
        available: Object.keys(merged),
        missingRequired: result.missingRequired,
        missingOptional: result.missingOptional,
      });
      return result;
    }

    logger?.info('X cookies read', {
      available: Object.keys(normalizedByDomain),
      missingRequired: result.missingRequired,
      missingOptional: result.missingOptional,
    });

    return result;
  } catch (err) {
    logger?.error('Failed to read X cookies', {
      error: err instanceof Error ? err.message : String(err),
    });
    throw new XAuthError('Failed to read cookies', X_AUTH_ERROR_CODES.CookieReadFailed, {
      cause: err instanceof Error ? err.message : String(err),
    });
  }
};

export const clearXCookies = async (options: XCookieReadOptions = {}): Promise<void> => {
  const { useWebViewStore = true, logger } = options;
  logger?.warn('Clearing X cookies', { baseUrl: X_BASE_URL, useWebViewStore });

  try {
    await CookieManager.clearAll(useWebViewStore);
    if (typeof CookieManager.flush === 'function') {
      await CookieManager.flush();
    }
    logger?.info('X cookies cleared');
  } catch (err) {
    logger?.error('Failed to clear X cookies', {
      error: err instanceof Error ? err.message : String(err),
    });
    throw new XAuthError('Failed to clear cookies', X_AUTH_ERROR_CODES.CookieReadFailed, {
      cause: err instanceof Error ? err.message : String(err),
    });
  }
};

export const hasCookieValue = (cookies: XCookieRecord, name: string): boolean => {
  const value = cookies[name];
  return typeof value === 'string' && value.trim().length > 0;
};
