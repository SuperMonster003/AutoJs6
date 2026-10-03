# Compose UI contract V1

Status: V1 frozen at P1.1 on 2026-10-02; P1.3 registration and the P2 renderer are
implemented on 2026-10-03. P3.1-P3.5 provide UI and floating-window script entries, lifecycle guards
and localized errors in the matching local host preview.
P4 adds packaged examples, verified host accessibility selectors and catalog/documentation guards.
The host owns `plugin-api/compose-ui-api`; the plugin consumes
its release AAR as compileOnly. The contract depends on Android, Kotlin/JDK and common-plugin-api,
and contains no Compose implementation dependency.

## Frozen surface and compatibility

- `ComposeUiIds`, `ComposeUiContract`, `ComposeUiCapabilityKeys`, `ComposeUiErrorCodes`,
  `ComposeUiLimits` and `ComposeUiVocabulary.kt` centralize names and limits.
- `loading` contains the factory, renderer, host environment and queued event sink interfaces.
- `model` contains immutable values, nodes/subtrees, patches/batches, events, commands and theme data.
- `catalog` contains the V1 component/property/slot/event/command/modifier descriptors and validation.
- `src/test/resources/compose-ui-v1.snapshot` is the reviewed compatibility snapshot: public literals,
  enum names, loading method signatures, model constructors/getters, and all catalog records.
  A change to these semantics requires an explicit version decision and a reviewed snapshot update.
- `CONTRACT_VERSION=1`, `MIN_SUPPORTED=1`. `REQUIRED_HOST_VERSION_CODE=5316` is confirmed at P1.3 (AutoJs6 6.8.0). This build includes
  the V1 loader, session core, shared dependencies and Plugin Center registration. Only this deployment
  metadata changes in the frozen snapshot; the V1 wire API remains unchanged.
- `api.spike` (version -1) is outside the frozen V1 surface. P1.2 removes all implementation/test
  consumers; its unused definitions remain in the unchanged staged AAR until artifact maintenance.
  Its former diagnostics are not V1 loading methods.

The host now packages the V1 API module in app/inrt. The small legacy fixture types travel in the
same AAR during this transition. The host now packages ComposeUiPluginHost/Loader, ComposeSession,
TreeReconciler, CallbackRegistry, ScriptUiDispatcher, resource/window owners and ComposeThemeBridge.
No temporary Android component or Compose implementation is added to the host APK.

## Loading and thread ownership

The renderer factory is named by application metadata `org.autojs.plugin.compose.RENDERER_FACTORY`
and has a public no-argument constructor. Discovery uses INFO category `compose-ui`. The host loads
the plugin with a parent-first PathClassLoader and an APK native search path for the current process
ABI. Shared Kotlin/AndroidX/API types come from the host; Compose implementation classes come from
the plugin. The existing shared lock fingerprint is unchanged.

Factory `contractVersion()` and capabilities must agree. The capabilities Bundle has:

| Key | Type | Meaning |
| --- | --- | --- |
| contractVersion | Int | Supported renderer contract version |
| components | ArrayList<String> | Implemented subset of ComponentCatalog.V1 names |
| features | ArrayList<String> | Optional feature identifiers; unknown optional entries can be ignored |
| composeVersion | String | Renderer implementation version |
| sharedDepsFingerprint | String | SHA-256 of the coordinate-sorted shared dependency table |

The factory returns a fresh capabilities Bundle and creates a renderer on the main thread. Every
renderer method is main-thread confined. `view()` creates/returns its owned View lazily; `dispose()`
is idempotent. The host environment supplies Context, a FIFO main-thread executor, event sink,
initial ThemeSpec and a positive session ID. The context retains host window/services/theme and
delegates plugin resources/assets/classloader with the host Configuration.

`eventSink.enqueue` is nonblocking. It never executes a script callback during composition, layout,
measurement or drawing. The host's script scheduler performs dispatch and rejects stale generation,
closed session and unregistered callback events. No bidirectional synchronous wait is permitted.

## Data and ownership

Model lists, maps and sets are defensive, unmodifiable copies. Their elements are immutable contract
values. Constructors check structural limits and finite numbers; the catalog checks component-level
rules. Whole-scene and patch transaction validation belongs to the host/store. Value equality is
structural, including modifier/child order; diagnostic toString does not expose UI text.

UiNode is a flat record with positive nodeId, type, nullable key, props, ordered modifier operations,
child IDs, named slot root IDs and event-name-to-callback-ID bindings. Node IDs are host allocated,
session-local and never reused for another script node. Unmounted script handles do not cross this
boundary. Named slots each reference one node; a layout node groups multiple slot children.

UiTree supplies rootId plus the complete node list for SetRoot, Insert and ReplaceSlot. It rejects
missing references, duplicate IDs, cycles, multiply owned children, unreachable nodes, duplicate
sibling keys, excess depth and excess unique callback IDs. Slot subtrees participate in depth and
ownership checks. Session-wide budgets remain enforced after the complete patch transaction.

BitmapRef is the ownership exception: it borrows a script/host-owned Bitmap through a weak handle.
It does not copy or recycle pixels. `get()` returns null if the owner was collected or recycled.
Scripts/host resource resolvers must retain their own strong owner while the image is needed;
renderers must not treat the reference as ownership. File paths and host drawables are resolved by
the host before becoming a BitmapRef; the renderer receives no URL/file decoding authority.

## Patch semantics

UiPatchBatch carries positive sessionId, nonnegative Long generation and an ordered patch list.
The renderer is bound to one session. Successful batches advance the accepted generation in order;
the first may use generation 0. Stale/mismatched batches fail with INVALID_ARGUMENT. Validate the
entire result before publishing it; failure preserves the previous tree and generation.

| Operation | Semantics |
| --- | --- |
| SetProps | Replace the complete props map. Null modifier/callbacks keep existing values; empty collections clear them. The host merges script-level partial property writes before producing this patch |
| Insert | Insert a closed subtree into parentId's primary children at index. New IDs must not collide with the live scene |
| Remove | Remove the indicated node/subtree from its parent, releasing corresponding renderer state |
| Move | Move an existing node into destination parentId's primary children; toIndex is measured after removal from its current location. Reject cycles and invalid destinations |
| ReplaceSlot | Replace a named slot with a closed subtree, or remove the slot when subtree is null |
| SetRoot | Replace the scene with a closed UiTree |

Callback bindings are committed with their corresponding patch batch; script functions never cross
the API. Modifier clickable callbacks are numeric IDs in the declared callback argument. The host
callback registry also accounts for command callbacks, not only node/tree callback references.

## Events and commands

UiEvent contains sessionId, Long generation, nodeId, event type, callbackId and a Bundle payload.
`NO_NODE_ID=-1` represents a session event; callback 0 is reserved for system events. Payloads are
copied on construction and on every getter. Allowed values are null, String, Boolean, Int, Long and
Double; Float is normalized to Double. Arrays, nested Bundles, binders and arbitrary Parcelables are
rejected. Component-specific payload fields are declared in EventSpec; system error events carry
code/message and optional prop. Payloads and UI text must not be logged.

Focus and Blur address a node. ScrollTo has an optional lazy-item index and optional pixel offset;
without index it requires a nonnegative absolute pixel offset. An indexed lazy offset may be negative.
Edit contains optional text/selection plus the last observed Long editSeq. Selection offsets are
UTF-16 and may be reversed. The renderer checks them against the current/new text and rejects stale
editing sequences. TextField text is an EDIT_COMMAND property: changes to an existing field must
be routed through Edit rather than resetting native IME state with SetProps.

ShowSnackbar carries message, optional action label, SHORT/LONG/INDEFINITE duration and optional
callback ID. Action/dismiss events distinguish the result. It is a session command; Snackbar's
catalog entry is COMMAND, not a mountable node. Commands execute in the same ordered main-thread
stream as patches and capture the accepted generation for subsequent events.

## Catalog V1

There are 30 entries: 29 node components plus command-only Snackbar. The roadmap's earlier list had
a count typo for selection/input, and TopAppBar appeared only in P2.5; both are reconciled here.

| Group | Names |
| --- | --- |
| Layout | Column, Row, Box, Spacer, LazyColumn, LazyRow, Scaffold |
| Containers/top bar | Surface, Card, HorizontalDivider, TopAppBar |
| Text/images | Text, Icon, Image |
| Buttons | Button, ElevatedButton, FilledTonalButton, OutlinedButton, TextButton, IconButton |
| Selection/input | Switch, Checkbox, RadioButton, Slider, TextField, OutlinedTextField |
| Progress | CircularProgressIndicator, LinearProgressIndicator |
| Dialog/notification | AlertDialog, Snackbar |

The authoritative property names, types, defaults, aliases, slots, payload fields and supported
commands are V1Catalog.kt and the reviewed snapshot. Unspecified defaults use the renderer's Material
theme/component defaults. UiValue.Null is an explicit null, used for indeterminate progress and
inherited optional colors. Primary children and a named content slot are mutually exclusive.
Surface and Scaffold accept at most one primary root; use Column/Row/Box for multiple children.
AlertDialog requires its confirm slot. Image.src accepts a resolved BitmapRef, Icon.name an IconName.

The 20 ordered modifier words are padding, size, width, height, fillMaxWidth, fillMaxHeight,
fillMaxSize, weight, align, background, border, clip, alpha, clickable, verticalScroll,
horizontalScroll, offset, aspectRatio, testTag and semantics. Weight requires Column/Row as the
immediate parent; align requires Box. The transmitted ScopeKind must match the descriptor.
Padding accepts one, two (horizontal/vertical) or four (start/top/end/bottom) dp arguments.
Numeric dimensions use Dp/Sp; the script bridge performs unit conversion before transport.

Shortcut prefix order is fixed: w, h, padding, bg, weight, alpha, testTag, contentDescription,
followed by explicit modifier operations in their original order. normalizeProperties canonicalizes
layout aliases, rejects conflicting aliases, extracts shortcuts and produces that prefix. V1 UiNode
props contain canonical component properties, not script aliases/shortcut keys. Callback/key/ref
extraction occurs before this normalization. TextField value aliases text; RadioButton selected
aliases checked. ThemeSpec permits the 48 color roles declared by ComposeUiThemeColors.

## Limits, errors and Parcel transport

The roadmap limits are unchanged: 5000 nodes/session, depth 64, 2000 patches/batch, 65536 UTF-16
characters/string, 2000 primary children/node (10000 for lazy nodes), 64 modifier ops, 4096 unique
callbacks/session, 8 sessions/engine, 32,000,000 bitmap pixels, and event queue capacity 1024.
The total node limit still applies to lazy trees. Supplementary bounds are value depth 64,
2000 items/value list, 128 properties/callback bindings per node, 32 slots, 32 event fields,
64 theme color entries, and 8 MiB per serialized Parcel frame. The byte bound does not limit an
ordinary in-process call that performs no serialization.

All 18 error-code literals are in ComposeUiErrorCodes and the snapshot. Structural/type errors
use INVALID_ARGUMENT; unsupported component/prop/modifier and scope errors retain distinct codes;
overflow uses LIMIT_EXCEEDED. ComposeUiContractException exposes code, optional nodeId/prop, and
retryable=true only for the four plugin-selection failures. Error translation and the script-side
ComposeError class are host work in later stages.

Each Parcelable has a public CREATOR and a framed encoding: magic 0x43554931, wire version 1,
body byte count, then an explicit type tag and fields. Counts, nesting, frame/string boundaries,
Boolean tags and duplicate map keys are checked before allocating collections. Maps are emitted in
key order; lists retain order. Unknown versions fail as PLUGIN_INCOMPATIBLE. On a write failure,
discard the partially written Parcel. Parcel is transport, not a durable storage format.

BitmapRef parcels encode a process nonce and weak handle, not pixel data. Same-process round trips
preserve object identity and ownership; a foreign process nonce or dead owner is rejected. The
future process-external fallback must define image transfer/resolution before it can transport
BitmapRef. It cannot silently turn a borrowed bitmap into an owned pixel copy.

## Validation and next stages

Contract JVM tests cover the compatibility snapshot, catalog coherence, normalization/scope rules,
deep immutability, tree/key/identifier/limit validation and host/contract source imports. Device
tests cover every value/patch/command kind, fixed wire tags, nested Parcel round trips, detached
event Bundles, bitmap ownership, malformed counts/frames and the byte budget.

P1.2 implements production selection/loading, transactions, callback registry and scheduler policy.
P1.3 registers the plugin and establishes the actual minimum host build. P2 migrates the prototype
renderer from api.spike to loading/model V1 and advertises only implemented catalog entries. This
freeze is not evidence that all 30 entries are rendered or that the compose script global exists.

## Historical P1.2 implementation boundary

The host rechecks installation, Android enablement, Plugin Center enablement, authorization,
minimum host version and INFO/factory metadata before loading code. Contract version precedes
capability fingerprint negotiation. Failure codes are stable; selection messages are localized in
all host languages. The process code cache is keyed by package, version, APK path and update time.
Package install/update/removal broadcasts invalidate code and owner caches. System/component
enablement and host enable/trust changes invalidate selection;
a still-identical authorized APK reuses its process loader so JNI ownership stays consistent.
At P1.2 an existing session retained its renderer until close. P5.1 adds live package-identity
subscriptions: replacement/removal closes affected sessions with PLUGIN_UNAVAILABLE, while
enable/trust changes retain their specific gate errors. Queries snapshot subscriptions and code
keys before consulting PackageManager, so delayed results cannot invalidate newly observed epochs.

Each script engine owns a ComposeSessionScope (at most 8 sessions). Session mutations stay on a
ScriptUiDispatcher; UI mode dispatches mutations directly on main and always queues events/tick
flushes. Non-UI mode captures ScriptAsyncDispatcher on the originating script thread. The session
keeps one in-flight main-thread transaction and one pending immutable target. Multiple renders in
one scheduler turn coalesce, success advances generation and callback bindings together, and a
failed transaction discards dependent pending targets while preserving the last acknowledged tree.
Closing immediately fences event ingress, clears script references and queued work, and schedules
main-thread detach/dispose without waiting across threads. Engine exit wiring belongs to P3/P4.

TreeReconciler scopes keys to a parent and matches unkeyed nodes by same-type index. It preserves
IDs across moves/compatible slot edits, allocates monotonically, and warns once per session for an
unkeyed list. Props are complete replacements. Callback IDs only resolve for the current generation,
node and event binding. The bounded event queue drops the oldest event on overflow and reports a
warning containing only session metadata. Unsupported component types are rejected before dispatch.

The plugin entry now implements V1. Its preview supports Column content, Text.text, Button.enabled
and click, primary/content-slot children, and padding/fillMaxWidth/testTag/semantics modifiers.
The component list contains only Column/Text/Button and FEATURES is empty. Other properties,
modifiers and commands return typed errors; they are not silently ignored. Default theme handling
uses the host seed as primary and the host night flag. Full Material tonal palettes, typography,
dynamic colors, all catalog properties and remaining components are P2 work. The Compose version
capability is generated from the BOM-resolved runtime, not a separate source-code version literal.

NodeStore validates a private transaction workspace, graph closure, catalog/scope constraints and
preview support before publishing an immutable frame. Ancestry cycles are rejected before mutation;
intermediate working node count is bounded as well as final tree size. A failed batch retains both
tree and generation. Explicit disposal releases composition, recomposer, coroutine scope and tree.
Host adapter disposal additionally releases Activity, mount closure and lifecycle/theme observers.

## P1.3 Plugin Center registration

Compose UI is an optional entry in the install wizard's UI category, alongside ImGui. Both are
INFO-only plugins: neither is assigned a Binder capability service action. Compose additionally
uses the application-level renderer factory metadata and the in-process V1 loading API; ImGui's
renderer registration mechanism is not reused. Generic INFO package visibility already covers
discovery, signer authorization, enablement and requiresHostVersion presentation.

The confirmed minimum host is 6.8.0 / 5316. The wizard catalog pre-registers the official package,
but its existing loader skips entries absent from the remote index. Local integration does not
publish a download or enable the future compose script global; that API remains P3 work. The
P1.3 changelog therefore describes installed-plugin management and the development preview.

## Current P2 renderer boundary

The dispatch table now contains all 29 V1 node types, and the capability list also includes the
command-only Snackbar entry (30 total). All catalog properties and slots are mapped.
Text inherits the surrounding Material text style when no style is
provided; explicit style/size/weight overrides merge normally. Icons use an explicit 280-name table
from the pinned core artifact: 49 glyph names in 5 styles plus 35 auto-mirrored variants. Plain
Home means Filled.Home; Outlined.Home and AutoMirrored.Filled.ArrowBack are examples of qualified
names. Names are case-insensitive. No extended icon or XML Material Components dependency is added.

The full 20-operation Modifier chain is interpreted in order with catalog defaults and scope checks.
Each clickable uses its own enabled flag and the component's enabled property; a disabled inner
clickable does not disable an outer hit region. Native controls retain an explicitly supplied node
click callback; without one an enabled modifier supplies native activation. Modifier operations are
keyed by name and occurrence around the whole composable loop item, preserving scroll state across
unrelated styling changes. A plain ScrollTo offset addresses the outermost scroll modifier on ordinary
nodes; LazyColumn/LazyRow use their native list state for indexed and absolute-pixel scrolling.
Lazy keys use the child key, falling back to nodeId; item type supplies the reuse contentType.
Focus observation is independent of command capability.
Command membership is checked against the accepted tree before scheduling and again before execution,
so removing a node rejects queued commands even before Compose disposes its old handle.

Controlled Switch/Checkbox/RadioButton/Slider values remain owned by host props. Events are proposals;
without a host patch the rendered value does not change. The queued event carries the generation of
the frame that produced it. NodeId/prop details on renderer error events now survive host dispatch.
Slider limits also bound its derived tick collection to MAX_VALUE_ITEMS (steps + 2 <= 2000), and
ranges must remain distinct and finite after conversion to Compose floats; violations fail before
composition with LIMIT_EXCEEDED or INVALID_ARGUMENT, preserving the previous frame.

Theme mapping covers all 48 V1 color overrides, system/explicit night mode, Android 12+ dynamic
colors, the 15 public typography roles, font families and font scale. A seed uses an independent
CIELAB/LCh tonal palette with gamut reduction, not Google's HCT algorithm. Explicit colors win over
the selected palette. Dynamic colors fall back to the seed on API24-30. Missing font scale retains
the host/system density object; an explicit scale overrides only fontScale, preserving density.

Borrowed bitmap painters resolve the weak handle on every draw, never cache or recycle the Bitmap,
and draw blank after expiration/recycle. A container around ComposeView contains runtime/linkage
failures during Android measure/layout/draw, reports once per failed traversal and allows retry on
a later frame. Fatal VM errors are not masked. The renderer releases composition, command handles,
recomposer, jobs and snapshots on close. Initial empty batches and generation 0 are valid; replay is
rejected after the first accepted batch. Unknown names have a debug-only diagnostic placeholder;
release rejects them, and known unimplemented components remain absent from capabilities.

TextField/OutlinedTextField own TextFieldState outside composition, retaining edits while a lazy
item is offscreen. Only removal or a component type change releases the editor. Initial editSeq is
0; text, UTF-16 selection and composition changes advance it. Value-change notifications coalesce
per display frame and use the latest accepted callback/generation. Edit synchronously observes
native state before comparing the supplied sequence: both older and future values fail with
INVALID_ARGUMENT and prop=editSeq. Failed edits leave text and selection intact. Existing-field
text declarations cannot change through tree patches; programmatic writes use Edit. A text-only
replacement moves the caret to the new end, while selection-only edits preserve the text.
Read-only fields still allow selection; disabled/stale input connections cannot mutate an editor.
Native user input obeys the same 65536 UTF-16 limit and emits a typed error after rejecting overflow.
Password visual transformation masks display and input semantics and suppresses copy/cut while
preserving readOnly and multiline support; it is always hidden, without last-character reveal.

Scaffold owns only its measured topBar/content spacing. TopAppBar and Scaffold use zero system
insets. For ScriptExecuteActivity, the host's ComposeActivityInsets lease selects adjustResize and
applies system-bar padding with bottom=max(systemBars.bottom, ime.bottom); it consumes those insets.
The renderer adds neither systemBarsPadding nor imePadding. Detach restores the prior soft-input
mode and legacy system-bar-only padding. Other embedders must supply the same single-owner policy;
floaty-specific keyboard behavior was validated in P3.4 with explicit window focus. This follows Android's documented
[inset-consumption rules](https://developer.android.com/develop/ui/compose/system/insets-ui).

Session ShowSnackbar chooses the first Scaffold in stable preorder (primary children first, then
slots sorted by name). A custom snackbarHost replaces that native host and makes ShowSnackbar fail
with INVALID_ARGUMENT rather than silently targeting another Scaffold. Without any Scaffold it also
fails. A bounded FIFO holds at most 1024 active/queued requests, using the shared event budget.
Each accepted request produces one ACTION or DISMISS at NO_NODE_ID with its execution generation;
removing/replacing the selected host dismisses pending requests, and session close cancels silently.
The host reserves one-shot command callbacks separately from node bindings, within the same 4096
callback limit, so unrelated tree commits do not discard command results. The terminal result releases
its command reference before invocation. Submission failure, event eviction and close also release
references; evicted results are not invoked. A callback ID already used for a command cannot be reused
for another command. Node event generations remain strict. AlertDialog.open is controlled by tree
props; dismissal emits a request without closing the dialog until a patch accepts it.

FEATURES remains empty; the component table is the authority for renderer availability. The matching
host now provides the UI script API below. Full accessibility fleet coverage, floaty/inrt execution,
long-running memory checks and performance thresholds remain later roadmap work.

## P3.1/P3.2/P3.3 local script implementation

The host installs `compose` and `$compose` as the same callable object before plugin auto-mount.
Calling it is an alias for `compose.mount`. `isAvailable()` and read-only `version` probe the existing
installation, enablement, trust and version gates; version is `{plugin, contract, compose}` or null.
The 29 element factory names are derived from V1 NODE entries. Snackbar stays a session command,
not an element factory. `createElement(type, props?, ...children)` shares the same conversion path.

Element props extract key/ref, child/slot nodes, onX callbacks and an immutable modifier chain before
catalog conversion. Numbers default to dp for dimension properties, explicit dp/sp strings must
match the catalog type, and colors use the host colors module. Text styles, shapes, icons and theme
values are checked before publication. ImageWrapper, decoded local files and host @drawable/name
resources retain a host owner while the weak BitmapRef is in use; Bitmap values returned by src
getters can be reassigned. URL loading is absent. None of these Rhino values cross into the APK.

`ComposeNode` supports read-only type/key/nodeId/parent/children, validated property/shortcut
accessors, set/get, append/insert/remove/replace/clear, slot, on/off and focus/blur/scrollTo/edit.
Unmounted nodes report nodeId=-1 while keeping an internal positive identity for future publication.
Child arrays are snapshots. Mutations validate the candidate connected tree before publishing, with
atomic rollback for cycles, duplicate keys, wrong scopes, bad values or capacity failures. A closed
session's retained handles report SESSION_CLOSED; commands on detached nodes report NODE_DETACHED.
Ownership cannot move between sessions. Unsupported property writes cannot create stray JS fields.

One engine owns a graph; each mount publishes stable IDs through ComposeSession.submitStableTree.
Accepted/rejected/superseded receipts run on the script dispatcher exactly once, including a no-op.
Callbacks are immutable versioned local tokens mapped to live core IDs, pruned on supersession and
acknowledgement. Script functions, close listeners and command callbacks share the bounded budget.
When an earlier listener closes the session, subsequent listeners from the same input stop running.
Cross-parent/slot transfers use an atomic SetRoot fallback where incremental insert ordering would
otherwise collide; ordinary same-parent reorders still produce Move.

`state(initial)` exposes value, tracks reads during render and queues one render per scheduler tick.
`batch(fn)` coalesces state and handle writes; it does not roll back state values when user code throws.
A successful render prunes obsolete dependencies; a failed render retains the old view and enough
old/attempted dependencies to retry when its inputs change. Actual script exceptions become
RENDER_FAILED with their cause/stack. Structural INVALID_ARGUMENT, DUPLICATE_KEY and other typed
validation errors retain their codes. Rendering never uses the generic helper that terminates UI
scripts on a callback exception. Asynchronous errors are reported through the script console.

Fresh render nodes match old handle objects by parent/key/type, or type plus same-type position when
unkeyed. Explicit nodes created outside that render keep their identity. Missing keys warn once per
session. `ref().current` is published only after an accepted frame and cleared on removal/close;
failed frames restore the accepted graph and refs. A rejected initial frame remains connected so a
subsequent valid update can recover. Native TextField events maintain the handle's current text,
selection and editSeq; pending edits coalesce to the complete final text/selection before dispatch.
An accepted field removal ends its native editing lifetime. The local editor then returns to that
lifetime's wire text declaration, end caret and sequence 0; reinsertion starts the corresponding new
lifetime. A coalesced remove/readd that never leaves the accepted tree preserves editing state.
One bounded deferred edit carries pre-mount selection or a detached local edit into insertion, while
retaining its original sequence. Old captured writes are not refreshed to bypass native arbitration.

Callback arguments are checkedChange(checked), slider valueChange(value), field
valueChange(text, {start,end}, editSeq), focusChange(focused), and scroll(firstVisibleIndex|null,
pixelOffset). Click, longClick, valueChangeFinished and dismissRequest have no positional payload.
Callbacks use their node as this. All run on the originating script dispatcher after renderer enqueue.

`mount(nodeOrRender, {theme?})` requires a real UI ScriptExecuteActivity and returns
root/update/post/showSnackbar/close/isClosed/on('close'). P5.1 preserves the normal UI content
replacement notification and runtime.ui.view assignment, then calls Activity.setContentView on
the already validated main thread. An attach failure uses the session's fatal control channel
and RENDERER_FAILED, without the generic UI helper terminating the entire engine first.
Repeated mount closes the prior UI session. ui.layout/layoutFile/setContentView replacing its view
closes it and warns; native Activity events retain the existing ui emitter. Activity destroy and
engine exit close sessions, compositions, callback references, queued work and image owners.
The existing Manifest handles orientation/size configuration changes without destroying the
engine; a real Activity recreation still destroys it. A render callback cannot call mount/floaty
recursively. Each plugin ComposeView gets a distinct public LifecycleOwner/SavedStateRegistryOwner
identity delegating to the original host Lifecycle/SavedStateRegistry, with ViewModel ownership
still inherited. This bounds Compose1.12 view-tree caches to that renderer across APK replacement.
The P2.4 single host inset owner remains in use. Lifecycle validation includes 20 real mount/close
cycles; this does not replace the later long-running heap/memory matrix.

`post(fn, delay?)` marshals worker updates without waiting on the UI thread; delay is a nonnegative
integer number of milliseconds. Session post cancels when that session closes, and engine exit
cancels module posts. Mutating state or handles from another thread requires post. The module's idle
dispatcher does not keep a non-UI script alive merely because the global object was installed.
`theme(spec?)` reads/sets the default and updates the current session; partial overrides preserve
live host seed/night/font defaults. Reads return independent objects. `sessions` is a read-only
array snapshot. Global ComposeError and compose.ComposeError share a per-scope Error constructor
with code/message/nodeId/prop/cause, stack and plugin-selection retryability.

## P3.4/P3.5 floating sessions and lifecycle guards

`compose.floaty(nodeOrRender, options?)` is available in UI and non-UI scripts. It shares the same
node/state/render pipeline and returns a ComposeFloatyWindow with `.session`, session forwarding
methods, and native window controls. The default is a resizable window; `raw:true` selects RawWindow.
`x/y`, `width/height`, `touchable`, `focusable` and `theme` are accepted. Geometry uses integer physical
pixels, matching legacy floaty, while node dimensions retain their catalog dp/sp units. Size also
accepts MATCH_PARENT (-1) and WRAP_CONTENT (-2). Missing geometry preserves native defaults; touchable
defaults true and focusable false. Unknown options and invalid types/ranges fail before native work.

The facade supports setPosition, setSize, getX/getY/getWidth/getHeight, requestFocus, disableFocus,
setTouchable and close. setAdjustEnabled controls the resizable chrome and returns INVALID_ARGUMENT
for a raw window. Geometry getters return native snapshots (-1 coordinates / 0 measured size before
creation); queued settings coalesce until the service is ready. Changing focus/touchability does not
reset a position or size subsequently changed by the user's native drag/resize interaction.

Overlay permission uses existing floaty.ensurePermission semantics and returns PERMISSION_REQUIRED
without opening a permission dialog. Availability/version probes still describe plugin availability,
independent of the additional permission needed for floating windows. Creation/WM failures are
reported on the script dispatcher and close the failed session. A bounded service-start deadline
prevents pending creation from holding a script alive indefinitely.

The Android adapter uses existing FloatyService, RawWindow/BaseResizableFloatyWindow and the owning
runtime's floaty registry. It does not use their blocking creation wait. Lifecycle and saved-state
owners are installed before WindowManager attachment. Focus/blur/scroll commands wait for the next
post-apply pre-draw, when native command handles exist; the queue is bounded and cancelled on close.
Native editing and Snackbar commands continue to use accepted data directly.

Provisional registry ownership exists before deferred attachment. Immediate floaty.closeAll, a native
close button, service teardown or script exit therefore closes a window even during service startup.
Cancelled managed windows cannot be resurrected by a stale service-creation snapshot. The existing
CopyOnWriteArraySet registry no longer holds a monitor while a script waits for main during legacy
closeAll; only snapshot members are removed, preserving concurrent later additions.

Each floating session owns one idempotent script-looper wait token, acquired on the script thread.
It keeps an otherwise idle non-UI script alive while the session exists and releases on close or
failed creation, including cancellation before the script cleanup queue runs. Native callbacks never
invoke JS on Android main for a non-UI script. Mutations and events return to the captured script
dispatcher; workers update through compose.post. Closing the last window lets an otherwise idle
script finish. Engine exit removes native windows immediately and clears queued work/refs/callbacks.
An explicit Timer rejection from a quitting script looper cancels late posts without escaping onto
Android main. Queue overflow and unrelated scheduling failures still propagate. If force-stop drops
the first script cleanup dispatch, the owner-thread exit hook retries that cleanup; callback receipts,
close notification, native disposal and wait-token release remain exactly once.
Native events, commit receipts and close notifications use reserved, bounded control channels,
separate from the ordinary script post queue. A full ordinary queue therefore cannot throw through
a native window callback or permanently disarm event delivery. Immediate user posts fail with
LIMIT_EXCEEDED at capacity; delayed posts report that code and cancel that callback if their deadline
finds the queue full. A rejected reactive/command post resets its scheduling flag so a later update
can recover. An unrecoverable renderer-open failure reports its error and closes the failed session;
ordinary rejected tree patches retain the existing recovery behavior.
Control slots are reserved during session construction. If that reservation fails, the already-created
provisional window and renderer are both disposed and the wait token is released. Rapid same-turn
create/close cycles may reach the pending-cleanup budget; yielding to the script dispatcher releases
those reservations. A failed reservation never leaves an invisible floaty registry entry.

The eight-session limit counts UI and floating sessions together. Replacing a UI session at the limit
preserves unrelated floaties and validates the replacement before closing the old UI. ui.layout only
replaces the UI mount. Closed root/ref getters become null immediately, and closing one binding cannot
clear a ref currently owned by another binding. Other retained-handle/window mutations report
SESSION_CLOSED. A floating mount inside a rolled-back transaction never attaches later or retains an
invisible reaction. Multi-session node transactions retain their existing atomic-rejection policy;
independent sessions can be updated separately or react to shared state.

All 18 error codes have host-localized messages. Existing Plugin Center selection messages remain
verbatim; script-domain errors retain component/property/node details. The per-realm ComposeError
constructor preserves nested causes, JS error identity and source stacks, supports cyclic causes
without recursive conversion, and passes engine interruption through. Strings are provided for all
10 host languages (11 resource directories), with completeness/sort tests.

Focus validation uses actual attached editor, window focus and InputMethodManager readiness before
requesting the keyboard. API24, Sony API31 and HyperOS API35 exercise real IME windows and native
InputConnection edits. HyperOS validation uses the desktop hosting surface and requestFocus; this
does not claim unrestricted overlay behavior above other apps on that ROM.

This remains a local development preview under D7. Use the matching host source/build containing
the script entry; the renderer's unchanged 5316 contract floor does not distinguish older unpublished
builds with the same number. Complete declarations/user docs and the remaining
compatibility/performance gates continue in their existing roadmap stages.

## P4 examples, accessibility and catalog guards

The plugin packages five indexed scripts in `assets/examples/`: counter (state/render), form
(retained handles, native input, Switch/Slider and validation), list (1000 stable keys and scrollTo),
floaty-hud (non-UI worker updates and owned cleanup), and theme (seed/dark/dynamic colors).
`.python/sync_examples.py --host <checkout> [--check]` maintains byte-identical host copies in
`app/src/main/assets-app/sample/Compose UI/` and the manifest in `assets-app/indices/`.
The existing host browser discovers the category dynamically; it needs no separate category registry.
Host device tests execute those actual packaged assets and check their equality with the installed
plugin, including against a minified release plugin.

Field valueChange includes text/selection/composition notifications. A form that treats a saved
value as dirty should compare the text before replacing its saved-status message. The example also
keeps validation feedback beside the focused editor so it remains visible when the IME resizes the
viewport. These are application-level choices, not changes to native event delivery.

The renderer enables testTagsAsResourceId at its root. A tag `start_button` is exposed verbatim as
viewIdResourceName; packageName is still the host. `id('start_button')`, `idContains('start_')` and
`idMatches('start_button')` or `idMatches(/^start_button$/)` match it. idMatches matches the whole
value, and adding a `pkg:id/` prefix does not match that raw tag. Tags do not create Android R.id
resources or replace stable Compose handle IDs.

The host's actual Android accessibility tree is distinct from ComposeTestRule's default merged
tree. In the tested Button with one tagged Text and a description, the clickable parent has ID
start_button, empty text and no description; the Text child has ID start_label and its own text.
The separate description child has no ID or click action. Both children's parent is start_button;
the parent class is android.view.View and there is also a role child. Prefer the action component's
own tag; text()/desc() can find child nodes whose clickable ancestor must be selected separately.
Do not assume that id(...).text(...) describes the same node or that every Button parent reports
android.widget.Button. Only composed/visible lazy-list items are available to these selectors.

This was verified through the real built-in host service and Rhino selectors, including
`id('start_button').findOnce().click()` reaching the script callback, on API24 x86 and API35 x86_64.
Inspecting the host's own UI requires Guard Mode off; the tested child structure uses default
Stable Mode off. Blocking selector waits belong to a non-UI script or worker. This is not a TalkBack
or third-party-service certification. The user-facing rules are in AutoJs6-Documentation's
`api/compose.md`; the rest of that API reference and declarations remain P6 work.

`:plugin-api:compose-ui-api:exportComposeUiCatalog` exports actual ComponentCatalog.V1 objects
from a JVM test-source tool, outside the frozen AAR. Its JSON includes 30 entries (29 NODE and
Snackbar COMMAND), 114 canonical properties and 12 aliases. The host tool
`.python/compose_catalog_check.py generate` produces TypeScript property interfaces and Markdown
tables; `check --dts <file> --docs <file>` compares the marked generated regions and rejects missing
files/regions, missing properties, wrong types and stale metadata. Generation is not a passed
consistency check. P6 still supplies input helper types, public factory signatures, handwritten API
methods and cross-property rules, then runs the real three-way check.

Plugin instrumentation coverage is opt-in with `-PcomposeUiCoverage=true` for debug only, using
JaCoCo 0.8.14. CI runs the complete suite on API24 x86 and API35 x86_64 and preserves reports.
For explicit adb runs, collect each owned device's coverage .ec under
`app/build/outputs/compose-coverage/`, then run `:app:reportComposeUiDeviceCoverage` with the same
flag and matching uninstrumented compiler output. The report includes plugin source classes,
excluding dependency classes and generated BuildConfig; release APKs contain no coverage runtime.
Measured coverage and reproduction details are recorded in the plugin repository's `docs/dev/p4-evidence.md`.
