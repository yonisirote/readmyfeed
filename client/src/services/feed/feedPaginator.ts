import { fetchFeedPage } from './feedFetcher';
import type { FeedItem, FeedPage, FetchParams } from './feedTypes';

type FeedPaginatorOptions = Omit<FetchParams, 'cursor'>;

export class FeedPaginator {
  private readonly baseParams: FeedPaginatorOptions;
  private cursor?: string;
  private items: FeedItem[] = [];
  private rawCount?: number;

  constructor(options: FeedPaginatorOptions) {
    this.baseParams = options;
  }

  public async loadInitial(): Promise<FeedPage> {
    this.items = [];
    this.cursor = undefined;
    this.rawCount = undefined;
    return this.fetchNextPage();
  }

  public async loadNext(): Promise<FeedPage> {
    if (!this.cursor) {
      return {
        items: this.items,
        cursor: this.cursor,
        rawCount: this.rawCount,
      };
    }

    return this.fetchNextPage();
  }

  private async fetchNextPage(): Promise<FeedPage> {
    const page = await fetchFeedPage({
      ...this.baseParams,
      cursor: this.cursor,
    });

    this.items = this.mergeItems(page.items);
    this.cursor = page.cursor;
    this.rawCount = page.rawCount;

    return {
      items: this.items,
      cursor: this.cursor,
      rawCount: this.rawCount,
    };
  }

  private mergeItems(incoming: FeedItem[]): FeedItem[] {
    const seen = new Set(this.items.map((item) => item.id));
    const merged = [...this.items];

    for (const item of incoming) {
      if (!item.id || seen.has(item.id)) {
        continue;
      }
      merged.push(item);
      seen.add(item.id);
    }

    return merged;
  }
}
