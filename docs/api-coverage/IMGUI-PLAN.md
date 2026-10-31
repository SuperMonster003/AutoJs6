# imgui 的 Node / Bun 形态与文档补齐方案 (P5.2f-2 盘点)

子会话 api-cov-p52f2-survey, 2026-10-31. 全程静态: 没有改宿主 / 插件 / Node / Bun 代码, 没有改矩阵基线, 没有用真机, 没有做新测量. 待决 Q213-Q217 见第 5 节, 拍板结果见第 6 节.

## 0. 盘点基准与卡片前提的出入

- 宿主 HEAD 94057fe64 (ImguiNames 最后改于 5102dbe42): `FUNCTIONS` 282 + `ALIASES` 248 + `CONSTANTS` 42 = 572, 与 pending-baseline.json 的 imgui 572 行差集为空. 看板写的 573 含内部导出 `imgui.__pluginId` (Q123, 已是 not-portable).
- 插件 HEAD dc96dc3 (v1.16.0, tag b3ddd02). 名称取自该提交的 `imgui.d.ts` 与 `index.js`.

卡片写于 R5 进行中, 下列几处与实际不符, 本文以实际为准:

| 卡片的说法 | 实际 |
|---|---|
| R5 是未推送的本地提交, 要标出新增或改名的名称 | R5 已发布并推送 (v1.16.0, 索引 51.json). 新能力全是 Session 成员与 createWindow 选项, 没有新全局名, ImguiNames 未变. 在途的是 R6, 见 1.4 |
| 用 P3.3 / P3.4 的桥接延迟数据 | 这两批的报告与笔记里没有逐次调用的延迟数字. 唯一实测在 Node Roadmap M12.2, 见 2.2. Bun 没有实测 |
| 文档名称门禁已过 | 没有过, 是按 Q121 暂缓 (policy.json `deferredNameGate`). 矩阵 doc / d.ts / Ace 三列都是 0 / 573 |
| 用 imgui 看板 R2 的帧耗时 | R2 的 workTime 只测列表; 直接相关的是 R8 的脚本构建时间 (`docs/FRAME-LOOP.md`), 两者都引用 |

## 1. 572 行盘点

### 1.1 按族

分族由脚本逐名归类并断言不重不漏, 合计正好 572. "每帧" 的判定来自 `ImGuiBridge.cpp` 的 `RequireFrame` / `RequireContext` 守卫 (253 个 JNI 函数: 帧内 169, 需上下文 34, 无守卫 50), JS 包装函数按其语义归入. 卡片列的 "字体 / DPI" 分散在样式族 (pushFont / setWindowFontScale), 信息族 (dp / px), 常量 (density) 与 Session 成员 (fontScale).

| 族 | 行 (函数 + 别名) | 带回调的函数 | 每帧调用 | 返回句柄 |
|---|---:|---:|---|---|
| 窗口与生命周期 (createWindow, launch*, closeWindow, addButtonEx, setTouchable 等) | 26 (13 + 13) | 5 | 否, 但渲染回调本身每帧运行 | Session 10, FloatingButton 1, Component 1 |
| 高层组件与对话框 (logWindow, toast, alert, confirm, statsOverlay, dataTable 等) | 16 (8 + 8) | 0 | 否, 组件由插件在帧内绘制 | LogWindow, Toast, StatsOverlay, DataTable 各 1 |
| 帧内容器 (begin / end 对, table*, popup, menu, tree, listClipper) | 92 (46 + 46) | 1 | 是 | 无 (TableSortSpecs 为数据) |
| with* 作用域 | 25 (22 + 3) | 22 | 是 | 无 |
| 控件 | 122 (61 + 61) | 0 | 是 | 无 |
| 布局 (sameLine, separator, setNextWindow*, indent 等) | 24 (12 + 12) | 0 | 是 | 无 |
| 绘制列表与纹理 | 18 (9 + 9) | 0 | 是, createTexture / destroyTexture 除外 | DrawList 3, Texture 1 |
| 拖放 | 14 (7 + 7) | 0 | 是 | 无 (DragDropPayload 为数据) |
| 样式 / 主题 / 字体 / ID 栈 | 36 (18 + 18) | 0 | push / pop 类是; setTheme, setStyleColor 等 6 个函数否 | 无 |
| 输入与事件 | 45 (25 + 20) | 0 | 查询类是; 6 个注入函数 (addKeyEvent 等) 否 | Vec2 2 |
| 项目 / 窗口查询 | 67 (34 + 33) | 0 | 是 | Vec2 5 |
| 调试与统计窗口 | 14 (7 + 7) | 0 | 是 | 无 |
| 值引用 (bool, float, ref, floats 等) | 10 (9 + 1) | 0 | 否 | IndexedRef / ValueRef / NativeArray |
| 信息与工具 (version, colorU32, icon, dp, px, 剪贴板, 示例) | 21 (11 + 10) | 0 | 否 | BackendCapabilities 1 |
| 常量与枚举 (Col, Key, WindowFlags 等, 含 density) | 42 | 0 | 否 | - |
| 合计 | 572 | 28 | | |

### 1.2 按能否脱离帧

这张表决定方案 (c) 能拿到多少:

| 档 | 行 | 函数 | 说明 |
|---|---:|---:|---|
| 帧内专用 | 433 (76 %) | 227 | 必须在 newFrame 与 render 之间同步调用 |
| 会话期, 不限帧 | 66 (12 %) | 35 | 需要会话或上下文, 不进帧 |
| 无需会话 | 31 (5 %) | 20 | 本地可算或读插件信息 |
| 常量 | 42 (7 %) | - | 纯数据 |

### 1.3 返回的对象 (不在矩阵里)

imgui 的返回对象目前没有进矩阵, 成员合计约 228 个. PLUGIN-APIS.md 8.2 的 "约 85 个成员" 已过时 (R3-R5 增加了很多).

| 对象 | 成员 | 备注 |
|---|---:|---|
| Session | 70 | 含 capture, setFps, on / once / off |
| DrawList | 71 | 帧内专用 |
| FloatingButton | 24 | |
| DataTable | 20 | 继承 Component |
| LogWindow | 13 | 继承 Component |
| Component | 11 | |
| Texture | 8 | |
| StatsOverlay | 6 | 继承 Component |
| Toast | 5 | 继承 Component |

这些是插件 `index.js` 构造的 JS 对象, 而 returned_objects.py 只认 `kotlin-class` 与 `augment-prototype` 两种成员来源, 登记它们要新增一种来源 (Q217).

### 1.4 在途的 R6 (工作区未提交, 会话 local_3c4bb372)

R6 新增 11 行: beginMultiSelect / endMultiSelect (帧内容器), setNextItemSelectionUserData / isItemToggledSelection (查询), selectionStorage (值引用), 各带 PascalCase 别名, 外加常量组 MultiSelectFlags. 落地后 imgui 公开名称 583, 含 `__pluginId` 584 行. R7 的 Roadmap 条目没有新全局名的计划.

## 2. Node / Bun 形态方案

### 2.1 决定性约束

比延迟更关键的是下面三条, 它们与设备快慢无关:

1. **帧内调用必须同步返回.** `if (ui.button("确定")) {...}` 要当场得到布尔值. Node / Bun 的宿主调用一律返回 Promise (p3-3-design.md: "宿主调用一律返回 Promise (Rhino 同步)"), 所以每个控件都要 `await`, 渲染回调变成 async 函数, 插件示例与文档的写法全部不通用.
2. **帧有线程亲和性和一把锁.** `newFrame()` 取 native 上下文单锁, `render()` 要求在同一线程调用 (`ImGuiPluginConnection` 抛 "ImGui frame must be rendered on the thread that opened it"). 桥接后这把锁要跨过每次 await 一直持有, 宿主也要把一个会话的全部调用钉在同一线程上. Bun 现在是每请求一条连接, 插件侧最多 8 个并发, 宿主侧 `BunHostCallExecutor` 是线程池, 调用不固定在一个线程上, 需要新增这层; Node 的宿主派发线程没有逐一核对, 实施前要先确认.
3. **插件在 Rhino 里运行.** ImGui 是进程内 SDK 插件, 8536 行 `index.js` 由 `ImguiModule` 经 `ScriptRuntime` 的 `Plugins.load` 加载 (校验, 会话, 高层组件都在这里). Node / Bun 没有插件加载器, 桥接要么在宿主为每次执行起一个 Rhino 引擎托管它, 要么在别处重写这些逻辑.

### 2.2 性能依据

全部来自已有记录, 没有新测量.

| 数据 | 来源 | 数值 |
|---|---|---|
| Node 单次往返 (device.isScreenOn, 200 次串行) | Node Roadmap M12.2, BridgeLatencySmokeTest, 2026-09-08 | 小米 arm64 Debug P50 / P95 0.523 / 0.606 ms, 混淆 Release 0.296 / 0.345 ms, 16 KB x86_64 AVD 3.737 / 9.479 ms, Sony API 28 armv7 4.333 / 7.445 ms |
| Bun 单次往返 | 无实测 | 结构上不比 Node 快: 每请求一条 unix socket 连接, HTTP/1.1, 再经 Binder |
| Rhino 帧构建 (light / heavy 300 行 text) | 插件 FRAME-LOOP.md R8, 968e9f18, debug 宿主 | 6.8 / 11.3 ms (P50) |
| Rhino 单次调用 (推算) | (11.3 - 6.8) / 300 | 约 15 us, Node 往返约为它的 20-35 倍 |
| 列表 workTime | 看板 imgui Q12 (R2) | 968e9f18 空子窗口 5.7-6.2 ms, 100000 行 12.7-14.6 ms; 63751f63 4.88-4.92 ms |
| 示例的 `ui.` 调用数 (静态, 不是运行时计数) | 插件 examples/ | log-hud 17, data-table-demo 20, parameter-panel 33, theme-gallery 34, large-list 34, widget-overview 68 |

按 60 fps 的 16.7 ms 预算推算, 只算桥接, 不计脚本, 绘制与序列化, 所以是乐观上限:

| 设备 / 构建 | 每帧最多调用数 | 68 次 (widget-overview) | 300 次 (R8 heavy) |
|---|---:|---:|---:|
| 小米 arm64 Debug | 31 | 36 ms (28 fps) | 157 ms (6 fps) |
| 小米 arm64 Release | 56 | 20 ms (49 fps) | 89 ms (11 fps) |
| 16 KB x86_64 AVD | 4 | 254 ms (4 fps) | 1121 ms (1 fps) |
| Sony API 28 armv7 | 3 | 295 ms (3 fps) | 1300 ms (1 fps) |

结论: 只有最快的设备勉强能跑简单窗口, 旧设备每帧 3-4 个控件就超预算. 这组数字的局限: M12.2 是 2026-09 一种轻量调用的串行数据, 没有并发吞吐数据, 也不含 imgui 的参数序列化; 桥接窗口默认 32 路 (`maxPendingBridgeCalls`, 上限 128, 超出按先进先出排队), 理论上能流水化, 但控件顺序必须严格保持, 而且第 2.1 节第 1 条的同步返回问题不因此消失.

### 2.3 现成的逃生口 (静态核对, 未上真机)

`engines.execScript` 在 Node 与 Bun 都已可用 (附录 D D-14 决定不排除): Node 的宿主路由 `NodeBridgeEngineDispatchService.executeScript` 用 `StringScriptSource` 走宿主的正常脚本执行路径; Bun 开放同名方法, 需要 `engines.exec` 授权 (`BunAutomationBridge.checkEngines`). 所以 Node / Bun 脚本应该可以启动一段 Rhino 脚本来开 imgui 窗口, 帧回调在 Rhino 里同步运行, 不受上面的约束.

未核实的三点, 留给实施卡: 这段 Rhino 脚本确实能打开窗口 (需一台真机冒烟, Node 与 Bun 各一次); Bun 的 execScript 是否走同一条执行路径 (本次只读到授权检查, 没有读到派发); 它与 Node / Bun 主脚本怎样通信 (矩阵里没有 `events.broadcast` 行, 不能假定, 需要在实施时确定并写进文档). 这三点不成立时, (b) 仍然成立, 只是文档里的逃生口要改写.

`rhino.run` 不是这条路: 它是 JSON-only 的限时执行 (`NodeBridgeRhinoExecutionService`), 撑不起常驻窗口.

### 2.4 方案

工作量是估算. 校准依据: P3.3 约 145 行, 宿主 + Node 代码改动约 4800 行, 约每行 33 行, 1 个子会话. imgui 大量行是别名与常量, 可表驱动, 所以取每行 10-33 行.

| | (a) 全量桥接 | (b) 整体排除 + 逃生口 (推荐) | (c1) 混合: 非帧 API 桥接 | (c2) 混合: 帧内批量提交 |
|---|---|---|---|---|
| 内容 | 572 行 + 228 个对象成员全部桥接, 帧回调经事件桥, 每控件 await | `imgui.*` 一条规则, 三列 not-portable, 文档写明走 `engines.execScript` | 139 行 + 157 个对象成员桥接 (log / toast / 对话框 / dataTable / 悬浮按钮), 433 行与 DrawList 排除 | (c1) 之上, 一帧的调用录制后一次提交, 宿主回放 |
| Node / Bun 能做什么 | 全部, 写法改为 async | 经 Rhino 脚本使用全部 imgui | 无帧回调的 HUD 类窗口 | 全部, 但结果晚一帧 |
| 改动 | 宿主帧驱动服务 + 托管 Rhino, Node / Bun 门面; 约 6000-15000 行 | 手写约 30 行规则与测试, 约 30 行文档, 其余重生成 | 宿主托管 Rhino, 无回调的 createWindow 形态, Node / Bun 门面, 常量快照; 约 3000-7000 行 | (c1) + 插件录制回放: R8 原型 856 行只录 10 个函数, 这里要 227 个 |
| 子会话 | 6-9 | 1 个小卡 | 4-5 | 10 以上 |
| 性能 | 见 2.2, 旧设备不可用 | 与 Rhino 相同 | 无每帧开销 | 约一次往返每帧, 未测; 输入结果晚一帧 |
| 主要风险 | 跨 await 持锁, 钉线程, 脚本异常时帧悬空; 每个 R 阶段都要同步 | 无帧内 API 的 Node 写法; 逃生口未上真机 | `createWindow` 现在要求函数参数, 要改插件; 只覆盖 24 % 的行 | Q6 (2026-10-30) 已否决 replay 模式: 每个 API 要实现并测试两遍; 回放时 ID 与分支可能和录制时不一致 |
| pending | 0, 但要先完成全部实施 | 0, 一个小卡即达成 | 0 | 0 |
| 分母 | 约 +228 x 3 格 | 不变 (对象是否登记见 Q217) | 约 +157 x 3 格 | 约 +228 x 3 格 |
| 能否再改 | - | 能: 删规则即回到 pending, 未做的工作不浪费 | 难: 公开 API 要长期维护 | 难 |

**推荐 (b).** 理由: 第 2.1 节的三条约束与设备无关, (a) 的成本买到的是旧设备不可用, 写法也和文档不同的 API; (c1) 只覆盖 24 % 的行, 而同样的需求用逃生口就能满足; (c2) 等于重开 Q6. (b) 让 pending 立刻清零, P6.3 终态报告不再被 R7 卡住, 之后新增的 imgui 名称由规则自动覆盖, imgui 子会话不用再按 Q14 登记基线. 而且 (b) 可逆, 以后真有 Node / Bun 使用 imgui 的需求, 删规则再开专题即可, 现在不做的工作不会浪费.

(a) 中考虑过但没有列入的路线: 在 Node / Bun 进程内直接加载 ImGui native 库并把 Surface 经 Binder 传过去. 这要给每个运行时做原生插件接入, 窗口权限也在宿主一侧, 比 (a) 更重, 不展开.

## 3. 文档 / d.ts / Ace 现状

| 面 | 现状 | 缺口 | 补齐工作量 |
|---|---|---|---|
| 文档站 api/imgui.md | 只有运行时表与一句 57 字符的占位话, 未挂侧栏 / 目录 (Q125) | 正文覆盖 0 / 572; 需 282 个函数 + 42 个常量 = 324 个标题, 248 个别名可入表 (名称门禁只把 `missing` 判失败, 表格提及算 `mention`, 能过门禁) | 约 5000 行 (每标题约 15 行), 1 个子会话 |
| 文档站对象页 | 无 | Session / DrawList 等 228 个成员 (Q217 选登记时才进门禁) | 约 2500 行, 1 个子会话 (可与上一行合卡, 约 1.5 卡) |
| 侧栏 / 目录 / changelog | 未挂 | Q125 约定补正文时挂; changelog 只写 zh-Hans | 随文档卡 |
| TS 声明仓库 | 完全没有 imgui, 也没有引用插件 d.ts | 新建 `declarations/autojs6/aj6-int-imgui.d.ts`, 由插件 `imgui.d.ts` (R6 后约 1860 行) 改写, capture 的返回类型接到宿主 ImageWrapper. 方案 (b) 下 autojs6-node / autojs6-bun 不需要声明 | 0.5 个子会话 |
| Ace 捆绑声明 | 完全没有 imgui | 同步 aj6-int-imgui.d.ts, 跑 `:app:generateAutoJs6LspDeclarations`, 查询清单约 +583 | 与 TS 同卡 |
| Offline Docs | imgui.html 与文档站 blob 相同 (46df5d20), 已同步 | 无, 随文档生成脚本更新 | 无额外工作 |
| 门禁 | policy.json `deferredNameGate.imgui` 暂缓 | 三面补齐后该项会被判 stale, 必须删除 | 1 行, 随最后一张卡 |

插件侧素材: `imgui.d.ts` 现有 333 行注释, R7.1 要求每个公开成员至少一行 JSDoc 并把 39 处 `unknown` 清零, R7.3 产出给 P5.2f 的移交清单, 所以文档与声明都应等 R7. README (25 KB), LIMITS.md, THREADING.md, MULTI-WINDOW.md 可作正文来源, ALIASES.md 由 `tools/generate_aliases_doc.py` 生成, 可直接转成别名表.

PLUGIN-APIS.md 8.2 的旧估算 (约 349 个标题, 194 个别名, 约 85 个成员) 已过时, 以本节为准.

## 4. 建议的实施卡

按第 6 节的拍板结果在那里定稿. 依赖关系:

- 排除规则卡: 不依赖 R7 的 API 稳定, 只要等正在改基线的收尾卡与 R6 提交完成.
- 文档卡 / 声明卡: 依赖 R7 (JSDoc, unknown 清零, 移交清单), 互相独立可并行.
- 收口 (删 `deferredNameGate.imgui`, 勾 P5.2): 依赖文档卡与声明卡.

## 5. 待决事项

| 编号 | 问题 | 选项 | 影响 | 改选返工 |
|---|---|---|---|---|
| Q213 | imgui 572 行在 Node / Bun 的终态 | (b) 整体排除 + 文档写逃生口 (推荐) / (c1) 非帧 API 桥接 / (a) 全量桥接 / (c2) 批量提交 | P5.2f-2, 矩阵三列, P6.3 | 见 2.4 |
| Q214 | 排除规则的理由词 | (a) `rhino-only`, 文档表显示 "仅限 Rhino 引擎", 与 D-14 的 plugins.load 一致, 不加新词 (推荐) / (b) `sync-only`, 显示 "依赖同步阻塞", 更贴根因, 但读者看不出该怎么办, 该词现在没有规则在用 / (c) 新增理由词 `immediate-mode` | 附录 D 新组, runtime-policy, 文档表 | 小: 改一个词后重生成; (c) 另改 REASONS 与文档生成器 |
| Q215 | 排除规则何时落地 | (a) 收尾卡与 R6 提交后立刻发小卡, 不等 R7 (推荐) / (b) 与文档卡一起等 R7 | P6.3, imgui R6 / R7 的 Q14 登记 | 小 |
| Q216 | 文档与声明的实施批次 | (a) R7 发布后两张卡并行: 文档卡 + 声明卡 (推荐) / (b) 一张卡做完 / (c) 声明卡现在先做, R7 后再改一遍 | P5.2f-2 | (c) R7.1 大改 d.ts, 声明要返工一次 |
| Q217 | 228 个对象成员是否进矩阵分母 | (a) 登记: 新增成员来源, 文档与 d.ts 都进门禁, Rhino 面有覆盖保证 (推荐) / (b) 不登记: 只写 d.ts 与文档, 矩阵只算模块根 572 行 | P5.2f-2 工具, 文档量 | (a) 工具约 50-80 行加测试, 文档约 +2500 行; (b) 无 |

## 6. 拍板结果

- Q213: 已决 (2026-11-01 00:2x), 维护者看过四个方案的代码写法对比后选 (b) 整体排除, 文档写 `engines.execScript` 逃生口, 推荐项.
- Q214 - Q217: 未决. 第二轮选择题维护者没有作答, 未按推荐项先行. 第 4 节的实施卡划分要等这四项定下来再定稿.
