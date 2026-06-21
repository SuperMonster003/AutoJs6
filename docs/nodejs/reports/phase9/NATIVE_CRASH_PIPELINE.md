# Phase 9 Native Crash Pipeline

Status: bootstrap-only policy gate.

S9-44 defines the crash evidence that must be collected when the embedded
Node process, `libnode.so`, V8, libuv, JNI bridge, or packaged Node process
crashes. This report does not inject a crash and does not require root access.

## Current Coverage

The current pipeline already has the required evidence anchors:

- runtime artifact manifest, integrity, distribution, and aggregate reports;
- native dependency and symbol archive reports with per-ABI SHA-256 values and
  Build ID fields when present;
- crash intake attachment checklist;
- logcat capture commands;
- tombstone or bugreport collection instructions;
- symbolication instructions using `ndk-stack` or `llvm-symbolizer`;
- Node Doctor report and packaged APK diagnostic report references.

The Phase 9 host gate verifies that those anchors remain present before the
native modernization aggregate can be treated as complete.

## Required Crash Bundle

For every native Node crash, attach:

- exact git commit, version name/code, runtime slot, identity profile, and
  device ABI;
- runtime artifact manifest and integrity reports;
- native dependency report;
- native symbol archive report;
- logcat window from process start through crash or timeout;
- tombstone text or Android bugreport excerpt;
- Node Doctor JSON/text report;
- execution descriptor, including entry type, package identity, backend,
  feature flags, and project/package metadata;
- packaged verifier/diagnostic report when the failure is packaged-only.

## Symbolication

Match crash frames by ABI, library name, SHA-256, and Build ID when available.
For bridge crashes, prefer the latest CMake output `symbol_file` path from the
symbol archive report when it has `.debug_*` or `.symtab` sections. For
`libnode.so` crashes, use the matched runtime artifact and its corresponding
symbol files from the runtime build.

## Remaining Work

Future automation still needs:

- one command that collects logcat, bugreport/tombstone, Node Doctor, runtime
  artifact reports, and packaged diagnostics into one redacted archive;
- active bridge/provider resource snapshots at the time of crash;
- last embedded runtime timeline events;
- automatic execution descriptor capture for packaged and main-app launches;
- symbolication wrapper that resolves against the exact Build ID and SHA-256;
- crash quarantine/retry integration with the future process-pool coordinator.
