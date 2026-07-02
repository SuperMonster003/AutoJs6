# Phase 14 Surface Manifest

`phase14-surface-manifest.json` tracks ownership and promotion state for Node
Profile v1.2 provider work. Phase 14 starts from the Phase 13 v1.1 closeout
state and carries forward only future-promotion backlog, not v1.1 release
blockers.

## Profile Vocabulary

- `safe_default`: the conservative default Safe Node Profile.
- `pro_compat_opt_in`: explicit Auto.js Pro compatibility profile.
- `desktop_compat_opt_in`: explicit desktop Node.js compatibility profile.
- `debug_unsafe_lab`: debug-only validation profile for high-risk behavior.
- `permanently_unsupported`: intentionally unavailable after policy review.

## Status Vocabulary

- `planned`: tracked by Phase 14 but implementation or evidence is not ready.
- `design_locked`: profile, risk, boundary, errors, gates, and rollback are
  documented, but runtime promotion has not landed.
- `debug_poc`: a debug-only proof of concept exists.
- `provider_poc`: a fake or live provider proof of concept exists, but the
  surface is not promoted for user scripts.
- `opt_in_promoted`: available only through an explicit Pro/Desktop profile or
  capability declaration.
- `default_promoted`: available in the default Safe Node Profile.
- `rejected`: intentionally unavailable with stable denial behavior and
  migration guidance.

## Current Surfaces

| Surface | Status | Profiles | Tasks | Risk | Summary |
| --- | --- | --- | --- | --- | --- |
| `phase14.baseline` | `default_promoted` | `safe_default` | `P14-00` | `low` | Imports the Phase 13 v1.1 closeout state and future-promotion backlog into a Phase 14 baseline verifier. |
| `phase14.surface_manifest` | `default_promoted` | `safe_default` | `P14-01` | `low` | Machine-readable Phase 14 manifest, schema, docs, and verifier. |
| `phase14.promotion_contracts` | `design_locked` | `safe_default`, `pro_compat_opt_in`, `desktop_compat_opt_in`, `debug_unsafe_lab` | `P14-02` to `P14-06` | `high` | Shared v1.2 capability vocabulary, report conventions, disclosure contract, rollback hooks, and device matrix, verified by `verifyNodePhase14PromotionContracts`. |
| `pro.ui_automation_media` | `provider_poc` | `pro_compat_opt_in` | `P14-07` to `P14-16` | `high` | Packaged live UI, Android overlay, accessibility lifecycle, MediaProjection, image operations, OCR quota/model policy, and docs/types sync. P14-07 imports packaged live UI provider POC evidence, P14-08 adds an Android overlay provider POC, P14-09 adds the overlay permission/OEM matrix, P14-10 adds the packaged overlay positive smoke/stress fixture, P14-11 adds the packaged accessibility lifecycle fixture, P14-12 adds the selector/action real-device matrix, P14-13 adds the Activity-owned MediaProjection provider contract, P14-14 adds the advanced image operation promotion contract, P14-15 adds the packaged image/OCR evidence contract, and P14-16 adds the docs/examples/declarations sync gate while keeping promotion blockers open. |
| `pro.tasks_device_media` | `provider_poc` | `pro_compat_opt_in` | `P14-17` to `P14-27` | `high` | Task database mutations, IntentTask/broadcast provider, notification ownership, power/OEM matrix, recorder sessions, packaged media stress, and docs/types sync. P14-17 adds the JSON-safe task database mutation contract, P14-18 adds the task schema migration and rollback contract, P14-19 adds the packaged task scheduling smoke baseline, P14-20 adds the JSON-safe IntentTask/broadcast provider contract, P14-21 adds the packaged broadcast trigger/remove smoke contract, P14-22 adds the notification ownership and foreground disclosure gate, P14-23 adds the settings/power OEM and background-launch matrix gate, P14-24 adds the real recorder session promotion gate, P14-25 adds the packaged recorder/media stress gate, P14-26 adds the media playback/MediaStore boundary gate, and P14-27 adds the task/device/media docs/examples/declarations sync gate while keeping runtime authority unpromoted. |
| `desktop.worker_runtime` | `design_locked` | `desktop_compat_opt_in`, `debug_unsafe_lab` | `P14-28` to `P14-32` | `critical` | `worker_threads` native availability, runnable/package/resource gates, and process-worker replacement decision. P14-28 adds the native worker availability audit and refreshes intentional non-support for v1.2 runnable workers until `worker.preflight.status=available` is archived on target ABIs. P14-29 adds the conditional worker runnable/packaged smoke gate and keeps source-only worker anchors from promoting authority while native workers are unavailable. P14-30 adds the conditional worker resource/RSS stress gate and records worker-specific heap/fd/resource archives as future native-available requirements. P14-31 closes the process-worker replacement decision for v1.2 with no user-facing facade and no raw process authority. P14-32 adds the desktop runtime docs/release policy sync gate and records `worker_intentionally_unsupported_v1_2`. |
| `security.packaged_release_gates` | `design_locked` | `safe_default`, `pro_compat_opt_in`, `desktop_compat_opt_in`, `debug_unsafe_lab` | `P14-33`, `P14-34` | `critical` | v1.2 security regression corpus and packaged aggregate smoke. P14-33 adds the v1.2 security regression corpus gate with positive, negative, abuse, runtime guard, promotion precondition, and rollback entries for high-risk provider families while keeping runtime authority unchanged. P14-34 adds the deterministic packaged aggregate smoke entry and report contract for packaged-capable v1.2 surfaces without claiming real device install-run success until archives are attached. |
| `docs.examples.final_gate` | `planned` | `safe_default`, `pro_compat_opt_in`, `desktop_compat_opt_in` | `P14-35` to `P14-39` | `medium` | Type declarations, migration docs, examples, release checklist, Phase 14 summary, and next-branch handoff. P14-35 adds the v1.2 TypeScript declaration profile matrix with metadata-only capability rows for callable, provider POC, stable-denied, intentionally unsupported, and packaged aggregate surfaces while keeping runtime authority unchanged. P14-36 records v1.2 migration and Pro comparison claim sync so docs match executable evidence and do not imply raw Android object parity. P14-37 adds the generated Phase 14 summary gate and release-blocking report classifier while keeping future promotion work visible. |

## Rules

- Every P14 task must map to exactly one surface.
- Every surface must declare at least one target profile.
- Every high or critical risk surface must declare rollback policy text.
- `default_promoted`, `opt_in_promoted`, `debug_poc`, `provider_poc`, and
  `rejected` surfaces must include evidence paths or gates.
- A `critical` surface must not become `default_promoted` without a future
  verifier explicitly approving that state.
- Runtime surface promotion must update type declarations, compatibility docs,
  profile truth reports, security corpus, packaged smoke, rollback docs, and
  the Phase 14 summary gate.

## Verification

- Host verifier: `tools/nodejs/phase14/verify-node-phase14-surface-manifest.js`
- Gradle gate: `:app:verifyNodePhase14SurfaceManifest`
- Schema: `docs/nodejs/schemas/phase14_surface_manifest.schema.json`
