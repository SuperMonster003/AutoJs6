# 脚本 sqlite 模块 onDowngrade: dbdowngrade_p3_api24 / 6.8.0 (5935)

- 设备: dbdowngrade_p3_api24 (emulator-5642), Android 7.0 / API 24
- 脚本: `tools/db-downgrade/sqlite_ondowngrade.py` + `sqlite_ondowngrade_probe.js` (ROADMAP P3.1, D6 / T5)
- 构建: 隔离克隆 E:/.cache/aj6dbp3 (HEAD 2f0663ba8 + 本条的 Database.java 与 SqliteModuleTest.kt) 的 debug x86_64 5935 (md5 e0acdf85); 宿主存储权限经 adb 授予 (RunIntentActivity 运行任何路径前都检查), 无点按
- 每节一次运行, 只记通过 / 失败与版本号, 行数, 回调事件 (R4)

## 10:52:42 org.autojs.autojs6 PASS

- probe 结果 8 s 内取得
- 设备文件 已删除 (files/aj6-db-downgrade-p3, /data/local/tmp/aj6-db-downgrade-p3.js)

| 项 | 结果 | 数值 |
| --- | --- | --- |
| plain: 无回调 2 -> 1 | PASS | 建库 v2, 打开 v1 后 v1 / 行 2; 再以 v2 打开 onUpgrade ['1->2'], v2 |
| partial: 回调无 onDowngrade | PASS | v1 / 行 2, 事件 ['open'] |
| callback: onDowngrade 参数与顺序 | PASS | 事件 ['downgrade 3->2', 'open'], 参数 3 个 ['number', 'number'], database org.autojs.autojs.core.database.Database, 回调内 v3 事务 True, onOpen 内 v2, 打开后 v2 / 行 3 |
| throw: onDowngrade 抛出 | PASS | 抛出 True (Failed to call method "sqlite.open". p3 refuses the downgrade.), 之后 v2 / 行 2 |
| readonly: readOnly 2 -> 1 | PASS | v1 / 行 2 |

## SqliteModuleTest 设备用例 (`tools/augment/device_eval.py --cases app/build/script-modules-device/SqliteModuleTest.json --strict-errors`)

| 项 | 结果 | 数值 |
| --- | --- | --- |
| 新层开 (`--new-modules on`) | PASS | 25 / 25 与 JVM 记录一致 |
