export const MAX_TEXT_CHARS = 500;

/** Trimmed text with 1..MAX_TEXT_CHARS code points, or null. Shared by /v1/parse and /v1/chat/completions. */
export function validateText(t: string): string | null {
  const trimmed = t.trim();
  const n = [...trimmed].length;
  return n >= 1 && n <= MAX_TEXT_CHARS ? trimmed : null;
}
