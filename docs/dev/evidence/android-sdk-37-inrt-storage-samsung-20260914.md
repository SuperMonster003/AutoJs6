# P2.16 / Q4: Samsung 真机存储启动补验

日期: 2026-09-14. 维护者恢复 `localhost:37478` 后执行, 存储实现提交 `e2e4d599d`. 本轮复用 [上一轮已验证的 inrt 运行时](android-sdk-37-inrt-storage-20260914.md), 没有重新编译生产代码. 运行期间另有图像 / 截图 / OpenCV / 脚本 API 改动提交为 `a968da723`, 不在本轮冻结产物的测试范围内.

## 设备与产物

- Samsung SM-F776B, Android 17 / API 37, ARM64, user / release-keys. 指纹 `samsung/b8sxeea/b8s:17/CP2A.260605.016/F776BXXU1AZGI_OXM1AZGI:user/release-keys`, `getconf PAGESIZE=4096`, 不属于真实 16 KB 内核.
- 开测时只有 owner user 0, 英文 UI, 不存在 AutoJs6 宿主 / inrt / 插件或保留数据的包. runner 每组重新确认这组条件及设备指纹, 未通过条件时不安装或删除已有应用.
- 从上一轮 `normal-final-fixtures/private.apk` 和 `target37-fixtures/private.apk` 派生本轮三个夹具集. 仅改测试项目、versionCode, 声明缺失场景另移除存储权限; 12 个 APK 的 DEX / resources.arsc / native 库各 53 个条目均按 SHA-256 与对应来源逐项核对一致.
- 全部测试项目显式设置 `runOnBoot=false`. 使用原短期测试证书 `b51cdbb230936f6374e8256d0ac90813c8cee4c108d45624b166a6e56b4d38f8`, 并以 aapt2 / apksigner 检查实际 target、版本和签名. 正常组版本 11000-11003, target 37 组 12000-12003, 升级组 13000-13003.
- 普通 Activity 驱动真实 Rhino 脚本, 未使用 instrumentation 或 `no-isolated-storage`. 脚本仅读写自身私有文件及新建的专用 `Download/autojs6-sdk37-storage-samsung-*.txt`, 回执写入包私有 `files/storage-result.jsonl`.

## 结果

三组真机检查共 **20/20**, 不重复计入首轮锁屏中断前的部分结果.

| 分组 | target / versionCode | 结果 |
| --- | --- | --- |
| 正常 UI / 文件读写 | 29 / 11000-11003 | 8/8 |
| 覆盖目标 UI / 文件读写 | 37 / 12000-12003 | 8/8 |
| 同签名覆盖升级 / 撤销 / 再授权 | 29 -> 37 / 11001 -> 13001 | 4/4 |

两组 UI 都在系统设置授权后实际退出 fixture 进程, 返回后只新增一条脚本回执. 每组清理前的共享文件均为初始内容后追加三次 `shared-write-ok`, 与脚本回执对应, 证明实际写入.

[机器记录](android-sdk-37-inrt-storage-samsung-results-20260914.json) 保存逐项结果、脚本回执、产物哈希、运行时字节对照和清理情况; 原始命令 / XML / 日志位于忽略目录 `build/sdk37-inrt-storage-samsung-20260914/`.

UI 矩阵覆盖私有脚本无共享授权启动、取消不执行、未授权设置返回不执行、授权后的共享文件读写、设置期间进程退出后的单次恢复、隐藏桌面入口保留与授权后隐藏、Log / 设置页运行，以及权限声明缺失提示.

覆盖升级单独建立 target 29 的所有文件访问授权, 先以脚本实际读写验证, 再同包名 / 同签名、递增 versionCode 覆盖 target 37. 记录原私有回执和共享文件是否保留, 升级后不先 grant; 随后检查撤销时停止及重新授权后恢复. 此路径不代表传统 READ / WRITE legacy 授权迁移.

## 工具与边界

`run_inrt_storage_probe.py` 增加显式 `--physical-device` / `--expected-fingerprint` 模式及 `--upgrade-fixtures` 路径. 真机模式要求 `build_inrt_storage_fixtures.py --disable-boot`, 校验 APK 哈希、项目路径、实际 target 元数据和开机选项. 只对本轮新安装的 fixture 改变存储授权; 不改系统语言、屏幕超时、锁屏配置或 compat override, 不重启手机.

每组完成或测试失败时, 先读取安装 APK 的设备端 SHA-256, 仅在匹配本轮夹具时卸载; 再检查专用共享文件的内容符合测试写入格式后删除, 清理唯一命名的 UI XML. 指纹、包或文件身份改变时保留现场并报告清理失败. 8 个 Python 测试覆盖错误设备、已有宿主、额外 profile、开机夹具、文件哈希变化以及成功 / 拒绝清理, 全部通过. [复跑方法](../../../tools/sdk37/README.md#packaged-app-storage-startup).

第一轮正常包只有私有脚本检查通过, 后续 UI XML 显示三星普通滑动锁屏, 共享权限提示未进入可操作前台. 该部分运行不计入最终成功数, 其测试 APK / 文件已清理. 后续 runner 在每次界面操作前唤醒屏幕并调用 `wm dismiss-keyguard`, 不提供凭据或修改锁屏设置; 安装增加 `--no-incremental`, 避免增量安装回退等待. 没有据此认定存储实现存在故障.

本轮仍属于冻结运行时的提前真机验证. 最终 Runtime Kit / 真实 APK Builder、独立用户 applicationId 的首装与卸载重装、历史 legacy 授权及 `preserveLegacyExternalStorage` 决策、其他 API / OEM、真实开机和无界面完整链继续开放. S01 / I02 / I03 / I05 仅补充已测子项, 不关闭 99 行完整实体机清单.

结束时设备仍在线, AutoJs6 包清单 (含保留数据项) 为空, 两个专用共享文件均不存在, 四次运行的 APK / 文件 / UI XML 清理全部成功. 设备端安装 APK 哈希均与本轮清单匹配后才卸载. 未创建 ADB 转发或后台 PC 服务, 未重启共享 ADB server; 系统内存限制器结束时 disabled, Advanced Protection false. 命令审查确认没有 compat / 系统设置写入, 存储授权调整只作用于新测试包.
