# Phase 12 Rhino Compatibility Summary

Status: P12-35 Phase 12 summary published.

This summary is generated from the Phase 12 task checklist, Phase 12 surface manifest, and final blocker registry.

## Task Coverage

- Total tasks: 37
- Completed tasks: 36
- Not selected branch tasks: 1
- Open tasks: 0

| Task | Status | Surface | Title |
| --- | --- | --- | --- |
| `P12-00` | `completed` | `phase12.baseline` | 创建 Phase 12 Rhino compatibility runtime promotion 任务文档。 |
| `P12-01` | `completed` | `phase12.baseline` | 建立 Phase 12 baseline verification gate。 |
| `P12-02` | `completed` | `phase12.surface_manifest` | 定义 Phase 12 promoted surface manifest。 |
| `P12-03` | `completed` | `events.keys` | 提升 `events.keys` pure constants compatibility。 |
| `P12-04` | `completed` | `input_observer` | 锁定 `input_observer` provider v1 contract。 |
| `P12-05` | `completed` | `input_observer` | 实现 `input_observer` fake provider 和 JS module POC。 |
| `P12-06` | `completed` | `input_observer` | 实现 live key observer provider gate。 |
| `P12-07` | `completed` | `input_observer` | 完成 input observer cleanup/security/packaged policy gate。 |
| `P12-08` | `completed` | `ui.compat` | 锁定 Rhino UI 到 Node JSON layout 的映射子集。 |
| `P12-09` | `completed` | `ui.compat` | 实现 `compat.ui` constrained facade。 |
| `P12-10` | `completed` | `ui.compat` | 增加 UI compat device and packaged smoke。 |
| `P12-11` | `completed` | `floaty.overlay` | 做出 floaty/overlay Phase 12 路径决策。 |
| `P12-12A` | `completed` | `floaty.overlay` | Provider path - 定义 overlay provider v1 contract。 |
| `P12-12B` | `not_selected` | `floaty.overlay` | Rejection path - 固化 floaty permanent non-goal。 |
| `P12-13` | `completed` | `floaty.overlay` | Provider path - 实现 overlay fake provider 和 facade POC。 |
| `P12-14` | `completed` | `floaty.overlay` | Provider path - live overlay packaged and cleanup gate。 |
| `P12-15` | `completed` | `tasks.compat` | 锁定 `compat.tasks` v1 范围。 |
| `P12-16` | `completed` | `tasks.compat` | 实现 `compat.tasks` disposable-task facade。 |
| `P12-17` | `completed` | `tasks.compat` | 决策 daily/weekly/user-task database 兼容路径。 |
| `P12-18` | `completed` | `tasks.compat` | 决策 intent/broadcast task compatibility。 |
| `P12-19` | `completed` | `tasks.compat` | 增加 tasks migration examples and smoke。 |
| `P12-20` | `completed` | `threads.replacement` | 复核 worker_threads 与 process slot 当前成熟度。 |
| `P12-21` | `completed` | `threads.replacement` | 发布 Rhino threads migration cookbook。 |
| `P12-22` | `completed` | `threads.replacement` | 决策 compute/process-worker replacement API。 |
| `P12-23` | `completed` | `rhino.json_only_shim` | 建立 Rhino JSON-only shim POC gate。 |
| `P12-24` | `completed` | `rhino.json_only_shim` | 实现 Rhino shim disabled/default denial path。 |
| `P12-25` | `completed` | `rhino.json_only_shim` | 实现 Rhino JSON-only execution adapter POC。 |
| `P12-26` | `completed` | `rhino.json_only_shim` | 增加 Rhino shim packaged metadata gate。 |
| `P12-27` | `completed` | `barcode.qrcode` | 定义 barcode/qrcode bridge schema。 |
| `P12-28` | `completed` | `barcode.qrcode` | 实现 barcode bridge fake provider and live provider。 |
| `P12-29` | `completed` | `barcode.qrcode` | 实现 `compat.barcode` and `compat.qrcode` facade。 |
| `P12-30` | `completed` | `barcode.qrcode` | 增加 barcode/qrcode examples and packaged smoke。 |
| `P12-31` | `completed` | `unsupported.utility_triage` | 盘点 `unsupported` utility-heavy modules 的 P12 决策。 |
| `P12-32` | `completed` | `unsupported.utility_triage` | 对一个低风险 utility module 做 promotion pilot。 |
| `P12-33` | `completed` | `dual_engine.device_evidence` | 将高价值 dual-engine cases 提升到 device instrumentation。 |
| `P12-34` | `completed` | `dual_engine.device_evidence` | 更新正式迁移文档和 Pro 9 对比文档。 |
| `P12-35` | `completed` | `phase12.summary` | 发布 Phase 12 summary。 |

## Surface Counts

- Total surfaces: 13
- Promoted surfaces: 9
- Rejected surfaces: 0
- Deferred surfaces: 4

| Status | Count |
| --- | ---: |
| `design_locked` | 1 |
| `promoted` | 9 |
| `provider_poc` | 3 |

## Promoted Surfaces

| Surface | Status | Tasks | Summary |
| --- | --- | --- | --- |
| `phase12.baseline` | `promoted` | `P12-00`, `P12-01` | Phase 12 task plan and baseline verification gate. |
| `phase12.surface_manifest` | `promoted` | `P12-02` | Machine-readable manifest for Phase 12 surface promotion state. |
| `events.keys` | `promoted` | `P12-03` | Pure constants facade for Rhino events.keys aliases. |
| `input_observer` | `promoted` | `P12-04`, `P12-05`, `P12-06`, `P12-07` | Controlled key observer contract, fake-source provider POC, accessibility live-source gate, and cleanup/security/packaged rejection gate. |
| `tasks.compat` | `promoted` | `P12-15`, `P12-16`, `P12-17`, `P12-18`, `P12-19` | P12-15 locks compat.tasks v1 scope to disposable one-shot add/remove/query over work_manager with JSON-safe ids/snapshots only. P12-16 promotes require("autojs6:compat").tasks with addDisposableTask/removeTask/queryTasks over work_manager.scheduleOnce/cancel/list while keeping require("tasks") and global tasks absent. P12-17 rejects daily/weekly user task database provider promotion with stable unsupported stubs, adds queryTimedTasks/getTimedTask/removeTimedTask aliases for Node-owned one-shot snapshots, and keeps updateTask blocked_by_design. P12-18 rejects IntentTask/broadcast provider promotion with stable unsupported stubs for addIntentTask/queryIntentTasks/getIntentTask/removeIntentTask, without receiver registration, manifest mutation, custom broadcast authority, or raw Intent exposure. P12-19 adds checked tasks migration examples and RHINO_TASKS_COMPAT.md buckets for supported_async, unsupported, and blocked_by_design cases without provider changes. |
| `barcode.qrcode` | `promoted` | `P12-27`, `P12-28`, `P12-29`, `P12-30` | P12-27 locks the barcode provider v1 JSON-safe schema for barcode and qrcode compatibility. P12-28 promotes the direct require("barcode") provider POC with barcode capability, provider registry health, fake transport conformance, live ML Kit image-handle gate, timeout/cleanup diagnostics, and JSON barcode snapshots. P12-29 adds Rhino-style autojs6:compat.barcode and QR-only autojs6:compat.qrcode facades over that provider. P12-30 adds migration examples plus packaged provider-dependent smoke policy evidence; live packaged execution remains model/provider dependent. |
| `unsupported.utility_triage` | `promoted` | `P12-31`, `P12-32` | P12-31 locks decisions for unsupported utility-heavy modules. P12-32 promotes s13n.bytes as a low-risk pure JS pilot through require("s13n") and autojs6:compat.s13n.bytes, while s13n.color/point/throwable/time and the remaining zip/canvas/recorder/media/mediainfo/opencc/pinyin utility-heavy paths stay deferred behind stable unsupported or future provider/resource gates. |
| `dual_engine.device_evidence` | `promoted` | `P12-33`, `P12-34` | P12-33 adds fake device instrumentation evidence for high-value dual-engine Rhino/Node compatibility cases. The result contract records engine, compatStatus, providerMode, resourceCleanupStatus, and skipReason for each Rhino and Node record while keeping supported_async and blocked_by_design buckets visible. P12-34 publishes the formal migration, Pro 9 comparison, matrix, security, testing, and type declaration guidance plus a developer-docs sync gate. |
| `phase12.summary` | `promoted` | `P12-35` | P12-35 publishes the Phase 12 completion summary generated from the task checklist, surface manifest, and final blocker registry. The summary enumerates P12-00 through P12-35, records promoted/rejected/deferred surface counts, device and packaged evidence, rejected decisions, remaining blockers, and Phase 13 recommendations sourced from actual evidence. |

## Rejected Surfaces

- None.

## Deferred Surfaces

| Surface | Status | Tasks | Summary |
| --- | --- | --- | --- |
| `ui.compat` | `provider_poc` | `P12-08`, `P12-09`, `P12-10` | P12-08 locks the Rhino UI JSON descriptor subset; P12-09 adds constrained autojs6:compat.ui over the existing require("ui") provider; P12-10 adds fake/device instrumentation evidence, packaged smoke markers, and UI_SESSION cleanup reporting while live packaged UI remains unpromoted. |
| `floaty.overlay` | `provider_poc` | `P12-11`, `P12-12A`, `P12-12B`, `P12-13`, `P12-14` | P12-11 selects the Provider path for a controlled JSON-only overlay provider. P12-12A locks the provider v1 contract; P12-13 lands the fake-provider POC for require("ui.overlay"). P12-14 overlay live gate records Android permission/settings/closeAll cleanup evidence, SYSTEM_ALERT_WINDOW packaged metadata, foreground disclosure requirements, and keeps live show/update/drainEvents/close fail-closed. Exact Rhino floaty.window/rawWindow raw-wrapper parity remains blocked_by_design. |
| `threads.replacement` | `design_locked` | `P12-20`, `P12-21`, `P12-22` | P12-20 design-locks Rhino threads replacement maturity: worker_threads remains default-off and native-availability gated, worker_computation remains reserved, second_execution_slot stays open, and no require("threads"), compat.threads, global threads/sync, or Rhino synchronous/shared-object parity is claimed. P12-21 publishes the migration cookbook and dual-engine blocked cases for threads.start, threads.disposable, and threads.sync using async/await, AbortController, explicit engines slots, work_manager, bounded async queues, Promise queues, and MessageChannel-style handoffs. P12-22 defers a compute/process-worker replacement API for Phase 12: no runtime API is promoted, future APIs must be separately named and must pass worker/process permissions, scoped script path, message size, cleanup, nested worker denial, bridge module isolation, resource stress, packaged behavior, and second_execution_slot gates. |
| `rhino.json_only_shim` | `provider_poc` | `P12-23`, `P12-24`, `P12-25`, `P12-26` | P12-23 establishes the default-disabled POC gate for an explicit timeout-bounded Rhino JSON-only run API. P12-24 reserves require("rhino") as a disabled facade: rhino.run() without experimental:true rejects with ERR_AUTOJS6_RHINO_SHIM_DISABLED, rhino.install() throws ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED, require("node:rhino") remains unavailable, node_modules/rhino cannot shadow it, compat.rhino and Rhino globals remain absent. P12-25 adds the explicit experimental Rhino JSON-only execution adapter POC through rhino.run({ experimental: true, source, timeoutMs, args }) and the scoped path variant. P12-26 records packaged metadata behavior as fail-closed: no packaged metadata shape is accepted in Phase 12 and packaged APK execution remains rejected. |

## Rejected Decisions

| Decision | Status | Evidence | Summary |
| --- | --- | --- | --- |
| `threads.rhino_parity` | `blocked_by_design` | `docs/nodejs/reports/phase12/RHINO_THREADS_MIGRATION_COOKBOOK.md` | Exact Rhino threads, sync, shared runtime objects, raw Java Thread handles, and default worker_threads parity remain blocked. |
| `floaty.raw_window_parity` | `blocked_by_design` | `docs/nodejs/design/OVERLAY_PROVIDER_V1.md` | Exact floaty.window/rawWindow raw View wrapper parity is a non-goal; future work must use the JSON-only overlay provider contract. |
| `tasks.daily_weekly_intent` | `unsupported` | `docs/nodejs/design/TASKS_DAILY_WEEKLY_USER_DATABASE_DECISION.md` | Daily/weekly user task database access and IntentTask/broadcast trigger parity stay rejected until dedicated providers exist. |
| `rhino.install_globals` | `unsupported` | `docs/nodejs/reports/phase12/RHINO_SHIM_DISABLED_PATH.md` | rhino.install(), require("node:rhino"), compat.rhino, and default Rhino globals remain unavailable in Node. |
| `s13n.utility_siblings` | `unsupported` | `docs/nodejs/reports/phase12/RHINO_S13N_BYTES_PROMOTION.md` | s13n.color/point/throwable/time are stable unsupported while only s13n.bytes is promoted. |

## Device And Packaged Evidence

| Surface | Evidence | Summary |
| --- | --- | --- |
| `ui.compat` | `docs/nodejs/reports/phase12/RHINO_UI_COMPAT_SMOKE.md` | UI compat has fake/device instrumentation and packaged smoke markers, while live packaged UI remains unpromoted. |
| `floaty.overlay` | `docs/nodejs/reports/phase12/RHINO_OVERLAY_LIVE_GATE.md` | Overlay live gate records permission/settings dry-run, SYSTEM_ALERT_WINDOW packaged metadata, foreground disclosure, and closeAll cleanup evidence. |
| `rhino.json_only_shim` | `docs/nodejs/reports/phase12/RHINO_SHIM_PACKAGED_METADATA_GATE.md` | Rhino JSON-only shim packaged metadata is fail-closed in Phase 12. |
| `barcode.qrcode` | `docs/nodejs/reports/phase12/RHINO_BARCODE_QRCODE_EXAMPLES.md` | Barcode/qrcode has checked migration examples and packaged provider-dependent smoke policy evidence. |
| `dual_engine.device_evidence` | `docs/nodejs/reports/phase12/RHINO_DUAL_ENGINE_DEVICE_EVIDENCE.md` | Dual-engine fake device evidence covers selected Rhino/Node compatibility cases and required result fields. |

## Remaining Blockers

| Blocker | Classification | Status | Required Resolution |
| --- | --- | --- | --- |
| `crash_evidence_automation` | `required_internal` | `open` | Crash marker, next-launch collection, resource snapshot, timeline tail, symbolization, and chaos injection pass focused gates. |
| `android_side_package_installation` | `required_internal` | `open` | Android package install/update/remove/list/verify/prune is transactional, cancellable, locked, integrity checked, and lifecycle-script free. |
| `packaged_long_running` | `required_internal` | `open` | Packaged APK long-running mode has foreground notification, stop action, lifecycle handling, checkpoint, quota, and cleanup evidence. |
| `second_execution_slot` | `required_internal` | `open` | At least two independent Node execution slots are available with isolated ResourceRegistry and crash evidence. |
| `full_heavy_resource_stress` | `required_internal` | `open` | 8-hour quick soak and 24-hour final soak show no monotonic leaks, orphan process, or unrecoverable runtime. |
| `worker_threads` | `conditional` | `decision_required` | Implement controlled worker_threads or declare intentionally unsupported with an AutoJs6 process-worker replacement. |
| `controlled_wasi` | `conditional` | `decision_required` | Either support only WebAssembly or implement a controlled autojs:wasi profile while keeping raw node:wasi disabled. |
| `x86` | `conditional` | `optional` | Optional experiment only; does not block the three required ABIs. |
| `node_24_17_community_fork` | `external_runtime_maintenance` | `waiting_for_android_artifacts` | Future runtime upgrade lane only; does not block Phase 11 completion on node24_5. |

## Phase 13 Recommendations

| Source | Evidence | Recommendation |
| --- | --- | --- |
| `ui.compat` | docs/nodejs/design/RHINO_UI_JSON_LAYOUT_SUBSET.md; docs/nodejs/schemas/rhino_ui_json_layout_subset.schema.json; docs/nodejs/fixtures/rhino-ui-json-layout-subset-fixtures.json | Promote live packaged UI only after Activity lifecycle, Back/Recents, disclosure, and cleanup gates close over the existing P12-10 evidence. |
| `floaty.overlay` | app/src/main/cpp/node_bridge_sources.cpp; app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt; app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt | Continue the provider path with explicit overlay permission, foreground disclosure, live show/update/drainEvents/close behavior, and no raw floaty window parity. |
| `threads.replacement` | docs/nodejs/design/RHINO_THREADS_MIGRATION_POLICY.md; docs/nodejs/design/WORKER_THREADS.md; docs/nodejs/reports/phase12/RHINO_THREADS_MATURITY_REVIEW.md | Split any worker/process replacement from Rhino thread parity and require scoped paths, message budgets, cleanup, nested-worker denial, and second execution slot evidence. |
| `rhino.json_only_shim` | docs/nodejs/design/RHINO_JSON_ONLY_SHIM.md; docs/nodejs/design/RHINO_SHIM.md; docs/nodejs/reports/phase12/RHINO_JSON_ONLY_SHIM_POC_GATE.md | Keep the JSON-only Rhino shim default-disabled unless packaged metadata, timeout, destroy, and crash evidence are promoted together. |
| `crash_evidence_automation` | Crash evidence bundle tooling can import resource snapshots and timeline tails from runtime evidence, but fatal marker, next-launch collection, and chaos injection are not automatic. | Crash marker, next-launch collection, resource snapshot, timeline tail, symbolization, and chaos injection pass focused gates. |
| `android_side_package_installation` | Package manager is host-managed/offline fixture only. | Android package install/update/remove/list/verify/prune is transactional, cancellable, locked, integrity checked, and lifecycle-script free. |
| `packaged_long_running` | Interactive long-running works; packaged service mode remains blocked. | Packaged APK long-running mode has foreground notification, stop action, lifecycle handling, checkpoint, quota, and cleanup evidence. |
| `second_execution_slot` | Explicit primary/overflow_1 one-shot slot selection now routes to independent Android service processes with focused concurrent instrumentation coverage; default scheduler routing, packaged behavior, slot crash evidence, and stress promotion remain open. | At least two independent Node execution slots are available with isolated ResourceRegistry and crash evidence. |
| `full_heavy_resource_stress` | Core long-running/resource stress exists; full heavy stress and 24-hour soak remain incomplete. | 8-hour quick soak and 24-hour final soak show no monotonic leaks, orphan process, or unrecoverable runtime. |
| `worker_threads` | Default disabled and blocked by native availability. | Implement controlled worker_threads or declare intentionally unsupported with an AutoJs6 process-worker replacement. |
| `controlled_wasi` | Pure WebAssembly works; raw node:wasi remains disabled. | Either support only WebAssembly or implement a controlled autojs:wasi profile while keeping raw node:wasi disabled. |

## Verification

- Generator: `tools/nodejs/phase12/generate-rhino-compat-phase12-summary.js`
- Gradle: `:app:generateNodeRhinoCompatPhase12Summary`
- Reports: `build/reports/nodejs/phase12-summary.json` and `build/reports/nodejs/phase12-summary.md`
