<!--suppress HtmlDeprecatedAttribute -->

<div align="center">
  <p>
    <img alt="AF_Banner" src="https://github.com/SuperMonster002/Hello-Sockpuppet/raw/master/auto.js-banner_800×224_transparent.png"/>
  </p>

  <p>Android 平台支持无障碍服务的 JavaScript 自动化工具</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6/releases/latest"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6?color=009688"/></a>
    <a href="https://github.com/mozilla/rhino"><img alt="Rhino" src="https://img.shields.io/badge/engine-rhino%201.7.14-ff69b4"/></a>
    <a href="https://www.codefactor.io/repository/github/SuperMonster003/AutoJs6"><img alt="CodeFactor Grade" src="https://www.codefactor.io/repository/github/SuperMonster003/AutoJs6/badge"/></a>
    <a href="https://lgtm.com/projects/g/SuperMonster003/AutoJs6/?mode=list"><img alt="LGTM Grade" src="https://img.shields.io/lgtm/grade/javascript/github/SuperMonster003/AutoJs6?label=lgtm"/></a>
    <br>
    <a href="https://github.com/SuperMonster003/AutoJs6/commit/ce88d3acb797b180c3f1f15bf77dbba5e934393c"><img alt="Created" src="https://img.shields.io/date/1636632233?color=2e7d32&label=created"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6/find/master"><img alt="GitHub Code Size" src="https://img.shields.io/github/languages/code-size/SuperMonster003/AutoJs6?color=795548"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6/find/master"><img alt="GitHub Code Lines" src="https://img.shields.io/tokei/lines/github/SuperMonster003/AutoJs6?color=37474F"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6?color=534BAE"/></a>
  </p>
</div>

******

### 简介

* Android 平台支持无障碍服务的 JavaScript 自动化工具
* 需要 [Android 7.0](https://zh.wikipedia.org/wiki/Android_Nougat) (`API 24`) 及以上
* 复刻 (Fork) 自 [hyb1996/Auto.js](https://github.com/hyb1996/Auto.js)

******

### 指南

******

* [项目文档](http://docs.autojs.org)

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


* Rhino 引擎由 [v1.7.7.2](https://github.com/mozilla/rhino/releases/tag/Rhino1_7_7_2_Release) 升级至 [v1.7.14](https://github.com/mozilla/rhino/)

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

# v6.0.3

###### 2022/03/19

* `新增` 多语言切换功能 (尚不完善)
* `新增` recorder 模块 (参阅 示例代码 > 计时器 > *.js × 4)
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
* `优化` 升级 Kotlinx Coroutines 版本 1.6.0-native-mt -> 1.6.0
* `优化` 升级 OpenCV 版本 3.4.3 -> 4.5.4 -> 4.5.5 (Ref to TonyJiangWJ)
* `优化` 升级 Okhttp3 版本 3.10.0 -> 5.0.0-alpha.4 -> 5.0.0-alpha.6
* `优化` 升级 Android Gradle 插件版本 7.2.0-beta01 -> 7.3.0-alpha06
* `优化` 升级 Auto.js-ApkBuilder 版本 1.0.1 -> 1.0.3
* `优化` 升级 Glide Compiler 版本 4.12.0 -> 4.13.1
* `优化` 升级 Gradle 发行版本 7.4-rc-2 -> 7.4.1
* `优化` 升级 Gradle Compile 版本 31 -> 32
* `优化` 升级 Gson 版本 2.8.9 -> 2.9.0

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
* `优化` 升级 Androidx AppCompat 版本 1.4.0 -> 1.4.1
* `优化` 升级 Androidx Preference 版本 1.1.1 -> 1.2.0
* `优化` 升级 Okhttp3 版本 3.10.0 -> 5.0.0-alpha.3 -> 5.0.0-alpha.4
* `优化` 升级 Android Material 版本 1.6.0-alpha01 -> 1.6.0-alpha02
* `优化` 升级 Android Gradle 插件版本 7.2.0-alpha06 -> 7.2.0-beta01
* `优化` 升级 Gradle 发行版本 7.3.3 -> 7.4-rc-2

# v6.0.1

###### 2022/01/01

* `新增` 连接 VSCode 插件支持客户端 (LAN) 及服务端 (LAN/ADB) 方式 (Ref to Auto.js Pro)
* `新增` 增加 $base64 全局对象 (Ref to Auto.js Pro)
* `新增` 增加 isInteger/isNullish/isPlainObject/isPrimitive/isReference 全局方法
* `新增` 增加 polyfill (Object.getOwnPropertyDescriptors)
* `新增` 增加 polyfill (Array.prototype.flat)
* `优化` 扩展 global.sleep 支持 随机范围/负数兼容
* `优化` 扩展 global.toast 支持 时长控制/强制覆盖控制/dismiss
* `优化` 包名对象全局化 (okhttp3/androidx/de)
* `优化` 升级 Android Material 版本 1.5.0-beta01 -> 1.6.0-alpha01
* `优化` 升级 Android Gradle 插件版本 7.2.0-alpha04 -> 7.2.0-alpha06
* `优化` 升级 Kotlinx Coroutines 版本 1.5.2-native-mt -> 1.6.0-native-mt
* `优化` 升级 Kotlin Gradle 插件版本 1.6.0 -> 1.6.10
* `优化` 升级 Gradle 发行版本 7.3 -> 7.3.3

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
