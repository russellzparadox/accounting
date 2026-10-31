# Handout T01 — android-scaffold
Status: partial

## Summary
- Gradle KTS multi-module project: 9 modules, Hilt on `app`, Compose M3 theme in `core:ui`, RTL forced, fa strings, Vazirmatn default font.
- Files written, no domain/DB/business logic. Wrapper 8.14.3 generated.
- NOT built: `assembleDebug` and `test` never ran. AGP is only on Google Maven, which is blocked in this sandbox. Android SDK also absent.

## Files created/changed
- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`
- `gradlew`, `gradlew.bat`, `gradle/wrapper/{gradle-wrapper.jar,gradle-wrapper.properties}`
- `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/example/accounting/{App,MainActivity}.kt`, `app/src/main/res/values/strings.xml`
- `core/{domain,data,persian,ui}/build.gradle.kts` + `src/main/AndroidManifest.xml` each
- `core/ui/src/main/java/com/example/accounting/core/ui/theme/{Color,Type,Theme}.kt`
- `core/ui/src/main/res/font/vazirmatn_{regular,medium,bold}.ttf`
- `core/ui/src/main/assets/licenses/OFL-Vazirmatn.txt`
- `feature/{entry,ledger,reports,settings}/build.gradle.kts` + `src/main/AndroidManifest.xml` each
- `tasks/T01-android-scaffold.md` (Status only)

## Public interfaces / contracts
- Namespaces: `com.example.accounting` (app), `com.example.accounting.core.{domain,data,ui,persian}`, `com.example.accounting.feature.{entry,ledger,reports,settings}`
- `@HiltAndroidApp class App : Application` (`com.example.accounting.App`)
- `@AndroidEntryPoint class MainActivity : ComponentActivity` (`com.example.accounting.MainActivity`)
- `fun AccountingTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)` in `com.example.accounting.core.ui.theme`; forces `LayoutDirection.Rtl`
- `val VazirmatnFamily: FontFamily`, `val AppTypography: Typography`, `val LightColors`, `val DarkColors`
- String keys: `app_name`, `home_placeholder_title`, `home_placeholder_body` (default `values/`, fa)
- Version catalog aliases: `libs.plugins.{android.application,android.library,kotlin.android,kotlin.compose,ksp,hilt}`; deps `libs.androidx.{core.ktx,activity.compose,compose.bom,compose.ui,compose.ui.graphics,compose.material3}`, `libs.hilt.{android,compiler}`
- Module graph: `app` -> all 8 modules; `core:data` -> `core:domain`; `feature:*` -> `core:domain` + `core:ui`

## Decisions made (+why)
- Gradle 8.14.3 (latest 8.x). Current Gradle is 9.8.2 but AGP 8.x does not support Gradle 9.x. Upgrade together with AGP later.
- AGP 8.13.0, Kotlin 2.2.20, KSP 2.2.20-2.0.4, Hilt 2.57.1, Compose BOM 2025.10.00, activity-compose 1.10.1, core-ktx 1.16.0. Google Maven and Maven Central lookups were blocked or rate-limited, so these are from memory and NOT verified. T01c must confirm.
- Hilt processing via KSP, not kapt.
- `core:domain`, `core:data`, `core:persian` are Android libraries, not pure JVM, for one uniform module type. Empty `src/main` contains only a manifest.
- `kotlin { jvmToolchain(21) }` in every module. JDK 21 is local. Avoids JVM target mismatch and toolchain download.
- `minSdk 26`, `compileSdk`/`targetSdk 36`, `applicationId`/namespace `com.example.accounting`. No package name was in T00 or docs, so this is a placeholder.
- Vazirmatn 33.0.3 from npm registry (`vazirmatn` package, OFL). GitHub release returned 403. Files are the static TTFs `Vazirmatn-{Regular,Medium,Bold}.ttf`, checksums match the npm tarball.
- OFL text lives in `core/ui/src/main/assets/licenses/`, not `res/font/`. AAPT2 rejects non-font files in `res/font`.
- Palette is neutral grey placeholder. Brand palette is T01b.
- Dark theme uses `isSystemInDarkTheme()`. Dynamic color is off.
- Only `app` applies Hilt plugin. Other modules add it when they need DI.
- Gradle wrapper generated in an empty scratch dir (`/tmp/wrapgen`) because `gradle wrapper` in the repo evaluates the root build script, which fails without AGP. The generated files were copied in unchanged.

## How verified (commands + result)
- `./gradlew assembleDebug` -> NOT RUN to success. Wrapper downloaded `gradle-8.14.3-bin.zip` from services.gradle.org OK. Then configuration failed: `Plugin [id: 'com.android.application', version: '8.13.0'] was not found ... could not resolve ... Google, MavenRepo, Gradle Central`. Cause: `dl.google.com` (Google Maven) returns 403 from the egress proxy (`maven.google.com` redirects there). Android SDK host `dl.google.com/android/repository` also 403, so `sdkmanager` is not possible. `ANDROID_HOME` is empty.
- `./gradlew test` -> NOT RUN. Same configuration failure expected.
- `grep -c "include" settings.gradle.kts` -> 9 (matches).
- ZWNJ count in `strings.xml` -> 5 (`خوش‌آمدید`, `صفحه‌ی`, `تراکنش‌ها`, `به‌زودی`, `می‌شود`). No em dash.
- Vazirmatn checksums match npm tarball (sha256 of the three TTFs).
- Not run: `fa_lint.py` / `persian_cleanup.py` on strings.xml (XML, not prose). Only ZWNJ and dash checks were done by hand.

## Open issues / risks
- BUILD UNVERIFIED. T01c (CI) must run `./gradlew assembleDebug` and `./gradlew test` on a machine with Google Maven and Android SDK. Expect possible version fixes in `libs.versions.toml`, especially KSP `2.2.20-2.0.4` and Hilt `2.57.1`.
- Compose BOM and AndroidX versions were not checked against Google Maven.
- Package name `com.example.accounting` is a placeholder. Owner must confirm before first release (T14).
- OFL file placed in `assets/licenses`, not `res/font` as the task literally lists. Reason above.
- `values/strings.xml` is the default resource; no `values-fa` qualifier. Locale is effectively fa. System locale switching is not overridden.
- Only static Vazirmatn weights Regular, Medium, Bold. Light/SemiBold/Black not bundled.
- Manifest has no theme attribute, so the activity uses the platform default window theme.

## Next task must know
- Start from `settings.gradle.kts` (9 modules). Add code only inside the module that owns it.
- Hilt is applied only in `app`. Add `alias(libs.plugins.hilt)` + `ksp(libs.hilt.compiler)` to any module that uses `@Inject`/`@HiltViewModel`.
- All UI goes through `AccountingTheme`. Fonts come from `VazirmatnFamily`. Strings are added to `app/src/main/res/values/strings.xml` (fa). T01b replaces `Color.kt` and may change `Type.kt`.
- `./gradlew` will not work locally without Google Maven and Android SDK access. Use CI (T01c).
