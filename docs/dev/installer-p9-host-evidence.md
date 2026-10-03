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
  separate 62-test suite and release assembly also passed in this invocation; parser changes
  and their provenance are committed separately.
  The initially reported 54 count was read from existing XML reports while parallel test work
  was still running; the complete source/test inventory is 62, and the final build passed.
- The release installer API AAR is 32,967 bytes, SHA-256
  `39971b5d9f29901075259e50da73367b1122c5f9453b4a9528d664c8cbfe575c`.
  No unknown transaction or actual installation was used to infer peer support.

Host build 5308 carries the script extension. This evidence does not claim plugin execution,
post-install outcome checks or a new Android device matrix; those belong to the plugin's
backend acceptance. Four associated public-documentation/declaration repositories are
synchronized separately after semantic review; their checks do not replace device acceptance.
All changes and commits remain local.

## Fixed host test APK

After the V3 host commit and the shared-parser commit, host build 5309 App Debug and androidTest
assembly passed (`build/p9-host-debug5309.log`). Artifacts were copied immediately under
`build/p9-fixed-host5309/`, with before/copy/after source hashes agreeing:

- `host5309-universal.apk`: 42,236,039 bytes, SHA-256
  `a2737d3ead27e64b2629336c71dab346085650ec2ed683137b1fb0819588f6ff`.
- `host5309-androidTest.apk`: 3,433,353 bytes, SHA-256
  `0275ee5a927ad2c9db2a5187f1603a69d9a388f9ebe8aff3b915ba96f155cff0`.

The workspace was at `3876677bae`, but a parallel task introduced an untracked accessibility
inspector package during this build, and its package is present in the DEX. This is an exactly
identified integrated test APK, not a claim of a clean-commit-only build. That other task's
sources were neither modified nor committed as part of the installer changes. Its later
commit does not retroactively change this provenance boundary. Both release AARs were copied
alongside the APKs with their already-recorded hashes. Device runs use only the dedicated AVD;
this build step did not replace the host on a user device.
