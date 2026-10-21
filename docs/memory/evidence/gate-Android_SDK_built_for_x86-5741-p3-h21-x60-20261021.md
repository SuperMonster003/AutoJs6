# Memory gate Android SDK built for x86 (5741) 20261021

- Device: Google Android SDK built for x86, API 24, serial emulator-5580
- Host: org.autojs.autojs6 versionCode 5741 (6.8.0), debuggable
- Run: Android_SDK_built_for_x86-5741-20261021163319, label p3-h21-x60, 2026-10-21T08:33:19.205641+00:00 -> 2026-10-21T08:34:01.456217+00:00
- Device state at start: {'batteryLevel': 100, 'powered': ['AC'], 'wakefulness': 'Awake'}
- Warm-ups 2, iterations 60, poll 10000 ms, force delay 1000 ms, capture off, meminfo phases
- Cell: retained / Java heap MB / native heap MB / script threads / Rhino contexts / live engines (ROADMAP appendix A)
- Thresholds (T1): retained 0, Java <= 2 MB, native <= 4 MB (+1 frame for capture), threads and contexts back to start, engines back to start

| Scenario | Mode | Status | Cell | Reasons |
| --- | --- | --- | --- | --- |
| S7 events / key observer / sensors / accessibility events | N | pass | 0 / +0.21 / +0.01 / +0 / +0 / 0 |  |

## Native detail (ROADMAP P3.1)

- Peak: largest rise of the native heap inside a measured run (sampled every 20 ms, before the after-run GC)
- Meminfo: dumpsys meminfo deltas in MB from the end of the warm-ups to the end: Native Heap Pss / Native Heap Alloc / Graphics / Unknown Pss
- Counters: images not recycled (vs start) / open media images at the end / live Mats (vs end of warm-ups)
- Instances: VMDebug instance count deltas from the start: Bitmap / org.opencv.core.Mat / AccessibilityNodeInfo, then AccessibilityNodeInfo against the end of the warm-ups (S7 / S7b fail above +50, ROADMAP P3.4)

| Scenario | Mode | Peak MB | Meminfo MB | Counters | Instances | Script stats (max) |
| --- | --- | --- | --- | --- | --- | --- |
| S7 | N | +0.51 | - | +0 / 0 / +0 | -1/+0/+0 (nodes +0 vs warm-ups) | - |

Raw reports: the .json file with the same name (one report per line under `results`).
