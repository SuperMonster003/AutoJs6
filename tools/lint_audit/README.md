# 宿主 lint 分类与修复

`report.py` 读取两份完整 Android lint XML, 输出规则统计、中文分类与全部诊断清单。
比较保留重复次数, 忽略行号移动, 不会创建 lint baseline 或修改检查级别。

本次修复结果、完整分类和验证范围见 [2026-09-25 审核记录](2026-09-25.md)。

## 运行检查

在仓库根目录执行:

```powershell
.\gradlew.bat --no-daemon --no-parallel --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx12g -XX:+UseG1GC -Dfile.encoding=UTF-8' '-Pautojs.sdk.lint.verify=true' :app:lintAppDebug
```

保留 `app/build/reports/lint-results-appDebug.xml` 和命令日志后再修改代码。
有其他任务同时构建宿主时, 在独立 worktree 中验证源码快照, 避免同时写入 Kotlin 编译产物。
编译错误、分析器崩溃或旧 XML 不能作为本次检查通过的证据。
该命令启用项目已有的严格检查开关, 使 Error/Fatal 导致 Gradle 失败。
依赖均已缓存但 Google Maven 的版本查询持续等待时, 可增加 `--offline` 完成检查。
离线模式不关闭代码检查规则, 但依赖升级建议基于本地缓存; 对比时不能将这一类数量下降计为代码修复。

```powershell
python tools/lint_audit/report.py build/lint-remediation/before-existing/lint-results-appDebug.xml app/build/reports/lint-results-appDebug.xml --output build/lint-remediation/comparison --fail-on-new-errors
python -m unittest discover -s tools/lint_audit -p 'test_*.py' -v
```

如果第二份报告来自独立检出目录, 增加 `--after-root <该目录>`。
第一份报告也来自其他检出目录时使用 `--before-root <该目录>`。
原始路径分别归一化后再比较, 避免将 worktree 路径差异误判为新增诊断。

产物:

- `report.md`: 各检查规则的修复前数量、当前数量、旧版路径压制数量和新增数量。
- `summary.json`: 机器可读统计。
- `issues.csv`: 全部当前、消失和按旧版路径压制的诊断, 含位置与消息, 使用 UTF-8 BOM 便于表格软件打开。

`--fail-on-new-errors` 仅检查新增 Error/Fatal, 不代表已有错误归零。
消失的诊断也可能来自明确说明理由的局部注解或其他任务的修改, 应结合源码差异判断。

## 冻结的 AutoJs4 兼容范围

`app/lint.xml` 使用 `issue id="all"` 下的 `ignore path` 排除:

- `app/src/main/java/com/stardust/**`
- `app/src/main/res/layout/console_view_legacy.xml`
- `app/src/main/res/values/{colors,dimens,ids,strings}_legacy.xml`
- `app/src/main/res/values-en/strings_legacy.xml`

这些类和资源用于旧版访问方式的兼容。保持名称、内容和结构不变, 不删除旧译文、不改写重定向类。
外部配置不会修改打包资源或运行时行为。不要将这里的路径例外扩大为整个应用的全局忽略。
现代代码中引用旧版 API 的问题仍需检查。

Android lint 配置与路径规则参见 [官方 lint 配置说明](https://googlesamples.github.io/android-custom-lint-rules/usage/lintxml.md.html)。

## 后续警告的处理顺序

1. **权限、资源释放和生命周期**: 优先检查 `MissingPermission`, `Recycle`, `ImplicitSamInstance`, `CheckResult`, `StaticFieldLeak` 的实际调用链。
2. **平台兼容**: 对照最低 SDK 24 确认 API 调用是否有完整守卫或兼容实现。以外部参数选择平台分支的代码需单独说明并验证调用方。
3. **文案与无障碍**: 核对占位符和语言覆盖, 再处理复数、内容描述、键盘焦点和 RTL。
4. **资源整理**: `UnusedResources` 和图标告警不能直接转换成删除或重命名操作。脚本资源解析器支持通过名称访问宿主资源, 须先核对动态访问和兼容契约。
5. **依赖与建议用法**: 依赖升级、布局重构、批量采用 KTX 等应在独立变更中验证。

资源动态访问的入口包括 `runtime/api/augment/autojs/Autojs.kt` 的资源代理,
以及 `core/ui/inflater/AndroidResourceResolver.kt` 的名称解析。静态引用为空不足以证明资源可以删除。
`CheckResult` 需区分界面订阅和独立的一次性后台操作; 例如 `MailTriggerReceiver` 使用
`goAsync()` 并在订阅的完成/失败路径调用 `finish()`, 不能为了消除告警就在 `onReceive` 返回时取消操作。
`StaticFieldLeak` 则需分别核对 Application Context、Activity 引用及对话框的清理路径。

当前代码保留 `NodeBridgeFileScope` 的两个局部 `NewApi` 注解: Android 调用方
`NodeBridgeFilesBridge` 根据 `SDK_INT < 26` 注入旧文件 API 分支, 桌面测试使用 NIO 分支。
这不扩展 `app/lint.xml` 的旧版路径豁免范围。

编辑器另存为先尝试硬链接发布完整文件。Android 7 上若文件系统不支持硬链接,
会明确报告无法安全发布; 不会回退到先删除目标或在最终文件名下写入半成品。
Android 8+ 保留原有 NIO 移动分支。
