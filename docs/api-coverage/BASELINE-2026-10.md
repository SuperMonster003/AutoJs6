# api-coverage 基线 (2026-10)

本文件按节记录 [ROADMAP](ROADMAP.md) P0 各条目的基线数字与修法. 第 1 节由 P0.1 写入; P0.3 (参数个数) 与 P0.4 (返回对象) 在后面追加自己的节, 不改前面的节.

## 1. P0.1 名称级门禁基线与修复 (2026-10-25, 子会话 api-cov-p0-1)

### 1.1 数据源与命令

- 读 JSON: `script-modules.json`, 由 `./gradlew :app:kspAppDebugKotlin` 生成, 路径 `app/build/generated/ksp/appDebug/resources/autojs6/script-modules.json` (= `extract_exports.DEFAULT_METADATA`). 本机共享 checkout 耗时: 热缓存 UP-TO-DATE 15 s, 只强制重跑 KSP 任务 (`--rerun --max-workers=2`) 30 s, 495 个任务, 产物与重跑前逐字节相同; augment 隔离克隆热缓存 105 s; CI 冷缓存估 4-8 min.
- 读 Kotlin: `extract_exports.py` 的旧包注册解析器 (`runtime/api/augment` + `ScriptRuntime.kt`).
- 门禁: `python tools/api-coverage/check.py [--source auto|ksp|kotlin] [--metadata <json>]` (`--source` 由本条目新增; 默认 auto, 文件存在时读 JSON; CI 用 `--source ksp`, 文件缺失即失败, 不再静默回退). Ace 检查用环境变量 `AUTOJS6_ACE_EDITOR_ROOT` 或同级 `AutoJs6-Plugin-Ace-Editor`. 本机全量含 Ace 约 26-30 s.
- 两条路径的差异: `python tools/api-coverage/extract_exports.py --compare-kotlin`.

### 1.2 基线数字

修前 = 2026-10-25 本会话开始时 (宿主 25996d7d2, Ace cc70b55); 修后 = 本节 1.6 的提交之后.

| 指标 | 读 Kotlin 修前 -> 修后 | 读 JSON 修前 -> 修后 |
|---|---|---|
| 注册路由 (registrationRoutes) | 92 -> 92 | 92 -> 92 |
| 显式导出 (explicitExports) | 1363 -> 1367 | 1368 -> 1368 |
| 文档证据 标题级 / 仅正文提及 | 1261 / 94 -> 1265 / 94 | 1265 / 95 -> 1265 / 95 |
| TS / Ace 查询路径 (queriedPaths) | 1598 -> 1602 | 1602 -> 1602 |
| 声明解析失败 | 0 -> 0 | 0 -> 0 |
| Ace 静态补全索引失败 | 9 -> 0 | 9 -> 0 |
| 未解析注册 (unresolved) | 2 -> 0 | 0 -> 0 |
| summary.json failures | 1 (TypeScript/Ace surface) -> 0 | 1 -> 0 |
| check.py 退出码 | 1 -> 0 | 1 -> 0 |

读 Kotlin 修后多出的 4 条是 colors 的四张颜色表 (1.4 节). ROADMAP 2.2 记的 2026-10-23 数字 (1363 / 1261 / 94 / 1598) 与本表读 Kotlin 修前一致.

修后两条路径的剩余差异 (`--compare-kotlin`, 不修, Kotlin 路径随 augment P5.2 退役):

- 只有 JSON: `wait.async` (flow 的 install 钩子定义, Kotlin 解析器不读 install 钩子).
- 种类或归属不同: `console.trace` (JSON Functions / Kotlin Properties), `isUiThread` (JSON 归全局 / Kotlin 归 ui).
- 模块可调用性不同: `Color`, `WebSocket` (JSON callable true / Kotlin false).

### 1.3 Ace 静态索引失败 9 条

- 条目: `$memory`, `memory`, `memory.collect` / `dumpHeap` / `retained` / `snapshot` / `watch`, `settings.categories` / `export`. 声明解析与 Ace 语义补全 (默认组 / 全组) 都通过, 只有静态补全 (`autojs6_indices.js`) 缺.
- 原因: Ace 插件捆绑的声明已有 memory 与 settings.export / categories (最近一次 2.43.0 be022c2, 2.44.0 1a4e029), 但没有重新生成静态索引, 宿主 `tools/ace-completion/autojs6_indices.source.json` 也随之过期 (`generate-editor-assets.mjs --check` 报 source stale).
- 修法: 按 `tools/ace-completion/README.md` 运行 `node tools/ace-completion/generate-editor-assets.mjs` (设 `AUTOJS6_ACE_EDITOR_ROOT`), 之后 `--check` 通过. 改动: Ace `autojs6_indices.js` / `types/lib.autojs6.d.ts` (Ace 017d031), 宿主 `autojs6_indices.source.json` (fc4559089). Ace 不改版本 (D14).

### 1.4 未解析注册 2 条 (只在读 Kotlin 时出现)

- `colors.selfAssignmentProperties`: `colorTables.map { table -> table::class.java.simpleName.lowercase() to table }`.
- `compose.selfAssignmentProperties`: `by lazy { val functions = linkedMapOf(...) ... }`; 显式键原先由文件末尾的 Compose 特例补出, 通用循环仍报 unresolved.
- 选择: 扩展解析器 (不走 policy 登记). 理由: 两处都是固定形状, 名称可从源码静态得出; 主路径 JSON 已显式列出这些名称 (colors 的四个 `@ScriptProperty`, compose 的 `installs`), 在 policy 里再补一份显式清单会与 JSON 重复且容易过期; 解析器按严格形状识别, 形状一变 (多出条目, 列表后缀, 改动 map, 计算出的键) 仍报 unresolved, 不会悄悄漏算. Compose 目录生成的 NODE 组件照旧排除 (与 JSON 的 `installsComputed` 一致).
- 结果: 两条路径 unresolved 都为 0; `policy.json` 的 `dynamicRegistrations` 清空 (原两条在两条路径下都已失效), `check.py` 对不再对应任何 unresolved 的条目报失败, 防止失效条目残留. 提交 7c109f62f (含 `test_coverage.py` 新增 3 个用例: 颜色表形状, Compose 函数表形状与 5 种反例, 当前源码可解析).
- Kotlin 回退路径的处置: 保留为本机无 KSP 产物时的回退, 不再承担 CI. augment P5.2 删除旧包后 `extract_kotlin` 读不到源码, 回退失效; 届时 CI 已只走 JSON (`--source ksp`), 依赖旧包源码的用例 (Mail 转发表, 本节当前源码用例, `test_script_modules.py` 的 HostTransitionTests) 自动跳过.

### 1.5 CI 工作流 (augment Q111)

- 核对: `.github/workflows/api-coverage.yml` 原本就运行含 Ace 的 `check.py` (未带 `--skip-ace`, 已 checkout Ace 仓库), 所以 9 条 Ace 失败在 CI 本就可见, 无需补 Ace 步骤. 原作业没有 Gradle 步骤, 只能走 Kotlin 回退.
- 改动 (199d13444): check.py 之前加 JDK 21 (temurin) + Gradle 缓存 (照搬 android.yml, 缓存键限定宿主 checkout) 与 `run-gradle-ci.sh ./gradlew :app:kspAppDebugKotlin --no-daemon --build-cache` (1200 s 守护); `check.py --source ksp`; 作业 timeout 20 -> 30 min; 触发路径加 `app/src/main/java/org/autojs/autojs/runtime/api/modules/**` (KSP 模块源码, 与 `extract_exports.METADATA_INPUTS` 一致) 与 `build-logic/ksp-script-modules-*/**`; 上传产物加 `script-modules.json`. CI 有了 JSON 后, HostTransitionTests (JSON 须包含 Kotlin 注册的全部名称) 也会在 CI 运行, 之前因无 JSON 一直跳过.
- 校验: actionlint 1.7.12 通过; 本机 `:app:kspAppDebugKotlin` 产物路径与 check.py 读取路径一致 (1.1 节).
- CI 改动未经远端验证, 待维护者推送后观察. 另: 该作业从 GitHub 默认分支 checkout 文档 / TS 声明 / Ace 等同级仓库; 本机 Ace / TS 声明 / 文档分别领先远端 53 / 54 / 4 个提交 (D6 不推送, 含本节的 Ace 017d031 与 memory / settings 声明), 推送前远端 CI 的 Ace 与文档检查结果会与本机不同.

### 1.6 提交

| 仓库 | 提交 | 内容 |
|---|---|---|
| Ace | 017d031 | 重新生成静态索引与 lib.autojs6.d.ts |
| 宿主 | fc4559089 | 补全源 autojs6_indices.source.json |
| 宿主 | 7c109f62f | 解析颜色表与 Compose 函数表, 清空 dynamicRegistrations, check.py --source, 单元测试 |
| 宿主 | 199d13444 | CI 构建 script-modules.json (augment Q111) |
| 宿主 | (本文件所在提交) | README 段落, 本基线文件, 总审计报告末行接管说明 |

## 2. P0.3 参数个数提取与比对 (2026-10-25, 子会话 api-cov-p0-3-4)

### 2.1 数据源, 命令与规则

- 实现参数个数 (D9): 读 JSON 时以 `script-modules.json` 为准 (augment-ksp-meta 的 arity: 单一原始值 vararg 重载声明的个数, 其余按参数表: 必需参数到全部参数, vararg 参数不限上限; 未声明的单一原始值 vararg 记 `vararg`, 函数体自行检查, 不比); 读 Kotlin 时取实现函数体开头的 `ensureArguments*` 守卫 (7 种, 跳过 `checkRuntime(...)` / `check(...) {...}` 与 `guarded {` 一类包装, 跟一层同文件委托; 同名函数优先只带 ScriptRuntime 的模块函数, 不取带 thisObj 的原型扩展), 其余记 unknown 并计数. 每条记录带 `source` (ksp / ensureArguments / unknown), 来源列 `metadata` 照旧 (ksp / kotlin).
- d.ts 个数: `dts-signatures.mjs` 读每个查询的 call / construct 签名 (rest 参数为元组时按元素计).
- 比对 (README "Arity"): d.ts 某个重载与实现所有重载都不相交为 fail; d.ts 接受实现拒绝的个数为 wider (警告); 实现区间的两端 (最小个数, 有限的最大个数, 非未声明 vararg 参数带来的无上限) 没有 d.ts 重载接受为 narrower (警告). 只报告, `check.py --strict-arity` 才把 fail 计入门禁失败.
- 命令: `python tools/api-coverage/check.py [--source ksp|kotlin]`, 产物 `build/reports/api-coverage/arity.json` (rows / counts / stats / crossCheck), `surfaces.json` rows[].arity, `summary.json` "arity". 单元测试 `test_arity.py` (8 例), `node tools/api-coverage/test-dts-signatures.mjs` (8 例).

### 2.2 基线数字 (宿主 78cc3a241 之后, TS 声明 44f6825, Ace 017d031)

| 指标 | 读 JSON (ksp) | 读 Kotlin |
|---|---|---|
| 有参数个数的行 (函数记录 + 可调用 / 可构造模块) | 1281 | 1273 |
| 来源 ksp / ensureArguments / unknown | 1281 / 0 / 0 | 0 / 979 / 294 |
| 重载按依据 | declared 292, params 955, vararg 60 | ensureArguments 934, vararg (unwrapArguments) 45 |
| 守卫种类 (读 Kotlin) | - | OnlyOne 297, LengthInRange 253, IsEmpty 177, AtMost 111, Length 84, AtLeast 11, NotEmpty 1 |
| unknown 原因 (读 Kotlin) | - | 守卫不在函数体开头 201 (委托 toolkit / FlowApi / EventWaitApi 等检查器), mail 转发表 48, Compose 函数表 13, 可调用模块的 invoke 32 |
| match | 1155 | 876 |
| fail | 0 | 0 |
| wider (警告) | 21 | 8 |
| narrower (警告) | 39 | 47 |
| vararg (不比) | 60 | 45 |
| unknown | 0 | 292 |
| no-dts | 1 | 0 |
| ksp 与 ensureArguments 交叉核对差异 | 47 (2.6 节) | 不适用 |

`ensureArguments*` 调用约 1094 处 (含 ArgumentGuards 自身 8 处定义); 读 Kotlin 时 979 个导出行能从守卫推出个数. 主路径读 JSON 没有 unknown.

### 2.3 失败与警告清单 (读 JSON)

fail: 0.

wider 21 行 (d.ts 允许的个数实现必然拒绝; 归 P2.2 改手写 d.ts; 其中 http 5 行与 util.java 2 行也可能是新层收紧过头, 已列入 2.6 节转 augment):

| API | 实现 | d.ts 重载 | 实现拒绝的个数 |
|---|---|---|---|
| auto.registerEvent / auto.registerEvents | 2-3 | 1-2, 3-3 | 1 |
| automator.gestures / gestures | 1-n | 0-n | 0 |
| automator.gesturesAsync / gesturesAsync | 1-n | 0-n, 1-2, 2-2 ... 10-10 | 0 |
| flow.clickWhenStableAfter / clickWhenStableAfter, flow.clickBoundsWhenStableAfter / clickBoundsWhenStableAfter | 2-7 | 1-7, 2-4 | 1 |
| colors.toColorStateList | 1-n | 0-n | 0 |
| console.trace | 0-2 | 1-n, 2-2 | 3+ |
| http.buildRequest | 2-2 | 1-2 | 1 |
| http.postJson / http.postJsonAsync | 2-4 | 1-4 | 1 |
| http.request / http.requestAsync | 2-3 | 1-3 | 1 |
| util.java.array | 2-n | 2-2, 1-n | 1 |
| util.java.mapToObject / util.java.objectToMap | 1-1 | 0-1 | 0 |
| util.java.toJsArray | 1-2 | 0-2 | 0 |

narrower 39 行 (实现接受, d.ts 没有声明的个数; 归 P2.2):

| API | 实现 | d.ts 重载 | 未声明 |
|---|---|---|---|
| Mathx.dist | 0-3 | 1-2, 2-3 | 0 |
| colors.alphaDouble / blueDouble / greenDouble / redDouble 及 getAlphaDouble / getBlueDouble / getGreenDouble / getRedDouble (8 行) | 1-2 | 1-1 | 2 |
| colors.toString | 0-2 | 1-2, 2-2 | 0 |
| console.setContentTextColor / setContentTextColors | 0-6 | 1-1, 1-6 | 0 |
| dialogs.build | 0-1 | 1-1 | 0 |
| engines.execAutoFile / engines.execScriptFile | 1-2 | 2-2 | 1 |
| engines.execScript | 2-3 | 3-3 | 2 |
| openConsole / showConsole | 0-1 | 0-0 | 1 |
| tasks.add*Task 9 行, timers.addDailyTask / addDisposableTask / addIntentTask / addWeeklyTask, workManager.addBroadcastIntentTask / addDailyTask / addDisposableTask / addIntentTask / addWeeklyTask (共 18 行) | 0-1 | 1-1 | 0 |
| util.morseCode.getCode / getPattern | 1-2 | 1-1 | 2 |
| yolo.load | 1-2 | 2-2 | 1 |

no-dts 1 行: `WebSocket.()` (模块可调用, 实现 1-2; d.ts 的 WebSocket 只有构造签名; 归 P2.2).

### 2.4 误报率核对 (flow / images / files)

- 三个模块共 181 行 (flow 68, images 90, files 5, 另含全局位置; files 的大部分成员在 Java 原型上, 见 3.4 节). 首版规则 (narrower 按全部个数比) 标记 5 行: flow 的 clickWhenStableAfter / clickBoundsWhenStableAfter (模块与全局各 1, wider) 与 images.clip (narrower).
- 逐行核对实现: flow 4 行属实 (`FlowArgs.waitArgs(withDelay = true)` 只有 cond 时报 delay 缺失, d.ts 第一个重载 `(cond, timeout?, ...)` 却允许 1 个参数); images.clip 是误报 (`ImagesCall.clip()` 只接受 2 个或 5 个参数, 参数表跨度 2-5 中间的 3 / 4 本就被拒绝, d.ts 的 2-2 与 5-5 准确).
- 调整: narrower 只比实现区间两端 (README "Arity"), narrower 由 60 行降到 39 行 (去掉的 21 行都是跨度中间的个数, 如 automator.click 1-4 对 d.ts 的 1 / 2 / 4, Color.() 0-4 对 d.ts 的 0-1 / 3-4). 调整后三个模块标记 4 行, 误报 0 (0%), 低于 5%.
- 其他模块抽查 (不计入验收): tasks.addDailyTask (新层 `options: NativeObject? = null`, 旧实现 AtMost 1 并以空对象代替) 与 engines.execScript (`config: Any? = null`) 的 narrower 属实; http.buildRequest 的 wider 属实 (生成的绑定 `ScriptArgs.count(args, 2)` 对 1 个参数报 arity 错误).

### 2.5 与 augment-ksp-meta 校准样本对照

- 校准样本 (共享笔记 augment-kspmeta-files/dts-arity-diffs-after.txt) 64 行: 54 行是本比对的函数行, 全部被标记 (narrower 35, wider 13, no-dts 1) 或属于不比的 vararg (5 行: isNullish.() 与 util.debuglog / deprecate / toRegularAndApply / toRegularAndCall, 都是 augment 有意不声明 arity 的变参或 Node 兼容桩), 没有一行判为 match. 其余 10 行不是函数行: 错误构造函数与 flow.Flow (属性), plugins.extend / extendAll / extendAllBut (属性), cvt.bytes / fmt.bytes (本比对记为 cvt.bytes.() / fmt.bytes.(), 实现以剩余参数自查 1-4, 上界不比, 记 match).
- 本比对多标记的 12 行: 上述函数的全局位置 (gestures, gesturesAsync, clickWhenStableAfter, clickBoundsWhenStableAfter), 嵌套模块 util.java 4 行与 util.morseCode 2 行, 全局 openConsole / showConsole; 校准样本只比了 74 个顶层模块.
- augment 记的 48 处类型签名差异与 "手写 d.ts 与实现不一致 7 处" 不是个数问题, 留给 D9 第二步的类型级比对与 P2.2.

### 2.6 ksp 与 ensureArguments 交叉核对 (转 augment, 本主题不改注解)

读 JSON 时用当前旧包 Kotlin 注册的守卫核对 ksp 区间, 差异 47 处 (`arity.json` crossCheck): 疑似漏标 1 处, 其余 46 处是新层与旧实现的个数不同.

- vararg-undeclared 1: `Numberx.ensureNumber` (ksp 未声明, 0-n; 旧实现 NotEmpty 1-n).
- 新层收紧: colors 19 (argb / hsl / hsla / hsv / hsva / rgb / rgba / oklab / oklch / toHsla / toHsva / toRgb / toHslString / toHsvString / toRgbString 改为按类型分的重载, 如 argb 1 或 4 个, 旧 1-4; toArgbString / toHslaString / toHsvaString / toRgbaString 下限 0 -> 1); ocr 8 与 barcode / qrcode 4 (detect / recognizeText / detectAll / recognizeTexts 上限 3 -> 2, 文档的选择形态); http 7 (buildRequest / request / requestAsync / postJson / postJsonAsync / postMultipart / postMultipartAsync 下限 1 -> 2, 旧实现允许只传 url); util.java.array 下限 1 -> 2, util.java.toJsArray 上限 不限 -> 2; Numberx.toFixedNum 2-2 -> 1-2; 全局 detect 0-4 -> 2-4.
- 元数据上限比实现宽 (实现自查): cvt.bytes.loose / strict, fmt.bytes.loose / strict (ksp 1-n, 旧 1-4).
- 处置: 转 augment 核对是否有意 (看板待决 Q19), 不改注解与实现. http 的下限变化会让 `http.request(url)` 一类旧写法在新层报错, 建议优先确认.

### 2.7 提交

| 仓库 | 提交 | 内容 |
|---|---|---|
| 宿主 | d6a129841 | arity.py, dts-signatures.mjs, verify-surfaces.mjs 记录 d.ts 签名个数, 单元测试 |
| 宿主 | b3c86b27c | extract_exports 给记录加 arity, check.py 写 arity.json 与 summary "arity", surfaces.json rows[].arity |
| 宿主 | (本节所在提交) | 回退解析的同名原型扩展取舍, README "Arity", 本节 |

## 3. P0.4 返回对象成员纳入分母 (2026-10-25, 子会话 api-cov-p0-3-4)

### 3.1 来源与产物

- 对象清单 `tools/api-coverage/returned-objects.json` (从 policy.json returnedObjectProbes 涉及的对象出发, 加 ROADMAP 列出的句柄与 script-modules.json 返回类型中的宿主类), 成员提取 `returned_objects.py`, 规则见 README "Returned objects": kotlin-class (Rhino 看到的 Java 对象: 类及其宿主内父类型的公开方法, 字段, Java record 分量, Kotlin 属性以名称计并以 JVM 访问器作别名), augment-prototype (运行时构建的 Scriptable: 字面量注册, 名称数组, 能解析为字面量集合的 forEach / for 注册, 常量键; 其余计算出的注册列入 dynamic), policy (清单列出).
- 生产函数: script-modules.json 返回类型匹配的导出行, 加清单补充 (声明为 kotlin.Any 的函数; 可调用 / 可构造模块写作 `X.()` / `X.new()`).
- `exports.json` 新段 `returnedObjects` (P0.2 约定的形状, 外加 documentation / probe / complete / externalBases / dynamic) 与 `returnedObjectsStats`; `check.py` 以每个对象的 d.ts 探针表达式另跑一次表面检查 (`build/reports/api-coverage/returned-objects/`), 结果写 `returned-objects.json`, 计数在 `summary.json` "returnedObjects", 不进 failures. 单元测试 `test_returned_objects.py` (4 例).

### 3.2 返回对象数与成员数

43 个对象, 1356 个成员: kotlin-class 32 个对象 1066 个成员, augment-prototype 11 个对象 290 个成员, policy 0. 不完整 1 个: Color (`get()` 把其他名称转给同名 colors 函数并以颜色为第一个参数, 只计显式名称 29 个).

| 对象 | 成员来源 | 成员 | 生产函数 | 文档缺 | d.ts 缺 | Ace 无 | Ace 仅全部组 |
|---|---|---:|---:|---:|---:|---:|---:|
| UiSelector | kotlin-class | 185 | 1 | 3 | 1 | 1 | 0 |
| UiObject | kotlin-class | 145 | 3 | 8 | 0 | 0 | 0 |
| UiObjectCollection | kotlin-class | 70 | 0 | 3 | 0 | 0 | 0 |
| ImageWrapper | kotlin-class | 16 | 26 | 1 | 0 | 0 | 10 |
| Flow | augment-prototype | 106 | 7 | 2 | 0 | 0 | 0 |
| MailClient | augment-prototype | 53 | 2 | 0 | 0 | 0 | 0 |
| EpubBook | augment-prototype | 14 | 1 | 0 | 0 | 0 | 0 |
| EpubReaderSession | kotlin-class | 35 | 1 | 14 | 1 | 1 | 2 |
| AiSession | augment-prototype | 11 | 1 | 0 | 11 | 11 | 0 |
| AgentRun | augment-prototype | 14 | 0 | 0 | 0 | 0 | 0 |
| ShellProcess | augment-prototype | 25 | 2 | 0 | 0 | 0 | 0 |
| ShellResult | kotlin-class | 5 | 4 | 0 | 0 | 0 | 0 |
| Dialog | kotlin-class | 140 | 1 | 132 | 1 | 1 | 136 |
| Canvas | kotlin-class | 54 | 2 | 0 | 0 | 0 | 54 |
| Color | augment-prototype (不完整) | 29 | 2 | 4 | 1 | 1 | 0 |
| VolatileDispose | augment-prototype | 3 | 1 | 0 | 0 | 0 | 0 |
| RawWindow | kotlin-class | 12 | 1 | 3 | 0 | 0 | 0 |
| FloatyWindow | kotlin-class | 12 | 1 | 3 | 0 | 0 | 0 |
| Zip | augment-prototype | 21 | 5 | 1 | 1 | 1 | 0 |
| Storage | augment-prototype | 12 | 1 | 0 | 0 | 0 | 0 |
| YoloDetector | augment-prototype | 2 | 1 | 0 | 0 | 0 | 0 |
| TtsUtterance | kotlin-class | 25 | 8 | 5 | 2 | 2 | 2 |
| ScriptExecution | kotlin-class | 13 | 3 | 8 | 1 | 1 | 12 |
| Database | kotlin-class | 51 | 1 | 0 | 0 | 0 | 49 |
| WebSocket | kotlin-class | 25 | 2 | 10 | 1 | 1 | 24 |
| InjectableWebView | kotlin-class | 1 | 2 | 0 | 0 | 0 | 1 |
| InjectableWebClient | kotlin-class | 3 | 2 | 1 | 0 | 0 | 3 |
| PReadableTextFile | kotlin-class | 5 | 2 | 1 | 0 | 0 | 5 |
| PWritableTextFile | kotlin-class | 7 | 2 | 1 | 5 | 1 | 6 |
| TimedTask | kotlin-class | 69 | 1 | 62 | 25 | 25 | 44 |
| IntentTask | kotlin-class | 21 | 1 | 20 | 1 | 1 | 20 |
| Mat | kotlin-class | 9 | 1 | 9 | 6 | 6 | 3 |
| MatchingResult | kotlin-class | 16 | 1 | 1 | 0 | 0 | 0 |
| ImageFeatures | kotlin-class | 12 | 1 | 2 | 0 | 0 | 0 |
| ObjectFrame | kotlin-class | 15 | 1 | 0 | 0 | 0 | 0 |
| PngQuantizationResult | kotlin-class | 5 | 1 | 0 | 0 | 0 | 0 |
| PngQuantizationFileResult | kotlin-class | 5 | 1 | 0 | 0 | 0 | 0 |
| ConsoleConfigurator | kotlin-class | 53 | 1 | 3 | 0 | 0 | 53 |
| ContinuationCreator | kotlin-class | 3 | 1 | 0 | 0 | 0 | 0 |
| ThemeColor | kotlin-class | 10 | 2 | 9 | 0 | 0 | 10 |
| App | kotlin-class | 10 | 1 | 0 | 10 | 0 | 10 |
| ScreenCaptureOptions | kotlin-class | 5 | 1 | 0 | 0 | 0 | 5 |
| ScriptEngine | kotlin-class | 29 | 1 | 17 | 3 | 3 | 26 |

### 3.3 门禁失败项 (单独列出, 不阻塞 CI, 供 P2.3)

- 43 个对象的探针都解析到显式 d.ts 类型. 名称级失败的成员 350 个 (文档缺 323, d.ts 缺 70, Ace 无 56); 另有 475 个成员只在 Ace 全部声明组中补全 (Java 对象的成员来自生成的 Java 声明组, Ace 默认库组不含), 不计失败, 矩阵 ace 列按 P0.2 定义记 none. 明细在 `build/reports/api-coverage/returned-objects.json` 的 failures.
- 集中的几类: Dialog (JsDialog 140 个公开方法, 文档只写了 6 个), TimedTask / IntentTask (定时任务对象的 getter / setter, 文档与 d.ts 大多未写), ScriptEngine / ScriptExecution / WebSocket (文档缺约一半), AiSession (探针 `ai.session.()` 解析到的 d.ts 类型缺 ask / chat / stream 等 11 个成员), App (d.ts 缺 10 个), Mat (d.ts 缺 6 个). 其中哪些是 Java 对象上公开但不打算作为脚本 API 的方法, 由 P2.3 判定, 必要时在 returned-objects.json 加 exclude.
- 读 Kotlin 时 (无 script-modules.json) 只有清单补充的生产函数, 成员与门禁结果相同.

### 3.4 未纳入的返回类型与已知缺口

- script-modules.json 里不在清单的宿主类 2 个: `MutableOkHttp` (http.__okhttp__, 内部导出) 与 `RootUtils.RootMode` (autojs.getRootMode, 枚举).
- 宿主外的类 16 个 (成员不枚举, 运行时列按 java-prototype 处理的候选): android NotificationChannel / Intent / ColorStateList / graphics.Point / Uri / View, androidx NotificationCompat.Builder, java File / Locale / ThreadPoolExecutor, okhttp3 OkHttpClient / Request, Rhino Synchronizer, OpenCV Point / Rect / Size.
- 宿主外父类型的成员不计: UiObject (androidx AccessibilityNodeInfoCompat), Database (SQLiteOpenHelper), WebSocket (okhttp3.WebSocket), InjectableWebView / Client (WebView / WebViewClient), Mat (OpenCV Mat); RhinoPaint 不在清单 (3 个自有成员, 其余来自 android Paint, 没有导出的生产函数).
- 嵌套句柄未列: MailClient.folder() 返回的文件夹对象 (`folderObject`), ai.agent (Augmentable 对象; 其 run() 返回的 AgentRun 已列). UiObjectCollection 与 AgentRun 没有导出行作为生产函数 (由 UiSelector / ai.agent 的成员产生).
- 22 个模块以 Java 对象为原型 (@ScriptPrototype: files, app, device, console, http, media, threads, timers, ui, util 等), 原型上的成员 (如 files.exists) 不在显式导出分母里, 现在只有 policy.json returnedObjectProbes 抽查几条; 是否按返回对象的方式纳入见看板待决 Q18.

### 3.5 矩阵

`docs/api-coverage/MATRIX.md` 重新生成 (78cc3a241): 2724 行 = 显式导出 1368 + 返回对象成员 1356. returned-member 行的 doc / dts / ace 取自本节门禁 (`check.py` 把 `<Object>#<member>` 行并入矩阵输入, runtime_matrix.py 未改); 运行时三列在 runtime-policy.json 映射或排除前为 pending: Node main 554 -> 1910, Node Worker 709 -> 2065, Bun 658 -> 2014 (返回对象成员的排除规则见看板待决 Q17).

### 3.6 提交

| 仓库 | 提交 | 内容 |
|---|---|---|
| 宿主 | 78cc3a241 | returned_objects.py, returned-objects.json, 单元测试, extract_exports / check.py 接线, verify-surfaces.mjs 的 `X.new()` Ace 文本, MATRIX.md |
| 宿主 | (本节所在提交) | README "Returned objects", 本节 |

## 4. P0.6 Ace 运行时指令提示门禁与 CI 接入 (2026-10-25, 子会话 api-cov-p0-6)

### 4.1 CI 工作流 (Q14 / Q16 (a), D16)

- `.github/workflows/api-coverage.yml`: check.py 改为 `--source ksp --matrix --strict-arity`; 新增产物 `runtime-coverage-matrix` (runtime-matrix.json 与 summary.json, 整个报告目录仍在 `script-api-coverage`); 新增两步: "Check Ace runtime completion against the runtime matrix" (Ace 仓库 `generate-runtime-api-queries.mjs --check --host ../AutoJs6` 与 `verify-runtime-api-coverage.mjs --fresh`) 和 "Check runtime declaration digests" (TS 仓库 `sync-runtime-declarations.mjs --check --ace` 与 `verify-runtime-declarations.mjs <Ace 自带编译器> --ace`); 触发路径加 `docs/api-coverage/MATRIX.md`. actionlint 通过. 维护说明 (Node / Bun 改声明或宿主改导出后回宿主 `--write-matrix`, 覆盖入口变化时再在 Ace 重新生成查询清单) 写进 README "Runtime coverage matrix > In CI".
- 本机按 CI 同参数验证 (宿主 HEAD 导出副本, 本机 KSP script-modules.json, 兄弟仓库本地 HEAD): check.py 退出码 0, failures 空, MATRIX.md current; arity match 1155 / fail 0 / wider 21 / narrower 39 / vararg 60 / no-dts 1 (`--strict-arity` 只拦 fail); Ace 清单 `--check` 通过, Ace 校验 3059 次查询 (其中矩阵 1887) 全部通过; TS `sync --check --ace` changed 0, `verify --ace` 79 份副本摘要一致. 宿主 Gradle 未运行 (沿用已有 KSP 输出).
- 同日直接在工作区运行时 MATRIX.md 为 stale: selector 会话尚未提交的 `UiObjectActions.kt` 新增 performTooltipAction (UiObject / UiObjectCollection / UiSelector 各 +1, 返回对象成员 1356 -> 1359). 这类改动提交时要一并 `check.py --write-matrix` 提交 MATRIX.md, 本卡片未替它重生成.

### 4.2 远端 CI 与本机的差别

远端作业读各仓库默认分支. 2026-10-25 本机领先远端: Ace 54, TS 声明 56, 文档 7, Node 56, Bun 54 个提交 (宿主不推送, D6). 推送前远端结果会与本机不同:

- Ace 默认分支没有 `generate-runtime-api-queries.mjs` 与 `runtime-api-queries.json`, Ace 步骤会因文件不存在失败.
- Node / Bun 默认分支的声明远落后于本机, 远端生成的矩阵与入库 MATRIX.md 不同, `--matrix` 会报 stale; TS 默认分支的 `verify-runtime-declarations.mjs` 忽略 `--ace` (只核对自身副本).
- 推送顺序建议 (P7): Node / Bun / TS / Ace / 文档先推, 宿主最后推; 推送后在宿主重新 `--write-matrix` 确认 MATRIX.md 仍为 current.

### 4.3 Ace 运行时查询 (D16)

- `tools/ace-lsp/generate-runtime-api-queries.mjs` (Ace 仓库) 读宿主 `build/reports/api-coverage/runtime-matrix.json` (`--matrix` / `--host` / 环境变量 `AUTOJS6_RUNTIME_MATRIX`, 默认兄弟目录; 没有矩阵时说明生成方法并以退出码 2 结束), 写入库的 `tools/ace-lsp/runtime-api-queries.json`: Node main / Worker / Bun 列为 bridged / local-shim / capability-gated 的每一行都有查询, 入口取 Node 单元格的 module / member (另加 explicitGlobal 的 `$autojs.<module>` 入口) 与 Bun 单元格的 namespace / member, 别名行共用同一入口只查一次; `--check` 比对矩阵.
- 数字: 矩阵 2724 行中覆盖行 759, 查询 1887 (Node main 676, Worker 502, `$autojs` 全局 186, Bun 523); `verify-runtime-api-coverage.mjs` 总查询 1172 -> 3059, 全部通过, 耗时约 21 s. P1.1 的 5d6c2aa91 (Bun 别名 getClip / setClip, notice.cancel, device 尺寸) 之后重新生成: Bun 529, 矩阵查询 1893, 总查询 3065, 全部通过 (Ace cfe50d2), 即 README "In CI" 所说的联动. 反例: 伪造 5 个不存在的入口 (各路线一个, 含错误命名空间与嵌套成员) 全部被列为失败. `--fresh` 把 LSP 声明生成到临时目录 (没有 Gradle 输出时自动使用), 宿主 CI 用它.

### 4.4 BUNDLED_DECLARATIONS.md

- Ace `types/autojs6/BUNDLED_DECLARATIONS.md` 写的是 4.42.0, 实际捆绑的 82 个核心声明与 TS 仓库 4.83.0 (44f6825) 逐字一致 (ROADMAP 立项时记的 4.81.0 之后又发了 4.82.0 / 4.83.0), 改为 4.83.0, 来源一行写明导入脚本与同步脚本, 新增运行时声明来源一行. 漂移原因: `sync-runtime-declarations.mjs --ace` 复制 package.json 却不改该行; 现在同步时一并改写, `--check` 时报 stale. 宿主导入脚本 `tools/ace-completion/import-autojs6-dts.mjs` 的模板同步更新, 重新导入得到同样的文本.
- 重新导入还会改写 Ace 的 `autojs6/index.d.ts` (改为字母序, 去掉 `aj6-main-app.d.ts` 引用), 与 Ace 现行版本不一致; 本卡片没有应用这部分 (恢复为 HEAD), 登记为待决 Q23.

### 4.5 `--skip-ace --matrix` 不再误报 stale

`check.py --skip-ace` 时 surfaces.json 的 aceChecked 为 false, 矩阵的 ace 列原先全部记 none, 入库 MATRIX.md 被判为 stale. 现在 ace 列保持 null, 矩阵缺 doc / dts / ace 任一列时 MATRIX.md 记 skipped 并在 matrix.findings 说明原因; `--write-matrix` 拒绝 `--skip-ace`, 生成器拒绝写出缺列的 MATRIX.md (单元测试 test_without_ace_or_reports). 复现命令 `check.py --source ksp --skip-ace --matrix --output <scratch>` 退出码 0.

### 4.6 提交

| 仓库 | 提交 | 内容 |
|---|---|---|
| 宿主 | 466f6f1a0 | runtime_matrix.py / check.py 的 skipped 修法, 单元测试 |
| 宿主 | (本节所在提交) | api-coverage.yml, README "In CI", import-autojs6-dts.mjs 模板, 本节 |
| Ace | 7d4f966 | generate-runtime-api-queries.mjs, runtime-api-queries.json, verify-runtime-api-coverage.mjs (矩阵查询, --fresh), BUNDLED_DECLARATIONS.md, 开发文档一节 |
| Ace | cfe50d2 | 随宿主 5d6c2aa91 重新生成 runtime-api-queries.json (1893) |
| TS 声明 | 9889606 | sync-runtime-declarations.mjs (Version 行), verify-runtime-declarations.mjs --ace, RUNTIME_APIS.md |

## 5. P1.5 矩阵修正 (2026-10-25, 子会话 api-cov-p1-5)

维护者 2026-10-25 拍板 Q26 / Q27 / Q28 均选 (a). 生成器, 策略与测试在宿主 a1873159b (规则见 tools/api-coverage/README.md "Runtime coverage matrix").

### 5.1 数据与命令

- 宿主 HEAD 导出副本 (6c62786b5, 副本内单独运行 `:app:kspAppDebugKotlin`, 不含其他会话未提交的改动), Node 990f9e35, Bun 3056b94, TypeScript 6.0.3 (Ace 自带编译器).
- 修正后: `python tools/api-coverage/check.py --source ksp --write-matrix --strict-arity`, 再以 `--matrix --docs-support --strict-arity` (含 Ace) 复核, 退出码 0, failures 空, MATRIX.md / 文档三页 / Ace 查询清单均 current.
- 修正前: 用 P1.5 之前的 runtime_matrix.py / list-runtime-surfaces.mjs / runtime-policy.json (宿主 e2ecc8aca) 读同一份报告. 两份矩阵行集相同 (2727 行), 状态变化全部可归到下表三项, 其他变化 0.

### 5.2 pending 前后 (三列)

| 列 | 修正前 | Q26 拒绝入口 (改记 pending) | Q27 句柄类型 (改记覆盖) | Q28 Toolkit 别名 (改记覆盖) | 修正后 |
|---|---:|---:|---:|---:|---:|
| Node main | 844 | +37 | -221 | -38 | 622 |
| Node Worker | 999 | +3 | -210 | -38 | 754 |
| Bun | 942 | +30 | -221 | -38 | 713 |

### 5.3 Q27 句柄成员比对

`runtime-policy.json` returnedObjects 把 7 个 augment-prototype 对象对到运行时句柄类型, 逐成员比对: 类型声明了的成员记覆盖 (Node 取模块状态 bridged, Worker 要求该模块是 Worker 宿主模块; Bun 记 capability-gated 并带生产调用的能力), 其余保持 pending. 运行时类型多出的成员 (如 Node / Bun `Book` 19 个成员对宿主 EpubBook 14 个) 不进分母.

| 对象 | Node 类型 | Bun 类型 (能力) | 成员 | Node main 覆盖 / pending | Node Worker 覆盖 / pending | Bun 覆盖 / pending | pending 成员 |
|---|---|---|---:|---|---|---|---|
| Flow | `autojs6:flow` Flow<unknown> | RuntimeFlow.Flow<unknown> (flow.control) | 106 | 104 / 2 | 104 / 2 | 104 / 2 | await, sync |
| MailClient | `autojs6:mail` Client | RuntimeMail.Client (mail.control) | 53 | 53 / 0 | 53 / 0 | 53 / 0 | - |
| ShellProcess | `autojs6:shell` ShellProcess | RuntimeShell.ShellProcess (shell.control) | 25 | 15 / 10 | 15 / 10 | 15 / 10 | addListener, getErrorStream, getInputStream, getOutputStream, isAlive, listenerCount, prependListener, prependOnceListener, removeListener, toString |
| AgentRun | `autojs6:ai` AgentRun | RuntimeAi.AgentRun (agent.control) | 14 | 14 / 0 | 14 / 0 | 14 / 0 | - |
| EpubBook | `autojs6:epub` Book | RuntimeEpub.Book (epub.control) | 14 | 13 / 1 | 13 / 1 | 13 / 1 | toString |
| Storage | `storages` StorageStore | RuntimeStorage.StorageStore (storage.control) | 12 | 11 / 1 | 0 / 12 | 11 / 1 | size; Worker 全部 12 个 (storages 不是 Worker 宿主模块, REVIEW R8) |
| AiSession | `autojs6:ai` Session | RuntimeAi.Session (ai.control) | 11 | 11 / 0 | 11 / 0 | 11 / 0 | - |
| 合计 | | | 235 | 221 / 14 | 210 / 25 | 221 / 14 | |

剩余 14 个成员 (Node main / Bun) 的归属登记为待决 Q31 (附录 C): Flow#await / sync 与 ShellProcess 的三个 Java 流 getter 可作附录 D 候选, EventEmitter 别名 (addListener 等 5 个), isAlive, toString (2), Storage#size 可由 P3 / P4 在运行时句柄补成员或写 memberAliases. 没有句柄类型的 augment-prototype 对象 (Color, Zip, VolatileDispose, YoloDetector) 仍全部 pending.

### 5.4 Q26 拒绝入口

`list-runtime-surfaces.mjs` 把每个调用签名都返回 `never` / `Promise<never>` 的成员标 `rejects`; 生成器优先取不拒绝的入口, 只剩拒绝入口时该列记 `pending` 并带 `rejects: true` 与入口 (MATRIX.md 写 `pending (rejects)`, 汇总行 "拒绝入口"). 拒绝入口单元格 Node main 37 / Worker 3 / Bun 30, 共 40 个显式导出行; 按附录 D 核对版分组 (D-4 25 行, D-5 2 行, D-7 3 行, D-12 7 行) 见 [REVIEW "P1.5 拒绝入口分组"](REVIEW-codex-2026-10.md). 文档 files 页的 files.open / toFile / open 随之由 "已支持 (本地实现)" / "需授权" 改为 "待补" (文档仓库 4007465); image / flow 两页不变.

### 5.5 Q28 Toolkit 别名

`runtime-policy.json` node / bun memberAliases 把 automator 的 19 个 Toolkit 助手 (backToApp, backUntil, clickAny, clickBoundsAny, clickBoundsIfExists, clickIfExists, collectList, dismissPopups, findAny, launchAndWait, scrollUntil, smartClick, smartClickBounds, toggle, typeInto, waitForEvent, waitForIdle, waitForNotification, waitForToast) 与它们的全局放置 (共 38 行) 对到 `autojs6:flow` / Bun `flow` 的同名成员 (via member-alias): Node main 与 Worker 记 bridged, Bun 记 capability-gated (flow.control). 文档生成器给 member-alias 入口标 "经 <模块>" (文档仓库 241296c); automator 页尚未插入运行时支持表 (P2.5).

### 5.6 Ace 运行时查询

矩阵新增的句柄成员单元格带 `type`, Ace 生成器把它们放进 node_handle / bun_handle 两条路由, 校验脚本在该类型的值上补全 (`.cjs` 用 JSDoc 类型, `.bun.ts` 用 `declare const`); 拒绝入口不再查询. 覆盖行 759 -> 981, 矩阵查询 1893 -> 2291 (Node main 652, Worker 499, `$autojs` 全局 186, Bun 512, Node 句柄 221, Bun 句柄 221), 加手写查询共 3463, 全部通过; 伪造的两个句柄成员按预期报失败 (Ace 6e2bda0).

### 5.7 共享签出中的校验

在共享签出运行同一命令时, MATRIX.md 判 stale, 另有 `auto.clearFlags` 文档名与表面检查失败: 均来自其他会话未提交的改动 (多出 `auto.clearFlags` 导出与 `UiObject#refresh` 成员, 共享签出的 KSP 产物也较旧); 两边共有的 2727 行状态 0 差异. 待这些改动提交后由其会话 `--write-matrix`.

### 5.8 提交

| 仓库 | 提交 | 内容 |
|---|---|---|
| 宿主 | e2ecc8aca | ROADMAP P1.5 条目, P1.4 依赖, 附录 C Q21 / Q26 / Q27 / Q28 已拍板 |
| 宿主 | a1873159b | list-runtime-surfaces.mjs (rejects, 句柄类型成员), runtime_matrix.py, runtime-policy.json (returnedObjects 7 条, memberAliases 38 x 2), 单元测试, README |
| 宿主 | 6c62786b5 | ROADMAP 进度 |
| 宿主 | 9c440e7b5 | MATRIX.md (HEAD 导出副本生成) |
| 宿主 | (本节所在提交) | 本节, REVIEW "P1.5 拒绝入口分组" 与第 9 节, ROADMAP 勾选与附录 C Q31 |
| 文档 | 241296c | runtime-support.js "经 <模块>", 自检, generator/README |
| 文档 | 4007465 | files 页运行时支持表与生成产物 |
| Ace | 6e2bda0 | 句柄查询路由, runtime-api-queries.json (2291), 开发文档 |

## 6. P1.4 白名单拍板与 pending 基线 (2026-10-29, 子会话 api-cov-p1-4)

维护者 2026-10-29 以三轮选择题逐组拍板附录 D (Q1) 与 Q5 (a); 结论表在 ROADMAP 附录 D, 题目材料在 REVIEW "P1.4 白名单拍板材料". 排除规则在 `tools/api-coverage/runtime-policy.json` (status "decided 2026-10-29"), 生成器与门禁规则见 `tools/api-coverage/README.md` "Runtime coverage matrix".

### 6.1 数据与命令

- 宿主 HEAD 导出副本 (git archive ffd0b4a58, 副本内单独运行 `:app:kspAppDebugKotlin`; 之后的宿主提交只改 tools / docs 与 assets 样例, 用 git archive HEAD 覆盖副本的 tools / docs, 不含其他会话未提交的改动), Node 990f9e35, Bun 3056b94, Ace 9fce0de, TS 496aaf8, 文档 14714fa.
- 生成: `check.py --write-matrix --write-pending-baseline --strict-arity --docs-support` (含 Ace); 验收: `check.py --matrix --strict-arity --docs-support` (含 Ace, `AUTOJS6_ACE_EDITOR_ROOT` 指向 Ace 仓库) 退出码 0, failures 空, MATRIX.md / pending-baseline.json / 文档三页 (images / files / flow) 均 current, 策略冲突 0.
- 单元测试 `python -m unittest discover -p "test_*.py"` (tools/api-coverage, 共享签出): 68 项通过, 跳过 3 项 (augment 包已删除的 2 项与需要旧 augment 包的 1 项, 与本条目无关).

### 6.2 HEAD 门禁修复 (拍板前)

augment P5.2 (37b93d893) 删除 `runtime/api/augment` 后, `returned-objects.json` 的 19 个对象类名与 `check-shell-contract.mjs` 读的两个源文件仍指向旧包, 返回对象成员从 1362 掉到 956. 0671ffb36 改指 `runtime/api/modules/<m>/`, 成员恢复 1362, 矩阵 2739 行. 另 7 个 Codex 设备脚本 (`check-runtime-ai` / `ai-agent` / `bridges` / `epub` / `mail` / `speech` / `tts`) 用正则读旧注册写法, 只改路径仍失败, 登记待决 Q42 (未先行).

### 6.3 pending 前后 (三列, 2739 行)

拍板前为 P1.5 规则下的 HEAD 矩阵 (P1.5 后宿主新增导出 12 行, 故与第 5.2 节的 622 / 754 / 713 不同).

| 列 | 拍板前 | D-13 删 jsox-extensions | D-14 拆分 | D-1 | D-6 | D-8 | 拍板后 |
|---|---:|---:|---:|---:|---:|---:|---:|
| Node main | 631 | 0 | +1 | +1 | +7 | +3 | 643 |
| Node Worker | 763 | +54 | +3 | +1 | +7 | +4 | 832 |
| Bun | 722 | +54 | +3 | +1 | +7 | +4 | 791 |

- 状态计数: not-portable 1126 / 1186 / 1185 -> 1115 / 1118 / 1117, denied 1 -> 0 (auto.waitFor 的 accessibility-sync-wait 删除), 覆盖 (bridged / local-shim / capability-gated) 不变.
- 删除规则 6 条: accessibility-sync-wait, sync-threads, sync-continuation, sync-sleep, sync-keep-alive, jsox-extensions; rhino-engine 只留 `plugins.load` (Worker / Bun); 新增 http-java-objects (http.client / http.buildRequest, java-prototype, 原属 native-http, pending 不变). 其余保持 pending 的组 (D-2 / D-4 / D-5 / D-7 / D-10 / D-12 / D-17, Q31 的 5 个句柄成员) 本来就是 pending, 计数不变.
- 拍板前的 3 个 shadowed 冲突 (jsox-extensions / rhino-engine / sync-sleep 在 Node main) 随规则删改消失.
- 按模块与模块族的分布见 REVIEW 第 7 节.

### 6.4 门禁: 缺理由与 pending 基线 (Q40 先行)

- `check.py --matrix` 把排除理由缺失, Bun 缺能力名与策略冲突列为失败 (此前只报告).
- `tools/api-coverage/pending-baseline.json` 按列记录允许的 pending 单元格 (643 / 832 / 791). 矩阵出现基线没有的 pending 格, 或基线列出的格已不再 pending, 都是失败; 基线缺失也是失败. 收缩用 `--write-pending-baseline`; 新增 pending 时该命令拒写, 须在看板登记待决后加 `--allow-new-pending`. 数据源不同 (例如 kotlin 与 ksp) 时比较记 skipped.
- 单元测试 `PendingBaselineTest.test_new_and_stale_cells` 覆盖新增与过期两种失败, `MarkdownTest.test_missing_reasons_fail` 覆盖缺理由失败, `RepositoryTest.test_p1_4` 核对拍板后的代表行.

### 6.5 Q41 代补 speech.speaker

题 12 (Q41) 维护者选本条目代补 8 行; auto.clearFlags 已由 selector P4 补齐 (文档 d163293, TS 0c4b188, Ace 5847c8b), 本条目只补 speech.speaker (`[p+] speaker` 与 enroll / enrollAsync / verify / verifyAsync / identify / identifyAsync / list / remove, 错误码 SPEAKER_NOT_FOUND / AUDIO_TOO_SHORT, feature `speaker`). 文档 none 340 -> 334, d.ts none 90 -> 82, Ace none 552 -> 544; 名称级门禁失败清零. 运行时列仍为 pending (Node / Bun 没有 speaker 入口, REVIEW 第 7 节归 P3.5 / P4.5). 3-Macaw P8.3.7 (d.ts / 文档站 / Ace) 届时只需核对.

### 6.6 提交

| 仓库 | 提交 | 内容 |
|---|---|---|
| 宿主 | 0671ffb36 | returned-objects.json 与 check-shell-contract.mjs 改指 runtime/api/modules |
| 宿主 | 0529f59db | REVIEW "P1.4 白名单拍板材料" (题 1-12) |
| 宿主 | 34de6958a | ROADMAP 附录 C (Q1 / Q5 / Q18 / Q20 / Q22 / Q23 / Q31 / Q34, Q40-Q42) 与附录 D 结论表 |
| 宿主 | d52fd2072 | runtime-policy.json 按拍板, check.py `--write-pending-baseline` / `--allow-new-pending`, 门禁缺理由失败与基线比较, 单元测试, README |
| 宿主 | 3c3764c4e | Ace 补全源 autojs6_indices.source.json (speech.speaker) |
| 宿主 | f461d9c1d | MATRIX.md 与 pending-baseline.json (HEAD 导出副本生成) |
| 宿主 | (本节所在提交) | 本节, REVIEW 第 7 节定稿与第 9 节, ROADMAP 勾选 P1.4 |
| 文档 | 14714fa | speech.md speaker 节, 错误码, features, progress.md, 全量生成 |
| TS | 496aaf8 | aj6-int-speech.d.ts speech.speaker |
| Ace | 9fce0de | aj6-int-speech.d.ts 副本, autojs6_indices.js, lib.autojs6.d.ts |

## 7. P2.2 参数个数不一致项修复 (2026-10-29, 子会话 api-cov-p2-2)

### 7.1 数据与命令

- 清单: 宿主 HEAD 导出副本 08f5eb2ea (script-modules.json 复用 P2.1 副本的 KSP 产物: 两副本 app/src/main 与 build-logic 逐字相同, 未再跑 Gradle), TS 496aaf8, 文档 ffd5c2c, Ace 9fce0de; `check.py --skip-ace` 得 match 1164, fail 0, wider 21, narrower 39, vararg 60, no-dts 1, 与 2.3 节同为 61 行.
- crossCheck 为 0 行: augment P5.2 (37b93d893) 删除旧包后没有可比的 Kotlin 注册守卫, 2.6 节的 47 处在 7.4 逐项核对.
- 判定依据: 新层 `runtime/api/modules/**` 的 `@ScriptFunction` 参数表与函数体检查, 文档 (`AutoJs6-Documentation/api`) 的签名与正文用法, 必要时对照 AutoJs4 (`modules/__http__.js`).

### 7.2 wider 21 行 (全部改 d.ts)

| API | 实现 | 原 d.ts | 结论 |
|---|---|---|---|
| auto.registerEvent / registerEvents | 2-3 | 1-2, 3-3 | 改 d.ts: listener 必需, 类型含 null / undefined (文档: 为 null 或 undefined 时移除监听; 旧实现同为 2-3) |
| automator.gestures / gestures, automator.gesturesAsync / gesturesAsync (4 行) | 1-n | 0-n | 改 d.ts: `(stroke, ...strokes)` 至少 1 笔 (文档 "一个或多个笔画") |
| flow.clickWhenStableAfter / clickBoundsWhenStableAfter 及全局 (4 行) | 2-7 | 1-7, 2-4 | 改 d.ts `Flow.WaitAfterFunction` 与文档签名 (flow.md / global.md) 的位置形式为 `(cond, timeout, interval, delayMin, delayMax?, onOk?, onErr?)`: FlowArgs 的位置延时是第 3 个数字, 少于 3 个数字且选项没有 delay 时抛错, 原签名的 timeout? / interval? 不成立 |
| colors.toColorStateList | 1-n | 0-n | 改 d.ts: `(color, ...colors)` (文档 "一个或多个") |
| console.trace | 0-2 | 1-n, 2-2 | 改 d.ts 为 `trace(message?, level?)`: 第二个参数是输出等级, 不是格式化参数, 3 个以上参数报错; 文档签名改 message 可选 (新层无参输出 undefined 与调用栈) |
| http.buildRequest / request / requestAsync (3 行) | 2-2 / 2-3 | 1-2 / 1-3 | 改 d.ts: options 必需. 6.8.0 文档写明必需且须含 method, 新层 builderOptions 一致; 6.7 文档可选的兼容问题见 Q51 |
| http.postJson / postJsonAsync (2 行) | 2-4 | 1-4 | 改 d.ts: data 必需 (文档 `postJson(url, data, options?, callback?)`); 见 Q51 |
| util.java.array | 2-n | 2-2, 1-n | 改 d.ts: `(type, dimension, ...dimensions)` (文档 "至少需要一个维度"; 旧实现 1 个参数同样抛错) |
| util.java.mapToObject / objectToMap (2 行) | 1-1 | 0-1 | 改 d.ts: 参数必需, 类型含 null / undefined (文档同) |
| util.java.toJsArray | 1-2 | 0-2 | 同上 |

### 7.3 narrower 39 行与 no-dts 1 行

改 d.ts 10 行 (文档签名本已如此):

| API | 实现 | 原 d.ts | 改为 |
|---|---|---|---|
| console.setContentTextColor / setContentTextColors | 0-6 | 1-1, 1-6 | 位置重载第一个参数可选 (文档: 0 至 6 个位置颜色, 无参传入空颜色配置) |
| dialogs.build | 0-1 | 1-1 | `build(properties?)` |
| engines.execScript / execScriptFile / execAutoFile (3 行) | 2-3 / 1-2 | 3-3 / 2-2 | config 可选 |
| openConsole / showConsole | 0-1 | 0-0 | `(isReset?)` |
| util.morseCode.getCode / getPattern | 1-2 | 1-1 | `(text, timeUnit?)` (文档 timeSpan?) |

保留 29 行 (d.ts 与文档不变):

| API | 实现 | d.ts | 理由 |
|---|---|---|---|
| tasks 的 addTimedTask / addDailyTask / addWeeklyTask / addMonthlyTask / addYearlyTask / addDisposableTask / addCountdownTask / addIntentTask / addBroadcastIntentTask, timers 的 addDailyTask / addDisposableTask / addIntentTask / addWeeklyTask, workManager 的 addBroadcastIntentTask / addDailyTask / addDisposableTask / addIntentTask / addWeeklyTask (18 行) | 0-1 | 1-1 | 新层 `options: NativeObject? = null`, 缺省时以空对象代入, 随后因缺少 path 抛出 (旧实现 AtMost 1 同此); 实际必需, 文档与 d.ts 写必需 |
| colors.alphaDouble / redDouble / greenDouble / blueDouble 与 getAlphaDouble / getRedDouble / getGreenDouble / getBlueDouble (8 行) | 1-2 | 1-1 | 第二个参数 options 只校验 max 取 1 或 255, 不影响结果 (恒为 0..1), 属兼容入口, 不作为用法列出 |
| colors.toString | 0-2 | 1-2, 2-2 | 无参时是对象自身的 toString (返回 "[object Object]"), 不是颜色用法 |
| Mathx.dist | 0-3 | 2-3, 1-2 | 无参返回 NaN (同内置 Math 函数的无参行为), 文档未列 |
| yolo.load | 1-2 | 2-2 | arity [1, 2] 只为缺 options 时给出 YOLO 自己的错误码; 缺 options.component 即抛 "yolo.load requires options.component", 文档与 d.ts 写 `(modelDir, options)` |
| WebSocket.() (no-dts) | 1-2 | 无调用签名 | 调用即 TypeError (须用 new), d.ts 只声明构造签名, 正确 |

### 7.4 ksp 与旧实现的 47 处差异 (2.6 节; Q19 本主题结论)

- colors 19: 新层按类型分重载 (argb 1 / 4; rgb / hsl / hsv 1 / 3; rgba / hsla / hsva 1 / 2 / 4; oklab / oklch 1 / 3-4; toHsva 1 / 2 / 4 / 5; toHsla 1 / 4; toRgb / toRgbString / toHslString / toHsvString 1 / 3; toArgbString / toRgbaString / toHslaString / toHsvaString 1-2), 文档签名一致, d.ts match. 文档缺 rgba / hsla / hsva 的 `(components, alpha)` 两参数重载 (实现与 d.ts 都有), 记为签名级文档补充项, 不属个数矛盾.
- ocr 8 与 barcode / qrcode 4: 文档形态最多 2 个参数 (ocr 的 `(img, options?)` / `(img, region)`, detectAll / recognizeTexts 的 `(image, options?)`), 新层一致, d.ts match.
- http 7: 6.8.0 文档 options / data / files 必需, 新层一致; d.ts 5 行改必需 (7.2), postMultipart(Async) 本已 match. 6.7.x 文档写为可选且旧实现接受只传 url, 登记 Q51 (推荐保持 6.8.0 必需, 已按推荐先行).
- util.java.array 下限 2 与 util.java.toJsArray 上限 2: 与文档一致, d.ts 已改 (7.2).
- Numberx.toFixedNum 1-2 (新层放宽): 文档 `(num, fraction?)` 一致. 全局 detect 2-4: 文档与 selector P1 一致.
- Numberx.ensureNumber (ksp 未声明的 vararg): 新层 0 个参数不抛 (旧 NotEmpty 抛), 与文档 "任意一个 o 不满足时抛出" 不矛盾, 不比, 不改.
- cvt.bytes / fmt.bytes 的 loose / strict (4 处): 元数据 1-n, 函数体自查 1-4, 文档与 d.ts 为 1-4 的重载, match.
- 结论: 新层与文档用法矛盾的 0 处. 可选改进 (不阻塞, 不登记): add*Task 18 行与 yolo.load 若在新层去掉默认值或收紧 arity, 可消去 19 行 narrower 警告, 但缺 path / component 的具体错误会变成通用的参数个数错误.

### 7.5 P2.2 宿主修复项

无 (7.4). 看板不登记 "转 augment" 行.

### 7.6 Q23 与 Ace 重新导入

- `import-autojs6-dts.mjs` (宿主 9fb6274a0): 不再按字母序生成 index.d.ts, 改为沿用 TS 仓库 `declarations/autojs6/index.d.ts` (Ace 现行版本与之逐行一致: 含 `aj6-main-app.d.ts`, mathx / numberx / arrayx 在末尾), 只追加源 index 未引用的核心文件, 引用的文件既不导入也不在 Ace 时告警. 探针文件验证: 只追加一行.
- 重新导入 (宿主工具, TS f5e837a, Ace 9fce0de 都用 HEAD 导出副本): `import-autojs6-dts.mjs` + `generate-editor-assets.mjs` (indices-from-dts, indices, generate-dts, LSP generate-declarations, 四个 verify 与 Ace verify-api-coverage) 后 `--check` 通过. Ace 改动 10 个文件 (8 个 aj6-int 副本, autojs6_indices.js, lib.autojs6.d.ts), index.d.ts 不变; 宿主 autojs6_indices.source.json 只有这些签名变化.
- LSP 分组: 导入前后 generate-declarations 的 manifest 10 组 (core 79, android 5, runtime-node 77, libraries / resources / main-app / jsox 三组 / runtime-bun 各 1) 源文件清单相同, 只有 core 内容哈希变化 (6dcc836a5278 -> f5a9b71eb191).
- Ace 自身校验: `verify-runtime-api-coverage`, `verify-runtime` (LSP 运行时, 与 Gradle verifyAutoJs6LspRuntime 同参数), `generate-language-indices --check`, `ace-builds-lock --check` 通过.

### 7.7 验收

- HEAD 导出副本 (宿主工具 5c6442382 + MATRIX.md af2208173, TS f5e837a, Ace f4f0651, 文档 0b9b58d, Node 990f9e35, Bun 3056b94, KSP 同 7.1): arity match 1195, fail 0, wider 0, narrower 29, vararg 60, unknown 0, no-dts 1; `check.py --matrix --strict-arity --docs-support` (含 Ace) 退出码 0, failures 空. 本会话在副本内重生成的 MATRIX.md 与 P2.1 提交的 af2208173 相同 (dts 列 name 1510 -> 1500, arity 1147 -> 1157 来自本节 d.ts).
- 宿主 HEAD 含 P2.3 的 b027777bc / d41ebd872 (Q18 原型成员入分母), `--matrix` 暂报新 pending 与 MATRIX 过期, 属 P2.3 进行中 (登记 Q54 后重写); arity 部分不变.
- TS: `tools/verify-api-coverage` / `verify-colors` / `verify-compose-declaration-body` / `verify-compose-tsx` / `verify-runtime-declarations --ace` / `verify-terminal` 与 `sync-runtime-declarations --ace --check` 通过 (TypeScript 5.1.3 与 Ace 的 6.0.3).
- TS ROADMAP Phase 7 "WebStorm 补全": TypeScript 语言服务对只引用 index.d.ts 的 JS 文件在全局位置请求补全, aj6-int 的 692 个值全局 (declare var / function / class / enum) 全部出现 (5.1.3 / 6.0.3), 已勾选 (f5e837a); WebStorm 界面未人工打开.
- 文档生成物: P2.1 的 0b9b58d 从包含 c579f54 的 HEAD 全量生成, 已含本节签名.

### 7.8 提交

| 仓库 | 提交 | 内容 |
|---|---|---|
| TS | 3b62d65 | 8 个 aj6-int d.ts 的参数个数 (7.2 / 7.3) |
| TS | f5e837a | docs/ROADMAP.md Phase 7 全局补全核对 |
| 文档 | c579f54 | flow.md / global.md 的 clickWhenStableAfter / clickBoundsWhenStableAfter 签名, console.md trace 签名 |
| 宿主 | 9fb6274a0 | Q23: import-autojs6-dts.mjs 沿用源 index.d.ts, README |
| Ace | f4f0651 | 重新导入 8 个 d.ts, autojs6_indices.js, lib.autojs6.d.ts |
| 宿主 | 3fd931ffb | Ace 补全源 autojs6_indices.source.json |
| 宿主 | (本节所在提交) | 本节, ROADMAP 勾选 P2.2, 附录 C Q23 已落地与 Q51 |

## 8. P1.6 Codex 结构检查脚本改读 exports.json (2026-10-29, 子会话 api-cov-p1-6)

Q42 (a). augment P5.2 (37b93d893) 删除旧 `augment` 包后, 7 个 Codex 结构检查脚本用正则读旧注册写法 (`selfAssignmentFunctions` / `FUNCTION_ASSIGNMENTS` / `FORWARDED`) 取宿主方法集, 读文件即 ENOENT 失败; 其中 6 个在 CI "Script API coverage" 里 (共 8 个脚本). 其余 17 个 Codex 静态脚本在 HEAD 本就通过, 没有同类失败, 未改.

### 8.1 改法

新增 `tools/api-coverage/host-exports.mjs`: 读同一次 `check.py` 运行写出的 `build/reports/api-coverage/exports.json` (`extract_exports.py` 读 KSP `script-modules.json`; 环境变量 `AUTOJS6_API_COVERAGE_EXPORTS` 可指定别的文件), 文件缺失, `dataSource.kind` 不是 ksp, 或比 `script-modules.json` 旧, 都直接失败并提示先运行 `check.py`. 7 个脚本只换宿主方法集的来源, 其余比对 (Node facade / Worker, `sync-*.mjs --check` 的 Bun host-client, Node d.ts, 文档, engine 路由集, 插件清单与打包权限) 不变, 不再读 Kotlin 注册.

| 脚本 | 旧来源 (augment 包) | 新来源 (exports.json) | 数量 |
|---|---|---|---:|
| check-runtime-tts | Tts.kt `FUNCTION_ASSIGNMENTS` | `tts` 的 Functions; 另加 Properties 须在 facade 上 | 31 + 4 |
| check-runtime-speech | Speech.kt / SpeechModels.kt | `speech` / `speech.models` 的 Functions | 10 + 8 |
| check-runtime-epub | Epub.kt | `epub` 的 Functions (仍断言 20) | 20 |
| check-runtime-mail | Mail.kt 注册 + `FORWARDED` (+Async) | `mail` 的 Functions (仍断言 52); Properties 须含 providers / accounts | 52 |
| check-runtime-ai | Ai.kt | `ai` 的 Functions 与 5 个名称相等, Getters 只有 agent, 模块可调用 | 5 |
| check-runtime-ai-agent | AiAgent.kt | 9 个根仍是脚本内清单 (见 Q46), 宿主侧核对 getter `ai.agent`; AgentRun 函数改取 `returnedObjects` (原写死 7 个) | 9 / 7 |
| check-runtime-bridges | OpenCC.kt / Images.kt | `opencc` / `images` 的 Functions; 转换表 `OpenccConversionPlan.kt` 只改路径到 `modules/opencc` (实现表交叉核对, 不是注册) | 33 / 81 |

数量与 Codex 批次报告一致 (tts 31, speech 18, epub 20, mail 52 + 嵌套 5, images 81). 反例 10 个全部失败 (退出码 1): 7 个模块各加一个宿主独有函数 (tts / speech.models / epub / mail / ai / opencc / images), AgentRun 加一个成员, dataSource 改 kotlin, 文件缺失.

### 8.2 CI

- `api-coverage.yml` 不需要改: 8 个脚本本就在 KSP 构建 (199d13444, augment Q111) 与 `check.py --source ksp --matrix --strict-arity` 之后, 同一步骤 "Test registration extraction and compare public surfaces" 内 (bash -e, check.py 失败即停), exports.json 用默认输出目录, 与脚本默认路径一致. 6 个失效步骤随脚本修复恢复.
- `act` 未安装, 按工作流顺序在 HEAD 导出副本内逐步骤运行等价命令 (8.3).
- 观察 (未改): 触发路径 `runtime/api/augment/**` 已无文件, 无害; bridges 与其余 16 个脚本仍只在本地运行 (REVIEW R1).

### 8.3 HEAD 导出副本与结果

- 快照: 宿主 1a86f2268 (本条目脚本提交; 其后的 P2.2 / P2.3 提交由其自行验收), 文档 ffd5c2c (P1.4 生成 MATRIX.md 之后, P2.1 提交之前), TS 496aaf8, Ace 9fce0de, Node 990f9e35, Bun 3056b94, Macaw cf32b5e, Folio 34658be, Stamp 59b8b37; 按 CI 布局并排放在 `E:/.cache/aj6-p1-6/`, 用 `git -c core.autocrlf=false archive` 导出 (见 8.4). 副本内 `gradlew.bat :app:kspAppDebugKotlin --max-workers=2 --no-daemon` 2 min 31 s.
- CI 步骤 "Test registration extraction and compare public surfaces" (工作目录宿主):
  - `python -m unittest discover -s tools/api-coverage -p 'test_*.py'`: 68 项通过 (跳过 3 项, 与 6.1 相同).
  - `python tools/api-coverage/check.py --source ksp --matrix --strict-arity`: 退出码 0, failures 空, MATRIX.md current, pending 643 / 832 / 791 与基线一致, 策略冲突 0.
  - `node tools/api-coverage/check-runtime-<java-callbacks|tts|speech|epub|mail|flow|ai|ai-agent>.mjs`: 8 个退出码 0.
- 其后的 CI 步骤 (Ace 运行时查询 `generate-runtime-api-queries.mjs --check --host ../AutoJs6` 与 `verify-runtime-api-coverage.mjs --fresh`, TS `sync-runtime-declarations.mjs --check --ace`, `verify-runtime-declarations.mjs`, `verify-api-coverage.mjs`): 5 个退出码 0.
- 全部 Codex 静态脚本: `node tools/api-coverage/check-*.mjs` 22 个与 `test-web-display-stream.mjs` / `test-web-download-stream.mjs` 共 24 个退出码 0; 第 25 个 `verify-surfaces.mjs` 由 check.py 调用 (上条通过).
- 验收: `AUTOJS6_ACE_EDITOR_ROOT=<副本>/AutoJs6-Plugin-Ace-Editor python tools/api-coverage/check.py --matrix --strict-arity --docs-support` 退出码 0 (含 Ace, 1377 个导出; 文档运行时表 3 页 current; Ace 指向副本而非共享签出, 因为共享 Ace 已有 P2.2 的新提交).
- 参考: 同命令改用文档 10424a2 (P2.1 给 mail 47 项加标题) 时唯一失败为 "MATRIX.md is stale" (doc 列 mention 231 -> 184 / heading 2174 -> 2221, mail 行 doc 9 -> 56), 属 P2.1 在途, 由其重生成.

### 8.4 本机导出的换行符

本机 `core.autocrlf=true` 时 `git archive` 把没有 eol 属性的文件转成 CRLF: Node 的 `node_bridge_sources.cpp` 变 CRLF, 而 Bun 的 `host-client.mjs` 有 `eol=lf` 仍是 LF, bridges 比较两边图像适配器源码文本 ("Portable Node/Bun image adapters drifted") 因此失败. CI (ubuntu) 与共享签出都是 LF, 不受影响. HEAD 导出副本应以 `git -c core.autocrlf=false archive` 导出.

### 8.5 待决

Q46 (`ai.agent` 的 9 个根不在 exports.json, 脚本保留清单) 已按推荐先行, 见 ROADMAP 附录 C.

### 8.6 提交

| 仓库 | 提交 | 内容 |
|---|---|---|
| 宿主 | 163cdc5c3 | ROADMAP 新增 P1.6 条目 |
| 宿主 | 1a86f2268 | host-exports.mjs, 7 个脚本改读 exports.json, README |
| 宿主 | 08ba1e80a | ROADMAP 勾选 P1.6, 附录 C Q42 标已拍板并落地, 登记 Q46 |
| 宿主 | (本节所在提交) | 本节 |

## 9. P2.3 返回对象成员补齐 + Q18 原型成员纳入分母 (2026-10-29, 子会话 api-cov-p2-3)

### 9.1 数据与命令

- 清单与验收都在 HEAD 导出副本 `E:/.cache/aj6-p2-3-head` 中运行 (`git -c core.autocrlf=false archive HEAD`, 兄弟仓库按 CI 布局).
  - KSP `script-modules.json` 由该副本在宿主 5f2403c34 上构建; 之后 app/src/main 未变, 只变了 androidTest.
  - Node 990f9e35, Bun 3056b94.
  - Ace 检查: `AUTOJS6_ACE_EDITOR_ROOT` 指向副本内的 Ace.
- 命令:
  - `python tools/api-coverage/check.py --write-matrix --write-pending-baseline --strict-arity` 重写 MATRIX.md 与基线 (第 1 步加 `--allow-new-pending`, Q54).
  - 验收: `check.py --matrix --strict-arity --docs-support`.
  - 单元测试: `python -m unittest discover -s tools/api-coverage -p 'test_*.py'`.
  - 文档仓库: `node generator/test-runtime-support.js`.

### 9.2 Q18 原型成员 (第 1 步, Q54 - Q56)

- `returned-objects.json` 新增 `prototypes`: 22 个 `@ScriptPrototype` 模块逐个登记.
- `extract_exports.py` 输出 `prototypes` / `prototypesStats`. 矩阵行为 `<module>.<member>`, 字段:
  - kind `prototype-member`;
  - memberSource `script-prototype`;
  - 运行时列与显式导出同样查找, 不适用 `java-prototype-objects`.
- 模块自身的显式导出遮蔽同名原型成员, 不重复计入.
- 不逐个计入的内容 (Q55): 色表, mime 常量, util.versionCodes, WebSocket 常量, 全局 156 个 Java 类代理, 用 `notEnumerated` / `skipConstants` 写明.
- 98 个非脚本 API 的 Java 成员按 exclude 排除 (Q56).
- 矩阵 2739 -> 2943 行:
  - 显式导出 1377;
  - 原型成员 205;
  - 返回对象成员 1362 -> 1361 (修正 TtsUtterance#utterance 误计).
- pending (Node main / Worker / Bun): 643 / 832 / 791 -> 776 / 987 / 949, 已写入基线 (Q54). 按模块与分批的增量见 REVIEW 7.2.

### 9.3 名称级失败 (第 2 步, Q57 / Q58)

| 项 | 第 2 步开始时 (HEAD 副本) | P2.3 结束 |
|---|---|---|
| 返回对象成员 | 1361 | 1248 |
| 返回对象名称级失败 | 352 (文档缺 326, d.ts 缺 72, Ace 无 22) | 0 |
| 返回对象只在 Ace 全部声明组补全 (不算失败) | 476 | 405 |
| 原型成员 | 205 | 205 |
| 原型成员名称级失败 | 68 (文档缺 6, Ace 无 62) | 0 |

返回对象成员减少 113 个:

- Q57 排除 70 个 (`excludeNote` 写明理由, 宿主 b693690b3):
  - Dialog 43: 包装的 android.app.Dialog 的系统回调, 自身注册的监听器实现, setOnBackPressedFromJs.
  - ScriptEngine 10: 引擎生命周期.
  - UiObject 5: 覆写外部基类 AccessibilityNodeInfoCompat 的成员.
  - TimedTask 4: 调度侧.
  - ImageFeatures 2.
  - 其余 6 个对象各 1.
- 大写常量 23 个: `skipConstants` 用于 TimedTask / IntentTask / ScriptEngine / ScriptExecution.
- 静态成员 13 个: TimedTask 的工厂方法, Mat 的计数器与工厂方法.
- EventEmitter 的静态 defaultMaxListeners 4 个: Dialog / EpubReaderSession / WebSocket / TtsUtterance 改由 eventEmitterType 页说明继承成员.
- 接口里的 private fun 3 个: UiObjectActions.performTooltipAction, 属于提取缺陷.

文档 95bf148:

- 17 页补条目.
- Java / Android 转发成员与属性读写方法写成员表 (Q58): Dialog 76 个, TimedTask / IntentTask, ThemeColor, Mat, Flow 事件等待步骤.
- 其余写独立标题.
- uiSelectorType 的 contentValid 在宿主中不存在, 改为实际的 contentInvalid.

d.ts (TS fa26b8b / 8f8ec07):

- 8 个原型模块的 Java 成员在模块类型上显式声明. 默认 Ace 库组不含生成的 Java 声明, 原型成员的 Ace 失败 62 个都源于此.
- App 预设成员写在与空前向类合并的接口里. Ace 的声明生成器只改写空前向类; 若直接在类里加成员, 会使 `App.CHR` 补全丢失, 已由 verifyAutoJs6LspRuntime 发现.
- Color / ZipFileHandle 补 toStringReadable.

探针调整:

- AiSession 的探针改为 `ai.session.().await`. `verify-surfaces.mjs` 把它补全为 `(await ai.session()).`.
- PWritableTextFile 的探针改为类路径. 原探针 `files.open.()` 取第一个重载, 返回 PReadableTextFile.

门禁 (b693690b3):

- 返回对象与原型成员的名称级失败, 以及 `returnedObjectsStats` / `prototypesStats` 的配置问题, 都进入 check.py failures.

### 9.4 Q20 (a) / Q22 (a) 运行时支持表 (第 3 步)

- 文档生成器 (文档 d45c14b):
  - 原型成员计入其模块 (Q22, 不加说明).
  - 类型页的标记块以 `<对象>#` 为根, 显示该对象的成员.
  - 模块页的标记块内另起 "返回对象" 小表, 每个对象一行; Java 对象成员整列 "不适用 (需要 Java 对象)" 只占一格.
- 矩阵的返回对象行新增 `page` 字段, 即对象的第一个文档页.
- 宿主 `docs_support.py`:
  - 行数核对计入原型成员与 `<对象>#` 根;
  - 另核对小表的成员数 `objectRows`;
  - 问题页 (标记写坏) 不再使核对本身报错 (794229e48).
- 三个试点页 (文档 004b35c):
  - image: 91 行; 返回对象 7 个, 共 70 个成员.
  - files: 22 -> 52 行, 含 30 个原型成员.
  - flow: 71 行, 不变.
- 其余页面的标记块 (含 16 个类型页的 `<对象>#` 块) 归 P2.5.

### 9.5 验收

- HEAD 副本: 宿主 794229e48 (MATRIX.md 885000bd2), 文档 e84af64, TS 8f8ec07, Ace aac52af, Node 990f9e35, Bun 3056b94.
- `check.py --matrix --strict-arity --docs-support` (含 Ace) 退出码 0, failures 空:
  - 返回对象 43 个 1248 个成员, 失败 0;
  - 原型成员 22 个模块 205 个, 失败 0;
  - MATRIX.md current, 2830 行 (显式导出 1377, 原型成员 205, 返回对象成员 1248);
  - pending 776 / 987 / 949, 与第 1 步相同: 去掉的返回对象行都是不适用 (java-prototype);
  - pending-baseline.json current;
  - docs-support 3 页 current, 行 214, 返回对象行 70;
  - arity: match 1195, fail 0, narrower 29, no-dts 1, 不变.
- 单元测试 74 项通过 (跳过 3). 文档 `test-runtime-support.js` 10 项通过.
- 在共享宿主工作区跑单测时, RepositoryTest.test_p1_4 会因 P3.2a 未提交的 Node facade (Worker 的 jsox.mathx) 失败. 这是 P3.2a 的预期变化, 已告知; HEAD 副本中通过.
- TS 与 Ace 校验:
  - TS: `tools/verify-*.mjs` 全部通过 (TypeScript 5.1.3 与 Ace 的 6.0.3), `sync-runtime-declarations --ace --check` 在副本中通过.
  - Ace 宿主侧: `generate-editor-assets.mjs --check` 与 5 个 verify / test 通过.
  - Ace 仓库: Gradle `verifyAutoJs6LspRuntime` / `verifyAutoJs6LanguageIndices` / `verifyAutoJs6ApiCoverage` 与 `ace-builds-lock --check` 通过.
- 文档: `normalize-markdown.py --check` changed=0, `auto-generate.py --check` 通过. Offline Docs 同步 2 次 (68bd7d0 / ec87e8c), 版本与 VERSION_BUILD 不变.

### 9.6 提交

| 仓库 | 提交 | 内容 |
|---|---|---|
| 宿主 | b027777bc / d41ebd872 | Q18: prototypes 登记, 提取, 矩阵行, README |
| 宿主 | 642e859b5 | MATRIX.md + pending-baseline.json (Q54), REVIEW 7.2 |
| 宿主 | b693690b3 | Q57 排除, 名称级失败进 failures, 探针, page 字段, docs_support, P3.2a 要的带引号 Worker 键 |
| 宿主 | 56e278420 | Ace 补全源 autojs6_indices.source.json |
| 宿主 | 885000bd2 | MATRIX.md (2830 行) |
| 宿主 | 794229e48 | docs_support 问题页 |
| 宿主 | (本节所在提交) | 本节, ROADMAP 勾选 P2.3, 附录 C Q18 / Q20 / Q22 / Q23 已落地, Q54 - Q58 |
| 文档 | 95bf148 / d45c14b / 004b35c / 49cb020 | 成员条目, 生成器, 试点页表格与 changelog / progress |
| 文档 | 4cd0f07 / e84af64 | 生成物 |
| TS | fa26b8b / 8f8ec07 | 显式成员与 App 接口 |
| Ace | aac52af | 重新导入, 索引与 lib.autojs6.d.ts |
| Offline Docs | 68bd7d0 / ec87e8c | 同步, 版本不变 |

## 10. P2.5 运行时支持表全量插入 + `--docs-support` 进 CI (2026-10-29, 子会话 api-cov-p2-5)

### 10.1 页面与根

- 依据: 宿主 72064c1a8 的矩阵 (KSP), 69 个模块根 1582 行 (显式导出 1377, 原型成员 205) 与 43 个返回对象 1248 个成员.
- 模块根的页: `policy.json` `documentationModules` 的第一页, 没有映射时为同名页 (与文档名称门禁相同). 返回对象的页: 矩阵行的 `page` (returned-objects.json `documentation` 的第一页).
- 结果 83 页, 其中 13 页此前已有表 (P0.5 / P2.3 / P3.2a / P4.2a), 本条目新增 70 页, global 页的根由 `global` 改为 `global species` (species 映射到 global 页, 此前未覆盖):
  - 67 个模块页: automator 页根为 `automator auto`, global 页为 `global species`, 其余一页一根; 27 个返回对象的第一页是模块页, 在该页 "返回对象" 小表 (Q20 (a)).
  - 16 个类型页, 根为 `<对象>#`: agentRunType, appType, canvas, colorType, epubBookType, epubReaderSessionType, flowType, imageWrapperType, injectableWebClientType, injectableWebViewType, mailClientType, shellProcessType, storageType, uiObjectCollectionType, uiObjectType, webSocketType.
- 问题页: 无. 页面都存在, 返回对象的 `documentation` 第一页都存在, 正文没有字面标记; returned-objects.json 未改.
- locks.md 中没有其他会话登记的文档页, 全部 70 页由本条目插入. P3.2b / P4.2b / P3.1a / P4.1a 改矩阵后按规则重生成各自页面的表 (`node generator/runtime-support.js`).
- 插入在 HEAD 导出副本内进行 (`--insert <页>:<根>` 70 次 + 改 global 的 begin 行), 再按字节拷回共享签出; `normalize-markdown.py --check` changed=0.

### 10.2 生成器与门禁

- 文档 49e36a2: `runtime-support.js --require-all` 另查返回对象: 对象的第一页既没有 `<对象>#` 根, 也没有模块根的标记块时记入 `uncoveredObjects` 并失败; 自测加一例 (10 项通过); generator/README 写明宿主以 `--check --require-all` 调用.
- 宿主 6499e3976: `check.py --docs-support` 一律带 require-all (Q69, 已按推荐先行), `docs_support.py` 摘要加 `uncoveredObjects`, `test_docs_support.py` 两例覆盖 (单测总数仍 76), README 的文档表一节与 "In CI".
- CI: `api-coverage.yml` 的 check.py 命令加 `--docs-support`. 工作流已把文档仓库检出到兄弟目录 `AutoJs6-Documentation` (默认分支, 手动运行可用 `documentation_ref`), check.py 默认读兄弟目录; 生成器只用 Node 内置模块, 不需 `npm ci`; Node 24 由 setup-node 提供. 宿主推送前文档仓库须已推送, 否则 CI 读到的默认分支没有这些表而失败 (与 TS / Ace / Node / Bun 的既有顺序约束相同, 供 P7.1).

### 10.3 顺带的签名修正 (文档 38a1239)

- appType: `openSettings` 改为 `launchSettings`. 宿主 `util/App.kt` 枚举只有 `launchSettings()` (`appUtils.launchSettings(packageName)`), d.ts 也是 `launchSettings(): boolean`; 文档写错, 宿主不改.
- color: `colors.rgba` / `hsva` / `hsla` 补 `(components, alpha)` 重载 (数组恰好 3 个分量 + 单独的 alpha), 依据 `ColorsModule.kt` (`componentsOf(..., 3, "rgb")` 等) 与 d.ts; 重载序号改为 1/4 - 4/4 与 1/3 - 3/3. 版本标 `6.2.0`, 与同组重载一致 (6.3.0 起的 `__colors__.js` 已有同一分支).
- 其他签名级差异本条目没有新发现.

### 10.4 验收

- HEAD 导出副本 `E:/.cache/aj6-p2-5-head` (`git -c core.autocrlf=false archive`, 六个仓库按 CI 布局并排). 宿主 KSP 在副本内跑一次 (72064c1a8, `--no-daemon --max-workers=2`), `script-modules.json` 与共享签出的逐字节相同; 其后宿主新增的提交不改 `app/` 的脚本模块 (只有 Node 插件 id 一行), 沿用该产物.
- 快照 B (本条目的验收): 宿主 HEAD 6499e3976 去掉 d268ce3a4 的三个工具文件 (等于 72064c1a8 + 6499e3976, 两者之间其余提交只改 ROADMAP), 文档 6b1267e, TS cfd7bc4, Ace fe92aaa, Node 3f620da0, Bun 976312d (本批开始时). `check.py --matrix --strict-arity --docs-support` (含 Ace, `--source` auto 即 ksp) 退出码 0, failures 空:
  - docs-support: 83 页全部 current, requireAll true, uncoveredRoots 0, uncoveredObjects 0; 表内 2186 行 (模块根 1582 + 16 个类型页的对象成员 604), 返回对象小表 644 个成员 (27 个对象), 差异项 585 行;
  - MATRIX.md current (2830 行), pending-baseline.json current, pending 663 / 806 / 757;
  - 返回对象 43 个 1248 个成员, 原型成员 22 个模块 205 个, 失败 0; 文档名称门禁 heading 1369; arity match 1195, fail 0, narrower 29, no-dts 1, 不变.
- 快照 A (共享 HEAD 全量: 宿主 6499e3976 含 d268ce3a4, Bun ddf607b): 退出码 1, 4 项失败都来自并行条目尚未重生成的派生文件: MATRIX.md 过期; pending-baseline.json 有 7 格不再 pending (P3.2b d268ce3a4 的 Q63 obsoleted 规则 6 格, P4.2b Bun ddf607b 的 global axios 1 格); global 与 util 两页的表过期. 其余 81 页 current, 未覆盖 0 / 0. 按并行规则由 P3.2b / P4.2b 在提交矩阵时 `--write-matrix` / `--write-pending-baseline` 并重生成这两页.
- 单元测试: 共享签出 (含本条目改动, 提交前) 与副本 (HEAD 工具) 均为 76 项通过 (跳过 3).
- 文档: `node generator/test-runtime-support.js` 10 项通过; `normalize-markdown.py --check` changed=0 (169 个文件); `auto-generate.py --check` "Generated documentation is current (169 modules)"; `git diff --check` 无输出. 全量生成与 `--sync-offline` 在副本内进行 (插件 74 个文件, 新增 / 删除 0, 插件 `normalize_offline_docs.py --check` 随同步通过), 生成物按字节拷回共享签出.
- CI 等价: "Test registration extraction and compare public surfaces" 步骤中本条目只改 check.py 一行, 已按上面运行单测与 check.py; 其余 10 个 node 脚本, Ace 与 TS 步骤不读文档表, 输入未变, 未重跑.

### 10.5 待决

- Q69 `check.py --docs-support` 是否要求每个模块根与返回对象都有表: (a) 一律要求, CI 同样执行 (推荐, 已先行) / (b) 另加开关, CI 只校验已有表 / (c) 不要求. 见附录 C.
- 附录 C Q46 / Q51 / Q54 - Q58 标 "已拍板 2026-10-29 (a)" (维护者在调度会话全选推荐项).

### 10.6 提交

| 仓库 | 提交 | 内容 |
|---|---|---|
| 文档 | 38a1239 | appType launchSettings, colors (components, alpha) 重载 |
| 文档 | 49e36a2 | 生成器 --require-all 查返回对象, 自测, README |
| 文档 | a26ef47 | 70 页标记块 + global 根, changelog / progress |
| 文档 | 6b1267e | 生成物 (73 页 HTML / JSON + 搜索索引) |
| Offline Docs | 4d7b117 | 同步 74 个文件, 版本与 VERSION_BUILD 不变 |
| 宿主 | 6499e3976 | check.py / docs_support.py require-all, 测试, README, api-coverage.yml |
| 宿主 | (本节所在提交) | 本节, ROADMAP 勾选 P2.5, 附录 C 七行状态与 Q69 |

## 11. P5.2c 插件契约导出与识别结果对象纳入分母 (2026-10-30, 子会话 api-cov-p5-2c)

### 11.1 新增的 110 行

- Q74 (a): `returned-objects.json` 加 `OcrResult` (kotlin-class `org.autojs.autojs.runtime.api.OcrResult`, 5 个成员 text / label / confidence / bounds / compareTo; producers ocr.detect 与 ocr.mlkit / paddle / rapid 的 detect) 与 `WrappedBarcode` (22 个成员 barcode / value 与 getFormat ... getWifi / getCornerPoints; producers barcode / qrcode 的 detect 与 detectAll). d.ts 探针新增数组元素语法 `.[]` (`ocr.detect.().[]`, `barcode.detectAll.().[]`). Node / Bun 按 java-prototype-objects (Q17 (a)) 记 not-portable; 两个运行时自己的 OcrResult / BarcodeSnapshot 是形态不同的快照, 与其他 Java 对象同样处理.
- Q75 (a): 新来源 `tools/api-coverage/plugin_exports.py` (`policy.json` `pluginExports.compose`, 根目录可由 `AUTOJS6_COMPOSE_UI_API_ROOT` 覆盖), 静态读宿主 `plugin-api/compose-ui-api` 的组件目录 (ComposeUiVocabulary / V1Catalog / ComposeUiV2 / AndroidViewInteropV1): 55 个 NODE 组件记显式导出行 `compose.<Name>` (metadata plugin:compose; Snackbar 是会话命令, 不计), 20 个修饰符记 `ComposeModifier#<名>` (producer compose.modifier), 8 个快捷属性记 `ComposeNode#<名>` (producer 55 个组件工厂 + createElement). 契约源码缺失时来源记 skipped, MATRIX.md / 基线 / 文档表比对记 skipped 且不写入. 行 id 与来源见附录 C Q105.
- Node / Bun: `runtime-policy.json` returnedObjects 把 ComposeModifier 映射到 Node 的全局类型 `RuntimeCompose.Modifier` (`typeScope: "global"`, 宿主 552f2b489) 与 Bun 的 `RuntimeCompose.Modifier`, ComposeNode 映射到 Node compose 模块的 `Node` 与 Bun 的 `RuntimeCompose.Node`. 两个运行时的节点只在 props 中接受快捷名, 没有 `node.w` 这类访问器, 8 行在三列各记 pending (Q104 (a), 已按推荐先行, 交 P3.3 / P4.3).

### 11.2 前后数字

| 项 | 改动前 (宿主 3fa1fc8ad, MATRIX 6ce328d3d) | 改动后 (宿主 e50f26694) |
| --- | --- | --- |
| 矩阵行 (显式导出 / 原型成员 / 返回对象成员) | 2836 (1383 / 205 / 1248) | 2946 (1438 / 205 / 1303) |
| 返回对象 | 43 个 | 47 个 (+OcrResult, WrappedBarcode, ComposeModifier, ComposeNode) |
| pending Node 主线程 / Worker / Bun | 469 / 538 / 558 | 477 / 546 / 566 |
| Node 主线程 bridged / not-portable | 1012 / 1014 | 1087 / 1041 |
| Bun capability-gated / not-portable | 969 / 1011 | 1044 / 1038 |
| 文档列 heading | 2560 | 2670 |
| d.ts 列 name | 1664 | 1774 |
| Ace 列 semantic / static | 843 / 1579 | 898 / 1634 |
| 文档运行时支持表 | 83 页, 表内 2192 行, 返回对象小表 644 个成员 | 85 页 (+composeModifierType, composeNodeType), 2275 行, 671 个成员 |
| 参数个数 match / narrower / unknown | 1201 / 29 / 0 | 1201 / 29 / 55 (组件工厂, 目录不给参数个数) |

- 名称级失败: 改动前后都是 0 (返回对象 47 个, failingMembers 0); 新增 110 行文档均为 heading, d.ts 均为 name 以上.
- pending 增加的 24 格全是 ComposeNode# 的 8 个快捷属性 (每列 8 格), 用 `--allow-new-pending` 写入基线 (宿主 e50f26694).
- Ace 运行时查询 3498 -> 3703 (Node 主线程 / Worker / Bun 各 +55 个组件函数, Node / Bun 句柄各 +20 个修饰符; Ace 81e6fe6).

### 11.3 提交

- 宿主: 25cf178be (附录 C Q104-Q106), efa050e2b (plugin_exports.py, 两个返回对象, `.[]` 探针, ace-completion 映射类型属性), e50f26694 (MATRIX.md 与 pending-baseline.json), 552f2b489 (typeScope), a211654e7 (Ace 索引源).
- TS 6c93f5d (Ocr.Result 与 Barcode.Result 成员). Ace cd6f5f6 (声明副本与补全索引), 81e6fe6 (运行时查询与 typeScope).
- 文档 78a1f70 (dataTypes OcrResult, composeNodeType 快捷属性访问器, 五页运行时支持表, changelog / progress), 62af947 (生成物); Offline Docs 69438ca (版本不变).

### 11.4 验收

- HEAD 导出副本 `E:/.cache/aj6-p5-2c-head` (六个仓库按 CI 布局并排). 宿主 KSP 在 3fa1fc8ad 的副本内跑一次; 其后宿主提交只改 imgui (P5.2f1 并行纳入, 本条目的元数据去掉 imgui, 谁后提交 MATRIX 谁重生成) 与 Rhino 反射调用的异常解包, 不改其余模块的注册.
- 快照 B (本条目的验收): 宿主 d4ca4333f, 文档 62af947, TS 6c93f5d, Ace 81e6fe6, Node a4309516, Bun 5e2f9e1. `check.py --matrix --strict-arity --docs-support` (含 Ace) 退出码 0, failures 空: MATRIX.md 与 pending-baseline.json current, 文档表 85 页 current, 未覆盖 0 / 0; tools/api-coverage 单元测试 92 项通过 (跳过 3).
- 快照 A (共享 HEAD 全量, Node 1e69e422): 退出码 1, 12 项失败都来自并行条目 P3.1b 的 Node 提交 (系统信息族进主线程与 Worker) 尚未重生成的派生文件: MATRIX.md 过期, 基线有 167 格不再 pending, app / autojs / dialogs / engines / installer / memory / notice / plugins / settings / sysprops 十页的表过期. 按并行规则由 P3.1b 提交矩阵时重生成.
- TS: verify-api-coverage / verify-runtime-declarations / verify-colors / verify-compose-declaration-body / verify-compose-tsx / verify-terminal 与 `sync-runtime-declarations.mjs --check --ace` 通过. Ace: `generate-runtime-api-queries.mjs --check` current, `verify-runtime-api-coverage.mjs --fresh` 3703 条通过; Gradle `verifyAutoJs6LspRuntime` / `verifyAutoJs6LanguageIndices` / `verifyAutoJs6ApiCoverage` (Ace 81e6fe6 的副本, `--max-workers=2 --no-daemon`) BUILD SUCCESSFUL.
- 文档: `normalize-markdown.py --check` changed=0 (174 个文件), `auto-generate.py --check` current, `runtime-support.js --check --require-all` 85 页 current, `test-runtime-support.js` 通过; Offline Docs `normalize_offline_docs.py --check` changed=0.

### 11.5 待决

- Q104 新增 pending 一次性计入基线 (已按推荐先行), Q105 行 id 与契约来源 (已按推荐先行), Q106 compose 其余返回对象 (约 42 行, 需拍板). 见附录 C.

## 12. P5.2f-1 imgui 纳入矩阵 / 基线 / 门禁 (2026-10-30, 子会话 api-cov-p5-2f1)

### 12.1 做了什么

- Q115 (a) 拆出的第一步: 宿主模块 imgui (61317ec4d, 插件 R1.1 的 dp / px / density 25eac064a) 的 464 个 KSP 安装项进分母. 463 个公开导出在 Node main / Worker / Bun 三列 pending, 一次性计入 pending-baseline.json (Q122); `imgui.__pluginId` 记 `policy.json` internalExports 与 internal-exports 规则 (Q123); Node / Bun 终态待 P5.2f-2 定 (Q124). 构造出的对象 (会话, DrawList 等) 未登记返回对象, 随 P5.2f-2.
- 名称门禁暂缓 (Q121): `policy.json` 新键 `deferredNameGate` 列 imgui. check.py 不把该根的 "Missing documentation name" 计为失败, verify-surfaces.mjs 把该根 (含 `$imgui`) 的 d.ts / Ace 失败路径记为 `deferred`; `summary.json` `deferredNameGate` 计数 (导出 464, 缺文档名 463, 失败路径 465); 该根无缺口或无导出时这一项本身失败, P5.2f-2 收尾必须删除. MATRIX.md 在状态汇总下写一行备注, doc / dts / ace 列照实记 none.
- `pending-baseline.json` 新增 `decisions` 列表, 重写时保留, `check.py --pending-decision "..."` 可重复追加; 现记 Q104 (P5.2c, ComposeNode# 24 格), Q114 (a) (speech 6 行 x 3 列, P3.2b 写入), Q122 (imgui).
- Q118 (a): runtime-policy.json 新规则 `java-introspection` (util.class / className / getClassName, 理由 java-prototype, 附录 D D-11), 三列各 -3, util 页运行时表随之重生成.
- 文档站原无 imgui 页: 新建最小页 api/imgui.md (标题 + 一句说明 + 生成的运行时支持表), 不进侧栏 / 目录, changelog 只写 util 表 (Q125).
- 顺带修两个原本失败的结构脚本 (与 imgui 无关): `check-runtime-speech.mjs` (在 CI 内) 跳过基线中 Node main 仍 pending 的 speech 行 (Q114 的 6 个函数, 基线去掉后恢复检查); `check-runtime-bridges.mjs` (不在 CI) 为 P3.2b 后 recorder 门面依赖的共享 JS 工具核心补桩.

### 12.2 前后数字

| 项 | 改动前 (宿主 23fa91217, MATRIX e50f26694) | 改动后 (宿主 150b4dbce) |
| --- | --- | --- |
| 矩阵行 (显式导出 / 原型成员 / 返回对象成员) | 2946 (1438 / 205 / 1303) | 3410 (1902 / 205 / 1303) |
| pending Node 主线程 / Worker / Bun | 477 / 546 / 566 | 937 / 1006 / 1026 (imgui 各 +463, Q118 各 -3) |
| not-portable Node 主线程 / Worker / Bun | 1041 / 1044 / 1038 | 1045 / 1048 / 1042 (各 +4: imgui.__pluginId 1, Q118 3) |
| 文档列 none / mention / heading | 8 / 268 / 2670 | 472 / 268 / 2670 |
| d.ts 列 none / name / arity | 9 / 1774 / 1163 | 473 / 1774 / 1163 |
| Ace 列 none / semantic / static | 414 / 898 / 1634 | 878 / 898 / 1634 |
| 文档运行时支持表 | 85 页, 表内 2275 行, 返回对象小表 671 个成员 | 86 页 (+imgui), 2739 行, 671 个成员 |
| Ace 运行时查询 | 3703 | 3703 (imgui 全 pending, 不出查询) |

- 名称级: 文档 heading 1430, missing 463 全在 imgui (暂缓); 查询路径 2138, 失败路径 465 全在 imgui / `$imgui` (暂缓); 返回对象 47 个, failingMembers 0; arity fail 0.

### 12.3 验收

- HEAD 导出副本 `E:/.cache/aj6-p5-2f1-h3` (`git -c core.autocrlf=false archive`, 宿主 23fa91217 + 本条目工具改动 = 5cf22077f, 文档 62af947 + 本条目改动, TS 6c93f5d, Ace 81e6fe6, Node a4309516 (P3.1b 的 1e69e422 之前, 与 P5.2c 快照相同), Bun 5e2f9e1, Bundled Libraries / Macaw / Folio / Stamp HEAD). 宿主 KSP 在副本内跑 (`--no-daemon --max-workers=2`, 不去掉 imgui).
- `check.py --source ksp --matrix --strict-arity --docs-support` (含 Ace) 退出码 0, failures 空; MATRIX.md 与 pending-baseline.json current, docs-support 86 页 current, uncovered 0 / 0.
- 单元测试 96 项通过 (跳过 3); `tools/api-coverage/check-*.mjs` 28 个全部通过 (改动前 bridges / speech 两个失败); Ace `generate-runtime-api-queries.mjs --check` 与 `verify-runtime-api-coverage.mjs --fresh` 通过; TS `sync-runtime-declarations.mjs --check --ace`, `verify-runtime-declarations.mjs`, `verify-api-coverage.mjs` 通过; 文档 `normalize-markdown.py --check` changed=0, `test-runtime-support.js` 通过, `auto-generate.py --sync-offline` 生成 2 个新文件 (imgui.html / imgui.json), 插件资源 +1 / ~4.
- 共享 HEAD 全量 (Node a1f09612) 仍有 P3.1b 尚未重生成的派生文件失败 (与 P5.2c 快照 A 相同, 不属本条目).
- imgui R 系列 (imgui 看板) 之后每加一个名称, 都要重跑 `check.py --write-matrix --write-pending-baseline --allow-new-pending` (新格归 Q122) 与文档 `runtime-support.js`, 否则 check.py 失败; Q124 (b) 可免去基线一步.

### 12.4 提交

| 仓库 | 提交 | 内容 |
|---|---|---|
| 宿主 | 5cf22077f | check.py / verify-surfaces.mjs / runtime_matrix.py 名称门禁暂缓与基线 decisions, policy.json / runtime-policy.json, 两个结构脚本, 测试, README |
| 宿主 | 150b4dbce | MATRIX.md 与 pending-baseline.json |
| 文档 | 5ca5ea7 | imgui 最小页, util 表, changelog / progress |
| 文档 | 4950012 | 生成物 (HTML / JSON / 搜索索引) |
| Offline Docs | ead196f | 同步 5 个文件, 版本不变 |
| 宿主 | (本节所在提交) | 本节, ROADMAP 附录 C (Q83 状态, Q114 / Q115 / Q117 / Q118 / Q120 / Q121-Q125) 与附录 D D-11, P5.2 落点, PLUGIN-APIS 8.2 拆 P5.2f-1 / P5.2f-2 |

## 13. P3.1b Node 系统信息与引擎族 (2026-10-30, 子会话 api-cov-p3-1b)

### 13.1 做了什么

- 本族 121 行 (autojs 21 + autojs.version 7 + 全局 R, app.autojs (Q93 (2)), installer 16, notice 7 + notice.channel 6, workManager 11, engines 8 (D-14), plugins 4 + plugins.tools 4, memory 5, settings 4, sysprops 4, powerManager 2, dialogs.build 1, 全局系统函数 17, Worker 另 getClip / setClip) 在 Node main / Worker 两列全部有显式状态, pending 清零. device / sensors / media 拆到 P3.1c.
- Node 插件: 共享工厂 `__autojs6_create_system` 供主线程与 Worker (CJS / ESM / 嵌套) 共用, 入口 autojs6:autojs / installer / memory / settings / sysprops / power_manager / system; notice 与 dialogs.build, engines (Q131 (a) 宿主视角), plugins.extend* / tools 补齐; d.ts, HOST-API, 能力目录与 9 个新能力 (Q133 (b), 非推荐).
- 宿主: RuntimeSystemBridge 路由与 9 个能力, engines execAutoFile / stopAllAndToast; runtime-policy.json 加 moduleAliases (powerManager -> power_manager, workManager -> tasks), globalModules 加 autojs6:system, 新规则 native-memory-watch (memory.watch, Node 两列, Q134 (a), 附录 D D-22); 结构脚本 check-runtime-system.mjs 接入 CI "Script API coverage"; test_runtime_matrix.py 加 test_p3_1b.
- 运行时形态登记 Q135 (已按推荐先行待确认), 真机发现的 getClip 后台差异与 plugin_tools.user_granted 并入该题.

### 13.2 前后数字

| 项 | 改动前 (宿主 476c17979, MATRIX 150b4dbce) | 改动后 (宿主 2c997b35d) |
| --- | --- | --- |
| 矩阵行 | 3410 | 3410 (不变) |
| pending Node 主线程 / Worker / Bun | 937 / 1006 / 1026 | 825 / 887 / 1026 (本族 112 / 119 -> 0; Bun 待 P4.1b) |
| bridged Node 主线程 / Worker | 1087 / 1018 | 1191 / 1129 |
| local-shim Node 主线程 / Worker | 341 / 338 | 348 / 345 |
| not-portable Node 主线程 / Worker / Bun | 1045 / 1048 / 1042 | 1046 / 1049 / 1042 (memory.watch, D-22) |
| 文档 / d.ts / Ace 列 | none 472 / 473 / 878 | 不变 |
| 文档运行时支持表 | 86 页 | 86 页, 本族 13 页的 Node 两列重生成 |
| Ace 运行时查询 | 3703 (Node main 1002, Worker 941) | 3908 (Node main 1101, Worker 1047) |

- 本族 121 行: Node main bridged 112 / local-shim 8 (plugins 与 plugins.tools) / not-portable 1; Worker bridged 112 / local-shim 7 / not-portable 2 (另 plugins.load, D-14).

### 13.3 验收

- HEAD 导出副本 `E:/.cache/aj6-p3-1b-acc` (宿主 2c997b35d, KSP 取自 23fa91217 (其后 app/ 无改动), Node 08781cee, Bun 5e2f9e1, TS ba92f3a, Ace 345c39a, 文档 b67f671).
- `check.py --matrix --strict-arity --docs-support` (含 Ace, AUTOJS6_ACE_EDITOR_ROOT) 退出码 0; MATRIX.md 与 pending-baseline.json current; 单元测试通过 (跳过 3).
- check-runtime-system.mjs (`{"hostMembers":118,"globals":20,"workerSystem":true,"capabilities":9,"hostApi":true}`) 与 check-runtime-app-shell.mjs 通过; Node 插件 api 测试 42 / 42 (含 test-system-rhino.js 10 项); TS `npm test` 通过; Ace `verify-runtime-api-coverage.mjs --fresh` 通过 (3908 条).
- 唯一失败: check-bun-app-shell.mjs 报 "Bun automation adapters differ from Node sources" (Node 08781cee 改了 Bun 客户端复制的无障碍段), 待 P4.1b 同步并重生成 host-client.mjs. 副本缺 speech / epub / mail / Bundled Libraries 兄弟仓库导致的跳过只与环境有关.
- 真机 968e9f18 (23046RP50C, API 35, 宿主 debug 5935): Rhino 46 / 46, Node main / ESM / 嵌套 Worker 各 46 通过, 未声明能力 7 通过; 装回原包并核对 md5. 报告 [runtime-system-engines-2026-10-30](../dev/runtime-system-engines-2026-10-30.md).

### 13.4 提交

| 仓库 | 提交 | 内容 |
|---|---|---|
| Node 插件 | 1e69e422 | 共享工厂, notice / dialogs.build / engines / plugins 补齐, d.ts, HOST-API, 能力目录与 9 个能力, 桌面对照测试 |
| Node 插件 | a1f09612 | 运行时套件钉住新目录摘要 |
| Node 插件 | d6662d91 | 真机修正 (isAutoJs6 属性, extendAllBut, powerManager device.power, Worker engines 列表冻结, plugin_tools.user_granted), 真机对照脚本 |
| Node 插件 | 08781cee | 无障碍坐标缩放经 __autojs6_accessibility_point_scale (Bun 客户端的副本没有本族时不缩放) |
| 宿主 | ea2e5bad4 | RuntimeSystemBridge, 9 个能力, engines execAutoFile / stopAllAndToast |
| 宿主 | 405d47c33 | runtime-policy.json, check-runtime-system.mjs 与 CI, test_p3_1b |
| 宿主 | 2c997b35d | MATRIX.md 与 pending-baseline.json |
| 文档 | b174541 | 本族 13 页运行时支持表, changelog / progress |
| 文档 | b67f671 | 生成物 (HTML / JSON / 搜索索引) |
| Offline Docs | 6323f87 | 同步生成页, 版本不变 |
| TS | ba92f3a | 同步 Node 运行时声明 |
| Ace | f618e66 | 内置 Node 运行时声明 |
| Ace | 345c39a | 运行时查询表 3703 -> 3908 |
| 宿主 | (本节所在提交) | 本节, 报告, ROADMAP P3.1b 落点 / 附录 C Q131-Q135 / 附录 D D-22, REVIEW 7.1 P3.1b 落点 |

### 13.5 P4.1b Bun 列 (2026-10-30, 子会话 api-cov-p4-1b)

- 本族在 Bun 列 120 行全部有显式状态, pending 清零. Bun 客户端经 sync-system.mjs 同步 Node 共享工厂, 宿主新增 13 个默认关闭的 Bun 授权 (Q136 (a) / Q137 (a)) 与 BunAutomationBridge 映射; runtime-policy.json 加 workManager -> tasks 别名, 7 个命名空间授权, notice / plugins.tools / 全局 current* 的成员授权, native-memory-watch 加 bun 列 (Q140 (a)); check-bun-system.mjs 接入 CI.
- 真机发现两处并修正: 插件工具授权 ID 含下划线被 Bun 插件整体拒绝 (改名 plugintools.control / plugintools.usergranted), WIDTH / HEIGHT 取服务窗口尺寸 (改用整屏真实尺寸). 验收发现 check-bun-js-tools.mjs 按 JS 工具族的 17 个成员核对 host.global, 本族加入 19 个全局系统成员后失败, 已修正.

| 项 | 改动前 (宿主 2c997b35d) | 改动后 (宿主 1bef4d983) |
| --- | --- | --- |
| 矩阵行 | 3410 | 3410 (不变) |
| pending Node 主线程 / Worker / Bun | 825 / 887 / 1026 | 825 / 887 / 909 (本族 Bun 117 -> 0) |
| Bun local-shim / capability-gated / not-portable | 298 / 1044 / 1042 | 313 / 1145 / 1043 (memory.watch, Q140 (a)) |
| 文档运行时支持表 | 86 页 | 86 页, 本族 13 页的 Bun 列重生成 |
| Ace 运行时查询 | 3908 (Bun 924) | 4012 (Bun 1028) |

- 本族 Bun 列 120 行: capability-gated 103, local-shim 15, not-portable 2 (memory.watch Q140 (a), plugins.load D-14).
- 验收: HEAD 导出副本 `E:/.cache/aj6-p4-1b-acc` (宿主 1bef4d983 + 其后的 d4e2bbf0d 检查脚本, KSP 取自 a268b1526 的构建 (其后 app/ 无改动), Node 08781cee, Bun e6b4296, 文档 e1d2dc8, TS f86a8bc, Ace 7b83ecb; 另导出 Bundled Libraries 0e4bd8a, 3-Folio EPUB 34658be, 3-Macaw Speech 1294e22, 3-Stamp Mail 59b8b37 供 CI 同款检查). `check.py --source ksp --matrix --strict-arity --docs-support` (含 Ace) 退出码 0, MATRIX.md / pending-baseline.json current, 运行时支持表 86 页 current; 单元测试 97 个通过 (跳过 3); CI 的 16 个结构脚本全部通过 (check-bun-system.mjs: members 119 / globals 20 / grants 13); Ace generate-runtime-api-queries --check 与 verify-runtime-api-coverage --fresh 通过 (4012 条); TS sync-runtime-declarations --check 与 npm test 通过; Bun host client 测试 75 + 15 通过, 19 个 sync-*.mjs --check 通过.
- 真机 Redmi 22120RN86C (API 33, 宿主 debug 5935): Rhino 49 / 49, Bun main / Worker 各 49 / 49, 只开查询授权的拒绝轮各 16 / 16; 装回原包并核对 md5. 报告 [bun-system-engines-2026-10-30](../dev/bun-system-engines-2026-10-30.md).

| 仓库 | 提交 | 内容 |
|---|---|---|
| Bun 插件 | def82bc | sync-system.mjs, 客户端 createSystemFamily 与授权映射, d.mts, system.test.mjs, CI |
| Bun 插件 | 73b5201 | host-api.md 授权表与 Bun 差异 (Q138 - Q140) |
| Bun 插件 | ef7e030 | engines.all / stopAll / stopSelf 无授权时带 missingCapabilities |
| Bun 插件 | 1383a79 | 插件工具授权 ID 改名跟随 |
| Bun 插件 | e6b4296 | WIDTH / HEIGHT 与 SetScreenMetrics 缩放用整屏真实尺寸 |
| 宿主 | 39e240a14 / d4ca4333f / cb3c277ce | 13 个授权与插件中心开关, BunAutomationBridge 映射, 插件工具授权与 905 s 上限 |
| 宿主 | 81f809d05 | runtime-policy.json, check-bun-system.mjs 与 CI |
| 宿主 | a268b1526 | 插件工具授权 ID 改为 plugintools.control / plugintools.usergranted, 单测按插件正则校验 |
| 宿主 | b757c0f9a | 真机脚本 device_bun_system.py |
| 宿主 | 1bef4d983 | MATRIX.md 与 pending-baseline.json |
| 宿主 | d4e2bbf0d | check-bun-js-tools.mjs 跟随本族的 host.global 成员 |
| 文档 | 7d86e0f / e1d2dc8 | 本族 13 页 Bun 列, changelog / progress; 生成物 |
| Offline Docs | 1dbc566 | 同步生成页, 版本不变 |
| TS | f86a8bc | 同步 Bun host client 声明 |
| Ace | f42f0be / 7b83ecb | 内置 Bun 声明; 运行时查询表 3908 -> 4012 |
| 宿主 | (本节所在提交) | 本节, 报告, ROADMAP P4.1b 落点 / 附录 C Q136-Q140 / 附录 D D-22 的 Bun 列, REVIEW 7.1 P4.1b 落点 |

### 13.6 Q135 改选 (b) (2026-10-30, 子会话 api-cov-p3-1b)

- 维护者 2026-10-30 08:3x 对 Q135 选 (b) (非推荐): getRootMode 返回 Rhino 的 RootMode 对象 (setRootMode 接受), 全局系统函数与 R 同时装到 globalThis (主线程与 Worker 的访问器, 赋值即替换; autojs6:system 保留), notice.channel 省略 id 时用当前脚本全名 (项目为入口文件名, 如 main.js), plugin_tools 隐含 plugin_tools.user_granted. 其余 (a) 项保持.
- 数字: 矩阵, pending 与文档 / d.ts / Ace 列不变 (Node main 825 / Worker 887); Ace 运行时查询表不变 (4012).
- 验收: Node 桌面测试 12 / 12, CI 中的 Node api 测试 181 / 181; 宿主 check-runtime-system.mjs (新增全局安装检查) 与 check-runtime-app-shell.mjs 通过; TS `npm test` 与 Ace `verify-runtime-api-coverage.mjs --fresh` 通过. 真机 968e9f18 (宿主 debug 5935 = fde89093f 导出, Node debug 329 = 6c2fae29 导出): Rhino 51 / 51, Node main / ESM / 嵌套 Worker 各 51 通过, 未声明能力 7 通过; 装回原包并核对 md5.

| 仓库 | 提交 | 内容 |
|---|---|---|
| Node 插件 | 6c2fae29 | 门面, d.ts, HOST-API, 桌面测试, 真机用例 |
| Node 插件 | a4d11a16 | 默认渠道用例比对各自的文件名 |
| 文档 | ab83aaa / e3b658e | changelog 一句, 生成物 |
| Offline Docs | cffced9 | 同步生成页, 版本不变 |
| TS | 4a5b382 | 同步 Node 运行时声明 |
| Ace | a57d699 | 内置 Node 运行时声明 |
| 宿主 | (本节所在提交) | 本节, check-runtime-system.mjs, 报告, ROADMAP 附录 C Q135 / P3.1b 行, REVIEW 7.1 |

### 13.7 P4.1b 返工: Q139 (b), Q135 (b) 跟随, Q161 (a) / Q162 (a) (2026-10-30, 子会话 api-cov-p4-1b)

- 维护者 08:3x 对 Q139 选 (b) (非推荐), Q138 / Q140 确认 (a), Q135 (b) 由 P4.1b 跟随; 09:1x 在本子会话对跟随中的两处 Bun 特有问题选 Q161 (a) 显式安装方法, Q162 (a) 删第二开关.
- Bun 客户端同步 Node 6c2fae29 (RootMode 对象, notice.channel 默认取脚本文件名, plugin_tools 隐含 user_granted); host.global.install(target?) 把全局系统函数与 R 装到 globalThis (不可枚举, 不计入 host.global 成员); Worker 的 exit / stop 经宿主 execution.control 结束整个执行 (不是授权, 每个 broker 都带, stop 带消息时执行以该消息失败); plugintools.usergranted 与其插件中心开关删除, 本族授权 13 -> 12.
- 数字: 本族矩阵行与状态不变 (本族 Bun pending 0; 改动前后都是 capability-gated / local-shim / not-portable, 无新行). 文档运行时支持表与 Ace 运行时查询表不因本节改变.
- 验收 (HEAD 导出副本 `E:/.cache/aj6-p4-1b-rw/acc`: 宿主 262870051, Bun 0207cd7, Node df2f1f5f, 文档 f5fd465, TS 34277c8, Ace 9be5352, 另 Bundled Libraries 0e4bd8a / 3-Folio EPUB 34658be / 3-Macaw Speech 37966b3 / 3-Stamp Mail 59b8b37; KSP 取宿主 e3689a136 + 本节改动的构建, 其后 runtime/ 与 core/ 只改了 Bun 授权与插件中心): 单元测试 102 个通过 (跳过 3); CI 的 16 个结构脚本全部通过 (check-bun-system.mjs: members 119 / globals 20 / grants 12); TS sync-runtime-declarations --check 与 verify-runtime-declarations 通过; Ace verify-runtime-api-coverage --fresh 通过; Bun host client 测试 118 个与 19 个 sync-*.mjs --check 通过, Bun JVM 单测 66 个, 宿主 BunAutomationPolicyTest 13 个通过. `check.py --source ksp --matrix --strict-arity --docs-support` 在该 HEAD 退出码 1, 失败项都不属本族: imgui.ListClipper / listClipper 6 格新 pending (宿主 772cff4c6 起, 未登记基线), device 17 格已不再 pending 而基线未缩 (P4.1c / P3.1c 进行中), api/device.md 与 api/imgui.md 运行时支持表待重生成, 由此 MATRIX.md 与 Ace 运行时查询表 stale.
- 真机 Redmi 22120RN86C (API 33, 宿主 debug 5935, Bun 0.21.0 (296)): Rhino 54 / 54, Bun main / Worker 各 54 / 54, 拒绝轮各 16 / 16, Worker exit(error) / stop() 都在主线程下一次报告前结束整个执行, exit(error) 的错误出现在宿主控制台; 23 个授权与原包已还原, md5 一致. Node a4d11a16 改了默认渠道用例 (各运行时比对自己的文件名) 后, 宿主 a5e9cb96b 让真机脚本跟随 (Rhino 定义 P31B_RUN, Bun 期望单文件 p31b_bun_<run>.js), 用宿主 a5e9cb96b / Bun d500a2d 的 debug 包复测 compare: Rhino 54 / 54, Bun main / Worker 各 54 / 54, 已还原.

| 仓库 | 提交 | 内容 |
|---|---|---|
| Bun 插件 | 0207cd7 | 客户端 (Node 6c2fae29 同步, install, Worker exit 经 execution.control), d.mts, host-api.md, sync-system.mjs, system.test.mjs |
| 宿主 | b2c78ccc5 | execution.control 停止路由, 删 plugintools.usergranted 与开关 (11 语言), BunAutomationPolicyTest |
| 宿主 | 262870051 | check-bun-system.mjs, runtime-policy 注释, device_bun_system.py |
| TS / Ace | 34277c8 / 9be5352 | 同步 Bun 声明 |
| 文档 / Offline Docs | 377ab43 / f5fd465 / 7e0a4c9 | changelog 一句, 生成物 |
| 宿主 | (本节所在提交) | 本节, 报告, ROADMAP 附录 C Q137 - Q140 / Q161 / Q162 与 P4.1b 落点, REVIEW 7.1 |

## 14. P3.1c Node 设备, 传感器与媒体族 (2026-10-30, 子会话 api-cov-p3-1c)

### 14.1 做了什么

- 本族 116 行 (device 86: 显式导出与 Device 原型成员, 含 manufacturers / brands / roms; sensors 21; media 9) 在 Node main / Worker 两列全部 bridged, pending 清零. 设计 Q146 - Q149 全选推荐; Q150 (getTimer) 已按推荐先行待确认.
- Node 插件: 启动快照 NodeDeviceProfile (Build 字段, pageSize, 市场名, manufacturers / brands / roms 判断, 整屏宽高, 传感器表, Q148 (a)); 共享工厂 `__autojs6_create_device` / `__autojs6_create_sensors` 供主线程与 Worker (CJS / ESM / 嵌套) 共用, sensors 为 Rhino 事件发射器 (Q147 (a)), media 的 Rhino 名称进 autojs6:media, Worker 经主线程转发 (Q149 (a)); 插件清单与能力目录 device.identifiers (Q146 (a)); d.ts, HOST-API, 桌面对照 8 项.
- 宿主: RuntimeDeviceBridge 路由 Rhino 的设备查询, device.identifiers 能力 (不被 device 隐含), keepAwake / cancelVibration (deviceRhinoControls, 不进 Bun 的 device.control), keepScreenOn 超时可省, vibrate 时序数组, 传感器 Rhino 名称与 delay 档; 结构脚本 check-runtime-device-media.mjs 接入 CI "Script API coverage"; test_runtime_matrix.py 加 test_p3_1c. runtime-policy.json 无需改动.

### 14.2 前后数字

| 项 | 改动前 (宿主 c5ec70ed3, MATRIX 1bef4d983) | 改动后 (宿主 c20e7a36f) |
| --- | --- | --- |
| 矩阵行 | 3410 | 3412 (+2 为 imgui ListClipper / listClipper, 宿主 772cff4c6, 不属本批) |
| pending 基线 Node 主线程 / Worker / Bun | 825 / 887 / 909 | 754 / 807 / 909 (本族 71 / 80 -> 0; Bun 随 P4.1c 的 5249ee3cc 为 819) |
| MATRIX pending Node 主线程 / Worker | 825 / 887 | 756 / 809 (含 imgui 新增 2 / 2, 不在基线) |
| bridged Node 主线程 / Worker | 1191 / 1129 | 1262 / 1209 |
| local-shim / not-portable Node 主线程 / Worker | 348 / 345, 1046 / 1049 | 不变 |
| 文档 / d.ts / Ace 列 | none 472 / 473 / 878 | none 474 / 475 / 880 (+2 为 imgui) |
| 文档运行时支持表 | 86 页 | 86 页, device / sensors / media 三页重生成 (Bun 列同时随 P4.1c 更新) |
| Ace 运行时查询 | 4012 (Node main 1101, Worker 1047) | 4163 (Node main 1172, Worker 1127; Bun 不变) |

- MATRIX.md 为 HEAD 全量重生成, Bun 列含 P4.1c 已提交的 Bun 代码 (Bun 本族 90 格由 P4.1c 收缩基线).

### 14.3 验收

- HEAD 导出副本 `E:/.cache/aj6-p3-1c-acc2` (宿主 5249ee3cc, KSP 取自共享签出 (5c19eeb10 之后 app/ 的模块无改动), Node 74f6dfa8, Bun d500a2d, 文档 f822e43, TS f964495, Ace 05076a9).
- `check.py --matrix --strict-arity --docs-support` (含 Ace): 本族无失败, MATRIX.md current; 退出码 1 只因 imgui listClipper 的 6 格新 pending (Node main / Worker / Bun 各 2) 与 api/imgui.md 运行时表 (宿主 772cff4c6, 不属本批); policyConflicts 3 为 Bun 的 device.getDensity / getHeight / getWidth 映射未使用 (P4.1c). api-coverage 单测 103 通过 (跳过 3).
- check-runtime-device-media.mjs (`{"hostMembers":146,"device":86,"sensors":21,"media":9,"sensorNames":12,"deviceRoutes":28,"hostApi":true}`) 通过; Node 插件 CI 的 JS 测试 189 / 189 (含 test-device-media-rhino.js 8 项); Ace `verify-runtime-api-coverage.mjs --fresh` 通过 (4163 条); TS `verify-runtime-declarations.mjs --ace` 通过 (以 Bun 0207cd7 组装的 TS 副本; 共享签出的 Bun 已含 P4.1c 的 media.control, 待其同步 Bun 声明). check-bun-device-media.mjs 报 "host-client.mjs differs from Node sensors facade", 待 P4.1c 同步 74f6dfa8; speech / epub / mail / Bundled Libraries 的检查因副本缺兄弟仓库失败, 只与环境有关.
- 真机 BH900ASK9E (Sony G8441, Android 9 / API 28, 宿主 debug 5935): Rhino 28 / 28, Node main / ESM / 嵌套 Worker 各 28 通过, 未声明能力 9 通过; 装回原包并核对 md5. 报告 [runtime-device-media-2026-10-30](../dev/runtime-device-media-2026-10-30.md).

### 14.4 提交

| 仓库 | 提交 | 内容 |
|---|---|---|
| 宿主 | bb01a8323 | RuntimeDeviceBridge, device.identifiers, keepAwake / cancelVibration, 传感器 Rhino 名称与 delay, RuntimeDeviceBridgeCapabilityTest |
| Node 插件 | 3250fa46 | 启动快照, device / sensors / media 共享工厂与 Worker, 能力目录与套件钉住, d.ts, HOST-API, 桌面对照测试 |
| Node 插件 | 38a6c666 | d.ts 的 autojs6:media 入口 |
| Node 插件 | 74f6dfa8 | register 先查 sensors 能力, 真机对照脚本 tools/nodejs/api/device-media |
| 宿主 | 0908a44d2 | check-runtime-device-media.mjs 与 CI, test_p3_1c |
| 宿主 | c20e7a36f | MATRIX.md 与 pending-baseline.json |
| TS | f964495 | 同步 Node 运行时声明, node-runtime-smoke.ts |
| Ace | e74c1e8 / 05076a9 | 内置 Node 运行时声明, 运行时查询表 4012 -> 4163 |
| 文档 | 9028c38 / f822e43 | 三页运行时支持表, changelog / progress, 生成物 |
| Offline Docs | 04c53cd | 同步生成页, 版本不变 |
| 宿主 | (本节所在提交) | 本节, 报告, ROADMAP P3.1c 落点与 P3.1 勾选 / 附录 C Q146 - Q150, REVIEW 7.1 P3.1c 落点 |

### 14.5 P4.1c Bun 列 (2026-10-30, 子会话 api-cov-p4-1c)

- 本族 116 行 (device 86, sensors 21, media 9) 在 Bun 列全部 capability-gated, pending 清零. Bun 客户端经 sync-device-dialogs.mjs / sync-sensors.mjs 同步 Node 共享工厂 (3250fa46 / 186fcade / 74f6dfa8), 插件把 Rhino 的同步成员写进 device-info.json (BunDeviceProfile), 宿主新增 3 个默认关闭的 Bun 授权 device.identifiers / media.control / media.library (Q152 (a) / Q153 (a)) 与 BunAutomationBridge 映射; runtime-policy.json 加 23 条 bun.memberCapabilities, 删 device.width / height / density 别名及随之无用的 3 条成员授权; check-bun-device-media.mjs 接入 CI. 设计 Q151 - Q155 全选推荐 (Q155 为保持现状).
- 真机发现: device.info 授权的存储键是 device_info (早于点号命名), 真机脚本按此映射. P3.1c 在 74f6dfa8 把 register 改为先查 sensors 能力 (P4.1c 建议), Bun a0cec4c 随之同步并复测.

| 项 | 改动前 (宿主 d4a7fdc2d, 基线 1bef4d983) | 改动后 (宿主 be312cd9d) |
| --- | --- | --- |
| 矩阵行 | 3410 | 3412 (+2 为 imgui ListClipper / listClipper, 宿主 772cff4c6, 不属本批) |
| pending 基线 Bun | 909 | 819 (本族 90 -> 0: device 61, sensors 20, media 9) |
| MATRIX pending Bun | 909 | 821 (含 imgui 新增 2, 不在基线) |
| Bun local-shim / capability-gated / not-portable | 313 / 1145 / 1043 | 313 / 1235 / 1043 |
| 策略冲突 | 0 | 0 (中间 3 条: 删别名后 device.getDensity / getHeight / getWidth 的成员授权未使用, be312cd9d 删去) |
| 文档运行时支持表 | 86 页 | 86 页, device / sensors / media 三页的 Bun 列随 P3.1c 9028c38 重生成 |
| Ace 运行时查询 | 4163 (Bun 1028) | 4253 (Bun 1118) |
| Bun 授权 (全开) | 59 + execution.control | 62 + execution.control = 63, 插件上限 64 |

- 本族 Bun 列 116 行: device.control 48, device.info 27, device.audio 9, device.identifiers 2, sensors.control 21, media.control 8, media.library 1. 标识符路由与 keepAwake / 指针位置的第二授权 (device.identifiers / device.power) 不进矩阵, 写在 host-api.md, 由拒绝轮覆盖.
- 验收: HEAD 导出副本 `E:/.cache/aj6-p4-1c-head4` (宿主 a2985a2b6 + be312cd9d 的 runtime-policy.json, KSP 取自 10:00 的构建 (其后 app/ 的 runtime 源码无改动), Node 4d3a18f6, Bun a0cec4c, 文档 567f906, TS 973963b, Ace dfa5e9e). `check.py --matrix --strict-arity --docs-support` (含 Ace): 本族无失败, 策略冲突 0, 缺能力名 0, `--write-matrix` 后 MATRIX.md 只有策略冲突一行变化 (随本节提交); 退出码 1 只因 imgui listClipper 的 6 格新 pending (Node main / Worker / Bun 各 2) 与 api/imgui.md 运行时表 (宿主 772cff4c6, 不属本批, 同 14.3). api-coverage 单测 103 通过 (跳过 3). 结构脚本 check-bun-device-media.mjs (`{"members":{"device":86,"sensors":21,"media":9},"routes":{"device.control":41,"media.control":6,"media.library":1},"declarations":"type-checked","hostApi":true}`), check-bun-sensors / check-runtime-device-dialogs / check-runtime-device-media / check-bun-automation / check-runtime-bridges / check-bun-system / check-bun-js-tools 通过. Ace `generate-runtime-api-queries.mjs --check` 与 `verify-runtime-api-coverage.mjs --fresh` 通过 (4253 条); TS `sync-runtime-declarations.mjs --check --ace` 与 `verify-runtime-declarations.mjs --ace` 通过. Bun host client 测试 124 / 124, sync-device-dialogs / sync-sensors `--check` 对 Node HEAD 通过, types.mts `tsc --strict` 通过; JVM 单测 BunDeviceProfileTest 4 与 BunHostClientModuleTest 7 (Bun e89ed66), 宿主 BunAutomationPolicyTest 13 与 RuntimeDeviceBridgeCapabilityTest 3 (c4208e76e).
- 真机 Redmi 22120RN86C (Android 13 / API 33, 宿主 debug 5935, Bun 0.21.0 (296) debug of a0cec4c): Rhino 28 / 28, Bun main / Worker 各 28 / 28, 只开 device.control 的拒绝轮各 13 / 13 (d500a2d 首轮 28 / 28 与 12 / 12); 7 个授权恢复为 absent, 装回原包并核对 md5. 报告 [bun-device-media-2026-10-30](../dev/bun-device-media-2026-10-30.md).

| 仓库 | 提交 | 内容 |
|---|---|---|
| Bun 插件 | 4c43eed | 17 个同步 Build 字段, device-media.test.mjs 进 CI, host-api.md 小节草稿 |
| Bun 插件 | 975a1ed | deviceCall 与授权映射, device.control 41 个方法, media.control / media.library 路由 |
| Bun 插件 | e89ed66 | BunDeviceProfile 写 device-info.json 的快照与传感器表, JVM 单测 |
| Bun 插件 | d500a2d | 同步 Node 共享工厂, 客户端快照成员与 getter, host.media, d.mts, host-api.md, 测试 |
| Bun 插件 | a0cec4c | 同步 Node 74f6dfa8 的 register 顺序 |
| 宿主 | bad0bb8bf / c4208e76e | media.control / media.library / device.identifiers 授权与插件中心开关 11 语言, BunAutomationBridge 映射, 策略单测 |
| 宿主 | e3689a136 / 8760e52ef | runtime-policy.json 23 条, check-bun-device-media.mjs 与 CI, check-bun-sensors.mjs 跟随 Rhino 层 |
| 宿主 | 62e337a08 | check-runtime-device-dialogs.mjs 比对 Bun 的 device.control / media.control / media.library |
| 宿主 | 5249ee3cc | pending-baseline.json (Bun 909 -> 819) |
| 宿主 | a2985a2b6 | 真机脚本 device_bun_device_media.py |
| 宿主 | be312cd9d | 删 3 条未使用的 bun.memberCapabilities |
| 文档 | 12c10d1 / 567f906 | changelog 的 Bun 一句; 生成物 |
| Offline Docs | befdf5e | 同步生成页, 版本不变 |
| TS | 973963b | 同步 Bun host client 声明, bun-runtime-smoke.ts |
| Ace | dfa5e9e / b463a2a | 内置 Bun 声明; 运行时查询表 4163 -> 4253 |
| 宿主 | (本节所在提交) | 本节, 报告, MATRIX.md 策略冲突行, ROADMAP P4.1c 落点 / 附录 C Q151 - Q155, REVIEW 7.1 P4.1c 落点 |

## 15. P2.4 类型级比对 (2026-10-30, 子会话 api-cov-p2-4)

### 15.1 做了什么

- `script-modules.json` (KSP) 的参数, 返回值与访问器类型经 `tools/api-coverage/type-map.json` 映射为 TypeScript 类型, 由 `dts-types.mjs` 用 TypeScript 检查器与 d.ts 比对, 分级 match / refined / coerced (一致), narrow / wide / error (不一致), unmapped (不比: `kotlin.Any?`, 未声明的类, d.ts 为 any 或泛型). 比对范围: 函数 (含别名与全局位置), 访问器, invoke / construct; `@ScriptInstall` 名称 (626) 与 `@ScriptJavaClass` (4) 没有类型. 规则与映射见 README "Types" 和报告 [api-coverage-types-2026-10-30](../dev/api-coverage-types-2026-10-30.md).
- 门禁: `check.py` 写 types.json 与 summary "types"; `--strict-types` 只拦不安全方向 (参数 error / wide, 返回 error / narrow), 已审例外写 type-map.json `reviewed` (fnmatch 模式, 过期也报失败), 已进 CI (Q187 (a)). 矩阵 dts 列对类型一致且参数个数相符的行记 typed, MATRIX.md 加类型比对汇总行与模块表 "类型 (ksp)" 列.
- Node: 矩阵 Node main 列已覆盖的行按 module / member 比对 `docs/nodejs/types/autojs6-node`, 返回值与访问器取 Promise 解包, 只报告 (runtime-types.json, summary types.node; Q186 (a)). `node-types.gradle.kts` 不生成 d.ts (只校验 73 份手写声明存在与被引用), 不改, augment 附录 C Q3 第二项由宿主门禁承担.
- d.ts 修复 (TS aa62341): 不安全方向 3 处 (engines.stopAllAndToast 返回 number, util.deprecate 不返回值, Flow 选择条件去掉 null), 参数窄 17 处补 null (Q189 (b)). 文档原本已与实现一致, 文档与离线文档不改. Q23 重导入 Ace (0a97709), aj6-int-speech.d.ts 保留捆绑版本 (TS d473c8e 属 3-Macaw, 未随本卡导入).
- 决策: Q186 (a) / Q187 (a) / Q188 (a) (ByteArray 返回按 number[] 算一致) / Q189 (b) (非推荐), Q190 (a) 已按推荐先行待确认.

### 15.2 前后数字

| 项 | 修复前 (TS 973963b) | 修复后 (TS aa62341, 宿主 583ca1b94) |
| --- | --- | --- |
| 比对行 (Rhino d.ts) | 1318: typed 1013 / differs 44 / unmapped 257 / no-dts 4 | 1318: typed 1034 / differs 23 / unmapped 257 / no-dts 4 |
| 参数位置 | match 79 / refined 46 / coerced 9 / narrow 39 / wide 3 / unmapped 1534 | match 81 / refined 63 / coerced 9 / narrow 22 / wide 1 / unmapped 1534 |
| 返回值与访问器 | match 783 / refined 249 / error 2 / unmapped 280 | match 785 / refined 249 / unmapped 280 |
| `--strict-types` 失败 | 5 (Q188 前 7) | 0 (已审 23 处) |
| MATRIX dts 列 | none 533 / name 1774 / arity 1163 / typed 0 | none 533 / name 1690 / arity 319 / typed 928 |
| MATRIX 类型比对行 | - | typed 993 / differs 23 / unmapped 252 (invoke / construct 41 行不是矩阵行) |
| Node d.ts (只报告, Node b77c28cb) | - | 覆盖 1744 行中类型行 1101: typed 735 / differs 70 / unmapped 296; 参数 narrow 35 / wide 4, 返回 narrow 12 / wide 3 / error 18 |

- unmapped 原因: `kotlin.Any?` 1765 个位置, ProxyObject / ProxyJavaObject 39, d.ts any 或泛型 8, Set 1, Throwable 1. no-dts 4 行: toString, ui.__autojs6Tsx, ui.__autojs6TsxFragment, WebSocket.() (同 P2.2).
- 已审 23 处 (type-map.json reviewed): tasks / timers / workManager 的 add*Task 选项 18 处 (null 会被当作空对象, 随后因缺 path / component 抛错, 保持必需, 同第 7 节; Q190 (a)); timers / workManager 的 queryTimedTasks / queryIntentTasks 选项 4 处 (Internal.WorkManager 共享段, 属第十批自动化卡或收尾批); auto.explain 目标 1 处 (wide: Automator.Target 经 Pickup.SingleSelector 收 null, 模块拒绝; 属 P3.4 / P4.4 或收尾批).
- Node d.ts 差异多为 Node 形态 (images.toBytes / compressToBytes 解出 Uint8Array, 12 个设置页启动函数不返回是否启动, util.dpToPx 等也收字符串, 任务选项不收 null), 清单在 runtime-types.json, 供 Node 卡片处理.

### 15.3 验收

- HEAD 导出副本 `E:/.cache/aj6-api-cov-p2-4-head` (宿主 9c46485a8 + 本卡工具, KSP 在副本内由 a5c55974a 生成 (其后 app/ 无脚本模块改动), TS bdaf3da, Ace bc80847, 文档 6e0936c, Node b77c28cb, Bun a33e26c (9b40a39 / b01dbe5 为 P4.4 在途, 宿主策略未跟上, 同 307655f10 的取法)).
- `check.py --source ksp --matrix --strict-arity --strict-types --docs-support` (含 Ace) 退出码 0, failures 空; `--write-matrix` 后 MATRIX.md 与 HEAD 只差类型列与汇总行; arity match 1201 / fail 0 / narrower 29 / vararg 60, pending 基线 current (Node main 680 / Worker 681 / Bun 835), 文档运行时支持表 86 页 current. api-coverage 单测 115 通过 (跳过 3; 含 test_kotlin_types.py 12 项), test-dts-types.mjs 27 例通过 (TypeScript 6.0.3).
- Ace: `generate-runtime-api-queries.mjs --check` (1755 行), `verify-runtime-api-coverage.mjs --fresh`, `verify-api-coverage.mjs` 与宿主 `generate-editor-assets.mjs --check` 通过 (副本内先跑 Ace 的 `:app:generateAutoJs6LspDeclarations`).
- TS: `verify-api-coverage.mjs` (TypeScript 5.1.3 与 Ace 的 6.0.3), `verify-runtime-declarations.mjs --ace`, verify-colors / verify-terminal / verify-compose-tsx / verify-compose-declaration-body 通过. `sync-runtime-declarations.mjs --check --ace` 在 HEAD 报 Node / Bun 运行时声明过期 (Node automation.d.ts 等, 第十批 P3.4 / P4.4 在途同步), 与本卡改的 Rhino 声明无关.
- 文档: `normalize-markdown.py --check` 与 `auto-generate.py --check` 通过 (175 个模块, 文档未改). Node 插件 HEAD 副本 `verifyAutoJs6NodeTypeDeclarations typeCheckNodeTypescriptSmoke` 通过.

### 15.4 提交

| 仓库 | 提交 | 内容 |
|---|---|---|
| TS | aa62341 | 12 个 aj6-int d.ts 的参数 / 返回类型 |
| Ace | 0a97709 | Q23 重导入 12 个 d.ts, autojs6_indices.js, lib.autojs6.d.ts |
| 宿主 | 00d36fb4f | Ace 补全源 autojs6_indices.source.json |
| 宿主 | a5c55974a | 附录 C Q156 / Q157 已决, Q186 - Q190 |
| 宿主 | 583ca1b94 | type-map.json, kotlin_types.py, dts-types.mjs, verify-runtime-types.mjs, 单测, check.py / runtime_matrix.py / verify-surfaces.mjs, README, CI, MATRIX.md, 报告 |
| 宿主 | (本节所在提交) | 本节, ROADMAP P2.4 落点 |

## 16. P3.3 Node UI 族 (2026-10-30, 子会话 api-cov-p3-3)

### 16.1 做了什么

- 本族 384 行 (ui 40, console 56, toast 1, floaty 8, web 4, terminal 20, compose 71, 族内全局 26; 返回对象成员 158) 在 Node main / Worker 两列除 7 行 not-portable (内部名 6, ui.layoutInflater) 外全部 bridged, pending 清零. 设计 Q164 / Q165 / Q166 / Q168 维护者选 (a); Q167 (能力 terminal) 与 Q169 (ui.layoutInflater 排除) 已按推荐先行待确认; Q104 (ComposeNode# 快捷访问器) 与 Q106 (a) (compose 返回对象) 随本批实施.
- 第 0 步 (开工前): imgui 插件 1.13.0 / 1.14.0 的 60 个新名字按 Q122 (a) 计入基线 (宿主 4427aad06, 全列 754 / 807 / 819 -> 814 / 867 / 879), imgui 页运行时表 (文档 cf1a50c / 674b4e7, Offline Docs c678904); 附录 C 落盘 Q150 / Q163 (7d384b19a), 登记 Q164 - Q169 (760879b55).
- 宿主 77e94eb5b: ui 内容视图 (showLayout Activity 的 contentView, 生命周期与 back_pressed 事件, interceptBack), RuntimeUiWindowControls (windowControl, barHeight), RuntimeFloatyWindow (floatyWindow / floatyRawWindow / floatyControl / floatyCloseAll), RuntimeConsoleBridge (isShowing, 11 个窗口控制, configure, rawInput 经 ConsoleImpl.requestInput), ScriptToast.dismissAll 按执行, RuntimeTerminalBridge 与能力 terminal (宿主清单, 不被 shell / shell.root 隐含); 单测 RuntimeConsoleBridgeConfigureTest / RuntimeTerminalBridgeTest / RuntimeUiWindowControlsTest.
- Node 插件 8fc9c801: 共享 UI 工厂 tools/nodejs/api/portable-ui-compose.js (sync-ui-compose.js 同步进 cpp, Bun 由 P4.3 同步) 供主线程与 Worker (CJS / ESM / 嵌套) 共用; autojs6:floaty / web / terminal (只认 autojs6: 名); ConsoleConfigurator 52 成员; 族内全局 15 个 (isUiThread, toastLog 等与小写形式, newWebSocket / newInjectableWebView / newInjectableWebClient, ComposeError); 插件清单 TERMINAL, 能力目录 542 个操作与套件钉住; d.ts (ui / console / toast / compose, 新增 floaty / web / terminal); HOST-API "UI 族的 Rhino 对照"; 桌面对照 test-ui-rhino.js 9 项进 build.yml. fc0ca86b: 真机对照脚本 tools/nodejs/api/ui-family (22 例, 未声明能力轮 7 例), HOST-API 注明 waitFor 先于异步 write 启动.
- 宿主 398ab3930: returned-objects.json 加 ComposeSession 7 / ComposeFloatyWindow 18 / ComposeState 1 / ComposeRef 1 与 ComposeNode 自有 20 成员 (`members`, returned_objects.py 对插件来源合并), runtime-policy.json 的 Node 句柄类型 10 个 (ConsoleConfigurator, FloatyWindow / RawWindow, WebSocket, InjectableWebView / InjectableWebClient 与 compose 四个), 族内全局的成员别名, 单列规则 java-prototype-layout-inflater (Q169, 等价写法 ui.inflate / ui.createView); 结构脚本 check-runtime-ui.mjs 接入 CI "Script API coverage"; test_p3_3, test_p1_4 / test_p1_5 不再保留 ui.layout / console.input 的 pending.

### 16.2 前后数字

| 项 | 改动前 (宿主 307655f10, P3.4 的矩阵) | P4.4 重生成 (宿主 7bb11982c, 已含 Node 8fc9c801) | 改动后 (宿主 398ab3930) |
| --- | --- | --- | --- |
| 矩阵行 | 3470 | 3470 | 3517 (+47 为 Q106 的 compose 返回对象: ComposeNode 自有 20, ComposeSession 7, ComposeFloatyWindow 18, ComposeState 1, ComposeRef 1) |
| pending 基线 Node 主线程 / Worker / Bun | 680 / 681 / 835 | 588 / 589 / 656 | 577 / 577 / 688 (本族 103 / 104 -> 0; Bun +33 为 Q106 新行交 P4.3, -1 为 Q169; P4.3 的 cc3a55f97 后 Bun 577) |
| bridged Node 主线程 / Worker | 1376 / 1375 | 1468 / 1467 | 1628 / 1628 |
| not-portable Node 主线程 / Worker | 1046 / 1049 | 1046 / 1049 | 944 / 947 (-103 为 Java 对象成员改记句柄类型 (Q166 / Q168), +1 为 ui.layoutInflater) |
| local-shim Node 主线程 / Worker | 368 / 365 | 368 / 365 | 不变 |
| 策略冲突 | 无 | 无 | java-prototype-objects shadowed (Node 主线程 / Worker 各 103): 句柄类型先覆盖这些行, 规则的 `*#*` 仍匹配它们, 门禁不拦 (Bun 列随 P4.3 同样 103) |
| 文档列 | mention 268 / heading 2670 | 同左 | mention 274 / heading 2711 |
| d.ts 列 | - (P2.4 前口径) | name 1690 / arity 319 / typed 928 | name 1737 / arity 319 / typed 928 |
| Ace 列 | semantic 898 | 同左 | semantic 945 |
| 文档运行时支持表 | - | - | 重生成 15 页 (ui, console, toast, floaty, web, terminal, global, compose, composeNodeType, 新增对象表的 composeSessionType / composeFloatyWindowType / composeStateType, webSocketType, injectableWebViewType / injectableWebClientType), 门禁 89 页 current |
| Ace 运行时查询 | - | 4736 (Node main 1291, Worker 1287, Node 句柄 308, Bun 句柄 308) | 5079 (Node main 1376, Worker 1373, Node 句柄 466, Bun 句柄 322) |

- 本族 158 行返回对象成员: ConsoleConfigurator 52, WebSocket 24, FloatyWindow / RawWindow 各 12, InjectableWebView 1, InjectableWebClient 2, ComposeNode 28, ComposeSession 7, ComposeFloatyWindow 18, ComposeState / ComposeRef 各 1. not-portable 7 行: ui.__autojs6Tsx / __autojs6TsxFragment / __inflate__ / __widgets__ 与全局 __autojs6Tsx / __autojs6TsxFragment (internal-exports), ui.layoutInflater. canvas 没有运行时模块, Canvas# 54 行维持 java-prototype-objects (D-11).
- 7bb11982c (P4.4) 以 Node 8fc9c801 重生成时先计入本族 92 / 92 格; 398ab3930 再减 11 / 12 (族内全局别名, Worker 的 ui.layout) 并写入 Q106 / Q166 / Q168 / Q169 的行.

### 16.3 验收

- HEAD 导出副本 `E:/.cache/aj6-api-cov-p3-3-head` (宿主 398ab3930, KSP 取自 77e94eb5b 的构建 (其后宿主源码只有 Bun 桥接改动), Node 8fc9c801 + fc0ca86b 的 ui-family, Bun b01dbe5, 文档 ab68155, TS d1f4cad, Ace 072c938).
- `check.py --source ksp --strict-arity --matrix --docs-support` (含 Ace): 退出码 0, failures 0; MATRIX.md current, pending 577 / 577 / 688 = 基线, 缺理由 0, 缺能力名 0, 策略冲突 2 (16.2 的 shadowed); 文档运行时表 89 页 current, 未覆盖根 / 对象 0; arity fail 0. api-coverage 单测 117 通过 (跳过 3).
- check-runtime-ui.mjs: `{"hostMembers":224,"consoleFacade":14,"globals":15,"hostRoutes":66,"capabilities":["terminal"],"hostApi":true}`.
- Node 插件 (副本为 LF 导出; 共享签出是 CRLF, extract.js 类测试须在 LF 导出上跑): build.yml 的 4 组 JS 测试 137 / 16 / 3 / 61 全部通过 (含 test-ui-rhino.js 9 项), test-ui-compose.js / test-webview.js 通过, d.ts 探针 `tsc --strict` 通过.
- TS `tools/sync-runtime-declarations.mjs --check --ace` (94 份声明, changed 0) 与 `tools/verify-runtime-declarations.mjs --ace` 通过; Ace `tools/ace-lsp/verify-runtime-api-coverage.mjs --fresh` 通过 (5079 条).
- 宿主: 合并 HEAD 的副本编译, 3 个新单测与相关单测通过 (77e94eb5b).
- 真机 968e9f18 (Xiaomi 23046RP50C, Android 15 / API 35; 宿主 debug 5935 与 Node 2.0.0 (340) debug, 借用 P3.4 的构建: 宿主 e2d824545 导出, P3.3 的 12 个宿主源文件与 77e94eb5b 逐字节一致; Node 8fc9c801 + P3.4 的 threads 两行): Rhino 19 / 19, Node main / ESM / 嵌套 Worker 各 22 / 22 (另 3 例 Node 专有), 未声明能力 7 / 7, 最终用例连续两轮通过; 装回原包 (宿主 5935, Node 1.32.0 (292)) 并核对 md5. 报告 [runtime-ui-2026-10-30](../dev/runtime-ui-2026-10-30.md).

### 16.4 提交

| 仓库 | 提交 | 内容 |
|---|---|---|
| 宿主 | 4427aad06 | 第 0 步: imgui 60 名按 Q122 (a) 计入基线, MATRIX.md |
| 文档 | cf1a50c / 674b4e7 | 第 0 步: imgui 页运行时表; 生成物 |
| Offline Docs | c678904 | 第 0 步: 同步生成页, 版本不变 |
| 宿主 | 7d384b19a / 760879b55 | 附录 C Q150 / Q163 落盘; Q164 - Q169 登记 |
| 宿主 | 77e94eb5b | UI 族宿主路由, 能力 terminal, 3 个单测 |
| Node 插件 | 8fc9c801 | 共享 UI 工厂与 Worker, autojs6:floaty / web / terminal, 能力目录与套件钉住, d.ts, HOST-API, 桌面对照与 build.yml |
| Node 插件 | fc0ca86b | 真机对照脚本 tools/nodejs/api/ui-family, HOST-API 的 waitFor 说明 |
| 宿主 | 398ab3930 | returned-objects.json (Q106), runtime-policy.json, check-runtime-ui.mjs 与 CI, test_p3_3, MATRIX.md 与 pending-baseline.json |
| TS | d1f4cad | 同步 Node 运行时声明 (ui / console / toast / compose / floaty / web / terminal) |
| Ace | b0a3b36 / 072c938 | 内置 Node 运行时声明; 运行时查询表 4736 -> 5079 |
| 文档 | ab68155 / 9567ec3 | 15 页运行时支持表, changelog / progress (zh-Hans); 生成物 |
| Offline Docs | a0f2265 | 同步生成页, 版本不变 |
| 宿主 | (本节所在提交) | 本节, 报告, ROADMAP P3.3 勾选与落点 / 附录 C Q104 / Q106 / Q164 - Q169, REVIEW 7.1 P3.3 落点 |

### 16.5 P4.3 Bun 列 (2026-10-30, 子会话 api-cov-p4-3)

- 本族 384 行 (口径同 16.1) 在 Bun 列除 7 行 not-portable 外全部 capability-gated 或 local-shim, pending 清零. Bun 客户端经 sync-ui-compose.js / sync-ui-family.mjs 同步 Node 8fc9c801 的共享 UI 工厂, uiCall 把 Node 能力名映射为 Bun 授权, 授权关闭时访问宿主前 reject NOT_GRANTED 并带 missingCapabilities. 宿主新增 4 个默认关闭的 Bun 授权 terminal.control / floaty.control / floaty.raw / floaty.touch (Q3 (a) / Q173 (a)) 与 BunAutomationBridge / broker 映射, Bun 授权上限 64 -> 128 (Q170 (a)); runtime-policy.json (bun) 加 namespaceCapabilities terminal / floaty, memberCapabilities 10 (floaty 8, web.newInjectableWebView / newInjectableWebClient), returnedObjects 10 个句柄的 Bun 映射; check-bun-ui-family.mjs 接入 CI. 设计 Q170 - Q174 维护者全选 (a).
- 分三步落矩阵: console 输出 (D-10, 401a16e9c), toast 族 (Q172 (a), 63b4c3c71), 其余 (cc3a55f97); console 窗口控制 13 格由 P4.4 的 7bb11982c 以 Bun b01dbe5 重生成时计入.
- 真机发现: ui.toast 授权的存储键是 ui_toast, 真机脚本按此映射 (首轮拒绝轮 toast.dismissAll 因此误放行, 修正后通过); 首轮合并运行时 Worker 全部缺失, 原因是共享 facade 的 WebSocket cancel 在失败的连接上递归, Bun 进程退出 (Node 4ed3a670 修复, Bun 22a2035 同步后复测通过).

| 项 | 改动前 (宿主 4427aad06, P3.3 第 0 步) | 改动后 (宿主 cc3a55f97, HEAD 7e559a409 不变) |
| --- | --- | --- |
| 矩阵行 | 3470 | 3517 (+47 为 P3.3 的 Q106 compose 返回对象) |
| pending 基线 Bun | 879 | 577 (本族 156 -> 0; 其余为 P4.4 自动化族, P3.3 净增 32) |
| Bun local-shim / capability-gated / not-portable | 313 / 1235 / 1043 | 376 / 1617 / 947 (not-portable -103 为句柄类型, +1 为 ui.layoutInflater, +6 为 P4.4 的 timers) |
| 策略冲突 | 无 | java-prototype-objects shadowed (bun, 103), Node 两列同 (16.2) |
| 文档运行时支持表 | - | 89 页 current; console / keys / toast / global 与 UI 族 13 页的 Bun 列重生成 |
| Ace 运行时查询 | 4253 (本批前两步) / 5079 (P3.3 后) | 4293 / 5289 |
| Bun 授权 (全开) | 62 + execution.control = 63, 上限 64 | 72 + execution.control = 73, 上限 128 (本批 4 个, 其余为 P4.4) |

- 本族 Bun 列 384 行: ui.compose 127, console.control 117, ui.layout 42, terminal.control 20, floaty.control 18, floaty.raw 13, ui.toast 11, clipboard.control 1, local-shim 28, not-portable 7.
- 验收: HEAD 导出副本 `E:/.cache/aj6-api-cov-p4-3-head` (宿主 7e559a409 的 docs / tools / app/src, KSP 取自 5bdb4d56a 的构建 (其后宿主 app/src 无改动), Node 4ed3a670, Bun 37a5f5c, 文档 ca10c2e, TS be281ba, Ace c5009db). `check.py --matrix --strict-arity --docs-support` (含 Ace): 退出码 0, failures 0, pending 577 / 577 / 577 = 基线; api-coverage 单测 119 通过 (跳过 3). CI 的 23 个结构脚本全部通过, 其中 check-bun-ui-family.mjs `{"members":{"ui":35,"floaty":8,"web":7,"terminal":20},"handles":{...11 个句柄...},"routes":{"terminal":15,"floaty":4},"declarations":"type-checked","hostApi":true}`; check-bun-app-shell / check-bun-js-tools / check-runtime-system 三个原先失败 (Bun 未同步 Node 0e1e6d14, 两个结构脚本期望过期), 由 Bun 37a5f5c 与宿主 7e559a409 修正. Bun host client 测试 146 / 146; TS `sync-runtime-declarations.mjs --check --ace` (94 份, changed 0) 与 `verify-runtime-declarations.mjs --ace` 通过; Ace `generate-runtime-api-queries.mjs --check` 与 `verify-runtime-api-coverage.mjs --fresh` 通过 (5289 条). 宿主单测在合并 HEAD 的副本通过: BunAutomationPolicyTest / BunConsoleInputTest / RuntimeTerminalBridgeTest / StaticReferenceGuardTest (5bdb4d56a), BunConsoleOutputTest / BunRuntimeContractTest (6c2b19446 / 2a948fa6a); Bun `:app:testDebugUnitTest` 与 verifyApiArtifacts 通过 (a33e26c).
- 真机 968e9f18 (Xiaomi 23046RP50C, Android 15 / API 35; 宿主 debug 5935 (5bdb4d56a 的 HEAD 副本构建), Bun 0.21.0 (296) debug of 22a2035): Rhino 22 / 22, Bun main / Worker 同一次执行各 22 / 22, 只开 floaty.control / shell.control 的拒绝轮各 13 / 13 (首轮 Bun 802beee: main / Worker 分开运行各 22 / 22, 拒绝轮 13 / 13); 10 个授权恢复为 absent, 装回原包 (宿主 5935, Bun 0.2.5 (118)) 并核对 md5. 报告 [bun-ui-2026-10-30](../dev/bun-ui-2026-10-30.md).

| 仓库 | 提交 | 内容 |
|---|---|---|
| 宿主 | 0abd12f79 / 90f5bb5ca | 附录 C Q170 / Q171; Q172 - Q174 |
| 宿主 | bcabd0452 | Q170: 契约上限 128, 旧插件 64, offeredCapabilities, 单测 |
| 宿主 | 6c2b19446 | BunConsoleOutput (console.control 的输出不计调用上限, 单条 8 MiB), 单测 |
| Bun 插件 | d632532 | host.console 输出 11 与全局 console 名 9, console-output.test.mjs 进 CI |
| 宿主 | 401a16e9c | 基线与 MATRIX (Bun 879 -> 847) |
| Bun 插件 | f96f2b8 | toast 族: host.toast 的 Rhino 参数与 dismissAll, host.global 的 toastXxx 10 与 isUiThread, toast-family.test.mjs 进 CI |
| 宿主 | 2a948fa6a / 4b2e74244 / 63b4c3c71 | broker 的 toast (去上限, forcible, dismissAll); 开关文案 11 语言; runtime-policy 与基线 (Bun 847 -> 835) |
| Bun 插件 | a33e26c | AAR 上限 128, PluginInfo 声明上限 |
| Bun 插件 | b01dbe5 | console 窗口控制 12, configure, rawInput / input |
| 宿主 | 5bdb4d56a | terminal.control / floaty.control / floaty.raw / floaty.touch 与 console.rawInput, 插件中心开关 11 语言, 策略单测 |
| Bun 插件 | 802beee / fba1cb3 | 同步 Node 8fc9c801 的共享 UI 族, uiCall 授权映射, Bun 自带 WebSocket, ui-family.test.mjs 进 CI, d.mts 与 host-api.md; HostCapability 加 4 项 |
| Node 插件 | 4895ff4d | sync-ui-compose.js 的 Bun 段落边界 |
| 宿主 | cc3a55f97 | runtime-policy.json, check-bun-ui-family.mjs 与 CI, test_p4_3, MATRIX.md 与 pending-baseline.json (Bun 688 -> 577) |
| 宿主 | 14943b3a6 | 真机脚本 device_bun_ui.py |
| Bun 插件 | b01cc86 / 22a2035 / 37a5f5c | 同步 Node 079579ec (P3.4 请求), 4ed3a670 / 370f4088 / c54d4592, 0e1e6d14 |
| 宿主 | 7e559a409 | check-bun-js-tools.mjs 与 check-runtime-system.mjs 的过期期望 |
| 文档 | 0933341 / 4edcb9b, fbdb103 / 555777b, b5def97 / 9c4fb16 | console / keys, toast / global, UI 族 13 页的运行时表与 changelog / progress (zh-Hans); 生成物 |
| Offline Docs | 9a9bb42 / 772cc3e / 7610517 | 同步生成页, 版本不变 |
| TS | 836ae70 / be281ba | 同步 Bun host client 声明 |
| Ace | 6700992 / cd85152, 8d0877c / c5009db | 内置 Bun 声明; 运行时查询表 4253 -> 4293, 5079 -> 5289 |
| 宿主 | (本节所在提交) | 本节, 报告, ROADMAP P4.3 落点, REVIEW 7.1 P4.3 落点 |

## 17. P3.4 Node 自动化族 (2026-10-30, 子会话 api-cov-p3-4)

### 17.1 做了什么

- 本族 (automator / auto / keys / ocr / barcode / qrcode / yolo / YoloDetector#, threads / continuation / timers / VolatileDispose# / 全局 sleep / wait, selector 全局 / Flow#, events 的 Q18 原型行) 在 Node main / Worker 两列 pending 清零, 只剩 Flow#await / Flow#sync (Q31). 设计 Q175 - Q178 全选推荐; Q179 (Promise 形态) 已按推荐先行待确认; 真机发现的 threads.pool 差异登记 Q180.
- Node 插件: 共享工厂 `__autojs6_create_automation` 供主线程与 Worker (CJS / ESM / 嵌套) 共用, 入口 autojs6:automator / auto / threads / continuation / timers / yolo / selector / qrcode, keys / ocr / autojs6:events / autojs6:system 补 Rhino 成员, 本族 Rhino 全局装到 globalThis (Q177 (a)); 能力目录 31 个操作与 4 个能力; d.ts (automation.d.ts 等), HOST-API, 桌面对照 18 项, 真机对照脚本 tools/nodejs/api/automation.
- 宿主: RuntimeAutomationBridge (automator / auto / yolo) 与 NodeAutomationObservers (events 新会话), 能力 accessibility.service / events.key.intercept / events.touch (不被父能力隐含, Q178 (a)) 与 yolo; runtime-policy.json 加 returnedObjects VolatileDispose / YoloDetector 与 wait.async 成员别名; 结构脚本 check-runtime-automation.mjs 接入 CI "Script API coverage"; test_runtime_matrix.py 加 test_p3_4, test_p1_4 去掉已覆盖的三行.

### 17.2 前后数字

| 项 | 改动前 (宿主 a5c55974a) | 改动后 (宿主 307655f10) |
| --- | --- | --- |
| pending 基线 Node 主线程 / Worker | 814 / 867 | 680 / 681 (本族 134 / 186 -> 0, 剩 Flow#await / Flow#sync) |
| 本族行 Node 主线程 / Worker | 187 行, pending 134 / 186 | pending 0 / 0 (Flow# 2 行另计, Q31) |
| Ace 运行时查询 | 4293 (Node main 1172, Worker 1127) | 4577 (Node main 1291, Worker 1287) |
| 文档运行时支持表 | - | 13 页重生成 (automator, barcode, continuation, events, flow, global, keys, ocr, qrcode, threads, timers, uiSelectorType, yolo) |

### 17.3 验收

- HEAD 导出副本 `E:/.cache/aj6-api-cov-p3-4-head` (宿主 a5c55974a + 本批改动, KSP 同副本, Node b77c28cb): `check.py --matrix --strict-arity --docs-support` (含 Ace) 退出码 0; api-coverage 单测通过 (test_runtime_matrix 42 项).
- check-runtime-automation.mjs (`{"hostMembers":180,"globals":29,"eventsPrototype":36,"hostRoutes":31,"capabilities":4,"hostApi":true}`) 通过; Node 插件桌面对照 test-automation-rhino.js 18 项通过 (CI 清单 128 / 128); TS verify-runtime-declarations 与 Ace verify-runtime-api-coverage.mjs --fresh 通过.
- 真机 BH900ASK9E (Sony G8441, Android 9 / API 28, 宿主 debug 5935 = e2d824545, Node debug 340 = 8fc9c801 + 079579ec 的 node_bridge_sources.cpp): Rhino 44 / 44, Node main / ESM / 嵌套 Worker 各 53 通过, 未声明能力 8 通过; 装回原包并核对 md5. 报告 [runtime-automation-2026-10-30](../dev/runtime-automation-2026-10-30.md).

### 17.4 提交

| 仓库 | 提交 | 内容 |
|---|---|---|
| 宿主 | 1468ec8d9 | 附录 C Q175 - Q179 |
| 宿主 | 0feda5cfc | RuntimeAutomationBridge, NodeAutomationObservers, 4 个能力, 单测 |
| Node 插件 | 9736c852 | 共享工厂, Worker 接线, 能力目录, d.ts, HOST-API, 桌面对照测试 |
| Node 插件 | b77c28cb | auto.enable / disable 别名 |
| Node 插件 | 079579ec | threads.pool 的 corePoolSize / maxPoolSize 校验, HOST-API 差异, 真机对照脚本 tools/nodejs/api/automation |
| 宿主 | 307655f10 | MATRIX.md, pending-baseline.json, runtime-policy.json |
| 宿主 | 9c46485a8 / 94c21be6f | check-runtime-automation.mjs, test_p3_4, CI |
| TS | bdaf3da | 同步 Node 运行时声明 |
| Ace | 2285938 / bc80847 | 内置 Node 运行时声明, 运行时查询表 4293 -> 4577 |
| 文档 | efaad8e / 6e0936c | 13 页运行时支持表, changelog / progress, 生成物 |
| Offline Docs | 28fa851 | 同步生成页, 版本不变 |
| 宿主 | (本节所在提交) | 本节, 报告, ROADMAP P3.4 勾选与落点 / 附录 C Q180, REVIEW 7.1 P3.4 落点 |

## 18. P4.4 Bun 自动化族 (2026-10-30, 子会话 api-cov-p4-4)

### 18.1 做了什么

- 本族 (automator / auto / keys / ocr / barcode / qrcode / yolo / YoloDetector#, threads / continuation / timers / VolatileDispose# / 全局 sleep / wait, selector 全局 / Flow#, events) 的 Bun 列 pending 清零, 只剩 Flow#await / Flow#sync (Q31). 设计 Q181 - Q185 全选推荐 (a).
- Bun 插件: sync-automation-family.mjs 同步 Node 共享工厂与声明, familyCall 授权映射, ocr / barcode / qrcode 门面, events 的 Rhino 成员, host.global 的本族 29 个全局 (install() 显式装, Q184 (a)), d.mts, host-api.md.
- 宿主: 6 个 Bun 授权 (accessibility.service / events.touch / events.key.intercept / ocr.control / barcode.control / yolo.control) 与插件中心开关, BunAutomationBridge 路由 (automator / auto 为 accessibility.control 的第二模块, ocr / barcode / yolo, captureScreenImage / dump / explain), runtime-policy.json bun 节, 结构脚本 check-bun-automation-family.mjs 接入 CI, test_p4_4, 真机脚本 device_bun_automation.py.

### 18.2 前后数字

| 项 | 改动前 (宿主 63b4c3c71) | 改动后 (宿主 7bb11982c) |
| --- | --- | --- |
| pending 基线 Bun | 835 | 656 (本族 166 + P4.3 的 console 13; Node 列的 92 / 92 来自 P3.3) |
| 本族行 Bun | 249 行, pending 168 | pending 2 (Flow#await / Flow#sync, Q31) |
| 全列 pending 基线 (Node main / Worker / Bun) | - | 577 / 577 / 577 (验收 HEAD 9ad6b0e3d, 含 P3.3 / P4.3 / P3.4 全部落点) |
| BunAutomationBridge 路由方法 | 295 | 314 |
| Ace 运行时查询 | 4577 (Bun 1158, Bun 句柄 303) | 4736 (Bun 1312, Bun 句柄 308) |
| 文档运行时支持表 | - | 12 页的 Bun 列 (automator, barcode, continuation, events, flow, global, ocr, qrcode, threads, timers, uiSelectorType, yolo) |

### 18.3 验收

- HEAD 导出副本 `E:/.cache/aj6-p4-4-head6` (宿主 9ad6b0e3d, KSP 取 77e94eb5b, 其后宿主源码只改了引擎与插件文件, 无脚本模块改动; Node 4ed3a670, Bun fa46dda, 文档 ca10c2e, TS be281ba, Ace c5009db): `check.py --source ksp --matrix --strict-arity --strict-types --docs-support` (含 Ace) 退出码 0; test_runtime_matrix 45 项通过 (含 test_p4_4).
- check-bun-automation-family.mjs 通过 (成员 automator 55 / auto 38 / keys 12 / ocr 6 / barcode 4 / qrcode 4 / yolo 1 / threads 9 / continuation 4 / timers 4, 工厂路由 37, ocr.control 3 / barcode.control 4 / yolo.control 3); 宿主 BunAutomationPolicyTest 18, RuntimeAutomationBridgeCapabilityTest 6, RuntimeFlowPolicyTest 8 通过; Bun 37a5f5c 的 CI node 测试 140 项通过 (host-client 125 项, 含 automation-family.test.mjs 5); TS verify-runtime-declarations 与 Ace verify-runtime-api-coverage.mjs --fresh 通过.
- 真机 bek749scrwv4wo8h (Redmi 22120RN86C, Android 13 / API 33, 宿主 debug 5935 含 e2d824545, Bun debug 296 = 37a5f5c): Rhino 53 / 53, Bun main / Worker 各 53 / 53, 只开 events.observe 的拒绝轮各 19 / 19; 装回原包并核对 md5, 12 个授权恢复为 absent. 报告 [bun-automation-2026-10-30](../dev/bun-automation-2026-10-30.md).

### 18.4 提交

| 仓库 | 提交 | 内容 |
|---|---|---|
| Bun 插件 | 40ea7e0 | keys 本地键码 12 名, automation-family.test.mjs 进 CI |
| 宿主 | 33ab825c4 | 6 个授权, 插件中心开关 11 语言, 策略单测 |
| 宿主 | 248dc70db | 附录 C Q181 - Q184 |
| 宿主 | 47fa448db | BunAutomationBridge 路由, Broker 路由, 单测 |
| Bun 插件 | 9b40a39 | 同步 Node 自动化族工厂与声明, 授权映射, 门面, 全局, d.mts, host-api.md, 测试 |
| 宿主 | e2d824545 | captureScreenImage / dump / explain (Q185 (a)), check-bun-automation-family.mjs, check-runtime-events.mjs |
| 宿主 | 7bb11982c | runtime-policy.json, MATRIX.md, pending-baseline.json, 附录 C Q185, CI |
| 文档 | a816ace / 8fef88c | 12 页 Bun 列, changelog / progress, 生成物 |
| Offline Docs | ac85b8a | 同步生成页, 版本不变 |
| TS | 26b89d9 | 同步 Bun 声明 |
| Ace | f1f1820 / b79c2f7 | 内置 Bun 声明, 运行时查询表 4577 -> 4736 |
| 宿主 | b23220775 | selector 授权更正为 accessibility.control |
| 文档 / Offline Docs | b003076 / ca10c2e, e0f38ce | uiSelectorType 授权与 changelog 更正 |
| 宿主 | df6f3448c / 1e17429cb | test_p4_4, device_bun_automation.py |
| Bun 插件 | fa46dda | host-api.md selector 授权名 |
| 宿主 | ac3ffd91e | 报告 bun-automation-2026-10-30 |
| 宿主 | (本节所在提交) | 本节, ROADMAP P4.4 落点, REVIEW 7.1 P4.4 落点 |

### 18.5 Q179 (b) / Q180 (a) 落实 (2026-10-30, 维护者 17:4x 拍板, 调度转 api-cov-p4-4)

- Q180 (a): 宿主 Threads.internalPool 改与实际上限比较, maxPoolSize 0 (含缺省) 视为不限, 给出正数时 corePoolSize 更大仍抛错. Rhino / Node / Bun 三方一致.
- Q179 (b), 非推荐: auto.waitFor / automator.waitForService 超时结束整个执行 (与 exit() 相同, Promise 不再落定), keepAlive / timers.keepAlive 返回数字 id (可交给 clearTimeout / clearInterval), continuation.enabled 为 false; 其余照 (a) (loop() 只警告, Worker 的 sleep 为 Promise). Bun 的这些成员同步自 Node 共享工厂, 故改 Node 工厂再同步. Node 的 d.ts, HOST-API 与对照用例随 d656265f 一并更新, 只差 Node 真机复测, 归 P3.5.
- 矩阵: 状态与入口不变, MATRIX.md 与 pending 基线未改.

| 项 | 结果 |
| --- | --- |
| 宿主单测 (HEAD 29537a241) | ThreadsPoolSizesTest 4, CoreRuntimeModulesTest 11 通过 |
| Node 桌面 | test-automation-rhino.js 19 项通过 |
| Bun 桌面 (7f25ae7) | host-client 等 17 个 node --test 文件 100 项通过; sync-automation-family / sync-events / sync-system --check 通过; types.mts 无诊断 |
| 真机 968e9f18 (Xiaomi 23046RP50C, API 35; 宿主 debug 5935 = 29537a241, Bun debug 296 = 7f25ae7), 本地成员对照 | continuation.enabled, timers.keepAlive, threads.pool, threads.pool.noMax, threads.pool.sizes: Rhino 5 / 5, Bun main / Worker 各 5 / 5 |
| 同上, 超时轮 (宿主无障碍服务关闭) | auto.waitFor(1000), automator.waitForService(1000): Rhino 与 Bun main 各 2 / 2, 等待之后的代码与等待之前排的 6 s 定时器都未执行 |
| 同上, 拒绝轮 (只开 events.observe) | Bun main / Worker 各 20 / 20 (新增 timers.keepAlive 返回 number) |
| 设备恢复 | 宿主 md5 742b4497, Bun 118 md5 555d45af, 与记录一致; 12 个授权 absent; 运行目录已删 |
| HEAD 副本 `E:/.cache/aj6-p4-4-head7` (宿主 5102dbe42, KSP 同副本, Node d656265f, Bun 7f25ae7, 文档 894d87b, TS 45a9346, Ace e4d9b63) | `check.py --source ksp --matrix --strict-arity --strict-types --docs-support` (含 Ace) 的唯一失败是 api/imgui.md 的运行时块: 它来自另一会话的 imgui 提交 5102dbe42 新增的矩阵行 (pending 577 -> 626), 文档站尚未同步; 只在副本中重新生成运行时块 (仅该页变化) 后退出码 0. test_runtime_matrix 45 项, check-bun-automation-family, check-runtime-automation, Ace generate --check / verify --fresh, TS sync --check / verify 通过 |

已由收口 B 重跑 (Q194 (a), 第 20 节): Redmi Rhino 54 / 54, Bun main / Worker 各 54 / 54, 拒绝轮 20 / 20; 超时轮含 Bun Worker, 在只读 AVD_API_33 上 6 / 6, 因为 Redmi 的宿主有 WRITE_SECURE_SETTINGS, 走不到超时.

| 仓库 | 提交 | 内容 |
|---|---|---|
| 宿主 | 29537a241 | Threads.internalPool, ThreadsPoolSizesTest, changelog fix 一条, 真机脚本 timeout 阶段 |
| Node 插件 | d656265f | 共享工厂, d.ts, HOST-API, 对照用例, 桌面用例 |
| Bun 插件 | 7f25ae7 | 同步共享工厂与声明, host-api.md, 测试 |
| TS | 45a9346 | 同步 Node / Bun 声明 |
| Ace | e4d9b63 | 内置声明 |
| 文档 | 894d87b | threads.md, changelog, 生成物 |
| Offline Docs | c2ea529 | 同步生成页, 版本不变 |
| 宿主 | (本节所在提交) | 本节, 附录 C Q179 / Q180, ROADMAP P4.4 遗留, REVIEW 7.1, 两份报告, 真机脚本 --cases |

## 19. P3.5 / P4.5 补缺 A (2026-10-31, 子会话 api-cov-p45-bridges)

### 19.1 做了什么

- Q80 (a) colors.theme, Q81 (a) gallery, Q82 (a) files.batchRename: 宿主新桥接模块 theme / gallery / batch_rename, Node main, Worker 与 Bun 共用; gallery 与 batch_rename 经执行级操作客户端 RuntimeOperationClients (open / start / poll / cancel / close), 查看器是事件流, 仿 epub 阅读会话.
- Q114: speech.diarize / detectLanguage / tag 及 Async 与 speech.speaker 的 Node / Bun 入口.
- Q195 (已按推荐先行): 三项能力各一, Bun 3 个授权默认关闭, 插件中心 3 个开关与 11 份文案.

### 19.2 前后数字

| 项 | 改动前 | 改动后 |
| --- | --- | --- |
| pending 基线 (Node main / Worker / Bun) | 626 / 626 / 626 | 574 / 574 / 574 (imgui 572, Flow#await / Flow#sync 2) |
| BunAutomationBridge 路由方法 | 329 | 348 (theme 9, gallery 5, batch_rename 5) |
| Ace 运行时查询 | 5289 (行 1996) | 5478 (行 2052) |
| 文档运行时支持表 | - | color, files, gallery, global, speech 5 页 |

### 19.3 验收

- HEAD 导出副本 `E:/.cache/aj6-api-cov-p45-bridges-head` (宿主 c46a5cef8, KSP 取同一副本的 script-modules.json, 其后宿主只改文档与脚本; Node 8a627e32, Bun 547c215, 文档 83aaaa4, TS d27bff0, Ace 92061b9): `check.py --metadata ... --matrix --strict-arity --strict-types --docs-support` 退出码 0, failures 空, Node d.ts 差异 33 且 unreviewed 0.
- api-coverage 单测 121 例通过 (跳过 3), check-runtime-plugin-family.mjs, check-runtime-speech.mjs, Node 与 Bun 的 CI 结构脚本全部退出码 0, 宿主 5 个相关测试类 32 例全过.
- TS `sync-runtime-declarations.mjs --check` changed 0, verify-api-coverage / verify-runtime-declarations 通过 (declarations 95); Ace `verify-runtime-api-coverage.mjs --fresh` 通过 (queries 6655, 矩阵查询 5478).
- 真机 OnePlus 63751f63 (OPD2413, Android 15, API 35; 宿主 debug 5935, Node debug 356, Bun debug 335, 3-Maple Image 198, Batch Rename 49, 3-Macaw Speech 165): 第二轮 Rhino 78 / 78, Node main 78 / 78, Node Worker 77 / 78, Bun main / Worker 各 78 / 78; 拒绝轮 Node main / Worker, Bun main / Worker 各 20 / 20. Worker 差的一例与第一轮 Rhino 的一例是同一种偶发无应答 (Q197). 证据 docs/dev/runtime-plugin-family-2026-10-31.md.
- 恢复: 宿主, Node, Maple, Batch Rename, Macaw 的 md5 与记录一致, Bun 卸载, 4 个授权 absent, 推送文件已删. Sony QV710AF65F 只装过 Node / Maple / Batch Rename 后原样恢复 (Bun 需要 API 33, 该机 API 31, 且已被 paddle-ocr-v3-p6-1 占用, 未跑用例).

### 19.4 提交

| 仓库 | 提交 | 内容 |
|---|---|---|
| 宿主 | 7b01dc338 | 桥接模块, 授权, 插件中心开关, 单测 |
| 宿主 | b925fc903 / c46a5cef8 | runtime-policy, runtime_matrix, 结构脚本, 真机脚本 |
| 宿主 | 5c9f86933 | MATRIX.md, pending-baseline.json |
| Node 插件 | 8736d145 / 4d1b1682 / 40727858 / 8a627e32 | 共享工厂, colors.theme, speech 成员, d.ts, HOST-API, 桌面用例 |
| Bun 插件 | 90646af / 9e0c5cd / 547c215 | 同步共享工厂与声明, host-api.md, 配额豁免, 测试 |
| TS | c3828a4 / d27bff0 | 同步 Node / Bun 声明 |
| Ace | 685cf30 / 92061b9 | 内置声明, 运行时查询表 |
| 文档 | 01c88de / 83aaaa4 | 运行时 API 新节, 5 页支持表, changelog / progress, 生成物 |
| Offline Docs | 7e519e0 | 同步生成页, 版本不变 |
| 宿主 | (本节所在提交) | 本节, 报告, ROADMAP 落点, 附录 C Q195 - Q197, REVIEW 7.1 |

## 20. P3.5 / P4.5 收口 B 与矩阵清零结论 (2026-10-31, 子会话 api-cov-p35-closeout)

### 20.1 矩阵清零结论

Node main, Node Worker, Bun 三列的 pending 基线都是 574 行, 由下表构成, 此外没有 pending 行. 其余行都已 bridged, local-shim, capability-gated, 或有附录 D 的排除理由.

| 项 | 每列行数 | 归属 | 理由 |
| --- | --- | --- | --- |
| imgui 模块 | 572 | P5.2f-2 (不属本批) | Q115 (a): imgui 看板的 R 系列还在改 API, P5.2f-1 只纳入矩阵, 基线与门禁, 其余等 R 系列稳定后做 |
| Flow#await, Flow#sync | 2 | Q31, Q202 待拍板 | Rhino 里阻塞到流程结束, 运行时 Flow 句柄是 thenable, 用 `await flow`. Q31 的决定是保持 pending 并在句柄补同名成员, P3.4 / P4.4 没有补, 本卡按卡片保持 pending, 由 Q202 决定补或长期保留 |

矩阵里 Q31 只剩这 2 行: 其他 12 个成员已由 P3 / P4 的句柄批次补齐.

后续: Q202 (a) 已补这 2 行, pending 572 / 572 / 572, 只剩 imgui, 见第 21 节.

### 20.2 做了什么

- Q190 (b): add*Task 18 处的 d.ts options 补 null, query*Tasks 4 处与 auto.explain 1 处跟随; Node 门面接受 null; type-map reviewed 撤销; Ace 重导入 (同时带上 TS d473c8e 的 speech).
- Node d.ts 与 runtime-types.json 差异 69 行: 修 36 行, 其余 33 行写进 type-map.json 的 nodeReviewed 并带理由, check.py 的 staleReviewed 同时看这张表.
- Q120 (b): 见附录 C. 另加 process.cwd() 与脚本源目录的文档小节 (文档 e1105d9).
- Q127 (a): 宿主含 Q116 修复后重录 15 例, 共享核心去掉 InvocationTargetException 模拟, 比对去掉 rhinoCrash 分支.
- Q179 (b): Node 真机复测, 修退出信号与定时器两处.
- Q194 (a): Bun 自动化族全量重跑.
- 全局 TODO: Node / Bun 全局, Worker 全局表, 声明改 void 对齐宿主 TS.
- java-prototype-objects: 规则支持 `except`, RawWindow, FloatyWindow, WebSocket, InjectableWebView, InjectableWebClient, ConsoleConfigurator 与 OcrResult 的已桥接成员退出规则, 不再被规则遮蔽 (107 格).
- flow / mail / epub / tts 三列核对: 全部覆盖, Flow 仅 await / sync pending, EpubReaderSession 34 行与 TtsUtterance 23 行是 not-portable. speech 归补缺 A.
- 修补缺 A 留下的过期断言: test-colors, test-automation-rhino, 以及 check-runtime-automation, check-runtime-js-tools, check-bun-js-tools, check-bun-files-data, check-bun-storage.

超出卡片字面的改动: tasks null 与 sysprops null 的门面修正, setIntervalExt 返回数字 id, 新属性 hostCwd, process.exit 清定时器 (Q201), 以及上面的断言修正.

### 20.3 前后数字

| 项 | 改动前 | 改动后 |
| --- | --- | --- |
| pending 基线 (Node main / Worker / Bun) | 626 / 626 / 626 | 574 / 574 / 574 (补缺 A 51 行 + TODO 1 行) |
| Node d.ts 差异 | 69, 未审 69 | 33, 全部 nodeReviewed, unreviewed 0, compared 1234 |
| type-map reviewed (Q190) | 18 处 | 0 |
| java-prototype-objects 遮蔽格 | 107 | 0 |
| 设备比对只要求抛错的 Color 用例 | 15 | 0 |

### 20.4 验收

- HEAD 导出副本 `E:/.cache/aj6-p35-head` (宿主 a9f7acd07, KSP 取同一副本的 script-modules.json; Node fde5c5a8, Bun 24a66fe, TS 8f8adff, Ace 3af850c): `check.py --source ksp --matrix --strict-arity --strict-types --docs-support` (含 Ace) 退出码 0, failures 空, MATRIX.md 与 pending-baseline.json 与 HEAD 无差异. 文档到 70b79f6 后复跑, 结果相同.
- api-coverage 单测 121 例通过 (跳过 3), 全部 check-runtime-* / check-bun-* / check-ui-* 通过, Node 桌面测试 153 + 16 + 3 + 62, Bun host client 测试 150 全过, sync-*.mjs --check 全过.
- TS sync --check changed 0, verify-runtime-declarations 通过 (declarations 95), verify-api-coverage 在真实仓库退出码 0; Ace verify-runtime-api-coverage --fresh 通过, verify-api-coverage 在真实仓库退出码 0. 文档 auto-generate --check 当前.
- 真机结果与设备恢复见 docs/dev/runtime-closeout-2026-10-31.md. 重录脚本改动后的 rhino 阶段在 Xiaomi 968e9f18 上复跑 633 / 633 (只验证脚本, 该机宿主与录制值错误名全同, 差异只在堆栈文本).
- 终版包复跑 (AVD_API_33, x86_64 debug, Node fde5c5a8, Bun 24a66fe): Bun 超时轮 6 / 6; Node AndroidTest SimpleRunSmokeTest, ExecutionModeLifecycleSmokeTest, NpmEcosystemSmokeTest, NodeRuntimePluginAndroidConformanceTest 全类 OK (37 tests).
- 未验证: process.exit 清定时器未在 arm64 真机复测 (改动是共享 JS, 已在 x86_64 验证). 后续: 2026-11-01 在 Redmi (arm64, API 33) 复测 7 / 7, 见第 21 节.

### 20.5 提交

| 仓库 | 提交 | 内容 |
|---|---|---|
| 宿主 | ad62a8859 | execution.control 答复 cwd, 不需授权 |
| 宿主 | 89bbda2a6 | nodeReviewed, reviewed 撤销, except, device_relative_paths.py, Bun Worker 超时轮 |
| 宿主 | a9f7acd07 | 5 个结构脚本, 补全源索引 |
| Node 插件 | ef0458f9 / 2eab83dd / c028aa90 / 2f645450 / fde5c5a8 | Q120, tasks / sysprops null, setIntervalExt, HOST-API, 声明 |
| Node 插件 | 26bfcc7f / ce517535 | Q127, TODO, 真机脚本, 重录 |
| Node 插件 | 5c1ce68b / 2025b2e1 | Q179 复测的两处修复 |
| Bun 插件 | 7623080 / fafc993 / 05bc2f6 / 9286a8f / 24a66fe | Q120 同步与 host-api.md |
| Bun 插件 | 42c47aa / 288f6ca / d12edff | Q127, TODO, Q179 同步 |
| TS | 752b0e5 / 8f8adff | 任务声明, 同步运行时声明 |
| Ace | 819eb87 / 3af850c | 重导入, 捆绑声明 |
| 文档 | e1105d9 / 70b79f6 | 相对路径小节, 生成物 |
| Offline Docs | 4c099cb | 同步生成页, 版本不变 |
| 宿主 | (本节所在提交) | 本节, 报告, ROADMAP 落点, 附录 C, REVIEW 7.1 |

## 21. P2.6 / P3.6 / P4.6 阶段收尾 (2026-11-01, 子会话 api-cov-closeout-bump)

### 21.1 结论

pending 基线 574 / 574 / 574 -> 572 / 572 / 572, 三列只剩 imgui 模块 (Q213 (b) 的排除规则由后续小卡落地). Flow# 的 106 行三列全部覆盖, 文档运行时支持表不再有待补项.

### 21.2 做了什么

- Q200 (a): Worker 的 load 钩子判断文件名是 package.json 再解析, 项目没有 package.json 时 new Worker(__filename) 不再抛 SyntaxError. 新桌面用例 test-worker-entry.js 进 Node CI, 在旧源码上复现 SyntaxError.
- Q202 (a): 共享工厂的 Flow 句柄补 await() 与 sync(timeout?), 都返回 Promise, 不阻塞脚本线程; sync 超时以 TIMEOUT 拒绝 (step 与 reason 都是 sync), 链继续运行, 同 Rhino. d.ts (TS, Node, Bun), Ace 捆绑声明, 文档 runtimeApis 与 flowType, 结构脚本 check-runtime-flow 与矩阵单测同步.
- Q201 (a): arm64 复测通过, 保持现状.
- 宿主 VERSION_BUILD 按 COMMON_AGENTS 校正: 4283 -> 4899, versionCode 5935 -> 6551. 之前比提交数少 614, 超出容差 10.
- Node 声明的最低宿主保持 3923 (Q206 (a) 已先行).
- 文档站 changelog: 新增 Flow 一条; 去掉同一版本里已过期的 "仍待补" 4 处; `.changelog/lang_zh-Hans.json` 从 10-30 起落后 api/changelog.md 37 条, 本卡同步 (`{{}}` 改写为 "双花括号", 否则模板解析失败).
- 各仓库 changelog 只写 zh-Hans (维护者同步其他语言). TS, Ace, Bun 与文档的 generate_markdown 有跨语言校验, 本卡用同一渲染函数跳过校验生成, 这 4 个仓库的 `--check` 在其他语言同步前失败, Node 通过.

### 21.3 版本

| 仓库 | 改动前 | 改动后 | 提交 |
|---|---|---|---|
| TS 声明 | 4.85.0 | 4.86.0 | 42291f0 |
| Ace | 2.47.0 / 345 | 2.48.0 / 388, 捆绑声明 4.86.0 | 3b975b7 |
| Node | 2.0.0 / 356 | 2.1.0 / 370 | 867fad8c |
| Bun | 0.21.0 / 335 | 0.22.0 / 346 | c214d93 |
| 宿主 | 6.8.0 / 4283 (versionCode 5935) | 6.8.0 / 4899 (versionCode 6551) | 6f2e66ca3 |
| 文档 | versionCode 171 | versionCode 172 | 80aeade |
| Offline Docs | 6.8.40 / 219 | 6.8.40 / 242 (VERSION_NAME 未发布, 生成器保留) | 556d7b2 |

### 21.4 真机

Redmi bek749scrwv4wo8h (arm64, API 33), 调试包: 宿主共享签出 2026-10-31 22:34 构建, Node 400b27cf, Bun 13bfd2e. 脚本 `tools/api-coverage/device_flow_terminals.py`.

| 轮 | 结果 |
|---|---|
| flow, Rhino 录制 | 11 / 11 (await.rejected 与 Node / Bun 不同, 登记 Q205) |
| flow, Node main + Worker | 22 / 22 |
| flow, Bun main + Worker | 22 / 22 |
| entry (Q200) | 1 / 1 |
| exit (Q201): control, timeout, interval, immediate, from-timer, from-promise, with-worker | 7 / 7 |

设备还原: 宿主 5935 md5 b958b465, Bun 293 md5 bbfca1f6, Node 291 md5 a8811d17 全部一致, 宿主授权列表无差异, enabled_accessibility_services 与开始时相同 (开始时宿主无障碍是开着的, 与 10-31 报告的 "off" 不同, 按现状还原). Redmi 宿主有 WRITE_SECURE_SETTINGS 且 Shizuku 在运行, 所以 Q201 用专门用例, 没走 waitFor 超时路径.

### 21.5 验收

HEAD 导出副本 `E:/.cache/aj6-closeout-bump-head` (宿主 6f2e66ca3, KSP 取同一副本 10-31 23:56 的 script-modules.json, 其后 app/src 无变动; 文档 80aeade, TS 42291f0, Ace 3b975b7, Node 867fad8c, Bun c214d93):

- `check.py --source ksp --matrix --strict-arity --strict-types --docs-support` 退出码 0, failures 空, pending 572 / 572 / 572, MATRIX.md 与 pending-baseline.json current, 文档 89 页 current, 未覆盖 0 / 0, Node d.ts unreviewed 0.
- api-coverage 单测 121 例通过 (跳过 3), test-dts-types 27 例, 全部 check-runtime-* / check-bun-* / check-ui-* 退出码 0, language_runtimes 退出码 0 (副本无语言运行时兄弟仓库, 源码比对跳过).
- Ace: generate-runtime-api-queries --check 未变, verify-runtime-api-coverage --fresh 通过, verify-api-coverage 在真实仓库通过.
- TS: sync-runtime-declarations --check changed 0, verify-runtime-declarations 与 verify-api-coverage 通过.
- Node 桌面用例 155 + 16 + 3 + 62 + 41 全过, Bun host client 151 全过, sync-*.mjs --check 全过.
- 文档 normalize-markdown --check changed 0, auto-generate --check 当前.

### 21.6 提交

| 仓库 | 提交 | 内容 |
|---|---|---|
| 宿主 | 5d582fdd1 | 附录 C 六行已决 |
| 宿主 | 5e99f75c6 / bdccf53fb | 结构脚本, 单测, MATRIX, 基线, device_flow_terminals.py |
| 宿主 | 31d087541 / 6f2e66ca3 | 复测记录, Q205 / Q206, 构建号 |
| Node 插件 | bd80f768 / 400b27cf / 867fad8c | Q200, Q202, 2.1.0 |
| Bun 插件 | 13bfd2e / c214d93 | Q202, 0.22.0 |
| TS | e23a852 / 42291f0 | 声明, 4.86.0 |
| Ace | 237f40d / 142bf6f / 3b975b7 | 捆绑声明, 查询清单, 2.48.0 |
| 文档 | 22c697e / b804db5 / 6834d14 / 80aeade | runtimeApis, flowType 表, changelog, 生成物 |
| Offline Docs | 556d7b2 | 同步生成页, 构建号 |
| 宿主 | (本节所在提交) | 本节, ROADMAP 落点, REVIEW 7.1 |

### 21.7 观察, 未改

- `tools/nodejs/api/test-worker-host-modules.js` 在 HEAD 上本来就失败 (console.input 的断言过期), 不在 Node CI.
- Node 的 test-flow.js / test-flow-tools.js / test-worker-flow.js 不在 Node CI 的 build.yml 里, 只由本节这类手动门禁覆盖.

## 22. P5.2f-2 卡 A: imgui 排除规则 (2026-11-01, 子会话 api-cov-imgui-exclude)

### 22.1 结论

pending 基线 583 / 583 / 583 -> 0 / 0 / 0, Node main, Worker 与 Bun 都没有 pending 行了. Q213 (b): imgui 583 行三列 not-portable / rhino-only, `imgui.__pluginId` 仍归 internal-exports. 矩阵行数不变. doc / d.ts / Ace 列不变, `deferredNameGate.imgui` 保留, 由卡 B / C / D 收口.

### 22.2 数字

| 列 | not-portable 前 | not-portable 后 | pending 前 | pending 后 |
|---|---:|---:|---:|---:|
| Node main | 941 | 1524 | 583 | 0 |
| Node Worker | 944 | 1527 | 583 | 0 |
| Bun | 944 | 1527 | 583 | 0 |

`pending-baseline.json` 按 Q40 (a) 只减, decisions 新增 Q213 (b). 规则按 `imgui.*` 匹配, 以后的 imgui 新名称不再产生 pending, Q122 的基线登记到此结束.

本卡进行中 imgui R7 登记了 ImGui 1.18.0 的 4 个名称 (宿主 89f6a01ae, setNextWindowFocus / setNextWindowCollapsed 及别名): 全部由规则覆盖, pending 保持 0, 基线不变, 只是矩阵 3578 -> 3582 行, not-portable 变成 1528 / 1531 / 1531 (3e45564eb), imgui 584 -> 588 行. Q215 (a) 的 "新名称自动覆盖" 由此得到验证. 新名称仍要重生成 MATRIX.md 与 imgui 页运行时表, 否则 check.py 报过期.

### 22.3 文档

- imgui 页运行时表: 改动前 584 行都是差异项, 只因内部导出 `imgui.__pluginId` 的规则不同, 新规则下单元格变长后 `api/imgui.md` 会到 420 KB. 生成器改为内部导出不影响一列的 "一致" 判定, 这一页其他 API 只占一格, 页面 82 KB -> 5 KB.
- 新增 "在 Node.js / Bun 中使用" 一节, 示例由 `device_imgui_escape.py --doc` 与脚本逐字比对.
- changelog 只写 zh-Hans.

### 22.4 真机

真机 Xiaomi 968e9f18 28 / 28 (Node 与 Bun 各跑文档示例, 行为核实, 主脚本先退出三个用例; 宿主 6551, ImGui 1.18.0, Node 2.0.0 / 340, Bun 0.21.0 / 296; Bun 授权跑完还原)

脚本 `tools/api-coverage/device_imgui_escape.py`, 三个用例: example 跑文档示例原文, details 核实页面写的行为, leave 让主脚本先退出. 另外统计宿主悬浮窗个数, 确认窗口出现后又消失.

实测结论:

- 2.3 的第一点成立: Node 与 Bun 都能用 engines.execScript 启动 Rhino 脚本打开 imgui 窗口, 帧回调持续运行, 窗口关闭后 Rhino 脚本自行结束.
- 第二点成立: Bun 走同一条路径, 启动的执行 spawnDepth 为 1, 可在 engines.all() 看到. arguments 含对象时两边都拒绝 (Node ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED, Bun INVALID_REQUEST).
- 第三点: 主脚本到面板用 arguments, 之后两个方向都用 events.broadcast. 对象和数组不保持, 用 JSON 文本 (Q218).
- timeoutMs 只限启动调用. 主脚本结束时窗口不关闭, 其他脚本发广播仍能关闭它.
- `ready` 在 createWindow 返回前同步触发, 之后注册的 session.on("ready") 收不到 (Q219).

### 22.5 门禁

HEAD 副本: `check.py --source ksp --matrix --strict-arity --strict-types --docs-support` 退出 0, failures 空; `tools/api-coverage` 单测 121 个通过 (3 个跳过); Ace `generate-runtime-api-queries.mjs --check --host ../AutoJs6` changed false; `generator/test-runtime-support.js` 11 项通过; `normalize-markdown.py --check` changed 0.

### 22.6 提交

| 仓库 | 提交 | 内容 |
|---|---|---|
| 宿主 | 41e93cb07 / 118851624 | 规则, test_p5_2f1 |
| 宿主 | 54678f1c0 / cf9749ba7 / 3e45564eb | MATRIX, 基线 |
| 宿主 | b6a719dcd | device_imgui_escape.py |
| 文档 | edbd218 / 34bbc9a | imgui 页, 生成器, changelog, 运行时表 |
| 文档 | 5e0724d | 生成物 (由同时提交的宿主小修卡一起重生成) |
| Offline Docs | 08fcf64 | 捆绑页 (同上) |
| 宿主 | (本节所在提交) | 本节, ROADMAP 落点, IMGUI-PLAN |

## 23. Q205 (a): Rhino 续体模式 await() 遇拒绝改为抛出 (2026-11-01, 子会话 api-cov-q205-await)

### 23.1 结论

Rhino 续体模式 (项目启动, `continuation` 特性) 下, promise.await(), flow.await(), continuation.await(rejected), resumeError 恢复的 cont.await(), 以及经同一路径等待的 http 请求, 遇拒绝都在调用处抛出拒绝原因, 不再把 Error 当返回值交回. 原因是 Error 就抛该 Error, 不是 Error 也抛出, catch 拿到原值. 超时与链继续运行等语义不变. 脚本没有捕获时以该错误结束, UI 模式下应用也不崩溃, 这正是 6.3.4 当年改成返回值想避开的问题.

### 23.2 根因与改动

- 根因: `rhino/continuation/Continuation.kt` 的 `resume()` 经 `Result.handle` 取值后调 `resumeContinuation`, 失败分支返回 error 本身. 挂起后 Kotlin 栈已丢, `Creator.await` 里的 handle 执行不到. 上游恢复路径只有 `setCallResult`, 没有抛出通路; `ACallFrame.parentFrame` 是 final, 宿主拼不出帧, 所以根治要动 fork.
- 历史: 原 JS 模块是 `throw result.error`, 6.3.4 Alpha2 (ee1fb8a6af, 2023-10-03) 改成 popException + return, 同版 changelog 修的是 "使用协程特性运行项目产生未捕获异常致使应用崩溃的问题".
- fork (维护者 19:4x 选定 "fork 加 API, 换 jar"): 新 `Context.resumeContinuationWithError`, 由 `Interpreter.restartContinuationWithError` 与 `ContinuationJump.throwResult` 在挂起的调用处抛出; 新文件 `ContinuationError`, 新测试 `ContinuationResumeErrorTest`, 钩子登记 725 行. 补丁基于 fork 7b3ddf3c1, 入库见 23.6.
- 宿主 956a09fa8: jar 3393229 -> 3394845 字节, sha256 `a5049a70ff381e171d795dcb311cbd41f7ad6244528378a058c739684e197385`. `Continuation.resume` 改走新 API, 删 `Result.handle`; `AutoJsContext` 同样从续体表移除; `Timer.runPosted` 让主 Looper 上恢复时抛出的错误走 exit, 不抛到 Looper; `ContinuationPrototype` 在 Promise 反应里恢复时, 脚本未捕获的错误经 setImmediate 抛出, 不再落入 `.catch(resumeError)` 二次恢复同一续体.

### 23.3 单测

| 范围 | 结果 |
|---|---|
| fork `:rhino:test` | 4404 例, 0 失败, 4 跳过 |
| fork `:tests` Continuation / Generator | 37 例, 0 失败 |
| fork HooksRegistryTest | 通过 |
| 宿主全量 testAppDebugUnitTest | 6040 例, 2 失败, 27 跳过 |

新增与调整: `AwaitRejectionTest` 12 例, `ContinuationResumeTest` +4, `TimerContinuationTest` +1, `PromiseHarness` 的 resumeError 真抛, `PromiseDifferentialTest` 的 KNOWN 加 K1 / K6.

宿主 2 个失败都与本卡无关: `GalleryLateOpeningTest` 重跑通过; `IdleWaiterTest` 是真实时钟断言 (睡眠 10 - 30 ms 后数事件), 3 组 Gradle 并行时两次失败, 失败的用例还不相同 (aBusyStreamTimesOut, eventsPostponeTheQuietPeriod), 它不依赖 Rhino, Timer 或续体. 21:07 在同一副本 (含修复与新 jar) 重跑 7 / 7 通过.

### 23.4 真机

Redmi bek749scrwv4wo8h (arm64, API 33), 调试包: 宿主 6551 md5 0bf64799 (HEAD 副本 + 本修复), Node 370 md5 3f5a96a9, Bun 346 md5 ec1cdd4a. 脚本 `tools/api-coverage/device_flow_terminals.py` (d2f21e771), 去掉了 RHINO_DIFFERENCES, 新增 rejection 阶段.

| 轮 | 修复后 | 原宿主 5935 对照 |
|---|---|---|
| flow, Rhino 录制 | 11 / 11, await.rejected 抛出 | - |
| flow, Node main + Worker | 22 / 22 | - |
| flow, Bun main + Worker | 22 / 22 | - |
| rejection, 后台 + UI 模式各 11 项捕获 + 6 项未捕获 | 34 / 34 | 0 / 34 |

- 未捕获项: 修复后只收到索引 0, 错误进日志, 宿主 pid 不变. 原宿主收到 [0, 1, 2] 或 [0, 2], 脚本带着错误值跑完.
- 捕获项在原宿主全部缺结果, 未细查, 推测是 `Promise.reject(null).await()` 不恢复.
- 设备还原: 宿主 5935 md5 b958b465, Bun 293 md5 bbfca1f6, Node 291 md5 a8811d17 全部一致, `/sdcard/Download` 无 aj6- 残留.

### 23.5 文档与 changelog

- 文档 a989c94 (`api/continuation.md`), d6fe7a8 (重生成, 生成器按惯例自增 versionCode 174 -> 175); Offline Docs e7a9e40 (VERSION_BUILD 244 -> 245).
- 宿主 3ab7e8d49: zh-Hans changelog 加一条修复. 依据: 返回错误值的行为自 6.3.4 起已发布, promise.await 与 cont.await 都受影响; 只有 flow.await 是 6.8.0 新增.

### 23.6 遗留

- fork 补丁未入 fork 仓库: 共享签出自 19:28 起处于 rhino-p8 的上游合并中. 补丁在共享笔记 `api-cov-q205-fork.patch` (430 行, 6 个文件), 宿主 jar 已含此 API, 补丁入库前从 fork 重建 jar 会让宿主编译失败. 登记 Q223.
- 2026-11-02 补记: 上游合并结束后补丁已入 fork 246901d27 (基于 507e5f7ed, Java 文件都能干净应用, 只有 HOOKS.md 要手工合, 钩子合计 702 -> 728 行). 在 fork HEAD 的副本上验证: `:rhino:test` 4459 例 0 失败 4 跳过 (含 HooksRegistryTest 2 例, ContinuationResumeErrorTest 11 例), `:tests` Continuation / Generator 33 例 0 失败, spotlessCheck 通过. Q223 已决, 宿主 jar 不变, 由 P9.7 重建.

## 24. P6.1 最低版本抽样 (2026-11-01, 子会话 api-cov-p6-1-minapi)

### 24.1 结论

设备 G8441 (API 28), 报告 [P6-1-MINAPI-2026-11-01.md](P6-1-MINAPI-2026-11-01.md). 抽样 26 项 (9 族) Node main / Worker 全过, P1.2 图像 9 / 9, UI 11 / 11, 截图 2 / 2 全过, Bun 全列按 API 33 记限制. pending 不变 (0 / 0 / 0, 标注不改状态).

### 24.2 minApi 标注规则

`runtime-policy.json` 新增 `minApi` 列表: 覆盖状态的单元格带上 `minApi` 与 `minApiRule`, 状态不变, 不算 pending 也不算排除, 不能用来清 pending; 规则不命中单元格或缺级别 / 理由时记策略冲突. `MATRIX.md` 新增 "最低 API 标注" 节. 现有 1 条规则 `bun-plugin-min-sdk` (Bun 列, 匹配 `*`, minApi 33, 2051 格): 插件 minSdk 33, 官方二进制调用 close_range, Android 12L 及更早的应用 seccomp 以 SIGSYS 结束进程. Node main / Worker 没有标注: 本轮失败项都不是 API 28 限制.

### 24.3 修复与新待决

- 宿主 6d9fd7546: `NotificationChannel.hasUserSetImportance` 是 API 29 方法, RuntimeSystemBridge 与 Channel.kt 的判断写成 API 28, Android 9 上读渠道抛 NoSuchMethodError. 改为 API 29, Node main / Worker 复测 51 / 51.
- 宿主 f14ed8d93 / 6d9fd7546: 设备套件在 API 33 以下跳过 Bun 模式 (`runtimeModes`, `bunRuntimeAvailable`).
- Q227 terminal waitFor 与 write 的竞态 (需拍板), Q228 插件进程存储授权入口 (已按推荐先行待确认).

### 24.4 提交

宿主 f14ed8d93 / 6d9fd7546 / 3c9c5b0bc / d584ca619; MATRIX.md 由 3e45564eb 带上标注节; Node 5587b94e; 文档 716dac1.

## 25. P5.2f-2 卡 C: imgui 声明与 Q219 (b) (2026-11-02, 子会话 api-cov-imgui-dts)

### 25.1 结论

imgui 的 d.ts 与 Ace 补齐: 589 条查询路径 (587 个公开名称 + `imgui` + `$imgui`) 声明解析, Ace 默认组, 全组与静态索引都是 589 / 589, 暂缓名单 589 -> 0. 文档已由卡 B 补齐, 所以本卡在 18d849a63 删掉 `deferredNameGate.imgui` 并重生成 MATRIX.md; 卡 B 不用再动这几处. P5.2 不勾, 等卡 D.

### 25.2 数字

| 指标 | 前 | 后 |
|---|---:|---:|
| imgui 查询路径 failingPaths | 589 (暂缓) | 0 |
| queriedPaths / surfaces failures | 2262 / 0 | 2262 / 0 |
| deferredNameGate | imgui | 空 |
| MATRIX dts none | 597 | 10 |
| MATRIX ace none | 1002 | 415 |
| MATRIX imgui 行 doc / d.ts / Ace | 0 / 0 / 0 | 332 / 587 / 587 (doc 为卡 B) |
| Ace runtime-api-queries.json | 5482 | 5482 |
| Ace 静态索引 autojs6_indices.js | 1852095 字节 | 2069806 字节 (+11.8 %) |
| aj6-int-imgui.d.ts | - | 3243 行, 184 KB (LSP core 组 lib.autojs6.core.d.ts 现约 2.02 MB) |

13 个返回对象 (Session, DrawList, FloatingButton, DataTable, LogWindow, Toast, StatsOverlay, Component, Texture, SelectionStorage, MultiSelectIO, Dialog, DialogPromise) 的 821 条 "生产者 + 成员" 路径都能从调用表达式解析到显式类型 (例如 `imgui.createWindow.().setFps`, `imgui.alert.().controller.close`), 卡 D 的 probe 可以直接用. 每个 Promise 还有一条 `Symbol.toStringTag`, 是探针脚本的假象, 不计.

### 25.3 改写点

由插件 `imgui.d.ts` 改写: `Session.capture` 返回宿主 `ImageWrapper` (去掉 `CapturedImage` 占位), 去掉内部的 `__pluginId` (Q123), `imgui` / `$imgui` 放进 `aj6-int-init.d.ts`. 注释里的 docs/ 路径在文件头说明属于插件仓库. autojs6-node / autojs6-bun 不加 (Q213 (b)).

`tsc --strict` 覆盖: TS `docs/smoke/imgui-smoke.ts` (随 `npm test`), 插件 10 个示例 (`npm run test:imgui`, 工具 `tools/verify-imgui-examples.mjs`, 零诊断; 把 createWindow 与 ui.text 改成错名后报 18 条, 证明检查生效).

### 25.4 Q219 (b)

插件 0080f5e: ready 在脚本线程取监听器并记下事件, `Session.on` 把它投递给之后注册的监听器, 每个监听器一次, 总在 on 返回后运行, off 与会话关闭会取消. index.js 净增 22 行, 没动宿主与示例 (examples/ 没有用 ready).

验证: 插件单测 77 全过 (新增 2 项, 其中一项在另一线程触发 surfaceCreated; 换回旧 index.js 这 2 项失败), `check_types.py` 过. 真机 Xiaomi 968e9f18 (API 35, 宿主 6551, 脚本 `tools/device_ready_check.py`) 7 项全过, 紧跟 createWindow 注册的监听器 calls=1, 与渲染回调同线程, `readyAtReturn=true`.

没有 bump, 没有发版, 是否发 1.18.1 由维护者决定.

### 25.5 与方案的出入

- IMGUI-PLAN 与卡片写 Ace 查询清单 "约 +588", 实际不变. `runtime-api-queries.json` 只收 Node / Bun 的 bridged, local-shim, capability-gated 格, imgui 三列都是 not-portable. 那 588 个名称由 check.py 的 `queriedPaths` 覆盖, 而这些路径之前就在查询里, 只是暂缓, 所以 queriedPaths 也不变.
- 静态索引与 source 不是卡片列的 `:app:generateAutoJs6LspDeclarations` 生成的, 要跑宿主 `tools/ace-completion/generate-editor-assets.mjs`, 否则 aceStatic 失败. 两者都跑了.
- 约束: 常量组的内层成员 (`imgui.Col.Text`) 没有进静态索引, 模型不展开 `Readonly<Record<K, number>>`, 语义补全有. 名称门禁只查到 `imgui.Col`, 不受影响.
- Ace 的 `aj6-int-speech.d.ts` 留在捆绑版本: TS d830b2b 属于 3-Macaw P9.3, 不随本卡导入.

验收: 六个仓库 HEAD 副本 (宿主 9cb05d39c 加本卡文件, TS a4108be, 文档 dda3fac, Ace 2fd349f, Node 5587b94e, Bun c214d93) 上 `check.py --source ksp --matrix --strict-arity --strict-types --docs-support` 退出 0, failures 为空; 工具单测 122 过; TS `npm test` 与 `sync-runtime-declarations.mjs --check --ace` 过; Ace `generate-runtime-api-queries.mjs --check` (指向该副本的矩阵) 与 `verify-runtime-api-coverage.mjs --fresh` 过. KSP 没有重跑: 复用 aj6-q218-head 的输出, 它与 aj6-p6-2-head 和共享签出的 sha1 都是 07e72243, 模块源码 11-01 20:50 之后没变.

### 25.6 待决与提交

新待决 Q236 (`Session.once` 不传 session), 见附录 C. Q237-Q239 未用.

TS a4108be; Ace 2fd349f; 插件 0080f5e / 05b799d; 宿主 18d849a63 与本节.

## 26. P5.2f-2 卡 B: imgui 文档 (2026-11-02, 子会话 api-cov-imgui-docs)

### 26.1 结论

imgui 公开名称 587 个全部有文档: `missingDocumentation` 583 -> 0, 其中 332 个是标题, 255 个是别名表里的一行. `imgui.__pluginId` 是内部导出, 不进文档门禁. 运行时列不变, imgui 三列仍是 not-portable / rhino-only, pending 0 / 0 / 0. 文档里的 Rhino 示例 25 块全部在真机跑过. `deferredNameGate.imgui` 与 MATRIX.md 由卡 C 在 18d849a63 处理, 本卡不动.

### 26.2 数字

| 项 | 前 | 后 |
|---|---:|---:|
| `api/imgui.md` 行数 | 101 | 5346 |
| imgui 页标题 | 0 | 332 (289 函数 + 43 常量组) |
| 别名表行 | 0 | 255 |
| 对象页 | 0 | 13 页 3170 行 |
| imgui 公开名称 none / mention / heading | 583 / 4 / 0 | 0 / 255 / 332 |
| 文档门禁全部 2017 行 missing / mention / heading | 583 / 4 / 1430 | 0 / 255 / 1762 |
| MATRIX doc 列 none / mention / heading | 592 / 278 / 2712 | 9 / 529 / 3044 |

对象页比 IMGUI-PLAN 第 1.3 节多 Dialog 与 DialogPromise, 共 13 页, 以 R7.3 移交清单为准. 卡 D 的 returned-objects 要按 13 个对象写: `imguiSessionType`, `imguiDrawListType`, `imguiFloatingButtonType`, `imguiComponentType`, `imguiDataTableType`, `imguiLogWindowType`, `imguiToastType`, `imguiStatsOverlayType`, `imguiDialogType`, `imguiDialogPromiseType`, `imguiTextureType`, `imguiSelectionStorageType`, `imguiMultiSelectIOType`. 这些页目前没有运行时块, 由卡 D 加.

### 26.3 文档

- 卡 A 的运行时支持表与 "在 Node.js / Bun 中使用" 一节原样保留, 正文开头写明 Node.js / Bun 走 `engines.execScript` 逃生口.
- Q219 (b): `ready` 按普通会话事件说明, 注明 1.18.0 及更早版本中 `session.on('ready', ...)` 收不到, 这些版本用 `createWindow` 的 `onReady` / `events` 选项.
- Q236 待决: 文档按现状写 `once` 的监听器只收到事件对象, 会话用 `event.session` 取. 选 (a) 时这两处要改成版本说明.
- "广播的值" 一格保持 JSON 文本写法. 这是 Q218 (a) 修好之前的行为, 由小修卡 api-cov-q218-q227-q228 改短.
- 站点的 marked 在表格单元格里不认转义的竖线, 单元格会被切断; `&#124;` 放进反引号又会原样显示. 所以表格里不写竖线, 组合标志改为列出各项.
- changelog 只写 zh-Hans, 其他 9 种语言由维护者同步. `.python/generate_markdown.py --check` 报其他语言与 zh-Hans 的键不一致, 这是只写 zh-Hans 的预期结果.
- `dataTypes.md` 加 13 个对象的类型段, `toc.md`, `sidebar.md`, `progress.md` 挂上入口 (Q125).

### 26.4 真机

Xiaomi 968e9f18 (23046RP50C, API 35) 25 / 25: 宿主 6551, ImGui v1.18.0 正式 arm64 包 (md5 143cf015b9c502c963ac7391608b2ba3, versionCode 58), 不发声. 原包 md5 f5a8d28d5484dca3e36d805b59822518, 跑完装回并核对.

脚本 `tools/api-coverage/device_imgui_docs.py` 抽出 imgui 页全部 Rhino 代码块, 逐块包在函数里运行, 查四点: 代码块跑完不抛异常, 2.5 s 后会话仍打开 (帧回调没有抛错), 关闭后 0.5 s 内没有 console.error (事件监听器的错误打印在这里), 悬浮窗出现后又消失.

第一版脚本只查到关闭之前, 所以 25 / 25 也漏了一个错误: Session 页示例在 `once('closed', ...)` 里读第二个参数, 而 `once` 只传事件对象 (Q236), 关闭时抛 TypeError. 卡 C 登记 Q236 后才发现. 示例改读 `event.reason`, 脚本加上 console.error 检查. `console` 是 Java 对象, 脚本里不能挂钩子, 所以从 logcat 的 GlobalConsole 标签读. 反例: 旧示例判 FAIL, 报 `Session 'closed' listener failed`; 新示例 PASS. 全量重跑 25 / 25.

首轮有一块悬浮窗轮询漏检, 等待从 1.8 s 加到 2.5 s 后不再出现. 另外 23 个新示例用 tsc strict 通过, `device_imgui_escape.py --doc` 3 项 PASS. 全量重跑之后文档没有再改.

### 26.5 门禁

宿主 776f50a8a, TS a4108be, Ace 2fd349f, Node 2a449b9a, Bun d8eb960 的 HEAD 副本加文档 4ac9950 (KSP 取 aj6-q218-head 副本, 1355737 字节): `check.py --source ksp --matrix --strict-arity --strict-types --docs-support` 退出 0, failures 空, `deferredNameGate` 已空, 文档门禁 heading 1762 / mention 255 / missing 0, MATRIX current, pending 0 / 0 / 0, 运行时支持页 89 页全部 current. 卡 C 删门禁之前, 宿主 9cb05d39c 副本报告 `deferredNameGate.imgui.missingDocumentation` 0, failingPaths 589 (声明缺口, 由卡 C 清零).

文档: `generator/auto-generate.py --check` 报当前 (189 模块), Offline Docs 的 `imgui.html`, `imguiDataTableType.html`, `imguiSessionType.html` 与文档站生成物 blob 相同 (imgui.html fc1d3198). 14 页生成物的 7632 个站内链接与锚点无断链.

### 26.6 待决与提交

没有新待决, Q232-Q235 未用.

| 仓库 | 提交 | 内容 |
|---|---|---|
| 文档 | b594045 / 2560c32 / dda3fac / 9d81b68 | imgui 页, 13 个对象页, 目录, 类型段, changelog, 链接, 表格与 `once` 修正 |
| 文档 | 6267b29 / 98fe030 / 4ac9950 | 生成物 |
| Offline Docs | 83bfcef / 5c4e677 / 8b66af2 | 捆绑页 |
| 宿主 | 9cb05d39c / (本节所在提交) | device_imgui_docs.py, 本节, ROADMAP 落点与 Q219 行, IMGUI-PLAN, tools README |

## 27. Q218 (a) / Q227 (a) / Q228 (b) 小修 (2026-11-02, 子会话 api-cov-q218-q227-q228)

三项都在 Redmi bek749scrwv4wo8h (22120RN86C, API 33, MIUI) 上验证, Node 与 Bun 各一遍.

### 27.1 Q218 (a): events.broadcast 保真

主脚本发往 Rhino 的对象和数组以 JSON 文本 (`BroadcastEmitter.Json`) 过进程级广播表, 每个有监听器的 Rhino 脚本在自己的作用域里解析, 所以监听器拿到原生对象, 脚本之间不共享. Rhino 发往主脚本的值走 `NativeJSON.stringify`. 主脚本之间仍是 JSON, Rhino 之间仍是同一个对象.

| 值 | 结果 |
|---|---|
| 字符串, 数字, 布尔值, `null`, 对象, 数组 | 两个方向都保持结构 |
| 对象里的函数和 `undefined` | 省略 |
| 数组里的函数和 `undefined` | `null` |
| Rhino 直接传函数, `NaN`, 无穷大 | `null` |
| 主脚本直接传 `undefined` | Rhino 收到 `null` |
| Rhino 直接传 `undefined` | 字符串 `undefined` (不是本卡引入) |
| Rhino 传循环引用, 或 `toJSON` 抛异常 | 该参数到主脚本是 `null`, 发送方不抛异常 |
| 主脚本传循环引用 | `emit` 的 Promise 以 `TypeError` 拒绝, 广播不发出 |

有两处与 JSON.stringify 的语义不同. Rhino 发送循环引用时不抛, 因为发送方不知道谁在监听, 抛错会让无关脚本出错并截断后面的监听器, 这是有意的. Rhino 直接传 `undefined` 时, Rhino 调用 Java 方法那一层已经转成字符串 `undefined`, 广播层分不清它和真的字符串, Rhino 监听器之间一直也是这个值, 不为它加包装层, 文档写明. 提交 1fbe504c7 的说明写的是 "null as arguments", 对这一种情况不准确, 以本节为准.

### 27.2 Q227 (a): terminal 订阅就绪

请求到宿主的顺序不保证, 只靠宿主排序不够, 所以拆成两条路由: `terminal.waitForStart` 在订阅运行后答令牌, `terminal.waitForResult` 取匹配行. 共享门面按会话 id 持有闸门, 同一会话的 write 排在此前发起的 waitFor 订阅生效之后; 失败的 start 也放行 write. 超时从 start 计, 语义同 waitFor. Rhino 的 "自调用起订阅" 不变, 旧路由 waitFor 保留. `TerminalService.waitForLine` 拆成 `startLineWait` + `awaitLine`.

Node ui-family 的 runtime 变体去掉 5587b94e 的 `sleep 1`, Rhino 变体保留. 终端路由由 15 变 17, Bun 自动继承 `terminal.control`.

### 27.3 Q228 (b): 共享存储提示

插件进程有自己的 UID 且没有界面. 宿主给每次执行一个 `PluginStorageAccess.Prompt`, 观察脚本的错误输出 (Node 的 stderr 与 errorStack, Bun 的 ERROR 级输出), 判定被拒绝后打开一次 `PluginStorageAccessActivity`, 由用户跳转到系统设置里该插件的页面. 不加桥接路由与能力 ID, 不改契约 AAR, 错误本身不变. Node 的 `files` 模块多数方法把拒绝变成 false, 所以它在第一次拒绝时向 stderr 写一行.

实测改变了两处设计. Android 13 未授权时, 在共享存储根目录新建文件报 EPERM, 建目录报 EACCES, `Download` / `Documents` 里自己创建的文件可写, 所以判定覆盖 EACCES 与 EPERM (74b6e8120, Node 28c9801e, Bun b186a23). Java 形式的 `open failed:` 来自宿主进程, 给插件授权改变不了它, 所以不算.

真机还发现一个缺陷: 提示的任务不进最近任务, 离开前台后藏在后台, 下一次启动只把旧任务带回来, 显示旧插件. 启动加 `FLAG_ACTIVITY_CLEAR_TASK`, 新提示替换旧提示 (dd4d01c73).

已知局限:

- 脚本 catch 且不输出的裸 `node:fs` 错误不提示 (Q247).
- 系统拦截后台 Activity 时没有提示, 只有错误本身. Redmi 上宿主在后台时能弹出, 其他 ROM 未验证.
- 维护者第一次没有看到留在手机上的提示: 跑完时它在最上层, 回复时已不在任务栈里, 原因未查明. 按 HOME 复现不出 (提示留在任务栈), 可能是点到对话框外面. 下一次执行会再提示, 不影响使用.
- 查他包的 appop 被系统拒绝时按未授权处理, 最坏情况是对已授权的插件提示一次.

### 27.4 真机结果

候选包: 宿主 6551, Node 370, Bun 346.

| 用例 | Node | Bun |
|---|---|---|
| Q218 双向 (`device_q218_q227_q228.py broadcast`) | 8 / 8 | 8 / 8 |
| Q227 waitFor 后紧跟 write, 8 轮 | 8 / 8 | 8 / 8 |
| ui-family main / Worker (含去掉延迟的 terminal.session) | 22 / 22 x 2 | 22 / 22 x 2 |
| Q228 提示: 错误不变, 只提示一次, 在最上层 | files, logged, uncaught 各 3 / 3 | logged, uncaught 各 3 / 3 |
| Q228 授权后: 写入成功, 不再提示 | 2 / 2 | 2 / 2 |

P6.1 的探针是 Node 6 / 8. 授权由维护者在手机上点. 结束时宿主 5935 (md5 b958b465), Node 291 (a8811d17), Bun 293 (bbfca1f6) 装回并核对, 两插件的 `MANAGE_EXTERNAL_STORAGE` 还原为 default (系统设置写的是 UID 级, 要 `appops set --uid`), 临时文件已删.

### 27.5 门禁

宿主 37b0279a3, TS a4108be, Ace 2fd349f, Node 28c9801e, Bun b186a23, 文档 5488fbe 的 HEAD 副本: `check.py --source ksp --matrix --strict-arity --strict-types --docs-support` 退出 0, 矩阵不变, MATRIX.md 不用重生成. api-coverage 的 CI 结构脚本 (check-runtime-* 与 check-bun-*) 全部退出 0, `tools/api-coverage` Python 单测 122 项通过 (跳过 3). Node 与 Bun CI 里的 JS 测试步骤在 HEAD 上重跑, 共 382 项全过, `sync-js-tools.mjs --check`, `sync-ui-compose.js --check` 通过.

宿主单测: core.broadcast, core.eventloop, engine.*, PluginStorageAccessTest, StaticReferenceGuardTest 全过 (300 项中 1 跳过). Node CI 的 JS 测试 238 项, Gradle 96 项; Bun CI 的 JS 测试 144 项, Gradle 单测全过.

文档: `generator/auto-generate.py --sync-offline --increment-versions` 在 HEAD 克隆里跑, 文档 5488fbe, Offline Docs db09833.

### 27.6 待决与提交

Q246, Q247 已决 (附录 C), Q248, Q249 未用.

| 仓库 | 提交 | 内容 |
|---|---|---|
| 宿主 | 1fbe504c7 | Q218 |
| 宿主 | fb0897688 | Q227 |
| 宿主 | 776f50a8a / 74b6e8120 / dd4d01c73 | Q228 |
| 宿主 | 37b0279a3 / (本节所在提交) | device_q218_q227_q228.py, 附录 C, 本节 |
| Node | e014c284 / 0d5d9e5b / 2a449b9a / 28c9801e | Q227, Q228, HOST-API 与 zh-Hans changelog |
| Bun | d8eb960 / b186a23 | Q246, Q227, Q228, host-api 与 zh-Hans changelog |
| 文档 | 0a987af / 5488fbe | events, imgui 一格, runtimeApis, terminalSessionType, changelog, 生成物 |
| Offline Docs | db09833 | 捆绑页 |

宿主 `.changelog/lang_zh-Hans.json` 有维护者未提交的改动, 本卡没有碰, 三条建议文案在看板结果行. Bun 的 `generate_markdown.py --check` 失败是只写 zh-Hans 时的预期.

## 28. P5.2f-2 卡 D: imgui 成员登记与收口 (2026-11-02, 子会话 api-cov-imgui-members)

### 28.1 结论

imgui 返回对象的成员进了矩阵分母 (Q217 (a)): 13 个对象 309 个成员, 三列 not-portable / rhino-only (规则 imgui-rhino-only-members), pending 保持 0 / 0 / 0, pending-baseline.json 未动. 成员表由 `plugin_exports.py` 的新 kind `dts-interfaces` 从插件 `imgui.d.ts` 读取, 插件增减成员时门禁能发现. P5.2 勾选, P5 只剩 P5.3 收尾.

### 28.2 数字

| 指标 | 前 | 后 |
|---|---:|---:|
| 矩阵行数 | 3582 | 3891 (+309) |
| 返回对象 / 成员 | 51 / 1351 | 64 / 1660 |
| Node main / Worker / Bun not-portable | 1528 / 1531 / 1531 | 1837 / 1840 / 1840 |
| pending | 0 / 0 / 0 | 0 / 0 / 0 |
| doc none / mention / heading | 9 / 529 / 3044 | 9 / 564 / 3318 |
| dts none / name | 10 / 2325 | 10 / 2634 |
| ace none / semantic | 415 / 946 | 415 / 1255 |
| 文档运行时块页 | 89 | 102 |
| 工具单测 | 122 | 125 |

矩阵增加的 309 行等于成员总数, 其中 Getters 150, Functions 142, GettersAndSetters 17. 309 行的 doc 是 heading 274 / mention 35, d.ts 全部 name, Ace 全部 semantic. 35 个 mention 都是 ImguiDrawList 的 PascalCase 别名, 写在该页的别名表里.

| 对象 | 接口自身 | 继承 | 合计 |
|---|---:|---:|---:|
| ImguiSession | 66 | 4 (DialogMethods) | 70 |
| ImguiDrawList | 71 | - | 71 |
| ImguiFloatingButton | 24 | - | 24 |
| ImguiDataTable | 20 | 11 (Component) | 31 |
| ImguiLogWindow | 15 | 11 (Component) | 26 |
| ImguiDialog | 8 | 11 (Component) | 19 |
| ImguiToast | 7 | 11 (Component) | 18 |
| ImguiStatsOverlay | 6 | 11 (Component) | 17 |
| ImguiComponent | 11 | - | 11 |
| ImguiTexture | 8 | - | 8 |
| ImguiSelectionStorage | 6 | - | 6 |
| ImguiMultiSelectIO | 5 | - | 5 |
| ImguiDialogPromise | 3 | - (Promise 不列) | 3 |
| 合计 | | | 309 |

### 28.3 读取器

`dts-interfaces` 读文件里每个接口一份成员表, 不产生导出行. 接口自身声明的名称遮蔽继承来的同名成员, 重载算一个成员, 嵌套对象类型里的字段不算成员. 文件之外的基类 (DialogPromise 的 `Promise`) 记入 `externalBases`, 其成员不列.

独立核对: 用 Ace 捆绑的 TypeScript 6.0.3 检查器列出 69 个接口的属性, 与读取器比较. 67 个完全一致, 13 个目标接口都在内; 差异只有 `Vec2` 与 `IndexedRef` 的数字索引成员 (`0`, `1`), 这两个接口不是返回对象, 读取器不收数字名称.

### 28.4 门禁反例

都在 HEAD 副本里跑, 用 `AUTOJS6_IMGUI_TYPES_ROOT` 指向临时目录, 没有改动任何仓库.

| 情况 | 退出码 | 行数 | 结果 |
|---|---:|---:|---|
| d.ts 副本的 Session 加成员 gateProbe | 1 | 3892 | 3 条失败: `ImguiSession#gateProbe (doc missing, d.ts missing, Ace none)`, MATRIX.md stale, imguiSessionType 运行时块与矩阵不符 |
| 没有 ImGui 检出 | 0 | 3582 | imgui skipped, 对象 51, 矩阵与基线不比较 |

第二行说明缺仓库时门禁等于没有, 所以 CI 必须检出. 工作流已加该仓库的检出与 `imgui_plugin_ref` 输入.

### 28.5 与方案的出入

- 对象是 13 个, 不是方案写的 11 个. 卡 B 按 R7.3 移交清单多写了 Dialog 与 DialogPromise, 卡 C 的声明里也有.
- 卡片没有列文档站, 但 `--docs-support` 要求每个返回对象的页面有运行时块 (26.2 已写明由卡 D 加), 所以动了文档站与 Offline Docs. 改动只是 13 页各插一块生成内容, 文档 changelog 不用写: 这些页随 6.8.0 新增.
- 插件 `API-COVERAGE-HANDOVER.md` 写 StatsOverlay 7 个成员, 实际 6 个. `tools/check_types.py --members` 把 `snapshot()` 返回类型里的 `history` 也算了进去; IMGUI-PLAN 1.3 的 6 是对的. Toast 7, LogWindow 15 等其余数字与读取器相同. 插件仓库本卡不碰, 留作记录.
- 工具代码约 110 行, 比方案估的 50-80 行多: 接口头要去掉类型参数, 成员要跳过嵌套类型, 还要跟随 extends. `relative()` 顺带改为兄弟检出也输出相对路径, 否则矩阵 JSON 的 source 字段带本机绝对路径.

### 28.6 验收

HEAD 副本: 宿主 64ac0e706, 文档 7455375, TS 82814a1, Ace 6cfc79d, Node bd51cb89, Bun ad4eb91, ImGui 836f12f (已在 origin/master, CI 默认检出的就是它; 相对 v1.18.0 的 imgui.d.ts 只改注释, 成员不变). KSP 没有重跑: 复用共享签出的 script-modules.json (sha1 07e72243, 1355737 字节), 模块源码自 89f6a01ae (11-01 20:50) 之后没变.

- `check.py --source ksp --matrix --strict-arity --strict-types --docs-support` 退出 0, failures 空, returned-objects 报告 failures 空, MATRIX current, pending baseline current, 运行时块 102 页 current.
- 工具单测 125 项过 (3 项跳过, 都是 augment 迁移期用例, 与本卡无关), 新增读取用例, 缺仓库 skipped 用例与矩阵规则用例.
- 工作流其余步骤全部退出 0: 16 个 `check-runtime-*`, 7 个 `check-bun-*`, `test-dts-types.mjs`, `language_runtimes.py`, Ace `generate-runtime-api-queries.mjs --check` 与 `verify-runtime-api-coverage.mjs --fresh` (查询清单不变), TS `sync-runtime-declarations.mjs --check`, `verify-runtime-declarations.mjs`, `verify-api-coverage.mjs`.
- 文档 `auto-generate.py --check` 当前 (189 模块), Offline Docs 的 imguiSessionType.html 与 imguiToastType.html 和文档站生成物 blob 相同.

### 28.7 待决与提交

没有新待决, Q251-Q255 未用.

| 仓库 | 提交 | 内容 |
|---|---|---|
| 宿主 | 64ac0e706 | plugin_exports.py, 单测, policy, returned-objects, 规则, 工作流, tools README, MATRIX.md, 同一提交 |
| 宿主 | (本节所在提交) | ROADMAP P5.2 与 D-23, IMGUI-PLAN, 本节 |
| 文档 | 65035cc / 7455375 | 13 个对象页运行时块, 生成物 |
| Offline Docs | f80b11f | 捆绑页 |

Ace 与 TS 没有改动. 版本没有 bump, 留给 P5.3.

## 29. P5.3 阶段收尾 bump (2026-11-02, 子会话 api-cov-p5-3-bump)

### 29.1 结论

P5 全部完成. 按 Q260 (a) 只 bump TS 声明与 Ace, Node / Bun / ImGui / 文档站不动. 矩阵, pending 与 Ace 查询清单都没有变化, 这张卡只改版本, changelog 与一条注释.

### 29.2 版本

| 仓库 | 改动前 | 改动后 | 提交 |
|---|---|---|---|
| TS 声明 | 4.86.0 | 4.87.0 | 170b45b |
| Ace | 2.48.0 / 391 | 2.49.0 / 392, 捆绑声明 4.87.0 | ab0dc66 |
| Node | 2.1.0 / 376 | 不动 | - |
| Bun | 0.22.0 / 349 | 不动 | - |
| ImGui | 1.18.1 / versionCode 63 | 不动 (已发布, tag v1.18.1 = 836f12f) | - |
| 文档 | versionCode 181 | 不动 | - |
| Offline Docs | 6.8.40 / 252 | 不动 | - |

文档 versionCode 与 Offline Docs 的 VERSION_BUILD 是生成时自动 +1 的结果, 发卡时是 180 / 251, 其后 1.18.1 与卡 D 的提交又各加了一次. 上次收尾 (第 21 节) 对这两个仓库同样没有改版本名, 所以 Q261 未用.

### 29.3 做了什么

- TS: `aj6-int-imgui.d.ts` 里 fontScale 的说明删掉不存在的 ui.getFontSize(), 与 ImGui 1.18.1 的 imgui.d.ts 同一行文字; 公开成员不变. changelog 两条: imgui 模块声明, fontScale 说明.
- Ace: 用宿主 `tools/ace-completion/import-autojs6-dts.mjs` 从 TS 170b45b 导入, 在 HEAD 副本里运行, 再把 `aj6-int-imgui.d.ts`, `package.json`, `BUNDLED_DECLARATIONS.md` 三个文件放回共享签出. 静态索引与 lib.autojs6.d.ts 没有变化. VERSION_BUILD 按仓库 AGENTS.md 取可达提交数 + 1, 提交后 392 = 392. changelog 两条: imgui / $imgui 在 Rhino 脚本中的补全, 捆绑声明 4.87.0.
- changelog 只写 zh-Hans, 用各仓库生成脚本的渲染函数跳过跨语言校验生成. Q136 的条目没有写.

### 29.4 验收

HEAD 导出副本 `E:/.cache/aj6-p5-3-bump-head` (宿主 d9acac927, TS 170b45b, Ace ab0dc66, 文档 7455375, Node bd51cb89, Bun ad4eb91, ImGui 65315bc). KSP 元数据没有重新构建: 共享签出 13:37 的 script-modules.json 与另外两份副本的 md5 相同 (0e2c5170), 其输入目录的最后一次改动是 89f6a01ae (11-01 20:50), 早于这三份构建, 复制到副本使用.

| 检查 | 结果 |
|---|---|
| 宿主 `check.py --source ksp --matrix --strict-arity --strict-types --docs-support` (含 Ace) | 退出 0, failures 空, pending 0 / 0 / 0, MATRIX.md 与 pending-baseline.json current, 文档 102 页 current, 未覆盖 0 / 0, 查询路径 2262 / 失败 0 |
| Ace `generate-runtime-api-queries --check` | changed false, 查询 5482, 行 2054 (不变) |
| Ace `verify-runtime-api-coverage --fresh`, `verify-api-coverage`, 宿主 `generate-editor-assets --check` | 退出 0 (副本先用 generate-declarations 生成 aceLspAssets) |
| Ace `prepush_check.py --no-gradle` | 构建号 drift 0, 图标资源与 Python 测试通过; 生成文档一项失败, 见下 |
| TS `npm test`, `npm run test:imgui`, `sync-runtime-declarations --check` | 退出 0, imgui 示例 10 个 0 诊断, 声明 95 个 changed 0 |
| getFontSize grep | TS 0, Ace 0 |

预期失败, 都等维护者同步其他 9 种语言:

- TS 与 Ace 的 `generate_markdown --check`.
- TS 的 `.python/tests` 13 个 error, 都在 setUp 的跨语言校验. 这不是本卡引入的: 42291f0 之前通过, 本卡之前的 82814a1 已是同样的 13 个 error. 这项在 TS CI 里, 推送前要先同步语言.

Gradle 没有跑: 本卡只改声明注释, 版本号与文档, 机器上还有 rhino-q51 的 JMH 与 aj6-q50 的构建在跑.

### 29.5 与卡片的出入

- Ace 的 `aj6-int-speech.d.ts` 没有导入. 导入脚本会把它一起带上 (4 行, TS d830b2b), 而 Ace 6cfc79d 写明这份留给 3-Macaw, 所以放回共享签出时只取了上面三个文件. 问题是 Macaw P9.5.7 的原文只记录提交状态, 没有导入动作, 登记 Q262.
- 卡片说 Q136 的 Ace 条目由 3-macaw 发版时写, 实际 6cfc79d 已把一条写进 v2.48.0 的优化项. 本卡没有改动它.

### 29.6 待决与提交

新待决 Q262 (已按推荐先行待确认), Q261 与 Q263-Q265 未用.

| 仓库 | 提交 | 内容 |
|---|---|---|
| TS | 170b45b | 4.87.0, 注释, changelog |
| Ace | ab0dc66 | 2.49.0 / 392, 捆绑声明 4.87.0, 注释, changelog |
| 宿主 | (本节所在提交) | ROADMAP P5.3 与附录 C, 本节 |

## 30. P6.2 ColorOS 抽样 (2026-11-02, 子会话 api-cov-p6-2-coloros)

OnePlus OPD2413 (ColorOS V15.0.1, API 35) 上抽样新增能力的授权, provider 绑定与前台服务, 报告 [P6-2-COLOROS-2026-11-02.md](P6-2-COLOROS-2026-11-02.md), 脚本 `tools/api-coverage/device_p6_2_coloros.py`. 不重复 P3.5 / P4.5 的 78 项四形态与 P4.2b 的 Bun 拒绝轮.

| 项 | 结果 |
|---|---|
| 授权 13 类 21 项, 五形态 | 全过, `shizuku.exec` 因服务未运行跳过 |
| 收回授权 7 项 | 全部给出错误或 false, 不挂起 |
| provider 绑定与重绑, 六类 provider 五形态 + 运行时 | 34 / 34: 宿主前台, 后台, 无悬浮窗启动宽限内 |
| 脚本中途杀 provider / 运行时 / 宿主 | 全过 |
| `PluginAutoWakeDeviceTest` / MCP 抽屉监听器重绑 | 5 项 / 4 项 |
| 录屏 | 五形态, 服务都在前台 |
| Q228 (b) 提示 | 走通, 系统页 `AppManageExternalStorageActivity` |
| 后台 60 s, 五组 | 前台服务或绑定在时 20 / 20; 都没有时 4 ~ 5 / 20 |

失败项判定:

- ColorOS 限制 1 条: 宿主进程崩溃或被系统杀掉后, 无障碍服务不自动重绑 (Q240, 文档写明, 矩阵不加标注).
- 后台冻结不是 ColorOS 限制: `use_freezer=true`, `freeze_debounce_timeout=10000`, AOSP API 35 模拟器同样停住 (Q241). 回到前台后 Rhino / Node 继续, Bun 场景宿主被系统杀掉.
- 真缺陷 1 个: 无 su 设备上两个首次 root 检查同时进行时丢应答, Bun 并发 `getAccess` 从 10 轮 8 次超时到 0 次, 中位 358 ms.

落点:

| 仓库 | 提交 | 内容 |
|---|---|---|
| 宿主 | 803bf1e65 | `RootUtils` 串行, `RootUtilsRootShellTest` |
| 宿主 | (本节所在提交) | 脚本, 报告, README, ROADMAP 勾选 P6.2, 附录 C Q240 / Q241, 本节 |
| 文档 | eaf4d58 | `runtimeApis` "宿主在后台" 一节; 生成物留给下次生成 |

Q242 ~ Q245 未用. 矩阵与 `pending-baseline.json` 不变. 版本没有 bump.
