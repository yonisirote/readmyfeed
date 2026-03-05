export type TtsModelConfig = {
  modelPath: string;
  tokensPath: string;
  dataDirPath: string;
};

export type TtsModelStatus = 'not_downloaded' | 'downloading' | 'extracting' | 'ready' | 'error';

export type TtsDownloadProgress = {
  totalBytesWritten: number;
  totalBytesExpectedToWrite: number;
  fraction: number;
};

export type TtsSpeakOptions = {
  speakerId?: number;
  speed?: number;
};
