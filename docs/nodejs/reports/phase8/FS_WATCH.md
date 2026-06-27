# S8-30 Scoped FS Watch Gate

Status: complete

S8-30 adds a focused gate for scoped `fs.watch()`, `fs.promises.watch()`,
`fs.watchFile()`, and `fs.unwatchFile()`. The implementation stays inside the
AutoJs6 scoped filesystem and records Android watcher behavior as a
compatibility surface, not as raw filesystem notification authority.

## Covered Surface

`NodeFsWatchInstrumentationTest` verifies:

- `fs.watch()` callback listeners receive scoped file change notifications
- `FSWatcher` event listeners, `close()`, `ref()`, and `unref()` stay usable
- `AbortSignal` closes active and pre-aborted `fs.watch()` handles
- path-like values, including scoped file URLs and Buffers, stay accepted
- `encoding: "buffer"` returns Buffer filenames when Android/Node provides a
  filename
- `fs.promises.watch()` returns a scoped async iterator with `next()`,
  `return()`, async iteration, `AbortSignal`, and buffer filename support
- `fs.watchFile()` supports polling existing and missing files, bigint stats,
  `stop()`, `ref()`, `unref()`, listener-specific `unwatchFile()`, and
  full-path `unwatchFile()`
- relative escapes reject with `ERR_AUTOJS6_FS_PATH_ESCAPE`
- absolute outside paths reject with `ERR_AUTOJS6_FS_ABSOLUTE_PATH_DENIED`
- missing `fs.watch()` targets reject with `ENOENT`
- recursive `watch` stays unsupported with
  `ERR_AUTOJS6_EMBEDDED_NODE_UNSUPPORTED_OPTION`
- malformed listeners reject with `ERR_INVALID_ARG_TYPE`
- watcher quota rejects with `ERR_AUTOJS6_FS_WATCH_LIMIT`
- event-rate overflow rejects with `ERR_AUTOJS6_FS_WATCH_RATE_LIMITED`
- timeout cleanup closes leaked watcher handles before the next embedded Node
  execution

Android file notification delivery can coalesce events and may report
`change`, `rename`, or a null filename depending on provider behavior. The gate
therefore verifies the controlled watcher wrapper, scoped path policy, resource
limits, and cleanup behavior while accepting documented Android/Node event
coalescing.

## Gate

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFsWatchInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

Advanced scoped fs regression pairing:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFsPromisesInstrumentationTest,org.autojs.autojs.engine.NodeFileHandleAdvancedInstrumentationTest,org.autojs.autojs.engine.NodeFdApiInstrumentationTest,org.autojs.autojs.engine.NodeOpendirInstrumentationTest,org.autojs.autojs.engine.NodeFsWatchInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

This gate is part of `nodeJsV11AlphaInstrumentationClasses`, and therefore
flows into the v1.1 alpha/beta functional profile selection.

## Evidence

Verified on Sony XQ-AT72 `QV710AF65F` on 2026-06-20:

- `:app:compileAppDebugKotlin`
- `:app:compileAppDebugAndroidTestKotlin`
- focused `NodeFsWatchInstrumentationTest` finished 3/3 successfully
- combined `NodeFsPromisesInstrumentationTest`,
  `NodeFileHandleAdvancedInstrumentationTest`, `NodeFdApiInstrumentationTest`,
  `NodeOpendirInstrumentationTest`, and `NodeFsWatchInstrumentationTest`
  command finished 11/11 successfully
- `:app:verifyNodeCompatibilityProfile`
- `:app:verifyNodeErrorCatalog`
- `git diff --check`
- `git diff -- version.properties` produced no diff

## Security Boundary

S8-30 keeps watchers inside the scoped fs boundary:

- watchers are created only by AutoJs6-managed scoped `fs` entry points
- recursive watch remains disabled until Android/provider behavior can be
  proven reliable
- the active watcher quota is 16 per embedded execution
- each watcher is limited to 64 events per 1000 ms window
- quota and rate-limit errors close or settle managed watcher records
- relative escapes, absolute outside paths, Android/device roots, and symlink
  escapes remain denied by the existing scoped path policy
- service stop and timeout cleanup close active watchers before the next
  embedded Node execution
