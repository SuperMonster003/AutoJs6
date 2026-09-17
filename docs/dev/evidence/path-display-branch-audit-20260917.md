# Path display and temporary branch audit, 2026-09-17

## Path display

Script sources use the common path formatter for task descriptions and execution lifecycle logs, including Node project entries. Known roots are displayed as `$cwd`, `$sdcard`, `$sample`, `$files`, `$cache` and `$data`. `$cwd` means the application's configured working directory. Actual execution paths and entry extensions remain intact; unknown paths and document URIs retain their original representation.

Directory matching now respects complete path components and trailing separators. For example, a working directory of `/storage/emulated/0/Scripts` abbreviates `/storage/emulated/0/Scripts/TestQ/media-library/main.cjs` to `$cwd/TestQ/media-library/main.cjs`, but does not match `ScriptsBackup`. Cached sources append their cache marker after abbreviating the original path.

Verification before branch integration:

- Debug application and Android test APKs built successfully with JDK 21.
- 30 JVM tests passed across path formatting, script sources and pending task routing, with no failures or skips.
- Four Android tests passed on the owned Android 13 / API 33 x86_64 emulator. They cover display aliases, unchanged execution paths, pending and running task presentation, cache markers, and both reported Node media projects through the real plugin runtime.
- Both media samples completed their expected-output assertions. With the acceptance fixture's working directory set to `/storage/emulated/0/Scripts/TestQ`, both start and finish logs contained `[$cwd/media-library/main.cjs]` or `[$cwd/media-playback/main.cjs]` respectively. The test restores the working directory afterward.
- Ten-language 6.8.0 changelogs were updated and the Markdown generator completed.

Build command:

```powershell
.\gradlew.bat :app:assembleAppDebug :app:assembleAppDebugAndroidTest :app:testAppDebugUnitTest --tests 'org.autojs.autojs.pio.PFilesTest' --tests 'org.autojs.autojs.script.JavaScriptSourceTest' --tests 'org.autojs.autojs.ui.main.task.*' --console=plain
```

Device test classes: `org.autojs.autojs.pio.PathDisplayInstrumentationTest` and `org.autojs.autojs.engine.NodeMediaProjectInstrumentationTest`, with instrumentation argument `mediaProjectRoot=/storage/emulated/0/Scripts/TestQ`.

Local raw evidence is under `build/verification/path-display-branch-audit/`, including `build-paths.log`, `path-device-tests.log`, `generate-paths.log`, and the branch/worktree snapshots taken before changes. The full-path log examples in the earlier terminal/console/media evidence document describe its earlier verification run; this change updates their subsequent display behavior.

## JVM branch integration

The six nested `codex/m9-*` branches contain eight commits with real additions: the explicit JVM execution path supports clipboard calls, JSON arguments, safe exception class names, execution observations, bounded multi-file Java source archives and named entry classes. They are integrated through the maximal `codex/m9-arbitrary-entry` branch. The session preserves the existing checked-in `autojs.jvmSource.experimental=true` setting. The Gradle provider falls back to false only if this property is absent; the current application build enables it.

The merge keeps the current SDK 37 configuration, Python heartbeat handling and R8 fixture digest. Version code 5281 meets the Java provider's existing host requirement. Protocol negotiation now retains compatibility with the installed Kotlin provider's protocol 1.1 / entry API 2, while selecting protocol 1.6 / entry API 4 with a compatible Java provider. Capability fingerprints bind the negotiated entry API; newer request fields and observations remain gated by their protocol versions.

Verification:

- 118 host JVM tests and 71 shared JVM protocol tests passed (189 total, no failures or skips).
- Application and Android test APKs built with the explicitly enabled experimental JVM flag.
- Nine Android integration tests passed with Java Runtime 0.8.3-m9 and Kotlin Runtime 0.7.2-m10 on the owned Android 13 emulator. They cover canonical and named entries, multiple Java files, JSON arguments, foreground clipboard access, safe exception details, execution observations, legacy Kotlin compatibility, and 12 consecutive Java executions.
- The tests passed with both the Java plugin's debug and signed release APKs. Current-provider evidence uses D8 8.13.23, supplied through the test's `jvmD8Version` argument.
- The ten-language changelog incorporates these additions without replacing later mainline entries or backdating the release.

This verification exposed a Java plugin finalization race: resource closure and worker death could both dispatch finalization, allowing a terminal callback to run before its observation was ready. The related Java Runtime repository now gives exactly one caller finalization ownership. Both new deterministic regressions fail before the correction; all 167 plugin JVM tests pass afterward. Debug/test/release assembly, lint (0 errors), signed release collection, three Python tests and Markdown consistency checks also passed. The plugin retains its M9 designation as 0.8.3-m9, build 58. See that repository's `docs/session-finalization-20260917.md` for the diagnosis and verification boundaries.

Local logs include `build-jvm-3.log`, `java-plugin-build.log`, `jvm-device-fixed.log` and `jvm-device-signed-release.log`, with JUnit reports archived separately. Initial failing runs and the host thread dump are retained rather than represented as passing evidence.

## OpenCC branch integration and final build

The two `codex/opencc-m3-20260831` commits add plugin contract version 2 with conversion-type discovery, batches of up to 1024 texts and chains of up to 32 stages. Existing contract-v1 plugins retain ordered per-item/per-stage fallback. The 16 composed Rhino conversions use the plugin's chain operation when supported. Plugin instruction metadata also resolves localized `@raw/...` references, including package-qualified references, alongside existing `@string/...` support.

Review found that the branch's chain refactor omitted the existing `S2TWI` and `TWI2S` identifiers. Both aliases are retained, with explicit locale-independent lookup. The public Rhino method signatures remain unchanged. The documentation and TypeScript declaration repositories were checked: their current methods, aliases and examples remain accurate, so neither requires regeneration or a version change.

The final build uses the repository's existing Gradle properties, including its already-enabled experimental JVM source property; no temporary command-line feature override is needed. Application APKs were generated for arm64-v8a, armeabi-v7a, x86, x86_64 and universal, plus the Android test APK.

- 160 application JVM tests, 71 shared JVM protocol tests and 2 OpenCC contract tests passed (233 total, no failures or skips). These cover the path changes and both functionality-bearing branch integrations.
- Seven Android tests passed on the owned Android 13 / API 33 x86_64 emulator in 8.146 seconds. The four path/Node tests revalidated the original media examples and their shortened task/log paths. Three OpenCC tests verified plugin metadata and conversion-type discovery, a full 1024-text batch, chain equivalence, both documented aliases through the public Rhino API, all 16 composed methods and their string identifiers, and localized local/package-qualified instruction resource resolution.
- Legacy OpenCC routing is covered by JVM tests; the installed plugin used for the real Binder tests supports contract version 2. No device result is claimed for an uninstalled legacy OpenCC APK.
- The 9 JVM Android tests were verified separately with both debug and signed Java Runtime 0.8.3-m9 APKs, including the 12-execution sequence. The Java plugin correction is committed as `b96252b` in its own repository.
- Ten-language JSON changelog entries were merged into the current release in their relevant groups. The Markdown generator completed and the date remains 2026/09/17.

Raw evidence: `build-final.log`, `final-device.log`, `generate-opencc.log`, and archived `final-host-unit-results`, `final-jvm-protocol-unit-results` and `final-opencc-contract-unit-results` directories under the local verification directory.

## Completed local branch audit

The initial 34 local branches were audited against starting master `7d37c7756`. Thirty-one temporary branches have been removed, after verifying each original tip remained unchanged and became reachable from master. Deletion used `git branch -d` throughout; no force deletion, remote branch deletion or push was performed. All deleted tips remain recoverable from the recorded hashes and master ancestry.

- 10 temporary branches were already ancestors of the starting master.
- 13 AI/archive branches contained only patch-equivalent commits, as confirmed by `git cherry`. History reconciliation in `5bc664eee` retains their original commits while keeping the current tree.
- MediaInfo M2 query behavior was already integrated with subsequent absolute-path assertions, playback/MediaStore providers and corrected options handling. Its one unique commit is retained by the same history merge; reapplying its obsolete hunks would regress the current implementation.
- The six nested JVM branches were integrated through their maximal branch in `9074790cc`, with the compatibility corrections and verification described above.
- OpenCC was integrated in `35969485a`, with its existing public aliases preserved and verified.

| Deleted local branch | Original tip | Audit result |
| --- | --- | --- |
| `codex/ai-android-session` | `3c25a428e43f0cb25ca9d3d52416442f94e6074c` | Patch-equivalent; history retained in 5bc664eee |
| `codex/ai-model-list-docs` | `4a9d6426c641b8b8c534a3780978c960078cac75` | Patch-equivalent; history retained in 5bc664eee |
| `codex/ai-model-selection-smoke` | `ff7425fda1c582a5c772d53f2be1cee742a1354a` | Patch-equivalent; history retained in 5bc664eee |
| `codex/ai-plugin-listing` | `c5164ff6f27334aa5637cf9b8a1416d98939f8e4` | Patch-equivalent; history retained in 5bc664eee |
| `codex/ai-public-ask-evidence` | `b7be25656a116285caa8b5ec6f5792bfc14877c3` | Already an ancestor of the starting master |
| `codex/ai-public-ask-smoke` | `ceb44b8ba272a958bad37ec4f1aae2fd4b29dcbd` | Patch-equivalent; history retained in 5bc664eee |
| `codex/ai-public-chat` | `b0aa6768484d6046551264f69ecc84b527bbb442` | Patch-equivalent; history retained in 5bc664eee |
| `codex/ai-public-chat-docs` | `b98b53cfed5b834b85dce3f90789593ac65fa2ac` | Patch-equivalent; history retained in 5bc664eee |
| `codex/ai-public-stream` | `41bdad042e2f51f28143254f41b6b5ee0948721c` | Patch-equivalent; history retained in 5bc664eee |
| `codex/ai-runtime-route` | `88fdf4d5a2c92b1a7407466b689fb4ebcb66f364` | Patch-equivalent; history retained in 5bc664eee |
| `codex/ai-session-hardening` | `2f6425148550cb4944068bcea0064ae8d187e307` | Patch-equivalent; history retained in 5bc664eee |
| `codex/ai-session-lifecycle` | `9c847383496bcd627a2fd56b0b6bb759297e5ac7` | Patch-equivalent; history retained in 5bc664eee |
| `codex/ai-session-policy` | `325577c825d755d076a51572c894a8445996ad39` | Already an ancestor of the starting master |
| `codex/archive-manager-v14` | `78e870f1c34ca906f5456a612453cd1896e1530e` | Patch-equivalent; history retained in 5bc664eee |
| `codex/archive-manager-v15-output-batches` | `b7ccb055d3f0bd892f630a68f88b4ca512a2083f` | Patch-equivalent; history retained in 5bc664eee |
| `codex/archive-manager-v18-replacement-undo` | `e7a586bb103fa01b3915e9e3c35168fd17d75efc` | Already an ancestor of the starting master |
| `codex/m9-arbitrary-entry` | `018ee731059c1410007ce86b031561911c0e4882` | Functional integration in 9074790cc |
| `codex/m9-jvm-args` | `74d854b59850229797b65294873d9e5c467bfd04` | Functional integration in 9074790cc |
| `codex/m9-jvm-clipboard` | `6ed811106aba02dece8e8266e62dcf5038141815` | Functional integration in 9074790cc |
| `codex/m9-multifile-source` | `4bc6ad07e5240a13b1668d51472119c9b2974811` | Functional integration in 9074790cc |
| `codex/m9-observation-export` | `6985a81b167ceeae84f205598784e4a1d678b113` | Functional integration in 9074790cc |
| `codex/m9-runtime-diagnostic` | `2acd4607853b06d6f75486ef4c54627d5ee90b5d` | Functional integration in 9074790cc |
| `codex/on-device-ai-rename` | `3f526e819f7d2ee5ee2d19406a1479a0385798d5` | Already an ancestor of the starting master |
| `codex/opencc-m3-20260831` | `926d273582194a1b516b2f4f3d8b5ff343197f9d` | Functional integration in 35969485a |
| `feat/mcp-p4` | `e1aa3f813dc1415eca9a7e89781f5d26be26cb53` | Already an ancestor of the starting master |
| `feature/mediainfo-m2-query` | `f07f4294cd5e0bcc177abc7b1a2e45b28facc6c7` | Equivalent current behavior; history retained in 5bc664eee |
| `mcp-p35-docs` | `04c9c16b7a3ab2cccbd82afba37f667a039aba40` | Already an ancestor of the starting master |
| `mcp-p6-conformance-app` | `f13a85ef8e05a9549e6baf2d1a120ac93c22c15c` | Already an ancestor of the starting master |
| `mcp-p6-lifecycle` | `89347f22b8a83b2793b68ba54624cd14752f3994` | Already an ancestor of the starting master |
| `node-m19-3-plugin-owned-metadata` | `8114911e506b4a0a0493f07d477ad7a22e14d902` | Already an ancestor of the starting master |
| `node-media-playback-mediastore` | `7fda1c62984dec2ee01cc076e5e6b0a7a36822e3` | Already an ancestor of the starting master |

Three local branches remain:

| Retained branch | Reason |
| --- | --- |
| `master` | Current integration branch |
| `release/v6.8.0-plugin-managed-candidate` | Release candidate tracking `private/release/v6.8.0-plugin-managed-candidate`; its four distinct release workflow/template commits remain on that branch |
| `remote/master` | Existing local historical reference for the 6.4.1-era baseline; not a temporary task branch |

The release candidate and local historical reference retain their original tips. The optional question about including the release candidate did not receive a reply, so the stated preservation assumption was used.

All six worktree status snapshots match the initial inspection. The clean `D:/idea-projects/AutoJs6-m19-worktree` was detached at its original commit `7fda1c629` before deleting its branch. Its files and directory remain available. The two existing dirty backup worktrees retain their uncommitted edits and untracked tests; the remaining detached worktrees were not changed.

| Worktree | Preserved state |
| --- | --- |
| `D:/idea-projects/AutoJs6` | Committed integration on master |
| `D:/idea-projects/.a6-sdk37-webview-20260913` | Clean, detached at 16ba3901f |
| `D:/idea-projects/.bak/cleanup-20260912/worktrees/.a6-g3-legacy-host` | Existing uncommitted work, detached at fff913caa |
| `D:/idea-projects/.bak/cleanup-20260912/worktrees/AutoJs6-Archive-Ui-Verify` | Existing uncommitted work, detached at c02f4ed6d |
| `D:/idea-projects/AutoJs6-m19-worktree` | Clean, detached at original 7fda1c629 |
| `D:/idea-projects/AutoJs6-mcp-p4-worktree` | Clean, detached at 9c3ba2e52 |

The full pre-audit branch/worktree snapshots, per-branch merge previews, ancestry checks and deletion output are retained locally under the verification directory. The table above preserves every removed branch name and full original tip in version control.
