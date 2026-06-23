# Phase 12: Rhino Compatibility Runtime Promotion Tasks

盘点日期：2026-06-23

本文件是 Phase 11A 之后的下一阶段任务清单。Phase 11A 已完成 Rhino
`augment` inventory、status manifest、compat matrix、`autojs6:compat`
第一批 facade、示例、双引擎语料、packaged smoke 和资源清理门禁。

Phase 12 的目标不是重新盘点 API，也不是实现 Auto.js Pro 9 的完整
`require("rhino").install()` 语义，而是把 Phase 11A 中仍处于
design-gated、provider gap、partial 或 unsupported 状态的高价值能力拆成
可逐项落地的任务。

## Phase 11A 基线

Phase 12 以以下事实为起点：

- Rhino augment modules：73。
- Rhino API entries：879。
- Node bridge modules with evidence：23。
- Node type modules with evidence：46。
- Matrix status totals：`supported` 134、`supported_async` 52、`partial` 332、
  `renamed` 1、`unsupported` 388、`blocked_by_design` 45。
- 已有显式 compat 入口：`require("autojs6:compat")`。
- 默认不向 V8 安装 Rhino globals；只有
  `compat.install({ globals: true })` 才能显式安装已支持的兼容全局。
- 当前没有运行时 `require("rhino")`、`rhino.run()`、`rhino.install()` 或
  `compat.rhino`。
- `events`/input observer、UI compat、floaty/overlay、tasks、threads、
  Rhino JSON-only shim、barcode/qrcode 是 Phase 11A summary 中列出的主要
  下一阶段方向。

权威输入：

- `docs/nodejs/reports/phase11/PHASE11A_SUMMARY.md`
- `docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md`
- `docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md`
- `docs/nodejs/rhino-compat/status-manifest.json`
- `docs/nodejs/design/RHINO_COMPAT_LOADER.md`
- `docs/nodejs/design/RHINO_EVENTS_INPUT_OBSERVER.md`
- `docs/nodejs/design/RHINO_UI_COMPATIBILITY.md`
- `docs/nodejs/design/RHINO_FLOATY_COMPATIBILITY.md`
- `docs/nodejs/design/RHINO_TASKS_WORK_MANAGER_MIGRATION.md`
- `docs/nodejs/design/RHINO_THREADS_MIGRATION_POLICY.md`
- `docs/nodejs/design/RHINO_JSON_ONLY_SHIM.md`
- `docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json`

## Phase 12 目标

1. 将 Phase 11A 的设计门禁转化为可验证的 runtime provider、compat facade
   或永久非目标决策。
2. 优先补齐脚本开发者迁移 Rhino 脚本时最容易卡住的能力：按键事件、UI、
   悬浮窗、任务、线程替代、条码/二维码。
3. 每新增一个 Node runtime surface，都必须同步 bridge permission、
   type declarations、status manifest、compat matrix、迁移文档、host gate、
   device gate 或明确的不可运行原因。
4. 保持 Safe Node Profile 边界：不泄漏 raw Java/Rhino/Android object，
   不默认开放高风险系统能力，不把同步 Rhino 对象模型伪装成 Node 同步 API。
5. 为后续 Phase 13 或 release gate 提供明确 evidence，而不是只新增设计文档。

## 非目标

Phase 12 仍不包含：

- 默认安装所有 Rhino globals。
- 实现 Pro 9 风格的 `require("rhino").install()`。
- 把 `Scriptable`、`NativeObject`、`Context`、`Activity`、`View`、
  `AccessibilityNodeInfo`、`WindowManager`、`FloatyWindow`、`TimedTask`、
  `IntentTask`、`Thread`、`ThreadPoolExecutor`、`KeyEvent`、`MotionEvent`
  或任何 Java raw object 暴露给 Node。
- 让 `require("events")` 指向 Rhino `events`；Node core `events` 不能被
  Rhino compat 覆盖。
- 通过 overlay、shell、root、accessibility 或 hidden service 实现隐式按键
  监听、触摸监听或悬浮窗权限绕过。
- 解除 native addon、unrestricted `child_process`、raw network、raw WASI、
  private Node bindings、production Inspector 的既有拒绝策略。

## 提交规则

每个 P12 task 应独立提交。提交只覆盖该 task 的代码、测试、文档和报告产物。

推荐提交信息格式：

```text
nodejs phase12 P12-03: add compat events keys facade
```

每个 task 完成时需要：

- 将本文件对应 checkbox 改为 `[x]`。
- 在 task 下方补充 Evidence，列出路径、命令或设备验证摘要。
- 如果新增 runtime surface，必须同时更新 types、docs、status manifest 和
  compat matrix。
- 如果 task 选择永久非目标，需要补充 blocked reason、稳定错误码、负向 gate
  和迁移建议。
- 如果因设备、权限、模型资源或 Android 版本无法运行，需要记录明确原因和
  可复现命令。
- 不把多个 P12 task 混入同一个提交。

## 验收状态词汇

Phase 12 继续沿用 Phase 11A 的 API 状态：

- `supported`
- `supported_async`
- `renamed`
- `partial`
- `blocked_by_design`
- `unsupported`

Phase 12 task 本身可使用以下执行状态：

- `planned`：只在本文件中列出，尚未实现。
- `design_locked`：设计、边界、错误码和 gate 计划已冻结。
- `provider_poc`：已有 fake 或 live provider POC，但不代表正式支持。
- `promoted`：runtime surface、types、docs、host/device gate 均已落地。
- `rejected`：明确成为永久非目标，并有负向 gate。

## 总体 Checklist

### Track 0: Phase bootstrap and baseline gates

- [x] P12-00: 创建 Phase 12 Rhino compatibility runtime promotion 任务文档。
  - 范围：基于 Phase 11A summary 的 P12-01 到 P12-09 拆分新阶段任务。
  - 产物：`docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md`
  - 验收：文档包含目标、边界、提交规则、任务拆分、验收标准和执行顺序。
  - Evidence：本文件。

- [x] P12-01: 建立 Phase 12 baseline verification gate。
  - 范围：新增 Phase 12 专用 host verifier，确认 Phase 11A inventory、matrix、
    status manifest 和 summary 仍可作为输入。
  - 建议产物：
    - `tools/nodejs/phase12/verify-rhino-compat-phase12-baseline.js`
    - Gradle task：`:app:verifyNodeRhinoCompatPhase12Baseline`
    - `build/reports/nodejs/phase12-baseline-verification.json`
    - `build/reports/nodejs/phase12-baseline-verification.md`
  - 验收：
    - S11-00 到 S11-31 仍全部完成。
    - Phase 11A summary 中 P12-01 到 P12-09 anchor 仍存在。
    - `status-manifest.json` 覆盖 73 个 Rhino module 和 879 个 API entry。
    - 缺少任一权威输入时 gate 失败。
  - Evidence：
    - Host verifier：`tools/nodejs/phase12/verify-rhino-compat-phase12-baseline.js`
    - Gradle gate：`:app:verifyNodeRhinoCompatPhase12Baseline`
    - 报告产物：`build/reports/nodejs/phase12-baseline-verification.json`
    - 报告产物：`build/reports/nodejs/phase12-baseline-verification.md`
    - Gate 锁定 Phase 11A inventory、summary anchor、status manifest 覆盖和
      final blocker registry 的 Phase 12 输入边界。

- [x] P12-02: 定义 Phase 12 promoted surface manifest。
  - 范围：新增人工维护 manifest，记录每个 P12 runtime surface 的状态：
    `planned`、`design_locked`、`provider_poc`、`promoted` 或 `rejected`。
  - 建议产物：
    - `docs/nodejs/rhino-compat/phase12-surface-manifest.json`
    - `docs/nodejs/rhino-compat/PHASE12_SURFACE_MANIFEST.md`
    - verifier 与 schema fixture
  - 验收：
    - 每个 P12 task 都能映射到一个 surface 或 explicit non-goal。
    - runtime surface 被新增但 manifest 未更新时 gate 失败。
    - manifest 中的 `promoted` surface 必须包含 docs、types、tests 和
      resource cleanup evidence。
  - Evidence：
    - Manifest：`docs/nodejs/rhino-compat/phase12-surface-manifest.json`
    - 文档：`docs/nodejs/rhino-compat/PHASE12_SURFACE_MANIFEST.md`
    - Host verifier：`tools/nodejs/phase12/verify-rhino-compat-phase12-surface-manifest.js`
    - Gradle gate：`:app:verifyNodeRhinoCompatPhase12SurfaceManifest`
    - 报告产物：`build/reports/nodejs/phase12-surface-manifest-verification.json`
    - 报告产物：`build/reports/nodejs/phase12-surface-manifest-verification.md`

### Track A: Events, keys, and input observer

- [x] P12-03: 提升 `events.keys` pure constants compatibility。
  - 范围：只实现 Rhino `events.keys` 的常量映射，不实现 observer。
  - 建议入口：
    - `compat.events.keys`
    - `compat.keys`
    - 可选显式 global：`keys`，仅在 `compat.install({ globals: true })` 后安装。
  - 边界：
    - 不覆盖 Node core `require("events")`。
    - 不新增 key/touch listener。
    - 不泄漏 Android `KeyEvent` class 或 raw event。
  - 建议产物：
    - `autojs6:compat` runtime facade
    - `docs/nodejs/types/autojs6-node/rhino_compat.d.ts`
    - `tools/nodejs/phase12/verify-rhino-compat-events-keys-facade.js`
  - 验收：
    - 覆盖 Rhino `events.keys` 中的主要 key name 和 numeric code。
    - 常量对象 frozen、只读、JSON-safe。
    - 默认不注入 global。
    - status manifest 中 `events.keys.*` 从 `unsupported` 更新为
      `supported` 或 `renamed`。
  - Evidence：
    - Runtime：`app/src/main/cpp/node_bridge_sources.cpp`
    - Types：`docs/nodejs/types/autojs6-node/rhino_compat.d.ts`
    - Type docs：`docs/nodejs/types/autojs6-node/README.md`
    - Status manifest：`docs/nodejs/rhino-compat/status-manifest.json`
    - Matrix：`docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md`
    - Design docs：`docs/nodejs/design/RHINO_COMPAT_LOADER.md`,
      `docs/nodejs/design/RHINO_EVENTS_INPUT_OBSERVER.md`
    - Host verifier：`tools/nodejs/phase12/verify-rhino-compat-events-keys-facade.js`
    - Gradle gate：`:app:verifyNodeRhinoCompatEventsKeysFacade`
    - 报告产物：`build/reports/nodejs/rhino-compat-events-keys-facade-verification.json`
    - 报告产物：`build/reports/nodejs/rhino-compat-events-keys-facade-verification.md`

- [x] P12-04: 锁定 `input_observer` provider v1 contract。
  - 范围：将 S11-21 设计门禁变成可实现的 provider contract。
  - 建议 API：
    - `require("input_observer").observeKeys(descriptor, options?)`
    - `subscription.drainEvents(options?)`
    - `subscription.close(options?)`
    - `inputObserver.getAvailableSources()`
  - 边界：
    - `input_observer.intercept` 必须独立能力声明。
    - v1 优先只做 key observation；touch observation 可继续 design-gated。
    - 后台/packaged 不得静默开启监听。
  - 建议产物：
    - `docs/nodejs/design/INPUT_OBSERVER_PROVIDER_V1.md`
    - capability manifest diff
    - type declaration draft
    - fake-provider request/response schema
  - 验收：
    - JSON event schema 明确包含 `source`、`key`、`keyCode`、`action`、
      `eventTime`、`repeatCount`。
    - queue size、event size、subscription count、timeout 和 close 行为有上限。
    - raw `KeyEvent`、`MotionEvent`、listener、thread 不跨过 bridge。
  - Evidence：
    - Contract：`docs/nodejs/design/INPUT_OBSERVER_PROVIDER_V1.md`
    - Schema fixture：`docs/nodejs/schemas/input_observer_provider_v1.schema.json`
    - 既有设计同步：`docs/nodejs/design/RHINO_EVENTS_INPUT_OBSERVER.md`
    - Loader 边界同步：`docs/nodejs/design/RHINO_COMPAT_LOADER.md`
    - Surface manifest：`docs/nodejs/rhino-compat/phase12-surface-manifest.json`
    - Host verifier：`tools/nodejs/phase12/verify-rhino-compat-input-observer-contract.js`
    - Gradle gate：`:app:verifyNodeRhinoCompatInputObserverContract`
    - 报告产物：`build/reports/nodejs/rhino-compat-input-observer-contract-verification.json`
    - 报告产物：`build/reports/nodejs/rhino-compat-input-observer-contract-verification.md`

- [x] P12-05: 实现 `input_observer` fake provider 和 JS module POC。
  - 范围：先使用 fake provider 验证 JS API、capability denial、queue drain 和
    cleanup，不要求 live Android key capture。
  - 建议产物：
    - `NodeBridgeModules.INPUT_OBSERVER`
    - `NodeBridgePermissionManifest.INPUT_OBSERVER`
    - runtime module source
    - `docs/nodejs/types/autojs6-node/input_observer.d.ts`
    - conformance instrumentation fake transport test
  - 验收：
    - `observeKeys()` 返回 opaque subscription handle。
    - `drainEvents()` 只返回 JSON snapshots。
    - `close()` 幂等。
    - destroy 后 active observer count 为 0。
    - 未声明 capability 时 bridge dispatch 前失败。
  - Evidence：
    - Runtime module：`app/src/main/cpp/node_bridge_sources.cpp`
    - Bridge registry/provider：`app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt`
    - Capability manifest：`app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt`
    - Resource cleanup type：`app/src/main/java/org/autojs/autojs/engine/NodeBridgeResourceRegistry.kt`
    - Provider health：`app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt`
    - Types：`docs/nodejs/types/autojs6-node/input_observer.d.ts`
    - Instrumentation fake transport：`app/src/androidTest/java/org/autojs/autojs/engine/NodeInputObserverBridgeInstrumentationTest.kt`
    - Host verifier：`tools/nodejs/phase12/verify-rhino-compat-input-observer-fake-provider.js`
    - Gradle gate：`:app:verifyNodeRhinoCompatInputObserverFakeProvider`
    - 报告产物：`build/reports/nodejs/rhino-compat-input-observer-fake-provider-verification.json`
    - 报告产物：`build/reports/nodejs/rhino-compat-input-observer-fake-provider-verification.md`

- [x] P12-06: 实现 live key observer provider gate。
  - 范围：在可用 Android 环境上接入受控 key observation；如果 live path
    不可行，需要将 task 转为明确的 design-locked blocker。
  - 建议产物：
    - live provider implementation
    - Android instrumentation test
    - Doctor provider health entry
    - live-provider summary 集成
  - 验收：
    - provider unavailable、permission denied、capability denied 有稳定错误。
    - close、timeout、engine stop、service destroy 后 listener 全部释放。
    - 不启用 key interception；intercept 仍需单独 task。
    - packaged 模式无前台/可见状态时拒绝。
  - Evidence：
    - Live provider gate：`app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt`
    - Accessibility disconnect cleanup：`app/src/main/java/org/autojs/autojs/core/accessibility/AccessibilityService.kt`
    - Doctor provider health：`app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt`
    - Runtime facade boundary：`app/src/main/cpp/node_bridge_sources.cpp`
    - Android instrumentation accessibility live source gate：`app/src/androidTest/java/org/autojs/autojs/engine/NodeInputObserverBridgeInstrumentationTest.kt`
    - Surface manifest：`docs/nodejs/rhino-compat/phase12-surface-manifest.json`
    - Host verifier：`tools/nodejs/phase12/verify-rhino-compat-input-observer-live-gate.js`
    - Gradle gate：`:app:verifyNodeRhinoCompatInputObserverLiveGate`
    - 限制：packaged foreground smoke / rejection report 仍由 P12-07 收口。
    - 报告产物：`build/reports/nodejs/rhino-compat-input-observer-live-gate-verification.json`
    - 报告产物：`build/reports/nodejs/rhino-compat-input-observer-live-gate-verification.md`

- [x] P12-07: 完成 input observer cleanup/security/packaged policy gate。
  - 范围：把 P12-05/P12-06 的资源释放、安全拒绝和 packaged 行为纳入统一 gate。
  - 建议产物：
    - `tools/nodejs/phase12/verify-rhino-compat-input-observer-security.js`
    - `docs/nodejs/reports/phase12/RHINO_INPUT_OBSERVER_SECURITY.md`
    - packaged smoke 或明确的 packaged rejection report
  - 验收：
    - active subscription、pending event、listener count 在 destroy 后归零。
    - queue overflow 被记录且不会 OOM。
    - hidden listener、silent intercept、overlay-based capture、shell fallback
      均有负向验证。
  - Evidence：
    - Security report：`docs/nodejs/reports/phase12/RHINO_INPUT_OBSERVER_SECURITY.md`
    - Runtime cleanup/diagnostics：`app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt`
    - Accessibility disconnect callback：`app/src/main/java/org/autojs/autojs/core/accessibility/AccessibilityService.kt`
    - Execution mode policy：`app/src/main/java/org/autojs/autojs/engine/NodeExecutionModePolicy.kt`
    - Provider packaged policy：`app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt`
    - Negative/overflow instrumentation：`app/src/androidTest/java/org/autojs/autojs/engine/NodeInputObserverBridgeInstrumentationTest.kt`
    - Host verifier：`tools/nodejs/phase12/verify-rhino-compat-input-observer-security.js`
    - Gradle gate：`:app:verifyNodeRhinoCompatInputObserverSecurity`
    - 报告产物：`build/reports/nodejs/rhino-compat-input-observer-security-verification.json`
    - 报告产物：`build/reports/nodejs/rhino-compat-input-observer-security-verification.md`

### Track B: UI compatibility runtime facade

- [x] P12-08: 锁定 Rhino UI 到 Node JSON layout 的映射子集。
  - 范围：把 S11-23 设计文档中的可迁移 UI DSL 子集转成明确 schema。
  - 建议产物：
    - `docs/nodejs/design/RHINO_UI_JSON_LAYOUT_SUBSET.md`
    - layout descriptor schema
    - fixture：可接受、拒绝、迁移提示
  - 验收：
    - 只接受 JSON descriptor，不接受 XML raw string、executable attribute、
      function binding 或 Rhino `NativeView`。
    - 明确 `ui.layout(...)`、`ui.layoutFile(...)`、`ui.findById(...)`、
      `ui.run()`、`ui.post()` 的迁移状态。
    - `ui.run()` 和 `ui.post()` 继续 `blocked_by_design`。
  - Evidence：
    - Subset contract：`docs/nodejs/design/RHINO_UI_JSON_LAYOUT_SUBSET.md`
    - Layout descriptor schema：`docs/nodejs/schemas/rhino_ui_json_layout_subset.schema.json`
    - Fixtures：`docs/nodejs/fixtures/rhino-ui-json-layout-subset-fixtures.json`
    - Existing UI design：`docs/nodejs/design/RHINO_UI_COMPATIBILITY.md`
    - Existing UI bridge：`docs/nodejs/design/UI_BRIDGE.md`
    - Runtime JSON preflight：`app/src/main/cpp/node_bridge_sources.cpp`
    - Provider validation：`app/src/main/java/org/autojs/autojs/engine/NodeBridgeUiProvider.kt`
    - Host verifier：`tools/nodejs/phase12/verify-rhino-compat-ui-json-layout-subset.js`
    - Gradle gate：`:app:verifyNodeRhinoCompatUiJsonLayoutSubset`
    - 报告产物：`build/reports/nodejs/rhino-compat-ui-json-layout-subset-verification.json`
    - 报告产物：`build/reports/nodejs/rhino-compat-ui-json-layout-subset-verification.md`

- [x] P12-09: 实现 `compat.ui` constrained facade。
  - 范围：在 `autojs6:compat` 中新增受限 `compat.ui`，映射到现有
    `require("ui")` 的 `showLayout`、`update`、`batchUpdate`、`on`、`close`。
  - 边界：
    - 不新增默认 global `ui`。
    - 如果支持 explicit global `ui`，必须只在 `install({ globals: true })`
      后暴露受限 facade。
    - 不实现 `ui.run()`、`ui.post()`、raw view lookup。
  - 建议产物：
    - runtime facade
    - `rhino_compat.d.ts`
    - host gate
    - TypeScript smoke
  - 验收：
    - `compat.ui.showLayout(json)` 返回 opaque `UiHandle`。
    - event callbacks 在 Node event loop 上处理 JSON snapshots。
    - invalid descriptor 在 bridge dispatch 前失败。
    - status manifest 中可迁移 UI API 更新为 `partial` 或
      `supported_async`，raw view API 保持 `blocked_by_design`。
  - Evidence：
    - Runtime facade：`app/src/main/cpp/node_bridge_sources.cpp`
    - Types：`docs/nodejs/types/autojs6-node/rhino_compat.d.ts`
    - TypeScript smoke：`sample/nodejs/typescript-smoke/src/main.ts`
    - Status manifest：`docs/nodejs/rhino-compat/status-manifest.json`
    - Fake-transport instrumentation：`app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt`
    - Subset contract：`docs/nodejs/design/RHINO_UI_JSON_LAYOUT_SUBSET.md`
    - Host verifier：`tools/nodejs/phase12/verify-rhino-compat-ui-facade.js`
    - Gradle gate：`:app:verifyNodeRhinoCompatUiFacade`
    - 报告产物：`build/reports/nodejs/rhino-compat-ui-facade-verification.json`
    - 报告产物：`build/reports/nodejs/rhino-compat-ui-facade-verification.md`

- [x] P12-10: 增加 UI compat device and packaged smoke。
  - 范围：验证 `compat.ui` 在设备侧和 packaged 场景下的最小路径。
  - 建议产物：
    - Android instrumentation test
    - packaged smoke marker
    - `docs/nodejs/reports/phase12/RHINO_UI_COMPAT_SMOKE.md`
  - 验收：
    - show/update/batchUpdate/event/close 路径可运行或有明确 skip reason。
    - stop、timeout、destroy 后 `UI_SESSION` resource 为 0。
    - packaged 若不支持 live UI，必须稳定拒绝且不留下 pending bridge calls。
  - Evidence：
    - 报告：`docs/nodejs/reports/phase12/RHINO_UI_COMPAT_SMOKE.md`
    - Packaged smoke marker：`packaged.rhino_compat.compat_ui=true`
    - Packaged smoke marker：`packaged.rhino_compat.pending=0`
    - Packaged instrumentation：`app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt`
    - Fake-transport instrumentation：`app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt`
    - Resource cleanup instrumentation：`app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeResourceLeakStressInstrumentationTest.kt`
    - Provider cleanup：`app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt`
    - Host verifier：`tools/nodejs/phase12/verify-rhino-compat-ui-smoke.js`
    - Gradle gate：`:app:verifyNodeRhinoCompatUiSmoke`
    - 报告产物：`build/reports/nodejs/rhino-compat-ui-smoke-verification.json`
    - 报告产物：`build/reports/nodejs/rhino-compat-ui-smoke-verification.md`

### Track C: Floaty and overlay decision

- [ ] P12-11: 做出 floaty/overlay Phase 12 路径决策。
  - 范围：从两个路径中选择一个，并记录理由：
    - Provider path：实现受控 overlay provider。
    - Rejection path：将 Rhino `floaty.window/rawWindow` 设为更长期或永久非目标。
  - 建议产物：
    - `docs/nodejs/design/FLOATY_OVERLAY_PHASE12_DECISION.md`
    - status manifest 更新
    - matrix 更新
  - 验收：
    - 不能同时声称 provider path 和 permanent rejection。
    - 决策必须覆盖 permission、foreground disclosure、packaged manifest、
      closeAll/destroy cleanup、raw window object denial。
  - Evidence：待补充。

- [ ] P12-12A: Provider path - 定义 overlay provider v1 contract。
  - 适用条件：P12-11 选择 Provider path。
  - 范围：只设计 JSON descriptor overlay 和 opaque handle，不兼容 Rhino raw
    window object。
  - 建议 API：
    - `require("ui.overlay").show(descriptor, options?)`
    - `OverlayHandle.update(patch)`
    - `OverlayHandle.close()`
    - `closeAll()`
    - `hasPermission()`
    - `openPermissionSettings()`
  - 验收：
    - `ui.overlay` 或 `floaty` capability 不得被 base `ui` 暗含。
    - 没有 hidden overlay；必须有用户可见 disclosure。
    - raw `WindowManager`、`LayoutParams`、`FloatyWindow` 不跨 bridge。
  - Evidence：待补充。

- [ ] P12-12B: Rejection path - 固化 floaty permanent non-goal。
  - 适用条件：P12-11 选择 Rejection path。
  - 范围：把 Rhino `floaty.window`、`floaty.rawWindow`、overlay permission
    flow 设为长期或永久非目标，同时保留 `floaty.getClip` 的 clipboard 迁移路径。
  - 建议产物：
    - negative runtime gate
    - migration doc 更新
    - stable error code 文档
  - 验收：
    - `require("floaty")`、`require("ui.overlay")`、`compat.floaty`、
      global `floaty` 均不可用或稳定拒绝。
    - migration docs 明确建议改用 `clipboard`、`compat.getClip()` 或
      Activity-owned `require("ui")`。
  - Evidence：待补充。

- [ ] P12-13: Provider path - 实现 overlay fake provider 和 facade POC。
  - 适用条件：P12-11 选择 Provider path。
  - 范围：先以 fake provider 验证 JS shape、capability、handle lifecycle、
    closeAll 和 denial path。
  - 建议产物：
    - bridge module
    - permission manifest entries
    - types
    - conformance fake-provider tests
  - 验收：
    - provider fake path 不打开真实 overlay。
    - handle close/destroy 后 active overlay count 为 0。
    - 未声明 overlay capability 时 dispatch 前失败。
  - Evidence：待补充。

- [ ] P12-14: Provider path - live overlay packaged and cleanup gate。
  - 适用条件：P12-11 选择 Provider path 且 P12-13 已完成。
  - 范围：验证真实 overlay 或明确设备 skip policy。
  - 验收：
    - permission absent、permission settings、foreground disclosure、closeAll、
      execution stop、packaged manifest 均有记录。
    - 没有后台静默 overlay。
    - raw window/control object 不泄漏。
  - Evidence：待补充。

### Track D: Tasks compatibility over WorkManager and task database

- [ ] P12-15: 锁定 `compat.tasks` v1 范围。
  - 范围：从 S11-22 的 migration design 中选出可落地子集。
  - 推荐 v1 子集：
    - `compat.tasks.addDisposableTask({ path, date|delayMs, ... })`
    - `compat.tasks.removeTask(id)`
    - `compat.tasks.queryTasks(options?)`
  - 边界：
    - 不返回 raw `TimedTask` 或 `IntentTask`。
    - 不直接暴露 Android task database model。
    - `addDailyTask`、`addWeeklyTask`、`addIntentTask` 必须单独决策。
  - 验收：
    - v1 明确映射到 `work_manager`，而不是 Rhino task database parity。
    - status manifest 中每个 `tasks.*` API 有新的 P12 决策。
  - Evidence：待补充。

- [ ] P12-16: 实现 `compat.tasks` disposable-task facade。
  - 范围：将 Rhino disposable task 的常见脚本迁移到
    `work_manager.scheduleOnce()`。
  - 建议产物：
    - `autojs6:compat.tasks`
    - types
    - host gate
    - fake/live work_manager bridge tests
  - 验收：
    - scoped path validation 与 `work_manager` 保持一致。
    - 返回 JSON-safe task snapshot 或 id，不返回 raw task object。
    - cancel/list 能清理 ResourceRegistry task registration。
    - packaged queued fallback 不能创建一方向持久任务。
  - Evidence：待补充。

- [ ] P12-17: 决策 daily/weekly/user-task database 兼容路径。
  - 范围：评估 `tasks.addDailyTask`、`addWeeklyTask`、`queryTimedTasks`、
    `getTimedTask`、`updateTimedTask` 是否进入 Node provider 或保持拒绝。
  - 验收：
    - 如果进入 provider，必须有 task ownership、database schema、migration
      snapshot、update/delete policy 和 scheduler backend policy。
    - 如果拒绝，必须有稳定错误码和迁移建议。
    - 不把 daily/weekly 机械映射到 `schedulePeriodic()`。
  - Evidence：待补充。

- [ ] P12-18: 决策 intent/broadcast task compatibility。
  - 范围：处理 `tasks.addIntentTask`、`queryIntentTasks`、broadcast triggers。
  - 验收：
    - 不暴露 raw `BroadcastReceiver`、`IntentFilter`、`PendingIntent`、
      custom broadcast authority。
    - boot/package-event/exact-alarm 等高风险触发必须单独 capability。
    - packaged 行为必须明确。
  - Evidence：待补充。

- [ ] P12-19: 增加 tasks migration examples and smoke。
  - 范围：为 disposable task、daily/weekly rejection 或 provider path、
    intent-task rejection 或 provider path 增加示例和 gate。
  - 建议产物：
    - `docs/nodejs/examples/rhino-compat/tasks.*`
    - `docs/nodejs/reports/phase12/RHINO_TASKS_COMPAT.md`
  - 验收：
    - 示例能被 example gate 编译。
    - supported_async 与 blocked_by_design cases 在报告中分桶。
  - Evidence：待补充。

### Track E: Threads replacement and worker/process policy

- [ ] P12-20: 复核 worker_threads 与 process slot 当前成熟度。
  - 范围：基于已有 worker_threads experimental tests、process slot、
    `second_execution_slot` blocker，决定 Phase 12 是否能承接 Rhino threads
    迁移的一部分。
  - 验收：
    - 默认仍不开放 `worker_threads`。
    - 如果推荐 experimental worker path，必须说明 native availability、
      capability isolation、resource cleanup 和 packaged 状态。
    - 不声称兼容 Rhino 同步线程和共享对象模型。
  - Evidence：待补充。

- [ ] P12-21: 发布 Rhino threads migration cookbook。
  - 范围：为常见 Rhino `threads` 模式提供 Node 替代写法。
  - 覆盖模式：
    - `threads.start(fn)` -> `async`/`await` 或显式 engine/process slot。
    - `threads.interrupt()` -> `AbortController`、timeout 或 script stop。
    - `threads.pool()` -> future worker policy 或 bounded async queue。
    - `sync(fn, lock)` -> single-owner state、Promise queue、MessageChannel。
  - 验收：
    - cookbook 示例不依赖默认 `worker_threads`。
    - `threads.*` status 仍为 `blocked_by_design` 或明确 `renamed` 迁移。
    - dual-engine corpus 增加至少 2 个 blocked/migrated cases。
  - Evidence：待补充。

- [ ] P12-22: 决策 compute/process-worker replacement API。
  - 范围：如果 Phase 12 需要给 Rhino `threads` 用户提供更强替代能力，
    设计单独命名的 compute/process-worker API；否则明确延后。
  - 验收：
    - API 不复用 `threads` 名称，避免误导为 Rhino parity。
    - worker/process 权限、scoped script path、message size、cleanup、
      nested worker denial、bridge module isolation 都有 gate。
    - 与 `second_execution_slot` blocker 的关系明确，不越权关闭 final blocker。
  - Evidence：待补充。

### Track F: Explicit Rhino JSON-only shim POC

- [ ] P12-23: 建立 Rhino JSON-only shim POC gate。
  - 范围：把 S11-26 设计转成默认禁用的 POC gate。
  - 建议 API：
    - `const rhino = require("rhino")`
    - `await rhino.run({ source, timeoutMs, args })`
    - `await rhino.run({ path, timeoutMs, args })`
  - 默认状态：
    - release/default Safe Node Profile 中仍应拒绝。
    - POC 只能通过显式 experimental flag 或 test metadata 开启。
  - 验收：
    - `rhino.install()` 永远拒绝并返回
      `ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED`。
    - JSON validator 能拒绝 function、symbol、bigint、undefined、cycle、
      prototype object、handle、Java object marker。
    - 不向 V8 安装 Rhino globals。
  - Evidence：待补充。

- [ ] P12-24: 实现 Rhino shim disabled/default denial path。
  - 范围：在默认 profile 下固定 `require("rhino")` 或 `rhino.run()` 的稳定
    拒绝行为。是否允许 `require("rhino")` 返回 disabled facade 由 P12-23 决策。
  - 验收：
    - 默认无 experimental flag 时不能执行 Rhino。
    - denial error code、message、docs、types 一致。
    - `rhino.install()` 不因 experimental flag 变为可用。
  - Evidence：待补充。

- [ ] P12-25: 实现 Rhino JSON-only execution adapter POC。
  - 范围：通过 script manager 或 dedicated compatibility process 执行小段
    Rhino source/path，返回 JSON-safe result。
  - 验收：
    - 必须 timeout-bounded。
    - stop/destroy 后 Rhino engine 释放。
    - stdout/stderr 或 console 摘要有上限。
    - Rhino exception 转为稳定 JSON error object。
    - 不能返回 raw Rhino/Java/Android object。
  - Evidence：待补充。

- [ ] P12-26: 增加 Rhino shim packaged metadata gate。
  - 范围：如果 P12-25 支持 packaged，则必须要求显式 packaged metadata
    opt-in；否则记录 packaged rejection。
  - 验收：
    - 无 metadata 的 APK 不能执行 shim。
    - metadata 只授权 JSON-only run，不授权 `install()` 或 globals。
    - packaged timeout、crash、destroy 有 evidence。
  - Evidence：待补充。

### Track G: Barcode and qrcode JSON-safe provider

- [ ] P12-27: 定义 barcode/qrcode bridge schema。
  - 范围：把 Rhino `barcode`、`qrcode` 的 MLKit 结果转成 Node JSON-safe
    snapshot contract。
  - 建议结果字段：
    - `rawValue`
    - `displayValue`
    - `format`
    - `formatName`
    - `valueType`
    - `valueTypeName`
    - `boundingBox`
    - `cornerPoints`
    - selected structured payloads as JSON
  - 验收：
    - 不暴露 MLKit `Barcode`、`WrappedBarcode`、`ImageWrapper`、`Bitmap`。
    - image input 支持 path、opaque image handle、existing-permission capture。
    - region、format filter、QR-only mode 有明确 schema。
  - Evidence：待补充。

- [ ] P12-28: 实现 barcode bridge fake provider and live provider。
  - 范围：新增受控 `barcode` bridge module，先 fake provider，再接入 live
    MLKit provider。
  - 建议产物：
    - `NodeBridgeModules.BARCODE`
    - permission manifest capability：`barcode`
    - provider registry health
    - conformance tests
    - live MLKit instrumentation gate
  - 验收：
    - fake provider 验证 request/response shape。
    - live provider 使用 provider-owned image handles。
    - handle validation、unknown/recycled handle denial、timeout、cleanup 通过。
    - MLKit model unavailable 时稳定 degraded/unavailable。
  - Evidence：待补充。

- [ ] P12-29: 实现 `compat.barcode` and `compat.qrcode` facade。
  - 范围：提供 Rhino 风格 `detect`、`detectAll`、`recognizeText`、
    `recognizeTexts` wrapper。
  - 验收：
    - `qrcode` 固定 QR format filter。
    - `recognizeText` 返回 string 或 null；`recognizeTexts` 返回 string array。
    - `detect`/`detectAll` 返回 frozen JSON snapshots。
    - status manifest 中 `barcode` 和 `barcode.qr_code` 从 provider gap 更新为
      `supported_async` 或 `partial`。
  - Evidence：待补充。

- [ ] P12-30: 增加 barcode/qrcode examples and packaged smoke。
  - 范围：补齐迁移示例、fixture、device 或 packaged 验证。
  - 验收：
    - 示例覆盖 image path、image handle、QR-only。
    - packaged 如果模型资源未随包提供，必须稳定拒绝并说明模型策略。
    - image temp handle 在 OCR/barcode 共用路径中正确 recycle。
  - Evidence：待补充。

### Track H: Unsupported utility-heavy modules triage

- [ ] P12-31: 盘点 `unsupported` utility-heavy modules 的 P12 决策。
  - 范围：从 388 个 `unsupported` API 中挑出可通过纯 JS 或现有 provider
    低风险补齐的模块。
  - 候选：
    - `zip`
    - `canvas`
    - `recorder`
    - media/audio helper
    - remaining formatter/converter/text gaps
  - 验收：
    - 每个候选模块得到 `promote`、`defer` 或 `permanent_non_goal` 决策。
    - 不在同一个 task 中实现多个大模块。
    - matrix 和 migration docs 更新。
  - Evidence：待补充。

- [ ] P12-32: 对一个低风险 utility module 做 promotion pilot。
  - 范围：从 P12-31 中选择一个纯 JS 或低风险 provider 模块，完成端到端
    promotion。
  - 验收：
    - runtime facade、types、host tests、status manifest、matrix、docs 全部同步。
    - 缺依赖路径有 stable unsupported error。
    - 不引入新的 Android raw object leakage。
  - Evidence：待补充。

### Track I: Dual-engine and release evidence

- [ ] P12-33: 将高价值 dual-engine cases 提升到 device instrumentation。
  - 范围：从 Phase 11A 的 dual-engine corpus 中选择支持价值最高的 cases，
    用真实或 fake bridge transport 在设备侧执行。
  - 候选：
    - events.keys constants
    - UI compat show/update/close
    - tasks disposable migration
    - barcode/qrcode
    - Rhino shim disabled/default denial
  - 验收：
    - 报告字段包含 `engine`、`compatStatus`、`providerMode`、
      `resourceCleanupStatus`、`skipReason`。
    - `supported_async` 与 `blocked_by_design` 继续分桶。
  - Evidence：待补充。

- [ ] P12-34: 更新正式迁移文档和 Pro 9 对比文档。
  - 范围：同步 Phase 12 结果到脚本开发者可读文档。
  - 必改文档：
    - `docs/nodejs/MIGRATION_FROM_RHINO.md`
    - `docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md`
    - `docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md`
    - `docs/nodejs/SECURITY_MODEL.md`
    - `docs/nodejs/TESTING.md`
    - `docs/nodejs/types/autojs6-node/README.md`
  - 验收：
    - 已 promoted API 有示例。
    - rejected API 有稳定错误码和迁移建议。
    - 不再把已完成 P12 surface 标为 provider gap。
  - Evidence：待补充。

- [ ] P12-35: 发布 Phase 12 summary。
  - 范围：汇总 Phase 12 完成项、rejected decisions、remaining blockers、
    device/packaged evidence、下一阶段建议。
  - 建议产物：
    - `docs/nodejs/reports/phase12/PHASE12_SUMMARY.md`
    - `tools/nodejs/phase12/generate-rhino-compat-phase12-summary.js`
    - Gradle task：`:app:generateNodeRhinoCompatPhase12Summary`
    - `build/reports/nodejs/phase12-summary.json`
    - `build/reports/nodejs/phase12-summary.md`
  - 验收：
    - summary 能枚举 P12-00 到 P12-35 状态。
    - promoted/rejected/deferred surface 有 counts。
    - Phase 13 建议来自实际 evidence，不是泛化愿望。
  - Evidence：待补充。

## 推荐执行顺序

1. P12-00 到 P12-02：建立新阶段基线和 surface manifest。
2. P12-03 到 P12-07：先处理 events.keys 和 input observer，因为它们既影响
   Rhino 迁移体验，也能验证 lifecycle-heavy provider 模式。
3. P12-08 到 P12-10：推进 UI compat；这是已有 Node `ui` bridge 的兼容包装，
   比 overlay 更低风险。
4. P12-11 到 P12-14：对 floaty/overlay 做 provider 或 permanent non-goal
   决策；不要在未决策前写 runtime facade。
5. P12-15 到 P12-19：处理 tasks；先 disposable migration，再决定 daily、
   weekly、intent/broadcast。
6. P12-20 到 P12-22：处理 threads 替代策略；默认不开放 `worker_threads`。
7. P12-23 到 P12-26：实现或继续拒绝 explicit Rhino JSON-only shim。
8. P12-27 到 P12-30：补 barcode/qrcode provider gap。
9. P12-31 到 P12-35：收尾 unsupported triage、device evidence、文档和 summary。

## Phase 12 完成标准

Phase 12 可以判定完成，当且仅当：

- Phase 12 baseline gate 可运行，且 Phase 11A 关键输入仍完整。
- P12 promoted surface manifest 覆盖本文件中的每个 runtime surface。
- `events.keys` 至少有明确的 promoted 或 rejected 结果。
- `input_observer` 至少完成 fake provider POC 和 cleanup/security gate；live
  provider 如不可行，必须有明确 blocker。
- UI compat 至少完成 constrained schema、`compat.ui` 决策和 device/packaged
  evidence 或稳定拒绝。
- floaty/overlay 已选择 provider path 或 rejection path，且对应 gate 完成。
- tasks 至少完成 disposable migration 或明确拒绝，并更新 daily/weekly/intent
  的决策。
- threads 迁移 cookbook 发布，且没有默认声称 Rhino thread parity。
- Rhino JSON-only shim 仍保持默认禁用；如实现 POC，必须 JSON-only、
  timeout-bounded、无 globals、无 raw object。
- barcode/qrcode 已完成 provider promotion 或保留 provider gap 并有明确原因。
- status manifest、compat matrix、types、migration docs 和 Pro 9 对比文档与
  实际 runtime surface 一致。
- Phase 12 summary 发布，并给出下一阶段任务拆分。

## 给后续 Codex 的执行提示

- 开始任一 P12 task 前，先运行或补齐 P12-01 baseline gate。
- 修改 `autojs6:compat` 时，优先在 `app/src/main/cpp/node_bridge_sources.cpp`
  中查找既有 facade 风格，不要另起一套 loader。
- 新增 bridge module 时，必须同步 `NodeBridgeProtocol.kt`、
  `NodeBridgePermissionManifest.kt`、provider registry、types、security docs
  和 conformance tests。
- 任意 lifecycle-heavy provider 都必须先有 fake-provider gate，再考虑 live
  provider。
- 如果某能力无法安全实现，直接走 rejected path，并把负向 gate、错误码和迁移
  建议做完整；这比半开放 runtime surface 更容易维护。
