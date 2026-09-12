# Android SDK 37 Roadmap 执行条件与证据校正 (2026-09-13)

对应 [Roadmap](../android-sdk-37-roadmap.md). 本次从 `ad02beda5` 开始, 初始宿主工作区干净, SDK 配置仍为 compile 36 / app target 36 / inrt target 29. 维护者已确认的范围 (宿主 + 42 插件), 两步切换, inrt 目标 37, 探针授权全部保留.

本次完成静态源码复核、既有证据一致性核验与相关官方资料核验, 没有重新执行原探针或任何设备功能测试. 文档主页面的缺项不被解释为平台没有变化; 新增 API 的版本与行为按具体来源确认.

## 1. 会直接影响执行的发现

| 发现 | 证据 | Roadmap 处理 |
| --- | --- | --- |
| vital lint 被禁用, 构建绿不代表检查完成 | [app/build.gradle.kts](../../../app/build.gradle.kts): `abortOnError=false`, 两个 `lintVitalAnalyze*Release` 的 `enabled=false`, 模板分发的 `onlyIf`; CI 还用 `-x` 排除 inrt analysis | P0.2 先落实有效入口; P1 要求实际执行与新生成报告, 统计 Error / Fatal |
| 安装器的 BAL 模式不是通用 sender 设置 | [AndroidPackageSessionInstaller.kt](../../../app/src/main/java/org/autojs/autojs/ui/main/scripts/AndroidPackageSessionInstaller.kt): `setPendingIntentCreatorBackgroundActivityStartMode`, `PendingIntent.getActivity`, `session.commit(callback.intentSender)`; 注释明确要支持界面离开前台后的确认回调 | P2.5 按 creator / sender / 目标类型分开核验, 不直接将 `ALLOW_ALWAYS` 替换成 `ALLOW_IF_VISIBLE` |
| 固定快捷方式成功回调是广播 token | [ShortcutUtils.kt](../../../app/src/main/java/org/autojs/autojs/util/ShortcutUtils.kt), [ShortcutManager.java](../../../app/src/main/java/org/autojs/autojs/external/shortcut/ShortcutManager.java) 使用 `PendingIntent.getBroadcast()` | 先判断是否启动 Activity, 不给每个 `IntentSender` 统一增加 BAL 授权 |
| 系统安装用户 CA 不等于本应用信任它 | [network_security_config.xml](../../../app/src/main/res/xml/network_security_config.xml) 的 base 仅 `system`, 特定域额外信任资源 CA; [RequestBuilder.kt](../../../app/src/main/java/org/autojs/autojs/runtime/api/augment/http/RequestBuilder.kt) 有单独 `isInsecure` TrustManager 路径 | P2.6 区分信任链失败 / CT / ECH / LNP, 调整 Q2 为默认保留平台策略、必要时局部处理 |
| inrt 启动只请求旧存储权限, 回调不判断 grantResults 就运行脚本 | [SplashActivity.kt](../../../app/src/main/java/org/autojs/autojs/inrt/SplashActivity.kt) 的 `checkPermission(WRITE_EXTERNAL_STORAGE)` 与 `onRequestPermissionsResult()` | P2.16 提前适配; P3.3 区分新安装 / 覆盖升级 / 重装 / 拒绝权限 |
| R8 插件 SDK 属性没有被 Android DSL 实际采用 | `D:/idea-projects/AutoJs6-Plugin-R8-Compiler/app/build.gradle.kts` 硬编码 compile / target 36, min 24, versionCode / versionName; 两个 plugin-api 模块硬编码 compile / lint target 36 | P5.3 单列统一版本来源与实际 APK 校验, 属性值不能作为完成凭据 |
| R8 插件捆绑的脚本编译平台库仍固定为 API 36 | 同仓 `app/build.gradle.kts` 的 `platforms/android-36/android.jar`, 资源 `r8-library/android-36.jar`, 校验摘要与文件大小 | 单独记录脚本编译 API 37 符号支持边界; 提升插件 compileSdk 不代表该资源自动更新 |
| 部分插件的有效 minSdk 与属性快照不同 | Paddle v4 / v5 / v6 的 `app/build.gradle.kts` 为 min 26; R8 app 为 min 24 | 附录 E 修正, 后续逐仓以 DSL / APK 为准, 不改变既有最低版本 |
| 原兼容命令不可按原样批量执行 | `am compat` 单次操作是一个 change 与一个 package; 原文有多个变更名并列、缺包名的示例 | 附录 C 改为 PowerShell 独立操作与逐项循环, 保存并恢复原 override |
| 原测试任务通配路径不可直接执行 | `.github/workflows/android.yml` 使用实际模块任务; `:plugin-api:*:testDebugUnitTest` 不是 Gradle 任务路径展开语法 | P1 使用根任务选择器并核验参与模块; 宿主全量测试不带 CI 的 `--tests` 过滤 |

上述均为 SOURCE 证据, 表示执行方案需要修正, 不表示对应的运行时问题已经修复.

## 2. 平台行为核验与边界

- VERIFIED: 官方 BAL 指南区分创建方与发送方授权, 新常量从 API 36 提供. 结合本地安装器的异步确认需求, 当前 `ALLOW_ALWAYS` 不能作机械替换. 这是对调用链的评估, 最终行为仍需 P2.5 设备验证. [Activity security](https://developer.android.com/guide/components/activities/secure-bal).
- VERIFIED: 自定义 CA 的信任配置与 CT 是不同机制, ECH 要求网络库采用相应能力. 安装用户 CA 后默认客户端能否受信任必须先看应用 NSC. [Network security configuration](https://developer.android.com/privacy-and-security/security-config).
- VERIFIED: 音频诊断的 `enable` / `throw` 会覆盖 target 版本与闹钟例外, 属设备级强制模式; 仅在该模式下的成功或失败不能代替默认行为验收. [Background audio hardening](https://developer.android.com/about/versions/17/changes/bg-audio).
- VERIFIED: 同一 profile 的回环与跨 profile 回环应分开讨论. Android 17 对跨 profile 回环增加独立限制, 不能以 LNP 的回环豁免保证所有宿主 / 插件安装组合可达. [All-app behavior changes](https://developer.android.com/about/versions/17/behavior-changes-all), [Android Enterprise 17](https://developer.android.com/work/versions/android-17).
- VERIFIED: `preserveLegacyExternalStorage` 的官方用途是旧安装的数据迁移, 不让首次安装 / 卸载重装继续避开分区存储. 29 -> 37 跨级升级的具体设备结果仍为 UNVERIFIED. [Storage migration](https://developer.android.com/training/data-storage/use-cases).
- VERIFIED: CJKV 输入事件增加更细的变更信息, 需覆盖 a11y 事件监听链路; 不能仅检查 `android.accessibilityservice` 包. [Targeted behavior changes](https://developer.android.com/about/versions/17/behavior-changes-17).
- VERIFIED: 自定义 WebView UA 功能仍存在, 但给缩减的默认 UA 拼接后缀不会恢复设备信息. 官网博客建议需要详细信息的页面评估 UA Client Hints. [WebView UA reduction](https://android-developers.googleblog.com/2024/12/user-agent-reduction-on-android-webview.html).
- VERIFIED: NPU 特性声明要求出现在官方总览, 虽然本次取得的目标版本主页面未展开该节; 需要使用 NPU 的插件仍需核验声明与回退. [Android 17 summary](https://developer.android.com/about/versions/17/summary).
- VERIFIED: `SHOW_IMPLICIT` 在 API 37 弃用. 旧系统仍需验证删除 flag 后的输入法语义, 不把 API 37 说明推广到所有受支持版本. [InputMethodManager reference](https://developer.android.com/reference/android/view/inputmethod/InputMethodManager#SHOW_IMPLICIT).

本次没有重新核验所有工具链发行日期、Play 2027 的具体截止日、QPR minor SDK、全部 API diff 与附录 B 每一项语义; 不把此复核当作这些主题的完整报告. 行为变更主页面、总览与专题文档可能有不同覆盖范围, 按具体条目保留来源和 UNVERIFIED 状态.

## 3. 既有证据校验

对 [platform_compat 原文](android-sdk-37-platform-compat-api37-20260913.txt) 读取与计数:

| 项目 | 结果 |
| --- | --- |
| dump 行数 | 393 |
| `enableSinceTargetSdk=37` | 36 项 |
| `enableSinceTargetSdk=36` | 19 项 |
| `ASM_RESTRICTIONS` | `230590090`, `enableSinceTargetSdk=37; overridable`, 未带 `disabled` |
| `RESTRICT_LOCAL_NETWORK` | `365139289`, `disabled`, 没有 target 门控字段 |

因此 `ASM_RESTRICTIONS` 的 "默认关闭" 不能由该 dump 支持. `overridable` 与默认开关状态不同; 指定包的真实行为还需设备验证. 36 项清单只涵盖此镜像中的该门控值, 不能当作全部 Android 17 变更.

读取本机 SDK 的 ZIP 条目 (Python `zipfile`, 未运行 Gradle):

| SDK jar | `android/hardware/fingerprint/*.class` |
| --- | --- |
| `E:/.android/sdk/platforms/android-36/android.jar` | 4 |
| `E:/.android/sdk/platforms/android-37.0/android.jar` | 0 |

API 36 中的四个类为 `FingerprintManager`, `FingerprintManager$AuthenticationCallback`, `FingerprintManager$AuthenticationResult`, `FingerprintManager$CryptoObject`. 这是本机 SDK 编译符号的差异证据 (SOURCE), 不证明 Android 17 设备运行时是否仍保留类实现.

`https://developer.android.com/about/versions/17/compat-framework-changes` 间隔重试共 3 次均返回工具的 Internal Error; 搜索未取得该页正文. 没有 HTTP 404 的可靠证据, 因此不标记为 404, 也不编造其完整变更解释 / change ID. 名称与 ID 继续引用入库 dump.

## 4. 文档验收与后续入口

- PASS: Roadmap 附录 B 的 36 项名称 / ID 与原始 dump 完全一致; 附录 E 为 42 个不重复且实际存在的插件仓库.
- PASS: 3 份文档的 UTF-8 解码、本地相对链接、代码块配对检查; 3 段 PowerShell 示例通过 PowerShell AST 语法解析, 未执行其中的 adb 命令.
- PASS: `git diff --check` 检查本次文档无空白错误; P0.2 / P1-P6 均未误勾为完成, 没有单条 `am compat` 携带多个 change 的示例.
- 已修正文档中的 lint 跳过条件、BAL 角色、网络安全前提、兼容命令、inrt 前置工作与插件配置例外.
- 保留 P0.2 / P1-P6 的未完成状态; 按原始任务范围继续实施时, 先完成 P0.2 的有效 lint 基线与 debug target 覆盖, 再执行 P1.
- 本次只产生开发文档, 不记作产品 fix / feature / dependency, 不修改 `.changelog` / README / SDK 版本 / 关联 API 声明.
- 会话期间出现的其它源码修改属于共享工作区的其它工作, 本次按显式文件清单只提交 Roadmap 和两份说明性证据文档.
