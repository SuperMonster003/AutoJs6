# fs.promises Design Gate

Status: implemented as a scoped compatibility surface. This design records the current boundary and the regression gates that must remain true.

## Goal

`fs.promises` exists to support pure JavaScript CommonJS packages that expect modern Node file APIs while preserving the AutoJs6 scoped filesystem model. It is not unrestricted desktop `fs.promises`.

## Current Entry Points

- `require("fs").promises`
- `require("fs/promises")`
- `require("node:fs").promises`
- `require("node:fs/promises")`

The aliases resolve to the same frozen scoped module. `node_modules/fs` and `node_modules/fs/promises` must not shadow these builtins.

## Scope

The current implementation is broader than the original MVP proposal. It includes the MVP read/write/stat APIs and additional compatibility APIs, all behind the same scoped path policy.

Core scoped promise APIs:

- `readFile`
- `writeFile`
- `appendFile`
- `stat`
- `lstat`
- `statfs`
- `access`
- `readdir`
- `mkdir`
- `unlink`
- `rm`
- `rmdir`
- `realpath`
- `copyFile`
- `rename`
- `truncate`
- `readlink`

Expanded scoped compatibility APIs:

- `open`, returning an AutoJs6 limited `FileHandle`
- `opendir`
- `watch`
- `glob`
- `cp`
- `link`
- `symlink`
- `chmod`
- `lchmod`
- `chown`
- `lchown`
- `utimes`
- `lutimes`
- `mkdtemp`
- `mkdtempDisposable`

The expanded APIs are compatibility surfaces, not permission expansion. Each path argument is validated before reaching Node's native fs implementation.

## Limited FileHandle

`fs.promises.open()` returns a limited FileHandle wrapper owned by the current embedded execution. The wrapper may expose common read/write/stat/sync/close helpers and line-reading helpers where tests cover them.

Rules:

- FileHandle fd ownership must be tracked.
- Operations must reject if the fd is closed, foreign, or not owned by the current scoped fs registry.
- FileHandle paths remain subject to the original open-time scoped validation.
- FileHandle stream helpers must still route through scoped stream tracking.
- Service destroy or timeout must close owned handles and reject pending work.

## Scoped Path Policy

All promise APIs must reuse the same policy as sync and callback fs:

- no absolute user paths
- no NUL byte paths
- no `..` escape
- no `/proc`, `/sys`, or `/dev`
- no realpath escape
- no symlink escape
- package exports/imports targets must remain inside their package root
- root removal of the working directory is denied
- file descriptors and stream options must not bypass path validation

Errors keep Node-style `code` values where useful, such as `EPERM` or `ENOENT`, and attach AutoJs6 canonical codes where the denial is an AutoJs6 policy denial, such as `ERR_AUTOJS6_FS_PATH_ESCAPE`.

## Lifecycle Policy

Promise operations must be asynchronous from JavaScript's perspective and must be counted as pending work. The embedded runtime must not report success while scoped fs promise callbacks, stream callbacks, watchers, or zlib/file interactions are still pending.

On timeout or service destroy:

- pending fs promise work must reject or be abandoned only after the runtime is terminal
- owned FileHandles must be closed
- watchers must be closed
- scoped streams must be destroyed
- diagnostic counters must return to zero for the next execution

## Packaged APK Behavior

Packaged APKs use the same scoped fs implementation. No extra Android permissions are required for app-private packaged data and working-directory access. Packaged tests must continue to verify that module sources, encrypted entries, and package metadata cannot escape the scoped working directory.

## Regression Tests

Required coverage:

- read/write/append roundtrip
- stat/lstat/access/readdir/mkdir/rm/unlink
- ENOENT shape
- EPERM and AutoJs6 scoped denial shape
- `Promise.all` and nested promise drain
- FileHandle close and owned-fd behavior
- stream and watcher cleanup
- symlink and realpath escape denial
- package exports/imports target escape denial
- `fs.promises` alias and shadowing protection
- packaged APK smoke for scoped fs and npm corpus

Current broad coverage lives mainly in `EmbeddedScriptMvpInstrumentationTest`, with focused regression coverage in `NodeCompatCorpusInstrumentationTest`, `NodeSecurityRegressionInstrumentationTest`, and `NodeFsPromisesInstrumentationTest`.
