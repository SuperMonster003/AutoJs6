# ACE Release Device Report - Sony XQ-AT72

This report records the physical-device release gate for the ordinary ACE
experimental editor. Do not mark the release candidate accepted until every P0
path has a recorded result.

## Device

- Device: Sony XQ-AT72
- Android version: 12
- API level: 31
- Android System WebView version: com.google.android.webview 145.0.7632.120
- Repository baseline commit: 4a4563e4
- Installed app: 6.7.1 Alpha5, versionCode 3824, DEBUGGABLE TEST_ONLY
- Installed app update time: 2026-06-16 01:32:18
- Test package: org.autojs.autojs6.ace
- Plugin permission declaration: `org.autojs.autojs6.ace.permission.PLUGIN`;
  requested plugin permission remains `org.autojs.permission.PLUGIN`
- ACE enabled: yes, `key_$_editor_engine=ace`; static default remains Native
- LSP enabled: false, `key_$_ace_lsp_enabled=false`
- Clean host enabled: yes
- IME: com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME
- Installed IMEs: Google voice input, Google LatinIME, Baidu OEM IME
- Navigation mode: 2 (gesture navigation setting)
- Display size: 1096x2560
- Display density: 420 dpi
- Font scale: 1.0
- Theme: dark; device night mode is yes and app `key_$_night_mode_enabled=true`
- Font: JetBrains Mono default; no `key_$_ace_editor_font` override persisted
- Font size: 14sp default; no `key_$_editor_text_size` override persisted

## Automated Gate

| Command | Result | Notes |
| --- | --- | --- |
| `node tools/ace-ci/check-ace-release-gate.mjs` | PASS | Runs the full ACE editor CI gate first, then release static invariants. |
| `node tools/ace-ci/check-ace-editor-gate.mjs` | PASS | Boundary, surface, API model, Pro-only, completion, d.ts, LSP client, and tooltip checks passed. |
| `git diff --check` | PASS | Exit code 0 with no whitespace errors; follow-up doc-only diff check emitted LF/CRLF warnings only. |

## Manual Gate Summary

| Area | Result | Notes |
| --- | --- | --- |
| Native default | STATIC PASS | Release gate confirms Native resource/default path; device preference is ACE explicit opt-in. |
| LSP default-off | DEVICE PASS | Device preference has `key_$_ace_lsp_enabled=false`. |
| VisualEditorAce unavailable | STATIC PASS | Confirmed by release gate; device UI sweep still TBD. |
| First paint | DEVICE SMOKE PASS | File-manager open sampling showed coherent ACE surfaces in dark and light themes; see Gate 2 notes. |
| File-manager open repeat | DEVICE SMOKE PASS | 10 dark-theme opens and 5 light-theme opens of `ace-release-gate.js` reached `EditActivity`; no fallback/fatal signal in logcat. |
| Keyboard show/hide | DEVICE SMOKE PASS | Three IME show/hide rounds kept `EditActivity` focused; see Gate 3 notes. |
| Keyboard stress | DEVICE SMOKE PASS | Debug IME stress helper completed 20 cycles; ordinary `EditActivity` show/hide stress completed 20/20 cycles on small, 100-line, and 1000-line samples; see Gate 4 notes. |
| Click anchor accuracy | DEVICE SMOKE PASS | Top, middle, and lower visible-line taps landed on the observed target rows with keyboard hidden before each tap. |
| Lower viewport tap anchor | DEVICE SMOKE PASS | Low-screen taps kept the caret visible just above the IME/symbol toolbar and did not fallback ACE. |
| Long-press selection | DEVICE SMOKE PASS | Long-press selected `copyAlphaTarget` and displayed the ACE host ActionMode menu; see Gate 5 notes. |
| Double-tap selection | DEVICE SMOKE PASS | Double-tap selected `doubleTapTarget`; copying the selection and reading the clipboard returned `doubleTapTarget`. |
| Copy/paste | DEVICE SMOKE PASS | Copy, single-line paste, multi-line paste, and Unicode paste passed. Drag handles and exact CJK target composition remain TBD. |
| CJK composition | DEVICE PARTIAL PASS | Gboard Pinyin composition entered candidates, survived cursor move and keyboard hide without ACE fallback, and committed CJK text through the keyboard path; exact `toast("你好")` candidate selection remains a human hand-test item. |
| Completion accept | DEVICE SMOKE PASS | Instrumentation smoke accepted `toast`, `app.launch`, `files.read`, `images.findImage`, and `device.getBattery` through the ACE popup path; see Gate 6 notes. |
| Tooltip fallback | DEVICE SMOKE PASS | Instrumentation smoke showed local tooltip DOM/render state with `markdownRenderer=safe-subset`; static source checks reject `fetch`, XHR, and raw hover `innerHTML`. |
| Rotation | DEVICE SMOKE PASS | Instrumentation smoke typed unsaved text in ACE, requested IME show, rotated landscape and portrait, and preserved text, dirty state, and cursor visibility; see Gate 7 notes. |
| Native/ACE switching | DEVICE SMOKE PASS | Instrumentation smoke saved, switched ACE -> Native -> ACE, preserved text and clean state, and produced no ACE fallback diagnostics; see Gate 7 notes. |
| TextMirror fallback | DEVICE SMOKE PASS | Ready-timeout instrumentation entered unsaved text before fallback and verified Native received the TextMirror text with dirty state preserved. |
| Ready-timeout fallback | DEVICE SMOKE PASS | `forceReadyTimeout` reached Native with `READY_TIMEOUT` diagnostics and runtime event history. |
| JS-init fatal fallback | DEVICE SMOKE PASS | `forceJsInitThrow` reached Native with loaded file text and `JS_INIT_ERROR` diagnostics. |
| Renderer-gone fallback | DEVICE SMOKE PASS | `forceRendererGoneFallback` reached Native with loaded file text and `RENDER_PROCESS_GONE` diagnostics without querying a dead WebView. |
| Asset-missing fallback | DEVICE SMOKE PASS | `forceAssetMissing` routed directly to Native and preserved an `ASSET_MISSING` diagnostics snapshot. |
| Large-file Native routing | DEVICE SMOKE PASS | Native large-file instrumentation loaded a script larger than the 512 KiB threshold through the Native editor, preserving length, prefix/suffix text, clean state, and Native engine routing. |
| Native editor regression | DEVICE SMOKE PASS | Instrumentation opened Native editor, edited, showed/hidden IME, saved, searched/replaced, toggled breakpoints, opened a large file, and ran a script without ACE fallback dependency. |
| Diagnostics export | DEVICE SMOKE PASS | `EditorView.copyAceDiagnosticsToClipboard()` exported ACE diagnostics from a real `EditActivity` without including the loaded script's unique identifier, string literal, or function name. |
| Publishing cleanup audit | STATIC PASS | See `docs/dev/ace-release-publishing-cleanup.md`; final package/label restore is still pending before merge or non-test release. |

## Diagnostics Snapshots

Record copied ACE diagnostics for every failure or ambiguous result.

| Scenario | Diagnostics reference | Notes |
| --- | --- | --- |
| Gate 2 file-manager open smoke | N/A | No failure diagnostics were produced; screenshot samples were reviewed during the run. |
| Gate 3 IME/tap-anchor smoke | N/A | No failure diagnostics were produced; screenshot samples and logcat were reviewed during the run. |
| Gate 4 keyboard stress | Inline Gate 4 notes | Temporary pasted diagnostics from the 1000-line sample: `aceHealthState=RUNNING`, `lastFailureType=unknown`, `lastImeState=HIDDEN`, `maxBridgeEventWindowCount=18` in a 1000 ms window, below the storm threshold of 60; event history had no `bridge_event_storm` and heartbeat checks were OK. |
| Gate 5 ActionMode bridge mismatch | Inline Gate 5 notes | A development-only 7-argument `startActionMode` bridge attempt produced `Error invoking startActionMode: Method not found`; the final implementation restored the six-argument bridge and passed the copy/paste smoke below. |
| Gate 6 completion/tooltip/LSP smoke | Instrumentation PASS | `AceCodeEditorSmokeTest` ran 5/5 tests on Sony XQ-AT72 with no failures, including popup completion accept, tooltip local rendering, LSP disabled state, and LSP enabled no-server static fallback. |
| Gate 7 rotation/restore/switching smoke | Instrumentation PASS | `AceCodeEditorSmokeTest` ran 6/6 tests on Sony XQ-AT72 with no failures, including `EditActivity` rotation, background restore, save, ACE -> Native -> ACE switching, dirty-state checks, cursor visibility checks, and no ACE fallback diagnostics. |
| Gate 8 fallback hook smoke | Instrumentation PASS | `AceCodeEditorSmokeTest` ran 8/8 tests on Sony XQ-AT72 with no failures, including ready-timeout TextMirror preservation, startup fatal fallback hooks, and diagnostics checks for `READY_TIMEOUT`, `JS_INIT_ERROR`, `RENDER_PROCESS_GONE`, and `ASSET_MISSING`. |
| Gate 9 Native regression smoke | Instrumentation PASS | `NativeEditorRegressionSmokeTest` ran 2/2 tests on Sony XQ-AT72 with no failures, including Native edit/save/search/replace/breakpoint/run coverage and large-file loading above the 512 KiB threshold. |
| Diagnostics export smoke | Instrumentation PASS | `AceCodeEditorSmokeTest` ran 9/9 tests on Sony XQ-AT72 with no failures after adding the diagnostics export no-script-text check. |

## Gate 2 Notes

- Time: 2026-06-15 23:14-23:20 CST.
- Test script: `/sdcard/Scripts/ace-release-gate.js`, 119 B, created for
  this gate and removed after the run.
- Dark theme: 10 opens from the AutoJs6 file manager edit icon. Screenshots
  were sampled at about 350 ms and 1200 ms after each tap; `EditActivity`
  focus was observed in 10/10 rounds.
- Light theme: temporarily set the test package prefs to
  `key_$_night_mode_enabled=false`,
  `key_$_auto_night_mode_enabled=false`, and
  `key_$_night_mode=key_$_night_mode_always_off`; restored the original prefs
  immediately after the five light-theme rounds. `EditActivity` focus was
  observed in 5/5 rounds.
- Reviewed dark samples from rounds 1, 5, and 10 and light samples from rounds
  1 and 5. The samples showed the correct file content, coherent ACE editor
  background, and no white/black/half-initialized editor surface.
- Logcat during both runs had no `AndroidRuntime FATAL EXCEPTION`, ACE
  fallback, renderer-gone, or fatal ACE signal. IME remained hidden.
- This is an ADB screenshot-sampling smoke result, not a high-frame-rate video
  capture. Treat any later human-observed transition flash as a new Gate 2
  finding.

## Gate 3 Notes

- Time: 2026-06-15 23:27-23:32 CST.
- Test script: `/sdcard/Scripts/ace-gate3-anchors.js`, 120 labeled
  `console.log("anchor-line-NNN")` rows, created for this gate and removed
  after the run.
- Tap-anchor smoke:
  - Opened the script through `EditIntentActivity`; initial state had
    `mInputShown=false` and `EditActivity` focused.
  - With the keyboard hidden before each tap, tapped upper, middle, and lower
    visible code rows.
  - Screenshots showed the upper tap on line 2, the middle tap around line 19,
    and the low tap on line 38 just above the IME/symbol toolbar.
  - Each tap showed the IME and kept `EditActivity` focused.
- IME show/hide smoke:
  - Ran three explicit cycles from a focused ACE editor.
  - Each cycle had `mInputShown=true` before hide,
    `mInputShown=false` after Back, and `mInputShown=true` after a low
    viewport tap.
  - `EditActivity` stayed focused after every hide and show step.
  - Hide screenshots showed the editor expanding back to the symbol toolbar
    with no stale bottom padding. Show screenshots kept the caret visible above
    the IME after low-viewport taps.
- Logcat during Gate 3 had no `AndroidRuntime FATAL EXCEPTION`, ACE fallback,
  renderer-gone, or fatal ACE signal.
- Device cleanup left AutoJs6 ACE at `MainActivity` with `mInputShown=false`;
  the temporary test script was removed.
- This is an ADB screenshot-sampling smoke result. Keep any later
  human-observed tap-anchor mismatch or transition flash as a new Gate 3
  finding.

## Gate 4 Notes

- Time: 2026-06-15 23:38-23:54 CST.
- Isolated debug helper:
  - Direct shell launch of `AceEditorDebugActivity` was blocked because the
    activity is not exported, and `run-as` launch was rejected by
    ActivityManager UID ownership checks.
  - Entered the helper through Settings -> About app and developer ->
    long-press app icon -> Developer Options -> ACE editor debug.
  - Pressed `IME STRESS`; the status reached
    `IME stress finished: 20 cycles`, with `AceEditorDebugActivity` still
    focused. The helper uses a 122-line stress sample and jumps among line 1,
    line 48, and line 96.
- Ordinary editor stress:
  - `/sdcard/Scripts/ace-gate4-small.js`: 20/20 tap-to-show and Back-to-hide
    IME cycles passed in `EditActivity`.
  - `/sdcard/Scripts/ace-gate4-100.js`: 101 lines by `wc -l`; 20/20
    tap-to-show and Back-to-hide cycles passed in `EditActivity`.
  - `/sdcard/Scripts/ace-gate4-1000.js`: 1001 lines by `wc -l`; 20/20
    tap-to-show and Back-to-hide cycles passed in `EditActivity`.
  - Every ordinary editor cycle kept `EditActivity` focused, observed
    `mInputShown=true` after tap and `mInputShown=false` after Back, and ended
    with ACE still active.
- Diagnostics:
  - Copied ACE diagnostics from the editor More menu, pasted them into
    `/sdcard/Scripts/ace-gate4-diagnostics.txt`, saved the temporary file, and
    read it back from the device before cleanup.
  - The pasted diagnostics reported `aceHealthState=RUNNING`,
    `lastFailureType=unknown`, `lastImeState=HIDDEN`,
    `fileSizeBytes=22924`, `largeFilePolicy=auto;thresholdBytes=524288`,
    `currentBridgeEventWindowCount=2`, `lastBridgeEventWindowCount=5`, and
    `maxBridgeEventWindowCount=18`. The code threshold for
    `bridge_event_storm` is 60 events per 1000 ms window.
  - Runtime event history contained resize and heartbeat records, including
    heartbeat checks with `ok=true`, and no `bridge_event_storm`.
  - Broad logcat greps matched unrelated Google IME speech fallback messages.
    A narrower AutoJs6/ACE scan found only Google IME start-input records for
    `org.autojs.autojs6.ace`, with no `AndroidRuntime FATAL EXCEPTION`, ACE
    fallback, renderer-gone, heartbeat fallback, or app fatal signal.
- Debug-only observation:
  - After hiding the IME inside `AceEditorDebugActivity`, repeated ADB taps on
    the already-focused debug sample did not reopen the IME until switching
    back to ordinary `EditActivity`. The ordinary editor path used by release
    users passed the 20-cycle small/100-line/1000-line stress runs above.

## Gate 5 Notes

- Time: 2026-06-16 00:40-00:49 CST.
- Test script: `/sdcard/Scripts/ace-gate5-selection.js`, containing
  `copyAlphaTarget`, paste marker rows, and a CJK marker row.
- Selection ActionMode fix validation:
  - Long-pressed `copyAlphaTarget` in ACE. The word was selected and the host
    ActionMode menu displayed `复制`, `粘贴`, `全选`, `删除行`, and `复制行`.
  - Copied the selected text through the ActionMode `复制` item.
  - Read the system clipboard back through
    `/sdcard/Scripts/ace-gate5-readclip-probe.js`; the file
    `/sdcard/Scripts/ace-gate5-clip-probe.txt` contained `copyAlphaTarget`.
  - Set the clipboard to `gate5PasteSingle`, long-pressed
    `copyAlphaTarget`, tapped ActionMode `粘贴`, saved, and read the script
    back from `/sdcard/Scripts/ace-gate5-selection.js`. The saved line was
    `const copy_source = "gate5PasteSingle copyBetaTarget";`.
  - A focused logcat scan after the passing copy/paste run had no
    `AndroidRuntime FATAL EXCEPTION`, `fatalError`, ACE fallback,
    renderer-gone, `JS_RUNTIME_ERROR`, or `bridge_event_storm` match.
- Extended Gate 5 validation:
  - Time: 2026-06-16 01:34-01:48 CST, after reinstalling
    `org.autojs.autojs6.ace` versionCode 3824 on Sony XQ-AT72.
  - Double-tapped `doubleTapTarget`, copied the selected text, and read the
    system clipboard through `/sdcard/Scripts/ace-gate5-readclip-probe.js`.
    The probe file contained `doubleTapTarget`.
  - Tried two ADB drag gestures from the selected `dragAlphaTarget` range
    toward the following token. The visible selection did not reliably expand,
    and copying afterward still returned `dragAlphaTarget`; selection-handle
    dragging remains unpassed and needs direct hand testing.
  - Set the clipboard from a foreground AutoJs6 script to
    `gate5MultiLineA`, `gate5MultiLineB`, and `gate5MultiLineC` separated by
    newlines, long-pressed `pasteMultiAnchor`, tapped ActionMode `粘贴`, saved,
    and read `/sdcard/Scripts/ace-gate5-selection.js` back. The saved
    `paste_multi` value contained the three pasted lines.
  - Set the clipboard to `中文🙂 "quotes" \\ backslash`, long-pressed
    `pasteUnicodeAnchor`, tapped ActionMode `粘贴`, saved, and read the script
    back. The saved `paste_unicode` line contained the Chinese text, emoji,
    double quotes, and backslashes.
  - Used `/sdcard/Scripts/ace-gate5-cjk-composition.js` and Gboard Pinyin.
    `adb shell input text nihao` entered an uncommitted composition with
    visible candidates including `你好`. Pressing Back while the composition was
    uncommitted canceled it without changing focus away from `EditActivity` or
    producing an ACE fallback/fatal log signal.
  - Repeated the uncommitted composition smoke, tapped another code line to
    move the cursor, then hid the keyboard. The editor stayed focused, the
    uncommitted text did not pollute the saved file, and the focused log scan
    had no `AndroidRuntime FATAL EXCEPTION`, `fatalError`, ACE fallback,
    renderer-gone, `JS_RUNTIME_ERROR`, or `bridge_event_storm` match.
  - A screen-keyboard coordinate run in `/sdcard/Scripts/ace-gate5-cjk-ui.js`
    committed CJK text through Gboard, but the ADB-driven second candidate
    selection resolved to `你haozip` rather than the intended `你好`. Treat this
    as CJK composition partial coverage only; exact `toast("你好")` entry still
    needs human hand testing.
- Development-only finding fixed before the passing smoke:
  - A first implementation added a seventh `selectedText` argument to
    `autojs.startActionMode(...)`; WebView's JavaScriptInterface reported
    `Error invoking startActionMode: Method not found`, and diagnostics showed
    `aceHealthState=FALLING_BACK` with `lastFailureType=JS_RUNTIME_ERROR`.
  - The final implementation restored the original six-argument bridge and
    sends the lightweight selection state immediately before starting
    ActionMode.
- Remaining Gate 5 items not yet completed: drag selection handles and exact
  CJK candidate entry such as `toast("你好")`.

## Gate 6 Notes

- Time: 2026-06-16 02:06-02:07 CST.
- Command:
  `.\gradlew.bat :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.ui.edit.editor.ace.AceCodeEditorSmokeTest"`
- Result: PASS on Sony XQ-AT72; 5 tests completed, 0 skipped, 0 failed.
- Completion accept smoke:
  - The instrumentation test opened `AceEditorDebugActivity`, waited for ACE
    ready/first-paint, then drove the real ACE autocomplete popup and
    `insertMatch` path from WebView JavaScript.
  - Static completions accepted through the popup inserted the expected
    prefixes for `toast`, `app.launch`, `files.read`, `images.findImage`, and
    `device.getBattery`.
  - The final diagnostics snapshot stayed `aceHealthState=RUNNING` with no
    `lastFailureType` and no runtime event history entry containing
    `fallback`.
- Tooltip and LSP default-off smoke:
  - With `key_$_ace_lsp_enabled=false`, `window.AutoJsAce.getLspState()`
    reported `enabled=false`, `state=disabled`, `serverReady=false`, and
    `completionProvider=static-fallback` / `hoverProvider=static-fallback`.
  - `window.AutoJsAce.showLspTooltip(0, 5)` for `app.launch(...)` returned
    true. Tooltip state reported `domCreated=true`, `markdownLoaded=true`, and
    `markdownRenderer=safe-subset`.
  - Static source gates continue to reject tooltip `fetch`, XHR, and raw hover
    `innerHTML` assignment.
- LSP alpha no-server fallback smoke:
  - The instrumentation test enabled `AceEditorLspPreferences` before launching
    a fresh ACE editor instance.
  - `window.AutoJsAce.getLspState()` reported `enabled=true`,
    `state=configured-no-server`, `transport=unavailable`,
    `fallback=static-completion`, `serverReady=false`, and
    `serverAvailable=false`.
  - Completion accept still used the static fallback path and did not produce
    ACE fallback or fatal diagnostics.

## Gate 7 Notes

- Time: 2026-06-16 08:36-08:37 CST.
- Command:
  `.\gradlew.bat :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.ui.edit.editor.ace.AceCodeEditorSmokeTest"`
- Result: PASS on Sony XQ-AT72; 6 tests completed, 0 skipped, 0 failed.
- Rotation and restore smoke:
  - The instrumentation test opened a real `EditActivity` with ACE selected and
    loaded a temporary JavaScript file from the app cache.
  - It inserted an unsaved marker through the active ACE engine, requested IME
    show best-effort through the editor input view, and verified that ACE text,
    editor dirty state, and sticky save dirty state were all true.
  - It requested landscape, then portrait, and verified after each orientation
    change that the same unsaved text and dirty state remained present.
  - It moved the task to the background, brought the activity back to the
    foreground, and verified that text and dirty state still matched the ACE
    display.
  - WebView-side ACE cursor bounds checks passed before rotation, after each
    orientation change, after background restore, and after switching back to
    ACE.
- Save and engine switching smoke:
  - Saving returned the expected text, wrote the same text to the temporary
    file, and cleared both editor dirty state and sticky save dirty state.
  - Switching from ACE to Native preserved the saved text and clean state.
  - Switching from Native back to ACE preserved the saved text and clean state,
    restored ACE to `aceHealthState=RUNNING`, and produced no
    `lastFailureType` or runtime event history entry containing `fallback`.
- This is an instrumentation smoke for the release gate. Any later
  human-observed rotation flash, double padding, or post-rotation tap-anchor
  mismatch should be recorded as a new Gate 7 finding.

## Gate 8 Notes

- Time: 2026-06-16 09:16-09:22 CST.
- Command:
  `.\gradlew.bat :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.ui.edit.editor.ace.AceCodeEditorSmokeTest"`
- Result: PASS on Sony XQ-AT72; 8 tests completed, 0 skipped, 0 failed.
- Ready-timeout TextMirror smoke:
  - The instrumentation test opened a real `EditActivity` with ACE selected,
    entered unsaved draft text before the ready timeout fallback, and verified
    ACE TextMirror plus sticky dirty state before fallback completed.
  - Native fallback received the unsaved text, `EditorView` dirty state stayed
    true, and diagnostics contained `lastFailureType=READY_TIMEOUT` plus a
    runtime event history entry.
- Startup fatal fallback smoke:
  - `forceJsInitThrow` reached Native with the loaded file text and
    `lastFailureType=JS_INIT_ERROR`.
  - `forceRendererGoneFallback` reached Native with the loaded file text and
    `lastFailureType=RENDER_PROCESS_GONE`, without relying on a query against a
    dead WebView.
  - `forceAssetMissing` routed directly to Native with the loaded file text and
    an `ASSET_MISSING` diagnostics snapshot.
  - Startup fatal hooks fire before ACE is editable, so they validate
    loaded-file text and diagnostics rather than unsaved typed text. Unsaved
    text preservation is covered by the ready-timeout hook above.
- Test harness hardening:
  - `EditActivity` startup fallback hooks now use instrumentation
    `ActivityMonitor`, which waits for launch without waiting for fallback
    startup idle.
  - Instrumentation cleanup removes launched editor tasks directly, avoiding
    the dirty-editor exit confirmation dialog from leaking a fallback activity
    into the next smoke test.
- Source boundary follow-up:
  - ACE asset availability is exposed through `AceEditorAssets`, so
    `EditorView` can record asset-missing diagnostics without depending on the
    concrete ACE view class.

## Gate 9 Notes

- Time: 2026-06-16 09:49 CST.
- Command:
  `.\gradlew.bat :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.ui.edit.editor.NativeEditorRegressionSmokeTest"`
- Result: PASS on Sony XQ-AT72; 2 tests completed, 0 skipped, 0 failed.
- Native core workflow smoke:
  - The instrumentation test selected the Native editor before launch and
    opened a real `EditActivity` on a temporary JavaScript file.
  - Direct text insertion appended an edit marker, preserved the Native engine,
    and set both editor dirty state and sticky save dirty state.
  - Best-effort IME show/hide kept the editor on Native. The test also created
    and shut down the Native auto-completion provider.
  - Search/replace updated the expected token. Breakpoint add/remove changed
    `breakpointsSnapshot` for line 1 as expected.
  - Save wrote the same text to disk and cleared dirty state.
  - Run returned a script execution, entered the running state, and returned to
    stopped state after `forceStop()`.
- Native large-file smoke:
  - The instrumentation test created a temporary script larger than
    `EditorTextLoadPolicy.LARGE_FILE_THRESHOLD_BYTES` (512 KiB).
  - The real `EditActivity` stayed on the Native engine, completed loading,
    preserved the exact text length, matched prefix/suffix samples, reported
    more than 2000 lines, and remained clean.
- Coverage note: this is an instrumentation smoke for Native regression. It is
  not a substitute for later manual Native UI exploration if a human-observed
  regression appears.

## Diagnostics Export Notes

- Time: 2026-06-16 09:53 CST.
- Command:
  `.\gradlew.bat :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.ui.edit.editor.ace.AceCodeEditorSmokeTest"`
- Result: PASS on Sony XQ-AT72; 9 tests completed, 0 skipped, 0 failed.
- The new smoke opened a real `EditActivity` with ACE selected and loaded a
  temporary JavaScript file containing a unique identifier, string literal, and
  function name.
- It exported diagnostics through `EditorView.copyAceDiagnosticsToClipboard()`
  and verified the clipboard starts with `ACE diagnostics`, reports
  `engineType=ACE`, reports `aceHealthState=RUNNING`, and includes file-size
  metadata.
- The exported diagnostics did not contain the unique identifier, string
  literal, or function name from the loaded script.
- Gate 8 continues to cover fallback diagnostics export. This smoke covers the
  normal user-facing ACE export path.

## Blockers

### P0

- No automated P0 blocker found in preflight. Gate 2, Gate 3, Gate 4, Gate 6,
  Gate 7, Gate 8, and Gate 9 device smoke found no P0 first-paint, file-open, IME,
  tap-anchor, keyboard stress, completion, tooltip, LSP fallback, rotation,
  restore, engine-switching, fallback hook, TextMirror, diagnostics, or
  bridge-event-storm blocker. Gate 9 also found no P0 Native editor edit/save,
  search/replace, breakpoint, run, auto-completion, or large-file regression.
  Diagnostics export smoke found no script-text leak in the user-facing ACE
  diagnostics clipboard path.

### P1

- TBD after device gate.

### P2

- Debug-only `AceEditorDebugActivity` did not reopen IME from repeated ADB
  taps after Back hid the keyboard while the debug sample stayed focused.
  Ordinary `EditActivity` stress passed and this is not treated as a release
  blocker for the experimental ACE editor.

## Final Decision

- [ ] Release candidate accepted
- [ ] Release candidate conditionally accepted
- [ ] Release candidate rejected

Decision notes:

```text
TBD
```
