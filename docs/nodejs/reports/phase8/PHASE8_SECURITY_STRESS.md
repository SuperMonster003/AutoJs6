# Phase 8 Security / Stress Expansion

S8-47 adds `verifyNodePhase8SecurityStressGate` as the focused security and
stress expansion gate for the Phase 8 capability set.

## Covered Areas

The gate selects:

- Node 24.17 security-fix probes while the runtime slot remains defaulted to
  `node24_5`.
- Lightweight TypeScript routing and stripping tests.
- Module conformance v2 for CJS/ESM cycles, `require(esm)`, import attributes,
  package conditions, and moduleSources graphs.
- Controlled HTTP/HTTPS, stream semantics, Agent, undici, and offline HTTP npm
  corpus coverage.
- FileHandle, fd, opendir, fs.watch, safe file operations, and the
  FileHandle/watcher lifecycle fuzzer.
- Module compile-cache diagnostics and runtime prewarm/performance matrix
  coverage.
- Inspector disabled-state security checks.
- Hardened isolated runtime policy POC, capability broker v2, process-pool
  policy, and bridge RPC malformed-input fuzzer.

## Security Boundary

The gate does not promote native addons, unrestricted `child_process`, raw
`net`/`tls`/`dns`/`dgram`, release inspector, unrestricted `vm`, Java/Android
interop, unscoped filesystem access, or worker_threads availability. Known
design-gated inputs are recorded by the Phase 8 summary instead of being
treated as enabled capabilities.

## Command

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:verifyNodePhase8SecurityStressGate "-Pautojs.nodejs.test.deviceProfile=sony"
```
