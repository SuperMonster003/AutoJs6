# Lua M3.2 参数映射 Smoke 证据（2026-08-11）

## 结论

M3.2 已完成。AutoJs6 宿主会在 Lua 引擎初始化时冻结 `ExecutionConfig` 参数，将允许的值
转换为有配额上限的 `LuaValue`，再经 production Binder/PFD/session 链传入独立插件进程。
Lua 5.4.8 脚本可通过 `require("autojs").arguments` 读取该快照。

本阶段只实现数据输入，不开放 `Context`、`Intent`、Java 对象、反射或新的 Host
Capability Broker 权限。

## 固定输入

- AutoJs6 revision：`4ae0e48ee7a4562d8a5a1341d14696fe496bd61a`。
- 独立插件 revision：`04b082d877578cd40af4ec1eebd2e53b39d1e35a`，
  `VERSION_BUILD=17`。
- 设备：`QV710AF65F`，Android API 31，`arm64-v8a`；fingerprint：
  `Sony/XQ-AT72_CN/XQ-AT72:12/58.2.A.10.44A/058002A0100044A0891821322:user/release-keys`。
- 三份 APK 均只有一个 V2 signer，certificate SHA-256 为
  `2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8`。

| APK | SHA-256 |
| --- | --- |
| AutoJs6 arm64-v8a debug | `dfedb57952b7831b6e10beae6d254ebbd0214c9dc46c2bb594439d95979c00ef` |
| AutoJs6 androidTest | `ac632dc8522d38294a43ef60847c31c1dcf20d21aabe59d8e160aeac5f9e8b48` |
| Lua Provider arm64-v8a debug | `614a8c3a30b7ced5a534bb71ab18b01fbafd37b4b278d76fb247782e6e2cd028` |

## 实现与构建

- 宿主仅接受 Boolean、整数、有限浮点数、String、ByteArray、稠密数组/List 和字符串键
  Map；循环、null/hole、非字符串键、非有限数及任意 Android/Java 对象均 fail closed。
- 宿主在发现 Provider 前完成深拷贝和 depth、node、data、item、key、container 配额检查；
  同一引擎的循环执行复用不可变快照。
- Provider 使用私有版本化快照跨 Kotlin/JNI 边界；Native 再独立验证完整消费和相同配额，
  数组映射为 Lua 的 `1..n`，map key 与 bytes 保持 binary-safe。
- 宿主 focused JVM：18/18；插件 JVM：39/39；均无 failure、error 或 skip。
- 插件 Native/JNI、arm64-v8a/x86_64、debug APK 与 androidTest APK 组装成功；宿主
  app/debug 与 androidTest APK 也已生成。所有 Gradle 调用均在连续两个空闲采样后串行启动。

## 设备收据

精确 selector：

```text
org.autojs.autojs.core.plugin.lua.LuaOfficialRuntimeSmokeTest#officialProviderExecutesAndReleasesSequentialSessions
```

测试先执行 `return 7`，再传入：

```text
enabled = true
items = [4, "ready"]
meta = { stdout = <unique>, stderr = <unique>, delta = 5 }
```

第二段 Lua 校验 Boolean、1-based 数组、String 和嵌套 map，通过参数中的 marker 分别输出
stdout/stderr，并返回 `items[1] + meta.delta`。宿主断言结果为 `LuaValue.Int64Value(9)`，
两类输出分别进入 `Log.INFO` 与 `Log.ERROR`。

```text
Time: 1.92
OK (1 test)
INSTRUMENTATION_CODE: -1
M3_OFFICIAL_PROVIDER_SMOKE=PASS
REMOTE_PROCESS=20708 io.github.supermonster003.autojs6.plugin.lua.runtime:lua_runtime
```

安装后拉取的三个 APK 摘要均与本地审计值一致。测试结束后 Host、Provider 和测试包均成功
卸载；Android 用户 0、10 中没有残留包路径、目标进程或 instrumentation。

## 边界

本收据证明有界参数快照、完整 V1 输入值到 Lua table/string 的映射、既有 console 通道、
标量结果和真实 production Provider happy path。它不证明参数持久化到 parcel、外部 Intent
对象兼容、复合结果、Host Capability Broker、timer、文件/网络能力或默认公开启用。
