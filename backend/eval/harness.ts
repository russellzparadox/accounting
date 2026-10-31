import { readFileSync } from "node:fs";
import type { ExtractInput, LlmClient } from "../src/llm.js";
import { parseText, type ParseResult } from "../src/parse.js";

export interface Expect {
  clarification?: boolean;
  type?: string;
  amount_text?: string;
  amount_value?: number;
  amount_unit?: "toman" | "rial";
  amount_rial?: number;
  date?: string; // default: set today
  paid_with?: "CASH" | "BANK" | null; // default null
  bank_account?: string | null; // default null
  party_name?: string | null; // default null
  category?: string | null; // default null
}
export interface EvalSentence { id: string; text: string; expect: Expect }
export interface EvalSet { version: number; today: string; sentences: EvalSentence[] }

export function loadEvalSet(): EvalSet {
  return JSON.parse(readFileSync(new URL("./sentences.fa.json", import.meta.url), "utf8")) as EvalSet;
}

const CLARIFY_QUESTION = "متن شامل یک تراکنش مالی روشن نیست. لطفاً دوباره بگویید.";

/** LLM tool input (what the model would emit) for one eval sentence. amount_rial is NOT included. */
export function llmOutputFor(s: EvalSentence, today: string): unknown {
  const e = s.expect;
  if (e.clarification) {
    return {
      type: null, amount_text: null, amount_value: null, amount_unit: null, date: null,
      paid_with: null, bank_account: null, party_name: null, category: null, memo: null,
      confidence: 40, needs_clarification: { question: CLARIFY_QUESTION },
    };
  }
  return {
    type: e.type, amount_text: e.amount_text, amount_value: e.amount_value, amount_unit: e.amount_unit,
    date: e.date ?? today, paid_with: e.paid_with ?? null, bank_account: e.bank_account ?? null,
    party_name: e.party_name ?? null, category: e.category ?? null, memo: null, confidence: 90,
    needs_clarification: null,
  };
}

/** Full response body the server must return for an intent expectation. */
export function expectedIntentBody(s: EvalSentence, today: string): Record<string, unknown> {
  const e = s.expect;
  return {
    type: e.type, amount_text: e.amount_text, amount_value: e.amount_value, amount_unit: e.amount_unit,
    amount_rial: e.amount_rial, date: e.date ?? today, paid_with: e.paid_with ?? null,
    bank_account: e.bank_account ?? null, party_name: e.party_name ?? null, category: e.category ?? null,
    memo: null, confidence: 90, needs_clarification: null,
  };
}

/** Mock LLM: returns the eval expectation for each sentence text. Checks harness plumbing, not model quality. */
export class FixtureLlm implements LlmClient {
  constructor(private readonly byText: Map<string, unknown>) {}
  async extract({ text }: ExtractInput): Promise<unknown> {
    if (!this.byText.has(text)) throw new Error(`no fixture for text`);
    return this.byText.get(text);
  }
}

export function fixtureFor(set: EvalSet, overrides: Map<string, (o: any) => any> = new Map()): FixtureLlm {
  const m = new Map<string, unknown>();
  for (const s of set.sentences) {
    const out = llmOutputFor(s, set.today);
    m.set(s.text, overrides.get(s.id) ? overrides.get(s.id)!(out) : out);
  }
  return new FixtureLlm(m);
}

export interface EvalResult { id: string; pass: boolean; problems: string[] }

/** A call that turns one sentence into a ParseResult-shaped outcome (parseText, or the OpenAI route). */
export type EvalCall = (text: string) => Promise<ParseResult>;

export async function runEval(set: EvalSet, llm: LlmClient): Promise<EvalResult[]> {
  return runEvalWith(set, (text) => parseText({ text, today: set.today }, llm));
}

export async function runEvalWith(set: EvalSet, call: EvalCall): Promise<EvalResult[]> {
  const results: EvalResult[] = [];
  for (const s of set.sentences) {
    const problems: string[] = [];
    try {
      const r = await call(s.text);
      if (s.expect.clarification) {
        if (r.kind !== "clarification") problems.push(`expected clarification, got ${r.kind}`);
      } else if (r.kind !== "intent") {
        problems.push(`expected intent, got ${r.kind}`);
      } else {
        const want = expectedIntentBody(s, set.today);
        for (const k of Object.keys(want)) {
          if (k === "needs_clarification") continue;
          if (JSON.stringify((r.body as Record<string, unknown>)[k]) !== JSON.stringify(want[k])) {
            problems.push(`${k}: want ${JSON.stringify(want[k])} got ${JSON.stringify((r.body as Record<string, unknown>)[k])}`);
          }
        }
        if (!s.text.includes(r.body.amount_text)) problems.push("amount_text not a substring of text");
      }
    } catch (e) {
      problems.push(`threw ${e instanceof Error ? e.name : "unknown"}`);
    }
    results.push({ id: s.id, pass: problems.length === 0, problems });
  }
  return results;
}

/**
 * Drives one sentence through POST /v1/chat/completions of a running proxy and converts the
 * OpenAI-shaped answer back to a ParseResult. mode "tools" forces the record_transaction tool call;
 * mode "content" sends no tools and reads JSON from message.content.
 */
export function viaOpenAi(baseUrl: string, token: string, mode: "tools" | "content", model = "eval-model"): EvalCall {
  return async (text) => {
    const body: Record<string, unknown> = { model, messages: [{ role: "user", content: text }] };
    if (mode === "tools") {
      body.tools = [{ type: "function", function: { name: "record_transaction", parameters: { type: "object" } } }];
      body.tool_choice = { type: "function", function: { name: "record_transaction" } };
    }
    const res = await fetch(`${baseUrl}/v1/chat/completions`, {
      method: "POST",
      headers: { "content-type": "application/json", authorization: `Bearer ${token}` },
      body: JSON.stringify(body),
    });
    if (res.status !== 200) throw new Error(`HTTP ${res.status}`);
    const json = (await res.json()) as { choices: { message: { content: string | null; tool_calls?: { function: { name: string; arguments: string } }[] } }[] };
    const msg = json.choices[0]!.message;
    if (mode === "tools" && msg.tool_calls && msg.tool_calls.length > 0) {
      const fn = msg.tool_calls[0]!.function;
      const args = JSON.parse(fn.arguments) as Record<string, unknown>;
      if (fn.name === "ask_clarification") return { kind: "clarification", body: { needs_clarification: { question: String(args.question) } } } as ParseResult;
      return { kind: "intent", body: args as never };
    }
    const parsed = JSON.parse(msg.content ?? "null") as Record<string, unknown>;
    if (parsed.needs_clarification) return { kind: "clarification", body: parsed as never };
    return { kind: "intent", body: parsed as never };
  };
}
