# AutoJs6 APK 体积缩减 Roadmap

本文是宿主 APK (`app` 模块, `app` 与 `inrt` 两个 flavor 共用同一套依赖) 体积缩减的可执行状态表.
以 2026-09-17 的本地代码快照 (`master@479988e1b`, `VERSION_NAME=6.8.0`, `VERSION_BUILD=5281`) 为起点,
每个条目均可独立 Check 并落地, 后续会话按阶段逐步推进.
输入包括 Codex 于 2026-09-17 给出的五条建议 (Flexmark, 更新检查 SDK, JUnit 传入路径, DEX 编译器, APK 签名依赖);
第 3 节对其逐条核实, 核实结果与建议不一致处已按证据改写.
2026-09-17 维护者已对附录 A 的 Q1-Q8 拍板, 结果回填为 D10-D16; 新增 Q9 待决.

使用方式:

1. 每次会话开始时, 从 "阶段总览" 选取一个或多个未完成条目, 优先级按阶段顺序; P1 内条目互不依赖, 可以任选.
2. 条目完成后勾选 `[x]`, 并在条目后追加证据 (提交 hash / 测试类名 / 设备型号 / 体积报告文件), 证据等级见附录 C.
3. 凡是改变 APK 内容的条目, 完成后用 `tools/size/apk_size_report.py --compare docs/dev/evidence/apk-size-baseline-20260917.json` 生成报告, 存为 `docs/dev/evidence/apk-size-<阶段>-<日期>.md` (JSON 同名), 条目证据中引用该文件与 universal APK 的字节数.
4. 涉及依赖增删的提交必须同步 `.changelog/lang_*.json` 的 `dependency` 分类 (10 语言, 固定术语 "移除" / "附加" / "升级" / "模块化" / "本地化") 并重新生成 markdown (D7); 涉及用户可感知行为的条目另加 `improvement` / `fix`.
5. 附录 A 的 "待决事项" 在进入对应阶段前由维护者拍板, 拍板结果回填到 "固定决策".

---

## 1. 固定决策与硬约束

| 编号 | 决策 | 含义 |
| --- | --- | --- |
| D1 | 度量口径 | 主指标为 `:app:assembleAppRelease` 产出的 `autojs6-v<ver>-universal.apk` 文件字节数, 副指标为 `arm64-v8a` split. 构成分析与前后对比一律用 `tools/size/apk_size_report.py` (zip 条目分组 + `apkanalyzer dex packages`), 不手工估算. DEX 表中的 "包归属原始字节" 不含共享字符串池, 移除一个包的 APK 收益以 0.39 倍归属字节为保守下限. |
| D2 | 脚本可见的 Java 表面 | 脚本可通过 `Packages.*` / `importClass` 访问 APK 内任何类, 因此移除依赖等于移除脚本表面. 文档项目 (`D:/webstorm-projects/AutoJs6-Documentation/api`) 明确记载的第三方类不移除: 目前只有 `org.joda.time` (`scriptingJava.md`, `workManager.md`). 仅出现在声明文件生成物 `libraries.d.ts` / `.generated/autojs6-dts-inputs-*.json` 中的库不算文档承诺, 但每次依赖移除后要在下一次 d.ts 发布 (`aj6dts.bat -Publish`) 中重新生成. |
| D3 | `isMinifyEnabled` 保持 `false` | 脚本对全部类的反射访问, 插件 AIDL 表面, Rhino 动态类生成与 `defineClass` 都依赖未混淆 / 未收缩的 DEX. 只有 P6.7 的实验条目给出体积与兼容证据并经维护者拍板 (Q1) 后才考虑改变. |
| D4 | `libc++_shared.so` 保留 | 它是进程级 C++ 运行时: OpenCV 插件与 APK Builder 模板的原生库在宿主进程内加载时依赖它 (`docs/dev/opencv-plugin-integration.md`), 并与 16 KB 对齐链路 (NDK r28.2 重编) 绑定. 四个 ABI 共 1.50 MiB 压缩体积不在本 Roadmap 削减范围内. |
| D5 | DEX 编译器宿主依赖的移除以插件门禁为前提 | `com.android.tools:r8` (6.59 MiB 归属 DEX + 2.93 MiB `resources/new_api_database.ser`) 的移除前提是 `docs/dev/dex-compiler-plugin-evaluation.md` 的 Phase 2 (默认启用 + 宿主回退) 与 Phase 3 (依赖移除决策) 通过, 而 R5 性能证据 (`dex-compiler-r5-performance-evidence-2026-08-27.md`) 的缓存命中延迟门禁目前 0/3. 本 Roadmap 只做不依赖插件的部分 (P5.1 dx 合并到 D8), 并记录 R8 的体积账目; `new_api_database.ser` 是 D8 的 API 建模数据, 不能单独用 packaging 排除. |
| D6 | apk-signer 模块共用 | `modules/apk-signer` 同时被宿主与 `plugins/apk-builder-template` 依赖. 宿主必须保留对远程构建输出 APK 的独立签名校验 (`RemoteBuildApkValidator` 用 `com.android.apksig.ApkVerifier`) 与预期签名解析 (`RemoteBuildExpectedSignerResolver` 用 `KeyStoreHelper.loadJks` / `loadBks`); 密钥创建 (`CertCreator`) 与签名执行 (`ApkSigner` / `ApkSignerTool`) 只属于插件. 拆分后插件的功能与既有 JKS / BKS 兼容回退不能退化, 系统 `BKS` 实现是否足以替代 SpongyCastle 回退必须先有设备证据 (P4.2). |
| D7 | 依赖变更遵守 `AGENTS.md` | `dependency` 日志 10 语言同步, 每种语言只用既有固定术语 (如 zh-Hans 的 "移除 X 版本 Y"). 6.8.0 尚未发布: 同一版本内先 "附加" 后又移除的依赖 (Apache Commons Codec 1.15) 直接删除其 "附加" 条目, 而不是追加 "移除" 条目. 本地 jar 无版本号时按既有条目省略版本 (如 "模块化 Android AXML"). |
| D8 | 二进制 jar 不手工改写 | 需要裁剪的本地 jar (Rhino fat jar 内的 JLine) 通过 `app/build.gradle.kts` 中的 Gradle `Jar` 任务从原 jar 派生: 原 jar 保持 `compileOnly` 供 IDE 与编译使用, 派生 jar 走 `runtimeOnly`, 打包与单测使用派生 jar. 原始 jar 与其来源不变, 裁剪规则可审阅. |
| D9 | Roadmap 位于 `docs/dev/`, 证据位于 `docs/dev/evidence/` | 与仓库内既有 roadmap 的位置与风格一致. |
| D10 | release 不启用 minify (Q1, 2026-09-17) | D3 定案, P6.7 的测量实验取消; R8 收缩 / 混淆不再作为体积手段评估. |
| D11 | media3 替换为平台播放器 (Q2, 2026-09-17) | P6.1 进入实施: `ScriptMusicService` 改用 `android.media.MediaPlayer` + 平台 `android.media.session.MediaSession` + `Notification.MediaStyle`, 不引入新依赖; 脚本 `media` 模块的公开行为与 `evidence/android-sdk-37-media3-20260914.md` 记录的前台服务 / 通知 / 焦点行为作为回归清单. |
| D12 | log4j 替换为自有文件日志 (Q3, 2026-09-17) | P6.2 进入实施: 保留文档化的 `console.setGlobalLogConfig` / `resetGlobalLogConfig` 全部选项与 PatternLayout 子集语义; 脚本全局代理类 `LogConfigurator` / `Level` / `LogManager` (文档 `global.md` 列出) 的去留见 Q9. |
| D13 | `JsTextViewLegacy` 为 Android 7.x 预留 (Q4, 2026-09-17) | 该分支修复 issue #4 / #9 (Android 7.x 解析 UI 模式 `text` 元素异常). 允许更好方案: 只有在 API 24 / 25 上复现并用现行 appcompat 修复后, 才移除 `libs/androidx-appcompat-1_0_2` 与 4 个 legacy 文件 (P6.4). |
| D14 | 内置 CHANGELOG 只保留 zh-Hans 与 en (Q5, 2026-09-17) | 其余 8 个语言与重复的 `CHANGELOG.md` 在资产合并时剪除, 离线回退链为 asset(当前语言) -> 缓存(当前语言) -> asset(en); 生成脚本与仓库内文件不变 (P6.5). |
| D15 | 资源优化进入 (Q6, 2026-09-17) | P6.6: 仅无损 WebP 转换与 lint `UnusedResources` 驱动的删除, 不重绘. |
| D16 | `com.stardust` 兼容包保留 (Q7, 2026-09-17) | P6.8 关闭; SpongyCastle 去留等 P4.2 设备证据 (Q8), 流程不变. |

---

## 2. 范围与非目标

范围内:

- 依赖声明: `app/build.gradle.kts` (`dependencies` 各段, `packaging`, `resourceExcludePatterns`), `gradle/libs.versions.toml`, 本地 jar `libs/*.jar` 与 `libs/javamail-android/`, 共用模块 `modules/apk-signer`.
- 直接受影响的宿主代码: `util/TextUtils.kt`, `ui/settings/ReleaseHistoryRepository.kt`, `network/UpdateChecker.java`, `model/explorer/ExplorerSorter.java`, `rhino/AndroidClassLoader.kt` 与 `com/stardust/autojs/rhino/AndroidClassLoader.java`, `apkbuilder/template/RemoteBuild*.kt`, 5 个 `kotlin-reflect` 调用点 (`runtime/api/augment/{global/GlobalClasses,ocr/Ocr,s13n/S13n,selector/Selector,web/WebSocketFields}.kt`).
- 度量工具 `tools/size/apk_size_report.py` 与证据文件.
- `inrt` flavor 与 runtime-kit 模板随宿主依赖同步受益, 不单独处理.

非目标 (本 Roadmap 不处理):

- 宿主自身代码 (`org.autojs.autojs.*`, 8.48 MiB 归属 DEX) 的重构或按功能拆分.
- 插件仓库 (OpenCV / OCR / Node.js 等) 的体积.
- 美术资源的重绘; 只做格式转换与未用资源检测 (P6.6).
- 脚本 API 的行为变更; 任何条目都不得改变已文档化的脚本表面 (D2).
- 已经模块化到插件的能力 (OpenCV 原生库, Offline Docs, APK Builder 模板 `default_key_store.bks`), 它们已由 `appMergedAssetPruneIncludes` 与插件契约处理.

---

## 3. 现状诊断 (基线 2026-09-17)

基线报告: [apk-size-baseline-20260917.md](evidence/apk-size-baseline-20260917.md) (JSON 同名), 由 `master@479988e1b` 的 `:app:assembleAppRelease` 产物生成 (release 未启用 minify, `lintVital` 报告的 `RemoveWorkManagerInitializer` 为既有非阻断错误).

| 产物 | 字节 | MiB |
| --- | ---: | ---: |
| `autojs6-v6.8.0-universal.apk` | 41,146,055 | 39.24 |
| `autojs6-v6.8.0-arm64-v8a.apk` | 39,949,029 | 38.10 |

universal APK 构成 (压缩后 MiB):

| 组 | 压缩 | 原始 | 说明 |
| --- | ---: | ---: | --- |
| DEX (10 个文件) | 26.18 | 67.64 | 433,419 个定义方法, 470,161 个引用方法; 包归属字节 40.94 MiB |
| `resources.arsc` | 3.33 | 3.33 | 存储不压缩; 已按 `supportedResourceLocales` 过滤语言 |
| `resources/new_api_database.ser` | 2.93 | 6.38 | R8 / D8 的 API 建模数据库 (D5) |
| `res/` (3,109 个文件) | 2.55 | 3.68 | 其中 PNG 约 1.86 MiB |
| `assets/doc` (12 个文件) | 0.95 | 3.18 | 10 语言 `CHANGELOG-*.md`, 用于离线版本历史 (Q5) |
| `lib/<abi>` x 4 | 1.53 | 4.32 | `libc++_shared.so` 1.50 (D4), jackpal x 8 共 0.03 |
| `assets/modules` + `assets/sample` + `assets/fonts` + `assets/indices` + `assets/js-beautify` | 0.59 | 2.10 | 脚本模块 / 示例 / 字体 / 索引 |
| 松散资源 (非 DEX / res / assets) | 0.60 | 1.51 | `org/joda/time` 时区表 0.12 (484 个文件), `org/jline/nativ` 0.11 (含 3 个 `.dll`), `org/mozilla/javascript` 0.10, `org/apache/commons/codec` 音标表 0.09 (128 个文件), `META-INF` 0.05 (204 个文件), `okhttp3` 公共后缀表 0.04, `languages/Prism_*.java` 0.02 (25 个文件), `mime.cache` 0.03 |

DEX 包归属字节 (原始 MiB) 中与依赖对应的项, 按体积排序, 附本 Roadmap 的处置:

| 包 | 归属 MiB | 来源 | 处置 |
| --- | ---: | --- | --- |
| `com.android.tools.r8` | 6.59 (+2.93 `.ser`) | `libs.r8`, 本地 D8 编译 (`AndroidClassLoader`) | P5.2, 受 D5 门禁 |
| `org.spongycastle` | 2.28 | `modules/apk-signer` -> `libs.prov` | P4 |
| `androidx.media3` + `com.google.common` (Guava, 经 `media3-common` 传入) | 2.18 + 1.13 | `libs.media3.*`, 仅 `core/media/ScriptMusicService.kt` | P6.1 (Q2) |
| `kotlin.reflect` | 1.44 | `kotlin("reflect")`, 5 个调用点 | P3 |
| `com.fasterxml` (Jackson) | 0.93 | `libs.jackson.databind`, 仅 `github-api` 需要 | P2 |
| `com.vladsch` (Flexmark) | 0.71 | `libs.flexmark.html2md`, 仅 `TextUtils.htmlToMarkdown`, 其唯一调用在已注释的 `parseHtmlFlow` | P1.1 |
| `com.android.dx` + `com.legacy.android.dx` + `com.android.dex` | 0.45 + 0.43 + 0.03 | 两个本地 dx jar, 仅 `AndroidClassLoader.dexJar()` (单类 `defineClass` 路径) 与 `com.stardust` 兼容加载器 | P5.1 |
| `com.stardust` | 0.43 | 旧 Auto.js 兼容包 | 保留 (Q7) |
| `org.apache.commons.lang3` | 0.41 | `libs.commons.lang3`; 宿主直接引用仅 `ExplorerSorter.isAsciiPrintable`, 但 `github-api` 在运行时解析 `StringUtils` / `ToStringBuilder` (`GitHubClient`, `GHRepository`, `GHObject` 等 22 个类) | P1.5 去掉宿主引用, 依赖随 P2.4 移除 |
| `org.jline` (+0.13 松散资源) | 0.36 | `libs/org-mozilla-rhino-2_0_0-SNAPSHOT.jar` 内打包的 JLine, 只有 Rhino shell 控制台用到; 宿主只引用 `tools.shell.ShellContextFactory` 与 `tools.debugger.*` | P1.7 |
| `androidx.appcompatlegacy` | 0.33 | `libs/androidx-appcompat-1_0_2`, 供 `JsTextViewLegacy` | P6.4 (Q4) |
| `org.joda` (+0.12 时区表) | 0.33 | `libs.joda.time`, 8 个文件; 文档记载的脚本 API | 保留 (D2) |
| `org.kohsuke` | 0.27 | `libs/github-api-1_306.jar`, 仅 `UpdateChecker` | P2 |
| `org.apache.log4j` | 0.23 | `libs.log4j` + `android-logging-log4j`, `GlobalConsole` 文件日志; 5 个已知 CVE | P6.2 (Q3) |
| `org.jsoup` | 0.23 | `UpdateChecker` (解析 GitHub 渲染的 HTML) 与 `parseHtmlFlow` (停用) | P2.5 |
| `javax.mail` + `com.sun.mail` + `javax.activation` | 0.25 | `libs/javamail-android/*.jar`, 全仓库 (含 assets / 文档 / d.ts) 无任何引用 | P1.3 |
| `org.apache.commons.io` | 0.14 | `github-api` 与 `apk-signer` 传入, 宿主无直接引用 | P1.4 (模块侧) / P2.4 (宿主侧) |
| `org.apache.commons.codec` (+0.09 音标表) | 0.12 | `modules/apk-signer` 声明但模块源码无引用 | P1.4 |
| `org.junit` + `junit` + `org.hamcrest` | 0.14 | 两条生产传入路径: `com.github.atlassian:commonmark-java` 聚合坐标带入 `commonmark-test-util`; `com.madgag.spongycastle:prov` 的 POM 把 `junit:junit:4.12` 声明为编译依赖. `resources.excludes("junit/**/*")` 只排除资源, 不影响 DEX | P1.2 |

对 Codex 建议的核实结论:

| 建议 | 核实 |
| --- | --- |
| Flexmark 停用链 | 成立. `htmlToMarkdown()` 的唯一调用在 `parseHtmlFlow()`, 其调用点已注释, 发布历史走 Markdown 解析. |
| 更新检查 SDK 过大 | 成立. `org.kohsuke` 只在 `UpdateChecker` (仓库 / release / assets / 令牌连接 / `renderMarkdown`); Jackson 0.93 MiB 与 Commons IO 只为它服务. |
| JUnit 两条传入路径 | 成立, 且 `resources.excludes` 确实只能排除资源. |
| DEX 编译器 | 成立, 但拆分: dx 两个 jar 的用途 (单类 `defineClass`) 可以直接合并到 D8 (D8 已在 API 24 上用于 jar 编译, 依赖 `desugar_jdk_libs` 的 `java.nio.file`); R8 的移除受 D5 门禁. |
| APK 签名依赖 | 成立, 但 `modules/apk-signer` 也被 `plugins/apk-builder-template` 依赖, 所以是模块拆分而非移除; `KeyStoreHelper.loadBks` 已经先尝试系统 `"BKS", "BC"` 再回退 SpongyCastle, 回退是否仍需要必须先测. |

理论收益 (压缩后, 保守估计): P1 约 1.0-1.5 MiB (实测 -1.28 MiB, 见 P1.8); P2 约 0.8 MiB (含 Commons Lang3); P3 约 0.6 MiB; P4 约 1.0 MiB; P5.1 约 0.4 MiB; P6.1 约 1.3 MiB; P5.2 约 5.5 MiB (受门禁). 不含 R8 约 5 MiB (约 12%), 含 R8 约 10.5 MiB (约 26%).

---

## 4. 阶段总览

### P0 度量基座

- [x] P0.1 `tools/size/apk_size_report.py`: 读取一个或多个 APK, 输出 zip 条目分组 (DEX / `resources.arsc` / `res` / `assets/<dir>` / `lib/<abi>` / `META-INF` / 松散资源), 扩展名分组, 最大条目, 以及 `apkanalyzer dex packages` 的包归属字节 (深度阈值 + 关注列表); `--json` / `--markdown` 落盘, `--compare` 与基线 JSON 按 "文件名去版本" 匹配并输出差值列. `apkanalyzer` 依次从 `--apkanalyzer` / `$APKANALYZER` / `$ANDROID_HOME` / `$ANDROID_SDK_ROOT` / `local.properties` 的 `sdk.dir` 定位. 证据: 本文第 3 节数据均由该脚本生成, 2026-09-17.
- [x] P0.2 基线证据: `master@479988e1b` 的 `assembleAppRelease` 产物 (universal 41,146,055 字节, arm64-v8a 39,949,029 字节) 生成 `docs/dev/evidence/apk-size-baseline-20260917.{md,json}`. 证据: 上述文件, SHA-256 记录在文件内, 2026-09-17.
- [x] P0.3 本 Roadmap 第 3 节现状诊断与第 1 节固定决策落盘. 证据: 本文, 2026-09-17.

### P1 零风险清理 (停用链路, 未使用依赖, 测试依赖误入生产)

- [x] P1.1 Flexmark 移除: 删除 `TextUtils.htmlToMarkdown` 与 `ReleaseHistoryRepository.parseHtmlFlow` (含已注释的调用与仅为其存在的 `Jsoup` import), 从 `app/build.gradle.kts` 与 `gradle/libs.versions.toml` 删除 `flexmark-html2md`; `EmojiReference.txt` 排除项随之失效, 一并删除. 预计 -0.71 MiB 归属 DEX. 证据: 报告中 `com.vladsch` 由 0.71 MiB 归零, 2026-09-17.
- [x] P1.2 JUnit 退出生产 DEX: (a) `libs.commonmark` 从聚合坐标 `com.github.atlassian:commonmark-java` 改为宿主实际使用的两个模块 `com.github.atlassian.commonmark-java:commonmark` 与 `commonmark-ext-heading-anchor` (同版本 `commonmark-parent-0.9.0`, 已在依赖树中解析, 离线可用); (b) `modules/apk-signer/build.gradle.kts` 对 `libs.prov` 排除 `junit` group; (c) `resourceExcludePatterns` 删除因此失效的 `junit/**/*`, `LICENSE-junit.txt`, `spec.txt`. 验证: 依赖树 (`:app:dependencies --configuration appReleaseRuntimeClasspath`) 不再出现 `junit` / `hamcrest`, 报告中 `org.junit` / `junit` / `org.hamcrest` 行归零. 预计 -0.14 MiB. 证据: 离线依赖树无 `junit` / `hamcrest` / `commonmark-test-util`, 三个包归零, `org.commonmark` 0.06 -> 0.05 MiB (ext 模块随聚合坐标一起消失), 2026-09-17.
- [x] P1.3 JavaMail 移除: 删除 `libs/javamail-android/` 三个 jar 与对应三行依赖. 全仓库 (`app/src`, assets, docs, d.ts) 无 `javax.mail` / `javax.activation` / `com.sun.mail` 引用. 预计 -0.25 MiB 归属 DEX, 另去掉 `META-INF/mailcap` 等松散资源. 证据: `com.sun.mail` / `javax.activation` 归零, `javax.mail` 只剩 log4j `SMTPAppender` 的引用残留 (0 个定义方法), 2026-09-17.
- [x] P1.4 `modules/apk-signer` 去掉未使用的 `commons-codec` 与 `commons-io` 声明 (模块与 `plugins/apk-builder-template` 源码均无 `org.apache.commons` 引用); 宿主侧 `commons-io` 仍由 `github-api` 需要, 留给 P2.4. 预计 -0.12 MiB 归属 DEX, -0.09 MiB 音标表资源 (128 个文件). 证据: `org.apache.commons.codec` 与 `org/apache/commons` 松散资源归零, 2026-09-17.
- [x] P1.5 Commons Lang3 去掉宿主直接引用: `ExplorerSorter` 的 `StringUtils.isAsciiPrintable(name.substring(0, 1))` 改为本地判断 (首字符在 `' '..'~'` 之间; 空名原调用会抛异常, 现按非 ASCII 处理). 依赖本身暂不删除: 去掉后报告显示 `github-api` 仍在运行时引用 `org.apache.commons.lang3.StringUtils` / `builder.ToStringBuilder` (`GitHubClient` 在请求路径上), 删除会让更新检查抛 `NoClassDefFoundError`; 依赖声明移到 GitHub API 段并注明随 P2.4 一起移除 (届时 -0.41 MiB). 证据: 第一次 P1 构建 (删除依赖) 的报告中 `org.apache.commons.lang3` 为 "referenced only" 128 字节 (`StringUtils` 7 处, `builder.*` 9 处引用), 依赖恢复后为 0.41 MiB 不变, 2026-09-17.
- [x] P1.6 打包排除: `languages/*` 与 `GrammarLocator.template.java` (prism4j-bundler 携带的 `Prism_*.java` 源码模板与定位器模板, 运行时无消费者), `DebugProbesKt.bin` (kotlinx-coroutines 调试探针). 注意 AGP 的 `packaging.resources.excludes` 是 java.nio glob: `languages/**/*` 只匹配至少两层深的条目, 首次构建因此未排除, 改为单层形式后生效. 预计 -0.02 MiB. 证据: 报告中 `other/languages` 归零, APK 内无 `languages/` / `GrammarLocator.template.java` / `DebugProbesKt.bin`, 2026-09-17.
- [x] P1.7 Rhino jar 去 JLine (D8): 在 `app/build.gradle.kts` 注册 `Jar` 任务从 `libs/org-mozilla-rhino-2_0_0-SNAPSHOT.jar` 派生去掉 `org/jline/**`, `META-INF/maven/org.jline/**`, `META-INF/native-image/**` 的 jar; 原 jar 改为 `compileOnly`, 派生 jar `runtimeOnly`. `ShellContextFactory` (`AndroidContextFactory`) 与 `tools.debugger.*` (`Dim`) 不引用 JLine; `META-INF/services/org.mozilla.javascript.tools.ConsoleProvider` 仅在 Rhino shell `Main` 中通过 `ServiceLoader` 读取, 宿主不走该路径. 预计 -0.36 MiB 归属 DEX, -0.13 MiB 松散资源 (含 3 个 `.dll` 与 4 个非 Android `.so`). 证据: `slimRhinoJar` 任务 (`app/build/slim-libs/*-slim.jar`, 957 -> 约 850 个条目, 2.9 MB -> 2.2 MB), 报告中 `org.jline` 归零 (仅 Rhino `JLineConsole` 的引用残留), `org.mozilla.javascript.tools.shell` 仍在 DEX (279 个方法); 60 个引用 Rhino 的 JVM 测试文件经 `testCompileOnly` 编译并通过, 2026-09-17.
- [x] P1.8 验证与证据: `:app:compileAppDebugKotlin` + `:app:testAppDebugUnitTest` 通过; `:app:assembleAppRelease` 后运行 `apk_size_report.py --compare` 生成 `evidence/apk-size-p1-<日期>.md`, 记录 universal / arm64 字节数与差值, 确认关注列表中 `com.vladsch` / `org.junit` / `junit` / `org.hamcrest` / `com.sun.mail` / `javax.activation` / `org.apache.commons.codec` 归零, `javax.mail` / `org.jline` 只剩未定义的引用残留 (log4j `SMTPAppender` 与 Rhino `JLineConsole` 引用的类型 id, 各不足 1 KiB, 两个类都不会被加载); `.changelog` 10 语言 `dependency` (移除 Flexmark Java HTML to Markdown 0.64.8 / JavaMail for Android 本地库; 删除 Apache Commons Codec 1.15 的 "附加" 条目) 与一条 `improvement` (体积缩减) 落盘并重新生成 markdown. 证据: `:app:assembleAppRelease` + `:app:testAppDebugUnitTest` (466 个结果文件, 0 失败) 通过; [apk-size-p1-20260917.md](evidence/apk-size-p1-20260917.md): universal 41,146,055 -> 39,803,986 字节 (-1,342,069, -1.28 MiB, -3.3%), arm64-v8a 39,949,029 -> 38,606,960 字节 (-1.28 MiB); 定义方法数 433,419 -> 415,464; 该 APK 由 changelog 落盘前的源码构建 (只差 `assets/doc` 中新增的日志行). 等级 E1, 2026-09-17.

### P2 更新检查瘦身 (GitHub SDK -> REST)

- [ ] P2.1 盘点 `UpdateChecker.GitHubRepoUtils` 用到的接口: `GitHub.connectAnonymously` / `connectUsingOAuth(token)`, `getUser(name).getRepository(repo)`, `repo.getLatestRelease()` (含 `getTagName` / `getBody` / `getPublishedAt`), `release.listAssets()` (`getName` / `getSize` / `getBrowserDownloadUrl`), `repo.renderMarkdown(body, MARKDOWN)`; 记录鉴权头 / 限流 / 错误处理与 ABI 选包 (`pickAssetIntelligently`) 和多语言更新说明 (`fetchLatestChangelog`) 的现有行为作为回归清单.
- [ ] P2.2 新增 `network/github/GitHubReleaseClient` (OkHttp + Gson, 复用宿主已有的 `OkHttpClient`): `GET /repos/{owner}/{repo}/releases/latest` 与 assets 列表, 可选 `Authorization: Bearer <token>`, 与 SDK 相同的超时与 `User-Agent`; 数据类只保留宿主使用的字段. JSON 解析与选包逻辑抽为纯函数并加 JVM 单测 (固定 JSON fixture).
- [ ] P2.3 Markdown 渲染本地化: `renderMarkdown` 远程渲染改为 `TextUtils.markdownToHtml` (commonmark 已在宿主), 保留后续 `Jsoup` 解析到 `Spanned` 的流程不变; 无网络渲染失败分支随之消失.
- [ ] P2.4 删除 `libs/github-api-1_306.jar`, `libs.jackson.databind`, 宿主侧 `libs.commons.io` (确认 `modules/apk-signer` 在 P1.4 后不再传入) 与 `libs.commons.lang3` (P1.5 之后只剩 github-api 的运行时引用), 相关 `strictly` 注释与 `libs.versions.toml` 条目; `coreLibraryDesugaring` 保留 (与 GitHub 段落无关). 预计 -1.75 MiB 归属 DEX.
- [ ] P2.5 `jsoup` 去留评估: P2.3 后 `Jsoup` 只用于 "HTML -> Spanned" (`UpdateChecker` 第 600 行附近); 若 `HtmlCompat.fromHtml` + 现有 `CommonMarkdownView` 能覆盖列表 / 链接 / 代码块的显示, 移除 `libs.jsoup` (-0.23 MiB); 否则记录原因并保留.
- [ ] P2.6 验证: JVM 单测 (解析 / 选包 / 令牌头); 设备冒烟 (附录 B 第 1 项) 覆盖有无令牌, 最新版本与已是最新两种结果, 更新说明多语言回退; 体积报告 `evidence/apk-size-p2-<日期>.md`; changelog `dependency` (移除 GitHub API 1.306 / Jackson Databind 2.13.4.2 / Apache Commons IO 2.8.0 / Apache Commons Lang3 3.18.0 / 视 P2.5 而定的 jsoup).

### P3 kotlin-reflect 移除

- [ ] P3.1 5 个调用点改为 Java 反射, 保持语义: `GlobalClasses` (`Classes::class.declaredMemberProperties` -> `Classes::class.java.declaredFields`, 只列 Kotlin 属性对应的字段, 排除合成字段与 `Companion` / `INSTANCE`), `Ocr` (`OcrMode` 属性名大小写不敏感匹配 -> 字段或 getter 名匹配), `S13n` (`TimeUtils::class.declaredMemberFunctions` -> `declaredMethods` 按名查找并处理 `@JvmStatic` / `@JvmOverloads` 多重载), `Selector` (`Object::class.declaredMembers` -> `Object::class.java.declaredMethods` + `declaredFields`), `WebSocketFields` (`CoreWebSocket.Companion::class.declaredMemberProperties` -> Companion 字段 / getter). 每处先用现有单测或新增 JVM 单测固化 "重构前导出的名字集合" (与 a11y roadmap 的 `SelectorGlobalsTest` 快照模式一致).
- [ ] P3.2 删除 `implementation(kotlin("reflect"))`, 确认依赖树无其它 `kotlin-reflect` 传入 (当前只有版本对齐约束 `(c)`), 新增 `META-INF/*.kotlin_module` 与 `kotlin/**/*.kotlin_builtins` 打包排除 (只被 kotlin-reflect 读取). 预计 -1.44 MiB 归属 DEX.
- [ ] P3.3 验证: `:app:testAppDebugUnitTest` 全量; 设备冒烟脚本调用 `ocr.mode`, `s13n` 时间转换, 选择器全局函数与 `WebSocket` 字段访问 (附录 B 第 2 项); 体积报告; changelog `dependency` (移除 Kotlin Reflect).

### P4 apk-signer 拆分与 SpongyCastle

- [ ] P4.1 模块拆分: 新建 `modules/apk-verifier` (只含 `com.android.apksig.**`, `com.mcal.apksigner.utils.{KeyStoreHelper,JksKeyStore,JKS,LoadKeystoreException}` 与 `Base64` 等必要工具, 不依赖 SpongyCastle 之外的任何第三方库), 宿主改依赖它; `modules/apk-signer` 保留完整内容供 `plugins/apk-builder-template` 使用, 并依赖 `apk-verifier` 以免重复源码. 删除 `src/main/resources/keystore/testkey.*` 在宿主侧的打包 (仅 `ApkSigner.kt` 的调试签名使用, 留在 `apk-signer`).
- [ ] P4.2 BKS 兼容证据: 用 SpongyCastle 生成 BKS v1 与 v2 (当前插件 `CertCreator` 路径) 各一份, 在 API 24 / 28 / 33 / 35 AVD 与一台真机上通过系统 `KeyStore.getInstance("BKS", "BC")` 加载并读出证书; 同时覆盖 `assets/autojs.keystore` 与用户常见的 keytool JKS. 结果写入 `evidence/apk-size-p4-bks-<日期>.md`.
- [ ] P4.3 依 P4.2 的设备证据决定 (Q8 拍板: 等待证据, 不预设结论): 系统 BKS 全部通过则 `apk-verifier` 的 `loadBks` 去掉 SpongyCastle 回退, 宿主不再打包 `org.spongycastle` (-2.28 MiB 归属 DEX, -0.01 MiB `org/spongycastle/x509` 资源); 存在失败机型则保留回退并记录失败样本, 本条目标记为 "保留".
- [ ] P4.4 验证: 远程构建签名解析与输出校验的既有单测 (`apkbuilder/template/*Test`) 通过; `plugins/apk-builder-template` 编译与打包不变; 体积报告; changelog `dependency` (视 P4.3 结果: 移除 Spongy Castle Prov 1.58.0.0, 或 "模块化 APK Verifier").

### P5 DEX 编译器宿主依赖

- [ ] P5.1 dx 合并到 D8: `AndroidClassLoader.dexJar()` (单个 `.class` -> dex, `defineClass` 与兼容路径) 改用与 `loadJarLocally` 相同的 D8 调用 (API 26+ `D8Command.builder`, API 24 / 25 `D8.main` 参数形式, `--min-api` 取运行时 API); `com/stardust/autojs/rhino/AndroidClassLoader.java` 的 dx 调用委托到新实现. 删除 `libs/com-android-dx-1_14.jar` 与 `libs/com-legacy-android-dx-1_7_0.jar`. 验证: 既有 DEX 夹具 / `androidTest` 在 API 24 AVD 与 API 33+ 设备上执行 `defineClass` 路径 (附录 B 第 3 项). 预计 -0.91 MiB 归属 DEX.
- [ ] P5.2 R8 (账目条目, 受 D5 门禁): 记录 `com.android.tools.r8` 6.59 MiB 归属 DEX + `resources/new_api_database.ser` 2.93 MiB, 约 5.5 MiB APK 收益; 前置条件是 DEX 编译器插件通过缓存命中延迟门禁并晋级默认路径, 且 `loadAar` / 动态 `defineClass` / 失败回退有插件侧或宿主侧的非 R8 方案. 门禁通过前本条不动, 每次评审只更新 "前置条件状态".

### P6 功能级候选 (需拍板)

- [ ] P6.1 (Q2 已拍板: 替换, D11) `androidx.media3` (2.18 MiB) 与其带入的 Guava (1.13 MiB) 移除. 现状: `ScriptMusicService` (213 行) 是 `MediaSessionService`, 用到 `ExoPlayer.Builder`, `MediaItem` / `MediaMetadata`, `DefaultMediaNotificationProvider`, `MediaSession.Callback` (`ControllerInfo` / `ConnectionResult` / `SessionResult`), 播放器 API 为 setMediaItem / prepare / play / pause / stop / seekTo / duration / currentPosition / volume / repeatMode (OFF / ONE) / playbackState / isPlaying / addListener / clearMediaItems / release, 清单声明 `androidx.media3.session.MediaSessionService` intent-filter. 步骤: (a) 以 `MediaPlayer` 实现同一套播放器操作 (单曲 / 循环 / 进度 / 音量 / 状态回调), 用 `AudioManager` + `AudioFocusRequest` 处理焦点, 用平台 `MediaSession` + `Notification.MediaStyle` 提供通知控制与媒体按键, 服务仍为 `foregroundServiceType="mediaPlayback"`; (b) 清单去掉 media3 intent-filter; (c) 删除 `libs.media3.*` 与 catalog 条目; (d) 回归: 脚本 `media` 模块公开行为, `evidence/android-sdk-37-media3-20260914.md` 的前台服务 / 通知 / 焦点用例在 API 24 AVD 与 API 33+ 真机重跑; changelog `dependency` (移除 AndroidX Media3 ExoPlayer / Session 1.11.0; 6.8.0 内 "附加" 后又移除, 按 D7 删除 "附加" 条目) 与 `improvement`. 本地文件与 http(s) 直链 (`MediaPlayer.setDataSource`) 为支持范围, DASH / HLS 自适应流不在承诺内.
- [ ] P6.2 (Q3 已拍板: 替换, D12) log4j 1.2.17 (0.23 MiB, 5 个 CVE) 与 `android-logging-log4j`: 现状为 `GlobalConsole` 经 `org.apache.log4j.Logger` 镜像每行日志, `Console.setGlobalLogConfig` (`runtime/api/augment/console/Console.kt:604`) 用 `LogConfigurator` 配置 file / filePattern / maxFileSize / maxBackupSize / immediateFlush / resetConfiguration / rootLevel, `resetGlobalLogConfig` 调 `LogManager.getLoggerRepository().resetConfiguration()`; 文档 `console.md` 记载全部选项且 `filePattern` 指向 log4j PatternLayout. 步骤: (a) 新增自有滚动文件日志器 (按 maxFileSize 分卷, maxBackupSize 轮换, immediateFlush 控制刷盘, rootLevel 过滤), 实现 PatternLayout 子集 `%d{fmt}` / `%p` / `%c` / `%C` / `%m` / `%n` / `%t` / `%r` 与 `%-5p` 一类宽度修饰, 未知转换符原样输出; 默认格式 `%d - [%p::%c::%C] - %m%n` 与默认文件 `android-log4j.log` 不变; (b) `GlobalConsole` 与两个 console API 改接新日志器, 行为用 JVM 单测固化 (格式, 分卷, 轮换, 级别); (c) 删除 `libs.log4j` / `libs.android.logging.log4j`; (d) 脚本全局代理类 `LogConfigurator` / `Level` / `LogManager` (`augment/global/Classes.kt:144, 210, 213`, 文档 `global.md`) 按 Q9 处理; changelog `dependency` (移除 Log4j 1.2.17 / Android Logging Log4j 1.0.3) 与 `improvement`.
- [ ] P6.3 `joda-time`: 文档记载的脚本 API, 保留 (D2); 仅评估 `org/joda/time/tz` 时区表是否可按 `supportedResourceLocales` 之外的策略裁剪 (不建议, 时区与语言无关).
- [ ] P6.4 (Q4 已拍板: 为 Android 7.x 预留, 允许更好方案, D13) `androidx.appcompatlegacy` (0.33 MiB 归属 DEX; `appcompat-legacy.aar` 另带一整套 appcompat 1.0.2 资源: `values.xml` 179 KB 与 `abc_*` 布局, 并入 `res/` 与 `resources.arsc`): `ViewAttributesFactory` / `DynamicLayoutInflater` / `XmlConverter` 在 `SDK_INT < 26` 时把 UI 模式 `text` 元素映射到 `JsTextViewLegacy` (appcompat 1.0.2 的 `AppCompatTextView`), 修复 6.0.2 记录的 issue #4 / #9. 步骤: (a) 在 API 24 与 25 AVD 上把映射临时切到 `JsTextView` (现行 appcompat 1.7.1) 复现异常并记录堆栈; (b) 定位根因 (主题属性缺失 / emoji2 初始化 / TextClassifier 等), 优先在 `DynamicLayoutInflater` 侧用 `ContextThemeWrapper` 或属性补齐修复; (c) 修复经 API 24 / 25 AVD 与一台 Android 7.x 真机 (若可得) 验证后, 删除 `libs/androidx-appcompat-1_0_2`, `JsTextViewLegacy*` 3 个文件, `AppCompatTextViewLegacyAttributes`, `TextViewLegacyInflater`, `Classes.kt:477` 的别名与三处版本分支; (d) 无法修复则保留并把根因写入本条, 不再重复评估. 预计 -0.33 MiB DEX 加资源.
- [ ] P6.5 (Q5 已拍板: 只内置 zh-Hans 与 en, D14) `assets/doc` 0.95 MiB: 现状 `assets-app/doc/` 有 10 个语言文件 (0.23-0.48 MB 各), 与 zh-Hans 同内容的 `CHANGELOG.md` (228,958 字节, 无代码读取) 和 `RHINO.md`; 只有 `ReleaseHistoryRepository.readBestLocalSample` 读取 `doc/CHANGELOG-<lang>.md`, 在线路径与缓存 (`CHANGELOG-<lang>.cache.md`) 已存在. 步骤: (a) `appMergedAssetPruneIncludes` 增加 `doc/CHANGELOG-{ar,es,fr,ja,ko,ru,zh-Hant-HK,zh-Hant-TW}.md` 与 `doc/CHANGELOG.md` (合并期剪除, 生成脚本与仓库文件不变, `RHINO.md` 保留); (b) `readBestLocalSample` 回退链改为 asset(当前语言) -> 缓存(当前语言) -> asset(en), 并加 JVM 单测 (纯函数选择逻辑); (c) 验证 zh-Hant-TW 语言下离线打开版本历史显示 en 样本, 联网后显示对应语言并写入缓存; 体积报告. 预计 -0.7 MiB.
- [ ] P6.6 (Q6 已拍板: 进入, D15) `res/` (2.55 MiB, 其中 PNG 1.86 MiB): (a) `lint` 的 `UnusedResources` 报告 (注意脚本 UI 模式经名字引用的资源与 `R.drawable` 反射访问, 逐项确认后再删); (b) PNG 无损转 WebP (`cwebp -lossless`, 仅对转换后更小的文件生效, 启动图标与 `mipmap` 除外), 记录逐文件体积差; (c) 体积报告与 `improvement` 日志.
- [x] P6.7 (Q1) minify 实验: 取消. 2026-09-17 维护者拍板 release 不启用 minify (D10), 不再做测量实验.
- [x] P6.8 (Q7) `com.stardust` 兼容包 (0.43 MiB): 保留. 2026-09-17 维护者拍板 (D16), 关闭.

### P7 门禁与收尾

- [ ] P7.1 发布检查项: 发版前运行 `apk_size_report.py --compare <上一发布的 JSON>`, 报告存入 `evidence/`, universal APK 相对上一发布增长超过 5% 时在 PR / 提交说明中给出原因. 与 `page-size-16kb-maintainer-checklist.md` 并列.
- [ ] P7.2 关联项目同步: 依赖移除后按 `AGENTS.md` 执行 d.ts 发布 (`aj6dts.bat -Publish`), 使 `libraries.d.ts` 不再声明已移除的库; 文档项目无需改动 (D2).
- [ ] P7.3 Roadmap 收尾: 汇总各阶段体积报告为 `evidence/apk-size-summary-<日期>.md` (基线 -> 各阶段 -> 最终), 回填第 3 节 "理论收益" 的实际值.

---

## 附录 A. 待决事项

| 编号 | 问题 | 建议 | 拍板 (2026-09-17) |
| --- | --- | --- | --- |
| Q1 | 是否在 release 启用 minify (R8 收缩 / 优化, 不混淆)? | 默认不启用 (D3). | 不启用 (D10), P6.7 取消. |
| Q2 | media3 是否替换为平台播放器? | 建议替换 (约 1.3 MiB, 最大的功能级项). | 替换 (D11). |
| Q3 | log4j 是否自研替换? | 建议替换 (体积小但 CVE 清零), 保留 `console.setGlobalLogConfig` 语义. | 替换 (D12); 派生 Q9. |
| Q4 | `JsTextViewLegacy` 是否仍需要旧版 appcompat? | 待盘点后决定. | 为 Android 7.x 专门预留; 有更好方案可实施 (D13, P6.4 的复现 / 修复 / 验证流程). |
| Q5 | 内置 CHANGELOG 的语言范围? | 只内置 zh-Hans 与 en, 其余在线. | 按建议实施 (D14). |
| Q6 | PNG -> WebP 与未用资源清理是否进入本 Roadmap? | 建议进入, 仅无损转换与 lint 报告驱动的删除. | 进入 (D15). |
| Q7 | `com.stardust` 兼容包去留? | 建议保留. | 保留 (D16). |
| Q8 | 系统 `BKS` 是否足以替代 SpongyCastle 回退? | 由 P4.2 的设备证据决定. | 等待设备证据. |
| Q9 | log4j 替换后, 文档 `global.md` 列出的脚本全局代理类 `LogConfigurator` (`de.mindpipe...LogConfigurator`), `Level`, `LogManager` (`org.apache.log4j.*`) 如何处理? | 建议 (a): 用自有实现提供同名代理类, 覆盖文档化用法 (`LogConfigurator` 的 fileName / filePattern / maxFileSize / maxBackupSize / rootLevel / configure(); `Level` 常量; `LogManager.getLogger(name)` 与 `getRootLogger()` 的 debug / info / warn / error / fatal), 其余 log4j API 不再提供, 文档与 d.ts 同步; 备选 (b): 从 `Classes` 与 `global.md` 移除三个代理并在 changelog 标注. | 待拍板. |

## 附录 B. 冒烟清单

1. (P2) 设置页 "检查更新": 无令牌与有令牌各一次; 当前版本为最新与存在新版本各一次 (可临时把本地 `VERSION_NAME` 降低); 更新说明按 UI 语言显示, 缺失语言回退到英文; 点击下载走 ABI 选包.
2. (P3) 脚本: `ocr.mode = "paddle"` 等大小写变体, `s13n` 的时间转换函数名, 选择器全局函数 `text("x").exists()`, `web.newWebSocket` 后访问 `WebSocket` 静态字段; 与 P3.1 固化的名字集合对照.
3. (P5.1) 脚本: `runtime.loadJar` 已有夹具, 再加 `new JavaAdapter` / `defineClass` 路径 (例如实现 Java 接口的匿名类) 在 API 24 AVD 与 API 33+ 设备上各跑一次.
4. (P1) 打开 "关于" -> 版本历史 (在线与离线各一次), 编辑器 Markdown 预览 (commonmark), 资源管理器名称排序 (ASCII / 非 ASCII 混合), 运行一段使用 `importClass(Packages.org.mozilla.javascript.tools.shell.ShellContextFactory)` 之外的普通脚本确认 Rhino 未受 JLine 裁剪影响.

## 附录 C. 证据等级

| 等级 | 含义 |
| --- | --- |
| E0 | 代码已提交, 编译通过 |
| E1 | E0 + JVM 单测通过 + 体积报告落盘 |
| E2 | E1 + AVD 冒烟通过 (记录 API level) |
| E3 | E2 + 真机冒烟通过 (记录机型) |

## 附录 D. 文件地图

| 关注点 | 文件 |
| --- | --- |
| 度量 | `tools/size/apk_size_report.py`, `docs/dev/evidence/apk-size-*.{md,json}` |
| 依赖声明 | `app/build.gradle.kts` (`dependencies` 各段, `resourceExcludePatterns`, `packaging`), `gradle/libs.versions.toml`, `libs/*.jar`, `libs/javamail-android/`, `modules/apk-signer/build.gradle.kts` |
| P1 代码 | `util/TextUtils.kt`, `ui/settings/ReleaseHistoryRepository.kt`, `model/explorer/ExplorerSorter.java` |
| P2 代码 | `network/UpdateChecker.java` (`GitHubRepoUtils`), `network/api/*`, `ui/widget/CommonMarkdownView.kt` |
| P3 代码 | `runtime/api/augment/global/GlobalClasses.kt`, `ocr/Ocr.kt`, `s13n/S13n.kt`, `selector/Selector.kt`, `web/WebSocketFields.kt` |
| P4 代码 | `modules/apk-signer/src/main/java/com/mcal/apksigner/utils/KeyStoreHelper.kt`, `JksKeyStore.kt`, `JKS.java`, `apkbuilder/template/RemoteBuildExpectedSignerResolver.kt`, `RemoteBuildApkValidator.kt`, `plugins/apk-builder-template/` |
| P5 代码 | `rhino/AndroidClassLoader.kt` (`dexJar`, `loadJarLocally*`), `com/stardust/autojs/rhino/AndroidClassLoader.java`, `docs/dev/dex-compiler-plugin-evaluation.md` |
| P6 代码 | `core/media/ScriptMusicService.kt`, `core/console/GlobalConsole.kt`, `ui/widget/JsTextViewLegacy*`, `app/src/main/assets-app/doc/`, `app/src/main/res/` |
