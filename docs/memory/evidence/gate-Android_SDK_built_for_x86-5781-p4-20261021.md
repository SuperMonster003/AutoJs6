# Memory gate Android SDK built for x86 (5781) 20261021

- Device: Google Android SDK built for x86, API 24, serial emulator-5554
- Host: org.autojs.autojs6 versionCode 5781 (6.8.0), debuggable
- Run: Android_SDK_built_for_x86-5781-20261021214626, label p4, 2026-10-21T12:45:33.696550+00:00 -> 2026-10-21T14:04:58.939703+00:00
- Device state at start: {'batteryLevel': 100, 'powered': ['AC'], 'wakefulness': 'Awake'}
- Merged runs: Android_SDK_built_for_x86-5781-20261021204533 (all); Android_SDK_built_for_x86-5781-20261021214626 (S4f, S5)
- PROJECT_MEDIA app-op (ROADMAP Q3): `allow` during S4f normal, S4f force, before `default`, afterwards `default`
- Warm-ups 2, iterations 20, poll 10000 ms, force delay 1000 ms, capture on, meminfo phases
- Cell: retained / Java heap MB / native heap MB / script threads / Rhino contexts / live engines (ROADMAP appendix A)
- Thresholds (T1): retained 0, Java <= 2 MB, native <= 4 MB (+1 frame for capture), threads and contexts back to start, engines back to start

| Scenario | Mode | Status | Cell | Reasons |
| --- | --- | --- | --- | --- |
| S1 empty script | N | pass | 0 / +0.08 / -0.04 / +0 / +0 / 0 |  |
| S1 empty script | F | pass | 0 / +0.05 / +0.08 / +0 / +0 / 0 |  |
| S2 threads.start x 10 + threads.pool | N | pass | 0 / +0.16 / +0.09 / +0 / +0 / 0 |  |
| S2 threads.start x 10 + threads.pool | F | pass | 0 / +0.11 / +0.02 / +0 / +0 / 0 |  |
| S3 nested timers on main and child threads | N | pass | 0 / +0.10 / -0.02 / +0 / +0 / 0 |  |
| S3 nested timers on main and child threads | F | pass | 0 / +0.09 / -0.00 / +0 / +0 / 0 |  |
| S4a captureScreen x 20 | N | pass | 0 / +0.07 / +8.17 / +0 / +0 / 0 |  |
| S4a captureScreen x 20 | F | pass | 0 / +0.09 / +4.27 / +0 / +0 / 0 |  |
| S4b images.read + findImage | N | pass | 0 / +0.10 / +0.03 / +0 / +0 / 0 |  |
| S4b images.read + findImage | F | pass | 0 / +0.09 / +0.07 / +0 / +0 / 0 |  |
| S4c images.copy / clip / resize chain | N | pass | 0 / +0.15 / +0.70 / +0 / +0 / 0 |  |
| S4c images.copy / clip / resize chain | F | pass | 0 / +0.09 / +0.23 / +0 / +0 / 0 |  |
| S4d matchTemplate + oneShot | N | pass | 0 / +0.07 / +0.12 / +0 / +0 / 0 |  |
| S4d matchTemplate + oneShot | F | pass | 0 / +0.06 / -0.11 / +0 / +0 / 0 |  |
| S4e threaded copy + findImage + recycle (issue #372 / #200 pattern) | N | pass | 0 / +0.10 / +0.34 / +0 / +0 / 0 |  |
| S4e threaded copy + findImage + recycle (issue #372 / #200 pattern) | F | pass | 0 / +0.09 / +0.16 / +0 / +0 / 0 |  |
| S4f captureScreen x 200 without recycle (issue #204) | N | pass | 0 / +0.02 / +12.01 / +0 / +0 / 0 |  |
| S4f captureScreen x 200 without recycle (issue #204) | F | pass | 0 / +0.03 / +8.68 / +0 / +0 / 0 |  |
| S5 ui script with button, text and webview | N | pass | 0 / +0.07 / +0.06 / +0 / +0 / 0 |  |
| S5 ui script with button, text and webview | F | pass | 0 / +0.09 / -0.18 / +0 / +0 / 0 |  |
| S6 floaty.window + floaty.rawWindow | N | pass | 0 / +0.10 / +0.91 / +0 / +0 / 0 |  |
| S6 floaty.window + floaty.rawWindow | F | pass | 0 / +0.07 / +0.74 / +0 / +0 / 0 |  |
| S7 events / key observer / sensors / accessibility events | N | pass | 0 / +0.09 / -0.07 / +0 / +0 / 0 |  |
| S7 events / key observer / sensors / accessibility events | F | pass | 0 / +0.09 / -0.07 / +0 / +0 / 0 |  |
| S7b className(...).find() loop on the accessibility tree | N | skipped | skipped | the host accessibility service is not bound to the test process (run with memoryGateAccessibilityWaitMs and turn the service off and on, ROADMAP appendix C Q9) |
| S7b className(...).find() loop on the accessibility tree | F | skipped | skipped | the host accessibility service is not bound to the test process (run with memoryGateAccessibilityWaitMs and turn the service off and on, ROADMAP appendix C Q9) |
| S8 JavaAdapter / interface adapters x 100 + runtime.loadDex | N | pass | 0 / +0.10 / +0.52 / +0 / +0 / 0 |  |
| S8 JavaAdapter / interface adapters x 100 + runtime.loadDex | F | pass | 0 / +0.09 / +0.41 / +0 / +0 / 0 |  |
| S9 engines.execScriptFile restarting itself 3 levels | N | pass | 0 / +0.15 / +0.14 / +0 / +0 / 0 |  |
| S9 engines.execScriptFile restarting itself 3 levels | F | pass | 0 / +0.13 / -0.21 / +0 / +0 / 0 |  |
| S10 http.get + WebSocket on a loopback server | N | pass | 0 / +0.11 / -0.03 / +0 / +0 / 0 |  |
| S10 http.get + WebSocket on a loopback server | F | pass | 0 / +0.12 / +0.42 / +0 / +0 / 0 |  |
| S11 device.* reads, vibrate and keepScreenOn | N | pass | 0 / +0.07 / -0.08 / +0 / +0 / 0 |  |
| S11 device.* reads, vibrate and keepScreenOn | F | pass | 0 / +0.06 / -0.31 / +0 / +0 / 0 |  |
| S12 plugins.load of an installed official plugin | N | pass | 0 / +0.10 / +0.09 / +0 / +0 / 0 |  |
| S12 plugins.load of an installed official plugin | F | pass | 0 / +0.09 / -0.17 / +0 / +0 / 0 |  |
| S13 speech.listen session open when the script ends | N | skipped | skipped | no speech plugin (3-Macaw Speech or 3-Stone AI) is installed, enabled and authorized |
| S13 speech.listen session open when the script ends | F | skipped | skipped | no speech plugin (3-Macaw Speech or 3-Stone AI) is installed, enabled and authorized |
| S14 console.show with exitOnClose, then the script ends | N | pass | 0 / -0.02 / +0.31 / +0 / +0 / 0 |  |
| S14 console.show with exitOnClose, then the script ends | F | pass | 0 / +0.09 / +0.38 / +0 / +0 / 0 |  |

## Native detail (ROADMAP P3.1)

- Peak: largest rise of the native heap inside a measured run (sampled every 20 ms, before the after-run GC)
- Meminfo: dumpsys meminfo deltas in MB from the end of the warm-ups to the end: Native Heap Pss / Native Heap Alloc / Graphics / Unknown Pss
- Counters: images not recycled (vs start) / open media images at the end / live Mats (vs end of warm-ups)
- Instances: VMDebug instance count deltas from the start: Bitmap / org.opencv.core.Mat / AccessibilityNodeInfo, then AccessibilityNodeInfo against the end of the warm-ups (S7 / S7b fail above +50, ROADMAP P3.4)

| Scenario | Mode | Peak MB | Meminfo MB | Counters | Instances | Script stats (max) |
| --- | --- | --- | --- | --- | --- | --- |
| S1 | N | +0.43 | +0.06/-0.04/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes -2 vs warm-ups) | - |
| S1 | F | +0.36 | -0.05/+0.08/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S2 | N | +0.52 | +0.12/+0.09/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S2 | F | +0.47 | +0.01/+0.02/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S3 | N | +0.52 | +0.04/-0.02/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S3 | F | +0.41 | +0.17/-0.00/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-1 (nodes +0 vs warm-ups) | - |
| S4a | N | +8.29 | +0.88/+8.17/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S4a | F | +8.10 | +0.50/+4.27/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/+0 (nodes +0 vs warm-ups) | - |
| S4b | N | +6.83 | +0.23/+0.03/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S4b | F | +9.46 | +0.09/+0.07/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S4c | N | +0.87 | +0.86/+0.70/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S4c | F | +1.00 | +0.16/+0.23/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S4d | N | +6.46 | +0.21/+0.12/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S4d | F | +6.91 | -0.00/-0.11/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/+48 (nodes +50 vs warm-ups) | - |
| S4e | N | +20.62 | +0.29/+0.34/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-1 (nodes +0 vs warm-ups) | - |
| S4e | F | +19.94 | +0.23/+0.16/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S4f | N | +15.63 | +1.04/+12.01/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | nullFrames 0, frames 200, slowestCaptureMs 1381, totalMs 258960, maxOpenMediaImages 0 |
| S4f | F | +12.16 | +0.87/+8.68/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/+0 (nodes +2 vs warm-ups) | nullFrames 0, frames 3, slowestCaptureMs 431, maxOpenMediaImages 0 |
| S5 | N | +1.89 | +1.17/+0.06/+0.00/+1.60 | +0 / 0 / +0 | +0/+0/+3 (nodes +1 vs warm-ups) | - |
| S5 | F | +1.94 | +1.14/-0.18/+0.00/+1.60 | +0 / 0 / +0 | +0/+0/+3 (nodes +0 vs warm-ups) | - |
| S6 | N | +1.10 | +1.12/+0.91/+0.00/+0.00 | +0 / 0 / +0 | -1/+0/-2 (nodes -2 vs warm-ups) | - |
| S6 | F | +1.09 | +0.95/+0.74/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/+0 (nodes +2 vs warm-ups) | - |
| S7 | N | +0.54 | - | +0 / 0 / +0 | +0/+0/+0 (nodes +0 vs warm-ups) | - |
| S7 | F | +0.49 | - | +0 / 0 / +0 | +0/+0/+0 (nodes +0 vs warm-ups) | - |
| S8 | N | +0.66 | +0.26/+0.52/+0.00/+0.18 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S8 | F | +0.70 | +0.27/+0.41/+0.00/+0.18 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S9 | N | +0.49 | +0.05/+0.14/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S9 | F | +0.95 | -0.05/-0.21/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S10 | N | +0.54 | +0.20/-0.03/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S10 | F | +0.48 | +0.34/+0.42/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S11 | N | +0.36 | +0.05/-0.07/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S11 | F | +0.46 | -0.15/-0.31/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S12 | N | +0.49 | +0.06/+0.09/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S12 | F | +0.56 | -0.02/-0.17/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S14 | N | +0.54 | +1.00/+0.31/+0.00/+0.00 | +0 / 0 / +0 | +3/+0/+0 (nodes +0 vs warm-ups) | - |
| S14 | F | +0.48 | +0.95/+0.38/+0.00/+0.00 | +0 / 0 / +0 | +4/+0/+0 (nodes +0 vs warm-ups) | - |

Raw reports: the .json file with the same name (one report per line under `results`).
