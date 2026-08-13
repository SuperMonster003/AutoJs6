# Lua M3.3 `device.info` Broker Smoke 证据（2026-08-11）

## 结论

M3.3 已完成。独立 Lua Provider 声明 `device.info`，AutoJs6 宿主仅从已固定的
Provider/Host capability 交集授予它，并通过会话绑定的 Host Capability Broker 返回六个
非敏感设备字段。Lua 5.4.8 脚本可调用 `require("autojs").device.info()`；未安装、未授予或
身份变化时均明确失败，不会回退到其他引擎。

本阶段没有开放通用 Binder/Java 调用，也没有返回 serial、fingerprint、Android ID 或裸
Android 对象。

## 固定输入

- AutoJs6 revision：`9729b5c7c4c5807f1ab494f03df93a59e3e8e3be`。
- 独立插件 revision：`74194f54c4f5e9762b17e827821e52f55fb79559`，
  `VERSION_BUILD=18`。
- 设备：`QV710AF65F`，Android API 31，`arm64-v8a`，4 KiB page；fingerprint：
  `Sony/XQ-AT72_CN/XQ-AT72:12/58.2.A.10.44A/058002A0100044A0891821322:user/release-keys`。
- 三份 APK 均只有一个 V2 signer，certificate SHA-256 为
  `2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8`。

| APK | SHA-256 |
| --- | --- |
| AutoJs6 arm64-v8a debug | `69ec8483b222ccb33af22caeb4bc2c9eeae494546e9c2fa0d3d365fecafb0e61` |
| AutoJs6 androidTest | `bf6bb42d1b4d021448e810f88c76b509de6e429f819eeb9589e7681131452fdf` |
| Lua Provider arm64-v8a debug | `b302c470b9f6d0e5f54f42483b7ff62278d75937018ef9e6f1c8713c1297928b` |

## 实现与本地门禁

- Host allowlist 只含 `device.info`，Provider requirement 不强制该能力，因此旧的空 capability
  Provider 仍可运行基础 Lua。执行请求只能复制 pinned grant，不能自行扩权。
- Broker 只接受空 map 参数和零 descriptors，并返回
  `brand/manufacturer/model/device/product:String + sdkInt:Int64`；调用 ID、execution ID、UID、
  重放、配额、终态和关闭状态继续由 V1 policy 约束。
- Provider/JNI 只暴露固定无参 `autojs.device.info()`，不能由脚本选择 capability 名或传任意
  参数。Host rejection 映射为 `HOST_CAPABILITY_FAILED / HOST_CALL`。
- 插件 Gradle：JVM 42/42，零失败、错误或跳过；Native arm64-v8a/x86_64、debug APK 与
  androidTest APK 均构建成功。
- 宿主 focused JVM：29/29（broker 5、engine 16、selection 8），零失败、错误或跳过；
  App 与 androidTest APK 构建成功。
- `CAPABILITY_DENIED` 的 focused JVM 用例还确认未授权调用不会读取设备信息源。
- 所有成功 Gradle invocation 都只在连续两个 15 秒空闲采样后启动；检测到其他 Codex
  Gradle 客户端时保持等待，没有并发执行。

## 设备收据

精确 selector：

```text
org.autojs.autojs.core.plugin.lua.LuaOfficialRuntimeSmokeTest#officialProviderExecutesAndReleasesSequentialSessions
```

测试先执行 `return 7`，再执行：

```lua
local autojs = require("autojs")
local device = autojs.device.info()
-- 校验五个字符串字段与数值 sdkInt
autojs.console.log("<unique-device-info-marker>")
return device.sdkInt
```

宿主断言结果等于设备的 `Build.VERSION.SDK_INT`，并在 `GlobalConsole` 中观察到唯一 INFO
marker。最终收据：

```text
Time: 2.292
OK (1 test)
INSTRUMENTATION_CODE: -1
M3_OFFICIAL_PROVIDER_SMOKE=PASS
REMOTE_PROCESS=3502 io.github.supermonster003.autojs6.plugin.lua.runtime:lua_runtime
```

安装后从设备拉取的三份 APK 摘要均与本地审计值一致。测试结束后 Host、Provider 和测试包
均成功卸载；Android 用户 0、10 中没有残留包路径、目标进程或 instrumentation。

## 边界

本收据证明 production Provider 的 capability 声明、宿主 pinned grant、跨进程
Broker/callback、固定 JNI 函数、受控 console、标量结果和独立进程 happy path。它不把
focused JVM 的 denial 提升为 hostile Android UID 证据，也不证明 timer、文件、网络、shell、
UI、完整 API/ABI 矩阵、签名 release 或默认公开启用。
