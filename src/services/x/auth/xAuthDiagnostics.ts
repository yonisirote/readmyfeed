import { NativeModules, Platform } from 'react-native';

import { XAuthLogger } from './xAuthLogger';

export type XAuthDiagnostics = {
  logEnvSummary: () => void;
};

export const createXAuthDiagnostics = (logger?: XAuthLogger): XAuthDiagnostics => {
  const logEnvSummary = () => {
    const hasCookieManager = Boolean(NativeModules?.CookieManager);
    logger?.info('X auth environment summary', {
      platform: Platform.OS,
      userAgent: typeof navigator !== 'undefined' ? navigator.userAgent : 'native',
      expo: true,
      secureStore: true,
      cookieManager: hasCookieManager,
    });
  };

  return { logEnvSummary };
};
