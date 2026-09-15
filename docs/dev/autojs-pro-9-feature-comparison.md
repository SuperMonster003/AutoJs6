# AutoJs6 6.8.0 与 Auto.js Pro 9.3.11 功能差异对比

盘点日期: 2026-09-14.

## 0. 基线与资料来源

| 项目 | AutoJs6 | Auto.js Pro |
| --- | --- | --- |
| 版本 | 6.8.0 (build 5280), 2026-09-14 | Pro 9.3.11-0 (versionCode 9131100), 2023-02-09 |
| 资料 | 工作区 `D:\idea-projects\AutoJs6`, 声明文件仓库 (72 个 d.ts, 56 601 行), 文档仓库, 43 个官方插件仓库, 官方插件索引 (31 条) | `T:\backups\idea-projects\AutoJsPro`: 脱壳 APK, CFR / Procyon 反编译, smali 树, 官方离线文档 (2023-02-12 快照), VS Code 扩展 9.0.9 与 1.3.2 |
| SDK | minSdk 24, targetSdk 36 (inrt 29), compileSdk 37 | minSdk 21, targetSdk 30, compileSdk 33 |
| ABI | arm64-v8a, armeabi-v7a, x86_64, x86 (+ universal), 16 KB 页对齐 | arm64-v8a, armeabi-v7a |
| 许可 | 开源 (MPL 2.0), GitHub 更新 | 商业闭源, 账号 + 付费 (支付宝 / 微信 / Google Play / 华为 IAP), 360 加固 + StringFog + QVM |

两点注意:

- Pro 经过加固与字符串加密, 端口与 URL 等信息来自 Flutter AOT 快照与 VS Code 扩展, 而非 DEX.
- Pro 官方文档 "指南" 页写有 "Auto.js Pro 移除了无障碍等自动化模块", 但 9.3.11 APK 实际包含无障碍服务, 选择器, 录制回放, RootAutomator 等全部自动化模块. 该措辞疑为商店版本或法务口径, 下文以 APK 实物为准.

## 1. 一页总览

| 维度 | Auto.js Pro 9.3.11 | AutoJs6 6.8.0 |
| --- | --- | --- |
| 脚本引擎 | Rhino 1.7.14 (第一代 API) + 内嵌 Node.js 16 / V8 (第二代 API, `libnode.so` 48.8 MB) | Rhino 2.0.0-SNAPSHOT 分支 (内置) + 外部插件引擎: Node.js 24.5, Bun 1.4, QuickJS, Python 3.13, Lua 5.4, Java / Kotlin, TypeScript 编译器 |
| API 形态 | 第一代: 全局对象; 第二代: `require('模块')`, Promise 异步 | 单一代 API, 全局对象 + `$` 别名, 同步为主并配 `*Async` / `flow` / `continuation` |
| UI 壳 | Flutter (文件管理 / 任务 / 打包 / 设置 / 账号) + 原生编辑器与悬浮工具 | 全原生 Android (Material), 10 种语言, 主题色系统 |
| 编辑器 | Ace 1.4.12 + Sourcegraph TypeScript 语言服务器 (TS 4.2, WebSocket), 多标签, vim / emacs / sublime / vscode 键位, 断点调试, 可视化 UI 设计器 (实验) | 原生编辑器 + Ace 插件 (热切换), 本地 TypeScript IntelliSense (补全 / 签名 / 悬停 / 自动导入 / 重命名 / 跨文件诊断), 断点调试, 版本历史, 编码转换, 单文件窗口 |
| 打包 | 原生 C++ 构建器, 加密等级 0 / 1 / 2 (DEX) / 3 (V8 快照) / 100 (在线), 组件混淆, 插件合并, 发布到代码商店 | Kotlin 构建器 (已迁入 APK Builder Template 插件), AES 脚本加密, TS 编译打包, 12 种库可选打包, V1 / V2 / V3 签名, 构建配置档案, 远程构建 (实验) |
| 远程开发 | JSON-RPC over WebSocket, 授权指纹, 布局分析, 设备文件浏览, rsync 同步, 远程终端, FTP, Node Inspector 单步调试 | JSON socket (客户端 6347 / 服务端 7347), 运行 / 停止 / 保存 / 项目; 另有 MCP Server 插件面向 AI 代理 |
| 终端 | Termux 终端 + node / npm / npx / yarn / pnpm / corepack | 无终端界面, 无 npm |
| OCR / 视觉 | PaddleOCR (官方插件, 内置 `ocr` 模块) + ML Kit 插件; OpenCV 4.5.1 全量 JS 绑定 (`@autojs/opencv`, 含 DNN / SVM / 人脸 / 跟踪器) | ML Kit / Paddle v3-v6 / Rapid 三系引擎自动选择; OpenCV 4.8.0 插件; 条码 13 种码制; YOLO; 本地 LLM (`ai`); 图像相似度 / 量化 / 特征匹配 |
| 商业与生态 | 账号, 付费, 插件商店, 代码商店, Bugly / Flurry 遥测 | 无账号; 插件中心 (GitHub 索引, 签名信任, ABI / 16 KB 兼容提示), 30 个冻结插件契约 |

## 2. 引擎与语言运行时

### 2.1 引擎矩阵

| 引擎 | Pro 9.3.11 | AutoJs6 6.8.0 |
| --- | --- | --- |
| Rhino | 1.7.14, `optimizeLevel` 可设, 协程需 `useFeatures: ["continuation"]` | 2.0.0-SNAPSHOT 分支, 解释模式 (-1) 以支持续体, 协程默认可用 |
| Node.js | 内嵌进程内 (PlutoJS, libuv 挂到 ALooper), Node 16, `@types/node` 14, ESM loader, `"nodejs";` / `.node.js` / `.mjs` / `project.json type=node`; 打包 APK 可携带 (`excluded-zip-files.json` 的 `v8` 组) | 独立插件 APK, 外部进程 + Binder 会话, Node 24.5.0, 3 个 ABI; 入口 `"nodejs";` / `"node";` / `.node.js` / `.cjs` / `.mjs` / `.ts` / `.mts` / `.cts`; 常驻进程复用, 双运行时并发, stdin / readline, 仅调试用 Inspector / CDP; 打包 APK 不支持外部 Node |
| Bun | 无 | 插件, Bun 1.4.0, `"bun";` / `.bun.js` / `.bun.ts` / `.bun.tsx`, 单文件, 无 AutoJs6 全局 |
| QuickJS | 无 | 插件, ES module + 顶层 await + BigInt, 仅 `console` / `print` |
| Python | 无 | 插件, CPython 3.13.9 (Chaquopy 17), 项目快照 ≤ 16 MiB, `from autojs6 import app, device, execution, project` (只读快照), 长任务前台服务 |
| Lua | 无 | 插件, PUC Lua 5.4.8, `:lua_runtime` 进程, 宿主 storage / toast / device 代理 |
| Java / Kotlin | 无 | 插件 (实验), ECJ / Kotlin 2.3 K2 + D8 |
| TypeScript | 不在运行时转译, 仅编辑器类型与 LSP; 终端可 `npm i -g typescript` 手动编译 | 编译器插件: `.ts` / `.tsx` 单文件与项目, Source Map v3 栈映射, 打包时编译, 定时任务 / 快捷方式 / Tasker 共用同一准备流水线 |
| `.auto` 回放 | RootAutomatorEngine (`libroot_automator.so`, v1 / v2) | RootAutomatorEngine |
| 失败策略 | Node 不可用时回退 Rhino | 插件缺失 / 未授权 / ABI 不符时明确失败, 不回退 |

### 2.2 语言特性 (Rhino 侧)

| 特性 | Pro (Rhino 1.7.14) | AutoJs6 (Rhino 2.0.0-SNAPSHOT) |
| --- | --- | --- |
| ES5 + `let` / `const` / 箭头函数 / 模板字符串 / 解构 / 展开 / 默认参数 / 生成器 | 部分 ES6 | 支持 |
| `Promise` / `Symbol` / `Map` / `Set` / `Proxy` / TypedArray | 部分 | 支持 |
| `BigInt`, `Object.values`, `Array.prototype.includes`, 星光面 `\u{...}` | 无 | 支持 |
| `class` 语法 | 无 | 无 (分支未实现 CLASS 词法) |
| `async` / `await` | 无 (协程 `Promise.prototype.await()` 替代) | 无 (`continuation.await` / `Promise.prototype.wait()` / `flow` 替代) |
| ES module `import` / `export` | 无 (Node 侧有) | 无 (Node / Bun / QuickJS 插件有) |
| E4X | 有 | 有 (已标注弃用) |
| 续体 / 协程 | 项目级开关 | 默认开启, `continuation.create / await / delay` |

### 2.3 Java 互操作

| 能力 | Pro | AutoJs6 |
| --- | --- | --- |
| Rhino LiveConnect (`Packages`, `importClass`, `JavaAdapter`) | 有 (第一代) | 有 |
| Node 侧 Java 访问 | `$autojs.java` / `$java.findClass`, `rhino.install()` 得到 `java.*` / `android.*` (无 `importClass` / `JavaAdapter`) | Node 插件默认不暴露 Java / Android 对象 (安全模型), `require("autojs6:compat")` 提供受控门面 |
| 运行时子类化 | `$java.defineClass(class X extends android.widget.Button {...})` 运行时生成 DEX 并缓存, 生成类可直接写在布局 XML 中 | Rhino `JavaAdapter`; 无运行时 DEX 生成 |
| 线程亲和 | `ThreadMode = ui / current / compute / io`, `method.invoke(target, args, mode)`, 跨线程调用自动返回 Promise, 显式装箱 `boxInt` 等 | `threads` / `ui.run` / `ui.post`, `key_rhino_java_primitive_wrap` 开关 |
| 加载字节码 | `runtime.loadJar / loadDex`, `$java.loadDex / loadJar` | `runtime.loadJar / loadDex / loadAar / loadJarWithR8 / retraceR8Stack` (D8 / R8 编译器插件) |

### 2.4 线程与异步模型

- Pro: Rhino `threads` 共享运行时对象; Node 单线程事件循环, `worker_threads` 子线程不能访问 `$autojs`; Rhino 与 Node 引擎可互相 `execScriptFile` 并通过 `engine.emit` / `broadcast` 传 JSON.
- AutoJs6: `threads.start / pool / lock / atomic / disposable`, `Promise.prototype.wait()`, `continuation`, `flow` 链式异步 (`all / race / any / timeout / retry / scope / cancelAll`), `timers.setIntervalExt / keepAlive`; Node 插件通过 `autojs6:host` 消息与宿主通信.

## 3. 脚本 API 逐模块对比

约定: "Pro 一代" 指 Rhino 全局 API (官方 v8 文档 1169 条标题), "Pro 二代" 指 Node 内置模块 (31 个, 15 347 行, 见 `assets/v8/built_in_modules`).

| 模块 | Pro 一代 | Pro 二代 (Node) | AutoJs6 | 差异要点 |
| --- | --- | --- | --- | --- |
| 全局函数 | `sleep / setClip / getClip / toast / toastLog / exit / random / requiresApi / requiresAutojsVersion / runtime.loadJar / loadDex / requestPermissions / context` | 无全局 (需 `require`), `$autojs.keepRunning / requireAutoJsVersion / properties` | 一代全部 + `toastVerbose / Info / Warn / Error`, `wait / wait.async`, `currentComponent`, `setScaleBase* / cX / cY / cXy / cYx`, `isNullish / isJavaObject / isJavaClass` 等类型判定, `species`, `isAutoJs6` | AutoJs6 保留全局风格并扩展; Pro 二代改为模块化 |
| `app` | 26 项 (launch / intent / broadcast / service / email / installed apps / openAppSetting ...) | `makeIntent / registerBroadcastReceiver / getApkInfo / getInstalledPackages` 等 24 项 | 一代全部 + `*Dual` 双开 / 工作资料系列 (`launchDual / uninstallDual / isDualInstalled / killDual / openDualUrl / startDualActivity`), `kill`, `isInstalled`, `getApkInfo`, `sendLocalBroadcastSync`, intent 选项 `root / dual / shizuku` | AutoJs6 独有双开 API; Pro 独有动态广播接收器对象 |
| `device` | 47 项 | `Device` 类 + `OS` + `battery-changed` 事件 | 一代全部 + `pageSize`, `density`, `manufacturer`, `summary / digest`, IP / IPv6 / 网关 / Wi-Fi 状态, 指针位置开关, `getSharedDeviceId` | 基本对等, AutoJs6 网络与显示信息更多 |
| `files` | 41 项 | 走 Node `fs` | 一代全部 + `readAssets`, `getHumanReadableSize`, `getSimplifiedPath`, `createIfNotExists`, `toFile` | AutoJs6 Rhino 侧另有 `fs / path / os / process / Buffer` Node 兼容垫片 (仅同步 fs) |
| `http` / WebSocket | `get / post / postJson / postMultipart / request`; `$web.newWebSocket` (okhttp) | Node `http / https / net / tls` + `axios 0.21.4` | OkHttp: 同步 + `*Async` 成对, `put / delete / head`, `BodyWriter`, `isInsecure`, 重试, body 缓存; `web.newWebSocket` 含重建退避与完整关闭码; 内置 `axios`, `cheerio` | Pro 二代可起 HTTP 服务器 (Node); AutoJs6 Rhino 侧无服务器 |
| `crypto` / `base64` | digest / encrypt / decrypt / generateKeyPair | Node `crypto` | 摘要 MD5 / SHA-1..512, 对称与非对称, 密钥对含 X25519 / X448 / EC / RSASSA-PSS, 文件模式 | 对等偏 AutoJs6 |
| `zip` | zip4j 21 项 | `zip.js` zip4j, AES-256 | zip4j, AES-128 / 192 / 256, 符号链接策略 | 对等 |
| 本地存储 | `storages` (5 项) | `datastore` (Jetpack DataStore, 可 AES 加密, 事务 `edit`) | `storages` 同步 + 异步变体, `all / names / selfRemove`; `sqlite` 完整包装 (`open / query / rawQuery / WrappedCursor`) | Pro 独有加密 DataStore; AutoJs6 独有 `sqlite` 模块 (Pro 样例用 Java SQLite) |
| `threads` / `timers` / `engines` | threads 21 项, timers 7 项, engines 22 项 | `engines` Promise 化, `ScriptExecution` 事件 `start / success / exception`, 跨引擎 `emit / broadcast` | threads + `pool / allThreads / hasRunningThreads`; timers + `setIntervalExt / keepAlive`; engines 同一代 + `execScript` 内存 TS | Pro 独有 Rhino 与 Node 互启互通 |
| `events` | emitter / broadcast / key / touch / notification / toast | `accessibility` 事件 (`key_event / key_<NAME> / toast / window_state_changed` 等全部 AccessibilityEvent 类型), `clip_changed` | 一代全部 + `observeToast / observeNotification` 移除, `setKeyInterceptionEnabled`, `emitSticky`, `__asEmitter__`, `auto.registerEvent` (distinct / within / debounce) | Pro 二代事件名更细; AutoJs6 有按键拦截与事件去抖 |
| `sensors` | register / unregister | `getSensor(name)` 任意 `TYPE_*` | 11 种类型化传感器 + `ignoresUnsupportedSensor` | 对等 |
| `media` | 10 项音乐播放 | `MediaPlayer` 类 + `scanFile` | 一代全部, 基于 Media3 前台服务 (音频焦点 + 通知控制); `mediainfo` 插件读容器 / 轨道信息 | AutoJs6 播放更完整 |
| 通知 | 无 (第一代仅事件) | `notify(id, options)` 带 JS `onClick / onDelete`, `requestListeningNotifications` | `notice` 模块: 渠道管理, 预设, 优先级, 大文本, intent; 通知监听 | 对等 |
| `dialogs` | 27 项 | `showXxxDialog` Promise 化, `overlay / app` 两种类型 | 一代全部 + `customView`, `linkify`, `onBackHandler`, UI 模式返回 Promise | 对等 |
| `floaty` | window / rawWindow 26 项 | `createWindow`, 标志常量, `enableDrag(keepToEdge: bounce)`, `touch` 事件 | 一代全部 + `hasPermission / ensurePermission / getClip`, `requestFocus / disableFocus` | 对等 |
| `console` | 19 项 | Node `console` + `setLogFilePath` | 一代全部 + `input / rawInput` 交互, 分级颜色, 时间格式, 标题 / 内容透明度, 顺序无关链式配置, `<console>` / `<globalconsole>` 控件 | AutoJs6 明显更多 |
| `ui` | 见第 5 节 | 见第 5 节 | 见第 5 节 | |
| `images` / `colors` | images 85 项, colors 21 项 | `image.js` 1547 行 + `color.js` (Color / HSV / HSL 类) + `@autojs/opencv` | 见第 4 节; `colors` 6646 行声明, web / css / material / android 四套色表, HSV / HSL 全套转换 | 见第 4 节 |
| `ocr` / 条码 | `$ocr.create` Paddle (插件) + `$plugins.load` ML Kit | `createOCR` (Paddle-Lite 参数 `cpuPowerMode / useOpenCL / models`), ML Kit 插件 | `ocr.mlkit / paddle / rapid`, 自动选择, `ocr.mode / tap`; `barcode / qrcode` 13 种码制 | AutoJs6 独有条码与多引擎 |
| `shell` / 特权 | `shell(cmd, root)`, `Shell` 类, 附 am / pm 命令附录 | `exec / createShell / checkAccess("root"|"adb")`, `ShizukuShell`, `Tap / Swipe / SendKey / InputText` | `shell`, `shizuku` (含 `state` 探针), `RootAutomator`, `app.startActivity({shizuku})` | 对等, 两者均 Shizuku |
| 定时任务 | `$work_manager` 10 项 (daily / weekly / disposable / intent) | `work_manager.js` + `addOneTimeTask / addBroadcastIntentTask / addActivityIntentTask` | `tasks` (+ monthly / yearly / countdown, `updateTask`, 异步回调) 与 Pro 兼容子集 `workManager` | Pro 独有 activity intent task; AutoJs6 独有月 / 年 / 倒计时 |
| `settings` / `power_manager` | `$settings.setEnabled / isEnabled`; 电池优化 2 项 | 5 个偏好项对象; `newWakeLock / wakeUp / isScreenOn` | `settings.isEnabled / setEnabled`; `powerManager` 2 项 | 对等 |
| `plugins` | `$plugins.load(pkg)` 独立 APK 插件 (SDK `org.autojs.plugin.sdk`), 打包可合并 | `plugins.load / unload / unloadAll` | `plugins.load(pkg)` 兼容旧 SDK 注册表 (`com.stardust.*` 兼容包 354 文件), 项目内 `plugins/*.js`, `plugins.extend*` | AutoJs6 另有 30 个 AIDL 契约的官方插件体系 |
| `util` | 23 项 | `java_utils`, `lang` (`lazy / Deferred / delay / TimeoutError`) | 类型判定 20 余项, `util.java`, `util.morseCode`, `util.versionCodes`, `util.inspect` | 对等偏 AutoJs6 |
| 文本 / 本地化 | 无 | 无 | `pinyin`, `pinyin4j`, `opencc` (14 条转换路线), `pangu`, `i18n` (banana-i18n), `nanoid`, `cvt / fmt`, `s13n`, `jsox` (`Mathx / Numberx / Arrayx`), `sysprops`, `mime`, `recorder`, `tts` (30 余项), `ai` (`ask / chat / stream / session / catalog`), `yolo`, `mediainfo`, `autojs` (版本 / 权限探针 / IPC `send / invoke / handle`) | 全部为 AutoJs6 独有 |
| Pro 二代独有 | | `handler_thread`, `media_projection` (`ScreenCapturer.nextImage / latestImage`, 方向感知虚拟显示), `clip_manager` (`clip_changed` 事件), `lazy_ui_object`, `rhino` 兼容层, `@autojs/opencv`, `convert` (空文件) | | |

## 4. 选择器, 自动化与视觉

### 4.1 选择器

| 项 | Pro 一代 | Pro 二代 | AutoJs6 |
| --- | --- | --- | --- |
| 形态 | 链式 `text().desc().clickable()`, 49 项 | `select({ className: 'Button', text: /OK/, childCount: { '>=': 1 } })` 查询对象, 45 个节点属性 + 运算符对象, `where / from / atMost / timeout / maxRetries / iterator` | 链式 + 对象映射 + 字符串 DSL `select(syntax)`; 注册表约 60 个基础准则, 每个文本类准则派生 `Contains / StartsWith / EndsWith / Matches / Match` |
| 几何准则 | `bounds / boundsInside / boundsContains` | `boundsInScreen` | `left / top / right / bottom / width / height / centerX / centerY` 及 `min* / max*` 矩阵, `screenCenterX / Y`, `screenCoverage` |
| 树 / 表格 | `drawingOrder` | `depth / indexInParent / drawingOrder / childCount` | `depth / indexInParent / drawingOrder / childCount / minChildCount / maxChildCount / hasChildren / row / column / rowSpan / columnSpan` |
| 查询 | `findOne / findOnce / find / untilFind / exists / waitFor` | `first / last / takeAt / all / iterator` | 一代全部 + `findIterator()` 惰性迭代, `untilFindOne`, `performAction` |
| 相对定位 | 无 | 无 | `pickup / detect` + 罗盘 DSL (`p2c3>1`, `s<1`, `k4`), 12 种结果类型 |
| 诊断 | 无 | 无 | `auto.explain(selector)` 逐过滤器命中计数, `auto.dump({format: text/json/xml})`, `auto.stats`, `UiObject.dumpSubtree` |
| 节点动作 | 40 项 | 29 项 (含 `pageUp / Down / Left / Right`, `contextClick`, `showOnScreen`, `scrollToPosition`) | 约 45 项 + `clickBounds`, `imeEnter`, `dragStart / dragDrop / dragCancel`, `moveWindow`, `setProgress`, `pressAndHold`, `showTooltip`, `nextAtMovementGranularity`, `nextHtmlElement` |
| 节点扩展 | `Rect` | `tree()`, `refresh()` | `waitForStable / waitUntilGone / snapshot / fingerprint / isSimilar / isShifted / isStale / summary / toJSON` |
| 服务控制 | `auto.waitFor / setMode / setFlags / setWindowFilter` | `accessibility.enableService / goToSettings / enableKeyEvents / hasSecureSettingsPermission` | `auto.start / stop / restart`, `setFlags` (5 种), `findWindows / findWindowRoots`, `registerEvent`, `launchSettings` |
| 工具集 | 无 | 无 | `automator.smartClick / clickIfExists / clickAny / findAny / scrollUntil / typeInto / dismissPopups (PopupGuard) / collectList / launchAndWait / backUntil / backToApp / toggle / retry / waitForIdle / waitForEvent / waitForToast / waitForNotification`, 支持 `humanize` 随机抖动; `flow` 链式异步 |

### 4.2 手势与全局动作

两者均有 `click / longClick / press / swipe / gesture(s)`, `back / home / recents / notifications / quickSettings / powerDialog / splitScreen / lockScreen / takeScreenshot / headsetHook / accessibilityButton / accessibilityShortcut / switchToInputMethod`. Pro 二代额外有 `StrokeDescription` 多指构造器与 `showLauncherAllApps`; AutoJs6 额外有 `dismissNotificationShade`, `accessibilityAllApps`, `accessibilityButtonChooser`, `gesturesAsync`.

RootAutomator: Pro 有 v1 (裸 `/dev/input` 多点触控协议) 与 v2 (`libroot_automator.so`, 批量 `exec / flush`); AutoJs6 为 v1 风格 (`tap / touchDown / touchMove / touchUp / sendEvent / setDefaultId`).

### 4.3 图像与视觉

| 能力 | Pro | AutoJs6 |
| --- | --- | --- |
| 截图 | MediaProjection + 无障碍截图 (Android 11+) | 同; 可配置权限请求延时, 异步请求 |
| 找色 | `findColor / findColorInRegion / findAllPointsForColor / findColorEquals / findMultiColors / detectsColor / detectsMultiColors`, `ColorMapping` 单例 | 同 + `findPointByColor(s) / findPointsByColor(s)`, 向量化 OpenCV 实现, `countPointsByColor`, `getMeanColor` |
| 找图 | `findImage / findImageInRegion / matchTemplate` 金字塔加速; `matchTemplate({useTransparentMask})` 直接抛 "not supported" | `findImage / matchTemplate` 多尺度 `scales`, 透明模板掩码匹配 (OpenCV ≥ 4.5.2 masked matching) |
| 特征匹配 | `detectAndComputeFeatures / matchFeatures` SIFT / ORB + FLANN / BruteForce | 同 |
| 相似度 | `getSimilarity(img1, img2, options)` | `psnr / ssim / mssim / hist / mse / ncc / getSimilarity({metric}) / isEqual` |
| OpenCV 暴露 | 一代: `cvtColor / inRange / interval / blur / medianBlur / gaussianBlur / threshold / adaptiveThreshold / findCircles / matToImage`; 二代: `require('@autojs/opencv')` = opencv4nodejs 5.6.14 全量 (Mat / DNN Net / SVM / Eigen-Fisher-LBPH 人脸 / Facemark / 8 种 Tracker / 背景减除 / 超像素 / 26 个 Haar-LBP 级联) | Java 绑定 OpenCV 4.8.0 内置 (`org.opencv.*` 经 LiveConnect 直接可用), 原生 `.so` 由 OpenCV 插件提供; 包装函数 `bilateralFilter / flip / invert / isGrayscale / downsample / concat` 等 |
| 量化 / 压缩 | 无 | `quantize / quantizeToFile / compress / compressToBytes` (libimagequant) |
| OCR | Paddle-Lite (官方插件, `models: default / slim`, `cpuPowerMode`, `useOpenCL`, `detectRotation`, 结果可 `click()`), ML Kit 插件 | ML Kit / Paddle PP-OCRv3-v6 (mobile / server / 多语言) / Rapid v3-v6 共 25 个引擎变体, 自动选择与显式模式 |
| 条码 / 目标检测 / LLM | 无 | `barcode` (ML Kit), `yolo` (NCNN), `ai` (LiteRT-LM 本地推理, CPU / GPU / NPU) |
| 画布 | `canvas` 31 项 | `Canvas` / `Paint` 全局类, 画布样例 |

## 5. UI 系统

| 项 | Pro 一代 (Rhino `ui`) | Pro 二代 (Node `ui`) | AutoJs6 |
| --- | --- | --- | --- |
| 布局语法 | XML / E4X | XML 字符串 `inflateXml`, JS 类 `extends ui.Activity` (真实 Activity 子类) | E4X + JSX / TSX + Android 原生 XML (`ui.useAndroidLayout`, `ui.layoutFile`, 项目 `res/`) |
| 控件 | 约 30 个 (text / button / input / img / vertical / horizontal / linear / frame / relative / checkbox / radio / switch / progressbar / seekbar / spinner / timePicker / datePicker / fab / toolbar / card / drawer / list / tab / console / globalconsole ...) | 同 + `RecyclerView` 适配器 (`item_created / bind_item`), 15 个钩子的 inflater 流水线, `Widget` 注册表, `R` 资源代理, `imageLoader` | 109 个 inflater, 51 个 `Js*` 控件 (含 `JsSearchView / JsRatingBar / JsChronometer / JsNumberPicker / JsVideoView / JsWebView / JsTabLayout / JsViewPager / JsConsoleView / JsGlobalConsoleView`), androidx / material 短名映射 (`CardView / RecyclerView / MaterialButton / Chip / TabLayout / TextInputLayout ...`), `<include> / <merge> / theme / style` |
| 自定义控件 | `ui.registerWidget` | `Widget.register`, JS 类可作为布局标签 | `ui.registerWidget` + `ui.Widget.defineAttr` |
| Web 界面 | WebView | `ui/web.js` + `autojs.sdk.v1.js` 页面侧 SDK (`$autojs.invoke / handle / send`), React / Vue2 + Vant / Vue3 CLI 完整样例 | `ui-ext.js` 同协议 `JsBridge` (`$autojs:internal:request / response`), Vue2 + Vant SFC 与可交互 HTML 样例 |
| 原生资源编译 | 无 | `androidResources` + 内置 `libaapt2.so` 真实编译 `res/` | 项目 `res/` 目录由宿主解析 (无 aapt2) |
| 状态栏 / 导航栏 | `statusBarColor` | `initialStatusBar` | `statusBarColor / statusBarIconLight / navigationBarColor / navigationBarIconLight`, 高度查询 |
| 可视化设计器 | 实验性 `VisualEditorActivity` (设计 / 代码 / 预览 / 属性 / 事件) | | 无 (仅 `docs/dev/visual-editor-ace-investigation.md`) |
| 生命周期事件 | `ui.emitter` (`back_pressed` 等) | `activityLifecycle` 8 个事件 | `ui.emitter` |

## 6. 应用层功能

### 6.1 IDE / 编辑器

| 项 | Pro | AutoJs6 |
| --- | --- | --- |
| 编辑器内核 | Ace 1.4.12 (WebView) + 旧原生编辑器切换 | 原生编辑器 + Ace 插件 (签名 / 契约 / ABI 校验, 运行时热切换) |
| 多标签 | 有 (关闭全部 / 其他 / 右侧) | 无, 每文件一个编辑窗口 (多进程) |
| 键位方案 | vim / emacs / sublime / vscode | Ace 快捷面板, 动作历史与收藏 |
| 智能补全 | Sourcegraph `javascript-typescript-langserver` (TS 4.2) 经 `ws://localhost:37245`, 自定义 `lib.autojs8 / autojs9` 库, 备用索引 `all.json` + 3991 个 Android 类索引 | 本地 TypeScript IntelliSense (补全 / 签名帮助 / 悬停 / 自动导入 / 拼写修正 / 跳转定义 / 项目级重命名 / 跨文件诊断), 可选声明组与语义语言矩阵; Java 类搜索对话框 |
| 调试 | Rhino 断点 / 单步 / 监视 / 调试栏; Node 脚本走 VS Code Inspector (9229) | Rhino 断点 / 启动调试器 / 表达式求值 / 监视 (`ui/edit/debug`, `DebugToolbarFragment`) |
| 格式化 / 检查 | beautify.js (长按 Tab), JS lint | js-beautify, TypeScript 诊断 |
| 其他 | 主题 dark_plus / light_plus, 字号 8-56, gutter / 不可见字符 / 打印边距 / 软换行, 选区放大镜 (实验), 自定义符号栏, 预执行脚本 | 主题明暗独立 + 预览, 字体下载 (签名校验), 连字, 缩进参考线, 换行缩进风格, 捏合缩放, 沉浸全屏, 编码检测 / 转换 / BOM, 版本历史, 草稿紧急保存, 大文件模式, 底部日志面板 (栈帧跳转 + 运行按钮), 符号键盘栏 |
| 可视化 UI 设计 | 实验性 (浩然贡献) | 无 |

### 6.2 文件管理

| 项 | Pro (Flutter) | AutoJs6 |
| --- | --- | --- |
| 基础操作 | 新建 / 重命名 / 复制 / 剪切 / 删除 (多选) / 移动 / 解压 / zip 导入 / 下载 | 新建目录 / 文件 / 导入 / 新建项目, 移动到 / 复制到 / 重命名 / 删除 / 发送 / 重置为初始内容, 多选批量 5 种动作 |
| 搜索排序 | 关键词 / 递归 / 正则, 名称 / 日期 / 大小 / 类型 | 全局搜索, 名称 / 日期 / 大小 / 类型, 隐藏文件与扩展名过滤 |
| 预览 | 无 (XML 布局预览) | Markdown (GFM / 主题 / 自定义 CSS), HTML, 图片查看器 (同目录遍历 / 缩放), 音视频与播放列表, EXIF / MediaInfo |
| 压缩包 | 解压 | Explorer Action 插件协议 v22: 浏览 / 解压 / 创建 / 修改 / 多卷 / 密码恢复 / 包内单文件运行 |
| APK | 无 | APK / APKS / XAPK / APKM / APKZ / AAB 检查, 清单查看, 分包安装器 (证书一致性检查), 注册为系统安装器 |
| 其他 | 脚本目录可改 (`~` 为私有目录), 定位文件, 复制路径 | 固定路径栏, 回收站与版本历史 (配额), 差量刷新, 分屏状态保持 |

### 6.3 项目与打包

| 项 | Pro | AutoJs6 |
| --- | --- | --- |
| 构建实现 | 原生 C++ (`libautojspro.so` `nativeBuild / nativeOptimize`), `template.apk` 6.4 MB, `libaapt2.so`, `android.jar` 26 MB | Kotlin (`android-axml` / `android-arsc-editor` / `apk-parser` / `apk-signer` 模块), 6.8.0 起整体迁入 APK Builder Template 插件 (SHA-256 证书固定, Runtime Kit 溯源) |
| 项目模板 | Empty / Counter / Basic UI / Bottom Nav / Android UI (empty, drawer) / Node.js / Node.js + Vue / 自定义 (目录或 zip) | 新建项目向导 (`ProjectConfigActivity`), Node 插件项目向导 |
| `project.json` | 21 个顶层字段: `type`, `abis`, `assets`, `useFeatures`, `scripts` (8 个 build hook), `encryptLevel`, `launchConfig` (splash / hideLogs / splashText / splashIcon / splashLayoutXml / stableMode), `optimization` (removeOpenCv / unusedResources / removeAccessibilityService / obfuscateComponents), `androidResources`, `permissionConfig`, `publish`, `signingConfig`, `features` (builtInOCR / activityIntentTasks / nodejs), `plugins` | `name / versionName / versionCode / packageName / main / type / assets / excludeDirs / icon / abis / libs / permissions / signatureScheme / useFeatures / node / launchConfig / build`; 模糊反序列化兼容 AutoJs4 / AutoX / Pro 键名 |
| 脚本保护 | 等级 0 无 / 1 简单离线 / 2 DEX 编译 / 3 V8 快照 / 100 在线 (服务端, 源码压缩 ≤ 1 MB, 需购买) | AES 加密 + 解密引擎; TypeScript 打包时编译 (仅含加密 JS, 可选不内嵌 Source Map) |
| 精简 / 混淆 | 去 OpenCV, 去无障碍服务, 去未用资源, 组件混淆 (dexlib2 / smali) | 库按需勾选 (Terminal Emulator / Embedded Node.js / OpenCV / ML Kit OCR / Paddle OCR / Rapid OCR / OpenCC / Pinyin / Pinyin4j / ML Kit Barcode / MediaInfo / Image Quantization), 无组件混淆 |
| ABI | arm64-v8a / armeabi-v7a | arm64-v8a / armeabi-v7a / x86_64 / x86, 16 KB 对齐报告 |
| 权限 | 约 170 项目录任选 + 启动时申请列表 | 80 项 (带说明), 默认 13 项 |
| 签名 | 创建 (别名 / 密码 / 年限 / CN / OU / O / L / ST / C) / 导入 / 默认签名 / 记住密码, v1 PKCS#7 原生实现 | 密钥库管理 (创建 / 校验 / 删除, AES 加密存储, 内置默认库), 方案 V1+V2 / V1+V2+V3 / V2+V3 / V2 |
| 启动配置 | 显示启动图 / 文字 / 图标 / 自定义 splash XML, 隐藏日志, 稳定模式 | 共享存储 / 开机运行 / 日志可见 / 启动图可见 / 启动器可见 / 日志与设置快捷方式可见 / splash 文字, 构建配置档案 (导入导出, 字段固定策略) |
| 发布 | 发布到代码商店 (`publish` 块, 审核流程) | 无 |
| 打包产物运行时 | `com.stardust.autojs.inrt` (Splash / Log / Settings / CrashReport), 可含 Node 运行时 | `org.autojs.autojs6.inrt` (Splash / Log / Settings), targetSdk 29, 不支持外部 Node 插件 |
| 远程构建 | 无 | 实验性 (开发者选项), 构建委托插件进程 |

### 6.4 任务与触发

| 项 | Pro | AutoJs6 |
| --- | --- | --- |
| 定时 | 一次 / 每日 / 每周 (位掩码), 延迟 / 循环次数 / 循环间隔 | 一次 / 每日 / 每周 / 每月 / 每年 / 倒计时, 重复结束条件 (日期或次数) |
| 广播触发 | 23 种系统广播 + 自定义 action, 5 秒防抖 | 15 种预设 + 自定义 action |
| Activity intent 任务 | `ExternalActionActivity` 接管 `SEND / VIEW / EDIT` 任意 MIME (按项目启用) | 无 (仅 js / text 的运行 / 编辑 / 导入入口) |
| 调度后端 | AlarmManager | AlarmManager / JobScheduler / WorkManager 可选, 精确闹钟权限助手 |
| 项目任务 | 项目可作为任务目标 | 项目目录可直接建任务, 触发时按 `project.json` 重新解析 Rhino / Node / Python 入口 |
| 快捷方式 / 小部件 / 磁贴 / Tasker | 均有 | 均有 (磁贴: 布局范围 / 布局层次) |

### 6.5 远程开发与外部集成

| 项 | Pro | AutoJs6 |
| --- | --- | --- |
| 协议 | JSON-RPC 2.0 over WebSocket; 设备端 27139, 扩展端 21029, 旧协议 9317; `DebugContentProvider` 供 adb 发现 | JSON socket (`hello / command / bytes_command`); 客户端模式 6347, 服务端模式 7347 (局域网或 ADB 转发) |
| 授权 | PC 密钥指纹, 允许一次 / 始终允许 / 拒绝, 撤销全部 | 抽屉开关, Android 17 本地网络权限 |
| 扩展命令 | 23 项: 运行 / 项目 / 停止 / 重跑 / 保存 / 布局分析 (Flutter web 检查器) / 浏览设备文件 (VFS) / rsync 同步 (`.autojs.sync.ignore`) / 远程终端 / FTP / 新建 V8 或 V9 项目 / 调试配置 | 18 项: 连接 / 断开 / 运行 / 指定设备运行 / 停止 / 重跑 / 保存 / 新建文件或项目 / 运行项目 / 查看文档 |
| 断点调试 | Node Inspector 9229 (adb 转发 9138) 在 VS Code 单步 | 无远程断点 (仅设备端 Rhino 调试器) |
| AI 代理 | 无 | MCP Server 插件 (端口 9637, `/mcp`, 授权范围的能力代理: 无障碍 / 按键 / 引擎 / 控制台 / 文件 / 应用 / 剪贴板 / 截图 / Shell / Toast) |
| 更新 | Bugly Beta 应用内升级 | GitHub `version.properties`, 忽略更新管理, 发布历史页 |

### 6.6 悬浮工具与检查器

两者均有圆形悬浮菜单 (运行 / 录制 / 布局范围 / 布局层次 / 停止), 悬浮控制台, 快捷设置磁贴. 差异:

- Pro: 节点信息可 "复制为 Pro 8 选择器 / Pro 9 选择器", 生成 `findOne / untilFind / waitFor`, 保存布局到文件, 悬浮运行控制器 (`FloatingControllerView`), 音量下退出检查器.
- AutoJs6: 代码生成对话框, 窗口切换对话框, 指针位置开关, 最近包名 / 活动复制, 屏幕取色器插件 (拖拽准星 + 放大镜), ImGui 悬浮层插件; 悬浮控制台标题栏 / 内容透明度, 时间格式, 输入栏.

### 6.7 特权与权限

| 项 | Pro | AutoJs6 |
| --- | --- | --- |
| 无障碍开启 | 用户开关 / root / `WRITE_SECURE_SETTINGS` | 用户开关 / root / WSS / Shizuku, A11y 服务管理对话框 (状态诊断, 自检, 事件监视器, 启动 / 停止 / 重启, 清缓存) |
| Shizuku | 有 (`ShizukuShell`) | 有 (`shizuku` 模块, `UserService`) |
| 录制 | 无障碍动作录制 → JS; root `getevent` → `.auto` 或 RootAutomator 脚本 | 同 |
| 权限助手 | 使用情况访问, 所有文件, 电池优化 | 通知发布, 本地网络, 通知访问, 所有文件, 使用情况, 电池优化, 悬浮窗, 小米 / vivo 后台弹窗, 写系统 / 安全设置, 项目媒体, Shizuku |

### 6.8 设置, 外观与其他

| 项 | Pro | AutoJs6 |
| --- | --- | --- |
| 语言 | 中 / 英 (Flutter), 少量 zh-CN / ja / ko / de / ru 框架级条目 | 10 种 (zh-Hans / zh-Hant-TW / zh-Hant-HK / en / es / fr / ru / ar / ja / ko) |
| 外观 | 深色模式 | 夜间 + 自动夜间 (时间 / 亮度), 主题色系统 (色库 / 搜索 / 历史 / 自动对比度), 启动图标别名切换, 启动器快捷方式选择 |
| 日志 | log4j 文件日志 (路径 / 大小 / 备份), 控制台上限, Java 栈, 中文错误 | 每脚本控制台 + 全局控制台, 导出 / 发送, 栈帧跳转, 进程退出记录 (Android 11+) |
| 稳定性 | 稳定模式, 守护模式, 前台服务, 音量上停止, 音量下录制 | 同 + 重启策略与计划重启, 前台常亮 |
| 账号 / 付费 | 登录 / 注册 / 找回 / 资料 / 充值 / 绑定, 一次性或按量付费, 试用页 | 无 |
| 遥测 | Bugly + Flurry | 无 |
| 终端 | Termux 终端 (npm / npx / yarn / pnpm / corepack / node-gyp, npm registry 可配) | 无 |
| 文档 | 在线 / 应用内 WebView | 在线 / 离线文档插件即时切换 |
| 样例 | 英文 262 / 中文 276 个文件, 31 类 | 206 个文件, 28 类 (中文), 另有 accessibility / python / yolo 开发样例 |

### 6.9 插件生态

| 项 | Pro | AutoJs6 |
| --- | --- | --- |
| 分发 | 应用内插件商店 (账号), 打包时合并进 APK | 插件中心 (GitHub 索引, 在线安装 / 更新, 启用 / 授权, 变体优先级, 官方认证, ABI 与 16 KB 对齐提示, 多语言更新日志) |
| 协议 | 单一 SDK (`Plugin` / `PluginRegistry` / `PluginService` AIDL) | 30 个冻结契约 (引擎 ×6, TS 编译器, DEX / R8, 编辑器, 文件动作, APK 模板, 离线文档, MCP, AI ×3, OCR ×3, 条码, OpenCV, 图像量化, YOLO, 取色器, MediaInfo, OpenCC, Pinyin ×2), 旧 SDK 注册表兼容 |
| 已知插件 | ML Kit OCR, Paddle OCR (内置模块依赖), FFmpeg | 43 个官方仓库 (见索引), 一致性测试应用 (AI / DEX / Lua / Python / YOLO) |

## 7. 差异清单

### 7.1 Pro 有而 AutoJs6 没有

1. 进程内 Node.js 16 引擎与第二代 Promise 化 API, 打包 APK 可携带 Node 运行时.
2. 终端 (Termux) 与 npm / yarn / pnpm 包管理, 可安装 ws / express / koa 等 npm 包并起 HTTP 服务器.
3. `$java.defineClass` 运行时 DEX 生成 (JS 类继承 Android 类并在布局 XML 中使用), 线程亲和模式 `ui / current / compute / io`.
4. `@autojs/opencv` (opencv4nodejs 全量: DNN / SVM / 人脸识别 / 跟踪器 / 背景减除 / 级联分类器).
5. Rhino 与 Node 引擎互启互通 (`execScriptFile` + `emit / broadcast`).
6. `androidResources` 真实 aapt2 编译, Node UI 的 `RecyclerView` 适配器与 15 钩子 inflater.
7. 页面侧 Web SDK (`autojs.sdk.v1.js`) 与 React / Vue3 完整样例 (AutoJs6 有同协议桥, 缺 React / Vue3 样例).
8. 编辑器多标签, vim / emacs / sublime / vscode 键位, 选区放大镜, 可视化 UI 设计器.
9. 打包: DEX / V8 快照 / 在线加密等级, 组件混淆, 去未用资源, build hook 脚本, 发布到代码商店, 自定义 splash 布局 XML.
10. Activity intent 任务 (接管任意 MIME 的 SEND / VIEW / EDIT), 5 秒防抖.
11. 远程开发: 授权指纹, 设备文件浏览, rsync 同步, 远程终端, FTP, 远程布局分析, Node 断点单步.
12. 加密 DataStore (`createDatastore({encryptionKey})`), `media_projection.ScreenCapturer.nextImage`, `clip_changed` 事件, RootAutomator v2 批量多点.
13. 账号体系, 付费, 插件商店 / 代码商店, Bugly / Flurry.

### 7.2 AutoJs6 有而 Pro 没有

1. 多语言运行时插件: Node.js 24.5 (外部进程, 安全模型), Bun, QuickJS, Python, Lua, Java / Kotlin; TypeScript 编译器 (Rhino 与 Node 双目标, Source Map 栈映射, 打包时编译).
2. Rhino 2.0.0 分支: BigInt, 模板字符串, 更完整 ES6 内建, 续体默认可用; `flow` 链式异步与 `continuation`.
3. 选择器: 几何 / 树 / 表格准则矩阵, `pickup / detect` 罗盘 DSL, 字符串 DSL, `findIterator`, `explain / dump / stats` 诊断, `waitForStable / waitUntilGone / snapshot / fingerprint`; 自动化工具集 (`smartClick / dismissPopups / collectList / backUntil / waitForToast ...`, `humanize`).
4. 双开 / 工作资料 API (`app.*Dual`), 按键拦截, 事件去抖注册.
5. 视觉: 三系 OCR 引擎 25 个变体自动选择, 条码 13 种码制, YOLO, 本地 LLM (`ai`), 图像相似度 6 种指标, libimagequant 量化, 透明模板掩码匹配, OpenCV 4.8.0.
6. 文本与本地化: `pinyin / pinyin4j / opencc / pangu / i18n / nanoid / cvt / fmt / s13n / jsox / sysprops / mime / tts / mediainfo / sqlite`.
7. 编辑器: 本地 TypeScript IntelliSense (项目级重命名, 跨文件诊断), 编码检测转换, 版本历史与回收站, 大文件模式, 字体下载校验, 底部日志面板.
8. 文件管理: 多选批量, 压缩包读写协议 v22, Markdown / HTML / 图片 / 音视频预览, APK 家族检查与分包安装器.
9. 打包: 4 种 ABI, 12 种库勾选, V1 / V2 / V3 签名方案, 构建配置档案, 远程构建, 16 KB 对齐报告.
10. 任务: 每月 / 每年 / 倒计时, 重复结束条件, 三种调度后端, 项目级任务.
11. 权限与诊断: A11y 服务管理与事件监视对话框, Shizuku 自动开启, 小米 / vivo 弹窗权限, Android 17 本地网络权限, 进程退出记录.
12. MCP Server 插件 (AI 代理远程操控), 官方插件中心与 30 个冻结契约, 主题色系统, 10 种语言, 离线文档插件.

### 7.3 两者都有但实现不同

- 断点调试: 两者都有设备端 Rhino 调试器; Pro 另有 VS Code 远程 Node 单步.
- Shizuku / root / WSS 开启无障碍: 两者都有, AutoJs6 多 Shizuku 自动开启与诊断面板.
- WebView 桥: 同一 `$autojs:internal:request / response` 协议, Pro 提供独立页面 SDK 文件与 React / Vue3 样例.
- 定时任务 API: AutoJs6 的 `workManager` 别名保持 Pro 返回约定, `tasks` 为超集.
- 旧插件 SDK: AutoJs6 保留 `com.stardust.*` 兼容包与 `org.autojs.plugin.sdk.registry` 加载路径.
- 协程: Pro 需 `project.json useFeatures`, AutoJs6 默认可用.
- OpenCV: Pro 内置原生库 (打包可去除), AutoJs6 原生库改为插件 (打包可勾选).

## 8. 数字对照

| 指标 | Pro 9.3.11 | AutoJs6 6.8.0 |
| --- | --- | --- |
| Rhino API 文档标题 (一代) | 1 169 条 / 37 页 | 约 130 页文档, 72 个 d.ts 共 56 601 行 |
| Node 内置模块 | 31 个, 15 347 行, `libautojs9.d.ts` 15 586 行 | 插件侧 bridge 模块 + `autojs6-node` 声明 |
| 内置样例 | 262 (英) / 276 (中) | 206 |
| 清单权限 | 128 项声明 (约 100 项为目录用途) | 76 项 (75 项主清单 + 1 项 target37 覆盖) |
| 打包权限目录 | 约 170 | 80 |
| 原生库 (arm64) | 22 个, `libnode.so` 48.8 MB, `libopencv_java4.so` 17 MB, `libapp.so` 7.8 MB | 宿主精简, OpenCV / Node / OCR 等移入插件 |
| 官方插件 | ML Kit OCR, Paddle OCR, FFmpeg | 43 个仓库, 索引 31 条 |
| 版本活跃 | 2021-03 至 2023-02 | 2021-12 至今, 22 个版本, 339 feature / 503 fix / 420 improvement 条目 |
