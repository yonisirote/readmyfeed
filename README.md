# ReadMyFeed

Read your X/Twitter home feed out loud.

## Current Features

- Expo app for fetching the X/Twitter home timeline
- Basic feed preview UI (API key input + tweets list)
- Mobile-first networking with Expo FileSystem to preserve cookie headers

## Getting Started

### 1. Get your API Key

You need an X/Twitter API key to fetch your feed. Follow these steps:

**For Chrome/Chromium:**
1. Install the [X Auth Helper extension](https://chromewebstore.google.com/detail/x-auth-helper/igpkhkjmpdecacocghpgkghdcmcmpfhp)
2. Open incognito mode and log in to X/Twitter
3. Click the extension and click "Get Key"
4. Copy the API key

**For Firefox:**
1. Install the [Rettiwt Auth Helper extension](https://addons.mozilla.org/en-US/firefox/addon/rettiwt-auth-helper)
2. Open private mode and log in to X/Twitter
3. Click the extension and click "Get API Key"
4. Copy the API key

Full instructions: https://github.com/Rishikant181/Rettiwt-API

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

Open the app on a device/simulator, paste your API key, and fetch the first page.

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
