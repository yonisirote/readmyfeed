export type SpeechEngineOptions = {
  onStart?: () => void;
  onDone?: () => void;
  onStopped?: () => void;
  onError?: (error: unknown) => void;
};

export type SpeechEngine = {
  speak: (text: string, options?: SpeechEngineOptions) => void;
  stop: () => void;
};
