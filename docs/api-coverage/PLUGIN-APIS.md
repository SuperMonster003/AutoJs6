# 官方插件自有脚本 API 盘点 (P5.1, 2026-10-29)

本文件是 [ROADMAP](ROADMAP.md) P5.1 的产出: 对官方插件索引中的每个插件, 列出不经宿主 KSP 注册 (不在 [MATRIX](MATRIX.md) 分母) 的脚本入口, 给出条目数, 文档 / d.ts / Ace / Node / Bun 五处现状与建议, 末尾是汇总表与 Q2 选择题材料. 子会话 api-cov-p5-1 (local_7fcb8c88-f18a-4657-85b6-29fa30d684f7) 静态盘点, 只读各插件仓库.

## 1. 口径

### 1.1 清单与数据源

- 清单: `AutoJs6-Official-Plugins-Index/official-repositories.json` (索引 24d337f), 50 个仓库. 本地另有 5 个仓库不在索引里, 不在本次范围 (第 5 节).
- 五处现状的数据源: 文档 `AutoJs6-Documentation/api/*.md` (6b1267e, 标题级或仅正文提及), d.ts `AutoJs6-TypeScript-Declarations/declarations/**` (cfd7bc4), Ace `AutoJs6-Plugin-Ace-Editor` 捆绑声明 (fe92aaa), Node `AutoJs6-Plugin-NodeJs-Runtime` 的 `docs/nodejs/types/autojs6-node/*.d.ts` 与 `docs/HOST-API.md` (3f620da0), Bun `AutoJs6-Plugin-Bun-Runtime` 的 `host-client.d.mts` 与 `docs/host-api.md` (ddf607b). 宿主 6499e3976, 分母数字取自 MATRIX.md (2830 行).
- 写法: "有 n/n", "部分 k/n", "无 0/n"; "不适用" 表示该处不消费这种语言或这种入口. 插件仓库自带的 README / docs / d.ts 单独注明, 不算 "文档" 或 "d.ts" 列.

### 1.2 插件向脚本暴露入口的五种机制

| 机制 | 说明 | 本次结果 |
|---|---|---|
| M1 `plugins.load(包名)` | 宿主 `Plugins.load` 读 manifest meta-data `org.autojs.plugin.sdk.registry`, 调用 registry 类的 `loadDefault`, 再 require 插件 assets 主脚本并以插件实例调用其导出函数, 返回值即脚本对象 | 只有 ImGui |
| M2 `plugins.tools` 工具 | 插件按 [插件工具协议](../dev/plugin-tool-protocol-v1.md) 自描述工具, 脚本经 `plugins.tools.call('<短名>_<工具名>', args)` 调用; 4 个通用函数 (list / providers / call / callAsync) 已在分母 | 5 个插件, 12 个工具 |
| M3 宿主 KSP 模块由插件实现 | 宿主模块经 `plugin-api/<x>-api` 契约绑定插件服务, 脚本只见宿主模块 | 已在分母, 只注明不重复计 |
| M4 插件自有全局 / 模块 | 运行时插件给本语言脚本的宿主接口, 注入脚本作用域的 JS, 文档要求脚本直接使用的契约类 | 9 个运行时类插件 (TypeScript 引擎除外) |
| M5 其他 | 面向脚本的 intent / action / URL scheme | 无. 各插件的 action 都是宿主绑定的服务, 界面入口或受 `org.autojs.permission.PLUGIN` 保护的插件间入口 |

编辑器, 预览 / 查看器, 设置页, 文件管理器动作, 面向 PC 客户端的 MCP 工具都不是脚本 API.

## 2. 有自有脚本入口的插件 (14 个)

### 2.1 AutoJs6-Plugin-ImGui (1.9.4 / 34, 8567ca9)

- 入口 (M1): `plugins.load('io.github.supermonster003.autojs6.plugin.imgui')`, 注册类 `ImGuiPluginRegistry`, 主脚本 `app/src/main/assets/plugin/index.js` (6249 行); 导出函数返回 api 并定义全局 `imgui`, 插件中心的自动挂载也走这里.
- 条目数: 顶层 458 个名称 = 函数 227 (含 bool / float / int / string / ref / floats / bytes / strings 等引用辅助与 version) + 常量对象 37 (共 495 个常量) + PascalCase 别名 194 (Dear ImGui 风格, index.js 第 6035 行起的别名表). 构造出的对象约 85 个方法: 会话 (createWindow 等返回) 26 方法 + 33 属性, 悬浮按钮 (addButtonEx) 10, 纹理 4 方法 + 3 属性, DrawList 11 方法 (另 11 个别名), 组件基类 4, logWindow 11, dataTable 15, toast 1, dialog 2 (其 promise 3), statsOverlay 1. 按宿主矩阵口径 (别名并入主条目, 常量对象各记 1 行), 约 349 行 = 函数 227 + 常量对象 37 + 对象成员约 85.
- 现状 (按 458 个顶层名称): 文档 无 0/458, d.ts 无 0/458, Ace 无 0/458, Node 无 0/458, Bun 无 0/458.
- 插件自带: `app/src/main/assets/types/imgui.d.ts` (1129 行, 10 段 JSDoc) 覆盖 458/458 顶层名称, 对象方法名 70/74 (缺 export / update / cancel / accept); manifest meta-data `org.autojs.plugin.imgui.TYPESCRIPT_DECLARATION` 指向它, 但宿主与 Ace 都不读取. README 与 `docs/` 正文提及 134/227 个函数.
- Node / Bun: 运行时没有 `plugins.load`, index.js 依赖 Rhino 的 Java / Android 互操作, 全部行应为 not-portable (rhino-only).
- 建议: 纳入. 它是唯一走 `plugins.load` 的官方插件, 规模最大且五处全空; d.ts / Ace 可直接复用插件自带声明, 主要工作量在签名级文档. 详见 Q2-1.

### 2.2 plugins.tools 工具提供方 (5 个插件, 12 个工具)

| 插件 (版本, HEAD) | 工具 (参数个数) | 目录源文件 | 已在分母的宿主模块 |
|---|---|---|---|
| Three-Hanzi-CC (2.1.0 / 150, 02d31bd) | `opencc_convert` (2) | `OpenccToolCatalog.kt` | opencc 33 |
| Yolo-NCNN (0.2.0 / 63, d8639af) | `yolo_detect` (6) | `tool/YoloToolCatalog.kt` | yolo 1, YoloDetector# 2 |
| Image-Quantization (1.1.0 / 43, 1ab9fe3) | `imagequant_quantize` (9, 输出 png) | `tool/ImageQuantToolCatalog.kt` | images.quantize / quantizeToFile 2, PngQuantizationResult# 5, PngQuantizationFileResult# 5 |
| Three-Stack-Archive (3.1.0 / 131, 2a32b30) | `archive_list` (5), `archive_read` (4), `archive_extract` (3, 输出目录) | `tool/ArchiveToolCatalog.kt` | 无 (宿主 zip 模块用内置 Zip4j, 不依赖本插件) |
| Three-Macaw-Speech (1.3.0 / 154 开发中, ae17ccc) | `speech_listen` (6), `speech_speak` (5), `speech_transcribe` (3), `speech_synthesize` (5), `speech_speaker_verify` (3), `speech_speaker_identify` (3) | `tool/SpeechToolCatalog.kt` | speech 11, speech.models 8, speech.speaker 8 (tts 走系统 TTS 引擎, Macaw 只是可选引擎之一, 不算) |

- 现状 (逐工具, 共 12): 文档 部分 2/12 (只有 `opencc_convert` 与 `imagequant_quantize` 在 plugins.md 示例里出现, 无标题), d.ts 无 0/12 (只在注释举例), Ace 无 0/12, Node 无 0/12, Bun 无 0/12. 插件 README 列出了 Hanzi-CC / Yolo / Macaw 的工具, Stack-Archive 只在 changelog 提到.
- 通用入口: `plugins.tools` 的 4 个函数已在分母, 文档 / d.ts / Ace 4/4, Node main / Worker / Bun 各 pending 4 (由 P3 / P4 处理). Node 的 `plugins` 模块是 Node 自己的本地 JS 插件管理器, 与宿主同名不同义.
- 文档错误: `AutoJs6-Documentation/api/plugins.md` 第 306 行的 `imagequant_quantize` 示例写 `colors: 16` 与 `outputs: { image: ... }`, 实际参数名是 `maxColors`, 输出名是 `png`; 宿主 `PluginToolSchema` 会以 "is not a parameter of this tool" 拒绝 `colors`.
- P5.2a (2026-10-30) 之后: Macaw 1.3.0 开发中的 P8.4 (6520621) 又加了 `speech_diarize`, `speech_detect_language`, `speech_tag`, 官方工具共 15 个; 工具表与五处现状见第 10 节附表 (附录 C Q156).
- 建议: 不逐工具进矩阵. 工具是动态目录 (随插件版本变化, 如 Macaw 1.3.0 新增两个说话人工具; 非官方插件也能提供), 参数签名本身是 JSON Schema 并由宿主调用前校验. 改为 plugins.md 增加 "官方插件工具表" (12 行: 名称, 提供插件, 最低版本, 参数, 输出, 可从各 catalog 生成) 并修正上面的示例; d.ts 可加工具名字面量联合 (保留 string 后备) 只用于补全, 不计覆盖. 详见 Q2-2.

### 2.3 JS 运行时的自有入口 (Node / Bun / QuickJS)

这三个插件对宿主 API 的映射由矩阵的 Node main / Node Worker / Bun 列衡量, 下面只列没有宿主模块对应的运行时自有入口.

P5.2b (2026-10-30) 之后的现状见第 9 节附表: 文档 132/132, d.ts 与 Ace 各 125/125 (QuickJS 不适用), Node 改计 113 条 (ROADMAP 附录 C Q99). 本节保留 P5.1 盘点时的数字.

#### AutoJs6-Plugin-NodeJs-Runtime (1.32.0 / 299, 3f620da0)

- 入口 (M4): 12 组 110 条, 声明在 `docs/nodejs/types/autojs6-node/`. 运行时控制: `autojs6:bridge` 2, `autojs6:host` 11 (宿主消息 EventEmitter), `autojs6:lifecycle` 7, `autojs6:profile` 1 (38 个字段的能力快照), `$autojs` 自有 3 (schema / profile / androidContext). 包与本地插件: npm 21, package_manager 14, plugins 19 (本地 JS 插件管理器). Rhino 迁移: rhino 13, `autojs6:compat` 控制成员 10 (另 89 个是宿主 API 再导出, 不计). 其他: media_store 8 (scanFile 除外), `worker_threads.WorkerPool` 1. android / input_observer / fetch / websocket 宿主有分散的对应, 不计.
- 现状: 文档 无 0/110 (bridge / compat / $autojs 只在 runtimeApis.md 正文提到), d.ts 有 109/110, Ace 有 109/110 (两处副本与插件声明逐字相同), Node 有 110/110 (插件 HOST-API.md 与 d.ts), Bun 不适用. 缺口: WorkerPool 只有 HOST-API.md 的文字说明, d.ts 与 Ace 都没有.
- 建议: 不进宿主分母, 在本文件附表跟踪, 补文档站小节与 WorkerPool 声明. 详见 Q2-3.

#### AutoJs6-Plugin-Bun-Runtime (0.21.0 / 296, ddf607b)

- 入口 (M4): `app/src/main/assets/autojs6/host-client.d.mts` 中没有宿主对应的 12 条: 模块导出 5 (createHostClient, SDK_VERSION, HOST_API_METHODS, HostCallError, OPENCC_CONVERSION_TYPES), HostClient 成员 6 (sdkVersion, bridgeVersion, capabilities, limits, hasCapability, call), 环境变量 1 (AUTOJS6_HOST_CLIENT_MODULE). 其余 42 个成员对应宿主模块, 已由矩阵 Bun 列衡量.
- 现状: 文档 部分 5/12 (正文提及), d.ts 有 12/12, Ace 有 12/12, Bun 有 12/12, Node 不适用.
- 建议: 同 Node. 详见 Q2-3.

#### AutoJs6-Plugin-QuickJS-Runtime (1.0.4 / 42, 0bcee52)

- 入口 (M4): 全局 console (debug / info / log / warn / error), print, scriptArgs, 共 3 个顶层名称 7 条, 见 `app/src/main/cpp/quickjs_runtime_jni.cpp` 的 `installHostGlobals`. 只是输出流与入参, 不桥接宿主模块.
- 现状: 文档 无 0/7 (只有 memory.md 的 `bridgeSessions.quickjs`), d.ts 无 0/7, Ace 无 0/7, Node / Bun 不适用. 插件 README 写了 console / print, 没写 scriptArgs.
- 建议: 同 Node, 文档站补一小节 (重点是 scriptArgs 与 console 子集). 详见 Q2-3.

### 2.4 非 JS 语言运行时 (Python / Java / Kotlin / Lua / AutoJs4)

这些入口给 Python / Java / Kotlin / Lua 脚本或上游 Auto.js 4 引擎使用, d.ts / Ace (JS 补全) / Node / Bun 列不适用. 都不实现宿主 KSP 模块.

| 插件 (版本, HEAD) | 入口 (M4) | 条目数 | 文档站 | 插件自带 | 桥接宿主 / 自有 |
|---|---|---|---|---|---|
| Python-Runtime (0.5.5 / 135, 5514e82) | `autojs6` 包 (`app/src/main/python/autojs6/`, 以各模块 `__all__` 为准) | 顶层 27 (15 子模块, toast, notice, 10 个异常类), 函数 46, 常量 54 | 无 0/46 (engines.md 只说明 .py 交给插件) | `docs/python/HOST_*.md` 部分覆盖; 无 .pyi, Ace 的 Pyright 无类型桩 | 40 个函数经能力 broker 桥接宿主同名能力, 6 个自有 (artifacts / execution / project / result) |
| Java-Runtime (0.8.4-m9 / 80, 67212f9) | JVM Entry API 4 (宿主 `plugin-api/jvm-source-api` 的 `JvmEntryApi.kt`, 插件以 `protocol/jvm-source-api.aar` 冻结) | 7 个类型, 17 个方法 | 无 0/17 | `docs/context-api.zh-CN.md` 17/17; Ace 的 ECJ classpath 不含这套接口 | console / toast / sleep / app.launch / clipboard 桥接宿主, args 与 cancellation 自有 |
| Kotlin-Runtime (0.7.3-m10 / 46, ea1c21d) | 同 Java, 锁在 Entry API 2 | 6 个类型, 13 个方法 (无 args / clipboard) | 无 0/13 | 只有 README 与 samples | Java 的同源子集 |
| Lua-Runtime (0.1.3-rc.2 / 102, 9ced281) | `require("autojs")` 表 (`app/src/main/cpp/lua_runtime_jni.cpp` 的 `install_autojs_module`), 另改写全局 print / warn / require | 顶层 6, 叶子 12 (console 4, device.info, storage 4, ui.toast, now, arguments) | 无 0/12 | README 12/12; Ace 的 LuaLS 无 autojs 类型定义 | console / device / toast 走宿主能力; storage 是宿主专为 Lua 提供的 LuaHostStorage, 不是 storages 模块 |
| AutoJs4-Runtime (1.0.1 / 12, 2f49526) | 上游 Auto.js 4 引擎原样 API (`autojs4/autojs/src/main/assets/init.js` 与 `modules/__*__.js`) | 约 180 个顶层名称 (21 个模块, 约 13 个 init 全局, 57 个全局赋值, 39 个 `__asGlobal__` 提升函数, 约 53 个 selector 名), 成员未逐条计 | autojs4Runtime.md 只写用法, 并说明补全与文档以 AutoJs6 为准 | 无 | 不桥接宿主, 兼容对照用途 |

- 建议: 不纳入本主题 (D1 / D2 / D3 的对象是 AutoJs6 的 JS API 与 Node / Bun 矩阵, 语言层 API 是另一维度); 缺口 (Python 无 .pyi, Lua 无 LuaLS 定义, Kotlin 无 API 表, 文档站无语言 API 页) 交各插件路线图或另立项. 详见 Q2-4.

#### 语言运行时 API 附表 (P5.2d, 2026-10-30)

Q2-4 纳入的四个语言运行时 API 不进覆盖矩阵, 由文档页与静态门禁 `tools/api-coverage/language_runtimes.py` (Q77 (a), 宿主 3fa1fc8ad) 保证与插件一致. 门禁从各插件仓库的注册源提取名称 (Python: 包与各子模块的 `__all__`, 函数带签名; Java / Kotlin: 插件冻结的 `protocol/jvm-source-api.aar`, 从 AutoJsJvmEntry 出发取同包内经参数 / 返回 / throws 可达的类型与方法, 因为 Kotlin 锁在 Entry API 2 而宿主 HEAD 的 `JvmEntryApi.kt` 是 Entry API 4, 见附录 C Q109; Lua: 模拟 `install_autojs_module` 的建表操作), 与入库的名称清单 `tools/api-coverage/language-runtimes.json` 比对, 再与文档页的 `###` 标题和表格首格比对; 缺项, 多出的项与缺页都失败, 插件仓库不在时源码比对打印 skipped. CI 检出四个公开插件仓库运行它 (Q110). 第 2.4 节与第 6 节的 "文档 0/n" 是 P5.1 盘点时 (文档 6b1267e) 的状态.

| 插件 (版本, HEAD) | 条目数 | 文档页 (文档 fee509b) | 门禁状态 |
|---|---|---|---|
| Python-Runtime (0.5.5, 5514e82) | 函数 46 + 常量 54 + 异常类 10 | `api/pythonRuntime.md` | 通过: 名称, 参数名 / `*` / 是否有默认值 |
| Java-Runtime (0.8.4-m9, 67212f9) | 方法 17 + 类型 7 (Entry API 4) | `api/javaRuntime.md` | 通过: 名称, 参数个数 |
| Kotlin-Runtime (0.7.3-m10, ea1c21d) | 方法 13 + 类型 6 (Entry API 2, 无 args / clipboard) | `api/kotlinRuntime.md` | 通过: 名称, 参数个数 |
| Lua-Runtime (0.1.3-rc.2, 9ced281) | 函数 11 + 值 1 (arguments), 共 12 | `api/luaRuntime.md` | 通过: 名称 (C 源码无参数信息) |

类型桩 (Python `.pyi`, LuaLS meta, Java / Kotlin 桩并入 Ace) 由 P5.2e 补; 门禁以同一份名称清单为基准扩展, 扩展点见 `tools/api-coverage/README.md` "Language runtime APIs (Q77)". AutoJs4 上游 API 不纳入 (Q76), autojs4Runtime.md 只给差异指引.

## 3. 没有自有脚本 API 的插件 (36 个)

每行一个插件: 版本, 证据, 已在分母的宿主模块 (矩阵行数). 结论均为 "无自有脚本 API, 不纳入".

### 3.1 实现宿主模块的插件 (M3, 已在分母)

| 插件 | 版本 | 证据 | 已在分母 |
|---|---|---|---|
| Three-Folio-EPUB | 2.1.0 / 178 | EPUB 与 READER_OPEN 两个 action 由宿主 epub 模块调用, VIEW 与文件管理器动作是界面 | epub 21, EpubBook# 14, EpubReaderSession# 34 |
| Three-Stamp-Mail | 2.1.0 / 162 | 只有 OAuth 回调与设置页 | mail 56, MailClient# 53 |
| Three-Stone-AI | 1.5.0 / 299 | VOICE_COMMAND 是接收 Macaw 语音指令的一端, 另有设置页 | ai 6 (不含 agent), AiSession# 11 |
| Three-Stove-Agent | 1.3.0 / 437 | 是 plugins.tools 与 MCP 的使用方; `catalog/ToolCatalog.kt` 是给模型用的内部工具表; 分享, 快捷方式, VOICE_COMMAND 是 Android 通用入口 | getter `ai.agent`, AgentRun# 14; `ai.agent` 的 9 个根成员不在分母, 已由附录 C Q46 登记 |
| Three-Shell-Terminal | 1.1.0 / 87 | TERMINAL 与 TERMINAL_OPEN 由宿主 terminal 模块调用 | terminal 20 |
| Three-Block-Compose | 1.9.0 / 223 | assets 只有 doc 与 examples; 组件目录契约在宿主 `plugin-api/compose-ui-api` (V1Catalog / ComposeUiV2 / AndroidViewInteropV1), 插件只渲染; 设计器 EDIT / VIEW 是编辑器界面 | compose 16, ui 的 Compose 部分; 运行时安装的目录 (55 个 NODE 组件 + 20 个修饰符 + 8 个属性快捷名) 不在分母, 见 Q75 |
| Three-Maple-Image | 3.4.0 / 206 | GALLERY 服务与 GALLERY_SESSION_OPEN 供宿主 gallery 模块, 文件管理器动作与 image VIEW 是界面 | gallery 11 |
| Three-Setup-Installer | 1.2.1 / 143 | INSTALLER 服务供 installer 模块, INSTALLER_SETTINGS 与 apk 的 VIEW / INSTALL_PACKAGE 是界面 | installer 16 |
| Batch-Rename | 1.1.1 / 50 | BATCH_RENAME 服务供 files.batchRename, 文件管理器动作是界面 | files.batchRename 17 |
| MediaInfo | 2.1.4 / 86 | MEDIAINFO 服务供 mediainfo 模块 | mediainfo 7 |
| OpenCV | 1.1.5 / 40 | 只提供 `libopencv_java4.so`, `org.opencv.*` Java API 在宿主; `OpenCVHelper` 经 `OpenCvPluginHost.ensureLoaded` 加载, 被 Images / ImageFeatureMatching 使用 | images (间接) |
| MLKit-Barcode | 1.0.5 / 36 | `BarcodeResult` 是宿主 `plugin-api/mlkit-barcode-api` 的 AIDL parcelable (10 个字段), 经宿主 `WrappedBarcode#barcode` 暴露, 只是返回值类 | barcode 4, qrcode 4; WrappedBarcode 不在返回对象表, 见 Q74 |
| MLKit-OCR | 1.0.4 / 32 | 服务 action `org.autojs.plugin.PADDLE_OCR`, AIDL OcrResult 由宿主转成自己的 OcrResult | ocr 6, ocr.mlkit 2; OcrResult 不在返回对象表, 见 Q74 |
| Paddle-OCR-PP-OCRv3 | 1.0.6 / 61 | 同 MLKit-OCR | ocr 6, ocr.paddle 2 |
| Paddle-OCR-PP-OCRv4 | 1.0.4 / 37 | 同上 | ocr 6, ocr.paddle 2 |
| Paddle-OCR-PP-OCRv5 | 1.0.4 / 88 | 同上 | ocr 6, ocr.paddle 2 |
| Paddle-OCR-PP-OCRv6 | 1.0.4 / 41 | 同上 | ocr 6, ocr.paddle 2 |
| Rapid-OCR-PP-OCRv3 | 1.0.6 / 49 | 同上 | ocr 6, ocr.rapid 2 |
| Rapid-OCR-PP-OCRv6 | 1.0.6 / 56 | 同上 | ocr 6, ocr.rapid 2 |
| Pinyin | 1.0.3 / 33 | 自定义词典与读音契约由宿主模块处理 (`PinyinCustomDictionary.kt`) | pinyin 16 |
| Pinyin4j | 1.3.2 / 38 | 同上 | pinyin4j 3 |

### 3.2 宿主 `runtime` 对象路由的插件

| 插件 | 版本 | 证据 | 归属 |
|---|---|---|---|
| DEX-Compiler | 1.1.2 / 60 | 设置里开启实验编译器后, 宿主 `runtime.loadJar` (2 个重载) 与 `runtime.loadJarWithClasspath` 经 `AndroidDexCompilerRuntime` 交给本插件, 失败时回退内置编译器 | `runtime` 是 ScriptRuntime Java 对象, 声明在 `aj6-main-app.d.ts`, 属 D16 不纳入的宿主 Java 对象声明 |
| R8-Compiler | 0.3.0-provider-dev / 51 | 宿主 `runtime.loadJarWithR8` (6 个重载) 与 `runtime.retraceR8Stack` 只走本插件, 无回退 (`ScriptRuntime.kt` 第 647-775 行). 参考数字: 文档 部分 2/3 (loadJarWithClasspath 仅正文), d.ts 3/3, Ace 3/3, Node 0/3, Bun 0/3 | 同上 (D16) |

### 3.3 界面类插件 (无脚本入口)

| 插件 | 版本 | 证据 |
|---|---|---|
| Ace-Editor | 2.46.0 / 336 | 编辑器入口 (meta-data `editor.ENTRYPOINT`); `editor-api` 是宿主编辑器与插件的契约; 101 个 JS 资源是 ace-builds / luals 编辑器资源 |
| APK-Builder-Template | 1.2.26 / 111 | 打包模板, 打包后的应用里脚本仍用宿主 API; 宿主全局 Java 类别名 `ApkBuilder` 是宿主内部类 |
| APK-Inspector | 1.2.2 / 56 | 文件管理器动作与 content:// APK 的 VIEW 查看器 |
| File-Inspector | 1.1.1 / 30 | 文件管理器动作与查看器 |
| HTML-Previewer | 1.1.3 / 40 | 文件管理器动作与预览 |
| Markdown-Previewer | 1.2.3 / 34 | 文件管理器动作与预览 |
| Offline-Docs | 6.8.40 / 208 | 文档内容包, 8 个 JS 是文档站页面脚本; 宿主 `core/plugin/docs` 只给文档页面用 |
| MCP-Server | 1.2.0 / 250 | 宿主 `core/plugin/mcp` 只服务设置页, 抽屉开关与能力代理; OPEN_SETTINGS 受 `org.autojs.permission.PLUGIN` 保护; MCP 工具与 `assets/prompts` 面向 PC 客户端; 与 plugins.tools 共用 `plugin_tools` 桥但只是消费方 |
| Three-Adapt-A11y | 1.4.1 / 67 | 宿主只有 `core/accessibility/companion/*` 让伴生无障碍服务跟随宿主开关; 插件让 auto / selector 读到更多节点, 不新增 API |
| Three-Color-Picker | 2.0.1 / 58 | SCREEN_COLOR_PICKER 服务只被宿主抽屉的 `ScreenColorPickerTool` 使用, runtime/api 无引用 |
| Three-Amber-Player | 4.0.0 / 57 | 宿主只在文件管理器界面引用 (`ExplorerVideoPlayerPluginUi.kt`); content:// VIEW 是通用系统 intent |
| Three-Terra-Player | 1.7.0 / 69 | 同 Amber-Player (另有 M3U 播放列表界面) |
| TypeScript-Engine | 0.7.0 / 123 | 只做编译, 产物由 Rhino / Node 执行; TSX 编译为宿主 `ui.__autojs6Tsx` 调用 (宿主 ui 的内部入口); COMPILER / PARSER 服务供宿主使用 |

## 4. 盘点中发现的宿主分母缺口 (不属 Q2, 另行登记)

拍板: Q74 / Q75 均已拍板 2026-10-29 (推荐项), 由 P5.2c 实施 (第 8 节).

| 编号 | 发现 | 条目 | 建议 |
|---|---|---|---|
| Q74 | `ocr.detect` 等与 `barcode.detect` 等在 KSP 元数据里返回 `kotlin.Any`, `tools/api-coverage/returned-objects.json` 的 producers 没有列它们, 宿主 `OcrResult` (text / confidence / bounds / label 4 个字段) 与 `WrappedBarcode` (约 23 个成员, `barcode` 字段是插件契约的 BarcodeResult) 不在返回对象表 (现 43 个对象). 文档对 WrappedBarcode 有 23/23 标题, d.ts 两者都有 (`aj6-int-barcode.d.ts`, `libraries.d.ts`) | 约 27 行 | 补进 returned-objects.json (静态, 一个小条目) |
| Q75 | compose 的运行时目录 (installComputed: V1 30 + V2 25 + AndroidView 1 = 56 个组件, 其中 55 个 NODE 组件安装为 `compose.<Name>`; `compose.modifier()` 链 20 个修饰符; 8 个属性快捷名) 不在分母, REVIEW 第 35 行只注明 "不在分母". 文档 56/56 + 20/20 (composeComponents.md, composeModifierType.md), d.ts / Ace / Node / Bun 都以 `ComposeFactories` 映射类型的键覆盖 | 83 行 | 纳入分母 (extract_exports 读目录契约, 预计五处已覆盖, 价值在防目录与声明漂移) |
| (Q46 已登记) | `ai.agent` 的 9 个根成员不在 exports.json | 9 行 | 按 Q46 处理, 不重复 |
| (文档错误) | plugins.md 第 306 行 `imagequant_quantize` 示例参数名错 (见 2.2) | 1 处 | 随 Q2-2 的落地批次修正 |

## 5. 不在索引的本地插件仓库 (不在本次范围)

AutoJs6-Plugin-Bundled-Libraries, AutoJs6-Plugin-Three-Paper-PDF, AutoJs6-Plugin-Three-Prism-Camera, AutoJs6-Plugin-Three-Slate-Studio, AutoJs6-Plugin-Three-Tally-Sheet. 后四个是 2026-10-22 / 23 立项的开发中插件, 进入索引时按本文件口径补一节 (多数会以 M3 宿主模块进分母).

## 6. 汇总表

"自有条目" 只计不在分母的入口. "结论" 列为 Q2 拍板结果 (2026-10-29, 见第 8 节).

| 插件 | 机制 | 自有条目 | 文档 | d.ts | Ace | Node | Bun | 已在分母 (M3) | 建议 | 结论 |
|---|---|---:|---|---|---|---|---|---|---|---|
| ImGui | M1 + 全局 imgui | 458 名称 (约 349 行) | 0 | 0 | 0 | 0 | 0 | 无 | 纳入 (Q2-1) | 纳入; 维护者另开会话把 ImGui 改为宿主注册的全局模块 (能力交插件), 落地后经 KSP 进分母, P5.2f 等它 |
| Three-Macaw-Speech | M2 | 6 工具 | 0/6 | 0/6 | 0/6 | 0/6 | 0/6 | speech 27 | 工具表, 不逐工具进矩阵 (Q2-2) | 工具表, 不进矩阵 (P5.2a) |
| Three-Stack-Archive | M2 | 3 工具 | 0/3 | 0/3 | 0/3 | 0/3 | 0/3 | 无 | 同上 | 工具表, 不进矩阵 (P5.2a) |
| Three-Hanzi-CC | M2 | 1 工具 | 示例 1 | 0/1 | 0/1 | 0/1 | 0/1 | opencc 33 | 同上 | 工具表, 不进矩阵 (P5.2a) |
| Yolo-NCNN | M2 | 1 工具 | 0/1 | 0/1 | 0/1 | 0/1 | 0/1 | yolo 1 + 2 | 同上 | 工具表, 不进矩阵 (P5.2a) |
| Image-Quantization | M2 | 1 工具 | 示例 1 (参数名错) | 0/1 | 0/1 | 0/1 | 0/1 | images 2 + 10 | 同上 | 工具表, 不进矩阵 (P5.2a) |
| NodeJs-Runtime | M4 | 110 | 0/110 | 109/110 | 109/110 | 110/110 | 不适用 | 无 (矩阵列) | 附表, 补文档小节与 WorkerPool (Q2-3) | 附表跟踪, 只补缺口 (P5.2b) |
| Bun-Runtime | M4 | 12 | 5/12 提及 | 12/12 | 12/12 | 不适用 | 12/12 | 无 (矩阵列) | 同上 | 附表跟踪, 只补缺口 (P5.2b) |
| QuickJS-Runtime | M4 | 7 | 0/7 | 0/7 | 0/7 | 不适用 | 不适用 | 无 | 同上 | 附表跟踪, 只补缺口 (P5.2b) |
| Python-Runtime | M4 (Python) | 46 函数 | 0/46 | 不适用 | 不适用 | 不适用 | 不适用 | 无 | 不纳入本主题 (Q2-4) | 纳入本主题 (维护者选非推荐项; P5.2d / P5.2e, 门禁 Q77) |
| AutoJs4-Runtime | M4 (上游 Auto.js 4) | 约 180 名称 | 0 | 不适用 | 不适用 | 不适用 | 不适用 | 无 | 同上 | 不纳入 (Q76; 上游原样 API, 补全与文档以 AutoJs6 为准) |
| Java-Runtime | M4 (Java) | 17 方法 | 0/17 | 不适用 | 不适用 | 不适用 | 不适用 | 无 | 同上 | 纳入本主题 (P5.2d / P5.2e) |
| Kotlin-Runtime | M4 (Kotlin) | 13 方法 | 0/13 | 不适用 | 不适用 | 不适用 | 不适用 | 无 | 同上 | 纳入本主题 (P5.2d / P5.2e) |
| Lua-Runtime | M4 (Lua) | 12 | 0/12 | 不适用 | 不适用 | 不适用 | 不适用 | 无 | 同上 | 纳入本主题 (P5.2d / P5.2e) |
| 第 3 节 36 个插件 | 无 / M3 | 0 | - | - | - | - | - | 见第 3 节 | 不纳入 (无自有 API) | 无自有脚本 API |

合计: 50 个插件中 14 个有自有入口 (JS 可见: ImGui 458 名称, 工具 12, Node 110, Bun 12, QuickJS 7; 其他语言: Python 46, Java 17, Kotlin 13, Lua 12, AutoJs4 约 180), 36 个没有.

## 7. Q2 选择题材料

先问条目多, 影响大的组; 条目少且理由相同的插件合并为一题. 工作量按 P2 ~ P4 的规则估算, "子会话" 指 P5.2 的一个批次.

### Q2-1 ImGui (458 个名称, 约 349 行; 五处全空)

- (a) 纳入, 按 P2 ~ P4 规则 (推荐): 文档新增 imgui 页与对象类型页, 约 349 个标题 + 签名 + 示例 (194 个 PascalCase 别名并入驼峰条目); d.ts 复用插件自带 imgui.d.ts (458 名称, 补 4 个对象方法); Ace 捆绑同一文件并重建索引; Node / Bun 约 349 行用一条 not-portable (rhino-only) 规则; 工具: extract_exports 增加 pluginExports 来源读 imgui.d.ts (约 80 行). 约 3 个子会话 (文档 2, 声明与工具 1), 真机只需一台装 ImGui 做冒烟.
- (b) 纳入, 文档降为概览: d.ts / Ace / Node / Bun 同 (a); 文档只加一页概览 + 由 d.ts 生成的成员表 (无逐条示例), 矩阵 doc 列记 mention, runtime-policy 登记该例外 (偏离 D3). 约 1 个子会话.
- (c) 暂缓: 本期只保留本文件记录, ImGui 下个版本或宿主 6.8.0 公开后再定.
- (d) 不纳入: 保持插件自带 d.ts 与 README, 宿主文档只在 plugins.md 提一句.

### Q2-2 plugins.tools 工具 (5 个插件 12 个工具: Macaw 6, Stack-Archive 3, Hanzi-CC 1, Yolo 1, Image-Quantization 1)

- (a) 工具表, 不逐工具进矩阵 (推荐): plugins.md 加 "官方插件工具表" 12 行 (名称, 提供插件, 最低版本, 参数, 输出; 由各 catalog 生成或手写) 并修正第 306 行示例; d.ts / Ace 可选加 12 个工具名字面量联合 (不计覆盖); Node / Bun 不新增 (通用 4 行仍由 P3 / P4 补). 约 0.5 个子会话, 真机一台装 5 个插件核对 `plugins.tools.list()` 与表一致.
- (b) 逐工具进矩阵: 12 行; 文档 12 个标题 + 示例, d.ts / Ace 12 个按工具名的类型化重载 (参数类型从 JSON Schema 手写), Node / Bun 各 12 个类型化入口 (依赖通用 call 先桥接). 约 1.5 个子会话, 每次插件改工具都要同步.
- (c) 不纳入: 只修正 plugins.md 示例.

### Q2-3 JS 运行时自有入口 (Node 110, Bun 12, QuickJS 7)

- (a) 不进分母, 本文件附表跟踪, 只补缺口 (推荐): 文档站 runtimeApis.md 加 "运行时自有接口" 小节 (按组表格列 Node 12 组 110 条, Bun 12 条, QuickJS 7 条, 不逐条标题); d.ts / Ace 补 Node `worker_threads.WorkerPool` 1 条; QuickJS 的 scriptArgs 写进文档 (Ace 没有 QuickJS 专用补全组, 不补). 约 0.5 个子会话, 不需设备.
- (b) 按 P2 规则签名级: 129 个文档标题 + 示例, d.ts / Ace 补 WorkerPool 1 条 + QuickJS 7 条 (新声明组), 门禁加 "运行时自有" 附表校验 (约 60 行). 约 1.5 个子会话.
- (c) 不纳入: 保持各插件仓库文档 (Node HOST-API.md, Bun host-api.md).

### Q2-4 非 JS 语言运行时 (Python 46 函数, Java 17, Kotlin 13, Lua 12, AutoJs4 约 180 名称)

- (a) 不纳入本主题, 缺口交各插件路线图 (推荐): 0 工作量; 本文件记录缺口 (Python 无 .pyi, Lua 无 LuaLS 定义, Kotlin 无 API 表, 文档站无语言 API 页), AutoJs4 是上游原样 API 不补.
- (b) 只补文档站: 各加一页语言 API 表 (Python 46 / Java 17 / Kotlin 13 / Lua 12, 共 88 条, 不含 AutoJs4). 约 1 个子会话.
- (c) 纳入本主题: 文档页 + 类型桩 (Python .pyi, LuaLS 定义) + Ace 对应语言补全. 约 2 ~ 3 个子会话, 改 4 个插件仓库与 Ace.

### 无需出题的部分

- 第 3 节 36 个插件没有自有脚本 API, 结论直接记为 "无自有脚本 API".
- Q74 / Q75 是宿主分母缺口, 不属 Q2, 与 Q2 同轮请维护者拍板 (见附录 C).

## 8. 拍板结果 (2026-10-29, 两轮选择题) 与 P5.2 分批

### 8.1 结论

| 题 | 维护者选择 | 影响 |
|---|---|---|
| Q2-1 ImGui | 纳入. 维护者另开会话把 ImGui 改为类似 pinyin / mediainfo 的形式: 宿主直接注册全局模块, 背后能力交给插件, 不再依赖 `plugins.load` | 改造落地后 ImGui 经 KSP 以 M3 进分母 (不需要 pluginExports 来源), 再按 P2 ~ P4 规则补文档 / d.ts / Ace 与 Node / Bun 状态; P5.2f 等该会话完成 |
| Q2-2 plugins.tools 工具 | 工具表, 不进矩阵 (推荐) | P5.2a |
| Q2-3 JS 运行时自有入口 | 附表跟踪, 只补缺口 (推荐) | P5.2b |
| Q2-4 非 JS 语言运行时 | 纳入本主题 (非推荐项): 文档页 + 类型桩 (Python .pyi, LuaLS 定义, Java / Kotlin 桩) + Ace 对应语言补全 | P5.2d / P5.2e, 改 Python / Java / Kotlin / Lua 4 个插件仓库与 Ace |
| Q76 AutoJs4 上游 API | 不纳入 (推荐) | autojs4Runtime.md 可选补 "与 AutoJs6 的主要差异" 指引, 随 P5.2d |
| Q77 语言 API 防漂移 | 加静态门禁 (推荐) | P5.2d 新增检查: 从 Python `__all__`, Lua `install_autojs_module`, `JvmEntryApi.kt` 提取清单, 校验文档页标题与类型桩一致 |
| Q74 OcrResult / WrappedBarcode | 补进返回对象表 (推荐) | P5.2c, 矩阵约 +27 行 |
| Q75 compose 运行时目录 | 纳入分母 (推荐) | P5.2c, 矩阵 +83 行 |

纳入合计: ImGui 约 349 行 (改造后以宿主模块计), 工具 12 条 (工具表, 不进矩阵), JS 运行时自有 129 条 (附表), 语言运行时 88 条 (附表 + 门禁), 宿主分母补漏约 110 行 (Q74 约 27 + Q75 83). 不纳入: AutoJs4 上游 API 约 180 名称, 以及第 3 节 36 个无自有 API 的插件.

### 8.2 P5.2 分批建议 (每批 1 个子会话)

| 批次 | 插件 | 内容 | 改动仓库 | 设备与插件安装 | 依赖 / 并行 |
|---|---|---|---|---|---|
| P5.2a 工具表 [静态 + 1 台核对] | Three-Hanzi-CC, Yolo-NCNN, Image-Quantization, Three-Stack-Archive, Three-Macaw-Speech (5) | plugins.md "官方插件工具表" 12 行 (名称, 提供插件, 最低版本, 参数, 输出), 修正第 306 行示例 (`maxColors`, 输出 `png`); d.ts / Ace 加工具名字面量联合 (保留 string, 不计覆盖); 生成离线文档 | Documentation, Offline-Docs, TS 声明, Ace | 一台主力设备 (Redmi 或 Xiaomi) 装齐 5 个插件, `plugins.tools.list()` 核对名称与参数; Macaw 的两个说话人工具需 1.3.0 构建 (未发布时表内最低版本写 1.3.0 并只核对其余 10 个) | 可与 P5.2b / P5.2c / P5.2d 并行; Ace 索引重建与 P5.2b 排队; 落点 (2026-10-30, api-cov-p5-2a): 文档 014ed75 (工具表 + 示例) / 8998510 (changelog / progress) / c1c2fe2 (生成物), Offline Docs 20253b2, TS e6cb1a5, Ace ee1144b, 宿主 c0b3a686e (Ace 补全源); 工具 15 个 (Macaw P8.4 新增 3 个, Q156), 真机 OnePlus 63751f63 list() 15 / 15 通过, 附表见第 10 节 |
| P5.2b JS 运行时附表 [静态] | NodeJs-Runtime, Bun-Runtime, QuickJS-Runtime (3) | runtimeApis.md 加 "运行时自有接口" 小节 (按组表格: Node 12 组 110 条, Bun 12 条, QuickJS 7 条, 重点写 scriptArgs); Node `worker_threads.WorkerPool` 补 d.ts (Node 插件 `docs/nodejs/types/autojs6-node`, TS 声明 autojs6-node 同步, Ace 捆绑副本); 本文件加附表 | Documentation, Offline-Docs, Node 插件 (d.ts), TS 声明, Ace | 无 | 与 P3 系列错开 Node 插件 d.ts 文件 (共享 checkout 先在 locks.md 登记); 落点 (2026-10-30, api-cov-p5-2b): 文档 b31e71b / 62455f7 / 10bc48b, Offline Docs 836e334, Node 9e6e1a32, TS baf2100, Ace 79a115b |
| P5.2c 宿主分母补漏 [静态] | MLKit-OCR / Paddle / Rapid 系列, MLKit-Barcode, Three-Block-Compose (只作来源) | Q74: `returned-objects.json` 加 OcrResult# 与 WrappedBarcode# (producers 写 ocr.detect 系列与 barcode.detect 系列), 约 +27 行; Q75: `extract_exports.py` 读 compose-ui-api 目录契约 (V1Catalog / ComposeUiV2 / ComposeModifier) 生成 83 行, 核对五处覆盖; 重新生成 MATRIX.md 与 pending-baseline | 宿主 tools/api-coverage, docs/api-coverage | 无 | MATRIX 重新生成与同期其他子会话排队; 新增行若有 pending 交对应 P3 / P4 批次; 落点 (2026-10-30, api-cov-p5-2c): 宿主 efa050e2b (来源与返回对象) / e50f26694 (MATRIX 与基线) / 552f2b489 (typeScope) / a211654e7 (Ace 索引源), TS 6c93f5d, Ace cd6f5f6 / 81e6fe6, 文档 78a1f70 / 62af947, Offline Docs 69438ca; 新增 110 行 (矩阵 2836 -> 2946), pending 469 / 538 / 558 -> 477 / 546 / 566 (ComposeNode# 8 个快捷属性, 交 P3.3 / P4.3, Q104); compose 其余返回对象另登记 Q106 |
| P5.2d 语言运行时文档与门禁 [静态] | Python-Runtime, Java-Runtime, Kotlin-Runtime, Lua-Runtime (4) | 文档站新增语言运行时 API 页 (Python 46 函数 + 常量 / 异常, Java 17, Kotlin 13, Lua 12, 签名 + 示例); Q77 静态门禁 (tools/api-coverage 新检查约 120 行 + CI 一步, 先校验文档标题); autojs4Runtime.md 可选补差异指引 (Q76) | Documentation, Offline-Docs, 宿主 tools/api-coverage | 无 | 可与 P5.2a / b / c 并行. 落点 (2026-10-30, api-cov-p5-2d): 宿主 3fa1fc8ad (门禁 + 名称清单 + CI), 文档 fee509b (四页 + autojs4Runtime 指引) / 4e9a3d7 (生成物), Offline Docs a25a1ff; 附表见 2.4 节末 |
| P5.2e 语言类型桩与 Ace 补全 [静态 + 1 台抽查] | Python-Runtime, Lua-Runtime, Java-Runtime, Kotlin-Runtime (4) + Ace | Python `.pyi` (插件仓库), LuaLS `---@meta` 定义 (插件仓库), Java / Kotlin 接口桩并入 Ace 的 ECJ / Kotlin 补全 classpath, Ace 捆绑 Pyright / LuaLS 类型; P5.2d 门禁扩展到类型桩 | 4 个插件仓库, Ace | 一台主力设备装 Ace 与 4 个语言运行时, 在编辑器里抽查补全 (每种语言 3 个名称) | 依赖 P5.2d 的清单与门禁 |
| P5.2f-1 ImGui 纳入矩阵 / 基线 / 门禁 [静态] | ImGui (1) | Q115 (a) 拆出的第一步: imgui 宿主模块 (61317ec4d) 的 464 个 KSP 安装项 (含 R1.1 的 dp / px / density, 25eac064a)进 MATRIX.md (463 个公开导出三列 pending, `imgui.__pluginId` 记内部导出, Q123), 新增 1389 格一次性计入 pending-baseline.json (Q122, Node / Bun 终态见 Q124), 名称门禁按 policy.json `deferredNameGate` 暂缓到 P5.2f-2 (Q121), 文档站最小 imgui 页与运行时支持表 (Q125); 顺带 Q118 util.class / className / getClassName 排除 | 宿主 tools/api-coverage, docs/api-coverage; Documentation, Offline-Docs | 无 | 已完成 (api-cov-p5-2f1), 数字与提交见 BASELINE 第 12 节 |
| P5.2f-2 ImGui 文档 / d.ts / Ace [静态 + 1 台冒烟, 约 2 个子会话] | ImGui (1) | 等 imgui 看板 R0-R8 的 API 稳定 (Q115 (a)): 文档 imgui 页正文与对象类型页 (约 349 个标题 + 示例, 194 个别名并入驼峰条目), 挂侧栏 / 目录 / changelog (Q125); d.ts / Ace 由插件 imgui.d.ts 改写为模块声明 (补 4 个对象方法); 构造出的对象 (会话 / 悬浮按钮 / 纹理 / DrawList 等约 85 个成员) 登记 returned-objects.json 进分母; 删掉 policy.json `deferredNameGate` 的 imgui (Q121, 根无缺口时门禁会要求删除); Node / Bun 状态定案 (Q124) | Documentation, Offline-Docs, TS 声明, Ace, 宿主 tools/api-coverage | 一台主力设备装 ImGui, 文档示例冒烟 | 待发, 依赖 imgui R 系列; 文档量大可拆两批 |

P5.3 收尾时 bump 被改到的仓库 (D19): 文档 / Offline-Docs / TS 声明 / Ace, 以及 P5.2b / P5.2e 改到的 Node / Python / Java / Kotlin / Lua 插件.

## 9. JS 运行时自有接口附表 (P5.2b, 2026-10-30)

按 Q2-3 (a), Node / Bun / QuickJS 运行时插件的自有入口不进矩阵分母, 在本表跟踪. 文档 = 文档站 `api/runtimeApis.md` 的 "运行时自有接口" 节 (每条一个表格行, 不设逐条标题); d.ts = Node 插件 `docs/nodejs/types/autojs6-node` 与 TS 声明 `declarations/autojs6-node`, Bun 插件 `app/src/main/assets/autojs6/host-client.d.mts` 与 TS 声明 `declarations/autojs6-bun` (两处逐字相同, 摘要见 `declarations/runtime-declarations.json`); Ace = Ace 捆绑副本 `autojs6/types/autojs6-node` / `autojs6-bun` (由 TS 仓库 `tools/sync-runtime-declarations.mjs --ace` 同步, 摘要与 TS 相同). 最低版本是首个含该入口的插件版本 (开发版本号, 按各版本最后一个提交的声明与源码判定).

计数: Node 113 (P5.1 记 110; 另计 `worker_threads` 主线程门面的 `policy` / `nativeAvailable` / `diagnostics` 3 条, 见 ROADMAP 附录 C Q99), Bun 12, QuickJS 7, 共 132.

| 运行时 | 组 | 入口 | 条数 | 最低版本 | 文档 | d.ts | Ace |
|---|---|---|---:|---|---|---|---|
| Node | `autojs6:bridge` | callAutoJs, permissions | 2 | 1.0.0 (Worker 1.6.0) | 有 2/2 | 有 2/2 (bridge.d.ts) | 有 2/2 |
| Node | `autojs6:host` | on, once, addListener, prependListener, prependOnceListener, off, removeListener, removeAllListeners, listenerCount, ref, unref | 11 | 1.3.0 | 有 11/11 | 有 11/11 (host.d.ts) | 有 11/11 |
| Node | `autojs6:lifecycle` | policy, checkpoint, readCheckpoint, clearCheckpoint, onCheckpoint, onRestore, onStop | 7 | 1.0.0 | 有 7/7 | 有 7/7 (lifecycle.d.ts) | 有 7/7 |
| Node | `autojs6:profile` | 模块本身 (能力快照) | 1 | 1.0.0 | 有 1/1 | 有 1/1 (autojs_global.d.ts) | 有 1/1 |
| Node | `$autojs` 自有 | schema, profile, androidContext | 3 | 1.0.0 | 有 3/3 | 有 3/3 (autojs_global.d.ts) | 有 3/3 |
| Node | `package_manager` | status, policy, sourcePolicy, planInstall, planUpdate, planRemove, planRegistryInstall, planTarballInstall, install, update, remove, list, verify, prune | 14 | 1.0.0 (install / update / remove 的 npm 包规格形态 1.6.0) | 有 14/14 | 有 14/14 (package_manager.d.ts) | 有 14/14 |
| Node | `npm` | package_manager 的 14 个名称, packageManager, cli, exec, runScript, installFromRegistry, publish, pack | 21 | 1.0.0; cli / exec / runScript / installFromRegistry / publish / pack 1.6.0 (此前名称在但一律拒绝) | 有 21/21 | 有 21/21 (npm.d.ts) | 有 21/21 |
| Node | `plugins` (本地 JS 插件) | install, enable, disable, isEnabled, configure, config, version, dependencies, exports, capabilities, status, start, stop, reload, unload, list, manifest, resolve, load | 19 | 1.0.0 | 有 19/19 | 有 19/19 (plugins.d.ts) | 有 19/19 |
| Node | `rhino` | name, version, schema, mode, enabled, errorCodes, isEnabled, status, run, install, importClass, importPackage, JavaAdapter | 13 | 1.0.0 | 有 13/13 | 有 13/13 (rhino.d.ts) | 有 13/13 |
| Node | `autojs6:compat` 控制成员 | name, version, mode, errorCodes, status, install, unsupported, blockedByDesign, assertSupported, selector (其余 89 个是宿主 API 再导出, 不计) | 10 | 1.0.0 | 有 10/10 | 有 10/10 (rhino_compat.d.ts) | 有 10/10 |
| Node | `media_store` | capabilities, query, get, insert, update, delete, remove, exportFile (scanFile 对应宿主 media.scanFile, 不计) | 8 | 1.5.5 | 有 8/8 | 有 8/8 (media_store.d.ts) | 有 8/8 |
| Node | `worker_threads` 主线程扩展 | WorkerPool, policy, nativeAvailable, diagnostics | 4 | 1.0.0 | 有 4/4 | 有 4/4 (worker_threads.d.ts, P5.2b 新增) | 有 4/4 (P5.2b 新增) |
| Bun | 客户端模块导出与环境变量 | createHostClient, SDK_VERSION, HOST_API_METHODS, HostCallError, OPENCC_CONVERSION_TYPES, AUTOJS6_HOST_CLIENT_MODULE | 6 | 0.2.6 | 有 6/6 | 有 6/6 (host-client.d.mts; 环境变量为文件头说明, process.env 的类型来自 Bun 类型包) | 有 6/6 (环境变量由 Ace runtime-bun 组声明) |
| Bun | `HostClient` 非宿主成员 | sdkVersion, bridgeVersion, capabilities, limits, hasCapability, call | 6 | 0.2.6 | 有 6/6 | 有 6/6 | 有 6/6 |
| QuickJS | 全局输出 | console.debug / info / log / warn / error, print | 6 | 1.0.0 | 有 6/6 | 不适用 (Q2-3 (a): 不设 QuickJS 声明组) | 不适用 (同左) |
| QuickJS | 入参 | scriptArgs | 1 | 1.0.0 | 有 1/1 | 不适用 | 不适用 |

合计: 文档 132/132 (P5.1 时 Node 0/110, Bun 5/12 仅正文提及, QuickJS 0/7); d.ts 与 Ace 各 125/125 适用条目 (Node 113 + Bun 12; P5.1 时 Node 109/110, 缺 WorkerPool), QuickJS 7 条按 Q2-3 (a) 不适用. 无 "缺".

维护: 运行时插件增删自有入口时同步本表与 runtimeApis.md 的对应表格. 本表不在 `check.py` 门禁内 (Q2-3 (a) 未加附表校验); Node / Bun 的宿主模块成员仍由矩阵的 Node / Bun 列衡量.

## 10. 官方插件工具表附表 (P5.2a, 2026-10-30)

按 Q2-2 (a), `plugins.tools` 的官方插件工具不进矩阵分母, 在本表跟踪. 文档 = 文档站 `api/plugins.md` "插件工具" 节下的 "官方插件工具表" (每个工具一个表格行: 说明, 提供插件, 最低插件版本, 参数 (类型, 必填 / 默认), 结果字段与文件 / 目录输出; 文档 014ed75); d.ts = TS 声明 `declarations/autojs6/aj6-int-plugins.d.ts` 的 `Plugins.OfficialToolName` 字面量联合, `call` / `callAsync` 的 `name` 改为 `ToolName = OfficialToolName | (string & {})` (仍接受任意字符串, 只为补全, 不计覆盖; TS e6cb1a5); Ace = 捆绑副本同一文件, 静态索引与 `lib.autojs6.d.ts` 的签名随之改为 `ToolName` (Ace ee1144b, 宿主补全源 c0b3a686e). Node / Bun 不新增 (Q2-2 (a); 通用 4 个函数仍由 P3 / P4 的矩阵列衡量).

参数与输出取自各插件仓库工具目录源码 (只读): Hanzi-CC `OpenccToolCatalog.kt` (02d31bd), Yolo-NCNN `tool/YoloToolCatalog.kt` (d8639af), Image-Quantization `tool/ImageQuantToolCatalog.kt` (1ab9fe3), Stack-Archive `tool/ArchiveToolCatalog.kt` (2a32b30), Macaw `tool/SpeechToolCatalog.kt` (1294e22; `SpeechLimits` 常量取宿主 `plugin-api/speech-api`). 最低插件版本 = 首次加入该工具的提交所在的 VERSION_NAME. 计数: 15 个工具 (第 2.2 节与 Q2-2 记 12 个, 之后 Macaw 1.3.0 的 P8.4 (6520621) 新增 `speech_diarize` / `speech_detect_language` / `speech_tag`, 见附录 C Q156). 最低版本中只有 Macaw 1.2.0 已发布 (官方索引: Hanzi-CC 2.0.0, Yolo 0.1.3, Image-Quantization 1.0.3, Stack-Archive 3.0.0, Macaw 1.2.0), 表内照写版本号, 不标 "未发布" (附录 C Q157).

真机核对 (2026-10-30, OnePlus 63751f63 OPD2413 API 35, 宿主 debug 5935; 已装 Hanzi-CC 2.1.0 / 145, Yolo 0.2.0 / 62, Image-Quantization 1.1.0 / 43, Stack-Archive 3.1.0 / 127, Macaw 1.3.0 / 154 debug, 本会话未装卸任何包): Rhino 脚本比对 `plugins.tools.list()` 与目录源码的工具名, 提供方包名, 参数名, `type`, `x-autojs6-type`, `default`, `required` 与输出名 / `kind`, 15 / 15 通过; `plugins.tools.providers()` 中 5 个提供方均为 `available` / `official`. 设备上另有第三方工具示例 javasample / ktsample 的 6 个工具 (`user_granted`), 不属官方工具表. 脚本 `device_tool_table.py` 存于共享笔记目录 (`p5-2a-device_tool_table.py`), 用 `check -s <serial> --expected <catalogs.json>` 重跑 (catalogs.json 由同目录 `p5-2a-extract_catalogs.py` 从各插件仓库的目录源码求值生成).

| 工具 | 提供插件 (版本, HEAD) | 最低插件版本 | 参数个数 | 文档行 | d.ts | Ace | 真机 list() |
|---|---|---|---:|---|---|---|---|
| `opencc_convert` | 3-Hanzi CC (2.1.0 / 150, 02d31bd) | 2.1.0 (f7cd98d) | 2 | 有 | 有 | 有 | 通过 |
| `yolo_detect` | AutoJs6 YOLO NCNN (0.2.0 / 63, d8639af) | 0.2.0 (c8a0a0e) | 6 | 有 | 有 | 有 | 通过 |
| `imagequant_quantize` | Image Quantization (1.1.0 / 43, 1ab9fe3) | 1.1.0 (1ab9fe3) | 9 | 有 (示例参数名已修正: `maxColors`, 输出 `png`) | 有 | 有 | 通过 |
| `archive_list` | 3-Stack Archive (3.1.0 / 131, 2a32b30) | 3.1.0 (3246acc) | 5 | 有 | 有 | 有 | 通过 |
| `archive_read` | 同上 | 3.1.0 (3246acc) | 4 | 有 | 有 | 有 | 通过 |
| `archive_extract` | 同上 | 3.1.0 (3246acc) | 3 | 有 | 有 | 有 | 通过 |
| `speech_listen` | 3-Macaw Speech (1.3.0 / 156 开发中, 1294e22) | 1.2.0 (e2ddd72) | 6 | 有 | 有 | 有 | 通过 |
| `speech_speak` | 同上 | 1.2.0 (e2ddd72) | 5 | 有 | 有 | 有 | 通过 |
| `speech_transcribe` | 同上 | 1.2.0 (e2ddd72) | 3 | 有 | 有 | 有 | 通过 |
| `speech_synthesize` | 同上 | 1.2.0 (e2ddd72) | 5 | 有 | 有 | 有 | 通过 |
| `speech_speaker_verify` | 同上 | 1.3.0 (P8.3, 806aae5) | 3 | 有 | 有 | 有 | 通过 |
| `speech_speaker_identify` | 同上 | 1.3.0 (P8.3, 806aae5) | 3 | 有 | 有 | 有 | 通过 |
| `speech_diarize` | 同上 | 1.3.0 (P8.4, 6520621) | 4 | 有 | 有 | 有 | 通过 |
| `speech_detect_language` | 同上 | 1.3.0 (P8.4, 6520621) | 2 | 有 | 有 | 有 | 通过 |
| `speech_tag` | 同上 | 1.3.0 (P8.4, 6520621) | 2 | 有 | 有 | 有 | 通过 |

合计: 文档 15/15 (P5.1 时 2/12, 仅示例提及), d.ts 15/15 与 Ace 15/15 (只为补全, 不计覆盖; P5.1 时 0/12), 真机 15/15. 无 "缺".

维护: 插件增删工具或改参数时, 同步 plugins.md 工具表, `OfficialToolName` (TS 声明, 再按 Q23 导入 Ace) 与本表; 本表不在 `check.py` 门禁内 (Q2-2 (a) 未加附表校验), 漂移靠真机脚本重跑发现.
