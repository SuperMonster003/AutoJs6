# Samsung 原生 16 KB 补验与剩余事项, 2026-09-11

结论: 宿主与 16 个原生插件取得真实运行时证据, 30 项 instrumentation 全部通过且无跳过. 6 个独立 OCR 插件在新增图片用例中失败; 其中 Paddle v3 的另一张参考图补测通过, 仍存在可重复崩溃. Three-Stone-AI 尚缺真实模型输入, P6 保持开放. 用户 APK 的嵌入式 OCR 另有通过收据, 不可用它替代独立插件进程的完整验收.

对应 [完整 JSON](page-size-16kb-samsung-20260911.json), [主 Roadmap](../page-size-16kb-roadmap.md) 与 [维护者收尾清单](../page-size-16kb-maintainer-checklist.md).

## 设备与产物身份

| 项目 | 实测值 |
| --- | --- |
| 用户提供的连接 | `localhost:30078` |
| 设备 / ABI | Samsung SM-A566B, `arm64-v8a` |
| 系统 | Android 16 / API 36 |
| 实际页大小 | `getconf PAGE_SIZE=16384`; 宿主与用户 APK 的进程内读取也为 16384 |
| 验收时的 linker 兼容模式 | `bionic.linker.16kb.app_compat.enabled=false`, 写入后读回 |
| 验收时的 PM 兼容模式 | `pm.16kb.app_compat.disabled=true`, 写入后读回 |
| 被测包的系统兼容状态 | 宿主及 24 个插件 / Builder 应用全部 `pageSizeCompat=0` |
| 宿主 | AutoJs6 6.8.0 / build 5279, arm64 Debug |
| 宿主 APK SHA-256 | `1b576582db30881ab856d4f11ec79529ab6241a0d81d860911bb480aa2260777` |
| 源码收集时宿主 HEAD | `d17d125783426f89ee446a6352551a6a7b873150` |

使用已有本地产物安装, 每个 APK 安装后的 `base.apk` SHA-256 均与安装输入一致. JSON 记录 APK 路径, 构建时间, 收集时 HEAD, 版本与完整 SHA. 收集时 HEAD 不是干净 Release 构建来源的证明; 其它插件工作区既有修改保持原样. 本轮没有运行 Gradle, 发布或推送.

25 个应用 APK 的直接 `lib/` ELF / SDK `zipalign -c -P 16 4` 检查全部通过. Python 和 Builder 的归档内扫描由先前相同 SHA 对应的共享门禁记录提供, 不将直接 `lib/` 扫描冒充递归扫描.

## 通过的真实运行时测试

| 测试组 | 数量 | 实际行为 |
| --- | ---: | --- |
| 宿主原生库 / 图像 / 插件中心 | 5 | libc++ / 两个 jackpal JNI, PTY 子进程退出 37, OpenCV 运算与异常处理, 图像量化, 实际 ViewHolder 对齐标记 |
| 宿主语言与 provider | 7 | Node 执行, Lua 顺序会话 / 释放, MediaInfo snapshot, TypeScript Binder 编译, Python 公开引擎及 ssl / sqlite3 / ctypes, MLKit Barcode 与 OCR live provider |
| Yolo-NCNN | 1 | NCNN 参考图片推理, 5 个检测结果符合类别 / 置信度 / 框的断言 |
| ImGui | 1 | 真实渲染生命周期与语义截图基线 |
| QuickJS | 3 | 发现 / prewarm / 语言执行, 超时 / 取消 / 输出与输入边界, manifest 契约 |
| OpenCC | 1 | 真实服务转换测试 |
| Archive-Manager | 3 | Unicode TAR 容器组含 native Zstd, 源大小变化失败处理, 取消清理 |
| Ace-Editor | 1 | 实际 LuaLS 语义补全, 人为中止后的恢复及资源释放 |
| Bun | 8 | arm64 原生 payload / 锁, JS / TS / 示例执行, 超时 / 取消 / 输出上限与子进程回收 |
| 合计 | 30 | 全通过, 无 Assume 跳过 |

LuaLS 的独立运行记录为 `path=semantic`, 17 个补全, 2 个诊断, 1 次预期进程中止与 1 次恢复, 活跃 provider 最终为 0. 这不属于不支持 ABI 时的回退分支. Bun 的 arm64 结果不覆盖仍为 B1 的 x86_64 JSC 故障.

## 独立 OCR 失败

通过宿主公开 `ocr.recognizeText` 调用每个 provider, 输入为测试生成的 1000 x 320 英文图片. 各版本通过 `variant` 明确选择. 宿主捕获 `DeadObjectException`, 对应原生 / Java 崩溃日志证明 provider 已退出.

| 对象 | 被测变体 | 已确认的失败 |
| --- | --- | --- |
| Paddle v3 | 默认 | `libpaddle_light_api_shared.so`, `SIGSEGV / SEGV_ACCERR`, 首帧偏移 `0xc7a44`, Build ID `46c961e2f8cc425ebc702c59f28a96caf6c4980e` |
| Paddle v4 | Server | 旧 `libc++_shared.so` 初始化 SIGSEGV, 首帧 `0x5c15c`, 下一帧 `std::__ndk1::ios_base::Init` |
| Paddle v5 | Thai | 与 v4 相同的库 Build ID 和初始化路径 |
| Paddle v6 | Tiny | 与 v4 相同的库 Build ID 和初始化路径 |
| Rapid v3 | Mobile | `dlopen failed: library "libc++_shared.so" not found`, 由 `libopencv_java4.so` 依赖触发 |
| Rapid v6 | Small | 同样缺少 OpenCV 的 C++ runtime 依赖 |

Paddle v4-v6 的上述 libc++ Build ID 均为 `ef6e7301b33650c6ab99d63132badbf97c1a64a8`. 其它模型 / 语言变体没有逐个运行, 后续应先比较其 native SHA 再决定复验范围. JSON 保留程序头, 实际错误, 崩溃日志摘要与本地原始日志 SHA.

旧 libc++ 的 RELRO 末端 `0x111000` 是可写数据保护边界的调查线索, 不在本记录中宣称已确定根因. 简单要求所有 RELRO 末端对齐也会误判本轮可运行的某些库, 必须结合 LOAD 的布局与页间空隙判断.

## 用户 APK 对照

实际通过 APK Builder 生成 / 签名 / 安装并执行用户 APK, 检查每个 `.so` 保持 DEFLATED, `extractNativeLibs=true`, 64 位 ELF 对齐合规. JSON 的 `userApks` 保存每种组合的完整结果及 SHA, 不靠版本名称关联不同二进制.

已通过的组合均输出 `terminalExit=37`, 图像宽度 `944`, 量化结果 `19924` 字节, Paddle v3 与 Rapid 均返回 `['测试']`. 组合包含 Rapid v3 / v6, 并分别检查选择 / 不选择独立 OpenCV 插件的路径.

独立 Paddle v3 APK 与已通过用户 APK 中的 `libNative.so`, `libpaddle_light_api_shared.so`, `libc++_shared.so` 三个文件 SHA 完全相同, `libopencv_java4.so` 不同. 随后的输入对照中, 独立插件也成功识别与用户 APK 相同的 `944 x 640` 参考图, 输出 `['测试']`; 对宿主和插件同时 force-stop 后, `1000 x 320` 生成图再次触发崩溃. 因而 v3 属于部分通过, 仍需定位输入 / 初始化相关的原生问题. 本轮没有做同一失败输入的 4 KB 对照, 不能断言它是 16 KB 独有, 也不能直接判定 Paddle-Lite 整体不支持 16 KB.

最初一份用户 APK 因额外安装的 Paddle flavor 不在测试夹具的包名禁用列表中, 选到了不匹配的模型资产, 返回 `No embedded Paddle OCR assets found`. 初始失败收据保留; 隔离为 Paddle v3 后重新生成的收据单独记录. 这也提示最终用户 APK 验收需要明确所选模型变体, 不能只记录仓库名称.

## 测试准备与范围限制

- OCR 测试脚本最初在 native 调用前遇到文件启动权限及 Rhino 颜色重载问题; 改用明确脚本 Intent 与 `drawColor(int)` / `setColor(int)`, 并使用仓库实际存在的 `ImageWrapper.ofBitmap` 后才执行 native 组. 这些夹具准备错误与上述 provider 崩溃分开记录.
- 使用可信签名的 Lua Release 和显式开启 remote build 的 APK Builder 资格测试包. 产品默认开关与信任校验未修改.
- Three-Stone-AI 仅完成安装与静态检查, 尚未导入 `.litertlm`; 已请求模型本机路径. 不把在线 / fake provider 结果作为本地推理证据.
- 最终 Release 候选, 合并后重新生成的 Runtime Kit, 宿主 / 插件远程 CI 与公开资产索引刷新仍未完成. 本轮设备结果不能提前关闭这些项.
- QuickJS-Host 已由维护者合并到 master 并移除工作树; D11 已关闭. 其它 Q1-Q4 保留已实现默认值, 不冒充维护者新回答.
- 收尾已恢复三个 Paddle flavor 的包启用状态, 宿主临时存储 AppOp 与两个兼容模式属性的原始值. 设备页大小仍为 16384, 测试包与用户 APK 保留供维护者检查. 后续严格验收须重新关闭兼容模式并读回, 不能将本轮验收时的属性值误当作设备当前值.
