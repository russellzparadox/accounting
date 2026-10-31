plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// CI passes -PbuildNumber=<github.run_number>. Local builds keep versionCode 1 / 0.1.0.
val ciBuildNumber: Int? = findProperty("buildNumber")?.toString()?.toIntOrNull()

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

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

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
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.androidx.room.runtime)
    androidTestImplementation(libs.kotlinx.coroutines.core)
}
