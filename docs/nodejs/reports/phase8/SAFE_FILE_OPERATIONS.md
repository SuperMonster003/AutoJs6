# S8-31 Scoped Safe File Operations Gate

Status: complete

S8-31 adds a focused gate for the scoped safe file operations that were
previously covered mostly by broad embedded-script regression tests. The gate
promotes `cp`, `mkdtemp`, `statfs`, and a constrained `glob` subset as
documented v1.1 scoped filesystem compatibility surface without changing the
Safe Node Profile boundary.

## Covered Surface

`NodeSafeFileOperationsInstrumentationTest` verifies:

- `fs.cpSync()`, `fs.cp()`, and `fs.promises.cp()` copy files, directory
  trees, URL paths, callback paths, file-to-directory targets, force/skip
  overwrite behavior, and synchronous filter functions inside the scoped
  working directory
- `fs.mkdtempSync()`, `fs.mkdtemp()`, `fs.promises.mkdtemp()`,
  `fs.mkdtempDisposableSync()`, and `fs.promises.mkdtempDisposable()` create
  scoped temporary directories from string, Buffer, and file URL prefixes
- disposable temporary directories expose frozen remove handles and remove
  their scoped trees after sync or async disposal
- `fs.statfsSync()`, `fs.statfs()`, and `fs.promises.statfs()` return numeric
  and bigint filesystem statistics for scoped directories and files
- `fs.globSync()`, `fs.glob()`, and `fs.promises.glob()` cover the safe subset
  of string and string-array patterns, scoped `cwd`, file URL and Buffer `cwd`,
  `withFileTypes`, string-array excludes, and synchronous exclude functions
- relative escapes, absolute outside paths, NUL paths, missing inputs, invalid
  callback/options shapes, `cp` async filters, recursive-required directory
  copies, self-copy into a source subtree, and `glob` `followSymlinks` all stay
  denied with stable AutoJs6 or Node error metadata
- `link`, `chmod`, `chown`, and `symlink` remain outside the S8-31 promoted
  surface. Existing scoped wrappers are regression-tested only for bounded
  behavior: safe hard links may work when the platform supports them, while
  source/destination escapes, external symlink targets, and Android/device-root
  paths remain denied.

## Gate

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeSafeFileOperationsInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

Advanced scoped fs regression pairing:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFsPromisesInstrumentationTest,org.autojs.autojs.engine.NodeFileHandleAdvancedInstrumentationTest,org.autojs.autojs.engine.NodeFdApiInstrumentationTest,org.autojs.autojs.engine.NodeOpendirInstrumentationTest,org.autojs.autojs.engine.NodeFsWatchInstrumentationTest,org.autojs.autojs.engine.NodeSafeFileOperationsInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

This gate is part of `nodeJsV11AlphaInstrumentationClasses`, and therefore
flows into the v1.1 alpha/beta functional profile selection.

## Evidence

Verified on Sony XQ-AT72 `QV710AF65F` on 2026-06-20:

- `:app:compileAppDebugKotlin`
- `:app:compileAppDebugAndroidTestKotlin`
- focused `NodeSafeFileOperationsInstrumentationTest` finished 3/3
  successfully
- combined `NodeFsPromisesInstrumentationTest`,
  `NodeFileHandleAdvancedInstrumentationTest`, `NodeFdApiInstrumentationTest`,
  `NodeOpendirInstrumentationTest`, `NodeFsWatchInstrumentationTest`, and
  `NodeSafeFileOperationsInstrumentationTest` command finished 14/14
  successfully
- `:app:verifyNodeCompatibilityProfile`
- `git diff --check`
- `git diff -- version.properties` produced no diff

## Security Boundary

S8-31 keeps safe file operations inside the scoped fs boundary:

- all promoted operations go through AutoJs6-managed scoped `fs` entry points
- `cp` creates destination parents only inside the scoped working directory and
  rejects async filters, source-subtree self-copy, and unsupported source types
- `mkdtemp` prefixes must resolve inside the scoped root, with scoped parents
  already present unless the underlying operation can create them safely
- `statfs` accepts only scoped existing paths and does not expose mount/device
  operations
- `glob` rejects parent-segment patterns, absolute/protocol patterns, invalid
  excludes, and `followSymlinks`
- external symlink targets and Android/device roots remain denied
- hard-link, chmod, chown, and symlink behavior is not promoted by S8-31; the
  gate only verifies that their escape and external-target boundaries stay
  denied or platform-fail-closed
