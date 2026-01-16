import { getNumberEnv } from './env.js';

export const serverConfig = {
  port: getNumberEnv('PORT', 3001),
};
