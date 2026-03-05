export class TtsError extends Error {
  public readonly code: string;
  public readonly context?: Record<string, unknown>;

  public constructor(message: string, code: string, context?: Record<string, unknown>) {
    super(message);
    this.name = 'TtsError';
    this.code = code;
    this.context = context;
  }
}

export const ttsErrorCodes = {
  ModelDownloadFailed: 'TTS_MODEL_DOWNLOAD_FAILED',
  ModelExtractionFailed: 'TTS_MODEL_EXTRACTION_FAILED',
  ModelNotReady: 'TTS_MODEL_NOT_READY',
  InitializationFailed: 'TTS_INITIALIZATION_FAILED',
  GenerationFailed: 'TTS_GENERATION_FAILED',
  NotInitialized: 'TTS_NOT_INITIALIZED',
} as const;
