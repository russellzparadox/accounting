export type RateResult = { allowed: true } | { allowed: false; retryAfterSec: number };

/** Fixed window, N requests per windowMs per key. Clock injectable for tests. */
export class FixedWindowLimiter {
  private readonly hits = new Map<string, { start: number; count: number }>();

  constructor(
    private readonly limit: number,
    private readonly windowMs: number,
    private readonly now: () => number = Date.now,
  ) {}

  check(key: string): RateResult {
    const t = this.now();
    const e = this.hits.get(key);
    if (e === undefined || t - e.start >= this.windowMs) {
      this.hits.set(key, { start: t, count: 1 });
      return { allowed: true };
    }
    if (e.count >= this.limit) {
      return { allowed: false, retryAfterSec: Math.max(1, Math.ceil((e.start + this.windowMs - t) / 1000)) };
    }
    e.count += 1;
    return { allowed: true };
  }
}
