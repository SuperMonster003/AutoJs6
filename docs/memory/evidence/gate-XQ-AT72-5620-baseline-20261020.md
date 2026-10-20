# Memory gate XQ-AT72 (5620) 20261020

- Device: Sony XQ-AT72, API 31, serial QV710AF65F
- Host: org.autojs.autojs6 versionCode 5620 (6.8.0), debuggable
- Run: XQ-AT72-5620-20261020191146, label baseline, 2026-10-20T08:21:50.578470+00:00 -> 2026-10-20T11:15:37.098153+00:00
- Device state at start: {'batteryLevel': 71, 'powered': ['USB'], 'wakefulness': 'Awake'}
- Merged runs: XQ-AT72-5620-20261020162150 (all); XQ-AT72-5620-20261020172032 (S4e); XQ-AT72-5620-20261020172131 (S4e); XQ-AT72-5620-20261020180657 (S4a); XQ-AT72-5620-20261020190236 (S7); XQ-AT72-5620-20261020190550 (S7); XQ-AT72-5620-20261020191146 (S7)
- Warm-ups 2, iterations 20, poll 10000 ms, force delay 1000 ms, capture off
- Cell: retained / Java heap MB / native heap MB / script threads / Rhino contexts / live engines (ROADMAP appendix A)
- Thresholds (T1): retained 0, Java <= 2 MB, native <= 4 MB (+1 frame for capture), threads and contexts back to start, engines back to start

| Scenario | Mode | Status | Cell | Reasons |
| --- | --- | --- | --- | --- |
| S1 empty script | N | fail | 1 / +0.04 / +0.03 / +0 / +0 / 0 | retained runtimes 1/20 |
| S1 empty script | F | fail | 1 / +0.04 / +0.02 / +0 / +0 / 0 | retained runtimes 1/20 |
| S2 threads.start x 10 + threads.pool | N | fail | 20 / +30.18 / +0.44 / +0 / +0 / 0 | retained runtimes 20/20; java heap +30.18 MB |
| S2 threads.start x 10 + threads.pool | F | fail | 20 / +30.21 / +0.88 / +44 / +44 / 0 | retained runtimes 20/20; java heap +30.21 MB; script threads +44; rhino contexts +44 |
| S3 nested timers on main and child threads | N | fail | 20 / +29.23 / +0.36 / +0 / +0 / 0 | retained runtimes 20/20; java heap +29.23 MB |
| S3 nested timers on main and child threads | F | fail | 20 / +29.32 / +0.39 / +0 / +0 / 0 | retained runtimes 20/20; java heap +29.32 MB |
| S4a captureScreen x 20 | N | fail | 1 / -0.05 / -0.11 / +0 / +0 / 0 | retained runtimes 1/20 |
| S4a captureScreen x 20 | F | fail | 1 / +0.07 / +0.00 / +0 / +0 / 0 | retained runtimes 1/20 |
| S4b images.read + findImage | N | fail | 1 / +0.04 / -0.67 / +0 / +0 / 0 | retained runtimes 1/20 |
| S4b images.read + findImage | F | fail | 1 / +0.04 / -2.47 / +0 / +0 / 0 | retained runtimes 1/20 |
| S4c images.copy / clip / resize chain | N | fail | 1 / +0.04 / -0.81 / +0 / +0 / 0 | retained runtimes 1/20 |
| S4c images.copy / clip / resize chain | F | fail | 1 / +0.04 / -0.31 / +0 / +0 / 0 | retained runtimes 1/20 |
| S4d matchTemplate + oneShot | N | fail | 1 / +0.04 / -1.29 / +0 / +0 / 0 | retained runtimes 1/20 |
| S4d matchTemplate + oneShot | F | fail | 1 / +0.03 / -5.93 / +0 / +0 / 0 | retained runtimes 1/20 |
| S4e threaded copy + findImage + recycle (issue #372 / #200 pattern) | N | fail | 20 / +29.59 / -5.80 / +0 / +0 / 0 | retained runtimes 20/20; java heap +29.59 MB |
| S4e threaded copy + findImage + recycle (issue #372 / #200 pattern) | F | fail | 20 / +29.82 / -3.48 / +0 / +0 / 0 | retained runtimes 20/20; java heap +29.82 MB |
| S5 ui script with button, text and webview | N | fail | 1 / +0.06 / -0.20 / +0 / +0 / 0 | retained runtimes 1/20 |
| S5 ui script with button, text and webview | F | fail | 1 / +0.05 / -0.20 / +0 / +0 / 0 | retained runtimes 1/20 |
| S6 floaty.window + floaty.rawWindow | N | fail | 1 / +0.06 / +0.09 / +0 / +0 / 0 | retained runtimes 1/20 |
| S6 floaty.window + floaty.rawWindow | F | fail | 1 / +0.06 / +0.09 / +0 / +0 / 0 | retained runtimes 1/20 |
| S7 events / key observer / sensors / accessibility events | N | fail | 1 / +0.02 / -0.06 / +0 / +0 / 0 | retained runtimes 1/20 |
| S7 events / key observer / sensors / accessibility events | F | fail | 1 / +0.04 / +0.15 / +0 / +0 / 0 | retained runtimes 1/20 |
| S8 JavaAdapter / interface adapters x 100 + runtime.loadDex | N | fail | 1 / +0.04 / -0.37 / +0 / +0 / 0 | retained runtimes 1/20 |
| S8 JavaAdapter / interface adapters x 100 + runtime.loadDex | F | fail | 1 / +0.04 / -0.45 / +0 / +0 / 0 | retained runtimes 1/20 |
| S9 engines.execScriptFile restarting itself 3 levels | N | fail | 1 / +0.08 / +0.01 / +0 / +0 / 0 | retained runtimes 1/20 |
| S9 engines.execScriptFile restarting itself 3 levels | F | fail | 1 / +0.08 / +0.03 / +0 / +0 / 0 | retained runtimes 1/20 |
| S10 http.get + WebSocket on a loopback server | N | fail | 1 / +0.08 / +0.07 / +0 / +0 / 0 | retained runtimes 1/20 |
| S10 http.get + WebSocket on a loopback server | F | fail | 19 / +26.90 / +0.69 / +0 / +0 / 0 | retained runtimes 19/20; java heap +26.90 MB |
| S11 device.* reads, vibrate and keepScreenOn | N | fail | 1 / +0.10 / +0.03 / +0 / +0 / 0 | retained runtimes 1/20 |
| S11 device.* reads, vibrate and keepScreenOn | F | fail | 1 / +0.09 / +0.04 / +0 / +0 / 0 | retained runtimes 1/20 |
| S12 plugins.load of an installed official plugin | N | fail | 20 / +53.47 / +0.34 / +0 / +0 / 0 | retained runtimes 20/20; java heap +53.47 MB |
| S12 plugins.load of an installed official plugin | F | fail | 20 / +53.56 / +0.35 / +0 / +0 / 0 | retained runtimes 20/20; java heap +53.56 MB |

## Shortest strong reference paths (shark, exited ScriptRuntime instances)

### S1 N: 2 exited runtimes in the dump, dump 2020 ms, analysis 8660 ms

- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S1 F: 2 exited runtimes in the dump, dump 2001 ms, analysis 8841 ms

- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S2 N: 22 exited runtimes in the dump, dump 2385 ms, analysis 24984 ms

- 21 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> LooperHelper.sLoopers -> ConcurrentHashMap[] -> Threads$Companion$TimerThread.target -> RhinoUtils$$ExternalSyntheticLambda18.f$0 -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- ignoring java.lang.Thread.target: 14 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> LooperHelper.sLoopers -> ConcurrentHashMap[] -> Looper.mQueue -> MessageQueue.mIdleHandlers -> ArrayList[] -> Loopers.scriptRuntime -> ScriptRuntime
- ignoring java.lang.Thread.target: 7 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> LooperHelper.sLoopers -> ConcurrentHashMap[] -> Looper.mQueue -> MessageQueue.mIdleHandlers -> ArrayList[] -> Loopers.scriptRuntime -> ScriptRuntime
- ignoring java.lang.Thread.target: 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.looper.LooperHelper.sLoopers (static)
  java.util.concurrent.ConcurrentHashMap[]
  org.autojs.autojs.runtime.api.Threads$Companion$TimerThread.target
  org.autojs.autojs.util.RhinoUtils$$ExternalSyntheticLambda18.f$0
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT (second pass ignoring java.lang.Thread.target)
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.looper.LooperHelper.sLoopers (static)
  java.util.concurrent.ConcurrentHashMap[]
  android.os.Looper.mQueue
  android.os.MessageQueue.mIdleHandlers
  java.util.ArrayList[]
  org.autojs.autojs.core.looper.Loopers.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT (second pass ignoring java.lang.Thread.target)
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.looper.LooperHelper.sLoopers (static)
  java.util.concurrent.ConcurrentHashMap[]
  android.os.Looper.mQueue
  android.os.MessageQueue.mIdleHandlers
  java.util.ArrayList[]
  org.autojs.autojs.core.looper.Loopers.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT (second pass ignoring java.lang.Thread.target)
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S2 F: 22 exited runtimes in the dump, dump 2447 ms, analysis 14305 ms

- 22 runtime(s), root THREAD_OBJECT: Threads$Companion$TimerThread.mLooper -> Looper.mQueue -> MessageQueue.mIdleHandlers -> ArrayList[] -> Loopers.scriptRuntime -> ScriptRuntime

```
THREAD_OBJECT
  org.autojs.autojs.runtime.api.Threads$Companion$TimerThread.mLooper
  android.os.Looper.mQueue
  android.os.MessageQueue.mIdleHandlers
  java.util.ArrayList[]
  org.autojs.autojs.core.looper.Loopers.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S3 N: 22 exited runtimes in the dump, dump 2419 ms, analysis 23888 ms

- 21 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> LooperHelper.sLoopers -> ConcurrentHashMap[] -> Threads$Companion$TimerThread.target -> RhinoUtils$$ExternalSyntheticLambda18.f$0 -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- ignoring java.lang.Thread.target: 21 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> LooperHelper.sLoopers -> ConcurrentHashMap[] -> Looper.mQueue -> MessageQueue.mIdleHandlers -> ArrayList[] -> Loopers.scriptRuntime -> ScriptRuntime
- ignoring java.lang.Thread.target: 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.looper.LooperHelper.sLoopers (static)
  java.util.concurrent.ConcurrentHashMap[]
  org.autojs.autojs.runtime.api.Threads$Companion$TimerThread.target
  org.autojs.autojs.util.RhinoUtils$$ExternalSyntheticLambda18.f$0
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT (second pass ignoring java.lang.Thread.target)
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.looper.LooperHelper.sLoopers (static)
  java.util.concurrent.ConcurrentHashMap[]
  android.os.Looper.mQueue
  android.os.MessageQueue.mIdleHandlers
  java.util.ArrayList[]
  org.autojs.autojs.core.looper.Loopers.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT (second pass ignoring java.lang.Thread.target)
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S3 F: 22 exited runtimes in the dump, dump 2399 ms, analysis 24343 ms

- 21 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> LooperHelper.sLoopers -> ConcurrentHashMap[] -> Threads$Companion$TimerThread.target -> RhinoUtils$$ExternalSyntheticLambda18.f$0 -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- ignoring java.lang.Thread.target: 21 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> LooperHelper.sLoopers -> ConcurrentHashMap[] -> Looper.mQueue -> MessageQueue.mIdleHandlers -> ArrayList[] -> Loopers.scriptRuntime -> ScriptRuntime
- ignoring java.lang.Thread.target: 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.looper.LooperHelper.sLoopers (static)
  java.util.concurrent.ConcurrentHashMap[]
  org.autojs.autojs.runtime.api.Threads$Companion$TimerThread.target
  org.autojs.autojs.util.RhinoUtils$$ExternalSyntheticLambda18.f$0
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT (second pass ignoring java.lang.Thread.target)
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.looper.LooperHelper.sLoopers (static)
  java.util.concurrent.ConcurrentHashMap[]
  android.os.Looper.mQueue
  android.os.MessageQueue.mIdleHandlers
  java.util.ArrayList[]
  org.autojs.autojs.core.looper.Loopers.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT (second pass ignoring java.lang.Thread.target)
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S4a N: 2 exited runtimes in the dump, dump 1994 ms, analysis 9921 ms

- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S4a F: 2 exited runtimes in the dump, dump 1999 ms, analysis 9560 ms

- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S4b N: 2 exited runtimes in the dump, dump 2005 ms, analysis 8579 ms

- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S4b F: 2 exited runtimes in the dump, dump 2002 ms, analysis 9821 ms

- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S4c N: 2 exited runtimes in the dump, dump 2012 ms, analysis 9185 ms

- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S4c F: 2 exited runtimes in the dump, dump 2030 ms, analysis 9232 ms

- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S4d N: 2 exited runtimes in the dump, dump 2006 ms, analysis 8798 ms

- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S4d F: 2 exited runtimes in the dump, dump 1981 ms, analysis 10552 ms

- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S4e N: 22 exited runtimes in the dump, dump 2462 ms, analysis 24987 ms

- 21 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> LooperHelper.sLoopers -> ConcurrentHashMap[] -> Threads$Companion$TimerThread.target -> RhinoUtils$$ExternalSyntheticLambda18.f$0 -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- ignoring java.lang.Thread.target: 21 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> LooperHelper.sLoopers -> ConcurrentHashMap[] -> Looper.mQueue -> MessageQueue.mIdleHandlers -> ArrayList[] -> Loopers.scriptRuntime -> ScriptRuntime
- ignoring java.lang.Thread.target: 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.looper.LooperHelper.sLoopers (static)
  java.util.concurrent.ConcurrentHashMap[]
  org.autojs.autojs.runtime.api.Threads$Companion$TimerThread.target
  org.autojs.autojs.util.RhinoUtils$$ExternalSyntheticLambda18.f$0
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT (second pass ignoring java.lang.Thread.target)
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.looper.LooperHelper.sLoopers (static)
  java.util.concurrent.ConcurrentHashMap[]
  android.os.Looper.mQueue
  android.os.MessageQueue.mIdleHandlers
  java.util.ArrayList[]
  org.autojs.autojs.core.looper.Loopers.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT (second pass ignoring java.lang.Thread.target)
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S4e F: 22 exited runtimes in the dump, dump 2400 ms, analysis 25214 ms

- 22 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> LooperHelper.sLoopers -> ConcurrentHashMap[] -> Threads$Companion$TimerThread.target -> RhinoUtils$$ExternalSyntheticLambda18.f$0 -> ScriptRuntime
- ignoring java.lang.Thread.target: 21 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> LooperHelper.sLoopers -> ConcurrentHashMap[] -> Looper.mQueue -> MessageQueue.mIdleHandlers -> ArrayList[] -> Loopers.scriptRuntime -> ScriptRuntime
- ignoring java.lang.Thread.target: 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.looper.LooperHelper.sLoopers (static)
  java.util.concurrent.ConcurrentHashMap[]
  org.autojs.autojs.runtime.api.Threads$Companion$TimerThread.target
  org.autojs.autojs.util.RhinoUtils$$ExternalSyntheticLambda18.f$0
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT (second pass ignoring java.lang.Thread.target)
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.looper.LooperHelper.sLoopers (static)
  java.util.concurrent.ConcurrentHashMap[]
  android.os.Looper.mQueue
  android.os.MessageQueue.mIdleHandlers
  java.util.ArrayList[]
  org.autojs.autojs.core.looper.Loopers.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT (second pass ignoring java.lang.Thread.target)
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S5 N: 2 exited runtimes in the dump, dump 1997 ms, analysis 14722 ms

- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S5 F: 2 exited runtimes in the dump, dump 2127 ms, analysis 11729 ms

- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S6 N: 2 exited runtimes in the dump, dump 2050 ms, analysis 10205 ms

- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S6 F: 2 exited runtimes in the dump, dump 2062 ms, analysis 10296 ms

- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S7 N: 2 exited runtimes in the dump, dump 2153 ms, analysis 11503 ms

- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S7 F: 2 exited runtimes in the dump, dump 2352 ms, analysis 10524 ms

- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S8 N: 2 exited runtimes in the dump, dump 2142 ms, analysis 9633 ms

- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S8 F: 2 exited runtimes in the dump, dump 2134 ms, analysis 11829 ms

- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S9 N: 2 exited runtimes in the dump, dump 1999 ms, analysis 9760 ms

- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S9 F: 2 exited runtimes in the dump, dump 1891 ms, analysis 9681 ms

- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S10 N: 2 exited runtimes in the dump, dump 1812 ms, analysis 10226 ms

- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S10 F: 20 exited runtimes in the dump, dump 2365 ms, analysis 21665 ms

- 18 runtime(s), root THREAD_OBJECT: Thread<local> -> RealWebSocket.listener -> WebSocket$WebSocketMessageListener.this$0 -> WebSocket.mBridges -> ScriptBridges.engine -> LoopBasedJavaScriptEngine.mRuntime -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
THREAD_OBJECT
  java.lang.Thread <local>
  okhttp3.internal.ws.RealWebSocket.listener
  org.autojs.autojs.core.web.WebSocket$WebSocketMessageListener.this$0
  org.autojs.autojs.core.web.WebSocket.mBridges
  org.autojs.autojs.runtime.ScriptBridges.engine
  org.autojs.autojs.engine.LoopBasedJavaScriptEngine.mRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S11 N: 2 exited runtimes in the dump, dump 1983 ms, analysis 9048 ms

- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S11 F: 2 exited runtimes in the dump, dump 1986 ms, analysis 8528 ms

- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S12 N: 22 exited runtimes in the dump, dump 2637 ms, analysis 25558 ms

- 20 runtime(s), root JNI_GLOBAL: LoadedApk$ServiceDispatcher$DeathMonitor.this$0 -> LoadedApk$ServiceDispatcher.mConnection -> Plugin.pluginInstance -> ImGuiPluginConnection.runtime -> Plugins$PluginRuntime.topLevelScope -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
JNI_GLOBAL
  android.app.LoadedApk$ServiceDispatcher$DeathMonitor.this$0
  android.app.LoadedApk$ServiceDispatcher.mConnection
  org.autojs.autojs.core.plugin.Plugin.pluginInstance
  io.github.supermonster003.autojs6.plugin.imgui.ImGuiPluginConnection.runtime
  org.autojs.autojs.runtime.api.Plugins$PluginRuntime.topLevelScope
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S12 F: 22 exited runtimes in the dump, dump 2769 ms, analysis 25472 ms

- 20 runtime(s), root JNI_GLOBAL: LoadedApk$ServiceDispatcher$DeathMonitor.this$0 -> LoadedApk$ServiceDispatcher.mConnection -> Plugin.pluginInstance -> ImGuiPluginConnection.runtime -> Plugins$PluginRuntime.topLevelScope -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: Instrumentation$InstrumentationThread.contextClassLoader -> PathClassLoader.runtimeInternalObjects -> Object[][] -> VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
JNI_GLOBAL
  android.app.LoadedApk$ServiceDispatcher$DeathMonitor.this$0
  android.app.LoadedApk$ServiceDispatcher.mConnection
  org.autojs.autojs.core.plugin.Plugin.pluginInstance
  io.github.supermonster003.autojs6.plugin.imgui.ImGuiPluginConnection.runtime
  org.autojs.autojs.runtime.api.Plugins$PluginRuntime.topLevelScope
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  android.app.Instrumentation$InstrumentationThread.contextClassLoader
  dalvik.system.PathClassLoader.runtimeInternalObjects
  java.lang.Object[][]
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```


Raw reports: the .json file with the same name (one report per line under `results`).
