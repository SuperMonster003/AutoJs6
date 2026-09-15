# Android SDK 37: P5 插件舰队第三批 (2026-09-16 凌晨)

## 1. 结果与范围

维护者修复了第二批的发现 (Archive-Manager / MCP-Server 的既有失败, OpenCC / Three-Stone-AI 的环境固定测试改为 `assumeTrue` 跳过, Screen-Color-Picker 的对话框窗口释放) 并提交了 Image-Quantization / TypeScript-Engine 的工作区. 本批: (1) 在同一 AVD 上用 HEAD 全量 androidTest 复核这 5 个仓库; (2) 对暴露只剩 "公网更新检查 / WebView 文档" 或根本没有暴露的仓库执行 targetSdk 37 步骤 (`fleet.py bump --target 37`). 设备仍只用 `AVD_API_37.1_16K` (`emulator-5600`, x86_64).

### 1.1 复核 (HEAD, 不排除任何测试)

| 仓库 | HEAD | 结果 | 备注 |
| --- | --- | --- | --- |
| Archive-Manager | `534cd7c` | OK 180 | 上批排除的 `manageArchiveRejectsZipContainerAliases` 已通过 |
| MCP-Server | `e591d6e` | OK 35 | 上批排除的空闲停止通知测试已通过 (`2d945e2` 覆盖通知权限拒绝 / 授予两种情况) |
| OpenCC | `9b214f7` | 9 项 1 失败 | `OpenccStandaloneUiTest#allStandaloneActionsRemainExplicitCancellableAndRestorable` (第 3.2 节); 四个多阶段测试按 `assumeTrue` 跳过 |
| Three-Stone-AI | `fbfad9c` | OK 3 | 真实模型测试在未 staging 时 `assumeTrue` 跳过 |
| Screen-Color-Picker | `45a6850` | OK 10 | 上批不稳定的 `overlayExplanationSurvives...` 通过 |

### 1.2 targetSdk 37 步骤

| 仓库 | 提交 | 版本 (build) | AVD instrumentation | 暴露判断 |
| --- | --- | --- | --- | --- |
| MLKit-Barcode | `57e88a0` | 1.0.4 (17) | OK 1 | manifest 以 `tools:node="remove"` 去掉 INTERNET, 合并后无暴露 (扫描器已修正, 第 3.3 节) |
| HTML-Previewer | `ce965bf` | 1.1.2 (22) | OK 42 | WebView 只加载插件自有 origin 的文档, 外部资源默认拦截 (计数提示), INTERNET 不用于局域网 |
| Markdown-Previewer | `ed7c37b` | 1.2.2 (18) | OK 28 | 同上 |
| Ace-Editor | `271e3c9` | 1.7.2 (99) | OK 23 (第二次; 第一次 1 失败, 第 3.1 节) | INTERNET 用于字体下载 (公网), LSP 回环; WebView 加载 assets 编辑器 |
| OpenCC | `1e5bfc9` | 1.4.2 (102) | OK 8 (排除 1, 第 3.2 节) | INTERNET 用于 GitHub 更新检查 |

三个 WebView 插件的 target 37 证据是各自 UI instrumentation (渲染、锚点、缩放、无障碍焦点等) 在 target 37 构建上通过; UA 缩减与 File System Access 没有单独的测试, 记为 "套件通过" 而非逐项核对.

未做 target 步骤的仓库及原因: NodeJs / Bun / Python-Runtime 与 MCP-Server 要先做 `ACCESS_LOCAL_NETWORK` 声明与引导 (P5.3 本地网络条目); Screen-Color-Picker (`mediaProjection` + 悬浮窗取色), Three-Ember / Terra (`mediaPlayback` 后台音频强化) 的套件不含真实取色 / 播放, 需要单独的设备探针; Three-Stone-AI 的内存上限推理需要 staging 真实模型 (`.python/run_real_model_test.py`, 公网下载); Accessibility-Compat 需 P2.14 / D16 的说明与 `WRITE_SECURE_SETTINGS` 复测.

## 2. 发布说明用语

仅 target: "在 compileSdk 之后将 targetSdk 提升到 37 (Android 17), 插件行为不依赖新目标版本" (`BUMP_NOTES_TARGET_ONLY`), 提交信息 `chore(sdk): raise targetSdk to 37 (Android 17)`. 五个仓库的 INTERNET 只用于 GitHub 更新检查 / 字体下载 / 自有 origin 的 WebView, 不涉及局域网, 所以 Android 17 的本地网络保护不改变行为.

## 3. 发现

### 3.1 Ace-Editor: Lua 语义冒烟测试的时序 (WATCH)

第一次 target 步骤 23 项中 `AceLuaSemanticSmokeTest#bundledLuaLanguageServerPassesSemanticRecoveryAndReleaseGate` 失败: "ACE semantic service switched to static fallback after slow lua-signatureHelp (1548 ms)". 用同一 universal APK 单跑两次均通过; 用 `-arm64-v8a` 拆分包误装时 LuaLS 根本起不来 (x86_64 AVD), 这是装错包, 不是产品问题. 第二次 target 步骤全量 23 项通过并提交 `271e3c9`; 时序阈值与 AVD 负载有关, 留作观察.

### 3.2 OpenCC: 独立入口剪贴板 UI 测试在全量套件里失败, 单跑通过 (WATCH)

`OpenccStandaloneUiTest#allStandaloneActionsRemainExplicitCancellableAndRestorable` ("Timed out waiting for explicit Paste action after 4 foreground user taps") 在 HEAD 复核与 target 步骤的全量套件中失败 (第二批 compile 步骤时通过过一次), 单独运行该类两次都 OK, `ClipboardService` 没有拒绝记录; 与 SDK 级别无关 (HEAD compile 37 / target 36 同样失败), 疑与套件顺序 / 前台焦点有关, 留给维护者. target 步骤以方法级 `notClass` 排除并写入步骤明细.

### 3.3 扫描器: `tools:node="remove"` 的权限不算暴露

MLKit-Barcode 声明 INTERNET 只是为了从合并 manifest 里去掉它, 扫描器把它列为暴露并让该仓走了两步走; `2156eceab` 起忽略带 `tools:node="remove"` 的 `uses-permission`.

### 3.4 Three-Stone-AI 不用 NPU

`LiteRtLmEngineFactory` 只在 `Backend.CPU(...)` / `Backend.GPU()` 之间选择 (litertlm-android 0.15.0), 无 NPU / QNN / NNAPI 引用, 因此 target 37 不需要 `android.hardware.npu` 特性声明; 该结论写入 P5.4 条目, 内存上限推理仍待真实模型.

### 3.5 其它

- 官方索引 `AutoJs6-Official-Plugins-Index` (`df2eb35`): 生成器 `tools/generate_official_plugin_index.py` 与 `plugins.official.generated.json` 都没有任何 SDK 字段, P4 的 "无 targetSdk 字段假设" 成立.
- 磁盘: 删除 `build/sdk37-p0-p1` (3.0 GB, P0 / P1 的 APK / AVD 日志副本, 证据文档已摘录) 后 D: 剩 40 GB; 其余 `build/sdk37-*` 目录 (p33 1.4 GB, p24 0.9 GB, p212-followup 0.9 GB, inrt-storage 0.7 GB, p27 0.6 GB 等) 同属可再生的历史产物, 需要空间时可一并删除.
- 上批 chain 脚本的 `--readme-keys` 在 target 步骤重复传入时因 "nothing to change" 失败 (设计如此: 无变化视为键错); README 已说明只在改变对应级别的那一步传键 (`1380c56e7`).

## 4. 工具与命令

- 复核: `build/sdk37-fleet/chain11_reverify.sh` (HEAD 构建 -> 卸载 -> 装 universal 包 -> 全量 `am instrument`); target 步骤: `chain12_target.sh` / `chain13.sh` / `chain14.sh` (不入库).
- 扫描: `py tools/sdk37/fleet.py scan --output build/sdk37-fleet/scan-20260916-p5e.json` / `table` 复扫见附录 E.
