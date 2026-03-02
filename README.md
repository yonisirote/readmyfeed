# ReadMyFeed

An Expo (React Native) app that connects to social media feeds (currently X/Twitter) and displays them in a unified interface.

## Features

- Unified feed view for multiple social platforms
- X/Twitter authentication and timeline integration
- Service-based architecture with dependency injection for testability

## Prerequisites

- Node.js 18+
- Expo CLI
- Android Studio (for Android development)
- Xcode (for iOS development)

## Installation

```bash
npm install
```

## Running the App

```bash
# Start development server
npm start

# Run on Android
npm run android

# Run on iOS
npm run ios

# Run web version
npm run web
```

## Development

```bash
# Run linter
npm run lint

# Format code
npm run format

# Run tests
npm test
```

## Architecture

```
app/                    # Expo Router screens
  (auth)/               # Auth flow screens
src/
  services/             # Business logic
    x/
      auth/             # X authentication
      timeline/         # X timeline fetching
```

## Tech Stack

- Expo SDK 54
- React Native 0.81
- React 19
- TypeScript
- Expo Router (file-based routing)
- Expo Secure Store (secure storage)
