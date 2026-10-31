plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.accounting.core.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    jvmToolchain(21)
}

ksp {
    // Schema JSON is exported on every build (exportSchema = true on AppDatabase).
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Debug and release KSP runs share the one schema folder above. With org.gradle.parallel=true and lint
// analysing the release variant, both can run at once and one reads the other's half-written schema JSON
// (CI 41194425401: kspDebugKotlin "Unexpected JSON token ... $.database.entities[3]"). Run them in sequence.
tasks.matching { it.name == "kspReleaseKotlin" }.configureEach {
    dependsOn("kspDebugKotlin")
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:persian"))

    // api: AppDatabase extends RoomDatabase, so consumers (app DI module, feature:entry tests) need it on their compile classpath.
    api(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.coroutines.core)
    ksp(libs.androidx.room.compiler)

    // Robolectric + in-memory Room: runs under ./gradlew test (JVM, no emulator).
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.okhttp.mockwebserver)
}
