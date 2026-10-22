# Memory gate Android SDK built for x86 (5935) 20261022

- Device: Google Android SDK built for x86, API 24, serial emulator-5660
- Host: org.autojs.autojs6 versionCode 5935 (6.8.0), debuggable
- Run: Android_SDK_built_for_x86-5935-20261022211412, label q56-after, 2026-10-22T13:14:12.767938+00:00 -> 2026-10-22T13:15:02.034006+00:00
- Device state at start: {'batteryLevel': 100, 'powered': ['AC'], 'wakefulness': 'Awake'}
- Warm-ups 2, iterations 20, poll 10000 ms, force delay 1000 ms, capture off, meminfo phases
- Cell: retained / Java heap MB / native heap MB / script threads / Rhino contexts / live engines (ROADMAP appendix A)
- Thresholds (T1): retained 0, Java <= 2 MB, native <= 4 MB (+1 frame for capture), threads and contexts back to start, engines back to start

| Scenario | Mode | Status | Cell | Reasons |
| --- | --- | --- | --- | --- |
| S13 speech.listen session open when the script ends | N | pass | 0 / +0.10 / +0.01 / +0 / +0 / 0 |  |
| S13 speech.listen session open when the script ends | F | pass | 0 / +0.08 / -0.06 / +0 / +0 / 0 |  |

## Native detail (ROADMAP P3.1)

- Peak: largest rise of the native heap inside a measured run (sampled every 20 ms, before the after-run GC)
- Meminfo: dumpsys meminfo deltas in MB from the end of the warm-ups to the end: Native Heap Pss / Native Heap Alloc / Graphics / Unknown Pss
- Counters: images not recycled (vs start) / open media images at the end / live Mats (vs end of warm-ups)
- Instances: VMDebug instance count deltas from the start: Bitmap / org.opencv.core.Mat / AccessibilityNodeInfo, then AccessibilityNodeInfo against the end of the warm-ups (S7 / S7b fail above +50, ROADMAP P3.4)
- Classes (ROADMAP P4.4), from the end of the warm-ups to the end: loaded classes (Debug.getLoadedClassCount; S8 fails when they grow in most measured runs, one-time steps do not count) / VMDebug java.lang.Class objects / VMDebug DexClassLoader / loaders registered by runtime.loadDex in the host / VMDebug WebView; `-` where unavailable (VMDebug only on API 24 / 28)

| Scenario | Mode | Peak MB | Meminfo MB | Counters | Instances | Classes | Script stats (max) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| S13 | N | +0.49 | +0.05/+0.01/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +22 / +22 / +0 / +0 / +0 | refused 1 |
| S13 | F | +0.44 | -0.02/-0.06/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +1 / +1 / +0 / +0 / +0 | refused 1 |

Raw reports: the .json file with the same name (one report per line under `results`).
