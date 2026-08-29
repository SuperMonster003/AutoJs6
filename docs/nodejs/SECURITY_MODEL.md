# AutoJs6 Node.js Security Model

Last reviewed: 2026-08-29.

AutoJs6 Node.js spans two Android applications with separate UIDs. The runtime plugin
executes Node/V8/libuv. The host owns Android automation authority. Neither side is a
generic desktop shell.

## Plugin Trust Boundary

The host discovers a fixed service contract and validates package visibility, service
permission, signature/trust, user authorization, host-version compatibility, contract
range, and ABI before execution. The host does not dlopen plugin libraries or copy
them into its APK.

## Workspace Boundary

Requests transfer an explicit workspace archive with bounded size/count/path rules.
The plugin receives a sandbox root and explicit input mappings; output writeback and
deletion tombstones are bounded. Absolute private paths, traversal, NUL bytes,
realpath/symlink escape, and unbounded watches are denied by policy.

Host-side module-source providers are request-scoped. They materialize or decrypt only
resolver-selected candidates, enforce version/byte/count/time limits, and are closed
with the execution. Provider v3 may additionally compile an exact runtime-created
`.ts/.mts/.cts` candidate for a compiler-backed Node project. The runtime plugin reads
that candidate from its private workspace with no-follow identity checks and sends a
byte- and SHA-256-bound PFD; the host overlays it on the existing project snapshot
without writing source or output into the project. Generated paths, Source Maps, and
the original file identity are revalidated before execution. This does not turn the
host into a second module resolver or grant either process a new filesystem root.

## Capability Boundary

The plugin performs script-side capability preflight. The host independently checks
the declared capability, child capability, Android permission, provider health,
rate/resource limits, and execution ownership. The two manifests intentionally are
not one shared implementation.

Only bounded JSON-safe values cross Binder. Raw Android/Java objects, cursors, views,
bitmaps, native pointers, file handles, processes, and arbitrary reflection do not.
Resources are registered to an execution and closed on completion, cancellation,
timeout, or peer death.

## Runtime Boundary

Native addons, arbitrary shared-library loading, private Node bindings, Inspector,
unbounded workers/processes/networking, and unrestricted Java interop are disabled or
allowlisted by the runtime plugin. Runtime-native fixes belong in the plugin.

## Packaging

The host has no embedded Node service or libnode. Exported APKs cannot bundle the
runtime. Legacy **backend="embedded"** work_manager values and **embedded_script.***
diagnostic keys are wire compatibility names only.

See [Capability policy](CAPABILITY_STABILITY_POLICY.md) and the plugin's
**docs/HOST-API.md**.
