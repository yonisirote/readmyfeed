import { useEffect, useState } from 'react';
import './App.css';

import { fetchXHomeTimeline } from './services/xHomeTimeline.js';
import { Tts } from './native/tts.js';

const nativeLog = async (message: string): Promise<void> => {
  try {
    const logger = (window as any)?.__RMF_NATIVE_LOG__;
    if (typeof logger === 'function') {
      logger(message);
    } else {
      console.log('[rmf] nativeLog: __RMF_NATIVE_LOG__ missing');
    }
  } catch (error) {
    const msg = error instanceof Error ? error.message : String(error);
    console.log(`[rmf] nativeLog failed: ${msg}`);
  }
};


type TweetSample = {
  id: string;
  user?: string;
  text?: string;
};


declare global {
  interface Window {
    __RMF_API_KEY__?: string;
    __RMF_AUTO_RUN__?: boolean;
  }
}

function App() {
  const [apiKey, setApiKey] = useState('');
  const [count] = useState(5);
  const [cursor, setCursor] = useState<string | undefined>(undefined);
  void cursor;
  const [isLoading, setIsLoading] = useState(false);
  const [tweets, setTweets] = useState<TweetSample[]>([]);
  const [error, setError] = useState<string | undefined>(undefined);

  const [isSpeaking, setIsSpeaking] = useState(false);
  const [speakIndex, setSpeakIndex] = useState(0);

  const setErrorMessage = (message: string | undefined) => {
    setError(message);
    if (message) {
      void nativeLog(`ui error: ${message}`);
    }
  };

  const buildSpokenText = (tweet: TweetSample): string => {
    const user = tweet.user ? `@${tweet.user}` : '@unknown';
    const body = tweet.text ?? '';
    return `${user}: ${body}`;
  };

  const speakCurrentTweet = async (tweetIndex: number): Promise<void> => {
    if (tweets.length === 0) {
      setErrorMessage('No tweets loaded.');
      return;
    }

    const clampedIndex = Math.min(Math.max(tweetIndex, 0), tweets.length - 1);
    const tweet = tweets[clampedIndex];

    if (!tweet) {
      setErrorMessage('Invalid tweet index.');
      return;
    }

    const utteranceId = tweet.id || `tweet_${clampedIndex}`;
    const text = buildSpokenText(tweet);

    await nativeLog(`tts speak idx=${clampedIndex} utteranceId=${utteranceId}`);
    await Tts.speak({ text, utteranceId });
  };

  const fetchOnceNative = async (nextCursor?: string, apiKeyOverride?: string) => {
    const effectiveApiKey = (apiKeyOverride ?? apiKey).trim();
    await nativeLog(`fetchOnceNative invoked apiKeyLen=${effectiveApiKey.length}`);

    if (!effectiveApiKey) {
      setErrorMessage('Missing API key.');
      return;
    }

    setErrorMessage(undefined);
    setIsLoading(true);

    try {
      const result = await fetchXHomeTimeline({
        apiKey: effectiveApiKey,
        count,
        cursor: nextCursor,
        log: (message) => {
          void nativeLog(message);
        },
      });

      setCursor(result.nextCursor);
      setTweets(result.tweetSamples);

      for (const t of result.tweetSamples) {
        void nativeLog(`tweet id=${t.id} user=${t.user ?? '(unknown)'} text=${t.text ?? ''}`);
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      setErrorMessage(`Native fetch failed: ${message}`);
      console.log(`[rmf] native fetch failed: ${message}`);
      await nativeLog(`native fetch failed: ${message}`);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    const keyFromIntent = typeof window.__RMF_API_KEY__ === 'string' ? window.__RMF_API_KEY__ : '';
    const shouldAutoRun = Boolean(window.__RMF_AUTO_RUN__);

    let removeDone: (() => Promise<void>) | undefined;
    let removeError: (() => Promise<void>) | undefined;

    void (async () => {
      const doneListener = await Tts.addListener('ttsDone', async () => {
        // Advance and speak the next tweet
        setSpeakIndex((prev) => prev + 1);
      });
      removeDone = doneListener.remove;

      const errorListener = await Tts.addListener('ttsError', (evt) => {
        const message = typeof evt?.message === 'string' ? evt.message : 'Unknown TTS error';
        setErrorMessage(`TTS error: ${message}`);
        setIsSpeaking(false);
      });
      removeError = errorListener.remove;
    })();

    if (keyFromIntent && !apiKey.trim()) {
      setApiKey(keyFromIntent);
      console.log('[rmf] API key received from intent extras');
    }

    if (shouldAutoRun && keyFromIntent) {
      console.log('[rmf] auto-run: starting native X fetch');
      void nativeLog(`auto-run calling fetchOnceNative keyLen=${keyFromIntent.length}`);
      void fetchOnceNative(undefined, keyFromIntent);
    }

    return () => {
      void removeDone?.();
      void removeError?.();
    };
    // Only run once on mount.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const onFetchFirstPage = async () => {
    setCursor(undefined);
    setSpeakIndex(0);
    await fetchOnceNative(undefined);
  };

  const onSpeak = async () => {
    setErrorMessage(undefined);

    if (tweets.length === 0) {
      setErrorMessage('No tweets loaded.');
      return;
    }

    const indexToSpeak = Math.min(Math.max(speakIndex, 0), tweets.length - 1);

    setIsSpeaking(true);
    setSpeakIndex(indexToSpeak);

    try {
      await speakCurrentTweet(indexToSpeak);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      setErrorMessage(`TTS failed: ${message}`);
      setIsSpeaking(false);
    }
  };

  useEffect(() => {
    if (!isSpeaking) return;
    if (tweets.length === 0) return;

    if (speakIndex >= tweets.length) {
      setIsSpeaking(false);
      setSpeakIndex(0);
      return;
    }

    void (async () => {
      try {
        await speakCurrentTweet(speakIndex);
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        setErrorMessage(`TTS failed: ${message}`);
        setIsSpeaking(false);
      }
    })();
  }, [isSpeaking, speakIndex, tweets]);

  const onStop = async () => {
    setErrorMessage(undefined);
    try {
      await Tts.stop();
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      setErrorMessage(`TTS stop failed: ${message}`);
    } finally {
      setIsSpeaking(false);
    }
  };


  return (
    <div style={{ maxWidth: 720, margin: '0 auto', padding: 16 }}>
      <h1 style={{ marginBottom: 8 }}>ReadMyFeed — Phase 0</h1>
      <p style={{ marginTop: 0 }}>
        Spike: run <code>rettiwt-api</code> directly in a Capacitor WebView.
      </p>

      <div style={{ display: 'grid', gap: 12 }}>
        <label style={{ display: 'grid', gap: 6 }}>
          <span>API key (paste from X Auth Helper)</span>
          <textarea
            value={apiKey}
            onChange={(e) => setApiKey(e.target.value)}
            placeholder="Paste API key"
            rows={3}
            style={{ width: '100%', fontFamily: 'monospace' }}
            autoCapitalize="none"
            autoCorrect="off"
            spellCheck={false}
          />
        </label>

         <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
           <button onClick={onFetchFirstPage} disabled={isLoading || !apiKey.trim()}>
             Fetch first 5 tweets
           </button>

           <button onClick={onSpeak} disabled={isLoading || tweets.length === 0}>
             Speak (from {Math.min(speakIndex + 1, Math.max(tweets.length, 1))}/{Math.max(tweets.length, 1)})
           </button>

           <button onClick={onStop} disabled={!isSpeaking}>
             Stop
           </button>
         </div>


        <div style={{ fontFamily: 'monospace', fontSize: 12 }}>
          <div style={{ marginBottom: 8 }}>
            <strong>Status:</strong> {isLoading ? 'loading…' : 'idle'}
            {error ? ` | ERROR: ${error}` : ''}
          </div>

          <div
            style={{
              border: '1px solid rgba(255,255,255,0.2)',
              borderRadius: 8,
              padding: 12,
              minHeight: 220,
              whiteSpace: 'pre-wrap',
            }}
          >
            {tweets.length === 0
              ? 'No tweets loaded.'
              : tweets
                  .slice(0, 5)
                  .map((t, idx) => {
                    const header = `${idx + 1}. @${t.user ?? '(unknown)'} — ${t.id}`;
                    const body = t.text ?? '';
                    return `${header}\n${body}`;
                  })
                  .join('\n\n')}
          </div>
        </div>
      </div>
    </div>
  );
}

export default App;
