# Optional AndroidView interoperability V1

F.2 adds a negotiated, in-process extension. It does not change the frozen V1
loading interfaces, Parcelable encoding, ValueKind, 30-entry component catalog
or contract version. The current release AAR preserves all 197 retained V1 class
files byte-for-byte, appends nine interop classes and removes seven unused P0
negative-version fixture classes. Artifact comparison is recorded in
`p7-interop-aar-compatibility.json`.

The base renderer factory still advertises only its V1 component set. It adds
`android-view-interop-v1` to `FEATURES` and the separate implementation class name
under `androidViewFactoryV1`. These values are plain strings; the base factory,
its private renderer seam and normal V1 path must not link new shared types on
an old host. New hosts explicitly select the extension, load the named class,
require `AndroidViewRendererFactoryV1` and verify `extensionVersion() == 1`.
Malformed advertised extensions fail compatibility checks instead of silently
falling back. The extension remains separate from the future V2 catalog work.

`AndroidViewInteropV1.catalog` explicitly combines V1 with one AndroidView node.
Its required wire property `view` is an integral `UiValue.Num` lease in
`1..Int.MAX_VALUE`. Script inputs are actual Android Views or synchronous
`(Context) -> View` factories; the host prepares them on main and keeps the native
object beside the node. Factories are never sent to the renderer. The getter
returns the prepared View, and the host reuses a retained same-node/same-View
lease across reconciliation and coalesced A -> B -> A mutations.

`AndroidViewRendererV1.applyWithViews(batch, bindings)` receives the complete
target tree's process-local `AndroidViewBindingV1(nodeId, leaseId, view)` list.
Bindings are not Parcelable. The extension previews the entire V1 patch result,
checks exact binding correspondence, native parent and identity constraints,
then changes visible borrowed children and publishes claims/tree as one operation.
Failed validation or replacement preserves the preceding accepted state.
Recover/forceLayout/requestLayout work is completed before publishing the tree.
If a native hook disposes the renderer during that work, closure wins: cleanup
releases touched slots/claims instead of restoring a closed session's old state.

`AndroidViewClaimsV1` lives in the host parent loader, so renderer instances from
different APK loaders share one ownership authority. Main-thread validation and
replacement use weak identity keys and weak owners; the table itself holds no
View/renderer strongly. An accepted View is exclusive to the same renderer,
node and lease until that ownership is released, even before first composition
or while offscreen. Host preflight rejects an incompatible new mount before
closing its previous UI. Release is idempotent.

Compose's AndroidView creates a plugin-owned guarded FrameLayout. The host-owned
View is its borrowed child. Offscreen disposal releases the slot, not the View's
user resources; a later slot can reuse that View under the same accepted binding.
The plugin never steals a View from an arbitrary parent, destroys a WebView or
clears user listeners. Invalid native traversal is reported through the existing
typed renderer error boundary. User-supplied Views retain their original legacy
UI listeners/behavior; those callbacks do not acquire the Compose event-queue
guarantee merely because the View is embedded.

The host's XML `<compose>` container is independent of the optional renderer
extension. `compose.attach` can still show ordinary V1 content with an old
provider. It only accepts the creating runtime's container and returns a normal
session; page, floating and container sessions share the existing eight-session
budget. A successful replacement closes only the matching old container session.
First attachment may follow an initial off-window setup; a later real detach
ends that session. Closing it removes its content, not the outer XML/legacy window.

The public API deliberately keeps direct XML/Compose node mixing invalid. Explicit
containers and AndroidView are the two bridges. Native factories run through a
one-way script-to-main handoff outside composition; errors return to the caller
without the generic UI callback helper terminating the script. A factory, render
callback or same-stack replacement callback cannot open a nested mount.

Official reference for the native holder lifecycle:
[AndroidView API](https://developer.android.com/reference/kotlin/androidx/compose/ui/viewinterop/package-summary#AndroidView(kotlin.Function1,androidx.compose.ui.Modifier,kotlin.Function1,kotlin.Function1,kotlin.Function1)).
Runtime behavior is checked against the pinned Compose UI 1.12.1 artifact and
actual API 24 / API 35 execution, not inferred from a newer library release.
