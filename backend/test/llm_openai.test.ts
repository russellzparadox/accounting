import { createServer, type IncomingMessage, type Server, type ServerResponse } from "node:http";
import type { AddressInfo } from "node:net";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { LlmTransportError } from "../src/llm.js";
import { extractFirstJsonObject, OpenAiLlmClient } from "../src/llm_openai.js";
import { parseText } from "../src/parse.js";

// Local stub of an OpenAI-compatible provider. No network. Each test sets `handler`.
type Handler = (body: any, req: IncomingMessage, res: ServerResponse) => void | Promise<void>;
let stub: Server;
let base: string;
let handler: Handler;
let seen: { body: any; auth: string | undefined }[];

const goodArgs = {
  type: "PURCHASE_CASH", amount_text: "۵۰۰٬۰۰۰ تومان", amount_value: 500000, amount_unit: "toman",
  date: "2026-10-31", paid_with: "CASH", bank_account: null, party_name: null, category: null, memo: null,
  confidence: 90, needs_clarification: null,
};

function json(res: ServerResponse, status: number, body: unknown): void {
  res.writeHead(status, { "content-type": "application/json" });
  res.end(typeof body === "string" ? body : JSON.stringify(body));
}
const toolReply = (args: unknown) => ({
  choices: [{ index: 0, finish_reason: "tool_calls", message: { role: "assistant", content: null, tool_calls: [
    { id: "c1", type: "function", function: { name: "record_transaction", arguments: JSON.stringify(args) } },
  ] } }],
});
const contentReply = (content: string) => ({
  choices: [{ index: 0, finish_reason: "stop", message: { role: "assistant", content } }],
});

beforeEach(async () => {
  seen = [];
  handler = (_b, _r, res) => json(res, 500, { error: "unset" });
  stub = createServer((req, res) => {
    let raw = "";
    req.on("data", (c) => { raw += c; });
    req.on("end", () => {
      const body = raw ? JSON.parse(raw) : null;
      seen.push({ body, auth: req.headers.authorization });
      if (req.url !== "/v1/chat/completions") return json(res, 404, { error: "nope" });
      void handler(body, req, res);
    });
  });
  await new Promise<void>((r) => stub.listen(0, "127.0.0.1", () => r()));
  base = `http://127.0.0.1:${(stub.address() as AddressInfo).port}/v1`;
});
afterEach(async () => { await new Promise((r) => stub.close(r)); });

const client = (over: Partial<ConstructorParameters<typeof OpenAiLlmClient>[0]> = {}) =>
  new OpenAiLlmClient({ baseUrl: base, model: "local-model", timeoutMs: 2000, ...over });
const input = { text: "۵۰۰٬۰۰۰ تومان نقد خریدم", today: "2026-10-31" };

describe("upstream tools path", () => {
  it("sends forced strict tool + schema, returns tool arguments", async () => {
    handler = (_b, _r, res) => json(res, 200, toolReply(goodArgs));
    const out = await client().extract(input);
    expect(out).toEqual(goodArgs);
    expect(seen).toHaveLength(1);
    const b = seen[0]!.body;
    expect(b.model).toBe("local-model");
    expect(b.tool_choice).toEqual({ type: "function", function: { name: "record_transaction" } });
    expect(b.tools[0].type).toBe("function");
    expect(b.tools[0].function.strict).toBe(true);
    expect(b.tools[0].function.parameters.$schema).toBeUndefined();
    expect(b.tools[0].function.parameters.additionalProperties).toBe(false);
    expect(b.messages[0].role).toBe("system");
    expect(b.messages[1].content).toContain("today: 2026-10-31");
    expect(b.messages[1].content).toContain(input.text);
    expect(seen[0]!.auth).toBeUndefined();
  });

  it("sends Bearer only when a key is configured", async () => {
    handler = (_b, _r, res) => json(res, 200, toolReply(goodArgs));
    await client({ apiKey: "sk-upstream-KEY" }).extract(input);
    expect(seen[0]!.auth).toBe("Bearer sk-upstream-KEY");
  });

  it("tool call for another name -> null (rejected later by schema)", async () => {
    handler = (_b, _r, res) => json(res, 200, {
      choices: [{ message: { role: "assistant", tool_calls: [{ id: "c", type: "function", function: { name: "other", arguments: "{}" } }] } }],
    });
    expect(await client().extract(input)).toBeNull();
  });
});

describe("upstream fallbacks", () => {
  it("(a) tools ignored (200, no tool_calls) -> json_schema attempt is used", async () => {
    handler = (b, _r, res) => {
      if (b.tools) return json(res, 200, contentReply("Sure, here it is."));
      return json(res, 200, contentReply(JSON.stringify(goodArgs)));
    };
    const out = await client().extract(input);
    expect(out).toEqual(goodArgs);
    expect(seen).toHaveLength(2);
    expect(seen[1]!.body.response_format.type).toBe("json_schema");
    expect(seen[1]!.body.response_format.json_schema.strict).toBe(true);
    expect(seen[1]!.body.tools).toBeUndefined();
  });

  it("(a) tools refused (400) -> json_schema used", async () => {
    handler = (b, _r, res) => {
      if (b.tools) return json(res, 400, { error: { message: "tools not supported" } });
      return json(res, 200, contentReply(JSON.stringify(goodArgs)));
    };
    expect(await client().extract(input)).toEqual(goodArgs);
  });

  it("(b) json_schema refused (400) -> JSON mode with first object extracted from prose", async () => {
    handler = (b, _r, res) => {
      if (b.tools) return json(res, 200, contentReply("no tools"));
      if (b.response_format?.type === "json_schema") return json(res, 400, { error: "unsupported" });
      return json(res, 200, contentReply(`Here you go:\n\`\`\`json\n${JSON.stringify(goodArgs)}\n\`\`\`\nDone.`));
    };
    expect(await client().extract(input)).toEqual(goodArgs);
    expect(seen.at(-1)!.body.response_format).toEqual({ type: "json_object" });
    expect(seen.at(-1)!.body.messages[0].content).toContain("JSON Schema");
  });

  it("(a) json_schema returns schema-invalid JSON -> falls to JSON mode", async () => {
    handler = (b, _r, res) => {
      if (b.tools) return json(res, 200, contentReply("x"));
      if (b.response_format.type === "json_schema") return json(res, 200, contentReply('{"type":"NOPE"}'));
      return json(res, 200, contentReply(JSON.stringify(goodArgs)));
    };
    expect(await client().extract(input)).toEqual(goodArgs);
  });

  it("JSON mode with no object in content -> null, parse rejects it as invalid output", async () => {
    handler = (b, _r, res) => {
      if (b.tools) return json(res, 400, {});
      if (b.response_format?.type === "json_schema") return json(res, 400, {});
      return json(res, 200, contentReply("I cannot help with that."));
    };
    const out = await client().extract(input);
    expect(out).toBeNull();
    await expect(parseText({ text: input.text, today: input.today }, { extract: async () => out }))
      .rejects.toMatchObject({ code: "llm_schema_violation" });
  });

  it("tools ignored and no JSON at all -> full chain, 3 requests", async () => {
    handler = (_b, _r, res) => json(res, 200, contentReply("plain text"));
    await client().extract(input);
    expect(seen).toHaveLength(3);
  });

  it("end-to-end: fallback output still goes through parseText (amount_rial computed by backend)", async () => {
    handler = (b, _r, res) => {
      if (b.tools) return json(res, 200, contentReply("x"));
      return json(res, 200, contentReply(JSON.stringify(goodArgs)));
    };
    const r = await parseText({ text: input.text, today: input.today }, client());
    expect(r.kind).toBe("intent");
    expect(r.body).toMatchObject({ amount_rial: 5000000 });
  });
});

describe("upstream errors (mapped, body never echoed)", () => {
  const cases: [number, string, number][] = [
    [401, "llm_auth_failed", 401],
    [403, "llm_auth_failed", 403],
    [429, "llm_rate_limited", 429],
    [500, "llm_upstream_error", 500],
    [503, "llm_upstream_error", 503],
    [404, "llm_bad_request", 404],
  ];
  it.each(cases)("HTTP %i -> %s", async (status, code, st) => {
    handler = (_b, _r, res) => json(res, status, { error: { message: "secret upstream text sk-LEAK" } });
    const e = await client().extract(input).catch((x: unknown) => x);
    expect(e).toBeInstanceOf(LlmTransportError);
    expect((e as LlmTransportError).code).toBe(code);
    expect((e as LlmTransportError).status).toBe(st);
    expect(JSON.stringify(e) + (e as Error).message).not.toContain("LEAK");
  });

  it("timeout -> llm_timeout", async () => {
    handler = () => new Promise(() => { /* never answers */ });
    const e = await client({ timeoutMs: 100 }).extract(input).catch((x: unknown) => x);
    expect(e).toBeInstanceOf(LlmTransportError);
    expect((e as LlmTransportError).code).toBe("llm_timeout");
  });

  it("connection refused -> llm_request_failed, no URL in message", async () => {
    const dead = new OpenAiLlmClient({ baseUrl: "http://127.0.0.1:1/v1", model: "m", timeoutMs: 2000 });
    const e = await dead.extract(input).catch((x: unknown) => x);
    expect((e as LlmTransportError).code).toBe("llm_request_failed");
    expect((e as Error).message).not.toContain("127.0.0.1");
  });

  it("5xx on the tools step is not retried into fallbacks", async () => {
    handler = (_b, _r, res) => json(res, 502, {});
    await expect(client().extract(input)).rejects.toMatchObject({ code: "llm_upstream_error" });
    expect(seen).toHaveLength(1);
  });
});

describe("extractFirstJsonObject", () => {
  it("handles braces inside strings and nested objects", () => {
    expect(extractFirstJsonObject('x {"a":"}{","b":{"c":1}} y')).toEqual({ a: "}{", b: { c: 1 } });
  });
  it("returns null when no object", () => {
    expect(extractFirstJsonObject("nothing here")).toBeNull();
    expect(extractFirstJsonObject("{ broken")).toBeNull();
  });
});
