# Lua 受控模块快照设备证据（2026-08-13）

## 结论

M4 受控模块快照在真实宿主、production INFO/RUNTIME、跨进程 Host Broker 和
PUC Lua 5.4.8 Native runner 上通过一条精确选择的设备 smoke。Provider 和 native
开关仅为本次构建显式启用；两仓默认值仍为关闭。

本次验证的是内部应用文件路径的产品预览 happy path，不代表完整项目系统、递归模块图、
SAF URI、普通外部存储别名或 API/ABI 全矩阵已经通过。

## 固定输入

- 宿主 revision：`1c373d1330d23ab76747648a50532929509ece99`
- 独立 Provider revision：`9eddc6b1ba97a9b622f9cda977ef6b56fba729ca`
- Provider `VERSION_BUILD=19`，Git commit count 为 19。
- 设备：`QV710AF65F`，API 31，`arm64-v8a`，4 KiB page。
- fingerprint：`Sony/XQ-AT72_CN/XQ-AT72:12/58.2.A.10.44A/058002A0100044A0891821322:user/release-keys`
- 三份安装 APK 的 signer certificate SHA-256：
  `2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8`。

实际安装并由设备拉回复核的 APK：

| 角色 | SHA-256 |
| --- | --- |
| AutoJs6 arm64 Debug | `c77e335abd9919c935b9af1f1fdb6993ea3fc9e54b94551aeaaec7dd34571ae5` |
| AutoJs6 androidTest | `56b3f0e5ec84614743a191e02e32e36708f193ae363b87dae2b9453e436b23a5` |
| Lua Provider arm64 Debug | `c279defb8e59fecd9a5c6a3eec59713c73b2402b23c19272575b5353b1d816f1` |

## 构建与聚焦测试

- Provider：`testDebugUnitTest + assembleDebug + assembleDebugAndroidTest` 成功；
  43/43 JVM tests，0 failure、0 error、0 skipped。
- 宿主：App Debug Kotlin/JVM 编译、App Debug APK 和 androidTest APK assembly 成功。
- 宿主受影响测试：33 项，0 failure、0 error；32 项执行通过，1 项因 Windows 当前用户
  无权创建 symbolic link 而按 JUnit assumption 跳过。Android 生产读取仍使用
  `lstat + O_NOFOLLOW + fstat` 身份绑定；设备 happy path已实际执行。
- 静态路线图门：208 项通过；它只作为源码/配置证据。

所有 Gradle 客户端均在启动前进行两次相隔 15 秒的独占检查。若发现其他 Codex Gradle
客户端，本任务以 exit 9 放弃启动并等待，没有与其并行构建。

## 精确设备方法

selector：

```text
org.autojs.autojs.core.plugin.lua.LuaOfficialRuntimeSmokeTest#officialProviderExecutesAndReleasesSequentialSessions
```

同一个 engine 依次验证既有基础执行、console/arguments、`device.info`，并新增第三个本地
入口 `main.lua`。模块根固定为同级 `main.modules/`；`helper.lua` 递增一次全局计数且不返回值。
入口连续两次 `require("helper")`，同时断言两次返回 `true`、模块仅执行一次，最终返回
`Int64(1)`。这证明了会话级冻结数据经 Host Broker 到 Provider、摘要复核、text-only load、
`nil -> true` 与执行内 require cache 的 production happy path。

设备回执：

```text
OK (1 test)
INSTRUMENTATION_CODE: -1
M3_OFFICIAL_PROVIDER_SMOKE=PASS
REMOTE_PROCESS=14710 io.github.supermonster003.autojs6.plugin.lua.runtime:lua_runtime
DEVICE_RESTORED_USERS=0,10
```

`M3_OFFICIAL_PROVIDER_SMOKE` 是复用脚本的历史标签；本次 exact method 已包含 M4 模块快照断言。

## 失败诊断与修复

首次设备运行正确地 fail closed：Android 应用数据路径中的合法祖先 canonical alias 被旧逻辑
误判为模块根自身 symbolic link。三个包随后仍从用户 0/10 完整卸载。修复后只比较最终路径
组件与其 canonical parent，模块文件本身继续由 `O_NOFOLLOW + fstat(dev, ino, size)` 绑定；
重新构建后同一 selector 通过。

## 恢复与未覆盖范围

脚本在 `finally` 中逆序 force-stop/uninstall。结束时，用户 0 和 10 均无宿主、androidTest
或 Provider 的 current/uninstalled package record，`pm path`、目标进程和 instrumentation
也均为空。

仍未建立：API 24 模块设备执行、x86_64 模拟器、外部存储/SAF、递归或 manifest 模块图、
大规模模块目录、并发文件替换 hostile device case、签名 release 与默认 production enablement。
