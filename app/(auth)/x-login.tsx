import { useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, SafeAreaView, StyleSheet, Text, View } from 'react-native';
import { WebView } from 'react-native-webview';
import type { WebViewNavigation } from 'react-native-webview';

import {
  createXAuthLogger,
  createXAuthDiagnostics,
  evaluateXWebViewNavigation,
  XAuthError,
  XAuthService,
  X_ALLOWED_ORIGINS,
  X_LOGIN_URL,
} from '../../src/services/x/auth';

const logger = createXAuthLogger();
const diagnostics = createXAuthDiagnostics(logger);

export default function XLoginScreen() {
  const authService = useMemo(() => new XAuthService({ logger }), []);
  const [status, setStatus] = useState<'idle' | 'loading' | 'capturing' | 'done' | 'error'>('idle');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const captureInFlight = useRef(false);
  const didCapture = useRef(false);

  const handleNavigationStateChange = async (navState: WebViewNavigation) => {
    try {
      const decision = evaluateXWebViewNavigation(navState, logger);
      if (!decision.shouldCapture || status === 'capturing' || status === 'done') {
        return;
      }

      if (captureInFlight.current || didCapture.current) {
        logger.debug('Capture already in progress or completed');
        return;
      }

      captureInFlight.current = true;
      setStatus('capturing');
      logger.info('Login hint detected. Attempting cookie capture.', { url: navState.url });

      const session = await authService.captureAndStoreSession();
      logger.info('X session stored', { cookieNames: session.cookieNames });

      didCapture.current = true;
      setStatus('done');
    } catch (err) {
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

      <View style={styles.webViewContainer}>
        <WebView
          source={{ uri: X_LOGIN_URL }}
          originWhitelist={[...X_ALLOWED_ORIGINS]}
          onNavigationStateChange={handleNavigationStateChange}
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

      <View style={styles.statusBar}>
        {status === 'capturing' && <Text style={styles.statusText}>Capturing session…</Text>}
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
});
