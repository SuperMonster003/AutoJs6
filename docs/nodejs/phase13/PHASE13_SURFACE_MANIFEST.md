# Phase 13 Surface Manifest

`phase13-surface-manifest.json` tracks ownership and promotion state for Phase
13 surfaces. It is separate from the Rhino compatibility status manifest: Rhino
status records API-level migration state, while this file records Phase 13
workstreams, target profiles, evidence, risk, and rollback policy.

## Profile Vocabulary

- `safe_default`: the current default Safe Node Profile.
- `pro_compat_opt_in`: explicit Auto.js Pro compatibility profile.
- `desktop_compat_opt_in`: explicit desktop Node.js compatibility profile.
- `debug_unsafe_lab`: debug-only validation profile for high-risk features.
- `permanently_unsupported`: intentionally unavailable after policy review.

## Status Vocabulary

- `planned`: the surface is tracked by Phase 13 but implementation has not
  started.
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
| `phase13.baseline` | `default_promoted` | `safe_default` | `P13-00`, `P13-01` | `low` | Phase 13 checklist and baseline verifier. |
| `phase13.surface_manifest` | `default_promoted` | `safe_default` | `P13-02` | `low` | Machine-readable Phase 13 manifest and verifier. |
| `profile.promotion_model` | `design_locked` | `safe_default`, `pro_compat_opt_in`, `desktop_compat_opt_in`, `debug_unsafe_lab` | `P13-03`, `P13-04`, `P13-10`, `P13-49` | `medium` | Shared profile vocabulary, relaxation rubric, truth reporting, and diagnostics. |
| `runtime.graduation_blockers` | `planned` | `safe_default` | `P13-05` to `P13-09` | `high` | Required internal blockers and runtime graduation. |
| `pro.entry_java_rhino` | `debug_poc` | `pro_compat_opt_in`, `debug_unsafe_lab` | `P13-11` to `P13-16` | `critical` | Pro-style entry directives, `$autojs`, Java interop, and Rhino helpers. P13-11 adds `NodeEntryDirective` metadata and `phase13-pro-entry-directives` evidence; P13-12 adds read-only `$autojs` global v1 and `phase13-autojs-global-v1` evidence; P13-13 adds allowlist-only Java handle calls, field reads, release cleanup, and `phase13-java-interop-allowlist` evidence; P13-14 adds explicit experimental `rhino.install` Java proxy globals and `phase13-rhino-install-java-proxy` evidence; P13-15 records the stable unsupported `importClass`/`importPackage` decision and `phase13-rhino-import-helpers-decision` evidence. UI lifecycle, thread dispatch, `JavaAdapter`, and full Java/Rhino parity remain unpromoted. |
| `pro.ui_automation_media` | `planned` | `pro_compat_opt_in` | `P13-17` to `P13-20` | `high` | Live UI, overlay, accessibility, screenshot, image, and OCR. |
| `pro.tasks_app_shell_media_utils` | `planned` | `pro_compat_opt_in` | `P13-21` to `P13-27` | `high` | Tasks, Intent/broadcast, app helpers, shell/root/Shizuku, media, and utilities. |
| `desktop.network_fs` | `planned` | `desktop_compat_opt_in`, `debug_unsafe_lab` | `P13-28` to `P13-33` | `high` | Node builtin matrix, raw network, broader fs roots, and advanced fs APIs. |
| `desktop.execution_runtime` | `planned` | `desktop_compat_opt_in`, `debug_unsafe_lab` | `P13-34` to `P13-40` | `critical` | Workers, `vm`, inspector, WASI, native addons, and child process. |
| `desktop.modules_npm_process` | `planned` | `desktop_compat_opt_in` | `P13-41` to `P13-44` | `high` | Full ESM, npm/package manager, stdlib, and process parity. |
| `packaging.plugins_distribution` | `planned` | `pro_compat_opt_in`, `desktop_compat_opt_in` | `P13-45` to `P13-48` | `high` | Packaged APK metadata, plugin distribution, dependency corpus, and integrity. |
| `security.rollback_privacy` | `planned` | all runtime profiles | `P13-50` to `P13-52` | `critical` | Relaxed-capability security corpus, rollback, privacy, and disclosure. |
| `docs.examples.final_gate` | `planned` | `safe_default`, `pro_compat_opt_in`, `desktop_compat_opt_in` | `P13-53` to `P13-57` | `medium` | Examples, types, migration docs, and final Phase 13 summary gate. |

Current `runtime.graduation_blockers` evidence includes the P13-05 crash marker
store/host collector, P13-06 default one-shot scheduler gate, P13-07 partial
packaged long-running dispatch gate, P13-08 full heavy stress report gate, and
P13-09 partial Android local package store. The surface remains `planned` until
P13-05 fully closes `crash_evidence_automation`, P13-06 adds
packaged/fatal-crash/stress evidence, P13-07 adds packaged install/run
restore/background-policy evidence, P13-08 has real 8h/24h clean soak reports,
and P13-09 exposes a complete package installation/distribution path.

## Rules

- Every P13 task must map to exactly one surface.
- Every surface must declare at least one target profile.
- Every high or critical risk surface must declare a rollback path.
- `default_promoted`, `opt_in_promoted`, `debug_poc`, and `provider_poc`
  surfaces must include evidence paths or gates.
- A `critical` surface must not become `default_promoted` without a future
  verifier explicitly approving that state.
- Runtime surface promotion must update type declarations, compatibility docs,
  profile truth reports, security corpus, packaged smoke, and rollback docs.

## Verification

- Host verifier: `tools/nodejs/phase13/verify-node-phase13-surface-manifest.js`
- Gradle gate: `:app:verifyNodePhase13SurfaceManifest`
- Schema: `docs/nodejs/schemas/phase13_surface_manifest.schema.json`
