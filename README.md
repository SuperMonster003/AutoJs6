<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://s1.imagehub.cc/images/2023/03/07/af8ed087c9d354b9ab6142aae7bbafb6.png" alt="autojs6-banner_800×224" border="0" width="704" />
  </p>

  <p>Android 平台支持无障碍服务的 JavaScript 自动化工具</p>

  <p>
    <a href="http://download.autojs6.com"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6"/></a>
    <a href="http://issues.autojs6.com"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6?color=009688"/></a>
    <a href="http://commit.autojs6.com/99a1d8490fac5b6d55f6f183db59ad833a2064ed"><img alt="Created" src="https://img.shields.io/date/1636632233?color=2e7d32&label=created"/></a>
    <br>
    <a href="https://github.com/mozilla/rhino"><img alt="Rhino" src="https://img.shields.io/badge/rhino-1.7.15--snapshot-F06292"/></a>
    <a href="https://developer.android.com/studio/archive"><img alt="Android Studio" src="https://img.shields.io/badge/android%20studio-2022.1+-B64FC8"/></a>
    <br>
    <a href="https://www.codefactor.io/repository/github/SuperMonster003/AutoJs6"><img alt="CodeFactor Grade" src="https://www.codefactor.io/repository/github/SuperMonster003/AutoJs6/badge"/></a>
    <a href="http://project.autojs6.com/find/master"><img alt="GitHub Code Size" src="https://img.shields.io/github/languages/code-size/SuperMonster003/AutoJs6?color=795548"/></a>
    <a href="http://project.autojs6.com/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6?color=534BAE"/></a>
  </p>
</div>

******

### 简介

******

[Auto.js](https://github.com/hyb1996/Auto.js) 是一款 Android 平台支持 [无障碍服务](https://developer.android.com/guide/topics/ui/accessibility/service?hl=zh-cn) 的 JavaScript 自动化工具软件.

Auto.js 由 [hyb1996](https://github.com/hyb1996) 于 `2017/01/27` 初次发布, 于 `2020/03/13` 停止维护, 最终版本名称为 `4.1.1 Alpha2`, 构建版本号为 `461`.

AutoJs6 在 Auto.js 最终项目的基础上, 于 `2021/12/01` 进行二次开发, 继续保持开源免费.

基于 Auto.js 二次开发的开源项目 (以开发时间排序, 仅部分列举):

|                         项目名称                          |          应用名称          |                          开发者                          |     开发时间     |
|:-----------------------------------------------------:|:----------------------:|:-----------------------------------------------------:|:------------:|
|   [Auto.js](https://github.com/TonyJiangWJ/Auto.js)   |       Auto.js M        |     [TonyJiangWJ](https://github.com/TonyJiangWJ)     | `2019/11/21` |
|      [AutoX](https://github.com/kkevsekk1/AutoX)      | Autox.js / Autox.js v6 |       [kkevsekk1](https://github.com/kkevsekk1)       | `2020/07/24` |
| [AutoJs6](https://github.com/SuperMonster003/AutoJs6) |        AutoJs6         | [SuperMonster003](https://github.com/SuperMonster003) | `2021/12/01` |

******

### 功能

******

* 可用作 JavaScript IDE (代码补全/变量重命名/代码格式化)
* 支持基于 [无障碍服务](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService) 的自动化操作
* 支持浮动按钮快捷操作 (脚本录制及运行/查看包名及活动/布局分析)
* 支持选择器 API 并提供控件遍历/获取信息/控件操作 (类似 [UiAutomator](https://developer.android.com/training/testing/ui-automator))
* 支持布局界面分析 (类似 Android Studio 的 LayoutInspector)
* 支持录制功能及录制回放
* 支持屏幕截图/保存截图/图片找色/图片匹配
* 支持 [E4X](https://zh.wikipedia.org/wiki/E4X) (ECMAScript for XML) 编写界面
* 支持将脚本文件或项目打包为 APK 文件
* 支持利用 Root 权限扩展功能 (屏幕点击/滑动/录制/Shell)
* 支持作为 Tasker 插件使用
* 支持与 VSCode 连接并进行桌面开发 (需要 [AutoJs6-VSCode-Extension](http://vscext-project.autojs6.com) 插件)

******

### 环境

******

- Android 操作系统
- [API](https://developer.android.com/guide/topics/manifest/uses-sdk-element#ApiLevels) [24](https://developer.android.com/reference/android/os/Build.VERSION_CODES#N) ([7.0](https://zh.wikipedia.org/wiki/Android_Nougat)) [[N](https://developer.android.com/reference/android/os/Build.VERSION_CODES#N)] 及以上

******

### 指南

******

* [应用文档](https://docs.autojs6.com)
* [使用手册 (待编写)](https://docs.autojs6.com/#/manual)
* [疑难解答](https://docs.autojs6.com/#/qa)
* [项目编译构建](#项目编译构建)
* [脚本开发辅助](#脚本开发辅助)

******

### 主要变更

******

* VSCode 插件支持客户端 (LAN) 及服务端 (LAN/ADB) 连接方式

* 多语言适配 (西/法/俄/阿/日/韩/英/简中/繁中等)

* 夜间模式适配 (设置页面/文档页面/布局分析页面/浮动窗口等)

* [Rhino](https://github.com/mozilla/rhino/) 引擎由 [v1.7.7.2](https://github.com/mozilla/rhino/releases/tag/Rhino1_7_7_2_Release) 升级至 [v1.7.15-SNAPSHOT](http://rhino.autojs6.com/blob/dbe3f43ba5eb01e7f76139208f36c383dcd1c488/gradle.properties#L3)

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

    * 查看 Rhino 引擎 [更多新特性](http://project.autojs6.com/blob/master/app/src/main/assets/doc/RHINO.md)

    * 查看 Rhino 引擎 [兼容性列表](https://mozilla.github.io/rhino/compat/engines.html)

******

### 版本历史

******

[comment]: <> "Version history only shows last 3 versions"

# v6.5.0

###### 2023/12/02

* `新增` opencc 模块 (参阅 项目文档 > [中文转换](https://docs.autojs6.com/#/opencc)) (Ref to [LZX284](https://github.com/SuperMonster003/AutoJs6/pull/187/files#diff-8cff73265af19c059547b76aca8882cbaa3209291406f52df1dafbbc78e80c46R268))
* `新增` UiSelector 增加 [plus](https://docs.autojs6.com/#/uiObjectType?id=m-plus) 及 [append](https://docs.autojs6.com/#/uiObjectType?id=m-append) 方法 _[`issue #115`](http://issues.autojs6.com/115)_
* `新增` 打包应用页面增加 ABI 及库的筛选支持 (Ref to [AutoX](https://github.com/kkevsekk1/AutoX)) _[`issue #189`](http://issues.autojs6.com/189)_
* `修复` 打包应用文件体积异常庞大的问题 (Ref to [AutoX](https://github.com/kkevsekk1/AutoX) / [LZX284](https://github.com/SuperMonster003/AutoJs6/pull/187/files#diff-d932ac49867d4610f8eeb21b59306e8e923d016cbca192b254caebd829198856R61)) _[`issue #176`](http://issues.autojs6.com/176)_
* `修复` 打包应用无法显示并打印部分异常消息的问题
* `修复` 打包应用页面选择应用图标后可能显示空图标的问题
* `修复` 打包应用包含 MLKit Google OCR 库时可能出现的上下文未初始化异常
* `修复` ocr.<u>mlkit/ocr</u>.<u>recognizeText/detect</u> 方法无效的问题
* `修复` 部分文本 (如日志页面) 显示语言与应用设置语言可能不相符的问题
* `修复` 部分语言在主页抽屉开关项可能出现文本溢出的问题
* `修复` 部分设备无障碍服务开启后立即自动关闭且无任何提示消息的问题 _[`issue #181`](http://issues.autojs6.com/181)_
* `修复` 部分设备无障碍服务开启后设备物理按键可能导致应用崩溃的问题 (试修) _[`issue #183`](http://issues.autojs6.com/183)_ _[`issue #186`](http://issues.autojs6.com/186#issuecomment-1817307790)_
* `修复` 使用 auto(true) 重启无障碍服务后 pickup 功能异常的问题 (试修) _[`issue #184`](http://issues.autojs6.com/184)_
* `修复` floaty 模块创建浮动窗口拖动时可能导致应用崩溃的问题 (试修)
* `修复` app.startActivity 无法使用简称参数的问题 _[`issue #182`](http://issues.autojs6.com/182)_ _[`issue #188`](http://issues.autojs6.com/188)_
* `修复` importClass 导入的类名与全局变量冲突时代码抛出异常的问题 _[`issue #185`](http://issues.autojs6.com/185)_
* `修复` Android 7.x 无法使用无障碍服务的问题
* `修复` Android 14+ 可能无法正常使用 runtime.<u>loadJar/loadDex</u> 方法的问题 (试修)
* `修复` 安卓系统快速设置面板中 "布局范围分析" 和 "布局层次分析" 不可用的问题 _[`issue #193`](http://issues.autojs6.com/193)_
* `修复` 自动检查更新功能可能导致应用 [ANR](https://developer.android.com/topic/performance/vitals/anr) 的问题 (试修) _[`issue #186`](http://issues.autojs6.com/186)_
* `修复` 文件管理器示例代码文件夹点击 "向上" 按钮后无法回到工作路径页面的问题 _[`issue #129`](http://issues.autojs6.com/129)_
* `修复` 代码编辑器使用替换功能时替换按钮无法显示的问题
* `修复` 代码编辑器长按删除时可能导致应用崩溃的问题 (试修)
* `修复` 代码编辑器点击 fx 按钮无法显示模块函数快捷面板的问题
* `修复` 代码编辑器模块函数快捷面板按钮函数名称可能溢出的问题
* `优化` 代码编辑器模块函数快捷面板适配夜间模式
* `优化` 打包应用启动页面适配夜间模式并调整应用图标布局
* `优化` 打包应用页面支持使用软键盘 ENTER 键实现光标跳转
* `优化` 打包应用页面支持点击 ABI 标题及库标题切换全选状态
* `优化` 打包应用页面默认 ABI 智能选择并增加不可选择项的引导提示
* `优化` 文件管理器根据文件及文件夹的类型及特征调整菜单项的显示情况
* `优化` 文件管理器文件夹右键菜单增加打包应用选项
* `优化` 无障碍服务启用但功能异常时在 AutoJs6 主页抽屉开关处将体现异常状态
* `优化` 部分依赖或本地库版本调整 _[`CHANGELOG.md`](http://project.autojs6.com/blob/master/app/src/main/assets/doc/CHANGELOG.md#v650)_

# v6.4.2

###### 2023/11/15

* `新增` dialogs.build() 选项参数属性 inputSingleLine
* `新增` console.setTouchable 方法 _[`issue #122`](http://issues.autojs6.com/122)_
* `修复` ocr 模块部分方法无法识别区域参数的问题 _[`issue #162`](http://issues.autojs6.com/162)_  _[`issue #175`](http://issues.autojs6.com/175)_
* `修复` Android 7.x 发现新版本时无法获取版本详情的问题
* `修复` Android 14 申请截图权限时导致应用崩溃的问题
* `修复` 主页抽屉快速切换 "浮动按钮" 开关时可能导致应用崩溃的问题
* `修复` 使用菜单关闭浮动按钮时重启应用后浮动按钮可能依然显示的问题
* `修复` 安卓 13 及以上系统设置页面选择并切换 AutoJs6 语言后无法生效的问题
* `修复` 构建工具初次构建时无法自动完成 OpenCV 资源部署的问题
* `优化` 原生化 bridges 模块以提升脚本执行效率 (Ref to [aiselp](https://github.com/aiselp/AutoX/commit/7c41af6d2b9b36d00440a9c8b7e971d025f98327))
* `优化` 重构无障碍服务相关代码以增强无障碍服务的功能稳定性 (实验性) _[`issue #167`](http://issues.autojs6.com/167)_
* `优化` UiObject 和 UiObjectCollection 的打印输出格式
* `优化` 构建工具在构建环境 Gradle JDK 版本不满足要求时作出升级提示
* `优化` 部分依赖或本地库版本调整 _[`CHANGELOG.md`](http://project.autojs6.com/blob/master/app/src/main/assets/doc/CHANGELOG.md#v642)_

# v6.4.1

###### 2023/11/02

* `修复` 构建工具无法自适应未知平台的问题 (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #158`](http://pr.autojs6.com/158)_
* `修复` 脚本退出时可能导致应用崩溃的问题 _[`issue #159`](http://issues.autojs6.com/159)_
* `修复` http 模块获取响应对象的 body.contentType 返回值类型错误 _[`issue #142`](http://issues.autojs6.com/142)_
* `修复` device.width 及 device.height 返回数据不正确的问题 _[`issue #160`](http://issues.autojs6.com/160)_
* `修复` 代码编辑器长按删除时可能导致应用崩溃的问题 (试修) _[`issue #156`](http://issues.autojs6.com/156)_
* `修复` 代码编辑器反向选择文本后进行常规操作可能导致应用崩溃的问题
* `修复` 部分设备长按 AutoJs6 应用图标无法显示快捷方式菜单的问题
* `修复` 部分设备打包项目时点击确认按钮无响应的问题
* `修复` app.sendBroadcast 及 app.startActivity 无法使用简称参数的问题
* `修复` floaty 模块 JsWindow#setPosition 等方法首次调用时的功能异常
* `优化` 增加 Termux 相关权限以支持 Intent 调用 Termux 执行 ADB 命令 _[`issue #136`](http://issues.autojs6.com/136)_
* `优化` http 模块获取的响应对象可重复使用 body.string() 及 body.bytes() 方法
* `优化` 增加 GitHub Actions 自动打包支持 (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #158`](http://pr.autojs6.com/158)_
* `优化` 构建工具自适应 Temurin 平台
* `优化` 部分依赖或本地库版本调整 _[`CHANGELOG.md`](http://project.autojs6.com/blob/master/app/src/main/assets/doc/CHANGELOG.md#v641)_

##### 更多版本历史可参阅

* [CHANGELOG.md](http://changelog.autojs6.com)

******

### 项目编译构建

******

如需对 AutoJs6 开源项目进行调试或开发, 可使用 Android Studio 或 [IntelliJ IDEA](https://www.jetbrains.com/idea/) ([Jetbrains](https://www.jetbrains.com/) 公司产品).

本小节以 Android Studio 为例介绍 AutoJs6 开源项目的编译构建方法, IntelliJ IDEA 与之类似.

#### Android Studio 准备

下载 `Android Studio Hedgehog | 2023.1.1` 版本 (按需选择其一):

- [android-studio-2023.1.1.26-windows.exe](https://redirector.gvt1.com/edgedl/android/studio/install/2023.1.1.26/android-studio-2023.1.1.26-windows.exe) (1.1 GB)
- [android-studio-2023.1.1.26-windows.zip](https://redirector.gvt1.com/edgedl/android/studio/ide-zips/2023.1.1.26/android-studio-2023.1.1.26-windows.zip) (1.1 GB)

> 注: 上述版本发布时间为 2023 年 11 月 30 日. 如需下载其他版本, 或上述链接已失效, 可访问 [Android Studio 发行版本归档](https://developer.android.com/studio/archive?hl=en) 页面.

安装或解压上述文件, 运行 Android Studio 软件 (如 `"D:\android-studio\bin\studio64.exe"`).

#### Android SDK 准备

> 注: 如果计算机系统已安装 Android SDK (安卓软件开发工具包), 则可跳过此小节内容.

在 Android Studio 软件中使用快捷键 `CTRL + ALT + S` 打开设置页面:

```text
Appearance & Behavior (外观与表现) -> 
System Settings (系统设置) -> 
Android SDK (安卓软件开发工具包)
```

`Android SDK Location (安卓软件开发工具包位置)` 处如果是空白内容, 可点击右侧 `Edit (编辑)` 按钮, 在弹出的窗口中多次点击 `Next (下一步)`.

> 注: 过程中可能需要同意一个或多个相关协议才能继续.

待相关资源下载并安装完毕, 点击 `Finish (完成)` 按钮.  
上述 `Android SDK Location (安卓软件开发工具包位置)` 处将自动完成路径填写, SDK 准备工作随即完成.

#### Android SDK Tools 准备

AutoJs6 需要使用部分 SDK 工具 (如 NDK 及 CMake).

> 注: 如果计算机系统已安装 AutoJs6 全部所需的 Android SDK Tools, 则可跳过此小节内容.

在 Android Studio 软件中使用快捷键 `CTRL + ALT + S` 打开设置页面:

```text
Appearance & Behavior (外观与表现) -> 
System Settings (系统设置) -> 
Android SDK (安卓软件开发工具包) -> 
SDK Tools (SDK 工具) (位于右侧窗口)
```

勾选 `Show Package Details (显示包详情)`, 依次点击 NDK 及 CMake, 确保相应版本的工具已勾选 (截至 2023 年 10 月 30 日, NDK 所需版本为 `21.1.6352462`, CMake 所需版本为 `3.10.2`).

SDK 工具的版本信息位于 AutoJs6 项目根目录的 `version.properties` 文件中.

#### JDK 准备

AutoJs6 项目依赖的 `JDK (Java 开发工具包)` 发行版本不低于 `17`, 但建议不低于 `19`.

需额外留意, 截至 2023 年 10 月 30 日, AutoJs6 暂不支持 JDK 最新版本 `21`.

> 注: 如果计算机系统已安装 JDK 且版本满足上述要求, 则可跳过此小节内容.

JDK 可使用 IDE 直接下载, 或访问 [Oracle 网站](https://www.oracle.com/java/technologies/downloads/) 下载.

在 Android Studio 软件中使用快捷键 `CTRL + ALT + S` 打开设置页面:

```text
Build, Execution, Deployment (构建, 执行, 开发) ->
Build Tools (构建工具) -> 
Gradle
```

`Gradle JDK` 处可选择或添加不同版本的 JDK.

如果列表中已存在合适版本的 JDK (>= `17`), 则直接选择即可.  
否则可以选择 `Download JDK (下载 JDK)` 下载合适的 JDK, 点击 `Download (下载)` 按钮并等待下载完成.  
也可以选择 `Add JDK (添加 JDK)` 添加已存在的本地 JDK, 定位其目录并完成 JDK 添加.

#### AutoJs6 资源克隆

在 Android Studio 主页面点击 `Get from VCS (从版本控制系统获取)` 按钮.  
`URL (统一资源定位地址)` 处填入 `https://github.com/SuperMonster003/AutoJs6.git`,  
`Directory (目录)` 处可根据需要修改为特定路径.  
点击 `Clone (克隆)` 按钮, 等待 AutoJs6 项目资源在设备本地完成克隆.

> 注: 上述过程可能需要安装 [Git (分布式版本控制系统)](https://git-scm.com/download).

#### AutoJs6 项目构建

克隆完成后, Android Studio 将打开 AutoJs6 的项目窗口, 并自动完成初步的 `Dependencies (依赖)` 下载及 Gradle 构建工作.

> 注: 上述过程可能非常耗时. 若网络条件欠佳, 可能需要重试多次 (点击 Retry 按钮).

构建完成后, Android Studio 的 `Build` 标签页将出现类似 `BUILD SUCCESSFUL in 1h 17m 34s` 的消息.

打包项目并生成可安装到安卓设备的 APK 文件:

- 调试版 (Debug Version)
    - `Build (构建)` -> `Build Bundle(s) / APK(s)` -> `Build APK(s)`
    - 生成带默认签名的调试版安装包
    - 路径示例: `"D:\AutoJs6\app\build\outputs\apk\debug\"`
- 发布版 (Release Version)
    - `Build (构建)` -> `Generate Signed Bundle / APK`
    - 选择 `APK` 选项
    - 准备好签名文件 (新建或选取), 生成已签名的发布版安装包
    - 路径示例: `"D:\AutoJs6\app\release\"`

> 参阅: [Android Docs](https://developer.android.com/studio/run?hl=zh-cn)

******

### 脚本开发辅助

******

开发 AutoJs6 可运行的脚本, 需使用合适的开发工具:

- [VSCode](https://code.visualstudio.com/download) / [WebStorm](https://www.jetbrains.com/webstorm/download/) / [HBuilderX](https://www.dcloud.io/hbuilderx.html) ...

如需在 PC 上进行脚本编写与调试, VSCode 插件可以实现 PC 与手机的互联:

- [AutoJs6-VSCode-Extension](http://vscext-project.autojs6.com) - AutoJs6 调试器 (VSCode 平台插件)

使用开发工具编写代码时, 代码智能补全功能可以更好地辅助开发者完成代码编写:

- [AutoJs6-TypeScript-Declarations](http://dts-project.autojs6.com) - AutoJs6 声明文件 (代码智能补全)

编写代码时, AutoJs6 相关 API 及使用方式, 可随时查阅应用文档:

- [AutoJs6-Documentation](http://docs-project.autojs6.com) - AutoJs6 应用文档

现有的脚本开发项目可作为参考, 激发个人脚本项目的创作灵感:

- [Ant-Forest](https://github.com/TonyJiangWJ/Ant-Forest) - 蚂蚁森林能量自动收取脚本 by [TonyJiangWJ](https://github.com/TonyJiangWJ)
- [Ant-Forest](https://github.com/SuperMonster003/Ant-Forest) - 蚂蚁森林能量自动收取脚本 by [SuperMonster003](https://github.com/SuperMonster003)
- [autojs](https://github.com/e1399579/autojs) - Auto.js 实用脚本 by  [e1399579](https://github.com/e1399579)
- [autojsDemo](https://github.com/snailuncle/autojsDemo) - Auto.js 演示示例 by  [snailuncle](https://github.com/snailuncle)
- [autojs 相关仓库](https://github.com/topics/autojs) - GitHub 与 autojs 话题相关的全部仓库

******

### 贡献参与

******

感谢每一位参与 AutoJs6 项目开发的贡献人员.

|                         贡献人员                          |                                       提交数                                       |     最近提交     |
|:-----------------------------------------------------:|:-------------------------------------------------------------------------------:|:------------:|
|       [LZX284](https://github.com/LZX284) (Ai)        |     [17](https://github.com/SuperMonster003/AutoJs6/commits?author=LZX284)      | `2023/11/19` |
|     [TonyJiangWJ](https://github.com/TonyJiangWJ)     |   [4](https://github.com/SuperMonster003/AutoJs6/commits?author=TonyJiangWJ)    | `2023/10/31` |
| [little-alei](https://github.com/little-alei) (抠脚本人)  |   [12](https://github.com/SuperMonster003/AutoJs6/commits?author=little-alei)   | `2023/07/12` |
|          [aiselp](https://github.com/aiselp)          | [6](https://github.com/SuperMonster003/AutoJs6/pulls?q=is%3Apr+author%3Aaiselp) | `2023/06/14` |
|        [LYS86](https://github.com/LYS86) (LYS)        |      [2](https://github.com/SuperMonster003/AutoJs6/commits?author=LYS86)       | `2023/06/03` |

数据更新于 `2023/12/02`.

数据条目按 `最近提交` 降序排序.

新发起的暂未处理的 Pull Request, 将在合并处理后加入数据统计.

部分贡献人员在 [GitHub Contributors](https://github.com/SuperMonster003/AutoJs6/graphs/contributors) 未能正常出现, 其提交记录为空, 仍可通过 [Pull Request](https://github.com/SuperMonster003/AutoJs6/pulls) 查看贡献记录.

[//]: # (
    # --------------------------------------------------------------#
    # Before committing and pushing to the remote GitHub repository #
    # --------------------------------------------------------------#
    - CHANGELOG.md
        - Update entries for AutoJs6 by checking all changed files
        - Update entries for Gradle plugins [ implementation ]
        - Update version name and released date
        - Append related GitHub issues to changelog entries
    - README.md
        - Latest changelog was synchronized by which in CHANGELOG.md
        - Changelog entries are not more than three
        - The summary of the latest changelog for committing to Git [ DO NOT commit or push ]
        - Update badges like [ android studio / rhino / ... ]
        - Update android studio download links and version names
        - Update contribution section
    - Remove the part like [ alpha / beta / ... ] of VERSION_NAME in version.properties
    - Update dependencies TypeScript declarations if needed.
    - Re-generate documentation by running the python script
    - Check the two-way versions for AutoJs6 and VSCode ext, then publish the ext to Microsoft
    - Run Gradle task "app:assembleInrtRelease"
    - Build APK to determine the final VERSION_BUILD field
    - Run Gradle task "app:appendDigestToReleasedFiles"
    - Commit and push to GitHub
    - Publish the latest release with signed APKs
)
