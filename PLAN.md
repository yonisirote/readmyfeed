# ReadMyFeed Conversion Progress

## What changed

- Converted the Capacitor/Vite spike into an Expo (React Native) app in `client/`.
- Removed the legacy backup server and Android native project artifacts.
- Added a minimal UI for API key input + tweet samples.
- Ported the X home timeline fetcher to React Native with polyfills and Expo-friendly networking.
- Added a CLI fetch path using the same fetcher for quick verification.
- Introduced Jest tests for timeline parsing and author extraction.
- Added EAS build config for APK generation (preview profile).

## Key implementation notes

- Direct X fetch uses the Rettiwt query id and headers, with `x-client-transaction-id` generation.
- Networking uses `expo-file-system` on device to preserve cookie headers.
- Polyfills include `crypto.subtle.digest` and `ArrayBuffer.prototype.transfer` for mobile compatibility.
- Timeline parsing is split into `xTimelineParsing.ts` with tests to validate cursor + author extraction.

## How to run

- Expo app: `npm --prefix client run start`
- Android: `npm --prefix client run android`
- CLI fetch: `API_KEY=... npm --prefix client run fetch:cli`
- Tests: `npm --prefix client test`

## APK builds

- `client/eas.json` contains a `preview` profile that builds an APK.
- Use `npx eas build -p android --profile preview` from `client/`.
- Local credentials are stored in `client/credentials.json` (gitignored).
