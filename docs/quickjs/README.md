# QuickJS Runtime plugin integration

AutoJs6 host build 5279 introduces a distinct `quickjs` JavaScript engine backed by
the separately installed `AutoJs6-Plugin-QuickJS-Runtime` application. User source is
evaluated by the official QuickJS 2026-06-04 engine in the plugin's isolated
`:quickjs_runtime` process. It is never reinterpreted by Rhino or forwarded to the
Node.js or Bun runtime plugins.

## Selecting QuickJS

Use an explicit directive at the beginning of a `.js` or `.mjs` source:

```javascript
"quickjs";

console.log("QuickJS", "Unicode", "中文", 2n ** 63n);
```

The short directive `"qjs";` is equivalent. Alternatively, use one of the compound
file markers:

```text
*.quickjs.js   script mode
*.quickjs.mjs  ES module mode
```

An unmarked `.mjs` file keeps the existing Node.js default unless it contains an
explicit QuickJS directive. Engine selections are exclusive: a source that also
selects Rhino, Node.js, or Bun fails before dispatch instead of falling back.

## Version 1.0 boundary

The first contract deliberately supports a bounded single-source execution model:

- JavaScript scripts and single-file ES modules are supported, including BigInt,
  Unicode, Promise jobs, and module top-level await.
- Every request receives a fresh native `JSRuntime` and `JSContext`.
- Source is limited to 8 MiB and transferred as one immutable, read-only file
  descriptor. Combined stdout and stderr are limited to 4 MiB.
- The default limits are 60 seconds of execution time, 64 MiB of runtime memory,
  and a 1 MiB stack. The protocol caps memory at 256 MiB.
- Stop requests and runtime deadlines reach the QuickJS interrupt handler.
- Encrypted AutoJs6 entries are decrypted only into a private temporary snapshot;
  the host unlinks the snapshot before dispatch and clears transient byte arrays.

The plugin intentionally exposes only bounded `console` and `print` output helpers.
It does not expose AutoJs6 globals, Java or Android objects, filesystem or network
APIs, `quickjs-libc`, `std`, `os`, native addons, timers, a project tree, relative
module imports, or TypeScript parsing. QuickJS is also unsupported for
`preExecuteScript`, AutoJs6 UI modes, Node projects, and packaged APK/inrt execution;
those cases fail closed.

## Host/plugin trust boundary

The contract source is mirrored in `plugin-api/quickjs-runtime-api`. Before binding,
AutoJs6 requires the signature permission, normal plugin trust and enablement, a
compatible ABI, and the exact plugin id, engine, variant, protocol, capability set,
Binder descriptor, and minimum host build. The selected component, package UID,
version, update time, and signer set are pinned across each call.

Calls run on a bounded, non-queueing Binder lane. A timed-out execution dispatch is
never retried because its remote state is uncertain; cancellation uses a separate
cleanup lane. Callback events must come from the pinned plugin UID, match the
execution id and protocol, have strictly increasing sequence values, and remain
inside the negotiated byte limits. The host waits briefly for the terminal callback
so final output chunks are drained before the callback is closed.

## Runtime and packaging

The initial plugin supports Android 7.0 (API 24) or later and ships `arm64-v8a`,
`armeabi-v7a`, `x86_64`, `x86`, and universal APKs. AutoJs6 build 5279 or newer is
required. The plugin's native ELF and APK verifier enforces 16 KiB segment and ZIP
alignment; its CI also contains an API 35 `google_apis_ps16k` runtime target.

Packaged applications cannot depend on AutoJs6's signature-protected external
plugin, so the APK builder rejects QuickJS entries instead of silently compiling or
rerouting them. Runtime source, release artifacts, provenance, and exact verification
commands live in the
[QuickJS Runtime plugin repository](https://github.com/SuperMonster003/AutoJs6-Plugin-QuickJS-Runtime).
