# P6.1 media3 -> 平台播放器证据 (2026-09-18)

决策 D11 (Q2): `ScriptMusicService` 改用 `android.media.MediaPlayer` + 平台 `android.media.session.MediaSession` + `Notification.MediaStyle`, 不引入新依赖; 回归清单为脚本 `media` 模块的公开行为与 [android-sdk-37-media3-20260914.md](android-sdk-37-media3-20260914.md) 记录的前台服务 / 通知 / 焦点用例 (`app/src/androidTest/java/org/autojs/autojs/core/media/ScriptMusicDeviceTest.kt`, 11 项, 后端无关: 它经通知的 `EXTRA_MEDIA_SESSION` 令牌用平台 `MediaController` 驱动会话).

## 实现

- `core/media/ScriptMusicService.kt` 由 `MediaSessionService` 改为普通 `Service`; `ScriptMusicPlayer.kt` (租约, 主 looper 串行, 30 s 就绪等待) 与 `runtime/api/Media.java` (公开方法) 未改.
- 播放器: 单个 `MediaPlayer`, `USAGE_MEDIA` / `CONTENT_TYPE_MUSIC` 属性, `setDataSource(path)` (仍只接受可读的本地文件) + `prepareAsync`, `isLooping` 实现单曲循环, `setVolume` 实现请求音量, API 26+ 用 `seekTo(pos, SEEK_CLOSEST)`; 就绪 (`onPrepared`) 后申请焦点并 `start`, 再完成脚本侧的 `ready`; `onCompletion` 记为 `ended` (时长保留, 位置报告为时长, 下次 `resume` 从头开始; 完成后的 `seekTo` 清除 `ended`, 与 ExoPlayer STATE_ENDED -> READY 一致); `onError` 以 "Cannot play the requested audio file." 释放请求.
- 音频焦点: `AudioFocusRequest` (API 26+) / 旧 API (24 / 25) 申请 `AUDIOFOCUS_GAIN`, 从首次播放持有到停止 / 释放 (ExoPlayer 的自动焦点处理也只在停止时放弃); `LOSS_TRANSIENT` 暂停并在 `GAIN` 恢复, `LOSS_TRANSIENT_CAN_DUCK` 音量乘 0.2 并在 `GAIN` 还原, `LOSS` 暂停不自动恢复; 焦点申请被拒时以原 `RECOVERY_MESSAGE` 释放请求 (ExoPlayer 在此情形以 `AUDIO_FOCUS_LOSS` 原因置 `playWhenReady=false`, 原代码对未就绪的请求同样释放).
- 耳机拔出: `ACTION_AUDIO_BECOMING_NOISY` 接收器 (`RECEIVER_NOT_EXPORTED`) 只在播放期间注册, 收到即暂停 (对应 ExoPlayer `setHandleAudioBecomingNoisy(true)`).
- 会话: `MediaSession` 带 `FLAG_HANDLES_MEDIA_BUTTONS | FLAG_HANDLES_TRANSPORT_CONTROLS` (API 24 / 25 需要), 元数据为文件名, `PlaybackState` (NONE / BUFFERING / PLAYING / PAUSED, 动作 PLAY / PAUSE / PLAY_PAUSE / SEEK_TO / STOP) 在每次状态变化时发布, 拥有请求期间 `isActive`; 回调 `onPlay` / `onPause` / `onSeekTo` / `onStop` 只作用于当前拥有者, `onStop` 释放请求 (原 `COMMAND_STOP` 语义). 与 media3 不同之处: 平台会话不再按 uid / 可信度拒绝控制器, 令牌只经本应用的通知 (`MediaStyle`) 暴露给通知监听者.
- 通知: 沿用 `promote()` 的顺序 (准备 / 播放前 `startForeground`, API 29+ 为 `mediaPlayback` 类型, API 34+ 检查 `FOREGROUND_SERVICE_MEDIA_PLAYBACK`), 通知增加 暂停 / 播放 与 停止 两个动作 (`PendingIntent.getService` 到本服务, `onStartCommand` 处理, 服务在通知存在期间总在前台), 紧凑视图显示两者; 状态变化时 `notify` 刷新 (原由 `DefaultMediaNotificationProvider` 自动刷新).
- 清单去掉 `androidx.media3.session.MediaSessionService` intent-filter (服务仍为 `foregroundServiceType="mediaPlayback"`, 未导出); `app/build.gradle.kts` 删除 `libs.media3.*` 依赖段, `gradle/libs.versions.toml` 删除 `media3` 版本与两个坐标.

## 设备验证 (`ScriptMusicDeviceTest`, 宿主 debug 6.8.0 (5281) + androidTest APK, `am instrument -w -r -e class ...ScriptMusicDeviceTest`)

| 环境 | 结果 |
| --- | --- |
| AVD API 24 (x86, Android 7.0) | 11/11 |
| Sony G8441 (API 28, Android 9) | 11/11 |
| Redmi 22120RN86C (API 33, Android 13; 测试前 `pm grant POST_NOTIFICATIONS`, 测试后撤销) | 11/11 |

三个环境首次运行即全部通过, 无重试. 用例: 同步时长 + 播放 / 暂停 / 定位 / 恢复 / 停止与通知释放; 被接管的运行时不能控制或释放新拥有者; 同一脚本连续四次替换; 不存在 / 无法解码的文件清理后可重试; 无效路径 / NaN 音量不接管他人播放; UI 线程与已关闭运行时拒绝; 中断的排队请求不能迟到启动; 平台 `MediaController` 的暂停 / 定位 / 播放 / 停止; 暂时焦点丢失暂停, 焦点恢复后继续; 自然完成保留时长且再次恢复从头开始; 真实 Rhino 脚本经公开 `media` 方法播放并在运行时结束后释放. 原始输出 `build/tmp-size/p6_1_instr_<serial>.txt`.

## 脚本冒烟 (`build/tmp-size/p6-1-smoke.js`, 宿主内生成 5 s 静音 WAV, 音量 0)

| 项目 | AVD API 24 | Redmi API 33 |
| --- | --- | --- |
| `androidx.media3.exoplayer.ExoPlayer` / `com.google.common.collect.ImmutableList` | 不存在 / 不存在 | 不存在 / 不存在 |
| `media.playMusic` (循环) -> `isMusicPlaying` / `getMusicDuration` | true / 5000 | true / 5000 |
| 通知 (`dumpsys` 等价的 `activeNotifications`) | 标题为文件名, 动作 [Pause, Stop], `android.mediaSession` 存在, 模板 `MediaStyle` | 同左 |
| `pauseMusic` -> `musicSeekTo(2000)` -> `resumeMusic` | false / 2000 / true 2210 | false / 2000 / true 2302 |
| 通知动作 PAUSE intent / PLAY intent | 暂停 (动作变为 [Play, Stop]) / 恢复 (动作变回 [Pause, Stop]) | 同左 |
| adb `KEYCODE_MEDIA_PAUSE` (平台媒体按键 -> `MediaSession`) | 暂停 | 暂停 (logcat `MediaSessionService: Media button session is changed to org.autojs.autojs6/ScriptMusicService`, 按键送达后 500 ms 内 `isMusicPlaying` 由 true 变 false) |
| 通知动作 STOP intent | 停止: playing=false, pos=-1, dur=0, 通知消失 | 同左 |
| 停止后再播放 + `stopMusic` | 通过, 通知消失 | 通过 |
| 非循环自然完成 (定位到 4700) -> `resumeMusic` | pos=5000 dur=5000 -> 从头 (pos 182) | pos=5000 -> 从头 (pos 253) |

Redmi 首轮冒烟时 adb 按键落在 "暂停 / 定位 / 恢复" 步骤内 (脚本首次启动较慢), 表现为该步骤后 `isMusicPlaying=false`; 把按键推迟到 +11 s 并逐 500 ms 记录后确认按键在到达时暂停, 与 AVD 一致.

## 体积

[apk-size-p6-1-20260918.md](apk-size-p6-1-20260918.md): universal 33,312,764 字节 (对 P5.1 -2,556,263 = -2.44 MiB; 对基线 -7,833,291 = -7.47 MiB, -19.0%), arm64-v8a 32,115,738 字节, DEX 文件 9 -> 8, 定义方法 310,704 (P5.1 352,078), `androidx.media3` 2.18 -> 0 MiB, `com.google.common` 1.13 -> 0.00 MiB (剩 1 个方法, 来自其他传入路径), 条目 3930.

## changelog

6.8.0 内 media3 先 "附加" 后移除, 按 D7 删除 "附加 AndroidX Media3 ExoPlayer / Session 版本 1.11.0" 条目 (10 语言); 同版本 `improvement` 的 "media.playMusic 使用 Media3 音乐前台服务, ..." 改为 "使用音乐前台服务" (10 语言, 功能描述不变), 不另加内部实现更换的条目. 公开方法签名不变, d.ts 与文档项目无需改动.
