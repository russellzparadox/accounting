import { describe, expect, it } from "vitest";
import { fixtureFor, loadEvalSet, runEval } from "../eval/harness.js";

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
