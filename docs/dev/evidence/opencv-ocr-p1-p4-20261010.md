# OpenCV / OCR 解耦 P1-P4 证据 (2026-10-10)

对应 [OpenCV 运行时与 OCR 插件解耦 Roadmap](../opencv-ocr-runtime-decoupling-roadmap.md) 的 P1-P4. 改动前的对照见 [P0 基线](opencv-ocr-p0-baseline-20261010.md).

## 提交

| 仓库 | 提交 | 内容 |
| --- | --- | --- |
| 宿主 | `7d8f853a3b` | P1.1-P1.6: 按内容解析同名原生库, libc++ 选择与符号校验, D12 按 DT_NEEDED 带入旧版 OCR 的 OpenCV, D10 只接受 Paddle v3, 打包 OpenCV 来源标记与 INRT 校验 |
| 宿主 | `4528568bc5` | P2.1: 内置 Paddle 引擎不再加载 OpenCV 运行时, paddle-ocr-engine 去掉 OpenCVLoader 与 OpenCV 编译依赖 |
| 宿主 | `6733cbf4be` | P3.6 / P4.1: 退役两个 OpenCV 分发脚本, 更新集成说明与 16 KB 清单 |
| Rapid OCR PP-OCRv6 | `97ca8c3`, `608f5cc` | P3.1 / P3.4 / P3.5: 私有静态 OpenCV, 来源清单, 1.0.6 日志; JNI 引用测试改链静态库 |
| Rapid OCR PP-OCRv3 | `756f0a8` | 同上, 并移植 PP-OCRv6 的 JNI 异常边界 |
| Paddle OCR PP-OCRv3 | `4efab64` | P3.2 / P3.4 / P3.5: 隐藏 OpenCV 符号, 移除 OpenCV AAR, 官方 SDK 来源清单, JNI 异常边界, 1.0.6 |

插件仓库在当天另行重写过本地历史 (去除 AGENTS.md / CLAUDE.md), 上表为重写后的哈希.

## 静态验收 (llvm-readelf, NDK r28c)

| 库 | ABI | NEEDED 含 libopencv_java4.so | 导出 cv:: | 未定义 cv:: | 动态导出 | OpenMP 引用 | PT_LOAD 对齐 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rapid v6 / v3 `libRapidOcr.so` | arm64-v8a, armeabi-v7a, x86, x86_64 | 否 | 0 | 0 | `JNI_OnLoad`, `JNI_OnUnload`, 3 个 `Java_*` | 0 | 16 KB |
| Paddle v3 `libNative.so` | arm64-v8a, armeabi-v7a | 否 | 0 (此前 1275) | 0 | 3 个 `Java_*` | 0 | 16 KB |

三个插件的 APK 均不再携带 `libopencv_java4.so`. Rapid v6 debug 包体积: universal 121.9 MB -> 72.0 MB, arm64-v8a 48.2 MB -> 40.4 MB, x86_64 64.7 MB -> 42.6 MB.

JNI 引用测试 (`tools/jni/run_jni_tests.py`, 链接同一组 OpenCV 静态库): Rapid v6 在 x86_64 16 KB 模拟器 (API 37), Rapid v3 与 Paddle v3 在 Xiaomi 23046RP50C (API 35) 上均 PASS.

## 打包验证 (P1 / P2)

设备: Xiaomi 23046RP50C (968e9f18), API 35, arm64-v8a, 4 KB. 宿主与 APK Builder 模板均由本轮宿主构建 (6.8.0, 5328). 插件: OpenCV 1.1.4, Paddle OCR v3 (本轮), Rapid OCR v6 (本轮).

| 场景 | 打包结果 | `libopencv_java4.so` | 来源标记 | 运行 |
| --- | --- | --- | --- | --- |
| A: OpenCV + Paddle + Rapid | 成功 | 仅 OpenCV 插件 | 有 | paddle / rapid / auto 均识别 "测试"; images 成功 (4.8.0); 交替 3 轮与三线程各 5 次并发全部成功 |
| B: Paddle + Rapid, 不勾选 OpenCV | 成功 | 无 | 无 | OCR 全部成功; images 报 "OpenCV was not included in this packaged application" |
| C: 只启用 Paddle v6 small | 失败 | - | - | 报 PP-OCRv3 专用提示 (`error_apk_paddle_ocr_requires_pp_ocrv3_plugin`) |
| D: Paddle v3 与 v6 同时启用 | 成功, 选中 v3 | 无 | 无 | 只复制 v3 的原生库与 `.nb` 模型 |

单元测试: `PackagedNativeLibraryPolicyTest` (9), `ElfDynamicInfoTest` (4, 以仓库内 NDK r28c 的 libc++ 为真实样本), `BundledOpenCvRuntimeTest` (5), `ApkBuilderLibPolicyTest` (8), `NativeLibraryLoadGuardTest` (3).

## 版本差异共存 (P4.2)

Rapid OCR v6 测试包把私有 OpenCV 换成官方 OpenCV 4.12.0 Android SDK 静态库 (仅为验证构建, 未提交; SDK 的 SHA-256 与 GitHub 发布摘要一致; 4.12.0 的静态库由 NDK r27 / clang 18.0.3 编译, arm64 闭包含 KleidiCV). `libRapidOcr.so` 中可检出 `OpenCV 4.12.0`, 导出仍只有 5 个 JNI 符号.

| 设备 | 产物 | images (OpenCV 插件 4.8.0) | Rapid (私有 4.12.0) | Paddle v3 (私有 4.8.0) | 交替 / 并发 | 进程线程数 |
| --- | --- | --- | --- | --- | --- | --- |
| Xiaomi API 35, arm64, 4 KB | OpenCV + Paddle + Rapid | (232, 0), 与只勾选 OpenCV 的对照一致 | "测试" | "测试" | 全部成功 | 52 -> 80, 与 P0 基线 (同版本) 一致 |
| AVD_API_37.1_16K, x86_64, 16 KB | OpenCV + Rapid | (0, 0), 与只勾选 OpenCV 的对照一致 | "测试" | 不适用 (ARM only) | 全部成功 | 49 -> 63; 只勾选 OpenCV 时 44 -> 49 |

两种设备上 `/proc/self/maps` 同时映射 `libopencv_java4.so` 与 `libRapidOcr.so`, 进程无崩溃.

## 宿主应用回归 (P4.3)

Xiaomi API 35 上的宿主进程通过 `RunIntentActivity` 运行脚本: images 使用 OpenCV 插件 4.8.0, ocr 经 Binder 调用 Rapid OCR v6 (私有 4.12.0) 与 Paddle OCR v3. 单独, 交替 3 轮与三线程各 5 次并发全部成功. 宿主进程只映射 `libopencv_java4.so`; Rapid 插件进程只映射 `libRapidOcr.so` 与 `libonnxruntime.so`. 测试临时启用的两个插件已恢复为测试前的停用状态.

## 旧版兼容 (P4.4)

设备: Redmi 22120RN86C (bek749scrwv4wo8h), API 33, arm64-v8a. 插件为已发布的旧版: Paddle OCR v3 1.0.5, Rapid OCR v6 1.0.5 (共享 OpenCV), OpenCV 1.1.4.

| 场景 | `libopencv_java4.so` | 来源标记 | 运行 |
| --- | --- | --- | --- |
| OpenCV + Paddle + Rapid | 三方字节相同, 写入一份 (D9) | 有 | 全部成功 |
| Paddle + Rapid, 不勾选 OpenCV | 按旧版 `libRapidOcr.so` 的 DT_NEEDED 带入 (D12) | 无 | OCR 全部成功; images 拒绝加载 (C8 已消除) |

旧版 Paddle v3 1.0.5 的 `libNative.so` 在新宿主引擎下无需预先加载 OpenCV 即可工作, P2.1 的前提成立. 内容不同的同名库冲突在实际插件中无法构造, 由单元测试覆盖.

## 未覆盖

- 16 KB arm64 实机: Paddle v3 仅支持 ARM, 本轮没有 16 KB ARM 设备.
- Paddle OCR v4-v6 只在宿主应用中经 Binder 使用 (D6, D10), 本轮未改其运行时.
