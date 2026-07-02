# Phase 16 Node v1.4 Promotion Contracts

Status: P16-02 host contract ready.

This document records the v1.4 opt-in promotion bars in
[`node-v1-4-promotion-contracts.json`](node-v1-4-promotion-contracts.json).
The gate is `:app:verifyNodePhase16PromotionContracts` and writes
`phase16-promotion-contracts.json` plus `.md`.

## Promotion State Vocabulary

Phase 16 uses these state labels only:

- `promoted`
- `provider_poc`
- `future_only`
- `stable_denied`
- `rejected`

No provider in P16-02 is marked `promoted`. Existing Phase 15 provider POC and
stable-denied work remains imported as v1.4 backlog until each provider-specific
P16 gate supplies the complete evidence set.

## Required Evidence

Provider promotion requires all of these bars:

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

If any bar is absent, the provider must remain `provider_poc`, `future_only`,
`stable_denied`, or `rejected`.

## Provider Bars

The contract maps the Phase 15 backlog into Phase 16 provider bars:

- `live_ui` owns `P16-04` from `P15-04`.
- `overlay` owns `P16-05`, `P16-06`, and `P16-07` from `P15-05` through `P15-07`.
- `accessibility` owns `P16-08` from `P15-08`.
- `media_projection` owns `P16-09` from `P15-09`.
- `image_ocr` owns `P16-10` from `P15-10`.
- `task_database` owns `P16-12`, `P16-13`, and `P16-14` from `P15-12` through `P15-14`.
- `intent_broadcast` owns `P16-15` and `P16-16` from `P15-15` and `P15-16`.
- `notifications` owns `P16-17` from `P15-17`.
- `settings_power` owns `P16-18` from `P15-18`.
- `recorder` owns `P16-19` and `P16-20` from `P15-19` and `P15-20`.
- `mediastore_playback` owns `P16-21` from `P15-21` and remains `stable_denied`.

## Guard Rails

The Safe profile remains default. P16-02 does not grant runtime authority,
does not promote raw Android objects, and does not make packaged APK behavior
implicit. Provider reports must keep their report schema under
`autojs6-node-phase16-<surface-or-task>-v1` and their report files under the
`phase16-` prefix.
