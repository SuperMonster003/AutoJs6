# AutoJs6 Node Experimental Promotion Policy

This policy defines how an experimental Node capability can move toward stable support. A feature cannot become stable
by changing only documentation.

## Promotion States

Allowed status changes:

- experimental -> partial
- partial -> stable
- experimental -> disabled
- partial -> experimental
- stable -> partial, only with release-note justification

Any promotion must update:

- `NodeRuntimeCapabilities`
- `require("autojs6:profile")` output when applicable
- generated compatibility profile checks
- [COMPATIBILITY_PROFILE.md](COMPATIBILITY_PROFILE.md)
- [releases/NODE_PROFILE_V1_STABLE_SURFACE.md](releases/NODE_PROFILE_V1_STABLE_SURFACE.md)
- [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md)
- user-facing docs or examples

## Required Gates For Any Promotion

Every promotion requires:

- positive tests for the supported path
- negative/security tests for denied paths
- packaged APK smoke or a documented packaged no-op reason
- compatibility corpus coverage where module resolution or npm behavior changes
- lifecycle or resource stress coverage when the feature creates handles, threads, sockets, timers, files, or Android resources
- stable error codes for rejected paths
- feature flag migration notes
- rollback notes

Security, scoped filesystem, bridge permission, packaged manifest, and native crash risks must be reviewed before a
feature can be stable.

## Feature-Specific Gates

### ESM

Promotion requires:

- `.mjs` entry tests
- `package.json` `type=module` and `type=commonjs` tests
- ESM import of CJS tests
- CJS require of ESM decision tests
- top-level await tests
- `import.meta` scoped metadata, resolver, and denial tests
- scoped file URL and path escape tests
- package `exports` condition-order tests
- encrypted moduleSources graph tests
- packaged APK ESM smoke
- stack trace and source map tests

Default v1 status: experimental.

### Dynamic Import

Promotion requires:

- explicit relationship to the ESM gate
- local relative import tests
- package, builtin, absolute path, `file:`, network URL, and path escape denial tests
- packaged APK dynamic import smoke
- service destroy with pending dynamic import test

Default v1 status: experimental and off by default.

### Worker Threads

Promotion requires:

- pure-JS worker computation tests
- worker exception, terminate, parent stop, and timeout tests
- max-worker limit tests
- memory and thread stress tests
- denial of AutoJs bridge modules inside workers
- denial of Android, Java, raw network, nested worker, and native addon access
- packaged smoke

Default v1 status: experimental and off by default. Task 122 currently blocks runnable worker advertising until native
availability plus the worker execution and resource stress suites pass.

### Controlled Network And Fetch

Promotion requires:

- Android `INTERNET` manifest checks for packaged APKs
- timeout, abort, redirect, unsupported scheme, max response size, header, body, and service destroy tests
- live provider smoke on an explicitly approved device profile
- privacy review for request/response logging
- proof that raw `http`, `https`, `net`, `tls`, `dns`, and `dgram` remain denied

Default v1 status: controlled wrapper present, live provider experimental and off by default.

### WebSocket

Promotion requires:

- connect, send, receive, close, timeout, abort, and service destroy tests
- max connection, queue, and message-size limit tests
- packaged `INTERNET` manifest checks
- resource registry cleanup tests
- proof that raw `net`, `tls`, and HTTP upgrade authority remain denied

Default v1 status: controlled wrapper present, live provider experimental and off by default.

### Java Interop

Promotion requires:

- allowlist audit for every class and method
- denial tests for `Runtime`, `ProcessBuilder`, `System`, `ClassLoader`, reflection, and native library loading
- opaque handle lifecycle tests
- packaged release policy tests
- security signoff that it cannot bypass scoped fs, shell, process, or native addon policy

Default v1 status: experimental and off by default.

### `vm`

Promotion requires:

- explicit sandbox boundary document
- denial of filesystem, process, network, Java, Android, native addon, and bridge capability bypasses
- source map and stack behavior tests
- resource and timeout tests
- packaged smoke

Default v1 status: disabled.

### Long-Running Service Mode

Promotion requires:

- foreground notification and user-stop design
- Android foreground-service type and permission verification
- timeout, idle, resource quota, crash, restart, and process-death tests
- packaged APK manifest verification
- resource registry cleanup and leak stress tests

Default v1 status: design-only.

## Demotion And Disablement

Demote or disable a feature when:

- security tests fail
- scoped fs or bridge capability checks can be bypassed
- packaged APK behavior cannot be made deterministic
- lifecycle stress shows unresolved leaks
- native crashes are reproducible and not isolated
- compatibility claims exceed tested behavior

Demotion requires release notes and compatibility profile updates. Stable error codes should remain stable even when a
feature is demoted.
