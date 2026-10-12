# `com.stardust` 兼容包盘点 (Roadmap P7.1)

日期: 2026-10-12. 对象: 宿主 `app/src/main/java/com/stardust/**` (354 个文件, Java 282, Kotlin 72). 盘点脚本按 import 与同包简单类名引用计算依赖闭包, 结论用于决定 `autojs4-runtime-plugin-roadmap.md` 第 7 阶段 (D18) 的做法. 数字为脚本结果, 同包引用按类名匹配, 可能略高估 "受污染" 集合, 不会低估.

## 1. 结论

- 宿主代码 (`org.autojs.**`) 没有任何 `import com.stardust.*`, 仅 3 处注释提到来源. 兼容包是只被脚本 (Rhino LiveConnect) 使用的叶子.
- 兼容包反向依赖宿主: 25 个文件直接 `import org.autojs.*` (下称 "桥接文件"), 另有 99 个文件直接或间接引用桥接文件 (下称 "受污染文件"). 这 124 个文件不能移出 `:app`, 否则形成 `:app` 与新模块的循环依赖.
- 其余 230 个文件只依赖 Android / AndroidX / Rhino / OpenCV / 第三方库以及彼此, 可以整体迁入独立库模块 (方案 1 的可行范围).
- 因此 "独立 Gradle 库模块" 的准确形态是: `:compat:stardust` 承载 230 个独立文件; 124 个桥接与受污染文件留在 `:app` 的 `com.stardust` 包内 (包名不变), 它们编译时依赖新模块. 两部分都不改类名与包名, 脚本可见行为不变.

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

## 7. 复现盘点

```powershell
py E:/.cache/user-temp/claude/.../scratchpad/stardust_inventory.py   # 本次使用的一次性脚本
```

脚本逻辑: 遍历 `app/src/main/java/com/stardust/**/*.{java,kt}`, 读取 `import`; 桥接 = 含 `import org.autojs.`; 受污染 = 经 `com.stardust.*` import 或同包类名引用可达桥接文件 (迭代至不动点); 其余为独立. 正式实施 P7.2 时把脚本收入 `tools/` 并以编译结果校正.
