# P7.1 打包应用带上脚本用到的库

ROADMAP P7.1 的设计 (子会话 augment-p7-1, 2026-10-29). 第 1 - 5 节是现状与方案, 第 6 节是拍板结果 (附录 C Q185 起), 第 7 节是实施结果.

## 1. 现状

- P5.2 阶段 5 (`67ef4c1a5`) 从 `src/main/assets/modules` 删除了 Bundled Libraries 收纳的 13 个库 (32 个文件, 原始 860422 字节). 打包应用的模板由 inrt 变体构建, 同用 `src/main/assets`, 所以也不再带这些文件 (附录 C Q181 选 (3): 两处都删, 另立项即本条).
- 打包应用在没有安装插件的设备上 `require` 这些库, 或读取全局 `axios` / `cheerio` / `dayjs` / `i18n` / `pangu` (`ScriptRuntime.js_mod_*` 也走 `require`), 抛出 `Error` (`code` 为 `MODULE_NOT_FOUND`, `reason` 为 `not-installed`). 消息来自 `ScriptModulePluginMessages.unavailable`, 5 种原因的文案都让用户去 "AutoJs6 > 插件中心", 不适合打包应用.
- 打包流程: `RemoteApkBuildRequestPreparer` 在宿主侧准备项目归档与 "构建输入" 归档 (原生库, 插件资产); APK Builder Template 插件解开模板 APK 后, 把构建输入归档按原路径解到构建目录 (`RemoteZipExtractor.validateBuildInputEntry` 接受 `lib/...` 与 `assets/<目录>/...`). 构建输入归档目前只在有原生库且有 ABI 时才生成.
- 运行时: Rhino `require` 的模块提供者依次是 APK 资产 `assets/modules` (`ProcessWideAssetModuleScriptProvider`), Bundled Libraries 插件 (`PluginModuleScriptProvider`), 绝对路径. 应用自带的文件优先.

因此把库文件写进构建输入归档的 `assets/modules/<文件>`, 生成的应用就恢复到 P5.2 之前的行为, 不需要改 APK Builder Template 插件.

## 2. 体积

插件 1.0.0 的文件, 压缩体积按 deflate 估算 (APK 中资产默认压缩). 模板 APK 约 34 MB.

| 库 | 文件数 | 原始 | 压缩后 |
| --- | --- | --- | --- |
| cheerio | 1 | 612.0 KB | 136.5 KB |
| axios | 12 | 63.7 KB | 18.7 KB |
| i18n (含 banana-i18n) | 2 | 62.6 KB | 14.3 KB |
| dayjs | 6 | 34.2 KB | 10.4 KB |
| pangu | 3 | 20.7 KB | 5.8 KB |
| lodash | 1 | 12.3 KB | 4.7 KB |
| buffer, fs, path, process, events, os, nodejs | 各 1 | 共 34.7 KB | 共 8.6 KB |
| 合计 | 32 | 840.3 KB | 约 199 KB (模板的 0.6%) |

## 3. (a) 怎样判定脚本用到了哪些库

候选:

1. 静态扫描 (推荐), 另在打包页 "依赖库" 中加一项 "Bundled Libraries" 兜底, 勾选即复制全部 13 个.
2. 只静态扫描, 不加勾选项.
3. 不扫描, 插件可用时总是复制全部 13 个 (每个打包应用多约 199 KB, 包括不用这些库的应用).
4. 不扫描, 只加勾选项, 由开发者自己选.

扫描规则 (候选 1 / 2):

- 范围: 与项目归档相同的文件. 单文件打包只扫该文件, 目录打包遵守构建排除的目录. 扩展名 `.js` `.mjs` `.cjs` `.ts` `.mts` `.cts` `.jsx` `.tsx`. 跳过已加密的文件 (`EncryptedScriptFileHeader`) 与大于 4 MB 的文件.
- 入口按 Node / QuickJS / Bun 运行的项目不扫描: 这些运行时的 `require` 不经过 Rhino, Node 内置的 `fs` / `path` / `events` 等会被误判. 这类项目里经 Rhino 运行的脚本若用到库, 用勾选项.
- 匹配: `require(...)`, `import ... from`, `export ... from`, `import(...)` 中的字符串字面量 (单引号, 双引号, 不含插值的反引号). 去掉 `node:` 与 `modules/` 前缀后, 按插件清单 `script-modules.json` 的 `modules` 与 `files` 归属到库 (`dayjs/plugin/utc` 归 dayjs, `banana-i18n` 归 i18n). 另外, 5 个全局名 `axios` / `cheerio` / `dayjs` / `i18n` / `pangu` 作为标识符出现 (前一个字符不是 `.`, 字母, 数字, `_`, `$`) 也算用到.
- 依赖闭包: 对已选中库的文件用同样的规则再扫, 加入它们 `require` 的库 (`fs` 带上 `buffer` 与 `path`, `nodejs` 带上 `process` 与 `buffer`), 直到不再增加. 清单之外的名字 (cheerio 内部的 `parse5`, axios 的 `stream`) 忽略.
- 误判:
  - 多带: 注释, 字符串, 对象键里出现库名. 只多出该库的体积 (最大是 cheerio 的 136.5 KB), 不影响运行.
  - 漏带: `require(变量)`, `eval`, 运行时从项目以外读取的脚本, 已加密的源文件, Node 等项目里经 Rhino 运行的脚本. 运行到时抛出打包应用版的消息 (第 5 节), 消息提示开发者勾选 "Bundled Libraries" 后重新打包.
- 耗时: 逐文件正则扫描, 估计 100 个文件 (共 1 MB) 在手机上不超过 100 ms; 实施后实测, 记入附录 A "P7.1".

勾选项 "Bundled Libraries" 写进 `project.json` 的 `libs` (同其他依赖库), 不对应原生库, 模板插件按标签查原生库时查不到, 不受影响.

## 4. (b) 从哪里复制

从已安装的插件 APK 复制 (Q181 (3) 已定的方向):

- 用 PackageManager 拿到插件的 APK 路径, 以 `ZipFile` 读取 `assets/<moduleRoot>/<文件>`. 插件 1.0.0 的模块文件不压缩存储 (`d287681`), 读取快. 文件列表按插件自己的 `script-modules.json` (库的 `files`, 以 `/` 结尾的项包含其下全部文件), 以后插件升级库或增减文件, 宿主不用改.
- 条件与宿主中的脚本相同: 插件已安装, 签名为官方 (`PluginTrustManager`), 插件与该库都开启, 即 `ScriptModulePluginState.lookup` 为 `Available`. 不复制未授权的插件中的代码.

不用宿主副本: 宿主在 `67ef4c1a5` 已删除这些文件; 保留一份就是 Q181 的选项 (1) (维护者已否), 宿主 APK 回增约 0.2 MB 压缩体积, 升级库时要同步插件与宿主两处. 也不在构建时联网下载 (离线不能打包, 还要另做校验).

## 5. (c) 插件不可用时的构建与打包应用中的提示

构建时, 扫描到的库有一部分无法复制 (插件未安装, 未授权, 插件或该库已关闭), 候选:

1. 照常构建 (推荐). 构建成功的对话框列出未复制的库与原因, 并指向宿主的插件中心 (这时人在宿主里, 合适). 目前成功对话框不显示警告, 要加.
2. 构建失败, 提示先安装或开启插件. 扫描多带时也会被拦下.
3. 构建前弹框确认是否继续.

打包应用运行时 (`BuildConfig.isInrt`), 消息改用面向开发者的新文案, 不提插件中心. `e.code` (`MODULE_NOT_FOUND`), `e.library`, `e.reason` 不变 (`reason` 反映运行设备上插件的状态, 通常是 `not-installed`). 文案候选见第 6 节 Q188.

## 6. (d) 运行设备装有插件, 而应用自身没带库

读取路径: 应用资产中没有时, 走 `PluginModuleScriptProvider` -> `ScriptModulePlugins.access(打包应用的 Context)`.

- 可见性: 打包应用与宿主同用 `src/main/AndroidManifest.xml`, 其中 `<queries>` 有 `<intent><action android:name="org.autojs.plugin.INFO" /></intent>`, 而插件的 `BundledLibrariesInfoService` 声明了该 action. 所以 Android 11+ 上插件对打包应用可见, 不依赖 `QUERY_ALL_PACKAGES` (APK Builder Template 插件会去掉项目没选的权限).
- 授权: 按插件的签名判断 (官方 SHA-256), 与宿主无关.
- 开关: 插件开关存在打包应用自己的偏好中, 默认开启 (打包应用没有插件中心); 库开关是插件中的组件启用状态, 对设备上所有应用生效.

预期打包应用能经插件读到库. 是否保留这条回退见 Q187.

验证计划 (真机, 空闲设备): 用设备测试在手机上打包两个应用, 脚本都用 dayjs, lodash, pangu (`require` 与全局 `dayjs` / `pangu`):

- A: 插件可用时打包, 扫描后带上 3 个库.
- B: 打包时插件未安装 (adb 卸载), 不带库.

再在两种状态下运行 A 与 B: 插件已安装 / 未安装. 核对 `require` 与全局 getter 的结果, 错误的 `code` / `reason` / 消息. A 在两种状态下都应可用; B 在插件已安装时按 Q187 的决定, 未安装时抛出打包应用版的消息. 结果记入附录 A "P7.1".

## 7. 拍板结果

维护者 2026-10-29 拍板 (附录 C Q185 - Q188):

- Q185 = 推荐: 静态扫描, 打包页依赖库加 "Bundled Libraries" 勾选项 (勾选即带全部 13 个).
- Q186 = 推荐: 照常构建, 成功对话框列出未复制的库与原因, 指向宿主插件中心.
- Q187 = 推荐: 保留回退, 打包应用先读自带的, 缺的再读设备上已装的官方插件 (不改提供者链).
- Q188 = 非推荐: 两条面向开发者的消息, 第一条另加 "或在本设备安装插件". 原因与消息的对应:
  - `library-disabled` (设备上的插件装着, 但该库已关闭): "此应用打包时没有带上库 X, 本设备上插件中的该库已关闭; 用 AutoJs6 重新打包, 或在插件中开启该库".
  - 其余原因 (`not-installed`, `unauthorized`, `plugin-disabled`, `incompatible`): "此应用打包时没有带上库 X; 用 AutoJs6 重新打包 (动态加载时勾选 Bundled Libraries), 或在本设备安装插件 Bundled Libraries".

## 8. 实施结果

宿主 `0d928b9a8` (2026-10-29), 按第 3 - 7 节实施:

- 扫描: `core/plugin/scriptmodule/ScriptModuleUsageScanner.kt` (规则同第 3 节). 计划: `PackagedScriptModules.kt` 的纯函数按 `ScriptModulePluginState.unavailableReason` 决定每个库复制或列为缺失, 已复制的 `.js` 再扫依赖; Android 侧从插件 APK 的 `ZipFile` 读文件, 读取期间插件版本或安装时间变化视为不兼容.
- 构建: `RemoteApkBuildRequestPreparer` 把复制的文件写进 build inputs 归档的 `assets/modules/...` (没有原生库时归档也可只含模块文件, 模板插件不用改); 勾选项 `ApkBuildLibrary.BUNDLED_LIBRARIES` 显示在打包页 "支持库" (中文界面的实际名称, 第 3 节写作 "依赖库") 中. 提示经 `BuildOutcome.notices` 显示在成功对话框新增的一段 (`dialog_build_progress.xml`).
- 消息: `ScriptModulePluginMessages.unavailable(..., packaged = BuildConfig.isInrt)`, 打包应用的两条按第 7 节, 11 个 values 目录各加 8 条字符串 (两条运行时消息, 成功对话框的已打包 / 未打包与各原因的处理办法).
- 实测: 扫描 0-1 ms, 计划 13-38 ms (63751f63, 一个脚本); 全部 13 个库使应用增大 211357 字节, 与第 2 节的估算 (约 199 KB) 相近. 真机两态 (插件未装 / 已装) 下应用 a (扫描) / b (打包时无插件) / c (勾选) 全部符合预期, 第 6 节的回退在装有插件的设备上可用 (b 什么都没带也可用). 详见 ROADMAP 附录 A "P7.1".
- 偏离设计: 无. 实施中发现缺库消息里 Node 兼容模块与 lodash 的名称带 `.js` (jvm-npm 一致的既有行为), 登记为附录 C Q189, 推荐项 (保持现状) 已先行.
