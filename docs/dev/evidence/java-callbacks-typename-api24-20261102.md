# Android 7.x 上 Java 回调桥的 Class.getTypeName (2026-11-02)

问题: `loadJar` 改走 D8 后复测 ([loadjar-d8-api24-20261101.md](loadjar-d8-api24-20261101.md)), `RuntimeJavaCallbacksNativeTest` 在 API 24 / 25 仍是 1 / 4, 剩余 3 项报 `NoSuchMethodError: Class.getTypeName()`. 该方法 API 26 才有, 所以 Node.js / Bun / Worker 的 `java.proxy`, `java.wrap`, `java.defineClass` 在 Android 7.x 上都用不了. 维护者拍板 rhino-class Q50 = A.

结论: 用等价函数替换两处调用后, API 24 / 25 回调用例全部通过, API 35 不变.

## 修法

- `RuntimeJavaSubclassGenerator.typeName(type)`: 数组取到元素类型后按维度补 `[]`, 其余用 `Class.getName()`, 与 `Class.getTypeName()` 的实现相同. 所以 `int[][]`, `java.util.Map$Entry`, `java.lang.String[]` 的写法在所有 API 级别一致. 脚本里的方法键 (`write(byte[],int,int)`), 事件的 `signature`, `callSuper` 的 `parameterTypes` 都用这个格式; `callSuper` 的名字回到 `RuntimeJavaSession.loadClass`, 它按 `[]` 后缀解析.
- `RuntimeJavaSubclassGenerator.key` 与 `RuntimeJavaSession` 的 `callSuper` 改用它.
- 生成器里的 `Method.parameterCount` 改为 `parameterTypes.size`. 这是回调桥路径上唯一另一个文档标为 API 26 的调用, 实测在 7.x 能跑 (见下节), 改掉是为了这条路径不再依赖非公开方法, 不是修故障.

## API 24 / 25 上的同类调用

探针是一个 `dalvikvm` 直接运行的类, 用 `Class.getMethod` 查公开方法, 再直接调用一次:

| 方法 | API 24 / 25 | API 35 |
| --- | --- | --- |
| `Class.getTypeName` | 无 | 有 |
| `Type.getTypeName` | 无 | 有 |
| `Method.getParameters` | 无 | 有 |
| `Method.getParameterCount` | 不是公开方法, 但直接调用返回正确结果 (应用进程内同样通过) | 有 |
| `ThreadLocal.withInitial` | 无 (宿主里由 L8 脱糖, 回调会话能构造) | 有 |

全仓核对结果, 都不用改:

| 位置 | 判定 |
| --- | --- |
| `NodeMethodRegistry`, `PluginMountValidator`, `OcrPluginHost`, `Dialogs` 的 `.parameterCount` | 不在回调桥范围; 上表显示 7.x 上能跑, 不是故障, 不动 |
| apksig `Asn1BerParser.getElementType` 的 `Type.getTypeName` | 有 `SDK_INT >= P` 门控 |
| 其他 `typeName` / `getTypeName` (FileUtils, 条码, 无障碍事件等) | 同名的自有属性, 与 `Class` 无关 |
| `app/src/main/assets*`, fork `Rhino-For-AutoJs6` 主代码 | 没有 `getTypeName` 调用 |

## 测试

JVM: `RuntimeJavaSubclassGeneratorTest` 7 / 7. 新增 2 例: 基元 (含 `void`), 包装类, 枚举, 嵌套类, 接口, 匿名类, 这些类型的 1 - 3 维数组, 逐个与 `Class.getTypeName()` 对照; `key` 的字面值.

设备 (`RuntimeJavaCallbacksNativeTest` 新增 2 例, 进入 `device_rhino_check.py` 默认集):

- `typeNamesMatchThePlatformFromApi26`: 字面值在所有 API 级别成立, API 26+ 再与平台 `Class.getTypeName()` 对照.
- `superCallsResolveArrayAndNestedParameterTypes`: 这张卡之前的用例都没有走 `callSuper`. 这里覆盖 `write(byte[],int,int)` 与 `setUncaughtExceptionHandler(java.lang.Thread$UncaughtExceptionHandler)`.

临时 AVD (`google_apis`, API 24 / 25 x86, API 35 x86_64), 构建: 宿主 43c858a63 加本次改动 (克隆):

| 项目 | API 24 | API 25 | API 35 |
| --- | --- | --- | --- |
| `RuntimeJavaCallbacksNativeTest` 原 4 项 (夹具 `callback.jar` 版本 52) | 4 / 4 (改动前 1 / 4) | 4 / 4 (改动前 1 / 4) | 4 / 4 |
| 新增 2 项 | 2 / 2 | 2 / 2 | 2 / 2 |
| `device_rhino_check.py` 默认集 (含新增 2 项) | 45 过 1 跳过 | 45 过 1 跳过 | 44 过 2 跳过 |

跳过项与上次相同: `ImagesResizeRhinoInstrumentationTest` 的既有 1 项; `legacyDxRefusesJava8ClassesOnTheApiLevelsThatKeepIt` 只在 API 26 以下运行. 上次是 42 / 41 过, 这次多的 3 项是新增的 2 项与上次全集之后才加入的 `orderedClasspathCompilesWithD8`.

未覆盖: 真机未测 (API 31 不受影响); Node.js / Bun 插件进程经 `RuntimeJavaCallbacksDeviceTest` 的整条路径未在 API 24 / 25 跑, 本次验证的是宿主桥接层.

## 设备脚本

`device_rhino_check.py --callback-fixtures DIR` 把 `tools/api-coverage/build-java-callback-fixtures.py` 的产物推到测试读取的位置. 推到 API 35 AVD 后要 `chmod` 才可读, API 24 的 sdcard 则拒绝 `chmod`, 所以这一步失败不算错误.

用例写法上的两个差异:

- `LinkedHashMap` 的首次 `put` 在 API 24 / 25 不触发 `removeEldestEntry`, API 35 触发, 不能用它当固定触发回调的方法.
- 三台 AVD 同时 `adb install` 时 API 25 卡满 600 s 超时, 依次安装正常.
