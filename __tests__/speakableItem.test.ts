import { getSpeakableItemLanguage, getSpeakableItemText, type SpeakableItem } from '../src/tts';

describe('speakableItem helpers', () => {
  it('prefers hebrew playback when tweet text contains hebrew characters', () => {
    const item: SpeakableItem = {
      text: 'Hello שלום',
      authorLabel: 'yoni',
      lang: 'en',
    };

    expect(getSpeakableItemLanguage(item)).toBe('he-IL');
  });

  it('maps english timeline items to en-US speech', () => {
    const item: SpeakableItem = {
      text: 'Hello world',
      authorLabel: 'yoni',
      lang: 'en',
    };

    expect(getSpeakableItemLanguage(item)).toBe('en-US');
  });

  it('formats author label into speakable text', () => {
    const item: SpeakableItem = {
      text: 'Hello world',
      authorLabel: 'yoni',
      lang: 'en',
    };

    expect(getSpeakableItemText(item)).toBe('yoni says: Hello world');
  });

  it('uses hebrew connector when text contains hebrew characters', () => {
    const item: SpeakableItem = {
      text: 'שלום עולם',
      authorLabel: 'yoni',
      lang: 'he',
    };

    expect(getSpeakableItemText(item)).toBe('yoni אומר: שלום עולם');
  });

  it('omits the author prefix when author label is empty', () => {
    const item: SpeakableItem = {
      text: 'Hello world',
      authorLabel: '',
      lang: 'en',
    };

    expect(getSpeakableItemText(item)).toBe('Hello world');
  });
});
