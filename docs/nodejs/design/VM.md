# Embedded Node `vm` Design Gate

`vm` is disabled in the AutoJs6 Safe Node Profile. This task records the decision boundary and does not change runtime behavior.

## Why `vm` Is High Risk

Node's `vm` module is useful for feature detection, bundlers, test runners, and packages that compile snippets with `vm.Script`. It is also easy to misunderstand as a sandbox. In an embedded Android automation runtime, a second JavaScript context is not enough to protect:

- `process`, global, and prototype leakage
- AutoJs bridge modules and pending provider handles
- scoped filesystem wrappers
- dynamic code and loader bypass attempts
- timeout and lifecycle ownership
- memory pressure from repeated contexts or compiled scripts

The current runtime already executes inside a V8 isolate and a Node environment owned by the embedded service. Adding another context would still share native process state unless a much stricter membrane is designed.

## Current Decision

Keep `vm` disabled by default.

Current behavior:

- `require("vm")` rejects with the disabled-builtin error shape.
- `require("node:vm")` rejects with the same error shape.
- `require.resolve("vm")` and `require.resolve("node:vm")` reject before local package shadowing.
- `node_modules/vm` cannot replace the disabled builtin.
- `require("autojs6:profile").featureFlags.vm.enabled` is `false`.
- `NodeRuntimeCapabilities.featureFlagsById["vm"]` is `status=disabled`, `securityLevel=high`, and has no build/project/packaged override.

## Deferred Options

If future npm compatibility requires a limited surface, evaluate one of these options behind a new experimental gate:

- expose nothing and keep improving the disabled error and feature-detection story
- expose only a non-context `vm.Script` compile/run path that uses the current global context
- expose a separate context only after a strict membrane, timeout, memory, and bridge-denial model exists

Do not expose these APIs without a new design task:

- `createContext`
- `runInContext`
- `runInNewContext`
- `compileFunction`
- `SyntheticModule`
- `SourceTextModule`
- dynamic import hooks

## Acceptance For Any Future Enablement

Future enablement must prove:

- no access to raw Android or AutoJs bridge objects
- no bypass of scoped filesystem
- no access to disabled builtins or process internals
- deterministic timeout and lifecycle cancellation
- memory and compiled-script limits
- packaged APK behavior and diagnostics
- positive and negative instrumentation coverage
