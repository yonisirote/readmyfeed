# ReadMyFeed

Read your X/Twitter home feed out loud.

## Current Features

- Basic feed fetching from X/Twitter
- Browser-based text-to-speech (TTS)

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

### 2. Configure

Create a `.env` file in the `server/` directory with your API key:

```bash
API_KEY=your_api_key_here
```

### 3. Start the Server

```bash
cd server
npm install
npm run dev
```

### 4. Try it out

Open your browser and go to: `http://localhost:3001/tts.html`

You'll be able to fetch your feed and have it read aloud using browser TTS.
