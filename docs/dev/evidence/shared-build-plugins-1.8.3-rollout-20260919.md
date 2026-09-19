# Shared build plugins 1.8.3 publication and consumer rollout

Date: 2026-09-19. Both shared plugin IDs are pinned to 1.8.3 in AutoJs6 and all
44 sibling Android plugin repositories. Consumer commits are local; this work
published the shared Gradle plugin release only.

## Publication

The shared repository changes were committed separately as `0c5e428` (exclude
JVM test assemblies from APK verification) and `65501e1` (SDK XML v4 support
while preserving AGP/AAPT2 identity). The annotated `v1.8.3` tag points at the
latter commit.

- [Publication workflow](https://github.com/SuperMonster003/AutoJs6-Gradle-Platform-Versions/actions/runs/35413646734): succeeded for Maven Central and Gradle Plugin Portal.
- [CI workflow](https://github.com/SuperMonster003/AutoJs6-Gradle-Platform-Versions/actions/runs/35413646141): succeeded.
- [GitHub release](https://github.com/SuperMonster003/AutoJs6-Gradle-Platform-Versions/releases/tag/v1.8.3): published.
- Both public repositories returned HTTP 200 for the implementation POM/JAR and both plugin marker POMs. Exact URLs and checksums are in the adjacent JSON receipt.
- The implementation JAR SHA-256 in both repositories is `d2498690b3e163ffae1f7aebc31d4749935d75c142b2324f2878ac79fd6e4e71`.
- A fresh independent consumer using only public repositories and `--refresh-dependencies` built a real APK with resources, assembled JVM tests and passed 16 KB native alignment verification.

Shared plugin verification: 94 JVM tests, 21 scraper tests, all ten translation
files, isolated Maven publication, the headless sample and the SDK XML fixture.

## Consumer validation

Environment: Windows, JDK 21.0.1, Android SDK API 37 and Build Tools 37.0.0.
Each repository used its own Gradle wrapper. The plugin checks used the following
temporary command-line metadata, as prescribed by repository build guidance:

```text
-Djava.vendor=Eclipse Adoptium
-Djava.vendor.version=Temurin-21.0.12.1+1
-Didea.active=true
-Didea.version=2026.2.3
-Didea.vendor.name=JetBrains
-Dandroid.studio.latest.known.compatible.agp.version=9.1.0
-Pautojs.gradle.build.number.auto.increment.enabled=false
```

These flags do not change the installed JDK, which remained 21.0.1. Each plugin
built a representative Debug APK, its AndroidTest APK and JVM test assembly,
ran that variant's JVM tests and Lint, and checked generated Markdown plus the
repository's applicable Python checks. The temporary verification task asserted
the resolved AGP 9.1.1, sdklib 32.2.1 and common 32.1.1 coordinates, the published
1.8.3 plugin JAR, and the absence of an APK alignment finalizer on JVM test
assembly. All 44 checks passed without the SDK processing warning. Actual APK
assembly retained native page alignment verification.

The [verification init script](verify-platform-1.8.3.init.gradle) is retained
beside this report. From each plugin repository, the Gradle invocation was
`./gradlew.bat --no-daemon --max-workers=2 --console=plain --init-script
<AutoJs6>/docs/dev/evidence/verify-platform-1.8.3.init.gradle
verifyPlatformRollout`, together with the flags above and `JAVA_HOME` pointing
at JDK 21. The DEX Compiler recheck additionally loaded its existing
`scripts/r4-d8-upgrade/check-boot-classpath.init.gradle` regression script.

The Angus Mail mail-core tests, QuickJS API tests and Bun APK runtime integrity
checks were also included where available. App test counts below exclude those
additional modules and Python suites.

Every plugin's VERSION_BUILD equals its reachable commit count after the
upgrade commit. Existing application version names were preserved. All ten
current-version changelogs and generated Markdown were updated; executable
version assertions and current repository guidance were synchronized.

Angus Mail and Readium were validated in isolated worktrees while concurrent
feature work continued. Their upgrades were rebased on the completed feature
commits, the relevant checks were rerun, and the original branches were advanced
with fast-forward merges. The table identifies the tested upgrade commit;
the JSON also records the observed branch head and build count when later
independent commits exist.

## Corrections found during validation

- APK Builder Template requires PLUGIN_VERSION_BUILD to mirror VERSION_BUILD; both were advanced together.
- DEX Compiler's AGP 9.1 bootClasspath getter reads finalized compile options before returning a Provider. Its getter is now deferred as well as its value. The existing early-Test-task regression check confirmed the exact AGP-selected android.jar as both a JVM argument and a tracked task input.
- The Angus Mail punctuation check found an Arabic comma in its existing changelog and an Arabic semicolon after integration with a concurrent mail-core update. Both were replaced with ASCII punctuation and the documents and tests were rerun.
- Repository Python contracts that asserted an older shared plugin version were synchronized and rerun.

## Host validation

Public 1.8.3 artifacts were used for `:app:compileInrtReleaseKotlin`, `:app:assembleAppDebug`, `:app:assembleAppDebugUnitTest` and `:app:assembleAppDebugAndroidTest`. The Inrt Release Kotlin compilation passed with the configured 8 GiB Kotlin heap. The App Debug APK passed 16 KB native alignment verification; JVM test assembly had no APK alignment finalizer. Both JVM and Android instrumentation test sources compiled.

The full host JVM run discovered 2,992 tests: 2,986 passed, 5 were skipped, and `IdleWaiterTest.aBusyStreamTimesOut` failed its assumption of receiving at least five events within a 150 ms timeout under concurrent build load. Its assertions for a non-quiet result and elapsed timeout had passed. The entire `IdleWaiterTest` class was rerun without source changes: all seven tests passed. The targeted test recheck and AndroidTest APK assembly completed successfully. The first full-run result remains recorded as a failure, separately from the successful recheck.

There were no Kotlin compiler warnings or SDK processing warnings in the initial public-artifact host build. Existing Java deprecation/unchecked-operation notes and Gradle deprecation summaries remain. A JDK thread snapshot during the longer test run showed the worker writing the central directory of an 18,184-entry ZIP fixture, rather than waiting in a deadlock; the shell-launched process had no active IntelliJ debugger session.

## Plugin commits

App JVM totals: 3590 discovered, 8 skipped, 0 failures, 0 errors.

| Plugin repository | Upgrade commit | Upgrade build | App JVM tests | Skipped | Variant |
| --- | --- | ---: | ---: | ---: | --- |
| AutoJs6-Plugin-Accessibility-Compat | `f5ad173972` | 19 | 33 | 0 | Debug |
| AutoJs6-Plugin-Ace-Editor | `903d626b01` | 103 | 169 | 0 | Debug |
| AutoJs6-Plugin-Angus-Mail | `8f02dbdce6` | 21 | 31 | 0 | Debug |
| AutoJs6-Plugin-APK-Builder-Template | `ff2250e456` | 45 | 38 | 0 | Debug |
| AutoJs6-Plugin-APK-Inspector | `504bff9b0c` | 40 | 227 | 0 | Debug |
| AutoJs6-Plugin-Archive-Manager | `4b0bf9dadf` | 108 | 233 | 8 | Debug |
| AutoJs6-Plugin-Bun-Runtime | `b0e1a41752` | 115 | 59 | 0 | Debug |
| AutoJs6-Plugin-DEX-Compiler | `82bd137429` | 46 | 90 | 0 | Debug |
| AutoJs6-Plugin-File-Inspector | `f04fb8d1c4` | 18 | 61 | 0 | Debug |
| AutoJs6-Plugin-HTML-Previewer | `85af267e23` | 24 | 70 | 0 | Debug |
| AutoJs6-Plugin-Image-Quantization | `e514280641` | 25 | 4 | 0 | Debug |
| AutoJs6-Plugin-Image-Tools | `3aa918e18c` | 15 | 101 | 0 | Debug |
| AutoJs6-Plugin-Image-Viewer | `ee1b0ff003` | 14 | 53 | 0 | Debug |
| AutoJs6-Plugin-ImGui | `5d84b5fa05` | 16 | 30 | 0 | Debug |
| AutoJs6-Plugin-Java-Runtime | `d75c940b46` | 59 | 167 | 0 | Debug |
| AutoJs6-Plugin-Kotlin-Runtime | `98c7b31657` | 29 | 154 | 0 | Debug |
| AutoJs6-Plugin-Lua-Runtime | `a86dce7929` | 78 | 62 | 0 | ProviderDebug |
| AutoJs6-Plugin-Markdown-Previewer | `973e0d6ff1` | 20 | 66 | 0 | Debug |
| AutoJs6-Plugin-MCP-Server | `eb04106d96` | 67 | 235 | 0 | Debug |
| AutoJs6-Plugin-MediaInfo | `b9b8596b28` | 52 | 29 | 0 | Debug |
| AutoJs6-Plugin-MLKit-Barcode | `54ceceadc1` | 18 | 16 | 0 | Debug |
| AutoJs6-Plugin-MLKit-OCR | `c95741d683` | 17 | 16 | 0 | Debug |
| AutoJs6-Plugin-NodeJs-Runtime | `d9a27bff99` | 220 | 96 | 0 | Debug |
| AutoJs6-Plugin-Offline-Docs | `ba149c354d` | 45 | 2 | 0 | Debug |
| AutoJs6-Plugin-OpenCC | `3394b56aac` | 104 | 18 | 0 | Debug |
| AutoJs6-Plugin-OpenCV | `35976c1cbd` | 23 | 10 | 0 | Debug |
| AutoJs6-Plugin-Paddle-OCR-PP-OCRv3 | `70c3734770` | 23 | 16 | 0 | Debug |
| AutoJs6-Plugin-Paddle-OCR-PP-OCRv4 | `0b33f1650a` | 23 | 16 | 0 | MobileDebug |
| AutoJs6-Plugin-Paddle-OCR-PP-OCRv5 | `1a014152eb` | 25 | 16 | 0 | MobileDebug |
| AutoJs6-Plugin-Paddle-OCR-PP-OCRv6 | `8073181aae` | 27 | 16 | 0 | SmallDebug |
| AutoJs6-Plugin-Pinyin | `1989f3d204` | 19 | 13 | 0 | Debug |
| AutoJs6-Plugin-Pinyin4j | `3f4753917f` | 26 | 17 | 0 | Debug |
| AutoJs6-Plugin-Python-Runtime | `cd7faa4d14` | 111 | 56 | 0 | Debug |
| AutoJs6-Plugin-QuickJS-Runtime | `c60d62124e` | 15 | 8 | 0 | Debug |
| AutoJs6-Plugin-R8-Compiler | `888a945c52` | 21 | 56 | 0 | Debug |
| AutoJs6-Plugin-Rapid-OCR-PP-OCRv3 | `cda5e5032d` | 26 | 16 | 0 | Debug |
| AutoJs6-Plugin-Rapid-OCR-PP-OCRv6 | `ccdd3731e6` | 28 | 16 | 0 | Debug |
| AutoJs6-Plugin-Readium-EPUB-Reader | `de0981655b` | 17 | 85 | 0 | Debug |
| AutoJs6-Plugin-Screen-Color-Picker | `cfe757fd9e` | 16 | 42 | 0 | Debug |
| AutoJs6-Plugin-Three-Ember-Player | `3eb743a6de` | 28 | 138 | 0 | Debug |
| AutoJs6-Plugin-Three-Stone-AI | `4654e9759d` | 192 | 359 | 0 | Debug |
| AutoJs6-Plugin-Three-Terra-Player | `34d2ae9449` | 38 | 104 | 0 | Debug |
| AutoJs6-Plugin-TypeScript-Engine | `5d6f172936` | 82 | 534 | 0 | Debug |
| AutoJs6-Plugin-Yolo-NCNN | `6e7ef6d34b` | 39 | 12 | 0 | Debug |

## Evidence boundaries

Android instrumentation sources and APKs were built; device tests were not run
as part of this build tooling upgrade. Existing unrelated compiler, Lint and
Gradle deprecation diagnostics are not claimed to be eliminated. The host's
initial full JVM run and targeted recheck are recorded separately above.

Detailed local logs remain in the ignored
`build/warning-verification/rollout-183/` directory. The adjacent JSON receipt
records public artifact checksums, per-repository check results, test counts
and exact commit IDs. The initial warning fixes are documented in
[the build warning cleanup report](build-warning-cleanup-20260919.md).
