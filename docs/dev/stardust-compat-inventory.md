# `com.stardust` 兼容包盘点 (Roadmap P7.1)

日期: 2026-10-12. 对象: 宿主 `app/src/main/java/com/stardust/**` (354 个文件, Java 282, Kotlin 72; P7.2 实施后分布在 `compat/stardust/src/main/java` 与 `app/src/main/java` 两处, 见第 8 节). 盘点脚本按 import 与同包简单类名引用计算依赖闭包, 结论用于决定 `autojs4-runtime-plugin-roadmap.md` 第 7 阶段 (D18) 的做法. 数字为脚本结果, 同包引用按类名匹配, 可能略高估 "受污染" 集合, 不会低估.

## 1. 结论

- 宿主代码 (`org.autojs.**`) 没有任何 `import com.stardust.*`, 仅 3 处注释提到来源. 兼容包是只被脚本 (Rhino LiveConnect) 使用的叶子.
- 兼容包反向依赖宿主: 25 个文件直接 `import org.autojs.*` (下称 "桥接文件"), 另有 99 个文件直接或间接引用桥接文件 (下称 "受污染文件"). 这 124 个文件不能移出 `:app`, 否则形成 `:app` 与新模块的循环依赖.
- 其余 230 个文件只依赖 Android / AndroidX / Rhino / OpenCV / 第三方库以及彼此, 可以整体迁入独立库模块 (方案 1 的可行范围).
- 因此 "独立 Gradle 库模块" 的准确形态是: `:compat:stardust` 承载独立文件; 桥接与受污染文件留在 `:app` 的 `com.stardust` 包内 (包名不变), 它们编译时依赖新模块. 两部分都不改类名与包名, 脚本可见行为不变. (本节 25 / 99 / 230 是 P7.1 的静态估计; P7.2 以编译结果校正为 30 / 145 / 179, 见第 8 节.)

## 2. 桥接文件 (25) 及其宿主依赖

| 文件 | 宿主依赖 |
| --- | --- |
| `com/stardust/Redirect.kt` | `BuildConfig`, `R` |
| `com/stardust/autojs/ScriptEngineService.java` | `ViewUtils`, `R` |
| `com/stardust/autojs/core/console/ConsoleFloaty.java` | `FloatyService`, `ResizableExpandableFloaty`, `ResizableExpandableFloatyWindow`, `R` |
| `com/stardust/autojs/core/console/ConsoleImpl.java` | `FloatyService`, `ResizableExpandableFloatyWindow`, `R` |
| `com/stardust/autojs/core/console/ConsoleView.java` | `ResizableExpandableFloatyWindow`, `R` |
| `com/stardust/autojs/core/console/GlobalConsole.java` | `core.console.log.Level`, `core.console.log.Logger` |
| `com/stardust/autojs/core/floaty/BaseResizableFloatyWindow.java` | `FloatyService`, `ResizableFloaty`, `ResizableFloatyWindow`, `WindowBridge`, `R` |
| `com/stardust/autojs/core/floaty/RawWindow.java` | `FloatyService`, `FloatyWindow`, `WindowBridge`, `WindowTypeCompat`, `R` |
| `com/stardust/autojs/core/ui/ViewExtras.java` | `R` |
| `com/stardust/autojs/core/ui/inflater/inflaters/{AppBar,DatePicker,TabLayout,TimePicker,Toolbar}Inflater.java` | `R` |
| `com/stardust/autojs/core/ui/widget/JsToolbar.java` | `R` |
| `com/stardust/autojs/rhino/AndroidClassLoader.java` | `rhino.ClassFileDexer` |
| `com/stardust/autojs/rhino/debug/Debugger.java` | `engine.ScriptEngineService` |
| `com/stardust/autojs/runtime/ScriptRuntime.java` | `R` |
| `com/stardust/autojs/runtime/api/{Device,Dialogs,Events,Floaty}.java` | `R` (`Floaty` 另依赖 `FloatyService`) |
| `com/stardust/autojs/util/FloatingPermission.java` | `FloatingWindowPermissionUtil`, `R` |
| `com/stardust/util/IntentUtil.java` | `R` |
| `com/stardust/util/ResourceMonitor.java` | `BuildConfig` |

宿主依赖分三类:

1. 资源 `org.autojs.autojs6.R` (21 个文件, 43 个资源名): 字符串 `text_ok`, `error_no_display_over_other_apps_permission`, `text_start_running`, `text_already_stop_n_scripts` 等 12 个; 布局 `console_view_legacy`, `console_view_item`, `floaty_window`, `floating_console_expand`, `floating_window_collapse`, `raw_window`, `js_toolbar`, `js_appbar`, `js_tablayout`, `date_picker_spinner`, `time_picker_spinner`, `simple_spinner_dropdown_item`; id `close`, `resizer`, `move_cursor`, `view_tag_view_extras`, `title`, `submit`, `move_or_resize`, `minimize`, `log_list`, `input_container`, `input`, `container`, `console`; 样式 `ScriptTheme`, `ConsoleTheme` 与 3 个 AppCompat 主题; `dimen/floaty_window_offset`; `plurals/text_already_stop_n_scripts`. 这些资源同时被宿主自身使用, 不能单方面移走.
2. 宿主浮动窗口实现 `org.autojs.autojs.ui.enhancedfloaty.**` (11 处): 该包自身又依赖 `R`, `util.IntentUtils`, `ui.widget.ViewSwitcher`, `annotation.ReservedForCompatibility`, 属于宿主 UI 层.
3. 宿主引擎与工具: `engine.ScriptEngineService`, `rhino.ClassFileDexer`, `core.console.log.{Logger,Level}`, `util.ViewUtils`, `BuildConfig`.

## 3. 受污染文件 (99)

按包统计: `autojs.runtime.api` 11, `autojs.core.ui.inflater.inflaters` 13, `autojs.engine` 8, `autojs.execution` 8, `autojs.core.ui.attribute` 7, `autojs.core.record.inputevent` 5, `autojs.core.inputevent` 4, `autojs.core.looper` 4, 其余 (`core.accessibility`, `core.image`, `core.opencv`, `core.ui.dialog`, `core.ui.inflater`, `core.ui.nativeview`, `core.ui.widget`, `rhino`, `rhino.continuation`, `rhino.debug`, `core.boardcast`, `core.graphics`, `core.util`, `autojs`, `project`, `util` 等) 1-3 个. 它们是 Auto.js 运行时的核心 (引擎, 执行, 运行时 API, UI 膨胀), 依赖桥接文件中的 `ScriptRuntime`, `ScriptEngineService`, `ConsoleImpl`, `ViewExtras` 等.

## 4. 独立文件 (230, 可迁入 `:compat:stardust`)

按包统计 (节选): `com.stardust.util` 37, `automator.filter` 16, `view.accessibility` 12, `autojs.core.database` 10, `autojs.core.ui.widget` 10, `automator.simple_action` 10, `autojs.rhino` 9, `com.stardust.app` 8, `autojs.core.ui.inflater.util` 8, `autojs.script` 8, `com.stardust.pio` 8, `automator` 6, `autojs.core.permission` 5, `autojs.core.ui.inflater` 5, `com.stardust.io` 5, `autojs.project` 4, `com.stardust.concurrent` 4, 其余 1-3 个.

外部依赖 (import 计数): `android.*` / `androidx.*` 为主, `org.mozilla.javascript` 48, `org.opencv.*`, `org.w3c.dom`, `com.afollestad.materialdialogs`, `com.google.android.material`, `jackpal.androidterm`, `com.makeramen.roundedimageview`, `org.greenrobot.eventbus`, `ezy.assist`. 新模块需要声明这些依赖的子集 (与 `app/build.gradle.kts` 相同版本, 优先经 `libs.versions.toml`).

## 5. P7.2 执行步骤 (方案 1, 真实模块)

1. 新建 `compat/stardust` (`com.android.library`, namespace `com.stardust.compat`, 无资源), `settings.gradle.kts` 以 `include(":compat:stardust")` 注册, `app` 以 `implementation(project(":compat:stardust"))` 依赖.
2. 用脚本按第 4 节清单 `git mv` 230 个文件到 `compat/stardust/src/main/java/com/stardust/**`, 桥接与受污染文件保留在 `app/src/main/java/com/stardust/**`.
3. 为新模块声明第 4 节的依赖; 以 `:compat:stardust:assembleRelease` 定位脚本未捕获的同包引用 (编译错误即误判为独立的文件), 把这些文件移回 `app`, 直到模块独立编译通过.
4. `app/lint.xml` 的忽略路径与 `proguard-rules.pro` 的 `-keep class com.stardust.**` 保持 (混淆规则对模块类同样生效); 新模块 `lint { abortOnError = false }`, `consumerProguardFiles` 中重复 keep 规则.
5. 验收: 宿主全量构建, 单元测试, 以及设备上 `importClass(com.stardust.autojs.core.util.Shell)` / `com.stardust.pio.PFiles.read(...)` / `com.stardust.util.ScreenMetrics` 等直接调用的冒烟脚本结果不变 (这些类分属两部分).
6. 文案: 内部重构, 不记 changelog; `AGENTS.md` 与各 Roadmap 中 "`app/src/main/java/com/stardust/**` 冻结" 的表述改为同时覆盖 `compat/stardust/`.

## 6. 方案 2 (别名表) 的前提

方案 2 只对 "宿主存在 1:1 对应类且签名兼容" 的类有意义. 第 3 节的 99 个受污染文件多数在宿主有同名对应 (`org.autojs.autojs.engine.*`, `execution.*`, `runtime.api.*`), 但构造器与方法签名随 AutoJs6 演进已不同, 别名映射会把脚本的旧调用导向签名不同的新类, 需要逐类对照. 建议在 P7.2 完成后, 从脚本实际调用过的名称 (issue #290 及后续反馈) 出发小范围试点, 不整体替换.

## 7. 复现盘点 (P7.1 的一次性脚本)

```powershell
py E:/.cache/user-temp/claude/.../scratchpad/stardust_inventory.py   # 本次使用的一次性脚本
```

脚本逻辑: 遍历 `app/src/main/java/com/stardust/**/*.{java,kt}`, 读取 `import`; 桥接 = 含 `import org.autojs.`; 受污染 = 经 `com.stardust.*` import 或同包类名引用可达桥接文件 (迭代至不动点); 其余为独立. 正式实施 P7.2 时把脚本收入 `tools/` 并以编译结果校正.

## 8. P7.2 实施结果 (2026-10-12)

- 模块: `compat/stardust` (`com.android.library`, namespace `com.stardust.compat`, 无资源, `lint.xml` 全部忽略, `consumer-rules.pro` 复制 `app/proguard-rules.pro` 的 `com.stardust` keep 规则), `settings.gradle.kts` 以 `compat` 列表注册 `:compat:stardust`, `:app` 以 `implementation(project(":compat:stardust"))` 依赖. 依赖只声明独立文件实际 import 的库: Rhino (`compileOnly`, 运行时由宿主的精简 jar 提供), `annotation`, `core-ktx`, `appcompat`, `recyclerview`, `material`, `gson`, `okhttp`, `eventbus`.
- 分类脚本收入 `tools/stardust/split_stardust_compat.py` (报告模式检查两处文件是否放对, `--apply` 用 `git mv` 纠正), 比 P7.1 脚本多识别三类宿主依赖: 代码中的内联 `org.autojs.` 引用 (`typealias BuildConfig = org.autojs.autojs6.BuildConfig` 两处, `TokenStream extends org.autojs.autojs.rhino.TokenStream`, `Dim`, `OpenCVHelper`), 宿主本地非 `org.autojs` 包 (`ezy.assist.compat`), 以及字符串字面量的排除 (`"org.autojs.autojs4"` 等不算引用).
- 校正后的分布: 桥接 30, 受污染 145 (两个 `BuildConfig` typealias 把 `com.stardust.automator` 整包与 `view.accessibility` 的 4 个 Kotlin 文件拖回 `app`), 独立 179 (Java 153, Kotlin 26) 迁入 `compat/stardust`; `app/src/main/java/com/stardust` 余 175 个文件. `:compat:stardust:assembleDebug` 独立编译通过 (仅 deprecation 提示), `:app:assembleAppDebug` 通过; `:app:testAppDebugUnitTest` 4262 个用例中仅 `SelectorGlobalsTest.exportedNamesMatchTheSnapshot` 失败 (选择器新增 minDepth / maxDepth / minIndexInParent / maxIndexInParent 全局函数后 `selector-global-functions.txt` 快照未更新, 属维护者选择器工作, 与本次拆分无关).
- 设备: XQ-DQ72 (Android 13) 上 `stardust_smoke.js` (直接调用 `com.stardust.pio.PFiles`, `util.ScreenMetrics`, `util.ClipboardUtil`, `util.UiHandler`, `autojs.annotation.ScriptInterface`, `util.Func1`, `util.MapBuilder` 等模块侧类, 以及 `autojs.core.util.Shell`, `automator.filter.IdFilter`, `automator.UiGlobalSelector`, `autojs.core.opencv.OpenCVHelper`, `autojs.AutoJs` 等留在 app 的类) 在拆分前 (5350) 与拆分后 (同版本号的本地构建) 的控制台输出逐行一致, 仅用时不同 (0.032 s 与 0.036 s).
- 后续: 第 5 节步骤 6 的文案已同步 (`accessibility-architecture.md`, `accessibility-automation-roadmap.md`, `android-sdk-37-roadmap.md`, `page-size-16kb-roadmap.md`, `autojs4-runtime-plugin-roadmap.md` D1); `app/lint.xml` 的忽略路径与 `proguard-rules.pro` 保持不变. 方案 2 (别名表) 仍按第 6 节的前提另行评估.

## 9. 方案 2 (别名表) 候选清单 (2026-10-12, 静态分析)

工具: `tools/stardust/split_stardust_compat.py` 同目录下的一次性脚本 (按 `com.stardust.<path>` -> `org.autojs.autojs.<path>` 的类比路径或同名类匹配宿主类, 并比较 public 方法名集合). 数字只反映方法名重合, 不反映参数与返回类型, 因此是别名表的 "上限", 不是可直接启用的清单.

- 354 个 `com.stardust` 类中 305 个在宿主有同名类, 其中 241 个位于类比路径. 方法名完全被宿主覆盖的 173 个, 覆盖率 >= 80% 的 23 个, 覆盖率不足 80% 的 70 个, 另有 39 个没有 public 方法 (接口, 注解, 数据类).
- 宿主侧已由 `:compat:stardust` 承载的 179 个独立文件不需要别名 (类本身仍在), 别名只对留在 `app` 的 175 个桥接 / 受污染文件有意义, 且只有脚本会直接引用的类值得做.
- 脚本可能直接引用的候选 (runtime.api, core.util, pio, automator, core.image, core.accessibility, util; 列出方法名覆盖率 >= 80% 的):

| `com.stardust` 类 | 宿主类 | 重合 / 兼容包方法数 / 宿主方法数 | 当前位置 |
| --- | --- | --- | --- |
| `com.stardust.pio.PFiles` | `org.autojs.autojs.pio.PFiles` | 38 / 41 / 45 | compat |
| `com.stardust.autojs.core.accessibility.SimpleActionAutomator` | `org.autojs.autojs.core.accessibility.SimpleActionAutomator` | 31 / 31 / 49 | app |
| `com.stardust.autojs.runtime.api.AbstractShell` | `org.autojs.autojs.runtime.api.AbstractShell` | 31 / 32 / 35 | app |
| `com.stardust.autojs.runtime.api.Files` | `org.autojs.autojs.runtime.api.Files` | 31 / 32 / 33 | app |
| `com.stardust.autojs.runtime.api.Device` | `org.autojs.autojs.runtime.api.Device` | 30 / 30 / 61 | app |
| `com.stardust.util.BiMaps` | `org.autojs.autojs.core.ui.BiMaps` | 30 / 30 / 30 | compat |
| `com.stardust.autojs.runtime.api.Images` | `org.autojs.autojs.runtime.api.Images` | 21 / 21 / 41 | app |
| `com.stardust.autojs.runtime.api.Events` | `org.autojs.autojs.runtime.api.Events` | 21 / 22 / 23 | app |
| `com.stardust.automator.GlobalActionAutomator` | `org.autojs.autojs.core.automator.GlobalActionAutomator` | 18 / 18 / 27 | compat |
| `com.stardust.autojs.core.util.Shell` | `org.autojs.autojs.runtime.api.Shell` | 14 / 14 / 14 | app |
| `com.stardust.util.IntentExtras` | `org.autojs.autojs.tool.IntentExtras` | 11 / 11 / 11 | compat |
| `com.stardust.autojs.core.image.Colors` | `org.autojs.autojs.core.image.Colors` | 9 / 9 / 9 | compat |
| `com.stardust.autojs.runtime.api.AbstractConsole` | `org.autojs.autojs.runtime.api.AbstractConsole` | 9 / 10 / 9 | compat |
| `com.stardust.autojs.runtime.api.Threads` | `org.autojs.autojs.runtime.api.Threads` | 9 / 10 / 16 | app |
| `com.stardust.autojs.runtime.api.Timers` | `org.autojs.autojs.runtime.api.Timers` | 9 / 11 / 11 | app |
| `com.stardust.autojs.runtime.api.Dialogs` | `org.autojs.autojs.runtime.api.Dialogs` | 8 / 8 / 8 | app |
| `com.stardust.autojs.runtime.api.Engines` | `org.autojs.autojs.runtime.api.Engines` | 8 / 8 / 10 | app |
| `com.stardust.pio.PFile` | `org.autojs.autojs.pio.PFile` | 8 / 8 / 10 | compat |
| `com.stardust.util.DeveloperUtils` | `org.autojs.autojs.util.DeveloperUtils` | 8 / 9 / 8 | compat |
| `com.stardust.automator.simple_action.ActionFactory` | `org.autojs.autojs.core.automator.action.ActionFactory` | 7 / 7 / 7 | app |
| `com.stardust.pio.PWritableTextFile` | `org.autojs.autojs.pio.PWritableTextFile` | 7 / 7 / 7 | compat |
| `com.stardust.autojs.core.util.ScriptPromiseAdapter` | `org.autojs.autojs.runtime.api.ScriptPromiseAdapter` | 5 / 5 / 5 | compat |
| `com.stardust.pio.PReadableTextFile` | `org.autojs.autojs.pio.PReadableTextFile` | 5 / 5 / 5 | compat |
| `com.stardust.util.BackPressedHandler` | `org.autojs.autojs.event.BackPressedHandler` | 5 / 5 / 5 | compat |
| `com.stardust.autojs.core.image.ColorFinder` | `org.autojs.autojs.core.image.ColorFinder` | 4 / 4 / 14 | app |
| `com.stardust.automator.simple_action.FilterAction` | `org.autojs.autojs.core.automator.action.FilterAction` | 4 / 4 / 4 | app |
| `com.stardust.util.ArrayUtils` | `org.autojs.autojs.util.ArrayUtils` | 4 / 4 / 4 | compat |
| `com.stardust.util.SimpleCache` | `org.autojs.autojs.tool.SimpleCache` | 4 / 4 / 4 | compat |
| `com.stardust.automator.filter.BooleanFilter` | `org.autojs.autojs.core.automator.filter.BooleanFilter` | 3 / 3 / 3 | app |
| `com.stardust.automator.filter.Selector` | `org.autojs.autojs.core.automator.filter.Selector` | 3 / 3 / 4 | app |
| `com.stardust.automator.simple_action.SearchTargetAction` | `org.autojs.autojs.core.automator.action.SearchTargetAction` | 3 / 3 / 3 | app |
| `com.stardust.util.MapBuilder` | `org.autojs.autojs.tool.MapBuilder` | 3 / 3 / 3 | compat |
| `com.stardust.util.StateObserver` | `org.autojs.autojs.tool.StateObserver` | 3 / 3 / 3 | compat |
| `com.stardust.autojs.core.image.TemplateMatching` | `org.autojs.autojs.core.image.TemplateMatching` | 2 / 2 / 13 | app |
| `com.stardust.autojs.runtime.api.Plugins` | `org.autojs.autojs.runtime.api.Plugins` | 2 / 2 / 10 | app |
| `com.stardust.automator.filter.BoundsFilter` | `org.autojs.autojs.core.automator.filter.BoundsFilter` | 2 / 2 / 5 | app |
| `com.stardust.automator.simple_action.SearchUpTargetAction` | `org.autojs.autojs.core.automator.action.SearchUpTargetAction` | 2 / 2 / 2 | app |
| `com.stardust.util.AdvancedEncryptionStandard` | `org.autojs.autojs.engine.encryption.AdvancedEncryptionStandard` | 2 / 2 / 2 | compat |
| `com.stardust.util.MD5` | `org.autojs.autojs.inrt.launch.MD5` | 2 / 2 / 2 | compat |
| `com.stardust.util.Objects` | `org.autojs.autojs.util.Objects` | 2 / 2 / 2 | compat |
| `com.stardust.util.SparseArrayEntries` | `org.autojs.autojs.tool.SparseArrayEntries` | 2 / 2 / 2 | compat |
| `com.stardust.autojs.core.accessibility.AccessibilityServiceUsher` | `org.autojs.autojs.core.accessibility.AccessibilityServiceUsher` | 1 / 1 / 2 | compat |
| `com.stardust.automator.ActionArgument` | `org.autojs.autojs.core.automator.ActionArgument` | 1 / 1 / 1 | compat |
| `com.stardust.automator.filter.Filter` | `org.autojs.autojs.core.automator.filter.Filter` | 1 / 1 / 1 | app |
| `com.stardust.automator.simple_action.Able` | `org.autojs.autojs.core.automator.action.Able` | 1 / 1 / 1 | app |
| `com.stardust.automator.simple_action.ActionTarget` | `org.autojs.autojs.core.automator.action.ActionTarget` | 1 / 1 / 1 | app |
| `com.stardust.automator.simple_action.DepthFirstSearchTargetAction` | `org.autojs.autojs.core.automator.action.DepthFirstSearchTargetAction` | 1 / 1 / 1 | app |
| `com.stardust.automator.simple_action.ScrollAction` | `org.autojs.autojs.core.automator.action.ScrollAction` | 1 / 1 / 1 | app |
| `com.stardust.automator.simple_action.ScrollMaxAction` | `org.autojs.autojs.core.automator.action.ScrollMaxAction` | 1 / 1 / 1 | app |
| `com.stardust.pio.PRandomAccessBinaryFile` | `org.autojs.autojs.pio.PRandomAccessBinaryFile` | 1 / 1 / 1 | compat |
| `com.stardust.pio.PReadableBinaryFile` | `org.autojs.autojs.pio.PReadableBinaryFile` | 1 / 1 / 1 | compat |
| `com.stardust.pio.UncheckedIOException` | `org.autojs.autojs.pio.UncheckedIOException` | 1 / 1 / 1 | compat |
| `com.stardust.util.AssetsCache` | `org.autojs.autojs.util.AssetsCache` | 1 / 1 / 1 | compat |
| `com.stardust.util.DrawerAutoClose` | `org.autojs.autojs.ui.widget.DrawerAutoClose` | 1 / 1 / 1 | compat |
| `com.stardust.util.LimitedHashMap` | `org.autojs.autojs.tool.LimitedHashMap` | 1 / 1 / 1 | compat |
| `com.stardust.util.NetworkUtils` | `org.autojs.autojs.util.NetworkUtils` | 1 / 1 / 10 | compat |

- 覆盖率低且核心的类 (不适合别名, 需保留兼容副本): `UiObject` (42/69, 宿主 107), `UiSelector` (9/41), `UiObjectCollection` (9/36), `ScriptRuntime` (25/39), `ConsoleImpl` (13/17 对 91), `ProjectConfig` (25/30 对 65), `DynamicLayoutInflater` (9/22) 等.
- 建议: 若实施方案 2, 第一批只做 `runtime.api.*` (Files, Device, Images, Events, Threads, Timers, Dialogs, Engines, AbstractShell, AbstractConsole), `core.util.Shell`, `pio.PFiles` / `PFile` / `PWritableTextFile` / `PReadableTextFile` 与 `core.image.Colors` 这 ~18 个类, 每条别名附编译期签名对照测试 (参数与返回类型, 不只是方法名); `automator.*` 与 UI 膨胀相关类不做. (维护者 2026-10-12 确认按此实施, 结果见第 10 节.)

## 10. 方案 2 (别名表) 第一批实施 (2026-10-12)

### 10.1 机制

- 别名表 `app/src/main/java/org/autojs/autojs/rhino/StardustClassAliases.kt` (`TABLE`: `com.stardust` 类名 -> 宿主类名, 嵌套类随外部类映射), 由 `AndroidClassLoader.loadClass` 在查找自身 DEX 与父加载器之前查询. `AndroidClassLoader` 是 Rhino 的 application class loader, `Packages.com.stardust.*`, 顶层 `com.stardust.*`, `importClass` 与 `importPackage` 的类名查找都经它完成, 因此脚本写 `com.stardust.pio.PFile` 时拿到的是 `org.autojs.autojs.pio.PFile` 的 `NativeJavaClass`, `instanceof`, `new`, 静态调用与嵌套类访问都落在宿主类上.
- 别名对 Java 层不可见: `AndroidClassLoader.loadClass` 只在调用栈显示查找由 Rhino 自身发起 (`org.mozilla.javascript.*` 帧, 即 `Kit.classOrNull`) 时才应用别名. VM 链接类 (Rhino 生成的 JavaAdapter 类, 以 `AndroidClassLoader` 为父加载器) 时上方没有 Java 帧, 脚本调用 `java.lang.Class.forName("com.stardust...")` 时调用方是 `java.lang.Class`, 两者都拿到所请求的副本; 这是必需的, 因为 ART 拒绝加载器返回名字不同的类 (`Initiating class loader ... returned class ... instead of ...`). XQ-AT72 (宿主 debug 5351) 实测: `Packages.com.stardust.pio.PFiles`, `importClass`, `importPackage`, `new com.stardust.pio.PFile(...)`, `new com.stardust.autojs.runtime.api.Files(runtime)` 与 `Kit.classOrNull(applicationClassLoader, ...)` 均得到宿主类, `runtime.files` / `runtime.dialogs` / `runtime.timers` / `runtime.events` 对相应 `com.stardust` 名字的 `instanceof` 为 true; 而 `java.lang.Class.forName(name, true, applicationClassLoader)` 与 `applicationClassLoader.loadClass(name)` 仍返回 `com.stardust.pio.PFiles` 副本; 表外的 `Device` / `ScreenMetrics` 名字不受影响. 第二批 (AbstractShell, Shell, Images, Colors) 同机复测: `new com.stardust.autojs.core.util.Shell(context, false, true).isRoot()` 可用, `com.stardust.autojs.core.image.Colors.RED` 等常量与 `runtime.colors instanceof` 正确, `com.stardust.autojs.runtime.api.Images.concat(img, img, Gravity.RIGHT / BOTTOM)` 对 8x8 图得到 16x8 / 8x16. 注意 `Shell` 构造后立刻 `exit()` 会因终端会话尚未在主线程初始化而 NPE, 宿主与兼容副本的 `exit()` 实现相同, 与别名无关. 另注意脚本中单参数的 `java.lang.Class.forName(name)` 对任何应用类 (宿主类与兼容类都一样) 都报 `ClassNotFoundException`, 因为经反射调用时 ART 取到的调用方加载器是 boot loader, 与别名无关. `runtime.loadDex` / `loadJar` 创建的 `DexClassLoader` 以应用类加载器 (而非 `AndroidClassLoader`) 为父加载器, 用户 DEX 按 JVM 类名链接到的本来就是兼容副本.
- 不能挂在类加载器以外的层 (如替换 `NativeJavaPackage`) 的原因: Rhino 2.0.0 的 `NativeJavaPackage.getPkgProperty` 与 `forcePackage` 为包私有, `importPackage` 直接调用它们, 子类无法拦截; 而 `ClassLoader` 层对所有入口一致.
- 副本保留: 兼容包自身的引擎栈 (`com.stardust.autojs.runtime.ScriptRuntime` 构造 `Files` / `Device` / `Images` / `Events` / `Threads` / `Timers` / `Dialogs` / `Engines`, `ConsoleImpl` 继承 `AbstractConsole`, `PFiles.open` 返回 `PWritableTextFile` / `PReadableTextFile`) 仍以类型引用这些类, 删除副本就要把兼容 `ScriptRuntime` 等核心类改写为宿主类型, 等同于方案 3 的范围; 加上用户 DEX 的 JVM 链接, 本批不删除任何副本, 别名只改变脚本按名字解析到的类. roadmap P7.3 "删除 (a) 类的副本" 因此留待方案 3 由维护者另行决定.

### 10.2 启用规则 (工具与测试一致)

别名只改变名字解析到的类, 脚本已持有的对象 (`files`, `dialogs` 等) 不受影响, 因此只有脚本能 "通过类名" 触及的成员才可能回退:

- 阻止启用: 兼容类的 public 静态方法 / 静态字段在宿主类上缺失, 参数不兼容或不是静态 (含只在 Kotlin companion 上); 脚本可调用的 public 构造器 (参数不含 `com.stardust.autojs.*` 引擎类型) 在宿主类上缺失; 可构造或可继承 (抽象类 / 接口) 的类的实例成员缺失; 嵌套 public 类缺失.
- 视为兼容 (对 Rhino 脚本无差别): 数值类型互换 (`int` / `long` / `double` / 装箱类型), `Object` 对 Rhino 值类型 (`Callable`, `Scriptable`, `NativeObject` 等), `String` 对 `CharSequence`, 宿主参数为 `Object`; 返回类型差异一律不检查.
- 仅记录: 需要引擎内部类型的构造器 (脚本从未能调用), 纯静态工具类缺少的无参构造器, 不可构造类的实例成员差异.

工具 `tools/stardust/split_stardust_compat.py` 同目录的 `compare_alias_signatures.py` 用 `javap -public` 读取 `:app` 与 `:compat:stardust` 的编译产物按上述规则逐类报告 (默认核对别名表, `--all` 普查全部类比路径候选, `--details` 列出每个成员); 单元测试 `app/src/test/java/org/autojs/autojs/rhino/StardustClassAliasesTest.kt` 对已启用的每一项用反射重做同样的核对 (另校验简单名一致, 两侧可加载, 表与工具的 `ALIASES` 一致, 以及 `AndroidClassLoader` 对别名返回宿主类而对表外类名返回副本), 宿主日后删掉脚本可触及的成员时测试失败而不是脚本静默报错. 2026-10-12 `:app:testAppDebugUnitTest --tests "*StardustClassAliasesTest"` 5 个用例通过; 工具的 `ALIASES` 保留全部 16 个候选 (含 5 个保留副本的类), 以便宿主补齐缺口后报告直接显示为 "alias".

### 10.3 核对结果与启用清单

| `com.stardust` 类 | 宿主类 | 成员 | 一致 | 宽松兼容 | 仅记录 | 阻止 | 结论 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `autojs.runtime.api.AbstractConsole` | `runtime.api.AbstractConsole` | 22 | 22 | 0 | 0 | 0 | 启用 |
| `autojs.runtime.api.Dialogs` (+ `NonUiDialogs`) | `runtime.api.Dialogs` | 17 | 3 | 13 (`Object` -> `Callable`) | 1 | 0 | 启用 |
| `autojs.runtime.api.Engines` | `runtime.api.Engines` | 9 | 6 | 0 | 3 | 0 | 启用 |
| `autojs.runtime.api.Events` | `runtime.api.Events` | 51 | 37 | 13 (`Object` -> `Callable`) | 1 (`observeGesture` 实例方法, 不可构造) | 0 | 启用 |
| `autojs.runtime.api.Files` | `runtime.api.Files` | 41 | 37 | 1 | 3 | 0 | 启用 |
| `autojs.runtime.api.Threads` | `runtime.api.Threads` | 11 | 9 | 0 | 2 | 0 | 启用 |
| `autojs.runtime.api.Timers` | `runtime.api.Timers` | 12 | 6 | 6 (`int` -> `double`) | 0 | 0 | 启用 |
| `pio.PFiles` | `pio.PFiles` | 59 | 44 | 1 | 4 | 10 -> 0 (宿主补齐, 见下) | 启用 |
| `pio.PFile` | `pio.PFile` | 18 | 14 | 0 | 4 (返回 `PFile[]` 而非 `File[]`) | 0 | 启用 |
| `pio.PWritableTextFile` | `pio.PWritableTextFile` | 19 | 19 | 0 | 0 | 0 | 启用 |
| `pio.PReadableTextFile` | `pio.PReadableTextFile` | 10 | 10 | 0 | 0 | 0 | 启用 |
| `autojs.runtime.api.AbstractShell` | `runtime.api.AbstractShell` | 41 | 40 | 0 | 0 | 1 -> 0 (宿主补 `isRoot()`, 见下) | 启用 |
| `autojs.core.util.Shell` | `runtime.api.Shell` | 60 | 58 | 0 | 0 | 2 -> 0 (宿主补构造器 `(Context, boolean, boolean)` 与 `isRoot()`, 见下) | 启用 |
| `autojs.runtime.api.Images` | `runtime.api.Images` | 28 | 22 | 0 | 5 | 1 -> 0 (宿主补静态 `concat(img, img, dir)`, 见下) | 启用 |
| `autojs.core.image.Colors` | `core.image.Colors` | 21 | 8 | 0 | 1 | 12 -> 0 (宿主补 12 个静态颜色常量, 见下) | 启用 |
| `autojs.runtime.api.Device` | `runtime.api.Device` | 52 | 50 | 0 | 0 | 2 -> 0 (宿主补静态 `width` / `height`, 见下) | 启用 (维护者 2026-10-12 先决定不补, 当晚改为补齐) |

- 为启用上述类对宿主做的加法 (维护者 2026-10-12 确认, 都不改既有行为):
  - `org.autojs.autojs.pio.PFiles`: `readBytes(InputStream)`, `copyRaw`, `write(OutputStream, String, String?)`, `writeBytes(OutputStream, ByteArray?)`, `copyAsset`, `renameWithoutExtensionAndReturnNewPath`, `generateNotExistingPath`, `copyAssetToTmpFile` 补 `@JvmStatic` (此前只能经 `PFiles.INSTANCE` 调用); 新增 `getSdcardPath()` 与 `getSimplifiedPath(path)` (Auto.js 4 同名方法的语义, 基于 `EnvironmentUtils.externalStoragePath`).
  - `org.autojs.autojs.runtime.api.AbstractShell`: 新增 `isRoot()`, 与 `isExecWithRoot()` 相同.
  - `org.autojs.autojs.runtime.api.Shell`: 新增构造器 `Shell(Context, boolean root, boolean shouldReadOutput)`, 委托给两参构造器; Auto.js 4 只保存而从未读取 `shouldReadOutput`.
  - `org.autojs.autojs.runtime.api.Images`: 新增静态 `concat(ImageWrapper, ImageWrapper, int)`, 运行时取自第一张图片 (为此 `ImageWrapper` 增加只读属性 `scriptRuntime`), Auto.js 4 的方向 `Gravity.LEFT` / `RIGHT` 按 `START` / `END` 处理.
  - `org.autojs.autojs.core.image.Colors`: companion 常量 `BLACK`, `DKGRAY`, `GRAY`, `LTGRAY`, `WHITE`, `RED`, `GREEN`, `BLUE`, `YELLOW`, `CYAN`, `MAGENTA`, `TRANSPARENT` (与 `android.graphics.Color` 相同).
  - `org.autojs.autojs.runtime.api.Device`: 公开静态字段 `width` / `height` (类加载时取 `ScreenMetrics` 的设备尺寸, 每次构造 `Device` 时刷新; 兼容副本是 `static final`, 且因兼容包的 `ScreenMetrics` 从未初始化而恒为 0).
- 至此 16 个候选全部启用, 没有保留副本的类.
- `--all` 普查: 304 个有类比路径或同名宿主类的 `com.stardust` 类中 201 个按规则无阻止项 (多数是引擎内部类或接口, 脚本不会直接引用). 复现: `py tools/stardust/compare_alias_signatures.py --all`. 第二批的取舍见 10.4 节.

### 10.4 第二批 (2026-10-12, 维护者确认 "候选可以继续推进")

取舍规则: `--all` 普查中位于脚本可见包 (`pio`, `util`, `concurrent`, `io`, `lang`, `notification`, `autojs.core.eventloop`, `autojs.core.image`, `autojs.core.storage`, `autojs.core.util`, `autojs.core.inputevent.InputEventCodes`) 且结论为 alias 的类全部启用; 排除界面与组件类 (`util.DrawerAutoClose`, `util.BackPressedHandler`, `notification.NotificationListenerService`); 引擎内部包 (`engine`, `execution`, `rhino`, `script`, `project`, `core.ui`, `core.record`, `core.looper`, `core.permission`, `automator` 等) 不做. 启用的 36 个类 (宿主目标不在类比路径的以 `->` 注明):

- `autojs.core.eventloop`: EventEmitter, SimpleEvent; `autojs.core.image`: ColorFinder; `autojs.core.inputevent`: InputEventCodes; `autojs.core.storage`: LocalStorage; `autojs.core.util.ScriptPromiseAdapter -> runtime.api.ScriptPromiseAdapter`
- `concurrent`: ConcurrentArrayList, Value, VolatileDispose; `io`: ByteBufferBackedInputStream, ByteBufferBackedOutputStream, ConcatReader, EFile; `lang`: ThreadCompat; `notification.Notification -> core.notification.Notification`
- `pio`: PFileInterface, PRandomAccessBinaryFile, PReadableBinaryFile, UncheckedIOException
- `util`: AdvancedEncryptionStandard -> engine.encryption, ArrayUtils -> util, AssetsCache -> util, BiMap / BiMaps -> core.ui, Callback / Consumer / Func1 / Supplier -> tool, IntentExtras / LimitedHashMap / MapBuilder / SimpleCache / SparseArrayEntries / StateObserver -> tool, MD5 -> inrt.launch, Objects -> util

别名表现共 52 项; 单测 6 个用例通过; XQ-AT72 抽样 (IntentExtras, LocalStorage 读写, EventEmitter on/emit, MapBuilder, Func1 适配器, InputEventCodes 常量, ScriptPromiseAdapter, ThreadCompat, PFileInterface) 均解析到宿主类并正常工作.

脚本可见包里仍为副本的类及其阻止项 (需要宿主增补时由维护者决定, 补齐后普查会直接显示为 alias): `autojs.core.image.ImageWrapper` (静态 `ofBitmap` / `ofMat` / `ofImage` 与构造器 `(int, int)` 在宿主都多一个 `ScriptRuntime` 参数, `toBitmap(Image)` 只在 companion 上); `util.ScreenMetrics` (静态 `scaleX` / `scaleY` / `rescaleX` / `rescaleY` 在宿主是实例方法, 缺 `initIfNeeded(Activity)`, 构造器 `(int, int)`, `setDesignWidth` / `setDesignHeight`); `util.UiHandler` (缺 `getContext()`, `toast(String)`, `toast(int)`); `autojs.core.util.ProcessShell` (缺 `readSucceedOutput()`, `getSucceedOutput()`, `getSucceedReader()`); `util.TextUtils` (缺静态 `join`, `toEmptyIfNull`, `indexOf` x2, `lastIndexOf` x2); `autojs.core.image.ColorDetector` (嵌套类缺 `detectsColor(int, int, int)`); `autojs.core.image.TemplateMatching` (`fastTemplateMatching` 参数为 `core.opencv.Mat` 而宿主为 `org.opencv.core.Mat`); `concurrent.VolatileBox` (缺 `setAndNotify`); `io.Zip` (`unzip` 缺 `@JvmStatic`); `util.NetworkUtils` (`isWifiAvailable(Context)`); `util.DeveloperUtils` (`verifyApk(Activity, int)`); `util.ViewUtils` (`findParentById`, `pxToSp`). `util.ClipboardUtil` 没有同名宿主类 (宿主为 `ClipboardUtils`), 按 "简单名一致" 规则不是候选.
