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

export default function App() {
  const [apiKey, setApiKey] = useState('');
  const [status, setStatus] = useState<FetchStatus>('idle');
  const [error, setError] = useState<string | null>(null);
  const [tweets, setTweets] = useState<XHomeTimelineTweetSample[]>([]);
  const [meta, setMeta] = useState<FetchMeta | null>(null);
  const fadeAnim = useRef(new Animated.Value(1)).current;

  const handleFetch = async () => {
    const trimmedKey = apiKey.trim();
    if (!trimmedKey) {
      setError('Paste a valid API key first.');
      setStatus('error');
      return;
    }

    setStatus('loading');
    setError(null);
    setTweets([]);
    setMeta(null);
    fadeAnim.setValue(0);

    try {
      const result = await fetchXHomeTimeline({
        apiKey: trimmedKey,
        count: TWEET_COUNT,
        log: (message) => {
          console.log(`[rmf] ${message}`);
        },
      });

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

  const hasTweets = tweets.length > 0;
  const statusText = status === 'loading' ? 'Fetching from X...' : status === 'ready' ? 'Timeline loaded' : 'Idle';

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
