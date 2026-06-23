# Phase 11A: Rhino Augment Compatibility Tasks

盘点日期：2026-06-22

本文件是 Phase 11 中面向脚本兼容性的任务轨道。它不替代
`docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json` 中的 Phase 11 completion
blockers，而是把
[`INTEGRATION_STATUS_PRO9_COMPARISON.md`](../../INTEGRATION_STATUS_PRO9_COMPARISON.md)
中提出的 Rhino `augment` 迁移缺口拆成可逐个提交、可追踪、可验收的
任务。

## 目标

让脚本开发者能在 Node.js 引擎中逐步使用 AutoJs6/Rhino 时代的主要脚本
能力，尤其是 `org/autojs/autojs/runtime/api/augment` 中的全局 API、内置
模块、自动化 DSL 和常用工具模块。

第一阶段目标不是把 Rhino 对象模型搬进 V8，也不是实现 Pro 9 的完整
`require("rhino").install()` 语义，而是建立一条安全、显式、可测试的
compat 路径：

- Rhino API inventory 可自动生成。
- 每个 Rhino API 有 Node 迁移状态。
- Node bridge、runtime module、types 和 docs 与 inventory 可做 diff。
- 低风险模块先进入 Node facade。
- 自动化核心 API 以 handle/bridge/Promise 模型实现 Rhino 风格 wrapper。
- 缺能力时返回稳定错误码和迁移提示。
- 每个 Task 独立提交，方便后续追踪和回滚。

## 提交规则

每个 Task 单独提交一次。提交应只覆盖该 Task 的代码、测试、文档和报告
产物。

推荐提交信息格式：

```text
nodejs phase11 S11-01: add rhino augment inventory schema
```

每个 Task 完成时需要：

- 将本文件对应 checkbox 改为 `[x]`。
- 在 Task 下方补充 evidence 路径或运行命令结果摘要。
- 如有 Gradle gate，提交中必须包含 gate 任务或 gate 输出更新。
- 如因设备、权限或外部条件无法运行，需要记录明确原因。
- 不把多个 S11 Task 混在一个提交里。

## 边界

### 本轨道包含

- Rhino `augment` API inventory。
- Rhino API 到 Node bridge/runtime module/types/docs 的映射。
- 低风险 utility facade。
- scoped `files` facade。
- `toast`、clipboard、timers、console、storages、sqlite 等常用兼容层。
- selector/accessibility 核心 DSL。
- image/OCR/screenshot 常用 wrapper。
- app/device/shell 常用兼容 API。
- UI、floaty、tasks、events、threads 的设计门禁和后续实现任务。
- 显式 JSON-only Rhino shim 的可行性设计。
- 示例、迁移文档、双引擎兼容测试和 packaged APK smoke。

### 本轨道不包含

- 默认向 Node 全局注入所有 Rhino globals。
- 把 Rhino `Scriptable`、Android `View`、`Context`、
  `AccessibilityNodeInfo` 或 Java raw object 暴露给 Node。
- 支持 `importClass`、`importPackage`、`JavaAdapter` 穿透到 Node。
- 默认实现 Pro 9 的 `$autojs.java` 或 `require("rhino").install()`。
- 放开 native addon、unrestricted `child_process`、raw network、
  raw `node:wasi`、private Node bindings 或 production Inspector。
- 替代 `crash_evidence_automation`、`android_side_package_installation`、
  `packaged_long_running`、`second_execution_slot`、
  `full_heavy_resource_stress` 这些 Phase 11 required internal blockers。

## 状态词汇

Inventory 和兼容矩阵统一使用以下状态：

- `supported`：Node 中已有同步或等价语义。
- `supported_async`：Node 中可用，但必须通过 Promise/async API。
- `renamed`：能力可用，但模块名、方法名或参数形态不同。
- `partial`：部分可用，缺关键参数、权限路径、资源释放或 packaged 覆盖。
- `blocked_by_design`：违反 Safe Node Profile 边界，明确不实现。
- `unsupported`：尚未实现，且暂未判定为永久拒绝。

## 总体 checklist

### Track 0: Phase bootstrap

- [x] S11-00: 提交本 Phase 11A 任务文档。
  - 产物：`docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md`
  - 验收：文档包含范围、边界、任务拆分、提交规则和验收标准。
  - Evidence：本文件创建并作为 Phase 11A bootstrap 任务提交。

### Track A: Inventory and gates

- [x] S11-01: 定义 Rhino augment inventory schema。
  - 范围：定义 module、global assignment、self assignment、alias、
    getter、class assignment、withDollarPrefix、attributes、source location、
    migration status、notes 字段。
  - 产物：schema 文档和测试 fixture。
  - 验收：schema 能覆盖 `Augmentable`、`AugmentableProxy`、`Invokable`、
    `Constructable`、`AsEmitter`、simple getter proxy。
  - Evidence：
    `docs/nodejs/rhino-compat/AUGMENT_INVENTORY_SCHEMA.md`、
    `docs/nodejs/rhino-compat/rhino-augment-inventory.schema.json`、
    `docs/nodejs/rhino-compat/fixtures/rhino-augment-inventory.sample.json`。

- [x] S11-02: 实现 augment source scanner。
  - 范围：扫描 `org/autojs/autojs/runtime/api/augment` 和
    `ScriptRuntime.augment(...)` 的实际注册顺序。
  - 产物：scanner task 或 Kotlin utility。
  - 验收：能输出所有已注册 augment module，包含文件路径和行号。
  - Evidence：
    `tools/nodejs/phase11/scan-rhino-augment-inventory.js`；
    `node --check tools/nodejs/phase11/scan-rhino-augment-inventory.js`；
    `node tools/nodejs/phase11/scan-rhino-augment-inventory.js --output build/reports/nodejs/rhino-augment-source-scan.json --fail-on-unresolved`
    输出 73 个注册模块、21 个 proto reference、0 个未解析 receiver。

- [x] S11-03: 生成 `rhino-augment-api-inventory.json` 和 `.md`。
  - 范围：新增 Gradle 任务，例如 `generateNodeRhinoAugmentInventory`。
  - 产物：`build/reports/nodejs/rhino-augment-api-inventory.json`、
    `build/reports/nodejs/rhino-augment-api-inventory.md`。
  - 验收：报告稳定排序，连续运行无非确定性 diff。
  - Evidence：
    `tools/nodejs/phase11/generate-rhino-augment-inventory.js`；
    `:app:generateNodeRhinoAugmentInventory` 生成
    `rhino-augment-api-inventory.json`、`rhino-augment-api-inventory.md` 和
    `rhino-augment-source-scan.json`；当前输出 73 个注册模块、879 个 API
    entry、0 个未解析 receiver。

- [x] S11-04: 生成 Node bridge/types/docs 差距报告。
  - 范围：对比 `NodeBridgeModules`、
    `docs/nodejs/types/autojs6-node/index.d.ts`、runtime module source 和
    inventory。
  - 产物：`build/reports/nodejs/rhino-node-compat-gap.json`、
    `build/reports/nodejs/rhino-node-compat-gap.md`。
  - 验收：每个 Rhino API 至少落到一个状态词汇。
  - Evidence：
    `tools/nodejs/phase11/generate-rhino-node-compat-gap.js`；
    `:app:generateNodeRhinoNodeCompatGap` 生成 gap JSON/Markdown；当前输出
    73 个 Rhino 模块、879 个 API entry、24 个模块有 bridge evidence、24
    个模块有 type evidence。

- [x] S11-05: 增加 inventory status manifest。
  - 范围：建立人工维护的 status/decision manifest，避免 scanner 只知道
    "存在"，不知道 "迁移策略"。
  - 产物：例如 `docs/nodejs/rhino-compat/status-manifest.json`。
  - 验收：manifest 缺项时 gate 能失败并指出具体 API。
  - Evidence：
    `docs/nodejs/rhino-compat/status-manifest.json` 显式覆盖 73 个模块和
    879 个 API entry；`docs/nodejs/rhino-compat/STATUS_MANIFEST.md` 记录维护
    规则；`tools/nodejs/phase11/rhino-compat-status-manifest.js` 负责校验和
    应用状态。负向校验删除 `global.__module` 后，
    `generate-rhino-augment-inventory.js --require-status-manifest` 失败并输出
    `missing API global.__module`。

- [x] S11-06: 增加 `verifyNodeRhinoCompatInventory` gate。
  - 范围：校验 inventory、status manifest、bridge/types/docs diff。
  - 产物：Gradle verification task。
  - 验收：新增 Rhino augment API 如果没有 Node 迁移状态，gate 失败。
  - Evidence：
    `tools/nodejs/phase11/verify-rhino-compat-inventory.js`；
    `:app:verifyNodeRhinoCompatInventory` 生成
    `rhino-compat-inventory-verification.json` 和 `.md`；当前 gate 通过，73
    个模块、879 个 API entry、0 个 policy violation。

- [x] S11-07: 发布第一版 Rhino-to-Node 兼容矩阵。
  - 范围：把 report 中的当前人工矩阵转换为生成或半生成文档。
  - 产物：`docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md`。
  - 验收：矩阵按模块分组，包含状态、Node 替代模块、风险和下一步。
  - Evidence：
    `tools/nodejs/phase11/generate-rhino-compat-matrix.js`；
    `:app:generateNodeRhinoCompatMatrix` 生成
    `docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md`；当前矩阵覆盖 73 个
    Rhino 模块和 879 个 API entry，并按模块记录 status、Node surface、
    risks 和 next step。

### Track B: Low-risk facade

- [x] S11-08: 确定 compat loader 和模块命名。
  - 范围：决策 `"nodejs compat"`、`require("autojs6:compat")`、
    `require("rhino-compat")` 或其他入口形态。
  - 产物：design note、runtime module stub、错误码策略。
  - 验收：不会默认污染 Node global；必须显式 opt-in。
  - Evidence：
    `docs/nodejs/design/RHINO_COMPAT_LOADER.md` 记录入口决策，选定
    `require("autojs6:compat")`，拒绝默认 Rhino globals 和裸
    `require("rhino-compat")` alias；`app/src/main/cpp/node_bridge_sources.cpp`
    增加 `autojs6:compat` runtime stub；`docs/nodejs/types/autojs6-node/rhino_compat.d.ts`
    增加类型声明；`tools/nodejs/phase11/verify-rhino-compat-loader.js` 和
    `:app:verifyNodeRhinoCompatLoader` 校验 no-default-global、错误码和文档/类型覆盖。

- [x] S11-09: 实现 toast、sleep、clipboard、basic global facade。
  - 范围：`toast()`、`toastLog()`、`sleep()`、`setClip()`、`getClip()` 等
    低风险常用 API。
  - 产物：runtime module、types、host tests。
  - 验收：Node compat 示例可运行；缺 clipboard provider 时有稳定错误码。
  - Evidence：
    `app/src/main/cpp/node_bridge_sources.cpp` 在 `autojs6:compat` 中暴露
    `toast`、`toastLog`、`sleep`、`setClip`、`getClip` Promise facade；
    `install({ globals: true })` 才显式安装同名全局，默认 `require("autojs6:compat")`
    不污染 global；`docs/nodejs/types/autojs6-node/rhino_compat.d.ts` 补齐类型；
    `tools/nodejs/phase11/verify-rhino-compat-basic-facade.js` 和
    `:app:verifyNodeRhinoCompatBasicFacade` 使用 fake toast/clipboard provider 验证
    facade、async sleep 和显式 global install。

- [x] S11-10: 实现 scoped `files` compatibility facade。
  - 范围：覆盖常用 `files.read/write/exists/listDir/join/path/remove/copy/move`
    等，并严格遵守 scoped fs。
  - 产物：facade、types、path traversal tests。
  - 验收：拒绝越界路径；错误消息包含迁移建议。
  - 完成：`require("files")`、`compat.files` 和显式 `files` global install
    已接入；`files.toFile()` 标记为 Safe Node Profile 边界阻断，
    `files.open()` 保持稳定 unsupported。
  - 证据：`tools/nodejs/phase11/verify-rhino-compat-files-facade.js` 和
    `:app:verifyNodeRhinoCompatFilesFacade` 覆盖 scoped read/write/copy/move/remove
    以及 path traversal migration hint。

- [x] S11-11: 实现 pure utility facade。
  - 范围：`base64`、`colors`、`formatter`、`converter`、`mime`、
    `nanoid` 的优先子集。
  - 产物：runtime module 或 JS facade、types、unit tests。
  - 验收：不依赖 Android provider；可在 host gate 中跑。
  - 状态：已接入 `require("base64")`、`require("colors")`、
    `require("formatter")` / `require("fmt")`、`require("converter")` /
    `require("cvt")`、`require("mime")`、`require("nanoid")`，并通过
    `autojs6:compat` 显式安装 `base64`、`colors`、`fmt`、`cvt`、
    `mime`、`nanoid`、`util`。
  - 边界：`colors.toColorStateList()`、`colors.setPaintColor()`、
    `colors.themeColor` 保持 Safe Node Profile 边界阻断；`util.inspect`
    迁移到 Node `require("util").inspect` / `compat.util.inspect`。
  - 证据：`tools/nodejs/phase11/verify-rhino-compat-pure-utilities.js` 和
    `:app:verifyNodeRhinoCompatPureUtilities` 覆盖 host-side base64、colors、
    bytes formatter/converter、mime、nanoid、explicit globals 和阻断策略。

- [x] S11-12: 实现 text utility facade。
  - 范围：`opencc`、`pinyin`、`pinyin4j` 的可用子集和缺依赖降级。
  - 产物：facade、types、fixture tests。
  - 验收：缺少 native/resource 依赖时返回稳定 `unsupported` 错误。
  - 状态：已接入 `require("opencc")`、`require("pinyin")`、
    `require("pinyin4j")`，并通过 `autojs6:compat` 显式安装
    `opencc`、`pinyin`、`pinyin4j`。
  - 边界：`opencc.*`、`pinyin.segment`、`pinyin.fromPhrase()` 缺少完整
    Android/native/resource 依赖时返回稳定
    `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED`。
  - 证据：`tools/nodejs/phase11/verify-rhino-compat-text-utilities.js` 和
    `:app:verifyNodeRhinoCompatTextUtilities` 覆盖 fixture 拼音、pinyin4j
    格式化、OpenCC 降级、explicit globals 和 require 入口。

- [x] S11-13: 实现 storages/sqlite compatibility facade。
  - 范围：把 Rhino `storages`、`sqlite` 常用形态映射到现有
    `storage/storages`、`database/sqlite` bridge。
  - 产物：facade、types、migration examples。
  - 验收：数据隔离、关闭、事务错误路径有测试。
  - 状态：已接入 `autojs6:compat` 显式安装 `storages`、`sqlite`，
    并通过 compat 聚合暴露 `storage`、`storages`、`database`、`sqlite`。
  - 边界：`storages.names()`、`storages.all()` 缺少受控枚举后端时返回稳定
    `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED`；`putSync`、`removeSync`、
    `clearSync` 和 `storages.removeSync()` 在 Node 中为 Promise 型 async
    兼容入口；`sqlite` 仅接受受控 database bridge 的 scoped name，不恢复
    Rhino 任意文件路径和 callback open 形态。
  - 证据：`tools/nodejs/phase11/verify-rhino-compat-storage-sqlite.js` 和
    `:app:verifyNodeRhinoCompatStorageSqlite` 覆盖 storage 数据隔离、sqlite
    callable、database close、transaction 错误路径、explicit globals 和类型
    smoke。

- [x] S11-14: 稳定 console/timers/error compatibility。
  - 范围：确认 `console`、`setTimeout`、`setInterval`、`clearTimeout`、
    `clearInterval`、异常格式和 source map 输出。
  - 产物：compat tests、docs。
  - 验收：常用 Rhino 调试脚本迁移到 Node 后输出可读。
  - 状态：已接入 `autojs6:compat.console`、`autojs6:compat.timers`
    和 `console`/timer 显式 globals；`console.print`、`verbose`、
    `err`、`clearConsole`、`console.show()` 等常见调试脚本路径可读输出，
    悬浮控制台窗口控制为 no-op 兼容层；交互输入和 Auto.js timer 扩展保持
    stable unsupported；新增 `verifyNodeRhinoCompatConsoleTimersError` 覆盖
    console 输出、timer facade、source-map-aware `errorStack` 和类型 smoke。

### Track C: Automation core facade

- [x] S11-15: 设计 selector DSL handle 模型。
  - 范围：定义 `text()`、`desc()`、`id()`、链式过滤、`findOne()`、
    `find()`、`click()` 的异步/同步外观策略。
  - 产物：design doc、types draft、bridge method gap list。
  - 验收：明确哪些 API 是 `await`，哪些仅提供 compatibility wrapper。
  - 状态：已新增 `docs/nodejs/design/RHINO_SELECTOR_DSL_HANDLE.md`、
    `RHINO_SELECTOR_DSL_TYPES_DRAFT.md` 和 `RHINO_SELECTOR_DSL_BRIDGE_GAPS.md`；
    结论为 selector builder/chain 同步生成 frozen JSON descriptor，所有
    accessibility 查询/动作方法返回 Promise；S11-16 MVP 不需要新增 Java
    bridge method，主要实现 Node 侧 Rhino selector handle wrapper；新增
    `verifyNodeRhinoSelectorDslHandleDesign` 锁定设计、类型草案和 bridge gap。

- [x] S11-16: 实现 selector/accessibility 第一版 facade。
  - 范围：覆盖最常见的 `text("...").findOne(timeout)`、
    `text("...").click()`、`back()`、`home()`、`click(x,y)`。
  - 产物：facade、types、instrumentation tests。
  - 验收：无 raw `AccessibilityNodeInfo` 泄漏；handle 超时后释放。
  - 状态：已在 `autojs6:compat` 中新增 `selector` DSL 和直接 builder/action
    别名，`findOne(number)` 归一化为 `{ timeoutMs }`，`find()` 路由到
    `accessibility.findAll()`；handle/descriptor 保持 frozen JSON，不暴露
    raw `AccessibilityNodeInfo`；新增
    `verifyNodeRhinoCompatSelectorAccessibility` host gate 覆盖默认不注入全局、
    显式安装 selector globals、链式不可变和 Promise 动作边界；新增
    `NodeAccessibilitySelectorBridgeInstrumentationTest.jsRhinoCompatSelectorFacadeUsesFakeTransport`
    通过 fake bridge transport 覆盖 compat selector facade 的设备侧嵌入式
    Node 路径。

- [x] S11-17: 扩展 app/device/shell compatibility。
  - 范围：`app.launchPackage`、`app.openAppSetting`、basic intent helpers、
    `device.wakeUp/vibrate/isScreenOn`、`shell()` 常用形态。
  - 产物：facade、types、permission docs。
  - 验收：shell 权限和 root/shizuku 边界清晰，不提升 Safe Node Profile。
  - 状态：已在 `autojs6:compat` 中暴露 `app`、`device` 和 callable
    `shell(command, optionsOrRoot)`；`shell(command, true)` 仅归一化为
    `{ root: true }`，root 仍需显式能力，Shizuku 保持 unsupported；
    `rhino_compat.d.ts`、TypeScript smoke、迁移/安全文档已更新；新增
    `verifyNodeRhinoCompatAppDeviceShell` host gate 覆盖默认不注入全局、
    显式安装 globals、app/device 路由和 shell root 边界。

- [x] S11-18: 实现 images compatibility 第一版。
  - 范围：`images.read/save/clip/resize/grayscale/threshold/findImage/
    matchTemplate/findColor/findMultiColors/recycle`。
  - 产物：facade、types、image handle lifecycle tests。
  - 验收：每个 image handle 在 stop/timeout/destroy 后可追踪释放。
  - 状态：已在 `autojs6:compat` 中暴露 `images` facade，并支持
    Rhino 风格 `read/save`、region/size 参数、`threshold(image,
    threshold, maxValue, type)`、`findImage/findColor` region overload、
    `matchTemplate/findMultiColors/recycle`；所有 bridge 操作保持
    Promise 语义，image handle 继续由受控 provider 持有并要求显式
    `recycle()`；新增 `verifyNodeRhinoCompatImagesFacade` host gate 覆盖
    默认不注入全局、显式安装 `images`、handle 创建/派生/回收路由和
    S11-19 screenshot permission 边界。

- [x] S11-19: 补齐 screenshot permission compatibility。
  - 范围：`requestScreenCapture()`、`captureScreen()` 和 media projection
    provider 的 Rhino 风格 wrapper。
  - 产物：facade、permission flow docs、device smoke。
  - 验收：拒绝权限时错误稳定；packaged 行为有记录。
  - 状态：已新增 `compat.requestScreenCapture()`、
    `requestScreenCaptureAsync()`、`captureScreen()` 以及
    `compat.images.requestScreenCapture/captureScreen/stopScreenCapture`
    wrapper；请求权限走受控 `media_projection` provider，`captureScreen`
    固定使用已有权限路径，不从 Node service 直接弹窗；拒绝权限和 packaged
    fallback 保留稳定 bridge error；新增
    `verifyNodeRhinoCompatScreenshotPermission` host gate 覆盖 boolean/object
    orientation、显式 globals、capture-to-file recycle 和拒绝路径。

- [x] S11-20: 实现 OCR/barcode compatibility strategy。
  - 范围：`ocr.recognizeText()`、`ocr.detectTextBounds()`、`barcode`、
    `qrcode` 的可用子集。
  - 产物：facade、types、fixture tests。
  - 验收：图像输入支持 path/handle，输出为 JSON-safe object。
  - 状态：新增 `compat.ocr` callable facade 和 explicit `ocr` global，
    支持 handle、path、existing-permission capture 输入；`recognizeText()`
    返回 Rhino 风格字符串数组，`detect()` / `detectTextBounds()` 返回
    JSON-safe OCR result snapshots。barcode/qrcode 记录为 S11-20 bridge gap，
    在受控 barcode provider 和 JSON-safe result contract 落地前不暴露为 supported。
    新增 `verifyNodeRhinoCompatOcrBarcodeStrategy` gate 覆盖 path/handle/capture、
    region normalization、临时 image recycle、MLKit-only mode 和 barcode gap。

### Track D: Lifecycle-heavy APIs

- [x] S11-21: 设计 events/keys/input observer compatibility。
  - 范围：事件订阅、按键监听、触摸/key observer 的权限和资源释放。
  - 产物：design gate、capability manifest diff。
  - 验收：没有未释放 listener；后台/packaged 语义有明确边界。
  - 状态：新增 `docs/nodejs/design/RHINO_EVENTS_INPUT_OBSERVER.md`，明确
    `events.__asEmitter__()` / `events.asEmitter()` 因 Rhino `ScriptableObject`、
    Java `EventEmitter`、`Thread` / `MainThreadProxy` 语义标记为
    `blocked_by_design`；`events.keys` 记录为后续可落地的 pure-constant
    subset，但 S11-21 不新增 runtime facade。capability manifest diff 仅作为
    未来 `input_observer`、`input_observer.keys`、`input_observer.touch`、
    `input_observer.intercept` 提案记录，当前 canonical capability list 不变。
    新增 `verifyNodeRhinoCompatEventsInputObserverDesign` gate 校验设计文档、
    status manifest、matrix、迁移/安全/loader 文档、Gradle 注册，以及 S11-21
    没有提前开放 `input_observer` runtime capability。

- [x] S11-22: 设计 tasks 到 WorkManager/用户任务数据库的迁移。
  - 范围：Rhino `tasks` 与现有 `work_manager` 的 API 映射和不可映射项。
  - 产物：design doc、migration table。
  - 验收：不把长期任务误映射为一次性 bridge call。
  - 状态：新增 `docs/nodejs/design/RHINO_TASKS_WORK_MANAGER_MIGRATION.md`，
    将 Rhino `tasks` 拆分为 `work_manager` 可部分迁移项、用户任务数据库/广播
    设计延后项，以及 raw `TimedTask` / `IntentTask` 对象阻断项。当前仅保留
    `require("work_manager")` 作为 Node scheduled-task surface，不新增
    `require("tasks")`、`compat.tasks` 或 `tasks` global；`addDailyTask()` /
    `addWeeklyTask()` 不机械映射为 `schedulePeriodic()`。新增
    `verifyNodeRhinoCompatTasksWorkManagerDesign` gate 校验 design/migration
    table、status manifest、matrix、迁移/安全/loader/WorkManager 文档和未提前
    开放 runtime facade。

- [ ] S11-23: 设计 UI compatibility。
  - 范围：Rhino UI DSL、`ui.run()`、`ui.post()`、layout update、event drain。
  - 产物：design gate、sample migration。
  - 验收：UI thread/Node event loop 关系明确；无跨线程 raw object 泄漏。

- [ ] S11-24: 设计 floaty compatibility。
  - 范围：overlay permission、foreground disclosure、window handle、
    close/cleanup、packaged 行为。
  - 产物：design gate、provider gap list。
  - 验收：没有后台静默 overlay；关闭路径可审计。

- [ ] S11-25: 明确 threads migration policy。
  - 范围：Rhino `threads` 到 Node async/process slot/worker policy 的迁移。
  - 产物：policy doc、examples。
  - 验收：不承诺默认 `worker_threads`；同步共享对象模型标记为
    `blocked_by_design` 或替代方案。

- [ ] S11-26: 设计 explicit Rhino JSON-only shim。
  - 范围：`rhino.run({ source, timeoutMs, args })` 或等价显式 API。
  - 产物：design doc、security boundary、POC gate 计划。
  - 验收：只允许 JSON-safe input/output；不安装 Rhino globals 到 V8。

### Track E: Examples, tests, release evidence

- [ ] S11-27: 建立常用 Rhino 脚本迁移示例集。
  - 范围：toast/files/storage/selector/image/ocr/app/device/shell 的旧写法和
    Node compat 写法。
  - 产物：`docs/nodejs/examples/rhino-compat/` 或同等目录。
  - 验收：示例都能被 example gate 编译或执行。

- [ ] S11-28: 建立双引擎兼容测试集。
  - 范围：同一用例在 Rhino 和 Node compat mode 下验证核心行为。
  - 产物：host tests、device instrumentation tests。
  - 验收：测试报告能区分 `supported_async` 和 `blocked_by_design`。

- [ ] S11-29: 建立 packaged APK compatibility smoke。
  - 范围：packaged 脚本验证 compat loader、bridge permission、资源释放。
  - 产物：Gradle device gate 和报告。
  - 验收：至少覆盖 toast/files/storage/selector/image 基础路径。

- [ ] S11-30: 建立 resource cleanup/security gate。
  - 范围：compat facade 下的 image、selector、event、ui、floaty、db、
    websocket/fetch 资源释放和拒绝路径。
  - 产物：gate、security report。
  - 验收：stop/timeout/destroy 后 active resource 归零或有可解释残留。

- [ ] S11-31: 发布 Phase 11A summary。
  - 范围：汇总 inventory、compat matrix、已支持 API、剩余缺口、blocked
    decisions、推荐迁移路径。
  - 产物：`docs/nodejs/reports/phase11/PHASE11A_SUMMARY.md` 和生成报告。
  - 验收：summary 可直接支撑下一阶段任务拆分。

## 推荐执行顺序

1. S11-00 到 S11-07：先建立 inventory、状态词汇和 gate。
2. S11-08 到 S11-14：完成低风险 facade，快速改善旧脚本迁移体验。
3. S11-15 到 S11-20：推进脚本作者最常用的自动化核心。
4. S11-21 到 S11-26：处理生命周期复杂或安全边界敏感的 API。
5. S11-27 到 S11-31：补齐示例、双引擎测试、packaged smoke 和 summary。

Track B 可以在 S11-06 之后并行推进，但每个模块提交仍应保持单 Task。
Track C 必须等待 selector/image/OCR 的 bridge method gap 明确后再实现。
Track D 不应在没有 design gate 的情况下直接开放 provider。

## Phase 11A 完成标准

Phase 11A 可以判定完成，当且仅当：

- Rhino `augment` inventory 可自动生成。
- Inventory 中每个 API 都有 Node 迁移状态。
- 新增 Rhino API 未标注迁移状态时 gate 会失败。
- Node bridge/runtime module/types/docs 与 inventory 的差距可报告。
- 低风险 facade 至少覆盖 toast、sleep、clipboard、files、base64、colors、
  formatter、converter、mime、nanoid、storages、sqlite。
- 自动化核心至少覆盖 selector/accessibility、app、device、shell、images、
  screenshot、OCR 的基础路径。
- 常用 Rhino 脚本有 Node compat 示例。
- 兼容层不泄漏 raw Java/Rhino/Android object。
- stop/timeout/destroy 后 compat 资源可追踪释放。
- packaged APK smoke 覆盖核心 compat API。
- Phase summary 明确下一阶段仍需处理的 UI、floaty、tasks、events、
  threads、Rhino shim 和 required internal blockers。

## 当前初始判断

当前 AutoJs6 已具备 Node runtime、bridge、types、provider registry 和 Phase
10 门禁基础，因此 Phase 11A 的首要风险不是 Node 能否启动，而是 Rhino
API 表面太大、同步写法多、对象模型与 Safe Node Profile 冲突。先做
inventory 和状态门禁，可以避免在没有完整地图的情况下零散补 facade，
也能让后续每个 Task 的提交边界更清晰。
