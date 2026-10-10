# OpenCV / OCR 解耦 P0 基线证据 (2026-10-10)

对应 [OpenCV 运行时与 OCR 插件解耦 Roadmap](../opencv-ocr-runtime-decoupling-roadmap.md) 的 P0.2 与 P0.3. 本记录描述改动前的打包行为, 作为 P1 / P2 的对照.

## 环境

- 设备: Xiaomi 23046RP50C (968e9f18), Android 15 / API 35, arm64-v8a, 页大小 4096.
- 宿主: master `9526a9ec25` 的 debug 构建 (6.8.0, 5328), 主代码未改动, 仅新增测试与工具.
- APK Builder 模板: 由同一宿主 `:app:generateRuntimeKit` 生成的 universal Runtime Kit 构建的 debug 插件 (1.0.4+autojs6-6.8.0, 528202). 设备原有模板只支持宿主 5279, 无法用于本轮.
- 插件: OpenCV 1.1.4 (23), Paddle OCR v3 1.0.5 (48), v4 mobile 1.0.4 (23), v5 1.0.4 (74), v6 small 1.0.4 (27), Rapid OCR PP-OCRv6 1.0.5 (32).
- 工具: `app/src/androidTest/.../OpenCvOcrPackagingDeviceTest.kt` 只启用指定 OCR 插件后打包, 记录构建结果; `tools/opencv-ocr/verify_packaged_ocr.py` 列出产物原生库, 按字节比对来源, 安装运行并收集结果. 回执位于本地 `build/tmp-opencv-ocr/baseline/<场景>/receipt.json`.

## P0.2: Paddle OCR v4 / v5 / v6 在打包 APK 中不可用

只勾选 "Paddle OCR", 分别只启用一个插件:

| 场景 | 打包结果 | 复制的原生库 (除终端库与 libc++) | 复制的资产 | ocr (paddle) | images (OpenCV) |
| --- | --- | --- | --- | --- | --- |
| v5 | 成功 | libonnxruntime.so, libonnxruntime4j_jni.so, libopencv_java4.so | 4 个 `assets/models/ppocrv5-*` | 失败: 未找到内置 Paddle OCR 资源 | 成功, 使用 v5 带入的 libopencv_java4.so |
| v6 small | 成功 | 同上 | 4 个 `assets/models/ppocrv6-small/*` | 失败, 同上 | 成功, 同上 |
| v4 mobile | 成功 | 同上 | 3 个 `assets/models/ppocrv4-*` | 失败, 同上 | 成功, 同上 |
| v3 与 v6 small 同时启用 | 成功, 选中 v6 | 同上 | v6 的 4 个模型 | 失败, 同上 | 成功 |

结论:

- C9 成立. INRT 的 ocr 只认 Paddle Lite `.nb` 模型, v4-v6 的 ONNX 模型, ONNX Runtime 与 OpenCV 被复制但不会运行.
- 现存缺陷: `ApkBuildLibraryCatalog.selectBestPluginVariantCandidate` 取变体号最大的插件, v3 与任一 v4-v6 同时启用时选中后者, 打包 APK 的 Paddle OCR 因此不可用. D10 (只接受 v3) 一并修复.
- C8 成立. 未勾选 "OpenCV" 时, images 加载了 OCR 插件带入的同名库, 没有核对来源.

## P0.3: 同时勾选 OpenCV 与 OCR 的产物

启用 Paddle OCR v3 与 Rapid OCR PP-OCRv6, 产物 `lib/arm64-v8a/`:

| 条目 | 字节相同的来源 | 备注 |
| --- | --- | --- |
| libNative.so | Paddle OCR v3 | 不 NEEDED libopencv_java4.so; 动态符号表导出 1275 个 `cv::` 符号 (1.0.5) |
| libRapidOcr.so | Rapid OCR v6 | NEEDED libopencv_java4.so |
| libopencv_java4.so | OpenCV, Paddle OCR v3, Rapid OCR v6 | 三方逐字节相同, 打包器只写入一份 |
| libc++_shared.so | 宿主, Paddle OCR v3, Rapid OCR v6 | 三方相同 (NDK r28c) |
| libpaddle_light_api_shared.so, libonnxruntime.so | 各自插件 | |
| libjackpal-*.so | 宿主 | 终端默认库 |

运行结果 (勾选与不勾选 "OpenCV" 两种产物相同): paddle, rapid 与 auto 模式均识别出 "测试"; `images.findImage` / `images.matchTemplate` 成功; 交替调用 3 轮与三线程各 5 次并发调用全部成功; 进程线程数从 53 增至 81-82.

这份 "正常" 依赖于三方副本逐字节相同. 不勾选 "OpenCV" 的产物中, images 同样能工作, 用的是 OCR 插件带入的副本 (C8).
