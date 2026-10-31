import { describe, expect, it } from "vitest";
import { authenticate, parseTokens, tokenKey } from "../src/auth.js";
import { toRial, AmountError } from "../src/amount.js";
import { FixedWindowLimiter } from "../src/ratelimit.js";
import { todayTehran } from "../src/server.js";
import { loadConfig } from "../src/config.js";

describe("auth", () => {
  const tokens = parseTokens(" aaa111 , bbb222 ");
  it("parses comma list", () => expect(tokens).toEqual(["aaa111", "bbb222"]));
  it("no header -> 401", () => expect(authenticate(undefined, tokens).ok).toBe(false));
  it("wrong token -> 401", () => expect(authenticate("Bearer nope", tokens).ok).toBe(false));
  it("non-Bearer scheme -> 401", () => expect(authenticate("Basic aaa111", tokens).ok).toBe(false));
  it("right token -> ok with hashed key, raw token not in key", () => {
    const r = authenticate("Bearer bbb222", tokens);
    expect(r).toEqual({ ok: true, key: tokenKey("bbb222") });
    expect(r.ok && r.key.includes("bbb222")).toBe(false);
  });
  it("empty token list rejects everything (fail closed)", () => {
    expect(authenticate("Bearer ", parseTokens(""))).toEqual({ ok: false });
  });
});

describe("rate limit", () => {
  it("429 after N per window, then window resets", () => {
    let t = 0;
    const l = new FixedWindowLimiter(3, 60_000, () => t);
    expect(l.check("k").allowed).toBe(true);
    expect(l.check("k").allowed).toBe(true);
    expect(l.check("k").allowed).toBe(true);
    const r = l.check("k");
    expect(r.allowed).toBe(false);
    if (!r.allowed) expect(r.retryAfterSec).toBe(60);
    expect(l.check("other").allowed).toBe(true);
    t = 60_000;
    expect(l.check("k").allowed).toBe(true);
  });
});

describe("amount conversion", () => {
  it("toman x10, rial x1", () => {
    expect(toRial(500000, "toman")).toBe(5000000);
    expect(toRial(2000000, "rial")).toBe(2000000);
  });
  it("rejects zero and unsafe integers", () => {
    expect(() => toRial(0, "rial")).toThrow(AmountError);
    expect(() => toRial(Number.MAX_SAFE_INTEGER, "toman")).toThrow(AmountError);
  });
});

describe("date and config", () => {
  it("today is computed in Asia/Tehran, not UTC", () => {
    // 2026-10-30 21:00 UTC is 2026-10-31 00:30 in Tehran (UTC+03:30).
    expect(todayTehran(new Date("2026-10-30T21:00:00Z"))).toBe("2026-10-31");
  });
  it("defaults: model haiku, text not logged, api key optional for load", () => {
    const c = loadConfig({});
    expect(c.model).toBe("claude-haiku-5-5");
    expect(c.logText).toBe(false);
    expect(c.apiKey).toBeUndefined();
  });
  it("DEBUG_LOG_TEXT only enabled by exactly 1", () => {
    expect(loadConfig({ DEBUG_LOG_TEXT: "true" }).logText).toBe(false);
    expect(loadConfig({ DEBUG_LOG_TEXT: "1" }).logText).toBe(true);
  });
});
