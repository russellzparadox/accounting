# Tasks index
Run sequentially, one subagent at a time. Handouts are the only inter-task channel.

| ID | Name | Depends | Status |
|----|------|---------|--------|
| T00 | bootstrap | none | done |
| T01 | android-scaffold | T00 | done (CI green) |
| T01c | github-actions | T01 | done |
| T01d | ci-green | T01,T01c,T03 | done |
| T01e | auto-release | T01d | done (build-55 + latest published) |
| T01b | design-system | T00 | done |
| T02 | accounting-rules | T00 | done (needs accountant review) |
| T03 | domain-core | T02, T01 | done (26 tests pass in scratch JVM project) |
| T04 | room-persistence | T03, T05 | done (CI green, 11 Robolectric tests) |
| T05 | persian-utils | T01 | done (105 tests) |
| T06 | backend-llm-proxy | T02, T03 | done (109 tests, mock eval 58/58; live LLM unverified until T13) |
| T06b | openai-compat | T06 | done (175 backend tests, CI green) |
| T07 | nl-pipeline | T03, T04, T05, T06 | done (CI green run 62; 173 local tests) |
| T08a | entry-viewmodel | T04, T07 | done (CI green run 71) |
| T08 | entry-ui | T01b, T08a | done (CI green run 41191124546; build #86 released) |
| T09 | voice-input | T08 | done (CI green run 41192294108; build #88+; manual device test pending by user) |
| T09b | voice-fallback-fix | T09, T12b | done (CI green run 41213284854 on 08fceec; audit 6/6 MET; manual device test pending by user) |
| T09c | cloud-stt | T09b, T06b, T12b | todo (queued after T09b) |
| T10 | ledger-ui | T04, T08 | done (fix rounds 1+2; final CI run 41194728898 success on 0f959e9; audit 20 MET, 3 PARTIAL, 0 NOT MET; see handouts/T10-ledger-ui.md) |
| T10b | manual-journal-editor | T08, T10 | done (CI green run 102, build #102; D1+D3 carried to T12) |
| T11 | reports | T10 | done (CI green run 109, build #109; PDF render unverified -> T13; unit setting -> T12) |
| T12 | settings-onboarding | T08, T10b, T11 | done (CI green run 115, build #115; follow-through in T12b) |
| T12b | settings-follow-through | T12 | done (CI green run 41203285636, build #118; audit items 1-5 MET, 2 code-only sub-points) |
| T13a | emulator-qa | T12b | done (instrumented run 41208267404 green, 13 QA files pulled; PDF Persian shaping needs human look) |
| T13 | qa | T12b, T13a | done (CI green run 41210267830 on de7c888; live LLM eval not run, no key; see handouts/T13-qa.md) |
| T14 | release-prep | T13 | todo |

## Run order
T00 -> T01 -> T01c -> T01b -> (T01d after T03) -> T02 -> T03 -> T04 -> T05 -> T06 -> T07 -> T08a -> T06b -> T08 -> T09 -> T10 -> T10b -> T11 -> T12 -> T12b -> T13a -> T13 -> T09b -> T09c -> T14

Files: `tasks/Txx-slug.md` (from `tasks/_TEMPLATE.md`), `handouts/Txx-slug.md` (from `handouts/_TEMPLATE.md`).
