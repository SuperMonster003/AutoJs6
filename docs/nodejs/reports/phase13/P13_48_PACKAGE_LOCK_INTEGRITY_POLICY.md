# P13-48 Package Lock And Integrity Policy

P13-48 records package lock and integrity policy as partial. The current
landing adds a unified policy descriptor for Android-side package manager,
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

## Remaining Work

- Signed plugin archive extraction, update channels, marketplace cache pruning,
  and plugin rollback UI are not implemented yet.
- Native addon promotion still requires signed packaged artifacts, ABI/hash
  allowlists, dependency scanning, crash isolation, and packaged positive plus
  hostile corpus evidence.
- Android runtime registry install and mutating package-manager JavaScript
  facades remain unpromoted.
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
