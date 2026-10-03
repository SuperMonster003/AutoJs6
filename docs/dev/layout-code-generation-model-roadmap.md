# AutoJs6 布局分析 "生成代码" 模型辅助生成 Roadmap

本文是布局分析 "生成代码" 对话框 (`CodeGenerateDialog`) 改造为 "确定性候选搜索 + 计划验证 + 小模型排序" 的可执行状态表.
以 2026-10-03 的本地代码快照 (`master@2082edef14`, `VERSION_NAME=6.8.0`, `VERSION_BUILD=5315`) 为起点,
每个条目均可独立 Check 并落地, 后续会话按阶段逐步推进.

使用方式:

1. 每次会话开始时, 从 "阶段总览" 选取一个或多个未完成条目. P0-P1 不依赖模型, 单独落地即有价值, 必须先做; 之后分两条可并行的线: 数据与模型 (P2-P4), 分发与管理 (P5-P6); 两线在 P7 (对话框集成) 与 P8 (评估与发布) 汇合.
2. 条目完成后勾选 `[x]`, 并在条目后追加证据 (提交 hash / 测试类名 / 设备型号 / 数据集 manifest), 证据等级见附录 C.
3. 涉及字符串资源的条目必须同步 11 个 `strings.xml` (`values` + 10 个语言目录, `values` 与 `values-en` 内容一致, 引号写作 `\"`); 涉及 feature / fix / improvement 的提交必须同步 `.changelog/lang_*.json` (10 个语言) 并重新生成 markdown.
4. 附录 A 的 "待决事项" 在进入对应阶段前由维护者拍板, 拍板结果回填到 "固定决策".
5. 生成的代码只使用现有脚本公开 API (D9), 因此不涉及文档 / d.ts / Ace 声明仓库同步; P8.6 的用户文档页为可选项. Shared-Assets 仓库的条目 (P5, P8.3) 在该仓库内提交; 签名与发布 dispatch 由维护者执行.
6. 一个子条目一个提交, 只暂存本功能的文件 (工作区可能有其他会话的未提交改动).

---

## 1. 固定决策与硬约束

D1-D6 来自 2026-10-03 的需求原文, 为硬性要求; D7-D21 是本 Roadmap 据此推导的实现约束, 维护者可在 P0 开始前经附录 A 推翻, 之后不再重新讨论.

| 编号 | 决策 | 含义 |
| --- | --- | --- |
| D1 | 模型不进宿主 APK | 训练好的模型发布到 `AutoJs6-Shared-Assets` 仓库的新资产族 `codegen-models/` (独立目录, manifest, tag 前缀, 工作流, 签名密钥, 与 `ace-fonts/` 同构). 宿主只内置: 目录公钥, 引导目录 (bootstrap catalog, 数 KB), 推理解释器与管理代码. 宿主 universal APK 增量 <= 256 KiB, 以 `tools/size/apk_size_report.py` 记录. |
| D2 | 模型规模 | 目标 1-5 MiB 的排序模型; 硬上限 100 MiB, 且单个发布文件必须 < 100 MiB (GitHub 单文件限制); 推理常驻内存 <= 32 MiB. |
| D3 | 对话框新增 "使用模型辅助生成" 复选框, 默认开启 | 位于 "生成代码" 对话框的选项区 (文案可调, 见 Q1; 是否跨会话记忆见 Q2). 关闭时完全走规则排序 (P1), 不触网, 不加载模型. |
| D4 | 按需下载, 显示进度, 可取消 | 只在用户点击 "生成" 且开关开启, 本机没有可用模型时提示下载: 显示模型名, 版本, 大小, 来源; 下载对话框显示进度条与 "已下载 / 总大小", 可取消. 取消或失败不阻塞本次生成: 提供 "使用规则生成". 下载完成后自动继续本次生成. |
| D5 | 多模型, 本地导入, 多版本, 可更新 | 官方目录可列出多个模型 (通常一个), 用户可下载其中任意个; 支持导入本地离线模型包 (自定义或官方均可); 每个模型 (`modelId`) 可安装多个版本, 可更新到新版本. 同一时刻只有一个 "当前使用" 的模型版本 (Q10). |
| D6 | 重复检测与版本对比 | 导入本地模型或下载在线模型时, 若已存在相同 `modelId` (任一版本) 或相同内容 (sha256), 先提示用户, 并以并排表格显示两个版本 (附录 E.5 的对比字段, 差异行高亮), 选项为 "替换 / 保留两者 / 取消" (内容完全相同时只提示 "已存在相同模型"). 更新到新版本时不弹确认提示, 但进度与完成界面同样并排显示新旧两个版本. |
| D7 | 模型只给已验证的候选排序 | 流水线固定为: 结构化请求 -> 确定性候选搜索 -> 计划验证 -> 排序 -> 渲染. 未通过验证的候选不进入排序, 也不会出现在结果中; 因此任何模型 (含自定义模型) 都不能让当前布局上的结果出错, 只影响 "选哪一个正确计划". 规则排序器 (`RuleRanker`) 始终可用, 是模型的基线与回退. 界面不展示概率; 理由文字由程序依据计划结构生成, 不由模型生成. |
| D8 | 生成请求结构化 | 新增 `CodeGenRequest`, 对话框只负责组装请求: 目标集合, 范围 (单个 / 集合), 查询方式 (等待找到 / 限时等待 / 立即判断 3 种; 旧对话框的 4 个选项按附录 E.1 映射, "直到找到所有" 并入范围 "集合"), 动作 (无 / 点击 / 长按 / 设置文本 / 向前滚动 / 向后滚动) 与参数, 动作接收者策略 (自动 / 自身), 可用属性 (id / text / desc 三个开关), 代码风格 (经典 / Flow). 覆盖全部组合, 而非只覆盖 "唯一目标". |
| D9 | 定位计划 IR + 两个渲染器, 不改脚本 API | `LocatePlan` 描述 "如何从窗口根找到目标" (锚点选择器, 关系步骤, 序号, 基数, 动作接收者), 由 `ClassicRenderer` / `FlowRenderer` 渲染. 只输出现有公开 API (`untilFindOne` / `findOnce(i)` / `untilFind` / `exists` / `parent(n)` / `child(i)` / `findOne(selector)` / `*Each` / `waitAsync` / `waitThenClick` 等), 渲染表见附录 E.1. |
| D10 | 验证器按脚本语义执行计划 | 在捕获树上以脚本引擎同一套 `Selector` / 搜索算法执行计划 (DFS 先序, `findOnce(i)` 的序号, `untilFindOne` 取首个匹配), 单目标要求解析结果即为目标, 集合要求集合相等. 验证范围只有布局分析捕获的活动窗口 (F13), 在理由中注明. |
| D11 | 引擎泛型于 `UiNode<N>`, 与 Android 解耦 | 新引擎包 `core/accessibility/codegen/**` 中除设备侧适配外不引用 Android 类型; 设备上用既有 `InspectorNode` (`id()` 为 `fullId`), JVM 上用新增 `SnapshotNode` (直接包装 `NodeInfoSnapshot`, 不经 `NodeInfo`) 与既有 `FakeUiNode`. 旧 `codegeneration/**` 在 P1.8 切换后删除 (`com.stardust.**` 兼容包不动). |
| D12 | 模型 = 纯数值特征上的 CatBoost 对称树 (oblivious trees), Kotlin 解释执行 | 不引入任何 ML 运行时 (ONNX / TFLite / CatBoost JNI). 只用数值特征 (类别特征在 Kotlin 侧预先编码), 深度 4-6, 树数 <= 1024: 每棵树 = d 个 (特征序号, 阈值) + 2^d 个叶值, 1024 棵深度 6 约 0.3 MiB. 训练目标 YetiRank 或 PairLogit, 以任务为分组. |
| D13 | 特征只有一份实现 | 候选特征由 Kotlin `CandidateFeatures` 计算; PC 侧训练数据也经同一 Kotlin 代码 (JVM 工具任务) 导出, Python 只负责训练与导出. 特征集带 `featureSchemaVersion` 与名称列表哈希; 宿主声明支持的 schema 版本区间, 不兼容的模型显示为 "不兼容" 且不加载. |
| D14 | 模型包格式自定 | 扩展名 `.ajcgm` (zip): `manifest.json`, `model.bin` (对称树, 小端, 魔数与格式版本), `features.json`, `MODEL_CARD.md`, `LICENSE`; 字段见附录 E.2. 读取器做全量边界检查 (条目白名单, 解压大小上限, 特征序号, 叶数 = 2^d, 校验和), 畸形包只会被拒绝, 不会崩溃. |
| D15 | 信任与来源 | 官方模型经 Shared-Assets 的签名目录分发 (ECDSA P-256 / SHA-256, 新密钥, 与 Ace 字体同一套校验: 公钥固定, 拒绝回滚 / 同版本异内容, 逐文件校验大小与 sha256). 本地导入的包若 sha256 与已验证官方目录中的某个版本一致, 标为 "官方", 否则标为 "自定义". 由于 D7, 自定义模型无需额外的安全警告, 只标注来源. |
| D16 | 复用 Ace 字体分发栈, 不搬动它 | 直接复用通用部分 (`EcdsaP256CatalogSignatureVerifier`, `FontDownloadConnection` / `OkHttpFontDownloadSource`, 大小与 sha256 校验); 字体专属的目录模型 / 仓库 / 存储在 `codegen/store/` 下按同一模式另写. 不重构 `ui/edit/editor/ace/font/**`. |
| D17 | 存储与选择 | 已安装模型位于 `noBackupFilesDir/codegen_models/<modelId>/<versionCode>-<sha8>/model.ajcgm`, 登记表 `registry.json`; 安装先写临时目录, 校验后原子改名. 当前模型存 Pref `key_codegen_model_current` (`modelId@versionCode-sha8`); 指向的版本不存在时视为 "未安装". |
| D18 | 网络与隐私 | 只在需要时联网: 点击生成且无可用模型, 打开模型管理, 手动检查更新. 目录缓存 24 h, 不后台下载, 不上传布局, 无遥测. 训练数据只来自维护者主动采集 (P2), 真实应用的原始快照不入公开仓库. |
| D19 | 性能预算 | 生成 (候选搜索 + 验证 + 特征 + 排序 + 渲染) 在工作线程执行, 可取消; 5,000 节点树上 P95 <= 1 s (Redmi 22120RN86C, API 33), 超过 300 ms 显示 "正在生成"; 每次生成至多尝试 2,000 个候选, 保留 200 个通过验证的候选. 模型首次加载 <= 200 ms, 之后常驻缓存直到切换模型. |
| D20 | 规则排序是基线与回退 | 开关关闭, 模型缺失 / 不兼容 / 损坏, 加载或打分抛异常, 打分超时: 一律回到 `RuleRanker`, 结果对话框注明原因. 新模型版本只有在 P8.1 的门槛 (附录 E.4) 上不劣于规则排序时才能发布. |
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

<!-- CONTINUE-CODEGEN-ROADMAP -->
