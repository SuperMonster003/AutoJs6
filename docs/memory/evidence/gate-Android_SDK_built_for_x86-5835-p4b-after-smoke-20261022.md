# Memory gate Android SDK built for x86 (5835) 20261022

- Device: Google Android SDK built for x86, API 24, serial emulator-5580
- Host: org.autojs.autojs6 versionCode 5835 (6.8.0), debuggable
- Run: Android_SDK_built_for_x86-5835-20261022095506, label p4b-after-smoke, 2026-10-22T01:55:06.551804+00:00 -> 2026-10-22T02:00:59.632025+00:00
- Device state at start: {'batteryLevel': 100, 'powered': ['AC'], 'wakefulness': 'Awake'}
- Stopped early: the device went offline after S10 normal
- Warm-ups 2, iterations 5, poll 10000 ms, force delay 1000 ms, capture off, meminfo phases
- Cell: retained / Java heap MB / native heap MB / script threads / Rhino contexts / live engines (ROADMAP appendix A)
- Thresholds (T1): retained 0, Java <= 2 MB, native <= 4 MB (+1 frame for capture), threads and contexts back to start, engines back to start

| Scenario | Mode | Status | Cell | Reasons |
| --- | --- | --- | --- | --- |
| S1 empty script | N | pass | 0 / +0.03 / -0.01 / +0 / +0 / 0 |  |
| S1 empty script | F | pass | 0 / +0.02 / -0.04 / +0 / +0 / 0 |  |
| S2 threads.start x 10 + threads.pool | N | pass | 0 / +0.04 / +0.05 / +0 / +0 / 0 |  |
| S2 threads.start x 10 + threads.pool | F | pass | 0 / +0.04 / +0.12 / +0 / +0 / 0 |  |
| S3 nested timers on main and child threads | N | pass | 0 / +0.03 / +0.11 / +0 / +0 / 0 |  |
| S3 nested timers on main and child threads | F | pass | 0 / +0.03 / +0.10 / +0 / +0 / 0 |  |
| S4b images.read + findImage | N | pass | 0 / +0.03 / +0.20 / +0 / +0 / 0 |  |
| S4b images.read + findImage | F | pass | 0 / +0.02 / +0.03 / +0 / +0 / 0 |  |
| S5 ui script with button, text and webview | N | pass | 0 / +0.02 / +0.09 / +0 / +0 / 0 |  |
| S5 ui script with button, text and webview | F | pass | 0 / +0.02 / -0.04 / +0 / +0 / 0 |  |
| S6 floaty.window + floaty.rawWindow | N | pass | 0 / +0.04 / +0.46 / +0 / +0 / 0 |  |
| S6 floaty.window + floaty.rawWindow | F | pass | 0 / +0.03 / +0.32 / +0 / +0 / 0 |  |
| S7 events / key observer / sensors / accessibility events | N | pass | 0 / +0.03 / -0.01 / +0 / +0 / 0 |  |
| S7 events / key observer / sensors / accessibility events | F | pass | 0 / +0.02 / -0.07 / +0 / +0 / 0 |  |
| S9 engines.execScriptFile restarting itself 3 levels | N | pass | 0 / +0.05 / -0.01 / +0 / +0 / 0 |  |
| S9 engines.execScriptFile restarting itself 3 levels | F | pass | 0 / +0.03 / -0.04 / +0 / +0 / 0 |  |
| S10 http.get + WebSocket on a loopback server | N | pass | 0 / +0.05 / -0.08 / +0 / +0 / 0 |  |

## Native detail (ROADMAP P3.1)

- Peak: largest rise of the native heap inside a measured run (sampled every 20 ms, before the after-run GC)
- Meminfo: dumpsys meminfo deltas in MB from the end of the warm-ups to the end: Native Heap Pss / Native Heap Alloc / Graphics / Unknown Pss
- Counters: images not recycled (vs start) / open media images at the end / live Mats (vs end of warm-ups)
- Instances: VMDebug instance count deltas from the start: Bitmap / org.opencv.core.Mat / AccessibilityNodeInfo, then AccessibilityNodeInfo against the end of the warm-ups (S7 / S7b fail above +50, ROADMAP P3.4)
- Classes (ROADMAP P4.4), from the end of the warm-ups to the end: loaded classes (Debug.getLoadedClassCount; S8 fails when they grow in most measured runs, one-time steps do not count) / VMDebug java.lang.Class objects / VMDebug DexClassLoader / loaders registered by runtime.loadDex in the host / VMDebug WebView; `-` where unavailable (VMDebug only on API 24 / 28)

| Scenario | Mode | Peak MB | Meminfo MB | Counters | Instances | Classes | Script stats (max) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| S1 | N | +0.38 | +0.10/-0.01/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes -2 vs warm-ups) | +18 / +18 / +0 / +0 / +0 | - |
| S1 | F | +0.36 | +0.16/-0.04/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +1 / +1 / +0 / +0 / +0 | - |
| S2 | N | +0.42 | +0.18/+0.05/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes -2 vs warm-ups) | +18 / +18 / +0 / +0 / +0 | - |
| S2 | F | +0.36 | +0.14/+0.13/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +1 / +1 / +0 / +0 / +0 | - |
| S3 | N | +0.37 | +0.15/+0.11/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes -50 vs warm-ups) | +18 / +18 / +0 / +0 / +0 | - |
| S3 | F | +0.48 | +0.05/+0.10/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +1 / +1 / +0 / +0 / +0 | - |
| S4b | N | +3.88 | +0.21/+0.20/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes -2 vs warm-ups) | +18 / +18 / +0 / +0 / +0 | - |
| S4b | F | +12.14 | +0.10/+0.03/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +1 / +1 / +0 / +0 / +0 | - |
| S5 | N | +1.77 | +0.80/+0.09/+0.00/+0.99 | +0 / 0 / +0 | +0/+0/-2 (nodes -3 vs warm-ups) | +5 / +5 / +0 / +0 / +0 | - |
| S5 | F | +1.83 | +0.87/-0.04/+0.00/+1.00 | +0 / 0 / +0 | +0/+0/-2 (nodes -3 vs warm-ups) | +5 / +5 / +0 / +0 / +0 | - |
| S6 | N | +0.95 | +0.54/+0.46/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/+0 (nodes +0 vs warm-ups) | +22 / +22 / +0 / +0 / +0 | - |
| S6 | F | +0.96 | +0.43/+0.32/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/+0 (nodes +0 vs warm-ups) | +5 / +5 / +0 / +0 / +0 | - |
| S7 | N | +0.41 | - | +0 / 0 / +0 | +0/+0/+0 (nodes +0 vs warm-ups) | +18 / +18 / +0 / +0 / +0 | - |
| S7 | F | +0.43 | - | +0 / 0 / +0 | +0/+0/+0 (nodes +0 vs warm-ups) | +1 / +1 / +0 / +0 / +0 | - |
| S9 | N | +0.40 | +0.02/-0.01/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +17 / +17 / +0 / +0 / +0 | - |
| S9 | F | +0.91 | -0.08/-0.04/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +0 / +0 / +0 / +0 / +0 | - |
| S10 | N | +0.48 | +0.18/-0.08/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +1 / +1 / +0 / +0 / +0 | - |

Raw reports: the .json file with the same name (one report per line under `results`).
