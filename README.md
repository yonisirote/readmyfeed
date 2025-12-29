# ReadMyFeed

Read your X/Twitter home feed out loud.

## Dev

- Smoke test (verifies X feed calls work):
  - `npm run smoke`

- Smoke test pagination loop (mocked):
  - `npm run smoke:pagination:mock`

- Run backend:
  - `npm run dev`
  - `GET http://localhost:3001/healthz`
  - `GET http://localhost:3001/api/feed?count=5`

## Pagination contract

`GET /api/feed` returns:

- `tweets`: current page of tweets
- `nextCursor`: cursor string for the next page (if any)
- `hasMore`: boolean indicating whether the client should fetch `nextCursor`

Client loop:

1. Fetch first page: `GET /api/feed?count=10`
2. Read all returned `tweets`
3. If `hasMore` is true, fetch next page using:
   - `GET /api/feed?count=10&cursor=<nextCursor>`
4. Repeat until `hasMore` is false

Clients should de-dupe by `tweet.id` across pages.

## Config

Create a root `.env` with:

- `API_KEY=...` (from X Auth Helper)
- `PORT=3001` (optional)
