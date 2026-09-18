# P3.4 构建警告清理: `notice.config` 的第 7 处 kotlin-reflect 调用点与 K2 `when` 误报 (2026-09-19)

对应 [apk-size-reduction-roadmap.md](../apk-size-reduction-roadmap.md) P3.4 (P3 kotlin-reflect 移除的追加项). 起因是维护者列出的 7 条 Kotlin 编译警告:

```text
w: .../engine/NodeMediaStore.kt:408:13 'when' expression over a subject of type 'Any?' is not exhaustive. Add a 'null' or 'else' branch.
w: .../runtime/api/augment/notice/Notice.kt:252:52 Call uses reflection API which is not found in compilation classpath. Make sure you have kotlin-reflect.jar in the classpath.
w: .../runtime/api/augment/notice/Notice.kt:253:91 (同上)
w: .../runtime/api/augment/notice/Notice.kt:259:40 (同上)
w: .../runtime/api/augment/notice/Notice.kt:259:73 (同上)
w: .../runtime/api/augment/notice/Notice.kt:266:32 (同上)
w: .../runtime/api/augment/notice/Notice.kt:266:39 (同上)
```

## 结论

- `Notice.kt` 的 6 条警告不是"噪音": `augment/notice/Notice.kt` 的 `config()` 是 P3 盘点漏掉的第 7 处 kotlin-reflect 调用点 (`KClass.members` / `KCallable.call` / `KMutableProperty1.setter.call`). 它只 `import kotlin.reflect.KMutableProperty1` (stdlib 自带的接口), 不含 `kotlin.reflect.full`, 所以 P3.1 按 `kotlin.reflect.full` 的盘点没有命中; K2 在 classpath 无 kotlin-reflect 时只给警告而非错误. 后果是 P3 (`9fc905eb5`, 2026-09-18) 之后的每个构建里 `notice.config({...})` 一调用即抛 `KotlinReflectionNotSupportedError` (真机复现见下), 该版本尚未发布, 无用户可见影响.
- 修复: 新增 `util/BeanProperties.kt` (Java 反射按 JVM 访问器解析公开实例属性), `config()` 改用它; `Notice.Companion.Config` 新增 `pristine()` 提供重置值. 顺带修正了原实现中"`null` / `undefined` 重置"从未生效的逻辑 (原代码在 `Default` object 中按 key 原名查找, 而 `Default` 常量是 SCREAMING_CASE, 永远查不到, 于是对可空属性写入 null, 对非空 Boolean 开关则会抛 `IllegalArgumentException`).
- `NodeMediaStore.kt:408` 是 Kotlin 2.4.0 K2 检查器 `FirJavaWhenExhaustivenessWarningChecker` (诊断名 `UNEXHAUSTIVE_WHEN_BASED_ON_JAVA_ANNOTATIONS`) 的误报: 该 `when` 有 `null, JSONObject.NULL ->` 与 `else ->` 分支, 但检查器只承认"单条件 `null ->`"或"`is T?`"形式的分支, 对多条件分支与 `else` 均视而不见. 修复只把主语显式声明为 `Any?` (去掉 `JSONObject.opt` 的平台类型), 语义不变.
- 修复后 `:app:compileAppDebugKotlin --rerun` (从头编译, 不带 `-q`) 与 `:app:assembleAppRelease` 均无 `w:` 行.
- 证据等级 E3: JVM 单测 + Sony G8441 API 28 + Redmi 22120RN86C API 33 + AVD API 24 脚本冒烟 (修复前后对照).

## 1. `notice.config` 与 kotlin-reflect

### 修复前 (真机复现)

Sony G8441 (API 28), 宿主 6.8.0 build 5281 (2026-09-18 15:29 安装, 即 P6.6 之后的构建), 探针 `build/tmp-size/notice-config-probe.js` (不重装直接运行):

```text
ERR config presets !! InternalError: Failed to call method "notice.config". Kotlin reflection implementation is not found at runtime. Make sure you have kotlin-reflect.jar in the classpath.
kotlin.jvm.KotlinReflectionNotSupportedError: ... (/storage/emulated/0/notice-config-probe.js#28)
OK  notice with presets -> {"id":900626265,"title":"null","text":"probe-content-1","channel":"notice-config-probe.js"} fixedIdMatches=false
ERR reset presets with null / undefined !! (同上, #38)
OK  unknown key is rejected -> rejected: ... Kotlin reflection implementation is not found at runtime ...
OK  private field is rejected -> rejected: ... Kotlin reflection implementation is not found at runtime ...
OK  type mismatch is rejected -> rejected: ... Kotlin reflection implementation is not found at runtime ...
OK  non-object argument is rejected -> rejected: ... Argument config x (String) must be a JavaScript Object for notice.config
ERR presets still work after the rejections !! (同上, #64)
ERR final reset !! (同上, #70)
```

任何带 key 的 `notice.config` 调用都在 `rtConfig::class.members` 处抛错 (异常发生在参数校验之前, 所以三个"应拒绝"的用例也只是因反射错误而"被拒绝"); 只有非对象参数走到了原有的 `require`. 预设未生效, 通知落在默认渠道 (脚本名), 标题为空, id 为动态值.

### 改动

- 新增 `app/src/main/java/org/autojs/autojs/util/BeanProperties.kt`: `find(type, name)` 按 Kotlin 属性到 JVM 访问器的映射查找公开实例属性 (`foo` -> `getFoo()` / `setFoo(v)`; Boolean `isFoo` -> `isFoo()` / `setFoo(v)`), 只接受 public 非 static 非 synthetic 非 bridge 的方法, 名字含 `$` 的 (`internal` 名字改编) 与空名直接返回 null; `Property.get` / `set` 经 `Method.invoke`, `InvocationTargetException` 解包为原异常, 只读属性 `set` 抛 `IllegalStateException`. 与 P3 的 `util/CompanionProperties.kt` 同属"Java 反射替代 kotlin-reflect"的工具.
- `runtime/api/Notice.kt`: `Config` 新增 `internal fun pristine() = Config(scriptRuntime)`, 供重置时读取"全新配置"的取值 (`Default.USE_*` / `ENABLE_*` 开关为 true, 其余预设为 null, `defaultChannelId` 回到脚本名).
- `runtime/api/augment/notice/Notice.kt` `config()`: `rtConfig::class.members.find { it.name == key }` -> `BeanProperties.find(rtConfig.javaClass, key)`; `configProperty.call(rtConfig)` -> `get(rtConfig)`; 空值重置改为 `configProperty.get(pristine)` (`pristine` 为 lazy, 只在需要时构造一次); `configProperty is KMutableProperty1<*, *>` -> `isMutable`; `setter.call` -> `set`. 校验顺序与错误消息 (`Argument key "..." is invalid for notice.config`, `Property config.<key> for notice must be mutable`) 不变; 私有字段 (`privateDefaultChannelId`) 与 `internal` 成员照旧不可见.
- 行为差异 (均为修复): (1) `null` / `undefined` 现在真正重置为初始值, 包括三个非空 Boolean 开关; (2) 类型不匹配 (如 `useDynamicDefaultNotificationId: "yes"`) 由 `Method.invoke` 抛 `IllegalArgumentException` (`argument 1 should have type boolean, got java.lang.String`), 原实现下是 kotlin-reflect 的 `IllegalArgumentException`, 同为脚本侧的 `InternalError`.

### JVM 单测

- 新增 `app/src/test/java/org/autojs/autojs/util/BeanPropertiesTest.kt` 5 个用例: 公开 `var` 的 getter / setter (含 `String?`, 原生 `boolean`, `Any?`); Boolean `isReady` 以原名作 getter 且 `ready` 不可见; 自定义访问器与只读属性; 私有 / `internal` (名字改编) / 非属性方法 / `getWithArgument` / 空名 / `title$app` / `Companion` 均不可见; 访问器异常解包与类型不匹配.
- 既有 `runtime/api/augment/notice/NoticeCompatibilityTest` 2 个用例不变.
- `:app:testAppDebugUnitTest` 全量: 2,990 个用例 0 失败 (5 个跳过, 491 个结果文件), 编译阶段 (`--rerun`, 不带 `-q`) 零 `w:` 行.

### 修复后 (三台设备)

宿主 6.8.0 build 5282 (`app/build/outputs/apk/app/release/autojs6-v6.8.0-universal.apk`), `bash build/tmp-size/notice_config_run.sh <serial> install`:

探针步骤: 设置 `{ defaultTitle: "PROBE-TITLE", useDynamicDefaultNotificationId: false, defaultIsSilent: true, defaultChannelId: "probe_channel" }` 后 `notice("probe-content-1")`, 用 `NotificationManager.getActiveNotifications()` 读回 id / `android.title` / `android.text` / 渠道; 再以 `null` / `undefined` 重置四项后 `notice("probe-content-2")` 读回; 然后检查未知 key (`noSuchPreset`), 私有字段 (`privateDefaultChannelId`), 类型不匹配 (`useDynamicDefaultNotificationId: "yes"`), 非对象参数 (`"x"`) 四种拒绝; 最后设置 `defaultContent` / `defaultTitle` 并以显式 title 发通知, 再全部重置.

| 设备 | 结果 |
| --- | --- |
| Sony G8441 (API 28) | 10/10: 预设生效 (title = `PROBE-TITLE`, 渠道 `probe_channel`, id = `"script_notification".hashCode()` = 654640095); 重置后 title 为空, 渠道回到脚本名 `notice-config-probe.js`, id 回到动态值; 未知 key 与私有字段按 `Argument key "..." is invalid for notice.config` 拒绝, 类型不匹配按 `setUseDynamicDefaultNotificationId` 的参数类型错误拒绝, 非对象参数按原 `require` 拒绝; 之后 `defaultContent` 预设生效且显式 title 覆盖 `defaultTitle`; 最终重置成功 |
| Redmi 22120RN86C (API 33) | 10/10, 与 Sony 一致 (当日为全新安装, 需先 `appops set org.autojs.autojs6 MANAGE_EXTERNAL_STORAGE allow`, 否则 `RunIntentActivity` 读不到 `/sdcard` 下的脚本, 无任何输出) |
| AVD API 24 (x86) | 10/10, 与 Sony 一致 (API < 26 无渠道字段; 全新安装需先 `pm grant` READ / WRITE_EXTERNAL_STORAGE) |

原始输出: `build/tmp-size/notice_config_before_sony.txt`, `notice_config_after.txt` (Sony), `notice_config_after2.txt` (Redmi + AVD).

## 2. `NodeMediaStore.kt:408` 的 K2 误报

用 `kotlin-compiler-embeddable` 2.4.0 独立编译 (`build/tmp-size/k2probe/`, `-Xrender-internal-diagnostic-names`, classpath 含 android.jar API 37) 复现并做变体矩阵:

| 变体 (主语 `val value = filter.opt(key)`, 平台类型) | 是否告警 |
| --- | --- |
| 原样: `null, JSONObject.NULL ->` + `is JSONArray ->` + `is JSONObject ->` + `else ->` | 告警 |
| 拆成单独的 `null ->` 与 `JSONObject.NULL ->` 分支 | 不告警 |
| 只有 `null ->` + `else ->` | 不告警 |
| 只有 `else ->` | 告警 |
| `JSONObject.NULL ->` + `else ->` (无 `null` 分支) | 告警 |
| `is JSONArray ->` + `else ->` | 告警 |
| 原样但 `when` 作表达式赋值 | 告警 |
| 无主语 `when { value == null \|\| value == JSONObject.NULL -> ... }` | 不告警 |
| 主语显式 `val value: Any? = filter.opt(key)` (采用) | 不告警 |
| 主语为 `Any?` 形参 (无平台类型) | 不告警 |

`javap -c` 读 `org.jetbrains.kotlin.fir.analysis.jvm.checkers.expression.FirJavaWhenExhaustivenessWarningChecker` 可见其逻辑: 仅当 `when` 已判定为穷尽时运行; 取主语的 `enhancedTypeForWarning` (由 Java 侧的告警级可空注解得到, android.jar 37 的 `JSONObject.opt` 带 `androidx.annotation.RecentlyNullable`), 若其可为 null, 则遍历分支, 只把"`FirEqualityOperatorCall` 且参数类型为 `Nothing?`" (即单条件 `== null`) 或"`FirTypeOperatorCall` `is` 且目标类型可空"计为 null 分支; 多条件分支是 `FirBinaryLogicExpression`, `else` 是 `FirElseIfTrueCondition`, 两者都不计, 于是报 `UNEXHAUSTIVE_WHEN_BASED_ON_JAVA_ANNOTATIONS`. 2026-09-19 未检索到该诊断的公开 issue (2.4.0 新增). 修复采用显式 `Any?` 并加注释说明, 避免日后被当作冗余类型标注删掉.

## 3. 体积

本项不以体积为目标. 当前 universal 31,711,892 字节, 对 P6.6 快照 (31,595,920) +115,972 字节 (+0.11 MiB, 全部在 dex, `org.autojs.autojs` +0.12 MiB), 来自 P6.6 之后已合入的 mail 脚本 API 等提交与本次新增的一个小工具类; 未达 P7.1 门禁阈值. 报告留在 `build/tmp-size/warn-size.{json,md}`, 不进入 evidence.

## 4. 文件

- `app/src/main/java/org/autojs/autojs/util/BeanProperties.kt` (新增)
- `app/src/test/java/org/autojs/autojs/util/BeanPropertiesTest.kt` (新增)
- `app/src/main/java/org/autojs/autojs/runtime/api/Notice.kt`
- `app/src/main/java/org/autojs/autojs/runtime/api/augment/notice/Notice.kt`
- `app/src/main/java/org/autojs/autojs/engine/NodeMediaStore.kt`
- 探针 (不入库): `build/tmp-size/notice-config-probe.js`, `notice_config_run.sh`, `k2probe/probe*.kt`
