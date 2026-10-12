# AutoJs6 16 KB 页大小 (Support 16 KB page sizes) 适配 Roadmap

本文是 AutoJs6 宿主, APK 打包链路, 共享构建插件, 官方插件索引以及同目录全部官方插件仓库的 16 KB 页大小适配可执行状态表.
以 2026-09-11 的本地代码快照 (宿主 `master@3507afdda`, `VERSION_NAME=6.8.0`, `VERSION_BUILD=5279`) 与同日对 44 个同目录仓库的 APK / ELF 扫描结果为起点,
每个条目均可独立 Check 并落地. 第 3 节保留部署前基线, 当前部署与验收状态见第 5 节及 [2026-09-11 验证记录](evidence/page-size-16kb-20260911.md). 全部剩余事项, 执行默认值, 手动验收与发布注意事项集中见 [维护者收尾清单](page-size-16kb-maintainer-checklist.md).

使用方式:

1. 每次会话开始时, 从 "阶段总览" 选取一个或多个未完成条目, 优先级按阶段顺序; 涉及多个仓库的条目按 "宿主先行, 被宿主进程加载的插件其次, 独立进程插件再次" 的次序推进.
2. 条目完成后勾选 `[x]`, 并在条目后追加证据 (提交 hash / 测试类名 / 设备型号 / 扫描结果), 证据等级见附录 D.
3. 每个插件仓库的条目落地时, 同时在该仓库的 changelog (`improvement` 分类) 与 README 中记录 "16 KB 页大小支持", 并按各仓库惯例递增版本号; 宿主条目落地时同步 `.changelog` (10 语言) 并重新生成 README.
4. 附录 C 记录本次按推荐方案采用的执行默认值. 这些默认值不冒充维护者对 Q1-Q4 的明确回答; D1-D12 保持原有范围.
5. P0-P6 不涉及 JS 公开 API 变更 (D12). 维护者于 2026-09-13 单独启动附录 B 的 B3 (`device.pageSize`), 该项按 `AGENTS.md` 同步文档, d.ts 与 LSP 关联项目.

---

## 1. 固定决策

以下决策已由维护者确认 (D1-D8 于 2026-09-11 以选择题形式拍板, D9-D12 为由此派生的执行约定), 后续阶段不再重新讨论:

| 编号 | 决策 | 含义 |
| --- | --- | --- |
| D1 | 宿主与 inrt 模板保持压缩打包, 只修 ELF 对齐 | `app/build.gradle.kts:1073` 的 `jniLibs.useLegacyPackaging = true` 保持不变 (`.so` 以 DEFLATED 打包, `extractNativeLibs=true`, 安装时解压). 由此 APK-Builder-Template 插件现有的 zip 写入逻辑 (全部条目 DEFLATED, 不改写 `extractNativeLibs`) 无需改动, 用户生成的 APK 只需满足 ELF `PT_LOAD` 对齐即合规. "现代打包" (未压缩 + 16 KB zip 对齐) 列为附录 B 的后续评估项 B2, 不在本 Roadmap 实施. |
| D2 | jackpal 终端模拟器原生库从上游源码用 NDK r28.2 重编 | `libs/jackpal-androidterm-libtermexec-1_0/libtermexec-release.aar` 内的 `libjackpal-androidterm5.so` / `libjackpal-termexec2.so` (4 ABI 均 4 KB, 仓库无 C 源码) 以上游 jackpal/Android-Terminal-Emulator 的 JNI 源码重编, 产物以新 AAR 替换, 仓库内保留可复现的构建脚本与 provenance / lock 文件 (参照 OpenCV / MediaInfo 插件的做法); 宿主日常构建与 CI 不引入 NDK. |
| D3 | NDK 下限 r28.2.13676358, 低于者升级, 高于者保留, 全部补显式链接标志 | Paddle v3 (r21), Rapid v3/v6 (r23), ImGui / Image-Quantization (r26) 升到 r28.2; MediaInfo / Yolo / Ace 的 r29 保留. 所有自编原生库的 CMake 补 `-Wl,-z,max-page-size=16384` 与 `-Wl,-z,common-page-size=16384`, 不依赖 NDK 默认值 (防止 NDK 回退时静默回归). NDK 版本记入各仓库 `version.properties`. |
| D4 | 16 KB 验证门禁以共享 Gradle 插件任务提供 | 在 `AutoJs6-Gradle-Platform-Versions` 仓库新增 (同仓库发布的) 项目级插件与任务 `verifyNativePageAlignment` (命名与实现细节见附录 A 与待决 Q1), 解析 APK 内每个 `.so` 的 ELF `PT_LOAD` 对齐与未压缩条目的 zip 数据偏移, 失败即构建失败; 自动挂到 `assemble*` 与 CI. 已有门禁的仓库 (OpenCV / MediaInfo / OpenCC / QuickJS / Lua / Bun) 保留自身门禁并叠加统一任务. |
| D5 | 设备验证 = ps16k 模拟器 + 原生 16 KB 真机各跑一遍 | 每个改动仓库在 `google_apis_ps16k` 模拟器 (本机已装 `android-36` 与 `android-37.0` 的 x86_64 镜像) 上验证 `getconf PAGE_SIZE == 16384`, 安装与启动无 16 KB 兼容模式警告, 冒烟脚本通过; 宿主与被宿主进程加载的插件 (OpenCV / Image-Quantization) 以及仅提供 arm64 的插件 (Yolo) 额外在原生 16 KB 的 arm64 真机 (Node / Bun 插件验收用过的 Samsung SM-A566B 类设备) 上验收, 由维护者执行并回填证据. |
| D6 | 宿主加载前校验 + 明确错误 + 官方索引新增字段 + 插件中心标记 | 宿主在 staging 插件 `.so` 时解析 ELF `PT_LOAD` 对齐, 设备页大小为 16 KB 且库仅 4 KB 对齐时抛出结构化错误 (新增错误码 + 多语言文案) 而非 `dlopen` 崩溃; `AutoJs6-Official-Plugins-Index` 的条目新增原生库页对齐字段; 插件中心在 16 KB 设备上对不兼容版本显示标记 / 警告. |
| D7 | 第三方预编译库全部修复, 不设 `pageSizeCompat` | Rapid-OCR v3/v6 把 RapidAI/OnnxruntimeBuilder 的预编译 `libonnxruntime.so` 换为 Maven `com.microsoft.onnxruntime:onnxruntime-android:1.21.1` (Paddle v4-v6 已在用, 实测 `0x4000`), `libRapidOcr.so` 用 r28.2 重编; Paddle v3 的 `libNative.so` 用 r28.2 重编, Paddle-Lite 携带的旧 `libc++_shared.so` 换为 r28.2 副本 (Paddle-Lite 本体实测 `0x10000` 已合规). 任何仓库都不设置 `android:pageSizeCompat` (见 D10). |
| D8 | 门禁只强制 arm64-v8a / x86_64, 32 位顺带修但不作为验收条件 | 16 KB 页只存在于 64 位设备, Play 要求也只针对 64 位, NDK r28+ 默认只对 64 位启用 16 KB 对齐. 自编库的显式标志对全部 ABI 生效 (32 位随之对齐), 但 MLKit / ORT / Chaquopy / NDK `libc++_shared.so` 等第三方 32 位库不追. 不借机削减任何仓库的 ABI 产物. |
| D9 | Roadmap 位于宿主 `docs/dev/page-size-16kb-roadmap.md` | 与仓库内既有 roadmap 的位置与风格一致 (a11y Roadmap 的 D3 先例). 插件仓库自身的 ROADMAP / README 在其条目落地时追加一行指向本文的记录. |
| D10 | 任何仓库都不设置 `android:pageSizeCompat` | 该属性无论取 `enabled` 还是 `disabled` 都会关闭系统的兼容模式警告, 会掩盖回归; 系统兼容模式 (backcompat) 只作为验证阶段的诊断手段 (通过 adb 属性开关, 见附录 E), 不作为交付形态. |
| D11 | `AutoJs6-QuickJS-Host` 不单独跟踪 | 该目录是宿主 6.8.0 的验证分叉, 本 Roadmap 不为其单列条目. 2026-09-11 已由维护者将关联分支合并到宿主 master 并解决冲突 (`d17d12578`), 工作树与目录均已移除; 原定 rebase 收尾由该合并完成. |
| D12 | P0-P6 不引入 JS 公开 API 变更 | 主阶段不触发文档 / d.ts / LSP 声明同步. 附录 B 的 B3 于 2026-09-13 由维护者单独启动, 按 `AGENTS.md` 完成关联项目同步. |

由 D1 / D6 派生的硬约束:

- `app/src/main/jniLibs/*/libc++_shared.so` 必须保留: 宿主在加载 OpenCV 插件运行时之前先 `System.loadLibrary("c++_shared")` (`core/plugin/opencv/OpenCvPluginHost.kt:219`), OpenCV 插件的 AAR 有意不携带 libc++ (`AutoJs6-Plugin-OpenCV/NATIVE_BUILD.md`), 由宿主提供进程级唯一的 C++ 运行时. 因此宿主的 libc++ 版本必须不低于所有被宿主进程加载的插件所用的 NDK (P1.1 后为 r28.2, 高于 OpenCV 的 r26b 与 Image-Quantization 升级后的 r28.2).
- APK-Builder-Template 的 zip 写入逻辑 (`RemoteApkLightweightBuilder.kt:490-518`) 不得改为 STORED: 在 D1 的压缩打包前提下, 未压缩但未按 16 KB 对齐的 `.so` 反而会触发系统兼容模式. P1.6 为此增加回归守卫.
- `com/stardust/**` 兼容包全程冻结 (与 a11y Roadmap 相同; 2026-10-12 起分布在 `compat/stardust/` 与 `app/` 两处). (2026-10-13 更新: 兼容包副本已按 autojs4-runtime-plugin-roadmap P7.3 方案 3 移除, `com.stardust` 类名改由 `rhino/StardustClassAliases` 映射到宿主类)

---

## 2. 范围与非目标

范围内 (按阶段归属):

- 宿主 `AutoJs6`: `app/src/main/jniLibs/**`, `libs/jackpal-androidterm-libtermexec-1_0/**`, `app/build.gradle.kts` (packaging / splits / `generateRuntimeKit`), `core/plugin/opencv/OpenCvPluginHost.kt`, `core/plugin/imagequant/ImageQuantPluginHost.kt`, `apkbuilder/**` (打包链路的原生库收集与警告), `core/plugin/center/**` (插件中心标记), `.github/workflows/{android,release-runtime-kit}.yml`, `tools/16kb/**`.
- 打包链路: `AutoJs6-Plugin-APK-Builder-Template` (仅回归守卫与 CI 门禁, 不改写入逻辑), 宿主 `generateRuntimeKit` 产出的 Runtime Kit 清单.
- 共享构建插件: `AutoJs6-Gradle-Platform-Versions` (新增门禁插件 / 任务).
- 官方索引: `AutoJs6-Official-Plugins-Index` (生成器与 JSON 字段).
- 含原生代码的官方插件 (23 个): OpenCV, Image-Quantization, Paddle-OCR v3 / v4 / v5 / v6, Rapid-OCR v3 / v6, MLKit-Barcode, MLKit-OCR, Yolo-NCNN, ImGui, TypeScript-Engine, Lua-Runtime, NodeJs-Runtime, QuickJS-Runtime, OpenCC, MediaInfo, Ace-Editor, Bun-Runtime, Python-Runtime, Three-Stone-AI, Archive-Manager.
- 纯 JVM 官方插件 (18 个, 仅 "断言无原生库" 的门禁, 随各自下次发布顺带): Accessibility-Compat, APK-Inspector, DEX-Compiler, File-Inspector, HTML-Previewer, Image-Tools, Image-Viewer, Java-Runtime, Kotlin-Runtime, MCP-Server, Markdown-Previewer, Offline-Docs, Pinyin, Pinyin4j, R8-Compiler, Screen-Color-Picker, Three-Ember-Player, Three-Terra-Player.

非目标:

- `AutoJs6-QuickJS-Host` (D11), 以及 `.a6-*`, `AutoJs6-m9-*`, `AutoJs6-M5-Retest`, `AutoJs6-Archive-Ui-Verify`, `AutoJs6-Node24-Validation`, `AutoJs4`, `AutoX*`, `Rhino-For-AutoJs6`, `TempApplication`, `Auto.js-SDK-Plugins-Garage` 等归档 / 验证 / 第三方目录.
- Bun 运行时在 x86_64 16 KB 设备上的 JSC 崩溃 (上游 WebKit `CeilingOnPageSize` 问题, `AutoJs6-Plugin-Bun-Runtime/ROADMAP.md:243-266`): 属运行时页大小假设而非打包对齐, 记入附录 B 的 B1.
- 现代打包 (未压缩 + 16 KB zip 对齐) 与 APK-Builder-Template 写入逻辑改造 (D1, 附录 B 的 B2).
- 第三方 32 位库的对齐 (D8).
- P0-P6 之外的 JS 公开 API (D12); 单独启动的 `device.pageSize` 见附录 B 的 B3.

---

## 3. 部署前基线 (2026-09-11, 保留用于对照)

### 3.1 平台与工具链事实

以下事实取自 [Android 官方 Support 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes) (2026-09-10 版, 2026-09-11 复核) 与本机环境, 是各条目的直接依据:

| 事实 | 对本 Roadmap 的含义 |
| --- | --- |
| Google Play: 所有 targetSdk >= 35 的应用必须在 64 位设备上支持 16 KB 页; 自 2027-02-01 起不支持 16 KB 的更新无法发布 | 宿主 targetSdk 36, 插件均为 36; 即便主要经 GitHub 分发, 也以 Play 合规为标准 |
| 16 KB 设备上, 若 `.so` 的 `PT_LOAD` 对齐为 4 KB, 或未压缩 `.so` 在 zip 内只按 4 KB 对齐, 包管理器以 "16 KB backcompat 模式" 运行应用, 首次启动显示警告; Android 17 可用 `bionic.linker.16kb.app_compat.enabled=fatal` 让不兼容的二进制直接中止 | 验证阶段用 "关闭兼容模式 / fatal" 得到确定性结论 (附录 E); D10 决定不设 `pageSizeCompat` |
| AGP >= 8.5.1 自动把未压缩 `.so` 按 16 KB zip 对齐; `useLegacyPackaging=true` (压缩) 时 zip 对齐不适用, 只需 ELF 对齐 | 宿主 AGP 9.4.0 (由 `autojs6-platform-versions` 1.7.5 自动选定, Gradle 9.7.1, Kotlin 2.4.0, KSP 2.3.12); D1 选择压缩打包 |
| NDK r28+ 默认 16 KB 对齐 (仅 64 位); r27 及以下需 `-Wl,-z,max-page-size=16384` (推荐同时 `-Wl,-z,common-page-size=16384`); r27+ 在 16 KB 模式下不再定义 `PAGE_SIZE` 宏 | D3 的下限与显式标志; 自编 C/C++ 中若引用 `PAGE_SIZE` 需改用 `getpagesize()` / `sysconf(_SC_PAGESIZE)` (本次审计的自编代码未见此类引用, 各条目落地时复核) |
| NDK r26 及以下的 `libc++_shared.so` 未对齐; 本机实测: r26.1 全部 ABI `0x1000`; r27.0 / r28.2 / r29.0 的 arm64 与 x86_64 为 `0x4000`, 32 位仍 `0x1000` | P1.1 用 r28.2 副本替换宿主 libc++; 32 位按 D8 接受 |
| `zipalign -c -P 16 -v 4 app.apk` 需 build-tools >= 35.0.0; `llvm-readelf -lW lib.so` 的 LOAD `Align` 应为 `0x4000`; 使用 r27 以下工具链时还需检查 RELRO: `(VirtAddr + MemSiz) % 0x4000 == 0` | 本机 build-tools 35.0.0 ~ 37.0.0, NDK r26.1 / r27.0 / r28.2 / r29.0 均已安装 |
| 测试环境: SDK Manager 的 "Google APIs Experimental 16 KB Page Size" 镜像; Pixel 8 / 8a / 9 系列开发者选项 "Boot with 16KB page size"; `adb shell getconf PAGE_SIZE` 应返回 `16384` | 本机已装 `system-images;android-36;google_apis_ps16k;{x86_64,arm64-v8a}` 与 `android-37.0` 同类镜像 (arm64 镜像在 x86 主机上不可用, arm64 证据由真机提供); 当前接入的 3 台真机 (Xiaomi 23046RP50C, Sony G8441, Sony XQ-AT72) 均不支持 16 KB 启动 |

### 3.2 宿主与打包链路

| 问题 | 锚点 |
| --- | --- |
| 宿主 APK 内全部 3 个 `.so` 只对齐到 4 KB: `libc++_shared.so` (1.3 MB, 来自旧 NDK, `c5e8b6e54` / `441e02754` 时期加入) 与 `libjackpal-androidterm5.so` / `libjackpal-termexec2.so` (预编译 AAR, 无源码) | `app/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86,x86_64}/libc++_shared.so` (4 个均 `0x1000`); `libs/jackpal-androidterm-libtermexec-1_0/libtermexec-release.aar` (`jni/*/` 8 个均 `0x1000`); 扫描对象 `app/build/outputs/apk/app/debug/autojs6-v6.8.0-arm64-v8a.apk` (2026-09-11) |
| jackpal 两个库被 APK 打包器默认写进每一个用户 APK, 因此宿主的不合规会传播到全部用户生成的应用 | `apkbuilder/ApkBuilder.kt:366` (`defaultLibsToInclude = TERMINAL_EMULATOR.libsToInclude`); `apkbuilder/template/RemoteApkBuildRequestPreparer.kt:571-660` (从宿主 APK / `nativeLibraryDir` 复制) |
| 宿主把插件 `.so` staging 到自身目录后 `System.load` 进宿主进程: OpenCV (`libopencv_java4.so`, 已 16 KB) 与 Image-Quantization (`libpngquant_bridge.so`, 当前 4 KB). 插件不对齐时崩溃的是宿主进程, 且错误形态为 `LinkageError` 而非可解释的错误码 | `core/plugin/opencv/OpenCvPluginHost.kt:219-226, 266`; `core/plugin/imagequant/ImageQuantPluginHost.kt:226, 323` |
| OCR 引擎 (`plugin-api/rapid-ocr-engine`, `plugin-api/paddle-ocr-engine`) 以 `System.loadLibrary("onnxruntime" / "RapidOcr" / "Native")` 加载, 库不在宿主 APK 内; 运行于插件进程, 或被打包进 inrt 用户 APK 后运行于该应用进程 | `plugin-api/rapid-ocr-engine/.../OcrEngine.kt:17-18`; `plugin-api/paddle-ocr-engine/.../OCRPredictorNative.java:44`; `runtime/api/OcrRapid.kt:87` |
| 打包链路: 宿主把所需 `.so` (宿主自带 + 所选插件的) 收集为一个 DEFLATED zip 交给 APK-Builder-Template 插件; 插件用 `java.util.zip.ZipOutputStream` 把全部条目 (含 `lib/**/*.so`) 以 DEFLATED 写出, 无任何对齐逻辑, `extractNativeLibs` 直接继承模板 (`configureManifest` 只改名称 / 包名 / 版本 / 权限 / 启动主题); 内置的 apksig 虽有 `LIBRARY_PAGE_ALIGNMENT_BYTES = 16384`, 但只对 STORED 条目有意义 | `RemoteApkBuildRequestPreparer.kt:1070-1090`; `AutoJs6-Plugin-APK-Builder-Template/app/src/main/java/org/autojs/plugin/apkbuilder/template/impl/RemoteApkLightweightBuilder.kt:198-213, 490-518, 535-549`; 宿主 `modules/apk-signer/src/main/java/com/android/apksig/{Constants.java:46, ApkSigner.java:765-799, 1279-1280}` |
| `generateRuntimeKit` 产出的 `native-libs.json` 只记录 `lib/**/*.so` 的摘要, 不记录对齐; `build-contract.json` 无页对齐字段 | `app/build.gradle.kts:1318-1322, 1326-1339` |
| 宿主 CI 与 Runtime Kit 发布流程没有任何对齐检查; CI 安装的 CMake 3.10.2 已无对应的原生构建 (宿主无 CMake 工程, `.git/modules/app/src/main/cpp` 为 `881c7f7b2` 把 Node 桥迁出后遗留的本地元数据) | `.github/workflows/android.yml:43-53`; `.github/workflows/release-runtime-kit.yml:51-60` |
| 宿主 manifest 未声明 `extractNativeLibs` / `pageSizeCompat` (由 AGP 依 `useLegacyPackaging` 写入 `extractNativeLibs=true`) | `app/src/main/AndroidManifest.xml` |
| 宿主既有的对齐检查工具只服务 Yolo R5 发布 (`allLoadAlignmentsAtLeast16384`, `zipalign -c -P 16 4`), 未泛化 | `tools/yolo/verify-yolo-r5-packaging.ps1:533, 707` |

### 3.3 插件矩阵 (64 位 ELF 现状取自 2026-09-11 扫描, 32 位仅注记)

| 仓库 | 原生来源 | 64 位 ELF 现状 | 打包 | NDK | 显式标志 | 既有门禁 / CI | 运行进程 | 阶段 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| OpenCV | OpenCV 4.8.0 源码重编为 `libs/opencv-native-4.8.0.aar` + provenance (`NATIVE_BUILD.md`, `scripts/opencv/build-native-aar.ps1:41,128`) | `libopencv_java4.so` 4 ABI 均 `0x4000` | 压缩 | r26.1 (脚本内, 带标志) | 有 | `verifyElfLoadSegmentAlignment` (`app/build.gradle.kts:27,330,370,391,580`) + `.github/workflows/build.yml:36-39` | 宿主进程 (staging 加载) | P2.2 (核对) |
| Image-Quantization | CMake: libimagequant + libpng 1.6.49 静态 -> `libpngquant_bridge.so` (`app/src/main/cpp/CMakeLists.txt`) | arm64 `0x1000` | 压缩 | r26.1 (`app/build.gradle.kts:24`) | 无 | `verification.yml` 只比对指纹, 无对齐检查 | 宿主进程 (staging 加载) | P2.1 |
| Paddle-OCR v3 | CMake `libNative.so` + Paddle-Lite 预编译 (`libs/paddleocr/PaddleLite/**`, 无 provenance) + OpenCV 4.8.0 AAR | `libNative.so` `0x1000`, 自带 `libc++_shared.so` `0x1000`; `libpaddle_light_api_shared.so` `0x10000`, `libopencv_java4.so` `0x10000` | 未压缩 (`useLegacyPackaging=false`, `app/build.gradle.kts:97`) | r21.1 (`version.properties` `PADDLE_OCR_NDK_VERSION`) | 无 | 无 / 无 CI | 插件进程; 或打包进用户 APK | P2.3 |
| Paddle-OCR v4 / v5 / v6 | Maven `com.microsoft.onnxruntime:onnxruntime-android:1.21.1` + OpenCV 4.8.0 AAR + 自带 `libc++_shared.so` (`libs/ppocrvN-plugin-runtime/src/main/jniLibs`) | 64 位全部 OK (`libonnxruntime*.so` `0x4000`, libc++ / opencv `0x10000`); armeabi-v7a 三库 `0x1000` | 压缩 | 无 (无自编) | - | 无 / 无 CI | 插件进程; 或打包进用户 APK | P2.5 |
| Rapid-OCR v3 / v6 | CMake `libRapidOcr.so` + RapidAI/OnnxruntimeBuilder 预编译 `libonnxruntime.so` (v3 1.14.0, v6 1.18.0, `version.properties`) + opencv-mobile 4.5.3 静态 | `libRapidOcr.so` `0x1000`, `libonnxruntime.so` `0x1000` (arm64 与 x86_64 均如此) | 压缩 | r23.1 (`RAPID_OCR_NDK_VERSION`) | 无 | 无 / 无 CI | 插件进程; 或打包进用户 APK | P2.4 |
| MLKit-Barcode / MLKit-OCR | Maven `com.google.mlkit:barcode-scanning:17.3.0` / `text-recognition-chinese:16.0.1` | 64 位 `0x4000`; 32 位 `0x1000` | 压缩 | - | - | 无 / 无 CI | 插件进程 | P4.4 |
| Yolo-NCNN | CMake `libautojs_yolo.so` + ncnn 20260526 静态 (仅 arm64) | `0x4000` (仅靠 r29 默认值) | 压缩 | r29.0 | 无 | 无 / 无 CI; 现有证据为 4 KB 设备 (`docs/evidence/r9-*-4k-*.json`) | `:provider` 进程 | P3.8 |
| ImGui | CMake Dear ImGui -> `libautojs6_imgui.so` | x86_64 `0x1000` (arm64 同因, 待扫描确认) | 压缩 | r26.1 (`version.properties` `IMGUI_NDK_VERSION`) | 无 | `android.yml` 只 assemble | 插件进程 (`:privilege_broker` 为 Binder) | P3.1 |
| TypeScript-Engine | CMake 单文件 JNI -> `libautojs6_r2_fd_flags.so` (4 ABI, 无 abiFilters) | 64 位 `0x4000`; 32 位 `0x1000` | 未压缩 (AGP 默认) | r28.2 (`NDK_VERSION`) | 无 | `verifyR2FileDescriptorNativeBridgeInApk` 只查库存 (`app/build.gradle.kts:1428-1444`); 无 CI | `:typescript_*` 进程 | P3.2 |
| Lua-Runtime | CMake Lua 5.4.8 -> `libautojs_lua_runtime.so` (arm64 + x86_64) | OK | 未压缩 | r28.2 | 无 | `tools/verify_{debug,release_candidate}_artifacts.ps1` (zipalign -P 16 + LOAD >= 16384) + `ci.yml:82`; API 37 模拟器 16 KB 证据 (`ROADMAP-R5.md:77,142,166`) | `:lua_runtime` | P3.3 |
| NodeJs-Runtime | 预编译 `libnode.so` (Node 24.21.0 源码容器重编, `LDFLAGS=-Wl,-z,max-page-size=16384`, LFS) + CMake `libautojs6-node.so` 桥 | OK | 压缩 | lock 记 r28.2, `container/Dockerfile` 仍写 26.1 | libnode 有, 桥无 | `tools/nodejs/runtime-build/verify-source-runtime.py:41`; `build.yml` 无对齐步骤 (`restore-native` 只查 ELF 魔数); SM-A566B 16 KB 验收 (`docs/ARM64-16KB-ACCEPTANCE-20260910.md`) | `:nodejs_runtime` | P3.4 |
| QuickJS-Runtime | CMake QuickJS 2026-06-04 -> `libautojs_quickjs_runtime.so` | OK | 未压缩 | r28.2 | 有 | `tools/quickjs/verify_apk_variants.py` (ELF + zip 偏移) + `build.yml` `verify-16kb` 作业 (API 35 16 KB 模拟器) | `:quickjs_runtime` | P3.5 (参考实现) |
| OpenCC | CMake OpenCC 静态 -> `libopencc_jni.so` | OK | 未压缩 (AGP 默认) | r28.2 | 有 (max + common) | `opencc-release.yml:399-402` `zipalign -c -P 16 -v 4`; `build.yml:163-195` `verify-16kb` 作业 | 插件进程 | P3.6 (参考实现) |
| MediaInfo | CMake MediaInfoLib 26.05 + ZenLib 0.4.41 子模块 -> `libmediainfo.so` | OK | 压缩 | r29.0 | 有 | `scripts/verify_native_build.py:209,233-237` (`elfMaxPageSize` 来自 `native/upstream.lock.json`) + `build.yml` | 插件进程 | P3.7 (参考实现) |
| Ace-Editor | 预编译 LuaLS 3.18.2 (`tools/ace-lsp/build-luals-android.ps1`, r29 luamake, lock 记 sha256 / bytes) | 64 位 `0x4000`; armeabi-v7a `0x1000` | 压缩 + `extractNativeLibs="true"` (LuaLS 以可执行文件 exec) | r29.0 | 仅 x86 stub 有 (`:252`) | lock 无对齐字段; 无 CI | 独立 OS 进程 (exec) | P3.9 |
| Bun-Runtime | 预编译 Bun 1.4.0 PIE 可执行 (`libbun_exec.so`) + `libbun_supervisor.so` (r29, 带标志) | OK | 压缩 + exec | r29.0 | supervisor 有 | `tools/bun-runtime/verify-apk-runtime.mjs:216-225` (`zipalign -c -P 16 4`) + lock `minimumLoadAlignment: 16384` + 4 个 CI 工作流 | `:bun_runtime` (exec) | P3.10 (仅叠加门禁; x86_64 JSC 崩溃见 B1) |
| Python-Runtime | Maven Chaquopy 17.0.0 / CPython 3.13.9 (`locks/python-runtime.lock`) | `lib/` 内 8 个均 `0x4000` | 未压缩 | - | - | `tools/verify-r2-static.ps1` 等 PS1 门禁 (wheel 的 16 KiB LOAD 门禁见 `docs/maintenance/CPYTHON_RUNTIME_POLICY.md:163-171`); 无 CI | `:python_runtime` | P4.1 (assets 内的扩展模块尚未扫描) |
| Three-Stone-AI | Maven `com.google.ai.edge.litertlm:litertlm-android:0.15.0` | `liblitertlm_jni.so` `0x4000` | 未压缩 | - | - | 无 / 无 CI | 插件进程 | P4.2 |
| Archive-Manager | Maven `com.github.luben:zstd-jni:1.5.7-15@aar` | `0x4000` | 未压缩 | - | - | 无 | 插件进程 | P4.3 |
| 纯 JVM 18 个 | 无 (`org.tukaani:xz` 为纯 Java) | 无 `.so` | - | - | - | - | - | P4.5 |
| APK-Builder-Template | 无自有原生; 转写宿主与插件的 `.so` | 继承来源 | DEFLATED 写入 | - | - | `build-from-runtime-kit.yml` 等 3 个工作流均无对齐检查 | - | P1.6 |

### 3.4 可复用资产

- 宿主本次会话新增 `tools/16kb/scan_alignment.py`: 一次性扫描同目录全部仓库的最新 APK, 仓库内散落的 `.so` 与 AAR / JAR 内的 `.so`, 输出 ELF 最小 `PT_LOAD` 对齐, zip 压缩标志与数据偏移, `extractNativeLibs`, 并生成 Markdown 报告 (本节数据的来源). 用途是跨仓库巡检; 单仓库门禁由 D4 的共享插件承担.
- 门禁实现参考 (按可移植性排序): QuickJS `tools/quickjs/verify_apk_variants.py` (纯 Python, ELF + zip 偏移), OpenCV `verifyElfLoadSegmentAlignment` (Gradle Kotlin, 直接解析字节), MediaInfo `scripts/verify_native_build.py` (lock 驱动的 `elfMaxPageSize`), Lua `tools/verify_debug_artifacts.ps1`, Bun `tools/bun-runtime/verify-apk-runtime.mjs`, 宿主 `tools/yolo/verify-yolo-r5-packaging.ps1:533,707`.
- CI 参考: QuickJS / OpenCC 的 `verify-16kb` 作业 (16 KB 模拟器 + `getconf PAGE_SIZE`), OpenCC 的 `zipalign -c -P 16 -v 4` 发布步骤.
- 预编译库的可复现构建与 provenance 参考: OpenCV (`NATIVE_BUILD.md` + `scripts/opencv/build-native-aar.ps1` + provenance JSON), MediaInfo (`native/upstream.lock.json`), Node (`tools/nodejs/runtime-build/runtime-build.lock.json`), Bun (`tools/bun-runtime/runtime.lock.json`), Ace (`tools/ace-lsp/luals-build-lock.json`).

---

## 4. 目标状态

- 宿主的 universal 与 4 个 ABI 拆分 APK, inrt 模板, Runtime Kit, 全部官方插件 APK, 以及经 APK 打包器生成的用户 APK 中, 每一个 arm64-v8a / x86_64 的 `.so` 均满足 `PT_LOAD` 对齐 >= 16 KB; 未压缩打包的仓库同时满足 zip 数据偏移 16 KB 对齐.
- 每个仓库的构建与 CI 都由 D4 的共享门禁守住, 并对第三方依赖升级 (MLKit / ORT / Chaquopy / LiteRT-LM / zstd-jni) 形成自动回归检查.
- 宿主对不对齐的插件原生库给出结构化错误而非崩溃; 官方索引与插件中心暴露页对齐兼容性 (D6).
- 以上均在 ps16k 模拟器与原生 16 KB 真机上取得 `DEVICE-16K` 级证据, 且在兼容模式关闭 (或 fatal) 的条件下通过 (D5, D10).

---

## 5. 阶段总览

| 阶段 | 主题 | 依赖 | 涉及仓库 | 状态 |
| --- | --- | --- | --- | --- |
| P0 | 基线, 共享门禁与工具链 | 无 | 宿主 (tools), Gradle-Platform-Versions | 完成 (共享插件 1.8.0 已公开发布, CI 与独立消费验证通过) |
| P1 | 宿主与打包链路 | P0.2 (门禁), 其余可先行 | 宿主, APK-Builder-Template | 宿主及用户 APK 的 Samsung 16 KB 真机验证通过; 最终 Runtime Kit / 远程 CI 待补; D11 已合并收尾 |
| P2 | 被宿主进程加载或打包进用户 APK 的插件 | P0.2, P1.1 (libc++) | Image-Quantization, OpenCV, Paddle v3-v6, Rapid v3 / v6 | 静态门禁通过, OpenCV / 量化 / 用户 APK 真机通过; 6 个独立 OCR 插件补测失败, 需按 P6 的 X1-X4 修复 |
| P3 | 独立进程的自编原生插件 | P0.2 | ImGui, TypeScript-Engine, Lua, NodeJs, QuickJS, OpenCC, MediaInfo, Yolo-NCNN, Ace-Editor, Bun | 构建门禁及列明的模拟器 / arm64 真机冒烟通过; Bun x86_64 仍按 B1 跟踪; 最终发布候选收据待补 |
| P4 | 第三方 / Maven 原生依赖插件与纯 JVM 插件 | P0.2 | Python, Three-Stone-AI, Archive-Manager, MLKit x2, 纯 JVM 18 个 | 门禁完成; Python / MLKit / Archive 真机运行通过, Three-Stone-AI 真实模型推理待补 |
| P5 | 索引, 插件中心与文档 | P1.5, P2 | Official-Plugins-Index, 宿主 (plugin center), common-plugin-api | 代码与索引生成完成; 记录真实发布资产, 未发布新版插件 |
| P6 | 验收矩阵与关闭 | P1-P5 | 全部 | 进行中; 静态复扫为 0, 全矩阵与发布后 CI 待补 |

阶段内条目可并行; P2 / P3 / P4 之间无依赖, 只是优先级不同 (P2 的库会进入宿主进程或用户 APK, 影响面最大). P0.2 未完成前, 各仓库可先用 `tools/16kb/scan_alignment.py` 或本仓库已有门禁取得 `ELF` 级证据, 门禁落地后补 `ANDROID_BUILD` 证据.

---

## P0: 基线, 共享门禁与工具链

目标: 固定事实与工具, 让后续每个仓库的改动都能被同一把尺子度量.

### P0.1 基线扫描 (完成 2026-09-11)

- [x] 编写跨仓库扫描脚本并对宿主与 43 个同目录仓库执行一次: 每个仓库取 `**/build/outputs/apk/**` 中每个变体目录的最新 APK (至多 5 个), 解析 `lib/**/*.so` 的 ELF `PT_LOAD` 最小对齐, zip 压缩标志与数据偏移, `aapt2` 读取 `extractNativeLibs` / `native-code` / `targetSdk`; 另扫描仓库内散落的 `.so` 与 AAR / JAR 内的 `.so`. 结果汇总为第 3 节. (SOURCE: `tools/16kb/scan_alignment.py`; ELF: 报告 `build/tmp-16kb/alignment-report.md`, 2026-09-11)
- [x] 三组只读审计 (运行时插件 7 个, 视觉 / OCR 插件 10 个, 杂项 7 个 + APK-Builder-Template 打包路径 + 纯 JVM 18 个) 的结论合并进 3.2 / 3.3 / 3.4. (DOCS: 本文)

### P0.2 共享门禁插件 (D4)

仓库: `AutoJs6-Gradle-Platform-Versions`. 设计草案见附录 A, 命名见待决 Q1.

- [x] 新增项目级插件 (与 `autojs6-platform-versions` 同仓库发布, 建议 id `io.github.supermonster003.autojs6-native-alignment`) 与任务 `verifyNativePageAlignment`: 输入为已装配变体的 APK 文件 (按文件扫描 `build/outputs/apk/**`, 或经反射读取 AGP `androidComponents` 的 APK 产物, 二选一见 Q1), 对 `strictAbis` (默认 `arm64-v8a`, `x86_64`) 内每个 `.so` 断言 `PT_LOAD` 最小对齐 >= `pageSize` (默认 16384); 条目未压缩时断言数据偏移 % 16384 == 0; 可选 RELRO 末端对齐检查; 输出 `build/reports/native-alignment/<variant>.json` 与失败摘要. (JVM: 插件仓库单元测试, 用合成的最小 ELF 头与 zip 夹具覆盖 ELF32 / ELF64 / 压缩 / 未压缩 / 缺失 APK 四类分支)
- [x] 扩展 DSL `nativeAlignment { strictAbis; pageSize; expectNoNativeLibraries; alsoScan(files) }`: `expectNoNativeLibraries = true` 供纯 JVM 插件断言 "没有任何 `.so`" (P4.5); `alsoScan` 供 Python 插件扫描 assets 内的 ELF 归档 (P4.1).
- [x] 自动挂接: 应用插件后 `assemble<Variant>` `finalizedBy` 对应的验证任务; 提供 `-Pautojs.nativeAlignment.skip=true` 的逃生口 (仅本地, CI 禁止).
- [x] 消费样例与文档: 插件仓库 README 增加用法; `sample/` 增加一个含 4 KB `.so` 的负样例工程, 断言任务失败. (JVM + ANDROID_BUILD: 样例工程 `assembleDebug` 失败于预期错误)
- [x] 发布新版本 (建议 1.8.0), 宿主与各插件仓库在各自条目中升级 `settings.gradle.kts` 的插件版本并应用门禁.

### P0.3 宿主工具与验证清单 (完成 2026-09-11)

- [x] `tools/16kb/scan_alignment.py` 入库: 项目根目录与 SDK 路径可由 `AUTOJS6_PROJECTS_ROOT` / `ANDROID_HOME` 覆盖, 输出目录默认 `build/tmp-16kb/`; 用法写在脚本头部. (SOURCE)
- [x] `tools/16kb/verify_device.sh <serial> <package>`: 输出 `getconf PAGE_SIZE`, 目标包的 `dumpsys package` 中与页大小 / 兼容模式相关的行, 安装后首个启动的 logcat 中 `16 KB` / `page size` 关键行, 以及 Android 17 上开关 fatal 属性的子命令 (附录 E 的命令集脚本化). (SOURCE; DEVICE-16K: 在 ps16k 模拟器上跑通)
- [x] 创建 ps16k AVD 的命令与命名约定写入附录 E (`aj6-ps16k-36-x86_64`, `aj6-ps16k-37-x86_64`), 并在维护者机器上创建一次. (DEVICE-16K)

P0 验收: 共享门禁插件发布且负样例失败; `verify_device.sh` 在 ps16k 模拟器上输出 `16384`; 本文 3.x 节数据与脚本输出一致.

---

## P1: 宿主与打包链路

目标: 宿主自身与用户生成的 APK 合规, 宿主对插件原生库有加载前保护, CI 有门禁.

### P1.1 `libc++_shared.so` 替换

- [x] 用 NDK r28.2.13676358 `toolchains/llvm/prebuilt/<host>/sysroot/usr/lib/{aarch64,x86_64,arm,i686}-linux-android*/libc++_shared.so` 替换 `app/src/main/jniLibs/` 下 4 个文件 (64 位 `0x4000`; 32 位仍 `0x1000`, 按 D8 接受并在 provenance 中注明); 新增 `app/src/main/jniLibs/PROVENANCE.md` 或 `libc++_shared.provenance.json` (NDK 版本, 各 ABI sha256, 对齐值). (ELF: `scan_alignment.py`)
- [x] 确认 OpenCV 插件 (r26b 构建) 与升级后的 libc++ 兼容: 宿主加载 OpenCV 插件运行时后执行 `images.*` 冒烟 (libc++ 向后兼容, 预期无问题, 仍需证据). (DEVICE: 至少 1 台 4 KB 真机; DEVICE-16K: ps16k 模拟器)
- [x] changelog `improvement`: 宿主原生库 16 KB 页大小对齐 (与 P1.2 合并为一条).

### P1.2 jackpal 终端模拟器原生库重编 (D2)

- [x] 确认源码映射: 上游 jackpal/Android-Terminal-Emulator 中 `libtermexec` 模块的 JNI (`process.cpp`) 对应 `libjackpal-termexec2.so`, `term` 模块的 JNI (`termExec.cpp` / `fileCompat.cpp` / `common.cpp`) 对应 `libjackpal-androidterm5.so`; 以现有 AAR 内库的导出符号 (`Java_jackpal_androidterm_*`) 逐一核对, 并记录上游 commit. (DOCS: `libs/jackpal-androidterm-libtermexec-1_0/native-build/README.md`)
- [x] 新增可复现构建: `libs/jackpal-androidterm-libtermexec-1_0/native-build/{CMakeLists.txt 或 Android.mk, build.ps1 / build.sh, upstream.lock.json}`, NDK r28.2, `ANDROID_PLATFORM=android-24`, 4 ABI, 显式 `-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384`, 产物打成新 `libtermexec-release.aar` (Java 部分与原 AAR 完全一致, 仅替换 `jni/**`); 记录各 ABI sha256 与对齐. 宿主日常构建不引入 NDK (D2). (SOURCE; ELF)
- [x] 回归: 宿主终端模拟器 (抽屉 / 脚本 `$shell` 相关路径) 可创建 pty 并执行命令; inrt 用户 APK 内的终端功能同样可用. (DEVICE x2 含 1 台 ps16k)
- [x] changelog `improvement` (与 P1.1 合并).

### P1.3 宿主构建门禁与 Runtime Kit 清单

- [x] `app/build.gradle.kts` 应用 P0.2 的门禁插件, `strictAbis = [arm64-v8a, x86_64]`, 覆盖 `assembleApp*` / `assembleInrt*` 全部变体与 `generateRuntimeKit` 的输入模板 APK; 现有 "宿主 APK 不得携带 OpenCV JNI" 守卫 (`app/build.gradle.kts:88-129`) 保留. (ANDROID_BUILD)
- [x] Runtime Kit: `native-libs.json` 每个条目追加 `minLoadAlign` (十进制字节), `build-contract.json` 与 `runtime-kit.json` 的 `contract` 追加 `nativeLibPageAlignment: 16384` (兼容: 字段为新增, `schemaVersion` 不变; APK-Builder-Template 读取时缺省视为 4096). (SOURCE; ANDROID_BUILD: `generateRuntimeKit` 产物核对)
- [x] 在 P1.1 / P1.2 之前先挂门禁并观察到它对当前 4 KB 库报错, 作为门禁有效性的证据. (ANDROID_BUILD: 失败日志摘录)

### P1.4 宿主 CI

- [x] `.github/workflows/android.yml`: 门禁随 `assemble` 自动执行; 另加显式步骤对上传的 release arm64 / universal APK 执行 `zipalign -c -P 16 -v 4` (build-tools 35+) 与 `tools/16kb/scan_alignment.py` 单仓库模式, 输出作为工件; 移除已无用途的 "Install CMake 3.10.2" 步骤 (若确认没有任何模块依赖). (ANDROID_BUILD: 对应构建和 SDK 检查本地通过; 发布后远程 CI 待补)
- [x] `.github/workflows/release-runtime-kit.yml`: 同上, 对 inrt 模板 APK 执行门禁后再发布. (ANDROID_BUILD)

### P1.5 宿主运行时保护 (D6)

- [x] 新增 Android-free 工具 `core/plugin/nativealign/{ElfLoadAlignment,NativePageSize}.kt`: ELF 解析器不依赖 Android; 页大小读取封装在 Android 层. 解析 ELF32 / ELF64 程序头得到最小 `PT_LOAD` 对齐 (只读前 64 KB); `NativePageSize.current()` 用 `Os.sysconf(OsConstants._SC_PAGESIZE)`; `isCompatible(minAlign, pageSize)`. (JVM: `ElfLoadAlignmentTest`, 夹具复用 P0.2 的合成 ELF)
- [x] `OpenCvPluginHost.stageCandidate` / `ensureLoaded` 与 `ImageQuantPluginHost.ensureLoaded` 在 `System.load` 前调用校验: 设备页大小 16 KB 且库对齐 < 16 KB 时抛新错误码 `NATIVE_LIBRARY_PAGE_ALIGNMENT_UNSUPPORTED` (两个 `*PluginLoadError` 枚举各加一项), 文案说明 "插件 X 的原生库未按 16 KB 对齐, 请升级插件"; `LoadedRuntime` 记录 `nativeMinLoadAlign` 供诊断输出. 字符串 x 11 文件, 按 name 升序. (JVM: ELF 解析与错误策略测试; 新版插件在 ps16k 加载通过; API 37/fatal 中含真实旧版 4 KB JNI 的测试插件返回结构化错误, 见验证记录)
- [x] APK 打包器: `RemoteApkBuildRequestPreparer` 收集宿主 / 插件 `.so` 时解析对齐, 任一 64 位库 < 16 KB 时在构建进度与结果中给出警告 (不阻断, 见待决 Q3), 并把最小对齐写入构建输入的元数据. (JVM: `NativeBuildAlignmentTest`; 元数据存于 Prepared 和本地输入 sidecar, 不向严格白名单 Binder 协议添加未知 extras)
- [x] changelog `feature` / `improvement` 各 1 条 (加载前校验; 打包警告).

### P1.6 APK-Builder-Template 回归守卫

仓库: `AutoJs6-Plugin-APK-Builder-Template`.

- [x] JVM 测试: 用一个含 `lib/arm64-v8a/x.so` 的模板夹具走 `RemoteApkLightweightBuilder`, 断言输出 APK 中 `.so` 条目为 DEFLATED 且 manifest `extractNativeLibs` 与模板一致 (D1 的硬约束); 断言不存在 STORED 的 `.so`. (JVM)
- [x] CI (`build-from-runtime-kit.yml`): 对 `runtime-kit/template.apk` 执行 ELF 对齐门禁 (复用 P0.2 插件的 `alsoScan` 或 `scan_alignment.py`), 对构建产物执行 `zipalign -c -P 16 -v 4` 仅作为信息 (压缩打包下不要求通过, 记录即可). (ANDROID_BUILD)
- [x] README 增加 "16 KB 页大小: 输出 APK 继承模板的压缩打包, 合规性取决于模板与所选插件的 ELF 对齐" 说明. (DOCS)

### P1.7 文档与收尾

- [x] 宿主 `.changelog` (10 语言) 与 README 重新生成; 本文 P1 条目回填证据. (DOCS)
- [x] `AutoJs6-QuickJS-Host` 关联分支已由维护者合并到宿主 master 并解决冲突 (`d17d12578`), 工作树与目录均已移除. D11 收尾完成, 无需再次 rebase. (DOCS; `git worktree list --porcelain`)

P1 验收: 宿主 universal / arm64-v8a / x86_64 / inrt 模板 APK 通过门禁 (`ELF` 全 `0x4000` 或以上); ps16k 模拟器 (API 36) 上安装宿主无兼容模式警告, `verify_device.sh` 输出 `16384`, 冒烟脚本 (`sample/accessibility/smoke-legacy-api.js` + 终端 + OpenCV 插件 `images.*`) 通过; 原生 16 KB 真机同样通过; 用 P2.1 之前的 Image-Quantization APK 触发 P1.5 的结构化错误; CI 绿; changelog 已写.

---

## P2: 被宿主进程加载或打包进用户 APK 的插件

目标: 消除会进入宿主进程或用户 APK 的 4 KB 库.

### P2.1 Image-Quantization

- [x] `app/build.gradle.kts:24` 的 NDK 26.1 -> 28.2 (同步 BuildConfig / manifest placeholder / `verification.yml` 的运行时指纹); `app/src/main/cpp/CMakeLists.txt` 补显式标志; 应用共享门禁. (ANDROID_BUILD; ELF)
- [x] 版本号递增, changelog `improvement`, README. (DOCS)
- [x] 宿主侧: ps16k 上 P1.5 的错误消失, `images.quantize` 类冒烟通过. (DEVICE-16K)

### P2.2 OpenCV (核对)

- [x] 叠加共享门禁 (保留 `verifyElfLoadSegmentAlignment` 与 provenance 校验); 用 `scan_alignment.py` 复核当前发布 APK; 在 API 36/37 ps16k, 4 KB arm64 与 Samsung SM-A566B 原生 16 KB arm64 上通过宿主加载 / 运算 / 异常冒烟, 见 Samsung 补验. (ANDROID_BUILD; DEVICE-16K)
- [x] 2026-09-12: 以 NDK r28c (28.2.13676358, clang 19.0.1) 重编 4 个 ABI 的 `libopencv_java4.so`, 插件 v1.1.2 (build 12) 最低宿主 build 5280; 宿主策略同时接受 r26b / r28c 原生构建; 宿主 5280 + 插件 v1.1.2 的 `OpenCvPluginHostDeviceTest` 在 Samsung 原生 16 KB arm64 (第三时段), 新旧四台 ps16k 模拟器与 Redmi 4 KB arm64 均 2/2 通过. (ANDROID_BUILD; DEVICE-16K; [第二轮证据](evidence/page-size-16kb-round2-20260912.md))

### P2.3 Paddle-OCR v3 (D7)

- [x] `version.properties` `PADDLE_OCR_NDK_VERSION` 21.1 -> 28.2; `libs/paddleocr/src/main/cpp/CMakeLists.txt` 补显式标志; 复核 C++ 源码无 `PAGE_SIZE` 假设. (ANDROID_BUILD)
- [x] 替换 `libs/paddleocr/PaddleLite/cxx/libs/{arm64-v8a,armeabi-v7a}/libc++_shared.so` 为 r28.2 副本 (64 位 `0x4000`); 为 Paddle-Lite 预编译库补 `PROVENANCE.md` (来源未知则如实记录 "来源未记录, 2026-09-11 实测 `0x10000`" + sha256). (ELF; DOCS)
- [x] 该仓库为未压缩打包, 门禁同时覆盖 zip 偏移; 应用共享门禁; 版本号递增, changelog, README. (ANDROID_BUILD; DOCS)
- [x] `1000 x 320` 生成图崩溃已补 4 KB 对照 (2026-09-12): Xiaomi 23046RP50C 上相同产物, 相同 pc `0xc7a44` 复现, 尺寸扫描显示宽 800-1024 且高 320/352 稳定崩溃, 1000 x 640 / 640 x 320 / 1000 x 300 / 944 x 640 通过; 属于 Paddle-Lite 预编译库的输入相关缺陷, 与页大小无关, 转 backlog B7. OpenCV payload 同步 r28c 后, v3 在 Redmi 4 KB 以 1000 x 640 生成图与参考图通过. (DEVICE; 第二轮证据)
- [x] 原生 16 KB arm64 上 r28c payload 的 v3 最终产物复验 (2026-09-12 Samsung 第三时段): `1000 x 640` 生成图与 `944 x 640` 参考图均通过. (DEVICE-16K)

### P2.4 Rapid-OCR v3 与 v6 (D7)

- [x] `libs/rapidocr/build.gradle.kts`: 去掉 RapidAI/OnnxruntimeBuilder 7z 下载, 改为解包 Maven `com.microsoft.onnxruntime:onnxruntime-android:1.21.1` 的 AAR (`jni/<abi>/libonnxruntime.so` + `headers/`) 到 `build/onnxruntime-native/`, `version.properties` 的 ORT 版本键随之更新; CMake 按 1.21 的 C++ API 修正编译错误 (预期改动小). (ANDROID_BUILD)
- [x] `RAPID_OCR_NDK_VERSION` 23.1 -> 28.2; CMake 补显式标志 (含 x86_64); 应用共享门禁. (ELF)
- [x] 模型与新 ORT 的推理结果一致性: 用仓库内既有样例图片对比 v3 / v6 识别结果. (DEVICE x1 4 KB + DEVICE-16K)
- [x] 版本号递增, changelog, README; 两个仓库分别提交. (DOCS)
- [x] X1 修复 (2026-09-12): `-DANDROID_STL=c++_shared` 使 AGP 打包 NDK r28.2 的 `libc++_shared.so`; OpenCV AAR 换为 r28c donor; Samsung 16 KB (首批 r26b donor 产物), API 36/37 ps16k 与 Redmi 4 KB (r28c donor) 的独立 provider 调用通过; v1.0.2. (DEVICE-16K; DEVICE)

### P2.5 Paddle-OCR v4 / v5 / v6

- [x] 应用共享门禁 (64 位已合规); 可选: 用 r28.2 副本替换自带的 armeabi-v7a `libc++_shared.so` (仍为 4 KB, 仅统一来源); 记录 ORT 升级时必须复核对齐的约定. (ANDROID_BUILD; DOCS)
- [x] X2 修复 (2026-09-12): v4/v5/v6 的 arm64-v8a 与 armeabi-v7a `libc++_shared.so` 换为 NDK r28.2 副本 (旧库 RELRO 末端 `0x111000` 与可写数据同页, 16 KB 取整后被 mprotect 只读, 初始化写入 `SEGV_ACCERR`); ORT 会话改为文件路径内存映射, 修复 v4 Server 90 MB 识别模型的 `OutOfMemoryError`; OpenCV payload 同步 r28c. Samsung 16 KB: v6 Tiny / v5 Thai 通过, v4 Server 不再崩溃 (随后的 OOM 由 mmap 修复); Redmi 4 KB: v4 Mobile / Server, v5 Thai / Server, v6 Tiny 全部通过 (Server 含参考图); Samsung 上 v4 Mobile 的三次运行均为旧 APK 的 X2 崩溃 (当时 Mobile 重建失败), 最终 Mobile 产物经 APK 内 libc++ 审计为 r28.2, 其 16 KB 运行时验证待新时段. v1.0.2. (DEVICE-16K; DEVICE)
- [x] 最终产物在原生 16 KB arm64 的复验 (2026-09-12 Samsung 第三时段): v4 Mobile / v4 Server (含参考图), v5 Thai, v6 Tiny 均通过, v4 Server 不再 OOM. (DEVICE-16K)

### P2.6 用户 APK 端到端

- [x] 宿主 (P1 后) + P2.1 ~ P2.5 的插件生成用户 APK, 静态检查 `.so` 与压缩 / 解压契约, 在 API 36/37 ps16k, 4 KB arm64 与 Samsung 原生 16 KB arm64 执行终端 / 图像 / Paddle v3 / Rapid. 具体组合与 SHA 见部署及 Samsung 收据; 该结果不覆盖独立 OCR 插件进程或未测试的 Paddle 模型变体. (ELF; DEVICE-16K)

P2 验收: 各插件 APK 通过门禁; 宿主在 ps16k 上加载 OpenCV 与 Image-Quantization 无 P1.5 错误; OCR 插件在各自进程内于 ps16k / 16 KB 真机运行; P2.6 用户 APK 通过.

---

## P3: 独立进程的自编原生插件

目标: 显式标志 + 门禁 + 版本记录, 消除对 NDK 默认值的隐式依赖; 修复 ImGui.

- [x] P3.1 ImGui: `version.properties` `IMGUI_NDK_VERSION` 26.1 -> 28.2; CMake 补显式标志; 应用共享门禁; `android.yml` 随 assemble 生效; 版本号, changelog, README. (ANDROID_BUILD; ELF; DEVICE-16K: ps16k 上 ImGui 示例渲染)
- [x] P3.2 TypeScript-Engine: CMake 补显式标志; `verifyR2FileDescriptorNativeBridgeInApk` (`app/build.gradle.kts:1428-1444`) 补 `PT_LOAD` 断言或改为应用共享门禁; 新建最小 `.github/workflows/build.yml` (assemble + 门禁); 版本号, changelog. (ANDROID_BUILD; ELF)
- [x] P3.3 Lua-Runtime: CMake 补显式标志; 叠加共享门禁 (PS1 门禁保留); 记录. (ANDROID_BUILD)
- [x] P3.4 NodeJs-Runtime: 桥 `app/src/main/cpp/CMakeLists.txt` 补显式标志; 统一 `tools/nodejs/runtime-build/container/Dockerfile` 的 `ANDROID_NDK_VERSION` 与 lock (28.2); `build.yml` 增加对齐门禁 (`restore-native` 之后); 叠加共享门禁; 版本号, changelog. (ANDROID_BUILD; ELF)
- [x] P3.5 QuickJS-Runtime: 叠加共享门禁 (可选); 其余保持, 作为参考实现记录. (ANDROID_BUILD)
- [x] P3.6 OpenCC: 叠加共享门禁; 保留 `zipalign -P 16` 与 `verify-16kb` 作业. (ANDROID_BUILD)
- [x] P3.7 MediaInfo: 叠加共享门禁; 保留 `verify_native_build.py`. (ANDROID_BUILD)
- [x] P3.8 Yolo-NCNN: CMake 补显式标志并应用共享门禁; 本地构建与门禁通过; 本轮 `0.1.1` / build 3 在 Samsung SM-A566B 原生 16 KB 下执行 NCNN 参考图片推理, 5 个检测结果通过参考值断言. SHA 与结果见 Samsung 补验. (ANDROID_BUILD; DEVICE-16K 真机)
- [x] P3.9 Ace-Editor: `tools/ace-lsp/build-luals-android.ps1` 的 luamake 链接参数补显式标志 (目前仅 x86 stub 有); `luals-build-lock.json` 每 ABI 增加 `minLoadAlign`; Gradle 门禁断言 lock 与实物一致 (LuaLS 为 exec 的可执行文件, 只需 ELF 对齐, 压缩打包为有意为之); 是否立即重编二进制见待决 Q4; 无 CI 则至少本地门禁. (ANDROID_BUILD; ELF)
- [x] P3.10 Bun-Runtime: 叠加共享门禁 (可选); x86_64 16 KB 的 JSC 崩溃保持记录于 B1 与该仓库 README. (ANDROID_BUILD)

P3 验收: 各插件通过门禁; ps16k (x86_64) 上各插件冒烟通过 (Bun x86_64 除外, 按 B1 记录); Node / Lua / QuickJS / Yolo 在 16 KB arm64 真机上通过.

---

## P4: 第三方 / Maven 原生依赖插件与纯 JVM 插件

目标: 对无法自编的依赖建立 "升级即复核" 的自动门禁; 补扫 lib/ 之外的 ELF.

- [x] P4.1 Python-Runtime: 应用共享门禁 (`lib/` 8 个已 `0x4000`, 未压缩打包由 AGP 处理 zip 对齐); 用 `alsoScan` 或 `tools/verify-r2-static.ps1` 新步骤扫描 Chaquopy 经 assets 交付的扩展模块归档 (`target-3.13.9-0-<abi>.zip` / stdlib `.imy`, 运行时解压后 `dlopen`, 不在 `lib/` 内); wheel 的 16 KiB LOAD 门禁保持; 新建最小 CI. (ANDROID_BUILD; ELF; DEVICE-16K: ps16k 上 `import ssl, sqlite3, ctypes` 冒烟)
- [x] P4.2 Three-Stone-AI: 应用共享门禁 (`litertlm-android:0.15.0` 实测 `0x4000`, 未压缩打包); 记录 "升级 LiteRT-LM 必须复核" 约定. (ANDROID_BUILD)
- [x] P4.3 Archive-Manager: 应用共享门禁 (`zstd-jni:1.5.7-15@aar` 实测 `0x4000`). (ANDROID_BUILD)
- [x] P4.4 MLKit-Barcode / MLKit-OCR: 应用共享门禁 (64 位 `0x4000`); 版本目录注释标明复核约定. (ANDROID_BUILD)
- [x] P4.5 纯 JVM 18 个仓库: 随各自下次发布应用共享门禁的 `expectNoNativeLibraries = true` 模式, 不单独发版; 本条目在全部完成时勾选, 中途在会话记录中列出已完成的仓库. (ANDROID_BUILD)

P4 验收: 上述仓库门禁报告存在且通过; Python 的 assets 内 ELF 扫描结果记录 (若发现 4 KB 扩展模块, 转为 Chaquopy 升级或剔除该模块的待决事项).

---

## P5: 索引, 插件中心与文档 (D6)

- [x] P5.1 插件契约: `plugin-api/common-plugin-api` 新增 meta-data 键常量 `org.autojs.plugin.contract.NATIVE_PAGE_ALIGNMENT` (值为十进制字节, 如 `16384`; 无原生库的插件不声明或声明 `0`); 各含原生库的插件在 manifest 声明, 共享门禁校验声明值 <= 实测最小对齐, 不一致即失败. (SOURCE; JVM)
- [x] P5.2 官方索引: `AutoJs6-Official-Plugins-Index/tools/generate_official_plugin_index.py` 为每个 release 计算 `nativePageAlignment` (优先从发布资产 APK 实测 ELF, 结果按 sha 缓存; 无法下载时回退到 meta-data 声明), 条目级别取各 release 中最新版本的值; 字段名与语义见待决 Q2; `test_generate_official_plugin_index.py` 补用例; 重新生成 `plugins.official.generated.json`. (JVM: 生成器测试; DOCS)
- [x] P5.3 宿主插件中心: `core/plugin/center/{PluginIndexEntry,PluginCenterItem,InstalledPluginMetadataStore}.kt` 解析索引字段与已安装插件的 meta-data; 设备页大小为 16 KB 时, 对 `nativePageAlignment < 16384` 的版本显示标记并在安装 / 选择前警告 (文案 x 11); `PluginCenterItemAdapter` 展示; P1.5 与本条共用 `NativePageSize`. changelog `feature`. (JVM: semantics 测试; DEVICE-16K: ps16k 上用一个故意保留 4 KB 的旧插件 APK 验证标记)
- [x] P5.4 文档: 宿主 README 的插件章节说明 16 KB 标记含义; 各插件仓库 README / changelog 在其条目落地时已写 (核对清单); 本文各阶段回填. (DOCS)

P5 验收: 索引 JSON 含全部官方插件的页对齐字段; 插件中心标记在 ps16k 模拟器上可见; 宿主 changelog 已写.

---

## P6: 验收矩阵与关闭

- [ ] P6.1 矩阵: ps16k x86_64 (API 36 与 37) 上安装宿主 + 全部 x86_64 插件, 关闭兼容模式 (API 37 用 fatal 属性) 后逐一运行冒烟; 原生 16 KB arm64 真机上安装宿主 + 全部 arm64 插件同样执行; 结果表回填本节 (设备 / API / 页大小 / 结论). (DEVICE-16K)
- [x] P6.2 回归守卫: 宿主与 42 个插件的本地构建及门禁通过 (宿主与插件修改后的远程 CI 尚未触发); 索引重新生成; `scan_alignment.py` 全量复扫为 0 个 64 位不合规项. (ANDROID_BUILD; ELF)
- [ ] P6.3 关闭: 更新本文状态与会话记录, 更新自动记忆索引; 附录 B 的 backlog 交由维护者决定是否另立 Roadmap.

---

### 当前验证矩阵 (2026-09-11)

| 环境 | 页大小 / 兼容模式 | 已通过的范围 |
| --- | --- | --- |
| API 36 x86_64 ps16k, emulator-5580 | 16384 / linker=false, PM compat disabled=true | 宿主 libc++ / jackpal PTY, OpenCV, Image Quantization, Python ssl/sqlite3/ctypes, 插件中心标记, Rapid v3 用户 APK; 2026-09-12: Rapid v3/v6 独立 provider (r28c donor), 宿主 5280 + OpenCV v1.1.2 设备测试; Three-Stone-AI 因 AVD 仅 2.5 GB 内存被 LOW_MEMORY 终止 (非插件缺陷) |
| API 37 x86_64 ps16k, emulator-5582 | 16384 / linker=fatal, PM compat disabled=true | 上述宿主与 Python 功能; Rapid v3/v6 用户 APK 与可选 OpenCV 依赖; ImGui 渲染, QuickJS / Node / Lua 执行, MediaInfo 读取, TypeScript 编译; 旧图像量化库负例; 2026-09-12: Rapid v3/v6 独立 provider (r28c donor), 宿主 5280 + OpenCV v1.1.2 设备测试, Three-Stone-AI 真实 Qwen2.5-1.5B 推理通过 |
| Xiaomi 23046RP50C, API 35 arm64 | 4096 | 上述宿主与 Python 功能; 同一个用户 APK 顺序运行 Paddle v3 与 Rapid v3/v6, 均识别出 "测试"; 2026-09-12: Paddle v3 1000 x 320 崩溃的 4 KB 复现与尺寸扫描 (X3 对照) |
| Samsung SM-A566B, API 36 arm64 | 16384, compat off | 宿主及 16 个原生插件共 30 项 instrumentation 通过; 用户 APK 的终端 / 图像 / Paddle v3 + Rapid 通过. 独立 Paddle v3-v6 / Rapid v3-v6 共 6 个插件失败, Three-Stone-AI 缺模型; 见 Samsung 补验; 2026-09-12 第二时段 (localhost:54059): X1/X2 修复后 Rapid v3/v6, Paddle v6 Tiny / v5 Thai 通过, v4 Server 初始化不再崩溃 (随后 OOM 已修), Paddle v3 参考图通过; 时段到期, 最终产物未复验 |
| Redmi 22120RN86C, API 33 arm64 | 4096 | 2026-09-12 最终产物 4 KB 回归: 宿主 5280 + OpenCV v1.1.2 设备测试, Rapid v3/v6, Paddle v3 (1000 x 640 + 参考图), v4 Mobile / Server, v5 Thai / Server, v6 Tiny 全部通过; Three-Stone-AI: Redmi 22120RN86C (API 33, 4 KB arm64) 同样通过 (生成 13413 ms, 输出 "Hello tester! How's the testing going today?") |
| Samsung SM-A566B, API 36 arm64 (第三时段 localhost:30008) | 16384, compat off | 2026-09-12 最终 r28c 产物原生 16 KB arm64 复验: 宿主 5280 + OpenCV v1.1.2 (2/2), Paddle v4 Mobile / Server (含参考图), v5 Thai, v6 Tiny, v3 (1000 x 640 + 参考图), Rapid v6/v3 (r28c donor), Three-Stone-AI 真实 Qwen2.5-1.5B 推理全部通过; 收尾将两个兼容属性重置为默认 |
| AVD_API_36.0_16K (新, emulator-5554) | 16384, compat off, 12 GB RAM | 2026-09-12: 宿主 5280 + OpenCV v1.1.2 (2/2), Rapid v6/v3 (r28c donor), Three-Stone-AI 真实推理通过 (补齐 2.5 GB AVD 的 LOW_MEMORY 缺口) |
| Xiaomi 23046RP50C, API 35 arm64 (重连后) | 4096 | 2026-09-12 最终 r28c 产物 4 KB 回归: 宿主 5280 + OpenCV v1.1.2 (2/2), Rapid v6/v3, Paddle v4 Mobile / Server (含参考图), v5 Thai, v6 Tiny, v3 (1000 x 640 + 参考图) 全部通过; 与 Redmi 一起构成两台 4 KB arm64 真机回归 |

- [x] 原生 16 KB arm64 全部运行时验收 (2026-09-12): 宿主 / OpenCV / Image Quantization / Yolo / Node / Lua / QuickJS 等已过; 6 个 OCR 插件独立调用修复后, r28c donor / v1.0.2 最终产物与 Three-Stone-AI arm64 已在 Samsung 第三时段复验 ([第二轮证据](evidence/page-size-16kb-round2-20260912.md)). 未逐一运行的 Paddle 语言 / flavor 变体与最终 Release 候选按 R1/R5/R6 处理.
- [x] Three-Stone-AI 的真实模型推理验收 (2026-09-12): 在 Samsung 原生 16 KB arm64 (第三时段, 生成 3291 ms), 新 16 KB API 36 AVD (生成 1811 ms), API 37 ps16k 与 Redmi 4 KB arm64 上均以真实 Qwen2.5-1.5B (`faa60663…`) 完成目录整合, 健康检查, 引擎初始化与非空生成 (`LiteRtRealModelInferenceInstrumentationTest`); 2.5 GB 的 API 36 AVD 被 LOW_MEMORY 终止 (非缺陷). Bun 的 JSC 已知问题仍按 B1 单独跟踪.
- [x] Image Quantization 负向设备验证: 在当前测试 APK 中装入保留的真实 NDK r26b / 4 KB JNI, API 37/fatal 的公开量化调用返回 `NATIVE_LIBRARY_PAGE_ALIGNMENT_UNSUPPORTED`, 宿主不崩溃. 该夹具不是完整未修改的历史发布 APK; 验证后已恢复新版插件. (DEVICE-16K: `installedUnalignedPluginRaisesCatchableLoadError`)
- [ ] 宿主与插件后续发布后, 回填远程 CI 链接并重新实测官方索引的发布资产.

各已勾选条目的构建 / ELF / 单元测试证据统一存于
[evidence/page-size-16kb-20260911.md](evidence/page-size-16kb-20260911.md) 与对应 JSON.

### Samsung 补验后的必修项

- [x] X1 (2026-09-12): `c++_shared` 打包 libc++, 独立插件路径在 Samsung 16 KB (两个时段) / ps16k / Redmi 4 KB 复验通过 (r28c donor 最终产物见第三时段); 用户 APK 路径上一轮已通过.
- [x] X2 (2026-09-12): r28.2 libc++ 替换, 根因为旧库 RELRO 末端与可写数据同页 (`tools/16kb/elf_layout.py`); v4 Server / v5 Thai / v6 Tiny 在 Samsung 复验, 其它变体共用同一 libc++ 与 ORT 代码, 在 Redmi 4 KB 验证 v4 Mobile / v5 Server.
- [x] X3 (2026-09-12): 4 KB 对照复现相同崩溃并完成尺寸扫描, 归类为 Paddle-Lite 输入相关缺陷 (B7), 不阻塞 16 KB 验收; 同日确认真正触发条件为进程首次检测小形状, 已在 Paddle v3 v1.0.3 以预热修复 (见附录 B7).
- [x] X4 (2026-09-12): 新增 `tools/16kb/elf_layout.py` (RELRO / 可写数据页布局判定, 已区分旧 libc++ RISK 与 r28.2 OK) 与 `tools/16kb/ocr_provider_smoke.py` (真实 provider 调用回归, 收据 + logcat); 原生依赖完整性由 `c++_shared` 打包与真实调用共同覆盖.

详细失败边界, 默认值, 发布顺序, 手动测试与 backlog 见 [维护者收尾清单](page-size-16kb-maintainer-checklist.md), 具体测试和产物见 [Samsung 补验](evidence/page-size-16kb-samsung-20260911.md).
第 3 节的旧库 / 旧 NDK 数值是部署前快照, 不代表当前产物.

---

## 附录 A: 共享门禁插件实现 (P0.2)

- 输入: 变体的 APK 文件集合 (拆分 + universal); AAR (库模块) 可选; `alsoScan` 的任意 zip / 目录.
- 检查项 (每个 `lib/<abi>/*.so`, `abi in strictAbis`):
  1. ELF 魔数与类别 (ELF32 / ELF64), 程序头表可读;
  2. `min(p_align of PT_LOAD) >= pageSize` (默认 16384);
  3. 条目 `compress_type == STORED` 时 `dataOffset % pageSize == 0`;
  4. 可选 (`checkRelro = true`): 任一 `PT_GNU_RELRO` 满足 `(p_vaddr + p_memsz) % pageSize == 0`;
  5. 可选 (`declaredAlignmentMetaData`): manifest meta-data 声明值 <= 实测值 (P5.1).
- 输出: `build/reports/native-alignment/<variant>.json` (`{apk, extractNativeLibs, entries: [{name, abi, size, compressed, dataOffset, minLoadAlign, ok, reasons[]}], summary}`), 控制台失败摘要按 ABI 分组; 退出即 `GradleException`.
- 实现要点: 纯 Kotlin / JDK 解析, 不依赖 NDK 或 build-tools (CI 可另加 `zipalign -c -P 16 -v 4` 作为第二意见); 只读每个条目前 64 KB (程序头紧随 ELF 头); 大文件 (libnode / libpython) 不解压整体.
- 与 AGP 的耦合: 两种方案, 待决 Q1. (a) 文件扫描: 任务在 `assemble<Variant>` 之后按约定路径 `build/outputs/apk/**` 与变体名匹配, 零 AGP 类型依赖, 与 `autojs6-platform-versions` 的 "反射访问 AGP" 原则一致; (b) `androidComponents.onVariants { artifacts.get(SingleArtifact.APK) }`: 精确, 但需要 `compileOnly` 某版本 AGP 且各消费仓库 AGP 由平台插件动态决定.

## 附录 B: Backlog (不阻塞 P0-P6)

- B1 Bun x86_64 在 16 KB 设备上 JSC 中止 (上游 WebKit `CeilingOnPageSize`): Bun 仓库 2026-09-12 (`3ac84ca`) 以 `USE_64KB_PAGE_BLOCK=1` 与显式 JIT 配置重编 pinned WebKit 并重链九补丁 Bun, 独立 test-only APK 在原生 x86_64 API 36 的 4 KiB / 16 KiB AVD 各两轮 8/8 Binder 通过; 正式 payload 仍为官方 1.4.0, 候选转正与最终 Release 验收由 Bun 仓库 ROADMAP M5 跟踪. arm64 已通过.
- B2 现代打包评估 (D1 的替代路径): 2026-09-12 实测宿主 / inrt 每个 ABI 仅 3 个原生库 (1.3 MB 未压缩, 0.4 MB 压缩, 约 APK 的 1%), 改 `useLegacyPackaging=false` 的收益不足以抵消 APK-Builder-Template 的 STORED + 16 KB 对齐写入改造, 维持 D1; 仅当宿主原生库体量显著增长时重新评估 ([记录](evidence/page-size-16kb-backlog-20260912.md)).
- [x] B3 在 `device` 模块暴露只读 `pageSize` (2026-09-13): 返回当前系统的内存页大小 (字节), 复用 `NativePageSize.current()`, 支持 `$device.pageSize`; `DevicePageSizeInstrumentationTest` 在 4 KB arm64 真机与 16 KB x86_64 模拟器各 1/1 通过. 文档 / 离线插件, d.ts 4.11.0 / Ace 1.1.27 与 LSP 均按 `AGENTS.md` 同步, 版本递增, 多语言日志与生成物已更新 (ANDROID_BUILD + DEVICE-4K + DEVICE-16K; [证据](evidence/page-size-16kb-b3-20260913.md)).
- B4 已完成: 宿主 CI 的无用 CMake 3.10.2 / Ninja 安装步骤已移除, 构建本地通过.
- B5 32 位第三方库 (MLKit / ORT / Chaquopy / NDK libc++) 的对齐, 仅当上游提供时跟进. 2026-09-12 全仓扫描: 64 位 0 项不合规, 32 位 14 项 (armeabi-v7a 的 NDK libc++, ORT JNI, Paddle-Lite, 以及 Ace Editor 自编 `libautojs6_luals.so` 的 armeabi-v7a / x86); 16 KB 设备只运行 64 位代码, 无运行时影响 ([清单](evidence/page-size-16kb-backlog-20260912.md)).
- B6 `AutoJs6-Plugin-APK-Inspector` 增加 "APK 是否 16 KB 就绪" 的检查视图: 2026-09-12 已实现 (v1.1.1, `f9ff8f5`): 只读取 64 位库的 ELF 头 / 程序头表判定 `PT_LOAD` 对齐, `extractNativeLibs="false"` 时核对 ZIP 数据偏移, 结论进入原生库分区 / 宿主文件信息摘要 / 发现分区, 11 项单元测试; `test-fixtures/bundletool-selection` 的 16 KB 夹具未加 (单元测试以合成 ELF 覆盖) ([记录](evidence/page-size-16kb-backlog-20260912.md)).
- B7 Paddle v3 的 Paddle-Lite 预编译库 (`libpaddle_light_api_shared.so`) 崩溃已于 2026-09-12 修复 (v1.0.3, `d48dac1`): 真正的触发条件是进程首次检测输入较矮 (1000x320 -> 256x960, 640x320 等), 先跑过大形状后任何尺寸都正常; JNI 侧在首次检测前用 960x960 空白图预热检测器, Xiaomi Pad / Redmi 4 KB 上原崩溃尺寸全部通过, 原生 16 KB arm64 复验待下一个 Samsung 时段 ([记录](evidence/page-size-16kb-backlog-20260912.md); 旧的尺寸扫描见 [第二轮证据](evidence/page-size-16kb-round2-20260912.md)).

## 附录 C: 本次采用的执行默认值

Q1-Q4 没有收到新的明确回答. 本次根据部署授权采用下列推荐方案继续实现, 并将具体结果留供维护者复核, 不将其记作已确认的 D 决策.

### Q1 (P0.2): 门禁插件的命名与 APK 定位方式

已采用: 插件 id `io.github.supermonster003.autojs6-native-alignment`, 任务 `verifyNativePageAlignment`, 扩展 `nativeAlignment {}`; APK 定位采用附录 A 的方案 (a) 文件扫描 (零 AGP 类型依赖, 与平台插件既有原则一致), 变体名从任务名 `assemble<Variant>` 推导, 并以 `output-metadata.json` 验证全部重命名 / split APK 存在; 若某仓库改了 `outputFileName`, 用 `nativeAlignment.apkFiles(...)` 显式指定.

### Q2 (P5.2): 索引字段的名称与语义

已采用: release 级 `nativePageAlignment` (整数字节: `16384` / `4096`; 无原生库为 `0`), 条目级同名字段取最新 release 的值; 另加 `nativePageAlignmentSource` (`measured` / `declared`) 便于区分实测与声明. 不引入布尔 `supports16Kb`, 避免日后页大小再变时字段失效.

### Q3 (P1.5): APK 打包器遇到 4 KB 插件库时是警告还是阻断

已采用: 警告 (进度与结果中列出具体库与对齐值), 不阻断. 用户生成的 APK 可能面向 4 KB 设备; 阻断会让 P2 完成前的所有含 OCR / 图像库的打包失败. P2 完成后可评估升级为 "16 KB 设备上默认阻断, 设置项可放宽".

### Q4 (P3.9): Ace-Editor 的 LuaLS 二进制是否立即重编

已采用: 不立即重编 (64 位实物已 `0x4000`), 只补构建脚本的显式标志与 lock 的 `minLoadAlign` 断言, 待下次 LuaLS 升级时自然生效; 若维护者希望 armeabi-v7a 也对齐 (D8 不要求), 则随下次升级一并重编.

## 附录 D: 证据等级与验收方式

| 标签 | 可以证明 | 不能证明 |
| --- | --- | --- |
| `SOURCE` | 源码 / 脚本 / 配置存在, 结构符合设计 | 编译或行为正确 |
| `JVM` | Android-free 逻辑的单元测试 (JUnit4, 无 Robolectric) | 真机行为 |
| `ANDROID_BUILD` | `assemble*` 通过且门禁任务通过 (或 CI 绿) | 真机行为 |
| `ELF` | 对具体 APK / `.so` 的静态扫描结果 (`scan_alignment.py`, `zipalign -c -P 16`, `llvm-readelf`) | 运行时对页大小的假设 |
| `DEVICE` | 指定 4 KB 设备与 API 级别上的冒烟通过 | 16 KB 行为 |
| `DEVICE-16K` | ps16k 模拟器或原生 16 KB 真机 (注明 `getconf PAGE_SIZE`, 兼容模式状态) 上的冒烟通过 | 未列出设备 / ABI |
| `DOCS` | changelog / README / provenance / 本文已更新且版本号已递增 | - |

条目勾选时在其后追加证据, 格式示例: `[x] ... (ELF: 4/4 ABI 0x4000; DEVICE-16K: aj6-ps16k-36-x86_64, PAGE_SIZE=16384, compat off; commit abc1234)`.

## 附录 E: 命令与参考

- 官方说明: https://developer.android.com/guide/practices/page-sizes (Play 要求, backcompat 模式, AGP / NDK 版本, 检查与测试方法).
- 静态检查:
  - `python tools/16kb/scan_alignment.py` (全仓库巡检; `AUTOJS6_PROJECTS_ROOT` / `ANDROID_HOME` / `MAX_APKS` 可覆盖).
  - `%ANDROID_HOME%/build-tools/35.0.0/zipalign -c -P 16 -v 4 app.apk` (最后一行 `Verification successful`).
  - `%ANDROID_HOME%/ndk/28.2.13676358/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-readelf -lW lib.so | findstr LOAD` (`Align` 列应为 `0x4000`).
- 模拟器: `avdmanager create avd -n aj6-ps16k-36-x86_64 -k "system-images;android-36;google_apis_ps16k;x86_64"`; `aj6-ps16k-37-x86_64` 同理 (`android-37.0`); `adb shell getconf PAGE_SIZE` 应为 `16384`.
- 兼容模式诊断 (仅验证用, D10):
  - 关闭: `adb shell setprop bionic.linker.16kb.app_compat.enabled false && adb shell setprop pm.16kb.app_compat.disabled true`
  - Android 17 fatal: `adb shell setprop bionic.linker.16kb.app_compat.enabled fatal && adb shell setprop pm.16kb.app_compat.disabled true`
  - 恢复默认: 两个属性分别设回 `true` / `false`.
  - 单应用: 应用信息 -> Advanced -> "Run app with page size compat mode" (仅 16 KB 设备可见).
- NDK 链接标志 (CMake): `target_link_options(<target> PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384")`; ndk-build: `LOCAL_LDFLAGS += -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384`.

---

## 会话记录

### 2026-09-11 初稿

- 完成: 宿主与 43 个同目录仓库的 APK / ELF 基线扫描 (脚本 `tools/16kb/scan_alignment.py`); 三组只读审计 (运行时插件, 视觉 / OCR 插件, 杂项 + APK-Builder-Template 打包路径 + 纯 JVM 插件); 官方文档事实核对; 固定决策 D1-D8 (维护者以两轮选择题拍板, 全部采用推荐项) 与派生约定 D9-D12; 本 Roadmap 初稿.
- 关键发现: 宿主自身 3 个 `.so` 全为 4 KB (libc++ 与 jackpal), 且 jackpal 会随打包器进入每个用户 APK; 宿主把 OpenCV / Image-Quantization 的 `.so` 加载进自身进程, 后者当前 4 KB; Rapid v3/v6 / Paddle v3 / ImGui / Image-Quantization 为 64 位不合规; OpenCV / MediaInfo / OpenCC / QuickJS / Lua / Node / Bun 已有各自门禁并合规; 32 位库普遍 4 KB 但不在验收范围 (D8); APK-Builder-Template 以 DEFLATED 写入且不改 `extractNativeLibs`, 因此 D1 下用户 APK 只需 ELF 对齐.
- 未做: P0.2 门禁插件, P0.3 的 `verify_device.sh` 与 AVD 创建, 以及 P1 起全部代码改动.
- 下次会话建议起点: P0.2 (共享门禁插件, 拍板 Q1) 与 P1.1 / P1.2 (宿主 libc++ 与 jackpal) 可并行; P1.3 先挂门禁观察其对当前 4 KB 库报错.

### 2026-09-11 部署

- 共享门禁 `io.github.supermonster003.autojs6-native-alignment:1.8.0` 已发布至 Plugin Portal / Maven Central. 提交 `8d32a51`, CI SDK 路径修正 `8863240`; 发布工作流与负向 Android 样例 CI 均通过, 无 composite 的独立消费工程验证通过.
- 宿主及 42 个插件接入门禁; 23 个原生插件, 18 个纯 JVM 插件, 1 个携带 Runtime Kit 的打包模板插件. APK Builder 声明 16384 并递归扫描 assets 内模板; Python 递归扫描 Chaquopy ZIP / IMY 扩展模块. 不设置 pageSizeCompat.
- 宿主替换 NDK r28.2 libc++, 重编 jackpal 并保留 Java / 资源与 JNI 导出兼容. 新增加载前保护, 打包警告 / sidecar, Runtime Kit 对齐清单, 插件中心兼容性标记及安装 / 启用提醒.
- Paddle v3 的旧 Paddle-Lite `.dynsym sh_info` 不符合新 LLD 的要求; 已仅修正节头索引, PT_LOAD 内容保持逐字节不变, 原始与修正后的 SHA 及脚本入库. 四个 Paddle 仓库的预编译 OpenCV AAR 内未打包的旧 x86_64 库也已替换, 避免以后扩 ABI 时回归.
- Rapid v3/v6 使用 Maven ORT 1.21.1, NDK r28.2, OpenCV 4.8.0 共享 TBB 运行库. 实机发现 Paddle 后加载 Rapid 会因两份静态 OpenMP 中止; 已移除 Rapid 的静态 OpenMP, 并通过两引擎同进程识别. 新 Rapid 要求宿主 build >= 5279, 打包器可携带可选 OpenCV 依赖, 旧 Rapid APK 仍可打包.
- OpenCV 已验证的 r26b 预编译运行库按 P2.2 保留其真实 provenance, 本次没有伪造 NDK 来源或重编它; r28.2 下限应用于本次重编的库. Ace 的已对齐 LuaLS 二进制按 Q4 默认值保留, 显式标志在下次重编时生效.
- 所有 42 个插件本地构建和统一门禁通过, 全量扫描 64 位不合规数为 0. 5 种 ABI 的 Runtime Kit 均通过 ELF 与 SDK zipalign 复核. 多语言 changelog / README 已生成, 各仓库本次变更独立提交; 会话开始前已有变更保留在工作区.
- 官方索引按真实公开 APK 测量, 不用本地未发布版本覆盖旧发布版本的 4096 / unknown 值. 真实 16 KB arm64 设备缺失, P6 未关闭; QuickJS-Host 仍按 D11 由维护者 rebase.

- 补验: ImGui 渲染, QuickJS 语言执行, Node 宿主执行, MediaInfo 读取, TypeScript 编译及 Lua 会话在 API 37/fatal 下通过. 真实旧版 4 KB Image Quantization JNI 夹具被宿主结构化拦截. 完整收据和 42 个插件提交映射见 [部署证据](evidence/page-size-16kb-20260911.md).

- 补充矩阵: API 36/37 均通过 OpenCC 全转换, Ace LuaLS 语义与故障恢复, Archive Manager TAR/Zstd, MLKit 条码/OCR; API 36 另补齐 Node / Lua / MediaInfo / TypeScript 与 ImGui / QuickJS. Three-Stone-AI 的模型推理尚无当前模型输入, 不把 APK 安装记作推理验收. 宿主实现提交 `7a1f91481`, 官方索引 `df2eb35`, 插件提交表见证据文档.

- 收尾: 宿主 / 42 插件 / 索引 / 共享插件共 45 个仓库已复核. 原有 30 个插件脏工作区与 R8 的 AD 暂存状态保留. 自动记忆索引已更新. 最终 Runtime Kit 生成的用户 APK 在 API 36/37 与 4 KB arm64 上均复验通过; P6 因上述设备 / 模型 / 发布后验收项保持开放.

### 2026-09-11 合并收尾, 全量剩余事项与 Samsung 补验

- 维护者已将 QuickJS-Host 关联分支合并到宿主 master, 解决冲突并移除工作树 / 目录; 合并提交 `d17d12578`, D11 / P1.7 已关闭. 合并提交的验证说明与本轮所测 APK SHA 分别记录, 不混为同一来源证明.
- 已将全部发布阻塞, 其它收尾事项, 设备 / 模型 / 人工验收, Q1-Q4 默认值, D3 预编译库边界, 30 个既有未提交工作区与 B1-B6 后续项集中到 [维护者收尾清单](page-size-16kb-maintainer-checklist.md).
- 用户提供 `localhost:30078`, 实测 Samsung SM-A566B / API 36 / arm64-v8a / PAGE_SIZE 16384, 验收时关闭 linker / PM 兼容模式并读回. 宿主及 16 个原生插件的 30 项 instrumentation 全过, 无跳过; Yolo 得到 5 个参考检测结果, Ace LuaLS 为真实 semantic 路径 / 17 个补全 / 预期恢复.
- 6 个独立 OCR 插件在新增图片用例失败. Rapid v3/v6 明确缺少 OpenCV 所需的 libc++; Paddle v4 Server / v5 Thai / v6 Tiny 在旧 libc++ 初始化时 SIGSEGV; Paddle v3 的 1000 x 320 生成图两次崩溃, 944 x 640 参考图在独立插件中通过. 已增加 X1-X4, 未把静态门禁通过当作完整运行时支持, 未将未定位的问题直接归因于页大小.
- 4 种真实用户 APK 组合全部通过: Paddle v3 + Rapid v3/v6, 各自选择 / 不选择独立 OpenCV. 终端退出 37, 量化输出有效, 两种 OCR 均识别 "测试". 最初因额外 Paddle flavor 干扰的夹具失败独立保留, 隔离后重验. 最终 Release / 合并后新 Runtime Kit 与公开资产仍需按 R1-R8 验收.
- 所有安装输入与设备 base.apk SHA 一致; 25 个应用 APK 的直接 ELF / zipalign 检查通过. Python / Builder 的嵌套扫描引用相同产物 SHA 的原门禁. Three-Stone-AI 尚缺 `.litertlm` 模型输入, P6 未关闭.
- 两个兼容模式属性, 宿主临时存储 AppOp 与三个 Paddle flavor 状态均恢复原值; 测试 APK 保留. 本轮只提交文档 / 收据, 没有 Gradle 构建或产品源码改动. 详细证据见 [Samsung 补验](evidence/page-size-16kb-samsung-20260911.md) 及 JSON.

### 2026-09-12 第二轮: X1-X3 修复, r28c 统一重编与真机复验

- 用户提供 Samsung 第二时段 `localhost:54059` (2 小时). `tools/16kb/elf_layout.py` 证明 X2 根因 (旧 libc++ RELRO 末端 `0x111000` 与可写数据同页, 16 KB 取整后被 mprotect 只读); 以 r28.2 libc++ 替换 Paddle v4/v5/v6 后 Samsung 复验 v6 Tiny / v5 Thai 通过, v4 Server 不再崩溃; Rapid 以 `c++_shared` 打包 libc++ 后 v3/v6 在 Samsung 通过 (X1).
- Paddle v3 的 1000 x 320 崩溃在 Xiaomi 4 KB 复现并完成尺寸扫描, 归类为 Paddle-Lite 输入缺陷 (B7); v4 Server 的后续失败为 90 MB 模型读入 Java 堆的 OOM, ORT 会话改为内存映射.
- OpenCV 以 NDK r28c 重编 (v1.1.2, 最低宿主 5280), donor 同步至 Rapid v3/v6 与 Paddle v3-v6, 宿主策略接受 r26b / r28c; 最终产物 (宿主 5280 + OpenCV v1.1.2, Rapid, Paddle v3-v6 各变体) 在两台 ps16k 模拟器与 Redmi 4 KB arm64 通过; Samsung 时段在此之前到期, 原生 16 KB arm64 的最终复验与兼容属性恢复留待新时段. Xiaomi Pad 的 ADB 传输在扫描后卡死.
- Three-Stone-AI 新增真实模型 instrumentation, API 37 ps16k 上 Qwen2.5-1.5B 生成 "Hello tester! How's everything going?"; API 36 AVD (2.5 GB) 被 LOW_MEMORY 终止; Redmi 22120RN86C (API 33, 4 KB arm64) 同样通过 (生成 13413 ms, 输出 "Hello tester! How's the testing going today?"). 插件中心日期此前已为 ISO 8601, 本轮补 ISO 日期时间解析.
- 版本: 宿主 6.8.0 build 5280, OpenCV 1.1.2, Rapid v3/v6 1.0.2, Paddle v3-v6 1.0.2; 各仓库只做本地提交 (仅本轮文件), 未推送. 证据见 [第二轮](evidence/page-size-16kb-round2-20260912.md) 及 JSON.

### 2026-09-12 Samsung 第三时段: 最终产物原生 16 KB arm64 复验

- 用户重新上线 Samsung SM-A566B 第三时段 (`localhost:30008`, 约 85 分钟), 重连 Xiaomi Pad, 并提供新的 16 KB API 36 AVD (`emulator-5554`, 12 GB 内存).
- 该 RDB 链路的 `adb shell` 间歇卡死, 且并发 adb 调用 (含监控探针) 会拖垮传输并使自动化链中途死亡; 改为每次一个操作串行执行后稳定. `adb install` / `adb push` 传输本身正常, 200 MB server APK 与 1.6 GB 模型分别耗时约 2.5 / 20 分钟.
- 最终 r28c 产物在原生 16 KB arm64 全部通过: 宿主 5280 + OpenCV v1.1.2 宿主测试 2/2, Paddle v4 Mobile (修复后 libc++ 的首次 16 KB 运行) / v4 Server (内存映射 ORT, 不再 OOM, 含参考图) / v5 Thai / v6 Tiny / v3 (1000 x 640 + 参考图), Rapid v6/v3 (r28c donor), Three-Stone-AI 真实 Qwen2.5-1.5B 推理 (生成 3291 ms). 新 AVD 补齐了 2.5 GB AVD 的 Three-Stone LOW_MEMORY 缺口. 详见 [第二轮证据](evidence/page-size-16kb-round2-20260912.md).
- 收尾将 `bionic.linker.16kb.app_compat.enabled` 与 `pm.16kb.app_compat.disabled` 重置为默认 (捕获原值的链在 RDB 掉线时已死, RDB 设备归还时亦会重置); 测试 APK 保留. 剩余仅发布类事项 R1 / R2 / R5-R8 与未重建的旧 flavor.
- Xiaomi Pad 重连后 USB 传输恢复, 同一批最终产物在其上 (API 35, 4 KB arm64) 全部通过 (`pad4k-*` 收据, 见第二轮证据), 与 Redmi 一起构成两台 4 KB arm64 真机回归. 未重建的 14:11 旧 flavor: Paddle v4 mobileEn, v5 mobile / english / korean / latin / eslav / greek / arabic / cyrillic / devanagari / telugu / tamil, v6 small / medium; 发布前须从已提交源码重建全部 flavor (R1 / R5).

### 2026-09-12 附录 B 收尾与 Release 候选审计

- 维护者提交全部插件工作区并对宿主与各插件执行 `./gradlew clean assembleRelease`; 独立审计 89 个 Release APK (宿主 app / inrt, OpenCV, Rapid v3/v6, Paddle v3-v6 全部 flavor, Three-Stone-AI, Bun): 版本号, 64 位 ELF 对齐, libc++ RELRO 布局与门禁报告 0 项问题, 每个 64 位 ABI 只剩一个 r28.2 libc++ 二进制 ([审计](evidence/page-size-16kb-release-candidates-20260912.md)). 宿主暂不发布到 GitHub, R5 / R6 相关项暂缓.
- B7 修复并复验 (Paddle v3 v1.0.3), B6 实现 (APK Inspector v1.1.1), B5 / B2 实测记录, B1 记录 Bun 仓库的大页 JSC 候选状态; B3 在本轮尚未启动. 详见 [附录 B 记录](evidence/page-size-16kb-backlog-20260912.md).

### 2026-09-13: B3 公开 API

- 维护者单独启动 B3, 完成 `device.pageSize` / `$device.pageSize` 只读数值属性, 文档与离线内容同步, d.ts 与 Ace LSP 声明同步及版本递增. 4 KB arm64 真机 / 16 KB x86_64 模拟器上的生产 Rhino 属性契约测试均通过; B3 已勾选, 证据见 [验证记录](evidence/page-size-16kb-b3-20260913.md).
