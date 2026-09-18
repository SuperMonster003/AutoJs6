# P6.6 资源: PNG 无损转 WebP 与 lint 未用资源清理 (2026-09-18)

对应 [apk-size-reduction-roadmap.md](../apk-size-reduction-roadmap.md) P6.6 (决策 D15), 体积报告见 [apk-size-p6-6-20260918.md](apk-size-p6-6-20260918.md), 逐文件体积差见 [apk-size-p6-6-webp-files-20260918.csv](apk-size-p6-6-webp-files-20260918.csv).

## 结论

- `app/src/main/res/` 中 1264 个 PNG 位图无损转换为 WebP (源文件 1,790,772 -> 886,822 字节, -903,950 字节), 62 个 `mipmap*/` 启动图标与 1 个 jpg 不动, 无九宫格; 每个文件解码回 RGBA 与源 PNG 逐像素比对一致后才删除 PNG, 资源名不变 (`foo.png` -> `foo.webp`).
- lint `UnusedResources` 候选逐项用 `git grep -w` 复核后只删除应用内部遗留项: 10 个文件 (4 布局, 3 菜单, 1 xml, 2 mipmap PNG) 与 8 个值资源 (5 dimen, 2 string-array, 1 plurals x 11 语言); 脚本 UI 可按名字引用的目录 (drawable 图标集, AOSP anim / interpolator 集, string / color / style) 与库资源覆盖项 (bool) 全部保留.
- universal 32,067,627 -> 31,595,920 字节 (-471,707 字节 = -0.45 MiB; 对基线 41,146,055 为 -9,550,135 字节 = -9.11 MiB, -23.2%); `res/` 压缩 2,584,470 -> 2,092,039 字节 (-492,431), 其中位图 (png + webp) 1,910,765 -> 1,420,198 字节.
- 证据等级 E3: AVD API 24 与 Redmi 22120RN86C API 33 上按名字解码 / 缩放 / 夜间变体 / 脚本 UI 引用验证.

## (a) `UnusedResources` 逐项确认

lint 报告取自 `app/build/reports/lint-results-appDebug.xml` (2026-09-14 19:33 生成, `UnusedResources` 1595 项). 2026-09-18 12:32 重新执行 `:app:lintAppDebug` 约 60 分钟后 Gradle 守护进程消失 (`Gradle build daemon disappeared unexpectedly`, pid 54908, `-Xmx4g`), 未生成新报告; 之后并行会话未提交的 `core/plugin/mail` 源码使 `compileAppDebugKotlin` 失败, 当日无法再跑 lint. 因此改为: 以旧报告为候选集, 用 `build/tmp-size/p6_6_candidates.py` 对当前树逐项 `git grep -l -w -F <name>` (排除 docs / changelog), 加上人工判断资源类型是否可被脚本按名字引用. 自 09-14 以来 `res/` 只有 strings / colors / bools / 少量 layout 与 xml 改动, 均不在删除清单内.

| 类型 | 候选 | 处理 | 依据 |
| --- | ---: | --- | --- |
| drawable | 1004 (932 在 `drawable-xhdpi`) | 保留 | 脚本 UI `src="@drawable/x"` 经 `Drawables.java` `getIdentifier(x, "drawable")` 按名字解析, `R.drawable` 反射亦可达; 该目录即脚本图标集 |
| string / color / style | 261 / 48 / 4 | 保留 | `Strings.java` / `Colors.java` / `Res.java` 按名字解析; strings 还与其它会话的未提交修改重叠 |
| anim / interpolator | 183 / 51 | 保留 | `Strings.java:51` 把 `@anim/x` 经 `getIdentifier(x, "anim")` 解析 (文本切换器等动画属性), 203 个 anim 为 6.3.2 (e3fe98e5a) 整体引入的 AOSP 动画集, interpolator 只被这些 anim 引用; 属脚本可按名字使用的目录, 是否裁剪见附录 A 的新问题 |
| integer / dimen (AOSP 配置项) | 17 / 5 | 保留 | 与上面动画集同源 (`config_screen_rotation_*`, `button_pressed_*`, `popup_*_animation_*` 等), 随动画集一并决定 |
| bool | 3 | 保留 | `leak_canary_add_*` 与 `config_materialPreferenceIconSpaceReserved` 是对库资源的覆盖, 库内引用, lint 看不到 |
| raw `text_tool` | 1 | 保留 | 示例 `布局/播放视频文件.js` 用 `@raw/text_tool` |
| dimen `dimen_25dp` | 1 | 保留 | 示例 `OCR/PaddleOCR (截图识别) [v6.7.0+].js` 用 `R.dimen.dimen_25dp` |
| layout | 4 | 删除 | `image_text`, `js_switch`, `divider`, `abc_list_menu_item_checkbox`: 全树无 `R.layout.` / `@layout/` 引用 (后者只出现在 2023 年遗留的 `app/resources.arsc`) |
| menu | 3 | 删除 | `menu_login`, `menuitem_js_sample`, `menu_js_sample` (仅 `JsMenuInflater.kt` 一行注释提及, 注释同步移除) |
| xml | 1 | 删除 | `data_extraction_rules` 为模板文件, 清单未引用 |
| mipmap | 2 | 删除 | `ic_launcher_adaptive_foreground` (仅 hdpi) 与 `ic_launcher_legacy` 无引用; 其余 62 个启动 / 快捷方式图标不动 |
| dimen (应用内部) | 5 | 删除 | `about_item_avatar_side_length_compat`, `about_item_avatar_side_length_land_compact`, `icon_developer_identifier_image_padding_compact`, `expanded_view_initial_width`, `expanded_view_initial_height` |
| array | 2 | 删除 | `keys_ace_editor_font` / `values_ace_editor_font` (字体目录已改由 `raw/ace_font_catalog_v1.json` 提供) |
| plurals | 1 | 删除 | `text_ace_action_used_times`, 11 个 `values*/plurals.xml` 同步删除 |

删除脚本 `build/tmp-size/p6_6_delete.py`; 被删文件源大小合计 21,485 字节. `resources.arsc` 3,446,056 -> 3,444,324 字节.

## (b) PNG -> WebP

脚本 `build/tmp-size/p6_6_convert.py` (Pillow 12.3, `lossless=True, method=6`; 环境无 `cwebp`, Pillow 使用同一 libwebp 无损编码器). 规则: 只处理 `res/**/*.png`, 跳过 `mipmap*/` 与 `.9.png`, WebP 不小于 PNG 则跳过 (本次 0 个), 解码回 RGBA 与源图逐像素比对 (`ImageChops.difference`) 不一致则跳过 (0 个). 源图色彩模式: LA 974, RGBA 178, P 108, 1 位 3, RGB 1.

| 目录 | 文件数 | PNG 字节 | WebP 字节 | 节省 |
| --- | ---: | ---: | ---: | ---: |
| `drawable` | 176 | 934,161 | 345,446 | 588,715 |
| `drawable-xhdpi` | 1016 | 733,583 | 484,656 | 248,927 |
| `drawable-night` | 12 | 81,835 | 28,528 | 53,307 |
| `drawable-xxhdpi` | 14 | 11,974 | 8,060 | 3,914 |
| `drawable-xxxhdpi` | 13 | 11,851 | 7,324 | 4,527 |
| `drawable-hdpi` | 15 | 8,434 | 6,300 | 2,134 |
| `drawable-mdpi` | 15 | 5,663 | 4,170 | 1,493 |
| `drawable-ldpi` | 3 | 3,271 | 2,338 | 933 |
| 合计 | 1264 | 1,790,772 | 886,822 | 903,950 |

节省最多的文件: `drawable/autojs_material` 59,390 -> 22,126, `drawable/ic_color_library_cloned` 33,303 -> 8,644, `drawable/autojs6_material` 45,565 -> 22,548, `drawable-xhdpi/ic_build_apk` 25,679 -> 4,888, `drawable/ic_filter_bigger` 31,410 -> 10,778; 最少的 `ic_remove_white_24dp` 82 -> 46. APK 内 PNG 经 aapt2 压缩后本就小于源文件 (1546 个 PNG 条目 1,910,765 字节), 因此 APK 实际节省 (位图 -490,567 字节) 小于源文件节省 (-903,950 字节).

## 验证

### 脚本 (AVD API 24, density 3.5; Redmi API 33, density 2)

`build/tmp-size/p6-6-smoke.js` (普通模式) 与 `p6-6-ui.js` (`"ui"` 模式), 运行脚本 `p6_6_run_smoke.sh` / `p6_6_run_ui.sh`:

| 检查项 | 结果 (两台一致, 括号为 API 24 / API 33 的像素值) |
| --- | --- |
| `BitmapFactory.decodeResource` (`inScaled=false`) 四个已转换资源 | `autojs_material` 256x256, `ic_build_apk` 128x128, `ic_filter` 640x640, `avatar_developer` 256x256, 尺寸与源 PNG 一致, `hasAlpha=true`, 中心像素两台相同 (`0xec85c221` / `0xff1a1a1a` / `0x0` / `0xff643d21`) |
| `getDrawable` 密度缩放 (`drawable-xhdpi/ic_build_apk` 128 px) | BitmapDrawable 224x224 / 128x128, 等于 128 x density / 2 |
| 12 个 `drawable-night` 变体 | 白天 / 夜间配置上下文分别渲染, 全部 `nightDiffers=true`, 尺寸随密度 (如 `autojs6` 1050x315 / 600x180) |
| `mipmap/ic_launcher` (未转换) | BitmapDrawable 1519x1519 / 868x868 |
| `ui.inflate` 按名字引用 `bg="@drawable/ic_volume_high"` 与 `src="@drawable/ic_build_apk"` | bg BitmapDrawable 1792x1792 / 1024x1024, img RoundedDrawable 224x224 / 128x128 |
| `"ui"` 模式 `ui.layout` 四个 `<img src="@drawable/...">` + `<frame bg="@drawable/ic_download_thicker">` | 全部得到 Drawable 且尺寸正确; 把 `autojs_material` 的 ImageView 绘制到位图, 中心像素 `0xed80be20` / `0xee86c421` (缩放滤波后与解码值 `0xec85c221` 相近) |

首轮脚本发现两处与本改动无关的坑, 记录备查: `ui.inflate` 的根节点写 `w` / `h` 会在 `ViewAttributes.setWidth` 上 NPE (根视图无 LayoutParams), 需包一层父容器; `BitmapFactory.decodeResource` 对 vector 白天变体返回 null, 夜间比对改用 `getDrawable` + Canvas 绘制.

### 其它

- `:app:assembleAppRelease` 通过 (转换与删除后无残留引用).
- `:app:testAppDebugUnitTest`: 全量 2952 项, 2 失败均在并行会话未提交的 `org.autojs.autojs.core.plugin.mail` 单测 (`MailAttachmentSinkTest` / `MailSessionClientTest`, 与本项无关, 该会话已认领), 其余 0 失败, 5 跳过. 首轮因该会话的 `MailSessionClient.kt` 编译错误未能运行, 稍后重跑通过编译.
- 体积 ([apk-size-p6-6-20260918.md](apk-size-p6-6-20260918.md)):

| 指标 | P6.5 | P6.6 | 差 |
| --- | ---: | ---: | ---: |
| universal 字节 | 32,067,627 | 31,595,920 | -471,707 |
| arm64-v8a 字节 | 30,870,601 | 30,398,894 | -471,707 |
| 条目数 | 3820 | 3811 | -9 |
| `res/` 压缩字节 | 2,584,470 | 2,092,039 | -492,431 |
| `.png` 条目 / 字节 | 1546 / 1,910,765 | 284 / 535,736 | |
| `.webp` 条目 / 字节 | 0 | 1260 / 884,462 | |
| `resources.arsc` | 3,446,056 | 3,444,324 | -1,732 |
| dex 压缩字节 | 19,746,350 | 19,767,207 | +20,857 (P6.5 之后落地的插件中心提交 fdcea1b2d / 5ce9ae295, 与本项无关) |
| 对基线 (41,146,055) | -8.66 MiB | -9.11 MiB (-23.2%) | |

## 后续

- 新增位图资源时直接放 WebP (无损) 即可, 不必再走脚本; `mipmap*/` 继续保持 PNG.
- AOSP 动画集: 维护者已拍板保留 (Q11 方案 a, D19), 清单已写入文档, 见下一节.
- 下次 lint 成功后 (需守护进程有更大堆或单独执行 `lintAnalyzeAppDebug`), 用新报告复核本次保留项是否有变化.

## Q11 落地: `res/anim` 保留为脚本目录, 文档补齐 `@anim/` 清单 (2026-09-18)

维护者拍板方案 (a) (D19). `res/anim` 的 203 个资源按根元素分类 (`build/tmp-size/p6_6/anim_catalog2.json`): 131 个视图动画 (`set` / `alpha` / `translate` 根元素, 不含 objectAnimator), 61 个 animator (objectAnimator), 9 个插值器 (`*Interpolator` 根元素), 2 个状态列表动画 (`selector`). 脚本 UI 只在 `textswitcher` 的 `animIn` / `animOut` 经 `Strings.parseAnimation` (`getIdentifier(name, "anim", pkg)` + `AnimationUtils.loadAnimation`) 按名字使用它们, 因此可用清单即 131 个视图动画.

### 设备验证 (`build/tmp-size/p6-6-anim-probe.js` 与 `p6-6-anim-ui.js`, 运行脚本 `p6_6_run_anim.sh`)

| 检查项 | AVD API 24 | Sony G8441 API 28 | Redmi 22120RN86C API 33 |
| --- | --- | --- | --- |
| 131 个视图动画逐个 `@anim/<name>` 解析并加载 | 127 可加载; 4 个 `activity_*` 失败 (`Unknown animation name: extend`) | 同 API 24 | 131 全部可加载 |
| 61 animator / 9 插值器 / 2 状态列表动画 | 全部加载失败 (预期, 非视图动画) | 同 | 同 |
| `@android:anim/fade_in`, `@android:anim/slide_in_left` 经同一解析路径 | 解析为系统资源 (`0x10a0000` AlphaAnimation 500 ms, `0x10a0002` AnimationSet 400 ms) | 同 | 同 |
| `@anim/does_not_exist` | id 0 (`parseAnimation` 记录 Invalid resource) | 同 | 同 |
| `"ui"` 脚本 4 个 `textswitcher` (`slide_in_left` / `slide_out_right`, `dialog_enter` / `dialog_exit`, `@android:anim/fade_in` / `fade_out`, `anim="from bottom"`) | 4 个均得到 in / out 动画, `setText` 后动画启动 | 同 | 同 (首轮以 `activity_open_enter` 亦通过) |

- 4 个 `activity_close_enter` / `activity_close_exit` / `activity_open_enter` / `activity_open_exit` 使用 `<extend>` 元素 (Android 12 起支持), 在 API 24 / 28 上加载抛 RuntimeException; 首轮 Sony 的 UI 脚本用了 `activity_open_enter`, 整个 `ui.layout` 因 InflateException 失败, 文档据此注明 "API 31+" 与 "引用不可加载的资源会使整个布局解析失败".
- 文档: AutoJs6-Documentation `a0234d0` 在 `api/uiAttributes.md` 新增「动画资源」一节 (131 个名称按 9 组列出, `animIn` / `animOut` 行指向该节), `python auto-generate.py uiAttributes` 重新生成 `docs/uiAttributes.html` 与 `json/uiAttributes.json`; 生成脚本 `build/tmp-size/p6_6_anim_docs.py` (`normalize-markdown.py` 会把「」改为半角引号, 先跑一次再生成).
- 体积不变 (无资源改动); 新增或删除 `res/anim` 视图动画时同步该清单 (维护者清单第 2 节).
