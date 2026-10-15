# ui-mode layout preview contract V1 / V2

Status: V1, added 2026-10-15 for the 3-Block Compose designer's `"ui"` mode (XML / E4X) projects (plugin
roadmap P10.2, decisions D46 - D50); V2, appended the same day for the interactive preview (plugin roadmap
P10.6, 3-Block Compose 1.7.0): `runScript` / `stopScript` and the `onConsole` / `onScriptError` /
`onScriptExit` callbacks, see "Script runs (V2)". The host owns `plugin-api/ui-preview-api` (package
`org.autojs.plugin.ui.preview`) and provides the service (`UiPreviewService`, action
`org.autojs.plugin.ui.PREVIEW`, permission `org.autojs.permission.PLUGIN`); the 3-Block Compose plugin
packages the module and is the first client.

## Why the host renders

`ui.layout` is `E4X -> XmlConverter -> DynamicLayoutInflater`: about a hundred inflater classes that each hold a
`ScriptRuntime`, evaluate `{{ }}` attributes in the script's scope and need the host's AppCompat / Material
widgets. A copy in the plugin would be large and never exact, so the preview is rendered by the host process
with the real inflater inside a real runtime, and shown in the designer either embedded through
`SurfaceControlViewHost` (API 30+, D46) or as an offscreen snapshot (API 24 - 29, D48). Level 1 of D47 is the
only level in V1: the runtime executes the constant script `"ui";` and every layout is applied from Kotlin, so
no user code runs and no request content ever becomes source text. Level 2 (running the whole script) is the
append-only V2: `runScript` starts a normal host script execution whose `ui.layout` content goes into the same
embedded root (see "Script runs (V2)").

## Binder surface

```aidl
interface IUiPreviewService {
    int getContractVersion();
    String getContractHash();
    Bundle getPreviewInfo();
    Bundle createSession(in Bundle request, IUiPreviewCallback callback);
    Bundle setLayout(String sessionId, in Bundle layout, in @nullable ParcelFileDescriptor output);
    Bundle resize(String sessionId, int widthPx, int heightPx);
    void release(String sessionId);
    Bundle reattach(String sessionId);
    Bundle setPointerMode(String sessionId, int mode);
    Bundle setHighlight(String sessionId, int elementIndex);
    // V2
    Bundle runScript(String sessionId, in Bundle request);
    Bundle stopScript(String sessionId);
}
oneway interface IUiPreviewCallback {
    void onBounds(String sessionId, in Bundle bounds);
    void onLayoutFailed(String sessionId, in Bundle failure);
    void onSessionLost(String sessionId, in Bundle reason);
    void onTap(String sessionId, in Bundle tap);
    // V2
    void onConsole(String sessionId, in Bundle entry);
    void onScriptError(String sessionId, in Bundle error);
    void onScriptExit(String sessionId, in Bundle exit);
}
```

- `UiPreviewContract.HASH` is the SHA-256 of `HASH_INPUT`, which lists both interfaces' transactions in order.
  Transactions are append-only; reordering or changing one requires a new contract version.
- Versioning: `getContractVersion()` and `getContractHash()` keep answering for the frozen V1 base
  (`BASE_VERSION` = 1, `HASH_V1`), so a V1 client (3-Block Compose 1.6.0) works unchanged against a V2 host;
  `createSession` accepts `contractVersion` 1 or 2. A V2 client reads `contractVersion` (2), `contractHash`
  (`HASH`, the V2 digest) and `scriptRunSupported` from `getPreviewInfo()` before it uses the V2 transactions.
- Bundles carry primitives, strings and string arrays, plus the `SurfacePackage` Parcelable, the
  `InputTransferToken` Parcelable and the host token Binder named below.
- Every call checks `org.autojs.permission.PLUGIN` (signature level); the Manifest entry blocks binding for
  everybody else. A session belongs to the calling uid; other uids get `SESSION_NOT_FOUND` for it.
- Callbacks are oneway: the host never waits for the client. Calls into the service run their session work on
  the host's main thread with a bounded wait on the Binder thread (`LAYOUT_TIMEOUT_MS` = 5000 for layouts,
  `SESSION_CREATE_TIMEOUT_MS` = 15000 for `createSession`, which creates a script engine).

## Preview info

`getPreviewInfo()` returns `contractVersion`, `contractHash`, `status` (`ready`), `hostVersionCode`,
`hostVersionName`, `modes` (string array: `embedded` on API 30+, `snapshot` always),
`interactivePointerSupported` (API 33+), `imeSupported` (API 35+), `maxSessionsPerClient` (2), `maxWidthPx` /
`maxHeightPx` (4096), `maxXmlChars` (262144), `maxBindingsChars` (65536), `maxBoundsEntries` (5000),
`maxSnapshotPixels` (16777216) and `layoutTimeoutMs` (5000). V2 adds `scriptRunSupported` (API 33+): whether
`runScript` is available on this device; `contractVersion` / `contractHash` here are the current V2 values.

## Sessions

`createSession` request keys: `contractVersion` (must equal 1), `mode` (`embedded` or `snapshot`), `widthPx` /
`heightPx` (1..4096, the logical screen the layout fills), optional configuration overrides `densityDpi`
(0 or 72..1200), `nightMode` (0 follow host, 1 no, 2 yes), `localeTag` (BCP 47, empty follows the host) and
`fontScale` (0 or 0.5..3.0), `displayId` (default display when absent), `clientVersionCode` (logged) and
`label` (at most 128 chars; shown as the name of the preview runtime in the host's running task list, Q20
option b). The embedded mode also needs `hostToken` (`SurfaceView.getHostToken()`, a Binder) or, on API 35+,
`inputTransferToken` (`SurfaceView.getRootSurfaceControl().getInputTransferToken()`), which is preferred
because only that construction can host the soft keyboard (D50 level 3).

The result carries `sessionId` and, for the embedded mode, `surfacePackage`, which the client hands to
`SurfaceView.setChildSurfacePackage`. The client's `SurfaceView` must sit above its window
(`setZOrderOnTop(true)`), otherwise the system never routes touches to the embedded hierarchy (P10.0); in the
edit pointer mode the host root therefore consumes the pointer itself, reports taps through `onTap` (`x`, `y`
in root pixels, `elementIndex` of the deepest hit or -1, `className`) and draws the highlight requested with
`setHighlight`. `setPointerMode(1)` lets pointer input reach the inflated views and is refused with
`UNSUPPORTED_MODE` below API 33, where popups of an embedded tree crash the host (D50 level 2).

Each session owns one preview runtime: a `ScriptEngineService` execution of the constant script `"ui";` whose
engine runs on the main thread with a `UiContentHost` (the session root) in place of the Activity. The
execution is listed under `label` among the running tasks; stopping it there releases the session and sends
`onSessionLost` with `reason` = `stopped`. The session is also released when the client calls `release`, when
its callback Binder dies, when a layout request times out (`timeout`), when the runtime fails
(`script_error`) or when the service is destroyed (`service_destroyed`).

`resize` relays out the embedded hierarchy (a snapshot session records the size for the next layout).
`reattach` returns a fresh `surfacePackage` for the same hierarchy after the client's surface was destroyed
and created again (the designer goes to the background and back); it is `UNSUPPORTED_MODE` for snapshots.

## Layouts

`setLayout` keys: `xml` (at most 262144 UTF-16 chars; the client has already replaced every E4X `{expr}`),
`bindingsJson` (a JSON object, at most 65536 chars; the `{{ }}` binding context, names resolve on it first and
on the runtime's global scope second) and optional `xmlSha256`. The call is synchronous: the host parses the
JSON into a script object, sets `ui.bindingContext`, invokes `ui.layout(xml)` on the main thread and replaces
the session root's content.

Outcomes: `applied` with `elementCount` (elements of the converted document), `elapsedMs` and
`diagnosticsJson`; `failed` with `errorCode`, `errorMessage` (at most 4096 chars) and a position; `timed_out`
after `layoutTimeoutMs` (the session is released once the main thread returns).

- `XML_INVALID`: `line` / `column` of the parser (1-based, -1 when unknown) and `elementIndex` of the last
  element opened before that position.
- `INFLATE_FAILED`: `elementIndex` of the element being inflated and, when the failure happened while applying
  an attribute, `attrName`. The session survives; the next `setLayout` applies normally.
- `BINDING_FAILED` never fails a frame: a `{{ }}` expression that throws keeps its raw attribute text and the
  failure is appended to `diagnosticsJson` (`code`, `message`, `i`, `a`). A binding that fails outside a
  `setLayout` call (a list item bound later) is delivered through `onLayoutFailed` with the same keys.

Element indices are the pre-order numbers of the elements (only elements, root = 0) of the converted layout
document; the client serializes its own tree in the same order. List and grid item instances inflate the
template element again and share its index, distinguished by the instance number in the bounds.

In the snapshot mode `output` is required: a writable descriptor owned by the client (a file in its cache
directory). After the layout the host measures and lays out the root at the session size, draws it into an
ARGB_8888 bitmap and writes the raw row-major pixels (`snapshotFormat` = `argb8888-rowmajor`,
`snapshotByteLength` = width x height x 4, `snapshotSha256`, `snapshotWidthPx` / `snapshotHeightPx`); the host
closes its copy of the descriptor. Views that need a window (`WebView`, `SurfaceView`, `TextureView`,
`VideoView`) are replaced by a labeled placeholder rectangle before attributes are applied.

## Bounds

After every layout pass of the embedded hierarchy (debounced by 50 ms), and once per snapshot layout, the host
sends `onBounds` with `boundsJson`, `boundsCount`, `boundsTruncated` and `elementCount`:

```json
[{"i":0,"l":0,"t":0,"r":1080,"b":1920,"c":"JsLinearLayout","d":"root","n":0,"s":0},
 {"i":1,"l":24,"t":24,"r":300,"b":80,"c":"JsTextView","d":"title","n":0,"s":0}]
```

`i` element index, `l` / `t` / `r` / `b` pixels in the embedded root (what the client's `SurfaceView` shows one
to one), `c` the view's class name, `d` the `id` attribute without `@+id/` (absent without an id), `n` the
instance (0 for the element's first view, item instances count up), `s` `View.getVisibility()`. Entries are
sorted by `i` then `n`; beyond 5000 entries the list is cut and `boundsTruncated` is true (a oneway callback
has no caller-owned descriptor to fall back to, and 5000 entries stay far below the Binder buffer).

## Script runs (V2)

`runScript(sessionId, request)` runs the whole script (`scriptText`, at most `MAX_SCRIPT_CHARS` = 524288 UTF-16
chars, which must start with the `"ui"` directive; `scriptName`, sanitized like the session label) as a normal
host script execution: it is created through `ScriptEngineService.execute` like any script, so it is listed in
the host's running tasks under `scriptName` and can be stopped there; its `ExecutionConfig.uiContentHost` is a
per-run `UiContentHost` (`UiPreviewScriptRun`) that delegates the root to the session, so the script's
`ui.layout` content replaces whatever the layout preview showed and its `{{ }}` bindings, event handlers,
timers and modules run with the script's real semantics and side effects (files, network, accessibility,
floating windows, all under the host's script permission model). Only embedded sessions on API 33+ accept
runs (`UNSUPPORTED_MODE` otherwise, decision D50: popup windows of the embedded tree crash the host below
API 33); the client sets `setPointerMode(1)` so touches reach the views. The working directory of a run is
`<host cache>/ui-preview/<sessionId>` (`files.*` relative paths resolve there; the directory is removed with
the session). The call returns `outcome` = `applied` and `runId` once the engine started (bounded by
`SCRIPT_START_TIMEOUT_MS` = 15000), `SCRIPT_FAILED` when the engine could not start or the text is not a
`"ui"` script. A second `runScript` on the same session stops the previous run first (`onScriptExit` with
`replaced`).

- `onConsole(sessionId, { runId, level, text })` forwards every entry of the run's console (`console.*`,
  `log`, `print`, the runtime's own warnings and errors); `level` is an `android.util.Log` priority.
- `onScriptError(sessionId, { runId, errorMessage, line })` reports an uncaught error of the run (line is
  -1 when unknown); `onScriptExit` with `error` follows.
- `onScriptExit(sessionId, { runId, reason })` ends every run exactly once: `exit` (the script called
  `exit()` or its runtime ended), `stopped` (`stopScript`, or the host task list), `error`, `replaced`, or
  `released` (the session was released while the script ran).
- `stopScript(sessionId)` stops the running script, if any, and returns `applied` (with the stopped
  `runId`); the layout-preview runtime and the root stay, so the client can send `setLayout` again.
- The layout-preview runtime (`"ui";`) of the session keeps running beside a script run; the session's
  `setLayout` during a run replaces the root content as well, so a client should not interleave them.

## Residual differences from running the script

The preview has no Activity window: `ui.finish`, `keepScreenOn`, `statusBarColor`, `statusBarIconLight`,
`statusBarIconLightBy`, `backgroundColor`, `navigationBarColor`, `navigationBarIconLight` and
`navigationBarIconLightBy` are no-ops that print one warning per function to the runtime console; `ui.root`
is the session root; `ui.emitter` is null. The root carries the script theme's window background; there are
no status / navigation bar insets. Dialogs of the runtime (`dialogs.*`) would open as the host's overlay
windows, which V1 never triggers because no user code runs. The plugin roadmap's section 3.5 lists the full
set for both D47 levels.
