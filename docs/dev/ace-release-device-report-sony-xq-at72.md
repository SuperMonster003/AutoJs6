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
| First paint | TBD | |
| File-manager open repeat | TBD | |
| Keyboard show/hide | TBD | |
| Keyboard stress | TBD | |
| Click anchor accuracy | TBD | |
| Lower viewport tap anchor | TBD | |
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

## Diagnostics Snapshots

Record copied ACE diagnostics for every failure or ambiguous result.

| Scenario | Diagnostics reference | Notes |
| --- | --- | --- |
| No device diagnostics yet | TBD | Awaiting Sony XQ-AT72 manual gate. |

## Blockers

### P0

- No automated P0 blocker found in preflight. Device P0 status is TBD.

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
