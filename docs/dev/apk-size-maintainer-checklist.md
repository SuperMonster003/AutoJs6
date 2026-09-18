# APK 体积维护者清单

更新日期: 2026-09-18. 对应 [APK 体积 Roadmap](apk-size-reduction-roadmap.md) P7.1, 阶段汇总见 [apk-size-summary-20260918.md](evidence/apk-size-summary-20260918.md). 与 [16 KB 维护者收尾清单](page-size-16kb-maintainer-checklist.md) 并列: 前者管原生库对齐, 本文管安装包体积.

## 1. 发版前检查 (每次发布必做)

1. 构建发布产物: `./gradlew :app:assembleAppRelease` (度量口径 D1: universal 与 arm64-v8a 两个 APK).
2. 与上一发布的报告比较并落盘:

   ```bash
   python tools/size/apk_size_report.py \
       app/build/outputs/apk/app/release/autojs6-v<ver>-universal.apk \
       app/build/outputs/apk/app/release/autojs6-v<ver>-arm64-v8a.apk \
       --compare docs/dev/evidence/apk-size-release-<上一版本>.json \
       --json docs/dev/evidence/apk-size-release-<ver>.json \
       --markdown docs/dev/evidence/apk-size-release-<ver>.md \
       --max-growth-percent 5
   ```

   脚本按文件名 (版本号替换为 `*`) 匹配上一发布的条目, 退出码 3 表示某个 APK 比上一发布大了 5% 以上 (报告仍会写出). 首次发布时 `--compare` 用 [基线](evidence/apk-size-baseline-20260917.json) 或最近的阶段报告 ([P6.6](evidence/apk-size-p6-6-20260918.json)).
3. 退出码 3 时, 在 PR / 提交说明里写明增长原因 (新依赖, 新资产, 新原生库或方法数增长), 并引用报告中对应的分组 / DEX 包行; 不接受 "未知". 增长若来自有意的功能, 记录后照常发布; 若来自误入的依赖 (报告 "仅被引用" 行或 DEX 表中的新包), 先修再发.
4. 提交 `evidence/apk-size-release-<ver>.{json,md}`, 下一次发布以它为 `--compare` 输入.

## 2. 日常开发默认值 (已采用, 无需再问)

- 新增依赖前先看它在 DEX 表中的落点: 大于 150 KiB 原始 DEX 的包会出现在报告的 DEX 表 (`--dex-min-kib`), 提交说明写明体积代价; 优先使用平台 API 或已有依赖 (P2 / P6.1 / P6.2 的做法).
- 位图资源直接放无损 WebP (`res/drawable*/`), 启动 / 快捷方式图标 (`mipmap*/`) 保持 PNG; 九宫格保持 `.9.png` (P6.6).
- 只给脚本按名字使用的资源目录 (drawable 图标集, `res/anim` AOSP 动画集, string / color / style) 不按 lint `UnusedResources` 删除; 删除应用内部遗留资源前用 `git grep -w` 复核并写入证据 (P6.6, Q11 待拍板).
- 资产剪除清单在 `app/build.gradle.kts` 的 `appMergedAssetPruneIncludes`; 内置版本历史只保留 zh-Hans 与 en (`bundledChangelogLanguageTags`, P6.5), 新增语言时把标签加进推导列表.
- Rhino 只打包瘦身 jar (`slimRhinoJar`, 去掉 `org/jline/**`), 编译与测试仍用完整 jar (P1).
- 打包排除 (`packaging.resources.excludes`) 用 java.nio glob, `dir/**/*` 只匹配深度 >= 2, 需要时写 `dir/*` (P1).
- 不做 minify / R8 (D10); `com.stardust` 兼容包保留 (D16); joda-time 时区表保留 (P6.3); 旧版 dx 1.7.0 为 API 24 / 25 保留 (Q10 前不动).

## 3. 受门禁的体积项 (发布不阻塞, 评审时只更新状态)

| 编号 | 内容 | 门禁 | 预估 |
| --- | --- | --- | --- |
| P5.2 | 去掉运行时 R8 (`com.android.tools.r8` + `new_api_database.ser`) | D5: DEX 编译器插件晋级默认路径, 且 `loadAar` / `defineClass` 有非 R8 方案 | 约 5.5 MiB |
| P5.3 | 旧版 dx 1.7.0 | Q10: 需要 `desugar_jdk_libs_nio` 或放弃 7.x 动态类加载, 建议保持现状 | 约 0.17 MiB |
| Q11 | `res/anim` AOSP 动画集 (183 个未引用 anim + 51 interpolator) | 维护者拍板是否作为脚本目录保留 | 源文件约 0.33 MiB |

## 4. 证据与工具

- 度量工具 `tools/size/apk_size_report.py` (P0.1): zip 分组, 最大条目, `apkanalyzer dex packages` 的包级 DEX 原始字节, `--compare` 差异表, `--max-growth-percent` 门禁.
- 基线 [apk-size-baseline-20260917.{json,md}](evidence/apk-size-baseline-20260917.md); 各阶段报告 `evidence/apk-size-p*-2026091{7,8}.{json,md}` 与说明文档 `evidence/apk-size-p*-<主题>-<日期>.md`.
- 设备验证脚本 (未纳入仓库) 位于 `build/tmp-size/`; 复现方法写在各阶段证据的 "验证" 一节.
