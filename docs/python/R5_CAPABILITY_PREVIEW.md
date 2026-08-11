# Python `autojs6` 最小能力预览

R5 首批能力采用“执行启动时冻结的一次性快照”，不建立双向 Binder broker。这样可以先提供常用只读信息，同时保持现有的单向请求、PFD 所有权和禁止调度后重放边界。

## 开启条件

能力 API 与 Python 开发者预览共用同一个默认关闭开关：

```text
-Pautojs.python.preview.enabled=true
```

它只在 Debug 构建中生效。宿主还会要求可信的官方 Python Runtime 支持协议 1.1 能力快照；不满足时稳定拒绝执行，不降级到 JavaScript。

## API

```python
from autojs6 import app, device, execution, project

print(app.snapshot())
print(device.snapshot())
print(execution.snapshot())
print(project.read_text("data/message.txt"))
```

- `app.snapshot()`：宿主包名、版本名、版本号和是否为 Debug 构建。
- `device.snapshot()`：API level、Android release、厂商/品牌/型号和 ABI 列表；不包含 serial、Android ID、IMEI 或 fingerprint。
- `execution.snapshot()`：本次 request ID、逻辑入口以及是否具有项目 workspace。
- `project.exists(path)`、`read_bytes(path)`、`read_text(path)`：只访问本次执行的插件私有 workspace；单文件执行没有该 grant。

项目路径必须是 NFC 规范化的 `/` 分隔相对路径，拒绝绝对路径、URI、反斜线、空段、`.`、`..`、链接和特殊文件。单次读取最多 1 MiB，文本只支持严格 UTF-8。

## 边界

- 宿主只发送有界 JSON bytes；不跨 Binder 或 Python 边界传递 `Context`、`ScriptRuntime`、View、Binder 或任意 Java 活对象。
- snapshot 绑定 request ID、entry point 和 project grant，并通过独立 PFD 的精确长度、EOF 与 SHA-256 校验。
- 返回值是脱离内部状态的副本；修改字典不会改变后续读取结果。
- `project` 是对 R4 私有 workspace 的只读便利层，不重新读取宿主实时项目，也不写回宿主。
- 该 API 是能力边界，不是 Python 沙箱。当前 Chaquopy 自带 Java bridge；对不可信脚本的强隔离仍依赖独立 APK/UID/进程与最小权限。

网络、shell、数据库、辅助功能、UI、调度器和任何有副作用能力均未开放。只有实际出现需要宿主特权或实时状态的用例时，才在后续阶段引入 execution-scoped 双向 broker。

示例见 [最小 Python 项目](../../sample/python/minimal-project/README.md)。
