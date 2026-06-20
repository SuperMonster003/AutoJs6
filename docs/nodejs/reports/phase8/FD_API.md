# S8-28 Callback And Sync FD API Gate

Status: complete

S8-28 adds a focused gate for callback and synchronous fd APIs on the scoped
`fs` facade. The implementation remains AutoJs6-managed: scripts receive
numeric fd values for Node compatibility, but every accepted fd must map to an
execution-owned scoped descriptor.

## Covered Surface

`NodeFdApiInstrumentationTest` verifies:

- `require("fs")` and `require("node:fs")` keep the same managed facade
- `fs.open()` and `fs.openSync()` open only scoped paths
- `fs.close()` and `fs.closeSync()` close only owned descriptors
- `fs.read()` / `fs.readSync()` support Buffer reads and the options-object
  read form
- `fs.write()` / `fs.writeSync()` support Buffer and string writes
- `fs.fstat()` / `fs.fstatSync()` expose metadata for owned descriptors
- `fs.ftruncate()` / `fs.ftruncateSync()` operate only on owned descriptors
- relative path escapes reject with `ERR_AUTOJS6_FS_PATH_ESCAPE`
- absolute paths outside the working directory reject with
  `ERR_AUTOJS6_FS_ABSOLUTE_PATH_DENIED`
- non-owned, invalid, or closed descriptors reject with `EBADF`
- an execution that times out with an unclosed sync fd does not poison the next
  embedded Node execution after service settlement

This gate intentionally does not add raw OS descriptor authority, socket
descriptors, device files, or fd inheritance. The numeric fd is a compatibility
token guarded by the scoped descriptor registry.

## Gate

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFdApiInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

Advanced scoped fs regression pairing:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFsPromisesInstrumentationTest,org.autojs.autojs.engine.NodeFileHandleAdvancedInstrumentationTest,org.autojs.autojs.engine.NodeFdApiInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

This gate is part of `nodeJsV11AlphaInstrumentationClasses`, and therefore
flows into the v1.1 alpha/beta functional profile selection.

## Evidence

Verified on Sony XQ-AT72 `QV710AF65F` on 2026-06-20:

- `:app:compileAppDebugAndroidTestKotlin`
- focused `NodeFdApiInstrumentationTest` finished 2/2 successfully
- combined `NodeFsPromisesInstrumentationTest`,
  `NodeFileHandleAdvancedInstrumentationTest`, and
  `NodeFdApiInstrumentationTest` command finished 6/6 successfully
- `:app:verifyNodeCompatibilityProfile`
- `git diff --check`
- `git diff -- version.properties` produced no diff

## Security Boundary

S8-28 keeps callback and sync fd support inside the scoped fs boundary:

- fd values are accepted only when registered by AutoJs6-managed
  `open()` / `openSync()`
- callback and sync operations share the same descriptor ownership checks
- invalid, closed, or foreign numeric descriptors reject before raw native fd
  access
- path validation still blocks relative escapes, absolute outside paths,
  symlink escapes, and Android/device roots
- timeout cleanup closes or invalidates leaked descriptors before the next
  embedded Node execution
