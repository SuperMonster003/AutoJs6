# Stream Design Gate

Status: implemented as a partial compatibility surface. This document records the current boundary so later stream work does not accidentally open network, unrestricted fs, or native handle escape paths.

## Goal

Stream support exists to satisfy pure JavaScript npm packages that expect common Node stream primitives, pipeline helpers, fs streams, readline inputs, and zlib transforms. It is not a commitment to full desktop Node stream fidelity.

## Current Entry Points

- `require("stream")`
- `require("node:stream")`
- `require("stream/promises")`
- `require("node:stream/promises")`
- `require("stream/consumers")`
- `require("node:stream/consumers")`
- `require("stream/web")`
- `require("node:stream/web")`

These modules are builtin-priority and must not be shadowed by `node_modules`.

## Current Compatibility Surface

The current test corpus covers:

- `Readable`
- `Writable`
- `Duplex`
- `Transform`
- `PassThrough`
- `Readable.from()`
- callback `pipeline`
- callback `finished`
- promise `pipeline`
- promise `finished`
- common stream state helpers
- stream interaction with readline
- scoped `fs.createReadStream`
- scoped `fs.createWriteStream`
- scoped fs stream active-resource diagnostics
- zlib transform streams

## Safety Boundaries

Streams must not create new authority. A stream can only operate on data or handles that were already allowed by another scoped subsystem.

Required boundaries:

- no network sockets through streams unless a future network design gate opens them
- fs streams must use scoped fs path validation
- FileHandle streams must use owned fd tracking
- active scoped fs streams must be tracked until `close`, `end`, `finish`, or `error`
- zlib streams must enforce zlib input/output budgets and active stream cleanup
- stream constructors must not expose raw native handles
- destroyed streams must release callbacks and pending counters
- timeout must destroy active AutoJs6-owned streams

## Backpressure And Fidelity

The compatibility target is enough fidelity for common pure-JS packages. Full desktop Node backpressure timing and every internal state edge case are not release blockers for Safe Node Profile.

When a mismatch is discovered, prefer:

- stable documented behavior
- deterministic errors
- tests for the specific package pattern
- no expansion of authority

## Lifecycle Policy

Streams can keep the runtime alive only while they have pending work that the profile intentionally tracks. Completed, destroyed, or errored streams must not poison the next execution.

Lifecycle tests should cover:

- pipeline success
- pipeline error propagation
- finished callback and promise resolution
- readable async iteration
- writable drain and finish
- timeout with active stream
- service destroy with active stream
- active scoped fs stream timeout recovery

## Deferred Work

- unrestricted network streams
- worker-thread stream transfer
- native addon stream sources
- inspector/debugger stream plumbing
- full Web Streams parity beyond the current limited surface

## Regression Tests

Required coverage:

- `Readable.from(["a", "b"])` async iteration
- `PassThrough` writes and reads
- `Transform` chunk mutation
- callback and promise pipeline success
- callback and promise pipeline error propagation
- `finished` callback and promise paths
- scoped fs read/write streams reject escaping paths
- scoped fs read/write streams reject absolute Android/device paths, including `/proc`, `/sys`, and `/dev`
- active scoped fs streams are visible in pending diagnostics and do not poison the next execution after timeout/service cleanup
- zlib stream roundtrip remains bounded
- active zlib transforms are visible in pending diagnostics after first data write and do not poison the next execution after timeout/service cleanup
- `node:stream` cannot be shadowed

Current coverage lives in the embedded stream, fs, readline, zlib, compatibility corpus, security regression tests, and `NodeStreamMvpInstrumentationTest`.
