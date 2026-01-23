import './src/polyfills/native';

import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useRef, useState } from 'react';
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

import { buildTweetSpeechText, speakText, stopSpeech } from './src/services/tts/ttsService';
import { fetchXHomeTimeline, type XHomeTimelineTweetSample } from './src/services/xHome/xHomeTimeline';

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
  entriesCount: number;
  tweetsCount: number;
  nextCursor?: string;
};

const formatHandle = (handle?: string) => {
  if (!handle) return '@unknown';
  return handle.startsWith('@') ? handle : `@${handle}`;
};

const truncateText = (text?: string, maxLen = 220) => {
  if (!text) return '';
  if (text.length <= maxLen) return text;
  return `${text.slice(0, maxLen).trim()}…`;
};

const logTimeline = (message: string) => {
  console.log(`[rmf] ${message}`);
};

const mergeTweetSamples = (
  current: XHomeTimelineTweetSample[],
  incoming: XHomeTimelineTweetSample[],
): XHomeTimelineTweetSample[] => {
  const seen = new Set(current.map((tweet) => tweet.id));
  const merged = [...current];
  for (const tweet of incoming) {
    if (!tweet.id || seen.has(tweet.id)) {
      continue;
    }
    merged.push(tweet);
    seen.add(tweet.id);
  }
  return merged;
};

export default function App() {
  const [apiKey, setApiKey] = useState('');
  const [status, setStatus] = useState<FetchStatus>('idle');
  const [error, setError] = useState<string | null>(null);
  const [tweets, setTweets] = useState<XHomeTimelineTweetSample[]>([]);
  const [meta, setMeta] = useState<FetchMeta | null>(null);
  const [isSpeaking, setIsSpeaking] = useState(false);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [speechError, setSpeechError] = useState<string | null>(null);
  const [isFetchingMore, setIsFetchingMore] = useState(false);
  const fadeAnim = useRef(new Animated.Value(1)).current;
  const tweetsRef = useRef<XHomeTimelineTweetSample[]>([]);
  const apiKeyRef = useRef<string | null>(null);
  const nextCursorRef = useRef<string | undefined>(undefined);
  const currentIndexRef = useRef(0);
  const speechSessionRef = useRef(0);
  const isFetchingMoreRef = useRef(false);

  const setFetchingMoreState = (value: boolean) => {
    isFetchingMoreRef.current = value;
    setIsFetchingMore(value);
  };

  const updateCurrentIndex = (value: number) => {
    currentIndexRef.current = value;
    setCurrentIndex(value);
  };

  const endSpeechSession = (sessionId: number, resetIndex: boolean) => {
    if (sessionId !== speechSessionRef.current) return;
    setIsSpeaking(false);
    setFetchingMoreState(false);
    if (resetIndex) {
      updateCurrentIndex(0);
    }
  };

  const handleStopSpeech = () => {
    if (!isSpeaking && !isFetchingMoreRef.current) {
      return;
    }
    speechSessionRef.current += 1;
    stopSpeech();
    setIsSpeaking(false);
    setFetchingMoreState(false);
  };

  function advanceSpeechQueue(index: number, sessionId: number) {
    if (sessionId !== speechSessionRef.current) return;
    const nextIndex = index + 1;
    currentIndexRef.current = nextIndex;
    const items = tweetsRef.current;
    if (nextIndex < items.length) {
      speakTweetAtIndex(nextIndex, sessionId);
      return;
    }
    const nextCursor = nextCursorRef.current;
    if (!nextCursor) {
      endSpeechSession(sessionId, true);
      return;
    }
    if (isFetchingMoreRef.current) {
      return;
    }
    void fetchMoreTweets(nextCursor, sessionId);
  }

  function speakTweetAtIndex(index: number, sessionId: number) {
    const tweet = tweetsRef.current[index];
    if (!tweet) {
      endSpeechSession(sessionId, true);
      return;
    }
    const text = buildTweetSpeechText(tweet);
    speakText(text, {
      onStart: () => {
        if (sessionId !== speechSessionRef.current) return;
        updateCurrentIndex(index);
      },
      onDone: () => {
        if (sessionId !== speechSessionRef.current) return;
        advanceSpeechQueue(index, sessionId);
      },
      onStopped: () => {
        if (sessionId !== speechSessionRef.current) return;
        setIsSpeaking(false);
      },
      onError: (error) => {
        if (sessionId !== speechSessionRef.current) return;
        const message = error instanceof Error ? error.message : String(error);
        setSpeechError(`Speech error: ${message}`);
        endSpeechSession(sessionId, false);
      },
    });
  }

  async function fetchMoreTweets(cursor: string, sessionId: number) {
    if (isFetchingMoreRef.current) return;
    const apiKeyForFetch = apiKeyRef.current;
    if (!apiKeyForFetch) {
      setSpeechError('Missing API key for auto-fetch.');
      endSpeechSession(sessionId, false);
      return;
    }
    setFetchingMoreState(true);
    setSpeechError(null);

    try {
      const result = await fetchXHomeTimeline({
        apiKey: apiKeyForFetch,
        count: TWEET_COUNT,
        cursor,
        log: logTimeline,
      });

      if (sessionId !== speechSessionRef.current) {
        return;
      }

      const previousCount = tweetsRef.current.length;
      const merged = mergeTweetSamples(tweetsRef.current, result.tweetSamples);
      tweetsRef.current = merged;
      nextCursorRef.current = result.nextCursor;
      setTweets(merged);
      setMeta({
        entriesCount: result.entriesCount,
        tweetsCount: result.tweetsCount,
        nextCursor: result.nextCursor,
      });

      if (merged.length === previousCount) {
        setSpeechError('No additional tweets returned.');
        endSpeechSession(sessionId, false);
        return;
      }

      const nextIndex = currentIndexRef.current;
      if (nextIndex < merged.length) {
        speakTweetAtIndex(nextIndex, sessionId);
      } else {
        endSpeechSession(sessionId, true);
      }
    } catch (err) {
      if (sessionId !== speechSessionRef.current) {
        return;
      }
      const message = err instanceof Error ? err.message : String(err);
      setSpeechError(`Auto-fetch failed: ${message}`);
      endSpeechSession(sessionId, false);
    } finally {
      setFetchingMoreState(false);
    }
  }

  const handleFetch = async () => {
    const trimmedKey = apiKey.trim();
    if (!trimmedKey) {
      setError('Paste a valid API key first.');
      setStatus('error');
      return;
    }

    speechSessionRef.current += 1;
    stopSpeech();
    setIsSpeaking(false);
    setFetchingMoreState(false);
    setSpeechError(null);
    updateCurrentIndex(0);
    tweetsRef.current = [];
    nextCursorRef.current = undefined;
    apiKeyRef.current = trimmedKey;

    setStatus('loading');
    setError(null);
    setTweets([]);
    setMeta(null);
    fadeAnim.setValue(0);

    try {
      const result = await fetchXHomeTimeline({
        apiKey: trimmedKey,
        count: TWEET_COUNT,
        log: logTimeline,
      });

      tweetsRef.current = result.tweetSamples;
      nextCursorRef.current = result.nextCursor;
      setTweets(result.tweetSamples);
      setMeta({
        entriesCount: result.entriesCount,
        tweetsCount: result.tweetsCount,
        nextCursor: result.nextCursor,
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
    const items = tweetsRef.current;
    if (status === 'loading' || items.length === 0 || isSpeaking) {
      return;
    }
    setSpeechError(null);
    stopSpeech();
    setFetchingMoreState(false);

    const sessionId = speechSessionRef.current + 1;
    speechSessionRef.current = sessionId;
    setIsSpeaking(true);

    const startIndex = Math.max(0, Math.min(currentIndexRef.current, items.length - 1));
    currentIndexRef.current = startIndex;
    speakTweetAtIndex(startIndex, sessionId);
  };

  const hasTweets = tweets.length > 0;
  const statusText = status === 'loading' ? 'Fetching from X...' : status === 'ready' ? 'Timeline loaded' : 'Idle';
  const canPlay = hasTweets && !isSpeaking && status !== 'loading';
  const canStop = isSpeaking || isFetchingMore;
  const spokenIndexLabel = hasTweets ? Math.min(currentIndex + 1, tweets.length) : 0;

  return (
    <LinearGradient colors={['#f2d8c4', '#e7eef8']} style={styles.background}>
      <StatusBar style="dark" />
      <SafeAreaView style={styles.safeArea}>
        <ScrollView contentContainerStyle={styles.scrollContent} keyboardShouldPersistTaps="handled">
          <View style={styles.header}>
            <Text style={styles.eyebrow}>ReadMyFeed</Text>
            <Text style={styles.title}>Home timeline preview</Text>
            <Text style={styles.subtitle}>
              Paste your X Auth Helper API key to fetch the first {TWEET_COUNT} tweets.
            </Text>
          </View>

          <View style={styles.panel}>
            <Text style={styles.label}>API key</Text>
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
                  <Text style={styles.buttonText}>Fetch tweets</Text>
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

            {hasTweets ? (
              <Text style={styles.ttsStatus}>
                {isSpeaking
                  ? `Reading ${spokenIndexLabel} of ${tweets.length}`
                  : `Ready to read ${tweets.length} tweets`}
              </Text>
            ) : null}

            {isFetchingMore ? <Text style={styles.ttsStatus}>Fetching more tweets...</Text> : null}

            {speechError ? <Text style={styles.errorText}>{speechError}</Text> : null}

            {error ? <Text style={styles.errorText}>{error}</Text> : null}

            {meta ? (
              <View style={styles.metaRow}>
                <Text style={styles.metaText}>Entries: {meta.entriesCount}</Text>
                <Text style={styles.metaText}>Tweets: {meta.tweetsCount}</Text>
                <Text style={styles.metaText} numberOfLines={1}>
                  Cursor: {meta.nextCursor ? 'set' : 'none'}
                </Text>
              </View>
            ) : null}
          </View>

          <Animated.View style={[styles.timeline, { opacity: fadeAnim }]}>
            <Text style={styles.sectionTitle}>Tweet samples</Text>
            {hasTweets ? (
              tweets.map((tweet, index) => (
                <View key={`${tweet.id}_${index}`} style={styles.tweetCard}>
                  <View style={styles.tweetHeader}>
                    <Text style={styles.tweetIndex}>{String(index + 1).padStart(2, '0')}</Text>
                    <View style={styles.tweetMeta}>
                      <Text style={styles.tweetUser}>{formatHandle(tweet.user)}</Text>
                      <Text style={styles.tweetId} numberOfLines={1}>
                        {tweet.id || 'missing id'}
                      </Text>
                    </View>
                  </View>
                  <Text style={styles.tweetText}>{truncateText(tweet.text)}</Text>
                </View>
              ))
            ) : (
              <View style={styles.emptyState}>
                <Text style={styles.emptyTitle}>No tweets yet</Text>
                <Text style={styles.emptyBody}>Fetch the timeline to see a preview here.</Text>
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
