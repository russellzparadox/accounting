import { describe, expect, it } from "vitest";
import { ConfigError, loadConfig } from "../src/config.js";

describe("provider selection (env)", () => {
  it("default provider is anthropic; model from ANTHROPIC_MODEL", () => {
    const c = loadConfig({ ANTHROPIC_API_KEY: "k" });
    expect(c.provider).toBe("anthropic");
    expect(c.model).toBe("claude-haiku-5-5");
    expect(loadConfig({ ANTHROPIC_MODEL: "m2" }).model).toBe("m2");
  });

  it("openai: requires LLM_MODEL; defaults base URL and timeout; no key needed", () => {
    expect(() => loadConfig({ LLM_PROVIDER: "openai" })).toThrow(ConfigError);
    const c = loadConfig({ LLM_PROVIDER: "openai", LLM_MODEL: "llama3" });
    expect(c.provider).toBe("openai");
    expect(c.model).toBe("llama3");
    expect(c.openaiBaseUrl).toBe("https://api.openai.com/v1");
    expect(c.openaiApiKey).toBeUndefined();
    expect(c.timeoutMs).toBe(20_000);
  });

  it("openai: base URL trailing slashes stripped; timeout from LLM_TIMEOUT_MS", () => {
    const c = loadConfig({
      LLM_PROVIDER: "openai", LLM_MODEL: "m", OPENAI_BASE_URL: "http://localhost:11434/v1///",
      OPENAI_API_KEY: "sk-x", LLM_TIMEOUT_MS: "5000",
    });
    expect(c.openaiBaseUrl).toBe("http://localhost:11434/v1");
    expect(c.openaiApiKey).toBe("sk-x");
    expect(c.timeoutMs).toBe(5000);
  });

  it("invalid provider and invalid timeout", () => {
    expect(() => loadConfig({ LLM_PROVIDER: "gemini" })).toThrow("LLM_PROVIDER must be anthropic or openai");
    expect(loadConfig({ LLM_PROVIDER: "openai", LLM_MODEL: "m", LLM_TIMEOUT_MS: "-3" }).timeoutMs).toBe(20_000);
  });

  it("stt: unset base URL means disabled; trailing slashes stripped; defaults", () => {
    const off = loadConfig({ ANTHROPIC_API_KEY: "k" });
    expect(off.sttBaseUrl).toBeUndefined();
    expect(off.sttApiKey).toBeUndefined();
    expect(off.sttModel).toBe("whisper-1");
    expect(off.sttTimeoutMs).toBe(30_000);
    const on = loadConfig({
      ANTHROPIC_API_KEY: "k", STT_BASE_URL: "https://api.groq.com/openai/v1//", STT_API_KEY: "g",
      STT_MODEL: "whisper-large-v3", STT_TIMEOUT_MS: "9000",
    });
    expect(on.sttBaseUrl).toBe("https://api.groq.com/openai/v1");
    expect(on.sttApiKey).toBe("g");
    expect(on.sttModel).toBe("whisper-large-v3");
    expect(on.sttTimeoutMs).toBe(9000);
    expect(loadConfig({ ANTHROPIC_API_KEY: "k", STT_BASE_URL: "   " }).sttBaseUrl).toBeUndefined();
    expect(loadConfig({ ANTHROPIC_API_KEY: "k", STT_TIMEOUT_MS: "-1" }).sttTimeoutMs).toBe(30_000);
  });
});
