******

### 版本历史

******

# v6.0.1

###### 2021/12/07

* `新增` polyfill (Object.getOwnPropertyDescriptors)
* `新增` polyfill (Array.prototype.flat)
* `新增` isInteger/isNullish/isPlainObject/isPrimitive/isReference
* `优化` 扩展global.sleep支持随机范围/负数兼容
* `优化` 扩展global.toast支持时长控制/强制覆盖控制/dismiss方法
* `优化` 包名对象全局化 (okhttp3/androidx/de)
* `优化` 升级 Android Material 版本 1.5.0-beta01 -> 1.6.0-alpha01
* `优化` 升级 Android Gradle 插件版本 7.2.0-alpha04 -> 7.2.0-alpha05

# v6.0.0

###### 2021/12/01

* `新增` 主页抽屉底部增加重启应用按钮
* `新增` 主页抽屉增加忽略电池优化/显示在其他应用上层等开关
* `修复` 应用初始安装后部分区域主题颜色渲染异常的问题
* `修复` sign.property 不存在时无法 build 的问题
* `修复` 定时任务面板一次性任务的月份存取错误
* `修复` 应用设置页面开关颜色不随主题变更的问题
* `修复` 无法识别打包插件及打包插件下载地址无效的问题
* `修复` 首页抽屉 "查看使用情况权限" 开关状态可能不同步的问题
* `修复` TemplateMatching.fastTemplateMatching 潜在的 Mat 内存泄漏问题
* `优化` 升级 Rhino 引擎版本 1.7.7.2 -> 1.7.13 -> 1.7.14-snapshot
* `优化` 升级 OpenCV 版本 3.4.3 -> 4.5.4
* `优化` ViewUtil.getStatusBarHeight 提升兼容性
* `优化` 主页抽屉移除用户登录相关模块并移除布局占位
* `优化` 主页移除社区及市场标签页面并优化布局对其方式
* `优化` 修改一些设置选项的默认开关状态
* `优化` 关于页面增加 SinceDate 并优化 Copyright 显示
* `优化` 升级 JSON 模块至 2017-06-12 版本并整合 cycle.js
* `优化` 移除 Activity 前置时的自动检查更新功能并移除检查更新相关按钮
* `优化` AppOpsKt#isOpPermissionGranted 内部代码逻辑
* `优化` ResourceMonitor 使用 ReentrantLock 增强安全性 (Ref to TonyJiangWJ)
* `优化` 使用 Maven Central 等仓库替换 JCenter 仓库
* `优化` 抽离并移除重复的本地库文件
* `优化` 本地化 CrashReport 版本 2.6.6
* `优化` 本地化 MutableTheme 版本 1.0.0
* `优化` 附加 Androidx Preference 版本 1.1.1
* `优化` 附加 SwipeRefreshLayout 版本 1.1.0
* `优化` 升级 Android Analytics 版本 7.0.0 -> 13.1.0
* `优化` 升级 Android Annotations 版本 4.5.2 -> 4.8.0
* `优化` 升级 Android Gradle 插件版本 3.2.1 -> 4.1.0 -> 7.0.3 -> 7.2.0-alpha04
* `优化` 升级 Android Job 版本 1.2.6 -> 1.4.2
* `优化` 升级 Android Material 版本 1.1.0-alpha01 -> 1.5.0-beta01
* `优化` 升级 Androidx MultiDex 版本 2.0.0 -> 2.0.1
* `优化` 升级 Apache Commons Lang3 版本 3.6 -> 3.12.0
* `优化` 升级 Appcompat 版本 1.0.2 -> 1.4.0
* `优化` 升级 ButterKnife Gradle 插件版本 9.0.0-rc2 -> 10.2.1 -> 10.2.3
* `优化` 升级 ColorPicker 版本 2.1.5 -> 2.1.7
* `优化` 升级 Espresso Core 版本 3.1.1-alpha01 -> 3.5.0-alpha03
* `优化` 升级 Eventbus 版本 3.0.0 -> 3.2.0
* `优化` 升级 Glide Compiler 版本 4.8.0 -> 4.12.0 -> 4.12.0
* `优化` 升级 Gradle Build Tool 版本 29.0.2 -> 30.0.2
* `优化` 升级 Gradle Compile 版本 28 -> 30 -> 31
* `优化` 升级 Gradle 发行版本 4.10.2 -> 6.5 -> 7.0.2 -> 7.3
* `优化` 升级 Groovy-Json 插件版本 3.0.7 -> 3.0.8
* `优化` 升级 Gson 版本 2.8.2 -> 2.8.9
* `优化` 升级 JavaVersion 版本 1.8 -> 11 -> 16
* `优化` 升级 Joda Time 版本 2.9.9 -> 2.10.13
* `优化` 升级 Junit 版本 4.12 -> 4.13.2
* `优化` 升级 Kotlin Gradle 插件版本 1.3.10 -> 1.4.10 -> 1.6.0
* `优化` 升级 Kotlinx Coroutines 版本 1.0.1 -> 1.5.2-native-mt
* `优化` 升级 Leakcanary 版本 1.6.1 -> 2.7
* `优化` 升级 LicensesDialog 版本 1.8.1 -> 2.2.0
* `优化` 升级 Material Dialogs 版本 0.9.2.3 -> 0.9.6.0
* `优化` 升级 Okhttp3 版本 3.10.0 -> 5.0.0-alpha.2 -> 5.0.0-alpha.3
* `优化` 升级 Reactivex RxJava2 RxAndroid 版本 2.0.1 -> 2.1.1
* `优化` 升级 Reactivex RxJava2 版本 2.1.2 -> 2.2.21
* `优化` 升级 Retrofit2 Converter Gson 版本 2.3.0 -> 2.9.0
* `优化` 升级 Retrofit2 Retrofit 版本 2.3.0 -> 2.9.0
* `优化` 升级 Zip4j 版本 1.3.2 -> 2.9.1