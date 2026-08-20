# AutoJs6 Node.js Integration Plan

Status: Node Profile v1.2 is the current integration baseline. Implemented Node
capabilities are compiled in and enabled for ordinary Debug, Release, host API,
and packaged-AAR builds. The detailed deployment sequence remains in
[COMPLETION_ROADMAP.md](COMPLETION_ROADMAP.md).

## Runtime Topology

AutoJs6 selects one of four release/runtime arrangements:

- `nodePlugin`: the host discovers the independently installed Node runtime
  plugin and talks to it through published API contract v2.
- `nodeFull`: the host packages all supported Embedded Node ABIs.
- `nodeMini`: the host packages an explicitly selected supported ABI subset.
- `rhinoOnly`: no Node payload is packaged and Rhino remains the execution
  backend.

The Node runtime executes in the isolated `:nodejs_embedded` process. Host-owned
Android capabilities are reached through versioned bridge providers; project
files and encrypted module graphs are supplied through scoped transports rather
than unrestricted host filesystem access.

Supported Node ABIs are `arm64-v8a`, `armeabi-v7a`, and `x86_64`. `x86` remains
unsupported and fails packaging preflight.

## Stable Capabilities

These capabilities are part of every Node-enabled build:

| Capability | Product boundary |
| --- | --- |
| ESM | `.mjs`, package `type=module`, static imports/exports, top-level await, `import.meta`, and scoped local resolution. |
| Dynamic import | Local/package/builtin imports through the scoped loader; remote URL imports stay denied. |
| Controlled network | `fetch`, AutoJs6 `axios`/`undici` adapters, and provider-owned HTTP I/O with permission, timeout, redirect, and size policy. |
| WebSocket | Provider-owned `ws:`/`wss:` sessions with connection, queue, message, timeout, and lifecycle limits. |
| Raw network builtins | Native `dns`, `dns/promises`, `http`, `https`, `net`, and `tls`, subject to Android sandbox/manifest policy and caller-managed Node limits. |
| Workers | Native `worker_threads` with a bounded worker profile, scoped source loading, smaller authority than the parent, and cleanup accounting. |
| Child processes | Native `child_process`, subject to Android sandbox and caller-managed validation, timeout, cancellation, stdio, and cleanup. |
| Java interop | `require("java")`, `$autojs.java`, and explicit Rhino proxy installation through a strict class/member allowlist and opaque handles. |

There are no Node capability opt-in Gradle properties. Stable host/plugin request
keys are `esmEnabled`, `dynamicImportEnabled`,
`rawNodeNetworkModulesEnabled`, `workerThreadsEnabled`,
`childProcessEnabled`, and `javaInteropEnabled`. The host always sends the
stable set enabled; project/package feature overrides are rejected.

## Boundaries That Remain

Formal availability is separate from authority:

- bridge calls require declared runtime capabilities and provider support;
- network I/O requires runtime network authorization and Android
  `android.permission.INTERNET` where packaging rules require it;
- filesystem access remains scoped to the execution sandbox;
- Java interop does not expose reflection, class loaders, unrestricted package
  traversal, native-library loading, `Runtime`, or `ProcessBuilder`;
- worker scopes do not inherit unrestricted AutoJs bridge, Java, shell,
  Inspector, or nested-worker authority;
- process, socket, worker, response, message, source, memory, and duration
  budgets remain enforced;
- service stop, timeout, cancellation, and native failure must close all
  provider-owned resources.

Inspector remains disabled. Native addons and unrestricted WASI remain
unsupported. Scoped `fs`, `zlib`, and `vm` retain their documented partial
status; formalizing the shipped capabilities does not overstate those surfaces.

## Build And Artifact Contract

Ordinary build commands must require no feature-specific properties:

```powershell
.\gradlew.bat :plugin-api:nodejs-api:bundleReleaseAar
.\gradlew.bat :app:assembleAppRelease
```

Release and AAR creation must not reject, strip, or rewrite the stable capability
set. Generated `BuildConfig` fields use stable names and are `true` for every
Node-enabled variant. Published plugin artifacts use runtime contract v2 and
versioned API/catalog/runtime-kit manifests with locked SHA-256 hashes.

## Verification Before Deployment

Each deployment increment should verify, as applicable:

1. host and plugin API contract tests;
2. Kotlin/Java compilation and Android instrumentation compilation;
3. all three native ABIs and runtime-kit payload hashes;
4. compatibility profile generation and verification;
5. type declarations, examples, packaging preflight, and AAR contents;
6. live loopback network/WebSocket and device worker/process tests on the
   approved device profile;
7. changelog generation in every supported language;
8. a clean logical commit before checking a Roadmap item.

Historical phase reports and immutable release manifests remain evidence of how
the runtime reached this baseline. They are not current configuration inputs and
must never be parsed as build switches.
