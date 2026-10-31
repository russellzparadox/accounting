// Inbound OpenAI-compatible surface: POST /v1/chat/completions and GET /v1/models.
// Pure helpers only (no HTTP). server.ts wires auth, rate limit, body reading and the parse pipeline.
//
// The proxy is an accounting parser, not a chat model: it takes the LAST user message as the
// sentence, ignores system/developer/assistant/tool messages, and runs the same parse pipeline
// as /v1/parse. Request sampling fields (temperature, max_tokens, ...) are accepted and ignored.
import { randomBytes } from "node:crypto";
import type { ParseResult } from "./parse.js";
import { MAX_TEXT_CHARS, validateText } from "./text.js";

export const OPENAI_MAX_BODY_BYTES = 32 * 1024;
export const TOOL_RECORD = "record_transaction";
export const TOOL_CLARIFY = "ask_clarification";
export const OWNED_BY = "accounting-proxy";

const ROLES = new Set(["system", "developer", "user", "assistant", "tool"]);

export interface OpenAiError {
  status: number;
  body: { error: { message: string; type: string; param: string | null; code: string | null } };
  headers?: Record<string, string>;
}

export function oaError(
  status: number,
  type: string,
  code: string | null,
  message: string,
  param: string | null = null,
  headers?: Record<string, string>,
): OpenAiError {
  return { status, body: { error: { message, type, param, code } }, headers };
}

export interface ChatRequest {
  model: string | undefined;
  stream: boolean;
  hasTools: boolean;
  text: string;
}

/** Validates the chat body and extracts the last user message. Returns an OpenAI-shaped 400 on failure. */
export function parseChatRequest(x: unknown): { ok: true; req: ChatRequest } | { ok: false; err: OpenAiError } {
  const bad = (message: string, param: string | null) =>
    ({ ok: false as const, err: oaError(400, "invalid_request_error", null, message, param) });

  if (typeof x !== "object" || x === null || Array.isArray(x)) {
    return bad("Request body must be a JSON object.", null);
  }
  const b = x as Record<string, unknown>;

  if (b.model !== undefined && typeof b.model !== "string") return bad("'model' must be a string.", "model");
  if (b.stream !== undefined && typeof b.stream !== "boolean") return bad("'stream' must be a boolean.", "stream");
  if (b.tools !== undefined && !Array.isArray(b.tools)) return bad("'tools' must be an array.", "tools");

  if (!Array.isArray(b.messages) || b.messages.length === 0) {
    return bad("'messages' must be a non-empty array.", "messages");
  }
  for (const m of b.messages) {
    if (typeof m !== "object" || m === null || typeof (m as { role?: unknown }).role !== "string" ||
        !ROLES.has((m as { role: string }).role)) {
      return bad("Each message needs a valid 'role'.", "messages");
    }
  }

  const last = [...(b.messages as { role: string; content?: unknown }[])].reverse().find((m) => m.role === "user");
  if (!last) return bad("'messages' must contain a user message.", "messages");
  const content = userText(last.content);
  if (content === null) return bad("User message content must be a string or text parts only.", "messages");
  const text = validateText(content);
  if (text === null) {
    return bad(`User message must be 1 to ${MAX_TEXT_CHARS} characters after trimming.`, "messages");
  }

  return {
    ok: true,
    req: {
      model: b.model as string | undefined,
      stream: b.stream === true,
      hasTools: Array.isArray(b.tools) && b.tools.length > 0 && b.tool_choice !== "none",
      text,
    },
  };
}

/** String content, or an array of {type:"text", text} parts joined by newlines. Anything else is null. */
function userText(content: unknown): string | null {
  if (typeof content === "string") return content;
  if (!Array.isArray(content)) return null;
  const texts: string[] = [];
  for (const p of content) {
    if (typeof p !== "object" || p === null || (p as { type?: unknown }).type !== "text") return null;
    const t = (p as { text?: unknown }).text;
    if (typeof t !== "string") return null;
    texts.push(t);
  }
  return texts.join("\n");
}

export function completionId(): string {
  return `chatcmpl-${randomBytes(12).toString("hex")}`;
}

function toolCallId(): string {
  return `call_${randomBytes(12).toString("hex")}`;
}

/** One tool call (tools mode) or JSON content (plain mode) for a ParseResult. */
export function assistantPayload(
  result: ParseResult,
  hasTools: boolean,
): { content: string | null; tool_calls?: ToolCall[]; finish_reason: "stop" | "tool_calls" } {
  if (!hasTools) {
    return { content: JSON.stringify(result.body), finish_reason: "stop" };
  }
  const call: ToolCall = result.kind === "intent"
    ? { id: toolCallId(), type: "function", function: { name: TOOL_RECORD, arguments: JSON.stringify(result.body) } }
    : {
      id: toolCallId(),
      type: "function",
      function: {
        name: TOOL_CLARIFY,
        arguments: JSON.stringify({ question: result.body.needs_clarification.question }),
      },
    };
  return { content: null, tool_calls: [call], finish_reason: "tool_calls" };
}

export interface ToolCall {
  id: string;
  type: "function";
  function: { name: string; arguments: string };
}

export function completionBody(args: {
  id: string; created: number; model: string; result: ParseResult; hasTools: boolean;
}): Record<string, unknown> {
  const p = assistantPayload(args.result, args.hasTools);
  const message: Record<string, unknown> = { role: "assistant", content: p.content };
  if (p.tool_calls) message.tool_calls = p.tool_calls;
  return {
    id: args.id,
    object: "chat.completion",
    created: args.created,
    model: args.model,
    choices: [{ index: 0, message, finish_reason: p.finish_reason }],
    // The proxy does not meter tokens in v1. Fields are present for SDK compatibility.
    usage: { prompt_tokens: 0, completion_tokens: 0, total_tokens: 0 },
  };
}

/** Minimal SSE: one delta chunk with the whole payload, a final chunk with finish_reason, then [DONE]. */
export function streamBody(args: {
  id: string; created: number; model: string; result: ParseResult; hasTools: boolean;
}): string {
  const p = assistantPayload(args.result, args.hasTools);
  const base = { id: args.id, object: "chat.completion.chunk", created: args.created, model: args.model };
  const delta: Record<string, unknown> = { role: "assistant", content: p.content };
  if (p.tool_calls) {
    delta.tool_calls = p.tool_calls.map((c, i) => ({ index: i, ...c }));
  }
  const first = { ...base, choices: [{ index: 0, delta, finish_reason: null }] };
  const last = { ...base, choices: [{ index: 0, delta: {}, finish_reason: p.finish_reason }] };
  return `data: ${JSON.stringify(first)}\n\ndata: ${JSON.stringify(last)}\n\ndata: [DONE]\n\n`;
}

export function modelsBody(model: string, created: number): Record<string, unknown> {
  return { object: "list", data: [{ id: model, object: "model", created, owned_by: OWNED_BY }] };
}
