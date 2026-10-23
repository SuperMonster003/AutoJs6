# P3.7 require 的 Kotlin 实现 (jvm-npm.js Kotlin 化) 设计

- 状态: 设计稿 (2026-10-23, 子会话 augment-p3-7), 分叉 Q80-Q84 待维护者拍板, 确认后实现.
- 对应: roadmap P3.7, T3 (按需挂载), T8 (开关), T11 (插件模块源), D10 (错误类型), R2 (性能); 附录 C Q80-Q84, 附录 E "P3.7".
- 现状代码: `app/src/main/assets/modules/jvm-npm.js` (404 行), `ScriptRuntime.initEpilogue`, `RhinoJavaScriptEngine.initRequireBuilder`.

## 0. 现状

- 启动: `initEpilogue` 经引擎的 Rhino Require 加载 `jvm-npm.js` (每个脚本执行一次模块体, 编译结果由 `ProcessWideAssetModuleScriptProvider` 跨脚本缓存), 定义全局 `Module` 与 `require` (= `Module.require`), 特性 PERMANENT (可枚举, 可写, 不可配置).
- 三条加载路径:
  1. 文件模块: jvm-npm 自己解析路径, 以 `new Function` 包装执行, 按规范路径缓存在 `require.cache`.
  2. 原生 Rhino Require (`initRequireBuilder` 安装的 `TypeScriptRhinoCommonJsRequire`, sandboxed): 处理 8 个内置名, `http(s)://`, 以及本地解析失败的所有 id. 这包括资产模块 axios / dayjs / cheerio / i18n / pangu / banana-i18n / promise / continuation / result-adapter / ui-ext 与它们的子路径, 找不到时报 Rhino 原生错误. 模块在 ModuleScope 中以脚本执行, 与 `Global` 的 axios 等 getter 共用同一缓存.
  3. TypeScript 项目: `requireMain` 走 Rhino Require, 项目模块内的 `require` 是 Rhino 的模块级 require, 不经 jvm-npm. P3.7 不改这条路径.
- 公开面:
  - 文档 `api/modules.md`: 解析顺序, 扩展名, 缓存, resolve / paths / root / main / extensions, Module 实例字段, 包装参数, 内置名.
  - d.ts `aj6-int-jvm-npm.d.ts`: `Internal.Require` 与 `Internal.Require.Module`.
  - 宿主内部调用方:
    - `ScriptRuntime.js_Module`: `NodeJavaScriptEngine` 调用 `runMain`.
    - `RhinoUtils.js_require`: 调用时按名读取全局 `require`, 调用方为 `Plugins.kt` 与 `bindModule`.
    - `rhinoRequireFunction`: 原生 Require 的引用, 供 Global getter, `ScriptPolyfills` 与 `ui-ext` 使用.
- 示例与资产: `示例代码` 中只有 `控件/自定义控件-使用配置勾选框.js` 使用 require (相对路径带扩展名, 被引用模块以 `module.exports` 导出, 依赖 ui). 资产模块内部的 require (如 `axios/axios.min.js`, `banana-i18n`) 走 Rhino 的模块级 require, 不受影响.

## (a) 解析规则

`require(id, parent)` (全局 require 与 `Module.require` 可传 parent, 模块内的 require 以本模块为 parent) 依次:

0. 参数: id 必须是非空字符串, 否则抛 TypeError (D10).
   - 关态: 非字符串时报 Rhino 的 TypeError "Cannot find function endsWith".
   - 关态: 空串可能误载 root 下 package.json 的 main 或 index.js.
1. 去掉 `node:` 前缀.
2. 核心模块名:
   - 命名模块源认为是核心名, 且工作目录下没有同名文件时, 交给命名模块源 (见 (f)).
   - P3.7 的核心名是资产源的 8 个: buffer, events, fs, lodash, nodejs, os, path, process (带不带 `.js` 都算).
   - "同名文件" 是 `files.exists('<名称>.js')`, 相对工作目录判断. 与关态相同.
3. `http://` / `https://`: 交给原生 Require (URL 源), 与关态相同.
4. 本地解析 `resolve(id, parent)`: roots 为 `[base, ...paths()]`, 对每个 root 依次尝试下列各项, 首个命中即为结果:
   - base: parent 有 id 时取其所在目录, 否则取 `require.root`. `require.root` 的初值为工作目录, 可赋值修改.
   - `paths()`:
     - `<user.home>/.node_modules` 与 `<user.home>/.node_libraries`. Android 上 user.home 为空串, 即 `/.node_modules` 等.
     - 再加环境变量 `NODE_PATH` (按 `:` 分隔). 关态内部的 `NODE_PATH` / `debug` 属性在 RequireCtor 实例上, 脚本拿不到, 保持不公开.
   - (1) 类加载器资源 `<id>.js`: 关态的 resolveCoreModule. Android 上几乎不会命中. 它与 root 无关, 关态每个 root 查一次, 新实现只查一次.
   - (2) 文件 `<root>/<id>`, 依次补 `.js`, `.mjs`, `.json`. id 已带这三种扩展名之一 (不分大小写) 时不补. 绝对路径的 id 不拼 root, 找不到文件时按目录解析.
   - (3) 目录 `<root>/<id>`:
     - 有 `package.json` 时取其 `main`, 先按文件 (只补 `.js`) 再按目录解析; 没有 main 时取 `index.js`.
     - 没有 package.json 时取 `index.js`.
     - package.json 不能解析时抛 PARSE_ERROR.
   - (4) node_modules: `<root>/node_modules/<id>` 按文件 (只补 `.js`) 与目录解析, 再到 root 的父目录, 直到根目录.
   - 结果为规范路径 (`File.getCanonicalPath`, 解析 `.` / `..` 与符号链接).
5. 本地解析失败时交给命名模块源: 资产模块及其子路径; P4.3 起加上插件模块. 都没有时抛 "找不到模块" 错误 (Q83).
6. 加载:
   - 缓存命中直接返回 (见 (b)).
   - `.js` / `.mjs` (不分大小写) 以函数包装执行.
   - `.json` 解析后直接存入缓存.
   - 类加载器资源命中的模块: 文件名为资源名, `__dirname` 为 `''`.

保留的非 Node 行为 (两态相同, 文档已写或属现状, 登记附录 E 作 "保持"):

- 裸名 (如 `utils`) 先按 base 下的文件与目录解析, 再查 node_modules.
- 相对 id (`./x`) 在 base 下找不到时同样查 node_modules 与 `paths()`.
- `.mjs` 按 CommonJS 执行.
- package.json 只读 `main`, 不支持 `exports` 字段.
- 模块失败时不从 `parent.children` 中移除.
- `require.main` 只由 `Module.runMain` 设置, 普通脚本中为 undefined.

文件探测的代价与剪枝:

- 典型的找不到 (主脚本在 `/storage/emulated/0/脚本` 下 `require('./nope')`): 关态约 51 次 `File.exists` 与 3 次类加载器资源查找. 其中第一个 root 约 23 次 (6 层目录各 3 次), 两个 `paths()` root 各约 14 次.
- 剪枝: 新实现在 `<dir>` 不存在时跳过 `<dir>/...` 下的全部探测. 路径的中间目录不存在时 stat 必然失败, `..` 也要经过该目录, 所以结果不变.
- 剪枝后同一情形约 15-20 次.
- 命中时与关态相同: 第一个存在的候选即返回.

## (b) 缓存与隔离

缓存:

- `require.cache` 是普通对象, 全局 require, `Module.require` 与各模块的 require 共用同一对象.
- 键为模块的规范路径 (Q82), 等于 `module.id` / `module.filename` / `__filename`.
- 值为 Module 实例 (JS 模块) 或 JSON 的解析结果.
- 写入时机: 执行模块代码之前写入. 循环依赖因此拿到未完成的 `exports`, 同 Node 与关态.
- 模块抛出时删除该键并原样重抛 (D10: 脚本错误原样透传), 再次 require 会重新执行.
- 脚本 `delete require.cache[k]` 后再次 require 会重新加载.
- 命中时: Module 实例返回其 `exports`, 其他值 (JSON) 原样返回.
  - 关态以 `cached.exports === undefined` 区分, 有两个缺陷 (Q81 修复项 2).
  - 缺陷一: JSON 值含 `exports` 键时, 第二次 require 返回该键的值.
  - 缺陷二: 模块 `module.exports = undefined` 时, 第二次 require 返回 Module 对象本身.
- 每个运行时一份, 随 ScriptRuntime 释放, 不新增进程级静态表. 不做跨脚本的编译缓存 (关态的 `new Function` 同样每次编译).

Module 对象 (`new Module(id, parent, core)`, 脚本仍可自行构造):

- 字段与关态相同: `_exports`, `id`, `core`, `parent`, `children`, `filename`, `loaded`.
- `exports` 是不可枚举, 不可配置的访问器, 按 `this._exports` 读写.
- `require` 是绑定到该模块的函数.
- 构造时加入 `parent.children`.
- 由 require 加载的模块, 其 `module.require` 带 `resolve(id)`, `paths()`, `cache`, `extensions`.
- 全局 `require` (即 `Module.require(id, parent)`) 带 `resolve(id, parent)`, `paths()`, `cache`, `extensions`, 以及访问器 `main` (只读) 与 `root` (可写).
- 函数的 name / length 与关态相同.

隔离 (函数包装):

- 编译: 模块源码编译为 `function (exports, require, module, __filename, __dirname) {` + 源码 + `\n}`.
  - 函数头与源码第一行同行, 行号与文件一致.
  - 源名为模块的规范路径.
  - 在脚本的顶层作用域中编译 (父作用域为全局), 解释模式与关态相同.
- 调用: 以 `this = module.exports` 调用.
- 顶层的 var / let / const / function / class 都是这个函数的局部变量: 不写入全局, 也不与全局或其他模块的同名绑定冲突 (#564). 非严格模式下未声明的赋值仍写入全局, 同 Node 与关态.
- `exports` 是形参: 重新赋值不影响 `module.exports`; `module.exports = x` 替换导出.
- 严格模式由模块自身的 `'use strict'` 决定.
- 与关态 `new Function` 的区别只在源名:
  - 关态错误的文件名为 `file:///android_asset/modules/jvm-npm.js#109(Function)`, 新实现为模块路径.
  - 行号两者相同 (JVM 实测, 源码第 3 行的错误两者都报第 3 行).
- 首行 `#!` 改为注释, 同关态.
- 读取:
  - 加密脚本 (以 `EncryptedScriptFileHeader` 开头) 解密后执行, 明文字节用后清零, 同关态.
  - 文件按 UTF-8 一次读入.
  - 关态先 `readBytes` 判断加密, 再用 Scanner 重读; 空文件时 Scanner 抛 NoSuchElementException, 被包装成 IO_ERROR (Q81 修复项 3).
- continuation:
  - 关态 require 链中有 `bind` 产生的 BoundFunction, Rhino 解释器不内联它, 链上因此有 Java 帧, 模块顶层代码中捕获 continuation 本来就会失败.
  - 新实现由 Kotlin 调用模块函数, 结果相同.
  - 模块导出的函数之后被主脚本调用时不受影响.
  - JVM 用例会固定两态一致.
- 循环依赖: a 引用 b, b 再引用 a 时, b 拿到 a 当时的 `exports`. 若 a 之后替换 `module.exports`, b 仍持有旧对象. 同 Node 与关态.

## (c) 与 Rhino Require / ModuleScriptProvider 的关系

Rhino commonjs Require 与函数包装的语义差别:

| 方面 | Rhino Require (ModuleScope 中执行脚本) | jvm-npm / 新实现 (函数包装) |
|---|---|---|
| 顶层声明 | 定义在模块作用域对象上, 其原型为全局. 2026-07-13 之前的 jar 上, 与全局同名的 const / let 报 redeclaration (#564); 与只读全局同名的 var 被静默忽略 | 函数局部变量 |
| `__filename` / `__dirname` | 无 | 有 |
| 缓存 | 按模块 id / URI, 不公开 | 按规范路径, 公开为 `require.cache` |
| module 字段 | sandboxed 模式下只有 id / exports | 另有 parent / children / loaded / filename / require |
| 解析 | 只在 provider 链中按 id 查找 (资产, URL, 绝对路径) | (a) 的全部规则 |
| 找不到 | `Module "x" not found.`; 在模块外用相对 id 时报 `Can't resolve relative module ID "./x" when require() is used outside of a module` (issue #340 / #360 中用户看到的就是这两条) | Q83 |

结论 (Q80 推荐项): 自实现解析, 缓存与函数包装, 与引擎已有的 Rhino Require 组合, 不继承它.

- 不继承的原因:
  - Require 的 getModule / executeModuleScript 是 private, 执行方式固定为 ModuleScope, 缓存不公开.
  - 继承只能复用 "按 id 取模块" 这一层, 而这一层正是与现有行为不同的部分.
  - 要得到 `__dirname`, `require.cache` 与 module 字段, 需要反射或复制约 400 行 Rhino 代码, 差分测试预计多 10 条以上已知偏差.
- 组合:
  - 引擎的 Rhino Require 实例作为命名模块与 URL 的加载器.
  - 资产模块的跨脚本编译缓存 (`ProcessWideAssetModuleScriptProvider`) 照常使用.
  - 与 Global 的 axios 等 getter 共用模块实例, `require('axios') === axios` 在两态都成立.
  - T11 的插件模块源也接在这一层 (见 (f)).
- 关于 roadmap 原文 "基于 Rhino commonjs Require": 本设计复用的是 Require 实例与 ModuleScriptProvider 链, 不复用它的模块执行方式.

代码位置 (bootstrap 包, P2.5 启动模块 Kotlin 化的先例), 预计主源码约 700-800 行:

- `runtime/api/modules/bootstrap/require/ScriptRequire.kt`: 全局 require, `Module` 构造函数及其成员, 加载与缓存, 约 400 行.
- `runtime/api/modules/bootstrap/require/ScriptModuleResolver.kt`: 文件解析 (纯 Kotlin, 可在 JVM 上用临时目录测试), 约 200 行.
- `runtime/api/modules/bootstrap/require/ScriptModuleSource.kt`: T11 解析器接口与资产源, 约 80 行.
- `ScriptRuntime` 与 `RhinoJavaScriptEngine` 接入, 约 40 行.

挂载 (T3 / T8):

- 开关关: 不变 (加载 jvm-npm.js).
- 开关开:
  - initEpilogue 不加载 jvm-npm.js.
  - 全局 `Module` 与 `require` 在原位置以 `LazyDataSlot` 定义, 特性同现在 (PERMANENT), 首次读取值时创建本运行时的 ScriptRequire.
  - `Object.keys(global)` 的顺序不变.
- `ScriptRuntime.js_Module` 在开关开时返回 Kotlin 的 Module, `NodeJavaScriptEngine` 的 runMain 照常可用.
- 原生 Require 的引用:
  - `rhinoRequireFunction` 现在是首次使用时从全局读取 `require`, 只因 epilogue 先加载 jvm-npm.js 才读到原生的.
  - 开关开时在改写全局 require 之前显式取得. 否则 Global 的 axios getter 首次使用时会读到 Kotlin require, 形成递归.
- `require('jvm-npm')`:
  - 开关开时返回 Kotlin 的 Module, 不执行资产中的 jvm-npm.js. 该文件第 379 行会改写全局 require.
  - 关态返回同一 Rhino 缓存中的 jvm-npm Module, 两态的含义相同: "当前在用的 Module".
- 启动收益: 关态每个脚本都执行 jvm-npm.js 的模块体 (闭包, RequireCtor, 读取工作目录); 开关开时到首次 require 之前为 0. P3.6 的 epilogue 分段计时可量出差值, P3.7 的计时也记一次.
- P5.2: 删除 jvm-npm.js 与开关分支.

## (d) 两态与差分测试

- 开 = Kotlin, 关 = 现 jvm-npm.js (不再修改).
- 表面一致:
  - 全局 `Module` / `require` 的自有属性名, 特性, 函数 name / length.
  - `main` / `root` 的访问器形态.
  - Module 实例字段与 `exports` 访问器.
  - `module.require` 的成员.
- 行为一致, 只有 Q81 修复项与 Q83 的错误形态不同; 每条在附录 E "P3.7" 登记, 差分测试中逐条白名单.

JVM 差分 (主):

- 现有 `JvmNpmTest` (P2.5) 扩展为 `ScriptRequireDifferentialTest`.
- 同一组脚本在两侧运行:
  - 关侧: 资产 jvm-npm.js, 加假的 `runtime.files` 与记录调用的假原生 require.
  - 开侧: Kotlin, 接同一个假原生 require.
- 比较项:
  - 导出值, `require.cache` 的键.
  - module 字段 (id / filename / loaded / parent.id / children 的 id).
  - `__filename` / `__dirname`.
  - resolve / paths 的结果.
  - 原生 require 收到的 id 序列.
  - 错误的 name / code.
- 白名单以外必须相同.
- 样例:
  - 自建的临时目录: 相对与绝对路径, 三种扩展名, 带 / 不带 main 的 package.json, index.js, 多层 node_modules, `NODE_PATH`, 循环依赖, JSON, 加密文件, shebang, #564 三文件, 错误路径.
  - `示例代码/控件/自定义控件-使用配置勾选框.js` 的被引用模块: 以桩 ui 加载, 比较导出.

真机 smoke (XQ-AT72, 一次):

- 新工具 `tools/augment/device_require_check.py` 与 `tools/augment/bench/require_smoke/`.
- 样例内容:
  - 主脚本, lib 下的相对模块, JSON, 带 package.json 的目录, index.js.
  - 子目录中向上找到的 node_modules 包, 循环依赖.
  - #564 的三文件 (`const log`).
  - `path` / `node:events` 内置名, `require('axios') === axios`.
  - 找不到的模块, 模块错误的源名.
- `--new-modules on|off` 各运行一次, 输出逐行比较; 已登记差异的行带标记, 按白名单比较.
- 结束时装回原包 debug 5835 (md5 d4994f77), 开关关.

## (e) issue #564

- 报告 (2026-07-22, OPEN, 6.7.0):
  - lib/a.js 与 lib/b.js 都写 `const log = require("./log.js")`.
  - main.js 依次 require 两者, 报 `TypeError: redeclaration of var log. (file:///android_asset/modules/jvm-npm.js#67)`.
  - 报告者在 Rhino shell 中复现不出.
- 根因 (JVM 实测, 用 v6.7.0 的 Rhino jar):
  - 6.7.0 的 `Module._load` 为 `return NativeRequire.require(file)`, 文件模块由 Rhino Require 在 ModuleScope 中执行, ModuleScope 的原型为全局.
  - 初始化脚本时 `ScriptRuntime.initScript` 发现顶层 const / let 名已能经原型链看到 (AutoJs6 的全局 `log`), 于是调用 `redefineProperty`, 抛出 redeclaration.
  - 触发条件是与全局对象上的属性同名, 而不是兄弟模块之间同名: 非全局名 `cfg` 在两个兄弟模块中不冲突. shell 的全局没有 `log`, 所以复现不出.
  - 报告说的 "第二个文件" 是 main.js 之后加载的第一个模块 lib/a.js.
- 现状 (master, 两态都已修复):
  1. `1b3a915ac` (2026-06-14, `feat(node): add CommonJS compatibility modules`, v6.8.0-alpha5 起) 恢复了 `new Function` 包装, 文件模块不再经 ModuleScope.
  2. 2026-07-13 起的 Rhino jar (`f78c28198`, 之后 `0e690b394`) 在 ModuleScope 路径上也不再抛出. 同一探针在 v6.7.0, f78c28198, 0e690b394 三个 jar 上对照过.
- P3.7 的做法:
  - 开态以函数包装执行文件模块, 隔离不依赖 jar 的行为.
  - 仍走 ModuleScope 的路径 (资产模块, URL 模块, TypeScript 项目) 依赖第 2 项. 加一条 JVM 回归用例: 原生 Require 加载的模块顶层 `const log` 不报错, 防止以后升级 jar 时回退.
  - issue 的复现脚本在两态各一条 JVM 用例, 真机 smoke 各一次.
- changelog: 6.8.0 的 zh-Hans 日志没有 #564 条目, 而修复 (上面两项) 在 6.8.0 中对两态都生效, 因此是否补一条由维护者决定 (单独出题, 不编号).
  - 建议条目 (fix): `模块文件的顶层 const / let 与全局对象上的名称 (如 log) 同名时, require 报 "redeclaration of var" 的问题` + issue #564 引用.
  - `.changelog/` 有其他会话的在途改动, 本会话不写.

## (f) 与 P4.3 的分工

P3.7 定义解析器接口与资产实现, P4.3 加插件实现:

```kotlin
/**
 * Named modules (T11): Node core module names and library names, consulted before the local lookup (core names) or
 * after it misses (everything else).
 */
interface ScriptModuleSource {
    /** Whether [name] is a Node core module name this source provides; reads cached indexes only, no IO. */
    fun isCore(name: String): Boolean

    /**
     * Exports of the named module [id], or [ABSENT] when this source does not know [id]. Throws an Error with install
     * guidance when it knows [id] but cannot provide it now (plugin not installed or disabled).
     */
    fun require(id: String): Any?

    companion object {
        val ABSENT = Any()
    }
}
```

- 组合: 每个运行时一个 `ScriptModuleSources(listOf(资产源, /* P4.3: 插件源 */))`.
  - `isCore` 取任一源为真.
  - `require` 取第一个不为 ABSENT 的结果.
  - 全部 ABSENT 时由 require 抛 "找不到模块" 错误.
- 调用点: ScriptRequire 的第 2 步 (核心名) 与第 5 步 (本地未命中). P4.3 再加 Global 的 getter.
- P3.7 的资产源 (`AssetModuleSource`):
  - 核心名为上述 8 个.
  - `require(id)`: 引擎的 ModuleScriptProvider 能提供 id 时 (资产, 资产子路径, 存在的绝对路径), 经原生 Require 加载 (同一缓存); 否则返回 ABSENT. 先查 provider 再加载, 找不到时不靠捕获 Rhino 的异常判断.
  - `jvm-npm` 特例见 (c).
- P4.3 的插件源 (`PluginModuleSource`):
  - 索引: 插件中心已缓存的清单 `scriptModules` (不唤醒插件进程).
  - 文件: 经 ContentProvider 读取一次, 按插件版本缓存.
  - 执行方式建议同资产: 作为 `ModuleSourceProvider` 接进引擎 Require 的 provider 链, 模块实例与 Global getter 共用.
  - 排在资产源之后 (T11: 本地未命中时查询).
  - 未安装或已禁用: 抛带安装指引的 Error, code 沿用 Q83.
  - P4.3 同时把 Global 的 axios / cheerio / dayjs / i18n / pangu getter 从 `rhinoRequire(name)` 改为经 `ScriptModuleSources`. P3.7 不改这些 getter (两态共用, 行为不变).
- P5.2: 删除资产中的库与垫片后, 资产源只剩启动必载的内部名.
- 关态: jvm-npm.js 的原生回退走引擎 Require. 若 P4.3 把插件 provider 接进该链, 关态也能加载插件模块 (没有安装指引), 由 P4.3 决定.

## (g) 测试计划

JVM (主):

- `ScriptModuleResolverTest` (临时目录, 纯 Kotlin):
  - (a) 的每条规则: 相对 / 绝对, 扩展名顺序 `.js` > `.mjs` > `.json`, 大写扩展名.
  - 目录: main 指向文件 / 目录, 无 main 时 index.js, package.json 损坏.
  - node_modules 逐层向上, 裸名先查 base, `paths()` 与 `NODE_PATH`.
  - 目录名含 `,` / `|` (Q81 修复项 1).
  - 剪枝前后结果相同: 同一组 id 对比 "不剪枝的参照实现".
- `ScriptRequireTest` (Rhino, 开侧):
  - 缓存键: `./a` 与 `../lib/a` 为同一模块, 符号链接.
  - 循环依赖, 抛出后删除缓存再重试, `delete require.cache[k]` 重新加载, JSON 缓存 (含 exports 键).
  - module 字段, 隔离: 顶层声明不进全局, `this === module.exports`, 重新赋值 exports, 严格模式, 非严格模式未声明赋值.
  - shebang, 空文件, 加密文件.
  - 错误: 非字符串与空串为 TypeError; 找不到为 Q83 的形态; PARSE_ERROR / IO_ERROR 带 code 与 cause; 模块错误的源名与行号.
  - runMain / `require.main` / `process.mainModule`, root 的读写, 带 parent 的 resolve.
  - 内置名与 http 交给原生, 本地未命中再交给命名源, `require('jvm-npm')`.
  - 模块顶层捕获 continuation 时两态同样失败.
- #564: issue 的三文件, 与全局同名的 `const log` / `let toast` 在两态都通过; 原生 Require 路径上的 `const log` 回归用例 (见 (e)).
- 挂载:
  - 开态 `Module` / `require` 是 LazyDataSlot, 特性 PERMANENT, `Object.keys(global)` 顺序不变.
  - 开态不加载 jvm-npm.js (假原生 require 未收到 `jvm-npm`).
  - 先 require 再触发 Global getter 时 getter 仍拿到原生模块 (防递归).
- 差分: `ScriptRequireDifferentialTest`, 见 (d).
- 门禁: `StaticReferenceGuardTest` 保持绿 (预计没有新的进程级静态字段; 若有, 用弱引用 / onExit 清理, 或白名单加行写理由).
- 隔离克隆 `E:/.cache/aj6p37`: 全量 `:app:testAppDebugUnitTest` 0 失败, `:app:assembleAppDebug` 通过 (Gradle 按 locks.md 排队).

真机 (XQ-AT72, 按 locks.md 登记):

- (d) 的 smoke, 两态各一次.
- R2 计时 (口径见 Q84):
  - 参数错误 `require(1)` / `require('')`: 对照同一次测量的 `undefined.x`, 不超过 1.2 倍.
  - 找不到模块 `require('./nope')`: 主脚本在 6 层目录下, 口径见 Q84.
  - 成功路径不劣于关态: 缓存命中, 清缓存后首次加载小模块, `require('path')`, `require('axios')`.
  - epilogue: 开关开 / 关.
- 工具: `device_bench.py` 等现由 augment-p3-6 在改 (locks.md 18:55). 到时仍被占用的话, P3.7 的计时用例放新文件 `bench/require_calls.js`, 由新工具调用, 不改它们的文件.

## 待决 (附录 C Q80-Q84)

| 编号 | 问题 | 推荐项 (是否先行) | 其他选项 | 改选的返工 |
|---|---|---|---|---|
| Q80 | 实现方式 | (1) 自实现解析 / 缓存 / 函数包装, 组合引擎现有 Rhino Require 加载命名模块与 URL. 主源码约 700-800 行, 测试约 1000 行. 未先行 (设计待确认) | (2) 继承 Rhino Require: ModuleScope 语义, 需反射或复制约 400 行, 预计多 10 条以上已知偏差; (3) 不 Kotlin 化, 只在 jvm-npm.js 中修缺陷 (约 50 行), P3.7 改为只做 #564 回归 | 实现前改选无返工 |
| Q81 | 解析与加载的兼容范围 | (1) 照搬 jvm-npm 的规则与顺序, 本阶段在开态修 9 个明确缺陷 (下表), 关态不改 | (2) 改为 Node 语义 (相对 id 不查 node_modules, 裸名不查 base, 支持 package.json exports): 文档 api/modules.md 要改, 现有脚本可能找不到模块; (3) 完全照搬, 缺陷留到 P5 | (3) 约少 60 行与 15 条用例; (2) 约多 150 行并改文档 |
| Q82 | 缓存键 | (1) 规范路径 (现状, 同 Node 的 realpath; `require.cache[require.resolve(x)]` 可用) | (2) 绝对路径但不解析符号链接: 同一文件经链接加载两次; (3) 请求写法 (`./a`): 同一文件不同写法加载多次, `require.cache` 的用法失效 | 小 (一处函数) |
| Q83 | 找不到模块时的错误 | (1) `Error: Cannot find module './x' (from <base>)`, `code = 'MODULE_NOT_FOUND'` (文档所写的 code; 关态实际是 Rhino 原生消息, 无 code) | (2) 照搬关态: `Module "x" not found.` / `Can't resolve relative module ID ...` (issue #340 / #360 中用户看不懂的两条); (3) Rhino 消息加 code | 小 (一处消息与用例) |
| Q84 | R2 中 "找不到模块" 的对照口径 | (1) 对照同一次测量中关态的同一调用, 开不超过关的 1.2 倍 (预计快于关); 参数错误照 R2 对照 `undefined.x`; Rhino 原生同名失败的耗时与探测次数记入附录 A 作参考 | (2) 严格对照 Rhino 原生 require 的同名失败, 不超过 1.2 倍: 原生只查资产, 不探测文件, 达标需要跨调用缓存目录是否存在 (运行中新建的 node_modules 要有失效机制), 约多 100-150 行, 有 "运行中新建的文件找不到" 的风险 | (2) 约 100-150 行加用例 |

Q81 (1) 在开态修复的缺陷 (关态 jvm-npm.js 不改, 附录 E "P3.7" 逐条登记):

1. `findRoot` 按 `/` `\` `,` `|` 拆分 parent.id. 例如 `/sdcard/a,b/main.js` 的相对 require 在 `/sdcard/a/b` 下查找. 改为只按路径取父目录.
2. 缓存命中时的取值缺陷, 见 (b) "缓存".
3. 空文件模块报 IO_ERROR, 改为空模块 (`exports` 为 `{}`).
4. 大写扩展名 (`./A.JS`) 能解析, 但 require 返回 undefined. 改为扩展名不分大小写.
5. 每次本地解析失败都向 System.out / System.err 打印 "Cannot resolve ... defaulting to native" (关态 `debug: true`, 脚本无法关闭). 改为不打印.
6. 模块错误的源名为 `jvm-npm.js#109(Function)`, 改为模块路径 (行号不变).
7. `require` 的参数不是非空字符串时, 关态的错误含义不明, 改为 TypeError (D10).
8. `Module.runMain` 找不到文件时报 IO_ERROR `Cannot read file [false]`, 改为 Q83 的错误.
9. IO_ERROR / PARSE_ERROR 是原型链借用 Error 的普通对象 (没有 stack, constructor 为内部的 ModuleError). 改为真正的 Error, 保留 message / code / cause.

另问 (不编号, 汇报事项): issue #564 是否在 6.8.0 changelog 补 fix 条目, 见 (e).

## 实施顺序 (确认后)

1. 解析器与命名模块源, 以及 `ScriptModuleResolverTest`.
2. ScriptRequire 与挂载, 以及 `ScriptRequireTest` 和 #564 用例.
3. 差分测试与真机工具.
4. 克隆全量测试与 assemble, 真机 smoke 与 R2.
5. roadmap 记录: P3.7 勾选, 附录 A 计时, 附录 C Q80-Q84 结果, 附录 E "P3.7" 的差异与 #564.
