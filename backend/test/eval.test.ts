import { afterAll, beforeAll, describe, expect, it } from "vitest";
import type { AddressInfo } from "node:net";
import type { Server } from "node:http";
import { fixtureFor, loadEvalSet, runEval, runEvalWith, viaOpenAi } from "../eval/harness.js";
import { createLogger } from "../src/log.js";
import { FixedWindowLimiter } from "../src/ratelimit.js";
import { createApp } from "../src/server.js";

// Offline eval: mock LLM (fixture expectations). Verifies the pipeline end to end: schema,
// deterministic amount conversion, field rules, clarification path. Not a model-quality score;
// the live run is T13.
describe("eval (mock LLM, offline)", () => {
  const set = loadEvalSet();

  it("runs every sentence and reports pass/fail", async () => {
    const results = await runEval(set, fixtureFor(set));
    const failed = results.filter((r) => !r.pass);
    const passed = results.length - failed.length;
    console.log(`EVAL mock: ${passed}/${results.length} passed`);
    for (const f of failed) console.log(`FAIL ${f.id}: ${f.problems.join("; ")}`);
    expect(results.length).toBeGreaterThanOrEqual(50);
    expect(failed).toEqual([]);
  });

  it("harness detects a wrong amount unit (self-check)", async () => {
    const id = set.sentences.find((s) => s.expect.amount_unit === "toman")!.id;
    const results = await runEval(set, fixtureFor(set, new Map([[id, (o) => ({ ...o, amount_unit: "rial" })]])));
    expect(results.find((r) => r.id === id)?.pass).toBe(false);
  });

  it("harness detects an intent returned where clarification was expected (self-check)", async () => {
    const s = set.sentences.find((x) => x.expect.clarification)!;
    const results = await runEval(set, fixtureFor(set, new Map([[s.id, () => ({
      type: "PURCHASE_CASH", amount_text: "۱ تومان", amount_value: 1, amount_unit: "toman", date: set.today,
      paid_with: "CASH", bank_account: null, party_name: null, category: null, memo: null, confidence: 10, needs_clarification: null,
    })]])));
    expect(results.find((r) => r.id === s.id)?.pass).toBe(false);
  });
});

describe("eval through the OpenAI inbound path (mock LLM, offline)", () => {
  const set = loadEvalSet();
  const TOKEN = "eval-token";
  let server: Server;
  let base: string;

  beforeAll(async () => {
    server = createApp({
      llm: fixtureFor(set),
      tokens: [TOKEN],
      limiter: new FixedWindowLimiter(100_000, 60_000),
      logger: createLogger({ sink: () => {}, logText: false }),
      now: () => new Date(`${set.today}T06:00:00Z`),
    });
    await new Promise<void>((r) => server.listen(0, "127.0.0.1", () => r()));
    base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
  });
  afterAll(async () => { await new Promise((r) => server.close(r)); });

  it("tools mode (record_transaction / ask_clarification): every sentence", async () => {
    const results = await runEvalWith(set, viaOpenAi(base, TOKEN, "tools"));
    const failed = results.filter((r) => !r.pass);
    console.log(`EVAL openai-tools: ${results.length - failed.length}/${results.length} passed`);
    for (const f of failed) console.log(`FAIL ${f.id}: ${f.problems.join("; ")}`);
    expect(failed).toEqual([]);
  });

  it("content mode (JSON in message.content): every sentence", async () => {
    const results = await runEvalWith(set, viaOpenAi(base, TOKEN, "content"));
    const failed = results.filter((r) => !r.pass);
    console.log(`EVAL openai-content: ${results.length - failed.length}/${results.length} passed`);
    for (const f of failed) console.log(`FAIL ${f.id}: ${f.problems.join("; ")}`);
    expect(failed).toEqual([]);
  });
});
