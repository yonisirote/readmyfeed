import cors from 'cors';
import express from 'express';

import { serverConfig } from './config/server.js';
import { feedRouter } from './routes/feed.js';

const app = express();

app.use(cors());
app.use(express.json());

app.get('/healthz', (_req, res) => {
  res.json({ ok: true });
});

app.use('/api/feed', feedRouter);

app.get('/tts', (_req, res) => {
  res.sendFile('tts.html', {
    root: new URL('./pages', import.meta.url).pathname,
  });
});

app.listen(serverConfig.port, () => {
  console.log(`server listening on :${serverConfig.port}`);
});
