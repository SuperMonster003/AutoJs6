# Terminal navigation, console time settings and Node media, 2026-09-17

> Moved out: the built-in terminal this report describes was extracted into the 3-Shell Terminal plugin (`AutoJs6-Plugin-Three-Shell-Terminal`) and removed from the host on 2026-10-01 (plugin roadmap P1.3). The report is kept as historical evidence; the current contract is described in [the terminal plugin protocol](../terminal-plugin-protocol-v1.md).

This follows [the terminal and manager refinements](terminal-manager-refinements-20260917.md). The working tree was clean at the start, based on `6cfaaba22`. The existing media implementation from `node-media-playback-mediastore` (`7fda1c629`) was reviewed and merged without conflicts for this change.

## Behavior

- Terminal manager New session creates and opens the session immediately. Both New session and the session-row play button launch a distinct terminal activity, keeping the caller and its manager dialog underneath. Back and toolbar Up return to that manager. Normal launches still use single-top reuse when the terminal is already on top.
- An inactive terminal releases its PTY view callbacks and reattaches on resume. Destroying an inactive activity cannot clear callbacks now owned by the foreground terminal. The terminal's manager visibility is saved for activity recreation.
- Tapping the toolbar directory subtitle copies the full current directory, including when the displayed home path is abbreviated as `~`. Android's `/data/user/0` and `/data/data` home aliases both abbreviate correctly. The old Copy home path menu item and its eleven localized strings were removed.
- The MCP vector retains its viewport, size and tint and adds a 6-unit outline to the filled paths for slightly thicker strokes.
- Node project entry sources preserve the real file path and extension in both lifecycle log messages. Package-script and package-bin launch descriptions retain their existing behavior.

Time visibility and time format use separate persisted keys for each surface:

| Surface | Default time visibility |
| --- | --- |
| Editor bottom log panel | Shown |
| Log activity | Shown |
| `globalconsole` element | Hidden |
| `console` element | Hidden |
| Floating `console.show()` window | Hidden |

Existing log-activity preferences are retained. The other surfaces have their own keys. Display-setting UI changes persist; script attributes and explicit console API calls affect their instance without overwriting user preferences for later scripts. Log contents remain shared where appropriate; these preferences belong to the views.

## Media failure and integration

The installed Node runtime plugin was version 1.5.5, which already exposes playback sessions and `media_store`. The host branch being used did not yet provide the corresponding Android implementations. The local media branch supplies the playback and MediaStore providers, permission mappings, registration, disclosure metadata and integration coverage.

Playback reuses the existing script music service, audio focus and foreground media notification, with session controls, end detection and execution cleanup. MediaStore uses structured filters and whitelisted columns, supports app-owned item import, rename, export and deletion, and preserves the distinct `media.playback`, `media.library` and `media.library.mutate` capabilities. Android permissions still apply.

The plugin-owned `media.d.ts`, `media_store.d.ts`, compatibility declarations and type documentation already describe these APIs. No Rhino augment API or declaration contract was changed, so no downstream Rhino documentation/declaration generation was needed.

## Verification

- JDK 21, debug application and Android test APKs built successfully. The final command also ran the terminal and console JVM suites and `JavaScriptSourceTest`: **91 tests passed**, with no failures or skips.
- **Five terminal/console device tests passed** on the owned Android 13 / API 33 x86_64 emulator. Coverage includes 30 startup cancellations, shell state and unsubmitted input across sessions, selection snapshots, New session / Resume / Back from an existing terminal's manager, restored PTY input after returning, full-path clipboard copying and all five independent time preference scopes.
- **22 Node device tests passed**: media project acceptance (2), media bridge integration (10), provider registry (5), bridge conformance (5). No tests were skipped in the successful run.
- Both reported projects were copied read-only from the user's device to the owned emulator under `/storage/emulated/0/Scripts/TestQ`. Their `main.cjs`, project/package metadata and audio fixtures were kept intact. The acceptance tests resolve their configured project entry, execute through the host script engine, compare every non-empty line of `expected-output.txt`, and check both lifecycle log paths.
- `media-library` passed capabilities, import, query, rename, export, scan and deletion, ending with `sample.media-library=PASS`. The imported MediaStore item was removed by the sample's cleanup.
- `media-playback` passed start, pause, seek/resume, natural completion, stop, stale-session status and compatibility music calls, ending with `sample.media-playback=PASS`.
- Manual home-drawer checks confirmed immediate terminal launch and return to the same manager for New session and Resume. The directory subtitle copied its path, grouped menus remained intact, and the MCP icon's size, tint and heavier strokes were visually checked. All manually created terminal sessions were closed afterward.
- Ten-language changelog JSON files were updated for 6.8.0 with date `2026/09/17`; `.python/generate_markdown.py` regenerated the READMEs and application changelogs. JSON/XML parsing, ASCII punctuation in changed changelog entries, matching default/English shared strings, ordering of the eight new non-translatable preference keys, and `git diff --check` passed.

The first device runs exposed a terminal home-path alias mismatch, then an incorrect test-only project launch route and missing MediaInfo / ML Kit test plugins. The alias and test route were corrected and the test environment completed before the successful runs above. Verification used the isolated emulator; no application was installed or run on the user's phone.

Observed lifecycle logs included:

```text
开始运行 [/storage/emulated/0/Scripts/TestQ/media-library/main.cjs].
[/storage/emulated/0/Scripts/TestQ/media-library/main.cjs] 运行结束 (用时 0.830 秒)
开始运行 [/storage/emulated/0/Scripts/TestQ/media-playback/main.cjs].
[/storage/emulated/0/Scripts/TestQ/media-playback/main.cjs] 运行结束 (用时 3.027 秒)
```

## Reproduction and artifacts

Build:

```powershell
.\gradlew.bat :app:assembleAppDebug :app:assembleAppDebugAndroidTest :app:testAppDebugUnitTest --tests 'org.autojs.autojs.core.terminal.*' --tests 'org.autojs.autojs.core.console.*' --tests 'org.autojs.autojs.script.JavaScriptSourceTest' --console=plain
```

The media project test accepts `-e mediaProjectRoot /storage/emulated/0/Scripts/TestQ`; that directory must contain the plugin-owned `media-library` and `media-playback` sample folders. The regression run used Node Runtime 1.5.5, MediaInfo 2.1.3, ML Kit OCR 1.0.3 and ML Kit Barcode 1.0.4. The last three also satisfy pre-existing conformance test dependencies.

Local raw evidence is under `build/verification/terminal-manager-navigation/`: `build-final.log`, `terminal-console-tests.log`, `media-tests-2.log`, `media-project-logcat.log`, `changelog-generation.log`, fixture copies and screenshots. JVM XML reports are under `app/build/test-results/testAppDebugUnitTest/`.

Final debug APKs are under `app/build/outputs/apk/app/debug/`. SHA-256:

| APK | SHA-256 |
| --- | --- |
| `autojs6-v6.8.0-arm64-v8a.apk` | `1a8a5cebd07db734101f38fe38c9a973d1fd67aafdb5d0feeee5e067e7b9622a` |
| `autojs6-v6.8.0-x86_64.apk` | `cbd1d7078d949614ffe6534ef69f421723d4e217a1346a0bdf5a38b6f801c36d` |
