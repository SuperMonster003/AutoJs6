# Phase 15 Node v1.3 Packaged Aggregate Smoke

P15-24 adds the deterministic packaged aggregate smoke entry for Node Profile
v1.3. The Gradle entry is:

```powershell
.\gradlew.bat --console=plain :app:installAndRunNodeV13PackagedAggregateSmoke
```

The current gate is `host_contract_ready`: it validates the aggregate packaged
smoke contract and writes report artifacts under `build/reports/nodejs`. It
does not claim device install-run success, promoted provider authority, or
release tag approval until exported packaged APK archives and downstream
type/migration/closeout gates are attached.

## Aggregate Suites

The aggregate covers:

- packaged live UI;
- packaged overlay;
- packaged accessibility;
- packaged MediaProjection;
- packaged image/OCR;
- packaged task scheduling;
- packaged broadcast;
- packaged notifications/power;
- packaged recorder/media;
- packaged MediaStore/playback denial;
- packaged security corpus coverage.

Every suite must record:

- declared positive path or conditional skip;
- undeclared capability denial;
- packaged metadata mismatch denial;
- Safe downgrade behavior;
- task removal or provider destroy cleanup;
- stable-denied boundary preservation where applicable;
- report artifact path.

## Critical Manifest Policy

The aggregate contract can be `ready`, but the P15-24 task mapping remains
`planned` in `phase15-surface-manifest.json` because it belongs to the critical
`security.types.packaged_migration` release lane.

## Verification

Expected reports:

```text
build/reports/nodejs/phase15-packaged-aggregate-smoke.json
build/reports/nodejs/phase15-packaged-aggregate-smoke.md
```
