type LogPayload = Record<string, unknown>;

export type XAuthLogLevel = 'debug' | 'info' | 'warn' | 'error';

export type XAuthLogger = {
  debug: (message: string, payload?: LogPayload) => void;
  info: (message: string, payload?: LogPayload) => void;
  warn: (message: string, payload?: LogPayload) => void;
  error: (message: string, payload?: LogPayload) => void;
};

const formatPayload = (payload?: LogPayload) => {
  if (!payload || Object.keys(payload).length === 0) {
    return '';
  }

  try {
    return ` ${JSON.stringify(payload)}`;
  } catch (err) {
    return ' [payload-unserializable]';
  }
};

const emit = (level: XAuthLogLevel, message: string, payload?: LogPayload) => {
  const prefix = `[XAuth][${level.toUpperCase()}]`;
  const line = `${prefix} ${message}${formatPayload(payload)}`;

  if (level === 'error') {
    console.error(line);
  } else if (level === 'warn') {
    console.warn(line);
  } else if (level === 'info') {
    console.info(line);
  } else {
    console.log(line);
  }
};

export const createXAuthLogger = (): XAuthLogger => ({
  debug: (message, payload) => emit('debug', message, payload),
  info: (message, payload) => emit('info', message, payload),
  warn: (message, payload) => emit('warn', message, payload),
  error: (message, payload) => emit('error', message, payload),
});
