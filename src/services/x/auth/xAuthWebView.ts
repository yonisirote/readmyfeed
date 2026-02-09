import type { WebViewNavigation } from 'react-native-webview';

import { X_AUTH_ERROR_CODES, XAuthError } from './xAuthErrors';
import { XAuthLogger } from './xAuthLogger';
import { XAuthLoginState } from './xAuthTypes';
import { looksLikeLoggedInUrl, isXBaseUrl } from './xAuthUtils';

export type XWebViewDecision = {
  state: XAuthLoginState;
  shouldCapture: boolean;
};

export const evaluateXWebViewNavigation = (
  navState: WebViewNavigation,
  logger?: XAuthLogger,
): XWebViewDecision => {
  if (!navState?.url) {
    logger?.warn('WebView navigation missing URL');
    throw new XAuthError('WebView URL missing', X_AUTH_ERROR_CODES.WebViewNotReady);
  }

  const isLoggedInHint = looksLikeLoggedInUrl(navState.url);
  const inScope = isXBaseUrl(navState.url);

  logger?.debug('WebView navigation update', {
    url: navState.url,
    title: navState.title,
    loading: navState.loading,
    canGoBack: navState.canGoBack,
    isLoggedInHint,
    inScope,
  });

  return {
    state: {
      isLoggedInHint: isLoggedInHint && inScope,
      url: navState.url,
    },
    shouldCapture: isLoggedInHint && inScope,
  };
};
