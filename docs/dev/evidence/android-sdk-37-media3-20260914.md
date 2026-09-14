# P2.2 / Q3: Media3 脚本音乐服务

日期: 2026-09-14. 源码基线: `a98ed81b3`, 本记录与实现一并提交. 对应已确认的 Q3=(b) / D14. 本轮完成播放后端与生命周期迁移, 不关闭整个 P2.2.

## 实现

- 引入直接依赖 `androidx.media3:media3-exoplayer:1.11.0` 和 `androidx.media3:media3-session:1.11.0`. 版本取自 [Media3 官方发布记录](https://developer.android.com/jetpack/androidx/releases/media3). 没有调整正式 target, 编译仍为 37, 宿主 / inrt target 仍为 36 / 29.
- `runtime/api/Media.java` 保留全部公开方法签名, 将音乐方法交给 `core/media/ScriptMusicPlayer.kt`. 扫描仍使用原媒体扫描连接; 回收时即使断开扫描连接发生异常, 也会关闭音乐租约. 冻结的 `com/stardust/**` 未修改.
- `ScriptMusicService.kt` 是独立的 `MediaSessionService`, 使用 `mediaPlayback` 类型, 只绑定本应用, 未导出. 通用 `AppForegroundService` 保留原类型. 宿主和 inrt 复用该服务.
- 通过 `USAGE_MEDIA` / `CONTENT_TYPE_MUSIC` 配置 ExoPlayer 自动焦点管理和耳机断开处理. 在准备 / 播放请求焦点之前先发布媒体通知并启动前台状态, 随后由 Media3 更新通知和系统媒体控制. 这不绕过 [Android 17 后台音频规则](https://developer.android.com/about/versions/17/changes/bg-audio) 或 FGS 启动限制.
- 同一应用只有一个活动脚本音乐播放器. 新的有效播放请求接管所有权; 旧请求解绑, 旧脚本的暂停 / 恢复 / 定位 / 停止 / 回收不影响新请求. 无法读取的文件和非法音量在接管前拒绝. 不在多个脚本之间混音.
- 每个请求有独立连接和完成对象. 所有播放器和连接操作在主 Looper 上串行进行; 脚本工作线程同步等待准备完成, 最长 30 秒. 线程中断, 超时, 脚本回收或接管后, 迟到的连接不能重新启动已取消的请求. 回收不依赖已经中断的脚本线程继续等待.
- 系统媒体控制允许可信控制器连接, 不开放替换播放列表 / 文件的命令. 媒体通知使用文件名, 不显示完整路径. 停止和脚本退出后清理播放器内容, 通知和服务绑定.
- Manifest 与新项目默认权限加入 `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, 打包权限页增加对应说明. 11 份字符串资源新增两个条目, 默认与英文一致, 位置和半角标点检查通过.

## 脚本行为与入口边界

1. `playMusic` 仍在返回前准备好时长, 但必须从工作线程调用. UI 线程给出已有的本地化阻塞提示; UI 回调可使用 `threads.start`. `volume` 必须是 `0..1` 的有限数值, 不修改系统流音量.
2. 被其他脚本接管或停止后, `isMusicPlaying` 为 false, 时长为 0, 位置为 -1. 自然播放结束后仍保留时长, `resumeMusic` 从头开始. 焦点暂时丢失的恢复交给 Media3.
3. 原通用前台服务的可见界面恢复说明继续用于其他音频路径. `media.playMusic` 自动连接专用音乐服务, 不要求额外开启该通用开关. 后台启动被拒绝后应回到可见界面重试, 不能以服务存在或进度增长证明可听.
4. 已有打包项目如果自定义了权限列表, 需要加入媒体播放前台服务权限后重建. 本轮验证的是共享 inrt 程序和其 Manifest, 没有把它等同于最终用户项目的打包 / 首次启动 / 权限迁移验收.
5. `NodeBridgeProtocol.dispatchMedia` 当前只实现音量查询 / 设置, 并未开放脚本音乐播放. 本轮审查后保留原契约, 不把 Rhino 后端迁移当作 Node 播放能力上线. TTS 使用独立语音引擎; 合成文件不要求音乐服务运行.

公开方法签名不变, 检查 `AutoJs6-TypeScript-Declarations/declarations/autojs6/aj6-int-media.d.ts` 后无需生成新声明. `AutoJs6-Documentation` 的 Media / progress 已更新并生成, 文档提交 `b20d960`; 离线文档插件提交 `2cc6692` 同步了相同内容, contentVersion 仍为 6.8.0, 插件版本为 6.8.1 / build 40.

## 设备验证

[机器可读结果](android-sdk-37-media3-results-20260914.json) 包含每个用例的名称, 安装包 SHA-256, 目标版本, JUnit 输出和历史失败记录. 验证器按完整测试名集合匹配, 不把缺失或跳过的测试计入通过.

| 系统 | 应用 | target | 最终结果 |
| --- | --- | --- | --- |
| Android 17 / API 37 | 宿主 debug | 37 | 11/11 |
| Android 17 / API 37 | inrt debug | 37 | 11/11 |
| Android 17 / API 37 | 宿主 debug | 36 | 11/11 |
| Android 17 / API 37 | inrt debug | 29 | 11/11 |
| Android 7.0 / API 24 | 宿主 debug | 36 | 11/11 |
| Android 7.0 / API 24 | inrt debug | 29 | 11/11 |

最终 66/66. 两个只读临时 AVD 分别为 `AVD_API_37` / `emulator-5598` 和 `AVD_API_24` / `emulator-5594`. 前者报告 getconf PAGESIZE=16384, 不作为真 16 KB 实体设备的验收. 指纹保存在 JSON 中.

测试使用应用私有目录的 5 秒无声 PCM WAV, 播放器音量为 0. 未修改系统音量, 未申请麦克风, 未重启设备, 未运行 TTS. 通过可见 LogActivity 开始测试, 所以不计作独立后台启动的负例或 WIU 恢复测试.

11 项覆盖:

- 同步时长, 播放 / 暂停 / 定位 / 恢复 / 停止和通知释放.
- 新脚本接管后, 旧脚本操作和退出不会停止新脚本.
- 同一脚本连续四次替换播放请求.
- 不存在 / 无法解码的文件清理, 以及失败后重新播放.
- 无效路径 / NaN 音量不会接管另一脚本.
- UI 线程和已回收运行时拒绝播放.
- 工作线程中断后, 先前排入主线程的请求不能迟到启动.
- 平台 MediaController 的暂停 / 定位 / 播放 / 停止.
- 实际暂时音频焦点丢失时暂停, 释放竞争焦点后恢复.
- 自然完成后保留时长, 再次恢复从头开始.
- 真实 Rhino 脚本通过公开 `media` 方法播放, 运行时结束后释放.

每组结束后 `dumpsys activity services` 均不再包含 `ScriptMusicService`. API 24 宿主快照原有的系统无障碍绑定不作为音乐服务残留.

### 保留的失败与调试证据

最初 9 项宿主测试在 API 24 / 37 上各通过 9/9. 增加焦点和自然完成用例后, 首轮 target 37 是 10/11, 失败的是自然完成测试.

原断言仅等待 `!isPlaying`, 将定位到 4700 ms 时的短暂缓冲当成文件结束, 随即调用 `resume`. IntelliJ 暂停栈为 `ExoPlayerImpl.getPlaybackState:570 -> ScriptMusicService.resume:118 -> ScriptMusicPlayer.resume:58`. 直接读取播放器字段得到:

```text
playbackState = 2 (BUFFERING)
playWhenReady = true
positionUs = 4700000
durationUs = 5000000
playbackSuppressionReason = 0
playbackError = null
```

测试改为同时等待位置到达 4999 ms 以后, 再判断停止并验证从头恢复. 没有因此修改生产播放逻辑. 修正后的六组均为 11/11.

初始两次 `-e debug true` 附加方式产生 `BIND APPLICATION ANR`, 不作为正常启动的失败样本. 改为测试启动后延迟执行并附加调试器. 非暂停日志表达式因 Kotlin 求值器无法解析属性而失败, 随后检查暂停栈与字段; 没有把失败表达式当作运行值. 最后清理了临时日志点 / 连接 / 配置, 5 个用户异常断点保持原状, 其中 2 个启用.

## 构建与可重现性

- 首轮 Kotlin / Java 编译通过, 4m33s.
- 宿主 / inrt debug 与两个 androidTest APK 构建通过, 6m26s.
- 双 target 37 debug 验证构建通过, 1m50s. 修正测试后的两个 androidTest APK 重新构建通过, 55s. target 37 应用包复用此前归档的同一生产实现, 更新测试 APK; 相关哈希分别记录.
- 正常宿主 / inrt 的 debug / release 构建通过, 6m54s; 最终正常 debug / androidTest 输出恢复通过, 25s. release 只做构建和包检查, 本轮没有把音乐 instrumentation 安装到 release 上运行.
- 6 个应用 APK 的 target, 未导出的媒体服务, `mediaPlayback` 类型, 对应权限和签名均已核验. 构建的原生 16 KB 对齐任务通过; 不等同于所有真实页大小环境的运行验收.
- 在线文档 129 模块规范化 / freshness 检查通过. BAT 先 dry-run, 再 `--verify-offline` 同步. 离线插件 debug / release 内容门禁通过: 185 个文档资产, 10610003 bytes, canonical SHA-256 `a5bf8db715cf70eecad324195ec6cb69aa9c9ecabf385322252667afa11ccd4e`. 更新日志 / README 25 个生成物检查通过.
- 未重跑主项目完整 Lint. 两个 release 的 lintVital 仍各报告 1 个既有 `RemoveWorkManagerInitializer` Fatal, 与 [P0 / P1 基线](android-sdk-37-p0-p1-20260913.md) 一致. 项目 `abortOnError=false` 使构建继续成功; 本轮未 suppress 此项或调整 WorkManager 初始化语义, 不宣称 release Lint 门禁通过.
- 最终归并了同版本中旧的 Media3 完全模块化依赖条目, 再生成日志并重打正常四个应用 APK, 1m12s. 宿主 debug 与已测包仅有 11 个 CHANGELOG 资产不同, DEX / resources 相同; inrt debug 整包相同. 四个最终 APK 的 target 和签名再次通过检查, 哈希记录在 JSON 的 finalOutputChecks 中.

设备用例位于 `app/src/androidTest/java/org/autojs/autojs/core/media/ScriptMusicDeviceTest.kt`. 对已安装的对应 debug / androidTest APK, 可选择明确设备后运行:

```powershell
adb -s <selected-serial> shell am instrument -w -r -e class org.autojs.autojs.core.media.ScriptMusicDeviceTest org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner
```

inrt 的 runner 包名改为 `org.autojs.autojs6.inrt.test`. 用例会打开 LogActivity 并申请 / 释放媒体焦点, 执行前应确认设备当前没有需要保留的音频任务. 本轮原始构建 / 安装 / JUnit / Manifest / 调试输出位于忽略目录 `build/sdk37-media3-20260914/`.

## 剩余门禁与清理

- Samsung `localhost:37478` 在安装前已断开, 重新连接未成功; 本轮没有修改该真机或其他实体设备.
- 待验: 无 instrumentation 特权的后台 FGS 拒绝与可见恢复, 真机扬声器 / 耳机 / 蓝牙与锁屏可听性, 全部定时后端和真实重启, 实际 TTS 引擎与独立播放器插件, API 35 限制组合, 最终用户打包项目的权限列表迁移.
- `AU01-AU03` / `AU05-AU06` 和 P2.2 总体验收保持未完成. 仅有音乐进度, 焦点成功或通知不能关闭这些门禁.
- 两个 AVD 使用 `-read-only -no-snapshot-save`, 核对进程创建时间 / 路径和 AVD 名称后均已停止. 临时 JDWP 转发和运行配置已移除, 共享 ADB 进程 31400 保留. 详细清理记录见 JSON.
