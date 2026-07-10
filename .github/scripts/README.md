# Android CI helpers

`run-gradle-ci.sh` executes the Android Gradle build in a dedicated process group while preserving live logs.

The wrapper enforces three safeguards:

- a bounded build deadline (`GRADLE_CI_TIMEOUT_SECONDS`);
- immediate termination for known Kotlin/R8 metadata rewrite failures and runner resource failures;
- a maximum retained/output log size (`GRADLE_CI_MAX_LOG_BYTES`) to prevent warning floods from consuming an entire job.

The Android workflow invokes only `assembleApkBuilderTemplateDistribution`. That aggregate task already builds the INRT Runtime Kit, the app release APKs, and the APK Builder template plugin, so a separate `packageInrtRelease` invocation would duplicate expensive compilation work.
