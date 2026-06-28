# Auto.js Pro v9 对照下的 AutoJs6 Node.js 集成现状

本文记录当前仓库在集成 Node.js 能力方面的实际状态，并对照 Auto.js Pro
v9 文档说明哪些能力已经具备、哪些能力尚需完成，以及 Rhino 时代内置
JavaScript API，尤其是 `org/autojs/autojs/runtime/api/augment` 中主要 API，
在 Node.js 引擎中的迁移状态。

盘点时间：2026-06-24。

当前版本基线来自根目录 `version.properties`：

- `VERSION_NAME=6.7.1 Alpha4`
- `VERSION_BUILD=3923`
- `MIN_SDK_VERSION=24`
- `TARGET_SDK_VERSION=36`
- `COMPILE_SDK_VERSION=36`

## 参照资料

外部参照：

- [Auto.js Pro v9 阅读须知](https://www.wuyunai.com/docs/v9/)
- [如何从第一代 API 迁移过来](https://www.wuyunai.com/docs/v9/migrate.html)
- [rhino - 犀牛 Java 交互](https://www.wuyunai.com/docs/v9/generated/modules/rhino.html)
- [globals - 全局函数与变量](https://www.wuyunai.com/docs/v9/generated/modules/globals.html)
- [image - 图片处理](https://www.wuyunai.com/docs/v9/generated/modules/image.html)
- [media_projection - 屏幕截图](https://www.wuyunai.com/docs/v9/generated/modules/media_projection.html)

内部参照：

- `docs/nodejs/QUICK_START.md`
- `docs/nodejs/COMPATIBILITY_PROFILE.md`
- `docs/nodejs/NODE_PROFILE_V1_1_USER_DRAFT.md`
- `docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md`
- `docs/nodejs/MIGRATION_FROM_RHINO.md`
- `docs/nodejs/design/RHINO_COMPAT_LOADER.md`
- `docs/nodejs/reports/phase11/PHASE11A_SUMMARY.md`
- `docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json`
- `docs/nodejs/types/autojs6-node/README.md`

## Pro v9 给出的目标形态

Auto.js Pro v9 文档中，Node.js 的定位可以概括为：

- 旧 `.js` 默认仍走 Rhino/第一代 API，Node.js 需要显式启用。
- Node 入口包括文件头 `"nodejs";`、`.node.js`、`.mjs`。
- Pro 9 模块和 Node 内置模块都通过 `require()` 使用，不再默认把第一代
  API 全局变量注入 Node。
- 第二代 API 大量采用 Promise/异步模型，例如截图权限和图片处理。
- 可以使用 Node.js 内置模块和 npm 包，文档示例以 Node.js 16.x 为参照。
- 可以通过 `$autojs.java` 访问 Java/Android API。
- 可以通过 `require("rhino").install()` 安装 `Packages`、`java`、`android`
  等类似 Rhino 的 Java 访问入口。
- Pro 9 文档也明确说明 `importClass`、`importPackage` 暂不支持，`JavaAdapter`
  当前也不是可用的完整 Rhino 等价实现。
- Node 使用事件循环模型，不再提供 Rhino `threads` 那种共享运行时对象的线程
  模型；`worker_threads` 方向也要求子线程不能访问 Auto.js API。
- Pro 9 提供 `"nodejs ui"` / `"ui-thread"` 这样的 UI 线程入口语义。

这给 AutoJs6 的对照带来两个不同目标：

- Node.js 能力：现代 JavaScript、模块系统、包解析、受控 Node 内置能力。
- 脚本兼容能力：让现有 Rhino 脚本作者能逐步迁移主要 AutoJs API。

AutoJs6 当前更接近第二个目标的安全收敛版本，而不是 Pro v9 行为的逐项复制。

## 总体结论

AutoJs6 当前已经具备较完整的 Node.js runtime 骨架：

- 嵌入式 Node/V8/libuv 运行路径。
- Node 24.5.0 native headers 和 `libnode.so`。
- `arm64-v8a`、`armeabi-v7a`、`x86_64` 三个 ABI 的 native runtime 资产。
- Node 脚本入口路由、后端选择、独立 Android 服务进程。
- CommonJS、部分 ESM、局部动态 `import()`、轻量 TypeScript 路由。
- scoped filesystem、受控包解析、静态 `node_modules` 依赖图。
- AutoJs bridge 协议、权限声明、资源注册、超时、取消、销毁清理。
- 一批 Android/AutoJs 能力的 Node bridge module。
- TypeScript 声明、示例、项目模板、测试门禁和打包烟测。
- 显式 Rhino compatibility loader：`require("autojs6:compat")`。

但它还不是 Auto.js Pro v9 的完整等价实现，也不是 Rhino 第一代 API 的
drop-in replacement。尤其需要注意：

- 默认不会把 Rhino 全局变量安装到 Node。
- 默认没有 `$autojs.java`。
- 默认没有 `require("rhino").install()`。
- 默认不暴露 raw Java/Android/Rhino 对象给 V8。
- `worker_threads`、raw network、native addon、`child_process` 等仍按 Safe
  Node Profile 受限或拒绝。
- Rhino `augment` 兼容已经建立了 inventory、状态 manifest 和一批 facade，
  但仍有大量 API 是 `partial`、`unsupported` 或 `blocked_by_design`。

### P12-34 文档同步结论

P12-34 将 Phase 12 已完成的脚本迁移结论同步到正式文档、Pro 9 对比、
兼容矩阵、安全模型、测试说明和类型声明 README。当前不应再把这些已完成
P12 surface 标为 provider gap：

- `events.keys` 已通过 `require("autojs6:compat").keys` 和
  `compat.events.keys` 提供纯常量；Android 按键观察仍属于
  `input_observer` provider POC，而不是 `events.keys` 缺口。
- `compat.tasks.addDisposableTask()`、`removeTask()`、`queryTasks()` 以及
  `queryTimedTasks()`、`getTimedTask(id)`、`removeTimedTask(id)` 已作为
  Node-owned one-shot WorkManager 迁移路径；daily/weekly/user database 和
  IntentTask 仍以 `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED` 稳定拒绝，raw
  task object mutation 以 `ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN` 拒绝。
- `barcode` / `qrcode` 已完成 P12-27 到 P12-30：`require("barcode")`、
  `compat.barcode` 和 QR-only `compat.qrcode` 支持 JSON-safe snapshot 和
  path/image-handle/capture 输入；packaged/live 行为仍取决于 ML Kit provider
  可用性。
- `s13n.bytes` 已通过 `require("s13n").bytes(...)` 和
  `compat.s13n.bytes(...)` promoted 为纯 JS 同步 helper；
  `s13n.color`、`s13n.point`、`s13n.throwable`、`s13n.time` 保持
  `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED`。
- `require("rhino")` 仍是受控 JSON-only shim：默认
  `rhino.run()` 使用 `ERR_AUTOJS6_RHINO_SHIM_DISABLED` 拒绝，
  `rhino.install()` 使用 `ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED` 拒绝。
- P12-33 的 `RhinoNodeDualEngineCompatInstrumentationTest` 和
  `p12-33-fake-device-results.json` 只是 fake device evidence，不授予新的
  Java/Rhino/global/provider 权限。

## 当前实现基线

### 运行时和版本

当前代码中的 Node Profile：

- Profile：`AutoJs6 Safe Node Profile`
- Engine version：`autojs6-node-profile-v1.1`
- Node header/runtime baseline：`v24.5.0`
- 默认 runtime slot：`node24_5`
- 默认构建后端：`embedded`
- 默认发布形态：`nodeFull`

主要实现位置：

- `app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt`
- `app/src/main/cpp/node_runtime_api_v1.h`
- `app/src/main/cpp/node_runtime_adapter_24_5.cpp`
- `app/src/main/cpp/node_bridge_sources.cpp`
- `app/src/main/jniLibs/arm64-v8a/libnode.so`
- `app/src/main/jniLibs/armeabi-v7a/libnode.so`
- `app/src/main/jniLibs/x86_64/libnode.so`

ABI 状态：

| ABI | 当前状态 |
| --- | --- |
| `arm64-v8a` | 有 `libnode.so`，属于当前支持范围 |
| `armeabi-v7a` | 有 `libnode.so`，属于当前支持范围 |
| `x86_64` | 有 `libnode.so`，属于当前支持范围 |
| `x86` | 无 `libnode.so`，当前不是 Phase 11 必需 ABI |

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
- `.tsx` 进入 Node 路由后由能力策略拒绝或构建前处理
- `project.json` 中 `type: "node"` 或 `projectType: "node"`
- 加密脚本头中的 Node 执行标记

与 Pro v9 一致的是：普通 `.js` 默认仍保留 Rhino 兼容路径。与 Pro v9
不同的是：AutoJs6 额外纳入了轻量 TypeScript 路由，并将 `.cjs` 作为最稳的
CommonJS 用户入口。

### 后端选择

`NodeRuntimeCapabilities.backendSwitches` 当前支持：

- `rhino`
- `native`
- `embedded`
- `auto`

默认路径是 `embedded`。`rhinoOnly` 发布形态会强制回落到 Rhino，并通过
`ERR_AUTOJS6_NODE_DISABLED` 等诊断说明失败原因，不会静默把 Node 脚本当作
普通 Rhino 脚本执行。

### Android 进程和服务

当前 manifest 和代码中已经具备独立 Node 运行结构：

- `NativeNodeEmbeddedProbeService`
- `NativeNodeEmbeddedScriptService`
- `NativeNodeEmbeddedScriptServiceSlot1`
- `NativeNodeEmbeddedScriptServiceSlot2`
- `NativeNodeSandboxedScriptService`
- `NodeBridgeEngineDispatchService`
- `NodeBridgeUiDispatchService`
- `NodeBridgeUiActivity`

这说明当前不是单纯复用 Rhino 执行器，而是已经有 Node 运行进程、桥接调度、
UI 调度和预留 slot 的 Android 运行时结构。

### CommonJS、包解析和依赖

已经具备：

- `require()`
- `module.require()`
- `module.exports`
- `exports`
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
- resolver trace 和诊断
- `require("autojs6:profile")` 只读诊断

限制：

- 不是完整桌面 Node resolver。
- embedded file engine 依赖静态预加载和 source budget。
- 动态 `require()` 如果不能被预加载，运行时会要求使用静态字符串
  `require(...)` 或显式 `moduleSources`。

### ESM 和动态 import

当前状态是 `partial`：

- `.mjs` 路由 Node。
- 支持局部静态 import。
- 支持 selected builtin import。
- 支持 top-level await。
- 支持 CommonJS 默认导入。
- 支持部分 package conditions。
- 支持本地动态 `import()`。

仍不等价于桌面 Node.js：

- 不支持自定义 loader hooks。
- 不支持网络 URL import。
- 不支持绝对路径或越权路径 import。
- 不支持 native addon。
- `require()` 加载含 async graph 的 ESM 会拒绝。

### TypeScript

当前仓库具备轻量 TypeScript 路由和验证门禁：

- `.ts`
- `.mts`
- `.cts`
- TypeScript 示例、类型声明和验证任务。

限制：

- `.tsx` 不是嵌入式运行时直接支持能力。
- 不读取完整 `tsconfig.json` path mapping。
- 不在 embedded Node 中运行任意 TypeScript compiler plugin。
- 运行时只处理 erasable syntax 方向，完整编译应放到宿主侧或打包前。

### Node 内置模块

当前不是完整桌面 Node.js，而是 Safe Node Profile 下的受限 builtin 集合。

稳定或部分可用方向包括：

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

默认禁用或策略拒绝：

- `child_process`
- `cluster`
- `dgram`
- `domain`
- `inspector`
- `repl`
- `vm`
- `wasi`
- `worker_threads`
- `process.binding`
- `process._linkedBinding`
- `process.dlopen`
- native `.node` addon

网络相关需要单独说明：

- `fetch`、`axios`、`websocket` 是受控 bridge-backed 能力。
- raw `net`、`tls`、`dns`、`dgram`、socket server、upgrade、CONNECT 等仍保持拒绝。
- `http`、`https` 不是 Pro v9 示例中那种无限制桌面 Node 能力，必须按
  AutoJs6 网络策略和 provider 状态理解。
- live network 默认需要 `autojs.nodejs.network.experimental=true` 和
  `network` capability。

### scoped filesystem

AutoJs6 Node `fs` 不是 Android 全盘文件能力，而是 scoped fs：

- 根为脚本工作目录或项目根目录。
- 拒绝绝对路径越权。
- 拒绝 `..` escape。
- 拒绝 NUL byte。
- 拒绝 `/proc`、`/sys`、`/dev`。
- 拒绝 symlink escape。
- 拒绝 realpath escape。
- FileHandle、fd、watcher 等都必须有执行期归属和清理策略。

这与 Rhino `files` 模块的习惯差异很大。迁移时不能把
`files.read("/sdcard/...")` 简单替换为 `fs.readFileSync("/sdcard/...")`。

### npm 和依赖

已具备：

- vendored pure JavaScript dependencies。
- 静态 `node_modules` package graph。
- host-side project wizard / validator。
- host-side dependency sanitizer / compatibility report。
- offline real npm corpus。
- package `exports`、`imports`、scoped packages 等兼容测试。

尚未具备：

- Android 端运行时 `npm install`。
- Android 端 install/update/remove/list/verify/prune package manager。
- lifecycle scripts / postinstall。
- native addon build。
- unchecked `.bin` shims。

这与 Pro v9 文档中“终端里 `npm i --no-bin-links 模块名`”的用户体验有明显差异。
AutoJs6 当前更接近“宿主侧或打包前整理依赖，运行时执行已净化的纯 JS 依赖”。

## AutoJs bridge 能力

### 协议和权限模型

当前已经有：

- `NodeBridgeRequest`
- `NodeBridgeResponse`
- `NodeBridgeError`
- `NodeBridgeModules`
- `NodeBridgeCapabilityProvider`
- `NodeBridgeProviderRegistry`
- `NodeBridgePermissionManifest`
- `require("autojs6:bridge")`
- `node.permissions`
- `autojs6.node.permissions`
- `node.bridgeLimits`
- `autojs6.node.bridgeLimits`
- pending call timeout
- service destroy rejection
- AbortSignal cancellation
- resource registry
- provider health / diagnostics

capability 映射示例：

- `toast` 需要 `toast`
- `app.launchPackage` / `app.launchApp` 需要 `app.launch`
- `app.launch` 是 `app.launchPackage` 的 Node 兼容别名
- `app.openAppSetting` 需要 `app.settings`
- `app.startActivity` / `app.viewFile` / `app.editFile` 需要 `app.activity`
- `app.getPackageName` / `app.getAppName` / `app.isInstalled` 需要显式
  `app.query`
- `media_projection` 映射到 `screen_capture`
- `image.captureScreen` 需要 `image` 和 `screen_capture`
- `ocr` 需要 `ocr` 和 `image`
- `fetch` / `websocket` 需要 `network`
- `java` 映射到 `java_interop`
- `shell.exec({ root: true })` 需要 `shell.root`

### 当前 bridge 模块

`NodeBridgeModules` 当前注册的用户可见桥接模块如下：

| 模块 | 方法范围 | 当前判断 |
| --- | --- | --- |
| `toast` | `showToast`, `toast` | 已有基础 toast bridge |
| `app` | `launchPackage`, `launchApp`, `openAppSetting`, `startActivity` | 基础应用能力，范围小于 Rhino `app` |
| `engines` | `myEngine`, `all`, `stopAll`, `stopSelf`, `execScript`, `execScriptFile` | 受控脚本调度，不暴露 raw engine 对象 |
| `dialogs` | `alert`, `confirm`, `input`, `select` | Promise 化对话框能力，需关注 Activity/provider 生命周期 |
| `accessibility` | `isEnabled`, `ensureEnabled`, `click`, `back`, `home`, `recentApps`, `findByText`, `clickText`, `findOne`, `findAll`, `longClick`, `setText`, `scrollForward`, `scrollBackward` | P13-19 records selector/accessibility as partial: selector v2 JSON descriptors and compat selector handles are covered, direct `waitFor`/`pickup`/selector-style `detect` stay stable-denied, and gesture, packaged service, raw node, root/Shizuku parity remain open |
| `media_projection` | `requestScreenCapture`, `nextImage`, `stop` | P13-20 records screenshot/image/OCR as partial: API shape is reserved, optional real-device capture smoke exists, but bridge-owned prompt, foreground disclosure, stop cleanup, and packaged behavior remain blocked |
| `image` / `images` | `captureScreen`, `readImage`, `saveImage`, `clip`, `resize`, `grayscale`, `threshold`, `findImage`, `matchTemplate`, `findColor`, `findMultiColors`, `recycle` | P13-20 records screenshot/image/OCR as partial: scoped `readImage`/`recycle` are live provider paths, while capture/save/transform/match/color operations remain fail-closed; no raw Bitmap/Mat/OpenCV object is exposed |
| `ocr` | `recognize`, `recognizeText`, `detectTextBounds` | P13-20 records screenshot/image/OCR as partial: ML Kit OCR works over provider-owned image handles; Paddle/Rapid/plugin provider selection stays stable-denied for v1.1 while capture/path packaged evidence remains blocked |
| `storage` / `storages` | `get`, `put`, `remove`, `clear`, `keys`, `contains` | JSON-only，按项目/包/工作目录隔离 |
| `database` / `sqlite` | `open`, `exec`, `run`, `get`, `all`, `transaction`, `close` | 受控 SQLite handle，不是 raw SQLite/Android DB 对象 |
| `notifications` | `notify`, `cancel`, `cancelAll` | 通知 post/cancel 子集 |
| `sensors` | `getAvailableSensors`, `once`, `subscribe`, `unsubscribe`, `drainEvents` | 当前限 accelerometer、gyroscope、light 等受控路径 |
| `ui` | `showLayout`, `update`, `batchUpdate`, `close`, `drainEvents` | JSON descriptor 和 opaque handle，不是 Rhino UI DSL/raw View |
| `clipboard` | `getText`, `setText`, `hasText` | 对应 Pro v9 `clip_manager` 和 Rhino `setClip/getClip` 的受控形态 |
| `device` | `isScreenOn`, `wakeUp`, `vibrate` | 设备信息/动作子集，范围小于 Rhino `device` |
| `shell` | `exec` | 非交互 shell，替代部分 Rhino shell 用法；root 需显式 capability |
| `fetch` | `request` | 受控网络，默认 experimental |
| `websocket` | `connect`, `send`, `close`, `drainEvents` | 受控 WebSocket，默认 experimental |
| `work_manager` | `scheduleOnce`, `schedulePeriodic`, `cancel`, `list` | 受控 WorkManager 调度，不是完整 Rhino `tasks` |
| `java` | `type`, `callStatic`, `new` | 实验性 allowlist，不是 Rhino Java bridge |

TypeScript 声明还覆盖：

- `autojs6:lifecycle`
- `autojs6:compat`
- `files`
- `base64`
- `colors`
- `formatter` / `fmt`
- `converter` / `cvt`
- `mime`
- `nanoid`
- `opencc`
- `pinyin`
- `pinyin4j`
- `plugins`

## 与 Auto.js Pro v9 的主要差异

| 方面 | Auto.js Pro v9 文档 | AutoJs6 当前状态 |
| --- | --- | --- |
| 默认引擎 | 默认 Rhino，显式启用 Node | 一致，普通 `.js` 默认 Rhino |
| Node 入口 | `"nodejs";`、`.node.js`、`.mjs` | 支持这些入口，并额外支持 `"node";`、`.cjs`、TS 路由 |
| Node 版本参照 | 文档以 Node 16.x 为参照 | 当前 baseline 为 Node 24.5.0 |
| 模块导入 | Pro 9 模块需要 `require()` | 一致，但 AutoJs6 还要求 Safe Profile 和 capability |
| npm | Android 终端内 `npm i --no-bin-links` | Android 运行时 npm install 尚未完成，当前偏 host/vendor |
| Node builtins | 文档示例更接近普通 Node 使用 | 受 Safe Profile 约束，很多 builtin 是 wrapper 或 denial |
| 文件系统 | 示例可读 Android 绝对路径 | scoped fs，默认拒绝越权路径 |
| Java/Android | `$autojs.java`、`require("rhino").install()` | P13-12 已有只读 `$autojs` 和 gated `$autojs.java` facade；P13-13 增加实验 allowlist 下的 `java.lang.Math`、`java.util.UUID`、`android.graphics.Rect` handle 构造/实例调用/字段读取/释放；P13-14 增加显式 experimental `rhino.install` Java proxy，可安装 `Packages`、`java`、`android` 代理但仍只走 allowlist；P13-15 决定 `importClass`/`importPackage` 在 Node 中稳定拒绝并给出赋值式迁移；P13-16 决定 `JavaAdapter` 稳定拒绝并预留 `java.defineClass()` / `$autojs.java.defineClass()` replacement；默认仍拒绝 Java 调用，线程 dispatch、实际 proxy-interface provider 和完整 Java/Rhino parity 仍未完成 |
| Rhino 全局兼容 | 第一代 API 保留在 Rhino；Node 需 require 新模块 | Rhino 保留；Node 有显式 `autojs6:compat`，但不默认安装 globals |
| 线程 | Node event loop，`worker_threads` 方向受限 | `worker_threads` 默认关闭，Rhino `threads` 不作为 Node 兼容目标 |
| UI 线程 | `"nodejs ui"` / `"ui-thread"` | P13-11 已补 `NodeEntryDirective` metadata、descriptor/project config 透传和 gate；P13-17 记录 direct `require("ui")` live Activity provider evidence、JSON layout/update/event drain/close 和 provider cleanup；Activity lifecycle、Back/Recents、orientation、live packaged UI、实际 UI-thread dispatch 仍未等价 |
| 网络 | 文档列出 `http/https` 等 Node 能力 | raw network 默认拒绝，推荐受控 `fetch`/`axios`/`websocket` |
| 打包 | Pro v9 自有打包体系 | AutoJs6 已有 packaged smoke、runtime libs、release flavor、ABI 策略，但 packaged long-running 仍未完成 |

## Rhino `augment` API 迁移现状

### 原始 Rhino augment 模型

当前 `org/autojs/autojs/runtime/api/augment` 是 Rhino 运行时内置 API 的主要实现。
`ScriptRuntime.initPrologue()` 会把这些模块安装到 Rhino `TopLevelScope`。

这些实现依赖：

- `org.mozilla.javascript.Scriptable`
- `ScriptableObject`
- `BaseFunction`
- `RhinoUtils`
- Rhino 注解和 Rhino 类型转换
- Rhino/Java 对象模型和脚本线程模型

Node 用户源码实际在 embedded Node/V8 中执行，不能直接获得这些 Rhino
`ScriptableObject` 全局对象。把 Rhino 对象直接暴露给 V8 会带来对象生命周期、
线程、Java 对象泄漏、权限绕过和调试归属问题，因此 AutoJs6 采用显式 facade
和 bridge/handle 模型。

### Phase 11A 总量

Phase 11A 已建立机器可追踪的兼容矩阵：

- Rhino augment modules：73
- Rhino API entries：879
- Node bridge modules with evidence：23
- Node type modules with evidence：46
- Rhino modules with bridge evidence：24
- Rhino modules with type evidence：37
- Rhino modules without Node surface evidence：35

状态统计：

| 状态 | 数量 | 含义 |
| --- | ---: | --- |
| `supported` | 134 | Node 中已有同步或等价语义 |
| `supported_async` | 52 | Node 中可用，但必须通过 Promise/async |
| `partial` | 332 | 部分可用或 API 形态未完全对齐 |
| `renamed` | 1 | 能力可用，但名称或入口变化 |
| `unsupported` | 388 | 尚未实现，且未判定为永久拒绝 |
| `blocked_by_design` | 45 | 违反 Safe Node Profile，明确拒绝 |

权威跟踪文件：

- `docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md`
- `docs/nodejs/rhino-compat/status-manifest.json`
- `build/reports/nodejs/rhino-augment-api-inventory.json`
- `build/reports/nodejs/rhino-node-compat-gap.json`

### `autojs6:compat`

当前已经有显式 Rhino compatibility loader：

```js
"nodejs";

const compat = require("autojs6:compat");
```

重要规则：

- `require("autojs6:compat")` 不会默认污染 `globalThis`。
- 如需 Rhino 风格全局名，必须显式调用 `compat.install({ globals: true })`。
- `"nodejs compat"` directive 在 Phase 11A 中被拒绝，原因是过于隐式，不利于
  source scanning 和 packaged 行为审计。
- `require("rhino-compat")` 被保留，不作为当前入口。
- `require("rhino")` 已保留为受控 facade；`rhino.run({ experimental: true })`
  是 JSON-only POC；默认 `rhino.install()` 仍拒绝。
- P13-14 只实现了显式 experimental `rhino.install` Java proxy POC，尚不是
  Pro v9 的完整 `require("rhino").install()` 等价行为。
- P13-15 已决策 `importClass` / `importPackage`：Node 中不模拟 Rhino 动态
  import 语义，显式 helper 返回 `ERR_AUTOJS6_RHINO_IMPORT_UNSUPPORTED`，
  迁移时应从 P13-14 proxy target 赋值到本地变量。
- P13-16 已决策 `JavaAdapter`：Node 中不模拟 Rhino adapter，显式
  `rhino.JavaAdapter()` 返回 `ERR_AUTOJS6_RHINO_JAVA_ADAPTER_UNSUPPORTED`；
  `java.defineClass()` / `$autojs.java.defineClass()` 已预留但当前仍返回
  `ERR_AUTOJS6_JAVA_METHOD_DENIED`。

当前 `autojs6:compat` 已覆盖或部分覆盖：

- `toast`
- `toastLog`
- `sleep`
- `setClip`
- `getClip`
- scoped `files`
- `base64`
- `colors`
- `formatter` / `fmt`
- `converter` / `cvt`
- `mime`
- `nanoid`
- `util.inspect` 迁移目标
- `opencc`、`pinyin`、`pinyin4j` 的受限文本工具方向
- `storages`
- callable `sqlite`
- `console`、timers、error stack 迁移
- selector/accessibility 第一版 DSL
- `app`
- `device`
- callable `shell`
- `images` 读写、变换、查找、回收
- `requestScreenCapture`
- `captureScreen`
- callable `ocr`

这些 facade 不是完整 Rhino 同步阻塞 API。凡是跨 Android bridge 的能力，
都应按 Promise 使用。

示例：

```js
"nodejs";

const compat = require("autojs6:compat");

await compat.toastLog("start");
await compat.sleep(500);
await compat.setClip("hello");
const text = await compat.getClip();

const ok = compat.text("OK").clickable();
await ok.click({ timeoutMs: 3000 });
```

如果确实需要全局迁移体验：

```js
"nodejs";

const compat = require("autojs6:compat");
compat.install({ globals: true });

await toastLog("start");
await sleep(500);
await setClip("hello");
const ok = text("OK").clickable();
await ok.click({ timeoutMs: 3000 });
```

### 主要 augment 模块对应状态

| Rhino/augment 类别 | Rhino 典型用法 | Node 当前对应 | 状态 |
| --- | --- | --- | --- |
| `global` | `toastLog`, `sleep`, `setClip`, `getClip`, selector globals | `autojs6:compat` | 部分，已覆盖低风险和部分自动化全局 |
| `app` | `app.launchPackage`, intent/broadcast/helpers | `app`, `autojs6:compat.app` | 部分，基础启动/设置/Activity、URL/email/uninstall、exact app lookup、`parseUri` descriptor 可用，raw URI/广播/列表/kill/dual 等缺口仍在 |
| `autojs` | `$autojs`, version/resource 等 | `autojs6:profile`, `engines` 等分散能力 | 部分 |
| `automator` / `auto` / `selector` | `text("OK").findOne().click()` | `accessibility`, `autojs6:compat` selector handle | 部分，P13-19 固定 selector v2/compat handle 证据；`waitFor`、手势、raw node、packaged service、root/Shizuku 仍未完成 |
| `root_automator` | root input event 自动化 | 无稳定 Node bridge | `blocked_by_design` 或待单独 provider |
| `files` | `files.read/write/createWithDirs` | scoped `files`, scoped `fs` | 部分，路径语义与 Rhino 不完全一致 |
| `images` | 截图、读写、找图、找色、模板匹配 | `image/images`, `media_projection`, `autojs6:compat.images` | 部分，P13-20 固定 scoped read/recycle 和 compat wrapper 证据；MediaProjection、save/transform/match/color、packaged 行为仍未完成 |
| `ocr` | `ocr.recognize`, MLKit/Paddle/Rapid | `ocr`, `autojs6:compat.ocr` | 部分，P13-20 固定 ML Kit/provider-owned handle 证据；Paddle/Rapid fallback、packaged model/path/capture 仍未完成 |
| `barcode` / `qrcode` | 条码/二维码识别或生成 | `barcode`, `autojs6:compat.barcode`, QR-only `autojs6:compat.qrcode` | `supported_async`，已完成 P12-27 到 P12-30 provider/facade/examples |
| `dialogs` | 同步/阻塞式 dialogs | `dialogs` Promise bridge | 部分，API 形态不同 |
| `console` | `console.log`, 浮动控制台 | Node console 和 `autojs6:compat.console` | 部分，浮动控制台控制为 no-op 或 unsupported |
| `device` | 设备信息、亮屏、震动、系统状态 | `device`, `autojs6:compat.device` | 部分 |
| `events` / `keys` | 按键、广播、事件监听 | `node:events` 仅覆盖 JS 事件；`compat.keys` / `compat.events.keys` 覆盖 key constants；`input_observer` 是 provider POC | `events.keys` 已支持，观察/拦截仍 provider-gated |
| `threads` | `threads.start`, shared runtime 对象 | async/await、`engines`、`work_manager` 方向 | `blocked_by_design`，不是 Node 默认兼容目标 |
| `timers` | `setTimeout/setInterval` 和 AutoJs 扩展 | Node timers, `autojs6:compat` | 部分，AutoJs 特有扩展仍需核对 |
| `ui` | Rhino XML/DSL UI、raw View | `ui` JSON descriptor/opaque handle | 部分，不暴露 raw View，不支持 `ui.run/ui.post` 等价 |
| `floaty` | 悬浮窗、`floaty.window`, `floaty.getClip` | `getClip` 迁移到 clipboard/compat；P13-18 记录 `ui.overlay` JS facade/types、permission/settings/closeAll live gate 和 packaged `SYSTEM_ALERT_WINDOW` metadata；live overlay creation 仍 fail-closed | `require("floaty")`、`compat.floaty`、raw `floaty.window/rawWindow` View parity 和真实 overlay provider 仍未实现 |
| `storages` | 本地存储 | `storage/storages`, `autojs6:compat.storages` | 部分，JSON-only、命名空间隔离 |
| `sqlite` | SQLite helpers | `database/sqlite`, callable `compat.sqlite` | `supported_async` |
| `shell` / `shizuku` | shell/root/shizuku | `shell.exec`, callable `compat.shell` | shell 部分；Shizuku 未开放 |
| `http` | Rhino http helpers | controlled `fetch` / `axios` / network facade | API 不等价，默认 experimental |
| `web` / `WebSocket` | WebSocket 等 | `websocket` controlled bridge | 部分 |
| `media` | 播放/录音等多媒体 | 暂无完整 Node bridge | 部分或待实现 |
| `sensors` | 传感器监听 | `sensors` limited subset | 部分 |
| `notice` | 通知 | `notifications` | 部分 |
| `plugins` | AutoJs plugin 能力 | JS-only local plugin prototype | 部分，非完整插件市场/签名体系 |
| `tasks` | 定时任务、用户任务数据库 | `work_manager`, `autojs6:compat.tasks` | 部分，一次性 disposable task 已支持；daily/weekly/IntentTask/user database/raw object 仍拒绝 |
| `colors` | 颜色解析、转换、Android ColorStateList | `colors`, `autojs6:compat.colors` | 大量纯 JS 可用，Android 对象相关 blocked |
| `crypto` | AutoJs crypto 工具 | Node `crypto` 部分能力 | 需要 API 对齐 |
| `base64` | Base64 工具 | `base64`, Buffer | 已有 facade |
| `canvas` | Canvas 绘制 | 暂无稳定 Node bridge | 待实现或明确非目标 |
| `recorder` | 录制相关 | 暂无稳定 Node bridge | 待实现 |
| `zip` | 压缩/解压 | `zlib` 不等价，暂无 AutoJs zip bridge | 待实现 |
| `mime` | MIME 工具 | `mime` facade | 已有 |
| `sysprops` | 系统属性 | 暂无稳定 Node module | 待实现，需权限审查 |
| `opencc` | 中文转换 | `opencc` 类型/facade，资源依赖路径 stable unsupported | 部分 |
| `pinyin` / `pinyin4j` | 拼音转换 | `pinyin`, `pinyin4j` fixture-backed subset | 部分到已支持子集 |
| `nanoid` | ID 生成 | `nanoid` facade | 已有 |
| `formatter` / `converter` | 格式化、单位转换 | `formatter/fmt`, `converter/cvt` | 已有子集 |
| `s13n` / `jsox` / `util` | 序列化、JS 扩展、工具 | `s13n.bytes`, `autojs6:compat.s13n.bytes`, Node builtin 或暂无对应 | `s13n.bytes` 已支持，其余继续分批决策 |
| `continuation` | Rhino continuation/coroutine | Node Promise/async 模型 | 不建议等价迁移 |
| Java bridge | `Packages`, `java.*`, `android.*`, `importClass`, `JavaAdapter` | P13-13 experimental `java` allowlist；P13-14 `rhino.install` Java proxy POC；P13-15 rejects `importClass`/`importPackage` with stable migration guidance | 默认不等价 |

### 最关键的脚本开发者缺口

如果目标是“脚本开发者把现有 Rhino 脚本切到 Node 后，主要 API 仍能正常使用”，
当前已经有一条显式迁移路径，但还没有完全完成：

短期推荐写法：

```js
"nodejs";

const compat = require("autojs6:compat");

await compat.toastLog("start");
await compat.app.launchPackage("com.example");
const ok = await compat.text("OK").findOne(3000);
await ok.click({ timeoutMs: 3000 });
```

显式全局兼容写法：

```js
"nodejs";

const compat = require("autojs6:compat");
compat.install({ globals: true });

await toastLog("start");
await app.launchPackage("com.example");
const ok = await text("OK").findOne(3000);
await ok.click({ timeoutMs: 3000 });
```

仍不能承诺的写法：

```js
"nodejs";

toast("ok");                 // 未 install globals 时不可用
text("OK").findOne().click(); // 需要 await，且返回 handle/snapshot
require("rhino").install();   // 当前不存在
java.lang.StringBuilder;      // 当前不是默认 Node 能力
```

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
- `node_modules` 静态图。
- partial ESM。
- local dynamic import。
- 轻量 TypeScript 路由和验证。
- `autojs6:profile`。
- `autojs6:bridge`。
- `autojs6:lifecycle`。
- `autojs6:compat`。
- bridge permission manifest。
- bridge limits。
- bridge resource registry。
- error code 体系。
- TypeScript declaration package：`docs/nodejs/types/autojs6-node`。
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

### 已有或部分可用的 Rhino compat facade

- `toast` / `toastLog`
- `sleep`
- `setClip` / `getClip`
- scoped `files`
- `base64`
- `colors`
- `formatter` / `fmt`
- `converter` / `cvt`
- `mime`
- `nanoid`
- `opencc` / `pinyin` / `pinyin4j` 的受限方向
- `storages`
- `sqlite`
- `console`
- timers
- selector/accessibility DSL
- `app`
- `device`
- callable `shell`
- `images`
- screenshot wrappers
- ML Kit-only OCR facade

### 已明确拒绝或保持非目标的高风险能力

- native `.node` addon
- unrestricted `child_process`
- unrestricted raw network
- raw socket server/listen/upgrade/CONNECT
- private Node bindings
- `process.dlopen`
- unrestricted `vm`
- production Inspector
- raw Java/Android object graph
- raw Android scheduler/broadcast/content provider/system manager access
- raw `node:wasi`
- default Rhino globals in Node
- Pro v9 style `require("rhino").install()`
- `importClass` / `importPackage` / `JavaAdapter` 穿透到 Node

这些拒绝不是遗漏，而是 Safe Node Profile 的安全边界。

## 尚需完成的能力

### Phase 11 required internal blockers

`docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json` 中仍为 open 的 required internal
blockers：

- `crash_evidence_automation`
- `android_side_package_installation`
- `packaged_long_running`
- `second_execution_slot`
- `full_heavy_resource_stress`

含义：

- crash marker、next-launch collection、symbolization、chaos injection 尚未形成
  完整自动化闭环。
- P13-09 已补 Android 端 app-private local package store，支持
  install/update/remove/list/verify/prune、transaction、cancel、lock 和
  integrity；JS API、registry/download、signed native payload 和 distribution
  integration 尚未完成。
- packaged APK long-running Node service mode 尚未完成。
- 第二执行 slot 已有显式 one-shot 路由和 focused instrumentation，但默认调度、
  packaged 行为、slot crash evidence 和 stress promotion 仍未完成。
- P13-08 已补 `verifyNodePhase13FullHeavyResourceStress` 状态 gate、可比较
  report contract、accepted/rejected 形状 fixtures 和归档前校验 helper；
  fixtures 只验证 gate fail-closed 规则，真实 8 小时 quick soak 和 24 小时
  final soak 证据仍未完成。

### Conditional decisions

仍需产品和技术决策：

- `worker_threads`：实现受控 worker，或明确由 AutoJs6 process-worker/slot
  模型替代。
- `controlled_wasi`：仅支持纯 WebAssembly，还是实现受控 `autojs:wasi`。
- `x86`：当前 optional，不是 Phase 11 必需 ABI。

### 与 Pro v9 的能力差距

- Android 端运行时 `npm install` 和全局 npm 安装能力未完成。
- P13-12 已提供 `$autojs` global v1 和 gated `$autojs.java` facade；P13-13
  已在实验 allowlist 下补充 Rect handle 实例调用、字段读取和释放；默认仍拒绝
  Java 调用，完整 Java object/thread parity 尚未完成。
- 默认 `require("rhino").install()` 仍拒绝；P13-14 仅提供
  `rhino.install({ experimental: true })` Java proxy POC。
- P13-15 已决定 `importClass` / `importPackage` 不作为 Node 能力；显式
  helper 稳定返回 `ERR_AUTOJS6_RHINO_IMPORT_UNSUPPORTED`。
- P13-16 已决定 `JavaAdapter` 不作为 Node 能力；显式 helper 稳定返回
  `ERR_AUTOJS6_RHINO_JAVA_ADAPTER_UNSUPPORTED`，`defineClass` replacement
  仍是稳定拒绝。
- `java.*`、`android.*`、`Packages`、`JavaAdapter` 不是默认 Node 能力。
- `"nodejs ui"` / `"ui-thread"` 已有 P13-11 metadata partial，但 Pro v9
  等价的 Activity lifecycle、Back/Recents 和实际 UI-thread dispatch 尚未完成。
- raw Node `http/https/net/tls/dns/dgram` 不作为默认能力。
- `worker_threads` 未作为默认可用能力。
- Pro v9 的部分第二代模块在 AutoJs6 中名称、形状或状态不同。

### 与 Rhino `augment` 的剩余缺口

高优先级缺口：

- 默认全局兼容不是目标，但显式 `compat.install({ globals: true })` 仍需继续扩展。
- selector DSL 需要更多 Rhino 选择器细节和常见链式写法；P13-19 已固定
  JSON selector/compat handle 证据，但 `waitFor`、gesture、packaged
  accessibility service、raw node 和 root/Shizuku tiers 仍未完成。
- `images` 需要继续覆盖更多 Rhino overload、权限路径、packaged 行为和资源回收；P13-20 已固定 scoped `readImage`/`recycle`、ML Kit OCR、compat wrapper 和 raw object denial 证据。
- `app` 需要扩展广播、邮件、intent helpers、应用信息查询等。
- `device` 需要扩展更多设备信息、电源、亮度、剪贴板兼容细节。
- `events.keys` 纯常量已支持；按键观察、拦截和 touch observer 仍是 provider
  gated。
- `threads` 需要明确迁移模式和示例，而不是照搬 Rhino 线程。
- `floaty` 仍未实现 Node provider；P13-18 仅固定 `ui.overlay` permission/settings/cleanup live gate，真实 overlay `show`/`update`/`drainEvents`/`close` provider 仍缺。
- Rhino UI DSL 到 Node UI bridge 的迁移层尚未实现。
- `tasks` 的一次性 disposable task 已迁移到 `work_manager`；用户任务数据库、
  daily/weekly 和 IntentTask 迁移尚未完成。
- `zip`、`canvas`、`recorder` 等缺失模块仍需 provider 或永久非目标决策；
  `barcode` / `qrcode` 已完成 JSON-safe provider/facade/examples，但 live
  packaged 可用性仍取决于 ML Kit provider。
- `shizuku` / root advanced shell 的权限和生命周期模型尚未完成。
- Java/Rhino compatibility 目前只有受控 POC：P12-25 `rhino.run`
  JSON-only execution adapter，以及 P13-14 `rhino.install` Java proxy；完整
  Pro/Rhino parity 仍未完成。

## 建议路线

### P0：把已存在 compat 能力产品化

- 将 `autojs6:compat` 的用户文档从设计文档提升为正式迁移文档。
- 将常用 API 按 `supported` / `supported_async` / `partial` / `unsupported`
  形成可搜索表格。
- 增加更多“Rhino 写法到 Node 写法”的一对一示例。
- 对 Promise 化差异给出明确迁移规则。
- 对 packaged APK 中可用/不可用的 compat API 单独标注。

### P0：自动化核心继续补齐

- selector DSL 的常见链式方法。
- accessibility action 的错误码和超时语义。
- screenshot permission 的真实设备路径和 packaged fallback。
- image handle 生命周期、找图、找色、模板匹配。
- OCR provider/model 可用性诊断。
- app/device/shell 常用 API 扩展。

### P1：生命周期复杂 API 独立推进

- `input_observer` provider；`events.keys` 纯常量已经 promoted。
- UI compat runtime facade，仍保持 `UiHandle` 和 JSON event snapshots。
- floaty/overlay 的真实 provider 仍需后续 gate；P13-18 已将 raw window parity 保持为非目标，并记录 `ui.overlay` partial promotion gate。
- `tasks` 继续拆分用户任务数据库和 broadcast trigger；WorkManager one-shot
  disposable 兼容路径已经 promoted。
- 线程迁移策略和示例库。
- 显式 Rhino JSON-only shim POC 和 P13-14 `rhino.install` Java proxy POC。

显式 Rhino JSON-only shim 已有 P12-25 POC，但仍不是默认能力。继续提供
`require("rhino")` 时，只允许如下受控形态：

```js
const rhino = require("rhino");

const result = await rhino.run({
  source: "toast('legacy')",
  timeoutMs: 5000,
  args: []
});
```

边界要求：

- 不在 V8 中安装 Rhino globals。
- 不把 Java object、Rhino `Scriptable`、Android `View`、`Context`、
  `AccessibilityNodeInfo` 带回 Node。
- 不支持 `importClass`、`importPackage`、`JavaAdapter` 穿透到 Node。
- 只返回 JSON、stdout/stderr 摘要和稳定错误对象。
- packaged APK 需要显式 metadata opt-in。

### P1：包管理与发布能力

- Android-side pure-JS package manager。
- 事务、锁、完整性校验、取消、无 lifecycle script。
- 打包时依赖图锁定和诊断。
- packaged long-running service。
- 第二执行 slot 的默认调度和 stress promotion。

### P2：低风险工具模块收尾

- `zip`
- `sysprops`
- `s13n`
- `jsox`
- `util` 其他子集
- `opencc` 字典资源路径
- `pinyin` 完整词典和地名/姓氏模式
- `barcode` / `qrcode` 后续只保留 provider-dependent live/packaged 证据补强

## 对脚本开发者的推荐迁移形态

优先使用显式模块：

```js
"nodejs";

const { showToast } = require("toast");
const app = require("app");
const accessibility = require("accessibility");

await showToast("start");
await app.launchPackage("com.example");
await accessibility.click(accessibility.text("OK"), { timeoutMs: 3000 });
```

迁移 Rhino 脚本时使用显式 compat：

```js
"nodejs";

const compat = require("autojs6:compat");

await compat.toastLog("start");
await compat.setClip("ready");

const ok = compat.text("OK").clickable();
await ok.click({ timeoutMs: 3000 });
```

确实需要旧全局名时再安装 globals：

```js
"nodejs";

const compat = require("autojs6:compat");
compat.install({ globals: true });

await toastLog("start");
await sleep(1000);
await setClip("ready");
```

迁移原则：

- 需要 Android bridge 的 API 基本都要 `await`。
- 不要期待 raw Java object、`View`、`AccessibilityNodeInfo`、`Bitmap` 进入 Node。
- 文件路径使用项目相对路径或 scoped API。
- 线程代码改为 async/await、AbortController、`engines`、`work_manager` 或未来 slot/worker 策略。
- `floaty`、复杂 UI、events、tasks、Java bridge 等脚本短期仍建议保留 Rhino 或等待专门迁移层。

## 验收标准

要认为 Node.js 集成真正满足“现有 Rhino 主要能力在 Node.js 引擎中正常使用”，
至少需要：

- `augment` API inventory 持续自动生成。
- 每个 Rhino API 都有显式迁移状态。
- Node bridge/runtime module/types/docs 与 inventory diff 无遗漏。
- `autojs6:compat` 覆盖主要全局函数和常用模块。
- 常用 Rhino 示例有 Node 兼容版本。
- 迁移测试能覆盖 Rhino 和 Node compat mode 的同一组核心用例。
- packaged APK 中覆盖核心 compat API。
- 缺能力时有稳定错误码和迁移提示。
- stop/timeout/destroy 后 bridge resource 归零。
- 没有 raw Java/Android/Rhino object 泄漏。
- Pro v9 对照文档、AutoJs6 Quick Start、Migration 文档、TypeScript 声明同步更新。

## 结论

AutoJs6 的 Node.js 集成已经超过“实验入口”阶段，具备 runtime、模块系统、
受控文件系统、桥接协议、权限模型、类型声明、示例、测试和打包验证等核心骨架。

P13-21 records task database provider promotion as partial: `work_manager` and
`autojs6:compat.tasks` one-shot scheduling are evidence-backed, while direct
`tasks`, daily/weekly/user database operations, IntentTask/broadcast triggers,
packaged promotion, and raw task object mutation remain blocked.

P13-22 records IntentTask/broadcast provider promotion as partial: Android/Rhino
receiver substrate and stable `compat.tasks` rejection stubs are
evidence-backed. For v1.1 these compat names remain Promise-returning stable
rejection stubs until a dedicated JSON-safe broadcast provider owns the future
surface; receiver registration, manifest mutation, boot/package/startup/custom
triggers, packaged install/trigger/remove behavior, and raw `Intent` /
`BroadcastReceiver` objects remain blocked.

P13-23 records bounded app/intent/broadcast Pro parity as ready: JSON-safe
`app.intent()` descriptors, normalized `app.startActivity()` options, exact
`app.query` lookups, scoped file view/edit helpers, `app.launch()` alias
parity, and JSON-safe `app.parseUri()` descriptors are evidence-backed.
`app.getUriForFile()`, `app.kill()`, dual-user helpers, and
`app.sendBroadcast()` are stable denials for P13-23 v1.1. Future raw
URI/FileProvider, root/Shizuku/dual-user, and broadcast authority belongs under
P13-22/P13-24 gates, while raw installed-app list APIs and raw Android
app/intent objects remain future-provider work. Packaged scoped-file Activity
routing is covered by the focused packaged app bridge smoke.

P13-24 records shell/root/Shizuku capability promotion as partial: ordinary
`shell.exec()` stays on the controlled provider, `shell.execRoot()` and
`compat.shell.execRoot()` are explicit root facades requiring `shell.root`, and
`shell.execShizuku()` / `shizuku: true` stay stable-denied for v1.1 until a
dedicated Shizuku provider owns authorization, lifecycle, cleanup, packaged
disclosure, rollback, and device matrix evidence.

P13-25 records notifications/settings/power manager promotion as partial:
`notifications.getPermissionStatus()`, `notifications.openSettings()`,
`notifications.openNotificationSettings()`,
`device.isIgnoringBatteryOptimizations()`, and
`device.openBatteryOptimizationSettings({ dryRun: true })` are evidence-backed
JSON-safe helpers requiring exact `notifications.settings` or `device.power`
where appropriate. Notification actions/listeners/rich layouts,
Do-Not-Disturb/exact-alarm policy, direct wakelock/power-manager APIs, OEM
power matrices, packaged disclosure, and raw Android notification/power objects
remain blocked.

P13-26 records media/recorder/mediainfo provider promotion as partial:
`media.getAudioStreamVolume()`, `media.getAudioStreamMaxVolume()`,
`media.getAudioStreamInfo()`, scoped `mediainfo.read()` / `mediainfo.get()`,
and `recorder.getStatus()` are evidence-backed JSON-safe helpers requiring
exact `media.audio`, `media.metadata`, or `media.recording` where appropriate.
Playback, real recorder sessions, foreground microphone disclosure, MediaStore
access, packaged media assets, migration examples, and raw Android media
objects remain blocked.

P13-27 records utility-heavy Rhino modules promotion as partial:
`require("jsox")`, direct `jsox.mathx` / `jsox.arrayx` / `jsox.numberx`
submodule requires, and `require("autojs6:compat").jsox` now expose the pure
Rhino `Mathx`, `Arrayx`, and `Numberx` helper subsets. Scripts can migrate
random/statistics/distance/log/pow/min/max helpers, array set/sort helpers, and
number parse/clamp/format helpers to Node, and can copy helpers with
`jsox.extend(target, "Mathx" | "Arrayx" | "Numberx")`. Default global `Math`
or prototype mutation, `zip`, `canvas`, OpenCC/pinyin packaged resources,
`sysprops`, and raw Android/OpenCV object parity remain blocked.

P13-29 closes the controlled HTTP/HTTPS client subset for v1.1:
`require("http")`, `require("node:http")`, `require("https")`, and
`require("node:https")` provide a controlled client facade over AutoJs6 fetch
for request/get, stream-style responses, timeout, redirect, provider-owned TLS
errors, live Android/OkHttp loopback fetch/axios/`http.get()` evidence, and
packaged `network` to `android.permission.INTERNET` metadata proof. This is not
unrestricted desktop networking; server/listen, raw sockets, proxy/tunnel
options, `CONNECT`, DNS/TLS session APIs, and broader packaged install/run
network behavior remain blocked.

P13-30 records net/tls/dns staged compatibility as partial: `require("net")`,
`require("node:net")`, `require("tls")`, `require("node:tls")`,
`require("dns")`, `require("node:dns")`, `require("dns/promises")`, and
`require("node:dns/promises")` are reserved limited builtins with TypeScript
coverage and focused tests. They support package-probing shapes such as
`SocketAddress`, `BlockList`, socket/server counters, TLS metadata, and
loopback/IP-literal DNS lookup, while real sockets, server listeners, network
DNS, Android resolver state, TLS sessions, fd/native handles, packaged INTERNET
proof, and live Android network evidence remain blocked.

P13-31 records dgram UDP capability as stable-denied: `require("dgram")`,
`require("node:dgram")`, `require.resolve("dgram")`, transitive package
requires, and local `node_modules/dgram` shadow attempts remain denied. UDP
client sockets, server bind/listen, multicast, broadcast, Android multicast
state, fd/native handles, packaged permission proof, and live Android UDP
evidence are not part of the current v1.1 Desktop profile.

P13-39 records native addon loading as partial: `nativeAddonProfile` exposes a
diagnostic-only decision snapshot while arbitrary `.node` loading,
`process.dlopen`, native builds, and runtime download-exec remain denied.
Desktop-profile support is still blocked on signed packaged artifacts, ABI and
hash allowlists, dependency scanning, crash isolation, and packaged
positive/hostile corpus evidence.

P13-32 records filesystem profile expansion as partial: Safe Node remains
`workingDirectory` scoped, `autojs6:profile.filesystemProfile` reports
`mode="scoped_working_directory"` with `additionalRoots=[]`, and `/sdcard`,
SAF/document roots, app-private roots, user-authorized roots, and Android
shared storage remain unavailable until an explicit Pro/Desktop root provider,
grant lifecycle, packaged smoke, and per-root symlink/realpath audit land.

P13-33 records desktop fs advanced APIs as partial: scoped streams,
`FileHandle`, fd APIs, `opendir`, watchers, and `realpath` are present enough
for current Node Profile v1.1 evidence, but remain scoped, execution-owned, and
Android-platform-limited. Pro scripts that expect broad filesystem roots, raw
native fds, recursive watchers, or desktop-identical packaged watcher/fd
lifecycle behavior still need future profile promotion work.

P13-34 records `worker_threads` opt-in promotion as partial: Safe Node still
rejects worker threads by default, while `workerThreadsProfile` describes the
request-local experimental preflight, worker policy, bridge isolation,
MessageChannel/transfer-list/WorkerPool targets, and packaged worker state.
This is not yet Pro/desktop parity for worker scripts because native
availability, current experimental gate evidence, crash evidence, packaged
`.mjs` worker smoke, and Android resource ceilings remain open.

P13-35 records process-worker replacement as partial: Safe Node now exposes
`processWorkerReplacementProfile` diagnostics that choose
`worker_threads.WorkerPool` as the current replacement path without introducing
a `process_worker` or `compute_worker` facade. `child_process` remains denied,
`shell` remains the scoped command bridge, and `worker_computation` plus
`second_execution_slot` stay unpromoted until future evidence closes those
gates. For v1.1 there is no user-facing process-worker API or UX; any scoped
`child_process` runner remains a separate P13-40 track.

P13-40 records child_process scoped runner as partial: Safe Node now exposes
`childProcessProfile` diagnostics while `child_process` and
`node:child_process` stay denied. The current migration path remains the
separate `shell` bridge; a real Desktop scoped runner still needs app-private
allowlisted binaries, bounded stdio, timeout/cancel/kill/cleanup behavior,
packaged evidence, and hostile corpus coverage.

P13-41 records ESM loader parity as partial: Safe Node now exposes
`esmLoaderProfile` diagnostics for the AutoJs6-managed partial ESM loader.
`.mjs`, `type=module`, TLA, local static/dynamic import, package
`exports`/`imports`, JSON import attributes, controlled JavaScript/JSON
`data:` URL imports, scoped `import.meta`, and CJS interop have focused
evidence, but raw loader hooks, network/file/unrestricted URL imports, custom
conditions/import maps, broad packaged encrypted graphs, and pending evaluation
cleanup remain outside Desktop parity.

P13-42 records Android package manager parity as partial: Safe Node now exposes
`packageManagerProfile` diagnostics and keeps the practical install path as
host-managed validation plus Android app-private local store substrate. This
narrows the gap for pure JavaScript dependencies that have already been
resolved and sanitized by host tooling, but it is not yet Pro/Desktop npm
parity. Android npm CLI, registry download/cache, remote tarball install,
lifecycle scripts, bin shims, symlinks, native builds, signed native payloads,
packaged dependency distribution, and real npm corpus growth remain open. The
script-visible `package_manager` facade is limited to status/policy/
sourcePolicy, dry-run planInstall/planUpdate/planRemove, policy-only
planRegistryInstall/planTarballInstall, guarded install/update/remove, and
list/verify/prune over the app-private store. Remote source plan calls return
`blocked_by_policy` without network/cache access or install authority; registry
plans can parse caller-provided npm `metadata`/`packument` as frozen
dist-tag/semver/integrity diagnostics, and tarball plans can parse
URL/name/version/sha512 shape locally. `npm` is script-visible only as a guarded
alias over that facade; CLI, registry install, lifecycle, publish, and pack
entry points remain stable-denied.

P13-43 records Node stdlib conformance as partial: Safe Node now exposes
`stdlibProfile` diagnostics for the representative stdlib subset covering
`crypto`, streams, zlib, Buffer, URL, OS, readline, TTY, `perf_hooks`, and
`async_hooks`. This narrows the gap for common desktop packages that depend on
low-risk builtins, and controlled `process.getBuiltinModule` now returns the
same limited wrappers as `require()`, but it is not yet full Pro/Desktop
stdlib parity. Broader module conformance, Android OS/terminal edge cases, raw
`process.getBuiltinModule` bypass behavior, `zlib/promises`, raw native
handles, unrestricted network behavior, and broader real npm pass-rate
dashboards remain open. The current stdlib corpus marker is
`real_npm_phase13_55_fixture_corpus`.

P13-44 records process/env/argv parity as partial: Safe Node now exposes
`processParityProfile` diagnostics for controlled `process.env`,
string-coercing script-local env writes, filtered sensitive env, scoped
cwd/chdir, argv metadata, `exitCode`, timing, resource snapshots,
permission/report facades, and bounded stdio. `process.exit(code)` maps to the
embedded-script lifecycle without
native process termination. Full desktop process parity still requires native
process-exit policy, Android signal policy, raw inherited env policy, native
binding decisions, raw process handles, and packaged lifecycle evidence.

P13-45 records packaged APK capability metadata as partial: packaged projects
can now expose diagnostic `packagedMetadata` for profile, builtin, native asset,
filesystem-root, execution-mode, network, and Android permission intent.
This narrows the gap toward Pro/Desktop packaged declarations, but the metadata
does not yet select a real profile, widen builtins, load native assets, expand
filesystem roots, or grant undeclared Android/bridge authority.

P13-46 records plugin system parity as partial: Safe Node now exposes
`pluginSystemProfile` diagnostics for the JS-only local plugin manager,
install/load/lifecycle operations, host capability ceiling, packaged local-file
status, and debug-only reload. This narrows the gap toward Pro/Desktop plugin
distribution, but Android provider plugins, native plugins, signed archives,
remote marketplace install/update, packaged plugin distribution, and
plugin-granted host authority remain unavailable.

P13-47 records dependency compatibility corpus as partial: the fixed real npm
corpus now carries `phase13Compatibility` package reason categories for
`missing_builtin`, `native_addon`, `postinstall`, `network`, and `fs`, and the
desktop differential report can show pass-rate rows by category. The current
corpus has 55 offline fixtures with a stdlib `targetedFixtureDeltas` +5
record. This improves Pro/Desktop dependency planning without executing unsafe
registry packages, native payloads, lifecycle scripts, or postinstall
downloads.

P13-48 records package lock and integrity policy as partial: AutoJs6 Node now
has a shared `NodePackageIntegrityPolicy` and runtime
`packageIntegrityProfile` for Android package manager, plugin packages, and
native addon artifacts. Android local-store rollback, stale transaction prune,
corruption detection, and reinstall/update recovery are covered, while signed
plugin archives and marketplace install/update remain future work. Signed
native artifact verification is delegated to the P13-39 packaged native path;
P13-48 keeps native addon integrity diagnostics fail-closed until that path
exists.

P13-49 records profile selection UX and diagnostics as partial: AutoJs6 Node
now exposes `profileSelection` diagnostics through `autojs6:profile` and Node
Doctor. Scripts can see requested Pro/Desktop/Debug metadata, declared
high-risk capabilities, blocked promotions, and actionable failure hints for
missing profile/capability/permission requirements. This improves migration
debuggability for Pro/Desktop parity without promoting any profile beyond the
current `safe_default` authority. Effective Pro/Desktop/Debug profile
switching stays stable-denied for P13-49 v1.1.

P13-50 records relaxed-capability security corpus closeout: AutoJs6 Node now
tracks raw network, filesystem relaxation, Java interop, `child_process`,
native addon loading, inspector, and WASI in
`RELAXED_CAPABILITY_SECURITY_CORPUS.json`, with positive tests, negative tests,
abuse cases, CI evidence, runtime guards, promotion preconditions, and rollback
posture. This creates the security evidence lane needed for Pro/Desktop parity,
but raw sockets, expanded roots, raw process/native handles, raw WASI,
inspector, native addons, and release Java interop remain unpromoted.

P13-51 records per-profile rollback playbook as partial: AutoJs6 Node now
documents default-off Pro/Desktop/Debug profile switches, relaxed-capability
rollback switches, Safe-profile crash/stress fallback, and packaged APK
downgrade actions through `NodeProfileRollbackPolicy`,
`autojs6:profile.profileRollbackPolicy`, and Node Doctor. This supports the
long-term Auto.js Pro plus desktop Node target without silently granting
authority; runtime rollback wiring and automatic crash circuit breaker
implementation are deferred until effective profile switching is promoted,
while settings UI, packaged builder UI, and privacy prompts remain future work.

P13-52 records privacy and disclosure policy as partial: AutoJs6 Node now
documents prompt channels, foreground disclosure requirements, Android
permission/settings metadata, and packaged denial behavior through
`NodePrivacyDisclosurePolicy`, `autojs6:profile.privacyDisclosurePolicy`, and
Node Doctor. This closes the diagnostic lane for screenshot, recording,
overlay, accessibility, network, filesystem relaxation, Java interop,
root/Shizuku shell, `child_process`, native addon, inspector, and WASI without
granting implicit high privilege; prompt UI, notification ownership, and
packaged disclosure review remain future work.

P13-53 records Pro parity examples as partial: AutoJs6 Node now includes
`sample/nodejs/pro-parity-suite`, an example catalog for `$autojs.java`,
`rhino.install`, UI, floaty/overlay, tasks, screenshot/OCR, and accessibility
migration. Each snippet records required capabilities, expected output,
packaged behavior, and a skip reason. The focused runtime smoke subset runs
Activity-owned UI layout and WorkManager one-shot schedule/cancel behavior,
while high-risk Pro snippets remain outside default runtime smoke and do not
grant Pro authority.

P13-54 records desktop Node parity examples as partial: AutoJs6 Node now
includes `sample/nodejs/desktop-parity-suite`, an example catalog for ESM,
`worker_threads`, HTTP/HTTPS, raw network, advanced fs, WASI, inspector, and
sanitized pure JavaScript npm dependency migration. Each snippet records
`desktop_compat_opt_in`, required capabilities, expected output, packaged
behavior, skip reason, and a Safe-profile denial marker. The focused runtime
smoke subset runs managed local ESM, the checked-in pure JavaScript dependency
path, and scoped advanced fs FileHandle/watch behavior, while high-risk desktop
snippets remain outside default runtime smoke and do not grant Desktop
authority.

P13-55 records TypeScript profile declarations as partial: AutoJs6 Node now
ships `profile_capabilities.d.ts`, a declaration-only Safe/Pro/Desktop/Debug
matrix for the P13-53 and P13-54 migration surfaces, including `raw_network`.
TypeScript scripts can use `ProfileModuleModeFor`, `ProfileRequiredFor`,
`SafeDefaultModeFor`, and `PackagedBehaviorFor` to document profile
requirements and Safe-profile denial behavior. The matrix is metadata-only and
does not declare denied desktop
modules as callable modules or grant Pro/Desktop authority.

P13-56 records migration and Pro9 comparison docs as partial: this document now
uses the same profile vocabulary as the Phase 13 surface manifest. The labels
are:

- Pro-compatible: Safe-profile or documented Pro-profile migration paths that
  help Auto.js Pro/Rhino authors rewrite scripts to explicit Node bridge modules
  or guarded Pro examples.
- Desktop-compatible: scoped desktop Node.js patterns such as local module
  loading, partial local ESM, scoped filesystem behavior, controlled HTTP/HTTPS,
  host-managed pure-JS dependencies, and the desktop example catalog.
- gated/diagnostic-only: debug profile rows, provider POCs, static examples,
  declaration-only profile metadata, or stable denials that are not promoted as
  normal script APIs.

debug-only/provider POC is not formal availability. `sample/nodejs/pro-parity-suite`
and `sample/nodejs/desktop-parity-suite` are migration evidence, while
`profile_capabilities.d.ts` is metadata-only. `$autojs.java`, Java/Rhino proxy
globals, raw floaty, unrestricted UI-thread execution, daily/weekly task rows,
raw network sockets, expanded filesystem roots, `worker_threads`, `vm`,
`inspector`, WASI, native addons, `child_process`, Android npm CLI, registry
install, lifecycle scripts, and native payloads remain gated unless a later
runtime promotion gate explicitly changes their status.

P13-36 records `vm` module promotion as partial: Safe Node now exposes
`vmProfile` diagnostics and routes `vm` / `node:vm` to Node's native builtin.
This improves Pro/desktop parity for packages that use `vm.Script` or contexts.
Focused P13-36 evidence now covers repeated compiled-script budgets, native
timeout interruption, bridge/process/global non-injection probes, private
binding boundaries, and packaged fixture behavior; broader duration and package
corpus expansion remain future hardening work.

P13-37 records inspector/devtools debug profile as partial: Safe Node now
exposes `inspectorProfile` diagnostics while keeping `inspector` and
`node:inspector` denied. This is not yet Pro/desktop parity for scripts that
expect DevTools attach, CPU profiles, heap snapshots, or debugger URLs;
explicit debug-session UI, loopback ADB forwarding, attach lifecycle, profiler
artifact redaction, app-private export, and packaged release/debug behavior
remain open.

P13-38 records controlled WASI profile as partial: Safe Node now exposes
`wasiProfile` diagnostics while keeping raw `wasi` and `node:wasi` denied.
Pure WebAssembly remains available, but this is not yet Pro/desktop parity for
WASI-oriented packages. For v1.1, pure WebAssembly is the supported runtime
path and raw/controlled WASI APIs stay stable-denied; scoped or
user-authorized preopens, virtual fd tables,
explicit args/env policy, limited clock/random, network/process-exit denial,
resource budgets, worker integration, packaged `.wasm`/WASI behavior, and
hostile corpus coverage remain open.

但它仍是 Safe Node Profile，而不是 Pro v9 或桌面 Node.js 的无边界复刻。
对脚本开发者最重要的 Rhino 能力迁移已经有 `autojs6:compat` 这条显式路径，
并且 Phase 11A 已经完成 inventory、状态矩阵、低风险 facade、自动化核心 facade、
示例、双引擎测试和 packaged smoke 的基础建设。下一步的重点不是简单“打开所有全局”，
而是继续把 Rhino 主要 API 逐项映射为安全、显式、可测试、可打包的 Node
bridge/facade，并对无法安全迁移的 API 给出稳定拒绝和清晰替代路径。
