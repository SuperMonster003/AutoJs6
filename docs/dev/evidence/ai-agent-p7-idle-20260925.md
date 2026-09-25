# P7 task service and idle reclamation

API 37.1 / 16 KiB private AVD, real host APK build 5296, production Agent debug build 66. No production code or public contract changed in this item.

Host `AiAgentPluginRoundTripTest` methods:

- `foregroundServiceExistsOnlyWhileATaskIsActive`: cancellation removes the foreground service.
- `foregroundServiceCoversQueuedWorkAndStopsAfterTheLastCompletion`: one active and two queued tasks; service covers each task and exits after the last completion.
- `idleUnboundPluginCanBeReclaimedAndAttachedWithoutReplayingHistory`: persist terminal history, detach/unbind, Android `am kill`, observe process disappearance, attach a different PID, wait for asynchronous history loading, assert zero replay and one successful explicit new task.

Final instrumentation: 3/3, 1.664 s. Enable `autojs.agent.plugin=true` and `autojs.agent.lifecycle=true`; use a private unlocked AVD. The reclamation case guards against physical devices and uses Android's safe-process reclamation rather than force-stop/SIGKILL. The initial test read too early during asynchronous loading; the final assertions wait for terminal durability before reclamation and history availability after attachment.

Agent-side full instrumentation: 74/74, 284.621 s. Its independent idle-overlay probe observed 0 refresh dispatches and model calls in both 5-second windows. Agent JVM: 472 passed / 1 opt-in performance skip; debug/instrumentation/lint succeeded, 0 errors / 6 existing warnings. Full host lint remains outside this item's completed validation. Neither whole-device battery life nor a release gate is claimed.
