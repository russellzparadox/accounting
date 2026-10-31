import Anthropic from "@anthropic-ai/sdk";
import { SYSTEM_PROMPT } from "./prompt.js";
import { llmOutputSchema } from "./validate.js";

export const TOOL_NAME = "record_transaction";

export interface ExtractInput {
  text: string;
  today: string; // yyyy-MM-dd, Asia/Tehran
}

/** LLM client seam. Tests and the eval harness inject a mock; production uses AnthropicLlmClient. */
export interface LlmClient {
  /** Returns the raw tool input. Validation happens in parse.ts, not here. */
  extract(input: ExtractInput): Promise<unknown>;
}

export class LlmTransportError extends Error {
  constructor(readonly code: string, readonly status?: number) {
    // Never carries the SDK message: it can echo request data or headers.
    super(code);
    this.name = "LlmTransportError";
  }
}

/** Minimal transport surface, so tests can fake the SDK without network. */
export interface MessagesTransport {
  messages: { create(body: Anthropic.MessageCreateParamsNonStreaming): Promise<Anthropic.Message> };
}

export interface AnthropicLlmOptions {
  apiKey: string;
  model: string;
  timeoutMs?: number;
  transport?: MessagesTransport;
}

export class AnthropicLlmClient implements LlmClient {
  private readonly transport: MessagesTransport;
  private readonly model: string;

  constructor(opts: AnthropicLlmOptions) {
    this.model = opts.model;
    this.transport =
      opts.transport ??
      new Anthropic({ apiKey: opts.apiKey, maxRetries: 2, timeout: opts.timeoutMs ?? 20_000 });
  }

  async extract({ text, today }: ExtractInput): Promise<unknown> {
    let msg: Anthropic.Message;
    try {
      msg = await this.transport.messages.create({
        model: this.model,
        max_tokens: 1024,
        thinking: { type: "disabled" },
        output_config: { effort: "low" },
        system: [{ type: "text", text: SYSTEM_PROMPT, cache_control: { type: "ephemeral" } }],
        tools: [
          {
            name: TOOL_NAME,
            description: "Record one parsed accounting transaction from the user's Persian text.",
            input_schema: llmOutputSchema as Anthropic.Tool.InputSchema,
            strict: true,
          },
        ],
        tool_choice: { type: "tool", name: TOOL_NAME },
        messages: [{ role: "user", content: `today: ${today}\n<user_text>\n${text}\n</user_text>` }],
      });
    } catch (e) {
      const status = typeof (e as { status?: unknown }).status === "number"
        ? (e as { status: number }).status
        : undefined;
      throw new LlmTransportError("llm_request_failed", status);
    }
    if (msg.stop_reason === "max_tokens" || msg.stop_reason === "refusal") {
      throw new LlmTransportError(`llm_stop_${msg.stop_reason}`);
    }
    const block = msg.content.find((b) => b.type === "tool_use" && b.name === TOOL_NAME);
    if (!block || block.type !== "tool_use") throw new LlmTransportError("llm_no_tool_use");
    return block.input;
  }
}
