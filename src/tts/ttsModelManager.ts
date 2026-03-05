import {
  documentDirectory,
  createDownloadResumable,
  getInfoAsync,
  makeDirectoryAsync,
  deleteAsync,
} from 'expo-file-system/legacy';
import { unzip } from 'react-native-zip-archive';

import {
  TTS_MODEL_ZIP_URL,
  TTS_MODEL_NAME,
  TTS_ONNX_FILENAME,
  TTS_TOKENS_FILENAME,
  TTS_ESPEAK_DIR_NAME,
} from './ttsConstants';
import { TtsError, ttsErrorCodes } from './ttsErrors';
import { createTtsLogger, TtsLogger } from './ttsLogger';
import { TtsDownloadProgress, TtsModelConfig, TtsModelStatus } from './ttsTypes';

export type TtsModelManagerOptions = {
  logger?: TtsLogger;
  modelZipUrl?: string;
};

const TTS_DIR_NAME = 'tts-models';

const getBasePaths = (): {
  ttsDir: string;
  archiveUri: string;
  extractDir: string;
  modelDir: string;
} => {
  if (!documentDirectory) {
    throw new TtsError('Document directory not available', ttsErrorCodes.ModelDownloadFailed);
  }

  const ttsDir = `${documentDirectory}${TTS_DIR_NAME}`;
  return {
    ttsDir,
    archiveUri: `${ttsDir}/${TTS_MODEL_NAME}.zip`,
    extractDir: `${ttsDir}/extracted`,
    modelDir: `${ttsDir}/extracted/${TTS_MODEL_NAME}`,
  };
};

const toNativePath = (fileUri: string): string =>
  fileUri.startsWith('file://') ? fileUri.slice(7) : fileUri;

export class TtsModelManager {
  private readonly logger: TtsLogger;
  private readonly modelZipUrl: string;

  public constructor(options: TtsModelManagerOptions = {}) {
    this.logger = options.logger ?? createTtsLogger();
    this.modelZipUrl = options.modelZipUrl ?? TTS_MODEL_ZIP_URL;
  }

  public async getModelStatus(): Promise<TtsModelStatus> {
    try {
      const { modelDir } = getBasePaths();
      const onnxUri = `${modelDir}/${TTS_ONNX_FILENAME}`;
      const tokensUri = `${modelDir}/${TTS_TOKENS_FILENAME}`;
      const espeakUri = `${modelDir}/${TTS_ESPEAK_DIR_NAME}`;

      const [onnx, tokens, espeak] = await Promise.all([
        getInfoAsync(onnxUri),
        getInfoAsync(tokensUri),
        getInfoAsync(espeakUri),
      ]);

      if (onnx.exists && tokens.exists && espeak.exists) {
        return 'ready';
      }

      const { archiveUri } = getBasePaths();
      const archive = await getInfoAsync(archiveUri);
      if (archive.exists) {
        return 'extracting';
      }

      return 'not_downloaded';
    } catch (err) {
      this.logger.error('Failed to check model status', {
        error: err instanceof Error ? err.message : String(err),
      });
      return 'error';
    }
  }

  public async downloadModel(onProgress?: (progress: TtsDownloadProgress) => void): Promise<void> {
    const { ttsDir, archiveUri } = getBasePaths();

    this.logger.info('Starting model download', { url: this.modelZipUrl });

    await makeDirectoryAsync(ttsDir, { intermediates: true });

    const downloadResumable = createDownloadResumable(
      this.modelZipUrl,
      archiveUri,
      {},
      (downloadProgress) => {
        const fraction =
          downloadProgress.totalBytesExpectedToWrite > 0
            ? downloadProgress.totalBytesWritten / downloadProgress.totalBytesExpectedToWrite
            : 0;

        onProgress?.({
          totalBytesWritten: downloadProgress.totalBytesWritten,
          totalBytesExpectedToWrite: downloadProgress.totalBytesExpectedToWrite,
          fraction,
        });
      },
    );

    try {
      const result = await downloadResumable.downloadAsync();

      if (!result || result.status !== 200) {
        throw new Error(`Download failed with status ${result?.status}`);
      }

      this.logger.info('Model download complete', { uri: result.uri });
    } catch (err) {
      await deleteAsync(archiveUri, { idempotent: true });

      if (err instanceof TtsError) throw err;

      throw new TtsError('Failed to download TTS model', ttsErrorCodes.ModelDownloadFailed, {
        cause: err instanceof Error ? err.message : String(err),
      });
    }
  }

  public async extractModel(): Promise<void> {
    const { archiveUri, extractDir, modelDir } = getBasePaths();

    this.logger.info('Extracting model archive');

    await makeDirectoryAsync(extractDir, { intermediates: true });

    try {
      await unzip(toNativePath(archiveUri), toNativePath(extractDir));

      const info = await getInfoAsync(modelDir);
      if (!info.exists) {
        throw new Error(`Expected model directory not found after extraction: ${TTS_MODEL_NAME}`);
      }

      this.logger.info('Model extraction complete', { modelDir });

      await deleteAsync(archiveUri, { idempotent: true });
    } catch (err) {
      if (err instanceof TtsError) throw err;

      throw new TtsError('Failed to extract TTS model', ttsErrorCodes.ModelExtractionFailed, {
        cause: err instanceof Error ? err.message : String(err),
      });
    }
  }

  public async ensureModel(
    onProgress?: (progress: TtsDownloadProgress) => void,
  ): Promise<TtsModelConfig> {
    const status = await this.getModelStatus();

    if (status === 'ready') {
      this.logger.debug('Model already available');
      return this.getModelConfig();
    }

    if (status === 'not_downloaded' || status === 'error') {
      await this.downloadModel(onProgress);
    }

    await this.extractModel();
    return this.getModelConfig();
  }

  public async deleteModel(): Promise<void> {
    const { ttsDir } = getBasePaths();

    this.logger.info('Deleting TTS model files');
    await deleteAsync(ttsDir, { idempotent: true });
  }

  public getModelConfig(): TtsModelConfig {
    const { modelDir } = getBasePaths();

    return {
      modelPath: toNativePath(`${modelDir}/${TTS_ONNX_FILENAME}`),
      tokensPath: toNativePath(`${modelDir}/${TTS_TOKENS_FILENAME}`),
      dataDirPath: toNativePath(`${modelDir}/${TTS_ESPEAK_DIR_NAME}`),
    };
  }
}
