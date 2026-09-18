# P6.2 log4j 替换为自有滚动文件日志器 (2026-09-18)

对应 [apk-size-reduction-roadmap.md](../apk-size-reduction-roadmap.md) P6.2 (决策 D12 / D17), 体积报告见 [apk-size-p6-2-20260918.md](apk-size-p6-2-20260918.md).

## 结论

- `log4j:log4j:1.2.17` (5 个已知 CVE) 与 `de.mindpipe.android:android-logging-log4j:1.0.3` 已从宿主删除, 由新增包 `app/src/main/java/org/autojs/autojs/core/console/log/` 提供同等能力.
- 脚本可见行为不变: `console.setGlobalLogConfig` 全部文档化选项 (file / maxFileSize / maxBackupSize / rootLevel / filePattern / immediateFlush / resetConfiguration), `console.resetGlobalLogConfig`, 以及 `global.md` 列出的三个全局代理类 `LogConfigurator` / `Level` / `LogManager` (D17: 同名, 自有实现, 覆盖文档化用法).
- universal APK 33,312,764 -> 33,136,742 字节 (-176,022 字节 = -0.17 MiB, 对基线 -8,009,313 字节 = -7.64 MiB, -19.5%), DEX 定义方法 310,704 -> 308,453, `org.apache.log4j` 归属 0.23 MiB -> 0.
- 证据等级 E3: JVM 单测 + AVD API 24 + Sony G8441 API 28 + Redmi 22120RN86C API 33 脚本冒烟.

## 改动

### 新增 `core/console/log/`

| 文件 | 内容 |
| --- | --- |
| `Level.kt` | log4j 1.2 同名同序的级别 `OFF` / `FATAL` / `ERROR` / `WARN` / `INFO` / `DEBUG` / `TRACE` / `ALL` (`@JvmField` 静态字段, 脚本 `Level.OFF` 与 `Level["ERROR"]` 均可用), `toLevel(name[, default])` (未知回落 DEBUG, 同 log4j), `toLevel(int)`, `valueOf(name)` (严格, 供 console API, 未知名称报 `IllegalArgumentException` 并列出可选值), `isGreaterOrEqual`, `toInt`. |
| `PatternLayout.kt` | `LogEvent` 与 log4j `PatternLayout` 子集: `%d` / `%d{ISO8601 \| ABSOLUTE \| DATE \| 自定义 SimpleDateFormat}` (非法自定义格式回落 ISO8601, `Locale.ROOT`), `%p`, `%c` / `%c{n}`, `%C` / `%C{n}`, `%m`, `%n`, `%t`, `%r`, `%%`, `%F` `%l` `%L` `%M` 输出 `?` (不查调用栈), `%x` / `%X{key}` 输出空; 宽度修饰 `%-5p` / `%5p` / `%.3p` / `%5.3p` (截断保留尾部, 同 log4j); 未知转换符与末尾孤立 `%` 原样输出. |
| `Appenders.kt` | `Appender` 接口; `RollingFileAppender`: 追加模式打开, 自动建父目录, UTF-8 字节计数 (打开时从现有文件长度起算), 写入后达到 `maximumFileSize` 即分卷 (`file.N` 删除, `file.i` -> `file.i+1`, `file` -> `file.1`, 新建空文件; `maxBackupIndex` 0 时清空), `immediateFlush=false` 时 8 KiB 缓冲直到 `flush` / 分卷 / `close`; 写失败只记 logcat 不抛出; 关闭后丢弃事件. `LogCatAppender`: 标签 `%c`, 消息按 `logCatPattern`, TRACE / DEBUG / INFO / WARN / ERROR+FATAL -> `Log.v` / `d` / `i` / `w` / `e`. |
| `LogManager.kt` | `Logger` (`log` / `trace` / `debug` / `info` / `warn` / `error` / `fatal` / `isEnabledFor`, 可设自身 `level`), `LoggerRepository` (根级别默认 DEBUG, 按名缓存日志器, appender 列表, `resetConfiguration` 关闭全部 appender 并恢复根级别与各日志器级别, 无 appender 时不构造事件), `LogManager` 单例 (`getLoggerRepository` / `getRootLogger` / `getLogger(name \| Class)` / `resetConfiguration` / `shutdown`). |
| `LogConfigurator.kt` | 表面与默认值同 android-logging-log4j 1.0.3: `fileName` `android-log4j.log`, `filePattern` `%d - [%p::%c::%C] - %m%n`, `logCatPattern` `%m%n`, `maxBackupSize` 5, `maxFileSize` 512 KiB, `immediateFlush` / `useLogCatAppender` / `useFileAppender` / `resetConfiguration` true, `rootLevel` DEBUG, `internalDebugging` false; 5 个构造器, `configure()` (按需重置 -> 文件 appender -> logcat appender -> 根级别), `setLevel(loggerName, level)`. 脚本经 `cfg.setFileName(...)` 等 setter 与 `isImmediateFlush()` 等 getter 访问. |

包名刻意不用 `org.apache.log4j` / `de.mindpipe...`, 避免漏洞扫描器按类名误报.

### 改接

- `core/console/GlobalConsole.kt` 与兼容层 `com/stardust/autojs/core/console/GlobalConsole.java`: `Logger.getLogger(GlobalConsole::class.java)` 改为自有 `Logger`, `toLog4jLevel` 改名 `toLogLevel` (Android `Log` 级别 -> `Level` 映射不变: VERBOSE / DEBUG -> DEBUG, INFO, WARN, ERROR, ASSERT -> FATAL).
- `runtime/api/augment/console/Console.kt`: `setGlobalLogConfig` 改用自有 `LogConfigurator`, `rootLevel` 由反射 `Level` 字段改为 `Level.valueOf(大写名称)`, 未知级别报错而非 `NoSuchFieldException`; 默认 `file` `android-log4j.log` (相对脚本目录), 默认 `filePattern` `%m%n`, 512 KiB / 5 备份不变. `resetGlobalLogConfig` 改调自有 `LogManager.getLoggerRepository().resetConfiguration()`.
- `runtime/api/augment/global/Classes.kt`: `LogConfigurator` / `Level` / `LogManager` 三个代理指向自有类.
- `app/build.gradle.kts`: 删除 `implementation(libs.log4j)` / `implementation(libs.android.logging.log4j)` 与 2024-08-14 的 CVE FIXME 注释块; `gradle/libs.versions.toml`: 删除 `log4j` / `android-logging-log4j` 版本与库条目.
- `assets/modules/obsolete/__console__.js` 仍引用 `org.apache.log4j.*` FQN, 但该目录在 `build.gradle.kts` 的资产裁剪列表中 (不进 APK) 且被 IDE 排除, 不改.

### 未提供的 log4j 能力

`Packages.org.apache.log4j.*` / `Packages['de.mindpipe.android.logging.log4j'].*` 不再存在 (d.ts `aj6-int-console.d.ts` 示例与 `aj6-int-init.d.ts` 三个 `declare class` 的基类需随 P7.2 同步); `Logger.addAppender` / 自定义 `Appender` / `log4j.properties` / MDC / NDC / `%F` `%L` 等调用位置信息不提供. 未调用 `setGlobalLogConfig` 时不落盘 (与 log4j 未配置时只打 "no appenders" 警告的行为等价).

## 验证

### JVM 单测 (`app/src/test/java/org/autojs/autojs/core/console/log/`, 23 项)

- `PatternLayoutTest` (7): 默认 `%m%n`; 旧默认文件格式逐字符匹配 `1970-01-01 01:02:03,456 - [INFO::org.autojs...GlobalConsole::org.autojs...GlobalConsole] - hello`; `%d` 具名 / 自定义 / 非法回落; `%p` `%t` `%r`; `%c{n}` / `%C{n}` 精度; 宽度修饰; `%%` / 未知转换符 / 孤立 `%` / `%F` `%L` `%X{}` `%x`.
- `RollingFileAppenderTest` (6): 追加与建目录; 20 字节阈值 6 行 -> `file` 空, `file.1` = 5-6 行, `file.2` = 3-4 行, 无 `file.3`; 现有字节 + UTF-8 中文计数触发分卷; `maxBackupIndex` 0 清空; `immediateFlush=false` 缓冲到 `flush` / `close`; 关闭后丢弃.
- `LogConfiguratorTest` (10): 默认值同 android-logging-log4j; 根级别过滤 + `%c{1}`; console 风格配置 (`%m%n`, 12 字节阈值, 1 备份, `Level.valueOf("ALL")`) 分卷; `OFF` 全静默; `resetConfiguration` 关闭 appender / 恢复根级别 / 清日志器级别; `resetConfiguration=false` 叠加 appender; 日志器级别覆盖根级别 (`setLevel` 需在 `configure()` 之后, 同 log4j); `useFileAppender=false`; `Level` 查找与序; 日志器按名缓存.
- `:app:testAppDebugUnitTest` 全量: 2880 项, 0 失败, 5 跳过 (P6.1 时 2857 + 本次 23).

### 脚本冒烟 (`build/tmp-size/p6-2-smoke.js`, 三环境全部 OK)

| 环境 | 结果 |
| --- | --- |
| AVD API 24 (x86 APK) | 全部 OK |
| Sony G8441 API 28 (arm64 APK) | 全部 OK |
| Redmi 22120RN86C API 33 (arm64 APK) | 全部 OK |

检查项: `org.apache.log4j.Logger` 与 `de.mindpipe...LogConfigurator` 不可加载, 自有 `LogConfigurator` 可加载; `setGlobalLogConfig({ file, filePattern: '%m%n', maxFileSize: 160, maxBackupSize: 2, rootLevel: 'info' })` 后 `console.verbose` / `log` 被过滤, `info` / `warn` / `error` 带 `HH:mm:ss.SSS/I: ` 前缀落盘; 12 行填充后 `plain.log` 0 字节, `plain.log.1` 192, `plain.log.2` 190, 无 `.3`, 最早行已轮出; `resetGlobalLogConfig` 后文件不再变化; `setGlobalLogConfig({})` 写脚本目录 `android-log4j.log` 且为 `%m%n`; `filePattern '%d{yyyy-MM-dd} [%-5p] %c{1}: %m%n'` 输出 `2026-09-18 [WARN ] GlobalConsole: 11:13:12.688/W: p62-patterned`; `new LogConfigurator()` + setter + `configure()`, `LogManager.getLogger('smoke.Probe').warn` / `getRootLogger().error` 按 `%d{HH:mm} %-5p %c{1} - %m%n` 落盘, `info` 被 WARN 根级别过滤, `getLoggerRepository().resetConfiguration()` 后不再写; `Level.toLevel('info') === Level.INFO`, `Level['ERROR']`, `String(Level.OFF)`, `isGreaterOrEqual`, 未知名称回落 DEBUG, `toInt()`; `rootLevel: 'verbose'` 报 `Unknown log level: VERBOSE (expected one of OFF, FATAL, ERROR, WARN, INFO, DEBUG, TRACE, ALL)`.

### 体积 ([apk-size-p6-2-20260918.md](apk-size-p6-2-20260918.md))

| 指标 | P6.1 | P6.2 | 差 |
| --- | ---: | ---: | ---: |
| universal 字节 | 33,312,764 | 33,136,742 | -176,022 |
| arm64-v8a 字节 | 32,115,738 | 31,939,716 | -176,022 |
| DEX 定义方法 | 310,704 | 308,453 | -2,251 |
| `org.apache.log4j` 归属 MiB | 0.23 | 0 | -0.23 |
| 对基线 (41,146,055) | -7.47 MiB | -7.64 MiB (-19.5%) | |

## 后续

- P7.2: 文档 `console.md` 的 `filePattern` 默认值写为 `'%d - [%p::%c::%C] - %m%n'` 而 `console.setGlobalLogConfig` 实际默认为 `%m%n` (历史即如此), PatternLayout 链接可改为指向本实现支持的子集; `global.md` "OkHttp 和 Log4j 类代理" 一节的三个代理改为自有实现说明; d.ts `aj6-int-init.d.ts` 的 `Level` / `LogConfigurator` / `LogManager` 基类与 `aj6-int-console.d.ts` 示例改为 `org.autojs.autojs.core.console.log.*`.
- `tools/size/apk_size_report.py` 的关注包列表保留 `org.apache.log4j` 以便报告持续显示 0.
