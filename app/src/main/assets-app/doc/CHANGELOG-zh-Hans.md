******

### 版本历史

******

# v6.6.3

###### 2025/05/21

* `新增` 版本历史功能, 可查看发行版本历史更新记录 (多语言) 与统计数据
* `新增` timers.keepAlive 方法 (已全局化), 用于保持脚本活跃状态
* `新增` engines.on('start/stop/error', callback) 等事件监听方法, 用于监听脚本引擎全局事件
* `新增` images.detectMultiColors 方法, 用于多点颜色校验 _[`issue #374`](http://issues.autojs6.com/374)_
* `新增` images.matchFeatures/detectAndComputeFeatures 方法, 支持全分辨率找图 (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) _[`issue #366`](http://issues.autojs6.com/366)_
* `新增` images.compressToBytes 方法, 用于压缩图像并生成字节数组
* `新增` images.downsample 方法, 用于像素降采样并生成新的 ImageWrapper
* `修复` 主页文档标签显示在线文档时部分内容被系统导航栏遮挡的问题
* `修复` 部分设备代码编辑器空行显示方框字符的问题
* `修复` 主题色设置页面调色盘对话框可能无限叠加的问题
* `修复` 无障碍服务关闭时音量加键停止所有脚本功能失效的问题
* `修复` 定时任务页面编辑自定义广播内容时出现的输入法遮挡问题
* `修复` APK 文件类型信息对话框可能无法获取应用名称及 SDK 信息的问题
* `修复` Android 15 部分页面状态栏背景颜色可能无法动态跟随主题色的问题
* `修复` dialogs 模块无法正常使用 customView 属性的问题 _[`issue #364`](http://issues.autojs6.com/364)_
* `修复` 使用 JavaAdapter 时导致 ClassLoader 调用栈溢出的问题 _[`issue #376`](http://issues.autojs6.com/376)_
* `修复` console.setContentTextColor 方法导致日志字体颜色丢失默认值的问题 _[`issue #346`](http://issues.autojs6.com/346)_
* `修复` console.setContentBackgroundColor 方法无法接受颜色名称参数的问题 _[`issue #384`](http://issues.autojs6.com/384)_
* `修复` images.compress 方法实现原理由像素降采样修正为编码质量变化
* `修复` images.resize 方法无法正常使用的问题
* `修复` README.md 中部分语言日期格式不正确的问题
* `修复` Gradle 构建脚本可能因获取到无效库档案文件长度而导致构建失败的问题 _[`issue #389`](http://issues.autojs6.com/389)_
* `优化` 布局分析支持控件隐藏 (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #371`](http://pr.autojs6.com/371)_ _[`issue #355`](http://issues.autojs6.com/355)_
* `优化` 布局分析菜单添加渐变分隔线实现一定程度的功能分组
* `优化` 主题色扩充适配范围并支持更多控件类型
* `优化` 主页抽屉在横向屏幕或超宽屏幕的宽度适应性
* `优化` 关于应用与开发者页面增加水平布局及小屏布局适配
* `优化` 设置页面对话框相关设置支持 "使用默认值" 菜单选项
* `优化` 文件管理器浮动按钮展开后点击其他区域可自动隐藏
* `优化` 打包单文件时自动读取并勾选已安装应用的声明权限 _[`issue #362`](http://issues.autojs6.com/362)_
* `优化` 意图相关操作 (编辑/查看/安装/发送/播放等) 增加操作异常提示
* `优化` ImageWrapper#saveTo 方法的路径参数支持相对路径
* `优化` images.save 方法使用 quality 参数时支持 png 格式的文件体积压缩 _[`issue #367`](http://issues.autojs6.com/367)_
* `优化` 已忽略更新记录及客户端模式连接地址记录支持清空操作
* `优化` 版本更新信息支持多语言显示 (与当前显示语言同步)
* `优化` 使用异步加载方式一定程度提升文件管理器列表滑动流畅性
* `优化` Gradle 构建脚本提升版本自适应能力 _[`discussion #369`](http://discussions.autojs6.com/369)_
* `依赖` 本地化 Material Dialogs 版本 0.9.6.0
* `依赖` 本地化 Material Date Time Picker 版本 4.2.3
* `依赖` 本地化 libimagequant 版本 2.17.0
* `依赖` 本地化 libpng 版本 1.6.49
* `依赖` 附加 Jsoup 版本 1.19.1
* `依赖` 附加 Material Progressbar 版本 1.4.2
* `依赖` 附加 Flexmark Java HTML to Markdown 版本 0.64.8
* `依赖` 升级 Gradle 版本 8.14-rc-1 -> 8.14
* `依赖` 升级 Androidx Room 版本 2.7.0 -> 2.7.1

# v6.6.2

###### 2025/04/16

* `新增` ui.statusBarAppearanceLight/statusBarAppearanceLightBy/navigationBarColor 等方法
* `新增` ui.statusBarHeight 属性 (getter), 用于获取状态栏高度 _[`issue #357`](http://issues.autojs6.com/357)_
* `新增` images.flip 方法, 用于图像翻转 _[`issue #349`](http://issues.autojs6.com/349)_
* `新增` 设置页面增加 "文件扩展名" 设置选项
* `新增` 主题色设置页面增加新布局支持 (分组/定位/搜索/历史记录/调色盘增强等)
* `修复` Android 15 状态栏背景颜色与主题色不一致的问题
* `修复` plugins.load 方法无法正常加载插件的问题 _[`issue #290`](http://issues.autojs6.com/290)_
* `修复` dx 库在 Android 7.x 无法正常使用的问题 _[`issue #293`](http://issues.autojs6.com/293)_
* `修复` ScriptRuntime 使用 require 引用内置模块时可能出现的同步状态异常 (试修) _[`issue #298`](http://issues.autojs6.com/298)_
* `修复` notice 模块缺失 getBuilder 等扩展方法的问题 _[`issue #301`](http://issues.autojs6.com/301)_
* `修复` shizuku/shell 等方法无法接受字符串参数的问题 _[`issue #310`](http://issues.autojs6.com/310)_
* `修复` colors.pixel 方法无法接受单通道图像参数的问题 _[`issue #350`](http://issues.autojs6.com/350)_
* `修复` engines.execScript/execScriptFile 等方法执行脚本时默认工作路径异常 _[`issue #358`](http://issues.autojs6.com/358)_ _[`issue #340`](http://issues.autojs6.com/340)_ _[`issue #339`](http://issues.autojs6.com/339)_
* `修复` floaty.window/floaty.rawWindow 无法在子线程执行的问题
* `修复` floaty.getClip 可能无法正常获取剪切板内容的问题 _[`issue #341`](http://issues.autojs6.com/341)_
* `修复` ui.inflate 返回值丢失 attr/on/click 等原型方法的问题
* `修复` 使用 XML 语法将 JavaScript 表达式作为属性值时, 其作用域上下文绑定错误的问题 _[`issue #319`](http://issues.autojs6.com/319)_
* `修复` 部分方法调用出现异常时无法被 try..catch 块捕获的问题 _[`issue #345`](http://issues.autojs6.com/345)_
* `修复` 布局分析页面生成代码时可能导致应用崩溃的问题 _[`issue #288`](http://issues.autojs6.com/288)_
* `修复` 打包应用无法正常使用 shizuku 模块的问题 _[`issue #227`](http://issues.autojs6.com/227)_ _[`issue #231`](http://issues.autojs6.com/231)_ _[`issue #284`](http://issues.autojs6.com/284)_ _[`issue #287`](http://issues.autojs6.com/287)_ _[`issue #304`](http://issues.autojs6.com/304)_
* `修复` 代码编辑器跳转到行尾时可能跳转到下一行起始位置的问题
* `修复` 设置页面连续快速点击对话框类型项目时可能导致应用崩溃的问题
* `优化` 精简打包应用模板 APK 文件大小
* `优化` 应用 (以及打包应用) 支持更多权限 _[`issue #338`](http://issues.autojs6.com/338)_
* `优化` 打包页面支持 Pinyin 库选项
* `优化` 打包应用主活动页面优化状态栏背景及文字颜色
* `优化` 打包应用设置页面增加访问所有文件和发送通知等特殊权限开关 _[`issue #354`](http://issues.autojs6.com/354)_
* `优化` 控件的文字及图标根据主题色亮度值自动切换合适的颜色
* `优化` 部分控件主题色与背景色对比度过低时的视觉体验
* `优化` 调色盘 HEX 输入控件增强剪贴板粘贴色值时的兼容性
* `优化` 应用内页面导航栏设置为透明或半透明以增强视觉体验
* `优化` UI 模式状态栏及导航栏默认为 `md_grey_50` 色值且设置为亮色模式
* `优化` 主页抽屉无障碍服务开关支持与脚本代码同步
* `优化` 主页文档页面搜索时支持双向查找按钮
* `优化` 主页 "文件" 标签支持通过长按切换浮动按钮可见状态
* `优化` 代码编辑器标题文字支持字体大小自适应
* `优化` 日志页面浮动按钮可见状态与列表滚动操作联动
* `优化` 脚本项目配置文件 project.json 支持更多打包选项 _[`issue #305`](http://issues.autojs6.com/305)_ _[`issue #306`](http://issues.autojs6.com/306)_
* `优化` 脚本项目配置文件 project.json 支持选项名称宽松匹配及别名兼容
* `优化` APK 文件类型信息对话框增加文件大小与签名方案信息
* `优化` APK 文件类型信息对话框增加点击监听器支持文本复制与应用详情跳转
* `优化` 尝试恢复 com.stardust 前缀包以便提升代码兼容性 _[`issue #290`](http://issues.autojs6.com/290)_
* `优化` floaty.window/floaty.rawWindow 同时支持主线程和子线程执行
* `优化` getClip 全局方法适时借助 floaty.getClip 方法以提升兼容性
* `优化` files.path 及相关方法传入空值路径参数时的兼容性
* `优化` 同步最新的 Rhino 引擎官方上游代码并进行必要的代码适配
* `优化` README.md 完善项目构建与运行相关内容 _[`issue #344`](http://issues.autojs6.com/344)_
* `依赖` 附加 Eclipse Paho Client Mqttv3 版本 1.1.0 _[`issue #330`](http://issues.autojs6.com/330)_
* `依赖` 升级 Gradle Compile 版本 34 -> 35
* `依赖` 升级 Gradle 版本 8.12 -> 8.14-rc-1
* `依赖` 升级 Rhino 版本 1.8.0-SNAPSHOT -> 1.8.1-SNAPSHOT
* `依赖` 升级 Androidx Recyclerview 版本 1.3.2 -> 1.4.0
* `依赖` 升级 Androidx Room 版本 2.6.1 -> 2.7.0
* `依赖` 升级 Androidx WebKit 版本 1.12.1 -> 1.13.0
* `依赖` 升级 Pinyin4j 版本 2.5.0 -> 2.5.1

# v6.6.1

###### 2025/01/01

* `新增` pinyin 模块, 用于汉语拼音转换 (参阅 项目文档 > [汉语拼音](https://docs.autojs6.com/#/pinyin))
* `新增` pinyin4j 模块, 用于汉语拼音转换 (参阅 项目文档 > [汉语拼音](https://docs.autojs6.com/#/pinyin4j))
* `新增` UiObject#isSimilar 及 UiObjectCollection#isSimilar 方法, 用于确定控件或控件集合是否相似
* `新增` currentComponent 全局方法, 用于获取当前活动的组件名称信息
* `修复` 部分环境因回退版本过低而无法正常编译项目的问题
* `修复` 调用不存在的方法时可能出现的 "非原始类型值" 异常
* `修复` 部分设备无法正常添加脚本快捷方式的问题 (试修) _[`issue #221`](http://issues.autojs6.com/221)_
* `修复` automator.click/longClick 方法参数类型限制错误 _[`issue #275`](http://issues.autojs6.com/275)_
* `修复` 选择器不支持 ConsString 类型参数的问题 _[`issue #277`](http://issues.autojs6.com/277)_
* `修复` UiObjectCollection 实例缺失自身方法及属性的问题
* `优化` 打包页面支持签名配置/密钥库管理/权限设置 (by [luckyloogn](https://github.com/luckyloogn)) _[`pr #286`](http://pr.autojs6.com/286)_
* `优化` 提升浮动窗口当前包名及当前活动名的识别准确性 (优先级: Shizuku > Root > A11Y)
* `优化` 提升 currentPackage/currentActivity 识别准确性 (优先级: Shizuku > Root > A11Y)
* `优化` 恢复日志活动窗口单个条目文本内容的双击或长按选择功能 _[`issue #280`](http://issues.autojs6.com/280)_
* `优化` 脚本项目识别在 project.json 损坏情况下尽可能还原关键信息
* `优化` 打包单文件时自动生成的包名后缀支持将简体中文转换为拼音 (支持多音字)
* `优化` UiSelector#findOnce 及 UiSelector#find 方法支持负数参数
* `优化` app.startActivity/startDualActivity 方法增强适配性
* `优化` UI 元素及 className 相关选择器支持更多的包名前缀省略形式 (如 RecyclerView, Snackbar 等)
* `优化` 同步最新的 Rhino 引擎官方上游代码并进行必要的代码适配
* `依赖` 附加 Pinyin4j 版本 2.5.0
* `依赖` 附加 Jieba Analysis 版本 1.0.3-SNAPSHOT (modified)
* `依赖` 升级 Gradle 版本 8.11.1 -> 8.12

# v6.6.0

###### 2024/12/02 - 内置模块重写, 谨慎升级

* `提示` 内置模块使用 Kotlin 重新编写以提升脚本运行效率但可能需要多次迭代逐步完善
* `提示` 内置 init.js 文件默认为空但支持开发者自行扩展内置模块或挂载外部模块
* `新增` axios 模块 / cheerio 模块 (Ref to [AutoX](https://github.com/kkevsekk1/AutoX))
* `新增` sqlite 模块, 用于 SQLite 数据库简单操作 (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (参阅 项目文档 > [SQLite](https://docs.autojs6.com/#/sqlite))
* `新增` mime 模块, 用于处理和解析 MIME 类型字符串 (参阅 项目文档 > [MIME](https://docs.autojs6.com/#/mime))
* `新增` nanoid 模块, 可作为字符串 ID 生成器 (Ref to [ai/nanoid](https://github.com/ai/nanoid))
* `新增` sysprops 模块, 用于获取运行时环境配置数据 (参阅 项目文档 > [系统属性](https://docs.autojs6.com/#/sysprops))
* `新增` ocr 模块支持 [Rapid OCR](https://github.com/RapidAI/RapidOCR) 引擎
* `新增` 布局分析支持切换窗口 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` auto.clearCache 方法, 支持清除控件缓存 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` threads.pool 方法, 支持线程池简单应用 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` images.matchTemplate 方法增加 useTransparentMask 选项参数, 支持透明找图 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` images.requestScreenCaptureAsync 方法, 用于 UI 模式异步方式申请截图权限 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` images.requestScreenCapture 方法增加 isAsync 选项参数, 支持异步方式获取屏幕截图 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` images.on('screen_capture', callback) 等事件监听方法, 支持监听屏幕截图可用事件 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` images.stopScreenCapture 方法, 支持主动释放截图申请的相关资源 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` images.psnr/mse/ssim/mssim/hist/ncc 及 images.getSimilarity 方法, 用于获取图像相似性度量值
* `新增` images.isGrayscale 方法, 用于判断图像是否为灰度图像
* `新增` images.invert 方法, 用于反色图像转换
* `新增` s13n.point/time 方法, 用于标准化点对象及时长对象 (参阅 项目文档 > [标准化](https://docs.autojs6.com/#/s13n))
* `新增` console 模块 gravity (重力), touchThrough (穿透点击), backgroundTint (背景着色) 等相关方法 (参阅 项目文档 > [控制台](https://docs.autojs6.com/#/console))
* `新增` Mathx.randomInt/Mathx.randomFloat 方法, 用于返回指定范围内的随机整数或随机浮点数
* `新增` app.launchDual/startDualActivity 等方法, 用于处理双开应用 (需要 Shizuku 或 Root 权限) (实验性)
* `新增` app.kill 方法, 用于强制停止应用 (需要 Shizuku 或 Root 权限)
* `新增` floaty.getClip 方法, 用于借助浮动窗口聚焦间接获取剪切板内容
* `修复` Fragment 子类 (如 [DrawerFragment](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/ui/main/drawer/DrawerFragment.kt#L369) / [ExplorerFragment](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/ui/main/scripts/ExplorerFragment.kt#L48) 等) 中存在的 View Binding 内存泄漏
* `修复` [ScreenCapture](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/core/image/capture/ScreenCapturer.java#L70) / [ThemeColorPreference](https://github.com/SuperMonster003/AutoJs6/blob/10960ddbee71f75ef80907ad5b6ab42f3e1bf31e/app/src/main/java/org/autojs/autojs/ui/settings/ThemeColorPreference.kt#L21) 等类中存在的实例内存泄漏
* `修复` Android 14 申请截图权限导致应用崩溃的问题 (by [chenguangming](https://github.com/chenguangming)) _[`pr #242`](http://pr.autojs6.com/242)_
* `修复` Android 14 开启前台服务导致应用崩溃的问题
* `修复` Android 14 代码编辑器运行按钮点击后无法正常亮起的问题
* `修复` 项目打包后应用可能因缺少必要库文件无法正常运行的问题 _[`issue #202`](http://issues.autojs6.com/202)_ _[`issue #223`](http://issues.autojs6.com/223)_ _[`pr #264`](http://pr.autojs6.com/264)_
* `修复` 编辑项目时可能因指定图标资源不存在而导致应用崩溃的问题 _[`issue #203`](http://issues.autojs6.com/203)_
* `修复` 截图权限申请时无法正常使用参数获取指定屏幕方向的截图资源
* `修复` 部分设备无法正常添加脚本快捷方式的问题 (试修) _[`issue #221`](http://issues.autojs6.com/221)_
* `修复` 调用 http 模块与发送请求相关的方法将出现累积性请求发送延迟的问题 _[`issue #192`](http://issues.autojs6.com/192)_
* `修复` Shizuku 服务在 AutoJs6 进入主活动页面之前可能无法正常使用的问题 (试修) _[`issue #255`](http://issues.autojs6.com/255)_
* `修复` random(min, max) 方法可能出现结果越界的问题
* `修复` pickup 方法结果类型参数无法正常传入空数组的问题
* `修复` UiObject#bounds() 得到的控件矩形可能被意外修改而破坏其不变性的问题
* `修复` text/button/input 元素的文本内容包含半角双引号时无法正常解析的问题
* `修复` text/textswitcher 元素的 autoLink 属性功能失效的问题
* `修复` 不同脚本可能错误地共享同一个 ScriptRuntime 对象的问题
* `修复` 全局变量 HEIGHT 及 WIDTH 丢失 getter 动态属性的问题
* `修复` 脚本启动时 RootShell 随即加载可能导致启动高延迟的问题
* `修复` 控制台浮动窗口设置背景颜色导致矩形圆角样式丢失的问题
* `修复` 无障碍服务自动启动可能出现的服务异常问题 (试修)
* `修复` 主页文档页面左右滑动 WebView 控件时可能触发 ViewPager 切换的问题
* `修复` 文件管理器无法识别包含大写字母文件扩展名的问题
* `修复` 文件管理器首次进入项目目录时可能无法自动识别项目的问题
* `修复` 文件管理器删除文件夹后页面无法自动刷新的问题
* `修复` 文件管理器排序文件及文件夹时可能出现 ASCII 首字母名称置后的问题
* `修复` 代码编辑器调试功能的 FAILED ASSERTION 异常
* `修复` 代码编辑器调试过程中关闭编辑器后无法再次正常调试的问题
* `修复` 代码编辑器跳转到行尾时可能遗漏末尾字符的问题
* `修复` 主活动页面启动日志活动页面时可能出现闪屏的问题
* `修复` 打包应用无法正常使用 opencc 模块的问题
* `优化` 打包页面中 "不可用 ABI" 控件的点击提示体验
* `优化` 支持使用 Shizuku 控制 "指针位置" 显示开关
* `优化` 支持使用 Shizuku 控制 "投影媒体" 及 "修改安全设置" 权限开关
* `优化` automator.gestureAsync/gesturesAsync 支持回调函数参数
* `优化` tasks 模块使用同步方式进行数据库操作避免可能的数据访问不一致问题
* `优化` 脚本执行模式支持管道符号分隔模式参数 (如 `"ui|auto";` 开头)
* `优化` 脚本执行模式支持单引号及反引号且支持省略分号 (如 `'ui';` 或 `'ui'` 开头)
* `优化` 脚本执行模式支持 axios, cheerio, dayjs 等模式参数快捷导入内置扩展模块 (如 `"axios";` 开头)
* `优化` 脚本执行模式支持 x 或 jsox 模式参数快捷启用 JavaScript 内置对象扩展模块 (如 `"x";` 开头)
* `优化` img 元素 src 及 path 属性支持本地相对路径 (如 `<img src="a.png"` />)
* `优化` 代码编辑器导入 Java 类和包名时支持智能判断插入位置
* `优化` images 模块支持直接使用路径作为图像参数
* `优化` importPackage 支持字符串参数
* `优化` 服务端模式 IP 地址支持剪切板导入智能识别且支持空格按键智能转换
* `优化` 文件管理器新建文件时支持默认前缀选择并自动生成合适的数字后缀
* `优化` 文件管理器运行项目时具体化异常消息提示 _[`issue #268`](http://issues.autojs6.com/268)_
* `优化` 文件管理器支持更多类型并支持显示对应的图标符号 (支持 800 多种文件类型)
* `优化` 文件管理器可编辑的文件类型 (jpg/doc/pdf...) 增加编辑按钮
* `优化` 文件管理器 APK 文件支持查看基础信息, Manifest 信息及权限列表
* `优化` 文件管理器音视频等媒体文件支持查看基础信息及 MediaInfo 信息
* `优化` 打包单文件时支持自动填入合适的标准化名包并支持无效字符过滤提示
* `优化` 打包单文件时支持根据已安装同包名应用自动设置图标并自增版本号及版本名称
* `优化` 打包项目配置文件支持 abis/libs 选项指定默认包含的 ABI 架构及扩展库
* `优化` 打包项目配置文件 abis/libs 选项无效或无可用时支持相关消息提示
* `优化` LeakCanary 在正式发行版本中被排除以避免增加不必要性
* `优化` 项目源代码所有英文注释增加简体中文翻译以增强注释可读性
* `优化` README 及 CHANGELOG 支持多语言 (由脚本自动生成)
* `优化` Gradle 构建脚本提升版本自适应能力
* `依赖` 附加 MIME Util 版本 2.3.1
* `依赖` 附加 Toaster 版本 12.6
* `依赖` 附加 EasyWindow (for Toaster) 版本 10.3
* `依赖` 升级 Gradle 版本 8.5 -> 8.11.1
* `依赖` 升级 Rhino 版本 1.7.15-SNAPSHOT -> 1.8.0-SNAPSHOT
* `依赖` 升级 Android Material Lang3 版本 1.10.0 -> 1.12.0
* `依赖` 升级 Androidx Annotation 版本 1.7.0 -> 1.9.1
* `依赖` 升级 Androidx AppCompat 版本 1.6.1 -> 1.7.0
* `依赖` 升级 Androidx WebKit 版本 1.8.0 -> 1.12.1
* `依赖` 升级 Apache Commons 版本 3.13.0 -> 3.16.0
* `依赖` 升级 ARSCLib 版本 1.2.4 -> 1.3.1
* `依赖` 升级 Gson 版本 2.10.1 -> 2.11.0
* `依赖` 升级 Jackson DataBind 版本 2.13.3 -> 2.13.4.2
* `依赖` 升级 Joda Time 版本 2.12.5 -> 2.12.7
* `依赖` 升级 LeakCanary 版本 2.12 -> 2.14
* `依赖` 升级 MLKit Barcode Scanning 版本 17.2.0 -> 17.3.0
* `依赖` 升级 MLKit Text Recognition Chinese 版本 16.0.0 -> 16.0.1
* `依赖` 升级 Retrofit2 Converter Gson 版本 2.9.0 -> 2.11.0
* `依赖` 升级 Retrofit2 Retrofit 版本 2.9.0 -> 2.11.0
* `依赖` 升级 Desugar JDK Libs 版本 2.0.3 -> 2.0.4
* `依赖` 升级 Test Runner 版本 1.5.2 -> 1.6.2
* `依赖` 升级 Junit Jupiter 版本 5.10.0 -> 5.10.3
* `依赖` 降级 OkHttp3 版本 5.0.0-alpha.11 -> 4.12.0

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
* `修复` Android 14 可能无法正常使用 runtime.<u>loadJar/loadDex</u> 方法的问题 (试修)
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
* `优化` 控制台打印错误消息时附加详细的堆栈信息
* `依赖` 附加 ARSCLib 版本 1.2.4
* `依赖` 附加 Flexbox 版本 3.0.0
* `依赖` 附加 Android OpenCC 版本 1.2.0
* `依赖` 升级 Gradle 版本 8.5-rc-1 -> 8.5

# v6.4.2

###### 2023/11/15

* `新增` dialogs.build() 选项参数属性 inputSingleLine
* `新增` console.setTouchable 方法 _[`issue #122`](http://issues.autojs6.com/122)_
* `修复` ocr 模块部分方法无法识别区域参数的问题 _[`issue #162`](http://issues.autojs6.com/162)_ _[`issue #175`](http://issues.autojs6.com/175)_
* `修复` Android 7.x 发现新版本时无法获取版本详情的问题
* `修复` Android 14 申请截图权限时导致应用崩溃的问题
* `修复` 主页抽屉快速切换 "浮动按钮" 开关时可能导致应用崩溃的问题
* `修复` 使用菜单关闭浮动按钮时重启应用后浮动按钮可能依然显示的问题
* `修复` Android 13+ 设置页面选择并切换 AutoJs6 语言后无法生效的问题
* `修复` 构建工具初次构建时无法自动完成 OpenCV 资源部署的问题
* `优化` 原生化 bridges 模块以提升脚本执行效率 (Ref to [aiselp](https://github.com/aiselp/AutoX/commit/7c41af6d2b9b36d00440a9c8b7e971d025f98327))
* `优化` 重构无障碍服务相关代码以增强无障碍服务的功能稳定性 (实验性) _[`issue #167`](http://issues.autojs6.com/167)_
* `优化` UiObject 和 UiObjectCollection 的打印输出格式
* `优化` 构建工具在构建环境 Gradle JDK 版本不满足要求时作出升级提示
* `依赖` 升级 Gradle 版本 8.4 -> 8.5-rc-1
* `依赖` 降级 Commons IO 版本 2.14.0 -> 2.8.0
* `依赖` 降级 Jackson DataBind 版本 2.14.3 -> 2.13.3

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
* `依赖` 升级 Gradle 版本 8.4-rc-3 -> 8.4
* `依赖` 升级 Android dx 版本 1.11 -> 1.14

# v6.4.0

###### 2023/10/30

* `新增` ocr 模块支持 Paddle Lite 引擎 (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #120`](http://pr.autojs6.com/120)_
* `新增` 打包功能支持内置插件与外部插件两种打包方式 (by [LZX284](https://github.com/LZX284)) _[`pr #151`](http://pr.autojs6.com/151)_
* `新增` WebSocket 模块 (参阅 项目文档 > [WebSocket](https://docs.autojs6.com/#/webSocketType))
* `新增` barcode / qrcode 模块 (参阅 项目文档 > [条码](https://docs.autojs6.com/#/barcode) / [二维码](https://docs.autojs6.com/#/qrcode))
* `新增` shizuku 模块 (参阅 项目文档 > [Shizuku](https://docs.autojs6.com/#/shizuku)) 及主页抽屉权限开关
* `新增` device.rotation / device.orientation 等方法
* `新增` 内部 Java 类支持 class 静态属性访问
* `新增` 支持在安卓系统设置页面选择并切换应用语言 (Android 13+)
* `新增` 支持设置页面添加或长按应用图标激活 [应用快捷方式](https://developer.android.com/guide/topics/ui/shortcuts?hl=zh-cn) , 可启动文档和设置等页面
* `修复` 重新合并部分 PR (by [aiselp](https://github.com/aiselp)) 以解决部分脚本无法正常结束运行的问题 _[`pr #75`](http://pr.autojs6.com/75)_ _[`pr #78`](http://pr.autojs6.com/78)_
* `修复` 打包应用无法使用 AutoJs6 新增 API 的问题 (by [LZX284](https://github.com/LZX284)) _[`pr #151`](http://pr.autojs6.com/151)_ _[`issue #149`](http://issues.autojs6.com/149)_
* `修复` 打包应用在系统夜间模式下的样式异常
* `修复` VSCode 插件保存文件到本地时文件扩展名信息丢失的问题
* `修复` 使用协程特性运行项目产生未捕获异常致使应用崩溃的问题
* `修复` 重启或退出应用时浮动按钮无法记录其位置状态信息的问题
* `修复` 设备屏幕方向改变时无法获取更新后的设备配置信息的问题 _[`issue #153`](http://issues.autojs6.com/153)_
* `修复` 屏幕旋转至横向时 Toolbar 标题字体过小的问题
* `修复` 屏幕旋转至横向时应用主页的页签排版过于拥挤的问题
* `修复` 屏幕旋转至横向时浮动按钮可能溢出屏幕的问题 _[`issue #90`](http://issues.autojs6.com/90)_
* `修复` 屏幕多次旋转时无法恢复浮动按钮的坐标及屏幕侧边方向的问题
* `修复` 部分设备消息浮动框可能出现遗漏显示或重复显示的问题
* `修复` 消息浮动框在多个脚本同时运行时可能存在被遮蔽的问题 _[`issue #67`](http://issues.autojs6.com/67)_
* `修复` 使用广播分析布局时点击布局无法弹出菜单且导致应用崩溃的问题
* `修复` 第二次及以后创建的 WebSocket 实例均无法正常触发监听器的问题
* `修复` 撤销 importPackage 的全局重定向方法以避免某些作用域下的包导入异常 _[`issue #88`](http://issues.autojs6.com/88)_
* `修复` 日志活动页面使用复制或导出功能时可能导致应用崩溃的问题
* `优化` 日志活动页面导出功能重命名为发送功能并重新实现符合实际意义的导出功能
* `优化` 日志活动页面发送功能支持条目数量过大时自动截取并作出提示
* `优化` ocr 模块同时兼容 Google MLKit 及 Paddle Lite 引擎 (参阅 项目文档 > [光学字符识别](https://docs.autojs6.com/#/ocr?id=p-mode))
* `优化` 提升无障碍服务自动启动的成功概率
* `优化` Kotlin 注解处理由 kapt 迁移至 KSP
* `优化` 构建工具支持 IntelliJ Idea EAP 版本
* `优化` 构建工具自适应 Java 发行版本以尽量避免 "无效的发行版本" 问题
* `优化` 构建工具优化 IDE 及相关插件的版本退级逻辑并增加版本预测能力
* `优化` 适配 VSCode 插件 1.0.7
* `依赖` 附加 Rikka Shizuku 版本 13.1.5
* `依赖` 附加 MLKit Barcode Scanning 版本 17.2.0
* `依赖` 升级 OpenCV 版本 4.5.5 -> 4.8.0 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `依赖` 升级 Gradle Compile 版本 33 -> 34
* `依赖` 升级 Gradle 版本 8.3-rc-1 -> 8.4-rc-3
* `依赖` 升级 Apache Commons Lang3 版本 3.12.0 -> 3.13.0
* `依赖` 升级 Glide 版本 4.15.1 -> 4.16.0
* `依赖` 升级 Android Analytics 版本 14.3.0 -> 14.4.0
* `依赖` 升级 Androidx WebKit 版本 1.7.0 -> 1.8.0
* `依赖` 升级 Androidx Preference 版本 1.2.0 -> 1.2.1
* `依赖` 升级 Androidx Annotation 版本 1.6.0 -> 1.7.0
* `依赖` 升级 Androidx Recyclerview 版本 1.3.0 -> 1.3.2
* `依赖` 升级 Android Material 版本 1.9.0 -> 1.10.0
* `依赖` 升级 Androidx AppCompat 版本 1.4.2 -> 1.6.1
* `依赖` 升级 Commons IO 版本 2.8.0 -> 2.14.0
* `依赖` 升级 Jackson DataBind 版本 2.13.3 -> 2.14.3
* `依赖` 移除 Zeugma Solutions LocaleHelper 版本 1.5.1

# v6.3.3

###### 2023/07/21

* `新增` 代码编辑器的代码注释功能 (by [little-alei](https://github.com/little-alei)) _[`pr #98`](http://pr.autojs6.com/98)_
* `新增` auto.stateListener 用于无障碍服务连接状态监听 (by [little-alei](https://github.com/little-alei)) _[`pr #98`](http://pr.autojs6.com/98)_
* `新增` UiObject 类型添加 nextSibling / lastChild / offset 等方法 (参阅 项目文档 > [控件节点](https://docs.autojs6.com/#/uiObjectType))
* `修复` VSCode 插件在脚本字符总长度超过四位十进制数时无法解析数据的问题 _[`issue #91`](http://issues.autojs6.com/91)_ _[`issue #93`](http://issues.autojs6.com/93)_ _[`issue #100`](http://issues.autojs6.com/100)_ _[`issue #109`](http://issues.autojs6.com/109)_
* `修复` VSCode 插件无法正常保存文件的问题 _[`issue #92`](http://issues.autojs6.com/91)_ _[`issue #94`](http://issues.autojs6.com/93)_
* `修复` 浮动按钮菜单项 "管理无障碍服务" 点击后可能未发生页面跳转的问题
* `修复` runtime.requestPermissions 方法丢失的问题 _[`issue #104`](http://issues.autojs6.com/104)_
* `修复` events.emitter 不支持 MainThreadProxy 参数的问题 _[`issue #103`](http://issues.autojs6.com/103)_
* `修复` 在 _[`pr #78`](http://pr.autojs6.com/78)_ 中存在的代码编辑器无法格式化代码的问题
* `修复` 使用 JavaAdapter 时导致 ClassLoader 调用栈溢出的问题 _[`issue #99`](http://issues.autojs6.com/99)_ _[`issue #110`](http://issues.autojs6.com/110)_
* `优化` 调整模块作用域 (by [aiselp](https://github.com/aiselp)) _[`pr #75`](http://pr.autojs6.com/75)_ _[`pr #78`](http://pr.autojs6.com/78)_
* `优化` 移除发行版本应用启动时的签名校验 (by [LZX284](https://github.com/LZX284)) _[`pr #81`](http://pr.autojs6.com/81)_
* `优化` 在 _[`pr #98`](http://pr.autojs6.com/98)_ 基础上的编辑器代码注释功能的行为, 样式及光标位置处理
* `优化` 在 _[`pr #98`](http://pr.autojs6.com/98)_ 基础上添加代码注释菜单项
* `优化` 适配 VSCode 插件 1.0.6
* `优化` UiObject#parent 方法增加级数参数支持 (参阅 项目文档 > [控件节点](https://docs.autojs6.com/#/uiObjectType))
* `依赖` 升级 Gradle 版本 8.2 -> 8.3-rc-1

# v6.3.2

###### 2023/07/06

* `新增` crypto 模块 (参阅 项目文档 > [密文](https://docs.autojs6.com/#/crypto)) _[`issue #70`](http://issues.autojs6.com/70)_
* `新增` UI 模式增加 textswitcher / viewswitcher / viewflipper / numberpicker / video / search 等控件
* `新增` 日志活动页面增加复制及导出日志等功能 _[`issue #76`](http://issues.autojs6.com/76)_
* `新增` 客户端模式增加 IP 地址历史记录功能
* `修复` 客户端模式自动连接或服务端模式自动开启后可能无法显示 IP 地址信息的问题
* `修复` 客户端模式及服务端模式连接后在切换语言或夜间模式时连接断开且无法再次连接的问题
* `修复` 客户端模式输入目标地址时无法使用自定义端口的问题
* `修复` 客户端模式输入目标地址时某些字符将导致 AutoJs6 崩溃的问题
* `修复` VSCode 插件远程命令可能出现解析失败造成命令无法响应的问题 (试修)
* `修复` Android 7.x 发现新版本时无法获取版本详情的问题
* `修复` images.pixel 无法获取无障碍服务截图的像素色值的问题 _[`issue #73`](http://issues.autojs6.com/73)_
* `修复` UI 模式 Android 原生控件 (大写字母开头) 无法使用预置控件属性的问题
* `修复` runtime.loadDex/loadJar 加载多个文件时仅第一个文件生效的问题 _[`issue #88`](http://issues.autojs6.com/88)_
* `修复` 部分设备安装应用后启动器仅显示文档图标的问题 (试修) _[`issue #85`](http://issues.autojs6.com/85)_
* `优化` 适配 VSCode 插件 1.0.5
* `优化` 支持 cheerio 模块 (Ref to [aiselp](https://github.com/aiselp/AutoX/commit/7176f5ad52d6904383024fb700bf19af75e22903)) _[`issue #65`](http://issues.autojs6.com/65)_
* `优化` JsWebSocket 实例支持使用 rebuild 方法重新重建实例并建立连接 _[`issue #69`](http://issues.autojs6.com/69)_
* `优化` base64 模块支持 number 数组及 Java 字节数组作为主要参数的编解码
* `优化` 增加对 JavaMail for Android 的支持 _[`issue #71`](http://issues.autojs6.com/71)_
* `优化` 获取版本更新信息时使用 Blob 数据类型以增强无代理网络环境适应性
* `优化` 客户端模式连接过程中在主页抽屉副标题显示目标 IP 地址
* `优化` 客户端模式输入目标地址时支持对不合法的输入进行提示
* `优化` 客户端模式支持使用软键盘回车键建立连接
* `优化` 服务端模式开启后保持常开状态 (除非手动关闭或应用进程结束) _[`issue #64`](http://issues.autojs6.com/64#issuecomment-1596990158)_
* `优化` 实现 AutoJs6 与 VSCode 插件的双向版本检测并提示异常检测结果 _[`issue #89`](http://issues.autojs6.com/89)_
* `优化` 增加短信数据读取权限 (android.permission.READ_SMS) (默认关闭)
* `优化` findMultiColors 方法内部实现 (by [LYS86](https://github.com/LYS86)) _[`pr #72`](http://pr.autojs6.com/72)_
* `优化` runtime.loadDex/loadJar/load 支持按目录级别加载或同时加载多个文件
* `依赖` 升级 LeakCanary 版本 2.11 -> 2.12
* `依赖` 升级 Android Analytics 版本 14.2.0 -> 14.3.0
* `依赖` 升级 Gradle 版本 8.2-milestone-1 -> 8.2

# v6.3.1

###### 2023/05/26

* `新增` 发布通知权限及主页抽屉开关 _[`issue #55`](http://issues.autojs6.com/55)_
* `新增` UI 模式支持简单的 Android 布局解析 (参阅 示例代码 > 布局 > 简单安卓布局)
* `新增` UI 模式增加 console / imagebutton / ratingbar / switch / textclock / togglebutton 等控件
* `新增` UI 模式控件的颜色色值支持 [OmniColor](https://docs.autojs6.com/#/omniTypes?id=omnicolor) 类型 (如 color="orange")
* `新增` UI 模式的控件完全支持 attr 方法设置控件属性 (如 ui.text.attr('color', 'blue'))
* `新增` UI 模式控件支持布尔类型属性值的缺省形式 (如 clickable="true" 可简写为 clickable 或 isClickable)
* `新增` button 控件支持 isColored 及 isBorderless 布尔类型属性
* `新增` console.resetGlobalLogConfig 方法用于重置全局日志配置
* `新增` web.newWebSocket 方法用于创建 Web Socket 实例 (参阅 项目文档 > [万维网](https://docs.autojs6.com/#/web?id=m-newwebsocket))
* `修复` 文件管理器的文件夹排序异常
* `修复` floaty 模块构建的浮动窗口无法调节样式及位置的问题 _[`issue #60`](http://issues.autojs6.com/60)_
* `修复` floaty 模块构建的浮动窗口与系统状态栏重叠的问题
* `修复` http.postMultipart 方法功能异常 _[`issue #56`](http://issues.autojs6.com/56)_
* `修复` Android 7.x 无法运行任何脚本的问题 _[`issue #61`](http://issues.autojs6.com/61)_
* `修复` sign.property 文件不存在时无法构建项目的问题
* `修复` 高版本系统 AutoJs6 置于后台时可能因无前台通知权限而崩溃的问题 (API >= 33)
* `修复` 调用 console.show 方法后日志窗口点击 FAB 按钮无法清空日志的问题
* `修复` 代码编辑器调试时出现的 prototype 空指针异常
* `修复` 代码编辑器运行脚本时在缓存文件夹运行临时脚本而非先保存再在原始位置运行以避免可能的脚本内容丢失问题
* `修复` 调整布局层次分析的层级色条宽度避免层级过多时控件名称无法显示的问题 _[`issue #46`](http://issues.autojs6.com/46)_
* `优化` 布局分析浮动窗口增加退出按钮以关闭窗口 _[`issue #63`](http://issues.autojs6.com/63)_
* `优化` 脚本绝对路径使用简称形式以缩减文本长度并增加可读性
* `优化` 将 Error 替换为 Exception 避免出现异常时 AutoJs6 应用崩溃
* `优化` 视图 (View) 绑定方式由 ButterKnife 迁移至 View Binding _[`issue #48`](http://issues.autojs6.com/48)_
* `优化` 服务端模式非正常关闭时将于 AutoJs6 启动时自动开启 _[`issue #64`](http://issues.autojs6.com/64)_
* `优化` 客户端模式非正常关闭时将于 AutoJs6 启动时按最近一次的历史地址自动连接
* `依赖` 升级 LeakCanary 版本 2.10 -> 2.11
* `依赖` 升级 Android Material 版本 1.8.0 -> 1.9.0
* `依赖` 升级 Androidx WebKit 版本 1.6.1 -> 1.7.0
* `依赖` 升级 OkHttp3 版本 3.10.0 -> 5.0.0-alpha.9 -> 5.0.0-alpha.11
* `依赖` 升级 MLKit Text Recognition Chinese 版本 16.0.0-beta6 -> 16.0.0

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
* `依赖` 本地化 Auto.js APK Builder 版本 1.0.3
* `依赖` 本地化 MultiLevelListView 版本 1.1
* `依赖` 本地化 Settings Compat 版本 1.1.5
* `依赖` 本地化 Enhanced Floaty 版本 0.31
* `依赖` 附加 MLKit Text Recognition Chinese 版本 16.0.0-beta6
* `依赖` 升级 Gradle 版本 8.0-rc-1 -> 8.2-milestone-1
* `依赖` 升级 Android Material 版本 1.7.0 -> 1.8.0
* `依赖` 升级 Glide 版本 4.14.2 -> 4.15.1
* `依赖` 升级 Joda Time 版本 2.12.2 -> 2.12.5
* `依赖` 升级 Android Analytics 版本 14.0.0 -> 14.2.0
* `依赖` 升级 Androidx WebKit 版本 1.5.0 -> 1.6.1
* `依赖` 升级 Androidx Recyclerview 版本 1.2.1 -> 1.3.0
* `依赖` 升级 Zip4j 版本 2.11.2 -> 2.11.5
* `依赖` 升级 Junit Jupiter 版本 5.9.2 -> 5.9.3
* `依赖` 升级 Androidx Annotation 版本 1.5.0 -> 1.6.0
* `依赖` 升级 Jackson DataBind 版本 2.14.1 -> 2.14.2
* `依赖` 升级 Desugar JDK Libs 版本 2.0.0 -> 2.0.3

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
* `修复` Android 10+ 无法正常运行有关设置画笔颜色的示例代码的问题
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
* `修复` 使用浮动按钮菜单关闭按钮后重启应用时浮动按钮依然显示的问题
* `修复` 布局层次分析时长按列表项可能导致弹出菜单溢出下方屏幕的问题
* `修复` Android 7.x 系统在夜间模式关闭时导航栏按钮难以辨识的问题
* `修复` http.post 等方法可能出现的请求未关闭异常
* `修复` colors.toString 方法在 Alpha 通道为 0 时其通道信息在结果中丢失的问题
* `优化` 重定向 Auto.js 4.x 版本的公有类以实现尽可能的向下兼容 (程度有限)
* `优化` 合并全部项目模块避免可能的循环引用等问题 (临时移除 inrt 模块)
* `优化` Gradle 构建配置从 Groovy 迁移到 KTS
* `优化` Rhino 异常消息增加多语言支持
* `优化` 主页抽屉权限开关仅在开启时弹出提示消息
* `优化` 主页抽屉布局紧贴于状态栏下方避免顶部颜色条的低兼容性
* `优化` 检查更新/下载更新/更新提示功能兼容 Android 7.x 系统
* `优化` 重新设计设置页面 (迁移至 AndroidX)
* `优化` 设置页面支持长按设置选项获取详细信息
* `优化` 夜间模式增加 "跟随系统" 设置选项 (Android 9+)
* `优化` 应用启动画面适配夜间模式
* `优化` 应用图标增加数字标识以提升多个开源版本共存用户的使用体验
* `优化` 主题色增加更多 Material Design Color (材料设计颜色) 选项
* `优化` 文件管理器/任务面板等列表项图标适当轻量化并适配主题色
* `优化` 主页搜索框的提示文本颜色适配夜间模式
* `优化` 对话框/文本/Fab/AppBar/列表项等部件适配夜间模式
* `优化` 文档/设置/关于/主题色/布局分析等页面及浮动按钮菜单适配夜间模式
* `优化` 页面布局尽可能兼容 RTL (Right-To-Left) 布局
* `优化` 关于页面增加图标动画效果
* `优化` 关于页面版权声明文本自动更新年份信息
* `优化` 应用初始安装后自动决定并设置合适的工作目录
* `优化` 禁用文档页面双指缩放功能避免文档内容显示异常
* `优化` 任务面板列表项按相对路径简化显示任务的名称及路径
* `优化` 文本编辑器按钮文本适当缩写避免文本内容溢出
* `优化` 文本编辑器设置字体大小支持恢复默认值
* `优化` 提升浮动按钮点击响应速度
* `优化` 点击浮动按钮布局分析按钮直接进行布局范围分析
* `优化` 布局分析主题自适应 (浮动窗口跟随应用主题, 快速设置面板跟随系统主题)
* `优化` 布局控件信息列表按可能的使用频率重新排序
* `优化` 布局控件信息点击复制时根据选择器类型自动优化输出格式
* `优化` 使用浮动窗口选择文件时按返回键可返回至上级目录而非直接关闭窗口
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
* `优化` 提升 console.show 显示的日志浮动窗口文字亮度以增强内容辨识度
* `优化` ImageWrapper#saveTo 支持相对路径保存图像文件
* `优化` 重新设计 colors 全局对象并增加 HSV / HSL 等色彩模式支持 (参阅 项目文档 > [颜色](https://docs.autojs6.com/#/color))
* `依赖` 升级 Gradle Compile 版本 32 -> 33
* `依赖` 本地化 Android Job 版本 1.4.3
* `依赖` 本地化 Android Plugin Client SDK For Locale 版本 9.0.0
* `依赖` 本地化 GitHub API 版本 1.306
* `依赖` 附加 JCIP Annotations 版本 1.0
* `依赖` 附加 Androidx WebKit 版本 1.5.0
* `依赖` 附加 Commons IO 版本 2.8.0
* `依赖` 附加 Desugar JDK Libs 版本 2.0.0
* `依赖` 附加 Jackson DataBind 版本 2.13.3
* `依赖` 附加 Jaredrummler Android Device Names 版本 2.1.0
* `依赖` 附加 Jaredrummler Animated SVG View 版本 1.0.6
* `依赖` 替换 Jrummyapps ColorPicker 版本 2.1.7 为 Jaredrummler ColorPicker 版本 1.1.0
* `依赖` 升级 Gradle 版本 7.5-rc-1 -> 8.0-rc-1
* `依赖` 升级 Gradle 构建工具版本 7.4.0-alpha02 -> 8.0.0-alpha09
* `依赖` 升级 Kotlin Gradle 插件版本 1.6.10 -> 1.8.0-RC2
* `依赖` 升级 Android Material 版本 1.6.0 -> 1.7.0
* `依赖` 升级 Androidx Annotation 版本 1.3.0 -> 1.5.0
* `依赖` 升级 Androidx AppCompat 版本 1.4.1 -> 1.4.2
* `依赖` 升级 Android Analytics 版本 13.3.0 -> 14.0.0
* `依赖` 升级 Gson 版本 2.9.0 -> 2.10
* `依赖` 升级 Joda Time 版本 2.10.14 -> 2.12.1
* `依赖` 升级 Kotlinx Coroutines 版本 1.6.1-native-mt -> 1.6.1
* `依赖` 升级 OkHttp3 版本 3.10.0 -> 5.0.0-alpha.7 -> 5.0.0-alpha.9
* `依赖` 升级 Zip4j 版本 2.10.0 -> 2.11.2
* `依赖` 升级 Glide 版本 4.13.2 -> 4.14.2
* `依赖` 升级 Junit Jupiter 版本 5.9.0 -> 5.9.1

# v6.1.1

###### 2022/05/31

* `新增` 检查更新/下载更新/更新提示功能 (参阅 设置页面) (暂不支持 Android 7.x 系统)
* `修复` 应用在 Android 10 系统无法读写外部存储的问题 _[`issue #17`](http://issues.autojs6.com/17)_
* `修复` 编辑器页面长按时可能导致应用崩溃的问题 _[`issue #18`](http://issues.autojs6.com/18)_
* `修复` 编辑器页面长按菜单 "删除行" 和 "复制行" 功能无效的问题
* `修复` 编辑器页面选项菜单中 "粘贴" 功能缺失的问题
* `优化` 部分异常消息字符串资源化 (en / zh)
* `优化` 调整内容未保存对话框的按钮布局并增加颜色区分
* `依赖` 附加 github-api 版本 1.306
* `依赖` 替换 retrofit2-rxjava2-adapter 版本 1.0.0 为 adapter-rxjava2 版本 2.9.0

# v6.1.0

###### 2022/05/26 - 包名变更, 谨慎升级

* `提示` 修改应用包名为 org.autojs.autojs6 避免与开源 Auto.js 应用包名冲突
* `新增` 主页抽屉增加 "投影媒体权限" 开关 (Root / ADB 方式) (开关状态检测为实验性)
* `新增` 文件浏览器支持显示隐藏文件和文件夹 (参阅 设置页面)
* `新增` 强制 Root 检查功能 (参阅 设置页面 及 示例代码)
* `新增` autojs 模块 (参阅 示例代码 > AutoJs6)
* `新增` tasks 模块 (参阅 示例代码 > 任务)
* `新增` console.launch() 方法启动日志活动页面
* `新增` util.morseCode 工具 (参阅 示例代码 > 工具 > 摩斯电码)
* `新增` util.versionCodes 工具 (参阅 示例代码 > 工具 > 安卓版本信息查询)
* `新增` util.getClass() 等方法 (参阅 示例代码 > 工具 > 获取类与类名)
* `新增` timers.setIntervalExt() 方法 (参阅 示例代码 > 定时器 > 条件周期执行)
* `新增` colors.toInt() / rgba() 等方法 (参阅 示例代码 > 图像与颜色 > 基本颜色转换)
* `新增` automator.isServiceRunning() / ensureService() 方法
* `新增` automator.lockScreen() 等方法 (参阅 示例代码 > 无障碍服务 > Android 9 新增)
* `新增` automator.headsethook() 等方法 (参阅 示例代码 > 无障碍服务 > Android 11 新增)
* `新增` automator.captureScreen() 方法 (参阅 示例代码 > 无障碍服务 > 获取屏幕截图)
* `新增` dialogs.build() 选项参数属性 animation, linkify 等 (参阅 示例代码 > 对话框 > 个性化对话框)
* `修复` dialogs.build() 选项参数属性 inputHint, itemsSelectedIndex 等功能异常
* `修复` JsDialog#on('multi_choice') 回调参数功能异常
* `修复` UiObject#parent().indexInParent() 总是返回 -1 的问题 _[`issue #16`](http://issues.autojs6.com/16)_
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
* `优化` device.vibrate() 支持模式振动及摩斯电码振动 (参阅 示例代码 > 设备 > 模式振动 / 摩斯电码振动)
* `优化` 外部存储读写权限适配高版本安卓系统 (API >= 30)
* `优化` 控制台字体采用 Material Color 增强普通及夜间主题下的字体可读性
* `优化` 保存 ImageWrapper 所有实例弱引用并在脚本结束时自动回收 (实验性)
* `依赖` 附加 CircleImageView 版本 3.1.0
* `依赖` 升级 Android Analytics 版本 13.1.0 -> 13.3.0
* `依赖` 升级 Gradle 构建工具版本 7.3.0-alpha06 -> 7.4.0-alpha02
* `依赖` 升级 Android Job 版本 1.4.2 -> 1.4.3
* `依赖` 升级 Android Material 版本 1.5.0 -> 1.6.0
* `依赖` 升级 CrashReport 版本 2.6.6 -> 4.0.4
* `依赖` 升级 Glide 版本 4.13.1 -> 4.13.2
* `依赖` 升级 Joda Time 版本 2.10.13 -> 2.10.14
* `依赖` 升级 Kotlin Gradle 插件版本 1.6.10 -> 1.6.21
* `依赖` 升级 Kotlinx Coroutines 版本 1.6.0 -> 1.6.1-native-mt
* `依赖` 升级 LeakCanary 版本 2.8.1 -> 2.9.1
* `依赖` 升级 OkHttp3 版本 5.0.0-alpha.6 -> 5.0.0-alpha.7
* `依赖` 升级 Rhino 引擎版本 1.7.14 -> 1.7.15-SNAPSHOT
* `依赖` 升级 Zip4j 版本 2.9.1 -> 2.10.0
* `依赖` 移除 Groovy JSON 版本 3.0.8
* `依赖` 移除 Kotlin Stdlib JDK7 版本 1.6.21

# v6.0.3

###### 2022/03/19

* `新增` 多语言切换功能 (尚不完善)
* `新增` recorder 模块 (参阅 示例代码 > 计时器)
* `新增` 使用 "修改安全设置权限" 自动启用无障碍服务及开关设置
* `修复` 点击快速设置面板中相关图标后面板未自动收起的问题 (试修) _[`issue #7`](http://issues.autojs6.com/7)_
* `修复` toast 使用强制显示参数时可能导致 AutoJs6 崩溃的问题
* `修复` Socket 传输数据头部信息不完整时可能导致 AutoJs6 崩溃的问题
* `优化` 启动或重启 AutoJs6 时根据选项设置自动开启无障碍服务
* `优化` 开启浮动按钮开关时尝试自动开启无障碍服务
* `优化` 所有资源文件补全元素对应的英文翻译
* `优化` 微调主页抽屉布局 减小项目排列间距
* `优化` 主页抽屉增加前台服务状态开关的同步
* `优化` 主页抽屉展开时立即按需同步开关状态
* `优化` 显示指针位置增加状态检测及结果提示
* `优化` 支持 64 位操作系统 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `优化` 浮动按钮初始化时同时应用透明度设置 (无需点击后再应用透明度)
* `优化` 重置文件内容时增加是否为示例代码文件的检测并增加结果提示
* `优化` 转移打包插件下载地址 GitHub -> JsDelivr
* `依赖` 附加 Zeugma Solutions LocaleHelper 版本 1.5.1
* `依赖` 降级 Android Material 版本 1.6.0-alpha02 -> 1.5.0
* `依赖` 升级 Kotlinx Coroutines 版本 1.6.0-native-mt -> 1.6.0
* `依赖` 升级 OpenCV 版本 3.4.3 -> 4.5.4 -> 4.5.5 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `依赖` 升级 OkHttp3 版本 3.10.0 -> 5.0.0-alpha.4 -> 5.0.0-alpha.6
* `依赖` 升级 Gradle 构建工具版本 7.2.0-beta01 -> 7.3.0-alpha06
* `依赖` 升级 Auto.js-ApkBuilder 版本 1.0.1 -> 1.0.3
* `依赖` 升级 Glide Compiler 版本 4.12.0 -> 4.13.1
* `依赖` 升级 Gradle 发行版本 7.4-rc-2 -> 7.4.1
* `依赖` 升级 Gradle Compile 版本 31 -> 32
* `依赖` 升级 Gson 版本 2.8.9 -> 2.9.0

# v6.0.2

###### 2022/02/05

* `新增` images.bilateralFilter() 双边滤波图像处理方法
* `修复` 多次调用 toast 只生效最后一次调用的问题
* `修复` toast.dismiss() 可能无效的问题
* `修复` 客户端模式及服务端模式开关可能无法正常工作的问题
* `修复` 客户端模式及服务端模式开关状态不能正常刷新的问题
* `修复` Android 7.x 解析 UI 模式 text 元素异常 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`issue #4`](http://issues.autojs6.com/4)_ _[`issue #9`](http://issues.autojs6.com/9)_
* `优化` 忽略 sleep() 的 ScriptInterruptedException 异常
* `依赖` 附加 Androidx AppCompat (Legacy) 版本 1.0.2
* `依赖` 升级 Androidx AppCompat 版本 1.4.0 -> 1.4.1
* `依赖` 升级 Androidx Preference 版本 1.1.1 -> 1.2.0
* `依赖` 升级 Rhino 引擎版本 1.7.14-SNAPSHOT -> 1.7.14
* `依赖` 升级 OkHttp3 版本 3.10.0 -> 5.0.0-alpha.3 -> 5.0.0-alpha.4
* `依赖` 升级 Android Material 版本 1.6.0-alpha01 -> 1.6.0-alpha02
* `依赖` 升级 Gradle 构建工具版本 7.2.0-alpha06 -> 7.2.0-beta01
* `依赖` 升级 Gradle 发行版本 7.3.3 -> 7.4-rc-2

# v6.0.1

###### 2022/01/01

* `新增` 连接 VSCode 插件支持客户端 (LAN) 及服务端 (LAN/ADB) 方式 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` base64 模块 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` 增加 isInteger/isNullish/isObject/isPrimitive/isReference 全局方法
* `新增` 增加 polyfill (Object.getOwnPropertyDescriptors)
* `新增` 增加 polyfill (Array.prototype.flat)
* `优化` 扩展 global.sleep 支持 随机范围/负数兼容
* `优化` 扩展 global.toast 支持 时长控制/强制覆盖控制/dismiss
* `优化` 包名对象全局化 (okhttp3/androidx/de)
* `依赖` 升级 Android Material 版本 1.5.0-beta01 -> 1.6.0-alpha01
* `依赖` 升级 Gradle 构建工具版本 7.2.0-alpha04 -> 7.2.0-alpha06
* `依赖` 升级 Kotlinx Coroutines 版本 1.5.2-native-mt -> 1.6.0-native-mt
* `依赖` 升级 Kotlin Gradle 插件版本 1.6.0 -> 1.6.10
* `依赖` 升级 Gradle 发行版本 7.3 -> 7.3.3

# v6.0.0

###### 2021/12/01

* `新增` 主页抽屉底部增加重启应用按钮
* `新增` 主页抽屉增加忽略电池优化/显示在其他应用上层等开关
* `修复` 应用初始安装后部分区域主题颜色渲染异常的问题
* `修复` sign.property 文件不存在时无法构建项目的问题
* `修复` 定时任务面板一次性任务的月份存取错误
* `修复` 应用设置页面开关颜色不随主题变更的问题
* `修复` 无法识别打包插件及打包插件下载地址无效的问题
* `修复` 主页抽屉 "查看使用情况权限" 开关状态可能不同步的问题
* `修复` TemplateMatching.fastTemplateMatching 潜在的 Mat 内存泄漏问题
* `优化` 升级 Rhino 引擎版本 1.7.7.2 -> 1.7.13 -> 1.7.14-SNAPSHOT
* `优化` 升级 OpenCV 版本 3.4.3 -> 4.5.4
* `优化` ViewUtil.getStatusBarHeight 提升兼容性
* `优化` 主页抽屉移除用户登录相关模块并移除布局占位
* `优化` 主页移除社区及市场标签页面并优化布局对其方式
* `优化` 修改一些设置选项的默认开关状态
* `优化` 关于页面增加 SinceDate 并优化 Copyright 显示
* `优化` 升级 JSON 模块至 2017-06-12 版本并整合 cycle.js
* `优化` 移除 Activity 前置时的自动检查更新功能并移除检查更新相关按钮
* `优化` AppOpsKt#isOpPermissionGranted 内部代码逻辑
* `优化` ResourceMonitor 使用 ReentrantLock 增强安全性 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `优化` 使用 Maven Central 等仓库替换 JCenter 仓库
* `优化` 抽离并移除重复的本地库文件
* `依赖` 本地化 CrashReport 版本 2.6.6
* `依赖` 本地化 MutableTheme 版本 1.0.0
* `依赖` 附加 Androidx Preference 版本 1.1.1
* `依赖` 附加 SwipeRefreshLayout 版本 1.1.0
* `依赖` 升级 Android Analytics 版本 7.0.0 -> 13.1.0
* `依赖` 升级 Android Annotations 版本 4.5.2 -> 4.8.0
* `依赖` 升级 Gradle 构建工具版本 3.2.1 -> 4.1.0 -> 7.0.3 -> 7.2.0-alpha04
* `依赖` 升级 Android Job 版本 1.2.6 -> 1.4.2
* `依赖` 升级 Android Material 版本 1.1.0-alpha01 -> 1.5.0-beta01
* `依赖` 升级 Androidx MultiDex 版本 2.0.0 -> 2.0.1
* `依赖` 升级 Apache Commons Lang3 版本 3.6 -> 3.12.0
* `依赖` 升级 Appcompat 版本 1.0.2 -> 1.4.0
* `依赖` 升级 ButterKnife Gradle 插件版本 9.0.0-rc2 -> 10.2.1 -> 10.2.3
* `依赖` 升级 ColorPicker 版本 2.1.5 -> 2.1.7
* `依赖` 升级 Espresso Core 版本 3.1.1-alpha01 -> 3.5.0-alpha03
* `依赖` 升级 Eventbus 版本 3.0.0 -> 3.2.0
* `依赖` 升级 Glide Compiler 版本 4.8.0 -> 4.12.0 -> 4.12.0
* `依赖` 升级 Gradle Build Tool 版本 29.0.2 -> 30.0.2
* `依赖` 升级 Gradle Compile 版本 28 -> 30 -> 31
* `依赖` 升级 Gradle 发行版本 4.10.2 -> 6.5 -> 7.0.2 -> 7.3
* `依赖` 升级 Groovy-Json 插件版本 3.0.7 -> 3.0.8
* `依赖` 升级 Gson 版本 2.8.2 -> 2.8.9
* `依赖` 升级 JavaVersion 版本 1.8 -> 11 -> 16
* `依赖` 升级 Joda Time 版本 2.9.9 -> 2.10.13
* `依赖` 升级 Junit 版本 4.12 -> 4.13.2
* `依赖` 升级 Kotlin Gradle 插件版本 1.3.10 -> 1.4.10 -> 1.6.0
* `依赖` 升级 Kotlinx Coroutines 版本 1.0.1 -> 1.5.2-native-mt
* `依赖` 升级 LeakCanary 版本 1.6.1 -> 2.7
* `依赖` 升级 LicensesDialog 版本 1.8.1 -> 2.2.0
* `依赖` 升级 Material Dialogs 版本 0.9.2.3 -> 0.9.6.0
* `依赖` 升级 OkHttp3 版本 3.10.0 -> 5.0.0-alpha.2 -> 5.0.0-alpha.3
* `依赖` 升级 Reactivex RxJava2 RxAndroid 版本 2.0.1 -> 2.1.1
* `依赖` 升级 Reactivex RxJava2 版本 2.1.2 -> 2.2.21
* `依赖` 升级 Retrofit2 Converter Gson 版本 2.3.0 -> 2.9.0
* `依赖` 升级 Retrofit2 Retrofit 版本 2.3.0 -> 2.9.0
* `依赖` 升级 Zip4j 版本 1.3.2 -> 2.9.1