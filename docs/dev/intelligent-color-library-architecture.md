# AutoJs6 智能颜色库架构 (Intelligent Color Library Architecture)

本文描述主题色 "自建颜色库" 与 "智能颜色库" 落地后的架构, 供后续维护与扩展参考. 决策编号 (D1~D24) 与阶段编号 (P0~P7) 见 [Roadmap](intelligent-color-library-roadmap.md). 路径相对 `app/src/main/java/org/autojs/autojs/`, 以 2026-10-07 的 master 为准.

## 1. 分层

```text
页面层          theme/app/*Activity, *Dialogs, Adapter       颜色库页, 条目页, 旧布局, 智能库页与编辑器, 历史
  |
选中与应用      theme/library/ColorSelection                 选中状态编解码 (D9)
                theme/ThemeColorCoordinator                  主题色修改的唯一入口 (D10, D6, D11)
  |
引擎胶水        theme/intelligent/IntelligentColorEngine     进程内单例, 订阅事件, 调度, 持久化运行状态
                NightModeState, DeviceStateMonitor,          状态来源 (只在激活库需要时注册, D5)
                WallpaperColorSource
                theme/app/ScriptThemeChanges                 脚本修改的历史与撤销 (P6.3)
  |
纯核心          theme/intelligent/IntelligentColorPlanner    决策: 显示哪个组, 使用哪个颜色
                IntelligentConfig(+Json, Validator),         配置模型, 编解码, 有效性结论
                StateResolver, IntervalSchedule,             状态解析, 计时
                DailySchedule, DateOverrides, calendar/*     每日定时, 日期覆盖, 日出日落 / 农历 / 节日
                theme/color/*                                OKLab / OKLCH, 度量, 派生, 和谐色, 色觉模拟, 量化
                theme/usage/*, theme/library/ColorLibraryJson 推荐评分与聚类, 交换格式
  |
持久化          theme/app/ColorHistoryDatabase (Room v2)      历史, 用户库, 组, 收藏
                Pref                                          选中状态, 激活快照, 运行状态, 撤销快照
```

约束: 纯核心只依赖 Kotlin 标准库, `java.time` 与 Gson 树模型, 不引用 Android 类型 (D7, D15), 因此可在 JVM 上以 JUnit4 测试. 胶水层不做决策, 只负责收集输入, 调用纯核心并落地结果.

## 2. 数据模型与迁移

### 2.1 Room 数据库

数据库文件仍为 `color-history-database.db` (D8), `ColorHistoryDatabase` version 2, `exportSchema = true`, schema 位于 `app/schemas/org.autojs.autojs.theme.app.ColorHistoryDatabase/{1,2}.json`.

| 表 | 内容 | 要点 |
| --- | --- | --- |
| `color_history` / `palette_history` | 既有的颜色历史与调色板历史 | v2 新增 `use_count` (默认 1), `source` (默认 `USER`), `source_detail` (脚本名称等); 既有列不变 |
| `color_library` | 用户颜色库 | `kind` (`CREATED` / `IMPORTED` / `CLONED`), `is_intelligent`, `name`, `origin`, `config_json` (智能库配置), `position`, 创建与修改时间 |
| `color_library_color` | 普通库的颜色 | 外键 `library_id`, 级联删除 |
| `color_group` | 智能库的组 | `enabled`, `position`, 时间; 外键级联删除 |
| `color_group_state` | 组在各状态下的颜色 | 主键 (`group_id`, `state_key`); `primary_color` / `accent_color` / `dark_color` 可空 |
| `color_favorite` | 收藏 | `color` 唯一, `pinned`, `position` |

`MIGRATION_1_2` 只做加法: 为两张历史表 `ALTER TABLE ... ADD COLUMN`, 再按 2.json 的语句建新表与索引. 建表语句逐字取自 KSP 生成的 2.json, 避免手写偏差. `ColorHistoryDatabase.createBuilder` 供生产代码与迁移测试共用, 保证测试与线上注册了同一组迁移.

智能库的配置 (状态源, 换组时机, 切换方式, 日期覆盖层) 以带版本号的 JSON 存在 `config_json` 列中, 新增配置项不需要数据库迁移; 组与颜色用表存储, 以便搜索与排序. 日期覆盖层在数据库中引用组的行 id, 在交换格式中引用组的数组下标, 由 `ColorLibraryJson` 互相映射.

访问入口为 `theme/library/ColorLibraryRepository` (挂起函数, 在 IO 线程执行); 页面层不直接使用 DAO.

### 2.2 选中状态与 ID 分段

选中状态沿用两个 Pref 键 `SELECTED_COLOR_LIBRARY_ID` / `SELECTED_COLOR_LIBRARY_ITEM_ID` 与旧布局的 legacy 索引 (D9). `ColorSelectionCodec` 把它们归一化为 `ColorSelection`:

| 变体 | 库 id | 条目 id |
| --- | --- | --- |
| `Preset` | 预设库 `0x01`~`0xFF` (历史取值不变) | 条目 id, 未知时为空 |
| `Palette` | `0x21` | 0 |
| `Favorite` | `0x23` | 收藏行 id |
| `UserColor` | `0x0100_0000 + rowId` | 颜色行 id |
| `Intelligent` | 预设智能库 `0x100`~`0x1FF`, 用户智能库 `0x0200_0000 + rowId` | 起始组 id |
| `Unknown` | 其他 | 原值 |

用户库按类型分段, 使冷启动时只看 Pref 就能判断当前是否为智能库, 不查数据库. 库键缺失时由 legacy 索引决定 (自定义色, 默认色, Material 第 n 色).

## 3. 主题色的应用: 协调器

`ThemeColorCoordinator` 是修改主题色的唯一入口 (D10). 每次修改带一个 `ThemeColorSource`; 是否生效, 何时生效由纯对象 `ThemeColorApplyPolicy` 决定, 协调器只负责线程与副作用.

| 来源 | 发起方 | 生效时机 | 暂停期间 | 停用智能库 | 写颜色历史 |
| --- | --- | --- | --- | --- | --- |
| `USER` | 主题色页面中的选择 | 立即, 并丢弃待生效的脚本修改 | 照常生效 | 是 | 是 (由页面写入) |
| `ACTIVATION` | 激活智能库或选择其中的组 | 立即 | 照常生效 | 否 | 否 |
| `INTELLIGENT` | 引擎换组或状态变化 | 立即 | 丢弃 (恢复后重新评估) | 否 | 否 (不刷屏, 不影响推荐) |
| `SCRIPT` | `colors.theme.set` / `reset` | 500 ms 固定窗口结束时, 只应用窗口内最后一次 | 照常生效 | 是 | 是 (来源 `SCRIPT` 与脚本名) |
| `RESTORE` | 撤销脚本修改, 派生配色设置变化后的重新应用 | 立即, 并丢弃待生效的脚本修改 | 照常生效 | 否 | 否 |

生效 (`commit`) 总在主线程按以下顺序执行, 调用方可在任意线程调用 `apply`:

1. `CommitObserver.beforeCommit` (脚本撤销快照在第一次脚本修改前采集).
2. `ThemeColorManager.setThemeColor` 保存三色并更新内存.
3. `ThemeChangeNotifier.notifyThemeChanged` 通知界面.
4. 来源为 `USER` / `SCRIPT` 时调用 `Listener.onExplicitColorApplied`, 引擎随即停用.
5. `CommitObserver.afterCommit` (脚本修改写历史; `USER` / `ACTIVATION` / `RESTORE` 丢弃撤销快照, `ScriptUndoRules`).

脚本窗口是固定窗口: 第一个请求开窗并安排一次 flush, 后续请求只替换待生效值, 不顺延窗口; `USER` / `RESTORE` / `ACTIVATION` 取消窗口. 因此脚本的紧密循环只产生一次可见变化, 长时间慢速调用至多每 500 ms 生效一次. `pendingOrCurrent` 让 `colors.theme.get()` 读到窗口中尚未生效的值.

`ThemeColorManager.resolve(primary, accent, dark)` 按 D2 补全三色: 显式值优先; 否则 "派生配色" 为自动时由 `ThemeColorDerivation` 推导, 为关时与主色相同 (与改造前逐像素一致).

## 4. 暂停机制 (D6)

主题色相关页面 (`ColorSelectBaseActivity` 的 8 个子类: 颜色库页, 条目页, 用户库页, 虚拟库页, 旧布局, 智能库列表, 智能库页, 智能库编辑器) 在 `onCreate` 中以 Activity 实例为令牌调用 `pause`, 在销毁时释放. 页面因旋转或夜间模式重建时, 释放延后一轮主线程循环, 让新实例先取得令牌, 避免中途恢复.

- 持有任一令牌期间, `IntelligentColorEngine.evaluate` 在入口直接返回: 组, 运行状态与颜色都不变化, 计时与状态变化只是被跳过.
- 最后一个令牌释放时, 协调器回调 `onResumed`, 引擎以 `EVALUATE` 评估一次, 把期间到期的换组与状态变化一次性补上.
- `ACTIVATION` 与 `RESTORE` 不受暂停影响, 因为它们是用户在这些页面上的直接操作.

## 5. 引擎

### 5.1 职责划分

`IntelligentColorEngine` (Application 级单例) 负责:

- 持有激活库的 `IntelligentSnapshot` 与 `IntelligentRuntimeState`, 并写入 Pref.
- 只在激活库需要时注册监听: 夜间模式 (`NightModeState`), 设备状态 (`DeviceStateMonitor`: 电量 / 充电, 省电模式, 无障碍服务, 脚本运行), 时间与时区变化广播, 壁纸颜色 (仅 "跟随壁纸" 预设), 脚本启动 (仅勾选 "脚本启动" 换组时机). 停用时全部注销 (D5).
- 用主线程 `Handler` 在最早的到期时间唤醒: 换组到期 (时间间隔或每日定时), 时间类状态的下一次变化, 存在日期覆盖层时的下一个午夜. 不使用 AlarmManager, WorkManager 或前台服务.
- 每次 Activity 恢复时调用 `evaluateIfNeeded`: 仅在到期, 状态变化, 覆盖层变化或颜色偏离预期时评估, 使高频调用保持低开销.

决策全部来自 `IntelligentColorPlanner.plan(PlannerInput)`, 它是纯函数: 输入配置, 组, 当前状态 key, 事件, 运行状态, 覆盖组与时区, 输出显示的组, 有效颜色, 是否换组与新的运行状态.

### 5.2 事件与换组

| 事件 | 来源 | 何时换组 |
| --- | --- | --- |
| `ACTIVATE` | 激活库, 或激活库的配置被编辑 | 不换组; 从起始组 (或第一个可用组) 开始, 重新建立计时网格 |
| `APP_START` | 冷启动时的首次评估 | 勾选 "应用启动" 时 |
| `EVALUATE` | 计时到期, 状态变化, 恢复, 前台回归 | 状态变化且勾选 "状态变化", 或时间间隔 / 每日定时到期 |
| `SCRIPT_NEXT` | `colors.theme.next()` | 总是 (不要求勾选换组时机) |
| `SCRIPT_START` | 有脚本开始运行 (同时启动的多个脚本合并为一次) | 勾选 "脚本启动" 时 |

规划器的规则:

1. 状态 key 不在配置的状态列表中时取第一个状态.
2. 一次评估中多个换组时机同时触发只换一组; 状态变化时先换组, 再取新状态的颜色.
3. 时间间隔: 首次以 "现在 / 下一个整分 / 下一个整时" 为锚点建立网格; 之后到期只推进一格, 进程死亡期间错过的多个周期只补一次 (D5). 每日定时同理.
4. 禁用组与没有任何颜色的组不参与轮换; 当前组被删除或禁用时直接取下一个可用组.
5. 顺序切换按排序键 (手动顺序, 创建时间, 修改时间, 名称, 亮度, 色相, 彩度; 色值类参照指定状态) 排列, 可倒序, 到末尾后循环; 随机切换在组数不少于 2 时不与当前组相同.
6. 日期覆盖层命中时显示其组并暂停轮换, `currentGroupId` 保持不变, 另记 `overrideGroupId`; 离开后回到原组, 不推进.
7. 有效颜色为显示组在当前状态下的颜色; 当前状态缺色时取按状态顺序第一个有颜色的状态.

结果与当前主题色不同 (或来源为 `ACTIVATION`) 时经协调器应用; 然后写回运行状态与选中项名称缓存, 重新安排唤醒.

### 5.3 停用

以下情况停用激活库并注销全部监听: 用户或脚本显式选色 (协调器回调), 选中状态不再指向该库 (例如旧布局中改选其他颜色), 该库被删除, `colors.theme.deactivate()`.

### 5.4 冷启动

`App.onCreate` 主进程分支的开头调用 `IntelligentColorEngine.preload()`: 后台线程从 Pref 解码快照与运行状态 (不查询 Room), 并以纯函数方式空跑一次规划器与状态解析, 预先加载相关类. `ThemeColorManager.init` 与夜间模式初始化之后, 任何 Activity 绘制之前, 调用 `IntelligentColorEngine.init`: 取用预加载的结果 (尚未完成时等待), 然后以 `APP_START` 同步评估一次. 因此到期的换组在首帧之前就已生效, 不会闪色 (D9). 预设库的快照按当前代码与语言重建 (组 id 稳定, 当前组不变, D24), 这一步推迟到首帧之后执行, 有变化时再评估一次. 代价是: 进程停止期间若更换了壁纸 (或预设颜色随版本变化), 激活 "跟随壁纸" 时冷启动的首帧仍为旧颜色, 随后由延后重建或首个 Activity 恢复时的壁纸检查更正; 换来的是冷启动不再同步读取壁纸颜色与重建 19 组色阶. inrt (打包应用) 不初始化引擎.

冷启动路径上的其他取舍: 选中状态只比较保存的库 id (智能库 id 有独立分段), 不加载选择页面的类; 不依赖本地时间的配置使用 UTC, 避免首次加载时区数据; 名称排序所需的 `Collator` 延迟到首次比较时创建; 运行状态与摘要名称只在变化时写入. 2026-10-07 的实测 (release 构建, Sony G8441 / API 28, 激活 "材料设计日夜配对"): 评估耗时中位数 1.66 ms, 优化前为 11.7 ms.

冷启动的评估耗时可经 `adb shell setprop log.tag.IntelligentColorEngine DEBUG` 开启记录 (默认关闭), 强制停止后重新启动, logcat 中的 `IntelligentColorEngine` 日志给出快照解码, 评估与总耗时 (微秒).

## 6. 快照与持久化格式

### 6.1 Pref 键

| 键 | 内容 |
| --- | --- |
| `INTELLIGENT_COLOR_ACTIVE_SNAPSHOT` | 激活库快照: `libraryId`, `name`, `config` (同 6.2), `groups` (每组 `id`, `name`, `enabled`, `position`, `createdTime`, `modifiedTime`, `states.{key}.{primary,accent,dark}`, 颜色为 ARGB 整数) |
| `INTELLIGENT_COLOR_RUNTIME_STATE` | 运行状态: `currentGroupId`, `lastStateKey`, `overrideGroupId`, `intervalAnchorAt`, `nextDueAt`, `dailyDueAt`, `lastRotatedAt` (均可省略) |
| `INTELLIGENT_COLOR_SCRIPT_STATE` | "脚本状态" 状态源的当前状态名 |
| `THEME_COLOR_SCRIPT_UNDO` | 脚本撤销快照: 选中状态原始值与名称, legacy 索引与自定义色, 三色, 激活库的快照与运行状态 |

解码从不抛出异常: 快照格式错误时视为未激活, 运行状态格式错误时取默认值.

### 6.2 智能库配置 JSON

`IntelligentConfigJson`, `version` 为 1, 未知字段忽略, 未知类型的状态源或换组时机原样往返, 非法值修复为合法值 (如时间间隔钳制为不少于 5 秒且至多 9999 个单位, D19).

- `stateSource.type`: `none`, `nightMode`, `timePeriods`, `sunriseSunset`, `weekday`, `season`, `battery`, `charging`, `powerSave`, `accessibilityService`, `scriptRunning`, `scriptStates`.
- `rotation[].type`: `stateChange`, `appStart`, `interval`, `daily`, `scriptStart`.
- `order`: `mode` 为 `sequential` (含 `key`, `referenceState`, `descending`) 或 `random`.
- `overrides[]`: `when.type` 为 `dateRange` 或 `holiday`, `group` 为组 id.

状态 key 稳定 (如 `day` / `night`, `period:0`, `weekday:1`, `season:summer`, `script:0`), 切换状态源时按 key 迁移颜色.

### 6.3 交换格式

`ColorLibraryJson`: `format = "autojs6.color-library"`, `schemaVersion = 1`, 一个文件可含多个库 (D14, 草案见 Roadmap 附录 B). 导入总是新建库, 重名加序号; 高于支持版本的 schema 拒绝导入; 颜色为不透明的 `#RRGGBB`.

## 7. 状态源与日期

- `StateResolver.resolve(source, now, zone, facts, lastStateKey)` 是纯函数, 返回状态 key 与时间类状态的下一次变化时间. 胶水层只负责收集 `DeviceFacts` (夜间模式, 电量, 充电, 省电, 无障碍服务, 脚本运行, 脚本状态).
- 电量区间带 2 个百分点的回差 (`BATTERY_HYSTERESIS`), 避免在阈值附近抖动.
- 日出日落由 `SolarCalculator` 计算, 坐标默认由 `TimeZoneLocations` 按 IANA 时区推断, 可手动覆盖 (D20); 季节按半球.
- 日期覆盖层由 `DateOverrides.firstMatch` 解析, 节日来自 `HolidayCalendar` (含 `ChineseLunarCalendar` 的农历节日, D21).

## 8. 脚本接口

`colors` 模块的感知色彩函数 (`runtime/api/augment/colors/Colors.kt`) 直接调用 `theme/color` 的纯算法. `colors.theme` (`ColorsTheme.kt`) 的写操作经 `ThemeColorCoordinator.applyFromScript` 进入脚本窗口; 涉及引擎的操作在主线程执行并等待结果 (超时 10 秒). `ScriptThemeChanges` 作为协调器的 `CommitObserver`, 负责脚本修改的历史记录 (调色板历史, 来源 `SCRIPT` 与脚本名) 与撤销快照, 主题色页面菜单 "撤销脚本修改" 调用其 `undo`.

## 9. 可测性

| 层 | 测试 |
| --- | --- |
| 色彩算法 | `OkColorTest`, `ColorMetricsTest`, `ThemeColorDerivationTest`, `ColorHarmonyAndVisionTest`, `ColorQuantizerTest` |
| 协调与撤销 | `ThemeColorApplyPolicyTest`, `ScriptUndoTest` |
| 引擎核心 | `IntelligentColorPlannerTest`, `IntervalScheduleTest`, `DailyScheduleAndOverridesTest`, `StateResolverTest`, `IntelligentConfigJsonTest`, `IntelligentConfigValidatorTest`, `IntelligentEngineJsonTest`, `IntelligentAutoFillTest` |
| 日期 | `SolarCalculatorTest`, `ChineseLunarCalendarTest`, `HolidayCalendarTest`, `TimeZoneLocationsTest` |
| 库与选中 | `ColorSelectionCodecTest`, `ColorLibraryJsonTest`, `LibraryNamingTest`, `ColorUsageTest` |
| 脚本 | `ColorsPerceptualTest` (以 Int 为输入的内部函数, 避开 `android.graphics.Color`) |
| 迁移 | `androidTest/.../ColorHistoryDatabaseMigrationTest` (需设备) |

JVM 环境只有 JUnit4, 没有 Robolectric, 因此任何新逻辑都应先放进纯对象再由胶水层调用. 运行: `:app:testAppDebugUnitTest --tests "org.autojs.autojs.theme.*"`. 设备冒烟脚本位于 `docs/dev/smoke/intelligent-color-library/`, 可经 `RunIntentActivity` 运行并读取 logcat 中的 `GlobalConsole`.

## 10. 扩展指南

- 新增状态源: 在 `IntelligentConfig` 中加 `StateSource` 子类与状态 key, 在 `IntelligentConfigJson` 中加类型键, 在 `StateResolver` 中解析; 需要设备状态时在 `DeviceStateMonitor` 中加 `Fact` 并只在 `factsFor` 命中时注册; 编辑器加选项与摘要 (`IntelligentSummaryFormatter`); 补单测.
- 新增换组时机: 加 `RotationTrigger` 与类型键; 由事件驱动的时机在 `IntelligentEvent` 中加事件, 并在规划器的 `fired` 条件中处理; 胶水层只在勾选时注册事件来源.
- 新增预设智能库: 在 `IntelligentPresets` 中分配 `0x100`~`0x1FF` 内的新 id, 组 id 一经发布不再改变 (激活快照按组 id 恢复).
- 修改数据库: 只增不减, 版本号加一, 新增 `Migration` 并注册到 `createBuilder`, 由 KSP 导出新的 schema JSON, 在 `ColorHistoryDatabaseMigrationTest` 中补测试.
