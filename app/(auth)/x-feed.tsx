import { useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import {
  clearXFollowingTimelineBatch,
  getXFollowingTimelineBatch,
  toSpeakableItem,
  XFollowingTimelineBatch,
  XTimelineItem,
} from '../../src/services/x/timeline';
import {
  getSpeakableItemLanguage,
  getSpeakableItemText,
  TtsError,
  TtsService,
} from '../../src/tts';

export default function XFeedScreen() {
  const [batch, setBatch] = useState<XFollowingTimelineBatch | null>(null);
  const [items, setItems] = useState<XTimelineItem[]>([]);
  const [isSpeaking, setIsSpeaking] = useState(false);
  const [speechError, setSpeechError] = useState<string | null>(null);
  const ttsService = useMemo(() => new TtsService(), []);
  const playbackRequestId = useRef(0);

  useEffect(() => {
    if (!batch?.items) {
      setItems((prev) => (prev.length === 0 ? prev : []));
      return;
    }

    const unique = new Map<string, XTimelineItem>();
    for (const item of batch.items) {
      if (!item.id || unique.has(item.id)) {
        continue;
      }
      unique.set(item.id, item);
    }

    const nextItems = Array.from(unique.values());
    setItems((prev) => {
      if (
        prev.length === nextItems.length &&
        nextItems.every((item, i) => prev[i]?.id === item.id)
      ) {
        return prev;
      }
      return nextItems;
    });
  }, [batch]);

  useEffect(() => {
    const cached = getXFollowingTimelineBatch();
    if (cached) {
      setBatch(cached);
      clearXFollowingTimelineBatch();
    }

    return () => {
      ttsService.deinitialize();
    };
  }, [ttsService]);

  const handlePlayFeed = async () => {
    const speakableItems = items.filter((item) => item.text.trim().length > 0).map(toSpeakableItem);

    if (speakableItems.length === 0) {
      setSpeechError('No tweet text available to read yet.');
      return;
    }

    const requestId = playbackRequestId.current + 1;
    playbackRequestId.current = requestId;

    setSpeechError(null);
    setIsSpeaking(true);

    try {
      await ttsService.initialize();

      for (const item of speakableItems) {
        if (playbackRequestId.current !== requestId) {
          return;
        }

        const language = getSpeakableItemLanguage(item);

        if (language && !ttsService.hasLanguageSupport(language)) {
          continue;
        }

        await ttsService.speak(getSpeakableItemText(item), {
          language,
          rate: 0.95,
        });
      }
    } catch (err) {
      setSpeechError(
        err instanceof TtsError ? err.message : 'Unable to play the fetched tweets right now.',
      );
    } finally {
      if (playbackRequestId.current === requestId) {
        setIsSpeaking(false);
      }
    }
  };

  const handleStopFeed = async () => {
    playbackRequestId.current += 1;
    await ttsService.stop();
    setIsSpeaking(false);
  };

  return (
    <SafeAreaView style={styles.root}>
      <View style={styles.header}>
        <Text style={styles.title}>Following feed</Text>
        <Text style={styles.subtitle}>Showing the first batch from your X following timeline.</Text>
        <View style={styles.actionsRow}>
          <Pressable
            accessibilityRole="button"
            onPress={handlePlayFeed}
            disabled={items.length === 0 || isSpeaking}
            style={[
              styles.actionButton,
              (items.length === 0 || isSpeaking) && styles.actionButtonDisabled,
            ]}
          >
            <Text style={styles.actionButtonText}>{isSpeaking ? 'Playing...' : 'Play tweets'}</Text>
          </Pressable>
          <Pressable
            accessibilityRole="button"
            onPress={handleStopFeed}
            disabled={!isSpeaking}
            style={[styles.stopButton, !isSpeaking && styles.actionButtonDisabled]}
          >
            <Text style={styles.stopButtonText}>Stop</Text>
          </Pressable>
        </View>
        {speechError ? <Text style={styles.errorText}>{speechError}</Text> : null}
      </View>

      {items.length === 0 ? (
        <View style={styles.loadingState}>
          <ActivityIndicator size="small" />
          <Text style={styles.loadingText}>Preparing your feed…</Text>
        </View>
      ) : (
        <FlatList
          data={items}
          keyExtractor={(item) => item.id}
          contentContainerStyle={styles.listContent}
          ItemSeparatorComponent={() => <View style={styles.separator} />}
          renderItem={({ item }) => (
            <View style={styles.feedItem}>
              <Text style={styles.feedAuthor}>
                {item.authorHandle ? `@${item.authorHandle}` : item.authorName || 'Unknown'}
              </Text>
              <Text style={styles.feedText}>{item.text || '[No text]'}</Text>
              <Text style={styles.feedMeta}>
                {item.isRetweet ? 'Repost' : item.replyTo ? 'Reply' : 'Post'}
                {item.createdAt ? `  •  ${new Date(item.createdAt).toLocaleString()}` : ''}
              </Text>
            </View>
          )}
        />
      )}
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
    fontSize: 18,
    fontWeight: '700',
  },
  subtitle: {
    marginTop: 4,
    color: '#525252',
  },
  actionsRow: {
    flexDirection: 'row',
    marginTop: 14,
    gap: 10,
  },
  actionButton: {
    backgroundColor: '#111827',
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: 999,
  },
  stopButton: {
    backgroundColor: '#e5e7eb',
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: 999,
  },
  actionButtonDisabled: {
    opacity: 0.45,
  },
  actionButtonText: {
    color: '#ffffff',
    fontWeight: '600',
  },
  stopButtonText: {
    color: '#111827',
    fontWeight: '600',
  },
  errorText: {
    marginTop: 10,
    color: '#b42318',
  },
  loadingState: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  loadingText: {
    marginTop: 8,
    color: '#4c4c4c',
  },
  listContent: {
    paddingHorizontal: 16,
    paddingBottom: 24,
  },
  feedItem: {
    paddingVertical: 8,
  },
  feedAuthor: {
    fontWeight: '600',
    color: '#1e1e1e',
    marginBottom: 4,
  },
  feedText: {
    color: '#1f1f1f',
    lineHeight: 20,
  },
  feedMeta: {
    marginTop: 6,
    color: '#616161',
    fontSize: 12,
  },
  separator: {
    borderBottomWidth: 1,
    borderBottomColor: '#ececec',
  },
});
