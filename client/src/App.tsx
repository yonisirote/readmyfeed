import { useMemo, useState } from 'react';
import './App.css';

import { Http } from '@capacitor-community/http';

type LogLine = {
  level: 'info' | 'error';
  message: string;
};

function App() {
  const [apiKey, setApiKey] = useState('');
  const [count, setCount] = useState(5);
  const [cursor, setCursor] = useState<string | undefined>(undefined);
  const [isLoading, setIsLoading] = useState(false);
  const [logs, setLogs] = useState<LogLine[]>([]);

  // Note: `rettiwt-api` cannot run in a WebView (Node-only deps).
  // Keep placeholder here so UI stays stable.
  useMemo(() => null, []);

  const appendLog = (line: LogLine) => {
    setLogs((prev) => [{ ...line }, ...prev].slice(0, 200));
  };

  const fetchOnce = async (_nextCursor?: string) => {
    appendLog({
      level: 'error',
      message: 'Web fetch disabled in Capacitor: rettiwt-api breaks in WebView. Use Native fetch.',
    });
  };

  const fetchOnceNative = async (nextCursor?: string) => {
    if (!apiKey.trim()) {
      appendLog({ level: 'error', message: 'Missing API key.' });
      return;
    }

    setIsLoading(true);
    appendLog({
      level: 'info',
      message: `Fetching (native http)… count=${count} cursor=${nextCursor ? 'set' : 'none'}`,
    });

    try {
      // NOTE: Keep this minimal for now: just prove native HTTP can bypass CORS.
      // We call our existing backend route (or you can later point this directly to x.com).
      const baseUrl = 'http://10.0.2.2:3001';
      const url = new URL('/api/feed', baseUrl);

      const params: Record<string, string> = {
        count: String(count),
      };
      if (nextCursor) params.cursor = nextCursor;

      appendLog({ level: 'info', message: `Native GET ${url.toString()} params=${JSON.stringify(params)}` });

      const res = await Http.get({
        url: url.toString(),
        params,
        headers: {},
        connectTimeout: 15000,
        readTimeout: 15000,
      });

      appendLog({ level: 'info', message: `Native response status=${res.status}` });
      appendLog({ level: 'info', message: `Native response keys=${Object.keys(res ?? {}).join(',')}` });

      const data = res.data as any;
      const tweets = Array.isArray(data?.tweets) ? data.tweets : [];
      appendLog({ level: 'info', message: `Native parsed tweets=${tweets.length} hasMore=${Boolean(data?.hasMore)}` });

      const next = typeof data?.nextCursor === 'string' ? data.nextCursor : undefined;
      setCursor(next);
    } catch (error) {
      appendLog({
        level: 'error',
        message: `Native fetch failed: ${error instanceof Error ? error.message : String(error)}`,
      });
    } finally {
      setIsLoading(false);
    }
  };

  const onFetchFirstPage = async () => {
    setCursor(undefined);
    await fetchOnce(undefined);
  };

  const onFetchNextPage = async () => {
    await fetchOnce(cursor);
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

        <label style={{ display: 'grid', gap: 6 }}>
          <span>Count</span>
          <input
            type="number"
            value={count}
            onChange={(e) => setCount(Number(e.target.value))}
            min={1}
            max={35}
          />
        </label>

        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <button onClick={onFetchFirstPage} disabled={isLoading || !apiKey.trim()}>
            Fetch first page
          </button>
          <button onClick={onFetchNextPage} disabled={isLoading || !apiKey.trim() || !cursor}>
            Fetch next page
          </button>
          <button
            onClick={async () => {
              setCursor(undefined);
              await fetchOnceNative(undefined);
            }}
            disabled={isLoading}
          >
            Native fetch (server)
          </button>
          <button
            onClick={() => {
              setLogs([]);
            }}
            disabled={isLoading}
          >
            Clear logs
          </button>
        </div>

        <div style={{ fontFamily: 'monospace', fontSize: 12 }}>
          <div style={{ marginBottom: 8 }}>
            <strong>Status:</strong> {isLoading ? 'loading…' : 'idle'} | <strong>Cursor:</strong>{' '}
            {cursor ? `${cursor.slice(0, 12)}…` : '(none)'}
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
            {logs.length === 0
              ? 'Logs will appear here.'
              : logs
                  .map((l) => `${l.level.toUpperCase()}: ${l.message}`)
                  .join('\n')}
          </div>
        </div>
      </div>
    </div>
  );
}

export default App;
