# 3-Stove Agent host conformance fixture

This debug-only, `testOnly` APK runs under its own UID and `:agent` process. It has no network, accessibility, storage or device-control permissions. The exported driver requires the host's signature permission and verifies both the host package and its signer on every entry point. Do not ship this APK.

The driver receives the production host capability/model Binder objects, invokes them on its own worker, and relays callbacks through its own Binder stubs. Host assertions verify the callback UID, so an accidental same-process call cannot pass as a cross-process result. The separate test-only control AIDL uses Binder handles and then the existing generated production interfaces; it changes no published contract or API AAR. Its copy under `app/src/androidTest/aidl` must stay byte-identical.

`ThreeStoveAgentGrantConformanceTest` exercises:

- Method and permission denial before dispatch, byte ceilings with multibyte UTF-8, and a valid request before/after rejection.
- Required-permission enforcement in the actual Android provider even if the caller omits permission declarations.
- The actual accessibility query rate limiter using read-only `isEnabled`, including recovery after the window expires.
- Pending-call concurrency and revocation.
- All eight capability/model Binder entry points with a foreign UID, including destroy/cancel, and a valid owner as positive control.
- Model input bytes, calls per minute, token reservation and settlement, using injected deterministic provider runners. No real model is invoked.
- Host logcat exclusion of private request text, including an explicitly logged toast through the shared plugin broker.
- Real ordered broadcasts to the production manifest receiver, from a same-signer package that is not the 3-Stove Agent. Missing, mistyped and foreign-created identity tokens must be rejected. Claimed package-name extras do not provide authority.

Build/install with the repository JDK and usual Gradle vendor flags:

```text
gradlew :app:assembleAppDebug :app:assembleAppDebugAndroidTest :test-apps:three-stove-agent-conformance:assembleDebug
adb -s SERIAL install -r PATH_TO_HOST_APK
adb -s SERIAL install -r -t app/build/outputs/apk/androidTest/app/debug/app-app-debug-androidTest.apk
adb -s SERIAL install -r -t test-apps/three-stove-agent-conformance/build/outputs/apk/debug/three-stove-agent-conformance-debug.apk
adb -s SERIAL shell am instrument -w -r -e autojs.threeStoveAgent.grant.conformance.enabled true -e class org.autojs.autojs.core.plugin.agent.ThreeStoveAgentGrantConformanceTest org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner
```

Use an isolated emulator. The log test displays a toast containing a randomly generated test marker. No screen taps, real model selection, network changes, orders or payments are required. Tests unbind the driver and destroy host brokers. The driver APK must be signed like the test host, as with the other repository conformance APKs.

The driver also exposes a minimal fake IThreeStoveAgentPlugin/IThreeStoveAgentLink through `getPlugin`. Test-only `control` operations send deliberately faulty callbacks or terminate only this fixture process. The production host still pins discovery to the real plugin; instrumentation supplies this fixture's transport and UID to the real controller/registry without changing the production authorization policy.

`ThreeStoveAgentPeerConformanceTest` covers early startRun echo, malformed/oversized/version-mismatched link and run callbacks, run-ID/sequence/terminal fencing, retired generations, real transferred-descriptor closure and actual plugin-process death/rebinding without replay. Each controller binding owns a real unbind lease. Run with:

```text
adb -s SERIAL shell am instrument -w -r -e autojs.threeStoveAgent.peer.conformance.enabled true -e class org.autojs.autojs.core.plugin.agent.ThreeStoveAgentPeerConformanceTest org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner
```

The reciprocal independent fake host is in the Agent repository's `test-apps:fake-host`, with its own instrumentation and guarded installation runner. It uses the real host package identity and must run only in a disposable AVD with separate data. See `docs/dev/evidence/ai-agent-p7-conformance-20260925.md` for this item's evidence and remaining gates. The six-device matrix and E4 acceptance are separate.

## Production-plugin lifecycle checks

The host instrumentation also tests the installed production Agent with deterministic model replies. These tests do not require this driver APK. Select the lifecycle methods from `ThreeStoveAgentPluginRoundTripTest`, plus `ThreeStoveAgentPublicApiTest#pluginProcessDeathFailsEveryHostOwnedHandle`, using `-e autojs.agent.plugin true -e autojs.agent.lifecycle true -e autojs.agent.actions true`. Use only a private, unlocked, unsecured AVD. The lifecycle opt-in verifies the device identity before injecting process death or exercising the keyguard.

The real host-death case needs an external driver because killing the host also ends its instrumentation. Install the host debug/instrumentation APKs and production Agent debug APK, then run from the repository root:

```text
python test-apps/three-stove-agent-conformance/run_host_death.py --adb PATH_TO_ADB --serial emulator-5586 --output build/p7-lifecycle-host-death
```

The driver waits for a durable checkpoint containing one running and two queued tasks, verifies the host PID, and sends SIGKILL to that host process only. The setup instrumentation is expected to abort. The plugin must survive and persist three `blocked / HOST_UNAVAILABLE` results. A second instrumentation process then verifies an empty queue, no task foreground service, no model replay, and successful completion of an explicitly requested new task. Only a passing second phase counts as acceptance. Logs stay in the specified local output directory; no private task bodies are printed.

The other lifecycle cases use real plugin Binder death, a separate callback process, duplicate late replies after cancellation, real screen-off/keyguard state and real foreground-window changes. These production-plugin checks complement the independent fake Agent/fake host suites above.

## Production-plugin performance baseline

The installed production plugin can also run the original P7 200-node baseline using the existing debug action surface. No conformance driver APK, Provider model or network is required:

```text
adb -s emulator-5586 shell am instrument -w -r -e autojs.agent.plugin true -e autojs.agent.actions true -e autojs.agent.performance true -e class org.autojs.autojs.core.plugin.agent.ThreeStoveAgentPluginRoundTripTest#performanceBaselineForTwoHundredNodesAndOneTaskMemory org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner
```

Use a private, unlocked AVD with the host debug/test APKs and current Agent APK installed. The test verifies the emulator identity, force-stops the Agent before binding, temporarily enables host accessibility, and creates a grid of 200 real text controls. Every observation updates all controls; the production tool returns exactly 200 accessibility nodes, including structural nodes, and the next model request must contain the current frame's first cell. The normal query grant remains active.

One task makes 35 `ui_dump` calls: one first-use sample, four further warmups, then 30 measured calls. Timing begins immediately before the fixed model decision is delivered and ends at the next model request, including both Binder directions, host traversal, plugin observation processing and context compilation. Host dispatch is also timed separately. Fixture updates, 120 ms query pacing and memory probes are outside the interval. The fixed model performs no inference and explicitly reports zero token usage. Warm p95 must be below 300 ms; the first-use sample and maximum are always reported separately, without a hard real-time claim.

The same fresh plugin process is checked throughout. Private `/proc` counters provide baseline/final RSS and the kernel process high-water RSS; `dumpsys meminfo` samples PSS before the task, at model boundaries every five dumps, and after completion. PSS is a sampled peak, while high-water RSS includes process startup, loaded history and task services. This is not an attribution of all host/model memory to the task. The test restores accessibility services and finishes its fixture; preserve and restore the AVD's surrounding settings when running the suite manually.
