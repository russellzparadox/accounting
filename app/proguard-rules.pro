# R8 rules for the release (and the minified QA) build type. Enabled in app/build.gradle.kts.
#
# Policy: keep only what a rule is needed for. Libraries ship their own consumer rules inside their
# AAR/JAR, so none of these are repeated here:
#   - kotlinx.serialization: kotlinx-serialization-core ships META-INF/com.android.tools/r8/kotlinx-serialization-r8.pro
#     (checked in the cached 1.9.0 jar). Serializers are generated at compile time, not looked up by reflection.
#   - OkHttp: ships META-INF/proguard/okhttp3.pro (checked in the cached 4.12.0 jar).
#   - Hilt, Room (except the rule below), Compose, lifecycle, coroutines: consumer rules in their AARs
#     (not checked locally; Google Maven is blocked in the authoring session, so this is verified by the CI release build only).
#   - Enum valueOf(String) (Side, AccountType, ... read back from Room text columns): the AGP default rules
#     keep enum values()/valueOf(); the names are constructor literals, so field renaming does not change them.

# Room creates the generated database class by name: `Room.databaseBuilder(...)` calls
# Class.forName("...db.AppDatabase_Impl") and then its no-arg constructor. R8 cannot see that reference,
# so it would remove or rename the class and the app would crash on first database access.
-keep class com.example.accounting.core.data.db.AppDatabase_Impl {
    <init>();
}

# Readable crash stacks after retracing with the mapping file uploaded by CI (release-mapping artifact):
# keep source file names and line numbers, but hide the real file name in the APK.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
