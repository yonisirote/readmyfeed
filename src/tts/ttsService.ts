import TTSManager from 'react-native-sherpa-onnx-offline-tts';

import { TTS_DEFAULT_SPEAKER_ID, TTS_DEFAULT_SPEED } from './ttsConstants';
import { TtsError, ttsErrorCodes } from './ttsErrors';
import { createTtsLogger, TtsLogger } from './ttsLogger';
import { TtsModelManager, TtsModelManagerOptions } from './ttsModelManager';
import { TtsDownloadProgress, TtsSpeakOptions } from './ttsTypes';

export type TtsServiceOptions = TtsModelManagerOptions & {
  logger?: TtsLogger;
};

export class TtsService {
  private readonly logger: TtsLogger;
  private readonly modelManager: TtsModelManager;
  private initialized: boolean = false;

  public constructor(options: TtsServiceOptions = {}) {
    this.logger = options.logger ?? createTtsLogger();
    this.modelManager = new TtsModelManager({
      logger: this.logger,
      modelZipUrl: options.modelZipUrl,
    });
  }

  public async initialize(onProgress?: (progress: TtsDownloadProgress) => void): Promise<void> {
    if (this.initialized) {
      this.logger.debug('TTS already initialized');
      return;
    }

    this.logger.info('Initializing TTS');

    try {
      const config = await this.modelManager.ensureModel(onProgress);

      this.logger.debug('Model config', {
        modelPath: config.modelPath,
        tokensPath: config.tokensPath,
        dataDirPath: config.dataDirPath,
      });

      await TTSManager.initialize(JSON.stringify(config));
      this.initialized = true;

      this.logger.info('TTS initialized successfully');
    } catch (err) {
      if (err instanceof TtsError) throw err;

      throw new TtsError('Failed to initialize TTS engine', ttsErrorCodes.InitializationFailed, {
        cause: err instanceof Error ? err.message : String(err),
      });
    }
  }

  public async speak(text: string, options?: TtsSpeakOptions): Promise<void> {
    this.assertInitialized();

    const speakerId = options?.speakerId ?? TTS_DEFAULT_SPEAKER_ID;
    const speed = options?.speed ?? TTS_DEFAULT_SPEED;

    this.logger.debug('Generating speech', {
      textLength: text.length,
      speakerId,
      speed,
    });

    try {
      await TTSManager.generateAndPlay(text, speakerId, speed);
    } catch (err) {
      throw new TtsError('Failed to generate speech', ttsErrorCodes.GenerationFailed, {
        cause: err instanceof Error ? err.message : String(err),
      });
    }
  }

  public async save(text: string, path?: string): Promise<void> {
    this.assertInitialized();

    this.logger.debug('Saving speech to file', { textLength: text.length, path });

    try {
      await TTSManager.generateAndSave(text, path, 'wav');
    } catch (err) {
      throw new TtsError('Failed to save speech', ttsErrorCodes.GenerationFailed, {
        cause: err instanceof Error ? err.message : String(err),
      });
    }
  }

  public addVolumeListener(callback: (volume: number) => void): { remove: () => void } {
    return TTSManager.addVolumeListener(callback);
  }

  public deinitialize(): void {
    if (!this.initialized) return;

    this.logger.info('Deinitializing TTS');
    TTSManager.deinitialize();
    this.initialized = false;
  }

  public isInitialized(): boolean {
    return this.initialized;
  }

  public getModelManager(): TtsModelManager {
    return this.modelManager;
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
