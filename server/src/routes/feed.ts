import { Router } from 'express';

import { getFeed } from '../handlers/feed.js';

export const feedRouter = Router();

feedRouter.get('/', getFeed);
