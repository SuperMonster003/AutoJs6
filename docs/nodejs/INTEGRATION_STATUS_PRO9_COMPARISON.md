# Auto.js Pro v9 对照下的 AutoJs6 Node.js 集成现状

本文记录当前仓库在集成 Node.js 能力方面的实际状态，并对照 Auto.js Pro v9 文档中的 Node.js 方向，说明已经具备的能力、尚未完成的能力，以及 Rhino 时代内置 JavaScript API 在 Node.js 引擎中的迁移缺口。

盘点时间：2026-06-22。

当前版本基线来自根目录 `version.properties`：

- `VERSION_NAME=6.7.1 Alpha4`
- `VERSION_BUILD=3923`
- `MIN_SDK_VERSION=24`
- `TARGET_SDK_VERSION=36`
- `COMPILE_SDK_VERSION=36`

## 参照范围

外部参照为 Auto.js Pro v9 官方文档：

- Pro 9 默认仍以 Rhino 执行旧脚本，使用 `"nodejs";` 文件头、`.node.js` 或 `.mjs` 进入 Node.js。
- Pro 9 的 Node.js 能力以 `require()` 导入模块，不再默认注入 Pro 8/Rhino 风格的全局模块。
- Pro 9 文档使用 Node.js 16.x 作为内置模块参照，并强调 npm 生态、Node 内置模块、Pro 9 内置模块、Android/Java 交互、事件循环线程模型、UI 线程入口。
- Pro 9 提供 `$autojs.java`，并提供 `require("rhino").install()` 来安装类似 Rhino 的 `java.*`、`android.*` 访问方式；但文档中也明确 `importClass`、`importPackage` 和 `JavaAdapter` 暂不支持。

AutoJs6 当前不是照搬 Pro 9。仓库已有的是一个更收敛的 `AutoJs6 Safe Node Profile`，目标是 Android 上的受控 Node.js 能力，而不是完整桌面 Node.js 或 Rhino 全局 API 的无差别复刻。

## 当前实现基线

### 运行时和版本

当前代码中的 Node Profile 为：

- Profile：`AutoJs6 Safe Node Profile`
- Engine version：`autojs6-node-profile-v1.1`
- Node header/runtime baseline：`v24.5.0`
- 默认构建后端：`autojs.nodejs.backend=embedded`
- 默认发布形态：`nodeFull`
- 默认 runtime slot：`node24_5`

相关代码：

- `app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt`
- `app/build.gradle.kts`
- `app/src/main/cpp/CMakeLists.txt`

当前本地 native 资产中：

- `arm64-v8a` 有 `libnode.so`
- `armeabi-v7a` 有 `libnode.so`
- `x86_64` 有 `libnode.so`
- `x86` 没有 `libnode.so`，并且构建策略明确要求 x86 继续 fail-fast

这和当前文档中的 ABI 策略一致：`arm64-v8a`、`armeabi-v7a`、`x86_64` 是支持范围，`x86` 不是当前必需目标。

### 脚本入口

当前已经支持下列 Node 路由入口：

- 文件头 `"nodejs";`
- 文件头 `"node";`
- `.node.js`
- `.cjs`
- `.mjs`
- `.ts`
- `.mts`
- `.cts`
- `.tsx` 进入 Node 路由后再由能力策略拒绝或处理
- `project.json` 中 `type: "node"` 或 `projectType: "node"`
- 加密脚本头中的 Node 执行标记

相关代码：

- `JavaScriptSource.EXECUTION_MODES` 中注册了 `nodejs` 和 `node`
- `JavaScriptSource.isNodeJsFileName()` 中注册了 Node 文件后缀
- `JavaScriptFileSource.getEngineName()` 会根据执行模式、后缀和最近的 `project.json` 选择 Node 或 Rhino
- `AbstractAutoJs` 将 `JavaScriptSource.ENGINE_NODEJS` 注册到 `NodeJavaScriptEngineFactory`

和 Pro 9 一致的是：普通 `.js` 仍默认保留 Rhino 兼容路径。和 Pro 9 不同的是：AutoJs6 还把轻量 TypeScript 后缀纳入了 Node 路由策略。

### 后端选择

当前 `NodeRuntimeBackend` 支持：

- `rhino`
- `native`
- `embedded`
- `auto`

默认 `embedded`。`rhinoOnly` 发布形态下会强制回落到 Rhino，并通过 `ERR_AUTOJS6_NODE_DISABLED` 类诊断清楚失败，不会静默把 Node 脚本当作普通 Rhino 脚本。

### Android 进程和服务

当前 manifest 已有独立 Node 服务进程：

- `NativeNodeEmbeddedProbeService`，进程 `:nodejs_embedded`
- `NativeNodeEmbeddedScriptService`，进程 `:nodejs_embedded`
- `NativeNodeEmbeddedScriptServiceSlot1`，进程 `:nodejs_embedded_1`
- `NativeNodeEmbeddedScriptServiceSlot2`，进程 `:nodejs_embedded_2`
- `NativeNodeSandboxedScriptService`，`isolatedProcess=true`
- `NodeBridgeEngineDispatchService`
- `NodeBridgeUiDispatchService`
- `NodeBridgeUiActivity`

这说明当前已经不是单纯的 Rhino 兼容包装，而是有独立 Node 进程、桥接调度、UI 调度和预留 slot 的 Android 运行时结构。

### CommonJS、模块和包解析

当前已经具备：

- `require()`
- `module.require()`
- `module.exports` 和 `exports`
- JSON modules
- 本地 `.js`、`.cjs`、`.json`
- `node_modules` 遍历
- scoped packages
- package `main`
- package `exports`
- package `imports`
- package self-reference
- CommonJS 条件：`require`、`node`、`default`
- module source budget
- 内置模块 shadowing 防护
- resolver trace 和 resolver visualizer
- `require("autojs6:profile")` 只读诊断

需要注意：当前 embedded file engine 对 CommonJS 依赖有静态预加载机制。动态 `require()` 不能保证被预加载，运行失败时会提示使用静态字符串 `require(...)` 或显式 `moduleSources`。这比桌面 Node.js 更受限。

### ESM 和动态 import

当前状态是 `partial`：

- `.mjs` 路由 Node
- 支持局部静态 import
- 支持 selected builtin import
- 支持 top-level await
- 支持 CommonJS 默认导入
- 支持部分 package conditions
- 支持本地动态 `import()`

仍不等同于桌面 Node.js：

- 不支持自定义 loader hooks
- 不支持网络 URL import
- 不支持绝对路径或越权路径 import
- 不支持 native addon
- 对同步 `require()` 加载含 async graph 的 ESM 有拒绝路径

### TypeScript

当前仓库有轻量 TypeScript 路由和验证门禁，目标是 erasable syntax 和构建/宿主管理路径，不是完整 TypeScript 编译器运行时。

已经具备：

- `.ts`
- `.mts`
- `.cts`
- TypeScript 示例和验证任务

仍需注意：

- `.tsx` 不作为嵌入式运行时能力直接支持
- 不提供完整 `tsconfig` path mapping
- 不在嵌入式 Node 中运行任意 compiler plugin

### Node 内置模块

当前不是完整桌面 Node.js，而是受限内置模块集合。

已作为稳定或部分能力出现的模块和全局包括：

- `path`
- `util`
- `events`
- `assert`
- `querystring`
- `string_decoder`
- `timers`
- `buffer`
- `console`
- 安全的 `process` 子集
- scoped `fs`
- scoped `fs/promises`
- `stream` 部分能力
- `zlib` 部分能力
- `crypto` 部分能力
- `os`、`url`、`tty`、`readline`、`v8` 等受限 wrapper

默认禁用或策略性拒绝：

- `child_process`
- `cluster`
- `dgram`
- `inspector`
- `repl`
- `vm`
- `wasi`
- `worker_threads`
- `process.binding`
- `process._linkedBinding`
- `process.dlopen`
- native `.node` addon

网络相关模块的状态较特殊：

- `fetch`、`axios`、`websocket` 是受控 bridge-backed 能力。
- `http`、`https`、`undici` 有受控 facade 方向。
- 原始 `net`、`tls`、`dns`、`dgram` 以及服务器、socket、upgrade、CONNECT 等能力仍保持拒绝或受限。
- live network 默认需要 `autojs.nodejs.network.experimental=true` 和 `network` capability。

### scoped filesystem

当前 Node `fs` 不是 Android 全盘文件能力，而是 scoped fs：

- root 为脚本工作目录或项目根目录
- 拒绝绝对路径越权
- 拒绝 `..` escape
- 拒绝 NUL byte
- 拒绝 `/proc`、`/sys`、`/dev`
- 拒绝 symlink escape
- 拒绝 realpath escape
- FileHandle/fd/watcher 等都有执行期归属和清理策略

这和 Rhino `files` 模块的使用习惯差异很大。迁移时不能简单把 `files.read("/sdcard/...")` 换成 `fs.readFileSync("/sdcard/...")`。

### npm 和依赖

已具备：

- vendored pure JavaScript dependencies
- `node_modules` package graph
- host-side project wizard / validator
- host-side dependency bundler / sanitizer / compatibility report
- offline real npm corpus
- package `exports`、`imports`、scoped package 等兼容测试

尚未具备：

- Android 端运行时 `npm install`
- Android 端安装、更新、删除、校验 npm 包的事务型 package manager
- lifecycle scripts / postinstall
- native addon build
- unchecked `.bin` shims

这和 Auto.js Pro v9 文档中的“在终端里 npm i --no-bin-links 模块名”有明显差异。AutoJs6 当前更接近“宿主侧或打包前整理依赖，运行时执行已净化的纯 JS 依赖”。

## AutoJs bridge 能力

### 协议和权限模型

当前已经有：

- `NodeBridgeRequest`
- `NodeBridgeResponse`
- `NodeBridgeError`
- `NodeBridgeModules`
- `NodeBridgeCapabilityProvider`
- `NodeBridgeProviderRegistry`
- `require("autojs6:bridge")`
- `node.permissions`
- `autojs6.node.permissions`
- `node.bridgeLimits`
- `autojs6.node.bridgeLimits`
- pending call timeout
- service destroy rejection
- AbortSignal cancellation
- resource registry
- provider health/diagnostics

bridge capability 的默认映射已经在 Node bootstrap 中实现，例如：

- `app.launchPackage` 需要 `app.launch`
- `app.openAppSetting` 需要 `app.settings`
- `app.startActivity` 需要 `app.activity`
- `media_projection` 映射到 `screen_capture`
- `image.captureScreen` 需要 `image` 和 `screen_capture`
- `ocr` 需要 `ocr` 和 `image`
- `fetch`、`websocket` 需要 `network`
- `java` 映射到 `java_interop`

### 当前 Node bridge 模块

`NodeBridgeModules` 当前注册了：

| 模块 | 方法范围 | 当前判断 |
| --- | --- | --- |
| `toast` | `showToast`, `toast` | 已有稳定模块形状，适合 Rhino `toast()` 的第一批迁移 |
| `app` | `launchPackage`, `launchApp`, `openAppSetting`, `startActivity` | 已有基础应用能力，范围小于 Rhino `app` |
| `engines` | `myEngine`, `all`, `stopAll`, `stopSelf`, `execScript`, `execScriptFile` | 受控脚本调度，不暴露原始 `ScriptEngine`/`ScriptExecution` |
| `dialogs` | `alert`, `confirm`, `input`, `select` | JS 协议已有，Activity/provider 生命周期仍需关注 |
| `accessibility` | `isEnabled`, `ensureEnabled`, `click`, `back`, `home`, `recentApps`, selector 查询和动作 | 覆盖一部分自动化常用能力，但不是 Rhino selector/raw node 完整等价 |
| `media_projection` | `requestScreenCapture`, `nextImage`, `stop` | 协议存在，真实授权和 packaged 行为仍是重点 |
| `image` / `images` | 截图、读写、裁剪、缩放、灰度、阈值、找图、模板匹配、找色、多点找色、回收 | handle-based，不暴露 Bitmap/Mat；部分 provider 仍需提升 |
| `ocr` | `recognize`, `recognizeText`, `detectTextBounds` | 部分能力，依赖 image handle 和 OCR provider/model |
| `storage` / `storages` | `get`, `put`, `remove`, `clear`, `keys`, `contains` | JSON-only、按项目/包隔离，不等于 Rhino 所有 storage 语义 |
| `database` / `sqlite` | `open`, `exec`, `run`, `get`, `all`, `transaction`, `close` | 受控 SQLite 能力，不是 Node `node:sqlite` |
| `notifications` | `notify`, `cancel`, `cancelAll` | post/cancel 子集，动作/监听/复杂通知未开放 |
| `sensors` | `getAvailableSensors`, `once`, `subscribe`, `unsubscribe`, `drainEvents` | 当前仅 accelerometer、gyroscope、light |
| `ui` | `showLayout`, `update`, `batchUpdate`, `close`, `drainEvents` | JSON descriptor 和 opaque handle，不是 Rhino UI DSL/raw View |
| `clipboard` | `getText`, `setText`, `hasText` | 对应 Pro 9 `clip_manager`/Rhino `setClip/getClip` 的受控形态 |
| `device` | `isScreenOn`, `wakeUp`, `vibrate`，以及 `sdkInt/width/height/density` | 设备信息和基础动作子集，范围小于 Rhino `device` |
| `shell` | `exec` | 非交互 shell，替代部分 `child_process.exec()` 和 Rhino shell 用法 |
| `fetch` | `request` | 受控网络，默认 experimental |
| `websocket` | `connect`, `send`, `close`, `drainEvents` | 受控 WebSocket，默认 experimental |
| `work_manager` | `scheduleOnce`, `schedulePeriodic`, `cancel`, `list` | 受控 WorkManager metadata 和 scheduled runner，非完整任务系统 |
| `java` | `type`, `callStatic`, `new` | 实验性 allowlist，不是 Rhino Java bridge |

另外，Node bootstrap 中还保留了：

- `axios`
- `undici`
- `plugins`
- `autojs6:profile`
- `autojs6:bridge`
- `autojs6:lifecycle`
- experimental `worker_threads`
- limited `node:test`

这些不全是 Android bridge provider，有些是 JS facade 或诊断模块。

## 与 Auto.js Pro v9 的主要差异

| 方面 | Auto.js Pro v9 文档 | AutoJs6 当前状态 |
| --- | --- | --- |
| 默认引擎 | 默认 Rhino，显式启用 Node | 一致，普通 `.js` 默认 Rhino |
| Node 版本 | 文档参照 Node 16.x | 当前 baseline 为 Node 24.5.0 |
| 模块导入 | Pro 9 模块需 `require()` | 一致，且 AutoJs6 保留 bridge capability manifest |
| npm | 终端内 `npm i --no-bin-links` 安装 | Android 运行时 npm install 尚未完成；当前偏 host-side/vendor 模式 |
| Node builtins | 更接近 Node 内置模块使用说明 | AutoJs6 Safe Profile 更严格，很多 builtin 是 wrapper 或 denial |
| Java/Android | `$autojs.java`，`require("rhino").install()` | 默认无 `$autojs`，无 `require("rhino")`，只有 experimental `java` allowlist |
| Rhino 全局兼容 | Pro 9 第一代 API 保留在 Rhino，Node 需 require 新模块 | AutoJs6 也保留 Rhino，但 Node 不自动拥有 `augment` 全局 |
| 线程 | Node event loop，`worker_threads` 可作为方向，Pro 9 说明 worker 内不能访问 autojs API | AutoJs6 `worker_threads` 默认关闭且仍是条件决策项 |
| UI 线程 | Pro 9 有 `"nodejs ui"` / `"ui-thread"` 文档语义 | AutoJs6 当前更偏 `require("ui")` declarative bridge；Node 线程切换语义未等价 |
| 网络 | Pro 9 可用 Node `http/https` 示例 | AutoJs6 默认走受控 fetch/http facade，raw socket/server 方向保持拒绝 |
| 打包 | Pro 9 自有打包体系 | AutoJs6 已有 packaged smoke、runtime native libs、release flavor、ABI 策略，但 long-running packaged mode 仍未完成 |

## Rhino `augment` API 迁移现状

这是对脚本开发者最关键的部分。

当前 `org/autojs/autojs/runtime/api/augment` 是 Rhino 运行时内置 API 的主要实现。`ScriptRuntime.initPrologue()` 会把这些模块安装到 Rhino 的 `TopLevelScope`，实现依赖：

- `org.mozilla.javascript.Scriptable`
- `ScriptableObject`
- `BaseFunction`
- `RhinoUtils`
- Rhino 注解和 Rhino 类型转换

因此，虽然 `NativeNodeEmbeddedJavaScriptEngine` 继承了 `RhinoJavaScriptEngine` 以复用执行框架和 `ScriptRuntime` 绑定，但 Node 用户源码实际在 embedded Node/V8 中执行，并不会直接得到这些 Rhino `ScriptableObject` 全局对象。

结论：

- Rhino 的 `augment` API 在 Rhino 引擎中仍然可用。
- Node.js 引擎中目前只能使用已经独立实现的 Node bridge modules。
- `augment` 不是自动 Node module。
- 目前没有 `require("rhino")` shim。
- 目前没有 `rhino.install()`。
- 目前没有默认安装旧 AutoJs 全局变量到 Node global。

### `augment` 到 Node 的对应矩阵

| Rhino/augment 类别 | Rhino 典型用法 | Node 当前对应 | 状态 |
| --- | --- | --- | --- |
| `toast` | `toast("x")`, `toastLog()` | `require("toast").showToast()` | 有对应，但全局函数兼容层未实现 |
| `global` | `sleep`, `exit`, `setClip`, `getClip`, `currentPackage`, `waitForActivity`, `random`, `cX/cY` | timers、clipboard、accessibility/app/device 的分散模块 | 分散且不等价，需要 legacy facade |
| `app` | `app.launchPackage`, `app.startActivity`, intent/broadcast 等 | `require("app")` 的 launch/settings/activity 子集 | 部分 |
| `autojs` | `$autojs`, version/resource 等 | `autojs6:profile`、部分 `app` metadata | 部分，模块名和对象模型不同 |
| `automator` / `auto` / `selector` | `auto`, `click`, `text("OK").findOne()` | `require("accessibility")` selector descriptors 和动作 | 部分，selector DSL 兼容层未完成 |
| `root_automator` | root input event 自动化 | 无稳定 Node bridge | 待实现或明确不支持 |
| `files` | `files.read/write/createWithDirs` | scoped `fs` / `fs/promises` | 有替代，不兼容 Rhino 路径和 API 形状 |
| `images` | `requestScreenCapture`, `captureScreen`, `images.findImage` | `media_projection`, `image/images` handle API | 部分，需兼容包装和 provider 提升 |
| `ocr` | `ocr.recognize`, MLKit/Paddle/Rapid 等 | `require("ocr")` over image handle | 部分，provider/model 仍需完善 |
| `dialogs` | 同步/阻塞式 dialogs | Promise-based `require("dialogs")` | API 形状不同，Activity/provider 生命周期需完善 |
| `console` | `console.log`, 悬浮控制台等 | Node `console` 和 stdout/stderr 捕获 | 基础日志已具备，Rhino 控制台高级能力未等价 |
| `device` | 设备信息、亮屏、震动、更多系统信息 | `require("device")` 子集 | 部分 |
| `events` / `keys` | 按键、广播、事件监听 | Node `events` builtin 不是 AutoJs events；无完整 AutoJs events bridge | 待实现 |
| `threads` | `threads.start`, 共享变量式线程 | Promise flow；`worker_threads` 默认禁用 | 不兼容，需迁移策略 |
| `timers` | `setTimeout/setInterval` 及 AutoJs 扩展 | Node timers | 基础可用，AutoJs 扩展需核对 |
| `ui` | Rhino XML/DSL UI、raw View 交互 | `require("ui")` JSON descriptor/opaque handle | 部分，不是 Rhino UI 替代 |
| `floaty` | 悬浮窗/overlay | 无稳定 Node module，设计门禁 | 待实现 |
| `storages` | 本地存储 | `storage/storages` JSON-only | 部分 |
| `sqlite` | SQLite helpers | `database/sqlite` controlled handle | 部分 |
| `shell` / `shizuku` | shell/root/shizuku | `shell.exec`，Shizuku 未作为 v1.1 bridge expansion | 部分 |
| `http` | Rhino http helpers | controlled `fetch`/`axios`/http facade | API 不等价，默认 network experimental |
| `web` / `WebSocket` | WebSocket 等 | `websocket` controlled module | 部分 |
| `media` | 播放/录音等多媒体 | 无稳定 Node module | 待实现 |
| `sensors` | 传感器监听 | `sensors` limited subset | 部分 |
| `notice` | 通知 | `notifications` post/cancel subset | 部分 |
| `plugins` | AutoJs plugin 能力 | Node `plugins` 仅 JS-only local prototype | 部分，原生/签名/市场未实现 |
| `tasks` | AutoJs 任务体系 | `work_manager` controlled scheduler | 部分，用户任务数据库/boot/broadcast 未完成 |
| `colors` / `Color` | 颜色解析、转换、表 | 无专门 Node `colors` bridge；部分 image 找色 | 待实现 pure JS/bridge module |
| `crypto` | AutoJs crypto 工具 | Node `crypto` 部分能力 | 需 API 对齐 |
| `base64` | Base64 工具 | `Buffer` 可替代 | 需兼容 facade |
| `barcode` / `qrcode` | 条码/二维码 | 无稳定 Node bridge | 待实现 |
| `canvas` | Canvas 绘制 | 无稳定 Node bridge | 待实现 |
| `recorder` | 录制相关 | 无稳定 Node bridge | 待实现 |
| `zip` | 压缩/解压 | Node `zlib` 不等价，无 AutoJs zip bridge | 待实现 |
| `mime` | MIME 工具 | 无专门 Node module | 可先做 pure JS facade |
| `sysprops` | 系统属性 | 无稳定 Node module | 待实现，需权限审查 |
| `opencc` / `pinyin` / `pinyin4j` | 中文转换、拼音 | 无 Node bridge | 可先做 pure JS 或 JVM-backed bridge |
| `nanoid` | id 生成 | 可用 npm/pure JS 或 facade | 待明确 |
| `formatter` / `converter` / `s13n` / `jsox` / `util` | 工具、序列化、格式化、JS 扩展 | Node builtin 或无对应 | 需 API inventory 后分批迁移 |
| `continuation` | Rhino continuation/coroutine | Node Promise/async model | 不建议等价迁移 |
| Java bridge | `importClass`, `Packages`, `JavaAdapter`, `java.*`, `android.*` | experimental `java` allowlist | 不等价，默认拒绝 |

### 最重要的缺口

如果目标是“脚本开发者把现有 Rhino 脚本切到 Node 后，原有主要 API 仍能正常使用”，当前还没有完成。

现在的实现更接近：

```js
"nodejs";

const { showToast } = require("toast");
const app = require("app");
const accessibility = require("accessibility");
```

而不是：

```js
"nodejs";

toast("ok");
app.launchPackage("...");
text("OK").findOne().click();
```

所以需要单独建设一个 Rhino API compatibility layer。这个 layer 不应该简单暴露 Rhino `ScriptableObject` 到 V8，因为那会带来对象生命周期、线程、Java 对象泄漏、权限绕过和调试归属问题。更稳妥的方向是：

1. 以 `augment` 为源生成 API inventory。
2. 为每个 Rhino API 定义 Node 等价模块、legacy alias 或明确不支持的错误码。
3. Android/AutoJs 能力全部通过 Node bridge provider 暴露。
4. JavaScript 侧只接收 JSON snapshot 或 opaque handle。
5. 可选提供显式 legacy mode，例如项目配置 `node.compatibility: "rhino-globals"` 或文件头 `"nodejs compat"`，但不默认污染所有 Node 脚本。
6. 对常用全局函数提供薄 wrapper，例如 `toast()`、`sleep()`、`setClip()`、`getClip()`、`text()`、`desc()`、`files.*`、`images.*`。
7. 对高风险能力继续保持显式权限和稳定拒绝，例如 Java bridge、root、Shizuku、floaty、raw Android objects。

## 已经具备的能力清单

### 可认为已落地的基础能力

- Node 路由入口。
- 默认 embedded backend。
- Rhino 默认兼容路径保留。
- Node 24.5 native headers 和 `libnode.so`。
- 三个支持 ABI 的 native runtime 资产。
- 独立 Node embedded service 进程。
- manifest 中的 Node 服务和 UI dispatch 组件。
- CommonJS。
- scoped fs。
- package `main`、`exports`、`imports`。
- `node_modules` 静态图预加载。
- partial ESM。
- local dynamic import。
- 轻量 TypeScript 路由和验证。
- `autojs6:profile`。
- `autojs6:bridge`。
- bridge permission manifest。
- bridge limits。
- bridge resource registry。
- error code 兼容体系。
- TypeScript declaration package `docs/nodejs/types/autojs6-node`。
- examples、project wizard、host validator、compatibility profile、CI gate 文档。

### 已有或部分可用的 AutoJs bridge 能力

- `toast`
- `app`
- `clipboard`
- `device`
- `shell`
- `engines`
- `dialogs`
- `accessibility`
- `media_projection`
- `image` / `images`
- `ocr`
- `storage` / `storages`
- `database` / `sqlite`
- `notifications`
- `sensors`
- `ui`
- `fetch`
- `axios`
- `websocket`
- `work_manager`
- JS-only `plugins`
- experimental `java`

### 已经明确拒绝的高风险能力

- native `.node` addon
- unrestricted `child_process`
- unrestricted raw network
- raw socket server/listen/upgrade/CONNECT
- private Node bindings
- `process.dlopen`
- unrestricted `vm`
- release Inspector
- raw Java/Android object graph
- raw Android scheduler/broadcast/content provider/system manager access
- raw WASI

这些拒绝不是遗漏，而是 Safe Node Profile 的安全边界。

## 尚需完成的能力

### Phase 11 required internal blockers

当前 final blocker registry 中仍为 open 的 required internal 项：

- `crash_evidence_automation`
- `android_side_package_installation`
- `packaged_long_running`
- `second_execution_slot`
- `full_heavy_resource_stress`

含义：

- crash marker、next-launch collection、symbolization、chaos injection 还没有完整自动化闭环。
- Android 端 npm/package install/update/remove/list/verify/prune 还没完成。
- packaged APK long-running Node service mode 还没完成。
- 第二执行 slot 已有 explicit diagnostic route，但默认调度、packaged 行为、slot crash evidence 和 stress promotion 还没完成。
- 8 小时 quick soak 和 24 小时 final soak 还没完成。

### Conditional decisions

仍需产品和技术决策：

- `worker_threads`：实现受控 worker，或明确用 AutoJs6 process-worker 替代。
- `controlled_wasi`：只支持纯 WebAssembly，还是实现受控 `autojs:wasi`。
- `x86`：当前 optional，不是 Phase 11 必需 ABI。

### 与 Pro 9 能力差距

- Android 端 `npm install` 和全局 npm 安装能力未完成。
- `$autojs.java` 不存在。
- `require("rhino").install()` 不存在。
- `java.*`、`android.*`、`Packages`、`JavaAdapter` 不存在。
- `"nodejs ui"` / `"ui-thread"` 的 Pro 9 线程语义未等价实现。
- raw Node `http/https/net/tls/dns/dgram` 不作为默认能力。
- `worker_threads` 未作为默认可用能力。
- Pro 9 文档中列出的部分第二代模块在 AutoJs6 中名称、形状或状态不同。

### 与 Rhino `augment` 能力差距

高优先级待补：

- 全局 API 兼容层。
- `files` API facade。
- selector DSL facade：`text()`、`desc()`、`id()`、链式选择器、`findOne()`、`find()`、`click()` 等。
- `images` API facade：尽量兼容 `images.read/findImage/matchTemplate/findColor` 等常用脚本写法。
- `app` 扩展：广播、邮件、intent helpers、应用信息查询等。
- `device` 扩展：更完整设备信息、电源/亮度/剪贴板兼容细节。
- `events` / `keys` / input event observers。
- `threads` 迁移策略。
- `floaty` 和 overlay permission flow。
- Rhino UI DSL 到 Node UI bridge 的迁移层。
- `tasks` 到 `work_manager` 和用户任务数据库的迁移。
- `colors`、`base64`、`formatter`、`converter`、`mime`、`opencc`、`pinyin` 等 pure utility 模块。
- `zip`、`barcode`、`canvas`、`recorder` 等缺失模块。
- `shizuku` / root advanced shell 的权限和生命周期模型。
- Java/Rhino compatibility shim 的显式隔离实现。

## 建议路线

### 第一阶段：API inventory 和差距自动化

目标：先知道 Rhino API 到底有多少。

建议实现：

- 写一个 Gradle 或 Kotlin 工具扫描 `org/autojs/autojs/runtime/api/augment`。
- 提取每个 `Augmentable` 的：
  - module key
  - global assignment functions
  - self assignment functions
  - getters
  - aliases
  - `$` 前缀别名
  - Java class assignment
- 输出 `build/reports/nodejs/rhino-augment-api-inventory.json`。
- 和 `NodeBridgeModules`、`docs/nodejs/types/autojs6-node/*.d.ts` 做 diff。
- 在 CI 中加入“新增 Rhino API 未标注 Node 迁移状态则失败”的文档门禁。

### 第二阶段：低风险兼容 facade

优先做不需要 Android 高权限、不需要 raw Java 对象的模块：

- `toast`
- `sleep`
- `setClip/getClip`
- `files` 的 scoped facade
- `base64`
- `colors`
- `formatter`
- `converter`
- `mime`
- `nanoid`
- `pinyin/opencc`
- `storages`
- `sqlite/database`

这一阶段可以显著提升旧脚本迁移体验，风险也较小。

### 第三阶段：自动化核心 API

优先覆盖脚本作者最常用的自动化路径：

- selector DSL
- accessibility action
- screenshot permission
- image handle lifecycle
- image matching
- OCR
- app launch/settings/activity
- shell exec
- device wake/vibrate/basic info

这一阶段应提供 Rhino 风格 wrapper，但内部全部走 Node bridge 和 opaque handle。

### 第四阶段：UI、floaty、tasks、events

这些能力生命周期复杂，建议在已有 design gate 上继续推进：

- UI bridge live provider 和 Rhino UI DSL 迁移。
- floaty overlay permission、foreground disclosure、handle cleanup。
- tasks 到 WorkManager 和 AutoJs6 用户任务数据库的衔接。
- events/keys/input observer 的权限、速率限制和资源释放。

### 第五阶段：显式 Rhino shim

不要默认实现 `rhino.install()`。

建议实现一个显式、隔离、JSON-only 的 shim：

```js
const rhino = require("rhino");

const result = await rhino.run({
  source: "toast('legacy')",
  timeoutMs: 5000,
  args: []
});
```

边界：

- 不在 V8 中安装 Rhino globals。
- 不把 Java object、Rhino `Scriptable`、Android `View`、`Context`、`AccessibilityNodeInfo` 带回 Node。
- 不支持 `importClass/importPackage/JavaAdapter` 穿透到 Node。
- 只返回 JSON、stdout/stderr 摘要和稳定错误对象。
- packaged APK 需要显式 metadata opt-in。

如果将来确实需要 `installGlobals()`，建议只作为显式 legacy mode，并且第一版只安装安全 wrapper，不安装 Java bridge globals。

## 对脚本开发者的推荐迁移形态

短期推荐：

```js
"nodejs";

const { showToast } = require("toast");
const app = require("app");
const accessibility = require("accessibility");
const image = require("image");

await showToast("start");
await app.launchPackage("com.example");
const ok = accessibility.text("OK");
await accessibility.click(ok, { timeoutMs: 3000 });
```

中期希望提供：

```js
"nodejs compat";

toast("start");
app.launchPackage("com.example");
text("OK").findOne(3000).click();
```

但中期兼容层内部仍应是 Promise/bridge/handle 模型。对于原 Rhino 中同步阻塞的 Android API，需要定义清楚是：

- 保持 Promise API；
- 提供 blocking-looking compatibility wrapper；
- 还是要求脚本迁移为 `await`。

建议优先选择 Promise/`await`，兼容 wrapper 只用于最常见、最安全的旧写法。

## 验收标准

要认为 Node.js 集成真正满足“现有 Rhino 主要能力在 Node.js 引擎中正常使用”，至少需要：

- `augment` API inventory 自动生成。
- 每个 Rhino API 标注：
  - `supported`
  - `supported_async`
  - `renamed`
  - `partial`
  - `blocked_by_design`
  - `unsupported`
- Node bridge/types/docs 与 inventory diff 无遗漏。
- 常用 Rhino 示例有 Node 兼容版本。
- 迁移测试能在 Rhino 和 Node compat mode 跑同一组核心用例。
- packaged APK 中也覆盖核心 compat API。
- 无 raw Java/Android object 泄漏。
- bridge resource 在 stop/timeout/destroy 后归零。
- 缺能力时有稳定错误码和迁移提示。
- Pro 9 对照文档和 AutoJs6 自身文档同步更新。

## 总结

当前 AutoJs6 已经具备较完整的 Node.js runtime 骨架：入口路由、embedded Node 24.5、native assets、独立服务进程、CommonJS、部分 ESM、scoped fs、package resolver、桥接协议、能力权限、类型声明、测试和发布门禁都已经存在。

但它还不是 Auto.js Pro v9 的完整等价实现，也不是 Rhino API 的 drop-in replacement。当前最需要补齐的是面向脚本开发者的 Rhino `augment` 兼容层：把旧的全局 API 和常用模块映射为显式、安全、可测试的 Node bridge/facade。没有这一层，Node 引擎虽然能运行现代 JavaScript 和受控模块，但旧脚本迁移成本仍然较高。
