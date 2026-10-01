# 安装器宿主三入口真机验收

日期: 2026-10-01. 本文记录插件路线图 P1.4 / P2.6 的宿主文件管理器 APK, 文件管理器 XAPK, 插件中心 URL 三处实际界面, 分别在安装器插件未安装和已安装时的六个最终 case. 宿主为 AutoJs6 6.8.0 / build 5304 Debug, 安装器插件为 3-Setup Installer / build 41 Debug.

验收使用 Redmi 真机 `bek749scrwv4wo8h`, Android 13 / API 33, arm64. 正式宿主包为 `org.autojs.autojs6`, 实际 UID 为 `10778`. 每个 case 开始前枚举用户 `0` 和 `999`, 拒绝复用已安装或保留数据的测试包. 无插件阶段先执行, 当时安装器插件确实未安装; 随后才安装插件执行有插件阶段, 没有通过卸载用户原有安装器插件制造退化条件.

## 实际界面与证据口径

测试类为 `org.autojs.autojs.core.plugin.installer.InstallerHostEntryDeviceTest`, 位于宿主 `app/src/androidTest`. 测试可以把真实文件管理器导航到自有夹具目录, 安装动作则来自生产页面上的实际按钮. 它不直接调用 `PackageInstallRouter`, 不替换调用 UID, 不使用插件内部测试 Binder 入口.

| 入口 | 本轮最终使用的真实 UI 路径 | 无安装器插件 | 有安装器插件 |
| --- | --- | --- | --- |
| 文件管理器 APK | MainActivity 文件页 -> 对应 APK 行 More -> Information -> 信息对话框 Install | 系统安装器确认并安装成功 | 插件确认 -> 系统确认 -> 安装成功 |
| 文件管理器 XAPK | MainActivity 文件页 -> 对应 XAPK 行 Install 图标 | 宿主显示需要安装器插件的引导, 取消后测试包仍不存在 | 插件确认 -> 系统确认 -> 安装成功 |
| 插件中心 URL | PluginCenterActivity 添加菜单 -> Install from URL -> 输入 loopback URL -> Retrieve -> 实际下载后的信息对话框 Install | 系统安装器确认并安装成功 | 插件确认 -> 系统确认 -> 安装成功 |

APK 的最终证明口径是 `More > Information > Install`, 不是文件名默认点击. 设备已有且保留启用的 APK Inspector 会占用文件行主动作, 因此最终用例明确等待 Inspector 主按钮出现且独立信息图标隐藏, 再访问更多菜单. 未禁用 Inspector, 未更改 APK 默认应用, 未利用插件发现尚未完成的时间窗口取得最终通过结果.

有插件时, 宿主正式发现和选择的 id 必须为 `three-setup-installer`, 插件自己的真实窗口必须显示固定夹具身份. 本轮实际经过系统确认完成安装, 不是 Shizuku / Root 静默安装矩阵. API 33 的这六项也不单独证明 API 34+ 的 `BIND_ALLOW_ACTIVITY_STARTS` 或后台通知行为; 这些仍使用对应专项设备证据.

## 真机验收暴露的宿主入口缺口

APK Inspector 被发现后, 原 `ExplorerItemActionPolicy` 会保留 Inspector 主按钮和 Install 按钮, 隐藏信息图标; 原文件行 More 菜单又没有宿主 Information 项. 因而用户在保持 Inspector 启用时, 没有稳定的宿主包信息入口. 初始用例在发现前后得到不同的文件行动作, 暴露了这一实际可达性缺口.

本轮最小生产修复限于 `ExplorerItemViewHolder` 和 `menu_script_options.xml`: 仅当可安装包文件的行信息图标被隐藏时, 在 More 菜单提供现有本地化 `text_info` 对应的 Information. 菜单调用既有 `showInfo`, 同时捕获打开菜单时的文件对象和主插件动作, 防止行重绑定后展示其他文件. Inspector 默认点击和主按钮保持原行为, 安装按钮继续走正式 `PackageInstallRouter`, 不变更公开 JS API 或 Installer V1 契约.

十语言 v6.8.0 fix 日志与相应 README / CHANGELOG 已同步. 生成复用原生成器的渲染函数, 仅更新发行历史块, 没有顺带刷新在线元数据.

## 固定夹具与真实 URL 下载

APK 来自宿主 `app/src/androidTest/assets/installer/fixture-v1.apk`, 对应插件仓库的既有无代码 spike fixture:

| 属性 | 实际值 |
| --- | --- |
| package | `io.github.supermonster003.autojs6.installer.spike.fixture` |
| versionCode | `1` |
| label | `3-Setup Spike Fixture` |
| APK 大小 | `8,536` 字节 |
| SHA-256 | `fec583a389978fdc298e9d85979b95feceb93e6fb3fd3f581511c826401e8b69` |
| 应用内容 | `hasCode=false`, 无 Activity / Service / Provider / Receiver, 无 DEX 和原生库 |
| XAPK 内容 | 本次生成的 ZIP 容器, 仅包含上述 APK, 条目名 `base.apk` |

文件管理器来源位于宿主私有 `files/p1-installer-entry/<case>/sources`. URL 路径使用独立本机 HTTP 目录中的同一 APK, 文件名为 `installer-entry-<case>.apk`, HTTP 服务仅监听 `127.0.0.1`, 由明确的 `adb reverse` 端口映射供设备访问. 测试仅接受 `http://127.0.0.1:<port>/installer-entry-<case>.apk`, 不接受其他主机, query, fragment 或任意路径.

最终无插件 URL case 和有插件 URL case 均使用端口 `49408`. 实际点击 Retrieve 后经过生产下载代码, 验证 `cache/plugin_dl` 中下载文件以及 `cache/from_uri/import-<UUID>` 中导入文件的 SHA-256 均与固定 APK 一致. 不是绕过下载后直接提交本地文件.

驱动在测试写出 `ready.json` 后完成真实插件/系统确认, 等待实际成功并关闭结果页, 再写本 case 的 `driver-complete.json`. 安装用例随后通过实际 `pm path` 对应的已安装 APK 再次验证 SHA-256, 包名, `versionCode=1` 和 `hasCode=false`. 无插件 XAPK 是指导/拒绝路径, 不安装夹具, 不伪记 `installedVersion`.

## 六个最终结果

下列六份最终 instrumentation 日志均为 `OK (1 test)`, 无跳过. 每个对应 `result.json` 均为 `passed=true`, `hostStateRestored=true`. 表中耗时是完整 instrumentation 用例耗时, 含 UI 等待和驱动人工确认等待, 不作为安装性能指标.

| 状态 / 入口 | 最终 case | 用例耗时 | 结果 | 宿主本地日志 |
| --- | --- | --- | --- | --- |
| absent / APK | `p1-absent-apk-20261001c` | 144.265 s | 实际安装 v1 | `build/p1-absent-apk-menu-redmi33.log` |
| absent / XAPK | `p1-absent-xapk-20261001c` | 10.366 s | `plugin-required`, 已取消, 包不存在 | `build/p1-absent-xapk-install-redmi33.log` |
| absent / URL | `p1-absent-url-20261001c` | 36.011 s | 实际安装 v1 | `build/p1-absent-url-redmi33-final.log` |
| present / APK | `p1-present-apk-20261001a` | 61.771 s | 实际安装 v1 | `build/p1-present-apk-redmi33.log` |
| present / XAPK | `p1-present-xapk-20261001b` | 43.842 s | 实际安装 v1 | `build/p1-present-xapk-redmi33-verified.log` |
| present / URL | `p1-present-url-20261001a` | 48.587 s | 实际安装 v1 | `build/p1-present-url-redmi33.log` |

本地原始输出归档于 `build/p1-entry-evidence/<case>/device-evidence.tar` 及相邻提取的 JSON. 构建目录不提交, 本文保留 case 和日志定位用于核对. 每个 case 包含按步骤记录的截图和节点 JSON, `ready.json`, `result.json`, `restore.json`; 临时启用插件的 case 另含 `enable-restore.json`. `fileEntryGesture`, `visibleFileActions` 和 `touches` 保存实际入口, 行按钮集合, 窗口 id 和触摸坐标, 避免把不同动作混称.

## UI 稳定性与恢复

最终按钮操作使用 `UiAutomation.injectInputEvent` 发送真实触屏 down/up. 触摸前要求节点与实际屏幕和活动窗口的交集非空, bounds / windowId / 文件行可见动作集合持续稳定至少 500 ms. 文件管理器另要求实际 MainActivity 有窗口焦点, 当前是文件页及本次 sources 目录, RecyclerView 空闲, 无待处理布局或条目动画. `ACTION_CLICK` 返回 true 不作为目标界面已经打开的证据.

测试在任何导航前以 AtomicFile 持久保存以下 4 个 key 的存在状态和值, 仅恢复这些 key:

| key | 本设备本轮原始值 |
| --- | --- |
| `ExplorerView.page_state` | 存在, 整数 `0` |
| `ExplorerView.explorer_current` | 存在, `/storage/emulated/0/脚本` |
| `ExplorerView.explorer_root` | 存在, `/storage/emulated/0/脚本` |
| `ExplorerView.explorer_history` | 存在, 字符串 `[]` |

后续生命周期修正将本 case 的 hostTaskId 和 intent case 标记写入 journal. 恢复只结束该 task 中的 MainActivity / PluginCenterActivity 实例, 等全部达到 AndroidX `Stage.DESTROYED` 且 1 秒没有相关生命周期事件, 再在主线程 commit 原导航值并稳定核验至少 1 秒. 不结束其他任务, 不修改生产导航生命周期规则. 最终 present XAPK / URL 的 taskId 分别为 `1819` / `1822`, 各记录 1 个已销毁宿主实例. 之前已通过的四个 case 不补写未采集的 taskId.

present case 使用已有 Debug `InstallerProbeEnablement` 的单 key 持久事务. 最终三项的启用 journal 均为 `originalPresent=false`, `originalEnabled=true`, 恢复后 `restoredPresent=false`, `restoredEnabled=true`, `pending=false`, `phase=restored`. 没有回写整个偏好文件, 没有修改信任信息, 插件优先级, Inspector 偏好或系统默认应用.

下载清理先规范化可信 `context.cacheDir` 根, 再拼固定子目录和本次 leaf. 本设备 Java 路径的实际对应是 `/data/user/0/.../cache` -> `/data/data/.../cache`. 子目录及 leaf 的 symlink 拒绝和固定摘要校验仍保留. 只有路径, 摘要和归属均匹配的下载/导入/来源被删除, 不清空宿主缓存.

新 case 拒绝覆盖旧目录或任一 pending journal. 单独的 `restoreInterruptedEntry` 可使用原 case 恢复. 旧 journal 若没有 taskId, 仅在当前不存在任何活动 MainActivity / PluginCenterActivity 时恢复, 不猜测应结束的用户任务.

## 保留的初轮失败与补充结果

以下失败均保留原日志和 case. 它们不计入上面的最终六项通过, 不把不同失败轮次的局部结果拼成一次成功.

| 初轮 case / 日志 | 观察与处理 |
| --- | --- |
| `p1-absent-apk-20261001a`, `build/p1-absent-apk-redmi33-initial.log` | 原文件名点击曾完整通过, 作为补充保留; 它发生于异步插件发现条件下, 不替代后续稳定的 More > Information 最终 APK 路径 |
| `p1-absent-xapk-20261001a`, `build/p1-absent-xapk-redmi33.log` | 真实引导已出现, 但测试把生产名称 `3-Setup Installer` 错写为 `Three Setup Installer`; 改用生产显示名称常量, 原轮次仍为失败 |
| `p1-absent-xapk-20261001b`, `build/p1-absent-xapk-redmi33-verified.log` | 文件名 bounds 不变, 但 Inspector 发现后行按钮从 Install + Information 改为 Inspector + Install, 未出现预期宿主信息页; 修复真实信息入口, 最终 XAPK 改用文件行正式 Install 图标, 没有禁用 Inspector |
| `p1-absent-url-20261001a`, `build/p1-absent-url-redmi33.log` | 下载和实际安装成功, finally 将系统根路径别名误判为来源变化; 保留摘要及 leaf 守卫, 改为先规范化可信缓存根, 单独恢复后重新执行最终 URL case |
| `p1-absent-url-20261001b`, `build/p1-absent-url-redmi33-verified.log` | 已进入真实系统确认并写 ready, 驱动未在 180 s 内完成 marker, 最终因驱动等待超时失败; 本轮源清理/宿主恢复成功, 不能称 UI 挂死或安装通过 |
| `p1-present-xapk-20261001a`, `build/p1-present-xapk-redmi33.log` | 实际安装 v1 成功, finally 只等初始 instance.isDestroyed, 四导航 key 又被迟到生命周期清除; 修正为限定 task 的实例追踪, DESTROYED 屏障及稳定恢复, 保留初轮失败 |

URL 源恢复用例 `build/p1-absent-url-recovery.log` 为 `OK (1 test)`, 0.063 s; present XAPK 导航恢复用例 `build/p1-present-xapk-recovery.log` 为 `OK (1 test)`, 2.249 s. 这两项只证明恢复动作, 不替代完整安装用例. 对应中断 journal 恢复为 `pending=false`, `phase=restored` 后才继续新 case.

最初 URL 清理失败的旧 `result.json` 曾在安装结束时写入 `passed=true`, instrumentation 仍明确失败. 该原始记录不改写; 最终测试已修正为任何 finally 失败都记录 `passed=false`, `hostStateRestored=false` 和 `cleanupFailure`.

## 构建核验与当前边界

本轮两份已完成的完整宿主构建日志为:

- `build/p1-host-entry-build.log`: `BUILD SUCCESSFUL in 17m 16s`, 包含 App Debug, Inrt Debug, androidTest, 全量 `testAppDebugUnitTest` 和 `lintAppDebug`
- `build/p1-host-information-fix-build.log`: `BUILD SUCCESSFUL in 11m 37s`, 包含 More Information 修复后的对应完整检查

两轮 lint 使用 12 GB Gradle 堆. 此后测试结果 XML 目录被其他定向测试覆盖, 因此不从其当前条数反推本次完整 JVM 数量; 全量通过结论以两份完整构建日志为准.

最后的生命周期测试修正对应 `build/p1-host-lifecycle-recovery-build.log`, 已完成 `assembleAppDebugAndroidTest` 和 `lintAppDebug`, `BUILD SUCCESSFUL in 11m 38s`. 该轮报告修改时间为 2026-10-01 22:06:58, 汇总为 0 Error / Fatal, 2399 Warning, 3 Hint. 上表最后两项实际设备测试也使用这版生命周期恢复代码通过. 没有因测试计数文件被覆盖而重复全量构建.

## 最终设备清理回填

主驱动已完成本轮清理, 原始记录位于 `build/p1-entry-evidence/`:

- 每次成功安装后按已核验的固定 APK 归属移除测试包, 最后在用户 0 / 999 中核验包和保留数据均不存在.
- Redmi 的插件本轮新安装, 原历史文件不存在. 四条实际安装产生的记录逐条核验 package/version/origin 和 session ID 后移除, 没有清空任意历史. `plugin-history-cleanup/result.json` 记录 4 条确切 ID, `preservedCount=0`, 操作前/后 SHA-256 分别为 `28288e405cd2abe41584bb2be7a7d1afcbaf13440335b6d760789cff55b73302` / `b846bb61fb6963a768b4a393a481a66005e6b182d8b4c3aacfb5af36946ee7be`.
- 12 个成功/失败 case 的恢复 journal 均为 `pending=false`, `phase=restored`, 对应证据归档后只移除这 12 个自有目录中的已核验文件. `remote-cleanup.json` 记录 `allJournalsRestored=true`, `privateCasesRemoved=true`. 来源已由各自测试按摘要清理, 没有清空宿主缓存.
- 两个 loopback HTTP 子进程已停止, 本轮 38543 / 60035 / 49408 的 adb reverse 映射均移除. 固定 HTTP 来源保留在宿主本地忽略目录 `build/p1-http/`, 不留设备网络监听或服务.
- 宿主和插件的 `REQUEST_INSTALL_PACKAGES` 包级/UID 级原模式均恢复为 `default`; 插件仓库持久 journal `4c313b23e20947be8f69e5a6ed678c15` / `5ccf70935aa948b1b4364eb1198d4bdd` 均 `pending=false`, `phase=restored`. 四导航 key 和单一插件启用 key 按上述记录恢复, APK Inspector 和系统默认应用未修改.
- 最新源码对应的最终构建/lint 结论已回填上一节.

本轮仅本地提交, 不推送或发布. 本文不修改或重组插件 ROADMAP 的原 checkbox; 原条目的最终勾选由主驱动结合上述完成证据和最终清理状态统一处理.
