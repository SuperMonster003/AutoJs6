# Compose UI contract V1

Status: P1.1 frozen on 2026-10-02. This document specifies the host/plugin boundary, not delivery of
the script API or production renderer. The host owns `plugin-api/compose-ui-api`; the plugin consumes
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
- `CONTRACT_VERSION=1`, `MIN_SUPPORTED=1`. `REQUIRED_HOST_VERSION_CODE=5308` is deliberately still
  provisional and will be back-filled in P1.3; this metadata correction does not change the V1 wire API.
- `api.spike` (version -1) is a retained P0 regression fixture, excluded from the frozen V1 surface.
  The prototype plugin factory still implements it until P2.1. It must never be accepted as a V1
  renderer. Its diagnostic classOrigins/probe/diagnostics methods are not V1 loading methods.

The host now packages the V1 API module in app/inrt. The small legacy fixture types travel in the
same AAR during this transition, but no spike Activity or loader is added to the host APK. Production
loader selection, caching, fingerprint negotiation and renderer migration remain P1.2/P2.1 work.

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
