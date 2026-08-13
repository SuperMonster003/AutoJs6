# Lua 官方 Provider 端到端 Smoke 证据（2026-08-11）

## 结论

M2 的 opt-in happy path 已通过。AutoJs6 宿主没有使用 fake provider 或 debug fault
service，而是发现并绑定独立插件的 production INFO/RUNTIME 服务，经只读 PFD 创建远端
session，并在同一个 `LuaPluginScriptEngine` 中依次得到 `Int64(7)` 和 `Int64(8)`。

普通源码配置仍为 `autojs.lua.native.enabled=false`、
`autojs.lua.provider.enabled=false`；本次 APK 只通过命令行参数显式开启，因此该证据不等于
默认启用或发布就绪。

## 固定输入

- 宿主 smoke revision：`c91e792b0c1e098303280972fd61b8d7cc647fa1`。
- 独立插件 revision：`e8ed2bdc4d72d06314354aefb1e1a5f36b8343d5`，
  `VERSION_BUILD=15`。
- 设备：`QV710AF65F`，Android API 31，`arm64-v8a`；fingerprint：
  `Sony/XQ-AT72_CN/XQ-AT72:12/58.2.A.10.44A/058002A0100044A0891821322:user/release-keys`。
- 三份 APK 的 V2 signer certificate SHA-256 均为
  `2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8`。

| APK | SHA-256 |
| --- | --- |
| AutoJs6 arm64-v8a debug | `d146349b194fa55b903347fbf0e7310bc71ce6c40230914ecf34559a65d1d53c` |
| AutoJs6 androidTest | `a1babf8b651bac0e1706e592cf2962211bbc41d0b92e1cf3cfd4b6414f668f52` |
| Lua provider arm64-v8a debug | `ba4200ee743d6696d2aba092c0733831e187952de45e72fc3d702257cd5365a2` |

## 构建收据

- `:app:compileAppDebugAndroidTestKotlin`：`BUILD SUCCESSFUL`。
- `:app:assembleAppDebug :app:assembleAppDebugAndroidTest`：`BUILD SUCCESSFUL`。
- sibling `:app:assembleDebug` 使用
  `-Pautojs.lua.native.enabled=true -Pautojs.lua.provider.enabled=true`
  和 `-Pautojs.lua.faultHarness.enabled=false`：`BUILD SUCCESSFUL`。
- 每次 Gradle 调用前均轮询其他 Codex Gradle 客户端，取得连续两个空闲采样后才启动；
  sibling 构建前实际等待了另一个 Java worktree 的 Gradle 任务结束。

## 设备收据

精确测试 selector：

```text
org.autojs.autojs.core.plugin.lua.LuaOfficialRuntimeSmokeTest#officialProviderExecutesAndReleasesSequentialSessions
```

instrumentation 参数：

```text
autojs.luaRuntime.officialProvider.enabled=true
```

结果：

```text
Time: 1.942
OK (1 test)
INSTRUMENTATION_CODE: -1
```

测试在运行前验证：

- INFO 与 RUNTIME action 各只有一个已启用的官方插件服务；
- 两个服务均由 `io.github.supermonster003.autojs6.plugin.lua.runtime:lua_runtime`
  承载，使用冻结的插件权限并处于 exported 状态；
- 宿主与插件签名匹配；
- 第一次执行返回 `LuaValue.Int64Value(7)`，第二次执行返回
  `LuaValue.Int64Value(8)`，证明前一会话已经释放并允许顺序复用。

安装前，`org.autojs.autojs6`、`org.autojs.autojs6.test` 和
`io.github.supermonster003.autojs6.plugin.lua.runtime` 在 Android 用户 0、10 中均精确不存在。
一次预运行在 instrumentation 前因 PowerShell 对 `adb install` 输出的类型判断错误而中止；
其 `finally` 已卸载宿主并确认全用户恢复。修正收据解析后，正式方法通过。最终按
test → provider → host 顺序卸载，用户 0、10 中三包再次全部精确不存在。

## 证据边界

本收据只关闭 M2 官方 Provider happy path。它不证明：

- production provider 默认开启、release 签名或公开发布；
- console、arguments、`require("autojs")` 或其他 AutoJs capability；
- 并发 BUSY、Android OOM、hostile blocking PFD、update/uninstall 或完整 API/ABI 矩阵。

这些工作按精简后的 Roadmap 分别进入 M3 和 M5，不回填为本次 smoke 的阻断项。
