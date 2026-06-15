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

## Evidence

- Initial RC aggregate failure: `docs/nodejs/reports/phase4/oneplus-rc-gate/`
- Current packaged smoke export and host verification: `docs/nodejs/reports/phase4/oneplus-packaged-smoke-current-export/`
- Dynamic import compatibility rerun: `docs/nodejs/reports/phase4/oneplus-packaged-dynamic-compat-rerun/`
- Lifecycle stress: `docs/nodejs/reports/phase4/oneplus-lifecycle-stress/`
- OnePlus whitelist/focused smoke recovery: `docs/nodejs/reports/phase4/oneplus-device-whitelist-smoke/`
- Reduced memory stress pass: `docs/nodejs/reports/phase4/oneplus-memory-leak-stress-reduced-whitelisted/`
- Sony device transition: `docs/nodejs/reports/phase4/sony-device-transition/`

## Still Required Before Publication

- Full default `NodeEmbeddedMemoryLeakStressInstrumentationTest` on Sony with explicit serial `QV710AF65F`.
- Full `:app:verifyEmbeddedNodeReleaseCandidate "-Pautojs.embeddedNode.adbSerial=QV710AF65F"` rerun after packaged smoke artifacts are present.
- Commit-scoped review of generated `version.properties` churn before release tagging.
