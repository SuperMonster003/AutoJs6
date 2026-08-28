******

### 发行历史

******

# v6.8.0

###### 2026/08/28

* `新增` TypeScript 依赖快照现会拒绝原生 addon 信号 (`.node`, `binding.gyp` 与 `gypfile`) 和安装生命周期 hook (`preinstall`, `install` 与 `postinstall`), 提供稳定错误码及纯 JavaScript/WASM 替代指引; 普通 package script 仍仅作为惰性元数据保留
* `新增` 宿主 TypeScript 依赖契约已同步 resolver policy revision 3, 并包含 dayjs, lodash, zod, uuid 与 nanoid 真实发布 manifest 覆盖, 锁定 Rhino CommonJS 入口选择和稳定的 ESM-only 错误
* `新增` TypeScript 项目编译与 Ace 现共用同一冻结 `node_modules` 类型层: package `types`/`typings`, TypeScript 6 `typesVersions`, 嵌套声明与已安装 `@types` 在 Rhino 和 Node profile 中产生一致的 dayjs 补全, hover 与 strict TS2322 诊断
* `新增` Lua Runtime 宿主能力新增按文件脚本隔离的持久键值存储与 Toast: `autojs.storage.get/put/remove/clear` 使用应用私有, 同步提交且有界的命名空间, `autojs.ui.toast` 通过宿主主线程单次派发
* `新增` Ace LSP 默认文件类型新增 .mts/.cts 及其声明文件变体, 使外部 Ace 编辑器可选择与执行一致的 Rhino/Node TypeScript profile; 旧版默认选择会自动迁移, 显式自定义后缀集合保持不变
* `新增` APK Builder 支持在打包阶段编译 Rhino TypeScript 项目; 最终 APK 仅包含加密 JavaScript 与不内嵌源码的可选 Source Map, 不包含 TypeScript 源码或编译器; 加密项目使用一次性 AES-256-GCM staging, 解密与重加密明文仅存于内存并在使用后清零
* `新增` Node.js Runtime 插件, 内置 Node.js 24.5.0 运行时, 支持识别 "nodejs" 模式参数或识别文件扩展名 (.node.js/.cjs/.mjs) 运行 Node.js 脚本
* `新增` Node.js Runtime 调试构建支持按次执行显式开启仅绑定本机回环地址的 Inspector/CDP 会话, release 构建, 远程监听及阻塞等待保持禁止
* `新增` 独立 TypeScript 编译插件客户端, 校验冻结的 AIDL/R2 归档协议, 支持 UTF-8 单文件 Rhino 编译, 有界诊断及强制 Source Map 输出
* `新增` 本地单文件 .ts 及 Rhino .tsx 脚本会先经独立编译插件再由 Rhino 执行, 保留 UTF-8 源文件身份与 Source Map 输出, 类型错误在执行前拒绝, 且绝不回退到 Node.js 插件或直接执行原始 TypeScript; 不受支持的声明文件及编译器不可用场景会返回明确的本地化错误
* `新增` 带 "ui" 模式参数的本地单文件 .ts/.tsx 脚本支持经编译后在 Rhino Activity 中执行字符串 XML 及原生 TSX 布局, 包含受控内置标签, Fragment, 标量属性, 视图查找, 点击回调, 文本更新及显式关闭 UI
* `新增` TypeScript 编译诊断以宿主 ScriptCompilationException 记录提供源文件, 1 基行列, 严重级别与 TS 错误码; Console 与编辑器底部日志面板显示可点击的干净位置, 可跳转到对应源码位置
* `新增` Rhino TypeScript 运行异常会消费编译器 Source Map v3, 将生成 JavaScript 堆栈帧映射回原 .ts 文件与行号, 不虚构 Rhino 未提供的列号, Console 与编辑器日志面板中的映射位置可直接点击跳转
* `新增` 本地单文件 .mts/.cts 脚本会先经独立 TypeScript 编译插件的 Node profile 编译为 .mjs/.cjs, 再交由 Node.js Runtime 插件执行, 保留 UTF-8 文件名并绕过旧的 TypeScript 正则剥离; 单文件 Node .ts 在项目 package 元数据支持完成前会被明确拒绝
* `新增` Node TypeScript 运行异常会消费编译器 Source Map v3, 将 V8 堆栈帧映射回原 .mts/.cts 文件的精确行列, Console 与编辑器日志面板中的映射位置可直接点击跳转
* `新增` TypeScript 项目现在会将有界项目快照作为单个 Program 交给 Rhino 或 Node profile 编译, 保留相对 import, CommonJS 循环依赖, JSON 运行时文件, package `type` 上下文, 导入模块诊断及逐模块 Source Map 栈映射; package 元数据保持严格校验并拒绝 lifecycle scripts
* `新增` TypeScript 编译结果可按源码图, 编译器与声明指纹, profile 与 policy 修订及 package 元数据组成的语义键跨执行缓存; 未修改脚本会跳过编译器 prewarm 与 Worker 编译, 已认证输出归档在复用前仍会重新校验, 损坏或超额条目自动回退到正常编译
* `新增` TypeScript 项目从编译到执行共用一份精确只读 `node_modules` 快照; Rhino 装载 CommonJS/JSON 和 dual-package 的 `require` 分支并提供稳定的 ESM-only 错误, Node 接收不可变私有 workspace overlay 并执行 ESM/CJS/dual package, 且不会回写依赖变更
* `新增` 定时任务, 广播任务, 快捷方式及 Tasker/本地 Run Intent 现在与前台启动共用同一条 TypeScript preparation 管线; Tasker 前置 JavaScript 会保持在已编译的 Rhino/Node 输出之前执行, Source Map 行偏移也会计入该前缀, 不再绕过编译并直接执行原始 TypeScript
* `新增` 项目文件夹支持直接创建定时任务与广播任务, 触发时按 project.json 重新解析 Rhino/Node.js/Python 入口, 并恢复项目工作目录及 useFeatures 配置
* `新增` Python Runtime 插件, 内置 Chaquopy 17.0.0 与 CPython 3.13.9, 支持识别文件扩展名 (.py) 运行 Python 脚本
* `新增` Python Runtime 协议 1.4 为每次执行协商显式严格 JSON 结果与可选输出 artifact, 对数量, 规范化路径, 单个/合计大小, 精确 PFD 引用及 SHA-256 设限; Host 仅消费类型化结果且绝不从 stdout 推断
* `新增` Python Runtime 协议 1.5 新增执行级同步 Host 能力 broker, 校验固定插件 UID, 采用严格有界 JSON, 调用配额, 防重放与生命周期约束, 执行终态后拒绝调用; 完整首批低风险能力向 Python 脚本开放 toast, 剪贴板读写, 应用启动/打开 URL, 动态电量/屏幕/亮度/音量信息, 分级宿主控制台输出及权限感知通知
* `新增` Python Runtime 协议 1.5 Host broker 支持在当前项目根目录或单文件脚本目录内进行有界 UTF-8 文本读写, 路径存在/类型检查及排序的直接目录枚举; 规范化相对路径与稳定上限阻止访问越过执行根目录
* `新增` Python Runtime 协议 1.5 Host broker 仅向经用户授权的前台启动开放有界 alert/confirm/prompt/select 对话框; 后台启动稳定返回 INTERACTIVE_NOT_ALLOWED, 取消或执行终态清理会关闭活动对话框 UI
* `新增` Python Runtime 协议 1.5 Host broker 开放不含绝对路径的当前引擎信息, 确定性停止自身及最多 16 次限定范围的非 Python Host 子脚本异步启动; provider 保持单会话期间嵌套 Python 稳定返回 NESTED_PYTHON_NOT_ALLOWED
* `新增` Python Runtime 协议 1.5 Host broker 开放有界的无障碍点击, 长按, 定时按压, 滑动, 返回及主页操作; 坐标与持续时间经过严格校验, 无障碍服务不可用时稳定返回 ACCESSIBILITY_UNAVAILABLE
* `新增` Python Runtime 协议 1.5 Host broker 开放有界且绑定单次执行的无障碍树快照与 AND 组合选择器查询, 通过不透明节点引用执行显式点击与文本设置; 快照, 扫描, 字符串, 保留节点及响应均有固定上限, 无障碍不可用, 扫描不完整与节点过期时返回稳定的类型化错误
* `新增` Python Runtime 协议 1.5 Host broker 通过 Android 11+ 无障碍服务开放执行级屏幕截图, 以 PNG/JPEG 编码并仅保留一张图像, 通过 32 KiB 顺序分块和 SHA-256 元数据传输, 编码大小上限为 4 MiB, 支持显式释放且在终态覆盖字节; 不打开权限界面, 无障碍不可用, 截图失败, 图像句柄过期与越限均返回稳定的类型化错误
* `新增` Python Runtime 协议 1.5 Host broker 可在最新的 Android 11+ 无障碍截图中按可选区域和逐通道阈值搜索有界 RGB 颜色, 按行优先返回首个坐标或确定性的未命中结果, 不传输或保留图像字节
* `新增` Python Runtime 协议 1.5 Host broker 接收一份执行级 PNG/JPEG 模板, 通过 24 KiB 顺序分块、1 MiB 编码上限与 SHA-256 校验上传, 解码上限为单边 2048 像素及总计 100 万像素, 并以不透明模板像素和 RGB 阈值在最新的 Android 11+ 无障碍截图中按行优先确定性匹配; 生命周期、区域及比较预算均 fail-closed, 且不依赖 OpenCV 运行时, Android 强制的 333 ms 截图请求间隔仅在收到精确的间隔过短错误时于 350 ms 后在既有截止时间内有界重试
* `新增` Python Runtime 显式严格 JSON 结果在脚本终态后以 `[result]` 前缀写入宿主控制台, 已校验输出 artifact 原子发布到项目或脚本目录下的 `.python-artifacts` 执行专属目录并打印绝对路径; 该保留目录不进入后续项目快照
* `新增` Lua Runtime 插件, 内置 PUC Lua 5.4.8, 支持识别文件扩展名 (.lua) 运行 Lua 脚本
* `新增` 独立 Java/Kotlin JVM Source Runtime 插件后端, 支持在隔离进程中编译和运行单文件 .java/.kt 脚本, 提供 package/import, 实时 console 输出, 可取消 sleep, 宿主 toast 与显式能力协商
* `新增` JVM Source Runtime 支持范围为单文件 Java 8 (Java provider 最低 API 24) 与 Kotlin 2.3 (Kotlin provider 最低 API 26); 本次发布仅在 API 31/arm64-v8a 真机完成 smoke, 不声明未验证 API 级别
* `新增` AutoJs6 官方插件可通过签名权限保护的版本化只读设置契约, 读取宿主主题色, 暗色模式策略及语言的外观快照
* `新增` 全局 JavaScript (Rhino) ai 的所有本地与在线请求统一通过 AI Provider 插件目标执行; 开发者选项会打开可信 3-Stone AI 设置入口, 或提供精确的安装/启用/更新指引, 宿主不再保存 AI 档案或凭据, 也不再包含提供商 HTTP 传输
* `新增` 全局 JavaScript (Rhino) `ai.catalog` API 公开统一的本地/在线插件目标目录; 省略 `plugin` 与 `target` 时选择官方插件声明的默认目标, `target` 精确选择一个目标, `plugin` 精确选择第三方组件; 响应包含 reasoning, 完整 usage 与耗时, finish reason, profile 及实际目标元数据, 稳定错误绝不静默改路由
* `新增` 全局 JavaScript (Rhino) `ai.session` API 可创建持久 AI provider 插件 Conversation, 为会话固定目标/提供商/模型/生成参数, 后续 `ask`/`chat`/`stream` 轮次只发送新的用户提示词; 正常完成后保留上下文, 取消/超时/协议失败或显式关闭时释放整个会话
* `新增` `ai.ask`/`ai.chat`/`ai.stream` 与持久 `ai.session` 的 AI provider 路由支持 `structuredJson` 和 JSON 对象 `responseSchema`; schema 启用 LiteRT-LM 原生 JSON Schema 约束解码, 会话为每轮固定同一 schema, 完成 JSON 文本执行严格验证
* `新增` AI Provider V2 本地目标将显式 `cpu`, `gpu` 和 `npu` backend profile 作为可选目标控件; 可用性按设备报告, 不可用 profile 失败且不回退 CPU, 会话固定一个 backend, GPU 要求系统 OpenCL 可加载, 官方插件因未打包 EAP runtime 而报告 NPU 不可用
* `新增` 全局 JavaScript (Rhino) yolo 模块提供默认关闭的精确组件 YOLO 提供方路由, 支持版本化 Binder/PFD 传输, 基于清单的 NCNN 模型加载, 目标检测, 显式取消及资源清理, 且不静默回退提供方
* `新增` tts 模块, 支持文本转换为语音 (参阅 项目文档 > [文本转语音](https://docs.autojs6.com/#/tts))
* `新增` settings 模块 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` work_manager 模块 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` power_manager 模块 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` device.getMarketName 方法, 用于获取设备的 "市场名称" _[`issue #513`](http://issues.autojs6.com/513)_
* `新增` ui.useAndroidLayout 方法, 用于 Android 原生布局控件支持
* `新增` runtime.loadAar 方法, 用于动态加载 AAR 文件
* `新增` 显式 runtime.loadJarWithR8 入口, 支持默认关闭的精确组件选择, 同签名身份固定, 有界五产物 R8 传输, 隔离缓存接纳, 以及无 D8/dx 回退的失败关闭行为
* `新增` 文件管理器支持多选文件及批量复制/移动/删除操作
* `新增` 文件管理器 Explorer Action 协议 v4 支持有序文件/文件夹/混合目标及同父级多选动作, 目录菜单与五项上图标下文字操作栏可显示插件压缩动作, 并通过请求级宿主文件会话受控读取目标及原子发布同级输出
* `新增` 文件管理器 Explorer Action 协议 v5 支持可信单文件只读插件通过固定 UID 的 Binder 会话, 在原生 Explorer 列表/路径栏/主题及返回栈中分页呈现压缩档案目录
* `新增` 文件管理器 Explorer Action 协议 v6 支持通过宿主现有预览器直接预览压缩档案内可读取的文档, 图片, 音频和视频条目
* `新增` 文件管理器 Explorer Action 协议 v7 至 v10 支持待提交文件强制读回校验, 原目标原子替换, 带客户端死亡和重启恢复的宿主持有目录输出事务, 以及带有界进度和取消的原生当前目录/选择项解压, 使压缩档案插件无需存储权限即可修改选中 ZIP 并安全发布已校验输出
* `新增` 文件管理器 Explorer Action 协议 v11 新增有界的宿主原生密码恢复, 可在首次打开压缩档案或解压时请求及重试密码; 重试保持档案路径与选择, 每次请求后清理瞬态密码缓冲
* `新增` 文件管理器 Explorer Action 协议 v12 提供有界且绑定档案会话的同级分卷源; 压缩档案插件仅能通过不透明 ID 打开宿主批准的 ZIP 和 RAR 卷只读描述符, 不会获得目录或文件系统路径
* `新增` 文件管理器 Explorer Action 协议 v13 支持压缩档案插件在同一只读会话内重建文件名解码; 原生路径栏可选择 ZIP 编码, 并保留最深可用内部路径与仍存在的已选条目
* `新增` 文件管理器 Explorer Action 协议 v14 新增有界复合文件名后缀匹配; 压缩档案插件可精确登记 `.zip.001` 与 `.7z.001` 首卷并复用绑定会话的 v12 同级卷源, 不会匹配任意 `.001` 文件或获得目录路径
* `新增` 文件管理器 Explorer Action 协议 v15 新增可恢复多文件输出批次; 插件可在全部待提交文件验证后统一发布, 宿主在发布前持久记录父目录及文件身份, 失败或重启时只回滚身份仍匹配的成员
* `新增` 文件管理器 Explorer Action 协议 v16 新增由宿主持有的源项目回收站交接; 仅接受完整原始选择与全部已提交输出的精确证明, 宿主先持久化恢复副本再移除源项目, 不授予插件任意路径或删除权限
* `新增` 文件管理器 Explorer Action 协议 v17 仅在普通主动作未匹配时提供显式只读的“作为压缩档案打开...”入口; 既有档案会话执行一次结构探测并可在原生路径栏标注实际格式, 建立菜单时不后台扫描文件且不新增文件权限
* `新增` 文件管理器 Explorer Action 协议 v18 在受控替换前把目标上一版本保存在宿主私有持久存储中, 只公开不透明的有界历史元数据; 仅当当前文件仍精确匹配已提交替换结果时允许恢复一次, 不覆盖外部改写并保留中断恢复证据
* `新增` 文件管理器支持安全预览 Markdown 与 HTML 文件, 提供 GFM 渲染/多套主题/自定义 CSS 及受控的同目录资源加载
* `新增` Markdown 与 HTML 预览支持全屏模式及默认关闭的 "以全屏模式启动" 设置
* `新增` 文件管理器支持通过最左侧图标按钮预览 Markdown/HTML 及查看图片/播放音频/视频; 应用可注册为系统媒体查看器, 音频支持后台播放及通知栏联动控制
* `新增` 文件管理器置顶路径栏, 支持显示当前路径及高亮当前目录, 点击父级目录直接跳转, 长路径自动定位到末端及横向滑动浏览, 父级/当前目录配色与类别栏/文件名保持一致, 多语种文字及分隔箭头按字形墨迹重心光学居中
* `新增` 文件管理器支持查看图片文件信息及 EXIF 详情
* `新增` 图片编辑及格式转换由独立的 Image Tools 插件提供, 插件不可用时仅隐藏对应动作, 不影响图片查看/图片信息/EXIF 详情
* `新增` 图片查看器支持自动遍历同目录图片, 提供紧跟双指焦点的同步缩放, 左右滑动及工具栏前后按钮切换, 显示序号/分辨率/大小/格式/修改时间/EXIF 的紧凑三行信息栏, 全屏模式/以全屏模式启动/带历史记录的背景色设置, ActionBar/系统栏/图标可随背景色自适应配色及明暗对比
* `新增` 文件管理器支持查看 APK/APKS/XAPK/APKM/APKZ/AAB 文件的安装包详情及清单
* `新增` 安装器支持 APK 及 APKS/XAPK/APKM/APKZ 容器中的兼容拆分 APK 组合, 包含设备拆分选择及签名证书一致性校验
* `新增` 代码编辑器支持通过外部插件加载 Ace 编辑器, 包含签名/信任/Editor API 合约/宿主版本/二进制 ABI 兼容性校验, 插件不可用时自动回退至原生编辑器
* `新增` 代码编辑器支持 Ace 外部插件运行时热插拔, 插件安装或启用状态变化时自动切换编辑器引擎
* `新增` Ace 编辑器的内置本地 TypeScript 智能补全成为正式功能并默认开启, 支持 AutoJs6 API 补全/签名帮助/悬浮提示/诊断, 可在设置中关闭
* `新增` Ace 编辑器设置支持按需加载 android/libraries/resources/main-app AutoJs6 TypeScript 声明组, 自动补齐组依赖, 所有可选声明组默认关闭以降低语言服务启动耗时及内存占用
* `新增` Ace 编辑器支持快捷键面板/操作历史/操作收藏/移动端文本选择/双指缩放等功能
* `新增` 代码编辑器支持按指定编码重新加载或转换文件, 可配置 BOM 并提供编码风险提示及撤销/重做
* `新增` 代码编辑器工具栏下方信息栏显示文件名/实时光标位置 (行/列)/文件编码, 点击对应项可查看文件详情/跳转到行/更改编码
* `新增` 代码编辑器系统状态栏/ActionBar/按钮图标及文字/更多及嵌套菜单/编辑器内对话框/原生编辑器光标/手柄及选择控件动态跟随当前编辑器主题, 状态栏图标自动保持明暗对比, 使顶部至代码区域视觉风格统一
* `新增` 代码编辑器支持沉浸式全屏模式, 设置会持久保存且可从菜单或设置页面切换; 从屏幕边缘滑动可临时显示系统栏, 返回键会优先退出全屏而非离开编辑器
* `新增` 代码编辑器支持底部日志面板显示 (by [M17764017422](https://github.com/M17764017422)) _[`pr #502`](http://pr.autojs6.com/502)_
* `新增` 代码编辑器底部日志面板支持堆栈帧代码跳转 (by [M17764017422](https://github.com/M17764017422)) _[`pr #502`](http://pr.autojs6.com/502)_
* `新增` 代码编辑器底部日志面板提供 "运行/停止" 按钮, 可便捷启停脚本
* `新增` 代码编辑器支持专属设置页面及编辑器内设置对话框, 提供主题预览/亮暗模式独立配置/字体/行号槽/自动换行等显示选项
* `新增` 定时任务支持按月/按年/倒计时任务及重复结束条件 _[`issue #294`](http://issues.autojs6.com/294)_ _[`issue #537`](http://issues.autojs6.com/537)_
* `新增` 插件中心支持在线安装插件及更新插件; 全局设置页面及使用说明页面; SDK 插件自动挂载功能; 多变体优先级排序功能; 启动页跳转/隐藏插件; 可用 ABI 交集检测及提示
* `新增` 设置页面提供 "运行后自动显示底部日志面板", "代码高亮阈值" 等代码编辑器设置选项
* `新增` 打包应用配置支持显示或隐藏 "日志" 及 "设置" 启动器快捷方式 _[`issue #550`](http://issues.autojs6.com/550)_
* `新增` 全局 JavaScript (Rhino) R8 集成新增 3 个校验产物导出的 `runtime.loadJarWithR8` 重载及协议 1.1 `runtime.retraceR8Stack`; mapping/seeds/usage/retrace metadata 原子发布, retrace 绑定 mapping 溯源, 提供者缺失或无效时禁止 D8/dx 或本地回退
* `修复` 文件管理器中部分已识别为视频的格式仍会打开系统应用候选菜单的问题; 现在所有已识别的视频类型均优先调用官方视频播放器插件, 插件未安装, 已禁用, 未授权, 不兼容, 不可用或启动失败时显示恢复引导及明确的 "用其他应用打开" 选项
* `修复` 打包应用的 Rhino CommonJS require 加载器无法解密依赖 JavaScript 模块, 导致 build-time 编译的多文件 TypeScript 项目以 "Cannot read file" 失败的问题
* `修复` Node.js UI 句柄在 Android Activity 销毁后可能仍保持活动, 允许迟到更新并持续占用提供器资源直至执行清理
* `修复` Node.js 插件执行因模块探测及工作区归档预算过小而可能拒绝大型纯文本依赖图及真实 node_modules 工作区
* `修复` Node.js 空脚本及简单脚本在包含大量文件的工作目录中因不必要的模块扫描及完整读取候选文件而启动缓慢的问题
* `修复` 短生命周期脚本启动界面隐藏时错误回收 Node.js 常驻进程的问题
* `修复` 打包应用的 Node.js 交互式长运行任务正常停止后, 预期的常驻进程 Binder 断开回调可能将 stopped 状态覆盖为 failed 的问题
* `修复` Node.js console.assert 在断言失败时错误抛出 AssertionError 异常而非仅向 stderr 写入失败消息的问题
* `修复` Node.js 脚本执行结果因运行时进程稳定等待而延迟返回的问题
* `修复` Node.js 活动执行取消时可能在异常进程隔离前过早移除执行注册, 导致完成等待器阻塞及恢复延迟的问题
* `修复` Node.js 外部运行时 Binder 响应丢失后自动回退可能导致同一脚本被重复执行的问题
* `修复` Node.js TypeScript 项目经工作区归档执行后可能将仅供运行时使用的编译产物同步回源码目录, 导致后续执行与原项目文件冲突的问题
* `修复` Android 私有目录 /data/user/0 与 /data/data 别名未规范化写回插件请求, 导致合法 Node.js workingDirectory 被误判为越过 sandboxRoot 的问题
* `修复` nodePlugin 在宿主不含 Node 运行时时错误排除所有 ABI 的 JNI/AAR 库, 导致 Rhino Shell 初始化因缺少 libjackpal-termexec2.so 而崩溃的问题
* `修复` Node.js R6 证据采集及验证因 apksigner 证书摘要输出格式变更而拒绝 Android Build Tools 37 报告的 APK 签名身份的问题
* `修复` IntelliJ IDEA 构建 Android 模块时因 compileJava 任务匹配歧义而失败的问题
* `修复` IntelliJ IDEA 全项目构建在 Clean 后因 Python Runtime API 分发 AAR 尚未生成而无法解析 Android 依赖, 且 conformance 夹具未同步追加的 AIDL 方法导致编译失败的问题
* `修复` Android 报告当前已分配原生堆字节数为零时, 健康的 Node.js R6 持久运行时 soak 证据被错误拒绝的问题
* `修复` Node.js R6 打包 APK 证据因采集器未准备当前 Inrt 模板或传入目标 ABI 而被跳过或错误绑定 arm64 的问题
* `修复` 跨 ABI 打包应用时错误将已安装宿主进程的原生库复制到其他 ABI 目录, 导致 32 位打包应用加载 64 位终端库并崩溃的问题
* `修复` TypeScript UI 脚本在引擎创建前编译失败时, 全局执行监听器访问不存在的引擎可能导致宿主在报告诊断的同时崩溃的问题
* `修复` 堆栈帧与编译诊断链接将 1 基源码列号直接当作 0 基编辑器偏移, 导致光标多前进一个字符的问题
* `修复` DEX 编译器实验性宿主可能接受与请求 minApi 不兼容的 DEX 版本, 错误拒绝合法的 65,536 field/method ID 边界, 并在排队的 pipe drain 延迟启动时重新创建已回滚的临时产物
* `修复` runtime.loadJar 无法正常动态加载由较高 Java 版本编译的 class 文件的问题
* `修复` app.isInstalled/kill 等方法无法正常使用包名参数的问题 _[`issue #495`](http://issues.autojs6.com/495)_
* `修复` 无 Root 权限设备调用 shell 相关方法时可能出现假死的问题
* `修复` dialogs.multiChoice 方法返回的多选结果被错误转换为字符串的问题 _[`issue #518`](http://issues.autojs6.com/518)_
* `修复` 脚本对话框在线程 Looper 退出时关闭, 因无法调度 timer 回调而导致应用崩溃的问题
* `修复` selector 模块反射调用重载方法时可能选择错误方法签名的问题
* `修复` Rhino 引擎更新后作用域 API 兼容性回归导致部分运行时辅助功能异常的问题
* `修复` Rhino 模块桥接对 null/undefined, Callable, 构造器, 原型, realm 及 Kotlin 返回值的处理不正确, 可能导致跨 realm 对象, 回调丢失或运行时异常的问题
* `修复` Proxy 包装函数无法作为 dialogs, events, timers, UI, selector 及 automator 等旧模块的回调, 且 TimerThread 回调参数可能被嵌套传递的问题
* `修复` Callable 与 BaseFunction 兼容重载导致普通 JavaScript 函数调用 EventEmitter.on 等 Java 方法时出现参数类型歧义的问题
* `修复` EventEmitter.once 监听器在事件递归派发或监听器抛出异常时可能被重复调用的问题
* `修复` automator.home 等全局操作首次调用时可能因辅助 Looper 初始化被中断而抛出空指针异常, 且手势纵坐标可能错误使用横向比例缩放的问题
* `修复` 早期 JavaScript 模块迁移中的参数分派, 返回值, 数值边界, 图像坐标及临时资源生命周期与原实现不一致的问题
* `修复` Android 14+ 申请屏幕捕获权限时可能因媒体投影前台服务启动异常导致应用崩溃的问题 _[`issue #516`](http://issues.autojs6.com/516)_
* `修复` 多线程或跨文件重复调用 OCR 时可能反复申请屏幕捕获权限的问题 _[`issue #551`](http://issues.autojs6.com/551)_
* `修复` Paddle OCR 插件服务长时间运行后可能断开且无法自动恢复的问题 _[`issue #515`](http://issues.autojs6.com/515)_ _[`issue #525`](http://issues.autojs6.com/525)_
* `修复` Paddle OCR 插件未处于运行状态时无法被自动唤醒, 导致功能调用或项目打包失败的问题 _[`issue #517`](http://issues.autojs6.com/517)_
* `修复` `images.resize(ImageWrapper, width, height)` 错误用法会将高度作为 OpenCV 插值参数传入, 且不兼容的原生异常展开可能导致应用崩溃而非报告脚本异常的问题
* `修复` MediaInfo 插件媒体库不可用或 Binder 调用异常时, 文件管理器媒体详情可能仅显示 "..." 或导致应用崩溃, 且无法降级显示 Android 平台元数据的问题
* `修复` 文件管理器视频详情对话框错误显示内嵌音频格式及元数据字段的问题
* `修复` 部分 WebView 版本及实体设备上的 Markdown 与 HTML 预览主文档被请求拦截器误拒绝的问题
* `修复` 图片查看器双指缩放未能紧随手指移动, 缩小时难以恢复原始大小, 且首次进入时 ActionBar 可能短暂或持续使用主题色的问题
* `修复` 图片查看器退出时 Glide 仍尝试使用已销毁的 Activity 发起图片加载, 导致应用崩溃的问题
* `修复` 无障碍服务在连续执行 back 等操作时可能意外中断的问题 _[`issue #527`](http://issues.autojs6.com/527)_
* `修复` 脚本反复运行后内存占用可能持续增长的问题 (试修) _[`issue #548`](http://issues.autojs6.com/548)_
* `修复` 通过外部 URI 或后台运行脚本时可能无法保留原始路径, 导致相对路径解析异常的问题 _[`issue #545`](http://issues.autojs6.com/545)_
* `修复` Tasker 无法正常调用并运行脚本的问题 _[`issue #501`](http://issues.autojs6.com/501)_
* `修复` 主页文档标签页操作时, ViewPager 可能因异常多指触摸序列产生无效指针索引并导致应用崩溃的问题
* `修复` Ace 编辑器在输入法交互/文本选择/代码折叠/双指缩放/堆栈跳转等场景下的多项交互问题
* `修复` 编辑器设置主标题, fx 函数面板, 固定在左侧的文档抽屉及 Ace 文本选择工具栏可能沿用 AutoJs6 应用配色而非当前编辑器主题, 且 Ace 行操作可能重复出现的问题
* `修复` 全屏编辑模式下软键盘可能覆盖多功能按键栏, 且全屏提示 Snackbar 下方导航栏区域可能透明的问题
* `修复` 代码编辑器文档侧栏可能仅显示主题背景而无内容, 且编辑器设置缺少基于编辑器背景生成的稳定分层角色色, 导致列表滚动及条目复用后标题, 副标题, 分类及控件颜色可能丢失的问题
* `修复` 非全屏编辑模式下软键盘可能覆盖多功能按键栏的问题
* `修复` 代码编辑器信息栏可能省略文件扩展名或在打开文档时短暂显示文件末尾临时光标坐标, 且应用与编辑器主题明暗不一致时多功能按键栏与软键盘之间可能闪现亮色间隙
* `修复` 大型文件使用不兼容编码重新加载后产生超长文本行时, Ace 编辑器可能长时间无响应并回退至原生编辑器的问题
* `修复` Ace 外部插件在编辑器或设置界面使用期间被禁用或卸载时可能丢失未保存编辑状态或导致界面异常的问题
* `修复` 插件中心图标缓存及下拉刷新状态在部分场景下可能异常的问题
* `修复` 单个本地插件进程异常时可能导致插件中心列表完全空白的问题
* `修复` 主页插件标签页中已安装插件的图标无法随夜间模式动态切换的问题
* `修复` 插件项目发布多个变体时插件中心可能安装错误变体 APK 的问题
* `修复` APK Builder Template 插件可能重复显示或错误提示更新的问题
* `修复` 离线项目文档插件更新期间完整性校验缓存可能复用旧内容的问题
* `修复` 文件管理器 APK 详情中缺少 Manifest 按钮及完整多行内容的问题
* `修复` 文件管理器解析 APK 时错误忽略 ZIP 条目名的大小写, 导致部分包含仅大小写不同条目的有效安装包被误判为含有重复条目而无法查看或安装的问题
* `修复` 外部 URI 缓存导入因信任内容提供方文件名而可能引发路径穿越或同名文件覆盖的问题
* `修复` 文件管理器在回收站删除文件后列表滚动位置被重置的问题
* `修复` 主页抽屉开关项的开关视图有效点击区域过小的问题
* `修复` 浮动按钮菜单显示的最近应用包名及 Activity 信息异常的问题 _[`issue #529`](http://issues.autojs6.com/529)_
* `修复` 浮动按钮 "运行脚本" 对话框最小化后可能无法还原的问题 (试修) _[`issue #500`](http://issues.autojs6.com/500)_
* `修复` 启动页背景未延伸到刘海屏的短边切口区域的问题
* `修复` 文件管理器可能不会刷新 Explorer Action 新提交的同级输出, 或把已提交的输出目录显示为文件的问题
* `修复` Android 7 离开原生档案页时, 会话预览缓存清理路径调用了较新的 File NIO API, 可能导致应用崩溃的问题
* `修复` 从 Explorer Action 播放器返回时, 文件管理器即使保留了目录与滚动位置仍可能连续触发两次全列表重绑定, 导致页面剧烈闪动的问题
* `修复` 从较远的主页标签切换到文件页时会逐页平滑滚动, 期间拦截第一次列表手势并表现为约 1 秒无响应的问题
* `修复` 从视频播放器等横屏插件页面返回时因配置变更重建整个主界面, 导致文件管理器剧烈闪动甚至连续闪动两次的问题
* `修复` 每次回到主界面都会重新发现并绑定插件服务, 可能拉起插件进程并导致文件列表约半秒无响应的问题
* `修复` ZIP/JAR 等由插件处理的文件, Explorer Action 拼图图标填满整个操作单元而显得过大的问题
* `修复` 文件管理器无法可靠区分同为 `.ts/.mts` 后缀的 TypeScript 源码/声明文件与 MPEG-2 TS 视频, 且 `.d.ts/.d.mts/.d.cts` 未被始终视为声明文件的问题
* `修复` 插件支持的 `.py/.ts/.java/.kt` 源文件未统一显示可执行源码图标与运行入口的问题
* `优化` 移除宿主侧 Node.js Gradle 开关及重复的样例, 类型声明, 项目向导与校验脚本, 运行时默认值和开发资产现统一由 Node.js Runtime 插件维护
* `优化` DEX 编译器诊断现在在既有预算内保留经脱敏的 INFO/WARNING/ERROR 详情及可选逻辑来源, 归档条目与位置元数据; 开发者选项会显示仅存于进程内的最近一次 JAR 加载路径, 并提供 BUSY, 超时及回退提示
* `优化` TypeScript 编译现在为每次请求记录隐私安全的性能观察, 涵盖准备, 缓存查找, 编译器绑定/预热, 远程编译, 输出处理, 总耗时, 归档大小, 源文件数及宿主堆内存变化, 并将编译器耗时细分为 ingress 前, ingress, 阶段间隔, parse, semantic, emit 及 emit 后区间; 格式错误或乱序的 progress 回调会被忽略, 这些观察不设置性能门禁
* `优化` 插件化离线项目文档, 插件名称 Offline Documentation
* `优化` 插件化打包应用功能, 插件名称 APK Builder Template, 可按需安装及更新打包应用所需模板
* `优化` KeyCode 方法提升参数兼容性 _[`issue #492`](http://issues.autojs6.com/492)_
* `优化` KeyCode 方法支持使用 Shizuku 权限发送键码指令
* `优化` 延迟初始化非关键组件, 提升应用启动性能
* `优化` 缓存 Rhino 内置资源编译结果及运行时增强函数元数据, 提升脚本初始化性能
* `优化` 提升 Rhino 控制台打印大型数组时的格式化性能
* `优化` 重构 Kotlin 模块增强架构, 以声明式挂载规范支持多个别名及各名称独立的 `$` 前缀控制, 显式绑定 Rhino realm, 分离 tasks 与 work_manager 语义, 并补充模块兼容性及语义回归测试
* `优化` 复用 Node.js 常驻进程及进程级运行时, 提升脚本连续运行时的启动性能
* `优化` Node.js 启动支持分阶段计时及可信的冷启动/运行时热启动性能报告
* `优化` 缓存 Node.js 内置原生库解析结果, 避免重复发现插件
* `优化` 缓存已授权的 Node.js 外部运行时候选项及常驻 Binder 会话, 支持状态失效与原子重连, 降低插件发现及绑定开销
* `优化` 移除 Node.js 默认执行路径中的项目全目录加密模块扫描, 提升复杂脚本及大型项目的启动性能
* `优化` Node.js 引擎切换加入真实 Rhino/Node 多轮执行回归测试, 覆盖外部插件常驻会话复用/恢复及完整回收
* `优化` Node.js 外部插件常驻运行时加入 soak 测试及生产证据门禁, 覆盖设备/ABI 矩阵/运行时生命周期/资源指标及 8 小时/24 小时稳定性
* `优化` Node.js 原生校验支持解析并验证外部运行时插件的源码及构建根目录
* `优化` Node.js 最终集成及发布门禁在所需报告/当前源码证据/生产验收产物缺失或过期时采用 fail-closed 策略
* `优化` Node.js 原始及动态输入默认关闭旧 TypeScript 正则 stripping, 仅在显式启用临时请求级兼容开关时保留, 常规执行统一要求编译器产物
* `优化` Node.js TypeScript 计算式 import() 在闭合编译快照内将 .ts/.mts/.cts 确定性映射到预编译的 .js/.mjs/.cjs 模块, 未命中/歧义/越界目标返回稳定错误且不回退读取运行时新增的 TypeScript 源码
* `优化` 独立 Python 脚本发生精确 ModuleNotFoundError 时在控制台追加安全提示, 引导 sibling/local 模块 import 使用包含 `project.json` 与 `type: python` 的显式 Python 项目, 且不暴露私有路径
* `优化` Python Runtime 输出 chunk 到达时立即写入宿主控制台, 使用按 stdout/stderr 独立的增量 UTF-8 解码保留跨 chunk 码点, 并将端到端输出上限提高到 16 MiB
* `优化` Python 项目的 project.json 支持 timeout 正整数毫秒配置, 默认 5 分钟、最大 30 分钟, 并继续受运行时插件上限约束
* `优化` Python 项目的 project.json 支持通过 "entryMode": "module" 与规范化点分 main 名称声明模块入口, 以标准 Python 模块语义运行并支持包内相对导入
* `优化` 将 Python 项目 workspace 上限提高至压缩 64 MiB、8192 个文件条目及解压 128 MiB, 并在分发前按快照实际三维需求匹配运行时 Provider 能力, 支持随项目携带纯 Python 依赖
* `优化` Python 项目的 project.json 支持以内联文本或显式项目内相对文件声明有限 stdin 快照, 使 sys.stdin.read() 无需交互提示即可读取预置输入
* `优化` 插件化 ML Kit OCR/ML Kit Barcode/Rapid OCR/OpenCV/Image Quantization/OpenCC/Pinyin/Pinyin4j/MediaInfo 相关功能
* `优化` 文件管理器将媒体详情查询整合为单次 MediaInfo 快照, 减少 Binder 调用及等待时间
* `优化` 文件外部查看与媒体播放降级仅授予 URI 读取权限
* `优化` 统一文件管理器默认点击, 主要按钮, 媒体详情播放和 overflow 菜单插件动作, 通过显式主文件页界面策略提供确定性的单次降级
* `优化` Explorer Action v7 允许压缩档案插件在最终发布前以只读方式重开隐藏的待提交输出并完成校验, 同时保留 v4 客户端的直接提交兼容语义
* `优化` Explorer Action v12 在每次打开同级卷前校验调用方 UID, 普通文件身份, 大小, 修改时间, 符号链接状态及会话生命周期, 并限制名称长度, 目录数量和打开次数
* `优化` 原生档案选择模式仅显示退出和解压, 集成有界进度与取消; 文件系统五项操作栏的文字在窄屏上居中显示于图标下方
* `优化` 保持文件管理器既有的紧凑路径栏, 列表和五项选择操作栏尺寸, 同时保留无障碍标签, 按钮角色, 选中状态及 TalkBack 播报; 插件启动前仍会对 Explorer Action 请求大小进行封闭校验
* `优化` 将 AI 文本与 DEX 编译器一致性 provider 从生产 `plugins` 目录迁入专用 `test-apps` 构建组, 保留恶意 Binder 回归覆盖并明确独立生产插件的发布边界
* `优化` DEX 编译器实验性宿主路径保持默认关闭并要求显式组件, 实施精确组件 UID 与完整签名者集合锁定, 单次分派降级, 语义缓存来源追踪, 有界回调入口, 隔离 Binder 超时, 结构化 DEX ZIP 校验, reliable pipe 有界 drain 与 EOF 对端错误检查, 由源/目标双摘要保护的可回滚单次发布, 异步终止信号与发布提交点原子仲裁, 且取消/超时/Binder death 在提交前胜出时强制回滚
* `优化` DEX 编译器 R1.1 保持实验默认关闭, 允许 runtime.loadJar 的 raw JAR 优先使用一个显式选择且与宿主同签名的精确组件 provider, 失败时仅回退一次到内置 D8/dx, 取消时不回退, AAR 加载继续使用内置路径
* `优化` DEX 编译器 R1 已闭环, 同时保持默认关闭与精确组件显式 opt-in: R1.1 生产加载链路 7/7、R1.2 自动化 4/4、R1.3 真实 provider 矩阵 7/7、退出条件 3/3. API 34 production routing 8/8, 宿主 DEX 16 suites/149 tests、wire 4/24、fake-provider 5/25 及插件 48 tests 全通过, lint 0 error. Canonical campaign f3c2b1af-be93-41e7-b541-f167f90e5cc1 在 API 24/25 使用 D8 CLI、API 26/28/34/36 在 x86_64 使用 D8Command、API 31 使用 QV arm64 多用户真机, 七个真实 provider 单元全部 PASS; 较早失败或主动终止的 campaign 继续原样保留. last-waiter 协作取消与更广泛恢复仍是未勾选的 R2 工作
* `优化` DEX 编译器实验性一致性测试扩展至真实 Android Binder 传输与产物存储, 覆盖精确签名组件, 恶意回调/输出/超时场景, 目标 API 感知的 DEX 校验及 DexClassLoader 执行
* `优化` 默认关闭的纯 AI 文本宿主策略支持确定性信任选择, 精确身份锁定与重验证, 宿主/供应商能力交集, 歧义候选拒绝, 插件管理凭据隔离, 不含敏感内容的安全诊断, 取消/终态会话竞态的确定性所有权, 请求绑定的工具及 descriptor JSON 验证, streaming 聚合长度/SHA-256/配额校验, 解码后 JSON 重复键拒绝, 含敏感数据的模型 `toString` 摘要化及能力集合的域分离缓存键
* `优化` 已接通的 AI Provider V2 宿主路径将精确组件 PackageManager 发现与 Binder metadata 协商, 分页 `listTargets` 协调, 每次分发及回调身份复查, 单次消费目录 token, 不含凭据的不可变目标计划, 有界 session/PFD 传输, 流式 credit, 进程熔断, 唯一终态所有者及幂等清理统一用于显式 `ai.*` 插件路由
* `优化` Ace WebView 运行时/JavaScript bridge/内置语言服务/静态资源完成插件化, 降低宿主 APK 耦合并支持独立安装及更新
* `优化` 项目离线文档完成插件化, 降低宿主 APK 体积并支持独立安装及更新
* `优化` 离线项目文档首次加载的文件清单解析及完整 SHA-256 校验移至后台线程, 避免阻塞界面
* `优化` APK 构建所需的 AXML/ARSC/解析/签名/密钥库及密码学工具链全部迁入 APK Builder Template 插件, 宿主改用轻量 APK 信息检查
* `优化` 代码编辑器引擎选择尊重用户显式设置, Ace 不可用时提供插件中心跳转入口
* `优化` 代码编辑器 "运行" 按钮点击后变换为 "停止" 按钮, 可便捷停止脚本
* `优化` 调整代码编辑器操作栏及菜单层级, 将日志设为常驻操作按钮, 并重新整理文件编码与只读模式入口
* `优化` 代码编辑器完善主题/字体/工具栏/只读模式/折叠/选择菜单等交互体验
* `优化` 代码编辑器完善文件编码检测及保存安全性, 编码转换仅在用户保存文件时写入磁盘
* `优化` 进一步压缩代码编辑器信息栏高度及水平留白, 为代码区域释放更多空间
* `优化` 底部日志面板支持在独立日志页面点击堆栈帧跳转至源码
* `优化` 项目文档来源切换支持即时生效
* `优化` 文件管理器仅在点击 Manifest 按钮后后台解码及格式化完整的二进制 AndroidManifest.xml, 并优化大文本滑动及双指缩放性能
* `优化` 文件管理器 APK 详情仅在 Manifest 明确指定时显示最高 SDK, 并将安装条目改为异常且仅在存在异常时显示
* `优化` 文件管理器 Markdown 预览支持经安全清理的 GitHub 常用原始 HTML 及 HTTPS 远程图片, 并改善窄屏表格滚动与旧式隐藏注释兼容性
* `优化` 插件中心完善 "排序" 及 "筛选" 对话框的样式及功能
* `优化` 插件中心默认展示常用插件变体并提供独立页面查看其他变体
* `优化` 插件中心完善多语言更新日志/语言缓存/官方认证/索引加载等功能
* `优化` 插件中心优先渲染本地轻量快照与缓存索引, 再并行刷新插件元数据及在线索引; 异常插件保持禁用, 尝试启用时会显示实际异常与恢复提示
* `优化` 主页插件标签页浮动按钮支持长按进入插件中心
* `优化` 项目字符串资源完成多语言翻译同步/文件分类/名称排序及 ASCII 标点规范化
* `优化` 限制 APK 语言资源仅保留项目支持的 10 种配置, 降低安装包体积
* `优化` 构建系统支持动态匹配 Kotlin/KSP/AGP/R8 兼容版本并降低 Gradle 启动内存压力
* `优化` 将未发布的官方 AI 插件定名为 3-Stone AI, 同时以中性 `ai-provider-api`, `org.autojs.plugin.ai.provider.api`, `org.autojs.plugin.AI_PROVIDER`, 新 Binder 描述符, AP schema 域及重命名的一致性测试夹具完全替换跨进程合约; 不保留身份别名
* `优化` 直接实现 AI Provider V2 分页目标发现: 每项目标携带稳定的 target/provider/model 身份, locality, 配置与可用状态, capabilities, limits, controls 及允许的 HTTPS origins; 支持 REMOTE/HYBRID 执行和插件自管凭据, 凭据字节不经过 Binder, 且不保留被取代的 wire 别名
* `优化` 文件管理器目录刷新采用最小差量更新, 未变化的行不再重新绑定, 原地刷新保持当前滚动位置
* `优化` 重构文件管理器的列表布局, 条目呈现, 选择工具栏, 归档工作流, 输入能力, 输出预留及恢复组件, 缩减过大的视图与会话类, 并使导航, 安全边界及操作策略可独立测试
* `优化` TypeScript 编译器绑定与启动改为在有界的应用启动预热中完成, 不再占用首次用户编译; revision 1 项目会话会在进入编辑器或真实编译后保留一个活动绑定, 启动空闲到期、内存压力、失败与应用退出时均安全回退到冷编译
* `优化` Gradle 平台兼容数据及其更新任务已迁至 AutoJs6-Gradle-Platform-Versions; AutoJs6 现直接使用 Maven Local 中的 1.5.0 版本, 不再保留本地数据覆盖、重复抓取器及原有 Puppeteer 依赖
* `依赖` 模块化 Android AXML
* `依赖` 模块化 Android ARSC Editor
* `依赖` 模块化 APK Signer
* `依赖` 模块化 ARSCLib 版本 1.3.8
* `依赖` 模块化 Spongy Castle Prov 版本 1.58.0.0
* `依赖` 模块化 Image Quantization 版本 2.17.0
* `依赖` 模块化 OpenCV 版本 4.8.0
* `依赖` 模块化 AndroidX Media3 版本 1.10.1
* `依赖` 附加 Apache Commons Codec 版本 1.15
* `依赖` 附加 juniversalchardet 版本 2.0.4
* `依赖` 移除 Android Annotations 版本 4.8.0
* `依赖` 移除 APK Parser 本地模块
* `依赖` 移除 Glide KSP 版本 5.0.5
* `依赖` 移除 ICU4J 版本 77.1
* `依赖` 移除 Jaredrummler Android Device Names 版本 2.1.1
* `依赖` 移除 JCIP Annotations 版本 1.0
* `依赖` 移除 Junit Jupiter 版本 5.13.4
* `依赖` 移除 Retrofit2 Converter Gson 版本 3.0.0
* `依赖` 移除 Retrofit2 Kotlin Coroutines Adapter 版本 0.9.2
* `依赖` 移除 Tiny Sign 版本 0.9
* `依赖` 移除 app 模块重复声明的 AndroidX Room KTX 版本 2.8.1
* `依赖` 移除 AndroidX MultiDex 版本 2.0.1
* `依赖` 移除 MLKit Barcode Scanning 版本 17.3.0
* `依赖` 移除 MLKit Text Recognition Chinese 版本 16.0.1
* `依赖` 移除 OpenCC 版本 1.2.2
* `依赖` 移除 Pinyin4j 版本 2.5.1
* `依赖` 移除 Jieba Analysis 版本 1.0.3-SNAPSHOT (modified)
* `依赖` 移除 Rapid OCR 本地库

# v6.7.0

###### 2026/03/14

* `新增` 插件中心功能, 支持插件的安装/卸载/更新等操作 (入口: 主页抽屉按钮/主页标签页)
* `新增` 版本历史功能, 支持查看/恢复可编辑文件的历史版本 (入口: 主页抽屉按钮/文件管理器菜单/代码编辑器菜单)
* `新增` 回收站功能, 支持查看/恢复已删除的文件/文件夹 (入口: 主页抽屉按钮)
* `新增` Paddle OCR (PP-OCRv5) 插件, 用于光学字符识别
* `新增` cvt 模块, 用于数据单位转换 (参阅 项目文档 > [单位转换](https://docs.autojs6.com/#/cvt))
* `新增` fmt 模块, 用于数据格式化 (参阅 项目文档 > [格式化](https://docs.autojs6.com/#/fmt))
* `新增` zip 模块, 用于文件压缩与解压缩操作 (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (参阅 项目文档 > [Zip](https://docs.autojs6.com/#/zip))
* `新增` mediainfo 模块, 用于查看媒体文件的详细信息 (参阅 项目文档 > [媒体信息](https://docs.autojs6.com/#/mediainfo))
* `新增` cvt.bytes 方法, 用于字节数据单位转换 (参阅 项目文档 > [单位转换](https://docs.autojs6.com/#/cvt))
* `新增` fmt.bytes 方法, 用于字节数据格式化 (参阅 项目文档 > [格式化](https://docs.autojs6.com/#/fmt))
* `新增` s13n.bytes 方法, 用于标准化字节数据 (参阅 项目文档 > [标准化](https://docs.autojs6.com/#/s13n))
* `新增` app.isDualInstalled 方法, 用于检测双开应用是否已安装 (需要 Shizuku 或 Root 权限) _[`issue #450`](http://issues.autojs6.com/450)_
* `新增` device.getSharedDeviceId 方法, 用于跨应用获取统一共享设备 ID _[`issue #455`](http://issues.autojs6.com/455)_
* `新增` device.setPointerLocation 等 Toggleable 系列方法, 用于设置或获取指针位置系统设置项 _[`issue #381`](http://issues.autojs6.com/381)_
* `新增` dialogs.build 方法支持 textAllCaps/(positive/negative/neutral)TextAllCaps 选项参数, 用于控制按钮文本是否全部大写
* `新增` images.loadAsync 方法, 用于异步获取网络图像资源 _[`issue #327`](http://issues.autojs6.com/327)_
* `新增` ui.getNavigationBarHeight 方法/navigationBarHeight 属性 (getter), 用于获取导航栏高度 _[`issue #456`](http://issues.autojs6.com/456)_
* `新增` ui.getVisible(Status/Navigation)BarHeight 方法/visible(Status/Navigation)BarHeight 属性 (getter), 用于快捷获取状态栏或导航栏可见高度 _[`issue #456`](http://issues.autojs6.com/456)_
* `新增` http.put/del(ete)/head 方法, 用于使用不同的 HTTP 请求方法发送网络请求
* `新增` http.getAsync/postAsync/requestAsync 等方法, 用于异步发送网络请求 _[`issue #423`](http://issues.autojs6.com/423)_
* `新增` http 模块请求相关方法获取的 body 对象增加 stream/saveToFile/close 等方法 _[`issue #452`](http://issues.autojs6.com/452)_
* `新增` http 模块请求相关方法支持缓存控制选项参数 (cacheBody/bodyCacheThresholdBytes)
* `新增` http 模块请求相关方法支持不安全选项参数 (isInsecure/insecure), 用于忽略证书相关异常 _[`issue #417`](http://issues.autojs6.com/417)_
* `新增` http 模块请求相关方法支持 options.client 选项, 用于配置 OkHttpClient.Builder (如 followRedirects 等) _[`issue #454`](http://issues.autojs6.com/454)_
* `新增` auto.state 属性 (getter) 及 shizuku.state 属性 (getter), 用于获取无障碍服务状态及 Shizuku 服务状态
* `新增` runtime.(set/is)JavaPrimitiveWrap 方法, 用于设置或获取 Java 原始类型包装策略 _[`issue #435`](http://issues.autojs6.com/435)_
* `新增` autojs.(restart/exit) 方法, 用于重启或退出 AutoJs6 应用, 并支持应用重启时自动运行其参数指定的脚本 _[`issue #460`](http://issues.autojs6.com/460)_
* `新增` UiObject#isShifted 方法, 用于检测控件位置变化 _[`issue #469`](http://issues.autojs6.com/469)_
* `新增` UiObject#snapshot 方法, 用于捕获控件当前状态 _[`issue #469`](http://issues.autojs6.com/469)_
* `新增` engines.myEngine().getStartTime() 方法, 用于获取脚本引擎启动时间戳 _[`issue #449`](http://issues.autojs6.com/449)_
* `新增` crash 全局方法, 用于测试崩溃报告页面
* `新增` structuredClone 全局方法, 用于深拷贝 JavaScript 对象 (参阅 [MDN](https://developer.mozilla.org/zh-CN/docs/Web/API/Window/structuredClone))
* `新增` 等价系列选择器 (UiSelector#id/text/...) 及包含系列选择器 (UiSelector#xxxContains) 支持正则表达式参数
* `新增` 选择器的正则表达式参数支持使用标志 (i, m, s, u)
* `新增` 正则表达式支持后瞻断言语法 _[`issue #464`](http://issues.autojs6.com/464)_
* `新增` 文件管理器增加 "移动到" 及 "复制到" 菜单项, 支持操作中止及进度状态显示
* `新增` 代码编辑器增加 "多功能键盘" - "符号设置" 菜单项, 支持符号编辑及按配置级别进行符号的创建/导入/导出等
* `新增` 主页抽屉增加 "指针位置" 工具
* `新增` 主页抽屉增加 "所有文件管理权限" 开关
* `新增` 主页抽屉增加 "后台弹出界面" 开关 (针对 [小米/Vivo] 设备)
* `新增` 设置页面增加 "Java 原始类型包装" 设置选项 _[`issue #435`](http://issues.autojs6.com/435)_
* `新增` 设置页面增加 "定时任务调度引擎" 设置选项, 支持 AlarmManager/WorkManager/JobScheduler _[`issue #457`](http://issues.autojs6.com/457)_ _[`issue #434`](http://issues.autojs6.com/434)_ _[`issue #449`](http://issues.autojs6.com/449)_ _[`issue #388`](http://issues.autojs6.com/388)_ _[`issue #378`](http://issues.autojs6.com/378)_ _[`issue #163`](http://issues.autojs6.com/163)_ _[`issue #53`](http://issues.autojs6.com/53)_ _[`issue #21`](http://issues.autojs6.com/21)_
* `新增` 设置页面增加 "应用启动器图标" 设置选项, 支持自适应图标/透明背景图标 _[`issue #405`](http://issues.autojs6.com/405)_
* `新增` 设置页面增加 "重启策略" 设置选项, 用于设置主页抽屉栏重启按钮是否使用快速重启策略
* `新增` 设置页面增加 "屏幕捕获权限申请延迟" 设置选项, 用于避免权限申请窗口遮挡屏幕
* `新增` 设置页面启动器快捷方式增加 "插件" 选项, 用于通过快捷方式跳转到插件中心页面
* `新增` 打包应用支持开机自启动并在配置页面增加开关配置
* `新增` 打包应用支持应用快捷方式 (日志页面/设置页面)
* `新增` 打包应用配置页面增加撤销/重做/保存按钮
* `新增` 打包应用配置页面增加配置机制, 支持配置的建立/修改/删除/导入/导出 _[`issue #483`](http://issues.autojs6.com/483)_
* `新增` Scrapers 工具 (run-scrapers.mjs) 用于自动更新 Gradle 构建脚本结构化数据/README 通用数据/README 模板数据等
* `新增` Gradle Wrapper 切换工具 (gradle-wrapper-switcher), 用于切换本地 Gradle 主版本 (如 Gradle 8.x 与 Gradle 9.x)
* `修复` notice 方法参数数量及类型限制错误 _[`issue #462`](http://issues.autojs6.com/462)_
* `修复` ui.R 属性 (getter) 无法正常使用的问题
* `修复` ui.post 方法回调参数中产生异常时可能静默失败的问题
* `修复` isJavaClass/isJavaPackage 等全局方法无效的问题
* `修复` timers.keepAlive 方法 timeout 参数功能无效的问题
* `修复` floaty.window/rawWindow 方法无法接受字符串参数的问题 _[`issue #467`](http://issues.autojs6.com/467)_
* `修复` floaty.rawWindow 因传入错误的上下文导致部分控件显示异常的问题
* `修复` util.isString 方法无效的问题
* `修复` util.class[Name]/getClass[Name] 可能返回错误结果的问题
* `修复` util.java.array 无法正常使用 Boolean 等类型参数的问题
* `修复` threads.disposable() 返回的对象在存取数据时可能被意外装箱的问题 _[`issue #435`](http://issues.autojs6.com/435)_
* `修复` console.build 方法多次调用时, 日志浮动窗口样式选项未能正常重置的问题
* `修复` console.build 方法的 (title/content)BackgroundColor 选项导致透明度或着色选项被覆盖的问题 _[`issue #458`](http://issues.autojs6.com/458)_
* `修复` console/toast 等方法显示 Java Double 时可能丢失末尾零的问题
* `修复` 无法使用 console/toast 等方法显示 BigInt 数据类型的问题
* `修复` toast 方法因持有 Toast 实例强引用而导致可能得内存泄露问题
* `修复` 部分全局对象可能丢失 JavaScript 原型属性及方法的问题
* `修复` 使用 XML 语法将 JavaScript 表达式作为属性值时, this 对象可能出现指向错误的问题
* `修复` dialogs.build 方法 linkify 选项参数功能失效的问题
* `修复` dialogs.build 方法可能破坏对象参数原有数据的问题
* `修复` canvas 元素控件 setMaxFps 方法内部帧率计算错误
* `修复` images.concat 方法纵向拼接时宽度值计算错误
* `修复` images.requestScreenCapture 方法调用时用户取消授权可能导致应用崩溃的问题
* `修复` images.captureScreen 方法获取的截图可能存在方向错误或黑边填充的问题 _[`issue #337`](http://issues.autojs6.com/337)_
* `修复` images 部分方法使用区域参数时 1 被误解析为百分比的问题
* `修复` images 部分相关方法出现异常时 oneShot 标记功能失效的问题 _[`issue #372`](http://issues.autojs6.com/372)_
* `修复` images 部分相关方法可能引发内存泄露的问题 _[`issue #372`](http://issues.autojs6.com/372)_
* `修复` images.save 及 ImageWrapper#saveTo 方法传入路径不存在时无法正常写入文件的问题
* `修复` ImageWrapper#recycle 方法回收图像资源功能失效的问题
* `修复` ocr 部分重载方法可能无法正常使用的问题
* `修复` ocr.detect 方法获得的结果可能与 ocr.mode 不匹配的问题 _[`issue #468`](http://issues.autojs6.com/468)_
* `修复` auto.registerEvent 注册的无障碍服务事件会被其他脚本误清理的问题 _[`issue #466`](http://issues.autojs6.com/466)_ _[`issue #343`](http://issues.autojs6.com/343#issuecomment-3263953918)_
* `修复` Android 10 UiObject#child 方法可能出现 ArrayIndexOutOfBoundsException 异常的问题 _[`issue #416`](http://issues.autojs6.com/416)_
* `修复` 内置模块方法调用可能出现 Rhino 上下文可能不一致的问题
* `修复` 脚本创建大量浮动窗口后, 脚本退出时可能出现窗口残留的问题
* `修复` 运行项目时 project.json 配置参数可能无法正常解析的问题
* `修复` 项目打包时 project.json 的 excludedDirs 配置参数将导致配置文件解析失败的问题 _[`issue #428`](http://issues.autojs6.com/428)_
* `修复` Android 7.x 可能无法正常使用打包功能的问题
* `修复` 脚本项目配置文件保存时原始键名可能会被修改的问题
* `修复` 脚本项目配置文件中构建版本号或构建时间出现较大数字时可能导致应用崩溃的问题
* `修复` 频繁获取或重建 ImageReader 时可能因缓冲区暂无可用帧导致应用崩溃的问题
* `修复` 输入事件观察器 InputEventObserver 可能导致应用启动时明显卡顿的问题
* `修复` Shizuku 用户服务进程未能正常结束导致进程堆积的问题 _[`issue #474`](http://issues.autojs6.com/474)_
* `修复` 打包应用无法正常使用 Paddle OCR 与 Rapid OCR 功能的问题
* `修复` 打包应用配置文件 project.json 中的 key 值可能被意外修改的问题
* `修复` 打包应用配置页面滑动 "需要的权限" 列表时可能出现复选框图标闪烁的问题
* `修复` 发行历史页面部分系统因字体差别导致统计数据显示不完整的问题
* `修复` 部分设备无法正常初始化 MLKit Google OCR 的问题 (试修) _[`issue #8`](http://issues.autojs6.com/8#issuecomment-3117061768)_
* `修复` 部分设备无法正常触发文件管理器功能按钮点击事件的问题 (试修) _[`issue #465`](http://issues.autojs6.com/465)_
* `修复` 部分设备代码编辑器空行显示方框字符的问题 (试修)
* `修复` 代码编辑器软键盘可能遮挡文本内容的问题
* `修复` 代码编辑器在只读模式下依然可以编辑代码内容的问题
* `修复` 代码编辑器在只读模式下点击标题区域及部分菜单项导致应用崩溃的问题
* `修复` 代码编辑器加载大文件可能导致应用崩溃的问题 (试修)
* `修复` 代码编辑器自动打开新建文件时功能按钮状态初始化异常
* `修复` 代码编辑器编辑文件时置于后台可能导致应用崩溃的问题
* `修复` Android 15+ 代码编辑器点击 fx 按钮无法显示模块函数快捷面板的问题 (试修)
* `修复` ErrorDialogActivity 可能无法正常启动或短时间自动消失的问题 _[`issue #479`](http://issues.autojs6.com/479)_ _[`issue #471`](http://issues.autojs6.com/471)_ _[`issue #414`](http://issues.autojs6.com/414)_ _[`issue #340`](http://issues.autojs6.com/340#issuecomment-2973485826)_
* `修复` Canvas 构造函数可接受的参数类型错误 _[`issue #402`](http://issues.autojs6.com/402)_
* `修复` 崩溃报告页面复制详细信息功能失效的问题
* `修复` Android 16+ 自定义返回逻辑失效导致返回功能异常的问题
* `修复` 文件管理器搜索结果展示后点击返回按钮或切换标签页面时可能导致应用崩溃的问题
* `修复` 文件管理器删除项目文件夹后 UI 未能自动刷新的问题
* `修复` 任务面板列表项频繁变动时可能导致应用崩溃的问题
* `修复` 使用 "三按钮" 手势导航时导航栏前景色可能与夜间模式关联异常的问题
* `修复` APK 文件类型信息对话框可能无法获取应用名称及 SDK 信息的问题
* `修复` 客户端模式连接后, 短时间断开连接并再次连接时, 出现握手超时且难以再次建立连接的问题
* `修复` 客户端模式连接时, 旋转屏幕及切换语言等触发 Activity 重建的操作导致 AutoJs6 总是重建连接的问题
* `修复` 服务端模式连接时, 旋转屏幕及切换语言等触发 Activity 重建的操作导致 VSCode 控制台无法输出日志的问题 _[`issue #385`](http://issues.autojs6.com/385)_
* `修复` 连接 VSCode 插件时, 多种方式同时连接可能导致日志打印数量成倍增加的问题
* `修复` 布局分析页面生成代码时对于集合控件可能生成失败的问题 (试修) _[`issue #328`](http://issues.autojs6.com/328)_
* `修复` 设置页面 "使用 '音量减' 键控制录制" 开关功能失效的问题 _[`issue #480`](http://issues.autojs6.com/480)_
* `修复` 录制脚本生成的代码文件可能出现坐标数值与屏幕实际像素值不匹配的问题 _[`issue #480`](http://issues.autojs6.com/480)_
* `修复` 录制脚本生成的代码文件首个行为 (如点击或滑动等) 总是被忽略的问题
* `修复` 浮动按钮 "运行脚本" 对话框后台操作文件时可能导致应用崩溃的问题
* `修复` 主页活动页面生命周期结束后重新进入主页时, 浮动按钮状态可能被重置的问题
* `修复` 小米设备 "显示在其他应用上层" 开关可能跳转到错误设置页面的问题
* `修复` 检查更新中止下载过程后, 网络数据包依然持续后台获取的问题 (试修)
* `修复` 构建工具启用 isCleanup[Paddle/Rapid]Ocr 配置选项时无法正常完成 Rebuild Project 任务的问题
* `优化` 插件化 Paddle OCR (PP-OCRv3) 相关功能 _[`issue #334`](http://issues.autojs6.com/334)_ _[`issue #331`](http://issues.autojs6.com/331)_ _[`issue #325`](http://issues.autojs6.com/325)_
* `优化` runtime.loadJar 方法增强兼容性 (by [LYS86](https://github.com/LYS86)) _[`pr #475`](http://pr.autojs6.com/475)_
* `优化` ui.getStatusBarHeight 方法/statusBarHeight 属性 (getter) 增强一定程度的兼容性
* `优化` console.log 等方法打印全局对象 (images, app, ocr 等) 时支持显示详细信息
* `优化` dialogs.build 方法支持使用 MaterialDialog.Builder 原生选项参数
* `优化` dialogs.build 方法 animation/linkify 选项参数支持字符串宽松匹配
* `优化` device.rotation/rotation/width/height 方法获取屏幕方向及宽高数据的稳定性与一致性
* `优化` app.launchDual/startDualActivity 等方法提升执行效率并增强兼容性
* `优化` android.graphics.Paint#setColor 支持正常解析 ColorInt/ColorHex/ColorName 等颜色参数
* `优化` 内置模块相关方法实参类型的异常消息增加类型摘要信息
* `优化` 内置模块 (dayjs/cheerio/dayjs/i18n) 脱离脚本执行模式, 使用全局懒加载方式替代
* `优化` 控制台相关方法打印巨型数据时根据上限适当省略部分内容以降低打印耗时
* `优化` 控制台浮动窗口倒计时起始值由 6 秒增加到 9 秒
* `优化` 控制台浮动窗口内部实现进行无锁化及队列化处理以提升其参数设置效率与成功率
* `优化` 控制台浮动窗口背景色彩行为相关 API (透明度/着色/基色) 更符合安卓设计规范 _[`issue #458`](http://issues.autojs6.com/458)_
* `优化` 文件管理器浮动按钮展开后点击菜单项时优化菜单收起时机
* `优化` 文件管理器/任务面板支持显示文件/任务数量统计信息
* `优化` 代码编辑器保存文件失败时自动存为草稿并支持另存为新文件
* `优化` 代码编辑器加载大文件时提升一定程度的流畅度
* `优化` 代码编辑器 "查找/替换" 支持状态持久化及实时显示搜索计数信息
* `优化` 代码编辑器提示保存时确保保存成功后再退出编辑器以降低保存失败率
* `优化` 代码编辑器保存按钮的状态更符合用户主观逻辑
* `优化` 代码编辑器支持 "-", "=", "(", "[", "{", "<" 等按键长按功能
* `优化` 打包应用打包过程对话框增加 "中止" 按钮
* `优化` 打包应用打包过程对话框的显示方式并增加耗时统计
* `优化` 打包应用使用新配置安装时将覆盖旧应用的相关配置
* `优化` 打包应用配置页面默认勾选必要权限 (WAKE_LOCK/INTERNET/WRITE_EXTERNAL_STORAGE/...) _[`issue #397`](http://issues.autojs6.com/397)_
* `优化` 打包应用配置页面支持更多选项的读取及配置 (启动时显示日志/启动时显示启动页/启动页标语文本/...)
* `优化` 打包应用设置页面增加前台服务开关 _[`issue #406`](http://issues.autojs6.com/406)_
* `优化` 脚本项目配置文件保存时增加键名冲突检测机制防止键名歧义
* `优化` 崩溃报告页面支持双指缩放调整字体大小并添加常用功能按钮
* `优化` 应用启动器图标支持自适应图标特性 _[`issue #405`](http://issues.autojs6.com/405)_
* `优化` Rhino 引擎在泛型签名解析失败时回退为原始反射类型以增强低版本安卓系统的反射方法可用性
* `优化` 主页抽屉开关类条目添加分隔线, 支持点击标题文字区域显示详情对话框并按需支持快捷跳转系统设置
* `优化` 启动或重启 AutoJs6 时支持点击主页抽屉 "客户端模式" 标题文字区域中止正在尝试建立的连接
* `优化` 客户端模式连接时支持使用 IPv6 地址及域名地址建立连接
* `优化` 客户端模式连接时支持特殊用途 IPv4 地址 (回环/广播/多播/保留/...) 检测提示
* `优化` 客户端模式连接时支持连接状态显示及管理 (修正地址/中止连接)
* `优化` 服务端模式连接时支持显示已建立连接的客户端数量
* `优化` 读取通知权限支持自动重连机制 ([requestRebind](https://developer.android.com/reference/android/service/notification/NotificationListenerService#requestRebind(android.content.ComponentName))) 以增强通知监听相关功能的稳定性
* `优化` 浮动按钮增强后台启动 Activity 的安全性以避免应用崩溃
* `优化` 浮动按钮 "更多" 对话框使用异步加载数据方式提升显示流畅度
* `优化` 浮动按钮 "运行脚本" 对话框增加 "主页" 菜单项
* `优化` 浮动按钮 "运行脚本" 对话框支持最小化及状态恢复并尽最大努力保持窗口常驻或自动恢复
* `优化` 支持使用 Shizuku 权限录制脚本及录制回放 (回放流畅度受一定影响) _[`issue #320`](http://issues.autojs6.com/320)_
* `优化` 主题色设置页面定位主题色时使用快速定位方式以提升定位效率
* `优化` 使用 [LiveData](https://developer.android.com/topic/libraries/architecture/livedata) 及 [SharedFlow](https://developer.android.com/kotlin/flow/stateflow-and-sharedflow) 替代已弃用的 [LocalBroadcastManager](https://developer.android.com/jetpack/androidx/releases/localbroadcastmanager)
* `优化` Gradle 构建脚本提升 7z 格式文件的解压效率
* `优化` Gradle 构建脚本支持获取详细的 Android Studio IDE 版本 (如 "2025.1.4.7")
* `优化` Gradle 构建脚本支持自动生成 VersionCodesList 类所需数据以降低脚本启动延迟
* `优化` 使用 [版本目录 (Version Catalogs)](https://developer.android.com/build/migrate-to-catalogs) 集中管理 Gradle 依赖和插件版本
* `优化` 模块化 Gradle 构建脚本, 将共享构建逻辑迁移至 build-logic 并抽象为约定插件
* `优化` 使用 Gradle 约定插件简化本地 AAR 库加载逻辑
* `优化` 移除 "android.enableJetifier=true" Gradle 配置选项以适配 Gradle 9
* `优化` 使用 [Toolchain](https://docs.gradle.org/current/userguide/toolchains.html) 替代 sourceCompatibility/targetCompatibility 以降低构建环境差异
* `依赖` 模块化 Expandable Layout 版本 1.6.0
* `依赖` 模块化 Expandable RecyclerView 版本 3.0.0-RC1
* `依赖` 模块化 Recyclerview Flexible Divider 版本 1.4.0
* `依赖` 本地化 Root Shell 版本 1.6
* `依赖` 附加 AndroidX Activity (KTX) 版本 1.12.2
* `依赖` 附加 AndroidX Core (KTX) 版本 1.15.0
* `依赖` 附加 AndroidX Work Runtime 版本 2.11.0
* `依赖` 附加 AndroidX Interpolator 版本 1.0.0
* `依赖` 附加 Room RxJava2 版本 2.8.1
* `依赖` 升级 Android SDK 版本 35 -> 36
* `依赖` 升级 Gradle 8 版本 8.14 -> 8.14.4
* `依赖` 升级 Gradle 9 版本 8.14 -> 9.4.0
* `依赖` 升级 Rhino 版本 1.8.1-SNAPSHOT -> 2.0.0-SNAPSHOT
* `依赖` 升级 Desugar JDK Libs 版本 2.0.4 -> 2.1.5
* `依赖` 升级 Android Material 版本 1.12.0 -> 1.13.0
* `依赖` 升级 AndroidX AppCompat 版本 1.7.0 -> 1.7.1
* `依赖` 升级 AndroidX Room 版本 2.7.1 -> 2.7.2
* `依赖` 升级 AndroidX WebKit 版本 1.13.0 -> 1.14.0
* `依赖` 升级 Material Progressbar 版本 1.4.2 -> 1.6.1
* `依赖` 升级 Apache Commons 版本 3.16.0 -> 3.18.0
* `依赖` 升级 Retrofit2 Retrofit 版本 2.11.0 -> 2.12.0
* `依赖` 升级 Retrofit2 Converter Gson 版本 2.11.0 -> 2.12.0
* `依赖` 升级 Retrofit2 RxJava2 版本 2.11.0 -> 2.12.0
* `依赖` 升级 Joda Time 版本 2.12.7 -> 2.14.0
* `依赖` 升级 Gson 版本 2.11.0 -> 2.13.1
* `依赖` 升级 ARSCLib 版本 1.3.1 -> 1.3.5
* `依赖` 升级 Jsoup 版本 1.19.1 -> 1.20.1
* `依赖` 升级 Junit Jupiter 版本 5.10.3 -> 5.13.0
* `依赖` 升级 OpenCC 版本 1.2.0 -> 1.2.2
* `依赖` 移除 Android Support Annotation v7 版本 28.0.0
* `依赖` 移除 Android Support AppCompat v7 版本 28.0.0
* `依赖` 移除 Android Support RecyclerView v7 版本 28.0.0

# v6.6.4

###### 2025/05/31

* `提示` API 变更: ui.(status/navigation)BarAppearanceLight[By] -> ui.(status/navigation)BarIconLight[By]
* `新增` util.dpToPx/spToPx/pxToDp/pxToSp 方法, 用于像素单位转换
* `修复` 屏幕旋转至横向时子标题可能显示不完整的问题
* `修复` 屏幕旋转至横向时部分页面内容被侧边导航栏遮挡的问题
* `修复` Android 15 部分页面状态栏背景着色区域不完整的问题 _[`issue #398`](http://issues.autojs6.com/398)_
* `修复` 代码编辑器可能使用置信度不足的编码写入文件导致内容解码异常的问题 (试修)
* `优化` 关于应用与开发者页面增加布局适配通用性并去除不必要的布局分类
* `优化` README.md 项目编译构建小节添加多种方式便于定位目标设置页面 _[`issue #404`](http://issues.autojs6.com/404)_
* `依赖` 附加 AndroidX ConstraintLayout 版本 2.2.1

# v6.6.3

###### 2025/05/27

* `新增` 发行历史功能, 可查看发行版本的发行历史记录 (多语言) 与统计数据
* `新增` timers.keepAlive 方法 (已全局化), 用于保持脚本活跃状态
* `新增` engines.on('start/stop/error', callback) 等事件监听方法, 用于监听脚本引擎全局事件
* `新增` images.detectMultiColors 方法, 用于多点颜色校验 _[`issue #374`](http://issues.autojs6.com/374)_
* `新增` images.matchFeatures/detectAndComputeFeatures 方法, 支持全分辨率找图 (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) _[`issue #366`](http://issues.autojs6.com/366)_
* `新增` images.compressToBytes 方法, 用于压缩图像并生成字节数组
* `新增` images.downsample 方法, 用于像素降采样并生成新的 ImageWrapper
* `新增` ui.keepScreenOn 方法, 用于 UI 页面获取焦点时保持设备屏幕常亮
* `新增` ui.root 属性 (getter), 用于获取 UI 页面布局的 "窗口内容根容器" 节点
* `新增` webview 元素支持基于 JsBridge 的 Web 页面布局 (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) [参阅 示例代码 > 布局 > 可交互 HTML / Vue2 + Vant (SFC)] _[`issue #281`](http://issues.autojs6.com/281)_
* `新增` 布局分析支持控件隐藏 (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #371`](http://pr.autojs6.com/371)_ _[`issue #355`](http://issues.autojs6.com/355)_
* `修复` 主页文档标签及文档活动页面显示在线文档时部分内容被系统导航栏遮挡的问题
* `修复` 部分页面 Toolbar 点击按钮时可能导致标题点击事件误触发的问题
* `修复` 部分设备代码编辑器空行显示方框字符的问题 (试修)
* `修复` 主题色设置页面调色盘对话框可能无限叠加的问题
* `修复` 无障碍服务关闭时音量加键停止所有脚本功能失效的问题
* `修复` 定时任务页面编辑自定义广播内容时出现的输入法遮挡问题
* `修复` webview 元素中的控件无法正常激活输入法软键盘的问题
* `修复` APK 文件类型信息对话框可能无法获取应用名称及 SDK 信息的问题
* `修复` 文件管理器示例代码进入项目目录时可能无法自动加载子目录文件内容的问题
* `修复` Android 15 UI 模式顶部内容被状态栏覆盖的问题
* `修复` Android 15 部分页面状态栏背景颜色可能无法动态跟随主题色的问题
* `修复` dialogs 模块无法正常使用 customView 属性的问题 _[`issue #364`](http://issues.autojs6.com/364)_
* `修复` dialogs.input 方法的表达式参数可能无法获得执行结果的问题
* `修复` 使用 JavaAdapter 时导致 ClassLoader 调用栈溢出的问题 _[`issue #376`](http://issues.autojs6.com/376)_
* `修复` console.setContentTextColor 方法导致日志字体颜色丢失默认值的问题 _[`issue #346`](http://issues.autojs6.com/346)_
* `修复` console.setContentBackgroundColor 方法无法接受颜色名称参数的问题 _[`issue #384`](http://issues.autojs6.com/384)_
* `修复` images.compress 方法实现原理由像素降采样修正为编码质量变化
* `修复` images.resize 方法无法正常使用的问题
* `修复` engines.all 方法可能触发 ConcurrentModificationException 的问题 _[`issue #394`](http://issues.autojs6.com/394)_
* `修复` README.md 中部分语言日期格式不正确的问题
* `修复` Gradle 构建脚本可能因获取到无效库档案文件长度而导致构建失败的问题 _[`issue #389`](http://issues.autojs6.com/389)_
* `优化` 布局分析菜单添加渐变分隔线实现一定程度的功能分组
* `优化` 脚本项目配置文件 project.json 支持 permissions 选项 (by [wirsnow](https://github.com/wirsnow)) _[`pr #391`](http://pr.autojs6.com/391)_ _[`issue #362`](http://issues.autojs6.com/362)_
* `优化` 打包单文件时自动读取并勾选已安装应用的声明权限 _[`issue #362`](http://issues.autojs6.com/362)_
* `优化` 主题色扩充适配范围并支持更多控件类型
* `优化` 主页抽屉在横向屏幕或超宽屏幕的宽度适应性
* `优化` 关于应用与开发者页面增加水平布局及小屏布局适配
* `优化` 设置页面对话框相关设置支持 "使用默认值" 菜单选项
* `优化` 文件管理器浮动按钮展开后点击其他区域可自动隐藏
* `优化` 代码编辑器格式化代码支持 `??`, `?.`, `??=` 等运算符
* `优化` 代码编辑器支持 GB18030 / UTF-16 (LE/BE) / Shift_JIS 等编码的文件读写
* `优化` 代码编辑器支持显示文件详细信息 (路径/编码/换行符/字节及字符总数等) _[`issue #395`](http://issues.autojs6.com/395)_
* `优化` 意图相关操作 (编辑/查看/安装/发送/播放等) 增加操作异常提示
* `优化` webview 元素的 url 属性支持相对路径
* `优化` ImageWrapper#saveTo 方法的路径参数支持相对路径
* `优化` images.save 方法使用 quality 参数时支持 png 格式的文件体积压缩 _[`issue #367`](http://issues.autojs6.com/367)_
* `优化` 已忽略更新记录及客户端模式连接地址记录支持清空操作
* `优化` 版本更新信息支持多语言显示 (与当前显示语言同步)
* `优化` 使用异步加载方式一定程度提升文件管理器列表滑动流畅性
* `优化` 脚本异常消息在控制台的显示内容与格式
* `优化` 示例代码支持将文件夹重置为初始内容
* `优化` APK 文件签名信息提升检测效率
* `优化` APK 文件类型信息及媒体文件类型信息优化对话框显示效率及信息展示逻辑
* `优化` Gradle 构建脚本提升版本自适应能力 _[`discussion #369`](http://discussions.autojs6.com/369)_
* `依赖` 模块化 Material Dialogs 版本 0.9.6.0
* `依赖` 模块化 Material Date Time Picker 版本 4.2.3
* `依赖` 本地化 libimagequant 版本 2.17.0
* `依赖` 本地化 libpng 版本 1.6.49
* `依赖` 附加 ICU4J 版本 77.1
* `依赖` 附加 Jsoup 版本 1.19.1
* `依赖` 附加 Material Progressbar 版本 1.4.2
* `依赖` 附加 Flexmark Java HTML to Markdown 版本 0.64.8
* `依赖` 升级 Gradle 版本 8.14-rc-1 -> 8.14
* `依赖` 升级 AndroidX Room 版本 2.7.0 -> 2.7.1

# v6.6.2

###### 2025/04/16

* `新增` ui.statusBarAppearanceLight/statusBarAppearanceLightBy/navigationBarColor 等方法
* `新增` ui.statusBarHeight 属性 (getter), 用于获取状态栏高度 _[`issue #357`](http://issues.autojs6.com/357)_
* `新增` images.flip 方法, 用于图像翻转 _[`issue #349`](http://issues.autojs6.com/349)_
* `新增` 设置页面增加 "文件扩展名" 设置选项
* `新增` 主题色设置页面增加新布局支持 (分组/定位/搜索/历史记录/调色盘增强等)
* `修复` Android 15 状态栏背景颜色与主题色不一致的问题
* `修复` plugins.load 方法无法正常加载插件的问题 _[`issue #290`](http://issues.autojs6.com/290)_
* `修复` dx 库在 Android 7.x 无法正常使用的问题 _[`issue #293`](http://issues.autojs6.com/293)_
* `修复` ScriptRuntime 使用 require 引用内置模块时可能出现的同步状态异常 (试修) _[`issue #298`](http://issues.autojs6.com/298)_
* `修复` notice 模块缺失 getBuilder 等扩展方法的问题 _[`issue #301`](http://issues.autojs6.com/301)_
* `修复` shizuku/shell 等方法无法接受字符串参数的问题 _[`issue #310`](http://issues.autojs6.com/310)_
* `修复` colors.pixel 方法无法接受单通道图像参数的问题 _[`issue #350`](http://issues.autojs6.com/350)_
* `修复` engines.execScript/execScriptFile 等方法执行脚本时默认工作路径异常 _[`issue #358`](http://issues.autojs6.com/358)_ _[`issue #340`](http://issues.autojs6.com/340)_ _[`issue #339`](http://issues.autojs6.com/339)_
* `修复` floaty.window/floaty.rawWindow 无法在子线程执行的问题
* `修复` floaty.getClip 可能无法正常获取剪切板内容的问题 _[`issue #341`](http://issues.autojs6.com/341)_
* `修复` ui.inflate 返回值丢失 attr/on/click 等原型方法的问题
* `修复` 使用 XML 语法将 JavaScript 表达式作为属性值时, 其作用域上下文绑定错误的问题 _[`issue #319`](http://issues.autojs6.com/319)_
* `修复` 部分方法调用出现异常时无法被 try..catch 块捕获的问题 _[`issue #345`](http://issues.autojs6.com/345)_
* `修复` 布局分析页面生成代码时可能导致应用崩溃的问题 _[`issue #288`](http://issues.autojs6.com/288)_
* `修复` 打包应用无法正常使用 shizuku 模块的问题 _[`issue #227`](http://issues.autojs6.com/227)_ _[`issue #231`](http://issues.autojs6.com/231)_ _[`issue #284`](http://issues.autojs6.com/284)_ _[`issue #287`](http://issues.autojs6.com/287)_ _[`issue #304`](http://issues.autojs6.com/304)_
* `修复` 代码编辑器跳转到行尾时可能跳转到下一行起始位置的问题
* `修复` 设置页面连续快速点击对话框类型项目时可能导致应用崩溃的问题
* `优化` 精简打包应用模板 APK 文件大小
* `优化` 应用 (以及打包应用) 支持更多权限 _[`issue #338`](http://issues.autojs6.com/338)_
* `优化` 打包应用配置页面支持 Pinyin 库选项
* `优化` 打包应用主活动页面优化状态栏背景及文字颜色
* `优化` 打包应用设置页面增加访问所有文件和发送通知等特殊权限开关 _[`issue #354`](http://issues.autojs6.com/354)_
* `优化` 控件的文字及图标根据主题色亮度值自动切换合适的颜色
* `优化` 部分控件主题色与背景色对比度过低时的视觉体验
* `优化` 调色盘 HEX 输入控件增强剪贴板粘贴色值时的兼容性
* `优化` 应用内页面导航栏设置为透明或半透明以增强视觉体验
* `优化` UI 模式状态栏及导航栏默认为 `md_grey_50` 色值且设置为亮色模式
* `优化` 主页抽屉无障碍服务开关支持与脚本代码同步
* `优化` 主页文档页面搜索时支持双向查找按钮
* `优化` 主页 "文件" 标签支持通过长按切换浮动按钮可见状态
* `优化` 代码编辑器标题文字支持字体大小自适应
* `优化` 日志页面浮动按钮可见状态与列表滚动操作联动
* `优化` 脚本项目配置文件 project.json 支持更多打包选项 _[`issue #305`](http://issues.autojs6.com/305)_ _[`issue #306`](http://issues.autojs6.com/306)_
* `优化` 脚本项目配置文件 project.json 支持选项名称宽松匹配及别名兼容
* `优化` APK 文件类型信息对话框增加文件大小与签名方案信息
* `优化` APK 文件类型信息对话框增加点击监听器支持文本复制与应用详情跳转
* `优化` 尝试恢复 com.stardust 前缀包以便提升代码兼容性 _[`issue #290`](http://issues.autojs6.com/290)_
* `优化` floaty.window/floaty.rawWindow 同时支持主线程和子线程执行
* `优化` getClip 全局方法适时借助 floaty.getClip 方法以提升兼容性
* `优化` files.path 及相关方法传入空值路径参数时的兼容性
* `优化` 同步最新的 Rhino 引擎官方上游代码并进行必要的代码适配
* `优化` README.md 完善项目构建与运行相关内容 _[`issue #344`](http://issues.autojs6.com/344)_
* `依赖` 附加 Eclipse Paho Client Mqtt V3 版本 1.1.0 _[`issue #330`](http://issues.autojs6.com/330)_
* `依赖` 升级 Android SDK 版本 34 -> 35
* `依赖` 升级 Gradle 版本 8.12 -> 8.14-rc-1
* `依赖` 升级 Rhino 版本 1.8.0-SNAPSHOT -> 1.8.1-SNAPSHOT
* `依赖` 升级 AndroidX Recyclerview 版本 1.3.2 -> 1.4.0
* `依赖` 升级 AndroidX Room 版本 2.6.1 -> 2.7.0
* `依赖` 升级 AndroidX WebKit 版本 1.12.1 -> 1.13.0
* `依赖` 升级 Pinyin4j 版本 2.5.0 -> 2.5.1

# v6.6.1

###### 2025/01/01

* `新增` pinyin 模块, 用于汉语拼音转换 (参阅 项目文档 > [汉语拼音](https://docs.autojs6.com/#/pinyin))
* `新增` pinyin4j 模块, 用于汉语拼音转换 (参阅 项目文档 > [汉语拼音](https://docs.autojs6.com/#/pinyin4j))
* `新增` UiObject#isSimilar 及 UiObjectCollection#isSimilar 方法, 用于确定控件或控件集合是否相似
* `新增` currentComponent 全局方法, 用于获取当前活动的组件名称信息
* `修复` 部分环境因回退版本过低而无法正常编译项目的问题
* `修复` 调用不存在的方法时可能出现的 "非原始类型值" 异常
* `修复` 部分设备无法正常添加脚本快捷方式的问题 (试修) _[`issue #221`](http://issues.autojs6.com/221)_
* `修复` automator.click/longClick 方法参数类型限制错误 _[`issue #275`](http://issues.autojs6.com/275)_
* `修复` 选择器不支持 ConsString 类型参数的问题 _[`issue #277`](http://issues.autojs6.com/277)_
* `修复` UiObjectCollection 实例缺失自身方法及属性的问题
* `优化` 打包应用配置页面支持签名配置/密钥库管理/权限设置 (by [luckyloogn](https://github.com/luckyloogn)) _[`pr #286`](http://pr.autojs6.com/286)_
* `优化` 提升浮动窗口当前包名及当前活动名的识别准确性 (优先级: Shizuku > Root > A11Y)
* `优化` 提升 currentPackage/currentActivity 识别准确性 (优先级: Shizuku > Root > A11Y)
* `优化` 恢复日志活动窗口单个条目文本内容的双击或长按选择功能 _[`issue #280`](http://issues.autojs6.com/280)_
* `优化` 脚本项目识别在 project.json 损坏情况下尽可能还原关键信息
* `优化` 打包单文件时自动生成的包名后缀支持将简体中文转换为拼音 (支持多音字)
* `优化` UiSelector#findOnce 及 UiSelector#find 方法支持负数参数
* `优化` app.startActivity/startDualActivity 方法增强适配性
* `优化` UI 元素及 className 相关选择器支持更多的包名前缀省略形式 (如 RecyclerView, Snackbar 等)
* `优化` 同步最新的 Rhino 引擎官方上游代码并进行必要的代码适配
* `依赖` 附加 Pinyin4j 版本 2.5.0
* `依赖` 附加 Jieba Analysis 版本 1.0.3-SNAPSHOT (modified)
* `依赖` 升级 Gradle 版本 8.11.1 -> 8.12

# v6.6.0

###### 2024/12/02 - 内置模块重写, 谨慎升级

* `提示` 内置模块使用 Kotlin 重新编写以提升脚本运行效率但可能需要多次迭代逐步完善
* `提示` 内置 init.js 文件默认为空但支持开发者自行扩展内置模块或挂载外部模块
* `新增` axios 模块 / cheerio 模块 (Ref to [AutoX](https://github.com/kkevsekk1/AutoX))
* `新增` sqlite 模块, 用于 SQLite 数据库简单操作 (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (参阅 项目文档 > [SQLite](https://docs.autojs6.com/#/sqlite))
* `新增` mime 模块, 用于处理和解析 MIME 类型字符串 (参阅 项目文档 > [MIME](https://docs.autojs6.com/#/mime))
* `新增` nanoid 模块, 可作为字符串 ID 生成器 (Ref to [ai/nanoid](https://github.com/ai/nanoid))
* `新增` sysprops 模块, 用于获取运行时环境配置数据 (参阅 项目文档 > [系统属性](https://docs.autojs6.com/#/sysprops))
* `新增` ocr 模块支持 [Rapid OCR](https://github.com/RapidAI/RapidOCR) 引擎
* `新增` 布局分析支持切换窗口 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` auto.clearCache 方法, 支持清除控件缓存 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` threads.pool 方法, 支持线程池简单应用 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` images.matchTemplate 方法支持 useTransparentMask 选项参数, 支持透明找图 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` images.requestScreenCaptureAsync 方法, 用于 UI 模式异步方式申请截图权限 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` images.requestScreenCapture 方法支持 isAsync 选项参数, 支持异步方式获取屏幕截图 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` images.on('screen_capture', callback) 等事件监听方法, 支持监听屏幕截图可用事件 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` images.stopScreenCapture 方法, 支持主动释放截图申请的相关资源 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` images.psnr/mse/ssim/mssim/hist/ncc 及 images.getSimilarity 方法, 用于获取图像相似性度量值
* `新增` images.isGrayscale 方法, 用于判断图像是否为灰度图像
* `新增` images.invert 方法, 用于反色图像转换
* `新增` s13n.point/time 方法, 用于标准化点对象及时长对象 (参阅 项目文档 > [标准化](https://docs.autojs6.com/#/s13n))
* `新增` console 模块 gravity (重力), touchThrough (穿透点击), backgroundTint (背景着色) 等相关方法 (参阅 项目文档 > [控制台](https://docs.autojs6.com/#/console))
* `新增` Mathx.randomInt/Mathx.randomFloat 方法, 用于返回指定范围内的随机整数或随机浮点数
* `新增` app.launchDual/startDualActivity 等方法, 用于处理双开应用 (需要 Shizuku 或 Root 权限) (实验性)
* `新增` app.kill 方法, 用于强制停止应用 (需要 Shizuku 或 Root 权限)
* `新增` floaty.getClip 方法, 用于借助浮动窗口聚焦间接获取剪切板内容
* `修复` Fragment 子类 (如 [DrawerFragment](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/ui/main/drawer/DrawerFragment.kt#L369) / [ExplorerFragment](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/ui/main/scripts/ExplorerFragment.kt#L48) 等) 中存在的 View Binding 内存泄漏
* `修复` [ScreenCapture](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/core/image/capture/ScreenCapturer.java#L70) / [ThemeColorPreference](https://github.com/SuperMonster003/AutoJs6/blob/10960ddbee71f75ef80907ad5b6ab42f3e1bf31e/app/src/main/java/org/autojs/autojs/ui/settings/ThemeColorPreference.kt#L21) 等类中存在的实例内存泄漏
* `修复` Android 14 申请截图权限导致应用崩溃的问题 (by [chenguangming](https://github.com/chenguangming)) _[`pr #242`](http://pr.autojs6.com/242)_
* `修复` Android 14 开启前台服务导致应用崩溃的问题
* `修复` Android 14 代码编辑器运行按钮点击后无法正常亮起的问题
* `修复` 项目打包后应用可能因缺少必要库文件无法正常运行的问题 _[`issue #202`](http://issues.autojs6.com/202)_ _[`issue #223`](http://issues.autojs6.com/223)_ _[`pr #264`](http://pr.autojs6.com/264)_
* `修复` 编辑项目时可能因指定图标资源不存在而导致应用崩溃的问题 _[`issue #203`](http://issues.autojs6.com/203)_
* `修复` 截图权限申请时无法正常使用参数获取指定屏幕方向的截图资源
* `修复` 部分设备无法正常添加脚本快捷方式的问题 (试修) _[`issue #221`](http://issues.autojs6.com/221)_
* `修复` 调用 http 模块与发送请求相关的方法将出现累积性请求发送延迟的问题 _[`issue #192`](http://issues.autojs6.com/192)_
* `修复` Shizuku 服务在 AutoJs6 进入主活动页面之前可能无法正常使用的问题 (试修) _[`issue #255`](http://issues.autojs6.com/255)_
* `修复` random(min, max) 方法可能出现结果越界的问题
* `修复` pickup 方法结果类型参数无法正常传入空数组的问题
* `修复` UiObject#bounds() 得到的控件矩形可能被意外修改而破坏其不变性的问题
* `修复` text/button/input 元素的文本内容包含半角双引号时无法正常解析的问题
* `修复` text/textswitcher 元素的 autoLink 属性功能失效的问题
* `修复` 不同脚本可能错误地共享同一个 ScriptRuntime 对象的问题
* `修复` 全局变量 HEIGHT 及 WIDTH 丢失 getter 动态属性的问题
* `修复` 脚本启动时 RootShell 随即加载可能导致启动高延迟的问题
* `修复` 控制台浮动窗口设置背景颜色导致矩形圆角样式丢失的问题
* `修复` 无障碍服务自动启动可能出现的服务异常问题 (试修)
* `修复` 主页文档页面左右滑动 WebView 控件时可能触发 ViewPager 切换的问题
* `修复` 文件管理器无法识别包含大写字母文件扩展名的问题
* `修复` 文件管理器首次进入项目目录时可能无法自动识别项目的问题
* `修复` 文件管理器删除文件夹后页面无法自动刷新的问题
* `修复` 文件管理器排序文件及文件夹时可能出现 ASCII 首字母名称置后的问题
* `修复` 代码编辑器调试功能的 FAILED ASSERTION 异常
* `修复` 代码编辑器调试过程中关闭编辑器后无法再次正常调试的问题
* `修复` 代码编辑器跳转到行尾时可能遗漏末尾字符的问题
* `修复` 主活动页面启动日志活动页面时可能出现闪屏的问题
* `修复` 打包应用无法正常使用 opencc 模块的问题
* `优化` 打包应用配置页面中 "不可用 ABI" 控件的点击提示体验
* `优化` 支持使用 Shizuku 控制 "指针位置" 显示开关
* `优化` 支持使用 Shizuku 控制 "投影媒体" 及 "修改安全设置" 权限开关
* `优化` automator.gestureAsync/gesturesAsync 支持回调函数参数
* `优化` tasks 模块使用同步方式进行数据库操作避免可能的数据访问不一致问题
* `优化` 脚本执行模式支持管道符号分隔模式参数 (如 `"ui|auto";` 开头)
* `优化` 脚本执行模式支持单引号及反引号且支持省略分号 (如 `'ui';` 或 `'ui'` 开头)
* `优化` 脚本执行模式支持 axios, cheerio, dayjs 等模式参数快捷导入内置扩展模块 (如 `"axios";` 开头)
* `优化` 脚本执行模式支持 x 或 jsox 模式参数快捷启用 JavaScript 内置对象扩展模块 (如 `"x";` 开头)
* `优化` img 元素 src 及 path 属性支持本地相对路径 (如 `<img src="a.png"` />)
* `优化` 代码编辑器导入 Java 类和包名时支持智能判断插入位置
* `优化` images 模块支持直接使用路径作为图像参数
* `优化` importPackage 支持字符串参数
* `优化` 客户端模式 IP 地址支持剪切板导入智能识别且支持空格按键智能转换
* `优化` 文件管理器新建文件时支持默认前缀选择并自动生成合适的数字后缀
* `优化` 文件管理器运行项目时具体化异常消息提示 _[`issue #268`](http://issues.autojs6.com/268)_
* `优化` 文件管理器支持更多类型并支持显示对应的图标符号 (支持 800 多种文件类型)
* `优化` 文件管理器可编辑的文件类型 (jpg/doc/pdf/...) 增加编辑按钮
* `优化` 文件管理器 APK 文件支持查看基础信息, Manifest 信息及权限列表
* `优化` 文件管理器音视频等媒体文件支持查看基础信息及 MediaInfo 信息
* `优化` 打包单文件时支持自动填入合适的标准化名包并支持无效字符过滤提示
* `优化` 打包单文件时支持根据已安装同包名应用自动设置图标并自增版本号及版本名称
* `优化` 打包项目配置文件支持 abis/libs 选项指定默认包含的 ABI 架构及扩展库
* `优化` 打包项目配置文件 abis/libs 选项无效或无可用时支持相关消息提示
* `优化` LeakCanary 在正式发行版本中被排除以避免增加不必要性
* `优化` 项目源代码所有英文注释增加简体中文翻译以增强注释可读性
* `优化` README 及 CHANGELOG 支持多语言 (由脚本自动生成)
* `优化` Gradle 构建脚本提升版本自适应能力
* `依赖` 附加 MIME Util 版本 2.3.1
* `依赖` 附加 Toaster 版本 12.6
* `依赖` 附加 EasyWindow (for Toaster) 版本 10.3
* `依赖` 升级 Gradle 版本 8.5 -> 8.11.1
* `依赖` 升级 Rhino 版本 1.7.15-SNAPSHOT -> 1.8.0-SNAPSHOT
* `依赖` 升级 Android Material 版本 1.10.0 -> 1.12.0
* `依赖` 升级 AndroidX Annotation 版本 1.7.0 -> 1.9.1
* `依赖` 升级 AndroidX AppCompat 版本 1.6.1 -> 1.7.0
* `依赖` 升级 AndroidX WebKit 版本 1.8.0 -> 1.12.1
* `依赖` 升级 Apache Commons 版本 3.13.0 -> 3.16.0
* `依赖` 升级 ARSCLib 版本 1.2.4 -> 1.3.1
* `依赖` 升级 Gson 版本 2.10.1 -> 2.11.0
* `依赖` 升级 Jackson DataBind 版本 2.13.3 -> 2.13.4.2
* `依赖` 升级 Joda Time 版本 2.12.5 -> 2.12.7
* `依赖` 升级 LeakCanary 版本 2.12 -> 2.14
* `依赖` 升级 MLKit Barcode Scanning 版本 17.2.0 -> 17.3.0
* `依赖` 升级 MLKit Text Recognition Chinese 版本 16.0.0 -> 16.0.1
* `依赖` 升级 Retrofit2 Converter Gson 版本 2.9.0 -> 2.11.0
* `依赖` 升级 Retrofit2 Retrofit 版本 2.9.0 -> 2.11.0
* `依赖` 升级 Desugar JDK Libs 版本 2.0.3 -> 2.0.4
* `依赖` 升级 Test Runner 版本 1.5.2 -> 1.6.2
* `依赖` 升级 Junit Jupiter 版本 5.10.0 -> 5.10.3
* `依赖` 降级 OkHttp3 版本 5.0.0-alpha.11 -> 4.12.0

# v6.5.0

###### 2023/12/02

* `新增` opencc 模块 (参阅 项目文档 > [中文转换](https://docs.autojs6.com/#/opencc)) (Ref to [LZX284](https://github.com/SuperMonster003/AutoJs6/pull/187/files#diff-8cff73265af19c059547b76aca8882cbaa3209291406f52df1dafbbc78e80c46R268))
* `新增` UiSelector 增加 [plus](https://docs.autojs6.com/#/uiObjectType?id=m-plus) 及 [append](https://docs.autojs6.com/#/uiObjectType?id=m-append) 方法 _[`issue #115`](http://issues.autojs6.com/115)_
* `新增` 打包应用配置页面增加 ABI 及库的筛选支持 (Ref to [AutoX](https://github.com/kkevsekk1/AutoX)) _[`issue #189`](http://issues.autojs6.com/189)_
* `修复` 打包应用文件体积异常庞大的问题 (Ref to [AutoX](https://github.com/kkevsekk1/AutoX) / [LZX284](https://github.com/SuperMonster003/AutoJs6/pull/187/files#diff-d932ac49867d4610f8eeb21b59306e8e923d016cbca192b254caebd829198856R61)) _[`issue #176`](http://issues.autojs6.com/176)_
* `修复` 打包应用无法显示并打印部分异常消息的问题
* `修复` 打包应用配置页面选择应用图标后可能显示空图标的问题
* `修复` 打包应用包含 MLKit Google OCR 库时可能出现的上下文未初始化异常
* `修复` ocr.recognizeText/detect 方法无效的问题
* `修复` 部分文本 (如日志页面) 显示语言与应用设置语言可能不相符的问题
* `修复` 部分语言在主页抽屉开关项可能出现文本溢出的问题
* `修复` 部分设备无障碍服务开启后立即自动关闭且无任何提示消息的问题 _[`issue #181`](http://issues.autojs6.com/181)_
* `修复` 部分设备无障碍服务开启后设备物理按键可能导致应用崩溃的问题 (试修) _[`issue #183`](http://issues.autojs6.com/183)_ _[`issue #186`](http://issues.autojs6.com/186#issuecomment-1817307790)_
* `修复` 使用 auto(true) 重启无障碍服务后 pickup 功能异常的问题 (试修) _[`issue #184`](http://issues.autojs6.com/184)_
* `修复` floaty 模块创建浮动窗口拖动时可能导致应用崩溃的问题 (试修)
* `修复` app.startActivity 无法使用简称参数的问题 _[`issue #182`](http://issues.autojs6.com/182)_ _[`issue #188`](http://issues.autojs6.com/188)_
* `修复` importClass 导入的类名与全局变量冲突时代码抛出异常的问题 _[`issue #185`](http://issues.autojs6.com/185)_
* `修复` Android 7.x 无法使用无障碍服务的问题
* `修复` Android 14 可能无法正常使用 runtime.loadJar/loadDex 方法的问题 (试修)
* `修复` 安卓系统快速设置面板中 "布局范围分析" 和 "布局层次分析" 不可用的问题 _[`issue #193`](http://issues.autojs6.com/193)_
* `修复` 自动检查更新功能可能导致应用 [ANR](https://developer.android.com/topic/performance/vitals/anr) 的问题 (试修) _[`issue #186`](http://issues.autojs6.com/186)_
* `修复` 文件管理器示例代码文件夹点击 "向上" 按钮后无法回到工作路径页面的问题 _[`issue #129`](http://issues.autojs6.com/129)_
* `修复` 代码编辑器使用替换功能时替换按钮无法显示的问题
* `修复` 代码编辑器长按删除时可能导致应用崩溃的问题 (试修)
* `修复` 代码编辑器点击 fx 按钮无法显示模块函数快捷面板的问题
* `修复` 代码编辑器模块函数快捷面板按钮函数名称可能溢出的问题
* `优化` 代码编辑器模块函数快捷面板适配夜间模式
* `优化` 打包应用启动页面适配夜间模式并调整应用图标布局
* `优化` 打包应用配置页面支持使用软键盘 ENTER 键实现光标跳转
* `优化` 打包应用配置页面支持点击 ABI 标题及库标题切换全选状态
* `优化` 打包应用配置页面默认 ABI 智能选择并增加不可选择项的引导提示
* `优化` 文件管理器根据文件及文件夹的类型及特征调整菜单项的显示情况
* `优化` 文件管理器文件夹右键菜单增加打包应用选项
* `优化` 无障碍服务启用但功能异常时在 AutoJs6 主页抽屉开关处将体现异常状态
* `优化` 控制台打印错误消息时附加详细的堆栈信息
* `依赖` 附加 ARSCLib 版本 1.2.4
* `依赖` 附加 Flexbox 版本 3.0.0
* `依赖` 附加 Android OpenCC 版本 1.2.0
* `依赖` 升级 Gradle 版本 8.5-rc-1 -> 8.5

# v6.4.2

###### 2023/11/15

* `新增` dialogs.build 方法支持 inputSingleLine 选项参数
* `新增` console.setTouchable 方法 _[`issue #122`](http://issues.autojs6.com/122)_
* `修复` ocr 模块部分方法无法识别区域参数的问题 _[`issue #162`](http://issues.autojs6.com/162)_ _[`issue #175`](http://issues.autojs6.com/175)_
* `修复` Android 7.x 发现新版本时无法获取版本详情的问题
* `修复` Android 14 申请截图权限时导致应用崩溃的问题
* `修复` 主页抽屉快速切换 "浮动按钮" 开关时可能导致应用崩溃的问题
* `修复` 使用菜单关闭浮动按钮时重启应用后浮动按钮可能依然显示的问题
* `修复` Android 13+ 设置页面选择并切换 AutoJs6 语言后无法生效的问题
* `修复` 构建工具初次构建时无法自动完成 OpenCV 资源部署的问题
* `优化` 原生化 bridges 模块以提升脚本执行效率 (Ref to [aiselp](https://github.com/aiselp/AutoX/commit/7c41af6d2b9b36d00440a9c8b7e971d025f98327))
* `优化` 重构无障碍服务相关代码以增强无障碍服务的功能稳定性 (实验性) _[`issue #167`](http://issues.autojs6.com/167)_
* `优化` UiObject 和 UiObjectCollection 的打印输出格式
* `优化` 构建工具在构建环境 Gradle JDK 版本不满足要求时作出升级提示
* `依赖` 升级 Gradle 版本 8.4 -> 8.5-rc-1
* `依赖` 降级 Commons IO 版本 2.14.0 -> 2.8.0
* `依赖` 降级 Jackson DataBind 版本 2.14.3 -> 2.13.3

# v6.4.1

###### 2023/11/02

* `修复` 构建工具无法自适应未知平台的问题 (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #158`](http://pr.autojs6.com/158)_
* `修复` 脚本退出时可能导致应用崩溃的问题 _[`issue #159`](http://issues.autojs6.com/159)_
* `修复` http 模块获取响应对象的 body.contentType 返回值类型错误 _[`issue #142`](http://issues.autojs6.com/142)_
* `修复` device.width 及 device.height 返回数据不正确的问题 _[`issue #160`](http://issues.autojs6.com/160)_
* `修复` 代码编辑器长按删除时可能导致应用崩溃的问题 (试修) _[`issue #156`](http://issues.autojs6.com/156)_
* `修复` 代码编辑器反向选择文本后进行常规操作可能导致应用崩溃的问题
* `修复` 部分设备长按 AutoJs6 应用图标无法显示快捷方式菜单的问题
* `修复` 部分设备打包项目时点击确认按钮无响应的问题
* `修复` app.sendBroadcast 及 app.startActivity 无法使用简称参数的问题
* `修复` floaty 模块 JsWindow#setPosition 等方法首次调用时的功能异常
* `优化` 增加 Termux 相关权限以支持 Intent 调用 Termux 执行 ADB 命令 _[`issue #136`](http://issues.autojs6.com/136)_
* `优化` http 模块获取的响应对象可重复使用 body.string() 及 body.bytes() 方法
* `优化` 增加 GitHub Actions 自动打包支持 (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #158`](http://pr.autojs6.com/158)_
* `优化` 构建工具自适应 Temurin 平台
* `依赖` 升级 Gradle 版本 8.4-rc-3 -> 8.4
* `依赖` 升级 Android dx 版本 1.11 -> 1.14

# v6.4.0

###### 2023/10/30

* `新增` ocr 模块支持 Paddle Lite 引擎 (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #120`](http://pr.autojs6.com/120)_
* `新增` 打包功能支持内置插件与外部插件两种打包方式 (by [LZX284](https://github.com/LZX284)) _[`pr #151`](http://pr.autojs6.com/151)_
* `新增` WebSocket 模块 (参阅 项目文档 > [WebSocket](https://docs.autojs6.com/#/webSocketType))
* `新增` barcode / qrcode 模块 (参阅 项目文档 > [条码](https://docs.autojs6.com/#/barcode) / [二维码](https://docs.autojs6.com/#/qrcode))
* `新增` shizuku 模块 (参阅 项目文档 > [Shizuku](https://docs.autojs6.com/#/shizuku)) 及主页抽屉权限开关
* `新增` device.rotation / device.orientation 等方法
* `新增` 内部 Java 类支持 class 静态属性访问
* `新增` 支持在安卓系统设置页面选择并切换应用语言 (Android 13+)
* `新增` 支持设置页面添加或长按应用图标激活 [应用快捷方式](https://developer.android.com/guide/topics/ui/shortcuts?hl=zh-cn) , 可启动文档和设置等页面
* `修复` 重新合并部分 PR (by [aiselp](https://github.com/aiselp)) 以解决部分脚本无法正常结束运行的问题 _[`pr #75`](http://pr.autojs6.com/75)_ _[`pr #78`](http://pr.autojs6.com/78)_
* `修复` 打包应用无法使用 AutoJs6 新增 API 的问题 (by [LZX284](https://github.com/LZX284)) _[`pr #151`](http://pr.autojs6.com/151)_ _[`issue #149`](http://issues.autojs6.com/149)_
* `修复` 打包应用在系统夜间模式下的样式异常
* `修复` VSCode 插件保存文件到本地时文件扩展名信息丢失的问题
* `修复` 使用协程特性运行项目产生未捕获异常致使应用崩溃的问题
* `修复` 重启或退出应用时浮动按钮无法记录其位置状态信息的问题
* `修复` 设备屏幕方向改变时无法获取更新后的设备配置信息的问题 _[`issue #153`](http://issues.autojs6.com/153)_
* `修复` 屏幕旋转至横向时 Toolbar 标题字体过小的问题
* `修复` 屏幕旋转至横向时应用主页的页签排版过于拥挤的问题
* `修复` 屏幕旋转至横向时浮动按钮可能溢出屏幕的问题 _[`issue #90`](http://issues.autojs6.com/90)_
* `修复` 屏幕多次旋转时无法恢复浮动按钮的坐标及屏幕侧边方向的问题
* `修复` 部分设备消息浮动框可能出现遗漏显示或重复显示的问题
* `修复` 消息浮动框在多个脚本同时运行时可能存在被遮蔽的问题 _[`issue #67`](http://issues.autojs6.com/67)_
* `修复` 使用广播分析布局时点击布局无法弹出菜单且导致应用崩溃的问题
* `修复` 第二次及以后创建的 WebSocket 实例均无法正常触发监听器的问题
* `修复` 撤销 importPackage 的全局重定向方法以避免某些作用域下的包导入异常 _[`issue #88`](http://issues.autojs6.com/88)_
* `修复` 日志活动页面使用复制或导出功能时可能导致应用崩溃的问题
* `优化` 日志活动页面导出功能重命名为发送功能并重新实现符合实际意义的导出功能
* `优化` 日志活动页面发送功能支持条目数量过大时自动截取并作出提示
* `优化` ocr 模块同时兼容 Google MLKit 及 Paddle Lite 引擎 (参阅 项目文档 > [光学字符识别](https://docs.autojs6.com/#/ocr?id=p-mode))
* `优化` 提升无障碍服务自动启动的成功概率
* `优化` Kotlin 注解处理由 kapt 迁移至 KSP
* `优化` 构建工具支持 IntelliJ Idea EAP 版本
* `优化` 构建工具自适应 Java 发行版本以尽量避免 "无效的发行版本" 问题
* `优化` 构建工具优化 IDE 及相关插件的版本退级逻辑并增加版本预测能力
* `优化` 适配 VSCode 插件 1.0.7
* `依赖` 附加 Rikka Shizuku 版本 13.1.5
* `依赖` 附加 MLKit Barcode Scanning 版本 17.2.0
* `依赖` 升级 Android SDK 版本 33 -> 34
* `依赖` 升级 OpenCV 版本 4.5.5 -> 4.8.0 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `依赖` 升级 Gradle 版本 8.3-rc-1 -> 8.4-rc-3
* `依赖` 升级 Apache Commons Lang3 版本 3.12.0 -> 3.13.0
* `依赖` 升级 Glide 版本 4.15.1 -> 4.16.0
* `依赖` 升级 Android Analytics 版本 14.3.0 -> 14.4.0
* `依赖` 升级 AndroidX WebKit 版本 1.7.0 -> 1.8.0
* `依赖` 升级 AndroidX Preference 版本 1.2.0 -> 1.2.1
* `依赖` 升级 AndroidX Annotation 版本 1.6.0 -> 1.7.0
* `依赖` 升级 AndroidX Recyclerview 版本 1.3.0 -> 1.3.2
* `依赖` 升级 Android Material 版本 1.9.0 -> 1.10.0
* `依赖` 升级 AndroidX AppCompat 版本 1.4.2 -> 1.6.1
* `依赖` 升级 Commons IO 版本 2.8.0 -> 2.14.0
* `依赖` 升级 Jackson DataBind 版本 2.13.3 -> 2.14.3
* `依赖` 移除 Zeugma Solutions LocaleHelper 版本 1.5.1

# v6.3.3

###### 2023/07/21

* `新增` 代码编辑器的代码注释功能 (by [little-alei](https://github.com/little-alei)) _[`pr #98`](http://pr.autojs6.com/98)_
* `新增` auto.stateListener 用于无障碍服务连接状态监听 (by [little-alei](https://github.com/little-alei)) _[`pr #98`](http://pr.autojs6.com/98)_
* `新增` UiObject 类型添加 nextSibling / lastChild / offset 等方法 (参阅 项目文档 > [控件节点](https://docs.autojs6.com/#/uiObjectType))
* `修复` VSCode 插件在脚本字符总长度超过四位十进制数时无法解析数据的问题 _[`issue #91`](http://issues.autojs6.com/91)_ _[`issue #93`](http://issues.autojs6.com/93)_ _[`issue #100`](http://issues.autojs6.com/100)_ _[`issue #109`](http://issues.autojs6.com/109)_
* `修复` VSCode 插件无法正常保存文件的问题 _[`issue #92`](http://issues.autojs6.com/91)_ _[`issue #94`](http://issues.autojs6.com/93)_
* `修复` 浮动按钮菜单项 "管理无障碍服务" 点击后可能未发生页面跳转的问题
* `修复` runtime.requestPermissions 方法丢失的问题 _[`issue #104`](http://issues.autojs6.com/104)_
* `修复` events.emitter 不支持 MainThreadProxy 参数的问题 _[`issue #103`](http://issues.autojs6.com/103)_
* `修复` 在 _[`pr #78`](http://pr.autojs6.com/78)_ 中存在的代码编辑器无法格式化代码的问题
* `修复` 使用 JavaAdapter 时导致 ClassLoader 调用栈溢出的问题 _[`issue #99`](http://issues.autojs6.com/99)_ _[`issue #110`](http://issues.autojs6.com/110)_
* `优化` 调整模块作用域 (by [aiselp](https://github.com/aiselp)) _[`pr #75`](http://pr.autojs6.com/75)_ _[`pr #78`](http://pr.autojs6.com/78)_
* `优化` 移除发行版本应用启动时的签名校验 (by [LZX284](https://github.com/LZX284)) _[`pr #81`](http://pr.autojs6.com/81)_
* `优化` 在 _[`pr #98`](http://pr.autojs6.com/98)_ 基础上的编辑器代码注释功能的行为, 样式及光标位置处理
* `优化` 在 _[`pr #98`](http://pr.autojs6.com/98)_ 基础上添加代码注释菜单项
* `优化` 适配 VSCode 插件 1.0.6
* `优化` UiObject#parent 方法增加级数参数支持 (参阅 项目文档 > [控件节点](https://docs.autojs6.com/#/uiObjectType))
* `依赖` 升级 Gradle 版本 8.2 -> 8.3-rc-1

# v6.3.2

###### 2023/07/06

* `新增` crypto 模块 (参阅 项目文档 > [密文](https://docs.autojs6.com/#/crypto)) _[`issue #70`](http://issues.autojs6.com/70)_
* `新增` UI 模式增加 textswitcher / viewswitcher / viewflipper / numberpicker / video / search 等控件
* `新增` 日志活动页面增加复制及导出日志等功能 _[`issue #76`](http://issues.autojs6.com/76)_
* `新增` 客户端模式增加 IP 地址历史记录功能
* `修复` 客户端模式自动连接或服务端模式自动开启后可能无法显示 IP 地址信息的问题
* `修复` 客户端模式及服务端模式连接后在切换语言或夜间模式时连接断开且无法再次连接的问题
* `修复` 客户端模式输入目标地址时无法使用自定义端口的问题
* `修复` 客户端模式输入目标地址时某些字符将导致 AutoJs6 崩溃的问题
* `修复` VSCode 插件远程命令可能出现解析失败造成命令无法响应的问题 (试修)
* `修复` Android 7.x 发现新版本时无法获取版本详情的问题
* `修复` images.pixel 无法获取无障碍服务截图的像素色值的问题 _[`issue #73`](http://issues.autojs6.com/73)_
* `修复` UI 模式 Android 原生控件 (大写字母开头) 无法使用预置控件属性的问题
* `修复` runtime.loadDex/loadJar 加载多个文件时仅第一个文件生效的问题 _[`issue #88`](http://issues.autojs6.com/88)_
* `修复` 部分设备安装应用后启动器仅显示文档图标的问题 (试修) _[`issue #85`](http://issues.autojs6.com/85)_
* `优化` 适配 VSCode 插件 1.0.5
* `优化` 支持 cheerio 模块 (Ref to [aiselp](https://github.com/aiselp/AutoX/commit/7176f5ad52d6904383024fb700bf19af75e22903)) _[`issue #65`](http://issues.autojs6.com/65)_
* `优化` JsWebSocket 实例支持使用 rebuild 方法重新重建实例并建立连接 _[`issue #69`](http://issues.autojs6.com/69)_
* `优化` base64 模块支持 number 数组及 Java 字节数组作为主要参数的编解码
* `优化` 增加对 JavaMail for Android 的支持 _[`issue #71`](http://issues.autojs6.com/71)_
* `优化` 获取版本更新信息时使用 Blob 数据类型以增强无代理网络环境适应性
* `优化` 客户端模式连接过程中在主页抽屉副标题显示目标 IP 地址
* `优化` 客户端模式输入目标地址时支持对不合法的输入进行提示
* `优化` 客户端模式支持使用软键盘回车键建立连接
* `优化` 服务端模式开启后保持常开状态 (除非手动关闭或应用进程结束) _[`issue #64`](http://issues.autojs6.com/64#issuecomment-1596990158)_
* `优化` 实现 AutoJs6 与 VSCode 插件的双向版本检测并提示异常检测结果 _[`issue #89`](http://issues.autojs6.com/89)_
* `优化` 增加短信数据读取权限 (android.permission.READ_SMS) (默认关闭)
* `优化` findMultiColors 方法内部实现 (by [LYS86](https://github.com/LYS86)) _[`pr #72`](http://pr.autojs6.com/72)_
* `优化` runtime.loadDex/loadJar/load 支持按目录级别加载或同时加载多个文件
* `依赖` 升级 LeakCanary 版本 2.11 -> 2.12
* `依赖` 升级 Android Analytics 版本 14.2.0 -> 14.3.0
* `依赖` 升级 Gradle 版本 8.2-milestone-1 -> 8.2

# v6.3.1

###### 2023/05/26

* `新增` 发布通知权限及主页抽屉开关 _[`issue #55`](http://issues.autojs6.com/55)_
* `新增` UI 模式支持简单的 Android 布局解析 (参阅 示例代码 > 布局 > 简单安卓布局)
* `新增` UI 模式增加 console / imagebutton / ratingbar / switch / textclock / togglebutton 等控件
* `新增` UI 模式控件的颜色色值支持 [OmniColor](https://docs.autojs6.com/#/omniTypes?id=omnicolor) 类型 (如 color="orange")
* `新增` UI 模式的控件完全支持 attr 方法设置控件属性 (如 ui.text.attr('color', 'blue'))
* `新增` UI 模式控件支持布尔类型属性值的缺省形式 (如 clickable="true" 可简写为 clickable 或 isClickable)
* `新增` button 控件支持 isColored 及 isBorderless 布尔类型属性
* `新增` console.resetGlobalLogConfig 方法用于重置全局日志配置
* `新增` web.newWebSocket 方法用于创建 Web Socket 实例 (参阅 项目文档 > [万维网](https://docs.autojs6.com/#/web?id=m-newwebsocket))
* `修复` 文件管理器的文件夹排序异常
* `修复` floaty 模块构建的浮动窗口无法调节样式及位置的问题 _[`issue #60`](http://issues.autojs6.com/60)_
* `修复` floaty 模块构建的浮动窗口与系统状态栏重叠的问题
* `修复` http.postMultipart 方法功能异常 _[`issue #56`](http://issues.autojs6.com/56)_
* `修复` Android 7.x 无法运行任何脚本的问题 _[`issue #61`](http://issues.autojs6.com/61)_
* `修复` sign.property 文件不存在时无法构建项目的问题
* `修复` 高版本系统 AutoJs6 置于后台时可能因无前台通知权限而崩溃的问题 (API >= 33)
* `修复` 调用 console.show 方法后日志窗口点击 FAB 按钮无法清空日志的问题
* `修复` 代码编辑器调试时出现的 prototype 空指针异常
* `修复` 代码编辑器运行脚本时在缓存文件夹运行临时脚本而非先保存再在原始位置运行以避免可能的脚本内容丢失问题
* `修复` 调整布局层次分析的层级色条宽度避免层级过多时控件名称无法显示的问题 _[`issue #46`](http://issues.autojs6.com/46)_
* `优化` 布局分析浮动窗口增加退出按钮以关闭窗口 _[`issue #63`](http://issues.autojs6.com/63)_
* `优化` 脚本绝对路径使用简称形式以缩减文本长度并增加可读性
* `优化` 将 Error 替换为 Exception 避免出现异常时 AutoJs6 应用崩溃
* `优化` 视图 (View) 绑定方式由 ButterKnife 迁移至 View Binding _[`issue #48`](http://issues.autojs6.com/48)_
* `优化` 服务端模式非正常关闭时将于 AutoJs6 启动时自动开启 _[`issue #64`](http://issues.autojs6.com/64)_
* `优化` 客户端模式非正常关闭时将于 AutoJs6 启动时按最近一次的历史地址自动连接
* `依赖` 升级 LeakCanary 版本 2.10 -> 2.11
* `依赖` 升级 Android Material 版本 1.8.0 -> 1.9.0
* `依赖` 升级 AndroidX WebKit 版本 1.6.1 -> 1.7.0
* `依赖` 升级 OkHttp3 版本 3.10.0 -> 5.0.0-alpha.9 -> 5.0.0-alpha.11
* `依赖` 升级 MLKit Text Recognition Chinese 版本 16.0.0-beta6 -> 16.0.0

# v6.3.0

###### 2023/04/29

* `新增` ocr 模块 (参阅 项目文档 > [光学字符识别](https://docs.autojs6.com/#/ocr)) _[`issue #8`](http://issues.autojs6.com/8)_
* `新增` notice 模块 (参阅 项目文档 > [消息通知](https://docs.autojs6.com/#/notice))
* `新增` s13n 模块 (参阅 项目文档 > [标准化](https://docs.autojs6.com/#/s13n))
* `新增` Color 模块 (参阅 项目文档 > [颜色类](https://docs.autojs6.com/#/colorType))
* `新增` colors.setColorPaint 方法, 用于设置 android.graphics.Paint 画笔颜色 (参阅 项目文档 > [颜色类](https://docs.autojs6.com/#/colorType?id=m-setpaintcolor))
* `新增` 前台时保持屏幕常亮功能及设置选项
* `新增` 额外的文档启动器 (launcher) 便于独立阅读应用文档 (支持在设置中隐藏或显示)
* `修复` colors.toString 方法功能异常
* `修复` app.openUrl 方法自动添加协议前缀功能异常
* `修复` app.viewFile/editFile 在参数对应文件不存在时的行为异常
* `修复` pickup 方法的回调函数无法被调用的问题
* `修复` 布局分析显示的控件信息 bounds 属性值负数符号被替换为逗号的问题
* `修复` bounds/boundsInside/boundsContains 选择器无法正常筛选狭义空矩形 (如边界倒置矩形) _[`issue #49`](http://issues.autojs6.com/49)_
* `修复` 更换主题或修改语言后点击或长按主页文档标签将导致应用崩溃的问题
* `修复` 代码编辑器双指缩放调节字体大小时可能出现抖动的问题
* `修复` 构建脚本中部分依赖源无法下载的问题 (已全部整合) _[`issue #40`](http://issues.autojs6.com/40)_
* `修复` Tasker 无法添加 AutoJs6 操作插件 (Action Plugin) 的问题 (试修) _[`issue #41`](http://issues.autojs6.com/41)_
* `修复` 高版本 JDK 编译项目时 ButterKnife 注解无法解析资源 ID 的问题 _[`issue #48`](http://issues.autojs6.com/48)_
* `修复` 无障碍服务较大概率出现服务异常的问题 (试修)
* `修复` images.medianBlur 的 size 参数使用方式与文档不符的问题
* `修复` engines 模块显示脚本全称时文件名与扩展名之间句点符号丢失的问题
* `修复` 加权 RGB 距离检测算法内部实现可能存在的计算失误 (试修)
* `修复` console 模块的浮动窗口相关方法无法在 show 方法之前使用的问题
* `修复` console.setSize 等方法可能无法生效的问题 _[`issue #50`](http://issues.autojs6.com/50)_
* `修复` colors.material 颜色空间的颜色常量赋值错误
* `修复` UI 模式的日期选择控件 minDate 及 maxDate 属性无法正确解析日期格式的问题
* `修复` 运行脚本后快速切换到主页 "任务" 标签页面将出现两个相同运行中任务的问题
* `修复` 文件管理页面从其他页面返回时页面状态可能被重置的问题 _[`issue #52`](http://issues.autojs6.com/52)_
* `修复` 文件管理页面排序状态与图标显示状态不符的问题
* `优化` 文件管理页面增加文件及文件夹修改时间显示
* `优化` 文件管理页面排序类型支持状态记忆
* `优化` README.md 添加项目编译构建小节与脚本开发辅助小节 _[`issue #33`](http://issues.autojs6.com/33)_
* `优化` images 模块相关方法的区域 (region) 选项参数支持更多传入方式 (参阅 项目文档 > [全能类型](https://docs.autojs6.com/#/omniTypes?id=omniregion))
* `优化` app.startActivity 页面简写参数增加 pref/homepage/docs/about 等形式的支持
* `优化` web 模块的全局方法挂载到模块本身以增强可用性 (参阅 项目文档 > [万维网](https://docs.autojs6.com/#/web))
* `优化` web.newInjectableWebView 方法内部默认实现部分常用的 WebView 设置选项
* `优化` colors 模块添加多种转换方法及工具方法并添加更多静态常量以及可直接作为参数的颜色名称
* `优化` console 模块添加多种控制台浮动窗口的样式配置方法并支持 build 构建器统一配置窗口样式
* `优化` 控制台浮动窗口支持拖动标题区域移动窗口位置
* `优化` 控制台浮动窗口支持脚本结束后自动延迟关闭
* `优化` 控制台浮动窗口及其 Activity 活动窗口支持双指缩放调整字体大小
* `优化` http 模块相关方法支持超时选项参数 (timeout)
* `优化` Gradle 构建脚本支持 JDK 版本主动降级 (fallback)
* `优化` Gradle 构建脚本支持根据平台类型及版本自动选择合适的构建工具版本 (程度有限)
* `依赖` 本地化 Auto.js APK Builder 版本 1.0.3
* `依赖` 本地化 MultiLevelListView 版本 1.1
* `依赖` 本地化 Settings Compat 版本 1.1.5
* `依赖` 本地化 Enhanced Floaty 版本 0.31
* `依赖` 附加 MLKit Text Recognition Chinese 版本 16.0.0-beta6
* `依赖` 升级 Gradle 版本 8.0-rc-1 -> 8.2-milestone-1
* `依赖` 升级 Android Material 版本 1.7.0 -> 1.8.0
* `依赖` 升级 Glide 版本 4.14.2 -> 4.15.1
* `依赖` 升级 Joda Time 版本 2.12.2 -> 2.12.5
* `依赖` 升级 Android Analytics 版本 14.0.0 -> 14.2.0
* `依赖` 升级 AndroidX WebKit 版本 1.5.0 -> 1.6.1
* `依赖` 升级 AndroidX Recyclerview 版本 1.2.1 -> 1.3.0
* `依赖` 升级 Zip4j 版本 2.11.2 -> 2.11.5
* `依赖` 升级 Junit Jupiter 版本 5.9.2 -> 5.9.3
* `依赖` 升级 AndroidX Annotation 版本 1.5.0 -> 1.6.0
* `依赖` 升级 Jackson DataBind 版本 2.14.1 -> 2.14.2
* `依赖` 升级 Desugar JDK Libs 版本 2.0.0 -> 2.0.3

# v6.2.0

###### 2023/01/21

* `新增` 重新设计及编写项目文档 (部分完成)
* `新增` 西/法/俄/阿/日/韩/英/繁中等多语言适配
* `新增` 工作路径设置选项增加路径选择/历史记录/默认值智能提示等功能
* `新增` 文件管理器支持任意目录的上一级跳转 (直至 "内部存储" 目录)
* `新增` 文件管理器支持将任意目录快捷设置为工作路径
* `新增` 版本更新忽略及管理已忽略更新功能
* `新增` 代码编辑器支持双指缩放调节字体大小
* `新增` idHex 选择器 (UiSelector#idHex) (参阅 项目文档 > [选择器](https://docs.autojs6.com/#/uiSelectorType))
* `新增` action 选择器 (UiSelector#action) (参阅 项目文档 > [选择器](https://docs.autojs6.com/#/uiSelectorType))
* `新增` Match 系列选择器 (UiSelector#xxxMatch) (参阅 项目文档 > [选择器](https://docs.autojs6.com/#/uiSelectorType))
* `新增` 拾取选择器 (UiSelector#pickup) (参阅 项目文档 > [选择器](https://docs.autojs6.com/#/uiSelectorType)) _[`issue #22`](http://issues.autojs6.com/22)_
* `新增` 控件探测 (UiObject#detect) (参阅 项目文档 > [控件节点](https://docs.autojs6.com/#/uiObjectType))
* `新增` 控件罗盘 (UiObject#compass) (参阅 项目文档 > [控件节点](https://docs.autojs6.com/#/uiObjectType)) _[`issue #23`](http://issues.autojs6.com/23)_
* `新增` 全局等待方法 wait (参阅 项目文档 > [全局对象](https://docs.autojs6.com/#/global?id=m-wait))
* `新增` 全局缩放方法 cX/cY/cYx (参阅 项目文档 > [全局对象](https://docs.autojs6.com/#/global?id=m-wait))
* `新增` 全局 App 类型 (参阅 项目文档 > [应用枚举类](https://docs.autojs6.com/#/appType))
* `新增` i18n 模块 (基于 banana-i18n 的 JavaScript 多语言方案) (参阅 项目文档 > 国际化)
* `修复` 软件语言切换后可能导致的页面文字闪变及部分页面按钮功能异常
* `修复` 工作路径为一个项目时软件启动后不显示项目工具栏的问题
* `修复` 工作路径可能跟随软件语言切换而自动改变的问题 _[`issue #19`](http://issues.autojs6.com/19)_
* `修复` 定时任务启动延时显著 (试修) _[`issue #21`](http://issues.autojs6.com/21)_
* `修复` JavaScript 模块名被覆盖声明时导致存在依赖关系的内部模块无法正常使用的问题 _[`issue #29`](http://issues.autojs6.com/29)_
* `修复` 高版本安卓系统点击快速设置面板中相关图标后面板可能无法自动收起的问题 (试修) _[`issue #7`](http://issues.autojs6.com/7)_
* `修复` 高版本安卓系统可能出现部分页面与通知栏区域重叠的问题
* `修复` Android 10+ 无法正常运行有关设置画笔颜色的示例代码的问题
* `修复` 示例代码 "音乐管理器" 更正文件名为 "文件管理器" 并恢复正常功能
* `修复` 文件管理器下拉刷新时可能出现定位漂移的问题
* `修复` ui 模块作用域绑定错误导致部分基于 UI 的脚本无法访问组件属性的问题
* `修复` 录制脚本后的输入文件名对话框可能因外部区域点击导致已录制内容丢失的问题
* `修复` 文档中部分章节标题超出屏幕宽度时无法自动换行造成内容丢失的问题
* `修复` 文档中的示例代码区域无法正常左右滑动的问题
* `修复` 文档页面下拉刷新时表现异常且无法撤销刷新操作的问题 (试修)
* `修复` 应用初始安装后主页抽屉夜间模式开关联动失效的问题
* `修复` 系统夜间模式开启时应用启动后强制开启夜间模式的问题
* `修复` 夜间模式开启后已设置的主题色可能无法生效的问题
* `修复` 夜间模式下部分设置选项文字与背景色相同而无法辨识的问题
* `修复` 关于页面功能按钮文本长度过大导致文本显示不完全的问题
* `修复` 主页抽屉设置项标题长度过大导致文本与按钮重叠的问题
* `修复` 主页抽屉权限开关在提示消息对话框消失后可能出现状态未同步的问题
* `修复` Root 权限修改主页抽屉权限开关失败时未继续弹出 ADB 工具对话框的问题
* `修复` Root 权限显示指针位置在初次使用时提示无权限的问题
* `修复` 图标选择页面的图标元素排版异常
* `修复` 代码编辑器启动时可能因夜间模式设置导致闪屏的问题 (试修)
* `修复` 代码编辑器设置字体大小时可用最大值受限的问题
* `修复` 部分安卓系统脚本运行结束时日志中无法统计运行时长的问题
* `修复` 使用浮动按钮菜单关闭按钮后重启应用时浮动按钮依然显示的问题
* `修复` 布局层次分析时长按列表项可能导致弹出菜单溢出下方屏幕的问题
* `修复` Android 7.x 系统在夜间模式关闭时导航栏按钮难以辨识的问题
* `修复` http.post 等方法可能出现的请求未关闭异常
* `修复` colors.toString 方法在 Alpha 通道为 0 时其通道信息在结果中丢失的问题
* `优化` 重定向 Auto.js 4.x 版本的公有类以实现尽可能的向下兼容 (程度有限)
* `优化` 合并全部项目模块避免可能的循环引用等问题 (临时移除 inrt 模块)
* `优化` Gradle 构建配置从 Groovy 迁移到 KTS
* `优化` Rhino 异常消息增加多语言支持
* `优化` 主页抽屉权限开关仅在开启时弹出提示消息
* `优化` 主页抽屉布局紧贴于状态栏下方避免顶部颜色条的低兼容性
* `优化` 检查更新/下载更新/更新提示功能兼容 Android 7.x 系统
* `优化` 重新设计设置页面 (迁移至 AndroidX)
* `优化` 设置页面支持长按设置选项获取详细信息
* `优化` 夜间模式增加 "跟随系统" 设置选项 (Android 9+)
* `优化` 应用启动画面适配夜间模式
* `优化` 应用图标增加数字标识以提升多个开源版本共存用户的使用体验
* `优化` 主题色增加更多 Material Design Color (材料设计颜色) 选项
* `优化` 文件管理器/任务面板等列表项图标适当轻量化并适配主题色
* `优化` 主页搜索框的提示文本颜色适配夜间模式
* `优化` 对话框/文本/FAB/AppBar/列表项等部件适配夜间模式
* `优化` 文档/设置/关于/主题色/布局分析等页面及浮动按钮菜单适配夜间模式
* `优化` 页面布局尽可能兼容 RTL (Right-To-Left) 布局
* `优化` 关于页面增加图标动画效果
* `优化` 关于页面版权声明文本自动更新年份信息
* `优化` 应用初始安装后自动决定并设置合适的工作目录
* `优化` 禁用文档页面双指缩放功能避免文档内容显示异常
* `优化` 任务面板列表项按相对路径简化显示任务的名称及路径
* `优化` 代码编辑器按钮文本适当缩写避免文本内容溢出
* `优化` 代码编辑器设置字体大小支持恢复默认值
* `优化` 提升浮动按钮点击响应速度
* `优化` 点击浮动按钮布局分析按钮直接进行布局范围分析
* `优化` 布局分析主题自适应 (浮动窗口跟随应用主题, 快速设置面板跟随系统主题)
* `优化` 布局控件信息列表按可能的使用频率重新排序
* `优化` 布局控件信息点击复制时根据选择器类型自动优化输出格式
* `优化` 使用浮动窗口选择文件时按返回键可返回至上级目录而非直接关闭窗口
* `优化` 客户端模式连接计算机输入地址时支持数字有效性检测及点分符号自动转换
* `优化` 客户端及服务端建立连接后在主页抽屉显示对应设备的 IP 地址
* `优化` 部分全局对象及内置模块增加覆写保护 (参阅 项目文档 > 全局对象 > [覆写保护](https://docs.autojs6.com/#/global?id=%e8%a6%86%e5%86%99%e4%bf%9d%e6%8a%a4))
* `优化` importClass 和 importPackage 支持字符串参数及不定长参数
* `优化` ui.run 支持出现异常时打印栈追踪信息
* `优化` ui.R 及 auto.R 可便捷获取 AutoJs6 的资源 ID
* `优化` app 模块中与操作应用相关的方法支持 App 类型参数及应用别名参数
* `优化` dialogs 模块中与异步回调相关的方法支持省略预填参数
* `优化` app.startActivity 等支持 url 选项参数 (参阅 示例代码 > 应用 > 意图)
* `优化` device 模块获取 IMEI 或硬件序列号失败时返回 null 而非抛出异常
* `优化` 提升 console.show 显示的日志浮动窗口文字亮度以增强内容辨识度
* `优化` ImageWrapper#saveTo 支持相对路径保存图像文件
* `优化` 重新设计 colors 全局对象并增加 HSV / HSL 等色彩模式支持 (参阅 项目文档 > [颜色](https://docs.autojs6.com/#/color))
* `依赖` 本地化 Android Job 版本 1.4.3
* `依赖` 本地化 Android Plugin Client SDK For Locale 版本 9.0.0
* `依赖` 本地化 GitHub API 版本 1.306
* `依赖` 升级 Android SDK 版本 32 -> 33
* `依赖` 附加 JCIP Annotations 版本 1.0
* `依赖` 附加 AndroidX WebKit 版本 1.5.0
* `依赖` 附加 Commons IO 版本 2.8.0
* `依赖` 附加 Desugar JDK Libs 版本 2.0.0
* `依赖` 附加 Jackson DataBind 版本 2.13.3
* `依赖` 附加 Jaredrummler Android Device Names 版本 2.1.0
* `依赖` 附加 Jaredrummler Animated SVG View 版本 1.0.6
* `依赖` 替换 Jrummyapps ColorPicker 版本 2.1.7 为 Jaredrummler ColorPicker 版本 1.1.0
* `依赖` 升级 Gradle 版本 7.5-rc-1 -> 8.0-rc-1
* `依赖` 升级 Gradle 构建工具版本 7.4.0-alpha02 -> 8.0.0-alpha09
* `依赖` 升级 Kotlin Gradle 插件版本 1.6.10 -> 1.8.0-RC2
* `依赖` 升级 Android Material 版本 1.6.0 -> 1.7.0
* `依赖` 升级 AndroidX Annotation 版本 1.3.0 -> 1.5.0
* `依赖` 升级 AndroidX AppCompat 版本 1.4.1 -> 1.4.2
* `依赖` 升级 Android Analytics 版本 13.3.0 -> 14.0.0
* `依赖` 升级 Gson 版本 2.9.0 -> 2.10
* `依赖` 升级 Joda Time 版本 2.10.14 -> 2.12.1
* `依赖` 升级 Kotlinx Coroutines 版本 1.6.1-native-mt -> 1.6.1
* `依赖` 升级 OkHttp3 版本 3.10.0 -> 5.0.0-alpha.7 -> 5.0.0-alpha.9
* `依赖` 升级 Zip4j 版本 2.10.0 -> 2.11.2
* `依赖` 升级 Glide 版本 4.13.2 -> 4.14.2
* `依赖` 升级 Junit Jupiter 版本 5.9.0 -> 5.9.1

# v6.1.1

###### 2022/05/31

* `新增` 检查更新/下载更新/更新提示功能 (参阅 设置页面) (暂不支持 Android 7.x 系统)
* `修复` 应用在 Android 10 系统无法读写外部存储的问题 _[`issue #17`](http://issues.autojs6.com/17)_
* `修复` 编辑器页面长按时可能导致应用崩溃的问题 _[`issue #18`](http://issues.autojs6.com/18)_
* `修复` 编辑器页面长按菜单 "删除行" 和 "复制行" 功能无效的问题
* `修复` 编辑器页面选项菜单中 "粘贴" 功能缺失的问题
* `优化` 部分异常消息字符串资源化 (en / zh)
* `优化` 调整内容未保存对话框的按钮布局并增加颜色区分
* `依赖` 附加 github-api 版本 1.306
* `依赖` 替换 retrofit2-rxjava2-adapter 版本 1.0.0 为 adapter-rxjava2 版本 2.9.0

# v6.1.0

###### 2022/05/26 - 包名变更, 谨慎升级

* `提示` 修改应用包名为 org.autojs.autojs6 避免与开源 Auto.js 应用包名冲突
* `新增` 主页抽屉增加 "投影媒体权限" 开关 (Root / ADB 方式) (开关状态检测为实验性)
* `新增` 文件浏览器支持显示隐藏文件和文件夹 (参阅 设置页面)
* `新增` 强制 Root 检查功能 (参阅 设置页面 及 示例代码)
* `新增` autojs 模块 (参阅 示例代码 > AutoJs6)
* `新增` tasks 模块 (参阅 示例代码 > 任务)
* `新增` console.launch() 方法启动日志活动页面
* `新增` util.morseCode 工具 (参阅 示例代码 > 工具 > 摩斯电码)
* `新增` util.versionCodes 工具 (参阅 示例代码 > 工具 > 安卓版本信息查询)
* `新增` util.getClass() 等方法 (参阅 示例代码 > 工具 > 获取类与类名)
* `新增` timers.setIntervalExt() 方法 (参阅 示例代码 > 定时器 > 条件周期执行)
* `新增` colors.toInt() / rgba() 等方法 (参阅 示例代码 > 图像与颜色 > 基本颜色转换)
* `新增` automator.isServiceRunning() / ensureService() 方法
* `新增` automator.lockScreen() 等方法 (参阅 示例代码 > 无障碍服务 > Android 9 新增)
* `新增` automator.headsethook() 等方法 (参阅 示例代码 > 无障碍服务 > Android 11 新增)
* `新增` automator.captureScreen() 方法 (参阅 示例代码 > 无障碍服务 > 获取屏幕截图)
* `新增` dialogs.build 方法支持 animation, linkify 等选项参数 (参阅 示例代码 > 对话框 > 个性化对话框)
* `修复` dialogs.build 方法 inputHint, itemsSelectedIndex 等选项参数功能异常
* `修复` JsDialog#on('multi_choice') 回调参数功能异常
* `修复` UiObject#parent().indexInParent() 总是返回 -1 的问题 _[`issue #16`](http://issues.autojs6.com/16)_
* `修复` Promise.resolve() 返回的 Thenable 在临近脚本结束时可能不被调用的问题
* `修复` 包名或类名中可能的拼写失误 (boardcast -> broadcast / auojs -> autojs)
* `修复` images.requestScreenCapture() 在高版本安卓系统可能导致应用崩溃的问题 (API >= 31)
* `修复` images.requestScreenCapture() 多个脚本实例同时申请可能导致应用崩溃的问题
* `修复` 调用 new RootAutomator() 可能出现的假死问题
* `优化` RootAutomator 在无 Root 权限时将无法实例化
* `优化` 重新设计 "关于应用与开发者" 页面
* `优化` 重构全部内置 JavaScript 模块
* `优化` 重构全部 Gradle 构建脚本并增加公共配置脚本 (config.gradle)
* `优化` Gradle 构建工具支持版本号自动管理及构建文件自动命名
* `优化` Gradle 构建工具增加 task 支持附加 CRC32 摘要到构建文件 (appendDigestToReleasedFiles)
* `优化` shell() 调用时将异常写入返回结果而非直接将异常抛出 (无需 try/catch)
* `优化` 使用 Rhino 内置的 JSON 替代原 json2 模块
* `优化` auto.waitFor() 支持超时参数
* `优化` threads.start() 支持箭头函数参数
* `优化` console.trace() 支持按日志等级参数 (参阅 示例代码 > 控制台 > 打印调用栈)
* `优化` device.vibrate() 支持模式振动及摩斯电码振动 (参阅 示例代码 > 设备 > 模式振动 / 摩斯电码振动)
* `优化` 外部存储读写权限适配高版本安卓系统 (API >= 30)
* `优化` 控制台字体采用 Material Color 增强普通及夜间主题下的字体可读性
* `优化` 保存 ImageWrapper 所有实例弱引用并在脚本结束时自动回收 (实验性)
* `依赖` 附加 CircleImageView 版本 3.1.0
* `依赖` 升级 Android Analytics 版本 13.1.0 -> 13.3.0
* `依赖` 升级 Gradle 构建工具版本 7.3.0-alpha06 -> 7.4.0-alpha02
* `依赖` 升级 Android Job 版本 1.4.2 -> 1.4.3
* `依赖` 升级 Android Material 版本 1.5.0 -> 1.6.0
* `依赖` 升级 CrashReport 版本 2.6.6 -> 4.0.4
* `依赖` 升级 Glide 版本 4.13.1 -> 4.13.2
* `依赖` 升级 Joda Time 版本 2.10.13 -> 2.10.14
* `依赖` 升级 Kotlin Gradle 插件版本 1.6.10 -> 1.6.21
* `依赖` 升级 Kotlinx Coroutines 版本 1.6.0 -> 1.6.1-native-mt
* `依赖` 升级 LeakCanary 版本 2.8.1 -> 2.9.1
* `依赖` 升级 OkHttp3 版本 5.0.0-alpha.6 -> 5.0.0-alpha.7
* `依赖` 升级 Rhino 引擎版本 1.7.14 -> 1.7.15-SNAPSHOT
* `依赖` 升级 Zip4j 版本 2.9.1 -> 2.10.0
* `依赖` 移除 Groovy JSON 版本 3.0.8
* `依赖` 移除 Kotlin Stdlib JDK7 版本 1.6.21

# v6.0.3

###### 2022/03/19

* `新增` 多语言切换功能 (尚不完善)
* `新增` recorder 模块 (参阅 示例代码 > 计时器)
* `新增` 使用 "修改安全设置权限" 自动启用无障碍服务及开关设置
* `修复` 点击快速设置面板中相关图标后面板未自动收起的问题 (试修) _[`issue #7`](http://issues.autojs6.com/7)_
* `修复` toast 使用强制显示参数时可能导致 AutoJs6 崩溃的问题
* `修复` Socket 传输数据头部信息不完整时可能导致 AutoJs6 崩溃的问题
* `优化` 启动或重启 AutoJs6 时根据选项设置自动开启无障碍服务
* `优化` 开启浮动按钮开关时尝试自动开启无障碍服务
* `优化` 所有资源文件补全元素对应的英文翻译
* `优化` 微调主页抽屉布局 减小项目排列间距
* `优化` 主页抽屉增加前台服务状态开关的同步
* `优化` 主页抽屉展开时立即按需同步开关状态
* `优化` 显示指针位置增加状态检测及结果提示
* `优化` 支持 64 位操作系统 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `优化` 浮动按钮初始化时同时应用透明度设置 (无需点击后再应用透明度)
* `优化` 重置文件内容时增加是否为示例代码文件的检测并增加结果提示
* `优化` 转移打包插件下载地址 GitHub -> JsDelivr
* `依赖` 附加 Zeugma Solutions LocaleHelper 版本 1.5.1
* `依赖` 降级 Android Material 版本 1.6.0-alpha02 -> 1.5.0
* `依赖` 升级 Android SDK 版本 31 -> 32
* `依赖` 升级 Kotlinx Coroutines 版本 1.6.0-native-mt -> 1.6.0
* `依赖` 升级 OpenCV 版本 3.4.3 -> 4.5.4 -> 4.5.5 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `依赖` 升级 OkHttp3 版本 3.10.0 -> 5.0.0-alpha.4 -> 5.0.0-alpha.6
* `依赖` 升级 Gradle 构建工具版本 7.2.0-beta01 -> 7.3.0-alpha06
* `依赖` 升级 Auto.js-ApkBuilder 版本 1.0.1 -> 1.0.3
* `依赖` 升级 Glide Compiler 版本 4.12.0 -> 4.13.1
* `依赖` 升级 Gradle 发行版本 7.4-rc-2 -> 7.4.1
* `依赖` 升级 Gson 版本 2.8.9 -> 2.9.0

# v6.0.2

###### 2022/02/05

* `新增` images.bilateralFilter() 双边滤波图像处理方法
* `修复` 多次调用 toast 只生效最后一次调用的问题
* `修复` toast.dismiss() 可能无效的问题
* `修复` 客户端模式及服务端模式开关可能无法正常工作的问题
* `修复` 客户端模式及服务端模式开关状态不能正常刷新的问题
* `修复` Android 7.x 解析 UI 模式 text 元素异常 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`issue #4`](http://issues.autojs6.com/4)_ _[`issue #9`](http://issues.autojs6.com/9)_
* `优化` 忽略 sleep() 的 ScriptInterruptedException 异常
* `依赖` 附加 AndroidX AppCompat (Legacy) 版本 1.0.2
* `依赖` 升级 AndroidX AppCompat 版本 1.4.0 -> 1.4.1
* `依赖` 升级 AndroidX Preference 版本 1.1.1 -> 1.2.0
* `依赖` 升级 Rhino 引擎版本 1.7.14-SNAPSHOT -> 1.7.14
* `依赖` 升级 OkHttp3 版本 3.10.0 -> 5.0.0-alpha.3 -> 5.0.0-alpha.4
* `依赖` 升级 Android Material 版本 1.6.0-alpha01 -> 1.6.0-alpha02
* `依赖` 升级 Gradle 构建工具版本 7.2.0-alpha06 -> 7.2.0-beta01
* `依赖` 升级 Gradle 发行版本 7.3.3 -> 7.4-rc-2

# v6.0.1

###### 2022/01/01

* `新增` 连接 VSCode 插件支持客户端 (LAN) 及服务端 (LAN/ADB) 方式 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` base64 模块 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` 增加 isInteger/isNullish/isObject/isPrimitive/isReference 全局方法
* `新增` 增加 polyfill (Object.getOwnPropertyDescriptors)
* `新增` 增加 polyfill (Array.prototype.flat)
* `优化` 扩展 global.sleep 支持 随机范围/负数兼容
* `优化` 扩展 global.toast 支持 时长控制/强制覆盖控制/dismiss
* `优化` 包名对象全局化 (okhttp3/androidx/de)
* `依赖` 升级 Android Material 版本 1.5.0-beta01 -> 1.6.0-alpha01
* `依赖` 升级 Gradle 构建工具版本 7.2.0-alpha04 -> 7.2.0-alpha06
* `依赖` 升级 Kotlinx Coroutines 版本 1.5.2-native-mt -> 1.6.0-native-mt
* `依赖` 升级 Kotlin Gradle 插件版本 1.6.0 -> 1.6.10
* `依赖` 升级 Gradle 发行版本 7.3 -> 7.3.3

# v6.0.0

###### 2021/12/01

* `新增` 主页抽屉底部增加重启应用按钮
* `新增` 主页抽屉增加忽略电池优化/显示在其他应用上层等开关
* `修复` 应用初始安装后部分区域主题颜色渲染异常的问题
* `修复` sign.property 文件不存在时无法构建项目的问题
* `修复` 定时任务面板一次性任务的月份存取错误
* `修复` 应用设置页面开关颜色不随主题变更的问题
* `修复` 无法识别打包插件及打包插件下载地址无效的问题
* `修复` 主页抽屉 "查看使用情况权限" 开关状态可能不同步的问题
* `修复` TemplateMatching.fastTemplateMatching 潜在的 Mat 内存泄漏问题
* `优化` 升级 Rhino 引擎版本 1.7.7.2 -> 1.7.13 -> 1.7.14-SNAPSHOT
* `优化` 升级 OpenCV 版本 3.4.3 -> 4.5.4
* `优化` ViewUtil.getStatusBarHeight 提升兼容性
* `优化` 主页抽屉移除用户登录相关模块并移除布局占位
* `优化` 主页移除社区及市场标签页面并优化布局对其方式
* `优化` 修改一些设置选项的默认开关状态
* `优化` 关于页面增加 SinceDate 并优化 Copyright 显示
* `优化` 升级 JSON 模块至 2017-06-12 版本并整合 cycle.js
* `优化` 移除 Activity 前置时的自动检查更新功能并移除检查更新相关按钮
* `优化` AppOpsKt#isOpPermissionGranted 内部代码逻辑
* `优化` ResourceMonitor 使用 ReentrantLock 增强安全性 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `优化` 使用 Maven Central 等仓库替换 JCenter 仓库
* `优化` 抽离并移除重复的本地库文件
* `依赖` 本地化 CrashReport 版本 2.6.6
* `依赖` 本地化 MutableTheme 版本 1.0.0
* `依赖` 附加 AndroidX Preference 版本 1.1.1
* `依赖` 附加 SwipeRefreshLayout 版本 1.1.0
* `依赖` 升级 Android SDK 版本 28 -> 30 -> 31
* `依赖` 升级 Android Analytics 版本 7.0.0 -> 13.1.0
* `依赖` 升级 Android Annotations 版本 4.5.2 -> 4.8.0
* `依赖` 升级 Gradle 构建工具版本 3.2.1 -> 4.1.0 -> 7.0.3 -> 7.2.0-alpha04
* `依赖` 升级 Android Job 版本 1.2.6 -> 1.4.2
* `依赖` 升级 Android Material 版本 1.1.0-alpha01 -> 1.5.0-beta01
* `依赖` 升级 AndroidX MultiDex 版本 2.0.0 -> 2.0.1
* `依赖` 升级 Apache Commons Lang3 版本 3.6 -> 3.12.0
* `依赖` 升级 Appcompat 版本 1.0.2 -> 1.4.0
* `依赖` 升级 ButterKnife Gradle 插件版本 9.0.0-rc2 -> 10.2.1 -> 10.2.3
* `依赖` 升级 ColorPicker 版本 2.1.5 -> 2.1.7
* `依赖` 升级 Espresso Core 版本 3.1.1-alpha01 -> 3.5.0-alpha03
* `依赖` 升级 Eventbus 版本 3.0.0 -> 3.2.0
* `依赖` 升级 Glide Compiler 版本 4.8.0 -> 4.12.0
* `依赖` 升级 Gradle Build Tool 版本 29.0.2 -> 30.0.2
* `依赖` 升级 Gradle 发行版本 4.10.2 -> 6.5 -> 7.0.2 -> 7.3
* `依赖` 升级 Groovy-Json 插件版本 3.0.7 -> 3.0.8
* `依赖` 升级 Gson 版本 2.8.2 -> 2.8.9
* `依赖` 升级 Java 版本 8 -> 11 -> 16
* `依赖` 升级 Joda Time 版本 2.9.9 -> 2.10.13
* `依赖` 升级 Junit 版本 4.12 -> 4.13.2
* `依赖` 升级 Kotlin Gradle 插件版本 1.3.10 -> 1.4.10 -> 1.6.0
* `依赖` 升级 Kotlinx Coroutines 版本 1.0.1 -> 1.5.2-native-mt
* `依赖` 升级 LeakCanary 版本 1.6.1 -> 2.7
* `依赖` 升级 LicensesDialog 版本 1.8.1 -> 2.2.0
* `依赖` 升级 Material Dialogs 版本 0.9.2.3 -> 0.9.6.0
* `依赖` 升级 OkHttp3 版本 3.10.0 -> 5.0.0-alpha.2 -> 5.0.0-alpha.3
* `依赖` 升级 Reactivex RxJava2 RxAndroid 版本 2.0.1 -> 2.1.1
* `依赖` 升级 Reactivex RxJava2 版本 2.1.2 -> 2.2.21
* `依赖` 升级 Retrofit2 Converter Gson 版本 2.3.0 -> 2.9.0
* `依赖` 升级 Retrofit2 Retrofit 版本 2.3.0 -> 2.9.0
* `依赖` 升级 Zip4j 版本 1.3.2 -> 2.9.1