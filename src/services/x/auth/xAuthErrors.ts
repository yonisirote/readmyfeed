export class XAuthError extends Error {
  public readonly code: string;
  public readonly context?: Record<string, unknown>;

  public constructor(message: string, code: string, context?: Record<string, unknown>) {
    super(message);
    this.name = 'XAuthError';
    this.code = code;
    this.context = context;
  }
}

export const X_AUTH_ERROR_CODES = {
  CookieReadFailed: 'X_AUTH_COOKIE_READ_FAILED',
  CookieMissingRequired: 'X_AUTH_COOKIE_MISSING_REQUIRED',
  CookieStringInvalid: 'X_AUTH_COOKIE_STRING_INVALID',
  WebViewNotReady: 'X_AUTH_WEBVIEW_NOT_READY',
  SecureStoreFailed: 'X_AUTH_SECURE_STORE_FAILED',
} as const;
