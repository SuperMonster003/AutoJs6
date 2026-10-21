# Memory gate Android SDK built for x86 (5781) 20261021

- Device: Google Android SDK built for x86, API 24, serial emulator-5554
- Host: org.autojs.autojs6 versionCode 5781 (6.8.0), debuggable
- Run: Android_SDK_built_for_x86-5781-20261021223434, label p4-cold-x60, 2026-10-21T14:34:34.156071+00:00 -> 2026-10-21T14:36:07.041366+00:00
- Device state at start: {'batteryLevel': 100, 'powered': ['AC'], 'wakefulness': 'Awake'}
- PROJECT_MEDIA app-op (ROADMAP Q3): `allow` during S4a force, before `default`, afterwards `default`
- Warm-ups 2, iterations 60, poll 10000 ms, force delay 1000 ms, capture on, meminfo phases
- Cell: retained / Java heap MB / native heap MB / script threads / Rhino contexts / live engines (ROADMAP appendix A)
- Thresholds (T1): retained 0, Java <= 2 MB, native <= 4 MB (+1 frame for capture), threads and contexts back to start, engines back to start

| Scenario | Mode | Status | Cell | Reasons |
| --- | --- | --- | --- | --- |
| S4a captureScreen x 20 | F | pass | 0 / +0.22 / +4.61 / +0 / +0 / 0 |  |

## Native detail (ROADMAP P3.1)

- Peak: largest rise of the native heap inside a measured run (sampled every 20 ms, before the after-run GC)
- Meminfo: dumpsys meminfo deltas in MB from the end of the warm-ups to the end: Native Heap Pss / Native Heap Alloc / Graphics / Unknown Pss
- Counters: images not recycled (vs start) / open media images at the end / live Mats (vs end of warm-ups)
- Instances: VMDebug instance count deltas from the start: Bitmap / org.opencv.core.Mat / AccessibilityNodeInfo, then AccessibilityNodeInfo against the end of the warm-ups (S7 / S7b fail above +50, ROADMAP P3.4)

| Scenario | Mode | Peak MB | Meminfo MB | Counters | Instances | Script stats (max) |
| --- | --- | --- | --- | --- | --- | --- |
| S4a | F | +8.09 | +0.87/+4.61/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes -2 vs warm-ups) | - |

Raw reports: the .json file with the same name (one report per line under `results`).
