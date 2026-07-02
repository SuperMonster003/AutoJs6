# Phase 14 Security Regression Corpus

P14-33 expands the v1.2 security regression corpus for the Phase 14 provider
promotion track. It is a host-side corpus and report gate; it does not promote
runtime authority or replace connected-device security instrumentation.

## Corpus

Machine-readable source:

- `docs/nodejs/security/NODE_PROFILE_V1_2_SECURITY_REGRESSION_CORPUS.json`

The corpus records positive, negative, abuse, runtime guard, promotion
precondition, and rollback entries for:

- live UI;
- overlay;
- accessibility;
- MediaProjection;
- image/OCR;
- task database;
- IntentTask/broadcast;
- notifications;
- power settings;
- recorder/media;
- media playback and MediaStore;
- worker/process-worker;
- packaged metadata mismatch and Safe downgrade.

## Boundary Categories

P14-33 requires coverage for:

- `raw_object_denial`;
- `path_escape`;
- `permission_bypass`;
- `lifecycle_cleanup`;
- `packaged_metadata_mismatch`;
- `safe_downgrade`.

## Verification

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14SecurityRegressionCorpus
```

Expected reports:

```text
build/reports/nodejs/phase14-security-regression-corpus.json
build/reports/nodejs/phase14-security-regression-corpus.md
```
