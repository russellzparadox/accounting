import { describe, expect, it } from "vitest";
import { expectedIntentBody, llmOutputFor, loadEvalSet } from "../eval/harness.js";
import { fieldRuleErrors, TYPE_IDS } from "../src/rules.js";
import { validateLlmOutput, validateResponse } from "../src/validate.js";

const set = loadEvalSet();

describe("contract: eval expectations vs schemas and field rules", () => {
  it("has at least 50 sentences", () => {
    expect(set.sentences.length).toBeGreaterThanOrEqual(50);
  });

  it.each(set.sentences.map((s) => [s.id, s] as const))("%s validates against schemas", (_id, s) => {
    expect(validateLlmOutput(llmOutputFor(s, set.today))).toBe(true);
    if (s.expect.clarification) {
      expect(validateResponse({ needs_clarification: { question: "x" } })).toBe(true);
      return;
    }
    const body = expectedIntentBody(s, set.today);
    expect(validateResponse(body)).toBe(true);
    expect(fieldRuleErrors(body as never)).toEqual([]);
    expect(body.amount_rial).toBe((body.amount_value as number) * (body.amount_unit === "toman" ? 10 : 1));
    expect(s.text.includes(body.amount_text as string)).toBe(true);
  });

  it("covers every type id except REVERSAL", () => {
    const seen = new Set(set.sentences.map((s) => s.expect.type).filter(Boolean));
    for (const t of TYPE_IDS) expect(seen.has(t), t).toBe(true);
  });

  it("covers Persian, Arabic and Latin digits, number words, injection and clarification", () => {
    const texts = set.sentences.map((s) => s.text).join("\n");
    expect(/[۰-۹]/.test(texts)).toBe(true);
    expect(/[٠-٩]/.test(texts)).toBe(true);
    expect(/[0-9]/.test(texts)).toBe(true);
    expect(texts.includes("نیم میلیون")).toBe(true);
    expect(set.sentences.filter((s) => /ignore previous|نادیده بگیر/i.test(s.text)).length).toBeGreaterThanOrEqual(2);
    expect(set.sentences.some((s) => s.expect.clarification)).toBe(true);
  });
});

describe("contract: field rules reject invalid intents", () => {
  const base = {
    type: "PURCHASE_CASH", amount_text: "۱ تومان", amount_value: 1, amount_unit: "toman", amount_rial: 10,
    date: "2026-10-31", paid_with: "CASH", bank_account: null, party_name: null, category: null,
    memo: null, confidence: 50, needs_clarification: null,
  };
  it("accepts the base case", () => {
    expect(validateResponse(base)).toBe(true);
    expect(fieldRuleErrors(base as never)).toEqual([]);
  });
  it.each([
    ["cash purchase without paid_with", { paid_with: null }, "paid_with_required"],
    ["credit purchase with paid_with", { type: "PURCHASE_CREDIT", party_name: "x" }, "paid_with_must_be_null"],
    ["credit purchase without party", { type: "PURCHASE_CREDIT", paid_with: null }, "party_name_required"],
    ["expense without category", { type: "EXPENSE_PAID", category: null }, "category_required"],
    ["category on a sale", { type: "SALE_CASH", category: "RENT" }, "category_must_be_null"],
    ["bank_account on cash purchase", { bank_account: "1201" }, "bank_account_not_allowed"],
  ])("rule: %s", (_n, patch, code) => {
    expect(fieldRuleErrors({ ...base, ...patch } as never)).toContain(code);
  });
  it("schema rejects REVERSAL, zero amount, confidence 101, extra keys, non-null needs_clarification with intent", () => {
    expect(validateResponse({ ...base, type: "REVERSAL" })).toBe(false);
    expect(validateResponse({ ...base, amount_rial: 0 })).toBe(false);
    expect(validateResponse({ ...base, confidence: 101 })).toBe(false);
    expect(validateResponse({ ...base, amount_rial: 10, extra: 1 })).toBe(false);
    expect(validateResponse({ ...base, needs_clarification: { question: "q" } })).toBe(false);
  });
  it("LLM schema rejects amount_rial and non-integer amount_value", () => {
    const llm = llmOutputFor(set.sentences[0]!, set.today) as Record<string, unknown>;
    expect(validateLlmOutput({ ...llm, amount_rial: 5 })).toBe(false);
    expect(validateLlmOutput({ ...llm, amount_value: 1.5 })).toBe(false);
  });
});
