# Memory gate sdk_gphone64_x86_64 (5935) 20261023

- Device: Google sdk_gphone64_x86_64, API 35, serial emulator-5686
- Host: org.autojs.autojs6 versionCode 5935 (6.8.0), debuggable
- Run: sdk_gphone64_x86_64-5935-20261023204650, label mp2-fix-ci-local, 2026-10-23T12:46:50.416292+00:00 -> 2026-10-23T13:18:40.933305+00:00
- Device state at start: {'batteryLevel': 100, 'powered': ['AC'], 'wakefulness': 'Awake'}
- PROJECT_MEDIA app-op (ROADMAP Q3): `allow` during S4a normal, S4f normal, before `default`, afterwards `default`
- Warm-ups 2, iterations 20, poll 10000 ms, force delay 1000 ms, capture on, meminfo phases
- Cell: retained / Java heap MB / native heap MB / script threads / Rhino contexts / live engines (ROADMAP appendix A)
- Thresholds (T1): retained 0, Java <= 2 MB, native <= 4 MB (+1 frame for capture), threads and contexts back to start, engines back to start

| Scenario | Mode | Status | Cell | Reasons |
| --- | --- | --- | --- | --- |
| S1 empty script | N | pass | 0 / +0.07 / +0.02 / +0 / +0 / 0 |  |
| S2 threads.start x 10 + threads.pool | N | pass | 0 / +0.12 / +0.03 / +0 / +0 / 0 |  |
| S3 nested timers on main and child threads | N | pass | 0 / +0.09 / -0.01 / +0 / +0 / 0 |  |
| S4a captureScreen x 20 | N | pass | 0 / +0.08 / -0.02 / +0 / +0 / 0 |  |
| S4b images.read + findImage | N | pass | 0 / +0.06 / +0.02 / +0 / +0 / 0 |  |
| S4c images.copy / clip / resize chain | N | pass | 0 / +0.07 / +0.02 / +0 / +0 / 0 |  |
| S4d matchTemplate + oneShot | N | pass | 0 / +0.08 / -0.01 / +0 / +0 / 0 |  |
| S4e threaded copy + findImage + recycle (issue #372 / #200 pattern) | N | pass | 0 / +0.08 / +0.01 / +0 / +0 / 0 |  |
| S4f captureScreen x 200 without recycle (issue #204) | N | pass | 0 / +0.02 / +0.26 / +0 / +0 / 0 |  |
| S5 ui script with button, text and webview | N | pass | 0 / +0.07 / +0.27 / +0 / +0 / 0 |  |
| S6 floaty.window + floaty.rawWindow | N | pass | 0 / +0.12 / +0.07 / +0 / +0 / 0 |  |
| S7 events / key observer / sensors / accessibility events | N | pass | 0 / +0.08 / -0.00 / +0 / +0 / 0 |  |
| S7b className(...).find() loop on the accessibility tree | N | skipped | skipped | the host accessibility service is not bound to the test process (run with memoryGateAccessibilityWaitMs and turn the service off and on, ROADMAP appendix C Q9) |
| S8 JavaAdapter / interface adapters x 100 + runtime.loadDex of a fresh copy | N | pass | 0 / +0.07 / +0.10 / +0 / +0 / 0 |  |
| S9 engines.execScriptFile restarting itself 3 levels | N | pass | 0 / +0.12 / -0.00 / +0 / +0 / 0 |  |
| S10 http.get + WebSocket on a loopback server | N | pass | 0 / +0.07 / +0.08 / +0 / +0 / 0 |  |
| S11 device.* reads, vibrate and keepScreenOn | N | pass | 0 / +0.07 / +0.02 / +0 / +0 / 0 |  |
| S12 plugins.load of an installed official plugin | N | pass | 0 / +0.04 / +0.05 / +0 / +0 / 0 |  |

## Native detail (ROADMAP P3.1)

- Peak: largest rise of the native heap inside a measured run (sampled every 20 ms, before the after-run GC)
- Meminfo: dumpsys meminfo deltas in MB from the end of the warm-ups to the end: Native Heap Pss / Native Heap Alloc / Graphics / Unknown Pss
- Counters: images not recycled (vs start) / open media images at the end / live Mats (vs end of warm-ups)
- Instances: VMDebug instance count deltas from the start: Bitmap / org.opencv.core.Mat / AccessibilityNodeInfo, then AccessibilityNodeInfo against the end of the warm-ups (S7 / S7b fail above +50, ROADMAP P3.4)
- Classes (ROADMAP P4.4), from the end of the warm-ups to the end: loaded classes (Debug.getLoadedClassCount; S8 fails when they grow in most measured runs, one-time steps do not count) / VMDebug java.lang.Class objects / VMDebug DexClassLoader / loaders registered by runtime.loadDex in the host / VMDebug WebView; `-` where unavailable (VMDebug only on API 24 / 28)

| Scenario | Mode | Peak MB | Meminfo MB | Counters | Instances | Classes | Script stats (max) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| S1 | N | +1.42 | -0.15/+0.02/+0.00/+0.01 | +0 / 0 / +0 | -/-/- | +17 / - / - / +0 / - | - |
| S2 | N | +1.52 | +0.09/+0.03/+0.00/+0.02 | +0 / 0 / +0 | -/-/- | +21 / - / - / +0 / - | - |
| S3 | N | +1.41 | +0.08/+0.00/+0.00/+0.02 | +0 / 0 / +0 | -/-/- | +65 / - / - / +0 / - | - |
| S4a | N | +16.55 | +0.52/-0.02/+0.00/+0.01 | +0 / 0 / +0 | -/-/- | +4 / - / - / +0 / - | - |
| S4b | N | +7.90 | -0.03/+0.03/+0.00/+0.01 | +0 / 0 / +0 | -/-/- | +17 / - / - / +0 / - | - |
| S4c | N | +12.55 | +0.20/+0.01/+0.00/+0.01 | +0 / 0 / +0 | -/-/- | +17 / - / - / +0 / - | - |
| S4d | N | +7.90 | +0.13/-0.01/+0.00/+0.02 | +0 / 0 / +0 | -/-/- | +65 / - / - / +0 / - | - |
| S4e | N | +28.16 | -3.76/+0.00/+0.00/+0.02 | +0 / 0 / +0 | -/-/- | +65 / - / - / +0 / - | - |
| S4f | N | +16.54 | +0.50/+0.26/+0.00/+0.01 | +0 / 0 / +0 | -/-/- | +5 / - / - / +0 / - | nullFrames 0, frames 200, slowestCaptureMs 1411, totalMs 246368, maxOpenMediaImages 0 |
| S5 | N | +3.45 | +0.57/+0.28/+0.00/+0.56 | +0 / 0 / +0 | -/-/- | +48 / - / - / +0 / - | - |
| S6 | N | +1.91 | +0.17/+0.08/+0.00/+0.04 | +0 / 0 / +0 | -/-/- | +48 / - / - / +0 / - | - |
| S7 | N | +1.43 | - | +0 / 0 / +0 | -/-/- | +65 / - / - / +0 / - | - |
| S8 | N | +1.47 | -2.49/+0.10/+0.00/+0.02 | +0 / 0 / +0 | -/-/- | +65 (median +0 per run) / - / - / +0 / - | - |
| S9 | N | +4.20 | +0.17/-0.00/+0.00/+0.01 | +0 / 0 / +0 | -/-/- | +65 / - / - / +0 / - | - |
| S10 | N | +1.43 | +0.38/+0.08/+0.00/+0.01 | +0 / 0 / +0 | -/-/- | +0 / - / - / +0 / - | - |
| S11 | N | +1.41 | +0.09/+0.02/+0.00/+0.01 | +0 / 0 / +0 | -/-/- | +17 / - / - / +0 / - | - |
| S12 | N | +3.79 | -0.95/+0.05/+0.00/+0.02 | +0 / 0 / +0 | -/-/- | +17 / - / - / +0 / - | - |

Raw reports: the .json file with the same name (one report per line under `results`).
