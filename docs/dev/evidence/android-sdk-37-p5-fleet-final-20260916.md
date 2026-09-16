# P5 最后 9 仓 targetSdk 37 收尾

日期: 2026-09-16. 接续 [batch 3](android-sdk-37-p5-fleet-batch3-20260916.md).

最后 9 仓的 targetSdk 步骤、权限引导、真实设备探针、版本和多语言说明已完成并提交. 最终扫描为 **42 / 42 仓 compileSdk 37、targetSdk 37, 42 个插件工作区干净**. 本文记录最后 9 仓的本地构建与 Android 17 验收; 各仓发布、外部客户端组合、实体机矩阵及 Roadmap 其它 WATCH 项分别保留原有状态.

机器记录: [results.json](android-sdk-37-p5-fleet-final-20260916/results.json), [42 仓扫描](android-sdk-37-p5-fleet-final-20260916/fleet-final-scan.json). 原始工作产物保存在宿主 `build/verification/sdk37-fleet-final-20260916`, 与待清理的 `build/sdk37-*` 分开存放. 当前目录归档测试日志、构建日志、设备状态及必要截图, 不归档 APK、模型、签名密钥或临时系统镜像. 归档文本统一 LF 换行并移除行末空白, 测试内容保持; 原始字节文件仍保留在上述工作产物目录.

## 1. 提交与全量测试

| 仓库 | 最终 HEAD | 版本 / build | 完整套件实际通过 | 条件用例补测 |
| --- | --- | --- | ---: | --- |
| NodeJs-Runtime | `861e084` | 1.5.3 / 174 | 62 | - |
| Bun-Runtime | `4a7d654` | 0.2.4 / 111 | 18 | 16 KB 页面拒绝诊断 1 |
| Python-Runtime | `808e716` | 0.5.4 / 109 | 2 | - |
| MCP-Server | `d4c3796` | 1.0.2 / 60 | 35 | 显式启用的监听进程终止 1 |
| Screen-Color-Picker | `c2625fe` | 1.1.3 / 15 | 10 | - |
| Three-Ember-Player | `5cabf8b` | 3.1.2 / 27 | 35 | - |
| Three-Terra-Player | `cf1ed3d` | 1.6.2 / 37 | 48 | - |
| Three-Stone-AI | `48a1b59` | 1.1.4 / 191 | 3 | 已 staging 真实模型, 无跳过 |
| Accessibility-Compat | `d822ccd` | 1.3.1 / 18 | 12 | 另做保护模式及重绑探针 |

九仓完整套件都从最终 HEAD 构建, **没有传 `notClass` / `notTest` 或其它排除参数**. JUnit 报告合计 `OK (227 tests)`, 其中 225 项直接执行成功, 两项 `AssumptionViolatedException` 在适用条件下另行执行成功, 因而 **227 个独立测试用例均有实际通过记录**. 不把 JUnit 的 OK 总数直接当作无跳过的通过数:

- Bun 的正常运行套件在 API 37 / x86_64 / 4096-byte 页面运行, 19 项中的 `cachedPageSizeRefusalFollowsLocaleInPrewarmInfoAndRun` 只适用于不受支持的 16 KB x86_64 页面. 在 API 37.1 / 16384-byte 页面补测该方法, 10 个语言的缓存错误、prewarm / info / run 及 finished 事件均通过. [完整日志](android-sdk-37-p5-fleet-final-20260916/Bun-Runtime-instrument.txt), [16 KB 补测](android-sdk-37-p5-fleet-final-20260916/Bun-Runtime-page16k-refusal.txt). 该证据验证准确拒绝, 不表示 Bun 的 x86_64 原生运行时已支持 16 KB.
- MCP 的 `killListenerProcess` 是显式 opt-in 的生命周期辅助方法. 在 `-e mcpKillListener true -e delay_msec 5000` 的测试启动后, 启动插件的回环监听服务, 再由测试从插件自己的 UID 终止真实 `:mcp_server` PID, 方法成功. 先启动服务再启动 instrumentation 会被 instrumentation 的进程重启清掉, 不能作为前置条件. [完整日志](android-sdk-37-p5-fleet-final-20260916/MCP-Server-instrument.txt), [补测](android-sdk-37-p5-fleet-final-20260916/MCP-Server-kill-listener-staged.txt).

Bun 与 MCP 的维护者并行工作完成后重新核对最终 HEAD, 未用早期隔离快照替代验收. Bun 最终完整套件含 M7 新增测试, 其 LAN UI 四状态亦在最终 APK 重跑. MCP 最终 build 60 的完整套件及签名构建通过.

九仓均通过 `assembleDebug`, `testDebugUnitTest`, `assembleDebugAndroidTest`, `lintDebug` 与 `appendDigestToReleasedFiles` 依赖的 release 构建 / 原生对齐及各仓载荷校验. 最终签名 APK 的路径与 SHA-256 记录在 results.json. Python 的 release 门禁要求 clean tree / 正确提交计数, 已先提交再重跑完整构建通过; 早期脏树失败不作为最终结果. 最终十语 Markdown `--check` 全通过, 三个 Three 仓 `check_repository.py` 在提交后全通过. [文档与仓库检查](android-sdk-37-p5-fleet-final-20260916/final-repository-checks.json).

宿主最终工作树 `:app:assembleAppDebug` 通过, AutoJs6 6.8.0 / 5280. 此次宿主代码为 Advanced Protection 说明及设置页刷新; 并保留已提交 Bun M7 代码所需、工作区中已有的两个设置字段声明. [构建日志](android-sdk-37-p5-fleet-final-20260916/host-current-build.log). 本次没有改动脚本公开 API.

## 2. 设备与权限边界

全部新设备操作使用本次独占 AVD:

| AVD / serial | 系统 | 实际页面 | 用途 |
| --- | --- | ---: | --- |
| `AVD_API_37.1_16K` / `emulator-5568` | API 37, `CP31.260623.012/16064790` | 16384 | 七仓普通套件、LAN、取色、播放器、Bun 拒绝诊断 |
| `codex-sdk37-fleet-4k-20260916` / `emulator-5564` | API 37, Google APIs x86_64 | 4096 | Bun 真实运行、宿主 UI / Node CLI、OpenCC 完整及分阶段测试 |
| `codex-sdk37-fleet-20260916` / `emulator-5566` | API 37, `CE2A.260420.019/15611780` | 16384 | 真实模型、启用内存限制器的专项环境、保护模式对照 |

早期 `emulator-5600` / `5602` 的只读副本已关闭. `5566` 是本次专门创建的 6 GiB 测试 AVD; 内存专项需要它以 `-writable-system` 启动、root adbd、remount 并重启 framework. 其系统配置与普通出厂镜像明确区分. 其它 UI 复测使用只读副本. 实体设备与另一会话的 `emulator-5612` 未操作. 本轮最终 5566 / 5568 / 5564 均已关闭; 5568 关闭前核对音频 override 已恢复为 0. [最后两个 AVD 的关闭回执](android-sdk-37-p5-fleet-final-20260916/owned-device-cleanup.json).

## 3. Node / Bun / Python / MCP 的本地网络

四仓显式声明 `ACCESS_LOCAL_NETWORK`, target 37 后不依赖 INTERNET 的兼容拆分. 运行时按插件 UID 检查权限, 拒绝时对实际相关网络错误追加本地化引导; Node / Python 保持原错误协议, Bun 保留有界 stderr 和失败事件. 公网及回环不因插件未获 LAN 权限而被提前一概拒绝. 权限按 UID 的边界参考 [Android 本地网络权限](https://developer.android.com/privacy-and-security/local-network-permission).

Node / Bun / Python 提供插件自身设置入口, 用户点击后申请 Nearby devices; 永久拒绝后进入应用设置, onResume 刷新实际状态. 主动打开入口不会自动运行脚本. MCP 在自己的设置中给出说明与申请入口, 开启 LAN 需要显式确认; **系统授予权限本身不会自动打开 LAN 开关或启动监听**. 服务在实际 LAN 绑定前检查插件权限, 拒绝时返回 `local_network_permission_required`; 回环仍可用.

三运行时用真实脚本连接 `10.0.2.2` 的临时 TCP 夹具, 读取固定字节 `K`. 系统对话框拒绝 -> 实际访问失败及插件设置引导; 系统对话框授予 -> 成功; `pm revoke` -> 再次失败及引导; 恢复后 UI 状态正确. MCP 对拒绝 / 授予 / 撤销 / 恢复四状态执行真实服务绑定检查, 回环四次均可用; 另实际验证系统授权后仍须再次手动打开 LAN 开关.

记录: [Node](android-sdk-37-p5-fleet-final-20260916/NodeJs-Runtime-lan-ui.json), [Bun](android-sdk-37-p5-fleet-final-20260916/Bun-Runtime-lan-ui.json), [Python](android-sdk-37-p5-fleet-final-20260916/Python-Runtime-lan-ui.json), [MCP](android-sdk-37-p5-fleet-final-20260916/MCP-Server-lan-ui.json). 每仓另归档 denied / granted / revoked 的原始测试日志. 这些是 AVD 实际权限与本地 socket 验证, 不替代实体 Wi-Fi、跨 profile、Inspector / Claude Code 的完整客户端组合.

Node 的 [宿主 CLI 夹具](../../../tools/sdk37/fixtures/fleet/node-host-cli.js) 在真正的宿主脚本进程中运行, 从插件 `nativeLibraryDir/libnodexe.so` 直接 exec; JS 资产解包到宿主私有 cache, `AUTOJS6_NODE_CLI_ROOT` 指向 `lib/node_modules`, 未在可写目录放可执行二进制. Node 子进程 UID 为宿主 10239, 与插件 10240 不同, `process.execPath` 等于 `/proc/self/exe` 的实际路径. Node `v24.21.0`, npm / npx `11.19.0`, Corepack `0.36.0` 均通过. [回执](android-sdk-37-p5-fleet-final-20260916/NodeJs-Runtime-host-cli.json). 这是宿主 UID 的真实启动链验证, 未操作终端 UI 或下载 npm 包.

## 4. 真实取色与后台播放

Screen-Color-Picker 在系统 MediaProjection 对话框先取消并回到 Inactive, 再明确选择整个屏幕并授权. 对自有夹具 `#123456` 取色, 悬浮窗复制 HEX 为 `#123456`, RGB 为 `R18,G52,B86`; 夹具切到前台实际读取剪贴板核对. 停止后 MediaProjection、前台服务及悬浮界面释放, 主界面回到 Inactive. [结果](android-sdk-37-p5-fleet-final-20260916/Screen-Color-Picker-real-color.json), [HEX 截图](android-sdk-37-p5-fleet-final-20260916/color-copied-hex.png), [RGB 截图](android-sdk-37-p5-fleet-final-20260916/color-copied-rgb.png), [停止后服务](android-sdk-37-p5-fleet-final-20260916/color-stopped-service.txt).

两播放器用生成的 120 秒 MP4 / M4A, 通过只读 content URI 显式授权给播放器. 开启后台音频 throw 模式并核对 `dumpsys audio` 的 **`mHardeningOverride=3`**. 此镜像的有效 shell 命令为 `cmd audio set-hardening throw`; [文档](https://developer.android.com/about/versions/17/changes/bg-audio) 中的 `set-enable-hardening` 在此镜像未改变实际 override. 工具读取设备帮助表选择命令, 并断言 override, 避免把无效命令的零退出状态当作已启用. [设备帮助](android-sdk-37-p5-fleet-final-20260916/head-player-probe-v2/audio-shell-help.txt), [实际开启状态](android-sdk-37-p5-fleet-final-20260916/head-player-probe-v2/audio-throw.txt).

最终 HEAD 实际播放 -> Home 后进度继续 -> 暂停 / 恢复 -> 锁屏继续 -> 锁屏控制 -> 明确退出, 全程核对 media session、真实 foreground service 及音频状态. Ember 使用产品已有的后台音频选项, 关闭测试安装的 PiP 以进入真实后台音频路径. Terra 使用通知中的 **Exit player**, 因为其 MEDIA_STOP 本来保留可恢复 session. 两者退出后 foreground service 都消失. [Ember 回执](android-sdk-37-p5-fleet-final-20260916/head-player-probe-v2/ember-result.json), [Terra 回执](android-sdk-37-p5-fleet-final-20260916/head-player-probe-v2/terra-result.json).

本轮发现并修复 Ember 的真实缺陷: Activity 和 Service 共用通知 ID 6004, 接管后 Activity 调用 Media3 `setPlayer(null)` 会取消已经属于服务的同 ID 通知, 系统撤销 foreground 身份, 但应用缓存仍为 true. JDI 只读运行时取证记录了 `NotificationManager.cancel -> PlayerNotificationManager.setPlayer -> PlayerSystemIntegration.detach -> VideoPlayerActivity.handoffToBackgroundPlayback` 及缓存值. 先尝试 IDE 调试接入, 当前 IDE 项目为宿主且无该插件调试会话, 随后使用独立 JDI 观察器, 未在调试器中修改状态. [接管前故障调用链](android-sdk-37-p5-fleet-final-20260916/ember-jdwp-trace.txt).

修复为先解除 Activity 通知所有权, 再把播放器交给服务; 接管失败恢复 UI, 服务在 Activity 可见时重新确认 foreground. 同时补齐用户停止播放进入 STATE_IDLE 时释放后台服务的行为. 修复后的完整套件及上述真实 throw 模式探针通过. 模拟器为 headless / no-audio, **未声称实际听音**; 播放列表切换、蓝牙和 OEM 音频仍按实体机表验收.

## 5. Three-Stone-AI 真实模型与 Android 内存限制器

源码只选择 `Backend.CPU` / `Backend.GPU`, 没有 NPU 委托, 不添加 `android.hardware.npu`. 本轮实际推理使用 CPU.

通过仓库 `.python/run_real_model_test.py` staging 固定模型 `Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm`, 来源固定 revision `19edb84c69a0212f29a6ef17ba0d6f278b6a1614`. 大小 **1,597,931,520 bytes**, SHA-256 **`faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9`**. 经过应用私有存储校验、生产模型目录集成、健康检查、真实 LiteRT-LM 初始化及 48-token 有界推理, 输出 `Hello tester! How's everything going?`, 8 次 streaming delta. [回执](android-sdk-37-p5-fleet-final-20260916/stone-limited-real/litertlm-real-model-inference.json).

普通 API 37 / 37.1 AVD 均没有启用限制器. 专用 6 GiB AVD 的实际 `services.jar` 表明该镜像读取 `/vendor/etc/memory-limiter-config.xml`, 对应文件缺失. 在该一次性环境安装 [测试 profile](android-sdk-37-p5-fleet-final-20260916/memory-limiter-config.xml), 重新启动 framework 后, `am memory-limiter status` 显示 **enabled, limits=true, monitoring=true, killing=true, ignored=none, watch-failed=0**. 该配置是专项测试环境, 不修改宿主 / 插件生产内存策略, 也不把此状态描述成出厂镜像默认行为. 实现原理参考 [AOSP memory limiter](https://source.android.com/docs/core/perf/memory-limiter); 不把新源码文档中的路径直接套用到旧镜像.

真实推理 PID 6840 / UID 10231 的 `/apps/uid_10231/pid_6840` 起初由系统设置 `memory.high=2147483648`, `memory.swap.max=2147483648`. 实际负载跨越 soft threshold, `memory.events high=2`, 记录峰值 **2,230,661,120 bytes**; 系统自适应把 `memory.high` 提高到 **2,252,341,248**. 推理正常完成, `oom=0`, `oom_kill=0`. [逐次 cgroup 采样](android-sdk-37-p5-fleet-final-20260916/stone-limited-real/cgroup-observations.json), [最终限制器状态](android-sdk-37-p5-fleet-final-20260916/stone-limited-real/final-memory-status.txt).

随后在同一限制器启用环境从最终 HEAD 执行完整 3 项套件, 真实模型参数已传入, 无 assumption skip, 23.506 s 通过. [完整日志](android-sdk-37-p5-fleet-final-20260916/Three-Stone-AI-head-full-instrument.txt). **2 GiB 是本次初始 soft threshold, 并非硬性杀进程上限**. 未触发限制器杀进程, 因而 P2.13 的 `MemoryLimiter:AnonSwap` 退出报告验收继续保留; GPU / NPU 推理也不在本次运行证据内.

## 6. Accessibility-Compat 与宿主说明

插件和宿主均声明 `QUERY_ADVANCED_PROTECTION_MODE`, 在 API 36+ 查询全局状态, 显示 On / Off / Unavailable 及一致的多语言说明, 返回设置页时刷新. 说明明确区分全局模式、实际服务绑定结果与系统无障碍限制, `WRITE_SECURE_SETTINGS` 授权不会绕过系统限制. 两仓均不声明 `isAccessibilityTool`. 参考 [Advanced Protection Mode](https://developer.android.com/privacy-and-security/advanced-protection-mode).

实际保护模式 false / true 分别验证安全设置控制测试、禁用 / 启用自己的服务、保留其它服务配置, 以及 force-stop 后两次重绑. 两组都成功. 当前镜像在 mode=true 时服务仍可绑定, 因此没有把全局模式解释为已经被系统阻止. [模式与重绑记录](android-sdk-37-p5-fleet-final-20260916/Accessibility-Compat-protection-secure-settings.json).

最终插件 APK 另验证既有 SettingsActivity 从后台回到前台会从 Off 刷为 On. 宿主当前 APK 验证插件设置页的 Off / On、onResume 更新及说明对话框. [宿主 UI 记录](android-sdk-37-p5-fleet-final-20260916/host-advanced-protection.json), [宿主实际 On 页面](android-sdk-37-p5-fleet-final-20260916/host-a11y-on.xml). 实际发生非辅助工具限制后的失败分支仍按 P2.14 WATCH 验收, 不伪造该条件.

## 7. OpenCC 复核

保持维护者修复 `5c04062` 不变. 在当前 HEAD 重新构建并执行完整套件, `OpenccStandaloneUiTest#allStandaloneActionsRemainExplicitCancellableAndRestorable` 的 Paste / 窗口转换路径通过, 没有方法级排除. [完整日志](android-sdk-37-p5-fleet-final-20260916/OpenCC-instrument.txt).

普通 runner 的 `OK (9 tests)` 包含 4 类显式条件用例. 已运行仓库 `scripts/ci/run_phased_device_tests.py` 的 5 个阶段: standalone / Binder 入口、restart prepare / verify、文档截图. 另显式运行 official benchmark 的 first-load / steady-state 两阶段, 均无跳过并通过. 合计 **9 个独立用例有通过记录, 12 次成功执行** (正常套件 5 次, 分阶段 7 次). 这比单独反复重跑剪贴板类提供了完整的当前套件记录, 但一次成功不意味着所有设备上的时序问题已永久消失.

## 8. 历史 build 目录清理

已只读检查宿主 `build` 的直接子目录 `sdk37-*`: **29 个目录, 10,268,480,729 bytes / 9.563 GiB**, 均为未跟踪产物, 未发现 reparse point 或其后代. [逐目录清单](android-sdk-37-p5-fleet-final-20260916/cleanup-inventory.json). 本轮模型及证据位于 `build/verification`, 不在删除候选中.

自动批准审核两次拒绝实际删除, 包括经过边界验证的 `Remove-Item -LiteralPath ... -Recurse -Force`, 未返回具体拒绝理由. **实际删除 0 个目录**, 未改用其它删除方法绕过拒绝.

提供 [remove_historical_builds.ps1](../../../tools/sdk37/remove_historical_builds.ps1): 默认只读清单; 明确 `-Apply` 时, 在同一 PowerShell 内验证根路径、直接子目录前缀、reparse point 和 Git 跟踪状态, 所有候选通过后才逐个删除. 本轮只运行过它的默认只读模式. 用户确认相关历史构建已停止后可自行运行该具体脚本; 已授权的删除在代理侧仍受上述自动审核阻止.

## 9. 复跑入口

工具说明见 [tools/sdk37/README.md](../../../tools/sdk37/README.md), 包含合成取色 / 播放夹具、后台播放探针、宿主 UID 的 Node CLI 夹具和历史清理脚本. LAN、真实模型与安全设置测试已随各插件提交; 在指定独占 AVD 中执行, 不遍历或重置其它已连接设备.

归档的完整 suite 日志必须同时检查 `OK (...)`, failure 和 `INSTRUMENTATION_STATUS_CODE` 的 -1 / -2 / -3 / -4, 并按方法名关联条件补测. 单独的 shell 零退出码、JUnit OK 计数、限制器 disabled 或 `assumeTrue` 都不能作为目标功能已经验证的替代证据.
