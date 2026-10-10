# OpenCV 运行时与 OCR 插件解耦 Roadmap

本文是 OpenCV 插件与 OCR 插件 (Paddle OCR / Rapid OCR) 之间 OpenCV 运行时解耦的可执行状态表.
以 2026-10-10 的本地代码快照为起点: 宿主 `master@94406ccc08` (`VERSION_NAME=6.8.0`, `VERSION_BUILD=5328`), `AutoJs6-Plugin-OpenCV` 1.1.5 (`76a3034cfa`), Paddle OCR PP-OCRv3 1.0.5 / PP-OCRv4 1.0.4 / PP-OCRv5 1.0.4 / PP-OCRv6 1.0.4, Rapid OCR PP-OCRv3 1.0.6 / PP-OCRv6 1.0.6.
每个条目均可独立 Check 并落地, 后续会话按阶段逐步推进.
2026-10-10 各插件仓库重写过本地历史 (去除 AGENTS.md / CLAUDE.md), 上述插件哈希已变化, 例如 OpenCV 插件 `76a3034cfa` -> `db42815f26`; 对照表位于 `D:/idea-projects/_repo-reset-backups/agents-claude-history-20261010/`.

设计输入: 维护者 2026-10-10 的问答结论. OpenCV 插件转为只服务宿主的运行时提供方, 与其他插件完全隔离; 其他插件完全独立管理自己的 OpenCV, 与 OpenCV 插件无关. OpenCV 插件升级到新版本时, 不应牵动任何 OCR 插件, 反之亦然.

关联文档: [OpenCV plugin integration](opencv-plugin-integration.md), [16 KB 页大小 Roadmap](page-size-16kb-roadmap.md), OpenCV 插件仓库 `ROADMAP.md` 的 M4 (OpenCV 新变体).

使用方式:

1. 每次会话开始时, 从 "阶段总览" 选取未完成条目. P1 是解除 OpenCV 插件独立升级阻碍的关键路径, 优先推进; P3 的各 OCR 仓库可以并行.
2. 条目完成后勾选 `[x]`, 并在条目后追加证据 (提交 hash / 测试 / 设备型号与 API level).
3. 宿主与 OCR 插件分属不同仓库. 每个仓库按自身 `AGENTS.md` 处理版本号, 10 种语言的更新日志与生成文档.
4. 附录 A 的待决事项在进入对应阶段前由维护者拍板, 结果回填到 "固定决策".

---

## 1. 现状与耦合点

宿主应用 (非打包 APK) 中的运行时分工:

| 使用方 | OpenCV 来源 | 进程 |
| --- | --- | --- |
| images 等模块, `org.opencv` Java API | OpenCV 插件的 `libopencv_java4.so`, 经契约校验后加载 | AutoJs6 进程 |
| ocr 模块 -> Paddle OCR / Rapid OCR 插件 | 各插件 APK 自带的 `libopencv_java4.so` | 插件自身进程, 经 Binder 调用 |

宿主应用中两者已经天然隔离. 耦合集中在构建期来源链与打包 APK 两处:

| 编号 | 耦合点 | 位置 | 影响 |
| --- | --- | --- | --- |
| C1 | OCR 仓库的 OpenCV 二进制以 OpenCV 插件为供体 | Paddle OCR v3-v6 `libs/org-opencv-4_8_0/opencv-4.8.0.native-alignment.json`; Rapid OCR v3/v6 `libs/opencv-native-4.8.0.provenance.json` 与 `docs/16kb.md` | 来源记录指向 OpenCV 插件; 已核对的 OCR APK 中 arm64 `libopencv_java4.so` 与 OpenCV 插件逐字节相同 |
| C2 | 宿主工具从 OpenCV 插件向 OCR 仓库分发二进制 | `tools/16kb/refresh_paddle_opencv.py`, `tools/16kb/refresh_rapid_opencv.py` (固定供体 SHA-256) | 构建期由宿主统一推送同一份 OpenCV |
| C3 | 打包器以 OpenCV 插件为唯一所有者 | `RemoteApkBuildRequestPreparer.resolveSelectedPluginArtifacts`: 同时勾选 OpenCV 时, 从其他插件产物中移除 `libopencv_java4.so` | 打包 APK 中 OCR 原生库运行在 OpenCV 插件的 OpenCV 上 |
| C4 | 打包器同名条目先到先得 | `copyFirstMatchedSoToZip` / `copyPluginArtifactToZip` 跳过已写入的 `lib/<abi>/<name>`, 不比较内容 | 内容不同的同名库被静默丢弃, 包括 `libc++_shared.so` |
| C5 | Rapid OCR 动态链接共享 OpenCV | Rapid OCR `libs/rapidocr/src/main/cpp/CMakeLists.txt` 以 IMPORTED SHARED 方式链接; `libRapidOcr.so` 的 NEEDED 含 `libopencv_java4.so` | OpenCV 版本变化即 C++ ABI 变化 |
| C6 | Paddle OCR v3 静态链接但导出 OpenCV 符号 | `libNative.so` 不依赖 `libopencv_java4.so`, 但动态符号表导出 1034 个 `cv::` 符号; APK 仍携带 `libopencv_java4.so` | 同进程存在多份 OpenCV 时有符号干扰风险 |
| C7 | 宿主内置 Paddle 引擎强制加载 OpenCV 运行时 | `App.kt` 安装 `OCRPredictorNative.installOpenCvRuntimeLoader { OpenCvPluginHost.ensureLoaded(...) }`; `PaddleOcrEmbeddedEngine.getEngine` 也先调用 `ensureLoaded` | 打包 APK 中 Paddle OCR 依赖一份名为 `libopencv_java4.so` 的库, 而 `libNative.so` 本身并不需要 |
| C8 | INRT 加载目录中任意同名库 | `OpenCvPluginHost.tryLoadBundledRuntime` 只核对版本字符串 | 未勾选 OpenCV 的打包 APK 中, images 可能加载 OCR 插件带入的副本 |
| C9 | Paddle OCR v4-v6 的原生库被复制但不运行 | `ApkBuildLibraryCatalog.PADDLE_OCR` 的 v4-v6 变体复制 `libopencv_java4.so` 与 ONNX Runtime; INRT 的 ocr 只使用内置 Paddle Lite 引擎 (`.nb` 模型) 与内置 Rapid 引擎, 宿主不含 v4-v6 的 Kotlin 引擎代码 | 打包 APK 体积浪费, 并经 C4/C8 引入同名副本 (待 P0.2 实测确认) |

历史约束: Rapid OCR 曾使用 OpenCV Mobile 与静态 OpenMP, 与 Paddle Lite 同时加载进同一个打包 APK 时会中止 (Rapid OCR `docs/16kb.md`, 提交 `799b729`). 改用 TBB 与共享 OpenCV 运行时 (C5) 正是为了解决这个问题. 新方案必须继承 "同一进程不引入多份 OpenMP 运行时" 这一结论.

## 2. 目标运行模型

宿主应用: 保持不变. images 在 AutoJs6 进程中使用 OpenCV 插件的 OpenCV; ocr 经 Binder 调用 OCR 插件进程, OCR 插件使用自带的任意 OpenCV 版本. 两者版本不同也互不影响.

打包 APK: 只有一个进程和一个原生库目录. 解耦后同一进程内存在两份互不可见的 OpenCV:

| 组件 | OpenCV 形态 | 符号可见性 |
| --- | --- | --- |
| images 等模块与 `org.opencv` Java API | OpenCV 插件提供的 `libopencv_java4.so` (版本 A) | 进程中唯一导出 OpenCV 符号与 `Java_org_opencv_*` JNI 方法的库 |
| 内置 Rapid OCR / Paddle OCR 引擎 | 静态链接进 `libRapidOcr.so` / `libNative.so` 的私有 OpenCV (版本 B) | OpenCV 符号全部隐藏, 只导出 `JNI_OnLoad` 与本引擎的 `Java_*` 方法 |

两份 OpenCV 共存的前提 (对应固定决策 D2-D5):

1. 符号私有: OCR 引擎库不以 `libopencv_java4.so` 命名, 不 NEEDED 该库, 动态符号表中没有 `cv::` 符号. 引擎内部对 OpenCV 的引用在链接期绑定到自身副本.
2. Java 层不共享: 一个 App 中 `org.opencv` Java 类只能存在一份, 归属版本 A. OCR 引擎在打包路径上不使用任何 `org.opencv` 类, 包括 `OpenCVLoader`.
3. 只以中立数据跨界: images 与 ocr 之间只传递 Bitmap, 像素缓冲与基本类型. 区域参数 (`org.opencv.core.Rect` 等) 在宿主侧换算为整数后再交给引擎. 禁止传递 `Mat`, native 指针或 OpenCV 异常对象; OCR 引擎在自身 JNI 层捕获 `cv::Exception` 并转为 Java 异常.
4. 线程运行时: 私有 OpenCV 使用 TBB (静态链接, 随 OpenCV 符号一并隐藏) 或 pthreads 并行后端, 禁止 OpenMP. 两份 OpenCV 各有一组工作线程, 需在 P4.2 评估线程数.
5. C++ 运行时唯一: 进程内只有一份 `libc++_shared.so`, 它的 NDK 版本不得旧于任何参与组件的构建 NDK. 目前宿主 `tools/16kb/refresh_libcxx.py` 固定为 NDK r28c (28.2.13676358), OCR 插件同样使用 r28c.

满足以上条件后, 同时勾选 OpenCV 与 OCR 的打包 APK 中, images 使用版本 A, ocr 使用版本 B, 两者可同时工作. 代价是 APK 体积与内存中多出一份 OpenCV (静态链接配合 `--gc-sections` 只保留实际用到的模块) 和一组线程.

---

## 3. 固定决策与硬约束

| 编号 | 决策 | 含义 |
| --- | --- | --- |
| D1 | OpenCV 插件只服务宿主 | 宿主应用与打包模板中的 `org.opencv` Java API, 以及 images 等模块加载的 `libopencv_java4.so`, 只能来自 OpenCV 插件. OpenCV 插件不向其他插件供货, 其发布不评估对 OCR 插件的影响. `OpenCvPluginContract` 对 OpenCV 版本, Java API 指纹与 NDK 的锁定属于宿主与 OpenCV 插件之间的有意耦合, 不在本文解除范围内. |
| D2 | OCR 插件的 OpenCV 是私有实现细节 | 版本, 构建方式与升级节奏由各 OCR 仓库自行决定. 进入打包 APK 的 OCR 原生库中不出现名为 `libopencv_java4.so` 的文件, 引擎库不导出 OpenCV 符号. |
| D3 | 跨边界只传中立数据 | 见第 2 节条件 3. |
| D4 | 私有 OpenCV 禁用 OpenMP | 见第 2 节条件 4. |
| D5 | `libc++_shared.so` 唯一且不旧于任何组件 | 见第 2 节条件 5; 打包器按 NDK 版本选择, 不再先到先得. |
| D6 | Paddle OCR v4-v6 可在插件进程内保留 Java API 用法 | 这些插件在自身进程中运行, 可继续通过 `org.opencv` Java API 使用自带 OpenCV. 只要它们的 OpenCV 不进入打包 APK (附录 A Q2), 就与 OpenCV 插件无关. |
| D7 | 来源记录不再引用 OpenCV 插件 | OCR 仓库的来源清单直接记录 OpenCV 上游标签与提交, NDK 版本, 构建选项与逐 ABI 哈希. 历史更新日志中 "来源: AutoJs6-Plugin-OpenCV" 等条目作为历史事实保留. |
| D8 | OpenCV 插件仓库无需改代码 | 本 Roadmap 不修改 OpenCV 插件的发现契约, 原生库或发布流程. |
| D9 | 旧版 OCR 插件按字节比对 (附录 A Q1 -> a) | 未解耦的旧版 OCR 插件与 OpenCV 同时勾选时, 两份 `libopencv_java4.so` 字节相同则只保留一份; 内容不同则终止打包, 错误信息列出两个来源并提示升级 OCR 插件. 由 P1.1 的通用同名冲突检测实现. |
| D10 | 打包 APK 不支持 Paddle OCR v4-v6 (附录 A Q2 -> a) | 打包器的 Paddle OCR 只接受 v3 插件, 不再复制 v4-v6 的模型, ONNX Runtime 与 OpenCV. 只装有 v4-v6 时给出可操作的错误. v4-v6 继续在宿主应用中经 Binder 使用 (D6). |
| D11 | 私有 OpenCV 取自官方 SDK 静态库 (附录 A Q3 -> a) | OCR 引擎库静态链接 OpenCV 官方 Android SDK 发布包中的静态库 (TBB 并行后端, 无 OpenMP 引用). 各 OCR 仓库自带下载与 SHA-256 校验步骤, 来源清单记录上游版本, 下载地址与哈希. |
| D12 | 不新增私有 OpenCV 声明 (附录 A Q4 -> 按产物判断) | 打包器直接依据插件 APK 的实际内容区分新旧插件: 是否携带 `libopencv_java4.so`, 引擎库是否 NEEDED 它. 插件端无需新增 meta-data 或 capability. |

---

## 4. 范围与非目标

范围内:

- 宿主: `apkbuilder/ApkBuildLibraryCatalog.kt`, `apkbuilder/template/RemoteApkBuildRequestPreparer.kt`, `core/plugin/opencv/OpenCvPluginHost.kt`, `App.kt`, `plugin-api/paddle-ocr-engine`, `plugin-api/rapid-ocr-engine`, `runtime/api/augment/ocr/`, `tools/16kb/refresh_paddle_opencv.py`, `tools/16kb/refresh_rapid_opencv.py`, `docs/dev/opencv-plugin-integration.md`
- OCR 仓库: Paddle OCR PP-OCRv3 / v4 / v5 / v6 与 Rapid OCR PP-OCRv3 / v6 的 CMake, Gradle 打包, 来源清单与文档

非目标 (本 Roadmap 不处理):

- OpenCV 插件自身的版本升级 (OpenCV 插件 `ROADMAP.md` 的 M4, 需宿主先发布新 Java API 与契约).
- OpenCV 运行时的 AIDL 进程隔离 (`opencv-plugin-integration.md` 的 "Future isolated runtime").
- MLKit OCR (不使用 OpenCV) 与 YOLO NCNN (使用 ncnn 自带的 simpleocv).

---

## 5. 阶段总览

### P0 基线与证据

- [x] P0.1 记录耦合点 C1-C9 与 Rapid OCR 的 OpenMP 历史. (证据: 本文第 1 节与附录 B, 2026-10-10 代码与 APK 核对)
- [x] P0.2 实测 Paddle OCR v4 / v5 / v6 在打包 APK 中的可用性: 分别勾选后检查 ocr 可选引擎与实际识别结果, 确认 C9. 结论回填附录 A Q2. (证据: [P0 基线](evidence/opencv-ocr-p0-baseline-20261010.md), Xiaomi 23046RP50C API 35. 三者打包成功但 Paddle OCR 均报 "未找到内置 Paddle OCR 资源"; v3 与 v6 同时启用时打包器选中 v6, 由 D10 一并修复)
- [x] P0.3 为打包器补充基线测试: 同时勾选 OpenCV 与 Paddle OCR / Rapid OCR 时, 记录产物中 `lib/<abi>/` 条目及其来源, 作为 P1 改动前后的对照. (证据: 同上; 设备测试 `OpenCvOcrPackagingDeviceTest` 与 `tools/opencv-ocr/verify_packaged_ocr.py`, 按字节比对每个条目的来源插件)

验收: C9 有设备证据; P1 前后的打包产物差异可由测试说明.

### P1 宿主打包器 (关键路径)

- [x] P1.1 同名原生库冲突检测: 两个来源提供相同的 `lib/<abi>/<name>` 时比较 SHA-256. 相同则保留一份; 不同则以可操作的错误终止打包, 错误信息列出两个来源插件. `libc++_shared.so` 按 P1.2 处理. (证据: 宿主 `7d8f853a3b`, `PackagedNativeLibraryPolicy`; [P1-P4 证据](evidence/opencv-ocr-p1-p4-20261010.md))
- [x] P1.2 `libc++_shared.so` 选择规则: 候选来自宿主与列出它的插件, 取其自身 Android ident 构建号最新的一份, 相同时优先宿主; 每个 NEEDED 它的待打包库, 其未定义的 `std::__ndk1` 符号必须能在所选运行时 (或同 ABI 的其他待打包库) 中找到, 否则终止打包 (D5). 实施时发现 NDK r28c 自带的 `libc++_shared.so` ident 为 `r27-beta1` / 11883388, 与用 r28c 构建的库无法按 NDK 版本直接比较, 因此改为符号级校验. (证据: 同上, `ElfDynamicInfo` 与单元测试)
- [x] P1.3 移除 "OpenCV 插件为唯一所有者" 的替换逻辑 (C3). 按 D12 不新增声明: 新版 OCR 插件不携带 `libopencv_java4.so`; 旧版插件的副本按 D9 与 OpenCV 插件比对字节. (证据: 同上; P4.4)
- [x] P1.4 `ApkBuildLibraryCatalog`: Paddle OCR v3 与 Rapid OCR 的 `libopencv_java4.so` 改为 `neededOnlyLibsToInclude`, 只在该插件的待打包库 DT_NEEDED 它时复制 (D12); Paddle OCR 只保留 v3 变体 (D10). (证据: 同上; 场景 C / D)
- [x] P1.5 打包 OpenCV 来源标记: 勾选 OpenCV 时打包器写入 `assets/opencv-runtime.properties` (插件包名, 版本与逐 ABI SHA-256); `OpenCvPluginHost.tryLoadBundledRuntime` 加载前核对哈希, 拒绝来源不明的同名库 (C8). 行为变化已写入宿主更新日志的提示. (证据: 同上; 场景 B 与 P4.4)
- [x] P1.6 单元测试覆盖冲突检测, libc++ 选择, 新旧 OCR 插件分流与来源标记校验. (证据: `PackagedNativeLibraryPolicyTest`, `ElfDynamicInfoTest`, `BundledOpenCvRuntimeTest`, `ApkBuilderLibPolicyTest`)

验收: OpenCV 插件发布任意兼容版本后, 与已解耦的 OCR 插件同时勾选时可以成功打包, 且产物中的 `libopencv_java4.so` 只来自 OpenCV 插件; 同名库的内容冲突不再被静默吞掉.

### P2 宿主内置 OCR 引擎

- [x] P2.1 移除 `App.kt` 中的 `OCRPredictorNative.installOpenCvRuntimeLoader` 与 `PaddleOcrEmbeddedEngine.getEngine` 中的 `OpenCvPluginHost.ensureLoaded`; `plugin-api/paddle-ocr-engine` 删除 `OpenCVLoader.initDebug()` 回退与 `org.opencv.BuildConfig` 引用 (C7). 前提: 所选 Paddle OCR v3 插件的 `libNative.so` 不依赖 `libopencv_java4.so` (1.0.4 已满足, 见附录 B). (证据: 宿主 `4528568bc5`; 场景 B, P4.4 中旧版 1.0.5 同样通过)
- [x] P2.2 `plugin-api/rapid-ocr-engine`: 在 P3.1 落地后确认 `libRapidOcr.so` 可以在不含 `libopencv_java4.so` 的打包 APK 中加载; 旧版 Rapid OCR 插件的兼容按附录 A Q1. (证据: 场景 B; 旧版见 P4.4, 引擎模块无需改动)
- [x] P2.3 审计 `runtime/api/augment/ocr/`: 图像与区域参数在宿主侧转为 Bitmap 与整数后才进入引擎, 不向引擎传递 `Mat` (D3). (结论: `Ocr.kt` 把区域换算为 `org.opencv.core.Rect` 这一纯 Java 值类型, 再由基于 Bitmap 的 `images.clip` 裁剪; Paddle / Rapid 内置引擎只接收 Bitmap 与基本类型, 不调用 OpenCV native 方法)

验收: 打包 APK 只勾选 OCR 而不勾选 OpenCV 时, ocr 可用且产物中没有 `libopencv_java4.so`.

### P3 OCR 插件私有化 OpenCV (各仓库独立, 可并行)

- [x] P3.1 Rapid OCR PP-OCRv3 / PP-OCRv6: 改为静态链接 OpenCV (core, imgproc 等实际用到的模块以及 TBB). 编译选项加 `-fvisibility=hidden -fvisibility-inlines-hidden`, 链接选项加 `-Wl,--exclude-libs,ALL -Wl,--gc-sections`, 并用版本脚本只导出 `JNI_OnLoad` 与 `Java_*`. 并行后端保持 TBB 或 pthreads (D4). APK 不再携带 `libopencv_java4.so`. 验收: `llvm-readelf -d` 不含该 NEEDED, `--dyn-syms` 中 `cv::` 符号为 0. (证据: v6 `97ca8c3`, v3 `756f0a8`, 四个 ABI 均满足; [P1-P4 证据](evidence/opencv-ocr-p1-p4-20261010.md))
- [x] P3.2 Paddle OCR PP-OCRv3: 为 `libNative.so` 增加 `-Wl,--exclude-libs,ALL` 与版本脚本, 使导出的 `cv::` 符号从 1034 降为 0. 插件 Java 代码不使用 `org.opencv` (2026-10-10 检索为空), 因此在 P2.1 落地后停止携带 `libopencv_java4.so` 与 OpenCV AAR 依赖. (证据: `4efab64`, 两个 ABI 的导出 `cv::` 符号均为 0)
- [x] P3.3 Paddle OCR PP-OCRv4 / v5 / v6: 保留插件进程内的 Java API 用法 (D6), 按附录 A Q2 的结论决定是否需要打包支持. 若需要, 把预处理与后处理中的 OpenCV 调用下沉到私有原生库, 去除打包路径上的 `org.opencv` 依赖. (结论: D10 不支持打包, 无需下沉, 运行时代码不变)
- [x] P3.4 六个 OCR 仓库的来源清单改为直接记录 OpenCV 上游 (D7), 各自保留可复现的构建脚本 (存放位置见附录 A Q3), 并更新 Rapid OCR `docs/16kb.md` 等文档中的供体描述. (证据: Rapid v3 / v6 与 Paddle v3 的 `libs/opencv-android-sdk-4.8.0.provenance.json` 与 `tools/opencv/opencv_sdk_provenance.py`; Paddle v4-v6 的 `libs/org-opencv-4_8_0/opencv-4.8.0.provenance.json`, `tools/opencv/opencv_java_aar.py` 与 `tools/opencv/build_opencv_java.ps1`)
- [x] P3.5 各 OCR 插件声明私有 OpenCV (附录 A Q4), 提升版本并同步 10 种语言的更新日志. (结论: D12 不新增声明. Rapid v3 / v6 归入未发布的 1.0.6, Paddle v3 升至 1.0.6 并提示打包需 AutoJs6 6.8.0; Paddle v4-v6 运行时未变, 不升版本)
- [x] P3.6 宿主退役 `tools/16kb/refresh_paddle_opencv.py` 与 `tools/16kb/refresh_rapid_opencv.py` (C2), 其职责迁入各 OCR 仓库; `tools/16kb/refresh_libcxx.py` 保留 (D5). (证据: 宿主 `6733cbf4be`)

验收: 任一 OCR 仓库更换 OpenCV 版本时, 不需要 OpenCV 插件仓库或宿主契约的任何改动; 反之亦然.

### P4 文档与设备验证

- [x] P4.1 更新 `docs/dev/opencv-plugin-integration.md` 的 "APK Builder behavior" 一节: OpenCV 插件只拥有宿主的 OpenCV 运行时, OCR 引擎携带私有 OpenCV, 并引用本文第 2 节. (证据: 宿主 `6733cbf4be`)
- [x] P4.2 版本差异共存验证: 构建一个私有 OpenCV 版本与 OpenCV 插件不同的 Rapid OCR 测试包 (例如私有 4.10.0, OpenCV 插件 4.8.0), 打包同时勾选 OpenCV, Rapid OCR 与 Paddle OCR v3 的 APK. 交替及并发调用 `images.findImage` / `images.matchTemplate` 与 `ocr.detect`, 确认 `/proc/self/maps` 中两份 OpenCV 同时映射, 无崩溃, 结果与单独运行一致, 线程数在可接受范围内. 覆盖 arm64-v8a 与 x86_64, 以及 16 KB 页大小设备. (证据: [P1-P4 证据](evidence/opencv-ocr-p1-p4-20261010.md); 私有 OpenCV 4.12.0 的 Rapid v6 测试包, Xiaomi API 35 arm64 4 KB 与 AVD API 37 x86_64 16 KB; 16 KB arm64 实机未覆盖)
- [x] P4.3 宿主应用回归: images 与经 Binder 调用的 OCR 插件在 OpenCV 版本不同时同时工作. (证据: [P1-P4 证据](evidence/opencv-ocr-p1-p4-20261010.md), Xiaomi API 35)
- [x] P4.4 旧版兼容回归: 未解耦的旧 OCR 插件与新宿主组合时, 打包结果符合附录 A Q1 的预期. (证据: [P1-P4 证据](evidence/opencv-ocr-p1-p4-20261010.md), Redmi API 33)

---

## 6. 总体验收

1. OpenCV 插件发布新版本时, 不需要任何 OCR 仓库提交.
2. OCR 插件更换 OpenCV 版本时, 不需要 OpenCV 插件或宿主契约改动.
3. 宿主应用与打包 APK 中, images 等模块使用的 OpenCV 只来自 OpenCV 插件.
4. 同时勾选 OpenCV 与 OCR 的打包 APK 中, 两个不同版本的 OpenCV 共存并通过 P4.2 的设备验证.

---

## 附录 A: 待决事项

维护者已于 2026-10-10 拍板: Q1 -> (a), Q2 -> (a), Q3 -> 官方 SDK 静态库且各仓库自带, Q4 -> 不新增声明, 按产物判断. 结论见第 3 节 D9-D12.

| 编号 | 问题 | 选项 | 建议 |
| --- | --- | --- | --- |
| Q1 | 尚未解耦的旧版 OCR 插件与 OpenCV 同时勾选 | (a) 两份 `libopencv_java4.so` 字节相同时沿用单一副本, 不同时终止打包并提示升级 OCR 插件; (b) 一律终止打包 | (a), 兼容现有 4.8.0 用户 |
| Q2 | Paddle OCR v4-v6 是否需要打包 APK 支持 | (a) 不支持, 打包目录不再复制其原生库与模型; (b) 支持, 需完成 P3.3 的下沉 | 先完成 P0.2 再定 |
| Q3 | OCR 插件 OpenCV 构建脚本的存放位置 | (a) 每个仓库各自一份; (b) 中立的共享仓库 (不是 OpenCV 插件) | (a) 最符合 "完全无关", 代价是重复维护 |
| Q4 | 私有 OpenCV 的声明方式 | PluginInfo capability 或清单 meta-data, 键名待定 | 与现有 `PluginCapabilityKeys` 的命名保持一致 |

## 附录 B: 基线证据 (2026-10-10)

- `libRapidOcr.so` (Rapid OCR PP-OCRv6 1.0.6 debug APK, arm64-v8a): NEEDED 含 `libopencv_java4.so`, `libonnxruntime.so` 与 `libc++_shared.so`; 动态符号表中的 30 个 `cv::` 符号全部为未定义引用.
- `libNative.so` (Paddle OCR PP-OCRv3 1.0.4 release APK, arm64-v8a): NEEDED 不含 `libopencv_java4.so`; 动态符号表导出 1034 个 `cv::` 符号; APK 同时携带 `libopencv_java4.so` 与 `libc++_shared.so`.
- OpenCV 插件 `libs/opencv-native-4.8.0.aar` 的 SHA-256 为 `f760265bbc0bae75f2f906088fee3447eb32d9bc2b4d7ad8fd246923ddc26dd6`, 其中 arm64 `libopencv_java4.so` 的 SHA-256 为 `988f653ee309848dae1a57b5bedb32d85c17c6f2ad9b8c7ec1a1bb4c3095d506`. Rapid OCR PP-OCRv6 1.0.6 与 Paddle OCR PP-OCRv5 1.0.4 APK 中的 arm64 `libopencv_java4.so` 与之相同.
- Paddle OCR PP-OCRv3 的 `libs/` 与 `app/` 中没有引用 `org.opencv` 的 Java / Kotlin 源码.

检查命令 (NDK r28c 的 `llvm-readelf`):

```text
llvm-readelf -d libRapidOcr.so | grep NEEDED
llvm-readelf --dyn-syms -W libRapidOcr.so | grep -c _ZN2cv
llvm-readelf --dyn-syms -W libRapidOcr.so | grep " UND " | grep -c _ZN2cv
```
