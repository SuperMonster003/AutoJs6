# Node Profile v1.1 Controlled Network Promotion Evaluation

Date: 2026-06-17

Branch: `feature/nodejs-runtime-v1.1`

Device: Sony XQ-AT72 `QV710AF65F`, Android 12

Package identity: production, `org.autojs.autojs6`

## Recommendation

Keep controlled network experimental for Node Profile v1.1.

Do not promote controlled `fetch`, controlled `websocket`, or axios-shaped
network compatibility to partial support yet. The current bridge wrappers and
default-denial policy are healthy under production identity, but the evidence
still proves a gated experiment rather than a user-visible stable network
contract:

- the focused production-identity network suite passes wrapper, budget, service
  destroy, diagnostics, and denial cases;
- packaged smoke host verification remains green and reports packaged artifacts
  plus template readiness;
- there is still no live Android/OkHttp network smoke under an explicitly
  enabled `autojs.nodejs.network.experimental=true` profile;
- there is no packaged network opt-in fixture proving project metadata,
  `android.permission.INTERNET`, and live provider behavior together;
- axios is not backed by an AutoJs6-owned adapter and desktop Node adapters
  still depend on denied raw network modules;
- privacy review and release text for live network behavior are not complete.

Normal builds must continue to keep:

```properties
autojs.nodejs.network.experimental=false
```

## Commands Run

Focused controlled network instrumentation:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest `
  "-Pautojs.nodejs.identity=production" `
  "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFetchBridgeInstrumentationTest,org.autojs.autojs.engine.NodeWebSocketBridgeInstrumentationTest,org.autojs.autojs.engine.NodeRawNetworkPolicyInstrumentationTest,org.autojs.autojs.engine.NodeBridgePermissionManifestInstrumentationTest"
```

Result:

```text
15 tests, 0 failures, 0 errors, 0 skipped
```

Host-side packaged smoke verifier:

```powershell
.\gradlew.bat --console=plain :app:embeddedNodePackagedSmokeHostVerify `
  "-Pautojs.nodejs.identity=production" `
  "-Pautojs.embeddedNode.expectedAbis=arm64-v8a"
```

Result:

```text
BUILD SUCCESSFUL
embedded.node.packaged.smoke.pull.ready=true
embedded.node.packaged.smoke.aggregate.ready=true
```

The refreshed packaged aggregate report was written to
`app/build/reports/embedded-node/packaged-smoke-aggregate.txt` at
`2026-06-17T13:09:11.275361200+08:00[Asia/Shanghai]`.

## Evidence Matrix

| Gate | Status | Notes |
| --- | --- | --- |
| controlled `fetch` wrapper | Pass as experiment | Fake-provider `GET`/`POST`, global `fetch`, and `require("fetch")` pass. |
| controlled WebSocket wrapper | Pass as experiment | Fake-provider connect, send, receive, close, `require("websocket")`, and diagnostics pass. |
| timeout policy | Pass | Focused fetch and WebSocket tests cover timeout rejection. |
| abort policy | Pass for fetch | Fetch abort rejection is covered. WebSocket cancellation remains tied to timeout/destroy paths. |
| response/message size budget | Pass | Fetch response-size and WebSocket message-size rejections are covered. |
| redirect limit | Pass for fetch | Fetch redirect budget rejection is covered. |
| unsupported scheme | Pass | Fetch and WebSocket reject unsupported schemes. |
| concurrent request/connection limits | Pass | Fetch max concurrent request and WebSocket max connection rejections are covered. |
| service destroy cancellation | Pass for fake provider paths | Fetch and WebSocket destroy cleanup and pending bridge cleanup pass. |
| Android provider default denial | Pass | Normal providers reject with `ERR_AUTOJS6_NETWORK_DISABLED` while the experimental flag is false. |
| raw network modules | Pass as denial | `http`, `https`, `net`, `tls`, `dns`, `dns/promises`, and `dgram` plus `node:` aliases remain denied. |
| policy shadowing | Pass | Local `node_modules` fixtures cannot shadow controlled or denied modules. |
| capability to Android permission mapping | Pass | `network` maps to `android.permission.INTERNET`; mismatch detection is covered. |
| packaged smoke verifier | Pass | Pulled and verified current packaged smoke APK artifacts and template readiness. |
| packaged network opt-in fixture | Missing | No fixture proves declared network capability, packaged manifest, and live provider together. |
| live real-network smoke | Missing | No explicit live Android/OkHttp request path was run for v1.1 promotion. |
| axios compatibility | Not supported | No AutoJs6-owned adapter or fetch-adapter corpus is admitted. |
| privacy/release review | Missing | Live network behavior needs explicit user-facing privacy and permission review before promotion. |

## Packaged APK Position

The packaged verifier evidence is useful but not sufficient for promotion. It
proves current packaged artifacts can be pulled and statically verified, and the
capability manifest tests prove `network` maps to `android.permission.INTERNET`.
It does not prove a packaged app can opt into controlled live network I/O.

Before partial promotion, add a packaged network fixture that:

- declares the `network` Node capability in project metadata;
- includes `android.permission.INTERNET` because of that declaration;
- runs a bounded live request only when `autojs.nodejs.network.experimental=true`;
- proves the same packaged app rejects before I/O when the flag is false;
- records diagnostics for timeout, abort, response budget, redirect budget, and
  service destroy cancellation.

## Axios Decision

Axios remains outside the v1.1 support contract.

The existing runtime contains legacy Rhino-facing axios integration, but Node
Profile network support cannot inherit that as a desktop Node guarantee. Node
axios adapters commonly depend on `http`, `https`, socket agents, streams, and
DNS behavior that the Safe Node Profile intentionally denies. Axios can be
reconsidered only after an AutoJs6-owned adapter or pinned fetch-compatible
adapter has its own corpus and packaged gate.

## v1.1 Decision

For Node Profile v1.1:

- keep `autojs.nodejs.network.experimental=false` by default;
- keep controlled `fetch` and `websocket` documented as experimental bridge
  wrappers;
- keep raw Node networking denied for bare names, `node:` aliases, and
  shadowing attempts;
- do not claim axios compatibility;
- do not update `NODE_PROFILE_V1_STABLE_SURFACE.md` to include live network
  support;
- require explicit promotion evidence before enabling Android/OkHttp live I/O.

## Next Promotion Gate

Before reconsidering partial promotion, add:

- explicit live-provider smoke under an approved device profile and
  `autojs.nodejs.network.experimental=true`;
- local deterministic endpoint or approved test endpoint coverage for timeout,
  abort, redirect, response-size, headers, body, TLS, and service destroy;
- packaged network opt-in fixture with manifest permission verification;
- privacy review covering user-visible permission and network disclosure;
- axios/fetch-adapter corpus only if axios is meant to be user-visible;
- release-note text that distinguishes controlled wrappers from raw Node
  networking.
