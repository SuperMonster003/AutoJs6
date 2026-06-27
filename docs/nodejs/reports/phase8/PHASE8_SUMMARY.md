# Phase 8 Summary

Phase 8 closes as the Node Profile v1.1 capability expansion stage. It adds
substantial runtime evidence without changing the default Safe Node Profile
boundaries.

## Completed Capability Areas

- Node 24.17 candidate intake, diff, and security-probe gate scaffolding.
- Lightweight TypeScript `.ts` / `.mts` / `.cts` runtime support.
- Module conformance v2 for CJS/ESM interop and package condition behavior.
- Web Platform globals and controlled HTTP/HTTPS/undici client facades.
- Advanced scoped filesystem coverage for FileHandle, fd APIs, opendir,
  watchers, safe operations, and lifecycle fuzzing.
- Compile-cache diagnostics, prewarm policy, and performance matrix reporting.
- Inspector, DevTools, and profiling design/security gates with release
  endpoint denial preserved.
- Hardened isolated runtime POC, capability broker v2, and process-pool policy
  gates.
- Host-side dependency manager and real npm corpus expansion to 20 fixtures.
- Phase 8 user examples and aggregate Phase 8 gate/summary wiring.

## Preserved Boundaries

Phase 8 does not promote native addons, unrestricted `child_process`, raw
socket/DNS/TLS/datagram modules, release inspector, unrestricted `vm`, Java or
Android interop, unscoped filesystem access, or worker_threads by default.
`node24_5` remains the default runtime slot until a complete Node 24.17 Android
artifact candidate is available and passes the explicit upgrade gate.

## Final Gate

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:verifyNodePhase8Gate "-Pautojs.nodejs.test.deviceProfile=sony"
```

The gate writes `app/build/reports/nodejs/phase8-summary.json`.
