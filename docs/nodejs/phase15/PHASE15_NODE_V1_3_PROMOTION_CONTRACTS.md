# Phase 15 Node v1.3 Promotion Contracts

This document defines the Node Profile v1.3 promotion vocabulary and bars for
Phase 15. The contract does not grant runtime authority. It prevents a provider
from becoming opt-in promoted until implementation, connected device evidence,
packaged behavior, disclosure, rollback, security, TypeScript, docs, Safe
downgrade, and raw-object denial evidence all agree.

Machine-readable source:

- `docs/nodejs/phase15/node-v1-3-promotion-contracts.json`

## Promotion States

| State | Meaning |
| --- | --- |
| `promoted` | Available only through explicit v1.3 Pro/Desktop opt-in profile. |
| `provider_poc` | Provider contract or proof exists, but user-facing promotion is not granted. |
| `future_only` | Tracked as future work and excluded from current release-blocking counts. |
| `stable_denied` | Authority remains denied with stable diagnostics and regression coverage. |
| `rejected` | Authority is intentionally unavailable until a new design record reopens it. |

## Opt-In Promotion Bars

A provider cannot enter `promoted` unless all bars below are present:

- `implementation_gate`
- `connected_device_evidence`
- `packaged_apk_evidence`
- `user_visible_disclosure`
- `rollback_disable_path`
- `security_regression_corpus`
- `typescript_declaration_profile`
- `migration_compat_release_docs`
- `safe_downgrade_behavior`
- `raw_object_denial_regression`

Missing evidence keeps the provider at `future_only`, `provider_poc`, or
`stable_denied` depending on the current design record. Safe remains the
default profile, and raw Android object parity remains rejected.

## Provider Bars

| Provider | Current state | Tasks | Source backlog | Rollback |
| --- | --- | --- | --- | --- |
| `live_ui` | `provider_poc` | `P15-04` | `P14-07` | `disable_live_ui_provider` |
| `overlay` | `provider_poc` | `P15-05` to `P15-07` | `P14-08` to `P14-10` | `disable_overlay_provider` |
| `accessibility` | `provider_poc` | `P15-08` | `P14-11`, `P14-12` | `disable_accessibility_provider` |
| `media_projection` | `provider_poc` | `P15-09` | `P14-13` | `disable_media_projection_provider` |
| `image_ocr` | `provider_poc` | `P15-10` | `P14-14`, `P14-15` | `disable_image_ocr_provider` |
| `task_database` | `provider_poc` | `P15-12` to `P15-14` | `P14-17` to `P14-19` | `disable_task_database_provider` |
| `intent_broadcast` | `provider_poc` | `P15-15`, `P15-16` | `P14-20`, `P14-21` | `disable_intent_broadcast_provider` |
| `notifications` | `provider_poc` | `P15-17` | `P14-22` | `disable_notification_provider` |
| `settings_power` | `provider_poc` | `P15-18` | `P14-23` | `disable_settings_power_provider` |
| `recorder` | `provider_poc` | `P15-19`, `P15-20` | `P14-24`, `P14-25` | `disable_recorder_provider` |
| `mediastore_playback` | `stable_denied` | `P15-21` | `P14-26` | `keep_mediastore_playback_denied` |

## Report Conventions

Phase 15 generated reports use deterministic report names and machine-readable
JSON with markdown mirrors:

- Report name pattern: `phase15-<surface-or-task>.json`
- Markdown mirror: required
- Schema pattern: `autojs6-node-phase15-<surface-or-task>-v1`
- Required report fields: `schema`, `generatedAt`, `gate`, `task`, `status`,
  `checks`, `policyViolations`, `blockers`, and `decision`
- Status values: `passed`, `ready`, `partial`, `failed`, `blocked`, and
  `archived`
- Blocker classifications: `release_blocking`, `future_promotion`, `external`,
  and `intentional_non_goal`

## Verification

- Host verifier: `tools/nodejs/phase15/verify-node-phase15-promotion-contracts.js`
- Gradle gate: `:app:verifyNodePhase15PromotionContracts`
- Reports:
  - `build/reports/nodejs/phase15-promotion-contracts.json`
  - `build/reports/nodejs/phase15-promotion-contracts.md`
