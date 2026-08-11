# AutoJs6 Python 引擎交付路线图

最后更新：2026-08-11

本路线图以“尽快形成可用的 Python 引擎”为主线。详细测试过程、历史 APK 哈希和逐次诊断记录保存在证据文档与报告目录中，不再作为日常推进清单重复展开。

运行时协议以 [Python Runtime Plugin Protocol V1](../dev/python-runtime-protocol-v1.md) 为准。

## 交付目标

1. **引擎 MVP**：独立 CPython 插件通过统一宿主引擎路径执行本地 `.py`，支持输出、结构化错误、超时、取消和恢复，且绝不回退到 Rhino。
2. **预览版**：补齐 Python 项目、常用启动入口和最小 AutoJs6 能力桥。
3. **稳定版**：完成安全、兼容、供应链、升级、回滚和发布验证。

## 推进与门禁原则

- 每个阶段只设置一个退出门槛，不为每个测试场景创建独立门禁。
- 当前阶段只验证受本阶段修改影响的层级；纯文档或无关测试变化不自动废弃既有运行时证据。
- JVM/静态、Android 构建、真机 Binder、真实 CPython 和稳定发布证据继续分层描述。
- API/ABI 扩展矩阵、hostile 变体、压力和长时 soak 统一放到稳定版发布前，不阻塞 MVP 主线。
- 诊断证据可以支持工程决策，但不得冒充公开稳定版发布证据。
- R3-P 完成前 Python 功能默认关闭；开启后也只允许显式选择并固定可信插件。
- Gradle 任务仍须独占执行；使用真机时必须指定精确 serial，并在结束后恢复和核验设备状态。

以下问题属于立即阻断项：

- `.py` 可能进入 Rhino、RootAutomator 或其他旧引擎；
- provider 身份、调用 UID、signer 或 session owner 未被校验；
- CPython、插件 native 库或 Python 对象被加载到宿主进程；
- `start()` 可能已调度用户代码后发生自动重放；
- 正常、取消、超时或 Binder death 路径无法释放 session/PFD/binding 或恢复运行时；
- 同一次执行可能产生多个终态。

## 当前检查点

| 阶段 | 状态 | 说明 |
| --- | --- | --- |
| R0 | **已完成** | 版本化协议、传输和资源所有权基线 |
| R1 | **已完成** | 默认关闭的宿主策略、假 provider、AAR consumer 和当前构建基线 |
| R2 | **已满足 R3 前置条件** | 独立 CPython 插件及 API 31/arm64 代表性真机纵向证据；不是稳定版验收 |
| R3-P | **已完成（default-off 开发者预览）** | 单文件 Python 引擎纵向路径已通过代表性真机 smoke；公开入口仍关闭 |
| R4 | **已完成（限定入口范围的开发者预览）** | Python 项目、编辑器及新建快捷方式/定时任务入口一致；content 项目树、Widget 与 Tasker 项目上下文明确延后 |
| R5 | **已完成（default-off 能力预览）** | 不可变只读能力快照和最小 `autojs6` Python API 已通过构建及 API 31/arm64 代表性项目 smoke |
| R6 | **进行中（P1 已完成，P2 待冻结）** | 默认关闭的 RC 准入、合规源码和聚焦编译已完成；下一步冻结可追溯产物 |

## R0：协议与隔离基线（已完成）

目标：冻结安全、可演进的宿主—插件边界，不改变生产脚本路由。

- [x] 建立 `plugin-api:python-runtime-api` 和版本化 AIDL/TaggedWire 协议。
- [x] 固定 engine/provider/action/permission 标识和独立插件进程要求。
- [x] 使用受限 `byte[]` 元数据和声明式 PFD 传输源码及大对象。
- [x] 定义长度、SHA-256、PFD 引用、输出配额和序列约束。
- [x] 定义唯一终态、幂等取消/关闭、竞态所有权和禁止调度后重放。
- [x] 建立 codec、schema、malformed input 和终态竞态的 JVM 基线。
- [x] 保持 CPython、Chaquopy 和 Python native 库不进入宿主进程。

退出条件：协议和静态聚合通过，宿主仍无 Python 生产执行路由。

证据索引：

- `build/reports/python/r0-gate.json`
- `build/reports/python/release-api-distribution-gate.json`
- `plugin-api/python-runtime-api/`

## R1：默认关闭的宿主契约（已完成）

目标：在没有公开 Python 路由的前提下，完成宿主选择、绑定和契约夹具。

- [x] 建立 debug-only 假 provider、AAR-only consumer 和 Android harness。
- [x] 实现组件、UID、完整 signer、版本、更新时间和 capability 固定策略。
- [x] 固定 action discovery 后使用 exact component/action-null 绑定。
- [x] 建立 PFD 所有权、输出、终态、取消、超时、death 和无重放测试基础。
- [x] 编译宿主及 androidTest，并保持生产 manifest 不声明 Python provider。
- [x] 引入 `PythonLaunchBoundaryPolicy`，在引擎关闭时拒绝 Python-looking 启动。
- [x] 当前 JVM/静态、Android source compile 和聚合测试通过。

退出条件：宿主契约聚合通过，`.py` 仍在旧引擎分派前 fail-closed。

证据索引：

- `build/reports/python/r1-conformance-gate.json`
- `test-apps/python-runtime-conformance/`
- `test-apps/python-runtime-api-consumer/`

## R2：独立 CPython 运行时候选（已满足 R3 前置条件）

目标：证明独立插件可以通过冻结协议安全执行真实纯 Python，为宿主引擎集成提供可用依赖。

- [x] 建立独立 sibling repository、独立 APK/UID 和 `:python_runtime` 进程。
- [x] 固定 Chaquopy 17、CPython 3.13.9、stdlib-only 和 64 位 ABI 范围。
- [x] 使用审核并锁定的 release AAR、依赖锁和校验元数据构建插件。
- [x] 实现 exact binding、每入口 caller/session owner 校验、单活动 session 和零排队。
- [x] 实现可靠 PFD snapshot、长度/EOF/SHA-256 校验和有界输出。
- [x] 实现真实 stdout/stderr、返回码、`SystemExit`、异常和结构化 traceback。
- [x] 对取消、超时、阻塞 I/O 和 native 工作使用进程退休及 clean rebind。
- [x] 在 API 31/arm64 代表性设备完成真实 CPython、PFD、取消、超时和恢复纵向验证。
- [x] 完成同签名不同 UID 的 provider 入口拒绝验证。
- [x] 检查 debug APK 权限、signer、ABI、第三方 wheel 缺失和 ELF 16 KiB page 兼容性。
- [ ] 补齐许可证、源码提供义务和 CPython 安全维护策略；此项阻断 R6，不阻断 R3-P。

退出条件：可信插件在代表性设备上可执行、停止、超时并恢复；宿主可以开始默认关闭的 R3-P 集成。

R2 已达到上述退出条件。不同 signer、跨 UID session-handle relay、provider 更新/禁用/卸载竞态、完整设备矩阵、压力和 soak 统一移至 R6。

证据索引：

- [R2 当前树部分证据说明](R2_CURRENT_TREE_PARTIAL_BUNDLE.md)
- `build/reports/python/r2-partial-testsha-5bfe88f9/`
- `build/reports/python/r2-identity-testsha-4d91301f/`
- `D:\idea-projects\AutoJs6-Plugin-Python-Runtime\ROADMAP.md`

## R3-P：单文件 Python 引擎预览（已完成，默认关闭）

目标：在显式开发者开关后，以统一宿主 `ScriptEngine` 路径执行本地普通 `.py`。

本阶段不包含 Python 项目、content URI、在线 pip、workspace、UI 脚本或 AutoJs6 capability API。

### R3-P1：默认关闭的单文件 Python Source 路由骨架（源码已部署）

- [x] 增加编译期开关，默认 `false`，且只有 `DEBUG && flag` 才允许预览路由。
- [x] 让 `ScriptSource` 支持语言自己的真实扩展名。
- [x] 新增 `PythonScriptSource`、`PythonFileSource`，engine ID 固定为 `python`。
- [x] 仅在开关开启时，让 `createFileLaunch` 和 `createPathLaunch` 将本地普通 `.py` 解析为 `PythonFileSource`。
- [x] 开关关闭时继续返回稳定的 default-off 错误。
- [x] `.pyc`、URI、project 和 semantic-origin guard 继续 fail-closed。
- [x] P1 路由保持与执行 client 解耦；实际 engine 注册由 P2 使用同一个双重开关控制。
- [x] 增加聚焦的 source/classifier/route 单元测试。

R3-P1 精简门禁：

- 只要求受影响源码编译、聚焦单元测试和静态检查通过；
- feature-off、受限来源拒绝和无 fallback 必须有确定结果；
- 本切片不要求完整设备矩阵、hostile 变体、soak 或发布 receipt。

R3-P1 退出条件：宿主能在显式 debug 开关下生成正确的 Python source；开关关闭或来源不受支持时仍安全失败且绝不回退。

### R3-P2：宿主 client 与 engine 纵向链路（源码已部署）

- [x] 将 debug conformance transport 提炼为生产可用的 `PythonRuntimeClient`。
- [x] 保留 action discovery、完整身份/capability 复核和 exact component/action-null 绑定。
- [x] 监听 Binder death，并保证 close/unbind 幂等。
- [x] 新增 `PythonPluginScriptEngine`，只发送 TaggedWire、源码字节和 PFD。
- [x] Python engine 的 `put()` 不向插件传播 Java 全局对象。
- [x] 将 stdout/stderr 写入宿主控制台，并把远端失败映射为稳定异常。
- [x] `forceStop()`、`destroy()` 覆盖 cancel/close/unbind/PFD 清理。
- [x] 明确 `start()` 后不自动重试或重放。
- [x] 在 `AbstractAutoJs` 中仅按 `DEBUG && flag` 注册 Python engine。
- [x] 提供插件缺失、禁用、不可信、不兼容、忙、death、超时和执行失败的稳定错误。

R3-P2 退出条件：宿主具备默认关闭、可测试的 Python source/client/engine 纵向链路。

当前验证状态：启用预览开关的 `appDebug` 聚焦测试 9/9 通过，AndroidTest 源码完成编译；有界 RPC 等待、超时终止和调度后不重放边界已经修正并纳入当前构建。

### R3-P3：代表性真机闭环

- [x] 完成 P1/P2 的最小串行构建与聚焦测试复验。
- [x] 在 `QV710AF65F`（API 31、`arm64-v8a`）完成一个 exact-device smoke：1/1，零失败、零跳过。
- [x] 验证 `Scripts.run` 进入真实插件，覆盖输出、类型化异常元数据和 `forceStop()` 后恢复；四次启动均为 Python engine，未发生 JavaScript fallback。

真机 smoke 结束后，临时安装包、冻结用户集合和临时目录均已恢复并核验。该检查是轻量开发者预览证据，不创建正式 R3 receipt 或 gate。

证据索引：

- `tools/python/run-r3-preview-smoke.ps1`

R3-P 总退出条件：

1. 显式开启后，本地 `.py` 通过统一宿主 engine 路径执行真实 CPython。
2. 输出、异常和停止行为对用户可见，停止后下一次执行能够恢复。
3. 默认状态仍关闭，任何 Python 失败都不会进入旧引擎。
4. 当前构建和一个代表性真机纵向检查通过。

若后续需要自动化，可新增 `verifyPythonR3SingleFileEngineMvp` 聚合任务；它不是当前门禁，当前也不存在正式 R3 receipt。

## R4：Python 项目与常用入口（已完成，限定开发者预览范围）

目标：在不扩大运行时权限的前提下覆盖正常 AutoJs6 启动面。

### R4-P1：严格项目准入（已完成）

- [x] 严格识别 `project.json` 的 `type: "python"` 与字符串 `main`，不依赖旧打包配置的完整字段校验。
- [x] 要求 Python 项目入口为项目目录内存在的普通 `.py` 文件，拒绝绝对路径、URI、空段、`.`、`..`、`.pyc` 和 canonical/symlink 逃逸。
- [x] 对“非 Python 项目但 `main` 为 `.py`”返回稳定 `PYTHON_PROJECT_TYPE_REQUIRED`，关闭旧 `createPathLaunch` 的意外放宽。
- [x] 对合法 Python 项目先返回严格 admission；默认关闭时稳定拒绝，开启项目预览后也不经过旧 `createPathLaunch` 或 JavaScript 路由。
- [x] 主源码完成 Java/Kotlin 编译；项目准入聚焦测试通过，覆盖严格 JSON、类型、反斜线、入口和 canonical 边界。Windows 不具备测试所需链接权限时，只跳过对应 symlink 个案。

R4-P1 只负责识别和拒绝边界；项目执行能力由 R4-P2 提供。用户直接执行项目目录中的 `main.py` 仍属于 R3 单文件路径，不自动取得项目 workspace。

### R4-P2：最小可运行项目纵向链路（已完成）

- [x] 支持最小 `project.json`：`type: "python"` 与规范化的项目内 `.py` 入口，包括嵌套入口目录。
- [x] 宿主生成私有、有界的 SOURCE + workspace ZIP 一致快照；拒绝路径穿越、链接/特殊文件、大小写冲突、入口遮蔽和超配额输入。
- [x] 插件校验并解包到每次执行独享的私有目录，以项目根作为 cwd、入口目录作为首个 import 路径；不向宿主写回修改。
- [x] `ProjectLauncher` 和 Explorer 项目工具栏接入 `PythonProjectSource`；Python 项目当前只显示 Run，Build/Edit 保持关闭且无空目录崩溃。
- [x] 对本切片不支持的来源继续返回稳定错误，不做隐式兼容或 JavaScript fallback。
- [x] 增加最小示例项目和简体中文使用说明。
- [x] 当前宿主聚焦测试 22 个用例中 21 个通过、1 个 Windows symlink 条件跳过；插件 JVM 测试 28/28 通过，宿主 AndroidTest 与双方 debug APK 构建成功。
- [x] 在 `QV710AF65F`（API 31、`arm64-v8a`）完成 exact-device 项目 smoke：1/1，零失败、零跳过；验证 sibling import、项目 cwd、相对数据读取、Python engine 路由和零 JavaScript fallback，设备状态已恢复。

R4-P2 退出条件已满足：单文件能力保持不变，一个最小目录项目可从 `ProjectLauncher`/Explorer Run 进入真实插件执行，且整个纵向链路无旧引擎 fallback。

证据索引：

- [Python 项目预览说明](PROJECT_PREVIEW.md)
- `sample/python/minimal-project/`
- `tools/python/run-r4-project-smoke.ps1`

本切片不新增正式 receipt 或独立聚合门禁；聚焦构建加一个代表性 smoke 已足以推进下一步。

### R4-P3：常用入口一致性（已完成）

- [x] 编辑器执行本地 `.py` 时冻结语义来源；执行项目声明的入口时，使用最新编辑内容作为 SOURCE，并保留项目根、workspace 与项目 cwd。
- [x] 明确 Intent 边界：本地路径与 `file:` URI 按单文件语义运行；Import Intent 可导入单个 `.py`；Python `content:` 直接执行、项目树/`project.json` 导入及 `.pyc` 均稳定拒绝。
- [x] 新建快捷方式、定时任务和广播任务时保存版本化 `source_kind` 与 `project_root`，再次启动时恢复项目上下文。
- [x] 对缺失、未知、部分或已失效的项目上下文 fail-closed；无上下文的旧记录保持原有单文件语义，不自动升级。
- [x] 共用同一项目准入、私有快照和 no-fallback 规则；清单在生成执行快照前后重新核验。
- [x] 当前树 6 组聚焦单元测试共 27 项：26 通过、0 失败、1 个 Windows symlink 条件跳过；受影响的主源码与 AndroidTest 源码编译通过。

退出条件已满足：本地编辑器以及新建的快捷方式、定时/广播任务能够区分 Python 单文件与项目入口；Intent 的单文件、导入和拒绝边界明确；无效上下文不会进入 JavaScript。

Widget、Tasker 的项目上下文、content 项目树及公开 Python MIME chooser 注册按需后续接入，不阻断 R4。本切片只保留一次聚焦单元回归和 Android 源码编译，不重跑 R2 的 Binder/CPython/设备矩阵，也不新增 receipt 或聚合门禁。

## R5：最小能力 API 与用户预览（已完成）

目标：提供小而可审计的 `autojs6` Python API，不削弱独立进程隔离，也不让实时双向 broker 成为预览版前置条件。

### R5-P1：执行启动时的不可变只读能力（当前切片）

- [x] 在 wire/API 解码层向后兼容地扩展为 1.1；AIDL 方法保持不变，只新增可选的 `HOST_CAPABILITY_SNAPSHOT` PFD。当前 R5 预览执行仍明确要求该能力，不会降级到旧 provider。
- [x] 宿主为每次 request 冻结 execution/app/device 文档，绑定 request ID、entry point、grant、精确长度和 SHA-256。
- [x] snapshot 不含 serial、Android ID、IMEI、fingerprint、`Context`、Binder 或任意 Java 活对象。
- [x] 插件在启动 CPython 前完成长度、EOF、摘要、严格 JSON schema 和 execution/project 关联校验。
- [x] 新增 Python `autojs6.app/device/execution/project` 包；返回快照副本，项目文件只读访问当前插件私有 workspace。
- [x] 项目读取只接受安全相对路径，单次最多 1 MiB，文本只支持严格 UTF-8；单文件执行不获得 project grant。
- [x] 没有能力 snapshot 时返回稳定 `CapabilityUnavailableError`，绝不从插件 Android Context 或 Java bridge 隐式补数据。
- [x] 复用现有 `DEBUG && autojs.python.preview.enabled` 默认关闭开关，不再增加第二个容易漂移的用户开关。
- [x] 更新最小项目示例和简体中文能力说明。
- [x] 完成宿主/API 与插件的串行 Gradle 编译和聚焦单测，并由 current-tree 1.1 分发清单锁定插件使用的 release AAR。
- [x] 用已有 R4 项目 smoke 顺带验证 R5 API，并确认设备恢复。

当前轻量证据：协议/API 与宿主聚焦 JVM 测试共 `60/60` 通过，宿主 Debug 与 AndroidTest Kotlin 编译通过；插件 JVM 测试 `32/32`、纯 Python 测试 `38/38` 通过。`QV710AF65F`（API 31、arm64-v8a、用户 `[0,10]`）上的 exact project smoke 为 `1/1`、零失败/错误/跳过，验证 `autojs6.app/device/execution/project`、workspace import/相对读取和零 JavaScript fallback；runner 与独立复核均确认三包恢复为全用户/global absent。该证据仅关闭 default-off R5 预览切片，不生成正式发布 receipt。

本次设备绑定产物：host `3c1afb6226f2ffcf312542ff5d4f0fcfe10afb004fe9b69d356c9c9c44d3276f`，plugin `5e8c1f8f855f004922d05c67114b2956613a1fb6a48a2b3efedea15e8cc0b63d`，androidTest `f0a7f2f99fd6fff20dbf8b3b586cc5a8babf6cb25167c6ada522cc3fa90ccc1c`，共同 signer `2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8`。

协议说明见 [R5-P1 能力快照协议](R5_P1_CAPABILITY_SNAPSHOT_PROTOCOL.md)，用户 API 见 [`autojs6` 最小能力预览](R5_CAPABILITY_PREVIEW.md)。

### R5-P2：按真实需求增加实时能力

只有出现必须访问宿主特权或实时状态的明确用例时，才引入 execution-scoped 双向 capability broker。届时每次调用必须绑定 execution ID、provider UID、固定 grant、timeout、quota 和唯一终态。

网络、图片、数据库、辅助功能、shell、UI 和 scheduler 均保持未开放；它们不阻断当前只读预览，也不会被一次性批量接入。

R5 退出条件已满足：预览用户显式开启 Python 后，可通过最小项目使用只读 `autojs6` API；聚焦构建和一个代表性真机项目 smoke 均已通过。当前不新增聚合 gate 或正式 receipt。

## R6：稳定发布闭环（进行中）

R6 按可独立交付的三个小阶段推进。前一阶段完成即可进入下一阶段，不等待完整 API × ABI 矩阵、重复 hostile 变体或固定时长 soak；这些只在发现具体风险时追加，不作为日常推进门禁。

### R6-P1：默认关闭的 RC 准入与合规源码（已完成）

- [x] 增加唯一且默认值为 `false` 的 Release Candidate 构建属性；Debug 预览仍由既有开关控制，普通 Release 不会因构建变体而自动启用 Python。
- [x] 用同一份纯策略统一 source 准入、engine 注册、执行保护和 runtime client，避免只打开部分链路；开关关闭时继续 fail-closed 且绝不回退到 JavaScript。
- [x] 为 Debug/Release 两个通道的默认关闭、显式启用和交叉开关隔离增加聚焦单元测试源码。
- [x] 串行运行聚焦单元测试并完成受影响的 Debug/Release 源码编译；本项不要求安装 APK 或运行完整回归。
- [x] 补齐插件随发行所需的许可证/NOTICE、源码来源与提供说明、依赖锁说明及 CPython 安全维护策略源码；NOTICE 与 Chaquopy/CPython 完整上游许可副本均作为 APK asset 落盘。

插件合规源码当前位于独立 sibling 仓库；该仓库仍为 unborn、全树尚未提交，因此“进入可追溯版本控制”仍由 P2 的首个逻辑提交负责，不把未提交文件误当发布来源证明。

P1 聚焦证据：`PythonRuntimeChannelPolicyTest` 为 `4/4`、零失败/错误/跳过；`compileAppDebugKotlin` 与 `compileAppReleaseKotlin` 在普通构建下成功，生成的两个 BuildConfig 开关均为 `false`。Gradle daemon 日志记录 `BUILD SUCCESSFUL in 15m 47s`；Kotlin daemon 连接失败后使用进程内 fallback 完成，不属于源码失败。插件静态门保持 PASS，NOTICE 根文件与 APK asset 副本逐字节一致，Chaquopy 17.0.0 与 CPython 3.13.9 上游完整许可文本已作为独立 assets 落盘。P1 不生成 receipt，也不提升为已签名 Release 产物证据。

P1 退出条件：普通构建保持默认关闭；只有显式 RC 构建能完整打开一致的 Python 路径；合规材料已落盘并准备进入受版本控制的候选源码。此阶段不要求设备矩阵、soak 或正式 receipt。

### R6-P2：可追溯 Release Candidate 产物

- [ ] 冻结宿主与插件的 commit、版本、包名、协议版本和兼容范围，确认候选源码树可追溯。
- [ ] 配置并核验候选签名身份，以锁定依赖和 release AAR 构建宿主与插件 release APK。
- [ ] 生成单份 provenance 记录，包含 commit、版本、signer、APK/AAR 哈希、ABI、依赖锁和构建输入；完成最小 release 打包静态检查。

P2 退出条件：得到可重复识别、可安装且来源可追溯的一组宿主/插件 Release Candidate 产物。P2 只冻结候选产物，不提前生成稳定版 receipt。

### R6-P3：集中发布验证与唯一 receipt

- [ ] 对 P2 的精确产物运行一次集中 hostile/lifecycle 验证，覆盖异签调用者、跨 UID session relay、provider 更新/禁用/卸载、Binder death，以及取消/超时后的资源释放与 clean rebind。
- [ ] 在一个代表性真实设备链路上完成一次从既有版本升级到候选版本、禁用/崩溃恢复及回滚的连续流程，并核验结束后的设备恢复。
- [ ] 对声明发行的 ABI 做打包核验并记录实际设备覆盖；额外 API/ABI 设备、压力或限时 soak 仅由已识别风险触发，不建立穷举矩阵门禁。
- [ ] 汇总前两阶段的冻结输入和本阶段结果，生成唯一最终发布 receipt；失败后必须重新生成候选产物与 receipt，不沿用旧结论。

R6 总退出条件：精确 Release Candidate 在声明范围内通过上述集中安全、生命周期、升级与回滚流程，且唯一最终 receipt 能回指全部冻结产物和证据。

最终仅保留一个聚合任务：`verifyPythonFinalIntegrationGate`；它在 P3 才执行，不阻塞 P1/P2 日常推进。

## 全局不变量

1. JavaScript/Rhino 行为保持不变。
2. `.py` 永不回退到 Rhino 或 RootAutomator。
3. CPython 和 native wheel 始终位于独立插件 APK/进程。
4. Android UID、signer 和白名单 Binder capability 是权限边界；Python 本身不是沙箱。
5. 用户代码可能已经调度后不得自动重放。
6. session、PFD、callback、binding 和 runtime generation 在所有终态都必须释放或退休。
7. 只有当前阶段退出条件阻断主线；发布级矩阵不得反向阻塞 MVP 开发。
