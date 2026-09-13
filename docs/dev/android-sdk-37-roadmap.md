# AutoJs6 Android SDK 37 (Android 17) 升级 Roadmap

本文是 AutoJs6 宿主 (含 inrt 打包模板与仓内 modules / plugin-api / test-apps) 以及同目录 42 个官方插件仓库把 `compileSdk` / `targetSdk` 从 36 升到 37 的可执行状态表.
以 2026-09-13 的本地代码快照 (宿主 `master@60b147716`, `VERSION_NAME=6.8.0`, `VERSION_BUILD=5280`) 与同日的 [探针记录](evidence/android-sdk-37-probe-20260913.md) (compileSdk 37 探针编译, API 37 模拟器 `dumpsys platform_compat` 清单) 为起点, 每个条目均可独立 Check 并落地.

2026-09-13 复核: [执行条件与证据校正](evidence/android-sdk-37-review-20260913.md). 随后已完成 P0.2 与 P1 本地实现和验证, 见 [P0.2 / P1 实施记录](evidence/android-sdk-37-p0-p1-20260913.md), 以及 P2.3 动态加载只读适配, 见 [P2.3 实施记录](evidence/android-sdk-37-p23-20260913.md). 当前正常配置为 compileSdk 37 / 宿主 targetSdk 36 / inrt targetSdk 29; P1 的 SDK 36 / 37 lint 差异为零, 但既有错误仍存在, 远程 CI 尚未验证, P1 不标记整体关闭. 当前 release 未启用 minify, P1 的 release 冒烟不构成 R8 优化验证. P2 其余条目及 P3-P6 尚未实施.

使用方式:

1. 每次会话开始时, 从 "阶段总览" 选取一个或多个未完成条目, 优先级按阶段顺序; 宿主先行, 被宿主进程加载或打包进用户 APK 的插件其次, 独立进程插件再次, 纯 JVM 插件最后.
2. 条目完成后勾选 `[x]`, 并在条目后追加证据 (提交 hash / 测试类名 / 设备型号 / lint 报告路径), 证据等级见附录 D.
3. 涉及 JS 公开 API 或权限模型的条目 (如 P2.1 的本地网络权限), 完成后必须同步 `AGENTS.md` 列出的关联仓库 (文档 / d.ts / Ace LSP 声明) 与 `.changelog` (10 语言) 并重新生成 README.
4. 附录 A 的待决事项在进入对应阶段前由维护者拍板, 拍板结果回填到第 1 节.
5. 附录 B 是指定 API 37 镜像中 36 项 targetSdk 门控变更的逐项评估, 不是 Android 17 全部行为变更的全集. `dumpsys` 只能证明变更名称 / ID / 配置存在, 不能证明行为语义或功能通过. 官方文档的核对状态 (VERIFIED / UNVERIFIED) 随条目标注; 页面未提及不能作为 "明确未变" 的证据, 网络失败时保留待核验状态.

---

## 1. 固定决策

D1-D4 已由维护者于 2026-09-13 以选择题形式确认, 后续不重复征询. D5-D11 是据此拟定的执行约定, 遇到源码证据不符时修正文档并记录原因, 不把派生约定当作额外的维护者拍板:

| 编号 | 决策 | 含义 |
| --- | --- | --- |
| D1 | 范围 = 宿主 + 全部 42 个官方插件仓库 | 宿主 (app 两个 flavor, 仓内 modules / plugin-api / test-apps / plugins 子项目随 `version.properties` 自动跟随) 先行; 42 个插件仓库作为 P5 逐仓推进, 每仓独立条目, 独立提交, 独立版本递增与 changelog. `AutoJs6-Gradle-Platform-Versions` 无需改动 (数据表已含 `37.0=9.1.1`), `AutoJs6-Official-Plugins-Index` 仅在插件发布时按既有流程刷新. |
| D2 | 两步走 | 宿主先把 `COMPILE_SDK_VERSION` 升到 37 (P1, `TARGET_SDK_VERSION` 保持 36) 单独提交并回归; 行为变更适配在 targetSdk 仍为 36 时借助兼容开关与临时 target 覆盖完成 (P2); 最后单独提交 `TARGET_SDK_VERSION=37` (P3). 每一步可独立回滚, 与 Archive-Manager 插件 2026-08-23 的做法一致. |
| D3 | inrt 模板 `TARGET_SDK_VERSION_INRT` 29 -> 37 | 用户打包的 APK 与宿主同步以 37 为目标, 在 P3 与宿主 targetSdk 同一提交切换. 由于 inrt 一次跨越 8 个 API 等级 (30-37), P3.3 单列 "打包应用 29 -> 37 清单", 存储引导与旧版升级保护见待决 Q4. |
| D4 | 探针编译作为 P0 基线 | 2026-09-13 已完成所列 Kotlin / Java 任务的 compileSdk 37 探针 (0 错误, 仅 1 处新增弃用告警) 与 36 基线对照, 结果记入 [探针记录](evidence/android-sdk-37-probe-20260913.md); P1 独立补齐 inrt / release / lint / 出包与设备验证. 源码复核确认 release 的 `isMinifyEnabled=false`, 因此 R8 优化不在当前产物路径中; 将来启用时另建基线. |
| D5 | 验证矩阵 | Android 17 证据只来自本机 AVD (`AVD_API_37` / `aj6-ps16k-37-x86_64`, `android-37.0` google_apis_ps16k x86_64 镜像); 真机 (Xiaomi 23046RP50C API 35, Sony XQ-DQ72 API 33, Redmi 22120RN86C API 33, Sony XQ-AT72 API 31, Sony G8441 API 28) 提供 targetSdk 提升后在旧系统上的回归; 16 KB 门禁 (`verifyNativePageAlignment`) 在每次 assemble 时继续生效. 无 Android 17 真机, 有则由维护者回填. |
| D6 | 落地位置 = master, 每个子条目一个提交 | 与 a11y / 16 KB Roadmap 一致; 提交时按显式文件清单暂存, 不 `git add -A` (多会话工作区). |
| D7 | 不捆绑无关依赖升级 | 本 Roadmap 只改 SDK 版本与由此强制或直接相关的代码; androidx.core 1.19 (要求 compileSdk 37) 等 "被解锁" 的升级记入附录 F, 不在本 Roadmap 实施. |
| D8 | `autojs6-platform-versions` 保持 1.8.0 | 1.8.0 与 1.8.1 的 `android-api-agp-compat.properties` 相同 (`37.0=9.1.1`), 当前解析出的 AGP 9.4.0 / Gradle 9.7.1 / Kotlin 2.4.0 / KSP 2.3.12 全部满足 API 37; 不因本次升级改动 `settings.gradle.kts` 的插件版本. |
| D9 | changelog 写法 | SDK 日志参考既有 35 -> 36 升级记录, 最终分类以各仓库 `AGENTS.md` 为准; 当前宿主规定 `dependency` 仅统计 Gradle 依赖声明, SDK 平台升级记入 `improvement` (例如 "编译 SDK 版本调整为 37", 英文 "Updated compile SDK to 37"). P1 只描述编译版本, P3 再描述宿主与打包应用的目标版本及用户可见变化; 行为适配按性质写入 `fix` / `improvement`. 不提前宣称 targetSdk 已升级. |
| D10 | `app/src/main/java/com/stardust/**` 兼容包保持冻结 | 探针编译显示 android.jar 37 未在该包内引发错误; 若后续 lint / 构建暴露必须修改的项, 仅允许 35 -> 36 先例 (`2ab13f3c7` 注释掉 `MainThreadProxy` 的 `Thread.stop` 系列) 那种 "最小编译修正", 不做其它改动. |
| D11 | 插件仓库的节奏 | 纯 JVM 且无行为暴露的插件 (附录 E "暴露" 列为空) 可在一次发布内同时完成 compileSdk 与 targetSdk; 存在本地网络 / 动态加载 / 后台音频 / 屏幕捕获暴露的插件与宿主一样两步走. 各仓库脏工作区 (附录 E) 先由维护者处理或按会话惯例分开提交, 本 Roadmap 的提交不得混入. |

由 D1-D3 派生的硬约束:

- SDK 版本只在 `version.properties` 一处定义 (`COMPILE_SDK_VERSION` / `TARGET_SDK_VERSION` / `TARGET_SDK_VERSION_INRT` / `MIN_SDK_VERSION`), 经 `build-logic/convention/src/main/kotlin/org/autojs/build/Versions.kt` 暴露为 `versions.sdkVersion*`, 47 个构建脚本 (app, modules, plugin-api, test-apps, plugins/apk-builder-template) 全部经此读取; 任何条目都不得在子项目脚本中写死数字.
- 宿主 `MIN_SDK_VERSION=24` 不变, 各插件保留实际有效的 minSdk (见附录 E); P2 中为 API 37 新增的调用一律以 `Build.VERSION.SDK_INT >= 37` 分支或 `*Compat` 包装, 不抬高 minSdk.
- P2 的代码改动必须在宿主 targetSdk 36 / inrt targetSdk 29 下也能正确运行. 每项按真实门控条件判断: 新权限走系统版本 / 目标版本分支, 对所有应用生效的行为在 Android 17 上也必须处理, 不能统一加 `targetSdk >= 37` 后跳过. 兼容开关与 target 覆盖包分别记录条件.

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
| 初始 `version.properties`: `COMPILE_SDK_VERSION=36`, `TARGET_SDK_VERSION=36`, `TARGET_SDK_VERSION_INRT=29`, `MIN_SDK_VERSION=24`; P1 后 compile 为 37, 其余保持 | 三个值分别对应 P1 / P3 / P3 (D3) 的改动点; `Versions.kt` 以整数读取, 因此写 `37` 而非 `37.0`; debug 临时 target 覆盖由独立 Gradle 属性控制 |
| 工具链 (由 `autojs6-platform-versions` 1.8.0 从 `version.properties` 与其数据表解析): Gradle 9.7.1, AGP 9.4.0, Kotlin 2.4.0, KSP 2.3.12; `android-api-agp-compat.properties` 中 `37.0=9.1.1` | API 37 的最低 AGP 是 9.1.1 (官方 AGP 9.1 发行说明, VERIFIED), 当前 9.4.0 满足, 无需 `android.suppressUnsupportedCompileSdk`; AGP 9.4 要求 Gradle >= 9.6.0, 已满足 |
| 本机 SDK: `platforms/android-37.0` (Platform.Version=17, rev 2, ExtensionLevel 22, 正式版), `build-tools/37.0.0`; AVD `AVD_API_37` 与 `aj6-ps16k-37-x86_64` 均为 `android-37.0;google_apis_ps16k;x86_64` | 平台目录名是 `android-37.0` 而非 `android-37`, AGP 9.4 对 `compileSdk = 37` 已正确解析 (Archive-Manager 先例: 2026-09-12 release APK `compileSdkVersion='37' compileSdkVersionCodename='17'`) |
| Android 17 正式版 2026-06-16 发布 (VERIFIED); Google 博客称 Play 将于 2027-08 要求新应用与更新以 API 37 为目标 (VERIFIED 引文, 具体日期 UNVERIFIED) | 本次升级不受外部截止期驱动, 但插件舰队与宿主同步可避免 2027 年集中返工 |
| 探针编译 (compileSdk 37, target 36): 所列任务 0 错误, 仅新增 1 处弃用告警 (`ui/edit/keyboard/SymbolsSettingsActivity.kt:1110` `InputMethodManager.SHOW_IMPLICIT`); 36 基线对照无其它差异; javac 告警 0 | P1 的编译修正预期较少; inrt / release / R8 / lint / 测试结果不能由此推出; 本机 android.jar 的 fingerprint 类差异已另行核验 (SOURCE, 见复核记录) |
| `build-logic/ksp-version-codes-processor/src/main/resources/version-codes.csv` 已含 `CINNAMON_BUN,Android 17,Cinnamon Bun,17,37,"June 16, 2026"` (`4bdb3bb68`, 2026-03-14); 文档仓库 `api/util.md` 已同步 | `util.versionCodes` / `SdkVersionUtils.sdkIntToString(37)` 已能解析 "17", 无需改动 |
| 上一次升级提交 `2ab13f3c7` (2025-09-26, 35 -> 36): 24 个文件, 主要是 android.jar 删除 / 弃用 API 的编译修正, changelog `dependency` "升级 Android SDK 版本 35 -> 36" | D9 的写法来源; 本次编译修正量远小于上次 |
| CI (`.github/workflows/android.yml`): JDK 21, 未显式安装 SDK 平台, 依赖 AGP 自动下载; `run-gradle-ci.sh` 跑单元测试子集, `assembleApkBuilderTemplateDistribution`, 16 KB 校验 | P1.6 需确认 runner 能取到 `platforms;android-37.0` (预期由 AGP `sdkDownload` 自动完成), 否则补 `sdkmanager` 步骤 |
| Manifest (`app/src/main/AndroidManifest.xml`): `largeHeap` (291), `requestLegacyExternalStorage` (293), `usesCleartextTraffic` (297), `tools:targetApi="tiramisu"`, 无 `screenOrientation` / `resizeableActivity` / `enableOnBackInvokedCallback` / `<property>` 大屏豁免; 前台服务类型 `specialUse` x2 (593, 603, 带 `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`), `microphone` (627), `mediaProjection` (948); 权限含 `INTERNET`, `SYSTEM_ALERT_WINDOW`, `MANAGE_EXTERNAL_STORAGE`, `READ_SMS` / `RECEIVE_SMS`, `BLUETOOTH_*`, `QUERY_ALL_PACKAGES` 等 74 项 | 未声明 Android 16 大屏豁免, 移除该 manifest 豁免预期无增量; 服务类型 / 启动条件仍需 P2.2 / P2.15 核验, 不能由当前声明推出 Android 17 无变化 |
| `network_security_config.xml`: `base-config` 仅系统信任锚, `githubusercontent.com` 域禁明文并附加 `@raw/isrg_root_x1`; 无 `certificateTransparency` / `domainEncryption` 元素 | P2.6 的改动点 (CT 默认开启, ECH 默认开启) |
| 初始 `app/build.gradle.kts`: `lint.abortOnError=false`; app / inrt vital analysis 禁用, 模板分发还会跳过 app vital; CI 显式排除 inrt vital analysis | P0.2 已增加 `-Pautojs.sdk.lint.verify=true`, 启用实际 analysis / report 并在错误时失败; 普通构建保持既有配置. SDK 36 / 37 均有 debug 547 项 Error / Fatal, 两个 release 各 1 项既有 Fatal; 差异为零不等于 lint 清零 |

### 3.2 Android 17 对宿主的影响矩阵

来源: 附录 B (模拟器 36 项门控变更) + 官方 "apps targeting 17" / "all apps" 页面 (核对状态随条目). 按影响排序, 每行对应一个 P2 条目.

| 编号 | 变更 | 生效条件 | 宿主暴露点 | 条目 |
| --- | --- | --- | --- | --- |
| I1 | 本地网络权限 `android.permission.ACCESS_LOCAL_NETWORK` (VERIFIED): targetSdk >= 37 默认阻断到局域网地址的出站 TCP / UDP, 入站 TCP accept, mDNS `.local` 解析; targetSdk <= 36 随 `INTERNET` 隐式授予; 同一 profile 内回环不受此权限限制. 跨 profile 的回环在 Android 17 上另受限制, 与 targetSdk 无关 ([官方说明](https://developer.android.com/about/versions/17/behavior-changes-all#block-cross-profile-loopback-traffic)) | 权限系统, 非兼容开关 (`RESTRICT_LOCAL_NETWORK` 仍为 disabled, 仅测试用); 跨 profile 另测 | VS Code 扩展连接两种模式 (`pluginclient/JsonSocketServer.kt:24` `ServerSocket`, `pluginclient/JsonSocketClient.kt:64-65` 连接电脑); 脚本 `http.*` / `web.*` / `$web.newWebSocket` (`runtime/api/augment/web/Web.kt`, `core/web/WebSocket.kt`); MQTT (`org.eclipse.paho.android.service.MqttService`); Node 桥 (`engine/NodeBridgeProtocol.kt` 的 `newWebSocket`); 用户脚本自建 socket | P2.1 |
| I2 | 后台音频强化 (VERIFIED): 全体应用需要可见 Activity 或非 `SHORT_SERVICE` 前台服务; targetSdk 37 的后台路径还需 while-in-use 能力, 但精确闹钟授权 + `USAGE_ALARM` 可豁免 WIU 条件. 播放 / 音量调用可静默失败, 焦点请求返回 `AUDIOFOCUS_REQUEST_FAILED`; `throw` 测试模式比默认规则更严 ([官方说明](https://developer.android.com/about/versions/17/changes/bg-audio)) | 全体 + target 37 加严 | `runtime/api/Media.java`, `runtime/api/Device.java` (音量), `runtime/api/tts/*.kt` (TTS), Node 桥音频; 定时任务 / 开机自启 / 广播触发的脚本在 `AppForegroundService` (`external/foreground/AppForegroundService.kt`) 下运行 | P2.2 |
| I3 | 动态加载的原生库必须只读 (`THROW_ERROR_FOR_WRITABLE_DCL`, VERIFIED): `System.load()` 可写文件抛 `UnsatisfiedLinkError` | target 37 | OpenCV / ImageQuant stager 原已尝试 `setWritable(false, false)`, 但忽略结果; P2.3 改为在写入临时文件前及返回最终文件前强制验证只读, 修复旧缓存. `AndroidClassLoader` 的 DEX 已有只读处理, 本轮另修复 API 24 / 25 优化输出覆盖原始 DEX; Ace Editor 从已安装的插件 APK 构造 `PathClassLoader` | P2.3 (完成, 见实施记录) |
| I4 | 无锁 `MessageQueue` (`USE_NEW_MESSAGEQUEUE`, VERIFIED) 公开 API 不变, 新实现中反射 `mMessages` 恒为 null; `LOOPER_CLEARS_THREAD_INTERRUPTED` 清除消息派发时的原生中断标记; `OVERRIDDEN_THREAD_START_METHOD` 使线程池绕过工作线程 start 覆写, 仍执行 run, 普通直接 start 调用不变 (后两项为 PLATFORM_CONFIG + DEVICE-37 对照结论) | target 37, 可在 target 36 调试包强制测试 | `Loopers` 的公开 IdleHandler、`MainThreadProxy` / `TimerThread` 中断、`exit()` / `threads.*` / `sleep()`; 宿主 Thread 子类未覆写 start. P2.4 修复了取消回调后历史时间阻止脚本退出的既有问题; 用户反射限制见附录 F | P2.4 |
| I5 | 后台活动启动: `IntentSender` 的 Activity 启动需核对 creator / sender 的显式授权 (VERIFIED); `ASM_RESTRICTIONS` 在本镜像为 target 37 门控且未标 disabled, 行为待核验; BAL 与后台启动前台服务是两套规则, 悬浮窗例外分别检查 | target 37 | `ui/main/scripts/AndroidPackageSessionInstaller.kt:173,176` (`MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS` / `ALLOWED`), `external/shortcut/ShortcutManager.java:46` 与 `ui/settings/LauncherShortcutsPreference.kt:35-68`, `ui/shortcut/ShortcutCreateActivity.java:161-163` (`requestPinShortcut(IntentSender)`), `ui/main/drawer/DrawerFragment.kt:160` (`StartIntentSenderForResult`); 脚本 `app.launch` / `app.startActivity` 从后台启动 | P2.5 |
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

需要继续核验的部分 (UNVERIFIED): 前台服务类型 / 超时, `SYSTEM_ALERT_WINDOW` / `TYPE_APPLICATION_OVERLAY`, MediaProjection, JobScheduler / AlarmManager, 预测性返回, 边到边, `android.accessibilityservice` 包与最低可安装 targetSdk. 当前主文档未列某项新限制, 不等于该项没有变化; P2.15 用 API diff / 对应专题文档 / 实机回归分别给出结论. 已有 Android 14-16 限制仍纳入 inrt 29 -> 37 回归.

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

42 个插件仓库全部 `TARGET_SDK_VERSION=36`, 除 Archive-Manager (`COMPILE_SDK_VERSION=37`) 外 `COMPILE_SDK_VERSION=36`; 41 个仓库使用 `autojs6-platform-versions` 1.8.0, Bun-Runtime 为 1.7.4 (且 `MIN_SDK_VERSION=33`). 属性文件不一定等于实际构建值: R8-Compiler 的 app / 两个 plugin-api 构建脚本硬编码 compileSdk 36, app 硬编码 targetSdk 36 / minSdk 24; Paddle v4 / v5 / v6 app 实际 minSdk 为 26. P5 必须核查消费点和产物. 初稿快照中 10 个仓库工作区有未提交改动 (Bun 18, Lua 30, MLKit-OCR 3, Paddle v3 40, v4 / v5 / v6 各 6, Rapid v3 45, Rapid v6 56, Yolo 36 个文件); 开始每仓工作时重新检查. 逐仓的进程模型, 权限暴露与阶段归属见附录 E.

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
| P0 | 基线与工具 | 无 | 宿主 | 完成 (2026-09-13): 覆盖开关, 分发拒绝, compat 工具, 有效 lint 基线 |
| P1 | 宿主 compileSdk 37 | P0 | 宿主 | 本地实现 / 构建 / 测试 / 冒烟完成; lint 零增量但有既有错误, 远程 CI 待验证 |
| P2 | 宿主 targetSdk 37 行为变更适配 (target 仍为 36) | P0.2 (覆盖开关), P1 | 宿主 | 进行中: P2.3 已完成, 其余条目待实施 |
| P3 | 宿主 targetSdk 37 切换 (app + inrt) | P2 全部 | 宿主, APK-Builder-Template (只读验证) | 未开始 |
| P4 | 关联项目, 文档与 CI | P1 / P3 各自完成后 | Documentation, TypeScript-Declarations, Ace-Editor (LSP), Offline-Docs, VSCode-Ext (仅文档) | 未开始 |
| P5 | 官方插件舰队 (42 仓) | P1 (工具链验证), P2.1 / P2.3 的方案 | 42 个插件仓库 | 未开始 |
| P6 | 验收与关闭 | P1-P5 | 全部 | 未开始 |

P2 可按影响矩阵分批推进: P2.6 的局域网 TLS 验证先满足 P2.1 权限前提; P2.16 的 inrt 验证复用 P2.1 / P2.2 等方案. P5 与 P2 / P3 可交错 (插件的 compileSdk 步骤不依赖宿主 targetSdk 切换), 但 P5 中涉及本地网络与动态加载的插件应复用 P2.1 / P2.3 落定的方案.

---

## P0: 基线与工具

目标: 固定事实, 让 P2 每个条目都能在 targetSdk 36 的代码上以 targetSdk 37 的行为验证.

### P0.1 探针与兼容清单 (完成 2026-09-13)

- [x] compileSdk 37 探针编译 (所列任务 0 错误, 新增 1 处弃用告警) 与 36 基线对照. (ANDROID_COMPILE: [探针记录](evidence/android-sdk-37-probe-20260913.md) 第 1 节; 历史摘要, 本次复核未重跑)
- [x] `AVD_API_37` 上 `dumpsys platform_compat` 全文入库, 提取 36 项 `enableSinceTargetSdk=37` 变更并逐项评估. (PLATFORM_CONFIG: [platform_compat 全文](evidence/android-sdk-37-platform-compat-api37-20260913.txt); DOCS: 附录 B, 行为语义未全部核验)
- [x] 初次官方资料收集已写入第 3 节与附录 B, 复核修正见 [执行条件与证据校正](evidence/android-sdk-37-review-20260913.md). (DOCS; 不代表所有 UNVERIFIED 项已关闭)

### P0.2 targetSdk 覆盖开关 (完成 2026-09-13)

- [x] `Versions.kt` 增加 `autojs.sdk.target.override`, 校验整数 / minSdk 至 max(compileSdk, 37), 并打印 debug 覆盖提示. 两个 target 37 debug APK 均已检查 badging 并安装; 正常 app / inrt 为 36 / 29. `SdkTargetOverrideGuard` 作为实际任务依赖拒绝 release / Runtime Kit / 模板分发, 覆盖缩写与聚合路径; 11 项参数 / 任务拒绝检查通过. (SOURCE; ANDROID_BUILD: [实施记录](evidence/android-sdk-37-p0-p1-20260913.md) 第 2 节)
- [x] [compat.sh](../../tools/sdk37/compat.sh) 与 [dump_compat.sh](../../tools/sdk37/dump_compat.sh) 保存原覆盖状态, 检查 debuggable 与整批 change, 每条 adb 命令只操作一个 change / package; 已在 API 37 对两个开关 enable / reset 并核对恢复, 非法 change 和 release 包拒绝通过. 音频 / 内存仅提供独立命令说明, 本次未修改设备级控制. (SOURCE; DEVICE-37: 实施记录第 2 节)
- [x] `-Pautojs.sdk.lint.verify=true` 启用 app / inrt vital analysis, 绕过模板分发跳过条件并令错误产生非零退出码; 实际运行 debug / 两个 release 报告. AGP 9.4 的 vital 输出为 text, 对比工具检查其诊断计数. (SOURCE; LINT: 实施记录第 3 节)
- [x] SDK 36 有效 lint 基线存档 `build/sdk37-lint-36/`, 包含命令日志、报告、版本与 lint model; 不新增禁用规则. debug 546 Error + 1 Fatal / 2437 Warning / 3 Hint, app / inrt release 各 1 Fatal. (LINT: 实施记录第 3 节)

P0 验收: app / inrt 覆盖包的 `targetSdkVersion` 为 37, 正常包仍为 36 / 29; release 覆盖被拒绝; 脚本在 AVD 上跑通并清理本次 override; 有效 lint 基线存在.

---

## P1: 宿主 compileSdk 37

目标: `COMPILE_SDK_VERSION=37`, 编译 / 测试 / 出包与运行回归通过, lint 无新增问题并显式记录既有问题, CI 获得独立远程证据; 宿主 targetSdk 保持 36 / inrt 保持 29. 本地实施证据见 [实施记录](evidence/android-sdk-37-p0-p1-20260913.md), 不将既有 lint 失败或未运行的 CI 标作通过.

- [x] `version.properties`: `COMPILE_SDK_VERSION=37`, 仓内构建脚本统一跟随; 宿主 / inrt 正常 target 仍为 36 / 29, 工具链与依赖版本保持. (SOURCE; ANDROID_BUILD: 实施记录第 1 / 4 节)
- [x] `SymbolsSettingsActivity` 和 AndroidTest 的 `NativeEditorRegressionSmokeTest` 使用 `showSoftInput(editText, 0)`, 保持已有焦点 / 调用时序. 实际符号设置页在 API 24 / 28 / 35 / 37 均验证输入框获得焦点、键盘显示、Back 隐藏. ([API 参考](https://developer.android.com/reference/android/view/inputmethod/InputMethodManager#SHOW_IMPLICIT); SOURCE; ANDROID_COMPILE; DEVICE: 实施记录第 5 节)
- [x] SDK 37 debug + app / inrt release lint 实际运行并存档 `build/sdk37-lint-37/`; [compare_lint.py](../../tools/sdk37/compare_lint.py) 对照 SDK 36 的 ID / 严重性 / 消息 / 文件及重复计数, 三份报告新增与减少均为零. 两轮 Gradle 均因既有错误退出 1, 不是 clean lint; 无新增 suppress / baseline / disabled issue. (LINT: 实施记录第 3 节)
- [x] 单元测试 3166 项: 3161 通过 / 5 跳过 / 0 失败; 其中 app 2533 通过 / 5 跳过. 对照 settings 核对 10 modules / 29 plugin-api / 1 plugins / 6 test-apps 均进入任务选择器, 21 项任务为 NO-SOURCE, 不算用例通过. AndroidTest Kotlin 编译通过, 含上述弃用修正后的重编译. (JVM; ANDROID_COMPILE: 实施记录第 4 节)
- [x] 正常 app debug / release 各 5 个 ABI / universal APK, inrt release 5 个及独立 inrt debug universal APK, 均为 compile 37 且 target 分别为 36 / 29; 5 份 Runtime Kit、根任务 `:assembleApkBuilderTemplateDistribution` 和 16 KB 门禁通过. 不在 Kit / 模板构建命令中直接追加 `assembleInrt*`: 现有脚本会因此关闭 ABI splits, 应通过 Kit 依赖构建 inrt release, debug 单独执行. (ANDROID_BUILD: 实施记录第 4 节)
- [x] API 28 / 35 / 37 debug 与 API 37 release 各通过 a11y 53 / 53, UI / overlay / 基础 MediaProjection; API 35 使用全屏授权通过. 该平板单应用选择本宿主后返回未授权, 保留为 P2.15 待定位观察项, 不推断为 SDK 升级引入. release 当前 `isMinifyEnabled=false`, 覆盖实际出包与运行路径, 无 R8 优化证据. (DEVICE; DEVICE-37: 实施记录第 5 节)
- [x] 本地 CI 入口修正并验证: editor gate 不再硬编码契约 VERSION=1, 验证正整数版本与 MIN=1 / MAX=VERSION; 当前契约为 5, 三项无效契约检查均被拒绝. CI 所列测试 / conformance assemble / Runtime Kit / 模板 / native 检查本地通过. (SOURCE; JVM; ANDROID_BUILD: 实施记录第 4 节)
- [ ] 远程 CI: 推送后的 `android.yml` 记录链接与 SDK 自动下载结果; 若 runner 无法获取平台再补显式 sdkmanager 安装. `release-runtime-kit.yml` 会上传 Release 资产并触发插件仓库, 其构建步骤已本地验证, 发布工作流留待正常发布时验证, 不为 P1 检查提前发布. (ANDROID_BUILD: 远程 CI / 正常发布链接)
- [x] changelog 按 D9 在 `improvement` 描述 compileSdk 37 (10 语言), gate 修正在 `fix`; 日期 2026/09/13, 已执行 `.python/generate_markdown.py` 生成 README / CHANGELOG. 本地提交 `chore(sdk): raise compileSdk to 37 (roadmap P1)`, 按显式文件与共享日志条目暂存. (DOCS)

P1 本地验收已记录; 整体关闭仍需远程 CI 证据, 发布工作流随正常发布补验. 既有 lint 错误清单持续可见, 不把零增量改写为全量 lint 通过. P2 的本地行为适配可使用已验证工具继续, 不提前切换持久 target.

---

## P2: 宿主 targetSdk 37 行为变更适配

目标: 在 targetSdk 仍为 36 的代码上完成全部适配, 每项用 P0.2 的覆盖开关或兼容开关在 `AVD_API_37` 上验证 "target 37 行为正确" 且真机 "target 36 行为不变". 条目按第 3.2 节影响矩阵编号.

### P2.1 本地网络权限 (I1)

- [ ] 事实核对: target 37 debug 包先声明 `ACCESS_LOCAL_NETWORK`, 确认合并 manifest 与实际包名后再做 grant / revoke 对照 (未声明的权限不能直接授予). 分别执行 VS Code 服务端 / 客户端, LAN TCP / UDP / WebSocket, 同 profile 的 `127.0.0.1` / `[::1]`, 记录拒绝, 授予, 撤销及重新进入功能的结果. HTTP 明文策略, TLS 证书, AVD NAT / 主机防火墙先独立排除, 不能把所有连接错误归因于 LNP. (DEVICE-37: 失败与恢复的两组日志)
- [ ] manifest 声明 `android.permission.ACCESS_LOCAL_NETWORK`; 新增 `permission/LocalNetworkPermission.kt` (沿用 `PostNotificationsPermission` 的 `Base` / `AbleToUrge` 结构, 常规 target <= 36 兼容路径不主动申请新权限, target 37 走标准运行时权限流程). 兼容开关强制测试的条件另外记录. (SOURCE; DEVICE-37)
- [ ] 申请时机 (待决 Q1, 默认按推荐项): VS Code 扩展连接入口 (`DevPluginService` 两种模式) 在启动前确保权限, 缺失时弹出说明并引导; 抽屉与设置页增加权限开关行 (与 "显示在其它应用上层" 同款 UI); 脚本层 `http` / `web` / socket 调用不主动弹窗, 但在目标为局域网地址且权限缺失时抛出带解释的错误 (文案 x 11 文件, 按 name 升序), 并提供 `runtime.requestPermissions(["access_local_network"])` (若既有权限申请 API 已支持通用权限名则复用, 不新增函数). (SOURCE; DEVICE-37)
- [ ] MQTT (`MqttService`) 与 Node 桥的局域网路径: 复测 broker 在局域网时的连接错误提示. (DEVICE-37)
- [ ] 对拒绝后重试、不再询问、既有 Nearby Devices 授权组以及 target 36 -> 37 覆盖安装分别记录权限状态; 系统选择器适用于用户选择的设备, 不能替代任意脚本 socket / 服务端权限. 工作资料中的宿主与个人资料中的插件若依赖回环, 单列跨 profile 受限用例, 不承诺 localhost 在所有安装组合中可达. (DEVICE-37)
- [ ] 文档: 若 JS 层出现新权限名或新错误码, 同步文档 / d.ts / LSP (P4); changelog `feature` (权限流程) 与 `improvement` (错误提示). (DOCS)

### P2.2 后台音频 (I2)

- [ ] 事实核对: 先在默认系统状态对 target 36 / 37 包分别测试 (a) 前台启动后切后台, (b) 定时任务, (c) 开机 / 广播入口的播放、焦点、音量、TTS; 对精确闹钟授权 + `USAGE_ALARM` 独立测试. 然后在可恢复的专用 AVD 上用 `cmd audio set-enable-hardening throw` 定位失败. `enable` / `throw` 会对所有 target 强制 WIU 且覆盖闹钟例外, 不能用其结果断言默认行为; 保存 `AudioHardening` 日志并恢复设备原状态. (DEVICE-37)
- [ ] 策略 (待决 Q3): 验证现有 `specialUse` 服务在用户可见 / 用户交互时启动后的能力; 按后台播放实际用途评估 `mediaPlayback` 类型. 诊断覆盖 Android 17 的全体应用规则及 target 37 的额外规则, 不能仅凭服务存活推断 WIU, 也不能假设存在公开的 WIU 查询 API. 定时任务 / 开机后无法自动播放的路径给出用户可执行的恢复入口或明确记录已知限制; 只打印警告不能算功能通过. TTS 另外记录实际合成 / 播放进程. (SOURCE; DEVICE-37)
- [ ] 定时任务 / 开机自启入口的前台服务启动方式复核 (`timing/**`, `external/foreground/**`), 确认与 Android 15 的 `FGS_BOOT_COMPLETED_RESTRICTIONS` / `FGS_SAW_RESTRICTIONS` 组合下仍可启动 `specialUse` 服务. (DEVICE-37; DEVICE: API 35 真机)
- [ ] changelog `improvement`. (DOCS)

### P2.3 动态加载只读 (I3)

- [x] OpenCV / ImageQuant stager 强制验证只读设置的返回值与 `canWrite()`: 临时文件先打开写入句柄, 再设只读并写入, 发布后再次校验; 命中旧缓存时也修复并校验. 失败复用各自 `NATIVE_LIBRARY_EXTRACTION_FAILED`, 不再交给 `System.load` 才发现. 原有代码已尝试去除写权限, 本项修复的是忽略失败结果的缺口. (SOURCE: `a784273d2`; JVM: 38/38; DEVICE-37: target 36 + `THROW_ERROR_FOR_WRITABLE_DCL`, 首次及旧缓存两轮各 3/3, target 37 默认行为 3/3; DEVICE: API 24 3/3)
- [x] 其余加载点复核: `app/src/main` 的 `System.load` 仅上述两处; `AndroidClassLoader` 有 3 个 `DexClassLoader` 构造点; Ace Editor 读取已安装的 APK; Node / Lua / QuickJS / Bun / Python 宿主侧为 Binder 调用, 未发现同类原生库 staging. 仓内 Paddle `OpenCLGuard` 另有一个 `System.load`, 仅尝试固定系统 / vendor / odm / apex 库路径. 插件进程内部仍由 P5 逐仓核验. (SOURCE: [调用点与边界](evidence/android-sdk-37-p23-20260913.md#调用点复核))
- [x] 实际脚本 `runtime.loadDex` / `runtime.loadJar` 在 API 37 target 36 + `THROW_ERROR_FOR_WRITABLE_DCL` 与 target 37 默认行为下通过; `tools/sdk37/smoke-dynamic-loading.js` 补充同一进程首次加载 / 缓存复用探针. (DEVICE-37: [测试矩阵](evidence/android-sdk-37-p23-20260913.md#设备验证))
- [x] 旧系统回归发现并修复 API 24 / 25 原始 DEX 输入与 ART 优化输出重合: 3 个构造点统一使用独立 `optimized` 子目录, API 26+ 保持原路径. API 24 / 37 的 `AndroidClassLoaderRawDexDeviceTest` 各 1/1, 检查两次加载后的原始 / 缓存字节和只读状态; API 24 实际 DEX / JAR 两轮调用通过. Java 8 JAR 在旧系统内置编译器中的既有限制单列于证据, 不作为加载适配通过的推论. (SOURCE; ANDROID_BUILD; DEVICE; DEVICE-37)
- [x] 两项 `fix` 日志分别写入当前版本 10 语言 JSON, 已运行 `.python/generate_markdown.py` 更新 README / CHANGELOG. 无 JS API 签名或权限模型变化, 无需同步文档 / d.ts 关联仓库. (DOCS: [P2.3 实施记录](evidence/android-sdk-37-p23-20260913.md))

### P2.4 Looper / 线程 (I4)

- [x] API 37 / target 36 默认、三个开关分别启用、组合启用, 以及实际 target 37 默认状态下, 每组 14/14 通过. 其中 12 个真实 Rhino 脚本覆盖定时器、线程池、sleep、exit、events、UI、悬浮窗、阻塞查找取消和 MainThreadProxy 中断; 组合 / target 37 持续 interval 各运行 2 分钟并正常退出. API 24 同组 14/14 通过. 原样执行 `sample/accessibility/smoke-legacy-api.js`, API 37 target 36 默认 / 组合、target 37 默认和 API 24 各 53/53. (DEVICE; DEVICE-37: [设备矩阵](evidence/android-sdk-37-p24-20260913.md#设备矩阵))
- [x] 按当前声明与继承关系复查 start 方法, 修正初始 "8 个文件" 估计: ThreadCompat、两层 TimerThread、FlowWorkerPool.WorkerThread 均未覆写 start, 初始化位于构造或 run; MainThreadProxy 是包装类, Media 覆写的是 MediaPlayer.start, 录制 / 会话 / 服务入口不是 Thread.start. 平台探针确认线程池 start/run 为 0/1 时真实 threads.pool 仍正常工作. (SOURCE; DEVICE-37: [复核表](evidence/android-sdk-37-p24-20260913.md#start-覆写与非-sdk-接口复核))
- [x] 修复基线复现的脚本不退出问题: 取消的远期回调留下历史最大时间, 导致空队列无法再次检查退出. Timer 改为登记实际排队 / 执行中的回调, 取消或完成时唤醒检查, 线程退出清理 Timer, UI / 直接 postDelayed 同样纳入. 取消 60 秒回调由 20 秒内无法结束变为正常即时结束; JVM 6/6, app / instrumentation 两种 target 与正常 inrt debug 构建通过, 16 KB 门禁通过. (SOURCE; JVM; ANDROID_BUILD; DEVICE; DEVICE-37)
- [x] 附录 F 记录新 MessageQueue 的 mMessages 与 target 37 static final 反射限制及生效条件. 已检查关联 util.java / threads 文档, 无受影响示例或 JS API 签名需要同步; P4 统一发布迁移提示. 当前 6.8.0 的 fix 日志已更新 10 语言并生成 README / CHANGELOG. (DOCS: [P2.4 证据](evidence/android-sdk-37-p24-20260913.md), [复跑工具](../../tools/sdk37/README.md#looper-and-thread-compatibility))

### P2.5 后台活动启动 (I5)

- [ ] 逐调用点区分 PendingIntent 的创建方、发送方与目标类型. 安装器 `createPendingIntentOptions()` 是 creator opt-in, token 交给系统 `PackageInstaller.Session.commit()` 发送, 代码明确支持界面离开前台后的确认回调; 保留此需求, 不能直接将 `ALLOW_ALWAYS` 改为 `ALLOW_IF_VISIBLE`. SDK 34 / 35 的 `ALLOWED` 兼容分支也不能改用仅 SDK 36 才存在的常量. 自己实际发送的 Activity token 优先使用可见条件 opt-in. (SOURCE)
- [ ] `ShortcutUtils` / `external/shortcut/ShortcutManager` 的成功回调使用 `PendingIntent.getBroadcast()`, 先确认是否真的启动 Activity; `DrawerFragment` 的 `StartIntentSenderForResult` 核对 ActivityResult 实际发送路径与 options 传递. 不能看到 `IntentSender` 就统一加 Activity BAL 授权. (SOURCE)
- [ ] `am compat enable COVER_INTENT_SENDER` + `StrictMode.VmPolicy.detectBlockedBackgroundActivityLaunch()` (debug 构建) 下: 安装 APK 会话, 固定快捷方式, 抽屉的 IntentSender 流程, 脚本从后台 `app.launch` / `app.startActivity` (悬浮窗豁免), 均无 "Background activity launch blocked". (DEVICE-37)
- [ ] `ASM_RESTRICTIONS` 的 dump 含 `enableSinceTargetSdk=37; overridable`, 未含 `disabled`; 核对最终镜像的默认状态与行为后复测跨应用启动, 分别记录默认 / enable / disable 结果, 不把 `overridable` 解释为默认关闭. 是否需要 `allowCrossUidActivitySwitchFromBelow` 由调用链证据决定. (PLATFORM_CONFIG; DEVICE-37)
- [ ] changelog `fix`. (DOCS)

### P2.6 网络安全配置 (I6)

- [ ] 按附录 C 分别启用 CT / ECH 开关. 当前 NSC 仅信任系统 CA, 安装用户 CA 本身不会使其受信任; 先记录 target 36 的证书链基线, 再用独立 debug 域的显式 CA 测试配置区分链验证失败与 CT 失败. 局域网测试先授予 LNP; 默认客户端与 `RequestBuilder.isInsecure` 的自定义 TrustManager 路径分别记录. (SOURCE; DEVICE-37)
- [ ] 默认保留 CT / ECH 配置. CT 例外只针对已验证的域与信任链, 不因证书不受信任而全局关闭 CT; 当前 `githubusercontent.com` 还含资源 CA, 记录实际验证链. ECH 需当前网络库及对端支持, 仅 target 升级或添加 `<domainEncryption>` 不证明已启用. 依据测试与 Q2 决定必要的局部配置. (SOURCE; DEVICE-37)
- [ ] 文档说明证书信任、CT、ECH、LNP 是不同条件, 不写成 "安装用户 CA 即可"; 涉及脚本公开行为时同步 P4. ([官方 NSC 文档](https://developer.android.com/privacy-and-security/security-config); DOCS)

### P2.7 Parcel 与插件 IPC (I7)

- [ ] `am compat enable PARCEL_HARDENING` 下运行插件 instrumentation / 冒烟: OpenCV, Image-Quantization, Paddle v4, Rapid v6, MLKit-Barcode, Yolo, Node, Lua, QuickJS, Python, MCP-Server 各一条最短链路 (复用 16 KB Roadmap 的 `ocr_provider_smoke.py` 与各 `*DeviceTest`); Tasker 插件 extras, 快捷方式 extras, `InstalledPluginRepository` 的 metaData 读取. (DEVICE-37)
- [ ] 若有失败, 修正读写不对称的 Parcelable / Bundle 用法; 无失败则记录 "无影响". (SOURCE / DOCS)

### P2.8 存储与文件管理器 (I8)

- [ ] 按附录 C 分别启用 `EXCLUDE_UNRELIABLE_STORAGE_VOLUMES`, `REQUIRE_PERMISSIONS_FOR_DIRECTORY_QUERIES`, `TRASH_BEHAVIOR_CHANGE` 后: 文件管理器根目录与外部存储卷列举 (`PathChecker`, 含插入 OTG / 模拟 SD 卡的 AVD 配置), 回收站, 版本历史, `files.*` 读写 `/sdcard`, `media.scanFile`, `images.save` 到相册, 相册 / 文档选择器; 先核实各开关实际约束的 provider / API, 再记录行为差异. (DEVICE-37)
- [ ] 修正与记录; `MANAGE_EXTERNAL_STORAGE` 引导 (`AllFilesAccessPermission`) 在 API 37 上流程不变. (SOURCE / DOCS)

### P2.9 小组件与通知 (I9)

- [ ] `ScriptWidget` 在 `AVD_API_37` (target 37 覆盖包) 上添加 / 更新 / 点击; `am compat enable CHECK_SIZE_OF_INFLATED_CUSTOM_VIEWS` 下检查宿主全部通知 (前台服务, 脚本通知 API, 崩溃通知) 无异常. (DEVICE-37)
- [ ] 若脚本通知 API 允许自定义视图, 增加尺寸保护或文档说明. (SOURCE / DOCS)

### P2.10 短信 / 通知监听 (I10)

- [ ] 在 AVD 上用 `adb emu sms send` 发送含验证码的短信, 对比 target 36 / 37 下脚本经 `content://sms` 与广播读取的可见性, 以及 `events.observeNotification` 收到的通知文本; 结论写入文档 (无代码可改, 属平台限制). (DEVICE-37; DOCS)

### P2.11 WebView (I11)

- [ ] `am compat enable ENABLE_USER_AGENT_REDUCTION` 下测试文档页, HTML 预览, `ui.webview` 的默认 / 自定义 UA 与渲染, 记录 OS / target / WebView 版本. 自定义 UA 仍可设置, 但给缩减后的默认 UA 追加后缀不会恢复完整设备信息; 需要详细信息的网页评估 User-Agent Client Hints (官方博客注明 WebView 116+ 且使用默认 UA). ([官方 WebView 说明](https://android-developers.googleblog.com/2024/12/user-agent-reduction-on-android-webview.html); DEVICE-37; DOCS)

### P2.12 大屏, 输入法与配置变化 (I12)

- [ ] 在 Xiaomi 23046RP50C (API 35, sw >= 600dp) 与 `AVD_API_37` 上以 target 37 覆盖包运行 UI 模式脚本 (含 `activity.setRequestedOrientation`), 悬浮窗, 控制台, 编辑器旋转 / 分屏; 确认与 target 36 行为一致 (宿主无豁免属性, 预期零差异), 旋转后输入法可见性与 `EditActivity` 的 `adjustResize` 表现. (DEVICE; DEVICE-37)
- [ ] 复核 `MainActivity` / `EditActivity` / `LogActivity` 的 `configChanges` 与 IME 恢复, 记录 Activity 重建 / `onConfigurationChanged` 次数; "更少 Activity 重建" 的具体触发条件补官方或源码证据后再确认. UI 脚本还需测试物理键盘、触摸板捕获、返回与边到边布局, 不以 Java / Kotlin 编译通过代替输入行为验证. (SOURCE; DEVICE-37)

### P2.13 内存上限 (I13)

- [ ] `AVD_API_37` 上 `am memory-limiter status`, 运行 OpenCV 大图 / OCR / Node 桥重负载脚本, 观察是否触发 `MemoryLimiter:AnonSwap`; 崩溃报告页对 `ApplicationExitInfo.REASON_OTHER` 的描述展示可读. (DEVICE-37)

### P2.14 Advanced Protection Mode (I14)

- [ ] 在 `AVD_API_37` 开启 Advanced Protection, 观察无障碍服务是否被撤销及设置页文案; 若确认 (二手来源转为实测), 在 `A11yServiceController` 的启用失败路径增加检测 (`AdvancedProtectionManager.isAdvancedProtectionEnabled()`, SDK >= 36) 与明确提示; 不声明 `isAccessibilityTool` (待决 Q5). (DEVICE-37; SOURCE; DOCS: 文档 FAQ)

### P2.15 低影响项逐项验证 (I15)

- [ ] 跟进 P1 的 Xiaomi 23046RP50C API 35 观察: 单应用选择 AutoJs6 后脚本返回未授权, 同机全屏授权成功; 以 compile 36 / 37 对照及系统授权结果、宿主 Activity 回调定位原因, 再决定是否转独立修复. API 37 的单应用基础截图通过不能替代该 OEM 路径. (DEVICE: [实施记录](evidence/android-sdk-37-p0-p1-20260913.md) 第 5 节)
- [ ] 对 `BONDING_APIS_REQUIRE_PRIVILEGED_PERMISSION`, `MAKE_SOCKET_READ_BEHAVIOR_CONSISTENT`, `RESTRICT_DATA_URI_COLUMNS`, `ENFORCE_STRICT_SQL_CHECKS`, `CHECK_ADD_MIRROR_DISPLAY_PERMISSION` (截图 `images.captureScreen` 经 MediaProjection), `DEPRECATE_SET_CONTENT_CAPTURE_ENABLED`, `SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL`, `BACK_DISPOSITION_CONTROLS_BACK_INTERCEPTION`, `DETECT_IMPLICIT_URI_PERMISSION_GRANT` (加 `detectImplicitUriPermissionGrant()` 检查 `app.viewFile` / 分享路径, 为 Android 18 预备), 在附录 B 对应行填 "已验证无影响" 或转为独立条目. (DEVICE-37)
- [ ] 为第 3.2 节的未确认项建立结论表: a11y (`AccessibilityService`, `AccessibilityNodeInfo`, 事件 / 手势 / 全局动作 / 截图), MediaProjection (每会话授权 / 回调 / resize / FGS), overlay (显示 / 触摸 / 后台启动), JobScheduler / WorkManager / 闹钟, 通知 / WebView, 最低可安装 target. 每行给 API diff 或专题文档 URL、宿主调用点、运行用例和结论; 文档未提及与尚未测试单列. 已知 Android 14-16 规则不能标作 Android 17 新规. (SOURCE; DOCS; DEVICE-37)
- [ ] a11y 事件补测 CJKV 输入法的 `TYPE_VIEW_TEXT_CHANGED` 组合 / 提交过程, 关注 `getTextChangeTypes()` 新信息及已有监听器兼容性; 不因 `android.accessibilityservice` 包本身未发现差异而忽略 `android.view.accessibility`. 仅当脚本 API 暴露新字段时同步文档 / d.ts. ([官方目标版本变更](https://developer.android.com/about/versions/17/behavior-changes-17); DEVICE-37)

### P2.16 inrt 切换前适配 (D3)

- [ ] 在仍保留 `TARGET_SDK_VERSION_INRT=29` 的代码上, 用 P0.2 的 target 37 debug 覆盖包提前执行 P3.3 清单, 完成 inrt 专属权限 / 存储 / 启动流程的适配; 正常 target 29 包同步回归. P3.3 是最终验收表, 不能等提交 target 37 后才首次排查这些差异. (SOURCE; DEVICE-37)
- [ ] 无关存储的 assets / 私有目录脚本在拒绝 "所有文件访问" 时仍可启动; 需要共享存储的脚本在缺少授权时得到明确引导. 测试取消授权、返回设置、永久拒绝、无 launcher 入口、开机启动, 不把权限请求回调到达视为授权成功. (DEVICE-37)

P2 验收: I1-I15 与 P2.16 每项有结论, 附录 B 对应行回填; target 37 覆盖包在 `AVD_API_37` 通过 P2.1-P2.5 与 inrt 专属用例; 同一代码的正常 debug 包在 API 28 / 33 / 35 真机上跑 a11y 冒烟与 VS Code 连接无退化; changelog 条目齐备. 已知平台限制单独记录, 不计为对应功能通过.

---

## P3: 宿主 targetSdk 37 切换

目标: `TARGET_SDK_VERSION=37` 与 `TARGET_SDK_VERSION_INRT=37` 同一提交切换, 全矩阵回归, 打包链路与用户 APK 验证.

### P3.1 切换

- [ ] `version.properties`: `TARGET_SDK_VERSION=37`, `TARGET_SDK_VERSION_INRT=37`; 移除 P0.2 覆盖开关的使用 (开关本身保留). (SOURCE)
- [ ] manifest 复核: 在 Android 11+ 且 target >= 30 时 `requestLegacyExternalStorage` 失效. `preserveLegacyExternalStorage` 是既有安装的数据迁移机制, 不保证 target 29 -> 37 在所有系统上保留访问, 也不覆盖首次安装 / 卸载重装; 按 Q4 与升级测试决定是否使用, 仅作用于 inrt. `tools:targetApi` 是否需要调整由 lint 决定. (SOURCE; LINT)
- [ ] 出包与门禁同 P1 (`aapt2` 显示 app 与 inrt 均 `targetSdkVersion:'37'`); 单元测试全量; lint 对比 P1 结果. (ANDROID_BUILD; JVM; LINT)

### P3.2 宿主回归矩阵

- [ ] `AVD_API_37`: a11y 冒烟 (`smoke-legacy-api.js`), 悬浮窗 / 控制台 / 布局分析, 截图与 OCR (OpenCV + Paddle v4 或 Rapid v6), VS Code 扩展两种模式 (权限引导), `http` / WebSocket 到局域网与回环, 定时任务与开机自启 (音频 / TTS), 通知 / 小组件, 文件管理器 (卷 / 回收站 / 媒体), APK 安装会话, 快捷方式, 文档 WebView, UI 模式与预测性返回, `exit()` / 线程中断. (DEVICE-37)
- [ ] 真机回归 (targetSdk 提升对旧系统的影响): Xiaomi 23046RP50C (API 35), Sony XQ-DQ72 (API 33), Sony XQ-AT72 (API 31), Sony G8441 (API 28) 各跑 a11y 冒烟 + VS Code 连接 + 截图 + 定时任务; 重点确认 API 28 / 31 上不出现新权限弹窗与行为变化. (DEVICE)
- [ ] 16 KB: `aj6-ps16k-37-x86_64` 上安装与 `verify_device.sh` 无兼容模式警告 (与 16 KB Roadmap 的验证一致). (DEVICE-16K)

### P3.3 打包应用 (inrt) 29 -> 37 清单 (D3)

用户 APK 的运行时代码与宿主共用, 以下按 API 等级列出对 "以 29 为目标的旧行为" 的增量, 每项在 `AVD_API_37` 与 API 33 真机上用打包出的示例应用验证:

| 等级 | 对打包应用的增量 | 验证 |
| --- | --- | --- |
| 30 | 分区存储强制: 任意共享路径的直接文件访问需评估所有文件访问权限; 应用专属目录 / MediaStore / SAF 各有规则, `MANAGE_EXTERNAL_STORAGE` 也不等于可访问任意其它应用私有目录. `SplashActivity.kt:76` 仍只申请旧存储权限 | 按脚本实际需要引导 (Q4); 授予 / 拒绝后的共享路径与私有目录脚本各测 |
| 30 | 包可见性 (`<queries>` 已在共用 manifest, 且声明 `QUERY_ALL_PACKAGES`) | `app.launch` / `app.getAppName` 任意包 |
| 31 | 组件 `exported` 显式化 (共用 manifest 已满足); `PendingIntent` 可变性 (代码已按 36 处理); 后台启动前台服务限制 (`FGS_BG_START_RESTRICTION_CHANGE_ID`; `BOOT_COMPLETED` 属豁免); 精确闹钟权限 (`SCHEDULE_EXACT_ALARM` + 已声明的 `USE_EXACT_ALARM`); 系统启动画面叠加在自定义 `SplashActivity` 之前; 通知跳板限制 | 开机自启 (`InrtBootCompletedReceiver`), 定时任务, 启动画面观感 |
| 33 | `POST_NOTIFICATIONS` 运行时权限; 拒绝时前台服务通知在通知栏不可见但服务仍需合法通知及启动条件; 细分媒体权限; 旧 `WRITE_EXTERNAL_STORAGE` 不能代替共享存储授权 | 通知权限开关 (`PostNotificationsPermissionSwitchPreference`), 按系统 / target 移除无效存储申请 |
| 34 | 前台服务类型必填 (已声明); 运行时注册接收器需导出标志 (共用代码); 隐式 Intent 只匹配已导出组件; `Thread.stop` 不可用; 动态加载 DEX 只读 (`AndroidClassLoader` 已处理); MediaProjection 每次会话授权 + 回调 | `runtime.loadDex`, 截图, `events.broadcast` |
| 35 | 边到边强制 (UI 模式脚本布局); 前台服务 `dataSync` / `mediaProcessing` 超时 (未用); `BOOT_COMPLETED` 禁止启动部分类型前台服务 (`specialUse` 允许); 悬浮窗不再豁免后台启动前台服务 (`FGS_SAW_RESTRICTIONS`, 仅可见悬浮窗豁免); 最低可安装 targetSdk 24 | UI 模式脚本顶部内容, 开机自启后的前台服务 |
| 36 | 预测性返回默认开启 (`onBackPressed` 不再回调, 共用 `BackCompat`); 边到边豁免移除; 大屏忽略方向; Intent 重定向保护; `announceForAccessibility` 弃用 | 打包应用的 `LogActivity` / `SettingsActivity` 返回, 平板方向 |
| 37 | P2 全部条目, 其中本地网络权限与后台音频对打包应用最直接 (脚本自建 HTTP 服务 / TTS) | 复用 P2.1 / P2.2 的用例 |

- [ ] 依上表逐行验证并修正 inrt 专属流程 (`SplashActivity` 权限引导, `fragment_preferences_inrt.xml` 的开关顺序与默认值). (SOURCE; DEVICE-37; DEVICE)
- [ ] 安装矩阵: 用相同 applicationId / 签名、递增 versionCode 的示例 APK 测 target 29 -> 37 覆盖安装, 并另测 target 37 首次安装 / 卸载重装, 两者不能共享成功结论. API 30 设备或 AVD、API 33 真机与 `AVD_API_37` 分别记录旧文件位置、已有授权、升级后的实际访问及迁移结果. `preserveLegacyExternalStorage` 不能替代新安装的权限引导. ([官方迁移说明](https://developer.android.com/training/data-storage/use-cases); DEVICE; DEVICE-37)
- [ ] 打包器 UI (`ui/project/BuildActivity`, `ProjectConfigActivity`) 与文档说明打包应用在 Android 11+ 的存储引导. (DOCS)

### P3.4 打包链路与 Runtime Kit

- [ ] `generateRuntimeKit` 重新生成; `AutoJs6-Plugin-APK-Builder-Template` 的 JVM 测试与 `build-from-runtime-kit.yml` 夹具中若有 `targetSdkVersion=29` 的硬编码期望, 改为读取模板 (该仓库条目在 P5.2 落地, 本条只做只读核对). (SOURCE; JVM)
- [ ] 用新 Runtime Kit 打包 3 个示例 (a11y 冒烟, UI 模式, 定时任务 + TTS), `aapt2` 显示 `targetSdkVersion:'37'`, 在 `AVD_API_37` 与 API 33 / 35 真机安装运行. (DEVICE-37; DEVICE)

### P3.5 文档与提交

- [ ] changelog: 按 D9 记录宿主 targetSdk 36 -> 37 与 inrt 29 -> 37 的实际变化, 核对 P2 的 `fix` / `improvement`, README 重新生成; 本文回填. (DOCS)
- [ ] 提交 `chore(sdk): raise targetSdk to 37 for app and inrt (roadmap P3)`. (DOCS)

P3 验收: P3.2 矩阵通过; P3.3 表每行有结论; 用户 APK 在 API 37 / 35 / 33 上安装运行; CI 绿.

---

## P4: 关联项目, 文档与 CI

- [ ] 文档仓库 (`AutoJs6-Documentation`): 若 P2.1 引入新权限名 / 错误码或 P2 新增任何 JS API, 执行 `auto-generate-for-autojs6.bat` 并提交 (含 `AutoJs6-Plugin-Offline-Docs`); 无 API 变化时只在文档增加 "Android 17 适配说明" 页 (本地网络权限, 后台音频, OTP 短信延迟, WebView UA, 反射限制, 私有 CA). (DOCS)
- [ ] d.ts 与 LSP (`AutoJs6-TypeScript-Declarations`, `AutoJs6-Plugin-Ace-Editor`): 仅当 JS API 变化时执行 `aj6dts.bat -Publish` 与 `:app:generateAutoJs6LspDeclarations`, 版本号加一. (DOCS)
- [ ] VS Code 扩展 (`AutoJs6-VSCode-Ext`): README / 故障排查增加 "Android 17 设备需授予本地网络权限" 说明 (不改代码, `VSCODE_EXT_REQUIRED_VERSION` 不变). (DOCS)
- [ ] CI: P1 / P3 各自推送后验证远程 `android.yml`, 记录 runner 是否自动下载 `android-37.0`; `release-runtime-kit.yml` 的外部发布与插件触发随正常发布验证, 构建步骤可先本地运行. (ANDROID_BUILD)
- [ ] `AutoJs6-Official-Plugins-Index`: 确认生成器无 targetSdk 字段假设; 插件发布时按既有流程刷新. (SOURCE)

---

## P5: 官方插件舰队 (42 仓)

目标: 每个插件 `compileSdk` / `targetSdk` 均为 37, 暴露项已适配, 各自 changelog / README / 版本号更新. 逐仓清单与暴露列见附录 E; 每仓都需独立记录 C (compile), T (target), V (验证), D (版本与文档) 四项状态, 不能只用分组的一次勾选代替 42 仓验收.

### P5.0 每仓通用条目 (复制到附录 E 对应行的证据栏)

1. 工作区: 若附录 E "脏" 列非 0, 先由维护者提交或搁置既有改动 (D11). (SOURCE)
2. compileSdk: 核查 `version.properties` 到各构建脚本的实际消费链后设为 37, 包含 app / Android library / lint 配置; R8-Compiler 硬编码例外见 P5.3. 构建 (`assembleDebug` / `assembleRelease`), 单元测试, 既有门禁 (16 KB `verifyNativePageAlignment`, 各仓库自有校验); 修正弃用并核对 APK 元数据. (ANDROID_BUILD; JVM)
3. 暴露适配: 按附录 E 暴露列处理 (本地网络 -> 复用 P2.1 权限助手模式; 动态加载 -> 只读; 前台服务 / 音频 -> P2.2 结论; 屏幕捕获 -> P2.15 结论; WebView -> P2.11 结论). (SOURCE)
4. targetSdk: `TARGET_SDK_VERSION=37`; 在 `AVD_API_37` 与一台 4 KB 真机 (API 33 或 35) 上跑该插件的 instrumentation / 冒烟 (16 KB Roadmap 用过的 `*DeviceTest`, `ocr_provider_smoke.py` 等). (DEVICE-37; DEVICE)
5. 版本递增 (各仓库惯例), changelog 按 D9 与各仓库规则记录已完成的 compile / target 步骤及适配, README 重新生成, 单独提交. (DOCS)
6. 发布节奏按待决 Q6.

### P5.1 被宿主进程加载 (OpenCV, Image-Quantization)

- [ ] 两仓通用条目; 运行时 `.so` 的只读处理在宿主侧 (P2.3), 插件侧无运行时暴露; 提交后用宿主 target 37 覆盖包复测加载. (DEVICE-37)

### P5.2 打包进用户 APK (Paddle v3 / v4 / v5 / v6, Rapid v3 / v6, APK-Builder-Template)

- [ ] 6 个 OCR 仓库通用条目; 核对引擎以 `System.loadLibrary` 从 `nativeLibraryDir` 加载 (只读), 无 staging 到可写目录的路径; 在 target 37 的用户 APK 内 (P3.4 示例) 复测识别. (SOURCE; DEVICE-37)
- [ ] APK-Builder-Template: 通用条目 + 模板 / 夹具中 targetSdk 期望改为读取模板 (P3.4), `configureManifest` 不改写 `uses-sdk` 的约束保持; CI 工作流对模板 APK 的 `aapt2` 检查加 `targetSdkVersion` 断言. (SOURCE; JVM; ANDROID_BUILD)

### P5.3 独立进程运行时插件 (NodeJs, Bun, Python, Lua, QuickJS, TypeScript-Engine, Java-Runtime, Kotlin-Runtime, DEX-Compiler, R8-Compiler)

- [ ] 通用条目; 逐仓检索 `System.load` / `dlopen` / `DexClassLoader`, 区分 Java 原生库加载, NDK 加载与独立 exec. 核查 Node `.node` / 用户 `.so`, Bun `libbun_exec.so` 及用户库, Python / Chaquopy 扩展模块, Java / Kotlin / DEX / R8 的 DEX 产物. Java `System.load` 按 P2.3 处理, DEX 延续已有只读要求; NDK `dlopen` / exec 是否受其它规则限制须有对应源码或设备证据, 不把 `THROW_ERROR_FOR_WRITABLE_DCL` 的 Java 行为直接推广到全部加载方式. 各插件用 target 37 包复测. (SOURCE; DEVICE-37)
- [ ] 本地网络: 在插件进程内执行脚本时打开局域网 socket (Node `http.createServer`, Bun `Bun.serve`, Python `socket`) 需要各插件自身的 `ACCESS_LOCAL_NETWORK` (权限按 UID, 宿主授权不覆盖插件); 三个带 `INTERNET` 的运行时 (Node, Bun, Python) 声明权限并在宿主转发的错误中提示 "在插件设置中授予". (SOURCE; DEVICE-37)
- [ ] 内存上限与 MessageQueue: 各运行时若使用 Android Looper, 在 `USE_NEW_MESSAGEQUEUE` 下复测; 大负载脚本观察 `am memory-limiter status`. (DEVICE-37)
- [ ] R8-Compiler: `app/build.gradle.kts` 与两个 plugin-api 模块硬编码 compileSdk 36, app 的 targetSdk 以及 library lint target 也为 36; 先统一版本来源再执行通用条目. app 的 versionCode / versionName 同样硬编码, 按该仓实际发布约定递增. 另外核查固定 `r8-library/android-36.jar` 及 SHA-256: 插件自身 compileSdk 提升不会更新脚本编译所用的平台库, 必须记录对 API 37 输入 / 符号的支持边界及相关测试. (SOURCE; JVM; ANDROID_BUILD)
- [ ] Bun-Runtime: 验证当前 1.7.4 平台插件是否正确解析 API 37; 只有阻碍本次构建时才升级到经验证版本, 否则遵循 D7 保持版本并将可选升级留到后续. (SOURCE; ANDROID_BUILD)

### P5.4 网络, 媒体与屏幕插件 (MCP-Server, Screen-Color-Picker, Three-Ember-Player, Three-Terra-Player, Three-Stone-AI, HTML-Previewer, Markdown-Previewer, Offline-Docs, Ace-Editor)

- [ ] MCP-Server: 局域网 SSE / Streamable 服务端需要 `ACCESS_LOCAL_NETWORK` (入站 accept 也受限); 插件设置页增加权限引导, 服务启动前检查; `specialUse` 前台服务不变; 在 `AVD_API_37` 上用 Inspector / Claude Code 走一次完整配对 (复用 MCP Roadmap 的 P3.1 验收流程). (SOURCE; DEVICE-37)
- [ ] Screen-Color-Picker: `mediaProjection` 前台服务 + 悬浮窗; `CHECK_ADD_MIRROR_DISPLAY_PERMISSION` 下取色通过. (DEVICE-37)
- [ ] Three-Ember / Three-Terra: `mediaPlayback` 前台服务在后台音频强化 (target 37 需 while-in-use) 下的启动时机; `cmd audio set-enable-hardening throw` 复测播放 / 列表切换 / 锁屏. (DEVICE-37)
- [ ] Three-Stone-AI: 官方 [Android 17 总览](https://developer.android.com/about/versions/17/summary) 列出 target 37 使用 NPU 需要声明 `android.hardware.npu` (VERIFIED). 若 LiteRT-LM 实际使用 NPU 委托, 添加可选特性声明并测试无 NPU 设备回退; 另测内存上限下的推理. 目标版本主页面未展开该项, 不凭章节缺失忽略总览要求. (SOURCE; DEVICE-37)
- [ ] HTML / Markdown 预览, Offline-Docs, Ace-Editor: WebView UA 缩减与 File System Access 下渲染正常; 同 profile 的 LSP / 本地文档回环验证与跨 profile 限制分开记录. (DEVICE-37)

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
| Q2 | Certificate Transparency 默认策略 (P2.6) | (a) 保持默认; (b) 对已验证且确需豁免的域作局部配置; 证书信任另行处理, 不因用户 CA 未受信任而关闭 CT | (a), 有证据时 (b); 复核修正原推荐的全局关闭方案 |
| Q3 | 后台音频策略 (P2.2) | (a) 保持前台服务常驻模式 + 诊断警告; (b) 引入 media3 `MediaSessionService` 承载脚本音频 | (a): 改动最小, 且脚本音频并非媒体应用语义 |
| Q4 | inrt 存储引导与旧版升级保护 (P2.16 / P3.3) | (a) 按共享存储需求引导并验证旧安装迁移; (b) 仅设置页开关; `preserveLegacyExternalStorage` 的采用由升级矩阵决定, 不承担新安装兼容保证 | (a): 无共享存储需求的脚本允许跳过, 拒绝权限后仍有可理解的结果 |
| Q5 | `isAccessibilityTool` (P2.14) | (a) 不声明, 只检测 Advanced Protection 并提示; (b) 声明 | (a): AutoJs6 是自动化工具, 声明会与其定义不符 |
| Q6 | 插件舰队发布节奏 (P5) | (a) 随各插件下一次发布顺带, 有行为适配的插件 (MCP-Server, Node, Bun, Python, 播放器 x2, Screen-Color-Picker) 单独发版; (b) 全部单独为 SDK 升级发版; (c) 只提交不发布, 由维护者统一发布 | (a) |
| Q7 | `usesCleartextTraffic` 迁移到 NSC 是否随本次 | (a) 否, 记入附录 F; (b) 是 | (a): Android 17 未强制, 与 SDK 升级解耦 |

---

## 附录 B: targetSdk 37 门控变更逐项评估

来源: `AVD_API_37` 的 `dumpsys platform_compat` (36 项 `enableSinceTargetSdk=37`), 含义列的核对状态: VERIFIED = 官方页面明确描述; UNVERIFIED = 仅由名称推断或二手来源. "结论" 列在 P2 完成后回填.

| 变更 (ID) | 含义 | 宿主影响 | 条目 | 结论 |
| --- | --- | --- | --- | --- |
| `ASM_RESTRICTIONS` (230590090, overridable) | dump 为 target 37 门控且未标 disabled (PLATFORM_CONFIG); `overridable` 不等于默认关闭, 完整行为与有效状态待核验 (UNVERIFIED) | 脚本跨应用启动 Activity | P2.5 | |
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
| `LOOPER_CLEARS_THREAD_INTERRUPTED` (458413887, overridable) | 消息派发时清除原生中断标记, ThreadCompat 保留脚本取消状态 (PLATFORM_CONFIG; DEVICE-37) | 脚本引擎中断语义 | P2.4 | 单项 / 组合 / target 37 默认实测通过, 见 P2.4 证据 |
| `MAKE_SOCKET_READ_BEHAVIOR_CONSISTENT` (383671392) | `BluetoothSocket` RFCOMM `read()` 断开返回 -1 (VERIFIED) | 脚本蓝牙 socket | P2.15 | |
| `OVERRIDDEN_THREAD_START_METHOD` (418924588) | 线程池跳过工作线程 start 覆写, run 仍执行; 直接 start 调用不变 (PLATFORM_CONFIG; DEVICE-37) | 宿主 Thread 子类及线程池工厂 | P2.4 | 宿主无 start 覆写; 单项 / 组合 / target 37 默认实测通过 |
| `PARCEL_HARDENING` (416031865) | Parcel 读写更严格 (UNVERIFIED) | 插件 IPC, Bundle extras | P2.7 | |
| `REBOOT_MODEM_THROW_EXCEPTIONS` (476225321) | 电话调制解调器重启 API 抛异常 (UNVERIFIED) | 无 | P2.15 | |
| `RENDERSCRIPT_SCRIPTC_DEPRECATION_CHANGE_ID` (297019750) | RenderScript ScriptC 停用 (UNVERIFIED) | 无 (代码无 RenderScript) | P2.15 | |
| `REQUIRE_PERMISSIONS_FOR_DIRECTORY_QUERIES` (157720069) | MediaStore / 文档目录查询需对应权限 (UNVERIFIED) | `media.*`, 文件管理器 | P2.8 | |
| `RESTRICT_DATA_URI_COLUMNS` (437318646) | `ContactsContract.Data` 移除账户列 (VERIFIED) | `QuickContactBadgeAttributes.kt` | P2.15 | |
| `RESTRICT_DEPRECATED_DATA_BUILDER_CONSTRUCTORS` (424093067) | 弃用的 Data Builder 构造器受限 (UNVERIFIED) | 待定位 | P2.15 | |
| `SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL` (417951523) | 显示密码设置拆分为触摸 / 物理键盘 (VERIFIED) | 无 | P2.15 | |
| `THROW_ERROR_FOR_WRITABLE_DCL` (463348571) | `System.load()` 可写文件抛 `UnsatisfiedLinkError` (VERIFIED) | OpenCV / 量化插件 staging | P2.3 | `a784273d2`; target 36 强制 / target 37 默认加载通过; 可写副本按预期被拒绝, 见 P2.3 实施记录 |
| `TRASH_BEHAVIOR_CHANGE` (461429441) | MediaStore 回收站语义变化 (UNVERIFIED) | `media.*`, 相册相关 | P2.8 | |
| `USE_NEW_MESSAGEQUEUE` (421623328) | 无锁 MessageQueue (VERIFIED) | 脚本引擎 Looper | P2.4 | 单项 / 组合 / target 37 默认实测通过, 见 P2.4 证据 |
| `USE_NSD_PICKER_WHEN_NO_LOCAL_NET_PERMISSION` (490596785) | 无本地网络权限时 NSD 走系统选择器 (VERIFIED) | 无 (未用 `NsdManager`) | P2.1 | |
| `WEAROS_WALLPAPER_HANDLES_SCALING` (272527315) | Wear 壁纸缩放 (UNVERIFIED) | 无 | P2.15 | |

不经兼容框架门控但与本次相关的项: `ACCESS_LOCAL_NETWORK` 权限 (I1), 后台音频强化 (I2), `static final` 反射写入抛 `IllegalAccessException` (I4, VERIFIED, 无开关), 小组件 RemoteViews 内存上限 (I9, VERIFIED), 密钥库上限, 应用内存上限 (I13), Advanced Protection (I14). 面向 38 的 `NETWORK_BLOCKED_WITHOUT_INTERNET_PERMISSION` 与 `enableSinceTargetSdk=10000` 的 4 项不在本次范围.

---

## 附录 C: 命令与参考

下列示例使用本工作区的 PowerShell. 先按 `adb devices -l` 替换序列号, 再从所测 APK / `dumpsys package` 确认包名; inrt 与各插件不能沿用宿主包名. `adb` / `aapt2` 若不在 PATH, 使用本机 SDK 的完整路径.

兼容开关每次仅接受一个 change 与一个 package. 先保存已有 override; 以下 `reset` 只适用于测试前没有该项 override 的包, 有则恢复原值. 普通设备上使用 debuggable 测试包; 具体可切换范围还受设备构建类型与 change 属性限制. 详见 [Compatibility framework tools](https://developer.android.com/guide/app-compatibility/test-debug).

```powershell
$Sdk37Serial = 'emulator-5554' # 替换为 adb devices 中已确认的 API 37 设备
$Sdk37Package = 'org.autojs.autojs6' # 替换为待测 APK 的 applicationId
$Sdk37Change = 'USE_NEW_MESSAGEQUEUE'
adb -s $Sdk37Serial shell getprop ro.build.version.sdk
adb -s $Sdk37Serial shell dumpsys package $Sdk37Package
adb -s $Sdk37Serial shell dumpsys platform_compat
adb -s $Sdk37Serial shell am compat enable $Sdk37Change $Sdk37Package
adb -s $Sdk37Serial shell am compat disable $Sdk37Change $Sdk37Package
adb -s $Sdk37Serial shell am compat reset $Sdk37Change $Sdk37Package
```

上面展示 enable / disable / reset 三种独立操作; 实际测试在每次切换后重新运行用例并记录, 不连续执行三条后再测试. 多个开关应逐个调用, 例如组合测试的启用步骤:

```powershell
$Sdk37Changes = @('USE_NEW_MESSAGEQUEUE', 'LOOPER_CLEARS_THREAD_INTERRUPTED', 'OVERRIDDEN_THREAD_START_METHOD')
foreach ($Sdk37Change in $Sdk37Changes) {
    adb -s $Sdk37Serial shell am compat enable $Sdk37Change $Sdk37Package
    if ($LASTEXITCODE -ne 0) { throw "Cannot enable $Sdk37Change for $Sdk37Package" }
}
# 完成用例后逐项恢复原 override; 原来没有覆盖时用 reset.
```

P2 中简写 `am compat enable CHANGE` 均表示用上述形式补全 adb, serial 与实际 package. `reset-all` 仅用于原先没有其它测试 override 的专用包. 开发者选项 > App Compatibility Changes 可检查同一组变更; `dumpsys` 的静态列表不是功能通过证据.

Android 17 本地网络权限, 必须先在 manifest 声明并安装 target 37 包:

```powershell
adb -s $Sdk37Serial shell pm revoke $Sdk37Package android.permission.ACCESS_LOCAL_NETWORK
# 运行拒绝场景.
adb -s $Sdk37Serial shell pm grant $Sdk37Package android.permission.ACCESS_LOCAL_NETWORK
# 重启待测功能, 运行授权场景; 另测真实 UI 申请及再次撤销.
```

Android 16 的 `RESTRICT_LOCAL_NETWORK` 与 `NEARBY_WIFI_DEVICES` 测试流程按 [本地网络权限指南](https://developer.android.com/privacy-and-security/local-network-permission) 执行; 不把 API 37 的新权限 grant 命令用于 API 36.

- 音频: `adb -s $Sdk37Serial shell cmd audio set-enable-hardening throw`, 状态通过 `adb -s $Sdk37Serial shell dumpsys audio` 与 logcat 检查. 这是全设备诊断模式, 会覆盖默认 target / 闹钟差异; `disable` 是关闭限制, 不等于恢复出厂默认. 在可恢复的专用 AVD 记录并恢复原状态, 默认规则的验收另跑一轮.
- 内存: `adb -s $Sdk37Serial shell am memory-limiter status`; 对具体 PID 使用 `manual <pid> <MB>` 测试, 完成后 `manual <pid> none` 恢复该 PID 的默认限制. `ignore all` 影响全设备, 不作为常规迁移步骤.
- StrictMode: debug 构建中以 API >= 36 调用 `detectBlockedBackgroundActivityLaunch()`, API >= 37 调用 `detectImplicitUriPermissionGrant()`, 配合 `penaltyLog()` / `build()` 安装策略; 不在旧系统无条件链接调用新方法.
- 短信: `adb -s $Sdk37Serial emu sms send 10086 "OTP 123456"` (仅模拟器); 记录实际 OTP 检测与 3 小时窗口, 不把未检测为 OTP 的普通短信视为保护机制失效.
- APK 核对 (本机): `& 'E:/.android/sdk/build-tools/37.0.0/aapt2.exe' dump badging '<实际 APK 路径>'`. 检查 `compileSdkVersion`, `targetSdkVersion`, `sdkVersion`, package; inrt / 插件 / 用户生成 APK 分别保留结果.
- lint: P0.2 先落实有效入口, 再独立执行 app debug 与 app / inrt release 检查. 不直接依赖当前被禁用的 `lintVitalAnalyze*Release`; 证据须包含新报告及任务执行状态.
- 单元测试: `./gradlew :app:testAppDebugUnitTest testDebugUnitTest :app:compileAppDebugAndroidTestKotlin`; 核查任务覆盖列表与测试报告, 不带 `--tests` 过滤时才代表全量宿主单元测试.
- 历史探针任务: `./gradlew --offline --continue --console=plain "-Pkotlin.daemon.jvmargs=-Xmx6g" :app:compileAppDebugKotlin :app:compileAppDebugJavaWithJavac compileDebugKotlin compileDebugJavaWithJavac`. 这些任务不包括完整 inrt / release 验证. 恢复临时 SDK 配置时只恢复本次修改的键, 保留构建自增 / 用户修改的其它属性.
- 模拟器: 先记录既有 adb 设备; 若启动专用 AVD, 用 `-no-snapshot-save`, 后台辅助进程使用隐藏窗口. 用 `adb -s $Sdk37Serial shell getconf PAGE_SIZE` / `getprop ro.build.version.sdk` 记录页大小与 API, 只关闭本次启动的模拟器.

官方参考按主题使用: [目标版本变更](https://developer.android.com/about/versions/17/behavior-changes-17), [所有应用变更](https://developer.android.com/about/versions/17/behavior-changes-all), [迁移清单](https://developer.android.com/about/versions/17/migration), [MessageQueue](https://developer.android.com/about/versions/17/changes/messagequeue), [大屏限制](https://developer.android.com/about/versions/17/changes/ff-restrictions-ignored), [后台 Activity 安全](https://developer.android.com/guide/components/activities/secure-bal), [AGP / API 兼容表](https://developer.android.com/build/releases/about-agp), [Play 目标版本要求](https://developer.android.com/google/play/requirements/target-sdk). 具体核验范围与取页失败见 [复核记录](evidence/android-sdk-37-review-20260913.md).

---

## 附录 D: 证据等级与验收方式

| 标签 | 可以证明 | 不能证明 |
| --- | --- | --- |
| `SOURCE` | 源码 / 配置 / 脚本存在, 结构符合设计 | 编译或行为正确 |
| `PLATFORM_CONFIG` | 指定镜像中存在某 change / ID / 默认门控配置 | 行为语义, 某包实际执行路径, 功能通过 |
| `JVM` | Android-free 逻辑的单元测试 (JUnit4, 无 Robolectric) | 真机行为 |
| `ANDROID_COMPILE` | 指定 variant 的 Kotlin / Java 编译任务通过 | 完整 APK, release R8, lint, 未编译的 variant, 运行时行为 |
| `ANDROID_BUILD` | `assemble*` / `generateRuntimeKit` 通过且 16 KB 门禁通过 (或 CI 绿) | 真机行为 |
| `LINT` | 检查未被跳过, 新报告与基线 diff 已记录, 无新增 error / fatal | `SKIPPED` / `abortOnError=false` 下仅凭构建成功不能证明无问题; 不能证明运行时行为 |
| `DEVICE` | 指定设备或 AVD 上列明用例通过, 记录 API 级别、页大小与真机 / 模拟器类型 | 未列出的系统或设备; 旧系统证据不能证明 Android 17 行为 |
| `DEVICE-37` | `AVD_API_37` (或 Android 17 真机, 注明型号) 上, 注明 targetSdk (36 + 覆盖 / 37) 与打开的兼容开关, 冒烟通过 | 未列出设备 / 开关组合 |
| `DEVICE-16K` | `aj6-ps16k-37-x86_64` 上安装无兼容模式警告 | - |
| `DOCS` | 本条所要求的文档 / changelog / d.ts 已更新, 需要生成或递增的产物已完成 | 未执行的构建 / 设备用例; 纯 roadmap 修订不要求版本递增 |

条目勾选时在其后追加证据, 格式示例: `[x] ... (DEVICE-37: AVD_API_37, target 37 覆盖包, compat USE_NEW_MESSAGEQUEUE on, 12/12 用例; commit abc1234)`.

---

## 附录 E: 插件舰队矩阵

"暴露" 列取自各仓库 manifest 扫描 (2026-09-13): 前台服务类型, 悬浮窗 (`SYSTEM_ALERT_WINDOW`), `INTERNET`, `MANAGE_EXTERNAL_STORAGE`, 以及已知的进程模型; "脏" 为初稿快照中的未提交文件数, 每仓开工时刷新. SDK 数值先来自属性文件, 已核实的构建脚本覆盖值在下表修正, 其余均以实际产物最终确认. 阶段与分组对应 P5.x. "证据" 列按 `C / T / V / D` 分别回填状态、提交 hash 与设备 (见 P5), 不将整个分组一次性勾选.

| 仓库 | compile / target / min | 平台插件 | 进程模型 | 暴露 | 脏 | 阶段 | 证据 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| OpenCV | 36 / 36 / 24 | 1.8.0 | 宿主进程 (staging 加载) | 动态加载 (宿主侧) | 0 | P5.1 | |
| Image-Quantization | 36 / 36 / 24 | 1.8.0 | 宿主进程 (staging 加载) | 动态加载 (宿主侧) | 0 | P5.1 | |
| Paddle-OCR-PP-OCRv3 | 36 / 36 / 24 | 1.8.0 | 插件进程 / 用户 APK | Binder, 原生库 | 40 | P5.2 | |
| Paddle-OCR-PP-OCRv4 | 36 / 36 / 26 | 1.8.0 | 插件进程 / 用户 APK | Binder, 原生库 | 6 | P5.2 | |
| Paddle-OCR-PP-OCRv5 | 36 / 36 / 26 | 1.8.0 | 插件进程 / 用户 APK | Binder, 原生库 | 6 | P5.2 | |
| Paddle-OCR-PP-OCRv6 | 36 / 36 / 26 | 1.8.0 | 插件进程 / 用户 APK | Binder, 原生库 | 6 | P5.2 | |
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
| R8-Compiler | 36 / 36 / 24 (硬编码) | 1.8.0 | 插件进程 | 动态加载 (DEX 产物), 固定 android-36.jar | 0 | P5.3 | |
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

- Android 17 新 MessageQueue 生效时, 私有 `mMessages` 字段虽然保留但恒为 null, 不能据此判断队列内容; 宿主使用公开 Handler / Looper / IdleHandler, Espresso 已为 3.7.0. Android 17 上 target 37 应用反射写入 `static final` 字段会抛 `IllegalAccessException`, JNI 写入也可能崩溃. 两项限制分别来自 [MessageQueue 官方说明](https://developer.android.com/about/versions/17/changes/messagequeue) 与 [target 37 官方行为变更](https://developer.android.com/about/versions/17/behavior-changes-17), 不泛化为所有旧 target 的反射读取限制. 当前 util.java / threads 文档无受影响示例, P4 汇总升级说明时提示用户脚本; [P2.4 评估](evidence/android-sdk-37-p24-20260913.md#start-覆写与非-sdk-接口复核).
- androidx.core 1.19.0 (2026-06-03) 要求 compileSdk 37, 且 `core-ktx` 已并入 `core`; 当前 `gradle/libs.versions.toml` 的 `core-ktx = "1.15.0"` 是为 AGP 8.2.2 时代保留的过期钉子, P1 之后可在独立的依赖升级中解除 (D7).
- androidx.activity 1.14.0-alpha02 起 Compose 系列库要求 compileSdk 37.1 (次级 SDK); 届时需要 `compileSdk { version = release(37) { minorApiLevel = 1 } }` 语法, `Versions.kt` 与 `autojs6-platform-versions` 数据表都要支持 "37.1" 形式. 本机尚未安装 `platforms;android-37.1`.
- `usesCleartextTraffic="true"` 已被官方标记为未来移除, 应把允许明文的域迁入 `network_security_config.xml` (待决 Q7 选 (a) 时记于此).
- Android 18 起 `ACTION_SEND` / `ACTION_IMAGE_CAPTURE` 不再隐式授予 URI 权限, P2.15 的 `detectImplicitUriPermissionGrant()` 检查结果可提前修正.
- 本机 `android-36/android.jar` 有 4 个 `android/hardware/fingerprint/*.class`, `android-37.0/android.jar` 为 0 (SOURCE: [复核记录](evidence/android-sdk-37-review-20260913.md)); 这证明 SDK 编译符号移除, 不等于设备运行时一定无此类. `InputMethodManager.SHOW_IMPLICIT` 之外的弃用由 lint 逐版跟踪.
- APK 签名方案 v3.2 (混合后量子, ML-DSA) 对内置 APK 打包器 / `modules/apk-signer` 是可选能力, 与本次无关.
- Advanced Protection Mode 对无障碍服务的限制若被实测确认, 需要在用户文档 FAQ 与插件中心 "无障碍兼容" 页面长期维护说明.
- P2.3 在 API 24 发现既有 JAR 编译器边界: 本地 D8 引用系统尚无的 `java.nio.file.Paths`, legacy dx 回退拒绝 Java 8 class major 52; Java 6 major 50 夹具可正常转换与加载. 后续独立修复旧系统编译器兼容性及失败诊断, 不因本次原始 DEX 目录修复或 API 37 冒烟通过而宣称此限制已解除. 见 [P2.3 证据](evidence/android-sdk-37-p23-20260913.md#jar-夹具与既有限制).

---

## 会话记录

### 2026-09-13 初稿

- 完成: 宿主与 42 个插件仓库的 SDK / 工具链 / manifest 事实收集; compileSdk 37 探针编译 (0 错误, 1 处新增弃用) 与 36 基线对照; `AVD_API_37` 的 `dumpsys platform_compat` 清单 (36 项门控) 与官方文档核对; 固定决策 D1-D4 (维护者以选择题拍板: 范围含全部插件, 两步走, inrt 同步升到 37, 允许探针编译) 与派生约定 D5-D11; 本 Roadmap 初稿与两份证据文件.
- 关键发现: 编译层面几乎零成本 (AGP 9.4.0 已满足 API 37, 探针范围内编译通过); 风险集中在 targetSdk 37 的运行时行为: 本地网络运行时权限 (含入站 accept, 影响 VS Code 扩展 / MCP / 脚本 socket), 后台音频 while-in-use 要求, `System.load` 只读要求 (初稿称 staging 未设只读, P2.3 复核修正为已有尝试但未检查失败), 无锁 MessageQueue 与线程中断语义, `IntentSender` 的 BAL opt-in, CT / ECH 默认开启; inrt 从 29 跨到 37 需要单独的存储引导与旧版升级保护.
- 环境变化: 本会话为取兼容清单启动并关闭了 `AVD_API_37` (`-no-snapshot-save`, 未改动 AVD 状态); 会话开始时列出的 API 27 模拟器 (`emulator-5554`) 在启动新模拟器前已不在 `adb devices` 中, 原因未查.
- 未做: P0.2 覆盖开关与脚本, P1 起全部代码改动.
- 下次会话建议起点: P0.2 (覆盖开关, 供 P2 全程使用) 与 P1 (compileSdk 37, 改动极小) 可同一会话完成; 随后 P2.1 / P2.3 / P2.4 三项影响最大且互不依赖, 可并行推进.

### 2026-09-13 执行条件复核

- 保留 D1-D4 与原探针历史结果; 修正 "全仓编译" 的证据范围, 增加 ANDROID_COMPILE / PLATFORM_CONFIG 等级, 去掉由页面缺席推断 "明确未变" 的结论.
- 补齐 P0.2 的有效 lint 入口与 release 覆盖拒绝要求, 修正 adb 多 change / 缺 package 示例及 Gradle 通配任务路径; 音频默认规则与设备级诊断模式分开验收.
- 修正 P2.5 的 creator / sender / broadcast 角色, P2.6 的用户 CA / CT / ECH 前提, P2.1 的权限声明与跨 profile 回环边界; 增加 a11y IME 事件验证及 P2.16 的 inrt 切换前适配.
- 核实插件实际配置例外: R8-Compiler 的 SDK 硬编码与固定 android-36.jar, Paddle v4 / v5 / v6 的 minSdk 26; SDK 日志分类遵循当前 AGENTS.md, 移除非必需的 Bun 平台插件顺带升级.
- 本次仅修订规划与证据, 未执行 Gradle 构建 / lint / 设备测试, 未将 P0.2 或 P1-P6 标为完成. 检查清单与来源见 [复核记录](evidence/android-sdk-37-review-20260913.md).

### 2026-09-13 P2.3 实施

- 开始前检查全部待提交工作, 既有 MCP 截图改动由同时工作的会话提交为 `dd521ed5d`, 确认干净后继续 SDK 适配. 期间新出现的 MCP / Node bridge 改动不混入本轮逻辑提交.
- `a784273d2`: OpenCV / ImageQuant 原生库临时文件, 发布文件与旧缓存均强制验证只读; 38 项 JVM 测试通过, API 37 target 36 强制 / target 37 默认及 API 24 的 JNI 测试通过. 修正初稿 "未设只读" 为 "已有尝试但忽略失败结果".
- 旧系统回归发现 API 24 原始 DEX 优化输出与输入重合并复现; API 24 / 25 改用独立目录, API 26+ 保持原路径. 新增原始 DEX 设备测试及实际 DEX / JAR 两次加载脚本. 既有 Java 8 JAR 编译器限制另列附录 F.
- 完成范围与 APK / 设备 / 日志详见 [P2.3 实施记录](evidence/android-sdk-37-p23-20260913.md). 正常 target 保持 36 / 29, P2.3 已勾选; 其余 P2 项目与 P3-P6 待实施, P1 的远程 CI 与既有 lint 状态保持原记录.

### 2026-09-13 P2.4 实施

- 完成三个 Looper / 线程开关的单项、组合与真实 target 37 默认行为验证, 增加 14 项设备测试及专用模拟器复跑工具. 组合与 target 37 的持续 interval 均运行 2 分钟, 完整 a11y 冒烟四组各 53/53.
- `705cb04ad`: 基线发现取消远期定时器后脚本不退出, 修复 Timer 实际工作登记、唤醒与线程退出清理. API 24 回归、6 项 Looper JVM 测试及 app / inrt debug 构建通过.
- 复核 start 的声明与继承关系, 没有宿主 Thread.start 初始化需要迁移. 调试器确认 ThreadCompat 取消状态能跨平台原生标记清除而保留; 平台反射限制与 util.java 文档评估写入附录 F.
- P2.4 已勾选, 正常 target 仍为 host 36 / inrt 29. 同时进行的 MCP / Node 改动由对应会话维护, 不混入本轮逻辑提交. [完整证据](evidence/android-sdk-37-p24-20260913.md).
