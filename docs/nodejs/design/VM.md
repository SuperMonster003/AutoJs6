# Embedded Node `vm` Native Availability

`vm` is enabled in the AutoJs6 Node profile as the native Node builtin. P13-36 promotes this surface for desktop npm compatibility, especially packages such as DOM/test tooling that compile snippets with `vm.Script`.

## Why `vm` Remains High Risk

Node's `vm` module is useful for feature detection, bundlers, test runners, and packages that compile snippets with `vm.Script`. It is also easy to misunderstand as a sandbox. In an embedded Android automation runtime, a second JavaScript context is not a security boundary and is not enough to protect:

- `process`, global, and prototype leakage
- AutoJs bridge modules and pending provider handles
- scoped filesystem wrappers
- dynamic code and loader bypass attempts
- timeout and lifecycle ownership
- memory pressure from repeated contexts or compiled scripts

The current runtime already executes inside a V8 isolate and a Node environment owned by the embedded service. A VM context still shares native process state unless a stricter membrane is introduced in a future profile.

## Current Decision

Expose the native Node `vm` builtin by default.

Current behavior:

- `require("vm")` returns Node's native `vm` module.
- `require("node:vm")` returns the same native module.
- `require.resolve("vm")` and `require.resolve("node:vm")` both return `vm`.
- `node_modules/vm` cannot replace or shadow the builtin.
- `vm.Script`, `createContext`, `runInContext`, `runInNewContext`, `compileFunction`, `SyntheticModule`, `SourceTextModule`, and native timeout options follow Node's implementation.
- AutoJs6 does not inject raw Android or AutoJs bridge objects into VM contexts.
- `require("autojs6:profile").featureFlags.vm.enabled` is `true`.
- `NodeRuntimeCapabilities.featureFlagsById["vm"]` is `status=partial`, `securityLevel=high`, and has no build/project/packaged override.

## Boundaries

The native module is available for compatibility, not isolation. Scripts must not treat `vm` as a permission boundary. AutoJs6 keeps the surrounding loader and process policy in force:

- no access to raw Android or AutoJs bridge objects is injected
- no bypass of scoped filesystem policy is granted by the loader
- disabled builtins such as `child_process`, `inspector`, `wasi`, and default-off `worker_threads` remain governed by their own policies
- VM code that uses native timeout options is still owned by the embedded script lifecycle
- memory and compiled-script follow-up limits remain open
- packaged APK behavior and diagnostics need focused smoke evidence

## Follow-Up Evidence

Future hardening should add:

- memory and compiled-script budget tests for repeated `vm.Script` and context workloads
- packaged APK smoke coverage for npm packages that exercise `vm`
- bridge/process/global leakage probes that document the native Node boundary clearly
- timeout and lifecycle cancellation stress evidence for long-running VM code
