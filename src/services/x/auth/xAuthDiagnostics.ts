import { NativeModules, Platform } from 'react-native';

import { XAuthLogger } from './xAuthLogger';

export type XAuthDiagnostics = {
  logEnvSummary: () => void;
};

// No equivalent in Rettiwt-API — this is a React Native-specific diagnostic helper
// for logging platform and cookie-manager availability during development.
export const createXAuthDiagnostics = (logger?: XAuthLogger): XAuthDiagnostics => {
  const logEnvSummary = () => {
    const hasCookieManager = Boolean(NativeModules?.CookieManager);
    logger?.info('X auth environment summary', {
      platform: Platform.OS,
      userAgent: typeof navigator !== 'undefined' ? navigator.userAgent : 'native',
      cookieManager: hasCookieManager,
    });
  };

  return { logEnvSummary };
};
