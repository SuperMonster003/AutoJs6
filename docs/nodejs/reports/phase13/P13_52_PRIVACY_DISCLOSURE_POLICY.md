# P13-52 Privacy Disclosure Policy

P13-52 records privacy and disclosure policy as partial. The current landing
adds `NodePrivacyDisclosurePolicy`, exposes frozen diagnostics through
`autojs6:profile.privacyDisclosurePolicy`, includes the same policy in Node
Doctor, extends TypeScript declarations, and adds a host gate for the privacy
and disclosure surface.

## Covered

- `NodePrivacyDisclosurePolicy` defines
  `autojs6-node-privacy-disclosure-policy-v1`.
- Sensitive surfaces now have metadata for prompt channel, disclosure channel,
  Android permissions/settings, packaged behavior, and promotion blocker.
- `raw_network` is listed separately from controlled `network`, so P13-50
  corpus, P13-51 rollback, and P13-52 disclosure ids align for raw Node network
  builtin promotion review.
- The policy covers screenshot, recording, overlay, accessibility, network,
  raw network, filesystem relaxation, Java interop, root/Shizuku shell,
  `child_process`, native addon, inspector, and WASI.
- Runtime diagnostics expose
  `require("autojs6:profile").privacyDisclosurePolicy` as a frozen object.
- Node Doctor JSON/text reports include a Privacy Disclosure Policy section.
- TypeScript declarations and the smoke project cover
  `PrivacyDisclosurePolicy`, surfaces, channels, packaged policy, and Doctor
  privacy fields.
- `verifyNodePhase13PrivacyDisclosurePolicy` generates
  `phase13-privacy-disclosure-policy.json` and `.md`.

## Remaining Work

- Settings UI prompt copy, localization, and explicit user review flow remain
  unpromoted.
- Foreground service notification ownership and channel policy remain future
  work for promoted screenshot, recording, overlay, root shell, and inspector
  surfaces.
- Packaged disclosure manifest authoring/review UI and installer/runtime
  enforcement are not implemented yet.
- P13-52 does not grant Pro/Desktop/Debug authority; effective runtime
  authority remains `safe_default`.

## Evidence

- Privacy policy:
  `docs/nodejs/security/PRIVACY_DISCLOSURE_POLICY.md`
- Kotlin policy:
  `app/src/main/java/org/autojs/autojs/engine/NodePrivacyDisclosurePolicy.kt`
- Runtime diagnostics:
  `app/src/main/cpp/node_bridge_sources.cpp`
- Node Doctor integration:
  `NodeDoctorReportGenerator` and
  `NodeDoctorReportGeneratorInstrumentationTest`
- Focused instrumentation:
  `NodePrivacyDisclosurePolicyPhase13DecisionInstrumentationTest`
- Type declarations:
  `docs/nodejs/types/autojs6-node/autojs_global.d.ts`
- Host verifier:
  `tools/nodejs/phase13/verify-privacy-disclosure-policy.js`
- Gradle gate:
  `:app:verifyNodePhase13PrivacyDisclosurePolicy`

## Verification

```powershell
node tools\nodejs\phase13\verify-privacy-disclosure-policy.js --repo-root . --report-dir build\reports\nodejs
.\gradlew.bat --console=plain :app:verifyNodePhase13PrivacyDisclosurePolicy
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodePrivacyDisclosurePolicyPhase13DecisionInstrumentationTest"
```
