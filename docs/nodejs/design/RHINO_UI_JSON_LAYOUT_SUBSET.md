# Rhino UI JSON Layout Subset

Status: P12-08 design-locked schema gate.

This document turns the S11-23 Rhino UI compatibility decision into a concrete
JSON-only migration subset for Phase 12. P12-09 adds constrained `compat.ui`
over this subset, but there is still no default global `ui`, XML parsing, or
direct Android view access. Device and packaged promotion are tracked
separately by P12-10.

## Entry Points

The only accepted runtime target for this subset is the existing Node
`require("ui")` provider shape:

- `ui.showLayout(layoutJson, options?)`
- `UiHandle.update(patch, options?)`
- `UiHandle.batchUpdate(patches, options?)`
- `UiHandle.on(event, callback, options?)`
- `UiHandle.close(options?)`

P12-09 exposes `autojs6:compat.ui` as a constrained wrapper for this shape.
The facade does not install a default global `ui`; when
`compat.install({ globals: true })` exposes `globalThis.ui`, that global is the
constrained facade described here, not Rhino's raw UI module.

## Accepted Descriptor

A layout descriptor is a plain JSON object with a required `type` field at the
root. Supported component types are:

- containers: `Column`, `Row`, `Vertical`, `Horizontal`, `LinearLayout`
- content and controls: `Text`, `Button`, `Input`, `Image`, `ScrollView`,
  `List`, `Switch`, `Checkbox`, `CheckBox`, `Progress`, `ProgressBar`, `Tabs`,
  `Tab`
- lower-case aliases already accepted by the Node provider, such as `text`,
  `button`, `input`, `list`, `switch`, `checkbox`, `progress`, `tabs`, and
  `tab`

Portable properties are JSON values only:

- identity: `id`
- display: `text`, `hint`, `title`, `contentDescription`, `alt`, `textSize`
- state: `value`, `checked`, `enabled`, `progress`, `max`, `indeterminate`,
  `selectedIndex`
- structure: `children`, `content`, `tabs`
- collection data: `items` as strings, numbers, booleans, nulls, or JSON
  objects with `text`, `label`, and `value`

Descriptor size limits mirror the existing JS bridge preflight:

- maximum JSON string length: 32768 characters
- maximum descriptor depth: 16
- maximum object properties per object: 64
- maximum array entries per array: 128
- maximum `batchUpdate()` patch entries: 64
- maximum drained UI events per request: 64
- live event queue bound: 128 provider events

`ui.state()` and `ui.bind()` are allowed only as Node-side one-way JSON helpers.
The binding object must be resolved before provider dispatch; it is not a
runtime object passed to Android.

## Rejected Descriptor Forms

The subset rejects anything that cannot be serialized as plain JSON before
bridge dispatch:

- XML raw strings and JSX/Rhino XML literals
- executable attributes such as `onClick`, `onLongClick`, `onclick`, or string
  snippets intended to be evaluated as JavaScript
- JavaScript functions, symbols, `bigint`, `undefined`, non-finite numbers,
  cyclic objects, non-plain object instances, promises, buffers, typed arrays,
  dates, regular expressions, or Java/Rhino host objects
- Rhino `NativeView`, Android `View`, `Activity`, `Context`, `Window`,
  `Handler`, widget registry, resource object, or Activity emitter references
- `WebView` descriptors until a separate WebView security gate exists
- floaty/overlay descriptors through base `ui`; overlay remains a separate
  capability decision

Invalid descriptors must fail before bridge dispatch where the JS facade can
detect them. Provider-side validation remains a second line of defense for
unsupported component types and nested component structure.

## Rhino API Migration Status

| Rhino API | P12-08 status | Node migration |
| --- | --- | --- |
| `ui.layout(...)` | `partial` | Manually translate the supported declarative tree into a JSON descriptor and pass it to `ui.showLayout(json)` or `compat.ui.showLayout(json)`. XML raw strings and executable attributes are rejected. |
| `ui.layoutFile(path)` | `planned` | May be migrated later only through a constrained project-local JSON descriptor file. XML file parsing is not part of P12-08. |
| `ui.findById(...)`, `ui.findByStringId(...)`, `ui.findView(...)`, `ui.root` | `blocked_by_design` | Widget ids are scoped inside an opaque `UiHandle`; updates use JSON patches and events carry JSON snapshots. |
| `ui.run(fn)` | `blocked_by_design` | Use normal Node async code plus `handle.update()` or `handle.batchUpdate()`. JavaScript never moves to the Android main looper. |
| `ui.post(fn, delay)` | `blocked_by_design` | Use `setTimeout()` or promises on the Node event loop, then send JSON updates. |
| `ui.inflate(...)`, `ui.__inflate__(...)`, `ui.setContentView(view)` | `blocked_by_design` | No `NativeView` or Android `View` crosses the bridge. |
| `ui.finish()` | `partial` | Close the explicit `UiHandle`; there is no implicit current Rhino Activity. |

## Fixtures

Canonical P12-08 fixtures live in
`docs/nodejs/fixtures/rhino-ui-json-layout-subset-fixtures.json`.

The fixture file is intentionally not a runtime test vector for rendering. It
documents three classes of migration inputs:

- accepted JSON descriptors that can be sent to `showLayout`
- rejected inputs that must fail before bridge dispatch or at provider
  validation
- migration hints for common Rhino UI calls whose behavior is intentionally
  blocked or rewritten

## Evidence

- Schema: `docs/nodejs/schemas/rhino_ui_json_layout_subset.schema.json`
- Fixtures: `docs/nodejs/fixtures/rhino-ui-json-layout-subset-fixtures.json`
- Runtime target: `app/src/main/cpp/node_bridge_sources.cpp`
- Provider validation: `app/src/main/java/org/autojs/autojs/engine/NodeBridgeUiProvider.kt`
- Existing design: `docs/nodejs/design/RHINO_UI_COMPATIBILITY.md`
- Host verifier: `tools/nodejs/phase12/verify-rhino-compat-ui-json-layout-subset.js`
- Gradle gate: `:app:verifyNodeRhinoCompatUiJsonLayoutSubset`
