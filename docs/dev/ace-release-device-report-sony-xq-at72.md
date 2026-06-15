# ACE Release Device Report - Sony XQ-AT72

This report records the physical-device release gate for the ordinary ACE
experimental editor. Do not mark the release candidate accepted until every P0
path has a recorded result.

## Device

- Device: Sony XQ-AT72
- Android version: 12
- API level: 31
- Android System WebView version: com.google.android.webview 145.0.7632.120
- Repository baseline commit: 5b205d04
- Installed app: 6.7.1 Alpha5, versionCode 3821, DEBUGGABLE TEST_ONLY
- Installed app update time: 2026-06-15 20:35:58
- Test package: org.autojs.autojs6.ace
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
| Keyboard stress | TBD | |
| Click anchor accuracy | DEVICE SMOKE PASS | Top, middle, and lower visible-line taps landed on the observed target rows with keyboard hidden before each tap. |
| Lower viewport tap anchor | DEVICE SMOKE PASS | Low-screen taps kept the caret visible just above the IME/symbol toolbar and did not fallback ACE. |
| Long-press selection | TBD | |
| Double-tap selection | TBD | |
| Copy/paste | TBD | |
| CJK composition | TBD | |
| Completion accept | TBD | |
| Tooltip fallback | TBD | |
| Rotation | TBD | |
| Native/ACE switching | TBD | |
| TextMirror fallback | TBD | |
| Ready-timeout fallback | TBD | |
| JS-init fatal fallback | TBD | |
| Renderer-gone fallback | TBD | |
| Asset-missing fallback | TBD | |
| Large-file Native routing | TBD | |
| Native editor regression | TBD | |
| Diagnostics export | TBD | |
| Publishing cleanup audit | STATIC PASS | See `docs/dev/ace-release-publishing-cleanup.md`; final package/label restore is still pending before merge or non-test release. |

## Diagnostics Snapshots

Record copied ACE diagnostics for every failure or ambiguous result.

| Scenario | Diagnostics reference | Notes |
| --- | --- | --- |
| Gate 2 file-manager open smoke | N/A | No failure diagnostics were produced; screenshot samples were reviewed during the run. |
| Gate 3 IME/tap-anchor smoke | N/A | No failure diagnostics were produced; screenshot samples and logcat were reviewed during the run. |

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

## Blockers

### P0

- No automated P0 blocker found in preflight. Gate 2 and Gate 3 device smoke
  found no P0 first-paint, file-open, IME, or tap-anchor blocker.

### P1

- TBD after device gate.

### P2

- TBD after device gate.

## Final Decision

- [ ] Release candidate accepted
- [ ] Release candidate conditionally accepted
- [ ] Release candidate rejected

Decision notes:

```text
TBD
```
