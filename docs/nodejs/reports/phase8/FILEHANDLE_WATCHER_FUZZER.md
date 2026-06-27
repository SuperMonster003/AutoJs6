# S8-32 FileHandle / Watcher Fuzzer Gate

Status: complete

S8-32 adds a deterministic fuzzer for the lifecycle edge cases around scoped
FileHandle, numeric fd, and watcher resources. It is a release evidence gate
for the Batch E advanced scoped filesystem surface, not a new runtime
capability.

## Covered Surface

`NodeFileHandleWatcherFuzzerInstrumentationTest` verifies:

- double-close and close/read races for managed `FileHandle` objects
- callback `fs.close()` races for execution-owned numeric fd tokens
- `AbortSignal` cancellation for `fs.watch()` and `fs.promises.watch()`
- closed fd reuse denial with stable `EBADF`
- watcher quota exhaustion with `ERR_AUTOJS6_FS_WATCH_LIMIT`
- directory watcher behavior while a watched path is renamed
- symlink replacement attempts, including external absolute target denial
- long nested scoped paths through `fs.promises.open()`
- Unicode scoped paths and watcher cleanup
- service timeout cleanup after intentionally leaked fd, FileHandle, watcher,
  and timer resources
- post-timeout recovery in a fresh embedded Node execution

Android file watcher delivery remains provider dependent. The fuzzer accepts
coalesced or missing event callbacks where Android/Node may legally coalesce
notifications, while still requiring final filesystem state, scoped path
policy, resource cleanup, and stable AutoJs6 error codes.

## Gate

Quick fuzzer budget:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFileHandleWatcherFuzzerInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.fuzzerBudget=quick"
```

Normal fuzzer budget:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFileHandleWatcherFuzzerInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.fuzzerBudget=normal"
```

Fuzzer regression pairing:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeResolverFuzzerInstrumentationTest,org.autojs.autojs.engine.NodeScopedFsFuzzerInstrumentationTest,org.autojs.autojs.engine.NodeFileHandleWatcherFuzzerInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.fuzzerBudget=quick"
```

This gate is part of `nodeJsV11BetaInstrumentationClasses`, and therefore
flows into the v1.1 beta functional profile selection.

## Evidence

Verified on Sony XQ-AT72 `QV710AF65F` on 2026-06-20:

- `:app:compileAppDebugKotlin`
- `:app:compileAppDebugAndroidTestKotlin`
- focused `NodeFileHandleWatcherFuzzerInstrumentationTest` quick budget
  finished 2/2 successfully
- focused `NodeFileHandleWatcherFuzzerInstrumentationTest` normal budget
  finished 2/2 successfully
- combined quick `NodeResolverFuzzerInstrumentationTest`,
  `NodeScopedFsFuzzerInstrumentationTest`, and
  `NodeFileHandleWatcherFuzzerInstrumentationTest` command finished 4/4
  successfully
- `git diff --check`
- `git diff -- version.properties` produced no diff

## Security Boundary

S8-32 keeps the advanced scoped fs lifecycle surface bounded:

- FileHandle and numeric fd values remain execution-owned compatibility
  objects; closed and non-owned descriptors reject with stable errors
- watcher resources remain scoped, quota-bound, and owned by the active
  embedded execution
- cancellation and timeout cleanup close leaked resources before the next
  execution can start
- path rename and Unicode/long-path cases must stay inside the scoped working
  directory
- external symlink replacement remains denied and must not create a readable
  outside path
- the fuzzer does not promote recursive watch, raw OS fd access, raw watcher
  handles, Android/device root access, or external symlink authority
