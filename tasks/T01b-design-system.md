# T01b — design-system
Status: todo
Depends: T00
Read first: CLAUDE.md, handouts/T00-bootstrap.md
Skills to invoke: impeccable, ui-ux-pro-max (only if installed; else design:design-system, design:ux-copy, design:accessibility-review, persian-writing:persian-writing)

## Goal
- Write `docs/DESIGN.md`: tokens, type, spacing, RTL rules, components, states, a11y, Persian UX copy list. Docs only, no code.

## Deliverables (files)
- `docs/DESIGN.md`

## Acceptance criteria
- [ ] `docs/DESIGN.md` has sections: color tokens (light + dark), Vazirmatn type scale, spacing, RTL rules, components, states, a11y, Persian UX copy list.
- [ ] Components listed: chat input bar, mic button, confirmation card, journal row, amount text.
- [ ] States listed per component: empty, error, loading.
- [ ] Every text color pair gives light and dark hex plus contrast ratio >= 4.5:1 (WCAG AA).
- [ ] Touch targets stated as >= 48dp.
- [ ] Amount text rule: Persian digits in UI, LTR isolation for numbers inside RTL text.
- [ ] Copy list is all Persian, ZWNJ used correctly.
- [ ] Only `docs/DESIGN.md` changed.

## Out of scope
- Compose code, icon/asset files, Figma, implementation of components.

## Verify (commands)
- `test -s docs/DESIGN.md`
- `grep -c "48dp" docs/DESIGN.md` (expect >= 1)
- `grep -ci "contrast" docs/DESIGN.md` (expect >= 1)
