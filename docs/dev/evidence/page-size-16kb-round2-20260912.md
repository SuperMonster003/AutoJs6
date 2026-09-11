# 16 KB 第二轮: X1-X3 修复, NDK r28c 统一重编与真机复验, 2026-09-12

结论: Samsung 补验暴露的 X1 (Rapid 缺 libc++) 与 X2 (Paddle v4 Server / v5 Thai / v6 Tiny 旧 libc++ 初始化 SIGSEGV) 已修复, 并在原生 16 KB arm64 (Samsung 剩余时段), 两台 16 KB x86_64 模拟器与 4 KB arm64 真机上取得真实 `ocr.recognizeText` 证据; X3 (Paddle v3 `1000 x 320` 崩溃) 在 4 KB 真机上以相同 pc 复现, 属于 Paddle-Lite 预编译库的输入相关缺陷, 与页大小无关, 转入 backlog B7. OpenCV 4.8.0 已用 NDK r28c 重编并同步到 Rapid / Paddle donor, 宿主 build 5280 起同时接受 r26b 与 r28c 原生构建. Three-Stone-AI 在 16 KB x86_64 (API 37) 上用真实 Qwen2.5-1.5B 模型完成初始化与非空生成. 最终产物在原生 16 KB arm64 上的复验因 Samsung 时段到期尚未完成.

对应 [完整 JSON](page-size-16kb-round2-20260912.json), [主 Roadmap](../page-size-16kb-roadmap.md), [维护者收尾清单](../page-size-16kb-maintainer-checklist.md) 与 [上一轮 Samsung 补验](page-size-16kb-samsung-20260911.md).

## 设备与时段

| 设备 | 系统 / ABI | 页大小 | 备注 |
| --- | --- | --- | --- |
| Samsung SM-A566B (RDB) (`localhost:54059`) | API 36 / arm64-v8a | 16384 | linker=false, PM compat disabled=true; 时段至 2026-09-11 23:52 |
| AVD sdk_gphone16k_x86_64 (`emulator-5582`) | API 37 / x86_64 | 16384 | linker=fatal, PM compat disabled=true, 4 GB RAM |
| AVD sdk_gphone16k_x86_64 (`emulator-5580`) | API 36 / x86_64 | 16384 | linker=false, PM compat disabled=true, 2.5 GB RAM |
| Xiaomi 23046RP50C (`968e9f18`) | API 35 / arm64-v8a | 4096 | X3 的 4 KB 对照; 扫描后 ADB 传输卡死 |
| Redmi 22120RN86C (`bek749scrwv4wo8h`) | API 33 / arm64-v8a | 4096 | 最终产物的 4 KB arm64 回归 |

Samsung 时段 `localhost:54059` 有效期至 2026-09-11 23:52 CST; 验收期间 `bionic.linker.16kb.app_compat.enabled=false`, `pm.16kb.app_compat.disabled=true`, 宿主与 6 个 OCR 插件 `pageSizeCompat=0` (`device-identity.txt`). 时段在恢复这两个属性之前到期, 恢复失败 (`device 'localhost:54059' not found`); 下次申领时请重新读取并按需恢复. Xiaomi Pad 在尺寸扫描后 ADB 传输卡死 (`error: closed` / `Too many open files`), 4 KB arm64 回归改用 Redmi 22120RN86C.

## 修复与变更

- X1, Rapid v3 / v6: `libs/rapidocr/build.gradle.kts` 增加 `-DANDROID_STL=c++_shared`, AGP 随 4 个 ABI 打包 NDK r28.2 的 `libc++_shared.so`; `opencv-native-4.8.0.aar` 换为 r28c donor.
- X2, Paddle v4 / v5 / v6: `jniLibs/{arm64-v8a,armeabi-v7a}/libc++_shared.so` 换为 NDK r28.2 副本 (`tools/16kb/refresh_libcxx.py --target`, 附 provenance JSON); `ORTSessionManager` 将模型资源一次性落盘到应用私有目录并以路径创建会话 (ONNX Runtime 内存映射), 修复 v4 Server 读取 90 MB 识别模型时的 `OutOfMemoryError` (宿主静默返回空结果); 四个 Paddle 仓库 `org-opencv-4_8_0/opencv-4.8.0.aar` 中 4 个 ABI 的 `libopencv_java4.so` 换为 r28c donor.
- X3, Paddle v3: 定位见下文, OpenCV payload 同步 r28c; Paddle-Lite 预编译库未改动.
- OpenCV 插件: `libopencv_java4.so` 以 NDK r28c (28.2.13676358, clang 19.0.1) 重编, v1.1.2 (build 12), 最低宿主 build 5280; 构建脚本, 打包门禁, provenance, NATIVE_BUILD 与 README 同步.
- 宿主: OpenCV / ImageQuant 策略接受 `28.2.13676358` 与 `26.1.10909125` (单元测试与设备测试覆盖); VERSION_BUILD 5280; `tools/16kb/elf_layout.py` (RELRO 末端与可写数据的 16 KB 页布局判定), `tools/16kb/ocr_provider_smoke.py` (通过 `RunIntentActivity` 真实调用各 provider, 生成收据与 logcat), `refresh_libcxx.py` / `refresh_rapid_opencv.py` / `refresh_paddle_opencv.py` 支持新 donor; 插件中心版本摘要补 ISO 8601 日期时间解析 (列表与对话框此前已输出 `yyyy-MM-dd`).
- Three-Stone-AI: 新增 `LiteRtRealModelInferenceInstrumentationTest` (走生产的目录整合 / 健康检查 / 引擎 / 会话 API, 输出 JSON 收据).

## X2 根因: 旧 libc++ 的 RELRO 末端与可写数据同页

`tools/16kb/elf_layout.py` 对 Paddle v4 原 `arm64-v8a/libc++_shared.so` (sha256 `cbf48c05fd429d28…`) 与 r28.2 副本 (sha256 `ab4e6c71b96b851d…`) 的判定:

```
== D:\idea-projects\AutoJs6\build\tmp-16kb\elf\libcxx-old-arm64.so (1055016 bytes)
  LOAD  R-X off=0x0 vaddr=0x0 filesz=0xfa5a4 memsz=0xfa5a4 align=0x10000
  LOAD  RW- off=0xfa7c8 vaddr=0x10a7c8 filesz=0x6948 memsz=0xa058 align=0x10000
  RELRO start=0x10a7c8 end=0x111000 end%16K=NOT aligned -> mprotect on 16 KB pages would cover 0x111000..0x114000 of writable data (RW LOAD ends 0x114820)
  verdict: RISK
```

```
== D:\idea-projects\AutoJs6\build\tmp-16kb\elf\libcxx-r282-arm64.so (9236352 bytes)
  LOAD  R-- off=0x0 vaddr=0x0 filesz=0x988a0 memsz=0x988a0 align=0x4000
  LOAD  R-X off=0x988a0 vaddr=0x9c8a0 filesz=0x8eff8 memsz=0x8eff8 align=0x4000
  LOAD  RW- off=0x127898 vaddr=0x12f898 filesz=0x9b98 memsz=0xa768 align=0x4000
  LOAD  RW- off=0x131430 vaddr=0x13d430 filesz=0x238 memsz=0x7d20 align=0x4000
  RELRO start=0x12f898 end=0x13a000 end%16K=NOT aligned
  verdict: OK
```

bionic 以页为单位对 `PT_GNU_RELRO` 做 `mprotect(PROT_READ)`; 旧库 RELRO 末端 `0x111000` 在 16 KB 页上被向上取整到 `0x114000`, 覆盖了同一 RW LOAD 中 `0x111000..0x114820` 的可写数据, `ios_base::Init` 的首次写入即 `SEGV_ACCERR`. r28.2 的 lld 在 RELRO 之后留出页间空隙, 第二个 RW LOAD 从 `0x13d430` 开始, 不受影响. 这与上一轮 "不能仅凭 `RELRO end % 16384` 拒绝所有库" 的判断一致: 判定必须结合 LOAD 布局.

## OCR provider 真实调用

每一行为一次通过宿主公开 `ocr.recognizeText(image, {mode, variant})` 的调用, 收据与 logcat 位于 `build/tmp-16kb/samsung-round2-20260911/`, SHA 见 JSON. Samsung 行使用的是修复后的首批产物 (Rapid 为 r26b donor, Paddle v4-v6 为 r28.2 libc++ + 旧 OpenCV payload); 模拟器与 Redmi 行使用 r28c donor 与最终 ORT 修复.

| 设备 | 用例 | provider | 输入 | 结果 | 耗时 (ms) | 备注 |
| --- | --- | --- | --- | --- | ---: | --- |
| Samsung SM-A566B (RDB) | `samsung-paddle-v3-generated` | paddle v3 | 1000 x 320 | 失败 | 1581 | InternalError: Failed to call method "ocr.recognizeText". binderDied: … |
| Samsung SM-A566B (RDB) | `samsung-paddle-v3-reference` | paddle v3 | 944 x 640 | 通过 | 1803 | 测试 |
| Samsung SM-A566B (RDB) | `samsung-paddle-v4-generated-3` | paddle v4 | 1000 x 320 | 失败 | 1829 | InternalError: Failed to call method "ocr.recognizeText". / Stack trac… |
| Samsung SM-A566B (RDB) | `samsung-paddle-v4-mobile-generated-2` | paddle v4 | 1000 x 320 | 失败 | 61324 | InternalError: Failed to call method "ocr.recognizeText". bindService … |
| Samsung SM-A566B (RDB) | `samsung-paddle-v4-mobile-generated` | paddle v4 | 1000 x 320 | 失败 | 1899 | InternalError: Failed to call method "ocr.recognizeText". / Stack trac… |
| Samsung SM-A566B (RDB) | `samsung-paddle-v4-server-generated` | paddle v4 | 1000 x 320 | 失败 | 1881 | 宿主返回空结果 (无异常) |
| Samsung SM-A566B (RDB) | `samsung-paddle-v4-server-reference-2` | paddle v4 | 944 x 640 | 失败 | 1832 | 宿主返回空结果 (无异常) |
| Samsung SM-A566B (RDB) | `samsung-paddle-v4-server-reference` | paddle v4 | 944 x 640 | 失败 | 1824 | 宿主返回空结果 (无异常) |
| Samsung SM-A566B (RDB) | `samsung-paddle-v5-thai-generated` | paddle v5 | 1000 x 320 | 通过 | 1269 | TEST, 16 KB |
| Samsung SM-A566B (RDB) | `samsung-paddle-v6-tiny-generated` | paddle v6 | 1000 x 320 | 通过 | 1120 | TEST 16 KB, Hello World |
| Samsung SM-A566B (RDB) | `samsung-rapid-v3-generated` | rapid v3 | 1000 x 320 | 通过 | 1479 | TEST 16 KB, Hello World |
| Samsung SM-A566B (RDB) | `samsung-rapid-v6-generated` | rapid v6 | 1000 x 320 | 通过 | 1556 | TEST 16 KB, Hello World |
| AVD sdk_gphone16k_x86_64 | `emulator-5582-16k-rapid-r28c-generated` | rapid v6 | 1000 x 320 | 通过 | 8602 | TEST 16 KB, Hello World |
| AVD sdk_gphone16k_x86_64 | `emulator-5582-16k-rapid-r28c-generated` | rapid v3 | 1000 x 320 | 失败 | 1240 | InternalError: Failed to call method "ocr.recognizeText". Plugin "io.g… |
| AVD sdk_gphone16k_x86_64 | `emulator-5582-16k-rapid-v3-r28c-generated` | rapid v3 | 1000 x 320 | 通过 | 2661 | TEST 16 KB, Hello World |
| AVD sdk_gphone16k_x86_64 | `emulator-5580-16k-rapid-r28c-generated` | rapid v6 | 1000 x 320 | 通过 | 20444 | TEST 16 KB, Hello World |
| AVD sdk_gphone16k_x86_64 | `emulator-5580-16k-rapid-r28c-generated` | rapid v3 | 1000 x 320 | 失败 | 1279 | InternalError: 方法 "ocr.recognizeText" 调用失败. 插件 "io.github.supermonster… |
| AVD sdk_gphone16k_x86_64 | `emulator-5580-16k-rapid-v3-r28c-generated` | rapid v3 | 1000 x 320 | 通过 | 4877 | TEST 16 KB, Hello World |
| Xiaomi 23046RP50C | `xiaomi-4k-paddle-v3-generated` | paddle v3 | 1000 x 320 | 失败 | 3524 | InternalError: 方法 "ocr.recognizeText" 调用失败. / Stack trace: / android.o… |
| Redmi 22120RN86C | `redmi-4k-paddle-v3-r28c-generated-1000x640` | paddle v3 | 1000 x 640 | 通过 | 3204 | TEST 16 KB, Hello World |
| Redmi 22120RN86C | `redmi-4k-paddle-v3-r28c-reference` | paddle v3 | 672 x 456 | 通过 | 2034 | 测试 |
| Redmi 22120RN86C | `redmi-4k-paddle-v4-mobile-r28c-generated` | paddle v4 | 1000 x 320 | 通过 | 5667 | TEST 16 KB, Hello |
| Redmi 22120RN86C | `redmi-4k-paddle-v4-server-r28c-generated` | paddle v4 | 1000 x 320 | 通过 | 61604 | TEST 16 KB, Hello World |
| Redmi 22120RN86C | `redmi-4k-paddle-v4-server-r28c-reference` | paddle v4 | 672 x 456 | 通过 | 30800 | 测试,  |
| Redmi 22120RN86C | `redmi-4k-paddle-v5-server-r28c-generated` | paddle v5 | 1000 x 320 | 通过 | 14116 | TEST, 16 KB |
| Redmi 22120RN86C | `redmi-4k-paddle-v5-server-r28c-reference` | paddle v5 | 672 x 456 | 通过 | 9957 | 测试 |
| Redmi 22120RN86C | `redmi-4k-paddle-v5-thai-r28c-generated` | paddle v5 | 1000 x 320 | 通过 | 3078 | TEST, 16 KB |
| Redmi 22120RN86C | `redmi-4k-paddle-v6-tiny-r28c-generated` | paddle v6 | 1000 x 320 | 通过 | 2878 | TEST 16 KB, Hello World |
| Redmi 22120RN86C | `redmi-4k-rapid-v6-v3-r28c-generated` | rapid v6 | 1000 x 320 | 通过 | 3864 | TEST 16 KB, Hello World |
| Redmi 22120RN86C | `redmi-4k-rapid-v6-v3-r28c-generated` | rapid v3 | 1000 x 320 | 通过 | 2139 | TEST 16 KB, Hello World |

Samsung 上 Paddle v4 Server 的三次失败均不再是初始化 SIGSEGV, 而是识别模型读入 Java 堆时的 `OutOfMemoryError` (宿主吞掉异常返回空数组), 由 ORT 内存映射修复; 该修复在 Redmi 4 KB 上以 v4 Server 与 v5 Server 的生成图 / 参考图验证通过. Samsung 上 v4 Mobile 的三次失败 (含 `bindService` 超时那次) 的 logcat 均为旧 libc++ 的初始化 SIGSEGV (pc `0x5c15c`): 当时 Mobile 的重建因输出目录无法删除而失败, 安装的仍是 14:11 的旧 APK. 最终 v1.0.2 Mobile APK 经下文 APK 内容审计携带 r28.2 libc++, 并在 Redmi 4 KB 通过, 但它在 16 KB arm64 上的运行时验证仍待新时段. 模拟器上 Rapid v3 首次失败为 "插件未在插件中心启用" (新装插件未自动启用), 冒烟工具随后增加 `--enable-packages`.

## X3: Paddle v3 `1000 x 320` 崩溃的 4 KB 对照

Xiaomi 23046RP50C (API 35, 4096) 上安装相同 Paddle v3 产物, 同一生成图触发相同崩溃: `libpaddle_light_api_shared.so` 首帧 `0xc7a44`, `SIGSEGV / SEGV_ACCERR`, 故障地址为带标记的堆地址. 尺寸扫描:

| 尺寸 | 结果 | 备注 |
| --- | --- | --- |
| 1000 x 320 | 崩溃 | InternalError: 方法 "ocr.recognizeText" 调用失败. / Stack trace: /… |
| 960 x 320 | 崩溃 | InternalError: 方法 "ocr.recognizeText" 调用失败. bindService time… |
| 992 x 320 | 崩溃 | InternalError: 方法 "ocr.recognizeText" 调用失败. / Stack trace: /… |
| 1024 x 320 | 崩溃 | InternalError: 方法 "ocr.recognizeText" 调用失败. / Stack trace: /… |
| 800 x 320 | 崩溃 | InternalError: 方法 "ocr.recognizeText" 调用失败. / Stack trace: /… |
| 1000 x 352 | 崩溃 | InternalError: 方法 "ocr.recognizeText" 调用失败. / Stack trace: /… |
| 1000 x 640 | 通过 | TEST 16 KB, Hello World |
| 640 x 320 | 通过 | TEST 16 KB, Hello World |
| 1000 x 300 | 通过 | TEST 16 KB, Hello World |
| 944 x 640 | 通过 | TEST 16 KB, Hello World |

宽 800-1024 且高 320 / 352 的输入稳定崩溃, 其它比例通过. 因此该崩溃与页大小无关, 是 Paddle-Lite 预编译库 (来源未记录) 对特定输入比例的缺陷, 记入 backlog B7; 16 KB 适配范围内 v3 以 `1000 x 640` 生成图与 `944 x 640` 参考图验收.

## APK 内 libc++ 审计

对被测 APK 直接解包 `lib/<abi>/libc++_shared.so` 并用 `elf_layout.py` 判定 (RISK = RELRO 末端所在 16 KB 页内仍有同一段的可写数据). 最后两行是 14:11 的旧构建, 仅作对照: 未重建的 flavor 仍携带旧 libc++, 发布前所有 flavor 必须从已提交源码重建并通过门禁.

| APK | 条目 | SHA-256 (前 16 位) | RELRO 末端 | 判定 |
| --- | --- | --- | --- | --- |
| `autojs6-v6.8.0-arm64-v8a.apk` | `lib/arm64-v8a/libc++_shared.so` | `cd61762848882a16` | `0x13a000` | OK |
| `autojs6-v6.8.0-x86_64.apk` | `lib/x86_64/libc++_shared.so` | `2e855857f8081973` | `0x134000` | OK |
| `app-app-debug-androidTest.apk` | - | - | - | no libc++_shared.so (host-provided runtime) |
| `autojs6-plugin-opencv-v1.1.2-arm64-v8a.apk` | - | - | - | no libc++_shared.so (host-provided runtime) |
| `autojs6-plugin-opencv-v1.1.2-x86_64.apk` | - | - | - | no libc++_shared.so (host-provided runtime) |
| `autojs6-plugin-rapid-ocr-pp-ocrv3-v1.0.1-arm64-v8a.apk` | `lib/arm64-v8a/libc++_shared.so` | `cd61762848882a16` | `0x13a000` | OK |
| `autojs6-plugin-rapid-ocr-pp-ocrv3-v1.0.1-x86_64.apk` | `lib/x86_64/libc++_shared.so` | `2e855857f8081973` | `0x134000` | OK |
| `autojs6-plugin-rapid-ocr-pp-ocrv6-v1.0.1-arm64-v8a.apk` | `lib/arm64-v8a/libc++_shared.so` | `cd61762848882a16` | `0x13a000` | OK |
| `autojs6-plugin-rapid-ocr-pp-ocrv6-v1.0.1-x86_64.apk` | `lib/x86_64/libc++_shared.so` | `2e855857f8081973` | `0x134000` | OK |
| `autojs6-plugin-paddle-ocr-pp-ocrv3-v1.0.1-arm64-v8a.apk` | `lib/arm64-v8a/libc++_shared.so` | `cd61762848882a16` | `0x13a000` | OK |
| `autojs6-plugin-paddle-ocr-pp-ocrv4-v1.0.2-mobile-arm64-v8a.apk` | `lib/arm64-v8a/libc++_shared.so` | `cd61762848882a16` | `0x13a000` | OK |
| `autojs6-plugin-paddle-ocr-pp-ocrv4-v1.0.2-server-arm64-v8a.apk` | `lib/arm64-v8a/libc++_shared.so` | `cd61762848882a16` | `0x13a000` | OK |
| `autojs6-plugin-paddle-ocr-pp-ocrv5-v1.0.1-thai-arm64-v8a.apk` | `lib/arm64-v8a/libc++_shared.so` | `cd61762848882a16` | `0x13a000` | OK |
| `autojs6-plugin-paddle-ocr-pp-ocrv5-v1.0.1-server-arm64-v8a.apk` | `lib/arm64-v8a/libc++_shared.so` | `cd61762848882a16` | `0x13a000` | OK |
| `autojs6-plugin-paddle-ocr-pp-ocrv6-v1.0.1-tiny-arm64-v8a.apk` | `lib/arm64-v8a/libc++_shared.so` | `cd61762848882a16` | `0x13a000` | OK |
| `autojs6-plugin-three-stone-ai-v1.1.1-arm64-v8a.apk` | - | - | - | no libc++_shared.so (host-provided runtime) |
| `autojs6-plugin-three-stone-ai-v1.1.1-universal.apk` | - | - | - | no libc++_shared.so (host-provided runtime) |
| `autojs6-plugin-three-stone-ai-v1.1.1-x86_64.apk` | - | - | - | no libc++_shared.so (host-provided runtime) |
| `app-debug-androidTest.apk` | - | - | - | no libc++_shared.so (host-provided runtime) |
| `autojs6-plugin-paddle-ocr-pp-ocrv5-v1.0.1-mobile-arm64-v8a.apk` | `lib/arm64-v8a/libc++_shared.so` | `9fb28ade1af6a5af` | `0x111000` | RISK |
| `autojs6-plugin-paddle-ocr-pp-ocrv6-v1.0.1-small-arm64-v8a.apk` | `lib/arm64-v8a/libc++_shared.so` | `9fb28ade1af6a5af` | `0x111000` | RISK |

## 宿主加载 OpenCV 的最终产物

宿主 build 5280 (`autojs6-v6.8.0-*.apk`) + OpenCV 插件 v1.1.2 (r28c) 的 `OpenCvPluginHostDeviceTest` (加载 / 运算 / 原生异常不杀宿主, 并断言 `nativeNdkVersion` 属于受支持集合):

| 设备 | 宿主 versionCode | OpenCV 插件 | 结果 |
| --- | --- | --- | --- |
| AVD sdk_gphone16k_x86_64 (API 37, 16384) | 5280 | v1.1.2 | 2 / 2 通过 |
| AVD sdk_gphone16k_x86_64 (API 36, 16384) | 5280 | v1.1.2 | 2 / 2 通过 |
| Redmi 22120RN86C (API 33, 4096) | 5280 | v1.1.2 | 2 / 2 通过 |

## Three-Stone-AI 真实模型推理

- AVD sdk_gphone16k_x86_64 (API 37, 页大小 16384): 模型 SHA `faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9` (1597931520 字节, 目录 id `qwen2-5-1-5b-instruct`), 健康检查 14308 ms, 引擎初始化 1254 ms, 生成 102681 ms, 输出 "Hello tester! How's everything going?" (8 个增量, CPU 后端).
- Redmi 22120RN86C (API 33, 页大小 4096): 模型 SHA `faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9` (1597931520 字节, 目录 id `qwen2-5-1-5b-instruct`), 健康检查 22796 ms, 引擎初始化 1096 ms, 生成 13413 ms, 输出 "Hello tester! How's the testing going today?" (10 个增量, CPU 后端).
- AVD sdk_gphone16k_x86_64 (API 36): ApplicationExitInfo reason=3 (LOW_MEMORY), rss=1.3GB; AVD MemTotal 2.5 GB. 1.6 GB 模型无法驻留, 不属于插件缺陷.
- 原生 16 KB arm64: Samsung 时段在模型准备完成前到期, 待新时段补测.

## 产物 SHA-256

| 产物 | 仓库 | SHA-256 |
| --- | --- | --- |
| `autojs6-v6.8.0-arm64-v8a.apk` | AutoJs6 | `08b04a489156edd836a9966cfaabace3b425e24207c15b1e454557057fb639ea` |
| `autojs6-v6.8.0-x86_64.apk` | AutoJs6 | `42aef34a7bb33de9c2b7c6cc86a76c572b59211d67f2ed2a0339f8e21c547c8a` |
| `app-app-debug-androidTest.apk` | AutoJs6 | `116f5123cf2e44f9e5a980509061eb4874f41de88fc23c302d277370312111f9` |
| `autojs6-plugin-opencv-v1.1.2-arm64-v8a.apk` | AutoJs6-Plugin-OpenCV | `e747256bc86768eb106dd77c7476fd69584dfa09a24ee94d47f157651e913d72` |
| `autojs6-plugin-opencv-v1.1.2-x86_64.apk` | AutoJs6-Plugin-OpenCV | `744c6205e294bfdb9fc5358a42165f1d5f8fb32c7a3b1352516b0e1eacf601b3` |
| `autojs6-plugin-rapid-ocr-pp-ocrv3-v1.0.1-arm64-v8a.apk` | AutoJs6-Plugin-Rapid-OCR-PP-OCRv3 | `6db0070e9c2b3f7d4ec10669edd0376ecd4623de95a5be1a89f3e0d62a72ebb2` |
| `autojs6-plugin-rapid-ocr-pp-ocrv3-v1.0.1-x86_64.apk` | AutoJs6-Plugin-Rapid-OCR-PP-OCRv3 | `763957bd108caac991576791da63a5626e881ce913816a7c9a173a9de8e168ed` |
| `autojs6-plugin-rapid-ocr-pp-ocrv6-v1.0.1-arm64-v8a.apk` | AutoJs6-Plugin-Rapid-OCR-PP-OCRv6 | `16023221df418e42a95d9ee53ddadb89879637f96d26f3cebbcc92dcb15df85a` |
| `autojs6-plugin-rapid-ocr-pp-ocrv6-v1.0.1-x86_64.apk` | AutoJs6-Plugin-Rapid-OCR-PP-OCRv6 | `078dda32758b73d0734858eb98e363995b2d548dcd0ac693a5bdcd30683e77c3` |
| `autojs6-plugin-paddle-ocr-pp-ocrv3-v1.0.1-arm64-v8a.apk` | AutoJs6-Plugin-Paddle-OCR-PP-OCRv3 | `dfb9d0d5f4210c41f52b00ee73930442a0df77b7058cee567de1b8ee7d6536e4` |
| `autojs6-plugin-paddle-ocr-pp-ocrv4-v1.0.2-mobile-arm64-v8a.apk` | AutoJs6-Plugin-Paddle-OCR-PP-OCRv4 | `2aa27e8e8085a821a81959b8b67d95858610229a1f59962740391414d4cafc7d` |
| `autojs6-plugin-paddle-ocr-pp-ocrv4-v1.0.2-server-arm64-v8a.apk` | AutoJs6-Plugin-Paddle-OCR-PP-OCRv4 | `d7daf8b0b1482b0feb5bd8a32938662e4261282dada7241c904639c119b17fb5` |
| `autojs6-plugin-paddle-ocr-pp-ocrv5-v1.0.1-thai-arm64-v8a.apk` | AutoJs6-Plugin-Paddle-OCR-PP-OCRv5 | `418788af478616f7b1999208971873e3fb87c979b24a9595ba0a52a9d2b72056` |
| `autojs6-plugin-paddle-ocr-pp-ocrv5-v1.0.1-server-arm64-v8a.apk` | AutoJs6-Plugin-Paddle-OCR-PP-OCRv5 | `574574515a06b2bd302a83506fd1a43a64e3448e83140f58ecb68f84c50e57de` |
| `autojs6-plugin-paddle-ocr-pp-ocrv6-v1.0.1-tiny-arm64-v8a.apk` | AutoJs6-Plugin-Paddle-OCR-PP-OCRv6 | `19f640e2ab6d4b92342cd266df94ae57bbebe8fb04420d0b6363d3d20d6b1d32` |
| `autojs6-plugin-three-stone-ai-v1.1.1-arm64-v8a.apk` | AutoJs6-Plugin-Three-Stone-AI | `cad6a96012d2fee0bf68c9e05196b13ca3b98bc3035179755535e87a34f1f945` |
| `autojs6-plugin-three-stone-ai-v1.1.1-universal.apk` | AutoJs6-Plugin-Three-Stone-AI | `ac4dab60095d4561bff345405bd7bc30edac3dca4d586a26a9407c1a16136bfe` |
| `autojs6-plugin-three-stone-ai-v1.1.1-x86_64.apk` | AutoJs6-Plugin-Three-Stone-AI | `c0a74a4531fcc14a41ffc1b10ea5404a0a2f0d2f1e836f3e73fe94632c26e5c1` |
| `app-debug-androidTest.apk` | AutoJs6-Plugin-Three-Stone-AI | `6690d85cd93bc4e29e159b9be71e1e7731e52e7f5f8733133e2be10967064cda` |
| `opencv-native-4.8.0.aar` | AutoJs6-Plugin-OpenCV | `f760265bbc0bae75f2f906088fee3447eb32d9bc2b4d7ad8fd246923ddc26dd6` |
| `opencv-native-4.8.0.aar` | AutoJs6-Plugin-Rapid-OCR-PP-OCRv3 | `75f9c45d1c8467128c061e15042b482c57f05c7ebfca8206b4d417faacb609b4` |
| `opencv-4.8.0.aar` | AutoJs6-Plugin-Paddle-OCR-PP-OCRv4 | `8c4b410ae07a709d30e75019cf636393ce8784cacbac95ef3f47accf80b5d25c` |

Rapid v3 / v6, Paddle v3 / v5 / v6 与 Three-Stone 的被测 APK 在版本号递增之前构建, 文件名仍为旧版本名; Paddle v4 为递增后的 v1.0.2 构建. 发布候选包必须从已提交的源码重新生成并按维护者清单 R1 / R5 验收.

## 限制与未完成事项

- 原生 16 KB arm64 上的最终产物复验 (r28c OpenCV 插件, r28c donor 的 Rapid / Paddle, Three-Stone-AI arm64) 需要新的 Samsung 时段.
- Samsung 兼容模式属性未恢复 (时段到期); Xiaomi Pad ADB 传输需重新插拔或重启后再用.
- API 36 AVD 仅 2.5 GB 内存, 1.6 GB 模型被 lmkd 终止 (LOW_MEMORY), 不是插件缺陷; 如需在该 AVD 复测请以 4 GB 内存重建.
- 远程 CI, 正式 Release, 官方索引刷新 (R5-R8) 未做; 各插件仓库仍含其它会话的未提交修改, 本轮提交只包含本轮文件.
