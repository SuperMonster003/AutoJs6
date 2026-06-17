# Phase 4 Embedded Node RC Status

Date: 2026-06-17

Primary device: Sony XQ-AT72 `QV710AF65F`

Current default package identity: `org.autojs.autojs6`

Side-by-side test identity: `org.autojs.autojs6.nodejs`, selected only with `-Pautojs.nodejs.identity=nodejsTest`.

OnePlus OPD2413 `63751f63` is now excluded from unattended current-branch gates because APK installation requires manual confirmation on that device. Existing OnePlus directories remain historical Phase 4 evidence.

## Completed In This Pass

- Fixed `verifyEmbeddedNodePackagedDebug` so standalone packaged APK diagnostic tasks run only when a concrete APK property is provided.
- Regenerated all eight current packaged smoke APKs with split instrumentation methods.
- Verified packaged ESM, dynamic import, and timeout diagnostics on device.
- Passed `:app:embeddedNodePackagedSmokeHostVerify "-Pautojs.embeddedNode.adbSerial=63751f63"`.
- Passed `NodeEmbeddedLifecycleStressInstrumentationTest` on OnePlus.
- Classified OnePlus Android 15 background-start prevention and documented the required whitelist/foreground-start preparation.
- Passed reduced `NodeEmbeddedMemoryLeakStressInstrumentationTest` after OnePlus device preparation.
- Kept legacy packaged INRT JNI symbols backward-compatible and moved current app calls to V2 JNI symbols.
- Switched the current unattended GA gate target to Sony XQ-AT72 after the temporary Node package identity allowed side-by-side branch testing.
- Passed the Task 128 Sony GA candidate gate with `:app:verifyNodeReleaseGate "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.fuzzerBudget=normal"`.
- Passed full default `NodeEmbeddedMemoryLeakStressInstrumentationTest` on Sony as part of the GA candidate gate.
- Confirmed the post-gate Sony crash buffer was clean for foreground-service startup, fatal exception, request spool, and embedded service crash markers.
- Regenerated the Task 129 pre-tag artifact set from commit `7e047953`.
- Added `docs/nodejs/releases/NODE_PROFILE_V1_GA_CHECKLIST.md`.
- Added the Task 130 post-GA maintenance plan.
- Added Task 131 release identity wiring and manifest identity fields.
- Added Task 136 GA artifact bundle collection.
- Added Task 137 tag dry-run instructions.
- Added Task 138 post-tag verification script.
- Restored production AutoJs6 identity as the default build identity and kept `nodejsTest` as an explicit side-by-side
  identity profile.
- Passed the Sony production-identity final gate from commit `857e6e82` with
  `:app:verifyNodeReleaseGate "-Pautojs.nodejs.identity=production" "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.fuzzerBudget=normal"`.
- Finalized GA metadata with `VERSION_BUILD=3923`, release tag `node-profile-v1.0.0`, and identity status
  `production-identity-final-gate-passed`.
- Merged Node Profile v1 GA into `master` at commit `d017807f`.
- Moved local annotated tag `node-profile-v1.0.0` to the `master` merge commit and passed
  `tools/nodejs/verify-node-profile-tag.ps1 -Tag node-profile-v1.0.0`.
- Created follow-up development branch `feature/nodejs-runtime-v1.1` from the GA merge commit.

## Evidence

- Initial RC aggregate failure: `docs/nodejs/reports/phase4/oneplus-rc-gate/`
- Current packaged smoke export and host verification: `docs/nodejs/reports/phase4/oneplus-packaged-smoke-current-export/`
- Dynamic import compatibility rerun: `docs/nodejs/reports/phase4/oneplus-packaged-dynamic-compat-rerun/`
- Lifecycle stress: `docs/nodejs/reports/phase4/oneplus-lifecycle-stress/`
- OnePlus whitelist/focused smoke recovery: `docs/nodejs/reports/phase4/oneplus-device-whitelist-smoke/`
- Reduced memory stress pass: `docs/nodejs/reports/phase4/oneplus-memory-leak-stress-reduced-whitelisted/`
- Sony device transition: `docs/nodejs/reports/phase4/sony-device-transition/`
- Sony GA candidate gate: `docs/nodejs/reports/phase4/sony-ga-candidate/`
- Sony production-identity final gate: `docs/nodejs/reports/phase4/sony-production-identity-final-gate/`
- GA tag checklist: `docs/nodejs/releases/NODE_PROFILE_V1_GA_CHECKLIST.md`
- Release identity decision record: `docs/nodejs/releases/NODE_PROFILE_V1_RELEASE_IDENTITY.md`
- Post-GA maintenance plan: `docs/nodejs/MAINTENANCE_PLAN.md`
- GA artifact bundle task: `:app:collectNodeProfileGaArtifacts`
- Tag dry run: `docs/nodejs/releases/NODE_PROFILE_V1_TAG_DRY_RUN.md`
- Post-tag verification script: `tools/nodejs/verify-node-profile-tag.ps1`
- Final GA merge commit: `d017807f`
- Final local GA tag: `node-profile-v1.0.0`
- Follow-up branch: `feature/nodejs-runtime-v1.1`

## Publication Sign-off Status

- Final release manifest and nightly report were regenerated from the final GA state with tag
  `node-profile-v1.0.0` and identity status `production-identity-final-gate-passed`.
- Local tag creation and post-tag verification passed.
- Branch and tag push remain a maintainer-controlled publication step; no remote push is implied by this report.
