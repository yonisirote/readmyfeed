import './src/polyfills/native';

import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import {
  useEffect,
  useRef,
  useState,
  type ComponentProps,
  type ComponentType,
  type RefAttributes,
} from 'react';
import {
  ActivityIndicator,
  Animated,
  Modal,
  Platform,
  Pressable,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { WebView } from 'react-native-webview';

import { clearAuth, loadAuth, saveAuth } from './src/services/auth/authStore';
import {
  clearXCookies,
  getXLoginUrl,
  isLoginComplete,
  resolveXAuthFromCookies,
  type XLoginState,
} from './src/services/auth/xLoginSession';
import { FeedPaginator } from './src/services/feed/feedPaginator';
import type { FeedItem, FeedSource, XAuth } from './src/services/feed/feedTypes';
import { expoSpeechEngine } from './src/services/tts/expoSpeechEngine';
import { SpeechQueueController } from './src/services/tts/speechQueueController';

const TWEET_COUNT = 5;

const theme = {
  ink: '#0f1c2f',
  inkSoft: '#43506a',
  accent: '#1aa39c',
  accentStrong: '#137d78',
  accentSoft: 'rgba(26, 163, 156, 0.16)',
  surface: 'rgba(255, 255, 255, 0.96)',
  surfaceMuted: 'rgba(240, 244, 250, 0.88)',
  border: 'rgba(15, 28, 47, 0.1)',
  error: '#b5443f',
  glow: 'rgba(26, 163, 156, 0.22)',
};

type WebViewHandle = {
  injectJavaScript: (script: string) => void;
  postMessage: (message: string) => void;
  reload: () => void;
};

const LoginWebView = WebView as unknown as ComponentType<
  ComponentProps<typeof WebView> & RefAttributes<WebViewHandle>
>;

const X_LOGIN_USER_AGENT = Platform.select({
  ios:
    'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1',
  android:
    'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36',
});

const LOGIN_POPUP_MESSAGE = 'x_login_popup_message';
const LOGIN_POPUP_CLOSE = 'x_login_popup_close';
const LOGIN_POPUP_BRIDGE = `
  (function() {
    var postMessage =
      window.ReactNativeWebView && window.ReactNativeWebView.postMessage
        ? window.ReactNativeWebView.postMessage.bind(window.ReactNativeWebView)
        : function() {};

    window.opener = {
      postMessage: function(data, origin) {
        postMessage(JSON.stringify({
          type: '${LOGIN_POPUP_MESSAGE}',
          data: data,
          origin: origin,
          sourceOrigin: window.location.origin
        }));
      }
    };

    window.close = function() {
      postMessage(JSON.stringify({
        type: '${LOGIN_POPUP_CLOSE}'
      }));
    };
  })();
  true;
`;

type FetchStatus = 'idle' | 'loading' | 'ready' | 'error';


type FetchMeta = {
  itemsCount: number;
  rawCount?: number;
  cursor?: string;
};

const SOURCE_OPTIONS: FeedSource[] = ['x', 'facebook', 'telegram'];

const SOURCE_LABELS: Record<FeedSource, string> = {
  x: 'X',
  facebook: 'Facebook',
  telegram: 'Telegram',
};

const isSourceEnabled = (source: FeedSource): boolean => source === 'x';

const formatHandle = (handle?: string) => {
  if (!handle) return '@unknown';
  return handle.startsWith('@') ? handle : `@${handle}`;
};

const formatAuthor = (item: FeedItem) => {
  if (item.authorHandle) return formatHandle(item.authorHandle);
  if (item.authorName) return item.authorName;
  return 'Unknown author';
};

const truncateText = (text?: string, maxLen = 220) => {
  if (!text) return '';
  if (text.length <= maxLen) return text;
  return `${text.slice(0, maxLen).trim()}…`;
};

export default function App() {
  const [source, setSource] = useState<FeedSource>('x');
  const [xAuth, setXAuth] = useState<XAuth | null>(null);
  const [loginState, setLoginState] = useState<XLoginState>('idle');
  const [loginError, setLoginError] = useState<string | null>(null);
  const [isLoginVisible, setIsLoginVisible] = useState(false);
  const [status, setStatus] = useState<FetchStatus>('idle');
  const [error, setError] = useState<string | null>(null);
  const [items, setItems] = useState<FeedItem[]>([]);
  const [meta, setMeta] = useState<FetchMeta | null>(null);
  const [isSpeaking, setIsSpeaking] = useState(false);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [speechError, setSpeechError] = useState<string | null>(null);
  const [isFetchingMore, setIsFetchingMore] = useState(false);
  const fadeAnim = useRef(new Animated.Value(1)).current;
  const itemsRef = useRef<FeedItem[]>([]);
  const paginatorRef = useRef<FeedPaginator | null>(null);
  const nextCursorRef = useRef<string | undefined>(undefined);
  const currentIndexRef = useRef(0);
  const speechSessionRef = useRef(0);
  const isFetchingMoreRef = useRef(false);
  const speechControllerRef = useRef<SpeechQueueController | null>(null);
  const queueDoneRef = useRef<() => void>(() => {});
  const queueErrorRef = useRef<(error: Error) => void>(() => {});
  const loginAttemptRef = useRef(false);
  const loginWebViewRef = useRef<WebViewHandle | null>(null);
  const [loginPopupUrl, setLoginPopupUrl] = useState<string | null>(null);

  const setFetchingMoreState = (value: boolean) => {
    isFetchingMoreRef.current = value;
    setIsFetchingMore(value);
  };

  const updateCurrentIndex = (value: number) => {
    currentIndexRef.current = value;
    setCurrentIndex(value);
  };

  const handleQueueError = (queueError: Error) => {
    const message = queueError instanceof Error ? queueError.message : String(queueError);
    setSpeechError(`Speech error: ${message}`);
    setIsSpeaking(false);
  };

  const handleQueueDone = async () => {
    setIsSpeaking(false);
    const nextCursor = nextCursorRef.current;
    const paginator = paginatorRef.current;

    if (!nextCursor || !paginator || isFetchingMoreRef.current) {
      updateCurrentIndex(0);
      return;
    }

    const sessionId = speechSessionRef.current;
    const previousCount = itemsRef.current.length;
    setFetchingMoreState(true);
    setSpeechError(null);

    try {
      const page = await paginator.loadNext();
      if (sessionId !== speechSessionRef.current) {
        return;
      }

      const mergedItems = page.items;
      itemsRef.current = mergedItems;
      nextCursorRef.current = page.cursor;
      speechControllerRef.current?.updateItems(mergedItems);
      setItems(mergedItems);
      setMeta({
        itemsCount: mergedItems.length,
        rawCount: page.rawCount,
        cursor: page.cursor,
      });

      if (mergedItems.length <= previousCount) {
        setSpeechError('No additional items returned.');
        updateCurrentIndex(0);
        return;
      }

      const startIndex = Math.min(previousCount, mergedItems.length - 1);
      setIsSpeaking(true);
      speechControllerRef.current?.play(mergedItems, startIndex);
    } catch (err) {
      if (sessionId !== speechSessionRef.current) {
        return;
      }
      const message = err instanceof Error ? err.message : String(err);
      setSpeechError(`Auto-fetch failed: ${message}`);
      updateCurrentIndex(0);
    } finally {
      setFetchingMoreState(false);
    }
  };

  queueDoneRef.current = () => {
    void handleQueueDone();
  };

  queueErrorRef.current = (queueError: Error) => {
    handleQueueError(queueError);
  };

  if (!speechControllerRef.current) {
    speechControllerRef.current = new SpeechQueueController({
      engine: expoSpeechEngine,
      onIndexChange: (index) => updateCurrentIndex(index),
      onDone: () => queueDoneRef.current(),
      onError: (queueError) => queueErrorRef.current(queueError),
    });
  }

  useEffect(() => {
    let isMounted = true;

    const loadStoredAuth = async () => {
      const stored = await loadAuth('x');
      if (!isMounted || !stored) return;
      setXAuth(stored);
      setLoginState('success');
    };

    void loadStoredAuth();

    return () => {
      isMounted = false;
    };
  }, []);

  const resetSpeechSession = () => {
    speechSessionRef.current += 1;
    speechControllerRef.current?.stop();
    setIsSpeaking(false);
    setFetchingMoreState(false);
    setSpeechError(null);
    updateCurrentIndex(0);
  };

  const handleStopSpeech = () => {
    if (!isSpeaking && !isFetchingMoreRef.current) {
      return;
    }
    speechSessionRef.current += 1;
    speechControllerRef.current?.stop();
    setIsSpeaking(false);
    setFetchingMoreState(false);
  };

  const handleSourceSelect = (nextSource: FeedSource) => {
    if (!isSourceEnabled(nextSource) || nextSource === source) {
      return;
    }
    setSource(nextSource);
    setStatus('idle');
    setError(null);
    setMeta(null);
    setItems([]);
    itemsRef.current = [];
    nextCursorRef.current = undefined;
    paginatorRef.current = null;
    resetSpeechSession();
  };

  const handleOpenLogin = () => {
    if (source !== 'x') {
      return;
    }
    loginAttemptRef.current = false;
    setLoginPopupUrl(null);
    setLoginError(null);
    setLoginState('idle');
    setIsLoginVisible(true);
  };

  const handleCloseLogin = () => {
    setIsLoginVisible(false);
    setLoginPopupUrl(null);
    loginAttemptRef.current = false;
    setLoginState(xAuth ? 'success' : 'idle');
  };

  const closeLoginPopup = (shouldReload = false) => {
    setLoginPopupUrl(null);
    if (shouldReload) {
      loginWebViewRef.current?.reload();
    }
  };

  const relayPopupMessage = (data: unknown, origin?: string) => {
    const serializedData = data === undefined ? 'undefined' : JSON.stringify(data);
    const serializedOrigin = JSON.stringify(origin ?? '');

    loginWebViewRef.current?.injectJavaScript(`
      window.dispatchEvent(new MessageEvent('message', {
        data: ${serializedData},
        origin: ${serializedOrigin},
        source: window
      }));
      true;
    `);
  };

  const handleLoginOpenWindow = (event: { nativeEvent: { targetUrl: string } }) => {
    if (!event?.nativeEvent?.targetUrl) {
      return;
    }
    setLoginPopupUrl(event.nativeEvent.targetUrl);
  };

  const handleLoginPopupMessage = (event: { nativeEvent: { data: string } }) => {
    if (!event?.nativeEvent?.data) {
      return;
    }

    try {
      const payload = JSON.parse(event.nativeEvent.data) as {
        type?: string;
        data?: unknown;
        origin?: string;
        sourceOrigin?: string;
      };

      if (payload.type === LOGIN_POPUP_MESSAGE) {
        relayPopupMessage(payload.data, payload.sourceOrigin ?? payload.origin);
        closeLoginPopup(true);
        void tryFinalizeLogin();
        return;
      }

      if (payload.type === LOGIN_POPUP_CLOSE) {
        closeLoginPopup(true);
      }
    } catch {
      closeLoginPopup(true);
    }
  };

  const handleLoginPopupNavigation = (url: string) => {
    if (!url) {
      return;
    }

    if (isLoginComplete(url)) {
      closeLoginPopup(false);
      handleLoginNavigation(url);
    }
  };

  const completeLogin = async (auth: XAuth) => {
    setLoginState('in_progress');
    setLoginError(null);
    setXAuth(auth);
    await saveAuth('x', auth);
    setLoginState('success');
    setIsLoginVisible(false);
    setLoginPopupUrl(null);
    loginAttemptRef.current = false;
  };

  const finalizeLogin = async () => {
    try {
      const auth = await resolveXAuthFromCookies();
      await completeLogin(auth);
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      setLoginError(`Login failed: ${message}`);
      setLoginState('error');
      loginAttemptRef.current = false;
    }
  };

  const tryFinalizeLogin = async () => {
    if (loginAttemptRef.current) {
      return;
    }

    loginAttemptRef.current = true;

    try {
      const auth = await resolveXAuthFromCookies();
      await completeLogin(auth);
    } catch {
      loginAttemptRef.current = false;
    }
  };

  const handleLoginNavigation = (url: string) => {
    if (!url) {
      return;
    }
    if (!isLoginComplete(url) || loginAttemptRef.current) {
      return;
    }
    loginAttemptRef.current = true;
    void finalizeLogin();
  };

  const handleLogout = async () => {
    setStatus('idle');
    setError(null);
    setMeta(null);
    setItems([]);
    itemsRef.current = [];
    nextCursorRef.current = undefined;
    paginatorRef.current = null;
    resetSpeechSession();
    setXAuth(null);
    setLoginState('idle');
    setLoginError(null);
    loginAttemptRef.current = false;

    try {
      await clearAuth('x');
      await clearXCookies();
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      setLoginError(`Logout cleanup failed: ${message}`);
      setLoginState('error');
    }
  };

  const handleFetch = async () => {
    if (source !== 'x') {
      setError('This source is not supported yet.');
      setStatus('error');
      return;
    }

    if (!xAuth) {
      setError('Log in to X first.');
      setStatus('error');
      return;
    }

    resetSpeechSession();
    itemsRef.current = [];
    nextCursorRef.current = undefined;
    paginatorRef.current = null;

    setStatus('loading');
    setError(null);
    setItems([]);
    setMeta(null);
    fadeAnim.setValue(0);

    const auth: XAuth = xAuth;
    const paginator = new FeedPaginator({
      source: 'x',
      auth,
      count: TWEET_COUNT,
    });
    paginatorRef.current = paginator;

    try {
      const page = await paginator.loadInitial();
      itemsRef.current = page.items;
      nextCursorRef.current = page.cursor;
      speechControllerRef.current?.updateItems(page.items);
      setItems(page.items);
      setMeta({
        itemsCount: page.items.length,
        rawCount: page.rawCount,
        cursor: page.cursor,
      });
      setStatus('ready');

      Animated.timing(fadeAnim, {
        toValue: 1,
        duration: 450,
        useNativeDriver: true,
      }).start();
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      setError(message);
      setStatus('error');
      fadeAnim.setValue(1);
    }
  };

  const handlePlayAll = () => {
    const queueItems = itemsRef.current;
    if (status === 'loading' || queueItems.length === 0 || isSpeaking) {
      return;
    }
    setSpeechError(null);
    setFetchingMoreState(false);

    speechSessionRef.current += 1;
    setIsSpeaking(true);

    const startIndex = Math.max(0, Math.min(currentIndexRef.current, queueItems.length - 1));
    speechControllerRef.current?.play(queueItems, startIndex);
  };

  const handleResume = () => {
    const queueItems = itemsRef.current;
    if (status === 'loading' || queueItems.length === 0 || isSpeaking) {
      return;
    }
    setSpeechError(null);
    setFetchingMoreState(false);

    speechSessionRef.current += 1;
    setIsSpeaking(true);
    speechControllerRef.current?.updateItems(queueItems);
    speechControllerRef.current?.resume();
  };

  const hasItems = items.length > 0;
  const sourceLabel = SOURCE_LABELS[source];
  const isLoggedIn = Boolean(xAuth);
  const statusText =
    status === 'loading'
      ? `Fetching from ${sourceLabel}...`
      : status === 'ready'
        ? 'Feed loaded'
        : 'Idle';
  const canPlay = hasItems && !isSpeaking && status !== 'loading' && !isFetchingMore;
  const canResume =
    hasItems &&
    !isSpeaking &&
    status !== 'loading' &&
    currentIndexRef.current > 0 &&
    currentIndexRef.current < items.length &&
    !isFetchingMore;
  const canStop = isSpeaking || isFetchingMore;
  const spokenIndexLabel = hasItems ? Math.min(currentIndex + 1, items.length) : 0;
  const subtitleText =
    source === 'x'
      ? `Log in to X to fetch the first ${TWEET_COUNT} items.`
      : `Connect a source to fetch the first ${TWEET_COUNT} items.`;
  const loginStatusText =
    loginState === 'in_progress'
      ? 'Checking session...'
      : loginState === 'error' && !isLoggedIn
        ? 'Login error'
        : isLoggedIn
          ? 'Connected'
          : 'Not connected';
  const loginActionLabel = isLoggedIn ? 'Re-login' : 'Login to X';
  const canLogin = loginState !== 'in_progress';
  const canLogout = isLoggedIn && loginState !== 'in_progress';
  const canFetch = isLoggedIn && status !== 'loading';

  return (
    <LinearGradient
      colors={['#d5f3ef', '#e8f0fb', '#f7f9fd']}
      style={styles.background}
    >
      <StatusBar style="dark" />
      <SafeAreaView style={styles.safeArea}>
        <ScrollView contentContainerStyle={styles.scrollContent} keyboardShouldPersistTaps="handled">
          <View style={styles.header}>
            <Text style={styles.eyebrow}>ReadMyFeed</Text>
            <Text style={styles.title}>Home feed preview</Text>
            <Text style={styles.subtitle}>{subtitleText}</Text>
            <View style={styles.headerLine} />
          </View>

          <View style={styles.panel}>
            <View style={styles.sourceRow}>
              <Text style={styles.label}>Source</Text>
              <View style={styles.sourceOptions}>
                {SOURCE_OPTIONS.map((option) => {
                  const isEnabled = isSourceEnabled(option);
                  const isSelected = option === source;
                  return (
                    <Pressable
                      key={option}
                      onPress={() => handleSourceSelect(option)}
                      style={({ pressed }) => [
                        styles.sourceButton,
                        isSelected && styles.sourceButtonActive,
                        !isEnabled && styles.sourceButtonDisabled,
                        pressed && isEnabled && styles.sourceButtonPressed,
                      ]}
                      disabled={!isEnabled || status === 'loading'}
                    >
                      <Text
                        style={[
                          styles.sourceButtonText,
                          isSelected && styles.sourceButtonTextActive,
                          !isEnabled && styles.sourceButtonTextDisabled,
                        ]}
                      >
                        {SOURCE_LABELS[option]}
                      </Text>
                    </Pressable>
                  );
                })}
              </View>
            </View>

            <Text style={styles.label}>X session</Text>
            <View style={styles.authRow}>
              <View style={[styles.statusPill, styles.authStatusPill]}>
                <View
                  style={[
                    styles.statusDot,
                    isLoggedIn && styles.statusDotReady,
                    loginState === 'error' && styles.statusDotError,
                  ]}
                />
                <Text style={styles.statusText}>{loginStatusText}</Text>
              </View>
              <Pressable
                onPress={handleOpenLogin}
                style={({ pressed }) => [
                  styles.authButton,
                  styles.authButtonPrimary,
                  !canLogin && styles.authButtonDisabled,
                  pressed && canLogin && styles.authButtonPressedPrimary,
                ]}
                disabled={!canLogin}
              >
                <Text style={styles.authButtonText}>{loginActionLabel}</Text>
              </Pressable>
              {isLoggedIn ? (
                <Pressable
                  onPress={handleLogout}
                  style={({ pressed }) => [
                    styles.authButton,
                    styles.authButtonSecondary,
                    !canLogout && styles.authButtonDisabled,
                    pressed && canLogout && styles.authButtonPressedSecondary,
                  ]}
                  disabled={!canLogout}
                >
                  <Text style={styles.authButtonTextSecondary}>Log out</Text>
                </Pressable>
              ) : null}
            </View>

            {loginError ? <Text style={styles.errorText}>{loginError}</Text> : null}

            <View style={styles.buttonRow}>
              <Pressable
                onPress={handleFetch}
                style={({ pressed }) => [
                  styles.button,
                  !canFetch && styles.buttonDisabled,
                  pressed && canFetch && styles.buttonPressed,
                ]}
                disabled={!canFetch}
              >
                {status === 'loading' ? (
                  <ActivityIndicator color="white" />
                ) : (
                  <Text style={styles.buttonText}>Fetch items</Text>
                )}
              </Pressable>
              <View style={styles.statusPill}>
                <View
                  style={[
                    styles.statusDot,
                    status === 'ready' && styles.statusDotReady,
                    status === 'error' && styles.statusDotError,
                  ]}
                />
                <Text style={styles.statusText}>{statusText}</Text>
              </View>
            </View>

            <View style={styles.ttsRow}>
              <Pressable
                onPress={handlePlayAll}
                style={({ pressed }) => [
                  styles.ttsButton,
                  styles.ttsButtonPrimary,
                  !canPlay && styles.ttsButtonDisabled,
                  pressed && canPlay && styles.ttsButtonPressedPrimary,
                ]}
                disabled={!canPlay}
              >
                <Text style={styles.ttsButtonText}>Play all</Text>
              </Pressable>
              <Pressable
                onPress={handleResume}
                style={({ pressed }) => [
                  styles.ttsButton,
                  styles.ttsButtonSecondary,
                  !canResume && styles.ttsButtonDisabled,
                  pressed && canResume && styles.ttsButtonPressedSecondary,
                ]}
                disabled={!canResume}
              >
                <Text style={styles.ttsButtonTextSecondary}>Resume</Text>
              </Pressable>
              <Pressable
                onPress={handleStopSpeech}
                style={({ pressed }) => [
                  styles.ttsButton,
                  styles.ttsButtonSecondary,
                  !canStop && styles.ttsButtonDisabled,
                  pressed && canStop && styles.ttsButtonPressedSecondary,
                ]}
                disabled={!canStop}
              >
                <Text style={styles.ttsButtonTextSecondary}>Stop</Text>
              </Pressable>
            </View>

            {hasItems ? (
              <Text style={styles.ttsStatus}>
                {isSpeaking
                  ? `Reading ${spokenIndexLabel} of ${items.length}`
                  : `Ready to read ${items.length} items`}
              </Text>
            ) : null}

            {isFetchingMore ? <Text style={styles.ttsStatus}>Fetching more items...</Text> : null}

            {speechError ? <Text style={styles.errorText}>{speechError}</Text> : null}

            {error ? <Text style={styles.errorText}>{error}</Text> : null}

            {meta ? (
              <View style={styles.metaRow}>
                <Text style={styles.metaText}>Items: {meta.itemsCount}</Text>
                <Text style={styles.metaText}>
                  Raw: {meta.rawCount !== undefined ? meta.rawCount : 'n/a'}
                </Text>
                <Text style={styles.metaText} numberOfLines={1}>
                  Cursor: {meta.cursor ? 'set' : 'none'}
                </Text>
              </View>
            ) : null}
          </View>

          <Animated.View style={[styles.timeline, { opacity: fadeAnim }]}>
            <Text style={styles.sectionTitle}>Feed samples</Text>
            {hasItems ? (
              items.map((item, index) => (
                <View key={`${item.id}_${index}`} style={styles.tweetCard}>
                  <View style={styles.tweetHeader}>
                    <Text style={styles.tweetIndex}>{String(index + 1).padStart(2, '0')}</Text>
                    <View style={styles.tweetMeta}>
                      <Text style={styles.tweetUser}>{formatAuthor(item)}</Text>
                      <Text style={styles.tweetId} numberOfLines={1}>
                        {item.id || 'missing id'}
                      </Text>
                    </View>
                  </View>
                  <Text style={styles.tweetText}>{truncateText(item.text)}</Text>
                </View>
              ))
            ) : (
              <View style={styles.emptyState}>
                <Text style={styles.emptyTitle}>No items yet</Text>
                <Text style={styles.emptyBody}>Fetch the feed to see a preview here.</Text>
              </View>
            )}
          </Animated.View>
        </ScrollView>
        <Modal
          visible={isLoginVisible}
          animationType="slide"
          onRequestClose={handleCloseLogin}
        >
          <SafeAreaView style={styles.modalContainer}>
            <View style={styles.modalHeader}>
              <View>
                <Text style={styles.modalEyebrow}>X session</Text>
                <Text style={styles.modalTitle}>Sign in to X</Text>
              </View>
              <Pressable
                onPress={handleCloseLogin}
                style={({ pressed }) => [
                  styles.modalCloseButton,
                  pressed && styles.modalCloseButtonPressed,
                ]}
              >
                <Text style={styles.modalCloseText}>Close</Text>
              </Pressable>
            </View>
            {loginError && loginState === 'error' ? (
              <Text style={styles.modalErrorText}>{loginError}</Text>
            ) : null}
            <View style={styles.modalBody}>
              <LoginWebView
                ref={loginWebViewRef}
                source={{ uri: getXLoginUrl() }}
                onNavigationStateChange={(navState) => handleLoginNavigation(navState.url)}
                onLoadEnd={() => {
                  if (!loginPopupUrl) {
                    void tryFinalizeLogin();
                  }
                }}
                onOpenWindow={handleLoginOpenWindow}
                sharedCookiesEnabled
                thirdPartyCookiesEnabled
                javaScriptCanOpenWindowsAutomatically
                setSupportMultipleWindows
                startInLoadingState
                userAgent={X_LOGIN_USER_AGENT}
                style={styles.webView}
              />
              {loginPopupUrl ? (
                <View style={styles.loginPopup}>
                  <View style={styles.loginPopupHeader}>
                    <Text style={styles.loginPopupTitle}>Google sign-in</Text>
                    <Pressable
                      onPress={() => closeLoginPopup(false)}
                      style={({ pressed }) => [
                        styles.modalCloseButton,
                        pressed && styles.modalCloseButtonPressed,
                      ]}
                    >
                      <Text style={styles.modalCloseText}>Close</Text>
                    </Pressable>
                  </View>
                  <LoginWebView
                    source={{ uri: loginPopupUrl }}
                    onNavigationStateChange={(navState) =>
                      handleLoginPopupNavigation(navState.url)
                    }
                    onMessage={handleLoginPopupMessage}
                    injectedJavaScriptBeforeContentLoaded={LOGIN_POPUP_BRIDGE}
                    sharedCookiesEnabled
                    thirdPartyCookiesEnabled
                    javaScriptCanOpenWindowsAutomatically
                    setSupportMultipleWindows
                    startInLoadingState
                    userAgent={X_LOGIN_USER_AGENT}
                    style={styles.webView}
                  />
                </View>
              ) : null}
              {loginState === 'in_progress' ? (
                <View style={styles.modalOverlay}>
                  <ActivityIndicator color={theme.accent} />
                  <Text style={styles.modalOverlayText}>Finalizing login...</Text>
                </View>
              ) : null}
            </View>
          </SafeAreaView>
        </Modal>
      </SafeAreaView>
    </LinearGradient>
  );
}

const styles = StyleSheet.create({
  background: {
    flex: 1,
  },
  safeArea: {
    flex: 1,
  },
  scrollContent: {
    paddingHorizontal: 22,
    paddingVertical: 30,
    gap: 24,
  },
  header: {
    gap: 8,
  },
  eyebrow: {
    color: theme.accentStrong,
    textTransform: 'uppercase',
    letterSpacing: 1.6,
    fontSize: 11,
    fontFamily: 'sans-serif-medium',
    backgroundColor: theme.accentSoft,
    alignSelf: 'flex-start',
    paddingHorizontal: 12,
    paddingVertical: 4,
    borderRadius: 999,
  },
  title: {
    fontSize: 32,
    color: theme.ink,
    fontFamily: 'serif',
    letterSpacing: -0.3,
  },
  subtitle: {
    fontSize: 15,
    color: theme.inkSoft,
    lineHeight: 22,
  },
  headerLine: {
    width: 56,
    height: 3,
    backgroundColor: theme.accent,
    borderRadius: 999,
  },
  panel: {
    padding: 18,
    borderRadius: 20,
    backgroundColor: theme.surface,
    borderWidth: 1,
    borderColor: theme.border,
    gap: 14,
    shadowColor: theme.glow,
    shadowOpacity: 0.3,
    shadowRadius: 20,
    shadowOffset: { width: 0, height: 8 },
    elevation: 4,
  },
  label: {
    fontSize: 11,
    color: theme.inkSoft,
    textTransform: 'uppercase',
    letterSpacing: 1.4,
  },
  sourceRow: {
    gap: 8,
  },
  sourceOptions: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
  },
  sourceButton: {
    paddingVertical: 7,
    paddingHorizontal: 14,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: theme.border,
    backgroundColor: theme.surface,
  },
  sourceButtonActive: {
    backgroundColor: theme.accent,
    borderColor: theme.accent,
  },
  sourceButtonDisabled: {
    opacity: 0.45,
  },
  sourceButtonPressed: {
    backgroundColor: theme.accentSoft,
  },
  sourceButtonText: {
    fontSize: 12,
    color: theme.inkSoft,
    fontFamily: 'sans-serif-medium',
  },
  sourceButtonTextActive: {
    color: 'white',
  },
  sourceButtonTextDisabled: {
    color: theme.inkSoft,
  },
  authRow: {
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: 10,
  },
  authStatusPill: {
    flexGrow: 1,
    flexBasis: 160,
  },
  authButton: {
    paddingVertical: 10,
    paddingHorizontal: 14,
    borderRadius: 12,
    alignItems: 'center',
    minWidth: 120,
  },
  authButtonPrimary: {
    backgroundColor: theme.accent,
    shadowColor: theme.glow,
    shadowOpacity: 0.25,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 6 },
    elevation: 2,
  },
  authButtonSecondary: {
    backgroundColor: theme.surfaceMuted,
    borderWidth: 1,
    borderColor: theme.border,
  },
  authButtonDisabled: {
    opacity: 0.6,
  },
  authButtonPressedPrimary: {
    backgroundColor: theme.accentStrong,
  },
  authButtonPressedSecondary: {
    backgroundColor: theme.surface,
  },
  authButtonText: {
    color: 'white',
    fontSize: 13,
    fontFamily: 'sans-serif-medium',
  },
  authButtonTextSecondary: {
    color: theme.inkSoft,
    fontSize: 13,
    fontFamily: 'sans-serif-medium',
  },
  buttonRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
  },
  button: {
    backgroundColor: theme.accent,
    paddingVertical: 13,
    paddingHorizontal: 18,
    borderRadius: 16,
    minWidth: 140,
    alignItems: 'center',
    shadowColor: theme.glow,
    shadowOpacity: 0.35,
    shadowRadius: 14,
    shadowOffset: { width: 0, height: 6 },
    elevation: 3,
  },
  buttonDisabled: {
    opacity: 0.6,
  },
  buttonPressed: {
    backgroundColor: theme.accentStrong,
  },
  buttonText: {
    color: 'white',
    fontSize: 16,
    fontFamily: 'sans-serif-medium',
  },
  ttsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: 10,
  },
  ttsButton: {
    flex: 1,
    paddingVertical: 11,
    paddingHorizontal: 12,
    borderRadius: 14,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: 'transparent',
  },
  ttsButtonPrimary: {
    backgroundColor: theme.accent,
    shadowColor: theme.glow,
    shadowOpacity: 0.25,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 6 },
    elevation: 2,
  },
  ttsButtonSecondary: {
    backgroundColor: theme.surfaceMuted,
    borderColor: theme.border,
  },
  ttsButtonDisabled: {
    opacity: 0.55,
  },
  ttsButtonPressedPrimary: {
    backgroundColor: theme.accentStrong,
  },
  ttsButtonPressedSecondary: {
    backgroundColor: theme.surface,
  },
  ttsButtonText: {
    color: 'white',
    fontSize: 14,
    fontFamily: 'sans-serif-medium',
  },
  ttsButtonTextSecondary: {
    color: theme.inkSoft,
    fontSize: 14,
    fontFamily: 'sans-serif-medium',
  },
  ttsStatus: {
    fontSize: 12,
    color: theme.inkSoft,
  },
  statusPill: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: theme.surface,
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 999,
    gap: 6,
    flex: 1,
    borderWidth: 1,
    borderColor: theme.border,
  },
  statusDot: {
    width: 8,
    height: 8,
    borderRadius: 999,
    backgroundColor: theme.inkSoft,
  },
  statusDotReady: {
    backgroundColor: theme.accent,
  },
  statusDotError: {
    backgroundColor: theme.error,
  },
  statusText: {
    fontSize: 12,
    color: theme.inkSoft,
  },
  errorText: {
    color: theme.error,
    fontSize: 13,
  },
  metaRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  metaText: {
    fontSize: 12,
    color: theme.inkSoft,
  },
  modalContainer: {
    flex: 1,
    backgroundColor: theme.surface,
  },
  modalHeader: {
    paddingHorizontal: 22,
    paddingVertical: 16,
    borderBottomWidth: 1,
    borderBottomColor: theme.border,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  modalErrorText: {
    color: theme.error,
    fontSize: 12,
    paddingHorizontal: 22,
    paddingBottom: 8,
  },
  modalEyebrow: {
    color: theme.accentStrong,
    textTransform: 'uppercase',
    letterSpacing: 1.6,
    fontSize: 10,
    fontFamily: 'sans-serif-medium',
  },
  modalTitle: {
    fontSize: 20,
    color: theme.ink,
    fontFamily: 'serif',
    marginTop: 4,
  },
  modalCloseButton: {
    paddingVertical: 6,
    paddingHorizontal: 12,
    borderRadius: 999,
    backgroundColor: theme.surfaceMuted,
    borderWidth: 1,
    borderColor: theme.border,
  },
  modalCloseButtonPressed: {
    backgroundColor: theme.surface,
  },
  modalCloseText: {
    color: theme.inkSoft,
    fontSize: 12,
    fontFamily: 'sans-serif-medium',
  },
  modalBody: {
    flex: 1,
    backgroundColor: theme.surface,
  },
  loginPopup: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: theme.surface,
    zIndex: 2,
  },
  loginPopupHeader: {
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderBottomWidth: 1,
    borderBottomColor: theme.border,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  loginPopupTitle: {
    fontSize: 14,
    color: theme.ink,
    fontFamily: 'serif',
  },
  webView: {
    flex: 1,
    backgroundColor: 'white',
  },
  modalOverlay: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(255, 255, 255, 0.9)',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
  },
  modalOverlayText: {
    fontSize: 13,
    color: theme.inkSoft,
    fontFamily: 'sans-serif-medium',
  },
  timeline: {
    gap: 14,
  },
  sectionTitle: {
    fontSize: 17,
    color: theme.ink,
    fontFamily: 'serif',
  },
  tweetCard: {
    padding: 16,
    borderRadius: 18,
    backgroundColor: theme.surface,
    borderWidth: 1,
    borderColor: theme.border,
    gap: 10,
    shadowColor: theme.glow,
    shadowOpacity: 0.2,
    shadowRadius: 16,
    shadowOffset: { width: 0, height: 6 },
    elevation: 2,
  },
  tweetHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  tweetIndex: {
    fontSize: 12,
    color: theme.accentStrong,
    backgroundColor: theme.accentSoft,
    borderRadius: 999,
    paddingVertical: 4,
    paddingHorizontal: 10,
    overflow: 'hidden',
    fontFamily: 'sans-serif-medium',
  },
  tweetMeta: {
    flex: 1,
  },
  tweetUser: {
    fontSize: 16,
    color: theme.ink,
    fontFamily: 'sans-serif-medium',
  },
  tweetId: {
    fontSize: 11,
    color: theme.inkSoft,
  },
  tweetText: {
    fontSize: 14,
    lineHeight: 21,
    color: theme.inkSoft,
  },
  emptyState: {
    padding: 18,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: theme.border,
    backgroundColor: theme.surface,
    gap: 6,
  },
  emptyTitle: {
    fontSize: 15,
    color: theme.ink,
    fontFamily: 'serif',
  },
  emptyBody: {
    fontSize: 13,
    color: theme.inkSoft,
  },
});
