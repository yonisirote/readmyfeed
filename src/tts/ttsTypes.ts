export type TtsSpeakOptions = {
  language?: string;
  pitch?: number;
  rate?: number;
  voice?: string;
  volume?: number;
  onStart?: () => void;
  onDone?: () => void;
  onStopped?: () => void;
  onError?: (error: Error) => void;
};
