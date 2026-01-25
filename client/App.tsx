import './src/polyfills/native';

import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Animated,
  Pressable,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';

import { loadAuth, saveAuth } from './src/services/auth/authStore';
import { FeedPaginator } from './src/services/feed/feedPaginator';
import type { FeedItem, FeedSource, XAuth } from './src/services/feed/feedTypes';
import { expoSpeechEngine } from './src/services/tts/expoSpeechEngine';
import { SpeechQueueController } from './src/services/tts/speechQueueController';

const TWEET_COUNT = 5;

const theme = {
  ink: '#16212e',
  inkSoft: '#2f3e4f',
  accent: '#c96a2a',
  accentStrong: '#f08b3f',
  surface: 'rgba(255, 255, 255, 0.92)',
  surfaceMuted: 'rgba(255, 255, 255, 0.72)',
  border: 'rgba(22, 33, 46, 0.14)',
  error: '#9b3a34',
};

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
  const [apiKey, setApiKey] = useState('');
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
      if (!isMounted || !stored?.apiKey) return;
      setApiKey(stored.apiKey);
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

  const handleFetch = async () => {
    if (source !== 'x') {
      setError('This source is not supported yet.');
      setStatus('error');
      return;
    }

    const trimmedKey = apiKey.trim();
    if (!trimmedKey) {
      setError('Paste a valid API key first.');
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

    const auth: XAuth = { apiKey: trimmedKey };
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

      void saveAuth('x', auth).catch(() => undefined);

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
      ? `Paste your X Auth Helper API key to fetch the first ${TWEET_COUNT} items.`
      : `Connect a source to fetch the first ${TWEET_COUNT} items.`;
  const authLabel = source === 'x' ? 'X API key' : 'API key';

  return (
    <LinearGradient colors={['#f2d8c4', '#e7eef8']} style={styles.background}>
      <StatusBar style="dark" />
      <SafeAreaView style={styles.safeArea}>
        <ScrollView contentContainerStyle={styles.scrollContent} keyboardShouldPersistTaps="handled">
          <View style={styles.header}>
            <Text style={styles.eyebrow}>ReadMyFeed</Text>
            <Text style={styles.title}>Home feed preview</Text>
            <Text style={styles.subtitle}>{subtitleText}</Text>
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

            <Text style={styles.label}>{authLabel}</Text>
            <TextInput
              multiline
              placeholder="Paste your key"
              value={apiKey}
              onChangeText={setApiKey}
              style={styles.input}
              autoCapitalize="none"
              autoCorrect={false}
              spellCheck={false}
              editable={status !== 'loading'}
            />

            <View style={styles.buttonRow}>
              <Pressable
                onPress={handleFetch}
                style={({ pressed }) => [
                  styles.button,
                  (!apiKey.trim() || status === 'loading') && styles.buttonDisabled,
                  pressed && styles.buttonPressed,
                ]}
                disabled={!apiKey.trim() || status === 'loading'}
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
    paddingHorizontal: 20,
    paddingVertical: 28,
    gap: 22,
  },
  header: {
    gap: 6,
  },
  eyebrow: {
    color: theme.accent,
    textTransform: 'uppercase',
    letterSpacing: 1.2,
    fontSize: 12,
    fontFamily: 'sans-serif-medium',
  },
  title: {
    fontSize: 30,
    color: theme.ink,
    fontFamily: 'serif',
  },
  subtitle: {
    fontSize: 15,
    color: theme.inkSoft,
    lineHeight: 22,
  },
  panel: {
    padding: 16,
    borderRadius: 18,
    backgroundColor: theme.surface,
    borderWidth: 1,
    borderColor: theme.border,
    gap: 12,
    shadowColor: '#000',
    shadowOpacity: 0.08,
    shadowRadius: 18,
    shadowOffset: { width: 0, height: 6 },
    elevation: 3,
  },
  label: {
    fontSize: 13,
    color: theme.inkSoft,
    textTransform: 'uppercase',
    letterSpacing: 1.1,
  },
  sourceRow: {
    gap: 8,
  },
  sourceOptions: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  sourceButton: {
    paddingVertical: 6,
    paddingHorizontal: 12,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: theme.border,
    backgroundColor: theme.surfaceMuted,
  },
  sourceButtonActive: {
    backgroundColor: theme.ink,
    borderColor: theme.ink,
  },
  sourceButtonDisabled: {
    opacity: 0.45,
  },
  sourceButtonPressed: {
    backgroundColor: theme.surface,
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
  input: {
    minHeight: 90,
    borderWidth: 1,
    borderColor: theme.border,
    borderRadius: 12,
    paddingHorizontal: 12,
    paddingVertical: 10,
    backgroundColor: theme.surfaceMuted,
    color: theme.ink,
    fontSize: 14,
    fontFamily: 'monospace',
  },
  buttonRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
  },
  button: {
    backgroundColor: theme.accent,
    paddingVertical: 12,
    paddingHorizontal: 18,
    borderRadius: 14,
    minWidth: 140,
    alignItems: 'center',
  },
  buttonDisabled: {
    opacity: 0.6,
  },
  buttonPressed: {
    backgroundColor: theme.accentStrong,
  },
  buttonText: {
    color: 'white',
    fontSize: 15,
    fontFamily: 'sans-serif-medium',
  },
  ttsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: 12,
  },
  ttsButton: {
    flex: 1,
    paddingVertical: 10,
    paddingHorizontal: 12,
    borderRadius: 12,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: 'transparent',
  },
  ttsButtonPrimary: {
    backgroundColor: theme.accent,
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
    backgroundColor: theme.surfaceMuted,
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 999,
    gap: 6,
    flex: 1,
  },
  statusDot: {
    width: 8,
    height: 8,
    borderRadius: 999,
    backgroundColor: theme.inkSoft,
  },
  statusDotReady: {
    backgroundColor: '#2f7d4d',
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
    gap: 10,
  },
  metaText: {
    fontSize: 12,
    color: theme.inkSoft,
  },
  timeline: {
    gap: 12,
  },
  sectionTitle: {
    fontSize: 18,
    color: theme.ink,
    fontFamily: 'serif',
  },
  tweetCard: {
    padding: 14,
    borderRadius: 16,
    backgroundColor: theme.surface,
    borderWidth: 1,
    borderColor: theme.border,
    gap: 10,
  },
  tweetHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  tweetIndex: {
    fontSize: 12,
    color: theme.inkSoft,
    backgroundColor: theme.surfaceMuted,
    borderRadius: 999,
    paddingVertical: 4,
    paddingHorizontal: 10,
    overflow: 'hidden',
  },
  tweetMeta: {
    flex: 1,
  },
  tweetUser: {
    fontSize: 15,
    color: theme.ink,
    fontFamily: 'sans-serif-medium',
  },
  tweetId: {
    fontSize: 11,
    color: theme.inkSoft,
  },
  tweetText: {
    fontSize: 14,
    lineHeight: 20,
    color: theme.inkSoft,
  },
  emptyState: {
    padding: 18,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: theme.border,
    backgroundColor: theme.surfaceMuted,
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
