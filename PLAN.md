# ReadMyFeed: X Login Cookie Auth Plan

## Goal

Replace the API key flow with an in-app X login via WebView, capture the
authenticated cookies (`auth_token`, `ct0`, optional `kdt`, `twid`), and use
them directly for GraphQL timeline fetches. Persist the cookie-based auth
securely on device and keep the rest of the feed + TTS architecture intact.
Ensure Google sign-in popups complete inside the login modal and close the
login view automatically once cookies are available.

## Status

- Core cookie-based login and timeline fetch are implemented.
- Google sign-in popup is bridged back into the login WebView.
- Login modal auto-closes after cookies are resolved.
- README update remains (see Migration steps).

## Non-goals

- Do not implement Facebook/Telegram fetchers yet.
- Do not add server-side auth or proxies.
- Do not redesign the theme beyond what is required for the login flow.

## Dependency updates

- Add `react-native-webview` for in-app login.
- Add `@react-native-cookies/cookies` for native cookie access.
- Update Expo config as needed for native modules (config plugin + prebuild).
- Requires a custom dev client or EAS build (Expo Go will not include cookies).

## Target architecture updates

### Core feed contracts (platform-agnostic)

File: `client/src/services/feed/feedTypes.ts`

- `FeedSource`: `x | facebook | telegram` (unchanged).
- Replace `XAuth` with cookie auth:
  - `authToken: string` (required, from `auth_token`).
  - `csrfToken: string` (required, from `ct0`).
  - `kdt?: string` (optional).
  - `twid?: string` (optional).
- Cookie header is derived at request time and not stored.
- `FacebookAuth`, `TelegramAuth`: unchanged placeholders.
- `FetchParams`: same discriminated union, but `x` auth now uses the cookie type.

### X auth and cookie helpers

File: `client/src/services/xHome/xHomeCookies.ts` (repurpose)

New/updated types:
- `XCookieJar`: alias for the cookie map returned by `CookieManager.get()`.
- `ParsedCookies`:
  - `authToken?: string`
  - `csrfToken?: string`
  - `kdt?: string`
  - `twid?: string`
  - `hasAuthToken: boolean`
  - `hasKdt: boolean`
  - `hasTwid: boolean`

New functions:
- `parseCookiesFromJar(cookies: XCookieJar): ParsedCookieValues`
- `toXAuth(parsed: ParsedCookieValues): XAuth` (throws if required tokens missing)
- `buildCookieHeaderFromAuth(auth: XAuth): ParsedCookies`

### X login session helpers

New file: `client/src/services/auth/xLoginSession.ts`

New types:
- `XLoginState = 'idle' | 'in_progress' | 'success' | 'error'`

New functions:
- `getXLoginUrl(): string` (default `https://x.com/i/flow/login`, fallback to `twitter.com` if needed)
- `isLoginComplete(url: string): boolean` (detect `x.com/home` or `twitter.com/home`)
- `readXCookies(): Promise<XCookieJar>` (via `CookieManager.get`, merge `x.com` + `twitter.com`, enable WebKit on iOS)
- `clearXCookies(): Promise<void>` (clear `x.com` + `twitter.com`; fallback to `clearAll()` only if needed)
- `resolveXAuthFromCookies(): Promise<XAuth>` (read + parse + validate; throw a retryable error if tokens missing)

### X timeline fetcher updates

File: `client/src/services/xHome/xHomeTypes.ts`

- Replace `apiKey` with `auth: XAuth` in `XHomeTimelineOptions`.

File: `client/src/services/xHome/xHomeTimeline.ts`

- Remove `decodeApiKeyToCookies` usage.
- Build the `Cookie` header from `auth` via `buildCookieHeaderFromAuth`.
- Use `auth.csrfToken` for `x-csrf-token`.
- Log only the presence of cookie parts, never the values.

File: `client/src/services/sources/x/xFetcher.ts`

- Pass `auth` directly into `fetchXHomeTimeline`.

### Auth persistence (secure storage)

File: `client/src/services/auth/authStore.ts`

- No changes to storage API, but now stores the cookie-based `XAuth` object.
- Keep the existing web guard; do not persist cookies in web builds.

### UI updates (X only)

File: `client/App.tsx`

- Replace the API key input with a login panel:
  - "Login to X" primary button (opens WebView modal).
  - "Connected" state once `XAuth` is saved.
  - "Log out" secondary button clears stored auth and cookies.
- WebView setup:
  - Enable `sharedCookiesEnabled` (iOS) and `thirdPartyCookiesEnabled` (Android).
  - Ensure the WebView uses the shared cookie store before reading cookies.
- WebView modal flow:
  - Load login URL from `getXLoginUrl()`.
  - On navigation change, check `isLoginComplete(url)`.
  - If complete, call `resolveXAuthFromCookies()`, save via `saveAuth('x', auth)`.
  - Close modal and update state; handle errors with inline status text.
  - Relay Google popup `postMessage` events to the opener so login completes
    without leaving the modal.
  - Attempt login completion after popup close or when the WebView finishes
    loading with cookies present.
- Load stored auth on app start and skip login if already present.

## Migration steps (implementation order)

1. Update `feedTypes.ts` and `xHomeTypes.ts` with cookie-based `XAuth`.
2. Repurpose `xHomeCookies.ts` with cookie parsing + header building helpers.
3. Add `xLoginSession.ts` for WebView login + cookie resolution.
4. Update `xHomeTimeline.ts` + `xFetcher.ts` to use `auth` instead of API key.
5. Update `App.tsx` UI and state flow for login/logout and modal WebView.
6. Update docs in `README.md` to describe the new login flow and remove
   API key extension instructions. (Pending)

## Testing and verification

- Manual checks on device/simulator:
  - WebView login completes and returns cookies.
  - Fetch timeline succeeds with cookie auth.
  - Logout clears stored auth and forces re-login.
  - Auto-fetch and TTS behavior unchanged.
- Use a custom dev client/EAS build when validating cookie access.
- Add/update unit tests for `parseCookiesFromJar` if feasible.
