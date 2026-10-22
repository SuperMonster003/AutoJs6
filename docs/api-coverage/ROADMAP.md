# AutoJs6 脚本 API 覆盖路线图 (api-coverage)

立项: 2026-10-23 (立项会话 local_a29026cd-0d11-4ed3-a101-5df2b6300d82, Fable 5.1, 五轮选择题 Q1-Q19 定稿). 看板: `D:/idea-projects/.claude/boards/api-coverage.md`. 调度: 由维护者在新会话 (桌面端 dispatch 环境) 输入 `/dispatch api-coverage` 启用, 按看板分批派发 Opus 5.5 子会话.

维护约定: 条目完成时把 `[ ]` 改为 `[x]` 并在该行追加落点 (提交 hash, 报告路径, 日期); 需要维护者拍板的事项登记到附录 C 与看板待决事项, 不自行采用默认值; 本文件属宿主仓库, 只本地提交, 不推送 (D6).

## 1. 目标与范围

本路线图回答两个问题并把答案变成可重复运行的门禁:

1. 宿主 `augment` 包面向脚本的 API, 在 AutoJs6-Documentation, AutoJs6-TypeScript-Declarations 与 Ace 编辑器智能提示中是否全面覆盖. 目标粒度是签名级 (名称, 重载数与参数个数, 之后是参数 / 返回类型) 并把返回对象的成员纳入分母 (D3, D9).
2. Node.js 运行时插件 (主线程与 Worker) 与 Bun 运行时插件是否全面覆盖 AutoJs6 API. 目标是函数级覆盖矩阵: 每个显式导出对每个运行时都有明确状态 (bridged / local-shim / capability-gated / not-portable / denied / pending), 终态是 pending 为零, 排除项全部有拍板过的理由 (D1, D2, D12).

范围内的仓库与产物:

| 仓库 | 本主题改动的部分 |
|---|---|
| AutoJs6 (宿主) | `tools/api-coverage/` 门禁与新增的矩阵 / 签名 / 文档表生成器; `docs/api-coverage/` 路线图, 矩阵与报告; Node / Bun 桥接的宿主侧 (`NodeBridgeProtocol`, provider, 能力清单); `tools/ace-completion/` 静态索引生成 |
| AutoJs6-Documentation | `api/*.md` (约 170 页) 的标题级条目, 签名与示例, 每页顶部生成的 "运行时支持" 表 (D13); 生成器 `generator/auto-generate-for-autojs6.bat` 同时产出 AutoJs6-Plugin-Offline-Docs |
| AutoJs6-TypeScript-Declarations | `declarations/autojs6/aj6-int-*.d.ts` (85 个文件), `declarations/autojs6-node` / `autojs6-bun` 的同步 (`tools/sync-runtime-declarations.mjs`), `tools/verify-api-coverage.mjs`, `docs/ROADMAP.md` 的矩阵任务标注取代 (D7) |
| AutoJs6-Plugin-Ace-Editor | 捆绑声明 `types/`, 静态补全索引, `tools/ace-lsp/verify-runtime-api-coverage.mjs`, `BUNDLED_DECLARATIONS.md` (D16) |
| AutoJs6-Plugin-NodeJs-Runtime | `node_bridge_sources.cpp` 内嵌 facade, `docs/nodejs/types/autojs6-node/*.d.ts` (76 个), `docs/HOST-API.md`, Worker 封装 |
| AutoJs6-Plugin-Bun-Runtime | `app/src/main/assets/autojs6/host-client.d.mts` 显式客户端与能力授权, `docs/host-api.md` |
| AutoJs6-Plugin-APK-Builder-Template | 仅在打包应用验证需要时更新 Runtime Kit 配对 (D11, D16) |
| 官方插件 (3-Stamp Mail, 3-Macaw Speech, 3-Folio EPUB, 3-Maple Image, ImGui, TypeScript Engine 等) | P5 盘点其不经 augment 注册的自有脚本 API, 纳入范围由维护者拍板后再补齐 (D17) |

不做的事:

- 不把 Bun 做成 Rhino / Node 兼容层, 不为缺失的 globals 提供隐式回退 (Bun ROADMAP "边界与非目标"); Bun 的覆盖以 host-client 显式入口加能力授权为准 (D2).
- 不用元数据生成 Node / Bun 桥接分发代码 (augment 附录 C Q3 已拍板, 对象句柄 / 回调 / 权限 / 异步语义无法从签名推出).
- 不纳入 VSCode 扩展 (不消费 d.ts), 不纳入 android-dts-generator 产出的 Java 对象声明 (`android.d.ts`, `aj6-main-app.d.ts`) 的覆盖度 (D16).
- 不推送, 不打 tag, 不发布 (D6); 发布集中在 P7, 由维护者逐仓库指示.
- 不在阶段内 bump 版本 (D14, D19).

## 2. 现状基线 (2026-10-23)

### 2.1 Codex 已完成的工作 (2026-10-15 至 2026-10-22)

维护者 2026-10-15 起在 Codex 会话中先后提出需求 1 (augment API 的文档 / 声明 / Ace 覆盖) 与需求 2 (Node.js / Bun 覆盖), 之后以 "请继续按审计报告推进, 下一批优先补齐 X" 逐批驱动. 载体与产物:

- 宿主 `tools/api-coverage/` (首提交 47a2a0a40): `extract_exports.py` (解析 Kotlin 注册, 未识别的注册表达式报 unresolved 并使门禁失败), `check.py` (名称级: 文档页证据按 `policy.json` 的 documentationModules 映射; `verify-surfaces.mjs` 做 TypeScript 类型解析与 Ace 默认 / 全组语义补全与静态补全查询; 输出 exports.json / documentation.json / surfaces.json / summary.json 到 `build/reports/api-coverage`), `policy.json` (documentationModules, internalExports, standardExports, dynamicRegistrations, returnedObjectProbes), `test_coverage.py`, 按模块的 `check-runtime-*.mjs` / `check-bun-*.mjs` / `check-ui-compose.mjs` / `check-runtime-views.mjs` / `check-webview-callbacks.mjs` / `check-runtime-bridges.mjs` (比对 Node 内嵌 facade 与宿主方法集), CI 工作流 `.github/workflows/api-coverage.yml` "Script API coverage". README 自述: 这是名称与类型解析级门禁, 不证明每个运行时重载.
- 总审计报告 `docs/dev/runtime-api-coverage-2026-10-17.md` 与 36 份批次证据报告 (清单见附录 A). 每批同步宿主实现, Node facade 与 d.ts, Bun host-client, 文档, TS 声明, Ace 提示, 并在 Redmi (API 33) 与 Xiaomi (API 35) 做真机与独立签名打包应用验证 (每批 56 ~ 185 项). 覆盖的模块批次: 坐标 / 控制台 / 拼音, OpenCC + 图像 (81 个 images 函数), 截图录屏 (Node / Bun / Worker), Compose / UI, View 互操作, XML 控件 (58 个 View 属性), WebView / Drawer / Toolbar / ViewPager, WebView 下载 / 文件 / 相机 / 音视频 / 屏幕共享, Bun 无障碍 / app / 剪贴板, 文件 / 存储, 数据库, 传感器, Shell + Shizuku, 事件 / 通知, 设备 / 对话框, Android 对象 / 任务, Java 互操作与回调 / 类加载, TTS (31 方法), speech (18 方法), EPUB (20 根方法), mail (57 根方法), flow (52 根方法 + 6 原型), AI provider, AI Agent (9 根方法 + 7 AgentRun 方法).
- 每份报告都注明 "不声称 Rhino 完整运行时等价". 报告列出的剩余边界: 同步 Rhino 终端, 任意 Java 对象 / 原型行为, 完整的 Rhino XML 控件与表达式绑定面, native 库与 `rhino.run`, 以及 "其他审计差异" (没有统一清单, 这正是本路线图要补的).

### 2.2 门禁基线数字 (本会话 2026-10-23 运行 `python tools/api-coverage/check.py`, 含 Ace)

| 指标 | 数值 |
|---|---|
| 注册路由 (registrationRoutes) | 92 |
| 显式导出 (explicitExports) | 1363 (74 个顶层名称, 其中 59 个模块有 2 个以上导出) |
| 文档证据 | 标题级 1261, 仅正文提及 94 (mail 47, flow 25, tts 7, 全局函数 11, Mathx 2, automator 1, util 1) |
| TS / Ace 查询路径 | 1598, 声明解析 0 失败 |
| Ace 静态补全索引失败 | 9 (memory 7: `$memory`, `memory`, collect / dumpHeap / retained / snapshot / watch; settings 2: categories / export) |
| 未解析注册 (unresolved) | 2 (`colors` 的颜色表 selfAssignmentProperties, `compose` 的函数表 lazy 映射) |
| 门禁自述排除 | 继承的 Java 原型成员, 返回对象, 嵌套动态对象, 颜色常量, 生成的 Compose 目录, JSOX 原型扩展, 别名展开, 重载语义 |

### 2.3 Node / Bun 模块级初判 (启发式, 以 P0.2 矩阵为准)

按 "模块名或别名在运行时声明中出现" 的启发式比对 59 个有 2 个以上导出的模块:

- Node d.ts 未见 10 个模块, 共 93 个导出: continuation (2), crypto (2), floaty (8), gallery (11), installer (16), qrcode (4), species (37), sysprops (4), web (4), zip (5). 已有同名或别名模块的函数级缺口未知.
- Bun host-client 未见 28 个模块, 共 346 个导出: Arrayx / Mathx / Numberx / jsox (JSOX 扩展), autojs (28), barcode, continuation, crypto, cvt, engines, floaty, fmt, gallery, http (Bun 自带 fetch), installer, mediainfo, ocr, plugins, qrcode, s13n, species, sysprops, threads, timers (Bun 自带), util, web, workManager, zip. 其中 http / timers / util / jsox / threads 预计多数落入 not-portable 或 local-shim.
- Node 历史矩阵 `docs/nodejs/history/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md` (2026-09-10 归档, 879 条: supported 149 / supported_async 66 / partial 320 / blocked_by_design 45 / renamed 1) 早于 Codex 全部批次, 只作分类参考.

### 2.4 各仓库版本与未推送状态

| 仓库 | 版本 | 领先远端 |
|---|---|---|
| AutoJs6 | 6.8.0, VERSION_BUILD 4283 (versionCode 5935), HEAD 9f31f2c79; 工作区另有其他会话在改的约 36 个文件 | 只本地 |
| AutoJs6-TypeScript-Declarations | 4.81.0 (e05f24e) | 51 提交 |
| AutoJs6-Plugin-Ace-Editor | 2.43.0 / build 329 (be022c2) | 50 提交 |
| AutoJs6-Plugin-NodeJs-Runtime | 1.32.0 / build 292 (6d4e6bdb) | 54 提交 |
| AutoJs6-Plugin-Bun-Runtime | 0.21.0 / build 296 (fb879f3) | 53 提交 |
| AutoJs6-Plugin-APK-Builder-Template | 1.2.26 / build 111, 配对宿主 versionCode 5850 | 40 提交 |
| AutoJs6-Documentation | 644d273 | 已推 |
| AutoJs6-Plugin-Offline-Docs | 6.8.40 / build 205 | 已推 |

### 2.5 关联路线图

- augment 重构 (`docs/augment/ROADMAP.md`, 看板 `.claude/boards/augment.md`, 当前 P3): T10 (P5 起 `extract_exports.py` 与 dts 生成器改读 KSP 产出的 `script-modules.json`), D6 (同一份元数据供 d.ts 与文档), P5.3 (文档 / d.ts 同步 + 附录 C Q3 的桥接覆盖报告). 本主题接管其中的覆盖产出 (D4): 覆盖矩阵, 桥接覆盖报告, 文档 / d.ts / Ace 偏差同步, 以及 Q3 的另一项 "NodeJs Runtime 插件 d.ts 生成 (`node-types.gradle.kts`) 改读 `script-modules.json` 参数规格" (并入 P2.4); augment 只保留 `script-modules.json` 的产出与 T10 的改读. 2026-10-23 已按维护者指示通知 augment 调度会话登记这一范围调整.
- TS 声明仓库 `docs/ROADMAP.md` (2026-07-27): Phase 0 的三项矩阵任务与 Phase 7 的 "WebStorm 补全看到新 globals" 未勾; 本主题 P0.2 完成后把矩阵任务标为被取代 (D7).
- Node 插件 ROADMAP "五. 明确不做的事" 与 `docs/HOST-API.md` "八. 明确不可用 (denied)"; Bun ROADMAP "边界与非目标". 三者是附录 D 排除白名单草案的来源 (D12).

## 3. 固定决策 (五轮选择题, 2026-10-23, 不再重问)

| 编号 | 决策 | 维护者选择 |
|---|---|---|
| D1 | Node.js 覆盖的判定基准 | 函数级矩阵: 以宿主显式导出为分母 (现 1363, T10 落地后改读 `script-modules.json`), 每条标状态, 机读矩阵接入门禁; 参数与返回全为原始值 / JSON 的函数必须有 Node 入口; 同步终端 / Java 原型 / 对象型按白名单排除 |
| D2 | Bun 的覆盖标准 | 与 Node 同一分母同一矩阵, 多两类状态 capability-gated 与 not-portable (写明理由); 目标是每个可移植 API 在 host-client 有显式入口或有记录的排除理由 |
| D3 | 文档 / d.ts / Ace 的覆盖粒度 | 签名级 + 返回对象: d.ts 的参数 / 返回类型与重载数与 Kotlin 注册一致, 文档有独立标题 + 签名 + 至少一个示例, Ace 补全解析到同一签名; UiObject / ImageWrapper / Flow 句柄等返回对象的成员纳入分母 |
| D4 | 与 augment 主题的边界 | 本主题接管覆盖产出 (矩阵, 桥接覆盖报告, 文档 / d.ts / Ace 偏差同步); augment 只负责 `script-modules.json` 与 T10 改读; 前期基线读 Kotlin, T10 落地后切数据源; 两份 roadmap 互登交叉引用 |
| D5 | 对 Codex 36 批成果的审查深度 | 静态全量 + 真机抽样: 矩阵对全部批次机器核对; 真机按模块族抽 10 个 (两台设备各 5 个), 发现问题再扩大 |
| D6 | 推送与发布 | 本主题不推不发, 各阶段只本地提交; 末阶段 P7 列出每个仓库的版本与前置 (宿主 6.8.0 公开后再推 Node / Bun / Ace / TS), 由维护者逐仓库指示; 文档仓库按惯例可推 |
| D7 | 矩阵与新工具的落点 | 宿主 `tools/api-coverage/` 扩展, 产物 `build/reports/api-coverage/runtime-matrix.json` 与人读的 `docs/api-coverage/MATRIX.md`, 接入 CI "Script API coverage"; TS 仓库的矩阵任务与 Node 历史矩阵标为被取代 |
| D8 | Codex 的后续角色 | 后续阶段全部由看板调度的 Opus 5.5 子会话执行, Codex 不再参与; 总审计报告封存为历史, 只追加一行 "由 docs/api-coverage/ROADMAP.md 接管" |
| D9 | 签名级比对的数据源与时序 | 两步走: 先用 `ensureArguments*` (7 种, 约 1084 处) 提取参数个数区间与 d.ts 重载比对; 类型级比对按模块在 `script-modules.json` 覆盖到该模块后开启, 矩阵每行标元数据来源 kotlin / ksp |
| D10 | 真机设备 | 主力 Redmi 22120RN86C (bek749scrwv4wo8h, API 33) + Xiaomi 23046RP50C (968e9f18, API 35) 延续 Codex 基线; 每个补缺阶段末加 1 轮 G8441 (BH900ASK9E, API 28) 最低版本抽样与 1 轮 OnePlus OPD2413 (63751f63, ColorOS 15) 授权 / 唤醒抽样; 与其他主题共用设备时按看板协调 |
| D11 | Node Worker 与打包应用验证 | Worker 是 Node 矩阵的独立一列 (main / worker), 每条补缺必须覆盖; 打包应用 (APK Builder 独立签名) 验证只在补缺涉及新权限入口 / provider / 服务绑定时做 |
| D12 | 排除白名单 | 本文件附录 D 为草案 (每条带理由分类); P1 首批子会话按实际导出核对后交维护者一次性拍板; 之后新增排除项一律登记待决 |
| D13 | 文档页呈现 Node / Bun | 每个模块页顶部一个由矩阵生成的 "运行时支持" 表 (Rhino / Node main / Node Worker / Bun 的状态与入口名, 按函数列差异项), 门禁校验不漂移; 参数差异与异步化细节留在 runtimeApis.md 与插件文档 |
| D14 | 版本与 changelog 节奏 | 阶段内不 bump; 每个阶段的收尾条目统一 bump + changelog (只写 zh-Hans, 其他语言由维护者同步) |
| D15 | 并行度 | 静态类条目不限并行 (共用宿主工作区只提交自己的文件); 真机类同时最多 2 个子会话, 各绑定一台主力设备; 补缺按模块族分批, 每批 3 ~ 5 个模块 |
| D16 | 外围范围 | 纳入: Ace 运行时指令 (Node / Bun) 提示门禁与 `BUNDLED_DECLARATIONS.md` 版本修正; APK Builder 模板 Runtime Kit 配对 (按需); 官方插件自身的脚本 API (先盘点). 不纳入: VSCode 扩展, 宿主 Java 对象声明 |
| D17 | 官方插件自身的脚本 API | 先盘点 (P5.1) 每个官方插件的自有脚本入口与条目数及其文档 / d.ts / Ace / Node / Bun 现状, 结果交维护者拍板纳入哪些; 之后按 augment 相同规则补齐; 不阻塞前面阶段 |
| D18 | 阶段划分 | P0 基线与工具 -> P1 Codex 成果审查 -> P2 Rhino 面文档 / d.ts / Ace 补齐 -> P3 Node (含 Worker) 补缺 与 P4 Bun 补缺 (可并行) -> P5 官方插件自有 API -> P6 阶段末抽样与终态报告 -> P7 发布 |
| D19 | 版本归属 | 每个阶段末只 bump 该阶段改到的仓库 (P2 末: TS 声明 / Ace / 文档; P3 末: Node + 宿主构建号; P4 末: Bun), 未改动的不动 |

## 4. 覆盖矩阵定义

- 分母 (行): `exports.json` 的显式导出 (模块, 名称, 别名, 注册种类, 源码位置), 加 P0.4 纳入的返回对象成员 (以 `policy.json` 的 returnedObjectProbes 为起点扩展为 returnedObjects 表: 对象名, 产生它的函数, 成员清单来源). T10 落地后分母改读 `script-modules.json`, 行 id 保持 `模块.名称` 不变.
- 列: `rhino` (恒为 native), `doc` (none / mention / heading / signature / example), `dts` (none / name / arity / typed), `ace` (none / semantic / static), `node_main`, `node_worker`, `bun`.
- 运行时状态枚举: `bridged` (经宿主桥接, 同步或异步均可, 异步的另记 async 标志), `local-shim` (运行时侧纯 JS 实现, 行为与 Rhino 一致并有测试), `capability-gated` (有入口但需显式能力授权; 仅 Bun 与 Node 的 profile 能力), `not-portable` (运行时自带等价物或语义上不可移植, 写明理由分类), `denied` (安全或设计上结构性拒绝, 写明理由分类), `pending` (应覆盖而尚未覆盖). 终态: 全部行无 pending.
- 理由分类 (not-portable / denied 必填): `security` (硬件标识符, 原始输入通道), `sync-only` (同步阻塞终端, 如 threads / continuation / sleep 类), `java-prototype` (任意 Java 对象 / 原型, 需句柄而非值), `runtime-native` (运行时自带等价物, 如 Node http / timers / util / crypto / zip, Bun fetch / timers), `runtime-builtin` (运行时门禁 BUILTIN_DISABLED, 如 Node 的 6 个可遮蔽 shim), `rhino-only` (Rhino 引擎本身的能力, 如 `rhino.run`, JSOX 原型扩展), `ui-thread` (必须在 Rhino UI 线程同步执行且无异步等价). 每个 not-portable 的 `runtime-native` 项须在矩阵写出运行时等价写法, 供文档 "运行时支持" 表引用.
- 数据源: Node 列读 `docs/nodejs/types/autojs6-node/*.d.ts` (`declare module` 名与 `$autojs.<module>` 显式入口), `docs/HOST-API.md` 的状态表 (bridged / bridged-host-provider / bridged-policy / local-shim / denied), `node_bridge_sources.cpp` facade 名 (复用 `check-runtime-bridges.mjs` 的解析); Worker 列读 Worker 封装与 Codex 各批的 Worker 测试清单; Bun 列读 `host-client.d.mts` 的命名空间成员与能力常量. 人工标注 (排除理由, 别名映射) 放 `tools/api-coverage/runtime-policy.json`, 不写进生成产物.
- 产物: `build/reports/api-coverage/runtime-matrix.json` (机读, CI 产物), `docs/api-coverage/MATRIX.md` (人读, 按模块汇总 + 差异行; 由生成器写, 提交入库, 门禁校验与 json 一致), 文档页的 "运行时支持" 表 (P0.5 生成器从同一 json 产出).

## 5. 阶段与条目

条目格式: 编号, 内容, 验收标准; 标注 [静态] (无设备) 或 [真机: 设备]; 并行与依赖写在阶段头或条目内. 子会话一律 claude-opus-5-5, context fresh, 250k 窗口, 共用宿主工作区时只提交自己的文件, 不推送, 不切分支, 不碰其他会话在改的文件, 宿主构建与其他会话排队.

### P0 基线与工具 [静态, 条目间可并行, P0.3 / P0.4 / P0.5 依赖 P0.2 的行 id 约定]

- [ ] P0.1 基线与门禁修复. 修 9 条 Ace 静态索引失败 (在 Ace 仓库按 `tools/ace-completion/README.md` 用 `generate-editor-assets.mjs` 重新生成静态索引, 确认 `--check` 通过), 修 2 条 unresolved 注册 (colors 颜色表与 compose 函数表: 扩展 `extract_exports.py` 解析器或登记为 `policy.json` dynamicRegistrations 并补显式清单, 二选一且写明理由), 核对 CI 工作流是否在跑含 Ace 的 `check.py` (若 CI 没跑 Ace, 补上并让 9 条失败在 CI 可见), 在 `docs/dev/runtime-api-coverage-2026-10-17.md` 末尾追加一行接管说明 (D8). 验收: `python tools/api-coverage/check.py` 退出码 0, summary.json 无 failures, unresolved 为 0; 基线数字写进 `docs/api-coverage/BASELINE-2026-10.md`.
- [ ] P0.2 运行时覆盖矩阵生成器 `tools/api-coverage/runtime_matrix.py` (或 .mjs, 与现有工具同语言优先 Python) 按第 4 节定义产出 runtime-matrix.json 与 MATRIX.md, 新增 `runtime-policy.json` (别名映射, 排除理由, 初始内容取附录 D 草案), `check.py` 新增 `--matrix` 把 pending 计数与排除理由缺失列为失败项 (首版只报告不失败, 待 D12 拍板后改为失败), 单元测试覆盖解析与状态推导. 同时在 TS 仓库 `docs/ROADMAP.md` 把 Phase 0 三项矩阵任务标注 "由 AutoJs6 docs/api-coverage 接管", 在 Node 仓库历史矩阵顶部追加同样一行. 验收: 矩阵行数 = 显式导出数; 对 Codex 36 批涉及的模块抽 3 个 (images, flow, mail) 人工核对状态无误; MATRIX.md 入库.
- [ ] P0.3 参数个数提取与比对. `extract_exports.py` 为每个函数记录 `ensureArguments*` 推出的参数个数区间 (OnlyOne / Length / LengthInRange / AtMost / AtLeast / IsEmpty / NotEmpty; 无法推出的记 unknown 并计数), `verify-surfaces.mjs` 取 d.ts 各重载的必需 / 可选参数个数, 比对规则写进 README (区间不相交为失败, d.ts 重载更宽为警告), `check.py` 新列 `arity`. 验收: 失败与警告清单入 BASELINE 文件; 比对误报率在 flow / images / files 三个模块人工核对后低于 5%, 否则调整规则再验收.
- [ ] P0.4 返回对象成员纳入分母. 以 `policy.json` returnedObjectProbes 为起点列出返回对象 (UiObject / UiSelector / UiObjectCollection, ImageWrapper, Flow 相关句柄, Mail / EPUB / Agent 句柄, ShellProcess, Dialog, Canvas / Paint 等), 为每个对象定义成员清单来源 (Kotlin 类的公开 Rhino 可见成员或 augment 原型注册), `extract_exports.py` 输出 `returnedObjects` 段, 名称级门禁 (文档 / d.ts / Ace) 覆盖这些成员, 矩阵为其增加行 (运行时列允许 `java-prototype` 排除). 验收: 返回对象数与成员数入 BASELINE; 门禁对它们的失败项单独列出 (不阻塞 CI, 供 P2.3 消化).
- [ ] P0.5 文档 "运行时支持" 表生成器 (D13). 文档仓库新增 `generator/runtime-support.js` (或宿主 `tools/api-coverage/render_doc_support.py`, 二选一并写明理由) 读 runtime-matrix.json, 在每个模块页顶部的标记块 (`<!-- runtime-support:begin -->` 到 `end`) 之间写入 / 更新表格: 四列状态与入口名, 下方按函数列差异项 (非 bridged 的行), `not-portable` 项附等价写法; `check.py --docs-support` 校验标记块内容与矩阵一致. 验收: 在 3 个模块页 (images, files, flow) 试插入并通过文档仓库现有的 markdown 检查; 全量插入留给 P2.5.
- [ ] P0.6 Ace 运行时指令提示门禁 (D16). Ace 仓库 `tools/ace-lsp/verify-runtime-api-coverage.mjs` 的查询清单改由 runtime-matrix.json 生成 (Node / Bun 列为 bridged / local-shim / capability-gated 的行都要有查询), 修正 `BUNDLED_DECLARATIONS.md` 的版本与来源 (现写 4.42.0, 实为 4.81.0), TS 仓库 `tools/sync-runtime-declarations.mjs` 的摘要校验纳入宿主 CI 工作流. 验收: Ace 运行时查询数不低于现有 1172 且全部通过; 捆绑声明与 TS 仓库 `declarations/runtime-declarations.json` 摘要一致.

### P1 Codex 成果审查 [P1.1 静态, 可与 P0 并行但依赖 P0.2 产物; P1.2 / P1.3 真机, 各绑定一台设备, 可同时进行]

- [ ] P1.1 静态全量审查 [静态]. 用矩阵与门禁对 36 份批次报告涉及的模块逐一核对: 报告宣称的方法名数 (如 flow 52 + 6, mail 57, speech 18, images 81) 与矩阵 bridged 行数是否一致; Node d.ts / HOST-API 状态 / facade 名 / Bun host-client 四处是否一致; 文档 / TS 声明 / Ace 是否都有对应条目; 报告列出的边界是否都在附录 D 草案里 (不在的补进草案并标 "Codex 报告来源"). 产出 `docs/api-coverage/REVIEW-codex-2026-10.md`: 不一致清单 (每条写模块, 名称, 四处现状, 建议归属 P2 / P3 / P4 或直接修), 以及附录 D 草案的核对版. 验收: 36 份报告全部核对; 小修 (单文件, 不涉及运行时行为) 直接提交并记 hash, 其余登记为后续阶段条目编号.
- [ ] P1.2 真机抽样 A [真机: Redmi API 33]. 模块族: 图像 (images 含截图), UI / Compose 与 View 互操作, WebView (下载 / 文件 / 媒体任选 1 批), shell + Shizuku, 事件 / 通知. 用 Codex 现成的 `check-runtime-*.mjs` / `check-bun-*.mjs` 与批次报告里的设备用例子集 (每族 10 ~ 20 项, Node main + Worker + Bun), 不重跑打包应用 (D11). 使用前核对设备当前宿主版本与其他主题的占用 (附录 C Q4). 验收: 每族通过 / 失败与关键数值写进 REVIEW 文件; 失败项登记为 P3 / P4 条目; 设备恢复到抽样前状态 (记录安装包哈希).
- [ ] P1.3 真机抽样 B [真机: Xiaomi API 35]. 模块族: Java 互操作与回调, flow, mail (需 3-Stamp Mail 测试账户, 沿用 Codex 批次的账户配置), speech (需 3-Macaw Speech), ai (需 provider 配置与 Agent 授权). 规模与验收同 P1.2.
- [ ] P1.4 审查结论与白名单拍板 [静态]. 汇总 P1.1 ~ P1.3, 把附录 D 核对版整理成选择题材料 (按理由分类分组, 每组给条目数与代表项) 交调度会话请维护者一次性拍板 (D12); 拍板后更新 `runtime-policy.json`, `check.py --matrix` 改为 pending 与缺理由即失败. 验收: 附录 D 标 "已拍板 (日期)"; 矩阵 pending 总数与按模块分布写进看板, 作为 P3 / P4 分批依据.

### P2 Rhino 面的文档 / d.ts / Ace 补齐 [静态, 可与 P3 / P4 并行; P2.1 / P2.2 / P2.3 可并行, P2.4 按模块随 augment 进度, P2.5 依赖 P0.5 与 P1.4]

- [ ] P2.1 94 项仅提及条目补为标题级 (mail 47, flow 25, tts 7, 全局 11: clickBoundsWait / clickBoundsWhenStable / clickWait / clickWhenStable / toasterror / toastinfo / toastlog / toastverbose / toastwarn / waitAsync / waitForGone, Mathx 2, automator 1, util 1), 每项独立标题 + 签名 + 至少一个示例 (D3); 全小写 toast 别名与 Mathx 别名可在正式条目下用 "别名" 小节并让 `policy.json` 识别别名标题. 验收: documentation.json 的 mention 为 0; 文档生成器跑通, Offline Docs 随之更新 (不 bump 版本).
- [ ] P2.2 参数个数不一致项修复 (P0.3 清单): d.ts 补重载或修参数可选性, 文档签名同步, Ace 重新导入声明 (`import-autojs6-dts.mjs` + 索引生成). 宿主侧发现 `ensureArguments*` 与实际用法矛盾的, 登记为宿主修复项 (本主题只记不改, 交 augment 主题或维护者). 验收: `check.py` arity 列失败为 0, 警告逐条有结论.
- [ ] P2.3 返回对象成员补齐 (P0.4 清单): 文档在产生该对象的页面下设对象小节 (或独立页, 沿用现有 UiObject / ImageWrapper 页的做法), d.ts 补成员, Ace 索引更新. 验收: 返回对象成员的名称级门禁失败为 0.
- [ ] P2.4 类型级比对 (D9 第二步). 当 augment 主题为某模块产出 `script-modules.json` 参数规格后, `verify-surfaces.mjs` 对该模块比对参数 / 返回类型, 矩阵该模块行标 `ksp`; 不一致项修 d.ts 与文档. 同一批模块接入时, Node 插件的 d.ts 生成 (`node-types.gradle.kts`) 改读 `script-modules.json` 的参数规格 (augment 附录 C Q3 原第二项, 按 D4 移交), 使 autojs6-node 的 d.ts 与宿主签名一致, 差异由 P0.6 的 Ace 运行时门禁与 TS 仓库 `sync-runtime-declarations.mjs` 校验. 每接入一批模块记一行 (模块名, 日期, 不一致数). 验收 (阶段性): 每批接入模块的类型比对失败为 0; 全部模块接入后本条目勾选. 此条目的节奏由 augment 看板 P5 进度决定, 调度会话跟踪.
- [ ] P2.5 文档 "运行时支持" 表全量插入 (约 170 页), 文档生成器全量运行, `check.py --docs-support` 接入 CI. 验收: 全部模块页有标记块且与矩阵一致; 文档仓库 markdown 检查通过.
- [ ] P2.6 阶段收尾: bump TS 声明 (minor), Ace (minor, 重新捆绑声明), 文档 changelog (zh-Hans) 与 Offline Docs 版本; 登记到看板 "已完成批次摘要". 不推送.

### P3 Node.js (main + Worker) 补缺 [按矩阵 pending 分批, 每批 3 ~ 5 个模块; 每批 = 1 个子会话 = 宿主桥接 + facade + d.ts + HOST-API + 文档运行时表 + 真机验证 (一台主力设备), Worker 必做, 打包按需 (D11)]

分批由 P1.4 后的矩阵 pending 分布决定, 建议模块族 (以 2.3 节初判为起点):

- [ ] P3.1 系统与设备族: settings, sysprops, powerManager, installer, memory (只读诊断), autojs 运行时信息, notice 与 notifications 的函数级差异.
- [ ] P3.2 文件与数据族: zip, crypto (与 Node 自带 crypto 的分工: 宿主实现的文件级 / 算法别名走 local-shim 或 not-portable, 逐条写理由), gallery, base64 / cvt / fmt / species / s13n 工具函数 (预期多为 local-shim).
- [ ] P3.3 UI 族: floaty 与 ui.overlay 的差异, canvas, web (InjectableWebView / WebSocket 差异), terminal.
- [ ] P3.4 自动化族: automator / selector / UiObject 函数级差异, qrcode / barcode / ocr / yolo 差异, continuation / threads / timers 的排除决定落地.
- [ ] P3.5 矩阵清零: 前四批之外剩余 pending 的收口批次 (数量由矩阵给出), 每条 bridged 或有拍板过的排除理由.
- [ ] P3.6 阶段收尾: bump Node 插件 (minor) 与宿主构建号, Node changelog (zh-Hans), HOST-API 与 COMPATIBILITY_PROFILE 同步, 看板摘要. 不推送.

每批验收: 该批模块在矩阵的 node_main / node_worker 列无 pending; `check-runtime-<批名>.mjs` (新增或扩展) 接入 CI; 真机用例 (Node main + CJS / ESM / 嵌套 Worker) 通过 / 失败与关键数值写进 `docs/dev/runtime-<批名>-<日期>.md` (沿用 Codex 报告命名); 设备恢复并记录哈希.

### P4 Bun 补缺 [同 P3 的分批与验收, 列为 bun; 与 P3 并行时占用另一台主力设备 (D15); host-client 每个新命名空间配独立能力授权, 默认关闭, 沿用 `<module>.control` 命名 (附录 C Q3 待拍板)]

- [ ] P4.1 ~ P4.4 模块族同 P3.1 ~ P3.4 (Bun 侧另含 http / timers / util / jsox / threads / engines / plugins / workManager 的 not-portable 或 capability-gated 判定, 每条写等价写法).
- [ ] P4.5 矩阵清零 (bun 列).
- [ ] P4.6 阶段收尾: bump Bun 插件 (minor), changelog (zh-Hans), `docs/host-api.md` 同步, 看板摘要. 不推送.

### P5 官方插件自身的脚本 API [P5.1 静态, 不阻塞前面阶段; P5.2 起依赖维护者拍板]

- [ ] P5.1 盘点 [静态]. 对每个官方插件 (AutoJs6-Plugin-* 仓库, 以 AutoJs6-Official-Plugins-Index 为清单) 列出不经 augment 注册的脚本入口: `plugins.load(...)` 返回对象的成员, 插件自有全局或模块, 契约 AAR 暴露给脚本的类; 每个入口记条目数与文档 / d.ts / Ace / Node / Bun 现状. 产出 `docs/api-coverage/PLUGIN-APIS.md` 与一道选择题材料 (按插件分组, 每组条目数与建议). 验收: 全部官方插件有结论行 (含 "无自有脚本 API").
- [ ] P5.2 纳入分母 (按拍板结果): `extract_exports.py` 增加 pluginExports 来源 (读各插件仓库的注册或声明文件, 路径写进 `policy.json`), 矩阵与文档 / d.ts / Ace / Node / Bun 按 P2 ~ P4 相同规则补齐, 分批方式同 P3. 验收: 纳入的插件 API 行在矩阵无 pending.
- [ ] P5.3 阶段收尾: 涉及的插件仓库各 bump 一次 (D19), 看板摘要.

### P6 阶段末抽样与终态报告 [P6.1 / P6.2 真机, 可同时; P6.3 静态; 依赖 P2 ~ P5 (P5 若未纳入则不等)]

- [ ] P6.1 最低版本抽样 [真机: G8441 API 28]. 从 P3 / P4 每族各抽 2 ~ 3 个新增入口 (Node main / Worker / Bun), 以及 P1 抽样中的图像与 UI 族各 1 批. 验收: 通过 / 失败与关键数值入报告; 失败项按 API 级别写明是否为 API 28 限制 (限制项在矩阵加 minApi 标注而非 pending).
- [ ] P6.2 ColorOS 抽样 [真机: OnePlus OPD2413]. 重点是新增能力授权 / provider 绑定 / 前台服务在 ColorOS 的授权与唤醒 (参考记忆 plugin-auto-wake-coloros), 每个新增授权类入口至少 1 项. 验收同 P6.1.
- [ ] P6.3 终态报告 [静态]. 全量门禁 (含 Ace, 含 `--matrix`, `--docs-support`) 通过; 矩阵 pending 为 0; `docs/api-coverage/FINAL-<日期>.md` 给出: 分母, 各列状态分布, 排除项按理由分类计数, 各仓库版本, 与 2.2 节基线的对比. 验收: 调度会话据此向维护者汇报 "需求 1 与需求 2 的回答".

### P7 发布 [维护者逐仓库指示 (D6); 子会话只准备, 不推送]

- [ ] P7.1 发布清单: 每个仓库的目标版本 (P2.6 / P3.6 / P4.6 / P5.3 的 bump 结果), 领先远端的提交数, 前置条件 (宿主 6.8.0 公开; Node / Bun 对最低宿主构建号的声明; Ace 捆绑声明版本; TS 声明 npm 发布与否), 推送顺序建议 (宿主 -> TS 声明 -> Ace -> Node -> Bun -> APK Builder 模板 -> 官方插件), 每仓库的 prepush 检查命令 (见记忆 ci-prepush-check). 产出 `docs/api-coverage/RELEASE-PLAN.md`.
- [ ] P7.2 按维护者指示逐仓库推送与发布登记 (每个仓库一行: 日期, tag, 索引提交). 全部完成后本路线图可结束, 看板归档.

## 6. 并行, 设备与子会话约定

- 并行 (D15): P0 各条目并行; P1.1 可与 P0 并行 (等 P0.2 首版产物即可); P1.2 / P1.3 分别绑定 Redmi / Xiaomi 同时进行; P2 与 P3 / P4 并行; P3 与 P4 并行时各占一台主力设备, 真机子会话同时不超过 2 个; P5.1 任何时候可插入; P6 在 P2 ~ P5 之后; P7 最后.
- 设备 (D10): 使用 Redmi 前核对维护者 2026-10-22 "Redmi 暂时保留当前状态" 的指示是否解除 (附录 C Q4); 每个真机子会话开始时记录设备上宿主与各插件的安装包哈希, 结束时恢复并核对; 手机上的授权对话框由维护者操作 (规则 device-testing); 多步验证写成仓库内可复用脚本, 输出只保留通过 / 失败与关键数值.
- 宿主工作区: 与 augment / memory 等主题共用 checkout, 只提交自己的文件, 不推送, 不切分支, 不碰其他会话在改的文件; 宿主构建与其他会话排队, 串行并限制 CPU (Codex 批次曾把系统拖到 100%).
- 子会话汇报: 完成时把结论追加到看板 "子会话结果" 区 (勾选条目, 新待决, 提交 hash, 可否归档), 只增不改; 每完成一个条目就把进度写进本文件, 不靠上下文记住; 需要维护者拍板的事项按看板格式登记, 阻塞的用选择题问.

## 附录 A. Codex 批次证据报告 (`docs/dev/`, 2026-10-17 至 2026-10-22, 36 份)

runtime-api-coverage-2026-10-17 (总审计报告), runtime-opencc-images-2026-10-17, runtime-image-parity-2026-10-17, runtime-capture-worker-2026-10-17, runtime-node-recording-2026-10-17, runtime-video-worker-2026-10-17, runtime-audio-capture-2026-10-17, runtime-ui-compose-2026-10-18, runtime-ui-interop-2026-10-18, runtime-xml-views-2026-10-18, runtime-view-components-2026-10-18, runtime-webview-callbacks-2026-10-18, runtime-webview-files-2026-10-18, runtime-webview-media-2026-10-18, runtime-webview-streams-2026-10-18, runtime-webview-display-2026-10-18, bun-automation-2026-10-19, bun-storage-2026-10-19, bun-database-2026-10-19, bun-sensors-2026-10-19, shell-api-coverage-2026-10-19, runtime-shell-2026-10-19, runtime-events-2026-10-19, runtime-device-dialogs-2026-10-19, runtime-android-tasks-2026-10-19, runtime-java-interop-2026-10-20, runtime-java-callbacks-2026-10-20, runtime-tts-2026-10-20, runtime-speech-2026-10-20, runtime-epub-2026-10-21, runtime-mail-2026-10-21, runtime-flow-core-2026-10-21, runtime-flow-tools-2026-10-21, runtime-flow-scope-2026-10-21, runtime-ai-core-2026-10-22, runtime-ai-agent-2026-10-22.

Codex 最后一批 (ai.agent) 的验收绑定宿主 versionCode 5850 / 源码 28d7849d 与对应 Runtime Kit; 之后主工作区已合入其他会话的改动 (5935). P1 真机抽样用当前主工作区构建, 不复用 Codex 的 Runtime Kit.

## 附录 B. 工具与命令速查

- 宿主门禁: `python tools/api-coverage/check.py [--skip-ace] [--output <dir>]` (Ace 检查需环境变量 `AUTOJS6_ACE_EDITOR_ROOT` 指向 Ace 仓库与 Node.js); 单元测试 `python -m unittest discover -s tools/api-coverage -p 'test_*.py'`; 本会话 2026-10-23 全量运行耗时约 1.5 分钟.
- 按模块运行时检查: `node tools/api-coverage/check-runtime-<模块>.mjs` 等 (清单见 README 与 CI 工作流).
- Ace 静态索引与声明导入: 宿主 `tools/ace-completion/README.md` (`generate-editor-assets.mjs [--check]`, `import-autojs6-dts.mjs <TS 仓库路径>`, `generate-autojs6-indices-from-dts.mjs`, `verify-autojs6-api-model.mjs`).
- Ace 运行时查询: Ace 仓库 `node tools/ace-lsp/verify-runtime-api-coverage.mjs`.
- TS 声明仓库: `node tools/verify-api-coverage.mjs` (编译 index 与 smoke, 检查每个 aj6-int-*.d.ts 主体), `node tools/sync-runtime-declarations.mjs`, `node tools/verify-runtime-declarations.mjs`; 发布链路 `D:/idea-projects/android-dts-generator/aj6dts.bat -Publish` (同时改 Ace 插件, 版本需手动加一) 只涉及 Java 对象声明, 本主题的 aj6-int-*.d.ts 为手写.
- 文档: `AutoJs6-Documentation/generator/auto-generate-for-autojs6.bat` (同时改 AutoJs6-Plugin-Offline-Docs); 文案规范见该仓库 AGENTS.md (ASCII 标点, api/*.md 为唯一内容源).
- Node 插件: `docs/HOST-API.md` 状态表, `docs/nodejs/COMPATIBILITY_PROFILE.md`; Bun 插件: `app/src/main/assets/autojs6/host-client.d.mts` 第 3 行能力常量, `docs/host-api.md`.

## 附录 C. 待决事项 (调度会话用选择题请维护者拍板; 编号在本主题内唯一)

| 编号 | 问题 | 选项 (推荐项在前) | 影响 | 改选返工 | 状态 |
|---|---|---|---|---|---|
| Q1 | 排除白名单 (附录 D) 的最终内容 | 按 P1.4 整理的分组材料逐组拍板 | P1.4, P3, P4 的 pending 数 | 每改一组影响该组的补缺批次 | 待 P1.4 出材料 |
| Q2 | 官方插件自身脚本 API 纳入哪些插件 | 按 P5.1 盘点结果逐插件选 | P5.2 | 纳入越多分母越大 | 待 P5.1 出材料 |
| Q3 | Bun 新能力授权粒度 | (a) 每个新命名空间一个 `<module>.control`, 默认关闭 (推荐, Codex 惯例) / (b) 按功能域合并 (如 `system.read` 覆盖 settings / sysprops / autojs) | P4 全部批次 | 中 (能力常量与文档) | 需拍板 (P4 首批前) |
| Q4 | Redmi 是否可用于本主题 | (a) 可用, P1.2 前先记录并恢复 (推荐) / (b) 继续保留当前状态, P1.2 改用 XQ-AT72 (API 31) | P1.2, P3 / P4 真机 | 小 (换设备需重跑基线安装) | 需拍板 (P1.2 前) |
| Q5 | pending 项允许用 local-shim 满足吗 | (a) 允许, 须与 Rhino 行为一致且有测试, 矩阵标 local-shim (推荐) / (b) 只有 bridged 计为覆盖, local-shim 只用于纯工具函数 (cvt / fmt / species / s13n) | P3 / P4 | 中 | 需拍板 (P1.4 同轮) |
| Q6 | Node 新补缺模块的入口形式 | (a) `autojs6:<module>` 模块 + `$autojs.<module>` 显式入口, 不挂全局 (推荐, Codex 惯例与 Node ROADMAP 一致) / (b) 另加全局别名 | P3 | 小 | 需拍板 (P3 首批前) |
| Q7 | P0.5 文档表生成器落点 | (a) 文档仓库 generator/ (推荐, 与现有生成器同处) / (b) 宿主 tools/api-coverage 产出后复制 | P0.5, P2.5 | 小 | 可由 P0.5 子会话按推荐先行并登记 |

维护者待办 (操作类, 不出题): 从 dispatch 环境新建会话输入 `/dispatch api-coverage`; 在 augment 看板 / 调度会话登记 "P5.3 的文档 / d.ts 同步与桥接覆盖报告改由 api-coverage 接管, augment 只保留 script-modules.json 与 T10 改读" (D4); 各真机子会话期间按需在手机上确认授权对话框; P7 时逐仓库指示推送.

## 附录 D. 排除白名单草案 (D12; P1.4 核对后一次拍板; 状态: 草案)

格式: 条目或模式 | 适用列 | 理由分类 | 说明 / 等价写法.

来源 1: Node `docs/HOST-API.md` "八. 明确不可用 (denied)" 与状态表

- accessibility: `powerDialog`, `waitFor` (同步阻塞等待), `rawNode` (暴露原始 AccessibilityNodeInfo) | node, bun | security / sync-only / java-prototype | 异步等价: flow 的 waitFor* 与 UiObject 句柄.
- 硬件标识符 (device 的 IMEI / 序列号 / MAC 等) | node, bun | security | 运行时按 Android 限制返回 permission-denied 或 null.
- RootAutomator 与 Shizuku 的原始输入通道 (sendEvent / 直写输入设备) | node, bun | security | 高层 click / swipe / gesture 已桥接; Shizuku 命令执行已桥接.
- Node 门禁 BUILTIN_DISABLED 的 6 个可遮蔽 shim (见记忆 node-runtime-160-loader-host-tests) | node | runtime-builtin | 由运行时策略控制, 不属覆盖缺口.

来源 2: Codex 报告列出的边界

- 同步 Rhino 终端: `sleep` 类阻塞, `threads` (Rhino 线程), `continuation`, `timers` 的同步语义, `events` 的同步 loop | node, bun | sync-only / runtime-native | Node 用 await / worker_threads / 原生 timers, Bun 同理; 矩阵逐条写等价写法.
- 任意 Java 对象 / 原型行为 (`util.java` 之外的直接原型访问, `unwrapJavaObject` 等) | node, bun | java-prototype | Java 互操作批次提供句柄式访问, 不承诺原型等价.
- 完整的 Rhino XML 控件与表达式绑定面 (布局 XML 中的 `{{ }}` 绑定, 自定义控件类) | node, bun | ui-thread | 可移植契约只含 12 个常用控件 + 58 个属性, 其余按需登记.
- native 库与 `rhino.run`, JSOX 原型扩展 (Arrayx / Mathx / Numberx 对内置对象的扩展) | node, bun | rhino-only | Node 有 `jsox` 模块的显式函数形式; Bun 待定 (Q1 拍板).

来源 3: Bun ROADMAP "边界与非目标" 与运行时自带等价物

- http / fetch / WebSocket / timers / util / crypto / zip / base64 / nanoid / mime 中与运行时自带能力重复的部分 | node, bun | runtime-native | 矩阵逐函数判定: 宿主特有行为 (如 http 的宿主 cookie / 代理设置, zip 的宿主路径) 仍须 bridged 或 local-shim.
- engines / plugins / autojs 中操作 Rhino 引擎本身的函数 (`engines.execScript*`, `plugins.load` 的 Rhino 模块) | node, bun | rhino-only | 运行时自有生命周期 API; 只读查询 (版本, 运行中脚本列表) 不排除.
- 32 位 ABI, native addon, 子进程沙箱 | bun | 非本主题 | 不进矩阵.

## 附录 E. 关联路线图交叉引用

- augment `docs/augment/ROADMAP.md`: T10, D6, P5.3, 附录 C Q3 (本主题 D4 接管覆盖产出; 维护者待办登记).
- TS 声明 `docs/ROADMAP.md`: Phase 0 三项矩阵任务 (P0.2 标注取代), Phase 7 "WebStorm 补全" (P2.2 顺带核对一次并勾选或登记).
- Node 插件 ROADMAP "五. 明确不做的事", `docs/HOST-API.md` 八; Bun 插件 ROADMAP "边界与非目标" (附录 D 来源).
- 记忆: node-runtime-160-loader-host-tests (BUILTIN_DISABLED), plugin-auto-wake-coloros (P6.2), ci-prepush-check (P7.1), test-devices-plugin-install-authorized (设备上可自由装卸官方插件), subsessions-use-opus.
