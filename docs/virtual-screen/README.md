# 虚拟屏幕执行 / Virtual Screen Execution

让一次自动化任务整体运行在一个后台虚拟显示屏上。**这是任务级启动选项，不是脚本 API**：已有的 JS 脚本一行都不用改，截图、点击、滑动、控件查找、应用启动等面向屏幕的能力会自动指向虚拟屏。

## 为什么要这样做

在物理屏上跑自动化会占用设备、抢占焦点，且脚本与用户操作互相干扰。把任务放进独立的 `VirtualDisplay` 后：脚本在后台屏运行，主屏可以正常使用；不同任务可以各占一块屏；虚拟屏随任务结束自动释放。

实现机制参考 [Mika](https://github.com/wangzan101/Mika)（Apache-2.0）：由 Shizuku 的 shell-uid 常驻用户服务持有 `VirtualDisplay`，而不是由 app 进程或一次性 `execCommand` 创建——后者会随进程退出把屏幕一起带走。

## 依赖与适用范围

| 项 | 要求 |
| --- | --- |
| Shizuku | 已启动并给 AutoJs6 授权；`IUserService` 以 shell uid（2000）常驻 |
| Android | 12（API 31）及以上；可信屏标志位从 API 33 起可用，独立焦点从 API 34 起可用 |
| 隐藏 API | `DisplayManager.createVirtualDisplay` 的 @hide flags、`InputEvent.setDisplayId`、`WindowManagerGlobal` 反射 |
| 无障碍 | 需要无障碍服务才能做控件查找（见下方「控件查找」） |

不满足条件时，任务会明确报错并在物理屏上继续（或按配置强制关闭），不会静默降级。

## 如何启用

四种入口，优先级从高到低：

1. **单次运行**：文件列表里脚本的「在虚拟屏运行」菜单项。
2. **定时任务**：任务设置页新增「虚拟屏幕（本任务）」三态选择——跟随全局设置 / 强制开启 / 强制关闭；该值持久化在定时任务数据库（`virtual_screen_mode` 列）。
3. **项目**：`project.json` 的 `useFeatures: ["virtualScreen"]`，让整个项目的脚本默认走虚拟屏。
4. **全局**：设置页开关 `virtual_screen_run_mode`。

外部启动也可以通过 Intent extra 指定：`virtual_screen_mode`（1=强制开、2=强制关、0/缺省=跟随设置），以及 `virtual_screen_width` / `virtual_screen_height` / `virtual_screen_density` / `virtual_screen_display_id`（正数表示复用已有虚拟屏）。不指定尺寸时跟随物理屏。

侧边栏「工具」分组里的**虚拟屏幕查看器**提供实时预览、点击/滑动/长按手势、返回/主页/多任务/电源键、输入文本、启动应用、保存 PNG、新建/切换/释放屏幕等操作；Activity 为 `exported=false`。

### 首页悬浮小窗

主界面右下角（避开主 FAB）常驻一个应用内悬浮预览：显示当前存活虚拟屏的实时画面、displayId、尺寸与 FPS；拖动标题可移动，点 `−` 收起成 `VS` 小浮标，点击画面打开完整查看器。它是加到 `android.R.id.content` 的普通 View，**不需要 `SYSTEM_ALERT_WINDOW` 权限**，也只在 AutoJs6 界面内可见；设置页 `virtual_screen_floating_window` 开关默认开启。

帧源与完整查看器共享：`VirtualScreenSession` 用 `PreviewObserverRegistry` 维护多个订阅者，第一个订阅者启动唯一的 `VirtualScreenFramePump`，最后一个离开时才停泵。因此打开/关闭查看器不会让小窗黑屏，反之亦然；脚本截图仍从帧泵的复用 Bitmap 复制。小窗跟随 `VirtualScreenManager.liveSessions()`，脚本、定时任务或查看器创建的屏出现/释放时自动切换，Activity 进入后台即停止订阅以免耗电。

## 架构

```
ScriptRuntime.initPrologue()
  └─ DisplayRoute.apply(ExecutionConfig)   ← 任务级屏幕作用域
       ├─ VirtualScreenManager / Session   ← app 侧：ImageReader surface、截图、生命周期
       │    ├─ PreviewObserverRegistry → VirtualScreenFramePump ← 唯一取帧者，小窗 / 查看器共享
       │    └─ Shizuku UserService (AIDL 51–61) ← shell 侧：建屏、输入、启动、释放
       ├─ accessibilityBridge.setDisplayScope(displayId)
       └─ runtime property "virtualScreen.displayId"
```

所有面向屏幕的运行时 API 都从 `DisplayRoute` 取当前 display，而不再假定默认显示屏：

| 能力 | 路由 |
| --- | --- |
| `click` / `swipe` / `longPress` / 按键 / 输入 | `SimpleActionAutomator` → `DisplayRoute` → shell 注入（`setDisplayId`） |
| `captureScreen()` / `images` 截图与保存 | `Images` → `VirtualScreenSession` 的 `ImageReader` |
| `app.launchPackage` / `launch` / `app.openUrl` | `App` → `launchOnDisplay` / `launchUriOnDisplay`（`startActivityAsUser` + `setLaunchDisplayId`） |
| `shell` 的 `am start` 类操作 | `Shell` → 追加 `--display <id>` |
| 控件查找 | `AccessibilityBridge` → `DisplayWindowScope`（见下） |

`DisplayRoute` 的会话登记到运行时的 closeable 管理器，任务结束即释放屏幕；查看器用 `createDetached()` 创建独立屏，可跨 Activity 存活。

### 控件查找

`AccessibilityService.getWindows()` 只返回默认屏的窗口，虚拟屏上的窗口必须走 `getWindowsOnAllDisplays()` 展开 `SparseArray`。这里引入 `DisplayWindowScope`，把窗口按 `displayId` 限定，并与 `auto.setWindowFilter` 的用户过滤取交集；`windowRoots()`、`windows()`、`getRootInCurrentWindow()`、`getRootInActiveWindow()` 全链路作用域化。虚拟屏下的 `performAction()` 也改用作用域窗口的包名做保护判断。

### 显示标志位

`VirtualDisplayFlags.forSdk(sdk, trusted)` 按系统版本组合 @hide 位：API ≤32 用基础位，API 33 起在 shell 拥有 `ADD_TRUSTED_DISPLAY` 时加 `TRUSTED | OWN_DISPLAY_GROUP | ALWAYS_UNLOCKED`，API 34 起 `OWN_FOCUS` 必须与 `STEAL_TOP_FOCUS_DISABLED` 成对出现，否则虚拟屏会抢走主屏焦点。该对象不引用任何 `android.*` 类，可在纯 JVM 上做单元测试。

### 接口版本

`IUserService.aidl` 新增事务 51–61（建屏、释放、列举、信息、启动、点击、滑动、按键、文本、强停、applink），`WrappedShizuku` 的接口版本随之从 `VERSION_CODE + 6` 提升到 `+ 8`。后续改动 AIDL 时需同步递增，否则会连到旧版服务。

## 验证状态

- `:app:compileAppDebugKotlin` 与 `:app:assembleAppDebug` 通过；与本次改动相关的 9 个测试类（虚拟屏标志位、坐标映射、窗口作用域、悬浮窗几何、多预览观察者、模块顺序、API surface 门禁、静态引用白名单、Rhino 别名兼容）共 55 个用例全部通过。KSP/API surface baseline 与静态引用白名单均已同步更新。
- 全量 `:app:testAppDebugUnitTest` 在本机另有 20 个与虚拟屏代码无关的既有基线失败：macOS 把 `/var` 规范化成 `/private/var` 引发的路径断言，以及中文 locale 下 Rhino 错误消息与英文断言不一致。Linux + 英文 locale 的 CI 上不会出现。
- 任务级主链路（建屏、Settings 落到虚拟屏、截图、输入、释放、与主屏隔离）在一台 Android 17 / API 37 设备上验证通过。
- **尚未在真机复验**：查看器预览与手势、定时任务建屏、applink 落到虚拟屏、截图与预览并发、控件查找修复后的完整链路，以及悬浮小窗的画面、拖动/折叠、与查看器同时打开时的双订阅共存。提交前请在目标设备上按上述清单补测。

## 已知限制

- 依赖 Shizuku 与多个隐藏 API，不同 ROM / 未来 Android 版本可能行为不一致；`ADD_TRUSTED_DISPLAY` 缺失时会退化为非可信屏，与主屏共享焦点。
- `IntentTask`（非定时任务的意图触发路径）尚未接入任务级虚拟屏配置。
- 少数系统应用或使用了 `FLAG_ACTIVITY_NEW_TASK` 且强制主屏的 Activity 可能无法落到虚拟屏。
- 同一时刻一个脚本只绑定一块屏；多屏并行需要额外的调度策略。
- 为保持 `com.stardust` Rhino 别名所承诺的 `ExecutionConfig.copy()` 签名不变，虚拟屏字段放在类体而不是 data class 主构造里；调用 `copy()` 的代码需要显式把这些选项带过去。
- PR 体积较大（约 4000 行，含 UI 与文档）。如果维护者希望拆分，建议的顺序是：① `core/virtualscreen` + AIDL/Shizuku 底座，② `DisplayRoute` 与运行时 API 路由，③ 控件查找作用域，④ 设置/定时任务/菜单，⑤ 查看器与示例。

## 示例

- `sample/virtual-screen/zero-change-demo.js`：存量脚本零改动，仅靠运行配置切到虚拟屏。
- `sample/virtual-screen/virtual-screen-demo.js`：显式 `virtualscreen` 模块的高级用法（手动建屏、指定尺寸、跨屏操作）。这是一个可选的高级接口，主推路径仍是任务级配置。
