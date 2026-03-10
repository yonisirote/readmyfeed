import * as Speech from 'expo-speech';

import { TtsService, ttsErrorCodes } from '../src/tts';

jest.mock('expo-speech', () => ({
  getAvailableVoicesAsync: jest.fn(),
  speak: jest.fn(),
  stop: jest.fn(),
  VoiceQuality: {
    Default: 'Default',
    Enhanced: 'Enhanced',
  },
}));

describe('TtsService', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.mocked(Speech.getAvailableVoicesAsync).mockResolvedValue([]);
    jest.mocked(Speech.speak).mockImplementation((_text, options) => {
      options?.onDone?.();
    });
    jest.mocked(Speech.stop).mockResolvedValue();
  });

  it('initializes by loading available voices', async () => {
    const service = new TtsService();

    await service.initialize();

    expect(Speech.getAvailableVoicesAsync).toHaveBeenCalledTimes(1);
  });

  it('speaks using expo-speech options', async () => {
    const service = new TtsService();

    await service.initialize();
    await service.speak('hello world', {
      language: 'en-US',
      pitch: 1.1,
      rate: 0.9,
      voice: 'voice-id',
      volume: 0.5,
    });

    expect(Speech.speak).toHaveBeenCalledWith(
      'hello world',
      expect.objectContaining({
        language: 'en-US',
        pitch: 1.1,
        rate: 0.9,
        voice: 'voice-id',
        volume: 0.5,
      }),
    );
  });

  it('resolves hebrew to an installed voice', async () => {
    jest.mocked(Speech.getAvailableVoicesAsync).mockResolvedValue([
      {
        identifier: 'he-il-language',
        language: 'iw-IL',
        name: 'Hebrew Placeholder',
        quality: Speech.VoiceQuality.Default,
      },
      {
        identifier: 'hebrew-voice',
        language: 'iw-IL',
        name: 'Hebrew',
        quality: Speech.VoiceQuality.Default,
      },
    ]);

    const service = new TtsService();

    await service.initialize();
    await service.speak('שלום', {
      language: 'he-IL',
    });

    const [, options] = jest.mocked(Speech.speak).mock.calls[0];
    expect(options).toEqual(
      expect.objectContaining({
        language: 'iw-IL',
        voice: 'hebrew-voice',
      }),
    );
  });

  it('prefers a local hebrew voice over a generic language entry', async () => {
    jest.mocked(Speech.getAvailableVoicesAsync).mockResolvedValue([
      {
        identifier: 'he-il-language',
        language: 'iw-IL',
        name: 'Hebrew Language',
        quality: Speech.VoiceQuality.Default,
      },
      {
        identifier: 'he-il-heb-local',
        language: 'iw-IL',
        name: 'Hebrew Local',
        quality: Speech.VoiceQuality.Default,
      },
    ]);

    const service = new TtsService();

    await service.initialize();
    await service.speak('שלום', {
      language: 'he-IL',
    });

    const [, options] = jest.mocked(Speech.speak).mock.calls[0];
    expect(options).toEqual(
      expect.objectContaining({
        language: 'iw-IL',
        voice: 'he-il-heb-local',
      }),
    );
  });

  it('prefers an exact US English voice over other English voices', async () => {
    jest.mocked(Speech.getAvailableVoicesAsync).mockResolvedValue([
      {
        identifier: 'australian-voice',
        language: 'en-AU',
        name: 'Australian English',
        quality: Speech.VoiceQuality.Default,
      },
      {
        identifier: 'us-voice',
        language: 'en-US',
        name: 'US English',
        quality: Speech.VoiceQuality.Default,
      },
    ]);

    const service = new TtsService();

    await service.initialize();
    await service.speak('hello', {
      language: 'en-US',
    });

    const [, options] = jest.mocked(Speech.speak).mock.calls[0];
    expect(options).toEqual(
      expect.objectContaining({
        language: 'en-US',
        voice: 'us-voice',
      }),
    );
  });

  it('keeps the requested language when no exact voice match is installed', async () => {
    const service = new TtsService();

    await service.initialize();
    await service.speak('שלום', {
      language: 'he-IL',
    });

    const [, options] = jest.mocked(Speech.speak).mock.calls[0];
    expect(options?.language).toBe('he-IL');
    expect(options?.voice).toBeUndefined();
  });

  it('reports whether hebrew is installed', async () => {
    jest.mocked(Speech.getAvailableVoicesAsync).mockResolvedValue([
      {
        identifier: 'hebrew-voice',
        language: 'iw-IL',
        name: 'Hebrew',
        quality: Speech.VoiceQuality.Default,
      },
    ]);

    const service = new TtsService();

    await service.initialize();

    expect(service.hasLanguageSupport('he-IL')).toBe(true);
    expect(service.hasLanguageSupport('en-US')).toBe(false);
  });

  it('throws when speaking before initialization', async () => {
    const service = new TtsService();

    await expect(service.speak('hello')).rejects.toMatchObject({
      code: ttsErrorCodes.NotInitialized,
    });
  });

  it('stops active speech', async () => {
    const service = new TtsService();

    await service.initialize();
    await service.stop();

    expect(Speech.stop).toHaveBeenCalledTimes(1);
  });

  it('stops exposing installed language support after deinitialize', async () => {
    jest.mocked(Speech.getAvailableVoicesAsync).mockResolvedValue([
      {
        identifier: 'hebrew-voice',
        language: 'iw-IL',
        name: 'Hebrew',
        quality: Speech.VoiceQuality.Default,
      },
    ]);

    const service = new TtsService();

    await service.initialize();
    service.deinitialize();

    await expect(service.speak('hello')).rejects.toMatchObject({
      code: ttsErrorCodes.NotInitialized,
    });
  });
});
