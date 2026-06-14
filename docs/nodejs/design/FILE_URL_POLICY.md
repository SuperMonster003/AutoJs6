# File URL Policy

Status: implemented as scoped file URL support. This supersedes the earlier disabled-by-default proposal because the current runtime already exposes covered `url.fileURLToPath()` and `url.pathToFileURL()` behavior.

## Goal

File URL support must not become a path escape hatch. It exists only as a compatibility bridge for pure JavaScript packages that convert between path strings and `file://` URLs.

## Current Entry Points

- `require("url").fileURLToPath`
- `require("url").pathToFileURL`
- `require("node:url").fileURLToPath`
- `require("node:url").pathToFileURL`

Both helpers are AutoJs6-scoped wrappers, not raw desktop Node helpers.

## Policy

`pathToFileURL(path)`:

- accepts only string paths
- validates the input path with the scoped fs policy before returning a URL
- rejects absolute paths and escaping relative paths
- returns a URL only for a path that is valid inside the current working directory scope

`fileURLToPath(url)`:

- accepts only a valid file URL value
- converts the URL to a path candidate
- validates the candidate with the scoped fs policy before returning it
- rejects file URLs that resolve outside the working directory scope

Filesystem APIs that accept path-like inputs must apply the same validation for URL objects as they do for strings and Buffers. A file URL must never bypass checks for absolute paths, parent traversal, symlinks, realpath escapes, device paths, or package target escapes.

## Non-goals

- no general URL loading
- no `http:` or `https:` filesystem reads
- no Android content URI support through Node fs
- no conversion of file URLs into privileged Android paths
- no widening of packaged APK asset access

## Error Shape

Current scoped file URL denials should preserve Node-compatible error shapes where applicable and attach AutoJs6 canonical fs codes for policy failures. Future work may add a narrower `ERR_AUTOJS6_FS_FILE_URL_UNSUPPORTED` only if a specific unsupported file URL form needs to be distinguished from ordinary scoped path denial.

## Packaged APK Behavior

Packaged scripts receive the same file URL policy as main-app scripts. File URLs may refer only to paths that are valid in the packaged script working directory. They must not expose APK internals, arbitrary assets, or host app private files outside the selected project scope.

## Regression Tests

Required coverage:

- `pathToFileURL("./safe.txt")` returns a file URL for a scoped path
- `fileURLToPath()` roundtrips a scoped file URL
- absolute paths are denied before URL creation
- `../` paths are denied before URL creation
- file URLs resolving outside the working directory are denied
- fs read/write APIs using URL objects remain scoped
- path strings produced by URL helpers cannot be reused to bypass scoped fs
- packaged APK smoke covers file URL use in a Node project if packaged corpus expands into this area

Current coverage is in the embedded fs/url instrumentation corpus and the security regression corpus.
