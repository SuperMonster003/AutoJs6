# SDK 37 P2.5: ASM 生效条件与跨 UID 启动对照

日期: 2026-09-13. 测试工具提交: `45741a160`. 对应 [Roadmap P2.5](../android-sdk-37-roadmap.md#p25-后台活动启动-i5). 机器记录: [12 组结果, 配置与 APK 哈希](android-sdk-37-p25-asm-20260913-results.json).

## 结论

DEVICE-37: 本机 API 37.0 revision 6 镜像默认就能执行 ASM 限制. 上轮 default / enable / disable 全部放行的关键缺项是顶部 fixture 没有声明应用级保护; 仅调用 `setAllowCrossUidActivitySwitchFromBelow(false)` 不会补上这项声明. 本轮在同一测试调用链中加入应用级保护后, target 37 宿主默认被明确拦截, 禁用调用方 ASM 后放行, 再启用后恢复拦截. 无需修改设备全局开关就能形成有效对照.

12/12 组符合各自预期, 包括 5 次系统 ASM 拦截与 7 次实际 landing Activity 创建. 宿主 target 36 与 inrt target 29 的默认放行, 和 target 37 的默认拦截均有实测. 这是同一任务下层 Activity 启动的门禁, 不代表 P2.5 所有真实入口或 P3 整体通过.

SOURCE / 决策: 本轮保持宿主与 inrt 的生产 manifest 和启动代码. 没有发现需要增加通用 Activity 例外的生产调用点证据. 顶部应用的声明控制它自己的任务保护, 不是调用方任意跨应用启动的授权; 给宿主添加 `true` 不能替受保护的第三方顶部页面放行. 后续真实入口若被阻断, 需要按其可见状态、任务结构和用户操作继续分析.

## 官方说明与实际平台的边界

- VERIFIED: 官方 Activity security 指南要求在 application 上将 `android:allowCrossUidActivitySwitchFromBelow` 设为 `false` 以启用保护; 特定 Activity 可用 setter 的 `true` 提供例外. [Activity security](https://developer.android.com/guide/components/activities/secure-bal).
- VERIFIED: `setAllowCrossUidActivitySwitchFromBelow` 自 API 35 提供, API reference 的说明仍引用 target 35. 同时, 上述指南的 Task Hijacking Prevention 段混合了 Android 15 与 API 37 的表述. 不把这些版本文字当作当前镜像已执行的门控. [Activity API](https://developer.android.com/reference/android/app/Activity#setAllowCrossUidActivitySwitchFromBelow(boolean)).
- PLATFORM_CONFIG / DEVICE-37: 本机 dump 是 `ChangeId(230590090; name=ASM_RESTRICTIONS; enableSinceTargetSdk=37; overridable)`. target 36 默认放行 / enable 拦截, target 37 默认拦截 / disable 放行共同验证了本镜像门控. `overridable` 不表示 disabled.
- VERIFIED / SOURCE-REFERENCE: AOSP 已读取版本的 `checkCrossUidActivitySwitchFromBelow` 分开检查 Activity 例外、顶部 UID 的 compat 与应用声明, `logAsmFailureAndCheckFeatureEnabled` 再检查调用方. 这帮助定位实验条件; 该提交不是本镜像源码, 不据此推定未测试的系统分支. [AOSP BackgroundActivityStartController, ab6ce1d8221a](https://android.googlesource.com/platform/frameworks/base/+/ab6ce1d8221aff9e70cceae1d09f12629d14f094/services/core/java/com/android/server/wm/BackgroundActivityStartController.java).

## 实验装置

1. 使用既有 debug `ActivityLaunchCompatibilityProbeActivity`, 它要求 DUMP 权限才能从外部启动. shell 仅启动探针; 待测调用由实际宿主 UID 10231 或 inrt UID 10260 执行, 不用 instrumentation 身份代发.
2. 探针在同一 task 内启动不同 UID 的 `org.autojs.sdk37.balfixture.TargetActivity`. fixture 保持在顶部, 并记录自身 UID、targetSdk、taskId 和请求的 Activity setter 值.
3. 下层探针到达 `onStop()` 后再等 12 秒. Python 工具在此期间保存 `dumpsys activity activities`, 确认 fixture 是该 task 的 resumed Activity. 本轮所有待测启动时调用方 importance 都是 400, 没有悬浮窗权限或已启用无障碍服务.
4. 下层探针调用 `startActivity` 启动同 UID 的 landing Activity. 成功必须读到本 case、同 task 的真实 landing 记录. 拦截必须同时满足无 landing 与新的系统 `[ASM] Abort Launching` 日志, 不能仅凭方法返回或未看见窗口判断.

所有拦截日志都同时写明 `BAL_ALLOW_FOREGROUND`. 普通 BAL 允许同一前台任务中的调用, 仍不代表 ASM 会允许它从不同 UID 的顶部页面下面启动. 拦截时调用方法正常返回, 系统 START 日志的 result code 为 102; 本轮没有以抛异常或 StrictMode 触发作为 ASM 成功条件.

fixture 有两种 APK: 默认 manifest 不含该属性; 受保护 manifest 含 `android:allowCrossUidActivitySwitchFromBelow="false"`. 二者 target 均为 37, Java 代码相同, 各自独立签名. 每次换签名先卸载独占 AVD 内的测试 fixture. 应用级属性的编译结果用 `aapt2 dump xmltree` 核对.

## 设备矩阵

下表的 caller / top 状态均指 `ASM_RESTRICTIONS` 的 package override. `default` 表示没有 override. 除第一行外, 顶部 fixture 都使用应用级 `false` 声明; 顶部 target 一直为 37.

| 记录目录 | 调用方 target | caller / top override | 顶部 Activity setter | 实际结果 |
| --- | --- | --- | --- | --- |
| `t37-no-manifest-default` | 宿主 37 | default / default | false | landing 创建, 顶部应用未声明保护 |
| `t37-protected-default` | 宿主 37 | default / default | false | ASM 拦截 |
| `t37-caller-disabled` | 宿主 37 | disable / default | false | landing 创建 |
| `t37-caller-enabled` | 宿主 37 | enable / default | false | ASM 拦截 |
| `t37-top-disabled` | 宿主 37 | default / disable | false | landing 创建 |
| `t37-activity-exception` | 宿主 37 | default / default | true | landing 创建 |
| `t37-activity-default` | 宿主 37 | default / default | 未调用 | ASM 拦截 |
| `t36-protected-default` | 宿主 36 | default / default | false | landing 创建 |
| `t36-caller-enabled` | 宿主 36 | enable / default | false | ASM 拦截 |
| `inrt29-protected-default` | inrt 29 | default / default | false | landing 创建 |
| `inrt37-protected-default` | inrt 37 | default / default | false | ASM 拦截 |
| `inrt37-caller-disabled` | inrt 37 | disable / default | false | landing 创建 |

首两组运行后, 工具补充了 fixture package dump, 并把 ASM 日志的边界从初始 shell 启动行改为带唯一 case ID 的 fixture 事件, 避免 logcat 较晚连接时遗漏初始启动行. 首两组的原始记录未改写; 已有 UID、task、生命周期和系统判定证据仍保留在机器记录中. 其余组使用改进后的边界校验. 最终审查将 task ID 匹配收紧为完整 token, 并用最终边界规则离线复核全部 12 份原始日志, 结果与保存的判定一致.

## 复跑

构建命令、测试参数与 APK 哈希都在机器记录和 [工具说明](../../../tools/sdk37/README.md#background-activity-launches) 中. 示例仅操作独占模拟器上的 debuggable 测试包:

```powershell
py tools/sdk37/build_bal_fixture.py --sdk E:/.android/sdk --jdk E:/.java/jdk-21.0.1 --allow-cross-uid-from-below false --output build/sdk37-bal/fixture-protected

# 安装匹配的宿主 target 37 调试 APK 和受保护 fixture 后, 先在默认状态运行.
py tools/sdk37/run_bal_probe.py --adb E:/.android/sdk/platform-tools/adb.exe --serial emulator-5568 --route asm --allow-below false --expect no-landing --output build/sdk37-bal/asm-default

# 记录原 override 后, 分别禁用和启用调用方, 每次使用新的结果目录.
& E:/.android/sdk/platform-tools/adb.exe -s emulator-5568 shell am compat disable ASM_RESTRICTIONS org.autojs.autojs6
py tools/sdk37/run_bal_probe.py --adb E:/.android/sdk/platform-tools/adb.exe --serial emulator-5568 --route asm --allow-below false --expect landing --output build/sdk37-bal/asm-disabled
& E:/.android/sdk/platform-tools/adb.exe -s emulator-5568 shell am compat enable ASM_RESTRICTIONS org.autojs.autojs6
py tools/sdk37/run_bal_probe.py --adb E:/.android/sdk/platform-tools/adb.exe --serial emulator-5568 --route asm --allow-below false --expect no-landing --output build/sdk37-bal/asm-enabled

# 本轮原先无 override, 因此用 reset 恢复; 若原先有 override, 应恢复原值.
& E:/.android/sdk/platform-tools/adb.exe -s emulator-5568 shell am compat reset ASM_RESTRICTIONS org.autojs.autojs6
```

顶部 package 的开关对照使用 `org.autojs.sdk37.balfixture`; inrt 使用 `--package org.autojs.autojs6.inrt` 和其自己的 compat override. Activity 例外用 `--allow-below true`, 默认 setter 用省略参数, 应用级默认则需要重新构建不传 `--allow-cross-uid-from-below` 的 fixture.

## 构建与环境收尾

- 宿主使用生产源码提交 `1ac2ecb37` 对应的已归档 target 37 调试 APK, 正常 target 36 APK 与本轮正常构建的 SHA-256 一致. inrt target 37 调试构建通过, 26 秒 / 736 tasks. 正常宿主 / inrt debug 与两者 release 主 manifest 构建通过, 27 秒 / 860 tasks.
- 新构建的 native 16 KB 对齐门禁通过; release 主 manifest 不包含 BAL 探针 Activity 或 fixture query. 本轮没有执行完整 release / R8 构建, 不据主 manifest 任务推定其通过.
- `version.properties` 仍为 compile 37 / 宿主 target 36 / inrt target 29, version 6.8.0 (5280). 正常输出目录已恢复到相同目标值. target 37 inrt 探针 APK 单独归档, SHA-256 为 `c9244d119f8fbae421e3cfd9cb1982db4c1a8057d3bb6007607c54f924b2f45d`.
- 独占 AVD 为 `aj6-ps16k-37-x86_64`, API 37.0 revision 6, 320x640 / 160 dpi, 3 GB / 4 vCPU / swiftshader / Vulkan 关闭. 使用 `-read-only -no-snapshot-load -no-snapshot-save -no-window -no-audio`. 指纹为 `google/sdk_gphone16k_x86_64/emu64xa16k:17/CE2A.260420.019/15611780:userdebug/dev-keys`.
- `getconf PAGE_SIZE` 返回 16384, `/proc/self/smaps` 的 KernelPageSize 为 4 kB; 两者分别记录, 不称为真实 16 KB 内核验收. adb 保持 shell UID 2000. 本轮没有修改全局 DeviceConfig / aconfig, 没有开启无障碍或授予悬浮窗权限.
- 调用方和顶部 fixture 的 ASM override 均恢复, 收尾 dump 与会话初始声明相同. window_manager DeviceConfig 前后相同. AVD 内宿主 / inrt 也恢复为 target 36 / 29. 核对 AVD 名称后关闭本轮 emulator-5568, 不保存快照; 未操作已连接真机或未知离线连接.
- 工具 Python 语法检查、fixture javac / D8 / aapt2 / APK 签名校验及 `git diff --check` 通过. 本轮只有测试工具和研发证据变化, 不涉及产品 feature / fix / improvement / dependency 条目, 不改 changelog, 不重新生成多语言 README / CHANGELOG, 不触发 Documentation / DTS 或 42 个插件版本更新.

## 后续门禁

关闭本机镜像的 ASM 生效条件与正反对照条目. 最终发布镜像与真机需要沿用此有效场景复跑. 尚未验收新任务 / clear-task / 多窗口的完整 ASM 组合, 真实 Launcher 固定快捷方式、小组件及其他通知、完整取色回传、无障碍启动例外、inrt 其他入口和旧系统矩阵. 不以本轮测试替代上述 P2.5 / P3 / P5 条目.
