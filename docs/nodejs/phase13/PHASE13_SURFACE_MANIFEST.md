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
| `profile.promotion_model` | `design_locked` | `safe_default`, `pro_compat_opt_in`, `desktop_compat_opt_in`, `debug_unsafe_lab` | `P13-03`, `P13-04`, `P13-10`, `P13-49` | `medium` | Shared profile vocabulary, relaxation rubric, truth reporting, and diagnostic-only profile selection UX with `phase13-profile-selection-diagnostics` evidence. |
| `runtime.graduation_blockers` | `planned` | `safe_default` | `P13-05` to `P13-09` | `high` | Required internal blockers and runtime graduation. |
| `pro.entry_java_rhino` | `debug_poc` | `pro_compat_opt_in`, `debug_unsafe_lab` | `P13-11` to `P13-16` | `critical` | Pro-style entry directives, `$autojs`, Java interop, and Rhino helpers. P13-11 adds `NodeEntryDirective` metadata and `phase13-pro-entry-directives` evidence; P13-12 adds read-only `$autojs` global v1 and `phase13-autojs-global-v1` evidence; P13-13 adds allowlist-only Java handle calls, field reads, release cleanup, and `phase13-java-interop-allowlist` evidence; P13-14 adds explicit experimental `rhino.install` Java proxy globals and `phase13-rhino-install-java-proxy` evidence; P13-15 records the stable unsupported `importClass`/`importPackage` decision and `phase13-rhino-import-helpers-decision` evidence; P13-16 records the stable unsupported `JavaAdapter` / reserved `defineClass` replacement decision and `phase13-rhino-java-adapter-decision` evidence. UI lifecycle, thread dispatch, actual proxy-interface implementation, and full Java/Rhino parity remain unpromoted. |
| `pro.ui_automation_media` | `debug_poc` | `pro_compat_opt_in` | `P13-17` to `P13-20` | `high` | P13-17 records partial live Activity-owned UI provider evidence with `phase13-live-ui-provider-promotion`; P13-18 records partial overlay provider evidence with `phase13-overlay-provider-promotion`; P13-19 records partial accessibility selector evidence with `phase13-accessibility-selector-promotion`; P13-20 records partial screenshot/image/OCR evidence with `phase13-screenshot-image-ocr-promotion`; live packaged UI, live overlay creation, advanced accessibility gestures, promoted MediaProjection, advanced image processing, packaged capture/OCR, Paddle/Rapid fallback, and raw Bitmap/OpenCV profile decisions remain open. |
| `pro.tasks_app_shell_media_utils` | `debug_poc` | `pro_compat_opt_in` | `P13-21` to `P13-27` | `high` | P13-21 records partial task database provider evidence with `phase13-task-database-provider`; P13-22 records partial IntentTask/broadcast evidence with `phase13-intent-broadcast-provider`; P13-23 records partial app/intent/broadcast evidence with `phase13-app-intent-broadcast-parity`; P13-24 records partial shell/root/Shizuku evidence with `phase13-shell-root-shizuku-capability`; P13-25 records partial notifications/settings/power manager evidence with `phase13-notifications-settings-power-manager`; P13-26 records partial media/recorder/mediainfo provider evidence with `phase13-media-recorder-mediainfo-provider`; P13-27 records partial utility-heavy Rhino module evidence with `phase13-utility-heavy-rhino-modules`; live `work_manager`, `compat.tasks` one-shot migration, Android/Rhino task DB substrate, broadcast receiver substrate, JSON-safe `app.intent()`, stricter `app.startActivity()` descriptor parsing, controlled `shell.exec()`, explicit `shell.root`, stable Shizuku rejection, `notifications.getPermissionStatus()`, `notifications.openSettings()`, `device.isIgnoringBatteryOptimizations()`, `device.openBatteryOptimizationSettings()`, read-only `media` audio status, scoped `mediainfo`, `recorder.getStatus()`, compat media aliases, and pure `jsox.mathx` helpers are available, while daily/weekly task database operations, packaged task rows, receiver capability split, manifest synthesis, trigger payloads, install/trigger/remove packaged smokes, `sendBroadcast`, root/Shizuku app options, dual-user helpers, URI/email/settings helpers, notification actions/listeners, wakelocks/direct power control, real root/Shizuku device matrices, playback, real recording, MediaStore, packaged media evidence, `zip`, `canvas`, OpenCC/pinyin packaged resources, sysprops, Arrayx, Numberx, and raw Canvas/Paint or Android/OpenCV object parity remain open. |
| `desktop.network_fs` | `planned` | `desktop_compat_opt_in`, `debug_unsafe_lab` | `P13-28` to `P13-33` | `high` | P13-28 records the Node builtin parity matrix; P13-29 records partial controlled HTTP/HTTPS client evidence with `phase13-http-https-client-parity`; P13-30 records partial limited net/tls/dns facade evidence with `phase13-net-tls-dns-staged-compatibility`; P13-31 records dgram/UDP stable-denied evidence with `phase13-dgram-udp-decision`; P13-32 records Safe filesystem profile diagnostics with `phase13-filesystem-profile-decision`; P13-33 records scoped advanced fs API diagnostics with `phase13-filesystem-advanced-apis`. Raw server/listen, real sockets, network DNS, TLS sessions, UDP, broader fs roots, packaged advanced-fs lifecycle evidence, packaged live-network evidence, full desktop fs semantics, and proxy/TLS session policy remain open. |
| `desktop.execution_runtime` | `planned` | `desktop_compat_opt_in`, `debug_unsafe_lab` | `P13-34` to `P13-40` | `critical` | P13-34 records `worker_threads` opt-in diagnostics and native-preflight evidence with `phase13-worker-threads-opt-in-promotion`; P13-35 records process-worker replacement diagnostics with `phase13-process-worker-replacement-decision`, choosing WorkerPool as the current replacement path without introducing `process_worker`; P13-36 records `vm` diagnostics with `phase13-vm-module-decision` while keeping `vm` denied until context isolation, timeout, memory, escape-denial, and packaged behavior evidence exists; P13-37 records inspector debug-profile diagnostics with `phase13-inspector-debug-profile-decision` while keeping inspector denied until explicit debug session UI, loopback ADB forwarding, DevTools attach, profiler redaction, and packaged behavior evidence exists; P13-38 records controlled WASI diagnostics with `phase13-controlled-wasi-profile-decision` while keeping raw WASI denied until scoped preopen, fd/env/args, resource, worker, and packaged behavior evidence exists; P13-39 records native addon diagnostics with `phase13-native-addon-loading-decision` while keeping `.node`, `process.dlopen`, native builds, and download-exec denied until signed packaged artifacts, ABI/hash allowlists, dependency scan, and crash isolation exist; P13-40 records child_process scoped-runner diagnostics with `phase13-child-process-scoped-runner-decision` while keeping `child_process` denied and `shell` separate until app-private allowlisted binaries, bounded stdio, cancel/kill/cleanup, packaged evidence, and hostile corpus exist; default worker authority, project/package opt-in, native runnable evidence, packaged worker evidence, and worker crash evidence remain open. |
| `desktop.modules_npm_process` | `planned` | `desktop_compat_opt_in` | `P13-41` to `P13-44` | `high` | P13-41 records partial ESM loader diagnostics with `phase13-esm-loader-parity-decision`: `.mjs`, `type=module`, TLA, local static/dynamic import, package exports/imports, JSON import attributes, scoped `import.meta`, and CJS interop are covered by partial gates, while raw loader hooks, network/file/data URL imports, packaged encrypted graph parity, broader ESM package corpus, and pending-evaluation cleanup remain open. P13-42 records Android package-manager substrate diagnostics with `phase13-package-manager-android-decision`: host-managed npm plus app-private Android local store are partial evidence, while Android npm CLI, registry download, lifecycle scripts, bin/native execution, symlink materialization, JS facade, packaged dependency distribution, and real npm corpus growth remain open. P13-43 records partial stdlib conformance with `phase13-stdlib-conformance-decision`: representative Safe subset coverage, Android difference notes, builtin matrix/corpus/differential evidence, and stable denials are present while full desktop stdlib parity, broader hostile inputs, and corpus pass-rate growth remain open. P13-44 records process/env/argv parity as partial with `phase13-process-parity-decision`: `processParityProfile`, focused argv/env/cwd/timing/resource/report/permission evidence, sensitive env filtering, and stable denials for raw exit, signals, native bindings, raw env inheritance, raw process handles, and full desktop process parity are present while lifecycle/signal/package evidence remains open. |
| `packaging.plugins_distribution` | `planned` | `pro_compat_opt_in`, `desktop_compat_opt_in` | `P13-45` to `P13-48` | `high` | P13-45 records packaged APK capability metadata as partial with `phase13-packaged-capability-metadata-decision`: `packagedCapabilityProfile`, `packagedMetadata`, `node.permissions` manifest enforcement, Android permission synthesis evidence, and stable denial for undeclared bridge capabilities are present while profile/builtin/native asset/filesystem-root metadata remains diagnostic-only. P13-46 records plugin system parity as partial with `phase13-plugin-system-parity-decision`: `pluginSystemProfile`, JS-only local manager evidence, host capability ceiling enforcement, and stable denied/deferred Android provider plugin, native plugin, remote marketplace, signed archive, and packaged distribution decisions are present. P13-47 records dependency compatibility corpus as partial with `phase13-dependency-compatibility-corpus`: `phase13Compatibility` manifest taxonomy, `missing_builtin`/`native_addon`/`postinstall`/`network`/`fs` package reason categories, and differential pass-rate rows are present. P13-48 records package lock/integrity policy as partial with `phase13-package-lock-integrity-policy`: `packageIntegrityProfile`, `NodePackageIntegrityPolicy`, Android rollback/prune/corruption recovery evidence, and fail-closed unsigned plugin/native install decisions are present. |
| `security.rollback_privacy` | `planned` | all runtime profiles | `P13-50` to `P13-52` | `critical` | P13-50 records relaxed-capability security corpus as partial with `phase13-relaxed-capability-security-corpus` evidence: raw network, filesystem relaxation, Java interop, `child_process`, native addon, inspector, and WASI have structured positive/negative/abuse cases plus focused fail-closed regression. P13-51 records per-profile rollback playbook as partial with `phase13-profile-rollback-playbook` evidence: default-off Gradle profile switches, `NodeProfileRollbackPolicy`, `autojs6:profile.profileRollbackPolicy`, Node Doctor rollback diagnostics, TypeScript coverage, and packaged Safe-profile downgrade guidance. P13-52 records privacy disclosure policy as partial with `phase13-privacy-disclosure-policy` evidence: `NodePrivacyDisclosurePolicy`, `autojs6:profile.privacyDisclosurePolicy`, Node Doctor Privacy Disclosure Policy diagnostics, TypeScript coverage, and packaged implicit-high-privilege denial. Prompt UI, foreground notification ownership, packaged review UI, automatic crash circuit breaker, and remote packaged kill switch remain future work. |
| `docs.examples.final_gate` | `planned` | `safe_default`, `pro_compat_opt_in`, `desktop_compat_opt_in` | `P13-53` to `P13-57` | `medium` | Examples, types, migration docs, and final Phase 13 summary gate. P13-53 records partial Pro parity examples with `sample/nodejs/pro-parity-suite` and `phase13-pro-parity-examples` evidence. |

Current `runtime.graduation_blockers` evidence includes the P13-05 crash marker
store/host collector, P13-06 default one-shot scheduler gate, P13-07 partial
packaged long-running dispatch gate, P13-08 full heavy stress report gate with
contract fixtures/archive helper, and P13-09 partial Android local package store. The surface
remains `planned` until
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
