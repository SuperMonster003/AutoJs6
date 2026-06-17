# Node Profile v1.1 Bridge Module Scope

Status: planning gate only. This document ranks possible AutoJs bridge modules
for Node Profile v1.1 and does not implement any new runtime API.

## Goal

v1.1 bridge work should be deliberate and small. Node Profile v1 already has a
wide bridge foundation, but several existing modules still need provider,
packaged, lifecycle, or promotion evidence. New modules should enter v1.1 only
when their Android permission model, packaged APK behavior, fake-provider tests,
resource cleanup, and user-visible contract are clear.

Do not use this plan to enable unrestricted Java/Android interop, raw network,
native addons, child processes, inspector, or broader filesystem access.

## Current Bridge Baseline

Current declared capability names:

- `toast`
- `app`, `app.launch`, `app.settings`, `app.activity`
- `dialogs`
- `engines`, `engines.exec`
- `accessibility`
- `screen_capture`
- `image`
- `clipboard`
- `device`
- `shell`, `shell.root`
- `network`
- `work_manager`
- `java_interop`

Current module registry includes:

- `toast`
- `app`
- `engines`
- `dialogs`
- `accessibility`
- `media_projection`
- `image` / `images`
- `clipboard`
- `device`
- `shell`
- `fetch`
- `websocket`
- `work_manager`
- `java`

Several entries are intentionally conservative:

- `dialogs`, `media_projection`, and `image` have stable JavaScript protocol
  surfaces but still need full Activity/provider ownership before broader use.
- controlled `fetch` and `websocket` remain experimental and default-off for
  live Android network I/O.
- `java_interop` remains experimental and default-off.
- `worker_threads` remains blocked by native availability and is not a bridge
  module users can rely on.

## Scoring Model

Scores use `1` to `5`, where higher is better for value/testability and worse
for risk/impact:

- User value: expected usefulness to Node script authors.
- Security risk: Android authority, privacy, code execution, or persistence
  risk.
- Testability: quality of fake-provider, instrumentation, packaged, and denial
  tests that can be built quickly.
- Packaged impact: manifest, template, encrypted source, ABI, or lifecycle
  work required for packaged APKs.
- Permission impact: Android runtime or manifest permissions required.

## Candidate Ranking

| Candidate | User value | Security risk | Testability | Packaged impact | Permission impact | v1.1 decision |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| `storage` | 5 | 2 | 5 | 2 | 1 | Best first new module candidate. |
| `notifications` post-only | 4 | 3 | 4 | 3 | 3 | Candidate after permission and channel policy. |
| `ocr` | 5 | 3 | 3 | 4 | 2 | Protocol MVP added in Task 148; live provider remains blocked on image/provider ownership. |
| `sensors` | 3 | 3 | 4 | 2 | 2 | Candidate for read-only/rate-limited MVP. |
| controlled `http` wrapper | 4 | 4 | 3 | 4 | 4 | Defer until network promotion gates pass. |
| `ui` | 5 | 5 | 2 | 5 | 3 | Design gate only for v1.1. |
| `floaty` | 4 | 5 | 2 | 5 | 5 | Defer behind UI and overlay policy. |
| `plugins` | 4 | 5 | 2 | 5 | 4 | Design gate only for v1.1. |
| Shizuku/root shell | 3 | 5 | 2 | 5 | 5 | Not a v1.1 bridge expansion. |

## Recommended v1.1 Scope

### Tier 0: Finish Existing Provider Gaps

Before adding broad new authority, prefer closing gaps in existing bridge
modules:

- Activity-owned `dialogs` provider with lifecycle and cancellation policy.
- Activity-owned `media_projection` provider for screen-capture permission.
- Android-owned `image` provider for capture/read/save/template matching with
  opaque handles and cleanup.
- Packaged diagnostics for each bridge module that requires manifest
  permissions or template metadata.

This produces more user value than adding another partially owned module.

### Tier 1: Low-Risk New Modules

#### `storage`

Recommended first new module if v1.1 implements a bridge module.

Initial capability:

```text
storage
```

Possible API:

```js
const storage = require("storage");
const store = storage.open("settings");
await store.put("theme", "dark");
const value = await store.get("theme", "light");
await store.remove("theme");
await store.clear();
```

Constraints:

- JSON-serializable keys and values only.
- Project/package scoped namespace, no raw filesystem path.
- Size limits per value and per store.
- No direct `SharedPreferences`, database, `ContentResolver`, or file handles.
- Packaged apps store data under their own package identity.
- Fake provider tests for get/put/remove/clear, size limits, namespace
  normalization, manifest capability enforcement, and provider destroy.

This module has high utility, no Android permission requirement, and a clear
bridge shape.

#### `notifications` Post-Only

Possible capability:

```text
notifications
```

Initial scope should be post/cancel only:

```js
const notifications = require("notifications");
await notifications.post({ id: "sync", title: "Done", text: "Export finished" });
await notifications.cancel("sync");
```

Constraints:

- No notification listener service.
- No raw `Notification`, `PendingIntent`, `RemoteViews`, or channel object.
- Channel ids are provider-owned and normalized.
- Android 13+ `POST_NOTIFICATIONS` permission and packaged manifest behavior
  must be explicit.
- No background scheduling authority; use `work_manager` design gates for that.

This is useful but should follow a permission review.

#### `sensors`

Possible capability:

```text
sensors
```

Initial scope should be read-only and bounded:

```js
const sensors = require("sensors");
const sample = await sensors.once("accelerometer", { timeoutMs: 1000 });
const sub = await sensors.subscribe("gyroscope", { intervalMs: 200, durationMs: 5000 });
await sub.close();
```

Constraints:

- No continuous unbounded subscriptions.
- Rate limits and duration limits are mandatory.
- Provider-owned resource handles must be closed on stop/destroy.
- Results are JSON snapshots.
- Deny high-risk or permissioned sensor classes until separately reviewed.

This is testable with fake providers but has privacy and battery implications.

### Tier 2: Valuable But Dependency-Blocked

#### `ocr`

OCR is high value, but it should not bypass the image bridge or model/asset
policy.

Task 148 adds the protocol MVP only: `require("ocr").recognize(image, options?)`
is routed as a controlled bridge module, uses existing opaque image handles,
requires both `ocr` and `image` capabilities, has fake-provider and manifest
tests, and rejects through the Android provider until a real OCR provider is
connected.

Possible capability:

```text
ocr
```

Possible API:

```js
const ocr = require("ocr");
const image = await require("image").readImage("./screen.png");
const results = await ocr.recognize(image, { lang: "auto" });
await image.recycle();
```

Constraints:

- Input must be an existing image handle or controlled screen-capture handle.
- Results are frozen JSON snapshots.
- OCR provider owns model selection, timeouts, memory budgets, and cleanup.
- Packaged APK verifier must account for OCR model assets if packaged OCR is in
  scope.
- No raw engine, model path, native pointer, `Bitmap`, `Mat`, or Java object
  crosses into Node.

The remaining work is provider ownership: model packaging, memory budgets,
image-handle lifecycle integration, packaged APK asset policy, and live Android
OCR smoke. Those remain blocked until image/provider ownership is ready enough
to make the handle lifecycle real.

#### Controlled `http` Wrapper

Do not add a separate Node-style `http` bridge in v1.1 while controlled network
is still experimental.

If this is revisited, it should be a thin adapter over the controlled `fetch`
provider, not raw `http`, `https`, `net`, `tls`, `dns`, agents, or sockets.
Axios compatibility must wait for an explicit fetch-adapter corpus and packaged
network opt-in fixture.

### Tier 3: Design-Only Gates

#### `ui`

Node-driven UI needs a separate design gate before implementation.

Open questions:

- Which Activity owns the UI?
- How does Node lifecycle bind to Android lifecycle?
- Does packaged APK UI use the same provider?
- How does this coexist with Rhino UI scripts?
- How are UI object handles represented and cleaned up?

No v1.1 implementation should start until `docs/nodejs/design/UI_BRIDGE.md`
answers these questions.

#### `floaty`

Floaty is a UI and overlay-permission feature, not a small bridge addition.

It requires:

- overlay permission policy;
- Activity/service ownership;
- foreground service behavior where needed;
- handle cleanup on script stop;
- user-visible disclosure and packaged manifest policy.

Floaty should wait behind the UI bridge design gate.

#### `plugins`

Plugins can imply downloaded code, Java/Android interop, native code, signatures,
updates, and permission delegation. This must remain design-only until
`docs/nodejs/design/PLUGIN_SYSTEM.md` defines:

- package format;
- permission declaration;
- native code policy;
- Java interop policy;
- update/signature policy;
- packaged APK policy.

#### Shizuku/Root Advanced Shell

The current `shell` bridge already has explicit `shell.root` capability for root
mode. Shizuku or advanced root/session APIs are not v1.1 bridge expansion
candidates.

They would require:

- separate capabilities, not automatic `shell` inheritance;
- explicit app/user consent model;
- command/session lifetime limits;
- no interactive fd/stream exposure unless a stream bridge is designed;
- packaged APK policy and permission disclosure.

## v1.1 Recommendation

The recommended v1.1 bridge order is:

1. Finish existing `dialogs`, `media_projection`, and `image` provider
   ownership before new high-risk modules.
2. Implement `storage` first if a new module is needed.
3. Consider post-only `notifications` after permission/channel policy.
4. Consider bounded read-only `sensors` after privacy/rate-limit review.
5. Keep the Task 148 OCR protocol MVP fail-closed until image handles are
   provider-owned and packaged model policy is clear.
6. Keep `ui`, `floaty`, `plugins`, controlled `http`, and Shizuku/root advanced
   shell as design gates only.

## Minimum Test Gate For Any New Module

Every new bridge module must include:

- module registry and permission manifest capability coverage;
- fake provider success and failure tests;
- manifest-declared allow/deny tests;
- timeout, cancellation, and service-destroy behavior;
- provider exception conversion to stable AutoJs6 errors;
- resource registry cleanup for every opaque handle;
- bridge limit or rate-limit policy where the module can create resources;
- packaged APK verifier updates for any manifest permission, service, receiver,
  model asset, or template dependency;
- documentation in `COMPATIBILITY_PROFILE.md`, `SECURITY_MODEL.md`, and
  `TESTING.md`.

## Non-Goals

This plan does not authorize:

- unrestricted Android or Java objects in Node;
- raw filesystem or raw network expansion;
- desktop Node compatibility for `http`, `https`, agents, sockets, or workers;
- native addons;
- child process expansion beyond the controlled `shell` bridge;
- inspector/debugger exposure;
- implicit packaged APK permissions.
