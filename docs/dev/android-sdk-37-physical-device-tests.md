# Android SDK 37 实体机测试清单

日期: 2026-09-13. 源于 [SDK 37 Roadmap](android-sdk-37-roadmap.md) 的 P2、P3、P5、P6, 覆盖宿主、inrt 用户 APK 与全部 42 个官方插件. 共 99 个验收行: 宿主 / 系统 51 行, inrt 6 行, 插件 42 行. 本文汇总待做的实体机工作, 不把模拟器通过项改记为真机通过. 每行有独立 ID, 可导出 CSV 填写状态与证据.

## 1. 设备与执行顺序

本轮只读清点确认以下 5 台设备在线, 均为 4 KB 内核页. 没有在线 API 37 真机. 原始清点位于 `build/sdk37-physical-20260913/inventory`; `localhost:50781` 离线, 未访问. Xiaomi 的 `am get-config` 当前含 `sw720dp`.

| 标记 | 当前设备 / adb serial | 用途 |
| --- | --- | --- |
| D28 | Sony G8441 / `BH900ASK9E`, API 28 | 旧系统 API 链接、权限、无障碍、截图、定时任务回归 |
| D31 | Sony XQ-AT72 / `QV710AF65F`, API 31 | Android 12 的 FGS、闹钟、组件与启动行为 |
| D33S | Sony XQ-DQ72 / `QV770340J7`, API 33 | 通知授权、inrt 29 -> 37、插件通用验收 |
| D33R | Redmi 22120RN86C / `bek749scrwv4wo8h`, API 33 | 第二 OEM 的后台保活、权限页、安装、存储与性能对照 |
| D35 | Xiaomi 23046RP50C / `968e9f18`, API 35, sw720dp | 平板 / 输入法 / 分屏, 已知单应用截图问题, FGS 与后台音频旧系统回归 |
| D37 | 待接入 Android 17 / API 37 真机 | 新权限、新限制和真实网络的必要补证; 旧系统上安装 target 37 APK 不能代替 |
| D16K | 待接入真实 16 KB 内核设备, 最好同时为 API 37 | ARM 原生库与实际页大小回归; 不能仅凭模拟器 `getconf` 或 APK 对齐通过认定 |

执行顺序:

1. **现有设备先做**: B、A01-A03、N01/N09、T、M、S、UI 与各自适用的 L; 每台至少完成无障碍 + VS Code 双向连接 + 截图 + 定时任务. D35 的单应用截图缺口优先单独复现.
2. **D33S 或 D33R + D35**: 完成用户 APK 安装 / 升级矩阵与全部插件的 4 KB 验收. Bun 的 minSdk 为 33, Kotlin / 部分 Paddle 为 26, 以实际 APK 为准.
3. **获得 D37 后**: 在同一设备 / 网络 / 设置下对比正常 target 36 与 target 37, 完成本地网络权限、音频、ASM、资料隔离、大屏输入等新规则. 已完成的 AVD 记录作为对照材料.
4. **最终包复跑**: P3 正式 target 37 包、最终 Runtime Kit 生成的用户 APK 和 P5 各插件最终提交重新验收. 调试探针通过不等于发布包的真实入口通过.

所有用例初始状态为 NOT_RUN. PASS 要有实际结果; FAIL 是与已确认预期不符; 缺少系统版本、外设、待适配代码或签名 APK 时记 BLOCKED; N/A 需说明具体不适用条件. 系统按设计阻止某项操作, 可以判为“限制负例通过”, 不能写成“该业务功能可用”. 未确认的 Android 17 变化只设观察目标, 不预设错误码或系统行为.

D37 / D16K 行用于补齐实体机证据. 缺设备时保留未覆盖项, 不把本次清单整理解释为新增硬件发布前置条件; 发布门禁以主 Roadmap 的原定 AVD / 旧系统矩阵为准.

O02 的受控 TLS 对照已提供 [自动化方法](../../tools/sdk37/README.md#tls-trust-ct-ech-and-request-scoped-insecure-options) 与 [P2.6 证据](evidence/android-sdk-37-p26-20260913.md). API 37 / API 24 的模拟器结果不关闭 O02. 真机仍需真实公网 / 企业代理端点与实际协商证据, 不在日用手机安装测试 CA; 现有 TLS helper 限模拟器, 不能直接把 serial 改成手机后使用.

## 2. 包与证据准备

同源对照至少准备宿主正常 target 36 / debug target 37、inrt 正常 target 29 / debug target 37. 用户生成 APK 和插件另存实际 applicationId、签名、versionCode、compile/target/min、SHA-256、源码提交和 ABI. 同机覆盖安装前后必须保留原文件与授权状态; 首次安装和升级分开记录. 测试安装 / 卸载仅针对专用测试 applicationId, 不为制造“干净状态”卸载日用包.

```powershell
$Sdk37Adb = 'E:/.android/sdk/platform-tools/adb.exe'
$Sdk37Serial = 'QV770340J7' # 替换为本轮唯一待测设备.
$Sdk37Package = 'org.autojs.autojs6'
$env:JAVA_HOME = 'E:/.java/jdk-21.0.1'

# 每组单独构建并归档 APK 和 output-metadata.json, 以免后一次构建覆盖前一组.
./gradlew :app:assembleAppDebug :app:assembleAppDebugAndroidTest
./gradlew :app:assembleInrtDebug :app:assembleInrtDebugAndroidTest
./gradlew :app:assembleAppDebug :app:assembleAppDebugAndroidTest '-Pautojs.sdk.target.override=37'
./gradlew :app:assembleInrtDebug :app:assembleInrtDebugAndroidTest '-Pautojs.sdk.target.override=37'

& 'E:/.android/sdk/build-tools/37.0.0/aapt2.exe' dump badging '<实际 APK 路径>'
Get-FileHash -Algorithm SHA256 '<实际 APK 路径>'
& $Sdk37Adb -s $Sdk37Serial install -r '<已确认签名一致的测试 APK>'
& $Sdk37Adb -s $Sdk37Serial install -r -t '<匹配该 target / variant 的 androidTest APK>'
```

目标覆盖参数仅用于 debug; release / Runtime Kit 使用正式配置. `assembleInrt*` 会使同次请求中的宿主关闭 ABI splits, 实际文件名以 `output-metadata.json` 为准. API 35 截图问题需要额外保留 compile 36 / target 36 的历史基线 APK, 不用“同为 compile 37 的两个 target”替代 compile 对照.

证据目录建议为 `build/sdk37-physical/<serial>/<apk-label>/<case-id>-<scenario>/`. 保存执行步骤、开始 / 结束时间、权限前后值、功能日志、返回值或文件、必要的截图 / 外部录像. 安装恢复过程中不运行 instrumentation / force-stop, 以免改变被测生命周期. 每组结束恢复该组改过的开关和测试任务.

## 3. 自动化入口

### 3.1 清点、快照和可填写表格

```powershell
py tools/sdk37/physical_device.py inventory --adb $Sdk37Adb --output build/sdk37-physical/inventory
py tools/sdk37/physical_device.py checklist --output build/sdk37-physical/checklist
py tools/sdk37/physical_device.py snapshot --adb $Sdk37Adb --serial $Sdk37Serial --package $Sdk37Package --output build/sdk37-physical/before
# 完成一组人工操作后:
py tools/sdk37/physical_device.py snapshot --adb $Sdk37Adb --serial $Sdk37Serial --package $Sdk37Package --include-logs --output build/sdk37-physical/after
```

这些子命令不改手机状态. `checklist` 从本文所有测试行生成 `cases.csv`, 增加实际设备 / APK、状态、观察、证据、BLOCKED / N/A 原因等列; 不自动勾选通过. `snapshot` 保存 package、app-ops、服务、compat、audio、屏幕信息, 不支持的命令单独记录退出码. 日志是有限的历史尾部, 不能单独作为本次用例成功证据. `getconf` 与内核 smaps 页大小分别保存.

### 3.2 选定的设备 instrumentation

```powershell
py tools/sdk37/physical_device.py instrument --adb $Sdk37Adb --serial $Sdk37Serial --package $Sdk37Package --expected-target 37 --suite dex --output build/sdk37-physical/dex
py tools/sdk37/physical_device.py instrument --adb $Sdk37Adb --serial $Sdk37Serial --package $Sdk37Package --expected-target 37 --suite native --output build/sdk37-physical/native
py tools/sdk37/physical_device.py instrument --adb $Sdk37Adb --serial $Sdk37Serial --package $Sdk37Package --expected-target 37 --suite install-state --output build/sdk37-physical/install-state
# API <= 36 + target 37, 相关 compat 无覆盖:
py tools/sdk37/physical_device.py instrument --adb $Sdk37Adb --serial $Sdk37Serial --package $Sdk37Package --expected-target 37 --suite looper-platform --clear-interrupt false --pool-starts 1 --output build/sdk37-physical/looper
# API 37 + target 36 默认状态: --expected-target 36 --clear-interrupt false --pool-starts 1.
# API 37 + target 37 默认状态: --expected-target 37 --clear-interrupt true --pool-starts 0.
```

`--expected-target` 必须写实际 APK 的 target, 上面的 37 不是设备 API. 脚本核对 debuggable 和匹配的 runner, 逐方法核对完成状态, 拒绝跳过、缺失、重复结果和失败; `OK (N tests)` 本身不够. dex 为 1 项, native 为 3 项且需已安装 / 启用兼容的 OpenCV 与 Image Quantization, install-state 为 2 项, looper-platform 为 2 项. instrumentation 会重启被测应用并中断其脚本; 这些套件不自动重绑无障碍, 完成后按需从 UI 重新启用. install-state 只验证状态存储与回调身份, 不能代替实际安装会话.

`run_looper_tests.py` 的完整 14 项会自动修改无障碍服务启用列表, 仍限定模拟器. 真机的完整脚本语义用下述实际脚本回归; 不通过移除串号校验来复用它.

### 3.3 真实 IPv4 LAN 的 11 项网络测试

PC 与手机处于可互通的测试网络. 先关闭旧 VS Code 测试连接, 空出手机 17643 / 7347. PC 固定响应服务使用 TCP 17637 / 17639 / 17640 和 UDP 17638; PC 还会主动连手机的 TCP 17643 / 7347. 路由器 AP 隔离、防火墙或 VPN 路由不通应先解决并记录, 不把它们当作权限拦截.

```powershell
# PC 终端 1: 换成实际地址; 只接受指定手机的来源 IPv4. Ctrl+C 结束.
py tools/sdk37/local_network_fixture.py --bind 192.168.1.10 --device 192.168.1.20

# PC 终端 2: 在手机 UI 确认授权状态后运行. 每次使用新目录.
py tools/sdk37/run_local_network_tests.py --adb $Sdk37Adb --serial $Sdk37Serial --host 192.168.1.10 --expect granted --output build/sdk37-physical/lan-granted
# 仅 D37 + target 37: 从手机设置撤销本地网络权限, 再执行:
py tools/sdk37/run_local_network_tests.py --adb $Sdk37Adb --serial $Sdk37Serial --host 192.168.1.10 --expect denied --output build/sdk37-physical/lan-denied
# 重新从 UI 授权, 再执行 granted 并保存另一份结果.
```

授权态要求 11/11; 拒绝态要求 9 项完成 + 2 个明确跳过的 VS Code 成功握手用例. 先证明授权可达, 再验证拒绝 / 撤销失败, 最后验证恢复; 保持网络和防火墙不变. 单次 TCP 超时或 IOException 不能独立证明 LNP. 该 fixture 的 MQTT 只做 CONNECT, WebSocket 只做 upgrade, VS Code 只做 hello; 完整业务仍在 N07 / N09 单测.

真实 LAN 不用 `adb forward`、`adb reverse` 或 `emu redir`; USB 调试本身可以保留. 本轮增加的完整 transport fixture 仅支持 IPv4, IPv6 LAN、mDNS / multicast、跨 profile 和完整 broker 需要 N04-N07 的独立步骤. `retry / denial / nearby` 自动点击系统权限页的 suites 仍限定模拟器, 真机权限页人工操作. 低于 API 37 或 target 低于 37 的包使用 granted 基线, 不执行新权限 grant / revoke.

### 3.4 BAL / ASM 与安装恢复

`run_bal_probe.py` 和 `run_install_recovery_probe.py` 增加 `--physical-device`, 允许显式选定测试手机. 前者会 force-stop 探针与 fixture, 后者保留进程 / pending session. 二者均不改 compat 或权限.

```powershell
# D37, 安装 target 37 debug 宿主和带应用级保护声明的 fixture 后:
py tools/sdk37/run_bal_probe.py --physical-device --adb $Sdk37Adb --serial $Sdk37Serial --route asm --allow-below false --expect no-landing --output build/sdk37-physical/asm-default

# 把专用测试 APK 置于应用 files/sdk37-bal-install.apk 后, 通过正式安装路径提交:
py tools/sdk37/run_install_recovery_probe.py --physical-device --adb $Sdk37Adb --serial $Sdk37Serial --route install --background --output build/sdk37-physical/install-background
py tools/sdk37/run_install_recovery_probe.py --physical-device --adb $Sdk37Adb --serial $Sdk37Serial --route install-recovery --output build/sdk37-physical/install-return
```

ASM 的 fixture 构建、默认 / enable / disable 对照与顶部 Activity 例外见 [12 组验证方法](evidence/android-sdk-37-p25-asm-20260913.md). 需分别核对调用方和顶部应用的 target / compat / manifest, 不能仅将 Activity setter 设为 false 就认定开启了应用保护. 安装 APK 的放置、系统确认与会话恢复见 [工具说明](../../tools/sdk37/README.md#installation-confirmation-recovery); session ID 只证明已提交.

### 3.5 可直接复用的脚本

| 脚本 | 方法与预期 | 自动化边界 |
| --- | --- | --- |
| [smoke-legacy-api.js](../../sample/accessibility/smoke-legacy-api.js) | 在解锁、无障碍已启用且守护模式关闭时从宿主执行; 与既有 53/53 基线核对, 保存完整输出 | 会停止 / 重启服务; 需要已配置的恢复方式或人工重新启用 |
| [smoke-ui.js](../../tools/sdk37/smoke-ui.js) | 从宿主运行, 授予悬浮窗, 人工确认屏幕捕获; 预期 Activity、可编辑控件、overlay、非空截图各有 PASS | 人工选择全屏 / 单应用分别运行, 不能仅凭截图尺寸证明内容正确 |
| [smoke-dynamic-loading.js](../../tools/sdk37/smoke-dynamic-loading.js) | 同目录放 fixture DEX / JAR, 两轮加载各返回固定字符串, 共 4 个加载 PASS | 夹具构建见 tools README; 旧 API 的 Java 6 JAR 编译器边界按已有证据保留 |
| [ocr_provider_smoke.py](../../tools/16kb/ocr_provider_smoke.py) | 例如 `py tools/16kb/ocr_provider_smoke.py SERIAL --adb ADB_PATH --jobs paddle:v4 rapid:v6 --image generated --out NEW_DIR`; 识别测试图并验证结果 | 会 force-stop 宿主并启动 UI 脚本; 默认不用 `--enable-packages` 改插件启用状态; 仍需图中文字实际命中 |
| [run_audio_probe.py](../../tools/sdk37/run_audio_probe.py) | 原生音频诊断, 参数和已知失效的 throw 状态见 P2.2 证据 | 仍限定模拟器, 不代表 Rhino / TTS / 真机可听性; 真机按 AU / T 行操作 |

## 4. 宿主与系统功能清单

表内“全部”指 D28 / D31 / D33S / D33R / D35, 并在 D37 到位后复跑. “专用”表示测试机上执行会影响后台状态 / 重启 / 系统保护的场景. 每行可含多个子场景, 证据中分开记录, 全部满足才勾选该行. AVD 已通过的细项不免除这里要求的 OEM / 真机复跑.

### 4.1 基线与旧系统回归

| ID | 测试项 | 设备 | 测试方法 / 自动化 | 预期结果 | Roadmap |
| --- | --- | --- | --- | --- | --- |
| [ ] B01 | 同源包与安装身份 | 全部 + D37 | 按第 2 节核对正常 / target 37 APK 与 test APK; 保存 SHA、签名、target、版本和设备快照; 分开记录首次安装、同签名覆盖安装 | 包身份准确, 现有数据保留; 错签名应明确失败, 不卸载日用包来绕过 | P0.2 / P3.1 |
| [ ] B02 | 启动、设置与权限入口 | 全部 | 正常包 / target 37 包分别打开首页、抽屉、设置、脚本列表; 退出重启; 开关通知、悬浮窗、无障碍、所有文件访问 | 无 NoSuchMethodError / VerifyError / 新 API 链接崩溃; 旧系统无 ACCESS_LOCAL_NETWORK 系统弹窗; 不支持的能力有明确状态 | P2 / P3.2 |
| [ ] B03 | 每台必跑组合 | 全部 | 按 A01、N09、M01、T01 顺序运行; 使用同一脚本、网络、授权和调度后端比较两种 target | 无障碍、VS Code、截图和定时任务均有独立结果, 不能只挑一台替代其他 OEM / API | P2 验收 / P3.2 |
| [ ] B04 | 发布候选与长时间使用 | D33S 或 D33R + D35 + D37 | 最终正式包重复 B03; 编辑 / 运行 / 停止脚本循环 20 次, 前后台切换并静置 30 分钟; 保存 ANR / 崩溃与内存趋势 | 无新增崩溃、ANR、残留任务或持续增长; 耗时 / 内存对照注明设备和电量条件 | P3.2 / P6 |

### 4.2 无障碍、脚本取消与特权入口

| ID | 测试项 | 设备 | 测试方法 / 自动化 | 预期结果 | Roadmap |
| --- | --- | --- | --- | --- | --- |
| [ ] A01 | 完整 a11y 冒烟 | 全部 | 执行 smoke-legacy-api.js, 含选择器、事件、服务重建后再次查找; 保存结果 | 与当前 53/53 基线一致; 服务重绑后的新实例可用; 明确跳过的项不计通过 | P2.4 / P3.2 |
| [ ] A02 | 中日韩越输入法事件 | D35 + D37, 其他机抽查 | 在自有输入框分别用中文拼音组合、日文、韩文、越南语组合 / 提交 / 删除; 记录 TYPE_VIEW_TEXT_CHANGED 和 API 37 的 textChangeTypes 可用信息 | 已有文本监听器能处理组合及提交; 不因新字段丢事件或崩溃; 事件序列以实际 IME 为证据 | P2.15 |
| [ ] A03 | 手势、全局动作与服务截图 | 全部 | 在自有测试页做 click、长按、滑动、多点手势, Home / Back / Recents; 分别测试 MediaProjection 与 AccessibilityService 截图可用路径 | 操作作用于正确控件, 回调对应成功 / 取消; 老 API 缺少截图接口可识别, 不用另一种截图成功替代 | P2.15 / P3.2 |
| [ ] A04 | Advanced Protection | 有该设置的专用 D37, API 36 可辅助 | 记录保护状态, 手动开启后检查服务是否停用 / 不能启用及宿主提示, 再关闭并恢复服务 | 记录真实系统行为; 若受保护限制, 应有可理解提示并不误称已启用; 此项当前仍待实测 / 适配, 不预设系统必定撤销 | P2.14 |
| [ ] A05 | 取消、OEM 服务恢复、Shizuku / root | 全部, 特权分支按实际设备 | interval / sleep / 阻塞选择器 / 线程池脚本各启动后停止, 取消远期回调; 用 looper-platform 辅助; 对已配置的 Shizuku / root / secure-settings 启停路径分别测试可用、授权取消、服务断开 | 脚本停止后不再产生副作用, 正常退出无历史回调挂起; 特权不可用明确失败, 不默默改用更高权限; 无障碍恢复结果真实 | P2.4 / P3.2 |
| [ ] A06 | 悬浮窗、控制台与布局分析 | 全部, D35 / D37 补多窗口 | 从实际入口显示悬浮按钮、可编辑控制台和布局分析; 拖动、点击、透传、输入、关闭后重开; 旋转 / 分屏 / 锁屏恢复, 撤销再授予悬浮窗权限 | 窗口坐标和触摸命中正确, 控制台可输入, 分析树对应当前页面; 权限失效有提示, 关闭后无残留窗口; SAW 已授权、overlay 实际可见和 FGS 启动资格分别记录 | P2.5 / P2.12 / P2.15 / P3.2 |

### 4.3 本地网络、资料隔离与 TLS

VERIFIED: Android 17 + target 37 的广泛 LAN 访问使用 ACCESS_LOCAL_NETWORK, 含入站 TCP 与 UDP; 旧 target 的隐式授权和已有 Nearby Devices 授权要单独比较. [官方 LNP 说明](https://developer.android.com/privacy-and-security/local-network-permission). 本表把网络不通与权限拒绝分开判定.

| ID | 测试项 | 设备 | 测试方法 / 自动化 | 预期结果 | Roadmap |
| --- | --- | --- | --- | --- | --- |
| [ ] N01 | 实际 IPv4 网络传输 | 全部 + D37 | 第 3.3 节 11 项 suite, PC 使用真实 LAN 地址; 同时保存 fixture 日志 | 授权基线 11/11, 含入站 accept、TCP / UDP、HTTP、WS、回环、握手与宿主 Node bridge fetch; 没有绕经 adb 隧道 | P2.1 / P3.2 |
| [ ] N02 | 本地网络权限完整 UI | D37 | 首次安装拒绝、再次请求、反复拒绝后设置入口、设置撤销、重新授权; 从抽屉 / 设置 / VS Code 主动连接分别触发; 另测已有蓝牙组授权与 target 36 -> 37 覆盖安装 | 懒申请可恢复, 后台脚本不擅自弹窗; 拒绝不假报连接成功; 升级与权限组状态按实际记录, 不强行清空后当作升级结果 | P2.1 |
| [ ] N03 | Wi-Fi / Ethernet / VPN / 热点 / Wi-Fi Direct | 有对应接口的 D37; D35 辅助 | 每种拓扑先保存两端地址、路由和实际接口, 跑 N01 授权 / 拒绝 / 恢复; Wi-Fi Direct 先建立真实 P2P 链接; VPN 分全隧道和 LAN 绕行 | 授权后在可达路径双向工作, 拒绝时符合该实际 LAN 路径的系统限制; AP 隔离或路由失败记环境问题; 缺外设记 BLOCKED | P2.1 |
| [ ] N04 | 真正 IPv6 LAN | IPv6 路由可用的 D37 | 使用 PC / 手机的 ULA 或链路本地地址, 标注 scope/interface; 原始 Socket / UDP 双向收发唯一标记, HTTP / WS 用带方括号的 IPv6 URL另测; 重复权限三态 | 成功有对端标记, 拒绝有系统 / 异常证据并能恢复; ::1 仅是回环, 不能代替 IPv6 LAN; 当前 IPv4 fixture 不覆盖 | P2.1 |
| [ ] N05 | mDNS / multicast / broadcast | 同网段 D37 + 发现端 | PC 发布专用 _http._tcp 测试服务与 .local 名称; 脚本 / NSD 查询; UDP 组播和广播发唯一标记; 对照拒绝与授权, 记录 multicast lock、Wi-Fi 和系统 picker 路径 | 广泛扫描 / 通信符合权限状态; picker 的单设备授权单独记录, 不推定获得任意 socket 权限; 不能用普通 DNS 成功代替 mDNS | P2.1 |
| [ ] N06 | 跨 UID / 跨 profile 回环 | 有工作资料的专用 D37 | 个人 / 工作资料分别启动独立 UID 的测试服务与客户端; 同资料和跨资料访问 localhost 分开测; 宿主和 Node / Bun / Python / MCP 插件分别授权 / 撤销 | 宿主授权不自动覆盖插件 UID; 同资料回环与 LAN 权限分开; 跨 profile 记录 Android 17 限制, 不把 NAT 或同 UID 结果代替 | P2.1 / P5.3 / P5.4 |
| [ ] N07 | 完整 MQTT 服务 | D33 或 D35 + D37 | 使用真实 broker, 经 Android MqttService CONNECT、订阅、发布带 case ID 的消息; 断网 / 杀连接后重连、前后台切换、拒绝再恢复 | 双端确认消息内容 / topic, 重连后无重复订阅泄漏; 权限拒绝与 broker 凭据错误区分; CONNECT fixture 成功不够 | P2.1 |
| [ ] N08 | 公网、DNS 与同资料回环 | 全部 + D37 | 访问固定可用 HTTPS 公网端点, 本地 DNS 解析, IPv4 / IPv6 loopback; D37 的 LAN 授权 / 拒绝两态重复, 移动数据另测 | 不因拒绝 LAN 破坏公网 / 普通 DNS / 同资料回环; 移动数据与 Wi-Fi 记录实际路由; 站点或证书故障单列 | P2.1 / P2.11 |
| [ ] N09 | 真正 VS Code 双模式与 MCP | 全部 + D37 | 电脑连手机服务端、手机连电脑服务端各执行带唯一 console 标记的脚本; 停止、重连、发送文件; MCP 插件用只读工具请求并断线恢复 | 除握手外确实执行 / 返回正确标记; 断线状态及时更新; D37 拒绝后按宿主 / 插件 UID 给出正确授权入口 | P2.1 / P3.2 / P5.4 |
| [ ] IPC01 | Parcel / Bundle 与 Tasker 参数 | 有 Tasker 的 D33 或 D35 + D37 | 用自有 Tasker 测试任务调用 AutoJs6, 传含中文 / 数组的固定参数并核对脚本收到值; 插件元数据、快捷方式 extras 与各 PL 行的 Binder 返回分别读取; D37 默认状态和专用 debug 的 PARCEL_HARDENING 对照分开保存 | 参数 / 返回值完整且类型一致, 没有 BadParcelableException 或读写错位; metadata 不只检查存在, 要核对协议版本与能力值; 缺 Tasker 则该子场景 BLOCKED | P2.7 / P5 |

### 4.4 音频与定时 / 开机

VERIFIED: Android 17 上后台音频有生命周期限制, target 37 后后台 FGS 还需 WIU 能力; 合格的精确闹钟 + USAGE_ALARM 有特定例外. 播放或音量调用可能静默失败. 以下既验收用户可用流程, 也保留应被阻止的负例. [官方后台音频说明](https://developer.android.com/about/versions/17/changes/bg-audio).

| ID | 测试项 | 设备 | 测试方法 / 自动化 | 预期结果 | Roadmap |
| --- | --- | --- | --- | --- | --- |
| [ ] AU01 | 实际 Rhino 音乐 / TTS 可听性 | 全部, 重点 D35 / D37 | 用项目现有 media.playMusic / tts 文档样例和固定本地音频, 前台开始后切后台、锁屏, 播放至少 2 分钟; 两个可用 TTS 引擎分别记录 | 合法前台 / 用户发起 FGS 路径实际可听, 无异常停顿; 有人工听音结果与引擎包名 / UID, start/done 回调本身不算可听 | P2.2 |
| [ ] AU02 | 无资格后台音频负例 | D37 | 分别在无 FGS、非 WIU FGS、合格用户发起 FGS 中尝试播放 / 焦点 / 音量; 保存实际服务能力与音量前后值, target 36 / 37 对照 | 不合格路径按系统规则被限制, 焦点 / 音量 / 播放分开判定; 未抛异常不算成功; 合法恢复后可听, 后台 TTS引擎另看实际行为 | P2.2 |
| [ ] AU03 | 用户恢复入口与 FGS 生命周期 | D35 + D37 | 锁屏后通过可见 Activity、通知、小组件或媒体键重新发起播放; 停止后检查服务; 对比从后台直接重建服务 | 用户入口真实恢复播放, FGS 与操作关系可追溯; 停止后释放资源; 不以 ALLOW_ALWAYS 或仅有 SAW 权限替代启动资格 | P2.2 / P2.5 |
| [ ] AU04 | 闹钟音频例外与音量 | 有精确闹钟授权的 D37 | 分开运行确实使用 USAGE_ALARM 的测试路径与普通媒体流; 授权 / 拒绝精确闹钟并记录实际生效权限、stream、WIU、音量前后值 | 仅满足条件的闹钟路径适用例外; 普通 media.playMusic 不因被定时调用就成为 alarm 流; 恢复测试前音量 | P2.2 / P3.3 |
| [ ] AU05 | 音频路由与播放器插件 | 有扬声器 / 耳机 / 蓝牙的 D35 + D37 | Three-Ember / Terra 分别前台播放、后台切歌、锁屏、媒体键、耳机插拔与蓝牙断连; 若有 BLE GATT 同时连接则另记 | 路由与暂停 / 恢复合理, 无无声却显示播放或后台突然补播; 两插件分别有证据, BLE 新规则未确认时记录观察而非预设 | P2.2 / P5.4 |
| [ ] AU06 | TTS 文件合成与错误恢复 | D35 + D37 | 可见与后台分别 synthesizeToFile, 校验输出文件可解码; 切离线声音、不可用声音 / 引擎、取消与超时; 另测实际 speak | 文件合成与播放分别判定; 后台播放资格不足不应错误禁止不播放的合成; 错误和取消有终结结果 | P2.2 |
| [ ] T01 | 三种定时后端 | 全部 + D37 | 设置中分别选择 Alarm、WorkManager、JobScheduler; 新建一次性 / 每日 / 每周任务, 修改 / 取消; 前台与锁屏分别保存计划和实际触发时间 | 在各后端自身调度规则内执行一次正确脚本, 取消后不执行; 延迟需同配置 target 36 基线对照, 不把非精确任务当精确闹钟 | P2.2 / P3.2 |
| [ ] T02 | 真重启后的宿主 / inrt 链路 | 专用 D35 + D37, D33 补 inrt | 启用已实现的开机任务, 人工重启设备并解锁, 不用 am broadcast 冒充 BOOT_COMPLETED; 记录 receiver、调度、脚本、FGS、音频各阶段 | 无 ANR、非法类型 FGS 或重复执行; D37 非 WIU 的开机音频限制明确记录, 不能把脚本启动当音频可用 | P2.2 / P3.3 |
| [ ] T03 | 电池 / 后台策略与长待机 | D33R + D35 + D37 | 在系统默认限制与用户明确允许后台的设置各测一次, 锁屏待机至少 30 分钟; 记录是否被 OEM 停止, 使用下一次实际任务触发恢复 | 区分 OEM 策略和 SDK 回归; 任务状态不假报完成, 恢复后不重复补跑; 恢复原电池设置 | P2.2 / P3.2 |

### 4.5 Activity、通知、安装与屏幕捕获

| ID | 测试项 | 设备 | 测试方法 / 自动化 | 预期结果 | Roadmap |
| --- | --- | --- | --- | --- | --- |
| [ ] L01 | Launcher 固定快捷方式 | 全部 + D37 | 从宿主固定脚本快捷方式, 确认系统添加, 回桌面点击; 冷启动 / 进程退出后再点; 删除与重新固定 | 图标、参数、脚本正确, 添加成功广播不代替实际点击执行; OEM Launcher 各记录 | P2.5 / P3.2 |
| [ ] L02 | 小组件与所有通知入口 | 全部 + D37 | 添加 / 更新 / 调整 ScriptWidget 并点击; 分别点击常驻服务、脚本、崩溃、安装通知及停止动作; 通知拒绝 / 恢复另测; 自定义通知视图仅在公开 API 支持时测尺寸 | 正确 Activity / session / script 被打开, 停止动作不会启动无关 Activity; 无 custom view 内存异常, 不支持自定义视图以源码说明 N/A | P2.5 / P2.9 |
| [ ] L03 | 后台安装确认恢复 | D33S 或 D33R + D35 + D37 | 第 3.4 节真实安装; 覆盖通知允许 / 拒绝、返回前台、普通进程死亡、两个 session、取消、签名冲突、结果页旋转; 专用包另测 force-stop 后过期回退 | 每个 session 有系统最终成功 / 失败结果; 不串会话; 普通进程死亡可恢复, force-stop 过期时提示重试并清理旧会话 | P2.5 / P3.2 |
| [ ] L04 | ASM 与 IntentSender 对照 | 专用 D37 | 第 3.4 节及 ASM 证据中的 12 组适用用例; 再分新 task / clear-task / 多窗口; 可见发送、后台发送、真实 a11y 例外和 SAW 例外分开记录 | 受保护同 task 下层启动有明确拦截日志, 例外 / disable有实际目标; 方法返回不等于打开; 新任务组合需独立结论 | P2.5 |
| [ ] L05 | 文件分享 / URI / 打开其他应用 | 全部 + D37 | app.viewFile、分享测试图片 / 文本、文件选择器结果, 从可见页与后台脚本分别调用; 自有 fixture 测显式 Intent 与外来嵌套 Intent 转发, debug 检查隐式 URI 授权诊断 | 接收应用能在声明授权内读取专用文件, 无越权 / FileUriExposedException; 外来 Intent 不擅自获得额外权限; 缺授权、重定向保护或后台受限能理解; API 37 诊断不能无条件在旧系统调用 | P2.15 / P3.3 |
| [ ] M01 | 全屏 / 单应用 MediaProjection | 全部 + D37 | smoke-ui.js 各跑全屏和单应用, 取消后重试, 连续两个会话, 系统停止后重启; 看实际像素、回调、FGS | 每次会话合法取得授权, 图片非空且内容 / 裁剪正确, 停止释放资源, 取消不会被当授权成功 | P2.15 / P3.2 |
| [ ] M02 | Xiaomi 单应用截图已知缺口 | D35 | 同机同授权先用 compile 36 / target 36, 再 compile 37 / target 36, 最后 compile 37 / target 37; 单应用选 AutoJs6, 与全屏对照; 记录系统结果和宿主回调 | 找到“单应用返回未授权”的最小条件并归类; 现状未解决前此项保持 FAIL / BLOCKED, 不能拿 AVD 成功关闭 | P1 观察 / P2.15 |
| [ ] M03 | resize、旋转、镜像与安全窗口 | D35 + D37 | 捕获时旋转、分屏、改窗口尺寸, 检查 resize 回调 / 图片尺寸; 在自有 FLAG_SECURE 测试页验证禁止内容, 检查镜像权限路径 | 尺寸变化后继续正确捕获, 无旧 VirtualDisplay / ImageReader 泄漏; 安全内容按系统拒绝, 不绕过保护 | P2.12 / P2.15 |
| [ ] M04 | 完整 Screen Color Picker | D33 或 D35 + D37 | 从真实抽屉入口授权插件, 授予 overlay / capture, 在自有已知色块上拖动 / 点击, 回传颜色; 取消、停止、重开 | 颜色与测试色块一致并说明色彩空间, 窗口坐标正确, 回传确实到达宿主; 到权限说明页不算完成 | P2.5 / P5.4 |

### 4.6 存储、输入、WebView 与其他系统能力

| ID | 测试项 | 设备 | 测试方法 / 自动化 | 预期结果 | Roadmap |
| --- | --- | --- | --- | --- | --- |
| [ ] S01 | 共享存储与所有文件访问 | 全部 + D37 | 在专用测试目录创建 / 读写 / 改名 / 删除文件, 分别拒绝和授予所有文件访问; 同时执行 assets / 私有目录脚本 | 私有脚本不依赖无关共享存储授权; 需要共享路径时给正确引导, 旧 WRITE_EXTERNAL_STORAGE 不被误认为全盘权限 | P2.8 / P2.16 |
| [ ] S02 | OTG / SD 卡与卷生命周期 | 有 USB OTG / SD 的 D35 + D37 | 插入含测试文件的介质, 枚举卷并用 SAF / 文件管理器读写, 拔出、重插、失效 URI; 文件在写入前后校验哈希 | 正常卷可见可读, 异常卷 / 拔出可恢复且无崩溃; 不可靠卷过滤语义待确认时保留观察和配置 | P2.8 / P5.5 |
| [ ] S03 | MediaStore、扫描与回收站 | D33 或 D35 + D37 | images.save 保存已知图、media.scanFile、相册 / 文档选择器, 系统回收站移入 / 恢复 / 删除, 文件管理器版本历史分别测 | 内容与条目同步, 目录查询不越权; App 自有回收站与 MediaStore trash 分开, 未确认的新语义不凭 change 名字定结论 | P2.8 |
| [ ] S04 | 文件插件热插拔与错误输入 | D33 或 D35 + D37 | Archive / Image Tools / Viewer / File / APK Inspector 用专用样本; 中途撤销 URI、拔出 OTG, 重开文件 | 错误可恢复, 不操作样本外文件; UI、provider、返回描述符各无挂起 | P5.5 |
| [ ] UI01 | 平板方向 / 分屏 / 窗口调整 | D35 + 大屏 D37 | 在 sw>=600dp 上运行含 setRequestedOrientation 的 UI 脚本, 编辑器 / 控制台 / overlay 旋转、分屏、连续调整尺寸; 手机上另做方向对照 | 大屏 target 37 不依赖方向 / 尺寸豁免; 布局可用、状态不丢失; API 35 行为只与其本机旧 target 基线比较 | P2.12 |
| [ ] UI02 | IME、Symbols Settings 与配置变化 | D35 + D37, D28 补旧 API | Symbols Settings 编辑符号, 脚本输入框 / EditActivity 弹键盘、Back收起、旋转再点击输入; 夜间 / 色彩 / 外设变化记录重建或 onConfigurationChanged | 焦点、光标、文本和 adjustResize 正常; 旋转不自动恢复 IME 时仍可用户唤起, 不以旧系统效果强套 Android 17 | P1 / P2.12 |
| [ ] UI03 | 物理键盘 / 触摸板 / Back / 边到边 | 有键盘 / 触摸板的 D35 + D37 | 键盘导航、Meta / Back 等系统快捷键、密码可见设置, touchpad捕获, 手势返回取消 / 完成, 三键导航, 刘海 / 系统栏 | 无焦点陷阱或输入吞噬, 文字和按钮不被系统栏遮挡; 系统保留快捷键不误判为应用丢键; 记录具体映射 / OS版本 | P2.12 / P2.15 / P3.3 |
| [ ] UI04 | WebView、字体与默认 UA | 全部 + D37 | 文档、HTML / Markdown 预览、ui.webview、Ace / LSP; 默认 / 自定义 UA、CJKV与符号字体、文件选择; 记录 WebView版本 | 正确渲染和输入, 默认 UA按实际版本记录; 自定义后缀不被当作恢复完整UA; 同资料回环服务可用, File System Access 未确认能力单列 | P2.11 / P5.4 |
| [ ] O01 | 测试短信 / OTP / 通知监听 | 有测试 SIM 和发送端的 D37 | 发送自有普通短信与测试 OTP, 记录时间; 用已获权限的脚本读取 content://sms / 广播与 observeNotification, 对照普通文本、验证码及恢复窗口 | 按实际平台过滤 / 延迟记录可见性, 不把过滤当丢包; 不读取真实账号 OTP; 真机没有 emu sms send, 未识别为OTP不能证明无过滤 | P2.10 |
| [ ] O02 | CT / ECH 与企业 / 私有 CA | D37, D35 基线 | 对固定公网TLS端点、显式配置的debug私有CA域、企业代理分别请求; 记录信任链、CT、协商和服务端ECH证据; LAN先满足N01 | 链信任失败与CT失败区分; 不靠全局关闭CT使测试变绿; 只有网络库 / 对端支持且有协商证据才记录ECH生效 | P2.6 |
| [ ] O03 | 蓝牙 RFCOMM / 联系人查询 | 有测试外设 / 测试联系人的 D37 | 已配对外设收发后主动断开读流; 绑定特权API用普通UID负例; 查询 ContactsContract.Data 合法列和旧账户列 / 非法SQL | EOF / 权限 / 查询结果按已核实平台行为记录; 不把RFCOMM变化套到普通TCP, 不对实际联系人做修改 | P2.15 |
| [ ] O04 | 内存压力与退出原因 | 专用 D37 + D16K | 固定 OpenCV大图、OCR、Node负载逐级增加并记录输入大小 / PSS / swap / thermal; 查看退出原因与崩溃报告, 默认限制先测 | 正常规模可用; 达限制的系统终止或应用拒绝有可读原因, 无残留执行; 不用 ignore all 改全设备限制, 不把实验强制杀进程当实际默认结果 | P2.13 / P5.3 |
| [ ] O05 | 真实16KB安装与原生链 | D16K | 独立记录getconf和内核页大小, 安装最终宿主 / 用户APK / 所有适用原生插件, 跑dex/native/OCR和独立runtime最短链路 | 真内核16KB下无兼容模式依赖和dlopen / JNI错误; 每个插件单独证据, APK对齐或4KB真机成功不能替代 | P3.2 / P5 / P6 |

## 5. inrt 用户 APK 专属清单

这些用例必须使用最终 Runtime Kit 经真实 APK Builder 生成的独立 applicationId. `org.autojs.autojs6.inrt` 调试包可用于提前诊断, 不替代用户 APK; 编译工具链和模板还没准备好时记 BLOCKED. 覆盖 API 30 的存储边界可先用 AVD, 但 D33 / D35 / D37 的用户 APK 实机验收仍保留.

| ID | 测试项 | 设备 | 测试方法 / 自动化 | 预期结果 | Roadmap |
| --- | --- | --- | --- | --- | --- |
| [ ] I01 | 三个实际打包样例 | D33S 或 D33R + D35 + D37 | 真实打包 a11y 冒烟、UI模式、定时任务+TTS 三个项目; aapt确认target37, 安装并执行对应A/UI/T/AU行 | 三个APK各能启动和完成目标路径, 不只有打包成功; 内嵌OCR的样例另外识别测试图 | P3.3 / P3.4 |
| [ ] I02 | 29 -> 37 覆盖升级 | D33 + D35 + D37, API30辅助 | 同applicationId / 签名, 递增versionCode; target29旧包创建共享 / 私有文件并授权, 覆盖target37; 不先grant或清数据 | 原文件 / 配置保留, 实际访问与迁移结果明确; preserveLegacyExternalStorage不能替代实际迁移证明 | P2.16 / P3.3 |
| [ ] I03 | 首装 / 重装与存储取消 | D33 + D35 + D37 | 独立样例首次target37安装、卸载重装分别测; 取消 / 返回 / 拒绝所有文件访问; assets与共享路径脚本分开 | 不需要共享存储的脚本可跳过授权启动; 需要共享路径时有明确引导; 不因升级曾成功而宣称首装也成功 | P2.16 / P3.3 |
| [ ] I04 | 权限、组件与包可见性 | D31 + D33 + D37 | 独立APK冷启动检查系统 / 自定义启动画面, 通知允许 / 拒绝, app.launch/getAppName测试已知包, events.broadcast, loadDex, 显式/隐式Intent, MediaProjection每会话授权 | 启动画面无卡死或错误重复导航; 无旧存储权限误用、缺exported / mutable标记或动态接收器错误; 请求回调不代替权限已授予 | P3.3 |
| [ ] I05 | 无Launcher / 开机 / 后台恢复 | 专用D33 + D35 + D37 | 分别打包有 / 无Launcher入口的配置; 设置权限后冷启动、普通进程退出、真实开机、后台网络 / TTS; 记录用户可到达的恢复入口 | 正常脚本执行和受限后的恢复方式明确; 无入口不能陷入永久无法授权; FGS存在不等于后台音频有WIU | P2.16 / P3.3 |
| [ ] I06 | 用户APK完整新规则回归 | D37, D33 / D35旧系统对照 | 对最终独立APK复用N01-N02、AU01-AU04、M01、L03及UI01-UI03的输入和预期, 从其脚本 / UI实际触发; 以该APK自己的UID / 权限和应用设置验收 | 宿主授权不替代用户APK授权, 网络 / 音频 / 截图 / Back / edge-to-edge结果独立; 全部有真实用户入口证据, 不用宿主test APK代测生成的APK | P2.16 / P3.3 / P3.4 |

## 6. 全部 42 个插件的实机验收行

每行都要在 **至少一台真实 4 KB API 33 或 35 设备**上跑最终 target 37 插件, D37 到位后再完成其新规则暴露项; 原生插件再关联 O05. 每行保存插件与宿主双方 APK / 提交, 以插件实际 minSdk 和 ABI 选择设备. 共用宿主进程、独立插件 UID、嵌入用户 APK 三种运行方式分开记录. 下面最短功能链不能替代该仓库已有更完整的 DeviceTest; 反过来, 缺少插件导致 instrumentation skip 也不算通过.

自动发现 runner 用 `adb -s SERIAL shell pm list instrumentation`; 从该插件仓库 README / androidTest 确认类名与预置样本后执行 `am instrument -w -r -e class 完整测试类 PLUGIN_TEST_PACKAGE/androidx.test.runner.AndroidJUnitRunner`. 不猜测42个包共享同一个类名, 也不使用宿主native套件替代独立runtime测试.

| ID | 测试项 | 设备 | 测试方法 / 自动化 | 预期结果 | Roadmap |
| --- | --- | --- | --- | --- | --- |
| [ ] PL01 | OpenCV | 4KB33/35 + D37 / D16K | native套件的2个OpenCV方法; 真实脚本矩阵运算 / 图像处理, 冷进程与缓存复用 | 计算正确, 错误可捕获, staging无写权限; 不是从意外内置库加载 | P5.1 / P2.3 |
| [ ] PL02 | Image-Quantization | 4KB33/35 + D37 / D16K | native套件量化方法; 已知图量化、透明度、重复加载 | 输出可解码indexed PNG, 颜色/透明度合理, 原生staging只读 | P5.1 / P2.3 |
| [ ] PL03 | Paddle-OCR-PP-OCRv3 | 4KB33/35 + D37 / D16K | OCR工具 `--jobs paddle:v3`, 独立provider与内嵌用户APK各识别测试图 | 命中已知文本, 两种运行方式各有结果, 取消后无残留计算 | P5.2 |
| [ ] PL04 | Paddle-OCR-PP-OCRv4 | 4KB33/35 + D37 / D16K | OCR工具 `--jobs paddle:v4`, provider及内嵌APK | 已知文本命中, JNI与Binder无错误 | P5.2 |
| [ ] PL05 | Paddle-OCR-PP-OCRv5 | 4KB33/35 + D37 / D16K | OCR工具 `--jobs paddle:v5`, provider及内嵌APK | 已知文本命中, 模型资源与ABI正确 | P5.2 |
| [ ] PL06 | Paddle-OCR-PP-OCRv6 | 4KB33/35 + D37 / D16K | OCR工具 `--jobs paddle:v6`, provider及内嵌APK | 已知文本命中, 冷启动 / 重用均完成 | P5.2 |
| [ ] PL07 | Rapid-OCR-PP-OCRv3 | 4KB33/35 + D37 / D16K | OCR工具 `--jobs rapid:v3`, provider及内嵌APK | 正确文本, 接口与原生资源完整 | P5.2 |
| [ ] PL08 | Rapid-OCR-PP-OCRv6 | 4KB33/35 + D37 / D16K | OCR工具 `--jobs rapid:v6`, provider及内嵌APK | 正确文本, 推理结束 / 取消后释放 | P5.2 |
| [ ] PL09 | APK-Builder-Template | 4KB33/35 + D37 | I01三个样例由该模板实际生成; 核对APK manifest后安装执行 | 用户APK target37且三种样例可用, 不被builder改回29 | P5.2 / P3.4 |
| [ ] PL10 | NodeJs-Runtime | 4KB33/35 + D37 / D16K | 自有脚本计算3、timer停止、加载受支持.node; Node进程http.createServer与LAN客户端 | 正确返回和退出; Node UID权限单独处理, 回环与LAN分开 | P5.3 |
| [ ] PL11 | Bun-Runtime | API>=33的4KB机 + D37 / D16K | Bun脚本计算3、Bun.serve双向LAN、取消; 记录实际exec进程和库 | 输出与退出正确, exec/dlopen单独证据, 不将Java DCL规则直接套用 | P5.3 |
| [ ] PL12 | Python-Runtime | 4KB33/35 + D37 / D16K | Python脚本计算3、线程退出、可用扩展模块和socket server/client; 后台长任务FGS | 插件UID权限正确, Chaquopy/JNI正常, 停止释放端口和服务 | P5.3 |
| [ ] PL13 | Lua-Runtime | 4KB33/35 + D37 / D16K | Lua最短脚本、宿主桥调用、取消循环、插件进程重建 | 返回值正确, 无Binder协议错误或挂起 | P5.3 |
| [ ] PL14 | QuickJS-Runtime | 4KB33/35 + D37 / D16K | quickjs单文件及ES module, 固定输出、桥调用与停止 | 正确使用QuickJS且无偷偷回退, 资源清理完成 | P5.3 |
| [ ] PL15 | TypeScript-Engine | 4KB33/35 + D37 / D16K | 编译含类型声明的简单TS并执行, 制造语法错误后重试 | 输出3, 诊断行列正确, 插件恢复后不留旧错误 | P5.3 |
| [ ] PL16 | Java-Runtime | 4KB33/35 + D37 | 编译/运行最小Java返回3, 连续两轮, 检查DEX加载 | 结果正确, DEX不可写或受支持的受控加载, 错误可捕获 | P5.3 |
| [ ] PL17 | Kotlin-Runtime | 4KB33/35 + D37 | 最小Kotlin脚本编译/运行, 错误后重新编译 | 返回3, 编译诊断可理解, DEX加载无新限制失败 | P5.3 |
| [ ] PL18 | DEX-Compiler | 4KB33/35 + D37 | 固定class/jar编译成DEX, 用宿主runtime.loadDex执行 | DEX真实可加载且返回固定值, 只生成文件不算完成 | P5.3 |
| [ ] PL19 | R8-Compiler | 4KB33/35 + D37 | 固定输入与keep规则编译优化DEX并实际加载, 检查平台库版本使用策略 | 输出可执行, 未混淆必要入口, android-36/37库选择有记录; 与宿主release是否minify分开 | P5.3 |
| [ ] PL20 | MCP-Server | 4KB33/35 + D37 | PC真实LAN握手和只读工具查询, 停止/重启、后台与通知拒绝、独立UID LNP | 工具返回正确、授权提示归属插件, specialUse服务状态真实 | P5.4 |
| [ ] PL21 | Screen-Color-Picker | 4KB33/35 + D37 | M04完整授权、overlay取色、回传、取消和会话重启 | 已知色块结果正确, FGS/capture/overlay都释放 | P5.4 |
| [ ] PL22 | Three-Ember-Player | 4KB33/35 + D37 | AU01/AU03/AU05, 本地与LAN媒体, 后台/锁屏切换列表 | 实际可听, 媒体键和停止有效, 插件本地网络权限正确 | P5.4 |
| [ ] PL23 | Three-Terra-Player | 4KB33/35 + D37 | 同上, 单独运行该播放器并保留其UID与服务记录 | 该插件独立通过, 不借另一播放器结果 | P5.4 |
| [ ] PL24 | Three-Stone-AI | 4KB33/35 + D37, NPU机按实际可用 | 固定短prompt本地推理, 取消, 内存压力; 有NPU时记录实际delegate, 无NPU时验证回退 | 不崩溃、不假报使用NPU, 无NPU设备仍可走支持的路径; 是否声明可选特性依实际实现核查 | P5.4 |
| [ ] PL25 | HTML-Previewer | 4KB33/35 + D37 | 本地HTML含资源/输入框/文件选择, 默认UA与LAN资源授权三态 | 页面可用、权限错误可理解, 不依赖完整默认UA | P5.4 |
| [ ] PL26 | Markdown-Previewer | 4KB33/35 + D37 | Markdown含中文、代码块、本地/远程图片和链接 | 正确渲染、打开链接与资源读取符合授权 | P5.4 |
| [ ] PL27 | Offline-Docs | 4KB33/35 + D37 | 断外网浏览、搜索、锚点跳转; 同资料loopback服务重启 | 文档可离线访问、来源版本正确, 不因LNP拒绝破坏同资料回环 | P5.4 |
| [ ] PL28 | Ace-Editor | 4KB33/35 + D37 | 语法高亮、补全、跳转、LSP回环, 旋转、键盘输入与插件重连 | 编辑内容不丢, 补全/索引正确, IPC与LSP恢复 | P5.4 |
| [ ] PL29 | MLKit-Barcode | 4KB33/35 + D37 / D16K | 固定内容的二维码/条码与空白图, 连续调用并取消 | 解码内容准确, 空白无误报, Binder结果可读 | P5.5 |
| [ ] PL30 | MLKit-OCR | 4KB33/35 + D37 / D16K | 固定中英文图, 确认模型已就绪后识别/取消 | 已知文本命中, 模型下载失败与识别故障区分 | P5.5 |
| [ ] PL31 | Yolo-NCNN | 4KB33/35 + D37 / D16K | 固定模型/测试图, 记录类别、框、坐标, 多次调用与取消 | 与该模型基线一致, provider不挂起, 内存回落 | P5.5 |
| [ ] PL32 | MediaInfo | 4KB33/35 + D37 / D16K | 固定视频/音频, 读取时长/编码/轨道; 无效文件负例 | 字段符合样本, 版本化协议兼容, 错误不杀宿主 | P5.5 |
| [ ] PL33 | OpenCC | 4KB33/35 + D37 / D16K | 固定简繁样例双向转换, 词典资源加载及重复调用 | 文本与该配置基线一致, 资源失败可解释 | P5.5 |
| [ ] PL34 | ImGui | 4KB33/35 + D37 / D16K | 自有窗口绘制/触摸/关闭, privilege broker可用与拒绝路径 | 交互正常、窗口退出后无残留, 权限失败不越权回退 | P5.5 |
| [ ] PL35 | Archive-Manager | 4KB33/35 + D37 | 专用ZIP列举、解压、中文名与坏包, 撤销SAF/拔OTG | 内容哈希一致, 错误清楚且只操作测试目录 | P5.5 |
| [ ] PL36 | Image-Tools | 4KB33/35 + D37 | 固定图片旋转/缩放/保存, 拒绝存储与无效图 | 尺寸/像素和输出路径正确, 无静默丢文件 | P5.5 |
| [ ] PL37 | Image-Viewer | 4KB33/35 + D37 | URI打开、缩放旋转、切图、权限撤销、外部卷拔出 | 显示正确, 状态可恢复, 不残留失效描述符 | P5.5 |
| [ ] PL38 | File-Inspector | 4KB33/35 + D37 | 已知大小/哈希的普通文件, URI和OTG文件, 撤权 | 描述匹配样本, 只读查询无外部写入 | P5.5 |
| [ ] PL39 | APK-Inspector | 4KB33/35 + D37 | 已知manifest/签名的APK与损坏APK, 文件菜单入口 | package/版本/SDK/签名信息准确, 坏包清楚失败 | P5.5 |
| [ ] PL40 | Pinyin | 4KB33/35 + D37 | 固定中文含多音字, 公开样例转换/排序, 重复IPC调用 | 与所选字典/策略的既有结果一致, 取消无挂起 | P5.5 |
| [ ] PL41 | Pinyin4j | 4KB33/35 + D37 | 多音字读音矩阵与已支持拼音体系, 对照旧插件兼容路径 | 能力声明与返回一致, 老版本限制有明确回退 | P5.5 |
| [ ] PL42 | Accessibility-Compat | 4KB33/35 + D37 | 实际启用兼容插件后执行A01/A03, 启停插件与宿主重连 | 兼容能力真实生效, 服务/选择器/动作正常, 不只读取元数据 | P5.5 |

## 7. 不列为必须实体机完成的工作

编译 / lint / JVM / APK元数据 / native静态对齐 / CI、API diff / 文档核验不需要实体机. 单一compat开关的语义定位、故意破坏缓存权限、人工压低进程内存和音频全局throw模式优先留在可恢复专用AVD. `cmd audio ... throw` exit 0不证明模式已生效, `disable`也不等于恢复默认. 若必须用专用真机做这些诊断, 先保存并能恢复原状态, 不混入默认规则验收.

现有模拟器证据已经覆盖安装恢复、Looper、只读加载和ASM若干路径, 见 [P2.3](evidence/android-sdk-37-p23-20260913.md)、[P2.4](evidence/android-sdk-37-p24-20260913.md)、[安装恢复](evidence/android-sdk-37-p25-recovery-20260913.md)、[ASM](evidence/android-sdk-37-p25-asm-20260913.md). 本文的对应真机行用于补齐OEM、ARM、真实外设/网络和最终APK行为. Android 17新规与Android 14-16既有规则分别记录, 不把旧系统已经存在的问题全部归因于target37.

## 8. 本轮交付的验证边界

本轮只执行 5 台设备的只读清点、API 35 平板的只读快照和本机工具测试, 没有安装测试 APK、运行手机 instrumentation、改权限、重启手机或执行上述功能验收. 实际验证如下:

| 检查 | 结果 / 原始记录 |
| --- | --- |
| Python 语法与 CLI | 6 个 Python 文件语法通过; 5 个工具 help 可用; 7 组不完整 / 错误设备与网络参数在调用 adb 或监听 socket 前被拒绝, 见 `build/sdk37-physical-20260913/tool-validation.json` |
| 本机单元测试 | `py -m unittest discover -s tools/sdk37 -p test_physical_device.py -v`, 4/4 通过; 包括错误 / 缺失 / 跳过 / 重复 / 崩溃判定与 HTTP / UDP 固定响应、来源过滤 |
| 历史日志解析 | 新解析器正确读取已有 P2.4 AVD 原始日志中的 14 项完成记录; 仅验证解析, 未重新执行这些设备测试 |
| 清单与链接 | 99 个唯一 ID, 42 个插件与主 Roadmap 附录 E 完全对应; 本文、主 Roadmap 与工具 README 的本地文件链接存在性检查通过; CSV 全部 NOT_RUN |
| 真机只读采集 | `build/sdk37-physical-20260913/inventory` 与 `snapshot-api35`; API 35 不支持 `am memory-limiter status`, 退出码 255 单独记录, 其他快照命令成功 |

这些工具检查不等于真实 LAN 或 Android 17 实体机验收; 全部测试行保持未勾选. 后续按 CSV 逐项补结果, 再将对应结论回填主 Roadmap.
