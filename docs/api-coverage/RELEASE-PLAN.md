# api-coverage 发布清单 (P7.1)

日期: 2026-11-02. 会话: api-cov-p7-1-release-plan (local_75ced59f-cbd2-4fb8-a955-7a1ce1b0431f). 路线图: [ROADMAP.md](ROADMAP.md) P7.1. 终态报告: [FINAL-2026-11-02.md](FINAL-2026-11-02.md).

## 使用说明

维护者按此清单逐仓库指示推送 (D6). 子会话只准备, 不推送; 每个仓库等维护者说 "推送 <仓库>" 后再执行, 执行结果写进 E 段. 本清单是静态快照: 没有用设备, 没有跑 Gradle, 没有推送, 没有打 tag, 只有宿主的本清单与 ROADMAP 有改动.

2026-11-02 15:3x 维护者拍板: 宿主暂无 push 计划 (Q274), P7.2 现在推送除宿主外的全部仓库 (步骤 3 ~ 9), Node / Bun 只推不发版; 步骤 1 / 2 / 10 等宿主有 push 计划后再做.

数字取样于 2026-11-02 15:0x ~ 15:3x. 各仓库先 `git fetch --quiet <remote>` 再算, 所以比 FINAL 第 7 节新. 宿主 HEAD 取 caca8faa7, 本清单提交后领先数 +1. 执行前用 B 段末尾的命令重算一次, 其他主题可能又有新提交.

先看这 6 条, 它们是 FINAL 与 ROADMAP 没写到的:

1. 宿主不能直接推到公开仓库. `public/master` 与 HEAD 没有共同祖先, `git push public master` 会被拒绝. 公开方式要维护者定 (Q274).
2. Node 2.1.0 与 Bun 0.22.0 从未发布过, Q227 / Q228 / Q246 / Q136 的 changelog 已写进这两节, 所以不再 bump 也能发 (Q271).
3. Node 的远端 `Android build` 从 2026-10-11 起连续 4 次失败 (`unit` 作业链接到 LFS 指针文件), 本地 prepush 查不出来, 推送后还会红 (Q275).
4. Offline Docs 的 `Markdown integrity` 现在就过不了: README 里的内容指纹落后, 而且 `auto-generate-for-autojs6.bat` 不重新生成 README, 要单独跑一次 (B6).
5. 宿主自己的 `Markdown integrity` 也过不了: 其他 9 种语言的 v6.8.0 条目数少于 zh-Hans, `--check` 报 36 行 (B8). FINAL 的清单没有列宿主.
6. 只有宿主的 `api-coverage.yml` 跨仓库检出, 它比对各仓库默认分支. 按 D6 先推宿主时它必然先红一次, 全部推完后要手动重跑一次才算通过 (C 段).

## A. 仓库总表

"其他主题" 一列按提交信息粗分, 只登记不处理; 一笔提交只计入第一个命中的主题, 未归类的不代表不属于本主题.

| 仓库 | 远端与默认分支 | HEAD | 当前版本 | 本主题目标版本 | 领先远端 | 其他主题未发版提交 |
|---|---|---|---|---|---|---|
| AutoJs6 | private = AutoJs6-Private (上游, master); public = AutoJs6 (master) | caca8faa7 | 6.8.0, VERSION_BUILD 4899, offset 1652, versionCode 6551 | 随 6.8.0, 版本名不动, 构建号按发布时 | private/master 2456, 落后 0; public/master 不可比 (见下) | api-coverage 488; augment 206; memory 167; selector 89; rhino-class 85; imgui 26; 3-macaw 14; settings 6; 未归类 1375 |
| AutoJs6-TypeScript-Declarations | origin main | 170b45b | 4.87.0 (package.json) | 4.87.0, 已 bump (P5.3) | 98, 落后 0 (远端 4.42.0) | 3-macaw 7 (含 Q136 82814a1); rhino-class 12; memory 4; selector 2; imgui 2; api-coverage 15; 未归类 56 |
| AutoJs6-Plugin-Ace-Editor | origin master | efb96ea | 2.49.0, build 393 | 2.49.0, build 393, 已 bump (P5.3, Q262) | 114, 落后 0 (远端 2.4.0) | 3-macaw 4 (含 Q136 6cfc79d, efb96ea); rhino-class 15; memory 5; selector 1; api-coverage 31; 未归类 58 |
| AutoJs6-Plugin-NodeJs-Runtime | origin master | bd51cb89 | 2.1.0, build 376 | 2.1.0 不改名 (Q271 推荐); 改选 bump 则 2.1.1. Q260 (a) 原定需 bump 并与 3-macaw 1.4.0 协调, 见 B3 | 35, 落后 0 (远端 2.0.0, 最新 tag v2.0.0) | 3-macaw 1 (Q136 bd51cb89); rhino-class 8; api-coverage 21; 未归类 5 |
| AutoJs6-Plugin-Bun-Runtime | origin master | ad4eb91 | 0.22.0, build 349 | 0.22.0 不改名 (Q271 推荐); 改选 bump 则 0.22.1. 依据同上 | 105, 落后 0 (远端 0.2.6, 最新 tag v0.2.5) | 3-macaw 1 (Q136 ad4eb91); rhino-class 22; selector 1; api-coverage 22; 未归类 59 |
| AutoJs6-Plugin-ImGui | origin master | 65315bc | 1.18.1, VERSION_BUILD 60 | 已发布, 无需动作 (tag v1.18.1 = 836f12f, versionCode 63, 索引 63.json) | 0 | 无 |
| AutoJs6-Documentation | origin master | eaf4d58 | 6.8.0, versionCode 181 (project.json) | 生成时 versionCode 自动 +1, 版本名跟 targetAutoJs6Version 6.8.0 | 131, 落后 0 | imgui 16; rhino-class 17; 3-macaw 9; selector 7; augment 7; memory 2; api-coverage 58; 未归类 15 |
| AutoJs6-Plugin-Offline-Docs | origin master | f80b11f | 6.8.40, build 252 | 同步时 build 自动 +1, 版本名由生成器保持 | 50, 落后 0 | imgui 6; selector 4; memory 2; 3-macaw 2; rhino-class 1; augment 1; api-coverage 26; 未归类 8 (都是文档站生成物) |
| AutoJs6-Plugin-APK-Builder-Template | origin master | 4625575 | 1.2.26, build 111, 配对宿主 HOST_VERSION_BUILD 5850 | 随宿主 v6.8.0 的 Runtime Kit 重建, 配对值由 `build-from-runtime-kit.yml` 决定 | 40, 落后 0 (远端 1.0.4) | 全部未归类, 按提交信息是各次宿主配对 |
| AutoJs6-Plugin-Bundled-Libraries | origin master | 0e4bd8a | 1.0.0, build 7 | 无需动作 | 0 | 无 |
| AutoJs6-Plugin-Three-Macaw-Speech | origin master | bba7fa3 | 1.4.0, build 172, 未打 tag | 归 3-macaw 主题, 本清单无动作 | 0 (工作区 1 个未跟踪文件, Q138 评估卡在写) | 全部属 3-macaw |
| AutoJs6-Plugin-Three-Folio-EPUB | origin master | 34658be | 2.1.0, build 178 | 不属本主题, 版本由维护者定 | 3, 落后 0 | 3 笔都是独立签名应用支持 (a10094c, 12328cb, 34658be) |
| AutoJs6-Plugin-Three-Stamp-Mail | origin master | 59b8b37 | 2.1.0, build 162 | 不属本主题, 版本由维护者定 | 3, 落后 0 | 3 笔都是独立签名应用支持 (276eecb, 85ea743, 59b8b37) |
| AutoJs6-Plugin-Python-Runtime | origin master | 3c9641e | 0.5.5, build 135 | 只推不发 (类型桩) | 2, 落后 0 | 无, 2 笔都是 P5.2e / Q142 |
| AutoJs6-Plugin-Java-Runtime | origin main | 1689b85 | 0.8.4-m9, build 80 | 只推不发 (README) | 2, 落后 0 | 67212f9 文档路径整理 |
| AutoJs6-Plugin-Kotlin-Runtime | origin main | 8068478 | 0.7.3-m10, build 46 | 只推不发 (README) | 2, 落后 0 | ea1c21d 文档路径整理 |
| AutoJs6-Plugin-Lua-Runtime | origin main | 4d60f0a | 0.1.3-rc.2, build 102 | 只推不发 (LuaLS 定义) | 2, 落后 0 | 9ced281 服务销毁时的会话修复 |
| AutoJs6-Official-Plugins-Index | origin main | 546a164 | 不适用 | 发版时追加索引提交 | 0 | 无 |

`public/master` 不可比的原因: fetch 后 `public/master` 仍是 ed3eb10e8 (2026-03-16), `git merge-base` 无结果. public 的根提交是 307dbe885, private 与 HEAD 的根提交是 418a8c96f, 两段历史互不相连, 所以 `rev-list` 得到的 4957 / 1365 没有意义. 内容上的对应点是 private 历史里的 04f12e0ee: 与 ed3eb10e8 同作者, 同时间, 同标题 ("Merge pull request #502"), 但树不同, public 多 53 个文件 (jieba 等). 按这个对应点, HEAD 比公开内容多 3884 笔 (= private/master 的 1428 + 未推的 2456). 公开仓库的版本是 6.7.0 (VERSION_BUILD 3804), 最新 Release v6.7.0.

Rhino-For-AutoJs6 不在本表: 宿主用入库的 `libs/org-mozilla-rhino-2_0_0-SNAPSHOT.jar`, 不依赖 fork 仓库推送. fork 领先 origin 203 笔, 工作区 46 项在改, 归 rhino-class 主题.

## B. 前置条件

| 编号 | 条件 | 当前状态 | 说明 |
|---|---|---|---|
| B1 | 宿主 6.8.0 公开 | 维护者 2026-11-02 决定: 暂无 push 计划, 代码留本地 (Q274); C 段步骤 1 / 2 / 10 暂停 | 宿主只本地提交. D6: 公开后再推 Node / Bun / Ace / TS. 公开方式见 Q274 |
| B2 | Node / Bun 对最低宿主的声明 | 已满足, 无需改 (Q272 (a) 已决保持 5278) | 比较的是宿主 versionCode 6551, 不是 VERSION_BUILD 4899. Node 声明 3923 (AndroidManifest 第 105, 121 行, NodeJsPluginInfoService, 2 个测试), Q206 (a) 已决保持. Bun 声明 5278 (宿主 `plugin-api/bun-runtime-api` 的 BunRuntimeContract, 经 AAR 进插件, README 同写). 两值都低于 6551, 所以 6.8.0 宿主能安装. 公开宿主止于 6.7.0 (VERSION_BUILD 3804), 低于两个声明值; 6.8.0 公开后在 6551 以上, 高于两个声明值. 公开用户不会落在声明值与 6551 之间, 不需要为此提高 |
| B3 | Node / Bun 版本与 3-macaw 1.4.0 协调 | 已决 Q271 (a): 不改版本名, 发布当天改 released_date | 2.1.0 / 0.22.0 没有 tag, 远端还是 2.0.0 / 0.2.6. bump 之后的提交已把条目写进 v2.1.0 / v0.22.0 节, 只差发布当天改 `released_date` (现为 2026/11/01). 3-macaw 1.4.0: 版本已是 1.4.0 且已推送, 未打 tag, Q138 评估卡 (local_fff2f977) 进行中, P9.5 未开始. 属于它的未发版提交: Node bd51cb89, Bun ad4eb91, TS 82814a1 / d830b2b / 59d3d98 / ca77091, Ace 6cfc79d / efb96ea / 3aebb44, 文档 df98878 / 23aab4b / 3c9235a. 这些随本清单推送, 两边没有先后依赖: 类型字段是可选的, 插件没到 1.4.0 时值为空, 不影响运行 |
| B4 | Ace 捆绑声明版本 | 已满足 | `BUNDLED_DECLARATIONS.md` 写 4.87.0, 与 TS package.json 一致. Q262 (b) 的 4 行在 efb96ea. 3-macaw 看板第 74 行仍写 "Ace 的 Rhino 声明同步归 P9.5.7", 已被 efb96ea 取代, 看板未改, 由 3-macaw 调度核对 |
| B5 | TS 声明是否发 npm | 已决 Q273 (a): 发 4.87.0, 维护者 npm publish | npm 现为 4.24.1 (2026-09-30, 共 11 版), 本地 4.87.0, GitHub 4.42.0. `npm pack --dry-run`: 214 个文件, 打包 2.49 MB, 解包 20.2 MB. 发布要维护者的 npm 账号, 子会话不发 |
| B6 | 文档站生成物与 Offline Docs 同步 | 未满足, 维护者待办 | `auto-generate.py --check` 退出 1, 6 个落后: `docs/changelog.html`, `docs/image.html`, `docs/progress.html`, `docs/runtimeApis.html`, `json/runtimeApis.json`, `docs/assets/offline-search-index.js` (eaf4d58 未生成). 跑 `generator/auto-generate-for-autojs6.bat` 后, 另要在 Offline Docs 跑 `py .python/generate_markdown.py` 并提交: 生成器不碰 README, README 现写 fileCount 232 / contentSha256 985e6d30, 实际 245 / 40e223c4, 还有 sourceBaseCommit d163293c 要核对 |
| B7 | changelog 其他 9 种语言同步 | 未满足, 维护者待办 | TS v4.87.0, Ace v2.49.0, Node v2.1.0, Bun v0.22.0 都只有 zh-Hans (1 / 10). 文档站同样落后. `generate_markdown --check` 失败是预期: TS, Ace, Bun, 文档站. TS `.python/tests` 13 例 error, 文档站 `.python/tests` 17 例 error, Bun `.python` 38 例中 2 例 error, 三者都在各自 CI 里. 例外: Node 的 `--check` 通过, 它不查跨语言版本一致, 通过不代表已同步 |
| B8 | 宿主 changelog 与生成物 | 未满足, 维护者待办 | `.changelog/lang_zh-Hans.json` 补本主题条目, 重新生成 CHANGELOG / README; 工作区 5 个未提交文件就是它, 本卡没碰. 另外宿主 `generate_markdown.py --check` 在 HEAD 与工作区都退出 1, 36 行 "expected N entries" (例如 ar v6.8.0 fix 194 比 150), 也就是宿主 9 种语言同样要同步, 否则 `Markdown integrity` 红 |
| B9 | Node `Android build` 的 `unit` 作业 | Q275 (a) 已决, 修复见 8b054d59 (本地, 随 P7.2 推送) | 远端 776a40668 (10-11) 到 22fd1d347 (10-30) 连续 4 次失败, `package` 与 `emulator` 随之跳过. 原因: 该作业 `lfs: false`, 而 `:app:testDebugUnitTest` 触发 CMake 链接 `libnode.so`, 读到 LFS 指针. HEAD 的作业步骤没变 (未跑 Gradle 验证). `restore-native` 只在 `package` 作业里用 |
| B10 | 待决 Q266 | 不阻塞 | 已按推荐先行, 见 ROADMAP 附录 C |

重算命令 (只更新远端引用, 不动工作区):

```bash
git -C D:/idea-projects/AutoJs6 fetch --quiet private
```

```bash
git -C D:/idea-projects/AutoJs6 rev-list --count private/master..HEAD
```

其他仓库把 `private/master` 换成 A 段的远端与分支.

## C. 推送顺序

依据: D6, Q110, 以及对各仓库工作流的实读. 关键事实: TS, Ace, Node, Bun, 文档站, Offline Docs 的 CI 都不检出其他仓库 (工作流里没有 `repository:`), 所以它们之间的先后不影响各自 CI. 跨仓库比对只发生在宿主 `api-coverage.yml`, 而它检出的是各仓库默认分支. 所以下面的顺序主要来自 D6 与来源关系, 不是 CI 硬依赖.

| 步骤 | 仓库 | 为什么排在这里 | 这一步之后还红的 CI |
|---|---|---|---|
| 1 | AutoJs6 推到 private/master | 其余各仓库都描述 6.8.0 的 API, D6 要求宿主先行. 快进推送, 不公开 | `Script API coverage` 首次运行必红: 文档, TS, Ace, Node, Bun 都还落后. 这是预期. `Markdown integrity` 在 B8 完成前红 |
| 2 | AutoJs6 公开并打 tag v6.8.0 | D6 的 "宿主 6.8.0 公开". 方式见 Q274. tag 推送触发 `release-runtime-kit.yml`, 再经 `AUTOJS6_PLUGIN_REPO_TOKEN` 通知 APK Builder 模板 | 同上 |
| 3 | AutoJs6-Documentation | Docsify 站跟随 master, `pages.yml` 推送即上线, 所以排在宿主公开之后 (Q157 "文档站随 6.8.0 推送"). 先跑 B6 | `Markdown integrity` 在 B7 完成前红 |
| 4 | AutoJs6-TypeScript-Declarations | 声明描述宿主 API. Ace 捆绑它, 所以排在 Ace 前, 这样 `BUNDLED_DECLARATIONS.md` 写的 4.87.0 在远端能找到 | `Markdown integrity` 在 B7 完成前红 |
| 5 | AutoJs6-Plugin-Ace-Editor | 捆绑 TS 4.87.0 与运行时声明. 自身 CI 用仓库内副本, 不下载 TS | `Markdown integrity` 在 B7 完成前红 |
| 6 | AutoJs6-Plugin-NodeJs-Runtime | 插件发版要求宿主已公开. TS 的 `autojs6-node/` 由它同步而来, 摘要在宿主 CI 里比对 | `Android build` 在 Q275 处理前红 |
| 7 | AutoJs6-Plugin-Bun-Runtime | 同上. `check-bun-*.mjs` 同时读 Bun 与 Node 两个仓库, 排在 Node 之后两者才齐 | `Markdown integrity` 在 B7 完成前红 |
| 8 | AutoJs6-Plugin-APK-Builder-Template | 只依赖宿主 tag 产出的 Runtime Kit, 不依赖 Node / Bun. 模板自身推送的 CI 只查源码与文档; 完整发版走 `build-from-runtime-kit.yml` | 无跨仓库依赖 |
| 9 | 官方插件: Offline Docs; Python, Java, Kotlin, Lua; Folio, Stamp | `language_runtimes.py` 比对文档与四个语言运行时 (Q110), `check-runtime-epub.mjs` / `check-runtime-mail.mjs` 读 Folio / Stamp (它们的 3 笔是否为通过门禁所必需没有验证, 一起推最稳). Offline Docs 的内容来自步骤 3 | Offline Docs `Markdown integrity` 在 B6 完成前红 |
| 10 | 手动重跑宿主 `Script API coverage` | 到这里 15 个仓库的默认分支才都到位 | 这一次必须绿, 是本主题远端门禁的验收 |

最后一步的命令 (`-R` 按 Q274 的结果选 AutoJs6-Private 或 AutoJs6):

```bash
gh workflow run api-coverage.yml -R SuperMonster003/AutoJs6-Private --ref master
```

`api-coverage.yml` 每一步读哪些仓库, 以及对应仓库未推时的结果 (按代码读取路径判断, 没有实跑模拟):

| 步骤 | 读取 | 未推时 |
|---|---|---|
| KSP 元数据 | 宿主 | 不受影响 |
| `check.py --source ksp --matrix --strict-arity --strict-types --docs-support` | 文档, TS, Ace, Node, Bun, ImGui | 任一落后都失败; 这是首次运行实际停下的地方, 同一 `run` 块里后面的命令与后续步骤都不再执行 |
| `check-runtime-*.mjs` (工作流里 16 个) | Node, Bun, 文档; speech 读 Macaw, epub 读 Folio, mail 读 Stamp | 对应仓库落后时失败 |
| `check-bun-*.mjs` (7 个) | Bun, Node, Ace; files-data 读 Bundled-Libraries | 同上 |
| `language_runtimes.py` | 文档, Ace, Python, Java, Kotlin, Lua | 同上 (Q110 已说明) |
| Ace 查询清单 (`generate-runtime-api-queries.mjs --check --host ../AutoJs6`) | Ace, 宿主 | Ace 未推时脚本不存在 (`origin/master` 上没有), 直接失败 |
| TS 运行时声明 (`sync-runtime-declarations.mjs --check --ace`) | TS, Ace, Node, Bun | TS 未推时脚本不存在 (`origin/main` 上没有), 直接失败 |
| TS 手写声明 (`verify-api-coverage.mjs`) | TS, Ace | 落后时失败 |

可选的预演: `workflow_dispatch` 接受 documentation / declarations / ace / node / bun / speech / epub / mail / imgui 九个 ref 输入, 可以先把这些仓库推到非默认分支, 再用这些 ref 跑一次门禁, 不动默认分支. Python, Java, Kotlin, Lua, Bundled-Libraries 没有 ref 输入, 要先推到默认分支. 这也算推送, 同样等维护者指示.

## D. 每仓库 prepush 检查

P7.2 的正式命令是 `py D:/idea-projects/prepush_check.py <仓库>`. 本卡按约定不跑 Gradle, 实跑的是 `--no-gradle`, 所以 Gradle 单元测试都没有结果, 要在 P7.2 补跑. 16 个仓库跑前跑后 `git status --porcelain` 都没有变化.

| 仓库 | 实跑命令 | 退出码 | 关键结论 |
|---|---|---|---|
| AutoJs6 | `tools/api-coverage/check.py --source ksp --matrix --strict-arity --strict-types --docs-support` (引用 FINAL 第 2 节, 未重跑) | 0 | pending 0 / 0 / 0, failures 空, 95 s |
| AutoJs6 | `py -3.13 .python/generate_markdown.py --check` | 1 | 36 行条目数不一致, 见 B8, HEAD 与工作区相同 |
| TS | `prepush_check.py --no-gradle` | 1 | 2 / 4: 生成文档 (zh-Hant-HK 与 zh-Hans 不一致), `.python/tests` 13 error, 都是 B7 的预期 |
| TS | `npm test`, `npm run test:runtimes` | 0, 0 | 这是 FINAL 留给本卡的项; TS CI 跑的是后者 |
| Ace | `prepush_check.py --no-gradle` | 1 | 4 / 5: 生成文档 (zh-Hant-HK 当前版本不一致), B7 的预期; 其余过, 构建号 393 / 393 |
| Node | `prepush_check.py --no-gradle` | 0 | 5 / 5, 构建号 376 / 376. 查不出 B9 与 B7 |
| Bun | `prepush_check.py --no-gradle` | 1 | 3 / 5: 生成文档 (en 没有 v0.22.0), `.python` 2 error, B7 的预期; 构建号 349 / 349 |
| 文档站 | `prepush_check.py --no-gradle` | 1 | 2 / 4: 生成文档, `.python/tests` 17 error, B7 的预期 |
| 文档站 | `py generator/auto-generate.py --check` | 1 | 6 个生成物落后, 见 B6 |
| Offline Docs | `prepush_check.py --no-gradle` | 1 | 4 / 5: 生成文档失败, 这项不是语言同步问题, 见 B6 |
| APK Builder 模板 | `prepush_check.py --no-gradle` | 0 | 7 / 7, 构建号 111 / 111 |
| ImGui | `prepush_check.py --no-gradle` | 0 | 5 / 5 |
| Bundled-Libraries | `prepush_check.py --no-gradle` | 0 | 5 / 5, 构建号漂移 -5, 在容差内 |
| Macaw | `prepush_check.py --no-gradle` | 0 | 6 / 6, 有未跟踪文件的提示 |
| Folio | `prepush_check.py --no-gradle` | 0 | 5 / 5 |
| Stamp | `prepush_check.py --no-gradle` | 0 | 5 / 5 |
| Python | `prepush_check.py --no-gradle` | 0 | 5 / 5 |
| Java | `prepush_check.py --no-gradle` | 0 | 5 / 5 |
| Kotlin | `prepush_check.py --no-gradle` | 0 | 5 / 5 |
| Lua | `prepush_check.py --no-gradle` | 0 | 6 / 6 |

本卡只写不跑的命令 (会改文件或需要账号): `generator/auto-generate-for-autojs6.bat`, Offline Docs 的 `py .python/generate_markdown.py`, 各仓库的 `prepush_check.py --fix`, `npm publish`, 全部 `git push` 与 `git tag`.

插件发版另按 ROADMAP 的门槛: prepush, CI 全绿, 在线设备冒烟. 索引生成器从 tag 静态读源码, 打 tag 前按记忆 index-generator-flavor-rules 本地模拟 `build_entries_from_release`.

## E. P7.2 发布登记表

P7.2 每完成一个仓库填一行, 不改已填的行.

| 仓库 | 日期 | tag | 索引提交 | 备注 |
|---|---|---|---|---|
| AutoJs6 (private) | | | 不适用 | |
| AutoJs6 (public) | | | 不适用 | |
| AutoJs6-Documentation | | | 不适用 | |
| AutoJs6-TypeScript-Declarations | | | 不适用 | npm: |
| AutoJs6-Plugin-Ace-Editor | | | | |
| AutoJs6-Plugin-NodeJs-Runtime | | | | |
| AutoJs6-Plugin-Bun-Runtime | | | | |
| AutoJs6-Plugin-APK-Builder-Template | | | | |
| AutoJs6-Plugin-Offline-Docs | | | | |
| AutoJs6-Plugin-Python-Runtime | | | | |
| AutoJs6-Plugin-Java-Runtime | | | | |
| AutoJs6-Plugin-Kotlin-Runtime | | | | |
| AutoJs6-Plugin-Lua-Runtime | | | | |
| AutoJs6-Plugin-Three-Folio-EPUB | | | | |
| AutoJs6-Plugin-Three-Stamp-Mail | | | | |
| 宿主 `Script API coverage` 重跑 | | 不适用 | 不适用 | run id: |

## F. 本卡新待决

详情在 ROADMAP 附录 C 与看板待决区. 五项已于 2026-11-02 15:3x 全部拍板.

| 编号 | 问题 | 状态 |
|---|---|---|
| Q271 | Node / Bun 发布版本号: 不改名, 还是 2.1.1 / 0.22.1 | 已决 (a) 不改名 |
| Q272 | Bun 最低宿主 5278 是否保持 (与 Node 的 Q206 对称) | 已决 (a) 保持 5278 |
| Q273 | TS 声明是否发 npm | 已决 (a) 发 4.87.0 |
| Q274 | 宿主 6.8.0 公开方式 | 已决: 暂无 push 计划, 公开方式届时再定 |
| Q275 | Node `unit` 作业长期失败, 推送前修不修 | 已决 (a) 推 Node 前修 |
