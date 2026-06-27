# Input Observer Provider V1

## Decision

P12-04 freezes the `input_observer` provider v1 contract for Node. P12-05 adds
the fake provider and JavaScript module POC. P12-06 adds a controlled
accessibility live-source gate for key observation. P12-07 adds cleanup,
security, and packaged/background rejection evidence:

- P12-06: accessibility live key observer provider gate.
- P12-07: cleanup, security, packaged rejection, and device policy gate.

The first module name is:

```js
const inputObserver = require("input_observer");
```

The v1 runtime must expose only JSON-safe snapshots and opaque subscription
handles. It must not expose Android `KeyEvent`, `MotionEvent`, listener,
`AccessibilityService`, `InputEventObserver`, `Handler`, `Looper`, `Context`,
Binder, Java object, Rhino `EventEmitter`, Rhino `ScriptableObject`, `Thread`, or
`MainThreadProxy` values.

## JavaScript API

The v1 API is Promise-based:

```js
const subscription = await inputObserver.observeKeys({
  keys: ["volume_up"],
  actions: ["down"],
  source: "accessibility",
}, {
  maxQueueSize: 64,
  timeoutMs: 10_000,
});

const events = await subscription.drainEvents({ maxEvents: 32 });
await subscription.close();
```

The module surface is:

- `observeKeys(descriptor, options?) -> Promise<InputObserverSubscription>`
- `getAvailableSources(options?) -> Promise<InputObserverSource[]>`
- `InputObserverSubscription#drainEvents(options?) -> Promise<InputObserverKeyEvent[]>`
- `InputObserverSubscription#close(options?) -> Promise<boolean>`

`observeTouch()` is reserved and remains design-gated. Key interception is not
part of v1 key observation; any request that asks for interception must fail with
a stable unsupported or denied error until a separate `input_observer.intercept`
provider is promoted.

## Descriptor

`observeKeys()` accepts a normalized descriptor:

```json
{
  "type": "key",
  "keys": ["volume_up"],
  "actions": ["down", "up"],
  "source": "accessibility",
  "intercept": false
}
```

Rules:

- `type` must be `key`.
- `keys` must contain normalized string key names, not `KeyEvent` instances.
- `actions` may contain `down` and `up`; omitted actions mean both.
- `source` may be `fake` for P12-05 tests or `accessibility` for the P12-06
  live provider gate. A live provider must reject unavailable service state
  explicitly.
- `intercept` must be absent or `false` in v1.

## Event Snapshot

Drained key events are frozen JSON snapshots:

```json
{
  "type": "key",
  "source": "fake",
  "key": "volume_up",
  "keyCode": 24,
  "action": "down",
  "eventTime": 123456789,
  "repeatCount": 0
}
```

Required fields are `source`, `key`, `keyCode`, `action`, `eventTime`, and
`repeatCount`. `eventTime` is a monotonic millisecond timestamp supplied by the
provider. `keyCode` uses Android key-code numbers but does not expose a raw
Android class.

## Bridge Methods

The controlled bridge module is `input_observer`. P12-05/P12-06 register these
methods:

| Method | Request Args | Response |
| --- | --- | --- |
| `observeKeys` | `[descriptor, options?]` | subscription snapshot |
| `drainEvents` | `[subscriptionId, options?]` | event snapshot array |
| `close` | `[subscriptionId, options?]` | boolean close result |
| `getAvailableSources` | `[options?]` | source snapshot array |

Every response must be JSON-safe. Subscription snapshots may include `id`,
`type`, `source`, `keys`, `actions`, `ready`, `closed`, `createdAt`, and
`droppedCount`; they must not include callbacks, listeners, native pointers, Java
objects, or Android objects.

## Capability Manifest Diff

The future canonical capability names are:

```diff
 Canonical capability names:
+input_observer
+input_observer.keys
+input_observer.touch
+input_observer.intercept
```

`observeKeys()` requires both `input_observer` and `input_observer.keys`.
`getAvailableSources()` requires `input_observer`. `input_observer.touch` is
reserved for touch observation and is not implied by key observation.
`input_observer.intercept` must be separate because interception can change
system key behavior.

No Android manifest permission is injected automatically for these capabilities.
Live key observation depends on the user-enabled accessibility service and a
foreground/user-visible execution surface.

## Limits

| Limit | Default | Hard Maximum |
| --- | ---: | ---: |
| `maxActiveSubscriptions` | 4 | 8 |
| `maxQueueSize` | 64 | 256 |
| `maxDrainBatchSize` | 32 | 128 |
| `maxEventBytes` | 2048 | 4096 |
| `maxTimeoutMs` | 10000 | 60000 |

Queue overflow must be explicit: the provider may drop oldest events or reject
new events, but it must report `droppedCount`. Queues must never grow without a
bound. The close() is idempotent rule means `close()` must resolve `false` after
the subscription is already closed or unknown.

## Resource Model

P12-05/P12-06 implementation must register each active subscription as
`NodeBridgeResourceType.INPUT_OBSERVER_SUBSCRIPTION`. The registered owner is the
current Node execution id; in other words, the owner is the current Node
execution id. Script stop, provider destroy, timeout, `AbortSignal`, and explicit
`close()` must use the same close path.

Policy anchor: owner is the current Node execution id.

Destroy cleanup must report zero active input observer subscriptions. Pending
events and JS-only listener helpers must be cleared when the resource is closed.

## Background And Packaged Policy

P12-05 fake provider tests may run without live Android input. P12-06/P12-07
accessibility live observation requires an operational AutoJs6 accessibility
service in the current process plus an active one-shot script or interactive
visible session. Scheduled/background launch surfaces, service-process-only
observation, and hidden background observation must reject before listener
registration.

- One-shot embedded Node may observe only while the execution is active.
- Interactive long-running mode requires a visible session state and user
  revocation path.
- Packaged APK metadata may declare the capabilities, but live observation still
  requires foreground/user-visible state and any required accessibility service
  state.
- Service-process-only background key/touch observation is not allowed.
- Hidden overlay/touch capture is not allowed.
- Key interception requires `input_observer.intercept`, foreground disclosure,
  and a separate provider gate.

P12-07 publishes the packaged/background rejection report in
`docs/nodejs/reports/phase12/RHINO_INPUT_OBSERVER_SECURITY.md`.

## Type Declaration Draft

The P12-05 implementation adds a checked-in declaration equivalent to this draft:

```ts
declare module "input_observer" {
  export type InputObserverKeyAction = "down" | "up";
  export type InputObserverSourceId = "fake" | "accessibility";

  export interface InputObserverKeyDescriptor {
    readonly type?: "key";
    readonly keys: readonly string[];
    readonly actions?: readonly InputObserverKeyAction[];
    readonly source?: InputObserverSourceId;
    readonly intercept?: false;
  }

  export interface InputObserverOptions {
    readonly maxQueueSize?: number;
    readonly timeoutMs?: number;
    readonly signal?: AbortSignal;
  }

  export interface InputObserverDrainOptions {
    readonly maxEvents?: number;
    readonly timeoutMs?: number;
    readonly signal?: AbortSignal;
  }

  export interface InputObserverKeyEvent {
    readonly type: "key";
    readonly source: InputObserverSourceId;
    readonly key: string;
    readonly keyCode: number;
    readonly action: InputObserverKeyAction;
    readonly eventTime: number;
    readonly repeatCount: number;
  }

  export interface InputObserverSubscription {
    readonly id: string;
    readonly type: "key";
    readonly ready: boolean;
    readonly closed: boolean;
    drainEvents(options?: InputObserverDrainOptions): Promise<readonly InputObserverKeyEvent[]>;
    close(options?: InputObserverDrainOptions): Promise<boolean>;
  }

  export interface InputObserverSource {
    readonly id: InputObserverSourceId;
    readonly type: "key";
    readonly available: boolean;
    readonly live: boolean;
    readonly reason?: string;
  }

  export function observeKeys(
    descriptor: InputObserverKeyDescriptor | readonly string[],
    options?: InputObserverOptions,
  ): Promise<InputObserverSubscription>;

  export function getAvailableSources(options?: InputObserverDrainOptions): Promise<readonly InputObserverSource[]>;
}
```

The checked-in P12-05/P12-06 declaration is
`docs/nodejs/types/autojs6-node/input_observer.d.ts`.

## Schema

`docs/nodejs/schemas/input_observer_provider_v1.schema.json` is the fake-provider
request/response schema fixture. It locks the method names, event snapshot
fields, capability names, and limits that P12-05/P12-06 must implement.
