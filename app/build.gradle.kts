plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// CI passes -PbuildNumber=<github.run_number>. Local builds keep versionCode 1 / 0.1.0.
val ciBuildNumber: Int? = findProperty("buildNumber")?.toString()?.toIntOrNull()

// Release signing is optional and never stored in the repo. Each value is read from a gradle property
// (-P<name>=...) or an environment variable of the same name. Without RELEASE_STORE_FILE, assembleRelease
// produces an unsigned APK (CI does this; release.yml signs tags from repository secrets). docs/SIGNING.md.
fun releaseSigningValue(name: String): String? =
    (findProperty(name)?.toString() ?: System.getenv(name))?.takeIf { it.isNotBlank() }

val releaseStoreFile: String? = releaseSigningValue("RELEASE_STORE_FILE")

android {
    namespace = "com.example.accounting"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.accounting"
        minSdk = 26
        targetSdk = 36
        versionCode = ciBuildNumber ?: 1
        versionName = if (ciBuildNumber != null) "0.1.$ciBuildNumber" else "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseSigningValue("RELEASE_STORE_PASSWORD")
                keyAlias = releaseSigningValue("RELEASE_KEY_ALIAS")
                keyPassword = releaseSigningValue("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 (code shrinking, obfuscation) and resource shrinking. Rules: app/proguard-rules.pro.
            // The mapping file is written to app/build/outputs/mapping/release/ (uploaded by CI).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }

        // Minified (same R8 rules as release) but debuggable and debug-signed, so the emulator job can install it,
        // run the androidTest suite against it, and read files/qa via run-as (T13a). Not for distribution.
        create("qa") {
            initWith(getByName("release"))
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    // Instrumented tests (docs/CI.md, instrumented.yml) run against the minified qa variant, not debug.
    testBuildType = "qa"

    buildFeatures {
        compose = true
        // Version name and build number for the settings About section.
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":core:persian"))
    implementation(project(":feature:entry"))
    implementation(project(":feature:ledger"))
    implementation(project(":feature:reports"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    // Bottom bar icons (AppShell.kt).
    implementation(libs.androidx.compose.material.icons.core)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Instrumented QA (T13a, app/src/androidTest). Not part of the unit-test or release build.
    // Room and coroutines are implementation deps of core:data / core:domain, so the test source set needs them directly.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    // The test manifest must also be merged into the qa (minified, debuggable) variant that runs androidTest.
    add("qaImplementation", libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.androidx.room.runtime)
    androidTestImplementation(libs.kotlinx.coroutines.core)
}
