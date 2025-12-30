import { Router } from 'express';

import { getFeed } from '../handlers/feedHandler.js';

export const feedRouter = Router();

feedRouter.get('/', getFeed);
