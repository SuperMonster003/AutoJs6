# P6.5 内置 CHANGELOG 只保留 zh-Hans 与 en (2026-09-18)

对应 [apk-size-reduction-roadmap.md](../apk-size-reduction-roadmap.md) P6.5 (决策 D14), 体积报告见 [apk-size-p6-5-20260918.md](apk-size-p6-5-20260918.md).

## 结论

- APK 内 `assets/doc/` 由 12 个条目 (压缩 1,009,234 字节) 减为 3 个 (`CHANGELOG-zh-Hans.md`, `CHANGELOG-en.md`, `RHINO.md`, 压缩 167,561 字节); universal 32,906,264 -> 32,067,627 字节 (-838,637 字节 = -0.80 MiB; 对基线 -9,078,428 字节 = -8.66 MiB, -22.1%).
- 剪除发生在资产合并期 (`app/build.gradle.kts`), 生成脚本 `.python/generate_markdown.py` 与仓库内 `app/src/main/assets-app/doc/` 的 11 个文件不变, 在线路径仍指向仓库文件.
- 离线回退链: 当前语言缓存 -> 当前语言资产 -> en 资产; 选版本最新者, 同版本靠前者胜出 (同语言缓存优先于资产, 与改动前一致; 当前语言优先于 en 回退).
- 证据等级 E3: JVM 单测 + AVD API 24 + Redmi 22120RN86C API 33 脚本与真实版本历史页面验证.

## 改动

### `app/build.gradle.kts`

```kotlin
val bundledChangelogLanguageTags = listOf("zh-Hans", "en")
val prunedChangelogAssets = listOf("ar", "es", "fr", "ja", "ko", "ru", "zh-Hans", "zh-Hant-HK", "zh-Hant-TW", "en")
    .filterNot { it in bundledChangelogLanguageTags }
    .map { "doc/CHANGELOG-$it.md" } + "doc/CHANGELOG.md"

val appMergedAssetPruneIncludes = listOf(...) + prunedChangelogAssets
```

`doc/CHANGELOG.md` 与 zh-Hans 内容相同且无代码读取 (`UpdateChecker` 只用它拼 GitHub URL), 一并剪除. inrt 变体原本就剪除整个 `doc/`. Ant 风格 include 不支持花括号, 因此按语言列表推导而非手写通配.

### `ui/settings/ReleaseHistoryRepository.kt`

- 新增 `FALLBACK_LANGUAGE_TAG = "en"`, `enum LocalSampleSource { ASSET, CACHE, FALLBACK_ASSET }`, `data class LocalSampleCandidate(source, languageTag, markdown)` (`latestVersion` 取首个 `# vX.Y.Z` 标题).
- `readLocalSampleCandidates(context, languageTag)`: 依次读取 `filesDir/CHANGELOG-<tag>.cache.md`, `assets/doc/CHANGELOG-<tag>.md`, 以及 tag 非 en 时的 `assets/doc/CHANGELOG-en.md`; 缺失项跳过.
- `chooseLocalSample(candidates)`: 纯函数, 忽略空白, 取版本最新者, 同版本保留靠前者.
- `readBestLocalSample` 改为组合上面两步; ProcessLogger 对每个候选记录 "<来源>最新版本 (<语言>): <版本>", 并记录 "初始内容选用: <来源> (<语言>)" (回退时为 `本地资产文件 (en)`), 复用现有字符串资源, 不新增翻译.
- `parseFirstVersion` 提为 `internal` 扩展供单测使用. 一个陷阱: 局部扩展函数里 `languageTag` 会被外层同名参数遮蔽, 标签用 `this.languageTag` (首轮设备验证发现日志写成了请求语言).

### 单测 (`ReleaseHistoryRepositoryTest`, 3 -> 8 项)

同语言缓存 / 资产按版本取新, 同版本保留缓存; 被剪除语言依次回退到缓存与 en 资产, 过期缓存输给更新的 en 资产; 空白候选忽略, 无候选返回 null; `parseFirstVersion` 对多标题 / 带后缀 / 无标题 / null 的处理.

## 验证

### 脚本 (`build/tmp-size/p6-5-smoke.js`, AVD API 24 与 Redmi API 33 结果一致)

| 检查项 | 结果 |
| --- | --- |
| APK 内 doc 资产 | `CHANGELOG-zh-Hans.md` / `CHANGELOG-en.md` / `RHINO.md` 存在; `CHANGELOG.md`, zh-Hant-TW / ja / ar / ru 不存在 |
| 链 zh-Hans | `[ASSET:zh-Hans@6.8.0, FALLBACK_ASSET:en@6.8.0]` -> ASSET zh-Hans |
| 链 en | `[ASSET:en@6.8.0]` (无重复回退项) -> ASSET en |
| 链 zh-Hant-TW, 无缓存 | `[FALLBACK_ASSET:en@6.8.0]` -> en 回退 |
| 链 ja, 同版本伪缓存 | `[CACHE:ja@6.8.0, FALLBACK_ASSET:en@6.8.0]` -> CACHE ja |
| 链 ja, 旧版伪缓存 (6.0.0) | -> FALLBACK_ASSET en |
| 应用语言切到 zh-Hant-TW 后打开版本历史 | ProcessLogger: `本地資產檔案最新版本 (en): 6.8.0` / `初始內容選用: 本地資產檔案 (en)` / `載入本地資料 (共計 22 項)` / raw 请求 `CHANGELOG-zh-Hant-TW.md` 成功并写入离线缓存 / `本地資料較新, 停止繼續分析線上資料`; 缓存文件 100,760 字节, 最新 6.7.0 (远端 master 尚未合并 6.8.0) |
| 打开后再读链 | `[CACHE:zh-Hant-TW@6.7.0, FALLBACK_ASSET:en@6.8.0]` -> 按版本仍选 en |
| 恢复语言设置 | AUTO |

页面 `uiautomator dump`: 列表以 v6.8.0 (2026/09/18) 开头, 22 个版本卡片正常渲染. 备注: API 33 上直接改写语言 Pref 不会切换每应用语言 (现有行为, 设置页切换时才调用 `AppCompatDelegate.setApplicationLocales`), 页面文案仍为英文, 但请求语言标签、URL 与缓存文件名均为 zh-Hant-TW, 回退链已覆盖. 两台设备都能访问 GitHub, "纯离线" 情形即请求前的初始内容选择 (上表第 7 行的前两条日志), 加上单测中的无缓存 / 过期缓存分支.

### 其它

- `:app:assembleAppRelease` 通过; `:app:testAppDebugUnitTest` 全量 2886 项, 0 失败, 5 跳过.
- 体积 ([apk-size-p6-5-20260918.md](apk-size-p6-5-20260918.md)):

| 指标 | P6.4 | P6.5 | 差 |
| --- | ---: | ---: | ---: |
| universal 字节 | 32,906,264 | 32,067,627 | -838,637 |
| arm64-v8a 字节 | 31,709,238 | 30,870,601 | -838,637 |
| 条目数 | 3829 | 3820 | -9 |
| `assets/doc` 压缩 MiB | 0.96 | 0.16 | -0.80 |
| 对基线 (41,146,055) | -7.86 MiB | -8.66 MiB (-22.1%) | |

## 后续

- 新增语言时把标签加入 `prunedChangelogAssets` 的源列表 (或改为从 `supportedResourceLocales` 推导); 若某天决定内置更多语言, 只改 `bundledChangelogLanguageTags`.
- 远端 `master` 合并 6.8.0 后, 被剪除语言首次联网即得到与内置版本同级的缓存, 之后离线显示该语言.
