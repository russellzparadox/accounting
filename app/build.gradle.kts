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
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
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
}
