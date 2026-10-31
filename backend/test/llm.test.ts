import { describe, expect, it } from "vitest";
import { AnthropicLlmClient, LlmTransportError, TOOL_NAME, type MessagesTransport } from "../src/llm.js";

function fakeTransport(reply: unknown, sink: { body?: any }): MessagesTransport {
  return {
    messages: {
      create: async (body) => {
        sink.body = body;
        if (reply instanceof Error) throw reply;
        return reply as never;
      },
    },
  };
}

const toolReply = {
  stop_reason: "tool_use",
  content: [{ type: "tool_use", id: "t1", name: TOOL_NAME, input: { type: "PURCHASE_CASH" }, caller: { type: "direct" } }],
};

describe("AnthropicLlmClient request shape (no network)", () => {
  it("forces the tool, uses strict schema, caches system prompt, model from option", async () => {
    const sink: { body?: any } = {};
    const c = new AnthropicLlmClient({ apiKey: "sk-test", model: "claude-haiku-5-5", transport: fakeTransport(toolReply, sink) });
    const out = await c.extract({ text: "نمونه", today: "2026-10-31" });
    expect(out).toEqual({ type: "PURCHASE_CASH" });
    expect(sink.body.model).toBe("claude-haiku-5-5");
    expect(sink.body.tool_choice).toEqual({ type: "tool", name: TOOL_NAME });
    expect(sink.body.tools[0].strict).toBe(true);
    expect(sink.body.tools[0].input_schema.additionalProperties).toBe(false);
    expect(sink.body.system[0].cache_control).toEqual({ type: "ephemeral" });
    expect(sink.body.messages[0].content).toContain("today: 2026-10-31");
    expect(JSON.stringify(sink.body.system)).not.toContain("2026-10-31");
  });

  it("no tool_use block -> LlmTransportError llm_no_tool_use", async () => {
    const c = new AnthropicLlmClient({ apiKey: "k", model: "m", transport: fakeTransport({ stop_reason: "end_turn", content: [{ type: "text", text: "hi" }] }, {}) });
    await expect(c.extract({ text: "x", today: "2026-10-31" })).rejects.toMatchObject({ code: "llm_no_tool_use" });
  });

  it("max_tokens stop -> LlmTransportError", async () => {
    const c = new AnthropicLlmClient({ apiKey: "k", model: "m", transport: fakeTransport({ ...toolReply, stop_reason: "max_tokens" }, {}) });
    await expect(c.extract({ text: "x", today: "2026-10-31" })).rejects.toMatchObject({ code: "llm_stop_max_tokens" });
  });

  it("SDK error: keeps status, drops message (no key or body echoed)", async () => {
    const err = Object.assign(new Error("invalid x-api-key sk-ant-SECRET-123"), { status: 401 });
    const c = new AnthropicLlmClient({ apiKey: "k", model: "m", transport: fakeTransport(err, {}) });
    const e = await c.extract({ text: "x", today: "2026-10-31" }).catch((x: unknown) => x);
    expect(e).toBeInstanceOf(LlmTransportError);
    expect((e as LlmTransportError).status).toBe(401);
    expect((e as Error).message).toBe("llm_request_failed");
    expect(JSON.stringify(e)).not.toContain("SECRET");
  });
});
