# Android SDK 37: P5 插件舰队第二批 (2026-09-15 深夜)

## 1. 结果与范围

第一批 (17 仓, [上一份证据](android-sdk-37-p5-fleet-20260915.md)) 之后, 维护者修复了 4 条发现并提交了曾脏的仓库. 本批用扩展后的 `tools/sdk37/fleet.py bump` (`fae5bcf49`) 处理剩余仓库: 无暴露的仓库仍一次到 37 / 37; 有暴露的仓库按 D11 两步走, 本批只做 compileSdk 37 的独立提交 (targetSdk 保持 36, 说明写在十语 changelog 里), target 步骤等各自的暴露复测. 设备验证只用 `AVD_API_37.1_16K` (`emulator-5600`): 安装 + `dumpsys package` 的 targetSdk 核对 + 全部 androidTest (环境固定的测试以 `notClass` 排除并写进步骤明细).

| 仓库 | 步骤 | 提交 | 版本 (build) | AVD instrumentation | 前置提交 / 备注 |
| --- | --- | --- | --- | --- | --- |
| Yolo-NCNN | 37 / 37 | `d4e9bbb` | 0.1.3 (36) | OK 5 | |
| Lua-Runtime | 37 / 37 | `050ca26` | 0.1.2-rc.2 (77) | OK 5 | provider / nativeTest / faultTest 三 flavor, 工具改为按 flavor 目录取 APK; changelog 键按先例 `v0.1.2` |
| MLKit-OCR | 37 / 37 | `61970ac` | 1.0.3 (16) | OK 1 | wrapper 9.5.0 `56cb291` |
| Paddle-OCR-PP-OCRv3 | 37 / 37 | `47d795c` | 1.0.5 (22) | OK 1 | wrapper `b0fe113`; `p_build_params` 十语 |
| Paddle-OCR-PP-OCRv4 | 37 / 37 | `e679494` | 1.0.4 (22) | OK 1 | wrapper + 库模块 source-set 覆盖删除 `947781c`; `p_build_params` 十语 |
| Paddle-OCR-PP-OCRv5 | 37 / 37 | `eab79a0` | 1.0.4 (24) | 安装 + target 核对 (无 androidTest) | `754ec1c` 同上 |
| Paddle-OCR-PP-OCRv6 | 37 / 37 | `160aaa5` | 1.0.4 (26) | 安装 + target 核对 (无 androidTest) | `27f442a` 同上 |
| Rapid-OCR-PP-OCRv3 | 37 / 37 | `815893d` | 1.0.5 (25) | OK 1 | wrapper `e9e2331`; `p_build_params` 十语 |
| Rapid-OCR-PP-OCRv6 | 37 / 37 | `0d535dc` | 1.0.5 (27) | OK 1 | wrapper `5c0e01e`; 首次因 D: 盘写满在 AAR 打包失败 (第 3.6 节) |
| R8-Compiler | 37 / 37 | `a2f4f75` | 0.2.2-provider-dev (20) | OK 3 | `e0b13cb` plugin-api 改读 `version.properties`; `9af84bd` retrace 设备测试改 JUnit4 (第 3.4 节); README 十语只改 targetSdk 措辞 (`--replace-json`), `android-36.jar` 保持 |
| Archive-Manager | 仅 target | `4b308f9` | 2.22.3 (106) | OK 178 (排除 1) | compileSdk 37 早已落地; 排除 `ArchiveIntentPolicyInstrumentationTest#manageArchiveRejectsZipContainerAliases` (第 3.6 节) |
| HTML-Previewer | 仅 compile | `3ecc6e2` | 1.1.1 (21) | OK 42 | `62d2126` manifest 契约测试接受拆分权限 (第 3.1 节) |
| Markdown-Previewer | 仅 compile | `6b441c4` | 1.2.1 (17) | OK 28 | |
| Screen-Color-Picker | 仅 compile | `01e229f` | 1.1.1 (13) | OK 10 | 一项 Espresso 焦点等待不稳定 (第 3.5 节) |
| Three-Ember-Player | 仅 compile | `fcfea36` | 3.1.1 (26) | OK 35 | `3572152` `Display.getHdrCapabilities()` 可空 (第 3.3 节) |
| Three-Terra-Player | 仅 compile | `da4ef76` | 1.6.1 (36) | OK 48 | |
| Three-Stone-AI | 仅 compile | `9666466` | 1.1.3 (189) | OK 2 (排除 1) | 排除需人工 staging 模型的 `LiteRtRealModelInferenceInstrumentationTest`; 该仓拒绝非 ASCII 标点, 阿拉伯语说明改 ASCII 逗号 |
| OpenCC | 仅 compile | `822afed` | 1.4.1 (100) | OK 6 (排除 3) | `2b1ac48` 契约测试接受拆分权限; 排除三个需 `-e` 阶段参数的多阶段测试; `p_build_requirements` 十语 |
| Ace-Editor | 仅 compile | `62d5d9e` | 1.7.1 (98) | OK 23 | wrapper `3501db2`; `p_build_requirements` 十语 |
| MLKit-Barcode | 仅 compile | `10f00bc` | 1.0.3 (16) | OK 1 | wrapper `413aaaa` |
| NodeJs-Runtime | 仅 compile | `c8da4bf` | 1.5.2 (173) | OK 61 | wrapper `e02eec5` |
| Python-Runtime | 仅 compile | `f20f364` | 0.5.3 (108) | OK 1 | `4ea9fe5` app 改读 `versions.sdkVersion*`; `locks/python-runtime.lock` 的 `android.compileSdk` 用 `--replace` 同步 |
| MCP-Server | 仅 compile | `41e53b5` | 1.0.1 (42) | OK 34 (排除 1) | 维护者 P6 提交 `4f6a5a9` 后工作区干净; 排除 `McpServerIdleStopTest#aListenerWithoutRequestsStopsItselfAfterTheConfiguredMinutes` (第 3.6 节); `p_build_requirements` 十语 |

维护者侧同日完成的相关提交 (本会话未动): Bun-Runtime `8b5b097` compileSdk 37 (0.2.3, target 36, 平台插件 1.7.4 + wrapper 9.5.0); Accessibility-Compat `b21e3a7` / `7456885` (compile 37, target 36, 工作区已干净); Java-Runtime `79d1d11` / Kotlin-Runtime `2d7148d` (证据测试接受 API 34+, 常规跑仍需 instrumentation 参数, 保留 `notClass`); DEX-Compiler `188c35e` (延迟解析 boot classpath).

复核 (上批发现 3.1 / 3.2): Image-Quantization `24d1ea8` 与 TypeScript-Engine `831f64e` 在 HEAD 重建后于同一 AVD 分别 OK 3 (`PngQuantDocumentationInstrumentedTest`) 与 OK 104 (全套), 上批失败项未再现. 会话末复扫发现两仓工作区各有维护者未提交的修改 (Image-Quantization 25 个文件, 含 `PngQuantDocumentationInstrumentedTest.kt` 与文档; TypeScript-Engine 7 个文件, 含 R1 / R2 测试与 `CompilerHostProtocol.kt`), 复核构建带着这些改动, 通过应归于维护者的修复而非 HEAD; 两仓的 37 / 37 提交在这些改动之前, 本会话未动它们.

原始逐步日志: `android-sdk-37-p5-fleet-results-batch2-20260915.json` (含失败的首次尝试; 由 `build/sdk37-fleet/bump-log.json` 截取本批条目).

## 2. 发布说明用语

- 37 / 37: 十语 `improvement` 与上批相同 ("将 compileSdk 与 targetSdk 提升到 37 (Android 17), 插件行为不依赖新目标版本").
- 仅 compile: "将 compileSdk 提升到 37 (Android 17), targetSdk 保持 36, 待依赖目标版本的行为验证后再提升" (`BUMP_NOTES_COMPILE_ONLY`); 提交信息 `chore(sdk): raise compileSdk to 37 (Android 17)`.
- 仅 target (Archive-Manager): "在 compileSdk 之后将 targetSdk 提升到 37 (Android 17), 插件行为不依赖新目标版本" (`BUMP_NOTES_TARGET_ONLY`); 提交信息 `chore(sdk): raise targetSdk to 37 (Android 17)`.
- 阿拉伯语三条说明改用 ASCII 逗号: Three-Stone-AI / MCP-Server 的 `ApplicationTextPunctuationTest` 拒绝打包文本中的任何非 ASCII 标点 (U+061B / U+060C).

## 3. 发现

### 3.1 API 37 把 `ACCESS_LOCAL_NETWORK` 从 INTERNET 拆分给旧 target (P5.3 本地网络条目)

HTML-Previewer 与 OpenCC 的 manifest 契约测试断言 `requestedPermissions` 恰为 INTERNET + 插件权限 + receiver 保护权限, 在 API 37.1 AVD 上多出 `android.permission.ACCESS_LOCAL_NETWORK`. APK 本身不含该权限 (`aapt2 dump permissions`), 是系统在安装时按 `/system/etc/permissions/platform.xml` 的 `<split-permission name="android.permission.INTERNET" targetSdk="37">` (特性开关 `access_local_network_permission_enabled`) 补上的, `dumpsys package permission` 显示 `prot=dangerous`. 含义: target <= 36 的带 INTERNET 应用在 Android 17 上自动持有该权限的申请项 (与旧位置权限拆分同型); target 37 起不再拆分, 需自行声明并在运行时申请. 两个仓库的测试改为 "SDK_INT >= 37 且 targetSdkVersion < 37 时额外期望该权限" (`62d2126`, `2b1ac48`); Node / Bun / Python / MCP-Server 的声明与提示仍在 P5.3 / P5.4 条目.

### 3.2 平台插件按 Gradle 版本封顶 AGP; AGP 9.3 库模块拒绝 source-set 覆盖

`autojs6-platform-versions` 1.8.1 按 wrapper 版本选最高 AGP: Gradle 9.3.0 只到 9.0.1, 而 compileSdk 37 需要 AGP 9.1.1+ ("The highest AGP available ... on Gradle 9.3.0 is 9.0.1, but this project requires AGP 9.1.1 or higher"). 10 个仓库先单独提交 wrapper 9.3.0 -> 9.5.0 (解析到 AGP 9.3.2, Yolo-NCNN 已验证): MLKit-OCR `56cb291`, Paddle v3 `b0fe113`, v4 `947781c`, v5 `754ec1c`, v6 `27f442a`, Rapid v3 `e9e2331`, v6 `5c0e01e`, Ace-Editor `3501db2`, MLKit-Barcode `413aaaa`, NodeJs-Runtime `e02eec5`. Paddle v4-v6 的 `libs/ppocr-android-sdk` 与 `libs/ppocrvN-plugin-runtime` 在 AGP 9.3 下配置失败 (`ClassCastException: DefaultAndroidLibrarySourceSet_Decorated -> AndroidLibrarySourceSet`), 原因是库模块脚本的 `sourceSets.named("main") { kotlin.directories += "src/main/java" }`; AGP 内建 Kotlin 已编译 `src/main/java`, 删除该块即可 (app 模块不受影响).

### 3.3 SDK 37 存根的可空标注 (Three-Ember-Player)

compileSdk 37 下 `Display.getHdrCapabilities()` 标为可空, `VideoPlayerActivity.supportedHdrTypes()` 直接取 `.supportedHdrTypes` 编译失败; `3572152` 改为 `?.supportedHdrTypes ?: IntArray(0)` (无 HDR 能力的显示器返回空数组). 这是本批唯一的源码级适配.

### 3.4 R8-Compiler: JUnit3 形式的设备测试在任何设备都失败

`R8RetraceAndroidTest : TestCase()` 让 androidx 运行器走 JUnit3 路径, 其中 `AndroidTestResult` 引用 `android.test.AndroidTestCase`; target >= 28 的包没有隐式 `android.test.base`, 于是 `NoClassDefFoundError`, "failed: null". 用 HEAD (36 / 36) 的测试 APK 复现相同. `9af84bd` 改为 JUnit4 (`@Test` + `org.junit.Assert.assertTrue`, 断言不变), 之后 3 项通过; 与升级无关.

### 3.5 Screen-Color-Picker: 一项 Espresso 焦点等待不稳定 (WATCH)

`overlayExplanationSurvivesActivityRecreationAndCancelRestoresInactiveState` 在 37 构建上两次失败 (`RootViewWithoutFocusException` 等窗口焦点 10 s; 另一次 `NoActivityResumedException`), HEAD 构建单跑一次通过, 37 构建第三次全套通过并提交 `01e229f`. 未找到与 compileSdk 相关的机制, 记为不稳定项, target 步骤复测悬浮窗时再看.

### 3.6 其它

- D: 盘在 Paddle / Rapid 连续全量构建后写满 (0 GB), Rapid v6 的 `bundleDebugAar` 报 "Could not add file ... to ZIP" (`--stacktrace` 见 `There is not enough space on the disk`); 删除已提交仓库的 `app/build` / `libs/rapidocr/build` 后 (释放 41 GB) 重跑通过. 构建目录不入库, 但连续多仓构建前应预留空间.
- Archive-Manager 的 debug 产物有四个 ABI 拆分包加一个通用包, 工具原先取按名排序的第一个 (`-arm64-v8a`), 契约测试 `pluginInfoDeclaresPackagedNativeAbisForExplorerEngine` 因此只看到 1 个 ABI; 工具改为优先无 ABI 后缀 / `universal` 的包.
- MCP-Server `McpServerIdleStopTest#aListenerWithoutRequestsStopsItselfAfterTheConfiguredMinutes` 等待空闲停止通知超时: 测试不申请 `POST_NOTIFICATIONS`, 全新安装时 `granted=false`, 通知不会进入 `activeNotifications`; HEAD (36 / 36) 构建在同一 AVD 同样超时, 与升级无关, 留给维护者 (`GrantPermissionRule` 或 `pm grant`).
- Archive-Manager `ArchiveIntentPolicyInstrumentationTest#manageArchiveRejectsZipContainerAliases` 期望 `library.jar` / `.aar` / `.war` 以 `application/zip` 进入 MANAGE 动作时被拒绝, 实际返回了请求; HEAD (target 36) 构建同样失败, 与 target 无关, 留给维护者.
- OpenCC 的 `[WARN]` README 提示含韩文时在 GBK 控制台崩溃 (`UnicodeEncodeError`), 工具在 `main()` 把 stdout / stderr 切到 UTF-8.
- R8-Compiler 的 `VERSION_NAME=0.2.1-provider-dev` 含两段后缀, 工具的版本解析改为接受连字符后缀 (`0.2.2-provider-dev`, changelog 键按先例 `v0.2.2`).

## 4. 工具与命令

- `py tools/sdk37/fleet.py bump <repo> --compile 37 [--target 37] --adb ... --serial emulator-5600 --sdk-root E:/.android/sdk --log build/sdk37-fleet/bump-log.json --commit [--readme-keys k1,k2] [--replace PATH OLD NEW] [--replace-json FILE] [--instrument-arg notClass a.b.C,a.b.D]`; 只给 `--compile` 或 `--target` 时使用对应的提交信息与十语说明.
- 批次脚本 `build/sdk37-fleet/chain4.sh` ... `chain9_headcheck.sh` (不入库) 顺序驱动单台 AVD; 同一设备不并行跑 instrumentation (本批一次并行导致 OpenCC 的前台 UI 测试超时, 重跑通过).
- 附录 E 行由 `build/sdk37-fleet/update_appendix.py` 从 bump 日志回填 (仅 compile 的行写 "37 / 36" 并注明待暴露复测).
