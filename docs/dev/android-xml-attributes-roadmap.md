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
| D5 | 属性按书写顺序应用 | `resolveAttributes` 改为保持 XML 顺序 (`LinkedHashMap`), 简写与分项 (margin / marginLeft, padding / paddingLeft) 的结果因此确定: 后写的覆盖先写的. 例外 (A5): 平台构造函数先读取且其 setter 会重置其它属性的, 按平台顺序 (ImageView 的 `adjustViewBounds` 先于 `scaleType`) |
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
  实施修正 (2026-10-16): 手写处理器要在真实 View 与脚本运行时上注册, JVM 无法得到, 因此改为设备测试 `AndroidAttributeCoverageDeviceTest`, 快照放在 `app/src/androidTest/assets/ui/android-attrs-coverage.json` (在最新平台 API 37 上生成), 由 `.python/android_attrs_coverage.py` 运行, 拉取与更新; A6 的文档生成器读同一快照.
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
| A9 | ConstraintLayout 辅助类与容器自身属性 (A5 覆盖矩阵发现) | A3 |

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

- [x] JVM: 解压的上限, 路径越界, 摘要缓存. 设备 `UiPreviewServiceTest`: "专注时光" 资源 zip + `res/layout/activity_main.xml` 的布局成功且 `@drawable` / `@dimen` / `<include>` 生效 (读回背景与尺寸), 无资源时同一布局失败或缺省; 工程 zip 的 `runScript` 中 `ui.layoutFile` 相对路径可用; 旧客户端 (v2 请求) 行为不变. API 24 / 33 / 35.
  证据 (2026-10-16): JVM `UiPreviewArchiveTest` 5 项 (文件与目录解出; `../`, 多级 `..`, 绝对路径, 反斜杠, 盘符, 空段, `.` 段与超长名称各自拒绝整个 zip 且不留目标与 `.partial`; 重复名; 条目数上限与按实写字节计的解包上限 (高压缩比条目), 恰好等于上限可通过; 已存在的目标不被覆盖), `UiPreviewRequestsTest` 新增 2 项 (资源键与 `layoutPath` 的组合与路径规则, 工程键与 `scriptPath`, zip 条目用 `ARCHIVE_INVALID`), 预览包 26 项全部通过. 设备 `UiPreviewServiceTest` 新增 3 项: (1) 信息宣告 v3 与 `resourcesSupported`; "专注时光" 的 `activity_main.xml` (根 `ScrollView`, `@drawable` 背景, `@dimen` 间距, 四个 `<include>`, 其中引用 `@string` / `@color`) 无资源时布局失败, 带资源 zip 与 `layoutPath` 时成功且边界含 `root`, 不带 `layoutPath` (以 zip 根为工作目录) 也成功; 同一摘要的两次请求只有一个解压副本, 去掉 `strings.xml` 的新 zip 换掉副本 (该布局因此失败), 临时与 `.partial` 文件不残留; 之后的 v2 请求回到会话自己的工作目录而再次失败; (2) 摘要不符与 `../` 条目的 zip 以 `ARCHIVE_INVALID` 拒绝且会话外无文件写出, `../` 的 `layoutPath` 与缺少 zip 的 `layoutPath` 以 `INVALID_REQUEST` 拒绝, 会话随后照常布局; (3) (API 33+) 工程 zip 的运行中 `ui.useAndroidLayout(); ui.layoutFile('res/layout/activity_main.xml')` 成功, `files.cwd()` 为 `archives/project-<n>`, 副本中有 `res/layout/view_timer.xml`; 随后的 v2 运行以 `replaced` 结束前一次并删除其副本, 新运行的工作目录不在 archives 下. 结果 (宿主 debug versionCode 5448): emulator-5580 (AVD_API_33) 14 项全部通过; emulator-5570 (API 24 x86) 14 项通过 (需要 API 33 的运行用例按假设跳过); AVD_API_35_16K 只读实例 (端口 5584, 2.5 GB 内存) 首次在开机后的内存紧张中宿主进程被回收 (无 Java / 原生崩溃记录, lowmemorykiller 同时杀了多个进程), 稍后复跑 14 项全部通过, 测试后关闭该实例.
- [x] `:plugin-api:ui-preview-api` release AAR 重建, 记录摘要 (供 3-Block Compose 锁定).
  证据 (2026-10-16): `ui-preview-api-release.aar` 20618 B, SHA-256 `4746f9f3e817e2c634e2005635373c9f864c7a33ca7f0703ca16133911dd8b94` (契约提交 `2da2d4119`), `--rerun-tasks` 重建字节一致; 由插件 P11.3 复制到 `libs/` 并更新锁文件.

### A1 元数据

- [x] 生成器与 `android-attrs.json` (API 24 - 37 起始 API, 公开过滤, AndroidX / Material `app:` 属性); 体积记录; `--check`; JVM 读取与查询单测.
  证据 (2026-10-16): `.python/generate_android_attrs.py --sdk <SDK> [--gradle-home <GRADLE_USER_HOME>] [--check]`: SDK `android-24` ... `android-36`, `android-37.0` 的 `attrs.xml` 逐级比对 (属性只在 `public.xml` / `public-final.xml` / `public-staging.xml` 公开时计入, 起始 API 为第一次以公开属性出现在该 styleable 的平台), 格式与枚举 / 标志值取自最新平台; `android.jar` 的 `android.widget` / `android.view` / `android.webkit` 类名决定保留哪些 styleable (`<Name>` 为类自身, `<Name>_Layout` / `<Name>_MarginLayout` 为其子视图的布局参数); AAR (appcompat 1.7.1, cardview 1.0.0, constraintlayout 2.2.1, coordinatorlayout 1.1.0, drawerlayout 1.1.1, recyclerview 1.4.0, swiperefreshlayout 1.1.0, material 1.13.0; 版本按 `appDebugRuntimeClasspath` 的解析结果) 的 `values.xml` 与 `classes.jar` 同理, 其中引用的 `android:` 属性沿用 SDK 定义; ConstraintLayout 写作 `format="flags"` 的属性按 `flag` 读. 输出 `app/src/main/assets/ui/android-attrs.json` (首版 126787 B 误收了 `Window`, `MenuItem`, `PopupWindow` 等非 View 类的 styleable; 随后生成器解析 `android.jar` 与各 AAR 的类文件取父类链, 只保留公开的 View 子类 (布局参数只保留 ViewGroup 子类), 现为 101024 B: 521 个 `android:` 属性, 670 个 `app:` 属性, 121 个 styleable, 121 个类键), `--check` 输出 `ANDROID_ATTRS_OK`. 运行时 `core/ui/inflater/AndroidAttributeTable` (Gson 解析, 资产懒加载一次): 按类链取成员 (近者优先, 结果按类缓存), 按父容器取布局参数成员, `find` / `findLayout` 先按给定前缀再回退 `android` / `app`. 测试: `.python/test_generate_android_attrs.py` 6 项 (定义与 styleable, 库属性引用 `android:` 与 `flags` 别名, 32 位标志, 类文件的父类与可见性, 父类链), JVM `AndroidAttributeTableTest` 4 项 (TextView 自身与继承属性及起始 API, gravity / visibility 取值, LinearLayout / RelativeLayout / ConstraintLayout / CoordinatorLayout 的布局参数, AppCompatTextView 的库属性与前缀回退) 通过.

### A2 View 属性兜底

- [x] `AndroidAttributeFallback` 与映射表, 取值转换, setter 选择与缓存; `resolveAttributes` 保持书写顺序; `paddingVertical` / `paddingHorizontal` 修正; 不支持名单中已有公开 API 的属性改走兜底. 保持 `StardustClassAliasesTest` 锁定的公开成员.
  证据 (2026-10-16): `DynamicLayoutInflater.applyAttributeValue` 成为两处属性应用点 (布局器自身与 ui 运行时的 `UIProxyLayoutInflaterDelegate`, 含自定义控件的 setter 回调) 的共同入口: 先由 `ViewInflater.setAttr` 处理, 返回 false (没有处理器) 或抛出新的 `UnsupportedAttributeException` (`Exceptions.unsupported` 现抛此子类, 即 `registerAttrUnsupported` 名单) 时交给 `AndroidAttributeFallback`; 名单内的属性兜底也设不了时照旧抛出 (A4 再改), 非 `android` / `app` / 无前缀的命名空间返回 `IGNORED_NAMESPACE`; 结果类型 `AttributeOutcome`. 兜底: 属性表按类链查成员, 起始 API 高于本机为不支持; `AndroidAttributeSetters` (Kotlin, 取代原计划的 JSON 映射表: 规则需要代码) 给出依次尝试的方法名 (显式表, `set<名>`, `*Tint` -> `set*TintList`, `set<名>Drawable`, `min*` -> `setMinimum*`, `is*` -> `set*`, `set<名>Enabled`; 比较忽略大小写, 因此 `scrollbarStyle` 找到 `setScrollBarStyle`) 与特例 (行距, 阴影, 自动字号区间, `maxLength`, `drawableStart` / `drawableEnd`, `scrollbars`, `requiresFadingEdge`); 候选只取公开单参实例方法, 按属性格式的参数类型偏好排序 (颜色: ColorStateList > int > Drawable; 引用: Drawable > ColorStateList > 文本 > id; 尺寸: float > int; 枚举: Java 枚举 > int), 按类与属性缓存; 取值转换复用 `Dimensions` / 资源解析器的颜色, 颜色状态列表, Drawable 与 `Ids`, 枚举 / 标志名按表, Java 枚举按 `centerCrop` -> `CENTER_CROP` 规则, 分数 `50%` -> 0.5; 某个候选转换失败时试下一个, 全部失败按布局错误 (与手写处理器的坏值一致). 顺序 (D5): 原始属性表与 `resolveAttributes` 都保持文档顺序 (LinkedHashMap). `paddingVertical` / `paddingHorizontal` 从不支持名单移出 (此前先注册后被覆盖, 设值即抛异常), `paddingVertical` 的百分比改按高度. 测试: JVM `AndroidAttributeFallbackTest` 3 项 (命名规则, 格式到参数类型的偏好与枚举名映射, 在 android.jar 平台类上找到的 setter: letterSpacing, backgroundTint, scaleType, nextFocusDown, scrollbarStyle, minHeight, CheckBox button 与 TextView textColorHint 的重载选择, LinearLayout divider, tooltipText); 设备 `AndroidAttributeFallbackDeviceTest` (原生布局文件经 `ui.layoutFile`: breakStrategy, nextFocusDown, labelFor, nestedScrollingEnabled, dividerPadding, touchscreenBlocksFocus, transitionGroup 与 API 26 的 tooltipText / focusedByDefault / justificationMode / autoSizeTextType, API 28 的 screenReaderFocusable / accessibilityHeading / outlineSpotShadowColor / fallbackLineSpacing 均按读回值生效, `padding` 后的 `paddingHorizontal` / `paddingVertical` 覆盖对应两边, 未知属性与 `tools:` 不影响布局) 在 emulator-5580 (API 33) 与 emulator-5570 (API 24) 通过; 回归: JVM `org.autojs.autojs.core.ui.*` 与 `StardustClassAliasesTest`, 设备 `UiPreviewServiceTest` (API 24 / 33) 与 `JsListViewDataBindingDeviceTest` 通过. 覆盖率数字见 A5.

### A3 布局参数兜底

- [x] 通用 LayoutParams 字段 / setter; ConstraintLayout 约束与数值字段; CoordinatorLayout behavior / anchor; AppBarLayout / CollapsingToolbarLayout 参数.
  证据 (2026-10-16): `AndroidAttributeFallback.apply` 在视图自身找不到的 `layout_*` 属性上转查父容器的布局参数成员 (`AttributeTable.findLayout`), `layout_editor_*` 视为设计期属性 (`IGNORED_NAMESPACE`); `AndroidLayoutParamRules.property` 把 ConstraintLayout 的属性名按模式映射到公开字段 (`layout_constraint<边>_to<边>Of` -> `<边>To<边>`, `_bias` / `_weight` / `_chainStyle` -> `horizontalBias` 等, `layout_constraintWidth_percent` -> `matchConstraintPercentWidth`, `Guide_*`, `goneMargin*`, circle, dimensionRatio, constrained*), 少数显式名称 (`layout_anchor` -> `anchorId`, `layout_alignWithParentIfMissing` -> `alignWithParent`, `layout_collapseParallaxMultiplier` -> `parallaxMultiplier`), 其余去掉 `layout_` 前缀; 依次尝试 params 的公开单参 setter (同 A2 的命名规则, 另加 `set<名>Id`) 与公开非 final 字段, 取值转换同 A2 (`parent` 按枚举为 0, `@+id/x` 为 id); 特例: `layout_behavior` 按类名实例化 Behavior (`(Context, AttributeSet)` 传 null, 或无参; `.Name` 相对应用包名), ConstraintLayout 2.1 的 `layout_constraintWidth` / `Height` 简写 (`wrap_content_constrained` 等), GridLayout 的 row / column / span / weight (按每个视图已写的值以 `GridLayout.spec` 重建). 设置后若 params 有 `validate()` 则调用, 再赋回视图触发重排. 测试: JVM `AndroidLayoutParamRulesTest` 2 项 (名称映射; 属性表为 ConstraintLayout / CoordinatorLayout / AppBarLayout / CollapsingToolbarLayout 列出的每个 `layout_*` 属性都对应 LayoutParams 的公开字段或 setter, 或为特例 / 设计期属性, 仅 Flow / Layer 等辅助类属性除外); 设备 `AndroidAttributeFallbackDeviceTest.layoutParamsOfConstraintCoordinatorAndGridLayoutsAreSet` (ConstraintLayout 中 c 位于 a 的右下, `goneMarginStart` 为 7 px, 百分比宽度 0.5 + bias 1 的 d 宽 150 px 贴右且贴底; AppBarLayout 子视图 `scroll|enterAlways` = 0x5; NestedScrollView 的 `@string/appbar_scrolling_view_behavior` 解析为 `AppBarLayout$ScrollingViewBehavior`; anchor 指向 AppBarLayout, `anchorGravity` = bottom|end; GridLayout 第 1 行第 2 列的单元格位于 (60, 30)) 在 emulator-5580 (API 33) 与 emulator-5570 (API 24) 通过.

### A4 不支持与未知

- [x] 结果分类贯通 `applyAttribute` 与运行时委托; Android XML 中跳过并警告一次 (按 Q1 拍板); `UiContentHost.onAttributeIgnored` 与预览诊断.
  证据 (2026-10-16): `DynamicLayoutInflater.convertXml` 在委托保留原 XML 时 (`ui.useAndroidLayout`, 或带命名空间前缀的属性) 给 inflate 上下文打上原生 XML 标记; `applyAttributeValue` 多一个上下文参数 (布局器与 `UI` 运行时委托两处调用同步更新), 处理器与兜底都没设上的属性: 原生 XML 中 `UNSUPPORTED` (含处理器声明不支持且兜底也设不了的, 此前抛异常使整个布局失败) 与 `UNKNOWN` 都跳过, 脚本控制台按 (类, 属性) 每个运行时警告一次 (新字符串 `ui_android_attribute_unknown` / `ui_android_attribute_unsupported`, 11 个语言目录), 并经新钩子 `UiContentHost.onAttributeIgnored(attrName, outcome, message)` 告知承载方; AutoJs6 XML 照旧 (未知属性静默忽略, 声明不支持的属性使布局失败). `tools:` 等其他命名空间为 `IGNORED_NAMESPACE`, 不警告也不产生诊断 (契约的 `namespace` 分类值保留未用). 预览会话在应用布局期间把钩子转为 `ATTRIBUTE_IGNORED` 诊断 (元素序号取当前元素, 属性名带前缀, 新字段 `o` 为 `unsupported` / `unknown`), 布局照常完成. 测试: JVM `UiPreviewBoundsTest` (诊断编码含 `o`); 设备 `UiPreviewServiceTest.ignoredAttributesOfNativeXmlAreDiagnosedWithoutStoppingTheFrame` (原生 XML 中 `android:onClick` 为 unsupported, `android:noSuchAttribute` 为 unknown, 都在元素 1, 布局为 applied; AutoJs6 XML 的未知属性无诊断, `onClick` 仍为 failed), `AndroidAttributeFallbackDeviceTest.unsupportedAndUnknownAttributesOfNativeXmlAreSkippedWithOneWarning` (两个带 onClick 与未知属性的 TextView 都创建成功) 在 emulator-5580 (API 33) 与 emulator-5570 (API 24) 通过; 回归: `UiPreviewServiceTest` 全部 15 项, `JsListViewDataBindingDeviceTest`, JVM `core.ui.preview` / `core.ui.inflater` 51 项通过. 顺带: `UiPreviewServiceTest` 的边界等待跳过元素数为 0 的报告 (嵌入会话首次遍历时报告的空层级在慢设备上可能晚于测试布局到达, 曾使 `embeddedSessionReturnsASurfacePackageAndBounds` 在 API 33 连续失败).

### A5 覆盖与测试

- [x] `AndroidAttributeCoverageTest` 与覆盖快照; 取值 / setter / 顺序 / 约束单测; `AndroidXmlAttributesDeviceTest` 在 API 24 / 30 / 33 / 35 / 37 通过.
  证据 (2026-10-16): 覆盖矩阵为设备测试 `AndroidAttributeCoverageDeviceTest` (原因见 3.5 的实施修正): 在 ui 脚本的运行时上, 按 `DynamicLayoutInflater.applyAttributeValue` 的同一判定把属性表中每个类自身 styleable 的属性归入 `handler` (手写处理器, 含 inflater 自身接受的名字与创建期读取的属性), `setter` (A2 兜底能找到公开方法或特例), `unsupported`, `api` (高于本机), 每个容器的子视图布局属性归入 `handler` / `params` (A3) / `design` / `unsupported`; 抽象类取各个具体子类中最好的一类; 每个 View 的创建与判定各占一个主线程任务 (一次做完会让脚本窗口超过 5 s 无响应, API 30 上被系统判为 ANR). 新增只读查询: `AndroidAttributeTable.classNames` / `ownMembersOf`, `ViewAttributes.isDeclaredUnsupported`, `AndroidAttributeFallback.layoutMembersOf`. 快照 `app/src/androidTest/assets/ui/android-attrs-coverage.json` (API 37 生成, 54670 B); 比较规则: 快照 API 上已支持的属性不得变为不支持且每个类仍能创建, 任意 API 上 `handler` 只能变为 `api`. 脚本 `.python/android_attrs_coverage.py --serial <s> [--install] [--update]` 只输出通过 / 失败, API, 各类计数与相对快照的变化. 第一次矩阵 (API 37): View 属性 1073 个 (98 个类可创建) 中 handler 313, setter 429, unsupported 331; 布局属性 179 个中 handler 63, params 70, design 2, unsupported 44; 11 个类无法创建. 按矩阵补上的缺口: (1) Material 组件在 ui 脚本的 AppCompat 主题下创建即抛 "requires Theme.MaterialComponents" (MaterialButton, MaterialCardView, Chip, Slider, SearchBar 等 10 个类): `createViewForName` 对 `com.google.android.material.*` 在主题缺 `isMaterialTheme` 时以 Material 3 主题补齐缺少的属性 (`applyStyle(force = false)`, 已有属性不变); (2) RelativeLayout 的 `layout_toStartOf` / `layout_toEndOf` / `layout_alignBaseline` 补为手写处理器 (AutoJs6 XML 同样受益); (3) `android:logo` / `android:theme` 的格式只在 SDK 的 `attrs_manifest.xml` 中声明, 生成器补读 (`merge_missing_formats`, 属性表 101169 B, 523 个 `android:` 属性), 平台 Toolbar 的 logo 因此找到 setLogo; (4) AppCompat 别名 `app:srcCompat` / `app:drawable*Compat` 按对应平台属性应用; (5) 显式名称 (completionThreshold, tabStrip*, tabIndicator, tabInlineLabel, tabUnboundedRipple, SwipeRefreshLayout 进度背景色, titleMaxLines, titleTextEllipsize, helperTextTextColor, FloatingActionButton 的 pressed / hoveredFocused TranslationZ) 与命名规则 `<x>Color<状态>` -> `set<X><状态>TintList`, `<x>Radius<状态>` -> `set<X><状态>Radius` (Material Slider 的 track / tick / thumb / halo); (6) 特例: layerType, outlineProvider, autofillHints, pointerIcon, fadingEdge, digits, textFontWeight, textLocale, ListView 的 entries (工程或编译的字符串数组), TableLayout 的 stretch / shrink / collapseColumns, Toolbar 的 contentInset* / titleMargin(s) / title / subtitleTextAppearance, switchTextAppearance, CollapsingToolbarLayout 的 expandedTitleMargin, TextInputLayout 的 boxCornerRadius*, 进度指示器的 indicatorColor, Material 的 shapeAppearance(Overlay); (7) 创建期读取的属性 (`style`, `android:theme`, `class`, Spinner / DatePicker / TimePicker 的模式) 视为已应用. 矩阵与设备读回同时发现三个缺陷并修复: A4 把 `style` / `android:theme` 与样式, 主题叠加层带入的属性也报为被忽略 (改为只报告元素上书写的属性, 像 Android 一样静默忽略样式与主题中视图不接受的属性); `scrollbars` / `requiresFadingEdge` 处理器只会开启不会关闭 (`none` 无效, 改为取值即开启的方向); 按书写顺序时 `adjustViewBounds` 写在 `scaleType` 之后会把缩放类型重置为 fitCenter (改为 ImageView 的 `adjustViewBounds` 先应用, 同平台构造函数, D5 例外). 现矩阵 (API 37): View 属性 1278 个 (108 个类) 中 handler 333, setter 622, unsupported 323 (可设置 74.7%, 余下多为无公开 API 的属性, 如 TextView 的 textEdit* 布局, DatePicker / TimePicker 的配色, SearchView 的图标); 布局属性 handler 66, params 70, design 2, unsupported 41 (其中 40 个为 ConstraintLayout 写在 `_Layout` styleable 中的容器自身与辅助类属性, 见 A9); 只有 SlidingDrawer 无法创建 (必须有 handle 子视图). 各 API 的计数: API 24 handler 329 / setter 566 / api 61, API 30 333 / 606 / 30, API 33 333 / 614 / 14, API 35 333 / 621 / 1; API 24 相对快照的 14 处变化都是 API 29 起才公开的 setter (滚动条 Drawable, 文本光标与选择柄, ProgressBar 最大尺寸等). 测试: 设备 `AndroidXmlAttributesDeviceTest` (一份原生布局含 View, TextView, Button, EditText, ImageView, FrameLayout, RelativeLayout, ScrollView, TableLayout, ListView, Switch, ProgressBar, SeekBar, CardView, 平台 Toolbar, MaterialButton, Slider, 逐项以 getter / 布局参数读回, 含上述特例, `app:srcCompat`, 工程字符串数组与 Material 组件), `AndroidAttributeCoverageDeviceTest`, `UiPreviewServiceTest` (A4 用例加 `style` / `android:theme` 无诊断; "专注时光" 示例带资源预览时诊断为空), `AndroidAttributeFallbackDeviceTest`, `JsListViewDataBindingDeviceTest` 在 emulator-5570 (API 24), 5590 (API 30, 只读实例), 5580 (API 33), 5584 (API 35, 只读实例), 5592 (API 37, 只读实例) 全部通过; JVM `AndroidAttributeFallbackTest` 新增 2 项 (缺口的名称, 规则, 格式与别名; 属性表的类名与自身成员), `org.autojs.autojs.core.ui.*` 与 `StardustClassAliasesTest` 共 74 项通过; 生成器测试 7 项 (新增 `merge_missing_formats`) 通过, `--check` 输出 `ANDROID_ATTRS_OK`. "专注时光" 示例整体预览由 `UiPreviewServiceTest` 承担 (原计划单列设备用例).

### A9 ConstraintLayout 辅助类与容器自身属性

- [ ] ConstraintLayout 把容器自身属性 (`android:maxWidth` / `maxHeight`, `constraintSet`, `layout_optimizationLevel`, `layoutDescription`) 与辅助类属性 (Barrier 的 `barrierDirection` / `barrierMargin` / `barrierAllowsGoneWidgets`, ConstraintHelper 的 `constraint_referenced_ids`, Flow 的 `flow_*`, CircularFlow) 都写在 `ConstraintLayout_Layout` 中, 属性表现把它们当作子视图布局参数, 因而在 ConstraintLayout / Barrier / Group / Flow 元素上为未知. 生成器为 ConstraintLayout 与 ConstraintHelper 子类另建只含非子视图属性的 styleable, 兜底补规则 (`flow_` 前缀, Barrier 的 setType / setMargin / setAllowsGoneWidgets, `constraint_referenced_ids` -> `setReferencedIds`); 设备读回 Barrier / Group / Flow.

### A7 资源限定符目录

- [x] 限定符解析与择优, 正式运行与预览的配置来源, 配置变化清缓存; `ScriptResourceRepositoryTest` 的 "只认无限定符" 用例改为择优用例 (语言, 夜间, 方向, 最小宽度, 版本, 密度, 冲突排除); 设备: 夜间 / 语言切换后同一布局取到对应资源.
  证据 (2026-10-16): 新文件 `core/ui/inflater/ResourceQualifiers.kt`: `ResourceConfiguration` (语言列表, 布局方向, 最小 / 可用宽高 dp, 横竖屏, 夜间, 密度, API; `of(context)` 取自上下文的 `Configuration`; `DEFAULT` 只匹配无限定符目录) 与 `ResourceQualifiers` (`parse`: 语言 / 地区 `zh`, `zh-rCN`, `b+zh+Hant+TW`, `ldrtl` / `ldltr`, `sw<N>dp` / `w<N>dp` / `h<N>dp`, `port` / `land`, `night` / `notnight`, `ldpi` ... `xxxhdpi`, `tvdpi`, `nodpi`, `anydpi`, `<N>dpi`, `v<N>`; 其它限定符或重复限定符的目录返回 null; `contradicts` 排除冲突目录; `best` 按 Android 优先级 (语言, 布局方向, 最小宽度, 宽度, 高度, 方向, 夜间, 密度, 版本) 逐项只保留匹配最好的目录, 地区匹配优于只匹配语言, 先出现的配置语言优先, 宽度与版本取不超过配置的最大值, 密度不排除目录而按 `ResTable_config::isBetterThan` 取最接近且优先缩小, `anydpi` 最优, 无限定符按 mdpi; 中文无 script 时按地区推断 Hans / Hant). `ScriptResourceRepository` 读取全部可识别的 `<type>[-<限定符>]` 目录 (按目录名排序), 每个值 / 样式 / 文件在当前配置下取最合适目录中的定义 (同一目录内后定义的覆盖先定义的, 与原行为一致); 不支持的目录忽略并列在 `ignoredDirectories`. 配置来源: `DynamicLayoutInflater` 每次 inflate 配置资源时取布局器上下文的配置 (脚本运行时为脚本 Activity), 再经新钩子 `UiContentHost.resourceConfiguration` 交承载方调整; 预览会话据当前会话尺寸 (按会话密度换算为 dp) 设宽高, 最小宽度与横竖屏, 夜间 / 语言 / 密度本就来自会话上下文; 每次 inflate 都重新配置, 因此夜间, 语言或尺寸变化后的下一次布局即按新配置选取. 测试: JVM `ResourceQualifiersTest` 4 项 (解析与不支持的限定符, 冲突排除含中文 script 推断, 优先级: 语言先于夜间, 地区先于语言, 配置语言顺序, 宽度, 夜间, 版本; 密度: 精确, 优先缩小, 远高密度不如略低密度, anydpi, 无限定符按 mdpi), `ScriptResourceRepositoryTest` 原 "只用基础目录" 用例改为默认配置用例并新增 `qualifiedDirectoriesFollowTheConfiguration` (字符串, 颜色引用, 尺寸, 样式, 布局, 密度与夜间 Drawable 随配置切换, 未覆盖的值保留, `values-large` 被忽略), JVM `core.ui.*` 与 `StardustClassAliasesTest` 共 79 项通过; 设备 `UiPreviewServiceTest.qualifiedResourcesFollowTheSessionNightModeLocaleAndSize` (同一布局的 `@dimen/box` 在日间 / 夜间 / fr-FR 夜间会话中分别取 values / values-night / values-fr, 会话改为 400 px 宽 (160 dpi) 后取 values-w300dp) 与 A5 的设备测试组在 emulator-5570 (API 24), 5590 (API 30), 5580 (API 33), 5584 (API 35), 5592 (API 37) 全部通过.

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
