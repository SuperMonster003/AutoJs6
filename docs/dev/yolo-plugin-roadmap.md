# AutoJs6 YOLO 独立插件部署 Roadmap

本文是 `work-yolo` 的可执行状态表。它以 2026-08-11 的本地代码快照为起点，
并更新至 2026-08-13 的 R5 证据；不以公开仓库中较旧的 Paddle OCR 结构为准。
2026-09-18 追加两项产品决定：插件中心对宿主同证书 (OFFICIAL) provider 完成全新安装后
自动写入显式启用记录（运行时闸门不变），以及 Provider 仓库已公开、宿主正式版不再是
Provider 公开发布的硬性前置条件。

## 当前结论

| 项目 | 本地事实 |
| --- | --- |
| R0 基线 | `work-yolo@10c6feb90`，R0 开始前工作树干净 |
| R0 本地提交 | `3c170896d8e4b528f1aa85953a4698d1742c8344`，未 push |
| 通用 Host | 已有 `AidlPluginHost`，包含发现、授权、显式组件绑定、连接池和 dedicated binding lease |
| OCR Host | 当前是统一的 `OcrPluginHost`；raw SharedMemory/pipe 仍是其私有实现 |
| YOLO Runtime | R1 sibling 已有 CPU/arm64 NCNN/JNI provider；主仓库已有 R2 default-off Host/Binder/PFD 路由、R3 Rhino Preview API 和 R4 manifest 驱动的有限模型导入 |
| R0 当前声明 | `SOURCE_DEPLOYED / STATIC_PASS / JVM_DIAGNOSTIC_PASS / AIDL_DIAGNOSTIC_PASS / ANDROID_BUILD_PASS`；不能证明 Binder/native/设备可运行 |
| R2 当前声明 | `SOURCE / ANDROID_BUILD / BINDER_PFD = PASS`；真实 NCNN 推理也经该链路复验，但不等于 Rhino `DEVICE_E2E` 或生产发布 |
| R3 当前声明 | `SOURCE / ANDROID_BUILD / DEVICE_E2E / PUBLIC_PREVIEW = PASS`；仍明确不声明 `PRODUCTION` |
| R4 当前声明 | `SOURCE / ANDROID_BUILD / BINDER_PFD / NATIVE_RUNTIME / DEVICE_E2E / PUBLIC_MODEL_IMPORT = PASS`；仅覆盖同一 decoder 的 YOLO11 detect 有限兼容集，不声明 `PRODUCTION` |
| R5 当前声明 | `SOURCE / ANDROID_BUILD / HOSTILE_BINDER / DESCRIPTOR_INTEGRITY / RESOURCE_200_FRAME / APK_PACKAGING / NATIVE_LOAD_ARM64_DEVICE = PASS`；`ELF_16K_COMPATIBLE = PASS_STATIC_ONLY`，性能只记录不设阈值；RC 是 `TEST_SIGNED_NONDEBUGGABLE`，不声明 `PRODUCTION` |
| 生产入口 | 插件中心可发现；无启用记录时，宿主同证书 (OFFICIAL) provider 视为启用（2026-10-16 起），其余签名默认关闭并需手动启用；2026-09-18 起插件中心对 OFFICIAL provider 的全新安装还会写入启用记录并完成启用探测；Rhino 已提供显式 component 的 opt-in Preview，无静默 provider 选择或回退 |

## 固定架构决定

1. NCNN、JNI、模型和 decoder 位于独立 `AutoJs6-Plugin-Yolo-NCNN`
   APK；AutoJs6 主进程不加载插件 native 库。
2. 使用 `IYoloProvider -> IYoloSession` Binder session，不把 native
   pointer 或可跨进程复用的裸 `long sessionId` 暴露给 Host/JS。
3. 协议元数据和结果使用有界 TaggedWire `byte[]`，大模型与图像只通过
   `ParcelFileDescriptor` 传输。
4. 一条 session 同时最多一条 detect，零排队；每个已接受 request 恰有一个
   detected/failed/cancelled 终态。cancel/close 幂等。
5. `openSession` 和 `detect` 一旦 dispatch 就不自动重试。Binder death 使当前
   session 失效，由调用者明确重新加载模型。
6. 安装 provider 不等于启用。Host 运行时必须保持 default-off（无启用记录即拒绝），
   并固定一个已授权的 exact component；不静默选择、不跨 provider 回退。
   2026-09-18 产品决定：插件中心在前台观察到宿主同证书 (OFFICIAL) provider 完成
   全新（非替换）安装时，可代替用户写入显式启用记录并完成启用探测；TRUSTED /
   USER_GRANTED 签名、adb 侧装与覆盖安装不自动启用。该规则对同一策略对象中的
   MCP Server 同样生效，宿主实现见 `PluginDefaultEnabledPolicy.shouldAutoEnableAfterInstall`。
   2026-10-16 维护者决定：宿主同证书 (OFFICIAL) provider 没有启用记录时也视为启用（与 MCP Server、3-Stove Agent 相同，任何安装途径都生效）；TRUSTED / USER_GRANTED 签名仍需显式启用，运行时选择仍固定已授权的 exact component，
   不静默选择、不跨 provider 回退。宿主实现见 `PluginDefaultEnabledPolicy.isEnabledByDefault` 与
   `AidlPluginHost` 的 `defaultEnabledFor`。
7. R0/R1 不重构 OCR。首次 YOLO 竖切稳定后，再决定是否把图像传输提取为公共组件。

协议细节见 [YOLO Plugin Protocol V1](yolo-plugin-protocol-v1.md)。

## 证据等级

| 标签 | 可以证明 | 不能证明 |
| --- | --- | --- |
| `SOURCE` | 文件、接口和策略源码存在 | 编译或行为正确 |
| `STATIC` | 选定的文件/文本 marker 与 app 直接依赖缺失 | 语义正确、间接依赖、完整生产路由或 native 状态 |
| `JVM` | codec、validation、lifecycle 的 Android-free 行为 | Binder、PFD、Android 生命周期 |
| `ANDROID_BUILD` | AIDL/Kotlin/C++ 编译以及 APK 打包 | 跨进程或真实推理 |
| `BINDER_PFD` | 指定 Android 环境中的 Binder、UID、FD 所有权 | NCNN 输出正确 |
| `NATIVE_RUNTIME` | 指定 ABI 上 `.so`、模型加载和真实 inference | Host/Rhino 端到端 |
| `DEVICE_E2E` | 指定设备、APK、模型和脚本的完整链路 | 未列出的设备/模型适用性 |
| `PRODUCTION` | 签名、hash、索引、发布 receipt，以及产品决定要求的生命周期证据 | 未明确覆盖的 API/ABI/backend |

低等级证据不得升级为高等级声明。设备证据缺失不会阻止下一阶段源码并行推进，
但会阻止对应的 runtime/release 声明。

## R0：中立协议与部署骨架

目标：固定不会因 NCNN 实现细节而重做的最小跨进程边界；不接入任何生产路由。

- [x] 核对本地插件、OCR、通用 AIDL Host、Rhino 注册和工作树边界。
- [x] 注册 `plugin-api:yolo-api`，不把它加入 `app` 依赖。
- [x] 定义 `IYoloProvider`、`IYoloSession`、`IYoloCallback`。
- [x] 定义 provider info/capabilities、open-session、detect、result、error、cancel wire schema。
- [x] 模型 artifact 声明 role、逻辑名、长度、SHA-256 和 FD index。
- [x] 固定 detect-only、single-in-flight、zero-queue、唯一终态和幂等 close 规则。
- [x] 用正数递增 sequence 隔离 late/replayed terminal，并让 400 个最大 label 的
  detection 结果仍落在 256 KiB metadata envelope 内。
- [x] 提供聚焦的 codec/validation/lifecycle JVM 测试源码。
- [x] 提供只读、无子进程的 R0 静态检查脚本。
- [x] 保持 Host/Rhino/NCNN/JNI/conformance app/生产调用点不存在。
- [x] 获取 `ANDROID_BUILD`：编译 `yolo-api` 的 AIDL、Kotlin 和测试。

R0 主 gate（无 Gradle、ADB、网络或设备访问）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\yolo\verify-yolo-r0-static.ps1
```

允许构建窗口打开后的补充证据；它不是继续 R1 源码工作的前置条件：

```powershell
.\gradlew.bat --console=plain `
  :plugin-api:yolo-api:testDebugUnitTest `
  :plugin-api:yolo-api:assembleDebug
```

R0 source exit：静态输出为 `YOLO_R0_STATIC=PASS` 和 `STATIC_MARKERS=PASS`，同时明确
`JVM_TESTS=NOT_RUN_BY_STATIC_SCRIPT`、`ANDROID_BUILD=NOT_RUN_BY_STATIC_SCRIPT`、
`BINDER_PFD=NOT_RUN_BY_STATIC_SCRIPT`、`NATIVE_RUNTIME=NOT_EVALUATED_BY_R0_GATE`、
`DEVICE_E2E=NOT_RUN_BY_STATIC_SCRIPT`、`APP_DIRECT_DEPENDENCY=NOT_ADDED`。这表示选定的
源码 marker 通过，不表示 runtime ready，也不替代完整仓库审计。

2026-08-11 当前证据（JVM/AIDL diagnostic 未写入 canonical receipt）：

- `STATIC`: `YOLO_R0_STATIC=PASS CHECKS=45 SOURCE_ONLY=YES STATIC_MARKERS=PASS`；
  `APP_DIRECT_DEPENDENCY=NOT_ADDED`，native 状态不由该 gate 评估。
- `JVM_DIAGNOSTIC`: R0 commit 时的 protocol-wire + YOLO 主源码由本机
  `kotlinc-jvm 1.7.10` 编译通过，并在 JDK 21.0.1 上运行；当时 3 个测试类共
  `14/14` 通过。之后新增的 SDK-safe open failure codec 不沿用这条旧 diagnostic。
- `AIDL_DIAGNOSTIC`: Android SDK build-tools 36.1.0 的 `aidl.exe` 使用 API 36
  `framework.aidl` 编译 3 个 YOLO AIDL 文件通过。
- `ANDROID_BUILD`: Gradle 9.6.1、AGP 9.3.0、Kotlin 2.3.21、JDK 21 执行
  `testDebugUnitTest + assembleDebug` 为 `BUILD SUCCESSFUL`；当前 17 tests、0 failure，
  `yolo-api-debug.aar` SHA-256 为
  `ce9763faa62977ef90af65ca42cf96670a8d59e68a057e309b92e9615ba06a37`；对应
  `src/main` snapshot 为
  `4cd47305c70b5c1533fbb16efe85bd9c572e571d05b841770976ee3593dd9174`。
- `BINDER_PFD / NATIVE_RUNTIME / DEVICE_E2E / PRODUCTION`: `NOT_RUN` 或 `NOT_PRESENT`。

ad-hoc JVM/AIDL 诊断只说明相应源码可被所列工具接受和聚焦逻辑行为；Gradle 结果只把
R0 提升到 `ANDROID_BUILD`，不覆盖 sibling APK、Binder/PFD、NCNN 或设备推理。

## R1：独立 NCNN Provider 的最短真实推理竖切

目标：先消除项目最大的未知量——Android/NCNN/model/decode 是否真实工作。

- [x] 创建独立 sibling `AutoJs6-Plugin-Yolo-NCNN`，生产实现不放进 `test-apps`。
- [x] 在 APK build 前交付 `common-plugin-api.aar`、`protocol-wire-api.aar`、
  `yolo-api.aar`，记录三者来源 commit 与 SHA-256；这不阻塞 sibling 源码搭建。
- [x] 固定 NCNN 版本、来源、hash、许可证、NDK、STL 和 ABI。
- [x] 首版只支持 CPU、`arm64-v8a`、detect 和一个已知模型 fixture。
- [x] 固定 manifest v1 的 NCNN graph/weights、input/output node 和一个 decoder。
- [x] 实现 `:provider` Service、session Binder、单 worker/零队列和 native handle 回收。
- [x] Service 使用 `org.autojs.permission.PLUGIN`；actionless exact-component bind 也返回 Binder。
- [x] 提供现有插件中心可识别的 `org.autojs.plugin.INFO` metadata service。
- [x] session 对 Host-owned callback Binder 执行 `linkToDeath`；Host death、open 失败和
  显式 close 走同一 native-handle/descriptor 回收路径。
- [x] 模型 artifact 严格验证 length/SHA-256/EOF 后才进入 NCNN。
- [x] 完成 RGBA_8888 -> letterbox -> inference -> decode/NMS -> 原图坐标。
- [x] 生成 provider debug APK；Vulkan、32 位和多 decoder 不作为 gate。

主 gate：一个真实 `.so`、真实模型和固定图片在明确的非受保护 Android 环境上
得到合法 Detection。证据拆成：

- `R1-BUILD`: AIDL/CMake/JNI link 和 APK 完成。
- `R1-NATIVE`: 指定设备或模拟器上完成真实模型推理。

2026-08-11 R1 exit：

- `R1-SOURCE=PASS`：sibling、协议 AAR、NCNN 20260526、严格 manifest 和本地
  YOLO11n fixture 均已有 hash/许可/身份记录；sibling 初始提交与提交前确定性 source
  snapshot 均写入 receipt，模型与测试图不进入 Git/Provider APK。
- `R1-BUILD=PASS`：Gradle/AIDL/Kotlin/CMake/JNI link 与 arm64 debug APK 通过。
- `R1-NATIVE=PASS`：授权设备 `968e9f18`（Xiaomi 23046RP50C、API 35、arm64）运行
  一条真实 fixture smoke，得到 1 bus + 4 person；临时安装随后已卸载。
- canonical receipt：`docs/dev/yolo-evidence/r1-summary.json`。

边界保持不变：R1 smoke 直接覆盖 materialization/native engine，Host -> Provider 的
Binder/PFD 竖切仍属于 R2；native model load 只有 deadline 前后检查，不声明可协作中止的
硬超时证据。

## 单一验收入口与 receipt

每阶段只维护一个主 gate，不把所有诊断组合成阻塞矩阵。脚本在进入对应阶段时创建；
canonical receipt 必须包含命令、commit/source snapshot、输入 artifact hash、环境和各证据层
状态，并写入下表所列的受版本控制路径；临时 `build/reports` 只算 generated output。

| 阶段 | 计划主命令 | canonical receipt |
| --- | --- | --- |
| R1 | `tools/yolo/verify-yolo-r1.ps1` | `docs/dev/yolo-evidence/r1-summary.json` |
| R2 | `tools/yolo/verify-yolo-r2.ps1` | `docs/dev/yolo-evidence/r2-summary.json` |
| R3 | `tools/yolo/verify-yolo-r3.ps1` | `docs/dev/yolo-evidence/r3-summary.json` |
| R4 | `tools/yolo/verify-yolo-r4.ps1` | `docs/dev/yolo-evidence/r4-summary.json` |
| R5 | `tools/yolo/verify-yolo-r5.ps1` | `docs/dev/yolo-evidence/r5-summary.json` |
| R6 | `tools/yolo/verify-yolo-r6.ps1` | `docs/dev/yolo-evidence/r6-summary.json` |

## R2：默认关闭的 AutoJs6 Host/Binder/PFD 接入

目标：连接真实 provider，但仍不暴露脚本 API。

- [x] 新增 `core/plugin/yolo/YoloPluginHost.kt`，组合现有 `AidlPluginHost`。
- [x] 增加最窄的 package-visibility query/插件中心发现映射，但不自动启用 provider
  （2026-09-18 起按固定架构决定 6 放宽：仅 OFFICIAL 签名的全新安装自动启用）。
- [x] 为通用选择策略增加兼容的 `defaultEnabled` 参数，YOLO 固定传 `false`。
- [x] 使用 exact component、授权、签名/UID/version pinning 和 dedicated binding lease。
- [x] `openSession`、`detect` 均显式 `retryOnRemoteException=false`。
- [x] Host 以只读 PFD 打开模型；provider 不申请共享存储访问权限。
- [x] 先实现窄范围 `YoloImageTransport`，不把 OCR 重构设为本阶段门禁。
- [x] session 同时持有 remote Binder 与 binding lease，close/death 时幂等释放。
- [x] 保持无 Rhino/Node/Explorer 生产调用点。

主 gate：Host 与 provider Android build 通过；一次真实 Binder/PFD smoke 作为独立
`BINDER_PFD` 证据。没有设备时只允许声明 `ANDROID_BUILD`。

2026-08-11 R2 exit：

- `R2-SOURCE=PASS`：main revision
  `10e7c85ad285de1ca14db8b18457f3f4992e3ed3` 与 provider revision
  `01b9093c55c7c1a78f39246c671e928df244483f` 均在 gate 前 clean；无 push。
- `R2-ANDROID_BUILD=PASS`：Provider arm64 debug APK、Host arm64 debug APK 与 Host
  instrumentation APK 串行 assemble；三者均为单 signer 且证书 SHA-256 一致，三个
  APK 都不包含本地模型/测试图。
- `R2-BINDER_PFD=PASS`：授权设备 `QV710AF65F`（API 31、arm64）在确认无 soak
  且三个目标包预先不存在后运行唯一 opt-in test；Host 与
  Provider UID 分离，provider/session 均为 remote Binder，模型 PFD 在同步 open 后关闭，
  图像 PFD 在 oneway dispatch 后关闭，callback UID/correlation 与唯一终态通过。
- 真实 YOLO11n 检测得到 1 bus + 4 person，五项与锁定 NCNN fixture 的 IoU 均大于
  `0.9999994`；重复 close 和重新 open session 均通过。
- 运行后 Host/Provider/Test 三包均 absent，唯一 staging 目录已删除；未启动 AVD。
- canonical receipt：`docs/dev/yolo-evidence/r2-summary.json`。

边界保持不变：R2 没有 Rhino/Node/Explorer 调用点，因此不声明 `DEVICE_E2E`、
`PUBLIC_PREVIEW` 或 `PRODUCTION`；hostile provider、timeout/cancel/death 与持续资源增长
检查仍按计划留在 R5。

## R3：Rhino opt-in Preview 端到端

目标：提供第一个可用但仍显式启用的脚本闭环。

- [x] 通过 `Yolo(this).augment(target)` 注册 `yolo`，不只增加一个字段。
- [x] 实现 `yolo.load()`、`detector.detect()`、`detector.close()`。
- [x] detector 实现 `Closeable` 并注册到 `ScriptRuntime.closeableManager`。
- [x] 只接受已授权的显式 exact component，不按 engine/package 静默选择或回退。
- [x] 结果 `RectF` bounds 使用原图坐标，可直接读取中心点服务 click/swipe。
- [x] 增加 `sample/yolo` 最小图片示例和稳定的 `YOLO_*` 脚本错误码。

主 gate：记录一个命名 Host APK、Provider APK、模型 SHA-256、设备 API/ABI、脚本和
结果的 `JS -> Host -> Binder/PFD -> JNI/NCNN -> Detection[]` receipt。

R3 exit 只能声明 `PUBLIC_PREVIEW` 或 `PARTIAL_DEVICE`，不能声明生产完毕。

2026-08-11 R3 exit：

- `R3-SOURCE=PASS`：main revision
  `e6833d5b7b5b84d151067371189abb2fa0ce89fe` 与 provider revision
  `01b9093c55c7c1a78f39246c671e928df244483f` 均在 gate 前 clean；无 push。
- `R3-ANDROID_BUILD=PASS`：Provider arm64 debug APK、Host arm64 debug APK 与 Host
  instrumentation APK 串行 assemble；三者单 signer 且证书 SHA-256 一致。模型和图片
  仅由 instrumentation 外部暂存，Host/Provider APK 均不包含模型。
- `R3-DEVICE_E2E=PASS`：授权设备 `QV710AF65F`（API 31、arm64）在确认无 soak、无竞争
  gate 且三个目标包预先不存在后，仅运行一个 opt-in instrumentation method。
- 设备实际执行
  `ScriptEngineService -> LoopBasedJavaScriptEngine (Rhino) -> yolo.load/detect/close ->`
  `exact Host -> remote Binder/PFD -> provider JNI/NCNN`；没有直接调用测试专用 Host API。
- 真实 YOLO11n 检测得到 1 bus + 4 person，五项与锁定 fixture 的 IoU 均大于
  `0.9999994`；原图 `RectF` 中心点和重复 close 通过。
- 运行后 Host/Provider/Test 三包和唯一 staging 目录均 absent；未启动 AVD。
- canonical receipt：`docs/dev/yolo-evidence/r3-summary.json`；generated receipt SHA-256
  `0f254c6cebf031dc0197263dd2a8ca957add85370d9b5fe4ede9257c4887c78b`。

证据边界：`SOURCE / ANDROID_BUILD / BINDER_PFD / NATIVE_RUNTIME / DEVICE_E2E /
PUBLIC_PREVIEW = PASS`；`STATIC/JVM` 没有由 R3 gate 重跑，`PRODUCTION = NOT_CLAIMED`。
故障注入、连续帧资源增长和 release 分别保留到 R5/R6；首发升级/回滚是否执行由后续明确
产品决定约束，当前最终决定为 `NOT_RUN_BY_PRODUCT_DECISION`。

## R4：公开模型导入与有限兼容

目标：从固定 fixture 扩展到用户模型，但仍保持可说明的兼容集合。

- [x] 发布并校验 manifest schema v1；不得用文件名猜 YOLO family。
- [x] 支持一个官方 fixture 和一个同 decoder 的自训练模型。
- [x] decoder registry 首先只开放明确实现的 decoder ID。
- [x] 对 manifest/node/task/shape/labels 做有界验证和明确错误。
- [x] capability 准确报告 backend/device/task/model-format/decoder/limit。
- [x] 已评估 OCR/YOLO 共用 `PluginImageTransport`；R4 无明确收益，继续延后且不作为门禁。

主 gate：两个 manifest 驱动模型通过真实 native inference 与原图坐标检查。

R4 exit（2026-08-11）：

- [x] sibling `eaaf05d` 发布有限 manifest v1、单项 decoder registry、动态 1-256
  类输出校验与协议 `MODEL_REJECTED`；`cdca31a` 固定 local-only 自训练 fixture
  recipe/provenance，模型、训练数据和测试图均未进入 Git/APK。
- [x] 主仓库 `923530e34` 将模型拒绝稳定映射为 Rhino
  `YOLO_MODEL_REJECTED`；`a83dafd2b` 固定唯一 R4 双模型 gate。
- [x] Provider Kotlin/AndroidTest/CMake/JNI 与 Host/AndroidTest 构建通过；R4 gate
  自身未重跑 `STATIC/JVM`，因此这两层保持 `NOT_RUN_BY_R4_GATE`。
- [x] `QV710AF65F` 在确认无 soak/并发设备任务后完成 API 31、arm64-v8a
  单次 production Rhino gate：官方 YOLO11n 得到 1 bus + 4 person，最小参考
  IoU `0.99999946`；自训练 1 类模型得到 1 个 `synthetic-target`，ground-truth
  IoU `0.93100712`、参考 IoU `0.99999978`。
- [x] 错误 shape `[1,6,8400]` 经公开 `yolo.load()` 稳定返回
  `YOLO_MODEL_REJECTED`；未使用 family 文件名猜测或未知 decoder fallback。
- [x] Host/Provider/instrumentation APK 均不包含模型；Provider 是唯一携带 arm64
  YOLO native 的 APK，Host 与 Provider 同 signer、不同 UID、exact component。
- [x] gate 前后三个临时包均不存在，唯一 staging 已删除，并已在 gate 后独立复查。
- [x] canonical receipt：[`yolo-evidence/r4-summary.json`](yolo-evidence/r4-summary.json)；
  generated receipt SHA-256
  `2fcee2b83e69af5333d741c0e35c0c933011f3786bbebf917bb99c6b80b32f73`。

证据边界：`SOURCE / ANDROID_BUILD / BINDER_PFD / NATIVE_RUNTIME / DEVICE_E2E /
PUBLIC_MODEL_IMPORT = PASS`；兼容范围仅为 schema 中声明的 YOLO11 detect、NCNN、CPU、
640、`in0/out0`、`ultralytics-detect` 和 1-256 labels。`PRODUCTION = NOT_CLAIMED`；
故障注入、资源增长和 RC/release 仍保留到 R5/R6；升级与版本回滚最终按首发产品决定
记录为 `NOT_RUN_BY_PRODUCT_DECISION`。

## R5：聚焦可靠性、性能与 RC

目标：只补首发会造成数据泄漏、死锁、崩溃或明显不可用的验证。

- [x] 覆盖错误 signer/UID 或 identity drift、截断/长度/hash 错误 PFD。
- [x] 覆盖 duplicate/late terminal、busy、timeout、cancel/close 和 provider death。
- [x] 连续 100-500 帧检查 FD/session/native handle 无持续增长。
- [x] 分开记录冷启动、模型加载和 warm inference；噪声性能阈值不作为默认 gate。
- [x] 校验 APK ABI、native load、16 KiB page alignment 和 license assets。
- [x] 形成 CPU/arm64-v8a RC；未支持能力必须明确拒绝。

主 gate：一组聚焦的 JVM、Android、Binder、native 报告。API 24-36 全矩阵、24 小时
soak、所有 YOLO 家族均不作为首发阻塞项。

R5 exit（2026-08-13）：

- [x] 主仓库 `c40464957` 与 sibling `2aa5b100e` 在 gate 前后均 clean；Provider
  `testDebugUnitTest + assembleRc + compileDebugAndroidTestKotlin`、Host 与 instrumentation
  APK、独立 hostile provider APK 串行构建通过，未使用 connected Gradle task；Provider
  focused JVM policy/error tests 为 `8/8`。
- [x] `QV710AF65F` 在确认无 soak/竞争 runner 后完成 API 31、arm64-v8a、4 KiB page
  验证；五个定向 instrumentation 方法全部 PASS，运行前后四个临时包均 absent，唯一
  staging 已删除，未启动 AVD。
- [x] test-only fake provider 覆盖 duplicate/late terminal、Host BUSY、timeout cancel
  ACK/no-ACK、wrong callback UID 与 provider death；production Host 均 fail-closed，dispatch
  后无自动重试。这里的 death 证据只证明 fake provider 会使 pinned session 失效，未运行
  真实 NCNN provider 进程死亡恢复。
- [x] 真实 NCNN provider 对 regular PFD 的短长度、长长度、同长度错误 SHA-256 均返回
  versioned `INVALID_REQUEST` 并关闭 Host descriptor；随后 valid model detect、幂等 close、
  reopen 均通过，空闲态为 0 session / 0 native handle / 0 model directory。
- [x] 真实 provider 完成 20 warmup + 200 measured：Host FD 早/晚中位数
  `107 -> 107`，Provider FD `73 -> 73`；Host RSS 中位数 `279984 -> 275928 KiB`，
  Provider RSS `190408 -> 196920 KiB`，未观察到阈值定义的 runaway。200 帧均有唯一终态、
  图像 PFD 关闭且 provider PID 稳定；close/reopen 后 session/native handle/model directory
  再次归零。
- [x] 设备计时只作记录：cold target `257.475 ms`、session/model open `361.362 ms`、
  first detect wall/provider inference `780.309/580 ms`；200 measured 的 wall/provider
  p50 为 `391.427/269 ms`、p95 为 `426.258/300 ms`，没有性能 PASS/FAIL 阈值。
- [x] Provider RC APK 为 `0.1.0-rc-test-signed`、non-debuggable、CPU/arm64-v8a、无模型；
  设备真实推理证明 4 KiB page 环境的 arm64 native load。`libautojs_yolo.so` 三个 LOAD
  segment 均为 16 KiB alignment 且 `zipalign -P 16` 通过，MPL-2.0、NCNN BSD-3-Clause
  与 provenance assets 均逐字节校验；这只声明静态 `ELF_16K_COMPATIBLE`，没有 16 KiB
  page 设备 load 证据。
- [x] canonical receipt：[`yolo-evidence/r5-summary.json`](yolo-evidence/r5-summary.json)；
  generated aggregate receipt SHA-256
  `2a4a872cf3e13baffd0ad09076c811548e095407faa858b4f0ae3ef3cfbd2f9e`。

证据边界：R5 的 RC 是本地 debug certificate 签发的 `TEST_SIGNED_NONDEBUGGABLE` 候选，
不是生产签名 release；R5 当时验证的 RC artifact 明确关闭 minify/resource shrink，故 R5
receipt 仍不含 shrinker 证据。后续 R6-A 已在独立 Provider source/build gate 中纳入 shrunk
release/RC admission；该新证据不得回写或提升 R5 artifact。`PRODUCTION = NOT_CLAIMED`。真实 Provider startup stale cleanup 仍
只有 JVM/Android build 证据；真实 NCNN provider death/recovery、16 KiB page 设备、长 soak、
完整 API 矩阵、发布签名与索引继续保留到 R6；升级与版本回滚按首发产品决定明确不运行。

## R6：有边界的生产发布

- [ ] 生成签名 Host/Provider release APK、证书和 SHA-256 receipt。
- [ ] 完成插件索引、显式授权/启用和版本兼容；升级与版本回滚按首发产品决定记录为
  `NOT_RUN_BY_PRODUCT_DECISION`。
- [ ] 完成 NCNN provenance、NOTICE、MPL-2.0、Kotlin runtime Apache-2.0 完整正文和模型许可说明。
- [ ] 一台代表性 arm64 物理设备和一个当前 API arm64 环境完成冷/热推理及进程死亡恢复；
  当前主机无法运行 arm64 AVD 时允许 API 35+ arm64 实体机替代。
- [ ] 发布说明列出实际验证的 API/ABI/backend/model，不暗示未验证能力。
- [ ] 继续保持运行时 default-off、fail-closed、无 dispatch 后重试；插件中心对 OFFICIAL
  签名的自动启用只写入显式记录，不改变运行时闸门。

主 gate：完整 `PRODUCTION` receipt；任何缺少的证据均保留对应的明确限制。

### R6-A：发布源码与预检

- [x] 增加 `verify-yolo-r6-publishable.ps1` 的 source/artifact 两个参数集；source 模式
  只输出 `SOURCE_STATIC_PASS / PRODUCTION_NOT_RUN`，artifact 模式必须显式提供两份 APK、
  发布负责人冻结的 Provider versionCode 和确认的生产证书 SHA-256 指纹。
- [x] artifact 预检要求显式 Host/Provider full commit、两个 clean worktree 和 Provider
  source-build receipt；fail-closed 校验 non-debuggable、同生产 signer、APK SHA-256、
  Provider arm64-only/严格 release-entry allowlist、NCNN provenance、NOTICE 和静态 ELF
  包装。Host 仅声明已知 YOLO/NCNN payload absent；已知 R5 TEST-SIGNED certificate 不可被提升。
- [x] 固定首发恢复/撤回手册：无 code 1 predecessor，不声明版本回滚；允许 exact v2
  same-version recovery，缺陷版本先禁用/撤回索引并通过 v3 forward-fix，恢复后运行时仍保持
  default-off；adb 重装需重新显式启用，插件中心重装的 OFFICIAL 包会自动写入启用记录。
- [x] 增加真实 Provider `am force-stop`、旧 session fail-closed/no-retry、显式重新选择并
  reopen 的 opt-in instrumentation 与单 serial gate 源码。

R6-A source gate 当前证据仅为源码/PowerShell parser 静态预检；未读取生产签名秘密，未检查生产 APK，
未执行 ADB、索引或外部发布。因此只能记为 `SOURCE_STATIC_PASS / PRODUCTION_NOT_RUN`。当前
artifact verifier 也不会把可编辑 JSON 或 unsigned Gradle output 当作 production signed APK
的源码证明；受控 producer 落地后，其 consumer 必须重新计算 exact APK hash/signer/version/
VCS metadata，结果为 `PASS_CONTROLLED_SIGNED_BUILD`，但该 receipt 不是 cryptographic
attestation，且设备、索引和发布未完成前不生成 canonical `r6-summary.json`。

### R6-B：生产 APK 与签名（待执行）

- [ ] 由发布负责人提供/确认唯一生产 signer SHA-256 公钥指纹，并在受控环境签发 Host、
  Provider 和 test-only instrumentation；不得把 keystore/alias/password 写入 receipt。
- [ ] 将已启用且由独立 source/build gate admitted 的 minified/shrunk Provider release
  交给受控 signed-build producer；artifact 预检绑定 Host/Provider 版本、证书指纹、长度和 SHA-256。
- [ ] 受控 Host/Provider signed-build producer 生成不可由任意 JSON 字段提升的 receipt，
  将 exact commit、clean source、构建调用和最终 signed APK SHA-256 绑定；此前 artifact
  build identity 和 cryptographic source attestation 保持 false。
- [ ] 生成可发布 APK receipt；静态 16 KiB ELF 证据仍不得替代对应 page-size 设备加载。

### R6-C：索引与首发生命周期边界（待执行）

- [ ] 索引固定 package/exact component/API 与 Host 兼容范围/ABI/backend/APK SHA-256；
  索引项本身不授予授权或启用，安装后运行时保持 default-off，仅插件中心对 OFFICIAL
  签名包自动写入启用记录。
- [ ] 索引绑定首发 `0.1.0 / versionCode=2`；本轮不制作 code 1 predecessor，升级与版本回滚
  固定为 `NOT_RUN_BY_PRODUCT_DECISION`。同版本恢复只允许 archived exact v2；缺陷修复使用
  v3 forward-fix，不同 signer/identity/version drift 均 fail-closed。

### R6-D：设备与发布（待执行）

- [ ] 代表性 arm64 物理设备完成 production APK 冷/热推理与真实 Provider death/reopen。
- [ ] 当前 API arm64 环境完成相同验证。由于当前 x86_64 主机无法安装 arm64 AVD，产品
  决定允许 API 35+ arm64 实体机替代；emulator 仍可用但不是必需。当前候选设备若已有
  用户包或其他任务则 fail-closed 保持 `NOT_RUN`，不能覆盖或用 x86_64 静态构建代替。
- [ ] release note 与索引只列实际通过的 API/ABI/backend/model；最终 receipt 绑定所有
  子报告的 APK SHA-256 后才可声明 `PRODUCTION=PASS`。

### R6-E：当前私有证据归档边界

- [x] 创建 private Provider 仓库并在首次源码推送前、推送后分别确认
  `visibility=private`；匿名 GitHub API 必须返回 `404`。
- [ ] 许可证与最终产物证据刷新后，才允许创建精确 `v0.1.0` tag 和 private Draft
  Release；草稿资产仅保留 Provider APK、脱敏证据摘要和索引准入候选，不上传 Host/
  AndroidTest APK、原始 receipt、设备 serial 或本地绝对路径。
- [x] 2026-09-18 产品决定：Provider 仓库已转为 public；AutoJs6 `6.8.0 / 5275` 正式版
  公开不再是 Provider 公开发布的硬性前置条件（宿主发布暂无计划）。
- [ ] 公开 Release 与 YOLO 官方索引条目仍以 R6-B 生产签名 APK 与 R6-D 设备终验为前置，
  不得由私有归档证据绕过。

## 首发后 backlog（不阻塞 R1-R6）

- Vulkan、32 位 ABI、double/ring buffer 和 10-30 FPS 专项优化。
- YOLOv5、end-to-end NMS、多 decoder。
- Segment、Pose、OBB、tracking。
- `vision.*` 泛化 API。
- INRT 内嵌 engine/打包器选择项。

## 设备保护边界

`QV710AF65F` 仅在确认没有 soak 测试运行时可用于 YOLO 验证，并且所有命令必须使用
明确 serial；优先备用设备为 Xiaomi `23046RP50C` 或 `QV770340J7`。在线 AVD 也可使用，
但临时启动的 AVD 必须在验证结束后关闭。receipt 必须记录设备、APK/模型 hash、前置
状态和恢复结果。
