# 最小 Python 项目

该示例用于默认关闭的 Python 开发者预览。它验证：

- `main.py` 可以导入同一工作区中的 `helper.py`；
- `autojs6.project` 可以从本次执行的私有项目快照只读访问 `data/message.txt`；
- `autojs6.app`、`autojs6.device` 和 `autojs6.execution` 返回本次执行开始前冻结的只读信息。

项目不使用在线 `pip` 或 native wheel。宿主会把项目与能力文档复制成有界快照；
插件不会通过该 API 重新访问原始宿主路径，也不会把 Android/Java 活对象传给 Python。
