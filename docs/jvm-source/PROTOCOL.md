# JVM Source Plugin Protocol

本文记录 AutoJs6 宿主与独立 JVM source runtime 插件之间当前可发布的协议面。
`plugin-api/jvm-source-api` 中的 AIDL、消息类型、codec 与 validation 是规范性实现；
本文用于说明发现、信任、生命周期和版本演进规则，不复制生成式验收证据。

## 1. 当前版本

| 项目 | 当前值 |
| --- | --- |
| Engine ID | `jvm-source` |
| Service action | `org.autojs.plugin.JVM_SOURCE` |
| Signature permission | `org.autojs.permission.PLUGIN` |
| Protocol | `1.1` |
| Entry API | `2` |
| Wire schema | `1.1`，tagged binary document |
| Host / Protocol minimum Android API | 24 |
| Java Runtime / Kotlin Runtime minimum API | 24 / 26 |
| Concurrent sessions per provider | 1 |

宿主协议源码位于 `plugin-api/jvm-source-api`。独立插件构建时不读取宿主工作树，
而是消费其 `protocol/` 目录中冻结的 `jvm-source-api.aar`、`common-plugin-api.aar`
与 `protocol-wire-api.aar`；锁文件记录每个 AAR 的 SHA-256 和来源状态。

## 2. 发现、选择与信任

Provider APK 必须导出一个处理 `org.autojs.plugin.JVM_SOURCE` 的 service，并要求
`org.autojs.permission.PLUGIN`。宿主只接纳同时满足以下条件的候选：

1. action、显式 component、exported/enabled 状态和 permission 均符合合同；
2. provider 与宿主签名证书相同；
3. Plugin Center 中逻辑启用；
4. `JvmProviderInfo` 的协议范围、Entry API 和宿主版本范围兼容；
5. capabilities 明确声明所运行的语言和固定 toolchain 身份。

M4 起，service 用 `org.autojs.plugin.JVM_SOURCE_LANGUAGE=java|kotlin` manifest metadata
声明发现阶段的语言，宿主只绑定与请求语言匹配的候选。M4 之前已发布、没有该 metadata 的
Java Runtime 仅作 Java 候选兼容；缺少 metadata 的 service 永远不能接收 Kotlin 请求。

宿主按 Java/Kotlin 分别持久化显式 provider component。该 component 消失、禁用或失去准入资格时，
宿主会清除旧选择；若恰好只有一个合格候选，则自动接管。多个候选且没有有效选择时
保持歧义失败，不按查询顺序静默选择。

## 3. 会话生命周期

`IJvmSourceProvider` 提供只读 provider info/capabilities，并通过 `openSession` 创建一次执行：

1. 宿主冻结源码字节，计算长度与 SHA-256，并生成 request ID、规范编译单元名、入口类、
   deadline、输出上限和 host-call allowlist。
2. 源码通过只读 `ParcelFileDescriptor` 传入；stdout/stderr 使用独立的有界管道，宿主在执行中
   增量校验 UTF-8 并按完整行发布到 console；源码正文和用户生成的 class/DEX 不进入 Binder metadata。
3. Provider 必须先回调 `onStarted`，随后可发送有界 `onDiagnostic`，最后只能进入
   `onCompleted`、`onFailed` 或 `onCancelled` 之一。
4. 宿主把 source SHA、toolchain fingerprint、编译器/worker 身份、artifact digest、DEX
   profile 和输出长度与本次请求逐项核对。协议违例会终止并退役该 transport。
5. `IJvmSourceSession.cancel/close`、deadline、Binder death 和宿主销毁都能结束会话；插件
   worker 为单任务隔离进程，可在宽限期后整进程终止。
6. Worker 只能通过 `IJvmHostBridge` 调用本次请求 allowlist 中的宿主能力；每次调用仍受
   request ID、caller identity、payload 大小和独立 timeout 约束。

Provider 进程负责对应语言编译器、D8 与 worker 生命周期；宿主进程从不加载用户生成的 DEX。

## 4. 脚本能力与授予

Protocol 1.1 的 provider 在 `JvmSourceCapabilities.scriptCapabilities` 中声明脚本能力，宿主仅把
这些声明写入本次 `JvmSourceRequest.grantedCapabilities`。需要跨进程调用宿主的能力还必须同时
出现在 `allowedHostCalls`；validation 要求两者严格对应，worker 与 compiler proxy 会再次逐项校验。

| Capability | Entry API 2 | 数据路径 |
| --- | --- | --- |
| `app.launch` | `context.app().launch(packageName)` | `app.launch` host bridge |
| `console.stream` | `context.console().log/error(message)` | worker stdout/stderr 有界管道 |
| `runtime.sleep` | `context.sleep(millis)` | worker 本地 sleep，session 取消会 interrupt 并抛出 `JvmCancellationException` |
| `toast.show` | `context.toast(message)` | `toast.show` host bridge，canonical 单字段 JSON，消息上限 8 KiB UTF-8 |

stdout/stderr reader 从 session 打开前即开始 drain，既保留 1 MiB 上限、最终长度与 SHA-256 双向校验，
也避免等待 terminal 后一次性显示。增量 decoder 会跨 pipe chunk 保留未完成的 UTF-8 字节，只在完整
换行或 EOF 时发布，非法 UTF-8 仍然失败关闭。`toast.show` 与 `app.launch` 共用 request ID、单调 call ID、
固定 compiler/worker PID/UID、独立 timeout 和重复调用拒绝机制。

## 5. 单文件源码合同

当前 Java provider 接受任意合法 `.java` 文件名，包括含空格或连字符的文件名。为保持
Protocol 1 与 ECJ/入口分析链一致，宿主发送的编译单元名仍规范为 `Main.java`：

- 无 `package` 时入口类为 `Main`；
- 有 `package a.b;` 时入口类为 `a.b.Main`；
- 入口类必须实现 `org.autojs.plugin.jvmsource.api.AutoJsJvmEntry`；
- `import` 与 static import 均允许；当前仍是单文件编译。

Kotlin Runtime 接受任意合法 `.kt` 文件名，宿主发送的编译单元名规范为 `Main.kt`：

- 无 `package` 时入口类为 `Main`；有 `package a.b` 时为 `a.b.Main`；
- 入口类同样必须实现 `AutoJsJvmEntry`；普通 `import` 允许；
- 编译器为 Kotlin/JVM 2.3.21，JVM target 1.8，kotlin-stdlib 由 provider 提供；
- `.kts`、KAPT/KSP、Compose、Java/Kotlin 混编和多文件工程不在 Protocol 1 范围；
- Kotlin provider 的最低系统为 API 26，因为固定编译器本身使用 Android 8.0 才提供的
  `java.lang.invoke.MethodHandle`；这不改变协议与 Java provider 的 API 24 下限。

文件名与 public class 的完全自由映射、多文件工程、自定义 classpath 和注解处理器不属于
Protocol 1 的行为承诺。

## 6. 固定上限

宿主和 provider 都必须执行 `JvmSourceValidation`。协议绝对上限包括：

| 资源 | 上限 |
| --- | ---: |
| Source | 4 MiB |
| Binder metadata document | 128 KiB |
| stdout | 1 MiB |
| stderr | 1 MiB |
| 单条 diagnostics 总预算 | 64 KiB |
| 返回 JSON | 64 KiB |
| Host bridge payload | 64 KiB |
| Class artifact | 20 MiB |
| DEX artifact | 32 MiB |
| 默认 / 最大执行时间 | 30 s / 120 s |

Provider 可以在 capabilities 中声明更小的限制，宿主按协商后的较小值发起请求。

## 7. 诊断与用户错误

编译 diagnostics 保留规范编译单元名、行、列和 severity，宿主按到达顺序写入 console。
Provider 的稳定错误由 `JvmSourceErrorCode` 与 failure phase 传输，宿主映射为
`[JVM_SOURCE_*]` 用户错误。缺失、禁用、不可信、协议不兼容、busy、编译失败、运行失败、
取消和 timeout 必须可区分；后台入口只返回错误，交互式入口可以引导用户打开 Plugin Center。

## 8. 传输层决定（M3）

M3 保留当前专用 `AndroidJvmSourceTransport`，不迁移到通用 `AidlPluginHost`。后者适合
短调用或普通 leased binding，也已具备 Plugin Center trust/enable 管理；但 JVM source 是
长生命周期的多 Binder 会话，还包含 source/output FD 所有权、同步 PID identity barrier、
started→terminal 状态机、deadline/cancel、provider 更新漂移检测和专用 compiler trust policy。
只替换 discovery/binding 会同时维护两套生命周期，不能删除上述专用逻辑，也没有用户可见收益。

重新评估的触发条件是：通用 host 原生表达整套 session lease/identity/FD 生命周期，或协议升级
需要多 provider 统一仲裁。在此之前，Plugin Center 负责展示与启停，专用 transport 负责执行。

## 9. 兼容性演进

- 增加可忽略 tagged 字段、capability 或新的诊断 code，可以提升 minor 版本；旧 reader 必须
  忽略未知的非 required 字段。
- 改变必填字段、AIDL 方法、FD 语义、终态规则或 Entry ABI，需要提升 major/Entry API，
  同时更新宿主 API 工程、独立插件冻结 AAR 和 lock。
- 新语言或新宿主能力必须由 capabilities/allowlist 显式声明；不得因文件扩展名相似而回退到
  另一语言 provider。
- 发布前至少验证：宿主缺插件错误、安装后执行、卸载后错误、无需重装宿主重新安装并恢复。

Entry API 2 为增加 `console/sleep/toast` 提升了 ABI 版本，因此 Protocol 1.1 宿主不协商 1.0 provider；
Java/Kotlin provider 也只声明 1.1。现有实现 `AutoJsJvmEntry` 的源码无需改动即可重新编译，但宿主、
provider 与冻结 `jvm-source-api.aar` 必须成套更新，toolchain fingerprint 会使旧编译缓存自然失效。
