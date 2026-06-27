# P13-51 Profile Rollback Playbook

P13-51 records per-profile rollback playbook as partial. The current landing
adds default-off Gradle switches for Pro/Desktop/Debug profile promotion and
the relaxed-capability bundle, exposes a shared rollback policy through Kotlin,
Node Doctor, and `autojs6:profile.profileRollbackPolicy`, and ties the evidence
to a host verifier.

## Covered

- `NodeProfileRollbackPolicy` defines
  `autojs6-node-profile-rollback-policy-v1`.
- Gradle defaults keep `autojs.nodejs.profile.pro.enabled`,
  `autojs.nodejs.profile.desktop.enabled`,
  `autojs.nodejs.profile.debugUnsafeLab.enabled`, and
  `autojs.nodejs.relaxedCapabilities.enabled` false.
- Release build checks reject enabling these promotion switches before later
  promotion/privacy/package gates are closed.
- `require("autojs6:profile").profileRollbackPolicy` exposes frozen
  per-profile switches, capability rollback actions, crash/stress/security
  triggers, packaged downgrade strategy, and Safe fallback metadata.
- `raw_network` is now exposed as the P13-50 corpus-facing rollback capability
  id for the `autojs.nodejs.rawNetworkModules.experimental` kill switch, while
  `raw_node_network_modules` remains available as the implementation-specific
  alias.
- Node Doctor JSON/text reports include a Profile Rollback Policy section.
- TypeScript declarations and the smoke project cover
  `ProfileRollbackPolicy`, profile/capability rollback states, triggers,
  release policy, and packaged downgrade fields.
- `verifyNodePhase13ProfileRollbackPlaybook` generates
  `phase13-profile-rollback-playbook.json` and `.md`.

## Remaining Work

- Runtime profile promotion and user-selectable profile switching remain
  unpromoted.
- Settings UI, packaged builder UI, automatic crash circuit breaker, remote
  packaged kill switch, and privacy disclosure prompts remain future work.
- The rollback playbook does not grant Pro/Desktop/Debug authority; effective
  runtime authority remains `safe_default`.

## Evidence

- Rollback playbook:
  `docs/nodejs/security/PROFILE_ROLLBACK_PLAYBOOK.md`
- Kotlin policy:
  `app/src/main/java/org/autojs/autojs/engine/NodeProfileRollbackPolicy.kt`
- Runtime diagnostics:
  `app/src/main/cpp/node_bridge_sources.cpp`
- Node Doctor integration:
  `NodeDoctorReportGenerator` and
  `NodeDoctorReportGeneratorInstrumentationTest`
- Focused instrumentation:
  `NodeProfileRollbackPlaybookPhase13DecisionInstrumentationTest`
- Type declarations:
  `docs/nodejs/types/autojs6-node/autojs_global.d.ts`
- Host verifier:
  `tools/nodejs/phase13/verify-profile-rollback-playbook.js`
- Gradle gate:
  `:app:verifyNodePhase13ProfileRollbackPlaybook`

## Verification

```powershell
node tools\nodejs\phase13\verify-profile-rollback-playbook.js --repo-root . --report-dir build\reports\nodejs
.\gradlew.bat --console=plain :app:verifyNodePhase13ProfileRollbackPlaybook
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeProfileRollbackPlaybookPhase13DecisionInstrumentationTest"
```
