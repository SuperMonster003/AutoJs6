# AutoJs6 布局分析 "生成代码" 模型辅助生成 Roadmap

本文是布局分析 "生成代码" 对话框 (`CodeGenerateDialog`) 改造为 "模型辅助候选构造 + 计划验证 + 综合排序" 的可执行状态表.
以 2026-10-03 的本地代码快照 (`master@2082edef14`, `VERSION_NAME=6.8.0`, `VERSION_BUILD=5315`) 为起点,
每个条目均可独立 Check 并落地, 后续会话按阶段逐步推进.

2026-10-07 目标校正: 维护者明确要求模型参与候选构造, 再推荐综合分最高的一个或多个方案. 原先 D7 的 "模型只排序" 收窄了目标, 按本次澄清修正. 已完成的 P0-P6 实现与验收记录保留, 其中构造策略、轨迹训练、模型能力协议及对应回归尚未完成; 历史 `[x]` 不代表这些新增职责已验收. 保持 P0-P8 编号, 先验证构造原型, 再沿原条目补做受影响部分并进入 P7. 具体方案与验收边界见 [模型参与候选构造设计](layout-code-generation-model-construction-design.md).

2026-10-07 补做进度 (离线原型, E1/E2): 已实现逐步构造器、独立 87 维状态/动作特征、轨迹导出、策略训练及 Kotlin 推理. 224 组受控任务导出 1,208,829 条观测动作, 两次导出与两次规范模型训练逐字节复现; 59 棵树的策略文件 84,454 B, 5,000 向量推理一致. 28 组受控留出首选质量与原搜索持平, 24 组合成瓶颈中有 3 组新增有效方案, 同时 3 组集合丢失覆盖; 18 次重放定位为有效前缀被族内 beam 剪枝. 构造机制已验证, 效果门槛未通过, 不作为 P7 接入或新能力发布的依据. 现有代码保持在测试/离线工具中, 后续继续补做构造、训练与终局评价. [首个构造原型报告](../../tools/codegen-model/reports/construction-first-2026.10-1.md).

使用方式:

1. 每次会话开始时, 从 "阶段总览" 选取一个或多个未完成条目. P0-P1 不依赖模型, 单独落地即有价值, 必须先做; 之后分两条可并行的线: 数据与模型 (P2-P4), 分发与管理 (P5-P6); 两线在 P7 (对话框集成) 与 P8 (评估与发布) 汇合.
2. 条目完成后勾选 `[x]`, 并在条目后追加证据 (提交 hash / 测试类名 / 设备型号 / 数据集 manifest), 证据等级见附录 C.
3. 涉及字符串资源的条目必须同步 11 个 `strings.xml` (`values` + 10 个语言目录, `values` 与 `values-en` 内容一致, 引号写作 `\"`); 涉及 feature / fix / improvement 的提交必须同步 `.changelog/lang_*.json` (10 个语言) 并重新生成 markdown.
4. 附录 A 的 "待决事项" 在进入对应阶段前由维护者拍板, 拍板结果回填到 "固定决策".
5. 生成的代码只使用现有脚本公开 API (D9), 因此不涉及文档 / d.ts / Ace 声明仓库同步; P8.6 的用户文档页为可选项. Shared-Assets 仓库的条目 (P5, P8.3) 在该仓库内提交; 签名与发布 dispatch 由维护者执行.
6. 一个子条目一个提交, 只暂存本功能的文件 (工作区可能有其他会话的未提交改动).

---

## 1. 固定决策与硬约束

D1-D6 来自 2026-10-03 的需求原文, 为硬性要求; D7 按维护者 2026-10-07 对模型参与构造的澄清修正. D8-D21 为实现约束, 本次同步调整相关职责与预算表述. 后续变更以维护者明确要求及记录为准, 不以旧设计限制已澄清的目标.

| 编号 | 决策 | 含义 |
| --- | --- | --- |
| D1 | 模型不进宿主 APK | 训练好的模型发布到 `AutoJs6-Shared-Assets` 仓库的新资产族 `codegen-models/` (独立目录, manifest, tag 前缀, 工作流, 签名密钥, 与 `ace-fonts/` 同构). 宿主只内置: 目录公钥, 引导目录 (bootstrap catalog, 数 KB), 推理解释器与管理代码. 宿主 universal APK 增量 <= 256 KiB, 以 `tools/size/apk_size_report.py` 记录. |
| D2 | 模型规模 | 目标 1-5 MiB 的专用模型包 (构造与排序组件合计, 待原型验证); 硬上限 100 MiB, 且单个发布文件必须 < 100 MiB (GitHub 单文件限制); 推理常驻内存 <= 32 MiB. |
| D3 | 对话框新增 "使用模型辅助生成" 复选框, 默认开启 | 位于 "生成代码" 对话框的选项区 (文案可调, 见 Q1; 是否跨会话记忆见 Q2). 关闭时完全走规则排序 (P1), 不触网, 不加载模型. |
| D4 | 按需下载, 显示进度, 可取消 | 只在用户点击 "生成" 且开关开启, 本机没有可用模型时提示下载: 显示模型名, 版本, 大小, 来源; 下载对话框显示进度条与 "已下载 / 总大小", 可取消. 取消或失败不阻塞本次生成: 提供 "使用规则生成". 下载完成后自动继续本次生成. |
| D5 | 多模型, 本地导入, 多版本, 可更新 | 官方目录可列出多个模型 (通常一个), 用户可下载其中任意个; 支持导入本地离线模型包 (自定义或官方均可); 每个模型 (`modelId`) 可安装多个版本, 可更新到新版本. 同一时刻只有一个 "当前使用" 的模型版本 (Q10). |
| D6 | 重复检测与版本对比 | 导入本地模型或下载在线模型时, 若已存在相同 `modelId` (任一版本) 或相同内容 (sha256), 先提示用户, 并以并排表格显示两个版本 (附录 E.5 的对比字段, 差异行高亮), 选项为 "替换 / 保留两者 / 取消" (内容完全相同时只提示 "已存在相同模型"). 更新到新版本时不弹确认提示, 但进度与完成界面同样并排显示新旧两个版本. |
| D7 | 模型参与构造, 所有推荐先验证 | 流水线为: 结构化请求 -> 规则/模型辅助构造 -> 统一验证、精简、去重 -> 综合排序 -> 渲染. 模型可在完整计划形成前选择锚点、条件、关系步骤与结束动作, 不自由输出 JavaScript, 不修改用户请求. 未通过当前捕获布局验证的候选不进入推荐; 模型开启与关闭时候选集合允许不同. 排序器自身仍只重排其已验证输入, 规则构造和排序保留为基线与回退. 现有仅排序实现不是构造能力的验收证据. 界面不展示未经校准的概率, 理由文字由程序依据计划结构生成. |
| D8 | 生成请求结构化 | 新增 `CodeGenRequest`, 对话框只负责组装请求: 目标集合, 范围 (单个 / 集合), 查询方式 (等待找到 / 限时等待 / 立即判断 3 种; 旧对话框的 4 个选项按附录 E.1 映射, "直到找到所有" 并入范围 "集合"), 动作 (无 / 点击 / 长按 / 设置文本 / 向前滚动 / 向后滚动) 与参数, 动作接收者策略 (自动 / 自身), 可用属性 (id / text / desc 三个开关), 代码风格 (经典 / Flow). 覆盖全部组合, 而非只覆盖 "唯一目标".  Q3 按 P0.1 与 E.1 落实: 限时默认 10,000 ms, 范围 100..600,000 ms; Flow 不限时等待显式使用 timeout: Infinity. |
| D9 | 定位计划 IR + 两个渲染器, 不改脚本 API | `LocatePlan` 描述 "如何从窗口根找到目标" (锚点选择器, 关系步骤, 序号, 基数, 动作接收者), 由 `ClassicRenderer` / `FlowRenderer` 渲染. 只输出现有公开 API (`untilFindOne` / `findOnce(i)` / `untilFind` / `exists` / `parent(n)` / `child(i)` / `findOne(selector)` / `*Each` / `waitAsync` / `waitThenClick` 等), 渲染表见附录 E.1. |
| D10 | 验证器按脚本语义执行计划 | 在捕获树上以脚本引擎同一套 `Selector` / 搜索算法执行计划 (DFS 先序, `findOnce(i)` 的序号, `untilFindOne` 取首个匹配), 单目标要求解析结果即为目标, 集合要求集合相等. 验证范围只有布局分析捕获的活动窗口 (F13), 在理由中注明. |
| D11 | 引擎泛型于 `UiNode<N>`, 与 Android 解耦 | 新引擎包 `core/accessibility/codegen/**` 中除设备侧适配外不引用 Android 类型; 设备上用既有 `InspectorNode` (`id()` 为 `fullId`), JVM 上用新增 `SnapshotNode` (直接包装 `NodeInfoSnapshot`, 不经 `NodeInfo`) 与既有 `FakeUiNode`. 旧 `codegeneration/**` 在 P1.8 切换后删除 (`com.stardust.**` 兼容包不动). |
| D12 | 专用构造策略与终局排序分职责训练 | 现有 v1 排序器为纯数值特征 CatBoost 对称树, 由 Kotlin 解释执行, 参数与历史验收见 P3/P4. 构造原型优先验证独立的状态/动作策略, 尝试复用数值树解释器而不新增通用 ML 运行时; 新策略的特征、权重与训练目标不能复用完整候选排序的定义. 动作分的跨状态可比性、搜索方式与最终模型结构须经对照实验确定, 不预先宣称当前排序包已支持构造. 小型神经结构解码器是备选研究方向, 仍须满足 D1/D2/D19. |
| D13 | 各角色的特征只有一份实现 | 完整候选特征由 Kotlin CandidateFeatures 计算; 新构造策略需独立的状态/动作特征实现及 schema/hash, 不给不适用的完整候选特征补零. PC 训练数据均经同一 Kotlin 实现导出, Python 负责训练与导出. 包/宿主按构造、排序角色与各自语法/特征版本校验兼容性; 当前 SUPPORTED_SCHEMA=1..1 仅为已实现的 v1 排序器支持范围, 新能力须显式演进. |
| D14 | 模型包格式自定 | 当前已实现的 v1 排序包扩展名 `.ajcgm` (zip): `manifest.json`, `model.bin` (对称树, 小端, 魔数与格式版本), `features.json`, `MODEL_CARD.md`, `LICENSE`; 字段见附录 E.2. 读取器做全量边界检查 (条目白名单, 解压大小上限, 特征序号, 叶数 = 2^d, 校验和), 畸形包只会被拒绝, 不会崩溃. 构造/排序组件的新能力需要显式版本化的格式与兼容校验 (原 P4/P5 内补做), 不把旧包解释为构造模型, 不覆盖已签发布文件. |
| D15 | 信任与来源 | 官方模型经 Shared-Assets 的签名目录分发 (ECDSA P-256 / SHA-256, 新密钥, 与 Ace 字体同一套校验: 公钥固定, 拒绝回滚 / 同版本异内容, 逐文件校验大小与 sha256). 本地导入的包若 sha256 与已验证官方目录中的某个版本一致, 标为 "官方", 否则标为 "自定义". 由于 D7, 自定义模型无需额外的安全警告, 只标注来源. |
| D16 | 复用 Ace 字体分发栈, 不搬动它 | 直接复用通用部分 (`EcdsaP256CatalogSignatureVerifier`, `FontDownloadConnection` / `OkHttpFontDownloadSource`, 大小与 sha256 校验); 字体专属的目录模型 / 仓库 / 存储在 `codegen/store/` 下按同一模式另写. 不重构 `ui/edit/editor/ace/font/**`. |
| D17 | 存储与选择 | 已安装模型位于 `noBackupFilesDir/codegen_models/<modelId>/<versionCode>-<sha8>/model.ajcgm`, 登记表 `registry.json`; 安装先写临时目录, 校验后原子改名. 当前模型存 Pref `key_codegen_model_current` (`modelId@versionCode-sha8`); 指向的版本不存在时视为 "未安装". |
| D18 | 网络与隐私 | 只在需要时联网: 点击生成且无可用模型, 打开模型管理, 手动检查更新. 目录缓存 24 h, 不后台下载, 不上传布局, 无遥测. 训练数据只来自维护者主动采集 (P2), 真实应用的原始快照不入公开仓库. |
| D19 | 性能预算 | 生成 (构造/搜索 + 验证 + 特征 + 排序 + 渲染) 在工作线程执行, 可取消; 5,000 节点树上 P95 <= 1 s (Redmi 22120RN86C, API 33), 超过 300 ms 显示 "正在生成"; 每次生成至多进行 2,000 次候选验证 (包括精简后的重验), 规则/模型分支合计保留至多 200 个通过验证的候选. 构造前缀数、动作评分数、深度与临时缓存也必须有界, 具体预算由原型实测确定. 模型、规则及失败回退共享本次总计数/时限, 不在回退时重置; 给规则分支及最终排序/渲染预留预算, 复用已有合格结果; 耗尽预算且无合格候选时明确失败. P95 目标不替代单次请求硬截止, 后者须在原型中确定. 模型首次加载 <= 200 ms, 之后常驻缓存直到切换模型. |
| D20 | 完整规则流程是基线与回退 | 开关关闭时完全使用规则构造 + RuleRanker. 构造策略缺失、不兼容、损坏、失败或超时, 按明确能力/原因保留规则分支; 排序失败则用 RuleRanker 重排当前合格候选, 不重新搜索. 结果区区分候选来源与排序来源, 旧排序包不得标为支持构造. 用户取消生成立即终止, 不自动回退; 仅取消下载时按 P7.2 等待用户决定是否继续规则生成. 新模型版本须通过 P8.1 的构造贡献、端到端效果与不劣于规则基线的验收后发布. |
| D21 | Roadmap 位于 `docs/dev/`; 训练工具位于宿主仓库 | 采集, 标注, 特征导出, 训练, 导出, 一致性校验工具位于 `tools/codegen-model/` (与 Kotlin 特征 / 推理在同一仓库, 一致性测试可同时运行); Shared-Assets 只接收构建好的模型包, 模型卡与数据集 manifest (Q5). |

---

## 2. 范围与非目标

范围内:

- `app/src/main/java/org/autojs/autojs/ui/floating/CodeGenerateDialog.java` (选项, 请求组装, 结果对话框) 与 `ui/floating/LayoutFloatyWindow.kt` 的入口
- 新增引擎包 `core/accessibility/codegen/**` (请求, 计划 IR, 候选搜索, 验证, 规则排序, 渲染, 特征), `codegen/model/**` (模型包与推理), `codegen/store/**` (目录, 下载, 导入, 存储, 重复检测, 更新)
- 新增界面 `ui/floating/codegen/**` (悬浮窗内的下载提示 / 进度 / 版本对比 / 结果对话框), `ui/settings/codegen/**` (设置中的模型管理页)
- 删除 `app/src/main/java/org/autojs/autojs/codegeneration/**` (P1.8) 及其测试 `FlowCodeStyleTest`
- `core/automator/filter/StringMatcher.kt` 的字符串字面量转义 (P0.3, 修复 `Selector.toString()`)
- 新增 `test-apps/codegen-fixture` (数据采集用的受控应用), `tools/codegen-model/**` (采集, 标注, 训练, 导出)
- `AutoJs6-Shared-Assets` 仓库的新资产族 `codegen-models/` 与 `tools/codegen-model-distribution/`, 工作流 `publish-codegen-models.yml`
- 资源: 11 个 `strings.xml`, `strings_donottranslate.xml` 偏好键, `res/raw/codegen_model_catalog_v1.json` 与 `codegen_model_catalog_public_key_v1.der`
- 单测 `app/src/test/java/org/autojs/autojs/core/accessibility/codegen/**`

非目标 (本 Roadmap 不处理):

- 脚本公开 API 的新增或变更; 生成结果只使用现有 API (D9).
- 端上训练, 联邦学习, 收集用户布局; 生成式模型 (LLM) 直接写代码.
- 跨窗口验证: 验证只覆盖布局分析捕获的活动窗口 (F13), 多窗口捕获另行立项.
- 用户框选的 "区域范围" 作为生成目标 (Q9).
- 布局分析搜索 / 筛选 (另见 `layout-inspector-search-roadmap.md`), 只复用其 `InspectorNode` 与会话.
- `com.stardust.**` 兼容包中的旧代码生成类.

---

## 3. 现状诊断 (2026-10-03)

行号以快照 `2082edef14` 为准. F1-F11 与 F18 是现有生成器的缺陷, 由 P0-P1 的新引擎按构造消除 (附录 B 第 1-6 项为其回归冒烟); F12-F17 是可复用的基础与约束.

| 编号 | 问题 / 事实 | 锚点 |
| --- | --- | --- |
| F1 | 对话框列出 "设置文本", 但 `setAction` 不处理它, 勾选后生成的代码没有动作; 即使接上, 生成器也只会写 `setText("")` | `CodeGenerateDialog.java:57, 125-139`, `CodeGenerator.kt:116` |
| F2 | "等待控件出现" 输出已弃用的 `waitFor()` (= `untilFind()`), "判断控件存在" 输出空的 `if(...exists()){\n  \n}`; 两者都忽略所选动作 | `CodeGenerator.kt:96-103` |
| F3 | 唯一性判定只看数量: 单目标要求 `searchIn(root, 2).size == 1`, "直到找到所有" 只要求非空; 从不检查命中的就是所选节点, 也不检查集合是否等于预期集合. `untilFind` 下第一个条件 (常为 `className`) 即停, 动作作用于整批节点 | `UiSelectorGenerator.kt:102-105` |
| F4 | 条件逐个累加且从不精简 (id -> className -> text -> desc -> 布尔 -> depth), 先加入的无效条件留在结果里; 没有序号 (`findOnce(i)`) 方案, 重复项只能失败 | `UiSelectorGenerator.kt:24-62` |
| F5 | 目标位于任何 `rowCount` / `columnCount > 0` 的祖先之下时, 一律先走集合形式 `X.children().forEach(child => { var target = child.findOne(item); target.click(); })`: 单目标意图被改成对每一项操作; 不含目标的项上 `findOne` 返回 null, 下一行抛异常 | `CodeGenerator.kt:33-38, 120-136, 147-155` |
| F6 | 经父节点的 `parent.child(i)` 路径不可达 (序号恒为 -1, 且 `index > 0` 跳过 0), 字符串还缺少 `.`; 经子节点细化的辅助生成器只继承 "使用 id", 忽略 text / desc 开关 | `CodeGenerator.kt:76-94`, `ReadOnlyUiObject.kt:25-35` |
| F7 | 选择器字面量不转义: 含 `"` / `\` / 换行的文本生成非法 JS; 同一函数也用于 `Selector.toString()`, 布局分析的 "复制为选择器" 同样受影响 | `StringMatcher.kt:81` |
| F8 | "直到找到一个" 输出已弃用且无限轮询的 `findOne()`; "未勾选任何查询方式" 静默回退为它 | `UiSelectorGenerator.kt:64-70`, `CodeGenerateDialog.java:141-155` |
| F9 | Flow 风格只在目标自身选择器唯一时生效, 且只支持 "直到找到一个" / "等待出现"; 其余组合静默回退经典风格, 界面不提示 | `CodeGenerator.kt:47-52`, `CodeStyle.kt:29-37` |
| F10 | `id()` 返回资源项名 (`title`), 而选择器 `id("title")` 匹配任意包的同名项; 跨包同名 id (如 `android:id/title`) 会被误判为唯一或不唯一 | `ReadOnlyUiObject.kt:47`, `InspectorNode.kt:48` (`fullId`) |
| F11 | 生成在主线程同步执行, 失败只显示 "生成失败", 无原因, 无备选, 无匹配预览 | `CodeGenerateDialog.java:90-123` |
| F12 | 入口: 节点菜单 -> `CodeGenerateDialog(mServiceContext, capture.root, mLayoutSelectedNode)`; 对话框基于 `InspectorDialogBuilder` (应用级主题, 悬浮窗类型), 已能在分析浮窗之上显示. 注意: 不在悬浮窗上开 `PopupWindow`, 不用应用 context 构建的 MaterialDialog `items()` (搜索 Roadmap 的设备结论) | `LayoutFloatyWindow.kt:191-195`, `InspectorDialogBuilder.kt:10` |
| F13 | 捕获范围只有活动窗口根 (含回退策略), 而脚本运行时搜索 `windowRoots()` (可能多个窗口); 捕获树上的验证不能证明脚本运行时唯一, 理由文字必须注明这一点 | `LayoutInspector.kt:47, 121-123`, `AccessibilityBridge.kt:153` |
| F14 | 可复用的脚本 API: `UiSelector.findOnce(i)` / `exists` / `untilFindOne` / `untilFind` / `findOne(timeout)`, `UiObject.parent(i)` / `child(i)` / `findOne(selector)`, `UiObjectCollection.clickEach` / `setTextEach` / `scrollForwardEach` 等; Flow 的 `waitAsync` / `waitThenClick` / `waitThenLongClick`, 默认超时 10 s | `UiSelector.kt:84, 113, 224, 242, 253`, `UiObject.kt:118, 123, 194`, `UiObjectCollection.kt:120-137`, `Flow.kt:136, 176, 184`, `FlowArguments.kt:53` |
| F15 | 可复用的树与快照: `InspectorNode` (设备侧 `UiNode`, `id()` 为 `fullId`), `UiSelector.searchIn` (泛型搜索), `NodeInfoSnapshot` (全部字段带默认值, Gson 可在 JVM 读) 与 `.layout.json` 信封, `FakeUiNode` 测试 DSL, JVM 测试可用 Rhino `Parser` | `UiSelector.kt:298`, `NodeInfoSnapshot.kt:15-53`, `LayoutSnapshotFiles.kt:20-23, 47, 68`, `app/src/test/.../FakeUiNode.kt:23` |
| F16 | 可复用的分发栈 (Ace 字体): 签名校验 `EcdsaP256CatalogSignatureVerifier`, 下载源 `OkHttpFontDownloadSource`, 事件 `Progress(bytesDownloaded, totalBytes)` / `Cancelled`, `FontDownloadSubscription.cancel()`, 文件校验, `noBackupFilesDir` 存储, 内置引导目录与公钥 (`res/raw/ace_font_catalog_*`), 及其 5 个 JVM 测试 | `ui/edit/editor/ace/font/*.kt`, `FontDownloader.kt:93-118`, `AceEditorFontManager.kt:104-109, 487-488` |
| F17 | Shared-Assets 仓库只有 `ace-fonts/` 一个资产族; README 要求新资产族各自的目录 / manifest / tag 前缀 / 工作流 / 完整性策略 / 签名密钥, 不用 Git LFS, Release 资产内容寻址且不可替换. 宿主没有任何 ML 依赖, `test-apps/` 只有插件一致性应用 | `AutoJs6-Shared-Assets/README.md:9-14, 22-28, 46-66`, `gradle/libs.versions.toml`, `settings.gradle.kts:140-158` |
| F18 | Flow 风格的长按输出全局 `waitThenLongClick(sel).sync()`, 但该函数只注册在 `flow` 对象上 (全局只有 `waitAsync` / `waitThenClick` / `waitForStable*` 等), 运行时抛 `ReferenceError`; `FlowCodeStyleTest` 断言的正是这一错误形式 | `CodeStyle.kt:34`, `Flow.kt:96-107` (`globalAssignmentFunctions`), `FlowCodeStyleTest.kt:17` |

---

## 4. 阶段总览

依赖关系: P0 -> P1 -> {P2 -> P3 -> P4} 与 {P5 -> P6} 两线并行 -> P7 -> P8. P1 完成即可单独发布 (规则排序, 不含模型); P6 只依赖 P4.2 的包读取器, 可先用 P4.4 的测试包联调.
以下路径省略前缀 `app/src/main/java/org/autojs/autojs/`, 引擎包 `core/accessibility/codegen/` 简写为 `codegen/`.

### P0 引擎基座 (JVM, 无界面)

- [x] P0.1 `CodeGenRequest` (`codegen/CodeGenRequest.kt`): 泛型于 `N : UiNode<N>`, 字段为目标集合 `targets: List<N>` (单个范围时 size = 1), `scope` (`SINGLE` / `COLLECTION`), `mode` (`WAIT` 不限时等待 / `WAIT_TIMEOUT(ms)` 限时等待 / `CHECK` 立即判断), `action` (`NONE` / `CLICK` / `LONG_CLICK` / `SET_TEXT(text)` / `SCROLL_FORWARD` / `SCROLL_BACKWARD`), `receiver` (`AUTO` / `SELF`), `attributes: Set<Attr>` (`ID` / `TEXT` / `DESC`), `style` (`CLASSIC` / `FLOW`). `validate()` 返回错误枚举列表而非抛异常: 目标为空, `SINGLE` 但目标多于 1 个, `WAIT_TIMEOUT` 超出 100..600,000 ms, `SET_TEXT` 的目标不可编辑且接收者为 `AUTO`. 附旧对话框 4 个查询选项到新请求的映射 `LegacyMode.toRequest(...)` (附录 E.1 末表). 单测: `CodeGenRequestTest` (每条校验规则 1 例, 映射 4 例). 证据 (E1, 2026-10-03): CodeGenRequestTest 12 项通过, 含超时两端边界, 错误累积, 四种旧模式与无选项映射; P0.5 记录联合编译与回归结果.
- [x] P0.2 `SnapshotNode` (`codegen/SnapshotNode.kt`): 直接包装 `NodeInfoSnapshot` 实现 `UiNode<SnapshotNode>` (D11), `id()` 返回 `fullId` (与 `InspectorNode` 一致, F10), 父指针在构造时建立, `performAction` 恒为 `false`; `SnapshotNode.load(file)` 读取 `.layout.json` 信封 (Gson, 与 `LayoutSnapshotFiles` 同一结构). 测试夹具 `app/src/test/resources/codegen/`: `login.layout.json` (无 id 的输入框与按钮), `list-duplicates.layout.json` (20 行列表, 每行文本 "删除" 重复, 行内 id 相同), `cross-package-id.layout.json` (两个包各有 `id/title`), 均为手写小树 (<= 200 节点); 5,000 节点的大树由测试代码生成, 不入库. 单测: `SnapshotNodeTest` (加载, 父子一致, `fullId`, 先序遍历顺序与 `InspectorNodes.preOrder` 相同). 证据 (E1, 2026-10-03): SnapshotNodeTest 6 项通过; 3 个夹具分别为 4 / 61 / 3 节点, 另验证 5,000 层树的迭代构造, 负索引, 父子对象身份与跨包完整 id.
- [x] P0.3 共享 JS 字符串字面量转义 (修复 F7): 把 `SelectorExport.quote` (`core/accessibility/inspector/SelectorExport.kt:76`, 已转义 `\\` `"` `\n` `\r` `\t`) 移到 `core/automator/filter/JsLiteral.kt` 的 `JsLiteral.quote(s)`, 补充 `\u2028` / `\u2029` 与其他控制字符 (`\u00XX`), `StringMatcher.kt:81` 与 `SelectorExport` 都改用它. 往返单测 `JsLiteralTest`: 含 `"` / `\` / 换行 / 制表 / `\u2028` / emoji 的文本, 生成 `text(...)` 后经 `SelectorCallParser.parse` 解析, 再在 `FakeUiNode` 树上匹配到原节点; 另以 Rhino `Parser` 解析无语法错误. 这是用户可见的修复 ("复制为选择器" 与脚本中 `Selector.toString()`): changelog fix 1 条 x 10 语言. 证据 (E1, 2026-10-03): JsLiteralTest 4 项通过, 含全部 C0/C1 控制字符, 两类 Unicode 行分隔符, emoji, 注入形态文本与正则反斜杠; Rhino 解析/求值及 inspector/filter 回归通过; 10 语言 JSON 与 22 个生成 Markdown 一致性校验通过.
- [x] P0.4 `LocatePlan` IR (`codegen/plan/LocatePlan.kt`): `SelectorSpec` (有序条件列表 `Condition(attr, value)`, 只含注册表已有的选择器名), `PlanStep` (`Index(i)` 即 `findOnce(i)`, 负数自末尾计; `Up(n)` 即 `parent(n)`; `Child(i)` 即 `child(i)`, 负数自末尾计; `Search(spec)` 即锚点子树内 `findOne(spec)`, 含锚点自身), `cardinality` (`ONE` / `ALL`), `receiver` (`Self` / `Up(n)`), `family` (`SELF` / `SELF_INDEX` / `ANCHOR_PATH` / `ANCHOR_SEARCH` / `CONTAINER_ITEMS`, 定义见附录 E.1). 提供稳定的 `planKey` (去重与日志用) 与 `SelectorText.render(spec)` (唯一的选择器文本出口, 使用 `JsLiteral.quote`). 单测 `LocatePlanTest`: `planKey` 稳定且区分步骤顺序, `SelectorText` 对每种条件名的输出与 `SelectorCallParser` 往返一致. 证据 (E1, 2026-10-03): LocatePlanTest 7 项通过; 五族形状与基数检查, 不可变条件/步骤, v1 长度前缀 planKey, 单字面量注册条件的解析往返与 Rhino 语法检查. IR 只接收 String/Boolean/Int, 排除 algorithm/currentApp 以保持节点谓词与既定搜索语义.
- [x] P0.5 编译 (`:app:compileAppDebugKotlin`) 与单测 (`:app:testAppDebugUnitTest --tests "org.autojs.autojs.core.accessibility.codegen.*"` 及 `JsLiteralTest`, `inspector.*` 回归) 通过; P0.3 单独一个提交 (含 changelog), 其余每个子条目一个提交. 除 P0.3 外无用户可见变化. 证据 (E1, 2026-10-03): 联合编译与 121 项 JVM 测试通过 (29 项新增, 0 失败/错误); 10 语言日志源与生成文件一致, git diff --check 通过. 详细记录如下.

#### P0 验收记录 (2026-10-03, E1)

- 编译与测试: Gradle wrapper 离线运行 :app:compileAppDebugKotlin 与 :app:testAppDebugUnitTest, 测试筛选为 org.autojs.autojs.core.accessibility.codegen.*, org.autojs.autojs.core.automator.filter.*, org.autojs.autojs.core.accessibility.inspector.*. 最终运行 BUILD SUCCESSFUL, 121 项通过.
- 新增测试: CodeGenRequestTest 12 项, SnapshotNodeTest 6 项, JsLiteralTest 4 项, LocatePlanTest 7 项. 覆盖请求边界与迁移, 3 个布局夹具, 5,000 层树, 字符串与正则字面量往返, 五族计划键与形状约束.
- 更新日志: v6.8.0 的 fix 各语言各增加 1 条, 日期保持 2026/10/03; 复用 .python/generate_markdown.py 的渲染函数, 只更新 10 个语言 JSON, 11 个 CHANGELOG 与 11 个 README 发行历史, 未触发在线元数据刷新.
- 提交: P0.1 = 707c259c00, P0.2 = ef23acb1b5, P0.3 = 3115e96c7d, P0.4 = cad0f89a62. P0.5 为本次验收记录提交.
- 阶段边界: 本阶段为 JVM 引擎基座; 对话框切换与设备冒烟按 P1 执行, 模型训练从 P2/P3 开始. P0 完成时的下一项为 P1.1 TargetSets.

### P1 规则流水线与对话框改造 (不含模型, 可单独发布)

- [x] P1.1 `TargetSets` (`codegen/TargetSets.kt`): 由所选节点推导 "集合" 范围的候选目标集合, 按顺序给出至多 3 个且去重: (a) 整个窗口中 `fullId` 与 `className` 都相同的节点; (b) 最近的 "重复容器" (子节点中至少 2 个与目标所在子树结构签名相同的祖先) 内, 与目标结构签名 (`className`, `fullId`, 相对容器项的路径) 相同的节点; (c) 整个窗口中 `text` 相同且非空的节点. 只返回 size >= 2 的集合, 每个集合带数量与一句程序生成的说明. 单测 `TargetSetsTest` (夹具 `list-duplicates`: (a)(b) 均为 20 项; 无重复时返回空). 证据 (E1, 2026-10-03): TargetSetsTest 9 项通过, 含三类建议与去重, 无 id/文本, 跨包 id, 最近重复容器, 新建节点包装及错误索引元数据, 5,000 层树. 描述保存为消息键与参数, 界面本地化在 P1.8 接入.
- [x] P1.2 `CandidateSearch` (`codegen/search/CandidateSearch.kt`): 按附录 E.1 的 5 个计划族确定性枚举候选, 便宜的在前:
  - `SELF`: 在允许的属性 (id / text / desc 由请求开关决定; `className`, `packageName` 始终可用; 布尔属性与 `depth` 作为结构条件, 带排序惩罚; 不用 bounds) 的子集上构造选择器, 再做贪心精简: 逐个删除条件, 删除后仍满足验证就保留删除 (修复 F4). id 默认用条目名, 条目名在窗口内跨包重名时改用完整 id (修复 F10).
  - `SELF_INDEX`: 选择器 + `findOnce(i)`, `i` 为目标在先序匹配中的位置, 末尾附近时另给负序号; 只用于单目标.
  - `ANCHOR_PATH`: 锚点为目标 6 级以内的祖先, 祖先的兄弟, 或近祖先的后代中自身选择器唯一的节点, 路径为至多 4 步的罗盘步骤 (向上, 子节点序号, 兄弟).
  - `ANCHOR_SEARCH`: 唯一定位的祖先锚点 + 子树内 `findOne(spec)`.
  - `CONTAINER_ITEMS`: 只用于集合, 唯一定位的容器 + `find(spec)`.

  预算按 D19: 至多尝试 2,000 个, 保留 200 个通过验证的; 可由 `CancellationSignal` 中途取消. 单测 `CandidateSearchTest`: 每个族至少 1 个夹具命中, 精简后不含冗余条件, 预算截断后仍确定 (同一输入两次结果相同), 跨包重名时输出完整 id. 证据 (E1, 2026-10-03): CandidateSearchTest 12 项通过, 覆盖五族, 正负序号, 精简/去重, 跨包完整 id, 属性开关, 确定性预算与取消. 候选族/锚点交替枚举; 验证尝试含精简且不超过 2,000, 保留不超过 200; 中间结果缓存限 200,000 个节点引用. AUTO 集合的最近接收者层数不一致时, 按现有统一 Up(n) IR 报告策略不匹配, 不擅自选更远祖先. 本条依赖 P1.3, 因此验证器先提交.
- [x] P1.3 `PlanVerifier` (`codegen/verify/PlanVerifier.kt`): 以脚本语义在捕获树上执行计划 (D10). 选择器匹配直接调用 `UiSelector.searchIn` (DFS 先序, 与 `findOnce(i)` / `untilFindOne` / `find` 一致), 罗盘步骤直接调用 `Compass.navigate`, `findOne(spec)` / `find(spec)` 包含接收者自身. 判定: 单目标要求解析结果 `=== target`; 集合要求解析集合与目标集合相等; 动作接收者 `Up(n)` 解析后须支持该动作 (可点击 / 可长按 / 可编辑 / 可滚动), `SELF` 不检查. 返回 `Verified(plan, resolved, facts)` 或 `Rejected(reason)`; `facts` 记录锚点匹配数, 序号, 步数, 文本是否像动态内容 (含数字 / 时间 / 计数) 等, 供排序与 P3 特征使用. 单测 `PlanVerifierTest`: 序号偏移 1 被拒, 集合多 1 项或少 1 项被拒, `findOne` 含自身, 接收者不可点击被拒, 与真实 `UiSelector` 在 `FakeUiNode` 树上的结果逐项一致. 证据 (E1, 2026-10-03): PlanVerifierTest 19 项通过, 含五族解析, 集合多选/漏选, 锚点自身, 首项语义, 接收者能力, 属性限制, 节点包装身份, 捕获根边界与取消. 内部 UiSelector.searchIn 增加可选生命周期参数, 验证器使用 NO_OP 以支持重复查询; 原默认行为与脚本 API 保持不变.
- [x] P1.4 `ClassicRenderer` (`codegen/render/ClassicRenderer.kt`): 按附录 E.1 渲染 3 种查询方式 x 2 种范围 x 6 种动作 x 5 个族. 关系步骤统一渲染为一次 `compass("...")` 调用 (返回 null 而不抛异常, 与 Flow 的 `compass` 选项同一字符串); 选择器文本只经 `SelectorText.render`. 不输出已弃用的 `findOne()` / `waitFor()` (修复 F2, F8); 设置文本带参数 (修复 F1); 立即判断不留空分支 (修复 F2); 单目标不改写成对每项操作 (修复 F5). 单测 `ClassicRendererTest`: 全组合快照 (附录 E.1 每行 1 例), 每段输出经 Rhino `Parser` 解析无语法错误, 不含 `waitFor(` 与无参 `findOne()`. 证据 (E1, 2026-10-03): ClassicRendererTest 10 项通过, 其中矩阵覆盖 216 种有效族/范围/查询/动作/接收者组合, 全部经 Rhino 解析并用 API 替身执行核对动作对象与参数; 覆盖渲染表快照, 空结果, 不可达路径, 空文本/转义及集合动作失败. 共用 RenderSupport 保持一次探测与判空语义.
- [x] P1.5 `FlowRenderer` (`codegen/render/FlowRenderer.kt`): 全部组合都有 Flow 形式 (修复 F9). `SELF` 族 (可带罗盘) 直接以选择器为条件: `waitThenClick(sel)`, `waitAsync(sel, opts).longClick()` 等; 其余族以函数为条件, 函数体即经典的 "单次解析" 表达式. 集合加 `resultType: 'widgets'`; 立即判断用 `flow.whenPresent(cond, w => ..., { timeout: 0 })`; 只用全局已注册的 `waitAsync` / `waitThenClick`, 其余经 `flow.` 调用 (修复 F18). 单测 `FlowRendererTest`: 同 P1.4 的全组合与 Rhino 解析; 另以白名单断言输出中的每个全局函数名都在 `Flow.globalAssignmentFunctions` 或 Automator 全局列表中. 证据 (E1, 2026-10-03): FlowRendererTest 8 项通过, 覆盖与经典风格相同的 216 种组合及 Rhino 执行. 全局调用名对照实际注册表; 函数条件的祖先接收者在回调中处理, 集合回调使用 *Each, 超时/立即判断用 whenPresent 跳过缺失目标, WAIT 显式 timeout: Infinity.
- [x] P1.6 `RuleRanker` 与理由文字 (`codegen/rank/RuleRanker.kt`, `codegen/rank/Explanation.kt`): `Ranker` 接口 `rank(request, verified): List<Ranked>`; `RuleRanker` 按固定代价排序 (族: `SELF` < `ANCHOR_SEARCH` < `ANCHOR_PATH` < `CONTAINER_ITEMS` < `SELF_INDEX`; 条件: 完整 id < 条目名 id < desc < text < className < 结构条件; 动态文本, 长路径, 大序号加罚), 同分时以 `planKey` 定序. `Explanation` 依据计划结构生成 1-3 行理由 (例: "以 id 唯一定位", "在 20 个相同项中取第 4 个, 列表顺序变化时会失效", "仅在当前活动窗口上验证"), 不含概率 (D7). 单测 `RuleRankerTest` (夹具上首选计划符合预期, 全序确定), `ExplanationTest` (每个族与每条加罚各 1 例). 证据 (E1, 2026-10-03): RuleRankerTest 7 项与 ExplanationTest 6 项通过. 固定代价覆盖族/属性/动态内容/路径/序号, 同分按 planKey; 额外区分首项匹配与唯一匹配. 理由为 2-3 行结构化消息键/参数, 恒含活动窗口验证范围, 不含模型概率; 界面翻译在 P1.8 接入.
- [x] P1.7 `CodeGenEngine` (`codegen/CodeGenEngine.kt`): 编排 `request -> search -> verify -> rank -> render`, 返回 `CodeGenResult` (首选 + 至多 9 个备选, 每项含代码, 理由, 解析命中数) 或 `CodeGenFailure` (原因枚举: 请求无效 / 无候选 / 全部未通过验证 / 已取消 / 超出预算, 附最接近的被拒原因). 由调用方提供线程; 记录各阶段耗时 (日志, 供 P8.2). 单测 `CodeGenEngineTest`: 3 个夹具的端到端输出快照, 取消后返回 "已取消", 失败时原因非空 (修复 F11 的 "无原因"). 证据 (E1, 2026-10-03): CodeGenEngineTest 8 项通过, 三个夹具的首选代码快照与备选确定性, 集合/Flow, 请求无效, 预算耗尽, 失败原因, 取消及阶段耗时均验证. 只渲染原始 Verified 集合, 最多 10 个结果. 联合 :app:compileAppDebugKotlin / :app:testAppDebugUnitTest (codegen.*, filter.*, inspector.*) 通过: 200 项, 本轮新增 79 项, 零失败/错误/跳过; 渲染器另在测试内覆盖 432 种组合. 当前为内部引擎, 下一项 P1.8 接入对话框; P1.10 的设备/体积验收尚未执行.

本轮 P1.1-P1.7 的本地提交: P1.1 = ba4cbd9621, P1.3 = b9c77c2386, P1.2 = 00436a9bd3, P1.4 = 3c43a915a2, P1.5 = 1e0aa872de, P1.6 = 9a2b599b47; P1.7 为本次引擎整合提交. 先提交 P1.3 是为满足 P1.2 的验证依赖, 未增加或拆分路线图条目.

- [x] P1.8 对话框选项改造与引擎切换: `CodeGenerateDialog.java` 改写为 Kotlin 并移到 `ui/floating/codegen/CodeGenerateDialog.kt` (删除原 Java 文件), 入口 `LayoutFloatyWindow.kt:192` 同步修改; 仍基于 `InspectorDialogBuilder` 与现有 `OptionGroup` 列表 (F12). 选项区:
  - 可用属性: id / text / desc 三个复选 (默认值沿用现状).
  - 范围: 单个 (默认) / 集合. 选 "集合" 时列出 `TargetSets` 给出的至多 3 个集合 (单选, 显示数量与说明); 没有集合时 "集合" 置灰并注明 "未找到相似控件".
  - 查询方式: 等待找到 (默认) / 限时等待 (超时输入框, 默认 10,000 ms, 见 Q3) / 立即判断.
  - 动作: 无 (默认) / 点击 / 长按 / 设置文本 (文本输入框) / 向前滚动 / 向后滚动, 改为单选 (旧版为多选且后者覆盖前者, "设置文本" 被忽略, F1).
  - 动作接收者: 自动 (默认, 目标自身不支持该动作时取最近支持的祖先) / 自身.
  - 代码风格: 经典 / Flow.

  选项状态抽成纯 Kotlin `CodeGenOptionsState` (`ui/floating/codegen/CodeGenOptionsState.kt`), `toRequest(root, target, targetSets)` 产出 `CodeGenRequest`, 请求无效时在对话框内显示具体错误 (不关闭对话框). 输入框所在的悬浮窗对话框必须可获焦点, 在 AVD API 24 与 HyperOS 设备上确认输入法能弹出. 生成在对话框持有的单线程执行器上运行, 带 `CancellationSignal`; 超过 300 ms 显示 "正在生成" 与 "取消" (D19). 本条目暂用现有的简单结果对话框显示首选代码与第一行理由, 失败时显示 `CodeGenFailure` 的原因文字 (F11). 同一提交删除 `codegeneration/**` 与 `FlowCodeStyleTest` (提交前 grep 确认 `com.stardust.**` 以外无引用). 单测 `CodeGenOptionsStateTest`: 每个选项到请求字段的映射, 默认值, 集合选择, 超时输入非法时的错误. 证据 (E3, 2026-10-03): CodeGenOptionsStateTest 15 项通过; 新界面已接入, 旧生成器及 FlowCodeStyleTest 已删除. 78 个共用字符串键同步 11 文件, 结构化理由/错误由 CodeGenText 本地化. API 24 AVD 与 Redmi 22120RN86C API 33 均通过选项映射, 非法超时保留输入, 实际悬浮窗 IME 显示/收回/再次显示及 320 dp 布局检查. 联合 JVM 测试 222 项通过; 设备工具随 P1.10 收尾提交.
- [x] P1.9 结果对话框 (`ui/floating/codegen/CodeGenResultDialog.kt`): 显示代码 (等宽, 可选中复制), 1-3 行理由, 解析命中数 (单个 "命中 1 个", 集合 "命中 20 个, 与所选集合一致"), 以及至多 9 个备选. 备选用对话框内 `RecyclerView` 列出 (每行: 族 + 主要条件的一行摘要), 点按切换上方显示的代码与理由; 不用 `PopupWindow`, 不用 MaterialDialog `items()` (F12). 按钮: 复制 / 在布局中显示 / 关闭. "在布局中显示" 隐藏 (不销毁) 结果对话框, 把当前计划的解析结果作为临时 `InspectorMatchView` (`ui/floating/layoutinspector/search/InspectorMatchView.kt:15`) 设到当前视图 (边界视图为 `LayoutBoundsView.kt:57` 的 `match`, 层次视图为其对应入口), 下一次点按视图时恢复原来的搜索匹配并重新显示结果对话框. 临时匹配以 `TreeMatch` (`core/accessibility/inspector/TreeMatch.kt`) 构造; 若没有 "由节点集合构造" 的工厂则新增 `TreeMatch.ofMatched(root, nodes)` (kept = 命中节点及其祖先). 单测 `TreeMatchOfMatchedTest` (`FakeUiNode` 树: 命中, 保留, 非命中, 空集合). 证据 (E3, 2026-10-03): TreeMatchOfMatchedTest 7 项通过; 结果对话框含等宽可选代码, 本地化理由/命中数, 至多九个备选与复制. 范围/层次窗口临时匹配在点按或返回时恢复原搜索会话; 关闭窗口使待恢复回调失效. API 24 与 Redmi API 33 的界面及预览设备检查均通过, 含备选切换/复制, 隐藏后同实例恢复, 隐藏时销毁不再弹出及两类视图恢复. HyperOS 的 Dialog.hide 后 isShowing 为 false, 测试以隐藏装饰视图和实例身份核对保留语义.
- [x] P1.10 发布收尾: changelog feature 1 条 (生成代码重写: 3 种查询方式, 集合范围, 动作接收者, 设置文本参数, 备选与理由, 布局中预览) 与 fix 1 条 (一句话合并 F1 / F2 / F5 / F8 / F9 / F10 / F18), 各 10 语言并重新生成 markdown; 字符串已在 P1.8 / P1.9 中随条目同步 11 个 `strings.xml`. 附录 B 第 1-6 项在 Redmi 22120RN86C (API 33) 与 AVD (API 24) 上执行并记录 (E3). 编译与单测 (`codegen.*`, `inspector.*`, `JsLiteralTest`) 通过; `tools/size/apk_size_report.py` 记录 P1 前后 universal APK 大小. P1 至此可单独发布. 证据 (E3, 2026-10-03): 下列编译, 界面, 实际代码执行与隔离 APK 体积检查均通过. feature/fix 各 1 条同步 10 语言与生成 Markdown; 未增加路线图条目.

  P1 验收记录 (2026-10-03):

  - 构建: :app:assembleAppDebug, :app:assembleAppDebugAndroidTest, :app:testAppDebugUnitTest 通过; JVM 筛选 codegen.*, filter.*, inspector.*, ui.floating.codegen.*, 合计 222 项, 零失败/错误/跳过. AppDebug 原生库 16 KiB 对齐检查通过.
  - 界面: CodeGenUiDeviceTest 5 项 + CodeGenPreviewDeviceTest 2 项在 AVD API 24 与 Redmi 22120RN86C API 33 均通过, 共 14 项. 检查真实悬浮窗输入法, 非法输入保留, 单选/集合/动作/风格映射, 备选切换/复制, 320 dp 布局, 隐藏预览与同实例恢复, 两类布局视图的搜索状态恢复及关闭清理.
  - 实际执行: CodeGenExecutionDeviceTest 5 项在 AVD API 24 通过. 另以 tools/layout-inspector/codegen-smoke.js 通过 RunIntentActivity 在两台设备各执行 27 段现场生成的代码, 共 54 段, 全部通过真实控件回执: 经典/Flow x 三种查询方式的点击/长按/转义文本, 20 个重复按钮中只点击第 4 个, 整组各点击一次, 正反滚动, 完整 id 区分 framework/app 同名 title. 无 id 的按钮与输入框均正常定位. 这采用复制值核对 + 宿主实际执行的自动化流程替代逐段手工粘贴; 全部片段与结果保存在应用外部文件目录 codegen-smoke/.
  - 工具与环境: 控件页 CodeGenSmokeActivity 仅在 debug 变体注册. UiAutomation 只使用 FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES. Redmi 的 instrumentation 重启宿主进程后曾使原服务落入系统 Crashed services/DEAD 连接状态, 所以脚本执行检查改为不重启进程的上述驱动; 同签名覆盖安装恢复了服务, 未重启手机. 驱动结束后服务仍绑定且 Crashed services 为空. HyperOS 隐藏对话框时 isShowing 为 false, 以装饰视图可见性和同实例恢复验证, 不据此误判已销毁.
  - APK: 同一隔离工作树以 2fd0809e57 为 P1 前基线, 只应用本路线图 P1 提交/日志/调试控件, 排除并行插件图标变更; 同为 AppDebug universal, 6.8.0 (5316), JDK 21, Gradle 9.7.1. 比较前重建 APK 输出, 避免增量 ZIP 保留旧资源空洞. tools/size/apk_size_report.py 的结果: 42,503,058 -> 42,668,174 字节, 增量 165,116 字节 (161.25 KiB), 小于 D1 的 256 KiB 上限; D1 在 P8.4 仍须对完整模型管理功能复验.
  - APK SHA-256: 前 acb9ca7ff9367917b1a4dea07b4a832b3ad56aaee49c155a5b0f26c950828f84; 后 d21eceffedfb701d689b042739c0d9a303011984548718a7175ec4f7a672ba9f.
  - 本地证据: build/codegen-smoke/api24-tests.log, redmi-ui-final.log, api24-blackbox.json, redmi-blackbox.json, p1-before.json, p1-after.json/md. 初轮 Redmi 失败记录保留在 redmi-tests.log, 未计为通过.
  - 提交: P1.8 = ff378073c3, P1.9 = beb3d7906d, P1.10 为本次收尾提交. 下一项为 P2.1 任务组格式.

### P2 训练数据 (受控应用 + 自动标注 + 真实应用少量标注)

模型要学的不是 "能否定位" (候选都已在捕获状态上验证, D7), 而是 "哪个计划在界面变化后仍然定位到同一目标". 因此训练样本以 "任务组" 为单位: 同一界面同一目标的多个状态 S0..Sk; 候选在 S0 上生成并验证, 标签 = 该计划在 S1..Sk 上仍解析到对应目标的比例. 工具代码统一放 `tools/codegen-model/` (D21), Python 用标准库 + `unittest`, 训练依赖在 P3 单独锁定.

- [x] P2.1 任务组格式 `*.cgtask.json` (`tools/codegen-model/FORMAT.md` 与 Kotlin 读取器 `codegen/dataset/TaskGroup.kt`): 字段 `format` (1), `groupId`, `source` (`fixture` / `real`), `app` (包名), `screenFamily` (划分数据集用, P2.6), `request` (范围, 可用属性, 动作所需能力; 与 `CodeGenRequest` 对应, 查询方式与风格不影响定位, 不入任务), `captureState` (默认 0), `states[]` (每项: `stateId`, `snapshot` (同目录下的 `.layout.json` 相对路径), `mutations` (施加的变化列表), `targets` (目标在该快照中的子节点序号路径, 如 `[0, 2, 1]`; 集合为路径列表)). 读取器校验路径存在, 各状态目标数一致, 单个范围恰好 1 个目标. 单测 `TaskGroupTest` (正常组, 路径越界, 目标数不一致, 未知 `format`). 证据 (E1, 2026-10-03): TaskGroupTest 8 项通过, 含默认值, 集合/单目标, 越界路径, 目标数量, 未知版本/字段, 重复键, 非整数与目录逃逸. 单状态仅供后续标注队列, 计算 survival 至少两状态; 任务读取复用 SnapshotNode, 不新增脚本 API.
- [x] P2.2 受控应用 `test-apps/codegen-fixture` (包名 `org.autojs.test.codegen.fixture`, 仅 View 体系, debug 签名, 不发布): `settings.gradle.kts` 新增列表 `codegenFixtureApps` 并加入 `include(...)` (不并入 `pluginConformanceTestApps`), `test-apps/README.md` 增加一段说明此类 "数据采集用应用". 界面族至少 8 个: 登录表单, 含重复项的列表 (RecyclerView), 设置页 (开关行), 网格, 选项卡, 对话框, 嵌套滚动, 无 id 界面. 启动参数 `am start -n <pkg>/.MainActivity --es screen <name> --el seed <n> --es mutations <a,b,...>` 决定界面与变化, 变化种类 (每种对应一类计划失效):
  - `shuffle` / `insert` / `delete`: 列表重排, 插入, 删除行 (使 `findOnce(i)` 与 `child(i)` 失效).
  - `retext`: 计数, 时间, 用户名等动态文本改变; `locale`: 中英文切换 (使 text / desc 条件失效).
  - `strip-ids` / `rename-ids`: 去掉或改名资源 id (模拟混淆或应用升级).
  - `wrap`: 在若干层级插入额外的包裹布局 (使 `parent(n)` 与长路径失效); `sibling`: 插入兄弟节点.

  真值: 每个参与任务的 View 带 `setTag(R.id.codegen_truth, key)`; 界面空闲后应用把 `{key, getGlobalVisibleRect, 层级深度}` 写到 `files/truth/<stateId>.json`. 动作回执: 被点击 / 长按 / 设置文本 / 滚动的 View 追加一行到 `files/receipts.jsonl` (`key`, 动作, 参数, 时间). 两者都经 `adb exec-out run-as <pkg> cat ...` 读取. 同一 `seed` + `mutations` 两次启动的真值文件逐字节相同 (确定性). 证据 (E3, 2026-10-03): debug APK 构建及 FixtureScenarioTest 5 项通过 (覆盖 8 族 x 48 seed x 全部变化及组合). Redmi 22120RN86C API 33 上每族独立进程重启两次, 8 组真值逐字节一致, 16 状态全部 6 个目标关联成功; 点击/长按/转义文本设置/双向滚动 5 项实际动作回执通过, 耗时 35.158 s. 可复用脚本位于模块 tools/smoke.py; 报告 build/codegen-model/fixture-smoke-redmi/report.json (不入库).
- [x] P2.3 采集驱动 `tools/codegen-model/capture/` (`collect.py` + 设备脚本 `capture.js`): `collect.py` 按计划表 (界面族 x seed x 变化组合) 逐个启动受控应用, 等待空闲, 经 `RunIntentActivity` 运行 `capture.js`, 拉回快照与真值. `capture.js` 只用 Java 互操作调用宿主现有代码 (`NodeInfo.Companion.capture` 取活动窗口根, `LayoutSnapshotFiles.INSTANCE.save`), 不新增脚本 API. 真值与快照的关联 (`capture/join.py`): 以 `getGlobalVisibleRect` == 快照 `bounds` 为主, 深度为辅; 未关联或多重关联的状态丢弃并计数. 一个界面先手工用布局分析捕获, 确认与 `capture.js` 的结果节点数和先序 `className` 序列相同 (E3, 记录设备). 单测 `test_join.py` (唯一关联, 裁剪导致的不一致, 重复 bounds). 证据 (E3, 2026-10-03): 19 项采集/关联测试通过, 含重复边界、裁剪、非法真值、超时计数与写入中断恢复. Redmi API 33 由悬浮菜单实际捕获列表页面, 与脚本结果均为 29 节点且先序 className 序列完全相同. 两轮各 24 状态试采均 0 丢弃; 正式采集计划含单目标和集合任务. 每次运行隔离设备请求/快照目录, 场景在输入目录外暂存并原子发布, 旧任务不会指向重采快照.
- [x] P2.4 自动标注 (JVM 工具任务 `CodeGenDatasetExportTask`, 位于 `app/src/test/java/.../codegen/dataset/`, 未设置环境变量 `CODEGEN_DATASET_DIR` 时 `Assume` 跳过, 因此日常单测不运行): 对每个任务组, 在捕获状态上运行 P1 的 `CandidateSearch` + `PlanVerifier`, 再把每个通过验证的计划在其他状态上用同一 `PlanVerifier` 执行 (目标按 `targets` 路径映射), 得到 `survival = 通过的状态数 / (k)`. 输出 `labels.jsonl` (每行: `groupId`, `planKey`, `family`, `survival`, 失败的状态与变化种类). 同时输出 RuleRanker 的首选, 供 P3.4 对比. 运行命令写入 `tools/codegen-model/README.md`. 单测: 在 2 个手写任务组上 `survival` 与预期一致 (含 `shuffle` 使 `SELF_INDEX` 得 0). 证据 (E2, 2026-10-03): CodeGenDatasetExporterTest 8 项通过, 含两手写任务的 survival、shuffle 序号失效、未来真值不改变捕获候选/规则首选、逐字节确定性、零候选记录及失败保留旧产物. Redmi 试采 56 组实际导出 2,786 候选, 56 组均有候选; 联合 codegen/filter/inspector/对话框 JVM 239 项全通过. 导出流式处理并缓存至多 16 个快照, 发布前复核输入校验和; 正式数据统计见 P2.7.

- [x] P2.5 真实应用标注 (`tools/codegen-model/label/label_real.py`, 命令行, 不联网): 维护者用布局分析对同一界面在不同时间 / 应用版本 / 语言下保存多个 `.layout.json` (现有 "保存布局" 功能, `LayoutFloatyWindow.kt:178`). 工具在第一个状态上以序号, 文本或 id 查询选出目标 (集合可多选), 在后续状态上按 `fullId` + `className` + 文本 + 相对位置自动推荐对应节点, 由维护者确认, 跳过或改选; 输出 `*.cgtask.json` (`source: real`). 真实快照与任务组只存在维护者本机目录 (环境变量 `CODEGEN_PRIVATE_DIR`), 不入任何公开仓库 (D18, Q6). 首轮规模见 Q8 (建议 >= 30 个应用, >= 150 个任务组). 单测 `test_label_real.py` (推荐算法: 重排后仍找到, 文本改变时按 id 找到, 无法确定时不自动确认). 证据 (E1, 2026-10-03): 标注工具及 14 项测试通过, 支持先序/文本/id 查询、集合多选、推荐后显式确认/跳过/改选; 无法确定、空输入或 EOF 均不自动确认. 真实数据与必要副本强制位于 CODEGEN_PRIVATE_DIR 且禁止 Git 工作树, 本轮未采集真实应用私有页面, 不将工具完成等同于 Q8 建议的 150 个真实任务已完成.
- [x] P2.6 数据集划分与 manifest (`tools/codegen-model/dataset/split.py`): 以 `screenFamily` (受控数据) 或 `app` (真实数据) 为单位做确定性哈希划分 train / valid / test = 70 / 15 / 15, 同一界面族的所有状态与 seed 只落在同一份 (避免泄漏); 真实数据另设独立的 `real-test` 份, 不参与训练 (Q6 可放宽). `dataset-manifest.json`: `datasetId` (如 `cg-ds-2026.10-1`), 生成时间, 生成器提交 hash, 受控应用 `versionCode`, 采集设备列表, 各份的任务组数 / 候选数 / 来源 / 界面族 / 变化种类统计, `labels.jsonl` 与 `features.csv` 的 sha256. 首个数据集目标: 受控数据 >= 2,000 个任务组, 覆盖全部 8 个界面族与全部变化种类. 单测 `test_split.py` (确定性, 无跨份界面族, 比例误差 <= 3%). 证据 (E2, 2026-10-03): 划分/manifest 17 项测试通过, 含整族隔离、100 族比例误差门槛、真实应用独立 real-test、UTF-16 输入摘要与陈旧标签拒绝. 首个 cg-ds-2026.10-1 含 2,688 组/133,704 候选, 覆盖 8 族与全部 9 类变化; train/valid/test 为 2,016/336/336 组. 必要取整说明: 8 个不可拆分且等量界面族按最大余数取 6/1/1, 实际 75/12.5/12.5%, 无法同时满足 70/15/15 的 3 个百分点误差; 保持整族隔离, 如实记录比例. features.csv 由 P3.2 产生, 当前 manifest 明确 features-pending/sha256=null, 工具已支持补入真实文件后校验和刷新, 不制造占位文件.
- [x] P2.7 阶段收尾: `:test-apps:codegen-fixture:assembleDebug` 通过; `python -m unittest discover tools/codegen-model` 通过; 在 Redmi 22120RN86C 上完成一次全量采集, 记录耗时, 丢弃率 (P2.3 未关联状态), 首个 manifest (E2); 若丢弃率 > 2% 先修关联再继续. 每个子条目一个提交; 采集产物不入库 (`tools/codegen-model/.gitignore` 忽略 `out/`). 证据 (E2/E3, 2026-10-03): Redmi 22120RN86C API 33 全量采集 1,152/1,152 状态成功, 0 丢弃 (0%), 1,283.743 s; 生成 2,688 任务组 (单目标 2,304, 集合 384), 全部 8 族 x 48 seed. 自动标注 133,704 候选, 无候选组 0; labels/rules/export-manifest 两次实际导出逐字节相同 (JVM 作业 24.816/25.468 s). 夹具编译/5 单测、联合 JVM 239 测试、Python 51 测试全通过, 最后补测 Windows junction 输出边界. 首个 manifest 位于 tools/codegen-model/out/cg-ds-2026.10-1/dataset-manifest.json (本机忽略目录), labels SHA-256 为 17d3798e3a680e0de4c26d5ddf1e85ee7bea00c0555761a86ab381f999bb0ad0. 768 个后续状态均有可观察变化, 真值 key 未出现在节点属性; 真实应用任务数为 0, features 待 P3.2. [汇总验收报告](../../tools/codegen-model/reports/p2-dataset-2026.10-1.md) 已入库, 原始快照/任务/标注未入库; 七小节各一个本地提交, 未推送.

### P3 特征与训练 (PC 侧)

- [x] P3.1 `CandidateFeatures` (`codegen/features/CandidateFeatures.kt`): 输入 `(request, Verified, 同组全部 Verified)`, 输出定长 `FloatArray`, 特征清单与编码见附录 E.3, 全部为有限数值 (NaN / 无穷视为缺陷, 测试断言). `FeatureSchema` 常量: `featureSchemaVersion = 1`, `names`, `namesHash` (名称以 `\n` 连接后 sha256 的前 16 位十六进制). 宿主声明 `SUPPORTED_SCHEMA = 1..1` (D13). 单测 `CandidateFeaturesTest`: 3 个夹具上的特征向量快照, 组内相对特征 (如 "规则代价名次") 与组成员顺序无关, `namesHash` 固定写在测试里 (改特征必须同时升 `featureSchemaVersion` 并更新该值). 证据 (E1, 2026-10-03): 64 列 schema 1, namesHash=9c6a5d8279ffca0e, SUPPORTED_SCHEMA=1..1; CandidateFeaturesTest 12 项通过, 含三个夹具的九份完整向量快照、成员/目标顺序不变、全部候选有限、捕获边界与 5,000 层树. 按组复用窗口统计/结构签名/规则名次, 集合聚合与缺失值约定详见 tools/codegen-model/FEATURES.md, 不读取未来状态或标签.
- [x] P3.2 特征导出: 扩展 P2.4 的 `CodeGenDatasetExportTask`, 同一次运行输出 `features.csv` (`split`, `groupId`, `planKey`, `label`, 特征列, 列名即 `FeatureSchema.names`) 与 `features.json` (schema 版本, 名称, 哈希; 即模型包中的同名文件). Python 不重算任何特征 (D13). 单测: 导出的列名与 `FeatureSchema` 一致, 同一输入两次导出逐字节相同. 证据 (E1, 2026-10-03): 导出器现同次原子发布 labels/rules/features.csv/features.json/export-manifest, 使用冻结 split-map 并校验整族隔离、真实数据仅 real-test. CodeGenDatasetExporterTest 11 项通过, 含五产物逐字节复现、64 列顺序、CSV 转义、未来真值不改变特征、失败保留旧产物及私有目录约束. manifest 绑定 schema/CSV/split 哈希, Python 仅验证与读取, 全量导出与训练证据见 P3.6.
- [x] P3.3 训练 (`tools/codegen-model/train/train.py`, `requirements.txt` 以 `==` 锁定 CatBoost 与 numpy 版本, Python 3.12): `CatBoostRanker`, 损失 YetiRank (备选 PairLogit, Q7), `group_id = groupId`, 标签为 `survival` 分级 (0 / 0.5 / 1 映射到 0..2), 深度 6, 迭代 <= 1024, 以 valid 早停, `random_seed` 固定, `thread_count=1` 以保证可复现. 只用数值特征 (D12). 输出 `out/<run>/model.cbm`, `model.json` (`save_model(format="json")`, 供 P4.1 转换), `train-log.json` (参数, 数据集 `datasetId`, 耗时, 树数, 深度). 复现要求: 同一数据集与参数两次训练 `model.json` 相同 (记录在 README; 若 CatBoost 在该平台不可复现, 记录差异并以 P4.4 一致性为准). 证据 (E2, 2026-10-03): Python 3.12.5 + catboost==1.2.10/numpy==2.2.6, YetiRank CPU 单线程、seed=20261003、深度 6、最多 1,024 轮, valid NDCG@1 早停. 21 项训练/数据校验测试通过 (含实际两次拟合). 全量两次训练分别 14.284951/15.420205 s, 均保留 6 棵树、bestIteration=5, CBM 21,536 B. 原始 JSON 仅 model_guid/train_finish_time 不同; 保留 model.raw.json 审计, model.json 只移除这两项元数据且数值完全不变, 两次规范文件逐字节一致 (SHA-256 cf70fe937ff4f087af7eb063ab3dbbbd691cb9e9b0c0bcc7befaa0da2a2df045). train-log 同时记录采集与特征导出提交、全部输入哈希及机器信息.
- [x] P3.4 评估与失败归因 (`tools/codegen-model/train/evaluate.py`): 在 test 与 real-test 上计算附录 E.4 的指标, 与 P2.4 输出的 RuleRanker 首选逐组对比, 分族 / 变化种类 / 界面族 / 来源统计. 输出 `report.md` (人读) 与 `metrics.json` (P8.1 门槛读取). 失败归因: 列出模型首选 `survival` 低于规则首选的前 50 个任务组 (组 id, 两边的 `planKey`, 失败的变化种类), 供下一轮补数据. 单测 `test_evaluate.py` (手写 3 组, 指标与手算一致). 证据 (E2, 2026-10-03): 14 项评估测试通过, 含三组手算六指标、空集/零候选覆盖率、近似同分确定性、CBM/JSON 实际预测及陈旧任务/快照拒绝. 首模型独立 test 336 组: top1Survival=0.8764880952, ruleTop1Survival=0.3288690476, regret=0.0491071429, perfectRate=0.7529761905, worseThanRule=0, top3Recall=0.9017857143; 按族/变化/界面/来源输出完整分项, 模型差于规则的组数 0. real-test=0 且指标为 null, 不推断真实应用表现或发布资格. 近似同分以分数降序的批次最大值为锚、批内规则顺序定序, 后续 P4 沿用同一约定.
- [x] P3.5 主动学习队列 (`tools/codegen-model/train/queue.py`): 对真实数据中未标注或只有单状态的界面, 按 "模型首选与规则首选不同" 且 "两者分差小" 排序, 输出 `queue.jsonl`, 作为下一轮 P2.5 采集的优先列表. 单测: 排序确定, 已标注的组不入队. 证据 (E1, 2026-10-03): Python 队列 10 测试及 JVM 候选导出 3 测试通过. 复用 Kotlin 搜索/验证/特征, 由显式 CodeGenQueueExportTask 为私有单状态真实任务导出候选与校验和; capture 目标须已由 P2.5 确认, 未确认未来状态不猜真值. 至少两确认状态或已有标签的组排除, 模型/规则首选不同且原始分差 <=0.1 (可配置) 时按分差/任务 id 排队. 每条绑定当前 task/snapshot 哈希, 输出限制在 CODEGEN_PRIVATE_DIR; 合成私有任务联调见 P3.6, 不计作真实应用采集.
- [x] P3.6 阶段收尾: `:app:testAppDebugUnitTest --tests "*.codegen.*"` 与 `python -m unittest discover tools/codegen-model` 通过; 首个模型的 `report.md` 附在提交说明或 `tools/codegen-model/reports/` (只含聚合指标, 不含真实应用快照内容); 记录训练机器与耗时. 证据 (E2, 2026-10-03): 联合 JVM 256 个常规测试通过 (2 个显式离线作业默认跳过, 随后全量特征导出/私有候选导出均实际运行通过); Python 3.12 启用 CatBoost 集成的 96 测试全部通过. 133,704 行 x 64 特征的五个导出产物两次逐字节一致, P2 labels/rules 哈希保持不变; 首模型两次训练数值与规范 JSON 相同, 耗时/机器详见 [首模型汇总报告](../../tools/codegen-model/reports/p3-first-model-2026.10-1.md) 及其聚合 metrics.json. Windows 11 / i5-12400 / 单线程拟合 14.285/15.420 s, 保留 6 棵深度 6 树; CBM 21,536 B, JSON 46,518 B. 12,960 测试候选的 CBM/JSON 桌面预测差 <=1.12e-16, 336 组首选相同 (不等同于 P4.4 端上一致性). 40 个受控快照构造的合成私有单状态任务联调得到 5 条队列, 不计入真实应用数据; real-test 仍为 0. 原始模型/数据在 out/ 忽略目录, 仅聚合报告入库; P3 六小节各本地提交, 未推送. 下一阶段 P4.

### P4 模型包与端上推理

- [x] P4.1 打包工具 (`tools/codegen-model/package/build_package.py`): 读 CatBoost `model.json` (`oblivious_trees[].splits` / `leaf_values`, `features_info.float_features`, `scale_and_bias`), 写 `model.bin`: 魔数 `AJCG`, 格式版本 `u16 = 1`, 特征数 `u16`, 树数 `u32`, 偏置 `f64`, 缩放 `f64`, 之后逐棵树: 深度 `u8`, d 个 (`u16` 特征序号, `f32` 阈值), 2^d 个 `f32` 叶值; 小端, 末尾 `u32` CRC32. 判定语义与 CatBoost 一致 (`value > border` 记为 1, 第 i 个分裂对应叶序号第 i 位, 以 P4.4 一致性测试为准). 拒绝打包: 存在类别特征, 树深 > 8, 树数 > 4,096, 特征数与 `features.json` 不一致. 再生成 `manifest.json` (附录 E.2), 复制 `features.json`, `MODEL_CARD.md` (模板含数据集 `datasetId`, 指标摘要, 已知局限), `LICENSE`, 打成 `.ajcgm` (zip, 条目顺序与时间戳固定, 可复现). 单测 `test_build_package.py`: 手写 2 棵树的 `model.json` 打包后逐字节与夹具一致, 类别特征被拒. 证据 (E2, 2026-10-03): 13 项打包测试通过, 含独立 76 B 两树 hex golden、CRC/叶位序、内部到 flat 特征映射、Float32 临界值/溢出、负 scale/bias、元数据来源绑定. 同输入本地候选包与 tiny 包各构建两次均逐字节一致; ZIP_STORED 配合固定顺序/时间/权限, 避免压缩库差异. 首模型 model.bin=1,790 B, 本地评估包=6,922 B; 五条目与边界详见 tools/codegen-model/package/FORMAT.md. 正式分发许可需维护者明确, 本轮候选使用 LicenseRef-LocalEvaluation, tiny 测试包沿宿主 MPL-2.0. 后续 P5 联调修正 (E1, 2026-10-03): d58c4e9bc3 限定 Python 打包时间为宿主可读的日历/偏移格式, 拒绝周日期与超过 9 位小数秒; 15 项打包测试通过.
- [x] P4.2 包读取器 (`codegen/model/ModelPackageReader.kt`, D14): 输入 `InputStream` 或文件, 输出 `ModelPackage` (manifest, features, 解析后的模型) 或 `Rejected(reason, detail)`. 校验:
  - zip: 条目白名单 (5 个固定名, 不允许目录, 重复名, `..`, 绝对路径), 单条目解压 <= 100 MiB, `model.bin` 与 `manifest.json` 必须存在, 总解压大小 <= 100 MiB (D2), 压缩比 > 200 视为炸弹.
  - `manifest.json`: `format == 1`, 必填字段齐全, `modelId` 匹配 `^[a-z0-9][a-z0-9._-]{1,63}$`, `versionCode` > 0, `model.sha256` 与实际一致, `featureSchemaVersion` 在 `SUPPORTED_SCHEMA` 内 (否则 `Rejected(INCOMPATIBLE_SCHEMA)`, 管理页显示 "不兼容", D13), `featureNamesHash` 等于宿主 `FeatureSchema.namesHash`.
  - `model.bin`: 魔数, 格式版本, CRC32, 特征序号 < 特征数, 深度 1..8, 叶数 = 2^d, 阈值与叶值有限, 文件长度与头部声明完全一致.

  解析后的模型以紧凑数组存放 (`IntArray` 特征序号, `FloatArray` 阈值与叶值), 4,096 棵深度 8 的上限对应约 4.2 MiB, 满足 D2 的 32 MiB 常驻. 单测 `ModelPackageReaderTest`: 每条拒绝规则 1 例 (包在测试中用代码生成); 随机变异测试 (对合法包逐字节翻转 / 截断 / 追加, 固定种子 2,000 次) 只会得到 `ModelPackage` 或 `Rejected`, 从不抛出其他异常. 证据 (E1, 2026-10-03): ModelPackageReaderTest 20 项通过, 含 2,000 次固定种子变异, 声明长度造假、实际流式计数、中央/本地头、重复/路径/截断/附加数据、ZIP CRC、严格 UTF-8/JSON、schema/宿主版本与全部二进制字段校验. 单个二进制按格式理论最大 4,395,040 B 进一步收紧, 文本/schema 另设有界上限; 包经有界磁盘副本校验, card/license 流式读取. InputStream 由调用者持有, 内部临时文件与句柄均清理; Android 调用传实际 hostVersionCode 与 context.cacheDir, 纯 JVM 默认临时目录不代替 Android 缓存目录.
- [x] P4.3 推理与 `ModelRanker` (`codegen/model/ObliviousTreeModel.kt`, `codegen/rank/ModelRanker.kt`): `score(features: FloatArray): Double` 无分配, 逐树计算叶序号后累加, 最后 `bias + scale * sum`. `ModelRanker(model, ruleRanker, budgetMs)` 实现 `Ranker`: 先算全部特征 (P3.1), 再打分, 按分数降序, 分差 < 1e-6 时以 `RuleRanker` 的顺序定序 (结果全序确定). 超出 `budgetMs` (默认 150 ms) 或抛异常时抛出 `RankerUnavailable(reason)`, 由 P7.4 回退. 模型只改变顺序: `ModelRanker` 的输出集合恒等于输入集合 (D7, 测试断言). 单测 `ObliviousTreeModelTest` (手算 2 棵树), `ModelRankerTest` (集合不变, 同分定序, 超时与异常转为 `RankerUnavailable`). 证据 (E1, 2026-10-03): 6 项解释器与 10 项模型排序测试通过, 覆盖两树手算/阈值相等/叶位序/缩放偏置、不可变数组/并发只读、原始 Verified 对象与集合保留、近似同分链、各阶段超时及异常/取消. score 正常路径仅标量与循环; 共用 ModelScoreOrder 与 P3 的分批同分语义一致. 预算覆盖特征/推理/排序, 不在此处提前接入 P7 回退. 读取器依赖该模型数组类型, 因而本条提交先于 P4.2, 未拆分或增加路线图条目.
- [x] P4.4 一致性与测试包: `tools/codegen-model/package/export_parity.py` 用 CatBoost `predict` 对 test 份的前 5,000 个候选输出 `parity.jsonl` (特征 + 期望分数); Kotlin `ModelParityTest` 读取包与 parity 文件 (环境变量 `CODEGEN_PARITY_DIR`, 未设置时跳过): 每个分数 `|差| <= 1e-4`, 每组首选相同. 另提交一个只用受控夹具训练的小测试包 `app/src/test/resources/codegen/models/tiny-test-v1.ajcgm` (< 64 KiB, `modelId = autojs6.codegen.tiny-test`) 及其 `parity.jsonl`, 使一致性测试在日常单测中也运行一次; 该包同时供 P6 联调 (P6 可先于 P3 完成). 证据 (E2, 2026-10-03): Python parity 6 项测试与 Kotlin 两项实际一致性测试通过. 取前 5,000 候选并补齐末组 12 项, 共 5,012 候选/130 完整组, 避免截断任务组后的首选伪验收; Kotlin 最大分数差 9.110787901889239e-9, 130 组首选全部一致. tiny 包直接使用 P3 受控数据训练的六棵树, 23,633 B (<64 KiB), 附 152 候选/4 完整组常规 parity 与来源哈希, 同样全通过; .gitattributes 固定二进制/LF 以保持跨检出校验和. 全量对照为 JVM 中的同一 Kotlin 实现, 手机性能仍在 P8.2 验收.
- [x] P4.5 预算基准 (`ModelBenchmarkTest`, 环境变量 `CODEGEN_BENCH=1` 才运行): 1,024 棵深度 6 的随机模型, 加载耗时, 200 个候选的特征 + 打分耗时, 解析后的对象大小 (按数组长度计算); JVM 上记录数值, 设备数值在 P8.2 测得. 预期: 打分 200 个候选 <= 20 ms (设备), 加载 <= 200 ms (D19). 证据 (E2, 2026-10-03): Windows 11 / JDK 21.0.1 / i5-12400 的按需基准通过, 固定 seed=20261003, 5 次预热/30 次测量. 1,024 棵深度 6 树, 5,000 节点与 200 个实际 PlanVerifier 验证候选: 首次包加载 69.3239 ms; 热加载 P50/P95=8.3208/9.8535 ms; 特征=11.6705/13.9883 ms; 评分=5.5798/17.8159 ms; 特征+评分=17.4368/29.8281 ms. 模型数组数据 320,512 B (不含对象头/临时buffer/特征上下文), binary 300,064 B. 原始结果 build/codegen-benchmark/p4/model-benchmark.json; 这些是 PC JVM 数据, 未据此声明 Redmi 的 P8.2 性能门槛通过.
- [x] P4.6 阶段收尾: 编译与 `codegen.*` 单测通过 (含 `tiny-test` 一致性), `python -m unittest discover tools/codegen-model` 通过; 记录 `tiny-test` 包与首个正式候选模型的大小. 每个子条目一个提交. 证据 (E2, 2026-10-03): 联合 codegen/filter/inspector/对话框 JVM 295 测试通过, 0 失败/错误, 2 个 P2 离线作业按约定跳过; 此次实际执行了完整/内置 tiny parity 和性能基准. Python 3.12 (启用实际 CatBoost 集成) 115 测试全通过. 本地候选包 6,922 B、tiny 包 23,633 B、二进制树 1,790 B; 两种包各两次构建均逐字节一致. [P4 汇总报告](../../tools/codegen-model/reports/p4-model-runtime-2026.10-1.md) 与聚合 metrics.json 已入库, 完整候选包/全量parity留在 out/ 忽略目录; 六小节各本地提交, 无远端推送. 当前完成核心读取/排序, 尚未接入 P7 生成界面; P8 仍需真实应用数据与手机性能, P5 正式分发许可及离线签名/测试仓库由维护者确定.

### P5 Shared-Assets 资产族 `codegen-models/` (在 `AutoJs6-Shared-Assets` 仓库内提交)

布局与 `ace-fonts/` 同构 (F17): 已审核的包作为源输入直接入库 (不用 Git LFS), Release 资产内容寻址且不可替换, 目录离线签名, 私钥不入库.

- [ ] P5.1 目录结构与 README: `codegen-models/README.md` (用途, 信任策略, 版本与 tag 规则, 发布流程), `sources/<modelId>/<versionCode>/` (`.ajcgm`, `MODEL_CARD.md`, `dataset-manifest.json`), `catalogs/catalog-v1.json` + `catalog-v1.sig.json`, `catalogs/history/vN/`, `catalogs/pending/vN/`, `keys/catalog-signing-public.pem`, `schemas/` (`catalog-v1.schema.json`, `signature-v1.schema.json`, `model-manifest-v1.schema.json`). 根 README 的 "Components" 与 "Repository layout" 增加该族一行. tag 前缀 `codegen-models-vN` (N 为目录版本), 资产名 `autojs6-codegen-<modelId>-v<versionCode>-<sha8>.ajcgm`. 进展 (E1, 2026-10-03): Shared-Assets 本地提交 d706807 已建立独立目录、README、23,633 B 的 MPL-2.0 tiny-test 源包及去设备序列号的聚合数据清单; 空 models 的 pending/v1 已复现并由维护者签署用于 APK 离线引导. 正式公钥已由维护者提供并以 Shared-Assets 5907b8a 记录; 签名引导资源见已完成的 P5.5. 正式远端 current 目录/签名仍留待首个正式模型发布, 本项暂不勾选.
- [x] P5.2 目录 schema (`catalog-v1.schema.json`, 字段见附录 E.2 后半): `schemaVersion: 1`, `catalogVersion` (单调递增, 拒绝回滚), `releaseTag`, `generatedAt`, `models[]` (每项: `modelId`, `displayName`, 多语言 `summary`, `versions[]`, `recommendedVersionCode`). 每个版本: `versionCode`, `versionName`, `featureSchemaVersion`, `featureNamesHash`, `minHostVersionCode`, `artifact` (`fileName`, `size`, `sha256`, `url`, `urls[]`), `releasedAt`, `datasetId`, `metrics` (附录 E.4 的摘要), `license` (`spdx`), 可选 `deprecated` / `revoked` (撤回的版本客户端提示更新, 不再提供下载). 同一 `modelId` + `versionCode` 在历次目录中内容不得改变 (客户端检测到即拒绝, 同 Ace 的 "同版本异内容"). 证据 (E1, 2026-10-03): Shared-Assets 本地提交 51dc70b; 三份 schema 已落地. 目录另绑定 repository; 版本按顺序排列、历史 artifact URL 保持不变, deprecated/revoked 仅单向启用; 无可推荐版本时 recommendedVersionCode 为 null. 严格日历时间格式与宿主一致, 校验器保留纳秒顺序.
- [x] P5.3 分发工具 `tools/codegen-model-distribution/` (参照 `tools/ace-font-distribution/` 的结构, 复用其签名格式与 `crypto.py` 的算法, 新密钥): `generate_keys.py` (离线生成 P-256 密钥对, 输出 keyId), `prepare_catalog.py` (扫描 `sources/`, 读取每个包的 `manifest.json`, 校验与 P4.2 相同的规则的 Python 子集, 计算大小与 sha256, 合并历史版本, 写入 `catalogs/pending/vN/`), `validate_catalog.py` (schema + 不变量: 版本唯一, 历史条目不变, 单文件 < 100 MiB (D2), url 指向本次或更早的 tag), `sign_catalog.py`, `verify_catalog.py`, `verify_release.py` (对照目录逐个校验 Release 资产). 单测 `tests/test_distribution.py`: 回滚被拒, 同版本异内容被拒, 超大文件被拒, 签名往返, 篡改一个字节后验签失败. 证据 (E1, 2026-10-03): Shared-Assets 本地提交 56b4c7e. 63 项分发相关单测全通过, 包含真实 tiny ZIP 变异、100 MiB 边界、历史不变量、独立 P-256 密钥/签名与篡改拒绝; 11 项实际 CLI 联调及本地模拟回读通过. 生产模型要求 P8.1 的 passed gate 绑定完整包 SHA, 不伪造门槛结果; tiny 仅可在明确测试模式的非正式仓库收录. 详情见 tools/codegen-model/reports/p5-distribution-2026.10-1.md.
- [x] P5.4 工作流 `.github/workflows/publish-codegen-models.yml` (`workflow_dispatch`, 输入 `release_tag` 与期望 `key_id`, 环境 `codegen-models-release`, Python 3.12): 复现目录与资产 -> `verify_catalog.py` -> 拒绝已存在的 tag / Release (不可变) -> 创建 Release 并上传资产 -> `verify_release.py` 回读校验. 与 `publish-ace-fonts.yml` 的并发组, 权限范围一致. 先在 fork 或私有测试仓库用 `tiny-test` 包 (P4.4) 跑通一次, 记录 run 链接 (E2). 证据 (E2, 2026-10-04): 维护者在独立公开测试仓库 SuperMonster003/AutoJs6-Codegen-Distribution-Test 完成 [run 37138706446](https://github.com/SuperMonster003/AutoJs6-Codegen-Distribution-Test/actions/runs/37138706446), attempt 1 / success, CI 63 项测试全通过. 发布提交及 tag 均为 8a0ae0128913f40b7fca306c76efc7c6209ac477; [测试 Release codegen-models-v1](https://github.com/SuperMonster003/AutoJs6-Codegen-Distribution-Test/releases/tag/codegen-models-v1) 含模型包 23,633 B / 目录 2,094 B / 签名 222 B, CI 最后回读验签通过. 本机独立下载、大小/SHA/包元数据/签名复核及匿名 HTTPS 目录/模型访问全部通过; 已存在 Release 的只读预检明确拒绝重复发布. 测试 keyId=p256-fc44588d1cb789dc; 正式 key 保持独立. 详见 [P5.4 远端验收报告](../../tools/codegen-model/reports/p5-release-acceptance-2026.10-1.md).
- [x] P5.5 宿主侧内置文件: 生成 `res/raw/codegen_model_catalog_public_key_v1.der` (由 P5.3 的公钥转换) 与引导目录 `res/raw/codegen_model_catalog_v1.json` (首次为空 `models: []` 的已签名目录, 或首个正式发布后的目录; 只用于离线时显示 "已知的官方模型" 与官方来源判定, D15). 引导目录与签名一起内置, 宿主同样验签. 本条目的宿主提交只含这两个文件与生成命令说明. 证据 (E1, 2026-10-03): 维护者正式 keyId=p256-f1a540e052cf0865, SPKI SHA-256=f1a540e052cf086520619e429cd3e3240fa3f8a4b8fca46e3531358906a67de3; 公钥记录于 Shared-Assets 本地提交 5907b8a. 维护者签署的空 bootstrap 原始目录 SHA-256=caceae92e1274a45c94cff35f6480e6c43db5bef5b6d6e29924ded6bc0191a7e. OpenSSL 与宿主实际 Kotlin 验签器均验证通过, Kotlin 同时拒绝字节篡改和测试 key ID. 两个正式资源已经安装 (公钥 91 B / 引导目录 557 B), :app:processAppDebugResources 通过; 原始目录字节及签名完整保存在 bootstrap envelope 中; 未发布空的远端 v1. 生产/debug 配置装配仍归 P6.1.

P5.5 本次生成命令 (在 Shared-Assets 根目录执行; `build/codegen-maintainer-verified/catalog-v1.sig.json` 是维护者提供的公开签名内容的可读副本, 不含私钥):

```powershell
py -3.12 tools/codegen-model-distribution/prepare_bootstrap.py --catalog codegen-models/catalogs/pending/v1/catalog-v1.json --signature build/codegen-maintainer-verified/catalog-v1.sig.json --public-key codegen-models/keys/catalog-signing-public.pem --expected-key-id p256-f1a540e052cf0865 --expected-repository SuperMonster003/AutoJs6-Shared-Assets --out-dir build/codegen-maintainer-verified/host-bootstrap --host-root D:/idea-projects/AutoJs6
```

- [ ] P5.6 阶段收尾: `python -m unittest discover tools/codegen-model-distribution` 通过; Shared-Assets 中每个子条目一个提交; 签名私钥由维护者离线保管, 签名与 dispatch 由维护者执行 (使用方式第 5 条). 进展 (E2, 2026-10-04): Shared-Assets 分发单测 63 项通过, 测试仓库 Linux CI 63 项及真实 Release 回读通过; 既有 Ace 回归 43 项通过、3 项原有宿主集成测试按套件条件跳过. P5.1-P5.5 已按逻辑本地提交, 维护者完成正式/测试签名和 P5.4 dispatch, 私钥未进入仓库. 此次要求维护者协助的材料与操作已经齐备; 宿主与正式 Shared-Assets 仍仅本地提交. P5.1 正式 current 目录/签名保留至 P8.3 首个正式模型发布, 因而完整阶段收尾暂不勾选, 不再等待重复签名或重跑同一 Release.

### P6 宿主模型管理 (目录, 下载, 导入, 版本)

存储与对比逻辑写成不依赖 Android 的纯 Kotlin (文件系统路径与时钟注入), 界面只做展示; 全部单测在 JVM 上运行.

- [x] P6.1 目录仓库 (`codegen/store/ModelCatalog.kt`, `ModelCatalogParser.kt`, `ModelCatalogRepository.kt`): 结构对照 `FontCatalogRepository` 另写 (D16). 地址 `https://raw.githubusercontent.com/SuperMonster003/AutoJs6-Shared-Assets/main/codegen-models/catalogs/catalog-v1.json` 与同目录 `.sig.json` (镜像见 Q4). 验签复用 `EcdsaP256CatalogSignatureVerifier` (新公钥, `expectedKeyId`, `expectedCatalogFileName = "catalog-v1.json"`). 规则:
  - 拒绝回滚 (`catalogVersion` 小于已缓存值) 与同版本异内容 (同一 `modelId` + `versionCode` 的 sha256 与此前见过的不同).
  - 验签或解析失败时保留旧缓存. 缓存 24 h (D18), 位于 `noBackupFilesDir/codegen_models/catalog/`.
  - 离线时用缓存, 再退到引导目录 (P5.5).
  - `minHostVersionCode` 高于当前宿主的版本标为 "需要更新 AutoJs6", schema 不兼容的版本标为 "不兼容", 两者都不提供下载.

  debug 构建可经 `src/debug/` 覆盖目录地址与公钥, 指向 P5.4 的测试仓库; release 构建只认正式公钥. 单测 `ModelCatalogParserTest` (必填字段, 未知字段忽略, 非法 `modelId`), `ModelCatalogRepositoryTest` (假 fetcher: 回滚被拒, 同版本异内容被拒, 坏签名保留缓存, 24 h 内不联网, 离线用引导目录). 证据 (E1/E2, 2026-10-04): 在既有目录仓库基础上完成 ModelCatalogConfiguration 与 CodegenCatalogFactory; 正式 namespace/keyId/完整 SPKI 固定校验, release 变体与工厂双重要求正式配置. debug 默认正式源, 显式源码开关可选择已发布测试源; 目录缓存、模型根目录及当前选择 Pref 均隔离. 实际正式/测试签名资源验证通过, release 资源符号表仅含正式 key/bootstrap. 配置测试 7 项、debug 变体 2 项、release 变体 1 项通过; debug/release Kotlin 编译通过. UI 入口仍按后续条目接入.
- [x] P6.2 存储与登记表 (`codegen/store/ModelStore.kt`, D17): 布局 `noBackupFilesDir/codegen_models/<modelId>/<versionCode>-<sha8>/model.ajcgm` + `registry.json`. 登记项: `modelId`, `versionCode`, `versionName`, `sha256`, `size`, `featureSchemaVersion`, `source` (`OFFICIAL` / `CUSTOM`), `origin` (目录版本或导入文件名), `installedAt`, manifest 摘要 (附录 E.5 所需字段). 安装流程:
  1. 写入 `staging/` 临时文件.
  2. `ModelPackageReader` (P4.2) 校验.
  3. 判定来源: sha256 出现在任一已验证目录 (当前, 缓存, 引导) 中为 `OFFICIAL`, 否则为 `CUSTOM` (D15).
  4. 原子改名到目标目录.
  5. 在文件锁内以 "临时文件 + 改名" 更新登记表.

  启动时对账: 删除没有目录的登记项, 删除不在登记表中的目录与残留的 `staging/`. 当前模型 Pref `key_codegen_model_current` (值 `modelId@versionCode-sha8`, 键名入 `strings_donottranslate.xml`). 删除当前版本后 Pref 置空 (视为 "未安装", 下次生成按 D4 提示). 单测 `ModelStoreTest`: 安装, 坏包被拒且无残留, 改名前中断后对账无登记项, 来源判定, 删除当前版本, 同一 `modelId` 的多版本并存. 证据 (E1/E2, 2026-10-04): ModelStore 与 Android 路径/Pref 适配完成, 实际包读取器验证私有安装快照后再原子安装/登记. 多版本和同 versionCode 不同内容可并存; 完整 SHA 判同一内容, 实际 SHA8 碰撞回归确认拒绝覆盖. 当前选择以登记表日志协调 Pref 持久化, 包括 SharedPreferences 内存已变但落盘失败的恢复. 对账保留 catalog/debug 缓存, 坏 registry 停止清理, 活跃 staging 租约跨实例保护且避免 POSIX 第二 FD 关闭问题. ModelStoreTest 32 项中 30 通过、2 项 Windows 符号链接权限限制跳过; Pref 适配 2 项通过. 真实测试包经下载/安装/选中/删除流程通过; 手机验收仍归 P6.8.
- [x] P6.3 下载器 (`codegen/store/ModelDownloader.kt`): 连接层复用 `FontDownloadSource` / `OkHttpFontDownloadSource` (D16), 事件 `ModelDownloadEvent` (`Queued` / `Progress(bytesDownloaded, totalBytes)` / `Success(installed)` / `Failure(error)` / `Cancelled`, 与 `FontDownloadEvent` 同形), `ModelDownloadSubscription.cancel()`. 流程:
  - 按 `urls` 顺序尝试镜像, 边下载边计算 sha256, 写入 `staging/`.
  - 超过目录声明的 `size` 立即中止. 结束后大小与 sha256 必须一致, 再交给 `ModelStore` 安装.
  - 取消或失败时删除临时文件.
  - 同一版本同时只有一个下载, 重复请求返回同一订阅.

  进度事件至多每 100 ms 或每 1% 发送一次. 单测 `ModelDownloaderTest` (假源: 进度单调, 中途取消无残留, 超长中止, sha256 不符, 首个镜像失败后用第二个, 重复请求共享订阅). 证据 (E1/E2, 2026-10-04): ModelDownloader 复用 HTTP 连接层, 完成大小/完整 SHA 校验, 镜像重试、单调节流进度、共享订阅与取消清理. 网络访问前与持有目录锁的安装提交前都检查已验证目录, 撤回或元数据变化阻止安装; 实际包 manifest 逐字段匹配目录, 数值零按 Python/目录语义归一化. 14 项假源测试通过, 含并发共享、废弃状态更新、撤回、坏包摘要、伪造 URL、观察者异常和重入取消. ModelDownloadIntegrationTest 用真实公开 HTTPS/测试公钥/包读取器完成 23,633 B tiny 包安装、来源判定、选中与删除. Android 共用服务共享下载并在主线程投递事件; 此次无设备验收或发布.

- [x] P6.4 重复检测与版本对比 (`codegen/store/DuplicateCheck.kt`, `codegen/store/VersionComparison.kt`, 纯 Kotlin, D6): `DuplicateCheck.evaluate(incoming, registry)` 返回:
  - `None`: 无同 `modelId` 且无同 sha256, 直接安装.
  - `Identical(existing)`: 已安装相同 sha256 (不论 `modelId` 是否相同), 只提示 "已存在相同模型", 不安装.
  - `SameModel(existing: List, compareWith)`: 已有同 `modelId` 的其他版本 (含同 `versionCode` 不同内容). `compareWith` 取同 `versionCode` 的那一项, 否则取当前使用的版本, 否则取 `versionCode` 最大的版本.

  `VersionComparison.build(old, new)` 输出附录 E.5 的行列表 `Row(field, oldText, newText, differs)`. 下载在线模型时, 检测在下载之前进行 (目录已给出 sha256 与 manifest 摘要), 不会先下载再提示. 选项与后果: 替换 = 安装新版本并删除 `compareWith`, 若被删的是当前版本则当前切到新版本; 保留两者 = 安装新版本, 当前不变 (目录名含 sha8, 同 `versionCode` 也可并存); 取消 = 不安装, 删除临时文件. 单测 `DuplicateCheckTest` (每种结果与 `compareWith` 选择规则各 1 例), `VersionComparisonTest` (差异标记, 缺失字段显示为 "-"). 证据 (E1, 2026-10-04): DuplicateCheckTest 12 项、VersionComparisonTest 13 项全部通过. 完整 SHA 优先, 同版本/当前/最高版本选择与多候选稳定次序均覆盖; E.5 固定 12 行由注入格式器本地化, 原始值判定差异, 不受摘要缩写、大小/百分比舍入影响. 目录、已安装包及导入 manifest 共享对比 DTO; 目录缺少的数据集创建日期按约定显示 "-", 不扩展目录协议. 替换/保留两者的持久行为已由 P6.2 验证, 对比视图和对话框留在 P6.5.
- [x] P6.5 对比视图与对话框 (`ui/floating/codegen/ModelComparisonView.kt`): 一个不依赖对话框类型的 View (两列表格: 字段名 / 已安装 / 新的, 差异行用主题强调色背景与 "≠" 标记, 不只靠颜色区分). 同一 View 用于设置页 (应用 context 的普通对话框) 与布局分析悬浮窗 (`InspectorDialogBuilder`, P7.2). 对话框 `ModelDuplicateDialog`: 标题 "已存在同名模型", 内容为对比视图, 按钮 替换 / 保留两者 / 取消; `Identical` 时只有一行说明与 "知道了". 界面文字 11 个 `strings.xml`. 证据 (E3, 2026-10-07): E.5 固定 12 行共享对比视图与 Identical/SameModel 对话框完成, 37 项文案同步 11 个 strings.xml. ModelDialogsDeviceTest 在 API 24 AVD 与 Redmi API 33 各 7 项通过, 覆盖普通/悬浮窗口、三个决定与 Back、完整单元格滚动可见、字体 2.0 与阿拉伯 RTL. 修正 HorizontalScrollView 的无界子测量, 由表格精确约束列宽以保证换行; P6.8 汇总设备记录.
- [x] P6.6 更新 (`codegen/store/ModelUpdates.kt` + 界面): 已安装的官方模型在已验证目录中存在更高 `versionCode` (且兼容, 未撤回) 时标记 "可更新". 更新不弹确认 (D6): 直接开始下载, 进度对话框上方为进度条与 "已下载 / 总大小" 及 "取消", 下方为新旧版本的对比视图 (P6.5); 完成后同一对话框显示 "已更新到 vX", 对比视图保留, 按钮 "删除旧版本" / "完成". 更新完成后若旧版本是当前版本, 当前切到新版本; 旧版本默认保留 (Q11). 自定义模型不参与在线更新 (只能再次导入). 检查更新只在打开管理页与点击 "检查更新" 时联网 (D18); 生成时若缓存目录显示有新版本, 只在结果对话框注明 "有新版本可用", 不自动下载. 单测 `ModelUpdatesTest` (更高版本, 不兼容版本, 撤回版本, 自定义模型不提示, 更新后当前版本切换). 证据 (E1/E3, 2026-10-07): ModelUpdatesTest 11 项、ModelUpdateCompletionTest 3 项与 ModelTransferStateTest 5 项通过. 更新以存储锁内 compare-and-set 切换当前版本, 保留下载期间的用户选择与旧包; 完成后的显式删除不清掉新版本选择. 进度/完成/失败复用同一比较组件, 取消只回调一次; 7 项文案同步 11 个 strings.xml. 两台设备通过公开测试目录 v1 -> v2 实际下载, 核对全 SHA 与当前/旧版本后果. 生成结果中的更新提示按既有 P7.4 接入.
- [x] P6.7 模型管理页 (`ui/settings/codegen/CodeGenModelsActivity.kt` + 入口 `CodeGenModelsPreference`, 沿用 `TrashAndVersionHistorySettingsActivity` / `TrashAndVersionHistorySettingsPreference` 的 Activity + 入口 Preference 模式; 入口位置见 Q12, 建议放在 `fragment_preferences.xml` 的无障碍服务分类 (`text_a11y_service`) 末尾). 内容:
  - "已安装" 分组: 按 `modelId` 分组, 每个版本一行: 名称, 版本, 来源 (官方 / 自定义), 大小, 状态 (可用 / 不兼容 / 需要更新 AutoJs6), 单选 "当前使用". 行菜单: 设为当前, 查看模型卡 (`MODEL_CARD.md` 以纯文本显示), 删除 (删除当前版本时提示生成将改用规则排序).
  - "可下载" 分组: 已验证目录中未安装的模型 / 版本, 显示大小与来源 "官方", 按钮 "下载" (先过 P6.4 的检测, 再显示与 P7.2 相同形式的进度).
  - 顶栏操作: 检查更新, 导入本地模型 (`ActivityResultContracts.OpenDocument`, 同 `PluginFragment.kt:44`; 经 `ContentResolver` 复制到 `staging/`, 超过 100 MiB 中止, 之后走 P6.4 与 P6.2).
  - 目录获取失败时显示原因与重试, 已安装模型照常可用.

  页面逻辑抽成 `CodeGenModelsViewModel` (纯 Kotlin 状态: 分组, 行状态, 操作可用性), 单测 `CodeGenModelsStateTest`. 界面文字 11 个 `strings.xml`. 证据 (E1/E3, 2026-10-07): 管理 Activity 与无障碍服务分类入口完成; CodeGenModelsStateTest 15 项、CodegenModelOperationsTest 8 项通过, 含来源判定、相同内容、100 MiB 截止、取消清理、篡改 staging/模型卡拒绝. 35 项文案同步 11 个 strings.xml, 10 语言 changelog 与 22 个生成 Markdown 同步. 两设备通过 4 项实际管理流程, 覆盖单字节坏包、自定义多版本、实际 GitHub 升级、提交跨 Activity 重建; API 24 系统文件选择器导入及两设备飞机模式/重试/已安装当前模型保留另行验收. 提供程序 CancellationSignal 与后台中断、持久提交后的共享变更通知、取消下载终止清理等待已接入.
- [x] P6.8 阶段收尾: 编译与 `codegen.*` 单测通过; 在 AVD (API 24) 与 Redmi 22120RN86C (API 33) 上用 `tiny-test` 包与 P5.4 测试仓库目录执行附录 B 第 7-10 项 (导入, 重复, 更新, 管理页操作与离线), 记录 (E3). 每个子条目一个提交. 证据 (E1/E2/E3, 2026-10-07): 默认正式源 APK 与最终 JVM 回归通过, 351 项中 345 通过、6 项既有条件跳过 (4 项 opt-in 导出/外部评估、2 项 Windows 符号链接权限). API 24 与 Redmi API 33 各通过 4 项 CodeGenModelsDeviceTest 和 7 项 ModelDialogsDeviceTest; 最终对比测试以普通/悬浮窗口检查完整文字可达、字体 2.0、RTL 与表宽上限. API 24 SAF 真实导入、两台飞机模式检查更新失败/重试/已安装当前模型保留通过. v2 由维护者签名并 dispatch, 公开工作流 37570326238 成功 (63 项分发测试), v1/v2 六项发布文件回读 hash 一致. 本轮实现提交 P6.5=41c6c4ec53、P6.6=3bfe1fdb61、P6.7=234ad66f16; 详细记录见 tools/codegen-model/reports/p6-management-2026.10-1.md. 调试源开关、设备安装包与网络设置均恢复, 测试模型清理, 宿主仅本地提交.

### P7 对话框集成 (模型开关, 按需下载, 回退)

本阶段按校正后的 D7 接入构造与排序. 前置工作为构造原型对照和原 P2-P6 受影响条目的补做, 详见 [构造设计第 5-6 节](layout-code-generation-model-construction-design.md#5-保留的基础与原条目内的补做范围); 不以先完成仅排序接入替代新的目标.

- [ ] P7.1 开关: P1.8 的选项区末尾新增复选框 "使用模型辅助生成" (默认勾选, 文案见 Q1, 是否记忆见 Q2), 下方一行小字显示当前状态: "当前模型: X vN" / "未安装, 生成时可下载" / "当前模型不兼容". `CodeGenOptionsState` 增加 `useModel` 字段. 关闭时: 不读目录, 不加载模型, 不联网, 使用完整规则构造与 `RuleRanker` (D3). 单测: `CodeGenOptionsStateTest` 补充默认值与记忆行为.
- [ ] P7.2 生成流程状态机 (`ui/floating/codegen/CodeGenFlow.kt`, 纯 Kotlin, 界面与网络以接口注入): 点击 "生成" 后:
  1. `useModel` 关闭: 规则构造与排序, 直接生成.
  2. 当前模型可用 (Pref 指向已安装且兼容的版本): 经 P7.3 加载, 按已验证能力参与候选构造和终局排序. 构造候选与规则候选统一验证/精简/去重; 旧包仅支持排序时明确显示能力, 不声称执行了模型构造.
  3. 当前模型不可用, 但已安装兼容版本: 自动选用目录推荐的 `modelId` 中 `versionCode` 最大的已安装版本 (没有则选最近安装的), 写入 Pref, 结果中注明 "已切换到 X vN".
  4. 未安装任何兼容版本: 读取目录 (24 h 缓存, 否则联网, 超时 8 s). 目录可用时显示下载提示: 模型名, 版本, 大小, 来源 "官方 (AutoJs6-Shared-Assets)", 按钮 下载 / 使用规则生成 / 取消 (D4). 目录不可用时提示原因, 按钮 重试 / 使用规则生成 / 取消.
  5. 选择下载: 先过 `DuplicateCheck` (P6.4; 例如本机只有不兼容的旧版本时, 显示对比视图与 替换 / 保留两者 / 取消), 再显示进度对话框 (进度条, "已下载 / 总大小", 取消). 完成后关闭进度对话框, 自动继续本次生成 (同一请求, 不需要再点一次).
  6. 下载取消或失败: 进度对话框变为说明 (原因) + 按钮 使用规则生成 / 重试 / 关闭, 不阻塞本次生成 (D4).

  "取消生成" 在任一步骤都结束请求且不留后台任务 (下载订阅一并取消); "取消下载" 仅停止传输, 保留请求并按第 6 步等待用户选择, 不自动回退. 所有对话框基于 `InspectorDialogBuilder`, 在布局分析悬浮窗之上显示; 不用 `PopupWindow`, 不用 `items()` (F12). 单测 `CodeGenFlowTest` (假目录 / 下载器 / 存储): 每条分支的步骤序列, 下载完成后自动继续, 取消无残留订阅, 目录不可用时的回退.
- [ ] P7.3 模型加载与缓存 (`codegen/model/ModelCache.kt`): 进程内只缓存一个已加载模型, 键为 `modelId@versionCode-sha8`; 首次使用在生成线程上加载 (不在主线程), 切换或删除当前模型时释放. 加载失败 (包损坏, 不兼容) 时把该版本标记为 "不可用" 直到下次启动, 本次按 D20 回退完整规则构造与排序 (复用已有规则候选, 遵守剩余预算). 单测 `ModelCacheTest` (命中, 切换释放, 失败标记). 缓存需覆盖已声明的构造/排序组件, 按各角色版本检查兼容与失败原因; 旧 v1 ranker 不拥有隐含的构造能力.
- [ ] P7.4 回退与说明 (`CodeGenEngine` 接入构造策略与 `Ranker` 选择): 引擎合并规则/模型构造来源的已验证候选; 构造策略失败时保留规则分支, 总预算不重置. `ModelRanker` 抛出 `RankerUnavailable` 时用 `RuleRanker` 重排当前合格集合 (不重新搜索). 结果区分别说明候选构造来源与以下排序来源:
  - "排序: 模型 X vN" (正常).
  - "排序: 规则 (原因)", 原因为以下之一: 已关闭模型辅助, 未安装模型, 下载已取消, 下载失败, 模型不兼容, 模型加载失败, 模型打分超时.
  - 缓存目录显示有新版本时追加 "有新版本可用", 点按打开 P6.7 管理页.

  单测 `CodeGenEngineTest` 补充: 关闭模型的输出与完整规则流程一致; 构造失败/取消/超时遵守剩余预算与请求约束; 排序失败只重排其合格输入. 以可复核轨迹与规范化候选差异证明模型在构造阶段介入; 不再断言模型开启与关闭时整条流水线的候选集合相同 (D7).
- [ ] P7.5 文字与 changelog: 新增界面文字同步 11 个 `strings.xml`; changelog feature 1 条 (模型辅助生成: 候选构造与优选, 按需下载, 本地导入, 多版本与更新, 版本对比) x 10 语言. 偏好键入 `strings_donottranslate.xml`.
- [ ] P7.6 阶段收尾: 编译与 `codegen.*` 单测通过; 附录 B 第 11-14 项在 AVD (API 24), Redmi 22120RN86C (API 33), Xiaomi Pad 23046RP50C (API 35) 上执行 (E3), 包括飞行模式下的回退与下载中途取消. 每个子条目一个提交.

### P8 评估, 性能与发布

- [ ] P8.1 发布门槛 (`tools/codegen-model/train/gate.py`): 读 P3.4 的 `metrics.json`, 按附录 E.4 判定通过 / 不通过, 输出一段可粘贴到 `MODEL_CARD.md` 的摘要. 不通过时 `prepare_catalog.py` (P5.3) 拒绝收录该版本 (以 `gate.json` 的 sha256 绑定模型包). 单测 `test_gate.py` (每条门槛恰在边界上与边界外各 1 例).
- [ ] P8.2 性能 (`CodeGenBenchmarkTest` (JVM) + 设备上的 `codegen-bench.js`, 用 Java 互操作调用 `CodeGenEngine`, 不新增脚本 API): 5,000 节点生成树与 3 个真实大界面 (桌面, 设置首页, 长列表), 各 30 次, 记录各阶段 P50 / P95 (搜索, 验证, 特征, 排序, 渲染) 与模型首次加载耗时, 结果以表格写入本条目. 门槛 (D19): Redmi 22120RN86C (API 33) 上 P95 <= 1 s, 首次加载 <= 200 ms, 常驻内存 <= 32 MiB (`Debug.getNativeHeapAllocatedSize` 与 Java 堆差值). 超标时优先降低候选预算 (D19 的 2,000 / 200), 并记录对 E.4 指标的影响.
- [ ] P8.3 首个官方模型发布 (Shared-Assets): 通过 P8.1 门槛的包放入 `codegen-models/sources/autojs6.codegen.ranker/<versionCode>/` (`modelId` 见 Q13), 运行 `prepare_catalog.py`, 由维护者离线签名并 dispatch `publish-codegen-models.yml` (tag `codegen-models-v1`), `verify_release.py` 回读通过; 宿主更新引导目录 `res/raw/codegen_model_catalog_v1.json` (P5.5) 为签名后的目录. 记录 run 链接, 资产 sha256, 模型大小 (D2: 目标 1-5 MiB).
- [ ] P8.4 APK 体积: `python tools/size/apk_size_report.py` 对比 P0 前 (快照 `2082edef14`) 与 P8.3 后的 universal APK, 增量 <= 256 KiB (D1), 结果写入本条目; 超标时先检查是否误把 `.ajcgm` 打进了 `res/raw` 或 `assets`.
- [ ] P8.5 舰队冒烟与架构说明: 附录 B 全部条目在 AVD (API 24), Sony G8441 (API 28), Sony XQ-AT72 (API 31), Redmi 22120RN86C (API 33), Xiaomi Pad 23046RP50C (API 35) 上执行, 结果表 (条目 x 设备) 写入本条目 (E3). 另写 `docs/dev/layout-code-generation-architecture.md` (流水线, 包与模块, 信任链, 数据流, 如何训练与发布新版本, 已知局限), 只写实现事实, 不重复本 Roadmap 的决策表.
- [ ] P8.6 (可选) 用户文档页: 在文档仓库的布局分析页面增加 "生成代码" 小节 (选项含义, 模型辅助的作用与隐私说明, 如何导入本地模型). 仅文档, 不涉及 d.ts / Ace 声明 (D9).

---

## 附录 A. 待决事项

| 编号 | 问题 | 建议 |
| --- | --- | --- |
| Q1 | 模型开关文案与职责说明. | 根据 2026-10-07 目标校正, 建议 "使用模型辅助生成", 副标题 "在本机辅助构造和优选定位方案, 不上传布局". 旧建议 "使用模型优选结果" 与仅强调排序的副标题不再用于代表完整目标. |
| Q2 | 开关是否跨会话记忆? D3 要求默认开启. | 建议记忆 (Pref `key_codegen_use_model`, 默认 true): 关闭过的用户不会每次被下载提示打扰. |
| Q3 | 限时等待的默认超时与输入范围; Flow 的 "等待找到" 用 `timeout: Infinity` 还是 Flow 默认 10 s? | 已按 P0.1 与 E.1 的规定落实并回填 D8: 限时默认 10,000 ms, 范围 100..600,000 ms; Flow 的等待找到显式 timeout: Infinity. 请求约束与渲染已通过 JVM 测试, 输入框在 P1.8 接入. |
| Q4 | 目录地址与镜像: 只用 `raw.githubusercontent.com`, 还是加 jsDelivr 等镜像? | 建议与 Ace 字体一致 (同一地址形式); 资产 `urls[]` 预留镜像位, 首版只有 GitHub Release. |
| Q5 | Shared-Assets 收录哪些文件: 只收模型包与模型卡, 还是也收数据集 manifest 与评估报告? | 建议收模型包, `MODEL_CARD.md`, `dataset-manifest.json` (只含统计与哈希) 与 `metrics.json`; 训练代码留在宿主仓库 (D21). |
| Q6 | 真实应用快照的使用范围: 只用于测试评估, 还是也参与训练? | 建议首版只用于 real-test 评估 (避免任何第三方界面内容进入模型); 待受控数据的指标稳定后再决定是否参与训练. |
| Q7 | 训练框架: CatBoost (对称树, 解释器最简单) 还是 LightGBM (普通树)? | 建议 CatBoost (D12): 对称树的端上解释器约 100 行, 无分配, 文件格式简单. |
| Q8 | 真实应用标注规模与应用清单. | 建议首轮 >= 30 个应用 (系统设置, 主流 IM, 购物, 地图, 浏览器等), 每个 >= 5 个任务组, 每组 >= 3 个状态. |
| Q9 | 是否支持框选 "区域" 作为生成目标 (布局分析已有区域框选)? | 建议不做 (§2 非目标), 待单个 / 集合两种范围稳定后另议. |
| Q10 | 同一时刻一个 "当前模型" (D5), 还是每次生成可临时选择? | 建议一个当前模型, 在管理页切换; 生成对话框只显示当前模型名. |
| Q11 | 更新后旧版本默认保留还是删除? | 建议保留, 完成界面提供 "删除旧版本" (P6.6); 管理页显示占用空间. |
| Q12 | 模型管理入口放在设置的哪个分类? | 建议放在 "无障碍服务" 分类末尾 (与布局分析同属自动化功能); 备选: 新建 "布局分析" 分类. |
| Q13 | 官方 `modelId` 与显示名. | 建议 `autojs6.codegen.ranker`, 显示名 "AutoJs6 代码生成排序模型". |

## 附录 B. 冒烟清单

每项都把生成的代码粘贴到脚本编辑器中运行一次, 目标应用保持在前台 (运行脚本前切回目标界面).

1. 设置文本: 在登录界面输入框上选 "设置文本" 并填入 `a"b\c`, 生成的代码运行后输入框内容正好是 `a"b\c`; "复制为选择器" 对含引号与换行的文本得到合法字符串 (F1, F7, P0.3).
2. 三种查询方式 x 点击: 等待找到 / 限时等待 3,000 ms / 立即判断 各生成一次并运行, 动作都被执行; 输出中没有 `waitFor(`, 没有无参 `findOne()`, 没有空的 `if` 分支 (F2, F8).
3. 列表中的重复项: 在 20 行 "删除" 按钮的第 4 行上生成 (单个, 点击), 运行后只点击第 4 行; 理由中出现 "在 20 个相同项中取第 4 个" 一类说明; 结果对话框 "在布局中显示" 只勾出第 4 行 (F3, F4, F5).
4. 集合: 同一列表选范围 "集合" 中的 "同一列表中的 20 个相似项", 生成 `clickEach` 形式, 命中数显示 20 且 "与所选集合一致"; 运行后 20 行都被点击 (F3, F5).
5. Flow 风格: 长按, 设置文本, 限时等待, 立即判断各一次, 全部可运行, 无 `ReferenceError`; 长按输出为 `flow.waitThenLongClick(...)` 或 `waitAsync(...).longClick()` (F9, F18).
6. 跨包同名 id 与无 id 界面: 设置页 `android:id/title` 一类控件生成时使用完整 id 或附加区分条件, 运行时命中所选控件; 无 id 界面也能给出结果, 失败时显示具体原因而非 "生成失败" (F6, F10, F11).
7. 本地导入: 管理页导入 `tiny-test-v1.ajcgm`, 显示来源 "自定义", 状态 "可用"; 再导入一个改坏一个字节的包, 显示被拒原因, 登记表无残留 (P6.2, P6.7).
8. 重复检测: 再次导入同一文件, 只提示 "已存在相同模型"; 导入同 `modelId` 的 v2, 显示并排对比表 (差异行带 "≠"), 分别试 "保留两者" 与 "替换", 结果符合 P6.4 (D6).
9. 更新: 测试目录中发布 v2 后, 管理页显示 "可更新", 点击后直接开始下载 (无确认), 进度对话框下方显示新旧对比, 完成后当前版本切到 v2, "删除旧版本" 可用 (P6.6, D6).
10. 管理页操作与离线: 切换当前模型, 查看模型卡, 删除当前版本 (提示改用规则排序); 飞行模式下打开管理页, 显示目录获取失败与重试, 已安装模型照常可用 (P6.1, P6.7).
11. 关闭开关: 取消勾选 "使用模型辅助生成" 后生成, 抓包或日志确认无网络请求, 结果显示 "排序: 规则 (已关闭模型辅助)" (D3).
12. 首次下载: 无已安装模型时生成, 显示下载提示 (模型名, 版本, 大小, 来源); 选 "下载" 后显示进度与 "已下载 / 总大小", 完成后不需要再点 "生成" 就直接显示结果, 排序来源为模型 (D4).
13. 取消与失败: 下载中途点 "取消", 临时文件被删除, 对话框提供 "使用规则生成", 选后得到规则结果; 断网时下载失败同样可回退 (D4, P7.2).
14. 回退原因: 当前模型不兼容、构造或排序失败时, 结果说明具体阶段与规则回退原因, 验证预算未重置. 固定合格候选集合后, 模型/规则排序都保持该集合; 模型开启与关闭的端到端候选集合允许不同, 新构造候选仍必须通过同一验证器 (D7, D20, P7.4).

## 附录 C. 证据等级

| 等级 | 含义 |
| --- | --- |
| E0 | 代码已提交, 编译通过 |
| E1 | E0 + JVM 单测 / Python 单测通过 |
| E2 | E1 + AVD 冒烟或 CI run 通过 (记录 API level 或 run 链接) |
| E3 | E2 + 真机冒烟通过 (记录机型) |

## 附录 D. 文件地图

| 关注点 | 文件 |
| --- | --- |
| 引擎 (新增) | `core/accessibility/codegen/`: `CodeGenRequest.kt` (P0.1), `SnapshotNode.kt` (P0.2), `plan/LocatePlan.kt` + `plan/SelectorText.kt` (P0.4), `TargetSets.kt` (P1.1), `search/CandidateSearch.kt` (P1.2), `verify/PlanVerifier.kt` (P1.3), `render/ClassicRenderer.kt` / `render/FlowRenderer.kt` (P1.4, P1.5), `rank/{Ranker,RuleRanker,Explanation,ModelRanker}.kt` (P1.6, P4.3), `CodeGenEngine.kt` (P1.7), `dataset/TaskGroup.kt` (P2.1), `features/{CandidateFeatures,FeatureSchema}.kt` (P3.1), `model/{ModelPackageReader,ObliviousTreeModel,ModelCache}.kt` (P4.2, P4.3, P7.3) |
| 存储 (新增) | `core/accessibility/codegen/store/`: `ModelCatalog*.kt` (P6.1), `ModelStore.kt` (P6.2), `ModelDownloader.kt` (P6.3), `DuplicateCheck.kt` / `VersionComparison.kt` (P6.4), `ModelUpdates.kt` (P6.6) |
| 引擎 (复用, 只读) | `core/automator/UiNode.kt`, `core/accessibility/UiSelector.kt` (`searchIn`), `core/automator/pick/Compass.kt`, `core/accessibility/inspector/{InspectorNode,SelectorCallParser,TreeMatch}.kt`, `core/accessibility/NodeInfoSnapshot.kt`, `LayoutSnapshotFiles.kt`, `ui/edit/editor/ace/font/{FontCatalogSignatureVerifier,FontDownloader}.kt` (D16) |
| 引擎 (改动) | `core/automator/filter/JsLiteral.kt` (新增) + `StringMatcher.kt`, `core/accessibility/inspector/SelectorExport.kt` (P0.3), `TreeMatch.kt` (P1.9 的 `ofMatched`, 如需) |
| 界面 (新增 / 改动) | `ui/floating/codegen/{CodeGenerateDialog,CodeGenOptionsState,CodeGenResultDialog,CodeGenFlow,ModelComparisonView,ModelDuplicateDialog}.kt`, `ui/settings/codegen/{CodeGenModelsActivity,CodeGenModelsPreference,CodeGenModelsViewModel}.kt`, `ui/floating/LayoutFloatyWindow.kt` (入口), `res/xml/fragment_preferences.xml` (Q12), `AndroidManifest.xml` (管理页 Activity) |
| 删除 | `codegeneration/**`, `ui/floating/CodeGenerateDialog.java`, `app/src/test/.../FlowCodeStyleTest.kt` (P1.8) |
| 资源 | `res/values{,-ar,-en,-es,-fr,-ja,-ko,-ru,-zh,-zh-rHK,-zh-rTW}/strings.xml`, `strings_donottranslate.xml` (`key_codegen_use_model`, `key_codegen_model_current`), `res/raw/codegen_model_catalog_v1.json`, `res/raw/codegen_model_catalog_public_key_v1.der` (P5.5) |
| 单测与夹具 | `app/src/test/java/org/autojs/autojs/core/accessibility/codegen/**`, `app/src/test/resources/codegen/*.layout.json`, `app/src/test/resources/codegen/models/tiny-test-v1.ajcgm`, 复用 `core/automator/FakeUiNode.kt` |
| 数据与训练 | `test-apps/codegen-fixture/` (P2.2), `tools/codegen-model/{capture,label,dataset,train,package}/` (P2-P4, P8.1) |
| Shared-Assets | `codegen-models/`, `tools/codegen-model-distribution/`, `.github/workflows/publish-codegen-models.yml` (P5) |
| 文档 | 本文, `docs/dev/layout-code-generation-architecture.md` (P8.5) |

## 附录 E. 速查

### E.1 计划族与渲染表

记号: `S` = 目标自身的选择器, `A` = 锚点或容器的选择器, `T` = 子树内的选择器 (三者都由 `SelectorText.render` 输出); `c` = 罗盘字符串; `i` = 序号; `t` = 超时毫秒; `act()` / `actEach()` = 第四张表的动作调用. 表中以 `;` 分隔的语句在输出中各占一行; 输出只用 `let`, 不用 `const`.

计划族:

| 族 | 计划含义 | 范围 | 一次解析 (返回控件 / 集合, 找不到为 null) |
| --- | --- | --- | --- |
| `SELF` | 目标自身的选择器; 单个取先序第一个, 集合取全部匹配 | 单个, 集合 | `S.findOnce()` / `S.find()` |
| `SELF_INDEX` | 选择器的第 i 个匹配, 负数自末尾计 | 单个 | `S.findOnce(i)` |
| `ANCHOR_PATH` | 唯一锚点 + 罗盘路径 | 单个 | `function locate() { let a = A.findOnce(); return a && a.compass("c"); }` |
| `ANCHOR_SEARCH` | 唯一祖先锚点 + 子树内首个匹配 (含锚点自身) | 单个 | `function locate() { let a = A.findOnce(); return a && a.findOne(T); }` |
| `CONTAINER_ITEMS` | 唯一容器 + 子树内全部匹配 | 集合 | `function locate() { let a = A.findOnce(); let ws = a && a.find(T); return ws && ws.nonEmpty() ? ws : null; }` |

`locate()` 在集合为空时也返回 `null`: 全局 `wait` 与 Flow 的函数条件都把空集合视为 "已满足" (`Global.kt` 的 `isWaitConditionSatisfied`), 必须由函数自己排除.

经典风格 (`ClassicRenderer`), `SELF` 族直接用选择器 API:

| 查询方式 | 单个 | 集合 | 无动作时 (单个 / 集合) |
| --- | --- | --- | --- |
| 等待找到 | `S.untilFindOne().act();` | `S.untilFind().actEach();` | `let w = S.untilFindOne();` / `let ws = S.untilFind();` |
| 限时等待 | `let w = S.findOne(t); if (w) { w.act(); }` | `if (S.findOne(t)) { S.find().actEach(); }` | `let w = S.findOne(t);` / `let ws = S.findOne(t) ? S.find() : null;` |
| 立即判断 | `let w = S.findOnce(); if (w) { w.act(); }` | `let ws = S.find(); if (ws.nonEmpty()) { ws.actEach(); }` | `let found = S.exists();` / `let ws = S.find();` |

经典风格, `SELF_INDEX` 与其余族先输出 `locate()` (`SELF_INDEX` 为 `function locate() { return S.findOnce(i); }`), 再用全局 `wait` (超时 >= 100 ms 时第二个参数按超时解释, 与 P0.1 的输入下限一致):

| 查询方式 | 有动作 | 无动作 |
| --- | --- | --- |
| 等待找到 | `wait(locate, Infinity, 200, { then: w => w.act() });` | `let w = wait(locate, Infinity, 200, { then: w => w });` |
| 限时等待 | `wait(locate, t, 200, { then: w => w.act() });` | `let w = wait(locate, t, 200, { then: w => w });` (超时为 `false`: `else` 返回 `null` 时不覆盖结果) |
| 立即判断 | `let w = locate(); if (w) { w.act(); }` | `let w = locate();` |

集合把 `w.act()` 换成 `ws.actEach()`. 动作接收者为 `Up(n)` 时: 单个对解析结果追加 `.parent(n)` 并在动作前判空; 集合改为 `ws.each(w => { let r = w.parent(n); if (r) { r.act(); } })`.

Flow 风格 (`FlowRenderer`). 条件 `C`: `SELF` 族为 `S` (单个且接收者为 `Up(n)` 时加选项 `compass: 'pN'`), 其余族为 `locate`; 集合加选项 `resultType: 'widgets'`:

| 查询方式 | 有动作 | 无动作 |
| --- | --- | --- |
| 等待找到 | 点击: `waitThenClick(C, { timeout: Infinity }).sync();`; 其他: `waitAsync(C, { timeout: Infinity }).act().sync();` | `let w = waitAsync(C, { timeout: Infinity }).sync();` |
| 限时等待 | `flow.whenPresent(C, w => w.act(), { timeout: t }).sync();` | `let w = null; flow.whenPresent(C, x => { w = x; }, { timeout: t }).sync();` |
| 立即判断 | 同限时等待, `{ timeout: 0 }` | 同限时等待, `{ timeout: 0 }` |

`flow.whenPresent` 超时只跳过处理函数而不抛错 (`FlowScheduler.runWhenPresent`), 与经典风格的 "找不到就跳过" 一致; 等待找到用 `timeout: Infinity` (Q3), 不用 Flow 默认的 10 s. 链上的动作 (`.click()` / `.setText(x)` 等) 对集合逐个执行. 集合且接收者为 `Up(n)` 时动作放进回调 (`.then(ws => ...)` / 处理函数), 内容同经典风格.

动作:

| 动作 | 单个 `act()` | 集合 `actEach()` | Flow 链 | 接收者须具备 (P1.3) |
| --- | --- | --- | --- | --- |
| 点击 | `click()` | `clickEach()` | `waitThenClick(...)` 或 `.click()` | clickable |
| 长按 | `longClick()` | `longClickEach()` | `.longClick()` (不用全局 `waitThenLongClick`, F18) | longClickable |
| 设置文本 | `setText("x")` | `setTextEach("x")` | `.setText("x")` | editable |
| 向前滚动 | `scrollForward()` | `scrollForwardEach()` | `.scrollForward()` | scrollable |
| 向后滚动 | `scrollBackward()` | `scrollBackwardEach()` | `.scrollBackward()` | scrollable |

`"x"` 经 `JsLiteral.quote` 输出 (P0.3).

旧对话框查询选项到新请求的映射 (`LegacyMode.toRequest`, P0.1):

| 旧选项 | 新请求 | 说明 |
| --- | --- | --- |
| 直到找到一个 (默认) | 单个, 等待找到 | 旧版输出已弃用的无参 `findOne()` (F8) |
| 直到找到所有 | 集合 (取 `TargetSets` 首项), 等待找到 | 旧版只要求非空, 不检查集合 (F3) |
| 等待控件出现 | 单个, 等待找到 | 旧版输出 `waitFor()` 且忽略动作 (F2); 新版保留动作 |
| 判断控件存在 | 单个, 立即判断 | 旧版输出空的 `if` 分支 (F2) |
| 未勾选任何项 | 单个, 等待找到 | 旧版静默回退 (F8); 新版为单选, 不会出现 |

### E.2 模型包与目录字段

下表为已实现的 v1 排序包及目录规范. 带构造能力的格式、角色/语法/特征声明和能力兼容需在原 P4/P5 条目内显式扩展; 已发布测试 Release v1/v2 都是排序包, 不表示构造包格式已实现.

`.ajcgm` 内的 `manifest.json` (P4.1 写, P4.2 读, `model-manifest-v1.schema.json` 约束):

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `format` | int | 固定 1 |
| `modelId` | string | `^[a-z0-9][a-z0-9._-]{1,63}$`, 官方为 `autojs6.codegen.ranker` (Q13) |
| `displayName` | object | 语言标签到名称, 至少含 `en`; 缺当前语言时回退 `en` |
| `versionCode` / `versionName` | int / string | `versionCode` > 0, 同一 `modelId` 内单调递增 |
| `kind` / `algorithm` | string | 固定 `ranker` / `catboost-oblivious` |
| `featureSchemaVersion` / `featureNamesHash` | int / string | 与宿主 `FeatureSchema` 比较 (D13) |
| `model` | object | `file` (`model.bin`), `size`, `sha256`, `trees`, `depth` (最大深度), `features` (特征数) |
| `dataset` | object | `datasetId`, `groups` (训练任务组数), `createdAt` |
| `metrics` | object | 附录 E.4 指标的摘要: `test` / `realTest` 各含 `top1Survival`, `ruleTop1Survival`, `regret`, `groups` |
| `training` | object | 工具版本 (CatBoost, Python), 宿主仓库提交 hash, `trainedAt` |
| `minHostVersionCode` | int | 低于此值的宿主显示 "需要更新 AutoJs6" |
| `license` / `publisher` | string | SPDX 表达式 / 发布者 (官方为 `AutoJs6`) |

目录 (`catalog-v1.json`) 的每个版本项字段见 P5.2; 其中 `featureSchemaVersion`, `featureNamesHash`, `minHostVersionCode`, `datasetId`, `metrics`, `license` 与包内 manifest 必须一致 (`prepare_catalog.py` 校验), 使下载前即可做重复检测与版本对比 (P6.4).

### E.3 候选特征 (`featureSchemaVersion = 1`)

此处为完整候选的终局排序特征. 构造状态/动作特征另有独立定义与 schema/hash, 不对下表缺失字段补零来代替, 具体字段由构造原型验证后落实到原 P3 条目.

全部为 `Float`; 布尔记 0 / 1; 计数类做 `ln(1 + x)`; 缺失记 -1 (CatBoost 只按阈值分裂, -1 落在最左侧). 组内相对特征以同一请求的全部已验证候选为组.

| 分组 | 特征 | 来源 |
| --- | --- | --- |
| 请求 | `scope_collection`, `action_*` (6 个独热), `attr_id` / `attr_text` / `attr_desc` 开关 | `CodeGenRequest` |
| 族 | `family_*` (5 个独热), `receiver_up_levels` | `LocatePlan` |
| 选择器 | `cond_count`, `has_full_id`, `has_entry_id`, `has_text`, `has_desc`, `has_class`, `has_package`, `structural_cond_count` | `SelectorSpec` (锚点与目标各一组, 前缀 `self_` / `anchor_`) |
| id 质量 | `id_entry_len`, `id_looks_obfuscated` (条目名 <= 2 字符或无元音的随机串), `id_cross_package_dup` | 条件值, 窗口统计 |
| 文本质量 | `text_len`, `text_digit_ratio`, `text_dynamic` (数字 / 时间 / 计数模式), `text_cjk_ratio`, `desc_len`, `desc_dynamic` | 条件值, `facts` |
| 序号 | `index_abs`, `index_negative`, `index_rel` (i / 匹配数), `selector_match_count` | `PlanStep.Index`, `facts` |
| 路径 | `path_steps`, `path_up_levels`, `path_child_steps`, `path_sibling_steps`, `path_max_child_index`, `anchor_distance` | `PlanStep` |
| 容器 | `container_item_count`, `container_item_uniform` (项结构签名一致比例) | `facts` |
| 目标 | `target_depth`, `target_has_id`, `target_has_text`, `target_clickable`, `target_in_list` (有可滚动且子节点重复的祖先), `target_same_class_siblings`, `target_set_size` | 目标节点 |
| 组内相对 | `rule_rank_norm` (规则名次 / 组大小), `rule_cost_gap` (与组内最优的代价差), `group_size`, `family_rank_in_group` | 同组 `Verified` |

特征名称以表中顺序展开 (独热按枚举顺序), 完整列表以 `FeatureSchema.names` 为准, 本表只是分组说明; 修改任何特征 (增删, 改顺序, 改编码) 都必须升 `featureSchemaVersion` (P3.1).

### E.4 评估指标与发布门槛

每个任务组 g 有已验证候选集合 V(g), 每个候选有 `survival` ∈ [0, 1] (P2.4). 记 `best(g) = max survival`, 排序器首选为 `top(g)`. 下表保留固定集合上的排序指标; 校正后的端到端评估须另外固定原规则流水线的集合与首选作为独立基线, 不以合并模型候选后的 RuleRanker 分数替代它.

| 指标 | 定义 |
| --- | --- |
| `top1Survival` | 各组 `survival(top(g))` 的平均值 |
| `ruleTop1Survival` | 同上, 首选取 `RuleRanker` 的结果 |
| `regret` | 各组 `best(g) - survival(top(g))` 的平均值 |
| `perfectRate` | `survival(top(g)) == 1` 的组所占比例 |
| `worseThanRule` | `survival(top(g)) < survival(ruleTop(g)) - 0.01` 的组所占比例 |
| `top3Recall` | 前 3 名中存在 `survival == best(g)` 的组所占比例 (对应结果对话框的备选) |

指标按 test 与 real-test 分别计算, 并按族, 变化种类, 界面族细分 (P3.4). 构造能力另按 [构造设计第 6 节](layout-code-generation-model-construction-design.md#6-在接入-p7-前验证贡献) 进行相同语法/预算/终局排序器的对照, 报告轨迹、规范化新增候选、覆盖率、候选质量上限与退化. 仅排序分数提高不能作为模型参与构造的证据; 原型效果与完整链路预算未验收前不得发布带构造能力的模型. 原有发布门槛继续保留, 按独立的完整规则基线解释 (P8.1, 全部满足才允许进入目录):

| 编号 | 门槛 |
| --- | --- |
| G1 | test: `top1Survival` >= `ruleTop1Survival` + 0.05 |
| G2 | real-test: `top1Survival` >= `ruleTop1Survival` (不劣于规则, D20); real-test 组数 >= 150 |
| G3 | test 与 real-test: `worseThanRule` <= 5% |
| G4 | 任一变化种类上 `top1Survival` 不低于规则 0.05 以上 (避免以某类退化换取平均提升) |
| G5 | 新版本相对当前官方版本: real-test 的 `top1Survival` 不下降超过 0.01 |
| G6 | 包大小 < 100 MiB 且目标 1-5 MiB (D2); 设备上满足 D19 (P8.2) |
| G7 | P4.4 一致性测试通过 (每个分数误差 <= 1e-4, 每组首选相同) |

G1 / G2 的数值为首版建议, 由维护者在 P8.1 前确认 (可随数据规模调整, 调整记录在本表).

### E.5 版本对比字段

`VersionComparison.build(old, new)` (P6.4) 输出以下行, 顺序固定; 两侧值不同的行标记 `differs` (界面以强调色背景 + "≠" 标出):

| 行 | 内容 | 来源 |
| --- | --- | --- |
| 名称 | `displayName` (当前语言) + `modelId` | manifest |
| 版本 | `versionName` (`versionCode`) | manifest |
| 来源 | 官方 / 自定义 (D15) | 登记表或目录 |
| 内容 | sha256 前 12 位 | 文件 |
| 大小 | 人类可读 (KiB / MiB) | 文件或目录 |
| 兼容性 | `featureSchemaVersion` + "兼容 / 不兼容 / 需要更新 AutoJs6" | manifest + 宿主 |
| 数据集 | `datasetId` 与创建日期 | manifest |
| 指标 | `top1Survival` (real-test) 与相对规则的差值 | manifest `metrics` |
| 发布时间 | `releasedAt` (目录) 或 `trainedAt` (包) | 目录 / manifest |
| 安装时间 | `installedAt`; 新版本一侧显示 "-" | 登记表 |
| 许可证 | `license` | manifest |
| 状态 | 当前使用 / 已安装 / 已撤回 | 登记表 + 目录 |

缺失的字段显示 "-" (`VersionComparisonTest`). 下载前的对比用目录中的值, 导入时用包内 manifest 的值, 两者字段相同 (E.2).
