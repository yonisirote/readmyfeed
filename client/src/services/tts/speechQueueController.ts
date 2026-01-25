import type { FeedItem } from '../feed/feedTypes';

import type { SpeechEngine, SpeechEngineOptions } from './speechEngine';
import { buildSpeechText } from './speechTextBuilder';

type SpeechQueueEvents = {
  onIndexChange?: (index: number, item: FeedItem) => void;
  onDone?: () => void;
  onError?: (error: Error) => void;
};

type SpeechQueueControllerOptions = SpeechQueueEvents & {
  engine: SpeechEngine;
  textBuilder?: (item: FeedItem) => string;
};

const clampIndex = (index: number, length: number): number => {
  if (length <= 0) return 0;
  return Math.max(0, Math.min(index, length - 1));
};

export class SpeechQueueController {
  private readonly engine: SpeechEngine;
  private readonly textBuilder: (item: FeedItem) => string;
  private readonly events: SpeechQueueEvents;
  private items: FeedItem[] = [];
  private currentIndex = 0;
  private sessionId = 0;
  private isPlaying = false;

  constructor(options: SpeechQueueControllerOptions) {
    this.engine = options.engine;
    this.textBuilder = options.textBuilder ?? buildSpeechText;
    this.events = {
      onIndexChange: options.onIndexChange,
      onDone: options.onDone,
      onError: options.onError,
    };
  }

  public play(items: FeedItem[], startIndex = 0): void {
    this.items = items;
    this.startAt(startIndex);
  }

  public resume(): void {
    if (!this.items.length) return;
    this.startAt(this.currentIndex);
  }

  public stop(): void {
    this.sessionId += 1;
    this.engine.stop();
    this.isPlaying = false;
  }

  public updateItems(items: FeedItem[]): void {
    this.items = items;
    if (this.currentIndex > items.length - 1) {
      this.currentIndex = Math.max(0, items.length - 1);
    }
  }

  public getCurrentIndex(): number {
    return this.currentIndex;
  }

  public getIsPlaying(): boolean {
    return this.isPlaying;
  }

  private startAt(index: number): void {
    if (!this.items.length) {
      this.currentIndex = 0;
      this.isPlaying = false;
      return;
    }

    const clamped = clampIndex(index, this.items.length);
    this.sessionId += 1;
    this.engine.stop();
    this.isPlaying = true;
    this.currentIndex = clamped;
    this.speakIndex(clamped, this.sessionId);
  }

  private finish(sessionId: number): void {
    if (sessionId !== this.sessionId) return;
    this.isPlaying = false;
    this.events.onDone?.();
  }

  private speakIndex(index: number, sessionId: number): void {
    const item = this.items[index];
    if (!item) {
      this.finish(sessionId);
      return;
    }

    const options: SpeechEngineOptions = {
      onStart: () => {
        if (sessionId !== this.sessionId) return;
        this.currentIndex = index;
        this.events.onIndexChange?.(index, item);
      },
      onDone: () => {
        if (sessionId !== this.sessionId) return;
        const nextIndex = index + 1;
        if (nextIndex >= this.items.length) {
          this.finish(sessionId);
          return;
        }
        this.speakIndex(nextIndex, sessionId);
      },
      onStopped: () => {
        if (sessionId !== this.sessionId) return;
        this.isPlaying = false;
      },
      onError: (error) => {
        if (sessionId !== this.sessionId) return;
        this.isPlaying = false;
        const normalizedError = error instanceof Error ? error : new Error(String(error));
        this.events.onError?.(normalizedError);
      },
    };

    this.engine.speak(this.textBuilder(item), options);
  }
}
