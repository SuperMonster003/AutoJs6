# Node Dual-runtime A/B Gate

Generated: 2026-06-19T15:15:38.867Z

## Decision

- Status: **blocked**
- Summary: Dual-runtime A/B gate is blocked until node24_current artifacts and identical suite evidence are supplied.
- Selector property: `autojs.nodejs.runtimeSlot`
- Allowed slots: `node24_5`, `node24_current`
- Selected slot: `node24_5`
- Build-only: true
- User project selectable: false
- Arbitrary native runtime selectable: false

## Slots

| Slot | Version | Selected | Status | Blockers |
| --- | --- | --- | --- | --- |
| `node24_5` | v24.5.0 | true | ready | 0 |
| `node24_current` | v24.17.0 | false | blocked | 7 |

## Identical Suite Matrix

| Suite | node24_5 | node24_current | Command |
| --- | --- | --- | --- |
| compatibilityCorpus | app/build/reports/nodejs/compatibility-profile.json | missing | `:app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeCompatCorpusInstrumentationTest"` |
| securityCorpus | build/reports/nodejs/v1.1-beta-summary.json | missing | `:app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeSecurityRegressionInstrumentationTest"` |
| packagedSmoke | app/build/reports/embedded-node/packaged-smoke-aggregate.txt | missing | `:app:verifyEmbeddedNodePackagedDebug` |
| memoryStress | build/reports/nodejs/node-profile-v1-ga/performance/embedded-node-memory-leak-stress-1781651031958.json | missing | `:app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedMemoryLeakStressInstrumentationTest"` |
| performanceBenchmark | build/reports/nodejs/node-profile-v1-ga/performance/embedded-node-performance-1781429181600.json | missing | `:app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedPerformanceInstrumentationTest"` |

## Blockers

- node24_current.artifacts.headers: candidate headers were not supplied
- node24_current.artifacts.jniLibs: candidate jniLibs were not supplied
- node24_current.suites.compatibilityCorpus: candidate compatibility corpus report was not supplied
- node24_current.suites.securityCorpus: candidate security corpus report was not supplied
- node24_current.suites.packagedSmoke: candidate packaged smoke report with packaged.node.version=v24.17.0 was not supplied
- node24_current.suites.memoryStress: candidate memory stress report was not supplied
- node24_current.suites.performanceBenchmark: candidate performance benchmark report was not supplied

## Re-run

```powershell
.\gradlew.bat --console=plain :app:generateNodeDualRuntimeABReport
.\gradlew.bat --console=plain :app:verifyNodeDualRuntimeABGate "-Pautojs.nodejs.test.deviceProfile=sony"
```

`verifyNodeDualRuntimeABGate` is intended for candidate validation and should be run only after `node24_current` artifacts and matching suite reports exist.
