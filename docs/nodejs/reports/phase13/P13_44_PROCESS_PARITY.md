# P13-44 Process/env/argv Parity

P13-44 records process/env/argv parity as partial. The current landing adds a
diagnostic `processParityProfile` to `autojs6:profile`, focused device
instrumentation, a host verifier, a Gradle gate, TypeScript declarations, and
surface-manifest evidence for the Safe process subset.

## Covered

- `process` and `node:process` resolve to the same controlled module.
- `process.argv`, `process.argv0`, `process.title`, `process.execArgv`, entry
  metadata, and project-runner argv propagation have focused evidence.
- `process.env` is a script-local mutable string map seeded from controlled
  request values plus safe defaults.
- Sensitive or host-control variables such as `NODE_OPTIONS`,
  `NODE_INSPECT_RESUME_ON_START`, `NODE_COMPILE_CACHE`, and AutoJs6 compile
  cache controls are filtered from script-visible env.
- `process.cwd()` and `process.chdir()` are scoped to the script working
  directory; relative in-scope movement is allowed and absolute or escape paths
  remain denied.
- `process.exitCode` is writable. `process.exit(code)` maps to an AutoJs6
  embedded-script lifecycle result without calling native process exit;
  `process.abort()`, `process.kill()`, and `process.reallyExit()` remain
  denied.
- `process.version`, `process.versions`, `process.release`, `platform`, `arch`,
  pid metadata, timing, CPU usage, memory usage, `resourceUsage()`,
  `permission`, `report.getReport()`, stdio shape, and stable denial of private
  process/native entry points are covered by instrumentation.

## Remaining Work

- Full desktop process parity is not claimed.
- Raw signal delivery and Android process signal differences need a complete
  policy and device matrix.
- Native/raw process termination remains denied; the supported
  `process.exit(code)` path is only the embedded-script lifecycle mapping.
- Raw inherited environment policy, packaged lifecycle evidence, raw process
  handles, native bindings, `process.dlopen`, `process.binding`, and
  `report.writeReport()` remain future Desktop-profile work.

## Evidence

- Runtime diagnostics: `app/src/main/cpp/node_bridge_sources.cpp`
  `processParityProfile`
- Focused device test:
  `NodeProcessParityPhase13DecisionInstrumentationTest`
- Existing behavior evidence:
  `NodeProcessSweepInstrumentationTest`, `EmbeddedScriptMvpInstrumentationTest`,
  inspector env filtering tests, compile-cache env filtering tests, and the
  modern-core process permission differential fixture
- Host verifier: `tools/nodejs/phase13/verify-process-parity-decision.js`
- Gradle gate: `:app:verifyNodePhase13ProcessParityDecision`

## Verification

```powershell
node tools\nodejs\phase13\verify-process-parity-decision.js --repo-root . --report-dir build\reports\nodejs
.\gradlew.bat --console=plain :app:verifyNodePhase13ProcessParityDecision
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeProcessParityPhase13DecisionInstrumentationTest"
```
