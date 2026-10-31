import type { AddressInfo } from "node:net";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import type { Server } from "node:http";
import { createLogger } from "../src/log.js";
import type { LlmClient } from "../src/llm.js";
import { FixedWindowLimiter } from "../src/ratelimit.js";
import { createApp, MAX_TEXT_CHARS } from "../src/server.js";

const TOKEN = "tok-good-123";
const today = "2026-10-31";

class StubLlm implements LlmClient {
  calls: { text: string; today: string }[] = [];
  constructor(private readonly fn: (text: string) => unknown | Promise<unknown>) {}
  async extract(input: { text: string; today: string }): Promise<unknown> {
    this.calls.push(input);
    return this.fn(input.text);
  }
}

const goodOutput = {
  type: "PURCHASE_CASH", amount_text: "۵۰۰٬۰۰۰ تومان", amount_value: 500000, amount_unit: "toman",
  date: today, paid_with: "CASH", bank_account: null, party_name: null, category: null, memo: null,
  confidence: 92, needs_clarification: null,
};

let server: Server;
let base: string;
let logs: string[];
let llm: StubLlm;
let limiter: FixedWindowLimiter;
let logText = false;

async function start(llmFn: (t: string) => unknown, opts: { limit?: number; now?: () => Date } = {}): Promise<void> {
  logs = [];
  llm = new StubLlm(llmFn);
  limiter = new FixedWindowLimiter(opts.limit ?? 100, 60_000);
  server = createApp({
    llm,
    tokens: [TOKEN],
    limiter,
    logger: createLogger({ sink: (l) => logs.push(l), logText }),
    now: opts.now ?? (() => new Date("2026-10-31T06:00:00Z")),
  });
  await new Promise<void>((r) => server.listen(0, "127.0.0.1", () => r()));
  base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
}

function post(body: unknown, auth: string | null = `Bearer ${TOKEN}`, raw?: string): Promise<Response> {
  const headers: Record<string, string> = { "content-type": "application/json" };
  if (auth !== null) headers.authorization = auth;
  return fetch(`${base}/v1/parse`, { method: "POST", headers, body: raw ?? JSON.stringify(body) });
}

beforeEach(() => { logText = false; });
afterEach(async () => { if (server) await new Promise((r) => server.close(r)); });

describe("auth (401)", () => {
  it("no token -> 401", async () => {
    await start(() => goodOutput);
    const r = await post({ text: "x" }, null);
    expect(r.status).toBe(401);
    expect(r.headers.get("www-authenticate")).toBe("Bearer");
    expect(llm.calls.length).toBe(0);
  });
  it("wrong token -> 401", async () => {
    await start(() => goodOutput);
    expect((await post({ text: "x" }, "Bearer wrong")).status).toBe(401);
  });
  it("right token -> 200", async () => {
    await start(() => goodOutput);
    expect((await post({ text: "۵۰۰٬۰۰۰ تومان نقد خریدم" })).status).toBe(200);
  });
});

describe("rate limit (429)", () => {
  it("429 after N requests per minute per token, with Retry-After", async () => {
    await start(() => goodOutput, { limit: 2 });
    expect((await post({ text: "a" })).status).toBe(200);
    expect((await post({ text: "a" })).status).toBe(200);
    const r = await post({ text: "a" });
    expect(r.status).toBe(429);
    expect(Number(r.headers.get("retry-after"))).toBeGreaterThan(0);
    expect(llm.calls.length).toBe(2);
  });
});

describe("text logging", () => {
  const secretText = "متن-محرمانه-۱۲۳۴۵ پرداخت";
  it("request text is not logged by default", async () => {
    await start(() => goodOutput);
    expect((await post({ text: secretText })).status).toBe(200);
    expect(logs.length).toBeGreaterThan(0);
    expect(logs.join("\n")).not.toContain("محرمانه");
  });
  it("text is logged only with DEBUG_LOG_TEXT=1 (logText true)", async () => {
    logText = true;
    await start(() => goodOutput);
    await post({ text: secretText });
    expect(logs.join("\n")).toContain("محرمانه");
  });
});

describe("secrets never returned or logged", () => {
  it("SDK error message containing a key is not in response or logs", async () => {
    await start(() => { throw new Error("bad key sk-ant-api03-LEAKME"); });
    const r = await post({ text: "x" });
    const body = await r.text();
    expect(r.status).toBe(502);
    expect(body).toContain("llm_unavailable");
    expect(body + logs.join("\n")).not.toContain("LEAKME");
  });
});

describe("request validation (400/404/405)", () => {
  it("bad JSON -> 400", async () => {
    await start(() => goodOutput);
    expect((await post(null, `Bearer ${TOKEN}`, "{nope")).status).toBe(400);
  });
  it.each([
    ["extra key", { text: "a", x: 1 }],
    ["missing text", {}],
    ["non-string", { text: 5 }],
    ["blank", { text: "   " }],
    ["too long", { text: "۱".repeat(MAX_TEXT_CHARS + 1) }],
    ["array", ["a"]],
  ])("%s -> 400", async (_n, body) => {
    await start(() => goodOutput);
    expect((await post(body)).status).toBe(400);
    expect(llm.calls.length).toBe(0);
  });
  it("GET /v1/parse -> 405, unknown path -> 404", async () => {
    await start(() => goodOutput);
    expect((await fetch(`${base}/v1/parse`, { headers: { authorization: `Bearer ${TOKEN}` } })).status).toBe(405);
    expect((await fetch(`${base}/nope`)).status).toBe(404);
  });
});

describe("response contract", () => {
  it("intent 200 body has T03 fields plus amount extras, amount_rial computed by backend", async () => {
    await start(() => goodOutput);
    const r = await post({ text: "۵۰۰٬۰۰۰ تومان نقد خریدم" });
    const b = await r.json() as Record<string, unknown>;
    expect(b).toMatchObject({ type: "PURCHASE_CASH", amount_value: 500000, amount_unit: "toman", amount_rial: 5000000, needs_clarification: null });
    expect(llm.calls[0]?.today).toBe(today);
  });
  it("clarification 200 body", async () => {
    await start(() => ({ ...goodOutput, type: null, amount_text: null, amount_value: null, amount_unit: null, date: null, paid_with: null, needs_clarification: { question: "روش پرداخت چه بود؟" } }));
    const r = await post({ text: "۵۰۰٬۰۰۰ تومان خریدم" });
    expect(r.status).toBe(200);
    expect(await r.json()).toEqual({ needs_clarification: { question: "روش پرداخت چه بود؟" } });
  });
  it("schema-invalid LLM output -> 502 invalid_llm_output, content not echoed", async () => {
    await start(() => ({ ...goodOutput, amount_rial: 1 }));
    const r = await post({ text: "x" });
    expect(r.status).toBe(502);
    expect(await r.json()).toEqual({ error: "invalid_llm_output" });
  });
  it("field-rule violation from LLM -> 502 (cash purchase without payment method and no clarification)", async () => {
    await start(() => ({ ...goodOutput, paid_with: null }));
    expect((await post({ text: "x" })).status).toBe(502);
  });
  it("relative date uses Asia/Tehran day (server clock 2026-10-30T21:00Z = 31 Oct in Tehran)", async () => {
    await start(() => goodOutput, { now: () => new Date("2026-10-30T21:00:00Z") });
    await post({ text: "x" });
    expect(llm.calls[0]?.today).toBe("2026-10-31");
  });
});
