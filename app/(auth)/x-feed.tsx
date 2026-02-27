import { useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, FlatList, SafeAreaView, StyleSheet, Text, View } from 'react-native';

import {
  clearXFollowingTimelineBatch,
  getXFollowingTimelineBatch,
  XFollowingTimelineBatch,
  XTimelineItem,
} from '../../src/services/x/timeline';

export default function XFeedScreen() {
  const [batch, setBatch] = useState<XFollowingTimelineBatch | null>(null);

  const items = useMemo<XTimelineItem[]>(() => {
    if (!batch?.items) {
      return [];
    }

    const unique = new Map<string, XTimelineItem>();
    for (const item of batch.items) {
      if (!item.id || unique.has(item.id)) {
        continue;
      }
      unique.set(item.id, item);
    }

    return Array.from(unique.values());
  }, [batch]);

  useEffect(() => {
    const cached = getXFollowingTimelineBatch();
    if (cached) {
      setBatch(cached);
      clearXFollowingTimelineBatch();
    }
  }, []);

  return (
    <SafeAreaView style={styles.root}>
      <View style={styles.header}>
        <Text style={styles.title}>Following feed</Text>
        <Text style={styles.subtitle}>Showing the first batch from your X following timeline.</Text>
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
