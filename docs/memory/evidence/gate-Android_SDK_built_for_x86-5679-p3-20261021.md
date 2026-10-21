# Memory gate Android SDK built for x86 (5679) 20261021

- Device: Google Android SDK built for x86, API 24, serial emulator-5580
- Host: org.autojs.autojs6 versionCode 5679 (6.8.0), debuggable
- Run: Android_SDK_built_for_x86-5679-20261021102657, label p3, 2026-10-21T02:26:57.527330+00:00 -> 2026-10-21T03:01:16.668973+00:00
- Device state at start: {'batteryLevel': 100, 'powered': ['AC'], 'wakefulness': 'Awake'}
- PROJECT_MEDIA app-op (ROADMAP Q3): `allow` during S4a normal, S4a force, S4f normal, S4f force, before `default`, afterwards `default`
- Warm-ups 2, iterations 20, poll 10000 ms, force delay 1000 ms, capture on, meminfo phases
- Cell: retained / Java heap MB / native heap MB / script threads / Rhino contexts / live engines (ROADMAP appendix A)
- Thresholds (T1): retained 0, Java <= 2 MB, native <= 4 MB (+1 frame for capture), threads and contexts back to start, engines back to start

| Scenario | Mode | Status | Cell | Reasons |
| --- | --- | --- | --- | --- |
| S1 empty script | N | pass | 0 / +0.08 / +0.05 / +0 / +0 / 0 |  |
| S1 empty script | F | pass | 0 / +0.06 / -0.05 / +0 / +0 / 0 |  |
| S2 threads.start x 10 + threads.pool | N | pass | 0 / +0.15 / +0.17 / +0 / +0 / 0 |  |
| S2 threads.start x 10 + threads.pool | F | pass | 0 / +0.12 / -0.15 / +0 / +0 / 0 |  |
| S3 nested timers on main and child threads | N | pass | 0 / +0.11 / -0.03 / +0 / +0 / 0 |  |
| S3 nested timers on main and child threads | F | pass | 0 / +0.09 / -0.08 / +0 / +0 / 0 |  |
| S4a captureScreen x 20 | N | pass | 0 / +0.07 / +8.32 / +0 / +0 / 0 |  |
| S4a captureScreen x 20 | F | pass | 0 / +0.10 / +0.19 / +0 / +0 / 0 |  |
| S4b images.read + findImage | N | pass | 0 / +0.08 / +0.04 / +0 / +0 / 0 |  |
| S4b images.read + findImage | F | pass | 0 / +0.06 / +0.12 / +0 / +0 / 0 |  |
| S4c images.copy / clip / resize chain | N | pass | 0 / +0.07 / +0.53 / +0 / +0 / 0 |  |
| S4c images.copy / clip / resize chain | F | pass | 0 / +0.06 / -0.05 / +0 / +0 / 0 |  |
| S4d matchTemplate + oneShot | N | pass | 0 / +0.07 / +0.09 / +0 / +0 / 0 |  |
| S4d matchTemplate + oneShot | F | pass | 0 / +0.06 / +0.11 / +0 / +0 / 0 |  |
| S4e threaded copy + findImage + recycle (issue #372 / #200 pattern) | N | pass | 0 / +0.10 / +0.18 / +0 / +0 / 0 |  |
| S4e threaded copy + findImage + recycle (issue #372 / #200 pattern) | F | pass | 0 / +0.10 / +0.11 / +0 / +0 / 0 |  |
| S4f captureScreen x 200 without recycle (issue #204) | N | pass | 0 / +0.02 / +4.82 / +0 / +0 / 0 |  |
| S4f captureScreen x 200 without recycle (issue #204) | F | pass | 0 / +0.03 / +8.71 / +0 / +0 / 0 |  |
| S5 ui script with button, text and webview | N | pass | 0 / +0.07 / -0.04 / +0 / +0 / 0 |  |
| S5 ui script with button, text and webview | F | pass | 0 / +0.09 / -0.20 / +0 / +0 / 0 |  |
| S6 floaty.window + floaty.rawWindow | N | pass | 0 / +0.11 / +0.80 / +0 / +0 / 0 |  |
| S6 floaty.window + floaty.rawWindow | F | pass | 0 / +0.23 / +0.82 / +0 / +0 / 0 |  |
| S7 events / key observer / sensors / accessibility events | N | pass | 0 / +0.10 / +0.19 / +0 / +0 / 0 |  |
| S7 events / key observer / sensors / accessibility events | F | pass | 0 / +0.09 / -0.04 / +0 / +0 / 0 |  |
| S7b className(...).find() loop on the accessibility tree | N | skipped | skipped | the host accessibility service is not bound to the test process (run with memoryGateAccessibilityWaitMs and turn the service off and on, ROADMAP appendix C Q9) |
| S7b className(...).find() loop on the accessibility tree | F | skipped | skipped | the host accessibility service is not bound to the test process (run with memoryGateAccessibilityWaitMs and turn the service off and on, ROADMAP appendix C Q9) |
| S8 JavaAdapter / interface adapters x 100 + runtime.loadDex | N | pass | 0 / +0.11 / +0.73 / +0 / +0 / 0 |  |
| S8 JavaAdapter / interface adapters x 100 + runtime.loadDex | F | pass | 0 / +0.09 / +0.42 / +0 / +0 / 0 |  |
| S9 engines.execScriptFile restarting itself 3 levels | N | pass | 0 / +0.17 / +0.07 / +0 / +0 / 0 |  |
| S9 engines.execScriptFile restarting itself 3 levels | F | pass | 0 / +0.16 / +0.19 / +0 / +0 / 0 |  |
| S10 http.get + WebSocket on a loopback server | N | pass | 0 / +0.09 / -0.18 / +0 / +0 / 0 |  |
| S10 http.get + WebSocket on a loopback server | F | pass | 0 / +0.05 / +0.49 / +0 / +0 / 0 |  |
| S11 device.* reads, vibrate and keepScreenOn | N | pass | 0 / +0.08 / +0.04 / +0 / +0 / 0 |  |
| S11 device.* reads, vibrate and keepScreenOn | F | pass | 0 / +0.06 / -0.26 / +0 / +0 / 0 |  |
| S12 plugins.load of an installed official plugin | N | pass | 0 / +0.10 / +0.11 / +0 / +0 / 0 |  |
| S12 plugins.load of an installed official plugin | F | pass | 0 / -0.03 / -0.27 / +0 / +0 / 0 |  |

## Native detail (ROADMAP P3.1)

- Peak: largest rise of the native heap inside a measured run (sampled every 20 ms, before the after-run GC)
- Meminfo: dumpsys meminfo deltas in MB from the end of the warm-ups to the end: Native Heap Pss / Native Heap Alloc / Graphics / Unknown Pss
- Counters: images not recycled (vs start) / open media images at the end / live Mats (vs end of warm-ups)
- Instances: VMDebug instance count deltas from the start: Bitmap / org.opencv.core.Mat / AccessibilityNodeInfo

| Scenario | Mode | Peak MB | Meminfo MB | Counters | Instances | Script stats (max) |
| --- | --- | --- | --- | --- | --- | --- |
| S1 | N | +0.37 | +0.11/+0.05/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 | - |
| S1 | F | +0.41 | -0.14/-0.04/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 | - |
| S2 | N | +0.40 | +0.20/+0.16/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 | - |
| S2 | F | +0.51 | -0.05/-0.15/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 | - |
| S3 | N | +0.43 | +0.01/-0.03/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 | - |
| S3 | F | +0.52 | -0.08/-0.16/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 | - |
| S4a | N | +8.55 | +1.01/+8.32/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 | - |
| S4a | F | +4.56 | +0.40/+0.20/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 | - |
| S4b | N | +6.84 | +0.18/+0.04/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 | - |
| S4b | F | +10.34 | +0.01/+0.12/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 | - |
| S4c | N | +0.87 | +0.55/+0.54/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 | - |
| S4c | F | +0.99 | -0.12/-0.05/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 | - |
| S4d | N | +6.47 | +0.27/+0.09/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 | - |
| S4d | F | +6.84 | -0.03/+0.11/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 | - |
| S4e | N | +19.74 | +0.14/+0.18/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 | - |
| S4e | F | +20.44 | +0.26/+0.11/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 | - |
| S4f | N | +8.15 | +1.21/+4.82/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 | nullFrames 0, frames 200, slowestCaptureMs 1401, totalMs 188747, maxOpenMediaImages 0 |
| S4f | F | +8.50 | +0.99/+8.71/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 | nullFrames 0, frames 1, slowestCaptureMs 415, maxOpenMediaImages 0 |
| S5 | N | +1.95 | +1.06/-0.04/+0.00/+1.59 | +0 / 0 / +0 | +0/+0/-2 | - |
| S5 | F | +1.98 | +0.98/-0.20/+0.00/+1.60 | +0 / 0 / +0 | +0/+0/+3 | - |
| S6 | N | +0.96 | +1.00/+0.80/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/+1 | - |
| S6 | F | +1.04 | +1.33/+0.82/+0.00/+0.00 | +0 / 0 / +0 | +1/+0/-2 | - |
| S7 | N | +0.52 | - | +0 / 0 / +0 | +0/+0/+0 | - |
| S7 | F | +0.44 | - | +0 / 0 / +0 | +0/+0/+0 | - |
| S8 | N | +0.70 | +0.40/+0.73/+0.00/+0.18 | +0 / 0 / +0 | +0/+0/-2 | - |
| S8 | F | +0.65 | +0.27/+0.42/+0.00/+0.18 | +0 / 0 / +0 | +0/+0/-2 | - |
| S9 | N | +0.60 | +0.16/+0.07/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 | - |
| S9 | F | +0.94 | +0.52/+0.19/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 | - |
| S10 | N | +0.47 | +0.19/-0.18/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 | - |
| S10 | F | +0.43 | +0.30/+0.50/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 | - |
| S11 | N | +0.41 | +0.06/+0.04/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 | - |
| S11 | F | +0.40 | -0.06/-0.26/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 | - |
| S12 | N | +0.45 | +0.13/+0.11/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 | - |
| S12 | F | +0.42 | -0.07/-0.27/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 | - |

Raw reports: the .json file with the same name (one report per line under `results`).
