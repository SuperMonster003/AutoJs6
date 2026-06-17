# Web Streams v1.1 Design Gate

Status: partial compatibility surface for Node Profile v1.1.

## Decision

AutoJs6 exposes enough Web Streams for pure JavaScript packages, Blob/File
helpers, scoped file web streams, and controlled fetch response consumption.
This does not imply browser parity or raw network streaming.

The v1.1 surface keeps Web Streams as a data adapter over already-authorized
resources:

- `ReadableStream`
- `WritableStream`
- `TransformStream`
- `TextEncoderStream`
- `TextDecoderStream`
- `ByteLengthQueuingStrategy`
- `CountQueuingStrategy`
- `require("stream/web")`
- `require("node:stream/web")`
- `Readable.toWeb()` / `Readable.fromWeb()`
- `Writable.toWeb()` / `Writable.fromWeb()`

## Fetch Body Policy

Controlled fetch remains buffer-bounded. Provider responses are collected under
the existing fetch `maxResponseBytes` policy before JavaScript receives a
`Response`.

For v1.1, `Response.body` remains the current buffer-backed Blob-compatible
body object. Scripts that need a Web Stream should use:

```javascript
const response = await fetch(url);
const reader = response.body.stream().getReader();
```

This preserves existing `response.text()`, `response.arrayBuffer()`, and
`response.json()` behavior while providing a Web Stream read path for modern
packages. Direct spec-style `Response.body` as a `ReadableStream` is deferred
until request/response streaming and backpressure policies are designed.

## Safety Boundaries

Web Streams must not create new authority:

- no raw network sockets
- no raw filesystem paths
- no file descriptor or native handle exposure
- no bypass around scoped fs stream validation
- no bypass around fetch response budgets
- no service-lifetime resource that survives runtime cleanup

Adapters may wrap only streams that another allowed subsystem already created,
such as scoped fs streams, Blob streams, zlib transforms, or controlled fetch
buffer bodies.

## Lifecycle

Pure Web Streams are JavaScript objects and are not tracked as native resources.
Owned subsystems remain responsible for cleanup:

- scoped fs streams are tracked and destroyed by the scoped fs registry
- zlib transforms are tracked after first data write or flush
- controlled fetch requests are tracked by the bridge request registry
- service destroy rejects pending bridge fetches and closes owned resources

Completed, errored, cancelled, or destroyed streams must not poison the next
embedded execution.

## Required Tests

- `stream/web` and `node:stream/web` builtin priority and shadowing denial
- global `ReadableStream`, `WritableStream`, and `TransformStream` smoke
- `Readable.toWeb()` / `Readable.fromWeb()` adapter smoke
- `Writable.toWeb()` / `Writable.fromWeb()` adapter smoke
- Blob `.stream()` read path
- controlled fetch `response.body.stream()` read path with fake provider
- service destroy cleanup through controlled fetch pending-request rejection
- packaged APK Web Streams smoke

## Deferred Work

- direct `Response.body` as a spec `ReadableStream`
- streaming request bodies
- streaming network responses
- full browser Web Streams parity
- worker transfer of streams
