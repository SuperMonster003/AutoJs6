<!--suppress HtmlDeprecatedAttribute -->

<div align="center">
  <p>
    <img alt="AF_Banner" src="https://github.com/SuperMonster002/Hello-Sockpuppet/raw/master/auto.js-banner_800×224_transparent.png"/>
  </p>

  <p>Android 平台支持无障碍服务的 JavaScript 自动化工具</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6/releases/latest"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6?color=009688"/></a>
    <a href="https://www.codefactor.io/repository/github/SuperMonster003/AutoJs6"><img alt="CodeFactor Grade" src="https://www.codefactor.io/repository/github/SuperMonster003/AutoJs6/badge"/></a>
    <a href="https://lgtm.com/projects/g/SuperMonster003/AutoJs6/?mode=list"><img alt="LGTM Grade" src="https://img.shields.io/lgtm/grade/javascript/github/SuperMonster003/AutoJs6?label=lgtm"/></a>
    <br>
    <a href="https://github.com/mozilla/rhino"><img alt="Rhino" src="https://img.shields.io/badge/rhino-1.7.15--snapshot-F06292"/></a>
    <a href="https://developer.android.com/studio/archive"><img alt="Android Studio" src="https://img.shields.io/badge/android%20studio-bumblebee%202021.1.1-B64FC8"/></a>
    <br>
    <a href="https://github.com/SuperMonster003/AutoJs6/commit/ce88d3acb797b180c3f1f15bf77dbba5e934393c"><img alt="Created" src="https://img.shields.io/date/1636632233?color=2e7d32&label=created"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6/find/master"><img alt="GitHub Code Size" src="https://img.shields.io/github/languages/code-size/SuperMonster003/AutoJs6?color=795548"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6?color=534BAE"/></a>
  </p>
</div>

******

### 简介

* Android 平台支持无障碍服务的 JavaScript 自动化工具
* 需要 [Android 7.0](https://zh.wikipedia.org/wiki/Android_Nougat) ([API](https://developer.android.com/guide/topics/manifest/uses-sdk-element#ApiLevels) [24](https://developer.android.com/reference/android/os/Build.VERSION_CODES#N)) 及以上
* 复刻 (Fork) 自 [hyb1996/Auto.js](https://github.com/hyb1996/Auto.js)

******

### 指南

******

* [项目文档](https://hyb1996.github.io/AutoJs-Docs) (临时参考文档)

******

### 功能

******

* 可用作 JavaScript IDE (代码补全/变量重命名/代码格式化)
* 支持基于 [无障碍服务](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService) 的自动化操作
* 支持悬浮窗快捷操作 (脚本录制及运行/查看包名及活动/布局分析)
* 支持选择器 API 并提供控件遍历/获取信息/控件操作 (类似 [UiAutomator](https://developer.android.com/training/testing/ui-automator))
* 支持布局界面分析 (类似 Android Studio 的 LayoutInspector)
* 支持录制功能及录制回放
* 支持屏幕截图/保存截图/图片找色/图片匹配
* 支持 [E4X](https://zh.wikipedia.org/wiki/E4X) (ECMAScript for XML) 编写界面
* 支持将脚本文件或项目打包为 APK 文件
* 支持利用 Root 权限扩展功能 (屏幕点击/滑动/录制/Shell)
* 支持作为 Tasker 插件使用
* 支持与 VSCode 连接并进行桌面开发 (需要 [AutoJs6-VSCode-Extension](https://github.com/SuperMonster003/AutoJs6-VSCode-Extension) 插件)

******

### 主要变更

******

* VSCode 插件支持客户端 (LAN) 及服务端 (LAN/ADB) 连接方式


* [Rhino](https://github.com/mozilla/rhino/) 引擎由 [v1.7.7.2](https://github.com/mozilla/rhino/releases/tag/Rhino1_7_7_2_Release) 升级至 [v1.7.15-SNAPSHOT](https://github.com/SuperMonster003/Rhino-For-AutoJs6/commits/master)

    * 支持 [Object.values()](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Object/values)

       ```javascript
       Object.values({name: 'Max', age: 4}); // ['max', 4]
       ```

    * 支持 [Array.prototype.includes()](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Array/includes)

       ```javascript
       [10, 20, NaN].includes(20); // true
       ```

    * 支持 [BigInt](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/BigInt)

       ```javascript
       typeof 567n === 'bigint'; // true
       ```

    * 支持 [模板字符串](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Template_literals)

       ```javascript
       `Lucky number: ${(Math.random() * 100).toFixed(0)}`
       ```

    * 查看 Rhino 引擎 [更多新特性](https://github.com/SuperMonster003/AutoJs6/blob/master/app/src/main/assets/doc/RHINO.md)

    * 查看 Rhino 引擎 [兼容性列表](https://mozilla.github.io/rhino/compat/engines.html)

******

### 版本历史

******

[comment]: <> "Version history only shows last 3 versions"

# v6.1.0

###### 2022/05/26 - 包名变更 谨慎升级

* `提示` 修改应用包名为 org.autojs.autojs6 避免与开源 Auto.js 应用包名冲突
* `新增` 首页抽屉增加 "投影媒体权限" 开关 (Root / ADB 方式) (开关状态检测为实验性)
* `新增` 文件浏览器支持显示隐藏文件和文件夹 (参阅 设置页面)
* `新增` 强制 Root 检查功能 (参阅 设置页面 及 示例代码)
* `新增` 内置 autojs 模块 (参阅 示例代码 > AutoJs6)
* `新增` 内置 tasks 模块 (参阅 示例代码 > 任务)
* `新增` console.launch() 方法启动日志活动页面
* `新增` util.morseCode 工具 (参阅 示例代码 > 工具 > 摩斯电码)
* `新增` util.versionCodes 工具 (参阅 示例代码 > 工具 > 安卓版本信息查询)
* `新增` util.getClass() 等方法 (参阅 示例代码 > 工具 > 获取类与类名)
* `新增` timers.setIntervalExt() 方法 (参阅 示例代码 > 定时器 > 条件周期执行)
* `新增` colors.toInt() / rgba() 等方法 (参阅 示例代码 > 图像与颜色 > 基本颜色转换)
* `新增` automator.isServiceEnabled() / ensureService() 方法
* `新增` automator.lockScreen() 等方法 (参阅 示例代码 > 无障碍服务 > 安卓 9 新增)
* `新增` automator.headsethook() 等方法 (参阅 示例代码 > 无障碍服务 > 安卓 11 新增)
* `新增` automator.captureScreen() 方法 (参阅 示例代码 > 无障碍服务 > 获取屏幕截图)
* `新增` dialogs.build() 选项参数属性 animation, linkify 等 (参阅 示例代码 > 对话框 > 个性化对话框)
* `修复` dialogs.build() 选项参数属性 inputHint, itemsSelectedIndex 等功能异常
* `修复` JsDialog#on('multi_choice') 回调参数功能异常
* `修复` UiObject#parent().indexInParent() 总是返回 -1 的问题 _[`issue #16`](https://github.com/SuperMonster003/AutoJs6/issues/16)_
* `修复` Promise.resolve() 返回的 Thenable 在临近脚本结束时可能不被调用的问题
* `修复` 包名或类名中可能的拼写失误 (boardcast -> broadcast / auojs -> autojs)
* `修复` images.requestScreenCapture() 在高版本安卓系统可能导致应用崩溃的问题 (API >= 31)
* `修复` images.requestScreenCapture() 多个脚本实例同时申请可能导致应用崩溃的问题
* `修复` 调用 new RootAutomator() 可能出现的假死问题
* `优化` RootAutomator 在无 Root 权限时将无法实例化
* `优化` 重新设计 "关于应用与开发者" 页面
* `优化` 重构全部内置 JavaScript 模块
* `优化` 重构全部 Gradle 构建脚本并增加公共配置脚本 (config.gradle)
* `优化` Gradle 构建工具支持版本号自动管理及构建文件自动命名
* `优化` Gradle 构建工具增加 task 支持附加 CRC32 摘要到构建文件 (appendDigestToReleasedFiles)
* `优化` shell() 调用时将异常写入返回结果而非直接将异常抛出 (无需 try/catch)
* `优化` 使用 Rhino 内置的 JSON 替代原 json2 模块
* `优化` auto.waitFor() 支持超时参数
* `优化` threads.start() 支持箭头函数参数
* `优化` console.trace() 支持按日志等级参数 (参阅 示例代码 > 控制台 > 打印调用栈)
* `优化` device.vibrate() 支持模式震动及摩斯电码震动 (参阅 示例代码 > 设备 > 模式震动 / 摩斯电码震动)
* `优化` 外部存储读写权限适配高版本安卓系统 (API >= 30)
* `优化` 控制台字体采用 Material Color 增强普通及夜间主题下的字体可读性
* `优化` 保存 ImageWrapper 所有实例弱引用并在脚本结束时自动回收 (实验性)
* `优化` 附加 CircleImageView 版本 3.1.0
* `优化` 升级 Rhino 引擎版本 1.7.14 -> 1.7.15-snapshot
* `优化` 部分依赖或本地库版本调整 (详见 [`CHANGELOG.md`](https://github.com/SuperMonster003/AutoJs6/blob/master/app/src/main/assets/doc/CHANGELOG.md#v610))

# v6.0.3

###### 2022/03/19

* `新增` 多语言切换功能 (尚不完善)
* `新增` 内置 recorder 模块 (参阅 示例代码 > 计时器)
* `新增` 使用 "修改安全设置权限" 自动启用无障碍服务及开关设置
* `修复` 点击 "快速设置" 中相关图标后面板未自动收起的问题 (试修) _[`issue #7`](https://github.com/SuperMonster003/AutoJs6/issues/7)_
* `修复` toast 使用强制显示参数时可能导致 AutoJs6 崩溃的问题
* `修复` Socket 传输数据头部信息不完整时可能导致 AutoJs6 崩溃的问题
* `优化` 启动或重启 AutoJs6 时根据选项设置自动开启无障碍服务
* `优化` 开启悬浮窗显示时尝试自动开启无障碍服务
* `优化` 所有资源文件补全元素对应的英文翻译
* `优化` 微调主页抽屉布局 减小项目排列间距
* `优化` 主页抽屉增加前台服务状态开关的同步
* `优化` 主页抽屉展开时立即按需同步开关状态
* `优化` 显示指针位置增加状态检测及结果提示
* `优化` 支持 64 位操作系统 (Ref to TonyJiangWJ)
* `优化` 悬浮窗初始化时同时应用透明度设置 (无需点击后再应用透明度)
* `优化` 重置文件内容时增加是否为示例代码文件的检测并增加结果提示
* `优化` 转移打包插件下载地址 GitHub -> JsDelivr
* `优化` 附加 Zeugma Solutions LocaleHelper 版本 1.5.1
* `优化` 降级 Android Material 版本 1.6.0-alpha02 -> 1.5.0
* `优化` 部分依赖或本地库版本调整 (详见 [`CHANGELOG.md`](https://github.com/SuperMonster003/AutoJs6/blob/master/app/src/main/assets/doc/CHANGELOG.md#v603))

# v6.0.2

###### 2022/02/05

* `新增` images.bilateralFilter() 双边滤波图像处理方法
* `修复` 多次调用 toast 只生效最后一次调用的问题
* `修复` toast.dismiss() 可能无效的问题
* `修复` 客户端模式及服务端模式开关可能无法正常工作的问题
* `修复` 客户端模式及服务端模式开关状态不能正常刷新的问题
* `修复` Android 7 解析 UI 模式 text 元素异常 (Ref to TonyJiangWJ) _[`issue #4`](https://github.com/SuperMonster003/AutoJs6/issues/4)_ _[`#9`](https://github.com/SuperMonster003/AutoJs6/issues/9)_
* `优化` 忽略 sleep() 的 ScriptInterruptedException 异常
* `优化` 附加 Androidx AppCompat (Legacy) 版本 1.0.2
* `优化` 升级 Rhino 引擎版本 1.7.14-snapshot -> 1.7.14
* `优化` 部分依赖或本地库版本调整 (详见 [`CHANGELOG.md`](https://github.com/SuperMonster003/AutoJs6/blob/master/app/src/main/assets/doc/CHANGELOG.md#v602))

##### 更多版本历史可参阅

* [CHANGELOG.md](https://github.com/SuperMonster003/AutoJs6/blob/master/app/src/main/assets/doc/CHANGELOG.md)

******

### 相关项目

******

* [Auto.js](https://github.com/TonyJiangWJ/Auto.js) { author: [TonyJiangWJ](https://github.com/TonyJiangWJ) }
    - `安卓平台 JavaScript 自动化工具 (二次开发项目)`
    
* [AutoX](https://github.com/kkevsekk1/AutoX) { author: [kkevsekk1](https://github.com/kkevsekk1) }
    - `安卓平台 JavaScript 自动化工具 (二次开发项目)`

* [AutoJs6-VSCode-Extension](https://github.com/SuperMonster003/AutoJs6-VSCode-Extension) { author: [SuperMonster003](https://github.com/SuperMonster003) }
    - `适用于 VSCode 的桌面开发插件 (二次开发项目)`

[comment]: <> (* [Auto.js-TypeScript-Declarations]&#40;https://github.com/SuperMonster003/Auto.js-TypeScript-Declarations&#41; { author: [SuperMonster003]&#40;https://github.com/SuperMonster003&#41; })
[comment]: <> (    - `Auto.js 声明文件 &#40;.d.ts&#41;`)
