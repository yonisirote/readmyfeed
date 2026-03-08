import * as Speech from 'expo-speech';

import { TtsError, ttsErrorCodes } from './ttsErrors';
import { createTtsLogger, TtsLogger } from './ttsLogger';
import { TtsSpeakOptions } from './ttsTypes';

type TtsVoice = {
  identifier: string;
  language: string;
};

export type TtsAvailableVoice = TtsVoice;

const normalizeLanguageTag = (value: string): string => value.replace(/_/g, '-').toLowerCase();

const getLanguageCandidates = (value: string): string[] => {
  const normalized = normalizeLanguageTag(value);
  const [primary, region] = normalized.split('-');

  if (primary === 'he' || primary === 'iw') {
    return region ? [`he-${region}`, `iw-${region}`, 'he', 'iw'] : ['he', 'iw'];
  }

  return region ? [normalized, primary] : [primary];
};

export type TtsServiceOptions = {
  logger?: TtsLogger;
};

export class TtsService {
  private readonly logger: TtsLogger;
  private availableVoices: TtsVoice[] = [];
  private initialized: boolean = false;

  public constructor(options: TtsServiceOptions = {}) {
    this.logger = options.logger ?? createTtsLogger();
  }

  public async initialize(): Promise<void> {
    if (this.initialized) {
      this.logger.debug('TTS already initialized');
      return;
    }

    this.logger.info('Initializing TTS');

    try {
      this.availableVoices = (await Speech.getAvailableVoicesAsync()).map((voice) => ({
        identifier: voice.identifier,
        language: voice.language,
      }));
      this.initialized = true;

      this.logger.info('TTS initialized successfully', { voiceCount: this.availableVoices.length });
    } catch (err) {
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

    try {
      await new Promise<void>((resolve, reject) => {
        Speech.speak(text, {
          ...resolvedOptions,
          onStart: () => {
            resolvedOptions.onStart?.();
          },
          onDone: () => {
            resolvedOptions.onDone?.();
            resolve();
          },
          onStopped: () => {
            resolvedOptions.onStopped?.();
            resolve();
          },
          onError: (error) => {
            resolvedOptions.onError?.(error);
            reject(
              new TtsError('Failed to generate speech', ttsErrorCodes.GenerationFailed, {
                cause: error.message,
              }),
            );
          },
        });
      });
    } catch (err) {
      if (err instanceof TtsError) {
        throw err;
      }

      throw new TtsError('Failed to generate speech', ttsErrorCodes.GenerationFailed, {
        cause: err instanceof Error ? err.message : String(err),
      });
    }
  }

  public async save(text: string, path?: string): Promise<void> {
    this.assertInitialized();

    this.logger.warn('Save requested but not supported by expo-speech', {
      textLength: text.length,
      path,
    });
    throw new TtsError(
      'Saving speech to a file is not supported by expo-speech',
      ttsErrorCodes.GenerationFailed,
    );
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
    this.initialized = false;
  }

  public isInitialized(): boolean {
    return this.initialized;
  }

  public hasLanguageSupport(language: string): boolean {
    this.assertInitialized();
    return this.findVoiceForLanguage(language) !== undefined;
  }

  public getAvailableVoices(): TtsAvailableVoice[] {
    this.assertInitialized();
    return [...this.availableVoices];
  }

  private resolveSpeakOptions(options?: TtsSpeakOptions): TtsSpeakOptions {
    if (!options?.language || options.voice) {
      return options ?? {};
    }

    const matchedVoice = this.findVoiceForLanguage(options.language);
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

  private findVoiceForLanguage(language: string): TtsVoice | undefined {
    const candidates = getLanguageCandidates(language);

    for (const candidate of candidates) {
      const matchedVoice = this.availableVoices.find(
        (voice) => normalizeLanguageTag(voice.language) === candidate,
      );

      if (matchedVoice) {
        return matchedVoice;
      }
    }

    return undefined;
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
