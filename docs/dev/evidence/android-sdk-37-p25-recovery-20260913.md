# SDK 37 P2.5: 后台安装确认恢复

日期: 2026-09-13. 起点: 宿主干净工作区 `ead79cb00`. 正常配置保持 compileSdk 37, 宿主 target 36, inrt target 29. 本轮关闭 [roadmap P2.5](../android-sdk-37-roadmap.md#p25-后台活动启动-i5) 中的后台安装恢复条目, 不关闭整个 P2.5 或最终 target 37 门禁.

代码, 单元 / 设备测试及多语言日志提交为 `05ad029fd`; 实际 UID 恢复探针与复跑工具提交为 `faf16a76a`. 本轮两个临时 AVD 已通过各自 serial 核对名称后关闭.

## 问题与实现

前一轮已经在同源 target 36 / 37 上验证: 无后台 Activity 启动资格时, `Session.commit()` 返回 session ID, 但系统发出的 Activity PendingIntent 无法打开私有安装确认入口. 创建方 `ALLOW_ALWAYS` 不会创造启动资格. 原始对照保留在 [P2.5 证据](android-sdk-37-p25-20260913.md), 本轮不把该问题重新解释为仅 target 37 才发生.

本轮实现如下 (SOURCE):

1. `AndroidPackageSessionInstaller` 在 commit 前注册会话, 使用按 session ID 区分的 mutable Broadcast PendingIntent. 新增的 `PackageInstallStatusReceiver` 不导出且不启动 Activity, 校验 action, data, 预期和实际 session ID; 未登记或已结束会话的回调被忽略.
2. `PackageInstallStatusStore` 使用 `noBackupFilesDir` 下的 AtomicFile 保存独立会话, 应用名称, 结果和确认版本. 一个会话被取消或确认已读时, 不清除其他会话. 终结状态不能被迟到的 pending 回调覆盖. 同步 commit 失败时撤销该会话并清理本地登记.
3. 系统确认 Intent 完整保存在 immutable Activity PendingIntent 中. 安装会话仍持有的广播 token 再引用这个确认 token, 保证应用退出后仍有系统引用. 磁盘仅保存 `cloneFilter().toUri()` 的身份, 重建时通过 `FLAG_NO_CREATE | FLAG_IMMUTABLE` 查找原 token, 不将 Binder / Parcelable payload 序列化到磁盘.
4. 通知以应用名称为标题, 按会话分别发布, 指向私有 `PackageInstallStatusActivity`. 通知权限关闭或发布失败不妨碍持久化和前台恢复. 用户返回应用时可选择继续; 正式 `PackageInstallerActivity` 也会在自身处于 RESUMED 时打开状态页.
5. 状态页在可见并获得窗口焦点后发送确认 token. 发送方 options 使用既有 `ActivityLaunchOptions`: API 36+ `ALLOW_IF_VISIBLE`, API 34 / 35 `ALLOWED`, 更旧版本不传. 系统确认页的 ActivityResult 只表示确认页返回; 最终成功仍依据 PackageInstaller 回调. 两者之间显示等待状态并允许退出等待页.
6. 旋转或进入后台只关闭当前对话框呈现, 不误当成用户已读. 用户查看失败结果或收到前台成功提示后才清理对应记录及通知. 已发出的旧版 Activity 回调仍有接收分支, 新会话不再创建这种回调.
7. force-stop 导致 token 不存在时, 将本地会话标记为失败, abandon 旧 session 并提示重新打开安装包. 本轮未实现跨设备重启的自动重新提交.

PendingIntent 的身份匹配, `FLAG_NO_CREATE`, `FLAG_UPDATE_CURRENT` 和进程退出后的系统 token 语义来自 [官方 PendingIntent 文档](https://developer.android.com/reference/android/app/PendingIntent) (VERIFIED). 系统 PendingIntent 表使用弱引用, 安装会话持有状态接收者的实现分别可见 [AOSP PendingIntentController](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android10-release/services/core/java/com/android/server/am/PendingIntentController.java) 和 [AOSP PackageInstallerSession](https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/pm/PackageInstallerSession.java) (VERIFIED, 用于实现依据, 不将这些分支当成当前模拟器的精确源码快照). 本轮的进程重建能力另有实际设备证据. 用户可见入口的授权边界参照 [官方 BAL 文档](https://developer.android.com/guide/components/activities/secure-bal).

## 设备与测试边界

仅操作本会话启动的两个只读模拟器, 均使用 `-read-only -no-snapshot-load -no-snapshot-save -no-window -no-audio`, 软件渲染, 未操作连接中的真机. 测试结束后关闭两个实例, 不保存快照或修改 AVD 基础数据.

| 实例 | 系统 | 页大小 | 构建指纹 |
| --- | --- | --- | --- |
| `emulator-5568`, `aj6-ps16k-37-x86_64` | API 37, x86_64 | 16384 | `google/sdk_gphone16k_x86_64/emu64xa16k:17/CE2A.260420.019/15611780:userdebug/dev-keys` |
| `emulator-5570`, `AVD_API_24` | API 24, x86 | 4096 | `google/sdk_google_phone_x86/generic_x86:7.0/NYC/6696031:userdebug/dev-keys` |

安装使用实际宿主 / inrt UID, 无 instrumentation 或 shell 身份代发. API 37 的后台用例关闭悬浮窗资格, 未启用无障碍; 有效后台提交时 `sendImportance=400`, `windowFocusAtSend=false`. 测试 APK 是已归档的真实取色器插件 1.1.0 (12). 独立 BAL fixture 仅用于第二会话和签名冲突场景, 不将 adb 预装 fixture 计入宿主安装成功数.

API 37 的 `getconf PAGE_SIZE` 返回 16384, `/proc/self/smaps` 的 `KernelPageSize` 返回 4096; 两种观测都记录在机器结果中, 不将该模拟器等同于 16 KB 真机. API 24 没有 getconf, 页大小取自 smaps. API 24 的无障碍配置最终为已启用, 本轮仅将它用于正式前台安装的旧系统兼容验证, 不作为无特权后台启动对照.

## 实际会话结果

共 11 个真实 PackageInstaller 会话: 8 个成功, 1 个用户取消, 1 个签名冲突, 1 个确认过期回退. 这些是预期不同的结果, 不是 11 次安装成功. [机器记录](android-sdk-37-p25-recovery-20260913-results.json) 包含 session ID, 原始探针结果, 46 份状态 / UI 快照及回调日志摘录.

| Session ID | 环境和场景 | 观测结果 |
| --- | --- | --- |
| `873037193` | API 37, host target 37 v2, 通知拒绝, 后台提交 | pending 已持久化. `kill -9` 后 PID `5486 -> 5804`, 原确认 token 存在. 返回应用继续, 系统确认后回调 0, 实际插件安装成功 |
| `200057667` | API 37, host target 37 v2, 多会话中的第一个 | 提交时另一个宿主 task 可见, importance 100, 仅计辅助观测. 第二会话取消并确认已读后仍可从通知打开本会话, 更新插件成功 |
| `1683033218` | API 37, host target 37 v2, 后台提交第二个 APK | importance 400. 点击对应通知进入正确 APK 的系统确认. 用户取消返回状态 3; 旋转结果页不丢记录. 关闭结果只清除此 session |
| `154924614` | API 37, host target 37 v3, 正式安装页更新不同签名 fixture | 系统回调 5, `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. 退出到桌面不清除结果; PID `7522 -> 7995` 后仍可查看原失败原因 |
| `266263096` | API 37, host target 37 v3, force-stop 后返回 | 查找原 token 返回 false. 点击继续后显示过期及重新安装提示, 本地状态 1, 旧 session 被 abandon. 这是本地回退, 不记作系统安装失败回调 |
| `943295966` | API 37, 正常 host target 36 v3, 通知拒绝 | importance 400 后台提交, 返回应用继续, 回调 0, 插件更新成功 |
| `1972619492` | API 37, 正常 inrt target 29 v3 | importance 400, 通知未授予, 返回应用继续, 回调 0; `installerPackageName=org.autojs.autojs6.inrt` |
| `1166153041` | API 24, 正常 host target 36 v3, 正式安装入口 | 系统 `CONFIRM_PERMISSIONS` 路径实际安装成功. 暴露确认页返回与最终回调之间短暂显示继续提示的问题, 随后修正 |
| `1914894935` | API 37, 最终 host target 37, 通知拒绝 + 进程重建复测 | importance 400, PID `10262 -> 10663`, 原 token 可恢复, 系统确认后回调 0, 记录清空 |
| `129438008` | API 24, 最终正常 host target 36, 正式安装入口复测 | 确认后观察到 `Waiting for the installation status`, 无重复继续提示, 随后回调 0 并清空记录 |
| `1114123554` | API 37, 最终正常 inrt target 29, 正式安装入口复测 | 系统确认后回调 0 并清空记录, 插件 `lastUpdateTime=2026-09-13 16:05:57`, 安装方为 inrt |

普通进程死亡使用 `run-as <package> kill -9 <实际 PID>`, 没有使用 force-stop 冒充. force-stop 场景单独记录. 所有安装成功均有状态 0 和实际包状态支持, 没有把 `Session.commit()` 返回或 UI 启动返回作为成功.

## 构建和自动检查

- 最终 target 37: `assembleAppDebug` 与 `testAppDebugUnitTest --tests ...PackageInstallStatusRecordTest` 通过, 33 秒, 781 个任务. JVM 1/1 验证成功 / 取消 / 签名冲突终结状态不能被迟到或重复回调复活.
- 最终正常配置: `assembleAppDebug`, `assembleInrtDebug`, `assembleAppDebugAndroidTest`, `processAppReleaseMainManifest`, `processInrtReleaseMainManifest` 通过, 22 秒, 898 个任务.
- 最终正常包在 API 37 和 API 24 分别执行 `PackageInstallStatusStoreDeviceTest`, 各 2/2. 验证不同 Store 实例重建, 多会话独立清除, 未知 session, 终结状态防回退, 错误 action / data / session ID 不修改登记.
- 宿主与 inrt release 主 Manifest 都不含 debug probe, receiver 和状态 Activity 均为 `exported=false`; 状态页不使用 `noHistory`, 支持重建. 未构建或宣称验证完整 release / R8 APK.
- 三个最终 APK 的 compile / target 与 16 KB ZIP 对齐已独立检查; Gradle 原生页对齐门禁通过.
- 5 个新提示覆盖 11 份资源, 插入点按 name 排序, 默认与英语内容一致, 新文案无全角标点. 检查发现既有文件含历史分组导致的全局顺序例外, 本轮未重排这些无关条目.
- 6.8.0 的 fix changelog 更新 10 种语言, 日期 `2026/09/13`, 已运行 `.python/generate_markdown.py` 更新 README / CHANGELOG. 生成器造成的无关英语 / 韩语日期格式变化已还原为原样.

最终 APK:

| APK (本地忽略目录 `build/sdk37-p25-recovery/`) | compile / target | SHA-256 |
| --- | --- | --- |
| `target37-reviewed.apk` | 37 / 37 | `17ffb30dca14cb0388591e7175d40f7da010df26ca15a4e30912672e4a4f5de0` |
| `normal-app-reviewed.apk` | 37 / 36 | `9545ac7ef76812e622b73d3338f952c07c5dd9f3233c9f5c41539e8947118a66` |
| `normal-inrt-reviewed.apk` | 37 / 29 | `c584022fc4a6c573a848bc373aa55c0e236fb0e611f1d6caf3f51fd0fbdc888e` |

真实取色器输入 `build/sdk37-p25/actual-picker-source.apk` 的 SHA-256 为 `c55dd575cd331e24bd5a57326314bc4e133bc414501ddf0bd80d821532f55444`. 中间 v2 / v3 APK 和签名冲突 fixture 的哈希也保留在机器记录中, 不将中间包用例和最终包复测混为同一个构建.

## 复跑和保留事项

使用 [run_install_recovery_probe.py](../../../tools/sdk37/run_install_recovery_probe.py) 和 [工具说明](../../../tools/sdk37/README.md#installation-confirmation-recovery). 该工具不强制停止应用或依赖 token fixture, `--background` 会返回 Home 并核对真实 importance. 提交, 确认页, 最终回调和包安装结果分别记录. 多会话测试需要两个不同 APK, 回调身份测试则不提交真实 session.

早期 v2 探针的通用收尾还会查询未安装的 BAL provider, 产生与安装无关的 `fixtureReadError`; 原值保留, 后续安装路由已取消这项查询. 一次 v2 提交 importance 100 也保留, 不计作有效后台例证. 本地 UI 抓取曾遇到控制台 GBK 编码问题, 原始文件已保存, 调整为 UTF-8 后继续; 顺序执行的 adb 快照不是跨进程原子快照, 个别初始 ledger 读数早于同一快照末尾的 UI, 以最终回调和稳定后的状态为准.

尚未覆盖设备重启 / 断电后的 session 恢复, 完整 Launcher 固定快捷方式, 其他通知 / 小组件, a11y 启动例外, 实际取色回传, ASM 有效正负对照和旧系统真机矩阵. 本轮 inrt 运行测试为当前 target 29, 不代替后续 target 37 打包应用验收. 没有脚本公开 API 或插件契约变动, 未触发关联文档 / DTS 生成或插件仓库更新.
