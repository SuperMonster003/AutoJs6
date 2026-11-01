# Memory gate 23046RP50C (6551) 20261101

- Device: Xiaomi 23046RP50C, API 35, serial 968e9f18
- Host: org.autojs.autojs6 versionCode 6551 (6.8.0), debuggable
- Run: 23046RP50C-6551-20261101194655, label group-ocr-interval, 2026-11-01T11:46:55.932072+00:00 -> 2026-11-01T11:58:17.772310+00:00
- Device state at start: {'batteryLevel': 100, 'powered': ['USB'], 'wakefulness': 'Awake'}
- PROJECT_MEDIA app-op (ROADMAP Q3): `allow` during S4a normal, S4a force, S4g normal, S4g force, before `default`, afterwards `default`
- Warm-ups 2, iterations 20, poll 10000 ms, force delay 1000 ms, capture on, meminfo phases
- Cell: retained / Java heap MB / native heap MB / script threads / Rhino contexts / live engines (ROADMAP appendix A)
- Thresholds (T1): retained 0, Java <= 2 MB, native <= 4 MB (+1 frame for capture), threads and contexts back to start, engines back to start

| Scenario | Mode | Status | Cell | Reasons |
| --- | --- | --- | --- | --- |
| S1 empty script | N | pass | 0 / +0.10 / +0.00 / +0 / +0 / 0 |  |
| S1 empty script | F | pass | 0 / +0.09 / +0.02 / +0 / +0 / 0 |  |
| S4a captureScreen x 20 | N | pass | 0 / +0.09 / -1.90 / +0 / +0 / 0 |  |
| S4a captureScreen x 20 | F | pass | 0 / +0.12 / -2.20 / +0 / +0 / 0 |  |
| S4g setInterval + captureScreen + ocr.detect (ML Kit OCR plugin) + keepAwake, two exit handlers | N | pass | 0 / +0.15 / +0.13 / +0 / +0 / 0 |  |
| S4g setInterval + captureScreen + ocr.detect (ML Kit OCR plugin) + keepAwake, two exit handlers | F | pass | 0 / +0.12 / -2.21 / +0 / +0 / 0 |  |

## Native detail (ROADMAP P3.1)

- Peak: largest rise of the native heap inside a measured run (sampled every 20 ms, before the after-run GC)
- Meminfo: dumpsys meminfo deltas in MB from the end of the warm-ups to the end: Native Heap Pss / Native Heap Alloc / Graphics / Unknown Pss
- Counters: images not recycled (vs start) / open media images at the end / live Mats (vs end of warm-ups)
- Instances: VMDebug instance count deltas from the start: Bitmap / org.opencv.core.Mat / AccessibilityNodeInfo, then AccessibilityNodeInfo against the end of the warm-ups (S7 / S7b fail above +50, ROADMAP P3.4)
- Classes (ROADMAP P4.4), from the end of the warm-ups to the end: loaded classes (Debug.getLoadedClassCount; S8 fails when they grow in most measured runs, one-time steps do not count) / VMDebug java.lang.Class objects / VMDebug DexClassLoader / loaders registered by runtime.loadDex in the host / VMDebug WebView; `-` where unavailable (VMDebug only on API 24 / 28)

| Scenario | Mode | Peak MB | Meminfo MB | Counters | Instances | Classes | Script stats (max) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| S1 | N | +0.09 | -1.94/+0.00/+0.00/-0.01 | +0 / 0 / +0 | -/-/- | +20 / - / - / +0 / - | - |
| S1 | F | +0.09 | -2.20/+0.02/+0.00/+0.02 | +0 / 0 / +0 | -/-/- | +0 / - / - / +0 / - | - |
| S4a | N | +43.35 | +0.01/-1.90/-0.41/+0.08 | +0 / 0 / +0 | -/-/- | +0 / - / - / +0 / - | - |
| S4a | F | +43.27 | -0.49/-2.20/-0.41/+0.10 | +0 / 0 / +0 | -/-/- | +59 / - / - / +0 / - | - |
| S4g | N | +43.27 | +1.02/+0.12/+0.00/+0.08 | +0 / 0 / +0 | -/-/- | +4 / - / - / +0 / - | hits 0, ticks 3, ocrLines 72 |
| S4g | F | +43.25 | -8.45/-2.21/-0.41/+0.10 | +0 / 0 / +0 | -/-/- | +77 / - / - / +0 / - | - |

Raw reports: the .json file with the same name (one report per line under `results`).
