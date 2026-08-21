# Node.js Plugin Failure and Crash Intake

Last reviewed: 2026-08-21.

First determine which process failed. AutoJs6 owns routing and host API dispatch;
the runtime plugin owns Node/V8/libuv and native crashes.

## Required Information

- AutoJs6 version name, version code, build type, and ABI.
- Runtime-plugin package version and whether it is installed, enabled, and authorized.
- Minimal project or script, with secrets removed.
- Exact launch surface and whether cancellation or timeout was involved.
- Node.js Doctor text or JSON report from Developer options.
- Focused logcat covering both the host and plugin processes.
- For native crashes: tombstone/backtrace and the exact plugin APK/build revision.

## Triage

1. Reproduce with a minimal one-shot script.
2. Confirm plugin discovery and contract version before interpreting a test result.
3. If the plugin process dies, file the native/runtime fix in the plugin repository.
4. If a Binder request, Android provider, permission, workspace transfer, or host
   cleanup fails, file the fix in the host repository.
5. If both are involved, record the request ID and execution ID so logs can be
   correlated across processes.

Do not run retired host native-dependency or symbol-archive tasks: the host contains
no Node native binary. See [Native crash diagnostics](NATIVE_CRASH_DIAGNOSTICS.md).
