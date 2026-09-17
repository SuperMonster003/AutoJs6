# AutoJs6 Java/Kotlin 脚本执行 Roadmap（v2）

最后更新：2026-08-22

> 后续状态（2026-08-26）：本文 M0–M6 记录保留为历史推进背景。Protocol 已演进至 1.6；M9-5 已支持
> 有界多文件 Java 源码包，M9-7 又增加显式非 `Main` 入口选择。旧 API 与编辑器普通启动仍默认
> `Main`，但 `Main` 已不再是 Java provider 的固定入口限制；当前规范以 `PROTOCOL.md` 为准。

本文取代旧版 Roadmap（R0–R4 阶段制），是 JVM 源文件执行的唯一推进入口。
旧的 `R0_ACCEPTANCE.md`–`R4_ACCEPTANCE.md`、`SECURITY_MODEL.md`、`INTEGRATION_PLAN.md`、
`COMPLETION_ROADMAP.md` 全部转为历史归档：可以查阅，但不再构成任何门禁、
证据分层或推进前置。`build/reports/jvm-source/*.json` 的各类 receipt 不再是真值来源。

## 1. 目标与原则

目标：用户在 AutoJs6 中可以像运行 `.js` 一样直接运行 `.java` / `.kt` 单文件；
编译执行能力由独立插件 APK 提供（存放于 `D:\idea-projects\`，热插拔），
宿主只保留路由、代理引擎与插件发现。

原则（与旧版的根本区别）：

- **先跑起来**。每个里程碑的出口是"用户可见的行为变化"，不是证据文档。
- **异常驱动修复**。发现问题就修，不预先为想象中的异常堆砌防御和测试。
- **最小验证**。受影响模块编译通过 + 一次真机手动冒烟即可推进；
  现有的 instrumentation 端到端测试保留为可选回归工具，手动触发，不挂进构建。
- **不再维护证据体系**。不写 canonical receipt，不算 source digest，
  不执行"Gradle 静默窗口"仪式（只须人为避免同时跑两个 Gradle 构建）。

## 2. 已确认的事实基础（2026-08-21 盘点）

技术链路已被真机证明可行，这是本 Roadmap 的最大依据：

- **Java 管线端到端真机通过**：`Main.java → ECJ 3.26.0 → D8 8.13.17 → DEX 校验
  → 独立 worker 进程 → InMemoryDexClassLoader → AutoJsJvmEntry.run()`，
  API 31 与 API 34 真机各通过一次完整链路（`build/reports/jvm-source/r1-device-runs/`），
  API 24/25/26 亦有 provider 内部管线冒烟通过（API 24/25 走 private DexClassLoader，
  NIO desugaring 兼容问题已修复）。
- **宿主侧代码完整**：`RemoteJvmSourceHost`（绑定/握手/PFD 快照/会话/终态）、
  `AndroidJvmSourceTransport`（Binder + 同签名校验）、`RemoteJvmScriptEngine`
  （stdout/stderr/诊断 → console）均已实现。
- **协议完整**：`plugin-api/jvm-source-api`，5 个 AIDL 接口 + 编解码 + 入口 ABI
  （`AutoJsJvmEntry` / `JvmScriptContext`），发现 action `org.autojs.plugin.JVM_SOURCE`。
- **Java provider APK 完整**：`plugins/jvm-source-java-provider`，独立
  applicationId（`org.autojs.plugin.jvmsource.java`）、零权限申请、
  `:compiler` / `:worker` 独立进程、与宿主同签名。

当前用户脚本跑不起来的原因不是缺实现，而是链路被五道人为闸门锁死：

1. `EditorScriptRunPolicy` 无条件隐藏 `.java`/`.kt` 的运行按钮（`EditorView` 四处联动拦截）。
2. `autojs.jvmSource.experimental=false` 默认关闭 → `RemoteJvmScriptEngine` 从不注册。
3. 没有 provider 选择机制：`JvmSourceProviderSelection.setExact` 无任何生产调用方。
4. 执行要求防伪 token（`runJvmSourceExplicit`），但生产代码中无人签发。
5. 脚本形态锁死：文件名必须是 `Main.java`、禁止 package/import、入口类硬编码 `Main`。

另外两个结构性事实：

- **Kotlin provider 是空壳**：6 个源文件、无编译器、服务 disabled、标注 Unavailable。
  Kotlin 侧一切从零算起（但可复用 Java provider 的 worker/DEX/校验代码）。
- **当前实现绕开了 AutoJs6 成熟的插件体系**（`AidlPluginHost`、`PluginInfo`/INFO 服务、
  Plugin Center、信任管理），自建了一套并行机制；且 provider 源码长在宿主仓库里，
  不符合"独立插件项目"的定位。`work-java` 分支落后 master 123 个提交，
  全部 jvm-source 改动尚未提交。

## 3. 部署看板

| 里程碑 | 状态 | 交付结果 |
| --- | --- | --- |
| M0 打通最短可用链路 | 成功路径与编译诊断真机短 smoke 通过 | 编辑器运行 `Main.java`，console 出结果与错误 |
| M1 放宽脚本形态 | **完成**（2026-08-22 package/import 文件列表真机 smoke 通过） | 任意文件名 + 文件列表直接运行 |
| M2 Java 插件工程独立化 | **完成**（2026-08-22 独立插件真机热插拔闭环通过） | `D:\idea-projects\AutoJs6-Plugin-Java-Runtime` 独立构建、热插拔 |
| M3 宿主侧回归主线 | **完成**（2026-08-22 master 真机 smoke 通过） | 宿主改动合入 master，接入 Plugin Center 体验 |
| M4 Kotlin 运行时插件 | **完成**（2026-08-22 三入口与编译诊断真机 smoke 通过） | `.kt` 单文件可运行 |
| M5 脚本能力面扩展 | **完成**（2026-08-22 API 31 Java/Kotlin 真机 smoke 通过） | 流式 console、可取消 sleep 与宿主 toast 可在脚本中调用 |
| M6 发布与插件索引 | **进行中**（本地发布准备完成，待首次仓库发布） | README、示例、版本与索引元数据已就绪；正式索引待 release |

M2 完成前的开发验证都在本 worktree（`work-java`）内进行；M4 可与 M3 并行。

## 4. 里程碑

### M0 — 打通最短可用链路（worktree 内）

目标：一台真机上，从编辑器运行 `Main.java`，console 显示执行结果与编译错误。
全部改动都在已有代码上"拔闸"，不新增子系统。

- [x] 解除 R0–R4 gate 脚本挂载：从 `app/build.gradle.kts` 移除
      `apply(from = "jvm-source-r*-gate.gradle.kts")` 五行，令 marker 校验不再阻碍改动
      （脚本文件本身可延后删除）。
- [x] 默认启用功能：`gradle.properties` 中 `autojs.jvmSource.experimental=true`。
- [x] 放开编辑器：`EditorScriptRunPolicy` 改为"功能启用且是 `.java` 时允许运行"；
      编辑器 tmp 保存对 `.java` 使用唯一子目录下的 `Main.java`
      （`TmpScriptFiles.createWithExactName`）。修复 `refreshDocumentRunPolicy` 在
      toolbar 未就绪时的 NPE，并在 toolbar 挂载后重刷运行策略。
- [x] 接通授权：`Scripts.runWithBroadcastSender` 与 `Scripts.executeLaunch` 对 `.java`
      走 `JvmSourceExplicitRunner.runJvmSourceExplicit`（token 机制保留，生产代码真正签发）。
- [x] 自动选择 provider：`JvmSourceProviderAutoSelection` 在未选择时自动发现并选用
      唯一可通过准入的同签名 provider，无需选择 UI。
- [x] 构建宿主 APK 与 Java provider APK（统一 sm003.jks 签名，`--offline` 构建通过，
      受影响单元测试 95/95）。
- [x] 真机冒烟：编辑器运行 `.java` 成功已于 2026-08-21 手动验证；将 `println`
      故意改为 `println2` 后，console 已按执行顺序显示 `Main.java:4:13`、源码与插入符、
      ECJ 的 undefined-method 原因、稳定错误码 `[JVM_SOURCE_COMPILE_FAILED]` 及结束日志。
      该结果仅为短 smoke，不升级为 canonical device evidence。

退出条件：上面最后一条在一台真机手动验证通过。
本阶段不做：选择 UI、多 provider 仲裁、缓存、性能、API 矩阵、Kotlin。

### M1 — 放宽脚本形态

目标：脚本长得像正常 Java 文件，且不局限于编辑器入口。

- [x] 允许任意 `.java` 文件名（`test-17.java` 等）。当前实现为"宿主接受任意文件名,
      发送给 provider 的编译单元统一规范为 `Main.java`"，因此源码内仍须声明
      `Main` 入口类；有 package 时入口变为 `<package>.Main`。文件名→类名的自由映射待
      插件独立（M2/M3）后随协议演进。
- [x] 允许 `package` 与 `import` 声明：宿主从冻结后的 UTF-8 快照计算 `Main.java` 与
      `<package>.Main`，provider 从收到的相同字节再次核对；ECJ、class/JAR ABI 分析、
      DEX 描述符、编译缓存、worker 反射加载和运行时行号定位均使用动态入口画像。
      packaged artifact 的 ECJ 编译及缓存发布→命中→物化 JVM 测试已通过。
- [x] 文件列表（Explorer）点击 `.java` 可直接运行：`FileUtils.TYPE.JAVA` 标记为
      EXECUTABLE，`Scripts.executeLaunch` 对 JVM 源统一走显式 runner；快捷方式/定时任务
      等汇聚入口自动继承。
- [x] 外部 intent（`RunIntentActivity`）支持 `.java`：file:// 经 ScriptIntents 正常路由，
      content:// 落入私有 tmp 后经显式 runner 执行。
- [x] 路径别名放开：`/sdcard` 等符号链接前缀做规范化解析而非拒绝
      （`JvmFileSource` / `ScriptLaunchSourceFactory`），仅拦截解析后路由类型变化。
- [x] provider 缺失/未启用/签名不符时给出明确、可读的失败提示。即使首次运行尚无
      selection，也会在绑定前分别映射为 `JVM_SOURCE_PROVIDER_MISSING`、
      `JVM_SOURCE_PROVIDER_DISABLED`、`JVM_SOURCE_PROVIDER_UNTRUSTED`，并给出安装、启用或
      安装匹配 AutoJs6 签名 provider 的恢复动作；入口级 JVM 测试已覆盖。
- [x] 2026-08-22 离线回归与交付构建通过：公共 API/源码布局 36/36、宿主受影响范围
      98/98、Java provider 全量 112/112，共 246/246 且无跳过；宿主 arm64 与 provider
      APK 均由同一 SM003 证书完成 v2 签名并通过 `apksigner verify`。
- [x] 真机冒烟：任意命名 `.java` 已于 2026-08-21 从编辑器、文件列表与外部 intent
      三条路径手动运行成功；2026-08-22 又将任意命名 `packaged-test.java`（`package smoke.m1`、
      普通 API import 与 static import）从文件列表直接运行，console 输出
      `M1 package/import OK: [package, import]` 并于 2.187 秒正常结束。该结果仅为短 smoke，
      不替代 API/ABI matrix 或 canonical evidence。

退出条件：一个带 package 与 import 的任意命名 java 文件，从文件列表点击运行成功。
本阶段不做：多文件编译、classpath 自定义、注解处理器。

### M2 — Java 能力插件工程独立化

目标：编译执行能力搬出宿主仓库，成为 `D:\idea-projects\` 下可独立构建、
热插拔的插件项目；宿主仅保留引擎与传输。

- [x] 新建 `D:\idea-projects\AutoJs6-Plugin-Java-Runtime` 并初始化独立 Git 仓库，采用 Lua Runtime 的
      hermetic 工程模式（独立 settings.gradle.kts、`protocol/` 目录放
      `common-plugin-api` / `protocol-wire-api` / `jvm-source-api` 三个冻结 AAR + SHA-256 锁定文件、
      `sign.properties` + `sm003.jks` 签名）；首个 lock 如实标记为 dirty worktree snapshot。
- [x] 迁移 `plugins/jvm-source-java-provider` 全部源码与测试入新仓库，独立构建出签名 APK；
      applicationId 按插件惯例改为 `io.github.supermonster003.autojs6.plugin.java.runtime`
      （宿主按 action 发现，不依赖包名）。冻结输入校验、112/112 provider 测试与 `assembleDebug`
      均在不包含宿主 sibling project 的独立 Gradle 图中离线通过。
- [x] 补充插件体系接入件：INFO 服务（`org.autojs.plugin.INFO` + `PluginInfo`）、
      `requiresHostVersion` meta-data，使 Plugin Center 能识别、启停该插件；最终 APK 保持零 Android
      权限请求，同时暴露受 signature permission 保护的 INFO 与 JVM_SOURCE 服务。
- [x] 宿主移除 `plugins/jvm-source-java-provider` 模块、源目录与 settings 条目；旧副本先移动到
      独立工程的 Git 忽略迁移备份中以便恢复。修复 persisted component 指向旧 applicationId 时
      阻塞新 provider 自动接管的问题；宿主受影响测试 101/101 与无内置 provider 的 arm64 APK
      离线组装通过。
- [x] 真机验证"独立仓库构建的插件 APK + 宿主 APK"可运行 java 脚本；
      卸载插件后运行给出明确提示，重装后恢复（热插拔闭环）。2026-08-22 的
      `packaged-test.java` smoke 先由独立插件在 1.950 秒内输出
      `M1 package/import OK: [package, import]`；卸载插件后在 0.016 秒内返回
      `[JVM_SOURCE_PROVIDER_MISSING]`；不重装宿主、重装同一插件 APK 后在 2.167 秒内
      恢复相同输出。Plugin Center 同时正确显示并启用 `Java Runtime 0.2.0-m2 (2)`。

退出条件：宿主不重装的前提下，插件 APK 安装→运行→卸载→提示→重装→恢复全流程通过。
本阶段不做：把宿主自建传输重写为 `AidlPluginHost`（现有传输已工作，M3 再评估）。

### M3 — 宿主侧回归主线

目标：宿主改动离开长期 worktree，合入 master，用户体验对齐其他语言插件。

- [x] 精简分支：R0–R4 gate 脚本、`tools/jvm-source/` 证据工具链、旧仓内 Kotlin R4
      scaffold 与 `build/reports/jvm-source/` receipt 均已移出工作树；docs 只保留本 Roadmap
      与 `PROTOCOL.md`。源码材料可从独立 Java 插件工程的 `.migration-backup/`
      恢复；5.88 GB 生成报告移至同盘 `E:\AutoJs6-Migration-Backup\m3-retired-jvm-source-20260822`。
- [x] 将宿主侧改动（script/engine/core.plugin.jvm/editor 接线）整体移植到 master；
      合并进 master 当前的 Node/Python/Lua 路由与编辑器运行策略，而不是覆盖这些后续演进。
      文件列表、编辑器、外部 file/content intent 与持久化入口均继续汇聚到显式 JVM runner。
- [x] 对齐插件体验：前台交互入口运行 `.java` 且插件缺失或被禁用时，弹窗引导到
      Plugin Center，并以独立插件包名预填搜索；后台、快捷方式与定时任务不启动 UI，
      仍返回稳定的 `JVM_SOURCE_PROVIDER_MISSING` / `JVM_SOURCE_PROVIDER_DISABLED` 错误。
      API 31 真机发现外部 `RunIntentActivity` 的 `Theme.NoDisplay` 强制要求在 onResume 前
      finish；现已改用既有 `AppTheme.Transparent` 承载缺失/禁用对话框，正常执行仍即时结束。
- [x] 评估并决定：M3 保留专用 `AndroidJvmSourceTransport`。通用 `AidlPluginHost` 已适合
      普通调用和 dedicated binding lease，但无法替代 JVM source 已验证的多 Binder 会话、
      FD 所有权、PID identity barrier、started→terminal 状态机与 compiler trust policy；
      现在迁移只会叠加两套生命周期。完整理由记录在 `PROTOCOL.md`。
- [x] 2026-08-22 master 离线验证完成：冻结公共 API 36/36、宿主受影响过滤集 140 项
      （1 项既有跳过）通过，Kotlin/Java 编译及 `assembleAppDebug` 通过；
      `jvm-source-api:lintDebug` 通过。app 全仓 lint 在分析 Kotlin Gradle script 时因
      AGP 9.3.0/Lint 内部 `findFirCompiledSymbol` 异常中止，未产生源码告警，不作为通过项。
      arm64 APK 为 `org.autojs.autojs6` 6.8.0 (5276)，SM003 v2 签名验证通过；master 构建的
      `jvm-source-api-debug.aar` 与独立插件冻结 AAR 字节及 SHA-256 完全一致。
- [x] master 真机 smoke 完成：独立 Java Runtime 禁用时，文件管理器运行按钮与 ADB
      `RunIntentActivity` 均稳定显示 Plugin Center 引导；API 31 首轮暴露的
      `Theme.NoDisplay` 生命周期异常修复并重装后复测通过。重新启用插件后，不重装宿主，
      文件列表、编辑器与外部 intent 均恢复运行，M1 package/import 行为保持正常。

退出条件：master 构建的宿主 + 独立插件 APK 跑通 M1 全部行为。

### M4 — Kotlin 运行时插件（可与 M3 并行）

目标：`.kt` 单文件与 `.java` 同等体验。这是真正的新开发。

- [x] 新建 `D:\idea-projects\AutoJs6-Plugin-Kotlin-Runtime`，工程模式同 M2；独立 Git/Gradle
      工程消费与 Java Runtime 相同的三份冻结 AAR 与 SHA-256 锁，不读取宿主 sibling project。
- [x] 编译器落地：`kotlin-compiler-embeddable 2.3.21`（本机 Gradle 缓存已有）
      + D8 `8.13.17`（与 Java provider 对齐同版本，避免拉取未缓存的 8.13.19）。
- [x] 完成 Kotlin 编译器的 Android 兼容层：对固定版本字节码做形状校验后移除
      `java.lang.management`/HotSpot `Unsafe` 假设，改用 Android 可见资源根与程序化扩展点注册，
      将 22 种 AWT/Swing/Beans 桌面类型重定位到受控 headless stub，并显式打包
      JetBrains Trove4J `1.0.20200330`。编译器类形状、`compiler.xml` 摘要、桌面类型全集与
      Trove 必需类均由构建任务 fail-closed 校验；编译器启动异常转换为不泄露私有路径的稳定诊断。
- [x] 复用 Java provider 的 worker/DEX 校验/加载执行代码（本阶段按计划先复制）；新增严格
      UTF-8 Kotlin source policy、K2 JVM compiler 驱动与 `KOTLIN_*` 结构化诊断。JVM 功能测试已
      实际跑通 `Main.kt (package/import) → Kotlin 2.3.21 → program.jar → D8 8.13.17 → DEX 校验`。
- [x] 宿主放开 `.kt`：`JvmSourceKind.KOTLIN` 走同一引擎，按 service manifest metadata 与
      capability 路由到 Kotlin 插件；Java/Kotlin selection 分开持久化、两语言互不回退。
      未声明 metadata 的既有 Java Runtime 仅作 Java 兼容候选。
- [x] 2026-08-22 离线回归与交付构建通过：Kotlin provider 全量 116/116、宿主 JVM 相关
      116/116、冻结公共 API 36/36；刷新语言 metadata 的 Java provider 112/112；Kotlin provider
      `assembleDebug`、`assembleRelease`、生产源码 `lintAnalyzeDebug` 与
      宿主 `assembleAppDebug` 均成功。Debug APK 为 13 DEX、61.79 MiB；关闭不适用于固定编译器
      图的 R8 shrink 后，release APK 为 8 DEX、43.23 MiB。两者均内含受控 API stub、入口 ABI、
      kotlin-stdlib 与 Trove4J，且与宿主同为 SM003 v2 签名。完整 `lintDebug` 仅因离线缓存缺少
      AndroidTest 间接依赖 `kotlinx-coroutines-core:1.8.0` 未运行，不是源码 lint 失败。
- [x] 明确 Kotlin Runtime 最低系统为 API 26：Kotlin 2.3.21 compiler bytecode 使用
      `MethodHandle.invoke/invokeExact`，D8 不允许其面向 API 24/25；Java Runtime 仍保持 API 24。
      这只提高 Kotlin provider APK 的安装门槛，不改变宿主或 Protocol V1 的 API 24 下限。
- [x] API 31 真机验证 `.kt → kotlinc → D8 → worker → 运行`：同一个带 package/import 的
      `m4-kotlin-test.kt` 从编辑器 Run（`[cache]`）、文件列表 Run 与外部 `RunIntentActivity`
      三条入口分别成功输出 `M4 Kotlin OK: [package, import] / en`；故意将 `println` 改为
      `println2` 后，console 显示 `Main.kt:10:9: KOTLIN_ERROR: Unresolved reference 'println2'.`
      与稳定终止码 `[JVM_SOURCE_COMPILE_FAILED]`。最终 release APK 覆盖安装后，外部 intent、
      文件列表与编辑器三入口又分别在 1.716、0.393、0.363 秒内输出同一成功结果。
      kotlin-stdlib 与 Trove4J 均随 provider 打包。

退出条件：一个 `.kt` 单文件从编辑器与文件列表运行成功；编译错误可读。
本阶段不做：`.kts`、KAPT/KSP、Compose、混编、多文件。

### M5 — 脚本能力面扩展

目标：让 Java/Kotlin 脚本"有用"。当前 `JvmScriptContext` 仅有
`app().launch()` 与取消检查，能力面按需求逐个加，每个能力 = 一个 bridge 方法 + 白名单项。

- [x] 第一批：`console.log/error`（执行中流式输出，替代结束后一次性回传）、
      `sleep`、`toast`。
- [ ] 第二批（按用户反馈定）：clipboard、device 信息、shell、文件读写（限工作目录）等。
- [x] 每新增能力在协议 capability 中声明，宿主按插件声明授予；协议文档同步到
      Protocol 1.1 / Entry API 2。

完成记录（2026-08-22）：

- 协议新增 `console.stream`、`runtime.sleep` 与 `toast.show`，并保留 `app.launch`；provider
  显式声明 `scriptCapabilities`，宿主仅授予其声明与自身支持集的交集，request 中的
  `grantedCapabilities` 与 `allowedHostCalls` 必须精确对应。toast 使用 canonical 单字段
  JSON，消息上限 8 KiB UTF-8，host bridge 继续执行 call ID、能力与方法白名单校验。
- worker stdout/stderr 仍走原有有界 PFD 管道，宿主改为增量 UTF-8 解码并按完整行实时写入
  console；同时保留完整输出、大小边界与最终 SHA-256 校验。跨 chunk 多字节字符、CRLF/LF、
  EOF 残行、非法 UTF-8 与 sink 异常均有单元测试覆盖。
- API 31 / arm64-v8a 真机 `QV710AF65F`：Java 第一行于 `15:14:28.618` 到达，第二行于
  `15:14:31.148` 到达，脚本测得 sleep `2529ms`；Kotlin 第一行于 `15:15:04.488`
  到达，第二行于 `15:15:07.007` 到达，脚本测得 sleep `2519ms`。两者第一行均在脚本
  结束前出现，证明不是结束后回放；Kotlin toast 截屏显示 `M5 Kotlin toast 你好`。
- 取消 smoke：Java 脚本进入 60 秒 `context.sleep` 后由 `engines.stopAll()` 走正常宿主停止
  路径，worker PID 消失，console 返回 `[JVM_SOURCE_CANCELLED]`，sleep 后哨兵行未出现，
  执行于 `1.653s` 结束。
- 离线回归：协议 API `40/40`、宿主 JVM 路径 `121/121`、Java provider `115/115`、
  Kotlin provider `119/119` 全绿；宿主 debug、Java `0.3.0-m5` release 与 Kotlin
  `0.2.0-m5` release APK 均构建并以同一 SM003 证书签名。

退出条件：首批三个能力在真机脚本中调用成功。

### M6 — 发布与插件索引

- [x] 两个插件仓库补 README、示例脚本、版本号规范。
- [ ] 提交 `AutoJs6-Official-Plugins-Index` 条目（packageName、engine、versionCode、
      digest 等 schemaVersion 2 字段）。
- [x] 宿主发版说明中声明支持范围：单文件 Java 8 目标（Java Runtime minApi 24）/
      Kotlin 2.3（Kotlin Runtime minApi 26），并列出实际抽样验证的 API；未验证 API 不作声明。

本地发布准备记录（2026-08-22）：

- Java/Kotlin 仓库均提供可直接运行的 `samples/m5-capabilities.*`，README 记录 SemVer
  milestone 版本、单调递增 versionCode、debug/release 离线构建命令与兼容范围。
- 两个 APK 均声明 schemaVersion 2 生成器可解析的 `plugin_engine=jvm-source`、
  provider ID、variant、`requiresHostVersion=5276`、精确 compiler service component、
  Protocol 1.1 范围及 backend/task；Gradle 门禁校验宿主版本声明不漂移。
- 官方索引生成器按本地模拟 release 成功解析两套完整元数据，自身回归 `22/22` 全绿。
  最终本地签名候选为 Java `0.3.0-m5(3)`、25,473,380 bytes、SHA-256
  `299c7ff7dd249a701cbafd2417ed10f47dcbd66433aacc2a22e481bef45646dc`；Kotlin
  `0.2.0-m5(2)`、45,339,438 bytes、SHA-256
  `2509a02fc21da2a046ec3a4b6c0e70d6cdd00785a3b7c157e7e1ce92c8e3115c`。两者
  signer SHA-256 均为 `31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213`。
- 含最终十语种发版说明的宿主 arm64 debug APK 为 42,725,194 bytes、SHA-256
  `3c92958e79aa4d744b2b9f77b16bb115c6a9c1c0c115681994aa1711b2fe4417`；覆盖安装后
  再次运行 Java sample，`0.981s` 成功结束，provider 精确选择未漂移。
- README 链接的两个 sample 原文件已直接推送 API 31 真机运行：Java 于 `1.489s` 完成并
  输出约 `547ms` sleep 间隔，Kotlin 于 `3.678s` 完成并输出约 `528ms` 间隔。
- v6.8.0 十种语言发版说明明确：Java 8/provider minApi 24、Kotlin 2.3/provider
  minApi 26；当前只声明 API 31/arm64-v8a 真机 smoke，不外推未验证 API。
- 正式索引项不能在本地预造：两个 provider 仓库当前均无首个 commit、remote、tag 或 GitHub
  release，而索引生成器以已发布 tag 的源码、release APK 大小/digest 与可选 admission
  manifest 为事实来源。完成首次 commit/push/tag/release 后，再运行
  `AutoJs6-Official-Plugins-Index/tools/generate_official_plugin_index.py` 并提交生成差异。

## 5. 减法清单（M0 立即执行，不设前置）

- 移除 `app/build.gradle.kts` 对五个 gate 脚本的 apply（14k 行 gate 逻辑退役）。
- `tools/jvm-source/`（15k 行证据工具）停止维护，M3 时删除。
- `docs/jvm-source/` 下除本文与未来协议文档外全部标注"历史归档"。
- 不再产出/校验 canonical receipt、source digest、静默窗口、evidence 分层。
- 保留 `JvmSourceR1EndToEndInstrumentationTest` 等设备测试作为**可选**回归工具，
  手动触发，不进构建链。

## 6. 保留的安全底线（零日常成本，架构已内建）

以下四条已由现有架构实现，保留它们不需要任何流程配合，因此不属于"安全边界堆砌"：

1. provider 是独立 APK/UID，编译与执行在 `:compiler`/`:worker` 独立进程，宿主进程不加载用户生成的 DEX。
2. 宿主与插件互相校验同签名（`signature` 级权限 + 双向校验），双方都用 `sm003.jks` 签名。
3. 源码经只读 PFD 快照传输，执行有 deadline 与取消，worker 单任务、可整进程终止。
4. 不下载远程 JAR/DEX/依赖，不在运行时解析 Maven；编译器版本固定随插件打包。

除此之外的旧安全条目（cache HMAC、canonical 矩阵、无穷 hostile fixture 等）
不再作为任何阶段的前置；cache 保持 disabled 直到有真实性能需求。

## 7. 工程纪律（精简版）

- **离线优先**：本机 `E:\.gradle` 已缓存 ECJ 3.26.0、R8 8.13.17、
  kotlin-compiler-embeddable 2.3.21 与 Trove4J 1.0.20200330。日常构建加 `--offline`；
  不要随手升级依赖版本（会触发外网拉取，当前网络易触发 Cloudflare 524）。
  确需新依赖时单独一次在线构建拉取，成功后立刻回到 offline。
- **签名**：宿主与全部插件统一使用 `D:\idea-projects\AutoJs6\sign.properties` +
  `D:\idea-projects\AutoJs6\app\sm003.jks`（signature 权限要求同签名，debug 构建也用它）。
- **Gradle**：不同时运行两个 Gradle 构建即可，无其他仪式。
- **设备验证**：每个里程碑一次手动真机冒烟即可推进；出问题记 issue、修复、继续。

## 8. 历史资料索引（只读）

- 旧阶段制 Roadmap、验收合同、gate 与证据工具已从主工作树退役；源码材料保存在
  `D:\idea-projects\AutoJs6-Plugin-Java-Runtime\.migration-backup\m3-retired-host-evidence`。
- 旧生成报告保存在
  `E:\AutoJs6-Migration-Backup\m3-retired-jvm-source-20260822\build-reports-jvm-source`；
  它们不再是阶段状态或发布判断的真值来源，可在确认不再需要后永久删除。
- 插件工程参照：`D:\idea-projects\AutoJs6-Plugin-Lua-Runtime`（hermetic 模式）、
  `AutoJs6-Plugin-DEX-Compiler`（convention 模式）、`docs/lua/ROADMAP.md`（里程碑风格来源)
