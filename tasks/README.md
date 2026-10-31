# Tasks index
Run sequentially, one subagent at a time. Handouts are the only inter-task channel.

| ID | Name | Depends | Status |
|----|------|---------|--------|
| T00 | bootstrap | none | done |
| T01 | android-scaffold | T00 | done (CI green) |
| T01c | github-actions | T01 | done |
| T01d | ci-green | T01,T01c,T03 | done |
| T01e | auto-release | T01d | in_progress |
| T01b | design-system | T00 | done |
| T02 | accounting-rules | T00 | done (needs accountant review) |
| T03 | domain-core | T02, T01 | done (26 tests pass in scratch JVM project) |
| T04 | room-persistence | T03, T05 | done (CI green, 11 Robolectric tests) |
| T05 | persian-utils | T01 | done (105 tests) |
| T06 | backend-llm-proxy | T02, T03 | done (109 tests, mock eval 58/58; live LLM unverified until T13) |
| T07 | nl-pipeline | T03, T05, T06 | todo |
| T08 | entry-ui | T01b, T04, T07 | todo |
| T09 | voice-input | T08 | todo |
| T10 | ledger-ui | T04, T08 | todo |
| T11 | reports | T10 | todo |
| T12 | settings-onboarding | T08 | todo |
| T13 | qa | T07, T08, T09, T10, T11, T12 | todo |
| T14 | release-prep | T13 | todo |

## Run order
T00 -> T01 -> T01c -> T01b -> (T01d after T03) -> T02 -> T03 -> T04 -> T05 -> T06 -> T07 -> T08 -> T09 -> T10 -> T11 -> T12 -> T13 -> T14

Files: `tasks/Txx-slug.md` (from `tasks/_TEMPLATE.md`), `handouts/Txx-slug.md` (from `handouts/_TEMPLATE.md`).
