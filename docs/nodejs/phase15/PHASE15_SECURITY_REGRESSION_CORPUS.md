# Phase 15 Security Regression Corpus

P15-23 updates the v1.3 security regression corpus for the Phase 15 provider
promotion track. It is a host-side corpus and report gate; it does not promote
runtime authority or replace connected-device security instrumentation.

## Corpus

Machine-readable source:

- `docs/nodejs/security/NODE_PROFILE_V1_3_SECURITY_REGRESSION_CORPUS.json`

The corpus imports P14-33 and P15-04 through P15-22, then records positive,
negative, abuse, runtime guard, promotion precondition, and rollback entries
for:

- live UI;
- overlay;
- accessibility;
- MediaProjection;
- image/OCR;
- task database;
- task schema and packaged task scheduling;
- IntentTask/broadcast and packaged broadcast;
- notifications and power settings;
- recorder/media and packaged recorder stress;
- MediaStore/playback stable-denied boundary;
- docs, TypeScript, and release-note sync;
- packaged metadata mismatch and Safe downgrade.

## Boundary Categories

P15-23 requires coverage for:

- `raw_object_denial`;
- `permission_bypass`;
- `lifecycle_cleanup`;
- `packaged_metadata_mismatch`;
- `safe_downgrade`;
- `cross_package_ownership_denial`;
- `foreground_disclosure`;
- `stable_denied_boundary`.

## Critical Manifest Policy

The corpus can be `ready`, but the P15-23 task mapping remains `planned` in
`phase15-surface-manifest.json` because it belongs to the critical
`security.types.packaged_migration` release lane. P15-23 records no newly
promoted providers and no runtime authority change. P15-24 packaged aggregate
smoke is the next release-evidence gate.

## Verification

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase15SecurityRegressionCorpus
```

Expected reports:

```text
build/reports/nodejs/phase15-security-regression-corpus.json
build/reports/nodejs/phase15-security-regression-corpus.md
```
