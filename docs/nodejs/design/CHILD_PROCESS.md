# Child Processes

Status: stable native Node builtin, enabled by default in Node Profile v1.2.

Both `require("child_process")` and `require("node:child_process")` resolve to
Node's native builtin in every Node-enabled Debug and Release build. The
capability has no Gradle opt-in, release block, project/package override, or
packaged-APK override.

- BuildConfig: `NODEJS_CHILD_PROCESS_ENABLED=true`
- host/plugin request contract v2: `childProcessEnabled=true`
- profile snapshot: `featureStates.childProcess=true`
- capability status: `stable`

## Native Semantics

The surface follows the Node runtime's `spawn`, `exec`, `execFile`, and
`fork` behavior as supported by the Android Node build. It exposes native
stdio streams, process handles, signals, shell options, caller-managed timeout,
and `kill()`.

This is distinct from the AutoJs `shell` bridge. The bridge has its own
capability declaration, root/Shizuku policy, output limits, diagnostics, and
provider lifecycle. Native `child_process` does not silently become a shell
bridge request.

## Android Boundary

Child processes inherit the Android application UID, SELinux domain, filesystem
permissions, environment, available executable set, and platform restrictions.
The native builtin is not an app-private-binary allowlist wrapper. Scripts must
validate executable paths and arguments, avoid untrusted shell strings, bound
stdout/stderr, set timeouts where appropriate, close stdin, and terminate or
reap children during cancellation and shutdown.

Availability of `child_process` does not grant root, Shizuku, Java, Android
bridge, native-addon, Inspector, or out-of-sandbox app permissions.

## Verification

Conformance covers bare/`node:` resolution, shadow protection, spawn/exec
availability on supported ABIs, stdio and exit behavior, timeout/cancel/kill,
cleanup, packaged behavior, and profile/Doctor reporting. Android or device
limitations are reported as runtime errors, not represented by a build feature
flag.
