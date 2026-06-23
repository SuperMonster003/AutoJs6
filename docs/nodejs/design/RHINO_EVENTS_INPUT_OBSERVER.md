# Rhino Events/Input Observer Compatibility

## Decision

S11-21 is a design gate only. Node compatibility must not expose Rhino's raw `events`
object, `events.__asEmitter__()`, Java `EventEmitter`, Rhino `ScriptableObject`,
`Thread`, or `MainThreadProxy` semantics through `require("autojs6:compat")`.

Pure JavaScript event emitters are already covered by Node's built-in
`require("node:events")`. That surface is unrelated to Android key/touch input
observation and must not be treated as a Rhino `events` facade.

Rhino key and touch observers are lifecycle-heavy and privacy-sensitive:

- `events.observeKey()`
- `events.onKeyDown(keyName, listener)`
- `events.onKeyUp(keyName, listener)`
- `events.on("key_down", listener)` / `events.on("key_up", listener)`
- `events.setKeyInterceptionEnabled([key, ]enabled)`
- `events.observeTouch()`
- `events.onTouch(listener)` / `events.on("touch", listener)`

These APIs require a future controlled `input_observer` bridge provider before any
Node facade is promoted.

`events.keys` is a safe pure-constant subset. P12-03 promotes that subset through
`compat.keys` and `compat.events.keys` without an Android provider. It does not
add key observers, touch observers, key interception, an `events` global, or a
`require("events")` override.

## Current Rhino Shape

The augment layer exposes `events.__asEmitter__()` by grafting public methods from a
Java `EventEmitter` onto a Rhino `ScriptableObject`. It may bind the emitter to the
default event loop, a `MainThreadProxy`, or a Java `Thread`.

The lower-level Rhino `Events` runtime registers listeners against the active
AutoJs6 accessibility service for key observation, `InputEventObserver` for touch
observation, and the accessibility key interrupter for key interception. Cleanup is
explicit in Rhino runtime shutdown: key listeners, touch observers, notification
observers, toast observers, and key interrupters are removed.

That model cannot cross into V8 as-is because it would leak live Java/Rhino objects
and would let native Android callbacks invoke JavaScript outside the Node event-loop
ownership model. In short, Java EventEmitter threading behavior is not a stable
Node compatibility contract.

## Future Node Module Shape

The proposed module name is `input_observer`. A later implementation may expose it
directly and optionally wrap a small Rhino-name facade after the provider is stable.

```js
const inputObserver = require("input_observer");

const keys = await inputObserver.observeKeys(["volume_up"], {
  actions: ["down"],
  maxQueueSize: 64,
  timeoutMs: 10_000,
});

keys.on("event", (event) => {
  console.log(event.keyName, event.action);
});

await keys.close();
```

The JavaScript API may provide callback helpers, but Android must never call those
callbacks directly. The provider records frozen JSON events into a bounded queue,
and the Node-side module drains them through bridge calls such as `drainEvents()`.

The bridge contract should be handle based:

- `observeKeys(descriptor, options?) -> InputObserverSubscription`
- `observeTouch(descriptor, options?) -> InputObserverSubscription`
- `drainEvents(subscriptionId, options?) -> InputObserverEvent[]`
- `close(subscriptionId) -> boolean`

`InputObserverSubscription` is an opaque JS handle with `id`, `type`, `ready`,
`close()`, and optional JS-only listener helpers. It is not an Android listener,
`KeyEvent`, `MotionEvent`, Rhino `EventEmitter`, Java object, or thread handle.

## Event Snapshots

Key events should be serialized as frozen JSON snapshots:

```json
{
  "type": "key",
  "action": "down",
  "keyName": "volume_up",
  "keyCode": 24,
  "eventTimeMs": 123456789,
  "repeatCount": 0,
  "source": "accessibility"
}
```

Touch events should be serialized as frozen JSON snapshots:

```json
{
  "type": "touch",
  "action": "move",
  "x": 120,
  "y": 340,
  "eventTimeMs": 123456789,
  "source": "input_observer"
}
```

No raw `KeyEvent`, `MotionEvent`, `Point`, `AccessibilityService`,
`InputEventObserver`, listener, Binder, `Handler`, `Looper`, `Context`, or Java
object may cross the bridge.

## Resource Model

Future implementation must register every active observer as
`NodeBridgeResourceType.INPUT_OBSERVER_SUBSCRIPTION`. Provider destroy and script
stop must close each owned subscription and report cleanup counters through the
existing ResourceRegistry diagnostics.

Required cleanup rules:

- every subscription has one owner execution id;
- `close()` is idempotent and unregisters Android listeners/interceptors;
- provider destroy closes all owned listeners, including queued but undrained ones;
- queue overflow drops or rejects with explicit diagnostics, never unbounded memory;
- listener callbacks are JS-only wrappers over drained event snapshots;
- cancellation through `AbortSignal`, timeout, script stop, or service destroy uses
  the same close path;
- leak checks must assert zero active `input_observer` resources after destroy.

Suggested hard limits:

| Limit | Default | Hard Maximum |
| --- | ---: | ---: |
| Active input observer subscriptions | 4 | 8 |
| Events per subscription queue | 64 | 256 |
| Drain batch size | 32 | 128 |
| Touch sample interval | 50 ms | 16 ms |

## Capability Manifest Diff

S11-21 does not change `NodeBridgePermissionManifest.definedCapabilities` because no
provider is promoted. The future provider should add capabilities in this shape:

```diff
 Canonical capability names:
+input_observer
+input_observer.keys
+input_observer.touch
+input_observer.intercept
```

`input_observer` grants observation only. `input_observer.keys` and
`input_observer.touch` allow more precise declarations when a manifest is enforced.
`input_observer.intercept` must be separate because it can change system key
behavior and maps to Rhino `events.setKeyInterceptionEnabled()`.

No Android manifest permission should be injected automatically for these proposed
capabilities. Global key observation depends on the user-enabled accessibility
service state and must still audit the `accessibility` provider dependency. Touch
observation depends on an explicit foreground/user-visible provider design and must
not silently use shell, root, overlay, or hidden input-event capture paths.

## Background And Packaged Boundaries

Future support must reject rather than degrade silently when the active launch
surface cannot own input observation.

- One-shot embedded Node may observe only while the execution is active.
- Interactive long-running mode may observe only with a visible session state and a
  user-revocable Stop action.
- Packaged APK metadata may declare the future capabilities, but the provider must
  still reject until the packaged app has a valid foreground/user-visible state and
  any required user-enabled accessibility service state.
- Service-process-only background key/touch observation is not allowed.
- Hidden overlay/touch capture is not allowed.
- Key interception is not allowed without `input_observer.intercept`, foreground
  disclosure, and explicit provider support.

## Migration Mapping

| Rhino API | Node S11-21 decision | Future mapping |
| --- | --- | --- |
| `events.__asEmitter__()` / `events.asEmitter()` | `blocked_by_design` | Use `require("node:events").EventEmitter` for pure JS events. |
| `events.observeKey()` | Design-gated | `inputObserver.observeKeys(...)` after provider promotion. |
| `events.onKeyDown("volume_up", fn)` | Design-gated | Observe key snapshots and filter `event.action === "down"`. |
| `events.on("key_down", fn)` | Design-gated | Subscribe to key snapshots through a JS-only listener helper. |
| `events.setKeyInterceptionEnabled(true)` | Design-gated, separate privilege | Requires `input_observer.intercept` and foreground disclosure. |
| `events.observeTouch()` | Design-gated | `inputObserver.observeTouch(...)` after privacy review. |
| `events.keys.*` | P12-03 promoted pure constants | Use `compat.keys.*` or `compat.events.keys.*`; no bridge/provider access. |

## Verification Gate

`verifyNodeRhinoCompatEventsInputObserverDesign` locks the S11-21 observer
decision by checking the design document, status manifest, generated matrix,
migration guide, security model, loader design, Gradle task registration, and the
absence of promoted `input_observer` runtime capability entries.

`verifyNodeRhinoCompatEventsKeysFacade` locks the P12-03 exception: only
`compat.keys` and `compat.events.keys` constants are promoted, while the runtime
still does not add key observers, touch observers, `input_observer`, a Rhino
`events` global, or a Node core `events` override.
