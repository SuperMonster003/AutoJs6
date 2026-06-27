# S8-20 Web Platform Globals Alignment

Status: complete

S8-20 adds a focused instrumentation gate for the Web Platform globals that are
needed before the controlled `node:http` and `node:https` client facade work.
It does not widen network authority or expose raw sockets.

## Covered Globals

- `Headers`
- `Request`
- `Response`
- `FormData`
- `Blob`
- `File`
- `URLPattern`
- `DOMException`
- `EventTarget`
- `MessageEvent`
- `CompressionStream`
- `DecompressionStream`

## Behavioral Coverage

`NodeWebPlatformGlobalsInstrumentationTest` verifies:

- every covered constructor is present on `globalThis`
- global descriptors stay non-writable
- `Headers` append/set/get/key iteration semantics
- `Blob` and `File` text, type, name, and `lastModified` behavior
- `FormData` string and Blob-with-filename entries
- `Request` method/header/body consumption and `bodyUsed`
- `Response` status, status text, `ok`, headers, and JSON parsing
- `URLPattern` object construction, `test()`, and group extraction
- `DOMException` name/message/code shape
- `EventTarget` listener object, `once`, cancelation, target, and
  `currentTarget`
- `MessageEvent` data, origin, lastEventId, and ports shape
- `CompressionStream` and `DecompressionStream` gzip roundtrip
- invalid compression format rejection
- global `fetch()` remains denied in the default controlled-network-off profile

The compression helper reads and writes concurrently so Web Stream backpressure
does not leave the embedded result envelope pending.

## Gate

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeWebPlatformGlobalsInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

This gate is also part of `nodeJsV11AlphaInstrumentationClasses`, and therefore
flows into the v1.1 alpha/beta functional profile selection.

## Evidence

Verified on Sony XQ-AT72 `QV710AF65F` on 2026-06-20:

- `:app:compileAppDebugAndroidTestKotlin`
- `:app:connectedAppDebugAndroidTest` for
  `NodeWebPlatformGlobalsInstrumentationTest`

The focused connected test finished 1/1 successfully.

## Security Boundary

S8-20 is a compatibility alignment only. It keeps these boundaries unchanged:

- raw `http`, `https`, `net`, `tls`, `dns`, and `dgram` network modules remain
  disabled for I/O
- global `fetch()` still rejects without controlled network authority
- no native addon, process, Java/Android object, raw socket, or unscoped
  filesystem capability is added
- packaged APK manifest behavior does not change solely because these globals
  are present
