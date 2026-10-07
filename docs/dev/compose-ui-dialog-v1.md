# Optional dialog presentation contract

F.3 adds a separately negotiated `dialog-v1` presentation factory. The contract
version remains 1; no V1 catalog, Parcel field, value kind or F.2 AndroidView
interface changes. All 206 previously retained class files are byte-identical.
The added classes live in `org.autojs.plugin.compose.api.dialog`.

The base factory advertises the feature and `dialogFactoryV1` class-name string.
Only a host that recognizes the feature resolves the optional factory class.
Its `extensionVersion()` must be 1. `create(environment, options)` returns an
`AndroidViewRendererV1`, so dialog content uses the same trees, commands, theme,
native bindings, ownership claims and atomic generation publication as F.2.
No Compose implementation type crosses this shared boundary.

`DialogOptionsV1` contains the presentation type (`alert` or `bottomSheet`), the
cancelable flag, a trusted Android window type and an optional Binder window
token. The host selects APPLICATION for the creating script's Activity, or
PHONE on API 24/25 and APPLICATION_OVERLAY from API 26 for non-UI scripts.
For Activity dialogs the host leaves the explicit token null, letting the bound
Activity Context supply its application token. A decor View window token is not
used as a substitute. Mode and owner come from the engine binding captured before
user code, not from the writable global activity alias.
Script options expose only type, cancelable and the existing theme override.
They cannot supply arbitrary window types or tokens. A missing overlay permission
fails through the existing typed permission path; it does not launch permission UI.

## Window and session ownership

The renderer's view is a non-interactive composition anchor. The actual modal
window belongs to the plugin's Compose Dialog. The host attaches the anchor to
the creating Activity's decor or a dedicated managed overlay with lifecycle and
saved-state owners. The UI path explicitly initializes Activity ViewTree owners
through the public API, including when no page has called setContentView yet. It does not put the dialog in another modal window or replace
the script's current page. Closing the session disposes the dialog, removes the
anchor and releases the package watch, native bindings and script keepalive.

Dismissal uses the existing event sink with the new `dialogDismissRequest` event
name, current generation, NO_NODE_ID and SYSTEM_CALLBACK_ID. It is a session
lifecycle signal, not a node callback. The host reserves a lifecycle delivery
slot and does not discard it after a newer tree generation or ordinary event
queue overflow. It executes close listeners on the owning script dispatcher.
An optional existing CODE/PROP payload denotes fatal asynchronous presentation
failure and is reported before closing. Ordinary apply failures retain their
existing transaction rollback behavior. No script function executes in Compose
composition or in the renderer's event-sink call stack.

The cancelable flag controls back, outside-click and sheet drag dismissal.
Explicit session close remains available when cancelable is false. Initial
sheet Hidden is preparation; it cannot close the session. A completed open
followed by Hidden requests closure at most once. Disposal never creates a new
window or sends a cancellation request. Dialogs share the existing eight-session
per-engine bound with pages, floating windows and XML containers.

The first dialog composition is installed in a posted main-thread initialization
step after attachment. This catches synchronous Dialog.show failures that arise
outside coroutine execution, including a decor attached later by the system.
Uncaught asynchronous composition failures use the coroutine error hook. Pending
initialization is removed on detach/dispose, and a closed anchor cannot reopen.

The anchor's first layout does not establish that the modal content is ready.
Commands that need composed nodes wait for pre-draw in the real dialog window,
and revalidate the target after a newer generation. Native View factories still
run in the host before composition. Native listeners retain their legacy rules.

## Public Material implementation

Pinned Compose UI 1.12.1 exposes `DialogProperties.windowType` and `windowToken`.
The renderer passes both before showing its window. Alert content uses a themed
Material3 Surface. The sheet uses public Material3 1.4.0 BottomSheetScaffold and
SheetState with a modal scrim inside a fullscreen Compose Dialog. It opens fully,
skips partial expansion and blocks invalid partial-state transitions.

This is an explicit adaptation of Material3's public sheet components, not a
claim that the original `ModalBottomSheet` function supports overlay windows.
The locked 1.4.0 function creates a private ComponentDialog before its content
can access DialogWindowProvider; setting its type from that content is too late
for the first show. The inspected 1.5.0-alpha29 source still has no corresponding
window type/token parameter. No dependency upgrade, private reflection, hidden
Android API or copied internal sheet implementation is used.

Public references: [DialogProperties](https://developer.android.com/reference/kotlin/androidx/compose/ui/window/DialogProperties),
[ModalBottomSheetProperties](https://developer.android.com/reference/kotlin/androidx/compose/material3/ModalBottomSheetProperties)
and [BottomSheetScaffold](https://developer.android.google.cn/reference/kotlin/androidx/compose/material3/BottomSheetScaffold.composable).
The acceptance record in `p7-dialog-evidence.md` identifies actual artifacts,
device runs and compatibility limits separately from these API descriptions.
