# P7.2 关联项目同步: 文档、d.ts 与 Ace 编辑器插件 (2026-09-18)

对应 [apk-size-reduction-roadmap.md](../apk-size-reduction-roadmap.md) P7.2. 宿主自身无代码改动; 本文记录三个关联仓库的提交与验证, 供维护者发布 / 推送时核对.

## 结论

| 仓库 | 提交 | 版本 | 内容 |
| --- | --- | --- | --- |
| AutoJs6-Documentation (`master`) | `55dad29` | 文档版本随宿主 6.8.0, 未全量发布 | `api/console.md` 与 `api/global.md` 改为描述内置日志器; 只重新生成这两页 |
| AutoJs6-TypeScript-Declarations (`main`) | `9affdc0` | 4.16.0 -> 4.17.0 | 三个日志代理改继承 `org.autojs.autojs.core.console.log.*`, `aj6dts -Publish` 重新生成主应用 / 资源 / 三方库声明, `libraries.d.ts` 不再声明宿主已移除的库 |
| AutoJs6-Plugin-Ace-Editor | `2266bcc` | 1.9.0 (101) -> 1.10.0 (102) | 内置声明同步到 4.17.0, 10 语言 changelog, LSP 聚合声明重新生成 |

未做 (维护者事项): `@sm003/autojs6-dts` 的 npm 发布, 三个仓库的推送, AutoJs6-Plugin-Offline-Docs 的离线文档同步 (`auto-generate.py --sync-offline` 需要全量生成, 留待下一次文档发布).

## 文档 (`55dad29`)

- `api/console.md` `setGlobalLogConfig`: `filePattern` 默认值由 `'%d - [%p::%c::%C] - %m%n'` 改为实际的 `'%m%n'` (`Console.kt` 历来如此), 说明改为本实现支持的 PatternLayout 子集 (`%d` / `%d{fmt}` 含 `ISO8601` / `ABSOLUTE` / `DATE`, `%p`, `%c{n}`, `%C{n}`, `%m`, `%n`, `%t`, `%r`, `%%`, 位置转换符输出 `?`, `%x` / `%X{key}` 输出空串, 宽度修饰, 未知转换符原样输出), 并加一句 6.8.0 起由 `org.autojs.autojs.core.console.log` 实现且选项不变.
- `api/global.md` 类代理清单: `LogConfigurator` 从 "AndroidX 及第三方类代理" 移出, "OkHttp 和 Log4j 类代理" 改为 "OkHttp 类代理", 新增 "日志类代理" 一节列出 `Level` / `LogConfigurator` / `LogManager` 并注明对应内置包.
- 生成: 在 `generator/` 下 `python auto-generate.py console global`, 只更新 `docs/console.html`, `docs/global.html`, `json/console.json`, `json/global.json` (6 个文件, +21 / -11 行); 未向宿主 `assets-app/docs/` 复制.

## d.ts (`9affdc0`, 4.17.0)

- 手工: `aj6-int-init.d.ts` 的 `Level` / `LogConfigurator` / `LogManager` 基类改为 `org.autojs.autojs.core.console.log.*`; `aj6-int-console.d.ts` 的 `disableGlobalLog` 示例与 `setGlobalLogConfig` 源码摘要改用代理 `new LogConfigurator()` / `Level.OFF` / `Level.valueOf(...)`, 不再出现 `Packages['de.mindpipe.android.logging.log4j']` 与 `org.apache.log4j`.
- 生成: `cd D:/idea-projects/android-dts-generator && ./aj6dts.bat -Publish -DeclarationsOutput <repo>/declarations -ApiRoots <repo>/declarations/autojs6` (宿主 appDebug 已是最新, 47 秒; ace profile 选取宿主 455 / 9,142 类, 资源 20 / 20, 三方库 742 / 23,633 类; 生成器自带 tsc 校验通过).

| 文件 | 变化 |
| --- | --- |
| `declarations/libraries.d.ts` | 26,864 -> 25,081 行; `org.apache.log4j`, `de.mindpipe`, `com.fasterxml`, `org.kohsuke`, `androidx.media3`, `org.spongycastle`, `commons.lang3`, `commons.io`, `flexmark`, `org.junit` 均为 0 处引用; `kotlin.reflect` 187 -> 20 (标准库 `KClass` 等类型引用), `com.google.common` 667 -> 1 与 `org.jline` 4 只剩溯源注释行 |
| `declarations/autojs6/aj6-main-app.d.ts` | +275 行: 新增 `org.autojs.autojs.core.console.log` 包 (Appender / Level / LogConfigurator / LogEvent / Logger / LoggerRepository / LogManager), `ConsoleTimeSettings`, 以及宿主 5282 的邮件插件契约类 |
| `declarations/autojs6/aj6-resources.d.ts` | +260 行: P6.6 删除的 `keys_ace_editor_font` / `values_ace_editor_font` 与 5 个 dimen 消失, 新增设备插件偏好、版本历史差异色等资源 ID |

- 校验: `tsc 5.1.3 --noEmit --skipLibCheck --strictNullChecks --target ES2020 --lib ES2020 declarations/autojs6/index.d.ts docs/smoke/*.ts` 退出 0.
- 版本: `package.json` `version` 与自依赖 `@sm003/autojs6-dts` 4.16.0 -> 4.17.0; `README.md` 版本历史保留最近 3 版 (新增 v4.17.0, 移除 v4.14.0); `docs/CHANGELOG.md` 顶部加 v4.17.0 (三条 `更新`).

## Ace 编辑器插件 (`2266bcc`, 1.10.0 / build 102)

- `-Publish` 直接写入 `app/src/main/assets/editor/ace-builds-1.4.12/autojs6/types/` 的 `aj6-main-app.d.ts`, `aj6-resources.d.ts`, `libraries.d.ts`; `aj6-int-init.d.ts` 与 `aj6-int-console.d.ts` 手工复制 (该目录为 LF, d.ts 仓库为 CRLF, 复制后 `sed -i 's/\r$//'`; 其余 `aj6-int-*.d.ts` 与 d.ts 仓库仅行尾不同, 内容一致).
- `types/autojs6/package.json` (`version` 与依赖) 与 `BUNDLED_DECLARATIONS.md` 改为 4.17.0; `version.properties` VERSION_BUILD 101 -> 102, VERSION_NAME 1.9.0 -> 1.10.0; `.changelog/lang_*.json` 新增 `v1.10.0` (`improvement` 1 条, 10 语言), `PYTHONIOENCODING=utf-8 python .python/generate_markdown.py` 生成 25 个文件, README 与 `.readme/*.md` 的 `adb install` 行改为 1.10.0.
- `./gradlew :app:generateAutoJs6LspDeclarations --offline` 通过 (main-app 1,082,506 字节, 输出在 `app/build/generated/aceLspAssets/`, 不入库).

## 备注

- 宿主侧 `aj6dts` 会构建 appDebug (本次因产物最新只花 47 秒), 与其它会话的 Gradle 运行需错开.
- 三个仓库均只提交本次触及的文件; 生成器溯源文件 `declarations/.generated/` 保持未跟踪.
