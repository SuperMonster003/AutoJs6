# Node Profile v1.1 Capability Matrix

This matrix records the migration target from Node Profile v1.0 to v1.1. A
feature cannot move to `partial` or `stable` without the listed tests, docs,
security review, and packaged APK decision.

| Feature | v1.0 status | v1.1 target | Required tests | Required docs | Security review | Packaged APK impact |
| --- | --- | --- | --- | --- | --- | --- |
| ESM | experimental, default off | partial, default on | `.mjs`, `type=module`, static import/export, TLA, CJS interop, moduleSources graph, denial paths | `COMPATIBILITY_PROFILE.md`, `design/ESM.md`, this matrix | Scoped fs, builtin allowlist, package target denial | Packaged `.mjs` local `.mjs`/CJS graph smoke required; dynamic import graph gated |
| Dynamic import | experimental, default off | partial, default on | Explicit-off denial, local `.mjs`/CJS, allowed builtin, package import condition, disabled builtin, escape/URL/absolute denial | `design/ESM.md`, error catalog | Loader bypass, scoped fs, builtin allowlist, package target denial | Packaged local `.mjs`/CJS dynamic import smoke required |
| Buffer | limited builtin wrapper | partial | `require("buffer")`, `require("node:buffer")`, ESM `node:buffer`, global consistency, `from`/`alloc`/`concat`, shadow denial | `COMPATIBILITY_PROFILE.md`, this matrix | Builtin routing must bypass `node_modules/buffer`; no native memory escape beyond Node-managed Buffer | Existing packaged ESM/dynamic import smokes rely on encrypted runtime sources; no manifest change |
| Core builtin feature-detect modules | mixed limited wrappers | partial | `NodeCoreBuiltinCompatibilityInstrumentationTest`, `NodeRealNpmCompatInstrumentationTest` core feature-detect fixture | `COMPATIBILITY_PROFILE.md`, `TESTING.md`, this matrix | No `process.getBuiltinModule()` bypass, no `EventTarget` expansion through `events`, disabled builtin denial unchanged | No manifest change; pure JS package corpus only |
| Stream primitives | partial | partial | Async iteration, callback/promise pipeline, callback/promise finished, Web Streams adapters, backpressure, destroy propagation, shadow denial | `design/STREAM.md`, `COMPATIBILITY_PROFILE.md` | Stream wrappers must not add raw network, process, native addon, or unscoped fs authority | Existing packaged stream smoke coverage; no manifest change |
| Web Streams | partial | partial | `stream/web`, global constructors, Blob stream, Node/Web adapters, fetch `response.body.stream()`, service-destroy via fetch bridge | `design/WEB_STREAMS_V1_1.md`, `design/NETWORK.md` | No raw network streaming, no unscoped fs, no native handle exposure, fetch response budgets preserved | Existing packaged Web Streams smoke coverage; no manifest change |
| Fetch | experimental, default off | experimental | Fake provider GET/POST, timeout, abort, size, raw http denial | `design/NETWORK.md` | Capability manifest, Android INTERNET gating | No INTERNET solely by runtime presence |
| WebSocket | experimental, default off | experimental | Fake connect/send/receive/close, size, timeout, raw socket denial | `design/WEBSOCKET.md` | Connection/resource limits | No INTERNET solely by runtime presence |
| fs streams | partial | partial | Read/write/pipe, scoped escape and `/proc`/`/sys`/`/dev` denial, active stream timeout/service cleanup | `design/STREAM.md` | Scoped fs, symlink policy, active stream registry cleanup | Existing packaged smoke coverage |
| zlib streams | partial | partial | gzip/gunzip, deflate/inflate, fs read->gzip->write, invalid input, active stream timeout/service cleanup | `design/ZLIB.md` | Input/output budgets, active stream registry cleanup | Existing packaged smoke coverage |
| worker_threads | experimental, default off | experimental, default off | Disabled default, debug-only worker probes | `design/WORKER_THREADS.md` | Worker isolation, bridge denial, quotas | No packaged default support |
| OCR | stable protocol, fail-closed Android provider | partial | Fake provider, handle validation, capability denial, provider unavailable | `design/BRIDGE_V1_1_MODULES.md`, compatibility profile | Opaque image handles, provider-owned resources | Packaged fake/provider-denial smoke only |
| storage | planned | planned | Put/get/remove/clear, namespace isolation | bridge design doc | Project/package isolation | Future packaged storage smoke |
| notifications | planned | planned | Fake provider, permission denial, cancel cleanup | bridge design doc | Android permission and owned notification cleanup | Manifest permission mapping required |
| sensors | planned | planned | Fake subscription, rate/limit cleanup | bridge design doc | Sensor privacy and resource limits | Future packaged fake smoke |
| work_manager | stable protocol metadata MVP | partial later | Schedule/cancel/list metadata, invalid path denial | `design/WORK_MANAGER.md` | Task path and capability review | Existing metadata only; execution later |
| UI bridge | design-gated | planned | Provider lifecycle, no raw View leak | `design/UI_BRIDGE.md` | Activity ownership and lifecycle | No default packaged UI until provider exists |
| Plugin system | design-gated | planned | JS-only loading, `.node` rejection, path escape denial | `design/PLUGIN_SYSTEM.md` | Capability isolation and signer/install review | No Node plugin runtime in v1.1 first batch |
| Native addon | unsupported | unsupported | `.node` denial through every resolver path | compatibility profile | Critical: no dlopen/process bypass | Must remain denied |
| Child process | disabled | disabled | `child_process` and `node:child_process` denial | compatibility profile | Raw process spawning denied | Use `shell` bridge only |
| Raw network modules | disabled/limited denial | disabled/limited denial | `http`, `https`, `net`, `tls`, `dns`, `dgram` denial | network docs | No raw sockets | No raw network permissions |
| Java interop | experimental, default off | experimental, default off | Allowlist and denial probes | `design/JAVA_INTEROP.md` | Critical Android object boundary | No packaged default support |

## Promotion Gate Rule

Moving any row to `partial` or `stable` requires:

- A focused instrumentation or host verification gate.
- A negative security test for the closest bypass path.
- Compatibility profile registry/doc updates.
- Packaged APK decision recorded as supported, diagnostic-only, or blocked.
- No regression to scoped fs, Rhino compatibility, backend switches, native
  addon denial, or process/network denial.
