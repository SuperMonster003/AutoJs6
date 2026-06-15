# ACE Release Device Report - Sony XQ-AT72

This report records the physical-device release gate for the ordinary ACE
experimental editor. Do not mark the release candidate accepted until every P0
path has a recorded result.

## Device

- Device: Sony XQ-AT72
- Android version: TBD
- API level: TBD
- Android System WebView version: TBD
- AutoJs6 build commit: TBD
- Test package: TBD
- ACE enabled: TBD
- LSP enabled: TBD
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
| `node tools/ace-ci/check-ace-release-gate.mjs` | TBD | |
| `node tools/ace-ci/check-ace-editor-gate.mjs` | TBD | |
| `git diff --check` | TBD | |

## Manual Gate Summary

| Area | Result | Notes |
| --- | --- | --- |
| Native default | TBD | |
| LSP default-off | TBD | |
| VisualEditorAce unavailable | TBD | |
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
| TBD | TBD | |

## Blockers

### P0

- TBD

### P1

- TBD

### P2

- TBD

## Final Decision

- [ ] Release candidate accepted
- [ ] Release candidate conditionally accepted
- [ ] Release candidate rejected

Decision notes:

```text
TBD
```
