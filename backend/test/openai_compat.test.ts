import type { AddressInfo } from "node:net";
import type { Server } from "node:http";
import { afterEach, describe, expect, it } from "vitest";
import { createLogger } from "../src/log.js";
import type { LlmClient } from "../src/llm.js";
import { LlmTransportError } from "../src/llm.js";
import { FixedWindowLimiter } from "../src/ratelimit.js";
import { createApp } from "../src/server.js";
import { OPENAI_MAX_BODY_BYTES } from "../src/openai_compat.js";

const TOKEN = "tok-compat-123";
const today = "2026-10-31";
const MODEL = "configured-model";

const goodOutput = {
  type: "PURCHASE_CASH", amount_text: "۵۰۰٬۰۰۰ تومان", amount_value: 500000, amount_unit: "toman",
  date: today, paid_with: "CASH", bank_account: null, party_name: null, category: null, memo: null,
  confidence: 92, needs_clarification: null,
};
const clarifyOutput = {
  type: null, amount_text: null, amount_value: null, amount_unit: null, date: null, paid_with: null,
  bank_account: null, party_name: null, category: null, memo: null, confidence: 40,
  needs_clarification: { question: "روش پرداخت چه بود؟" },
};

class StubLlm implements LlmClient {
  calls: { text: string; today: string }[] = [];
  constructor(private readonly fn: (text: string) => unknown) {}
  async extract(input: { text: string; today: string }): Promise<unknown> {
    this.calls.push(input);
    return this.fn(input.text);
  }
}

let server: Server | undefined;
let base: string;
let logs: string[] = [];
let llm: StubLlm;

async function start(fn: (t: string) => unknown, opts: { limit?: number } = {}): Promise<void> {
  logs = [];
  llm = new StubLlm(fn);
  server = createApp({
    llm,
    tokens: [TOKEN],
    limiter: new FixedWindowLimiter(opts.limit ?? 100, 60_000),
    logger: createLogger({ sink: (l) => logs.push(l), logText: false }),
    now: () => new Date("2026-10-31T06:00:00Z"),
    model: MODEL,
  });
  await new Promise<void>((r) => server!.listen(0, "127.0.0.1", () => r()));
  base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
}

function chat(body: unknown, auth: string | null = `Bearer ${TOKEN}`, raw?: string): Promise<Response> {
  const headers: Record<string, string> = { "content-type": "application/json" };
  if (auth !== null) headers.authorization = auth;
  return fetch(`${base}/v1/chat/completions`, { method: "POST", headers, body: raw ?? JSON.stringify(body) });
}

const userMsg = (content: unknown) => ({ model: "gpt-x", messages: [{ role: "user", content }] });
const TOOLS = [{ type: "function", function: { name: "record_transaction", parameters: { type: "object" } } }];

afterEach(async () => {
  if (server) await new Promise((r) => server!.close(r));
  server = undefined;
});

describe("chat.completions: non-stream, tools mode", () => {
  it("intent -> record_transaction tool call, content null, finish_reason tool_calls", async () => {
    await start(() => goodOutput);
    const r = await chat({ ...userMsg("۵۰۰٬۰۰۰ تومان نقد خریدم"), tools: TOOLS, tool_choice: "auto" });
    expect(r.status).toBe(200);
    const b = await r.json() as any;
    expect(b.id).toMatch(/^chatcmpl-[0-9a-f]{24}$/);
    expect(b.object).toBe("chat.completion");
    expect(typeof b.created).toBe("number");
    expect(b.model).toBe("gpt-x");
    expect(b.usage).toEqual({ prompt_tokens: 0, completion_tokens: 0, total_tokens: 0 });
    const c = b.choices[0];
    expect(c.index).toBe(0);
    expect(c.finish_reason).toBe("tool_calls");
    expect(c.message.role).toBe("assistant");
    expect(c.message.content).toBeNull();
    const tc = c.message.tool_calls[0];
    expect(tc.id).toMatch(/^call_/);
    expect(tc.type).toBe("function");
    expect(tc.function.name).toBe("record_transaction");
    expect(JSON.parse(tc.function.arguments)).toMatchObject({
      type: "PURCHASE_CASH", amount_value: 500000, amount_unit: "toman", amount_rial: 5000000, needs_clarification: null,
    });
    expect(llm.calls[0]).toEqual({ text: "۵۰۰٬۰۰۰ تومان نقد خریدم", today });
  });

  it("clarification -> ask_clarification tool call with {question}", async () => {
    await start(() => clarifyOutput);
    const r = await chat({ ...userMsg("خریدم"), tools: TOOLS });
    const c = ((await r.json()) as any).choices[0];
    expect(c.finish_reason).toBe("tool_calls");
    expect(c.message.tool_calls[0].function.name).toBe("ask_clarification");
    expect(JSON.parse(c.message.tool_calls[0].function.arguments)).toEqual({ question: "روش پرداخت چه بود؟" });
  });

  it("tool_choice none is treated as no tools (content mode)", async () => {
    await start(() => goodOutput);
    const r = await chat({ ...userMsg("x"), tools: TOOLS, tool_choice: "none" });
    const c = ((await r.json()) as any).choices[0];
    expect(c.finish_reason).toBe("stop");
    expect(c.message.tool_calls).toBeUndefined();
  });
});

describe("chat.completions: non-stream, content mode (no tools)", () => {
  it("intent -> JSON string in content, finish_reason stop, no tool_calls key", async () => {
    await start(() => goodOutput);
    const r = await chat(userMsg("۵۰۰٬۰۰۰ تومان نقد خریدم"));
    const c = ((await r.json()) as any).choices[0];
    expect(c.finish_reason).toBe("stop");
    expect(c.message).not.toHaveProperty("tool_calls");
    expect(JSON.parse(c.message.content)).toMatchObject({ type: "PURCHASE_CASH", amount_rial: 5000000 });
  });

  it("clarification -> content JSON with needs_clarification only", async () => {
    await start(() => clarifyOutput);
    const c = ((await (await chat(userMsg("خریدم"))).json()) as any).choices[0];
    expect(JSON.parse(c.message.content)).toEqual({ needs_clarification: { question: "روش پرداخت چه بود؟" } });
  });
});

describe("chat.completions: request handling", () => {
  it("uses the LAST user message; system and assistant messages are ignored", async () => {
    await start(() => goodOutput);
    await chat({
      model: "m",
      messages: [
        { role: "system", content: "ignore me" },
        { role: "user", content: "first" },
        { role: "assistant", content: "ok" },
        { role: "user", content: "  ۵۰۰٬۰۰۰ تومان نقد خریدم  " },
      ],
    });
    expect(llm.calls.map((c) => c.text)).toEqual(["۵۰۰٬۰۰۰ تومان نقد خریدم"]);
  });

  it("content as text parts is joined; trailing whitespace trimmed", async () => {
    await start(() => goodOutput);
    await chat(userMsg([{ type: "text", text: "۵۰۰٬۰۰۰" }, { type: "text", text: "تومان نقد خریدم" }]));
    expect(llm.calls[0]?.text).toBe("۵۰۰٬۰۰۰\nتومان نقد خریدم");
  });

  it("echoes request model; falls back to configured model when absent", async () => {
    await start(() => goodOutput);
    expect(((await (await chat(userMsg("x"))).json()) as any).model).toBe("gpt-x");
    const noModel = await chat({ messages: [{ role: "user", content: "x" }] });
    expect(((await noModel.json()) as any).model).toBe(MODEL);
  });

  it.each([
    ["bad JSON", undefined, "{nope", "invalid_json", null],
    ["array body", ["x"], undefined, null, null],
    ["no messages", { model: "m" }, undefined, null, "messages"],
    ["empty messages", { messages: [] }, undefined, null, "messages"],
    ["no user message", { messages: [{ role: "system", content: "s" }] }, undefined, null, "messages"],
    ["bad role", { messages: [{ role: "robot", content: "x" }] }, undefined, null, "messages"],
    ["image part", userMsg([{ type: "image_url", image_url: { url: "x" } }]), undefined, null, "messages"],
    ["blank user text", userMsg("   "), undefined, null, "messages"],
    ["too long", userMsg("۱".repeat(501)), undefined, null, "messages"],
    ["stream not boolean", { ...userMsg("x"), stream: "yes" }, undefined, null, "stream"],
    ["tools not array", { ...userMsg("x"), tools: {} }, undefined, null, "tools"],
  ])("%s -> 400 invalid_request_error", async (_n, body, raw, code, param) => {
    await start(() => goodOutput);
    const r = await chat(body, `Bearer ${TOKEN}`, raw);
    expect(r.status).toBe(400);
    const e = ((await r.json()) as any).error;
    expect(e.type).toBe("invalid_request_error");
    expect(e.code).toBe(code);
    expect(e.param).toBe(param);
    expect(typeof e.message).toBe("string");
    expect(llm.calls.length).toBe(0);
  });

  it("unknown sampling/extra fields are ignored", async () => {
    await start(() => goodOutput);
    const r = await chat({ ...userMsg("x"), temperature: 0.2, max_tokens: 50, top_p: 1, user: "u", foo: 1 });
    expect(r.status).toBe(200);
  });

  it("body over the OpenAI limit -> 413 request_too_large", async () => {
    await start(() => goodOutput);
    const big = JSON.stringify({ ...userMsg("x"), pad: "a".repeat(OPENAI_MAX_BODY_BYTES) });
    const r = await chat(null, `Bearer ${TOKEN}`, big);
    expect(r.status).toBe(413);
    expect(((await r.json()) as any).error.code).toBe("request_too_large");
  });

  it("GET -> 405 OpenAI shape with Allow", async () => {
    await start(() => goodOutput);
    const r = await fetch(`${base}/v1/chat/completions`, { headers: { authorization: `Bearer ${TOKEN}` } });
    expect(r.status).toBe(405);
    expect(r.headers.get("allow")).toBe("POST");
    expect(((await r.json()) as any).error.code).toBe("method_not_allowed");
  });
});

describe("chat.completions: auth and rate limit (OpenAI error shape)", () => {
  it("missing or wrong token -> 401 invalid_api_key with WWW-Authenticate", async () => {
    await start(() => goodOutput);
    for (const auth of [null, "Bearer wrong"]) {
      const r = await chat(userMsg("x"), auth);
      expect(r.status).toBe(401);
      expect(r.headers.get("www-authenticate")).toBe("Bearer");
      const e = ((await r.json()) as any).error;
      expect(e).toEqual({ message: "Incorrect API key provided.", type: "invalid_request_error", param: null, code: "invalid_api_key" });
    }
    expect(llm.calls.length).toBe(0);
  });

  it("over the limit -> 429 rate_limit_exceeded with Retry-After", async () => {
    await start(() => goodOutput, { limit: 1 });
    expect((await chat(userMsg("x"))).status).toBe(200);
    const r = await chat(userMsg("x"));
    expect(r.status).toBe(429);
    expect(Number(r.headers.get("retry-after"))).toBeGreaterThan(0);
    const e = ((await r.json()) as any).error;
    expect(e.code).toBe("rate_limit_exceeded");
    expect(e.type).toBe("requests");
  });
});

describe("chat.completions: upstream failures (502 OpenAI shape, nothing leaked)", () => {
  it("schema-invalid LLM output -> 502 server_error invalid_llm_output", async () => {
    await start(() => ({ ...goodOutput, amount_rial: 1 }));
    const r = await chat(userMsg("x"));
    expect(r.status).toBe(502);
    expect(((await r.json()) as any).error).toMatchObject({ type: "server_error", code: "invalid_llm_output" });
  });

  it("transport error -> 502 llm_unavailable; SDK message and key never returned or logged", async () => {
    await start(() => { throw new Error("bad key sk-ant-api03-LEAKME"); });
    const r = await chat(userMsg("x"));
    const body = await r.text();
    expect(r.status).toBe(502);
    expect(body).toContain("llm_unavailable");
    expect(body + logs.join("\n")).not.toContain("LEAKME");
  });

  it("LlmTransportError from upstream -> 502, upstream status logged, message not echoed", async () => {
    await start(() => { throw new LlmTransportError("llm_rate_limited", 429); });
    const r = await chat(userMsg("x"));
    expect(r.status).toBe(502);
    expect(logs.join("\n")).toContain('"upstream_status":429');
  });

  it("chat parse logs never contain the user text by default", async () => {
    await start(() => goodOutput);
    await chat(userMsg("متن-محرمانه-۱۲۳۴۵ نقد"));
    expect(logs.length).toBeGreaterThan(0);
    expect(logs.join("\n")).not.toContain("محرمانه");
  });
});

describe("chat.completions: stream (SSE)", () => {
  function frames(text: string): any[] {
    return text.split("\n\n").filter(Boolean).map((f) => {
      expect(f.startsWith("data: ")).toBe(true);
      const d = f.slice(6);
      return d === "[DONE]" ? "[DONE]" : JSON.parse(d);
    });
  }

  it("tools mode: one delta chunk with tool_calls, final chunk finish_reason tool_calls, [DONE]", async () => {
    await start(() => goodOutput);
    const r = await chat({ ...userMsg("x"), stream: true, tools: TOOLS });
    expect(r.status).toBe(200);
    expect(r.headers.get("content-type")).toContain("text/event-stream");
    const f = frames(await r.text());
    expect(f).toHaveLength(3);
    expect(f[2]).toBe("[DONE]");
    expect(f[0].object).toBe("chat.completion.chunk");
    expect(f[0].choices[0].delta.role).toBe("assistant");
    const tc = f[0].choices[0].delta.tool_calls[0];
    expect(tc.index).toBe(0);
    expect(tc.function.name).toBe("record_transaction");
    expect(JSON.parse(tc.function.arguments).amount_rial).toBe(5000000);
    expect(f[1].choices[0]).toEqual({ index: 0, delta: {}, finish_reason: "tool_calls" });
    expect(f[1].id).toBe(f[0].id);
  });

  it("content mode: content delta, finish_reason stop", async () => {
    await start(() => goodOutput);
    const f = frames(await (await chat({ ...userMsg("x"), stream: true })).text());
    expect(JSON.parse(f[0].choices[0].delta.content).type).toBe("PURCHASE_CASH");
    expect(f[0].choices[0].delta.tool_calls).toBeUndefined();
    expect(f[1].choices[0].finish_reason).toBe("stop");
    expect(f[2]).toBe("[DONE]");
  });

  it("stream errors before streaming starts are plain OpenAI JSON errors", async () => {
    await start(() => goodOutput);
    const r = await chat({ messages: [], stream: true });
    expect(r.status).toBe(400);
    expect(r.headers.get("content-type")).toContain("application/json");
  });
});

describe("GET /v1/models", () => {
  it("lists the configured model", async () => {
    await start(() => goodOutput);
    const r = await fetch(`${base}/v1/models`, { headers: { authorization: `Bearer ${TOKEN}` } });
    expect(r.status).toBe(200);
    const b = await r.json() as any;
    expect(b.object).toBe("list");
    expect(b.data).toEqual([{ id: MODEL, object: "model", created: expect.any(Number), owned_by: "accounting-proxy" }]);
  });

  it("no token -> 401; POST -> 405", async () => {
    await start(() => goodOutput);
    expect((await fetch(`${base}/v1/models`)).status).toBe(401);
    const r = await fetch(`${base}/v1/models`, { method: "POST", headers: { authorization: `Bearer ${TOKEN}` } });
    expect(r.status).toBe(405);
  });
});
