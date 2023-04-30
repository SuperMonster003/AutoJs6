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

* Android 平台支持 [无障碍服务](https://developer.android.com/guide/topics/ui/accessibility/service?hl=zh-cn) 的 JavaScript 自动化工具
* 需要 Android [API](https://developer.android.com/guide/topics/manifest/uses-sdk-element#ApiLevels) [24](https://developer.android.com/reference/android/os/Build.VERSION_CODES#N) ([7.0](https://zh.wikipedia.org/wiki/Android_Nougat)) [[N](https://developer.android.com/reference/android/os/Build.VERSION_CODES#N)] 及以上操作系统
* 克隆 (clone) 自 [hyb1996/Auto.js](https://github.com/hyb1996/Auto.js)

******

### 指南

******

* [应用文档](https://docs.autojs6.com)
* [使用手册 (待编写)](https://docs.autojs6.com/#/manual)
* [疑难解答](https://docs.autojs6.com/#/qa)
* [项目编译构建](#项目编译构建)
* [脚本开发辅助](#脚本开发辅助)

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
* 支持与 VSCode 连接并进行桌面开发 (需要 [AutoJs6-VSCode-Extension](http://vscext-project.autojs6.com) 插件)

******

### 主要变更

******

* VSCode 插件支持客户端 (LAN) 及服务端 (LAN/ADB) 连接方式

* 多语言适配 (西/法/俄/阿/日/韩/英/简中/繁中等)

* 夜间模式适配 (设置页面/文档页面/布局分析页面/悬浮窗等)

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

# v6.3.0

###### 2023/04/29

* `新增` ocr 模块 (参阅 项目文档 > [光学字符识别](https://docs.autojs6.com/#/ocr)) _[`issue #8`](http://issues.autojs6.com/8)_
* `新增` notice 模块 (参阅 项目文档 > [消息通知](https://docs.autojs6.com/#/notice))
* `新增` s13n 模块 (参阅 项目文档 > [标准化](https://docs.autojs6.com/#/s13n))
* `新增` Color 模块 (参阅 项目文档 > [颜色类](https://docs.autojs6.com/#/colorType))
* `新增` 前台时保持屏幕常亮功能及设置选项
* `新增` 额外的文档启动器 (launcher) 便于独立阅读应用文档 (支持在设置中隐藏或显示)
* `修复` colors.toString 方法功能异常
* `修复` app.openUrl 方法自动添加协议前缀功能异常
* `修复` app.viewFile/editFile 在参数对应文件不存在时的行为异常
* `修复` pickup 方法的回调函数无法被调用的问题
* `修复` 布局分析显示的控件信息 bounds 属性值负数符号被替换为逗号的问题
* `修复` bounds/boundsInside/boundsContains 选择器无法正常筛选狭义空矩形 (如边界倒置矩形) _[`issue #49`](http://issues.autojs6.com/49)_
* `修复` 更换主题或修改语言后点击或长按主页文档标签将导致应用崩溃的问题
* `修复` 文本编辑器双指缩放调节字体大小时可能出现抖动的问题
* `修复` 构建脚本中部分依赖源无法下载的问题 (已全部整合) _[`issue #40`](http://issues.autojs6.com/40)_
* `修复` Tasker 无法添加 AutoJs6 操作插件 (Action Plugin) 的问题 (试修) _[`issue #41`](http://issues.autojs6.com/41)_
* `修复` 高版本 JDK 编译项目时 ButterKnife 注解无法解析资源 ID 的问题 _[`issue #48`](http://issues.autojs6.com/48)_
* `修复` 无障碍服务较大概率出现服务异常的问题 (试修)
* `修复` images.medianBlur 的 size 参数使用方式与文档不符的问题
* `修复` engines 模块显示脚本全称时文件名与扩展名之间句点符号丢失的问题
* `修复` 加权 RGB 距离检测算法内部实现可能存在的计算失误 (试修)
* `修复` console 模块的浮动窗口相关方法无法在 show 方法之前使用的问题
* `修复` console.setSize 等方法可能无法生效的问题 _[`issue #50`](http://issues.autojs6.com/50)_
* `修复` colors.material 颜色空间的颜色常量赋值错误
* `修复` UI 模式的日期选择控件 minDate 及 maxDate 属性无法正确解析日期格式的问题
* `修复` 运行脚本后快速切换到主页 "任务" 标签页面将出现两个相同运行中任务的问题
* `修复` 文件管理页面从其他页面返回时页面状态可能被重置的问题 _[`issue #52`](http://issues.autojs6.com/52)_
* `修复` 文件管理页面排序状态与图标显示状态不符的问题
* `优化` 文件管理页面增加文件及文件夹修改时间显示
* `优化` 文件管理页面排序类型支持状态记忆
* `优化` README.md 添加项目编译构建小节与脚本开发辅助小节 _[`issue #33`](http://issues.autojs6.com/33)_
* `优化` images 模块相关方法的区域 (region) 选项参数支持更多传入方式 (参阅 项目文档 > [全能类型](https://docs.autojs6.com/#/omniTypes?id=omniregion))
* `优化` app.startActivity 页面简写参数增加 pref/homepage/docs/about 等形式的支持
* `优化` web 模块的全局方法挂载到模块本身以增强可用性 (参阅 项目文档 > [万维网](https://docs.autojs6.com/#/web))
* `优化` web.newInjectableWebView 方法内部默认实现部分常用的 WebView 设置选项
* `优化` colors 模块添加多种转换方法及工具方法并添加更多静态常量以及可直接作为参数的颜色名称
* `优化` console 模块添加多种控制台浮动窗口的样式配置方法并支持 build 构建器统一配置窗口样式
* `优化` 控制台浮动窗口支持拖动标题区域移动窗口位置
* `优化` 控制台浮动窗口支持脚本结束后自动延迟关闭
* `优化` 控制台浮动窗口及其 Activity 活动窗口支持双指缩放调整字体大小
* `优化` http 模块相关方法支持超时参数 (timeout)
* `优化` Gradle 构建脚本支持 JDK 版本主动降级 (fallback)
* `优化` Gradle 构建脚本支持根据平台类型及版本自动选择合适的构建工具版本 (程度有限)
* `优化` 部分依赖或本地库版本调整 _[`CHANGELOG.md`](http://project.autojs6.com/blob/master/app/src/main/assets/doc/CHANGELOG.md#v630)_

# v6.2.0

###### 2023/01/21

* `新增` 重新设计及编写项目文档 (部分完成)
* `新增` 西/法/俄/阿/日/韩/英/繁中等多语言适配
* `新增` 工作路径设置选项增加路径选择/历史记录/默认值智能提示等功能
* `新增` 文件管理器支持任意目录的上一级跳转 (直至 "内部存储" 目录)
* `新增` 文件管理器支持将任意目录快捷设置为工作路径
* `新增` 版本更新忽略及管理已忽略更新功能
* `新增` 文本编辑器支持双指缩放调节字体大小
* `新增` idHex 选择器 (UiSelector#idHex) (参阅 项目文档 > [选择器](https://docs.autojs6.com/#/uiSelectorType))
* `新增` action 选择器 (UiSelector#action) (参阅 项目文档 > [选择器](https://docs.autojs6.com/#/uiSelectorType))
* `新增` Match 系列选择器 (UiSelector#xxxMatch) (参阅 项目文档 > [选择器](https://docs.autojs6.com/#/uiSelectorType))
* `新增` 拾取选择器 (UiSelector#pickup) (参阅 项目文档 > [选择器](https://docs.autojs6.com/#/uiSelectorType)) _[`issue #22`](http://issues.autojs6.com/22)_
* `新增` 控件探测 (UiObject#detect) (参阅 项目文档 > [控件节点](https://docs.autojs6.com/#/uiObjectType))
* `新增` 控件罗盘 (UiObject#compass) (参阅 项目文档 > [控件节点](https://docs.autojs6.com/#/uiObjectType)) _[`issue #23`](http://issues.autojs6.com/23)_
* `新增` 全局等待方法 wait (参阅 项目文档 > [全局对象](https://docs.autojs6.com/#/global?id=m-wait))
* `新增` 全局缩放方法 cX/cY/cYx (参阅 项目文档 > [全局对象](https://docs.autojs6.com/#/global?id=m-wait))
* `新增` 全局 App 类型 (参阅 项目文档 > [应用枚举类](https://docs.autojs6.com/#/appType))
* `新增` i18n 模块 (基于 banana-i18n 的 JavaScript 多语言方案) (参阅 项目文档 > 国际化)
* `修复` 软件语言切换后可能导致的页面文字闪变及部分页面按钮功能异常
* `修复` 工作路径为一个项目时软件启动后不显示项目工具栏的问题
* `修复` 工作路径可能跟随软件语言切换而自动改变的问题 _[`issue #19`](http://issues.autojs6.com/19)_
* `修复` 定时任务启动延时显著 (试修) _[`issue #21`](http://issues.autojs6.com/21)_
* `修复` JavaScript 模块名被覆盖声明时导致存在依赖关系的内部模块无法正常使用的问题 _[`issue #29`](http://issues.autojs6.com/29)_
* `修复` 高版本安卓系统点击快速设置面板中相关图标后面板可能无法自动收起的问题 (试修) _[`issue #7`](http://issues.autojs6.com/7)_
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
* `优化` 部分全局对象及内置模块增加覆写保护 (参阅 项目文档 > 全局对象 > [覆写保护](https://docs.autojs6.com/#/global?id=%e8%a6%86%e5%86%99%e4%bf%9d%e6%8a%a4))
* `优化` importClass 和 importPackage 支持字符串参数及不定长参数
* `优化` ui.run 支持出现异常时打印栈追踪信息
* `优化` ui.R 及 auto.R 可便捷获取 AutoJs6 的资源 ID
* `优化` app 模块中与操作应用相关的方法支持 App 类型参数及应用别名参数
* `优化` dialogs 模块中与异步回调相关的方法支持省略预填参数
* `优化` app.startActivity 等支持 url 选项参数 (参阅 示例代码 > 应用 > 意图)
* `优化` device 模块获取 IMEI 或硬件序列号失败时返回 null 而非抛出异常
* `优化` 提升 console.show 显示的日志悬浮窗文字亮度以增强内容辨识度
* `优化` ImageWrapper#saveTo 支持相对路径保存图像文件
* `优化` 重新设计 colors 全局对象并增加 HSV / HSL 等色彩模式支持 (参阅 项目文档 > [颜色](https://docs.autojs6.com/#/color))
* `优化` 部分依赖或本地库版本调整 _[`CHANGELOG.md`](http://project.autojs6.com/blob/master/app/src/main/assets/doc/CHANGELOG.md#v620)_

# v6.1.1

###### 2022/05/31

* `新增` 检查更新/下载更新/更新提示功能 (参阅 设置页面) (暂不支持安卓 7.x 系统)
* `修复` 应用在安卓 10 系统无法读写外部存储的问题 _[`issue #17`](http://issues.autojs6.com/17)_
* `修复` 编辑器页面长按时可能导致应用崩溃的问题 _[`issue #18`](http://issues.autojs6.com/18)_
* `修复` 编辑器页面长按菜单 "删除行" 和 "复制行" 功能无效的问题
* `修复` 编辑器页面选项菜单中 "粘贴" 功能缺失的问题
* `优化` 部分异常消息字符串资源化 (en / zh)
* `优化` 调整内容未保存对话框的按钮布局并增加颜色区分
* `优化` 部分依赖或本地库版本调整 _[`CHANGELOG.md`](http://project.autojs6.com/blob/master/app/src/main/assets/doc/CHANGELOG.md#v611)_

##### 更多版本历史可参阅

* [CHANGELOG.md](http://changelog.autojs6.com)

******

### 项目编译构建

******

建议始终使用 Android Studio 进行 AutoJs6 调试或开发.

> 如需使用 [IntelliJ IDEA](https://www.jetbrains.com/idea/) ([Jetbrains](https://www.jetbrains.com/) 公司产品), 需留意以下注意事项:
> - IntelliJ IDEA 版本建议不低于 `2022.3.3 (Ultimate Edition)`
> - IntelliJ IDEA 的 Gradle JVM 版本不低于 `19`
> - 回退 Gradle 构建工具版本至 IDEA 支持的版本
>   - 如 `classpath("com.android.tools.build:gradle:7.4.0")`
>   - 具体版本可根据 IDEA 的错误提示获知
> - 回退 Kotlin 脚本 (KTS) 语法
>   - 如 ` com.android.build.api.dsl.CommonExtension#options` 需回退为 `packagingOptions`
> - ... ...

#### Android Studio 准备

下载 `Android Studio Flamingo | 2022.2.1` 版本 (按需选择其一):

- [android-studio-2022.2.1.18-windows.exe](https://redirector.gvt1.com/edgedl/android/studio/install/2022.2.1.18/android-studio-2022.2.1.18-windows.exe) (1.0 GB)
- [android-studio-2022.2.1.18-windows.zip](https://redirector.gvt1.com/edgedl/android/studio/ide-zips/2022.2.1.18/android-studio-2022.2.1.18-windows.zip) (1.0 GB)

> 注: 如需下载其他版本, 或上述链接已失效, 可前往 [Android Studio 发行版本归档](https://developer.android.com/studio/archive) 页面.

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

#### JDK 准备

AutoJs6 项目依赖的 `JDK (Java 开发工具包)` 发行版本不低于 `19`.

JDK 可使用 IDE 直接下载, 或访问 [Oracle 网站](https://www.oracle.com/java/technologies/downloads/) 下载.

> 注: 如果计算机系统已安装 JDK 且版本不低于 19, 则可跳过此小节内容.

在 Android Studio 软件中使用快捷键 `CTRL + ALT + S` 打开设置页面:

```text
Build, Execution, Deployment (构建, 执行, 开发) ->
Build Tools (构建工具) -> 
Gradle
```

`Gradle JDK` 处可选择或添加不同版本的 JDK.

- 列表中存在合适版本的 JDK
    - 直接选择即可
- 选择 `Download JDK (下载 JDK)` 下载新的 JDK
    - 选择一个版本不低于 19 的 JDK, 点击 `Download (下载)` 按钮并等待下载完成
- 选择 `Add JDK (添加 JDK)` 添加已存在的本地 JDK
    - 如计算机系统已存在合适版本的 JDK, 可定位其目录并完成 JDK 添加

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

******

### 相关项目

******

| 项目                                                                | 简介                                     | 开发者                                                   |
|-------------------------------------------------------------------|----------------------------------------|-------------------------------------------------------|
| <span style="white-space:nowrap">[AutoX (Autox.js)](https://github.com/kkevsekk1/AutoX)</span>            | <span style="white-space:nowrap">安卓平台 JavaScript 自动化工具 (Auto.js 二次开发项目)</span> | <span style="white-space:nowrap">[kkevsekk1](https://github.com/kkevsekk1)</span>             |
| <span style="white-space:nowrap">[AutoJs6-Documentation](http://docs-project.autojs6.com)</span>          | <span style="white-space:nowrap">AutoJs6 应用文档</span>                           | <span style="white-space:nowrap">[SuperMonster003](https://github.com/SuperMonster003)</span> |
| <span style="white-space:nowrap">[AutoJs6-VSCode-Extension](http://vscext-project.autojs6.com)</span>     | <span style="white-space:nowrap">AutoJs6 调试器 (VSCode 平台插件)</span>              | <span style="white-space:nowrap">[SuperMonster003](https://github.com/SuperMonster003)</span> |
| <span style="white-space:nowrap">[AutoJs6-TypeScript-Declarations](http://dts-project.autojs6.com)</span> | <span style="white-space:nowrap">AutoJs6 声明文件 (代码智能补全)</span>                  | <span style="white-space:nowrap">[SuperMonster003](https://github.com/SuperMonster003)</span> |
| <span style="white-space:nowrap">[Ant-Forest](https://github.com/SuperMonster003/Ant-Forest)</span>       | <span style="white-space:nowrap">蚂蚁森林能量自动收取脚本 (AutoJs6 示例脚本项目)</span>          | <span style="white-space:nowrap">[SuperMonster003](https://github.com/SuperMonster003)</span> |

[//]: # (
    # --------------------------------------------------------------#
    # Before committing and pushing to the remote GitHub repository #
    # --------------------------------------------------------------#
    - CHANGELOG.md
        - Update entries for AutoJs6 by checking all changed files
        - Update entries for Gradle plugins
        - Update version name and released date
        - Append related GitHub issues to changelog entries
    - README.md
        - Update badges like [ android studio / rhino / ... ]
        - Latest changelog was synchronized by which in CHANGELOG.md
        - The summary of the latest changelog for committing to Git
        - Changelog entries are not more than three
        - Update android studio download links and version names
    - Remove the part like [ alpha / beta / ... ] of VERSION_NAME in version.properties
    - Re-generate documentation by running the python script
    - Update dependencies information for Android Gradle Plugin in the top-level build.gradle.kts
    - Build APK to determine the final VERSION_BUILD field
)
