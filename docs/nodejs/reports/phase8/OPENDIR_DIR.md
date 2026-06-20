# S8-29 Opendir / Dir / Dirent Gate

Status: complete

S8-29 adds a focused gate for the scoped `opendir()` surface. The gate records
the v1.1 contract for directory iteration without adding raw directory handle
authority or widening scoped path access.

## Covered Surface

`NodeOpendirInstrumentationTest` verifies:

- `fs.opendir()`, `fs.opendirSync()`, and `fs.promises.opendir()` are available
  through the managed scoped `fs` facade
- `Dir.read()`, callback `Dir.read(callback)`, and `Dir.readSync()` return
  scoped `Dirent` objects
- `Dir.close()`, callback `Dir.close(callback)`, and `Dir.closeSync()` close
  managed directory handles
- async iteration over `Dir` drains entries and auto-closes the handle
- closed `Dir` handles reject with `ERR_DIR_CLOSED`
- `Dirent` exposes stable `name`, `path`, `parentPath`, `isFile()`,
  `isDirectory()`, and `isSymbolicLink()` behavior for local entries
- `encoding: "buffer"` returns Buffer names
- small recursive directory walks work for sync and promise opendir paths
- recursive walks are capped by the scoped fs recursive entry budget
- relative path escapes reject with `ERR_AUTOJS6_FS_PATH_ESCAPE`
- absolute paths outside the working directory reject with
  `ERR_AUTOJS6_FS_ABSOLUTE_PATH_DENIED`
- file targets, missing directories, NUL paths, and missing callbacks reject
  with stable Node or AutoJs6 error codes

This gate intentionally keeps directory handles execution-owned and JS-managed.
It does not expose raw OS directory handles, file descriptors, watcher handles,
or unscoped recursive traversal.

## Gate

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeOpendirInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

Advanced scoped fs regression pairing:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFsPromisesInstrumentationTest,org.autojs.autojs.engine.NodeFileHandleAdvancedInstrumentationTest,org.autojs.autojs.engine.NodeFdApiInstrumentationTest,org.autojs.autojs.engine.NodeOpendirInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

This gate is part of `nodeJsV11AlphaInstrumentationClasses`, and therefore
flows into the v1.1 alpha/beta functional profile selection.

## Evidence

Verified on Sony XQ-AT72 `QV710AF65F` on 2026-06-20:

- `:app:compileAppDebugKotlin`
- `:app:compileAppDebugAndroidTestKotlin`
- focused `NodeOpendirInstrumentationTest` finished 2/2 successfully
- combined `NodeFsPromisesInstrumentationTest`,
  `NodeFileHandleAdvancedInstrumentationTest`, `NodeFdApiInstrumentationTest`,
  and `NodeOpendirInstrumentationTest` command finished 8/8 successfully
- `:app:verifyNodeCompatibilityProfile`
- `git diff --check`
- `git diff -- version.properties` produced no diff

## Security Boundary

S8-29 keeps directory iteration inside the scoped fs boundary:

- directory handles are created only by AutoJs6-managed scoped `opendir()`
  entry points
- closed handles fail with `ERR_DIR_CLOSED` instead of falling through to raw
  native directory operations
- relative escapes, absolute outside paths, Android/device roots, and symlink
  escapes remain denied by the existing scoped path policy
- recursive walks reuse the scoped resolver and are capped at 4096 returned
  entries per operation
- returned `Dirent` objects are compatibility snapshots and do not expose raw
  native handles or descriptors
