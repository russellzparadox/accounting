# Eval report (T13): backend /v1/parse

Set: `backend/eval/sentences.fa.json`, version 1, 58 sentences, today `2026-10-31`.
Run: `cd backend && npm test` (vitest, 9 files, 175 tests, all green) and the eval block inside it.

## Results (mock LLM, offline)
| Path | Sentences | Passed | How |
|---|---|---|---|
| direct (`parseText`) | 58 | 58 | `eval.test.ts` "runs every sentence" |
| OpenAI inbound, tools mode | 58 | 58 | `record_transaction` / `ask_clarification` tool calls |
| OpenAI inbound, content mode | 58 | 58 | JSON in `message.content` |

The mock LLM returns each sentence's expected answer. So these runs check the plumbing only: schema validation, deterministic amount conversion (toman to rial), field rules, date defaulting and the clarification path. They say nothing about model quality. Two self-checks in the same file confirm the harness fails on a wrong unit and on an intent where a clarification was expected.

## Coverage of the set (by expected answer)
| Expected | Sentences |
|---|---|
| PURCHASE_CASH | 10 |
| PURCHASE_CREDIT | 4 |
| SALE_CASH | 2 |
| SALE_CREDIT | 2 |
| EXPENSE_PAID | 10 |
| EXPENSE_PAYABLE | 2 |
| RECEIPT_FROM_PERSON | 3 |
| PAYMENT_TO_PERSON | 2 |
| BANK_TRANSFER | 3 |
| OWNER_CAPITAL | 2 |
| OWNER_WITHDRAWAL | 2 |
| PURCHASE_RETURN | 2 |
| SALE_RETURN | 2 |
| clarification (no intent expected) | 12 |
| **Total** | **58** |

Gaps in the set: no `REVERSAL` or `MANUAL` sentence (these are not intents). No bank-to-bank transfer (not implemented, see `docs/ACCOUNTANT_REVIEW.md`). No spoken-number sentences in this set; they are covered by the pipeline tests (`SpokenAmountPipelineTest`) and the VM voice-path test (`EntryViewModelTest`).

## Failures
None on the mock runs.

## Live run: NOT run
No `ANTHROPIC_API_KEY` or OpenAI-compatible key is available in this session, so no live numbers exist. Do not read the mock 58/58 as model accuracy.

Expected live gate: at least 90% of sentences with `type`, `amount_rial` and `paid_with` all correct. The current tests do not compute this gate. `eval.test.ts` compares every expected field, and only against the mock. There is no live script.

How to run live, once a key exists:
1. Start the proxy with the key set, for example `LLM_PROVIDER=anthropic ANTHROPIC_API_KEY=... PROXY_TOKENS=<token> npm start` (after `npm run build`). For an OpenAI-compatible server: `LLM_PROVIDER=openai LLM_MODEL=<id> OPENAI_BASE_URL=<url> OPENAI_API_KEY=<key> PROXY_TOKENS=<token> npm start`.
2. Drive the sentences through the proxy with `viaOpenAi(baseUrl, token, "tools")` from `backend/eval/harness.ts` (`runEvalWith`). This reports the per-sentence problems list.
3. The 90% gate on the three fields still needs a small script that counts them. Write it before reading the result as a gate.

Open issue (see handout): the live gate and the live script are not implemented. The live provider behaviour of the Anthropic and OpenAI-compatible paths is still unverified.
