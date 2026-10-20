# Memory gate Android SDK built for x86 (5620) 20261020

- Device: Google Android SDK built for x86, API 24, serial emulator-5580
- Host: org.autojs.autojs6 versionCode 5620 (6.8.0), debuggable
- Run: Android_SDK_built_for_x86-5620-20261020181439, label baseline, 2026-10-20T08:37:51.417676+00:00 -> 2026-10-20T10:24:17.057584+00:00
- Device state at start: {'batteryLevel': 100, 'powered': ['AC'], 'wakefulness': 'Awake'}
- Merged runs: Android_SDK_built_for_x86-5620-20261020163751 (all); Android_SDK_built_for_x86-5620-20261020181439 (S4a)
- PROJECT_MEDIA app-op (ROADMAP Q3): `allow` during S4a normal, S4a force, before `default`, afterwards `default`
- Warm-ups 2, iterations 20, poll 10000 ms, force delay 1000 ms, capture on
- Cell: retained / Java heap MB / native heap MB / script threads / Rhino contexts / live engines (ROADMAP appendix A)
- Thresholds (T1): retained 0, Java <= 2 MB, native <= 4 MB (+1 frame for capture), threads and contexts back to start, engines back to start

| Scenario | Mode | Status | Cell | Reasons |
| --- | --- | --- | --- | --- |
| S1 empty script | N | fail | 1 / +0.04 / -0.25 / +0 / +0 / 0 | retained runtimes 1/20 |
| S1 empty script | F | fail | 1 / +0.04 / -0.20 / +0 / +0 / 0 | retained runtimes 1/20 |
| S2 threads.start x 10 + threads.pool | N | fail | 20 / +30.52 / +0.05 / +0 / +0 / 0 | retained runtimes 20/20; java heap +30.52 MB |
| S2 threads.start x 10 + threads.pool | F | fail | 20 / +30.60 / +0.73 / +44 / +44 / 0 | retained runtimes 20/20; java heap +30.60 MB; script threads +44; rhino contexts +44 |
| S3 nested timers on main and child threads | N | fail | 20 / +29.68 / -0.01 / +0 / +0 / 0 | retained runtimes 20/20; java heap +29.68 MB |
| S3 nested timers on main and child threads | F | fail | 20 / +29.76 / +0.00 / +0 / +0 / 0 | retained runtimes 20/20; java heap +29.76 MB |
| S4a captureScreen x 20 | N | fail | 1 / +0.05 / +4.47 / +0 / +0 / 0 | retained runtimes 1/20 |
| S4a captureScreen x 20 | F | fail | 2 / +1.53 / +4.49 / +0 / +0 / 0 | retained runtimes 2/20 |
| S4b images.read + findImage | N | fail | 1 / +0.04 / -0.21 / +0 / +0 / 0 | retained runtimes 1/20 |
| S4b images.read + findImage | F | fail | 2 / +1.52 / -0.24 / +0 / +0 / 0 | retained runtimes 2/20 |
| S4c images.copy / clip / resize chain | N | fail | 1 / +0.04 / +0.38 / +0 / +0 / 0 | retained runtimes 1/20 |
| S4c images.copy / clip / resize chain | F | fail | 1 / +1.51 / -0.25 / +0 / +0 / 0 | retained runtimes 1/20 |
| S4d matchTemplate + oneShot | N | fail | 1 / +0.04 / -0.25 / +0 / +0 / 0 | retained runtimes 1/20 |
| S4d matchTemplate + oneShot | F | fail | 2 / +1.53 / -0.21 / +0 / +0 / 0 | retained runtimes 2/20 |
| S4e threaded copy + findImage + recycle (issue #372 / #200 pattern) | N | fail | 20 / +30.03 / -0.06 / +0 / +0 / 0 | retained runtimes 20/20; java heap +30.03 MB; runs that did not end in time 2 |
| S4e threaded copy + findImage + recycle (issue #372 / #200 pattern) | F | fail | 20 / +30.24 / +0.28 / +0 / +0 / 0 | retained runtimes 20/20; java heap +30.24 MB |
| S5 ui script with button, text and webview | N | fail | 1 / +0.04 / -0.26 / +0 / +0 / 0 | retained runtimes 1/20 |
| S5 ui script with button, text and webview | F | fail | 1 / +0.04 / -0.14 / +0 / +0 / 0 | retained runtimes 1/20 |
| S6 floaty.window + floaty.rawWindow | N | fail | 1 / +0.05 / +0.54 / +0 / +0 / 0 | retained runtimes 1/20 |
| S6 floaty.window + floaty.rawWindow | F | fail | 1 / +0.07 / +0.75 / +0 / +0 / 0 | retained runtimes 1/20 |
| S7 events / key observer / sensors / accessibility events | N | fail | 1 / +0.04 / -0.21 / +0 / +0 / 0 | retained runtimes 1/20 |
| S7 events / key observer / sensors / accessibility events | F | fail | 1 / +0.04 / -0.27 / +0 / +0 / 0 | retained runtimes 1/20 |
| S8 JavaAdapter / interface adapters x 100 + runtime.loadDex | N | fail | 1 / +0.04 / +0.39 / +0 / +0 / 0 | retained runtimes 1/20 |
| S8 JavaAdapter / interface adapters x 100 + runtime.loadDex | F | fail | 1 / +0.04 / +0.57 / +0 / +0 / 0 | retained runtimes 1/20 |
| S9 engines.execScriptFile restarting itself 3 levels | N | fail | 1 / +0.09 / -0.07 / +0 / +0 / 0 | retained runtimes 1/20 |
| S9 engines.execScriptFile restarting itself 3 levels | F | fail | 1 / +0.10 / -0.05 / +0 / +0 / 0 | retained runtimes 1/20 |
| S10 http.get + WebSocket on a loopback server | N | fail | 1 / +0.08 / -0.05 / +0 / +0 / 0 | retained runtimes 1/20 |
| S10 http.get + WebSocket on a loopback server | F | fail | 20 / +28.85 / +0.78 / +0 / +0 / 0 | retained runtimes 20/20; java heap +28.85 MB |
| S11 device.* reads, vibrate and keepScreenOn | N | fail | 1 / +0.04 / -0.12 / +0 / +0 / 0 | retained runtimes 1/20 |
| S11 device.* reads, vibrate and keepScreenOn | F | fail | 1 / +0.04 / -0.16 / +0 / +0 / 0 | retained runtimes 1/20 |
| S12 plugins.load of an installed official plugin | N | fail | 1 / +0.04 / -0.10 / +0 / +0 / 0 | retained runtimes 1/20 |
| S12 plugins.load of an installed official plugin | F | fail | 1 / +0.08 / +9.99 / +0 / +0 / 0 | retained runtimes 1/20; native heap +9.99 MB |

## Shortest strong reference paths (shark, exited ScriptRuntime instances)

### S1 N: 2 exited runtimes in the dump, dump 15793 ms, analysis 2874 ms

- 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root STICKY_CLASS: VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
STICKY_CLASS
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S1 F: 2 exited runtimes in the dump, dump 14260 ms, analysis 2817 ms

- 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root STICKY_CLASS: VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
STICKY_CLASS
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S2 N: 22 exited runtimes in the dump, dump 28243 ms, analysis 20899 ms

- 21 runtime(s), root STICKY_CLASS: LooperHelper.sLoopers -> ConcurrentHashMap[] -> Threads$Companion$TimerThread.target -> RhinoUtils$$ExternalSyntheticLambda18.f$0 -> ScriptRuntime
- 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- ignoring java.lang.Thread.target: 14 runtime(s), root STICKY_CLASS: LooperHelper.sLoopers -> ConcurrentHashMap[] -> Looper.mQueue -> MessageQueue.mIdleHandlers -> ArrayList[] -> Loopers.scriptRuntime -> ScriptRuntime
- ignoring java.lang.Thread.target: 7 runtime(s), root STICKY_CLASS: LooperHelper.sLoopers -> ConcurrentHashMap[] -> Looper.mQueue -> MessageQueue.mIdleHandlers -> ArrayList[] -> Loopers.scriptRuntime -> ScriptRuntime
- ignoring java.lang.Thread.target: 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime

```
STICKY_CLASS
  org.autojs.autojs.core.looper.LooperHelper.sLoopers (static)
  java.util.concurrent.ConcurrentHashMap[]
  org.autojs.autojs.runtime.api.Threads$Companion$TimerThread.target
  org.autojs.autojs.util.RhinoUtils$$ExternalSyntheticLambda18.f$0
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS (second pass ignoring java.lang.Thread.target)
  org.autojs.autojs.core.looper.LooperHelper.sLoopers (static)
  java.util.concurrent.ConcurrentHashMap[]
  android.os.Looper.mQueue
  android.os.MessageQueue.mIdleHandlers
  java.util.ArrayList[]
  org.autojs.autojs.core.looper.Loopers.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS (second pass ignoring java.lang.Thread.target)
  org.autojs.autojs.core.looper.LooperHelper.sLoopers (static)
  java.util.concurrent.ConcurrentHashMap[]
  android.os.Looper.mQueue
  android.os.MessageQueue.mIdleHandlers
  java.util.ArrayList[]
  org.autojs.autojs.core.looper.Loopers.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS (second pass ignoring java.lang.Thread.target)
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S2 F: 22 exited runtimes in the dump, dump 21170 ms, analysis 6698 ms

- 22 runtime(s), root NATIVE_STACK: Object[][] -> ScriptRuntime

```
NATIVE_STACK
  java.lang.Object[][]
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S3 N: 22 exited runtimes in the dump, dump 8415 ms, analysis 10664 ms

- 21 runtime(s), root STICKY_CLASS: LooperHelper.sLoopers -> ConcurrentHashMap[] -> Threads$Companion$TimerThread.target -> RhinoUtils$$ExternalSyntheticLambda18.f$0 -> ScriptRuntime
- 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- ignoring java.lang.Thread.target: 21 runtime(s), root STICKY_CLASS: LooperHelper.sLoopers -> ConcurrentHashMap[] -> Looper.mQueue -> MessageQueue.mIdleHandlers -> ArrayList[] -> Loopers.scriptRuntime -> ScriptRuntime
- ignoring java.lang.Thread.target: 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime

```
STICKY_CLASS
  org.autojs.autojs.core.looper.LooperHelper.sLoopers (static)
  java.util.concurrent.ConcurrentHashMap[]
  org.autojs.autojs.runtime.api.Threads$Companion$TimerThread.target
  org.autojs.autojs.util.RhinoUtils$$ExternalSyntheticLambda18.f$0
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS (second pass ignoring java.lang.Thread.target)
  org.autojs.autojs.core.looper.LooperHelper.sLoopers (static)
  java.util.concurrent.ConcurrentHashMap[]
  android.os.Looper.mQueue
  android.os.MessageQueue.mIdleHandlers
  java.util.ArrayList[]
  org.autojs.autojs.core.looper.Loopers.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS (second pass ignoring java.lang.Thread.target)
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S3 F: 22 exited runtimes in the dump, dump 13383 ms, analysis 10765 ms

- 21 runtime(s), root STICKY_CLASS: LooperHelper.sLoopers -> ConcurrentHashMap[] -> Threads$Companion$TimerThread.target -> RhinoUtils$$ExternalSyntheticLambda18.f$0 -> ScriptRuntime
- 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- ignoring java.lang.Thread.target: 21 runtime(s), root STICKY_CLASS: LooperHelper.sLoopers -> ConcurrentHashMap[] -> Looper.mQueue -> MessageQueue.mIdleHandlers -> ArrayList[] -> Loopers.scriptRuntime -> ScriptRuntime
- ignoring java.lang.Thread.target: 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime

```
STICKY_CLASS
  org.autojs.autojs.core.looper.LooperHelper.sLoopers (static)
  java.util.concurrent.ConcurrentHashMap[]
  org.autojs.autojs.runtime.api.Threads$Companion$TimerThread.target
  org.autojs.autojs.util.RhinoUtils$$ExternalSyntheticLambda18.f$0
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS (second pass ignoring java.lang.Thread.target)
  org.autojs.autojs.core.looper.LooperHelper.sLoopers (static)
  java.util.concurrent.ConcurrentHashMap[]
  android.os.Looper.mQueue
  android.os.MessageQueue.mIdleHandlers
  java.util.ArrayList[]
  org.autojs.autojs.core.looper.Loopers.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS (second pass ignoring java.lang.Thread.target)
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S4a N: 2 exited runtimes in the dump, dump 18104 ms, analysis 3678 ms

- 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root STICKY_CLASS: VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
STICKY_CLASS
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S4a F: 3 exited runtimes in the dump, dump 9798 ms, analysis 5930 ms

- 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root STICKY_CLASS: VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: TermSession$2<local> -> Message.callback -> LoopBasedJavaScriptEngine$$ExternalSyntheticLambda0.f$0 -> LoopBasedJavaScriptEngine.mRuntime -> ScriptRuntime

```
STICKY_CLASS
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  jackpal.androidterm.emulatorview.TermSession$2 <local>
  android.os.Message.callback
  org.autojs.autojs.engine.LoopBasedJavaScriptEngine$$ExternalSyntheticLambda0.f$0
  org.autojs.autojs.engine.LoopBasedJavaScriptEngine.mRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S4b N: 2 exited runtimes in the dump, dump 12141 ms, analysis 2866 ms

- 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root STICKY_CLASS: VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
STICKY_CLASS
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S4b F: 3 exited runtimes in the dump, dump 20605 ms, analysis 9546 ms

- 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root STICKY_CLASS: VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: TermSession$2<local> -> Message.callback -> LoopBasedJavaScriptEngine$$ExternalSyntheticLambda0.f$0 -> LoopBasedJavaScriptEngine.mRuntime -> ScriptRuntime

```
STICKY_CLASS
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  jackpal.androidterm.emulatorview.TermSession$2 <local>
  android.os.Message.callback
  org.autojs.autojs.engine.LoopBasedJavaScriptEngine$$ExternalSyntheticLambda0.f$0
  org.autojs.autojs.engine.LoopBasedJavaScriptEngine.mRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S4c N: 2 exited runtimes in the dump, dump 10737 ms, analysis 3265 ms

- 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root STICKY_CLASS: VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
STICKY_CLASS
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S4c F: 3 exited runtimes in the dump, dump 15147 ms, analysis 4371 ms

- 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root STICKY_CLASS: VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: TermSession$2<local> -> Message.callback -> LoopBasedJavaScriptEngine$$ExternalSyntheticLambda0.f$0 -> LoopBasedJavaScriptEngine.mRuntime -> ScriptRuntime

```
STICKY_CLASS
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  jackpal.androidterm.emulatorview.TermSession$2 <local>
  android.os.Message.callback
  org.autojs.autojs.engine.LoopBasedJavaScriptEngine$$ExternalSyntheticLambda0.f$0
  org.autojs.autojs.engine.LoopBasedJavaScriptEngine.mRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S4d N: 2 exited runtimes in the dump, dump 17620 ms, analysis 3386 ms

- 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root STICKY_CLASS: VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
STICKY_CLASS
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S4d F: 3 exited runtimes in the dump, dump 12584 ms, analysis 4623 ms

- 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root STICKY_CLASS: VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime
- 1 runtime(s), root THREAD_OBJECT: TermSession$2<local> -> Message.callback -> LoopBasedJavaScriptEngine$$ExternalSyntheticLambda0.f$0 -> LoopBasedJavaScriptEngine.mRuntime -> ScriptRuntime

```
STICKY_CLASS
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
THREAD_OBJECT
  jackpal.androidterm.emulatorview.TermSession$2 <local>
  android.os.Message.callback
  org.autojs.autojs.engine.LoopBasedJavaScriptEngine$$ExternalSyntheticLambda0.f$0
  org.autojs.autojs.engine.LoopBasedJavaScriptEngine.mRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S4e N: 22 exited runtimes in the dump, dump 30405 ms, analysis 21228 ms

- 21 runtime(s), root STICKY_CLASS: LooperHelper.sLoopers -> ConcurrentHashMap[] -> Threads$Companion$TimerThread.target -> RhinoUtils$$ExternalSyntheticLambda18.f$0 -> ScriptRuntime
- 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- ignoring java.lang.Thread.target: 21 runtime(s), root STICKY_CLASS: LooperHelper.sLoopers -> ConcurrentHashMap[] -> Looper.mQueue -> MessageQueue.mIdleHandlers -> ArrayList[] -> Loopers.scriptRuntime -> ScriptRuntime
- ignoring java.lang.Thread.target: 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime

```
STICKY_CLASS
  org.autojs.autojs.core.looper.LooperHelper.sLoopers (static)
  java.util.concurrent.ConcurrentHashMap[]
  org.autojs.autojs.runtime.api.Threads$Companion$TimerThread.target
  org.autojs.autojs.util.RhinoUtils$$ExternalSyntheticLambda18.f$0
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS (second pass ignoring java.lang.Thread.target)
  org.autojs.autojs.core.looper.LooperHelper.sLoopers (static)
  java.util.concurrent.ConcurrentHashMap[]
  android.os.Looper.mQueue
  android.os.MessageQueue.mIdleHandlers
  java.util.ArrayList[]
  org.autojs.autojs.core.looper.Loopers.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS (second pass ignoring java.lang.Thread.target)
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S4e F: 22 exited runtimes in the dump, dump 20524 ms, analysis 15356 ms

- 21 runtime(s), root STICKY_CLASS: LooperHelper.sLoopers -> ConcurrentHashMap[] -> Threads$Companion$TimerThread.target -> RhinoUtils$$ExternalSyntheticLambda18.f$0 -> ScriptRuntime
- 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- ignoring java.lang.Thread.target: 21 runtime(s), root STICKY_CLASS: LooperHelper.sLoopers -> ConcurrentHashMap[] -> Looper.mQueue -> MessageQueue.mIdleHandlers -> ArrayList[] -> Loopers.scriptRuntime -> ScriptRuntime
- ignoring java.lang.Thread.target: 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime

```
STICKY_CLASS
  org.autojs.autojs.core.looper.LooperHelper.sLoopers (static)
  java.util.concurrent.ConcurrentHashMap[]
  org.autojs.autojs.runtime.api.Threads$Companion$TimerThread.target
  org.autojs.autojs.util.RhinoUtils$$ExternalSyntheticLambda18.f$0
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS (second pass ignoring java.lang.Thread.target)
  org.autojs.autojs.core.looper.LooperHelper.sLoopers (static)
  java.util.concurrent.ConcurrentHashMap[]
  android.os.Looper.mQueue
  android.os.MessageQueue.mIdleHandlers
  java.util.ArrayList[]
  org.autojs.autojs.core.looper.Loopers.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS (second pass ignoring java.lang.Thread.target)
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S5 N: 2 exited runtimes in the dump, dump 15115 ms, analysis 3989 ms

- 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root STICKY_CLASS: VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
STICKY_CLASS
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S5 F: 2 exited runtimes in the dump, dump 15315 ms, analysis 3465 ms

- 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root STICKY_CLASS: VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
STICKY_CLASS
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S6 N: 2 exited runtimes in the dump, dump 15362 ms, analysis 2759 ms

- 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root STICKY_CLASS: VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
STICKY_CLASS
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S6 F: 2 exited runtimes in the dump, dump 18673 ms, analysis 3450 ms

- 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root STICKY_CLASS: VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
STICKY_CLASS
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S7 N: 2 exited runtimes in the dump, dump 15482 ms, analysis 2934 ms

- 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root STICKY_CLASS: VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
STICKY_CLASS
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S7 F: 2 exited runtimes in the dump, dump 9649 ms, analysis 3073 ms

- 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root STICKY_CLASS: VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
STICKY_CLASS
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S8 N: 2 exited runtimes in the dump, dump 9393 ms, analysis 3180 ms

- 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root STICKY_CLASS: VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
STICKY_CLASS
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S8 F: 2 exited runtimes in the dump, dump 21212 ms, analysis 3243 ms

- 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root STICKY_CLASS: VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
STICKY_CLASS
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S9 N: 2 exited runtimes in the dump, dump 15555 ms, analysis 2960 ms

- 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root STICKY_CLASS: VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
STICKY_CLASS
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S9 F: 2 exited runtimes in the dump, dump 14847 ms, analysis 4181 ms

- 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root STICKY_CLASS: VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
STICKY_CLASS
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S10 N: 2 exited runtimes in the dump, dump 12902 ms, analysis 3230 ms

- 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root STICKY_CLASS: VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
STICKY_CLASS
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S10 F: 21 exited runtimes in the dump, dump 11059 ms, analysis 9543 ms

- 19 runtime(s), root THREAD_OBJECT: Thread<local> -> RealWebSocket.listener -> WebSocket$WebSocketMessageListener.this$0 -> WebSocket.mBridges -> ScriptBridges.engine -> LoopBasedJavaScriptEngine.mRuntime -> ScriptRuntime
- 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root STICKY_CLASS: VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

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
STICKY_CLASS
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S11 N: 2 exited runtimes in the dump, dump 16866 ms, analysis 3871 ms

- 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root STICKY_CLASS: VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
STICKY_CLASS
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S11 F: 2 exited runtimes in the dump, dump 22170 ms, analysis 3847 ms

- 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root STICKY_CLASS: VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
STICKY_CLASS
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S12 N: 2 exited runtimes in the dump, dump 18835 ms, analysis 5008 ms

- 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root STICKY_CLASS: VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
STICKY_CLASS
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS
  org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo.obj (static)
  org.mozilla.javascript.NativeObject.parentScopeObject
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S12 F: 2 exited runtimes in the dump, dump 20766 ms, analysis 4107 ms

- 1 runtime(s), root STICKY_CLASS: AccessibilityService.defaultBridge -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator$$ExternalSyntheticLambda4.f$0 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime
- 1 runtime(s), root STICKY_CLASS: VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
STICKY_CLASS
  org.autojs.autojs.core.accessibility.AccessibilityService.defaultBridge (static)
  org.autojs.autojs.core.accessibility.AccessibilityBridgeImpl.tap
  org.autojs.autojs.core.accessibility.SimpleActionAutomator$$ExternalSyntheticLambda4.f$0
  org.autojs.autojs.core.accessibility.SimpleActionAutomator.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

```
STICKY_CLASS
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
