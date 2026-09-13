# SDK 37 P2.11: WebView 文件选择、门控与根布局修复

日期: 2026-09-14. 会话起点 `373b7ef65`, 修复提交 `4d8564d98`.
本轮补齐生产 `ui.webview` 的文件选择与取消、File System Access 门控和 URI 授权观测.
四组共 24 个场景符合预期: 20 次真实系统选择器往返, 4 次开关关闭时的提前拒绝.
这不代表 P2.11 的实体机输入、全部文件操作或独立插件已经验收.

## 1. 环境与包

使用与 [P2.10](android-sdk-37-p210-20260914.md) 相同的独占 API 37 AVD, adb server 5054,
serial `emulator-5590`. WebView 为 `com.google.android.webview` 145.0.7632.218.
正常项目配置仍是 compile 37 / 宿主 target 36 / inrt target 29.

| 实际安装的 6.8.0 / 5280 包 | SHA-256 |
| --- | --- |
| target 36, `apks/final36/app.apk` | `95499d4de139d04f026e1ebb57bbe81733e1517005dc8f09bd2db617d2105f11` |
| target 37, `apks/final37/app.apk` | `b068ac720b55d7efed94db1fa86e8104eca416cc2909bbba54781afcc4c6e28f` |

相对路径基于 `build/sdk37-p210-session-20260914`.
两个包来自同一生产代码, 仅使用本项目既有 target 覆盖参数切换. 最后恢复了正常 target 36 构建.
全量初次构建 4m42s, 修复后的 target 37 / 36 构建及包含更新日志的最终构建均成功,
构建中包含既有 native page-alignment 检查.
Python 探针测试 25/25 通过, 包括错误设备 / target 的预检退出不会停止宿主,
以及门控拒绝、真正取消、文件内容和实际授权的错误证据拒绝.
最后增强的判定器也重新验证了归档的 24 个场景, 没有替换原始设备回执.

## 2. 修复与调试证据

修复前, `<input type=file>` 已触发 `EventWebView.InternalChromeClient.onShowFileChooser`,
却没有打开选择器. IDEA 非暂停日志断点在 `EventWebView.kt:472` 取得:

- `activity = null`.
- `webView.context = com.android.internal.policy.DecorContext@331b799`, 两次命中.

`ui.layout` 已设置脚本 Activity 为 inflater context, 但传入 `activity.window.decorView`
作为根布局 parent. `DynamicLayoutInflater.inflationBaseContext` 优先采用 `parent.context`,
于是使用不包含 Activity 包装链的 DecorContext 创建根布局和 WebView.
`ContextUtils.findActivity` 得到 null, 文件选择处理跳过 `DelegateHost` 分支.

修复只在 parent 正是当前 Activity 的 decorView 时选择 inflater 的 Activity context.
显式 inflation context 继续优先, 普通父布局的主题 context 仍继续继承.
没有替换 WebChromeClient 或在探针里手动打开文件选择器来绕过这个生产分支.
每个正式组还验证三个条件: WebView 能解析回当前 Activity, 普通子布局保持父节点
的 `ContextThemeWrapper`, 该子布局也能解析回 Activity. 四组共 12 项 context 断言通过.

该缺陷在本机 target 36 就能复现, 不将它命名为 Android 17 平台限制.
修改位于私有布局创建逻辑, 没有新增或改变公开脚本 API 签名; 文档 / d.ts 关联仓库无需发布.
十种语言 changelog 的 v6.8.0 已补充修复条目并更新为 2026/09/14, Markdown 已由项目脚本生成.
所有本轮日志断点、Remote 配置和 adb debug forward 已清理, 原有五个用户断点保持原状态.

## 3. 实际门控矩阵

使用 `loadDataWithBaseURL` 提供本地 HTML, base origin 为独有的
`https://appassets.androidplatform.net/sdk37/<case>`; DOM 检查 `isSecureContext=true`.
这是本地安全来源夹具, 不是外部网络或 TLS 连接验证. 每次创建独有 Download 文本文件,
由实际 adb 点击提供用户操作, 然后在 DocumentsUI 中选择该文件或按返回取消.
观察生产 `show_file_chooser` / `file_chooser_result` 事件, 不修改它们的返回结果.

| target | ENABLE_FILE_SYSTEM_ACCESS | HTML 选择 / 取消 | File System Access 打开 / 取消 | 保存取消 / 目录取消 |
| --- | --- | --- | --- | --- |
| 36 | default / false | 2 次系统往返 | 2 次系统往返, GET_CONTENT | 2 次提前拒绝, 无原生 chooser 回调 |
| 36 | enable / true | 2 次系统往返 | 2 次系统往返, OPEN_DOCUMENT | CREATE_DOCUMENT / OPEN_DOCUMENT_TREE 各往返 1 次 |
| 37 | default / true | 2 次系统往返 | 2 次系统往返, OPEN_DOCUMENT | CREATE_DOCUMENT / OPEN_DOCUMENT_TREE 各往返 1 次 |
| 37 | disable / false | 2 次系统往返 | 2 次系统往返, GET_CONTENT | 2 次提前拒绝, 无原生 chooser 回调 |

开关为 364980165, 校验定义的 target 阈值 37 与有效 `packageOverrides`, 不读取 raw override
作为实际状态. 四组均保持相同 WebView provider, 并在结束时重新检查开关未变化.
`showOpenFilePicker` / `showSaveFilePicker` / `showDirectoryPicker` 在四组中都存在;
因此旧记录中的 feature detection 不能证明全部功能已经启用.

保存 / 目录在开关关闭时也返回 `AbortError`, 但没有进入原生选择器, 没有人工取消.
JSON 用 `blocked-before-chooser` 单列, 不把它算作成功的系统选择器取消.

## 4. 文件内容与授权

| 路径 | FileChooserParams | 返回授权和网页结果 |
| --- | --- | --- |
| HTML input, 两种开关 | mode 0, permissionMode 0, GET_CONTENT | flags 1, 宿主有读取权限, 无写入权限; 网页 File 内容与独有夹具完全相等 |
| File System Access open, 关闭 | mode 0, permissionMode 1, GET_CONTENT | flags 1, 宿主读取 true / 写入 false; getFile 内容正确 |
| File System Access open, 开启 | mode 0, permissionMode 1, OPEN_DOCUMENT | flags 67 (read / write / persistable), 宿主读取和写入均 true; getFile 内容正确 |
| 保存, 开启 | mode 3, permissionMode 1, CREATE_DOCUMENT | 本轮取消, resultCode 0 / URI null |
| 目录, 开启 | mode 2, permissionMode 1, OPEN_DOCUMENT_TREE | 本轮取消, resultCode 0 / URI null |

可见 `permissionMode=1` 表示请求意图, 不能替代实际 Intent / URI 授权检查.
宿主本轮没有 broad storage 授权, 通过系统选择取得指定文档的权限.
四组的 `getPersistedUriPermissions` 前后均为空; persistable 标志不代表已经保存持久授权.
本轮没有调用 `takePersistableUriPermission`, 没有把 `queryPermission(read)=granted` 推导为写入成功.

HTML input 取消在本机产生 `change` 事件和空 FileList, 已选择的值被清空.
File System Access 的用户取消产生 rejected promise / `AbortError`.
所有真实取消均检查原生 resultCode 0, URI null; 选中则检查原生 -1、实际读取许可和完整文件内容.

[官方 FileChooserParams 文档](https://developer.android.com/reference/android/webkit/WebChromeClient.FileChooserParams)
说明 API 37 增加的目录模式和读写权限模式;
[WebChromeClient 文档](https://developer.android.com/reference/android/webkit/WebChromeClient)
说明了原生 chooser 回调及取消契约. 上述门控与回调细节是本机真实 provider 的观测结果,
不把其他 Chromium 版本的源码常量当成这个安装包的实现.

## 5. 证据、失败尝试和边界

机器结果: [P2.11 files JSON](android-sdk-37-p211-files-results-20260914.json).
复跑工具与证据判定测试提交 `71db66ed1`, 入口说明见 [SDK 37 工具](../../../tools/sdk37/README.md#webview-file-access).
代表截图: [HTML 文件选择](android-sdk-37-p210-p211-screens-20260914/html-file.png),
[File System Access 打开](android-sdk-37-p210-p211-screens-20260914/open-file.png),
[保存](android-sdk-37-p210-p211-screens-20260914/save-file.png),
[目录](android-sdk-37-p210-p211-screens-20260914/directory.png).
每组保存完整脚本、回调、DOM、XML、截图、包摘要和前后设备状态.
四张选择器代表截图已人工复核; 目录页位于系统禁止授予的存储根目录,
本轮按返回取消, 没有将该页面算成取得目录授权.

探索阶段保留了根 context 缺陷、DocumentsUI 异步恢复、HTML input 取消事件差异、
native / virtual WebView 节点重叠和一次 UIAutomator exit 137 的原始输出.
修订后的工具以实际窗口、回调和 DOM 结果等待, 对非成功 hierarchy dump 有界重试.
调试期间的宿主 ANR 弹窗已保留并显式关闭; 有弹窗的尝试不进入正式通过矩阵.
这些失败不作为冷启动性能已通过的证据.

结束前恢复宿主两个 compat override, 撤销临时 SMS 权限与通知监听访问,
核对无宿主 debug-app / adb forward, 再关闭独占只读 AVD 与私有 adb server 5054.
共享 ADB、手机和 AVD 基础用户数据未改动; 清理原始记录保存在会话目录的 `cleanup/`.

剩余: 实际保存 / 写入、目录选择 / 遍历、浏览器 handle 跨重启持久化,
文档 EWebView 的图片选择专属实现、HTML Previewer 插件自身 target 37,
以及实体机 UI04 的输入、复杂字体和文件交互. P2 / P3 / P5 不整体关闭.
