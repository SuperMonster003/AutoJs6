# AutoJs6 YOLO 独立插件部署 Roadmap

本文是 `work-yolo` 的可执行状态表。它以 2026-08-11 的本地代码为准，
不以公开仓库中较旧的 Paddle OCR 结构为准。

## 当前结论

| 项目 | 本地事实 |
| --- | --- |
| R0 基线 | `work-yolo@10c6feb90`，R0 开始前工作树干净 |
| R0 本地提交 | `3c170896d8e4b528f1aa85953a4698d1742c8344`，未 push |
| 通用 Host | 已有 `AidlPluginHost`，包含发现、授权、显式组件绑定、连接池和 dedicated binding lease |
| OCR Host | 当前是统一的 `OcrPluginHost`；raw SharedMemory/pipe 仍是其私有实现 |
| YOLO Runtime | R1 sibling 已有 CPU/arm64 NCNN/JNI provider 与 debug APK；主仓库仍不包含 runtime，且尚无 Host/Rhino 生产路由 |
| R0 当前声明 | `SOURCE_DEPLOYED / STATIC_PASS / JVM_DIAGNOSTIC_PASS / AIDL_DIAGNOSTIC_PASS / ANDROID_BUILD_PASS`；不能证明 Binder/native/设备可运行 |
| 生产入口 | 当前 main 工作树没有 Host、Rhino、插件中心、Manifest query 或自动执行入口；因此仍不可调用，不由 R0 marker gate 证明 |

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
6. 安装 provider 不等于启用。Host 必须保持 default-off，并固定一个已授权的
   exact component；不静默选择、不跨 provider 回退。
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
| `PRODUCTION` | 签名、hash、索引、升级/回滚和发布 receipt | 未明确覆盖的 API/ABI/backend |

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

- [ ] 新增 `core/plugin/yolo/YoloPluginHost.kt`，组合现有 `AidlPluginHost`。
- [ ] 增加最窄的 package-visibility query/插件中心发现映射，但不自动启用 provider。
- [ ] 为通用选择策略增加兼容的 `defaultEnabled` 参数，YOLO 固定传 `false`。
- [ ] 使用 exact component、授权、签名/UID/version pinning 和 dedicated binding lease。
- [ ] `openSession`、`detect` 均显式 `retryOnRemoteException=false`。
- [ ] Host 以只读 PFD 打开模型；provider 不申请共享存储访问权限。
- [ ] 先实现窄范围 `YoloImageTransport`，不把 OCR 重构设为本阶段门禁。
- [ ] session 同时持有 remote Binder 与 binding lease，close/death 时幂等释放。
- [ ] 保持无 Rhino/Node/Explorer 生产调用点。

主 gate：Host 与 provider Android build 通过；一次真实 Binder/PFD smoke 作为独立
`BINDER_PFD` 证据。没有设备时只允许声明 `ANDROID_BUILD`。

## R3：Rhino opt-in Preview 端到端

目标：提供第一个可用但仍显式启用的脚本闭环。

- [ ] 通过 `Yolo(this).augment(target)` 注册 `yolo`，不只增加一个字段。
- [ ] 实现 `yolo.load()`、`detector.detect()`、`detector.close()`。
- [ ] detector 实现 `Closeable` 并注册到 `ScriptRuntime.closeableManager`。
- [ ] 只接受显式 component/provider 或明确保存且已授权的选择。
- [ ] 结果 bounds 使用原图坐标，可直接服务 click/swipe。
- [ ] 增加一个最小截图/图片示例和稳定的脚本错误码。

主 gate：记录一个命名 Host APK、Provider APK、模型 SHA-256、设备 API/ABI、脚本和
结果的 `JS -> Host -> Binder/PFD -> JNI/NCNN -> Detection[]` receipt。

R3 exit 只能声明 `PUBLIC_PREVIEW` 或 `PARTIAL_DEVICE`，不能声明生产完毕。

## R4：公开模型导入与有限兼容

目标：从固定 fixture 扩展到用户模型，但仍保持可说明的兼容集合。

- [ ] 发布并校验 manifest schema v1；不得用文件名猜 YOLO family。
- [ ] 支持一个官方 fixture 和一个同 decoder 的自训练模型。
- [ ] decoder registry 首先只开放明确实现的 decoder ID。
- [ ] 对 manifest/node/task/shape/labels 做有界验证和明确错误。
- [ ] capability 准确报告 backend/device/task/model-format/decoder/limit。
- [ ] 评估并仅在收益明确时抽取 OCR/YOLO 共用 `PluginImageTransport`。

主 gate：两个 manifest 驱动模型通过真实 native inference 与原图坐标检查。

## R5：聚焦可靠性、性能与 RC

目标：只补首发会造成数据泄漏、死锁、崩溃或明显不可用的验证。

- [ ] 覆盖错误 signer/UID 或 identity drift、截断/长度/hash 错误 PFD。
- [ ] 覆盖 duplicate/late terminal、busy、timeout、cancel/close 和 provider death。
- [ ] 连续 100-500 帧检查 FD/session/native handle 无持续增长。
- [ ] 分开记录冷启动、模型加载和 warm inference；噪声性能阈值不作为默认 gate。
- [ ] 校验 APK ABI、native load、16 KiB page alignment 和 license assets。
- [ ] 形成 CPU/arm64-v8a RC；未支持能力必须明确拒绝。

主 gate：一组聚焦的 JVM、Android、Binder、native 报告。API 24-36 全矩阵、24 小时
soak、所有 YOLO 家族均不作为首发阻塞项。

## R6：有边界的生产发布

- [ ] 生成签名 Host/Provider release APK、证书和 SHA-256 receipt。
- [ ] 完成插件索引、显式授权/启用、版本兼容、升级和回滚。
- [ ] 完成 NCNN provenance、NOTICE 和模型许可说明。
- [ ] 一台代表性 arm64 物理设备和一个当前 API 模拟器完成冷/热推理及进程死亡恢复。
- [ ] 发布说明列出实际验证的 API/ABI/backend/model，不暗示未验证能力。
- [ ] 继续保持 default-off、fail-closed、无 dispatch 后重试。

主 gate：完整 `PRODUCTION` receipt；任何缺少的证据均保留对应的明确限制。

## 首发后 backlog（不阻塞 R1-R6）

- Vulkan、32 位 ABI、double/ring buffer 和 10-30 FPS 专项优化。
- YOLOv5、end-to-end NMS、多 decoder。
- Segment、Pose、OBB、tracking。
- `vision.*` 泛化 API。
- INRT 内嵌 engine/打包器选择项。

## 设备保护边界

`QV710AF65F` 当前不属于 YOLO 验证窗口。未经明确授权，不对它运行 ADB、Gradle
connected task、安装、查询或竞争资源。未来设备命令必须使用明确 serial，且 receipt
必须记录设备、APK/模型 hash、前置状态和恢复结果。
