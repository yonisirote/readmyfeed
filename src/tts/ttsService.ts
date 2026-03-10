import * as Speech from 'expo-speech';

import { TtsError, ttsErrorCodes } from './ttsErrors';
import { createTtsLogger, type TtsLogger } from './ttsLogger';
import type { TtsSpeakOptions, TtsVoice } from './ttsTypes';
import { findBestVoiceForLanguage } from './ttsVoiceSelection';

export type TtsServiceOptions = {
  logger?: TtsLogger;
};

const SPEECH_TIMEOUT_BASE_MS = 15_000;
const SPEECH_TIMEOUT_PER_CHAR_MS = 100;

export class TtsService {
  private readonly logger: TtsLogger;
  private availableVoices: TtsVoice[] = [];
  private initialized: boolean = false;
  private initPromise: Promise<void> | null = null;

  public constructor(options: TtsServiceOptions = {}) {
    this.logger = options.logger ?? createTtsLogger();
  }

  public async initialize(): Promise<void> {
    if (this.initialized) {
      this.logger.debug('TTS already initialized');
      return;
    }

    if (this.initPromise) {
      return this.initPromise;
    }

    this.initPromise = this.doInitialize();
    return this.initPromise;
  }

  private async doInitialize(): Promise<void> {
    this.logger.info('Initializing TTS');

    try {
      this.availableVoices = (await Speech.getAvailableVoicesAsync()).map((voice) => ({
        identifier: voice.identifier,
        language: voice.language,
        quality: voice.quality,
      }));
      this.initialized = true;

      this.logger.info('TTS initialized successfully', { voiceCount: this.availableVoices.length });
    } catch (err) {
      this.initPromise = null;

      if (err instanceof TtsError) throw err;

      throw new TtsError('Failed to initialize TTS engine', ttsErrorCodes.InitializationFailed, {
        cause: err instanceof Error ? err.message : String(err),
      });
    }
  }

  public async speak(text: string, options?: TtsSpeakOptions): Promise<void> {
    this.assertInitialized();

    const resolvedOptions = this.resolveSpeakOptions(options);

    this.logger.debug('Generating speech', {
      textLength: text.length,
      language: resolvedOptions.language,
      pitch: resolvedOptions.pitch,
      rate: resolvedOptions.rate,
      voice: resolvedOptions.voice,
      volume: resolvedOptions.volume,
    });

    await this.speakWithResolvedOptions(text, resolvedOptions);
  }

  public async stop(): Promise<void> {
    if (!this.initialized) {
      return;
    }

    await Speech.stop();
  }

  public deinitialize(): void {
    if (!this.initialized) return;

    this.logger.info('Deinitializing TTS');
    void Speech.stop();
    this.availableVoices = [];
    this.initialized = false;
    this.initPromise = null;
  }

  public hasLanguageSupport(language: string): boolean {
    this.assertInitialized();
    return findBestVoiceForLanguage(this.availableVoices, language) !== undefined;
  }

  private resolveSpeakOptions(options?: TtsSpeakOptions): TtsSpeakOptions {
    if (!options?.language || options.voice) {
      return options ?? {};
    }

    const matchedVoice = findBestVoiceForLanguage(this.availableVoices, options.language);
    if (!matchedVoice) {
      this.logger.warn('Requested TTS language not available, falling back to default voice', {
        requestedLanguage: options.language,
      });

      return options;
    }

    return {
      ...options,
      language: matchedVoice.language,
      voice: matchedVoice.identifier,
    };
  }

  private async speakWithResolvedOptions(text: string, options: TtsSpeakOptions): Promise<void> {
    const timeoutMs = SPEECH_TIMEOUT_BASE_MS + text.length * SPEECH_TIMEOUT_PER_CHAR_MS;

    try {
      await new Promise<void>((resolve, reject) => {
        const timer = setTimeout(() => {
          this.logger.warn('Speech timed out, resolving', { textLength: text.length, timeoutMs });
          void Speech.stop();
          resolve();
        }, timeoutMs);

        Speech.speak(text, {
          ...options,
          onStart: () => {
            options.onStart?.();
          },
          onDone: () => {
            clearTimeout(timer);
            options.onDone?.();
            resolve();
          },
          onStopped: () => {
            clearTimeout(timer);
            options.onStopped?.();
            resolve();
          },
          onError: (error) => {
            clearTimeout(timer);
            options.onError?.(error);
            reject(this.createGenerationError(error));
          },
        });
      });
    } catch (err) {
      if (err instanceof TtsError) {
        throw err;
      }

      throw this.createGenerationError(err);
    }
  }

  private createGenerationError(error: unknown): TtsError {
    return new TtsError('Failed to generate speech', ttsErrorCodes.GenerationFailed, {
      cause: error instanceof Error ? error.message : String(error),
    });
  }

  private assertInitialized(): void {
    if (!this.initialized) {
      throw new TtsError(
        'TTS not initialized — call initialize() first',
        ttsErrorCodes.NotInitialized,
      );
    }
  }
}
