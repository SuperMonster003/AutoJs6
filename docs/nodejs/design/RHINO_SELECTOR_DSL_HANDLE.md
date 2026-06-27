# Rhino Selector DSL Handle Design Gate

Status: design gate only. S11-15 does not enable new runtime behavior. It records the compatibility handle model that S11-16 should implement on top of the existing controlled `accessibility` bridge.

## Decision

Rhino selector compatibility in Node should use a synchronous JavaScript handle for selector construction and Promise-returning methods for any accessibility query or action.

The handle must be a plain JavaScript object, not a raw Rhino `UiSelector`, `UiObject`, Java object, or Android `AccessibilityNodeInfo`. It owns only a frozen JSON selector descriptor compatible with the existing `require("accessibility")` module:

```js
{
  type: "autojs6.accessibility.selector",
  version: 1,
  text: "OK",
  clickable: true
}
```

This keeps the Safe Node Profile boundary unchanged. Node scripts can build selectors synchronously, but every operation that touches the Android accessibility service must be awaited.

## Runtime Shape

The compatibility facade should expose selector builders through `require("autojs6:compat")` and, when explicitly installed, the common Rhino global names.

```js
const compat = require("autojs6:compat");

const ok = compat.text("OK").clickable();
const node = await ok.findOne(3000);
const clicked = await ok.click({ timeoutMs: 3000 });
const rows = await compat.descContains("Row").find();
```

S11-16 should add at least these builder entrypoints:

| Entry | Returns | Existing bridge dependency |
| --- | --- | --- |
| `text(value)` | selector handle | `accessibility.text()` descriptor builder |
| `desc(value)` | selector handle | `accessibility.desc()` descriptor builder |
| `id(value)` | selector handle | `accessibility.id()` descriptor builder |
| `className(value)` | selector handle | `accessibility.className()` descriptor builder |
| `textContains(value)` | selector handle | `accessibility.textContains()` descriptor builder |
| `descContains(value)` | selector handle | `accessibility.descContains()` descriptor builder |
| `textMatches(value)` | selector handle | `accessibility.textMatches()` descriptor builder |
| `descMatches(value)` | selector handle | `accessibility.descMatches()` descriptor builder |
| `idMatches(value)` | selector handle | `accessibility.idMatches()` descriptor builder |
| `classNameMatches(value)` | selector handle | `accessibility.classNameMatches()` descriptor builder |
| `clickable(value = true)` | selector handle | `accessibility.clickable()` descriptor builder |
| `enabled(value = true)` | selector handle | `accessibility.enabled()` descriptor builder |
| `depth(value)` | selector handle | `accessibility.depth()` descriptor builder |
| `boundsInside(...)` | selector handle | `accessibility.boundsInside()` descriptor builder |
| `boundsContains(...)` | selector handle | `accessibility.boundsContains()` descriptor builder |

Chaining merges descriptor fields into a new frozen descriptor:

```js
compat.text("OK").descContains("Confirm").clickable();
```

The original handle must remain immutable. Reusing `const ok = compat.text("OK")` must not mutate `ok` when a later chain appends filters.

## Execution Methods

Every method below crosses the bridge and returns a Promise:

| Rhino-style call | Node compatibility contract | Existing bridge method |
| --- | --- | --- |
| `text("OK").findOne(timeout)` | `Promise<UiNodeSnapshot | null>` | `accessibility.findOne` |
| `text("OK").findOne({ timeoutMs })` | `Promise<UiNodeSnapshot | null>` | `accessibility.findOne` |
| `text("OK").find()` | `Promise<readonly UiNodeSnapshot[]>` | `accessibility.findAll` |
| `text("OK").click()` | `Promise<boolean>` | `accessibility.click(selector)` |
| `text("OK").longClick()` | `Promise<boolean>` | `accessibility.longClick` |
| `text("OK").setText("value")` | `Promise<boolean>` | `accessibility.setText` |
| `text("OK").scrollForward()` | `Promise<boolean>` | `accessibility.scrollForward` |
| `text("OK").scrollBackward()` | `Promise<boolean>` | `accessibility.scrollBackward` |
| `click(x, y)` | `Promise<boolean>` | `accessibility.click(x, y)` |
| `back()` | `Promise<boolean>` | `accessibility.back` |
| `home()` | `Promise<boolean>` | `accessibility.home` |

`findOne(timeout)` is a compatibility overload. A number should map to `{ timeoutMs: timeout }`; an object should pass through as bridge options. The default timeout remains the existing accessibility module default.

`find()` is the Rhino-friendly name for `accessibility.findAll()`. The lower-level `findAll()` alias may also be exposed for clarity.

## Non-goals

S11-15 and S11-16 must not expose:

- raw `AccessibilityNodeInfo`;
- raw Rhino `UiObject` or `UiSelector`;
- Java object handles;
- synchronous blocking accessibility queries;
- automatic enabling of accessibility service;
- `pickup()`, `detect()`, `existsAll()`, or `existsOne()` as raw Rhino equivalents.

`exists()` can be implemented later as `await handle.findOne()` plus a boolean conversion. `pickup()` and `detect()` need a separate handle/snapshot design because Rhino can return `UiObject` instances or callback results that do not fit the current JSON snapshot boundary.

## Error And Resource Policy

Selector handle construction should throw synchronous `ERR_AUTOJS6_RHINO_COMPAT_INVALID_OPTION` only for malformed compatibility options. Descriptor validation errors from the existing accessibility builder can keep the current bridge error shape.

Bridge execution errors must preserve current `accessibility` semantics:

- unavailable provider: `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED`;
- rate limit: `ERR_AUTOJS6_BRIDGE_RATE_LIMITED`;
- malformed descriptor: pre-dispatch rejection;
- timeout/cancellation: existing bridge timeout/signal behavior.

The handle itself has no resource token and needs no explicit close. Result nodes are frozen JSON snapshots and must not support live methods. Any future live node handle must be a separate design gate with ownership, stale-handle, and cleanup rules.

## S11-16 Implementation Notes

S11-16 can be implemented entirely in the Node bridge JavaScript layer unless instrumentation reveals a missing provider method. The existing Kotlin bridge already has selector query/action methods and returns JSON snapshots or booleans.

Required JavaScript work:

- create a `RhinoSelectorHandle` factory around `accessibility` descriptor builders;
- merge descriptor fields immutably;
- add numeric timeout normalization for `findOne(timeout)`;
- expose builder globals through `autojs6:compat.install({ globals: true })`;
- route `find()` to `accessibility.findAll()`;
- route `click()/longClick()/setText()/scrollForward()/scrollBackward()` to existing selector actions;
- add host tests for descriptor immutability and instrumentation tests for no raw node leakage.
