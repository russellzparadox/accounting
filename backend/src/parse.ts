import type { LlmClient } from "./llm.js";
import { toRial } from "./amount.js";
import { fieldRuleErrors, isValidIsoDate } from "./rules.js";
import { validateLlmOutput, validateResponse } from "./validate.js";

/** Output that fails validation. The server maps it to 502 and never echoes the content. */
export class LlmOutputError extends Error {
  constructor(readonly code: string) {
    super(code);
    this.name = "LlmOutputError";
  }
}

export interface Clarification {
  needs_clarification: { question: string };
}

export interface Intent {
  type: string;
  amount_text: string;
  amount_value: number;
  amount_unit: "toman" | "rial";
  amount_rial: number;
  date: string;
  paid_with: "CASH" | "BANK" | null;
  bank_account: string | null;
  party_name: string | null;
  category: string | null;
  memo: string | null;
  confidence: number;
  needs_clarification: null;
}

export type ParseResult = { kind: "intent"; body: Intent } | { kind: "clarification"; body: Clarification };

/**
 * Pipeline: LLM tool input -> schema check -> clarification short-circuit -> deterministic amount_rial
 * -> field-rule check -> response schema check. Any failure after the LLM is LlmOutputError.
 */
export async function parseText(
  input: { text: string; today: string },
  llm: LlmClient,
): Promise<ParseResult> {
  const raw = await llm.extract(input);
  if (!validateLlmOutput(raw)) throw new LlmOutputError("llm_schema_violation");
  const o = raw as {
    type: string | null; amount_text: string | null; amount_value: number | null;
    amount_unit: "toman" | "rial" | null; date: string | null; paid_with: "CASH" | "BANK" | null;
    bank_account: string | null; party_name: string | null; category: string | null;
    memo: string | null; confidence: number; needs_clarification: { question: string } | null;
  };

  if (o.needs_clarification !== null) {
    return { kind: "clarification", body: { needs_clarification: { question: o.needs_clarification.question } } };
  }
  if (o.type === null || o.amount_text === null || o.amount_value === null ||
      o.amount_unit === null || o.date === null) {
    throw new LlmOutputError("llm_incomplete");
  }
  if (!isValidIsoDate(o.date)) throw new LlmOutputError("llm_bad_date");

  let amount_rial: number;
  try {
    amount_rial = toRial(o.amount_value, o.amount_unit);
  } catch {
    throw new LlmOutputError("llm_bad_amount");
  }

  const intent: Intent = {
    type: o.type,
    amount_text: o.amount_text,
    amount_value: o.amount_value,
    amount_unit: o.amount_unit,
    amount_rial,
    date: o.date,
    paid_with: o.paid_with,
    bank_account: o.bank_account,
    party_name: o.party_name,
    category: o.category,
    memo: o.memo,
    confidence: o.confidence,
    needs_clarification: null,
  };
  if (fieldRuleErrors(intent).length > 0) throw new LlmOutputError("llm_field_rule_violation");
  if (!validateResponse(intent)) throw new LlmOutputError("response_schema_violation");
  return { kind: "intent", body: intent };
}
