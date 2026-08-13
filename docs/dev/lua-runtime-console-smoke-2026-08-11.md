# Lua M3.1 Console Smoke 证据（2026-08-11）

## 结论

M3.1 已完成。独立 Lua Provider 现在提供受控的 `require("autojs")`，其中只包含
`console.log(string)` 与 `console.error(string)`。真实 AutoJs6 宿主经 production
INFO/RUNTIME、只读 PFD、远端 session 和 Native Lua 5.4.8 执行脚本，观察到 stdout 与
stderr 的正确级别，并取得标量结果。

console 是 V1 execution output 通道，不是 Host Capability Broker 调用，因此本阶段没有新增
capability grant，也没有提前开放参数、timer、文件、网络或 Android 对象。

## 固定输入

- AutoJs6 revision：`d4b6e41e9d8fb7456ef0cb7f5994a4df18672e69`。
- 独立插件 revision：`cf79c461a2ff4117e0e733cdaf0fc652985418cf`，
  `VERSION_BUILD=16`。
- 设备：`QV710AF65F`，Android API 31，`arm64-v8a`；fingerprint：
  `Sony/XQ-AT72_CN/XQ-AT72:12/58.2.A.10.44A/058002A0100044A0891821322:user/release-keys`。
- 三份 APK 均只有一个 V2 signer，certificate SHA-256 为
  `2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8`。

| APK | SHA-256 |
| --- | --- |
| AutoJs6 arm64-v8a debug | `d146349b194fa55b903347fbf0e7310bc71ce6c40230914ecf34559a65d1d53c` |
| AutoJs6 androidTest clean rebuild | `c2a4c91285a90e4d0beb8d59c37ea3979c354fc979ba8be0864bf956a3efdfa7` |
| Lua Provider arm64-v8a debug | `077a3ab34b0c6367c01969752d6d90b9f8715872d7130d163f0e003a4fe890af` |

## 构建与本地检查

- 插件 `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`：
  `BUILD SUCCESSFUL`，JVM 38/38，零失败、错误或跳过；Native CMake 同时编译
  arm64-v8a 与 x86_64。
- 插件 clean revision 使用 `native=true/provider=true/faultHarness=false` 无缓存组装：
  `BUILD SUCCESSFUL`。
- 插件 repository verifier：`STATIC_SCAFFOLD_OK`；其 Python hostile tests 23/23。
- 宿主 androidTest Kotlin 编译及 APK 组装成功；提交后无缓存重建生成上表最终测试 APK。
- 每次 Gradle 调用前都等待连续两个 15 秒空闲采样；发现其他 Codex Gradle 客户端时
  串行等待，没有并发启动。

## 设备收据

精确 selector：

```text
org.autojs.autojs.core.plugin.lua.LuaOfficialRuntimeSmokeTest#officialProviderExecutesAndReleasesSequentialSessions
```

脚本先执行 `return 7`，随后执行：

```lua
local autojs = require("autojs")
autojs.console.log("<unique-stdout-marker>")
autojs.console.error("<unique-stderr-marker>")
return 8
```

最终收据：

```text
Time: 1.974
OK (1 test)
INSTRUMENTATION_CODE: -1
M3_OFFICIAL_PROVIDER_CONSOLE_SMOKE=PASS
REMOTE_PROCESS=17321 io.github.supermonster003.autojs6.plugin.lua.runtime:lua_runtime
```

测试断言 stdout marker 进入宿主 `GlobalConsole` 的 `Log.INFO`，stderr marker 进入
`Log.ERROR`，返回值为 `LuaValue.Int64Value(8)`。仓库新增的 runner 对 adb 客户端设置
有界等待，核对安装后 APK 摘要，并在 `finally` 中清理。

正式收据前的两次诊断运行只暴露测试观察点问题：首次读取了不同控制台对象，第二次未考虑
`GlobalConsole` 会给存储内容增加时间和级别前缀。两次均卸载三包并复核设备恢复，不计为通过。
最终运行后，用户 0、10 中宿主、测试包和 Provider 均不存在，无残留目标进程或
instrumentation。

## 边界

本收据证明受控 autojs 模块、两类日志、既有输出配额链、标量结果和真实 production
Provider happy path。它不证明参数映射、Host Capability Broker、通用模块加载、超过窗口的
输出等待、签名 release、完整 API/ABI 或公开默认启用；这些继续按 Roadmap 的 M3.2、M3.3
和 M5 推进。
