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
