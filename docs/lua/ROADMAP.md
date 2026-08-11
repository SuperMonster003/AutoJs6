# AutoJs6 Lua 独立运行时部署路线图

最后更新：2026-08-11

本路线图面向一个由独立 APK 提供、运行在独立进程中的一等 `.lua` 脚本引擎。
主文件只记录可交付里程碑；测试明细、哈希、命令输出和故障分析统一放入证据文档，
避免让历史检查项阻塞后续功能部署。

## 使用规则

- `[x]` 表示该里程碑已经有与其描述相匹配的证据；源码存在不等于 Android 运行通过。
- 每个阶段只保留 2～4 个退出项。日常只检查受影响模块，不重复跑完整矩阵。
- 只有 Binder、PFD、Native 或进程边界发生变化时，才补一条目标设备 smoke。
- API/ABI 全矩阵、升级卸载、极端故障注入和签名检查集中到发布候选阶段。
- 诊断测试可以继续增加，但不自动成为当前阶段的阻断门禁。

## 永久约束

- Lua VM 只存在于独立插件的 `:lua_runtime` 进程；宿主只保留代理引擎。
- 仓库默认值和普通开发构建默认不开放 Provider；受控验证或发布构建必须显式开启。
- Provider 不可用、歧义或身份变化时明确失败，不回落到 Rhino、Node.js 或其他引擎。
- 源码通过只读 PFD 传输并校验长度和 SHA-256；开始派发后不自动重试。
- 会话只有一个终态；`cancel()`/`close()` 幂等；内存、时间、输出和并发都有上限。
- 不向插件暴露宿主 `Context`、`ScriptRuntime`、任意 Java 反射或裸文件路径。

## 部署看板

| 里程碑 | 状态 | 交付结果 |
| --- | --- | --- |
| M0 协议与宿主接入 | 已完成 | `.lua` 路由、协议、选择策略和 fail-closed 代理 |
| M1 独立 Native 运行时 | 已完成 | Lua 5.4.8/JNI、独立进程、ABI/16 KiB 和故障恢复 |
| M2 真实 Provider 闭环 | 已完成 | 宿主经 production INFO/RUNTIME 连续执行两次真实 Lua |
| M3 最小 AutoJs 能力 | 进行中 | console 与受限参数已闭环；下一步是首个宿主 broker 能力 |
| M4 产品预览 | 未开始 | 编辑器、持久入口和安装/错误体验 |
| M5 发布候选 | 未开始 | 签名、兼容抽样和独立发布 |

## M0 — 协议与宿主接入（已完成）

- [x] 冻结 V1 Binder、TaggedWire、LuaValue、只读 PFD 和生命周期协议。
- [x] `.lua` 通过宿主 Lua proxy 路由；运行时不可用时明确失败且不回退。
- [x] Provider 组件、UID、签名、版本、ABI、协议版本和 runtime slot 在派发前固定并复核。
- [x] 宿主具备有界同步 Binder lane、唯一终态、输出 credits 和资源清理策略。

## M1 — 独立 Native 运行时（已完成）

- [x] 独立 sibling APK 固定 PUC Lua 5.4.8，并仅编译允许的标准库和 text-only loader。
- [x] Native 标量执行、取消、deadline、内存限制、独立进程和 ABI/16 KiB 构建已验证。
- [x] Debug-only crash/wedge 验证完成 Binder death、进程重建和再次执行。
- [x] R2 hostile fixture 已覆盖关键 Binder/PFD 拒绝路径；扩展矩阵不再阻塞功能阶段。

## M2 — 真实 Provider 端到端闭环（已完成）

目标：不经过 fake provider 或 fault service，由 AutoJs6 宿主调用 production
INFO/RUNTIME 服务完成真正的 Lua 执行。

- [x] 增加一个显式 opt-in 的官方 Provider smoke；仓库默认开关继续保持关闭。
- [x] 在一台 ARM64 设备上跑通
  `LuaPluginScriptEngine -> production INFO/RUNTIME -> PFD -> session -> return 7`。
- [x] 同一安装内再执行 `return 8`，确认第一会话释放且第二次执行成功。
- [x] 记录插件/宿主 revision、APK 摘要、签名、远端进程和设备恢复状态。

最小验收：受影响模块编译通过；一个精确选择的设备方法返回 `OK (1 test)`；测试包和
Provider 恢复到审计前状态。Provider 仍不作为普通构建的默认可发现组件。

本阶段不做：hostile PFD 全矩阵、Android OOM、并发 BUSY、全部 API/ABI、签名发布、
AutoJs 能力模块。

## M3 — 最小 AutoJs 能力（进行中）

- [x] M3.1 实现受控 `require("autojs")`，仅开放
  `console.log(string)` / `console.error(string)`；输出继续受 sequence、credits、chunk
  和总字节上限约束。
- [x] 用真实 production Provider 运行示例脚本，验证 stdout、stderr 和标量结果返回。
- [x] M3.2 从 `ExecutionConfig` 冻结宿主参数，并安全映射为受配额约束的标量、稠密数组和
  字符串键 map；Lua 通过 `require("autojs").arguments` 读取该会话快照。
- [ ] M3.3 接入首个真正的宿主 broker 能力；Provider 声明、宿主授予，未授权明确返回
  `CAPABILITY_DENIED`。

本阶段不做：文件写入、网络、shell、UI、无障碍、通用 Java 反射和任意动态 C 模块。
timer、只读设备信息和受限模块加载可在上述最小闭环稳定后按需加入。

## M4 — 产品预览

- [ ] 保存及未保存的编辑器 Lua 脚本都可运行，并给出可理解的安装、授权和错误提示。
- [ ] 至少接通一种持久入口：定时任务或快捷方式；保留明确的 Lua engine 元数据。
- [ ] 增加受控模块快照；语法高亮可以独立交付，不阻塞执行链。

本阶段不做：完整项目系统、APK 打包、调试器、UI DSL 或 Rhino API 全兼容。

## M5 — 发布候选

- [ ] 生成签名、独立版本化、provider-enabled 的插件候选 APK，并验证默认/发布开关。
- [ ] 做精简兼容抽样：最低支持 API、当前 API、ARM64 真机、x86_64 模拟器和 16 KiB。
- [ ] 对 Rhino、Node.js、Explorer、编辑器和任务执行各做一次核心 smoke。
- [ ] 保存失败证据和回滚结果；未通过的诊断不得改写为发布通过。

## 风险与增强清单（不阻塞当前阶段）

- blocking/oversized PFD、OS 级 FD 泄漏、callback peer death、会话中 update/uninstall。
- Android process OOM、极端并发、完整 API 24～36 逐版本矩阵。
- 文件/网络/shell/UI/无障碍能力、项目/模块快照和更多句柄类型。
- Lua 5.5、LuaJIT、调试器、打包、Java interop 和 richer UI。

只有风险直接影响当前里程碑的 happy path、安全边界或资源上限时，才提升为阶段阻断项。

## 证据索引

- 协议：[`docs/dev/lua-runtime-protocol-v1.md`](../dev/lua-runtime-protocol-v1.md)
- 综合状态：[`docs/dev/lua-runtime-conformance.md`](../dev/lua-runtime-conformance.md)
- R2 Binder/PFD：[`docs/dev/lua-runtime-device-evidence-2026-08-10.md`](../dev/lua-runtime-device-evidence-2026-08-10.md)
- Native 执行：[`docs/dev/lua-runtime-native-device-evidence-2026-08-10.md`](../dev/lua-runtime-native-device-evidence-2026-08-10.md)
- 故障恢复：[`docs/dev/lua-runtime-fault-recovery-device-evidence-2026-08-10.md`](../dev/lua-runtime-fault-recovery-device-evidence-2026-08-10.md)
- 官方 Provider smoke：[`docs/dev/lua-runtime-official-provider-smoke-2026-08-11.md`](../dev/lua-runtime-official-provider-smoke-2026-08-11.md)
- M3 console smoke：[`docs/dev/lua-runtime-console-smoke-2026-08-11.md`](../dev/lua-runtime-console-smoke-2026-08-11.md)
- M3 参数 smoke：[`docs/dev/lua-runtime-arguments-smoke-2026-08-11.md`](../dev/lua-runtime-arguments-smoke-2026-08-11.md)

## 最小检查策略

1. 文档或纯策略改动：静态检查，加受影响模块 JVM 测试（如存在）。
2. Kotlin/AIDL/Native 改动：编译受影响模块，只运行对应 focused tests。
3. Binder/PFD/进程边界改动：在一台明确设备上增加一个 exact-method smoke。
4. 候选发布：再运行 M5 的兼容抽样和签名/产物门禁。

任何 Gradle 任务都必须在确认没有其他 Codex Gradle 客户端后串行启动；共享设备事务必须使用
显式 serial、审计安装前状态，并在 `finally` 中恢复。
