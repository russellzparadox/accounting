import { createHash, timingSafeEqual } from "node:crypto";

/** Tokens come from PROXY_TOKENS (comma-separated). Empty list = every request is rejected (fail closed). */
export function parseTokens(env: string | undefined): string[] {
  return (env ?? "").split(",").map((s) => s.trim()).filter((s) => s.length > 0);
}

/** Short non-reversible id for logs and rate-limit keys. Raw tokens are never logged. */
export function tokenKey(token: string): string {
  return createHash("sha256").update(token).digest("hex").slice(0, 16);
}

export type AuthResult = { ok: true; key: string } | { ok: false };

export function authenticate(header: string | undefined, tokens: string[]): AuthResult {
  if (!header) return { ok: false };
  const m = /^Bearer\s+(\S+)$/.exec(header);
  if (!m) return { ok: false };
  const presented = Buffer.from(m[1]!, "utf8");
  let matched: string | null = null;
  for (const t of tokens) {
    const b = Buffer.from(t, "utf8");
    if (b.length === presented.length && timingSafeEqual(b, presented)) matched = t;
  }
  return matched === null ? { ok: false } : { ok: true, key: tokenKey(matched) };
}
