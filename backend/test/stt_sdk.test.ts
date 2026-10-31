// Inbound compatibility proof for speech: the official `openai` npm package (devDependency)
// calls audio.transcriptions.create against the local proxy. No network: stub STT client.
import type { AddressInfo } from "node:net";
import type { Server } from "node:http";
import OpenAI, { toFile } from "openai";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import type { LlmClient } from "../src/llm.js";
import { createLogger } from "../src/log.js";
import { FixedWindowLimiter } from "../src/ratelimit.js";
import { createApp } from "../src/server.js";
import type { SttClient, TranscriptionInput } from "../src/stt.js";

const TOKEN = "tok-stt-sdk-123";
const llm: LlmClient = { extract: async () => { throw new Error("unused"); } };
const seen: TranscriptionInput[] = [];
const stt: SttClient = {
  transcribe: async (input) => {
    seen.push(input);
    return { text: "نوشابه پانصد هزار تومان" };
  },
};

let server: Server;
let client: OpenAI;

beforeAll(async () => {
  server = createApp({
    llm,
    stt,
    tokens: [TOKEN],
    limiter: new FixedWindowLimiter(100, 60_000),
    logger: createLogger({ sink: () => {}, logText: false }),
  });
  await new Promise<void>((r) => server.listen(0, "127.0.0.1", () => r()));
  const base = `http://127.0.0.1:${(server.address() as AddressInfo).port}/v1`;
  client = new OpenAI({ baseURL: base, apiKey: TOKEN, maxRetries: 0 });
});
afterAll(async () => {
  server.closeAllConnections();
  await new Promise((r) => server.close(r));
});

describe("official openai SDK -> local proxy (audio.transcriptions)", () => {
  it("transcriptions.create returns the text", async () => {
    const file = await toFile(Buffer.from("fake-m4a-bytes"), "clip.m4a", { type: "audio/mp4" });
    const res = await client.audio.transcriptions.create({ file, model: "whisper-1", language: "fa" });
    expect(res.text).toBe("نوشابه پانصد هزار تومان");
    expect(seen.length).toBe(1);
    expect(seen[0]!.language).toBe("fa");
    expect(seen[0]!.filename).toBe("audio.m4a");
    expect(seen[0]!.format).toBe("json");
  });

  it("response_format=text is passed through", async () => {
    const file = await toFile(Buffer.from("x"), "clip.webm", { type: "audio/webm" });
    const res = await client.audio.transcriptions.create({ file, model: "whisper-1", response_format: "text" });
    expect(String(res)).toBe("نوشابه پانصد هزار تومان");
    expect(seen.at(-1)!.format).toBe("text");
  });

  it("bad token surfaces as an SDK authentication error", async () => {
    const bad = new OpenAI({ baseURL: client.baseURL, apiKey: "wrong", maxRetries: 0 });
    const file = await toFile(Buffer.from("x"), "clip.m4a", { type: "audio/mp4" });
    await expect(bad.audio.transcriptions.create({ file, model: "whisper-1" })).rejects.toMatchObject({ status: 401 });
  });
});
