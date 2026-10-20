# Augment 重构工具

配合 `docs/augment/ROADMAP.md` 使用. 这些工具都只读宿主源码或已安装的宿主, 不修改设备上的宿主数据; 真机脚本通过 `RunIntentActivity` 启动, 结果从 logcat 的 `GlobalConsole` 行读取 (轮询累积, 从不清空 logcat).

## device_bench.py: 真机计时

```
py -3.12 -I tools/augment/device_bench.py -s SERIAL [--runs 5] [--skip-compile] [--skip-calls] [--new-modules on|off]
```

1. `bench/aj6_init_timing.js` 跑 `--runs` 次, 读取 debug 宿主打印的 `runtime-init-prologue` / `script-init` / `runtime-init-epilogue` (毫秒) 并给出最小 / 中位 / 最大值. release 宿主不打印这些行, 只会得到总用时.
2. `bench/obsolete_compile_timing.js` 把 `assets/modules/obsolete/` 推到设备, 在脚本里用 `new Function` 编译 45 个旧 JS 模块各 3 遍, 打印每遍总耗时与最慢的 8 个文件 (只编译不执行).
3. `bench/call_overhead.js` 对比普通 JS 函数, Rhino 内置函数, Augment 函数 / getter / 全局函数, 以及同一 Java 方法经 Rhino 自带桥接的单次耗时 (20 万次), 最后测 1000 次失败调用的异常路径.

基线数值见 ROADMAP 附录 A. 设备上的临时目录为 `/sdcard/Download/aj6-augment-bench`, 可随时删除.

`--new-modules on|off` 在计时前用一个小脚本设置新脚本模块层的隐藏开关 (ROADMAP T8, 偏好设置键 `key_$_new_script_module_layer`, 经 `Pref.putBooleanSync` 写入), 结束后恢复原值; 开关只影响之后启动的脚本.

## device_surface_snapshot.py: 真机 API 表面快照

```
py -3.12 -I tools/augment/device_surface_snapshot.py -s SERIAL [--out tools/augment/baseline]
```

在设备上运行 `surface_snapshot.js`, 枚举 `global` 的全部自有属性, 对模块对象再向下两层记录每个属性的特性 (可枚举 / 可写 / 可配置 / 访问器), 值的种类 (函数名与参数个数, 对象标签, Java 类名, 原始值) 与原型链, 并记录 `Object.keys(global)` 的顺序和 `$` 别名的同一性. 结果转换为按路径排序的文本 `surface-<model>-<versionCode>.txt` (一行一个属性, 便于 diff), 原始 JSON 放在同名 `.json`. 访问器属性只记录存在性, 不求值; 数据属性的值会被读取, 因此快照过程会触发 Java 侧 getter.

新实现切换前, 对同一设备再生成一份并与基线 diff, 只允许出现 ROADMAP 红线 R1 允许的差异.

## device_eval.py: 真机复核差分测试记录的 Kotlin 值

```
py -3.12 -I tools/augment/device_eval.py -s SERIAL [--cases DIR_OR_FILE ...] [--keep] [--new-modules on|off] [--strict-errors]
```

JVM 差分测试 (`app/src/test/java/org/autojs/autojs/runtime/api/augment/differential/`) 运行时把已记录的偏差与 JVM 上只能跳过的表达式导出到 `app/build/augment-differential/<测试类>.json`. 本脚本把这些表达式合成一个脚本在设备上运行, 按 `DifferentialRhino.render` 的规则渲染结果, 与 JVM 上记录的 Kotlin 值比对 (两侧都抛出时视为一致, JVM 构造不出 Kotlin 的错误消息); 跳过的表达式没有期望值, 只打印设备结果. 输出只有不一致的条目与汇总行. 先运行差分测试生成 JSON; 设备上的临时目录 `/sdcard/Download/aj6-augment-bench` 用完即删 (`--keep` 保留).

新脚本模块层的 JVM 测试 (`app/src/test/java/org/autojs/autojs/runtime/api/modules/`) 把新模块的期望值导出到 `app/build/script-modules-device/*.json` (同一格式, 期望值在 `kt`). 复核新模块时打开开关并比较错误类型: `--cases app/build/script-modules-device --new-modules on --strict-errors` (`--new-modules` 与 device_bench.py 相同, 结束后恢复原值; `--strict-errors` 让两侧都抛出时也要求错误名一致). `--debug-print on|off` 同样临时设置新模块层调试打印的总开关 (附录 C Q4, 键 `key_$_script_module_debug_print`), `--console` 打印本次运行期间 logcat 中 `GlobalConsole` (脚本控制台) 与 `ScriptModules` 标签的行, 用于查看调试条目原文.

## device_developer_options.py: 打开开发者选项页并截图

```
py -3.12 -I tools/augment/device_developer_options.py -s SERIAL [--out DIR] [--new-modules on|off] [--debug-print on|off]
```

`DeveloperOptionsActivity` 未导出, adb 的 `am start` 打不开; 本脚本经 RunIntentActivity 运行一个小脚本, 在应用进程内以 `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP` 打开它 (已打开的页面会重建, 显示刚写入的偏好), 等待后 `screencap` 拉回到 `--out` (默认 `tools/augment/__pycache__/`), 不点击屏幕. `--new-modules` / `--debug-print` 先写入对应的偏好 (ROADMAP P1.3 的 "脚本模块" 类别), 不自动恢复, 需要时以原值再运行一次. 屏幕须已点亮并解锁, 否则脚本只报告并退出.

## inventory_diff.py: 旧 JS 成员名 对 Kotlin 注册名

```
py -3.12 -I tools/augment/inventory_diff.py [--host D:/idea-projects/AutoJs6] [--dts D:/idea-projects/AutoJs6-TypeScript-Declarations/declarations/autojs6] [--output report.json]
```

用正则从 `assets/modules/obsolete/__xxx__.js` 收集方法, getter 与对象字面量键, 与 `tools/api-coverage/extract_exports.py` 解析出的 Kotlin 注册名比对, 再用 d.ts 公开声明过滤. 这是启发式的: Java 原型链成员, 子模块 (如 `util.java`), 返回对象的成员与 JS 内部辅助函数都会被列为 "仅 JS", 需要人工核对. 它只用于初筛, 正式的兼容性证据来自 JVM 差分测试与真机表面快照.
