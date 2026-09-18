# P5.1 DEX 转换器证据 (2026-09-18)

问题: Roadmap P5.1 原设想 "`AndroidClassLoader.dexJar()` 改用与 `loadJarLocally` 相同的 D8 调用 (API 26+ `D8Command.builder`, API 24 / 25 `D8.main` 参数形式)", 然后删除 `libs/com-android-dx-1_14.jar` 与 `libs/com-legacy-android-dx-1_7_0.jar`. 实施前先在设备上核实 D8 在各 API 级别是否真的可用.

结论: D8 在 API 24 / 25 完全不可用 (宿主的 `desugar_jdk_libs` 2.1.5 是不含 `nio` 的变体, `java.nio.file` 在 API 26 才存在), 旧版 dx 1.7.0 是 Android 7.x 唯一的 class -> dex 转换器; 只有 dx 1.14 (API 26+ 上的 `defineClass` 与 `loadJar` 回退) 能并入 D8. P5.1 据此只删除 dx 1.14, 旧版 dx 的去留列为 Q10 (P5.3).

## 门禁试验 (P4 构建, 仍含两个 dx jar)

脚本 `build/tmp-size/p5-gate.js`; 夹具 `probe7.jar` (`JarProbe`, class 文件版本 51) 与 `probe8.jar` (`Lambda8`: lambda / 默认方法 / 方法引用, 版本 52), 由 Temurin 11 `javac --release 7 / 8` 生成.

| 环境 | `runtime.loadJar` Java 7 jar | Java 8 jar | 实际转换器 | `JavaAdapter` (`AbstractList`) | 兼容加载器 `com.stardust` (直接调用 dx 1.14) |
| --- | --- | --- | --- | --- | --- |
| AVD API 24 (x86) | 通过 (232 ms) | 失败 (旧版 dx `Exception parsing classes`) | 旧版 dx 1.7.0; logcat 每次都先记录 `Failed: jarToDexR8` + `NoClassDefFoundError: Failed resolution of: Ljava/nio/file/Paths;` (`D8Command.parse`) | 通过 (116 ms, 旧版 dx) | 脚本崩溃: `NoSuchFieldError: No field callSiteIds ... in class Lcom/android/dex/TableOfContents;` (平台自带的 `com.android.dex` 遮蔽 dx 1.14 自带的同名类; 即 issue #293 引入重打包旧版 dx 的根因) |
| Redmi 22120RN86C API 33 | 通过 (335 ms) | 通过 (218 ms) | D8 | 通过 (135 ms, dx 1.14) | `defineClass` Java 7 类通过 (39 ms); Java 8 类被 dx 1.14 丢弃 (`ClassNotFoundException`, `loadJar` 则无输出文件) |

附带: 两个环境 `java.nio.file.Paths` 在 API 24 上 `ClassNotFoundException`, `j$.nio.file.Paths` 在两个环境都不存在 (证实没有 nio 脱糖).

## 实施

- 新增 `app/src/main/java/org/autojs/autojs/rhino/ClassFileDexer.kt`, 宿主运行时唯一的 class -> dex 入口: `isD8Available` (API 26+, 带 `@ChecksSdkIntAtLeast`), `runD8` (`D8Command.builder` + `D8.run`, 失败抛 `CompilationFailedException`; 不再经 `D8.main`, 它在失败时会 `System.exit`), `dexOutputs`, `packDexJar` (写出 `DexClassLoader` 读取的 `classes*.dex` jar, 与 dx `jarOutput` 布局相同), `convertToDexJar` (按 `isD8Available` 分派到 D8 或旧版 dx), `convertWithLegacyDx` (检查返回码与输出文件), `collectRuntimeLibraries`.
- `org/autojs/autojs/rhino/AndroidClassLoader.kt`: `loadJarLocally` 在 API 24 / 25 直接走重打包 + 旧版 dx (不再徒劳地先试 D8), API 26+ 走 `jarToDexR8`, 失败后对 zip4j 重打包副本再试一次 D8 (`jarToDexRepacked`, 顶替原 dx 回退位); `dexJar` (`defineClass` 与重打包路径) 经 `ClassFileDexer.convertToDexJar`; `jarToDexR8(jar)` 的编译步骤共用 `ClassFileDexer.runD8`; `generateTempFile` 去掉恒为 `false` 的参数. 冻结的 classpath seam `jarToDexR8(plan)` 未动.
- `com/stardust/autojs/rhino/AndroidClassLoader.java` (兼容加载器): `dexJar` 委托到 `ClassFileDexer.convertToDexJar`, 不再 import `com.android.dx`; 附带修复它在 7.x 上的 `NoSuchFieldError` 崩溃.
- `app/build.gradle.kts` 只保留 `libs/com-legacy-android-dx-1_7_0.jar` (注释说明仅供 API 24 / 25 与 Q10), `git rm libs/com-android-dx-1_14.jar`.
- JVM 单测 `ClassFileDexerTest` (4 项): 桌面 JVM 上同一个 r8 8.13.17 把 class jar 转成 `classes.dex` (magic `dex\n`, 含探针描述符), `packDexJar` 布局与覆盖 / 空列表, 坏 class 文件抛 `CompilationFailedException` 而不是退出进程.

## 冒烟 (P5.1 构建 6.8.0 (5281), `build/tmp-size/p5-smoke.js`)

夹具: `probe7.jar`, `probe8.jar`, `probe7.aar` (`classes.jar` = probe7), `broken.jar` (64 字节垃圾 `.class`). 输出 `build/tmp-size/p5/smoke-<serial>.txt`.

| 项目 | AVD API 24 (x86, 旧版 dx) | Sony G8441 API 28 (D8) | Redmi 22120RN86C API 33 (D8) |
| --- | --- | --- | --- |
| `com.android.dx.command.dexer.Main` / `ClassFileDexer.isD8Available` | 不存在 / `false` | 不存在 / `true` | 不存在 / `true` |
| `runtime.loadJar` Java 7 jar / 再次加载 (缓存命中) | 通过 97 ms / 9 ms | 通过 582 ms / 8 ms | 通过 328 ms / 6 ms |
| `runtime.loadJar` Java 8 jar (lambda, 默认方法, 方法引用) | 失败, `IOException: Legacy dx failed (status 1)` (7.x 既有限制, 以前是 `FileNotFoundException`) | 通过 498 ms | 通过 210 ms |
| `JavaAdapter`: `Callable` 对象 / `AbstractList` / `AbstractMap` + `Comparable` / `Runnable` 于 Java 线程 / 同类型再次创建 | 14 / 105 / 95 / 5 / 0 ms | 17 / 549 / 488 / 14 / 1 ms | 13 / 295 / 236 / 19 / 1 ms |
| `defineClass` Java 7 类: `org.autojs` 加载器 / `com.stardust` 兼容加载器 | 通过 48 ms / 71 ms (P4 构建上兼容加载器崩溃) | 通过 308 / 303 ms | 通过 115 / 173 ms |
| `defineClass` Java 8 类 (`org.autojs` 加载器) | 失败 (旧版 dx, 既有限制) | 通过 443 ms (dx 1.14 时丢类) | 通过 157 ms (dx 1.14 时丢类) |
| 兼容加载器 `loadJar` Java 7 / Java 8 jar | 通过 67 ms / 失败 (旧版 dx) | 通过 131 / 304 ms | 通过 97 / 281 ms |
| `runtime.loadAar` | 通过 66 ms | 通过 304 ms | 通过 97 ms |
| `runtime.loadJar` 坏 jar | `IOException: Legacy dx failed (status 1)`, 进程存活 | `CompilationFailedException: Invalid classfile header`, logcat `Failed: jarToDexR8` + `Failed: jarToDexRepacked`, 进程存活 | 同左, 进程存活 |

`JavaAdapter` 首个类型的耗时含 D8 首次运行的类加载 (API 26+ 上 dx 1.14 时 Redmi 为 135 ms); 稳态单类 D8 耗时见下.

## D8 单类耗时 (`build/tmp-size/p5-timing.js`, `probe7.jar`, 4 次)

| 环境 | 引导类路径 jar 数 | 含 library 输入 | 不含 library 输入 |
| --- | ---: | --- | --- |
| Redmi 22120RN86C API 33 | 41 | 99 / 91 / 82 / 92, 89 / 91 / 88 / 80 ms | 71 / 48 / 58 / 43, 48 / 56 / 43 / 45 ms |
| Sony G8441 API 28 | 23 | 93 / 87 / 103 / 85, 83 / 85 / 98 / 85 ms | 76 / 62 / 67 / 61, 58 / 56 / 55 / 75 ms |

library 输入约占 40 ms; `defineClass` 与 `loadJar` 保持同一条路径 (含 library 输入, 与 D8 的 API 建模一致), 不为此拆出第二条路径.

## 体积

[apk-size-p5-20260918.md](apk-size-p5-20260918.md): universal 35,869,027 字节 (对 P4 -316,584 = -0.30 MiB; 对基线 -5,277,028 = -5.03 MiB, -12.8%), arm64-v8a 34,672,001 字节, 定义方法 352,078 (P4 356,717), `com.android.dx` 0.45 -> 0 MiB; `com.legacy.android.dx` 0.43 MiB 保留 (Q10).

## Q10 (待拍板): 旧版 dx 1.7.0 的去留

| 方案 | 内容 | 代价 |
| --- | --- | --- |
| a | `desugar_jdk_libs` 改为 `desugar_jdk_libs_nio`, 让 D8 在 7.x 可运行, 再删除旧版 dx | `java.nio.file` 的脱糖作用于整个 APK (宿主, r8, okio 等), 需要 API 24 / 25 全面回归; nio 脱糖类本身有体积, 净收益未测 |
| b | 7.x 放弃动态类加载 (`runtime.loadJar` / `loadAar` / `JavaAdapter` 在 API 24 / 25 报错) | 与 D13 为 7.x 预留的方针相悖 |
| c | 保持现状 (建议) | 0.43 MiB 归属 DEX, 约 0.17 MiB APK |
