# pro-parity-suite

Phase 13 Auto.js Pro parity example catalog.

This project groups migration-oriented snippets for the most common Pro-style
Node surfaces: `$autojs.java`, `rhino.install`, UI, floaty/overlay, tasks,
notifications/settings/power, media/recorder, screenshot/OCR, and
accessibility. The main entry is a catalog runner; it
prints the expected marker for each snippet without claiming that gated Android
permissions or future providers are already promoted.

| Example | Snippet | Required capabilities | Packaged behavior | Skip reason |
| --- | --- | --- | --- | --- |
| Java interop | `snippets/java-interop.cjs` | `java_interop` | packaged Java interop stays metadata-only until profile promotion and allowlist review | `java_interop` requires explicit Pro/debug opt-in and allowlist provider |
| Rhino install | `snippets/rhino-install.cjs` | `rhino`, `java_interop` | packaged Rhino proxy globals stay experimental and metadata-only | `rhino.install({ experimental: true })` requires Java proxy provider readiness |
| UI layout | `snippets/ui-layout.cjs` | `ui` | packaged UI is provider-dependent until lifecycle/disclosure evidence lands | live Activity-owned UI provider may be unavailable |
| Floaty overlay | `snippets/overlay-floaty.cjs` | `ui.overlay`, `ui.overlay.permission` | packaged overlay requires reviewed disclosure and foreground notification ownership | overlay permission or promoted overlay provider may be unavailable |
| Tasks | `snippets/tasks-work-manager.cjs` | `work_manager` | one-shot metadata is supported; daily/weekly/intent tasks remain gated | WorkManager provider or persistent task rows may be unavailable |
| Notifications/power | `snippets/notifications-power.cjs` | `notifications`, `notifications.settings`, `device`, `device.power` | settings/status metadata is supported; notification ownership, foreground disclosure, and OEM power behavior remain gated | notifications/settings or device.power provider may be unavailable |
| Media/recorder | `snippets/media-recorder.cjs` | `media`, `media.audio`, `media.metadata`, `media.recording` | read-only media status and recorder denial are covered; real recording requires privacy-reviewed foreground disclosure | media, mediainfo, recorder, or scoped media fixture may be unavailable |
| Screenshot/OCR | `snippets/screenshot-ocr.cjs` | `screen_capture`, `image`, `ocr` | packaged capture/OCR remains gated by disclosure and permission review | MediaProjection permission, image handle, or OCR provider may be unavailable |
| Accessibility | `snippets/accessibility-selector.cjs` | `accessibility` | packaged accessibility needs explicit service/disclosure review | accessibility service may be disabled |

Prompt and disclosure references are deliberately metadata-only. The packaged
behavior rows identify where reviewed disclosure, foreground ownership, service
review, or permission review is required, while the skip reasons and guarded
snippets keep provider, permission, or profile denial paths explicit.

Expected output is listed in `expected-output.txt`.

`smoke.cjs` is the focused runtime smoke subset. It executes only the
Activity-owned JSON UI layout path represented by `snippets/ui-layout.cjs` and
the WorkManager one-shot schedule/cancel path represented by
`snippets/tasks-work-manager.cjs`. It does not execute Java interop, Rhino
global install, overlay, notifications/power, media/recorder, screenshot/OCR,
or accessibility authority examples.

P14-27 docs sync keeps `tasks-work-manager` as the only task/device/media
runtime smoke path. `notifications-power` and `media-recorder` remain migration
evidence and guarded/denial-oriented snippets until notification disclosure,
OEM power behavior, real recorder sessions, packaged recorder stress, playback,
and MediaStore provider gates are promoted.
