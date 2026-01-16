export type RateLimiter = {
  wait(): Promise<void>;
};

export function createMinIntervalRateLimiter(options: { minIntervalMs: number }): RateLimiter {
  const minIntervalMs = Math.max(0, Math.floor(options.minIntervalMs));
  let nextAllowedAt = 0;
  let tail = Promise.resolve();

  const wait = async () => {
    if (minIntervalMs <= 0) return;

    // Serialize all waiters so concurrent requests don't bypass the limit.
    tail = tail.then(async () => {
      const now = Date.now();
      const delayMs = Math.max(0, nextAllowedAt - now);

      if (delayMs > 0) {
        await new Promise<void>((resolve) => setTimeout(resolve, delayMs));
      }

      nextAllowedAt = Date.now() + minIntervalMs;
    });

    await tail;
  };

  return { wait };
}
