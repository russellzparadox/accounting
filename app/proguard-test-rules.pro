# R8 rules for the androidTest APK only (the app's rules are in proguard-rules.pro). Used when the
# instrumented tests run against the minified qa build type (AGP minifies the test APK too).

# androidx.test (and its transitive deps) reference compile-time-only annotations from
# com.google.errorprone.annotations (CanIgnoreReturnValue, MustBeClosed, ...). That artifact is not on
# the test runtime classpath and the annotations are never executed, so R8 only needs to be told the
# references are harmless. Without this, :app:minifyQaAndroidTestWithR8 fails with "Missing class".
-dontwarn com.google.errorprone.annotations.**

# The instrumentation runner (androidx.test) is started by the framework inside the app process and calls
# kotlin stdlib classes (e.g. kotlin.LazyKt) that are not referenced from test code. Run 41220171631 crashed
# with NoClassDefFoundError: kotlin.LazyKt in TestDirCalculator, because the test APK's R8 removed them.
# Keep the runner and the stdlib it uses in the test APK. This only affects the androidTest APK.
-keep class androidx.test.** { *; }
-keep class kotlin.** { *; }
