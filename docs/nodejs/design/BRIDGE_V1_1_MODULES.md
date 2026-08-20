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
- `app`, `app.launch`, `app.settings`, `app.activity`, `app.query`
- `dialogs`
- `engines`, `engines.exec`
- `accessibility`
- `screen_capture`
- `image`
- `ocr`
- `storage`
- `notifications`
- `sensors`
- `ui`
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
- `ocr`
- `storage` / `storages`
- `notifications`
- `sensors`
- `ui`
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
- controlled `fetch` and `websocket` are stable and enabled for live Android
  network I/O, subject to permission and provider policy.
- allowlisted `java_interop` is stable and enabled.
- `worker_threads` is a stable native builtin with a smaller worker authority
  set; it is not an AutoJs bridge module.

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
| `storage` | 5 | 2 | 5 | 2 | 1 | Implemented as v1.1 partial in Task 173. |
| `notifications` post-only | 4 | 3 | 4 | 3 | 3 | Implemented as v1.1 partial in Task 174. |
| `ocr` | 5 | 3 | 4 | 4 | 2 | Protocol MVP added in Task 148; Phase 7 adds a live MLKit provider over scoped provider-owned image handles. |
| `sensors` | 3 | 3 | 4 | 2 | 2 | Implemented as v1.1 partial in Task 175. |
| controlled `http` wrapper | 4 | 4 | 3 | 4 | 4 | Defer until network promotion gates pass. |
| `ui` | 5 | 5 | 3 | 5 | 3 | Protocol gate reserved in Task 182; live Activity provider remains design-gated. |
| `floaty` | 4 | 5 | 2 | 5 | 5 | Design gate recorded in Task 183; no runtime module. |
| `plugins` | 4 | 5 | 3 | 5 | 4 | JS-only local prototype in Task 184; full signed/native/plugin-center model remains gated. |
| Shizuku/root shell | 3 | 5 | 2 | 5 | 5 | Not a v1.1 bridge expansion. |

## Recommended v1.1 Scope

### Tier 0: Finish Existing Provider Gaps

Before adding broad new authority, prefer closing gaps in existing bridge
modules:

- Activity-owned `dialogs` provider with lifecycle and cancellation policy.
- Activity-owned `media_projection` provider for screen-capture permission.
- Android-owned `image` provider for capture/read/save/transforms/template
  matching/color search with opaque handles and cleanup.
- Packaged diagnostics for each bridge module that requires manifest
  permissions or template metadata.

This produces more user value than adding another partially owned module.

### Tier 1: Low-Risk New Modules

#### `storage`

Implemented as the first new v1.1 bridge module in Task 173.

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
bridge shape. The implemented Node surface is `require("storage")` and the
AutoJs-style alias `require("storages")`, both exposing `create(name)` and
`open(name)` store factories. Store handles are frozen JavaScript objects, and
all reads/writes dispatch through the controlled bridge.

#### `notifications` Post-Only

Possible capability:

```text
notifications
```

Initial scope is post/cancel only:

```js
const notifications = require("notifications");
const id = await notifications.notify({ title: "Done", text: "Export finished" });
await notifications.cancel(id);
```

Constraints:

- No notification listener service.
- No raw `Notification`, `PendingIntent`, `RemoteViews`, or channel object.
- Channel ids are provider-owned strings; JavaScript receives only numeric notification ids.
- Android 13+ `POST_NOTIFICATIONS` permission and packaged manifest behavior
  are explicit through the `notifications` capability.
- Owned notifications are registered against the current Node execution and
  canceled on provider destroy.
- No background scheduling authority; use `work_manager` design gates for that.

The implemented module is `require("notifications")` with `notify(options)`,
`cancel(id)`, and `cancelAll()`. It remains post-only: actions, listeners,
custom `PendingIntent`, and raw Android notification objects stay outside the
Safe Node Profile.

#### `sensors`

Possible capability:

```text
sensors
```

Initial scope is read-only and bounded:

```js
const sensors = require("sensors");
const sample = await sensors.once("accelerometer", { timeoutMs: 1000 });
const sub = sensors.subscribe("gyroscope", (event) => console.log(event.values), { intervalMs: 200 });
await sub.ready;
await sub.close();
```

Constraints:

- No continuous unbounded subscriptions.
- Sampling interval, active subscription, and event queue limits are mandatory.
- Provider-owned resource handles must be closed on stop/destroy.
- Results are JSON snapshots.
- Deny high-risk or permissioned sensor classes until separately reviewed.

The implemented module supports only `accelerometer`, `gyroscope`, and `light`.
JavaScript receives frozen JSON event snapshots; raw `SensorManager`, `Sensor`,
`SensorEvent`, listeners, and Android objects remain denied. This is still a
privacy- and battery-sensitive surface, so future sensor types need separate
review.

### Tier 2: Valuable But Dependency-Blocked

#### `ocr`

OCR is high value, but it should not bypass the image bridge or model/asset
policy.

Task 148 added the protocol MVP and Task 172 promotes the JavaScript surface to
v1.1 partial: `require("ocr").recognize(image, options?)`,
`recognizeText(image, options?)`, and `detectTextBounds(image, options?)` are
routed as controlled bridge methods, use existing opaque image handles, require
both `ocr` and `image` capabilities, and have fake-provider, manifest,
conformance, and TypeScript gates. Phase 7 adds the first live Android provider:
MLKit Chinese text recognition over provider-owned image handles created by the
scoped `image.readImage()` path and closed with `image.recycle()`.

Possible capability:

```text
ocr
```

Possible API:

```js
const ocr = require("ocr");
const image = await require("image").readImage("./screen.png");
const results = await ocr.recognize(image, { lang: "auto" });
const text = await ocr.recognizeText(image, { lang: "auto" });
const bounds = await ocr.detectTextBounds(image, { lang: "auto" });
await image.recycle();
```

Constraints:

- Input must be an existing image handle or controlled screen-capture handle.
- Results are frozen JSON snapshots.
- `recognizeText()` returns a single newline-joined string so text-only scripts
  do not need to inspect bounds.
- `detectTextBounds()` keeps the result snapshot shape and is reserved for
  provider implementations that can distinguish detection from extraction.
- OCR provider owns model selection, timeouts, memory budgets, and cleanup.
- Current live OCR uses MLKit Chinese text recognition; Paddle/RapidOCR model
  asset or plugin-provider selection remains diagnostic-only.
- Packaged APK verifier must account for OCR model assets if packaged OCR is in
  scope.
- No raw engine, model path, native pointer, `Bitmap`, `Mat`, or Java object
  crosses into Node.

The remaining work is broader provider ownership: packaged OCR asset policy,
alternate model/provider selection, screenshot/media-projection image sources,
image transforms, and template/color operations. Those remain blocked until the
image provider lifecycle is ready beyond scoped local-file handles.

#### Controlled `http` Wrapper

Do not add a separate Node-style `http` bridge. Native `http` is stable, while
the controlled `fetch`/`axios`/`undici` surface remains the provider-backed
API.

If this is revisited, it should be a thin adapter over the controlled `fetch`
provider, not raw `http`, `https`, `net`, `tls`, `dns`, agents, or sockets.
Axios compatibility must wait for an explicit fetch-adapter corpus and packaged
network opt-in fixture.

### Tier 3: Design-Only Gates

#### `ui`

Node-driven UI needs a separate Activity-owned provider gate before live
Android rendering.

Open questions:

- Which Activity owns the UI?
- How does Node lifecycle bind to Android lifecycle?
- Does packaged APK UI use the same provider?
- How does this coexist with Rhino UI scripts?
- How are UI object handles represented and cleaned up?

Task 149 added that design gate in [UI_BRIDGE.md](UI_BRIDGE.md). Task 182
reserves a protocol-gated `require("ui")` module with JSON-only
`showLayout`, opaque handle `on`/`update`/`close`, fake-provider event
draining, TypeScript declarations, and Android provider fail-closed behavior.
The live Activity-owned provider, lifecycle, packaged APK behavior, Rhino
compatibility boundary, and provider test gate remain unimplemented.

#### `floaty`

Floaty is a UI and overlay-permission feature, not a small bridge addition.

It requires:

- overlay permission policy;
- Activity/service ownership;
- foreground service behavior where needed;
- handle cleanup on script stop;
- user-visible disclosure and packaged manifest policy.

Floaty should wait behind the UI bridge design gate.

Task 183 records the overlay-specific gate in
[FLOATY_NODE.md](FLOATY_NODE.md). The current decision remains no
`require("floaty")` or `require("ui.overlay")` runtime module until overlay
permission flow, foreground disclosure, packaged APK behavior, resource
cleanup, and no-raw-Window/View tests are accepted.

S11-24 records Rhino `floaty` migration details in
[RHINO_FLOATY_COMPATIBILITY.md](RHINO_FLOATY_COMPATIBILITY.md): exact
`floaty.window()` / `floaty.rawWindow()` compatibility is blocked, provider
permission and close semantics remain deferred, and `floaty.getClip()` migrates
to the controlled clipboard surface instead of a hidden overlay window.

#### `plugins`

Plugins can imply downloaded code, Java/Android interop, native code, signatures,
updates, and permission delegation. Task 184 adds only a project-local JS-only
prototype:

- `require("plugins").load(id)`
- `manifest(id)` and `resolve(id)` diagnostics
- package metadata from `./plugins/<id>/package.json`
- `autojs6.plugin.permissions` capability declaration
- exact project opt-in through `plugin.<id>`
- no `.node` native addons
- plugin entry path escape denial

Task 150 added the full design gate in [PLUGIN_SYSTEM.md](PLUGIN_SYSTEM.md).
The current decision remains no installer, signed archive update path, Android
plugin APK adapter, packaged APK plugin runtime, or native plugin model in
v1.1. Native addons stay disabled unless a separate signed-native-provider model
is accepted.

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
2. Keep `storage` as the first implemented new module and use its capability,
   namespace, and JSON-only tests as the template for future low-risk modules.
3. Use post-only `notifications` as the pattern for future user-visible
   status surfaces: explicit capability, Android permission mapping, and
   owned-resource cleanup.
4. Use bounded read-only `sensors` as the pattern for event-style bridge
   modules: provider queues events, JavaScript drains snapshots, and destroy
   closes native listeners.
5. Keep OCR partial: the Phase 7 live MLKit provider may consume scoped
   provider-owned image handles, but packaged model policy, screenshot sources,
   and broader image transforms remain gated.
6. Keep live `ui`, `floaty`, `plugins`, controlled `http`, and Shizuku/root
   advanced shell as design gates only. The current `ui` surface is a
   fail-closed protocol reservation, not a live Android UI provider.

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
