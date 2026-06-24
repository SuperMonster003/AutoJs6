# P13-33 Filesystem Advanced APIs

Status: partial

P13-33 records desktop fs advanced APIs as partial. The current Safe Node
Profile v1.1 already has scoped coverage for streams, `FileHandle`, callback
and sync fd APIs, `opendir` / `Dirent`, watchers, and `realpath`, but the
surface is still execution-owned and working-directory scoped rather than full
desktop Node.js filesystem authority.

## Decision

- `require("autojs6:profile").filesystemProfile.advancedApis` exposes frozen
  diagnostics for `streams`, `fileHandle`, `fd`, `opendir`, `watch`, and
  `realpath` as `partial`.
- `rawFd` remains `false`; numeric fd values are compatibility tokens owned by
  the scoped descriptor registry, not native file descriptors exposed to
  scripts.
- `filesystemProfile.limits` records the current watcher envelope:
  `watcherLimit=16`, `watchEventLimit=64`, and `watchEventWindowMs=1000`.
- Scoped streams, `FileHandle`, fd APIs, `opendir`, `fs.watch`,
  `fs.watchFile`, `fs.promises.watch`, and `realpath` remain covered by the
  existing S8 regression suites and the new P13-33 focused profile gate.
- Recursive `fs.watch` remains unsupported on Android.

## Boundary

The following remain future work before P13-33 can move beyond partial:

- Kotlin/device result diagnostics for pending watcher and watchFile counts, or
  an equivalent host-report field;
- packaged APK live-smoke coverage for watcher, fd, FileHandle, stream, and
  close-on-destroy behavior;
- expanded-root reruns once P13-32 root authority providers exist;
- Android unsupported behavior notes for watcher coalescing, recursive-watch
  denial, platform-specific realpath behavior, filesystem permission failures,
  and package asset extraction differences;
- final Desktop profile decision for full `fs` / `fs.promises` semantics,
  including APIs that remain platform-limited or intentionally denied.

## Packaged Evidence

Existing packaged smoke sources and preflight checks already include packaged
realpath and opendir markers:

- `packaged.fs.realpath=true`
- `packaged.dirent.opendir=true`
- `packaged.url.opendir_url=true`

These are evidence for realpath/opendir compatibility only. They are not a full
packaged lifecycle proof for watcher, fd, FileHandle, stream, or
close-on-destroy behavior.

## Verification

- Host verifier: `tools/nodejs/phase13/verify-filesystem-advanced-apis.js`
- Gradle gate: `:app:verifyNodePhase13FilesystemAdvancedApis`
- Focused device gate:
  `NodeFilesystemAdvancedDecisionInstrumentationTest`
- Existing regression gates:
  `NodeFileHandleAdvancedInstrumentationTest`,
  `NodeFdApiInstrumentationTest`,
  `NodeOpendirInstrumentationTest`,
  `NodeFsWatchInstrumentationTest`,
  `NodeFileHandleWatcherFuzzerInstrumentationTest`,
  `NodeFsPromisesInstrumentationTest`,
  `NodeSafeFileOperationsInstrumentationTest`, and
  `NodeScopedFsFuzzerInstrumentationTest`
- Type declarations:
  `docs/nodejs/types/autojs6-node/autojs_global.d.ts` and
  `sample/nodejs/typescript-smoke/src/main.ts`

## Remaining Work

- Add packaged advanced-fs lifecycle smokes and archive current device evidence.
- Surface pending watcher/watchFile cleanup counts through Kotlin diagnostics.
- Re-run advanced-fs suites against each future promoted root class.
- Expand the Android compatibility notes for platform-specific watcher and
  realpath behavior before any Desktop profile promotion.
