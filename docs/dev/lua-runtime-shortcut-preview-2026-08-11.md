# Lua 快捷方式描述符证据（2026-08-11）

## 交付结果

- 新建 `.lua` / `.LUA` 快捷方式时，在既有 `path` 之外保存固定 V1 字段：
  `schema=1`、`engine=lua`、`sourceKind=file`。
- legacy broadcast、pinned shortcut 和 `Scripts.createShortcut` 三个创建分支共用同一个 Intent factory。
- 启动时仍先由 `ScriptLaunchSourceFactory` 根据真实路径创建 Launch，再比较实际 engine；描述符只作一致性断言，不能选择引擎。
- 三个描述符字段全部缺失时按旧快捷方式处理，因此既有 Lua、JavaScript 和 Auto 路径继续按扩展名路由。
- 任一字段出现但不完整、类型错误、schema/source kind 未知，或 Lua 描述符指向 JS/Auto/未知路径时明确拒绝，不降级重试。
- 拒绝时提示用户重新创建快捷方式；路径改名或删除仍按原路径报错，不搜索或自动重绑定。

描述符不保存 Provider 包名、Binder component、UID、签名、runtime slot 或 capability，也不赋予新的权限。
`ShortcutActivity` 继续只读取直接的 path extra，没有改用接受 data URI、JSON 或 inline script 的通用 Intent 入口，
也不会把 Android `Intent` 注入 Lua 参数。

## 验证

在检测到另一个 worktree 的 Gradle 客户端后先等待其退出，并取得两次连续空闲样本；随后串行运行：

```text
:app:testAppDebugUnitTest
--tests org.autojs.autojs.external.shortcut.ScriptShortcutLaunchDescriptorTest
--tests org.autojs.autojs.script.LuaScriptSourceRoutingTest
```

结果：

- `compileAppDebugKotlin`：PASS
- `compileAppDebugJavaWithJavac`：PASS
- `ScriptShortcutLaunchDescriptorTest`：6/6 PASS
- `LuaScriptSourceRoutingTest`：7/7 PASS
- 合计：13/13，0 failure，0 error，0 skipped

## 证据边界

这是宿主持久 Intent 格式、路由策略和 App 编译证据。它没有修改 Lua 协议、Binder、PFD、Native、
Provider 或进程生命周期，因此没有重复运行 production Provider 设备 smoke。Launcher UI 自动化、图标样式、
定时任务数据库的 engine 字段和完整任务恢复 smoke 留到后续产品或发布阶段。

外部调用者仍可完全省略描述符并进入既有 exported legacy path 行为，因此 V1 不是来源鉴权机制；
它只保证由本版本创建的 Lua 快捷方式不会借助 metadata 把其他路径强制切换成 Lua。
