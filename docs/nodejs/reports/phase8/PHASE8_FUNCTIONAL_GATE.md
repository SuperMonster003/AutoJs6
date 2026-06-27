# Phase 8 Functional Gate

S8-48 adds `verifyNodePhase8Gate` as the aggregate functional gate for the
Phase 8 Node Profile v1.1 capability expansion work.

## Gate Composition

The focused instrumentation set covers:

- Node 24.17 upgrade-boundary security probes.
- Lightweight TypeScript runtime support.
- Module conformance v2 and ESM/CJS interop probes.
- Controlled HTTP, HTTPS, stream, Agent, undici, and HTTP npm corpus coverage.
- Advanced scoped fs: FileHandle, fd APIs, opendir, watchers, safe operations,
  and FileHandle/watcher fuzzer coverage.
- Compile cache, runtime prewarm, and performance matrix diagnostics.
- Inspector design/security gates.
- Hardened sandbox, capability broker v2, process-pool policy, and bridge RPC
  malformed-input fuzzer coverage.
- Expanded real npm corpus.

The host side runs:

- `generateNode2417NativeDiffReport`
- `generateNode2417SecurityProbeReport`
- `generateNode2417UpgradeGateReport`
- `verifyNodeProjectWizard`
- `verifyNodeExamples`
- `verifyAutoJs6NodeTypeDeclarations`
- `verifyNodeCompatibilityProfile`
- `verifyNodeDependencyManagerFixture`
- `verifyNodeDependencyBundlerFixtures`

`generateNodePhase8Summary` writes
`app/build/reports/nodejs/phase8-summary.json`. The JSON summary fails the task
when an expected instrumentation selector is missing, failed, or errored.

## Known Blocked Inputs

The summary records these inputs as deliberately blocked or design-gated:

- Node 24.17 runtime promotion still requires Android runtime artifacts.
- Startup snapshot runtime loading remains design-gated.
- Inspector DevTools and profiling remain debug design-gated.
- Worker partial promotion remains blocked by native availability.

## Command

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:verifyNodePhase8Gate "-Pautojs.nodejs.test.deviceProfile=sony"
```
