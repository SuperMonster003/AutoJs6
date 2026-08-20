# AutoJs6 Node.js Compatibility Profile

This document reflects the current stable runtime contract. Regenerate the
machine-readable and Markdown snapshots with
`./gradlew :app:generateNodeCompatibilityProfile`; the outputs are
`app/build/reports/nodejs/compatibility-profile.json` and
`app/build/reports/nodejs/compatibility-profile.md`.

Stable capabilities are compiled into every build and enabled by default.
They do not have Gradle opt-in properties and they do not block Release tasks
or host AAR generation. Runtime permission declarations, allowlists, sandbox
boundaries, timeout/cancellation handling, and resource budgets remain
mandatory. Disabled or unsupported surfaces such as Inspector, native addons,
raw WASI, and unbounded process APIs are not advertised as available.

Generated at: `2026-08-20T20:55:36.287333100+08:00[Asia/Shanghai]`

Source of truth:

- `NodeRuntimeCapabilities.kt`
- `NodeBridgeProtocol.kt` / `NodeBridgeModules`
- `node_bridge_sources.cpp` bootstrap allowlist and denylist
- `app/build.gradle.kts` release ABI policy

## Runtime

| Field | Value |
| --- | --- |
| Profile | AutoJs6 Safe Node Profile |
| Engine | autojs6-node-profile-v1.2 |
| Expected Node | v24.5.0 |
| Default backend | plugin |
| Backend switches | auto, embedded, native, plugin, pluginOptional, rhino |
| Route suffixes | .cjs, .cts, .mjs, .mts, .node.js, .ts, .tsx |

## Feature Flags

| Flag | Default | Effective | Status | Security |
| --- | --- | --- | --- | --- |
| esm | true | true | stable | medium |
| dynamic_import | true | true | stable | medium |
| network | true | true | stable | high |
| raw_node_network_modules | true | true | stable | high |
| websocket | true | true | stable | high |
| worker_threads | true | true | stable | high |
| fs_streams | true | true | partial | medium |
| zlib_streams | true | true | partial | medium |
| vm | true | true | partial | high |
| inspector | false | false | disabled | high |
| java_interop | true | true | stable | critical |
| native_addon | false | false | unsupported | critical |
| child_process | true | true | stable | high |

## Builtins

| Builtin | Status | Raw network I/O |
| --- | --- | --- |
| assert | partial | not-network |
| assert/strict | partial | not-network |
| async_hooks | stable | not-network |
| buffer | partial | not-network |
| child_process | stable | not-network |
| cluster | disabled | not-network |
| console | partial | not-network |
| constants | partial | not-network |
| crypto | partial | not-network |
| dgram | disabled | not-network |
| diagnostics_channel | stable | not-network |
| dns | stable | enabled-by-default-policy-controlled |
| dns/promises | stable | enabled-by-default-policy-controlled |
| domain | disabled | not-network |
| events | stable | not-network |
| fs | partial | not-network |
| fs/promises | partial | not-network |
| http | stable | enabled-by-default-policy-controlled |
| https | stable | enabled-by-default-policy-controlled |
| inspector | disabled | not-network |
| module | partial | not-network |
| net | stable | enabled-by-default-policy-controlled |
| os | partial | not-network |
| path | stable | not-network |
| path/posix | stable | not-network |
| path/win32 | stable | not-network |
| perf_hooks | stable | not-network |
| process | partial | not-network |
| punycode | partial | not-network |
| querystring | partial | not-network |
| readline | partial | not-network |
| readline/promises | partial | not-network |
| repl | disabled | not-network |
| stream | partial | not-network |
| stream/consumers | partial | not-network |
| stream/promises | partial | not-network |
| stream/web | partial | not-network |
| string_decoder | partial | not-network |
| timers | partial | not-network |
| timers/promises | partial | not-network |
| tls | stable | enabled-by-default-policy-controlled |
| tty | partial | not-network |
| url | partial | not-network |
| util | stable | not-network |
| util/types | stable | not-network |
| v8 | partial | not-network |
| vm | partial | not-network |
| wasi | disabled | not-network |
| worker_threads | stable | not-network |
| zlib | partial | not-network |
| zlib/promises | disabled | not-network |

## AutoJs Bridge Modules

| Module | Status | Methods |
| --- | --- | --- |
| accessibility | partial | back, click, clickText, ensureEnabled, findAll, findByText, findOne, gesture, home, isEnabled, longClick, recentApps, scrollBackward, scrollForward, setText, swipe |
| app | stable | editFile, getAppName, getPackageName, isInstalled, launchApp, launchPackage, openAppSetting, startActivity, viewFile |
| barcode | stable | detect, detectAll, recognizeText, recognizeTexts |
| clipboard | stable | getText, hasText, setText |
| database | stable | all, close, exec, get, open, run, transaction |
| device | stable | isIgnoringBatteryOptimizations, isScreenOn, openBatteryOptimizationSettings, vibrate, wakeUp |
| dialogs | partial | alert, confirm, input, select |
| engines | stable | all, execScript, execScriptFile, myEngine, stopAll, stopSelf |
| fetch | stable | request |
| image | partial | captureScreen, clip, findColor, findImage, findMultiColors, grayscale, matchTemplate, readImage, recycle, resize, saveImage, threshold |
| images | partial | captureScreen, clip, findColor, findImage, findMultiColors, grayscale, matchTemplate, readImage, recycle, resize, saveImage, threshold |
| input_observer | stable | close, drainEvents, getAvailableSources, observeKeys |
| java | stable | call, callInstance, callStatic, dispose, getField, new, release, type |
| media | stable | getAudioStreamInfo, getAudioStreamMaxVolume, getAudioStreamVolume |
| media_projection | partial | nextImage, requestScreenCapture, stop |
| mediainfo | stable | get, read |
| notifications | stable | cancel, cancelAll, getPermissionStatus, notify, openSettings |
| ocr | stable | detectTextBounds, recognize, recognizeText |
| package_manager | stable | install, list, planInstall, planRemove, planUpdate, prune, remove, update, verify |
| recorder | stable | getStatus, start, stop |
| rhino | stable | run |
| sensors | stable | drainEvents, getAvailableSensors, once, subscribe, unsubscribe |
| shell | stable | exec |
| sqlite | stable | all, close, exec, get, open, run, transaction |
| storage | stable | clear, contains, get, keys, put, remove |
| storages | stable | clear, contains, get, keys, put, remove |
| toast | stable | showToast, toast |
| ui | partial | batchUpdate, close, drainEvents, showLayout, update |
| ui.overlay | stable | close, closeAll, drainEvents, hasPermission, openPermissionSettings, show, update |
| websocket | stable | close, connect, drainEvents, send |
| work_manager | stable | cancel, list, scheduleOnce, schedulePeriodic |

## Dangerous APIs

| API | Status | Security |
| --- | --- | --- |
| child_process | stable | high |
| dynamic_import | stable | medium |
| esm | stable | medium |
| inspector | disabled | high |
| java_interop | stable | critical |
| native_addon | unsupported | critical |
| process._debugProcess | disabled | high |
| process._linkedBinding | disabled | high |
| process.abort | disabled | high |
| process.binding | disabled | high |
| process.dlopen | disabled | high |
| process.exit | disabled | high |
| process.initgroups | disabled | high |
| process.kill | disabled | high |
| process.reallyExit | disabled | high |
| process.setegid | disabled | high |
| process.seteuid | disabled | high |
| process.setgid | disabled | high |
| process.setgroups | disabled | high |
| process.setuid | disabled | high |
| raw_node_network | stable | high |
| vm | partial | high |
| worker_threads | stable | high |

## Packaged APK

| Field | Value |
| --- | --- |
| Status | stable |
| Release flavor | nodePlugin |
| Expected ABIs | none |
| Supported ABIs | arm64-v8a, armeabi-v7a, x86_64 |
| Unsupported ABIs | x86 |
| Native libraries | libnode.so, libautojs6-node.so, libc++_shared.so |
| Service process | :nodejs_embedded |

## Android

| Field | Value |
| --- | --- |
| minSdkVersion | 24 |
| targetSdkVersion | 36 |
| targetSdkVersionInrt | 29 |
| compileSdkVersion | 36 |
