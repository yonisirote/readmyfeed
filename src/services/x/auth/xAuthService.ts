import { X_AUTH_ERROR_CODES, XAuthError } from './xAuthErrors';
import { createXAuthLogger, XAuthLogger } from './xAuthLogger';
import { readXCookies } from './xAuthCookieManager';
import { createXAuthSessionStore, XAuthSessionStore } from './xAuthSessionStore';
import { XAuthSession, XCookieReadResult } from './xAuthTypes';
import { createSessionFromCookies, looksLikeLoggedInUrl } from './xAuthUtils';

export type XAuthServiceOptions = {
  logger?: XAuthLogger;
  useWebKit?: boolean;
  store?: XAuthSessionStore;
};

export type XAuthCaptureResult = {
  session?: XAuthSession;
  cookieResult: XCookieReadResult;
};

export class XAuthService {
  private readonly logger: XAuthLogger;
  private readonly useWebKit: boolean;
  private readonly store: XAuthSessionStore;

  public constructor(options: XAuthServiceOptions = {}) {
    this.logger = options.logger ?? createXAuthLogger();
    this.useWebKit = options.useWebKit ?? true;
    this.store = options.store ?? createXAuthSessionStore(this.logger);
  }

  public shouldAttemptCapture(url?: string | null): boolean {
    const shouldCapture = looksLikeLoggedInUrl(url);
    this.logger.debug('Evaluated capture hint', { url, shouldCapture });
    return shouldCapture;
  }

  public async captureSession(): Promise<XAuthCaptureResult> {
    this.logger.info('Attempting to capture X session');

    const cookieResult = await readXCookies({ useWebKit: this.useWebKit, logger: this.logger });

    if (!cookieResult.hasRequired) {
      this.logger.warn('Missing required X cookies', {
        missingRequired: cookieResult.missingRequired,
        missingOptional: cookieResult.missingOptional,
      });
      return { cookieResult };
    }

    const session = createSessionFromCookies(cookieResult.cookies);
    this.logger.info('X session created', { cookieNames: session.cookieNames });
    return { session, cookieResult };
  }

  public async captureAndStoreSession(): Promise<XAuthSession> {
    const { session, cookieResult } = await this.captureSession();

    if (!session) {
      throw new XAuthError('Missing required cookies', X_AUTH_ERROR_CODES.CookieMissingRequired, {
        missingRequired: cookieResult.missingRequired,
        missingOptional: cookieResult.missingOptional,
      });
    }

    await this.store.set(session.encodedCookie);
    return session;
  }

  public async loadStoredSession(): Promise<string | null> {
    return this.store.get();
  }

  public async clearStoredSession(): Promise<void> {
    await this.store.clear();
  }
}
