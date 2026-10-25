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
