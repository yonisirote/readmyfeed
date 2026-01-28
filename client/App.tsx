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

// ============================================================================
// THEME - Dark Sleek with Electric Cyan
// ============================================================================

const theme = {
  // Backgrounds - deep charcoal with blue undertones
  bgDeep: '#06080c',
  bgPrimary: '#0a0d12',
  bgElevated: '#12161e',
  bgCard: '#181d28',
  bgCardHover: '#1e2433',

  // Accent - electric cyan
  accent: '#00d4ff',
  accentMuted: '#0099cc',
  accentDim: 'rgba(0, 212, 255, 0.15)',
  accentGlow: 'rgba(0, 212, 255, 0.4)',

  // Text hierarchy
  textPrimary: '#ffffff',
  textSecondary: '#9ca3af',
  textMuted: '#6b7280',
  textDisabled: '#4b5563',

  // Semantic
  error: '#ef4444',
  errorDim: 'rgba(239, 68, 68, 0.15)',
  success: '#22c55e',
  successDim: 'rgba(34, 197, 94, 0.15)',

  // Borders
  border: 'rgba(255, 255, 255, 0.06)',
  borderAccent: 'rgba(0, 212, 255, 0.3)',
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
type ScreenName = 'home' | 'login' | 'feed';

type FetchMeta = {
  itemsCount: number;
  rawCount?: number;
  cursor?: string;
};

const SOURCE_OPTIONS: FeedSource[] = ['x', 'facebook', 'telegram'];

const SOURCE_CONFIG: Record<FeedSource, { label: string; icon: string; enabled: boolean; tagline: string }> = {
  x: { label: 'X', icon: '𝕏', enabled: true, tagline: 'Posts from your timeline' },
  facebook: { label: 'Facebook', icon: 'f', enabled: false, tagline: 'Coming soon' },
  telegram: { label: 'Telegram', icon: '✈', enabled: false, tagline: 'Coming soon' },
};

const formatHandle = (handle?: string) => {
  if (!handle) return '@unknown';
  return handle.startsWith('@') ? handle : `@${handle}`;
};

const formatAuthor = (item: FeedItem) => {
  if (item.authorHandle) return formatHandle(item.authorHandle);
  if (item.authorName) return item.authorName;
  return 'Unknown author';
};

// ============================================================================
// MAIN APP COMPONENT
// ============================================================================

export default function App() {
  // Navigation state
  const [screen, setScreen] = useState<ScreenName>('home');
  const [source, setSource] = useState<FeedSource>('x');
  const screenFadeAnim = useRef(new Animated.Value(1)).current;

  // Auth state
  const [xAuth, setXAuth] = useState<XAuth | null>(null);
  const [loginState, setLoginState] = useState<XLoginState>('idle');
  const [loginError, setLoginError] = useState<string | null>(null);
  const [isLoginVisible, setIsLoginVisible] = useState(false);
  const [loginPopupUrl, setLoginPopupUrl] = useState<string | null>(null);
  const loginAttemptRef = useRef(false);
  const loginWebViewRef = useRef<WebViewHandle | null>(null);

  // Feed state
  const [status, setStatus] = useState<FetchStatus>('idle');
  const [error, setError] = useState<string | null>(null);
  const [items, setItems] = useState<FeedItem[]>([]);
  const [meta, setMeta] = useState<FetchMeta | null>(null);
  const itemsRef = useRef<FeedItem[]>([]);
  const paginatorRef = useRef<FeedPaginator | null>(null);
  const nextCursorRef = useRef<string | undefined>(undefined);

  // TTS state
  const [isSpeaking, setIsSpeaking] = useState(false);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [speechError, setSpeechError] = useState<string | null>(null);
  const [isFetchingMore, setIsFetchingMore] = useState(false);
  const currentIndexRef = useRef(0);
  const speechSessionRef = useRef(0);
  const isFetchingMoreRef = useRef(false);
  const speechControllerRef = useRef<SpeechQueueController | null>(null);
  const queueDoneRef = useRef<() => void>(() => {});
  const queueErrorRef = useRef<(error: Error) => void>(() => {});

  // ============================================================================
  // NAVIGATION HELPERS
  // ============================================================================

  const navigateTo = (nextScreen: ScreenName) => {
    Animated.timing(screenFadeAnim, {
      toValue: 0,
      duration: 150,
      useNativeDriver: true,
    }).start(() => {
      setScreen(nextScreen);
      Animated.timing(screenFadeAnim, {
        toValue: 1,
        duration: 200,
        useNativeDriver: true,
      }).start();
    });
  };

  const handleSourceSelect = (selectedSource: FeedSource) => {
    if (!SOURCE_CONFIG[selectedSource].enabled) return;
    
    setSource(selectedSource);
    
    if (selectedSource === 'x') {
      if (xAuth) {
        navigateTo('feed');
        // Auto-fetch if no items yet
        if (items.length === 0) {
          setTimeout(() => handleFetch(), 100);
        }
      } else {
        navigateTo('login');
        handleOpenLogin();
      }
    }
  };

  const handleBackToHome = () => {
    resetSpeechSession();
    navigateTo('home');
  };

  // ============================================================================
  // TTS HELPERS
  // ============================================================================

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

  // ============================================================================
  // AUTH EFFECTS AND HANDLERS
  // ============================================================================

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
    
    // If not logged in after closing, go back to home
    if (!xAuth && screen === 'login') {
      navigateTo('home');
    }
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
    
    // Navigate to feed after successful login
    navigateTo('feed');
    setTimeout(() => handleFetch(), 100);
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
    
    navigateTo('home');
  };

  // ============================================================================
  // FEED HANDLERS
  // ============================================================================

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
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      setError(message);
      setStatus('error');
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

  const handleSkipNext = () => {
    if (!isSpeaking || items.length === 0) return;
    
    const nextIndex = currentIndexRef.current + 1;
    if (nextIndex < items.length) {
      speechControllerRef.current?.stop();
      updateCurrentIndex(nextIndex);
      speechSessionRef.current += 1;
      setIsSpeaking(true);
      speechControllerRef.current?.play(itemsRef.current, nextIndex);
    }
  };

  const handleSkipPrev = () => {
    if (!isSpeaking || items.length === 0) return;
    
    const prevIndex = Math.max(0, currentIndexRef.current - 1);
    speechControllerRef.current?.stop();
    updateCurrentIndex(prevIndex);
    speechSessionRef.current += 1;
    setIsSpeaking(true);
    speechControllerRef.current?.play(itemsRef.current, prevIndex);
  };

  // ============================================================================
  // COMPUTED VALUES
  // ============================================================================

  const hasItems = items.length > 0;
  const currentItem = hasItems ? items[currentIndex] : null;
  const canPlay = hasItems && !isSpeaking && status !== 'loading' && !isFetchingMore;
  const canResume =
    hasItems &&
    !isSpeaking &&
    status !== 'loading' &&
    currentIndexRef.current > 0 &&
    currentIndexRef.current < items.length &&
    !isFetchingMore;
  const canStop = isSpeaking || isFetchingMore;
  const isLoggedIn = Boolean(xAuth);

  // ============================================================================
  // RENDER SCREENS
  // ============================================================================

  const renderHomeScreen = () => (
    <View style={styles.screenContainer}>
      {/* Header */}
      <View style={styles.homeHeader}>
        <Text style={styles.brandMark}>RMF</Text>
        <Text style={styles.homeTitle}>ReadMyFeed</Text>
        <Text style={styles.homeSubtitle}>
          Listen to your social feeds with text-to-speech
        </Text>
      </View>

      {/* Source Cards */}
      <View style={styles.sourceGrid}>
        <Text style={styles.sectionLabel}>SELECT A FEED</Text>
        
        {SOURCE_OPTIONS.map((sourceKey) => {
          const config = SOURCE_CONFIG[sourceKey];
          const isConnected = sourceKey === 'x' && isLoggedIn;
          
          return (
            <Pressable
              key={sourceKey}
              onPress={() => handleSourceSelect(sourceKey)}
              style={({ pressed }) => [
                styles.sourceCard,
                !config.enabled && styles.sourceCardDisabled,
                pressed && config.enabled && styles.sourceCardPressed,
              ]}
              disabled={!config.enabled}
            >
              <View style={styles.sourceCardContent}>
                <View style={[
                  styles.sourceIcon,
                  !config.enabled && styles.sourceIconDisabled,
                ]}>
                  <Text style={[
                    styles.sourceIconText,
                    !config.enabled && styles.sourceIconTextDisabled,
                  ]}>
                    {config.icon}
                  </Text>
                </View>
                
                <View style={styles.sourceInfo}>
                  <View style={styles.sourceNameRow}>
                    <Text style={[
                      styles.sourceName,
                      !config.enabled && styles.sourceNameDisabled,
                    ]}>
                      {config.label}
                    </Text>
                    {isConnected && (
                      <View style={styles.connectedBadge}>
                        <Text style={styles.connectedBadgeText}>Connected</Text>
                      </View>
                    )}
                    {!config.enabled && (
                      <View style={styles.comingSoonBadge}>
                        <Text style={styles.comingSoonBadgeText}>Soon</Text>
                      </View>
                    )}
                  </View>
                  <Text style={[
                    styles.sourceTagline,
                    !config.enabled && styles.sourceTaglineDisabled,
                  ]}>
                    {config.tagline}
                  </Text>
                </View>

                {config.enabled && (
                  <View style={styles.sourceArrow}>
                    <Text style={styles.sourceArrowText}>›</Text>
                  </View>
                )}
              </View>
            </Pressable>
          );
        })}
      </View>

      {/* Footer */}
      <View style={styles.homeFooter}>
        <Text style={styles.footerText}>
          More sources coming soon
        </Text>
      </View>
    </View>
  );

  const renderLoginScreen = () => (
    <View style={styles.screenContainer}>
      {/* Header */}
      <View style={styles.screenHeader}>
        <Pressable
          onPress={handleBackToHome}
          style={({ pressed }) => [
            styles.backButton,
            pressed && styles.backButtonPressed,
          ]}
        >
          <Text style={styles.backButtonText}>‹ Back</Text>
        </Pressable>
        
        <View style={styles.screenHeaderCenter}>
          <Text style={styles.screenTitle}>Connect to X</Text>
        </View>
        
        <View style={styles.headerSpacer} />
      </View>

      {/* Login Prompt */}
      <View style={styles.loginPrompt}>
        <View style={styles.loginIconLarge}>
          <Text style={styles.loginIconLargeText}>𝕏</Text>
        </View>
        <Text style={styles.loginPromptTitle}>Sign in to X</Text>
        <Text style={styles.loginPromptDesc}>
          Connect your X account to read your timeline aloud
        </Text>
        
        <Pressable
          onPress={handleOpenLogin}
          style={({ pressed }) => [
            styles.loginButton,
            pressed && styles.loginButtonPressed,
          ]}
        >
          <Text style={styles.loginButtonText}>Open X Login</Text>
        </Pressable>

        {loginError && (
          <View style={styles.errorBox}>
            <Text style={styles.errorBoxText}>{loginError}</Text>
          </View>
        )}
      </View>
    </View>
  );

  const renderFeedScreen = () => (
    <View style={styles.screenContainer}>
      {/* Header */}
      <View style={styles.screenHeader}>
        <Pressable
          onPress={handleBackToHome}
          style={({ pressed }) => [
            styles.backButton,
            pressed && styles.backButtonPressed,
          ]}
        >
          <Text style={styles.backButtonText}>‹ Back</Text>
        </Pressable>
        
        <View style={styles.screenHeaderCenter}>
          <View style={styles.feedHeaderBrand}>
            <Text style={styles.feedHeaderIcon}>𝕏</Text>
            <Text style={styles.screenTitle}>Timeline</Text>
          </View>
        </View>
        
        <Pressable
          onPress={handleLogout}
          style={({ pressed }) => [
            styles.logoutButton,
            pressed && styles.logoutButtonPressed,
          ]}
        >
          <Text style={styles.logoutButtonText}>Logout</Text>
        </Pressable>
      </View>

      {/* Main Content Area */}
      <View style={styles.feedContent}>
        {/* Status / Loading */}
        {status === 'loading' && (
          <View style={styles.loadingContainer}>
            <ActivityIndicator size="large" color={theme.accent} />
            <Text style={styles.loadingText}>Fetching your timeline...</Text>
          </View>
        )}

        {/* Error State */}
        {status === 'error' && error && (
          <View style={styles.errorContainer}>
            <Text style={styles.errorTitle}>Something went wrong</Text>
            <Text style={styles.errorMessage}>{error}</Text>
            <Pressable
              onPress={handleFetch}
              style={({ pressed }) => [
                styles.retryButton,
                pressed && styles.retryButtonPressed,
              ]}
            >
              <Text style={styles.retryButtonText}>Try Again</Text>
            </Pressable>
          </View>
        )}

        {/* Empty State */}
        {status === 'idle' && !hasItems && (
          <View style={styles.emptyContainer}>
            <Text style={styles.emptyIcon}>📡</Text>
            <Text style={styles.emptyTitle}>Ready to listen</Text>
            <Text style={styles.emptyDesc}>
              Tap the button below to fetch your timeline
            </Text>
            <Pressable
              onPress={handleFetch}
              style={({ pressed }) => [
                styles.fetchButton,
                pressed && styles.fetchButtonPressed,
              ]}
            >
              <Text style={styles.fetchButtonText}>Fetch Timeline</Text>
            </Pressable>
          </View>
        )}

        {/* Current Tweet Card - Now Playing Style */}
        {hasItems && currentItem && status === 'ready' && (
          <View style={styles.nowPlayingContainer}>
            {/* Progress Indicator */}
            <View style={styles.progressRow}>
              <Text style={styles.progressText}>
                {currentIndex + 1} of {items.length}
              </Text>
              {isFetchingMore && (
                <Text style={styles.fetchingMoreText}>Loading more...</Text>
              )}
            </View>

            {/* Tweet Card */}
            <View style={[
              styles.tweetCard,
              isSpeaking && styles.tweetCardActive,
            ]}>
              {/* Author Row */}
              <View style={styles.tweetAuthorRow}>
                <View style={styles.tweetAvatar}>
                  <Text style={styles.tweetAvatarText}>
                    {(currentItem.authorHandle || currentItem.authorName || 'U')[0].toUpperCase()}
                  </Text>
                </View>
                <View style={styles.tweetAuthorInfo}>
                  <Text style={styles.tweetAuthorName}>
                    {currentItem.authorName || 'Unknown'}
                  </Text>
                  <Text style={styles.tweetAuthorHandle}>
                    {formatAuthor(currentItem)}
                  </Text>
                </View>
                {isSpeaking && (
                  <View style={styles.speakingIndicator}>
                    <View style={styles.speakingDot} />
                    <View style={[styles.speakingDot, styles.speakingDotDelayed]} />
                    <View style={[styles.speakingDot, styles.speakingDotDelayed2]} />
                  </View>
                )}
              </View>

              {/* Tweet Text */}
              <Text style={styles.tweetText}>
                {currentItem.text || 'No content'}
              </Text>
            </View>

            {/* Playback Controls */}
            <View style={styles.controlsContainer}>
              {/* Secondary Controls Row */}
              <View style={styles.secondaryControls}>
                <Pressable
                  onPress={handleSkipPrev}
                  style={({ pressed }) => [
                    styles.skipButton,
                    (!isSpeaking || currentIndex === 0) && styles.controlDisabled,
                    pressed && isSpeaking && styles.skipButtonPressed,
                  ]}
                  disabled={!isSpeaking || currentIndex === 0}
                >
                  <Text style={styles.skipButtonText}>⟨⟨</Text>
                </Pressable>

                {/* Main Play/Stop Button */}
                {!isSpeaking ? (
                  <Pressable
                    onPress={canResume ? handleResume : handlePlayAll}
                    style={({ pressed }) => [
                      styles.playButton,
                      !canPlay && !canResume && styles.controlDisabled,
                      pressed && (canPlay || canResume) && styles.playButtonPressed,
                    ]}
                    disabled={!canPlay && !canResume}
                  >
                    <Text style={styles.playButtonText}>▶</Text>
                  </Pressable>
                ) : (
                  <Pressable
                    onPress={handleStopSpeech}
                    style={({ pressed }) => [
                      styles.stopButton,
                      pressed && styles.stopButtonPressed,
                    ]}
                  >
                    <Text style={styles.stopButtonText}>■</Text>
                  </Pressable>
                )}

                <Pressable
                  onPress={handleSkipNext}
                  style={({ pressed }) => [
                    styles.skipButton,
                    (!isSpeaking || currentIndex >= items.length - 1) && styles.controlDisabled,
                    pressed && isSpeaking && styles.skipButtonPressed,
                  ]}
                  disabled={!isSpeaking || currentIndex >= items.length - 1}
                >
                  <Text style={styles.skipButtonText}>⟩⟩</Text>
                </Pressable>
              </View>

              {/* Status Text */}
              <Text style={styles.playbackStatus}>
                {isSpeaking
                  ? 'Now reading...'
                  : canResume
                    ? 'Tap to resume'
                    : 'Tap to start reading'}
              </Text>
            </View>

            {/* Refresh Button */}
            <Pressable
              onPress={handleFetch}
              style={({ pressed }) => [
                styles.refreshButton,
                isFetchingMore && styles.controlDisabled,
                pressed && !isFetchingMore && styles.refreshButtonPressed,
              ]}
              disabled={isFetchingMore}
            >
              <Text style={styles.refreshButtonText}>↻ Refresh Feed</Text>
            </Pressable>

            {/* Speech Error */}
            {speechError && (
              <View style={styles.speechErrorBox}>
                <Text style={styles.speechErrorText}>{speechError}</Text>
              </View>
            )}

            {/* Meta Info */}
            {meta && (
              <View style={styles.metaRow}>
                <Text style={styles.metaText}>
                  {meta.itemsCount} items loaded
                  {meta.cursor ? ' • More available' : ''}
                </Text>
              </View>
            )}
          </View>
        )}
      </View>
    </View>
  );

  // ============================================================================
  // MAIN RENDER
  // ============================================================================

  return (
    <LinearGradient
      colors={[theme.bgDeep, theme.bgPrimary, theme.bgElevated]}
      style={styles.background}
    >
      <StatusBar style="light" />
      <SafeAreaView style={styles.safeArea}>
        <Animated.View style={[styles.animatedContainer, { opacity: screenFadeAnim }]}>
          {screen === 'home' && renderHomeScreen()}
          {screen === 'login' && renderLoginScreen()}
          {screen === 'feed' && renderFeedScreen()}
        </Animated.View>

        {/* Login Modal - Preserved from original */}
        <Modal
          visible={isLoginVisible}
          animationType="slide"
          onRequestClose={handleCloseLogin}
        >
          <SafeAreaView style={styles.modalContainer}>
            <View style={styles.modalHeader}>
              <View>
                <Text style={styles.modalEyebrow}>X SESSION</Text>
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

// ============================================================================
// STYLES
// ============================================================================

const styles = StyleSheet.create({
  // Layout
  background: {
    flex: 1,
  },
  safeArea: {
    flex: 1,
  },
  animatedContainer: {
    flex: 1,
  },
  screenContainer: {
    flex: 1,
    paddingHorizontal: 20,
  },

  // ============================================================================
  // HOME SCREEN
  // ============================================================================
  
  homeHeader: {
    paddingTop: 40,
    paddingBottom: 32,
    alignItems: 'center',
  },
  brandMark: {
    fontSize: 14,
    fontWeight: '700',
    color: theme.accent,
    letterSpacing: 4,
    marginBottom: 16,
  },
  homeTitle: {
    fontSize: 36,
    fontWeight: '300',
    color: theme.textPrimary,
    letterSpacing: -0.5,
    marginBottom: 8,
  },
  homeSubtitle: {
    fontSize: 15,
    color: theme.textSecondary,
    textAlign: 'center',
    lineHeight: 22,
  },

  // Source Grid
  sourceGrid: {
    flex: 1,
    gap: 12,
  },
  sectionLabel: {
    fontSize: 11,
    fontWeight: '600',
    color: theme.textMuted,
    letterSpacing: 1.5,
    marginBottom: 8,
    marginLeft: 4,
  },
  sourceCard: {
    backgroundColor: theme.bgCard,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: theme.border,
    overflow: 'hidden',
  },
  sourceCardDisabled: {
    opacity: 0.5,
  },
  sourceCardPressed: {
    backgroundColor: theme.bgCardHover,
    borderColor: theme.borderAccent,
  },
  sourceCardContent: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
    gap: 16,
  },
  sourceIcon: {
    width: 52,
    height: 52,
    borderRadius: 12,
    backgroundColor: theme.accentDim,
    alignItems: 'center',
    justifyContent: 'center',
  },
  sourceIconDisabled: {
    backgroundColor: theme.bgElevated,
  },
  sourceIconText: {
    fontSize: 24,
    color: theme.accent,
    fontWeight: '700',
  },
  sourceIconTextDisabled: {
    color: theme.textDisabled,
  },
  sourceInfo: {
    flex: 1,
    gap: 4,
  },
  sourceNameRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  sourceName: {
    fontSize: 18,
    fontWeight: '600',
    color: theme.textPrimary,
  },
  sourceNameDisabled: {
    color: theme.textDisabled,
  },
  sourceTagline: {
    fontSize: 13,
    color: theme.textSecondary,
  },
  sourceTaglineDisabled: {
    color: theme.textDisabled,
  },
  connectedBadge: {
    backgroundColor: theme.successDim,
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
  },
  connectedBadgeText: {
    fontSize: 10,
    fontWeight: '600',
    color: theme.success,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  comingSoonBadge: {
    backgroundColor: theme.bgElevated,
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
  },
  comingSoonBadgeText: {
    fontSize: 10,
    fontWeight: '600',
    color: theme.textMuted,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  sourceArrow: {
    width: 32,
    height: 32,
    alignItems: 'center',
    justifyContent: 'center',
  },
  sourceArrowText: {
    fontSize: 24,
    color: theme.textMuted,
    fontWeight: '300',
  },

  // Footer
  homeFooter: {
    paddingVertical: 24,
    alignItems: 'center',
  },
  footerText: {
    fontSize: 12,
    color: theme.textMuted,
  },

  // ============================================================================
  // SCREEN HEADER (shared)
  // ============================================================================
  
  screenHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 16,
    borderBottomWidth: 1,
    borderBottomColor: theme.border,
    marginBottom: 16,
  },
  screenHeaderCenter: {
    flex: 1,
    alignItems: 'center',
  },
  screenTitle: {
    fontSize: 17,
    fontWeight: '600',
    color: theme.textPrimary,
  },
  headerSpacer: {
    width: 60,
  },
  backButton: {
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: 8,
    backgroundColor: theme.bgCard,
  },
  backButtonPressed: {
    backgroundColor: theme.bgCardHover,
  },
  backButtonText: {
    fontSize: 15,
    color: theme.textSecondary,
    fontWeight: '500',
  },

  // ============================================================================
  // LOGIN SCREEN
  // ============================================================================
  
  loginPrompt: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 24,
    gap: 16,
  },
  loginIconLarge: {
    width: 88,
    height: 88,
    borderRadius: 22,
    backgroundColor: theme.accentDim,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 8,
  },
  loginIconLargeText: {
    fontSize: 44,
    color: theme.accent,
    fontWeight: '700',
  },
  loginPromptTitle: {
    fontSize: 26,
    fontWeight: '600',
    color: theme.textPrimary,
  },
  loginPromptDesc: {
    fontSize: 15,
    color: theme.textSecondary,
    textAlign: 'center',
    lineHeight: 22,
    marginBottom: 8,
  },
  loginButton: {
    backgroundColor: theme.accent,
    paddingVertical: 16,
    paddingHorizontal: 48,
    borderRadius: 14,
    marginTop: 8,
  },
  loginButtonPressed: {
    backgroundColor: theme.accentMuted,
  },
  loginButtonText: {
    fontSize: 16,
    fontWeight: '600',
    color: theme.bgDeep,
  },
  errorBox: {
    backgroundColor: theme.errorDim,
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 10,
    marginTop: 16,
  },
  errorBoxText: {
    fontSize: 13,
    color: theme.error,
    textAlign: 'center',
  },

  // ============================================================================
  // FEED SCREEN
  // ============================================================================
  
  feedHeaderBrand: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  feedHeaderIcon: {
    fontSize: 18,
    color: theme.accent,
    fontWeight: '700',
  },
  logoutButton: {
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: 8,
    backgroundColor: theme.bgCard,
  },
  logoutButtonPressed: {
    backgroundColor: theme.bgCardHover,
  },
  logoutButtonText: {
    fontSize: 13,
    color: theme.textMuted,
    fontWeight: '500',
  },

  feedContent: {
    flex: 1,
  },

  // Loading State
  loadingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 16,
  },
  loadingText: {
    fontSize: 15,
    color: theme.textSecondary,
  },

  // Error State
  errorContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
    paddingHorizontal: 24,
  },
  errorTitle: {
    fontSize: 20,
    fontWeight: '600',
    color: theme.textPrimary,
  },
  errorMessage: {
    fontSize: 14,
    color: theme.error,
    textAlign: 'center',
    lineHeight: 20,
  },
  retryButton: {
    backgroundColor: theme.bgCard,
    paddingVertical: 12,
    paddingHorizontal: 24,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: theme.border,
    marginTop: 8,
  },
  retryButtonPressed: {
    backgroundColor: theme.bgCardHover,
  },
  retryButtonText: {
    fontSize: 14,
    fontWeight: '500',
    color: theme.textPrimary,
  },

  // Empty State
  emptyContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
    paddingHorizontal: 24,
  },
  emptyIcon: {
    fontSize: 48,
    marginBottom: 8,
  },
  emptyTitle: {
    fontSize: 22,
    fontWeight: '600',
    color: theme.textPrimary,
  },
  emptyDesc: {
    fontSize: 15,
    color: theme.textSecondary,
    textAlign: 'center',
    lineHeight: 22,
  },
  fetchButton: {
    backgroundColor: theme.accent,
    paddingVertical: 14,
    paddingHorizontal: 32,
    borderRadius: 12,
    marginTop: 8,
  },
  fetchButtonPressed: {
    backgroundColor: theme.accentMuted,
  },
  fetchButtonText: {
    fontSize: 15,
    fontWeight: '600',
    color: theme.bgDeep,
  },

  // Now Playing Container
  nowPlayingContainer: {
    flex: 1,
    gap: 20,
  },
  progressRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  progressText: {
    fontSize: 13,
    fontWeight: '600',
    color: theme.accent,
    letterSpacing: 0.5,
  },
  fetchingMoreText: {
    fontSize: 12,
    color: theme.textMuted,
  },

  // Tweet Card
  tweetCard: {
    backgroundColor: theme.bgCard,
    borderRadius: 20,
    padding: 24,
    borderWidth: 1,
    borderColor: theme.border,
    gap: 20,
    minHeight: 200,
  },
  tweetCardActive: {
    borderColor: theme.accent,
    shadowColor: theme.accent,
    shadowOpacity: 0.2,
    shadowRadius: 20,
    shadowOffset: { width: 0, height: 4 },
    elevation: 8,
  },
  tweetAuthorRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
  },
  tweetAvatar: {
    width: 48,
    height: 48,
    borderRadius: 24,
    backgroundColor: theme.accentDim,
    alignItems: 'center',
    justifyContent: 'center',
  },
  tweetAvatarText: {
    fontSize: 20,
    fontWeight: '700',
    color: theme.accent,
  },
  tweetAuthorInfo: {
    flex: 1,
    gap: 2,
  },
  tweetAuthorName: {
    fontSize: 17,
    fontWeight: '600',
    color: theme.textPrimary,
  },
  tweetAuthorHandle: {
    fontSize: 14,
    color: theme.textMuted,
  },
  speakingIndicator: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  speakingDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: theme.accent,
    opacity: 0.8,
  },
  speakingDotDelayed: {
    opacity: 0.5,
  },
  speakingDotDelayed2: {
    opacity: 0.3,
  },
  tweetText: {
    fontSize: 18,
    lineHeight: 28,
    color: theme.textPrimary,
    fontWeight: '400',
  },

  // Playback Controls
  controlsContainer: {
    alignItems: 'center',
    gap: 12,
    paddingVertical: 8,
  },
  secondaryControls: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 24,
  },
  skipButton: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: theme.bgCard,
    borderWidth: 1,
    borderColor: theme.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  skipButtonPressed: {
    backgroundColor: theme.bgCardHover,
  },
  skipButtonText: {
    fontSize: 18,
    color: theme.textSecondary,
    fontWeight: '600',
  },
  playButton: {
    width: 80,
    height: 80,
    borderRadius: 40,
    backgroundColor: theme.accent,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: theme.accent,
    shadowOpacity: 0.4,
    shadowRadius: 16,
    shadowOffset: { width: 0, height: 4 },
    elevation: 8,
  },
  playButtonPressed: {
    backgroundColor: theme.accentMuted,
  },
  playButtonText: {
    fontSize: 28,
    color: theme.bgDeep,
    marginLeft: 4,
  },
  stopButton: {
    width: 80,
    height: 80,
    borderRadius: 40,
    backgroundColor: theme.bgCard,
    borderWidth: 2,
    borderColor: theme.accent,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stopButtonPressed: {
    backgroundColor: theme.bgCardHover,
  },
  stopButtonText: {
    fontSize: 22,
    color: theme.accent,
  },
  controlDisabled: {
    opacity: 0.4,
  },
  playbackStatus: {
    fontSize: 13,
    color: theme.textMuted,
  },

  // Refresh Button
  refreshButton: {
    alignSelf: 'center',
    paddingVertical: 12,
    paddingHorizontal: 20,
    borderRadius: 10,
    backgroundColor: theme.bgCard,
    borderWidth: 1,
    borderColor: theme.border,
  },
  refreshButtonPressed: {
    backgroundColor: theme.bgCardHover,
    borderColor: theme.borderAccent,
  },
  refreshButtonText: {
    fontSize: 14,
    color: theme.textSecondary,
    fontWeight: '500',
  },

  // Speech Error
  speechErrorBox: {
    backgroundColor: theme.errorDim,
    paddingVertical: 10,
    paddingHorizontal: 14,
    borderRadius: 8,
  },
  speechErrorText: {
    fontSize: 13,
    color: theme.error,
    textAlign: 'center',
  },

  // Meta
  metaRow: {
    alignItems: 'center',
  },
  metaText: {
    fontSize: 12,
    color: theme.textMuted,
  },

  // ============================================================================
  // LOGIN MODAL (preserved with dark styling)
  // ============================================================================
  
  modalContainer: {
    flex: 1,
    backgroundColor: theme.bgPrimary,
  },
  modalHeader: {
    paddingHorizontal: 20,
    paddingVertical: 16,
    borderBottomWidth: 1,
    borderBottomColor: theme.border,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: theme.bgElevated,
  },
  modalErrorText: {
    color: theme.error,
    fontSize: 12,
    paddingHorizontal: 20,
    paddingVertical: 8,
    backgroundColor: theme.errorDim,
  },
  modalEyebrow: {
    color: theme.accent,
    fontSize: 10,
    fontWeight: '600',
    letterSpacing: 1.5,
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: theme.textPrimary,
    marginTop: 4,
  },
  modalCloseButton: {
    paddingVertical: 8,
    paddingHorizontal: 14,
    borderRadius: 8,
    backgroundColor: theme.bgCard,
    borderWidth: 1,
    borderColor: theme.border,
  },
  modalCloseButtonPressed: {
    backgroundColor: theme.bgCardHover,
  },
  modalCloseText: {
    color: theme.textSecondary,
    fontSize: 13,
    fontWeight: '500',
  },
  modalBody: {
    flex: 1,
    backgroundColor: theme.bgPrimary,
  },
  loginPopup: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: theme.bgPrimary,
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
    backgroundColor: theme.bgElevated,
  },
  loginPopupTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: theme.textPrimary,
  },
  webView: {
    flex: 1,
    backgroundColor: '#ffffff',
  },
  modalOverlay: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(6, 8, 12, 0.95)',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
  },
  modalOverlayText: {
    fontSize: 14,
    color: theme.textSecondary,
    fontWeight: '500',
  },
});
