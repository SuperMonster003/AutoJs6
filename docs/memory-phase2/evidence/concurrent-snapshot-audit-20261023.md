# C36 并发集合快照写法全量核对 (2026-10-23, mp2-snap)

附录 C Q8 选 (b): 核对 `app/src/main/java/org/autojs/autojs/engine/` 与 `core/plugin/` 中对并发集合取快照或取单项的写法 (C35 同类). 行号以修复前的 f39ddf4da 为准; 逐处一行的全表见同目录 `concurrent-snapshot-audit-20261023.tsv` (965 行).

## 方法

- 扫描: 对两个目录的全部 `.kt` 文件匹配 `toList` / `toSet` / `toMap` / `toMutableList` / `toMutableSet` / `toMutableMap` / `toHashSet` / `toSortedMap` / `sortedWith` / `sortedBy` / `sortedByDescending` / `sorted` / `sortedDescending` / `reversed` / `take` / `takeLast` / `drop` / `first` / `last` / `single` / `firstOrNull` / `lastOrNull` / `singleOrNull` / `elementAt` / `random`, 回溯接收者链, 与同文件的声明对照, 声明或初始化含 `ConcurrentHashMap` / `newKeySet` / `CopyOnWrite*` / `ConcurrentLinked*` / `*BlockingQueue` / `ConcurrentSkipList*` / `synchronized*` 的记为并发 (共 78 个并发声明). Java 文件人工查 `new ArrayList<>(...)` 等复制写法.
- 人工核对: 接收者为并发集合的每一处都查了增删路径所在的线程与锁; 其余各处查接收者的来源 (新建集合, 函数返回值, 普通局部变量或参数, 数组, 常量, 枚举 entries).
- 判定依据: Kotlin 的 `toList()` / `toSet()` / `toMap()` 先读 `size`, 为 1 时用 `iterator().next()` 取那一项 (列表用 `get(0)`), `sortedWith` / `sorted*` / `reversed` / `take` 等在 1 项以内也走这条路径, `first()` / `last()` / `single()` 在集合变空时抛出; 其他线程在两步之间移除该项, 就抛 `NoSuchElementException` (COW 列表抛 `IndexOutOfBoundsException`). 安全的写法: `toArray()` 复制 (`ArrayList(c)`, `toTypedArray()`, `toMutableList()`), 直接遍历视图 (CHM 弱一致, COW 快照迭代), 带谓词的 `firstOrNull {}` / `singleOrNull {}` (只用 `hasNext` / `next`), 所有增删都在同一线程或同一把锁内.

## 汇总

| 项 | 处数 |
| --- | --- |
| 盘点到的调用 | 966 (Kotlin 965, Java 1) |
| 接收者为并发集合 | 24 (Kotlin 23, Java 1) |
| 其中竞态 (本次修复) | 17, 分布在 15 个文件 |
| 其中安全 | 7 |
| 扫描误报 (同名但不是并发集合) | 7, 均安全 |
| 接收者非并发 | 935 (新建集合 267, 函数返回值 88, 本文件声明的普通集合 / 局部变量 / 参数 430, 外部属性 / 数组 / 常量 150) |
| C35 已修 (不在扫描结果中) | 4 |

普通 `LinkedHashMap` / `HashMap` 跨线程使用的问题属另一类, 未在本项核对. COW 监听器集合在两个目录里都是直接 `forEach`, 安全.

## 竞态 17 处 (已修)

修法: 新增 `core/plugin/ConcurrentSnapshot.kt` 的 `Collection<T>.concurrentSnapshot()` (`ArrayList(this)`, 即经 `toArray()` 复制), 17 处的 `toList()` / 直接 `sortedWith` 改为先 `concurrentSnapshot()`, 不改其余语义. 单测 `ConcurrentSnapshotTest` 用覆写 `size` 的 `ConcurrentHashMap` 在读取大小后让另一线程清空, 确定性复现 `toList()` 抛 `NoSuchElementException`, 并验证 `concurrentSnapshot()` 在同一时序与另一线程反复增删时都不抛.

| 位置 | 调用 | 接收者 | 并发性 | 结论 |
| --- | --- | --- | --- | --- |
| core/plugin/agent/ThreeStoveAgentRunRegistry.kt:23 | `runs.values.toList()` | ConcurrentHashMap | 链路丢失回调取快照时, poller 线程 (70 / 73) 与 Binder 线程 (149 / 158) 同时移除 | 竞态, 已修 |
| core/plugin/dex/DexCompilerExperimentalHarness.kt:181 | `attempts.toList()` | newKeySet | 工作线程拒绝回调取快照时, 提交线程 += (192) 与结束 -= (994) | 竞态, 已修 |
| core/plugin/jvm/RemoteJvmSourceHost.kt:147 | `actions.toList()` | CopyOnWriteArrayList | close 时其他线程的 onCancel 注册后自行移除, 只剩一项时 `get(0)` 越界 | 竞态, 已修 |
| core/plugin/mail/MailSessionClient.kt:276 | `inflight.values.toList()` | ConcurrentHashMap | 会话失败取快照时, 调用完成路径 (233 / 242 / 249) 移除 | 竞态, 已修 |
| engine/NodeAudioRecorder.kt:168 | `entries.values.toList()` | ConcurrentHashMap (进程级) | 通知停止 (主线程) 与脚本线程 `release(id)` (225) | 竞态, 已修 |
| engine/NodeAudioRecorder.kt:200 | `entries.values.toList()` | 同上 | onDestroy 与脚本线程 `release(id)` (225) | 竞态, 已修 |
| engine/NodeBridgePluginToolsBridge.kt:119 | `operations.values.toList()` | ConcurrentHashMap | destroy 时 Binder 线程的结果到达 `finish` (458) 移除 | 竞态, 已修 |
| engine/NodeBridgeProtocol.kt:1040 | `javaHandles.keys.toList()` | ConcurrentHashMap | `javaLock` 内取快照, 资源登记表的释放回调 (4971) 在锁外移除 | 竞态, 已修 |
| engine/NodeBridgeProtocol.kt:1061 | `notificationHandles.values.toList()` | ConcurrentHashMap | `synchronized(notificationHandles)` 内取快照, 登记表的释放回调 (3889) 在锁外移除 | 竞态, 已修 |
| engine/NodeBridgeResourceRegistry.kt:200 | `active.values.sortedWith(...)` | ConcurrentHashMap | 诊断转储与 release / close (108 / 118 / 126 / 141) 并发 | 竞态, 已修 |
| engine/NodeJsEncryptedModuleSourceProvider.kt:641 | `activeWriteDescriptors.toList()` | newKeySet | 写线程与期限 Runnable 移除 (575 / 586 / 620 / 626 / 632) | 竞态, 已修 |
| engine/NodeJsTypeScriptModuleSourceProvider.kt:168 | `activePipeWrites.toList()` | newKeySet | cancel 时写线程完成后移除 (556) | 竞态, 已修 |
| engine/NodeModuleSourceFileTransportSession.kt:481 | `activeSourceDescriptors.toList()` | newKeySet | 传输线程移除 (412 / 420 / 472) | 竞态, 已修 |
| engine/RuntimeJavaSession.kt:270 | `registrations.keys.toList()` | ConcurrentHashMap | close 时其他线程 `closeRegistration` (265) | 竞态, 已修 |
| engine/RuntimeShellBridge.kt:154 | `processes.values.toList()` | ConcurrentHashMap | `lock` 内取快照, `Entry.close` (202) 在锁外移除 | 竞态, 已修 |
| engine/RuntimeTtsBridge.kt:125 | `session.pending.values.toList()` | ConcurrentHashMap | closeSession 时回调线程 cleanup (105) 移除 | 竞态, 已修 |
| engine/RuntimeWebMediaService.kt:27 | `entries.values.toList()` | ConcurrentHashMap (进程级) | STOP 与会话 Closeable 在任意线程移除 (48 / 79 / 80) | 竞态, 已修 |

## 并发但安全 7 处

| 位置 | 调用 | 接收者 | 结论 |
| --- | --- | --- | --- |
| core/plugin/agent/ThreeStoveAgentModelBroker.kt:130 | `operations.keys.singleOrNull { ... }` | ConcurrentHashMap | 安全: 带谓词, 经迭代器 `hasNext` / `next`, 弱一致不抛 |
| engine/NodeCapabilityBrokerPolicy.kt:224 | `rows.toList()` | ConcurrentLinkedQueue | 安全: 只增, `trimRows` 只在 size > 上限 (默认 64, 唯一构造处用默认值) 时 poll; 并发裁剪最多多删 (并发线程数 - 1) 行, 远到不了 0 行, 不会在读取大小后变空 |
| engine/NodeCapabilityBrokerPolicy.kt:227 | `rows.toList()` | 同上 | 安全: 同上 |
| engine/RuntimeComposeBridge.kt:109 | `sessions.values.toList()` | ConcurrentHashMap (进程级) | 安全: 增删与快照都在主线程 |
| engine/RuntimeComposeBridge.kt:253 | `sessions.values.firstOrNull { ... }` | 同上 | 安全: 带谓词, 且只在主线程 |
| engine/RuntimeUiInterop.kt:256 | `windows.values.toList()` | ConcurrentHashMap (进程级) | 安全: 在 `main.post` 内, 增删都在主线程 |
| engine/ScriptEngineService.java:312 | `new ArrayList<>(mScriptExecutions.values())` | ConcurrentHashMap | 安全: `ArrayList(Collection)` 经 `toArray()` 复制, 且在 synchronized 内 |

## 扫描误报 7 处 (同名但不是并发集合)

| 位置 | 接收者 | 结论 |
| --- | --- | --- |
| core/plugin/speech/SpeechPluginHost.kt:439 | `peer.features` | 插件声明的列表 (与 NodeAdvancedImages 的并发集合同名) |
| engine/RuntimeWebCallbacks.kt:281 / 301 | `permission.resources` | `PermissionRequest.getResources()` 的 String 数组 |
| engine/NodeDialogs.kt:37 / 43 / 90 / 96 | `sessions.values` | `NodeDialogs` 实例的 `sessions` 是主线程上的 `linkedMapOf`, 同文件的并发表是 `NodeDialogActivity.sessions` |

## C35 已修 4 处 (44bd8b532)

| 位置 (修复后, f39ddf4da) | 写法 |
| --- | --- |
| engine/NodeBridgeProtocol.kt:1377 | `uiDispatchReceivers.forEach { it.close() }` (直接遍历) |
| engine/NodeBridgeProtocol.kt:1411 | `ArrayList(websocketSessions.values)` (`toArray()` 复制) |
| engine/NodeBridgeProtocol.kt:1805 | `overlaySessions.keys.forEach { ... }` (直接遍历) |
| engine/NodeBridgeResourceRegistry.kt:136 | `active.values.toTypedArray().sortedWith(...)` (`toArray()` 复制) |
