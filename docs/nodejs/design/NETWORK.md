# Network Controlled Fetch MVP v1

Status: controlled fetch MVP implemented, live network provider gated. `autojs.nodejs.network.experimental=false` is the default, so normal builds keep real network I/O disabled while the JavaScript API, bridge protocol, diagnostics, and fake-provider tests are in place.

## Goal

AutoJs6 should eventually support a controlled HTTP client for automation scripts without exposing the raw desktop Node networking stack.

The Safe Node Profile must keep these separate:

- compatibility shims that let packages detect modules
- stable denial errors for unsafe operations
- an explicit controlled network capability that is permissioned, cancellable, bounded, and package-aware

Task 40 froze the design and diagnostics. Task 41 adds the controlled `fetch`/`require("fetch")` wrapper and bridge request shape without opening raw Node networking modules.

## Feature Flag

The gate flag is:

```properties
autojs.nodejs.network.experimental=false
```

Current wiring:

- Gradle property default: `false`
- BuildConfig field: `NODEJS_NETWORK_EXPERIMENTAL_ENABLED`
- Kotlin capability registry: `NodeRuntimeCapabilities.NETWORK_EXPERIMENTAL_PROPERTY`
- JavaScript profile snapshot: `require("autojs6:profile").experimentalFlags.network === false`

The flag gates the live Android/OkHttp provider. With the flag off, the JavaScript wrapper and bridge module exist, but the Android provider rejects requests with `ERR_AUTOJS6_NETWORK_DISABLED` before network I/O.

## Current Default Behavior

Default behavior with the flag off:

- global `fetch()` is an AutoJs6 controlled wrapper and rejects with the current network-disabled policy code shape when routed to the default Android provider
- `require("fetch")` returns the same controlled wrapper shape for CommonJS scripts
- `http`, `https`, `net`, `tls`, `dns`, and `dns/promises` remain limited compatibility modules whose I/O operations reject with `ERR_AUTOJS6_NETWORK_DISABLED`
- `dgram` remains a disabled builtin
- no raw socket, server, DNS query, TLS session, datagram, or unrestricted HTTP request is permitted
- packaged APK smoke must keep the same denial behavior

Network-related module names may be recognized for feature detection. Recognition is not permission to perform I/O.

## Implemented MVP Shape

The implemented MVP is a controlled `fetch` client, not raw Node `http` or `https`.

Reasons:

- `fetch` is Promise-first and maps naturally to bridge cancellation
- `AbortController.signal` gives a clear cancellation contract
- request and response bodies can be budgeted before exposing stream semantics
- the API can be backed by Android or AutoJs6 providers without giving Node direct socket authority

The MVP should not shadow user-installed `axios` with a builtin `require("axios")`. If axios compatibility is needed later, it should use an explicit AutoJs6 adapter built on the same controlled fetch provider.

User-facing entries:

```js
const response = await fetch("https://example.com/data.json", {
  method: "GET",
  timeoutMs: 30000,
  maxResponseBytes: 1048576,
  maxRedirects: 5,
  signal: abortController.signal,
});
```

CommonJS scripts can also use:

```js
const fetch = require("fetch");
const response = await fetch("https://example.com/data.json", {
  method: "POST",
  body: JSON.stringify({ ok: true }),
  headers: { "content-type": "application/json" },
  timeoutMs: 30000,
  maxResponseBytes: 1048576,
  maxRedirects: 5,
});
```

The wrapper supports:

- `GET` and `POST`
- object or array headers
- string, `Buffer`, `ArrayBuffer`, typed-array, and Blob-like bodies
- `timeoutMs`
- `AbortController.signal`
- `maxResponseBytes`
- `maxRedirects`
- `Response.text()`, `Response.arrayBuffer()`, and `Response.json()`
- `Response.body.stream()` for bounded Web Stream reads
- `require("fetch").policy`
- `require("fetch").diagnostics()`

The first implementation intentionally does not add a builtin `require("axios")` adapter.

## Authority Boundary

Node must not open sockets directly.

Future network requests should flow through:

```text
JS fetch wrapper
  -> AutoJs6 bridge request
  -> NativeNodeEmbeddedScriptService
  -> app-owned network provider
  -> Android/OkHttp HTTP stack
```

The current Android provider owns:

- Android permission checks
- request execution
- cancellation
- TLS configuration
- proxy policy
- response size enforcement
- service-destroy cleanup

The JavaScript wrapper owns only argument normalization, Promise lifecycle, and stable error conversion.

Normal builds still reject at provider entry because `BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED` is false.

## Request Policy

Controlled fetch is bounded before it reaches any raw network authority. Current policy:

- max concurrent user fetches per script: `4`
- default timeout: `30000` ms
- hard timeout cap: `120000` ms
- default max response body: `1048576` bytes
- hard max response body: `16777216` bytes
- default redirect count: `5`
- hard redirect count: `10`
- allowed URL schemes: `http:` and `https:` only

The JavaScript wrapper rejects `file:`, `content:`, `about:`, `data:`, malformed URLs, redirect targets outside `http:`/`https:`, GET bodies, redirect overflow, and concurrent-request overflow with stable `ERR_AUTOJS6_NETWORK_*` errors. The Android provider repeats the core policy for live OkHttp requests so fake-provider behavior and live-provider behavior do not diverge.

Diagnostics are intentionally exposed as counters, not raw provider handles:

```js
const fetch = require("fetch");

console.log(fetch.policy.maxConcurrentRequests);
console.log(fetch.diagnostics());
```

The diagnostics snapshot includes active request count, max active request count, timeout count, policy rejection count, abort count, and completed count. Native live bridge results also append provider-side fetch counters under `embedded_script.fetch_*`.

## Raw Module Policy

These raw modules remain disabled for I/O:

- `http`
- `https`
- `net`
- `tls`
- `dns`
- `dns/promises`
- `dgram`

Controlled fetch support must not imply support for:

- `http.Agent`
- raw sockets
- socket servers
- DNS resolver APIs
- TLS socket APIs
- UDP datagrams
- HTTP upgrade or CONNECT tunnels

## Android Permission Policy

The main app may already declare `android.permission.INTERNET` for other product features. That does not make network part of the Safe Node Profile.

Packaged APKs should include `android.permission.INTERNET` for Node only when project metadata explicitly opts into controlled network. The packaged verifier now reports whether the inspected manifest declares `android.permission.INTERNET`; a later packaging task should turn that diagnostic into an opt-in gate:

- no network opt-in: Node network disabled and no Node-driven INTERNET requirement
- network opt-in: INTERNET permission present, controlled client enabled, raw modules still denied

Example future project metadata:

```json
{
  "node": {
    "network": {
      "enabled": true,
      "client": "fetch",
      "timeoutMs": 30000,
      "maxResponseBytes": 1048576
    }
  }
}
```

## Timeout

Requests have a default timeout. Current defaults:

- default timeout: `30000` ms
- hard upper bound: `120000` ms unless a later release gate justifies more

Timeout rejects the JavaScript Promise with `ERR_AUTOJS6_NETWORK_TIMEOUT` and cancels the provider request.

## Abort

`AbortController.signal` is supported in the controlled fetch wrapper.

Required behavior:

- abort before dispatch rejects without starting provider I/O
- abort during dispatch cancels provider I/O
- abort after completion is ignored
- service destroy behaves like a provider-side abort for every pending request

## Redirects

The live provider does not follow redirects implicitly. The controlled JavaScript wrapper receives each 3xx response, validates the `Location` target against the same scheme policy, and dispatches the next request only while the request still has redirect budget remaining.

Current behavior:

- default redirect budget: `5`
- hard redirect cap: `10`
- redirect overflow rejects with `ERR_AUTOJS6_NETWORK_POLICY_DENIED`
- malformed or unsupported redirect targets reject with `ERR_AUTOJS6_NETWORK_INVALID_URL`
- `redirect: "manual"` returns the 3xx response to user code
- `redirect: "error"` rejects on the first redirect

## Body Budgets

The first implementation is buffer-oriented, not network-stream-oriented.
`Response.body` is the current Blob-backed body object, so scripts that need a
Web Stream should call `response.body.stream()`. Direct spec-style
`Response.body` as a `ReadableStream`, streaming request bodies, and streaming
network responses remain deferred to a later design gate.

Recommended defaults:

- default max response body: `1048576` bytes
- hard max response body: `16777216` bytes
- request body budget documented separately before uploads are enabled

Responses exceeding the active budget reject with `ERR_AUTOJS6_NETWORK_RESPONSE_TOO_LARGE` before returning partial data to user code.

## TLS And Certificates

MVP TLS behavior should use the Android platform trust store.

Out of scope for v1:

- accepting invalid certificates
- custom CA bundles
- certificate pinning
- client certificates
- exposing raw certificate objects
- TLS socket customization

Any certificate customization needs a separate design gate because it affects privacy, packaging, and user trust.

## Proxy Policy

MVP should use the Android/system proxy behavior of the chosen provider.

Out of scope for v1:

- per-request proxy configuration
- SOCKS proxy support
- proxy authentication APIs
- proxy bypass lists

## Background And Foreground Behavior

The first network client should be suitable for short, script-scoped requests.

It must not add:

- background upload/download jobs
- wake locks
- long-running transfer services
- foreground service promotion
- retry-after-reboot behavior

Long-running downloads or background sync belong in a separate task and should coordinate with the scheduled/background task design gate.

## Service Destroy Cancellation

When `NativeNodeEmbeddedScriptService` stops or is destroyed:

- all pending network bridge calls must be cancelled
- JavaScript Promises must reject
- provider handles must be released
- no callback may re-enter a destroyed Node environment

This must be tested with fake providers before any live network provider is exposed.

## Error Shape

Existing default denials use:

- `ERR_AUTOJS6_NETWORK_DISABLED`
- current fetch legacy code shape where applicable

Controlled client errors include:

- `ERR_AUTOJS6_NETWORK_ABORTED`
- `ERR_AUTOJS6_NETWORK_TIMEOUT`
- `ERR_AUTOJS6_NETWORK_RESPONSE_TOO_LARGE`
- `ERR_AUTOJS6_NETWORK_INVALID_URL`
- `ERR_AUTOJS6_NETWORK_PERMISSION_DENIED`
- `ERR_AUTOJS6_NETWORK_POLICY_DENIED`

Bridge transport failures should continue to use bridge-level errors such as `ERR_AUTOJS6_BRIDGE_TIMEOUT` and `ERR_AUTOJS6_BRIDGE_PROCESS_DEAD`.

## Test Gate

Before live network capability is enabled in release builds, tests must cover:

- flag off keeps global fetch and raw modules denied
- `BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED` is false in normal builds
- `require("autojs6:profile").experimentalFlags.network` is false
- raw modules remain denied even if live controlled fetch is enabled
- fake provider success for GET and POST: covered by `NodeFetchBridgeInstrumentationTest`
- object/array headers, string/Buffer/Uint8Array request bodies, and `Response.text()`/`json()`/`arrayBuffer()`: covered by `NodeFetchBridgeInstrumentationTest`
- timeout cancellation: covered by `NodeFetchBridgeInstrumentationTest`
- abort before dispatch and during dispatch: covered by `NodeFetchBridgeInstrumentationTest`
- response body budget rejection: covered by `NodeFetchBridgeInstrumentationTest`
- max concurrent request rejection: covered by `NodeFetchBridgeInstrumentationTest`
- unsupported scheme rejection: covered by `NodeFetchBridgeInstrumentationTest`
- redirect limit, `redirect: "manual"`, and `redirect: "error"` behavior: covered by `NodeFetchBridgeInstrumentationTest`
- service destroy rejection and active-count cleanup: covered by `NodeFetchBridgeInstrumentationTest`
- diagnostics counters for active, timeout, policy rejection, and abort paths: covered by `NodeFetchBridgeInstrumentationTest`
- raw modules still denied: covered by `NodeFetchBridgeInstrumentationTest` and security regression tests
- default Android provider disabled with the flag off: covered by `NodeFetchBridgeInstrumentationTest`
- service destroy cancellation: provider destroy cancels tracked OkHttp calls; broader live-provider lifecycle stress remains a follow-up
- packaged APK manifest behavior with and without opt-in: current verifier reports INTERNET presence; opt-in gating remains a follow-up

## Non-Goals

Task 41 does not enable:

- global network access
- `node:http`
- `node:https`
- `node:net`
- `node:tls`
- `node:dns`
- `node:dgram`
- userland axios adapter
- request streaming
- response streaming
- background transfers
- custom TLS or proxy configuration
