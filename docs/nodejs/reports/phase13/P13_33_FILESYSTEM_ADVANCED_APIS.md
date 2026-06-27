# P13-33 Filesystem Advanced APIs

Status: ready

P13-33 records the v1.1 scoped advanced filesystem API gate as ready. The
current Safe Node Profile v1.1 already has scoped coverage for streams,
`FileHandle`, callback and sync fd APIs, `opendir` / `Dirent`, watchers, and
`realpath`, but the surface is still execution-owned and working-directory
scoped rather than full desktop Node.js filesystem authority.

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

- packaged APK live-smoke coverage for watcher, fd, FileHandle, stream, and
  close-on-destroy behavior;
- expanded-root reruns once P13-32 root authority providers exist;
- final Desktop profile decision for full `fs` / `fs.promises` semantics,
  including APIs that remain platform-limited or intentionally denied.

## Android Compatibility Notes

- Watcher events may be coalesced by Android filesystem notifications and by
  the scoped bridge event budget. Consumers must treat `rename` / `change`
  event counts as advisory rather than one-to-one desktop event delivery.
- Recursive `fs.watch` remains denied on Android. Callers must register
  explicit watchers per authorized directory instead of relying on recursive
  subtree watching.
- `realpath` resolves after scoped-path policy and symlink escape checks.
  Android package assets and provider-backed paths may differ from desktop
  POSIX paths, so only working-directory and sandbox-root real paths are
  portable P13-33 evidence.

## Packaged Evidence

Existing packaged smoke sources and preflight checks already include packaged
realpath and opendir markers:

- `packaged.fs.realpath=true`
- `packaged.dirent.opendir=true`
- `packaged.url.opendir_url=true`

These are evidence for realpath/opendir compatibility only. They are not a full
packaged lifecycle proof for watcher, fd, FileHandle, stream, or
close-on-destroy behavior.

The P13-33 focused gate now also exposes `pendingScopedFsWatchCount` and
`pendingScopedFsWatchFileCount` through Kotlin result diagnostics, including
the adapter result-envelope fallback path, and asserts that both counts return
to zero after closing the scoped watcher and `watchFile` resources.

The docs, release matrix, desktop matrix, surface manifest, and Gradle gate are
evidence-only for this partial decision. They do not add a separate P13-33
blocker beyond the packaged device-exported evidence and future expanded-root
rerun items above.

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

## Closure

The v1.1 gate closes because `filesystemProfile.advancedApis`, watcher limits,
scoped streams, `FileHandle`, fd API compatibility tokens, `opendir`, `Dirent`,
`fs.watch`, `fs.watchFile`, `fs.promises.watch`, `realpath`, pending watcher/
watchFile diagnostics, Android compatibility notes, packaged realpath/opendir
markers, TypeScript declarations, docs, desktop matrix, surface manifest, and
Gradle gate are synchronized without exposing raw native fds or expanding
filesystem roots.

## Future Work

- Add packaged advanced-fs lifecycle smokes and archive current device evidence.
- Re-run advanced-fs suites against each future promoted root class.
