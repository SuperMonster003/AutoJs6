# AutoJs6 智能颜色库 (Intelligent Color Library) Roadmap

本文是主题色 "智能颜色库" 及其依赖的 "自建颜色库" 的可执行状态表.
以 2026-10-06 的本地代码快照 (`master@3089662e35`, `VERSION_NAME=6.8.0`, `VERSION_BUILD=5319`) 为起点,
每个条目均可独立 Check 并落地, 后续会话按阶段逐步推进.

状态: 2026-10-07 P0~P7 全部完成; 落地后的架构说明见 [intelligent-color-library-architecture.md](intelligent-color-library-architecture.md).

设计输入有两份: 维护者约两年前的草案 (触发器 / 组 / 背景色对话框, 摘要与映射见附录 F), 以及 2026-10-06 与维护者的四轮问答结论 (已固化为第 1 节的固定决策).

使用方式:

1. 每次会话开始时, 从 "阶段总览" 选取一个或多个未完成条目, 优先级按阶段顺序; 同一阶段内按编号顺序, 除非条目注明可并行.
2. 条目完成后勾选 `[x]`, 并在条目后追加证据 (提交 hash / 测试类名 / 设备型号与 API level), 证据等级见附录 D.
3. 涉及字符串资源的条目必须同步 11 个 `strings.xml`; 涉及 feature / fix / improvement 的提交必须同步 `.changelog/lang_*.json` 并重新生成 markdown (D16). 阶段不绑定版本号, 日志写入落地时的开发版本.
4. 附录 A 的 "待决事项" 在进入对应阶段前由维护者拍板, 拍板结果回填到 "固定决策".

---

## 1. 固定决策与硬约束

| 编号 | 决策 | 含义 |
| --- | --- | --- |
| D1 | 自建库与智能库共用一套数据模型 | 颜色库分三类: 预设 (代码内置, 只读), 自建 (来源为 `CREATED` 新建 / `IMPORTED` 导入 / `CLONED` 克隆), 智能 (自建库的一种, 内容为 "组", 由状态源与换组时机驱动). 预设智能库 (Material 日夜配对 / 背景色 / 跟随壁纸 / 四季与节日) 只读, 克隆后可改. 普通自建库的新建 / 导入 / 克隆与智能库在本 Roadmap 内一并落地, 智能库建立在自建库的存储与编辑能力之上. |
| D2 | 产出物为单色, 派生色可选 | 每组每个状态必填 primary, 可选 accent / dark. 全局设置 "派生配色" 取值为 关 (默认, accent = dark = primary, 与现状逐像素一致) 或 自动 (由 `ThemeColorDerivation` 推导). 组内显式填写的 accent / dark 在两种取值下都优先于全局结果. 不引入完整配色方案 (secondary / tertiary / surface 等). |
| D3 | 触发模型拆分为 "状态源" 与 "换组时机" | 智能库 = 1 个状态源 (决定组内取哪个颜色, "无" 视为 1 个状态) + 0~n 个换组时机 (状态变化 / 应用启动 / 时间间隔 / 每日定时 / 脚本, 决定何时换组) + 切换方式 (顺序 / 随机) + 0~n 个组 (可单独启用或禁用) + 0~n 个日期覆盖层. 旧思路的三种触发器均可由默认组合表达 (见附录 F). |
| D4 | 条件组合 = 单状态源 + 日期覆盖层 | 不支持多状态源笛卡尔积或通用规则引擎. 覆盖层有序, 自上而下第一个命中者生效; 命中期间改用其指定组且换组时机暂停, 离开后回到进入前的组 (不推进). 禁用组不参与轮换, 但仍可被覆盖层引用. 更复杂的组合交给 "脚本" 状态源与 `colors.theme` API (P6). |
| D5 | 触发器只在进程存活期间评估 | 不使用 AlarmManager / WorkManager / 前台服务唤醒进程. 进程存活 (含后台运行脚本 / 悬浮窗) 时监听状态变化并按边界对齐计时; 冷启动与回到前台时补算, 进程死亡期间到期的换组只补一次, 不按错过次数连续推进. 设备状态类监听仅在激活库用到时注册, 停用即注销. |
| D6 | 主题色页面期间暂停 (沿用旧思路) | 任一主题色相关页面 (颜色库页 / 条目页 / 旧布局 / 智能库页 / 智能库编辑器) 存活期间引擎暂停 (引用计数), 不应用任何智能结果, 期间的事件合并记录; 全部页面销毁后评估一次. 编辑器内的预览只渲染色块, 不改动全局主题色. |
| D7 | 色彩算法纯 Kotlin 自实现 | 包 `org.autojs.autojs.theme.color` 只依赖 Kotlin 标准库与 ARGB `Int`, 可 JVM 单测. 工作空间为 OKLab / OKLCH (Ottosson 2020); 色差为 OKLab 欧氏距离 (下称 dE_ok); 对比度沿用 WCAG 2.x 相对亮度 (与 `ColorUtils.luminance` 一致); 色域映射保持 L 与 h, 二分降低 C. 不引入 `androidx.palette`, 不调用 Material Components 中 `@RestrictTo` 的 `com.google.android.material.color.utilities`. |
| D8 | 持久化沿用 `color-history-database.db` | `ColorHistoryDatabase` version 1 -> 2, 显式 `Migration`, 只增不减 (不改动 `color_history` / `palette_history` 既有列), `exportSchema = true` (KSP 的 `room.schemaLocation` 已在 `app/build.gradle.kts:467` 配置). 未注册的占位实体 (`created_library` / `created_color` / `preset_color`) 重新设计, 无兼容负担. 智能库配置 (状态源 / 换组时机 / 切换方式 / 覆盖层) 以带版本号的 JSON 存入 `config_json` 列以减少迁移; 组与状态颜色用表存储以便搜索与排序. |
| D9 | 选中状态沿用现有键, ID 分段 | 继续使用 `SELECTED_COLOR_LIBRARY_ID` / `SELECTED_COLOR_LIBRARY_ITEM_ID` 及 legacy 键. ID 分段 (`ColorLibraryIds`): 预设库 `0x01`~`0xFF` (现有值不变), 预设智能库 `0x100`~`0x1FF`, 自建普通库 `0x0100_0000 + rowId`, 自建智能库 `0x0200_0000 + rowId` (rowId 小于 `0x0100_0000`). 自建库按类型分段, 使保存的选中状态无需查询数据库即可区分普通库与智能库 (2026-10-06 P0.4 由 "自建库 `0x10000 + rowId`" 修订). 智能库激活时 item id 为起始组 id. 激活库的配置与启用组颜色另存一份紧凑快照到 Pref, 冷启动评估只读快照, 不在主线程查询 Room. |
| D10 | 主题色修改统一经协调器并区分来源 | 新增 `ThemeColorCoordinator.apply(themeColor, source)`, source 取 `USER` / `INTELLIGENT` / `SCRIPT` / `RESTORE`; 现有 4 处 `ThemeColorManager.setThemeColor` 调用全部改经协调器. `USER` 与 `SCRIPT` 的直接设色停用当前智能库 (显式选择优先); `INTELLIGENT` 不写颜色历史且不计入使用次数, 避免轮换刷屏历史与污染推荐. |
| D11 | 脚本直接生效 + 限频 + 来源记录 | 不设权限门槛. 500 ms 窗口内的多次脚本修改合并为最后一次; 历史条目记录来源 "脚本" 与脚本名称; 最近一次修改来自脚本时, 主题色页面提供 "撤销脚本修改", 一键恢复到修改前的选择 (含智能库激活状态). |
| D12 | 推荐与学习纯本地 | 不联网, 不上传. 信号: 使用频次 + 近因衰减, 收藏 / 置顶, dE_ok 相似色聚类去重. 不使用 "与壁纸 / 当前色协调度" 信号. |
| D13 | 旧布局不承载新库类型 | 旧布局 (`ColorSelectActivity` / `ColorSettingRecyclerView`) 不显示自建库与智能库. 智能库激活时旧布局的标题摘要显示 "智能: 库名"; 在旧布局中选择任一颜色视为 `USER` 选择并停用智能库. 自建库颜色被选中后, 旧布局按 "自定义颜色" 槽位呈现 (与 `savePrefsForLegacyPalette` 相同). |
| D14 | 交换格式仅 AutoJs6 JSON | `format = "autojs6.color-library"`, `schemaVersion = 1`, 一个文件可含多个库 (草案见附录 B). 导入总是新建库 (不覆盖), 重名自动加序号; 高于支持版本的 schema 拒绝导入并提示; 未知字段忽略. 预设库可导出其快照, 导入后为普通自建库. 不支持 .gpl / .ase 等通用调色板, 代码片段与分享链接 / 二维码. |
| D15 | 可测逻辑与 Android 类型解耦 | JVM 单测环境只有 JUnit4 (无 Robolectric). 色彩算法, 引擎决策 (`IntelligentColorPlanner`), 计时 (`IntervalSchedule`), 配置校验, 选中 ID 编解码, JSON 编解码, 节日 / 农历 / 日出日落计算, 推荐评分与聚类均为纯对象; Android 胶水层只做订阅, 调度与持久化. |
| D16 | 资源, 日志与关联项目按仓库约定 | 字符串遵循 `AGENTS.md` (11 个 `strings.xml`, `values` 与 `values-en` 一致, 按 name 升序, 省略号 `...` 加 `tools:ignore="TypographyEllipsis"`, 不可翻译项入 `strings_donottranslate.xml`, plurals 入 `plurals.xml`); 用户可见变化同步 `.changelog/lang_*.json` 并重新生成; `colors` 模块 API 变更同步文档项目与 d.ts 声明项目 (P6.4). |
| D17 | 脚本 API 命名空间为 `colors.theme.*` (Q1, 2026-10-07 拍板) | 与既有 `colors.themeColor` 同处 `colors` 模块, 不新增全局名, `autojs.themeColor` 保持只读. 算法函数名在 P6.1 开工前与 d.ts 命名惯例一并确认. |
| D18 | `oklab` 距离算法同步到 `images` 找色, 默认算法不变 (Q2, 2026-10-07 拍板) | `colors.distance` 与 `images` 找色共用 `ColorDetector`, 新增算法对两者都可用; 找色热路径须先做性能对比, 不达标则只在 `colors.distance` 中提供, 并在 P6.1 证据中记录对比结果. |
| D19 | 时间间隔下限 5 秒 (Q3, 2026-10-07 拍板) | `IntervalSchedule.MIN_PERIOD_MILLIS = 5000`; 编辑器在 "秒" 单位下最小值为 5, 上限 9999, 输入提示显示范围; 配置解码 (含导入) 时钳制到同一范围; 低于 1 分钟时编辑器提示观感与耗电. 秒级主要用于演示与调试. |
| D20 | 地理信息由时区推断, 可手动覆盖 (Q4, 2026-10-07 拍板) | 日出日落与季节半球不申请定位权限: 内置 IANA 时区 -> 代表城市坐标表推断经纬度, 允许在设置中手动输入经纬度覆盖; 未知时区退回按 UTC 偏移估算经度, 纬度取北半球中纬度并提示可手动设置. |
| D21 | 首批节日与地区判定 (Q5, 2026-10-07 拍板) | 公历通用节日 (元旦 / 情人节 / 万圣节 / 圣诞节) + 中国农历节日 (春节 / 元宵 / 端午 / 七夕 / 中秋 / 重阳) + 少量地区节日; 按系统地区默认启用, 用户可增减. |
| D22 | inrt 中 `colors.theme` 的行为 (Q6, 2026-10-07 拍板) | 算法函数照常可用; 主题色控制只作用于 inrt 自身界面且不持久化; 用户颜色库不随包携带. |
| D23 | 旧布局长期保留并按需兼容 (Q7, 2026-10-07 拍板) | 不评估移除旧布局 (`ColorSelectActivity` / `ColorSettingRecyclerView`). 沿用 D13: 新库类型不在旧布局中显示, 但每个阶段落地时须确认旧布局下的摘要, 选中高亮与选色行为正确 (例如智能库激活时旧布局不高亮任何项, 摘要显示库名; 自建库颜色按 "自定义颜色" 槽位呈现), 并在该阶段证据中记录. |
| D24 | "Material 日夜配对" 按对比度挑选色阶 (Q8, 2026-10-07 拍板) | 每个色系在 50~900 中挑选: 日 = 与日间窗口背景 (#FAFAFA) 对比度不低于 3:1 (WCAG 1.4.11) 的最浅色阶, 夜 = 与夜间窗口背景 (#212121) 对比度不低于 3:1 的最深色阶; 无色阶达标时取对比度最高者 (`ThemeColorDerivation.pickDayShade` / `pickNightShade`). 结果如 Teal 日 500 (#009688) / 夜 700 (#00796B); 黄与琥珀的日间无色阶达到 3:1 (取 900, 约 2.5~2.7:1); 酸橙 / 黄 / 琥珀 / 橙 / 灰的日夜为同一色阶. 预设定义在代码中, 激活中的预设快照在启动时按当前代码与语言重建 (组 id 不变). |

---

## 2. 范围与非目标

范围内:

- `app/src/main/java/org/autojs/autojs/theme/app/**` (20 个文件, 约 2,500 行): 颜色库页 / 条目页 / 旧布局 / 颜色历史 / Room 实体与数据库
- `theme/ThemeColor.kt`, `theme/ThemeColorManager.kt`, `theme/widget/ThemeColorFloatingActionButton.java` (accent 的现有消费方)
- 新增包 (规划): `theme/color/**` (纯算法), `theme/library/**` (自建库仓库, 选中状态, JSON, 推荐), `theme/intelligent/**` (引擎核心与胶水层)
- 接入点: `App.kt` (主题色初始化 `:106` / `:136`, 夜间模式 `:178-192`, `onConfigurationChanged` `:385`), `util/ViewUtils.kt:938-960` (夜间模式判定), `ui/settings/ThemeColorPreference.kt` 与 `ui/main/drawer/DrawerFragment.kt:773` (入口与摘要), `core/plugin/settings/OfficialPluginSettingsProvider.kt:127-131` (向插件传递主题三色)
- 脚本: `runtime/api/augment/colors/Colors.kt`, `runtime/api/augment/autojs/Autojs.kt:95`
- 资源: `res/menu/menu_color_libraries.xml`, `res/layout/mt_color_*`, `res/values*/strings.xml`, `res/values/strings_donottranslate.xml`

非目标 (本 Roadmap 不处理):

- 完整配色方案 (Material 3 secondary / tertiary / surface 等) 与窗口背景色随主题色变化 (D2).
- 图片 / 截图 / 应用图标 / 文本 / 网页 `theme-color` 等取色来源的界面; 仅在 P6 向脚本提供 `colors.extract` 纯函数.
- 联网能力: 在线调色板, 网页取色, 云同步.
- 通用调色板格式, 代码片段导出, 分享链接 / 二维码 (D14).
- 后台唤醒与精确闹钟 (D5).
- "与壁纸 / 当前色协调" 的推荐信号 (D12).
- 旧布局对新库类型的支持 (D13).
- APK Builder 打包应用 (inrt) 携带用户颜色库 (见 Q6).

---

## 3. 现状诊断 (2026-10-06)

行号以快照 `3089662e35` 为准, 路径相对 `app/src/main/java/org/autojs/autojs/`, 资源相对 `app/src/main/`.

| 编号 | 问题 | 锚点 |
| --- | --- | --- |
| F1 | 智能颜色库是空占位: 颜色列表为 `emptyList()`, 点击弹出 "开发中" 对话框, 描述显示 "[ 开发中 ]"; 字符串 `text_new_intelligent_color_library` ("新建智能颜色库") 已存在但无引用 | `theme/app/ColorLibrariesActivity.kt:230-236`, `ColorLibraryAdapter.kt:26-33`, `ColorLibraryViewHolder.kt:27-31`, `res/values/strings.xml:1748` |
| F2 | "添加颜色库" 子菜单 (新建 / 导入 / 克隆) 只弹 "开发中" toast | `ColorLibrariesActivity.kt:159-170`, `res/menu/menu_color_libraries.xml` (`action_add_color_library`) |
| F3 | 自建库的数据层只是未注册的占位: `ColorLibrary` 实体只有 `id`, `CreatedColor` / `PresetColor` 无库关联; `ColorHistoryDatabase` 为 version 1, `exportSchema = false`, 只注册了两张历史表; `COLOR_LIBRARY_FROM_IMPORTED / CLONED / CREATED` 常量与 `ic_color_library_created / imported / cloned` 图标已存在但未使用 | `ColorEntities.kt:22-46`, `ColorHistoryDatabase.kt:8-15`, `ColorLibrariesActivity.kt:209-211`, `ColorLibraryViewHolder.kt:42-44` (注释代码) |
| F4 | 预设库硬编码, ID 空间未给用户库预留; 选中状态由两个 Pref 键与 legacy 索引双写, 摘要与定位逻辑按预设库逐一分支, 无法容纳新库类型 | `ColorLibrariesActivity.kt:200-207, 214-276`, `ColorSelectBaseActivity.kt:199` (定位), `:608-614` (键), `:659` (摘要), `:810-849` (保存) |
| F5 | 主题色事实上是单色: `ThemeColor` 有 primary / dark / accent 三个字段, 但所有入口均以 `ThemeColor(color)` 构造使三者相等; 读取 accent / dark 的只有悬浮按钮与官方插件设置主题 | `theme/ThemeColor.kt:16-19`, `ThemeColorManager.kt:69`, `theme/widget/ThemeColorFloatingActionButton.java:40`, `core/plugin/settings/OfficialPluginSettingsProvider.kt:127-131` |
| F6 | 设置主题色没有 "来源" 概念, 也没有暂停与限频: `ThemeColorManager.setThemeColor` 直接保存并广播, 4 个调用点分散在 UI 类中 | `ThemeColorManager.kt:58-67`; 调用点在 `ColorItemAdapter.kt`, `ColorLibrariesActivity.kt`, `ColorSelectBaseActivity.kt`, `ColorSettingRecyclerView.kt` |
| F7 | 颜色历史只记录最近使用时间, 无使用次数, 无来源, 也没有收藏, 不足以支撑推荐 | `ColorEntities.kt:48-73` |
| F8 | 无色彩算法基座: `ColorUtils` 只有 hex 转换, WCAG 相对亮度 (阈值 0.179), 基于 HSL 的对比度调整, 亮度 / 对比度系数调整; 无感知色彩空间, 无感知色差, 无和谐色 / 色阶, 无量化; 颜色相关代码没有任何 JVM 单测 | `util/ColorUtils.kt:275` (`luminance`), `:348` (`adjustColorForContrast`), `:482` / `:513`; `app/src/test/java/org/autojs/autojs/util/` |
| F9 | "当前是否夜间" 没有统一的可订阅来源: 应用设置 (跟随系统 / 常开 / 常关) 与系统配置变化分别处理 | `util/ViewUtils.kt:938-960`, `App.kt:178-192, 385-386` |
| F10 | 脚本侧只有只读 getter `colors.themeColor` / `autojs.themeColor`; `colors` 模块已有 `contrast` / `distance` / `luminance` / `blend` / `invert` / `isSimilar` 等函数, 新 API 需避免重名; `colors.distance` 的算法表与 `images` 找色共用 `ColorDetector` | `runtime/api/augment/colors/Colors.kt:159, 1295, 1360`, `runtime/api/augment/autojs/Autojs.kt:95`, `core/image/ColorDetector.kt:179` |
| F11 | 旧布局与新布局并存, 选中状态双写; 任何新库类型都必须定义它在旧布局下的表现 | `theme/app/ColorSelectActivity.kt`, `ColorSettingRecyclerView.kt`, `ColorSelectBaseActivity.kt:625-657, 815-840` |
| F12 | 默认颜色库中的两个窗口背景色 (`window_background_light` = `md_gray_50` #FAFAFA, `window_background_night` = `md_gray_900` #212121) 是独立的两项, 无法表达 "随夜间模式自动切换" | `ColorLibrariesActivity.kt:224-225`, `res/values/colors.xml:33-34` |
| F13 | Material 色阶资源齐全 (`ColorItems.MATERIAL_COLORS` 含 19 个色系, 每个色系都有 50~900, 多数另有 A100~A700), 但没有 "日 / 夜成对" 的组织方式 | `core/image/ColorItems.kt:11` |

---

## 4. 概念模型

### 4.1 术语

| 术语 | 含义 |
| --- | --- |
| 颜色库 | 颜色的集合. 预设库只读; 自建库可编辑; 智能库是内容为 "组" 的自建库. |
| 状态源 | 决定 "当前处于哪个状态" 的来源, 每个智能库恰好 1 个. "无" 只有 1 个状态. |
| 状态 | 状态源的一个取值, 以稳定的 key 标识 (如 `day` / `night`, `period:0`, `weekday:1`, `season:summer`), 切换状态源时按 key 迁移颜色. |
| 组 | 一个或多个颜色构成一组: 每个状态各有一个颜色 (primary, 可选 accent / dark). 某状态未填时, 取按状态顺序第一个已填颜色的状态 (即旧思路的 "与 '开' 相同", 日 / 夜任一方均可借用另一方). 组可启用或禁用. |
| 换组时机 | 何时从当前组切换到下一组, 每个智能库 0~n 个. 没有换组时机时固定在用户选中的组. |
| 切换方式 | 下一组如何选取: 顺序 (按排序键, 正序或倒序, 到末尾后循环) 或随机 (组数 >= 2 时不与当前组相同). |
| 日期覆盖层 | 有序的 "日期条件 -> 组" 列表, 命中时临时改用指定组 (D4). |
| 激活 | 智能库成为当前主题色的来源. 同一时刻至多一个激活库; 用户或脚本直接选色即停用 (D10). |
| 有效颜色 | 引擎算出的当前主题色: (覆盖层命中 ? 覆盖组 : 当前组) 在当前状态下的颜色, 再按 D2 补全 accent / dark. |

### 4.2 结构示例

```text
智能库 "暖冷交替"
  状态源     夜间模式 (2 个状态: 日 / 夜)
  换组时机   [状态变化] [每 1 小时, 整点对齐]
  切换方式   顺序 (色相, 正序, 参照 "日" 状态)
  覆盖层     1. 圣诞节 (12-24 ~ 12-26) -> 组 "圣诞"
  组
    [x] 橙     日 #FFB74D   夜 #E65100
    [x] 青     日 #4DD0E1   夜 #006064
    [x] 紫     日 #BA68C8   夜 #4A148C
    [ ] 圣诞   日 #C62828   夜 #1B5E20   (禁用, 仅供覆盖层引用)

卡片摘要: [夜间模式 + 每 1 小时 | 3/4 组 | 顺序 (色相)]
```

### 4.3 评估流程

1. 收集输入: 当前状态 key, 覆盖层命中情况, 事件 (激活 / 状态变化 / 应用启动 / 计时到期 / 脚本换组 / 恢复评估).
2. 若处于暂停 (D6): 只记录待处理事件 (同类合并) 后返回.
3. 覆盖层命中: 目标组 = 覆盖组, 首次命中时记住进入前的组; 未命中且刚离开覆盖期: 恢复进入前的组.
4. 否则, 若事件属于已勾选的换组时机: 按切换方式选取下一组 (跳过禁用组; 当前组已删除或已禁用时直接取下一组).
5. 有效颜色 = 目标组[当前状态] (缺失时取按状态顺序第一个有颜色的状态), 按 D2 补全 accent / dark.
6. 与当前主题色不同则 `ThemeColorCoordinator.apply(..., INTELLIGENT)`; 持久化运行时状态与激活快照 (D9), 安排下一次计时.

"状态变化" 作为换组时机时, 先换组再取新状态的颜色, 与旧思路 "夜间模式 | 5 组 | 随机切换" 的行为一致.

### 4.4 状态源

| 状态源 | 状态 | 阶段 |
| --- | --- | --- |
| 无 | 1 | P1 |
| 夜间模式 | 2: 日 / 夜 ("夜" 可设为与 "日" 相同) | P1 |
| 时段 | 2~6 段, 每段起始时刻 hh:mm | P3 |
| 日出日落 | 2: 白天 / 夜晚 | P3 |
| 星期 | 7, 或 2 (工作日 / 周末) | P3 |
| 季节 | 4 (按半球) | P3 |
| 电量 | 2~5 个区间 | P3 |
| 充电 / 省电模式 / 无障碍服务 / 脚本运行 | 各 2 | P3 |
| 脚本 | 2~8 个命名状态, 由 `colors.theme.state(name)` 切换 | P6 |

### 4.5 换组时机

| 换组时机 | 说明 | 阶段 |
| --- | --- | --- |
| 状态变化 | 状态源的状态改变时换组; 状态源有 2 个及以上状态时默认勾选 | P1 |
| 应用启动 | 进程冷启动时换组 (旧思路 "应用重启") | P1 |
| 时间间隔 | 每 n 秒 / 分 / 小时; 首次触发: 现在 / 下一个整分 (如 15:52) / 下一个整时 (如 16:00) | P1 |
| 每日定时 | 一个或多个 hh:mm | P3 |
| 脚本 | `colors.theme.next()` | P6 |

### 4.6 切换方式与排序键

- 顺序: 排序键为 手动顺序 / 创建时间 / 修改时间 / 名称 / 色值; 色值可选 亮度 (OKLCH L) / 色相 (h) / 彩度 (C), 并需指定参照状态. 支持正序或倒序, 到末尾后循环.
- 随机: 等概率选取, 组数 >= 2 时不与当前组相同.

### 4.7 有效性提示 (沿用旧思路并扩展)

| 情形 | 处理 |
| --- | --- |
| 0 个组, 或全部组被禁用 (且无覆盖层) | 可保存, 不可激活; 卡片显示 "无可用组" |
| 状态源只有 1 个状态, 启用组不超过 1 个, 且无覆盖层 | 保存或激活时提示 "当前设置不具备智能表现, 是否继续" |
| 勾选了换组时机但启用组不超过 1 个 | 提示换组时机不会产生效果 |
| 某些状态缺少颜色 | 以按状态顺序第一个有颜色的状态补齐并提示 |
| 时间间隔低于 1 分钟 | 提示频繁变色的观感与耗电 (下限见 Q3) |

### 4.8 卡片摘要格式

`[状态源 (+ 换组时机) | 组数 | 切换方式]`:

- 全部组启用时只显示总数, 如 `[夜间模式 | 5 组 | 随机切换]`; 有禁用组时显示 "启用/总数", 如 `3/4 组`.
- 状态源为 "无" 时只显示换组时机, 如 `[时间间隔 | 1 组 | 顺序切换]`.
- 有多个换组时机时显示前两个, 其余以 "等 N 项" 省略; 状态变化不单独显示 (由状态源隐含).
- 顺序切换附带排序键, 如 `顺序 (色相)`; 存在覆盖层时追加 `+ 覆盖 N`.

---

## 5. 阶段总览

### P0 基座: 算法, 数据, 协调器与引擎核心 (无用户可见变化)

- [x] P0.1 色彩空间与度量 (D7, F8): `theme/color/OkColor` (sRGB <-> 线性 RGB <-> OKLab <-> OKLCH, `gamutMap` 保持 L 与 h 二分降低 C), `ColorMetrics` (dE_ok, WCAG 对比度, 色相差). 验收: JVM 单测对照 Ottosson 参考值, 8 bit 往返误差不超过 1, 色域映射输出均在 sRGB 内, 对比度与 `ColorUtils.luminance` 的计算结果一致. 证据: `OkColorTest` (8 例: CSS Color 4 中红 / 绿 / 蓝的 OKLCH 参考值, 无彩色, alpha, 4913 色往返误差不超过 1, 色域映射保持 L / h 且彩度最大, 量化后色相漂移小于 2 度, 色相归一化) + `ColorMetricsTest` (4 例: WCAG 亮度与已知对比度如 #777777 对白色 4.478, dE_ok, 色相距离) (JVM), 2026-10-06. `ColorUtils.luminance` 依赖 `android.graphics.Color` 无法在 JVM 调用, 改为按相同公式对照 WCAG 已知值.
- [x] P0.2 派生与变体 (D2): `ThemeColorDerivation` 提供 `deriveDark(primary)`, `deriveAccent(primary)` (保证与 `window_background_light` / `window_background_night` 的对比度不低于 3:1), `nightVariant(dayColor)` / `dayVariant(nightColor)` (按 L 映射, 保持 h), `ensureContrast(color, background, ratio)` (沿 L 方向搜索最近的满足者). 验收: 单测覆盖灰阶 (C 接近 0, 色相无定义), 纯黑 / 纯白, 高彩度蓝与黄, 所有输出满足对比度断言. 证据: `ThemeColorDerivationTest` (JVM, 11 例, 样本为 216 色网格加 8 个 Material / 背景色): dark 亮度降 0.08 且保持色相; accent 对两种窗口背景均不低于 3:1, 灰色主色得到灰色 accent; `nightVariant(md_teal_50)` 与 md_teal_800 的 dE_ok 小于 0.05; 日 / 夜变体的亮度范围与色相; `ensureContrast` 在 6 种背景 x 2 种对比度的全部可行组合中达标, 只移动必要距离, 不可行时回退为黑或白; `ensureContrastAgainstBoth` 不可行时最大化较弱一侧. 实现: 候选色先 8 位量化再判定, 沿亮度双向步进 0.01 后二分细化, 取亮度最接近原色者, 2026-10-06.
- [x] P0.3 Room schema v2 (D8, F3, F7): 新表 `color_library` (`kind`, `is_intelligent`, `name`, `origin`, `config_json`, `position`, 创建 / 修改时间), `color_library_color` (普通自建库的颜色: `name`, `color`, `position`, 时间), `color_group` (`name`, `enabled`, `position`, 时间), `color_group_state` (`group_id`, `state_key`, `primary_color` / `accent_color` / `dark_color` 可空), `color_favorite` (`color` 唯一, `pinned`, `position`, 时间); `color_history` 与 `palette_history` 增加 `use_count INTEGER NOT NULL DEFAULT 1`, `source TEXT NOT NULL DEFAULT 'USER'`, `source_detail TEXT`; 外键级联删除; 删除占位实体; `MIGRATION_1_2`; `exportSchema = true`, 1.json 按现有实体反推 (做法同 `HistoryDatabase`). 验收: `:app:kspAppDebugKotlin` 生成 2.json; `androidTest/.../ColorHistoryDatabaseMigrationTest` 编译通过 (1 -> 2 保留历史数据, 新表可读写), 真机执行列入 P7.2. 证据: 1.json 未手工反推, 而是先在 version 1 上打开 `exportSchema` 由 KSP 从原实体生成 (真实 identityHash `d86daee1...`; 嵌入字段 `hex` 实际排在 `last_used_time` 之后, 手工反推易错), 再升级实体由 KSP 生成 2.json (`c54aa10b...`), `MIGRATION_1_2` 的建表语句逐字取自 2.json; 实体类名为 `ColorLibrary` / `LibraryColor` / `ColorGroup` / `ColorGroupState` / `FavoriteColor`, 来源常量 `HistorySource`, DAO 为 `theme/library/ColorLibraryDao` (库 / 颜色 / 组 / 状态 `@Upsert` / 收藏); `ColorHistoryDatabase.createBuilder` 供生产与迁移测试共用. `ColorHistoryDatabaseMigrationTest` (2 例: v1 历史行保留且新列取默认值; 新表可写, 收藏按颜色唯一, 删除库级联清除颜色 / 组 / 状态) 经 `:app:compileAppDebugAndroidTestKotlin` 编译通过, 真机执行留待 P7.2, 2026-10-06.
- [x] P0.4 选中状态与 ID 空间 (D9, F4): 值类型 `ColorSelection` (预设条目 / 调色板 / 自建库颜色 / 智能库 + 起始组) 与 `ColorSelectionCodec` (读写现有两个键, 兼容 legacy 索引与 `SELECT_NONE`); `getCurrentColorSummary` 与 `checkAndGetTargetInfoForThemeColorLocate` 改用之. 验收: 单测覆盖所有既有 Pref 组合, 解码结果与当前行为一致; 新 ID 分段往返无损. 证据: `theme/library/ColorSelection.kt` (`ColorLibraryIds` / `ColorSelection` / `ColorSelectionCodec`), `ColorSelectBaseActivity.currentColorSelection()`; 定位, 摘要, 以及 `ColorLibrariesActivity` / `ColorItemsActivity` 中三份重复的 legacy 解码 (高亮选中项, 标题点击, 副标题) 统一改用 codec, 旧布局 (D13) 不变. `ColorSelectionCodecTest` (JVM, 6 例: legacy 索引的自定义 / 默认 / Material 首末 / 越界, 库键存在时忽略 legacy, 5 个预设库与调色板, 智能库入口 0x22 与未知 id, 用户普通 / 智能分段, 全部变体往返, 分段不重叠与越界拒绝), 2026-10-06. 行为改进: 仅有 legacy 索引的 Material 选择现在可被定位, 在 Material 库页显示副标题并高亮; 预设库缺少条目 id 时一律按当前主题色匹配条目 (原先只有默认库如此).
- [x] P0.5 主题色协调器 (D6, D10, D11, F6): `ThemeColorCoordinator.apply(themeColor, source, detail)` 作为唯一入口, 4 个调用点迁移; 暂停引用计数 (`pause(token)` / `resume(token)`, 全部恢复后回调引擎); `SCRIPT` 500 ms 合并窗口; 历史写入规则 (`INTELLIGENT` 不写). 决策部分抽为纯对象 `ThemeColorApplyPolicy`. 验收: 单测覆盖 来源 x 暂停 x 合并 的组合; 手动选色行为与改造前一致 (E2). 证据: `theme/ThemeColorApplyPolicy.kt` (含 `ThemeColorSource`) 与 `theme/ThemeColorCoordinator.kt`; 4 个调用点 (`ColorItemAdapter`, `ColorLibrariesActivity` 历史应用, `ColorSelectBaseActivity.onColorPickerConfirmed`, 旧布局 `ColorSettingRecyclerView`) 的 `setThemeColor` + `notifyThemeChanged` 改为 `ThemeColorCoordinator.apply(color, USER)`, 生效时由协调器在主线程执行同样两步. 脚本窗口为固定窗口 (首个请求开窗, 窗口结束时应用最后一个值, 不因后续请求顺延), 因此循环调用只产生一次可见变化, 长时间慢速调用每 500 ms 至多一次; `USER` / `RESTORE` 丢弃待生效的脚本值并取消调度, 过期或提前的 flush 被忽略; `pendingOrCurrent` 供 P6 的 `colors.theme.get()` 读取待生效值. `Listener` (`onExplicitColorApplied` / `onResumed`) 留给 P1.4 的引擎接入; 页面注册暂停令牌在 P1.4 落地. 签名中的 `detail` 与脚本历史写入推迟到 P6.3 (届时才有调用方). `ThemeColorApplyPolicyTest` (JVM, 9 例), `theme.*` 单测共 72 例通过, 2026-10-06; 手动选色的设备验证并入 P1.8 冒烟.
- [x] P0.6 引擎核心 (D3, D4, D15): `IntelligentConfig` + `IntelligentConfigJson` (version 1, 未知字段忽略); `IntelligentColorPlanner` (输入: 配置, 组, 当前状态 key, 覆盖层命中, 事件, 运行时状态; 输出: 目标组, 有效颜色, 新运行时状态, 下次到期时间), 实现 4.3 的流程与 4.6 的切换方式 (名称排序的 Collator 由调用方注入, 随机源可注入种子); `IntervalSchedule.nextDue(now, every, unit, anchor)` (anchor = 现在 / 下一个整分 / 下一个整时) 与补算 (过期只换一次); `IntelligentConfigValidator` 产出 4.7 的各类结论. 验收: 单测覆盖顺序 (各排序键, 正倒序, 循环), 随机不连续重复, 禁用组跳过, 当前组被删除, 覆盖层进入与离开, 补算, 校验结论. 证据: 包 `theme/intelligent` (`IntelligentConfig` 含组模型 `IntelligentGroup` / `StateColors` / `EffectiveColor`, `IntelligentConfigJson` 基于 Gson 树模型, `IntervalSchedule`, `IntelligentColorPlanner`, `IntelligentConfigValidator`); JVM 单测 34 例: `IntelligentColorPlannerTest` (15), `IntervalScheduleTest` (5, 含 +05:30 时区的本地整点与夏令时跳变), `IntelligentConfigJsonTest` (6, 含未知类型原样往返与非法值修复), `IntelligentConfigValidatorTest` (8), 2026-10-06. 实现要点: 覆盖层命中期间 `currentGroupId` 保持不变, 另记 `overrideGroupId`, 因此离开时无需 "进入前的组" 字段; 换组时机 "脚本" 由 `SCRIPT_NEXT` 事件表达, 不要求勾选; 时间间隔下限暂定 5 秒 (`IntervalSchedule.MIN_PERIOD_MILLIS`, 待 Q3 拍板); 覆盖层的日期匹配 (P3.5) 由调用方解析后以 `overrideGroupId` 传入.
- [x] P0.7 阶段收尾: `:app:compileAppDebugKotlin`, `:app:testAppDebugUnitTest --tests "org.autojs.autojs.theme.*"`, `:app:compileAppDebugAndroidTestKotlin` 通过; 本阶段以内部基座为主, 只为用户可见的修复写 changelog; 本表证据回填. 证据: 上述三项均通过 (E1), `theme.*` JVM 单测 9 类 72 例, 2026-10-06. 提交: P0.1 `acb045b426`, P0.2 `6f75a7eefd`, P0.3 `4f55b3a2a4`, P0.6 `ae56359ad3`, P0.4 `4dc1ee8bfe`, P0.5 `0abbf5d7d0` (P0.6 先于 P0.4 / P0.5 完成, 互不依赖). changelog: P0.4 带来一处用户可见修复, 故在 v6.8.0 `fix` 末尾追加 10 种语言各 1 条 (传统布局中选择的材料设计颜色, 切换布局后无法 "定位当前主题色" 且材料设计颜色页面不显示摘要). 生成方式: 生成脚本运行时会联网刷新相关项目数据, 并把 README 的 "数据更新于" / "截至" 日期改为当日; 为与本次范围一致, 经驱动脚本禁用网络 (相关项目数据取自脚本内缓存, 缓存未改写), 生成后只保留 README 的新增行并还原两处日期, CHANGELOG markdown 各增 1 行.

### P1 最小闭环: 自建库基础 + 智能库 (夜间模式 / 应用启动 / 时间间隔) + 两个预设

- [x] P1.1 自建库基础 (D1, F2, F3): "添加颜色库 > 新建颜色库" (输入名称) 与 "克隆颜色库" (选择任一预设库或自建库, 生成 `CLONED` 库, 默认名 "原名 (副本)"); 库列表在预设库之后列出自建库并启用 created / cloned 图标; 长按菜单: 重命名 / 克隆 / 删除 (确认; 删除当前主题色所在库时主题色不变, 选中状态退化为 "调色板"); 自建库页复用 `ColorItemsActivity` 的列表与搜索并增加编辑模式: 添加颜色 (调色板 / 颜色历史 / 从其他库多选), 重命名, 修改色值, 拖拽排序, 多选删除. 应用颜色写入 `color_history` (库 ID 按 D9); 来自已删除库的历史条目显示为色值并标注 "已删除的库". 证据: 仓库 `theme/library/ColorLibraryRepository` (新建 / 克隆 / 重命名均经 `LibraryNaming` 规范化与去重, 删除库级联删除颜色并清除该库颜色历史, 颜色增删改与重排刷新库修改时间) 与 `LibraryNamingTest` (JVM, 2 例); 库列表 `ColorLibraryAdapter` 改为 `LibraryRow` (预设 / 用户), 回到页面时刷新, 长按预设库可打开 / 克隆, 长按用户库可打开 / 重命名 / 克隆 / 删除; 菜单 "新建颜色库" / "克隆颜色库" 落地 ("导入" 仍属 P2); 自建库页 `UserColorLibraryActivity` (点击应用, 长按重命名 / 修改色值 / 删除, 菜单: 搜索, 从调色盘 / 颜色库 / 历史记录添加, 调整顺序模式 (`ItemTouchHelper` 长按拖拽, 退出时保存), 批量删除, 重命名库, 删除库, 定位当前主题色); 应用自建库颜色经 `ColorSelectBaseActivity.applyUserLibraryColor` (选中键按 D9, 旧布局按 D13 写入自定义颜色槽, 历史写入, 协调器 `USER`); 选中项的库名与颜色名缓存在 `SELECTED_COLOR_LIBRARY_NAME` / `SELECTED_COLOR_LIBRARY_ITEM_NAME`, 摘要显示为 "库名 | 颜色名 [#RRGGBB]"; 全部历史可解析并应用用户库颜色 (`ColorHistoryItem` 增加 `libraryId` / `itemId`, 应用时按 id 而非按标识字符串匹配, 历史加载抽为 `loadColorHistoryItems`); 22 条字符串 x 11 个 `strings.xml`. 与计划的差异: 自建库页为独立页面, 只复用条目布局与 `ColorItemViewHolder` (新增按原始值绑定的重载), 因 `ColorItemsActivity` 整体绑定资源 id; 删除库时同时清除其颜色历史, 不保留 "已删除的库" 标注 (历史表只存 id, 库删除后颜色值已不可得); 多选删除用多选对话框代替长按多选模式; 删除当前主题色所在的库或颜色时, 主题色保持不变, 选中状态改记为调色盘. 编译 + `theme.*` 单测 74 例 (E1), 2026-10-06; 界面冒烟并入 P1.8.
- [x] P1.2 智能颜色库入口页 `IntelligentLibrariesActivity` (F1): 点击 "智能颜色库" 不再弹出 "开发中"; 列表分 "预设" 与 "我的" 两段, 卡片显示名称, 4.8 摘要, 当前组的各状态色块, "使用中" 标记; 菜单 "新建智能颜色库" (复用 `text_new_intelligent_color_library`); 长按: 编辑 / 克隆 / 重命名 / 删除 (预设只有克隆). 详情页列出组 (各状态色块, 禁用组置灰), 点击组 = 激活该库并以该组为起始组, 并显示运行信息 (当前状态, 当前组, 下次换组时间, 是否暂停). 颜色库列表中 "智能颜色库" 条目的描述改为智能库数量与使用中库名. 证据: `IntelligentLibrariesActivity` (分区标题 + 卡片, 卡片色块为当前组 (未激活时为首个可用组) 的 `SegmentedCircleDrawable`, 描述为 `IntelligentSummaryFormatter.summary` 加 "使用中: 组名"; "我的" 为空时显示提示行) 与详情页 `IntelligentLibraryActivity` (信息区为摘要, 激活时追加 "使用中" / 当前状态 / 当前组 / 下次换组时间; 组列表复用 `ColorItemViewHolder` 多色绑定, 禁用或无颜色的组半透明并提示不可用; 点击可用组经 `activateIntelligentLibrary` 以 `ACTIVATION` 来源立即生效); 克隆 / 重命名 / 删除抽为 `IntelligentLibraryOperations` (克隆为新组 id, 删除激活库时停用引擎并退回调色盘选中, 重命名后通知引擎); 仓库新增 `listIntelligentLibraries` / `loadIntelligent` / `saveIntelligent` (DAO 事务 `saveGroups` 保留已有组 id, 使编辑后激活库保持当前组); 引擎 `onLibraryChanged` 在配置未变时只评估, 不重置调度; 25 条字符串与 4 组复数 (组数, 每 N 秒 / 分 / 时; 俄语 / 阿拉伯语按各自复数类别) x 11 个文件. 编辑入口 (新建 / 编辑) 于 P1.3 接入编辑器. 编译通过 (E0), 2026-10-06; 界面冒烟并入 P1.8.
- [x] P1.3 智能库编辑器 `IntelligentLibraryEditorActivity` (D3, 4.4~4.7): 名称; 状态源 (本阶段: 无 / 夜间模式, "夜" 可设为与 "日" 相同); 换组时机多选 (状态变化 / 应用启动 / 时间间隔: 每 n 秒 / 分 / 小时, 首次触发: 现在 / 下一个整分 / 下一个整时, 并预览具体时刻); 切换方式 (顺序: 4.6 的排序键 + 参照状态 + 正倒序; 随机); 组列表 (添加 / 编辑 / 启用开关 / 删除 / 拖拽排序); 组编辑 (名称可空, 空时显示色值; 每个状态一个颜色, 来源为调色板 / 颜色库 / 颜色历史); 保存时运行 `IntelligentConfigValidator` 并给出 4.7 的提示; 返回时有未保存修改则确认. 编辑激活中的库, 保存后立即重新评估. 证据: `IntelligentLibraryEditorActivity` (表单布局 `mt_activity_intelligent_library_editor.xml` + 样式 `styles_intelligent_color_library.xml`); 状态源切换时各组颜色按状态位置迁移; "状态变化" 仅在状态源有 2 个及以上状态时可勾选; 时间间隔对话框 (数值 1~9999, 秒 / 分 / 时, 最近一次触发时机 "现在 / 下一个整分 / 下一个整时" 各附首次换组时刻的实时预览, 由 `IntervalSchedule` 计算); 切换方式对话框列出 7 个排序键与随机, 色值类排序键在多状态时显示 "参照状态", 顺序切换显示 "倒序"; 组列表复用 `ColorItemViewHolder`, 长按拖拽排序, 点击打开组编辑对话框 (组名, 启用, 每状态色块 + 色值, 非首个状态可勾选 "与 X 相同", 删除); 校验结论实时显示于表单底部 (7 类提示), 保存时 "不具备智能表现" 需确认, 0 个可用组可保存; 当前版本无法编辑的换组时机与覆盖层原样保留; 保存后通知引擎 (激活中的库立即重新评估), 新建的库保存后打开其详情页. 34 条字符串 x 11 个 `strings.xml` (复用既有的 `text_sort_descending`); 编译通过 (E0), 2026-10-07; 界面冒烟并入 P1.8.
- [x] P1.4 引擎接线 `IntelligentColorEngine` (D5, D6, D9, F9): Application 级单例; 激活 / 停用; 新增 `NightModeState` 统一 "当前是否夜间" 并可订阅 (应用设置变化 + `App.onConfigurationChanged`); 应用启动: 在 `ThemeColorManager.init()` 之后同步读取激活快照并评估, 首帧沿用上次保存的有效颜色, 结果不同时再应用; 时间间隔: 主线程 `Handler` 按 `IntervalSchedule` 对齐调度, `ACTION_TIME_CHANGED` / `ACTION_TIMEZONE_CHANGED` 时重排; 运行时状态 (当前组, 覆盖前组, 上次换组时间, 下次到期时间) 持久化; 所有主题色页面在 `onCreate` / `onDestroy` 中注册暂停令牌 (D6). 验收: 附录 C 第 3~6 项. 证据: `theme/intelligent/IntelligentColorEngine` (Application 级单例, 实现协调器 `Listener` 与夜间模式 `Listener`; 激活快照 `IntelligentSnapshot` 与运行时状态以 JSON 存于 Pref 键 `INTELLIGENT_COLOR_ACTIVE_SNAPSHOT` / `INTELLIGENT_COLOR_RUNTIME_STATE`, 编解码 `IntelligentEngineJson`); `NightModeState` 以 `ViewUtils.isNightModeEnabled` 为唯一依据 (各模式下均保持同步), 通知由 `ViewUtils.setDefaultNightMode` 发出并延后一轮主线程循环; `App.onCreate` 主进程 (非 inrt) 在主题色与夜间模式初始化之后调用 `init` 并以 `APP_START` 评估; 时间间隔用主线程 `Handler.postDelayed` 到 `nextDueAt`, 间隔生效期间注册 `ACTION_TIME_CHANGED` / `ACTION_TIMEZONE_CHANGED` 重排; 回到前台 (`ActivityLifecycleCallbacks.onActivityResumed`) 仅在到期, 状态变化或主题色偏离预期时评估, 暂停期间跳过; 用户或脚本的显式选色经协调器回调停用引擎; 选中状态不再属于激活库时自动停用. 新增协调器来源 `ACTIVATION` (用户激活库或选组: 立即生效, 暂停期间亦然, 不停用智能库), `ThemeColorApplyPolicyTest` 增至 10 例; 暂停令牌由 `ColorSelectBaseActivity` 统一持有 (颜色库页 / 条目页 / 旧布局 / 自建库页), 因配置变化重建时延后一轮释放 (`releasePauseToken`), 避免旋转或切换夜间模式时误评估; 选中状态写入 `savePrefsForIntelligent` (条目 id 为起始组, legacy 索引置空). `IntelligentEngineJsonTest` (JVM, 4 例), `theme.*` 单测 79 例 (E1), 2026-10-06; 设备验证并入 P1.8 (附录 C 第 3~6 项). P1.8 冒烟修正 (`d1a53941c7`): 时间间隔到期与夜间模式变化的回调原先直接评估, 绕过了暂停检查, 主题色页面打开期间组已前进且名称缓存已改, 只有颜色等到离开后才生效; 现在暂停检查位于 `evaluate` 入口, 暂停期间任何回调都不改变组与颜色, 最后一个令牌释放时经 `onResumed` 评估一次 (D6).
- [x] P1.5 预设 "Material 日夜配对" (F13, 旧思路 "材料颜色-50/800"): 19 个 Material 色系各一组, 日 = 50, 夜 = 800, 状态源为夜间模式, 不设换组时机 (选组即固定, 相当于旧思路中各色系的 "自动" 项); 只读, 可克隆后加入换组时机. 固定的 50 / 800 色仍在 "Material 颜色库" 中选择. 证据: `theme/intelligent/IntelligentPresets.materialDayNight` (库 id `0x100`, 组 id 为色系下标 0~18, 组名取 `md_*_50` 名称去掉 " (50)" 后缀, 各语言均为 "名称 (色阶)" 格式, 无需新增 19 x 10 条翻译; 库名 "材料设计日夜配对"), 2026-10-06; 入口随 P1.2 落地. 2026-10-07 按 D24 改为按对比度挑选色阶: `ThemeColorDerivation.pickDayShade` / `pickNightShade` (`ThemeColorDerivationTest` 新增 2 例: Teal 日 500 / 夜 700, 输入顺序无关, 黄色无达标色阶时取对比度最高的 900), 各色系的 50~900 取自 `ColorItems.MATERIAL_COLORS`; 引擎启动时按当前代码与语言重建激活中的预设快照 (`IntelligentColorEngine.refreshPreset`), 已激活的组保持不变, 名称随语言更新; 克隆出的副本保留克隆时的颜色.
- [x] P1.6 预设 "背景色" 与默认库合并项 (旧思路 "背景色对话框", F12): 预设智能库 "背景色" 有 4 组: 亮色 (日夜均为 #FAFAFA), 暗色 (日夜均为 #212121), 自动跟随 (#FAFAFA / #212121), 自动反色 (#212121 / #FAFAFA), 状态源为夜间模式, 无换组时机. 默认颜色库中的两个窗口背景色合并显示为一项 "背景色", 点击弹出对话框 "选择一个 AutoJs6 窗口背景色作为主题色" (四选一, 显示色值); 选 亮色 / 暗色 仍是原预设条目 (保留 item id 3 / 4, 兼容既有选中状态与历史), 选 自动跟随 / 自动反色 则激活预设 "背景色" 的对应组. 证据: `IntelligentPresets.background` (库 id `0x101`, 组 id 0~3); `ColorLibrariesActivity.displayColorsOf` 把默认库的条目 3 / 4 合并为沿用 id 3 的 "背景色" 条目 (`PresetColorItem.mergedColorRes`), 全局搜索仍列出两项原条目; `ColorItemsActivity` 的列表 / 搜索 / 滚动 / 定位均改用显示列表, 条目 4 与 "背景色" 智能库映射到合并条目高亮; 点击合并条目弹出单选对话框并预选当前方案; 条目页的历史改为按 id 应用 (`applyPresetItem` 上移至 `ColorSelectBaseActivity`); 多色条目由新的 `SegmentedCircleDrawable` 绘制 (两色时左日右夜), `ColorItemViewHolder` 复用单色视图时恢复圆形背景; 智能库选中时摘要显示 "库名 | 组名 [#RRGGBB]" (名称由引擎写入缓存). 9 条字符串 x 11 个 `strings.xml` (含日 / 夜状态名), 编译通过 (E0), 2026-10-06; 界面冒烟并入 P1.8 (附录 C 第 3, 7 项).
- [x] P1.7 摘要, 定位与旧布局 (D9, D13, F11): `getCurrentColorSummary` 在智能库激活时显示 "智能 | 库名 | 组名 #RRGGBB" 形式 (标识沿用 `color_library_identifier_intelligent_colors`); 抽屉与设置项摘要随之刷新; "定位当前主题色" 打开智能库详情页并定位到当前组, 自建库颜色则打开对应库并滚动到该颜色; 旧布局按 D13 处理. 证据: 摘要读取选中名称缓存 (`SELECTED_COLOR_LIBRARY_NAME` / `SELECTED_COLOR_LIBRARY_ITEM_NAME`, 自建库颜色在应用时写入, 智能库由引擎每次评估时写入当前组名), 形如 "库名 | 组名 [#RRGGBB]", 库名超过 12 个字符时截断; 抽屉与设置项沿用 `getCurrentColorSummary`, 随主题色变化刷新; 定位入口 `checkAndGetTargetInfoForThemeColorLocate` 对自建库颜色打开 `UserColorLibraryActivity` 并按参数滚动到该颜色, 对智能库打开 `IntelligentLibraryActivity` (详情页菜单再定位到当前显示的组); 旧布局摘要在智能库激活时显示 "智能: 库名 [#RRGGBB]", 旧布局中选色经协调器以 `USER` 来源停用智能库 (P0.5 / P1.4), 自建库颜色写入自定义颜色槽 (P1.1). 编译通过 (E0), 2026-10-07; 冒烟并入 P1.8 (附录 C 第 8 项).
- [x] P1.8 阶段收尾: 11 个 `strings.xml`; changelog (feature: 自建颜色库的新建 / 克隆 / 编辑, 智能颜色库与两个预设; improvement: 默认颜色库的背景色合并为对话框); 编译 + JVM 单测 + AVD 冒烟 (附录 C 第 1~8 项). 证据: 字符串: 本阶段新增 89 条字符串与 5 组复数, 11 个文件均无缺失, 非英语文件中与英语相同者仅为同形词 (西语 "Manual" / "Color", 法语 "Minutes" / "Chroma"). changelog: v6.8.0 `released_date` 改为 2026/10/07, 10 种语言各追加 feature 3 条 (自建颜色库; 智能颜色库与换组时机; 两个预设与编辑器) 与 improvement 2 条 (默认颜色库背景色合并; 摘要, 定位与旧布局); markdown 生成方式同 P0.7 (禁用网络, README 只保留新增行与版本日期标题, 还原 "数据更新于" / "截至" 两处日期). 编译 `:app:assembleAppDebug` 通过, `theme.*` JVM 单测 79 例通过 (E1). AVD 冒烟 (E3, emulator-5554, x86_64, API 37, 覆盖安装于升级前的 v6.8.0 调试版之上, 2026-10-07): 第 1 项 新建 "Smoke" 库并从 Android 颜色库添加 4 色, 应用后摘要 "Smoke | Olive [#808000]", 库 id `0x0100_0002`, 强制停止重启后摘要不变, 删除该库后主题色不变, 摘要退化为 "调色板 | #000080"; 第 2 项 克隆 Material 颜色库 (256 项) 并重命名一项, 原预设不变; 第 3 项 激活 "材料设计日夜配对" 的 Teal 组, 应用内开关与跟随系统 (`cmd uimode night`) 两种方式下主题色在 #E0F2F1 / #00695C 之间切换; 第 4 项 克隆预设并设为 "每 1 分钟, 下一个整分", 整分时自动换组, 停留在主题色页面跨过整分时组与颜色均不变, 离开后只补换一次 (修正后复验, 见 P1.4); 第 5 项 随机切换 + 状态变化, 连续切换系统夜间模式 6 次, 组序列 1 -> 5 -> 12 -> 2 -> 11 -> 16 -> 10, 无连续重复, 各次颜色与日夜状态相符; 第 6 项 强制停止后错过 3 个间隔再冷启动, 只换一组 (Red -> Pink), 下次到期对齐到整分, 首帧无闪色未能用 adb 目测, 依据为评估在 `App.onCreate` 中先于任何 Activity 绘制; 第 7 项 升级前在旧版选中 "背景色 (暗)", 升级后合并条目对话框预选 "暗色", 四个选项均显示色值, 选 "自动跟随" 激活预设 "背景色" 的组 2; 第 8 项 旧布局摘要 "Intell.: Background color [#FAFAFA]", 在旧布局选 Red (500) 后智能库停用, 新布局摘要为 "Material | Red (500)" 加色值; 另验证 Room v1 -> v2 升级后旧版写入的颜色历史仍可见 (第 14 项的设备部分), "应用启动" 换组 (冷启动 Light green -> Lime), 编辑激活库后保持当前组, 摘要卡片 `[夜间模式 + 应用启动 + 每 1 分钟 | 19 组 | 顺序切换]`. 观察 (未改): 分段色块无描边, 浅色半边在浅色页面上不可见 (与既有单色条目一致); 卡片摘要不列出 "状态变化" (由状态源隐含, 沿用旧思路格式); 日语中库名沿用既有的 "カラーパレット", 新增文案为 "カラーライブラリ", 两者并存. 提交: P1.1 `e50f35459d`, P1.4 `dcacdef59f`, P1.5 / P1.6 `7d3b561c97`, P1.2 `95c9ded883`, P1.3 `a6ff058510`, P1.7 `2b014bc8d3`, 冒烟修正 `d1a53941c7`.

### P2 自建库完善与交换 (AutoJs6 JSON)

- [x] P2.1 `ColorLibraryJson` 编解码 (D14, 附录 B): 纯对象; 颜色一律 `#RRGGBB`; 组在文件内以下标引用; 限额: 文件不超过 2 MiB, 库不超过 256 个, 每库颜色或组不超过 4096 个. 验收: 单测覆盖往返无损, 未知字段忽略, 高版本 schema 拒绝, 非法色值, 超限. 证据: `theme/library/ColorLibraryJson` (Gson 树模型; 编码为带缩进的 JSON, 时间为带偏移的 ISO 8601, 精确到秒; 智能库配置复用 `IntelligentConfigJson.toJson` / `fromJson`, 覆盖层的组 id 与文件下标经 `IntelligentConfig.remapGroupIds` 互相映射, 解码后组以下标为 id; 读取时也接受 `#AARRGGBB` 并去除透明度; 未知类型的库跳过并计数, 无效色值, 非对象的组与指向不存在组的覆盖层丢弃并按库计数 (`droppedValues`), 供导入预览提示; 错误为 `ColorLibraryJsonException` + `Reason` (过大 / 格式错误 / 不是颜色库文件 / 更高版本 schema 附版本号 / 无可导入的库 / 库过多 / 某库条目过多附库名), 编码时同样检查限额). `ColorLibraryJsonTest` (JVM, 9 例: 往返且组按下标重编号, 文件结构与附录 B 一致, 未知字段与类型, 不可导入的文件 (含 schemaVersion 99), 无效色值计数, 三项限额的边界与超限, 库名规范化与回退, 不受支持的配置原样往返, 色值严格解析). 同时修正: 组获得新 id 时覆盖层引用随之改写 (`ColorLibraryRepository.saveIntelligent` 在同一事务内按保存后的组 id 改写, 克隆时先映射到临时 id), 此前克隆或新建组会使覆盖层指向错误的组 (覆盖层自 P3.5 起可编辑, 当前无用户可见影响). `theme.*` 单测 91 例 (E1), 2026-10-07.
- [x] P2.2 导出: 库长按 "导出", 以及菜单 "导出颜色库..." 多选 (可含预设库与预设智能库的快照); SAF `CreateDocument` (`application/json`, 默认名 `autojs6-colors-yyyyMMdd-HHmmss.json`); IO 线程写出. 证据: `theme/app/ColorLibraryExchange` (在 Activity 构造期间注册 `CreateDocument` / `OpenDocument`; 待导出的库以键 (`preset:` / `intelligent-preset:` / `user:`) 记录并存入 `SavedStateRegistry`, 文档选择器期间 Activity 重建后仍能完成导出; IO 线程加载并写出, 文件内含 `app` 版本信息); 入口: 颜色库页长按预设库 / 自建库 "导出", 智能颜色库页长按预设与自建智能库 "导出", 颜色库页菜单 "导出颜色库..." 多选 (列出 5 个预设库, 2 个预设智能库与全部自建库, 智能库标注 "智能"); 预设普通库导出原始条目 (默认库的两个窗口背景色各为一项). 提交 `e68d5af01f`.
- [x] P2.3 导入: "添加颜色库 > 导入颜色库" 走 SAF `OpenDocument`; IO 线程解析与校验; 预览对话框 (库名, 类型, 颜色数或组数, 可勾选); 重名自动加序号 `名称 (2)`; 导入的库 `kind = IMPORTED` 并启用 imported 图标; 智能库配置引用了当前版本不支持的状态源或换组时机时, 保留原始 JSON 并在编辑器中标注 "不受支持, 不会生效"; 错误映射为本地化文案. 证据: "添加颜色库 > 导入颜色库" 接入 `ColorLibraryExchange.import` (`OpenDocument` 同时接受 `application/json`, `text/plain` 与 `application/octet-stream`, 因部分文件提供方不报告 JSON 类型); 读取至多 2 MiB + 1 字节, 去除 UTF-8 BOM 后解码; 预览为多选对话框 (默认全选, 显示 "名称 [N 色]" 或 "名称 [智能 | N 组]", 有被跳过的值或未知类型的库时在正文提示数量); 普通库经 `createPlainLibrary`, 智能库的组换为临时负数 id 并映射覆盖层后经 `saveIntelligent`, `kind = IMPORTED`, `origin` 记录文件名; 库名在现有库名与预设库名中去重 (冒烟中发现导回的预设快照与预设同名, 已修正为 `名称 (2)`, `c282519e09`); `Reason` 映射为 7 条本地化错误文案; 不受支持的状态源 / 换组时机 / 覆盖层原样保留, 编辑器沿用 P1.3 的 "包含当前版本不支持的条目, 它们不会生效" 提示. 12 条字符串与 5 组复数 x 11 个文件. 提交 `e68d5af01f`.
- [x] P2.4 全局搜索覆盖自建库颜色与智能库组颜色 (`filterColorsFromColorItems` 的数据源扩展, 结果带库标识前缀); 搜索帮助页同步说明. 证据: `ColorSelectBaseActivity.ColorQueryMatcher` 抽出原有的 `#` / `$` / 正则匹配规则供多种结果共用 (`filterColorsFromColorItems` 行为不变); 颜色库页搜索改用 `GlobalColorSearchAdapter`, 结果依次为预设颜色 (两个窗口背景色仍各为一项), 自建库颜色 (`ColorLibraryRepository.listAllPlainColors`), 预设与自建智能库的组 (按组名或任一状态的色值匹配, 以分段圆形显示各状态颜色); 描述末尾为库标识或库名 (过长时在中间截断, 保留 "(副本)" 与 "(2)" 等结尾, `c282519e09`); 点击预设颜色 / 自建库颜色 / 组分别经 `applyPresetItem` / `applyUserLibraryColor` / `activateIntelligentLibrary`, 选中状态按当前选择实时计算; 搜索帮助 (11 个 `color_search_help_document.md`) 增加 "搜索范围" 一段. 提交 `86117c4f5e`.
- [x] P2.5 库列表管理: 自建库拖拽排序, 多选删除 / 导出. 证据: 颜色库页菜单 "调整颜色库顺序" (`ColorLibraryAdapter.isReordering`, `ItemTouchHelper` 长按拖动, 预设库行不可拖动也不可作为目标; 每次拖动结束即经 `ColorLibraryRepository.reorderLibraries` 保存, 未列出的智能库保持相对顺序排在其后; 自建库少于 2 个时提示) 与 "删除颜色库..." (多选 + 确认, 删除当前主题色所在的库时退回调色板选中); 多选导出即 P2.2 的 "导出颜色库...". 6 条字符串 + 选择对话框标题 1 条 x 11 个文件. 提交 `86117c4f5e`.
- [x] P2.6 阶段收尾: 字符串, changelog (feature: 颜色库导入 / 导出; improvement: 搜索范围), 冒烟 (附录 C 第 9 项). 证据: 字符串: P2 新增 20 条字符串与 5 组复数, 11 个文件均无缺失. changelog: v6.8.0 各语言追加 feature 1 条 (颜色库导入与导出) 与 improvement 1 条 (搜索范围), 并按 `AGENTS.md` 合并同一功能的实现, 改写 P1 的两条 feature (自建颜色库补充 "颜色库本身也可拖动排序与批量删除"; "材料设计日夜配对" 由 "50 / 800 配对" 改为 "日夜色阶按与窗口背景的对比度挑选", D24); markdown 离线生成, README 保留新增与改写的条目行, 还原两处日期. `theme.*` JVM 单测 91 例 (E1). 冒烟 (2026-10-07): emulator-5554 (API 37, E2): D24 的 Teal 日夜色 #009688 / #00796B 与列表中 19 组色阶, 酸橙 / 黄等同色系显示为单色; D19 的间隔对话框输入 1 秒保存为 "每 5 秒", 提示 "间隔 (5 至 9999)"; 附录 C 第 9 项: 经 "导出颜色库..." 导出默认库快照 + 智能库副本 + 普通库副本 (文件名 `autojs6-colors-yyyyMMdd-HHmmss.json`, 结构与附录 B 一致), 导回后三个库的颜色数 / 组数与摘要一致且重名加序号, 将 `schemaVersion` 改为 99 的文件被拒绝并提示 "文件的格式版本为 99, 需要更新版本的 AutoJs6"; 全局搜索 "Blush" 命中两个自建库副本, "Teal" 命中预设颜色与三个智能库的 Teal 组, 点击组激活对应智能库; 拖动排序在强制停止重启后保持, 预设库不可被越过; 批量删除两个库. Sony G8441 (Android 9, API 28, E3): "材料设计日夜配对" 的 Teal 日间为 #009688; 长按 Android 颜色库导出, 再导入为 "Android color library (2)"; 搜索 "Lime" 命中导入库的 "Green / Lime" 与智能库的 Lime 组; 测试后删除导入库与导出文件并恢复原主题色 #FFDEAD. 冒烟发现的 3 个问题已修正 (`c282519e09`). D23 (旧布局): P2 不改动旧布局, 旧布局不显示也不搜索自建库与智能库.

### P3 条件扩展: 状态源, 每日定时与日期覆盖层

- [x] P3.1 时间类状态源: "时段" (2~6 段, 每段起始时刻, 默认 06:00 / 18:00 两段); "日出日落" (2 个状态, 纯对象 `SolarCalculator` 实现 NOAA 离线算法, 极昼 / 极夜时固定为白天 / 夜晚; 地理信息来源见 Q4). 验收: 单测对照若干城市与日期的日出日落 (误差不超过 2 分钟) 与极地情形. 证据: 模型 `StateSource.TimePeriods` (起始时刻为一天中的分钟数, 规范化为排序去重的 2~6 个, 默认 06:00 / 18:00, 最后一段跨午夜) 与 `StateSource.SunriseSunset` (位置为 null 时按 D20 由时区推断, 状态 key 沿用 day / night, 与夜间模式之间可沿用颜色); 纯对象 `theme/intelligent/calendar/SolarCalculator` (NOAA 太阳位置算法, 标准天顶角 90.833 度, 在事件时刻再迭代一次; 结果围绕当地太阳正午计算, 属于当地日期; 太阳不穿过地平线时按正午高度区分极昼 / 极夜) 与 `TimeZoneLocations` (约 200 个常用 IANA 时区 -> 代表城市坐标; 未知时区以标准偏移估算经度, 纬度取 35 度, 澳洲 / 南极等前缀取南纬, 标记为估算); `StateResolver` 给出当前状态与下一次变化时间 (时段为下一个起始时刻, 日出日落为相邻三天事件中的下一个, 极昼 / 极夜为下一个午夜). JVM 单测: `SolarCalculatorTest` (5 例: J2000 儒略日; 赤道春分 06:04 / 18:11 UTC, 北京夏至 04:46 / 19:46, 伦敦夏至 04:43 / 21:21, 纽约冬至 07:16 / 16:32, 悉尼夏至 05:41 / 20:05 均在 2 分钟内; 结果属于当地日期; 特罗姆瑟与南纬 75 度的极昼极夜; 与独立的 Almanac for Computers 算法在 14 个城市 x 12 个月上的差异不超过 3 分钟), `TimeZoneLocationsTest` (3 例), `StateResolverTest` 中的时段与日出日落用例. 2026-10-07.
- [x] P3.2 日期类状态源: "星期" (7 个状态或 "工作日 / 周末" 2 个状态, 一周首日跟随地区); "季节" (4 个状态, 按月划分气象季节, 半球来源见 Q4). 验收: 单测覆盖边界日期与南半球. 证据: `StateSource.Weekday` (7 个状态 `weekday:1`~`weekday:7` 为 ISO 星期一至星期日, 或 `workday` / `weekend` (星期六, 星期日为周末)) 与 `StateSource.Seasons` (气象季节, 南半球偏移 6 个月, 半球为 null 时按 D20 推断); 下一次变化分别为下一个午夜与下个月第一天. 与计划的差异: 星期状态固定按 ISO 顺序 (星期一在前) 排列, 未按地区调整一周首日, 因为组编辑器的 "与首个状态相同" 与引擎的回退规则 (取按状态顺序第一个有颜色的状态) 依赖同一顺序, 按地区重排显示会使二者不一致; 星期名称按当前语言显示. JVM 单测 `StateResolverTest` (星期与工作日 / 周末, 季节南北半球与月初边界, `seasonOf` 四个用例). 2026-10-07.
- [x] P3.3 设备状态源: "电量" (2~5 个区间, 阈值可调, 2% 迟滞防抖), "充电", "省电模式", "无障碍服务" (AutoJs6 无障碍服务是否运行), "脚本运行" (是否有脚本在运行). 广播与回调只在激活库用到时注册 (D5). 验收: 区间映射与迟滞的单测; AVD 上用 `dumpsys battery` 模拟. 证据: `StateSource.Battery` (1~4 个阈值规范化为排序去重的 1~99, 形成 2~5 个区间 `battery:i`; 离开当前区间须越过边界 2 个百分点, 电量未知时保持原区间或视为满电) 与 `Charging` / `PowerSave` / `AccessibilityService` / `ScriptRunning` (状态 key 为 off / on); `DeviceStateMonitor` 只监听激活库需要的状态 (电池为 `ACTION_BATTERY_CHANGED`, 省电为 `ACTION_POWER_SAVE_MODE_CHANGED`, 无障碍服务为 `AccessibilityService.registry` 监听器, 脚本为 `ScriptEngineService` 引擎生命周期回调, 以引擎数量判断), 停用库时全部注销 (D5), 连续变化合并为一次主线程回调; 引擎在设备事件后仅当解析出的状态变化时才评估, 避免电池广播频繁写入. JVM 单测 `StateResolverTest` (电量区间与迟滞 11 个断言, 开关类状态源). AVD 冒烟 (emulator-5554, API 37): `dumpsys battery set level` 依次 40 / 51 / 52 / 49 / 48 / 47, 状态依次为 `battery:0` / `battery:0` / `battery:1` / `battery:1` / `battery:1` / `battery:0`, 主题色在 #C62828 / #2E7D32 之间切换且阈值附近无抖动; `dumpsys battery set ac 1 / 0` 使充电状态源在 #F9A825 / #455A64 之间切换. 2026-10-07.
- [x] P3.4 换组时机 "每日定时" (一个或多个 hh:mm), 与时间间隔共用调度. 验收: 单测覆盖跨日与夏令时切换. 证据: `RotationTrigger.Daily` (1~12 个本地时刻) 与纯对象 `DailySchedule` (下一个严格晚于当前时刻的时刻; 夏令时跳过的时刻在跳变后触发, 重复的时刻只在首次出现时触发; `nextMidnight`); `IntelligentColorPlanner` 增加 `dailyDueAt`, 与时间间隔共用 "过期只换一次" 规则, 同一次评估中二者同时到期只换一组; 运行时状态 `earliestDueAt` 供引擎调度与详情页显示. JVM 单测: `DailyScheduleAndOverridesTest` (纽约 2026-03-08 的 02:30 在 03:30 EDT 触发, 2026-11-01 的 01:30 只在首次触发, 夏令时前后的午夜), `IntelligentColorPlannerTest` 新增 2 例 (进程死亡期间错过 20:00 与次日 08:00 只换一组并重新排到之后; 时间间隔与每日定时同时到期只换一组). 2026-10-07.
- [x] P3.5 日期覆盖层 (D4): 有序列表, 每项为日期条件 + 同库内的目标组; 日期条件: 自定义日期区间 (每年重复或仅一次, 支持跨年如 12-24 ~ 01-02), 节日 (`HolidayCalendar`: 公历固定日 / 第 n 个星期几 / 农历日期; `ChineseLunarCalendar` 以查表实现 1900~2100 年; 首批节日与地区见 Q5). 验收: 单测覆盖农历转换已知日期, 跨年区间, 多覆盖层优先级, 进入与离开时的组恢复. 证据: `DateCondition.Holiday` (节日 id + 提前 / 延后天数, 上限 30 天) 与 `DateOverrides` (自上而下第一个命中者生效; 日期区间按月-日数值比较, 起点晚于终点时跨年, 指定年份时只计从该年开始的那一次, 以 02-29 结束的区间在平年于 02-28 结束); `calendar/HolidayCalendar` 首批 14 个节日 (D21: 元旦, 情人节, 万圣节前夜, 圣诞节; 春节, 元宵, 端午, 七夕, 中秋, 重阳; 复活节 (匿名公历算法), 母亲节 (5 月第二个星期日), 父亲节 (6 月第三个星期日), 感恩节 (11 月第四个星期四, 推荐地区美国)), 农历节日的推荐地区为中文语言与 CN / TW / HK / MO / SG, 编辑器按当前地区把推荐节日排在前面 ("默认启用" 落地为推荐顺序: 覆盖层按库添加, 不设全局节日开关); `calendar/ChineseLunarCalendar` 以 1900~2100 年查表实现. 引擎每次评估时以当天日期匹配覆盖层, 存在覆盖层时在下一个午夜唤醒. JVM 单测: `ChineseLunarCalendarTest` (5 例: 25 个已知春节日期, 12 个节日日期, 闰月与 2025 年闰六月, 不存在的日期, 1900~2099 每年天数与春节落在 1-21 至 2-20 之间), `HolidayCalendarTest` (4 例), `DailyScheduleAndOverridesTest` (区间, 跨年, 仅一次, 02-29, 优先级); 仪器测试 `ChineseLunarCalendarIcuTest` 在 emulator-5554 上以平台 ICU 天文农历核对 1901~2099 年每个月 (含闰月) 的初一, 除 14 个已知差异外全部一致 (差异为朔日落在北京时间午夜前后的 1954, 1955, 1999, 2012, 2018, 2027, 2030, 2070 年各一个月, 以及 ICU 闰月位置不同的 1917, 1922, 1987 年, 表与官方日期一致, 如 2027 与 2030 年春节为 2 月 6 日与 2 月 3 日). AVD 冒烟: 节日覆盖层 (圣诞节, 提前 1 天) 在 `cmd alarm set-time` 把日期改到 2026-12-25 后立即命中并显示 "Christmas" 组, 改回当前日期后恢复进入前的 "Normal" 组 (附录 C 第 10 项). 2026-10-07.
- [x] P3.6 编辑器与摘要支持上述全部类型; 切换状态源时按状态 key 迁移已有颜色, 无法对应的状态以首个状态补齐并提示; 字符串, changelog (improvement: 智能颜色库支持的条件), 冒烟 (附录 C 第 10 项). 证据: 编辑器状态源列表列出全部 12 种, 可设置的状态源先打开设置 (`IntelligentConditionDialogs`: 时段 / 每日定时的时刻列表 (点击修改, 长按删除, 系统时间选择器), 日出日落位置 (按时区推断并显示城市坐标或 "估算", 或手动输入坐标), 季节半球, 电量阈值); 换组时机增加 "每日定时"; 新增 "日期覆盖" 区 (说明文字, 添加 / 编辑 / 删除, 拖动排序, 每行显示目标组的颜色与名称; 日期区间以 月-日 输入并可指定仅一次的年份, 节日按推荐顺序选择并设置提前 / 延后天数; 删除组时一并移除指向它的覆盖层). 切换状态源时两个状态源有相同 key 则按 key 迁移颜色, 否则按位置迁移, 组不会丢失全部颜色, 缺色的状态由校验器提示; 摘要与标签: 状态源名称与设置详情, 状态标签 (时段 "06:00 - 18:00", 星期名称, 季节, 电量 "20%-79%", 充电中 / 未充电等), 每日定时 "每天 08:00, 20:00" 或 "每天 N 次", 卡片追加 "+ 覆盖 N"; 详情页显示生效中的日期覆盖, 下次换组时间取 `earliestDueAt` 且覆盖期间不显示. 字符串: P3 新增 70 条字符串与 2 组复数 x 11 个文件. changelog: 按 `AGENTS.md` 合并同一功能的实现, 改写 v6.8.0 中智能颜色库的 feature 条目为最终行为 (全部状态源, 每日定时, 最短 5 秒, 日期覆盖层与农历节日), 不另起 improvement; markdown 离线生成, README 只保留改写的条目. 冒烟 (emulator-5554, API 37): 导入含电量 / 充电 / 节日覆盖层三个智能库的文件, 卡片摘要依次为 `[电量 | 1 组 | 顺序切换]`, `[充电 | 1 组 | 顺序切换]`, `[无 | 2 组 | 顺序切换 + 覆盖 1]`; 编辑器中把状态源改为时段 06:00 / 18:00 / 19:00 后组颜色按位置迁移, 组编辑器状态标签为 "06:00 - 18:00" 等; 覆盖层编辑对话框与按地区排序的节日列表 (en-US 下感恩节在前) 正常. `theme.*` JVM 单测 18 类 122 例 (E1). D23 (旧布局): P3 不改动旧布局. 2026-10-07.

### P4 跟随壁纸与配色衍生

- [x] P4.1 壁纸色来源 `WallpaperColorSource`: API 31+ 读取系统动态配色 (`android.R.color.system_accent1_*` / `system_accent2_*` / `system_accent3_*` 等色调); API 27~30 使用 `WallpaperManager.getWallpaperColors(FLAG_SYSTEM)` (主色 / 次色 / 第三色); API 24~26 不可用. 不读取壁纸位图, 不申请存储权限; 进程存活时通过 `addOnColorsChangedListener` 监听, 冷启动与回到前台时比较指纹后刷新. 证据: `WallpaperColorSource` (`MIN_SDK` 为 27): API 31+ 取 `system_accent1_500` / `system_accent2_500` / `system_accent3_500`, API 27~30 取 `getWallpaperColors(FLAG_SYSTEM)` 的主色 / 次色 / 第三色, 结果去除透明度并去重, 其余版本为空; 不读取位图, 不申请权限. 引擎在激活库为 "跟随壁纸" 时以 `watchWallpaper` 在主线程注册 `addOnColorsChangedListener`, 停用时注销; `onActivityResumed` 时比较种子色指纹 (`fingerprint`), 变化后重建预设快照并持久化, 再评估一次. 设备行为见 P4.7. 观察: API 31+ 上从非桌面设置壁纸时, SystemUI 把动态配色推迟到下次灭屏 (日志 "processing deferred until screen off"), 颜色监听触发时 `system_accent*` 尚未变化, 刷新由回到前台时的指纹比较完成.
- [x] P4.2 预设 "跟随壁纸": 每个壁纸种子色生成一组 (日 = 对日间背景对比度达标的变体, 夜 = `nightVariant`), 状态源为夜间模式, 无换组时机; 壁纸变化后重新生成并保持 "第 k 组" 的选择; 菜单 "保存为我的智能库" 以当前结果克隆出普通智能库; API 24~26 上预设显示为不可用并说明最低系统版本. 证据: `IntelligentPresets.wallpaper` (`ID_WALLPAPER = 0x102`): 每个种子色一组 (至多 3 组, 组 id 即序号, 名称为 "主色" / "次色" / "第三色"), 日 / 夜颜色取 `ThemeColorDerivation.dayNightPair` (与日 / 夜窗口背景的对比度各不低于 3:1 的最近颜色), 状态源为夜间模式, 无换组时机; 壁纸变化后重建且组 id 不变, 因而保持 "第 k 组" 的选择. 库列表长按菜单与库页面菜单中的 "克隆" 对该预设显示为 "保存为我的智能颜色库". 没有种子色时卡片与库页面附注 "需要 Android 8.1 或更高版本" (API 24~26) 或 "无法读取当前壁纸的颜色" (如动态壁纸不提供颜色). 偏差: 夜间颜色未用 `nightVariant`, 与 D24 一致按对比度取最近颜色, 中等明度的壁纸色日夜保持原色. 单测 `dayNightPairsMeetContrastAgainstBothBackgrounds`.
- [x] P4.3 配色衍生工具 (扩展 `ColorInfoDialogManager` 的颜色详情): 和谐色 (互补 / 类比 / 三角 / 分裂互补 / 四方, OKLCH 色相旋转 + 色域映射), 色阶 (同色相 9~11 级), 对比度 (相对日 / 夜窗口背景的比值, 标注是否达到 3:1 与 4.5:1), 色觉模拟 (红色盲 / 绿色盲 / 蓝色盲, Machado 2009 矩阵); 每个结果可 "应用" / "加入颜色库" / "收藏" (P5 落地后). 纯算法 `ColorHarmony` / `ColorVisionSimulation` 有单测. 证据: 纯算法 `ColorHarmony` (5 种和谐类型, OKLCH 色相旋转, `fromOkLch` 以降低彩度映射回 sRGB; 色阶 10 级, 亮度 0.97~0.32, 两端彩度收窄) 与 `ColorVisionSimulation` (Machado 2009 严重度 1.0 矩阵, 在线性 RGB 中计算, 保留透明度), 单测 `ColorHarmonyAndVisionTest` 4 例. 颜色详情对话框新增 "配色工具" 按钮 (`ColorDerivationDialog`): 和谐色, 色阶, 对比度 (相对 #FAFAFA / #212121 的比值, 标注是否达到 3:1 与 4.5:1, 未达 3:1 时并列最近的达标颜色), 色觉模拟 (红色盲 / 绿色盲 / 蓝色盲); 点击色块可 "应用为主题色" / "加入颜色库" (新建颜色库或选择已有的自建颜色库) / "复制", 点击行标题可 "全部加入颜色库". "收藏" 待 P5.
- [x] P4.4 组编辑器 "自动填充": 根据首个已填状态推导其余状态 (夜间模式用 `nightVariant`, 多状态按色阶分布), 可撤销. 证据: 纯对象 `IntelligentAutoFill.fill(keys, colors)`: 已有颜色的状态不变; 日 / 夜由第一个有颜色的状态取 `dayNightPair` 的另一侧 (D24), 其他多状态的状态源按 `ColorHarmony.tones(seed, n)` 由浅到深分配; 单测 `IntelligentAutoFillTest`. 组编辑器在多状态时显示 "自动填充其余状态", 填充后变为 "撤销自动填充", 再次点击恢复填充前的颜色与 "与 X 相同" 勾选, 之后的任何手动修改使撤销失效. 偏差: 夜间模式未用 `nightVariant`, 理由同 P4.2.
- [x] P4.5 "从种子色生成智能库" 向导: 种子 (当前主题色 / 调色板 / 壁纸 / 收藏) -> 方案 (和谐类型与组数) -> 状态源 (默认夜间模式, 自动填充) -> 预览 -> 创建. 证据: 智能颜色库列表菜单 "从种子色生成...": 种子色 (当前主题色, 调色盘, 可用时逐个列出的壁纸颜色) -> 方案 (5 种和谐色, 组数为其颜色数, 或 5 级色阶) -> 状态源 (夜间模式并自动填充夜间颜色, 或无) -> 预览 (可改名, 默认 "方案名 #RRGGBB", 显示各组分段色块与摘要) -> 创建并打开该库 (`saveIntelligent`, 类型为新建, 来源记录种子色). 纯对象 `IntelligentSeedGenerator` (去除灰色种子产生的重复颜色; 夜间模式时换组时机为 "状态变化", 与编辑器默认一致), 单测在 `IntelligentAutoFillTest`. 组名: 和谐色为 "种子色" 与色相偏移 (如 "+120°", "-30°"), 色阶为 "色阶 1"~"色阶 5" (冒烟后补充). 以 "收藏" 为种子待 P5.
- [x] P4.6 派生配色 (D2, F5): 主题色设置增加 "派生配色" (关 / 自动); 组编辑器中每个状态可展开填写 accent / dark; 审计 `ThemeColor` 三字段的全部消费方并接入 (至少 `ThemeColorFloatingActionButton` 的 accent, 状态栏改用 dark, `OfficialPluginSettingsProvider` 传给插件的三色). 验收: "关" 时截图与升级前一致 (E3). 证据: `ThemeColorManager.isDerivationEnabled` (Pref, 默认关闭) 与 `resolve(primary, accent?, dark?)`, 规则在纯函数 `ThemeColorDerivation.accentAndDark` 中: 显式值优先, 否则自动时派生 (`deriveAccent` / `deriveDark`), 关闭时等于主色; 单色入口 (`ThemeColorCoordinator.apply(Int)`, `ThemeColorManager.setThemeColor(Int)`) 与引擎的应用色和预期色均经 `resolve`. 设置入口为颜色库页面 "更多 > 派生配色..." (说明各颜色的用途), 修改后立即生效, 激活智能库时由 `IntelligentColorEngine.reapply` 以 RESTORE 重新应用当前组 (不换组). 组编辑器中每个有自身主色的状态可展开 "强调色 / 深色", 未指定时显示该设置给出的颜色并注明 "跟随派生配色", 可选色或重置. 消费方审计: accent / dark 此前只由 `ThemeColorFloatingActionButton` (accent) 与 `OfficialPluginSettingsProvider` (读取持久化的三色) 读取, 二者无需改动; 状态栏 (`StatusBarManager` 与 `BaseActivity` 自动着色的页面) 改用 dark, 其图标明暗也按 dark 判断; 主页与主题色页面的状态栏透明并透出应用栏 (主色), 不变; 脚本可经 `getColorAccent()` / `getColorPrimaryDark()` 读取. 单测 `derivationOffKeepsTheSingleColorTheme` (216 色网格, 关闭时三色相等, 显式值在两种设置下均优先). E3 见 P4.7.
- [x] P4.7 阶段收尾: 字符串, changelog (feature: 跟随壁纸, 配色衍生工具, 派生配色), 冒烟 (附录 C 第 11~12 项, 覆盖 API 26 / 27 / 31 / 33+). 证据: 字符串: P4 新增 49 条字符串与 2 组复数, 11 个文件均无缺失, 非英语文件中与英语相同者仅为法语同形词 ("Accent", "Harmonies"). changelog: v6.8.0 各语言改写 "智能颜色库预设" 一条 (增加 "跟随壁纸", 从种子色生成与自动填充), 追加 feature 2 条 (配色工具; 派生配色); markdown 离线生成, README 只保留改写与新增的条目行, 还原两处日期. `theme.*` JVM 单测 132 例 (E1). 冒烟 (2026-10-07): 第 12 项 (E3, emulator-5554, API 37): 先在升级前的构建 (P3) 上以 SystemUI 演示模式固定状态栏, 截取主页 (含悬浮按钮), 设置页与主题色页, 覆盖安装 P4 后保持 "派生配色" 关闭再截取: 设置页逐像素一致, 主页与主题色页的差异仅为信号格图标 (演示模式未固定) 与主题色页 "智能颜色库" 的条目数 8 -> 9 (新增预设); 改为 "自动" 后三色为 #1565C0 / dark #004E9E / accent #0B69CD, 设置页状态栏变为 #004E9E, 悬浮按钮由 #165EAF 变为 #0D61BB (按钮自身的对比度调整之后), 激活的智能库保持当前组; 改回 "关" 后三色恢复相等, 三个页面与升级前相比仍只差信号格图标. 第 11 项: API 26 (临时 AVD, 用后删除): 卡片与库页面注明 "需要 Android 8.1 或更高版本", 0 组; API 27 (AVD_API_27): 首次启动时默认壁纸的 `getWallpaperColors` 为 null, 0 组; 经脚本设置纯红壁纸后为 1 组 #C62827 / #CA2D2A, 激活后停留在主页设置纯蓝壁纸, 由颜色监听刷新为系统量化后的 #0F1598 (期间主页未经历恢复); API 28 (Sony G8441, 动态壁纸不提供颜色): 卡片注明 "无法读取当前壁纸的颜色"; API 31 (Sony XQ-AT72), 33 (Xiaomi 22120RN86C), 35 (Xiaomi 23046RP50C): 各得到 3 个系统强调色 (如 #007FAC / #657985 / #787296); API 37 (emulator-5554): 激活 "主色" 后设置纯红壁纸, 系统推迟动态配色, 回到前台后预设刷新为 #A06963 / #926F6B / #96703D, 离开主题色页面后主题色变为 #A06963 (D6); 再设置纯绿壁纸并灭屏亮屏后主题色变为 #607F5B. 实体设备只检查可用性, 不更换壁纸 (无法备份用户的壁纸). 另验证: 种子色向导 (三角 + 夜间模式; 类比 + 无), 组编辑器的自动填充与撤销, "强调色 / 深色" 面板, 配色工具各部分与色块操作. D23 (旧布局): P4 未改动旧布局, 抽屉摘要仍为 "Intell.: P3 Holiday [#1565C0]", 布局可来回切换; 旧布局中选色同样经 `resolve`, 受派生配色设置影响. 测试后删除测试库, 恢复模拟器壁纸与原激活库, AVD_API_27 恢复默认壁纸并清除应用数据, 实体设备主题色未改动. 冒烟修正: 生成的组没有名称时库页面重复显示色值, 改为按方案命名 (`150b4210f0`). 提交: P4.3 `69dec79d0b`, P4.1 / P4.2 `21cc6846a8`, P4.4 / P4.5 `73e2005c6f`, P4.6 `8a8d29f84d`.

### P5 推荐与收藏

- [x] P5.1 收藏与置顶: 颜色条目长按或详情对话框中 "收藏" / "取消收藏", 收藏可 "置顶"; 颜色库列表增加虚拟库 "收藏" (排序: 手动 / 收藏时间 / 色相 / 亮度), 提供 "从收藏创建智能库" (每色一组, 其余状态自动填充). 证据: 收藏入口: 颜色详情数值下方的 "收藏" / "置顶" 复选框 (新增通用接口 `InfoDialogItems.addCustom`), 预设颜色库条目长按 (`ColorItemAdapter` 新增长按回调) 与自建库颜色长按 (`showColorActions`, 原有的重命名 / 修改色值 / 删除之后追加收藏操作与颜色详情), 配色工具色块操作 "收藏" (补齐 P4.3). 仓库: `addFavorite` (按不透明色值唯一, 追加到手动顺序末尾), `removeFavorite` (同时删除其历史记录), `setFavoritePinned`, `reorderFavorites`. 虚拟库 "收藏" 的库 id 为 0x23, 新增选中类型 `ColorSelection.Favorite` (条目 id 为收藏行 id): 摘要为 "收藏 | #RRGGBB", "定位当前主题色" 打开收藏页并滚动到该项, 历史条目可解析收藏, 取消正在使用的收藏时选中状态退回调色板. 收藏页 (`VirtualColorLibraryActivity`): 排序 (`FavoriteSort`: 手动 / 收藏时间 / 色相 / 亮度, 置顶项总在最前; 色相排序中 OKLCH 彩度低于 0.02 的近灰色排在最后并由浅到深), 拖动排序 (切换为手动, 只能在同为置顶或同为非置顶的条目之间移动), 从调色盘收藏, "从收藏创建智能颜色库" (`IntelligentSeedGenerator.fromColors` 每色一组, 经种子色向导的状态源与预览步骤创建, 组名取颜色名称); 收藏没有名称列 (schema v2), 显示时取同色值预设颜色的名称, 否则为色值. 收藏同时作为 "从种子色生成" 的种子 (补齐 P4.5). 两个虚拟库的图标沿用颜色库图标的盒子样式 (星形 / 上升折线).
- [x] P5.2 使用统计 (D10, D12): `USER` / `SCRIPT` 应用颜色时 `use_count` 加 1 并刷新 `last_used_time`, 智能引擎应用的颜色不计; 纯对象 `ColorUsageScorer` (使用次数取对数并按近因指数衰减, 半衰期默认 14 天; 收藏与置顶加权). 验收: 单测覆盖排序稳定性与衰减. 证据: `ColorHistoryDao` / `PaletteHistoryDao` 的 `upsert` 对已有行 `use_count + 1`, 并更新为最近的时间, 来源与来源说明; `saveDatabaseForColorHistory` / `saveDatabaseForPaletteHistory` 增加 `source` 参数 (默认 USER, P6 的脚本传 SCRIPT); 智能引擎不写历史 (D10), 因此不计入. 纯对象 `ColorUsageScorer`: `ln(1 + 次数) x 0.5^(距上次使用 / 14 天)`, 收藏加 1.0, 置顶再加 1.0, 未来时间不加分; 按不透明颜色合并记录 (次数相加, 取最近时间); 排序为评分降序, 再按最近使用时间降序与颜色值升序, 与输入顺序无关. 单测 `ColorUsageTest` 6 例 (衰减: 14 天减半, 28 天为四分之一; 加分; 合并; 打乱输入顺序后排序不变; 聚类边界; 推荐; 收藏排序). 真机测试 `ColorUsageDatabaseTest` 2 例 (两次 upsert 后次数为 2, 第三次以 SCRIPT 记录后次数为 3 且来源更新; 调色板历史计数; 收藏唯一, 不透明, 置顶在前, 重排位置, 取消收藏删除其历史) 在 emulator-5554 (API 37) 通过.
- [x] P5.3 相似色聚类: 纯对象 `ColorClusterer` (dE_ok 阈值, 贪心聚类, 代表色取分数最高者), 用于推荐与颜色历史列表的 "合并相似颜色" 开关. 验收: 单测覆盖阈值边界与稳定性. 证据: 纯对象 `ColorClusterer`: 按优先级顺序贪心聚类, 与簇代表的 dE_ok 严格小于阈值 (默认 0.04, 约两个 OKLab 最小可觉差) 时并入, 否则自成一簇, 代表即簇内优先级最高 (最先) 的条目; 锚点条目 (收藏) 总是自成一簇但可吸收之后的条目. 单测覆盖阈值边界 (距离恰好等于阈值时不合并, 略大于距离的阈值合并) 与代表的稳定性. 颜色历史对话框顶部新增 "合并相似颜色" 复选框 (默认关闭, 记忆于偏好设置): 开启后相似条目并入最新的一条, 来源后显示 "+N 相似".
- [x] P5.4 虚拟库 "常用与推荐": 置顶收藏 -> 其余收藏 -> 按评分排序的历史色 (聚类去重后), 最多 30 项, 无数据时隐藏; 支持 "从推荐创建智能库". 证据: 纯对象 `ColorRecommender`: 置顶收藏 -> 其余收藏 -> 按 `ColorUsageScorer` 评分排列的历史颜色 (全部颜色库与调色板), 以收藏为锚点聚类去重, 至多 30 项; 同一颜色取最近一条记录决定其应用方式. 虚拟库 "常用与推荐" 的库 id 为 0x24, 无数据时在颜色库列表中隐藏; 条目显示 "所属库 · 使用 N 次", 未命名的调色板颜色取同色值预设颜色的名称; 点击按原来的选择方式应用 (`applyHistoryItem` 移至基类, 期间已取消的收藏按调色板颜色应用), 长按可应用, 加入颜色库, 收藏 / 置顶或查看详情; 菜单 "从推荐创建智能颜色库".
- [x] P5.5 阶段收尾: 字符串, changelog (feature: 颜色收藏, 常用与推荐), 冒烟. 证据: 字符串: P5 新增 24 条字符串与 2 组复数, 11 个文件均无缺失, 非英语文件中与英语相同者仅为西语同形词 "Manual"; 已有的 `text_pinned` ("已固定") 与 `text_unpin` 用于其他功能, 本阶段另用 `text_favorite_pinned` / `text_unpin_favorite` 以保持 "置顶" 的用词. changelog: v6.8.0 各语言追加 feature 2 条 (颜色收藏; 常用与推荐及使用计数与合并相似颜色); markdown 离线生成, README 只保留新增条目行并还原两处日期. `theme.*` JVM 单测 139 例 (21 个类, E1). 冒烟 (2026-10-07): emulator-5554 (API 37, E2): 颜色库列表在智能颜色库之后出现 "收藏" (0 项) 与 "常用与推荐" (3 项: #000080 调色板 / Red (500) Material / Background night 默认, 各使用 1 次); 在推荐中长按 Red (500) 收藏后该项移至最前并标注 "收藏", 其 Material 历史并入; 点击后选中库 id 为 0x23, 条目 1, 摘要为 "收藏 | " 加 Red (500) 的色值, 原激活的智能库停用; 颜色详情中 "收藏" 为勾选, 勾选 "置顶" 后列表标注 "置顶"; 在 Material 颜色库长按收藏 Red (50) / Red (900) / Pink (A200), 亮度排序为 Red (500, 置顶) / Red (50) / Pink (A200) / Red (900), 色相排序为 Red (500) / Pink (A200) / Red (50) / Red (900), 将 Pink (A200) 拖到 Red (50) 之前并在完成后保持; 以夜间模式从收藏创建智能颜色库, 得到 4 个以颜色名称命名的组 (Red (900) 为 #B71C1C / #C8302B); 历史对话框开启 "合并相似颜色" 后 Red (500) 显示 "收藏 +1 相似". Sony G8441 (Android 9, API 28, E3): 列表出现 "收藏" (0 项) 与 "常用与推荐" (1 项: Navajo white, 默认 · 使用 1 次); 长按 Material 的 Red (300) 收藏后显示于收藏页, 随后取消收藏, 主题色保持 #FFDEAD. 冒烟中修正 (已含于提交): 推荐条目的来源与次数之间加分隔符 " · ", 未命名调色板颜色取预设名称, 两个复选框的间距, 排序对话框的标题与当前选项预选. 测试后删除测试收藏与生成的智能库, 模拟器恢复原激活库 "P3 Holiday". 提交: `7283bd022b`.

### P6 脚本 API

- [x] P6.1 `colors` 模块算法函数 (命名见 Q1, 避开 F10 的既有名称): `toOklch` / `oklch`, `toOklab` / `oklab`, `ensureContrast`, `harmony`, `tones`, `derive` (返回 primary / accent / dark), `nightVariant`, `simulate`, `extract` (输入 `ImageWrapper`, 选项 count / region / 采样步长; 纯 Kotlin Wu 量化 + k-means 精修); `colors.distance` 增加 `oklab` 算法 (是否同步到 `images` 找色见 Q2). 验收: Rhino 层参数校验与单测, 量化器有 JVM 单测. 证据: `colors` 新增 11 个函数 (toOklab / oklab, toOklch / oklch, ensureContrast, harmony, tones, derive, nightVariant, simulate, extract). 命名按 D17 对照 d.ts 惯例核对: 与既有 `hsl` / `toHsl` 的 "构造 / 分解" 配对一致, 不与 F10 列出的函数重名. 构造函数接受分量或分量数组及可选 alpha (小数或 0..255), L 可写作百分比; 超出 sRGB 时保持亮度与色相并降低彩度. ensureContrast 默认 4.5 (WCAG AA 文本), 背景须不透明, 保留原 alpha; harmony 的 type 为 complementary / analogous / triadic / splitComplementary / tetradic; tones 为 2~20 级 (默认 10); derive 返回 { primary, accent, dark }, 与派生配色 "自动" 一致; simulate 的 deficiency 为 protanopia / deuteranopia / tritanopia; extract(image, { count 1~16 默认 5, region 同 images.findColor, step 默认约 65536 个采样 }) 按占比降序返回颜色, 忽略 alpha 低于 128 的像素, 结束后对单次图像调用 shoot. 纯 Kotlin `ColorQuantizer`: Wu 量化 (5 位 RGB 直方图, 累积矩) 加 OKLab 加权 k-means (至多 10 轮), 单测 5 例 (两色精确比例, 六色还原, 透明像素, 灰阶的确定性与四等分, 数量上限). `ColorDetector` 新增 "oklab" (dE_ok x 255, 黑白距离为 255; 查找表加局部变量, 无内存分配且线程安全), 因此 colors.isSimilar / colors.distance / images.detectColor 均可使用. D18 性能对比 (JVM, 1080 x 2400 个随机像素, 每像素纳秒): diff 4.98, rgb 与 rgb+ 12.19, h 18.44, hs 20.53, oklab 31.14, 约为现有最慢算法 hs 的 1.5 倍, 达标线取 "不超过现有最慢算法的 2 倍"; 另核对 images.findColor / findMultiColors 固定使用 diff 检测器且无算法参数, 不涉及找色热路径. Rhino 层参数校验与结果的单测 `ColorsPerceptualTest` 4 例 (`toIntRhino` 依赖 `android.graphics.Color`, 测试调用以 Int 为输入的内部函数). 设备: 冒烟脚本 `colors-functions.js` 的 21 项在 emulator-5554 (API 37) 与 Sony G8441 (API 28) 上全部通过. 提交 `39c72a143f`.
- [x] P6.2 主题色控制 `colors.theme` (Q1): `get()`, `set(color, options)` (options: accent / dark; `USER` 语义, 停用智能库, source = `SCRIPT`), `reset()`, `libraries()` (库摘要: id / name / kind / intelligent / 组数), `activate(libraryId, options)` (options: group), `deactivate()`, `next()` (立即换组, 不要求勾选 "脚本" 换组时机), `state(name)` (设置 "脚本" 状态源的当前状态, 未知状态名抛出异常), `current()` (库 / 组 / 状态 / 颜色). 编辑器增加 "脚本" 状态源 (声明 2~8 个命名状态) 与 "脚本" 换组时机. 证据: `ColorsTheme` 以 `ColorsTheme(this).augment(colors)` 挂在 `colors.theme`: get (主色, 含合并窗口中待生效的脚本颜色), set(color, { accent, dark }), reset (默认主题色), libraries (预设普通库, 预设智能库与用户库的 { id, name, kind (preset / created / imported / cloned), intelligent, size }), activate(libraryId, { group }) (只接受智能库, 组须启用且有颜色), deactivate (保留当前颜色, 选中状态转为调色板), next (立即以 SCRIPT_NEXT 换组, 无需换组时机; 主题色页面打开期间不变化, D6), state(name) (激活库须使用 "脚本状态" 状态源, 未知名称抛出异常), current ({ libraryId, libraryName, intelligent, groupId, groupName, state, color, accent, dark, summary }); 涉及引擎的调用在主线程执行并等待结果 (超时 10 秒). 模型: `StateSource.ScriptStates` (2~8 个名称, 去除空白并去重; 状态键按位置为 `script:i`, 改名保留颜色; 当前状态名持久化, 未知时取第一个) 与 `RotationTrigger.ScriptStart` (脚本启动时换组; 引擎仅在勾选时注册脚本引擎生命周期回调, 同时启动的多个脚本合并为一次). JSON 类型 `scriptStates` / `scriptStart`; 编辑器状态源列表增加 "脚本状态" 及其名称对话框, 换组时机增加复选框 "脚本启动"; 摘要与状态标签同步. 解释: 路线图中的 "脚本" 换组时机实现为 "脚本启动时换组", 因为 next() 不依赖任何时机, 若该时机仍指 next() 则没有独立作用. 单测: JSON 往返与规范化, 状态解析, 规划器只在勾选时于 SCRIPT_START 换组. 设备: `colors-theme-control.js` 的 9 项在 emulator-5554 上通过 (18 个库, 其中 10 个智能库; 激活 "材料设计日夜配对" 得到 Red, next 后为 Pink; 脚本状态 busy #003168 / idle #1565C0; 停用; reset 后为 #FFDEAD). 提交 `8509e3714d`.
- [x] P6.3 限频, 来源与回退 (D11): 合并窗口 500 ms; 颜色历史显示来源 "脚本: 名称"; 最近一次修改来自脚本时主题色页面菜单显示 "撤销脚本修改" (恢复修改前的 `ColorSelection` 与智能库激活状态). 验收: 单测覆盖合并与回退快照. 证据: 合并沿用 P0 的 `ThemeColorApplyPolicy` (500 ms 固定窗口, 既有单测 `scriptRequestsInsideAWindowCollapseIntoTheLast` 等); set / reset 经 `ThemeColorCoordinator.applyFromScript` 携带脚本名称. 提交观察者 `ScriptThemeChanges`: 脚本颜色生效后选中状态转为调色板, 调色板历史的来源为 SCRIPT 并记录脚本名称, 历史列表显示 "调色板 · 脚本: 名称"; 一连串脚本修改中第一次之前保存撤销快照 (选中状态的原始值与名称, legacy 索引与自定义色, 三色, 激活智能库的快照与运行状态), 用户选择, 激活或恢复时丢弃; 快照存在时主题色页面菜单显示 "撤销脚本修改": 当时有激活库则按原组重新激活, 否则恢复选中状态并以 RESTORE 应用原三色. 纯对象 `ScriptUndoSnapshot` (JSON 编解码) 与 `ScriptUndoRules`, 单测 2 例. 设备 (附录 C 第 13 项): emulator-5554 上 "P3 Holiday" 激活时运行 `colors-theme-loop.js`, 100 次 set 后只生效最后一个颜色 #1D247A, 智能库停用, 历史只新增一条 "调色板 · 脚本: colors-theme-loop"; 菜单出现 "撤销脚本修改", 撤销后 "P3 Holiday" 恢复激活 (组 Normal, #1565C0). Sony G8441 上从 "默认 | Navajo white" 运行同一脚本, 撤销后恢复原选择与名称; 测试后删除该设备上的测试历史与脚本文件. 提交 `8509e3714d`.
- [x] P6.4 关联项目同步 (D16, `AGENTS.md` "关联项目"): 文档项目 `colors` 章节新增函数与 `colors.theme`, 运行 `auto-generate-for-autojs6.bat`; d.ts 声明运行 `aj6dts.bat -Publish`, 两个仓库版本号加一并合理递增版本名称, 同步 `aj6-int-*.d.ts`, Ace Editor 仓库执行 `:app:generateAutoJs6LspDeclarations`; 按需在 "示例代码" 中加入主题色轮换与取色示例; 各仓库分别提交. 进展 (2026-10-07): 文档项目 `api/color.md` 新增 oklab / oklch / toOklab / toOklch, ensureContrast, harmony, tones, derive, nightVariant, simulate, extract 与 `colors.theme` 各成员, `dataTypes.md` 的 ColorDetectionAlgorithm 增加 oklab, 示例结果在 emulator-5554 上逐项核对; BAT `--verify-offline` 同步离线文档插件 (单元测试与 debug / release APK 门禁通过, 212 个文件), 文档项目 versionCode 91 -> 92, 插件 VERSION_BUILD 74 -> 75, provenance 记录 `4a87cb1`; 提交: 文档 `247322f`, `4a87cb1`, `7666a59`, `653f13f`, 离线插件 `1167c9e`. d.ts: `aj6-int-colors.d.ts` 声明新函数, `colors.theme` 与 oklab, 编译 smoke `colors-smoke.ts` (含 12 处预期错误) 经 TypeScript 5.1.3 验证; `aj6dts -Publish` 以 master 工作树为来源重新生成 main-app / resources / libraries 并通过 tsc (暂不发布到 Ace); 版本 4.31.0 -> 4.32.0, 提交 `9072042`, `2a27cdf`. 示例代码 > 图像与颜色 新增 3 个脚本 (感知配色, 截图取色作为主题色, 智能颜色库轮换), 在 emulator-5554 上运行通过, changelog 的两条 colors feature 补充示例引用. Ace Editor (另一任务提交后进行): 逐字节镜像声明 4.32.0 (colors, terminal 及其全局变量, main-app / resources / libraries), 精选 index 增加 terminal 引用, 捆绑元数据记为 4.32.0; `verify-runtime.mjs` 新增 colors / colors.theme / terminal 检查组 (带前缀的补全, 合法输入 0 诊断, 6 类非法输入均报错); `:app:generateAutoJs6LspDeclarations` 与 `verifyAutoJs6LspRuntime` 通过, LSP manifest 记录 4.32.0, JVM 测试 171 例 (32 个类) 与 debug APK 及 16 KB 对齐通过; 版本 1.24.0 -> 1.25.0, VERSION_BUILD 136 (等于提交数), changelog v1.25.0 十种语言; 提交 `c5d77a5`.
- [x] P6.5 阶段收尾: changelog (feature: `colors` 新增函数与 `colors.theme`), 冒烟脚本 `docs/dev/smoke/intelligent-color-library/` (附录 C 第 13 项). 证据: changelog: v6.8.0 改写 "智能颜色库" 条目 (状态源增加 "脚本状态", 换组时机增加 "脚本启动"), 追加 feature 2 条 (colors 感知色彩函数; colors.theme); markdown 离线生成, README 只保留改写与新增的条目行. 字符串: P6 新增 7 条, 11 个文件均无缺失; 繁体 (台湾) 的 "腳本執行" 改为 "指令碼執行", 与该文件的既有用词一致. 冒烟脚本位于 `docs/dev/smoke/intelligent-color-library/`: `colors-functions.js`, `colors-theme-loop.js` (附录 C 第 13 项), `colors-theme-control.js` (脚本状态部分需要名为 "Script smoke" 的库); 结果见 P6.1~P6.3. `theme.*` JVM 单测 149 例. P6.4 尚未进行: 文档项目, d.ts 项目与 Ace Editor 仓库中已有与本功能无关的未提交改动 (terminal 模块的文档与声明, 2026-10-05), 按 `AGENTS.md` 运行生成脚本并整体提交会把这些改动一并提交, 待维护者决定处理方式.

### P7 质量, 验证与文档

- [x] P7.1 单测补洞: 所有纯对象的分支覆盖复核, 重点是 `IntelligentColorPlanner` 与 `ColorSelectionCodec`. 证据: 临时开启 JaCoCo (`enableUnitTestCoverage`, 未提交) 统计 `theme.*` 纯对象的分支覆盖率为 85.6%, 未覆盖的分支对应真实行为, 据此补测: 规划器 (覆盖层指向无色组或未知组, 覆盖期间当前组被删除, 无可用组时换组, 随机切换只剩一组, 色值排序读取参照状态, 名称排序退回色值, 运行状态缺少到期时间, 重新激活时忽略已过期的每日定时), 选中状态编解码 (收藏, 分段边界, 非法行 id, 负的 legacy 索引), 配置模型 (新类 `IntelligentConfigModelTest`: 各状态源的状态 key 作为持久化约定, 规范化, 切换状态源后组颜色的回退, 不受支持项), 配置 JSON, 快照与交换格式 (未知状态源往返, 格式错误的条目, 无效日期 / 时刻 / 坐标, 类型错误的运行状态字段, schemaVersion 0 与不完整的元数据). 分支覆盖率升至 91.2% (`IntelligentColorPlanner` 86.3% -> 96.6%, `ColorSelectionCodec` 88.0% -> 100%, `IntelligentConfig` 76.8% -> 100%), 其余为短路组合与不可达路径; `theme.*` JVM 单测 170 例 (E1). 提交 `b6e18d8dc1`.
- [x] P7.2 Room 迁移真机回归: 安装含颜色历史数据的旧版本后升级, 执行 `ColorHistoryDatabaseMigrationTest` 并人工确认历史列表 (附录 C 第 14 项; 注意 `connectedAndroidTest` 结束后会卸载被测应用, 需加 `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`). 证据: 升级路径在自建临时 AVD (API 34, 用后删除) 上执行: 安装 P0.3 之前的 `3089662e35` (Room v1) 后经界面选择 Material Red 500 / Red 700 并用调色盘选 #358BB7, 只读导出得到 `user_version` 1, `color_history` 3 行, `palette_history` 1 行; 覆盖安装当前构建后摘要仍为 "Palette | #358BB7", "全部历史记录" 列出 3 项且时间与来源不变, 导出得到 `user_version` 2, 4 行均保留并取默认值 (`use_count` 1, `source` USER, `source_detail` null), 新表 5 张. 实体机已装新版本, 降级需卸载并清除数据, 因此升级路径未在实体机上执行. `ColorHistoryDatabaseMigrationTest` 以仅安装测试 APK 并 `am instrument` 的方式运行 (不重装被测应用, 无需 leaveApksInstalled): API 34 AVD 通过; Sony G8441 (API 28) 上发现测试自身的问题 (`SQLiteOpenHelper` 自 API 29 起才实现 `AutoCloseable`, `use` 抛出 `ClassCastException`), 改为显式关闭后 2 例通过 (E3), 提交 `b2819dd2e1`.
- [x] P7.3 附录 C 冒烟清单全部执行, 记录机型与 API level. 证据: 在最终构建 (含 P7.4 的冷启动改动) 上重新执行. 第 1 项 (emulator-5554, API 37) 新建 "P7Smoke" 并从 Android 颜色库添加 Maroon / Navy / Olive, 应用 Navy 后冷启动, 选中状态与摘要为 "P7Smoke | Navy [#000080]"; 删除该库后主题色不变, 摘要为 "Palette | #000080". 第 2 项 克隆 Material 颜色库并把副本第一项改名为 "P7Renamed", 原库第一项仍为 "Red (50)" (之后删除副本). 第 3 项 (Sony G8441, API 28, E3) 激活 "材料设计日夜配对" 的 Teal 组: 应用设置常开 / 常关分别为 #00796B / #009688, 跟随系统时 `cmd uimode night yes / no` 同样切换. 第 4 项 经仓库创建 "P7 Interval" (无 + 每 1 分钟, 下一个整分, 3 组): 14:31 整分 Red -> Green; 主题色页面打开期间跨过两个整分不变, 离开后只补一次. 第 5 项 "P7 Random" (夜间模式 + 状态变化, 5 组随机) 连续切换 6 次得到 Cyan / Green / Orange / Cyan / Purple / Green, 无连续重复. 第 6 项 强制停止 150 秒 (错过 3 个整分) 后冷启动只前进一组 (Green -> Blue). 第 8 项 开启旧布局设置时摘要为 "Intell.: P7 Interval [#43A047]" 且旧布局无高亮, 在旧布局选 Red (500) 后智能库停用 (摘要 "Red (500) [#F44336]"), 还原设置后新布局摘要为 "Material | Red (500) [#F44336]". 第 10 项 电量 40 / 51 / 52 / 49 / 48 / 47 依次为 battery:0 / 0 / 1 / 1 / 1 / 0, 充电 ac 1 / 0 为 #F9A825 / #455A64, `cmd alarm set-time` 改到 12-25 命中 "Christmas", 改回后恢复 "Normal". 第 13 项 循环 100 次 set 只生效 #1D247A, "撤销脚本修改" 恢复 "P3 Holiday | Normal". 第 14 项见 P7.2; 第 15 项见 P7.4 (最终构建复查: alarm 无条目, job 只有脚本历史, 回收站清理与定时任务的 Worker). 第 7 / 9 / 11 / 12 项 (背景色对话框与升级保持, 导入导出, 跟随壁纸的各 API 可用性, 派生配色关闭时截图一致) 涉及的代码 P7 未改动, 沿用 P1.8 / P2.6 / P4.7 的证据; 其中跟随壁纸受 P7.4 的预设延后重建影响: 进程停止期间更换壁纸时冷启动首帧仍为旧颜色, 随后更正 (已写入架构文档). 两次假失败来自测试环境: 模拟器的 "System UI isn't responding" 对话框拦截返回键, 以及旧布局页面未关闭使引擎按 D6 暂停; 排除后均通过. 测试后删除冒烟库与脚本留下的历史, 恢复各设备的选择, 夜间设置与系统深色模式.
- [x] P7.4 性能与耗电: 冷启动评估耗时 (只读快照, 目标不超过 2 ms), 主题色切换的掉帧情况; `dumpsys alarm` / `dumpsys jobscheduler` 中无本功能产生的条目 (D5). 证据: 新增默认关闭的计时日志 (`adb shell setprop log.tag.IntelligentColorEngine DEBUG`), 在 Sony G8441 (API 28) 上激活 "材料设计日夜配对" 后测冷启动评估: debug 构建 87 ms, release 构建 11.7 ms, 远超目标. 细分计时定位到: 判断选中状态时加载选择页面与 Material 颜色表 (约 19 ms), 首次获取系统时区加载时区数据 (约 10 ms), 创建 `Collator` (约 8 ms), 重复写入未变化的运行状态 (约 6 ms), 首帧前重建预设 (约 25 ms), Gson 解码的类加载 (约 8 ms). 改为: 直接比较保存的库 id (D9 分段), 不依赖本地时间的配置用 UTC, `Collator` 延迟创建, 只写有变化的状态与名称, 预设在首帧后重建 (冷启动后验证: 过期的快照名称被自动更正), 以及 `App.onCreate` 开头的 `IntelligentColorEngine.preload()` 在后台线程解码并空跑规划器与状态解析. 结果: release 构建评估中位数 1.66 ms (9 次冷启动, 最大 1.76 ms), Activity TotalTime 约 365 -> 345 ms. 掉帧: 同一设备主界面上每 1.5 秒换组 10 次, 13 帧中 11 帧超时 (中位 30 ms, 95% 分位 57 ms), 不换组的对照 26 帧无超时; 即每次换色带来 1 个慢帧, 来自整页控件重新着色 (手动选色同一路径), 换组为低频事件, 本次不处理. `dumpsys alarm` 无 AutoJs6 条目, `dumpsys jobscheduler` 只有 HistoryCleanupWorker (脚本历史), TmpScriptFilesCleanupWorker 与定时任务的 CheckTasksWorker, 均与本功能无关 (emulator-5554, API 37, 激活含日期覆盖层的 "P3 Holiday"; 附录 C 第 15 项). 提交 `8a5a5913e5`.
- [x] P7.5 架构文档 `docs/dev/intelligent-color-library-architecture.md`: 数据模型与迁移, 引擎事件与状态机, 协调器来源规则, 暂停机制, 快照格式, 可测性. 证据: `docs/dev/intelligent-color-library-architecture.md`: 分层与依赖约束, 数据模型与 v1 -> v2 迁移, 选中状态与 ID 分段, 协调器各来源的生效 / 暂停 / 停用 / 历史规则与提交顺序, 暂停机制, 引擎事件与规划规则, 停用条件, 冷启动 (预加载, 延后重建预设, 计时开关与实测), Pref 快照与配置 JSON 格式, 状态源与日期, 脚本接口, 可测性 (测试类一览), 扩展指南 (新增状态源 / 换组时机 / 预设 / 数据库迁移).
- [x] P7.6 changelog 终稿整理 (按 `AGENTS.md` 合并同一功能的连续实现为最终行为), 本 Roadmap 收尾与证据回填. 证据: changelog: v6.8.0 各语言把 colors.distance/invert/blend/contrast 一条移到感知色彩函数之前 (同模块相邻, `AGENTS.md` 第 5 条), 感知色彩函数一条去掉开头的"新增" (简繁中文与日韩); 本功能其余条目已在 P1~P6 中按最终行为合并, 复核无重复与前后冲突; markdown 离线生成, README 只保留条目行并还原两处日期刷新. 本 Roadmap: P0~P7 全部勾选并回填证据, 开头注明完成状态与架构文档入口.

---

## 附录 A. 待决事项

2026-10-07 维护者已全部拍板: Q1~Q6 采用下表建议, Q7 改为长期保留旧布局并按需兼容, Q8 改为按对比度自动挑选; 结论见 D17~D24. 下表保留原始问题与建议以供追溯.

| 编号 | 问题 | 建议 |
| --- | --- | --- |
| Q1 | 脚本 API 的命名空间与函数名: `colors.theme.*`, 独立 `themes` 模块, 还是让 `autojs.themeColor` 可写? | 建议 `colors.theme.*`: 与既有 `colors.themeColor` 同处一个模块, 不新增全局名. 算法函数名在 P6.1 开工前与 d.ts 命名惯例一并确认. |
| Q2 | `colors.distance` 新增的 `oklab` 算法是否同步到 `images` 找色 (共用 `ColorDetector`)? | 建议同步但默认算法不变: 找色热路径需单独做性能对比, 不达标则只在 `colors.distance` 中提供. |
| Q3 | 时间间隔的下限: 旧思路允许 "秒". | 建议下限 5 秒, 低于 1 分钟时编辑器提示. 秒级主要用于演示与调试. |
| Q4 | 日出日落与季节半球的地理信息来源 (不申请定位权限). | 建议由时区推断代表城市坐标 (内置 IANA 时区 -> 坐标表), 允许手动输入经纬度覆盖. |
| Q5 | 节日表的首批内容与地区判定. | 建议: 公历通用节日 (元旦 / 情人节 / 万圣节 / 圣诞节) + 中国农历节日 (春节 / 元宵 / 端午 / 七夕 / 中秋 / 重阳) + 少量地区节日; 按系统地区默认启用, 用户可增减. |
| Q6 | inrt (APK Builder 打包应用) 中 `colors.theme` 的行为. | 建议算法函数照常可用; 主题色控制只作用于 inrt 自身界面且不持久化; 用户颜色库不随包携带. |
| Q7 | 旧布局的去留 (D13 的长期维护成本). | 建议本 Roadmap 完成后评估移除, 不在本 Roadmap 内处理. |
| Q8 | "Material 日夜配对" 的色阶: 沿用旧思路的 50 / 800, 还是按对比度自动挑选? | 建议沿用 50 / 800 (与旧思路一致, 结果可预期), 克隆后可改. |

## 附录 B. 交换格式草案 (D14, P2.1)

```json
{
  "format": "autojs6.color-library",
  "schemaVersion": 1,
  "exportedAt": "2026-10-06T15:52:00+08:00",
  "app": { "versionName": "6.8.0", "versionCode": 5319 },
  "libraries": [
    {
      "type": "plain",
      "name": "我的品牌色",
      "colors": [
        { "name": "主色", "color": "#00695C" },
        { "color": "#37474F" }
      ]
    },
    {
      "type": "intelligent",
      "name": "暖冷交替",
      "config": {
        "version": 1,
        "stateSource": { "type": "nightMode" },
        "rotation": [
          { "type": "stateChange" },
          { "type": "interval", "every": 1, "unit": "hour", "anchor": "nextHour" }
        ],
        "order": { "mode": "sequential", "key": "hue", "referenceState": "day", "descending": false },
        "overrides": [
          { "when": { "type": "dateRange", "from": "12-24", "to": "12-26" }, "group": 3 }
        ]
      },
      "groups": [
        { "name": "橙", "enabled": true, "createdAt": "2026-10-01T09:00:00+08:00", "states": { "day": { "primary": "#FFB74D" }, "night": { "primary": "#E65100" } } },
        { "name": "青", "enabled": true, "states": { "day": { "primary": "#4DD0E1" }, "night": { "primary": "#006064" } } },
        { "name": "紫", "enabled": true, "states": { "day": { "primary": "#BA68C8" }, "night": { "primary": "#4A148C" } } },
        { "name": "圣诞", "enabled": false, "states": { "day": { "primary": "#C62828", "accent": "#FFD54F" }, "night": { "primary": "#1B5E20" } } }
      ]
    }
  ]
}
```

约定: 颜色为不透明的 `#RRGGBB`; 时间为 ISO 8601, 可省略 (省略时以导入时刻填充); `group` 为同一库 `groups` 数组的下标 (数据库中的 `config_json` 存的是组的行 id, 导入导出时互相映射); `dateRange` 省略 `year` 时每年重复, 指定 `year` 时只在从该年开始的那一次生效, 起点晚于终点表示跨年; 状态 key 与 4.1 一致; 未知字段忽略; `config.version` 与 `schemaVersion` 各自独立演进.

## 附录 C. 冒烟清单

1. 新建自建库并添加 3 色, 应用其中一色; 重启后选中状态与摘要正确; 删除该库后主题色不变, 摘要退化为调色板 (P1.1).
2. 克隆 "Material 颜色库" 后修改一项, 原预设库不受影响 (P1.1).
3. 激活 "Material 日夜配对" 的蓝绿组, 分别通过应用设置与跟随系统切换夜间模式, 主题色在 #009688 / #00796B 之间切换 (P1.4 / P1.5, 色阶按 D24; D24 之前为 #E0F2F1 / #00695C).
4. 新建 "无 + 时间间隔 1 分钟 (下一个整分)" 的 3 组库, 观察整分切换; 停留在主题色页面期间不切换, 离开后补一次 (P1.3 / P1.4 / D6).
5. 新建 "夜间模式 + 状态变化, 5 组随机" 库, 连续切换夜间模式 6 次, 不出现连续两次同组 (P0.6).
6. 强制停止应用, 等待超过一个间隔后冷启动: 只换一组, 首帧无闪色 (D5 / D9).
7. 默认颜色库 "背景色" 对话框四选一; 升级前选中 "窗口背景色 (暗)" 的用户升级后选中状态保持 (P1.6).
8. 旧布局: 智能库激活时摘要显示 "智能: 库名"; 在旧布局选色后智能库停用 (D13).
9. 导出两个库 (其中一个为智能库) 后再导入, 内容一致且重名加序号; 将 `schemaVersion` 改为 99 的文件被拒绝 (P2).
10. 电量状态源: 模拟充电 / 拔电与电量跨越阈值时颜色切换且无抖动 (P3.3); 节日覆盖层: 系统日期改到 12-25 时命中, 改回后恢复进入前的组 (P3.5).
11. API 26 / 27 / 31 / 33+ 上 "跟随壁纸" 的可用性, 以及更换壁纸后的刷新 (P4.1 / P4.2).
12. 派生配色 关 -> 自动 -> 关, 关闭时截图与升级前一致 (P4.6).
13. 脚本循环 100 次调用 `colors.theme.set(...)`, 只产生一次可见变化; "撤销脚本修改" 恢复原智能库的激活状态 (P6.3).
14. Room v1 -> v2 升级: 已有颜色历史与调色板历史完整保留 (P0.3 / P7.2).
15. `dumpsys alarm` / `dumpsys jobscheduler` 中无本功能产生的条目 (D5 / P7.4).

## 附录 D. 证据等级

| 等级 | 含义 |
| --- | --- |
| E0 | 代码已提交, 编译通过 |
| E1 | E0 + JVM 单测通过 |
| E2 | E1 + AVD 冒烟通过 (记录 API level) |
| E3 | E2 + 真机冒烟通过 (记录机型) |

## 附录 E. 文件地图 (规划)

| 关注点 | 文件 |
| --- | --- |
| 色彩算法 | `theme/color/OkColor.kt`, `ColorMetrics.kt` (P0.1), `ThemeColorDerivation.kt` (P0.2), `ColorHarmony.kt`, `ColorVisionSimulation.kt` (P4.3), `ColorQuantizer.kt` (P6.1) |
| 数据 | `theme/app/ColorEntities.kt` (v2 实体), `ColorHistoryDatabase.kt` (`MIGRATION_1_2`), `theme/library/ColorLibraryDao.kt`, `ColorLibraryRepository.kt`, `ColorLibraryJson.kt` (P2.1), `app/schemas/org.autojs.autojs.theme.app.ColorHistoryDatabase/{1,2}.json` |
| 选中与应用 | `theme/library/ColorSelection.kt`, `ColorSelectionCodec.kt` (P0.4), `theme/ThemeColorCoordinator.kt`, `ThemeColorApplyPolicy.kt` (P0.5) |
| 引擎 (纯) | `theme/intelligent/IntelligentConfig.kt`, `IntelligentConfigJson.kt`, `IntelligentConfigValidator.kt`, `IntelligentColorPlanner.kt`, `IntervalSchedule.kt` (P0.6), `calendar/SolarCalculator.kt`, `ChineseLunarCalendar.kt`, `HolidayCalendar.kt` (P3) |
| 引擎 (胶水) | `theme/intelligent/IntelligentColorEngine.kt`, `NightModeState.kt` (P1.4), `state/*StateSource.kt` (P3), `WallpaperColorSource.kt` (P4.1) |
| 推荐 | `theme/library/ColorUsageScorer.kt`, `ColorClusterer.kt` (P5) |
| 页面 | `theme/app/ColorLibrariesActivity.kt`, `ColorItemsActivity.kt`, `IntelligentLibrariesActivity.kt` (P1.2), `IntelligentLibraryEditorActivity.kt` (P1.3), `ColorInfoDialogManager.kt` (P4.3) |
| 脚本 | `runtime/api/augment/colors/Colors.kt` (P6.1), `colors.theme` 的实现文件 (P6.2, 位置随 Q1 确定) |
| 单测 | `app/src/test/java/org/autojs/autojs/theme/**`, `app/src/androidTest/java/org/autojs/autojs/theme/app/ColorHistoryDatabaseMigrationTest.kt` |

## 附录 F. 旧思路摘要与映射

维护者约两年前的草案要点, 以及在本 Roadmap 中的落点:

| 旧思路 | 本 Roadmap |
| --- | --- |
| 进入主题色设置页面时, 所有触发器暂停触发效果, finish 时再根据情况重新触发 | D6, P0.5, P1.4 |
| 触发器 "夜间模式": 2 个状态, 状态 "关" 可设为与状态 "开" 相同 | 状态源 "夜间模式" + 换组时机 "状态变化" (P1.3) |
| 触发器 "应用重启": 1 个状态 | 状态源 "无" + 换组时机 "应用启动" (P1.3) |
| 触发器 "时间间隔": 每经过 n 秒 / 分 / 小时触发一次; 首次触发: 现在 / 下一次整分 / 下一次整时 | 状态源 "无" + 换组时机 "时间间隔" (P0.6 `IntervalSchedule`, P1.3) |
| 组: 一个或多个颜色构成一组; 多组时顺序或随机切换; 顺序可选 创建时间 / 修改时间 / 名称 / 色值 (亮度等参考量, 可选状态), 正序或倒序; 组可启用或禁用 | 4.6, P0.6, P1.3 (另增 "手动顺序") |
| 库卡片: `[自定义名称]` `[夜间模式 \| 5 组 \| 随机切换]`, `[材料颜色-50/800]` `[夜间模式 \| 12 组 \| 顺序切换 (色相)]`, `[时间间隔 \| 1 组 \| 顺序切换]` | 4.8, P1.2 |
| 单状态且只有 1 组时提示 "当前设置不具备智能表现, 是否继续"; 0 组时提示 | 4.7, P0.6 (`IntelligentConfigValidator`), P1.3 |
| 智能颜色库 (单独设计): 蓝绿 / 蓝灰 等色系各含 50, 800 与 "自动" (50 / 800) | 预设 "Material 日夜配对" (P1.5); 固定的 50 / 800 色仍在 Material 颜色库中 |
| 背景色: 亮色 #FAFAFA / 暗色 #212121 / 自动 | 预设 "背景色" (P1.6) |
| 背景色对话框 "选择一个 AutoJs6 窗口背景色作为主题色": 亮色 / 暗色 / 自动跟随 / 自动反色 | 默认颜色库的合并项与对话框 (P1.6) |
