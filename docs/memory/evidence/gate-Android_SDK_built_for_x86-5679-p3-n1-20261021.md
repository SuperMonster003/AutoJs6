# Memory gate Android SDK built for x86 (5679) 20261021

- Device: Google Android SDK built for x86, API 24, serial emulator-5580
- Host: org.autojs.autojs6 versionCode 5679 (6.8.0), debuggable
- Run: Android_SDK_built_for_x86-5679-20261021101615, label p3-n1, 2026-10-21T02:15:16.287646+00:00 -> 2026-10-21T02:18:57.218301+00:00
- Device state at start: {'batteryLevel': 100, 'powered': ['AC'], 'wakefulness': 'Awake'}
- Merged runs: Android_SDK_built_for_x86-5679-20261021101516 (all); Android_SDK_built_for_x86-5679-20261021101615 (S1, S3, S8, S12)
- Warm-ups 2, iterations 20, poll 10000 ms, force delay 1000 ms, capture off, meminfo runs
- Cell: retained / Java heap MB / native heap MB / script threads / Rhino contexts / live engines (ROADMAP appendix A)
- Thresholds (T1): retained 0, Java <= 2 MB, native <= 4 MB (+1 frame for capture), threads and contexts back to start, engines back to start

| Scenario | Mode | Status | Cell | Reasons |
| --- | --- | --- | --- | --- |
| S1 empty script | F | fail | 0 / +0.29 / +9.93 / +0 / +0 / 0 | native heap +9.93 MB |
| S3 nested timers on main and child threads | F | pass | 0 / +0.03 / -0.18 / +0 / +0 / 0 |  |
| S8 JavaAdapter / interface adapters x 100 + runtime.loadDex | F | pass | 0 / +0.13 / +0.76 / +0 / +0 / 0 |  |
| S12 plugins.load of an installed official plugin | F | pass | 0 / +0.11 / -0.10 / +0 / +0 / 0 |  |

## Native detail (ROADMAP P3.1)

- Peak: largest rise of the native heap inside a measured run (sampled every 20 ms, before the after-run GC)
- Meminfo: dumpsys meminfo deltas in MB from the end of the warm-ups to the end: Native Heap Pss / Native Heap Alloc / Graphics / Unknown Pss
- Counters: images not recycled (vs start) / open media images at the end / live Mats (vs end of warm-ups)
- Instances: VMDebug instance count deltas from the start: Bitmap / org.opencv.core.Mat / AccessibilityNodeInfo

| Scenario | Mode | Peak MB | Meminfo MB | Counters | Instances | Script stats (max) |
| --- | --- | --- | --- | --- | --- | --- |
| S1 | F | +7.96 | +0.78/+9.92/+0.00/+0.05 | +0 / 0 / +0 | +1/+0/+1 | - |
| S3 | F | +0.37 | +0.08/-0.19/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-1 | - |
| S8 | F | +0.49 | +0.49/+0.75/+0.00/+0.18 | +0 / 0 / +0 | +0/+0/-1 | - |
| S12 | F | +0.30 | +0.09/-0.11/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-1 | - |

Raw reports: the .json file with the same name (one report per line under `results`).
