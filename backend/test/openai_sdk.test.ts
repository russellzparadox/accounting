// Inbound compatibility proof: the official `openai` npm package (devDependency) talks to the local
// proxy. No network: the proxy runs on 127.0.0.1 with a stub LLM.
import type { AddressInfo } from "node:net";
import type { Server } from "node:http";
import OpenAI from "openai";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { createLogger } from "../src/log.js";
import type { LlmClient } from "../src/llm.js";
import { FixedWindowLimiter } from "../src/ratelimit.js";
import { createApp } from "../src/server.js";

const TOKEN = "tok-sdk-123";
const intent = {
  type: "PURCHASE_CASH", amount_text: "۵۰۰٬۰۰۰ تومان", amount_value: 500000, amount_unit: "toman",
  date: "2026-10-31", paid_with: "CASH", bank_account: null, party_name: null, category: null, memo: null,
  confidence: 92, needs_clarification: null,
};
const llm: LlmClient = { extract: async () => intent };

const TOOLS = [{
  type: "function" as const,
  function: {
    name: "record_transaction",
    description: "Record one parsed transaction.",
    parameters: { type: "object", properties: { text: { type: "string" } }, required: ["text"] },
    strict: true,
  },
}];
const REQ = { model: "sdk-model", messages: [{ role: "user" as const, content: "۵۰۰٬۰۰۰ تومان نقد خریدم" }] };

let server: Server;
let client: OpenAI;
let limitedClient: OpenAI;
let base: string;

beforeAll(async () => {
  server = createApp({
    llm,
    tokens: [TOKEN],
    limiter: new FixedWindowLimiter(100, 60_000),
    logger: createLogger({ sink: () => {}, logText: false }),
    model: "configured-model",
  });
  await new Promise<void>((r) => server.listen(0, "127.0.0.1", () => r()));
  base = `http://127.0.0.1:${(server.address() as AddressInfo).port}/v1`;
  client = new OpenAI({ baseURL: base, apiKey: TOKEN, maxRetries: 0 });
  limitedClient = new OpenAI({ baseURL: base, apiKey: "wrong-token", maxRetries: 0 });
});
afterAll(async () => { await new Promise((r) => server.close(r)); });

describe("official openai SDK -> local proxy", () => {
  it("chat.completions.create with forced tool call", async () => {
    const res = await client.chat.completions.create({
      ...REQ,
      tools: TOOLS,
      tool_choice: { type: "function", function: { name: "record_transaction" } },
    });
    expect(res.object).toBe("chat.completion");
    expect(res.model).toBe("sdk-model");
    expect(res.choices[0]!.finish_reason).toBe("tool_calls");
    const call = res.choices[0]!.message.tool_calls![0]!;
    expect(call.type).toBe("function");
    expect(call.function.name).toBe("record_transaction");
    expect(JSON.parse(call.function.arguments)).toMatchObject({ amount_rial: 5000000, type: "PURCHASE_CASH" });
  });

  it("chat.completions.create without tools (content JSON)", async () => {
    const res = await client.chat.completions.create(REQ);
    expect(res.choices[0]!.finish_reason).toBe("stop");
    expect(JSON.parse(res.choices[0]!.message.content!)).toMatchObject({ amount_rial: 5000000 });
  });

  it("chat.completions.create with stream: true (tool call assembled by the SDK)", async () => {
    const stream = await client.chat.completions.create({ ...REQ, stream: true, tools: TOOLS });
    let name = "";
    let args = "";
    let finish: string | null = null;
    for await (const chunk of stream) {
      const ch = chunk.choices[0]!;
      for (const tc of ch.delta.tool_calls ?? []) {
        name += tc.function?.name ?? "";
        args += tc.function?.arguments ?? "";
      }
      if (ch.finish_reason) finish = ch.finish_reason;
    }
    expect(name).toBe("record_transaction");
    expect(JSON.parse(args)).toMatchObject({ amount_rial: 5000000 });
    expect(finish).toBe("tool_calls");
  });

  it("models.list lists the configured model", async () => {
    const page = await client.models.list();
    expect(page.data.map((m) => m.id)).toEqual(["configured-model"]);
  });

  it("wrong key -> AuthenticationError with status 401", async () => {
    await expect(limitedClient.chat.completions.create(REQ)).rejects.toMatchObject({ status: 401 });
  });

  it("bad request (no user message) -> BadRequestError with status 400", async () => {
    await expect(client.chat.completions.create({ model: "m", messages: [{ role: "system", content: "s" }] }))
      .rejects.toMatchObject({ status: 400 });
  });
});
