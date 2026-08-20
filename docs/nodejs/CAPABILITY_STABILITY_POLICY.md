# AutoJs6 Node Capability Stability Policy

This policy defines how Node.js runtime capabilities are classified, validated,
released, and, when necessary, demoted. A capability cannot change stability by
documentation alone.

## Current Stable Runtime Set

The AutoJs6 Node Profile v1.2 runtime ships these capabilities enabled in every
Debug and Release build:

- ESM entry loading and local dynamic `import()`
- controlled `fetch`, `axios`, `undici`, and WebSocket providers
- native `dns`, `dns/promises`, `http`, `https`, `net`, and `tls` builtins
- `worker_threads`
- `child_process`
- allowlisted Java/Android interop

They have no Gradle opt-in property, release-only block, project/package
override, or packaged-APK opt-in. Host/plugin request contract v2 uses stable
`*Enabled` field names, and `require("autojs6:profile")` exposes their current
state through `featureStates` and `enabledFeatures`.

Stable availability does not bypass authorization. Runtime permissions,
Android manifest permissions, Java allowlists, scoped filesystem rules,
process/network policies, resource budgets, cancellation, and lifecycle cleanup
remain mandatory.

## Status Values

The current compatibility profile uses:

- `stable`: enabled and supported within the documented AutoJs6 boundary.
- `partial`: enabled, but intentionally smaller than the desktop Node surface.
- `disabled`: compiled or recognized but denied by product policy.
- `unsupported`: intentionally unavailable and not advertised as runnable.
- `planned`: design work only; no runtime claim.

Current non-stable examples include partial scoped `fs`, `zlib`, and `vm`;
disabled Inspector; and unsupported native addons. A disabled or unsupported
surface must never be made runnable by an undocumented build property.

## Required Evidence For Promotion

Any move to `stable` requires all applicable evidence:

- positive runtime and API-contract tests;
- denial/security tests for out-of-bound behavior;
- packaged APK smoke or a documented, enforced packaged exclusion;
- compatibility corpus coverage for resolver/npm changes;
- lifecycle and resource stress for threads, sockets, processes, files, native
  handles, and Android resources;
- stable error codes for rejected operations;
- host/plugin contract and artifact compatibility checks;
- compatibility profile, type declarations, examples, and release-note updates;
- a rollback or disablement plan that does not depend on silently changing the
  public contract.

## Capability-Specific Requirements

ESM and dynamic import require scoped resolution, package-mode, CommonJS
interoperability, import attributes, encrypted/module-source graphs, packaged
execution, source-map, and path-escape coverage.

Network and WebSocket require Android `INTERNET` manifest validation, URL and
permission checks, timeouts, abort/cancel behavior, response/message budgets,
redirect policy, live loopback tests, resource cleanup, and privacy-safe
diagnostics.

Raw network builtins require explicit verification that Android sandboxing and
runtime network permission policy still apply and that their presence does not
grant unrelated AutoJs bridge authority.

Workers require native availability on every published ABI, worker-data and
message semantics, transfer-list validation, count/queue/message budgets,
termination, packaged execution, memory/thread stress, and a smaller worker
authority set than the parent script.

Child processes require process-permission enforcement, Android sandbox
behavior, bounded stdio and timeouts, cancellation/kill cleanup, lifecycle
diagnostics, and denial of authority escalation.

Java interop requires an allowlist audit for every class/member, opaque-handle
lifecycle tests, and denial of reflection, class loaders, `Runtime`,
`ProcessBuilder`, native-library loading, and raw Android context/UI objects.

## Demotion And Disablement

Demote or disable a capability when a security boundary can be bypassed,
packaged behavior is nondeterministic, supported ABIs disagree, lifecycle
stress leaks resources, or compatibility claims exceed verified behavior.
Demotion requires release notes, generated-profile changes, compatible error
behavior where possible, and an explicit migration path. It must not be hidden
behind a private build switch.
