# P2.16 / Q4: 打包应用按共享存储需求启动

日期: 2026-09-14. 源码基线: `af962199e`, 实现与本记录一并提交. 对应 Q4=(a) / D15. 本轮完成启动配置、权限引导与提前回归, P2.16 / P3.3 的完整用户 APK 和升级矩阵仍开放. 正式 compile 为 37, 宿主 / inrt target 仍为 36 / 29.

## 实现与配置

- `LaunchConfig.requiresSharedStorage` 默认 `false`, 写入 `project.json` 的 `launchConfig`. 仅访问打包 assets / 应用私有文件的项目直接启动. 需要直接读写共享路径的项目由作者显式开启, 不分析脚本来推断需求. SAF / MediaStore 项目继续按各自授权规则处理.
- `BuildActivity` 增加启动选项和说明, 同步项目加载 / 保存、撤销 / 重做、自动 / 固定字段策略以及打包配置的保存 / 导入 / 导出. `BuildConfigStore` 对旧配置默认补 `false`. `ProjectConfig` 的容错键修复和远程序列化保留新字段; `ProjectConfigActivity` 沿用项目配置编辑 / 保存入口.
- `InrtStorageAccess` 检查实际所有文件访问状态, 或实际可用的传统存储模式与 READ / WRITE 授权. Android 11+ 的专用设置入口带通用设置回退; Android 10 及以下请求运行时权限, 永久拒绝后进入应用设置. 权限声明缺失时提示重新打包. 勾选启动选项不会改写作者自定义的 Manifest 权限列表.
- `InrtLaunchGate` 供 `SplashActivity` 和带启动标志的 `LogActivity` 共用. 取消或未授权返回不会执行脚本; Activity Result 到达后重新查询权限. SavedStateRegistry 和 DialogFragment 保存等待阶段, 避免设置返回 / Activity 重建重复执行. Splash 的等待使用生命周期协程.
- `Pref` 在初始化、升级同步、设置返回与授权后重新计算桌面入口. 配置要求共享存储但尚未授权时保留入口; 授权后应用原有隐藏配置. 设置页增加 "运行脚本", 所有文件访问开关根据实际授权同步, 不把打开设置视为已授权.
- `AssetsProjectLauncher.runScript` 在创建引擎前再次检查共享存储需求, 同时覆盖开机入口和界面授权后的状态变化. 缺少权限时记录原因并恢复入口, 不从后台弹出授权界面. 这不豁免其他后台启动规则.
- 5 个字符串同步到 11 份资源, 默认与英文一致, 插入顺序及半角标点通过检查. 主仓库 10 份 changelog 增加 improvement 并重新生成 README / CHANGELOG. 冻结的 `com/stardust/**` 未修改, 本轮没有调整 Gradle 依赖.

平台依据: [所有文件访问](https://developer.android.com/training/data-storage/manage-all-files) 要求声明权限并检查 `Environment.isExternalStorageManager()`, 不授予其他应用私有目录访问. [Android 11 存储规则](https://developer.android.com/about/versions/11/privacy/storage) 区分旧 target 的传统模式与更新 target 后的分区存储. 本轮不会用单个权限回调推断具体文件可读写.

## 真实 Activity 与文件读写

[机器可读结果](android-sdk-37-inrt-storage-results-20260914.json) 包含逐项结果、实际脚本回执、设备指纹、APK SHA-256 和签名指纹. 原始命令、UI XML 与构建日志位于忽略目录 `build/sdk37-inrt-storage-20260914/`.

| 系统 | inrt target | 最终记录目录 | 结果 |
| --- | --- | --- | --- |
| API 24 | 29 | `api24-normal-final` | 8/8 |
| API 24 | 37 | `api24-target37-verified` | 9/9, 包含系统 "Don't ask again" 与应用设置恢复 |
| API 37 | 29 | `api37-normal-verified` | 8/8 |
| API 37 | 37 | `api37-target37` | 8/8 |

最终共 33 项. 四组分别检查私有脚本无存储授权启动、取消不执行、未授权返回不执行、授权后实际共享文件读写、缺少权限时保留隐藏项目入口、Log 入口复核授权并恢复隐藏配置、设置页运行脚本、Manifest 缺少权限时停止. 两组 API 37 都在系统设置授权后杀掉后台应用进程, 返回后仅产生一次新的成功回执.

测试通过普通 `am start` 进入真实 Activity, 不在 instrumentation 内执行存储脚本, 未启用 `no-isolated-storage`. 共享文件使用专用 `/sdcard/Download/autojs6-sdk37-storage-*.txt`, 脚本必须读回初始内容并写入新内容; 私有回执位于 `files/storage-result.jsonl`, 更新项目 assets 不会覆盖该文件. XML 和实际文件结果分别证明 UI 与执行结果.

另有 `SharedStorageBuildConfigDeviceTest` 在 API 24 / 37 的正常 target 29 inrt 上各通过 3/3, 共 6 个 JUnit 用例. 验证旧项目默认值、显式 true / false 的项目与远程 JSON 往返、打包配置保存 / 导入 / 导出及固定字段策略. 逐方法核对开始和成功状态, 未跳过或重复计数.

## 覆盖升级和开机的实际边界

API 37 上完成同 applicationId / 同测试签名的 target 29 -> 37 覆盖升级, versionCode 从 8001 增至 9001. `upgrade-manager` 的 4 项检查确认:

1. target 29 先具有实际 `MANAGE_EXTERNAL_STORAGE` 授权, 私有和共享读写成功.
2. 覆盖 target 37 后, 启动前已确认旧私有回执与共享文件保留; 授权继续有效, 启动后继续读写原文件.
3. 撤销授权后显示引导, 未执行脚本, 原回执数量与共享文件内容不变.
4. 再授权后恢复对原共享文件的读写.

当前模板的 `READ_EXTERNAL_STORAGE` 带 `maxSdkVersion=32`, `WRITE_EXTERNAL_STORAGE` 带 `maxSdkVersion=29`. 在 API 37 上直接 `pm grant` 返回 0 未建立传统 READ / WRITE 授权基线, 因此早期 legacy 尝试未计为通过. 本轮成功结论只适用于已有所有文件访问授权的上述升级路径. 未添加或决定采用 `preserveLegacyExternalStorage`; 历史旧包、API 30 / 33 和独立首次安装 / 卸载重装仍须分别验证. [官方迁移说明](https://developer.android.com/training/data-storage/use-cases#maintain-access-to-the-legacy-storage-location-for-data-migration) 将该标记用于升级迁移, 不保证新安装保留传统模式.

target 37 / API 37 的私有脚本完成过一次真实模拟器重启, 重启前后各产生一条成功私有文件回执, 所有文件访问均为 false. 早期立即重启后观察到包的 `stopped=true / notLaunched=true`, 没有第二条回执; 等待 PackageManager handlers 与状态落盘后该私有场景通过, 但未证明早期状态的根因. 第二次重启拟检查缺少共享授权的开机路径时, 模拟器 ADB `getprop sys.boot_completed` 超时 120 秒, 未取得结果. 因此只有 1 个实际私有开机检查计为通过, 共享拒绝 / 授权、无界面和 OEM 开机矩阵继续开放.

## 夹具与可复跑范围

`tools/sdk37/build_inrt_storage_fixtures.py` 从当前 inrt debug APK 替换 `assets/project` 和 versionCode; 声明缺失场景另移除三个存储权限节点. DEX、资源、SDK 声明和 native 库保留模板原值, 对齐后以独立短期测试证书签名. `run_inrt_storage_probe.py` 只接受明确归属的 emulator serial / AVD, 空 inrt 安装基线和独有共享路径, 安装四个递增版本的 disposable APK. 复跑命令见 [工具说明](../../../tools/sdk37/README.md#packaged-app-storage-startup).

这些夹具验证实际 inrt Activity 和 Rhino 执行, 没有经过真实 APK Builder、加密项目或发布的 Runtime Kit. 只读检查 APK Builder Template 的远程 JSON 处理仍保留 `launchConfig`, Manifest 编辑也保留权限上的 maxSdkVersion, 未修改该仓库. 最终用户项目须通过 P3.4 / P5 的真实打包流程验收, 本轮未生成或发布新 Runtime Kit.

## 构建与关联项目

- 主仓库正常宿主 / inrt 的 debug、release 及 inrt AndroidTest assemble 成功; 最终常规构建 3m 36s, 修正配置测试后的 AndroidTest 增量构建 11s. target 37 debug 覆盖构建成功, 仅用于诊断. 最终四个 APK 经 aapt2 / apksigner 核对, target 为宿主 36 / inrt 29, 签名均为原签名 `31a681fc...ae4c213`; 完整 SHA-256 在 JSON 中.
- assemble 触发 native page alignment 检查并通过. 已知 `RemoveWorkManagerInitializer` Fatal Lint 仍存在, 当前构建配置未因此中断; 不宣称完整 Lint 通过.
- Documentation 的 QA / progress 已更新, 执行 `auto-generate-for-autojs6.bat --dry-run` 与正式 `--verify-offline`, 生成 129 个模块, 同步 QA / progress / 搜索索引三个资产. Markdown normalize 与生成器 `--check` 通过. 文档版本 6.8.0 / 68.
- Offline Docs 为 6.8.1 / build 41, 10 语言 changelog 与生成文档已同步; 2 个单元测试通过. 最终 debug / release 构建及 `verifyOfflineDocsApks` 通过, 185 个文档资产共 10615957 bytes, 内容摘要 `08b023de666f31777014efda5e3e00f857ef2d8498a56a787bccd766bd080472`.
- `aj6dts.bat -Publish` 执行本地声明生成、TypeScript 验证和复制, 没有 npm 发布. 声明包版本 4.13.0, 新增 LaunchConfig 属性及 getter / setter; 资源和 libraries 声明同步当前构建产物, 包括前轮 Media3 后的依赖闭包. 该仓库没有独立 build 字段. 两仓 `aj6-int-*` 已按文本逐个核对一致, 无需修改内容.
- Ace Editor 为 1.4.0 / build 93, 10 语言 changelog 和生成文档已同步. 执行 `generateAutoJs6LspDeclarations`, 生成结果无需额外 LSP 文件差异. 31 个测试 suite 共 167 项通过, debug / release 构建及 native 对齐通过. 未升级这两个插件自身的 Gradle 工具链, 未将本地构建计作 Q6 独立版本已经发布.

关联仓库本地提交: Documentation `f14dde4`, Offline Docs `806f852`, TypeScript Declarations `0d8d683`, Ace Editor `4f99d9b`. 插件 build 41 / 93 与提交后的 HEAD 计数一致, 四仓工作区在提交后干净.

## 失败记录与清理

- 首次编译因 DialogFragment 的 `activity` 遮蔽外层非空 Activity 失败, 修正显式外层引用后通过.
- 首次配置测试使用不合法的空 ProjectConfig JSON, 3 项中 2 项失败. 改用带名称 / 包名 / 版本的有效最小项目后 6/6 通过, 未放宽生产解析校验.
- 早期 UI 复跑修正 API 24 的 emulator 属性判断、不支持的 shell 权限命令、设置界面等待和升级广播恢复桌面入口的等待. 最后一组 API 37 普通包初次冷启动超过旧 20 秒回执期限, 超时后的读取已有成功回执; 调整为 60 秒并保留最后一轮数据检查后完整 8 项通过. 这些部分运行不重复计入最终 33 项.
- 对一次升级后桌面入口延迟使用过 IntelliJ 临时 JDWP 附加与非暂停 tracepoint, 没有采集到 tracepoint 值. 仅保留后续系统组件状态和实际 UI 证据, 不声称获得调试器运行时根因. 已移除本轮附加配置、断点和端口转发, 用户原有断点未改变.
- 使用 `AVD_API_24 / emulator-5594` 与两个先后运行的 `AVD_API_37 / emulator-5598, emulator-5596` 只读实例. 首个 API 37 在开机超时后关闭, 后两个在完成矩阵后按 PID / 启动时间 / 路径 / AVD 身份核对并关闭. 共享 ADB server 保留, 未改动实体机. Samsung `localhost:37478` 在本轮离线.

下一批实体机重点为 S01 / I02 / I03 / I05, 仍保持完整清单的 99 行未验收状态. 最终 target 切换、真正用户 APK 的升级 / 首装 / 重装、所有共享存储开机分支以及真实 16 KB 内核设备继续由相应 Roadmap 门禁跟踪.

收尾检查时工作区另出现图像 / 截图 / OpenCV / ScriptRuntime 等并行改动, 时间晚于上述构建和测试. 本轮按明确文件路径提交存储适配, 这些额外改动留在工作区, 不计入本记录的验证范围.
