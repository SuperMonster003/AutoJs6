# Node.js Native Crash Diagnostics

Last reviewed: 2026-08-21.

Node/V8/libuv and the JNI bridge are owned by the external runtime-plugin process.
The AutoJs6 host APK has no Node native library, so retired host symbol/archive/native
dependency Gradle tasks are not valid diagnostic steps.

For a native crash collect:

- exact runtime-plugin version, APK, ABI, and source revision;
- Android build fingerprint and supported ABI list;
- tombstone or debugger backtrace with plugin symbols;
- logcat covering plugin bind, probe, execution ID, and process death;
- minimal script/workspace and timeout/cancellation steps;
- host Node.js Doctor report for contract, discovery, and capability context.

Symbolization and native root-cause work belong in the runtime-plugin repository.
Host changes are appropriate only when discovery, Binder lifecycle, request transfer,
error recovery, or host capability dispatch contributed to the failure.

See [Crash intake](CRASH_INTAKE.md).
