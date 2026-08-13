# Lua 独立运行时签名候选与兼容抽样（2026-08-13）

## 结论

M5 发布候选阶段通过。独立 Lua Provider `0.1.0-rc.1` 已用 AutoJs6 官方证书签名；同一组
冻结的宿主、androidTest 和 Provider APK 在最低 API、ARM64 真机以及 x86_64/16 KiB
环境完成 production INFO/RUNTIME happy-path 抽样。Rhino、Node.js、Explorer、编辑器和任务
路由的精简核心回归也已完成。

这里的“通过”仅表示本地签名候选可进入发布准备，不表示已经创建公开 tag、推送远端、上传
发布页或完成 production soak。Provider/native/RC 开关在仓库默认值中仍全部为 `false`。

## 冻结输入

- 宿主 revision：`d27e52bdf835709e8305562411554ab7e8117f89`。
- 独立 Provider revision：`0497d5061171421e655e0256ef5a1fe1566c5a23`，Git commit count 20。
- Provider 版本：`0.1.0-rc.1`，`versionCode=20`，min/target SDK 24/36。
- 官方 signer certificate SHA-256：
  `31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213`。
- Provider 通过绝对路径只读主项目外部签名文件；宿主构建期间的 ignored 临时副本已删除。
  签名文件和密码均未提交、保留或写入日志/报告。

Provider Release APK：

| ABI | SHA-256 | 字节数 |
| --- | --- | ---: |
| arm64-v8a | `b2604af2a2cf8c07ceaf3b93ee06677d11d214a1347e8ed341cd1bcd330540e4` | 677,618 |
| x86_64 | `0b02b20593256cdc0367553d61011498fc2ea55fa2dd5b1e35a7ce3477797efa` | 666,735 |
| universal | `b7dc4aa3ed2b37dab1bfc8ea05a1aed546adad0cc39b810d22146ba6251ade81` | 1,256,643 |

用于设备抽样的宿主 APK：

| 角色 | SHA-256 |
| --- | --- |
| AutoJs6 arm64 Debug | `470660c12320c129dbe1238e5cc334f18c1b8bd5f5e0f51554544e805bedf3e7` |
| AutoJs6 x86_64 Debug | `2097f02df1227e24102ace0cceb42e7e049cab59daac30f38522ca2fb42807b7` |
| AutoJs6 androidTest | `f4a4361a4079f9081318946972ce9651f5831c446461e17326cbbd9736cce251` |

设备安装后均从 `pm path` 的唯一 `base.apk` 拉回并复算摘要；没有使用文件名 glob 猜测产物。

## 构建与签名产物门

Provider canonical invocation 于 `2026-08-13T06:59:11.0701661Z` 开始，串行执行
`:app:testDebugUnitTest :app:assembleRelease`，并显式设置
`native=true / provider=true / fault=false / releaseCandidate=true`。Gradle 成功，9 个 JVM
suite 共 43/43，0 failure、0 error、0 skipped。

release verifier 在同一 revision 上独立复核通过：

- 三个 APK 均为非 debuggable、单 signer、APK Signature Scheme v2 有效；
- BuildConfig 与资源均为 native/provider 开启、fault harness 关闭；
- arm64-v8a 与 x86_64 native inventory 精确，所有 ELF LOAD 为 `0x4000`；
- 三个 APK 均通过 `zipalign -c -P 16 4`；Release 中不存在 fault JNI symbol；
- verifier 的原始边界是 `signed-packaging-only`、`deviceVerified=false`、
  `runtimeVerified=false`。后续三环境结果单独提供运行证据，没有回写或扩大包装门语义。

宿主随后在共享 Gradle 互斥下串行完成 App Debug/androidTest assembly，并运行四个 focused JVM
方法：Explorer 文件类型、编辑器临时扩展、Lua 的 Engines/Task 共享路由，以及既有 JS/Auto
路由隔离。结果 4/4，0 failure、0 error、0 skipped。构建产生的版本时间临时改动已恢复，签名
文件也已从工作树删除。

## 精简 API/ABI 运行抽样

三个环境使用上述同一组冻结产物，并运行同一个 exact selector：

```text
org.autojs.autojs.core.plugin.lua.LuaOfficialRuntimeSmokeTest#officialProviderExecutesAndReleasesSequentialSessions
```

该方法顺序验证标量执行、console、`device.info`、受控模块快照、会话释放和
`:lua_runtime` 独立进程。

| 环境 | API / ABI / page | 结果 | 恢复 |
| --- | --- | --- | --- |
| 临时 `DEX_R1_API24_X64` | API 24 / x86_64；旧系统不提供本轮可用的 `getconf PAGE_SIZE` | `OK (1 test)`；最低 API production Binder/Native 路径通过 | 用户 0 三包清空，AVD 已关闭 |
| `QV710AF65F` | API 31 / arm64-v8a / 4,096 | `OK (1 test)`；远端 PID 18255 | 用户 0/10 三包清空 |
| 临时 `Codex_Lua_M5_API37_16K` | API 37 / x86_64 / 16,384 | `OK (1 test)`；远端 PID 4545 | 用户 0 三包清空，AVD 已关闭并删除 |

API 24 fingerprint：
`Android/sdk_phone_x86_64/generic_x86_64:7.0/NYC/4174735:userdebug/test-keys`。
API 31 fingerprint：
`Sony/XQ-AT72_CN/XQ-AT72:12/58.2.A.10.44A/058002A0100044A0891821322:user/release-keys`。
API 37 fingerprint：
`google/sdk_gphone16k_x86_64/emu64xa16k:17/CE2A.260420.019/15611780:userdebug/dev-keys`。

API 24 暴露了两个 runner 兼容问题：Android 7 toolbox `ps` 不接受 `-A -o`，且该 adbd 的
sync pull 无法读取 PackageManager 返回路径。runner 改为按 SDK 选择 `ps` 形态，并在 API 24
仅在 `adb pull` 失败时用二进制安全的 `adb exec-out cat` 拉回 APK；摘要要求没有放宽。修复前
两次诊断均未开始 instrumentation，且每次都恢复包状态；修复后同一 selector 通过。

已有 `AVD_API_37` 因预检发现宿主包而被拒绝，没有覆盖或卸载其内容；之后新建 read-only、
no-snapshot 的临时 16 KiB AVD 完成验证，并在 finally 中停止、等待离线后删除 AVD 定义。

## 核心回归

| 能力 | 证据 | 结果 |
| --- | --- | --- |
| Explorer / 编辑器 / Lua 任务路由 | 4 个 focused App JVM 方法 | 4/4 PASS |
| Rhino | `RhinoE4XTest#testAttributeName`，QV710AF65F | `OK (1 test)` |
| Native editor | `NativeEditorRegressionSmokeTest#nativeEditorCoreWorkflowSmoke`，QV710AF65F | `OK (1 test)` |
| Node.js 与 Rhino 切换 | `NodeR6EngineSwitchInstrumentationTest#r6_01_requiredPluginNodeAndRhinoAlternateAcrossMultipleRounds`，QV710AF65F | `OK (1 test)`；3 轮多次真实 Node/Rhino 执行 |

Node 正式回归固定使用已归档、与宿主协议同代的 R6 arm64 APK：

- SHA-256：`0fa27835b9f4264197fba06b2e3281b63ae2eb8559d4784bcc3eb729471605b6`；
- package/version：`io.github.supermonster003.autojs6.plugin.nodejs`，`1.0.0`/36；
- signer 与宿主相同。测试内部要求 Native Node engine、远端 PID、exit 0、marker 写入、运行时
  复用和 Rhino 往返，因此不是只做发现或 Binder 连接的假阳性。

本轮实时 instrumentation 输出为 `OK (1 test)` 和 `INSTRUMENTATION_CODE: -1`，但未另行
落盘新的 JUnit receipt；历史 R6 receipt 绑定的是另一组宿主/测试 APK。因此这里记为
fresh device core smoke，不把它扩大为 package-bin/project-workspace 或独立 canonical R6 重验收。

## 失败诊断与回滚

发布候选记录保留了以下失败，没有把它们重命名为正式通过：

1. `baseBroadcastReceiverRunTaskExecutesNodeProjectPackageBinDescriptor` 在设备原 Node 1.0
   `f8ecb8cc…` 上等待输出文件超时。该 APK 实际来自 clean `a904ee20…` canonical R6
   lineage，具备所需 capability/lifecycle；本轮日志没有闭合根因。该结果保留为
   selector-specific 非阻断已知限制，`r6_01` 的 PASS 不覆盖 package-bin/project-workspace
   或 production 任务执行。
2. 同一 package-bin selector 在临时 Node 1.1 上也超时，日志明确为跨代
   `ERR_AUTOJS6_NODE_PLUGIN_CONTRACT_MISMATCH`，provider version `-1`、supported `2..2`。
   这是协议混配诊断，未计入回归通过。
3. 临时 Node 1.1 回滚时，首次普通 `install -r` 因版本降级被 Android 拒绝；随后使用
   `install -r -d` 恢复设备原 `1.0.0`，拉回 SHA-256 精确等于
   `f8ecb8cc0842a920a123c497f9f4b6733bdae1629f76962080c21f620c87d719`。
4. 两个本地事务包装脚本曾在 PowerShell 输出/终态审计处报语法或类型错误；无有效 JUnit
   receipt 的尝试均未计为测试结果。每次之后都直接复核包、进程、instrumentation 与 Node
   APK 摘要，再继续下一步。

最终状态：`QV710AF65F` 的用户 0/10 中无宿主、androidTest 或 Lua Provider；无相关进程或
活动 instrumentation。设备原 Node 插件只保留在原用户 0，版本与 SHA-256 均恢复。两个临时
AVD 均已停止；本轮创建的 API 37 AVD 定义已删除。

## 尚未建立

- 公开 Git remote、tag、Release 页面或商店发布；
- production soak、完整 API 24～37 逐版本矩阵、升级/卸载兼容矩阵；
- Android process OOM、极端并发和所有非 MVP AutoJs 能力。

这些事项不否定 M5 本地签名候选，但在未来公开发布或扩大默认启用范围时应重新评估。
