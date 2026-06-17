# Node Profile v1.1 Scope

Target branch: `feature/nodejs-runtime-v1.1`

Node Profile v1.1 is a capability expansion over the v1.0 Safe Node
Profile. It keeps the same sandbox, builtin allowlist, bridge capability
manifest, backend switches, and packaged APK requirements while promoting a
small set of already-gated features toward practical use.

## Goals

- ESM partial support for `.mjs` entries and `package.json` `type=module`
  entry routing.
- Safe ESM builtin imports through the existing builtin allowlist.
- Practical ESM-to-CommonJS interop while keeping CommonJS `require()` of ESM
  disabled with a stable error.
- Package `exports` and `imports` condition selection for both CommonJS and
  ESM importers.
- Controlled fetch and WebSocket remain on the v1.1 roadmap, but are not part
  of the first ESM promotion batch.
- NPM compatibility expansion continues to focus on pure JavaScript packages.
- AutoJs bridge expansion continues through capability-gated modules.
- Long-running script support remains an explicit mode, not accidental
  behavior from timers, sockets, workers, or bridge resources.

## Non-goals

- Full desktop Node.js compatibility.
- Native addon `.node` loading.
- Unrestricted `child_process`.
- Raw `net`, `tls`, `dns`, `dgram`, `http`, or `https` I/O.
- Unrestricted Java or Android object interop.
- Full inspector/debugger server support.
- Raw Node loader hooks, custom ESM loaders, or unrestricted `node:module`
  authority.
- Relaxing scoped filesystem policy.
- Removing Rhino fallback or the `rhino`, `native`, `embedded`, and `auto`
  backend switches.
- Removing the native one-shot oracle.

## Feature Status Targets

| Feature | v1.1 target | Notes |
| --- | --- | --- |
| CommonJS/npm subset | stable | Existing v1.0 contract remains first-class. |
| Scoped fs | stable | No policy loosening. |
| ESM entry | partial | `.mjs` and `type=module` entries run by default in v1.1. |
| ESM builtin import | partial | Only allowed builtins and limited wrappers. |
| ESM/CJS interop | partial | ESM may import CJS; CJS `require()` of ESM remains denied. |
| Package ESM conditions | partial | ESM uses `import`, `node`, `default`; CJS keeps `require`, `node`, `default`. |
| Dynamic import | partial | Local-only dynamic import is enabled by default for relative modules, allowed builtins, and scoped packages. |
| Packaged ESM | partial | Packaged `.mjs` entry plus local `.mjs`/CommonJS graph smoke is required; dynamic import local graph smoke is covered separately. |
| Buffer module | partial | `require("buffer")`, `require("node:buffer")`, and ESM `node:buffer` use the same builtin wrapper and cannot be shadowed by `node_modules/buffer`. |
| Stream primitives | partial | Async iteration, callback/promise pipeline and finished helpers, Web Streams adapters, backpressure smoke, destroy propagation, and shadowing denial stay covered as pure-JS compatibility surface. |
| fs streams | partial | Scoped read/write streams support read, write, pipe, stable denial paths, active-stream diagnostics, timeout/service cleanup, and packaged smoke coverage. |
| zlib streams | partial | Bounded transform streams support gzip/gunzip and deflate/inflate pipelines, scoped fs pipeline smoke, invalid input errors, active-stream diagnostics, timeout/service cleanup, and packaged smoke coverage. |
| Controlled fetch | experimental | Future v1.1 batch; raw network remains disabled. |
| WebSocket | experimental | Future v1.1 batch; raw sockets remain disabled. |
| Worker threads | experimental | Disabled by default. |
| Native addon | unsupported | Must remain disabled. |
| Child process | disabled | Use controlled `shell` bridge instead. |

## Success Criteria

- `.mjs` entry smoke passes without a per-test ESM opt-in.
- Package `type=module` entry smoke passes.
- Local static import, named export, default export, and top-level await pass.
- ESM imports of allowed builtins pass.
- ESM import of disabled builtins fails with stable AutoJs6 error metadata.
- ESM package `exports`/`imports` choose the import condition.
- CJS package `exports`/`imports` still choose the require condition.
- CommonJS `require()` of `.mjs` fails with
  `ERR_AUTOJS6_REQUIRE_ESM_UNSUPPORTED`.
- Packaged `.mjs` graph smoke passes with refreshed template support.
- Dynamic import local-only smoke passes for `.mjs`, CommonJS, allowed builtins,
  and packages, while URL, absolute path, escape, and disabled builtin paths
  remain denied.
- Buffer smoke passes for CJS and ESM import forms, global consistency, common
  construction APIs, and `node_modules/buffer` shadow denial.
- Stream smoke passes for async iteration, callback and promise pipeline,
  callback and promise finished, Web Streams adapters, slow-writable
  backpressure, destroy propagation, and `node_modules/stream` shadow denial.
- fs stream smoke passes for read/write/pipe, scoped escape denial,
  `/proc`/`/sys`/`/dev` absolute path denial, active stream timeout cleanup,
  and packaged APK markers.
- zlib stream smoke passes for gzip/gunzip, deflate/inflate, scoped fs
  read->gzip->write, invalid gzip error propagation, active stream timeout
  cleanup, and packaged APK markers.
- Compatibility profile marks ESM and dynamic import as `partial`.

## Safety Invariants

- ESM resolution must use the same scoped fs and package target checks as
  CommonJS.
- Builtin imports must route through the existing allowlist, limited wrappers,
  and disabled-builtin policy.
- ESM must not enable native addons, process binding, raw network, raw Android
  interop, or loader hooks.
- Packaged APK support must remain a first-class target; features not covered
  by packaged smoke stay experimental or disabled.
