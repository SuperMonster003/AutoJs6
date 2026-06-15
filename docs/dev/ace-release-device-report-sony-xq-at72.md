# ACE Release Device Report - Sony XQ-AT72

This report records the physical-device release gate for the ordinary ACE
experimental editor. Do not mark the release candidate accepted until every P0
path has a recorded result.

## Device

- Device: Sony XQ-AT72
- Android version: TBD
- API level: TBD
- Android System WebView version: TBD
- AutoJs6 build commit: 920c9f61
- Test package: org.autojs.autojs6.ace
- ACE enabled: TBD on device; explicit opt-in confirmed by static gate
- LSP enabled: TBD on device; default-off confirmed by static gate
- Clean host enabled: yes
- IME: TBD
- Navigation mode: TBD
- Display size: TBD
- Font scale: TBD
- Theme: TBD
- Font: TBD
- Font size: TBD

## Automated Gate

| Command | Result | Notes |
| --- | --- | --- |
| `node tools/ace-ci/check-ace-release-gate.mjs` | PASS | Runs the full ACE editor CI gate first, then release static invariants. |
| `node tools/ace-ci/check-ace-editor-gate.mjs` | PASS | Boundary, surface, API model, Pro-only, completion, d.ts, LSP client, and tooltip checks passed. |
| `git diff --check` | PASS | Exit code 0 with no whitespace errors; follow-up doc-only diff check emitted LF/CRLF warnings only. |

## Manual Gate Summary

| Area | Result | Notes |
| --- | --- | --- |
| Native default | STATIC PASS | Confirmed by release gate; device launch still TBD. |
| LSP default-off | STATIC PASS | Confirmed by release gate; device setting still TBD. |
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
