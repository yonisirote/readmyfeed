import './style.css';

import ClientTransaction from 'x-client-transaction-id';

type LogLevel = 'info' | 'error';

type LogLine = {
  level: LogLevel;
  message: string;
};

type ParsedCookies = {
  rawCookieHeader: string;
  csrfToken?: string;
};

const safeBodyPrefix = (text: string, maxLen = 800) => {
  const cleaned = text.replaceAll(/\s+/g, ' ').trim();
  return cleaned.slice(0, maxLen);
};

const app = document.querySelector<HTMLDivElement>('#app');
if (!app) throw new Error('Missing #app element');

const state = {
  apiKey: '',
  cursor: '' as string,
  count: 10,
  logs: [] as LogLine[],
};

const render = () => {
  app.innerHTML = `
    <div style="max-width: 760px; margin: 0 auto; padding: 16px; font-family: system-ui, -apple-system, Segoe UI, Roboto, sans-serif;">
      <h1 style="margin: 0 0 8px 0;">Mobile-friendly Rettiwt Proof</h1>
      <p style="margin: 0 0 16px 0; opacity: 0.8;">
        Desktop Chrome spike: generate <code>x-client-transaction-id</code> and attempt a followed-feed request with a pasted <code>API_KEY</code>.
      </p>

      <div style="display: grid; gap: 12px;">
        <label style="display: grid; gap: 6px;">
          <span>API key (base64 cookies from X Auth Helper)</span>
          <textarea id="apiKey" rows="3" placeholder="Paste API_KEY" style="width: 100%; font-family: ui-monospace, SFMono-Regular, Menlo, monospace;"></textarea>
        </label>

        <label style="display: grid; gap: 6px;">
          <span>Count</span>
          <input id="count" type="number" min="1" max="35" value="${state.count}" />
        </label>

        <div style="display: flex; gap: 8px; flex-wrap: wrap;">
          <button id="btnTx" type="button">Generate transaction id</button>
          <button id="btnFetch1" type="button">Fetch first page</button>
          <button id="btnFetch2" type="button">Fetch next page</button>
          <button id="btnClear" type="button">Clear logs</button>
        </div>

        <div style="font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px;">
          <div style="margin-bottom: 8px;"><strong>Cursor:</strong> ${state.cursor ? `${state.cursor.slice(0, 16)}…` : '(none)'}</div>
          <div id="logBox" style="border: 1px solid rgba(0,0,0,0.15); border-radius: 8px; padding: 12px; min-height: 220px; white-space: pre-wrap;"></div>
        </div>
      </div>
    </div>
  `;

  const apiKeyEl = document.querySelector<HTMLTextAreaElement>('#apiKey');
  const countEl = document.querySelector<HTMLInputElement>('#count');
  const logBox = document.querySelector<HTMLDivElement>('#logBox');

  if (!apiKeyEl || !countEl || !logBox) throw new Error('Missing UI elements');

  apiKeyEl.value = state.apiKey;
  apiKeyEl.oninput = () => {
    state.apiKey = apiKeyEl.value;
  };

  countEl.oninput = () => {
    state.count = Number(countEl.value);
  };

  logBox.textContent =
    state.logs.length === 0
      ? 'Logs will appear here.'
      : state.logs.map((l) => `${l.level.toUpperCase()}: ${l.message}`).join('\n');

  document.querySelector<HTMLButtonElement>('#btnClear')!.onclick = () => {
    state.logs = [];
    render();
  };

  document.querySelector<HTMLButtonElement>('#btnTx')!.onclick = async () => {
    await generateTransactionIdProbe();
    render();
  };

  document.querySelector<HTMLButtonElement>('#btnFetch1')!.onclick = async () => {
    state.cursor = '';
    await fetchFollowedTimeline(undefined);
    render();
  };

  document.querySelector<HTMLButtonElement>('#btnFetch2')!.onclick = async () => {
    await fetchFollowedTimeline(state.cursor || undefined);
    render();
  };
};

const appendLog = (level: LogLevel, message: string) => {
  state.logs = [{ level, message }, ...state.logs].slice(0, 250);
};

const decodeApiKeyToCookies = (apiKey: string): ParsedCookies => {
  // API_KEY is base64-encoded raw cookie string.
  // In real browsers/WebViews you generally cannot set the `cookie` header.
  // We only use this to extract CSRF token (`ct0`) for `x-csrf-token`.
  const decoded = atob(apiKey.trim());
  const rawCookieHeader = decoded;

  // Common X cookie name for CSRF token is `ct0`.
  // If present, we may need to send `x-csrf-token`.
  const match = rawCookieHeader.match(/(?:^|;\s*)ct0=([^;]+)/);
  const csrfToken = match?.[1];

  return { rawCookieHeader, csrfToken };
};

const getDocumentForX = async (): Promise<Document> => {
  // Fetch X homepage and parse it into a Document.
  const res = await fetch('https://x.com', {
    method: 'GET',
    credentials: 'omit',
  });

  if (!res.ok) throw new Error(`Failed to fetch x.com: ${res.status} ${res.statusText}`);

  const html = await res.text();
  const parser = new DOMParser();
  return parser.parseFromString(html, 'text/html');
};

const generateTransactionIdProbe = async () => {
  appendLog('info', `crypto.subtle available=${Boolean(globalThis.crypto?.subtle)}`);

  try {
    const doc = await getDocumentForX();
    const tx = await ClientTransaction.create(doc);

    // Use a stable path typical for X internal requests.
    const tid = await tx.generateTransactionId('GET', '/i/api/graphql/CRprHpVA12yhsub-KRERIg/HomeLatestTimeline');
    appendLog('info', `Generated tid length=${tid.length}`);
  } catch (err) {
    appendLog('error', `Transaction probe failed: ${err instanceof Error ? err.message : String(err)}`);
  }
};

const fetchFollowedTimeline = async (cursor?: string) => {
  if (!state.apiKey.trim()) {
    appendLog('error', 'Missing API_KEY.');
    return;
  }

  try {
    // Step 1: Generate transaction id.
    appendLog('info', 'Fetching x.com + generating transaction id…');
    const doc = await getDocumentForX();
    const tx = await ClientTransaction.create(doc);
    const transactionId = await tx.generateTransactionId('GET', '/i/api/graphql/CRprHpVA12yhsub-KRERIg/HomeLatestTimeline');

    // Step 2: Make the HomeLatestTimeline request.
    const url = new URL('https://x.com/i/api/graphql/CRprHpVA12yhsub-KRERIg/HomeLatestTimeline');
    url.searchParams.set(
      'variables',
      JSON.stringify({
        count: state.count,
        cursor: cursor,
        includePromotedContent: false,
        latestControlAvailable: true,
        withCommunity: false,
      }),
    );

    url.searchParams.set(
      'features',
      JSON.stringify({
        responsive_web_graphql_timeline_navigation_enabled: true,
        rweb_video_screen_enabled: false,
        view_counts_everywhere_api_enabled: true,
        longform_notetweets_consumption_enabled: true,
      }),
    );

    const { csrfToken } = decodeApiKeyToCookies(state.apiKey);
    appendLog('info', `CSRF token present=${Boolean(csrfToken)}`);

    appendLog('info', `Requesting feed… cursor=${cursor ? 'set' : 'none'}`);

    const feedRes = await fetch(url.toString(), {
      method: 'GET',
      headers: {
        'x-client-transaction-id': transactionId,
        'x-twitter-active-user': 'yes',
        'x-twitter-client-language': 'en',
        ...(csrfToken ? { 'x-csrf-token': csrfToken } : {}),
        // Authorization header is used by many X endpoints. Rettiwt tends to include it.
        // This bearer is the public one for web clients. X may rotate it.
        authorization:
          'Bearer AAAAAAAAAAAAAAAAAAAAANRILgAAAAAAvx3q1Y9V9w0j5mFj8R1YJw0sXfI%3D1O9v8v7T1pGkU8dS3V2b9cHq3rL2gY0mPpHk9Yt0',
        // NOTE: Browsers/WebViews do not allow setting the `cookie` header.
        // Auth must come from the WebView's cookie jar.
        referer: 'https://x.com/',
      },
      credentials: 'include',
    });

    appendLog('info', `Feed response status=${feedRes.status}`);

    const text = await feedRes.text();

      // Log some headers to help debug 403s.
      const headerSubset = {
        'content-type': feedRes.headers.get('content-type'),
        'x-rate-limit-limit': feedRes.headers.get('x-rate-limit-limit'),
        'x-rate-limit-remaining': feedRes.headers.get('x-rate-limit-remaining'),
        'x-rate-limit-reset': feedRes.headers.get('x-rate-limit-reset'),
      };
      appendLog('info', `Feed response headers=${JSON.stringify(headerSubset)}`);

      // Try parse JSON; if not JSON, surface body prefix (often HTML).
      let data: any = null;
      try {
        data = JSON.parse(text);
      } catch {
        appendLog('error', `Non-JSON response prefix: ${safeBodyPrefix(text)}`);
        return;
      }

    // Basic extraction attempt: find cursor + count items.
    const instructions: any[] =
      data?.data?.home?.home_timeline_urt?.instructions ?? data?.data?.home?.timeline?.instructions ?? [];

    const entries: any[] = [];
    for (const inst of instructions) {
      if (inst?.type === 'TimelineAddEntries' && Array.isArray(inst.entries)) {
        entries.push(...inst.entries);
      }
    }

    // Heuristic: cursor entry has entryId like "cursor-bottom-…" and content.value.
    const cursorEntry = entries.find((e) => String(e?.entryId ?? '').startsWith('cursor-bottom'));
    const nextCursor = cursorEntry?.content?.value ?? cursorEntry?.content?.itemContent?.value;

    // Heuristic: tweet entries have content.itemContent.tweet_results
    const tweetEntries = entries.filter((e) => e?.content?.itemContent?.tweet_results);

    appendLog('info', `Parsed entries=${entries.length} tweets=${tweetEntries.length} nextCursor=${nextCursor ? 'set' : 'none'}`);

    if (tweetEntries[0]) {
      const tweetResult = tweetEntries[0].content.itemContent.tweet_results.result;
      const legacy = tweetResult?.legacy;
      const core = tweetResult?.core?.user_results?.result?.legacy;
      appendLog(
        'info',
        `First tweet: user=${core?.screen_name ?? ''} text=${String(legacy?.full_text ?? '').slice(0, 120)}`,
      );
    }

    state.cursor = typeof nextCursor === 'string' ? nextCursor : '';
  } catch (err) {
    appendLog('error', `Fetch failed: ${err instanceof Error ? err.message : String(err)}`);
  }
};

render();
