# Node Profile v1.2 Release Candidate Checklist

Status: checklist ready; not release tag approval.

This checklist starts from `phase14_summary_gate_ready`. Node Profile v1.2 does
not promote the high-privilege provider POC reports as user-facing authority.
Those reports remain `provider_poc_not_promoted` and are tracked as v1.3 future
promotion backlog until their runtime, connected-device, packaged APK,
disclosure, rollback, and security evidence is complete.

## Required v1.2 RC Blockers

- [ ] `:app:generateNodePhase14Summary` reports `ready`.
- [ ] `:app:verifyNodePhase14ReleaseCandidateChecklist` reports `ready`.
- [ ] Final blocker registry has zero open final blockers.
- [ ] Phase 14 generated reports have zero policy violations.
- [ ] Phase 14 generated reports have zero release-blocking reports.
- [ ] Safe profile remains default and fail-closed.
- [ ] Release notes draft does not imply raw Android object parity.

## Host Gates

- [ ] `:app:verifyNodePhase14Baseline`
- [ ] `:app:verifyNodePhase14SurfaceManifest`
- [ ] `:app:verifyNodePhase14PromotionContracts`
- [ ] `:app:verifyNodePhase14SecurityRegressionCorpus`
- [ ] `:app:verifyNodePhase14TypeDeclarationProfiles`
- [ ] `:app:verifyNodePhase14MigrationProComparison`
- [ ] `:app:generateNodePhase14Summary`
- [ ] `:app:verifyNodePhase14ReleaseCandidateChecklist`

## Connected-Device Gates

- [ ] Run the production release gate on the selected RC device before any tag
  decision.
- [ ] Attach connected-device archives only for capabilities that are promoted
  in the RC surface.
- [ ] Keep UI, overlay, accessibility, MediaProjection, task database,
  IntentTask/broadcast, notification, power, recorder/media, MediaStore, and
  worker provider reports as v1.3 future promotion backlog while their reports
  remain `partial`.

## Packaged APK Gates

- [ ] `:app:installAndRunNodeV12PackagedSmoke` reports `host_contract_ready`.
- [ ] Production packaged install-run evidence is attached before tag approval.
- [ ] Missing capability, packaged metadata mismatch, Safe downgrade, and
  cleanup denial paths stay covered.

## Docs, Examples, And TypeScript

- [ ] `:app:verifyAutoJs6NodeTypeDeclarations`
- [ ] `:app:typeCheckNodeTypescriptSmoke`
- [ ] `:app:verifyNodeExamples`
- [ ] P14-16 UI/automation/media docs sync remains ready.
- [ ] P14-27 task/device/media docs sync remains ready.
- [ ] P14-32 desktop runtime docs sync remains ready.
- [ ] P14-35 type declaration profiles remain metadata-only for future-only
  capability rows.
- [ ] P14-36 migration and Pro comparison docs keep claims tied to executable
  evidence.

## Release Notes

- [ ] `docs/nodejs/releases/NODE_PROFILE_V1_2_RELEASE_NOTES_DRAFT.md` is
  updated for the exact commit.
- [ ] Release notes state that `provider_poc_not_promoted`,
  `worker_intentionally_unsupported_v1_2`, `host_contract_ready`, and
  `phase14_summary_gate_ready` are evidence labels, not runtime grants.
- [ ] Release notes keep raw Android object parity denied.

## v1.3 Future Promotion Backlog

The following backlog is explicitly not a required v1.2 RC blocker while the
Phase 14 summary classifies it as `future_promotion`:

- packaged live UI provider closure;
- Android overlay provider promotion and OEM matrix evidence;
- accessibility selector/action device evidence;
- Activity-owned MediaProjection provider promotion;
- advanced image/OCR packaged evidence;
- task database mutation provider and schema rollback;
- IntentTask/broadcast provider and packaged trigger/remove evidence;
- notification ownership packaged behavior and foreground disclosure;
- settings/power OEM and background-launch matrix;
- recorder/media stress and MediaStore/playback promotion;
- runnable native `worker_threads` if future ABI evidence becomes available.
