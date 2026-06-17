# zlib Decision Gate

Status: enabled as a bounded compatibility surface. The current implementation chose a controlled buffer and transform-stream profile rather than keeping `zlib` disabled.

## Decision

AutoJs6 Safe Node Profile exposes `zlib` for pure JavaScript npm compatibility, with explicit input and output budgets and without network integration.

Disabled:

- `zlib/promises`
- `node:zlib/promises`
- custom native tuning beyond Node's exposed safe options
- any network behavior implied by compressed HTTP bodies

## Current Entry Points

- `require("zlib")`
- `require("node:zlib")`

Both aliases resolve to the same frozen limited module. `node_modules/zlib` must not shadow the builtin.

## Buffer And Callback APIs

Current sync and callback APIs include:

- `gzip` / `gzipSync`
- `gunzip` / `gunzipSync`
- `deflate` / `deflateSync`
- `inflate` / `inflateSync`
- `deflateRaw` / `deflateRawSync`
- `inflateRaw` / `inflateRawSync`
- `brotliCompress` / `brotliCompressSync`
- `brotliDecompress` / `brotliDecompressSync`

Callback APIs must schedule asynchronously and be counted as pending zlib work. Diagnostics should return `pendingZlibCallbacks=0` after a successful run.

## Stream APIs

Current transform stream APIs include:

- `createGzip`
- `createGunzip`
- `createDeflate`
- `createInflate`
- `createDeflateRaw`
- `createInflateRaw`
- `createBrotliCompress`
- `createBrotliDecompress`

These are limited transform streams for already-allowed data. They do not open network, fs, or native addon authority.

Active stream tracking begins when a zlib transform first receives data or
flushes, not when feature-detection code merely constructs a transform. Active
transforms are reported through `pendingZlibStreams` and
`pendingZlibStreamCount`, keep the embedded runtime alive with an internal
unreported keepalive, and are released on `finish`, `end`, `close`, `error`, or
explicit `destroy()`.

## Budgets

The current implementation enforces:

- input limit: 512 KiB
- output limit: 2 MiB

These limits protect the embedded service from accidental decompression bombs and large memory spikes. Future changes to these limits require tests and release-note visibility.

## Lifecycle Policy

Zlib callback work must be tracked as pending work. Stream transforms must release pending state on finish, error, destroy, timeout, or service destroy.

Timeout or service destruction must not leave pending zlib callbacks that poison the next execution.
Timeout or service destruction must also destroy active zlib transforms and
clear their registry entries so the next execution can start cleanly.

## Packaged APK Behavior

No additional Android permissions are required. Packaged APK smoke should verify `zlib` builtin priority, a small compression roundtrip, and that `zlib/promises` remains denied.

## Regression Tests

Required coverage:

- gzip/gunzip sync roundtrip
- deflate/inflate sync roundtrip
- raw deflate/inflate sync roundtrip
- brotli callback roundtrip
- `util.promisify(zlib.gzip)` callback path
- zlib transform stream roundtrip with `stream.pipeline`
- scoped fs read stream -> gzip -> scoped fs write stream smoke
- invalid data errors
- input and output budget errors
- active zlib stream timeout and next-run recovery
- `zlib/promises` denial
- builtin shadowing protection
- packaged APK smoke

Current coverage is in the embedded npm-style zlib smoke, denied-builtin tests, and `NodeZlibBufferInstrumentationTest`.
