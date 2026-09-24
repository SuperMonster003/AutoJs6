# AI Agent P7 lifecycle matrix

Scope: the original P7 lifecycle item, using host 6.8.0 / 5296 and production Agent 1.0.0 / 64 on the private API 37.1 / 16 KiB AVD. Model replies are deterministic fixtures; no Provider, credentials, carrier network or real commerce task is used. This is separate from the pending full fake Agent/fake host conformance and six-device matrices.

## Production fixes

- The Android capability provider checks the host-owned `backend=plugin` and `engineName=ai-agent` identity after permission auditing. Accessibility screen operations, app launches and notification/quick-settings operations return `SCREEN_LOCKED` when the screen is off or the keyguard is showing. Read-only device/screen-state queries remain available. Other plugin families and direct Node.js scripts retain their behavior.
- A real cold-start race was reproduced while preparing the host-death case: `OfficialPluginSettingsProvider` could enter `ThemeColor.fromPreferences` before `App.onCreate`, poisoning `Pref` initialization with `GlobalAppContext` unavailable. `App.attachBaseContext` now publishes both application references before providers are published. `GlobalAppContext` retains the attached `Application` directly rather than asking `LoadedApk` for a reference that may not yet be exposed. A debug-only, unexported provider records whether both references existed at provider creation; instrumentation checks this early snapshot rather than the later initialized state.
- The Agent counterpart records unfinished crash history as `failed / process-died`, retains previously terminal results, propagates a locked pre-action observation and reconciles floating controls during the bounded wake transition. These changes are recorded in the Agent repository's lifecycle evidence.

No JS/AIDL signatures, contract AARs or Rhino sources changed. Existing public history normalization handles both legacy `blocked / HOST_UNAVAILABLE` and new `failed / process-died` records as typed results. A live host handle whose plugin dies still rejects with `PLUGIN_UNAVAILABLE` and has state `failed`.

## Matrix and controls

| Case | Evidence |
| --- | --- |
| Actual host process death | External driver verifies the instrumented host PID, waits for durable journals, sends SIGKILL, and verifies the plugin PID survives. One running and two queued tasks become `blocked / HOST_UNAVAILABLE`. A new host process observes no queued work or task service, zero model replay, and completion of an explicitly new run. |
| Actual plugin process death | SIGKILL of the production `:agent` process produces real Binder death. Three unfinished records recover as `failed / process-died`, pending requests are absent, the queue and task service are empty, and only an explicitly new task invokes the replacement model. |
| Host-owned handles | Force-stopping the plugin makes all three retained host handles settle once as `failed` with `PLUGIN_UNAVAILABLE`. |
| Callback death | A separate test-APK callback process is killed. Run observer loss leaves its owned task able to complete; link callback loss blocks all admitted tasks and refuses late admission. |
| Cancellation races | Model, withheld tool callback and waiting confirmation phases cross real Binder. Duplicate cancellation, duplicate late model/tool replies and a late approval cannot create further work or alter the single terminal result. Existing JVM cases additionally cover both scheduler orderings and cancellation during preparation. |
| Screen off / keyguard | Actual Android screen-off and screen-on-with-keyguard states each produce `SCREEN_LOCKED` observations. A scripted model asks; waking and unlocking alone do not answer it. An explicit answer allows a new observation to complete. Keyguard settings are restored. |
| Foreground app change | The test switches from the host fixture to Settings. A subsequent real dump reports `windowChanged`; separate scripted decisions either launch the target again and verify its package, or ask while Settings remains foreground. |
| Queue and foreground lifetime | Existing queue/cancellation/detach and foreground-service tests are included alongside the new cases. |

## Results

- JVM: 22 passed, no failures/errors/skips: run-record normalization (6), link policy (5), official settings access policy (6), and existing callable-module compatibility (5).
- Android: final 11/11 passed in 17.874 s, including the startup-order probe and the production-plugin lifecycle cases.
- Real host-death recovery: 1/1 passed in 0.211 s after the external driver independently verified three durable blocked records and the surviving plugin process. The preparation instrumentation's `Process crashed` message is the intentional SIGKILL checkpoint, not a passing test or an unexpected application failure. The earlier preparation failure from uninitialized `Pref` is separately retained in local logs and was fixed before this acceptance.
- Host debug and instrumentation APKs assemble; native 16 KiB alignment passes. Android-test-source lint analysis passes in isolation. Whole-host lint remains incomplete from the previous P7 grant session and is not claimed as passed. No lint rule or source set was disabled.
- Ten-language host changelog rendering completed. Release/R8 was not rerun because runtime dependencies and public contracts did not change; release gates remain open.

Reproduction commands and the external driver are in `test-apps/ai-agent-conformance/README.md` and `run_host_death.py`. Local ignored artifacts use `build/p7-lifecycle-*`, including the failed preparation, final Android log, startup build/lint logs and `host-death-result.json`. No private UI dumps or task bodies are committed.

This closes only the original lifecycle matrix once combined with the Agent full-suite evidence. Performance, idle power, full conformance, six-device acceptance, security audit and release gates retain their original roadmap positions.
