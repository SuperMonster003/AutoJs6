# Python 项目开发者预览

Python 项目功能建立在独立 Python Runtime 插件之上，当前只面向显式开启的 Debug 开发者预览。Release 构建、未开启开关的 Debug 构建和不受支持的入口仍会安全拒绝，不会回退到 JavaScript/Rhino。

## 最小项目结构

```text
my-python-project/
├─ project.json
├─ main.py
├─ helper.py
└─ data/
   └─ message.txt
```

`project.json` 只需声明项目类型和入口：

```json
{
  "type": "python",
  "main": "main.py"
}
```

入口必须是项目目录内已经存在的普通 `.py` 文件。绝对路径、URI、反斜线、空路径段、`.`、`..`、符号链接逃逸和 `.pyc` 都会被拒绝。

可运行示例位于 [`sample/python/minimal-project`](../../sample/python/minimal-project/README.md)。

## 运行模型

1. 宿主严格解析项目清单并固定项目根目录与入口。
2. 宿主把普通项目文件复制到私有、有界的 ZIP 快照；入口源码使用独立 PFD 发送，ZIP 不包含也不能遮蔽入口。
3. 插件再次校验 ZIP 路径、重复项、大小与摘要，在插件私有目录中发布临时执行快照；脚本可以在该临时目录内写入，但不会写回原项目。
4. CPython 以项目快照作为当前目录和 `sys.path[0]`，因此可进行项目内相对文件访问和普通 Python import。
5. 执行终止后，session、PFD、工作区和运行时进程按既有生命周期清理或退休。

当前预览上限为 16 MiB 压缩快照、1024 个工作区条目和 32 MiB 解压后数据。入口源码仍受独立 source 上限约束。

## 常用入口范围

| 入口 | 单文件 `.py` | Python 项目入口 | 当前约束 |
| --- | --- | --- | --- |
| Explorer Run | 支持 | 支持 | 项目入口由 `project.json` 严格确定 |
| 本地编辑器 | 支持 | 支持 | 使用最新编辑内容；只有清单声明的入口获得 workspace，项目内其他 `.py` 不冒充入口 |
| Run Intent | 本地路径或 `file:` URI 支持 | 裸路径保持单文件语义 | Python `content:` URI 不执行；项目上下文不作为公开外部 Intent ABI |
| Import Intent | 支持导入单个 `.py` | 不支持项目树 | 可依据文件名或 Python MIME 保留 `.py`；拒绝 `.pyc` 与 `project.json` |
| 新建快捷方式 | 支持 | 支持 | 创建时固定版本化项目上下文 |
| 新建定时/广播任务 | 支持 | 支持 | 数据库保存 source kind 与项目根 |
| 旧快捷方式/旧任务 | 保持原单文件语义 | 不自动升级 | 如需项目语义，应重新创建 |
| Widget、Tasker | 保持既有入口 | 暂不支持项目上下文 | 后续按需求增量接入 |

以上支持项仍要求显式 Debug 预览开关；关闭开关时继续 fail-closed。

## 当前能力

已纳入最小项目预览：

- `type: "python"` 与规范化相对 `main`；
- Python 源码和普通数据文件；
- 项目内 import、标准库和相对文件读取；
- Explorer 的 Run 入口。

暂未纳入：

- Widget 与 Tasker 的 Python 项目上下文；
- content URI 直接执行 Python、项目目录/树导入及公开 Python MIME chooser 注册；
- 项目 Build/Edit 配置界面；
- 在线 `pip`、wheel、native extension 或动态下载；
- 打包 APK；
- AutoJs6 capability broker、UI 脚本和任意 Java 对象互操作；
- 对原项目目录的写回。

这些缺口不会改变安全边界：不支持的路径必须返回稳定错误，Python 项目不会被旧引擎接管。
