# SDK 37 P2.12: 编辑器选区, 主页搜索与 IME 布局修复

日期: 2026-09-14. 会话从干净的 `master@852df35c4` 开始.
生产修复与十语言更新日志提交 `23cc3a03e`, 探针和判定测试提交 `e73f5c4f8`.
承接 [首次 P2.12 记录](android-sdk-37-p212-20260914.md), 修复宿主 Main / Edit / Log
在真实旋转、分屏重建和软键盘布局中的问题. P2.12 的实体机、外设、悬浮界面和连续窗口调整
验收继续开放, 不将本轮界面矩阵当作全部 SDK 37 迁移完成.

## 1. 先纠正状态判定

旧探针把 Ace 引擎 `isTextChanged=false` 当成宿主丢失未保存状态.
生产 `EditorView.shouldSaveDraft()` 实际综合了引擎标记和宿主 `saveStickyDirty`.
`restoreDraftTextForThisSession()` 已调用 `markRestoredDraftAsDirty()`, 宿主仍知道需要保存.

本轮在旧正常包 `1743b4788d21cf917d42d8e1c9638c0b1851ee90425f82174205cf1363374ba0`
上直接观察宿主方法. `debug-ace36-tablet-v2` 证明进入和退出分屏后 `draftDirty=true`,
文本保留, 光标确实从 49 变为 0. 因此没有修改生产 dirty 语义, 只修复确认丢失的选区.
探针现在分别记录引擎 `changed` 与宿主 `draftDirty`, 后者作为未保存状态的判据.
旧 JSON 保持原始值和当时结论, 不回写或伪造当时没有采集的宿主观察.

Main 的 Back 会主动收起 SearchView, 这是搜索操作的退出行为.
新矩阵用 IME hide API 单独收起 Main 的键盘, 保留搜索内容用于后续分屏恢复验证.
旧包 `baseline-main36-tablet` 在这个调整后的步骤下仍丢搜索框及查询, 证明配置恢复缺陷独立存在.
其他入口继续在 IME 可见时使用 Back; 本轮 Main 不构成返回手势的专项验收.

尝试通过 IDEA Remote JVM 附加与两个非暂停日志点观察 EditorView, 但没有获得有效命中.
本节结论来自生产方法的运行回执和源码, 不把无命中当成路径未执行, 也不冒充调试器取值.

## 2. 生产修复

### 2.1 编辑器选区

EditorView 的 View state 原先只保存脚本执行 ID、编码等信息, 没有保存宿主引擎的选区.
增加两个 offset 元数据, 不将大文本重复放进 View Bundle.
恢复等待文件 / 草稿加载结束、引擎 ready 和大文件分帧加载完成; Ace 的状态通知及磁盘草稿
完成回调会再次尝试. 已保存的选区优先于旧 Intent 的初始行列, 避免重建时重新跳到最初打开位置.
若下一次重建发生在恢复完成前, 继续保存待恢复的 offset.

宿主内部 `ScriptEditorEngine.selectRange` 分别调用原生 CodeEditText 的选区设置与既有
`EditorPluginSession.selectRange`. 不重新装载全文或重置 undo 历史来移动光标,
也不改变公共插件契约、脚本 API 或文档站 / d.ts.

大草稿负例进一步发现: content-only 编辑窗口没有文件路径, StableDraftFileHelper
在 key 为空时拒绝生成磁盘草稿. 文本达到 256 KiB 后不能再走 Bundle 小草稿路径,
重建会退回最初打开的内容. EditActivity 现在为这类窗口使用已有、跨重建保持的编辑会话 ID
作为草稿键; 不同会话隔离, Save As 后继续通过既有迁移流程切换到文档路径键.

### 2.2 矮窗口中的编辑区

手机 1080x2424 / 420 dpi 旋转后, 窗口高 1080px, IME 占 662px.
旧布局在 y=347px 以下只余 71px, 补全栏继续占据空间, editor viewport 高度变为 0.

测量时先按正常高度布局. IME 可见且正文不足 48dp 时临时将补全栏高度收为 0;
若仍不足, 再收起文件 / 行列状态栏, 保留主要工具栏操作. 变化只影响当次布局,
空间恢复后重新按原高度测量, 不修改全屏偏好、只读或调试模式的显隐状态.
嵌套布局沿父链清除测量缓存, 避免相同 MeasureSpec 让高度修改未生效, 且不额外发起布局遍历.

实际 target 36 手机回执: 正文从 0px 恢复到 134px, 底部 y=418px 与 IME 顶部一致;
收起 IME 后正文为 486px, 状态栏 63px、补全栏 184px 均恢复.

### 2.3 主页搜索

MainActivity 在 Activity state 中保存查询、展开和焦点状态, 在重新创建菜单后恢复.
不增加 configChanges 来绕过重建; 仍使用实际 fragment / pager 与查询回调.
正常收起搜索框的行为保留.

### 2.4 日志输入

日志页增加处理 IME / navigation bar 的外层布局, 底部使用两者的最大 inset.
内层 CoordinatorLayout 同时调整控制台与 FAB 的可用高度; 移除这个页面原先的重复导航栏
补偿, 显式设置 adjustResize. 不改变通用 ConsoleView 或其他页面的 inset 策略.
截图复核还发现旧 FAB 布局会盖住控制台发送按钮; 将 FAB 锚定到日志列表的底部内侧,
随输入栏显隐与窗口尺寸变化避让操作区域, 并增加实际按钮矩形不得重叠的回执判定.
做法参照 [Views 键盘与 inset 官方说明](https://developer.android.com/develop/ui/views/layout/sw-keyboard)
和 [边到边布局说明](https://developer.android.com/develop/ui/views/layout/edge-to-edge).

## 3. 环境与证据边界

- 独占只读 `AVD_API_37`, serial `emulator-5594`, 私有 adb 5056;
  `-no-snapshot-load -no-snapshot-save -read-only -no-window -memory 4096`.
- API 37 镜像 `google/sdk_gphone16k_x86_64/emu64xa16k:17/CE2A.260420.019/15611780:userdebug/dev-keys`.
  实际内核页 4096, 不作为真实 16 KB 验证.
- 手机 sw411dp, 平板 sw800dp; 平板横屏进入 Settings 分屏后宽度从 1280dp 变为 673dp,
  smallestWidth 降到 598dp, 退出后恢复. 没有改变 compat override.
- Ace 为此克隆中既有 1.1.18 / versionCode 29 / target 36. 当前插件仓库
  `master@a2e558b` 为 1.3.0 / build 92, 只做源码契约核对, 工作区未修改;
  本轮不替代 P5.4 的当前 Ace 最终包验收.
- 完整原始目录为 `build/sdk37-p212-followup-20260914`, 包含失败尝试、截图、控制器日志、
  APK 和构建日志. 每轮记录独立 caseId、实际安装 APK SHA-256 与观察脚本 SHA-256.
  文本由脚本设入, 不代表真实 IME composing text、硬件输入或候选词输入通过.

## 4. 最终矩阵与构建

[机器记录](android-sdk-37-p212-followup-results-20260914.json) 保留全部尝试, 使用
`selected` 标明最终验收的 32 组; 它们全部通过. 不把早期成功标志直接复制为最终结论.

| 场景 | target 36 | target 37 | 实际判据 |
| --- | --- | --- | --- |
| Script / Main / Native Edit / Ace Edit / Log, 手机和模拟平板 | 10/10 | 10/10 | 实际窗口、方向、IME、生命周期、文本、光标与可用输入区域; 平板包含分屏进入 / 退出 |
| Native / Ace, 已保存文件和未保存草稿, 非空选区 | 4/4 | 4/4 | offsets 12..32 均保留; 已保存文件仍 clean, 草稿仍 dirty; 已保存文件的旧初始定位不会覆盖恢复选区 |
| Native / Ace, 无路径的 409600 字符草稿 | 2/2 | 2/2 | 全文 SHA-256 和长度一致, dirty 保留, offset 409600 保留, 进入 / 退出分屏均有 file-backed draft 保存回调 |

两种编辑器在两个 target 的手机横屏 IME 下正文均为 134px, 键盘隐藏后辅助栏恢复.
Log 额外验证 FAB 不覆盖发送按钮. 没有修改程序来强制恢复重建后的 IME;
Log 旋转后按平台行为隐藏键盘, 文字仍保留, 用户可再次唤起.

截图:

- [原生编辑器横屏 IME](android-sdk-37-p212-followup-20260914/native37-phone-ime.png)
  与 [Ace 横屏 IME](android-sdk-37-p212-followup-20260914/ace37-phone-ime.png).
- [已保存文件的原生选区](android-sdk-37-p212-followup-20260914/native37-clean-range-split.png)
  与 [Ace 大草稿分屏](android-sdk-37-p212-followup-20260914/ace37-large-draft-split.png).
- [Main 分屏搜索](android-sdk-37-p212-followup-20260914/main37-split.png),
  [Log 手机输入 / 发送按钮](android-sdk-37-p212-followup-20260914/log37-phone-ime.png),
  [Log 分屏](android-sdk-37-p212-followup-20260914/log37-split.png).

APK 来源需要区分:

| 宿主包 | SHA-256 | 使用范围 |
| --- | --- | --- |
| complete36 | `4b1348d25861965e5e9257bb87abc6eca3b3294a6560184f0b9b7a537dc61d66` | target 36 的 14 组非 Log 用例; 已含完整编辑器 / Main 修复 |
| verified36 | `04a30c5209e48254aae1e97866ab6c75a9256d40b171564d670eaca93fee01ab` | FAB 锚点修正后的 target 36 Log 两组; 相比 complete36 仅改变 Log XML 布局 |
| verified37 | `91006eb680c74d88ae58ff544a02386236eb085deb0ea8659f11077b91238f15` | target 37 全部 16 组, 与 verified36 源码一致 |
| restored-verified36 | `e9e5c30d1ad31ae24c2d0b4a3c948bc94b659bd84f32ca5d4152412c874eef7c` | 关闭 target override 后重新构建的正常包, 单独复核 |

各包位于原始目录的 `apks/<名称>/app.apk`, 对应 inrt 包与早期尝试的 APK 哈希、
二进制 manifest 摘要均在 JSON. 正常包 compile 37 / host target 36 / inrt target 29;
debug override 两个 flavor 为 target 37. ScriptExecuteActivity 的 configChanges 仍为 `0xda4`,
LogActivity 的 softInputMode 为 adjustResize (`0x10`).

验证范围:

- 正常及 target 37 的 `:app:assembleAppDebug :app:assembleInrtDebug` 通过,
  最后恢复正常构建并通过 app / inrt 的 release main manifest 任务; 原生页对齐门禁随 assemble 通过.
  `restored-verified36` 的 Log 手机输入与 Ace 平板 409600 字符草稿另行复验 2/2 通过,
  不重复计入 32 组主矩阵.
- 现有编辑器 JVM 测试 161/161 通过, SDK 37 Python 工具测试 54/54 通过,
  其中配置证据测试 19 项. 覆盖宿主 dirty 判据、clean 负例、非空选区、全文哈希、磁盘草稿回调、
  辅助栏恢复和按钮遮挡. Python 自测不等于设备功能通过.
- 未重复完整 release / R8 或远程 CI, 未执行 inrt 用户 APK 的界面回归,
  未在旧 Android 或实体设备上运行本轮新增代码.
- 保留无有效 debugger 命中的尝试、首版嵌套测量缓存失效、初始 portrait 旋转未稳定、
  无路径大草稿丢失等失败记录. 旋转锁在 Main 预热后重新应用; 不靠放宽初始方向要求通过.
  早期 Log 回执没有按钮矩形数据, 仅证明 IME 避让, 不用于最终发送按钮验收.
  首个 Gradle 命令误用不存在的 assembleStandardDebug, 在任何编译前失败,
  后改用项目的 assembleAppDebug / assembleInrtDebug.

收尾: 显示 size / density 无 override, user rotation 为 free, 软件 IME 设置恢复为 0,
overlay / all-files AppOps 恢复 default, 通知授权与初始值一致. debug app 为 null, forward 为空.
两个临时日志点与 Remote 配置已删除, 用户五个断点仍为 2 启用 / 3 禁用.
独占模拟器 PID 34436 与私有 adb 5056 / PID 18756 经创建时间核对后关闭;
共享 adb 5037 / PID 31400 的命令行和创建时间保持不变. 恢复与关闭回执写入机器记录,
只读 AVD 的临时数据层随关闭丢弃. 本轮末尾 memory limiter 仍为 disabled, 不算 P2.13 负载验收.

## 5. 集中实体机测试安排

维护者已确认集中执行实体设备测试并反馈. 本轮没有操作实体机, 99 行实体机清单仍未执行.
集中开测时将按最终 APK / 插件版本固定 UI01-UI04 的步骤, 重点包括 D35 平板与 D28 旧系统,
以及 API 37 真机、物理键盘 / 触摸板、三键与手势导航、中文组合输入、悬浮控制台和连续分屏调整.
正常 target 36 / inrt 29 配置继续保留, 不因本轮通过而提前执行 P3 正式切换.
