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
| D1 | AutoJs4 引擎只在独立插件进程中运行 | 宿主 `app/src/main/java/com/stardust/**` 是 Auto.js 包的冻结兼容副本 (354 个文件), 与 AutoJs4 的 `com.stardust.*` 同名; 宿主 Rhino 2.0.0-SNAPSHOT 与 AutoJs4 的 Rhino 1.7.7.2 不能共存. 因此不得把 AutoJs4 类加载进宿主进程 (`core/plugin/Plugin.java` 那种进程内加载路径不可用), 插件以 `android:process=":autojs4_runtime"` 的 Service 提供执行. |
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
- [ ] P0.2 编写 `docs/dev/autojs4-runtime-protocol-v1.md`: 服务 action (`org.autojs.plugin.autojs4.RUNTIME`), category, `PluginInfo` 字段 (`engine = "autojs4"`, 协议版本, `requiresHostVersion`, AutoJs4 版本名与构建号), `runScript` 请求字段 (脚本绝对路径, 工作目录, 参数 extras, 是否 UI 模式由插件自行判断), 事件字段 (`started`, `log`, `finished`, `error`, `uiStarted`), 取消语义, 单次请求上限与超时, 失败回退.
- [ ] P0.3 新建 `plugin-api/autojs4-runtime-api` 模块: AIDL `IAutoJs4RuntimePlugin` / `IAutoJs4RuntimeCallback`, 常量类, 与 `quickjs-runtime-api` 对齐的 Bundle 键定义; 单元测试覆盖键名与版本常量.
- [ ] P0.4 在 AutoJs4 仓库确认可复用的最小壳: 列出 `inrt` 中与打包应用无关的部分 (启动器, 设置页, 日志页), 以及插件需要新增的 `GlobalConsole` 转发接口与 `ScriptEngineService` 外部启动入口; 以独立提交进入 AutoJs4 (不改变 AutoJs4 APK 行为).

### P1 插件仓库

- [ ] P1.1 按 `AUTOJS6_PLUGIN_NEW_REPO_AGENTS.md` 建仓 (标识见 D12), 复制 OpenCC 插件的构建, 文档, 资源骨架; `version.properties` 的 `VERSION_NAME` 为 `1.0.0` (D13).
- [ ] P1.2 以 Git submodule 引入 AutoJs4 仓库并只包含其 `:autojs` / `:common` / `:automator` 模块 (D5); 处理 AGP / Kotlin 版本差异 (AutoJs4 用 AGP 8.2.2 / Kotlin 1.8.22, 插件平台版本以 `AutoJs6-Gradle-Platform-Versions` 为准), 记录不可升级的原因.
- [ ] P1.3 Manifest: `INFO` 服务 (category `autojs4-runtime`), `RUNTIME` 服务 (`:autojs4_runtime` 进程, `org.autojs.permission.PLUGIN` 保护), AutoJs4 的 `ScriptExecuteActivity`, 权限 Activity, 截图 Activity 与无障碍服务; 不导出 AutoJs4 原有的 `RunIntentActivity`, `ShortcutActivity`, Tasker 与 `TaskReceiver` 入口 (D15).
- [ ] P1.4 原生库: 列出 `:autojs` 带入的 `.so` (OpenCV 3.4.3, 终端, root-shell), 按 ABI 拆分发布 (`arm64-v8a`, `armeabi-v7a`, `universal`); 全部按 16 KB 页对齐重新编译或替换 (D16), 用 `page-size-16kb-roadmap.md` 的对齐检查脚本验证, 无法对齐的库列出清单并决定取舍后再进入 P2.
- [ ] P1.5 插件进程内的 `Application`: 初始化 AutoJs4 的 `AutoJs` 子类 (参考 `inrt/.../autojs/AutoJs.kt`), 覆写 `createGlobalConsole()` 把日志推送到绑定中的宿主回调; 无宿主连接时落到插件自己的日志页.
- [ ] P1.6 权限引导页: 所有文件访问 (Android 11+) / 存储权限, 悬浮窗, 无障碍服务开启, 忽略电池优化; 从宿主发起运行且缺权限时, 插件打开引导页并以 `error(kind = PERMISSION)` 事件告知宿主.
- [ ] P1.7 设置页 (D14, D17): 权限与服务分组 (无障碍服务, 悬浮窗, 所有文件访问, 忽略电池优化, Root 状态, 每项显示当前状态并可跳转系统设置), 显示启动器图标 (沿用官方插件契约), "显示 AutoJs4 控制台窗口" (默认关闭), 运行结束后是否保留插件进程.
- [ ] P1.8 README (10 种语言), changelog, 官方签名发布任务 `appendDigestToReleasedFiles`, CI; `plugin_description` 说明 "用于在 AutoJs6 中以 Auto.js 4.1.1 Alpha2 引擎运行脚本, 便于对照 AutoJs6 的运行结果".

### P2 宿主接入

- [ ] P2.1 `core/plugin/autojs4/AutoJs4RuntimePluginHost.kt`: 基于 `AidlPluginHost` 的发现, 绑定, 签名与版本校验, 与 QuickJS 客户端同构; 单元测试覆盖版本不兼容, 未启用, 未授权三种拒绝路径.
- [ ] P2.2 `engine/AutoJs4PluginScriptEngine.kt` 与 `script/AutoJs4ScriptSource` (引擎名 `autojs4`, 持有绝对路径与工作目录); 在 `AbstractAutoJs.kt` 注册; `init` / `execute` / `forceStop` / `destroy` 语义与 QuickJS 引擎一致, 输出写入 `GlobalConsole`, 结束状态经 `ScriptExecution` 回报.
- [ ] P2.3 `Scripts.kt` 增加 `runWithAutoJs4(file)` 入口, 复用 `ScriptLaunchSourceFactory` 的路径与工作目录计算, 不改变现有 `run` / `runInteractive`.
- [ ] P2.4 菜单项: `menu_script_options.xml` 增加 `action_run_with_autojs4` (位于 "运行" 相关项之后, 插件动作之前), 对 `.js`, `.auto` 文件及含 `project.json` 的项目文件夹显示 (D3); 插件可用时执行, 插件未安装时同一位置显示 `action_install_autojs4_runtime` ("安装 AutoJs4 Runtime") 并跳转插件中心对应条目, 插件已安装但被禁用或不兼容时提示启用或升级; `ExplorerBuiltInActionHandler` 分发.
- [ ] P2.5 字符串资源 10 种语言 (`text_run_with_autojs4`, 失败提示, 权限提示), 排序与 `values-en` 一致性按宿主 `AGENTS.md`.
- [ ] P2.6 任务面板与日志: 任务条目显示引擎标识 (与 Node.js / Python 任务的现有显示方式一致), 日志行前缀 `[AutoJs4]`; 音量键 / 任务面板 / 编辑器停止按钮均能结束 AutoJs4 任务.
- [ ] P2.7 changelog (feature) 落盘: `文件管理器脚本菜单增加 "使用 AutoJs4 运行" 选项, 用于以 Auto.js 4.1.1 Alpha2 引擎运行脚本并对照结果 (需要 AutoJs4 Runtime 插件)`.

### P3 执行语义

- [ ] P3.1 普通脚本: 路径, 工作目录, `engines.myEngine().execArgv` 与 AutoJs4 的 `ScriptIntents` 参数对齐; 相对 `require` 与 `files.cwd()` 在插件进程中按原路径解析.
- [ ] P3.1a 项目文件夹: 宿主读取 `project.json` 的 `main` 作为入口 (缺失或无效时提示并拒绝运行), 工作目录为项目目录; AutoJs4 侧按其 `ProjectLauncher` 语义运行, 不解析 AutoJs6 新增的 `project.json` 键.
- [ ] P3.2 UI 模式脚本: 插件进程启动 `ScriptExecuteActivity`, 宿主收到 `uiStarted` 事件后只跟踪结束; 返回键与任务面板停止均能关闭该 Activity.
- [ ] P3.3 停止: 宿主 `forceStop` -> `cancelScript(executionId)`; 插件对普通脚本调用 `ScriptEngineService` 的停止, 对 UI 脚本结束 Activity; 10 秒内未确认时宿主标记为已停止并记录警告.
- [ ] P3.4 错误: 脚本异常以 `error` 事件回传 (消息, 栈, 行号), 宿主按现有脚本异常格式打印; 插件自身的崩溃 (Binder 断开) 映射为 "AutoJs4 运行时意外退出".
- [ ] P3.5 并发: 允许多个 AutoJs4 任务并行 (AutoJs4 本身支持多引擎), 插件进程常驻直到最后一个任务结束后按设置退出.
- [ ] P3.6 单元测试: 请求 / 事件编解码, 取消状态机, 权限错误映射 (JVM 单测, 不依赖 Android 类型).

### P4 权限与设备验证

- [ ] P4.1 Android 9 (G8441), 12 (XQ-AT72), 13 (XQ-DQ72), 15 (liuqin / OPD2413) 实机: 普通脚本, UI 脚本, 浮动窗口, 无障碍操作 (`click`, `text().findOne()`), 截图 (`captureScreen`), Root 可用设备上的 `shell(cmd, true)`.
- [ ] P4.2 16 KB 页设备 (租用 SM-A566B) 安装与运行验收: 所有原生库通过对齐检查, 普通脚本, 截图与 Root 操作可用 (D16).
- [ ] P4.3 原则 A 对照集: 从 `app/src/main/assets-app/sample` 挑选 20 个以上在 Auto.js 4.x 文档范围内的示例, 分别用 AutoJs6 与 AutoJs4 运行并记录差异; 差异进入 AutoJs6 issue 或记录为 "特定情况" (附录 B).
- [ ] P4.4 插件未安装, 未启用, 未授权, 版本不兼容, 缺权限五种状态的界面行为逐项验证.

### P5 文档与发布

- [ ] P5.1 AutoJs6-Documentation 增加 "AutoJs4 运行时" 页面 (用途, 入口, 权限, 限制, 原则 A 与差异反馈方式), README 版本历史与 `api/changelog.md` 同步.
- [ ] P5.2 AutoJs4 README 的 "关联项目" 小节增加插件条目; 插件 README 的 "简介" 说明与 AutoJs4 的版本对应关系.
- [ ] P5.3 官方插件索引登记, 插件中心分类 (建议 "运行时"), 安装向导不纳入 "标准" 预置 (按需安装).
- [ ] P5.4 AutoJs4 发布新版本时的插件同步流程写入插件 `AGENTS.md`: 更新 submodule / 依赖版本 -> 提升 `VERSION_NAME` -> changelog 一条 `升级 AutoJs4 版本 a -> b` -> 发布.

### P6 可选: 对照模式

- [ ] P6.1 "使用 AutoJs4 运行并对照": 同一脚本依次在 AutoJs6 与 AutoJs4 运行, 汇总两次控制台输出的逐行差异与运行时长, 结果页可一键复制为 issue 模板 (2026-10-12 拍板: 首版不纳入, 观察使用反馈后再定).

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

待定: 无. 新的问题按 "编号 / 问题 / 候选 / 建议" 四列追加到本表下方, 拍板后移入上表并回填第 1 节.

## 附录 B. 原则 A 的已知 "特定情况" (持续补充)

- 包名: AutoJs4 为 `org.autojs.autojs4`, AutoJs6 为 `org.autojs.autojs6`; 依赖包名的脚本行为必然不同.
- Rhino 版本差异带来的语法与内置对象差异 (ES6+ 特性在 AutoJs4 不可用).
- 已在 AutoJs6 中被明确移除或替换的功能 (如 AutoJs4 的打包插件机制, `.auto` 录制回放).
- 系统限制导致的差异 (targetSdk 28 与 37 的存储, 后台与权限模型不同).

## 附录 C. 参考

- 插件通道: `engine/QuickJsPluginScriptEngine.kt`, `core/plugin/quickjs/QuickJsRuntimePluginClient.kt`, `docs/quickjs/README.md`, `docs/dev/lua-runtime-protocol-v1.md`, `docs/dev/python-runtime-protocol-v1.md`
- 插件基础设施: `core/plugin/AidlPluginHost.kt`, `core/plugin/center/PluginTrustManager.kt`, `core/plugin/PluginCapabilityResolver.kt`, `docs/dev/plugin-protocol-wire-v1.md`, `docs/dev/host-capability-contract-v1.md`
- 菜单与运行入口: `ui/explorer/ExplorerItemViewHolder.kt`, `ui/explorer/ExplorerBuiltInActionHandler.kt`, `model/script/Scripts.kt`, `script/ScriptLaunchSourceFactory.kt`
- AutoJs4: `autojs/src/main/java/com/stardust/autojs/ScriptEngineService.java`, `autojs/.../core/console/GlobalConsole.java`, `inrt/src/main/java/com/stardust/auojs/inrt/autojs/AutoJs.kt`, `app/src/main/java/com/stardust/autojs/external/ScriptIntents.java`
- 规范: `D:/idea-projects/AUTOJS6_PLUGIN_NEW_REPO_AGENTS.md`, `D:/idea-projects/AUTOJS6_CHANGELOG_RELEASE_ISSUE_AGENTS.md`, `D:/idea-projects/COMMON_AGENTS.md`
