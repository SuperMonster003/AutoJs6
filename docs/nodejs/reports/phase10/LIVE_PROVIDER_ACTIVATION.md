# Phase 10 Live Provider Activation

Phase 10 S10-12 through S10-20 reuses the existing Android live-provider
implementations and verifies them as real device capabilities instead of
fake-provider or protocol-only evidence.

## Gate

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodeLiveProviderGate "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.network.experimental=true"
```

Result on Sony XQ-AT72:

- Tests: 27
- Failures: 0
- Errors: 0
- Skipped: 1
- Policy violations: 0
- Report: `app/build/reports/nodejs/live-provider-summary.json`

The skipped case is the optional controlled accessibility real-device smoke.
The aggregate still passed because the skip is policy-allowed and records a
reason; fail-closed accessibility and image-provider paths still ran.

## Provider Coverage

| Provider | Passed | Skipped | Failed | Phase 10 task |
| --- | ---: | ---: | ---: | --- |
| registry | 2 | 0 | 0 | S10-11 |
| doctor | 1 | 0 | 0 | S10-11 |
| fetch | 2 | 0 | 0 | S10-12 |
| websocket | 2 | 0 | 0 | S10-13 |
| ocr | 2 | 0 | 0 | S10-14 |
| storage | 2 | 0 | 0 | S10-15 |
| notifications | 1 | 0 | 0 | S10-16 |
| sensors | 5 | 0 | 0 | S10-17 |
| accessibility | 1 | 1 | 0 | S10-18 |
| image | 1 | 0 | 0 | S10-18 |
| ui | 3 | 0 | 0 | S10-19 |
| work_manager | 4 | 0 | 0 | S10-20 |

## Boundary Notes

- The network provider remains experimental and requires explicit
  `-Pautojs.nodejs.network.experimental=true`.
- Raw `net`, `tls`, `dns`, `dgram`, and unrestricted raw HTTP server/socket
  authority remain disabled.
- Fake-provider tests are excluded from the live-provider aggregate.
- Storage quota coverage now keeps each bridge request under the capability
  broker request-size limit and still exceeds the storage namespace quota.
- Accessibility requires an enabled service for the positive real-device smoke;
  unavailable and timeout/cancellation paths remain covered.
- No packaged APK permission is added solely because a provider exists.
