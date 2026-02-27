import { useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, SafeAreaView, StyleSheet, Text, View } from 'react-native';
import { WebView } from 'react-native-webview';
import type { WebViewMessageEvent, WebViewNavigation } from 'react-native-webview';
import { useRouter } from 'expo-router';

import {
  createXAuthDiagnostics,
  createXAuthLogger,
  evaluateXWebViewNavigation,
  X_AUTH_ERROR_CODES,
  XAuthError,
  XAuthService,
  X_ALLOWED_ORIGINS,
  X_LOGIN_URL,
} from '../../src/services/x/auth';
import { looksLikeLoggedInUrl } from '../../src/services/x/auth/xAuthUtils';
import {
  setXFollowingTimelineBatch,
  XTimelineError,
  XTimelineService,
} from '../../src/services/x/timeline';
import type { XFollowingTimelineBatch } from '../../src/services/x/timeline';

const logger = createXAuthLogger();
const diagnostics = createXAuthDiagnostics(logger);

const LOGIN_BRIDGE_SOURCE = 'readmyfeed-x-login';
const LOGIN_BRIDGE_SCRIPT = String.raw`(function () {
  if (window.__rmfXLoginBridgeInstalled) {
    return true;
  }

  window.__rmfXLoginBridgeInstalled = true;

  var SOURCE = 'readmyfeed-x-login';

  function post(type, payload) {
    try {
      window.ReactNativeWebView.postMessage(
        JSON.stringify({
          source: SOURCE,
          type: type,
          payload: payload || {},
        }),
      );
    } catch (err) {}
  }

  function notify() {
    post('url', {
      href: window.location ? window.location.href : '',
      title: document && document.title ? document.title : '',
    });
  }

  if (window.history && window.history.pushState) {
    var originalPushState = window.history.pushState;
    window.history.pushState = function () {
      var result = originalPushState.apply(this, arguments);
      notify();
      return result;
    };
  }

  if (window.history && window.history.replaceState) {
    var originalReplaceState = window.history.replaceState;
    window.history.replaceState = function () {
      var result = originalReplaceState.apply(this, arguments);
      notify();
      return result;
    };
  }

  window.addEventListener('popstate', notify);
  window.addEventListener('hashchange', notify);
  document.addEventListener('readystatechange', function () {
    if (document.readyState === 'complete') {
      notify();
    }
  });

  notify();
  return true;
})();`;

type LoginBridgeMessage = {
  source: typeof LOGIN_BRIDGE_SOURCE;
  type: 'url';
  payload: {
    href?: string;
    title?: string;
  };
};

const parseLoginBridgeMessage = (raw: string): LoginBridgeMessage | null => {
  try {
    const parsed = JSON.parse(raw) as LoginBridgeMessage;
    if (parsed?.source !== LOGIN_BRIDGE_SOURCE || parsed.type !== 'url') {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
};

export default function XLoginScreen() {
  const router = useRouter();
  const authService = useMemo(() => new XAuthService({ logger }), []);
  const timelineService = useMemo(
    () => new XTimelineService({ logger, authService }),
    [authService],
  );

  const [status, setStatus] = useState<
    'idle' | 'loading' | 'capturing' | 'fetching' | 'done' | 'error'
  >('idle');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [showWebView, setShowWebView] = useState(true);

  const captureInFlight = useRef(false);
  const didCapture = useRef(false);

  const applyTimelineBatch = (batch: XFollowingTimelineBatch) => {
    logger.info('Received following timeline batch', {
      count: batch.items.length,
      hasNextCursor: Boolean(batch.nextCursor),
    });

    if (batch.items.length === 0) {
      setErrorMessage('Connected to X but received an empty following feed.');
      setStatus('error');
      return;
    }

    setXFollowingTimelineBatch(batch);
    setStatus('done');
    router.replace('/(auth)/x-feed');
  };

  const fetchFollowingTimeline = async (cookieString: string) => {
    setStatus('fetching');

    try {
      const batch = await timelineService.fetchFollowingTimeline({
        cookieString,
      });

      applyTimelineBatch(batch);
    } catch (err) {
      if (err instanceof XTimelineError) {
        logger.warn('X timeline fetch error', {
          code: err.code,
          message: err.message,
          context: err.context,
        });
        setErrorMessage(err.message);
      } else {
        logger.error('Unexpected X timeline error', {
          error: err instanceof Error ? err.message : String(err),
        });
        setErrorMessage('Connected to X but failed to load your following feed.');
      }

      setStatus('error');
    }
  };

  const captureSessionWithRetry = async (
    maxAttempts: number = 3,
    delayMs: number = 800,
  ): Promise<ReturnType<typeof authService.captureAndStoreSession>> => {
    for (let attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        return await authService.captureAndStoreSession();
      } catch (err) {
        if (
          attempt < maxAttempts &&
          err instanceof XAuthError &&
          err.code === X_AUTH_ERROR_CODES.CookieMissingRequired
        ) {
          logger.debug('Cookie capture retry', {
            attempt,
            maxAttempts,
            delayMs,
            missingRequired: err.context?.missingRequired,
          });
          await new Promise((resolve) => setTimeout(resolve, delayMs));
          continue;
        }
        throw err;
      }
    }
    throw new XAuthError(
      'Cookie capture exhausted retries',
      X_AUTH_ERROR_CODES.CookieMissingRequired,
    );
  };

  const completeCaptureAndLoadTimeline = async ({
    reason,
    strict,
  }: {
    reason: string;
    strict: boolean;
  }) => {
    if (captureInFlight.current || didCapture.current) {
      return;
    }

    captureInFlight.current = true;

    if (strict) {
      setStatus('capturing');
      setShowWebView(false);
    }

    logger.info('Attempting session capture from navigation', { reason, strict });

    try {
      const session = await captureSessionWithRetry();
      logger.info('X session stored', { cookieNames: session.cookieNames, reason });

      didCapture.current = true;

      if (!strict) {
        setShowWebView(false);
      }

      await fetchFollowingTimeline(session.cookieString);
    } catch (err) {
      if (
        !strict &&
        err instanceof XAuthError &&
        err.code === X_AUTH_ERROR_CODES.CookieMissingRequired
      ) {
        logger.debug('Capture fallback skipped: cookies not ready yet', {
          reason,
          missingRequired: err.context?.missingRequired,
        });
        return;
      }

      if (err instanceof XAuthError) {
        logger.warn('X auth flow error', { code: err.code, message: err.message });
        setErrorMessage(err.message);
      } else {
        logger.error('Unexpected X auth error', {
          error: err instanceof Error ? err.message : String(err),
        });
        setErrorMessage('Unexpected error while logging in.');
      }

      setStatus('error');
    } finally {
      captureInFlight.current = false;
    }
  };

  const handleWebViewMessage = async (event: WebViewMessageEvent) => {
    const raw = event.nativeEvent?.data;
    if (!raw) {
      return;
    }

    const message = parseLoginBridgeMessage(raw);
    if (!message) {
      return;
    }

    const href = message.payload.href ?? '';
    if (!href) {
      return;
    }

    if (!X_ALLOWED_ORIGINS.some((origin) => href.startsWith(origin))) {
      return;
    }

    if (looksLikeLoggedInUrl(href)) {
      logger.info('Login bridge detected authenticated URL', { href });
      await completeCaptureAndLoadTimeline({
        reason: 'bridge-url',
        strict: true,
      });
    }
  };

  const handleNavigationStateChange = async (navState: WebViewNavigation) => {
    try {
      const decision = evaluateXWebViewNavigation(navState, logger);

      if (didCapture.current || captureInFlight.current) {
        return;
      }

      if (decision.shouldCapture) {
        await completeCaptureAndLoadTimeline({
          reason: 'post-login-hint',
          strict: true,
        });
        return;
      }

      const inScope = X_ALLOWED_ORIGINS.some((origin) => navState.url.startsWith(origin));
      if (!inScope || navState.loading) {
        return;
      }

      await completeCaptureAndLoadTimeline({
        reason: 'fallback-non-loading-nav',
        strict: false,
      });
    } catch (err) {
      logger.error('Navigation handler failure', {
        message: err instanceof Error ? err.message : String(err),
        url: navState.url,
      });
      setErrorMessage('Unexpected navigation error while logging in.');
      setStatus('error');
      captureInFlight.current = false;
    }
  };

  useEffect(() => {
    diagnostics.logEnvSummary();
  }, []);

  return (
    <SafeAreaView style={styles.root}>
      <View style={styles.header}>
        <Text style={styles.title}>Connect X</Text>
        <Text style={styles.subtitle}>
          Log in using your X username/email and password. Google/SSO is not supported.
        </Text>
      </View>

      {showWebView ? (
        <View style={styles.webViewContainer}>
          <WebView
            source={{ uri: X_LOGIN_URL }}
            originWhitelist={[...X_ALLOWED_ORIGINS]}
            onNavigationStateChange={handleNavigationStateChange}
            onMessage={handleWebViewMessage}
            injectedJavaScriptBeforeContentLoaded={LOGIN_BRIDGE_SCRIPT}
            javaScriptEnabled
            onLoadStart={() => {
              logger.debug('WebView load started');
              if (status === 'idle') {
                setStatus('loading');
              }
            }}
            onLoadEnd={() => {
              logger.debug('WebView load ended');
              if (status === 'loading') {
                setStatus('idle');
              }
            }}
            onError={(event) => {
              logger.error('WebView error', {
                code: event.nativeEvent?.code,
                description: event.nativeEvent?.description,
              });
              setErrorMessage(event.nativeEvent?.description ?? 'WebView error');
              setStatus('error');
            }}
            sharedCookiesEnabled
            thirdPartyCookiesEnabled
            setSupportMultipleWindows={false}
            startInLoadingState
            renderLoading={() => (
              <View style={styles.loadingOverlay}>
                <ActivityIndicator size="large" />
                <Text style={styles.loadingText}>Loading X…</Text>
              </View>
            )}
          />
        </View>
      ) : (
        <View style={styles.feedContainer}>
          <Text style={styles.feedTitle}>Following feed</Text>
          <Text style={styles.feedSubtitle}>Preparing your feed...</Text>
          <View style={styles.feedLoadingState}>
            <ActivityIndicator size="small" />
            <Text style={styles.feedLoadingText}>Loading first batch...</Text>
          </View>
        </View>
      )}

      <View style={styles.statusBar}>
        {status === 'capturing' && <Text style={styles.statusText}>Capturing session…</Text>}
        {status === 'fetching' && (
          <Text style={styles.statusText}>Loading your following feed…</Text>
        )}
        {status === 'done' && <Text style={styles.statusSuccess}>Connected successfully.</Text>}
        {status === 'error' && (
          <Text style={styles.statusError}>{errorMessage ?? 'Failed to connect.'}</Text>
        )}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: '#ffffff',
  },
  header: {
    paddingHorizontal: 20,
    paddingTop: 16,
    paddingBottom: 8,
  },
  title: {
    fontSize: 20,
    fontWeight: '700',
    marginBottom: 4,
  },
  subtitle: {
    fontSize: 14,
    color: '#444444',
  },
  webViewContainer: {
    flex: 1,
    borderTopWidth: 1,
    borderBottomWidth: 1,
    borderColor: '#e6e6e6',
  },
  statusBar: {
    paddingHorizontal: 20,
    paddingVertical: 12,
  },
  statusText: {
    color: '#2d2d2d',
  },
  statusSuccess: {
    color: '#0f7a35',
    fontWeight: '600',
  },
  statusError: {
    color: '#c0342b',
    fontWeight: '600',
  },
  loadingOverlay: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 16,
  },
  loadingText: {
    marginTop: 8,
    fontSize: 14,
  },
  feedContainer: {
    flex: 1,
    paddingHorizontal: 16,
    paddingTop: 8,
  },
  feedTitle: {
    fontSize: 18,
    fontWeight: '700',
  },
  feedSubtitle: {
    marginTop: 4,
    color: '#525252',
    marginBottom: 12,
  },
  feedLoadingState: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 12,
  },
  feedLoadingText: {
    marginLeft: 8,
    color: '#4c4c4c',
  },
});
