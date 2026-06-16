# Phase 4 Embedded Node RC Status

Date: 2026-06-16

Primary device: Sony XQ-AT72 `QV710AF65F`

Current package identity: `org.autojs.autojs6.nodejs`

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

## Evidence

- Initial RC aggregate failure: `docs/nodejs/reports/phase4/oneplus-rc-gate/`
- Current packaged smoke export and host verification: `docs/nodejs/reports/phase4/oneplus-packaged-smoke-current-export/`
- Dynamic import compatibility rerun: `docs/nodejs/reports/phase4/oneplus-packaged-dynamic-compat-rerun/`
- Lifecycle stress: `docs/nodejs/reports/phase4/oneplus-lifecycle-stress/`
- OnePlus whitelist/focused smoke recovery: `docs/nodejs/reports/phase4/oneplus-device-whitelist-smoke/`
- Reduced memory stress pass: `docs/nodejs/reports/phase4/oneplus-memory-leak-stress-reduced-whitelisted/`
- Sony device transition: `docs/nodejs/reports/phase4/sony-device-transition/`
- Sony GA candidate gate: `docs/nodejs/reports/phase4/sony-ga-candidate/`
- GA tag checklist: `docs/nodejs/releases/NODE_PROFILE_V1_GA_CHECKLIST.md`
- Release identity decision record: `docs/nodejs/releases/NODE_PROFILE_V1_RELEASE_IDENTITY.md`
- Post-GA maintenance plan: `docs/nodejs/MAINTENANCE_PLAN.md`

## Still Required Before Publication

- Restore or explicitly approve the temporary current-branch package identity before a public AutoJs6 GA tag.
- Select the final `VERSION_BUILD` / `BUILD_TIME` state and regenerate the release manifest from that clean state.
- Regenerate the final release manifest and nightly report after the final version/package identity state is committed.
- Replace `TBD` placeholders in GA release notes with the approved final commit and tag.
- Create the tag only after maintainer approval.
