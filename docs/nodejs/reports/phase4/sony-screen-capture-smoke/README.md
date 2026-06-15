# Sony Screen Capture Smoke

Date: 2026-06-16

- device: Sony XQ-AT72
- serial: `QV710AF65F`
- Android: 12 / API 31
- ABI: `arm64-v8a`
- package identity: `org.autojs.autojs6.nodejs`
- profile: `screen_capture`
- test class: `org.autojs.autojs.engine.NodeScreenCaptureRealDeviceSmokeInstrumentationTest`

Command:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pautojs.nodejs.test.deviceProfile=screen_capture" "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.screenCaptureSmoke.permissionTimeoutMs=60000"
```

Result:

- passed on Sony XQ-AT72
- Android MediaProjection prompt was approved on-device via the positive system button
- test requested permission through target app `MainActivity`, captured one frame with `ScreenCapturer`, verified frame and decoded bitmap dimensions, recycled the bitmap/frame, released the capturer, and stopped the foreground service

Notes:

- This profile is optional and is not part of `sony`, `smoke`, `full`, PR, nightly, or release gates.
- OnePlus OPD2413 was not used.
