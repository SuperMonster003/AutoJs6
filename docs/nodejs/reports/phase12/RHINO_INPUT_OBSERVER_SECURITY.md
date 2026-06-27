# Rhino Input Observer Security Gate

P12-07 closes the Phase 12 `input_observer` key-observation track. It does not
add touch observation or key interception. It hardens the P12-05 fake provider
and P12-06 accessibility live-source gate with cleanup diagnostics, bounded
queues, negative fallback checks, and a packaged/background rejection report.

## Runtime Policy

- `require("input_observer")` exposes only `getAvailableSources()`,
  `observeKeys()`, `drainEvents()`, and `close()`.
- `source: "fake"` is deterministic test input. It queues JSON key snapshots
  for the requested key/action pairs, never Android events.
- `source: "accessibility"` may register an `OnKeyListener` only when the
  current execution is an active one-shot script or an interactive visible
  session and the AutoJs6 accessibility service is operational in the current
  process.
- Scheduled/background launch surfaces reject live accessibility observation
  before attempting to register a listener.
- `intercept: true`, `observeTouch`, unknown sources, shell fallback, overlay
  capture, root capture, and hidden background listeners are not supported.

## Cleanup And Diagnostics

Every active subscription is registered as
`NodeBridgeResourceType.INPUT_OBSERVER_SUBSCRIPTION`. `close()`, engine destroy,
and accessibility-service disconnect all route through the same resource close
path.

Provider diagnostics expose:

- `embedded_script.input_observer_active_subscription_count`
- `embedded_script.input_observer_pending_event_count`
- `embedded_script.input_observer_dropped_event_count`
- `embedded_script.input_observer_live_listener_count`

After provider destroy, active subscriptions, pending events, dropped events,
and live listener counts must be zero.

## Queue Boundaries

The provider enforces `maxQueueSize` with a hard maximum of 256. On overflow it
drops oldest events and increments `droppedCount`; queues do not grow without a
bound. Draining is additionally capped by `maxEvents`.

## Packaged Rejection Report

Phase 12 does not promote hidden packaged/background input observation.
Packaged metadata may declare `input_observer` capabilities, but no Android
manifest permission is injected automatically and live key observation is not
allowed from scheduled/background launch surfaces.

Allowed live paths are restricted to:

- active one-shot script execution, while the execution is still alive;
- interactive visible session execution, with a user-visible stop/revocation
  path.

Everything else must fail with a stable bridge denial or provider-unavailable
error. In particular, packaged scheduled runners, service-process-only
background work, hidden overlay capture, shell/root key polling, and silent key
interception are rejected. A future packaged promotion would need separate
foreground visibility evidence and device smoke coverage.

## Evidence

- Runtime provider: `app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt`
- Service disconnect callback: `app/src/main/java/org/autojs/autojs/core/accessibility/AccessibilityService.kt`
- Execution mode policy: `app/src/main/java/org/autojs/autojs/engine/NodeExecutionModePolicy.kt`
- Provider health: `app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt`
- Instrumentation: `app/src/androidTest/java/org/autojs/autojs/engine/NodeInputObserverBridgeInstrumentationTest.kt`
- Host verifier: `tools/nodejs/phase12/verify-rhino-compat-input-observer-security.js`
- Gradle gate: `:app:verifyNodeRhinoCompatInputObserverSecurity`
