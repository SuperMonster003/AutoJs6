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
