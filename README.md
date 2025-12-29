# ReadMyFeed

Read your X/Twitter home feed out loud.

## Dev

- Smoke test (verifies X feed calls work):
  - `npm run smoke`

- Run backend:
  - `npm run dev`
  - `GET http://localhost:3001/healthz`
  - `GET http://localhost:3001/api/feed?count=5`

## Config

Create a root `.env` with:

- `API_KEY=...` (from X Auth Helper)
- `PORT=3001` (optional)
