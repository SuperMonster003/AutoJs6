# Lua 编辑器产品预览证据（2026-08-11）

## 交付结果

- 编辑器运行当前缓冲区时，会从文档显示名、URI 路径和编辑器名称中识别脚本类型。
- 已打开的 `.lua` / `.LUA` 文档统一使用 `.lua` 临时快照，因此保存后的内容和未保存修改都进入 Lua proxy，不再误入 Rhino。
- `.node.js` 和既有 JavaScript 扩展行为保持不变；无法识别的无标题或未知文档仍按既有规则回退为 JavaScript。
- Lua Runtime 不可用、兼容性失败、多 Provider、身份变化或绑定失败，会在 Explorer Toast 和编辑器 Snackbar 中显示可操作的本地化提示。
- 非 Lua 异常继续显示原始诊断，不改变现有错误处理。

这里的“未保存 Lua”指已打开或已命名为 `.lua` 的文档中尚未写回磁盘的编辑器缓冲区；
完全无标题文档尚未提供语言选择器，仍按 JavaScript 处理。

## 验证

在确认没有其他 Gradle 客户端后，使用原生 PowerShell 参数数组串行运行：

```text
:app:testAppDebugUnitTest
--tests org.autojs.autojs.ui.edit.editor.service.EditorScriptRunPolicyTest
--tests org.autojs.autojs.core.plugin.lua.LuaRuntimeFailurePresentationTest
```

结果：

- `compileAppDebugKotlin`：PASS
- `compileAppDebugJavaWithJavac`：PASS
- `EditorScriptRunPolicyTest`：5/5 PASS
- `LuaRuntimeFailurePresentationTest`：5/5 PASS
- 合计：10/10，0 failure，0 error，0 skipped

第一次命令因 PowerShell 将直接书写的 `-P...` 参数拆成任务名而在源码选择前失败；改为参数数组后通过。
该失败没有形成编译或运行证据，也未被计入验收结果。

## 证据边界

本切片只改变宿主编辑器路由策略和错误展示，没有修改协议、Binder、PFD、Native、Provider
或进程生命周期，因此按路线图不重复执行设备 smoke。既有 M2/M3 设备证据继续证明 Lua proxy 到
production Provider 的执行链；本证据只证明新编辑器入口的编译与纯策略行为。

Lua 调试器、格式化、语法高亮、无标题文档语言选择、插件中心直接安装按钮和持久入口不属于本切片。
