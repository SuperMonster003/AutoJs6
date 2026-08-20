# Node Network Support

Status: stable and enabled by default in Node Profile v1.2.

## Two Network Surfaces

AutoJs6 exposes two deliberately distinct network surfaces.

The controlled surface consists of global `fetch()`, `require("fetch")`, the
AutoJs6 `axios` and `undici` compatibility adapters, and the controlled
WebSocket provider. Requests cross the host capability bridge and are governed
by declared `network` permission, URL policy, timeouts, cancellation, redirect
limits, response/message budgets, concurrency limits, diagnostics, and
provider-owned cleanup.

The native surface consists of Node's `dns`, `dns/promises`, `http`,
`https`, `net`, and `tls` builtins. These are genuine Node builtins and
follow Node API semantics. They run inside the Android app/process sandbox and
require the packaged app's Android network permission. They do not inherit the
controlled provider's per-request bridge budgets, so callers must set Node
timeouts, close agents/sockets/servers, and bound buffering themselves.

`dgram` remains disabled. Availability of either surface does not grant
filesystem, Java, shell, process, accessibility, or other AutoJs bridge
authority.

## Runtime Contract

Network support has no feature-specific Gradle properties or release-only
paths. Current wiring:

- BuildConfig: `NODEJS_NETWORK_ENABLED=true` and
  `NODEJS_RAW_NETWORK_MODULES_ENABLED=true`
- host/plugin request contract v2:
  `rawNodeNetworkModulesEnabled=true`
- profile snapshot: `featureStates.network=true` and
  `featureStates.rawNodeNetworkModules=true`
- controlled provider source: `runtime:stable-network`
- capability status: `stable`

The transport boolean exists only for host/plugin contract negotiation.
AutoJs6 sends it enabled, and project/package metadata cannot override it.

## Controlled Fetch Boundary

The controlled provider accepts `http:` and `https:` URLs and supports
Promise-based requests, `AbortController`, bounded redirects, headers, string
and byte bodies, and buffered text/JSON/ArrayBuffer responses. Its exact method,
body, timeout, response-size, concurrency, and redirect limits are reported in
the runtime profile and Doctor diagnostics.

The `axios` and `undici` modules are AutoJs6 compatibility adapters over this
provider. They are not unrestricted copies of their desktop packages and do not
expose custom socket agents, proxy tunnels, connector injection, or arbitrary
transport adapters.

When the bridge permission manifest is enforced, missing `network` permission
rejects before controlled I/O. Provider destruction or execution cancellation
must abort tracked calls and suppress callbacks into a destroyed runtime.

## Native Builtin Boundary

Bare and `node:` aliases resolve to the same native builtin. Local
`node_modules` entries cannot shadow builtin names. Native sockets and servers
remain constrained by Android/Linux application identity, SELinux, the app
manifest, available executables/certificates, and Node's own APIs.

Because native builtins do not use the controlled bridge, using them is an
explicit choice by script authors. Production scripts should define their own
timeouts, connection limits, body/stream budgets, TLS policy, cancellation, and
shutdown cleanup.

## Packaging

Node-enabled packages that perform network I/O must contain
`android.permission.INTERNET` according to the packaging manifest policy.
Packaging preflight reports the permission and the stable network capability
state. Release and host AAR generation never require a network feature flag.

## Verification

Current gates cover:

- controlled fake-provider request/response, timeout, abort, redirect, size,
  scheme, concurrency, diagnostics, and cleanup behavior;
- on-device loopback HTTP and WebSocket I/O through the Android provider;
- native `dns`, HTTP(S), TCP, and TLS availability for bare and `node:`
  imports;
- builtin shadow protection;
- packaged manifest and APK smoke;
- service-destroy and long-running resource cleanup.

Run live tests with only the device profile:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeLiveProviderGate "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyNodeLiveFetchGate "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyNodeLiveWebSocketGate "-Pautojs.nodejs.test.deviceProfile=sony"
```
