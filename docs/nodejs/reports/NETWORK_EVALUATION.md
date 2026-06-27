# Controlled Network Evaluation

Date: 2026-06-16

Device: Sony XQ-AT72 (`QV710AF65F`), Android 12

Branch/package identity: `feature/nodejs-runtime`, `org.autojs.autojs6.nodejs`

Supersession note: Task 167 on `feature/nodejs-runtime-v1.1` later added an
AutoJs6-owned `require("axios")` adapter backed by controlled fetch. The v1 GA
live-network recommendation in this report still stands; only the old "no
adapter exists" axios statement has been superseded for v1.1 experiments.

## Recommendation

Keep controlled network experimental and off by default for Node Profile v1 GA.

The controlled `fetch` and `websocket` wrappers have enough fake-provider coverage to remain available for controlled
experiments, but they do not imply live Android network I/O, raw Node network modules, or axios support through desktop
Node adapters. Normal builds must continue to keep:

```properties
autojs.nodejs.network.experimental=false
```

Release docs should describe the stable contract as:

- raw `http`, `https`, `net`, `tls`, `dns`, `dns/promises`, and `dgram` authority remains denied
- global `fetch()` and `require("fetch")` are controlled bridge wrappers, not raw socket authority
- `require("websocket")` is a controlled AutoJs6 module, not a desktop `ws` clone
- live Android/OkHttp providers reject with `ERR_AUTOJS6_NETWORK_DISABLED` unless a future release explicitly enables the
  experimental gate
- axios is not a supported Node Profile v1 package unless it is explicitly configured to use a controlled fetch-compatible
  path or a future AutoJs6-owned adapter

## Commands Run

Focused network instrumentation:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFetchBridgeInstrumentationTest,org.autojs.autojs.engine.NodeWebSocketBridgeInstrumentationTest,org.autojs.autojs.engine.NodeRawNetworkPolicyInstrumentationTest,org.autojs.autojs.engine.NodeBridgePermissionManifestInstrumentationTest"
```

Result: passed, 15/15 tests.

Host-side packaged smoke verifier:

```powershell
.\gradlew.bat --console=plain :app:embeddedNodePackagedSmokeHostVerify "-Pautojs.embeddedNode.expectedAbis=arm64-v8a"
```

Result: passed. The verifier refreshed `app/build/reports/embedded-node/packaged-smoke-aggregate.txt` and recorded
`embedded.node.packaged.smoke.aggregate.ready=true`.

## Covered Cases

`NodeFetchBridgeInstrumentationTest` passed these controlled fetch cases:

- Kotlin bridge registry shape for `fetch.request`
- fake-provider `GET` and `POST`
- global `fetch()`
- `require("fetch")`
- shadowing protection against local `node_modules/fetch`
- timeout rejection
- abort rejection
- max response size rejection
- unsupported scheme rejection
- redirect limit rejection
- max concurrent request rejection
- service-destroy rejection and pending bridge cleanup
- diagnostics counters
- raw `http`, `https`, and `net` denial
- default Android provider rejection with `ERR_AUTOJS6_NETWORK_DISABLED`

`NodeWebSocketBridgeInstrumentationTest` passed these controlled WebSocket cases:

- registry/profile experimental state
- fake-provider connect, send, receive, and close
- `require("websocket")`
- shadowing protection against local `node_modules/websocket`
- timeout rejection
- unsupported scheme rejection
- max connection rejection
- message-size budget rejection
- service-destroy rejection and pending bridge cleanup
- diagnostics counters
- raw `net` and `tls` denial
- default Android provider rejection with `ERR_AUTOJS6_NETWORK_DISABLED`

`NodeRawNetworkPolicyInstrumentationTest` passed the raw network denial corpus:

- `http` and `node:http`
- `https` and `node:https`
- `net` and `node:net`
- `tls` and `node:tls`
- `dns` and `node:dns`
- `dns/promises` and `node:dns/promises`
- `dgram` and `node:dgram`
- local `node_modules` fixtures cannot shadow the policy

`NodeBridgePermissionManifestInstrumentationTest` passed the permission mapping and packaged mismatch gates:

- `network` capability maps to `android.permission.INTERNET`
- missing Android manifest permissions are derived from declared bridge capabilities
- packaged capability manifests report missing permission state before provider dispatch

## Packaged APK Permission Status

The refreshed host-side packaged verifier reports:

- success APK manifest inspected: true
- success APK declares `android.permission.INTERNET`: true
- declared relevant Android permissions include `android.permission.INTERNET`
- required Android manifest permissions ready: true
- packaged smoke aggregate ready: true

This is verifier evidence, not live network enablement. The inspected smoke APK does not declare a network capability
manifest, so the verifier also reports `android.permission.INTERNET` as an unused relevant permission. The current release
position remains: packaged APKs must not gain live Node network authority merely because the runtime contains controlled
network wrappers; explicit project metadata opt-in and live-provider gates remain follow-up work.

## Real Network Smoke

No live real-network smoke was run for this GA evaluation.

That is intentional for the default Node Profile v1 contract: the Android/OkHttp provider is gated by
`autojs.nodejs.network.experimental=false`, and the focused tests assert that normal Android providers reject before I/O.
Future live-provider promotion must add real network smoke for timeout, abort, response-size budget, unsupported scheme
handling, service destroy cancellation, packaged `INTERNET` opt-in, and raw-module denial in the same build.

## Axios Status

Axios is not a supported Node Profile v1 compatibility guarantee.

At the time of this GA evaluation there was no AutoJs6 `require("axios")`
builtin adapter, and desktop Node axios adapters normally depended on `http`,
`https`, streams, agents, and socket behavior outside the Safe Node Profile.
Task 167 later added a v1.1 experimental adapter owned by AutoJs6 and backed by
controlled fetch. That adapter still does not change the GA live-network
decision or allow desktop axios raw transport behavior.

## Release Decision

Node Profile v1 GA should document controlled network as:

- experimental
- disabled for live Android I/O by default
- controlled through AutoJs6 bridge wrappers
- fake-provider covered
- packaged-permission reporting covered
- not raw Node networking
- not desktop axios transport support by default

The next promotion gate should add live-provider device smoke under an explicit build flag, a packaged network opt-in
fixture, and an axios/fetch-adapter corpus only if axios is meant to be user-visible.
