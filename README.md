# ReadMyFeed

Read your X/Twitter home feed out loud.

## Current Features

- Expo app for fetching the X/Twitter home timeline
- Basic feed preview UI (X login + tweets list)
- Mobile-first networking with Expo FileSystem to preserve cookie headers

## Getting Started

### 1. Log in to X

The app now uses an in-app WebView to sign in and stores the X cookies
(`auth_token`, `ct0`) securely on device.

Note: This flow requires a custom dev client or EAS build because Expo Go does
not include the native cookie module.

### 2. Install dependencies

```bash
cd client
npm install
```

### 3. Start the Expo app

```bash
cd client
npm run start
```

Open the app on a device/simulator, tap "Login to X", complete the sign-in, and
fetch the first page.

Note: The CLI fetch script was removed when the fetcher switched to Expo-only networking.
Use the app (device or emulator) to verify fetches.

### 4. Run tests

```bash
cd client
npm test
```

### 5. Build an APK (EAS)

```bash
cd client
npx eas build -p android --profile preview
```
