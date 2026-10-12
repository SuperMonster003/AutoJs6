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
- 建议: 若实施方案 2, 第一批只做 `runtime.api.*` (Files, Device, Images, Events, Threads, Timers, Dialogs, Engines, AbstractShell, AbstractConsole), `core.util.Shell`, `pio.PFiles` / `PFile` / `PWritableTextFile` / `PReadableTextFile` 与 `core.image.Colors` 这 ~18 个类, 每条别名附编译期签名对照测试 (参数与返回类型, 不只是方法名); `automator.*` 与 UI 膨胀相关类不做.
