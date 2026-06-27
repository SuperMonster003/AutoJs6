# P13-49 Profile Selection Diagnostics

P13-49 records profile selection UX and diagnostics as partial. The current
landing makes Safe/Pro/Desktop/Debug profile metadata visible to scripts and
Node Doctor, reports declared high-risk capabilities, and upgrades missing
capability errors with profile, capability, and Android permission hints.

## Covered

- `NodeProfileSelectionDiagnostics` defines
  `autojs6-node-profile-selection-diagnostics-v1`.
- The effective runtime profile remains `safe_default`; requested
  `pro_compat_opt_in`, `desktop_compat_opt_in`, and `debug_unsafe_lab`
  metadata is reported as diagnostic-only.
- `require("autojs6:profile").profileSelection` exposes requested profiles,
  declared Node permissions, Android permissions, high-risk capability rows,
  blocked profile promotions, and failure hints.
- `ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED` errors now include
  `requiredProfiles`, `requiredNodePermissions`, `requiredAndroidPermissions`,
  and `profileSelectionHint`.
- Denied or undeclared capability failures remain explicit: the stable error
  surface carries required profile, Node permission, Android permission, and
  declaration example diagnostics without promoting the requested profile.
- Effective Pro/Desktop/Debug profile switching stays stable-denied for P13-49
  v1.1. Requested profile metadata remains diagnostic-only and the effective
  runtime profile remains `safe_default`.
- `profileSelectionHint.declarationExamples` now carries JSON-safe
  `nodePermissions`, `projectJson.node.permissions`, and
  `packageJson.autojs6.node.permissions` snippets for the missing capabilities.
- Node Doctor JSON/text reports include a Profile Selection section and can
  summarize project/package metadata from a project root.
- TypeScript declarations and the smoke project cover
  `ProfileSelectionDiagnostics` and
  `AutoJsBridgeCapabilityNotDeclaredError`.

## Remaining Work

- User-facing project settings and packaged builder controls are still future
  UX work.
- Rollback UI, disclosure copy, and per-profile capability enablement remain
  owned by P13-51/P13-52 and later promotion gates.

## Evidence

- Kotlin diagnostics:
  `app/src/main/java/org/autojs/autojs/engine/NodeProfileSelectionDiagnostics.kt`
- Runtime diagnostics and error hints:
  `app/src/main/cpp/node_bridge_sources.cpp`
- Node Doctor integration:
  `NodeDoctorReportGenerator` and
  `NodeDoctorReportGeneratorInstrumentationTest`
- Focused instrumentation:
  `NodeProfileSelectionDiagnosticsPhase13DecisionInstrumentationTest`
- Type declarations:
  `docs/nodejs/types/autojs6-node/autojs_global.d.ts`
- Host verifier:
  `tools/nodejs/phase13/verify-profile-selection-diagnostics.js`
- Gradle gate:
  `:app:verifyNodePhase13ProfileSelectionDiagnostics`

## Verification

```powershell
node tools\nodejs\phase13\verify-profile-selection-diagnostics.js --repo-root . --report-dir build\reports\nodejs
.\gradlew.bat --console=plain :app:verifyNodePhase13ProfileSelectionDiagnostics
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeProfileSelectionDiagnosticsPhase13DecisionInstrumentationTest"
```
