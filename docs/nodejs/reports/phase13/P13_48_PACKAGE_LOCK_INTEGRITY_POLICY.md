# P13-48 Package Lock And Integrity Policy

P13-48 records the v1.1 shared package lock and integrity policy as ready while
plugin archive and marketplace distribution promotion remains future work. The
current landing adds a unified policy descriptor for Android-side package manager,
plugin packages, and native addon artifacts, exposes the same contract through
`packageIntegrityProfile`, and verifies Android local-store rollback, cache
cleanup, and corruption recovery behavior.

## Covered

- `NodePackageIntegrityPolicy` defines the shared policy schema
  `autojs6-node-package-lock-integrity-policy-v1`.
- The policy records `autojs6-lock.json`, Android package record schema,
  SHA-512 SSRI-style integrity, signature scheme
  `autojs6-package-signature-v1`, rollback, cache cleanup, and corruption
  recovery requirements.
- The policy and runtime profile expose machine-readable recovery actions for
  verify, stale transaction prune, reinstall from a verified source, transaction
  snapshot restore, and disabling unsigned plugin/native subjects.
- `packageIntegrityProfile` exposes the policy as read-only diagnostics through
  `require("autojs6:profile")`.
- The Android local package store advertises the same policy through
  `nativeValues()` and keeps install/update/remove/list/verify/prune inside the
  existing locked transactional store.
- Focused instrumentation verifies integrity mismatch rollback, stale
  transaction prune, corruption detection, and reinstall/update recovery.
- Plugin packages and native addons are included in the policy but remain
  fail-closed: unsigned plugin installs, native addon loading, raw registry
  install, lifecycle scripts, postinstall downloads, and unverified cache reuse
  are still denied.
- Signed native addon artifact verification is delegated to P13-39's future
  packaged native path. P13-48 records the shared signature/hash policy and
  keeps native addon loading fail-closed until that path exists.

## Closure

The v1.1 gate closes because `NodePackageIntegrityPolicy`,
`packageIntegrityProfile`, Android local-store transactional rollback, stale
transaction prune, corruption detection, recovery actions, shared plugin/native
integrity vocabulary, signed-native delegation to P13-39, fail-closed unsigned
plugin/native install, fail-closed raw registry install, docs, surface manifest,
TypeScript declarations, and Gradle gate are synchronized without promoting
signed plugin archives, marketplace installs, native addon loading, lifecycle
scripts, postinstall downloads, or unverified cache reuse.

## Future Work

- Signed plugin archive extraction, update channels, marketplace cache pruning,
  and plugin rollback UI are not implemented yet.
- Android runtime registry install and mutating package-manager JavaScript
  facades remain owned by P13-42 and unpromoted here.
- User-facing corruption recovery diagnostics and per-profile rollback UX remain
  future P13 security/UX work.

## Evidence

- Policy descriptor:
  `app/src/main/java/org/autojs/autojs/engine/NodePackageIntegrityPolicy.kt`
- Runtime diagnostics: `packageIntegrityProfile`
- Android store integration:
  `NodeAndroidPackageInstallManager.nativeValues()`
- Focused instrumentation:
  `NodePackageLockIntegrityPolicyPhase13DecisionInstrumentationTest`
- Existing store/plugin/native evidence:
  `NodeAndroidPackageInstallManagerInstrumentationTest`,
  `NodePackageManagerPhase13DecisionInstrumentationTest`,
  `NodePluginSystemInstrumentationTest`, and P13-39 native addon policy.
- Host verifier:
  `tools/nodejs/phase13/verify-package-lock-integrity-policy.js`
- Gradle gate:
  `:app:verifyNodePhase13PackageLockIntegrityPolicy`

## Verification

```powershell
node tools\nodejs\phase13\verify-package-lock-integrity-policy.js --repo-root . --report-dir build\reports\nodejs
.\gradlew.bat --console=plain :app:verifyNodePhase13PackageLockIntegrityPolicy
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodePackageLockIntegrityPolicyPhase13DecisionInstrumentationTest"
```
