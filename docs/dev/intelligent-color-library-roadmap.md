# AutoJs6 智能颜色库 (Intelligent Color Library) Roadmap

本文是主题色 "智能颜色库" 及其依赖的 "自建颜色库" 的可执行状态表.
以 2026-10-06 的本地代码快照 (`master@3089662e35`, `VERSION_NAME=6.8.0`, `VERSION_BUILD=5319`) 为起点,
每个条目均可独立 Check 并落地, 后续会话按阶段逐步推进.

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
- [x] P1.4 引擎接线 `IntelligentColorEngine` (D5, D6, D9, F9): Application 级单例; 激活 / 停用; 新增 `NightModeState` 统一 "当前是否夜间" 并可订阅 (应用设置变化 + `App.onConfigurationChanged`); 应用启动: 在 `ThemeColorManager.init()` 之后同步读取激活快照并评估, 首帧沿用上次保存的有效颜色, 结果不同时再应用; 时间间隔: 主线程 `Handler` 按 `IntervalSchedule` 对齐调度, `ACTION_TIME_CHANGED` / `ACTION_TIMEZONE_CHANGED` 时重排; 运行时状态 (当前组, 覆盖前组, 上次换组时间, 下次到期时间) 持久化; 所有主题色页面在 `onCreate` / `onDestroy` 中注册暂停令牌 (D6). 验收: 附录 C 第 3~6 项. 证据: `theme/intelligent/IntelligentColorEngine` (Application 级单例, 实现协调器 `Listener` 与夜间模式 `Listener`; 激活快照 `IntelligentSnapshot` 与运行时状态以 JSON 存于 Pref 键 `INTELLIGENT_COLOR_ACTIVE_SNAPSHOT` / `INTELLIGENT_COLOR_RUNTIME_STATE`, 编解码 `IntelligentEngineJson`); `NightModeState` 以 `ViewUtils.isNightModeEnabled` 为唯一依据 (各模式下均保持同步), 通知由 `ViewUtils.setDefaultNightMode` 发出并延后一轮主线程循环; `App.onCreate` 主进程 (非 inrt) 在主题色与夜间模式初始化之后调用 `init` 并以 `APP_START` 评估; 时间间隔用主线程 `Handler.postDelayed` 到 `nextDueAt`, 间隔生效期间注册 `ACTION_TIME_CHANGED` / `ACTION_TIMEZONE_CHANGED` 重排; 回到前台 (`ActivityLifecycleCallbacks.onActivityResumed`) 仅在到期, 状态变化或主题色偏离预期时评估, 暂停期间跳过; 用户或脚本的显式选色经协调器回调停用引擎; 选中状态不再属于激活库时自动停用. 新增协调器来源 `ACTIVATION` (用户激活库或选组: 立即生效, 暂停期间亦然, 不停用智能库), `ThemeColorApplyPolicyTest` 增至 10 例; 暂停令牌由 `ColorSelectBaseActivity` 统一持有 (颜色库页 / 条目页 / 旧布局 / 自建库页), 因配置变化重建时延后一轮释放 (`releasePauseToken`), 避免旋转或切换夜间模式时误评估; 选中状态写入 `savePrefsForIntelligent` (条目 id 为起始组, legacy 索引置空). `IntelligentEngineJsonTest` (JVM, 4 例), `theme.*` 单测 79 例 (E1), 2026-10-06; 设备验证并入 P1.8 (附录 C 第 3~6 项).
- [x] P1.5 预设 "Material 日夜配对" (F13, 旧思路 "材料颜色-50/800"): 19 个 Material 色系各一组, 日 = 50, 夜 = 800, 状态源为夜间模式, 不设换组时机 (选组即固定, 相当于旧思路中各色系的 "自动" 项); 只读, 可克隆后加入换组时机. 固定的 50 / 800 色仍在 "Material 颜色库" 中选择. 证据: `theme/intelligent/IntelligentPresets.materialDayNight` (库 id `0x100`, 组 id 为色系下标 0~18, 组名取 `md_*_50` 名称去掉 " (50)" 后缀, 各语言均为 "名称 (色阶)" 格式, 无需新增 19 x 10 条翻译; 库名 "材料设计日夜配对"), 2026-10-06; 入口随 P1.2 落地.
- [x] P1.6 预设 "背景色" 与默认库合并项 (旧思路 "背景色对话框", F12): 预设智能库 "背景色" 有 4 组: 亮色 (日夜均为 #FAFAFA), 暗色 (日夜均为 #212121), 自动跟随 (#FAFAFA / #212121), 自动反色 (#212121 / #FAFAFA), 状态源为夜间模式, 无换组时机. 默认颜色库中的两个窗口背景色合并显示为一项 "背景色", 点击弹出对话框 "选择一个 AutoJs6 窗口背景色作为主题色" (四选一, 显示色值); 选 亮色 / 暗色 仍是原预设条目 (保留 item id 3 / 4, 兼容既有选中状态与历史), 选 自动跟随 / 自动反色 则激活预设 "背景色" 的对应组. 证据: `IntelligentPresets.background` (库 id `0x101`, 组 id 0~3); `ColorLibrariesActivity.displayColorsOf` 把默认库的条目 3 / 4 合并为沿用 id 3 的 "背景色" 条目 (`PresetColorItem.mergedColorRes`), 全局搜索仍列出两项原条目; `ColorItemsActivity` 的列表 / 搜索 / 滚动 / 定位均改用显示列表, 条目 4 与 "背景色" 智能库映射到合并条目高亮; 点击合并条目弹出单选对话框并预选当前方案; 条目页的历史改为按 id 应用 (`applyPresetItem` 上移至 `ColorSelectBaseActivity`); 多色条目由新的 `SegmentedCircleDrawable` 绘制 (两色时左日右夜), `ColorItemViewHolder` 复用单色视图时恢复圆形背景; 智能库选中时摘要显示 "库名 | 组名 [#RRGGBB]" (名称由引擎写入缓存). 9 条字符串 x 11 个 `strings.xml` (含日 / 夜状态名), 编译通过 (E0), 2026-10-06; 界面冒烟并入 P1.8 (附录 C 第 3, 7 项).
- [x] P1.7 摘要, 定位与旧布局 (D9, D13, F11): `getCurrentColorSummary` 在智能库激活时显示 "智能 | 库名 | 组名 #RRGGBB" 形式 (标识沿用 `color_library_identifier_intelligent_colors`); 抽屉与设置项摘要随之刷新; "定位当前主题色" 打开智能库详情页并定位到当前组, 自建库颜色则打开对应库并滚动到该颜色; 旧布局按 D13 处理. 证据: 摘要读取选中名称缓存 (`SELECTED_COLOR_LIBRARY_NAME` / `SELECTED_COLOR_LIBRARY_ITEM_NAME`, 自建库颜色在应用时写入, 智能库由引擎每次评估时写入当前组名), 形如 "库名 | 组名 [#RRGGBB]", 库名超过 12 个字符时截断; 抽屉与设置项沿用 `getCurrentColorSummary`, 随主题色变化刷新; 定位入口 `checkAndGetTargetInfoForThemeColorLocate` 对自建库颜色打开 `UserColorLibraryActivity` 并按参数滚动到该颜色, 对智能库打开 `IntelligentLibraryActivity` (详情页菜单再定位到当前显示的组); 旧布局摘要在智能库激活时显示 "智能: 库名 [#RRGGBB]", 旧布局中选色经协调器以 `USER` 来源停用智能库 (P0.5 / P1.4), 自建库颜色写入自定义颜色槽 (P1.1). 编译通过 (E0), 2026-10-07; 冒烟并入 P1.8 (附录 C 第 8 项).
- [ ] P1.8 阶段收尾: 11 个 `strings.xml`; changelog (feature: 自建颜色库的新建 / 克隆 / 编辑, 智能颜色库与两个预设; improvement: 默认颜色库的背景色合并为对话框); 编译 + JVM 单测 + AVD 冒烟 (附录 C 第 1~8 项).

### P2 自建库完善与交换 (AutoJs6 JSON)

- [ ] P2.1 `ColorLibraryJson` 编解码 (D14, 附录 B): 纯对象; 颜色一律 `#RRGGBB`; 组在文件内以下标引用; 限额: 文件不超过 2 MiB, 库不超过 256 个, 每库颜色或组不超过 4096 个. 验收: 单测覆盖往返无损, 未知字段忽略, 高版本 schema 拒绝, 非法色值, 超限.
- [ ] P2.2 导出: 库长按 "导出", 以及菜单 "导出颜色库..." 多选 (可含预设库与预设智能库的快照); SAF `CreateDocument` (`application/json`, 默认名 `autojs6-colors-yyyyMMdd-HHmmss.json`); IO 线程写出.
- [ ] P2.3 导入: "添加颜色库 > 导入颜色库" 走 SAF `OpenDocument`; IO 线程解析与校验; 预览对话框 (库名, 类型, 颜色数或组数, 可勾选); 重名自动加序号 `名称 (2)`; 导入的库 `kind = IMPORTED` 并启用 imported 图标; 智能库配置引用了当前版本不支持的状态源或换组时机时, 保留原始 JSON 并在编辑器中标注 "不受支持, 不会生效"; 错误映射为本地化文案.
- [ ] P2.4 全局搜索覆盖自建库颜色与智能库组颜色 (`filterColorsFromColorItems` 的数据源扩展, 结果带库标识前缀); 搜索帮助页同步说明.
- [ ] P2.5 库列表管理: 自建库拖拽排序, 多选删除 / 导出.
- [ ] P2.6 阶段收尾: 字符串, changelog (feature: 颜色库导入 / 导出; improvement: 搜索范围), 冒烟 (附录 C 第 9 项).

### P3 条件扩展: 状态源, 每日定时与日期覆盖层

- [ ] P3.1 时间类状态源: "时段" (2~6 段, 每段起始时刻, 默认 06:00 / 18:00 两段); "日出日落" (2 个状态, 纯对象 `SolarCalculator` 实现 NOAA 离线算法, 极昼 / 极夜时固定为白天 / 夜晚; 地理信息来源见 Q4). 验收: 单测对照若干城市与日期的日出日落 (误差不超过 2 分钟) 与极地情形.
- [ ] P3.2 日期类状态源: "星期" (7 个状态或 "工作日 / 周末" 2 个状态, 一周首日跟随地区); "季节" (4 个状态, 按月划分气象季节, 半球来源见 Q4). 验收: 单测覆盖边界日期与南半球.
- [ ] P3.3 设备状态源: "电量" (2~5 个区间, 阈值可调, 2% 迟滞防抖), "充电", "省电模式", "无障碍服务" (AutoJs6 无障碍服务是否运行), "脚本运行" (是否有脚本在运行). 广播与回调只在激活库用到时注册 (D5). 验收: 区间映射与迟滞的单测; AVD 上用 `dumpsys battery` 模拟.
- [ ] P3.4 换组时机 "每日定时" (一个或多个 hh:mm), 与时间间隔共用调度. 验收: 单测覆盖跨日与夏令时切换.
- [ ] P3.5 日期覆盖层 (D4): 有序列表, 每项为日期条件 + 同库内的目标组; 日期条件: 自定义日期区间 (每年重复或仅一次, 支持跨年如 12-24 ~ 01-02), 节日 (`HolidayCalendar`: 公历固定日 / 第 n 个星期几 / 农历日期; `ChineseLunarCalendar` 以查表实现 1900~2100 年; 首批节日与地区见 Q5). 验收: 单测覆盖农历转换已知日期, 跨年区间, 多覆盖层优先级, 进入与离开时的组恢复.
- [ ] P3.6 编辑器与摘要支持上述全部类型; 切换状态源时按状态 key 迁移已有颜色, 无法对应的状态以首个状态补齐并提示; 字符串, changelog (improvement: 智能颜色库支持的条件), 冒烟 (附录 C 第 10 项).

### P4 跟随壁纸与配色衍生

- [ ] P4.1 壁纸色来源 `WallpaperColorSource`: API 31+ 读取系统动态配色 (`android.R.color.system_accent1_*` / `system_accent2_*` / `system_accent3_*` 等色调); API 27~30 使用 `WallpaperManager.getWallpaperColors(FLAG_SYSTEM)` (主色 / 次色 / 第三色); API 24~26 不可用. 不读取壁纸位图, 不申请存储权限; 进程存活时通过 `addOnColorsChangedListener` 监听, 冷启动与回到前台时比较指纹后刷新.
- [ ] P4.2 预设 "跟随壁纸": 每个壁纸种子色生成一组 (日 = 对日间背景对比度达标的变体, 夜 = `nightVariant`), 状态源为夜间模式, 无换组时机; 壁纸变化后重新生成并保持 "第 k 组" 的选择; 菜单 "保存为我的智能库" 以当前结果克隆出普通智能库; API 24~26 上预设显示为不可用并说明最低系统版本.
- [ ] P4.3 配色衍生工具 (扩展 `ColorInfoDialogManager` 的颜色详情): 和谐色 (互补 / 类比 / 三角 / 分裂互补 / 四方, OKLCH 色相旋转 + 色域映射), 色阶 (同色相 9~11 级), 对比度 (相对日 / 夜窗口背景的比值, 标注是否达到 3:1 与 4.5:1), 色觉模拟 (红色盲 / 绿色盲 / 蓝色盲, Machado 2009 矩阵); 每个结果可 "应用" / "加入颜色库" / "收藏" (P5 落地后). 纯算法 `ColorHarmony` / `ColorVisionSimulation` 有单测.
- [ ] P4.4 组编辑器 "自动填充": 根据首个已填状态推导其余状态 (夜间模式用 `nightVariant`, 多状态按色阶分布), 可撤销.
- [ ] P4.5 "从种子色生成智能库" 向导: 种子 (当前主题色 / 调色板 / 壁纸 / 收藏) -> 方案 (和谐类型与组数) -> 状态源 (默认夜间模式, 自动填充) -> 预览 -> 创建.
- [ ] P4.6 派生配色 (D2, F5): 主题色设置增加 "派生配色" (关 / 自动); 组编辑器中每个状态可展开填写 accent / dark; 审计 `ThemeColor` 三字段的全部消费方并接入 (至少 `ThemeColorFloatingActionButton` 的 accent, 状态栏改用 dark, `OfficialPluginSettingsProvider` 传给插件的三色). 验收: "关" 时截图与升级前一致 (E3).
- [ ] P4.7 阶段收尾: 字符串, changelog (feature: 跟随壁纸, 配色衍生工具, 派生配色), 冒烟 (附录 C 第 11~12 项, 覆盖 API 26 / 27 / 31 / 33+).

### P5 推荐与收藏

- [ ] P5.1 收藏与置顶: 颜色条目长按或详情对话框中 "收藏" / "取消收藏", 收藏可 "置顶"; 颜色库列表增加虚拟库 "收藏" (排序: 手动 / 收藏时间 / 色相 / 亮度), 提供 "从收藏创建智能库" (每色一组, 其余状态自动填充).
- [ ] P5.2 使用统计 (D10, D12): `USER` / `SCRIPT` 应用颜色时 `use_count` 加 1 并刷新 `last_used_time`, 智能引擎应用的颜色不计; 纯对象 `ColorUsageScorer` (使用次数取对数并按近因指数衰减, 半衰期默认 14 天; 收藏与置顶加权). 验收: 单测覆盖排序稳定性与衰减.
- [ ] P5.3 相似色聚类: 纯对象 `ColorClusterer` (dE_ok 阈值, 贪心聚类, 代表色取分数最高者), 用于推荐与颜色历史列表的 "合并相似颜色" 开关. 验收: 单测覆盖阈值边界与稳定性.
- [ ] P5.4 虚拟库 "常用与推荐": 置顶收藏 -> 其余收藏 -> 按评分排序的历史色 (聚类去重后), 最多 30 项, 无数据时隐藏; 支持 "从推荐创建智能库".
- [ ] P5.5 阶段收尾: 字符串, changelog (feature: 颜色收藏, 常用与推荐), 冒烟.

### P6 脚本 API

- [ ] P6.1 `colors` 模块算法函数 (命名见 Q1, 避开 F10 的既有名称): `toOklch` / `oklch`, `toOklab` / `oklab`, `ensureContrast`, `harmony`, `tones`, `derive` (返回 primary / accent / dark), `nightVariant`, `simulate`, `extract` (输入 `ImageWrapper`, 选项 count / region / 采样步长; 纯 Kotlin Wu 量化 + k-means 精修); `colors.distance` 增加 `oklab` 算法 (是否同步到 `images` 找色见 Q2). 验收: Rhino 层参数校验与单测, 量化器有 JVM 单测.
- [ ] P6.2 主题色控制 `colors.theme` (Q1): `get()`, `set(color, options)` (options: accent / dark; `USER` 语义, 停用智能库, source = `SCRIPT`), `reset()`, `libraries()` (库摘要: id / name / kind / intelligent / 组数), `activate(libraryId, options)` (options: group), `deactivate()`, `next()` (立即换组, 不要求勾选 "脚本" 换组时机), `state(name)` (设置 "脚本" 状态源的当前状态, 未知状态名抛出异常), `current()` (库 / 组 / 状态 / 颜色). 编辑器增加 "脚本" 状态源 (声明 2~8 个命名状态) 与 "脚本" 换组时机.
- [ ] P6.3 限频, 来源与回退 (D11): 合并窗口 500 ms; 颜色历史显示来源 "脚本: 名称"; 最近一次修改来自脚本时主题色页面菜单显示 "撤销脚本修改" (恢复修改前的 `ColorSelection` 与智能库激活状态). 验收: 单测覆盖合并与回退快照.
- [ ] P6.4 关联项目同步 (D16, `AGENTS.md` "关联项目"): 文档项目 `colors` 章节新增函数与 `colors.theme`, 运行 `auto-generate-for-autojs6.bat`; d.ts 声明运行 `aj6dts.bat -Publish`, 两个仓库版本号加一并合理递增版本名称, 同步 `aj6-int-*.d.ts`, Ace Editor 仓库执行 `:app:generateAutoJs6LspDeclarations`; 按需在 "示例代码" 中加入主题色轮换与取色示例; 各仓库分别提交.
- [ ] P6.5 阶段收尾: changelog (feature: `colors` 新增函数与 `colors.theme`), 冒烟脚本 `docs/dev/smoke/intelligent-color-library/` (附录 C 第 13 项).

### P7 质量, 验证与文档

- [ ] P7.1 单测补洞: 所有纯对象的分支覆盖复核, 重点是 `IntelligentColorPlanner` 与 `ColorSelectionCodec`.
- [ ] P7.2 Room 迁移真机回归: 安装含颜色历史数据的旧版本后升级, 执行 `ColorHistoryDatabaseMigrationTest` 并人工确认历史列表 (附录 C 第 14 项; 注意 `connectedAndroidTest` 结束后会卸载被测应用, 需加 `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`).
- [ ] P7.3 附录 C 冒烟清单全部执行, 记录机型与 API level.
- [ ] P7.4 性能与耗电: 冷启动评估耗时 (只读快照, 目标不超过 2 ms), 主题色切换的掉帧情况; `dumpsys alarm` / `dumpsys jobscheduler` 中无本功能产生的条目 (D5).
- [ ] P7.5 架构文档 `docs/dev/intelligent-color-library-architecture.md`: 数据模型与迁移, 引擎事件与状态机, 协调器来源规则, 暂停机制, 快照格式, 可测性.
- [ ] P7.6 changelog 终稿整理 (按 `AGENTS.md` 合并同一功能的连续实现为最终行为), 本 Roadmap 收尾与证据回填.

---

## 附录 A. 待决事项

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
3. 激活 "Material 日夜配对" 的蓝绿组, 分别通过应用设置与跟随系统切换夜间模式, 主题色在 #E0F2F1 / #00695C 之间切换 (P1.4 / P1.5).
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
