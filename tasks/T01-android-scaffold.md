# T01 — android-scaffold
Status: todo
Depends: T00
Read first: CLAUDE.md, handouts/T00-bootstrap.md
Skills to invoke: persian-writing:persian-writing

## Goal
- Gradle KTS multi-module Android project. Hilt, Compose M3, RTL, fa default locale, Vazirmatn font, theme.

## Deliverables (files)
- `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`
- Modules (each with `build.gradle.kts` + `src/main`): `app`, `core/domain`, `core/data`, `core/ui`, `core/persian`, `feature/entry`, `feature/ledger`, `feature/reports`, `feature/settings`
- `app/src/main/AndroidManifest.xml` (`supportsRtl="true"`), `app/src/main/java/.../App.kt` (`@HiltAndroidApp`), `app/src/main/java/.../MainActivity.kt` (`@AndroidEntryPoint`, placeholder fa screen)
- `core/ui/src/main/java/.../theme/{Color,Type,Theme}.kt`
- `core/ui/src/main/res/font/` Vazirmatn `.ttf` + OFL license file
- `app/src/main/res/values/strings.xml` (default locale fa)

## Acceptance criteria
- [ ] `settings.gradle.kts` includes exactly the 9 modules above.
- [ ] Hilt wired: `@HiltAndroidApp` Application, `@AndroidEntryPoint` Activity.
- [ ] M3 theme in `core/ui` applied to placeholder screen.
- [ ] Layout direction RTL; `values/strings.xml` is fa.
- [ ] Vazirmatn registered as default typeface.
- [ ] No domain, DB, or business logic in any module.
- [ ] `./gradlew assembleDebug` exits 0.
- [ ] `./gradlew test` exits 0 (zero tests is OK).
- [ ] If Vazirmatn cannot be fetched, note in handout Open issues. Do not substitute silently.

## Out of scope
- Domain logic, Room, real screens, CI, signing, proguard.

## Verify (commands)
- `./gradlew assembleDebug`
- `./gradlew test`
- `grep -c "include" settings.gradle.kts` (expect 9 module includes)
