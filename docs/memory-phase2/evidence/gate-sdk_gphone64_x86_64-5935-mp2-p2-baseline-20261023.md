# Bridge gate sdk_gphone64_x86_64 (5935) 20261023

- Device: Google sdk_gphone64_x86_64, API 35, serial emulator-5612
- Host: org.autojs.autojs6 versionCode 5935 (6.8.0), debuggable
- Plugin: io.github.supermonster003.autojs6.plugin.bun.runtime versionCode 296 (0.21.0), commit fb879f3
- Plugin: io.github.supermonster003.autojs6.plugin.quickjs.runtime versionCode 42 (1.0.4), commit 0bcee52
- hidden_api_policy (ROADMAP D9): 1 during each run, before `None`, afterwards `None`
- Run: sdk_gphone64_x86_64-5935-20261023065403, label mp2-p2-baseline, 2026-10-22T22:54:03.963512+00:00 -> 2026-10-22T23:27:42.541046+00:00
- Device state at start: {'batteryLevel': 100, 'powered': ['AC'], 'wakefulness': 'Awake'}
- Warm-ups 2, iterations 20, poll 10000 ms, force delay 1000 ms, capture off, meminfo phases
- Cell (docs/memory-phase2/ROADMAP.md appendix A): retained / Java heap MB / native heap MB / script threads, b bridge threads / bindings: h host bridge registries vs start, s system connections to the plugin vs start / live engines / plugin PSS MB at the end (change since measured run 2)
- Thresholds (T1): retained 0, Java <= 2 MB, native <= 4 MB (+1 frame for capture), script threads, engines and bridge registries back to start (the connection pools of the Node, Bun and QuickJS clients may keep 1), Bun / QuickJS session objects back to start, system connections <= start + 1, bridge threads back to the end of the warm-ups (idle workers of the cached binder-call pool get up to 65 s to expire first, Q6); plugin PSS recorded only (R9)

| Scenario | Mode | Status | Cell | Reasons |
| --- | --- | --- | --- | --- |
| BN1 Empty script | N | fail | 8 / +0.13 / +0.01 / +0 b+0 / h+0 s+0 / 0 / 22.8 (-1.52) | retained session instances vs start: bun.broker 8, bun.callback 3, bun.automation 8, bun.callExecutor 8 |
| BN1 Empty script | F | fail | 9 / +0.13 / +0.03 / +0 b+0 / h+0 s+0 / 0 / 23.0 (-0.32) | retained session instances vs start: bun.broker 9, bun.callback 4, bun.automation 9, bun.callExecutor 9 |
| BN2 Host capabilities through the host client | N | fail | 7 / +0.15 / +0.02 / +0 b+0 / h+0 s+0 / 0 / 23.7 (+1.10) | retained session instances vs start: bun.broker 7, bun.callback 3, bun.automation 7, bun.callExecutor 7 |
| BN2 Host capabilities through the host client | F | fail | 7 / +0.18 / +0.02 / +0 b+0 / h+0 s+0 / 0 / 23.6 (+0.80) | retained session instances vs start: bun.broker 7, bun.callback 3, bun.automation 7, bun.callExecutor 7 |
| BN3 Sensor callbacks | N | fail | 4 / +0.14 / +0.04 / +0 b+0 / h+0 s+0 / 0 / 23.6 (+0.74) | retained session instances vs start: bun.broker 4, bun.callback 1, bun.automation 4, bun.callExecutor 4 |
| BN3 Sensor callbacks | F | fail | 5 / +0.13 / +0.03 / +0 b+0 / h+0 s+0 / 0 / 24.5 (+0.01) | retained session instances vs start: bun.broker 5, bun.callback 2, bun.automation 5, bun.callExecutor 5 |
| BN4 Plugin process killed | N | pass | 0 / +0.22 / -0.02 / +0 b+0 / h-1 s-1 / 0 / - |  |
| BN4 Plugin process killed | F | pass | 0 / +0.21 / -0.04 / +0 b+0 / h-1 s-1 / 0 / - |  |
| BN5 ColorOS bind refusal loop | N | skipped | skipped | BN5 runs on ColorOS only (OPD2413); this device is Google sdk_gphone64_x86_64 |
| BN5 ColorOS bind refusal loop | F | skipped | skipped | BN5 runs on ColorOS only (OPD2413); this device is Google sdk_gphone64_x86_64 |
| QJ1 Empty script | N | fail | 8 / +0.09 / +0.01 / +0 b+0 / h+0 s+0 / 0 / 25.9 (+0.50) | retained runs 8/20 (engine 8); retained session instances vs start: quickjs.callback 8 |
| QJ1 Empty script | F | fail | 8 / +0.12 / +0.01 / +0 b+0 / h+0 s+0 / 0 / 25.0 (+0.51) | retained runs 8/20 (engine 8); retained session instances vs start: quickjs.callback 8 |
| QJ2 File source and output | N | fail | 3 / +0.28 / +0.01 / +0 b+0 / h+0 s+0 / 0 / 25.3 (+0.97) | retained runs 3/20 (engine 3); retained session instances vs start: quickjs.callback 3 |
| QJ2 File source and output | F | fail | 3 / +0.28 / +0.01 / +0 b+0 / h+0 s+0 / 0 / 25.3 (+0.75) | retained runs 3/20 (engine 3); retained session instances vs start: quickjs.callback 3 |
| QJ3 Output callbacks from Promise jobs | N | fail | 8 / +0.13 / -0.00 / +0 b+0 / h+0 s+0 / 0 / 25.5 (+0.45) | retained runs 8/20 (engine 8); retained session instances vs start: quickjs.callback 8 |
| QJ3 Output callbacks from Promise jobs | F | fail | 9 / +0.16 / -0.00 / +0 b+0 / h+0 s+0 / 0 / 25.7 (+0.61) | retained runs 9/20 (engine 9); retained session instances vs start: quickjs.callback 9 |
| QJ4 Plugin process killed | N | pass | 0 / +0.21 / +0.02 / +0 b+0 / h-1 s-1 / 0 / - |  |
| QJ4 Plugin process killed | F | pass | 0 / +0.19 / +0.01 / +0 b+0 / h-1 s-1 / 0 / - |  |
| QJ5 ColorOS bind refusal loop | N | skipped | skipped | QJ5 runs on ColorOS only (OPD2413); this device is Google sdk_gphone64_x86_64 |
| QJ5 ColorOS bind refusal loop | F | skipped | skipped | QJ5 runs on ColorOS only (OPD2413); this device is Google sdk_gphone64_x86_64 |

## Bridge detail (docs/memory-phase2/ROADMAP.md T1 (d) / (h) / (i))

- Registries: host bridge tables whose size differs from the start (StaticRegistries.bridgeCounts, and the AidlPluginHost connection pools of the Bun and QuickJS clients)
- Bindings: connections of the host to the plugin / all bound service connections in LoadedApk.mServices, before the first run -> at the end (n/a: unreadable)
- New threads: bridge threads alive at the end that were not alive at the end of the warm-ups
- Plugin PSS MB: all processes of the plugin before the first run / after measured run 2 / at the end (recorded only, R9)
- Script: largest value of each gateStat over all runs; ended by error: runs of an expected failure (ND4 / BN4 / QJ4); session: Bun / QuickJS session objects above the start at the end (VMDebug counts), and the longest wait after a run until they were back

| Scenario | Mode | Registries | Bindings | New threads | Plugin PSS MB | Script |
| --- | --- | --- | --- | --- | --- | --- |
| BN1 | N | +0 | 1/1 -> 1/1 | - | 22.1 / 24.3 / 22.8 | session broker +8, callback +3, automation +8, callExecutor +8 |
| BN1 | F | +0 | 1/1 -> 1/1 | - | 25.2 / 23.3 / 23.0 | session broker +9, callback +4, automation +9, callExecutor +9 |
| BN2 | N | +0 | 1/1 -> 1/1 | - | 25.2 / 22.6 / 23.7 | deviceInfoKeys 8, pixels 16, pngBytes 275; session broker +7, callback +3, automation +7, callExecutor +7 |
| BN2 | F | +0 | 1/1 -> 1/1 | - | 25.7 / 22.8 / 23.6 | deviceInfoKeys 8, pixels 16, pngBytes 275; session broker +7, callback +3, automation +7, callExecutor +7 |
| BN3 | N | +0 | 1/1 -> 1/1 | - | 25.7 / 22.9 / 23.6 | sensorEvents 5; session broker +4, callback +1, automation +4, callExecutor +4 |
| BN3 | F | +0 | 1/1 -> 1/1 | - | 26.6 / 24.5 / 24.5 | sensorEvents 16; session broker +5, callback +2, automation +5, callExecutor +5 |
| BN4 | N | connectionPool -1 | 1/1 -> 0/0 | - | 26.7 / - / - | ended by error 22; session +0, cleared <= 11 ms |
| BN4 | F | connectionPool -1 | 1/1 -> 0/0 | - | 21.5 / - / - | ended by error 22; session +0, cleared <= 12 ms |
| QJ1 | N | +0 | 1/1 -> 1/1 | - | 21.1 / 25.4 / 25.9 | session callback +8 |
| QJ1 | F | +0 | 1/1 -> 1/1 | - | 26.5 / 24.5 / 25.0 | session callback +8 |
| QJ2 | N | +0 | 1/1 -> 1/1 | - | 26.7 / 24.3 / 25.3 | sourceDataChars 65536, checksum 581538; session callback +3 |
| QJ2 | F | +0 | 1/1 -> 1/1 | - | 27.2 / 24.5 / 25.3 | sourceDataChars 65536, checksum 581538; session callback +3 |
| QJ3 | N | +0 | 1/1 -> 1/1 | - | 27.1 / 25.0 / 25.5 | ticks 3; session callback +8 |
| QJ3 | F | +0 | 1/1 -> 1/1 | - | 27.2 / 25.1 / 25.7 | ticks 3; session callback +9 |
| QJ4 | N | connectionPool -1 | 1/1 -> 0/0 | - | 27.2 / - / - | ended by error 22; session +0, cleared <= 5 ms |
| QJ4 | F | connectionPool -1 | 1/1 -> 0/0 | - | 21.0 / - / - | ended by error 22; session +0, cleared <= 5 ms |

## Native detail (ROADMAP P3.1)

- Peak: largest rise of the native heap inside a measured run (sampled every 20 ms, before the after-run GC)
- Meminfo: dumpsys meminfo deltas in MB from the end of the warm-ups to the end: Native Heap Pss / Native Heap Alloc / Graphics / Unknown Pss
- Counters: images not recycled (vs start) / open media images at the end / live Mats (vs end of warm-ups)
- Instances: VMDebug instance count deltas from the start: Bitmap / org.opencv.core.Mat / AccessibilityNodeInfo, then AccessibilityNodeInfo against the end of the warm-ups (S7 / S7b fail above +50, ROADMAP P3.4)
- Classes (ROADMAP P4.4), from the end of the warm-ups to the end: loaded classes (Debug.getLoadedClassCount; S8 fails when they grow in most measured runs, one-time steps do not count) / VMDebug java.lang.Class objects / VMDebug DexClassLoader / loaders registered by runtime.loadDex in the host / VMDebug WebView; `-` where unavailable (VMDebug only on API 24 / 28)

| Scenario | Mode | Peak MB | Meminfo MB | Counters | Instances | Classes | Script stats (max) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| BN1 | N | +0.05 | -0.40/+0.01/+0.00/+0.00 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| BN1 | F | +0.05 | +0.04/+0.03/+0.00/+0.00 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| BN2 | N | +0.12 | -0.00/+0.01/+0.00/+0.01 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| BN2 | F | +0.12 | +0.08/+0.03/+0.00/+0.00 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| BN3 | N | +0.13 | +0.10/+0.04/+0.00/+0.01 | +0 / 0 / +0 | - | +5 / +5 / +0 / +0 / +0 | - |
| BN3 | F | +0.15 | -0.11/+0.03/+0.00/+0.00 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| BN4 | N | +0.13 | -0.51/-0.02/+0.00/+0.01 | +0 / 0 / +0 | - | +72 / +72 / +0 / +0 / +0 | - |
| BN4 | F | +0.13 | +0.41/-0.04/+0.00/+0.00 | +0 / 0 / +0 | - | +55 / +55 / +0 / +0 / +0 | - |
| QJ1 | N | +0.05 | +0.02/+0.01/+0.00/+0.01 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| QJ1 | F | +0.05 | -0.13/+0.01/+0.00/+0.00 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| QJ2 | N | +0.05 | -0.14/+0.02/+0.00/+0.02 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| QJ2 | F | +0.06 | +0.05/+0.01/+0.00/+0.01 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| QJ3 | N | +0.05 | +0.06/-0.00/+0.00/+0.00 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| QJ3 | F | +0.05 | -0.20/+0.01/+0.00/+0.01 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| QJ4 | N | +0.12 | +0.39/+0.02/+0.00/+0.01 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| QJ4 | F | +0.13 | +0.22/+0.01/+0.00/+0.01 | +0 / 0 / +0 | - | +6 / +6 / +0 / +0 / +0 | - |

## Shortest strong reference paths (shark, bridge session objects and exited ScriptRuntime instances)

### BN1 N: BunHostCapabilityBroker 3, BunPluginScriptEngine$OutputCallback 0, BunAutomationBridge 3, BunHostCallExecutor 3, AndroidNodeBridgeCapabilityProvider 0, BunPluginScriptEngine 0 in the dump, dump 2044 ms, analysis 7426 ms

- 3 object(s), root JNI_GLOBAL:  -> BunHostCapabilityBroker

```
JNI_GLOBAL
  -> org.autojs.autojs.engine.BunHostCapabilityBroker
```

### BN1 F: BunHostCapabilityBroker 4, BunPluginScriptEngine$OutputCallback 0, BunAutomationBridge 4, BunHostCallExecutor 4, AndroidNodeBridgeCapabilityProvider 0, BunPluginScriptEngine 0 in the dump, dump 1431 ms, analysis 6939 ms

- 4 object(s), root JNI_GLOBAL:  -> BunHostCapabilityBroker

```
JNI_GLOBAL
  -> org.autojs.autojs.engine.BunHostCapabilityBroker
```

### BN2 N: BunHostCapabilityBroker 3, BunPluginScriptEngine$OutputCallback 0, BunAutomationBridge 3, BunHostCallExecutor 3, AndroidNodeBridgeCapabilityProvider 0, BunPluginScriptEngine 0 in the dump, dump 1512 ms, analysis 7168 ms

- 3 object(s), root JNI_GLOBAL:  -> BunHostCapabilityBroker

```
JNI_GLOBAL
  -> org.autojs.autojs.engine.BunHostCapabilityBroker
```

### BN2 F: BunHostCapabilityBroker 3, BunPluginScriptEngine$OutputCallback 0, BunAutomationBridge 3, BunHostCallExecutor 3, AndroidNodeBridgeCapabilityProvider 0, BunPluginScriptEngine 0 in the dump, dump 2022 ms, analysis 10558 ms

- 3 object(s), root JNI_GLOBAL:  -> BunHostCapabilityBroker

```
JNI_GLOBAL
  -> org.autojs.autojs.engine.BunHostCapabilityBroker
```

### BN3 N: BunHostCapabilityBroker 1, BunPluginScriptEngine$OutputCallback 0, BunAutomationBridge 1, BunHostCallExecutor 1, AndroidNodeBridgeCapabilityProvider 0, BunPluginScriptEngine 0 in the dump, dump 2197 ms, analysis 9010 ms

- 1 object(s), root JNI_GLOBAL:  -> BunHostCapabilityBroker

```
JNI_GLOBAL
  -> org.autojs.autojs.engine.BunHostCapabilityBroker
```

### BN3 F: BunHostCapabilityBroker 2, BunPluginScriptEngine$OutputCallback 0, BunAutomationBridge 2, BunHostCallExecutor 2, AndroidNodeBridgeCapabilityProvider 0, BunPluginScriptEngine 0 in the dump, dump 1979 ms, analysis 10174 ms

- 2 object(s), root JNI_GLOBAL:  -> BunHostCapabilityBroker

```
JNI_GLOBAL
  -> org.autojs.autojs.engine.BunHostCapabilityBroker
```


Raw reports: the .json file with the same name (one report per line under `results`).
