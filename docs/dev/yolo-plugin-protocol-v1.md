# YOLO Plugin Protocol V1

本文定义 AutoJs6 与独立 YOLO provider APK 之间的 detect-only V1 边界。
`plugin-api:yolo-api` 的 AIDL、codec 和 validation 源码是规范；本文用于解释生命周期、
所有权和部署约束。R0 只部署协议源码，不声明 Android、Binder、NCNN 或设备可用。

## 模块与进程边界

| 组件 | 职责 |
| --- | --- |
| `plugin-api:common-plugin-api` | 独立 INFO metadata service 使用的现有插件中心契约 |
| `plugin-api:protocol-wire-api` | 有版本、有界、可演进的 TaggedWire envelope |
| `plugin-api:yolo-api` | YOLO AIDL、消息、codec、validation 和 session lifecycle policy |
| AutoJs6 Host（R2） | 授权、exact-component 选择、PFD、session lease、JS 结果转换 |
| `AutoJs6-Plugin-Yolo-NCNN`（R1） | 模型验证、预处理、NCNN、decode/NMS、native resource |

主 APK 不包含 NCNN、模型或 provider native 库。Provider 不接收主程序文件路径，
也不需要共享存储权限。

## Binder surface

```text
IYoloProvider
  byte[] getProviderInfo()
  byte[] getCapabilities()
  IYoloSession openSession(
      byte[] request,
      ParcelFileDescriptor[] modelDescriptors,
      IYoloCallback callback
  )

IYoloSession
  oneway detect(byte[] request, ParcelFileDescriptor imageFd)
  oneway cancel(byte[] request)
  oneway close()

IYoloCallback
  oneway onDetected(byte[] result)
  oneway onFailed(byte[] error)
  oneway onCancelled(byte[] cancellation)
```

YOLO runtime service 的 provider identity、执行能力、请求和回调全部使用 TaggedWire；
不接收远端 `Bundle extras`、任意实现类 Parcelable 或 Binder 内大对象。独立的
`org.autojs.plugin.INFO` service 继续使用现有插件中心 metadata 契约，但它不参与
YOLO session dispatch。

Provider-info wire message 至少声明：

- provider ID，例如 `autojs6-yolo-ncnn`；
- display name、author、version name/code；
- backend/provider variant，例如 `ncnn`，不表示模型版本；
- 可选的 Host version 范围；
- APK 实际包含的 ABI。

未来 Host 只在严格解码并复查 package identity 后，把这些本地值映射为现有
`PluginInfo` 供通用选择逻辑使用；远端不能直接提供一个带 Bundle 的对象图。

## TaggedWire messages

V1 定义以下有界 schema；精确 schema ID、tag 和默认限制见 `YoloContract` 与
`YoloCodec`。

| Message | 方向 | 关键内容 |
| --- | --- | --- |
| provider info | Provider -> Host | provider ID、展示/版本信息、backend、Host 范围和实际 ABI |
| capabilities | Provider -> Host | 协议范围、backend、task、model format、device、pixel format 和资源上限 |
| open-session request | Host -> Provider | request ID、版本、task、device、threads、模型 artifact declarations |
| model artifact | nested | role、逻辑名、长度、SHA-256、FD index |
| detect request | Host -> Session | sequence、request ID、width/height/stride/length、pixel format、threshold、IoU、max detections |
| detect result | Callback -> Host | sequence、request ID、原图尺寸、耗时和有界 detection 列表 |
| detection | nested | class ID、label、confidence、原图坐标 bounds |
| error | Callback -> Host | sequence、request ID、稳定 code/phase、有限 message、retryable 诊断位 |
| cancel request/result | 双向 | sequence、request ID、reason 和 elapsed time |

未知 optional tag 按 TaggedWire 规则跳过；未知且标记为 required-for-reader 的 tag
必须拒绝。已知单值字段重复、错误类型、非规范顺序、尾随数据、无效 UTF-8、
NaN/Infinity 和超过上限的 document 必须在进入 engine 前拒绝；schema 明确定义的
artifact、detection 和 capability 列表允许 repeated tag。

## 模型 descriptor 规则

`openSession` 的 wire request 声明每个 model descriptor。每项至少包含：

- `role`: `manifest`、`graph`、`weights`、`labels` 或协议允许的扩展 role；
- `logicalName`: 单个安全逻辑文件名，不是宿主绝对路径；
- `sizeBytes`: 必须读取的精确长度；
- `sha256`: 32 字节内容摘要；
- `descriptorIndex`: `modelDescriptors` 中的位置。

规则：

1. descriptor index 必须唯一、连续，并恰好消费传入数组中的每一项。
2. manifest 是必需 artifact；NCNN R1 还要求 graph/weights。
3. Provider 在加载前验证 exact length、EOF、SHA-256 和总字节上限；截断、尾随、
   摘要不符或多余 descriptor 均失败关闭。
4. Provider 可在同步 `openSession` 期间读取或复制 descriptor；方法返回后不得继续
   借用未取得所有权的 Host 对象。
5. `openSession` 返回前完成模型加载并返回一个新的 session Binder。失败不得返回
   半初始化 session。

模型类型由 manifest/声明的 decoder ID 决定，禁止根据文件名包含 `yolov5`、
`yolo11` 等字样推断 tensor 语义。

## 图像 descriptor 规则

V1 首条竖切只要求 raw RGBA_8888：

- `width`、`height` 为原图尺寸；
- `rowStride >= width * 4`；
- `sizeBytes == rowStride * height`，使用无溢出的 `Long` 计算；
- `imageFd` 必须提供 exact bytes 和 EOF；
- 不要求每帧计算 SHA-256，以免把高频截图 hash 变成固定成本。

Provider 的 Binder ingress 只做有界验证，并在返回前把 `imageFd` 复制为 worker
独占的 descriptor lease，然后立即把工作交给 session worker；Binder thread 不直接
执行 NCNN。复制失败必须产生稳定失败且不得 dispatch。Provider 在每个终态关闭 ingress
和 worker descriptor；Host 可在 oneway transact 返回后关闭自己的 PFD 副本。

Host 和 Provider 都必须把同一 session 的 detect/cancel/close 串行化到一条控制
lane；不能依赖多个调用线程的到达顺序来决定 cancel 或 close 是否先于 inference。

SharedMemory、raw pipe 或其他 Host transport 只要满足相同 RGBA/stride/length 语义，
对 provider 均等价。encoded JPEG/PNG、buffer reuse 和 ring buffer 不属于 V1 R0/R1 gate。

## Session lifecycle

每个 `IYoloSession` 具有以下固定规则：

```text
OPEN, idle, lastSequence=0
  detect(1,A) -> OPEN, active=(1,A), lastSequence=1

OPEN, active=(1,A)
  detect(2,B) -> (2,B) receives BUSY; B is not queued; lastSequence=2
  cancel(1,A) -> cancellation requested
  detected/failed/cancelled(1,A) -> OPEN, idle
  close() -> CLOSED; (1,A) receives SESSION_CLOSED cancellation

CLOSED
  close() -> no-op
  detect/cancel -> ignored or stable CLOSED failure; never dispatch inference
```

- 一条 session 同时最多一条 active detect，队列长度为零。
- 每个被接受的 `(sequence, request ID)` 恰好产生一个 `onDetected`、`onFailed` 或
  `onCancelled`。
- late/duplicate terminal 必须被丢弃并记诊断，不能再次完成 Host promise。
- cancel 和 close 幂等；脚本正常结束、异常退出和 Binder death 都走同一清理路径。
- Host 在 session 生命周期内持有 remote Binder 与 dedicated binding lease。
- Host 使用 `retryOnRemoteException=false`。`openSession` 或 `detect` dispatch 后的
  `RemoteException`/death 不自动重放，因为无法证明远端尚未接收请求。

同步 `openSession` 的拒绝使用 Binder 公共 SDK 可传输的 `IllegalArgumentException`；
其 message 必须由 `YoloOpenSessionFailureCodec` 编码为版本化前缀、
`YoloErrorCode.wireCode` 和有界文本。Host 只解析带已知前缀的异常，其他异常一律
fail-closed 为内部错误；文本不得包含文件路径或模型内容。detect 已经 dispatch 后的
失败只通过 callback terminal 传递，不再额外抛出第二个终态。平台内部的
`ServiceSpecificException` 不属于第三方应用公开 SDK，协议 V1 不依赖该隐藏类型。

每个 detect 还携带一个正数 `sequence`。同一 session 中每个新请求的 sequence 必须
严格大于此前看到的 sequence（允许跳号）；Provider 即使以 BUSY/CLOSED 拒绝，也消费
该 sequence。terminal/cancel 必须同时匹配 active 的 sequence 与 16 字节 opaque
request ID，迟到或乱序消息被丢弃。Host 仍不得复用 request ID；JS number 不用于承载
request/session identity。

## 坐标与 detection 语义

Provider 内部负责 resize、letterbox、decode、NMS 和逆变换。回调中的 bounds：

- 使用原始输入图像像素坐标；
- `left <= right`、`top <= bottom`；
- 被 clamp 到 `[0,width] x [0,height]`；
- confidence 必须为有限的 `[0,1]` 数值；
- detection 数量不得超过 request、provider 和协议三者的最小上限。

这使脚本结果可直接用于 AutoJs6 的 click/swipe 坐标，无需了解 640x640 letterbox。

## 发现、授权与失败关闭

- Discovery 使用 `org.autojs.plugin.YOLO` action。
- 实际连接必须是 actionless exact-component bind；Service `onBind()` 不得因
  `intent.action == null` 返回 null。
- Binder Service 必须使用 `org.autojs.permission.PLUGIN`，并运行在独立 provider
  进程。插件还应提供现有插件中心可见的 INFO service。
- 安装不等于启用。R2 Host 运行时必须把 YOLO 选择默认设为关闭，并要求已授权 provider；
  唯一的自动启用来源是插件中心对宿主同证书 (OFFICIAL) provider 全新安装写入的显式
  启用记录（2026-09-18），运行时仍只读取该记录。
- capability/identity 必须从将要 dispatch 的同一 Binder 和同一固定 package identity
  得到；UID、完整 signer set、version/component drift 均失败关闭。
- 显式请求 YOLO 时，缺少/不兼容/死亡的 provider 产生稳定错误；不得退到 OCR、Rhino、
  host 内 native 或另一个未选择 provider。

## R0 非目标

- 没有 NCNN/JNI/CMake/model binary。
- 没有 AutoJs6 Host、Rhino API、插件中心或 Manifest query 改动。
- 没有 Binder、PFD、APK、native 或设备验收。
- 没有 Segment/Pose/OBB/tracking、Vulkan、32 位 ABI、ring buffer 或 INRT。

因此 R0 的 `SOURCE`/`STATIC` 通过只能说明协议骨架已部署，不能说明 YOLO 已可运行。
