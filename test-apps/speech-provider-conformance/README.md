# Speech plugin conformance fixture

This debug-only, `testOnly` APK (`org.autojs.plugin.speech.fake`) is a fake provider of the speech contract
(`docs/dev/speech-plugin-protocol-v1.md`, 3-Macaw Speech roadmap P1). It runs under its own UID in the `:provider`
process, is signed like the test host so the Plugin Center trusts it, and answers every transaction of contract version 1
with canned values: models, file and PCM recognition, voice activity, synthesis into the host's staged file, progress
operations, external PCM sessions, wake sessions with one `thenListen` round, and a session model (`crash.process`) that
kills its own process to test the death handling. It never touches the network or storage. Do not ship this APK.

A microphone session (`speech.listen()` without `source`) is the background microphone probe of roadmap D22: it records
16 kHz mono PCM16 with `AudioRecord` in this process and reports a `vad` event every 500 ms with `active`, `peak`,
`nonZero`, `frames`, `at` and, from API 29, `silenced` (`AudioRecordingConfiguration.isClientSilenced`). It holds
`RECORD_AUDIO` only after the maintainer allows it in the system dialog of `FakeSpeechPermissionActivity`; without it the
session fails with `PERMISSION_DENIED` like the real plugin.

Build and install with the repository JDK:

```text
gradlew :app:assembleAppDebug :app:assembleAppDebugAndroidTest :test-apps:speech-provider-conformance:assembleDebug
adb -s SERIAL install -r PATH_TO_HOST_APK
adb -s SERIAL install -r -t app/build/outputs/apk/androidTest/app/debug/app-app-debug-androidTest.apk
adb -s SERIAL install -r -t test-apps/speech-provider-conformance/build/outputs/apk/debug/speech-provider-conformance-debug.apk
adb -s SERIAL shell am instrument -w -r -e class org.autojs.autojs.core.plugin.speech.SpeechScriptApiTest org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner
```

`SpeechScriptApiTest` runs its round trip tests when this APK is installed and its missing / incompatible plugin test
when it is not (uninstall it with `adb uninstall org.autojs.plugin.speech.fake`).

Background microphone probe (no instrumentation, so AutoJs6 keeps its real process state):

```text
adb -s SERIAL shell am start -n org.autojs.plugin.speech.fake/.FakeSpeechPermissionActivity
py test-apps/speech-provider-conformance/run_microphone_probe.py SERIAL
```

The first command shows the system microphone dialog, which the maintainer answers on the device. The probe starts a
script through `RunIntentActivity`, keeps AutoJs6 in the foreground, then presses HOME, then turns the screen off, and
prints for each phase whether the fake plugin process received audio or silence.
