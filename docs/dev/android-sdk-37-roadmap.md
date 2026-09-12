# AutoJs6 Android SDK 37 (Android 17) 升级 Roadmap

本文是 AutoJs6 宿主 (含 inrt 打包模板与仓内 modules / plugin-api / test-apps) 以及同目录 42 个官方插件仓库把 `compileSdk` / `targetSdk` 从 36 升到 37 的可执行状态表.
以 2026-09-13 的本地代码快照 (宿主 `master@60b147716`, `VERSION_NAME=6.8.0`, `VERSION_BUILD=5280`) 与同日的 [探针记录](evidence/android-sdk-37-probe-20260913.md) (compileSdk 37 探针编译, API 37 模拟器 `dumpsys platform_compat` 清单) 为起点, 每个条目均可独立 Check 并落地.

使用方式:

1. 每次会话开始时, 从 "阶段总览" 选取一个或多个未完成条目, 优先级按阶段顺序; 宿主先行, 被宿主进程加载或打包进用户 APK 的插件其次, 独立进程插件再次, 纯 JVM 插件最后.
2. 条目完成后勾选 `[x]`, 并在条目后追加证据 (提交 hash / 测试类名 / 设备型号 / lint 报告路径), 证据等级见附录 D.
3. 涉及 JS 公开 API 或权限模型的条目 (如 P2.1 的本地网络权限), 完成后必须同步 `AGENTS.md` 列出的关联仓库 (文档 / d.ts / Ace LSP 声明) 与 `.changelog` (10 语言) 并重新生成 README.
4. 附录 A 的待决事项在进入对应阶段前由维护者拍板, 拍板结果回填到第 1 节.
5. 附录 B 是 API 37 模拟器上 36 项 targetSdk 门控变更的逐项评估, 是 P2 条目的直接依据; 官方文档的核对状态 (VERIFIED / UNVERIFIED) 随条目标注, 网络不稳定时以模拟器实测为准.

---

## 1. 固定决策

以下决策已由维护者确认 (D1-D4 于 2026-09-13 以选择题形式拍板, D5-D11 为由此派生的执行约定), 后续阶段不再重新讨论:

| 编号 | 决策 | 含义 |
| --- | --- | --- |
| D1 | 范围 = 宿主 + 全部 42 个官方插件仓库 | 宿主 (app 两个 flavor, 仓内 modules / plugin-api / test-apps / plugins 子项目随 `version.properties` 自动跟随) 先行; 42 个插件仓库作为 P5 逐仓推进, 每仓独立条目, 独立提交, 独立版本递增与 changelog. `AutoJs6-Gradle-Platform-Versions` 无需改动 (数据表已含 `37.0=9.1.1`), `AutoJs6-Official-Plugins-Index` 仅在插件发布时按既有流程刷新. |
| D2 | 两步走 | 宿主先把 `COMPILE_SDK_VERSION` 升到 37 (P1, `TARGET_SDK_VERSION` 保持 36) 单独提交并回归; 行为变更适配在 targetSdk 仍为 36 时借助兼容开关与临时 target 覆盖完成 (P2); 最后单独提交 `TARGET_SDK_VERSION=37` (P3). 每一步可独立回滚, 与 Archive-Manager 插件 2026-08-23 的做法一致. |
| D3 | inrt 模板 `TARGET_SDK_VERSION_INRT` 29 -> 37 | 用户打包的 APK 与宿主同步以 37 为目标, 在 P3 与宿主 targetSdk 同一提交切换. 由于 inrt 一次跨越 8 个 API 等级 (30-37), P3.3 单列 "打包应用 29 -> 37 清单", 存储引导与旧版升级保护见待决 Q4. |
| D4 | 探针编译作为 P0 基线 | 2026-09-13 已完成 compileSdk 37 探针编译 (0 错误, 仅 1 处新增弃用告警) 与 36 基线对照, 结果记入 [探针记录](evidence/android-sdk-37-probe-20260913.md); P1 不再重复 "先看能不能编译", 直接从修正弃用与 lint 开始. |
| D5 | 验证矩阵 | Android 17 证据只来自本机 AVD (`AVD_API_37` / `aj6-ps16k-37-x86_64`, `android-37.0` google_apis_ps16k x86_64 镜像); 真机 (Xiaomi 23046RP50C API 35, Sony XQ-DQ72 API 33, Redmi 22120RN86C API 33, Sony XQ-AT72 API 31, Sony G8441 API 28) 提供 targetSdk 提升后在旧系统上的回归; 16 KB 门禁 (`verifyNativePageAlignment`) 在每次 assemble 时继续生效. 无 Android 17 真机, 有则由维护者回填. |
| D6 | 落地位置 = master, 每个子条目一个提交 | 与 a11y / 16 KB Roadmap 一致; 提交时按显式文件清单暂存, 不 `git add -A` (多会话工作区). |
| D7 | 不捆绑无关依赖升级 | 本 Roadmap 只改 SDK 版本与由此强制或直接相关的代码; androidx.core 1.19 (要求 compileSdk 37) 等 "被解锁" 的升级记入附录 F, 不在本 Roadmap 实施. |
| D8 | `autojs6-platform-versions` 保持 1.8.0 | 1.8.0 与 1.8.1 的 `android-api-agp-compat.properties` 相同 (`37.0=9.1.1`), 当前解析出的 AGP 9.4.0 / Gradle 9.7.1 / Kotlin 2.4.0 / KSP 2.3.12 全部满足 API 37; 不因本次升级改动 `settings.gradle.kts` 的插件版本. |
| D9 | changelog 写法 | 宿主与每个插件在各自 `dependency` 分类写一条 "升级 Android SDK 版本 36 -> 37" (英文 "Upgraded Android SDK version 36 -> 37", 其余语言按各仓库既有译法), 在 P1 (compileSdk) 落地时即写入, P3 不重复; 行为变更适配按性质写入 `fix` / `improvement`. |
| D10 | `app/src/main/java/com/stardust/**` 兼容包保持冻结 | 探针编译显示 android.jar 37 未在该包内引发错误; 若后续 lint / 构建暴露必须修改的项, 仅允许 35 -> 36 先例 (`2ab13f3c7` 注释掉 `MainThreadProxy` 的 `Thread.stop` 系列) 那种 "最小编译修正", 不做其它改动. |
| D11 | 插件仓库的节奏 | 纯 JVM 且无行为暴露的插件 (附录 E "暴露" 列为空) 可在一次发布内同时完成 compileSdk 与 targetSdk; 存在本地网络 / 动态加载 / 后台音频 / 屏幕捕获暴露的插件与宿主一样两步走. 各仓库脏工作区 (附录 E) 先由维护者处理或按会话惯例分开提交, 本 Roadmap 的提交不得混入. |

由 D1-D3 派生的硬约束:

- SDK 版本只在 `version.properties` 一处定义 (`COMPILE_SDK_VERSION` / `TARGET_SDK_VERSION` / `TARGET_SDK_VERSION_INRT` / `MIN_SDK_VERSION`), 经 `build-logic/convention/src/main/kotlin/org/autojs/build/Versions.kt` 暴露为 `versions.sdkVersion*`, 47 个构建脚本 (app, modules, plugin-api, test-apps, plugins/apk-builder-template) 全部经此读取; 任何条目都不得在子项目脚本中写死数字.
- `MIN_SDK_VERSION=24` 不变; P2 中为 API 37 新增的调用一律以 `Build.VERSION.SDK_INT >= 37` 分支或 `*Compat` 包装, 不抬高 minSdk.
- P2 的代码改动必须在 targetSdk 36 下也能正确运行 (改动先于切换合入), 因此新权限申请等逻辑以 "目标版本 >= 37 且系统 >= 37" 作为条件, 避免在切换前打扰用户.

---

## 2. 范围与非目标

范围内:

- 宿主: `version.properties`, `app/build.gradle.kts` (lint / flavor 段), `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/network_security_config.xml`, 第 3.3 节列出的代码锚点, `.changelog/**` + README, `docs/dev/**` (本文与证据), `.github/workflows/{android,release-runtime-kit}.yml` (仅在 SDK 自动下载失败时改动).
- 打包链路: inrt flavor (`TARGET_SDK_VERSION_INRT`), `generateRuntimeKit` 产物, `plugins/apk-builder-template`, `AutoJs6-Plugin-APK-Builder-Template` (模板与测试夹具中的 targetSdk 期望值).
- 42 个官方插件仓库 (附录 E), 每仓的 `version.properties`, changelog, README, 以及暴露列对应的适配代码.
- 关联项目 (仅当 JS 公开 API 或权限模型变化时): `AutoJs6-Documentation`, `AutoJs6-TypeScript-Declarations`, `AutoJs6-Plugin-Ace-Editor` (LSP 声明), `AutoJs6-Plugin-Offline-Docs`.

非目标:

- Android 17 QPR1 / QPR2 的次级 SDK (37.1 / 37.2, 本机已装 `android-36.1` 平台但未装 37.1) 与 `compileSdk { minorApiLevel }` 语法; 待 androidx (activity 1.14 / Compose) 真正要求 37.1 时另立条目 (附录 F).
- `usesCleartextTraffic` 迁移到 network security config (Android 17 未强制, 附录 F).
- Google Play 政策本身 (宿主经 GitHub 分发; Play 对 targetSdk 37 的要求预计 2027-08, 见附录 C 参考); 本 Roadmap 以平台行为正确为验收标准.
- `AutoJs6-Gradle-Platform-Versions` 的数据刷新 (37.1 出现后再做), `AutoJs6-Official-Plugins-Index` 的字段变更 (无需要).
- 归档 / 验证 / 第三方目录 (`.a6-*`, `AutoJs6-m9-*`, `AutoJs4`, `AutoX*`, `Rhino-For-AutoJs6`, `TempApplication`, `Auto.js-SDK-Plugins-Garage` 等).

---

## 3. 现状基线 (2026-09-13)

### 3.1 版本与工具链事实

| 事实 | 含义 |
| --- | --- |
| `version.properties`: `COMPILE_SDK_VERSION=36`, `TARGET_SDK_VERSION=36`, `TARGET_SDK_VERSION_INRT=29`, `MIN_SDK_VERSION=24` | 三个值分别对应 P1 / P3 / P3 (D3) 的改动点; `Versions.kt:37-40` 以 `requireInt` 读取, 因此写 `37` 而非 `37.0` |
| 工具链 (由 `autojs6-platform-versions` 1.8.0 从 `version.properties` 与其数据表解析): Gradle 9.7.1, AGP 9.4.0, Kotlin 2.4.0, KSP 2.3.12; `android-api-agp-compat.properties` 中 `37.0=9.1.1` | API 37 的最低 AGP 是 9.1.1 (官方 AGP 9.1 发行说明, VERIFIED), 当前 9.4.0 满足, 无需 `android.suppressUnsupportedCompileSdk`; AGP 9.4 要求 Gradle >= 9.6.0, 已满足 |
| 本机 SDK: `platforms/android-37.0` (Platform.Version=17, rev 2, ExtensionLevel 22, 正式版), `build-tools/37.0.0`; AVD `AVD_API_37` 与 `aj6-ps16k-37-x86_64` 均为 `android-37.0;google_apis_ps16k;x86_64` | 平台目录名是 `android-37.0` 而非 `android-37`, AGP 9.4 对 `compileSdk = 37` 已正确解析 (Archive-Manager 先例: 2026-09-12 release APK `compileSdkVersion='37' compileSdkVersionCodename='17'`) |
| Android 17 正式版 2026-06-16 发布 (VERIFIED); Google 博客称 Play 将于 2027-08 要求新应用与更新以 API 37 为目标 (VERIFIED 引文, 具体日期 UNVERIFIED) | 本次升级不受外部截止期驱动, 但插件舰队与宿主同步可避免 2027 年集中返工 |
| 探针编译 (compileSdk 37, target 36): 全仓 0 错误, 仅新增 1 处弃用告警 (`ui/edit/keyboard/SymbolsSettingsActivity.kt:1110` `InputMethodManager.SHOW_IMPLICIT`); 36 基线对照无其它差异; javac 告警 0 | P1 的代码改动预期极小; API 37 移除的 `android.hardware.fingerprint` 包本仓库未引用 |
| `build-logic/ksp-version-codes-processor/src/main/resources/version-codes.csv` 已含 `CINNAMON_BUN,Android 17,Cinnamon Bun,17,37,"June 16, 2026"` (`4bdb3bb68`, 2026-03-14); 文档仓库 `api/util.md` 已同步 | `util.versionCodes` / `SdkVersionUtils.sdkIntToString(37)` 已能解析 "17", 无需改动 |
| 上一次升级提交 `2ab13f3c7` (2025-09-26, 35 -> 36): 24 个文件, 主要是 android.jar 删除 / 弃用 API 的编译修正, changelog `dependency` "升级 Android SDK 版本 35 -> 36" | D9 的写法来源; 本次编译修正量远小于上次 |
| CI (`.github/workflows/android.yml`): JDK 21, 未显式安装 SDK 平台, 依赖 AGP 自动下载; `run-gradle-ci.sh` 跑单元测试子集, `assembleApkBuilderTemplateDistribution`, 16 KB 校验 | P1.6 需确认 runner 能取到 `platforms;android-37.0` (预期由 AGP `sdkDownload` 自动完成), 否则补 `sdkmanager` 步骤 |
| Manifest (`app/src/main/AndroidManifest.xml`): `largeHeap` (291), `requestLegacyExternalStorage` (293), `usesCleartextTraffic` (297), `tools:targetApi="tiramisu"`, 无 `screenOrientation` / `resizeableActivity` / `enableOnBackInvokedCallback` / `<property>` 大屏豁免; 前台服务类型 `specialUse` x2 (593, 603, 带 `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`), `microphone` (627), `mediaProjection` (948); 权限含 `INTERNET`, `SYSTEM_ALERT_WINDOW`, `MANAGE_EXTERNAL_STORAGE`, `READ_SMS` / `RECEIVE_SMS`, `BLUETOOTH_*`, `QUERY_ALL_PACKAGES` 等 74 项 | 未使用 Android 16 的大屏豁免属性, 因此 `DISABLE_OPT_OUT_UNIVERSAL_RESIZABLE_BY_DEFAULT` 无增量影响; 前台服务类型在 Android 17 无新增要求 (VERIFIED) |
| `network_security_config.xml`: `base-config` 仅系统信任锚, `githubusercontent.com` 域禁明文并附加 `@raw/isrg_root_x1`; 无 `certificateTransparency` / `domainEncryption` 元素 | P2.6 的改动点 (CT 默认开启, ECH 默认开启) |

### 3.2 Android 17 对宿主的影响矩阵

来源: 附录 B (模拟器 36 项门控变更) + 官方 "apps targeting 17" / "all apps" 页面 (核对状态随条目). 按影响排序, 每行对应一个 P2 条目.

| 编号 | 变更 | 生效条件 | 宿主暴露点 | 条目 |
| --- | --- | --- | --- | --- |
| I1 | 本地网络权限 `android.permission.ACCESS_LOCAL_NETWORK` (VERIFIED): targetSdk >= 37 默认阻断到局域网地址的出站 TCP / UDP, 入站 TCP accept, mDNS `.local` 解析; targetSdk <= 36 随 `INTERNET` 隐式授予; 回环地址不受限 | 权限系统, 非兼容开关 (`RESTRICT_LOCAL_NETWORK` 仍为 disabled, 仅测试用) | VS Code 扩展连接两种模式 (`pluginclient/JsonSocketServer.kt:24` `ServerSocket`, `pluginclient/JsonSocketClient.kt:64-65` 连接电脑); 脚本 `http.*` / `web.*` / `$web.newWebSocket` (`runtime/api/augment/web/Web.kt`, `core/web/WebSocket.kt`); MQTT (`org.eclipse.paho.android.service.MqttService`); Node 桥 (`engine/NodeBridgeProtocol.kt` 的 `newWebSocket`); 用户脚本自建 socket | P2.1 |
| I2 | 后台音频强化 (VERIFIED): 全体应用需要可见 Activity 或非 `SHORT_SERVICE` 前台服务才能播放 / 申请焦点 / 调音量; targetSdk 37 额外要求该前台服务具备 while-in-use 能力 (在可见或用户交互时启动), 否则静默失败 | 全体 + target 37 加严 | `runtime/api/Media.java`, `runtime/api/Device.java` (音量), `runtime/api/tts/*.kt` (TTS), Node 桥音频; 定时任务 / 开机自启 / 广播触发的脚本在 `AppForegroundService` (`external/foreground/AppForegroundService.kt`) 下运行 | P2.2 |
| I3 | 动态加载的原生库必须只读 (`THROW_ERROR_FOR_WRITABLE_DCL`, VERIFIED): `System.load()` 可写文件抛 `UnsatisfiedLinkError` | target 37 | `core/plugin/opencv/OpenCvPluginHost.kt:227-229` 与 `core/plugin/imagequant/ImageQuantPluginHost.kt:229-231` 在 staging 后直接 `System.load(staged.file)`; DEX 路径 `rhino/AndroidClassLoader.kt:93-122` 已复制到缓存并 `setReadOnly()` (API 34 先例), `core/plugin/editor/AceEditorPluginHost.kt:321` 以插件 APK 路径构造 `PathClassLoader` (系统只读路径) | P2.3 |
| I4 | 无锁 `MessageQueue` (`USE_NEW_MESSAGEQUEUE`, VERIFIED) 与 `LOOPER_CLEARS_THREAD_INTERRUPTED`, `OVERRIDDEN_THREAD_START_METHOD` (名称 VERIFIED, 语义 UNVERIFIED): 公开 API 不变, 反射 `mMessages` 恒为 null; Looper 派发前清除中断标志; 覆写 `Thread.start()` 的子类行为变化 | target 37 | 脚本引擎的 Looper 体系: `core/looper/Loopers.kt:20-25` (公开 `IdleHandler`), `core/looper/MainThreadProxy.java:68,86`, `core/looper/TimerThread.kt:74-76` (覆写 `interrupt()`), `exit()` / `threads.*` / `sleep()` 的中断语义 (127 处 `interrupt` / `isInterrupted`); 8 个含 `start()` 方法的文件需筛出 Thread 子类; 用户脚本对 Looper 内部的反射 | P2.4 |
| I5 | 后台活动启动 (VERIFIED): `IntentSender.sendIntent()` 需要发送方显式 opt-in (`COVER_INTENT_SENDER`); `ASM_RESTRICTIONS` 为 opt-in 默认关闭; `SYSTEM_ALERT_WINDOW` 仍是 BAL 豁免 | target 37 | `ui/main/scripts/AndroidPackageSessionInstaller.kt:173,176` (`MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS` / `ALLOWED`), `external/shortcut/ShortcutManager.java:46` 与 `ui/settings/LauncherShortcutsPreference.kt:35-68`, `ui/shortcut/ShortcutCreateActivity.java:161-163` (`requestPinShortcut(IntentSender)`), `ui/main/drawer/DrawerFragment.kt:160` (`StartIntentSenderForResult`); 脚本 `app.launch` / `app.startActivity` 从后台启动 (依赖悬浮窗豁免) | P2.5 |
| I6 | Certificate Transparency 默认开启 (`DEFAULT_ENABLE_CERTIFICATE_TRANSPARENCY`, VERIFIED), ECH 默认开启 (`ENABLE_DEFAULT_ENCRYPTED_CLIENT_HELLO`, VERIFIED); NSC 可按域关闭 (`<certificateTransparency enabled="false"/>`, `<domainEncryption>`) | target 37 | `network_security_config.xml`; 脚本访问私有 CA / 自签名服务器与企业 TLS 代理; 用户安装 CA 是否豁免 CT 为 UNVERIFIED | P2.6 |
| I7 | `PARCEL_HARDENING` (名称 VERIFIED, 语义 UNVERIFIED): Parcel 读写更严格 | target 37 | 插件 Binder 协议 (TaggedWire `byte[]`, `ParcelFileDescriptor`), `core/plugin/center/InstalledPluginRepository.kt:351` (`BundleCompat.getSerializable`), `engine/NodeModuleSourceFileTransportSession.kt`, Tasker / 快捷方式 extras | P2.7 |
| I8 | 存储: `EXCLUDE_UNRELIABLE_STORAGE_VOLUMES`, `REQUIRE_PERMISSIONS_FOR_DIRECTORY_QUERIES`, `TRASH_BEHAVIOR_CHANGE` (名称 VERIFIED, 语义 UNVERIFIED); `MediaStore` / `DocumentsContract` API 面有变化 | target 37 | 文件管理器与 `files.*` / `media.*`: `model/script/PathChecker.java:56`, `permission/AllFilesAccessPermission.kt`, MediaStore 查询点 (代码内未直接引用 `DATA` 列); `MANAGE_EXTERNAL_STORAGE` 语义不变 | P2.8 |
| I9 | 小组件 RemoteViews 内存上限 (VERIFIED, 超限抛 `IllegalArgumentException`), 通知自定义视图尺寸限制 (`CHECK_SIZE_OF_INFLATED_CUSTOM_VIEWS`, 细节 UNVERIFIED) | target 37 | `external/widget/ScriptWidget.java:49`; `engine/NodeBridgeProviderRegistry.kt` 的 RemoteViews; 通知构建点 (`util/NotificationUtils.kt`) | P2.9 |
| I10 | 短信 OTP 保护 (VERIFIED): 含 OTP 的短信对非默认短信应用延迟 3 小时 (`FILTER_GENERIC_OTP`, target 37; WebOTP 格式对全体); 通知监听器的 OTP 脱敏延续 Android 15 | target 37 / 全体 | 宿主代码不直接读短信, 但声明 `READ_SMS` / `RECEIVE_SMS` 供脚本 (`content://sms`, 广播) 使用; `core/notification/NotificationListenerService.kt:46` (`events.observeNotification`) | P2.10 |
| I11 | WebView UA 缩减 (`ENABLE_USER_AGENT_REDUCTION`, VERIFIED) 与 File System Access API (`ENABLE_FILE_SYSTEM_ACCESS`, UNVERIFIED) | target 37 | `core/ui/attribute/WebViewAttributes.kt:36` (`userAgentString` 属性), 文档 / 预览 WebView (34 个文件) | P2.11 |
| I12 | 大屏: 方向 / 可调整大小限制在 sw >= 600dp 被忽略且不可豁免 (`DISABLE_OPT_OUT_UNIVERSAL_RESIZABLE_BY_DEFAULT`, VERIFIED); 全体: 旋转后不再自动恢复输入法, 更少的 Activity 重建 (键盘 / 导航 / 触摸屏 / 色彩模式 / 桌面 UI 模式配置变化改为 `onConfigurationChanged`) | target 37 / 全体 | 宿主未使用 Android 16 豁免, 增量为零; `MainActivity` / `EditActivity` 的 `configChanges` 已含 `orientation|screenSize`; 脚本 UI 模式 `activity.setRequestedOrientation` 在平板上继续无效 (与 36 相同) | P2.12 |
| I13 | 应用内存上限 (全体, VERIFIED): 按 RAM 的每应用限制, 被杀时 `ApplicationExitInfo` 描述含 `MemoryLimiter:AnonSwap` | 全体 | `largeHeap` 应用 + 脚本引擎 + 进程内 OpenCV; 崩溃报告 (`ui/error/CrashReportActivity`) 对退出原因的展示 | P2.13 |
| I14 | Android Advanced Protection Mode 下, 未声明 `isAccessibilityTool="true"` 的无障碍服务被系统撤销且不可启用 (二手来源, UNVERIFIED) | 与 targetSdk 无关 | `res/xml/accessibility_service_config.xml` 未声明该属性 (AutoJs6 是自动化工具, 不应伪装为残障辅助工具, 见待决 Q5); `core/accessibility/control/A11yServiceController` 的 "启用失败" 诊断 | P2.14 |
| I15 | 低影响项: Bluetooth 绑定 API 需特权 (`BONDING_APIS_REQUIRE_PRIVILEGED_PERMISSION`), RFCOMM `read()` 断开返回 -1 (`MAKE_SOCKET_READ_BEHAVIOR_CONSISTENT`, 仅 `BluetoothSocket`), 联系人 `Data` 视图移除账户列与严格 SQL 检查 (`RESTRICT_DATA_URI_COLUMNS`, `ENFORCE_STRICT_SQL_CHECKS`, 仅 ContactsContract), 密钥库每应用 5 万把上限, RenderScript ScriptC 停用 (本仓库无 RenderScript), 镜像虚拟显示权限检查 (`CHECK_ADD_MIRROR_DISPLAY_PERMISSION`, 宿主经 MediaProjection 创建 `VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR`, `core/image/capture/ScreenCapturer.java:284-289`), `ContentCaptureManager.setContentCaptureEnabled(false)` 失效, 物理键盘密码显示拆分 | target 37 | 逐项验证即可, 预期无代码改动 | P2.15 |

Android 17 明确未变的部分 (VERIFIED, 由官方页面缺席确认): 前台服务类型与超时, `SYSTEM_ALERT_WINDOW` / `TYPE_APPLICATION_OVERLAY`, MediaProjection 行为, JobScheduler / AlarmManager 限制, 预测性返回 (36 已默认), 边到边 (35 强制, 36 移除豁免), `android.accessibilityservice` 包无 API 变化, 最低可安装 targetSdk (仍为 24, 二手来源 UNVERIFIED).

### 3.3 宿主代码锚点汇总

| 主题 | 锚点 |
| --- | --- |
| 版本定义与消费 | `version.properties`; `build-logic/convention/src/main/kotlin/org/autojs/build/Versions.kt:37-40`; `app/build.gradle.kts:876` (compileSdk), `:881-882` (min / target), `:940-942` (inrt flavor `targetSdk = versions.sdkVersionTargetInrt`), `:1090-1092` (`lint { abortOnError = false }`), `:1455-1466` (`lintVital*` 任务); `plugins/apk-builder-template/build.gradle.kts:44-49` |
| 本地网络 (I1) | `pluginclient/{DevPluginService.kt,JsonSocketServer.kt,JsonSocketClient.kt,JsonSocket.java}`; `runtime/api/augment/web/Web.kt`; `core/web/WebSocket.kt`; `engine/NodeBridgeProtocol.kt`; manifest `MqttService`; 权限助手模式 `permission/{PostNotificationsPermission,DisplayOverOtherAppsPermission,AllFilesAccessPermission}.kt` |
| 音频 (I2) | `runtime/api/Media.java`, `runtime/api/Device.java`, `runtime/api/tts/{AndroidTtsBackend,TtsCommandScheduler,TtsExecution,TtsService}.kt`, `external/foreground/AppForegroundService.kt`, `external/foreground/PythonLongRunningForegroundService.kt:155-161`, `timing/**` (定时任务触发路径), `inrt/InrtBootCompletedReceiver.kt:20` |
| 动态加载 (I3) | `core/plugin/opencv/OpenCvPluginHost.kt:219-229`, `core/plugin/imagequant/ImageQuantPluginHost.kt:226-231`, `rhino/AndroidClassLoader.kt:93-122`, `core/plugin/editor/AceEditorPluginHost.kt:316-326` |
| Looper / 线程 (I4) | `core/looper/{Loopers.kt,MainThreadProxy.java,TimerThread.kt}`, `engine/LoopBasedJavaScriptEngine*`, `inrt/autojs/LoopBasedJavaScriptEngineWithDecryption.kt`; 含 `start()` 的文件: `core/record/{GlobalActionRecorder,Recorder}.java`, `engine/LuaEngineRuntimeBackend.kt`, `runtime/api/Media.java`, `service/{AccessibilityServiceItemHelper.kt,ForegroundService.kt}`, `inrt/theme/preference/ForegroundServiceSwitchPreference.kt` |
| BAL (I5) | `ui/main/scripts/AndroidPackageSessionInstaller.kt:119-180`, `external/shortcut/ShortcutManager.java:46`, `ui/settings/LauncherShortcutsPreference.kt:35-68`, `ui/shortcut/ShortcutCreateActivity.java:161-163`, `ui/main/drawer/DrawerFragment.kt:160`; 13 处 `PendingIntent.get*`, 41 处 `FLAG_ACTIVITY_NEW_TASK` |
| 网络安全 (I6) | `app/src/main/res/xml/network_security_config.xml`, manifest `:297` `usesCleartextTraffic` |
| IPC (I7) | `core/plugin/**/*Host.kt` 的 Binder 协议, `plugin-api/protocol-wire-api`, `core/plugin/center/InstalledPluginRepository.kt:351`, `engine/NodeModuleSourceFileTransportSession.kt`, `external/tasker/**` |
| 存储 (I8) | `model/script/PathChecker.java:56`, `permission/AllFilesAccessPermission.kt`, `ui/storage/{TrashActivity,VersionHistoryActivity}`, `runtime/api/augment/{files,media}/**`, `ui/main/scripts/**` (文件管理器) |
| 小组件 / 通知 (I9) | `external/widget/ScriptWidget.java:49`, `res/xml/script_widget_config.xml`, `util/NotificationUtils.kt:137`, `engine/NodeBridgeProviderRegistry.kt` |
| 通知监听 / 短信 (I10) | `core/notification/NotificationListenerService.kt`, `service/NotificationService.kt`, `runtime/api/Events.java`, `engine/NodeEventObservers.kt` |
| WebView (I11) | `core/ui/attribute/WebViewAttributes.kt:36`, `ui/doc/DocumentationActivity`, HTML 预览路径 |
| inrt (D3) | `inrt/SplashActivity.kt:76,97-113` (仅申请 `WRITE_EXTERNAL_STORAGE`), `inrt/SettingsActivity.kt:52`, `inrt/theme/preference/{ManageAllFilesPermissionSwitchPreference,PostNotificationsPermissionSwitchPreference,DisplayOverOtherAppsPermissionSwitchPreference,ForegroundServiceSwitchPreference}.kt`, `res/xml/fragment_preferences_inrt.xml`, `inrt/launch/{AssetsProjectLauncher,GlobalProjectLauncher}.kt`, `apkbuilder/**`, manifest `:293` `requestLegacyExternalStorage` |

### 3.4 插件舰队现状

42 个插件仓库全部 `TARGET_SDK_VERSION=36`, 除 Archive-Manager (`COMPILE_SDK_VERSION=37`) 外 `COMPILE_SDK_VERSION=36`; 41 个仓库使用 `autojs6-platform-versions` 1.8.0, Bun-Runtime 为 1.7.4 (且 `MIN_SDK_VERSION=33`), R8-Compiler 无 `MIN_SDK_VERSION`. 10 个仓库工作区有未提交改动 (Bun 18, Lua 30, MLKit-OCR 3, Paddle v3 40, v4 / v5 / v6 各 6, Rapid v3 45, Rapid v6 56, Yolo 36 个文件). 逐仓的进程模型, 权限暴露与阶段归属见附录 E.

---

## 4. 目标状态

- 宿主 app / inrt 两个 flavor, 仓内全部子项目, 42 个官方插件的 APK 均为 `compileSdkVersion='37'` 且 `targetSdkVersion='37'` (`aapt2 dump badging`), Runtime Kit 与用户生成的 APK 随 inrt 模板同为 37.
- 第 3.2 节 I1-I15 每一项都有明确结论 (已适配 / 验证无影响 / 记录为已知限制), 其中 I1-I5 在 `AVD_API_37` 上以 `DEVICE-37` 级证据通过, 且真机回归 (API 28 / 31 / 33 / 35) 无退化.
- 单元测试, lint (`lintVital*Release` 无 fatal), 16 KB 门禁, CI 全绿; changelog (宿主 + 每个插件) 与 README 已更新; JS 公开 API 若有变化, 文档 / d.ts / LSP 已同步.
- 本文附录 E 每一行都有提交 hash 与验证设备.

---

## 5. 阶段总览

| 阶段 | 主题 | 依赖 | 涉及仓库 | 状态 |
| --- | --- | --- | --- | --- |
| P0 | 基线与工具 | 无 | 宿主 | 探针与兼容清单完成 (2026-09-13); 覆盖开关与脚本待做 |
| P1 | 宿主 compileSdk 37 | P0 | 宿主 | 未开始 |
| P2 | 宿主 targetSdk 37 行为变更适配 (target 仍为 36) | P0.2 (覆盖开关), P1 | 宿主 | 未开始 |
| P3 | 宿主 targetSdk 37 切换 (app + inrt) | P2 全部 | 宿主, APK-Builder-Template (只读验证) | 未开始 |
| P4 | 关联项目, 文档与 CI | P1 / P3 各自完成后 | Documentation, TypeScript-Declarations, Ace-Editor (LSP), Offline-Docs, VSCode-Ext (仅文档) | 未开始 |
| P5 | 官方插件舰队 (42 仓) | P1 (工具链验证), P2.1 / P2.3 的方案 | 42 个插件仓库 | 未开始 |
| P6 | 验收与关闭 | P1-P5 | 全部 | 未开始 |

P2 的条目之间无依赖, 可按影响矩阵顺序并行; P5 与 P2 / P3 可交错 (插件的 compileSdk 步骤不依赖宿主 targetSdk 切换), 但 P5 中涉及本地网络与动态加载的插件应复用 P2.1 / P2.3 落定的方案.

---

## P0: 基线与工具

目标: 固定事实, 让 P2 每个条目都能在 targetSdk 36 的代码上以 targetSdk 37 的行为验证.

### P0.1 探针与兼容清单 (完成 2026-09-13)

- [x] compileSdk 37 探针编译 (全仓 0 错误, 新增 1 处弃用告警) 与 36 基线对照. (ANDROID_BUILD: [探针记录](evidence/android-sdk-37-probe-20260913.md) 第 1 节)
- [x] `AVD_API_37` 上 `dumpsys platform_compat` 全文入库, 提取 36 项 `enableSinceTargetSdk=37` 变更并逐项评估. (SOURCE: [platform_compat 全文](evidence/android-sdk-37-platform-compat-api37-20260913.txt); DOCS: 附录 B)
- [x] 官方文档核对 (behavior-changes-17 / behavior-changes-all / features / AGP 发行说明 / SDK 仓库 XML / Play 政策), 结论并入第 3 节与附录 B, 核对状态逐项标注. (DOCS)

### P0.2 targetSdk 覆盖开关

- [ ] `Versions.kt` 增加可选 Gradle 属性 `autojs.sdk.target.override` (仅对 debug 构建生效, 未设置时无任何影响): `sdkVersionTarget` / `sdkVersionTargetInrt` 以该值覆盖, 供 P2 用 `-Pautojs.sdk.target.override=37` 出一个 "target 37 的 debug 包" 而不改动 `version.properties`; `showInfo()` 打印覆盖提示. 覆盖仅用于验证, release 任务检测到该属性时构建失败. (SOURCE; ANDROID_BUILD: 覆盖前后 `aapt2 dump badging` 的 `targetSdkVersion` 分别为 36 / 37)
- [ ] `tools/sdk37/compat.sh <serial> <package> <enable|disable|reset> <CHANGE...>` 与 `tools/sdk37/dump_compat.sh <serial>`: 封装 `am compat` / `dumpsys platform_compat` / `cmd audio set-enable-hardening` / `am memory-limiter`, 用法写在脚本头部 (附录 C 命令集脚本化). (SOURCE; DEVICE-37: 在 `AVD_API_37` 上对宿主 debug 包 enable / reset 一次)
- [ ] lint 基线: 在 compileSdk 36 上运行 `:app:lintAppDebug` 与 `:app:lintVitalAnalyzeAppRelease`, 报告存档到 `build/sdk37-lint-36/` (不入库), 供 P1.2 比对. (LINT)

P0 验收: 覆盖开关出包的 `targetSdkVersion` 为 37; 脚本在 AVD 上跑通; lint 基线存在.

---

## P1: 宿主 compileSdk 37

目标: `COMPILE_SDK_VERSION=37`, 编译 / lint / 测试 / 出包 / CI 全部通过, targetSdk 保持 36, 运行时行为不变.

- [ ] `version.properties`: `COMPILE_SDK_VERSION=36` -> `37`. 仓内 47 个构建脚本随之生效, 不改子项目脚本. (SOURCE)
- [ ] 修正探针暴露的弃用: `ui/edit/keyboard/SymbolsSettingsActivity.kt:1110` 的 `InputMethodManager.SHOW_IMPLICIT` 改用 `WindowInsetsControllerCompat.show(ime())` 或 `showSoftInput(view, 0)` (按调用语义选), 不加 `@Suppress`. (SOURCE; ANDROID_BUILD: 37 编译告警回到与 36 基线一致)
- [ ] lint 对比: `:app:lintAppDebug` 与 `:app:lintVitalAnalyzeAppRelease` / `lintVitalAnalyzeInrtRelease` 在 37 上运行, 与 P0.2 基线 diff; 新增的 error / fatal 全部处理, 新增的 warning 逐条决定处理或记录 (`ObsoleteSdkInt`, `UnusedAttribute`, `NewApi` 预期为零); `tools:targetApi` 与 `tools:ignore` 只在确有必要时调整. (LINT: 报告路径与 diff 摘要写入证据)
- [ ] 单元测试: `:app:testAppDebugUnitTest` 全量 (上次记录 2532 通过 / 5 跳过), `:plugin-api:*:testDebugUnitTest`, `:test-apps:*:testDebugUnitTest`; `:app:compileAppDebugAndroidTestKotlin` 通过. (JVM; ANDROID_BUILD)
- [ ] 出包与门禁: `assembleAppDebug`, `assembleAppRelease`, `assembleInrtRelease`, `generateRuntimeKit`, `assembleApkBuilderTemplateDistribution`, `verifyNativePageAlignment` 全部通过; `aapt2 dump badging` 显示 app `compileSdkVersion='37' targetSdkVersion:'36'`, inrt `targetSdkVersion:'29'`. (ANDROID_BUILD)
- [ ] 冒烟: debug 包装入 `AVD_API_37`, Xiaomi 23046RP50C (API 35), Sony G8441 (API 28), 运行 `sample/accessibility/smoke-legacy-api.js` 与一段 UI 模式 / 悬浮窗 / 截图脚本 (compileSdk 变化不改运行时行为, 此项只防止 R8 / 资源合并层面的意外). (DEVICE; DEVICE-37)
- [ ] CI: 推送后 `android.yml` 与 `release-runtime-kit.yml` 绿; 若 runner 未能自动获取 `platforms;android-37.0`, 在 `Set up JDK` 之后补 `sdkmanager "platforms;android-37.0" "build-tools;37.0.0"` 步骤. (ANDROID_BUILD: 远程 CI 链接)
- [ ] changelog `dependency`: "升级 Android SDK 版本 36 -> 37" (10 语言, 与 `2ab13f3c7` 的译法一致), 重新生成 README; 提交 `chore(sdk): raise compileSdk to 37 (roadmap P1)`, 按显式文件清单暂存. (DOCS)

P1 验收: 上述全部勾选; 本文 P1 条目回填证据; `git log -1` 为该提交.

---

## P2: 宿主 targetSdk 37 行为变更适配

目标: 在 targetSdk 仍为 36 的代码上完成全部适配, 每项用 P0.2 的覆盖开关或兼容开关在 `AVD_API_37` 上验证 "target 37 行为正确" 且真机 "target 36 行为不变". 条目按第 3.2 节影响矩阵编号.

### P2.1 本地网络权限 (I1)

- [ ] 事实核对: 用覆盖开关出 target 37 的 debug 包装入 `AVD_API_37`, 分别执行 VS Code 扩展的服务端模式 (设备监听) 与客户端模式 (连接电脑), `http.get("http://<局域网 IP>")`, `$web.newWebSocket("ws://<局域网 IP>")`, `http.get("http://127.0.0.1:<端口>")` (回环应不受影响), 记录失败形态 (超时 / `EPERM`) 与 logcat; 再用 `pm grant org.autojs.autojs6 android.permission.ACCESS_LOCAL_NETWORK` 复测通过. (DEVICE-37: 失败与恢复的两组日志)
- [ ] manifest 声明 `android.permission.ACCESS_LOCAL_NETWORK` (仅声明, targetSdk <= 36 时系统忽略); 新增 `permission/LocalNetworkPermission.kt` (沿用 `PostNotificationsPermission` 的 `Base` / `AbleToUrge` 结构: `has()` 在 SDK < 37 或 targetSdk < 37 时恒为 true, `request()` 走标准运行时权限流程). (SOURCE; JVM: 条件判断的单元测试)
- [ ] 申请时机 (待决 Q1, 默认按推荐项): VS Code 扩展连接入口 (`DevPluginService` 两种模式) 在启动前确保权限, 缺失时弹出说明并引导; 抽屉与设置页增加权限开关行 (与 "显示在其它应用上层" 同款 UI); 脚本层 `http` / `web` / socket 调用不主动弹窗, 但在目标为局域网地址且权限缺失时抛出带解释的错误 (文案 x 11 文件, 按 name 升序), 并提供 `runtime.requestPermissions(["access_local_network"])` (若既有权限申请 API 已支持通用权限名则复用, 不新增函数). (SOURCE; DEVICE-37)
- [ ] MQTT (`MqttService`) 与 Node 桥的局域网路径: 复测 broker 在局域网时的连接错误提示. (DEVICE-37)
- [ ] 文档: 若 JS 层出现新权限名或新错误码, 同步文档 / d.ts / LSP (P4); changelog `feature` (权限流程) 与 `improvement` (错误提示). (DOCS)

### P2.2 后台音频 (I2)

- [ ] 事实核对: `adb shell cmd audio set-enable-hardening throw` 后, 在 `AVD_API_37` 上以 (a) 前台可见时启动脚本, (b) 定时任务触发, (c) `InrtBootCompletedReceiver` / 广播触发 三种入口运行 `media.playMusic`, `device.setMusicVolume`, TTS 朗读, 记录哪些路径抛出 / 静默; 用 `dumpsys audio` 的 `AudioHardening` 行确认 `level`. (DEVICE-37)
- [ ] 策略 (待决 Q3, 默认按推荐项): 保持 "前台服务在应用可见 / 用户交互时启动并常驻" 的既有模式, 新增诊断: 当脚本调用音频 API 而前台服务缺乏 while-in-use 能力时, 在控制台输出一条可定位的警告 (SDK >= 37 且 target >= 37 时). 不引入 media3. (SOURCE; DEVICE-37)
- [ ] 定时任务 / 开机自启入口的前台服务启动方式复核 (`timing/**`, `external/foreground/**`), 确认与 Android 15 的 `FGS_BOOT_COMPLETED_RESTRICTIONS` / `FGS_SAW_RESTRICTIONS` 组合下仍可启动 `specialUse` 服务. (DEVICE-37; DEVICE: API 35 真机)
- [ ] changelog `improvement`. (DOCS)

### P2.3 动态加载只读 (I3)

- [ ] `OpenCvPluginHost.stageCandidate` / `ImageQuantPluginHost` staging 完成后对 `.so` 调用 `setReadOnly()` (失败时抛结构化错误, 复用 16 KB Roadmap P1.5 的错误码模式), 再 `System.load`; 已存在的旧 staged 文件在校验时补只读. (SOURCE; JVM: staging 策略测试; DEVICE-37: `am compat enable THROW_ERROR_FOR_WRITABLE_DCL` 下 OpenCV 与量化插件加载通过)
- [ ] 复核其余动态加载点: `rhino/AndroidClassLoader.loadDex` (已只读), `AceEditorPluginHost` (`PathClassLoader` 指向插件 APK), Node / Lua / QuickJS / Python 宿主侧是否有 staging 到可写目录后加载的路径 (grep `System.load(` 仅 2 处, `DexClassLoader` 3 处). (SOURCE: 复核结论写入本条)
- [ ] 脚本 API `runtime.loadDex` / `runtime.loadJar` 在 `THROW_ERROR_FOR_WRITABLE_DCL` 下通过. (DEVICE-37)
- [ ] changelog `fix`. (DOCS)

### P2.4 Looper / 线程 (I4)

- [ ] `am compat enable USE_NEW_MESSAGEQUEUE LOOPER_CLEARS_THREAD_INTERRUPTED OVERRIDDEN_THREAD_START_METHOD` 下运行: `sample/` 中的定时器 / `threads` / `sleep` / `exit()` / `events` / UI 模式 / 悬浮窗 / 长时间 `setInterval` 脚本与 a11y 冒烟; 重点验证 `exit()` 与 `threads.shutDownAll()` 能中断 `sleep` 与阻塞查找, `MainThreadProxy` / `TimerThread` 的 `interrupt()` 语义. (DEVICE-37: 用例清单与结果)
- [ ] 筛查含 `start()` 的 8 个文件, 列出其中的 `Thread` 子类并说明覆写方式; 若确有覆写 `Thread.start()` 的子类, 改为组合或在 `run()` 内完成前置逻辑. (SOURCE)
- [ ] 文档: 在 `docs/dev/accessibility-architecture.md` 的 "已知平台行为" 或本文附录 F 记录 "Android 17 上脚本反射 `MessageQueue.mMessages` 恒为 null, `static final` 字段反射写入抛 `IllegalAccessException`" 供用户脚本参考; 对 `util.java` / 反射相关文档页评估是否需要提示 (P4). (DOCS)

### P2.5 后台活动启动 (I5)

- [ ] `AndroidPackageSessionInstaller.kt:173,176`: `MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS` / `ALLOWED` 改为 `MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE` (SDK 36+) 并为 `IntentSender` 发送方设置 `ActivityOptions`; `requestPinShortcut(IntentSender)` 三处与 `DrawerFragment.kt:160` 的 `StartIntentSenderForResult` 补发送方 opt-in. (SOURCE)
- [ ] `am compat enable COVER_INTENT_SENDER` + `StrictMode.VmPolicy.detectBlockedBackgroundActivityLaunch()` (debug 构建) 下: 安装 APK 会话, 固定快捷方式, 抽屉的 IntentSender 流程, 脚本从后台 `app.launch` / `app.startActivity` (悬浮窗豁免), 均无 "Background activity launch blocked". (DEVICE-37)
- [ ] `ASM_RESTRICTIONS` (opt-in) 打开后复测脚本跨应用启动, 结论记录; 不在 manifest 设置 `allowCrossUidActivitySwitchFromBelow`. (DEVICE-37)
- [ ] changelog `fix`. (DOCS)

### P2.6 网络安全配置 (I6)

- [ ] `am compat enable DEFAULT_ENABLE_CERTIFICATE_TRANSPARENCY ENABLE_DEFAULT_ENCRYPTED_CLIENT_HELLO` 下: `http.get` 访问 (a) 公网 HTTPS, (b) 本机自签名 HTTPS 服务 (系统 "用户凭据" 安装其 CA), (c) 私有 CA 签发但未安装的证书; 记录 CT 对用户安装 CA 是否豁免 (UNVERIFIED 项的实测结论). (DEVICE-37)
- [ ] 按结论与待决 Q2 决定 `network_security_config.xml` 的 `base-config` 是否加 `<certificateTransparency enabled="false"/>` 与 `<domainEncryption>`; 无论如何为 `githubusercontent.com` 域保持 CT 默认. (SOURCE)
- [ ] 文档: 若保持 CT 开启, 在 `http` 模块文档增加 "Android 17 私有 CA 需安装为用户凭据" 说明 (P4). (DOCS)

### P2.7 Parcel 与插件 IPC (I7)

- [ ] `am compat enable PARCEL_HARDENING` 下运行插件 instrumentation / 冒烟: OpenCV, Image-Quantization, Paddle v4, Rapid v6, MLKit-Barcode, Yolo, Node, Lua, QuickJS, Python, MCP-Server 各一条最短链路 (复用 16 KB Roadmap 的 `ocr_provider_smoke.py` 与各 `*DeviceTest`); Tasker 插件 extras, 快捷方式 extras, `InstalledPluginRepository` 的 metaData 读取. (DEVICE-37)
- [ ] 若有失败, 修正读写不对称的 Parcelable / Bundle 用法; 无失败则记录 "无影响". (SOURCE / DOCS)

### P2.8 存储与文件管理器 (I8)

- [ ] `am compat enable EXCLUDE_UNRELIABLE_STORAGE_VOLUMES REQUIRE_PERMISSIONS_FOR_DIRECTORY_QUERIES TRASH_BEHAVIOR_CHANGE` 下: 文件管理器根目录与外部存储卷列举 (`PathChecker`, 含插入 OTG / 模拟 SD 卡的 AVD 配置), 回收站, 版本历史, `files.*` 读写 `/sdcard`, `media.scanFile`, `images.save` 到相册, 相册 / 文档选择器; 记录 `MediaStore` 查询行为差异. (DEVICE-37)
- [ ] 修正与记录; `MANAGE_EXTERNAL_STORAGE` 引导 (`AllFilesAccessPermission`) 在 API 37 上流程不变. (SOURCE / DOCS)

### P2.9 小组件与通知 (I9)

- [ ] `ScriptWidget` 在 `AVD_API_37` (target 37 覆盖包) 上添加 / 更新 / 点击; `am compat enable CHECK_SIZE_OF_INFLATED_CUSTOM_VIEWS` 下检查宿主全部通知 (前台服务, 脚本通知 API, 崩溃通知) 无异常. (DEVICE-37)
- [ ] 若脚本通知 API 允许自定义视图, 增加尺寸保护或文档说明. (SOURCE / DOCS)

### P2.10 短信 / 通知监听 (I10)

- [ ] 在 AVD 上用 `adb emu sms send` 发送含验证码的短信, 对比 target 36 / 37 下脚本经 `content://sms` 与广播读取的可见性, 以及 `events.observeNotification` 收到的通知文本; 结论写入文档 (无代码可改, 属平台限制). (DEVICE-37; DOCS)

### P2.11 WebView (I11)

- [ ] `am compat enable ENABLE_USER_AGENT_REDUCTION` 下: 文档页, HTML 预览, `ui.webview` 的 UA 字符串与页面渲染; `WebViewAttributes` 的 `userAgentString` 覆盖仍有效. 文档提示 "Android 17 默认 UA 缩减, 需要完整 UA 时显式设置". (DEVICE-37; DOCS)

### P2.12 大屏, 输入法与配置变化 (I12)

- [ ] 在 Xiaomi 23046RP50C (API 35, sw >= 600dp) 与 `AVD_API_37` 上以 target 37 覆盖包运行 UI 模式脚本 (含 `activity.setRequestedOrientation`), 悬浮窗, 控制台, 编辑器旋转 / 分屏; 确认与 target 36 行为一致 (宿主无豁免属性, 预期零差异), 旋转后输入法可见性与 `EditActivity` 的 `adjustResize` 表现. (DEVICE; DEVICE-37)
- [ ] 复核 `MainActivity` / `EditActivity` / `LogActivity` 的 `configChanges` 在 "更少 Activity 重建" 规则下无重复处理; 无改动则记录. (SOURCE)

### P2.13 内存上限 (I13)

- [ ] `AVD_API_37` 上 `am memory-limiter status`, 运行 OpenCV 大图 / OCR / Node 桥重负载脚本, 观察是否触发 `MemoryLimiter:AnonSwap`; 崩溃报告页对 `ApplicationExitInfo.REASON_OTHER` 的描述展示可读. (DEVICE-37)

### P2.14 Advanced Protection Mode (I14)

- [ ] 在 `AVD_API_37` 开启 Advanced Protection, 观察无障碍服务是否被撤销及设置页文案; 若确认 (二手来源转为实测), 在 `A11yServiceController` 的启用失败路径增加检测 (`AdvancedProtectionManager.isAdvancedProtectionEnabled()`, SDK >= 36) 与明确提示; 不声明 `isAccessibilityTool` (待决 Q5). (DEVICE-37; SOURCE; DOCS: 文档 FAQ)

### P2.15 低影响项逐项验证 (I15)

- [ ] 对 `BONDING_APIS_REQUIRE_PRIVILEGED_PERMISSION`, `MAKE_SOCKET_READ_BEHAVIOR_CONSISTENT`, `RESTRICT_DATA_URI_COLUMNS`, `ENFORCE_STRICT_SQL_CHECKS`, `CHECK_ADD_MIRROR_DISPLAY_PERMISSION` (截图 `images.captureScreen` 经 MediaProjection), `DEPRECATE_SET_CONTENT_CAPTURE_ENABLED`, `SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL`, `BACK_DISPOSITION_CONTROLS_BACK_INTERCEPTION`, `DETECT_IMPLICIT_URI_PERMISSION_GRANT` (加 `detectImplicitUriPermissionGrant()` 检查 `app.viewFile` / 分享路径, 为 Android 18 预备), 在附录 B 对应行填 "已验证无影响" 或转为独立条目. (DEVICE-37)

P2 验收: I1-I15 每项在附录 B 有结论; target 37 覆盖包在 `AVD_API_37` 通过 P2.1-P2.5 的全部用例; 同一代码的正常 debug 包在 API 28 / 33 / 35 真机上跑 a11y 冒烟与 VS Code 连接无退化; changelog 条目齐备.

---

## P3: 宿主 targetSdk 37 切换

目标: `TARGET_SDK_VERSION=37` 与 `TARGET_SDK_VERSION_INRT=37` 同一提交切换, 全矩阵回归, 打包链路与用户 APK 验证.

### P3.1 切换

- [ ] `version.properties`: `TARGET_SDK_VERSION=37`, `TARGET_SDK_VERSION_INRT=37`; 移除 P0.2 覆盖开关的使用 (开关本身保留). (SOURCE)
- [ ] manifest 复核: `requestLegacyExternalStorage` 对 target >= 30 无效但对旧版升级无害, 按待决 Q4 决定是否增加 `preserveLegacyExternalStorage="true"` (仅影响 inrt 用户 APK 从 29 升级到 37 的存储保留); `tools:targetApi` 是否需要调整由 lint 决定. (SOURCE; LINT)
- [ ] 出包与门禁同 P1 (`aapt2` 显示 app 与 inrt 均 `targetSdkVersion:'37'`); 单元测试全量; lint 对比 P1 结果. (ANDROID_BUILD; JVM; LINT)

### P3.2 宿主回归矩阵

- [ ] `AVD_API_37`: a11y 冒烟 (`smoke-legacy-api.js`), 悬浮窗 / 控制台 / 布局分析, 截图与 OCR (OpenCV + Paddle v4 或 Rapid v6), VS Code 扩展两种模式 (权限引导), `http` / WebSocket 到局域网与回环, 定时任务与开机自启 (音频 / TTS), 通知 / 小组件, 文件管理器 (卷 / 回收站 / 媒体), APK 安装会话, 快捷方式, 文档 WebView, UI 模式与预测性返回, `exit()` / 线程中断. (DEVICE-37)
- [ ] 真机回归 (targetSdk 提升对旧系统的影响): Xiaomi 23046RP50C (API 35), Sony XQ-DQ72 (API 33), Sony XQ-AT72 (API 31), Sony G8441 (API 28) 各跑 a11y 冒烟 + VS Code 连接 + 截图 + 定时任务; 重点确认 API 28 / 31 上不出现新权限弹窗与行为变化. (DEVICE)
- [ ] 16 KB: `aj6-ps16k-37-x86_64` 上安装与 `verify_device.sh` 无兼容模式警告 (与 16 KB Roadmap 的验证一致). (DEVICE-16K)

### P3.3 打包应用 (inrt) 29 -> 37 清单 (D3)

用户 APK 的运行时代码与宿主共用, 以下按 API 等级列出对 "以 29 为目标的旧行为" 的增量, 每项在 `AVD_API_37` 与 API 33 真机上用打包出的示例应用验证:

| 等级 | 对打包应用的增量 | 验证 |
| --- | --- | --- |
| 30 | 分区存储强制 (`requestLegacyExternalStorage` 失效): 脚本读写 `/sdcard` 需 `MANAGE_EXTERNAL_STORAGE`; `SplashActivity.kt:76` 目前只申请 `WRITE_EXTERNAL_STORAGE` (>= 33 无意义) | 首次启动引导 "所有文件访问" (待决 Q4); `files.read("/sdcard/...")` 在授予前后 |
| 30 | 包可见性 (`<queries>` 已在共用 manifest, 且声明 `QUERY_ALL_PACKAGES`) | `app.launch` / `app.getAppName` 任意包 |
| 31 | 组件 `exported` 显式化 (共用 manifest 已满足); `PendingIntent` 可变性 (代码已按 36 处理); 后台启动前台服务限制 (`FGS_BG_START_RESTRICTION_CHANGE_ID`; `BOOT_COMPLETED` 属豁免); 精确闹钟权限 (`SCHEDULE_EXACT_ALARM` + 已声明的 `USE_EXACT_ALARM`); 系统启动画面叠加在自定义 `SplashActivity` 之前; 通知跳板限制 | 开机自启 (`InrtBootCompletedReceiver`), 定时任务, 启动画面观感 |
| 33 | `POST_NOTIFICATIONS` 运行时权限 (前台服务通知不可见但服务可运行); 细分媒体权限; `WRITE_EXTERNAL_STORAGE` 申请必然被拒 | 通知权限开关 (`PostNotificationsPermissionSwitchPreference`), 去掉无效的存储申请 |
| 34 | 前台服务类型必填 (已声明); 运行时注册接收器需导出标志 (共用代码); 隐式 Intent 只匹配已导出组件; `Thread.stop` 不可用; 动态加载 DEX 只读 (`AndroidClassLoader` 已处理); MediaProjection 每次会话授权 + 回调 | `runtime.loadDex`, 截图, `events.broadcast` |
| 35 | 边到边强制 (UI 模式脚本布局); 前台服务 `dataSync` / `mediaProcessing` 超时 (未用); `BOOT_COMPLETED` 禁止启动部分类型前台服务 (`specialUse` 允许); 悬浮窗不再豁免后台启动前台服务 (`FGS_SAW_RESTRICTIONS`, 仅可见悬浮窗豁免); 最低可安装 targetSdk 24 | UI 模式脚本顶部内容, 开机自启后的前台服务 |
| 36 | 预测性返回默认开启 (`onBackPressed` 不再回调, 共用 `BackCompat`); 边到边豁免移除; 大屏忽略方向; Intent 重定向保护; `announceForAccessibility` 弃用 | 打包应用的 `LogActivity` / `SettingsActivity` 返回, 平板方向 |
| 37 | P2 全部条目, 其中本地网络权限与后台音频对打包应用最直接 (脚本自建 HTTP 服务 / TTS) | 复用 P2.1 / P2.2 的用例 |

- [ ] 依上表逐行验证并修正 inrt 专属流程 (`SplashActivity` 权限引导, `fragment_preferences_inrt.xml` 的开关顺序与默认值). (SOURCE; DEVICE-37; DEVICE)
- [ ] 旧版升级: 用 target 29 的旧模板打一个示例 APK 装到 API 33 真机与 `AVD_API_37`, 再用 target 37 的新模板覆盖安装, 验证脚本文件与 `/sdcard` 数据可继续访问 (待决 Q4 的 `preserveLegacyExternalStorage`). (DEVICE; DEVICE-37)
- [ ] 打包器 UI (`ui/project/BuildActivity`, `ProjectConfigActivity`) 与文档说明打包应用在 Android 11+ 的存储引导. (DOCS)

### P3.4 打包链路与 Runtime Kit

- [ ] `generateRuntimeKit` 重新生成; `AutoJs6-Plugin-APK-Builder-Template` 的 JVM 测试与 `build-from-runtime-kit.yml` 夹具中若有 `targetSdkVersion=29` 的硬编码期望, 改为读取模板 (该仓库条目在 P5.2 落地, 本条只做只读核对). (SOURCE; JVM)
- [ ] 用新 Runtime Kit 打包 3 个示例 (a11y 冒烟, UI 模式, 定时任务 + TTS), `aapt2` 显示 `targetSdkVersion:'37'`, 在 `AVD_API_37` 与 API 33 / 35 真机安装运行. (DEVICE-37; DEVICE)

### P3.5 文档与提交

- [ ] changelog: P2 各条目的 `fix` / `improvement` 汇总核对 (D9), README 重新生成; 本文回填. (DOCS)
- [ ] 提交 `chore(sdk): raise targetSdk to 37 for app and inrt (roadmap P3)`. (DOCS)

P3 验收: P3.2 矩阵通过; P3.3 表每行有结论; 用户 APK 在 API 37 / 35 / 33 上安装运行; CI 绿.

---

## P4: 关联项目, 文档与 CI

- [ ] 文档仓库 (`AutoJs6-Documentation`): 若 P2.1 引入新权限名 / 错误码或 P2 新增任何 JS API, 执行 `auto-generate-for-autojs6.bat` 并提交 (含 `AutoJs6-Plugin-Offline-Docs`); 无 API 变化时只在文档增加 "Android 17 适配说明" 页 (本地网络权限, 后台音频, OTP 短信延迟, WebView UA, 反射限制, 私有 CA). (DOCS)
- [ ] d.ts 与 LSP (`AutoJs6-TypeScript-Declarations`, `AutoJs6-Plugin-Ace-Editor`): 仅当 JS API 变化时执行 `aj6dts.bat -Publish` 与 `:app:generateAutoJs6LspDeclarations`, 版本号加一. (DOCS)
- [ ] VS Code 扩展 (`AutoJs6-VSCode-Ext`): README / 故障排查增加 "Android 17 设备需授予本地网络权限" 说明 (不改代码, `VSCODE_EXT_REQUIRED_VERSION` 不变). (DOCS)
- [ ] CI: P1 / P3 各自推送后远程 `android.yml` 与 `release-runtime-kit.yml` 绿; 记录 runner 是否自动下载了 `android-37.0`. (ANDROID_BUILD)
- [ ] `AutoJs6-Official-Plugins-Index`: 确认生成器无 targetSdk 字段假设; 插件发布时按既有流程刷新. (SOURCE)

---

## P5: 官方插件舰队 (42 仓)

目标: 每个插件 `compileSdk` / `targetSdk` 均为 37, 暴露项已适配, 各自 changelog / README / 版本号更新. 逐仓清单与暴露列见附录 E; 本节按分组给出条目模板与分组特有事项.

### P5.0 每仓通用条目 (复制到附录 E 对应行的证据栏)

1. 工作区: 若附录 E "脏" 列非 0, 先由维护者提交或搁置既有改动 (D11). (SOURCE)
2. compileSdk: `COMPILE_SDK_VERSION=37`; 构建 (`assembleDebug` / `assembleRelease`), 单元测试, 既有门禁 (16 KB `verifyNativePageAlignment`, 各仓库自有校验); 修正弃用. (ANDROID_BUILD; JVM)
3. 暴露适配: 按附录 E 暴露列处理 (本地网络 -> 复用 P2.1 权限助手模式; 动态加载 -> 只读; 前台服务 / 音频 -> P2.2 结论; 屏幕捕获 -> P2.15 结论; WebView -> P2.11 结论). (SOURCE)
4. targetSdk: `TARGET_SDK_VERSION=37`; 在 `AVD_API_37` 与一台 4 KB 真机 (API 33 或 35) 上跑该插件的 instrumentation / 冒烟 (16 KB Roadmap 用过的 `*DeviceTest`, `ocr_provider_smoke.py` 等). (DEVICE-37; DEVICE)
5. 版本递增 (各仓库惯例), changelog `dependency` "升级 Android SDK 版本 36 -> 37" + 适配条目, README 重新生成, 单独提交. (DOCS)
6. 发布节奏按待决 Q6.

### P5.1 被宿主进程加载 (OpenCV, Image-Quantization)

- [ ] 两仓通用条目; 运行时 `.so` 的只读处理在宿主侧 (P2.3), 插件侧无运行时暴露; 提交后用宿主 target 37 覆盖包复测加载. (DEVICE-37)

### P5.2 打包进用户 APK (Paddle v3 / v4 / v5 / v6, Rapid v3 / v6, APK-Builder-Template)

- [ ] 6 个 OCR 仓库通用条目; 核对引擎以 `System.loadLibrary` 从 `nativeLibraryDir` 加载 (只读), 无 staging 到可写目录的路径; 在 target 37 的用户 APK 内 (P3.4 示例) 复测识别. (SOURCE; DEVICE-37)
- [ ] APK-Builder-Template: 通用条目 + 模板 / 夹具中 targetSdk 期望改为读取模板 (P3.4), `configureManifest` 不改写 `uses-sdk` 的约束保持; CI 工作流对模板 APK 的 `aapt2` 检查加 `targetSdkVersion` 断言. (SOURCE; JVM; ANDROID_BUILD)

### P5.3 独立进程运行时插件 (NodeJs, Bun, Python, Lua, QuickJS, TypeScript-Engine, Java-Runtime, Kotlin-Runtime, DEX-Compiler, R8-Compiler)

- [ ] 通用条目; 动态加载只读: Node 的 `.node` 原生扩展与用户项目内的 `.so`, Bun 的 `libbun_exec.so` (exec 路径不受 `dlopen` 规则影响, 但其 `dlopen` 用户库需只读), Python (Chaquopy 解压到私有目录的扩展模块), Java / Kotlin / DEX / R8 编译产物的 DEX 加载 (`DexClassLoader` 前 `setReadOnly`), 逐仓 grep `System.load` / `dlopen` / `DexClassLoader` 并在 `THROW_ERROR_FOR_WRITABLE_DCL` 下复测. (SOURCE; DEVICE-37)
- [ ] 本地网络: 在插件进程内执行脚本时打开局域网 socket (Node `http.createServer`, Bun `Bun.serve`, Python `socket`) 需要各插件自身的 `ACCESS_LOCAL_NETWORK` (权限按 UID, 宿主授权不覆盖插件); 三个带 `INTERNET` 的运行时 (Node, Bun, Python) 声明权限并在宿主转发的错误中提示 "在插件设置中授予". (SOURCE; DEVICE-37)
- [ ] 内存上限与 MessageQueue: 各运行时若使用 Android Looper, 在 `USE_NEW_MESSAGEQUEUE` 下复测; 大负载脚本观察 `am memory-limiter status`. (DEVICE-37)
- [ ] Bun-Runtime 顺带把 `autojs6-platform-versions` 从 1.7.4 升到 1.8.0 (与舰队一致, 不是本 Roadmap 的必需项, 但同仓库改动时一并完成). (SOURCE)

### P5.4 网络, 媒体与屏幕插件 (MCP-Server, Screen-Color-Picker, Three-Ember-Player, Three-Terra-Player, Three-Stone-AI, HTML-Previewer, Markdown-Previewer, Offline-Docs, Ace-Editor)

- [ ] MCP-Server: 局域网 SSE / Streamable 服务端需要 `ACCESS_LOCAL_NETWORK` (入站 accept 也受限); 插件设置页增加权限引导, 服务启动前检查; `specialUse` 前台服务不变; 在 `AVD_API_37` 上用 Inspector / Claude Code 走一次完整配对 (复用 MCP Roadmap 的 P3.1 验收流程). (SOURCE; DEVICE-37)
- [ ] Screen-Color-Picker: `mediaProjection` 前台服务 + 悬浮窗; `CHECK_ADD_MIRROR_DISPLAY_PERMISSION` 下取色通过. (DEVICE-37)
- [ ] Three-Ember / Three-Terra: `mediaPlayback` 前台服务在后台音频强化 (target 37 需 while-in-use) 下的启动时机; `cmd audio set-enable-hardening throw` 复测播放 / 列表切换 / 锁屏. (DEVICE-37)
- [ ] Three-Stone-AI: 若 LiteRT-LM 使用 NPU 委托, 声明 `<uses-feature android:name="android.hardware.npu" android:required="false"/>` (target 37 要求, VERIFIED); 内存上限下的推理. (SOURCE; DEVICE-37)
- [ ] HTML / Markdown 预览, Offline-Docs, Ace-Editor: WebView UA 缩减与 File System Access 下渲染正常; LSP / 本地文档服务走回环不受本地网络权限影响, 记录结论. (DEVICE-37)

### P5.5 其余插件 (MLKit-Barcode, MLKit-OCR, Yolo-NCNN, MediaInfo, OpenCC, ImGui, Archive-Manager, Image-Tools, Image-Viewer, File-Inspector, APK-Inspector, Pinyin, Pinyin4j, Accessibility-Compat)

- [ ] 通用条目; Binder 协议在 `PARCEL_HARDENING` 下复测 (MLKit x2, Yolo, MediaInfo, OpenCC, ImGui); 存储类插件 (Archive-Manager, File-Inspector, Image-*) 在 P2.8 的三个开关下复测; Archive-Manager 已是 compileSdk 37, 只做 targetSdk 步骤. (DEVICE-37)

P5 验收: 附录 E 每行 compile / target 均为 37 且有提交 hash 与验证设备; 官方索引在发布后刷新.

---

## P6: 验收与关闭

- [ ] 宿主全矩阵 (P3.2) 与舰队 (P5) 证据齐备; `git log` 中每个子条目一个提交; 远程 CI 绿. (DOCS)
- [ ] 本文 "阶段总览" 状态列更新为 "完成"; 附录 A 全部待决事项回填到第 1 节; 附录 F 的后续建议移交给各自的 Roadmap 或 backlog. (DOCS)
- [ ] 自动记忆索引 (`MEMORY.md`) 中本 Roadmap 条目更新为已关闭. (DOCS)

---

## 附录 A: 待决事项

| 编号 | 事项 | 选项 | 推荐 |
| --- | --- | --- | --- |
| Q1 | 本地网络权限的申请时机 (P2.1) | (a) 懒申请: VS Code 连接 / 用户显式开启的服务启动时引导, 脚本层只报错 + 设置页开关; (b) 应用首次启动即申请; (c) 脚本层任何局域网调用都弹窗 | (a): 与既有 "按功能引导权限" 的模式一致, 不打扰不用局域网功能的用户 |
| Q2 | Certificate Transparency 默认策略 (P2.6) | (a) 保持平台默认开启, 仅文档说明; (b) `base-config` 关闭 CT 以保住私有 CA 脚本场景; (c) 按 P2.6 实测决定: 用户安装 CA 豁免则 (a), 否则 (b) | (c) |
| Q3 | 后台音频策略 (P2.2) | (a) 保持前台服务常驻模式 + 诊断警告; (b) 引入 media3 `MediaSessionService` 承载脚本音频 | (a): 改动最小, 且脚本音频并非媒体应用语义 |
| Q4 | inrt 存储引导与旧版升级保护 (P3.3) | (a) 首次启动引导 "所有文件访问" 且 manifest 增加 `preserveLegacyExternalStorage="true"`; (b) 仅设置页开关, 不引导; (c) 引导但不加保留属性 | (a): 打包应用的脚本几乎都读写 `/sdcard`, 旧版升级不应丢失访问 |
| Q5 | `isAccessibilityTool` (P2.14) | (a) 不声明, 只检测 Advanced Protection 并提示; (b) 声明 | (a): AutoJs6 是自动化工具, 声明会与其定义不符 |
| Q6 | 插件舰队发布节奏 (P5) | (a) 随各插件下一次发布顺带, 有行为适配的插件 (MCP-Server, Node, Bun, Python, 播放器 x2, Screen-Color-Picker) 单独发版; (b) 全部单独为 SDK 升级发版; (c) 只提交不发布, 由维护者统一发布 | (a) |
| Q7 | `usesCleartextTraffic` 迁移到 NSC 是否随本次 | (a) 否, 记入附录 F; (b) 是 | (a): Android 17 未强制, 与 SDK 升级解耦 |

---

## 附录 B: targetSdk 37 门控变更逐项评估

来源: `AVD_API_37` 的 `dumpsys platform_compat` (36 项 `enableSinceTargetSdk=37`), 含义列的核对状态: VERIFIED = 官方页面明确描述; UNVERIFIED = 仅由名称推断或二手来源. "结论" 列在 P2 完成后回填.

| 变更 (ID) | 含义 | 宿主影响 | 条目 | 结论 |
| --- | --- | --- | --- | --- |
| `ASM_RESTRICTIONS` (230590090, overridable) | Activity 安全模型 (任务劫持保护), target 37 起可 opt-in, 默认关闭 (VERIFIED) | 脚本跨应用启动 Activity | P2.5 | |
| `BACK_DISPOSITION_CONTROLS_BACK_INTERCEPTION` (454542168) | 输入法 `setBackDisposition` 决定是否拦截返回 (UNVERIFIED) | 无 (非输入法) | P2.15 | |
| `BONDING_APIS_REQUIRE_PRIVILEGED_PERMISSION` (401333807) | Bluetooth 绑定类 API 需要特权权限 (UNVERIFIED) | 脚本蓝牙 API (代码内无 `createBond`) | P2.15 | |
| `CHECK_ADD_MIRROR_DISPLAY_PERMISSION` (378605160) | 创建镜像虚拟显示需权限检查 (UNVERIFIED) | `ScreenCapturer.java:284-289` 经 MediaProjection 创建 `AUTO_MIRROR` 显示 | P2.15 | |
| `CHECK_SIZE_OF_INFLATED_CUSTOM_VIEWS` (270553691) | 通知自定义视图尺寸限制 (公告 VERIFIED, 细节 UNVERIFIED) | 通知构建点 | P2.9 | |
| `COVER_INTENT_SENDER` (405995292) | `IntentSender.sendIntent()` 需发送方 BAL opt-in (VERIFIED) | 安装会话, 固定快捷方式, 抽屉 IntentSender | P2.5 | |
| `DEFAULT_ENABLE_CERTIFICATE_TRANSPARENCY` (407952621) | CT 默认开启, NSC 可按域关闭 (VERIFIED) | 私有 CA 场景 | P2.6 | |
| `DEPRECATE_SET_CONTENT_CAPTURE_ENABLED` (454307959) | `setContentCaptureEnabled(false)` 不再生效, 改用 `FLAG_SECURE` (VERIFIED) | 无 | P2.15 | |
| `DETECT_IMPLICIT_URI_PERMISSION_GRANT` (460838111) | StrictMode 检测隐式 URI 授权, Android 18 起限制 (VERIFIED) | `app.viewFile` / 分享路径 | P2.15 | |
| `DETECT_INSTANCE_COUNT_VIOLATIONS_WHEN_LISTENER_SET` (333566037) | StrictMode 实例计数检测 (UNVERIFIED) | 无 | P2.15 | |
| `DISABLE_OPT_OUT_UNIVERSAL_RESIZABLE_BY_DEFAULT` (447301631) | 大屏方向 / 尺寸豁免失效 (VERIFIED) | 宿主未用豁免, 增量为零 | P2.12 | |
| `DONOT_STEAL_AUDIO_ON_GATT_CONN` (455594702) | BLE GATT 连接不再抢占音频路由 (UNVERIFIED) | 无 | P2.15 | |
| `ENABLE_DEFAULT_ENCRYPTED_CLIENT_HELLO` (419020719) | TLS ECH 默认开启, NSC `<domainEncryption>` 可关 (VERIFIED) | TLS 拦截代理环境 | P2.6 | |
| `ENABLE_FILE_SYSTEM_ACCESS` (364980165) | WebView File System Access API (UNVERIFIED) | WebView 页面 | P2.11 | |
| `ENABLE_MATCH_NON_THREAD_LOCAL_NETWORKS` (349487600) | 本地网络匹配扩展到非 Thread 网络 (UNVERIFIED) | 本地网络权限判定 | P2.1 | |
| `ENABLE_RESTRICT_ASSIST_STRUCTURE` (437416500) | 助手结构访问受限 (UNVERIFIED) | 无 | P2.15 | |
| `ENABLE_USER_AGENT_REDUCTION` (371034303) | WebView 默认 UA 缩减 (VERIFIED) | 文档 / 预览 / `ui.webview` | P2.11 | |
| `ENFORCE_STRICT_SQL_CHECKS` (484953293) | ContactsContract 查询严格 SQL 检查 (VERIFIED, 仅联系人) | `QuickContactBadgeAttributes.kt` | P2.15 | |
| `EXCLUDE_UNRELIABLE_STORAGE_VOLUMES` (391360514) | `getStorageVolumes()` 排除不可靠卷 (UNVERIFIED) | 文件管理器卷列举 | P2.8 | |
| `EXPAND_DEVICE_MANAGED_SCOPE` (451607072) | 设备策略范围变化 (UNVERIFIED) | 无 | P2.15 | |
| `FILTER_GENERIC_OTP` (437043173) | 含 OTP 的短信延迟 3 小时 (VERIFIED) | 脚本读短信 | P2.10 | |
| `LOOPER_CLEARS_THREAD_INTERRUPTED` (458413887, overridable) | Looper 派发前清除线程中断标志 (UNVERIFIED) | 脚本引擎中断语义 | P2.4 | |
| `MAKE_SOCKET_READ_BEHAVIOR_CONSISTENT` (383671392) | `BluetoothSocket` RFCOMM `read()` 断开返回 -1 (VERIFIED) | 脚本蓝牙 socket | P2.15 | |
| `OVERRIDDEN_THREAD_START_METHOD` (418924588) | 覆写 `Thread.start()` 的子类行为变化 (UNVERIFIED) | 含 `start()` 的 8 个文件 | P2.4 | |
| `PARCEL_HARDENING` (416031865) | Parcel 读写更严格 (UNVERIFIED) | 插件 IPC, Bundle extras | P2.7 | |
| `REBOOT_MODEM_THROW_EXCEPTIONS` (476225321) | 电话调制解调器重启 API 抛异常 (UNVERIFIED) | 无 | P2.15 | |
| `RENDERSCRIPT_SCRIPTC_DEPRECATION_CHANGE_ID` (297019750) | RenderScript ScriptC 停用 (UNVERIFIED) | 无 (代码无 RenderScript) | P2.15 | |
| `REQUIRE_PERMISSIONS_FOR_DIRECTORY_QUERIES` (157720069) | MediaStore / 文档目录查询需对应权限 (UNVERIFIED) | `media.*`, 文件管理器 | P2.8 | |
| `RESTRICT_DATA_URI_COLUMNS` (437318646) | `ContactsContract.Data` 移除账户列 (VERIFIED) | `QuickContactBadgeAttributes.kt` | P2.15 | |
| `RESTRICT_DEPRECATED_DATA_BUILDER_CONSTRUCTORS` (424093067) | 弃用的 Data Builder 构造器受限 (UNVERIFIED) | 待定位 | P2.15 | |
| `SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL` (417951523) | 显示密码设置拆分为触摸 / 物理键盘 (VERIFIED) | 无 | P2.15 | |
| `THROW_ERROR_FOR_WRITABLE_DCL` (463348571) | `System.load()` 可写文件抛 `UnsatisfiedLinkError` (VERIFIED) | OpenCV / 量化插件 staging | P2.3 | |
| `TRASH_BEHAVIOR_CHANGE` (461429441) | MediaStore 回收站语义变化 (UNVERIFIED) | `media.*`, 相册相关 | P2.8 | |
| `USE_NEW_MESSAGEQUEUE` (421623328) | 无锁 MessageQueue (VERIFIED) | 脚本引擎 Looper | P2.4 | |
| `USE_NSD_PICKER_WHEN_NO_LOCAL_NET_PERMISSION` (490596785) | 无本地网络权限时 NSD 走系统选择器 (VERIFIED) | 无 (未用 `NsdManager`) | P2.1 | |
| `WEAROS_WALLPAPER_HANDLES_SCALING` (272527315) | Wear 壁纸缩放 (UNVERIFIED) | 无 | P2.15 | |

不经兼容框架门控但与本次相关的项: `ACCESS_LOCAL_NETWORK` 权限 (I1), 后台音频强化 (I2), `static final` 反射写入抛 `IllegalAccessException` (I4, VERIFIED, 无开关), 小组件 RemoteViews 内存上限 (I9, VERIFIED), 密钥库上限, 应用内存上限 (I13), Advanced Protection (I14). 面向 38 的 `NETWORK_BLOCKED_WITHOUT_INTERNET_PERMISSION` 与 `enableSinceTargetSdk=10000` 的 4 项不在本次范围.

---

## 附录 C: 命令与参考

- 兼容开关 (仅 debuggable 构建可用):
  - `adb -s <serial> shell am compat enable|disable|reset <CHANGE_NAME|ID> org.autojs.autojs6`; `adb shell am compat reset-all org.autojs.autojs6`
  - `adb shell dumpsys platform_compat` (全量清单); `adb logcat | grep CompatibilityChangeReporter` (运行时报告)
  - 开发者选项 > App Compatibility Changes 有同样的开关
- 后台音频: `adb shell cmd audio set-enable-hardening enable|disable|throw`; `adb shell dumpsys audio | grep -A3 AudioHardening`
- 内存上限: `adb shell am memory-limiter status`; `am memory-limiter ignore <uid>|none|all`; `am memory-limiter manual <pid> <MB>|max|none`
- 本地网络 (Android 16 设备上模拟): `adb shell am compat enable RESTRICT_LOCAL_NETWORK <pkg>` 后重启; Android 17 上直接用 target 37 覆盖包 + `pm grant <pkg> android.permission.ACCESS_LOCAL_NETWORK` / `pm revoke`
- StrictMode (debug 构建): `VmPolicy.Builder().detectBlockedBackgroundActivityLaunch().detectImplicitUriPermissionGrant()`; logcat 关键字 "Background activity launch blocked", "Please set the grant explicitly in the app"
- 短信: `adb -s emulator-5554 emu sms send 10086 "验证码 123456"`
- 出包核对: `%ANDROID_HOME%/build-tools/37.0.0/aapt2 dump badging app.apk | findstr /C:"compileSdkVersion" /C:"targetSdkVersion"`
- lint: `./gradlew :app:lintAppDebug :app:lintVitalAnalyzeAppRelease :app:lintVitalAnalyzeInrtRelease`; 报告 `app/build/reports/lint-results-*.html`
- 探针 / 基线编译 (P0 用法): `./gradlew --offline --continue --console=plain "-Pkotlin.daemon.jvmargs=-Xmx6g" :app:compileAppDebugKotlin :app:compileAppDebugJavaWithJavac compileDebugKotlin compileDebugJavaWithJavac`
- 模拟器: `emulator -avd AVD_API_37 -no-snapshot-save` (`android-37.0;google_apis_ps16k;x86_64`, `getconf PAGE_SIZE` 为 16384); `adb shell getprop ro.build.version.sdk` 应为 37
- 官方参考 (2026-09-13 核对): https://developer.android.com/about/versions/17/behavior-changes-17 ; https://developer.android.com/about/versions/17/behavior-changes-all ; https://developer.android.com/about/versions/17/migration ; https://developer.android.com/privacy-and-security/local-network-permission ; https://developer.android.com/about/versions/17/changes/bg-audio ; https://developer.android.com/about/versions/17/changes/messagequeue ; https://developer.android.com/about/versions/17/changes/ff-restrictions-ignored ; https://developer.android.com/guide/components/activities/background-starts ; https://developer.android.com/guide/app-compatibility/test-debug ; https://developer.android.com/build/releases/about-agp (API 37.0 -> AGP 9.1.1) ; https://developer.android.com/google/play/requirements/target-sdk

---

## 附录 D: 证据等级与验收方式

| 标签 | 可以证明 | 不能证明 |
| --- | --- | --- |
| `SOURCE` | 源码 / 配置 / 脚本存在, 结构符合设计 | 编译或行为正确 |
| `JVM` | Android-free 逻辑的单元测试 (JUnit4, 无 Robolectric) | 真机行为 |
| `ANDROID_BUILD` | `assemble*` / `generateRuntimeKit` 通过且 16 KB 门禁通过 (或 CI 绿) | 真机行为 |
| `LINT` | lint 报告与基线 diff 已记录, 无新增 error / fatal | 运行时行为 |
| `DEVICE` | 指定 4 KB 真机与 API 级别 (28 / 31 / 33 / 35) 上的冒烟通过 | Android 17 行为 |
| `DEVICE-37` | `AVD_API_37` (或 Android 17 真机, 注明型号) 上, 注明 targetSdk (36 + 覆盖 / 37) 与打开的兼容开关, 冒烟通过 | 未列出设备 / 开关组合 |
| `DEVICE-16K` | `aj6-ps16k-37-x86_64` 上安装无兼容模式警告 | - |
| `DOCS` | changelog / README / 文档 / d.ts / 本文已更新且版本号已递增 | - |

条目勾选时在其后追加证据, 格式示例: `[x] ... (DEVICE-37: AVD_API_37, target 37 覆盖包, compat USE_NEW_MESSAGEQUEUE on, 12/12 用例; commit abc1234)`.

---

## 附录 E: 插件舰队矩阵

"暴露" 列取自各仓库 manifest 扫描 (2026-09-13): 前台服务类型, 悬浮窗 (`SYSTEM_ALERT_WINDOW`), `INTERNET`, `MANAGE_EXTERNAL_STORAGE`, 以及已知的进程模型; "脏" 为工作区未提交文件数. 阶段与分组对应 P5.x. "证据" 列在落地时回填 (提交 hash / 设备).

| 仓库 | compile / target / min | 平台插件 | 进程模型 | 暴露 | 脏 | 阶段 | 证据 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| OpenCV | 36 / 36 / 24 | 1.8.0 | 宿主进程 (staging 加载) | 动态加载 (宿主侧) | 0 | P5.1 | |
| Image-Quantization | 36 / 36 / 24 | 1.8.0 | 宿主进程 (staging 加载) | 动态加载 (宿主侧) | 0 | P5.1 | |
| Paddle-OCR-PP-OCRv3 | 36 / 36 / 24 | 1.8.0 | 插件进程 / 用户 APK | Binder, 原生库 | 40 | P5.2 | |
| Paddle-OCR-PP-OCRv4 | 36 / 36 / 24 | 1.8.0 | 插件进程 / 用户 APK | Binder, 原生库 | 6 | P5.2 | |
| Paddle-OCR-PP-OCRv5 | 36 / 36 / 24 | 1.8.0 | 插件进程 / 用户 APK | Binder, 原生库 | 6 | P5.2 | |
| Paddle-OCR-PP-OCRv6 | 36 / 36 / 24 | 1.8.0 | 插件进程 / 用户 APK | Binder, 原生库 | 6 | P5.2 | |
| Rapid-OCR-PP-OCRv3 | 36 / 36 / 24 | 1.8.0 | 插件进程 / 用户 APK | Binder, 原生库 | 45 | P5.2 | |
| Rapid-OCR-PP-OCRv6 | 36 / 36 / 24 | 1.8.0 | 插件进程 / 用户 APK | Binder, 原生库 | 56 | P5.2 | |
| APK-Builder-Template | 36 / 36 / 24 | 1.8.0 | 插件进程 (打包) | 模板 targetSdk 期望, CI 断言 | 0 | P5.2 | |
| NodeJs-Runtime | 36 / 36 / 24 | 1.8.0 | `:nodejs_runtime` | INTERNET (本地网络), MANAGE_EXTERNAL_STORAGE, 动态加载 (`.node`) | 0 | P5.3 | |
| Bun-Runtime | 36 / 36 / 33 | 1.7.4 | `:bun_runtime` (exec) | INTERNET (本地网络), 动态加载 | 18 | P5.3 | |
| Python-Runtime | 36 / 36 / 24 | 1.8.0 | `:python_runtime` | INTERNET (本地网络), 动态加载 (Chaquopy) | 0 | P5.3 | |
| Lua-Runtime | 36 / 36 / 24 | 1.8.0 | `:lua_runtime` | Binder | 30 | P5.3 | |
| QuickJS-Runtime | 36 / 36 / 24 | 1.8.0 | `:quickjs_runtime` | Binder | 0 | P5.3 | |
| TypeScript-Engine | 36 / 36 / 24 | 1.8.0 | `:typescript_*` | Binder | 0 | P5.3 | |
| Java-Runtime | 36 / 36 / 24 | 1.8.0 | 插件进程 | 动态加载 (DEX) | 0 | P5.3 | |
| Kotlin-Runtime | 36 / 36 / 26 | 1.8.0 | 插件进程 | 动态加载 (DEX) | 0 | P5.3 | |
| DEX-Compiler | 36 / 36 / 24 | 1.8.0 | 插件进程 | 动态加载 (DEX 产物) | 0 | P5.3 | |
| R8-Compiler | 36 / 36 / - | 1.8.0 | 插件进程 | 动态加载 (DEX 产物) | 0 | P5.3 | |
| MCP-Server | 36 / 36 / 24 | 1.8.0 | 插件进程 | INTERNET (本地网络服务端), `specialUse` 前台服务 | 0 | P5.4 | |
| Screen-Color-Picker | 36 / 36 / 24 | 1.8.0 | 插件进程 | `mediaProjection` 前台服务, 悬浮窗 | 0 | P5.4 | |
| Three-Ember-Player | 36 / 36 / 24 | 1.8.0 | 插件进程 | `mediaPlayback` 前台服务, INTERNET, 后台音频 | 0 | P5.4 | |
| Three-Terra-Player | 36 / 36 / 24 | 1.8.0 | 插件进程 | `mediaPlayback` 前台服务, INTERNET, 后台音频 | 0 | P5.4 | |
| Three-Stone-AI | 36 / 36 / 24 | 1.8.0 | 插件进程 | INTERNET, NPU 特性声明 (若用), 内存上限 | 0 | P5.4 | |
| HTML-Previewer | 36 / 36 / 24 | 1.8.0 | 插件进程 | INTERNET, WebView | 0 | P5.4 | |
| Markdown-Previewer | 36 / 36 / 24 | 1.8.0 | 插件进程 | INTERNET, WebView | 0 | P5.4 | |
| Offline-Docs | 36 / 36 / 24 | 1.8.0 | 插件进程 | WebView (回环) | 0 | P5.4 | |
| Ace-Editor | 36 / 36 / 24 | 1.8.0 | 插件进程 + LuaLS 独立进程 | INTERNET, WebView, LSP (回环) | 0 | P5.4 | |
| MLKit-Barcode | 36 / 36 / 24 | 1.8.0 | 插件进程 | Binder, INTERNET | 0 | P5.5 | |
| MLKit-OCR | 36 / 36 / 24 | 1.8.0 | 插件进程 | Binder | 3 | P5.5 | |
| Yolo-NCNN | 36 / 36 / 24 | 1.8.0 | `:provider` | Binder | 36 | P5.5 | |
| MediaInfo | 36 / 36 / 24 | 1.8.0 | 插件进程 | Binder | 0 | P5.5 | |
| OpenCC | 36 / 36 / 24 | 1.8.0 | 插件进程 | Binder, INTERNET | 0 | P5.5 | |
| ImGui | 36 / 36 / 24 | 1.8.0 | 插件进程 (`:privilege_broker`) | Binder | 0 | P5.5 | |
| Archive-Manager | 37 / 36 / 24 | 1.8.0 | 插件进程 | 存储卷 / MediaStore | 0 | P5.5 (仅 target) | |
| Image-Tools | 36 / 36 / 24 | 1.8.0 | 插件进程 | 存储 | 0 | P5.5 | |
| Image-Viewer | 36 / 36 / 24 | 1.8.0 | 插件进程 | 存储 | 0 | P5.5 | |
| File-Inspector | 36 / 36 / 24 | 1.8.0 | 插件进程 | 存储 | 0 | P5.5 | |
| APK-Inspector | 36 / 36 / 24 | 1.8.0 | 插件进程 | 存储 | 0 | P5.5 | |
| Pinyin | 36 / 36 / 24 | 1.8.0 | 插件进程 | - | 0 | P5.5 | |
| Pinyin4j | 36 / 36 / 24 | 1.8.0 | 插件进程 | - | 0 | P5.5 | |
| Accessibility-Compat | 36 / 36 / 24 | 1.8.0 | 插件进程 | - | 0 | P5.5 | |

---

## 附录 F: 后续建议 (不阻塞本 Roadmap)

- androidx.core 1.19.0 (2026-06-03) 要求 compileSdk 37, 且 `core-ktx` 已并入 `core`; 当前 `gradle/libs.versions.toml` 的 `core-ktx = "1.15.0"` 是为 AGP 8.2.2 时代保留的过期钉子, P1 之后可在独立的依赖升级中解除 (D7).
- androidx.activity 1.14.0-alpha02 起 Compose 系列库要求 compileSdk 37.1 (次级 SDK); 届时需要 `compileSdk { version = release(37) { minorApiLevel = 1 } }` 语法, `Versions.kt` 与 `autojs6-platform-versions` 数据表都要支持 "37.1" 形式. 本机尚未安装 `platforms;android-37.1`.
- `usesCleartextTraffic="true"` 已被官方标记为未来移除, 应把允许明文的域迁入 `network_security_config.xml` (待决 Q7 选 (a) 时记于此).
- Android 18 起 `ACTION_SEND` / `ACTION_IMAGE_CAPTURE` 不再隐式授予 URI 权限, P2.15 的 `detectImplicitUriPermissionGrant()` 检查结果可提前修正.
- `FingerprintManager` 包在 API 37 已移除 (本仓库未引用); `InputMethodManager.SHOW_IMPLICIT` 之外的弃用由 lint 逐版跟踪.
- APK 签名方案 v3.2 (混合后量子, ML-DSA) 对内置 APK 打包器 / `modules/apk-signer` 是可选能力, 与本次无关.
- Advanced Protection Mode 对无障碍服务的限制若被实测确认, 需要在用户文档 FAQ 与插件中心 "无障碍兼容" 页面长期维护说明.

---

## 会话记录

### 2026-09-13 初稿

- 完成: 宿主与 42 个插件仓库的 SDK / 工具链 / manifest 事实收集; compileSdk 37 探针编译 (0 错误, 1 处新增弃用) 与 36 基线对照; `AVD_API_37` 的 `dumpsys platform_compat` 清单 (36 项门控) 与官方文档核对; 固定决策 D1-D4 (维护者以选择题拍板: 范围含全部插件, 两步走, inrt 同步升到 37, 允许探针编译) 与派生约定 D5-D11; 本 Roadmap 初稿与两份证据文件.
- 关键发现: 编译层面几乎零成本 (AGP 9.4.0 已满足 API 37, 全仓编译通过); 风险集中在 targetSdk 37 的运行时行为: 本地网络运行时权限 (含入站 accept, 影响 VS Code 扩展 / MCP / 脚本 socket), 后台音频 while-in-use 要求, `System.load` 只读要求 (OpenCV / 量化插件 staging 路径未设只读), 无锁 MessageQueue 与线程中断语义, `IntentSender` 的 BAL opt-in, CT / ECH 默认开启; inrt 从 29 跨到 37 需要单独的存储引导与旧版升级保护.
- 环境变化: 本会话为取兼容清单启动并关闭了 `AVD_API_37` (`-no-snapshot-save`, 未改动 AVD 状态); 会话开始时列出的 API 27 模拟器 (`emulator-5554`) 在启动新模拟器前已不在 `adb devices` 中, 原因未查.
- 未做: P0.2 覆盖开关与脚本, P1 起全部代码改动.
- 下次会话建议起点: P0.2 (覆盖开关, 供 P2 全程使用) 与 P1 (compileSdk 37, 改动极小) 可同一会话完成; 随后 P2.1 / P2.3 / P2.4 三项影响最大且互不依赖, 可并行推进.
