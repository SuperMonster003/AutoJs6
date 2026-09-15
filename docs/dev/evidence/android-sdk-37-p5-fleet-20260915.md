# Android SDK 37: P5 插件舰队第一批 (2026-09-15)

## 1. 结果与范围

按 D11 / D21 用 `tools/sdk37/fleet.py bump` 逐仓完成 "无暴露且工作区干净" 的 17 个插件仓库的 compileSdk / targetSdk 37 发布步骤, 每仓一次提交, 设备验证只用 `AVD_API_37.1_16K` (API 37.1, x86_64, 16 KB 页, `emulator-5600`). 宿主 HEAD 为 `92335f13b` 之前的 targetSdk 37 调试包 (versionCode 5280) 与其测试包已装在该 AVD 上.

每仓的步骤 (全部通过才提交): `version.properties` (SDK 37 / 37, 下一补丁版本, `VERSION_BUILD` = 提交数 + 1) -> 十语 `.changelog` 一行 `improvement` -> `.readme/common.json` 的 SDK 数字与版本 (若有) -> 模板正文中的旧目标级别 (`--readme-keys`) -> `generate_markdown.py` 与 `--check` -> `check_repository.py --pending-commit` (若有) -> `assembleDebug testDebugUnitTest assembleDebugAndroidTest` -> AVD 安装 + `dumpsys` 核对 installed targetSdk = 37 -> 运行该仓的 androidTest -> `git diff --check` -> 固定 message `chore(sdk): raise compileSdk and targetSdk to 37 (Android 17)` 提交并核对提交数.

| 仓库 | 提交 | 版本 (build) | AVD instrumentation | 备注 |
| --- | --- | --- | --- | --- |
| Pinyin | `2527cd5` | 1.0.3 (18) | OK 7 | |
| Pinyin4j | `28507f5` | 1.3.2 (25) | OK 2 | |
| MediaInfo | `48bb6fb` | 2.1.3 (51) | OK 16 | `.readme/common.json` 的 `android_target_sdk` 同步 |
| ImGui | `ee9054d` | 1.9.3 (15) | OK 5 | |
| Image-Tools | `9210c49` | 1.2.1 (14) | OK 2 | |
| Image-Viewer | `eb0fde2` | 1.3.1 (13) | OK 48 | |
| OpenCV | `1101000` | 1.1.4 (22) | OK 1 | |
| Image-Quantization | `24d1ea8` | 1.0.3 (22) | OK 16 (排除 1, 第 3.1 节) | 首次 17 项跑出 1 失败, HEAD 复现相同 |
| Offline-Docs | `2e253cd` | 6.8.2 (42) | OK 2 | |
| TypeScript-Engine | `831f64e` | 0.6.3 (76) | OK 94 (排除 10, 第 3.2 节) | 首次 104 项跑出 10 失败, HEAD 复现相同 10 项 |
| File-Inspector | `7035ef1` | 1.1.1 (17) | OK 18 | `p_build_params` 十语 "target SDK 36" -> 37 |
| APK-Inspector | `59387a6` | 1.2.1 (39) | OK 2 | 同上 |
| QuickJS-Runtime | `947e4ab` | 1.0.3 (14) | OK 3 | `p_build_intro` 十语 "Android SDK 36" -> 37 |
| Java-Runtime | `9ef0b46` | 0.8.2-m9 (56) | OK 15 (排除 1, 第 3.3 节) | 里程碑后缀保留, changelog 键按该仓先例为 `v0.8.2`; `common.json` 的 `version_name` / `version_build` 同步 |
| Kotlin-Runtime | `3038600` | 0.7.2-m10 (27) | OK 2 (排除 1, 第 3.3 节) | 同上, changelog 键 `v0.7.2` |
| DEX-Compiler | `8383eeb` | 1.1.2 (44) | OK 2 | 前置修复 `aed6e7c` (第 3.4 节) |
| APK-Builder-Template | `41f06fd` | 1.0.3 (44) | 三个打包示例 3 / 3 PASS | 手工并入未发布的 1.0.3; CI 断言见第 4 节 |

以上 17 仓 `fleet.py table` 复扫均为 `37 / 37` (`build/sdk37-fleet/table-20260915-p5b.md`, 不入库). 原始逐步日志: `android-sdk-37-p5-fleet-results-20260915.json` (含失败的首次尝试).

未包含: 两步走 / 有暴露的仓库 (INTERNET, 前台服务, WebView, 无障碍等), 脏工作区仓库 (MCP-Server 4 个文件且 P6 进行中, Lua, MLKit-OCR, Paddle, Rapid, Yolo, Accessibility-Compat), Gradle 硬编码仓库 (Python-Runtime, R8-Compiler), Bun-Runtime (另一会话进行 roadmap 任务), Archive-Manager (仅 target 步骤, 存储暴露); 各仓库的发布与 tag; 真机.

## 2. 发布说明用语

内置十语 (`fleet.py` `BUMP_NOTES`), 仅用于行为不依赖新目标版本的插件; en: "Raise compileSdk and targetSdk to 37 (Android 17); the plugin's behavior does not depend on the new target"; zh-Hans: "将 compileSdk 与 targetSdk 提升到 37 (Android 17), 插件行为不受新目标版本影响". 有暴露的仓库日后用 `--note-file` 传各自说明.

## 3. 发现

### 3.1 Image-Quantization: 金样与环境绑定 (WATCH)

`PngQuantDocumentationInstrumentedTest.readmeComparisonArtifactIsReproducibleAndPortable` 在 x86_64 上按字节比对 `market-quantized.png` (724168 字节). 在 API 37.1 AVD 上输出 709458 字节, 首个差异在第 838 字节 (PLTE 之后的 IDAT 内), 而 quality 69 / error 9.8327 与金样常量完全一致, 即调色板与像素相同, 只是 deflate 输出不同 (平台 zlib 差异). 用 `git stash` 回到 HEAD (target 36, `673e30b`) 在同一 AVD 复跑: 同样失败且数值相同 (`build/sdk37-fleet/imagequant-head-check.log`). 与本次升级无关; 提交时以 `--instrument-arg notClass ...PngQuantDocumentationInstrumentedTest` 排除并记入步骤明细. 建议维护者把该测试改为比较解码后的像素或按 API 级别分金样.

### 3.2 TypeScript-Engine: 10 项在 API 37.1 上与目标无关地失败 (WATCH)

首次 104 项跑出 10 失败 (`R1AcceptedLifecycleInstrumentationTest` 1 项: 远端进程 FD 采样时进程身份变化; `R2BinderCompilationInstrumentationTest` 7 项: 4 项 "R2 capacity boundary requires a source above the single-file limit", 契约 `INVALID_REQUEST` vs `CONTRACT_MISMATCH`, 字段集合含 `diagnosticsQuotaRevision`, 金样 sha 不同; `R2ProjectCompilerInstrumentationTest` 2 项). HEAD (`becbfe9`, target 36, v0.6.2) 在同一 AVD 复跑失败集合完全相同 (`build/sdk37-fleet/typescript-head-failed.txt` 与 `typescript-failed-t37.txt` diff 为空). 与本次升级无关; 提交时以 `notClass 类#方法` 列表排除这 10 项, 其余 94 项通过. 测试源码不引用宿主包, 差异来自插件自身夹具 / 契约在 API 37.1 上的表现, 留给维护者.

### 3.3 Java-Runtime / Kotlin-Runtime: 只读 DEX 证据测试只接受 API 34 / 36

`JvmSourceR2ReadOnlyDexInstrumentationTest.diskDexIsReadOnlyBeforeFirstContentByte` 第一行即 `assertTrue(SDK_INT == 34 || SDK_INT == 36)`, 且需要 run id / 源码树 / 宿主 APK 摘要等 instrumentation 参数, 是给 API 34 / 36 出证据的生产者测试, 在 API 37.1 上按设计失败. 以 `notClass` 整类排除, 其余 15 / 2 项通过. 两仓 `VERSION_NAME` 带里程碑后缀 (`0.8.1-m9`, `0.7.1-m10`), 按各自 AGENTS.md "不得静默升为稳定版" 保留后缀递增补丁号; changelog 键沿用该仓先例 (顶部 `v0.8.1` 不带后缀 -> 新键 `v0.8.2`). 两仓根目录 `CHANGELOG.md` 为生成物, `bump` 首次提交步骤把它当作意外文件拒绝, 手工以同一 message 提交后已修正 (`cd09e6058`).

### 3.4 DEX-Compiler: 手写的 `platforms/android-<level>` 路径

`app/build.gradle.kts` 给 R4 D8 矩阵单测传 `platforms/android-${compileSdk}/android.jar`; SDK 中 API 37 平台目录名为 `android-37.0`, compileSdk 37 后 `testDebugUnitTest` 配置校验报输入文件不存在. 修复 `aed6e7c` (build 43): 改为取 AGP 的 `sdkComponents.bootClasspath` 中的 `android.jar`, compile 36 / 37 均通过; 之后 `8383eeb` 完成升级. 其它 16 仓未见同类手写路径 (`platforms/android-` 全文检索).

### 3.5 README 模板中的旧级别

`.readme/common.json` 只在部分仓库有 `android_target_sdk` (MediaInfo 等), File-Inspector / APK-Inspector / Java-Runtime / Kotlin-Runtime 的 `p_build_params` 与 QuickJS-Runtime 的 `p_build_intro` 在十种语言的正文里写死 "36"; `bump --readme-keys <key>` 只改该键值里的独立数字, 其余仍提 `SDK 36` / `API 36` / `targetSdk 36` / `Android 16` 的模板行以 `[WARN]` 列出 (本批无残留).

## 4. APK-Builder-Template (P5.2)

- `41f06fd`: SDK 37 / 37, `VERSION_BUILD` / `PLUGIN_VERSION_BUILD` 44, 十语 changelog 追加到未发布的 v1.0.3 (与 `ce6dfd9` 的 resources.arsc 修复、`35846fb` 的开关移除同一版本); `.github/workflows/build-from-runtime-kit.yml` 在 zipalign 记录之后用 `aapt dump badging` 断言 kit 的 `template.apk` `targetSdkVersion` 等于 `version.properties` 的 `TARGET_SDK_VERSION`. 插件测试与 `scripts/` 无 targetSdk 硬编码 (P3.4 已核).
- 本地门禁: `generate_markdown.py` / `--check` / `check_repository.py --pending-commit` / `:app:assembleDebug :app:testDebugUnitTest` 通过; `:app:assembleRelease -Pautojs.apkBuilder.templatePlugin.runtimeKitDir=<5280 universal kit>` 产物 badging `targetSdkVersion 37`, 内嵌 `assets/runtime-kit/template.apk` 为 inrt 5280 target 37.
- AVD: 覆盖安装该发布包 (installed targetSdk 37) 后 `run_packaged_examples.py` 三个示例全部 PASS (`android-sdk-37-p5-apk-builder-examples-20260915.md` / `-results-20260915.json`).

## 5. 工具与命令

- 提交前工具 (`92335f13b` bump 步骤, `cd09e6058` 根 CHANGELOG 前缀); 单测 `tools/sdk37/test_fleet.py` 13 项.
- 每仓命令: `py tools/sdk37/fleet.py bump <repo> --compile 37 --target 37 --adb E:/.android/sdk/platform-tools/adb.exe --serial emulator-5600 --sdk-root E:/.android/sdk --log build/sdk37-fleet/bump-log.json --commit [--readme-keys p_build_params|p_build_intro] [--instrument-arg notClass <类[#方法],...>]`.
- HEAD 对照: `build/sdk37-fleet/head_check.sh <repo> <applicationId> <输出前缀>` (stash -> 构建 -> 安装 -> 全量 instrumentation -> 失败清单 -> stash pop), 用于 3.1 / 3.2.
- AVD 启动: `emulator.exe -avd AVD_API_37.1_16K -read-only -no-window -no-audio -no-snapshot-save -no-boot-anim -port 5600`; 会话结束时关闭.
