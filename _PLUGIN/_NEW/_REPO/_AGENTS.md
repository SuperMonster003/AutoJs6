# AutoJs6 新插件仓库 AGENTS.md 参考规范

本文件用于新建 `AutoJs6-Plugin-*` 仓库时直接复制为仓库根目录的 `AGENTS.md`. 它总结了现有插件中已经稳定采用的工程约定, 并将规则区分为强制项和按需项. 新项目初始化完成后, 应删除所有不适用的说明和占位符, 让仓库内的 `AGENTS.md` 只保留真实有效的项目约束.

## 1. 规则等级与适用范围

- `MUST`: 所有新插件都必须遵循.
- `SHOULD`: 默认遵循. 只有项目本身存在明确理由时才可偏离, 并应在仓库文档中说明原因.
- `CONDITIONAL`: 仅在插件具备对应能力时应用, 例如原生库, 独立界面, 更新检查或文件操作.
- 用户在当前任务中的明确要求优先于本文件.
- 不要机械复制某个现有插件的全部实现. `AutoJs6-Plugin-OpenCC` 适合作为构建, 文档, 资源和激活协议的基础参照, 但原生 ABI, 模型, 独立界面和上游源码管理均属于按需能力.

## 2. 建仓前必须确定的标识

编码前先确定下列值, 并在 Gradle, Manifest, Binder API, 资源, 文档, 测试和主项目注册信息中保持完全一致.

| 占位符 | 示例 | 约束 |
|---|---|---|
| `{PROJECT_ROOT}` | `D:/idea-projects/AutoJs6-Plugin-Example-Previewer` | 新插件仓库根目录的绝对路径 |
| `{PROJECT_NAME}` | `AutoJs6-Plugin-Example-Previewer` | Git 仓库和目录名, 使用 `AutoJs6-Plugin-` 前缀 |
| `{ROOT_PROJECT_NAME}` | `autojs6-plugin-example-previewer` | `rootProject.name`, 使用小写 kebab-case |
| `{APP_NAME}` | `Example Previewer` | 英文标题, 不可翻译 |
| `{APPLICATION_ID}` | `io.github.supermonster003.autojs6.plugin.example.previewer` | 小写 ASCII 包名, 不包含连字符 |
| `{PLUGIN_ID}` | `example-previewer` | 稳定的逻辑 ID, 使用小写 ASCII |
| `{PLUGIN_ENGINE}` | `example` | 宿主脚本引擎或能力标识, 无引擎时按契约定义 |
| `{PLUGIN_VARIANT}` | `default` | 插件变体, 无特殊变体时使用 `default` |
| `{PLUGIN_SERVICE}` | `ExamplePreviewerPluginService` | 对外 Binder Service 的类名 |
| `{CAPABILITY_API}` | `example-previewer-api` | 专用 API AAR 或模块名 |
| `{SERVICE_ACTION}` | `org.autojs.plugin.EXAMPLE_PREVIEWER` | Binder 服务发现 action, 必须与宿主契约一致 |
| `{SERVICE_CATEGORY}` | `example-previewer` | Binder 服务发现 category, 必须与宿主契约一致 |
| `{REQUIRES_HOST_VERSION}` | `3923` | 使用该契约所需的最低宿主 versionCode |
| `{LANGUAGE_TAG}` | `zh-Hans` | 运行时选择本地化文档时使用的 BCP 47 language tag |
| `{VERSION_NAME}` | `1.0.0` | 语义化版本名称 |
| `{PLATFORM_VERSIONS_PLUGIN_VERSION}` | `1.7.0` | 建仓时从 `AutoJs6-Gradle-Platform-Versions/version.properties` 的 `VERSION_NAME` 读取并确认已发布到公共仓库, 不沿用模板中的历史值 |
| `{ABI}` | `arm64-v8a` | 发布 APK 对应的 ABI, 通用包使用 `universal` |
| `{CRC32}` | `A1B2C3D4` | `appendDigestToReleasedFiles` 写入文件名的 CRC32 摘要 |

还必须确认以下问题:

- 主项目当前是否已有同类能力. 插件应拥有可选能力的真实实现, 主项目只保留入口, 能力发现, 安装或启用引导以及合理退化. 插件未安装或被禁用时, 主项目不得继续通过重复代码提供同一完整功能.
- 现有公共插件 API 是否已经覆盖需求. 若没有, 先设计宿主与插件共同使用的契约, 常量, AIDL 和能力协商方式, 不要在两侧各自维护近似但不同的定义.
- 插件是否真的专属于 AutoJs6. 若协议可被其他 Auto.js 衍生版本实现, 用户可见描述不应写成 `适用于 AutoJs6`, `用于 AutoJs6` 或 `为 AutoJs6 提供`.
- 名称是否准确表达语义. 涉及预览器时使用 `Previewer`, 不使用 `Preview`. 涉及查看器时使用 `Viewer`, 不使用 `View`. 同步覆盖项目名, 应用标题, 包名, 类名, 服务 action, category, 插件 ID, 文档, URL 和测试夹具.
- 新项目不为尚未发布的旧名称或旧包名保留兼容层. 已发布项目改名时, 除非用户明确要求兼容, 也以新名称整体一致和可正常工作为优先.

## 3. 工作区与提交

### 3.1 会话开始

- MUST 运行 `git status --short`, 检查当前分支, 最近提交和相关文件差异.
- MUST 将已有未提交内容视为用户工作. 不覆盖, 不回滚, 不擅自整理与当前任务无关的改动.
- 禁止使用 `git reset --hard`, `git checkout -- <path>` 或其他可能丢失用户内容的命令, 除非用户明确授权.
- 修改前先查找仓库内更深层的 `AGENTS.md`. 离目标文件最近的规则优先.

### 3.2 开发过程

- 每个行为改动应同时考虑实现, 测试, 多语言资源, README, changelog, 主项目入口和公共契约.
- 不提交本地缓存, IDE 状态, 调试输出或无意生成的二进制文件.
- Gradle 自动修改 `BUILD_TIME` 时, 在确认来源后与相关变更一并处理, 不要只为缩小差异而回滚. 若 Gradle 修改 `VERSION_BUILD`, 必须按 Git 提交计数规则校正为下一笔提交的预计计数; `VERSION_NAME` 只按语义化版本规则调整, 不接受构建脚本产生的无依据版本漂移.
- 修改第三方源码, 模型或二进制时, 同步记录版本, 来源, 固定提交或校验值以及许可证.

### 3.3 提交

- 除非用户明确要求本次会话不要提交, 会话结束前 MUST 将本次范围内的全部文件按逻辑提交.
- 使用 Conventional Commits 风格, 例如 `feat: add ...`, `fix(activation): ...`, `docs: ...`, `build: ...`, `test: ...`, `ci: ...`, `chore: ...`.
- 一个提交应表达一个完整意图. 行为实现, 对应测试和对应 changelog 通常放在同一提交. 独立的构建迁移, 文档重写或 CI 改造可拆分提交.
- 每个提交尽量保持可构建, 可测试. 不要把同一功能任意按文件类型拆成无法单独理解的提交.
- 提交前 MUST 审阅 `git diff --check`, `git diff --cached`, `git status --short`, 并确认没有密钥, 本地路径, 临时 APK 或无关改动.
- 会话结束时再次检查工作区. 除用户明确要求保留未提交内容外, 当前任务完成后 MUST 再做一次单次整体提交或按逻辑拆分提交, 最终 `git status --short` 无输出.
- 明确说明验证范围和未运行的检查. 若发现无法纳入本次提交的用户改动, 停止自动提交并向用户说明, 不得为了追求干净工作区而覆盖它们.

### 3.4 新仓库初始化与提交计数

- 项目骨架落盘后 MUST 在项目根目录执行 `git init`.
- 初始化后不得把全部历史长期留在未提交状态. 按身份与构建骨架, 插件契约, 运行时能力, 资源与文档, 测试与 CI 等真实逻辑拆分初始提交.
- `VERSION_BUILD` MUST 与当前分支 `HEAD` 可达的 Git 提交数一致. 最终提交数为 12 时, `VERSION_BUILD` 必须为 12.
- 新仓库尚无提交时, 第一笔提交中的 `VERSION_BUILD` 为 1. 每次准备新提交时, 先用当前提交数加 1 得到即将产生的 build number, 写入 `version.properties`, 再把该文件与本次逻辑改动一并提交.
- 不要先把 `VERSION_BUILD` 写成当前提交数再创建新提交, 否则提交完成后会固定落后 1.
- 多个逻辑提交依次重复上述步骤. 不要先完成多笔提交, 最后随意填一个无法从 Git 验证的数字.

PowerShell 下可用以下方式计算下一笔提交的值:

```powershell
$currentCommitCount = 0
git rev-parse --verify HEAD 2>$null | Out-Null
if ($LASTEXITCODE -eq 0) {
    $currentCommitCount = [int](git rev-list --count HEAD)
}
$nextVersionBuild = $currentCommitCount + 1
```

最后一笔提交完成后 MUST 验证:

```powershell
$commitCount = [int](git rev-list --count HEAD)
$versionBuildLine = Select-String -LiteralPath version.properties -Pattern '^VERSION_BUILD=(\d+)$'
$versionBuild = [int]$versionBuildLine.Matches[0].Groups[1].Value
if ($versionBuild -ne $commitCount) {
    throw "VERSION_BUILD=$versionBuild, commitCount=$commitCount"
}
if (git status --short) {
    throw "Worktree is not clean"
}
```

若最后才发现计数不一致, 将 `VERSION_BUILD` 设置为 `当前提交数 + 1`, 创建一笔有明确含义的最终校正提交, 再重新验证. 不要设置为当前数后再提交.

### 3.5 版本名称

- 新插件的第一个正式版本从 `VERSION_NAME=1.0.0` 开始.
- `VERSION_NAME` 与提交数量不绑定, 按语义化版本管理. 兼容性修复增加 patch, 向后兼容的新能力增加 minor, 破坏性契约或用户行为变化增加 major.
- 仅有文档, 测试或 CI 调整时是否增加版本, 结合是否准备发布和仓库既有策略决定, 但不得为了匹配提交数机械增加版本名称.
- 修改 `VERSION_NAME` 时同步更新全部 changelog JSON 的版本 key, README, 发布文件名断言, 测试夹具及其他版本元数据.

### 3.6 Git 提交身份与历史邮箱隐私

- MUST 在初始化仓库后, 创建任何提交或附注标签之前, 显式设置仓库级 Git 身份: `git config --local user.name "SuperMonster003"` 和 `git config --local user.email "30370009+SuperMonster003@users.noreply.github.com"`. 不依赖全局邮箱配置, 不使用私人邮箱.
- MUST 在首次公开仓库和每次推送前审计待发布引用可达的完整历史, 覆盖 commit author, commit committer, annotated tag tagger, 以及提交/标签说明中的身份尾注 (例如 `Co-authored-by`). 仅修改 `user.email` 不会更新已有提交对象; `.mailmap` 的显示映射也不能替代历史清理.
- 如历史中含维护者私人邮箱, MUST 在本地保存可恢复且不公开上传的备份后改写受影响的提交和标签, 将维护者邮箱统一替换为上述 GitHub noreply 地址. 保留作者姓名, 源码树, 提交语义和有效的第三方署名; 核对 `VERSION_BUILD` 与最终可达提交计数.
- 需要替换已公开历史时, 同步更新所有受影响的远端分支和标签, 使用带明确预期旧对象 ID 的 `--force-with-lease` 防止覆盖审计后出现的新提交. 先合并有效工作再删除冗余分支; 不推送备份引用, `refs/original`, `refs/replace` 或包含旧邮箱的其他恢复引用.
- 历史改写后 MUST 重新获取远端引用并复核 author/committer/tagger 邮箱及本地 `user.email`, 验证本地与远端一致. 更新绑定旧提交 ID 的发布来源记录和索引准入清单, 但不得把旧构建或历史测试结果冒充为对新产物的验证.
- 已被克隆, fork 或缓存的旧对象不会因 force push 自动消失. 若要求从 GitHub 缓存或拉取请求引用中彻底移除已暴露信息, 应按 GitHub 的敏感数据清理流程另行处理; 不宣称改写可撤回已发生的公开披露.

## 4. 推荐的最小仓库结构

```text
{PROJECT_NAME}/
|-- .changelog/
|   |-- lang_ar.json
|   |-- lang_en.json
|   |-- lang_es.json
|   |-- lang_fr.json
|   |-- lang_ja.json
|   |-- lang_ko.json
|   |-- lang_ru.json
|   |-- lang_zh-Hans.json
|   |-- lang_zh-Hant-HK.json
|   |-- lang_zh-Hant-TW.json
|   `-- template_changelog.md
|-- .github/workflows/
|   |-- build.yml
|   `-- markdown.yml
|-- .python/
|   |-- check_markdown.bat
|   |-- generate_markdown.bat
|   `-- generate_markdown.py
|-- .readme/
|   |-- common.json
|   |-- lang_*.json
|   |-- README-*.md
|   |-- template_plugin_instruction.md
|   `-- template_readme.md
|-- app/
|   |-- sm003.jks              # Local only, ignored by Git
|   `-- src/
|       |-- androidTest/
|       |-- main/
|       |   |-- assets/doc/
|       |   |-- java/ or kotlin/
|       |   |-- res/
|       |   |   `-- mipmap/ic_launcher.png
|       |   `-- AndroidManifest.xml
|       `-- test/
|-- build-logic/
|-- gradle/wrapper/
|-- libs/
|   |-- common-plugin-api.aar
|   `-- {CAPABILITY_API}.aar
|-- .gitignore
|-- AGENTS.md
|-- build.gradle.kts
|-- gradle.properties
|-- gradlew
|-- gradlew.bat
|-- LICENSE
|-- README.md
|-- sign.properties           # Local only, ignored by Git
|-- settings.gradle.kts
`-- version.properties
```

按需增加 `docs/`, `scripts/`, `ROADMAP.md`, `THIRD_PARTY_NOTICES.md`, 原生模块, 模型模块或可复用的 `plugin-api/` 源码模块. 不要仅为目录整齐创建空模块.

## 5. Gradle 与版本平台

### 5.1 在线平台版本插件

- MUST 使用在线 Maven 仓库中的 `io.github.supermonster003.autojs6-platform-versions` 1.7.0 或经验证的后续稳定版. 建仓时先读取 `D:/idea-projects/AutoJs6-Gradle-Platform-Versions/version.properties` 的 `VERSION_NAME`, 再确认该不可变版本已能从公共仓库解析, 并据此替换 `{PLATFORM_VERSIONS_PLUGIN_VERSION}`. 升级时所有插件仓库统一更新, 不允许新仓库复制已经过期的版本号.
- 禁止使用 `mavenLocal()` 或本地 Maven 仓库解析该插件.
- 禁止继续使用旧的本地 platform versions 实现或手工复制同一组版本解析逻辑.
- 平台插件 MUST 在根 `settings.gradle.kts` 中应用, 且整个 `plugins` 块 MUST 位于 `includeBuild("build-logic")` 之前. 只有这样, included build 才能读取插件提前发布的版本系统属性.
- 禁止只在 `build-logic/settings.gradle.kts` 中应用平台插件. 这种半迁移会让 included build 与真正构建 Android 模块的根工程各自做一次版本决策, 并可能出现控制台显示新 AGP、实际 app 却仍使用旧 AGP 的假象.
- `settings.gradle.kts` 的基础形态如下. 可增加真实需要的模块, 但不要删除、下移或重复应用平台插件.

```kotlin
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "{ROOT_PROJECT_NAME}"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    plugins {
        id("io.github.supermonster003.autojs6-platform-versions") version "{PLATFORM_VERSIONS_PLUGIN_VERSION}"
        id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    }
}

plugins {
    id("io.github.supermonster003.autojs6-platform-versions")
    id("org.gradle.toolchains.foojay-resolver-convention")
}

includeBuild("build-logic")
include(":app")
```

- 根 `build.gradle.kts` MUST 从平台插件提供的系统属性声明所有模块实际使用的 Android/Kotlin/KSP 插件版本, 并使用 `apply false` 集中提供给模块. 至少含 Android application 的项目使用以下形态:

```kotlin
plugins {
    id("com.android.application") version System.getProperty("gradle.agp.version") apply false
    // 仅当仓库中的模块实际应用下列插件时再声明:
    // id("com.android.library") version System.getProperty("gradle.agp.version") apply false
    // id("org.jetbrains.kotlin.android") version System.getProperty("gradle.kotlin.version") apply false
    // id("org.jetbrains.kotlin.kapt") version System.getProperty("gradle.kotlin.version") apply false
    // id("org.jetbrains.kotlin.plugin.parcelize") version System.getProperty("gradle.kotlin.version") apply false
    // id("com.google.devtools.ksp") version System.getProperty("gradle.ksp.version") apply false
}
```

- 模块自己的 `plugins` 块只负责应用插件, 不重复硬编码版本. `build-logic/settings.gradle.kts` 不应用 platform-versions; 它可通过 `System.getProperty(...)` 消费根 settings 已发布的 Java、JVM target 与 Foojay 等版本属性.
- 接入平台插件后, 根 build 禁止继续硬编码 AGP/Kotlin/KSP 版本, 根 settings 也禁止用手工 `System.setProperty(...)` 模拟插件输出. 版本逃生门只能使用 `version.properties` 中的 `OVERRIDDEN_*`, 并应在常规构建中保持 `NONE`.
- 官方宿主与插件仓库 MUST 完全依赖已发布平台插件内置的兼容数据, 不得提交任何 `gradle/data` 消费端覆盖. 遇到兼容数据缺失或错误时, MUST 先在 `AutoJs6-Gradle-Platform-Versions` 中修正并验证数据, 发布新的不可变稳定版本, 再统一升级消费仓的 `{PLATFORM_VERSIONS_PLUGIN_VERSION}`; 不得以消费仓单文件覆盖作为紧急修复通道.
- 官方消费仓不得仅为 AGP 9 built-in Kotlin 或当前中央候选下界声明 `MIN_SUPPORTED_ANDROID_GRADLE_PLUGIN_VERSION`. compileSdk、KSP 等项目事实产生的下界应由中央解析器自动推导; 中央尚不能表达的真实兼容约束应先扩展中央数据模型和测试, 而不是在单个消费仓重复维护 AGP 下界. 中央 IDE→AGP 映射的最早版本是不可由消费仓放宽的全局 IDE 支持下界; `MIN_SUPPORTED_ANDROID_STUDIO_IDE_VERSION` 与 `MIN_SUPPORTED_INTELLIJ_IDEA_IDE_VERSION` 默认省略, 只有项目确需比中央下界更高的 IDE 时才可声明并说明理由. 没有 `version.properties` 的仓库应建立包含 compileSdk、targetSdk、最低 Java/Gradle 与 `OVERRIDDEN_*=NONE` 的最小版本输入.
- 从旧内联机制迁移时, 根插件声明与根 settings 迁移 MUST 在同一个可构建变更中完成. 使用 `AutoJs6-Gradle-Platform-Versions/.python/migrate_modules.py` 先检查/补齐根插件声明, 再使用 `migrate_downstream.py` 替换 settings; 两步之间的工作树不可作为最终结果交付.
- `*.pre-platform-versions.bak` 只用于迁移过程回滚. 新机制通过构建验证且差异审阅完成后 MUST 删除这些临时副本; `.gitignore` 规则只是防止误提交的最后一道保护, 不能替代收尾清理.
- 从本地源码快照迁移到公共插件时, 同步移除旧快照目录及其 `includeBuild`, CI 预热/离线测试步骤, 以及仓库受 Git 跟踪的 `.idea/gradle.xml` 复合构建引用. 被忽略的本地 `.idea` 属于开发者工作区, 不纳入迁移补丁; 提醒开发者重新导入 Gradle 即可. 历史 release notes 可以保留当时的命令, 现行受跟踪的工作流和 IDE 配置不得继续指向已删除目录.
- 迁移或新建后 MUST 确认根 settings 不再包含 `agpVersionMap`, `notations.classpath` 或为 AGP/Kotlin 注入 classpath 的旧 `buildscript` 逻辑, 并确认构建日志只打印一套平台版本决策.
- `build-logic` SHOULD 提供 `org.autojs.build.utils`, `org.autojs.build.versions`, `org.autojs.build.signs` 和 `org.autojs.build.jvm-convention` 等约定插件. 新项目可以从 OpenCC 的当前实现复制后再精简, 不要保留无用的项目专属常量.
- `app` 模块 MUST 从 `version.properties` 和版本约定插件读取 compileSdk, minSdk, targetSdk, versionCode 和 versionName. 不要在多个模块重复硬编码.
- `rootProject.name` 的字符串值 MUST 全部为小写. 推荐使用 `autojs6-plugin-...` 小写 kebab-case, 禁止出现 `AutoJs6-Plugin-...` 或其他大写字母.
- Java 和 Kotlin 编译编码 MUST 为 UTF-8. JDK 工具链版本由平台约定统一管理.
- 依赖优先使用 Maven Central, Google Maven 或明确可信的上游仓库. 能用明确坐标时不使用模糊的 `fileTree`.

平台版本机制的最低验收命令如下. 它显式模拟 GitHub Actions 常用的 Temurin 环境, 必须同时完成配置、Debug APK 构建与 JVM 单元测试:

```powershell
.\gradlew.bat --no-daemon '-Djava.vendor=Eclipse Adoptium' '-Djava.vendor.version=Temurin-21.0.12.1+1' :app:assembleDebug :app:testDebugUnitTest
```

验收日志 MUST 只有一段 `Version information for IDE platform and Gradle plugins`, 自动选择的 AGP MUST 不低于日志给出的 `Minimum`, 且不得出现 `checkAarMetadata` 报告依赖要求更高 AGP 的错误. 若仓库没有 `:app` 或没有对应测试任务, 使用其真实 Android application 模块和最接近的 JVM 测试任务替换, 并在仓库 `AGENTS.md` 中写明.

### 5.2 仓库边界与冗余构建代码

- Gradle 构建 MUST 自包含. 禁止引用插件仓库外部的 JAR 或 AAR, 例如 `implementation(files("../AutoJs6/lib/xxx.aar"))`, `implementation(files("$rootDir/../AutoJs6/..."))` 或指向兄弟项目的 `flatDir`.
- 宿主提供的公共或专用 API AAR 必须复制到插件仓库自己的 `libs/`, 改为受控源码模块, 或发布到可复现的 Maven 仓库. 宿主与插件需要同步更新时分别修改各仓库, 不通过跨仓库相对路径制造隐式耦合.
- 本地 AAR 或 JAR SHOULD 有来源, 版本和 SHA-256 锁定信息. 更新二进制时同步更新锁文件和契约测试.
- 除非插件明确集成 Visual Studio Code 或其扩展协议, 从 `version.properties`, `build-logic`, Gradle 脚本和相关文档中移除不必要的 VSCode 版本要求代码, 包括 `vscodeExtRequiredVersion` 等字段, accessor, 文档注释, 校验和版本映射. 名称中包含 `vscode-*` 的真实运行时依赖若确实被功能使用, 不属于可机械删除的版本要求代码.
- 移除不必要的 `id("org.jetbrains.kotlin.android")` 声明. 先检查平台版本插件, Android Gradle Plugin 和 `build-logic` 是否已经提供 Kotlin Android 支持, 避免在 `settings.gradle.kts`, 根脚本, app 模块或 convention plugin 中重复声明.
- 只有经过构建验证且确实无法由现有约定提供 Kotlin 支持时才保留 `org.jetbrains.kotlin.android`, 并在附近注释其必要原因.
- 清理构建脚本时不得删除签名解析, `signingConfigs`, release signing 选择, `org.autojs.build.signs` 或发布产物校验等签名相关代码.

### 5.3 插件 API 依赖

- 普通 Binder 插件 MUST 引入 `libs/common-plugin-api.aar` 或同一公共 API 的受控源码模块.
- 若能力已有专用 API, 同时引入对应 `{CAPABILITY_API}.aar` 或源码模块. 宿主和插件必须使用同一版本的常量与 AIDL.
- 使用 AIDL 时启用 `buildFeatures.aidl = true`. 使用 Gradle `resValue` 时启用 `buildFeatures.resValues = true`.
- 不要在插件内复制一份公共 `PluginInfo` 或相同包名的 AIDL 伪实现.

### 5.4 签名与发布构建

- 创建新插件时 MUST 从宿主项目复制以下文件到插件项目的相同相对路径:

| 宿主源文件 | 插件目标文件 | Git 处理 |
|---|---|---|
| `D:/idea-projects/AutoJs6/.gitignore` | `{PROJECT_ROOT}/.gitignore` | 按插件实际目录适当修改或补充后提交 |
| `D:/idea-projects/AutoJs6/sign.properties` | `{PROJECT_ROOT}/sign.properties` | 本地签名配置, 必须忽略, 不得提交 |
| `D:/idea-projects/AutoJs6/app/sm003.jks` | `{PROJECT_ROOT}/app/sm003.jks` | 本地签名密钥, 必须忽略, 不得提交 |

- `{PROJECT_ROOT}` 表示新插件项目根目录. 复制后检查 `storeFile` 能从新项目正确解析, 不在文档或日志输出密码内容.
- `.gitignore` MUST 至少忽略 `/sign.properties`, `/local.properties`, `*.jks`, `*.keystore` 和 `*.pre-platform-versions.bak`, 因而包括 `/app/sm003.jks` 以及平台版本迁移脚本的临时回滚副本. 在第一次提交前使用 `git check-ignore` 验证签名文件与迁移备份确实不会进入索引.
- 保留仓库原有的签名约定插件和 Gradle 签名脚本. Debug 是否复用 release 签名遵循仓库既有策略, Release 收集任务必须使用有效签名.
- 仓库可提供不含真实秘密的签名配置示例, 但不得提交密码, token, 私钥或开发者绝对路径.
- Release 收集任务 MUST 在签名缺失或不完整时失败, 不得把未签名 APK 当作正式发布产物.
- app 模块 MUST 保留名为 `appendDigestToReleasedFiles` 的 Gradle 任务. 可以根据单 APK, ABI splits 或其他真实变体调整实现, 但不得删除或改成无法由既有发布流程发现的名称.
- `appendDigestToReleasedFiles` MUST 依赖 `assembleRelease`, 验证签名配置, 校验实际 APK 集合与预期集合完全一致, 只收集已签名 release APK, 并给文件名追加 CRC32.
- 文件名必须符合项目语义. 单 APK 推荐 `{ROOT_PROJECT_NAME}-v{VERSION_NAME}-{CRC32}.apk`; ABI 拆分推荐 `{ROOT_PROJECT_NAME}-v{VERSION_NAME}-{ABI}-{CRC32}.apk`, 其中 Universal APK 使用 `universal`. 有真实产品变体时在不产生歧义的位置加入变体名称.
- 任务的源文件匹配和 rename 逻辑必须同时覆盖当前实际产物. 不要将只适用于 `app-release.apk` 的正则直接复制到 ABI 项目, 也不要将 ABI 正则用于单 APK 项目.
- 构建产物默认不入库. 只有仓库既有发布流程明确追踪 `releases/` 时才提交这些文件.

### 5.5 ABI 拆分与原生代码

- 新插件 SHOULD 优先评估并尽量启用 ABI splits. 只要包含 ABI 专属原生库, 可执行文件, 模型, 或拆分后能实质降低安装包体积, 就 MUST 生成各单 ABI APK 并启用 Universal APK.
- 只有在拆分确实没有必要时才省略, 例如插件完全由 ABI 无关的 Java/Kotlin bytecode 与普通资源构成, 拆分包内容实质相同且不会带来下载或兼容性收益. 省略原因应能由依赖与 APK 内容验证, 不要仅以实现方便为理由.
- 启用时使用以下基础形态, `include(...)` 必须与实际随包内容支持的 ABI 完全一致:

```kotlin
splits {
    abi {
        isEnable = true
        reset()
        include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        isUniversalApk = true
    }
}
```

- `PluginInfo.supportedAbis` 必须与 APK 内实际可加载的原生库一致, 不使用虚构 ABI.
- 启用拆分的插件 MUST 逐个验证单 ABI APK 和 Universal APK 的原生库清单, 安装, 加载与 Binder 往返.
- 面向当前 Android 设备的原生插件 MUST 检查 16 KB page size 兼容性, 包括 ELF 对齐, ZIP 对齐和至少一个真实 16 KB 运行环境测试.
- README 仅在用户需要选择安装包时解释 ABI. 不写 `supportedAbis = emptyArray()` 等内部实现细节.

## 6. Manifest 与特殊设备激活协议

ColorOS 等设备可能让新安装应用保持停止状态. 所有新插件 MUST 提供宿主可发现的无界面 Wake Activity, 使插件中心能够显示 `激活` 按钮并由宿主显式启动它. 仅声明 Binder Service 不足以覆盖该场景.

### 6.1 最小 Manifest 骨架

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="org.autojs.permission.PLUGIN" />

    <application
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true">

        <meta-data
            android:name="org.autojs.plugin.WAKE_ACTIVITY"
            android:value=".WakeActivity" />

        <meta-data
            android:name="org.autojs.plugin.info.AUTHOR"
            android:value="@string/plugin_author" />

        <activity
            android:name=".WakeActivity"
            android:excludeFromRecents="true"
            android:exported="true"
            android:finishOnTaskLaunch="true"
            android:permission="org.autojs.permission.PLUGIN"
            android:theme="@android:style/Theme.NoDisplay">
            <intent-filter>
                <category android:name="android.intent.category.DEFAULT" />
                <action android:name="org.autojs.plugin.action.WAKE" />
            </intent-filter>
        </activity>

        <service
            android:name=".{PLUGIN_SERVICE}"
            android:exported="true"
            android:permission="org.autojs.permission.PLUGIN">
            <intent-filter>
                <action android:name="{SERVICE_ACTION}" />
                <category android:name="{SERVICE_CATEGORY}" />
            </intent-filter>
        </service>

    </application>

</manifest>
```

对应 Activity 保持最小实现:

```kotlin
class WakeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
```

### 6.2 激活要求

- `org.autojs.plugin.WAKE_ACTIVITY` 的值必须能解析到真实且启用的 Activity.
- Wake Activity MUST 为 `exported=true`, 使用 `Theme.NoDisplay`, 受 `org.autojs.permission.PLUGIN` 保护, 响应 `org.autojs.plugin.action.WAKE` 与默认 category, 启动后立即结束.
- 不要给 Wake Activity 添加主界面副作用, 网络请求, 模型加载或耗时初始化.
- 所有对外 Activity 和 Service 均逐项审查 `android:exported`. 插件契约入口 MUST 使用 `org.autojs.permission.PLUGIN` 或契约规定的等价保护.
- Binder 服务的 action, category, service class 和包名必须与宿主发现逻辑完全一致. 大小写差异也视为错误.
- 若主能力接口本身不提供 `PluginInfo`, 或宿主设置页要求统一的信息入口, `CONDITIONAL` 增加受权限保护的 `org.autojs.plugin.INFO` Service, 实现 `IPluginInfoProvider`.
- 独立 UI 插件可按需增加 launcher 或 `ACTION_VIEW` 入口. 这些入口不能替代 Wake Activity.

### 6.3 激活验收

- MUST 有自动化 Manifest 契约测试, 验证 Wake Activity 存在, exported, permission, action, category 和 meta-data 全部正确.
- MUST 有服务发现测试, 使用真实 action 与 category 查询, 断言只命中期望 Service, 再通过显式 Component 绑定并校验 Binder descriptor.
- 在可用的 ColorOS 或同类设备上 SHOULD 执行真实验收: 安装后不手动打开插件, 进入插件中心, 确认出现 `激活`, 点击后可启用, 杀死或重启进程后仍可再次发现并调用.
- 若当前没有该类设备, 必须如实记录 `未执行真实设备激活验证`. 不得仅凭 Manifest 审查声称 ColorOS 已验证通过.

## 7. PluginInfo 与能力协商

插件返回的 `PluginInfo` 是插件中心展示, 兼容性判断和宿主调用的重要契约. MUST 填充并测试以下字段:

- `name`: 与不可翻译的英文 `app_name` 一致.
- `description`: 从当前 locale 的 `R.string.plugin_description` 读取.
- `instruction`: 有插件中心说明时使用 `@raw/plugin_instruction` 等资源引用. 没有时明确为 `null`, 不填无效 URL.
- `author` 和 `collaborators`: 与项目元数据一致.
- `versionName` 和 `versionCode`: 从当前已安装包的 `PackageInfo` 读取, 不复制另一套常量.
- `versionDate`: 从统一构建资源读取, 推荐格式 `MMM d, yyyy` 和 `GMT+08:00`.
- `id`, `engine` 和 `variant`: 从不可翻译构建资源或公共契约常量读取, 并与宿主完全一致.
- `supportedAbis`: 与实际安装包能力一致.
- `capabilities`: 至少包含 `PluginCapabilityKeys.REQUIRES_HOST_VERSION`. 能力有版本时同时上报 contract version 和可选功能集合.

如果插件对 ABI 没有限制, 即能力适用于任意 ABI, MUST 在 `getInfo()` 重写方法中显式设置 `supportedAbis = emptyArray()`. 即使公共 helper 当前默认返回空数组, 也保留这条可审计的显式声明:

```kotlin
override fun getInfo(): PluginInfo {
    return pluginInfo(
        name = getString(R.string.app_name),
        description = getString(R.string.plugin_description),
    ).apply {
        supportedAbis = emptyArray()
    }
}
```

有 ABI 限制时则显式返回真实支持列表, 并确保它与 `splits.abi.include(...)`, APK 内原生库和发布说明一致. 自动化测试必须区分 `emptyArray()` 表示不限 ABI, 非空数组表示受列出的 ABI 限制.

推荐将 Android 上下文读取与纯数据组装分开, 使元数据映射可由本地单元测试完整覆盖. PluginInfo 测试至少断言名称, 描述, 作者, 三个身份字段, 包版本, 日期, ABI, 最低宿主版本和契约版本.

新增可选方法时优先使用 capability negotiation. 宿主先读取契约版本或能力集合, 支持时调用新方法, 不支持时使用明确的旧路径或提示升级. 不要通过捕获任意异常猜测协议版本.

## 8. Binder 与公共 API 设计

- 公共常量, option key, capability key, ID, action 和 category MUST 集中在插件 API 中, 禁止散落字符串字面量.
- AIDL 方法的 nullability, 输入上限, 输出上限, 错误语义, 线程行为和资源所有权必须有文档与测试.
- Binder 输入 MUST 做边界校验. 列表长度, 文本大小, Bundle key, 枚举值, URI scheme 和索引均不得无限制接受.
- 批量能力 SHOULD 使用单次 Binder 往返, 同时设置明确上限, 避免 Binder transaction 过大.
- 已发布 AIDL 演进时保持旧 transaction 顺序, 在末尾追加新方法, 并通过 contract version 协商. 破坏性重设计必须同步升级宿主和插件契约.
- 文件能力优先使用 `content://` URI, `ParcelFileDescriptor` 或宿主 session, 不依赖对方可见的绝对文件路径.
- 对宿主提供的 Binder token, URI grant 和事务 ID 进行匹配校验. 操作失败时清理临时输出, 不留下半写入文件.
- 不在 Binder 主路径执行无界网络访问或不可取消的长耗时初始化.
- 服务被进程回收, 首次绑定, 重复绑定和并发调用都应保持确定行为.

## 9. 主项目职责与合理退化

如果新插件同时需要修改 `D:/idea-projects/AutoJs6`, MUST 遵循以下边界:

- 主项目保留合适的菜单, 文件操作, 设置项或脚本 API 入口.
- 主项目先发现插件状态, 再区分 `未安装`, `已安装但未激活或禁用`, `版本不兼容`, `调用失败` 和 `可用`.
- 未安装时显示简洁说明并引导到可信的插件安装来源.
- 已安装但未启用时显示 `激活` 或 `启用` 操作, 通过标准 Wake 协议处理, 不要求用户手动寻找应用图标.
- 不兼容时明确显示所需宿主或插件版本, 不默默回退到主项目中的重复完整实现.
- 调用失败时保留用户当前上下文, 提供可重试且可理解的错误信息.
- 插件禁用或卸载后, 主项目不应继续具备插件拥有的完整业务能力. 合理退化可以保留入口, 文件类型识别, 只读摘要或安装引导, 但不能悄悄保留一套功能等价实现.
- 更新插件包名, action, category, ID 或 API 时, 同步检查主项目注册表, ProGuard/R8, 文件类型入口, 安装 URL, 启用状态缓存和测试夹具.

若改动公开脚本 API, 还要检查并同步:

- `D:/webstorm-projects/AutoJs6-Documentation`
- `D:/webstorm-projects/AutoJs6-TypeScript-Declarations`
- `D:/idea-projects/AutoJs6-Plugin-Offline-Docs`
- `D:/idea-projects/AutoJs6-Plugin-Ace-Editor`

仅在真实涉及对应 API 时运行这些关联项目的生成脚本. 生成后按各仓库 `AGENTS.md` 处理版本, 测试和提交.

## 10. 应用标题与字符串资源

### 10.1 支持语言

所有用户可见字符串 MUST 覆盖当前插件共同使用的 10 种语言:

- 默认英语: `values/`
- 显式英语: `values-en/`
- 阿拉伯语: `values-ar/`
- 西班牙语: `values-es/`
- 法语: `values-fr/`
- 日语: `values-ja/`
- 韩语: `values-ko/`
- 俄语: `values-ru/`
- 简体中文: `values-zh/`
- 香港繁体中文: `values-zh-rHK/`
- 台湾繁体中文: `values-zh-rTW/`

`values/strings.xml` 与 `values-en/strings.xml` 的共有条目 MUST 内容一致. 各语言的格式化占位符, 转义和数量也必须一致.

### 10.2 不可翻译标题

- 所有插件标题和 Application Label MUST 为英文且不可翻译.
- `AndroidManifest.xml` 使用 `android:label="@string/app_name"`.
- `app_name` 使用 `resValue("string", "app_name", "{APP_NAME}")` 生成, 或放入 `values/strings_donottranslate.xml` 并标记 `translatable="false"`.
- `plugin_author`, `plugin_id`, `plugin_engine`, `plugin_variant` 等不可翻译项使用 Gradle `resValue` 或统一放入 `strings_donottranslate.xml`.
- 不要在多个 locale 中复制不同语言的 `app_name`.

### 10.3 plugin_description

- 每个 locale MUST 有 `R.string.plugin_description`.
- 描述简洁, 直接说明作用或功能, 句尾不加点号或其他终止标点.
- 禁止使用 `文件管理器插件. xxx` 或同义前缀. 插件即使可从文件管理器调用, 描述也应强调其本身能力.
- 除专属关系明确的插件, 避免 `适用于 AutoJs6`, `用于 AutoJs6`, `为 AutoJs6 提供` 及其他把功能限定到 AutoJs6 的表述.
- 示例: `预览 HTML 文件并检查页面内容` 优于 `文件管理器插件. 为 AutoJs6 提供 HTML 预览功能.`
- 英文示例: `Previews HTML files and inspects page content`, 句尾不加 `.`.

### 10.4 排序与标点

- `strings.xml` 中所有 `<string>` 按 `name` 升序排列.
- 不可翻译字符串统一放入 `strings_donottranslate.xml`.
- `<plurals>` 放入 `plurals.xml`, string array 和其他资源类型也放入各自合适的独立文件.
- 所有资源字符串避免全角和 CJK 标点. 使用 ASCII `, . : ; ! ? ( ) [ ] / -` 等符号. 语言字符本身不受此限制.
- 省略号一律使用三个 ASCII 点 `...`, 并添加 `tools:ignore="TypographyEllipsis"`. 不使用 U+2026 HORIZONTAL ELLIPSIS.
- 避免弯引号, 全角括号, 顿号和全角冒号. 必须表达并列关系时重写句子或使用 ASCII 逗号.
- 修改资源后用脚本扫描常见违规字符, 并人工检查 XML entity, apostrophe, `%` 占位符和双向文字布局.

### 10.5 启动器图标

- MUST 在 `app/src/main/res/mipmap/ic_launcher.png` 生成并保留一份适合当前插件的 PNG 启动器图标.
- 图标必须能体现插件自身用途, 不直接沿用其他插件的图案, 文字或颜色身份. 从宿主图标衍生时也应加入可辨识的插件语义.
- PNG 应为正方形, 边缘和透明区域合理, 在浅色与深色背景及插件中心的小尺寸展示中仍可辨认. 不要仅把低分辨率截图改名为启动器图标.
- Manifest 的 `android:icon` 与 README 模板必须指向真实存在的图标. 若模板引用 `mipmap-night/ic_launcher.png`, 则生成匹配的夜间图标, 否则从模板移除无效的夜间 source.
- 项目需要 adaptive icon 或不同 density 资源时可以额外提供, 但不得因此缺少上述基础 `mipmap/ic_launcher.png`.

## 11. README 与多语言生成

### 11.1 单一文案源

- README, 插件中心说明和 changelog SHOULD 由 `.readme/*.json`, `.changelog/*.json` 与模板统一生成.
- `.python/generate_markdown.py` 必须提供写入模式和 `--check` 只读校验模式.
- Windows 入口保留 `.python/generate_markdown.bat` 和 `.python/check_markdown.bat`.
- 生成器 SHOULD 检查语言集合, JSON 结构, 未替换占位符, 版本对齐, 孤儿产物和生成文件漂移.
- 修改 JSON 或模板后先运行生成器, 再运行 `--check`. 不手工修改随后会被生成器覆盖的 README 或 CHANGELOG.
- 多语言 Python 工具生成到项目根目录的 `README.md` MUST 明确标识为简体中文版本. 语言导航, 页面说明或其他醒目位置应出现 `简体中文`, 不能让读者误以为根 README 是无语言归属的通用版本.
- 根 `README.md` SHOULD 与 `.readme/README-zh-Hans.md` 使用同一份简体中文 JSON 文案源并保持内容同步, 不单独维护另一套简体中文正文.

### 11.2 README 头部

所有 README 使用 OpenCC `template_readme.md` 的 `<div align="center">` 风格, 保持图标, 一句话简介及 Release, Issues, License 徽章. 基础形态如下:

```html
<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <picture>
      <source srcset="{{ repo_url }}/blob/master/app/src/main/res/mipmap-night/ic_launcher.png?raw=true" media="(prefers-color-scheme: dark)" />
      <img src="{{ repo_url }}/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="{{ icon_alt }}" border="0" width="128" />
    </picture>
  </p>

  <p>{{ text_plugin_synopsis }}</p>

  <p>
    <a href="{{ repo_url }}/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/{{ repo_slug }}?label=Release"/></a>
    <a href="{{ repo_url }}/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/{{ repo_slug }}?color=A24232&label=Issues"/></a>
    <a href="{{ license_url }}"><img alt="GitHub License" src="https://img.shields.io/github/license/{{ repo_slug }}?color=534BAE&label=License"/></a>
  </p>
</div>
```

若没有夜间图标, 必须同步简化模板, 不留下失效链接.

### 11.3 README 内容

- 使用自然语言, 先说明用户能完成什么, 再说明安装和使用方式.
- SHOULD 包含语言入口, 简介, 功能, 安装, 快速使用或脚本示例, 兼容性, 常见问题, 发行历史, 许可证和必要的第三方声明.
- 有 UI 时提供真实且仍有效的截图. 截图中的包名, 版本和文案不得与当前实现矛盾.
- 不写 Android Studio 或 IntelliJ IDEA 版本信息. 构建所需 JDK, Gradle 和 Android SDK 只在确有用户价值时简洁说明.
- 不向普通用户解释 `supportedAbis = emptyArray()`, 内部 ABI 拆分策略, 安全边界声明, 证书签名过程或其他实现细枝末节, 除非它们直接影响安装, 权限, 数据或兼容性.
- 不把文件管理器入口写成插件的唯一用途, 除非插件确实只能通过该入口工作.
- 描述插件与 AutoJs6 的关系时保持克制. 专属协议, 离线文档和 APK 构建模板等明确专属能力可以说明依赖关系.
- README 中的链接必须指向真实仓库, release, issue, license 和生成 changelog, 不保留复制来源的 slug.

## 12. Changelog

### 12.1 文件边界

- `.changelog/` 只存放 10 个 `lang_*.json` 和 Markdown 模板, 不存放 `CHANGELOG-zh-Hant-TW.md` 等生成的多语言 changelog.
- 生成的 changelog 放入 `app/src/main/assets/doc/` 以及生成器明确规定的其他输出位置.
- `.readme/README-*.md` 可以作为生成产物保留, 但其内容必须由 JSON 与模板生成.

### 12.2 更新时机

当提交涉及 `feature`, `fix`, `improvement` 或 `dependency` 中任意分类时, MUST 更新当前版本的全部语言 JSON:

- 当前版本来自 `version.properties` 的 `VERSION_NAME`, 忽略 Alpha 或 Beta 等后缀后使用 `vX.Y.Z` key.
- `released_date` 更新为当日日期, 格式遵循既有 JSON, 推荐 `YYYY/MM/DD`.
- 同一分类内条目按功能关系和用户影响排序, 不把相关内容分散到多个位置.
- 与 AutoJs6 GitHub Issue 有关时 MUST 按既有格式写明 Issue 引用.

### 12.3 固定标签与写法

- 分类 key 只使用 `hint`, `feature`, `fix`, `improvement`, `dependency`.
- 标签必须沿用既有固定翻译. 简体中文对应 `提示`, `新增`, `修复`, `优化`, `依赖`. 英文对应 `Hint`, `Feature`, `Fix`, `Improvement`, `Dependency`.
- 其他语言从 OpenCC 当前 JSON 模板复制固定标签, 不临时发明近义词.
- 分类已经表达语义. `feature` 条目不要再次以 `新增` 开头, `fix` 条目不要再次以 `修复` 开头. `improvement` 可按自然语义表达.
- `dependency` 只记录 Gradle 构建脚本中 `implementation` 等实际依赖的变化. 字体, 模型, 普通资源和工具下载不计作 Gradle dependency.
- 依赖条目使用各语言历史中固定的动作词. 简体中文只使用既有的 `附加`, `升级`, `移除`, `模块化`, `本地化` 等术语和格式.
- 避免披露对用户无帮助的内部细节. 但行为变化, 兼容性, 数据影响和必要迁移必须如实记录.
- 所有语言表达自然且含义等价, 同时遵守 ASCII 标点约束.

更新 JSON 后运行:

```powershell
py .python/generate_markdown.py
py .python/generate_markdown.py --check
```

## 13. 独立界面, 设置与发行历史

以下规则仅适用于带独立 Activity, 设置页或插件更新检查的项目.

- 独立界面 SHOULD 跟随宿主或系统的语言, 夜间模式和主题设置, 并在宿主配置不可用时安全回退.
- 设置页 MUST 提供独立的 `发行历史` 入口, 复用应用内置的本地化 changelog 资产.
- 发行历史页面按当前 locale 选择 `doc/CHANGELOG-{LANGUAGE_TAG}.md`, 找不到时回退到英语, 加载失败时显示本地化错误.
- 更新对话框的 Neutral 按钮 `发行历史` MUST 打开内置发行历史 Activity, 不再跳转浏览器 URL.
- 更新对话框用于查看在线 release 的 Positive 按钮可继续打开可信发布页. 内置历史和在线发布页是两个不同动作.
- 更新检查必须有超时, 取消, 失败提示, 忽略版本和频率限制. 自动检查默认行为及计量网络策略应明确且可配置.
- 所有新增界面覆盖无障碍标签, 键盘或遥控导航, RTL, 大字体, 夜间模式和进程恢复.

## 14. 测试要求

### 14.1 本地单元测试

MUST 覆盖:

- PluginInfo 纯数据映射和 capability 内容.
- 输入规范化, 边界值, 非法 option, 空输入, Unicode supplementary code point 和异常路径.
- 版本比较, 最低宿主版本和 contract version 协商.
- 文件选择, MIME, 扩展名或 action catalog 规则, 若插件提供文件入口.
- 生成器或上游校验脚本的核心逻辑, 若项目包含自定义脚本.

### 14.2 Android instrumentation

Binder 插件 MUST 至少覆盖:

- 使用 action 与 category 发现且只发现一个预期 Service.
- 通过显式 Component 成功绑定, Binder descriptor 正确.
- `getInfo()` 返回当前包版本, 本地化描述, ID, engine, variant, ABI 和 capabilities. ABI 无限制时断言 `supportedAbis` 是显式空数组, 受限时断言其内容与构建拆分列表一致.
- 至少一次真实 Binder happy path, 非法参数, 大小上限和错误传播.
- 绑定, 解绑, 重复调用和进程重建路径不泄漏 ServiceConnection 或文件描述符.
- Manifest 中所有插件入口的 exported 与 permission 值正确.
- Wake Activity 和 INFO Service 的发现契约正确.

### 14.3 原生与重型能力

- `CONDITIONAL`: 每个发布 ABI 都验证 APK 中的原生库清单和加载结果.
- `CONDITIONAL`: 至少在 `arm64-v8a` 和 `x86_64` 环境执行真实 Binder 往返. 支持 16 KB page size 时增加对应模拟器或设备任务.
- `CONDITIONAL`: 模型下载, 解压或上游资源安装必须覆盖校验失败, 空间不足, 中断, 原子替换和损坏恢复.
- 性能 benchmark 与正确性测试分开. 不用宽松 benchmark 阈值掩盖功能错误.

## 15. CI 基线

- `build.yml` SHOULD 在 push, pull request 和手动触发时运行, 默认 `contents: read` 最小权限.
- CI 使用仓库 Gradle Wrapper 和受支持 JDK, 缓存 Gradle, 运行单元测试并组装 debug APK 与 androidTest APK.
- 有 Binder instrumentation 时, 使用 Android Emulator 执行发现和真实往返, 不只编译测试 APK.
- 原生插件采用 ABI matrix, 并为 16 KB page size 增加独立任务.
- `markdown.yml` 在 Windows 环境运行 `.python/check_markdown.bat`, 阻止生成文档漂移.
- 自定义 Python 构建或上游脚本必须运行其单元测试和固定来源校验.
- 任务设置符合真实构建时长的 timeout. 不因单个步骤可能耗时而省略最终验证.
- CI action 使用固定的大版本或仓库既有升级策略, 并定期更新. 不复制已经过期的 action 版本.

## 16. 验证顺序

根据变更范围执行最小但充分的验证. PowerShell 下的典型顺序如下:

```powershell
py .python/generate_markdown.py --check
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew.bat :app:lintDebug
```

有可用设备或模拟器时再执行对应 instrumentation task. Release 前额外执行签名发布任务, APK 集合校验, 安装和 Binder smoke test.

签名文件可用时, Release 预检至少执行:

```powershell
./gradlew.bat :app:appendDigestToReleasedFiles
```

检查 `releases/` 中只出现预期的已签名 APK, 单 ABI 与 Universal 变体齐全, 文件名语义正确, CRC32 与文件内容一致.

- 不因为全量构建耗时就只运行静态搜索.
- 不为文档单改运行不相关的重型原生全量构建, 但 MUST 运行文档生成校验.
- 构建时间较长时定期查看状态, 给予任务足够时间. 不用过短 timeout 将仍在正常运行的构建误判为失败.
- 任何未执行的验证都在最终说明中明确列出原因.

## 17. 许可证, 安全, 隐私与第三方内容

- 项目根目录 MUST 有一份与代码来源, 分发方式和第三方依赖兼容的 `LICENSE`. 不得保留空文件, 占位文本或从无关项目复制但实际不适用的许可证.
- README 徽章, 仓库元数据, 发布说明和源码头中的许可证名称必须与 `LICENSE` 一致. 使用 MPL 2.0 或其他许可证时保留其完整正式文本.
- 默认 `android:allowBackup="false"`, 除非产品需求明确要求备份并已审查敏感数据.
- 导出组件坚持最小化, 并使用插件签名权限保护契约入口.
- 不记录用户文件正文, API key, token, 私有 URI 或模型提示词到普通日志.
- 联网插件明确 endpoint, 超时, 重试, User-Agent, 代理和证书错误行为. 离线插件不得隐藏联网.
- API key 和账号配置存储在合适的私有存储中, 不写入资源, BuildConfig, 示例配置或提交历史.
- 文件处理使用最小权限和短生命周期 grant. 不申请与功能无关的存储, 网络, 麦克风或媒体权限.
- 第三方代码, AAR, native library, 模型和资源必须记录许可证与来源. 需要时增加 `THIRD_PARTY_NOTICES.md`.
- 固定上游版本时优先记录 release tag, commit 和 SHA-256. 自动更新脚本先验证来源和许可, 再生成可审查差异.

## 18. 完成检查清单

### 身份与构建

- [ ] 所有占位符已替换, 项目名, root name, applicationId, 包名, ID, engine, variant, action 和 category 一致.
- [ ] 预览器使用 `Previewer`, 查看器使用 `Viewer`.
- [ ] `rootProject.name` 的值全部为小写 kebab-case.
- [ ] 已从平台版本仓库读取并替换 `{PLATFORM_VERSIONS_PLUGIN_VERSION}`, 在线使用 `io.github.supermonster003.autojs6-platform-versions` 1.7.0 或经统一验证的后续稳定版, 不依赖 `mavenLocal()`.
- [ ] 平台插件只在根 `settings.gradle.kts` 应用一次, 位于 `includeBuild` 之前; `build-logic/settings.gradle.kts` 没有重复应用.
- [ ] 根 `build.gradle.kts` 已用 `gradle.agp.version` / `gradle.kotlin.version` / `gradle.ksp.version` 完整声明模块实际使用的插件, 且全部为 `apply false`.
- [ ] 根 settings 已移除旧内联 `agpVersionMap` / settings buildscript classpath 机制, Temurin 验收构建只输出一套版本决策并通过.
- [ ] 迁移验证完成后已删除 `*.pre-platform-versions.bak`, 且 Git 索引中从未包含这些临时副本.
- [ ] 仓库中不存在消费端 `gradle/data`, 也没有仅为中央候选下界重复声明 `MIN_SUPPORTED_ANDROID_GRADLE_PLUGIN_VERSION` 或通用 IDE 最低版本; 项目专用 IDE 下界只用于收紧中央支持范围并附有理由, 兼容数据修复已在中央仓库发布为新的不可变版本.
- [ ] Gradle 未引用插件仓库外部的 JAR, AAR, `flatDir` 或兄弟项目路径.
- [ ] 无不必要的 VSCode 版本要求代码或 `id("org.jetbrains.kotlin.android")` 声明.
- [ ] 版本, SDK 和 JDK 由统一约定读取.
- [ ] `VERSION_NAME` 初始为 1.0.0 或已按语义正确升级.
- [ ] `VERSION_BUILD` 与 `git rev-list --count HEAD` 完全一致.
- [ ] 已从宿主复制 `.gitignore`, `sign.properties` 和 `app/sm003.jks`, 后两个文件已由 Git 忽略.
- [ ] 签名脚本完整, `appendDigestToReleasedFiles` 可收集已签名 APK 并生成语义正确的 CRC32 文件名.
- [ ] 已尽量启用 ABI splits 与 Universal APK, 或有可验证的无需拆分理由.

### 激活与契约

- [ ] Manifest 声明 `org.autojs.permission.PLUGIN`.
- [ ] `WAKE_ACTIVITY`, Wake Activity, WAKE action 和默认 category 完整且可解析.
- [ ] 导出的插件组件受权限保护.
- [ ] 服务可按正式 action/category 发现并显式绑定.
- [ ] PluginInfo 字段, ABI 和 capability 与真实安装包一致.
- [ ] ABI 无限制时, `getInfo()` 显式写有 `supportedAbis = emptyArray()`.
- [ ] 主项目在未安装, 未启用, 不兼容和失败时均有合理提示, 且不保留重复完整实现.

### 资源与文档

- [ ] Application Label 为不可翻译英文.
- [ ] `app/src/main/res/mipmap/ic_launcher.png` 存在, 可辨识且与 Manifest 和 README 引用一致.
- [ ] 10 种语言资源完整, 默认英语与 `values-en` 共有内容一致.
- [ ] `plugin_description` 简洁, 无句尾点号, 无文件管理器前缀, 无不必要的 AutoJs6 表述.
- [ ] 资源字符串无全角或 CJK 标点, 省略号使用 `...`.
- [ ] string 按 name 排序, 不可翻译项和 plurals 位于正确文件.
- [ ] README 使用 OpenCC 风格居中 `<div>`, 无 Android Studio 或 IntelliJ IDEA 版本信息.
- [ ] 根 `README.md` 明确标识为简体中文版本, 并与 `README-zh-Hans.md` 使用同一文案源.
- [ ] README 表达自然, 不暴露无关内部细节, 所有链接和截图有效.
- [ ] 根目录 `LICENSE` 完整, 合适, 且与 README 徽章及第三方许可兼容.
- [ ] `.changelog` 只含 JSON 与模板, 生成的多语言 changelog 位于资产目录.
- [ ] 当前版本全部语言 changelog 已更新并生成.

### 测试与交付

- [ ] 单元测试覆盖元数据, 边界和能力协商.
- [ ] instrumentation 覆盖发现, 绑定, Binder 往返, permission 和 Wake 契约.
- [ ] 原生插件已验证所有 ABI 和 16 KB page size, 或明确记录不适用.
- [ ] Markdown check, 相关 Gradle 测试, assemble 和 lint 已通过.
- [ ] 可用时已完成特殊设备安装与激活实测, 否则明确记录未验证.
- [ ] `git diff --check` 通过, 无秘密, 临时文件或无关改动.
- [ ] `git init` 后的初始文件和本次会话改动均已按逻辑提交.
- [ ] 最终 `git status --short` 无输出, 验证范围和未执行验证已清楚说明.

## 19. 参考项目路由

只读取完成当前任务所需的参考, 不复制项目专属内容:

- 构建平台, Wake 激活, PluginInfo, 多语言生成和 README 样式: `D:/idea-projects/AutoJs6-Plugin-OpenCC`
- 纯 JVM PluginInfo 与真实 Binder 测试: `D:/idea-projects/AutoJs6-Plugin-Pinyin4j`
- 原生 ABI, 发布产物和 16 KB 验证: `D:/idea-projects/AutoJs6-Plugin-OpenCC` 与 `D:/idea-projects/AutoJs6-Plugin-MediaInfo`
- Explorer Action, 独立设置和内置发行历史: `D:/idea-projects/AutoJs6-Plugin-Three-Ember-Player` 与 `D:/idea-projects/AutoJs6-Plugin-Three-Terra-Player`
- 更新对话框与发行历史页面: `D:/idea-projects/AutoJs6-Plugin-Three-Stone-AI`
- 宿主入口, 插件发现, 安装和启用引导: `D:/idea-projects/AutoJs6`

参考时以这些仓库的当前代码为准, 不以历史 README 或旧 release 中已经淘汰的写法为准. 复制骨架后必须替换身份字段, URL, 文案, API 常量, 版本, 许可证和测试数据.
