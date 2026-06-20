# Runtime Build Plan

S9-04 introduces the reproducible Android Node runtime build plan for future
Node 24.17 artifacts.

Host-side verification:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeRuntimeBuildPlan
```

Outputs:

- `build/reports/nodejs/runtime-build-plan.json`
- `build/reports/nodejs/runtime-build-plan.md`

## Inputs

Lock file:

- `tools/nodejs/runtime-build/runtime-build.lock.json`

Plan-only entry points:

- `tools/nodejs/runtime-build/build-node-runtime.ps1`
- `tools/nodejs/runtime-build/build-node-runtime.sh`

The lock file records:

- current runtime version: Node 24.5
- target runtime version: Node 24.17
- Android ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`
- NDK and CMake versions
- Clang and Python versions
- Node source and nodejs-mobile fork identity placeholders
- ICU, OpenSSL, and libuv settings
- ABI compiler and linker flags
- 4 KB and 16 KB page-size targets
- configure arguments
- symbol visibility version script
- expected per-ABI SHA-256 and Build ID fields

## Current Status

The current decision is `bootstrap_only` because maintainer-provided artifact
metadata is still external. This is intentional for the first S9-04 pass:
the build plan is reviewable, but no promoted runtime artifact is emitted and
the default runtime remains Node 24.5.
