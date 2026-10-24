# P4.3 Bundled Libraries 插件与宿主插件模块源 (T11) 设计

- 状态: 设计已确认 (2026-10-24, 子会话 augment-p4-3): Q100-Q103 维护者 2026-10-24 均选推荐项 (1), 按此实施.
- 对应: roadmap P4.3, T11 (插件提供 JS 模块源), 附录 C Q5 / Q6 (已拍板迁出), P3.7 设计 `P3-7-require-design.md` (f) 节, P5.2 (删除 `assets/modules` 中的库与垫片).
- 两个仓库: 新建 `AutoJs6-Plugin-Bundled-Libraries` (本地提交, 不建远端), 宿主 `AutoJs6`.

## 0. 现状

- 宿主 `assets/modules` 中的 13 个可 require 名 (两个分组) 及其内部文件:

| 分组 | 名称 | 文件 (相对 `assets/modules`) | 约 |
|---|---|---|---|
| libraries | axios | `axios.js`, `axios/` (axios.min.js, browser-libs/*, utils/*, remark.md) | 89 KB |
| libraries | cheerio | `cheerio.js` | 612 KB |
| libraries | dayjs | `dayjs.js`, `dayjs/` (dayjs.min.js, locale.json, LICENSE, README.md, index.d.ts) | 45 KB |
| libraries | i18n | `i18n.js`, `banana-i18n.js` (i18n.js 内部 `require('banana-i18n')`) | 64 KB |
| libraries | pangu | `pangu.js`, `pangu.min.js`, `pangu.LICENSE.txt` | 25 KB |
| libraries | lodash | `lodash.js` (lodash core 4.17 custom build) | 16 KB |
| node-shims | buffer / events / fs / os / path / process / nodejs | 各自的 `<名称>.js` (fs 依赖 buffer / path, nodejs 依赖 process / buffer) | 共约 44 KB |

- 加载路径 (两态相同): 资产名经引擎的 Rhino Require (`RhinoJavaScriptEngine.initRequireBuilder` 的 provider 链: `ProcessWideAssetModuleScriptProvider` 然后 `SoftCachingModuleScriptProvider(AssetAndUrlModuleSourceProvider)`) 加载, 在 ModuleScope 中执行; 开态经 P3.7 `AssetModuleSource`, 关态经 jvm-npm.js 的 NativeRequire 回退. 资产内部的 require 写法有三种: 裸名 (`banana-i18n`, `process`), 带扩展名 (`pangu.min.js`), 带 `modules/` 前缀的路径 (`modules/axios/browser-libs/utils.js`).
- Global 的 `axios` / `cheerio` / `dayjs` / `i18n` / `pangu` getter: 两态都读 `ScriptRuntime.js_mod_*` (`by lazy { rhinoRequire(name) }`), 与 require 共用 Rhino Require 的模块缓存 (`require('axios') === axios`).
- lodash 与 7 个垫片是 jvm-npm 的 `builtInModules` (P3.7 的 `AssetModuleSource.CORE_FILES`): 工作目录下没有同名文件时直接交给命名模块源.

## (a) 插件清单 `scriptModules`

插件在 APK 的 `assets/script-modules.json` 声明, AndroidManifest 的 application 级 `<meta-data android:name="org.autojs.plugin.scriptModules.CATALOG" android:value="script-modules.json" />` 指向它. 宿主经 `PackageManager` 读 meta-data, 经 `createPackageContext(pkg, 0).assets` 读清单 (Q101), 两者都不启动插件进程.

```json
{
  "schemaVersion": 1,
  "moduleRoot": "modules",
  "groups": [
    { "id": "libraries" },
    { "id": "node-shims" }
  ],
  "libraries": [
    {
      "id": "axios",
      "group": "libraries",
      "version": "1.6.8",
      "license": "MIT",
      "modules": { "axios": "axios.js" },
      "files": ["axios.js", "axios/"]
    },
    {
      "id": "i18n",
      "group": "libraries",
      "modules": { "i18n": "i18n.js", "banana-i18n": "banana-i18n.js" },
      "files": ["i18n.js", "banana-i18n.js"]
    }
  ]
}
```

- `id`: 库名, 也是设置页开关与错误消息里的名称.
- `modules`: 可 require 的名称 (模块名与别名) 到文件的映射. 别名只是同一库中另一个可 require 的入口 (如 `banana-i18n`), 不另设开关.
- `files`: 库拥有的文件与目录 (以 `/` 结尾为目录前缀). 库内部的 require (`pangu.min.js`, `modules/axios/browser-libs/utils.js`) 按 "id 规范化为 `moduleRoot` 下的路径, 再找拥有它的库" 判定, 与资产 provider 的规范化相同 (去 `modules/` 前缀, 补 `.js`).
- `group`: `libraries` 与 `node-shims` (设置页分两节; 宿主只用于错误消息以外的诊断, 不影响加载).
- `version` / `license`: 设置页与 README 显示, 宿主不读.
- 插件文件放在插件 APK 的 `assets/modules/` 下, 相对路径与宿主 `assets/modules` 一致 (原文件留在宿主, P5.2 再删).

## (b) 宿主: 插件模块源

新包 `core/plugin/scriptmodule/`:

- `ScriptModulePluginCatalog` (纯 Kotlin, JVM 可测): 解析 `script-modules.json`, 把 id 规范化为路径并查找拥有它的库. 解析失败的清单视为插件不可用 (诊断写 logcat), 不抛给脚本.
- `ScriptModulePlugins` (进程级状态):
  - 只认官方插件包 `io.github.supermonster003.autojs6.plugin.bundled.libraries` (插件中心 id `bundled.libraries`, engine `script-modules`), 授权按插件中心 (`PluginTrustManager.isAuthorized`).
  - 快照: (versionCode, lastUpdateTime, 插件级启用, 已启用的库集合, 清单). 由 `PackageManager` 与插件中心的 `PluginEnableStore` 得出, 不绑定服务, 不唤醒插件进程.
  - 失效: 首次使用时注册一次进程级 `PACKAGE_ADDED / REMOVED / REPLACED / CHANGED` 接收器 (按包名过滤) 与 `PluginStateChangeRegistry` 监听, 收到即作废快照; 下一次查询重建. 没有变化时查询是一次内存读取, 不做 IPC.
  - 文件: 首次需要某文件时经插件的 `AssetManager` 读入, 编译结果按 (包, versionCode, lastUpdateTime, 路径, 编译配置) 进程级软引用缓存, 与 `ProcessWideAssetModuleScriptProvider` 同法. 插件更新 (versionCode 或 lastUpdateTime 变化) 时旧键自然失配, 快照作废时一并清空.
- 单库启用状态 (Q100 推荐项): 插件为每个库声明一个 `activity-alias` (目标为插件的 NoDisplay WakeActivity, action `org.autojs.plugin.SCRIPT_MODULE_LIBRARY`, meta-data `org.autojs.plugin.scriptModules.LIBRARY` = 库 id), 设置页的开关用 `setComponentEnabledSetting(..., DONT_KILL_APP)` 切换它; 宿主一次 `queryIntentActivities` 得到已启用的库. 开关会触发 `PACKAGE_CHANGED`, 宿主快照随之作废.
- `PluginModuleScriptProvider` (`engine/module/`, 每个引擎一个): 接在引擎 provider 链中, 位于 `ProcessWideAssetModuleScriptProvider` 之后 (本地优先), `SoftCaching(AssetAndUrl)` 之前. 对 id:
  1. 不是相对 id, 规范化后在清单中找到拥有它的库, 插件可用且库已启用: 返回 ModuleScript (URI `file:///android_asset/plugin-modules/bundled.libraries/<路径>`, 只作源名与缓存键; base 同 URI 目录).
  2. 插件未安装 (宿主内置的已知名称表, 见 (c)), 插件已禁用, 或库已禁用, 而 id 是已知名称: 抛安装 / 启用指引的 Error (见 (d)).
  3. 其他: 返回 null (交给下一个 provider), 不影响 URL 与绝对路径.
- 因为插件 provider 在引擎的 Rhino Require 中, 两态共用: 开态 P3.7 的 `AssetModuleSource.canLoad` (`canRequireNamedModule` 查 provider 链) 与关态 jvm-npm 的 NativeRequire 回退都经过它; Global getter 的 `rhinoRequire(name)` 也经过它. 模块在插件 provider 加载后与 getter 共用实例, `require('axios') === axios` 两态都成立.
- P3.7 接口边界: 不新增 `ScriptModuleSource` 实现 (插件模块经 `AssetModuleSource` 的 provider 链到达, 与资产同一缓存), 只在 `ScriptRequire` 中把 (d) 的错误改为调用 require 的脚本位置 (同 Q83 的 `moduleError`); `ScriptModuleSource` 的注释改为 "资产与插件源经引擎 provider 链". 设计 (f) 节所说的 `PluginModuleSource` 由 provider 代替, 原因: 单独的源要自己执行模块, 就不能与 getter 及 `require('modules/axios/...')` 这类内部 require 共用 Rhino Require 的缓存.

## (c) 本地优先与已知名称

- 本地优先: 资产 provider 在前, 宿主 `assets/modules` 仍有文件时 (P4.3 到 P5.2 之间) 插件不参与, 行为与现在相同 (两态); 插件只在资产缺失时提供. 因此 P4.3 期间正式构建中插件只是备用, 四态差别要在去掉资产的构建上才看得到 (Q102).
- 已知名称: 宿主内置 13 个可 require 名 (含 `banana-i18n`) 与它们所属的库 id, 用于插件未安装时给出指引 (此时没有清单可读). 已安装时以清单为准 (插件新增的库不必改宿主).
- 关态 jvm-npm 对 `builtInModules` 先查工作目录同名文件, 开态同 (P3.7 (a) 第 2 步), 不变.

## (d) 四态行为

| 状态 | `require('axios')` / `require('path')` | 全局 `axios` getter (Q103 推荐项) | 内部 require |
|---|---|---|---|
| 未安装 | Error, code `MODULE_NOT_FOUND`, 消息: 模块由官方插件 "Bundled Libraries" 提供, 尚未安装, 到 "插件中心" 安装 | 读取时抛同一 Error; `typeof axios` 也会抛 | - |
| 已安装且启用 | 插件中的模块, 与 getter 同一实例 | 模块 | 由插件 provider 提供 |
| 已禁用 (插件级: 插件中心开关) | Error, 消息: 插件已在插件中心禁用, 到插件中心启用 | 同左 | 同左 |
| 已禁用 (单库: 插件设置页) | Error, 消息: 库 axios 已在插件设置中关闭 | 同左 | 已加载的模块不受影响; 未加载的同左 |
| 卸载 | 同未安装 (快照随 `PACKAGE_REMOVED` 作废) | 同左 | - |

- 消息 10 种语言 (宿主 `values*/strings_script_module_plugins.xml`, 新文件, 不改 `strings.xml`), ASCII 标点, 经 `ScriptMessages` 风格取资源, 没有 Context 时 (JVM) 用英文回退. 例 (en): `Cannot find module 'axios': it is provided by the official plugin "Bundled Libraries", which is not installed. Install it in AutoJs6 > Plugin Center.` 错误对象带 `code = 'MODULE_NOT_FOUND'` 与 `pluginId = 'bundled.libraries'`, `library = 'axios'`.
- 开关生效时机: 插件中心开关与设置页开关都在下一次 require 未加载的模块时生效 (快照由广播作废, 不必重启脚本); 已在本脚本中加载的模块 (Rhino Require 每个运行时缓存) 到脚本结束前保持, 所以对用到它的脚本实际是 "下次运行生效".
- getter 的 `by lazy`: 初始化抛出时 Kotlin 的 lazy 不记住结果, 启用插件后同一脚本再次读取会重新加载.

## (e) 插件仓库

- 包名 `io.github.supermonster003.autojs6.plugin.bundled.libraries`, 插件中心 id `bundled.libraries`, engine (分类) `script-modules`, engineId `bundled-libraries`; 结构照 Offline Docs (只有资源, 无原生库, 无脚本 API 服务): INFO 服务 (`IPluginInfoProvider`, common-plugin-api.aar), WakeActivity, 设置页 (启动器入口, 插件中心 "启动页" 即它), 13 个 activity-alias.
- 设置页: 两节 (库 / Node 兼容垫片), 每个库一行 (名称, 版本, 许可证, 开关, 默认开); 平台 `Switch` 与 `DeviceDefault` 主题, 不引入 Compose / AppCompat.
- `requiresHostVersion`: 实现 T11 的宿主构建号 (本地提交时的 VERSION_BUILD, 发布前按实际发布的宿主更新).
- README / CHANGELOG / ROADMAP (ASCII 标点), 第三方许可证随附 (axios / cheerio / dayjs / banana-i18n / pangu / lodash 为 MIT, 垫片与 i18n.js 随宿主 MPL-2.0), `THIRD_PARTY_NOTICES.md` 写来源与版本.
- 构建: debug APK (本地验证用); 签名 release 与官方索引登记不在本条.

## (f) 与 P5.2 的衔接

- 本条不删 `assets/modules`. P5.2 在插件发布并登记索引后删除表 (0) 中的文件 (保留 `obsolete/` 与启动必载的 promise / ui-ext / jvm-npm 等), 删除后插件 provider 成为这些名称的唯一来源, 行为即 (d) 的四态.
- P5.2 同时把 `AssetModuleSource.CORE_FILES` 的注释与已知名称表对齐 (8 个核心名中只有 lodash 与 7 个垫片, 都在插件中).
- 验收 "`obsolete` 以外的 `assets/modules` 只剩启动必载文件" 属 P5.2, 本条记录清单.

## (g) 测试与验收

JVM:
- `ScriptModulePluginCatalogTest`: 清单解析 (缺字段, 重复名, 非法路径, `..`), id 规范化 (裸名, `.js`, `modules/` 前缀, 目录前缀归属), 已知名称表与宿主资产一致 (13 个名称都有资产文件).
- `PluginModuleScriptProviderTest`: 假的插件状态 (未安装 / 启用 / 插件级禁用 / 单库禁用) 下 provider 的返回与错误 (消息, code, library); 资产存在时插件不被询问 (本地优先); URL 与绝对路径交给后续 provider.
- 两态差分: 同一 Rhino 环境下资产隐藏, 插件 provider 提供文件时, 开 (ScriptRequire) / 关 (jvm-npm) 的 `require('axios') === axios`, `require('path').join` 等结果与四态错误的 name / code / message 一致.
- `StaticReferenceGuardTest`: 进程级快照与编译缓存 (软引用) 及接收器如需登记, 白名单写明理由.
- 隔离克隆 `E:/.cache/aj6p43`: 全量 `:app:testAppDebugUnitTest` 0 失败, `:app:assembleAppDebug`.

真机 (G8441, Q102 推荐项): 克隆构建一个去掉表 (0) 资产的 debug 包, 插件 debug 包安装 / 启用 / 插件级禁用 / 单库禁用 / 卸载, 两态各跑 `device_plugin_modules_check.py` (新脚本, 复用 `device_require_check.py` 的 `run_marked`; 原计划的 `--plugin-states` 扩展项因状态切换与预期表较长而独立成文件), 13 个名称各一条 smoke; 正式构建 (资产在) 跑一次确认本地优先. XQ-AT72: require 路径 R2 一次与 prologue.

## 已拍板 (附录 C Q100-Q103, 2026-10-24 维护者均选 (1))

| 编号 | 问题 | 推荐项 (是否先行) | 其他选项 | 改选的返工 |
|---|---|---|---|---|
| Q100 | 单库开关存在哪里, 宿主如何不唤醒插件读到 | (1) 插件设置页切换每个库的 activity-alias 组件状态, 宿主 `queryIntentActivities` 读取, 开关即时 (`PACKAGE_CHANGED`); 插件约 +40 行, 宿主约 +30 行. 已拍板 | (2) 开关放宿主的插件设置页 (插件中心 > 本插件 > 设置), 存宿主设置层 (要登记 NamedStores / inventory), 插件页只展示列表; 宿主约 +150 行. (3) 插件 ContentProvider 存状态, 宿主每次运行查询: 会唤醒插件进程, 违背 T11 | (2) 约 1 天; (3) 不建议 |
| Q101 | 文件与清单的读取方式 | (1) `createPackageContext(pkg).assets` 直接读插件 APK (同 Offline Docs 宿主的做法), 从不启动插件进程, 插件不写 provider 代码. 已拍板 | (2) 照 T11 原文经 ContentProvider: 每个版本首次读取启动一次插件进程, 插件约 +80 行 provider 与权限声明 | (2) 约半天 |
| Q102 | 四态真机验收的做法 (资产仍在宿主, 本地优先时插件不参与) | (1) 隔离克隆构建一个去掉 13 个名称资产的 debug 包做四态, 正式构建跑一次本地优先; 产品代码不加开关. 已拍板 | (2) 宿主加调试开关 (只在 debug 构建) 让资产库名跳过本地, 约 20 行, 留到 P5.2 删 | (2) 约 20 行与 2 条用例 |
| Q103 | 插件不可用时全局 `axios` 等 getter 的行为 | (1) 读取时抛与 require 相同的指引 Error (`Object.keys(global)` 不变; `typeof axios` 也会抛). 已拍板 | (2) 返回 undefined: `typeof axios` 检测安全, 但 `axios.get(...)` 只报 "Cannot read property get of undefined", 没有指引 | 小 (一处与 2 条用例) |
