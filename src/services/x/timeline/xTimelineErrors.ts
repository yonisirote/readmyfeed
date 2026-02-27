export class XTimelineError extends Error {
  public readonly code: string;
  public readonly context?: Record<string, unknown>;

  public constructor(message: string, code: string, context?: Record<string, unknown>) {
    super(message);
    this.name = 'XTimelineError';
    this.code = code;
    this.context = context;
  }
}

export const X_TIMELINE_ERROR_CODES = {
  SessionMissing: 'X_TIMELINE_SESSION_MISSING',
  CookieInvalid: 'X_TIMELINE_COOKIE_INVALID',
  RequestFailed: 'X_TIMELINE_REQUEST_FAILED',
  ResponseInvalid: 'X_TIMELINE_RESPONSE_INVALID',
} as const;
