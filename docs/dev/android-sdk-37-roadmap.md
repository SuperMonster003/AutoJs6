# AutoJs6 Android SDK 37 (Android 17) 升级 Roadmap

本文是 AutoJs6 宿主 (含 inrt 打包模板与仓内 modules / plugin-api / test-apps) 以及同目录 42 个官方插件仓库把 `compileSdk` / `targetSdk` 从 36 升到 37 的可执行状态表.
以 2026-09-13 的本地代码快照 (宿主 `master@60b147716`, `VERSION_NAME=6.8.0`, `VERSION_BUILD=5280`) 与同日的 [探针记录](evidence/android-sdk-37-probe-20260913.md) (compileSdk 37 探针编译, API 37 模拟器 `dumpsys platform_compat` 清单) 为起点, 每个条目均可独立 Check 并落地.

2026-09-13 复核: [执行条件与证据校正](evidence/android-sdk-37-review-20260913.md). 随后完成 P0.2 与 P1 本地实现和验证, 见 [P0.2 / P1 实施记录](evidence/android-sdk-37-p0-p1-20260913.md), 以及 [P2.3 动态加载只读适配](evidence/android-sdk-37-p23-20260913.md)、[P2.4 Looper / 线程适配](evidence/android-sdk-37-p24-20260913.md). [P2.1 本地网络权限](evidence/android-sdk-37-p21-20260913.md) 的代码、模拟器验证及关联文档 / 声明同步已落地, 真机网络、跨 profile 与完整 MqttService 流程仍待验收. 2026-09-14 下午按第 0 节的调整执行 P3.1: 当前正常配置为 compileSdk 37 / 宿主 targetSdk 37 / inrt targetSdk 37, 见 [P3.1 切换与门禁记录](evidence/android-sdk-37-p3-20260914.md); P1 的 SDK 36 / 37 lint 差异为零, 但既有错误仍存在, 远程 CI 尚未验证, P1 不标记整体关闭. 当前 release 未启用 minify, P1 的 release 冒烟不构成 R8 优化验证. P2 其余条目及 P3-P6 的整体目标仍未完成.

实体机执行入口: [完整测试清单](android-sdk-37-physical-device-tests.md), 共 99 行, 含宿主 / 系统 51 行、inrt 用户 APK 6 行、全部插件 42 行. 每行列出设备、步骤、预期和本 Roadmap 的对应项; `tools/sdk37/physical_device.py checklist` 可导出带状态 / 证据列的 CSV. 2026-09-14 维护者另提供 Samsung SM-F776B (`localhost:37478`, API 37, ARM64, 实际 4 KB 内核), 原 5 台真机继续承担旧系统回归; 真实 16 KB 内核设备仍待补齐. 接入设备本身不关闭功能验收项.

P2.6 本地验证已落地: [TLS / CT / ECH 实施记录](evidence/android-sdk-37-p26-20260913.md). 修复 `isInsecure` / `insecure` 污染后续请求的共享 TLS 配置; API 37 的 target 36 / 37 与独立兼容开关对照, 加上 API 24 回归, 共 7 组 42 次测试执行通过. 生产 CT / ECH 策略保持默认, 企业代理、实际公网信任链及真实 ECH 协商仍待补证, 不将 P2.6 整体关闭.

2026-09-14 已确认附录 A 的 Q1=a / Q2=a / Q3=b / Q4=a / Q5=a / Q6=b / Q7=b, 并完成 [Q7 明文配置迁移](evidence/android-sdk-37-q7-20260914.md): 修复 release 普通 HTTP 被 NSC 拒绝的问题, 宿主 / inrt 在 API 24 / 37 上 HTTP 40/40、TLS 回归 40/40. Samsung D37 恢复连接后另完成 [真机 62/62](evidence/android-sdk-37-q7-samsung-20260914.md): HTTP 24、TLS 26、DEX 4、Looper / 线程池 8, 含实际 release; target 37 未获 LAN 授权时回环仍通过. O02 / A05 等完整业务与网络拓扑验收继续保留, [Media3 脚本音乐服务](evidence/android-sdk-37-media3-20260914.md) 已接入宿主 / inrt, API 24 / 37 最终自动化 66/66; 后台独立启动与真机听音仍待验. [inrt 按需存储引导](evidence/android-sdk-37-inrt-storage-20260914.md) 已实现, API 24 / 37 的 target 29 / 37 UI 与真实读写最终 33/33、配置测试 6/6、已有所有文件访问授权的覆盖升级 4 项通过. Samsung 再次上线后另完成 [存储启动 16 项与升级 4 项真机补验](evidence/android-sdk-37-inrt-storage-samsung-20260914.md), 测试包与文件已清理. 完整历史迁移 / 开机 / 用户 APK 矩阵及全部插件独立发版继续实施.

P2.7 本地验证已落地: [Parcel / 插件 IPC 实施记录](evidence/android-sdk-37-p27-20260913.md). 所测 API 37 镜像的 `PARCEL_HARDENING` 长度检查已用设备字节码和正反例核实; 宿主 target 36 强制开启与 target 37 默认状态下, 11 个指定插件的最短链路均通过. 所测生产序列化路径无需修正; 真实 Tasker / 桌面操作、其它 ABI / OEM 及 P5 插件 target 37 发布验证继续开放.

P2.11 的 UA 与基本渲染验证已完成: [WebView 实施记录](evidence/android-sdk-37-p211-20260913.md). API 37 / WebView 145 的宿主 target 36 / 37 及独立兼容开关共 24 个场景通过, HTML Previewer 按自身 target / 开关另有 4 个场景通过. 2026-09-14 补齐 [文件选择器与 File System Access 门控](evidence/android-sdk-37-p211-files-20260914.md): 修复 UI 根布局丢失 Activity context, 四组 24 场景符合预期, 含 20 次真实选择器往返及 4 次门控提前拒绝. 实际保存 / 写入、目录操作、持久化与实体机 UI04 继续保留, 不将 P2.11 整体关闭.

P2.8 的存储调用与开关作用范围、系统选择器和权限状态验证已落地: [存储实施记录](evidence/android-sdk-37-p28-20260913.md). P2.9 修复了多位小组件 ID 导致的路径 / 点击映射误删, 并完成代表通知路径与内存限制对照: [小组件 / 通知实施记录](evidence/android-sdk-37-p29-20260913.md). 不可靠 USB / OTG、真实 Launcher / OEM 行为及 Icon 上限的公告 / 镜像差异继续保留, P2.8 / P2.9 不整体关闭.

P2.10 的 [短信即时可见性对照](evidence/android-sdk-37-p210-20260914.md) 已完成: 普通短信 / 普通 OTP / WebOTP 在四组 target / compat 下共 24 项查询 / 广播比较符合预期. 通知监听仅首个正式 target 36 默认组收到两个脱敏更新, 后续 target 36 / 37 都有回调缺失, 原因未定位; 真实三小时恢复也未实测, P2.10 保持部分完成.

P2.12 已修复 UI 脚本进入分屏时因 Activity 重建而退出的问题: [大屏 / 配置 / 输入记录](evidence/android-sdk-37-p212-20260914.md). 后续补齐 Main 搜索、原生 / Ace 选区、大草稿恢复及编辑器 / 日志 IME 布局修复, [32 个检查点通过](evidence/android-sdk-37-p212-followup-20260914.md); 实体机、外设和连续窗口调整等项目继续开放.

P2.13 已实现设置页进程退出记录, target 36 / 37 共 12 个页面检查点通过, 最终正常包空历史另 3/3, JVM 12/12. 当时普通镜像内存限制器 disabled; 2026-09-16 已在专用配置 AVD 完成 Three-Stone 真实模型与 memory.high 压力事件验证, 限制器终止后的宿主退出报告仍未验收. P2.14 已有官方依据和两个 target 的 16 个状态快照; mode=true 时服务仍可绑定, 未复现非辅助工具限制, 不据此误报启用失败. [实现、证据与后续条件](evidence/android-sdk-37-p213-p214-20260914.md). 额外全量 Lint 约 19 分 50 秒后取消且未生成本轮报告, 不标为通过; P2.13 / P2.14 均保持部分完成.

2026-09-16 最后 9 仓 target 步骤已完成, 舰队 **42 / 42 仓 37 / 37** 且插件工作区干净. 九仓完整套件实际通过 225 项, 两个条件用例另补测通过, 共覆盖 227 个独立用例, 无类 / 方法排除; OpenCC 的 9 个用例亦通过完整套件及分阶段补测覆盖. [最后 9 仓收尾记录](evidence/android-sdk-37-p5-fleet-final-20260916.md). 历史 `build/sdk37-*` 已盘点 29 个 / 9.563 GiB, 自动批准审核拒绝实际删除, 目录保留并提供可审阅脚本.

使用方式:

1. 每次会话开始时, 从 "阶段总览" 选取一个或多个未完成条目, 优先级按阶段顺序; 宿主先行, 被宿主进程加载或打包进用户 APK 的插件其次, 独立进程插件再次, 纯 JVM 插件最后.
2. 条目完成后勾选 `[x]`, 并在条目后追加证据 (提交 hash / 测试类名 / 设备型号 / lint 报告路径), 证据等级见附录 D.
3. 涉及 JS 公开 API 或权限模型的条目 (如 P2.1 的本地网络权限), 完成后必须同步 `AGENTS.md` 列出的关联仓库 (文档 / d.ts / Ace LSP 声明) 与 `.changelog` (10 语言) 并重新生成 README.
4. 附录 A 的 Q1-Q7 已由维护者于 2026-09-14 确认, 结果回填到第 1 节; 按对应决策实施, 不重复征询.
5. 附录 B 是指定 API 37 镜像中 36 项 targetSdk 门控变更的逐项评估, 不是 Android 17 全部行为变更的全集. `dumpsys` 只能证明变更名称 / ID / 配置存在, 不能证明行为语义或功能通过. 官方文档的核对状态 (VERIFIED / UNVERIFIED) 随条目标注; 页面未提及不能作为 "明确未变" 的证据, 网络失败时保留待核验状态.

---

## 0. 2026-09-14 调整: 验收策略与阶段顺序

### 0.1 问题

截至 2026-09-14 下午: P0 / P1 完成, P2 的 16 个子项代码适配全部落地 (83 项已勾选), 但每个子项都保留一条开放式的真机补证条目 (完整 MqttService、VPN / Wi-Fi Direct / mDNS 拓扑、企业 TLS 代理、三小时 OTP 等待、真实 16 KB 内核设备、OEM 桌面等), 而 P3 (真正切换 targetSdk) 的依赖写作 "P2 全部", [实体机清单](android-sdk-37-physical-device-tests.md) 99 行零勾选. 结果是 Roadmap 在 P2 的验证尾巴上循环, 始终到不了 P3. 维护者明确表示不接受大批量实体机测试, 要求简化流程并提供自动化脚本.

### 0.2 新增决策

D19-D21 已写入第 1 节: P3 只由自动化门禁把关 (D19); 未关闭条目按 AUTO / RC-SMOKE / WATCH / CI 三分 (D20); 插件舰队按脚本批量推进 (D21).

### 0.3 门禁入口

```powershell
$Sdk37Adb = 'E:/.android/sdk/platform-tools/adb.exe'
# 模拟器 (AVD_API_37, 16 KB 镜像): 安装 + 全部无人值守套件 + 完整 Looper + a11y 冒烟 + JVM 单测汇总
py tools/sdk37/run_gate.py --adb $Sdk37Adb --serial emulator-5554 --expected-target 37 --install --with-inrt --reinstall-on-mismatch --enable-a11y --junit-results app/build/test-results --output build/sdk37-gate/avd37
# 旧系统真机: 安装 + 适用的无人值守套件 (不改权限 / compat, 不卸载)
py tools/sdk37/run_gate.py --adb $Sdk37Adb --serial BH900ASK9E --expected-target 37 --install --with-inrt --output build/sdk37-gate/api28
```

套件按设备自动选择, `--suite` 可显式指定; 每次使用新的输出目录. 门禁不授予运行时权限、不改兼容开关、不在真机上卸载; 主机侧自检 `py -m unittest discover -s tools/sdk37 -p test_run_gate.py`. 门禁 PASS 的含义与边界见附录 D 的 `GATE` 行.

### 0.4 发布候选真机冒烟 (RC-SMOKE) 固定清单

每个发布候选人工执行一次, 总计约 30 分钟; 任一行失败回到对应 P2 条目修复后重跑门禁. 不扩展本表, 新增的真机疑点一律先记 WATCH.

| # | 设备 | 操作 (人工, 约耗时) | 对应条目 |
| --- | --- | --- | --- |
| 1 | API 37 真机 (Samsung SM-F776B) 或 `AVD_API_37` | 门禁 `--install --with-inrt` PASS | P3.2 |
| 2 | 一台旧系统真机 (API 28 或 31) | 门禁 PASS; 首页 / 抽屉 / 设置打开无崩溃, 无新权限弹窗 (3 分钟) | P3.2, B02 |
| 3 | API 37 真机 | VS Code 显式连接触发本地网络权限弹窗, 拒绝后再授权连接成功 (3 分钟) | P2.1, N02 |
| 4 | API 37 真机 | `media.playMusic` 与 TTS 前台可听, 切后台 / 锁屏后行为符合 P2.2 结论 (3 分钟) | P2.2, AU01 |
| 5 | API 37 真机 | 桌面固定快捷方式创建并点击一次 (2 分钟) | P2.5 / P2.7 |
| 6 | API 37 真机 | 文档 WebView 选一次文件、保存一次文件 (3 分钟) | P2.11 |
| 7 | API 35 平板 | 悬浮窗 + 控制台 + 分屏一次, IME 中文组合输入一次 (5 分钟) | P2.12, UI01-UI04 抽样 |
| 8 | API 33 真机 | target 29 打包 APK 覆盖安装为 target 37 打包 APK 一次, 共享文件仍可读 (5 分钟) | P3.3 迁移, I02 |
| 9 | 任意真机 | 定时任务与开机自启各触发一次 (5 分钟, 含重启) | P2.2 / P3.3 |
| 10 | 真实 16 KB 内核设备 (若有) | 安装 + 门禁 | P3.2 DEVICE-16K |

### 0.5 三分法应用结果

P1-P2 剩余 29 条未勾选条目已就地标注: CI 1, RC-SMOKE 8, WATCH 19, 另有 1 条 (P2.16 提前执行 P3.3) 被 P3.1 的实际切换取代. inrt 的安装 / 启动 / 权限声明由门禁 `--with-inrt` 自动覆盖; inrt 测试包与完整网络 suite 归 WATCH.

### 0.6 调整后的阶段顺序

1. P3.1 切换 (2026-09-14 本会话执行) -> 门禁 (`AVD_API_37` + 旧系统真机) -> 提交.
2. P3.3 / P3.4: 门禁 `--with-inrt` + 既有 `run_inrt_storage_probe.py` 自动矩阵 + Runtime Kit 重新生成; 人工只做 RC-SMOKE #8.
3. P5 舰队: 批处理脚本 (D21), 每仓一次提交, 设备验证在 AVD 自动完成.
4. P4 文档 / CI 与 P6 关闭: 以门禁报告与 RC-SMOKE 清单为验收证据.

---

## 1. 固定决策

D1-D4 已由维护者于 2026-09-13 确认. 2026-09-14 又明确选择 Q1=a / Q2=a / Q3=b / Q4=a / Q5=a / Q6=b / Q7=b, 见 D12-D18, 后续不重复征询. D5-D11 仍为执行约定. D12-D13 在本次明确确认前曾按原推荐默认实施; D14 替代早先 Q3=(a) 的最终方案, 不改写早先验证的历史:

| 编号 | 决策 | 含义 |
| --- | --- | --- |
| D1 | 范围 = 宿主 + 全部 42 个官方插件仓库 | 宿主 (app 两个 flavor, 仓内 modules / plugin-api / test-apps / plugins 子项目随 `version.properties` 自动跟随) 先行; 42 个插件仓库作为 P5 逐仓推进, 每仓独立条目, 独立提交, 独立版本递增与 changelog. `AutoJs6-Gradle-Platform-Versions` 无需改动 (数据表已含 `37.0=9.1.1`), `AutoJs6-Official-Plugins-Index` 仅在插件发布时按既有流程刷新. |
| D2 | 两步走 | 宿主先把 `COMPILE_SDK_VERSION` 升到 37 (P1, `TARGET_SDK_VERSION` 保持 36) 单独提交并回归; 行为变更适配在 targetSdk 仍为 36 时借助兼容开关与临时 target 覆盖完成 (P2); 最后单独提交 `TARGET_SDK_VERSION=37` (P3). 每一步可独立回滚, 与 Archive-Manager 插件 2026-08-23 的做法一致. |
| D3 | inrt 模板 `TARGET_SDK_VERSION_INRT` 29 -> 37 | 用户打包的 APK 与宿主同步以 37 为目标, 在 P3 与宿主 targetSdk 同一提交切换. 由于 inrt 一次跨越 8 个 API 等级 (30-37), P3.3 单列 "打包应用 29 -> 37 清单", 存储引导与旧版升级保护按 Q4=(a) / D15. |
| D4 | 探针编译作为 P0 基线 | 2026-09-13 已完成所列 Kotlin / Java 任务的 compileSdk 37 探针 (0 错误, 仅 1 处新增弃用告警) 与 36 基线对照, 结果记入 [探针记录](evidence/android-sdk-37-probe-20260913.md); P1 独立补齐 inrt / release / lint / 出包与设备验证. 源码复核确认 release 的 `isMinifyEnabled=false`, 因此 R8 优化不在当前产物路径中; 将来启用时另建基线. |
| D5 | 验证矩阵 | Android 17 使用本机 AVD (`AVD_API_37` / `aj6-ps16k-37-x86_64`) 与 2026-09-14 接入的 Samsung SM-F776B (`localhost:37478`, API 37, ARM64, 内核 4 KB); Xiaomi 23046RP50C API 35, Sony XQ-DQ72 API 33, Redmi 22120RN86C API 33, Sony XQ-AT72 API 31, Sony G8441 API 28 提供旧系统回归. 每次执行记录实际指纹与状态, 不把旧清点当作当前在线证明; `verifyNativePageAlignment` 在每次 assemble 时继续生效, 实际 16 KB 内核验证仍开放. |
| D6 | 落地位置 = master, 每个子条目一个提交 | 与 a11y / 16 KB Roadmap 一致; 提交时按显式文件清单暂存, 不 `git add -A` (多会话工作区). |
| D7 | 不捆绑无关依赖升级 | 本 Roadmap 只改 SDK 版本与由此强制或直接相关的代码; androidx.core 1.19 (要求 compileSdk 37) 等 "被解锁" 的升级记入附录 F, 不在本 Roadmap 实施. |
| D8 | `autojs6-platform-versions` 保持 1.8.0 | 1.8.0 与 1.8.1 的 `android-api-agp-compat.properties` 相同 (`37.0=9.1.1`), 当前解析出的 AGP 9.4.0 / Gradle 9.7.1 / Kotlin 2.4.0 / KSP 2.3.12 全部满足 API 37; 不因本次升级改动 `settings.gradle.kts` 的插件版本. |
| D9 | changelog 写法 | SDK 日志参考既有 35 -> 36 升级记录, 最终分类以各仓库 `AGENTS.md` 为准; 当前宿主规定 `dependency` 仅统计 Gradle 依赖声明, SDK 平台升级记入 `improvement` (例如 "编译 SDK 版本调整为 37", 英文 "Updated compile SDK to 37"). P1 只描述编译版本, P3 再描述宿主与打包应用的目标版本及用户可见变化; 行为适配按性质写入 `fix` / `improvement`. 不提前宣称 targetSdk 已升级. |
| D10 | `com/stardust/**` 兼容包保持冻结 (2026-10-12 起分布在 `compat/stardust/` 与 `app/` 两处, 见 `stardust-compat-inventory.md`) | 探针编译显示 android.jar 37 未在该包内引发错误; 若后续 lint / 构建暴露必须修改的项, 仅允许 35 -> 36 先例 (`2ab13f3c7` 注释掉 `MainThreadProxy` 的 `Thread.stop` 系列) 那种 "最小编译修正", 不做其它改动. | (2026-10-13 更新: 兼容包副本已按 autojs4-runtime-plugin-roadmap P7.3 方案 3 移除, `com.stardust` 类名改由 `rhino/StardustClassAliases` 映射到宿主类)
| D11 | 插件仓库的节奏 | 纯 JVM 且无行为暴露的插件 (附录 E "暴露" 列为空) 可在一次发布内同时完成 compileSdk 与 targetSdk; 存在本地网络 / 动态加载 / 后台音频 / 屏幕捕获暴露的插件与宿主一样两步走. 各仓库脏工作区 (附录 E) 先由维护者处理或按会话惯例分开提交, 本 Roadmap 的提交不得混入. |
| D12 | Q1=(a), 按需申请本地网络权限 | 2026-09-14 明确确认既有实现. 用户显式连接 VS Code / 开启服务或操作权限入口时引导, 自动连接与脚本网络调用不弹窗; 复用 `runtime.requestPermissions` 的通用名称数组. 只有 OS >= 37 且实际 target >= 37 才需要权限; manifest 声明也仅进入 target >= 37 的 flavor, 旧 target 不声明或申请. `d9465be13`, [P2.1 证据](evidence/android-sdk-37-p21-20260913.md). |
| D13 | Q2=(a), 保持默认 CT 策略 | 2026-09-14 明确确认. 不添加生产 CT / ECH 例外. debug 私有 CA 仅限三个保留测试域; `isInsecure` 只对当前请求生效. 真实域如确需局部 CT 例外, 先补齐信任链 / CT 失败证据再评估, 本次选择不授权未知生产域豁免. |
| D14 | Q3=(b), Media3 `MediaSessionService` 承载脚本音频 | 2026-09-14 明确确认, 替代原推荐 (a) 作为 P2.2 最终方案; 引入本项直接需要的 Media3 依赖符合 D7. 需落实播放控制、焦点、媒体通知、脚本结束清理与宿主 / inrt / Node 入口, 单独验证 TTS 引擎进程及开机 / 定时链. Media3 不自动豁免 FGS 启动和 Android 17 的音频资格限制; 已有可见界面恢复指引仍保留, 不作为 (b) 完成证据. |
| D15 | Q4=(a), 按共享存储需求引导并验证旧安装迁移 | 2026-09-14 明确确认. 无共享存储需求的 assets / 私有目录脚本可跳过所有文件访问授权; 有需求时提供可理解的引导与拒绝结果. `preserveLegacyExternalStorage` 是否采用由 P2.16 / P3.3 升级矩阵决定, 不能视为首次安装的兼容保证. |
| D16 | Q5=(a), 不声明 `isAccessibilityTool` | 2026-09-14 明确确认. 2026-09-16 宿主和 Accessibility-Compat 已检测 Advanced Protection 并提供一致说明, 区分全局模式、实际服务状态及安全设置控制; 全局 mode=true 不等于具体服务被限制. 实际限制后的失败诊断仍按 P2.14 WATCH 验收. |
| D17 | Q6=(b), 全部 42 个官方插件单独发布 SDK 升级版本 | 2026-09-14 明确确认. 每仓完成 P5 的版本、适配、构建、测试及 changelog 门禁后独立发版, 随发布更新官方索引. 保持 D11 的分步规则, 不将只改 compileSdk 或提交代码记作已发布. |
| D18 | Q7=(b), 本 Roadmap 内完成明文流量配置迁移 | 2026-09-14 明确确认, 纳入 P2.6. 将脚本自定义 HTTP 端点的允许策略明确写入主 NSC, 移除 Manifest 旧开关, 统一宿主 / inrt 的 debug 与 release 行为; 保留 GitHub 资源域 HTTPS 限制和证书策略, 验证实际 APK 与网络请求. |
| D19 | P3 切换只由自动化门禁把关 (2026-09-14) | `tools/sdk37/run_gate.py` 一条命令完成: 产物 badging / SHA-256, 安装, 无人值守 instrumentation 套件 (cleartext 4, dex 1, install-state 2, looper-platform 2, media 11; API 37 上另加 parcel 5; 装有 OpenCV / ImageQuant 时 native 3), 模拟器上另加完整 Looper 14 与 a11y 冒烟, JVM 单测汇总, 输出 `gate.json` / `gate.md`. `AVD_API_37` (16 KB 镜像) 与至少一台旧系统真机门禁 PASS 即满足 P3.1 / P3.2 的切换前提; 不再以 P2 全部真机补证为前提. |
| D20 | 未关闭条目三分法 (2026-09-14) | 每条未勾选条目就地标注: `(AUTO)` 已由门禁覆盖, 随门禁自动回归; `(RC-SMOKE)` 进入第 0.4 节的发布候选真机冒烟固定清单 (10 行以内, 每个发布候选人工做一次, 单台设备 30 分钟内); `(WATCH)` 发布后观察 / 用户反馈驱动, 不阻塞任何阶段, 原文保留为排查线索; `(CI)` 由远程 CI 推送后验证. 99 行实体机清单降级为深度验证目录, 不再是阶段门禁. |
| D21 | 插件舰队按脚本批量推进 (2026-09-14) | P5 的 42 仓 compileSdk / targetSdk 修改、构建、单测、版本递增与 changelog 骨架由批处理脚本执行 (P5.0 第 0 步), 逐仓一次提交; 设备验证只用 `AVD_API_37` 上各仓已有 device test / 冒烟脚本, 真机行为归入 WATCH. |

由 D1-D3 派生的硬约束:

- SDK 版本只在 `version.properties` 一处定义 (`COMPILE_SDK_VERSION` / `TARGET_SDK_VERSION` / `TARGET_SDK_VERSION_INRT` / `MIN_SDK_VERSION`), 经 `build-logic/convention/src/main/kotlin/org/autojs/build/Versions.kt` 暴露为 `versions.sdkVersion*`, 47 个构建脚本 (app, modules, plugin-api, test-apps, plugins/apk-builder-template) 全部经此读取; 任何条目都不得在子项目脚本中写死数字.
- 宿主 `MIN_SDK_VERSION=24` 不变, 各插件保留实际有效的 minSdk (见附录 E); P2 中为 API 37 新增的调用一律以 `Build.VERSION.SDK_INT >= 37` 分支或 `*Compat` 包装, 不抬高 minSdk.
- P2 的代码改动必须在宿主 targetSdk 36 / inrt targetSdk 29 下也能正确运行. 每项按真实门控条件判断: 新权限走系统版本 / 目标版本分支, 对所有应用生效的行为在 Android 17 上也必须处理, 不能统一加 `targetSdk >= 37` 后跳过. 兼容开关与 target 覆盖包分别记录条件.

---

## 2. 范围与非目标

范围内:

- 宿主: `version.properties`, `app/build.gradle.kts` (lint / flavor 段), `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/network_security_config.xml`, 第 3.3 节列出的代码锚点, `.changelog/**` + README, `docs/dev/**` (本文与证据), `.github/workflows/{android,release-runtime-kit}.yml` (仅在 SDK 自动下载失败时改动).
- 打包链路: inrt flavor (`TARGET_SDK_VERSION_INRT`), `generateRuntimeKit` 产物, `plugins/apk-builder-template`, `AutoJs6-Plugin-APK-Builder-Template` (模板与测试夹具中的 targetSdk 期望值).
- 42 个官方插件仓库 (附录 E), 每仓的 `version.properties`, changelog, README, 以及暴露列对应的适配代码.
- 关联项目 (仅当 JS 公开 API 或权限模型变化时): `AutoJs6-Documentation`, `AutoJs6-TypeScript-Declarations`, `AutoJs6-Plugin-Ace-Editor` (LSP 声明), `AutoJs6-Plugin-Offline-Docs`.
- Q7=(b) 的 `usesCleartextTraffic` -> Network Security Config 迁移, 以及 Q3=(b) 的 Media3 脚本音频服务.

非目标:

- Android 17 QPR1 / QPR2 的次级 SDK (37.1 / 37.2, 本机已装 `android-36.1` 平台但未装 37.1) 与 `compileSdk { minorApiLevel }` 语法; 待 androidx (activity 1.14 / Compose) 真正要求 37.1 时另立条目 (附录 F).
- Google Play 政策本身 (宿主经 GitHub 分发; Play 对 targetSdk 37 的要求预计 2027-08, 见附录 C 参考); 本 Roadmap 以平台行为正确为验收标准.
- `AutoJs6-Gradle-Platform-Versions` 的数据刷新 (37.1 出现后再做), `AutoJs6-Official-Plugins-Index` 的字段变更 (无需要).
- 归档 / 验证 / 第三方目录 (`.a6-*`, `AutoJs6-m9-*`, `AutoJs4`, `AutoX*`, `Rhino-For-AutoJs6`, `TempApplication`, `Auto.js-SDK-Plugins-Garage` 等).

---

## 3. 现状基线 (2026-09-13)

### 3.1 版本与工具链事实

| 事实 | 含义 |
| --- | --- |
| 初始 `version.properties`: `COMPILE_SDK_VERSION=36`, `TARGET_SDK_VERSION=36`, `TARGET_SDK_VERSION_INRT=29`, `MIN_SDK_VERSION=24`; P1 后 compile 为 37, 其余保持 | 三个值分别对应 P1 / P3 / P3 (D3) 的改动点; `Versions.kt` 以整数读取, 因此写 `37` 而非 `37.0`; debug 临时 target 覆盖由独立 Gradle 属性控制 |
| 工具链 (由 `autojs6-platform-versions` 1.8.0 从 `version.properties` 与其数据表解析): Gradle 9.7.1, AGP 9.4.0, Kotlin 2.4.0, KSP 2.3.12; `android-api-agp-compat.properties` 中 `37.0=9.1.1` | API 37 的最低 AGP 是 9.1.1 (官方 AGP 9.1 发行说明, VERIFIED), 当前 9.4.0 满足, 无需 `android.suppressUnsupportedCompileSdk`; AGP 9.4 要求 Gradle >= 9.6.0, 已满足 |
| 本机 SDK: `platforms/android-37.0` (Platform.Version=17, rev 2, ExtensionLevel 22, 正式版), `build-tools/37.0.0`; AVD `AVD_API_37` 与 `aj6-ps16k-37-x86_64` 均为 `android-37.0;google_apis_ps16k;x86_64` | 平台目录名是 `android-37.0` 而非 `android-37`, AGP 9.4 对 `compileSdk = 37` 已正确解析 (Archive-Manager 先例: 2026-09-12 release APK `compileSdkVersion='37' compileSdkVersionCodename='17'`) |
| Android 17 正式版 2026-06-16 发布 (VERIFIED); Google 博客称 Play 将于 2027-08 要求新应用与更新以 API 37 为目标 (VERIFIED 引文, 具体日期 UNVERIFIED) | 本次升级不受外部截止期驱动, 但插件舰队与宿主同步可避免 2027 年集中返工 |
| 探针编译 (compileSdk 37, target 36): 所列任务 0 错误, 仅新增 1 处弃用告警 (`ui/edit/keyboard/SymbolsSettingsActivity.kt:1110` `InputMethodManager.SHOW_IMPLICIT`); 36 基线对照无其它差异; javac 告警 0 | P1 的编译修正预期较少; inrt / release / R8 / lint / 测试结果不能由此推出; 本机 android.jar 的 fingerprint 类差异已另行核验 (SOURCE, 见复核记录) |
| `build-logic/ksp-version-codes-processor/src/main/resources/version-codes.csv` 已含 `CINNAMON_BUN,Android 17,Cinnamon Bun,17,37,"June 16, 2026"` (`4bdb3bb68`, 2026-03-14); 文档仓库 `api/util.md` 已同步 | `util.versionCodes` / `SdkVersionUtils.sdkIntToString(37)` 已能解析 "17", 无需改动 |
| 上一次升级提交 `2ab13f3c7` (2025-09-26, 35 -> 36): 24 个文件, 主要是 android.jar 删除 / 弃用 API 的编译修正, changelog `dependency` "升级 Android SDK 版本 35 -> 36" | D9 的写法来源; 本次编译修正量远小于上次 |
| CI (`.github/workflows/android.yml`): JDK 21, 未显式安装 SDK 平台, 依赖 AGP 自动下载; `run-gradle-ci.sh` 跑单元测试子集, `assembleApkBuilderTemplateDistribution`, 16 KB 校验 | P1.6 需确认 runner 能取到 `platforms;android-37.0` (预期由 AGP `sdkDownload` 自动完成), 否则补 `sdkmanager` 步骤 |
| Manifest (`app/src/main/AndroidManifest.xml`): `largeHeap` (291), `requestLegacyExternalStorage` (293), `usesCleartextTraffic` (297), `tools:targetApi="tiramisu"`, 无 `screenOrientation` / `resizeableActivity` / `enableOnBackInvokedCallback` / `<property>` 大屏豁免; 前台服务类型 `specialUse` x2 (593, 603, 带 `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`), `microphone` (627), `mediaProjection` (948); 权限含 `INTERNET`, `SYSTEM_ALERT_WINDOW`, `MANAGE_EXTERNAL_STORAGE`, `READ_SMS` / `RECEIVE_SMS`, `BLUETOOTH_*`, `QUERY_ALL_PACKAGES` 等 74 项 | 未声明 Android 16 大屏豁免, 移除该 manifest 豁免预期无增量; 服务类型 / 启动条件仍需 P2.2 / P2.15 核验, 不能由当前声明推出 Android 17 无变化 |
| `network_security_config.xml`: `base-config` 仅系统信任锚, `githubusercontent.com` 域禁明文并附加 `@raw/isrg_root_x1`; 无 `certificateTransparency` / `domainEncryption` 元素 | P2.6 的改动点 (CT 默认开启, ECH 默认开启) |
| 初始 `app/build.gradle.kts`: `lint.abortOnError=false`; app / inrt vital analysis 禁用, 模板分发还会跳过 app vital; CI 显式排除 inrt vital analysis | P0.2 已增加 `-Pautojs.sdk.lint.verify=true`, 启用实际 analysis / report 并在错误时失败; 普通构建保持既有配置. SDK 36 / 37 均有 debug 547 项 Error / Fatal, 两个 release 各 1 项既有 Fatal; 差异为零不等于 lint 清零 |

### 3.2 Android 17 对宿主的影响矩阵

来源: 附录 B (模拟器 36 项门控变更) + 官方 "apps targeting 17" / "all apps" 页面 (核对状态随条目). 按影响排序, 每行对应一个 P2 条目.

| 编号 | 变更 | 生效条件 | 宿主暴露点 | 条目 |
| --- | --- | --- | --- | --- |
| I1 | 本地网络权限 `android.permission.ACCESS_LOCAL_NETWORK` (VERIFIED): targetSdk >= 37 默认阻断到局域网地址的出站 TCP / UDP, 入站 TCP accept, mDNS `.local` 解析; targetSdk <= 36 随 `INTERNET` 隐式授予; 同一 profile 内回环不受此权限限制. 跨 profile 的回环在 Android 17 上另受限制, 与 targetSdk 无关 ([官方说明](https://developer.android.com/about/versions/17/behavior-changes-all#block-cross-profile-loopback-traffic)) | 权限系统, 非兼容开关 (`RESTRICT_LOCAL_NETWORK` 仍为 disabled, 仅测试用); 跨 profile 另测 | VS Code 扩展连接两种模式 (`pluginclient/JsonSocketServer.kt:24` `ServerSocket`, `pluginclient/JsonSocketClient.kt:64-65` 连接电脑); 脚本 `http.*` / `web.*` / `$web.newWebSocket` (`runtime/api/augment/web/Web.kt`, `core/web/WebSocket.kt`); MQTT (`org.eclipse.paho.android.service.MqttService`); Node 桥 (`engine/NodeBridgeProtocol.kt` 的 `newWebSocket`); 用户脚本自建 socket | P2.1 |
| I2 | 后台音频强化 (VERIFIED): 全体应用需要可见 Activity 或非 `SHORT_SERVICE` 前台服务; targetSdk 37 的后台路径还需 while-in-use 能力, 但精确闹钟授权 + `USAGE_ALARM` 可豁免 WIU 条件. 播放 / 音量调用可静默失败, 焦点请求返回 `AUDIOFOCUS_REQUEST_FAILED`; `throw` 测试模式比默认规则更严 ([官方说明](https://developer.android.com/about/versions/17/changes/bg-audio)) | 全体 + target 37 加严 | `runtime/api/Media.java`, `runtime/api/Device.java` (音量), `runtime/api/tts/*.kt` (TTS), Node 桥音频; 定时任务 / 开机自启 / 广播触发的脚本需复核 `AppForegroundService` (`external/foreground/AppForegroundService.kt`) 的实际启动状态 | P2.2 |
| I3 | 动态加载的原生库必须只读 (`THROW_ERROR_FOR_WRITABLE_DCL`, VERIFIED): `System.load()` 可写文件抛 `UnsatisfiedLinkError` | target 37 | OpenCV / ImageQuant stager 原已尝试 `setWritable(false, false)`, 但忽略结果; P2.3 改为在写入临时文件前及返回最终文件前强制验证只读, 修复旧缓存. `AndroidClassLoader` 的 DEX 已有只读处理, 本轮另修复 API 24 / 25 优化输出覆盖原始 DEX; Ace Editor 从已安装的插件 APK 构造 `PathClassLoader` | P2.3 (完成, 见实施记录) |
| I4 | 无锁 `MessageQueue` (`USE_NEW_MESSAGEQUEUE`, VERIFIED) 公开 API 不变, 新实现中反射 `mMessages` 恒为 null; `LOOPER_CLEARS_THREAD_INTERRUPTED` 清除消息派发时的原生中断标记; `OVERRIDDEN_THREAD_START_METHOD` 使线程池绕过工作线程 start 覆写, 仍执行 run, 普通直接 start 调用不变 (后两项为 PLATFORM_CONFIG + DEVICE-37 对照结论) | target 37, 可在 target 36 调试包强制测试 | `Loopers` 的公开 IdleHandler、`MainThreadProxy` / `TimerThread` 中断、`exit()` / `threads.*` / `sleep()`; 宿主 Thread 子类未覆写 start. P2.4 修复了取消回调后历史时间阻止脚本退出的既有问题; 用户反射限制见附录 F | P2.4 |
| I5 | 后台活动启动: `IntentSender` 的 Activity 启动需核对 creator / sender 的显式授权 (VERIFIED); 本机 ASM target 37 门控已实测, 同任务下层启动还受顶部应用的保护声明与 Activity 例外影响 (DEVICE-37); BAL 与后台启动前台服务是两套规则, 悬浮窗例外分别检查 | target 37 | `AndroidPackageSessionInstaller` 现用私有广播接收回调, `PackageInstallStatusCoordinator` 保存会话并提供通知 / 返回应用入口, `PackageInstallStatusActivity` 在可见时发送系统确认 token; `ActivityLaunchOptions` 处理发送方版本分支; 固定快捷方式, 抽屉 ActivityResult 与脚本 `app.launch` / `app.startActivity` 另行覆盖 | P2.5 |
| I6 | Certificate Transparency 默认开启 (`DEFAULT_ENABLE_CERTIFICATE_TRANSPARENCY`, VERIFIED), ECH 默认开启 (`ENABLE_DEFAULT_ENCRYPTED_CLIENT_HELLO`, VERIFIED); API 37.0 实测 ECH 默认模式为 OPPORTUNISTIC(2), 策略值不证明已协商 | target 37 | `network_security_config.xml`; 私有 CA / 企业代理; NSC 配置用户 / 资源 CA 的默认 CT 豁免已核实, 但 CA 仍需先被信任; `isInsecure` 后续请求污染已修复, 实际公网链与 ECH 待补证 | P2.6 |
| I7 | `PARCEL_HARDENING` (PLATFORM_CONFIG; DEVICE-37): 所测镜像中, `Parcel.readValue` 的长度前缀与实际消费字节数不一致时, 由平台 feature flag 和此兼容开关共同决定是否抛 BadParcelableException; 不泛化为所有 Binder 读取 | target 37, 可在 target 36 调试包强制测试 | 插件 Binder 协议 (TaggedWire `byte[]`, `ParcelFileDescriptor`), `InstalledPluginRepository`, Tasker / 快捷方式 extras; 11 个指定插件在两种启用条件下通过, 宿主数据往返及少读字段负例完成; 实际入口仍待 IPC01 | P2.7 ([证据](evidence/android-sdk-37-p27-20260913.md)) |
| I8 | 存储开关作用范围已由镜像实现与对照核实: 卷过滤在 `MediaStore.getExternalVolumeNames()`, 媒体回收目录受调用者 target 与 MediaProvider 自身 compat 共同控制; `REQUIRE_PERMISSIONS_FOR_DIRECTORY_QUERIES` 实为联系人目录提供方权限检查 | 按实际调用方 / provider 判断, 不能统一对宿主 enable 代替 | 文件管理器 / `files.*` / 图片保存扫描 / 应用回收站与历史已回归; 普通模拟 SD 卡通过, 不可靠 USB / OTG 待测. 宿主不是联系人目录提供方 | P2.8; [证据](evidence/android-sdk-37-p28-20260913.md) |
| I9 | 小组件 Bitmap 上限已验证; Icon 合计限制的公告为 target >= 37, 当前镜像实际 target > 37 才抛错, 差异保留. 通知开关按发送应用 UID 检查渲染后 ImageView 内存, 非简单布局尺寸限制 | target / 镜像实现及发送 UID 分别核验 | `ScriptWidget` 多位 ID 映射误删已修复; 内建通知为标准模板, `notice(builder)` 可传自定义视图, 已有正反例与用法说明 | P2.9; [证据](evidence/android-sdk-37-p29-20260913.md) |
| I10 | 短信 OTP 保护 (VERIFIED): 对非豁免应用过滤查询并延迟广播, 普通 OTP 受 `FILTER_GENERIC_OTP` target 37 门控, WebOTP 对全体; 四组即时行为已实测, 三小时恢复待测. 通知 OTP 脱敏延续 Android 15, 后续回调在 target 36 / 37 均有缺失, 未定位 | target 37 / 全体 | 宿主代码不直接读短信, 但声明 `READ_SMS` / `RECEIVE_SMS` 供脚本 (`content://sms`, 广播) 使用; `core/notification/NotificationListenerService.kt:46` (`events.observeNotification`) | P2.10 ([即时矩阵与通知保留项](evidence/android-sdk-37-p210-20260914.md)) |
| I11 | WebView UA 缩减 (`ENABLE_USER_AGENT_REDUCTION`, VERIFIED) 与 File System Access API (`ENABLE_FILE_SYSTEM_ACCESS`, DEVICE-37: 选择 / 取消 / Intent / URI 授权门控已验证, 写入与持久化待测) | target 37; 按 WebView 所属应用的 target / override 生效, 独立插件不继承宿主开关 | `core/ui/attribute/WebViewAttributes.kt:36` (`userAgentString` 属性), 文档 / 预览 WebView; UI 根布局的 Activity context 丢失已修复 | P2.11 ([文件门控与边界](evidence/android-sdk-37-p211-files-20260914.md)) |
| I12 | 大屏: sw >= 600dp 的方向 / 尺寸限制旧豁免在 target 37 失效 (`DISABLE_OPT_OUT_UNIVERSAL_RESIZABLE_BY_DEFAULT`, VERIFIED); 全体: 配置变化导致 Activity 重建后默认不恢复 IME; 减少重建的具体范围已核对官方说明 | target 37 / 全体 | 无旧豁免; UI 脚本分屏退出、Main 搜索、Edit 选区 / 大草稿、IME 与 Log 布局已修复, target 36 / 37 共 32 组模拟器用例通过; 悬浮 UI、外设和实体机仍待验收 | P2.12 |
| I13 | 应用内存上限 (全体, VERIFIED): 按 RAM 的每应用限制, 被杀时 `ApplicationExitInfo` 描述含 `MemoryLimiter:AnonSwap` | 全体 | 设置页历史退出诊断已实现; 2026-09-16 Three-Stone 真实模型在专用 AVD 启用限制器后通过并观察到 memory.high 压力事件, 未触发终止; 宿主限制器退出报告仍待验收 | P2.13 |
| I14 | Android 17 Advanced Protection 限制非辅助工具的无障碍服务 (官方规则 VERIFIED; 所测镜像 mode=true 时仍可绑定) | 与 targetSdk 无关 | 未声明 `isAccessibilityTool` (Q5=(a) / D16); 宿主和兼容插件已有全局状态与准确说明, 实际限制生效时的启用失败诊断仍待验收 | P2.14 |
| I15 | 低影响项: Bluetooth 绑定 API 需特权 (`BONDING_APIS_REQUIRE_PRIVILEGED_PERMISSION`), RFCOMM `read()` 断开返回 -1 (`MAKE_SOCKET_READ_BEHAVIOR_CONSISTENT`, 仅 `BluetoothSocket`), 联系人 `Data` 视图移除账户列与严格 SQL 检查 (`RESTRICT_DATA_URI_COLUMNS`, `ENFORCE_STRICT_SQL_CHECKS`, 仅 ContactsContract), 密钥库每应用 5 万把上限, RenderScript ScriptC 停用 (本仓库无 RenderScript), 镜像虚拟显示权限检查 (`CHECK_ADD_MIRROR_DISPLAY_PERMISSION`, 宿主经 MediaProjection 创建 `VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR`, `core/image/capture/ScreenCapturer.java:284-289`), `ContentCaptureManager.setContentCaptureEnabled(false)` 失效, 物理键盘密码显示拆分 | target 37 | 逐项验证即可, 预期无代码改动 | P2.15 |

需要继续核验的部分 (UNVERIFIED): 前台服务类型 / 超时, `SYSTEM_ALERT_WINDOW` / `TYPE_APPLICATION_OVERLAY`, MediaProjection, JobScheduler / AlarmManager, 预测性返回, 边到边, `android.accessibilityservice` 包与最低可安装 targetSdk. 当前主文档未列某项新限制, 不等于该项没有变化; P2.15 用 API diff / 对应专题文档 / 实机回归分别给出结论. 已有 Android 14-16 限制仍纳入 inrt 29 -> 37 回归.

### 3.3 宿主代码锚点汇总

| 主题 | 锚点 |
| --- | --- |
| 版本定义与消费 | `version.properties`; `build-logic/convention/src/main/kotlin/org/autojs/build/Versions.kt:37-40`; `app/build.gradle.kts:876` (compileSdk), `:881-882` (min / target), `:940-942` (inrt flavor `targetSdk = versions.sdkVersionTargetInrt`), `:1090-1092` (`lint { abortOnError = false }`), `:1455-1466` (`lintVital*` 任务); `plugins/apk-builder-template/build.gradle.kts:44-49` |
| 本地网络 (I1) | `pluginclient/{DevPluginService.kt,JsonSocketServer.kt,JsonSocketClient.kt,JsonSocket.java}`; `runtime/api/augment/web/Web.kt`; `core/web/WebSocket.kt`; `engine/NodeBridgeProtocol.kt`; manifest `MqttService`; 权限助手模式 `permission/{PostNotificationsPermission,DisplayOverOtherAppsPermission,AllFilesAccessPermission}.kt` |
| 音频 (I2) | `runtime/api/Media.java`, `runtime/api/Device.java`, `runtime/api/tts/{AndroidTtsBackend,TtsCommandScheduler,TtsExecution,TtsService}.kt`, `external/foreground/AppForegroundService.kt`, `external/foreground/PythonLongRunningForegroundService.kt:155-161`, `timing/**` (定时任务触发路径), `inrt/InrtBootCompletedReceiver.kt:20` |
| 动态加载 (I3) | `core/plugin/opencv/OpenCvPluginHost.kt:219-229`, `core/plugin/imagequant/ImageQuantPluginHost.kt:226-231`, `rhino/AndroidClassLoader.kt:93-122`, `core/plugin/editor/AceEditorPluginHost.kt:316-326` |
| Looper / 线程 (I4) | `core/looper/{Loopers.kt,MainThreadProxy.java,TimerThread.kt}`, `engine/LoopBasedJavaScriptEngine*`, `inrt/autojs/LoopBasedJavaScriptEngineWithDecryption.kt`; 含 `start()` 的文件: `core/record/{GlobalActionRecorder,Recorder}.java`, `engine/LuaEngineRuntimeBackend.kt`, `runtime/api/Media.java`, `service/{AccessibilityServiceItemHelper.kt,ForegroundService.kt}`, `inrt/theme/preference/ForegroundServiceSwitchPreference.kt` |
| BAL (I5) | `ui/main/scripts/AndroidPackageSessionInstaller.kt:119-180`, `external/shortcut/ShortcutManager.java:46`, `ui/settings/LauncherShortcutsPreference.kt:35-68`, `ui/shortcut/ShortcutCreateActivity.java:161-163`, `ui/main/drawer/DrawerFragment.kt:160`; 13 处 `PendingIntent.get*`, 41 处 `FLAG_ACTIVITY_NEW_TASK` |
| 网络安全 (I6) | `app/src/main/res/xml/network_security_config.xml`, debug 同名覆盖; Manifest 旧开关已由 Q7 迁移 (`494a048df`) |
| IPC (I7) | `core/plugin/**/*Host.kt` 的 Binder 协议, `plugin-api/protocol-wire-api`, `core/plugin/center/InstalledPluginRepository.kt:351`, `engine/NodeModuleSourceFileTransportSession.kt`, `external/tasker/**` |
| 存储 (I8) | `model/script/PathChecker.java:56`, `permission/AllFilesAccessPermission.kt`, `ui/storage/{TrashActivity,VersionHistoryActivity}`, `runtime/api/augment/{files,media}/**`, `ui/main/scripts/**` (文件管理器) |
| 小组件 / 通知 (I9) | `external/widget/ScriptWidget.java:49`, `res/xml/script_widget_config.xml`, `util/NotificationUtils.kt:137`, `engine/NodeBridgeProviderRegistry.kt` |
| 通知监听 / 短信 (I10) | `core/notification/NotificationListenerService.kt`, `service/NotificationService.kt`, `runtime/api/Events.java`, `engine/NodeEventObservers.kt` |
| WebView (I11) | `core/ui/attribute/WebViewAttributes.kt:36`, `ui/doc/DocumentationActivity`, HTML 预览路径 |
| inrt (D3) | `inrt/SplashActivity.kt:76,97-113` (仅申请 `WRITE_EXTERNAL_STORAGE`), `inrt/SettingsActivity.kt:52`, `inrt/theme/preference/{ManageAllFilesPermissionSwitchPreference,PostNotificationsPermissionSwitchPreference,DisplayOverOtherAppsPermissionSwitchPreference,ForegroundServiceSwitchPreference}.kt`, `res/xml/fragment_preferences_inrt.xml`, `inrt/launch/{AssetsProjectLauncher,GlobalProjectLauncher}.kt`, `apkbuilder/**`, manifest `:293` `requestLegacyExternalStorage` |

### 3.4 插件舰队现状

初始盘点 (2026-09-13): 42 个插件仓库全部 `TARGET_SDK_VERSION=36`, 除 Archive-Manager (`COMPILE_SDK_VERSION=37`) 外 `COMPILE_SDK_VERSION=36`; 41 个仓库使用 `autojs6-platform-versions` 1.8.0, Bun-Runtime 为 1.7.4 (且 `MIN_SDK_VERSION=33`). 属性文件不一定等于实际构建值: R8-Compiler 的 app / 两个 plugin-api 构建脚本硬编码 compileSdk 36, app 硬编码 targetSdk 36 / minSdk 24; Paddle v4 / v5 / v6 app 实际 minSdk 为 26. P5 必须核查消费点和产物. 初稿快照中 10 个仓库工作区有未提交改动 (Bun 18, Lua 30, MLKit-OCR 3, Paddle v3 40, v4 / v5 / v6 各 6, Rapid v3 45, Rapid v6 56, Yolo 36 个文件); 开始每仓工作时重新检查. 逐仓的进程模型, 权限暴露与阶段归属见附录 E.

---

## 4. 目标状态

- 宿主 app / inrt 两个 flavor, 仓内全部子项目, 42 个官方插件的 APK 均为 `compileSdkVersion='37'` 且 `targetSdkVersion='37'` (`aapt2 dump badging`), Runtime Kit 与用户生成的 APK 随 inrt 模板同为 37.
- 第 3.2 节 I1-I15 每一项都有明确结论 (已适配 / 验证无影响 / 记录为已知限制), 其中 I1-I5 在 `AVD_API_37` 上以 `DEVICE-37` 级证据通过, 且真机回归 (API 28 / 31 / 33 / 35) 无退化.
- 单元测试, lint (`lintVital*Release` 无 fatal), 16 KB 门禁, CI 全绿; changelog (宿主 + 每个插件) 与 README 已更新; JS 公开 API 若有变化, 文档 / d.ts / LSP 已同步.
- 本文附录 E 每一行都有提交 hash 与验证设备.

---

## 5. 阶段总览

| 阶段 | 主题 | 依赖 | 涉及仓库 | 状态 |
| --- | --- | --- | --- | --- |
| P0 | 基线与工具 | 无 | 宿主 | 完成 (2026-09-13): 覆盖开关, 分发拒绝, compat 工具, 有效 lint 基线 |
| P1 | 宿主 compileSdk 37 | P0 | 宿主 | 本地实现 / 构建 / 测试 / 冒烟完成; lint 零增量但有既有错误, 远程 CI 待验证 |
| P2 | 宿主 targetSdk 37 行为变更适配 | P0.2 (覆盖开关), P1 | 宿主 | 代码适配完成 (16 子项 83 项勾选); 剩余条目按 D20 标注为 RC-SMOKE / WATCH / CI, 不再门控 P3 |
| P3 | 宿主 targetSdk 37 切换 (app + inrt) | P1, 门禁 (D19) | 宿主, APK-Builder-Template (只读验证) | P3.1 完成 (2026-09-14, chore(sdk): raise targetSdk to 37 for app and inrt): 门禁在 AVD_API_37 (16 KB) 与 API 28 / 31 / 33 / 33 / 35 五台真机执行; P3.3 / P3.4 / P3.5 完成 (2026-09-15): AVD API 30 / 33 / 37.1 自动矩阵、Runtime Kit 5280 与三个打包示例, 插件 `ce6dfd9` 修复 `resources.arsc` 回归; 真机 RC-SMOKE #8 与 CI 推送验证 (P4) 保留 |
| P4 | 关联项目, 文档与 CI | P1 / P3 各自完成后 | Documentation, TypeScript-Declarations, Ace-Editor (LSP), Offline-Docs, VSCode-Ext (仅文档) | 进行中: P2.1 文档 / 声明 / LSP 已同步, 其余迁移说明与 CI 待完成 |
| P5 | 官方插件舰队 (42 仓) | P1 (工具链验证), P2.1 / P2.3 的方案, D21 批处理脚本 | 42 个插件仓库 | 2026-09-16: 42 / 42 仓 compile / target 37; 最后 9 仓适配、构建及 227 个用例覆盖完成; 实体机、跨仓完整业务及发布矩阵按各条目继续 |
| P6 | 验收与关闭 | P1-P5 | 全部 | 未开始 |

P2 可按影响矩阵分批推进: P2.6 的局域网 TLS 验证先满足 P2.1 权限前提; P2.16 的 inrt 验证复用 P2.1 / P2.2 等方案. P5 与 P2 / P3 可交错 (插件的 compileSdk 步骤不依赖宿主 targetSdk 切换), 但 P5 中涉及本地网络与动态加载的插件应复用 P2.1 / P2.3 落定的方案.

---

## P0: 基线与工具

目标: 固定事实, 让 P2 每个条目都能在 targetSdk 36 的代码上以 targetSdk 37 的行为验证.

### P0.1 探针与兼容清单 (完成 2026-09-13)

- [x] compileSdk 37 探针编译 (所列任务 0 错误, 新增 1 处弃用告警) 与 36 基线对照. (ANDROID_COMPILE: [探针记录](evidence/android-sdk-37-probe-20260913.md) 第 1 节; 历史摘要, 本次复核未重跑)
- [x] `AVD_API_37` 上 `dumpsys platform_compat` 全文入库, 提取 36 项 `enableSinceTargetSdk=37` 变更并逐项评估. (PLATFORM_CONFIG: [platform_compat 全文](evidence/android-sdk-37-platform-compat-api37-20260913.txt); DOCS: 附录 B, 行为语义未全部核验)
- [x] 初次官方资料收集已写入第 3 节与附录 B, 复核修正见 [执行条件与证据校正](evidence/android-sdk-37-review-20260913.md). (DOCS; 不代表所有 UNVERIFIED 项已关闭)

### P0.2 targetSdk 覆盖开关 (完成 2026-09-13)

- [x] `Versions.kt` 增加 `autojs.sdk.target.override`, 校验整数 / minSdk 至 max(compileSdk, 37), 并打印 debug 覆盖提示. 两个 target 37 debug APK 均已检查 badging 并安装; 正常 app / inrt 为 36 / 29. `SdkTargetOverrideGuard` 作为实际任务依赖拒绝 release / Runtime Kit / 模板分发, 覆盖缩写与聚合路径; 11 项参数 / 任务拒绝检查通过. (SOURCE; ANDROID_BUILD: [实施记录](evidence/android-sdk-37-p0-p1-20260913.md) 第 2 节)
- [x] [compat.sh](../../tools/sdk37/compat.sh) 与 [dump_compat.sh](../../tools/sdk37/dump_compat.sh) 保存原覆盖状态, 检查 debuggable 与整批 change, 每条 adb 命令只操作一个 change / package; 已在 API 37 对两个开关 enable / reset 并核对恢复, 非法 change 和 release 包拒绝通过. 音频 / 内存仅提供独立命令说明, 本次未修改设备级控制. (SOURCE; DEVICE-37: 实施记录第 2 节)
- [x] `-Pautojs.sdk.lint.verify=true` 启用 app / inrt vital analysis, 绕过模板分发跳过条件并令错误产生非零退出码; 实际运行 debug / 两个 release 报告. AGP 9.4 的 vital 输出为 text, 对比工具检查其诊断计数. (SOURCE; LINT: 实施记录第 3 节)
- [x] SDK 36 有效 lint 基线存档 `build/sdk37-lint-36/`, 包含命令日志、报告、版本与 lint model; 不新增禁用规则. debug 546 Error + 1 Fatal / 2437 Warning / 3 Hint, app / inrt release 各 1 Fatal. (LINT: 实施记录第 3 节)

P0 验收: app / inrt 覆盖包的 `targetSdkVersion` 为 37, 正常包仍为 36 / 29; release 覆盖被拒绝; 脚本在 AVD 上跑通并清理本次 override; 有效 lint 基线存在.

---

## P1: 宿主 compileSdk 37

目标: `COMPILE_SDK_VERSION=37`, 编译 / 测试 / 出包与运行回归通过, lint 无新增问题并显式记录既有问题, CI 获得独立远程证据; 宿主 targetSdk 保持 36 / inrt 保持 29. 本地实施证据见 [实施记录](evidence/android-sdk-37-p0-p1-20260913.md), 不将既有 lint 失败或未运行的 CI 标作通过.

- [x] `version.properties`: `COMPILE_SDK_VERSION=37`, 仓内构建脚本统一跟随; 宿主 / inrt 正常 target 仍为 36 / 29, 工具链与依赖版本保持. (SOURCE; ANDROID_BUILD: 实施记录第 1 / 4 节)
- [x] `SymbolsSettingsActivity` 和 AndroidTest 的 `NativeEditorRegressionSmokeTest` 使用 `showSoftInput(editText, 0)`, 保持已有焦点 / 调用时序. 实际符号设置页在 API 24 / 28 / 35 / 37 均验证输入框获得焦点、键盘显示、Back 隐藏. ([API 参考](https://developer.android.com/reference/android/view/inputmethod/InputMethodManager#SHOW_IMPLICIT); SOURCE; ANDROID_COMPILE; DEVICE: 实施记录第 5 节)
- [x] SDK 37 debug + app / inrt release lint 实际运行并存档 `build/sdk37-lint-37/`; [compare_lint.py](../../tools/sdk37/compare_lint.py) 对照 SDK 36 的 ID / 严重性 / 消息 / 文件及重复计数, 三份报告新增与减少均为零. 两轮 Gradle 均因既有错误退出 1, 不是 clean lint; 无新增 suppress / baseline / disabled issue. (LINT: 实施记录第 3 节)
- [x] 单元测试 3166 项: 3161 通过 / 5 跳过 / 0 失败; 其中 app 2533 通过 / 5 跳过. 对照 settings 核对 10 modules / 29 plugin-api / 1 plugins / 6 test-apps 均进入任务选择器, 21 项任务为 NO-SOURCE, 不算用例通过. AndroidTest Kotlin 编译通过, 含上述弃用修正后的重编译. (JVM; ANDROID_COMPILE: 实施记录第 4 节)
- [x] 正常 app debug / release 各 5 个 ABI / universal APK, inrt release 5 个及独立 inrt debug universal APK, 均为 compile 37 且 target 分别为 36 / 29; 5 份 Runtime Kit、根任务 `:assembleApkBuilderTemplateDistribution` 和 16 KB 门禁通过. 不在 Kit / 模板构建命令中直接追加 `assembleInrt*`: 现有脚本会因此关闭 ABI splits, 应通过 Kit 依赖构建 inrt release, debug 单独执行. (ANDROID_BUILD: 实施记录第 4 节)
- [x] API 28 / 35 / 37 debug 与 API 37 release 各通过 a11y 53 / 53, UI / overlay / 基础 MediaProjection; API 35 使用全屏授权通过. 该平板单应用选择本宿主后返回未授权, 保留为 P2.15 待定位观察项, 不推断为 SDK 升级引入. release 当前 `isMinifyEnabled=false`, 覆盖实际出包与运行路径, 无 R8 优化证据. (DEVICE; DEVICE-37: 实施记录第 5 节)
- [x] 本地 CI 入口修正并验证: editor gate 不再硬编码契约 VERSION=1, 验证正整数版本与 MIN=1 / MAX=VERSION; 当前契约为 5, 三项无效契约检查均被拒绝. CI 所列测试 / conformance assemble / Runtime Kit / 模板 / native 检查本地通过. (SOURCE; JVM; ANDROID_BUILD: 实施记录第 4 节)
- [ ] (CI) 远程 CI: 推送后的 `android.yml` 记录链接与 SDK 自动下载结果; 若 runner 无法获取平台再补显式 sdkmanager 安装. `release-runtime-kit.yml` 会上传 Release 资产并触发插件仓库, 其构建步骤已本地验证, 发布工作流留待正常发布时验证, 不为 P1 检查提前发布. (ANDROID_BUILD: 远程 CI / 正常发布链接)
- [x] changelog 按 D9 在 `improvement` 描述 compileSdk 37 (10 语言), gate 修正在 `fix`; 日期 2026/09/13, 已执行 `.python/generate_markdown.py` 生成 README / CHANGELOG. 本地提交 `chore(sdk): raise compileSdk to 37 (roadmap P1)`, 按显式文件与共享日志条目暂存. (DOCS)

P1 本地验收已记录; 整体关闭仍需远程 CI 证据, 发布工作流随正常发布补验. 既有 lint 错误清单持续可见, 不把零增量改写为全量 lint 通过. P2 的本地行为适配可使用已验证工具继续, 不提前切换持久 target.

---

## P2: 宿主 targetSdk 37 行为变更适配

目标: 在 targetSdk 仍为 36 的代码上完成全部适配, 每项用 P0.2 的覆盖开关或兼容开关在 `AVD_API_37` 上验证 "target 37 行为正确" 且真机 "target 36 行为不变". 条目按第 3.2 节影响矩阵编号.

### P2.1 本地网络权限 (I1)

- [x] Samsung SM-F776B / API 37 的宿主与 inrt 覆盖安装补证: 正常 target 36 / 29 为系统隐式 granted=true, 升至 target 37 为 false, 恢复正常 release 后又为 true; 未人为授予 / 撤销权限. target 37 的 HTTP / TLS 回环在测试前后均未获 LAN 授权时通过. 此项只覆盖权限状态与同设备 IPv4 回环, 权限 UI 和真实 LAN 仍在后续条目. (PHYSICAL; [D37 证据](evidence/android-sdk-37-q7-samsung-20260914.md))
- [x] 官方事实与 APK 核对: target <= 36 不声明也不申请新权限, 保留 INTERNET 的隐式 LAN 访问; target 37 包按 flavor 合并 `src/target37/AndroidManifest.xml`. 普通 host 36 / inrt 29 与覆盖 target 37 的 app / test APK 元数据及 SHA-256 均归档. 未使用 compat 强制开关. (VERIFIED; SOURCE; ANDROID_BUILD: [P2.1 证据](evidence/android-sdk-37-p21-20260913.md))
- [x] `LocalNetworkPermission`、抽屉、宿主设置与 inrt 设置接入标准运行时权限流程. 申请时机按已确认的 Q1=(a) / D12: 显式 VS Code 连接 / 开启服务才引导, 自动连接与脚本调用不主动弹窗. 复用 `runtime.requestPermissions(["access_local_network"])`, 保持异步 void API. (SOURCE: `d9465be13`; DEVICE-37)
- [x] HTTP 同步 / 异步、WebSocket、Node controlled fetch 在实际网络失败后附加条件式恢复提示并保留 cause; TLS 与主动取消不误判. 修复 Node 将 OkHttp 整次调用超时归类为取消的问题, 保留 TIMEOUT / ABORTED 各自错误码. 原生 Socket / Paho 保留原始异常, 文档提供显式申请和重试步骤. (JVM: 5/5; DEVICE-37: 网络 suite 与既有取消回归 1/1)
- [x] API 37 / 16 KB AVD: LAN TCP / UDP、入站 accept、HTTP、WebSocket、Paho CONNECT、VS Code 双向 hello、Node fetch 及同 profile 的 IPv4 / IPv6 loopback. 拒绝组 9 通过 / 2 个成功场景跳过, 授权恢复组 11/11. 通过 emulator redir 验证入站接口, 不以 adb tunnel 冒充 LAN; 未修改防火墙. (DEVICE-37: [矩阵与边界](evidence/android-sdk-37-p21-20260913.md#构建与用例))
- [x] host 拒绝后重试、连续拒绝后的设置入口、已有 Nearby Devices 授权、inrt 设置页重试, 四轮 UI 各 1/1. target 36 -> 37 覆盖安装且没有 Nearby 授权时, 新权限为未授予; 请求授权后恢复访问. 不把无弹窗的权限组复用与覆盖升级视为同一条件. (DEVICE-37)
- [x] 旧系统回归: API 24 上 normal host 36 / inrt 29 与 target 37 覆盖 host / inrt 各 11/11, 新权限门槛未误拦. API 37 上 normal host 36 两轮各 11/11. inrt 37 的 API 37 权限识别 / HTTP / loopback 拒绝与授权两轮各 3/3, 另有真实设置页重试测试. (DEVICE; DEVICE-37)
- [x] 文档 / d.ts / Offline-Docs / Ace LSP 已同步并递增各仓版本; 66 个手工声明文件与 3 个生成文件一致, 通用权限数组正反例、离线文档 APK 门禁与 LSP runtime 校验通过. 宿主及相关插件 10 语言 changelog 已生成, 11 个字符串文件检查通过. SDK 37 暴露的 d.ts 导出器平台目录拼接缺陷另仓修复. (DOCS; ANDROID_BUILD: [关联提交](evidence/android-sdk-37-p21-20260913.md#关联仓库))
- [ ] (WATCH) Android `org.eclipse.paho.android.service.MqttService` 与完整 broker 的 publish / subscribe / reconnect / lifecycle, 及拒绝后的恢复体验. 已通过的是 Paho MqttClient CONNECT 最小握手, 不据此关闭本项. (DEVICE-37)
- [ ] (WATCH) Android 17 真机 Wi-Fi / Ethernet / VPN、Wi-Fi Direct / hotspot、实际 IPv6 LAN 与 mDNS / multicast、公网回归. AVD NAT 结果不能替代这些网络拓扑. (DEVICE-37)
- [ ] (WATCH) 跨 profile loopback 与独立插件 UID 授权隔离; 系统选择器的有限授权不替代任意脚本 socket / 服务器权限. (DEVICE-37; P5)
- [ ] (WATCH) inrt 最终 target 37 的完整网络 suite 及最终打包 APK 回归, 与 P3.3 共同验收. (DEVICE-37)

### P2.2 后台音频 (I2)

- [x] 默认状态下完成 target 36 / 37 的前台, 可见启动 FGS 后切后台, 无 FGS 后台, 精确闹钟接收器普通媒体 / 闹钟用途对照, 以及 target 37 同一进程恢复和 Google TTS, 共 12 组有效记录. 11 组符合最初联合断言; target 37 普通媒体闹钟入口出现 "焦点失败 / 系统静音, 但媒体音量改变" 的镜像例外, 保留失败断言与 `AudioHardening` 证据. 不合并播放 / 焦点 / 音量为一个资格判断. (DEVICE-37: [矩阵与边界](evidence/android-sdk-37-p22-20260913.md); [结构化结果](evidence/android-sdk-37-p22-20260913-results.json))
- [x] 2026-09-13 按原推荐方案 (a) 完成可见界面启动 / 重启服务后重试脚本的恢复指引和无人值守播放限制. target 37 同一 PID 的服务 WIU 从 `DENIED` 恢复为 `PROC_STATE_TOP`, 焦点由 0 恢复为 1. 不通过服务存活, 音量读回, 播放器进度或额外抢占焦点推断播放资格. TTS 记录实际 Google 引擎 UID / PID, 文件合成不增加宿主播放资格拦截. 此项保留历史证据; 2026-09-14 的 Q3=(b) 已替代最终方案. (SOURCE; DEVICE-37)
- [x] 按 Q3=(b) / D14 引入 Media3 1.11.0 `MediaSessionService`, 接入宿主 / inrt 的脚本播放 / 暂停 / 恢复 / 定位, 媒体焦点与通知, 请求接管和退出清理. API 24 / 37、正常 target 36 / 29 与 debug 双 37 共 66/66, 含 Rhino, 平台媒体控制和真实暂时焦点丢失 / 恢复. UI 线程阻塞限制、工作线程同步准备、打包权限和单一所有权已同步 API / 离线文档. Node 仍只开放原音量桥, 不宣称有播放入口. (SOURCE; DEVICE-37; DEVICE-24; DOCS: [实现和边界](evidence/android-sdk-37-media3-20260914.md); [结构化结果](evidence/android-sdk-37-media3-results-20260914.json))
- [ ] (RC-SMOKE) 对 Media3 最终实现补验无 instrumentation 的后台启动拒绝与可见恢复, 真机锁屏 / 路由 / 可听性, 最终用户打包项目, 实际 TTS 与开机 / 定时触发. 不以服务存在、播放器进度或上述可见 Activity 自动化替代播放资格与可听性. (DEVICE-37; PHYSICAL; P2.16)
- [x] 源码复核定时 / 开机入口: Alarm / WorkManager / Job 调度链与宿主 / inrt 开机接收器不保证已经启动音频 FGS; `settings.setEnabled('foreground_service', true)` 仅写偏好, 不直接启动服务. 已写入用户 API 文档. (SOURCE)
- [ ] (WATCH) 完整端到端验收: Rhino `media.playMusic` / TTS, 全部实际定时后端, 宿主与 inrt 的开机链, TTS 后台可听性, 独立音频插件, 以及 API 35 真机与 `FGS_BOOT_COMPLETED_RESTRICTIONS` / `FGS_SAW_RESTRICTIONS` 的组合. 本轮开机探针启动 ANR, 不计入完成; 原生探针不代替脚本与打包应用验收. (DEVICE-37; DEVICE: API 35 真机)
- [ ] (WATCH) 在支持命令的专用镜像中完成 `cmd audio set-enable-hardening throw` 验证. 本轮命令 exit 0 但 `mHardeningOverride` 始终为 0, 异常模式断言失败, 不把该轮标为 throw 验收. 探针已加入覆盖状态检查. `enable` / `throw` 会加严所有 target 并覆盖闹钟例外; `disable` 不等于恢复默认. 已关闭本轮只读 AVD. (DEVICE-37)
- [x] 前台服务帮助的 11 份资源和当前版本 10 语言 `improvement`, README / CHANGELOG, Media / Device / TTS / Settings 文档已补齐; 文档同步和最终构建门禁见实施记录. 无 API 签名变化, 无需 d.ts 更新. (DOCS)

### P2.3 动态加载只读 (I3)

- [x] Samsung SM-F776B / API 37 / ARM64 的 `AndroidClassLoaderRawDexDeviceTest` 补验 4/4: 宿主正常 target 36 / 覆盖 37, inrt 正常 29 / 覆盖 37, 两次加载均保持原始 DEX / 缓存字节和只读 staging. 本轮不包含原生插件 `.so` 或最终用户 APK. (PHYSICAL; [D37 证据](evidence/android-sdk-37-q7-samsung-20260914.md))
- [x] OpenCV / ImageQuant stager 强制验证只读设置的返回值与 `canWrite()`: 临时文件先打开写入句柄, 再设只读并写入, 发布后再次校验; 命中旧缓存时也修复并校验. 失败复用各自 `NATIVE_LIBRARY_EXTRACTION_FAILED`, 不再交给 `System.load` 才发现. 原有代码已尝试去除写权限, 本项修复的是忽略失败结果的缺口. (SOURCE: `a784273d2`; JVM: 38/38; DEVICE-37: target 36 + `THROW_ERROR_FOR_WRITABLE_DCL`, 首次及旧缓存两轮各 3/3, target 37 默认行为 3/3; DEVICE: API 24 3/3)
- [x] 其余加载点复核: `app/src/main` 的 `System.load` 仅上述两处; `AndroidClassLoader` 有 3 个 `DexClassLoader` 构造点; Ace Editor 读取已安装的 APK; Node / Lua / QuickJS / Bun / Python 宿主侧为 Binder 调用, 未发现同类原生库 staging. 仓内 Paddle `OpenCLGuard` 另有一个 `System.load`, 仅尝试固定系统 / vendor / odm / apex 库路径. 插件进程内部仍由 P5 逐仓核验. (SOURCE: [调用点与边界](evidence/android-sdk-37-p23-20260913.md#调用点复核))
- [x] 实际脚本 `runtime.loadDex` / `runtime.loadJar` 在 API 37 target 36 + `THROW_ERROR_FOR_WRITABLE_DCL` 与 target 37 默认行为下通过; `tools/sdk37/smoke-dynamic-loading.js` 补充同一进程首次加载 / 缓存复用探针. (DEVICE-37: [测试矩阵](evidence/android-sdk-37-p23-20260913.md#设备验证))
- [x] 旧系统回归发现并修复 API 24 / 25 原始 DEX 输入与 ART 优化输出重合: 3 个构造点统一使用独立 `optimized` 子目录, API 26+ 保持原路径. API 24 / 37 的 `AndroidClassLoaderRawDexDeviceTest` 各 1/1, 检查两次加载后的原始 / 缓存字节和只读状态; API 24 实际 DEX / JAR 两轮调用通过. Java 8 JAR 在旧系统内置编译器中的既有限制单列于证据, 不作为加载适配通过的推论. (SOURCE; ANDROID_BUILD; DEVICE; DEVICE-37)
- [x] 两项 `fix` 日志分别写入当前版本 10 语言 JSON, 已运行 `.python/generate_markdown.py` 更新 README / CHANGELOG. 无 JS API 签名或权限模型变化, 无需同步文档 / d.ts 关联仓库. (DOCS: [P2.3 实施记录](evidence/android-sdk-37-p23-20260913.md))

### P2.4 Looper / 线程 (I4)

- [x] Samsung SM-F776B / API 37 / ARM64 的基础平台探针补验 8/8: 宿主 / inrt 的正常与覆盖 target 37 四组, 消息派发后的原生 / ThreadCompat 中断、显式清除、线程池 start 覆写与 run、普通直接 start 均符合预期. 未替代完整 Rhino / 无障碍 / OEM 恢复矩阵. (PHYSICAL; [D37 证据](evidence/android-sdk-37-q7-samsung-20260914.md))
- [x] API 37 / target 36 默认、三个开关分别启用、组合启用, 以及实际 target 37 默认状态下, 每组 14/14 通过. 其中 12 个真实 Rhino 脚本覆盖定时器、线程池、sleep、exit、events、UI、悬浮窗、阻塞查找取消和 MainThreadProxy 中断; 组合 / target 37 持续 interval 各运行 2 分钟并正常退出. API 24 同组 14/14 通过. 原样执行 `sample/accessibility/smoke-legacy-api.js`, API 37 target 36 默认 / 组合、target 37 默认和 API 24 各 53/53. (DEVICE; DEVICE-37: [设备矩阵](evidence/android-sdk-37-p24-20260913.md#设备矩阵))
- [x] 按当前声明与继承关系复查 start 方法, 修正初始 "8 个文件" 估计: ThreadCompat、两层 TimerThread、FlowWorkerPool.WorkerThread 均未覆写 start, 初始化位于构造或 run; MainThreadProxy 是包装类, Media 覆写的是 MediaPlayer.start, 录制 / 会话 / 服务入口不是 Thread.start. 平台探针确认线程池 start/run 为 0/1 时真实 threads.pool 仍正常工作. (SOURCE; DEVICE-37: [复核表](evidence/android-sdk-37-p24-20260913.md#start-覆写与非-sdk-接口复核))
- [x] 修复基线复现的脚本不退出问题: 取消的远期回调留下历史最大时间, 导致空队列无法再次检查退出. Timer 改为登记实际排队 / 执行中的回调, 取消或完成时唤醒检查, 线程退出清理 Timer, UI / 直接 postDelayed 同样纳入. 取消 60 秒回调由 20 秒内无法结束变为正常即时结束; JVM 6/6, app / instrumentation 两种 target 与正常 inrt debug 构建通过, 16 KB 门禁通过. (SOURCE; JVM; ANDROID_BUILD; DEVICE; DEVICE-37)
- [x] 附录 F 记录新 MessageQueue 的 mMessages 与 target 37 static final 反射限制及生效条件. 已检查关联 util.java / threads 文档, 无受影响示例或 JS API 签名需要同步; P4 统一发布迁移提示. 当前 6.8.0 的 fix 日志已更新 10 语言并生成 README / CHANGELOG. (DOCS: [P2.4 证据](evidence/android-sdk-37-p24-20260913.md), [复跑工具](../../tools/sdk37/README.md#looper-and-thread-compatibility))

### P2.5 后台活动启动 (I5)

- [x] 逐调用点区分 PendingIntent 的创建方、发送方与目标类型. 安装状态回调现使用 mutable Broadcast PendingIntent; 通知入口与保存系统确认 Intent 的 token 使用 immutable Activity PendingIntent. 接收器不启动 Activity, 用户打开私有状态页后以可见发送方 options 继续确认. API 36+ 使用 `ALLOW_IF_VISIBLE`, API 34 / 35 使用该版本支持的 `ALLOWED`, 更旧版本不传 options. 移除旧安装回调的 creator `ALLOW_ALWAYS` / `ALLOWED` 构造器, 保留已经发出的旧 Activity 回调的接收兼容. (SOURCE; [原调用点分析](evidence/android-sdk-37-p25-20260913.md), [恢复实现](evidence/android-sdk-37-p25-recovery-20260913.md))
- [x] `ShortcutUtils` / `external/shortcut/ShortcutManager` 的成功回调是广播且不启动 Activity; 取色器通过 AndroidX ActivityResult 转交 options. `DrawerFragment` 使用发送方适配器: API 36+ `ALLOW_IF_VISIBLE`, API 34 / 35 `ALLOWED`, 更旧系统不传 options. 未扩大安装器的创建方授权或其他广播 / Service token 的权限. (SOURCE)
- [x] API 36+ debug 构建基于既有 VmPolicy 添加 `detectBlockedBackgroundActivityLaunch()` 与日志诊断; 使用实际宿主 UID 和独立插件 UID 探针, 不以 send 返回或脚本返回代替目标创建证据. (SOURCE; DEVICE-37)
- [x] 对照默认 / enable / disable 的 `COVER_INTENT_SENDER`, 分开验证旧 `sendIntent` 重载, API 37 options 重载与 ActivityResult 路径. target 37 的旧重载默认拦截 / disable 放行; 显式 enable 时可见 ActivityResult 回传成功, 后台 options 发送负例被拦截. (DEVICE-37; [证据](evidence/android-sdk-37-p25-20260913.md))
- [x] 实际 Rhino `app.launch` / `app.startActivity` 后台负例被拦截, 授予 SYSTEM_ALERT_WINDOW 后目标创建且系统记录 `BAL_ALLOW_SAW_PERMISSION`. `app.launch` 的 true 不代表窗口已显示. 真实抽屉开关已启动插件 `PickerRequestActivity`, 到达其权限说明页后取消. 前台安装会话已完成系统确认并成功安装实际取色器插件. (DEVICE-37)
- [x] 无 BAL 资格时的后台安装确认恢复: 私有广播 + AtomicFile 会话记录 + 系统持有的确认 token, 提供通知和返回前台的继续入口. API 37 已覆盖宿主 target 36 / 37 与 inrt target 29, 通知拒绝, 普通进程重建, 两个 session 分别处理, 系统取消, 签名冲突和结果页旋转. force-stop 的 token 失效显示重新安装提示并 abandon 旧会话. API 24 正式安装路径通过, 确认页返回至最终回调期间显示等待状态. 11 个真实会话包含 8 次成功, 1 次取消, 1 次签名冲突及 1 次过期回退; 其中一次 importance 100 提交仅作为多会话辅助观测. (SOURCE; JVM; ANDROID_BUILD; DEVICE-37; DEVICE-24; [证据与机器记录](evidence/android-sdk-37-p25-recovery-20260913.md))
- [ ] (RC-SMOKE) 完整 Launcher 固定快捷方式, 小组件 / 其他通知点击, 真实取色与颜色回传, a11y 启动例外, inrt 其他专属设备用例及旧系统真机回归. 安装通知和 inrt 安装恢复已单独验收, 不代替其他入口; 广播 fixture 收到回调不代替完整固定快捷方式流程; BAL 的 SYSTEM_ALERT_WINDOW 权限例外不代替 FGS 的可见悬浮窗条件. (DEVICE-37; DEVICE-LEGACY)
- [x] 本机 API 37.0 revision 6 的 ASM 有效对照 12/12: 5 次系统拦截, 7 次实际 landing 创建. 上轮 fixture 缺少应用级 `allowCrossUidActivitySwitchFromBelow=false` 保护声明, Activity setter 的 false 不能代替它. 宿主 target 37 默认 / enable 拦截, disable 放行; 顶部包 disable 与 Activity true 例外分别放行. 宿主 target 36 默认放行 / enable 拦截, inrt target 29 默认放行, target 37 默认拦截 / disable 放行. 不改全局开关, 不把 `overridable` 解释为 disabled; 保持生产 manifest, 不添加通用例外. (PLATFORM_CONFIG; DEVICE-37; [ASM 证据](evidence/android-sdk-37-p25-asm-20260913.md))
- [ ] (WATCH) 在最终发布镜像 / API 37 真机上复跑上述有效 ASM 场景, 并结合真实入口核对新任务、任务清理与多窗口. 本机特定指纹的同任务对照不代替此项, 也不代替其他 P2.5 / P3 入口门禁. (DEVICE-37; P5)
- [x] changelog `improvement` 与安装恢复 `fix`, 10 种语言及生成文档同步; 安装提示覆盖 11 份资源, 默认资源与英语一致. 可见 ActivityResult 不传 options 的对照也成功, 因此不把授权策略完善描述为已经复现并修复的取色器故障. (DOCS; DEVICE-37)

### P2.6 网络安全配置 (I6)

- [x] 按 Q7=(b) / D18 将 Manifest 的明文开关迁至 NSC, 修复 release 自定义 HTTP 端点被默认策略拒绝的问题, 保留 GitHub 资源域 HTTPS 限制. API 24 / 37 AVD 的宿主 / inrt debug、release 和 target 37 debug 覆盖包共 HTTP 40/40、TLS 回归 40/40; 12 个应用 APK 配置与 CA 边界、16 个应用 / 测试 APK 签名核验通过. Samsung D37 恢复连接后, 同批产物补齐真机 HTTP 24/24、TLS 26/26; 实际公网、企业代理、ECH 与最终用户 APK 等剩余门禁继续保留到 O02. (SOURCE: `494a048df`; APK; DEVICE-24; DEVICE-37; PHYSICAL; [Q7 证据](evidence/android-sdk-37-q7-20260914.md); [D37 补验](evidence/android-sdk-37-q7-samsung-20260914.md))
- [x] 完成受控 TLS 夹具与 target 36 / 37 的 CT / ECH 独立开关对照. 未知 CA 拒绝、指定 debug CA 信任、主机名不匹配和强制 CT 缺 SCT 拒绝分别验证; target 37 的 LNP 前提单独满足. API 37 的 6 组各 6/6, API 24 回归 6/6 (旧系统不宣称支持 CT / ECH), 见 [P2.6 证据](evidence/android-sdk-37-p26-20260913.md). (SOURCE; DEVICE-37; DEVICE-24)
- [x] 修复真实 Rhino 请求中 `isInsecure` / `insecure` 对共享客户端的污染. 同步、传统回调和 Promise 请求后, 后续默认或显式 false 请求恢复校验, 可信域与主机名检查保留. `timeout` / `client` 普通配置继续共享, 不擅自重置脚本显式配置的自定义 TrustManager. (DEVICE-37; DEVICE-24)
- [x] 生产 NSC 保持默认 CT / ECH, 不添加全局关闭或新域例外; `githubusercontent.com` 资源 CA 的 CT 策略查询为 false. debug 测试 CA 不进入实际 appRelease APK. 当前 OkHttp 4.12.0 夹具连接无 ECH 扩展且可见 SNI, 不凭策略值宣称 ECH 生效. D13 记录 Q2=(a) 的确认与落实范围. (SOURCE; APK; DEVICE-37)
- [x] 公共 HTTP 文档区分证书信任、CT、ECH、LNP, 说明 `isInsecure` 的请求范围与旧行为; 已生成 Documentation 与 Offline Docs, 更新双份 provenance 及 10 语言 changelog. d.ts 的两个 boolean 签名无变化, 无需生成 / 发布声明. ([官方 NSC 文档](https://developer.android.com/privacy-and-security/security-config); DOCS)
- [ ] (WATCH) 对实际公网系统 CA 端点、`githubusercontent.com` 的实际验证链、企业 TLS 代理 / 私有域补证; 使用支持 ECH 的真实网络库与服务端记录协商, 明确与 GREASE 的区别. 需要目标地址、公开 CA 链 / 代理配置和 D37 网络环境, 对应实体机 O02. 当前夹具和策略查询不能替代此项. (DEVICE-37; PHYSICAL)

### P2.7 Parcel 与插件 IPC (I7)

- [x] 从实际 API 37 framework 字节码核实 `PARCEL_HARDENING` (416031865) 的长度前缀检查及平台 feature flag 条件. 在宿主与私有独立接收进程中, 故意少读一个字段的 Parcelable 于 target 36 disable 被接受、enable 抛 BadParcelableException、target 37 默认抛同类异常; 接收进程 PID 与 target 均有断言. 不把兼容配置存在等同于行为生效. (PLATFORM_CONFIG; DEVICE-37: [门控与负例](evidence/android-sdk-37-p27-20260913.md#1-门控与负例))
- [x] `ExecutionConfig` 现有六字段及读取边界、nullable / 完整 `PluginInfo` 与嵌套能力值、Tasker JSON 和 Lua 快捷方式 extras 均跨进程往返完整. `InstalledPluginRepository` 的 package-manager snapshot 与完整发现覆盖 11 个插件, 无 bindError, 日志 `info=true` 且版本与所装 APK 一致. 不扩展生产 wire 格式或修改冻结兼容包. (SOURCE; DEVICE-37)
- [x] OpenCV, Image-Quantization, Paddle v4 mobile, Rapid v6, MLKit-Barcode, Yolo, Node, Lua, QuickJS, Python, MCP-Server 各一条真实最短链路通过. 宿主 target 36 + enable 与 target 37 默认分别为 instrumentation 35/35、OCR 2/2; 插件仍 target 36, 单独 enable. 最终 target 36 disable 的 wire / QuickJS / Python 为 11/11. 共 81 次方法执行含重复负例, 去重为 15 个方法, 另有 4 次 OCR. (DEVICE-37: [结果矩阵](evidence/android-sdk-37-p27-20260913.md#3-有效结果矩阵))
- [x] 所测路径未发现需要修改生产序列化代码的 SDK 37 回归. 修正 QuickJS 控制台匹配和 Python 结果类型测试夹具; 旧 Python R2 诊断在 enable / disable 均失败, 与当前协议入口不匹配的源码分析及维护事项保留于证据 / 附录 F, 不记作已通过. 新增严格复跑工具与机器结果; 正常 / target 37 debug 构建、Python 工具测试 8/8、release 合并 manifest 的 debug 服务隔离检查通过. (SOURCE; ANDROID_BUILD; DOCS; `a6d037516`; [工具](../../tools/sdk37/README.md#parcel-and-plugin-ipc))
- [ ] (RC-SMOKE) 按实体机 [IPC01](android-sdk-37-physical-device-tests.md#43-本地网络资料隔离与-tls) 完成真实 Tasker 编辑 / 保存 / 触发与桌面 JS / Lua 快捷方式创建 / 点击, 含旋转和进程重建; 其它 ABI / OEM、inrt 成品及全部插件 target 37 发布验证仍按 P3 / P5 执行. 本轮 x86_64 AVD 与 extras 往返不代替这些入口或真实 16 KB 内核验证. (PHYSICAL; P3; P5)

### P2.8 存储与文件管理器 (I8)

- [x] 从实际 API 37 MediaProvider / ContactsProvider 和 framework-mediaprovider 字节码核实三个开关. 卷过滤只改变 MediaStore 卷名枚举, 联系人目录权限按目录提供方判断, 系统媒体回收受调用方 target > 36 与 provider compat 共同控制. 已纠正 I8、附录 B 与 P5 存储插件范围. (PLATFORM_CONFIG; SOURCE: [作用范围](evidence/android-sdk-37-p28-20260913.md#1-三个开关的实际作用范围))
- [x] target 36 默认及三个宿主开关分别独立 enable, target 37 默认与宿主 TRASH disable, 均完成九项真实 Rhino / 生产存储路径断言: 文件读写 / Explorer / 主卷与模拟 SD 卡 / 版本历史 / 应用回收站 / 图片保存扫描 / MediaStore trash-restore / 联系人权限基线 / 所有文件访问入口. 36 与 37 的媒体回收路径差异有实际 URI / 文件记录; 普通联系人查询不冒充远程目录门控命中. 未发现需修改生产存储代码的 SDK 37 回归. (DEVICE-37; SOURCE; DOCS)
- [x] target 36 / 37 均完成系统文档 / 相册选择和取消的完整 UI 回执, 文档实际字节与持久读权限、相册唯一像素内容均核验; Settings 关闭 / 重新开启后分别验证生产权限助手状态与真实页面. 12 组 84 次存储 / UI 断言执行通过, 含重复对照, 失败探索单列. (DEVICE-37; [机器结果](evidence/android-sdk-37-p28-results-20260913.json))
- [ ] (WATCH) 实体机 S01-S03: 不可靠 USB / OTG、拔出 / 重插 / 只读切换、OEM 选择器与旧 API / inrt 成品回归. AVD 普通模拟 SD 卡不证明 `/mnt/` 卷过滤或外设生命周期通过. (PHYSICAL; P3; P5)

### P2.9 小组件与通知 (I9)

- [x] 修复 `ScriptWidgets` 正则只捕获多位 ID 最后一位而误删路径 / requestCode 的既有缺陷. 修复前实际 widget 38 / 39 被解析为 8 / 9 并丢失映射; 修复后 target 36 / 37 的系统注册、批量更新、过期清理、实际 PendingIntent 点击和换脚本后点击均通过. 十语言 fix 日志与生成文件已同步. (SOURCE; DEVICE-37; ANDROID_BUILD; `6ab016582`; [证据](evidence/android-sdk-37-p29-20260913.md))
- [x] 定位 SystemUI `CHECK_SIZE_OF_INFLATED_CUSTOM_VIEWS`: 按发送应用 UID 检查自定义视图中的 ImageView 内存. 标准 `notice` / BigText、真实 AppForegroundService、小型自定义视图与超限负例在 target 36 / 37 的默认及相反 compat 状态下均符合预期, 日志匹配本次通知 ID. `notice()` 返回和记录 active 不代表自定义内容显示成功. 小组件与通知合计 6 组 26 次行断言执行通过, 含重复对照. (DEVICE-37; PLATFORM_CONFIG; [机器结果](evidence/android-sdk-37-p29-results-20260913.json))
- [x] 审计全部内建通知构建点, 均使用标准模板; CrashHandler 打开报告 Activity, 没有独立的崩溃通知入口. `notice(builder, options)` 支持自定义视图, 已补充图片缩放、逐 ImageView 累计、标准内容与异步渲染失败的说明, 不硬编码当前镜像阈值或关闭兼容开关. (SOURCE; DOCS: [通知用法](evidence/android-sdk-37-p29-20260913.md#4-生产通知构建点与脚本用法))
- [ ] (WATCH) 用更新的 API 37 镜像 / 真机复核小组件 Icon 上限: 官方称 target >= 37, 当前 CE2A.260420.019 的字节码与实际日志仅在 target > 37 抛错, 36 / 37 仍警告并接受. 原有 Bitmap 超限负例单独验证, 不用其代替 Icon 拒绝分支. (DEVICE-37; PHYSICAL)
- [ ] (WATCH) 实际 launcher 添加 / 进程重建 / 删除, OEM SystemUI / 方向 / 密度回归, 以及 Python 长任务、Node / Python 录音等全部业务通知触发链路. 本轮标准模板审计和代表路径不关闭完整业务生命周期验收. (PHYSICAL; P2.2; P3; P5)

### P2.10 短信 / 通知监听 (I10)

- [x] 在独占 API 37 AVD 注入虚构普通短信 / 普通 OTP / WebOTP, 用真实提供者分类 0 / 1 / 513 确认入库, 对照宿主 UID 的 `content://sms` 查询与 `SMS_RECEIVED`. target 36 default / enable、target 37 default / disable 共 24 项即时比较符合预期; 实际镜像代码另核对时间阈值与豁免分支. [实施记录与机器结果](evidence/android-sdk-37-p210-20260914.md). (DEVICE-37; SOURCE; DOCS)
- [ ] (WATCH) 定位 `events.observeNotification` 后续 OTP 脱敏更新缺失. 首个 target 36 默认正式组有普通通知与两次脱敏更新, 后续 target 36 / 37 的普通通知仍到达而脱敏更新缺失; 系统通知记录有 `mSensitiveContent=true`. 完整标准四组仅一组通过, 不把查询 / 广播通过或系统发布通知当成监听器通过, 不预先归因于 target 37 或平台限制. (DEVICE-37; DOCS)
- [ ] (WATCH) 真实等待三小时验证查询恢复与延迟广播释放, 再覆盖 SMS Retriever / 豁免身份、后台 / Doze 和重启; 不用修改时钟或短信时间戳替代. API 37 真机另复核通知助手与 OEM 差异. (DEVICE-37; PHYSICAL)

### P2.11 WebView (I11)

- [x] API 37 / WebView 145.0.7632.218: 宿主 target 36 默认 / enable、target 37 默认 / disable 各完成 `ui.webview` 默认 / 自定义 / 后缀 / reset 及真实文档两页, 24/24; 独立 HTML Previewer target 36 自身开关与宿主开关交叉对照 4/4. 核对 HTTP / WebSettings / DOM 的 UA、内容、尺寸、前台窗口和截图, 8 张代表截图人工复核. [实施记录与机器结果](evidence/android-sdk-37-p211-20260913.md). (DEVICE-37; SOURCE; DOCS)
- [x] 自定义 UA 保持原值, 给缩减后的默认 UA 追加后缀不会恢复完整设备信息. 本机 HTTPS 文档来源在默认 UA 下能通过 Client Hints 取得 OS / 型号 / 完整 WebView 版本; HTTP 夹具的 null 不能用来判断 provider 是否支持. 字段可用性按真实 provider 判断, 不保证所有设备返回完整值. ([官方 WebView 说明](https://android-developers.googleblog.com/2024/12/user-agent-reduction-on-android-webview.html), WebView 116+ 且使用默认 UA; DEVICE-37; DOCS)
- [x] 修复 `ui.layout` 以 Activity decorView 为 parent 时使用 DecorContext, 导致 `ui.webview` 无法解析 Activity / 打开生产文件选择器的问题. 保持显式 context 优先及普通子布局的父主题继承; 调试器取得 null Activity 与 DecorContext 实值, 修复后四组共 12 项 context 断言通过. `4d8564d98`, [修复证据](evidence/android-sdk-37-p211-files-20260914.md). (SOURCE; DEVICE-37)
- [x] `ENABLE_FILE_SYSTEM_ACCESS` 的 target 36 default / enable、target 37 default / disable 共 24 场景符合预期: HTML 选择 / 取消、File System Access 打开 / 取消, 开启时保存 / 目录取消共 20 次真实 DocumentsUI 往返; 关闭时保存 / 目录另有 4 次原生回调前拒绝. 核对 mode、permissionMode、Intent、返回读写授权与网页文件内容, 持久授权前后为空. [机器结果与截图](evidence/android-sdk-37-p211-files-20260914.md). (DEVICE-37; DOCS)
- [ ] (RC-SMOKE) 实际保存 / 写入、目录选择 / 遍历、浏览器 handle 跨重启持久化, 文档 EWebView 的图片选择专属路径. 实体机输入、复杂字体与文件交互继续执行 [UI04](android-sdk-37-physical-device-tests.md#46-存储输入webview-与其他系统能力); P5 仍需 HTML 插件自身 target 37 的最终包回归.

### P2.12 大屏, 输入法与配置变化 (I12)

- [x] 修复 `ScriptExecuteActivity` 缺少 `screenLayout` / `smallestScreenSize` 导致分屏时销毁脚本的问题. 无调试器复现与 IDEA 日志点取得 `changingConfigurations=true`, mask 2432, 新实例 execution=null; manifest 补齐两项后保留脚本与视图树. `38522f5c5`, [根因、APK 清单与回归](evidence/android-sdk-37-p212-20260914.md). (SOURCE; DEVICE-37)
- [x] API 37 的 target 36 / 37 默认态, 手机 sw411dp / 平板 sw800dp 共 4 组 UI 脚本通过: 手机响应 `setRequestedOrientation(LANDSCAPE)`, 平板忽略, 旋转与实际分屏往返保留同一 Activity 和输入文本; IME 显示 / Back 收起与配置回调均留证. 未使用旧豁免或 compat override, 不算独立开关正反测试. [机器记录](evidence/android-sdk-37-p212-results-20260914.json). (DEVICE-37)
- [x] 记录 Main / 原生 Edit / Ace Edit / Log 在两个 target、两种尺寸下的 16 组生命周期、状态、IME 与 viewport 基线观察. 原生 / Ace 平板光标归零, 主页分屏搜索未恢复, 手机横屏编辑器 viewport 为 0, 日志输入被 IME 遮挡. 后续纠正 Ace 的 dirty 解释: 引擎 changed=false, 宿主 shouldSaveDraft() 仍为 true, 并未丢失需要保存的标记. [历史结果](evidence/android-sdk-37-p212-20260914.md#3-target-36--37-的实际矩阵), [后续记录](evidence/android-sdk-37-p212-followup-20260914.md). (SOURCE; DEVICE-37)
- [x] 核对 "更少 Activity 重建" 的具体范围: keyboard / keyboardHidden / navigation / touchscreen / colorMode, 以及进入 / 离开 desk 的 uiMode; 非所有屏幕尺寸变化. IME 默认不恢复限定于配置变化导致 Activity 重建, 本轮 Log 旋转后消失而 Script / Edit 自行处理旋转时仍可见. [官方配置说明](https://developer.android.com/guide/topics/resources/runtime-changes), [IME 官方说明](https://developer.android.com/about/versions/17/behavior-changes-all). (DOCS; SOURCE; DEVICE-37)
- [x] 修复 Main 搜索恢复、原生 / Ace 光标与选区恢复、无文件路径大草稿丢失、横屏 IME 挤空正文、Log 输入及发送按钮遮挡. 生产提交 `23cc3a03e`, 探针 `e73f5c4f8`; target 36 / 37 的五入口矩阵 20/20, clean / dirty 非空选区 8/8, 409600 字符磁盘草稿 4/4, 合计 32/32 通过. Main 的主动退出搜索与 IME hide 分开验证; Ace 1.1.18 的运行回执及当前 1.3.0 契约源码核对不替代 P5.4 最终插件包验收. [修复与范围](evidence/android-sdk-37-p212-followup-20260914.md), [机器记录](evidence/android-sdk-37-p212-followup-results-20260914.json). (SOURCE; DEVICE-37)
- [ ] (RC-SMOKE) 补齐悬浮窗 / 悬浮控制台、拖动分屏分隔条 / 自由窗口、物理键盘、触摸板捕获、IME 组合输入、返回手势与边到边布局. 在 Xiaomi 23046RP50C API 35 平板及 API 37 真机执行 [UI01-UI04](android-sdk-37-physical-device-tests.md#46-存储输入webview-与其他系统能力); API 35 不证明 Android 17 新规, 本轮没有操作实体机.

### P2.13 内存上限 (I13)

- [x] `AVD_API_37` / CE2A.260420.019 / RAM 4096 MiB 的 `am memory-limiter status` 实测为 disabled, target 36 / 37 一致. 只完成环境核对, 未执行重负载或修改全局内存策略. [前置记录](evidence/android-sdk-37-p213-preflight-20260914.md). (DEVICE-37; DOCS)
- [x] 复核 `CrashHandler` / `CrashReportActivity`: 当前只处理 Java 异常与 Intent `message` / `error`, 生产代码没有 `getHistoricalProcessExitReasons` / `ApplicationExitInfo` 读取. 将原先误作既有能力验收的报告项拆为待实现. [源码依据](evidence/android-sdk-37-p213-preflight-20260914.md#2-现有报告链路). (SOURCE)
- [ ] (WATCH) 在内存限制器已启用的 API 37 镜像 / 设备运行有界的 OpenCV 大图 / OCR / Node 桥代表负载, 核对真实退出 reason=`REASON_OTHER`、description 包含 `MemoryLimiter:AnonSwap`, 并与被测进程和内存采样对应. disabled、普通 OOM、LMKD、手动结束均不能代替该证据. (DEVICE-37)
- [x] 实现设置页 "进程退出记录" (`25d34694e`): Android 11+ 后台读取当前安装以来最近 7 天 / 最多 20 条系统历史, 按时间 / 进程 / PID 去重, 保留正常退出、关联 WebView 与未知原因; 提供原始 reason / status / 描述、采样及复制, 不自动弹窗. target 36 / 37 各 6 个页面检查点通过, 最终正常包的空历史另 3/3, JVM 12/12. [实现与证据](evidence/android-sdk-37-p213-p214-20260914.md), [机器结果](evidence/android-sdk-37-p213-p214-results-20260914.json). (SOURCE; DEVICE-37)
- [ ] (WATCH) 在真正启用限制器的环境中核对负载 PID 与设置页报告, 验证限制器终止后的可读内容; 本轮手动停止 / signal 9 / 正常退出仅验证历史读取, 不替代限制器事件. 后续 SDK / 镜像若提供新的退出 reason, 同步核对判据; 旧系统不可用提示与 OEM 保留策略仍需设备验证. (DEVICE-37; PHYSICAL)

- [x] 2026-09-16: Three-Stone 的固定真实模型在专用 6 GiB API 37 AVD 启用 Android memory limiter 后推理成功, cgroup 初始 memory.high=2 GiB, high=2 后系统自适应上调阈值, 无 OOM / kill. 仅补齐该插件内存负载条件, 未代替上述宿主终止报告验收. [最后 9 仓收尾记录](evidence/android-sdk-37-p5-fleet-final-20260916.md). (DEVICE-37)

### P2.14 Advanced Protection Mode (I14)

- [x] 用 [Google 官方说明](https://blog.google/security/whats-new-in-android-security-privacy-2026/) 核对 Android 17 非辅助工具无障碍限制. 在独占 `AVD_API_37` 对 target 36 / 37 各执行开启 / 关闭保护、保护中解绑再绑定及设置页观察, 共 16 个状态快照: mode=true 时仍可绑定, 无 `no_non_tool_accessibility_service`, 设置页无拒绝文案. [实测与边界](evidence/android-sdk-37-p213-p214-20260914.md#4-p214-的实测边界). (DEVICE-37; SOURCE; DOCS)
- [x] 2026-09-16: 宿主和 Accessibility-Compat 全局状态与一致多语言说明已实现, 明确安全设置权限不绕过系统限制. 实际 Off / On、设置恢复刷新、WRITE_SECURE_SETTINGS 控制与 force-stop 重绑通过; 所测镜像仍未阻止绑定. [最后 9 仓收尾记录](evidence/android-sdk-37-p5-fleet-final-20260916.md). (SOURCE; DEVICE-37)
- [ ] (WATCH) 在实际实施限制的镜像 / 设备复现撤销与启用失败, 再为 `A11yServiceController` 失败路径增加准确检测和提示, 同步文档 FAQ. `AdvancedProtectionManager.isAdvancedProtectionEnabled()` 要求 SDK >= 36 与 QUERY_ADVANCED_PROTECTION_MODE; 全局 mode=true 不等于具体无障碍限制生效, 不能据此误报. 不声明 `isAccessibilityTool` (Q5=(a) / D16). (DEVICE-37; SOURCE; DOCS)

### P2.15 低影响项逐项验证 (I15)

- [ ] (WATCH) 跟进 P1 的 Xiaomi 23046RP50C API 35 观察: 单应用选择 AutoJs6 后脚本返回未授权, 同机全屏授权成功; 以 compile 36 / 37 对照及系统授权结果、宿主 Activity 回调定位原因, 再决定是否转独立修复. API 37 的单应用基础截图通过不能替代该 OEM 路径. (DEVICE: [实施记录](evidence/android-sdk-37-p0-p1-20260913.md) 第 5 节)
- [ ] (WATCH) 对 `BONDING_APIS_REQUIRE_PRIVILEGED_PERMISSION`, `MAKE_SOCKET_READ_BEHAVIOR_CONSISTENT`, `RESTRICT_DATA_URI_COLUMNS`, `ENFORCE_STRICT_SQL_CHECKS`, `CHECK_ADD_MIRROR_DISPLAY_PERMISSION` (截图 `images.captureScreen` 经 MediaProjection), `DEPRECATE_SET_CONTENT_CAPTURE_ENABLED`, `SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL`, `BACK_DISPOSITION_CONTROLS_BACK_INTERCEPTION`, `DETECT_IMPLICIT_URI_PERMISSION_GRANT` (加 `detectImplicitUriPermissionGrant()` 检查 `app.viewFile` / 分享路径, 为 Android 18 预备), 在附录 B 对应行填 "已验证无影响" 或转为独立条目. (DEVICE-37)
- [ ] (WATCH) 为第 3.2 节的未确认项建立结论表: a11y (`AccessibilityService`, `AccessibilityNodeInfo`, 事件 / 手势 / 全局动作 / 截图), MediaProjection (每会话授权 / 回调 / resize / FGS), overlay (显示 / 触摸 / 后台启动), JobScheduler / WorkManager / 闹钟, 通知 / WebView, 最低可安装 target. 每行给 API diff 或专题文档 URL、宿主调用点、运行用例和结论; 文档未提及与尚未测试单列. 已知 Android 14-16 规则不能标作 Android 17 新规. (SOURCE; DOCS; DEVICE-37)
- [ ] (RC-SMOKE) a11y 事件补测 CJKV 输入法的 `TYPE_VIEW_TEXT_CHANGED` 组合 / 提交过程, 关注 `getTextChangeTypes()` 新信息及已有监听器兼容性; 不因 `android.accessibilityservice` 包本身未发现差异而忽略 `android.view.accessibility`. 仅当脚本 API 暴露新字段时同步文档 / d.ts. ([官方目标版本变更](https://developer.android.com/about/versions/17/behavior-changes-17); DEVICE-37)

### P2.16 inrt 切换前适配 (D3)

- [x] (已被 P3.1 的实际切换取代, 2026-09-14; 提前验证的结果保留在 P3.3 表内) 在仍保留 `TARGET_SDK_VERSION_INRT=29` 的代码上, 用 P0.2 的 target 37 debug 覆盖包提前执行 P3.3 清单, 完成 inrt 专属权限 / 存储 / 启动流程的适配; 正常 target 29 包同步回归. P3.3 是最终验收表, 不能等提交 target 37 后才首次排查这些差异. (SOURCE; DEVICE-37)
- [x] 增加 `launchConfig.requiresSharedStorage=false` 及打包界面选项, 同步项目 / 远程 JSON、打包配置保存 / 导入 / 导出、固定字段与撤销 / 重做; 文档 / d.ts / Ace / 离线文档已同步. 配置测试 API 24 / 37 共 6/6. [实现与证据](evidence/android-sdk-37-inrt-storage-20260914.md). (SOURCE; DOCS; DEVICE)
- [x] assets / 私有目录脚本无存储授权启动; 共享路径需求缺少授权时引导, 取消 / 未授权返回不执行, Manifest 缺少声明提示重新打包. API 24 / 37 的正常 target 29 与覆盖 target 37 包最终 33/33, 含永久拒绝、设置中进程退出后恢复、隐藏桌面入口恢复和设置页运行脚本; 授权后检查真实文件读写. 同一冻结运行时在 Samsung API 37 的 target 29 / 37 两组另通过 16/16, [真机证据](evidence/android-sdk-37-inrt-storage-samsung-20260914.md). (DEVICE; DEVICE-37)
- [ ] (RC-SMOKE) 开机与无界面完整矩阵: 共享执行前检查和恢复入口已实现, API 37 / target 37 的私有脚本真实重启 1 项通过. 共享场景第二次重启因 AVD ADB 超时未完成, 拒绝 / 授权与 OEM 开机仍待验; 不以 UI 回调或源码守卫替代开机结果. (DEVICE-37; PHYSICAL)
- [ ] (RC-SMOKE) 完整旧安装迁移和真实用户 APK 验收: API 37 上同包名 / 签名、递增 versionCode 的 target 29 -> 37 已有所有文件访问授权路径在 AVD / Samsung 各 4 项通过, 原共享 / 私有文件保留, 撤销停止且重新授权恢复. 当前模板权限 maxSdkVersion 不能在 API 37 建立传统 READ / WRITE 基线, 历史 legacy 路径及 `preserveLegacyExternalStorage` 决策仍待 P3.3 矩阵; 提前夹具不替代最终 Runtime Kit / APK Builder. (DEVICE; DEVICE-37)

P2 验收: I1-I15 与 P2.16 每项有结论, 附录 B 对应行回填; target 37 覆盖包在 `AVD_API_37` 通过 P2.1-P2.5 与 inrt 专属用例; 同一代码的正常 debug 包在 API 28 / 33 / 35 真机上跑 a11y 冒烟与 VS Code 连接无退化; changelog 条目齐备. 已知平台限制单独记录, 不计为对应功能通过.

---

## P3: 宿主 targetSdk 37 切换

目标: `TARGET_SDK_VERSION=37` 与 `TARGET_SDK_VERSION_INRT=37` 同一提交切换, 全矩阵回归, 打包链路与用户 APK 验证.

### P3.1 切换

- [x] `version.properties`: `TARGET_SDK_VERSION=37`, `TARGET_SDK_VERSION_INRT=37`; P0.2 覆盖开关未再使用 (开关本身保留). `src/target37/AndroidManifest.xml` 随 `versions.sdkVersionTarget >= 37` 自动并入 app 与 inrt. (SOURCE: chore(sdk): raise targetSdk to 37 for app and inrt)
- [x] manifest 复核 (2026-09-14): `requestLegacyExternalStorage="true"` 保留, 仅对 API 29 及 target <= 29 的旧安装有效, 对 target 37 在 Android 11+ 无作用, 无需移除; `preserveLegacyExternalStorage` 未添加, 依据 D15 与 P3.3 已有的升级证据 (无该属性时 AVD / Samsung 各 4 项通过), 若真实用户升级反馈需要再评估 (WATCH); `tools:targetApi="tiramisu"` 由 lint 结果决定, 本轮 lint 见下条. (SOURCE)
- [x] 出包与门禁: `assembleAppDebug` / `assembleAppDebugAndroidTest` / `assembleInrtDebug` 成功, aapt2 badging app / inrt 均 compile 37 / target 37 / min 24 且声明 `ACCESS_LOCAL_NETWORK`; 单元测试 app 2612 (0 失败, 5 跳过), 全部模块 3246 (0 失败); lint: `lintAppDebug` / `lintVitalAppRelease` / `lintVitalInrtRelease` (`-Pautojs.sdk.lint.verify=true`, 52 分钟) 与 2026-09-13 的 compile 36 基线 `build/sdk37-lint-36` 用 `compare_lint.py` 对比: 两个 vital 报告不变 (各 1 项既有 Fatal `RemoveWorkManagerInitializer`); debug 报告 Error 546 -> 544, Fatal 1 -> 4 (计数含重复), Warning 2437 -> 2496, 新增 70 / 移除 10. 新增 Error / Fatal 只有两处, 均来自 P2 期间的代码而非 targetSdk 本身: `NetworkSecurityConfig` (debug NSC 三个 `.sdk37.test` 域缺 `includeSubdomains`) 与 `NewApi` (`DevPluginService` 调用 API 29 的 `RouteInfo.hasGateway`, 实际只在需要本地网络权限的 API 37 路径可达), 两处已随后修正 (`includeSubdomains="false"`, SDK_INT 分支), 未重新跑完整 lint. 新增 Warning 以 `InlinedApi` 18、`GradleDependency` / `NewerVersionAvailable` 各 10 为主, 与 target 无关; `tools:targetApi` 无新告警, 保持不变. 命令因既有错误退出 1, 与 P1 相同. ([记录](evidence/android-sdk-37-p3-20260914.md); ANDROID_BUILD; JVM; LINT)

### P3.2 宿主回归矩阵

- [x] (AUTO) `AVD_API_37` 门禁 (16 KB 镜像 `sdk_gphone16k_x86_64` CE2A.260420.019, target 37 正式包): cleartext 4/4, dex 1/1, install-state 2/2, looper-platform 2/2 (clearInterrupt=true / poolStarts=0), media 11/11, parcel 5/5, JVM 2612; looper-full 14/14 (ui-overlay 夹具改为从首个 tick 计时后); a11y 冒烟 42/42 (服务重启检查因无无人值守启动策略 SKIP). 原列出的悬浮窗 / OCR / VS Code / 定时 / 小组件 / 文件管理器 / 安装会话 / 快捷方式 / WebView / 预测性返回项归入第 0.4 节 RC-SMOKE 或各 P2 条目的 WATCH. (DEVICE-37: [记录](evidence/android-sdk-37-p3-20260914.md))
- [x] (AUTO) 旧系统真机门禁 (2026-09-14, target 37 正式 debug 包 + 同源测试包覆盖安装): Sony G8441 API 28、Sony XQ-AT72 API 31、Sony XQ-DQ72 API 33、Redmi 22120RN86C API 33、Xiaomi 23046RP50C API 35 各 cleartext 4/4, dex 1/1, install-state 2/2, looper-platform 2/2 (clearInterrupt=false / poolStarts=1), media 11/11 (Xiaomi 首轮 10/11 为首个用例通知超时, 重跑 11/11); native 3/3 仅 Xiaomi (两插件均安装), XQ-DQ72 因 Image Quantization 插件 1.0.0 过旧 (optionsApiVersion 0) 失败, 非 target 回归. API 28 首轮两个套件在连续 instrumentation 下出现 runner 进程未起即亡, 加入沉降与重试后全部通过. 人工只做 RC-SMOKE #2. (DEVICE: [记录](evidence/android-sdk-37-p3-20260914.md))
- [x] (AUTO) 16 KB: `AVD_API_37` 即 `google_apis_ps16k` 16 KB 镜像 (fingerprint `sdk_gphone16k_x86_64`, 内核页 16384), 上一条门禁即在该镜像执行; 真实 16 KB 内核设备为 RC-SMOKE #10. (DEVICE-16K)

### P3.3 打包应用 (inrt) 29 -> 37 清单 (D3)

用户 APK 的运行时代码与宿主共用, 以下按 API 等级列出对 "以 29 为目标的旧行为" 的增量, 每项在 `AVD_API_37` 与 API 33 真机上用打包出的示例应用验证:

| 等级 | 对打包应用的增量 | 验证 |
| --- | --- | --- |
| 30 | 分区存储强制: 任意共享路径的直接文件访问需评估所有文件访问权限; 应用专属目录 / MediaStore / SAF 各有规则, `MANAGE_EXTERNAL_STORAGE` 也不等于可访问任意其它应用私有目录. `InrtLaunchGate` 已按 Q4 的显式共享需求检查实际授权 | [提前验证](evidence/android-sdk-37-inrt-storage-20260914.md) 的 API 24 / 37 四组 33/33, Samsung 两组另通过 16/16; 最终用户 APK 与 API 30 / 33 迁移仍待验 结论 (2026-09-15): AVD API 30 / 33 / 37.1 各 8/8 (目标 37 首次安装) 与 4/4 (29 -> 37 覆盖安装, 所有文件授权与共享文件保留), 见 [P3.3/P3.4 证据](evidence/android-sdk-37-p33-p34-20260915.md) 第 3 节; 真机留 RC-SMOKE #8. |
| 30 | 包可见性 (`<queries>` 已在共用 manifest, 且声明 `QUERY_ALL_PACKAGES`) | `app.launch` / `app.getAppName` 任意包 结论 (2026-09-15): 三个打包示例的 `visibility-settings-app` (`app.getAppName` 解析系统设置包) 通过 (AVD 37.1). |
| 31 | 组件 `exported` 显式化 (共用 manifest 已满足); `PendingIntent` 可变性 (代码已按 36 处理); 后台启动前台服务限制 (`FGS_BG_START_RESTRICTION_CHANGE_ID`; `BOOT_COMPLETED` 属豁免); 精确闹钟权限 (`SCHEDULE_EXACT_ALARM` + 已声明的 `USE_EXACT_ALARM`); 系统启动画面叠加在自定义 `SplashActivity` 之前; 通知跳板限制 | 开机自启 (`InrtBootCompletedReceiver`), 定时任务, 启动画面观感 结论 (2026-09-15): 示例 `exact-alarm-capability` (`canScheduleExactAlarms` 为 true) 与 timer 示例的 `countdown-task-scheduled` / 20 s 定时脚本实际触发通过; 开机自启未自动化 (WATCH). |
| 33 | `POST_NOTIFICATIONS` 运行时权限; 拒绝时前台服务通知在通知栏不可见但服务仍需合法通知及启动条件; 细分媒体权限; 旧 `WRITE_EXTERNAL_STORAGE` 不能代替共享存储授权 | 通知权限开关 (`PostNotificationsPermissionSwitchPreference`) 待完整用户 APK 验收; 启动旧 WRITE 无条件申请已移除, 按 OS / 真实存储模式引导 结论 (2026-09-15): 示例 `post-notifications-runtime` (未授权时 `areNotificationsEnabled` 为 false, 前台服务与脚本照常) 通过. |
| 34 | 前台服务类型必填 (已声明); 运行时注册接收器需导出标志 (共用代码); 隐式 Intent 只匹配已导出组件; `Thread.stop` 不可用; 动态加载 DEX 只读 (`AndroidClassLoader` 已处理); MediaProjection 每次会话授权 + 回调 | `runtime.loadDex`, 截图, `events.broadcast` 结论 (2026-09-15): 示例 `load-dex` (`runtime.loadDex` 只读 DEX) 与 `broadcast-roundtrip` (运行时注册接收器) 通过; 截图未自动化. |
| 35 | 边到边强制 (UI 模式脚本布局); 前台服务 `dataSync` / `mediaProcessing` 超时 (未用); `BOOT_COMPLETED` 禁止启动部分类型前台服务 (`specialUse` 允许); 悬浮窗不再豁免后台启动前台服务 (`FGS_SAW_RESTRICTIONS`, 仅可见悬浮窗豁免); 最低可安装 targetSdk 24 | UI 模式脚本顶部内容, 开机自启后的前台服务 结论 (2026-09-15): ui 示例 `ui-edge-to-edge` (内容顶部 0、跨满显示区、状态栏 inset > 0) 通过. |
| 36 | 预测性返回默认开启 (`onBackPressed` 不再回调, 共用 `BackCompat`); 边到边豁免移除; 大屏忽略方向; Intent 重定向保护; `announceForAccessibility` 弃用 | 打包应用的 `LogActivity` / `SettingsActivity` 返回, 平板方向 结论 (2026-09-15): ui 示例 `ui-predictive-back-finishes-activity` (BACK 到达 `ui.emitter` back_pressed 回调且 activity 退出) 通过; `LogActivity` / `SettingsActivity` 返回与平板方向未自动化 (WATCH). |
| 37 | P2 全部条目, 其中本地网络权限与后台音频对打包应用最直接 (脚本自建 HTTP 服务 / TTS) | 复用 P2.1 / P2.2 的用例 结论 (2026-09-15): 示例 `local-network-declared` 与 timer 示例 `tts-done` 通过; 真实 LAN 访问留 RC-SMOKE. |

- [x] 依上表逐行验证并修正 inrt 专属流程 (`SplashActivity` 权限引导, `fragment_preferences_inrt.xml` 的开关顺序与默认值). (SOURCE; DEVICE-37; DEVICE) 2026-09-15: 上表每行已有结论 (AVD 自动矩阵 + 打包示例), inrt 专属流程无需修正; 真机项归 RC-SMOKE #8.
- [x] 安装矩阵: 用相同 applicationId / 签名、递增 versionCode 的示例 APK 测 target 29 -> 37 覆盖安装, 并另测 target 37 首次安装 / 卸载重装, 两者不能共享成功结论. API 30 设备或 AVD、API 33 真机与 `AVD_API_37` 分别记录旧文件位置、已有授权、升级后的实际访问及迁移结果. `preserveLegacyExternalStorage` 不能替代新安装的权限引导. 2026-09-14 已补 API 37 当前模板的所有文件访问授权升级, AVD / Samsung 分别通过 4 项, 不覆盖传统 legacy 授权或最终用户打包链. ([官方迁移说明](https://developer.android.com/training/data-storage/use-cases); DEVICE; DEVICE-37) 2026-09-15: 目标 37 首次安装 (vc 7000-7003) 与 29 -> 37 覆盖安装 (vc 8000 -> 9000) 分别在 AVD API 30 / 33 / 37.1 通过, 夹具用宿主密钥签名 (`--signing-properties`); API 33 真机留 RC-SMOKE #8.
- [x] 打包器 UI (`ui/project/BuildActivity`, `ProjectConfigActivity`) 与文档说明打包应用在 Android 11+ 的存储引导. `requiresSharedStorage` 同步项目配置与打包保存路径, QA 说明默认值、共享路径范围、取消 / 返回和隐藏入口恢复; d.ts、Ace 与离线文档已生成并验证. [实施记录](evidence/android-sdk-37-inrt-storage-20260914.md). (DOCS)

### P3.4 打包链路与 Runtime Kit

- [x] `generateRuntimeKit` 重新生成; `AutoJs6-Plugin-APK-Builder-Template` 的 JVM 测试与 `build-from-runtime-kit.yml` 夹具中若有 `targetSdkVersion=29` 的硬编码期望, 改为读取模板 (该仓库条目在 P5.2 落地, 本条只做只读核对). (SOURCE; JVM) 2026-09-15: Runtime Kit 以 HEAD `6da20f969` 重新生成 (5 个变体, `verify_runtime_kit_set.py` 通过); 插件仓库无 targetSdk 29 硬编码; 插件 `ce6dfd9` (v1.0.3, 配对宿主 5280).
- [x] 用新 Runtime Kit 打包 3 个示例 (a11y 冒烟, UI 模式, 定时任务 + TTS), `aapt2` 显示 `targetSdkVersion:'37'`, 在 `AVD_API_37` 与 API 33 / 35 真机安装运行. (DEVICE-37; DEVICE) 2026-09-15: `run_packaged_examples.py` 在 `AVD_API_37.1_16K` 打包、安装并运行三个示例 (targetSdkVersion 37), 脚本检查 11 / 9 / 9 全部通过; 过程中发现并修复插件的 `resources.arsc` 压缩回归 (目标 30+ 无法安装); 真机与 arm64 未做, 见 [P3.3/P3.4 证据](evidence/android-sdk-37-p33-p34-20260915.md).

### P3.5 文档与提交

- [x] changelog: 按 D9 记录宿主 targetSdk 36 -> 37 与 inrt 29 -> 37 的实际变化, 核对 P2 的 `fix` / `improvement`, README 重新生成; 本文回填. (DOCS) 已随 P3.1 的 `caea32694` 完成 (2026-09-14); 2026-09-15 本文回填 P3.3 / P3.4.
- [x] 提交 `chore(sdk): raise targetSdk to 37 for app and inrt (roadmap P3)`. (DOCS) 已提交 `caea32694` (2026-09-14).

P3 验收: P3.2 矩阵通过; P3.3 表每行有结论; 用户 APK 在 API 37 / 35 / 33 上安装运行; CI 绿.

---

## P4: 关联项目, 文档与 CI

- [x] P2.1 权限文档: `auto-generate-for-autojs6.bat --verify-offline` 已运行, Documentation `b46c52d` / `ecb72eb`, Offline-Docs `28c2284`; 版本与来源记录同步. (DOCS; ANDROID_BUILD)
- [x] P2.1 d.ts / LSP: 既有 requestPermissions 的通用名称数组纠正、异步说明及示例同步; `aj6dts.bat -Publish`、`:app:generateAutoJs6LspDeclarations` 与 `:app:verifyAutoJs6LspRuntime` 通过. DTS `acd8b62` (4.11.1), Ace `0d9e9a0` (1.1.28 / 39), 导出器 `525bebd`. (DOCS; ANDROID_BUILD)
- [ ] P2 其余条目完成后, 补齐 "Android 17 适配说明" (后台音频、OTP、WebView UA、反射限制、私有 CA 等已验证变化), 如有 JS API / 权限模型变化继续同步文档与声明. P2.1 的同步不代替最终迁移说明. (DOCS)
- [ ] VS Code 扩展 (`AutoJs6-VSCode-Ext`): README / 故障排查增加 "Android 17 设备需授予本地网络权限" 说明 (不改代码, `VSCODE_EXT_REQUIRED_VERSION` 不变). (DOCS)
- [ ] CI: P1 / P3 各自推送后验证远程 `android.yml`, 记录 runner 是否自动下载 `android-37.0`; `release-runtime-kit.yml` 的外部发布与插件触发随正常发布验证, 构建步骤可先本地运行. (ANDROID_BUILD)
- [x] `AutoJs6-Official-Plugins-Index`: 确认生成器无 targetSdk 字段假设; 插件发布时按既有流程刷新. (SOURCE) 完成 (2026-09-16): 索引仓 `df2eb35` 的 `tools/generate_official_plugin_index.py` 与 `plugins.official.generated.json` 没有任何 SDK 字段, 无需改动; 发布后的刷新仍按既有流程.

---

## P5: 官方插件舰队 (42 仓)

目标: 每个插件 `compileSdk` / `targetSdk` 均为 37, 暴露项已适配, 各自 changelog / README / 版本号更新. 逐仓清单与暴露列见附录 E; 每仓都需独立记录 C (compile), T (target), V (验证), D (版本与文档) 四项状态, 不能只用分组的一次勾选代替 42 仓验收.

进度 (2026-09-16): 42 仓 compile / target 37 已全部提交; 最后 9 仓含权限适配、真实取色 / 后台播放 / 模型负载 / 无障碍说明, 见 [最后 9 仓收尾记录](evidence/android-sdk-37-p5-fleet-final-20260916.md). 版本、设备及按条件执行的测试结果更新在附录 E.

### P5.0 每仓通用条目 (复制到附录 E 对应行的证据栏)

0. 工具 (D21): `py tools/sdk37/fleet.py scan|table|plan` 读取 42 仓的 `version.properties`、Gradle 硬编码、manifest 暴露、脏文件与最后提交, `plan` 给出每仓下一步; `apply <repo> --compile 37 --target 37 --gradle assembleDebug testDebugUnitTest` 只改一个干净仓库的属性文件并跑其 Gradle 检查, 不提交. 2026-09-15 扫描: 40 仓 compile 36, Archive-Manager 与 Accessibility-Compat 已 compile 37; 全部 target 36; Python-Runtime 与 R8-Compiler 在 Gradle 脚本硬编码 36; 脏工作区 9 个 (Accessibility-Compat 65 为进行中的功能开发, Lua 30, Paddle v3 40 / v4-v6 各 6, Rapid v3 45 / v6 56, MLKit-OCR 3, Yolo 36). (SOURCE) 2026-09-15 晚: `bump <repo> --compile 37 --target 37 --serial <AVD> --commit` (`92335f13b`) 一条命令完成第 2 / 4 / 5 步 (属性文件, 补丁版本递增且保留里程碑后缀, `VERSION_BUILD` = 提交数 + 1, 十语 changelog, README 模板 (`common.json` 数字 / 版本, `--readme-keys` 刷新正文里的旧级别), 生成器与仓库检查, Gradle, AVD 安装 + installed targetSdk 核对 + androidTest (`--instrument-arg notClass` 排除与环境绑定的测试并记入证据), 固定 message 提交并核对提交数); 首批 17 仓完成, 见附录 E 证据栏与 `evidence/android-sdk-37-p5-fleet-20260915.md`. (SOURCE; DEVICE-37)
1. 工作区: 若附录 E "脏" 列非 0, 先由维护者提交或搁置既有改动 (D11). (SOURCE)
2. compileSdk: 核查 `version.properties` 到各构建脚本的实际消费链后设为 37, 包含 app / Android library / lint 配置; R8-Compiler 硬编码例外见 P5.3. 构建 (`assembleDebug` / `assembleRelease`), 单元测试, 既有门禁 (16 KB `verifyNativePageAlignment`, 各仓库自有校验); 修正弃用并核对 APK 元数据. (ANDROID_BUILD; JVM)
3. 暴露适配: 按附录 E 暴露列处理 (本地网络 -> 复用 P2.1 权限助手模式; 动态加载 -> 只读; 前台服务 / 音频 -> P2.2 结论; 屏幕捕获 -> P2.15 结论; WebView -> P2.11 结论). (SOURCE)
4. targetSdk: `TARGET_SDK_VERSION=37`; 在 `AVD_API_37` 与一台 4 KB 真机 (API 33 或 35) 上跑该插件的 instrumentation / 冒烟 (16 KB Roadmap 用过的 `*DeviceTest`, `ocr_provider_smoke.py` 等). (DEVICE-37; DEVICE)
5. 版本递增 (各仓库惯例), changelog 按 D9 与各仓库规则记录已完成的 compile / target 步骤及适配, README 重新生成, 单独提交. (DOCS)
6. 发布节奏按 Q6=(b) / D17: 全部 42 仓各自发布 SDK 升级版本, 完成各自门禁后发布并刷新官方索引; 提交、构建与发布分别记录.

### P5.1 被宿主进程加载 (OpenCV, Image-Quantization)

- [ ] 两仓通用条目; 运行时 `.so` 的只读处理在宿主侧 (P2.3), 插件侧无运行时暴露; 提交后用宿主 target 37 覆盖包复测加载. (DEVICE-37) 进度 (2026-09-15): OpenCV `1101000` v1.1.4 与 Image-Quantization `24d1ea8` v1.0.3 已 37 / 37 并通过各自 AVD instrumentation (Image-Quantization 排除 1 项与环境绑定的金样测试, 证据 3.1); 宿主 target 37 覆盖包的加载复测未做.

### P5.2 打包进用户 APK (Paddle v3 / v4 / v5 / v6, Rapid v3 / v6, APK-Builder-Template)

- [ ] 6 个 OCR 仓库通用条目; 核对引擎以 `System.loadLibrary` 从 `nativeLibraryDir` 加载 (只读), 无 staging 到可写目录的路径; 在 target 37 的用户 APK 内 (P3.4 示例) 复测识别. (SOURCE; DEVICE-37) 进度 (2026-09-15 深夜): Paddle v3 `47d795c` v1.0.5, v4 `e679494` v1.0.4, v5 `eab79a0` v1.0.4, v6 `160aaa5` v1.0.4, Rapid v3 `815893d` v1.0.5, Rapid v6 `0d535dc` v1.0.5 已 37 / 37 (前置: Gradle wrapper 9.3.0 -> 9.5.0, 平台插件按 Gradle 版本封顶 AGP, 9.3.0 只到 9.0.1 而 compileSdk 37 需 9.1.1+, 9.5.0 解析到 9.3.2; Paddle v4-v6 另删去 `libs/*/build.gradle.kts` 中 AGP 9.3 库模块不再接受的 Kotlin source-set 覆盖), AVD 安装 + target 核对 (v3 / v4 / Rapid 各 1 项 instrumentation, Paddle v5 / v6 无 androidTest); `System.loadLibrary` 只读检索与 target 37 用户 APK 内识别复测未做.
- [x] APK-Builder-Template: 通用条目 + 模板 / 夹具中 targetSdk 期望改为读取模板 (P3.4), `configureManifest` 不改写 `uses-sdk` 的约束保持; CI 工作流对模板 APK 的 `aapt2` 检查加 `targetSdkVersion` 断言. (SOURCE; JVM; ANDROID_BUILD) 完成 (2026-09-15): `41f06fd` (v1.0.3 build 44, 并入未发布的 1.0.3), `build-from-runtime-kit.yml` 对 kit 的 `template.apk` 断言 `targetSdkVersion` 等于插件声明的 `TARGET_SDK_VERSION`; 插件测试无 targetSdk 硬编码; 用 5280 universal kit 重建后三个打包示例在 API 37.1 AVD 全部通过 (`evidence/android-sdk-37-p5-apk-builder-examples-20260915.md`).

### P5.3 独立进程运行时插件 (NodeJs, Bun, Python, Lua, QuickJS, TypeScript-Engine, Java-Runtime, Kotlin-Runtime, DEX-Compiler, R8-Compiler)

- [ ] 通用条目; 逐仓检索 `System.load` / `dlopen` / `DexClassLoader`, 区分 Java 原生库加载, NDK 加载与独立 exec. 核查 Node `.node` / 用户 `.so`, Bun `libbun_exec.so` 及用户库, Python / Chaquopy 扩展模块, Java / Kotlin / DEX / R8 的 DEX 产物. Java `System.load` 按 P2.3 处理, DEX 延续已有只读要求; NDK `dlopen` / exec 是否受其它规则限制须有对应源码或设备证据, 不把 `THROW_ERROR_FOR_WRITABLE_DCL` 的 Java 行为直接推广到全部加载方式. 各插件用 target 37 包复测. (SOURCE; DEVICE-37) 进度 (2026-09-15): QuickJS `947e4ab` v1.0.3, TypeScript-Engine `831f64e` v0.6.3, Java-Runtime `9ef0b46` v0.8.2-m9, Kotlin-Runtime `3038600` v0.7.2-m10, DEX-Compiler `8383eeb` v1.1.2 (前置修复 `aed6e7c`: 手写 `platforms/android-<level>` 路径改为 AGP bootClasspath, SDK 的 API 37 目录名为 `android-37.0`) 已 37 / 37 并通过 AVD instrumentation (排除项见证据 3.2 / 3.3); 本条的加载方式检索与 target 37 复测仍开放. 2026-09-15 深夜: Lua-Runtime `050ca26` v0.1.2-rc.2 (5 项; provider / nativeTest / faultTest 三 flavor 的 APK 目录) 与 R8-Compiler `a2f4f75` v0.2.2-provider-dev (3 项) 已 37 / 37; NodeJs-Runtime `c8da4bf` v1.5.2 (61 项) 与 Python-Runtime `f20f364` v0.5.3 (1 项) 只做了 compileSdk 37 (两步走第一步, target 仍 36); Bun-Runtime 由维护者会话 `8b5b097` 提升 compileSdk 37 (0.2.3, target 36), 本会话未动. 2026-09-16: Node / Bun / Python 的 target 步骤均已完成, 最终版本见附录 E 与收尾证据.
- [x] 本地网络: Node / Bun / Python / MCP 显式声明 `ACCESS_LOCAL_NETWORK`, 按插件 UID 检查并在相关实际失败中追加插件设置引导; 插件内主动点击申请, 拒绝及设置返回按实际状态处理. MCP LAN 开关另需显式确认, 授权本身不启动监听, 回环不受 LAN 前置检查影响. 四仓实际系统对话框拒绝 / 授予、撤销 / 恢复与 socket / 服务绑定复测通过. target 37 不再依赖 INTERNET 的旧 target 兼容拆分. 2026-09-16 完成, [最后 9 仓收尾记录](evidence/android-sdk-37-p5-fleet-final-20260916.md). (SOURCE; DEVICE-37)
- [ ] 内存上限与 MessageQueue: 各运行时若使用 Android Looper, 在 `USE_NEW_MESSAGEQUEUE` 下复测; 大负载脚本观察 `am memory-limiter status`. (DEVICE-37)
- [ ] R8-Compiler: `app/build.gradle.kts` 与两个 plugin-api 模块硬编码 compileSdk 36, app 的 targetSdk 以及 library lint target 也为 36; 先统一版本来源再执行通用条目. app 的 versionCode / versionName 同样硬编码, 按该仓实际发布约定递增. 另外核查固定 `r8-library/android-36.jar` 及 SHA-256: 插件自身 compileSdk 提升不会更新脚本编译所用的平台库, 必须记录对 API 37 输入 / 符号的支持边界及相关测试. (SOURCE; JVM; ANDROID_BUILD) 进度 (2026-09-15): `e0b13cb` 两个 plugin-api 模块改读 `version.properties`; `a2f4f75` v0.2.2-provider-dev (build 20) 已 37 / 37, AVD instrumentation 3 项 (前置 `9af84bd`: JUnit3 形式的 retrace 设备测试改为 JUnit4, target >= 28 的包没有 `android.test.base`, 该测试此前在任何设备都 NoClassDefFoundError); `r8-library/android-36.jar` 保持 36 (脚本编译用平台库, README 十语只改 targetSdk 措辞), API 37 输入 / 符号边界仍待记录.
- [x] NodeJs-Runtime 的 target 步骤与宿主直接 exec: `861e084` v1.5.3 / 174, 37 / 37, 完整 androidTest 62 项. 实际宿主脚本进程以宿主 UID 从插件只读 `nativeLibraryDir/libnodexe.so` 启动 Node v24.21.0, npm / npx 11.19.0, Corepack 0.36.0, 全通过; JS CLI 资产解包到宿主私有 cache, 与插件 UID 分开, 可写目录未放可执行二进制. 终端 UI / 下载 npm 包和通用存储矩阵另行验收. [最后 9 仓收尾记录](evidence/android-sdk-37-p5-fleet-final-20260916.md). (SOURCE; DEVICE-37)
- [x] Python-Runtime: `app/build.gradle.kts` 第 401 / 407 行硬编码 compileSdk / targetSdk 36 (versionCode / versionName 已走 `versions.*`), 先改为 `versions.sdkVersionCompile` / `versions.sdkVersionTarget` 再执行通用条目; 这是 2026-09-15 扫描新发现, 原表未记录. (SOURCE) 完成 (2026-09-15): `4ea9fe5` 改读 `versions.sdkVersion*` (含 `expectedRuntimeSupply` 的 lock 校验); compileSdk 37 `f20f364` (0.5.3 / 108, `locks/python-runtime.lock` 的 `android.compileSdk` 同步为 37, AVD instrumentation 1 项); target 步骤于 2026-09-16 完成 (`808e716`, 0.5.4 / 109, 完整 2 项及 LAN 四状态通过).
- [x] Bun-Runtime 平台解析与 target 步骤: 维护者的最终平台插件已为 1.8.0, wrapper 9.5.0; `4a7d654` v0.2.4 / 111 在实际 compile / target 37 构建、Lint、原生载荷校验与 signed release 全通过. API 37 / 4 KB 完整套件 18 项通过, 1 项只适用 16 KB x86_64 的拒绝诊断在对应 AVD 另跑通过; 19 个独立用例均有记录, 无显式排除. LAN 四状态已复测最终 APK. [最后 9 仓收尾记录](evidence/android-sdk-37-p5-fleet-final-20260916.md). (SOURCE; ANDROID_BUILD; DEVICE-37)

### P5.4 网络, 媒体与屏幕插件 (MCP-Server, Screen-Color-Picker, Three-Ember-Player, Three-Terra-Player, Three-Stone-AI, HTML-Previewer, Markdown-Previewer, Offline-Docs, Ace-Editor)

- [x] MCP-Server 的 LAN 权限引导与 target 步骤: `d4c3796` v1.0.2 / 60, 37 / 37, 在最终维护者 HEAD 重建与复测. 插件设置页显式申请及 LAN 二次确认, 服务绑定前检查, 拒绝 / 授予 / 撤销 / 恢复四状态与回环均通过; `specialUse` 保持. 完整 suite 35 项执行通过, opt-in 监听进程终止另 1 项通过, 共覆盖 36 个独立用例. [最后 9 仓收尾记录](evidence/android-sdk-37-p5-fleet-final-20260916.md). (SOURCE; DEVICE-37)
- [ ] (WATCH) MCP-Server 在 API 37 下与 Inspector / Claude Code 的完整配对和实体 LAN 拓扑, 复用其独立 Roadmap 的对应矩阵; 自动化协议套件不替代外部客户端组合. (DEVICE-37; PHYSICAL)
- [x] Screen-Color-Picker 的 target 步骤与真实取色: `c2625fe` v1.1.3 / 15, 37 / 37, 完整 androidTest 10 项. API 37.1 实际系统 MediaProjection 取消 / 授权, 悬浮取色夹具 `#123456`, 前台读剪贴板得到 HEX `#123456` / RGB `R18,G52,B86`, 停止后 projection / FGS / 悬浮窗释放. [最后 9 仓收尾记录](evidence/android-sdk-37-p5-fleet-final-20260916.md). (DEVICE-37)
- [x] Three-Ember / Three-Terra 的 target 步骤与真实后台播放: `5cabf8b` v3.1.2 / 27 (35 项), `cf1ed3d` v1.6.2 / 37 (48 项), 均 37 / 37. 修复 Ember 通知交接导致 FGS 被系统撤销及用户停止后服务未释放. 最终 HEAD 的 120 秒真实媒体在已核对 `mHardeningOverride=3` 的 throw 模式下后台继续、暂停 / 恢复、锁屏控制及明确退出通过. 当前镜像有效命令为 `cmd audio set-hardening throw`, 旧 `set-enable-hardening` 未改变状态. [最后 9 仓收尾记录](evidence/android-sdk-37-p5-fleet-final-20260916.md). (SOURCE; DEVICE-37)
- [ ] (WATCH) 播放列表切换、实际听音、蓝牙与 OEM 音频行为按实体机矩阵继续; headless / no-audio AVD 不提供听音结论. (PHYSICAL)
- [x] Three-Stone-AI 的 target 步骤与内存限制器下的真实推理: `48a1b59` v1.1.4 / 191, 37 / 37. 源码只用 Backend.CPU / Backend.GPU, 无 NPU 委托, 不需要 `android.hardware.npu`. 用 `.python/run_real_model_test.py` staging 已核对 SHA 的 1.598 GB Qwen2.5 1.5B 模型, 专用 6 GiB AVD 配置真正启用 Android memory limiter; CPU 推理正常完成, 实际 memory.high 压力事件 high=2, 无 OOM / kill. 最终 HEAD 完整 3 项含真实模型测试通过, 无跳过. 初始 2 GiB 是自适应 soft threshold, 未验证限制器杀进程或 GPU; P2.13 退出报告 WATCH 保留. [最后 9 仓收尾记录](evidence/android-sdk-37-p5-fleet-final-20260916.md). (SOURCE; DEVICE-37)
- [ ] HTML / Markdown 预览, Offline-Docs, Ace-Editor: WebView UA 缩减与 File System Access 下渲染正常; 同 profile 的 LSP / 本地文档回环验证与跨 profile 限制分开记录. (DEVICE-37) 进度 (2026-09-15): Offline-Docs `2e253cd` v6.8.2 已 37 / 37 (AVD instrumentation 2 项), WebView 复测未做. 2026-09-15 深夜: HTML-Previewer `3ecc6e2` (1.1.1 / 21, 42 项, 前置 `62d2126`), Markdown-Previewer `6b441c4` (1.2.1 / 17, 28 项), Ace-Editor `62d5d9e` (1.7.1 / 98, 23 项, 前置 wrapper `3501db2`) 只做 compileSdk 37; WebView 复测与 target 步骤开放. 2026-09-16 凌晨: HTML-Previewer `ce965bf` v1.1.2 (42 项) 与 Markdown-Previewer `ed7c37b` v1.2.2 (28 项) 已 37 / 37 (WebView 只加载自有 origin 的文档, 外部资源默认拦截, INTERNET 不涉及局域网); Ace-Editor `271e3c9` v1.7.2 (23 项; 第一次因 Lua 语义冒烟测试的 1.5 s signatureHelp 时序失败, 单跑与第二次全量均通过) (INTERNET 用于字体下载, LSP 回环). UA 缩减 / File System Access 以套件通过为证据, 无逐项测试.

### P5.5 其余插件 (MLKit-Barcode, MLKit-OCR, Yolo-NCNN, MediaInfo, OpenCC, ImGui, Archive-Manager, Image-Tools, Image-Viewer, File-Inspector, APK-Inspector, Pinyin, Pinyin4j, Accessibility-Compat)

- [ ] 通用条目; Binder 协议在 `PARCEL_HARDENING` 下复测 (MLKit x2, Yolo, MediaInfo, OpenCC, ImGui); 存储类插件 (Archive-Manager, File-Inspector, Image-*) 按 P2.8 已核实的 MediaStore 卷枚举 / 媒体回收行为及 SAF / OTG 生命周期复测, 分别记录插件自身 target 与 provider 条件; 联系人目录权限开关不套用到存储插件. Archive-Manager 已是 compileSdk 37, 只做 targetSdk 步骤. (DEVICE-37) 进度 (2026-09-15): MediaInfo `48bb6fb`, ImGui `ee9054d`, Image-Tools `9210c49`, Image-Viewer `eb0fde2`, File-Inspector `7035ef1`, APK-Inspector `59387a6`, Pinyin `2527cd5`, Pinyin4j `28507f5` 已 37 / 37 并通过各自 AVD instrumentation; `PARCEL_HARDENING` 与存储行为复测未做. 2026-09-15 深夜: MLKit-OCR `61970ac` v1.0.3 (1 项, 前置 wrapper `56cb291`) 与 Yolo-NCNN `d4e9bbb` v0.1.3 (5 项) 已 37 / 37; MLKit-Barcode `10f00bc` (1.0.3 / 16, 1 项, 前置 wrapper `413aaaa`) 与 OpenCC `822afed` (1.4.1 / 100, 6 项, 排除三个需 `-e` 阶段参数的多阶段测试, 前置 `2b1ac48`) 只做 compileSdk 37 (INTERNET 暴露, 两步走); Archive-Manager `4b308f9` v2.22.3 (106) 完成 targetSdk 37 (仅 target 步骤, AVD instrumentation 178 项, 排除 `ArchiveIntentPolicyInstrumentationTest#manageArchiveRejectsZipContainerAliases`: HEAD 构建在同一 AVD 复现失败, 与 target 无关, 留给维护者); 存储行为复测按通用条目仍开放. 2026-09-16 凌晨: Archive-Manager 维护者 `534cd7c` 后 HEAD 全量 180 项通过; MLKit-Barcode `57e88a0` v1.0.4 (1 项; manifest 以 `tools:node="remove"` 去掉 INTERNET, 实际无暴露, 扫描器 `2156eceab` 修正) 与 OpenCC `1e5bfc9` v1.4.2 (8 项, 方法级排除 `OpenccStandaloneUiTest#allStandaloneActionsRemainExplicitCancellableAndRestorable`: 套件内失败、单跑通过, HEAD 同样, 留给维护者) 已 37 / 37.

- [x] OpenCC 维护者 `5c04062` 后完整套件的 Paste / 窗口转换用例通过, 无方法级排除; 原 4 类条件用例另按资源入口、重启、截图及性能阶段全部补测, 覆盖 9 个独立用例. [最后 9 仓收尾记录](evidence/android-sdk-37-p5-fleet-final-20260916.md). (DEVICE-37)

- [x] Accessibility-Compat 的 P2.14 / D16 说明与 target 步骤: `d822ccd` v1.3.1 / 18, 37 / 37, 完整 androidTest 12 项. 插件和宿主均声明 QUERY_ADVANCED_PROTECTION_MODE, 检测全局模式并提供一致十语说明, 设置返回刷新; 保持不声明 isAccessibilityTool. 实际 mode=false / true 各完成 WRITE_SECURE_SETTINGS 禁用 / 启用及两次 force-stop 重绑, 保留其它服务配置; 宿主 / 插件设置页状态与说明 UI 通过. 所测镜像 mode=true 仍可绑定, 不误报具体服务已被限制; 真正发生限制后的启用失败仍由 P2.14 WATCH 跟进. [最后 9 仓收尾记录](evidence/android-sdk-37-p5-fleet-final-20260916.md). (SOURCE; DEVICE-37)

P5 验收: 附录 E 每行 compile / target 均为 37 且有提交 hash 与验证设备; 官方索引在发布后刷新.

---

## P6: 验收与关闭

- [ ] 宿主全矩阵 (P3.2) 与舰队 (P5) 证据齐备; `git log` 中每个子条目一个提交; 远程 CI 绿. (DOCS)
- [ ] 本文 "阶段总览" 状态列更新为 "完成"; 附录 A 已确认事项的实施状态均有结论; 附录 F 的后续建议移交给各自的 Roadmap 或 backlog. Q1-Q7 的决策本身已于 2026-09-14 回填到第 1 节. (DOCS)
- [ ] 自动记忆索引 (`MEMORY.md`) 中本 Roadmap 条目更新为已关闭. (DOCS)

---

## 附录 A: 已确认事项 (2026-09-14)

维护者已明确选择 Q1=a / Q2=a / Q3=b / Q4=a / Q5=a / Q6=b / Q7=b. 下表保留原选项供追溯; 确认决策不等于实现或验收完成.

| 编号 | 事项 | 选项 | 已确认结果 / 实施状态 |
| --- | --- | --- | --- |
| Q1 | 本地网络权限的申请时机 (P2.1) | (a) 懒申请: VS Code 连接 / 用户显式开启的服务启动时引导, 脚本层只报错 + 设置页开关; (b) 应用首次启动即申请; (c) 脚本层任何局域网调用都弹窗 | (a), D12. 既有实现 `d9465be13` 获明确确认; 剩余真机拓扑验收见 P2.1 |
| Q2 | Certificate Transparency 默认策略 (P2.6) | (a) 保持默认; (b) 对已验证且确需豁免的域作局部配置; 证书信任另行处理, 不因用户 CA 未受信任而关闭 CT | (a), D13. 保持默认, 未增加生产例外 |
| Q3 | 后台音频策略 (P2.2) | (a) 保持常驻服务, 提供可见界面恢复操作与已知限制; (b) 引入 Media3 `MediaSessionService` 承载脚本音频 | (b), D14. 已调整 P2.2 待办; 原 (a) 恢复指引的通过证据不关闭 Media3 迁移 |
| Q4 | inrt 存储引导与旧版升级保护 (P2.16 / P3.3) | (a) 按共享存储需求引导并验证旧安装迁移; (b) 仅设置页开关; `preserveLegacyExternalStorage` 的采用由升级矩阵决定, 不承担新安装兼容保证 | (a), D15. 按需引导已实现, 提前 UI / 读写 33 项及部分升级通过, Samsung 另补 16 项 UI / 读写与 4 项升级; 完整历史迁移 / 开机 / 用户 APK 矩阵继续 |
| Q5 | `isAccessibilityTool` (P2.14) | (a) 不声明, 只检测 Advanced Protection 并提示; (b) 声明 | (a), D16. 2026-09-16 宿主和兼容插件全局状态 / 说明已实现并通过 UI 与安全设置复测; 实际非辅助工具限制后的失败分支继续 WATCH |
| Q6 | 插件舰队发布节奏 (P5) | (a) 随各插件下一次发布顺带, 有行为适配的插件 (MCP-Server, Node, Bun, Python, 播放器 x2, Screen-Color-Picker) 单独发版; (b) 全部单独为 SDK 升级发版; (c) 只提交不发布, 由维护者统一发布 | (b), D17. 全部 42 仓单独为 SDK 升级发版, 各自门禁与发布记录仍待完成 |
| Q7 | `usesCleartextTraffic` 迁移到 NSC 是否随本次 | (a) 否, 记入附录 F; (b) 是 | (b), D18. 迁移、API 24 / 37 AVD 与 Samsung D37 受控验证已完成 (`494a048df`); O02 剩余场景和 P2.6 其他验收继续保留 |

---

## 附录 B: targetSdk 37 门控变更逐项评估

来源: `AVD_API_37` 的 `dumpsys platform_compat` (36 项 `enableSinceTargetSdk=37`), 含义列的核对状态: VERIFIED = 官方页面明确描述; UNVERIFIED = 仅由名称推断或二手来源. "结论" 列在 P2 完成后回填.

| 变更 (ID) | 含义 | 宿主影响 | 条目 | 结论 |
| --- | --- | --- | --- | --- |
| `ASM_RESTRICTIONS` (230590090, overridable) | 本机 target 37 门控已实测: 顶部应用声明保护且无 Activity 例外时, 下层停止 12 秒后的启动默认 / enable 拦截、disable 放行. 顶部 package compat 与应用声明分别影响结果; 不把 Activity setter false 当作应用级 opt-in. 宿主与 inrt 共 12 组通过 (PLATFORM_CONFIG; DEVICE-37), 最终镜像 / 真机及其他任务组合仍待验收 | 受保护任务中的下层 Activity 启动; 脚本与真实入口另行验证 | P2.5 | [ASM 证据](evidence/android-sdk-37-p25-asm-20260913.md) |
| `BACK_DISPOSITION_CONTROLS_BACK_INTERCEPTION` (454542168) | 输入法 `setBackDisposition` 决定是否拦截返回 (UNVERIFIED) | 无 (非输入法) | P2.15 | |
| `BONDING_APIS_REQUIRE_PRIVILEGED_PERMISSION` (401333807) | Bluetooth 绑定类 API 需要特权权限 (UNVERIFIED) | 脚本蓝牙 API (代码内无 `createBond`) | P2.15 | |
| `CHECK_ADD_MIRROR_DISPLAY_PERMISSION` (378605160) | 创建镜像虚拟显示需权限检查 (UNVERIFIED) | `ScreenCapturer.java:284-289` 经 MediaProjection 创建 `AUTO_MIRROR` 显示 | P2.15 | |
| `CHECK_SIZE_OF_INFLATED_CUSTOM_VIEWS` (270553691) | SystemUI 按通知发送 UID 检查自定义 View 层级中 ImageView 内存 (DEVICE-37 VERIFIED); 本镜像 warn / strip 为 2,000,000 / 5,000,000 bytes | 内建标准模板不走此分支; `notice(builder)` 可触发 | P2.9 | [实现与实际正反例](evidence/android-sdk-37-p29-20260913.md#3-通知自定义视图检查) |
| `COVER_INTENT_SENDER` (405995292) | 旧 `sendIntent` 重载在 target 37 默认被拦截, disable 后放行. API 37 options 重载与 ActivityResult 分开评估; 宿主可见时通过实际 AndroidX 回传, 后台发送负例被拦截 (DEVICE-37). 广播成功回调不追加 Activity 授权 (SOURCE) | 安装会话, 固定快捷方式, 抽屉 IntentSender | P2.5 | [P2.5 证据](evidence/android-sdk-37-p25-20260913.md) |
| `DEFAULT_ENABLE_CERTIFICATE_TRANSPARENCY` (407952621) | CT 默认开启, NSC 可按域关闭 (VERIFIED) | 私有 CA 场景 | P2.6 | |
| `DEPRECATE_SET_CONTENT_CAPTURE_ENABLED` (454307959) | `setContentCaptureEnabled(false)` 不再生效, 改用 `FLAG_SECURE` (VERIFIED) | 无 | P2.15 | |
| `DETECT_IMPLICIT_URI_PERMISSION_GRANT` (460838111) | StrictMode 检测隐式 URI 授权, Android 18 起限制 (VERIFIED) | `app.viewFile` / 分享路径 | P2.15 | |
| `DETECT_INSTANCE_COUNT_VIOLATIONS_WHEN_LISTENER_SET` (333566037) | StrictMode 实例计数检测 (UNVERIFIED) | 无 | P2.15 | |
| `DISABLE_OPT_OUT_UNIVERSAL_RESIZABLE_BY_DEFAULT` (447301631) | 大屏方向 / 尺寸豁免失效 (VERIFIED) | 无旧豁免; 默认 target 36 / 37 的方向对照和 UI 脚本分屏通过, Main / Edit / Log 状态与输入布局已修复; 悬浮 UI、外设和实体机继续开放 | P2.12 | [配置与输入证据](evidence/android-sdk-37-p212-20260914.md), [状态与布局回归](evidence/android-sdk-37-p212-followup-20260914.md) |
| `DONOT_STEAL_AUDIO_ON_GATT_CONN` (455594702) | BLE GATT 连接不再抢占音频路由 (UNVERIFIED) | 无 | P2.15 | |
| `ENABLE_DEFAULT_ENCRYPTED_CLIENT_HELLO` (419020719) | TLS ECH 默认开启, NSC `<domainEncryption>` 可关 (VERIFIED) | TLS 拦截代理环境 | P2.6 | |
| `ENABLE_FILE_SYSTEM_ACCESS` (364980165) | API 37 / WebView 145 实测: 关闭时 open 用 GET_CONTENT / 只读, save / directory 提前拒绝; 开启时 open 用 OPEN_DOCUMENT / 读写授权, save / directory 打开对应选择器. 两种状态均暴露 JS function; 写入与持久化待测 (DEVICE-37) | WebView 页面 | P2.11 | [24 场景与授权边界](evidence/android-sdk-37-p211-files-20260914.md) |
| `ENABLE_MATCH_NON_THREAD_LOCAL_NETWORKS` (349487600) | 本地网络匹配扩展到非 Thread 网络 (UNVERIFIED) | 本地网络权限判定 | P2.1 | |
| `ENABLE_RESTRICT_ASSIST_STRUCTURE` (437416500) | 助手结构访问受限 (UNVERIFIED) | 无 | P2.15 | |
| `ENABLE_USER_AGENT_REDUCTION` (371034303) | WebView 默认 UA 缩减 (VERIFIED); API 37 / WebView 145 的 target 36 默认不缩减, enable 缩减; target 37 默认缩减, disable 恢复. 独立预览插件按自身包生效 | 文档 / 预览 / `ui.webview` | P2.11 | [28/28 场景与 UA-CH 观测](evidence/android-sdk-37-p211-20260913.md) |
| `ENFORCE_STRICT_SQL_CHECKS` (484953293) | ContactsContract 查询严格 SQL 检查 (VERIFIED, 仅联系人) | `QuickContactBadgeAttributes.kt` | P2.15 | |
| `EXCLUDE_UNRELIABLE_STORAGE_VOLUMES` (391360514) | `MediaStore.getExternalVolumeNames()` 在应用 compat 与平台 flag 均开启时过滤目录以 `/mnt/` 开头的卷; 不改变 StorageManager 列表本身 (实现 VERIFIED) | 宿主无直接生产调用; 脚本可调用 Android API | P2.8 | [普通 SD 卡通过, 不可靠卷待测](evidence/android-sdk-37-p28-20260913.md) |
| `EXPAND_DEVICE_MANAGED_SCOPE` (451607072) | 设备策略范围变化 (UNVERIFIED) | 无 | P2.15 | |
| `FILTER_GENERIC_OTP` (437043173) | 普通 OTP 的即时查询 / 广播在 target 37 默认与 target 36 enable 下受限; WebOTP 在四组均受限. 当前提供者时间阈值为 3 小时并按分钟取整, 真实到期恢复待测 (SOURCE; DEVICE-37) | 脚本读短信 | P2.10 | [即时矩阵与通知保留项](evidence/android-sdk-37-p210-20260914.md) |
| `LOOPER_CLEARS_THREAD_INTERRUPTED` (458413887, overridable) | 消息派发时清除原生中断标记, ThreadCompat 保留脚本取消状态 (PLATFORM_CONFIG; DEVICE-37) | 脚本引擎中断语义 | P2.4 | 单项 / 组合 / target 37 默认实测通过, 见 P2.4 证据 |
| `MAKE_SOCKET_READ_BEHAVIOR_CONSISTENT` (383671392) | `BluetoothSocket` RFCOMM `read()` 断开返回 -1 (VERIFIED) | 脚本蓝牙 socket | P2.15 | |
| `OVERRIDDEN_THREAD_START_METHOD` (418924588) | 线程池跳过工作线程 start 覆写, run 仍执行; 直接 start 调用不变 (PLATFORM_CONFIG; DEVICE-37) | 宿主 Thread 子类及线程池工厂 | P2.4 | 宿主无 start 覆写; 单项 / 组合 / target 37 默认实测通过 |
| `PARCEL_HARDENING` (416031865) | 所测镜像中, 长度前缀 readValue 的字节数不匹配由 feature flag 与 compat 共同控制抛错 (PLATFORM_CONFIG; DEVICE-37) | 插件 IPC, Bundle extras | P2.7 | `a6d037516`; enable / disable / target 37 默认负例对照, 两组 11 插件最短链路通过; 见 P2.7 证据 |
| `REBOOT_MODEM_THROW_EXCEPTIONS` (476225321) | 电话调制解调器重启 API 抛异常 (UNVERIFIED) | 无 | P2.15 | |
| `RENDERSCRIPT_SCRIPTC_DEPRECATION_CHANGE_ID` (297019750) | RenderScript ScriptC 停用 (UNVERIFIED) | 无 (代码无 RenderScript) | P2.15 | |
| `REQUIRE_PERMISSIONS_FOR_DIRECTORY_QUERIES` (157720069) | ContactsProvider2 转发目录查询前, 按目录提供方 package / user 检查 compat 和提供方的联系人 / 通话记录权限 (实现 VERIFIED) | 宿主不是联系人目录提供方; 非 MediaStore / DocumentsContract 规则 | P2.8 范围核实 / P2.15 联系人 | [提供方范围](evidence/android-sdk-37-p28-20260913.md#1-三个开关的实际作用范围) |
| `RESTRICT_DATA_URI_COLUMNS` (437318646) | `ContactsContract.Data` 移除账户列 (VERIFIED) | `QuickContactBadgeAttributes.kt` | P2.15 | |
| `RESTRICT_DEPRECATED_DATA_BUILDER_CONSTRUCTORS` (424093067) | 弃用的 Data Builder 构造器受限 (UNVERIFIED) | 待定位 | P2.15 | |
| `SPLIT_SHOW_PASSWORDS_TO_TOUCH_AND_PHYSICAL` (417951523) | 显示密码设置拆分为触摸 / 物理键盘 (VERIFIED) | 无 | P2.15 | |
| `THROW_ERROR_FOR_WRITABLE_DCL` (463348571) | `System.load()` 可写文件抛 `UnsatisfiedLinkError` (VERIFIED) | OpenCV / 量化插件 staging | P2.3 | `a784273d2`; target 36 强制 / target 37 默认加载通过; 可写副本按预期被拒绝, 见 P2.3 实施记录 |
| `TRASH_BEHAVIOR_CHANGE` (461429441) | 当前镜像 target > 36 的调用者与 MediaProvider 自身 compat 共同决定 `.trash-storage` 路径; 单独切换宿主 compat 不能替代该组合 (DEVICE-37 VERIFIED) | 图片扫描后 MediaStore 回收 / 恢复; 应用回收站与历史独立 | P2.8 | [路径与恢复对照](evidence/android-sdk-37-p28-20260913.md#2-实际功能断言) |
| `USE_NEW_MESSAGEQUEUE` (421623328) | 无锁 MessageQueue (VERIFIED) | 脚本引擎 Looper | P2.4 | 单项 / 组合 / target 37 默认实测通过, 见 P2.4 证据 |
| `USE_NSD_PICKER_WHEN_NO_LOCAL_NET_PERMISSION` (490596785) | 无本地网络权限时 NSD 走系统选择器 (VERIFIED) | 无 (未用 `NsdManager`) | P2.1 | |
| `WEAROS_WALLPAPER_HANDLES_SCALING` (272527315) | Wear 壁纸缩放 (UNVERIFIED) | 无 | P2.15 | |

不经兼容框架门控但与本次相关的项: `ACCESS_LOCAL_NETWORK` 权限 (I1), 后台音频强化 (I2), `static final` 反射写入抛 `IllegalAccessException` (I4, VERIFIED, 无开关), 小组件 RemoteViews 内存上限 (I9, Bitmap 负例 VERIFIED, Icon 的 target 门槛存在公告 / 镜像差异, 见 P2.9), 密钥库上限, 应用内存上限 (I13), Advanced Protection (I14). 面向 38 的 `NETWORK_BLOCKED_WITHOUT_INTERNET_PERMISSION` 与 `enableSinceTargetSdk=10000` 的 4 项不在本次范围.

---

## 附录 C: 命令与参考

下列示例使用本工作区的 PowerShell. 先按 `adb devices -l` 替换序列号, 再从所测 APK / `dumpsys package` 确认包名; inrt 与各插件不能沿用宿主包名. `adb` / `aapt2` 若不在 PATH, 使用本机 SDK 的完整路径.

兼容开关每次仅接受一个 change 与一个 package. 先保存已有 override; 以下 `reset` 只适用于测试前没有该项 override 的包, 有则恢复原值. 普通设备上使用 debuggable 测试包; 具体可切换范围还受设备构建类型与 change 属性限制. 详见 [Compatibility framework tools](https://developer.android.com/guide/app-compatibility/test-debug).

```powershell
$Sdk37Serial = 'emulator-5554' # 替换为 adb devices 中已确认的 API 37 设备
$Sdk37Package = 'org.autojs.autojs6' # 替换为待测 APK 的 applicationId
$Sdk37Change = 'USE_NEW_MESSAGEQUEUE'
adb -s $Sdk37Serial shell getprop ro.build.version.sdk
adb -s $Sdk37Serial shell dumpsys package $Sdk37Package
adb -s $Sdk37Serial shell dumpsys platform_compat
adb -s $Sdk37Serial shell am compat enable $Sdk37Change $Sdk37Package
adb -s $Sdk37Serial shell am compat disable $Sdk37Change $Sdk37Package
adb -s $Sdk37Serial shell am compat reset $Sdk37Change $Sdk37Package
```

上面展示 enable / disable / reset 三种独立操作; 实际测试在每次切换后重新运行用例并记录, 不连续执行三条后再测试. 多个开关应逐个调用, 例如组合测试的启用步骤:

```powershell
$Sdk37Changes = @('USE_NEW_MESSAGEQUEUE', 'LOOPER_CLEARS_THREAD_INTERRUPTED', 'OVERRIDDEN_THREAD_START_METHOD')
foreach ($Sdk37Change in $Sdk37Changes) {
    adb -s $Sdk37Serial shell am compat enable $Sdk37Change $Sdk37Package
    if ($LASTEXITCODE -ne 0) { throw "Cannot enable $Sdk37Change for $Sdk37Package" }
}
# 完成用例后逐项恢复原 override; 原来没有覆盖时用 reset.
```

P2 中简写 `am compat enable CHANGE` 均表示用上述形式补全 adb, serial 与实际 package. `reset-all` 仅用于原先没有其它测试 override 的专用包. 开发者选项 > App Compatibility Changes 可检查同一组变更; `dumpsys` 的静态列表不是功能通过证据.

Android 17 本地网络权限, 必须先在 manifest 声明并安装 target 37 包:

```powershell
adb -s $Sdk37Serial shell pm revoke $Sdk37Package android.permission.ACCESS_LOCAL_NETWORK
# 运行拒绝场景.
adb -s $Sdk37Serial shell pm grant $Sdk37Package android.permission.ACCESS_LOCAL_NETWORK
# 重启待测功能, 运行授权场景; 另测真实 UI 申请及再次撤销.
```

Android 16 的 `RESTRICT_LOCAL_NETWORK` 与 `NEARBY_WIFI_DEVICES` 测试流程按 [本地网络权限指南](https://developer.android.com/privacy-and-security/local-network-permission) 执行; 不把 API 37 的新权限 grant 命令用于 API 36.

- 音频: `adb -s $Sdk37Serial shell cmd audio set-enable-hardening throw`, 状态通过 `adb -s $Sdk37Serial shell dumpsys audio` 与 logcat 检查. 这是全设备诊断模式, 会覆盖默认 target / 闹钟差异; `disable` 是关闭限制, 不等于恢复出厂默认. 在可恢复的专用 AVD 记录并恢复原状态, 默认规则的验收另跑一轮.
- 内存: `adb -s $Sdk37Serial shell am memory-limiter status`; 对具体 PID 使用 `manual <pid> <MB>` 测试, 完成后 `manual <pid> none` 恢复该 PID 的默认限制. `ignore all` 影响全设备, 不作为常规迁移步骤.
- StrictMode: debug 构建中以 API >= 36 调用 `detectBlockedBackgroundActivityLaunch()`, API >= 37 调用 `detectImplicitUriPermissionGrant()`, 配合 `penaltyLog()` / `build()` 安装策略; 不在旧系统无条件链接调用新方法.
- 短信: `adb -s $Sdk37Serial emu sms send 10086 "OTP 123456"` (仅模拟器); 记录实际 OTP 检测与 3 小时窗口, 不把未检测为 OTP 的普通短信视为保护机制失效.
- APK 核对 (本机): `& 'E:/.android/sdk/build-tools/37.0.0/aapt2.exe' dump badging '<实际 APK 路径>'`. 检查 `compileSdkVersion`, `targetSdkVersion`, `sdkVersion`, package; inrt / 插件 / 用户生成 APK 分别保留结果.
- lint: P0.2 先落实有效入口, 再独立执行 app debug 与 app / inrt release 检查. 不直接依赖当前被禁用的 `lintVitalAnalyze*Release`; 证据须包含新报告及任务执行状态.
- 单元测试: `./gradlew :app:testAppDebugUnitTest testDebugUnitTest :app:compileAppDebugAndroidTestKotlin`; 核查任务覆盖列表与测试报告, 不带 `--tests` 过滤时才代表全量宿主单元测试.
- 历史探针任务: `./gradlew --offline --continue --console=plain "-Pkotlin.daemon.jvmargs=-Xmx6g" :app:compileAppDebugKotlin :app:compileAppDebugJavaWithJavac compileDebugKotlin compileDebugJavaWithJavac`. 这些任务不包括完整 inrt / release 验证. 恢复临时 SDK 配置时只恢复本次修改的键, 保留构建自增 / 用户修改的其它属性.
- 模拟器: 先记录既有 adb 设备; 若启动专用 AVD, 用 `-no-snapshot-save`, 后台辅助进程使用隐藏窗口. 用 `adb -s $Sdk37Serial shell getconf PAGE_SIZE` / `getprop ro.build.version.sdk` 记录页大小与 API, 只关闭本次启动的模拟器.

官方参考按主题使用: [目标版本变更](https://developer.android.com/about/versions/17/behavior-changes-17), [所有应用变更](https://developer.android.com/about/versions/17/behavior-changes-all), [迁移清单](https://developer.android.com/about/versions/17/migration), [MessageQueue](https://developer.android.com/about/versions/17/changes/messagequeue), [大屏限制](https://developer.android.com/about/versions/17/changes/ff-restrictions-ignored), [后台 Activity 安全](https://developer.android.com/guide/components/activities/secure-bal), [AGP / API 兼容表](https://developer.android.com/build/releases/about-agp), [Play 目标版本要求](https://developer.android.com/google/play/requirements/target-sdk). 具体核验范围与取页失败见 [复核记录](evidence/android-sdk-37-review-20260913.md).

---

## 附录 D: 证据等级与验收方式

| 标签 | 可以证明 | 不能证明 |
| --- | --- | --- |
| `SOURCE` | 源码 / 配置 / 脚本存在, 结构符合设计 | 编译或行为正确 |
| `PLATFORM_CONFIG` | 指定镜像中存在某 change / ID / 默认门控配置 | 行为语义, 某包实际执行路径, 功能通过 |
| `JVM` | Android-free 逻辑的单元测试 (JUnit4, 无 Robolectric) | 真机行为 |
| `ANDROID_COMPILE` | 指定 variant 的 Kotlin / Java 编译任务通过 | 完整 APK, release R8, lint, 未编译的 variant, 运行时行为 |
| `ANDROID_BUILD` | `assemble*` / `generateRuntimeKit` 通过且 16 KB 门禁通过 (或 CI 绿) | 真机行为 |
| `LINT` | 检查未被跳过, 新报告与基线 diff 已记录, 无新增 error / fatal | `SKIPPED` / `abortOnError=false` 下仅凭构建成功不能证明无问题; 不能证明运行时行为 |
| `DEVICE` | 指定设备或 AVD 上列明用例通过, 记录 API 级别、页大小与真机 / 模拟器类型 | 未列出的系统或设备; 旧系统证据不能证明 Android 17 行为 |
| `DEVICE-37` | `AVD_API_37` (或 Android 17 真机, 注明型号) 上, 注明 targetSdk (36 + 覆盖 / 37) 与打开的兼容开关, 冒烟通过 | 未列出设备 / 开关组合 |
| `DEVICE-16K` | `aj6-ps16k-37-x86_64` 上安装无兼容模式警告 | - |
| `DOCS` | 本条所要求的文档 / changelog / d.ts 已更新, 需要生成或递增的产物已完成 | 未执行的构建 / 设备用例; 纯 roadmap 修订不要求版本递增 |
| `GATE` | `tools/sdk37/run_gate.py` 在指定设备上 overall PASS: 产物 badging / SHA-256 与安装身份一致, 所选无人值守套件逐方法完成, SKIP 均有原因 | 真实 LAN 拓扑、可听性、OEM 桌面、人工 UI, 以及未被选中的套件; 模拟器 PASS 不证明真机 OEM 行为 |

条目勾选时在其后追加证据, 格式示例: `[x] ... (DEVICE-37: AVD_API_37, target 37 覆盖包, compat USE_NEW_MESSAGEQUEUE on, 12/12 用例; commit abc1234)`.

---

## 附录 E: 插件舰队矩阵

数值列与 "脏" 列于 2026-09-15 用 `py tools/sdk37/fleet.py table` 重新扫描 (硬编码字面量覆盖属性文件时标注 "硬编码"); "暴露" 列取自各仓库 manifest 扫描 (2026-09-13, 2026-09-15 复核三行): 前台服务类型, 悬浮窗 (`SYSTEM_ALERT_WINDOW`), `INTERNET`, `MANAGE_EXTERNAL_STORAGE`, 以及已知的进程模型; "脏" 为初稿快照中的未提交文件数, 每仓开工时刷新. SDK 数值先来自属性文件, 已核实的构建脚本覆盖值在下表修正, 其余均以实际产物最终确认. 阶段与分组对应 P5.x. "证据" 列按 `C / T / V / D` 分别回填状态、提交 hash 与设备 (见 P5), 不将整个分组一次性勾选.

| 仓库 | compile / target / min | 平台插件 | 进程模型 | 暴露 | 脏 | 阶段 | 证据 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| OpenCV | 37 / 37 / 24 | 1.8.0 | 宿主进程 (staging 加载) | 动态加载 (宿主侧) | 0 | P5.1 | `1101000` v1.1.4 (build 22), AVD_API_37.1 instrumentation 1 项, 2026-09-15 |
| Image-Quantization | 37 / 37 / 24 | 1.8.0 | 宿主进程 (staging 加载) | 动态加载 (宿主侧) | 0 | P5.1 | `24d1ea8` v1.0.3 (build 22), AVD_API_37.1 instrumentation 16 项 (排除环境固定金样测试), 2026-09-15 |
| Paddle-OCR-PP-OCRv3 | 37 / 37 / 24 | 1.8.0 | 插件进程 / 用户 APK | Binder, 原生库 | 0 | P5.2 | `47d795c` v1.0.5 (build 22), AVD_API_37.1 instrumentation 1 项, 2026-09-15 |
| Paddle-OCR-PP-OCRv4 | 37 / 37 / 26 | 1.8.0 | 插件进程 / 用户 APK | Binder, 原生库 | 0 | P5.2 | `e679494` v1.0.4 (build 22), AVD_API_37.1 instrumentation 1 项, 2026-09-15 |
| Paddle-OCR-PP-OCRv5 | 37 / 37 / 26 | 1.8.0 | 插件进程 / 用户 APK | Binder, 原生库 | 0 | P5.2 | `eab79a0` v1.0.4 (build 24), AVD_API_37.1 安装 + target 核对, 2026-09-15 |
| Paddle-OCR-PP-OCRv6 | 37 / 37 / 26 | 1.8.0 | 插件进程 / 用户 APK | Binder, 原生库 | 0 | P5.2 | `160aaa5` v1.0.4 (build 26), AVD_API_37.1 安装 + target 核对, 2026-09-15 |
| Rapid-OCR-PP-OCRv3 | 37 / 37 / 24 | 1.8.0 | 插件进程 / 用户 APK | Binder, 原生库 | 0 | P5.2 | `815893d` v1.0.5 (build 25), AVD_API_37.1 instrumentation 1 项, 2026-09-15 |
| Rapid-OCR-PP-OCRv6 | 37 / 37 / 24 | 1.8.0 | 插件进程 / 用户 APK | Binder, 原生库 | 0 | P5.2 | `0d535dc` v1.0.5 (build 27), AVD_API_37.1 instrumentation 1 项, 2026-09-15 |
| APK-Builder-Template | 37 / 37 / 24 | 1.8.0 | 插件进程 (打包) | 模板 targetSdk 期望, CI 断言 | 0 | P5.2 | `41f06fd` v1.0.3 (build 44, 未发布), AVD_API_37.1 三个打包示例 3 / 3 PASS, CI 模板 targetSdkVersion 断言, 2026-09-15 |
| NodeJs-Runtime | 37 / 37 / 24 | 1.8.0 | `:nodejs_runtime` | INTERNET / ACCESS_LOCAL_NETWORK, MANAGE_EXTERNAL_STORAGE, 动态加载 (`.node` 经 dlopen), `libnode.so` dlopen; 2026-09-14 起另有宿主 uid 直接 exec 的终端启动器 `libnodexe.so` (插件 nativeLibraryDir) 与宿主解包的 npm / corepack 资产 (仅 JS) | 0 | P5.3 | `861e084` v1.5.3 (build 174), 62 项完整套件, API 37 AVD, 2026-09-16; [收尾证据](evidence/android-sdk-37-p5-fleet-final-20260916.md) |
| Bun-Runtime | 37 / 37 / 33 | 1.8.0 | `:bun_runtime` (exec) | INTERNET / ACCESS_LOCAL_NETWORK, 动态加载 | 0 | P5.3 | `4a7d654` v0.2.4 (build 111), 完整套件 18 项 + 16 KB 条件用例 1 项, API 37 AVD, 2026-09-16; [收尾证据](evidence/android-sdk-37-p5-fleet-final-20260916.md) |
| Python-Runtime | 37 / 37 / 24 | 1.8.0 | `:python_runtime` | INTERNET / ACCESS_LOCAL_NETWORK, Chaquopy; SDK 消费点已读 versions.sdkVersion* | 0 | P5.3 | `808e716` v0.5.4 (build 109), 2 项完整套件, API 37 AVD, 2026-09-16; [收尾证据](evidence/android-sdk-37-p5-fleet-final-20260916.md) |
| Lua-Runtime | 37 / 37 / 24 | 1.8.0 | `:lua_runtime` | Binder | 0 | P5.3 | `050ca26` v0.1.2-rc.2 (build 77), AVD_API_37.1 instrumentation 5 项, 2026-09-15 |
| QuickJS-Runtime | 37 / 37 / 24 | 1.8.0 | `:quickjs_runtime` | Binder | 0 | P5.3 | `947e4ab` v1.0.3 (build 14), AVD_API_37.1 instrumentation 3 项, 2026-09-15 |
| TypeScript-Engine | 37 / 37 / 24 | 1.8.0 | `:typescript_*` | Binder | 0 | P5.3 | `831f64e` v0.6.3 (build 76), AVD_API_37.1 instrumentation 94 项 (排除环境固定金样测试), 2026-09-15 |
| Java-Runtime | 37 / 37 / 24 | 1.8.0 | 插件进程 | 动态加载 (DEX) | 0 | P5.3 | `9ef0b46` v0.8.2-m9 (build 56), AVD_API_37.1 instrumentation 15 项 (排除 API 34 / 36 专用证据测试), 2026-09-15 |
| Kotlin-Runtime | 37 / 37 / 26 | 1.8.0 | 插件进程 | 动态加载 (DEX) | 0 | P5.3 | `3038600` v0.7.2-m10 (build 27), AVD_API_37.1 instrumentation 2 项 (排除 API 34 / 36 专用证据测试), 2026-09-15 |
| DEX-Compiler | 37 / 37 / 24 | 1.8.0 | 插件进程 | 动态加载 (DEX 产物) | 0 | P5.3 | `8383eeb` v1.1.2 (build 44), AVD_API_37.1 instrumentation 2 项, 2026-09-15 |
| R8-Compiler | 37 / 37 / 24 (硬编码) | 1.8.0 | 插件进程 | 动态加载 (DEX 产物), 固定 android-36.jar | 0 | P5.3 | `a2f4f75` v0.2.2-provider-dev (build 20), AVD_API_37.1 instrumentation 3 项, 2026-09-15 |
| MCP-Server | 37 / 37 / 24 | 1.8.0 | 插件进程 | INTERNET / ACCESS_LOCAL_NETWORK (本地网络服务端), `specialUse` 前台服务 | 0 | P5.4 | `d4c3796` v1.0.2 (build 60), 完整套件 35 项 + opt-in 终止 1 项, API 37 AVD, 2026-09-16; [收尾证据](evidence/android-sdk-37-p5-fleet-final-20260916.md) |
| Screen-Color-Picker | 37 / 37 / 24 | 1.8.0 | 插件进程 | `mediaProjection` 前台服务, 悬浮窗 | 0 | P5.4 | `c2625fe` v1.1.3 (build 15), 10 项 + 真实取色, API 37 AVD, 2026-09-16; [收尾证据](evidence/android-sdk-37-p5-fleet-final-20260916.md) |
| Three-Ember-Player | 37 / 37 / 24 | 1.8.0 | 插件进程 | `mediaPlayback` 前台服务, INTERNET, 后台音频 | 0 | P5.4 | `5cabf8b` v3.1.2 (build 27), 35 项 + throw 模式真实后台播放, API 37 AVD, 2026-09-16; [收尾证据](evidence/android-sdk-37-p5-fleet-final-20260916.md) |
| Three-Terra-Player | 37 / 37 / 24 | 1.8.0 | 插件进程 | `mediaPlayback` 前台服务, INTERNET, 后台音频 | 0 | P5.4 | `cf1ed3d` v1.6.2 (build 37), 48 项 + throw 模式真实后台播放, API 37 AVD, 2026-09-16; [收尾证据](evidence/android-sdk-37-p5-fleet-final-20260916.md) |
| Three-Stone-AI | 37 / 37 / 24 | 1.8.0 | 插件进程 | INTERNET, CPU / GPU (无 NPU), 内存限制器真实 CPU 推理 | 0 | P5.4 | `48a1b59` v1.1.4 (build 191), 3 项, 真实模型 / 已启用限制器, API 37 AVD, 2026-09-16; [收尾证据](evidence/android-sdk-37-p5-fleet-final-20260916.md) |
| HTML-Previewer | 37 / 37 / 24 | 1.8.0 | 插件进程 | INTERNET, WebView | 0 | P5.4 | `ce965bf` v1.1.2 (build 22), AVD_API_37.1 instrumentation 42 项, 2026-09-15 |
| Markdown-Previewer | 37 / 37 / 24 | 1.8.0 | 插件进程 | INTERNET, WebView | 0 | P5.4 | `ed7c37b` v1.2.2 (build 18), AVD_API_37.1 instrumentation 28 项, 2026-09-15 |
| Offline-Docs | 37 / 37 / 24 | 1.8.0 | 插件进程 | WebView (回环) | 0 | P5.4 | `2e253cd` v6.8.2 (build 42), AVD_API_37.1 instrumentation 2 项, 2026-09-15 |
| Ace-Editor | 37 / 37 / 24 | 1.8.0 | 插件进程 + LuaLS 独立进程 | INTERNET, WebView, LSP (回环) | 0 | P5.4 | `271e3c9` v1.7.2 (build 99), AVD_API_37.1 instrumentation 23 项, 2026-09-15 |
| MLKit-Barcode | 37 / 37 / 24 | 1.8.0 | 插件进程 | Binder, INTERNET | 0 | P5.5 | `57e88a0` v1.0.4 (build 17), AVD_API_37.1 instrumentation 1 项, 2026-09-15 |
| MLKit-OCR | 37 / 37 / 24 | 1.8.0 | 插件进程 | Binder | 0 | P5.5 | `61970ac` v1.0.3 (build 16), AVD_API_37.1 instrumentation 1 项, 2026-09-15 |
| Yolo-NCNN | 37 / 37 / 24 | 1.8.0 | `:provider` | Binder | 0 | P5.5 | `d4e9bbb` v0.1.3 (build 36), AVD_API_37.1 instrumentation 5 项, 2026-09-15 |
| MediaInfo | 37 / 37 / 24 | 1.8.0 | 插件进程 | Binder | 0 | P5.5 | `48bb6fb` v2.1.3 (build 51), AVD_API_37.1 instrumentation 16 项, 2026-09-15 |
| OpenCC | 37 / 37 / 24 | 1.8.0 | 插件进程 | Binder, INTERNET | 0 | P5.5 | `5c04062` v1.4.2 (build 103), 完整套件 5 项 + 条件阶段 7 次, 覆盖 9 个用例, API 37 AVD, 2026-09-16; [收尾证据](evidence/android-sdk-37-p5-fleet-final-20260916.md) |
| ImGui | 37 / 37 / 24 | 1.8.0 | 插件进程 (`:privilege_broker`) | Binder | 0 | P5.5 | `ee9054d` v1.9.3 (build 15), AVD_API_37.1 instrumentation 5 项, 2026-09-15 |
| Archive-Manager | 37 / 37 / 24 | 1.8.0 | 插件进程 | 存储卷 / MediaStore | 0 | P5.5 (仅 target) | `4b308f9` v2.22.3 (build 106), AVD_API_37.1 instrumentation 178 项 (排除环境固定金样测试), 2026-09-15 |
| Image-Tools | 37 / 37 / 24 | 1.8.0 | 插件进程 | 存储 | 0 | P5.5 | `9210c49` v1.2.1 (build 14), AVD_API_37.1 instrumentation 2 项, 2026-09-15 |
| Image-Viewer | 37 / 37 / 24 | 1.8.0 | 插件进程 | 存储 | 0 | P5.5 | `eb0fde2` v1.3.1 (build 13), AVD_API_37.1 instrumentation 48 项, 2026-09-15 |
| File-Inspector | 37 / 37 / 24 | 1.8.0 | 插件进程 | 存储 | 0 | P5.5 | `7035ef1` v1.1.1 (build 17), AVD_API_37.1 instrumentation 18 项, 2026-09-15 |
| APK-Inspector | 37 / 37 / 24 | 1.8.0 | 插件进程 | 存储 | 0 | P5.5 | `59387a6` v1.2.1 (build 39), AVD_API_37.1 instrumentation 2 项, 2026-09-15 |
| Pinyin | 37 / 37 / 24 | 1.8.0 | 插件进程 | - | 0 | P5.5 | `2527cd5` v1.0.3 (build 18), AVD_API_37.1 instrumentation 7 项, 2026-09-15 |
| Pinyin4j | 37 / 37 / 24 | 1.8.0 | 插件进程 | - | 0 | P5.5 | `28507f5` v1.3.2 (build 25), AVD_API_37.1 instrumentation 2 项, 2026-09-15 |
| Accessibility-Compat | 37 / 37 / 24 | 1.8.0 | 插件进程 | 2026-09-15 起: 无障碍服务 (SelectToSpeak 身份壳, `packageNames=com.tencent.mm`, 未声明 isAccessibilityTool), WRITE_SECURE_SETTINGS, Shizuku 查询, 与宿主服务状态同步的 receiver | 0 | P5.5 (仅 target; 另见 P5.5 专项) | `d822ccd` v1.3.1 (build 18), 12 项 + 保护模式说明 / 安全设置重绑, API 37 AVD, 2026-09-16; [收尾证据](evidence/android-sdk-37-p5-fleet-final-20260916.md) |

---

## 附录 F: 后续建议 (不阻塞本 Roadmap)

- Android 17 新 MessageQueue 生效时, 私有 `mMessages` 字段虽然保留但恒为 null, 不能据此判断队列内容; 宿主使用公开 Handler / Looper / IdleHandler, Espresso 已为 3.7.0. Android 17 上 target 37 应用反射写入 `static final` 字段会抛 `IllegalAccessException`, JNI 写入也可能崩溃. 两项限制分别来自 [MessageQueue 官方说明](https://developer.android.com/about/versions/17/changes/messagequeue) 与 [target 37 官方行为变更](https://developer.android.com/about/versions/17/behavior-changes-17), 不泛化为所有旧 target 的反射读取限制. 当前 util.java / threads 文档无受影响示例, P4 汇总升级说明时提示用户脚本; [P2.4 评估](evidence/android-sdk-37-p24-20260913.md#start-覆写与非-sdk-接口复核).
- androidx.core 1.19.0 (2026-06-03) 要求 compileSdk 37, 且 `core-ktx` 已并入 `core`; 当前 `gradle/libs.versions.toml` 的 `core-ktx = "1.15.0"` 是为 AGP 8.2.2 时代保留的过期钉子, P1 之后可在独立的依赖升级中解除 (D7).
- androidx.activity 1.14.0-alpha02 起 Compose 系列库要求 compileSdk 37.1 (次级 SDK); 届时需要 `compileSdk { version = release(37) { minorApiLevel = 1 } }` 语法, `Versions.kt` 与 `autojs6-platform-versions` 数据表都要支持 "37.1" 形式. 本机尚未安装 `platforms;android-37.1`.
- Android 18 起 `ACTION_SEND` / `ACTION_IMAGE_CAPTURE` 不再隐式授予 URI 权限, P2.15 的 `detectImplicitUriPermissionGrant()` 检查结果可提前修正.
- 本机 `android-36/android.jar` 有 4 个 `android/hardware/fingerprint/*.class`, `android-37.0/android.jar` 为 0 (SOURCE: [复核记录](evidence/android-sdk-37-review-20260913.md)); 这证明 SDK 编译符号移除, 不等于设备运行时一定无此类. `InputMethodManager.SHOW_IMPLICIT` 之外的弃用由 lint 逐版跟踪.
- APK 签名方案 v3.2 (混合后量子, ML-DSA) 对内置 APK 打包器 / `modules/apk-signer` 是可选能力, 与本次无关.
- Advanced Protection Mode 对无障碍服务的限制若被实测确认, 需要在用户文档 FAQ 与插件中心 "无障碍兼容" 页面长期维护说明.
- P2.3 在 API 24 发现既有 JAR 编译器边界: 本地 D8 引用系统尚无的 `java.nio.file.Paths`, legacy dx 回退拒绝 Java 8 class major 52; Java 6 major 50 夹具可正常转换与加载. 后续独立修复旧系统编译器兼容性及失败诊断, 不因本次原始 DEX 目录修复或 API 37 冒烟通过而宣称此限制已解除. 见 [P2.3 证据](evidence/android-sdk-37-p23-20260913.md#jar-夹具与既有限制).
- 旧 `PythonRuntimeRealPluginDiagnosticTest` 的 R2 诊断对当前 0.5.2 插件返回 `INVALID_REQUEST/SESSION_OPEN`, Parcel enable / disable 均可复现. 源码显示它协商新协议后调用不带 host broker 的旧 openSession, provider 在协议 1.5+ 要求 broker. 后续按该诊断的协议验收范围维护; P2.7 使用当前生产 PythonPluginScriptEngine 已完成三个条件的实际 CPython 往返, 没有删除或掩盖旧诊断失败. 见 [失败探索](evidence/android-sdk-37-p27-20260913.md#5-失败探索与处理).

---

## 会话记录

### 2026-09-13 初稿

- 完成: 宿主与 42 个插件仓库的 SDK / 工具链 / manifest 事实收集; compileSdk 37 探针编译 (0 错误, 1 处新增弃用) 与 36 基线对照; `AVD_API_37` 的 `dumpsys platform_compat` 清单 (36 项门控) 与官方文档核对; 固定决策 D1-D4 (维护者以选择题拍板: 范围含全部插件, 两步走, inrt 同步升到 37, 允许探针编译) 与派生约定 D5-D11; 本 Roadmap 初稿与两份证据文件.
- 关键发现: 编译层面几乎零成本 (AGP 9.4.0 已满足 API 37, 探针范围内编译通过); 风险集中在 targetSdk 37 的运行时行为: 本地网络运行时权限 (含入站 accept, 影响 VS Code 扩展 / MCP / 脚本 socket), 后台音频 while-in-use 要求, `System.load` 只读要求 (初稿称 staging 未设只读, P2.3 复核修正为已有尝试但未检查失败), 无锁 MessageQueue 与线程中断语义, `IntentSender` 的 BAL opt-in, CT / ECH 默认开启; inrt 从 29 跨到 37 需要单独的存储引导与旧版升级保护.
- 环境变化: 本会话为取兼容清单启动并关闭了 `AVD_API_37` (`-no-snapshot-save`, 未改动 AVD 状态); 会话开始时列出的 API 27 模拟器 (`emulator-5554`) 在启动新模拟器前已不在 `adb devices` 中, 原因未查.
- 未做: P0.2 覆盖开关与脚本, P1 起全部代码改动.
- 下次会话建议起点: P0.2 (覆盖开关, 供 P2 全程使用) 与 P1 (compileSdk 37, 改动极小) 可同一会话完成; 随后 P2.1 / P2.3 / P2.4 三项影响最大且互不依赖, 可并行推进.

### 2026-09-13 执行条件复核

- 保留 D1-D4 与原探针历史结果; 修正 "全仓编译" 的证据范围, 增加 ANDROID_COMPILE / PLATFORM_CONFIG 等级, 去掉由页面缺席推断 "明确未变" 的结论.
- 补齐 P0.2 的有效 lint 入口与 release 覆盖拒绝要求, 修正 adb 多 change / 缺 package 示例及 Gradle 通配任务路径; 音频默认规则与设备级诊断模式分开验收.
- 修正 P2.5 的 creator / sender / broadcast 角色, P2.6 的用户 CA / CT / ECH 前提, P2.1 的权限声明与跨 profile 回环边界; 增加 a11y IME 事件验证及 P2.16 的 inrt 切换前适配.
- 核实插件实际配置例外: R8-Compiler 的 SDK 硬编码与固定 android-36.jar, Paddle v4 / v5 / v6 的 minSdk 26; SDK 日志分类遵循当前 AGENTS.md, 移除非必需的 Bun 平台插件顺带升级.
- 本次仅修订规划与证据, 未执行 Gradle 构建 / lint / 设备测试, 未将 P0.2 或 P1-P6 标为完成. 检查清单与来源见 [复核记录](evidence/android-sdk-37-review-20260913.md).

### 2026-09-13 P2.3 实施

- 开始前检查全部待提交工作, 既有 MCP 截图改动由同时工作的会话提交为 `dd521ed5d`, 确认干净后继续 SDK 适配. 期间新出现的 MCP / Node bridge 改动不混入本轮逻辑提交.
- `a784273d2`: OpenCV / ImageQuant 原生库临时文件, 发布文件与旧缓存均强制验证只读; 38 项 JVM 测试通过, API 37 target 36 强制 / target 37 默认及 API 24 的 JNI 测试通过. 修正初稿 "未设只读" 为 "已有尝试但忽略失败结果".
- 旧系统回归发现 API 24 原始 DEX 优化输出与输入重合并复现; API 24 / 25 改用独立目录, API 26+ 保持原路径. 新增原始 DEX 设备测试及实际 DEX / JAR 两次加载脚本. 既有 Java 8 JAR 编译器限制另列附录 F.
- 完成范围与 APK / 设备 / 日志详见 [P2.3 实施记录](evidence/android-sdk-37-p23-20260913.md). 正常 target 保持 36 / 29, P2.3 已勾选; 其余 P2 项目与 P3-P6 待实施, P1 的远程 CI 与既有 lint 状态保持原记录.

### 2026-09-13 P2.4 实施

- 完成三个 Looper / 线程开关的单项、组合与真实 target 37 默认行为验证, 增加 14 项设备测试及专用模拟器复跑工具. 组合与 target 37 的持续 interval 均运行 2 分钟, 完整 a11y 冒烟四组各 53/53.
- `705cb04ad`: 基线发现取消远期定时器后脚本不退出, 修复 Timer 实际工作登记、唤醒与线程退出清理. API 24 回归、6 项 Looper JVM 测试及 app / inrt debug 构建通过.
- 复核 start 的声明与继承关系, 没有宿主 Thread.start 初始化需要迁移. 调试器确认 ThreadCompat 取消状态能跨平台原生标记清除而保留; 平台反射限制与 util.java 文档评估写入附录 F.
- P2.4 已勾选, 正常 target 仍为 host 36 / inrt 29. 同时进行的 MCP / Node 改动由对应会话维护, 不混入本轮逻辑提交. [完整证据](evidence/android-sdk-37-p24-20260913.md).

### 2026-09-13 P2.1 实施

- 会话开始时宿主为干净的 `cc1383376`, 本轮按逻辑分别提交代码、关联仓库与证据. `d9465be13` 接入 target 37 本地网络权限流程、错误诊断及回归测试; 正常 target 36 / 29 包不声明新权限.
- 验证拒绝 / 授予、覆盖升级、Nearby Devices 组复用及真实设置页重试; API 37 拒绝组 9 通过 / 2 跳过、恢复组 11/11, API 24 的四种 host / inrt / target 组合各 11/11. 修复 Node 整次调用超时被误报取消, 主动取消回归仍通过.
- Documentation、Offline-Docs、DTS 与 Ace 已同步提交. d.ts 导出遇到 `android-37` / `android-37.0` 差异, 在 android-dts-generator `525bebd` 中改为读取 AGP 实际 bootClasspath, 严格导出与类型校验通过.
- [实施记录与机器索引](evidence/android-sdk-37-p21-20260913.md) 保留完整构建 / 设备条件及限制. P2.1 的真机拓扑、跨 profile / UID、完整 MqttService 与最终 inrt 网络验收仍未勾选; P5 插件 SDK 迁移未因本轮内容同步而完成.
- 已停止本轮两个只读临时 AVD 与 LAN fixture, 清理自建转发. 下一项可推进 P2.2 后台音频; P2.1 剩余设备项在具备相应环境时继续补验.

### 2026-09-13: P2.2 默认行为对照与恢复指引

- 完成 12 组默认系统状态记录, 保留 11 组原预期通过和 1 组媒体音量豁免差异. target 37 同一服务在可见界面重新启动后, WIU 与音频焦点恢复已验证; Google TTS 的实际引擎进程另行记录.
- 宿主帮助 11 份资源与 10 语言 changelog 已提交 `c703a7953`; API 文档与生成物已提交 `7769e6d`, 文档生成器防止插件版本回退的修复已提交 `2d0ed0d`. 离线插件 `ad2f7cc` 保留 6.8.1 发行版, 内容版为 6.8.0, 构建号 33 与 Git 提交数一致.
- 宿主 / inrt debug 构建和 release Manifest 检查通过, 正常 target 仍为 36 / 29; 文档 128 模块 freshness, 离线插件 debug / release APK 内容门禁通过. 详细范围见 [P2.2 实施记录](evidence/android-sdk-37-p22-20260913.md).
- P2.2 仍需开机与完整脚本调度链, 真机可听性, API 35 组合及有效 `throw` 模式验收. 本轮开机启动 ANR, `throw` 命令未改变覆盖状态, 均未勾选完成. 可继续推进 P2.5 后台活动启动, 同时保留上述设备门禁.

### 2026-09-13: P2.5 发送方适配与实际启动对照

- 取色器增加按系统版本分支的可见发送方 options, 生产改动与 10 种语言 changelog 已提交 `1c284c9e0`. 安装器保留创建方授权, 广播 / Service 回调没有扩大 Activity 授权. Debug 增加 BAL StrictMode 日志与真实 UID 探针.
- `COVER_INTENT_SENDER` 的两方向对照通过: target 37 默认拦截 / disable 放行, 同源 target 36 默认放行 / enable 拦截. 可见 AndroidX ActivityResult 在 enable 下实际回传, 后台负例与真实 Rhino 脚本的 SYSTEM_ALERT_WINDOW 权限例外已分别记录.
- 通过真实前台安装确认安装了取色器插件, 并从实际抽屉开关启动到插件权限说明页. 完整取色与颜色回传未计通过. 无资格后台安装确认在 target 36 / 37 均被拦截, 属于当前安装路径已有的恢复缺口.
- ASM 的默认 / enable / disable 用例均放行, 保留最初错误预期及后续生命周期记录, 未把兼容配置 dump 当成限制生效证据. 下一步需构造能区分前台任务 / 调用链例外的对照.
- 宿主 target 37 调试包及正常宿主 / inrt 调试包构建通过, 16 KB 对齐检查通过; release 主 manifest 不含探针. [P2.5 实施记录与机器结果](evidence/android-sdk-37-p25-20260913.md) 保留 34 次探针记录, 包括超时, 辅助观测与错误预期, 不将它们统一计为通过.
- 后续优先补后台安装确认的恢复入口, 再关闭 ASM 与真实 Launcher / a11y / 旧系统 / inrt 设备项. 正常配置保持 compileSdk 37 / target 36 (宿主) / target 29 (inrt), P2.5 与 P3 的整体门禁仍开放.

### 2026-09-13: P2.5 后台安装确认恢复

- 关闭后台安装恢复条目. 代码 / 测试 / 多语言日志提交 `05ad029fd`, 复跑工具提交 `faf16a76a`. 系统先发送私有广播, 保存状态后再提供按会话区分的通知和返回应用提示. 正式安装页在可见时继续系统确认, 安装结果仍以 PackageInstaller 最终回调为准.
- 确认 token 由安装会话仍持有的广播 token 引用, 磁盘仅保存 filter identity, 不序列化系统 Intent 中的 Binder / Parcelable. 进程退出后可重新查找原 token; force-stop 使其失效时提供重新打开安装包的提示, 不混称为普通进程死亡.
- 覆盖 11 个真实安装会话, 17 份探针结果和 46 份界面 / 状态快照. 原始辅助错误和 importance 100 的非后台提交均保留并标注. 最终 target 37 包复测通知拒绝 + 进程重建 + 安装成功, 正常 target 36 包在 API 24 验证等待界面和最终成功, inrt target 29 的正式安装入口也通过.
- JVM 状态终结规则 1/1, API 37 / API 24 的状态持久化与回调身份测试各 2/2. 最终调试 APK 编译及 16 KB 对齐通过, 宿主 / inrt release 主 Manifest 的私有组件与探针隔离检查通过. [实施记录与 APK 哈希](evidence/android-sdk-37-p25-recovery-20260913.md).
- 本轮没有脚本公开 API 或插件契约变化, 不触发 Documentation / DTS 生成或 42 个插件的版本更新. 正常目标仍为 36 / 29; ASM, 其他入口, 真机矩阵与 target 37 的最终 inrt 门禁继续保持开放.

### 2026-09-13: P2.5 ASM 生效条件与宿主 / inrt 对照

- 关闭本机镜像的 ASM 生效条件与正反对照条目. 上轮只设置 Activity setter false, 顶部 fixture 没有应用级保护声明; 本轮补齐该实验条件后, 默认状态就出现系统 `[ASM] Abort Launching`. 不需要修改 DeviceConfig 或 aconfig.
- 12/12 组符合预期: 5 次系统拦截, 7 次 landing 创建. 分别覆盖调用方与顶部 package 的 compat、应用级默认与保护声明、Activity true 例外及默认 setter. 宿主 target 36 / 37 与 inrt target 29 / 37 的目标升级差异都有设备证据.
- 复跑工具提交 `45741a160`, 新增 fixture manifest 参数、UID / target / task 记录和启动前 resumed task 快照; 拦截要求本 case 的系统 ASM 日志, 不凭调用返回或目标缺失推断. [机器结果与复跑说明](evidence/android-sdk-37-p25-asm-20260913.md).
- 生产 manifest 与启动代码保持现状, 不添加通用放行例外. 本轮只有测试工具与研发文档, 不触发产品 changelog 或关联插件 / 文档仓库更新. target 37 inrt 调试构建、正常宿主 / inrt 构建、16 KB native 对齐和 release 主 manifest 检查通过; 不代表完整 release / R8 验收.
- 正常配置与输出仍为 compile 37 / 宿主 target 36 / inrt target 29. 兼容开关已恢复, 独占只读 AVD 已关闭. 最终镜像 / 真机 ASM 复跑及真实 Launcher、其他通知 / 小组件、取色回传、a11y 与 inrt 其余入口继续保持开放; P2.5 与 P3 尚未整体完成.

### 2026-09-13: 实体机测试工作汇总与执行工具

- [实体机清单](android-sdk-37-physical-device-tests.md) 汇总 99 个可勾选验收行, 每行含设备、操作、预期和阶段映射, 42 个官方插件各有独立记录. 生成的 CSV 全部初始化为 NOT_RUN, 一个测试行的多种设备 / 权限 / 包场景需要分别保存证据.
- 新增只读设备清点 / 快照、CSV 导出和选定 instrumentation 入口. 实际 LAN fixture 支持指定 PC 接口 / 手机来源 IPv4, BAL 与安装恢复探针通过显式 `--physical-device` 参数支持测试手机. 权限页自动点击、完整 Looper 与音频全局诊断仍按原工具边界运行.
- 只读清点确认 API 28 / 31 / 33 / 35 的 5 台真机在线, 均为真实 4 KB 内核页, Xiaomi 平板为 sw720dp; 另完成 API 35 的只读快照验证. 没有在线 API 37 真机或真实 16 KB 设备. 本轮未安装测试 APK、运行手机 instrumentation、修改权限或执行功能验收. 缺少新硬件记为实体机证据待补, 不另加原 Roadmap 未约定的发布前置条件.
- 本轮修改仅为测试工具与研发文档, 正常 SDK 配置不变, 无产品 changelog / JS API / 插件版本变化. Python 与工具验证结果见清单第 8 节; 不能以工具检查或 AVD 结果关闭实体机、最终用户 APK 与插件验收门禁.

### 2026-09-13: P2.6 TLS / CT / ECH 与 HTTP 请求范围

- 真实 TLS 基线复现两个 insecure 选项污染共享客户端的缺陷. 调整为当前请求使用独立 TLS 配置, 常规 client / timeout 配置继续共享. 两种选项的同步、回调和 Promise 连续请求均验证恢复链与主机名校验.
- API 37 同机 target 36 / 37 的默认、CT 独立开关、ECH 独立开关共 6 组, API 24 回归 1 组, 合计 42 次方法执行通过. API 37.0 ECH 的实测模式为 2 / 1, 没有套用 37.2 文档预期; 当前夹具连接无 ECH 扩展, 不宣称实际协商通过.
- 正常 app debug / release, androidTest 与 inrt debug 构建通过, 相关 JVM 测试 6/6, Python TLS 测试 2/2. 生产 NSC 不增加 CT / ECH 例外, appRelease 无测试 CA. [实施记录](evidence/android-sdk-37-p26-20260913.md) 与 [机器结果](evidence/android-sdk-37-p26-results-20260913.json) 保留失败探索、APK / 源码哈希和最终矩阵.
- HTTP 文档与 Offline Docs 已同步, 后者的 debug / release 内容门禁和 JVM 测试通过, 文档版本 6.8.0 / 插件发行版本 6.8.1 各自保留. d.ts boolean 签名无需调整. 宿主及插件的多语言 changelog 已生成, 未发布 APK 或远程推送.
- 正常配置仍为 compile 37 / host target 36 / inrt target 29; 两个独占只读 AVD、TLS helper 与临时调试资源已清理, 手机未做变更. P2.6 的真实域证书链、企业代理和 ECH 协商保留为待补, 后续可继续 P2.7, 不将 P2 / P3 整体关闭.

### 2026-09-13: P2.7 Parcel 与真实插件往返

- 会话开始时宿主为干净的 `4422786ab`. `a6d037516` 增加 debug 私有独立进程接收器、Parcel 字段 / 边界与负例测试、当前生产 QuickJS / Python / Barcode 链路及复跑工具; 没有生产 API / SDK 配置 / Gradle 依赖变化, 不触发产品 changelog 或关联文档 / d.ts 发布.
- 实际 API 37 字节码及双进程负例核实 `PARCEL_HARDENING` 的具体范围和门控. 两个启用条件分别 35/35 instrumentation + 2/2 OCR, 最终关闭对照 11/11; 11 个指定插件及元数据读取通过. 计数包含重复负例, 初版失败单列保存, 不用总数代替覆盖范围. [实施记录](evidence/android-sdk-37-p27-20260913.md) 与 [机器结果](evidence/android-sdk-37-p27-results-20260913.json) 保存 APK / 源码哈希、状态和失败探索.
- QuickJS 文本匹配与新的 Python 结果类型夹具已修正, 所测生产路径无 Parcel 回归; 旧 Python R2 诊断维护事项另列附录 F. IDE Remote JVM 调试会话未能建立, 没有取得 logpoint 值, 结论来自无调试器对照、既有日志和源码; 临时调试资源已移除.
- 正常 app / androidTest 与 target 37 覆盖构建通过, 最终恢复正常 target 36 产物并完成 enable / disable 对照. 原生对齐门禁、release 合并 manifest 隔离检查及 Python 工具测试通过; 没有重跑全量 release 或远程 CI, 99 行实体机清单保持未验收状态.
- 本轮仅用独占只读 x86_64 AVD, 实际内核页为 4096, 不当作真实 16 KB 验证. Lua 仅在本地测试副本重签名; 插件仓库的既有修改未触碰. 默认 ADB 5037 异常后改用独立 5049, 结束前恢复所测包的兼容状态并关闭本轮 AVD / ADB, 手机与其它模拟器未改动. 正常配置仍为 compile 37 / host target 36 / inrt target 29.
- P2.7 本地自动化条目已勾选, IPC01 的真实 Tasker / 桌面流程及 P3 / P5 的其它 ABI、inrt、42 插件发布验收保持开放; 后续继续 P2.8 存储与文件管理器. P2 / P3 尚未整体完成.

### 2026-09-13: P2.11 WebView UA 与基本渲染

- 基线 `4422786ab` 开始时干净. 工作期间 P2.7 改动进入主工作区, 本轮使用独立 worktree 与只读 API 37 AVD, 不混入另一会话文件.
- 复用 P2.6 已归档的 target 36 / 37 debug APK, 真实 Rhino UI、文档首页 / App 页与 HTML Previewer 共 28/28 场景通过. 自定义 UA、后缀与 reset 均符合当前 target / 开关; 独立预览器不继承宿主 UA 开关.
- 新增标准库复跑工具、8 项证据判定测试、机器结果与 8 张代表截图. 13 个探索结果单独保留, 包括被系统对话框遮挡的早期页面, 不计入正式通过. [完整记录](evidence/android-sdk-37-p211-20260913.md).
- 无生产代码或公开 API 变更, 无需产品 changelog / 关联文档生成 / 插件版本调整. 正常 compile 37 / host target 36 / inrt target 29 保持. UA override 已恢复, 独占 AVD 与 adb server 5038 已关闭.
- P2.11 的 File System Access 行为、实体机 UI04 与 P5 最终插件包仍待验证, P2 / P3 不整体关闭.

### 2026-09-13: P2.8 存储与 P2.9 小组件 / 通知

- 会话从干净的 `16ba3901f` 开始. `6ab016582` 修复多位小组件 ID 在批量更新时被截成末位、导致路径与点击映射误删的问题, 同步 6.8.0 十语言 fix 日志并生成 Markdown. 公开 API、权限模型和 Gradle 依赖未变, 无需关联文档站 / d.ts 发布.
- P2.8 的 MediaStore 卷枚举、媒体回收目录与 ContactsProvider 目录权限已按实际镜像实现重新定位, 修正原先笼统的存储权限归类. target 36 / 37 的存储、系统选择器与权限状态共 12 组 84 次行断言执行通过. 普通 SD 卡与普通联系人查询不冒充不可靠卷过滤 / 远程目录门控命中. `768325c80`, [完整证据](evidence/android-sdk-37-p28-20260913.md).
- P2.9 完成真实小组件注册 / 更新 / 两次脚本点击, 以及标准、前台服务、小型自定义和超限通知对照, 共 6 组 26 次行断言执行通过. 通知开关按发送 UID 生效; 自定义内容被剥离时通知记录及标准标题仍可存在. Bitmap 超限负例与 Icon 公告 / 镜像差异分别记录. [完整证据](evidence/android-sdk-37-p29-20260913.md).
- `14a594b10` / `855134388` 增加可复跑工具与严格证据判定. 后台冻结、启动 ANR、元数据发布和选择器加载 / 取消时序等失败探索单列保存, 不计入有效矩阵. 最终 target 36 对照使用同一 APK 的显式 speed 编译, 不证明冷启动性能全面验收. Python 工具测试 8/8 + 4/4、正常 / target 37 debug 构建与原生对齐门禁通过; 未重跑完整 release / 远程 CI.
- 正常配置与最终构建输出仍为 compile 37 / host target 36 / inrt target 29. 临时权限 / 小组件绑定授权及四个所测 compat 已恢复, 本轮调试资源、独占只读 AVD 和 ADB 5053 已清理. 手机、共享 ADB 与关联插件仓库未做修改.
- 不可靠 USB / OTG 与外设生命周期、真实 Launcher / OEM 通知、Icon 上限差异以及完整业务通知生命周期继续开放. 99 行实体机清单仍为 NOT_RUN, P2.8 / P2.9 / P3 不整体关闭; 下一轮可继续 P2.10 短信 / 通知监听.

### 2026-09-14: P2.10 短信即时矩阵与通知监听

- 会话从干净的 `373b7ef65` 开始. 同机 target 36 / 37 与 `FILTER_GENERIC_OTP` 四组正反对照, 普通短信 / 普通 OTP / WebOTP 的 24 项查询 / 广播比较均符合预期. 提供者实际分类、APK / 脚本摘要及当前镜像代码分别留证, 不以 shell UID 读取代替应用可见性. [实施记录](evidence/android-sdk-37-p210-20260914.md).
- 通知完整标准四组仅一个通过: 首个 target 36 默认组收到普通正文和两个脱敏更新, 后续 target 36 / 37 以及两个补充重复组均缺失脱敏更新. 系统发布记录仍有敏感正文标记, 原因未定位. 保留旧 runner 的 reportedSuccess 并按新标准重新校验, 不因即时 SMS 子集通过而放宽通知条件.
- 调试尝试未取得通知链路的有效命中, 启动 / 调试 ANR 保留为失败探索. 未改通知生产代码或公开 API, 未切换默认短信角色 / 系统时间. 三小时恢复、后台 / 豁免及 API 37 真机差异继续开放.

### 2026-09-14: P2.11 文件选择器修复与门控对照

- `4d8564d98` 修复 UI 根布局的 Activity context 关联. IDEA 非暂停日志断点直接取得 chooser 的 null Activity 与 DecorContext, 四组修复后根 Activity / 普通子主题共 12 项断言通过. 十语言 v6.8.0 changelog 和生成的 Markdown 已更新为 2026/09/14; 私有实现调整无需关联文档 / d.ts 发布.
- 四组共 24 场景符合预期: 20 次真实系统选择器往返, 4 次禁用门控时的提前拒绝. 网页内容、mode / permissionMode、Intent、实际 URI 读写许可和取消结果均保存. persistable 标志与实际持久授权分开记录, 不将保存取消算成写入通过. [实施记录与机器结果](evidence/android-sdk-37-p211-files-20260914.md).
- 复跑工具提交 `71db66ed1`. 正常宿主与 target 37 覆盖 debug 构建、native 对齐门禁通过, 最终恢复 compile 37 / host target 36 / inrt target 29 的正常配置与宿主产物. Python 证据判定和预检隔离测试 25/25 通过; 未重跑完整 release / R8 或远程 CI.
- 临时两个 compat、SMS 权限与通知监听访问已恢复, 本轮断点 / Remote 配置 / adb forward 已清理, 五个用户断点保持原状态. 独占只读 AVD 与私有 ADB 5054 已关闭, 共享 ADB 和实体机未改动.
- P2.10 / P2.11 均保持部分完成, 99 行实体机清单继续 NOT_RUN. 后续可继续 P2.12 大屏 / 输入 / 配置变化, 同时补通知投递、真实文件写入与持久化证据; P2 / P3 / P5 不整体关闭.

### 2026-09-14: P2.12 UI 脚本分屏与输入状态, P2.13 前置核对

- 从干净的 `8ce71de0e` 开始. `38522f5c5` 补齐 UI 脚本 Activity 的 screenLayout / smallestScreenSize 声明, 修复进入分屏时销毁脚本引擎的问题. 调试器实际读到配置掩码 2432 与新实例 `execution=null`, 无调试器对照同样复现. 十语言 changelog 与生成的 Markdown 已同步.
- API 37 / target 36 与 37 的 20 组主矩阵中, 4 组 UI 脚本通过; 其余 16 组的搜索、光标 / dirty、viewport 与 IME 遮挡问题如实保留. 旧工具 reportedSuccess 与最终校验结果分别保存, 不以文本幸存替代完整状态和布局验收. [实施记录](evidence/android-sdk-37-p212-20260914.md).
- 正常宿主 / inrt debug、target 37 宿主覆盖构建、恢复后的正常 debug 与 release 清单任务通过; 二进制 configChanges 均为 0xda4. Python 探针测试 38/38 通过. 未重跑完整 release / R8 / 远程 CI, 无公开 API 或关联插件发布变化.
- P2.13 的镜像状态为 memory limiter disabled; 现有报告页未实现历史退出读取. [前置核对](evidence/android-sdk-37-p213-preflight-20260914.md) 将条件检查、真实重负载与报告实现拆开, 没有修改全局内存策略制造替代结果.
- 正常构建包的 UI 脚本另有手机 / 平板 2/2 复验通过. 显示、IME 和测试权限已恢复, 独占只读 AVD / 私有 adb 5055 关闭, 五个用户断点、共享 adb 与实体机保持原状; 正常 SDK 配置仍为 compile 37 / host target 36 / inrt target 29.
- P2.12 / P2.13 仍为部分完成. 下一步优先定位已复现的编辑器状态和输入布局, 同时保留悬浮 UI / 外设 / 实体机及限制器启用设备的验收; P2 / P3 / P5 和 99 行实体机清单不整体关闭.

### 2026-09-14: P2.12 编辑状态与 IME 布局收敛

- 从干净的 `852df35c4` 开始. `23cc3a03e` 修复原生 / Ace 的光标和选区恢复, 为无路径编辑会话提供稳定的大草稿键, 调整矮窗口中的编辑器辅助栏, 保留 Main 搜索状态, 让 Log 输入栏和发送按钮可用. 十语言 v6.8.0 changelog 及生成 Markdown 已同步.
- 校正上轮解释: Ace 引擎 changed=false 不代表宿主草稿丢失, 旧包的 shouldSaveDraft() 仍为 true; Main Back 主动收起搜索框也不是配置重建缺陷. 新发现的无路径大草稿丢失则经 409600 字符实测确认并修复. `e73f5c4f8` 分别检查宿主 dirty、非空选区、全文哈希、文件草稿回调、辅助栏恢复和按钮矩形.
- API 37 的 target 36 / 37 最终 32/32 用例通过; 正常配置恢复包另有 Log 手机与 Ace 平板大草稿 2/2 复验. 编辑器 JVM 测试 161/161, Python 工具测试 54/54; 正常和覆盖 app / inrt debug、恢复后的 release manifest 与页对齐门禁通过. APK 来源与早期失败尝试见 [后续记录](evidence/android-sdk-37-p212-followup-20260914.md).
- 两个临时日志点、Remote 配置和 adb forward 已移除, 五个用户断点仍为 2 启用 / 3 禁用; 显示、IME 和测试权限已恢复. 独占只读 AVD 5594 与私有 adb 5056 已关闭, 共享 adb 5037 未变, 没有操作实体机.
- 按维护者约定集中开展实体设备测试, 开测前明确固定版本的 APK / 插件、设备、步骤、预期和回传材料. 99 行实体机清单保持 NOT_RUN; P2.12 的悬浮 UI、连续调整窗口、外设与实际 IME composing text 仍待补齐. 可继续推进 P2.13 历史退出诊断和 P2.14 / P2.15, 内存限制器启用设备仍是独立条件; 不提前关闭 P2 / P3 / P5 或切换正式 target 36 / 29.

### 2026-09-14 (P2.13 历史退出报告与 P2.14 环境对照)

- 设置页的进程退出记录已实现, 具备后台读取、7 天 / 20 条上限、多进程去重、原始系统原因与复制. target 36 / 37 的 12 个页面检查点通过, 最终正常包空历史另 3/3, JVM 12/12; 十语言资源 / changelog 及生成 Markdown 已同步.
- P2.14 两个 target 各 8 个状态快照显示 mode=true 时仍可绑定, 保护中解绑再启用成功, 未观察到对应 user restriction 或设置页拒绝. 官方规则已确认, 当前镜像的差异保持开放; 未据全局模式误报启用失败, 未声明 isAccessibilityTool.
- 正常与 target 37 覆盖 debug 构建、正常 release 主 manifest 合并通过. 额外全量 Lint 运行约 19 分 50 秒后仍停留在应用源码分析, 已取消且没有本轮报告; 不引用旧报告作为通过结果.
- [实现、设备证据与范围](evidence/android-sdk-37-p213-p214-20260914.md). 正常输出仍为 compile 37 / target 36 / inrt 29. 内存限制器仍 disabled, 真正的限制器负载报告、实际高级保护拒绝与旧系统 / OEM 验证继续保留; O04 已指向新的报告入口, 99 行实体机清单仍为 NOT_RUN. 下一步可继续 P2.15 / P2.16, 无需维护者本轮立即操作设备.

### 2026-09-14: Q1-Q7 确认、Q7 明文迁移与新 D37 接入

- 维护者确认 Q1=a / Q2=a / Q3=b / Q4=a / Q5=a / Q6=b / Q7=b, 已回填 D12-D18 和各阶段待办. Q3 改为 Media3 最终方案, Q6 改为全部 42 仓独立 SDK 升级发版, Q7 从后续建议转入 P2.6.
- `494a048df` 将明文策略写入主 NSC 并移除 Manifest 旧开关, 复现并修复 API 37 上宿主 / inrt release 普通 HTTP 被拒绝的问题. API 24 / 37 的 HTTP 40/40、TLS / CT / insecure 隔离 40/40, 工具测试 4/4; 同步 10 语言日志及生成文档.
- debug / release / target 37 覆盖构建通过, 最后恢复正常 target 36 / 29. 最终宿主 APK 仅比已测包多出生成日志内容, DEX / 编译资源相同; inrt 与已测包完全相同. 全量 Lint 和 R8 不在本次通过范围.
- 新 Samsung SM-F776B (`localhost:37478`, API 37, ARM64, 内核 4 KB) 初次只读检查成功, 随后断连且重连失败; 未安装 APK 或改设置, 99 行实体机验收仍 NOT_RUN. 需要维护者恢复连接或提供变化后的端口. 两个临时 AVD、TLS 服务和 reverse 已清理, 原共享 adb server 保留.
- [实现与证据](evidence/android-sdk-37-q7-20260914.md). 后续优先落实 Q3 Media3 与 P2.16 的 Q4 存储引导, 新机恢复后补齐适用的 API 37 真机项目.

### 2026-09-14: Samsung D37 恢复连接后的真机补验

- 维护者恢复 `localhost:37478` 后, 核对仍为相同指纹的 Samsung SM-F776B / API 37 / ARM64 / 4 KB 内核. 六组同批 APK 共 62/62: HTTP 24/24、TLS / CT / insecure 隔离 26/26、DEX 4/4、Looper / 线程池 8/8. 宿主 / inrt 正常 debug、target 37 debug 和正常 release 均有实际结果.
- 两个包从旧 target 升至 37 后 LAN 权限均为未授予, HTTP / TLS 回环仍通过; 恢复旧 target release 后系统恢复隐式授权. 未人为改权限或 compat, 未把 ADB 回环当作真实 LAN 或 ECH 证据.
- 结束前核对手机上最终四个 APK 的哈希, 移除本轮独占安装的包与 reverse, 关闭自建 TLS fixture. 设备仍在线, 内存限制器 disabled / Advanced Protection false 与初始一致, 共享 adb 保留. 本轮未改生产代码或构建版本, 不重复新增 changelog.
- [真机实施记录](evidence/android-sdk-37-q7-samsung-20260914.md) 与 [机器记录](evidence/android-sdk-37-q7-samsung-results-20260914.json). O02 / A05 已有部分真机证据, 完整验收行仍未勾选; 原集中人工测试安排继续有效, 无需再次提供连接地址.

### 2026-09-14: Q3 Media3 播放后端与生命周期

- 完成专用 `MediaSessionService` / ExoPlayer 接入, 显式媒体 FGS 类型与权限, 新项目默认权限与多语言说明. `media` 签名不变, 新请求接管单一播放器, 旧脚本退出不影响新脚本. 工作线程同步准备, 中断 / 超时 / 退出可取消迟到请求.
- 最终 66/66: API 37 宿主 / inrt 的正常 target 与双 target 37, API 24 的正常双 flavor, 每组 11 项. 初始自然完成测试把 BUFFERING 当成结束, IntelliJ 字段证据确认后修正断言; 不掩盖早期 10/11 或调试附加产生的启动 ANR.
- 正常 debug / release, 双 target 37 debug, 6 个应用 APK 的 Manifest / 签名检查通过; 最终输出恢复 target 36 / 29. 文档 129 模块和离线插件 debug / release 内容门禁通过, 无 d.ts 签名变更.
- Samsung 连接已离线, 本轮仅使用两个已关闭的只读 AVD. 真机听音、后台拒绝 / 恢复、TTS、开机 / 定时链、最终打包项目及 P2.16 继续开放. [完整记录](evidence/android-sdk-37-media3-20260914.md).

### 2026-09-14: Q4 按需共享存储启动与 P3.3 文档

- `requiresSharedStorage` 默认 false, 接入打包 UI / 配置持久化 / 远程 JSON. Splash / Log 共享实际权限门控, 设置返回与进程重建不重复执行; 取消、永久拒绝和声明缺失有明确结果, 隐藏项目保留授权恢复入口, 开机执行前检查权限.
- API 24 / 37 的 target 29 / 37 四组普通 Activity 与实际文件读写共 33/33, 配置 JUnit 6/6. API 37 已有所有文件访问授权的同签名 target 29 -> 37 升级 4 项通过, 私有脚本真实开机 1 项通过. 共享开机后续因 AVD ADB 超时未完成; 传统 legacy 授权、API 30 / 33、实际用户 APK 和 `preserveLegacyExternalStorage` 决策继续保留.
- 主仓库正常 debug / release 与覆盖 target 37 debug 构建通过, 正式 target 仍为宿主 36 / inrt 29. Documentation 6.8.0 / 68、Offline Docs 6.8.1 / 41、d.ts 4.13.0、Ace 1.4.0 / 93 已同步; Ace 167 项、离线文档 2 项单元测试及两插件 debug / release / native 检查通过. 不把本地提交计为 Q6 插件已发布, 已知 WorkManager Lint Fatal 也未关闭.
- 只读临时 AVD 已关闭, Samsung 仍离线, 本轮未变更实体机. S01 / I02 / I03 / I05 更新集中测试步骤, 99 行保持未验收. [实现、结果和范围](evidence/android-sdk-37-inrt-storage-20260914.md).

### 2026-09-14: Samsung 恢复连接后的存储启动与升级补验

- 冻结 `e2e4d599d` 对应的正常 / target 37 inrt 运行时, 重新生成关闭开机运行的存储夹具. Samsung SM-F776B / API 37 / ARM64 / 4 KB 上两组 UI、权限往返、进程退出恢复、隐藏入口与实际读写共 16/16, 同签名 target 29 -> 37 的所有文件访问授权升级另有 4/4.
- 复跑工具增加显式真机模式、设备 / 初始包 / boot 配置 / APK 哈希校验与清理; 8 项工具测试通过. 首轮被普通滑动锁屏中断的部分结果保留但不计入通过, 后续操作前唤醒并关闭滑动锁屏. 四次运行均按哈希清理测试包及专用文件, 结束时设备在线、AutoJs6 包清单为空, 未重启手机或改系统设置 / compat.
- 本轮没有重新编译生产代码, 同期图像 / 颜色改动不在冻结产物内. 正式 target 仍为宿主 36 / inrt 29; 未将提前 fixture 当作真实 APK Builder / Runtime Kit 用户包, 历史 legacy、完整开机、其他设备和首装 / 重装继续开放. S01 / I02 / I03 / I05 增补部分真机证据, 99 行不作整体勾选. [完整记录](evidence/android-sdk-37-inrt-storage-samsung-20260914.md).

### 2026-09-14: Roadmap 调整与 P3.1 targetSdk 37 切换

- 诊断: P2 代码适配已完成但每项挂着开放式真机补证, P3 被 "P2 全部" 门控, 99 行实体机清单零勾选; 维护者要求减少实体机测试并提供自动化. 新增第 0 节与 D19-D21, 29 条未关闭条目就地标注 CI / RC-SMOKE / WATCH, 实体机清单降级为深度验证目录, 固定 10 行 RC-SMOKE 清单.
- 工具: `tools/sdk37/run_gate.py` (+ `gate-a11y-wrapper.js`, `test_run_gate.py` 12 项主机侧自检). 一条命令完成 badging / 安装 / 无人值守套件 / 模拟器专属步骤 / JVM 汇总, 输出 gate.json / gate.md. 开发中修正: 测试包 badging 无 versionCode, aapt2 的 `minSdkVersion:` 拼写, 裸 inrt 模板无 `assets/project` 时 SplashActivity 必然 NPE (改为 SKIP 并提供 `--inrt-apk`), 连续 instrumentation 在 API 28 上 runner 进程未起即亡 (沉降 + 重试), 被 instrumentation 杀死的无障碍服务停留在系统 "Crashed services" 需切换设置重绑, AutoJs6 `console` 为不可赋值代理 (包装脚本改用函数作用域遮蔽).
- 切换: `version.properties` 宿主 / inrt 均 37; 构建、单测、lint 与六台设备门禁见 [P3.1 记录](evidence/android-sdk-37-p3-20260914.md). changelog 十种语言 `improvement` 各增一条, README / CHANGELOG 重新生成.
- 下次起点: P3.3 用 `build_inrt_storage_fixtures.py` 产出 target 37 夹具后跑 `run_gate.py --with-inrt --inrt-apk` 与 `run_inrt_storage_probe.py`; P3.4 Runtime Kit 重新生成; P4 CI 推送验证; P5 按 D21 写批处理脚本.

### 2026-09-15: 舰队重评估与 D21 工具

- 维护者提示 Accessibility-Compat 与 NodeJs-Runtime 期间有变化. 复核结果: Accessibility-Compat 自 `b21e3a7` 起自带 SelectToSpeak 身份壳无障碍服务与 `WRITE_SECURE_SETTINGS`, compileSdk 已 37, 工作区仍有另一会话的 65 个未提交文件; NodeJs-Runtime 新增宿主直接 exec 的终端启动器 `libnodexe.so` 与 npm / corepack 资产, libnode 改 small-icu, SDK 值未变. 两者在附录 E 与 P5.3 / P5.5 已改写.
- `tools/sdk37/fleet.py` (scan / table / plan / apply, `test_fleet.py` 5 项) 落地 D21; 全量扫描另发现 Python-Runtime 的 Gradle 硬编码 (原表未记录), 附录 E 数值列与脏列已按 2026-09-15 扫描刷新.
- 下次起点: 按 `fleet.py plan` 的顺序从无暴露且干净的仓库开始 (Pinyin, Pinyin4j, MediaInfo, ImGui, Image-*, File-Inspector, APK-Inspector, OpenCV, Image-Quantization, QuickJS, TypeScript-Engine, Java / Kotlin-Runtime, DEX-Compiler, Offline-Docs, APK-Builder-Template), 每仓 `apply` + Gradle 检查 + 单独提交; 暴露仓库两步走.

### 2026-09-15: P3.3 / P3.4 自动矩阵、Runtime Kit 与打包链路

- P3.3: `build_inrt_storage_fixtures.py --signing-properties` 用宿主密钥打出目标 37 / 29 夹具 (inrt 的 signature 级 `org.autojs.permission.PLUGIN` 与宿主共用, 随机密钥的夹具无法与 debug 宿主共存); `run_inrt_storage_probe.py` 在新建的 `AVD_API_30` / `AVD_API_33` 与 `AVD_API_37.1_16K` 各 8/8 (目标 37 首次安装) 与 4/4 (29 -> 37 覆盖安装); HEAD 门禁 `--with-inrt --inrt-apk` 在 37.1 通过 (a11y-smoke 一次 41/42 时序抖动, 复跑 42/42). 证据 [P3.3/P3.4 证据](evidence/android-sdk-37-p33-p34-20260915.md).
- P3.4: Runtime Kit 以 HEAD 重新生成并通过插件仓库校验; 插件仓库无 targetSdk 29 硬编码. 打包链路发现真实回归: 插件把 `resources.arsc` 压缩写入, 目标 30+ 的用户 APK 在 Android 11+ 无法安装 (inrt 目标 29 时从未暴露); 插件仓库 `ce6dfd9` (v1.0.3, build 42, 配对宿主 5280) 与宿主仓内 `plugins/apk-builder-template` 副本同修 (`RemoteUnsignedApkWriter`, JVM 测试). `tools/sdk37/run_packaged_examples.py` + opt-in `Sdk37PackagedExamplesProducerDeviceTest` 打包 a11y / ui / timer 三个示例并在 37.1 安装运行, 脚本检查 11 / 9 / 9 全部通过 (边到边、预测性返回、定时任务、TTS、loadDex、通知权限、精确闹钟、包可见性、广播).
- 发现 (WATCH): 插件 ADR (2026-09-02) 的正式路径 (`supportsApkBuild`, 不读开发者开关) 宿主与插件均未实现; HEAD 宿主打包页面只走 `RemoteApkBuildClient` (开发者开关 + `supportsRemoteBuild` + 协议 3), 按仓库既定配置 (`enableRemoteBuild=false`) 构建的插件在 HEAD 宿主一律 NOT_AVAILABLE. 维护者同日确认该开关为内部构建移除后的遗留物: 插件 `35846fb` 删除 `enableRemoteBuild` 并固定声明能力, 宿主 `e91d5b46b` 删除开发者开关与运行时门控, 无标志插件 + 新宿主复测 `run_packaged_examples.py` 全部 PASS; 宿主读取 ADR 正式键 (`supportsApkBuild` 等, 需同步 plugin-api) 仍待维护者安排.
- 未做: 真机 (RC-SMOKE #8 保留; 示例 APK 为 AVD 的 x86_64)、插件发布 / tag、宿主正式路径迁移. AVD 事项: `AVD_API_30` 默认端口 5602 与另一会话撞车, 改 `-port 5610`; 一次 a11y 示例服务 20 s 内未绑定 (刚安装即写设置), runner 改为重写设置最多 3 次.
- 下次起点: P5 舰队 (`fleet.py plan` 顺序, 每仓一次提交) 与 P4 文档 / CI; P3 只剩 RC-SMOKE #8 与 CI 推送验证.
### 2026-09-15 晚: P5 舰队第一批 (17 仓) 与 APK Builder 开关清理

- 维护者确认 `enableRemoteBuild` 等为早期 "宿主内部构建 + 远程构建" 双路径的遗留物, 内部构建早已移除: 宿主 `e91d5b46b` 删除开发者开关 / `RemoteApkBuildRuntimeGate` / 相关字符串与偏好 (63 文件), 插件仓库 `35846fb` 删除 `enableRemoteBuild` 构建开关并始终声明能力; 打包示例在无开关宿主 + 无标志插件上 3 / 3 PASS (`d339d5bd3` 记录). 宿主 plugin-api 的 ADR 正式键迁移仍留给维护者 (WATCH).
- `fleet.py bump` (`92335f13b`, `cd09e6058`) 落地 P5.0 第 0 步的逐仓发布, 17 仓各一次提交 (附录 E 证据栏), 全部在 `AVD_API_37.1_16K` 上安装 + installed targetSdk 核对 + androidTest; 维护者提示的 NodeJs-Runtime (终端 / npm 提交, 干净, 两步走) 与 MCP-Server (P6 进行中, 4 个脏文件) 本批未动, Bun-Runtime 另一会话进行中跳过.
- 发现 (均与本次升级无关, HEAD 在同一 AVD 复现): Image-Quantization 的 PNG 金样按字节比对而 API 37.1 的 deflate 输出不同 (像素 / 质量一致); TypeScript-Engine 10 项在 API 37.1 上失败 (夹具容量 / 契约 / FD 采样); Java / Kotlin-Runtime 的只读 DEX 证据测试只接受 API 34 / 36. 三者以 `notClass` 排除并写入步骤明细, 留给维护者. DEX-Compiler 手写 `platforms/android-37/android.jar` 路径因 SDK 目录名 `android-37.0` 失效, 先修 (`aed6e7c`) 后升.
- 未做: 两步走 / 有暴露仓库 (需 `--note-file` 各自说明与 AVD 暴露项复测), 脏工作区仓库 (D11), Python-Runtime / R8-Compiler 硬编码统一, Archive-Manager 的 target 步骤, 各仓发布 / tag, P5.1 / P5.3 / P5.5 的加载 / Binder / 存储复测.
- 下次起点: (1) 两步走仓库先 compileSdk 37 独立提交 (`bump --compile 37 --note-file ...` 需配合 target 不变的说明; 或先 `apply`), 再按 P5.3 / P5.4 / P5.5 的暴露条目在 AVD 复测后做 targetSdk; (2) 维护者处理脏仓库与 Bun 后补跑; (3) P4 文档 / CI; (4) 17 仓的发布与官方索引刷新由维护者决定.

### 2026-09-15 深夜: P5 舰队第二批 (两步走仓库, 曾脏仓库与前置修复)

- 维护者已修复上批 4 条发现并提交曾脏仓库; 复核: Image-Quantization / TypeScript-Engine 重建后于同一 AVD 全部通过 (上批失败项未再现; 两仓工作区带着维护者未提交的测试 / 文档修改, 会话末仍未提交: Image-Quantization 25 个文件, TypeScript-Engine 7 个文件, 本会话未动), Java / Kotlin-Runtime 的证据测试仍需 instrumentation 参数 (常规跑保留 `notClass`), DEX-Compiler `188c35e` 后 `assembleDebug testDebugUnitTest` 通过.
- `fleet.py bump` 扩展 (`fae5bcf49`): `--compile` / `--target` 单独执行 (D11 两步走, 各自提交信息与十语说明), `--replace` / `--replace-json` 镜像文件, flavor 化 APK 目录, 连字符版本后缀, 阿拉伯语说明只用 ASCII 标点, Windows 控制台 UTF-8. 前置提交: 10 仓 wrapper 9.3.0 -> 9.5.0, Paddle v4-v6 库模块 source-set 覆盖删除, Python / R8 版本来源统一, Three-Ember HDR 可空修复, HTML / OpenCC 拆分权限测试, R8 retrace 测试 JUnit4.
- 本批 37 / 37: Yolo-NCNN, Lua-Runtime, MLKit-OCR, Paddle v3-v6, Rapid v3 / v6, R8-Compiler, Archive-Manager (仅 target); 只做 compileSdk 37 (target 36): HTML / Markdown-Previewer, Screen-Color-Picker, Three-Ember / Terra / Stone, OpenCC, Ace-Editor, MLKit-Barcode, NodeJs-Runtime, Python-Runtime, MCP-Server. 证据 [evidence/android-sdk-37-p5-fleet-batch2-20260915.md](evidence/android-sdk-37-p5-fleet-batch2-20260915.md).
- 发现: (1) API 37 把 `ACCESS_LOCAL_NETWORK` 从 INTERNET 拆分给 target <= 36 的包 (P5.3 本地网络条目), 契约测试按此放宽; (2) 平台插件按 Gradle 版本封顶 AGP, compileSdk 37 需 wrapper >= 9.4; (3) AGP 9.3 库模块拒绝 `sourceSets.named("main") { kotlin.directories += ... }`; (4) R8 retrace 设备测试的 JUnit3 形式在任何设备都失败 (与升级无关); (5) Screen-Color-Picker 一项 Espresso 焦点等待不稳定; (6) D: 盘曾写满导致 Rapid v6 AAR 打包失败, 清理已提交仓库的 `app/build` 后重跑.
- 未做: 两步走仓库的 target 步骤 (需本地网络 / 前台服务 / WebView / 无障碍等暴露复测: NodeJs, Bun, Python, MCP-Server, Screen-Color-Picker, Three-*, HTML / Markdown, Ace, MLKit-Barcode, OpenCC, Accessibility-Compat); P5.1 / P5.2 / P5.3 / P5.5 的加载 / Binder / 存储复测; 各仓发布 / tag 与官方索引; P4 文档 / CI.

### 2026-09-16 凌晨: P5 舰队第三批 (复核维护者修复, 轻暴露仓库的 target 步骤)

- 复核 (HEAD 全量 androidTest, 同一 AVD): Archive-Manager 180 / MCP-Server 35 / Three-Stone-AI 3 / Screen-Color-Picker 10 项全部通过; OpenCC 9 项中剪贴板 UI 测试在套件内失败、单跑通过 (证据 3.2, 留给维护者).
- target 37 (只做 target 步骤, 内置说明): MLKit-Barcode `57e88a0`, HTML-Previewer `ce965bf`, Markdown-Previewer `ed7c37b`, Ace-Editor `271e3c9`, OpenCC `1e5bfc9`. 依据: INTERNET 只用于公网更新检查 / 字体 / 自有 origin 的 WebView, 或已被 `tools:node="remove"` 去掉. 证据 [evidence/android-sdk-37-p5-fleet-batch3-20260916.md](evidence/android-sdk-37-p5-fleet-batch3-20260916.md).
- 工具: 扫描器忽略 `tools:node="remove"` 的权限 (`2156eceab`); README 说明 `--readme-keys` 只在改变对应级别的那一步传入 (`1380c56e7`). P4 官方索引条目确认无 SDK 字段. 磁盘: 删除 `build/sdk37-p0-p1` (3 GB).
- 未做: 9 个仍为 37 / 36 的仓库的 target 步骤 (Node / Bun / Python / MCP 先做本地网络权限; Screen-Color-Picker 与 Three-Ember / Terra 需要真实取色 / 播放探针; Three-Stone-AI 需 staging 模型; Accessibility-Compat 需 P2.14 / D16 说明); P5.1 / P5.2 / P5.3 / P5.5 的加载 / Binder / 存储复测; 发布 / tag / 索引; P4 的 Android 17 适配说明与 VS Code 扩展 README (本机无该仓检出).

### 2026-09-16 (最后 9 仓 target 步骤收尾)

- 42 仓 compile / target 37 全部完成, 42 个插件工作区干净. 最后 9 仓最终 HEAD 的 debug / JVM / androidTest / lint / signed release 门禁及版本、十语说明完成, [最后 9 仓收尾记录](evidence/android-sdk-37-p5-fleet-final-20260916.md).
- Node / Bun / Python / MCP 的插件 UID 本地网络权限与实际拒绝 / 授予 / 撤销复测完成; Node 的真实宿主 UID Node / npm / npx / Corepack 启动通过. Bun 和 MCP 采用维护者并行阶段完成后的最终 HEAD.
- 真实取色通过; Ember 修复通知所有权交接导致 FGS 被撤销及停止后的清理; 两播放器在实际 throw hardening 下后台、控制、锁屏和退出通过. Three-Stone 在专用配置启用真实 Android memory limiter 后完成模型推理, 记录 high 压力事件但未触发 kill. 宿主及兼容插件的高级保护说明、WRITE_SECURE_SETTINGS 与重绑通过.
- 完整套件无显式排除, runner 报告 227 项含两个条件跳过; 普通运行实际 225 项 + Bun 16 KB / MCP opt-in 各 1 项补测通过, 覆盖全部 227 个独立用例. OpenCC 维护者修复后普通 5 项 + 分阶段 7 次通过, 覆盖全部 9 个独立用例, 包含原 Paste 时序项.
- 历史 build/sdk37-* 29 目录 / 9.563 GiB 已安全盘点; 自动批准审核拒绝实际删除且未给出具体理由, 未删除目录, 提供 tools/sdk37/remove_historical_builds.ps1 默认只读 / 显式 -Apply 脚本. 本轮证据位于 build/verification, 不在该 glob 内. 本轮 AVD 均已关闭.
- 发布 / tag / 索引、跨仓完整业务、实体机听音 / LAN / OEM、真正限制器终止后的宿主报告、实际无障碍非辅助工具限制仍按原 Roadmap 对应项验收; 不以本轮 target 步骤代替这些独立结果.
