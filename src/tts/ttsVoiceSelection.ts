import type { TtsVoice } from './ttsTypes';

const normalizeLanguageTag = (value: string): string => value.replace(/_/g, '-').toLowerCase();

const getLanguageCandidates = (value: string): string[] => {
  const normalized = normalizeLanguageTag(value);
  const [primary, region] = normalized.split('-');

  if (primary === 'he' || primary === 'iw') {
    return region ? [`he-${region}`, `iw-${region}`, 'he', 'iw'] : ['he', 'iw'];
  }

  return region ? [normalized, primary] : [primary];
};

const PLACEHOLDER_VOICE_PATTERN = /[-_]language$/;

const getVoicePriority = (voice: TtsVoice): number => {
  const identifier = voice.identifier.toLowerCase();
  let score = 0;

  if (identifier.includes('local')) {
    score += 4;
  }

  if (voice.quality === 'Enhanced') {
    score += 2;
  }

  if (identifier.includes('default')) {
    score += 1;
  }

  if (identifier.includes('network')) {
    score -= 1;
  }

  if (PLACEHOLDER_VOICE_PATTERN.test(identifier)) {
    score -= 5;
  }

  return score;
};

const pickPreferredVoice = (voices: TtsVoice[]): TtsVoice | undefined => {
  let preferredVoice: TtsVoice | undefined;
  let preferredScore = Number.NEGATIVE_INFINITY;

  for (const voice of voices) {
    const score = getVoicePriority(voice);

    if (!preferredVoice || score > preferredScore) {
      preferredVoice = voice;
      preferredScore = score;
    }
  }

  return preferredVoice;
};

export const findBestVoiceForLanguage = (
  voices: TtsVoice[],
  language: string,
): TtsVoice | undefined => {
  for (const candidate of getLanguageCandidates(language)) {
    const matchedVoice = pickPreferredVoice(
      voices.filter((voice) => normalizeLanguageTag(voice.language) === candidate),
    );

    if (matchedVoice) {
      return matchedVoice;
    }
  }

  return undefined;
};
