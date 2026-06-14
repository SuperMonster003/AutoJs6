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

Diagnostics should include the current engine type, ACE health state, last failure, Android/WebView versions, LSP state, file size, large-file policy, and TextMirror revision.

## Automation Status

The repository currently has only lightweight instrumentation test dependencies for the app module, without a complete editor UI automation stack such as Espresso actions and ActivityScenario rules. This document is therefore the executable acceptance source for task 12. The case IDs below map directly to future smoke test names:

- `openAceEditorAndTypeText`
- `saveAceEditorText`
- `switchNativeToAceKeepsText`
- `switchAceToNativeKeepsText`
- `aceFallbackKeepsText`
- `searchReplaceInAce`
- `toggleBreakpointInAce`
- `largeFileRoutesToNative`

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
- ACE text uses bundled JetBrains Mono, falling back to system monospace if the font asset cannot be loaded.
- Text remains readable and markers stay visible.

Failure logs:
- `themeChanged`
- `fontSizeChanged`

### ACE-IME-001: Input method and symbols/function bars

Preconditions:
- ACE editor is active.

Steps:
1. Focus the editor and show the soft keyboard.
2. Type ASCII, Chinese input, punctuation, and newline.
3. Use symbol bar and function keyboard entries.

Expected:
- Keyboard does not cover the cursor.
- Inserted snippets appear at the cursor.
- No layout overlap occurs after keyboard show/hide.

Failure logs:
- `WindowInsets`

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
- All fallback cases above return to Native without app crash.
- Recent small-file input survives fallback through TextMirror in practical tests.
- Large files route to Native under the current threshold policy.
- No blocked URL, file bridge, or LSP error prevents basic edit/save/run.
- Third-party asset and license checks remain clean.
- ACE diagnostics can be copied from an active ACE editor and after ACE fallback to Native.
