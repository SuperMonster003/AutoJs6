# Phase 14 Node v1.2 Packaged Aggregate Smoke

P14-34 adds the deterministic packaged aggregate smoke entry for Node Profile
v1.2. The Gradle entry is:

```powershell
.\gradlew.bat --console=plain :app:installAndRunNodeV12PackagedSmoke
```

The current gate is `host_contract_ready`: it validates the aggregate packaged
smoke contract and writes report artifacts under `build/reports/nodejs`. It
does not claim device install-run success until exported packaged APK archives
are attached.

## Aggregate Suites

The aggregate covers:

- packaged live UI;
- packaged overlay;
- packaged accessibility;
- packaged image/OCR;
- packaged task scheduling;
- packaged broadcast;
- packaged notifications/power;
- packaged recorder/media;
- packaged worker threads.

Every suite must record:

- declared positive path or conditional skip;
- undeclared capability denial;
- packaged metadata mismatch denial;
- Safe downgrade behavior;
- task removal or provider destroy cleanup;
- report artifact path.

## Verification

Expected reports:

```text
build/reports/nodejs/phase14-packaged-aggregate-smoke.json
build/reports/nodejs/phase14-packaged-aggregate-smoke.md
```
