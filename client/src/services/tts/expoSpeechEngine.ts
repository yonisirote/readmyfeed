import * as Speech from 'expo-speech';

import type { SpeechEngine, SpeechEngineOptions } from './speechEngine';

const mapOptions = (options?: SpeechEngineOptions): Speech.SpeechOptions | undefined => {
  if (!options) return undefined;
  return {
    onStart: options.onStart,
    onDone: options.onDone,
    onStopped: options.onStopped,
    onError: options.onError,
  };
};

export const expoSpeechEngine: SpeechEngine = {
  speak: (text, options) => {
    Speech.speak(text, mapOptions(options));
  },
  stop: () => {
    Speech.stop();
  },
};
