# AutoJs6 Node Profile v1 Stable Surface

This document freezes the GA-facing capability surface for AutoJs6 Node Profile v1. It is a release contract for the
Safe Node Profile, not a claim of full desktop Node.js compatibility.

The detailed source of truth remains:

- runtime registry: `NodeRuntimeCapabilities`
- JavaScript profile: `require("autojs6:profile")`
- generated reports: `:app:generateNodeCompatibilityProfile`
- narrative profile: [../COMPATIBILITY_PROFILE.md](../COMPATIBILITY_PROFILE.md)

## Status Categories

- Stable: the behavior is part of the v1 compatibility contract.
- Partial: the module or feature exists with a documented subset.
- Experimental: available only behind an explicit flag or focused test mode.
- Disabled: blocked by policy in normal builds.
- Unsupported: outside the v1 product scope.

Changing a category requires tests, compatibility profile updates, release notes, and the promotion policy in
[../EXPERIMENTAL_PROMOTION_POLICY.md](../EXPERIMENTAL_PROMOTION_POLICY.md).

## Stable

Runtime and routing:

- embedded backend as the default Node backend
- backend switches: `rhino`, `native`, `embedded`, `auto`
- native one-shot backend retained as a regression oracle
- isolated `:nodejs_embedded` script process
- `"nodejs";` and `"node";` directives
- `.node.js`, `.cjs`, and `.mjs` routing into the Node backend
- `project.json` Node entries
- packaged APK Node entries
- encrypted Node script metadata
- `require("autojs6:profile")` read-only diagnostics
- explicit embedded execution queue behavior and `ERR_AUTOJS6_NODE_ENGINE_BUSY`

CommonJS and resolver:

- `require()`, `module.require()`, `module.exports`, and `exports`
- JSON modules
- local `.js`, `.cjs`, and `.json` modules
- `node_modules` traversal
- scoped packages
- package `main`
- package `exports`
- package `imports`
- package self-reference
- CommonJS package condition order: `require`, `node`, `default`
- builtin shadowing protection
- resolver trace and resolver visualizer diagnostics
- module source budget checks

Filesystem policy:

- scoped `fs` sync and callback APIs covered by the embedded regression suite
- callback-to-Promise usage through `util.promisify`
- scoped file source loading from the working directory and project root
- canonical denial for absolute paths, NUL bytes, `..`, `/proc`, `/sys`, `/dev`, realpath escape, and symlink escape
- package target containment for `exports` and `imports`

Stable low-risk builtins and globals:

- `path`, `node:path`, `path/posix`, `path/win32`
- `util`, `node:util`, `util/types`
- `events`, `node:events`
- `assert`, `node:assert`, `assert/strict`
- `querystring`, `node:querystring`
- `string_decoder`, `node:string_decoder`
- `timers`, `node:timers`
- `buffer`
- `console`
- safe timer globals
- safe `process` identity and event subset

Packaging and release:

- native library injection for `libnode.so`, `libautojs6-node.so`, and `libc++_shared.so`
- ABI pruning for `arm64-v8a`, `armeabi-v7a`, and `x86_64`
- `x86` fail-fast policy
- packaged `NativeNodeEmbeddedScriptService` manifest verification
- packaged diagnostic reports
- packaged smoke host verifier
- release flavor policy: `nodeFull`, `nodeMini`, `rhinoOnly`
- native dependency report and symbol archive report
- Node Doctor host reports

Bridge protocol and stable module routing:

- asynchronous bridge request/response protocol
- fakeable capability providers
- per-execution pending-call timeout and service-destroy rejection
- bridge capability declarations through `node.permissions` and `autojs6.node.permissions`
- bridge limit declarations through `node.bridgeLimits` and `autojs6.node.bridgeLimits`
- bridge resource registry diagnostics
- stable module names and Promise-shaped APIs for:
  - `toast`
  - `app`
  - `engines`
  - `dialogs`
  - `accessibility`
  - `media_projection`
  - `image` and `images`
  - `clipboard`
  - `device`
  - `shell`
  - `fetch`
  - `websocket`
  - `work_manager`

The bridge module API shapes are stable. Some Android-backed providers are still partial or reject in normal builds, as
listed below.

## Partial

Runtime and module APIs:

- `.mjs` routes to Node, but default ESM execution is rejected.
- `node:module` exposes only the documented safe CommonJS subset.
- `fs/promises` exposes a scoped limited surface.
- `stream` exposes the documented MVP surface, without unrestricted network or process streams.
- `zlib` exposes documented buffer-oriented support where available; streaming zlib is partial.
- `crypto`, `os`, `url`, `dns`, `http`, `https`, `net`, `tls`, `tty`, `readline`, `v8`, and related modules are limited wrappers for compatibility, privacy, or stable denial behavior.

Bridge providers:

- `engines` exposes serialized snapshots and controlled spawn APIs, but not raw `ScriptEngine` or `ScriptExecution`.
- `dialogs` has stable JavaScript shape and fake-provider coverage; current Android provider may reject until a UI provider is connected.
- `accessibility` exposes selector descriptors and JSON node snapshots; current Android provider rejects privileged actions unless a safe provider is connected.
- `media_projection` and `image` expose the protocol and handle shapes; real screen capture and image-provider behavior remain gated by provider availability.
- `clipboard` and `device` expose privacy-safe Android provider paths plus packaged fallback behavior.
- `shell` is controlled and non-interactive; it is not Node `child_process`.
- `work_manager` registers controlled metadata only; it does not execute background Node jobs yet.
- `fetch` and `websocket` expose controlled wrappers and fake-provider coverage; normal builds reject live Android network I/O by default.

## Experimental

These features are not stable v1 behavior and must remain behind explicit gates:

- ESM execution: `autojs.nodejs.esm.experimental=false`
- dynamic import: `autojs.nodejs.dynamicImport.experimental=false`
- worker threads: `autojs.nodejs.worker_threads.experimental=false`
- controlled live network provider: `autojs.nodejs.network.experimental=false`
- WebSocket live provider when backed by network I/O
- Java/Android interop allowlist: `autojs.nodejs.javaInterop.experimental=false`
- Rhino compatibility shim
- long-running Node service mode
- scheduled or background Node execution through WorkManager

## Disabled

These surfaces are blocked by policy in normal v1 builds:

- unrestricted native addon loading
- unrestricted `child_process`
- unrestricted raw `http`, `https`, `net`, `tls`, `dns`, and `dgram`
- unrestricted WebSocket sockets or HTTP upgrade authority
- unrestricted `worker_threads`
- unrestricted `vm`
- unrestricted Java or Android object access
- inspector/debugger server
- `process.binding`
- `process.dlopen`
- `process._linkedBinding`
- `process.getBuiltinModule`
- raw Android scheduler, broadcast, content provider, account, telephony, VPN, camera, audio, display, print, and system manager access

## Unsupported

These are outside the v1 Safe Node Profile:

- full desktop Node.js compatibility
- runtime `npm install`
- lifecycle scripts and postinstall execution
- native `.node` addons
- custom ESM loaders
- loader hooks
- unrestricted source inspection through inspector
- unrestricted network servers
- interactive shells or process objects
- raw Android `Context`, `Intent`, `AccessibilityNodeInfo`, `MediaProjection`, `Image`, or Java object graph access

## GA Freeze Requirements

Before GA publication, the maintainer must verify:

- generated compatibility profile matches this surface
- [NODE_PROFILE_V1_GA.md](NODE_PROFILE_V1_GA.md) references this document
- [NODE_PROFILE_V1_KNOWN_ISSUES.md](NODE_PROFILE_V1_KNOWN_ISSUES.md) lists known limitations and workarounds
- [../RELEASE_CHECKLIST.md](../RELEASE_CHECKLIST.md) includes the stable surface and promotion-policy review
- OnePlus OPD2413 release-gate result is recorded in the RC or GA report
