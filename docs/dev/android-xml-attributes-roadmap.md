# AutoJs6 Android XML 布局属性扩充与预览资源目录 Roadmap

本文是宿主侧两项工作的可执行状态表, 由 3-Block Compose 插件路线图 P11 的决策 D65 (2026-10-16, 维护者选择 "宿主先行, 两项都先做") 立项:

- **R (资源目录)**: ui 布局预览服务在预览与交互预览中解析工程的 `res/` 目录 (插件路线图阶段 P11.H1).
- **A (属性集扩充)**: 动态布局器 (`DynamicLayoutInflater`) 对 Android XML 属性的支持从 "约 740 个手写属性名" 扩充为 "以 Android SDK 元数据驱动, 手写处理器优先, 反射 setter 兜底, 其余明确列为不支持并给出诊断" (插件路线图阶段 P11.H2).

起点为 2026-10-16 的本地代码 (`master@33a1514b7`, `VERSION_NAME=6.8.0`). 使用方式与仓库其它 roadmap 相同: 条目完成后勾选 `[x]` 并在其下写证据 (提交, 测试类, 设备 / API); 附录 B 的待决事项进入对应阶段前由维护者拍板; 宿主工作区由多个会话共用, 未编译通过的新文件先在会话 scratchpad 完成, 放回源码树后立即编译, 提交只暂存本功能的文件 (或 `git commit -- <路径>`).

---

## 0. 结论

- 严格意义的 "完整 Android XML 属性集" 不可达: 平台 `LayoutInflater` 与 View 构造函数只能从编译期生成的二进制 XML (`XmlBlock`) 读取属性, 运行时文本 XML 无法交给它们 (`obtainStyledAttributes` 要求 `XmlBlock.Parser`, 构造它需要隐藏 API). 宿主的 View 一律以 `AttributeSet = null` 构造, 属性全部由宿主处理器设置. 本路线图的目标是: 公开 API 能设置的属性都能设置, 不能设置的明确告知, 不再因个别属性让整个布局失败.
- 预览的资源目录与交互预览的工程目录都以 zip 经文件描述符交给宿主, 宿主解到会话目录后按正式运行相同的规则 (`ScriptResourceRepository`) 解析; 这是预览契约 v3, 旧键继续回答 v2 身份, 已发布的 3-Block Compose 1.7.0 不受影响.

---

## 1. 决策

| 编号 | 决策 | 含义 |
| --- | --- | --- |
| D1 | 宿主先行 (插件 D65, 维护者 2026-10-16) | R 与 A 都在插件 P11.3 之前完成; 改动留在本地 master; 插件按能力降级 |
| D2 | 元数据来源 | `android:` 属性取自 Android SDK `platforms/android-<N>/data/res/values/attrs.xml` (API 24 - 37 逐级比对得到每个属性在每个 styleable 中的起始 API, 以 `public-final.xml` 过滤非公开属性); `app:` 属性取自宿主实际依赖的 AndroidX / Material AAR 的 `res/values/values.xml` (版本以 `gradle/libs.versions.toml` 为准). 生成物入库, 生成器 `--check` 检测漂移 |
| D3 | 处理顺序 | 手写处理器 (现有 `*Attributes` 与各 `ViewInflater.setAttr`) 优先; 未命中时按元数据兜底 (映射表 -> 约定 setter -> 布局参数字段); 仍未命中归入 "不支持" 或 "未知". 兜底只作用于布局 (inflate) 路径, 不改变脚本 `view.attr()` / `NativeView.has` 的语义 (待决 Q3) |
| D4 | 取值转换复用现有工具 | 尺寸 `Dimensions`, 颜色 / 颜色状态列表与引用 `AndroidResourceResolver`, Drawable `Drawables`, id `Ids`, 字符串 `Strings`; 枚举 / 标志按元数据的 `enum` / `flag` 值表; 格式为多选时按 "引用 -> 颜色 -> 尺寸 -> 数值 -> 布尔 -> 字符串" 的顺序尝试 |
| D5 | 属性按书写顺序应用 | `resolveAttributes` 改为保持 XML 顺序 (`LinkedHashMap`), 简写与分项 (margin / marginLeft, padding / paddingLeft) 的结果因此确定: 后写的覆盖先写的 |
| D7 | 不支持 / 未知属性的运行时行为 (Q1 拍板, 2026-10-16) | 保留原 XML 的布局中, 不支持与未知属性跳过而不抛异常, 每个 (类, 属性) 在脚本控制台警告一次; AutoJs6 简写 XML 的现有行为不变 |
| D8 | 资源限定符与 vector 纳入 (Q2 拍板, 2026-10-16) | `ScriptResourceRepository` 支持限定符目录 (`-night` / `-notnight`, 语言与地区, `-land` / `-port`, `-v<N>`, `-sw<N>dp` / `-w<N>dp` / `-h<N>dp`, 密度), 按当前配置以 Android 的择优规则选取; drawable 支持 `<vector>` (宿主自行解析 path 数据, 平台 `VectorDrawable` 只能从编译后的 XML 创建) |
| D9 | 兜底只用于布局 (Q3 拍板, 2026-10-16) | 属性兜底只在 inflate 路径生效, 脚本 `view.attr()` 与 `NativeView.has` 的语义不变 |
| D6 | 预览契约 v3 只追加 | `setLayout` 请求追加资源 zip 的文件描述符, 布局文件在工程中的相对路径与 `androidLayout` 模式; `runScript` 请求追加工程 zip; 新诊断码 `ATTRIBUTE_IGNORED`. `getContractVersion()` / `getContractHash()` 仍答 v1, `getPreviewInfo()` 的 `contractVersion` / `contractHash` 仍答 v2 (1.7.0 客户端严格比较它们), v3 经新键 `supportedContractVersions` / `contractHashes` (同序数组) 宣告, 资源能力另有 `resourcesSupported` |

---

## 2. 现状事实 (2026-10-16 调研)

- 属性处理器: `core/ui/attribute/` 104 个文件, 约 529 处 `register*`, 含别名约 740 个属性名; `ViewAttributesFactory` 按 View 类链选属性类, 无任何通用兜底 (仅 `setGravity` 与未知 LayoutParams 的 `gravity` 字段两处反射). `registerAttrUnsupported` 的 58 个名字设值即抛异常, 使整个布局失败 (含 `onClick`, `tooltipText`, `layerType`, `nextFocus*`, `importantForAutofill`, `paddingHorizontal`); `paddingVertical` / `paddingHorizontal` 先注册后被不支持覆盖, 是缺陷.
- 命名空间: `ViewInflater.setAttr(view, ns, ...)` 对 `null` / `android` / `app` 走同一组处理器, 其它前缀 (`tools:`) 返回 false; 属性名超过两段抛 "Illegal attr name". `setAttr` 的返回值在 `DynamicLayoutInflater.applyAttribute` 与 ui 运行时的 `UIProxyLayoutInflaterDelegate.beforeApplyAttribute` 中都被丢弃, `afterApplyAttribute` 在运行时与预览中都不会被调用; 未处理的属性没有任何记录.
- View 创建: 简写标签经 `XmlConverter` 映射; 不含点号的类名先查 `ClassNameFilter.extendedClassNameMap` (34 个 AndroidX / Material 短名), 再试 `android.widget` / `android.view` / `android.webkit`; 反射构造只试 `(Context, null, 0, styleRes)` (有 style 时), `(Context)`, `(Context, null)`.
- 布局参数: 全部 `layout_*` 在 `ViewAttributes` 基类处理 (宽高, gravity, weight, margin, RelativeLayout 18 条规则); ConstraintLayout 的 `layout_constraint*`, CoordinatorLayout 的 `layout_behavior` / `layout_anchor`, AppBarLayout 的 `layout_scrollFlags`, CollapsingToolbarLayout 的 `layout_collapseMode` 均未处理, 静默忽略. 属性按 HashMap 顺序应用.
- 资源: `ScriptResourceRepository.configure(sourceFile, workingDirectory)` 从源文件目录向上找 `res`, 找不到看工作目录; 只认无限定符目录 (测试锁定); values 支持任意类型, 数组, style 继承; 文件资源 layout / drawable / mipmap / color; drawable XML 支持 shape / selector / layer-list 等, 不支持 vector; `<include>` 只读工程 `res/layout`, 深度上限 32. `inflate(xml)` 每次都以 cwd 重新 `configure`, `inflateFile(path)` 以源文件 `configure`.
- 预览: `UiPreviewService.setLayout` -> `UiPreviewSession.applyLayout` -> `UI.layout(runtime, xml)`; 会话运行时的 cwd 为宿主 `cacheDir`, 因此工程资源无从解析. 诊断只有 `BINDING_FAILED`. 契约哈希输入只覆盖方法签名与 Bundle 载荷类别.
- 测试: JVM 有 `ScriptResourceRepositoryTest` (16 项) 与预览请求 / 失败 / 边界编码的测试, `StardustClassAliasesTest` 锁定 `ViewAttributes` / `DynamicLayoutInflater` / `ResourceParser` 等的公开成员; 设备有 `UiPreviewServiceTest`; `DynamicLayoutInflater`, `ViewAttributes`, `AndroidResourceResolver`, `Dimensions`, `Gravities` 以及 `useAndroidLayout` / `layoutFile` / include / style / theme 没有测试.
- 文档: 文档站 `api/uiAttributes.md` 手写, 是 3-Block Compose 设计器属性目录的来源 (`generate_ui_catalog.py`); 第 90 行 "style 仅查找 AutoJs6 应用资源" 已落后于工程 `res/values` 样式支持. 更新日志没有 `layoutFile`, 工程 `res/`, include, style, theme 的条目.
- SDK: 本机 `E:\.android\sdk\platforms\android-23` - `android-37.0` 均有 `attrs.xml`, `android-37.0` 另有 `public-final.xml`; 宿主 minSdk 24, compileSdk 37.

---

## 3. 目标架构

### 3.1 属性元数据表 (A1)

- 生成器 `.python/generate_android_attrs.py --sdk <SDK 根> --aar <AAR 列表或 Gradle 缓存> [--check]`, 输出 `app/src/main/assets/ui/android-attrs.json` (紧凑 JSON; 体积在提交中记录, 受 APK 体积复核约束):
  - `formats`: 属性名 -> 格式集合 (`reference`, `color`, `dimension`, `float`, `integer`, `boolean`, `string`, `fraction`, `enum`, `flag`) 与 enum / flag 值表 (同名属性在不同 styleable 的值表相同, 按 SDK 去重).
  - `styleables`: styleable 名 -> 属性列表与起始 API (`TextView`, `View`, `LinearLayout_Layout`, `ViewGroup_MarginLayout`, `ConstraintLayout_Layout`, ...).
  - `classes`: 宿主可创建的 View 类 (简写标签的目标类, `extendedClassNameMap`, 常用 `android.widget` / `android.view`) -> 其类链对应的 styleable 列表; 布局参数 styleable 按父容器类选取.
- 宿主运行时以单例懒加载, JVM 测试可直接读取同一文件.

### 3.2 View 属性兜底 (A2)

- 入口: `BaseViewInflater.setAttr` 在手写处理器未命中时调用 `AndroidAttributeFallback.apply(view, ns, name, value, parent)`; 返回 "已应用 / 不支持 / 未知" 三种结果之一 (D3).
- 解析顺序: (1) 映射表 `android-attr-setters.json` (手写, 入库: 属性 -> 方法名与参数形态, 例如 `src` -> `setImageDrawable(Drawable)`, `textSize` -> `setTextSize(int unit, float)`, `lineSpacingMultiplier` -> 与 `lineSpacingExtra` 合并调用 `setLineSpacing`); (2) 约定 setter `set<Camel>` 按格式匹配参数类型 (`dimension` -> `int` 像素或 `float` 像素, `color` -> `int` 或 `ColorStateList`, `reference` -> `Drawable` / `ColorStateList` / `CharSequence` / `int` id, `enum` / `flag` -> `int`, `boolean`, `float`, `integer`, `string` -> `CharSequence` / `String`); 只调用公开方法, 方法在当前 API 不存在时视为不支持; (3) 结果缓存 (类, 属性) -> 调用器.
- 同时修正: 属性按书写顺序应用 (D5); `paddingVertical` / `paddingHorizontal` 的注册冲突; 不支持名单中已有公开 setter 的属性 (例如 `tooltipText` -> `setTooltipText`, API 26+) 改走兜底.

### 3.3 布局参数兜底 (A3)

- 通用: 父容器的 LayoutParams 类上与 `layout_<name>` 同名 (驼峰) 的公开字段或 setter, 按元数据格式转换后设置.
- ConstraintLayout: 约束属性 (`layout_constraint<Side>_to<Side>Of` 等) 的映射表, `@id/x` / `parent` 解析为 View id (同一次 inflate 内的 id 表), 偏移 / 比例 / 链样式 / guideline 百分比等数值字段.
- CoordinatorLayout: `layout_behavior` 以类名反射构造 Behavior (`(Context, AttributeSet)` 传 null), `layout_anchor` / `layout_anchorGravity` / `layout_insetEdge` / `layout_dodgeInsetEdges`; AppBarLayout `layout_scrollFlags`, CollapsingToolbarLayout `layout_collapseMode` / `layout_collapseParallaxMultiplier`.

### 3.4 不支持与未知的处理 (A4)

- 结果分类: 已应用; `IGNORED_NAMESPACE` (`tools:` 等); `UNSUPPORTED` (元数据中有, 但没有处理器也没有可调用的公开 API, 或当前 API 不提供); `UNKNOWN` (元数据中也没有, 多为拼写错误或第三方库属性).
- 运行时 (D7): Android XML (保留原 XML 的布局) 中的不支持 / 未知属性跳过而不抛异常, 每个 (类, 属性) 在脚本控制台警告一次; AutoJs6 简写 XML 的现有行为不变.
- 预览: 新钩子 `UiContentHost.onAttributeIgnored(attrName, outcome)` (仿照 `onBindingFailed`), 预览会话以新诊断码 `ATTRIBUTE_IGNORED` 回传 (属性名 + 元素序号 + 分类), 3-Block Compose 的问题列表据此显示.

### 3.5 覆盖矩阵与测试 (A5)

- JVM `AndroidAttributeCoverageTest`: 对 3.1 `classes` 中每个类的每个元数据属性, 以 `android.jar` 的反射信息判定 "手写 / 映射表 / 约定 setter / 布局参数 / 不支持", 生成 `docs/dev/android-xml-attributes-coverage.json` 快照, 覆盖率下降即失败.
- JVM 单测: 取值转换 (各格式, enum / flag 组合), setter 选择 (重载, 单位), 书写顺序, 约束 id 解析.
- 设备 `AndroidXmlAttributesDeviceTest`: 常用类 (View, TextView, Button, EditText, ImageView, LinearLayout, FrameLayout, RelativeLayout, ScrollView, ConstraintLayout, CoordinatorLayout, CardView, Switch, ProgressBar, SeekBar) 各一份原生 XML, 逐属性以 getter 或 LayoutParams 读回验证; 官方示例 "专注时光" 整体 inflate 无异常且关键属性生效; API 24 / 30 / 33 / 35 / 37 模拟器.

### 3.6 文档 (A6)

- 文档站新增页面 "Android XML 属性" (`api/uiAndroidAttributes.md`, 加入侧栏), 由宿主生成器 `--docs <AutoJs6-Documentation>` 从覆盖矩阵生成每类的 "已支持 / 不支持" 表与兜底规则说明, `--check` 检测漂移; `api/ui.md` 的 "布局转换模式" 与 `api/uiAttributes.md` 的过时说明随之修订. 3-Block Compose 的 `generate_ui_catalog.py` 增读该页, 生成原生命名空间目录 (插件 D65).
- 更新日志 (zh-Hans 定稿, 其它语言由维护者同步): Android XML 属性支持范围, 工程 `res/` 在预览中的解析.

### 3.7 资源限定符目录 (A7, D8)

- `ScriptResourceRepository` 读取 `res/<type>[-<qualifiers>]/`: 解析限定符 (语言 / 地区 `zh`, `zh-rCN`, `b+zh+Hans`; 夜间 `night` / `notnight`; 方向 `land` / `port`; 最小宽度 / 可用宽高 `sw<N>dp` / `w<N>dp` / `h<N>dp`; 密度 `ldpi` ... `xxxhdpi` / `nodpi` / `anydpi`; 平台版本 `v<N>`), 未识别的限定符目录忽略并记录; 每个资源按 Android 的择优算法 (先排除冲突, 再按限定符优先级逐项取最匹配) 在当前 `Configuration` 下选取.
- 配置来源: 正式运行取脚本 Activity 的配置; 预览取会话的配置 (设计器的夜间 / 语言设置与会话尺寸); 配置变化时清缓存.
- 现有 "只认无限定符目录" 的测试改为择优规则测试.

### 3.8 vector drawable (A8, D8)

- `AndroidDrawableInflater` 支持 `<vector>`: 解析 `android:width` / `height` / `viewportWidth` / `viewportHeight` / `tint` / `alpha` 与 `<group>` (旋转, 缩放, 平移) / `<path>` (`pathData`, 填充与描边颜色 / 透明度 / 宽度 / 线帽 / 线连接 / `fillType`, `trimPath*`) / `<clip-path>`, 以宿主自己的 `Drawable` 子类按 Path 绘制; path 数据按 SVG path 语法解析 (含相对命令与弧线); `@color` / `?attr` 颜色与 `<gradient>` (`aapt:attr` 内联) 按资源规则解析; 不支持的元素记录诊断.

### 3.9 预览的资源目录与工程目录 (R)

- 契约 v3 (D6) 的新键: `setLayout` 请求 `resourcesFd` (ParcelFileDescriptor, 工程 zip, 至少含 `res/`), `resourcesSha256`, `layoutPath` (布局文件在 zip 中的相对路径, 缺省视为 `res/layout/<匿名>.xml` 之外的脚本内布局), `androidLayout` (`true` / `false` / 缺省为自动, 对应 `ui.useAndroidLayout`); `runScript` 请求 `projectFd` / `projectSha256` (整个工程 zip, 脚本以解压目录为工作目录运行, 用于文件夹工程的交互预览); 信息 `resourcesSupported`, `supportedContractVersions`, `contractHashes`, `maxResourcesBytes`, `maxResourcesEntries`.
- 宿主: `UiPreviewArchive` 校验并解压 (大小 / 条目数上限, 拒绝绝对路径与 `..`, 只解普通文件), 按摘要缓存到会话目录 `cacheDir/ui-preview/<session>/<sha>/`, 会话释放时删除; `applyLayout` 在有资源时把会话运行时的工作目录与 `ui.useAndroidLayout` 设为请求值, 有 `layoutPath` 时以解压目录中的该文件作为源文件配置资源 (与 `ui.layoutFile` 相同), 使 `@drawable` / `@dimen` / `<include>` / style / theme 按正式运行的规则解析.
- 同一解压工具供插件路线图 D64 的工程 zip 入口 (运行 / 导入) 复用.

---

## 4. 范围与非目标

范围: 第 3 节全部; 宿主 JVM / 设备测试; 预览协议文档 `docs/dev/ui-preview-plugin-protocol-v1.md` 的 v3 小节; 文档站页面与更新日志; 契约 AAR 重建 (供 3-Block Compose 锁定).

非目标 (另立条目再议): 使用平台 `LayoutInflater` 或运行时编译二进制 XML; 主题属性 (`?attr` 之外的 `android:theme` 已支持, 不扩充主题 styleable 的逐项应用); 脚本 `view.attr()` 的兜底 (D9); 第三方库 (非宿主依赖) 的 `app:` 属性; `animated-vector` 与动画资源.

---

## 5. 阶段总览

| 阶段 | 内容 | 依赖 |
| --- | --- | --- |
| R1 | 契约 v3 常量, 身份宣告, 协议文档, `UiPreviewContractTest` | 无 |
| R2 | 宿主: zip 校验解压, 会话资源目录, `setLayout` / `runScript` 的 v3 处理, 能力宣告 | R1 |
| R3 | 测试: `UiPreviewServiceTest` 资源与工程用例, JVM 解压用例; 契约 AAR 重建 | R2 |
| A1 | 元数据生成器与表, JVM 读取 | 无 |
| A2 | View 属性兜底, 映射表, 书写顺序, 不支持名单修正 | A1 |
| A3 | 布局参数兜底 (通用 / ConstraintLayout / CoordinatorLayout / AppBarLayout) | A2 |
| A4 | 结果分类, 运行时警告, 预览诊断 `ATTRIBUTE_IGNORED` | A2, R1 |
| A5 | 覆盖矩阵, JVM 与设备测试 | A2 - A4 |
| A6 | 文档站页面, 生成器 `--docs`, 更新日志 | A5, A7, A8 |
| A7 | 资源限定符目录与择优 (D8) | 无 |
| A8 | vector drawable (D8) | 无 |

R 与 A 互不依赖 (A4 的诊断码除外), R 先做 (工作量小, 插件 P11.3 直接受益); A7 / A8 与 A1 - A5 互不依赖, 文档 A6 最后统一写.

---

## 6. 条目

### R1 契约 v3

- [x] `UiPreviewContract`: `VERSION` 3 与新 `HASH_INPUT` (v2 之后追加 Bundle 载荷类别 `pfd-zip` 与新键), `HASH_V2` 冻结; 新键常量与上限; `ERROR_ATTRIBUTE_IGNORED` 诊断码 (A4 使用); 身份宣告键. `UiPreviewContractTest`: 哈希, 键唯一, v1 / v2 前缀与冻结值不变.
  证据 (2026-10-16): `VERSION` = 3, `HASH` = `196e52cd...a44b` (输入在 v2 的方法与回调之后追加 `bundle:...-pfd`, `archive:zip-pfd-readonly` 与 setLayout / runScript 的新键名, 不加事务), `VERSION_V2` / `HASH_INPUT_V2` / `HASH_V2` (`81f7fe8d...`) 冻结; 新键 `supportedContractVersions`, `contractHashes`, `resourcesSupported`, 三个归档上限键, `resourcesFd` / `resourcesSha256` / `layoutPath` / `androidLayout`, `projectFd` / `projectSha256` / `scriptPath`; `ERROR_ARCHIVE_INVALID`, `ERROR_ATTRIBUTE_IGNORED` 与 `Diagnostic.OUTCOME` (`"o"`: namespace / unsupported / unknown); 上限 32 MiB / 解包 128 MiB / 4096 条目 / 路径 512 字符. `UiPreviewContractTest` 8 项通过 (v3 与 v2 的事务和回调完全相同, v1 / v2 哈希冻结值, 新错误码前缀, 上限, 键唯一). 宿主同一提交: `UiPreviewRequests.SUPPORTED_CONTRACT_VERSIONS` 接受 1 / 2 / 3; `getPreviewInfo()` 的 `contractVersion` / `contractHash` 改为冻结的 v2 值 (此前引用 `VERSION` / `HASH`, 升到 3 会使 1.7.0 插件失配), 新数组暂列 1 / 2 (R2 实现后加入 3); `UiPreviewRequestsTest` 10 项通过, `UiPreviewServiceTest` 的信息断言改为 v2 冻结值与数组 (设备运行见 R3), androidTest 编译通过.
- [x] `docs/dev/ui-preview-plugin-protocol-v1.md` 增加 v3 小节 (身份宣告, 资源 / 工程 zip 的格式与上限, 旧客户端行为).
  证据 (2026-10-16): 标题改为 V1 / V2 / V3, 状态段与 "Preview info" 说明三层身份, 新增 "Project resources and projects (V3)" 一节 (归档校验, setLayout / runScript 的新键与解析规则, 诊断, 旧宿主上的客户端降级).

### R2 宿主实现

- [x] `UiPreviewArchive` (校验, 解压, 缓存, 清理); `UiPreviewService.setLayout` / `runScript` 读取 v3 键并校验; `UiPreviewSession.applyLayout` 的资源配置与 `androidLayout`; 交互预览以工程目录为工作目录; `getPreviewInfo()` 宣告.
  证据 (2026-10-16): `UiPreviewArchive.receive` 在 Binder 线程把描述符内容复制到会话目录的临时文件 (32 MiB 上限, 边复制边算 SHA-256 并核对), `unpack` 逐条写普通文件 (名称经 `UiPreviewRequests.archivePath` 校验, 拒绝重复名, 条目数与解包字节边写边计), 先写 `<目标>.partial` 再改名, 任何拒绝都删除残余; `UiPreviewRequests.validateLayoutResources` / `validateProject` / `validateArchiveDigest` / `archivePath` (请求键用 `INVALID_REQUEST`, zip 条目用 `ARCHIVE_INVALID`). 会话: `receiveResources` 摘要不变时不读描述符而复用 `archives/resources-<n>`, 变化时解出新副本并删除旧的; `receiveProject` 每次运行一个新副本, `runScript` 在停止上一次运行后删除上一个副本; `applyLayout` 每次请求都设置 `ui.isAndroidLayout` 与引擎工作目录 (有资源时为解压目录, 否则恢复会话的缓存目录), 有 `layoutPath` 时把请求的 XML 写到解压副本中的该文件并以 `UI.layoutFile` 应用 (资源按源文件位置解析), 否则以 `UI.layout` 应用. `UiPreviewScriptRun` 以工程副本为工作目录, 并把 `scriptPath` 设为脚本源的 `overriddenFullPath`. 服务读取 `resourcesFd` / `projectFd` 后在任何路径上都关闭它们; 信息加入 v3 版本与哈希, `resourcesSupported` 与三个归档上限. 编译与既有预览单测通过; 新增测试与设备结果见 R3.

### R3 测试与 AAR

- [ ] JVM: 解压的上限, 路径越界, 摘要缓存. 设备 `UiPreviewServiceTest`: "专注时光" 资源 zip + `res/layout/activity_main.xml` 的布局成功且 `@drawable` / `@dimen` / `<include>` 生效 (读回背景与尺寸), 无资源时同一布局失败或缺省; 工程 zip 的 `runScript` 中 `ui.layoutFile` 相对路径可用; 旧客户端 (v2 请求) 行为不变. API 24 / 33 / 35.
- [ ] `:plugin-api:ui-preview-api` release AAR 重建, 记录摘要 (供 3-Block Compose 锁定).

### A1 元数据

- [ ] 生成器与 `android-attrs.json` (API 24 - 37 起始 API, 公开过滤, AndroidX / Material `app:` 属性); 体积记录; `--check`; JVM 读取与查询单测.

### A2 View 属性兜底

- [ ] `AndroidAttributeFallback` 与映射表, 取值转换, setter 选择与缓存; `resolveAttributes` 保持书写顺序; `paddingVertical` / `paddingHorizontal` 修正; 不支持名单中已有公开 API 的属性改走兜底. 保持 `StardustClassAliasesTest` 锁定的公开成员.

### A3 布局参数兜底

- [ ] 通用 LayoutParams 字段 / setter; ConstraintLayout 约束与数值字段; CoordinatorLayout behavior / anchor; AppBarLayout / CollapsingToolbarLayout 参数.

### A4 不支持与未知

- [ ] 结果分类贯通 `applyAttribute` 与运行时委托; Android XML 中跳过并警告一次 (按 Q1 拍板); `UiContentHost.onAttributeIgnored` 与预览诊断.

### A5 覆盖与测试

- [ ] `AndroidAttributeCoverageTest` 与覆盖快照; 取值 / setter / 顺序 / 约束单测; `AndroidXmlAttributesDeviceTest` 在 API 24 / 30 / 33 / 35 / 37 通过.

### A7 资源限定符目录

- [ ] 限定符解析与择优, 正式运行与预览的配置来源, 配置变化清缓存; `ScriptResourceRepositoryTest` 的 "只认无限定符" 用例改为择优用例 (语言, 夜间, 方向, 最小宽度, 版本, 密度, 冲突排除); 设备: 夜间 / 语言切换后同一布局取到对应资源.

### A8 vector drawable

- [ ] `<vector>` 解析与绘制 (group 变换, path 填充 / 描边 / trim, clip-path, 内联 gradient), path 数据解析器单测 (相对命令, 弧线, 隐式重复参数), 设备像素比对 (与同一 XML 经 aapt 编译后的平台 `VectorDrawable` 在 API 33 上逐像素差异不超过阈值).

### A6 文档

- [ ] 文档站 `api/uiAndroidAttributes.md` 与侧栏, `ui.md` / `uiAttributes.md` 修订, 生成器 `--docs` / `--check`; 更新日志 zh-Hans; 离线文档同步随宿主发布.

---

## 附录 A: 与 3-Block Compose 的衔接

- 插件 P11.3 使用 R 的 v3 键预览文件片段与原生布局, 在没有 `resourcesSupported` 的宿主上按 v2 发送 XML 文本并在问题列表说明资源无法解析.
- 插件的原生命名空间属性目录由 A6 的文档页生成; A4 的 `ATTRIBUTE_IGNORED` 诊断进入插件问题列表.
- 插件路线图: `D:\idea-projects\AutoJs6-Plugin-Three-Block-Compose\docs\dev\p11-designer-fidelity-roadmap.md` (P11.H1 = 本文 R, P11.H2 = 本文 A).

## 附录 B: 待决事项

Q1 - Q3 已于 2026-10-16 拍板 (D7 - D9): Q1 选 (a), Q2 选 (c), Q3 选 (a).

### Q1 (A4 前): Android XML 中不支持 / 未知属性的运行时行为

(a) 跳过, 每个 (类, 属性) 在脚本控制台警告一次 (建议); (b) 跳过且不提示; (c) 保持抛异常 (布局失败).

### Q2 (A1 前): 资源限定符目录与 vector drawable 是否纳入

(a) 都不纳入, 另立条目 (建议, 本路线图聚焦属性); (b) 纳入限定符目录 (`-night`, 语言, `-land`, `-v<N>`, 最小宽度), 按当前配置择优; (c) 两者都纳入 (vector 需自行解析 path 数据).

### Q3 (A2 前): 脚本 `view.attr(name, value)` 是否也使用兜底

(a) 只用于布局 (建议, 不改变 `view.attr` / `NativeView.has` 的既有语义); (b) 同时用于 `view.attr` (脚本可在运行时设置任意可兜底的属性).
