# ACE Keyboard And Viewport Test Plan

This plan is the dedicated release matrix for ACE keyboard, viewport, mobile
selection, fallback, and large-file routing behavior. Native remains the default
editor. ACE can stay experimental only while this matrix is repeatable on the
representative Android and WebView combinations below.

## Scope

This matrix covers regressions that are easy to miss with desktop-style editor
tests:

- file-manager open first paint and gutter flicker
- soft-keyboard show/hide resize behavior
- tap anchor accuracy in top, middle, and lower viewport areas
- completion popup accept by keyboard Enter and touch
- long-press, double-tap, copy, paste, and CJK composition paths
- rotation, background restore, fallback, and large-file Native routing
- diagnostics needed to distinguish real WebView/JS failures from transient IME
  stalls

Out of scope for this gate:

- server-backed LSP transport
- markdown tooltip rendering
- VisualEditorAce
- broad UI automation coverage

Those features stay deferred until this matrix is stable.

## Required Devices

Record the Android System WebView package and version from copied ACE
diagnostics for every run.

| Device class | Android API | Navigation mode | IME | Required |
| --- | --- | --- | --- | --- |
| Legacy baseline | 26 or nearest available | 3-button | Latin | Yes when available |
| Middle baseline | 29 or nearest available | 3-button | Latin | Yes when available |
| Current baseline | 33 or newer | Gesture | Latin | Yes |
| Current CJK | 33 or newer | Gesture | CJK | Yes when available |
| Latest WebView | 35 or newer when available | Gesture | Latin and CJK | Preferred |

If physical devices are limited, prioritize one gesture-navigation device with a
current WebView and one 3-button-navigation device. Emulator passes are useful,
but do not replace physical WebView validation for selection and IME behavior.

## Diagnostics Checklist

Before leaving a failed ACE session, run:

```text
More -> Copy ACE diagnostics
```

Every issue report should include:

- `viewportPolicy=AceEditorViewportPolicy`
- `lastImeState`
- `lastImeHeight`
- `lastImeTransitionStartedAtUptimeMillis`
- `lastImeTransitionEndedAtUptimeMillis`
- `lastImeIgnoredRequestReason`
- `lastHostImeVisible`
- `lastHostImeBottomInset`
- `lastViewportPolicyEvent`
- `lastAceResizeScheduledReason`
- `lastAceResizeDispatchedReason`
- `lastAceResizeCompletedReason`
- `lastBridgeEventName`
- `currentBridgeEventWindowCount`
- `lastBridgeEventWindowCount`
- `maxBridgeEventWindowCount`
- `runtimeEventHistory`

If fallback occurs, confirm the event history contains the deciding cause, such
as `renderer_gone`, `js_fatal`, `heartbeat_result`, `evaluateJavascript`, or
`fallback`. For suspected event storms, look for `runtimeEvent=bridge_event_storm`
and compare `maxBridgeEventWindowCount` across runs.

## Debug Hooks And Stress Method

Existing debug-only hooks remain the fallback and routing controls:

- `AceEditorTestHooks.forceAssetMissing`
- `AceEditorTestHooks.forceReadyTimeout`
- `AceEditorTestHooks.forceJsInitThrow`
- `AceEditorTestHooks.forceRendererGoneFallback`
- `AceEditorTestHooks.forceLargeFileNativeRoute`

The isolated ACE debug activity also exposes an `IME stress` button. It loads a
120-line sample, alternates top/middle/lower cursor anchors, and runs 20
programmatic show/hide cycles through the same ACE bridge and diagnostics path.
Use it as a quick regression helper before running the full device matrix.

Programmatic IME toggling still does not reliably reproduce user tap anchors,
touch focus, WebView selection handles, or CJK composition behavior. `ACE-KV-003`
therefore remains the release source of truth: run the manual repeated
show/hide case on real devices and preserve diagnostics after the run.

## Test Matrix

| ID | Scenario | Setup | Steps | Pass criteria | Diagnostics |
| --- | --- | --- | --- | --- | --- |
| ACE-KV-001 | File-manager open first paint | ACE selected, open `ace-basic.js` from File Manager | Open the file 10 times from a cold editor page, alternating light and dark theme when practical | No white or old-page flash. No persistent gutter-only flicker. First visible frame background matches the editor theme | `lastFirstPaintAtUptimeMillis`, `runtimeEvent=first_paint`, `lastAceResizeCompletedReason` |
| ACE-KV-002 | Top, middle, and lower tap anchors | File has at least 80 lines, keyboard hidden | Tap a top line, a middle line, and a lower visible line. Let the keyboard show and type a marker each time | Marker appears at the tapped line. Keyboard show does not push the intended caret out of view. Gutter and text columns stay aligned | `lastHostImeBottomInset`, `lastViewportPolicyBottomInset`, `lastAceResizeCompletedReason` |
| ACE-KV-003 | Repeated IME show/hide stress | Same file, gesture and 3-button navigation when available | First run the isolated debug activity `IME stress` helper, then manually tap to show keyboard, press Back to hide it, repeat at least 20 times, and copy diagnostics | ACE remains active. No fallback. No persistent white/black flash. Heartbeat resumes after IME settles | `lastImeState`, `lastImeIgnoredRequestReason`, `heartbeatSuspendedReason`, `runtimeEventHistory` |
| ACE-KV-004 | Completion popup accept | Static completion available | Type `console.lo`, accept highlighted completion with keyboard Enter. Type `app.la`, accept by touch | Completion inserts the selected item. No fallback, no `JS_RUNTIME_ERROR`, no keyboard focus loop | `lastBridgeEventName`, `completionError`, `recoverableError`, `lastAceResizeCompletedReason` |
| ACE-KV-005 | Long-press, double-tap, copy, paste, and CJK | Physical WebView device preferred, CJK IME when available | Long-press a word, double-tap a word, adjust selection, copy, paste on another line, then type CJK composition text | No fallback. Copy/paste works through WebView/ACE mobile path. CJK composition remains usable after action-mode entry and exit | `lastActionModeAtUptimeMillis`, `maxBridgeEventWindowCount`, `runtimeEvent=bridge_event_storm` |
| ACE-KV-006 | Rotation and background restore | Unsaved text in ACE | Type a marker, rotate, background the app, return to editor | Text and dirty state survive. Cursor remains visible. Old WebView callbacks do not trigger duplicate fallback | `runtimeEventHistory`, `lastReadyAtUptimeMillis`, `textMirrorRevision` |
| ACE-KV-007 | Fallback classifications | Use debug hooks one at a time | Force ready timeout, JS init throw, renderer-gone fallback, and asset-missing fallback | Real fatal paths still fallback to Native. Diagnostics name the failure. Recent text is preserved through TextMirror when applicable | `lastFailureType`, `lastFailureFatal`, `runtimeEvent=fallback`, `textMirrorRevision` |
| ACE-KV-008 | Large-file Native routing | ACE selected, file at or above current threshold | Open `ace-large-512k.js`, then force small-file Native route with debug hook | Large files use Native under policy. Forced hook also routes small files to Native. No CodeEditText loading APIs run on ACE path | `largeFilePolicy`, `text_ace_large_file_uses_native_editor` |
| ACE-KV-009 | Bridge event storm diagnostics | ACE active during repeated IME and selection actions | Run `ACE-KV-003`, then `ACE-KV-005`, then copy diagnostics | Diagnostics expose event-window counters. A storm, if present, is visible in event history instead of silent fallback | `currentBridgeEventWindowCount`, `lastBridgeEventWindowCount`, `maxBridgeEventWindowCount`, `runtimeEvent=bridge_event_storm` |

## Result Table

| Device | Android API | WebView version | IME | Navigation | KV-001 | KV-002 | KV-003 | KV-004 | KV-005 | KV-006 | KV-007 | KV-008 | KV-009 | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TBD | TBD | TBD | Latin | Gesture | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | |
| TBD | TBD | TBD | Latin | 3-button | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | |
| TBD | TBD | TBD | CJK | Gesture | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | |

## Release Gates

ACE keyboard and viewport work is gate-complete only when:

- every required `ACE-KV-*` case is runnable from this document
- representative device results are recorded in the result table or an attached
  issue/test report
- diagnostics can explain every fallback or suspected event storm
- repeated keyboard show/hide does not fallback ACE
- lower-viewport tap anchors remain accurate when IME appears
- long-press, double-tap, copy, paste, and CJK paths remain usable
- large files still route to Native under the current policy
- Native remains the default editor

Server-backed LSP transport, markdown tooltip loading, and VisualEditorAce are
not primary release targets until these keyboard and viewport gates pass on
representative devices.
