# Node Profile v1.1 Phase 7 Gate

This report defines the S7-49 functional gate for Phase 7 capability
expansion. It is not a release tag, beta gate, or RC gate.

Run it on the unattended Sony device:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodePhase7Gate "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.network.experimental=true"
```

The gate writes:

```text
build/reports/nodejs/phase7-summary.json
```

Required coverage:

- packaged ESM and packaged dynamic import smokes
- live provider focused gate for fetch, WebSocket, OCR, storage,
  notifications, sensors, accessibility/image, UI, and WorkManager
- long-running runtime, execution mode, queue, budget, and lifecycle stress
- worker default-denial/bootstrap gate, with runtime stress only when native
  workers are enabled and available
- modern core differential and compatibility probes
- dependency sanitizer host fixtures through `verifyNodeDependencyBundlerFixtures`
- plugin system and UI bridge instrumentation, including packaged v1.1 bridge
  plugin/UI smoke
- Phase 7 security corpus
- Phase 7 resource stress subset
- expanded real npm corpus and desktop differential report

`phase7-summary.json` is generated from the connected Android test XML plus
host gate dependencies. It records coverage groups, expected selectors, missing
selectors, failed selectors, skipped selectors, device profile, devices, network
and worker flags, XML result paths, and host gate evidence.

The summary task fails when a required selector is missing, failed, or errored.
Skipped selectors are recorded instead of treated as policy failures because
some Phase 7 probes depend on optional hardware, permissions, or native worker
availability already enforced by their focused tests.

