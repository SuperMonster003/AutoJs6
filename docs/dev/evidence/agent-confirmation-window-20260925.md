# Agent confirmation window identity

The P6.7 Redmi 12C run exposed two separate problems. The plugin's confirmation
activity originally returned to the workbench task instead of the target app.
The AI Agent repository fixes that task handoff. Once the actual Settings window
was restored, its compact tree could still carry the previous confirmation
activity's package/activity names from ActivityInfoProvider.

Those names are asynchronous event/usage-stat hints. They previously formed part
of the node snapshot's window key. A hint changing between inspection and action
could therefore reject a reference although its actual window had not changed.

NodeBridgeAccessibilityDiagnostics now binds snapshots to the actual window IDs
and bounded root fingerprints, which include each root's package. It retains
node fingerprint, bounds, uniqueness, traversal limits, provider scope, expiry,
prepared action type/state and inspected source-to-recipient checks. No stale
reference is retried automatically. A different window continues to fail closed.
Dump/explain/current-window metadata uses an available root package; a cached
activity from another package is omitted instead of labelling the wrong app.

No public JS or AIDL signatures changed. Rhino synchronization work was not
modified. The host build is 5294, paired with AI Agent build 61 for device testing.

Validation:

- Temurin 21 debug and androidTest assembly passed. Final packaging took 1m 52s.
- NodeRefSnapshotsTest and PreparedNodeActionTest: 21 JVM tests passed.
- API 37 / 16 KB AVD: NodeBridgeCompactNodeRefTest, 2/2 passed in 4.499 s on
  build 5294. The new case changes component hints after inspecting a real button,
  checks that the same window identity still acts, then presents a competing
  real dialog and verifies rejection without a second click.
- Redmi instrumentation initially could not bind accessibility after restarting
  the target process. The system reported stale DEAD connections and a pending
  binding. These runs failed at environment readiness before testing the fix.
  Reinstalling the final build and temporarily resetting the enabled-service
  list restored both pre-existing services; no service was left disabled.
- Redmi 12C / API 33 / Model8 Fable 5.1: a task started from the actual floating
  ball, accepted three individual notification confirmations, enabled Wi-Fi and
  observed the checked switch before completing. 7 steps / 6 tools / 7 model
  calls, 202708 ms, 86982 input + 512 output tokens (not estimated). The same
  checkbox action that previously returned NODE_REF_STALE succeeded. No shell
  command or operator tap changed Wi-Fi during the task. The model connection
  used a temporary USB CONNECT proxy; this is not direct-LTE reliability evidence.
- All retries and the entry/history checks are recorded in the AI Agent
  repository's `docs/dev/p67-entry-evidence-2026-09-24.md`.

Ignored local logs: `build/p67-window-build.log`,
`build/p67-window-final-build.log`, `build/p67-window-emulator-5586-final.log`.
Ten changelog languages and their generated history sections were updated using
the existing renderer without refreshing unrelated online project metadata.
