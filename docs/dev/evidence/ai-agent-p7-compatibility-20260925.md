# AI Agent: production entry compatibility

2026-09-25. Host runtime build 5296, installed Agent build 68. This change only updates instrumentation and evidence; it does not change Rhino, public contracts, grants, or the production APK.

`AiAgentEntryDeviceTest.installedPluginLauncherRequestsProductionAttachmentAfterWake` runs as the real host UID. With no active/queued task, it disables automatic connection restoration, detaches, invokes the installed Wake Activity, temporarily enables the official plugin, and opens the plugin's actual launcher. The launcher creates its own immutable PendingIntent and sends the protected `AI_AGENT_ATTACH` broadcast. The production receiver, inspector, connection owner, controller, brokers and plugin service must establish the link without a test transport or identity bypass. The test checks that no task starts and restores enablement, script roots and the remembered connection setting.

The older metadata assertion incorrectly equated the original contract floor (5286) with the installed plugin's later requirement (5289). It now checks agreement between the installed service manifest and its Binder metadata, and verifies that the requirement remains in the supported host range. The first run's failures are retained and are not counted as passes.

| Device | API | Final entry suite | Actual broadcast-to-attached time |
| --- | --- | --- | --- |
| AVD API 24, x86 | 24 | 5/5, 3.077 s | 1456 ms |
| Sony G8441 / BH900ASK9E | 28 | 5/5, 3.076 s | 1071 ms |
| Redmi 12C / bek749scrwv4wo8h | 33 | 5/5, 5.745 s | 1382 ms |
| Sony XQ-DQ72 / QV770340J7 | 33 | 5/5, 1.726 s | 312 ms |
| Xiaomi Pad / 968e9f18 | 35 | 5/5, 2.894 s | 907 ms |
| AVD API 37.1, x86_64, 16 KiB | 37 | 5/5, 5.878 s | 1875 ms |
| Sony XQ-AT72 / QV710AF65F | 31 | Not executed: offline | Expected online by 2026-09-27 20:00 UTC+8 |

The other four entry tests cover discovery, the explicit launcher, foreign identity rejection and all guide states. Together with the independent peer/death/grant matrix in `ai-agent-p7-conformance-20260925.md`, this closes P1.3's remaining positive production broadcast acceptance. These measurements end at `attached`, not the first `attaching` callback, and do not measure or justify a 500 ms foreground fallback policy. No ColorOS device was available.

Validation: `:app:assembleAppDebugAndroidTest` and `:app:lintAnalyzeAppDebugAndroidTest` pass (final build log `build/p7-compat-test-build2.log`). Whole-host lint remains a separate open gate. Device logs are private ignored artifacts in the Agent repository's `build/p7-compat-private/<serial>/host-instrumentation-2.txt`. The Agent compatibility evidence records the notification/floating-window suite and real-model Wi-Fi results separately; passing this entry suite does not prove model task completion.
