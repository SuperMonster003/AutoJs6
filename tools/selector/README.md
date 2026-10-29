# 选择器工具 (主题 selector)

配合 `docs/selector/ROADMAP.md` 使用 (P0 建立, P1.5 / P2 / P3 / P5.1 复测). 三个真机脚本都经 `RunIntentActivity` 启动设备上的 JS 脚本, 脚本把结果写成 JSON 文件, 驱动脚本 `adb pull` 后汇总; logcat 的 `GlobalConsole` 行只用作进度标记 (`AJ6SEL ...`, 结束标记 `AJ6SEL done`). 输出只有每项的通过 / 失败与关键数值, 不打印节点全文.

共同前提与约定:

- 宿主的无障碍服务须已开启 (脚本只等待服务就绪, 不打开设置页); 悬浮窗场景与黄金表需要悬浮窗权限. 需要人工确认的对话框由维护者在手机上操作, 脚本不点屏幕 (屏幕只用 `am start` 切换).
- 设备上的临时目录为 `/sdcard/Download/aj6-selector` (推送的脚本与结果文件), 可随时删除; 本机的中间文件在 `tools/selector/__pycache__/` (已忽略).
- `--new-modules on|off` 在运行前设置新脚本模块层的隐藏开关 (`key_$_new_script_module_layer`), 结束后恢复原值 (同 `tools/augment/device_bench.py`). 不给出时按设备现状运行, 输出首行与保存的文件都记录实际状态.
- 计时随宿主进程的调度组变化很大 (top-app 与 background 相差 4-6 倍), `device_bench.py` 在每个场景前后打印 PCY (`ta` / `fg` / `bg`); 对比修前修后时在同一设备, 同一场景与同一开关状态下进行.
- 运行用 `py -3.12 -I`; 共用的 adb 与脚本运行代码在 `_device.py`.

## device_bench.py + bench.js: 基准 (P0.1, P2.4, P3)

```
py -3.12 -I tools/selector/device_bench.py -s SERIAL [--scenarios settings main overlay] [--rounds 10] [--warmup 5]
    [--cpu-rounds 10] [--idle-rounds 3] [--cpu-ms 10000] [--new-modules on|off] [--save] [--label NAME] [--settings-package PKG] [--settle S]
```

场景: `settings` 系统设置首页 (`am start -S` 重启到首页; API 28 (G8441) 上隐式 intent 加 `-S` 报 "Intent does not match any activities", 改用解析出的组件 `-n`), `main` AutoJs6 主界面, `overlay` 桌面之上的原始悬浮窗 (进度条, 标签与 300 个 TextView, 由脚本创建, 经窗口过滤器 `{ packageName: AutoJs6 }` 搜索). 每项先预热 `--warmup` 次, 再计 `--rounds` 次, 输出中位数 / 最小 / 最大 (毫秒):

| 项 | 内容 |
| --- | --- |
| `full` | `selector().find().size()`, 全树遍历 (同时记录节点数是否稳定) |
| `miss` | `text('__aj6sel_none__').findOnce()` |
| `missId` | `id('aj6sel_none').findOnce()` (P2.3 快路径以精确 id / text 为入口) |
| `missMixed` | `textMatch(/^__aj6sel_none__$/).clickable(true).findOnce()` (正则声明在布尔条件之前, P2.2 的排序) |
| `hit` | `packageName(<场景包名>).findOnce()`, 命中根节点 (早命中) |
| `late` | `text(<DFS 顺序中最后一个唯一文本>).findOnce()` (晚命中, 单候选; 只记录其序号与长度) |
| `findOne600` | `text('__aj6sel_none__').findOne(600)` 的尝试次数 (`auto.stats.queries.attempts`), 耗时与进程 CPU (总量与每次尝试) |
| `findOne600Id` | `id('aj6sel_none').findOne(600)`, 同上 (P2.3: 轮询的中间尝试由 id 查找判定未命中) |
| `lateOne` | `text(<late 的文本>).findOne(600)`, 同上 (P2.3: 单候选命中经轮询入口, 期望非 null) |
| `cpuIdle` / `cpuFixed` / `cpuAssisted` | `--cpu-ms` 内进程 CPU (占一个核的百分比, `Process.getElapsedCpuTime`) 与尝试次数: 只休眠 (下限), 默认固定间隔轮询, `eventAssistedPolling` 标志 |
| `stormIdle` / `stormFixed` / `stormAssisted` | 同上, 悬浮窗场景中进度条与标签每 16 ms 更新 (事件风暴; 进程 CPU 含悬浮窗的绘制) |

轮询的三项与 CPU 各项同时累计 `auto.stats.fastPath` (P2.3 快路径的命中 / 未命中 / 回退 / 不一致次数, 宿主未提供时不输出); 不一致 (查找为空而完整遍历找到节点) 大于 0 时该项判失败.

`--settings-package`: 设置首页活动窗口的根节点不属于设置应用本身时给出其包名 (ColorOS 15 的 OPD2413: `com.oplus.wirelesssettings`), 否则场景判为不符; parity 同. `--settle`: 打开屏幕后再等 S 秒, 用于打开后还在加载内容的屏幕, parity 与 bench 同. OPD2413 (平板) 的设置首页是双栏, 活动窗口为右栏的 WLAN 列表, 条目随扫描结果增减与重排 (DFS 顺序约从第 96 个节点起变化, 最后一个唯一文本是某个网络名, 可能在测量中途消失), 因此不存 parity 快照, 只比同一构建的两次运行; bench 的 `lateOne` 在该屏幕上可能因网络消失而失败.

`--save` 把汇总 (含各轮样本, 不含节点文本) 写到 `baseline/bench-<model>[-<label>].json`. 修前基线见 ROADMAP 附录 A.

## device_parity.py + parity.js: 结果对照 (P0.2, P2.1-P2.3, P5.1)

```
py -3.12 -I tools/selector/device_parity.py -s SERIAL [--scenarios main settings] [--runs 2] [--new-modules on|off] [--save] [--check]
    [--settings-package PKG] [--settle S]
```

在固定屏幕 (AutoJs6 主界面, 系统设置首页) 上运行一组选择器 (全树 DFS / BFS, 精确 id / 短 id / `idMatch`, 精确 text / `textContains` / `textMatch` / `descMatch`, className + clickable, `boundsInside`, 深度范围, `filter` 函数, 对象选择器与混合数组的 `pickup`, 内容 `pickup`, `select('...')` 字符串语法, BFS, `findOnce(index)`, `findOnce`, `untilFind`), 把每个 `find()` 结果序列记为 `[fingerprint(), depth(), indexInParent()]`. 选择器需要的字面值 (id, 文本, 类名) 由屏幕按固定规则从全树 DFS 结果推出, 快照只存其哈希; 比较时哈希不同即屏幕已变化, 结果不可比.

轮询入口另有 7 个用例 (P2.3): `id(<全名>)` / `id(<短 id>)` / `text(<精确>)` / `text(<唯一文本>)` 的 `findOne(2000)`, `text(<唯一文本>).untilFindOne()`, `id(<全名>)` / `text(<精确>)` 的 `untilFind()`, 每个都与同一次运行中对应 `find()` 的结果逐节点比较 (不进快照), 并输出期间的 `auto.stats.fastPath`. `coverage` 一行统计根节点上的框架查找 (`findAccessibilityNodeInfosByText` / `ByViewId`) 对屏幕上不同文本 / id 的漏查数, 文本漏查按类名分组 (只有计数, 不含屏幕文本).

`--runs` 次运行逐项比较 (稳定屏幕应 100% 一致); `--save` 把第一次写到 `baseline/parity-<model>-<scenario>.json`; `--check` 把第一次与该文件比较 (P2 的 "顺序与数量不变" 判据). 主界面的文件列表或设置首页的内容变了 (新文件, 账号, 电量文字) 时需在修前构建上重新 `--save`.

## device_pick_table.py + pick_table.js: pickup / detect 黄金表 (P0.3, P1.5, P5.1)

```
py -3.12 -I tools/selector/device_pick_table.py -s SERIAL [--runs 2] [--new-modules on|off] [--save] [--check] [--show 20]
```

在脚本自建的原始悬浮窗 (固定的合成内容, 文本与描述以 `aj6pt` 开头, 布局见 `pick_table.js` 开头) 上枚举 `pickup` / `detect` 的调用, 每行记录结果的形状 (类型名, 数组长度, 字符串值, 节点摘要 [类名, 文本, 描述, 深度, 序号]) 或错误 (`name`, Java 异常类, 消息前 160 个字符), 回调行另记回调参数的形状与调用次数. 分节 (行 id 的第一段):

- `types`: `PickResultType` 的全部 47 种拼写与 12 种非拼写参数 (空串, null, 数字, 布尔, 空列表, 单元素列表 `['#']` 等, 带参数的 `['#', 'x']`, 对象) x 罗盘 (缺省 / `p` / `c0` / `s>1` / `k2`) x 选择器 (一个命中 / 三个命中 / 无命中) x (无回调 / 观察回调).
- `detect`: 同样的类型与罗盘 x (行节点 / null) x 回调.
- `forms`: 选择器参数的各类形式 (字符串, 正则, UiSelector, 对象及其错误键值, 数组, 数字, null / undefined / 布尔, 未调用的方法, 函数, Java 对象, UiObject, `select('...')`) x 结果类型.
- `roots`, `arity`, `callbacks`: root 形式, 参数个数与位置 (含罗盘与结果类型的歧义串), 回调的替换 / null / 抛出.
- `invoke`: 拦截表内外的参化调用 (`['child', 0]`, `['setText', 'x']`, `['bounds']`, `['hintText']`, 未知名字等), 在命中, 无命中与 EditText 上.
- `sweep`: `UiObject` 全部公开无参方法按名称调用 (D11 注册表的范围), 命中与无命中各一遍. `showTooltip` 在没有 tooltip 的 View 上执行会让框架在主线程抛出空指针 (`View.showLongClickTooltip`), 曾使 AutoJs6 自身崩溃 (22120RN86C, API 33, 2026-10-25; MIUI 随后把宿主与两个插件的无障碍服务移出已启用列表, 由维护者重新开启), 因此它与 `hideTooltip`, 拖放, 按住, 文本建议, `imeEnter`, `paste` 只在无命中时按名称调用 (ROADMAP 附录 C Q8).
- `exists`: `existsAll` / `existsOne`.

`--runs` 次运行逐行比较; `--save` 写到 `baseline/pick_table-<model>.json` (开关开) 或 `pick_table-<model>-legacy.json` (开关关, 旧 augment 层), 一行一条, diff 即可看出变化的行; `--check` 与该文件比较并按分节统计差异. P1.5 允许的差异只有 D6 (b) 与 P1.1 的错误类型, 逐条列入 ROADMAP 附录 A.

## registry_export.py: 节点方法注册表导出 (P4.2 / P4.3)

```
py -3.12 -I tools/selector/registry_export.py [--docs DIR] [--dts DIR] [--ace DIR] [--check]
```

不需要设备. 注册表 (`NodeMethodRegistry.uiObject`, D11) 由 JVM 用例 `NodeMethodRegistrySnapshotTest` 固化在 `app/src/test/resources/node-method-registry.txt` (每个方法一行: 分类 `own` / `compat_getter`, 名称与参数类型); 注册表变化时该用例失败并把新清单写到 `app/build/node-method-registry.txt`, 确属有意就用它覆盖资源文件, 再运行本脚本. 脚本读该文件, 改写三处标记之间的生成块:

- 文档仓库 `api/dataTypes.md` (`<!-- node-method-registry:begin -->` ... `end -->`): `UiObjectInvokable` 的可调用方法清单 (自有方法与 compat 只读方法两组, 带参数的重载列出参数类型).
- 声明仓库 `declarations/autojs6/aj6-int-selector.d.ts` 与 Ace Editor 插件中的副本 (`// <node-method-registry:begin>` ... `end>`): `Detect.ResultTypeFuncWithoutParams` (有无参重载的拼写) 与 `Detect.ResultTypeFuncWithParams` (每个带参重载一个元组), `Pickup.ResultType` / `Detect.ResultType` 包含它们.

拼写为方法名, getter `getXyz` 另有 `xyz` (与已有名称同名时不列). 三个仓库默认为宿主的同级目录, 给 `-` 跳过; `--check` 不写文件, 生成块与注册表不一致时退出码为 1. JVM 看到的是最新 API 的注册表 (2026-10-29: 206 个名称, 218 个方法), API 24-29 上 `getExtraRenderingInfo` 不登记 (附录 C Q16), 文档注明 API 30+.

## device_sample_explain.py: 应用内示例与冒烟核对 (P3.5, P4.4, P5.1)

```
py -3.12 -I tools/selector/device_sample_explain.py -s SERIAL [--samples explain normalize flow form list string events smoke] [--show-lines]
```

把仓库中的示例原样推送到设备运行, 按控制台输出逐项核对 (只打印核对项名称与通过 / 失败; `--show-lines` 另打印控制台行, 均为示例悬浮窗中的合成文本): `explain` 为 "无障碍服务 > 选择器诊断与节点树导出" (`auto.explain` 的逐过滤器计数, 单个结果, 罗盘每一步, 结果类型与结果对象的各字段, 写错的结果类型, 快路径的键, `dump` 三种形式, `toJSON`, `auto.stats`, 22 项), `normalize` 为 "无障碍服务 > 选择器规范化与候选偏好" (`normalize` 的重载, 选项, 写法与适用属性, `prefer` 的每个键, 键的顺序, 罗盘 `k`, 各种写法与报错, 20 项). P5.1 发布门禁另加前序 a11y roadmap P6.4 的其余 5 个示例: `flow` ("流程 (Flow) 异步等待与链式动作", 10 项), `form` ("工具集 表单填写与弹窗关闭", 13 项), `list` ("工具集 列表滚动与采集", 9 项), `string` ("字符串选择器与惰性迭代", 13 项), `events` ("事件驱动等待", 7 项, 第 5 / 6 项需要 AutoJs6 的通知权限), 以及开发冒烟 `smoke` (`sample/accessibility/smoke-legacy-api.js`, 按其 "N/N checks passed" 汇总与有无 `[FAIL]` 判定; 它以 AutoJs6 主界面为目标, 会点击一个控件并滚动, 只在宿主能无人值守重启无障碍服务 (root, 安全设置或 Shizuku) 时才做服务重启的检查, 否则跳过; 它会把主界面的列表滚下去, 之后 parity 的主界面快照不再可比, 因此门禁中先跑 parity 再跑示例, 或之后把主界面滚回顶部). 不给 `--samples` 时全部运行. 示例先回到桌面, 在自己的悬浮窗上运行, 需要无障碍服务与悬浮窗权限, 屏幕须已解锁; 不点屏幕.

## device_node_instances.py: 节点实例数 (P5.1, 红线 2)

```
py -3.12 -I tools/selector/device_node_instances.py -s SERIAL [--warmup 20] [--rounds 300] [--limit 50]
```

在 AutoJs6 主界面上循环本主题的查询 (每轮: `find()`, 命中刷新的 `findOnce()` 与其 `children()` / `parent()`, `prefer` 的单个结果, 精确 id 快路径的 `findOnce()` / `findOne(30)`, `text` 的 `findOne(30)` 轮询, 带罗盘与结果类型的 `pickup`, 含多键 `prefer` 的字符串选择器), 用 `dalvik.system.VMDebug.countInstancesOfClass` (memory 门禁的口径: 先 GC, 只计可达实例; 隐藏 API, 宿主在 API 24 / 28 上可用) 读 `AccessibilityNodeInfo` 的实例数: 开始, 预热结束, 一半与结束. 结束相对预热结束的增长不超过 `--limit` (+50, 同 memory 门禁 S7 / S7b) 且每轮的查询都得到预期结果时通过. 只输出轮数, 节点计数与实例数, 不含节点文本. 主要用于 API < 33 (节点按进程池化, 前序 P1.5).
