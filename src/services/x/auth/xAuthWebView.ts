import type { WebViewNavigation } from 'react-native-webview';

import { X_AUTH_ERROR_CODES, XAuthError } from './xAuthErrors';
import { XAuthLogger } from './xAuthLogger';
import { XAuthLoginState } from './xAuthTypes';
import { looksLikeLoggedInUrl } from './xAuthUtils';

export type XWebViewDecision = {
  state: XAuthLoginState;
  shouldCapture: boolean;
};

// No equivalent in Rettiwt-API — Rettiwt-API authenticates via HTTP requests
// (guest token POST or pre-supplied cookie strings). This module handles the
// React Native WebView login flow, detecting post-login navigation to decide
// when to capture cookies.
export const evaluateXWebViewNavigation = (
  navState: WebViewNavigation,
  logger?: XAuthLogger,
): XWebViewDecision => {
  if (!navState?.url) {
    logger?.warn('WebView navigation missing URL');
    throw new XAuthError('WebView URL missing', X_AUTH_ERROR_CODES.WebViewNotReady);
  }

  const isLoggedInHint = looksLikeLoggedInUrl(navState.url);

  logger?.debug('WebView navigation update', {
    url: navState.url,
    title: navState.title,
    loading: navState.loading,
    canGoBack: navState.canGoBack,
    isLoggedInHint,
  });

  return {
    state: {
      isLoggedInHint,
      url: navState.url,
    },
    shouldCapture: isLoggedInHint,
  };
};
