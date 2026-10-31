export type LogSink = (line: string) => void;

export interface Logger {
  info(event: string, fields: Record<string, string | number | boolean | null>): void;
}

const NEVER_LOGGED = new Set(["authorization", "api_key", "apiKey", "token"]);

/**
 * JSON-line logger. The `text` field (user input) is dropped unless logText is true
 * (DEBUG_LOG_TEXT=1). Credential-like keys are always dropped.
 */
export function createLogger(opts: { sink?: LogSink; logText: boolean }): Logger {
  const sink = opts.sink ?? ((line: string) => process.stdout.write(line + "\n"));
  return {
    info(event, fields) {
      const out: Record<string, unknown> = { ts: new Date().toISOString(), level: "info", event };
      for (const [k, v] of Object.entries(fields)) {
        if (NEVER_LOGGED.has(k)) continue;
        if (k === "text" && !opts.logText) continue;
        out[k] = v;
      }
      sink(JSON.stringify(out));
    },
  };
}
