# AutoJs6 Node Runtime Build

This directory contains the Phase 9 bootstrap plan for reproducible Android
Node runtime artifacts.

Current status: `bootstrap_only`.

The checked-in lock file records the target Node version, required Android
ABIs, Node 24.17 upstream provenance, Android fork provenance, toolchain
versions, page-size targets, configure arguments, runtime library settings,
linker visibility policy, and expected artifact metadata fields. Maintainers
still need to produce the Node 24.17 Android port artifacts, per-ABI library
hashes, Build IDs, and signing inputs before this can produce promoted
artifacts.

Run the host-side check:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeRuntimeBuildPlan
```

Plan-only script entry points:

```powershell
powershell -ExecutionPolicy Bypass -File tools\nodejs\runtime-build\build-node-runtime.ps1
sh tools/nodejs/runtime-build/build-node-runtime.sh
```

These scripts intentionally do not emit runtime artifacts yet. They keep S9-04
reviewable without changing the default embedded runtime.
