const SPEECH_LANGUAGE_BY_CONTENT_LANGUAGE: Record<string, string> = {
  en: 'en-US',
  he: 'he-IL',
};

const HEBREW_CHARACTER_PATTERN = /[\u0590-\u05FF]/;

export type SpeakableItem = {
  text: string;
  authorLabel: string;
  lang: string;
};

export const getSpeakableItemLanguage = (
  item: Pick<SpeakableItem, 'text' | 'lang'>,
): string | undefined => {
  if (HEBREW_CHARACTER_PATTERN.test(item.text)) {
    return 'he-IL';
  }

  return SPEECH_LANGUAGE_BY_CONTENT_LANGUAGE[item.lang.toLowerCase()];
};

export const getSpeakableItemText = (item: Pick<SpeakableItem, 'authorLabel' | 'text'>): string => {
  const authorPrefix = item.authorLabel ? `At ${item.authorLabel}. ` : '';

  return `${authorPrefix}${item.text}`.trim();
};
