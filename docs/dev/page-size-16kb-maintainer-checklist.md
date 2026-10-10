# 16 KB Roadmap 维护者收尾清单

更新日期: 2026-09-11. 对应 [主 Roadmap](page-size-16kb-roadmap.md), [部署证据](evidence/page-size-16kb-20260911.md) 及其 [完整产物记录](evidence/page-size-16kb-20260911.json).

本文区分必须完成的验收 / 发布工作, 已采用但未记作维护者明确回答的默认值, 以及不阻塞本 Roadmap 的后续建议. 原 Roadmap 第 3 节是部署前基线, 其中旧 NDK / 4 KB 库描述不是尚未修复的现状.

## 0. 2026-09-12 更新 (第二轮)

详见 [第二轮证据](evidence/page-size-16kb-round2-20260912.md) 与 JSON. 本节只记录状态变化, 下文各节保留原始要求.

| 编号 | 状态 | 说明 |
| --- | --- | --- |
| X1 | 已修复, 已复验 | Rapid v3/v6 以 `c++_shared` 打包 NDK r28.2 libc++; Samsung 16 KB, API 36/37 ps16k, Redmi 4 KB 的独立 provider 调用通过 |
| X2 | 已修复, 已复验 | 根因: 旧 libc++ RELRO 末端 `0x111000` 与可写数据同页 (`tools/16kb/elf_layout.py`); v4/v5/v6 换 r28.2 libc++; Samsung 复验 v6 Tiny / v5 Thai, v4 Server 不再崩溃; ORT 会话改内存映射修复 v4 Server 的 OOM; Redmi 4 KB 通过 v4 Mobile / Server, v5 Thai / Server, v6 Tiny. Samsung 上 v4 Mobile 的三次运行均为旧 APK (当时 Mobile 重建失败), 最终 Mobile 产物经 APK 内 libc++ 审计为 r28.2, 16 KB 运行时验证待新时段; 未重建的 14:11 旧 flavor APK 仍带旧 libc++ |
| X3 | 重新归类 (B7), 已修复 | Xiaomi 4 KB 复现相同 pc `0xc7a44`; 尺寸扫描的 "宽 800-1024 且高 320/352 崩溃" 实为进程首次检测小形状触发 (崩溃后进程重启造成的假象), 与页大小无关; 见下方 B7 |
| X4 | 已补充 | `elf_layout.py` 与 `ocr_provider_smoke.py` 纳入 `tools/16kb/`; 宿主策略接受 r26b / r28c |
| R3 | 原生 16 KB arm64 已复验 | 2026-09-12 Samsung 第三时段 (localhost:30008): 宿主 5280 + OpenCV v1.1.2, Paddle v4 Mobile/Server, v5 Thai, v6 Tiny, v3, Rapid v6/v3 (r28c donor) 全部通过; 同批产物在 Xiaomi Pad (API 35) 与 Redmi (API 33) 两台 4 KB arm64 真机回归通过; 剩余为未逐一运行的语言/flavor 变体与最终 Release 候选 (R1/R5/R6) |
| R4 | 原生 16 KB arm64 通过 | 2026-09-12 Samsung 第三时段真实 Qwen2.5-1.5B 推理通过 (生成 3291 ms); 另在新 16 KB API 36 AVD (1811 ms), API 37 ps16k, Redmi 4 KB arm64 通过; 2.5 GB AVD 因 LOW_MEMORY 终止 (非缺陷) |
| 版本 | 已本地提交 | 宿主 6.8.0 build 5280 (首个接受 r28c 的宿主), OpenCV 1.1.2 (build 12, 最低宿主 5280), Rapid v3/v6 1.0.2, Paddle v3-v6 1.0.2; 未推送 |
| donor / lock | 已联动 | OpenCV AAR `f760265b…` (r28c), Rapid AAR `75f9c45d…`, Paddle AAR `8c4b410a…`, libc++ r28.2 `ab4e6c71…`, 各 provenance JSON 随仓库提交 |
| Release 候选 | 已审计 | 2026-09-12 维护者从已提交源码 `clean assembleRelease`; 89 个 Release APK 版本 / 64 位 ELF / libc++ RELRO / 门禁 0 项问题 ([审计](evidence/page-size-16kb-release-candidates-20260912.md)); 未签名验证, 未推送 |
| R5 / R6 | 维护者暂缓 | 宿主暂不发布到远程 GitHub, 远程 CI 与官方索引刷新在发布时再做 |
| B7 | 已修复 | Paddle v3 v1.0.3 (`d48dac1`): 首次检测前 960x960 预热规避 Paddle-Lite 首次小形状崩溃; Pad / Redmi 4 KB 复验通过, 16 KB arm64 待下一个 Samsung 时段 ([记录](evidence/page-size-16kb-backlog-20260912.md)) |
| B6 | 已实现 | APK Inspector v1.1.1 (`f9ff8f5`) 16 KB 就绪检查视图 + 11 项单元测试 |
| B5 / B2 / B1 | 已记录 | 32 位不合规 14 项仅信息; 现代打包不需要; Bun 大页 JSC 候选在 Bun 仓库验证, 正式 payload 未替换 |

2026-09-12 补充: Samsung 第三时段已完成最终产物 (含 Three-Stone-AI arm64) 的原生 16 KB arm64 复验, 兼容属性已重置为默认. 剩余需要维护者的事项: R1 / R2 / R5-R8 (发布源码定稿, 合并后 Runtime Kit 与用户 APK, 远程 CI, 官方索引刷新, 人工流程), 以及从已提交源码重建全部发布 flavor (未重建的 14:11 旧构建仍带旧 libc++: Paddle v4 mobileEn, v5 mobile / english / korean / latin / eslav / greek / arabic / cyrillic / devanagari / telugu / tamil, v6 small / medium).

## 1. 已关闭的事项

- [x] D11 / P1.7: 维护者已把 QuickJS-Host 关联分支合并到宿主 master 并解决冲突, 提交 `d17d125783426f89ee446a6352551a6a7b873150`. `git worktree list --porcelain` 已无该工作树, 原目录不存在. 无需再做 rebase 或删除.
- [x] 合并提交说明记录了宿主 Debug 单元测试 2532 通过 / 5 跳过, QuickJS API 单元测试 3 通过, Debug APK 与对齐门禁, app/inrt 编译, API lint, Markdown 生成及 API 36 x86_64 16 KB 上的 3 项插件 instrumentation. 这些是合并时记录的验证, 不替代最终 Release / Runtime Kit / 用户 APK 的产物收据.
- [x] P0-P5 的对应代码已部署: 共享门禁, 宿主原生库, 23 个原生插件, 18 个纯 JVM 插件的无原生库断言, APK Builder 模板扫描, 加载前保护, 打包警告, 索引字段与插件中心提示. OCR 的后续修正与未完成验收明确列在 X1-X4, 不将代码部署等同于完整验收.
- [x] 共享 Gradle 插件 `io.github.supermonster003.autojs6-native-alignment:1.8.0` 已公开发布, 独立消费验证与远程 CI 已通过. 不要重复发布相同版本.
- [x] 用户提供的 `localhost:30078` 已上线: Samsung SM-A566B / API 36 / arm64-v8a / `PAGE_SIZE=16384`. 本轮已读回 `linkerCompat=false`, `pmCompatDisabled=true`. 设备准备不再是待办. 宿主与 16 个原生插件取得运行时证据, 30 项 instrumentation 全部通过; 6 个 OCR 插件的补测失败, Three-Stone-AI 缺模型. 详见 [Samsung 补验](evidence/page-size-16kb-samsung-20260911.md).

## 2. 本轮真机暴露的发布阻塞

| 编号 | 已观察到的问题 | 必须继续完成的工作 |
| --- | --- | --- |
| X1 | Rapid v3/v6 的独立插件 APK 缺少 `libc++_shared.so`. OpenCV 的 `DT_NEEDED` 需要它, 实际 Binder OCR 调用触发 `UnsatisfiedLinkError`, 插件进程退出 | 为两个独立插件携带兼容的 C++ runtime, 复核全部 ABI / split / universal 的依赖完整性. 分别复验独立 Binder 调用和嵌入用户 APK 的调用, 防止宿主 / 模板提供 libc++ 掩盖独立插件漏库. |
| X2 | Paddle v4 Server, v5 Thai, v6 Tiny 均在旧 `libc++_shared.so` 的 `std::__ndk1::ios_base::Init` 初始化中 SIGSEGV; 三者库 Build ID 相同 | 替换 / 重建并验证 C++ runtime, 连同旧 OpenCV 布局一起复核. 其它模型变体未逐一执行, 不能声称都已运行通过; 先确认它们是否使用相同二进制再按影响范围复验. |
| X3 | Paddle v3 的 `1000 x 320` 生成图两次触发 Paddle-Lite 内 SIGSEGV, 首帧 `0xc7a44`; 独立插件和用户 APK 的 `944 x 640` 参考图均识别成功 | 定位输入 / 初始化相关的原生崩溃, 并补同一失败输入的 4 KB 对照, 确认是否与页大小有关. OpenCV 库组合差异也应保留作调查线索. 当前不能判定 Paddle-Lite 整体不支持 16 KB; 也不能因单张参考图通过就关闭该崩溃. |
| X4 | 现有静态门禁未发现 X1-X3 | 随修复增加可复现的原生依赖完整性与真机回归检查. 复核 RELRO / 可写数据的页边界, 但不要仅凭 `RELRO end % 16384` 简单拒绝全部库: 本轮通过的 NDK libc++ 也可能在分离 LOAD 中保留页间空隙. D5 的真实执行仍是必要条件. |

X2 的旧 libc++ `GNU_RELRO` 末端为 `0x111000`, 与 16 KB 页不整齐, 崩溃位于初始化写入路径; 这是后续调查线索, 尚未用替换后的产物证明根因. OpenCV 官方独立插件的 r26b donor 已在本机通过加载与运算, 它与 Paddle 自带的旧 OpenCV 二进制不能混为一谈.

验收工具另有一项需要完善: `NativePageAlignmentApkProducerDeviceTest` 目前按 v3-v6 的基础包名控制启用状态, 没有覆盖 `.server` / `.thai` / `.tiny` 等 flavor 包名. 需按实际安装的 provider 枚举并恢复状态, 或把严格隔离所选 Paddle v3 的前置条件做成可执行检查. 本轮临时隔离后 4 种用户 APK 组合通过, 但还未修改该夹具源码.

## 3. 必须完成的其它收尾事项

| 编号 | 剩余工作 | 完成条件 / 关联条目 |
| --- | --- | --- |
| R1 | 确定最终发布源码与版本 | 保留并整合各仓库已有工作; 明确发布分支与 commit, 从固定源码生成候选包. 部署记录涉及 30 个原有脏工作区, 当时测试的是保留这些修改的工作区, 不能直接称为各 16 KB 独立提交的干净构建. |
| R2 | 对合并后的最终 Runtime Kit 与用户 APK 补收据 | QuickJS 合并涉及 `RemoteApkBuildRequestPreparer`, source routing 与 inrt launcher. 原 5 个 Runtime Kit 和用户 APK 收据属于合并前具体 SHA, 仍是有效历史证据; 最终发布时需用合并后的模板重新生成并验证产物. app Debug / inrt 编译通过不等于新模板的执行通过. |
| R3 | 修复失败项并补齐原生 16 KB arm64 矩阵 | 本轮已验证宿主及 16 个原生插件, Yolo 的 P3.8 真机证据已补齐; 剩余是 X1-X3 的 OCR 修复后复验, Three-Stone-AI, 未覆盖的发布变体, 以及最终候选产物. 按第 4 节真实调用并记录版本与 SHA. |
| R4 | Three-Stone-AI 真实本地模型推理 | 给定可用 `.litertlm` 模型, 记录模型来源 / SHA / 后端与插件 APK, 在 16 KB 下实际初始化并生成非空结果. 安装, Launcher 启动, fake provider 或在线服务通过均不能替代. |
| R5 | 最终候选包执行远程 CI 与发布验证 | 宿主和各插件部署提交尚无本次发布对应的全套远程运行收据. 从确定的分支触发各仓库适用工作流, 门禁不得 skip, 记录 CI URL / commit / APK SHA; 同步验证将发布的 split / universal / Release 变体. 已通过的共享插件 CI 无需重做. |
| R6 | 新版本公开后重新生成官方索引 | 下载并测量真实 Release APK, 更新最新条目的数值及 `measured` / `declared` 来源. 当前生成文件仍在描述旧公开版本; 不可以本地未发布 APK 的结果替换. |
| R7 | 插件中心与用户打包的完整人工流程复核 | 真机确认安装 / 更新 / 启用 / 取消警告, 旧版 4 KB / 已对齐 / 无原生库 / 未知四种状态, 打包警告的具体库名及结果说明. 自动 ViewHolder 和错误策略测试已覆盖核心逻辑, 这项补用户可见流程. |
| R8 | 关闭 P6 并交接 | X1-X4 及 R1-R7 的适用项完成后回填主 Roadmap, 设备矩阵, CI / 产物证据和自动记忆; 保留未解决的 B1 等边界, 不把静态对齐写成所有运行时均支持 16 KB. |

发布源有两个当前需处理的具体状态: 官方索引分支相对本地缓存的 `origin/main` 为 ahead 5 / behind 5, 需在发布前获取远端并正常整合, 不能据此直接强推; Three-Stone-AI 位于 `ui-redesign` 且存在既有未提交修改, 需先确定它的发布来源. 本次盘点不代为合并, 清理或推送这些工作.

宿主当前 master 的跟踪分支是 `private/master`, 各插件也并非统一使用 master. 发布前逐仓库核对 remote / branch, 不批量套用 `origin master`. 本轮 45 个仓库的本地状态摘要存入 Samsung JSON; 没有 fetch, ahead / behind 仅描述本地缓存的跟踪引用.

## 4. 真机与最终产物验收矩阵

每项记录设备型号 / API / ABI / 实际页大小 / 兼容模式 / APK SHA-256 / 版本 / 结果, 包含失败与跳过. 原生 16 KB arm64 与 API 36/37 x86_64 是不同的证据格, 4 KB 真机回归也另列. 某插件不提供 x86_64 时注明不适用.

| 对象 | 必须发生的真实行为 | 注意事项 |
| --- | --- | --- |
| 宿主 | libc++ 与两个 jackpal JNI 加载; PTY 命令与退出码; 宿主正常启动 | 三个库也会影响用户 APK. |
| OpenCV / Image-Quantization | 宿主进程内 staging / 加载, 图像运算及 PNG 量化 | 旧 4 KB JNI 的结构化拦截已有 API 37/fatal 证据; 若相关代码改变再补负例. |
| Paddle v3 / v4 / v5 / v6 | 每个版本各自实际识别图片, 记录输出 | v3 通过不能覆盖 v4-v6; 当前 ARM-only, 不能用 x86_64 模拟器代替. |
| Rapid v3 / v6 | 每个版本各自识别; 同一用户进程内先 Paddle 后 Rapid | 验证已修复的静态 OpenMP 冲突不再出现. |
| APK Builder / Runtime Kit | 实际生成, 签名, 安装并执行用户 APK; 终端 / 图像 / Paddle / Rapid 组合 | 分别覆盖选择与不选择独立 OpenCV 插件; 检查压缩方法, `extractNativeLibs=true`, ELF 与 optional OpenCV 依赖去重. 5 个 Kit 静态门禁与可运行 ABI 的实际执行分别记录. |
| Yolo-NCNN | NCNN 原生推理, 与固定图片的参考检测结果对比 | 仅 arm64; Sep 10 有旧版本 Samsung 证据, 仍需绑定当前候选产物. |
| Node / Lua / QuickJS / TypeScript | 真实脚本执行 / 编译与 Binder 往返, 会话释放 | Lua 需可信签名和已启用的 provider; 不放宽信任规则来通过测试. |
| Bun | arm64 上执行真实 Bun, prewarm / JS / TS / 取消或超时回收 | arm64 通过不覆盖 B1 的 x86_64 JSC 故障. |
| Python | 实际 CPython 和 native 扩展, 包括 ssl / sqlite3 / ctypes / mmap 等 | 除 APK `lib/` 外还有 Chaquopy ZIP / IMY 扩展; 历史 Samsung 探针可复用, 必须记录当前产物. |
| ImGui | 真实渲染与渲染生命周期 | 安装或 JNI 文件存在不足以证明渲染正常. |
| MLKit-Barcode / MLKit-OCR | 两种 provider 各自实际调用 | 不能用 fake transport 测试替代 native provider. |
| OpenCC / MediaInfo | 实际转换 / 媒体信息读取 | 记录 provider 输出. |
| Archive-Manager | 实际原生 Zstd 压缩 / 解压路径 | 纯 ZIP / TAR 操作不一定经过 Zstd JNI. |
| Ace-Editor | LuaLS 原生可执行文件的语义补全与恢复 | 确认 `path=semantic`, 不能将不支持 ABI 时的降级路径算作 LuaLS 运行通过. |
| Three-Stone-AI | 本地 LiteRT-LM 模型初始化与非空生成 | CPU 基线先验收; 若发布宣称 GPU 支持, 再验收相应设备 / 模型组合. |
| 18 个纯 JVM 插件 | 最终 APK 的 `expectNoNativeLibraries` 门禁 | 不需要为了本 Roadmap 给每个插件重复所有功能测试; 随各自正常发布携带门禁. |

已有 Node, Bun, Python 与 Yolo 的 Sep 10 Samsung 收据应保留. 这些并非从未在原生 16 KB 真机测试过; 当前缺口是最新产物的矩阵完整性与发布候选证据. Python 历史收据还明确说明当时宿主处于 4 KB ELF 兼容模式, 不可用于证明本次宿主合规.

人工补看: 首次启动是否出现页大小警告, 插件安装 / 升级 / 启用是否可理解, 警告取消是否生效, 原有 4 KB 设备基本功能是否回归. 32 位 ABI 的常规启动回归按发布 QA 执行; 其第三方库 16 KB 对齐不属于 D8 硬性条件.

Paddle 尚未逐个运行的变体为: v4 Mobile English; v5 Mobile / English / Korean / Latin / ESlav / Greek / Arabic / Cyrillic / Devanagari / Telugu / Tamil; v6 Small / Medium. 2026-09-12 已通过: v4 Mobile / Server, v5 Thai / Server, v6 Tiny (Redmi 4 KB 最终产物; v5 Thai / v6 Tiny 另在 Samsung 16 KB 通过, v4 Server 在 Samsung 不再崩溃). 后续按实际发布范围与共用 native SHA 确定需要重复的 native 初始化及模型加载检查.

## 5. 需要维护者复核的默认值与取舍

Q1-Q4 已经实现, 没有代码停在等待选择的状态. 维持现状即可继续验收; 只有希望改变行为时才需要另作决定. D1-D8 无需重新确认.

| 编号 | 当前已采用的方案 | 需要关注的取舍 |
| --- | --- | --- |
| Q1 | 插件 ID `io.github.supermonster003.autojs6-native-alignment`, 任务 `verifyNativePageAlignment`, DSL `nativeAlignment`; 文件与 `output-metadata.json` 定位所有变体 APK, 无 AGP 类型依赖 | 非标准输出可用 `apkFiles(...)` 明确指定. 推荐保留已发布接口. |
| Q2 | `nativePageAlignment` 为字节数; `0` 表示无原生库; 缺失表示未知; `nativePageAlignmentSource` 区分实测与声明; 最新 release 不借用旧版值 | 字段表示原生对齐, 不能保证 Bun JIT 等运行时逻辑均支持相应页大小. 推荐保留. |
| Q3 | 用户打包遇到 64 位 4 KB 库时警告, 不阻断; 宿主在 16 KB 下实际加载旧库时仍拒绝 | 这是两个不同入口. 可另决定以后是否在 16 KB 设备上默认阻断打包并提供放宽选项; 目前不需要等待该决定. |
| Q4 | Ace 已对齐的 64 位 LuaLS 实物不立即重编; 已补显式标志与 lock 校验 | 下次 LuaLS 升级按新脚本构建; 32 位重编不是当前要求. |
| D3 / OpenCV | 保留经实测合规且来源明确的 r26b OpenCV 预编译库; 本次不合规库均已用 r28.2 重编 | 与 D3 的字面 "所有自编库 NDK 下限" 存在需要明确的边界. 推荐把已验收预编译库的保留例外写清; 若要求所有可重编库都立即用 r28.2+, 需另做 OpenCV 重编, 更新 Rapid / Paddle donor 与 lock 并重验. |
| 发布安排 | 先确定宿主修复版本, 再发布对应 Runtime Kit, APK Builder 与有新宿主依赖的 Rapid, 最后刷新索引 | 需决定版本 / 分支与发布批次. 18 个纯 JVM 插件可随常规发布, 不必为无原生库门禁单独发版. |
| Three-Stone-AI 模型 | 使用维护者提供路径的可用 `.litertlm` 模型补真实推理 | 记录模型身份; 无模型则保留 R4, 不标成测试通过. |

## 6. 发布与维护时容易遗漏的事项

1. **版本与宿主依赖同步.** 新 Rapid 当前声明宿主 build >= 5279, 原因是可选 OpenCV 原生库收集能力. 最终宿主 versionCode 必须唯一并满足更新规则; 如果已公开的 5279 不含修复, 需把 Rapid 的最低宿主版本同步到真正首次发布修复的 build, 不能只因版本名称相同就认为具备该能力. 2026-09-12: 宿主 build 5280 是首个接受 r28c 原生构建的版本, OpenCV 插件 v1.1.2 已声明最低宿主 5280; Rapid 仍为 >= 5279.
2. **Runtime Kit 的身份.** 旧本地 Kit 的 `gitSha=local` 是诚实记录, 具体 SHA 才能证明所测内容. 发布前生成正式身份 / 清单, 不手改历史收据成新提交. 新模板发布后再构建相应 APK Builder.
3. **APK Builder 测试开关与签名.** 此前验收使用可信签名且显式开启 remote build 的测试包. 生产默认关闭的开关保持原语义; 16 KB 适配不自动开放远程打包. 要发布可用的该功能需遵守原功能的启用流程. Lua 同样不能靠降低签名检查通过验收.
4. **压缩与解压契约.** 宿主 / inrt / 用户 APK 继续压缩 `.so` 并使用 `extractNativeLibs=true`. 不可只把 ZIP 写入改成 STORED 而不做 16 KB 数据偏移对齐. Builder 回归测试同时覆盖 manifest 的两种输入值, 不代表最终用户模板可以任意改成 false.
5. **宿主 libc++ 所有权.** OpenCV 有意由宿主提供进程内 C++ runtime, 不要删宿主的 `libc++_shared.so`. 改 OpenCV / Image-Quantization 工具链后复核运行库兼容性.
6. **Rapid 依赖变化与体积.** ORT 1.21.1 + OpenCV 4.8.0 共享 TBB 是为解决 Paddle 后加载 Rapid 的真实崩溃. 不恢复静态 OpenMP, 不用 `KMP_DUPLICATE_LIB_OK` 绕过. 共享 OpenCV 增加插件体积, 用户 APK 同时选择独立 OpenCV 时应去重; 没选时仍必须带上 Rapid 的 optional payload. 2026-10-10 起由 [OpenCV / OCR 解耦](opencv-ocr-runtime-decoupling-roadmap.md) 取代: Rapid 静态链接官方 OpenCV SDK 并隐藏符号 (TBB, 无 OpenMP), 不再携带 `libopencv_java4.so`; 打包器只在旧版 `libRapidOcr.so` 的 DT_NEEDED 需要时复制其副本, 同名不同内容时终止打包.
7. **Paddle provenance.** v3 原始 Paddle-Lite 版本 / 下载 URL 未记录, 现有收据如实保留来源未知及 SHA. LLD 修复仅改 `.dynsym sh_info`, PT_LOAD 逐字节未变. 重新解压旧 SDK 后运行 `tools/16kb/refresh_paddle_opencv.py`, 避免把休眠的旧 x86_64 OpenCV 库带回来. 2026-10-10: 该脚本已退役 (解耦 P3.6). Paddle v3 静态链接官方 OpenCV SDK, 不再携带 OpenCV AAR; v4-v6 的 OpenCV 由各自仓库的来源清单维护.
8. **donor / lock 更新联动.** OpenCV 预编译库更换时, 同步 OpenCV 自身, 两个 Rapid AAR, 四个 Paddle AAR, 来源与 SHA lock 及实际 APK 的证据; 不只改版本字符串. LuaLS / Node / Bun 也要检查实际 exec 的 ELF, Python 要保留归档内扫描. 2026-09-12 已按此联动更新到 r28c donor, SHA 见第 0 节. 2026-10-10 起 OCR 插件不再以 OpenCV 插件为供体 (解耦 D7), OpenCV 插件更换预编译库时只联动宿主契约与证据.
9. **官方索引仍有旧记录.** 当前生成文件中 6 个 4096 条目是 APK Builder, MediaInfo, Node, Paddle v3, Rapid v3/v6; Python 为未知. 它们反映现有公开资产, 不是本地修复失败. 新版发布后逐项重新测量. `0` 与未知不可互换, `declared` 也不可称为实测.
10. **产物报告字段.** 部署表原有 R8 的 `None` 是采集器漏读版本. 已对原 SHA 对应的 APK 复核并更正为 `0.2.0-provider-dev` / build 1; JSON 保留更正依据. 发布记录仍应读最终 APK 的 metadata, 不靠构建后可能自增的属性文件反推.
11. **保留既有工作.** 不能为清理发布工作区执行全仓库 reset / clean; 特别保留 R8 原有暂存新增但工作区删除的 drawable 状态, 以及 Three-Stone-AI / Python 等其它会话修改. 整合后以最终源码重新生成有受影响内容的产物.
12. **测试范围诚实表达.** 门禁通过不等于真实推理 / JIT 通过, 模拟器不等于 arm64 真机, Debug 与测试签名包不等于正式 Release. 所有 skip / 无模型 / ABI 不适用与已知 B1 都在发布说明中明确.

## 7. 可选后续工作, 不阻塞本 Roadmap

| 编号 | 工作 | 当前建议 |
| --- | --- | --- |
| B1 | Bun x86_64 16 KB 的 WebKit/JSC 页大小假设, 需要大页 JIT 配置与上游 / 自编验证 | 由 Bun 自身 Roadmap (M5) 单独推进; 2026-09-12 Bun 仓库 `3ac84ca` 的大页 JSC 候选已在 x86_64 4 KiB / 16 KiB AVD 通过, 正式 payload 未替换. 候选转正前必须保留 x86_64 运行失败边界; arm64 通过不能抵消它. |
| B2 | 宿主 / inrt 采用未压缩原生库并重做 APK Builder 的 STORED + 16 KB 对齐 / manifest / 旧模板兼容 | 维持 D1, 不启动: 2026-09-12 实测每个 ABI 仅 3 个原生库, 约 APK 的 1% ([记录](evidence/page-size-16kb-backlog-20260912.md)). |
| B3 | JS `device.pageSize` 公开 API | 已完成 (2026-09-13): 只读数值属性, 文档 / 离线插件, d.ts 4.11.0 / Ace 1.1.27 与 LSP 同步及版本递增; 4 KB arm64 真机与 16 KB x86_64 模拟器各 1/1 通过 ([证据](evidence/page-size-16kb-b3-20260913.md)). |
| B5 | 第三方 32 位库也按 16 KB 对齐 | 等上游自然升级, D8 不把它列为验收条件; 2026-09-12 全仓扫描 32 位不合规 14 项 / 64 位 0 项已记录 ([清单](evidence/page-size-16kb-backlog-20260912.md)). |
| B6 | APK-Inspector 增加 16 KB 分析视图 | 已实现 (2026-09-12, v1.1.1 `f9ff8f5`); 真机 UI 复核可在下一个设备时段顺带完成. |

B4 的无用 CMake / Ninja CI 安装步骤清理已完成, 不属于剩余事项.

## 8. 执行入口

- 静态巡检: `python tools/16kb/scan_alignment.py`; 发布构建中的 `verifyNativePageAlignment` 不得跳过.
- 设备状态: `tools/16kb/verify_device.sh SERIAL PACKAGE inspect`; 兼容模式开关按系统权限执行并读回. 本轮 Samsung 已成功关闭兼容模式. 不设 `android:pageSizeCompat` 隐藏系统提示.
- 本轮结束时两个兼容模式属性, 三个临时禁用的 Paddle flavor 与宿主临时存储 AppOp 均已恢复原值; 测试包保留. 开始新一轮严格验收时重新执行兼容模式设置并读回.
- 用户 APK: `python tools/16kb/verify_user_apk.py SERIAL --adb E:/.android/sdk/platform-tools/adb.exe --rapid-version 3 --out <目录>`; 对 v6 重复, 再用 `--without-opencv-plugin` 覆盖不含 OpenCV 插件的产物 (新版 Rapid 使用私有 OpenCV, 旧版按 DT_NEEDED 带入其 OpenCV). 两份 OpenCV 版本不同的共存验证使用 `tools/opencv-ocr/verify_packaged_ocr.py`. 前置条件是配套宿主 / 测试 APK, 可信且已启用 remote build 的 APK Builder, 所需插件. 脚本当前选择 Paddle v3; v4-v6 需单独验收, 不能从此收据推断.
- 全部完成后回填主 Roadmap 的 P6; 不提前把当前清单全部勾选.

## 9. R1 所涉及的 30 个既有未提交工作区

以下每个名称均以 `AutoJs6-Plugin-` 为前缀. 部署前记录与本轮复核均显示这些仓库存在原有未提交工作, 不表示这些改动全部应该随 16 KB 发版; 需按各自功能完整性确定最终发布来源. 原 42 个部署提交的映射见部署 JSON.

| 分组 | 仓库 |
| --- | --- |
| 图像与 OCR, 12 个 | Image-Quantization, ImGui, MLKit-Barcode, MLKit-OCR, OpenCV, Paddle-OCR-PP-OCRv3, Paddle-OCR-PP-OCRv4, Paddle-OCR-PP-OCRv5, Paddle-OCR-PP-OCRv6, Rapid-OCR-PP-OCRv3, Rapid-OCR-PP-OCRv6, Yolo-NCNN |
| 语言与 AI, 7 个 | Java-Runtime, Kotlin-Runtime, Lua-Runtime, Python-Runtime, QuickJS-Runtime, TypeScript-Engine, Three-Stone-AI |
| 其它, 11 个 | Accessibility-Compat, DEX-Compiler, File-Inspector, Image-Tools, Image-Viewer, Offline-Docs, Pinyin, Pinyin4j, R8-Compiler, Three-Amber-Player, Three-Terra-Player |

QuickJS-Runtime 本轮所见仅有未跟踪 `.idea/`; R8 则有需要保留的特殊 AD 暂存状态. 按实际差异判断, 不把所有 dirty 状态等同于未完成产品代码.
