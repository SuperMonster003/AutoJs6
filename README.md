<!--suppress HtmlDeprecatedAttribute -->

<div align="center">
  <p>
    <img alt="AF_Banner" src="https://raw.githubusercontent.com/SuperMonster002/Hello-Sockpuppet/master/autojs6-banner_800%C3%97224_transparent.png"/>
  </p>

  <p>Android 平台支持无障碍服务的 JavaScript 自动化工具</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6/releases/latest"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6?color=009688"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6/commit/99a1d8490fac5b6d55f6f183db59ad833a2064ed"><img alt="Created" src="https://img.shields.io/date/1636632233?color=2e7d32&label=created"/></a>
    <br>
    <a href="https://github.com/mozilla/rhino"><img alt="Rhino" src="https://img.shields.io/badge/rhino-1.7.15--snapshot-F06292"/></a>
    <a href="https://developer.android.com/studio/archive"><img alt="Android Studio" src="https://img.shields.io/badge/android%20studio-flamingo 2022.2.1 canary 9-B64FC8"/></a>
    <br>
    <a href="https://www.codefactor.io/repository/github/SuperMonster003/AutoJs6"><img alt="CodeFactor Grade" src="https://www.codefactor.io/repository/github/SuperMonster003/AutoJs6/badge"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6/find/master"><img alt="GitHub Code Size" src="https://img.shields.io/github/languages/code-size/SuperMonster003/AutoJs6?color=795548"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6?color=534BAE"/></a>
  </p>
</div>

******

### 简介

* Android 平台支持无障碍服务的 JavaScript 自动化工具
* 需要 [Android 7.0](https://zh.wikipedia.org/wiki/Android_Nougat) ([API](https://developer.android.com/guide/topics/manifest/uses-sdk-element#ApiLevels) [24](https://developer.android.com/reference/android/os/Build.VERSION_CODES#N)) 及以上
* 克隆 (clone) 自 [hyb1996/Auto.js](https://github.com/hyb1996/Auto.js)

******

### 指南

******

* [项目文档](https://supermonster003.github.io/AutoJs6-Documentation/)

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

* 多语言适配 (西/法/俄/阿/日/韩/英/简中/繁中等)

* 夜间模式适配 (设置页面/文档页面/布局分析页面/悬浮窗等)

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

# v6.2.0

###### 2023/01/21

* `新增` 重新设计及编写项目文档 (部分完成)
* `新增` 西/法/俄/阿/日/韩/英/繁中等多语言适配
* `新增` 工作路径设置选项增加路径选择/历史记录/默认值智能提示等功能
* `新增` 文件管理器支持任意目录的上一级跳转 (直至 "内部存储" 目录)
* `新增` 文件管理器支持将任意目录快捷设置为工作路径
* `新增` 版本更新忽略及管理已忽略更新功能
* `新增` 文本编辑器支持双指缩放调节字体大小
* `新增` idHex 选择器 (UiSelector#idHex) (参阅 项目文档 > [选择器](https://supermonster003.github.io/AutoJs6-Documentation/#/uiSelectorType))
* `新增` action 选择器 (UiSelector#action) (参阅 项目文档 > [选择器](https://supermonster003.github.io/AutoJs6-Documentation/#/uiSelectorType))
* `新增` Match 系列选择器 (UiSelector#xxxMatch) (参阅 项目文档 > [选择器](https://supermonster003.github.io/AutoJs6-Documentation/#/uiSelectorType))
* `新增` 拾取选择器 (UiSelector#pickup) (参阅 项目文档 > [选择器](https://supermonster003.github.io/AutoJs6-Documentation/#/uiSelectorType))
* `新增` 控件探测 (UiObject#detect) (参阅 项目文档 > [控件节点](https://supermonster003.github.io/AutoJs6-Documentation/#/uiObjectType))
* `新增` 控件罗盘 (UiObject#compass) (参阅 项目文档 > [控件节点](https://supermonster003.github.io/AutoJs6-Documentation/#/uiObjectType))
* `新增` 全局等待方法 wait (参阅 项目文档 > [全局对象](https://supermonster003.github.io/AutoJs6-Documentation/#/global?id=m-wait))
* `新增` 全局缩放方法 cX/cY/cYx (参阅 项目文档 > [全局对象](https://supermonster003.github.io/AutoJs6-Documentation/#/global?id=m-wait))
* `新增` 全局 App 类型 (参阅 项目文档 > [应用枚举类](https://supermonster003.github.io/AutoJs6-Documentation/#/appType))
* `新增` i18n 模块 (基于 banana-i18n 的 JavaScript 多语言方案) (参阅 项目文档 > 国际化)
* `修复` 软件语言切换后可能导致的页面文字闪变及部分页面按钮功能异常
* `修复` 工作路径为一个项目时软件启动后不显示项目工具栏的问题
* `修复` 工作路径可能跟随软件语言切换而自动改变的问题 _[`issue #19`](https://github.com/SuperMonster003/AutoJs6/issues/19)_
* `修复` 定时任务启动延时显著 (试修) _[`issue #21`](https://github.com/SuperMonster003/AutoJs6/issues/21)_
* `修复` JavaScript 模块名被覆盖声明时导致存在依赖关系的内部模块无法正常使用的问题 _[`issue #29`](https://github.com/SuperMonster003/AutoJs6/issues/29)_
* `修复` 高版本安卓系统点击快速设置面板中相关图标后面板可能无法自动收起的问题 (试修) _[`issue #7`](https://github.com/SuperMonster003/AutoJs6/issues/7)_
* `修复` 高版本安卓系统可能出现部分页面与通知栏区域重叠的问题
* `修复` 安卓 10 及以上系统无法正常运行有关设置画笔颜色的示例代码的问题
* `修复` 示例代码 "音乐管理器" 更正文件名为 "文件管理器" 并恢复正常功能
* `修复` 文件管理器下拉刷新时可能出现定位漂移的问题
* `修复` ui 模块作用域绑定错误导致部分基于 UI 的脚本无法访问组件属性的问题
* `修复` 录制脚本后的输入文件名对话框可能因外部区域点击导致已录制内容丢失的问题
* `修复` 文档中部分章节标题超出屏幕宽度时无法自动换行造成内容丢失的问题
* `修复` 文档中的示例代码区域无法正常左右滑动的问题
* `修复` 文档页面下拉刷新时表现异常且无法撤销刷新操作的问题 (试修)
* `修复` 应用初始安装后主页抽屉夜间模式开关联动失效的问题
* `修复` 系统夜间模式开启时应用启动后强制开启夜间模式的问题
* `修复` 夜间模式开启后已设置的主题色可能无法生效的问题
* `修复` 夜间模式下部分设置选项文字与背景色相同而无法辨识的问题
* `修复` 关于页面功能按钮文本长度过大导致文本显示不完全的问题
* `修复` 主页抽屉设置项标题长度过大导致文本与按钮重叠的问题
* `修复` 主页抽屉权限开关在提示消息对话框消失后可能出现状态未同步的问题
* `修复` Root 权限修改主页抽屉权限开关失败时未继续弹出 ADB 工具对话框的问题
* `修复` Root 权限显示指针位置在初次使用时提示无权限的问题
* `修复` 图标选择页面的图标元素排版异常
* `修复` 文本编辑器启动时可能因夜间模式设置导致闪屏的问题 (试修)
* `修复` 文本编辑器设置字体大小时可用最大值受限的问题
* `修复` 部分安卓系统脚本运行结束时日志中无法统计运行时长的问题
* `修复` 使用悬浮窗菜单关闭悬浮窗后重启应用时悬浮窗依然开启的问题
* `修复` 布局层次分析时长按列表项可能导致弹出菜单溢出下方屏幕的问题
* `修复` 安卓 7.x 系统在夜间模式关闭时导航栏按钮难以辨识的问题
* `修复` http.post 等方法可能出现的请求未关闭异常
* `修复` colors.toString 方法在 Alpha 通道为 0 时其通道信息在结果中丢失的问题
* `优化` 重定向 Auto.js 4.x 版本的公有类以实现尽可能的向下兼容 (程度有限)
* `优化` 合并全部项目模块避免可能的循环引用等问题 (临时移除 inrt 模块)
* `优化` Gradle 构建配置从 Groovy 迁移到 KTS
* `优化` Rhino 异常消息增加多语言支持
* `优化` 主页抽屉权限开关仅在开启时弹出提示消息
* `优化` 主页抽屉布局紧贴于状态栏下方避免顶部颜色条的低兼容性
* `优化` 检查更新/下载更新/更新提示功能兼容安卓 7.x 系统
* `优化` 重新设计设置页面 (迁移至 AndroidX)
* `优化` 设置页面支持长按设置选项获取详细信息
* `优化` 夜间模式增加 "跟随系统" 设置选项 (安卓 9 及以上)
* `优化` 应用启动画面适配夜间模式
* `优化` 应用图标增加数字标识以提升多个开源版本共存用户的使用体验
* `优化` 主题色增加更多 Material Design Color (材料设计颜色) 选项
* `优化` 文件管理器/任务面板等列表项图标适当轻量化并适配主题色
* `优化` 主页搜索框的提示文本颜色适配夜间模式
* `优化` 对话框/文本/Fab/AppBar/列表项等部件适配夜间模式
* `优化` 文档/设置/关于/主题色/布局分析等页面及悬浮窗适配夜间模式
* `优化` 页面布局尽可能兼容 RTL (Right-To-Left) 布局
* `优化` 关于页面增加图标动画效果
* `优化` 关于页面版权声明文本自动更新年份信息
* `优化` 应用初始安装后自动决定并设置合适的工作目录
* `优化` 禁用文档页面双指缩放功能避免文档内容显示异常
* `优化` 任务面板列表项按相对路径简化显示任务的名称及路径
* `优化` 文本编辑器按钮文本适当缩写避免文本内容溢出
* `优化` 文本编辑器设置字体大小支持恢复默认值
* `优化` 提升悬浮窗点击响应速度
* `优化` 点击悬浮窗布局分析按钮直接进行布局范围分析
* `优化` 布局分析主题自适应 (悬浮窗跟随应用主题, 快速设置面板跟随系统主题)
* `优化` 布局控件信息列表按可能的使用频率重新排序
* `优化` 布局控件信息点击复制时根据选择器类型自动优化输出格式
* `优化` 使用悬浮窗选择文件时按返回键可返回至上级目录而非直接关闭悬浮窗
* `优化` 客户端模式连接计算机输入地址时支持数字有效性检测及点分符号自动转换
* `优化` 客户端及服务端建立连接后在主页抽屉显示对应设备的 IP 地址
* `优化` 部分全局对象及内置模块增加覆写保护 (参阅 项目文档 > 全局对象 > [覆写保护](https://supermonster003.github.io/AutoJs6-Documentation/#/global?id=%e8%a6%86%e5%86%99%e4%bf%9d%e6%8a%a4))
* `优化` importClass 和 importPackage 支持字符串参数及不定长参数
* `优化` ui.run 支持出现异常时打印栈追踪信息
* `优化` ui.R 及 auto.R 可便捷获取 AutoJs6 的资源 ID
* `优化` app 模块中与操作应用相关的方法支持 App 类型参数及应用别名参数
* `优化` dialogs 模块中与异步回调相关的方法支持省略预填参数
* `优化` app.startActivity 等支持 url 选项参数 (参阅 示例代码 > 应用 > 意图)
* `优化` device 模块获取 IMEI 或硬件序列号失败时返回 null 而非抛出异常
* `优化` 提升 console.show 显示的日志悬浮窗文字亮度以增强内容辨识度
* `优化` ImageWrapper#saveTo 支持相对路径保存图像文件
* `优化` 重新设计 colors 全局对象并增加 HSV / HSL 等色彩模式支持 (参阅 项目文档 > [颜色](https://supermonster003.github.io/AutoJs6-Documentation/#/color))
* `优化` 部分依赖或本地库版本调整 _[`CHANGELOG.md`](https://github.com/SuperMonster003/AutoJs6/blob/master/app/src/main/assets/doc/CHANGELOG.md#v620)_

# v6.1.1

###### 2022/05/31

* `新增` 检查更新/下载更新/更新提示功能 (参阅 设置页面) (暂不支持安卓 7.x 系统)
* `修复` 应用在安卓 10 系统无法读写外部存储的问题 _[`issue #17`](https://github.com/SuperMonster003/AutoJs6/issues/17)_
* `修复` 编辑器页面长按时可能导致应用崩溃的问题 _[`issue #18`](https://github.com/SuperMonster003/AutoJs6/issues/18)_
* `修复` 编辑器页面长按菜单 "删除行" 和 "复制行" 功能无效的问题
* `修复` 编辑器页面选项菜单中 "粘贴" 功能缺失的问题
* `优化` 部分异常消息字符串资源化 (en / zh)
* `优化` 调整内容未保存对话框的按钮布局并增加颜色区分
* `优化` 部分依赖或本地库版本调整 _[`CHANGELOG.md`](https://github.com/SuperMonster003/AutoJs6/blob/master/app/src/main/assets/doc/CHANGELOG.md#v611)_

# v6.1.0

###### 2022/05/26 - 包名变更 谨慎升级

* `提示` 修改应用包名为 org.autojs.autojs6 避免与开源 Auto.js 应用包名冲突
* `新增` 主页抽屉增加 "投影媒体权限" 开关 (Root / ADB 方式) (开关状态检测为实验性)
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
* `优化` 部分依赖或本地库版本调整 _[`CHANGELOG.md`](https://github.com/SuperMonster003/AutoJs6/blob/master/app/src/main/assets/doc/CHANGELOG.md#v610)_

##### 更多版本历史可参阅

* [CHANGELOG.md](https://github.com/SuperMonster003/AutoJs6/blob/master/app/src/main/assets/doc/CHANGELOG.md)

******

### 相关项目

******

* [Ant-Forest](https://github.com/SuperMonster003/Ant-Forest) { author: [SuperMonster003](https://github.com/SuperMonster003) }
    - `蚂蚁森林能量自动收取脚本 (AutoJs6 示例脚本项目)`
* 
* [Auto.js](https://github.com/TonyJiangWJ/Auto.js) { author: [TonyJiangWJ](https://github.com/TonyJiangWJ) }
    - `安卓平台 JavaScript 自动化工具 (Auto.js 二次开发项目)`

* [AutoX](https://github.com/kkevsekk1/AutoX) { author: [kkevsekk1](https://github.com/kkevsekk1) }
    - `安卓平台 JavaScript 自动化工具 (Auto.js 二次开发项目)`

* [AutoJs6-VSCode-Extension](https://github.com/SuperMonster003/AutoJs6-VSCode-Extension) { author: [SuperMonster003](https://github.com/SuperMonster003) }
    - `适用于 VSCode 的桌面开发插件 (AutoJs6 插件项目)`

[comment]: <> (* [Auto.js-TypeScript-Declarations]&#40;https://github.com/SuperMonster003/Auto.js-TypeScript-Declarations&#41; { author: [SuperMonster003]&#40;https://github.com/SuperMonster003&#41; })

[comment]: <> (    - `Auto.js 声明文件 &#40;.d.ts&#41;`)
