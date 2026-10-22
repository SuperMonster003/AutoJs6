# Bridge gate sdk_gphone64_x86_64 (5935) 20261022

- Device: Google sdk_gphone64_x86_64, API 35, serial emulator-5690
- Host: org.autojs.autojs6 versionCode 5935 (6.8.0), debuggable
- Plugin: io.github.supermonster003.autojs6.plugin.nodejs versionCode 292 (1.32.0), commit 6d4e6bdb
- hidden_api_policy (ROADMAP D9): 1 during each run, before `None`, afterwards `None`
- Run: sdk_gphone64_x86_64-5935-20261022230219, label mp2-baseline, 2026-10-22T15:02:19.220681+00:00 -> 2026-10-22T16:00:18.203898+00:00
- Device state at start: {'batteryLevel': 100, 'powered': ['AC'], 'wakefulness': 'Awake'}
- PROJECT_MEDIA app-op (ROADMAP Q3): `allow` during ND8 normal, ND8 force, before `default`, afterwards `default`
- Warm-ups 2, iterations 20, poll 10000 ms, force delay 1000 ms, capture on, meminfo phases
- Cell (docs/memory-phase2/ROADMAP.md appendix A): retained / Java heap MB / native heap MB / script threads, b bridge threads / bindings: h host bridge registries vs start, s system connections to the plugin vs start / live engines / plugin PSS MB at the end (change since measured run 2)
- Thresholds (T1): retained 0, Java <= 2 MB, native <= 4 MB (+1 frame for capture), script threads, engines and bridge registries back to start (Node connection pool may keep 1), system connections <= start + 1, bridge threads back to the end of the warm-ups; plugin PSS recorded only (R9)

| Scenario | Mode | Status | Cell | Reasons |
| --- | --- | --- | --- | --- |
| ND1 Empty script | N | fail | 8 / +9.85 / +0.20 / +0 b+1 / h+0 s+0 / 0 / 85.1 (-0.28) | retained runs 8/20 (engine 8, runtime 8, node.broker 8, node.provider 8); java heap +9.85 MB; registry LooperHelper.loopers +7; bridge threads +1: AutoJs6-Node-Plugin-Rpc |
| ND1 Empty script | F | fail | 1 / +8.34 / +1.82 / +0 b+1 / h+0 s+0 / 0 / 82.9 (-0.80) | retained runs 1/20 (engine 1, runtime 1, node.broker 1, node.provider 1); java heap +8.34 MB; registry LooperHelper.loopers +7; bridge threads +1: AutoJs6-Node-Plugin-Rpc |
| ND2 Host capabilities through the broker | N | fail | 8 / +9.87 / +0.21 / +0 b+0 / h+0 s+0 / 0 / 87.6 (+0.99) | retained runs 8/20 (engine 8, runtime 8, node.broker 8, node.provider 8); java heap +9.87 MB; registry LooperHelper.loopers +6 |
| ND2 Host capabilities through the broker | F | fail | 8 / +8.33 / +1.90 / +0 b+0 / h+0 s+0 / 0 / 85.4 (+1.11) | retained runs 8/20 (engine 8, runtime 8, node.broker 8, node.provider 8); java heap +8.33 MB; registry LooperHelper.loopers +7 |
| ND3 Pushed callbacks | N | fail | 8 / +9.88 / +0.21 / +0 b+0 / h+0 s+0 / 0 / 84.8 (+0.29) | retained runs 8/20 (engine 8, runtime 8, node.broker 8, node.provider 8); java heap +9.88 MB; registry LooperHelper.loopers +7 |
| ND3 Pushed callbacks | F | fail | 8 / +8.27 / +1.85 / +0 b+1 / h+0 s+0 / 0 / 84.5 (+0.46) | retained runs 8/20 (engine 8, runtime 8, node.broker 8, node.provider 8); java heap +8.27 MB; registry LooperHelper.loopers +7; bridge threads +1: AutoJs6-Node-Plugin-Rpc |
| ND4 Plugin process killed | N | fail | 0 / +0.28 / +0.03 / +0 b+1 / h+0 s-1 / 0 / - | bridge threads +1: AutoJs6-Node-Plugin-Rpc |
| ND4 Plugin process killed | F | fail | 0 / +0.27 / +0.03 / +0 b+1 / h+0 s-1 / 0 / - | registry LooperHelper.loopers +1; bridge threads +1: AutoJs6-Node-Plugin-Rpc |
| ND5 UI session | N | fail | 8 / +9.85 / -4.72 / +0 b+1 / h+0 s+0 / 0 / 88.6 (+0.70) | retained runs 8/20 (engine 8, runtime 8, node.broker 8, node.provider 8); java heap +9.85 MB; registry LooperHelper.loopers +7; bridge threads +1: AutoJs6-Node-Plugin-Rpc |
| ND5 UI session | F | fail | 5 / +8.33 / +1.87 / +0 b+1 / h+0 s+0 / 0 / 89.4 (+0.21) | retained runs 5/20 (node.broker 5, node.provider 5, engine 1, runtime 1); java heap +8.33 MB; registry LooperHelper.loopers +6; bridge threads +1: AutoJs6-Node-Plugin-Rpc |
| ND6 Compose | N | skipped | skipped | script: compose.isAvailable() is false (Block Compose plugin missing or disabled) |
| ND6 Compose | F | skipped | skipped | script: compose.isAvailable() is false (Block Compose plugin missing or disabled) |
| ND7a Web page and action | N | fail | 8 / +9.95 / -4.19 / +0 b+1 / h+0 s+0 / 0 / 89.9 (+0.55) | retained runs 8/20 (engine 8, runtime 8, node.broker 8, node.provider 8); java heap +9.95 MB; registry LooperHelper.loopers +6; bridge threads +1: AutoJs6-Node-Plugin-Rpc |
| ND7a Web page and action | F | fail | 1 / +8.36 / -2.44 / +0 b+1 / h+0 s+0 / 0 / 89.0 (+1.42) | retained runs 1/20 (engine 1, runtime 1, node.broker 1, node.provider 1); java heap +8.36 MB; registry LooperHelper.loopers +7; bridge threads +1: AutoJs6-Node-Plugin-Rpc |
| ND7b Web local download | N | fail | 2 / +8.30 / +0.35 / +0 b+1 / h+0 s+0 / 0 / 89.4 (+1.66) | retained runs 2/20 (engine 2, runtime 2, node.broker 2, node.provider 2); java heap +8.30 MB; registry LooperHelper.loopers +5; bridge threads +1: AutoJs6-Node-Plugin-Rpc |
| ND7b Web local download | F | fail | 7 / +7.00 / -2.84 / +0 b+1 / h+0 s+0 / 0 / 88.2 (-1.06) | retained runs 7/20 (engine 7, runtime 7, node.broker 7, node.provider 7); java heap +7.00 MB; registry LooperHelper.loopers +6; bridge threads +1: AutoJs6-Node-Plugin-Rpc |
| ND7c Web media session | N | fail | 8 / +10.05 / +0.38 / +0 b+1 / h+0 s+0 / 0 / 91.1 (+0.71) | retained runs 8/20 (engine 8, runtime 8, node.broker 8, node.provider 8); java heap +10.05 MB; registry LooperHelper.loopers +6; bridge threads +1: AutoJs6-Node-Plugin-Rpc |
| ND7c Web media session | F | fail | 8 / +8.35 / -2.38 / +0 b+1 / h+0 s+0 / 0 / 90.6 (+0.60) | retained runs 8/20 (engine 8, runtime 8, node.broker 8, node.provider 8); java heap +8.35 MB; registry LooperHelper.loopers +6; bridge threads +1: AutoJs6-Node-Plugin-Rpc |
| ND8 Screen capture | N | fail | 7 / +8.30 / +0.24 / +0 b+1 / h+0 s+0 / 0 / 91.2 (+0.87) | retained runs 7/20 (engine 7, runtime 7, node.broker 7, node.provider 7); java heap +8.30 MB; registry LooperHelper.loopers +6; bridge threads +1: AutoJs6-Node-Plugin-Rpc |
| ND8 Screen capture | F | fail | 8 / +8.38 / +1.99 / +0 b+1 / h+0 s+0 / 0 / 91.0 (+2.62) | retained runs 8/20 (engine 8, runtime 8, node.broker 8, node.provider 8); java heap +8.38 MB; registry LooperHelper.loopers +6; bridge threads +1: AutoJs6-Node-Plugin-Rpc |
| ND9 Overlay | N | fail | 8 / +9.89 / +0.24 / +0 b+1 / h+0 s+0 / 0 / 92.5 (+2.09) | retained runs 8/20 (engine 8, runtime 8, node.broker 8, node.provider 8); java heap +9.89 MB; registry LooperHelper.loopers +7; bridge threads +1: AutoJs6-Node-Plugin-Rpc |
| ND9 Overlay | F | fail | 7 / +6.98 / +1.58 / +0 b+1 / h+0 s+0 / 0 / 89.0 (+0.83) | retained runs 7/20 (engine 7, runtime 7, node.broker 7, node.provider 7); java heap +6.98 MB; registry LooperHelper.loopers +6; bridge threads +1: AutoJs6-Node-Plugin-Rpc |
| ND10 Dialog | N | fail | 9 / +11.53 / +1.22 / +0 b+1 / h+0 s+0 / 0 / 89.3 (-2.63) | retained runs 9/20 (engine 9, runtime 9, node.broker 9, node.provider 9); java heap +11.53 MB; registry LooperHelper.loopers +7; bridge threads +1: AutoJs6-Node-Plugin-Rpc |
| ND10 Dialog | F | fail | 8 / +8.49 / -2.15 / +0 b+1 / h+0 s+0 / 0 / 89.3 (-0.83) | retained runs 8/20 (engine 8, runtime 8, node.broker 8, node.provider 8); java heap +8.49 MB; registry LooperHelper.loopers +6; bridge threads +1: AutoJs6-Node-Plugin-Rpc |
| ND11 Image handles | N | fail | 8 / +10.15 / +0.23 / +0 b+1 / h+0 s+0 / 0 / 94.6 (+1.96) | retained runs 8/20 (engine 8, runtime 8, node.broker 8, node.provider 8); java heap +10.15 MB; registry LooperHelper.loopers +7; bridge threads +1: AutoJs6-Node-Plugin-Rpc |
| ND11 Image handles | F | fail | 4 / +8.37 / +2.15 / +0 b+1 / h+0 s+0 / 0 / 91.8 (+0.67) | retained runs 4/20 (node.broker 4, node.provider 4, engine 1, runtime 1); java heap +8.37 MB; registry LooperHelper.loopers +7; bridge threads +1: AutoJs6-Node-Plugin-Rpc |
| ND12 Audio recording | N | fail | 7 / +8.23 / +0.19 / +0 b+1 / h+0 s+0 / 0 / 91.2 (+1.09) | retained runs 7/20 (engine 7, runtime 7, node.broker 7, node.provider 7); java heap +8.23 MB; registry LooperHelper.loopers +6; bridge threads +1: AutoJs6-Node-Plugin-Rpc |
| ND12 Audio recording | F | fail | 8 / +8.33 / +1.83 / +0 b+1 / h+0 s+0 / 0 / 90.8 (+0.26) | retained runs 8/20 (engine 8, runtime 8, node.broker 8, node.provider 8); java heap +8.33 MB; registry LooperHelper.loopers +7; bridge threads +1: AutoJs6-Node-Plugin-Rpc |
| ND13 ColorOS bind refusal loop | N | skipped | skipped | ND13 runs on ColorOS only (OPD2413); this device is Google sdk_gphone64_x86_64 |
| ND13 ColorOS bind refusal loop | F | skipped | skipped | ND13 runs on ColorOS only (OPD2413); this device is Google sdk_gphone64_x86_64 |

## Bridge detail (docs/memory-phase2/ROADMAP.md T1 (d) / (h) / (i))

- Registries: host bridge tables whose size differs from the start (StaticRegistries.bridgeCounts)
- Bindings: connections of the host to the plugin / all bound service connections in LoadedApk.mServices, before the first run -> at the end (n/a: unreadable)
- New threads: bridge threads alive at the end that were not alive at the end of the warm-ups
- Plugin PSS MB: all processes of the plugin before the first run / after measured run 2 / at the end (recorded only, R9)
- Script: largest value of each gateStat over all runs; ended by error: runs of an expected failure (ND4)

| Scenario | Mode | Registries | Bindings | New threads | Plugin PSS MB | Script |
| --- | --- | --- | --- | --- | --- | --- |
| ND1 | N | +0 | 1/1 -> 1/1 | AutoJs6-Node-Plugin-Rpc | 75.6 / 85.4 / 85.1 | - |
| ND1 | F | +0 | 1/1 -> 1/1 | AutoJs6-Node-Plugin-Rpc | 83.3 / 83.7 / 82.9 | - |
| ND2 | N | +0 | 1/1 -> 1/1 | - | 73.4 / 86.6 / 87.6 | deviceInfoKeys 12, pngBytes 275 |
| ND2 | F | +0 | 1/1 -> 1/1 | - | 85.5 / 84.3 / 85.4 | deviceInfoKeys 12, pngBytes 275 |
| ND3 | N | +0 | 1/1 -> 1/1 | - | 83.1 / 84.5 / 84.8 | batteryEvents 1, sensorEvents 3 |
| ND3 | F | +0 | 1/1 -> 1/1 | AutoJs6-Node-Plugin-Rpc | 83.2 / 84.1 / 84.5 | sensorEvents 3 |
| ND4 | N | +0 | 1/1 -> 0/0 | AutoJs6-Node-Plugin-Rpc | 83.2 / - / - | ended by error 22 |
| ND4 | F | +0 | 1/1 -> 0/0 | AutoJs6-Node-Plugin-Rpc | 65.9 / - / - | ended by error 22 |
| ND5 | N | +0 | 1/1 -> 1/1 | AutoJs6-Node-Plugin-Rpc | 65.8 / 87.9 / 88.6 | - |
| ND5 | F | +0 | 1/1 -> 1/1 | AutoJs6-Node-Plugin-Rpc | 84.6 / 89.2 / 89.4 | - |
| ND7a | N | +0 | 1/1 -> 1/2 | AutoJs6-Node-Plugin-Rpc | 90.3 / 89.3 / 89.9 | chooserCancelled 1 |
| ND7a | F | +0 | 1/1 -> 1/2 | AutoJs6-Node-Plugin-Rpc | 88.2 / 87.6 / 89.0 | - |
| ND7b | N | +0 | 1/1 -> 1/2 | AutoJs6-Node-Plugin-Rpc | 87.9 / 87.7 / 89.4 | downloadDone 1 |
| ND7b | F | +0 | 1/1 -> 1/2 | AutoJs6-Node-Plugin-Rpc | 87.2 / 89.2 / 88.2 | downloadDone 1 |
| ND7c | N | +0 | 1/1 -> 1/2 | AutoJs6-Node-Plugin-Rpc | 86.7 / 90.4 / 91.1 | startAttempts 2 |
| ND7c | F | +0 | 1/1 -> 1/2 | AutoJs6-Node-Plugin-Rpc | 88.9 / 90.0 / 90.6 | startAttempts 2 |
| ND8 | N | +0 | 1/1 -> 1/1 | AutoJs6-Node-Plugin-Rpc | 88.7 / 90.3 / 91.2 | - |
| ND8 | F | +0 | 1/1 -> 1/1 | AutoJs6-Node-Plugin-Rpc | 89.1 / 88.4 / 91.0 | - |
| ND9 | N | +0 | 1/1 -> 1/1 | AutoJs6-Node-Plugin-Rpc | 88.7 / 90.4 / 92.5 | - |
| ND9 | F | +0 | 1/1 -> 1/1 | AutoJs6-Node-Plugin-Rpc | 90.7 / 88.2 / 89.0 | - |
| ND10 | N | +0 | 1/1 -> 1/1 | AutoJs6-Node-Plugin-Rpc | 87.5 / 91.9 / 89.3 | - |
| ND10 | F | +0 | 1/1 -> 1/1 | AutoJs6-Node-Plugin-Rpc | 87.6 / 90.1 / 89.3 | - |
| ND11 | N | +0 | 1/1 -> 1/1 | AutoJs6-Node-Plugin-Rpc | 87.5 / 92.6 / 94.6 | ocrDone 0 |
| ND11 | F | +0 | 1/1 -> 1/1 | AutoJs6-Node-Plugin-Rpc | 91.9 / 91.1 / 91.8 | ocrDone 0 |
| ND12 | N | +0 | 1/1 -> 1/1 | AutoJs6-Node-Plugin-Rpc | 89.4 / 90.1 / 91.2 | - |
| ND12 | F | +0 | 1/1 -> 1/1 | AutoJs6-Node-Plugin-Rpc | 89.1 / 90.5 / 90.8 | - |

## Native detail (ROADMAP P3.1)

- Peak: largest rise of the native heap inside a measured run (sampled every 20 ms, before the after-run GC)
- Meminfo: dumpsys meminfo deltas in MB from the end of the warm-ups to the end: Native Heap Pss / Native Heap Alloc / Graphics / Unknown Pss
- Counters: images not recycled (vs start) / open media images at the end / live Mats (vs end of warm-ups)
- Instances: VMDebug instance count deltas from the start: Bitmap / org.opencv.core.Mat / AccessibilityNodeInfo, then AccessibilityNodeInfo against the end of the warm-ups (S7 / S7b fail above +50, ROADMAP P3.4)
- Classes (ROADMAP P4.4), from the end of the warm-ups to the end: loaded classes (Debug.getLoadedClassCount; S8 fails when they grow in most measured runs, one-time steps do not count) / VMDebug java.lang.Class objects / VMDebug DexClassLoader / loaders registered by runtime.loadDex in the host / VMDebug WebView; `-` where unavailable (VMDebug only on API 24 / 28)

| Scenario | Mode | Peak MB | Meminfo MB | Counters | Instances | Classes | Script stats (max) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ND1 | N | +1.71 | -0.01/+0.20/+0.00/+0.04 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| ND1 | F | +1.75 | +1.50/+1.82/+0.00/+0.03 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| ND2 | N | +1.76 | +0.33/+0.21/+0.00/+0.03 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| ND2 | F | +1.76 | +1.46/+1.90/+0.00/+0.04 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| ND3 | N | +1.49 | +0.22/+0.21/+0.00/+0.01 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| ND3 | F | +1.73 | +1.42/+1.84/+0.00/+0.04 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| ND4 | N | +1.52 | +0.04/+0.02/+0.00/+0.01 | +0 / 0 / +0 | - | +71 / +71 / +0 / +0 / +0 | - |
| ND4 | F | +1.71 | -0.70/+0.03/+0.00/+0.01 | +0 / 0 / +0 | - | +48 / +48 / +0 / +0 / +0 | - |
| ND5 | N | +9.15 | -4.16/-4.72/+0.00/+0.06 | +0 / 0 / +0 | - | +2 / +2 / +0 / +0 / +0 | - |
| ND5 | F | +9.18 | +1.77/+1.87/+0.00/+0.05 | +0 / 0 / +0 | - | +2 / +2 / +0 / +0 / +0 | - |
| ND7a | N | +10.70 | -3.48/-4.19/+0.00/-0.23 | +0 / 0 / +0 | - | +4 / +4 / +0 / +0 / +0 | - |
| ND7a | F | +9.30 | -2.08/-2.43/+0.00/-0.04 | +0 / 0 / +0 | - | +6 / +6 / +0 / +0 / +0 | - |
| ND7b | N | +9.30 | +0.93/+0.35/+0.00/-0.57 | +0 / 0 / +0 | - | +2 / +2 / +0 / +0 / +0 | - |
| ND7b | F | +4.17 | -2.21/-2.84/+0.00/-0.04 | +0 / 0 / +0 | - | +2 / +2 / +0 / +0 / +0 | - |
| ND7c | N | +10.71 | +0.56/+0.38/+0.00/-0.36 | +0 / 0 / +0 | - | +2 / +2 / +0 / +0 / +0 | - |
| ND7c | F | +10.71 | -2.16/-2.38/+0.00/-0.00 | +0 / 0 / +0 | - | +2 / +2 / +0 / +0 / +0 | - |
| ND8 | N | +16.55 | +0.50/+0.24/+0.00/+0.02 | +0 / 0 / +0 | - | +2 / +2 / +0 / +0 / +0 | - |
| ND8 | F | +16.53 | +1.64/+1.99/+0.00/+0.04 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| ND9 | N | +10.05 | +0.55/+0.24/+0.00/+0.03 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| ND9 | F | +10.05 | +1.52/+1.59/+0.00/+0.04 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| ND10 | N | +8.94 | +1.36/+1.22/+0.00/+0.04 | +0 / 0 / +0 | - | +2 / +2 / +0 / +0 / +0 | - |
| ND10 | F | +7.62 | -1.63/-2.15/+0.00/+0.03 | +0 / 0 / +0 | - | +2 / +2 / +0 / +0 / +0 | - |
| ND11 | N | +1.80 | +0.21/+0.24/+0.00/+0.03 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| ND11 | F | +1.82 | +1.59/+2.15/+0.00/+0.03 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| ND12 | N | +1.72 | +0.47/+0.19/+0.00/+0.04 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| ND12 | F | +1.78 | +1.27/+1.83/+0.00/+0.05 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |

## Shortest strong reference paths (shark, bridge session objects and exited ScriptRuntime instances)

### ND1 N: NodeJsHostCapabilityBroker 1, AndroidNodeBridgeCapabilityProvider 1, NodePluginJavaScriptEngine 1, ScriptRuntime 1 in the dump, dump 2343 ms, analysis 12075 ms

- 1 object(s), root JNI_GLOBAL:  -> NodeJsHostCapabilityBroker
- 1 object(s), root JNI_GLOBAL: NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback -> NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener -> NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0 -> NodePluginJavaScriptEngine

```
JNI_GLOBAL
  -> org.autojs.autojs.engine.NodeJsHostCapabilityBroker
```

```
JNI_GLOBAL
  org.autojs.autojs.engine.NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback
  org.autojs.autojs.engine.NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener
  org.autojs.autojs.engine.NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0
  -> org.autojs.autojs.engine.NodePluginJavaScriptEngine
```

### ND1 F: NodeJsHostCapabilityBroker 1, AndroidNodeBridgeCapabilityProvider 1, NodePluginJavaScriptEngine 1, ScriptRuntime 1 in the dump, dump 2185 ms, analysis 16558 ms

- 1 object(s), root JNI_GLOBAL:  -> NodeJsHostCapabilityBroker
- 1 object(s), root JNI_GLOBAL: NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback -> NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener -> NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0 -> NodePluginJavaScriptEngine

```
JNI_GLOBAL
  -> org.autojs.autojs.engine.NodeJsHostCapabilityBroker
```

```
JNI_GLOBAL
  org.autojs.autojs.engine.NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback
  org.autojs.autojs.engine.NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener
  org.autojs.autojs.engine.NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0
  -> org.autojs.autojs.engine.NodePluginJavaScriptEngine
```

### ND2 N: NodeJsHostCapabilityBroker 1, AndroidNodeBridgeCapabilityProvider 1, NodePluginJavaScriptEngine 1, ScriptRuntime 1 in the dump, dump 4075 ms, analysis 19828 ms

- 1 object(s), root JNI_GLOBAL:  -> NodeJsHostCapabilityBroker
- 1 object(s), root JNI_GLOBAL: NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback -> NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener -> NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0 -> NodePluginJavaScriptEngine

```
JNI_GLOBAL
  -> org.autojs.autojs.engine.NodeJsHostCapabilityBroker
```

```
JNI_GLOBAL
  org.autojs.autojs.engine.NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback
  org.autojs.autojs.engine.NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener
  org.autojs.autojs.engine.NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0
  -> org.autojs.autojs.engine.NodePluginJavaScriptEngine
```

### ND2 F: NodeJsHostCapabilityBroker 2, AndroidNodeBridgeCapabilityProvider 2, NodePluginJavaScriptEngine 2, ScriptRuntime 2 in the dump, dump 1926 ms, analysis 14774 ms

- 2 object(s), root JNI_GLOBAL:  -> NodeJsHostCapabilityBroker
- 2 object(s), root JNI_GLOBAL: NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback -> NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener -> NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0 -> NodePluginJavaScriptEngine

```
JNI_GLOBAL
  -> org.autojs.autojs.engine.NodeJsHostCapabilityBroker
```

```
JNI_GLOBAL
  org.autojs.autojs.engine.NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback
  org.autojs.autojs.engine.NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener
  org.autojs.autojs.engine.NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0
  -> org.autojs.autojs.engine.NodePluginJavaScriptEngine
```

### ND3 N: NodeJsHostCapabilityBroker 1, AndroidNodeBridgeCapabilityProvider 1, NodePluginJavaScriptEngine 1, ScriptRuntime 1 in the dump, dump 2073 ms, analysis 11478 ms

- 1 object(s), root JNI_GLOBAL:  -> NodeJsHostCapabilityBroker
- 1 object(s), root JNI_GLOBAL: NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback -> NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener -> NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0 -> NodePluginJavaScriptEngine

```
JNI_GLOBAL
  -> org.autojs.autojs.engine.NodeJsHostCapabilityBroker
```

```
JNI_GLOBAL
  org.autojs.autojs.engine.NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback
  org.autojs.autojs.engine.NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener
  org.autojs.autojs.engine.NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0
  -> org.autojs.autojs.engine.NodePluginJavaScriptEngine
```

### ND3 F: NodeJsHostCapabilityBroker 1, AndroidNodeBridgeCapabilityProvider 1, NodePluginJavaScriptEngine 1, ScriptRuntime 1 in the dump, dump 1992 ms, analysis 11541 ms

- 1 object(s), root JNI_GLOBAL:  -> NodeJsHostCapabilityBroker
- 1 object(s), root JNI_GLOBAL: NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback -> NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener -> NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0 -> NodePluginJavaScriptEngine

```
JNI_GLOBAL
  -> org.autojs.autojs.engine.NodeJsHostCapabilityBroker
```

```
JNI_GLOBAL
  org.autojs.autojs.engine.NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback
  org.autojs.autojs.engine.NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener
  org.autojs.autojs.engine.NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0
  -> org.autojs.autojs.engine.NodePluginJavaScriptEngine
```

### ND5 N: NodeJsHostCapabilityBroker 4, AndroidNodeBridgeCapabilityProvider 4, NodePluginJavaScriptEngine 1, ScriptRuntime 1 in the dump, dump 4037 ms, analysis 32781 ms

- 4 object(s), root JNI_GLOBAL:  -> NodeJsHostCapabilityBroker
- 1 object(s), root JNI_GLOBAL: NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback -> NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener -> NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0 -> NodePluginJavaScriptEngine

```
JNI_GLOBAL
  -> org.autojs.autojs.engine.NodeJsHostCapabilityBroker
```

```
JNI_GLOBAL
  org.autojs.autojs.engine.NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback
  org.autojs.autojs.engine.NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener
  org.autojs.autojs.engine.NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0
  -> org.autojs.autojs.engine.NodePluginJavaScriptEngine
```

### ND5 F: NodeJsHostCapabilityBroker 5, AndroidNodeBridgeCapabilityProvider 5, NodePluginJavaScriptEngine 1, ScriptRuntime 1 in the dump, dump 2725 ms, analysis 14567 ms

- 5 object(s), root JNI_GLOBAL:  -> NodeJsHostCapabilityBroker
- 1 object(s), root JNI_GLOBAL: NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback -> NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener -> NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0 -> NodePluginJavaScriptEngine

```
JNI_GLOBAL
  -> org.autojs.autojs.engine.NodeJsHostCapabilityBroker
```

```
JNI_GLOBAL
  org.autojs.autojs.engine.NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback
  org.autojs.autojs.engine.NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener
  org.autojs.autojs.engine.NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0
  -> org.autojs.autojs.engine.NodePluginJavaScriptEngine
```

### ND7a N: NodeJsHostCapabilityBroker 1, AndroidNodeBridgeCapabilityProvider 1, NodePluginJavaScriptEngine 1, ScriptRuntime 1 in the dump, dump 2104 ms, analysis 12468 ms

- 1 object(s), root JNI_GLOBAL:  -> NodeJsHostCapabilityBroker
- 1 object(s), root JNI_GLOBAL: NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback -> NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener -> NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0 -> NodePluginJavaScriptEngine

```
JNI_GLOBAL
  -> org.autojs.autojs.engine.NodeJsHostCapabilityBroker
```

```
JNI_GLOBAL
  org.autojs.autojs.engine.NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback
  org.autojs.autojs.engine.NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener
  org.autojs.autojs.engine.NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0
  -> org.autojs.autojs.engine.NodePluginJavaScriptEngine
```

### ND7a F: NodeJsHostCapabilityBroker 1, AndroidNodeBridgeCapabilityProvider 1, NodePluginJavaScriptEngine 1, ScriptRuntime 1 in the dump, dump 2119 ms, analysis 12420 ms

- 1 object(s), root JNI_GLOBAL:  -> NodeJsHostCapabilityBroker
- 1 object(s), root JNI_GLOBAL: NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback -> NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener -> NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0 -> NodePluginJavaScriptEngine

```
JNI_GLOBAL
  -> org.autojs.autojs.engine.NodeJsHostCapabilityBroker
```

```
JNI_GLOBAL
  org.autojs.autojs.engine.NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback
  org.autojs.autojs.engine.NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener
  org.autojs.autojs.engine.NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0
  -> org.autojs.autojs.engine.NodePluginJavaScriptEngine
```

### ND7b N: NodeJsHostCapabilityBroker 2, AndroidNodeBridgeCapabilityProvider 2, NodePluginJavaScriptEngine 2, ScriptRuntime 2 in the dump, dump 2476 ms, analysis 23673 ms

- 2 object(s), root JNI_GLOBAL:  -> NodeJsHostCapabilityBroker
- 2 object(s), root JNI_GLOBAL: NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback -> NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener -> NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0 -> NodePluginJavaScriptEngine

```
JNI_GLOBAL
  -> org.autojs.autojs.engine.NodeJsHostCapabilityBroker
```

```
JNI_GLOBAL
  org.autojs.autojs.engine.NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback
  org.autojs.autojs.engine.NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener
  org.autojs.autojs.engine.NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0
  -> org.autojs.autojs.engine.NodePluginJavaScriptEngine
```

### ND7b F: NodeJsHostCapabilityBroker 1, AndroidNodeBridgeCapabilityProvider 1, NodePluginJavaScriptEngine 1, ScriptRuntime 1 in the dump, dump 2390 ms, analysis 13295 ms

- 1 object(s), root JNI_GLOBAL:  -> NodeJsHostCapabilityBroker
- 1 object(s), root JNI_GLOBAL: NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback -> NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener -> NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0 -> NodePluginJavaScriptEngine

```
JNI_GLOBAL
  -> org.autojs.autojs.engine.NodeJsHostCapabilityBroker
```

```
JNI_GLOBAL
  org.autojs.autojs.engine.NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback
  org.autojs.autojs.engine.NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener
  org.autojs.autojs.engine.NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0
  -> org.autojs.autojs.engine.NodePluginJavaScriptEngine
```

### ND7c N: NodeJsHostCapabilityBroker 1, AndroidNodeBridgeCapabilityProvider 1, NodePluginJavaScriptEngine 1, ScriptRuntime 1 in the dump, dump 2308 ms, analysis 11952 ms

- 1 object(s), root JNI_GLOBAL:  -> NodeJsHostCapabilityBroker
- 1 object(s), root JNI_GLOBAL: NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback -> NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener -> NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0 -> NodePluginJavaScriptEngine

```
JNI_GLOBAL
  -> org.autojs.autojs.engine.NodeJsHostCapabilityBroker
```

```
JNI_GLOBAL
  org.autojs.autojs.engine.NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback
  org.autojs.autojs.engine.NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener
  org.autojs.autojs.engine.NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0
  -> org.autojs.autojs.engine.NodePluginJavaScriptEngine
```

### ND7c F: NodeJsHostCapabilityBroker 1, AndroidNodeBridgeCapabilityProvider 1, NodePluginJavaScriptEngine 1, ScriptRuntime 1 in the dump, dump 2186 ms, analysis 11036 ms

- 1 object(s), root JNI_GLOBAL:  -> NodeJsHostCapabilityBroker
- 1 object(s), root JNI_GLOBAL: NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback -> NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener -> NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0 -> NodePluginJavaScriptEngine

```
JNI_GLOBAL
  -> org.autojs.autojs.engine.NodeJsHostCapabilityBroker
```

```
JNI_GLOBAL
  org.autojs.autojs.engine.NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback
  org.autojs.autojs.engine.NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener
  org.autojs.autojs.engine.NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0
  -> org.autojs.autojs.engine.NodePluginJavaScriptEngine
```

### ND8 N: NodeJsHostCapabilityBroker 2, AndroidNodeBridgeCapabilityProvider 2, NodePluginJavaScriptEngine 2, ScriptRuntime 2 in the dump, dump 1797 ms, analysis 11047 ms

- 2 object(s), root JNI_GLOBAL:  -> NodeJsHostCapabilityBroker
- 2 object(s), root JNI_GLOBAL: NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback -> NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener -> NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0 -> NodePluginJavaScriptEngine

```
JNI_GLOBAL
  -> org.autojs.autojs.engine.NodeJsHostCapabilityBroker
```

```
JNI_GLOBAL
  org.autojs.autojs.engine.NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback
  org.autojs.autojs.engine.NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener
  org.autojs.autojs.engine.NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0
  -> org.autojs.autojs.engine.NodePluginJavaScriptEngine
```

### ND8 F: NodeJsHostCapabilityBroker 2, AndroidNodeBridgeCapabilityProvider 2, NodePluginJavaScriptEngine 2, ScriptRuntime 2 in the dump, dump 2080 ms, analysis 11683 ms

- 2 object(s), root JNI_GLOBAL:  -> NodeJsHostCapabilityBroker
- 2 object(s), root JNI_GLOBAL: NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback -> NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener -> NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0 -> NodePluginJavaScriptEngine

```
JNI_GLOBAL
  -> org.autojs.autojs.engine.NodeJsHostCapabilityBroker
```

```
JNI_GLOBAL
  org.autojs.autojs.engine.NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback
  org.autojs.autojs.engine.NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener
  org.autojs.autojs.engine.NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0
  -> org.autojs.autojs.engine.NodePluginJavaScriptEngine
```

### ND9 N: NodeJsHostCapabilityBroker 1, AndroidNodeBridgeCapabilityProvider 1, NodePluginJavaScriptEngine 1, ScriptRuntime 1 in the dump, dump 1898 ms, analysis 10856 ms

- 1 object(s), root JNI_GLOBAL:  -> NodeJsHostCapabilityBroker
- 1 object(s), root JNI_GLOBAL: NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback -> NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener -> NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0 -> NodePluginJavaScriptEngine

```
JNI_GLOBAL
  -> org.autojs.autojs.engine.NodeJsHostCapabilityBroker
```

```
JNI_GLOBAL
  org.autojs.autojs.engine.NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback
  org.autojs.autojs.engine.NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener
  org.autojs.autojs.engine.NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0
  -> org.autojs.autojs.engine.NodePluginJavaScriptEngine
```

### ND9 F: NodeJsHostCapabilityBroker 1, AndroidNodeBridgeCapabilityProvider 1, NodePluginJavaScriptEngine 1, ScriptRuntime 1 in the dump, dump 1866 ms, analysis 10510 ms

- 1 object(s), root JNI_GLOBAL:  -> NodeJsHostCapabilityBroker
- 1 object(s), root JNI_GLOBAL: NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback -> NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener -> NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0 -> NodePluginJavaScriptEngine

```
JNI_GLOBAL
  -> org.autojs.autojs.engine.NodeJsHostCapabilityBroker
```

```
JNI_GLOBAL
  org.autojs.autojs.engine.NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback
  org.autojs.autojs.engine.NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener
  org.autojs.autojs.engine.NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0
  -> org.autojs.autojs.engine.NodePluginJavaScriptEngine
```

### ND10 N: NodeJsHostCapabilityBroker 1, AndroidNodeBridgeCapabilityProvider 1, NodePluginJavaScriptEngine 1, ScriptRuntime 1 in the dump, dump 1792 ms, analysis 10418 ms

- 1 object(s), root JNI_GLOBAL:  -> NodeJsHostCapabilityBroker
- 1 object(s), root JNI_GLOBAL: NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback -> NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener -> NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0 -> NodePluginJavaScriptEngine

```
JNI_GLOBAL
  -> org.autojs.autojs.engine.NodeJsHostCapabilityBroker
```

```
JNI_GLOBAL
  org.autojs.autojs.engine.NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback
  org.autojs.autojs.engine.NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener
  org.autojs.autojs.engine.NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0
  -> org.autojs.autojs.engine.NodePluginJavaScriptEngine
```

### ND10 F: NodeJsHostCapabilityBroker 1, AndroidNodeBridgeCapabilityProvider 1, NodePluginJavaScriptEngine 1, ScriptRuntime 1 in the dump, dump 2083 ms, analysis 13559 ms

- 1 object(s), root JNI_GLOBAL:  -> NodeJsHostCapabilityBroker
- 1 object(s), root JNI_GLOBAL: NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback -> NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener -> NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0 -> NodePluginJavaScriptEngine

```
JNI_GLOBAL
  -> org.autojs.autojs.engine.NodeJsHostCapabilityBroker
```

```
JNI_GLOBAL
  org.autojs.autojs.engine.NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback
  org.autojs.autojs.engine.NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener
  org.autojs.autojs.engine.NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0
  -> org.autojs.autojs.engine.NodePluginJavaScriptEngine
```

### ND11 N: NodeJsHostCapabilityBroker 2, AndroidNodeBridgeCapabilityProvider 2, NodePluginJavaScriptEngine 2, ScriptRuntime 2 in the dump, dump 1864 ms, analysis 10824 ms

- 2 object(s), root JNI_GLOBAL:  -> NodeJsHostCapabilityBroker
- 2 object(s), root JNI_GLOBAL: NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback -> NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener -> NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0 -> NodePluginJavaScriptEngine

```
JNI_GLOBAL
  -> org.autojs.autojs.engine.NodeJsHostCapabilityBroker
```

```
JNI_GLOBAL
  org.autojs.autojs.engine.NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback
  org.autojs.autojs.engine.NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener
  org.autojs.autojs.engine.NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0
  -> org.autojs.autojs.engine.NodePluginJavaScriptEngine
```

### ND11 F: NodeJsHostCapabilityBroker 1, AndroidNodeBridgeCapabilityProvider 1, NodePluginJavaScriptEngine 1, ScriptRuntime 1 in the dump, dump 1908 ms, analysis 10386 ms

- 1 object(s), root JNI_GLOBAL:  -> NodeJsHostCapabilityBroker
- 1 object(s), root JNI_GLOBAL: NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback -> NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener -> NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0 -> NodePluginJavaScriptEngine

```
JNI_GLOBAL
  -> org.autojs.autojs.engine.NodeJsHostCapabilityBroker
```

```
JNI_GLOBAL
  org.autojs.autojs.engine.NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback
  org.autojs.autojs.engine.NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener
  org.autojs.autojs.engine.NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0
  -> org.autojs.autojs.engine.NodePluginJavaScriptEngine
```

### ND12 N: NodeJsHostCapabilityBroker 2, AndroidNodeBridgeCapabilityProvider 2, NodePluginJavaScriptEngine 2, ScriptRuntime 2 in the dump, dump 1838 ms, analysis 11425 ms

- 2 object(s), root JNI_GLOBAL:  -> NodeJsHostCapabilityBroker
- 2 object(s), root JNI_GLOBAL: NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback -> NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener -> NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0 -> NodePluginJavaScriptEngine

```
JNI_GLOBAL
  -> org.autojs.autojs.engine.NodeJsHostCapabilityBroker
```

```
JNI_GLOBAL
  org.autojs.autojs.engine.NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback
  org.autojs.autojs.engine.NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener
  org.autojs.autojs.engine.NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0
  -> org.autojs.autojs.engine.NodePluginJavaScriptEngine
```

### ND12 F: NodeJsHostCapabilityBroker 1, AndroidNodeBridgeCapabilityProvider 1, NodePluginJavaScriptEngine 1, ScriptRuntime 1 in the dump, dump 1832 ms, analysis 11089 ms

- 1 object(s), root JNI_GLOBAL:  -> NodeJsHostCapabilityBroker
- 1 object(s), root JNI_GLOBAL: NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback -> NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener -> NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0 -> NodePluginJavaScriptEngine

```
JNI_GLOBAL
  -> org.autojs.autojs.engine.NodeJsHostCapabilityBroker
```

```
JNI_GLOBAL
  org.autojs.autojs.engine.NodeJsRuntimePluginHost$invokeRunScript$future$terminalCallback$1.$callback
  org.autojs.autojs.engine.NodeJsRuntimePluginBridge$runScript$1$runtimeCallback$1.$outputListener
  org.autojs.autojs.engine.NodePluginJavaScriptEngine$$ExternalSyntheticLambda1.f$0
  -> org.autojs.autojs.engine.NodePluginJavaScriptEngine
```


Raw reports: the .json file with the same name (one report per line under `results`).
