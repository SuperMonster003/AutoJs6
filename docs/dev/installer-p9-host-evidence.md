# Installer P9 host and contract evidence

Date: 2026-10-02. Host base: `77b5a3b0c5`, 6.8.0 / 5307, initially clean.

The V3 contract adds five nullable installation options, one advanced-option capability,
the optimizing stage and optional observation vocabulary. The first eleven AIDL transactions
remain unchanged. The host's same-Binder peer checks V3 and the capability even for explicit
false/none values; omitted options do not introduce keys into legacy requests. Script validation
rejects null, coerced types, unknown enums and policy-bypass keys. Optional owner and dexopt
observations already pass through the existing raw-result preservation, including null.

Validation on the implementation sources:

- `build/p9-installer-api-verify.log`: 9 installer-api JVM tests, 0 failures/errors/skips, release
  AAR assembly. The first attempted `testReleaseUnitTest` task does not exist in this build;
  it failed during task selection, then the supported Debug unit-test task passed.
- `build/p9-host-parser-verify.log`: 96 installer-related host JVM tests across 14 classes,
  no failures/errors/skips. App Debug main and unit-test sources compiled. The shared parser's
  separate 54-test suite and release assembly also passed in this invocation; parser changes
  and their provenance are committed separately.
- The release installer API AAR is 32,967 bytes, SHA-256
  `39971b5d9f29901075259e50da73367b1122c5f9453b4a9528d664c8cbfe575c`.
  No unknown transaction or actual installation was used to infer peer support.

Host build 5308 carries the script extension. This evidence does not claim plugin execution,
post-install outcome checks or a new Android device matrix; those belong to the plugin's
backend acceptance. Four associated public-documentation/declaration repositories are held
until those semantics are validated. All changes and commits remain local.
