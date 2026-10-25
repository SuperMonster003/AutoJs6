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
    [--cpu-rounds 10] [--idle-rounds 3] [--cpu-ms 10000] [--new-modules on|off] [--save] [--label NAME]
```

场景: `settings` 系统设置首页 (`am start -S` 重启到首页), `main` AutoJs6 主界面, `overlay` 桌面之上的原始悬浮窗 (进度条, 标签与 300 个 TextView, 由脚本创建, 经窗口过滤器 `{ packageName: AutoJs6 }` 搜索). 每项先预热 `--warmup` 次, 再计 `--rounds` 次, 输出中位数 / 最小 / 最大 (毫秒):

| 项 | 内容 |
| --- | --- |
| `full` | `selector().find().size()`, 全树遍历 (同时记录节点数是否稳定) |
| `miss` | `text('__aj6sel_none__').findOnce()` |
| `missId` | `id('aj6sel_none').findOnce()` (P2.3 快路径以精确 id / text 为入口) |
| `missMixed` | `textMatch(/^__aj6sel_none__$/).clickable(true).findOnce()` (正则声明在布尔条件之前, P2.2 的排序) |
| `hit` | `packageName(<场景包名>).findOnce()`, 命中根节点 (早命中) |
| `late` | `text(<DFS 顺序中最后一个唯一文本>).findOnce()` (晚命中, 单候选; 只记录其序号与长度) |
| `findOne600` | `text('__aj6sel_none__').findOne(600)` 的尝试次数 (`auto.stats.queries.attempts`) 与耗时 |
| `cpuIdle` / `cpuFixed` / `cpuAssisted` | `--cpu-ms` 内进程 CPU (占一个核的百分比, `Process.getElapsedCpuTime`) 与尝试次数: 只休眠 (下限), 默认固定间隔轮询, `eventAssistedPolling` 标志 |
| `stormIdle` / `stormFixed` / `stormAssisted` | 同上, 悬浮窗场景中进度条与标签每 16 ms 更新 (事件风暴; 进程 CPU 含悬浮窗的绘制) |

`--save` 把汇总 (含各轮样本, 不含节点文本) 写到 `baseline/bench-<model>[-<label>].json`. 修前基线见 ROADMAP 附录 A.

## device_parity.py + parity.js: 结果对照 (P0.2, P2.1-P2.3, P5.1)

```
py -3.12 -I tools/selector/device_parity.py -s SERIAL [--scenarios main settings] [--runs 2] [--new-modules on|off] [--save] [--check]
```

在固定屏幕 (AutoJs6 主界面, 系统设置首页) 上运行一组选择器 (全树 DFS / BFS, 精确 id / 短 id / `idMatch`, 精确 text / `textContains` / `textMatch` / `descMatch`, className + clickable, `boundsInside`, 深度范围, `filter` 函数, 对象选择器与混合数组的 `pickup`, 内容 `pickup`, `select('...')` 字符串语法, BFS, `findOnce(index)`, `findOnce`, `untilFind`), 把每个 `find()` 结果序列记为 `[fingerprint(), depth(), indexInParent()]`. 选择器需要的字面值 (id, 文本, 类名) 由屏幕按固定规则从全树 DFS 结果推出, 快照只存其哈希; 比较时哈希不同即屏幕已变化, 结果不可比.

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
