# P13-50 Relaxed Capability Security Corpus

P13-50 records relaxed-capability security corpus closeout. The current
landing adds a structured corpus for raw network, filesystem relaxation, Java
interop, `child_process`, native addon loading, inspector, and WASI, then ties
that corpus to focused Android regression tests and a host verifier.

## Covered

- `RELAXED_CAPABILITY_SECURITY_CORPUS.json` defines
  `autojs6-node-relaxed-capability-security-corpus-v1`.
- Every relaxed capability records positive tests, negative tests, abuse cases,
  CI evidence, runtime guards, promotion preconditions, and rollback posture.
- `NodeSecurityRegressionInstrumentationTest` now has focused P13-50 coverage
  for raw network denial, scoped filesystem positives/escapes, shell-bridge
  replacement for `child_process`, native addon denial, inspector denial, raw
  WASI denial, and Java interop allowlist/denylist behavior.
- Existing focused tests remain linked as companion evidence for controlled
  network facades, advanced scoped filesystem APIs, Java interop, child process,
  native addons, inspector, WASI, WASM, and package integrity.
- Runtime profile metadata remains fail-closed: raw sockets, broad filesystem
  roots, raw process/native handles, raw WASI, inspector, native addons, and
  release Java interop are not promoted by this task.
- The corpus now requires per-capability `runtimeGuards` and
  `promotionPreconditions` so future opt-in promotion has explicit fail-closed,
  rollback, privacy, packaged metadata, and connected-CI prerequisites.
- Every positive promotion path is linked to P13-51 rollback, P13-52 privacy
  disclosure, packaged metadata/review, and connected Android CI
  prerequisites.
- Safe-default regression coverage remains explicit for raw network, expanded
  filesystem roots, Java interop, `child_process`, native addon loading,
  inspector, and WASI.
- Release Java interop and raw authority remain unpromoted until prompts and
  packaged behavior exist.
- `verifyNodePhase13RelaxedCapabilitySecurityCorpus` generates
  `phase13-relaxed-capability-security-corpus.json` and `.md`.

## Boundary

- P13-50 is a security-corpus landing, not a runtime promotion.
- Real relaxed capability enablement still requires rollout controls, rollback
  UX, privacy disclosure, user prompts, packaged behavior, and final opt-in
  promotion evidence.
- P13-51/P13-52 must close rollback and privacy/disclosure requirements before
  any raw capability can be promoted beyond diagnostic or controlled surfaces.

## Evidence

- Corpus:
  `docs/nodejs/security/RELAXED_CAPABILITY_SECURITY_CORPUS.json`
- Focused instrumentation:
  `NodeSecurityRegressionInstrumentationTest`
- Companion device evidence:
  `NodeHttpDesktopParityInstrumentationTest`,
  `NodeNetTlsDnsStagedParityInstrumentationTest`,
  `NodeFilesystemAdvancedDecisionInstrumentationTest`,
  `NodeJavaInteropBridgeInstrumentationTest`,
  `NodeChildProcessPhase13DecisionInstrumentationTest`,
  `NodeNativeAddonPhase13DecisionInstrumentationTest`,
  `NodeInspectorPhase13DecisionInstrumentationTest`,
  `NodeWasiPhase13DecisionInstrumentationTest`, and
  `NodeWasmWasiInstrumentationTest`
- Runtime profile boundary:
  `app/src/main/cpp/node_bridge_sources.cpp`
- Host verifier:
  `tools/nodejs/phase13/verify-relaxed-capability-security-corpus.js`
- Gradle gate:
  `:app:verifyNodePhase13RelaxedCapabilitySecurityCorpus`

## Verification

```powershell
node tools\nodejs\phase13\verify-relaxed-capability-security-corpus.js --repo-root . --report-dir build\reports\nodejs
.\gradlew.bat --console=plain :app:verifyNodePhase13RelaxedCapabilitySecurityCorpus
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeSecurityRegressionInstrumentationTest#phase13RelaxedCapabilityCorpusCoversNetworkFsChildNativeInspectorAndWasi"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeSecurityRegressionInstrumentationTest#phase13JavaInteropRelaxedCapabilityCorpusKeepsAllowlistAndDenials"
```
