# T00 — Bootstrap task files
Status: done
Depends: none (CLAUDE.md, templates, docs skeletons already exist, made by main agent)
Read first: CLAUDE.md, tasks/_TEMPLATE.md, handouts/_TEMPLATE.md, docs/DECISIONS.md
Skills to invoke: none

## Goal
Create one task file per row below in `tasks/`, using `tasks/_TEMPLATE.md`. Each file: filled Goal, Deliverables (concrete paths), Acceptance criteria (checkboxes, testable), Out of scope, Verify commands, Depends, Skills. Terse bullets. Status: todo. Also create `tasks/README.md` = index table (id, name, depends, status) + the run order.

## Task table (filenames `Txx-slug.md`)
| ID | Slug | Task | Depends | Skills |
|----|------|------|---------|--------|
| T01 | android-scaffold | Gradle KTS project, modules (app, core:domain, core:data, core:ui, core:persian, feature:entry, feature:ledger, feature:reports, feature:settings), Hilt, Compose M3, RTL/fa default locale, Vazirmatn font in res, theme, `./gradlew assembleDebug` + `test` pass | T00 | persian-writing |
| T01b | design-system | `docs/DESIGN.md`: color tokens (light/dark), Vazirmatn type scale, spacing, RTL rules, components (chat input bar, mic button, confirmation card, journal row, amount text), empty/error/loading states, a11y (48dp, contrast), Persian UX copy list | T00 | impeccable/ui-ux-pro-max if exist else design:design-system, design:ux-copy, design:accessibility-review, persian-writing |
| T02 | accounting-rules | Research Iranian accounting (web). `docs/ACCOUNTING_RULES.md`, `core/domain/src/main/resources/chart_of_accounts.json` (کدینگ: group/کل/معین), posting templates spec (cash/credit purchase, cash/credit sale, expense paid/payable, receipt/payment with person, bank transfer, owner capital/withdrawal, purchase/sale return, reversal), fiscal year rules, doc numbering, toman/rial | T00 | persian-writing |
| T03 | domain-core | Pure Kotlin: Money(rial), Account, JournalEntry/Line, TransactionIntent model, PostingEngine from templates in T02, balance validation, reversal. JVM unit tests incl. fuzz (Dr==Cr) | T02, T01 | none |
| T04 | room-persistence | Room entities/DAOs/repos, seed loader from chart json, fiscal year + sequential doc numbers, immutability, DAO tests | T03 | none |
| T05 | persian-utils | Jalali date conv/format, Persian digit normalize, number-word parser ("پانصد هزار تومان", "نیم میلیون", "۵۰۰٬۰۰۰", "دو و نیم میلیون"), toman/rial formatting, unit tests (>=60 cases) | T01 | persian-writing |
| T06 | backend-llm-proxy | Node/TS proxy `POST /v1/parse` -> Claude Haiku tool-use with JSON schema (TransactionIntent / needs_clarification), bearer auth, rate limit, no logging of text by default; `docs/LLM_PROMPTS.md` + `docs/BACKEND.md`; eval harness with >=50 Persian sentences (mock-able without API key) | T02 | persian-writing |
| T07 | nl-pipeline | App NlParser client, normalize -> intent -> PostingEngine draft, local amount cross-check vs LLM amount, clarification flow, offline/error states, fakes for tests | T03, T05, T06 | none |
| T08 | entry-ui | Chat-style entry screen, editable confirmation card, commit/undo, per DESIGN.md | T01b, T04, T07 | design:* (see T01b), persian-writing |
| T09 | voice-input | `SpeechSource` interface, on-device fa-IR `SpeechRecognizer` impl, mic UI, permission flow, partial results, error states; cloud impl stub only; `docs/VOICE.md` | T08 | design:ux-copy, persian-writing |
| T10 | ledger-ui | Journal list, per-account ledger (معین), account picker, Jalali date filter/search | T04, T08 | design:* |
| T11 | reports | Trial balance, journal, ledger reports; PDF + CSV export (Persian shaping/RTL in PDF) | T10 | persian-writing, design:* |
| T12 | settings-onboarding | Settings (toman/rial, fiscal year start, backend URL/token), onboarding with LLM privacy disclosure, DB backup/restore | T08 | design:*, persian-writing |
| T13 | qa | Compose UI tests, instrumented tests, LLM eval run, invariant fuzz (1000 entries trial balance balanced), accountant-review checklist | T07..T12 | none |
| T14 | release-prep | Proguard, signing doc, privacy note, README (fa+en), release build | T13 | persian-writing |

## Deliverables
- `tasks/T01-*.md` … `tasks/T14-*.md`, `tasks/T01b-design-system.md`, `tasks/README.md`
- `handouts/T00-bootstrap.md` (per handouts/_TEMPLATE.md)

## Acceptance criteria
- [ ] 15 task files exist, each follows template, deps match table
- [ ] Each task lists its dependency handouts under "Read first"
- [ ] Each task's acceptance criteria are checkable by command or file existence
- [ ] README index matches
- [ ] Handout written
- [ ] Do NOT touch other files

## Out of scope
Implementing any task. No code.

## Verify
`ls tasks | wc -l` -> 17 (template + T00 + 15 + README minus nothing: confirm in handout)
