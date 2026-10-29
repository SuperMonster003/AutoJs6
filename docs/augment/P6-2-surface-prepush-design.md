# P6.2 表面快照比对纳入 prepush_check 的快速检查

ROADMAP P6.2 的设计. 本文比较三种可组合的形态, 第 4 节是拍板结果 (附录 C Q175-Q177), 第 5 节是实施结果.

## 1. 现状

- 表面快照只在真机上取: `tools/augment/device_surface_snapshot.py` 经 `RunIntentActivity` 在前台运行 `surface_snapshot.js` (约 4 s, 不含装包), 输出按行排序的文本, 由 `surface_compare.py` 比对. P5.2 矩阵的 XQ-AT72 快照 (versionCode 5935) 共 25394 行: `$` 别名 4683 行, `global.` / `globalThis.` 自引用 11908 行, 其余 8805 行; 再去掉函数自身的 `arity` / `length` / `name` 行, 是 3517 个属性 (其中 305 个是 `--roots` / `--global-classes` 的 `@` 行).
- `prepush_check.py` 对宿主跑 `.python/generate_markdown.py --check`, CI 引用的 python 单测目录, 以及 `android.yml` 中的 Gradle 任务 (`:app:testAppDebugUnitTest` 等); 文件头写明不跑模拟器与设备测试. 所以 `:app` 里的单测自动进入快速检查, CI 也跑, 不必改 `prepush_check.py`.
- 已有的局部比对: `ScriptModuleOrderTest` (注册顺序与顶层名, 对 `registrations.json` 与旧快照 5586), `ScriptModuleMounterTest.base64MatchesTheSurfaceBaseline` (只比 base64).

## 2. 三种形态

原型 (只在隔离克隆中, 未入库): 单测里用 `DifferentialRhino` + `RuntimeModulesFixture` 挂载 `ScriptModuleLayer.TOP_LEVEL_ORDER` 的全部 76 个顶层模块, 运行同一个 `surface_snapshot.js`, 再用 `device_surface_snapshot.flatten` 展平成设备格式, 与上面的设备快照对照. 元数据投影用 Python 原型读 `script-modules.json`.

| | (1) JVM 快照 | (2) script-modules.json 元数据 | (3) 有设备才跑 |
| --- | --- | --- | --- |
| 做法 | 单测挂载全部顶层模块, 运行 `surface_snapshot.js`, 展平成与设备相同格式的行, 与基线比 | 单测读 KSP 生成的 `script-modules.json`, 每个模块 / 函数 / accessor / install / javaClass 投影成一行 (名称, 属性, global, length, 别名, 重载数与各重载 arity), 与基线比 | prepush_check 加宿主步骤: adb 有设备且已装当前 versionCode 时取设备快照, 与该机型的基线比; 否则打印原因跳过 |
| 覆盖 | 顶层名 572 / 583, 缺的 13 个是引擎初始化定义的 (`require`, `Module`, `runtime`, `__engine__`, `ResultAdapter`, `structuredClone` 等 12 个) 与原型里挂载失败的 `ui`; 3212 个属性 (不含 `@` 行) 中有 2793 个 (87%), 其中 2738 行与设备逐字相同 | 93 模块, 1051 函数 (1077 重载), 97 accessor, 101 install, 4 javaClass, 共 1346 行; 没有运行时才有的东西 (install 钩子写入的成员, JS 定义的成员, 原型链, 常量值), 但有设备快照看不到的各重载 arity (Q19 一类差异) | 全部 (含引擎初始化与 JS 库), 但只代表该机型与插件安装状态 |
| 单次耗时 (不含构建) | 原型在本机高负载下 (同时有 Test262 与其他会话的 3 个 Gradle 构建): 挂载 16.1 s + 快照 11.3 s; 实施时在 JS 里直接输出行 (省去 3 MB 的 JSON 往返) 并复测, 目标 30 s 以内 | 1 s 以内 (Python 原型 20 ms) | 快照 4 s + adb 与核对 versionCode 约 10 s; 装包另算 |
| 基线 | `surface-jvm.txt` 约 7300 行 (340 KB): 不存 `global.` 重复, `$` 别名只记是否同一对象 | `script-modules-surface.txt` 1346 行 (130 KB) | 每个机型 x 插件安装状态一份, 每份约 25400 行 (1.3 MB) |
| 误报来源 | 随构建变化的值: 版本号, 版本日期, 资源 id (`app.autojs.name` 为 `res:2131886176`), 需掩码; 单测 JVM 里类初始化的先后 (第一次是 `ExceptionInInitializerError`, 之后是 `NoClassDefFoundError`), error 行只记 `error`; 改 `RuntimeModulesFixture` 或升级 Rhino 会整片变化 | 只有注解改动, 而这正是要报的 | 装的不是当前 HEAD 的构建; 机型差异 (XQ-AT72 没有 `normalize` / `prefer`, G8441 / XQ-DQ72 有); 插件装没装; 在前台启动 Activity, 与其他会话抢设备 (locks.md) |
| 维护 | 有意改表面时跑更新命令, 提交基线差异 (新增一个函数约 4 行) | 同左 (新增一个函数 1 行) | 按机型更新, 需要设备 |
| 代码量 (估计) | 单测约 200 行, `surface_snapshot.js` 小改, 更新脚本约 80 行 | 与 (1) 同一单测类, 约 80 行 | python 步骤约 120 行, `prepush_check.py` 加宿主步骤发现约 15 行 (影响所有仓库的 prepush 流程) |

(1) 与设备不同的 55 行, 多数是 JVM 上的值本来就不同 (`device.*`, `WIDTH` / `HEIGHT` 读屏幕尺寸报 not mocked, 版本号) 或引擎初始化给 `Promise` / `android` 设的属性不同; `Math` 的 67 个成员缺失是原型没有先解析 Rhino 的惰性构造器, 实施时补上. JVM 快照只与 JVM 基线比, 不与设备快照比.

## 3. 推荐

(1) + (2), 合成一个单测类 `ScriptSurfaceGateTest` (两个测试方法), 随 `:app:testAppDebugUnitTest` 进入 prepush_check 的快速检查与 CI `android.yml`; (3) 不进 prepush, 设备快照仍在发布矩阵里人工取 (`device_surface_snapshot.py`).

- (1) 管运行时真正挂出来的东西, (2) 管 (1) 看不到的重载 arity, 两者都不需要设备, 合计 30 s 以内.
- (3) 的结论依赖装在手机上的构建是否就是当前 HEAD, prepush 无法保证; 还会在前台启动 Activity, 与 locks.md 的设备排队冲突, 基线也要按机型与插件状态分份.
- 不改 `prepush_check.py`, 不影响其他仓库.

做法:

- 单测把实际结果写到 `app/build/script-modules-surface/` (`surface-jvm.txt`, `script-modules-surface.txt`), 与 `tools/augment/baseline/` 下的同名基线比; 不同时失败, 消息列出增减的行数, 前 40 行差异与更新命令.
- 更新命令: 先跑单测, 再 `py -3.12 -I tools/augment/surface_gate.py --update` (把 build 下的实际结果复制成基线); `--diff` 只打印差异. 命令同时写在单测与脚本的文档注释里.
- 基线缺失时单测跳过并说明原因 (Assume), 不失败.

## 4. 决定

维护者 2026-10-29 拍板, 三项都选推荐项 (附录 C Q175-Q177):

- Q175 形态: (1) + (2) 合成一个单测, (3) 不进 prepush.
- Q176 不一致时的处理: 单测失败 (prepush 与 CI 都会报); 未选: 只警告.
- Q177 与并行卡片的先后: 现在实施; 运行时基线在 augment-bl-publish 的阶段 5 提交后重新生成; 之后改表面的会话 (augment-q19, api-coverage 等) 在自己的提交里跑更新命令, 看板写明.

## 5. 实施结果

文件:

- `app/src/test/java/org/autojs/autojs/runtime/api/modules/ScriptSurfaceGateTest.kt`: 两个测试方法 `runtimeSurface` ((1)) 与 `metadataSurface` ((2)).
- `tools/augment/surface_gate.py`: `--diff` (默认) 打印 build 下的实际结果与基线的差异, `--update` 把实际结果复制成基线.
- `tools/augment/surface_snapshot.js`: 把查找名与 `$` 别名两段抽成函数 `describeLookup` / `describeAliases`, 设备输出不变; 单测运行 `let snapshot = {` 之前的全部函数, 后半段换成单测自己的部分.
- 基线 `tools/augment/baseline/surface-jvm.txt` 与 `tools/augment/baseline/script-modules-surface.txt`, 在 `37449fe83` (已含 augment-bl-publish 阶段 5 `67ef4c1a5` 与 imgui 注册 `61317ec4d`) 上生成.

更新命令 (有意改动表面时, 与改动放在同一个提交里):

```
gradlew :app:testAppDebugUnitTest --tests org.autojs.autojs.runtime.api.modules.ScriptSurfaceGateTest
py -3.12 -I tools/augment/surface_gate.py --update
```

(1) JVM 快照的做法与边界:

- 单测给 `ScriptRuntime` 中为 null 的宿主字段分配实例 (`java.` 类型调构造函数, 其他类型不经构造函数分配, 再给其自身的 `java.util` 字段放空实例), 挂载 `ScriptModuleLayer.TOP_LEVEL_ORDER` 的全部顶层模块, 在解释模式下运行快照 (编译模式在 `DifferentialRhino` 里不可用).
- `ui` 挂不上, 记为 `@mount.ui error` 一行: 它的 install 要改 `UI` 对象, 而不经构造函数分配的脚本对象没有属性表; 试过给 `org.autojs` 字段也分配实例, 反而让其他模块读到半成品对象而使快照失败, 已撤回. 挂载失败的原因打印在测试输出里.
- Rhino 的惰性构造器 (`JSON`, `Math`, `Reflect` 等) 先解析再描述, 所以比设备快照多出它们的成员; E4X 的 `XML` / `XMLList` / `Namespace` / `QName` 不解析 (这里没有绑定 XML 库, 读 `XML.prettyPrinting` 抛脚本捕获不到的 Java 异常), 与设备上一样记为未解析的 `LazilyLoadedCtor`.
- 值为全局对象自身的属性 (`globalThis`, `global`) 与和本名是同一值的 `$` 别名只记一行, 不重复展开; `$` 别名是否同一值记在 `@alias.` 行.
- 掩码: 版本号 / 版本名 / 版本日期 (`autojs.versionCode` 等与 `autojs.version.*` / `app.*` 同名项) 的值记为 `<build>` (只留类型), 资源 id 记为 `res:<id>`, 描述时抛错的属性只记 `error` (JVM 里类初始化的先后会改变异常类型). `device.*`, `WIDTH` / `HEIGHT` 等读 Android 服务的属性在 JVM 上恒为 `error`, 照记.
- 引擎初始化时定义的名称 (`require`, `Module`, `runtime`, `__engine__` 等) 不在此表面内, 仍由设备快照覆盖.

(2) 元数据投影: 宿主 `autojs6/script-modules.json` 的每个模块 (`module` 行, 名称 / 别名 / 属性 / `$` 前缀 / eager / emitter / install 等字段), 函数 (`fn` 行, 属性 / global / length / 别名, 以及 `overloads=` 各重载的 `min-max` 与参数形态, `r` 必填 / `o` 可选 / `v` vararg, 用 `;` 分隔), accessor, install (`install 模块:名称`, 含 arity) 与 Java 类各一行.

验证 (隔离克隆, `37449fe83` + 本改动):

- 基线: `surface-jvm.txt` 9538 行 (452 KB, 其中 imgui 1723 行), `script-modules-surface.txt` 1812 行 (231 KB, 其中 imgui 460 行). 比设计时的估计 (7300 + 1346 行) 多出的主要是 imgui (`61317ec4d`) 与 speech 的新函数.
- 单次比对耗时 (不含构建, 取测试报告): `runtimeSurface` 3.8-5.6 s, 本机另有构建时 11.2 s; `metadataSurface` 0.3-0.5 s.
- 干净树: 生成基线后 `--rerun` 两项通过, `surface_gate.py --diff` 两份都是 0 removed, 0 added.
- 全量: `:app:testAppDebugUnitTest` 954 类 5868 例 1 失败 27 跳过, 两项门禁在全量中也通过 (`runtimeSurface` 7.1 s, 与其他测试同进程, 不受先后影响); 唯一的失败是基线已有的 `BunAutomationPolicyTest.publicAutomationDoesNotExposeInternalAgentRoutes` (期望 194 实为 204, 附录 A "P5.2 阶段 5" 已记, 与本改动无关). `:app:assembleAppDebug` 通过.
- 注入: 在 `EpubModule` 中把 `open` 的 `@ScriptFunction(arity = [1])` 改为 `[2]`, 把 `metadata` 改名为 `metadataX`, 删去 `@ScriptInstall`, 两项都失败. `metadataSurface` 报 4 removed, 3 added (`fn epub.open` 的 `overloads=1-1(v)` -> `2-2(v)`, `fn epub.metadata` -> `fn epub.metadataX`, 少了 `install epub:EpubError`, `module epub` 的 `install="install"` -> `null`); `runtimeSurface` 报 9 removed, 4 added (少了 `epub.EpubError` 及其 4 个成员, `epub.metadata` 的 4 行换成 `epub.metadataX`). `open` 是 vararg, 运行时 length 恒为 0, 所以 arity 的改动只有 (2) 看得到, 与第 2 节的分工一致. 消息带增减行数, 差异行, 实际结果路径与更新命令.

已知限制: Gradle 的测试任务没有把 `tools/augment/` 声明为输入, 只改基线 (或 `surface_snapshot.js`) 而代码未变时, `:app:testAppDebugUnitTest` 可能被判为最新而不重跑; 加 `--rerun` 或改动代码后即会运行 (宿主工作区默认每次构建递增 VERSION_BUILD, 测试任务通常都会重跑). 未改 `build.gradle.kts`.
