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
- 建议: 不逐工具进矩阵. 工具是动态目录 (随插件版本变化, 如 Macaw 1.3.0 新增两个说话人工具; 非官方插件也能提供), 参数签名本身是 JSON Schema 并由宿主调用前校验. 改为 plugins.md 增加 "官方插件工具表" (12 行: 名称, 提供插件, 最低版本, 参数, 输出, 可从各 catalog 生成) 并修正上面的示例; d.ts 可加工具名字面量联合 (保留 string 后备) 只用于补全, 不计覆盖. 详见 Q2-2.

### 2.3 JS 运行时的自有入口 (Node / Bun / QuickJS)

这三个插件对宿主 API 的映射由矩阵的 Node main / Node Worker / Bun 列衡量, 下面只列没有宿主模块对应的运行时自有入口.

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

| 编号 | 发现 | 条目 | 建议 |
|---|---|---|---|
| Q74 | `ocr.detect` 等与 `barcode.detect` 等在 KSP 元数据里返回 `kotlin.Any`, `tools/api-coverage/returned-objects.json` 的 producers 没有列它们, 宿主 `OcrResult` (text / confidence / bounds / label 4 个字段) 与 `WrappedBarcode` (约 23 个成员, `barcode` 字段是插件契约的 BarcodeResult) 不在返回对象表 (现 43 个对象). 文档对 WrappedBarcode 有 23/23 标题, d.ts 两者都有 (`aj6-int-barcode.d.ts`, `libraries.d.ts`) | 约 27 行 | 补进 returned-objects.json (静态, 一个小条目) |
| Q75 | compose 的运行时目录 (installComputed: V1 30 + V2 25 + AndroidView 1 = 56 个组件, 其中 55 个 NODE 组件安装为 `compose.<Name>`; `compose.modifier()` 链 20 个修饰符; 8 个属性快捷名) 不在分母, REVIEW 第 35 行只注明 "不在分母". 文档 56/56 + 20/20 (composeComponents.md, composeModifierType.md), d.ts / Ace / Node / Bun 都以 `ComposeFactories` 映射类型的键覆盖 | 83 行 | 纳入分母 (extract_exports 读目录契约, 预计五处已覆盖, 价值在防目录与声明漂移) |
| (Q46 已登记) | `ai.agent` 的 9 个根成员不在 exports.json | 9 行 | 按 Q46 处理, 不重复 |
| (文档错误) | plugins.md 第 306 行 `imagequant_quantize` 示例参数名错 (见 2.2) | 1 处 | 随 Q2-2 的落地批次修正 |

## 5. 不在索引的本地插件仓库 (不在本次范围)

AutoJs6-Plugin-Bundled-Libraries, AutoJs6-Plugin-Three-Paper-PDF, AutoJs6-Plugin-Three-Prism-Camera, AutoJs6-Plugin-Three-Slate-Studio, AutoJs6-Plugin-Three-Tally-Sheet. 后四个是 2026-10-22 / 23 立项的开发中插件, 进入索引时按本文件口径补一节 (多数会以 M3 宿主模块进分母).

## 6. 汇总表

"自有条目" 只计不在分母的入口. "结论" 列在 Q2 拍板后填写.

| 插件 | 机制 | 自有条目 | 文档 | d.ts | Ace | Node | Bun | 已在分母 (M3) | 建议 | 结论 |
|---|---|---:|---|---|---|---|---|---|---|---|
| ImGui | M1 + 全局 imgui | 458 名称 (约 349 行) | 0 | 0 | 0 | 0 | 0 | 无 | 纳入 (Q2-1) | 待拍板 |
| Three-Macaw-Speech | M2 | 6 工具 | 0/6 | 0/6 | 0/6 | 0/6 | 0/6 | speech 27 | 工具表, 不逐工具进矩阵 (Q2-2) | 待拍板 |
| Three-Stack-Archive | M2 | 3 工具 | 0/3 | 0/3 | 0/3 | 0/3 | 0/3 | 无 | 同上 | 待拍板 |
| Three-Hanzi-CC | M2 | 1 工具 | 示例 1 | 0/1 | 0/1 | 0/1 | 0/1 | opencc 33 | 同上 | 待拍板 |
| Yolo-NCNN | M2 | 1 工具 | 0/1 | 0/1 | 0/1 | 0/1 | 0/1 | yolo 1 + 2 | 同上 | 待拍板 |
| Image-Quantization | M2 | 1 工具 | 示例 1 (参数名错) | 0/1 | 0/1 | 0/1 | 0/1 | images 2 + 10 | 同上 | 待拍板 |
| NodeJs-Runtime | M4 | 110 | 0/110 | 109/110 | 109/110 | 110/110 | 不适用 | 无 (矩阵列) | 附表, 补文档小节与 WorkerPool (Q2-3) | 待拍板 |
| Bun-Runtime | M4 | 12 | 5/12 提及 | 12/12 | 12/12 | 不适用 | 12/12 | 无 (矩阵列) | 同上 | 待拍板 |
| QuickJS-Runtime | M4 | 7 | 0/7 | 0/7 | 0/7 | 不适用 | 不适用 | 无 | 同上 | 待拍板 |
| Python-Runtime | M4 (Python) | 46 函数 | 0/46 | 不适用 | 不适用 | 不适用 | 不适用 | 无 | 不纳入本主题 (Q2-4) | 待拍板 |
| AutoJs4-Runtime | M4 (上游 Auto.js 4) | 约 180 名称 | 0 | 不适用 | 不适用 | 不适用 | 不适用 | 无 | 同上 | 待拍板 |
| Java-Runtime | M4 (Java) | 17 方法 | 0/17 | 不适用 | 不适用 | 不适用 | 不适用 | 无 | 同上 | 待拍板 |
| Kotlin-Runtime | M4 (Kotlin) | 13 方法 | 0/13 | 不适用 | 不适用 | 不适用 | 不适用 | 无 | 同上 | 待拍板 |
| Lua-Runtime | M4 (Lua) | 12 | 0/12 | 不适用 | 不适用 | 不适用 | 不适用 | 无 | 同上 | 待拍板 |
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
