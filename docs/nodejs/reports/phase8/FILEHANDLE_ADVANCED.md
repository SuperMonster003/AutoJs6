# S8-27 FileHandle Advanced Scoped FS Gate

Status: complete

S8-27 adds a focused advanced `FileHandle` gate for the scoped filesystem
surface behind `fs.promises.open()`. The gate records the v1.1 contract for
execution-owned descriptors without broadening scoped path authority.

## Covered Surface

`NodeFileHandleAdvancedInstrumentationTest` verifies:

- `require("fs/promises")`, `require("node:fs/promises")`, and `fs.promises`
  keep the same managed facade
- `fs.promises.open()` returns an execution-owned scoped `FileHandle`
- `FileHandle.read()`, `readFile()`, `write()`, `writeFile()`,
  `appendFile()`, `truncate()`, `datasync()`, `sync()`, `stat()`, and
  `close()` stay available
- `fs.promises.readFile(fileHandle)` and
  `fs.promises.appendFile(fileHandle, data)` accept managed handles
- relative path escapes reject with `ERR_AUTOJS6_FS_PATH_ESCAPE`
- absolute paths outside the working directory reject with
  `ERR_AUTOJS6_FS_ABSOLUTE_PATH_DENIED`
- closed handles and non-owned numeric descriptors reject with `EBADF`
- an execution that times out with an unclosed handle does not poison the next
  embedded Node execution after service settlement

The gate also keeps the current `FileHandle.readFile()` offset behavior visible:
promise-level `readFile(fileHandle)` coverage uses a fresh managed handle rather
than relying on an already advanced file position.

## Gate

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFileHandleAdvancedInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

Regression pairing with the existing `fs.promises` focused gate:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFsPromisesInstrumentationTest,org.autojs.autojs.engine.NodeFileHandleAdvancedInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

This gate is part of `nodeJsV11AlphaInstrumentationClasses`, and therefore
flows into the v1.1 alpha/beta functional profile selection.

## Evidence

Verified on Sony XQ-AT72 `QV710AF65F` on 2026-06-20:

- `:app:compileAppDebugAndroidTestKotlin`
- focused `NodeFileHandleAdvancedInstrumentationTest` finished 2/2
  successfully
- combined `NodeFsPromisesInstrumentationTest` and
  `NodeFileHandleAdvancedInstrumentationTest` command finished 4/4
  successfully
- `:app:verifyNodeCompatibilityProfile`
- `git diff --check`
- `git diff -- version.properties` produced no diff

## Security Boundary

S8-27 keeps FileHandle support inside the scoped fs boundary:

- handles are created only by AutoJs6-managed scoped `fs.promises.open()`
- numeric descriptors are accepted only when they are still open and owned by
  the current managed execution
- closed handles fail with `EBADF` instead of falling through to raw native fd
  access
- relative escapes, absolute outside paths, Android/device roots, and symlink
  escapes remain denied by the existing scoped path policy
- timeout cleanup closes or invalidates active managed descriptors before the
  next embedded Node execution
