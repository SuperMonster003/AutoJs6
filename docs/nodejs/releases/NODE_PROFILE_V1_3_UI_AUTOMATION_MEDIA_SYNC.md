# Node Profile v1.3 UI Automation Media Sync

Status: future-only documentation sync

P15-11 records the v1.3 release-facing state for the
`pro.ui_overlay_automation_media` lane. No P15-04 through P15-10 provider is promoted
by this file; all seven gates are imported as partial evidence with
`provider_poc_not_promoted` release wording.

| Task | Provider area | Release-facing state |
| --- | --- | --- |
| `P15-04` | packaged live UI | Partial evidence only; packaged relaunch, crash recovery, disclosure, Android 15+, and Safe downgrade archives remain future work. |
| `P15-05` | overlay provider | Partial evidence only; granted-permission archives, foreground disclosure, cleanup stress, packaged smoke, and OEM matrix evidence remain future work. |
| `P15-06` | overlay OEM matrix | Partial evidence only; Android 12/15+ granted-device archives and OEM settings notes remain future work. |
| `P15-07` | packaged overlay smoke | Partial evidence only; packaged install-run archives, disclosure/stop action, crash/relaunch cleanup, and stress evidence remain future work. |
| `P15-08` | accessibility | Partial evidence only; service-enabled archives, selector/action device matrix, and unsupported-tier diagnostics remain future work. |
| `P15-09` | MediaProjection | Partial evidence only; prompt/capture archives, foreground disclosure, packaged APK behavior, and Safe downgrade proof remain future work. |
| `P15-10` | image/OCR | Partial evidence only; advanced image provider archives, packaged OCR model evidence, quota/leak stress, and capture-to-OCR disclosure remain future work. |

Release notes must keep overlay, packaged accessibility, MediaProjection
capture, advanced image, packaged OCR/model, and recorder-session examples
static, guarded, or denial-oriented until the matching provider gate reports
promoted evidence and the downstream security, type, migration, and release
sync gates also pass.
