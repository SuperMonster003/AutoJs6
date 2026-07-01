# Overlay Provider V1

Status: P12-12A design-locked contract for the P12-11 Provider path; P12-13
adds the fake-provider runtime POC over `require("ui.overlay")`; P12-14 adds
the Android live gate while keeping overlay creation fail-closed; P13-18
records partial Phase 13 promotion evidence without enabling live creation.

## Decision

P12-12A freezes the first Node overlay provider contract. It is a controlled
JSON-only provider, not Rhino `floaty.window()` or `floaty.rawWindow()` parity.
P12-13 adds the fake-provider runtime POC after this contract. P12-14 records
the live gate for Android permission snapshots, explicit settings launch or
dry-run evidence, packaged `SYSTEM_ALERT_WINDOW` metadata, foreground
disclosure requirements, and cleanup summaries. It does not promote live
overlay creation; `show`, `update`, `drainEvents`, and per-handle `close`
remain fail-closed until a controlled Android provider is reviewed.

P13-18 keeps the same boundary and adds a Phase 13 verifier/report for the
promotion state. The gate proves permission snapshots, settings dry-run/launch,
closeAll cleanup summaries, explicit capabilities, packaged
`SYSTEM_ALERT_WINDOW` metadata records, TypeScript coverage, and raw
Window/View denial. It intentionally leaves live overlay creation blocked until
foreground disclosure, packaged install-run, task-removal cleanup, crash/relaunch,
and real overlay leak/stress evidence exist.

P14-08 adds a controlled Android overlay provider POC for
`show`/`update`/`drainEvents`/`close`/`closeAll`. It creates provider-owned
JSON-only overlay sessions only when `SYSTEM_ALERT_WINDOW` is already granted,
keeps raw `WindowManager`, `LayoutParams`, `FloatyWindow`, `WindowBridge`,
`View`, and `Context` objects out of JavaScript, and records the remaining
foreground disclosure, packaged positive smoke, and stress evidence as future
promotion work.

P14-09 adds the overlay permission/OEM matrix for this provider POC. The matrix
imports the permission-denied boundary, reserves Android 12 class and Android
15+ class archive rows, and keeps permission-granted runs, settings round-trip
OEM notes, task-removal cleanup, and relaunch behavior as future promotion
evidence before any opt-in promotion claim.

The first module name is:

```js
const overlay = require("ui.overlay");
```

`require("floaty")`, `compat.floaty`, and global `floaty` are not part of the
v1 contract. A future compatibility facade may wrap this provider, but it must
not accept Rhino XML literals or return Rhino raw window wrappers.

The provider must expose only JSON-safe snapshots and opaque overlay handles. It
must not expose Android `WindowManager`, `WindowManager.LayoutParams`,
`FloatyService`, `FloatyWindow`, `WindowBridge`, `View`, `ViewGroup`,
`NativeView`, `Context`, `Activity`, `Handler`, Binder, Java objects, Rhino
`JsWindow`, Rhino `JsRawWindow`, callbacks owned by Android, or native pointers.

## JavaScript API

The v1 API is Promise-based:

```js
const overlay = require("ui.overlay");

const handle = await overlay.show({
  content: {
    type: "Column",
    children: [
      { id: "status", type: "Text", text: "Running" },
      { id: "stop", type: "Button", text: "Stop" }
    ]
  },
  window: {
    width: 240,
    height: 120,
    x: 24,
    y: 96,
    touchable: true,
    focusable: false
  },
  disclosure: {
    title: "AutoJs6 overlay",
    text: "Script overlay is visible"
  }
});

await handle.update({ id: "status", text: "Done" });
await handle.close();
```

The module surface is:

- `show(descriptor, options?) -> Promise<OverlayHandle>`
- `closeAll(options?) -> Promise<OverlayCloseSummary>`
- `hasPermission(options?) -> Promise<OverlayPermissionSnapshot>`
- `openPermissionSettings(options?) -> Promise<OverlayPermissionSettingsResult>`
- `OverlayHandle#update(patch, options?) -> Promise<OverlaySnapshot>`
- `OverlayHandle#drainEvents(options?) -> Promise<OverlayEvent[]>`
- `OverlayHandle#close(options?) -> Promise<boolean>`

`rawWindow()`, raw flag mutation, raw view lookup, unrestricted touch capture,
and global input capture are not part of v1.

## Descriptor

`show()` accepts a normalized descriptor:

```json
{
  "content": {
    "type": "Column",
    "children": [
      { "id": "status", "type": "Text", "text": "Running" },
      { "id": "stop", "type": "Button", "text": "Stop" }
    ]
  },
  "window": {
    "width": 240,
    "height": 120,
    "x": 24,
    "y": 96,
    "touchable": true,
    "focusable": false,
    "draggable": true
  },
  "disclosure": {
    "title": "AutoJs6 overlay",
    "text": "Script overlay is visible"
  }
}
```

Rules:

- `content` must be a plain JSON descriptor compatible with the safe UI JSON
  subset. XML raw strings, executable attributes, functions, symbols, bigint,
  `undefined`, cyclic objects, typed arrays, buffers, dates, regular
  expressions, Java objects, and Rhino host objects are rejected.
- The provider contract reuses the safe UI JSON subset for portable component
  content.
- `window` contains provider-owned layout hints only. It must not contain raw
  `WindowManager.LayoutParams`, Android flag integers, gravity constants,
  Binder tokens, or platform object references.
- `touchable` controls whether provider-owned widgets receive ordinary UI
  events. It does not expose raw touch coordinates, gesture streams, or global
  input capture.
- `focusable` defaults to `false`. Focusable overlays require a separate
  follow-up review before promotion if they can affect IME or system prompt
  behavior.
- `disclosure.title` and `disclosure.text` are required. Hidden overlays,
  invisible overlays, fully transparent overlays, off-screen placement used as
  a background service, and overlays without user-visible ownership text are
  rejected.

## Snapshot And Events

`show()` returns an opaque `OverlayHandle`. JavaScript may observe only frozen
JSON snapshots:

```json
{
  "id": "overlay-1",
  "type": "overlay",
  "closed": false,
  "visible": true,
  "createdAt": 123456789,
  "bounds": { "x": 24, "y": 96, "width": 240, "height": 120 },
  "disclosure": { "title": "AutoJs6 overlay" }
}
```

Drained events are JSON snapshots:

```json
{
  "type": "click",
  "overlayId": "overlay-1",
  "targetId": "stop",
  "eventTime": 123456999
}
```

Required event fields are `type`, `overlayId`, `targetId`, and `eventTime`.
Provider-specific diagnostic events may add JSON-only `reason` or `state`
fields. Events must not contain Android event objects, raw coordinates from
global touch capture, view references, callbacks, or native pointers.

## Bridge Methods

The controlled bridge module is `ui.overlay`. P12-13 registers these bridge
methods for fake-transport POC coverage. P12-14 wires live-gate evidence for
`hasPermission`, `openPermissionSettings`, and no-op `closeAll` cleanup, while
live overlay creation remains fail-closed:

| Method | Request Args | Response |
| --- | --- | --- |
| `show` | `[descriptor, options?]` | overlay snapshot |
| `update` | `[overlayId, patch, options?]` | overlay snapshot |
| `drainEvents` | `[overlayId, options?]` | event snapshot array |
| `close` | `[overlayId, options?]` | boolean close result |
| `closeAll` | `[options?]` | close summary |
| `hasPermission` | `[options?]` | permission snapshot |
| `openPermissionSettings` | `[options?]` | settings intent result |

Every response must be JSON-safe. Overlay ids are opaque strings owned by the
provider and current Node execution id.

## Capability Manifest Diff

The canonical capability names are:

```diff
 Canonical capability names:
+ui.overlay
+ui.overlay.permission
+ui.overlay.foreground
+ui.overlay.packaged
+floaty.raw
+floaty.touch
```

Rules:

- `show()`, `update()`, `drainEvents()`, `close()`, and `closeAll()` require
  `ui.overlay`.
- `hasPermission()` and `openPermissionSettings()` require
  `ui.overlay.permission`.
- Foreground notification and user-visible disclosure require
  `ui.overlay.foreground` when Android policy or script lifetime needs a
  foreground service.
- Packaged manifest injection for `SYSTEM_ALERT_WINDOW` requires explicit
  package metadata and `ui.overlay.packaged`; it is never implied by base `ui`.
- `floaty.raw` and `floaty.touch` are reserved blocked-by-design capabilities.
  They are not implied by `ui.overlay` and must not be granted by v1.

No overlay permission is granted or emulated by the Node service process. A
settings page may open only after an explicit user-triggered API call.

## Limits

| Limit | Default | Hard Maximum |
| --- | ---: | ---: |
| `maxActiveOverlays` | 2 | 4 |
| `maxDescriptorBytes` | 32768 | 65536 |
| `maxDescriptorDepth` | 12 | 16 |
| `maxEventQueueSize` | 64 | 128 |
| `maxDrainBatchSize` | 32 | 64 |
| `maxLifetimeMs` | 600000 | 3600000 |

The provider must fail closed when limits are exceeded. Event queue overflow
must report `droppedCount`; queues must never grow without a bound.

## Resource Model

P12-13 implementation must register each active overlay as
`NodeBridgeResourceType.OVERLAY_SESSION` or an equivalent overlay-specific
resource type. The owner is the current Node execution id.

Close is idempotent:

- `handle.close()` resolves `true` the first time it closes an active overlay.
- `handle.close()` resolves `false` after the overlay is already closed or
  unknown to the provider.
- `closeAll()` closes only overlays owned by the current execution id and
  returns a JSON close summary.

Required cleanup paths:

- explicit `handle.close()`
- `closeAll()`
- execution success, failure, timeout, or user Stop
- embedded service destroy and native bridge destroy
- foreground notification stop action
- overlay permission revocation
- provider exception or stale handle detection
- packaged app task removal or process death

Destroy cleanup must report zero active overlay sessions and zero pending
overlay events.

## Background And Packaged Policy

Live overlays require an active one-shot script or an interactive visible
session. Scheduled/background launch surfaces must reject overlay creation before
provider dispatch unless a later task defines explicit user-visible packaged
state and foreground disclosure.

Packaged APK behavior:

- Package metadata must explicitly request the overlay provider.
- `SYSTEM_ALERT_WINDOW` may be injected only from explicit metadata and must be
  reported in diagnostics.
- Older packaged templates without live bridge metadata must fail closed and
  must not queue overlay creation as a host side effect.
- Permission absent, settings launch, foreground disclosure, task removal, and
  process death must have observable provider errors or skip evidence.

Service-process-only hidden overlays are not allowed.

## Type Declaration Draft

P12-13 implementation may add a checked-in declaration equivalent to this draft:

```ts
declare module "ui.overlay" {
  export interface OverlayDescriptor {
    readonly content: Record<string, unknown>;
    readonly window?: OverlayWindowOptions;
    readonly disclosure: OverlayDisclosure;
  }

  export interface OverlayWindowOptions {
    readonly width?: number | "wrap_content" | "match_parent";
    readonly height?: number | "wrap_content" | "match_parent";
    readonly x?: number;
    readonly y?: number;
    readonly touchable?: boolean;
    readonly focusable?: boolean;
    readonly draggable?: boolean;
  }

  export interface OverlayDisclosure {
    readonly title: string;
    readonly text: string;
  }

  export interface OverlaySnapshot {
    readonly id: string;
    readonly type: "overlay";
    readonly closed: boolean;
    readonly visible: boolean;
    readonly createdAt: number;
    readonly bounds?: { readonly x: number; readonly y: number; readonly width: number; readonly height: number };
    readonly disclosure?: { readonly title: string };
  }

  export interface OverlayEvent {
    readonly type: string;
    readonly overlayId: string;
    readonly targetId?: string;
    readonly eventTime: number;
  }

  export interface OverlayCloseSummary {
    readonly closedCount: number;
    readonly remainingCount: number;
  }

  export interface OverlayPermissionSnapshot {
    readonly granted: boolean;
    readonly canOpenSettings: boolean;
  }

  export interface OverlayPermissionSettingsResult {
    readonly opened: boolean;
    readonly reason?: string;
  }

  export interface OverlayHandle {
    readonly id: string;
    update(patch: Record<string, unknown>, options?: { readonly timeoutMs?: number; readonly signal?: AbortSignal }): Promise<OverlaySnapshot>;
    drainEvents(options?: { readonly maxEvents?: number; readonly timeoutMs?: number; readonly signal?: AbortSignal }): Promise<readonly OverlayEvent[]>;
    close(options?: { readonly timeoutMs?: number; readonly signal?: AbortSignal }): Promise<boolean>;
  }

  export function show(descriptor: OverlayDescriptor, options?: { readonly timeoutMs?: number; readonly signal?: AbortSignal }): Promise<OverlayHandle>;
  export function closeAll(options?: { readonly timeoutMs?: number; readonly signal?: AbortSignal }): Promise<OverlayCloseSummary>;
  export function hasPermission(options?: { readonly timeoutMs?: number; readonly signal?: AbortSignal }): Promise<OverlayPermissionSnapshot>;
  export function openPermissionSettings(options?: { readonly timeoutMs?: number; readonly signal?: AbortSignal }): Promise<OverlayPermissionSettingsResult>;
}
```

## Evidence

- Schema: `docs/nodejs/schemas/overlay_provider_v1.schema.json`
- Fixtures: `docs/nodejs/fixtures/overlay-provider-v1-fixtures.json`
- Decision: `docs/nodejs/design/FLOATY_OVERLAY_PHASE12_DECISION.md`
- Existing floaty design: `docs/nodejs/design/RHINO_FLOATY_COMPATIBILITY.md`
- Host verifier: `tools/nodejs/phase12/verify-rhino-compat-overlay-provider-contract.js`
- Gradle gate: `:app:verifyNodeRhinoCompatOverlayProviderContract`
