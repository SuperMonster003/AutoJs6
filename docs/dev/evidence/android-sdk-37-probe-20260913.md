# Android SDK 37 探针记录 (2026-09-13)

对应 [Android SDK 37 升级 Roadmap](../android-sdk-37-roadmap.md) 的 P0 基线. 快照: 宿主 `master@60b147716`, `VERSION_NAME=6.8.0`, `VERSION_BUILD=5280`, 工作区干净.

## 1. 探针编译 (compileSdk 36 -> 37, targetSdk 保持 36)

方法: 临时把 `version.properties` 的 `COMPILE_SDK_VERSION` 改为 `37`, 以 `--offline --continue` 运行 `:app:compileAppDebugKotlin :app:compileAppDebugJavaWithJavac compileDebugKotlin compileDebugJavaWithJavac` (覆盖 app 的 app flavor 与全部 modules / plugin-api / test-apps / plugins 子项目), 结束后 `git checkout -- version.properties` 还原并以相同任务重新编译 36 作为告警基线. 工具链: Gradle 9.7.1, AGP 9.4.0, Kotlin 2.4.0, KSP 2.3.12 (由 `autojs6-platform-versions` 1.8.0 选定, 未改动), 平台 `platforms;android-37.0` rev 2.

| 项目 | compileSdk 36 (基线) | compileSdk 37 (探针) |
| --- | --- | --- |
| 结果 | BUILD SUCCESSFUL, 8m 29s, 464 个任务 | BUILD SUCCESSFUL, 12m, 464 个任务 (260 执行) |
| Kotlin 编译错误 | 0 | 0 |
| javac 告警 | 0 | 0 |
| Kotlin 告警 (去重) | 28 | 29 |
| 仅出现于 37 的告警 | - | `app/src/main/java/org/autojs/autojs/ui/edit/keyboard/SymbolsSettingsActivity.kt:1110` `'static field SHOW_IMPLICIT: Int' is deprecated` (`InputMethodManager.SHOW_IMPLICIT` 于 API 37 弃用) |
| 仅出现于 36 的告警 | 无 | - |

结论: compileSdk 37 不引入编译错误; android.jar 37 相对 36 对本仓库只新增 1 处弃用告警. 未运行 lint 与单元测试 (留给 P1). inrt flavor 未单独编译 (与 app flavor 共用源码集, 仅 BuildConfig / manifest 占位符不同).

## 2. 平台兼容性变更清单 (API 37 模拟器)

方法: 启动 `AVD_API_37` (`system-images;android-37.0;google_apis_ps16k;x86_64`, Android 17, `ro.build.version.sdk=37`), 执行 `adb shell dumpsys platform_compat`, 全文保存为 [android-sdk-37-platform-compat-api37-20260913.txt](android-sdk-37-platform-compat-api37-20260913.txt) (393 行).

- `enableSinceTargetSdk=37` (即 targetSdk >= 37 才启用) 的变更共 36 项, 逐项影响评估见 Roadmap 附录 B.
- `enableSinceTargetSdk=36` 的 19 项 (`UNIVERSAL_RESIZABLE_BY_DEFAULT`, `DISABLE_OPT_OUT_EDGE_TO_EDGE`, `ENABLE_OVERLAY_TOUCH_PASS_THROUGH_OPT_IN_ENFORCEMENT`, `REMOVE_HIDDEN_SEND_INTENT_METHOD`, `LOCKDOWN_MEDIASTORE_VERSION` 等) 对当前 targetSdk 36 的宿主已经生效, 不属于本次升级的增量.
- `RESTRICT_LOCAL_NETWORK` (365139289) 在该镜像上为 `disabled` 且不带 targetSdk 门控: 本地网络限制在 Android 17 不经兼容性框架按 targetSdk 生效, 而是通过新的运行时权限 `android.permission.ACCESS_LOCAL_NETWORK` (targetSdk <= 36 的应用随 `INTERNET` 隐式授予, targetSdk >= 37 默认阻断). 该兼容开关仍可作为 Android 16 设备上的测试手段.
- 面向未来的门控: `NETWORK_BLOCKED_WITHOUT_INTERNET_PERMISSION` (`enableSinceTargetSdk=38`), `REQUIRE_TURN_SCREEN_ON_PERMISSION` / `MEDIA_CONTROL_MEDIA3_ACTIONS` / `ENFORCE_THREAD_CHECKS_ON_VIEW_ROOT_IMPL_APIS` / `VIRTUAL_MOUSE_CURSOR_POTION_IN_LOGICAL_COORDINATES` (`10000`, 尚未指定版本).

## 3. 环境事实

- 本机 SDK (`E:\.android\sdk`): `platforms/android-37.0` (Platform.Version=17, rev 2, ExtensionLevel 22, 非预览), `android-36.1`, `android-36`; `build-tools/37.0.0`; 系统镜像 `android-37.0;google_apis_ps16k` (x86_64, rev 6) 与 `android-CinnamonBun;google_apis_ps16k`; AVD `AVD_API_37`, `aj6-ps16k-37-x86_64` (均为 `android-37.0` 16 KB 镜像), `AVD_API_36.1`, `AVD_API_36.0_16K`.
- 接入真机 (`adb devices`): Xiaomi 23046RP50C (API 35), Sony G8441 (API 28), Sony XQ-AT72 (API 31), Sony XQ-DQ72 (API 33), Redmi 22120RN86C (API 33). 无 Android 17 真机.
- `autojs6-platform-versions` 数据表 `android-api-agp-compat.properties`: `37.0=9.1.1` (1.8.0 与 1.8.1 相同), 当前 AGP 9.4.0 满足.
- 先例: `AutoJs6-Plugin-Archive-Manager` 自 2026-08-23 (`bd329c4`) 起 `COMPILE_SDK_VERSION=37` / `TARGET_SDK_VERSION=36`, 2026-09-12 的 release APK `aapt2 dump badging` 显示 `compileSdkVersion='37' compileSdkVersionCodename='17' targetSdkVersion:'36'`.
- 宿主上一次 SDK 升级提交 `2ab13f3c7` (2025-09-26, 35 -> 36): 24 个文件, 主要是 android.jar 删除 / 弃用 API 的编译修正 (`Thread.stop` 系列, `Locale(String)` 构造器, `@Suppress("DEPRECATION")`), 版本目录迁移, changelog `dependency` 一条 "升级 Android SDK 版本 35 -> 36".
