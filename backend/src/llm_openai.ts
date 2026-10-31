import { SYSTEM_PROMPT } from "./prompt.js";
import { LlmTransportError, TOOL_NAME, type ExtractInput, type LlmClient } from "./llm.js";
import { llmOutputSchema, validateLlmOutput } from "./validate.js";

/**
 * Upstream OpenAI-compatible client (OpenAI, OpenRouter, vLLM, Ollama, LM Studio, ...).
 * Uses Node fetch, no SDK. Order of attempts:
 *  1. forced tool call `record_transaction` (strict schema)
 *  2. response_format json_schema, only if step 1 was refused (400) or ignored (no tool_calls)
 *  3. response_format json_object + schema in system text, first JSON object extracted
 * Schema validation happens here only to choose between fallbacks; parse.ts re-validates.
 * Upstream bodies, URLs and keys are never copied into errors.
 */
export interface OpenAiLlmOptions {
  baseUrl: string;
  model: string;
  apiKey?: string;
  timeoutMs: number;
  /** Test seam. Defaults to global fetch. */
  fetchImpl?: typeof fetch;
}

type Step = "tools" | "json_schema" | "json_object";

interface PostResult {
  ok: boolean;
  status: number;
  json: unknown;
}

// OpenAI strict schemas reject draft-07 meta keys. The shape itself is unchanged.
function openAiSchema(): Record<string, unknown> {
  const { $schema: _s, $id: _i, ...rest } = llmOutputSchema as Record<string, unknown>;
  return rest;
}

/** First balanced {...} object in free text. Returns null when none parses. */
export function extractFirstJsonObject(text: string): unknown {
  for (let start = text.indexOf("{"); start !== -1; start = text.indexOf("{", start + 1)) {
    let depth = 0;
    let inStr = false;
    let esc = false;
    for (let i = start; i < text.length; i++) {
      const ch = text[i]!;
      if (inStr) {
        if (esc) esc = false;
        else if (ch === "\\") esc = true;
        else if (ch === '"') inStr = false;
        continue;
      }
      if (ch === '"') inStr = true;
      else if (ch === "{") depth++;
      else if (ch === "}") {
        depth--;
        if (depth === 0) {
          try {
            return JSON.parse(text.slice(start, i + 1));
          } catch {
            break;
          }
        }
      }
    }
  }
  return null;
}

function safeJsonParse(s: string): unknown {
  try {
    return JSON.parse(s);
  } catch {
    return null;
  }
}

export class OpenAiLlmClient implements LlmClient {
  private readonly fetchImpl: typeof fetch;

  constructor(private readonly opts: OpenAiLlmOptions) {
    this.fetchImpl = opts.fetchImpl ?? fetch;
  }

  async extract(input: ExtractInput): Promise<unknown> {
    const userContent = `today: ${input.today}\n<user_text>\n${input.text}\n</user_text>`;

    // 1. forced tool call
    const toolRes = await this.post(this.body("tools", userContent));
    if (toolRes.ok) {
      const tc = firstToolCall(toolRes.json);
      if (tc !== undefined) return tc.name === TOOL_NAME ? safeJsonParse(tc.arguments) : null;
    } else if (toolRes.status !== 400) {
      throw upstreamError(toolRes.status);
    }
    // Refused (400) or ignored (200 without tool_calls): fall through.

    // 2. json_schema
    const schemaRes = await this.post(this.body("json_schema", userContent));
    if (schemaRes.ok) {
      const parsed = safeJsonParse(messageContent(schemaRes.json));
      if (validateLlmOutput(parsed)) return parsed;
    } else if (schemaRes.status !== 400) {
      throw upstreamError(schemaRes.status);
    }

    // 3. json_object + first JSON object from content
    const objRes = await this.post(this.body("json_object", userContent));
    if (!objRes.ok) throw upstreamError(objRes.status);
    return extractFirstJsonObject(messageContent(objRes.json));
  }

  private body(step: Step, userContent: string): Record<string, unknown> {
    const system = step === "json_object"
      ? `${SYSTEM_PROMPT}\n\nReply with one JSON object only, matching this JSON Schema: ${JSON.stringify(openAiSchema())}`
      : SYSTEM_PROMPT;
    const base: Record<string, unknown> = {
      model: this.opts.model,
      max_tokens: 1024,
      messages: [
        { role: "system", content: system },
        { role: "user", content: userContent },
      ],
    };
    if (step === "tools") {
      base.tools = [{
        type: "function",
        function: {
          name: TOOL_NAME,
          description: "Record one parsed accounting transaction from the user's Persian text.",
          parameters: openAiSchema(),
          strict: true,
        },
      }];
      base.tool_choice = { type: "function", function: { name: TOOL_NAME } };
    } else if (step === "json_schema") {
      base.response_format = {
        type: "json_schema",
        json_schema: { name: TOOL_NAME, schema: openAiSchema(), strict: true },
      };
    } else {
      base.response_format = { type: "json_object" };
    }
    return base;
  }

  private async post(body: Record<string, unknown>): Promise<PostResult> {
    const headers: Record<string, string> = { "content-type": "application/json" };
    if (this.opts.apiKey) headers.authorization = `Bearer ${this.opts.apiKey}`;
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), this.opts.timeoutMs);
    try {
      const res = await this.fetchImpl(`${this.opts.baseUrl}/chat/completions`, {
        method: "POST",
        headers,
        body: JSON.stringify(body),
        signal: ctrl.signal,
      });
      // Upstream body is read for parsing only. It is never returned or logged.
      const text = await res.text();
      return { ok: res.ok, status: res.status, json: safeJsonParse(text) };
    } catch (e) {
      const aborted = (e as { name?: string }).name === "AbortError";
      throw new LlmTransportError(aborted ? "llm_timeout" : "llm_request_failed");
    } finally {
      clearTimeout(timer);
    }
  }
}

function upstreamError(status: number): LlmTransportError {
  if (status === 401 || status === 403) return new LlmTransportError("llm_auth_failed", status);
  if (status === 429) return new LlmTransportError("llm_rate_limited", status);
  if (status >= 500) return new LlmTransportError("llm_upstream_error", status);
  return new LlmTransportError("llm_bad_request", status);
}

function firstMessage(json: unknown): Record<string, unknown> | undefined {
  const choices = (json as { choices?: unknown })?.choices;
  if (!Array.isArray(choices)) return undefined;
  const m = (choices[0] as { message?: unknown } | undefined)?.message;
  return typeof m === "object" && m !== null ? (m as Record<string, unknown>) : undefined;
}

function firstToolCall(json: unknown): { name: string; arguments: string } | undefined {
  const calls = firstMessage(json)?.tool_calls;
  if (!Array.isArray(calls) || calls.length === 0) return undefined;
  const fn = (calls[0] as { function?: { name?: unknown; arguments?: unknown } }).function;
  if (typeof fn?.name !== "string" || typeof fn.arguments !== "string") return { name: "", arguments: "" };
  return { name: fn.name, arguments: fn.arguments };
}

function messageContent(json: unknown): string {
  const c = firstMessage(json)?.content;
  return typeof c === "string" ? c : "";
}
