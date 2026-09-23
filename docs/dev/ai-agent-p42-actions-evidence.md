# AI Agent P4.2 host action evidence

Date: 2026-09-23. Host 6.8.0 / 5289. Plugin development build 1.0.0 / 37.

The host supplies read-only accessibility.inspectNode and provider-scoped tokens
for click, longClick, setText and both scroll directions. Execution validates the
inspected recipient, original child label when applicable, window, method, bounds
and action flags immediately before acting. Password metadata stays available for
the plugin's existing redaction gate; password contents are omitted. Append is
performed inside the host and password append is explicitly rejected. Compact
observations carry an opaque window identity digest. These are additive bridge
methods/metadata; no public JavaScript augment or AIDL signature changed.

The JVM suite has 3188 tests: 3182 passed, 6 existing conditional skips, no failures.
Six new PreparedNodeActionTest cases cover eligibility, changed action/password
flags, bounded search/recycling, text append and UTF-8 limits. The exact Agent grant
test now includes inspectNode. Debug and instrumentation assemblies pass.

AiAgentPluginRoundTripTest uses the installed plugin with the real host UID and
production HostCapabilityBrokerCore. The new debug-only, non-exported
AgentActionSmokeActivity provides synthetic nodes, a password field, a scrolling
region and a gesture target. Scripted model decisions exercise:

- Node-reference click, selector long-click, text replacement/append, two-direction
  scrolling and password replacement. Password text is absent from subsequent
  model input and received events. The next dump reports changes since the action.
- Direct target text changed during confirmation returns NODE_REF_STALE without
  another click. A separate test confirms the same result when a child label
  changes but its clickable parent's identity is unchanged; the unchanged label
  path first verifies that climbing and executing the parent works.
- Clipboard read/write, coordinate click/swipe/gesture, application launch by
  package/name and back/home/recents/notifications/quick_settings. Each mutation
  returns its action receipt, window-change field and stability wait evidence.
- Gesture disabled by default returns TOOL_DISABLED without coordinate dispatch.

Tests use opt-in private read-only AVDs (API 24 x86 / 4 KiB and API 37 x86_64 /
16 KiB). Accessibility settings are restored by the fixture. The password fixture
sets its input type after single-line mode and asserts Android's password flag;
system-bar insets keep controls visible on recent edge-to-edge Android targets.

On each AVD, the real plugin round-trip suite passed 19 tests with one existing
optional Wi-Fi scenario skipped (20 discovered). The capability broker suite
passed 5/5 and the plugin's own instrumentation passed 27/27. Details are recorded
in the companion plugin's docs/dev/p42-action-tools-evidence.md. These are E0/E1
correctness checks with a scripted model. They do not claim real-model E4
acceptance, physical-device compatibility or shopping/payment completion.
