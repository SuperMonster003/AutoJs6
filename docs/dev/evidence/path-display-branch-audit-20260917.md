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

The six nested `codex/m9-*` branches contain eight commits with real additions: the explicit JVM execution path supports clipboard calls, JSON arguments, safe exception class names, execution observations, bounded multi-file Java source archives and named entry classes. They are integrated through the maximal `codex/m9-arbitrary-entry` branch. The normal application build continues to disable experimental JVM source execution by default.

The merge keeps the current SDK 37 configuration, Python heartbeat handling and R8 fixture digest. Version code 5281 meets the Java provider's existing host requirement. Protocol negotiation now retains compatibility with the installed Kotlin provider's protocol 1.1 / entry API 2, while selecting protocol 1.6 / entry API 4 with a compatible Java provider. Capability fingerprints bind the negotiated entry API; newer request fields and observations remain gated by their protocol versions.

Verification:

- 118 host JVM tests and 71 shared JVM protocol tests passed (189 total, no failures or skips).
- Application and Android test APKs built with the explicitly enabled experimental JVM flag.
- Nine Android integration tests passed with Java Runtime 0.8.3-m9 and Kotlin Runtime 0.7.2-m10 on the owned Android 13 emulator. They cover canonical and named entries, multiple Java files, JSON arguments, foreground clipboard access, safe exception details, execution observations, legacy Kotlin compatibility, and 12 consecutive Java executions.
- The tests passed with both the Java plugin's debug and signed release APKs. Current-provider evidence uses D8 8.13.23, supplied through the test's `jvmD8Version` argument.
- The ten-language changelog incorporates these additions without replacing later mainline entries or backdating the release.

This verification exposed a Java plugin finalization race: resource closure and worker death could both dispatch finalization, allowing a terminal callback to run before its observation was ready. The related Java Runtime repository now gives exactly one caller finalization ownership. Both new deterministic regressions fail before the correction; all 167 plugin JVM tests pass afterward. Debug/test/release assembly, lint (0 errors), signed release collection, three Python tests and Markdown consistency checks also passed. The plugin retains its M9 designation as 0.8.3-m9, build 58. See that repository's `docs/session-finalization-20260917.md` for the diagnosis and verification boundaries.

Local logs include `build-jvm-3.log`, `java-plugin-build.log`, `jvm-device-fixed.log` and `jvm-device-signed-release.log`, with JUnit reports archived separately. Initial failing runs and the host thread dump are retained rather than represented as passing evidence.
