import { registerPlugin } from '@capacitor/core';

export type TtsSpeakOptions = {
  text: string;
  utteranceId: string;
};

export type TtsSpeakResult = {
  ok: boolean;
  utteranceId: string;
};

export type TtsStopResult = {
  ok: boolean;
};

export type TtsStartEvent = {
  utteranceId: string;
};

export type TtsDoneEvent = {
  utteranceId: string;
};

export type TtsErrorEvent = {
  utteranceId?: string;
  code?: string;
  message?: string;
};

export interface TtsPlugin {
  speak(options: TtsSpeakOptions): Promise<TtsSpeakResult>;
  stop(): Promise<TtsStopResult>;

  addListener(eventName: 'ttsStart', listenerFunc: (event: TtsStartEvent) => void): Promise<{ remove: () => Promise<void> }>; 
  addListener(eventName: 'ttsDone', listenerFunc: (event: TtsDoneEvent) => void): Promise<{ remove: () => Promise<void> }>; 
  addListener(eventName: 'ttsError', listenerFunc: (event: TtsErrorEvent) => void): Promise<{ remove: () => Promise<void> }>; 
}

export const Tts = registerPlugin<TtsPlugin>('Tts');
