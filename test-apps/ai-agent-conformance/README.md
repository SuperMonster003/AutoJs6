# AI Agent host conformance fixture

This debug-only, `testOnly` APK runs under its own UID and `:agent` process. It has no network, accessibility, storage or device-control permissions. The exported driver requires the host's signature permission and verifies both the host package and its signer on every entry point. Do not ship this APK.

The driver receives the production host capability/model Binder objects, invokes them on its own worker, and relays callbacks through its own Binder stubs. Host assertions verify the callback UID, so an accidental same-process call cannot pass as a cross-process result. The separate test-only control AIDL uses Binder handles and then the existing generated production interfaces; it changes no published contract or API AAR. Its copy under `app/src/androidTest/aidl` must stay byte-identical.

`AiAgentGrantConformanceTest` exercises:

- Method and permission denial before dispatch, byte ceilings with multibyte UTF-8, and a valid request before/after rejection.
- Required-permission enforcement in the actual Android provider even if the caller omits permission declarations.
- The actual accessibility query rate limiter using read-only `isEnabled`, including recovery after the window expires.
- Pending-call concurrency and revocation.
- All eight capability/model Binder entry points with a foreign UID, including destroy/cancel, and a valid owner as positive control.
- Model input bytes, calls per minute, token reservation and settlement, using injected deterministic provider runners. No real model is invoked.
- Host logcat exclusion of private request text, including an explicitly logged toast through the shared plugin broker.
- Real ordered broadcasts to the production manifest receiver, from a same-signer package that is not the AI Agent. Missing, mistyped and foreign-created identity tokens must be rejected. Claimed package-name extras do not provide authority.

Build/install with the repository JDK and usual Gradle vendor flags:

```text
gradlew :app:assembleAppDebug :app:assembleAppDebugAndroidTest :test-apps:ai-agent-conformance:assembleDebug
adb -s SERIAL install -r PATH_TO_HOST_APK
adb -s SERIAL install -r -t app/build/outputs/apk/androidTest/app/debug/app-app-debug-androidTest.apk
adb -s SERIAL install -r -t test-apps/ai-agent-conformance/build/outputs/apk/debug/ai-agent-conformance-debug.apk
adb -s SERIAL shell am instrument -w -r -e autojs.aiAgent.grant.conformance.enabled true -e class org.autojs.autojs.core.plugin.agent.AiAgentGrantConformanceTest org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner
```

Use an isolated emulator. The log test displays a toast containing a randomly generated test marker. No screen taps, real model selection, network changes, orders or payments are required. Tests unbind the driver and destroy host brokers. The driver APK must be signed like the test host, as with the other repository conformance APKs.

This first fixture increment covers the original P7 host grant matrix. The original P7 full fake Agent attach/startRun/hostile-callback scenarios and plugin-side fake host APK, and P1.3's pending cross-process attach/detach/death evidence, remain open. Passing this suite does not close those roadmap items, the six-device matrix or an E4 task acceptance.
