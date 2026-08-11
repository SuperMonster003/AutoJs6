# Python R5-P1 只读能力快照协议

R5-P1 在不改变 AIDL 的前提下，为 Python Runtime 协议 1.1 增加一个可选、只读且有界的宿主能力快照。它复用现有 `openSession(byte[], ParcelFileDescriptor[], ...)` 事务、`PythonPayloadReference`、长度和 SHA-256 校验，不建立插件到宿主的反向 Binder broker。

## Wire 扩展

- 协议上限从 `1.0` 提升为 `1.1`；TaggedWire schema major/minor 仍为 `1.0`，因为新增字段都是可跳过的 optional field。
- `PythonPayloadKind.HOST_CAPABILITY_SNAPSHOT = 4`。
- `PythonExecutionRequest` tag `13` 为可选 `hostCapabilitySnapshot`。
- `PythonRuntimeCapabilities` tag `10` 为可选 `supportsHostCapabilitySnapshot`，缺失时按 `false`。
- `PythonRuntimeResourceLimits` tag `12` 为可选 `maxHostCapabilitySnapshotBytes`，缺失时按 `0`。
- 全局上限 `MAX_HOST_CAPABILITY_SNAPSHOT_BYTES = 64 KiB`。
- `HOST_CAPABILITY_SNAPSHOT_REJECTED = 16` 只能作为已调度执行的 `INPUT_VALIDATION` 错误，不能授权重试。

快照引用必须使用唯一且完整覆盖 descriptor 数组的索引，并通过既有 declared-length 与 SHA-256 校验。空快照、超过全局或 provider 上限的快照、错误 payload kind、重复/遗漏 descriptor 均 fail closed。只有 request protocol 为 `1.1` 或同一 major 的后续版本、provider 显式支持且声明非零上限时才能携带该引用；当前协议上限仍是 `1.1`。

## 兼容矩阵

| 组合 | 行为 |
| --- | --- |
| 新宿主 + 旧 provider | Wire/API 层仍可协商 `1.0` 且不附加快照；当前 R5 预览宿主把快照作为执行要求，因此会在选择阶段拒绝旧 provider，不执行也不回退。 |
| 旧宿主 + 新 provider | 新 provider 应声明 `protocolMin=1.0, protocolMax=1.1`；旧 reader 跳过 optional tags，继续使用原 `openSession` 和 1.0 request。 |
| 新宿主 + 新 provider | 协商 1.1 且支持标志/上限有效后，才附加快照 PFD。 |

现有四个 AIDL 文件及方法顺序保持原样。新 provider 的原 `openSession` 必须同时接受其声明范围内的 1.0 与 1.1 request，不能把最低版本一并提升到 1.1。

## R5-P1 边界

快照内容只应包含 execution-scoped 的白名单标量、TaggedWire/JSON 数据，例如只读 app/device 信息；不得包含 `Context`、`PackageInfo`、`Build`、`Bundle`、任意 Java/Android 活对象、Binder、宿主私有路径或设备标识符。

project-scoped 文件读取直接使用插件已验证并隔离的 workspace，按相对路径、realpath containment、普通文件和读取上限校验；不通过本协议读取宿主实时文件。当前快照已经绑定 execution ID。本切片没有活能力调用；未来确有需求的 broker 还必须在每次调用重验 execution ID、provider UID 和 grant，并定义 timeout、quota 与销毁所有权。R5-P1 不声称已经提供双向 broker。
