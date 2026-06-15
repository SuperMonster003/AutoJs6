# ACE Editor Test Plan

This plan covers manual device validation for the ACE editor experiment. Native remains the default editor. ACE should only be enabled explicitly, and every fatal ACE failure must fall back to Native without losing the best available editor state.

## Prerequisites

- Build variant: debug app build.
- Test devices or emulators: at least API 26, API 29, API 33, and API 35 when available.
- Settings: set `Editor engine` to `ACE editor (experimental)` for ACE cases, and to `Native editor` for native comparison cases.
- Test files:
  - `ace-basic.js`: small JavaScript file under 512 KB.
  - `ace-large-512k.js`: file at or above the current 512 KB large-file threshold.
  - `ace-search.js`: file with repeated strings such as `foo`, `bar`, and `console.log`.
- Log filters:
  - `AceFailureType`
  - `ACE editor`
  - `WebView renderer`
  - `Blocked ACE`
  - `lspError`
- Local tool checks:
  - `node tools/ace-ci/check-ace-editor-gate.mjs`
  - `node tools/ace-completion/generate-autojs6-indices-from-dts.mjs`
  - `node tools/ace-completion/verify-autojs6-indices.mjs`
  - `node tools/ace-completion/test-completer.mjs`
  - `node tools/ace-hardening/check-editor-surface.mjs`

## Debug Hooks

The following hooks are available in debug builds through `AceEditorTestHooks`. Set them from a debugger or a debug-only local test entry before opening the editor, then call `AceEditorTestHooks.reset()` after each case.

| Hook | Purpose | Expected result |
| --- | --- | --- |
| `forceAssetMissing` | Simulate missing ACE assets before engine creation. | Native editor opens instead of ACE. |
| `forceReadyTimeout` | Suppress the JS ready event. | Fallback to Native after the ready timeout. |
| `forceJsInitThrow` | Throw during ACE JS initialization. | Fallback to Native with a JS init failure. |
| `forceRendererGoneFallback` | Simulate renderer-gone fallback before page load. | Fallback to Native without app crash. |
| `forceLargeFileNativeRoute` | Force ACE large-file routing to Native. | Native editor is used even for a small test file. |

## Diagnostics

Use `More -> Copy ACE diagnostics` from the editor menu when reporting ACE issues. The same export is available from the isolated ACE debug page through the `Copy ACE diagnostics` button.

Diagnostics should include the current engine type, ACE health state, last failure, Android/WebView versions, LSP state, file size, large-file policy, TextMirror revision, viewport policy, host IME event, ACE resize schedule/dispatch/complete fields, bridge event window counters, and recent runtime event history.

The isolated ACE debug page also has an `IME stress` action. It is a quick
debug-only helper for repeated programmatic show/hide cycles and diagnostics,
but it does not replace the manual device cases that validate real tap anchors,
selection handles, and CJK composition.

## Completion And LSP Checks

- `autojs6_indices.js` must be regenerated from `tools/ace-completion/autojs6_indices.source.json`.
- `autojs6_indices.source.json` must be regenerated from the bundled selected AutoJs6 declaration files when those files change.
- Static completion must work for `toast`, `app.launch`, `files.read`, `images.findImage`, `device.getBattery`, `console.log`, `threads.start`, and `engines.myEngine`.
- Accepting a highlighted completion with Enter or by tapping the popup must insert the item without an ACE fallback.
- `autojs_indices.js` is a fallback only; AutoJs6-specific entries should be added to the source JSON.
- Broad AutoJs6 API entries should come from the bundled `aj6-int-*.d.ts` declaration source first; source JSON fallback entries are for parser gaps and curated global member exposure.
- `ACE LSP smart completion (experimental)` is off by default.
- With LSP off, `getLspOptions()` returns `{}` and no WebSocket is created.
- With LSP on and no server available, ACE remains editable and static completion still works.

## Automation Status

The app module keeps a lightweight instrumentation setup without a complete
editor UI automation stack such as Espresso actions and ActivityScenario rules.
`AceCodeEditorSmokeTest` covers the current ACE basics by launching the isolated
debug activity, waiting for WebView ready/first-paint diagnostics, round-tripping
text through the bridge, and checking breakpoint/resize diagnostics.

This document remains the broader acceptance source for the richer editor flows
that still need future automation. The case IDs below map directly to future
smoke test names:

- `openAceEditorAndTypeText`
- `saveAceEditorText`
- `switchNativeToAceKeepsText`
- `switchAceToNativeKeepsText`
- `aceFallbackKeepsText`
- `searchReplaceInAce`
- `toggleBreakpointInAce`
- `largeFileRoutesToNative`
- `aceImeShowHideStress`
- `aceImeLowerViewportAnchor`
- `aceImeCompletionAndSelection`
- `aceMobileSelectionCopyPaste`

## IME Show/Hide Manual Checklist

The dedicated keyboard and viewport release matrix lives in
`docs/dev/ace-keyboard-viewport-test-plan.md`. Use this checklist as the short
manual pass, and use the dedicated matrix for release decisions, WebView
regression checks, and issue reports about flicker, tap-anchor drift, mobile
selection, or keyboard-triggered fallback.

Run this checklist on at least one gesture-navigation device and one 3-button-navigation device when available. Record the Android System WebView version from `More -> Copy ACE diagnostics`.

Setup:
- ACE editor is selected in settings.
- Open `ace-basic.js` with at least 80 lines so the top, middle, and bottom viewport areas can be tested.
- Use JetBrains Mono first, then repeat a quick pass with Source Code Pro.
- Test with the default Latin keyboard and one CJK IME when available.

Checklist:
1. Open the file from File Manager and wait for ACE first paint.
2. Tap near the top, middle, and bottom of the visible code area to show the soft keyboard.
3. Hide the keyboard with Back, then show it again by tapping code.
4. Repeat show/hide at least 20 times without leaving the editor.
5. Tap a lower-screen line while the keyboard is hidden, then let the keyboard show.
6. Type ASCII text, punctuation, newline, and CJK composition text.
7. Trigger completion while the keyboard is visible, accept the highlighted item with Enter, then accept another item by touch.
8. Long-press and double-tap text to enter selection/action-mode paths.
9. Use the symbol bar and function keyboard while the soft keyboard has recently shown and hidden.
10. Copy ACE diagnostics before leaving the editor.

Expected:
- No white/black flash, old file-list flash, or persistent gutter-only flicker.
- The tapped line becomes the caret anchor; keyboard show must not push the intended caret out of view.
- Cursor remains visible after show/hide, Back, completion accept, and action-mode entry/exit.
- ACE does not fallback to Native during repeated show/hide.
- Completion popup touch/Enter accept does not report `JS_RUNTIME_ERROR`.
- Diagnostics show `viewportPolicy=AceEditorViewportPolicy`, recent host IME fields, and ACE resize scheduled/dispatched/completed reasons.

Failure logs:
- `ACE console`
- `AceFailureType`
- `lastViewportPolicyEvent`
- `lastAceResizeScheduledReason`
- `lastAceResizeCompletedReason`
- `heartbeatSuspendedReason`

## Cases

### ACE-BASIC-001: Open ACE editor and type text

Preconditions:
- ACE editor is selected in settings.
- Open `ace-basic.js`.

Steps:
1. Type a new line: `console.log("ace typed");`.
2. Move the cursor across several lines.
3. Use undo and redo once.

Expected:
- Text appears immediately.
- Cursor line and column update normally.
- Save button becomes enabled after typing.
- Undo and redo state follows the edit.

Failure logs:
- `ACE console`
- `AceFailureType`

### ACE-FILE-001: Save ACE editor text

Preconditions:
- ACE editor is active.
- `ace-basic.js` is writable.

Steps:
1. Add a unique marker comment.
2. Save the file.
3. Close and reopen the file.

Expected:
- Marker persists after reopening.
- Save button disables after successful save.
- No draft overwrite occurs during async load.

Failure logs:
- `getTextForSaving`
- `StableDraftFileHelper`

### ACE-RUN-001: Run script from ACE

Preconditions:
- ACE editor is active.
- Script contains a visible `toast` or `console.log`.

Steps:
1. Run the script from the toolbar.
2. Open the log panel if needed.

Expected:
- Script executes.
- Run state updates in the toolbar and log panel.
- Log stack navigation still jumps to the expected line.

Failure logs:
- `ScriptExecution`
- `EXTRA_EXCEPTION_LINE_NUMBER`

### ACE-SEARCH-001: Search and replace in ACE

Preconditions:
- ACE editor is active.
- Open `ace-search.js`.

Steps:
1. Search for `foo`.
2. Navigate next and previous matches.
3. Replace one match with `bar`.
4. Replace all remaining matches.

Expected:
- Match count and selection update correctly.
- Replaced text is visible in the editor.
- Save button becomes enabled.

Failure logs:
- `EditorSearchSyntaxException`
- `replaceAll`

### ACE-BREAKPOINT-001: Toggle ACE breakpoint

Preconditions:
- ACE editor is active.

Steps:
1. Tap the left gutter on a script line.
2. Use the breakpoint menu action on the current line.
3. Clear all breakpoints.

Expected:
- Breakpoint marker appears and disappears.
- Native breakpoint state receives the same line numbers.
- Clear all removes every marker.

Failure logs:
- `breakpointChanged`

### ACE-SWITCH-001: Switch Native to ACE keeps text

Preconditions:
- Native editor is active.

Steps:
1. Type an unsaved marker.
2. Switch editor engine to ACE.

Expected:
- Text, dirty state, cursor, selection, breakpoints, read-only state, theme, and font size are preserved as far as supported.
- Undo/redo history is not expected to cross engines.
- The first manual engine switch shows a one-time undo/redo history notice.

Failure logs:
- `text_editor_engine_switched`
- `error_ace_editor_unavailable`

### ACE-SWITCH-002: Switch ACE to Native keeps text

Preconditions:
- ACE editor is active.

Steps:
1. Type an unsaved marker.
2. Switch editor engine to Native.

Expected:
- Latest typed text appears in Native.
- Save button remains enabled.
- Breakpoints and debug line are restored where applicable.

Failure logs:
- `text_editor_engine_switched`

### ACE-LARGE-001: Large file routes to Native

Preconditions:
- ACE editor is selected.
- Open `ace-large-512k.js`.

Steps:
1. Open the file.
2. Wait for loading to finish.

Expected:
- Native editor is used for responsiveness.
- Toast explains that large files use Native.
- Undo and redo are disabled during large-file mode.

Failure logs:
- `text_ace_large_file_uses_native_editor`

### ACE-LARGE-002: Debug hook forces Native route

Preconditions:
- ACE editor is selected.
- Set `AceEditorTestHooks.forceLargeFileNativeRoute = true`.
- Open `ace-basic.js`.

Steps:
1. Open the small file.
2. Try switching back to ACE from the menu.
3. Reset hooks.

Expected:
- Native editor is used even though the file is small.
- Switching to ACE is blocked while the hook is enabled.
- After reset, normal ACE switching works again.

Failure logs:
- `text_ace_large_file_uses_native_editor`

### ACE-FALLBACK-001: Asset missing fallback

Preconditions:
- ACE editor is selected.
- Set `AceEditorTestHooks.forceAssetMissing = true`.

Steps:
1. Open `ace-basic.js`.
2. Reset hooks.

Expected:
- Native editor opens.
- App does not crash.
- Toast says ACE is unavailable or Native remains active.

Failure logs:
- `ASSET_MISSING`
- `error_ace_editor_unavailable`

### ACE-FALLBACK-002: Ready timeout fallback

Preconditions:
- ACE editor is selected.
- Set `AceEditorTestHooks.forceReadyTimeout = true`.

Steps:
1. Open `ace-basic.js`.
2. Wait at least 5 seconds.
3. Reset hooks.

Expected:
- ACE falls back to Native.
- Text, dirty state, cursor, breakpoints, debug line, read-only state, theme, and font are restored from the best available snapshot.
- Fallback Toast is shown once.
- `More -> Copy ACE diagnostics` still reports the last ACE health and failure state after fallback.

Failure logs:
- `READY_TIMEOUT`

### ACE-FALLBACK-003: JS init throw fallback

Preconditions:
- ACE editor is selected.
- Set `AceEditorTestHooks.forceJsInitThrow = true`.

Steps:
1. Open `ace-basic.js`.
2. Reset hooks.

Expected:
- ACE reports JS init failure.
- Editor falls back to Native.
- App does not crash.

Failure logs:
- `JS_INIT_ERROR`
- `fatalError`

### ACE-FALLBACK-004: Renderer gone fallback

Preconditions:
- ACE editor is selected.
- Set `AceEditorTestHooks.forceRendererGoneFallback = true`.

Steps:
1. Open `ace-basic.js`.
2. Reset hooks.

Expected:
- Editor falls back to Native.
- App does not crash.
- Old ACE callbacks do not trigger duplicate fallback or duplicate Toast.

Failure logs:
- `RENDER_PROCESS_GONE`

### ACE-FALLBACK-005: Recent input survives fallback

Preconditions:
- ACE editor is active.
- Text mirror is enabled by default.

Steps:
1. Type several quick edits, including paste, multi-line delete, undo, and redo.
2. Trigger a fallback path with one debug hook in a new session, or simulate WebView renderer loss from platform tooling when available.

Expected:
- Native fallback uses the latest Kotlin-side mirrored text whenever WebView text cannot be queried.
- Small files calibrate against ACE text after idle.

Failure logs:
- `ACE text mirror delta failed`
- `Invalid ACE text delta`

### ACE-THEME-001: Theme and font

Preconditions:
- ACE editor is active.

Steps:
1. Switch between light and dark theme.
2. Change editor font size.
3. Switch ACE to Native and back.

Expected:
- ACE theme follows app theme.
- Font size is restored after engine switch.
- ACE text uses JetBrains Mono by default.
- `More -> ACE editor font` can switch to Source Code Pro and system monospace.
- The settings page persists the ACE editor font for future ACE sessions.
- Text remains readable and markers stay visible.

Failure logs:
- `themeChanged`
- `fontSizeChanged`

### ACE-COMPLETION-001: Static completion smoke test

Preconditions:
- Node.js is available on the development machine.

Steps:
1. Run `node tools/ace-completion/generate-autojs6-indices-from-dts.mjs`.
2. Run `node tools/ace-completion/generate-autojs6-indices.mjs`.
3. Run `node tools/ace-completion/generate-dts.mjs`.
4. Run `node tools/ace-completion/verify-autojs6-api-model.mjs`.
5. Run `node tools/ace-completion/verify-autojs6-pro-only.mjs`.
6. Run `node tools/ace-completion/verify-autojs6-indices.mjs`.
7. Run `node tools/ace-completion/test-completer.mjs`.

Expected:
- All commands pass.
- Common AutoJs6 API completions are present.
- Common AutoJs6 module member completions are declaration-backed in the intermediate API model.
- Reserved Pro-only API names are absent from source and runtime indices.
- Accepting a popup item by Enter and by touch does not report `JS_RUNTIME_ERROR`.
- Missing indices and legacy fallback paths do not throw.

Failure logs:
- `Missing required global completion`
- `Missing required member completion`
- `contains Pro-only completion`

### ACE-LSP-001: LSP default-off behavior

Preconditions:
- ACE editor is selected in settings.
- `ACE LSP smart completion (experimental)` is off.

Steps:
1. Open `ace-basic.js`.
2. Copy ACE diagnostics.
3. Verify no WebSocket request appears in WebView logs.

Expected:
- ACE opens normally.
- Diagnostics include `lspEnabled=false` and `lspState=disabled`.
- Static completion continues to work.

Failure logs:
- `lspError`
- `WebSocket`

### ACE-LSP-002: LSP flag without server

Preconditions:
- ACE editor is selected in settings.
- `ACE LSP smart completion (experimental)` is on.

Steps:
1. Open `ace-basic.js`.
2. Trigger completion for `console.lo`.
3. Copy ACE diagnostics.

Expected:
- ACE remains editable.
- Static completion returns `console.log`.
- Diagnostics include `lspEnabled=true` and `lspState=configured-no-server`.

Failure logs:
- `lspError`
- `AceFailureType.LSP_ERROR`

### ACE-IME-001: Input method and symbols/function bars

Preconditions:
- ACE editor is active.
- Open `ace-basic.js`.

Steps:
1. Focus the editor and show the soft keyboard.
2. Type ASCII, Chinese input, punctuation, and newline.
3. Use symbol bar and function keyboard entries.
4. Hide the keyboard with Back and type again.

Expected:
- Keyboard does not cover the cursor.
- Inserted snippets appear at the cursor.
- No layout overlap occurs after keyboard show/hide.
- Diagnostics include `viewportPolicy=AceEditorViewportPolicy`.

Failure logs:
- `WindowInsets`
- `lastHostImeVisible`
- `lastAceResizeCompletedReason`

### ACE-IME-002: Repeated soft keyboard show/hide stress

Preconditions:
- ACE editor is active.
- Open a file with at least 80 lines.

Steps:
1. Tap a visible line to show the keyboard.
2. Hide the keyboard with Back.
3. Repeat steps 1 and 2 at least 20 times.
4. Copy ACE diagnostics.

Expected:
- ACE remains active and does not fallback to Native.
- No persistent white/black flash appears during resize.
- Heartbeat suspension, if present, is transient and resumes after IME settles.
- Diagnostics include recent `lastViewportPolicyEvent`, `lastAceResizeScheduledReason`, `lastAceResizeDispatchedReason`, and `lastAceResizeCompletedReason`.

Failure logs:
- `HEARTBEAT_TIMEOUT`
- `EVALUATE_JAVASCRIPT_ERROR`
- `heartbeatSuspendedReason`
- `lastAceResizeCompletedReason`

### ACE-IME-003: Lower viewport tap anchor remains visible

Preconditions:
- ACE editor is active.
- Open a file with enough lines to scroll.

Steps:
1. Hide the keyboard.
2. Scroll so a target line is near the bottom of the visible editor.
3. Tap the target line and allow the keyboard to show.
4. Type a marker such as `// ime-anchor`.
5. Hide and show the keyboard once more without moving the cursor.

Expected:
- The typed marker appears at the tapped line, not above or below it.
- Keyboard show scrolls enough to keep the caret visible without jumping to an unrelated line.
- Hiding the keyboard does not over-scroll back past the original viewport.
- Gutter and text columns remain aligned after resize.

Failure logs:
- `lastHostImeBottomInset`
- `lastViewportPolicyBottomInset`
- `lastAceResizeCompletedReason`

### ACE-IME-004: Completion and selection while IME is visible

Preconditions:
- ACE editor is active.
- Static completion is available.

Steps:
1. Show the keyboard and type `console.lo`.
2. Accept the highlighted completion with the keyboard Enter key.
3. Type `app.la` and accept a completion by tapping the popup.
4. Long-press a word and adjust selection.
5. Double-tap a word and dismiss selection/action mode.

Expected:
- Completion accept by Enter and touch inserts the selected item.
- Long-press and double-tap do not fallback ACE.
- Action-mode bridge calls are handled without `autojs.startActionMode is not a function`.
- Selection entry/exit does not leave the keyboard or editor in a stuck focus state.

Failure logs:
- `JS_RUNTIME_ERROR`
- `completionError`
- `recoverableError`
- `lastActionModeAtUptimeMillis`

### ACE-MOBILE-001: Mobile selection, copy, paste, and CJK composition

Preconditions:
- ACE editor is active on a physical Android WebView device when available.
- Open a file with several lines of mixed ASCII, punctuation, and CJK text.

Steps:
1. Long-press a word and confirm the ACE mobile selection menu appears when available.
2. Double-tap a word, expand the selection handles, and dismiss action mode.
3. Copy selected text and paste it on another line.
4. Type CJK composition text before and after selection mode.
5. Tap the gutter before and after selection mode to toggle a breakpoint.
6. Copy ACE diagnostics.

Expected:
- Long-press and double-tap do not fallback ACE.
- Copy and paste are usable through the WebView/ACE mobile path.
- CJK composition remains usable after selection/action-mode entry and exit.
- Gutter breakpoint taps and debug-line markers remain aligned.
- Diagnostics do not show unbounded bridge event bursts.

Failure logs:
- `lastActionModeAtUptimeMillis`
- `maxBridgeEventWindowCount`
- `runtimeEvent=bridge_event_storm`
- `.ace_mobile-menu`

### ACE-LIFECYCLE-001: Rotation and background restore

Preconditions:
- ACE editor is active.

Steps:
1. Type unsaved text.
2. Rotate the device.
3. Send the app to background and reopen it.

Expected:
- Text and dirty state are preserved.
- Cursor is visible after returning.
- No duplicate WebView or old callback crash occurs.

Failure logs:
- `onRenderProcessGone`
- `EditorStateSnapshot`

## Compatibility Matrix

Record each run with the actual Android System WebView version from device settings.

| Android API | WebView version | ACE loads | IME OK | Search OK | Breakpoint OK | Dark theme OK | Fallback OK | Large route OK | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 26 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | |
| 29 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | |
| 33 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | |
| 35 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | |

## Release Gate

ACE can remain enabled as an experimental option only if:

- Native remains the default editor.
- The dedicated `docs/dev/ace-keyboard-viewport-test-plan.md` matrix is runnable
  for first paint, top/middle/bottom tap anchors, repeated IME stress,
  completion accept, long-press selection, CJK input, rotation, fallback, and
  large-file Native routing.
- `verify-autojs6-indices.mjs` and `test-completer.mjs` pass.
- All fallback cases above return to Native without app crash.
- Recent small-file input survives fallback through TextMirror in practical tests.
- Large files route to Native under the current threshold policy.
- No blocked URL, file bridge, or LSP error prevents basic edit/save/run.
- `lib.autojs6.d.ts` is generated from the same AutoJs6 completion source.
- Selected AutoJs6 declaration files and their MPL-2.0 license are bundled under `autojs6/types/autojs6`.
- `docs/dev/ace-lsp-design.md` still keeps server-backed LSP experimental and default-off.
- Third-party asset and license checks remain clean.
- ACE diagnostics can be copied from an active ACE editor and after ACE fallback to Native.
- ACE diagnostics include runtime event history and bridge event window counters
  for keyboard and viewport failures.
- LSP alpha, hover/markdown tooltip loading, and VisualEditorAce remain deferred
  until the keyboard and viewport matrix is stable on representative devices.
