# R8 rules for the qa build type only (minified, debuggable, runs androidTest). Not used by release.

# AGP does not package into the androidTest APK the classes that the tested app already provides. The qa app's
# R8 removed kotlin.LazyKt (app code never calls it), so the instrumentation runner (androidx.test) found neither
# copy at runtime: "NoClassDefFoundError: kotlin.LazyKt" in TestDirCalculator (runs 41220171631, 41220818087).
# Keep the kotlin stdlib in the app so the runner can use it. Affects the qa variant only.
-keep class kotlin.** { *; }
