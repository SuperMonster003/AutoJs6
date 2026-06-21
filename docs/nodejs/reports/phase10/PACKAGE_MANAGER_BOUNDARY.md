# Phase 10 Package Manager Boundary

## Status

S10-21 through S10-27 now have a host-managed `autojs6-pm` fixture gate.

This does not enable Android runtime package installation and does not claim
full npm CLI compatibility. It creates deterministic evidence for the safe pure
JavaScript installer shape that Phase 10 can build on.

## Gate

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase10Autojs6PmFixture
.\gradlew.bat --console=plain :app:verifyNodePackageManagerGate
```

`verifyNodePhase10Autojs6PmFixture` writes:

- `build/reports/nodejs/phase10-package-manager-boundary.json`
- `build/reports/nodejs/phase10-package-manager-boundary.md`

`verifyNodePackageManagerGate` also runs the existing host dependency manager
and dependency bundler fixtures so the new installer prototype stays connected
to the packaged APK sanitizer path.

## Supported In The Phase 10 Fixture

- local registry metadata fixture
- exact, caret, tilde, comparator range, and dist-tag resolution
- nested `node_modules` dependency layout
- `sha512` tarball integrity verification
- safe `.tgz` extraction with Tar Slip, absolute path, symlink, and hardlink
  denial
- `autojs6-lock.json` generation
- `install`, `installFromTarball`, `update`, `remove`, `list`, `verify`, and
  `prune` operation coverage
- compatibility analysis for lifecycle scripts, native addons, raw network,
  `child_process`, `worker_threads`, `node:wasi`, and private bindings
- offline cache-ready tarball fetch path

## Negative Coverage

The fixture gate must fail closed for:

- integrity mismatch
- tar path escape
- disabled lifecycle script
- native addon file
- non-HTTPS registry protocol

## Still Deferred

- Android runtime registry install UI
- live online registry gate
- hoisting
- package manager plugin hooks
- full npm CLI compatibility
- peer dependency installation

## Boundary

The package manager remains host-managed and experimental. Package lifecycle
scripts, `.bin` execution, native addons, raw process/network modules, private
Node bindings, symlinks, and arbitrary executables remain disabled. The output
is intended to feed the existing sanitizer and packaged APK preflight path, not
to grant package installation authority inside the mobile runtime.
