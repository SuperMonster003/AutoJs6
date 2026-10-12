# AutoJs6 AutoJs4 运行时插件 (AutoJs4 Runtime) Roadmap

本文是 "在 AutoJs6 中直接用 AutoJs4 引擎运行脚本" 这一能力的可执行状态表.
以 2026-10-12 的本地代码快照 (AutoJs6 `master@6b3ae51de6`, `VERSION_NAME=6.8.0`; AutoJs4 `master`, `4.4.0 / 496`) 为起点,
每个条目均可独立 Check 并落地, 后续会话按阶段逐步推进.

背景: AutoJs4 (https://github.com/SuperMonster003/AutoJs4) 是 Auto.js 4.1.1 Alpha2 的克隆项目, 是 AutoJs6 的行为基准.
原则 A: 除少数特定情况外, 能在 AutoJs4 正常运行的脚本, 在 AutoJs6 上也必须正常运行且效果与结果完全一致.
为了让用户在不另外安装 AutoJs4 的前提下对比两者的运行效果, 或快速判断 "AutoJs6 异常而 AutoJs4 正常" 的问题,
AutoJs6 文件管理器中的 AutoJs4 可运行脚本 (如 `*.js`, `*.auto`) 将获得 "使用 AutoJs4 运行" 菜单项, 由一个内嵌 AutoJs4 引擎的插件进程执行.

使用方式:

1. 每次会话开始时, 先确认附录 A 的待决事项是否已由维护者拍板, 再从 "阶段总览" 选取未完成条目, 优先级按阶段顺序.
2. 条目完成后勾选 `[x]`, 并在条目后追加证据 (提交 hash / 测试类名 / 设备型号).
3. 涉及宿主字符串资源的条目同步 10 种语言的 `strings.xml`; 涉及 feature / fix / improvement 的宿主提交同步 `.changelog/lang_zh-Hans.json` 并重新生成 Markdown (其他语言按维护者的同步流程处理).
4. 插件仓库遵循 `D:/idea-projects/AUTOJS6_PLUGIN_NEW_REPO_AGENTS.md`; 宿主与插件共享的契约先写入 `docs/dev/autojs4-runtime-protocol-v1.md`, 再落代码.

---

## 1. 固定决策与硬约束

| 编号 | 决策 | 含义 |
| --- | --- | --- |
| D1 | AutoJs4 引擎只在独立插件进程中运行 | 宿主 `com/stardust/**` (`compat/stardust/` 与 `app/` 两处, 见 P7) 是 Auto.js 包的冻结兼容副本 (354 个文件), 与 AutoJs4 的 `com.stardust.*` 同名; 宿主 Rhino 2.0.0-SNAPSHOT 与 AutoJs4 的 Rhino 1.7.7.2 不能共存. 因此不得把 AutoJs4 类加载进宿主进程 (`core/plugin/Plugin.java` 那种进程内加载路径不可用), 插件以 `android:process=":autojs4_runtime"` 的 Service 提供执行. |
| D2 | 执行走宿主的运行时引擎通道, 不走资源管理器动作 | 仿照 QuickJS Runtime 的形态: 宿主注册新的 `ScriptEngine` (引擎名 `autojs4`), 通过 AIDL `runScript` / `cancelScript` + oneway 回调与插件通信. 这样脚本进入宿主 `ScriptEngineService` 的任务列表, 可被任务面板, 音量键与 `stopAll` 停止, 输出进入宿主控制台. 资源管理器动作 (`explorer-action`) 只会启动一个插件 Activity 并传 content URI, 没有任务, 控制台与停止通道, 不适合作为执行通道. |
| D3 | 入口是显式菜单项 | 在脚本的 "更多" 菜单 (`R.menu.menu_script_options`, `ExplorerItemViewHolder.showOptionsMenu()`) 增加 "使用 AutoJs4 运行", 对 `.js`, `.auto` 文件及含 `project.json` 的项目文件夹显示 (项目以 `project.json` 的 main 脚本运行, 2026-10-12 拍板, 原 Q4); 插件已安装, 已启用, 已授权且版本兼容时执行, 插件未安装时该位置显示 "安装 AutoJs4 Runtime" 引导项并跳转插件中心 (原 Q9). 不按扩展名自动路由 (`.js` 默认仍由 Rhino 执行), 不改变默认运行按钮. |
| D4 | 以真实文件路径执行 | AutoJs4 引擎自行读取文件 (`require` 相对路径, `files.cwd()`, `images.read` 等相对路径), 因此插件接收脚本的绝对路径与工作目录, 不以 content URI 或 PFD 传源码. 插件需要自己的存储权限 (Android 11+ 为所有文件访问权限), 缺权限时由插件引导授权. |
| D5 | 插件以 AutoJs4 的 `:autojs` / `:common` / `:automator` 模块为引擎, 以 `inrt` 为壳模板 | 引擎代码不复制到插件仓库, 以 Git submodule 指向 AutoJs4 仓库的固定提交 (2026-10-12 拍板, 原 Q3), CI 以 `submodules: recursive` 检出; AutoJs4 更新后插件只需更新 submodule 并提升版本重新发布. 不包含 AutoJs4 的 `:app` (IDE 界面). |
| D6 | 日志经 `createGlobalConsole()` 回传宿主 | AutoJs4 的 `app/.../autojs/AutoJs.java:112-122` 已经通过覆写 `createGlobalConsole()` 把每行日志转发给 `DevPluginService`; 插件沿用该钩子把日志回传宿主 `GlobalConsole`, 日志级别与时间戳保持原值, 并在前缀标明引擎 (`[AutoJs4]`). |
| D7 | UI 模式脚本在插件进程的 `ScriptExecuteActivity` 中运行 | `"ui";` 脚本由 AutoJs4 的 `ScriptEngineService.execute` 路由到其自带 Activity (`:150-156`), 宿主只接收日志与结束状态; 浮动窗口, 对话框, 截图权限申请等界面均属于插件进程, 需要插件自身的悬浮窗等权限. |
| D8 | 无障碍服务由插件自带 | AutoJs4 的 `com.stardust.view.accessibility.AccessibilityService` (automator 模块) 在插件内注册, 用户需在系统设置中为插件单独开启; 是否接入宿主的 `AutoJs6AccessibilityCompanionContract` 同步开关见附录 A Q5. |
| D9 | 不修改 AutoJs4 的行为 | 插件只做壳, 权限引导与通信, 不修补 AutoJs4 引擎的脚本语义; 发现的差异回到原则 A 的对照流程处理 (修 AutoJs6, 或记录为 "特定情况"). 需要在 AutoJs4 仓库做的最小改动 (如暴露日志钩子, 提取壳代码) 作为独立提交进入 AutoJs4. |
| D10 | 契约版本化 | 契约 `autojs4-runtime-protocol-v1`, 请求与事件以 Bundle 传递 (与 QuickJS 一致), 插件 `PluginInfo.capabilities` 声明 `requiresHostVersion` 与协议版本, 宿主只接受协议主版本相同的插件. |
| D11 | Roadmap 位于 `docs/dev/` | 与仓库内既有 roadmap 的位置与风格一致. |
| D13 | 版本号 (2026-10-12 拍板, 原 Q2) | 插件使用独立的语义化版本 (首版 `1.0.0`), 内嵌的 AutoJs4 版本名与构建号写入 `PluginInfo.capabilities` 与 README "简介"; AutoJs4 更新时插件至少提升次版本号, changelog 记 `升级 AutoJs4 版本 a -> b`. |
| D14 | 无障碍服务独立, 插件设置页集中管理权限开关 (2026-10-12 拍板, 原 Q5) | 插件的无障碍服务不接入宿主伴生契约, 由用户单独开启; 插件设置页提供无障碍服务, 悬浮窗, 所有文件访问, 忽略电池优化, Root 状态等重要开关与状态项, 每项可直接跳转系统设置并显示当前状态. |
| D15 | 不导出 AutoJs4 原有外部入口 (2026-10-12 拍板, 原 Q6) | `RunIntentActivity`, `ShortcutActivity`, Tasker 插件入口与 `TaskReceiver` 均不进入插件 Manifest, 插件只接受宿主经 `org.autojs.permission.PLUGIN` 保护的 Binder 调用. |
| D16 | 首版即满足 16 KB 页对齐 (2026-10-12 拍板, 原 Q7) | 随 `:autojs` / `:common` 带入的原生库 (OpenCV 3.4.3, 终端 AAR, root-shell 等) 在首版就按 `docs/dev/page-size-16kb-roadmap.md` 的方法重新编译或替换为 16 KB 对齐版本, `NATIVE_PAGE_ALIGNMENT` 声明兼容; 无法对齐的库在 P1.4 列出并决定取舍, 不以 "如实声明不支持" 交付. |
| D17 | AutoJs4 控制台窗口由插件设置决定 (2026-10-12 拍板, 原 Q8) | 日志始终回传宿主; 插件设置 "显示 AutoJs4 控制台窗口" 默认关闭, 开启后 `console.show()` 等同时在插件进程显示 AutoJs4 原有浮动窗口, 便于对照视觉效果. |
| D12 | 名称与标识 (2026-10-12 拍板, 原 Q1) | 仓库 `AutoJs6-Plugin-AutoJs4-Runtime`, `rootProject.name` `autojs6-plugin-autojs4-runtime`, 应用标题 `AutoJs4 Runtime` (不可翻译), 插件 ID `autojs4-runtime`, 包名 `io.github.supermonster003.autojs6.plugin.autojs4.runtime`, 宿主引擎标识 `autojs4`, 服务 action `org.autojs.plugin.autojs4.RUNTIME`, category `autojs4-runtime`, 专用 API 模块 `autojs4-runtime-api`. 与 Node.js / Python / Lua Runtime 同一命名族. |
| D18 | 宿主的 `com.stardust` 兼容包不由插件替代, 改为宿主内独立模块化 (2026-10-12 拍板) | 脚本直接调用 `com.stardust.xxx.someMethod()` 是宿主进程内 Rhino 的 LiveConnect 调用, 插件进程 (D1) 无法提供; 把插件 DEX 加载进宿主又会撞上 Rhino 1.7.7.2 / 2.0.0 与 `org.opencv.*` 同名类冲突, 且 AutoJs4 的同名类依赖 AutoJs4 自己的 `ScriptRuntime` / `AutoJs` 单例. 因此兼容包留在宿主, 按第 7 阶段 (P7) 先抽成独立 Gradle 库模块 (方案 1), 必要时叠加 Rhino 类名别名表以删减副本 (方案 2); 彻底移除 (方案 3) 不在本 Roadmap 范围, 需维护者另行决定. |

补充事实 (2026-10-12, 实施 P1 时核实, 修正 F10 与 D16 的前提):

- AutoJs4 的引擎模块 (`:autojs` / `:common` / `:automator`) 本身只带入 jackpal 终端库 (`libtermexec-release.aar`, 仅 `armeabi` / `mips` / `x86`); `libopencv_java3.so` 位于 AutoJs4 的 `app/src/main/jniLibs/armeabi-v7a` (以及 `inrt`), 没有 64 位版本; `opencv-3.4.3.aar` 只有 Java 类; `root-shell` / `apk-builder` AAR 无原生库. 也就是说 AutoJs4 实际是 32 位应用 (`app` 的 ABI 拆分虽列出 `arm64-v8a`, 但 OpenCV 只有 32 位), 在 64 位专用设备 (包括 16 KB 页设备) 上本就无法安装.
- 由此首版插件先镜像了 AutoJs4 的原生库清单 (`armeabi-v7a` + `x86`), 并把是否补充 64 位构建列为 Q10. 2026-10-12 维护者拍板 Q10 = B (记为 D19): `armeabi-v7a` 继续逐字节使用 AutoJs4 原版 `libopencv_java3.so`; `arm64-v8a` 与 `x86_64` 以同一 OpenCV 3.4.3 源码 (归档 SHA-256 固定) 用 NDK 28.2 重编并 16 KB 对齐, 构建脚本 `tools/opencv/build_opencv_android.py` 校验 ELF PT_LOAD 对齐与原库 2039 个 JNI 符号的完整覆盖 (未改动的 `opencv-3.4.3.aar` Java 类因此继续可用); jackpal 终端库四个 ABI 复用宿主已重编的 AAR; `x86` 与 AutoJs4 一样不含 OpenCV. 对照结果中 64 位设备的 OpenCV 为重编版本, 与 AutoJs4 的 32 位原版二进制不同, 需在差异判定时注意.

---

## 2. 范围与非目标

范围内:

- 宿主: 新引擎 `AutoJs4PluginScriptEngine` 与脚本源类型, 插件发现与可用性检查, 菜单项与字符串资源, `plugin-api/autojs4-runtime-api` 模块, 契约文档.
- 插件仓库 `AutoJs6-Plugin-AutoJs4-Runtime` (名称见附录 A Q1): Manifest (INFO / RUNTIME 服务, `org.autojs.permission.PLUGIN` 保护), 引擎进程, 权限引导页, 设置页 (显示启动器图标, 日志回传开关, 控制台窗口策略), README / changelog / CI 按插件规范.
- AutoJs4: 为插件暴露必要的最小接口 (日志钩子, 不带 IDE 的启动器), 保持其 APK 自身行为不变.
- 文档: AutoJs6-Documentation 增加 "AutoJs4 运行时" 页面, 说明用途, 限制与原则 A.

非目标 (本 Roadmap 不处理):

- 让 AutoJs4 引擎使用宿主的无障碍服务, 截图权限或 Shizuku; 两个引擎的权限相互独立.
- 在宿主中实现 AutoJs4 脚本的编辑器高亮, 补全或文档; 编辑仍由宿主编辑器承担, 只是运行时不同.
- 打包应用 (inrt) 支持 AutoJs4 引擎.
- 自动把 "AutoJs4 正常, AutoJs6 异常" 的差异判定为 AutoJs6 的 bug; 差异的判定与修复在各自 issue 中进行.

---

## 3. 现状诊断 (2026-10-12)

| 编号 | 事实 | 锚点 |
| --- | --- | --- |
| F1 | 脚本 "更多" 菜单在 `showOptionsMenu()` 中构建, 内置项由 `ExplorerBuiltInActionHandler.handle()` 处理, 插件动作从 `host.pluginActionProvider.actionsFor(...)` 追加 (order 从 1000 起); 没有 "使用 ... 运行" 类菜单项或字符串 | `ui/explorer/ExplorerItemViewHolder.kt:636-725`, `ui/explorer/ExplorerBuiltInActionHandler.kt:38-121` |
| F2 | 运行按钮经 `Scripts.runInteractive` -> `ScriptLaunchSourceFactory.createPersistedPathLaunch`; 引擎由 `ScriptEngineManager` 按 `source.getEngineName()` 选取, 注册表硬编码在 `AbstractAutoJs.kt:70-121` (rhino, nodejs, python, bun, quickjs, lua, JVM, `.auto`) | `model/script/Scripts.kt:128-158`, `engine/ScriptEngineManager.java:101-137` |
| F3 | JavaScript 的引擎由文件头指令 (`"nodejs";`, `"quickjs";`, `"bun";`, `"rhino";`) 或文件名标记 (`*.quickjs.js`, `*.bun.js`) 决定, 没有扩展名到引擎的注册表 | `script/JavaScriptSource.java:39-50, 186-219`, `script/ScriptLaunchSourceFactory.kt:636-707` |
| F4 | QuickJS 插件通道是最简形态: AIDL `IQuickJsRuntimePlugin` (`getInfo`, `getRuntimeInfo`, `runScript(Bundle, PFD, callback)`, `cancelScript`, `prewarmRuntime`), oneway `onEvent(Bundle)` 回调, 宿主 `QuickJsPluginScriptEngine` 把输出写入 `GlobalConsole`, `forceStop` 调 `cancelScript` | `engine/QuickJsPluginScriptEngine.kt`, `core/plugin/quickjs/QuickJsRuntimePluginClient.kt`, `plugin-api/quickjs-runtime-api`, `docs/quickjs/README.md` |
| F5 | Lua 通道更重 (TaggedWire, 会话, 输出配额, 能力代理), 适合作为需要能力代理时的参考 | `engine/LuaPluginScriptEngine.kt`, `core/plugin/lua/LuaRuntimePluginHost.kt`, `docs/dev/lua-runtime-protocol-v1.md` |
| F6 | 插件发现, 启用, 授权与版本兼容: `AidlPluginHost.discover/selectFrom`, `PluginEnableStore`, `PluginTrustManager` (官方签名 / 受信任开发者 / 用户授权), `PluginCapabilityResolver.requiresHostVersion`; 插件中心清单 `InstalledPluginRepository.queryDeclaredPluginServices` 列出扫描的全部 action | `core/plugin/AidlPluginHost.kt`, `core/plugin/center/*.kt`, `core/plugin/PluginCapabilityResolver.kt` |
| F7 | 宿主有伴生无障碍服务契约, 可让官方插件的无障碍服务跟随宿主开关 | `plugin-api/common-plugin-api` 的 `AutoJs6AccessibilityCompanionContract`, `docs/dev/accessibility-companion-contract-v1.md` |
| F8 | AutoJs4 引擎完全位于 `autojs` 库模块 (`com.stardust.autojs`): `ScriptEngineService`, `engine/`, `execution/ScriptExecuteActivity`, `runtime/ScriptRuntime`, `core/console/{GlobalConsole,ConsoleImpl}`; 依赖 `:common` (apk-builder, root-shell) 与 `:automator` (无障碍服务); `inrt` 模块是只依赖这三者的最小运行壳 | `D:/idea-projects/AutoJs4/settings.gradle`, `autojs/`, `inrt/src/main/java/com/stardust/auojs/inrt/` |
| F9 | AutoJs4 的外部运行入口 (`external/open/RunIntentActivity`, `ScriptIntents` 的 `path` / `script` / `loop` / `interval` / `delay` extras, `ShortcutActivity`, Tasker) 均未受权限保护, content URI 会退化为字符串源而丢失路径与工作目录 | `AutoJs4/app/src/main/java/.../external/`, `AutoJs4/app/src/main/AndroidManifest.xml` |
| F10 | AutoJs4 构建参数: `applicationId org.autojs.autojs4`, minSdk 19, targetSdk 28, compileSdk 30, AGP 8.2.2, Kotlin 1.8.22, Java 17; 原生库含 OpenCV 3.4.3 与终端 AAR, 未做 16 KB 页对齐 | `AutoJs4/project-versions.json`, `AutoJs4/app/build.gradle`, `AutoJs4/autojs/build.gradle` |
| F11 | 宿主已有 AutoJs4 相关内容只有冻结兼容包, `project.json` 键名兼容 (`@SerializedNameCompatible ... target = {"AutoJs4","AutoX"}`) 与 `DeveloperUtils` 对 `org.autojs.autojs4` 包名的引用, 没有任何兼容模式或 AutoJs4 插件设计 | `app/lint.xml`, `project/ProjectConfig.java:89`, `com/stardust/util/DeveloperUtils.java:28` |

---

## 4. 阶段总览

### P0 需求定稿与契约

- [x] P0.1 附录 A 的待决事项由维护者拍板, 结论回填到第 1 节 "固定决策". 证据: Q1-Q9 已于 2026-10-12 全部拍板 (见 D3, D5, D12-D17).
- [x] P0.2 编写 `docs/dev/autojs4-runtime-protocol-v1.md`: 服务 action (`org.autojs.plugin.autojs4.RUNTIME`), category, `PluginInfo` 字段 (`engine = "autojs4"`, 协议版本, `requiresHostVersion`, AutoJs4 版本名与构建号), `runScript` 请求字段 (脚本绝对路径, 工作目录, 参数 extras, `sourceType` 由宿主显式给出, UI 模式由插件判断并在受理结果与 `started` 事件中回报), 事件字段 (定稿为 `started` / `log` / `finished` 三种, 错误以 `finished` 的 `errorCode` 表达, 不再单设 `error` 与 `uiStarted`), 取消语义, 单次请求上限与超时, 失败回退. 证据: 宿主提交 `a83da32429`.
- [x] P0.3 新建 `plugin-api/autojs4-runtime-api` 模块: AIDL `IAutoJs4RuntimePlugin` / `IAutoJs4RuntimeCallback`, 常量类, 与 `quickjs-runtime-api` 对齐的 Bundle 键定义; 单元测试覆盖键名与版本常量. 证据: 宿主提交 `18ca4b1665`, `AutoJs4RuntimeContractTest` 4 个用例通过, `:plugin-api:autojs4-runtime-api:assembleRelease` 成功.
- [x] P0.4 在 AutoJs4 仓库确认可复用的最小壳. 结论: 无需修改 AutoJs4. `AutoJs.createGlobalConsole()` / `initScriptEngineManager()` / `createRuntime()` 可覆写, `ScriptEngineService.execute(source, listener, config)` 为公开入口, `RhinoJavaScriptEngine.getEngineOfContext()` 可把全局控制台的每一行归属到具体执行; `inrt` 中可复用的只有 `AutoJs` 子类与 `LogActivity` 的模式, 其余 (启动器, Pref, Tasker) 与打包应用绑定, 不进入插件. AutoJs4 submodule 固定在 `3fb08269198ebd49a880ab3ca2621383eaa10443` (2026-03-15, 与 GitHub `master` 一致).

### P1 插件仓库

- [x] P1.1 按 `AUTOJS6_PLUGIN_NEW_REPO_AGENTS.md` 建仓 (标识见 D12), 以 QuickJS Runtime 插件 (同为运行时插件) 的构建, 文档, 资源骨架为模板; `version.properties` 的 `VERSION_NAME` 为 `1.0.0` (D13), `TARGET_SDK_VERSION=28` 与 AutoJs4 一致 (存储, 悬浮窗与后台行为对照的前提). 证据: `D:/idea-projects/AutoJs6-Plugin-AutoJs4-Runtime` 提交 `7f23278` (骨架), 平台插件 1.9.0 (1.9.1 尚未发布到公共仓库).
- [x] P1.2 以 Git submodule `autojs4/` 引入 AutoJs4 (固定 `3fb0826`), 插件仓库内的 `engine/common`, `engine/automator`, `engine/autojs` 以 `sourceSets` 指向 submodule 源码编译 (submodule 零改动), 依赖版本钉在 `gradle/libs.versions.toml` 的 `autojs4-*` 键. 版本差异处理: 引擎模块按 API 30 编译 (新 android.jar 存根移除了 `MainThreadProxy` 使用的 `Thread.stop(Throwable)` 等方法), `android.enableJetifier=true` (Material Dialogs 0.9 / EnhancedFloaty 仍引用 Support Library), `android.nonTransitiveRClass=false`, `android.uniquePackageNames=false` (vectordrawable 1.0.x 两个构件共用 package); AGP 9.3.3 / Kotlin 2.3.20 可直接编译 AutoJs4 的 Kotlin 1.8 代码, 未动用 `OVERRIDDEN_*`. 证据: 提交 `7f23278`, `:engine:autojs:assembleDebug` 成功.
- [x] P1.3 Manifest: `<application android:process=":autojs4_runtime">` 让全部组件 (含 AutoJs4 的 `ScriptExecuteActivity`, 截图/权限 Activity, `AccessibilityServiceUsher`, `FloatyService`) 与 Binder 服务同进程, 以共享 AutoJs4 的进程内单例; `INFO` 与 `RUNTIME` 服务 (category `autojs4-runtime`, `org.autojs.permission.PLUGIN` 保护), Wake Activity, FileProvider; 不导出 AutoJs4 原有的外部入口 (D15, 这些类位于未引入的 `:app` 模块). 证据: 提交 `aaec58a`.
- [x] P1.4 原生库 (D19, 结论见第 1 节 "补充事实"): `armeabi-v7a` 的 `libopencv_java3.so` 直接取自 submodule `autojs4/app/src/main/jniLibs/armeabi-v7a`, 逐字节与 AutoJs4 相同; `arm64-v8a` 与 `x86_64` 由 `tools/opencv/build_opencv_android.py` 以官方 `opencv-3.4.3.zip` (SHA-256 固定) + NDK 28.2.13676358 重编并 16 KB 对齐 (`-DOPENCV_VCSVERSION=3.4.3` 使 `Core.getBuildInformation()` 报告 `Version control: 3.4.3`), 产物与 SHA-256 / PT_LOAD 对齐 / 2039 个 JNI 符号覆盖记录在 `native/opencv/opencv.lock.json`, `--check` 在 CI 中按 SHA-256 复核; jackpal 终端库四个 ABI 复用宿主重编的 `libs/jackpal-androidterm-libtermexec-1_0/libtermexec-release.aar`; `x86` 与 AutoJs4 一样不含 OpenCV. 每 ABI 的 OpenCV 由 `StageOpenCvJniLibsTask` 暂存并经 `androidComponents.onVariants` 的 `sources.jniLibs.addGeneratedSourceDirectory` 接入 (AGP 9 不接受 `sourceSets` 里的 Provider). 证据: `assembleDebug` 产出 `arm64-v8a` / `armeabi-v7a` / `x86_64` / `x86` / `universal` 五个 APK, `lib/arm64-v8a/libopencv_java3.so` 16,256,784 字节, `lib/x86_64/libopencv_java3.so` 17,457,936 字节, `lib/armeabi-v7a/libopencv_java3.so` 11,194,476 字节 (原版), 共享对齐插件通过; 设备验证见 P4.2.
- [x] P1.5 `App` + `AutoJs4Runtime` (`com.stardust.autojs.AutoJs` 子类, 对应 inrt 的 `AutoJs.kt`): `createGlobalConsole()` 返回 `ForwardingGlobalConsole`, 每行经 `ExecutionRegistry` 以 Rhino Context (`RhinoJavaScriptEngine.getEngineOfContext`) / `ScriptThread-<id>` 线程名 / 唯一活动执行三步归属到具体执行后经 `HostEventSink` 回传; 无宿主连接时日志仍留在 AutoJs4 全局控制台, 可由插件的 `LogActivity` (`class.console`) 查看. 证据: 提交 `aaec58a`.
- [x] P1.6 权限: 存储权限按 targetSdk 28 的传统外部存储模型 (`READ/WRITE_EXTERNAL_STORAGE`), 缺失时 `runScript` 以 `PERMISSION_DENIED` + `permission=storage` 拒绝并打开 `SettingsActivity` 提示; 悬浮窗与无障碍服务由 AutoJs4 引擎在脚本调用时按其原有逻辑引导 (`ensureAccessibilityServiceEnabled` 打开系统设置并抛 `ScriptException`). 证据: 提交 `aaec58a`.
- [x] P1.7 设置页 `SettingsActivity` (D14, D17): "权限与服务" (无障碍服务, 悬浮窗, 存储权限, 忽略电池优化, Root 状态, 均显示当前状态并可跳转/检测), "运行" ("显示 AutoJs4 控制台窗口", 默认关闭, 由 `PluginJavaScriptEngine` 在引擎初始化后以 JS 前导把 `console.show/hide` 置空实现), "关于" (版本, 发行历史页, 项目主页). 入口: 无启动器图标, 以 MAIN + INFO 意图响应宿主插件设置页的 "打开插件", 并响应 `APPLICATION_PREFERENCES`. 未实现并记录: "显示启动器图标" 选项 (插件无 MAIN/LAUNCHER 入口, 不适用) 与 "运行结束后是否保留插件进程" (首版由系统回收, 后续视反馈). 控件为 AppCompat (`SwitchCompat`), 未引入 Material 3, 以免改变 AutoJs4 的依赖集. 证据: 提交 `aaec58a`.
- [x] P1.8 README / 插件说明 / changelog 10 种语言由 `.python/generate_markdown.py` 生成 (`--check` 通过), `appendDigestToReleasedFiles` 产出 `autojs6-plugin-autojs4-runtime-v1.0.0-{arm64-v8a,armeabi-v7a,x86_64,x86,universal}-<crc32>.apk`, CI `build.yml` (submodule 递归检出, 单元测试, lint, debug APK, OpenCV 锁文件 `--check`, 模拟器 instrumentation: API 24 x86 无 OpenCV, API 30 x86_64 加载重编 OpenCV) 与 `markdown.yml`; `plugin_description` 为 "使用内嵌的 Auto.js 4.1.1 Alpha2 引擎运行脚本, 用于对照 AutoJs6 的运行结果". 启动器图标为 `.python/generate_provisional_icon.py` 渲染的临时图案, 待维护者图稿. 证据: 提交 `a595583`, `67ae5cc`; `:app:lintDebug` 21 条警告 0 错误; 单元测试 8 个通过.

### P2 宿主接入

- [x] P2.1 `core/plugin/autojs4/AutoJs4RuntimePluginClient.kt`: 基于 `AidlPluginHost` 的发现, 绑定, 签名与版本校验, 与 QuickJS 客户端同构; `runScript` 使用 `callWithDedicatedBindingLease` 在整个执行期间保持专用绑定 (否则空闲 30 s 后宿主会解绑, 长脚本的插件进程可能被回收). 三种拒绝路径 (未安装 / 未启用 / 不兼容) 的判定在 `ui/autojs4/AutoJs4RuntimePluginUi.inspect()` 中按已安装包信息完成, 对应单元测试待 P3.6 一并补 (需要 Android `PackageManager` 的纯逻辑抽取). 证据: 本次会话宿主提交 (见 git log `feat(engine): add the AutoJs4 runtime engine channel`).
- [x] P2.2 `engine/AutoJs4PluginScriptEngine.kt` 与 `script/AutoJs4ScriptSource.kt` (引擎名 `autojs4`, 持有绝对路径, 源类型与项目目录); 在 `AbstractAutoJs.kt` 中紧随 QuickJS 注册 (仅非 inrt); `init` 选择插件并读取 `getRuntimeInfo`, `execute` 受理后阻塞等待 `finished` 事件, `forceStop` 经 `cancelScript` 取消并最多等待 10 s 确认 (P3.3), 插件 Binder 死亡映射为 "AutoJs4 运行时意外退出" (P3.4); 日志以 `[AutoJs4] ` 前缀写入 `GlobalConsole`. 单元测试 `AutoJs4ScriptSourceTest` 3 个用例通过.
- [x] P2.3 `Scripts.runWithAutoJs4(context, file)`: 文件走 `AutoJs4ScriptSource.forFile`, 项目文件夹走 `forProject` (读取 `project.json` 的 `main`, 缺失时提示 `error_autojs4_project_main_missing`), 工作目录为项目目录或文件所在目录; 不改变现有 `run` / `runInteractive`.
- [x] P2.4 菜单项: `menu_script_options.xml` 与 `menu_dir_options.xml` 增加 `action_run_with_autojs4` / `action_install_autojs4_runtime`, 文件菜单对 `.js` / `.auto` 显示, 目录菜单对含 `project.json` 的项目显示 (D3); 插件已安装时显示前者, 未安装时显示后者并跳转插件中心 (`PluginCenterActivity.launch(OFFICIAL, 包名)`); 已安装但禁用或不兼容时由 `AutoJs4RuntimePluginUi` 的对话框提示启用或升级 (复用既有插件提示字符串). 分发位于 `ExplorerBuiltInActionHandler` (文件) 与 `ExplorerPageViewHolder` (目录).
- [x] P2.5 字符串 10 种语言: `text_run_with_autojs4`, `text_install_autojs4_runtime`, `error_autojs4_project_main_missing`, `error_autojs4_runtime_permission_denied`, `error_autojs4_runtime_exited_unexpectedly`; 已按 `name` 排序, `values` 与 `values-en` 一致.
- [x] P2.6 任务面板: `TaskListRecyclerView` 对 `autojs4` 引擎按脚本扩展名显示 JavaScript / Auto 图标; 日志行前缀 `[AutoJs4]`; 停止经 `ScriptEngine.forceStop()` 走 `cancelScript`, 因此任务面板, 音量键与 `stopAll` 均可结束 AutoJs4 任务 (设备实测待 P4.1).
- [x] P2.7 changelog (feature) 已写入 `.changelog/lang_zh-Hans.json` 并重新生成 Markdown (与维护者同期未提交的 changelog 修改并存于同一文件, 由维护者一并提交): `文件管理器脚本菜单增加 "使用 AutoJs4 运行" 选项, 用于以 Auto.js 4.1.1 Alpha2 引擎运行 .js 及 .auto 文件或项目文件夹并对照运行结果 (需要 AutoJs4 Runtime 插件, 入口: 文件管理器 > 脚本或项目的更多菜单)`.

### P3 执行语义

- [x] P3.1 普通脚本: 宿主传绝对路径与工作目录 (`cwd()` 为空时取文件所在目录), 宿主 `ExecutionConfig.arguments` 中的字符串 / 布尔 / 数字参数经请求 `arguments` Bundle 转为 AutoJs4 `ExecutionConfig.setArgument` (即 `engines.myEngine().execArgv`); `delay` / `interval` / `loopTimes` 保留在协议中, 首版宿主入口不传 (与 AutoJs4 "运行" 按钮一致); 相对 `require` 与 `files.cwd()` 由插件进程内的 AutoJs4 引擎按原路径解析. 设备实测待 P4.1.
- [x] P3.1a 项目文件夹: 宿主 `AutoJs4ScriptSource.forProject` 读取 `project.json` 的 `main` (缺失或无效时提示 `error_autojs4_project_main_missing` 并拒绝运行), 工作目录为项目目录并以 `projectDirectory` 传给插件; 插件按 AutoJs4 `ProjectLauncher` 语义应用 `features`, 不解析 AutoJs6 新增键.
- [x] P3.2 UI 模式脚本: 插件由 `JavaScriptSource.getExecutionMode()` 判定并在受理结果与 `started` 事件中回报 `uiMode`, AutoJs4 自行启动 `ScriptExecuteActivity`; 宿主只跟踪 `finished`. 停止: `forceStop()` 对 UI 脚本调用 `activity.finish()` (AutoJs4 `LoopBasedJavaScriptEngine.forceStop` 既有行为). 设备实测待 P4.1.
- [x] P3.3 停止: 宿主 `forceStop` -> `cancelScript(executionId)` -> 插件 `engine.forceStop()` (主线程派发); 10 s 内未收到 `finished` 时宿主按已停止处理并记录警告 (`CANCEL_CONFIRM_TIMEOUT_MILLIS`); 尚未受理的执行以 60 s TTL 的预取消记录处理.
- [x] P3.4 错误: 脚本异常以 `finished` 事件的 `errorCode = SCRIPT_EXCEPTION` + `errorMessage` + `errorStackTrace` 回传 (异常文本与 AutoJs4 控制台已先以 `log` 事件打印), 宿主以 `AutoJs4RuntimeExecutionException` 抛出并走现有异常展示; 插件进程崩溃 (Binder 死亡) 映射为 `error_autojs4_runtime_exited_unexpectedly`.
- [x] P3.5 并发: 插件允许最多 16 个执行并行 (`MAX_CONCURRENT_EXECUTIONS`, 超出返回 `BUSY`), 宿主每个任务一个引擎并各自持有专用绑定; 插件进程在最后一个绑定释放后由系统按常规回收 ("运行结束后是否保留插件进程" 设置未实现, 见 P1.7).
- [~] P3.6 单元测试: 插件侧 `AutoJs4ExecutionRequestParserTest` (请求规范化与边界, 4 用例), `AutoJs4PluginMetadataTest` (身份, ABI 清单, UTF-8 截断分块, changelog 语言回退, 4 用例), 宿主侧 `AutoJs4RuntimeContractTest` (4 用例) 与 `AutoJs4ScriptSourceTest` (3 用例) 通过; 取消状态机与权限错误映射依赖 Android `Bundle` / `Binder`, 由插件 `AutoJs4RuntimeInstrumentedTest` (发现, 绑定, PluginInfo, 运行往返, 异常, 预取消, 取消, 非法请求) 覆盖; 2026-10-12 在 XQ-DQ72 (Android 13, 插件以 armeabi-v7a 运行) 上 `connectedDebugAndroidTest` 3 个用例全部通过 (真实 AutoJs4 引擎执行 `console.log` / `console.warn` / 异常脚本 / `while (true) { sleep(50); }` 取消).

### P4 权限与设备验证

- [~] P4.1 Android 9 (G8441), 12 (XQ-AT72), 13 (XQ-DQ72), 15 (liuqin / OPD2413) 实机: 普通脚本, UI 脚本, 浮动窗口, 无障碍操作 (`click`, `text().findOne()`), 截图 (`captureScreen`), Root 可用设备上的 `shell(cmd, true)`. 2026-10-12 已在 XQ-DQ72 (Android 13, 宿主 debug 5334 + 插件 1.0.0 armeabi-v7a) 验证: (1) 插件未安装时文件菜单显示 "安装 AutoJs4 Runtime" 并跳转插件中心官方库; (2) 安装后菜单显示 "使用 AutoJs4 运行", 宿主发现, 绑定插件并受理; (3) 缺少存储权限时插件返回 `PERMISSION_DENIED` / `storage`, 宿主控制台打印 "AutoJs4 Runtime 缺少权限 \"storage\", 请在插件设置中授予", 插件自动打开设置页, 在设置页点击 "Storage access" 弹出系统权限对话框并授予成功; (4) `hello.js` 在插件进程 (`:autojs4_runtime`, 32 位) 中由 AutoJs4 引擎运行, `console.log` / `console.warn` 以原级别加 `[AutoJs4]` 前缀出现在宿主控制台, `files.cwd()` 为脚本目录, 宿主打印 "运行结束 (用时 0.318 秒)"; (5) `loop.js` 无限循环的日志每 5 s 回传, 在宿主 "任务" 面板点击停止后插件 `RhinoJavaScriptEngine.forceStop` 中断脚本线程, 宿主以 `ScriptInterruptedException` 正常结束任务 (用时 16.2 s), 任务列表清空; (6) 插件 `connectedDebugAndroidTest` 3 个用例通过 (`-PandroidTestPermissionName=...test.PLUGIN`); (7) 2026-10-12 插件以 `arm64-v8a` 安装 (`primaryCpuAbi=arm64-v8a`, 进程 `os.arch` 为 `aarch64`) 后, `opencv_test.js` (`images.read` / `pixel` / `grayscale` / `findColor` / `findColorEquals` / `findImage` / `matchTemplate` / `Core.getBuildInformation()`) 经 "使用 AutoJs4 运行" 执行, logcat `OpenCV/StaticHelper` 显示 `opencv_java3 loaded`, 构建信息为 clang 19.0.1 / NDK 28.2 / `Version control: 3.4.3`; 换装 `armeabi-v7a` 包 (AutoJs4 原版库, GCC 4.8 / NDK r10e) 重跑同一脚本, 全部结果逐行一致 (含 `images.pixel` 对灰度图抛出的 `ArrayIndexOutOfBoundsException: length=1; index=3`, 见附录 B), 证明重编库与原版行为一致且不依赖 32 位进程. (8) 2026-10-12 16:30-16:45 在 G8441 (Android 9), XQ-AT72 (Android 12), OPD2413 (Android 15, ColorOS 需在屏幕上确认 ADB 安装) 与租用的 SM-A566B (Android 16, 16 KB 页) 上以 `tools` 外的一次性脚本 (安装宿主 5350 + 插件 1.0.0 (8) arm64-v8a, 授予存储权限, 经 "Run with AutoJs4" 运行 `opencv_test.js`) 验证: 四台设备的插件进程均为 aarch64, `Library opencv_java3 loaded`, `findColor` / `findImage` / `matchTemplate` 结果与 XQ-DQ72 一致; 其中 XQ-AT72 另验证了宿主 b13c75b157 的字符串单引号修正 (`log('obj = ', obj)` 输出 `obj =  { name: 'Auto.js', url: 'www.autojs.org' }`, 与 AutoJs4 相同). (9) 2026-10-12 维护者按向导第 1 步在六台已连接实机 (OPD2413, G8441, XQ-AT72, XQ-DQ72 与两台小米 23046RP50C / 22120RN86C) 经 "使用 AutoJs4 运行" 执行 UI 模式脚本 `01-ui-mode.js` (页面显示, 按钮计数回传宿主控制台, 返回键结束任务), 全部正常; 两台小米设备为全新安装, 先经插件的存储权限引导授予权限后才能运行 (即 P4.4 的缺权限路径; 其余四台此前已由一次性脚本 `pm grant`). (10) 2026-10-12 维护者反馈向导第 2 步 (浮动窗口 `02-floaty.js`: 悬浮标签显示, 2 秒后移动, 再 4 秒后关闭, 任务结束) 在已连接实机上一切正常. 未验证: 无障碍操作, 截图, Root (向导第 3-5 步); 另注意 `connectedDebugAndroidTest` 结束后 Gradle 会卸载插件, 之后需重新安装.
- [x] P4.2 16 KB 页设备 (2026-10-12 完成): 静态证据: `opencv.lock.json` 记录两份 64 位库 `minLoadAlign=16384`, jackpal AAR 的 8 个 `.so` 由宿主按 16 KB 重编, `NATIVE_PAGE_ALIGNMENT` 声明兼容, 共享对齐插件构建期通过. 动态证据: 租用的 SM-A566B (Android 16 / API 36, `getconf PAGE_SIZE` = 16384, 仅 arm64-v8a) 上安装宿主 debug 5350 与插件 1.0.0 (8) arm64-v8a 包, 经 "Run with AutoJs4" 运行 `opencv_test.js`: 插件进程 `os.arch` 为 `aarch64`, logcat `Library opencv_java3 loaded` / `First attempt to load libs is OK`, `Core.getBuildInformation()` 为重编库 (clang 19.0.1, `Version control: 3.4.3`), `findColor` / `findImage` / `matchTemplate` 结果与 XQ-DQ72 (4 KB 页) 上 arm64 重编库及 armeabi-v7a 原版库逐行一致. 4 KB 页设备 XQ-DQ72 的证据见 P4.1 第 7 项.
- [x] P4.3 原则 A 对照集 (2026-10-12, XQ-DQ72, 宿主 5350 + 插件 1.0.0 (7) arm64-v8a; 驱动脚本 `tools/autojs4/compare_samples.py`, 从 `assets-app/sample` 选 20 个不需要交互的示例, 每个分别用运行按钮与 "使用 AutoJs4 运行" 执行, 对照宿主控制台逐行输出): 13 个完全一致 (HelloWorld, 数字, liveConnect, 格式化输出, 调整控制台大小位置, 写入/读取/读写文本文件, 文件编码转换, 保存整数等简单数据, 原子变量, 线程启动与关闭, 循环执行, 保存日志); 4 个差异属已知类别: 打印不同级别 与 获取网页 在 AutoJs4 以 `illegal character` 失败 (模板字符串, ES6 语法), 产生随机数 仅随机值不同, 多线程简单示例 仅并发日志次序不同; E4X 示例在 AutoJs4 (Rhino 1.7.7.2) 中子节点/属性访问返回空值且命名空间序列化不同, AutoJs6 输出正确 (AutoJs4 缺陷, 记入附录 B); 1 个真实差异待维护者判定: 保存数组和复杂对象 中 `log('obj = ', obj)` 的对象格式, AutoJs4 为 `{ name: 'Auto.js', url: 'www.autojs.org' }` (字符串带引号), AutoJs6 为 `{ name: Auto.js, url: www.autojs.org }`. 需要交互 (对话框, 浮动窗口, 无障碍) 或依赖 OpenCV 插件 (宿主侧) 的示例未纳入.
- [~] P4.4 五种状态 (2026-10-12 XQ-DQ72): 未安装 -> 菜单显示 "安装 AutoJs4 Runtime" 并跳转插件中心 (P4.1 第 1 项); 未启用 -> 在插件中心关闭开关后点击 "使用 AutoJs4 运行" 弹出 "插件中心" 对话框, 内容 `插件 "AutoJs4 Runtime" 未在插件中心启用`, 按钮 取消 / 插件中心, 重新开启后恢复; 缺权限 -> `PERMISSION_DENIED` / storage 提示并自动打开插件设置页 (P4.1 第 3 项; 2026-10-12 维护者在两台全新安装的小米设备上同样经此路径授予存储权限后正常运行, P4.1 第 9 项). 未授权 (未信任签名) 与版本不兼容需要特制插件包, 未验证. 附带修正: 插件中心列表曾把插件显示为 "Auto.js", 原因是 AutoJs4 引擎库的 `values-en/app_name` = "Auto.js" 覆盖了插件默认语言的 `app_name`; 插件改用独立的 `plugin_name` 资源作为 Manifest label 与 `PluginInfo.name` (插件提交 `2a5e366`), 列表现显示 "AutoJs4 Runtime".

### P5 文档与发布

- [x] P5.1 AutoJs6-Documentation 新增 `api/autojs4Runtime.md` ("AutoJs4 运行时 (AutoJs4 Runtime)", `type=misc`: 用途, 安装与启用, 入口, 运行方式, 限制, 差异反馈, 相关链接; 明确 "本页面不是脚本 API"), 登记到 `api/sidebar.md` / `api/toc.md` (AutoJs6 条目之前), `api/progress.md` 2026-10-12 条目, `api/changelog.md` 与 README 版本历史 v6.8.0 各一条 `新增`; 校验器 `generator/normalize-markdown.py` 与离线插件 `.python/normalize_offline_docs.py` 的 "裸写 AutoJs" 规则放行 `AutoJs4` 项目名. 全站生成并同步到 AutoJs6-Plugin-Offline-Docs (`auto-generate-for-autojs6.bat --verify-offline`), 两仓库各自提交.
- [x] P5.2 AutoJs4 README 的 "关联项目" 小节增加插件条目 (AutoJs4 提交 `37c7c17`, 中英文); 插件 README 的 "简介" 与 "兼容性和范围" 说明内嵌 AutoJs4 4.4.0 / commit `3fb0826` / Rhino 1.7.7.2 的对应关系. 宿主 README 的关联项目表暂未加插件行 (需同步 10 种语言 `.readme/lang_*.json`; 维护者的 changelog 文案仍在工作区, 待其提交后再加, 避免把未定稿文案带进生成的 README).
- [x] P5.3 (2026-10-12) 插件仓库 https://github.com/SuperMonster003/AutoJs6-Plugin-AutoJs4-Runtime 已创建并推送 (`master`, 10 笔提交), release `v1.0.0` (标题 `1.0.0`, 英文正文, 5 个已签名 APK `autojs6-plugin-autojs4-runtime-v1.0.0-<abi>-<crc32>.apk` + `SHA256SUMS`, 签名证书与其他官方插件相同). 官方索引 AutoJs6-Official-Plugins-Index: `official-repositories.json` 加入仓库, `release-manifests/<package>/10.json` 准入清单 (源码提交, APK SHA-256 与签名摘要), `catalog-icons.json` + `icons/<package>/` 登记 Icon Studio 生成的 432 px 日夜图标, `plugins.official.generated.json` 重新生成. 插件源码以 `plugin_id` / `plugin_engine` / `plugin_variant` / `plugin_requires_host_version` (5334) resValue 向索引声明路由与兼容性元数据. 插件中心分类由宿主按 `engine` 归入运行时类; 安装向导的 "标准" 预置未纳入 (按需安装).
- [x] P5.4 AutoJs4 发布新版本时的插件同步流程已写入插件仓库 `AGENTS.md` (本地文件, 按仓库约定不入库): 更新 submodule -> 更新 `.readme/common.json` 的 `autojs4_commit` / `autojs4_version` 与 `THIRD_PARTY_NOTICES.md` -> changelog 一条 `升级 AutoJs4 版本 a -> b` -> 提升 `VERSION_NAME` -> 发布.

### P6 可选: 对照模式

- [x] P6.1 "使用 AutoJs4 运行并对照" (2026-10-12 实现, 维护者同日拍板纳入): 文件管理器脚本与项目菜单新增 `action_compare_with_autojs4` (可见性与 "使用 AutoJs4 运行" 相同); `core/plugin/autojs4/AutoJs4Comparison` 先用 AutoJs6 引擎 (`ScriptLaunchSourceFactory.createPersistedPathLaunch`) 再用 `AutoJs4ScriptSource` 运行目标, 以 `GlobalConsole.addLogListener` 采集两次运行的控制台行, 去掉宿主起止行, `[AutoJs4] ` 前缀, AutoJs4 的 `Script Running[...]` 与宿主 `runtime-init-*` 计时行后做 LCS 逐行 diff (超过 4e6 单元退化为按位置对比), 每次运行最多 120 秒, 超时 `forceStop`; `ui/autojs4/AutoJs4ComparisonActivity` 显示摘要 (路径, 是否一致与差异行数, 两次用时, 失败/超时提示), `-`/`+` 标记的差异, "复制为 issue 模板" (脚本, 宿主与插件版本, 设备, 两次完整输出, diff). 字符串 9 条 x 11 文件. 验证: XQ-AT72 上对 本地存储/保存数组和复杂对象.js 对照得到 "Console output is identical" (AutoJs6 0.016 s, AutoJs4 0.217 s); 文档页 "对照模式" 小节已同步到在线与离线文档.

### P7 宿主 `com.stardust` 兼容包的独立模块化 (D18, 仅涉及宿主仓库)

背景: 宿主 `app/src/main/java/com/stardust/**` 共 354 个文件, 其中 31 个桥接到 `org.autojs.autojs` 的宿主实现, 36 个引用 Rhino; 宿主自身代码只有 3 处引用 `com.stardust`, 示例脚本与内置 JS 模块均未使用; 该包由 6.x 按 _[`issue #290`](http://issues.autojs6.com/290)_ "尝试恢复 com.stardust 前缀包以便提升代码兼容性" 加回, 用途是让脚本里 `com.stardust.xxx.someMethod()` 这类直接 Java 调用继续可用. 目标是让主工程更简洁并消除这部分遗留代码的编辑器警告, 同时不改变脚本可见行为.

- [x] P7.1 盘点 (2026-10-12, `docs/dev/stardust-compat-inventory.md`): 354 个文件中 25 个直接 `import org.autojs.*` (21 个用宿主 `R` 的 43 个资源, 11 处用宿主 `ui.enhancedfloaty`, 其余用 `ScriptEngineService` / `ClassFileDexer` / `Logger` / `ViewUtils` / `BuildConfig`), 99 个经 import 或同包引用间接依赖它们, 230 个只依赖 Android / Rhino / OpenCV / 第三方库. 宿主代码没有任何 `import com.stardust.*`. 结论: 兼容包反向依赖宿主, 整体迁出会形成循环依赖; 可迁出的是 230 个独立文件. 与 issue 反馈的实际调用名称对照 (方案 2 的前提) 留待 P7.2 之后.
- [x] P7.2 方案 1 (2026-10-12 完成, 结果见 `stardust-compat-inventory.md` 第 8 节): 新建宿主库模块 `:compat:stardust` (`compat/stardust/src/main/java/com/stardust/**`, 无资源, `consumer-rules.pro` 复制 keep 规则, `lint.xml` 全部忽略) 承载 179 个独立文件; 30 个桥接文件与 145 个受污染文件留在 `app/src/main/java/com/stardust/**` 并依赖新模块; 两部分都不改类名, 包名与行为. 分类以 `tools/stardust/split_stardust_compat.py` 为准 (识别内联 `org.autojs.` 引用, 宿主本地包与字符串字面量), 以 `:compat:stardust:assembleDebug` 校正. 验收: `:app:assembleAppDebug` 通过; `:app:testAppDebugUnitTest` 4262 个用例中仅 `SelectorGlobalsTest.exportedNamesMatchTheSnapshot` 失败 (选择器新增 minDepth / maxDepth / minIndexInParent / maxIndexInParent 全局函数后 `selector-global-functions.txt` 快照未更新, 属维护者选择器工作, 与本次拆分无关); XQ-DQ72 (Android 13) 上 `stardust_smoke.js` (直接调用 `com.stardust.pio.PFiles`, `util.ScreenMetrics`, `util.ClipboardUtil`, `util.UiHandler`, `autojs.annotation.ScriptInterface`, `util.Func1`, `util.MapBuilder` 等模块侧类, 以及 `autojs.core.util.Shell`, `automator.filter.IdFilter`, `automator.UiGlobalSelector`, `autojs.core.opencv.OpenCVHelper`, `autojs.AutoJs` 等留在 app 的类) 在拆分前 (5350) 与拆分后 (同版本号的本地构建) 的控制台输出逐行一致, 仅用时不同 (0.032 s 与 0.036 s).
- [~] P7.3 方案 2 (维护者 2026-10-12 确认按第 9 节建议实施, 第一批同日完成, 结果见 `stardust-compat-inventory.md` 第 10 节): 别名表 `rhino/StardustClassAliases` 由 `AndroidClassLoader.loadClass` (Rhino 的 application class loader, `Packages` / 顶层 `com` / `importClass` / `importPackage` 的统一入口) 查询, 对 Java 层不可见 (用户 DEX 以应用类加载器为父加载器). 启用规则: 兼容类上脚本能通过类名触及的成员 (静态成员, 脚本可调用的构造器, 可构造或可继承类的实例成员, 嵌套类) 必须在宿主类上有脚本可见兼容的对应项, 由 `tools/stardust/compare_alias_signatures.py` (javap 普查) 与单元测试 `StardustClassAliasesTest` (反射复核每一项, 并校验表与工具一致, 加载器对 Rhino 查找返回宿主类而对其他调用方返回副本; 6 个用例通过) 共同保证; 别名只在查找由 Rhino 自身发起时生效, `Class.forName` 与 VM 链接仍得到所请求的类. XQ-AT72 (宿主 debug 5351) 实测 `Packages` / `importClass` / `importPackage` / `new` / `instanceof` 均按预期走宿主类, 表外类名与显式 `loadClass` 不受影响 (第 10.1 节). 第一批启用 15 个类: `runtime.api` 的 AbstractConsole, AbstractShell, Dialogs, Engines, Events, Files, Images, Threads, Timers, `core.util.Shell`, `core.image.Colors` 与 `pio` 的 PFiles, PFile, PWritableTextFile, PReadableTextFile; 为此宿主做了只增不改的补充 (维护者 2026-10-12 确认): `PFiles` 8 个 `@JvmStatic` 与 `getSdcardPath()` / `getSimplifiedPath()`, `AbstractShell.isRoot()`, `Shell(Context, boolean, boolean)`, 静态 `Images.concat(img, img, dir)` (及 `ImageWrapper.scriptRuntime`), `Colors` 的 12 个颜色常量. 候选中的 Device 因维护者决定不补静态 `width` / `height` 而保留副本 (第 10.3 节). 未做: 删除 (a) 类副本. 原因: 兼容包自身的引擎栈 (兼容 `ScriptRuntime`, `ConsoleImpl`, `PFiles.open`) 仍以类型引用这些类, 删除即改写核心兼容类, 属方案 3 范围; 且 `runtime.loadDex` 的用户 DEX 按 JVM 类名链接副本. 下一批候选见第 10.3 节的 `--all` 普查.
- [x] P7.4 文案 (2026-10-12): P7.2 为内部重构, 不写 changelog; P7.3 第一批虽未删副本但改变了脚本按 `com.stardust` 名字解析到的类, 已在 zh-Hans `improvement` 记一条 (列出 11 个类, 说明其他兼容类不变), 其他语言由维护者同步. 彻底移除 (方案 3) 需维护者单独拍板后另开条目.

---

## 附录 A. 待决事项 (需维护者拍板)

已拍板 (2026-10-12):

| 编号 | 问题 | 结论 |
| --- | --- | --- |
| Q1 | 插件名称与标识 | `AutoJs6-Plugin-AutoJs4-Runtime` / `AutoJs4 Runtime` / `autojs4-runtime`, 见 D12 |
| Q3 | 引擎源码引用方式 | Git submodule 指向 AutoJs4 仓库, 见 D5 |
| Q4 | 菜单项覆盖的文件类型 | `.js`, `.auto` 及含 `project.json` 的项目文件夹, 见 D3 与 P3.1a |
| Q9 | 首版范围 | 运行 + 插件缺失时的 "安装 AutoJs4 Runtime" 引导项; 对照模式 (P6) 不纳入首版 |
| Q2 | 插件版本号表示法 | 独立语义化版本, 首版 `1.0.0`, 见 D13 |
| Q5 | 无障碍服务开关是否跟随宿主 | 独立开启, 并在插件设置页集中提供无障碍服务等重要开关, 见 D14 |
| Q6 | 是否保留 AutoJs4 原有的外部入口 | 全部不导出, 见 D15 |
| Q7 | 原生库与 16 KB 页 | 首版即重新编译以满足对齐, 见 D16 |
| Q8 | AutoJs4 自带控制台浮动窗口的处理 | 由插件设置决定, 默认只回传宿主, 见 D17 |

| Q11 | 宿主 `com.stardust` 兼容包是否由插件替代 | 不替代 (进程边界, 见 D18); 宿主侧先做独立模块化 (方案 1), 必要时叠加别名表 (方案 2), 见 P7 |
| Q10 | 插件是否补充 `arm64-v8a` 原生库 (AutoJs4 本身只有 32 位 OpenCV) | B: 以 NDK 28.2 重编 OpenCV 3.4.3 (16 KB 对齐) 并复用宿主已重编的 jackpal AAR, 增加 `arm64-v8a` 与 `x86_64` 拆分包 (2026-10-12 拍板, 记为 D19, 见第 1 节 "补充事实") |

待定: 无. 新的问题按 "编号 / 问题 / 候选 / 建议" 四列追加到本表下方, 拍板后移入上表并回填第 1 节.

## 附录 B. 原则 A 的已知 "特定情况" (持续补充)

- 包名: AutoJs4 为 `org.autojs.autojs4`, AutoJs6 为 `org.autojs.autojs6`; 依赖包名的脚本行为必然不同.
- Rhino 版本差异带来的语法与内置对象差异 (ES6+ 特性在 AutoJs4 不可用). 已确认: 模板字符串在 AutoJs4 中以 `illegal character` 解析失败 (示例 控制台/打印不同级别.js, HTTP/获取网页.js).
- 已在 AutoJs6 中被明确移除或替换的功能 (如 AutoJs4 的打包插件机制).
- `.auto` 文件 (Root 录制回放) 在两者中均由同一个 `root_automator` 可执行文件 (32 位 ARM) 回放, 不属于差异; 64 位专用设备上两者同样无法回放.
- 系统限制导致的差异 (targetSdk 28 与 37 的存储, 后台与权限模型不同).
- AutoJs4 自身的缺陷在 AutoJs6 中已修复, 属于 "AutoJs4 异常而 AutoJs6 正常", 不构成差异. 已确认的例子: (1) AutoJs4 的 `ImageWrapper.pixel()` 对单通道 (灰度) Mat 读取 `channels[3]`, 因此 `images.pixel(images.grayscale(img), x, y)` 抛出 `ArrayIndexOutOfBoundsException: length=1; index=3` (2026-10-12 在 armeabi-v7a 原版库与 arm64-v8a 重编库上结果一致); (2) Rhino 1.7.7.2 的 E4X: `order.customer.firstname` 等子节点与 `@id` 属性访问返回空值, 带前缀命名空间序列化为 `e4x_0:` 或丢失前缀 (示例 JavaScript/E4X.js, AutoJs6 的 Rhino 2.0.0-SNAPSHOT 输出正确).
- 宿主侧 OpenCV 功能 (`images.findColor` / `findImage` / `matchTemplate` / `grayscale` 等) 依赖 OpenCV 插件, 未安装时以 `OpenCvPluginLoadException` 提示安装; AutoJs4 内置 OpenCV 3.4.3. 对照时先在宿主安装 OpenCV 插件, 否则该类差异只反映插件缺失.

## 附录 C. 参考

- 插件通道: `engine/QuickJsPluginScriptEngine.kt`, `core/plugin/quickjs/QuickJsRuntimePluginClient.kt`, `docs/quickjs/README.md`, `docs/dev/lua-runtime-protocol-v1.md`, `docs/dev/python-runtime-protocol-v1.md`
- 插件基础设施: `core/plugin/AidlPluginHost.kt`, `core/plugin/center/PluginTrustManager.kt`, `core/plugin/PluginCapabilityResolver.kt`, `docs/dev/plugin-protocol-wire-v1.md`, `docs/dev/host-capability-contract-v1.md`
- 菜单与运行入口: `ui/explorer/ExplorerItemViewHolder.kt`, `ui/explorer/ExplorerBuiltInActionHandler.kt`, `model/script/Scripts.kt`, `script/ScriptLaunchSourceFactory.kt`
- AutoJs4: `autojs/src/main/java/com/stardust/autojs/ScriptEngineService.java`, `autojs/.../core/console/GlobalConsole.java`, `inrt/src/main/java/com/stardust/auojs/inrt/autojs/AutoJs.kt`, `app/src/main/java/com/stardust/autojs/external/ScriptIntents.java`
- 规范: `D:/idea-projects/AUTOJS6_PLUGIN_NEW_REPO_AGENTS.md`, `D:/idea-projects/AUTOJS6_CHANGELOG_RELEASE_ISSUE_AGENTS.md`, `D:/idea-projects/COMMON_AGENTS.md`
