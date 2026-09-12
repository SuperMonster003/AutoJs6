# 16 KB Roadmap 附录 B (Backlog) 收尾记录 (2026-09-12)

维护者决定宿主暂不发布到远程 GitHub, 因此清单 R5 (远程 CI) / R6 (官方索引刷新) 中与宿主发布相关的项暂缓; 本文记录附录 B 中可自动化完成的事项. Release 候选产物审计见 [release-candidates](page-size-16kb-release-candidates-20260912.md); 第二轮运行时证据见 [round2](page-size-16kb-round2-20260912.md).

## B7: Paddle v3 首次检测崩溃 (已修复, v1.0.3)

根因: The bundled Paddle-Lite crashes (SIGSEGV / SEGV_ACCERR in a conv worker thread, pc 0xc7a44 / 0xc7a40 / 0xc7930 / 0x11f2cc in libpaddle_light_api_shared.so) when the FIRST detector run of a process has a short input after DetResizeImg (1000x320 -> 256x960, 640x320 -> 320x640, 1000x300 -> 288x960 ...). Once a large shape (1000x640 -> 576x960, reference 608x928, or 960x960) has run first, every later shape passes. The earlier size sweep looked size-dependent only because crashed processes restarted and the passing sizes ran in a warmed process.

修复: OCR_PPredictor::warm_up_det() runs infer_det once on a 960x960 white cv::Mat before the first real detection (libs/paddleocr/src/main/cpp/ocr_ppredictor.{h,cpp}); cost ~120 ms on Xiaomi Pad, ~450 ms on Redmi, once per process. 提交 `d48dac1`.

| 收据 | 设备 | 页大小 | 结果 | 说明 |
| --- | --- | --- | --- | --- |
| `b7-pad4k-paddle-v3-1000x320` | `968e9f18` | 4096 | 1000x320 ok | 旧 v1.0.1 APK, 进程已被 07:41 的 1000x640 / 参考图预热 |
| `b7-pad4k-paddle-v3-crash-set` | `968e9f18` | 4096 | 960x320 FAIL, 992x320 FAIL, 1024x320 FAIL, 800x320 FAIL, 1000x352 FAIL, 1000x300 FAIL | 旧 APK, 每个尺寸都是新进程的首次调用 (崩溃后进程重启) |
| `b7-t1-large-first` | `968e9f18` | 4096 | 1000x640 ok, 1000x320 ok, 960x320 ok, 800x320 ok, 1000x352 ok, 1000x300 ok | 旧 APK, force-stop 后先 1000x640 再各崩溃尺寸 |
| `b7-t2-small-first` | `968e9f18` | 4096 | 1000x320 FAIL | 旧 APK, force-stop 后首次即 1000x320 |
| `b7-t3-mid-first` | `968e9f18` | 4096 | 640x320 FAIL, 960x320 FAIL | 旧 APK, force-stop 后首次即 640x320 |
| `b7-t4-small-first-fulllog` | `968e9f18` | 4096 | 1000x320 FAIL | 旧 APK, 同 T2 并抓取完整 logcat (det shape 256,960 -> infer Run start -> SIGSEGV) |
| `b7-t5-960sq-first` | `968e9f18` | 4096 | 960x960 ok, 1000x320 ok, 640x320 ok | 旧 APK, force-stop 后首次 960x960 再 1000x320 / 640x320 |
| `b7fix-968e9f18-small-first` | `968e9f18` | 4096 | 1000x320 ok | v1.0.3 (预热修复), Xiaomi Pad, force-stop 后首次 1000x320 |
| `b7fix-968e9f18-crash-set` | `968e9f18` | 4096 | 640x320 ok, 960x320 ok, 992x320 ok, 1024x320 ok, 800x320 ok, 1000x352 ok, 1000x300 ok, 1000x640 ok | v1.0.3, Xiaomi Pad, 原崩溃尺寸集 + 1000x640 |
| `b7fix-968e9f18-reference` | `968e9f18` | 4096 | 840x572 ok | v1.0.3, Xiaomi Pad, 参考图 |
| `b7fix-bek749scrwv4wo8h-small-first` | `bek749scrwv4wo8h` | 4096 | 1000x320 ok | v1.0.3, Redmi, force-stop 后首次 1000x320 |
| `b7fix-bek749scrwv4wo8h-crash-set` | `bek749scrwv4wo8h` | 4096 | 640x320 ok, 960x320 ok, 992x320 ok, 1024x320 ok, 800x320 ok, 1000x352 ok, 1000x300 ok, 1000x640 ok | v1.0.3, Redmi, 原崩溃尺寸集 + 1000x640 |
| `b7fix-bek749scrwv4wo8h-reference` | `bek749scrwv4wo8h` | 4096 | 672x456 ok | v1.0.3, Redmi, 参考图 |

旧 APK 的 T1 (先大后小全通过) 与 T2 / T3 (首次即小尺寸崩溃) 直接证明首次形状依赖; T4 的完整 logcat 显示崩溃发生在 `ocr cpp det shape 256,960` 之后的 `infer Run start`, 即检测器推理阶段. 修复版在 Xiaomi Pad (API 35) 与 Redmi (API 33) 两台 4 KB arm64 上, force-stop 后首次 1000x320, 原崩溃尺寸集 (640x320, 800-1024x320, 1000x352), 1000x640 与参考图全部通过. 原生 16 KB arm64 上的复验需要下一个 Samsung 时段 (崩溃机制与页大小无关, 4 KB / 16 KB 表现一致).

## B6: APK Inspector 16 KB 就绪检查 (已实现, v1.1.1)

PageSizeReadinessInspector reads only the ELF header + program-header table (<= 64 KiB per entry, streamed) of every arm64-v8a / x86_64 / riscv64 library in a directly inspected APK or AAB, verifies PT_LOAD alignment >= 16 KB and, when the manifest declares extractNativeLibs="false", the ZIP data offsets of stored libraries via the package's own central directory. Verdicts READY / NOT_READY / UNVERIFIED / NO_64BIT_LIBRARIES / NOT_EVALUATED (nested container APKs) are rendered in the native-library section, the host file-information summary and (NOT_READY) the findings section; 17 strings x 11 locales; PageSizeReadinessInspectorTest (11 tests) plus the existing archive / partition suites pass; ManifestSummary gained extractNativeLibs.
提交 `f9ff8f5`.

## B5: 32 位第三方库对齐 (仅记录)

All misaligned entries are 32-bit (armeabi-v7a / x86): NDK r28.2 libc++_shared.so, ONNX Runtime libonnxruntime4j_jni.so, Paddle-Lite libpaddle_light_api_shared.so / libpaddle_lite_jni.so and the self-built libautojs6_luals.so of Ace Editor. Android 16 KB devices only run 64-bit code, so these rows have no runtime effect; they stay informational until upstreams ship 16 KB 32-bit builds (libautojs6_luals.so could be relinked with -z max-page-size=16384 if ever wanted).

`tools/16kb/scan_alignment.py --max-apks 3 (2026-09-12 13:21)`: 64 位不合规 0 项, 32 位不合规 14 项:

| 仓库 | 来源 | ABI | 条目 | 最小 PT_LOAD 对齐 |
| --- | --- | --- | --- | --- |
| AutoJs6 | `(repo files)` | armeabi-v7a | `app/src/main/jniLibs/armeabi-v7a/libc++_shared.so` | `0x1000` |
| AutoJs6 | `(repo files)` | x86 | `app/src/main/jniLibs/x86/libc++_shared.so` | `0x1000` |
| Ace-Editor | `autojs6-plugin-ace-editor-v1.1.25-universal.apk` | armeabi-v7a | `lib/armeabi-v7a/libautojs6_luals.so` | `0x1000` |
| Ace-Editor | `(repo files)` | armeabi-v7a | `app/src/main/jniLibs/armeabi-v7a/libautojs6_luals.so` | `0x1000` |
| Paddle-OCR-PP-OCRv3 | `(repo files)` | armeabi-v7a | `libs/paddleocr/PaddleLite/cxx/libs/armeabi-v7a/libc++_shared.so` | `0x1000` |
| Paddle-OCR-PP-OCRv3 | `(repo files)` | armeabi-v7a | `libs/paddleocr/PaddleLite/cxx/libs/armeabi-v7a/libpaddle_light_api_shared.so` | `0x1000` |
| Paddle-OCR-PP-OCRv3 | `(repo files)` | armeabi-v7a | `libs/paddleocr/PaddleLite/java/libs/armeabi-v7a/libpaddle_lite_jni.so` | `0x1000` |
| Paddle-OCR-PP-OCRv4 | `autojs6-plugin-paddle-ocr-pp-ocrv4-v1.0.2-mobile-universal.apk` | armeabi-v7a | `lib/armeabi-v7a/libc++_shared.so` | `0x1000` |
| Paddle-OCR-PP-OCRv4 | `autojs6-plugin-paddle-ocr-pp-ocrv4-v1.0.2-mobile-universal.apk` | armeabi-v7a | `lib/armeabi-v7a/libonnxruntime4j_jni.so` | `0x1000` |
| Paddle-OCR-PP-OCRv4 | `autojs6-plugin-paddle-ocr-pp-ocrv4-v1.0.2-mobile-en-armeabi-v7a.apk` | armeabi-v7a | `lib/armeabi-v7a/libc++_shared.so` | `0x1000` |
| Paddle-OCR-PP-OCRv4 | `autojs6-plugin-paddle-ocr-pp-ocrv4-v1.0.2-mobile-en-armeabi-v7a.apk` | armeabi-v7a | `lib/armeabi-v7a/libonnxruntime4j_jni.so` | `0x1000` |
| Paddle-OCR-PP-OCRv4 | `(repo files)` | armeabi-v7a | `libs/ppocrv4-plugin-runtime/src/main/jniLibs/armeabi-v7a/libc++_shared.so` | `0x1000` |
| Paddle-OCR-PP-OCRv5 | `(repo files)` | armeabi-v7a | `libs/ppocrv5-plugin-runtime/src/main/jniLibs/armeabi-v7a/libc++_shared.so` | `0x1000` |
| Paddle-OCR-PP-OCRv6 | `(repo files)` | armeabi-v7a | `libs/ppocrv6-plugin-runtime/src/main/jniLibs/armeabi-v7a/libc++_shared.so` | `0x1000` |

## B2: 现代打包评估 (不需要)

Host and inrt ship 3 native libraries per ABI (libc++_shared + 2 jackpal JNI): 1.3 MB uncompressed, 0.4 MB deflated, about 1% of the APK. useLegacyPackaging=true / extractNativeLibs=true stays; modern packaging would add ~0.9 MB per ABI to the download for ~1.3 MB less extracted storage and would force the APK-Builder-Template rewrite listed in D1. Re-evaluate only if the host native volume grows by an order of magnitude.

| APK | APK 大小 | .so 数 | .so 未压缩 | .so 压缩后 | STORED |
| --- | ---: | ---: | ---: | ---: | ---: |
| `autojs6-v6.8.0-arm64-v8a.apk` | 36,285,310 | 3 | 1,270,224 | 403,943 | 0 |
| `inrt-v6.8.0-arm64-v8a.apk` | 35,093,627 | 3 | 1,270,224 | 403,943 | 0 |
| `autojs6-v6.8.0-universal.apk` | 37,482,336 | 12 | 4,535,792 | 1,599,685 | 0 |

## B1: Bun x86_64 16 KB JSC (候选已在 Bun 仓库验证, 正式 payload 未替换)

AutoJs6-Plugin-Bun-Runtime 3ac84ca (2026-09-12) rebuilt JSC/WTF/bmalloc from pinned WebKit 0f966e81 with USE_64KB_PAGE_BLOCK=1 and explicit JIT/DFG/FTL/Wasm-JIT/external-mimalloc settings and relinked the nine-patch Bun (x86_64 SHA-256 704cc156...3963e38). The isolated test-only .api28binder.jsc16k APK passes the 8-test Binder suite twice on native x86_64 API 36 at 4 KiB and 16 KiB pages (docs/compatibility/2026-09-12-m5-x86-16k-jsc.md). The released Bun payload is unchanged (official 1.4.0+34cbb9a40), so the plugin's x86_64 16 KB block stands until the candidate becomes a release; tracked by the Bun repo ROADMAP M5.

## B3 / B4

- B3 (`device.pageSize`) 是 JS 公开 API 变更, 需要走 AGENTS.md 的文档 / d.ts / LSP 关联仓库流程 (版本递增与发布), 本轮未做, 留给维护者决定.
- B4 已完成 (宿主 CI 移除无用 CMake / Ninja 步骤).
