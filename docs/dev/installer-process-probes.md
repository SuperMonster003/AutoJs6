# 安装器正式宿主 UID 调试探针

日期: 2026-10-01. 首个提供本探针的宿主为 AutoJs6 6.8.0 / build 5303. 所有入口只在 `app/src/debug` 中声明和编译, 不进入宿主 Release, 不改变公共 JS API 或 Installer V1 契约.

## 身份与操作边界

`org.autojs.autojs.core.plugin.installer.InstallerProcessProbeActivity` 受 `android.permission.DUMP` 保护, 在独立 `:installer_process_probe` 进程中运行, 使用真实 AutoJs6 UID. 插件发现/选择, 已启用状态, 签名信任, 正式 `HostCallerGuard`, 来源只读 PFD 与 session lease 均经过生产代码. 不替换调用 UID 或使用插件内部测试 Binder 入口. 固定选中的插件 id 必须为 `three-setup-installer`.

`InstallerProcessProbeProvider` 不导出, 在另一个 `:installer_process_source` 进程中提供专用只读 pipe. 来源固定在宿主私有 `files/p6-installer-probe/<caseId>/fixture.apk`, `<caseId>` 只接受 1-64 位小写字母/数字/连字符. 探针只接受 `io.github.supermonster003.autojs6.installer.performance.fixture` v1, 约 100 MiB, 无代码, 无声明权限, 无组件. 桌面驱动在开始前必须核对所有用户均无该包或保留数据; 探针再次核对当前用户和 APK 内容.

`host-death` 模式通过正式 `InstallerPluginHost.openSession` 打开来源. 独立 provider 送出 512 KiB 后保持写端, 插件证明实际私有暂存及源前缀摘要后, 桌面驱动仅终止专用宿主调用进程. Provider 不随调用进程同时退出, 所以 EOF 不会替代实际 Binder 死亡的取消路径. 此模式不提交或安装包, 不终止用户的宿主主进程. 插件主进程死亡的另一个探针及完整证据位于插件仓库, 不能与此模式混称.

`performance` 模式依次执行正式 inspect, getUsers 和 openSession. Root 在 getUsers 之前显式 requestAuthorizer, 其耗时单独记录; 操作者应事先完成插件 Root/Shizuku 授权. `getUsersElapsedMs` 在确认原特权服务进程不存在时才可称服务冷启动, 安装阶段随后使用该服务. `none` 使用真实系统确认, `installElapsedMs` 包含用户等待和系统扫描时间. 阶段分别记录在 `stageElapsedMs`; 进度文件以 500 ms 节流写入, 不在每个 MiB 都同步落盘. 此模式不自行卸载夹具.

## 准备, 恢复与输出

同一 Activity 的 `prepare`/`restore` 模式仅临时修改 Three Installer 的一个正式启用 key. `prepare` 先验证正式信任策略, 将宿主 UID, 目标包签名指纹, 原 key 是否存在及布尔值用 AtomicFile + fd.sync 保存到 `enable-restore.json`, 再调用正式 `PluginEnableStore.setEnabled`. 只有生产启用策略实际允许时才同步 commit 相同的 true 值. 不修改 trust, priority 或其它插件偏好.

`restore` 核对 case/UID/包名/签名后, 只恢复这个 key; 原先不存在则 remove, 原先存在则恢复原布尔值. 清理或安装失败也要独立调用 restore. 恢复不取消正在进行的 session, 不删除来源或卸载应用. 成功 journal 为 `phase=restored`, `pending=false`, `restoredPresent/Enabled` 等于 `originalPresent/Enabled`. 未恢复 journal 阻止另一个 case 的 prepare 覆盖原值. 不使用整个偏好文件回写来恢复一个开关.

每个 case 的输出位于同一私有目录:

| 文件 | 内容 |
| --- | --- |
| `evidence.json` | UID/PID, 原始源摘要, 阶段/耗时, 安装结果或死亡阻塞点 |
| `error.json` | 已拒绝操作或验证失败, 不能作为通过证据 |
| `enable-restore.json` | 单 key 的持久准备/恢复 journal |

上述 AtomicFile 可能在中断时留有 `.bak`/`.new`. `cleanup` 仅关闭本 case 的 handle/provider 和控制 Activity, 验证源摘要后写 `phase=cleaned`. 性能模式只有收到真实成功终态时才能通过该清理屏障; 未确认的操作保留来源和 case 待诊断. 先恢复启用 journal, 核验清理, 再由驱动删除逐项明确列出的自有文件和空目录. 不进行递归删除或全局 session 清理.

控制命令使用 `NEW_TASK|MULTIPLE_TASK` 创建自己的短命 Activity, 不清除其它任务. `die` 仅用于已 READY 的 host-death case, expectedPid 必须等于当前自身 PID, 且正式 session 未结束/独立来源仍保持. 当 OEM 拒绝同 UID 的 run-as 信号时, 驱动先记录 signal-0 的 EACCES, 重新核对 PID/name/UID/startTicks, 才使用这个受 DUMP 保护的自身死亡命令. 系统在新 PID 重建旧 die Intent 时只结束控制页, 不杀新进程, 不写旧错误干扰恢复.

## 使用命令

优先使用插件仓库中的守卫驱动, 不手工拼接安装或进程终止命令. 先安装本宿主的 Debug APK, 并根据模式安装插件 Debug 或签名 Release APK. 下列命令从 `AutoJs6-Plugin-Three-Setup-Installer` 根目录执行, `$serial` 必须替换为明确的目标设备序列号.

```powershell
$serial = 'DEVICE_SERIAL'
$env:JAVA_HOME = 'E:\.java\jdk-21.0.1'
pwsh -File tools/build-performance-install-fixture.ps1 -Sdk 'E:\.android\sdk'
py tools/run-process-death-acceptance.py --self-test
py tools/run-process-death-acceptance.py --serial $serial --mode host --authorizer none --fixture build/performance-install-fixture-100/fixture.apk
```

上面的 host-death 驱动要求插件 Debug 中的观察入口, 会自行 prepare, 使用正式宿主调用, 证明取消/暂存清理并恢复原启用 key. 它不安装夹具. 性能驱动则要求插件为非 Debug 的签名 Release, 会实际安装专用夹具, 通过后限定该夹具执行清理:

```powershell
pwsh -File tools/run-release-performance.ps1 -SelfTest
pwsh -File tools/run-release-performance.ps1 -Serial $serial -Authorizer shizuku
# 可改为 root 或 none. none 的系统确认由操作者完成.
```

若会话中断且 journal 仍 pending, 在不删除该 case 的前提下单独恢复. `<caseId>` 使用原 journal 的值, 不新建替代 case:

```powershell
$caseId = 'ORIGINAL_CASE_ID'
$component = 'org.autojs.autojs6/org.autojs.autojs.core.plugin.installer.InstallerProcessProbeActivity'
& 'E:\.android\sdk\platform-tools\adb.exe' -s $serial shell am start -W -f 0x18000000 -n $component --es caseId $caseId --es mode restore
& 'E:\.android\sdk\platform-tools\adb.exe' -s $serial shell run-as org.autojs.autojs6 cat "files/p6-installer-probe/$caseId/enable-restore.json"
```

本轮 API 35 的正式宿主死亡验收使用实际 UID 10890, 调用 PID 516, 独立来源 PID 2608, 插件 PID 31933. 源送出 524,288 字节, 私有暂存 262,144 字节且摘要匹配; 死亡后为 CANCELLED, 暂存立即删除, 原启用 key 的存在/false 准确恢复. 驱动结果为 `build/process-death-acceptance/20261001-195224-9be594506a0942f99a1bdefa6479abfc/result.json`, `passed=true`, 路径相对插件仓库. 它验证官方宿主 UID 的实际调用进程, 不代替宿主三处用户界面的安装/退化矩阵.
