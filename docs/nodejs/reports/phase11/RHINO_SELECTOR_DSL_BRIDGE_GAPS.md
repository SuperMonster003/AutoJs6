# Rhino Selector DSL Bridge Gap List

Status: S11-15 design output. This report compares the Rhino selector DSL target with the existing Node `accessibility` bridge and identifies what S11-16 must implement.

## Existing Node Bridge Coverage

| Capability | Existing JavaScript surface | Existing Kotlin bridge method | S11-16 bridge gap |
| --- | --- | --- | --- |
| Build exact text selector | `accessibility.text(value)` | none; JS descriptor builder | No new bridge method. |
| Build exact desc selector | `accessibility.desc(value)` | none; JS descriptor builder | No new bridge method. |
| Build id selector | `accessibility.id(value)` | none; JS descriptor builder | No new bridge method. |
| Build class selector | `accessibility.className(value)` | none; JS descriptor builder | No new bridge method. |
| Build contains selector | `textContains`, `descContains` | none; JS descriptor builder | No new bridge method. |
| Build regex selector | `textMatches`, `descMatches`, `idMatches`, `classNameMatches` | none; JS descriptor builder | No new bridge method. |
| Build boolean filters | `clickable`, `enabled` | none; JS descriptor builder | No new bridge method. |
| Build bounds/depth filters | `boundsInside`, `boundsContains`, `depth` | none; JS descriptor builder | No new bridge method. |
| Query one node | `accessibility.findOne(selector, options)` | `findOne` | No new bridge method. Need Rhino handle overload normalization. |
| Query all nodes | `accessibility.findAll(selector, options)` | `findAll` | No new bridge method. Need Rhino `find()` alias. |
| Selector click | `accessibility.click(selector, options)` | `click` | No new bridge method. |
| Coordinate click | `accessibility.click(x, y, options)` | `click` | No new bridge method. |
| Long click | `accessibility.longClick(selector, options)` | `longClick` | No new bridge method. |
| Set text | `accessibility.setText(selector, text, options)` | `setText` | No new bridge method. |
| Scroll actions | `scrollForward`, `scrollBackward` | `scrollForward`, `scrollBackward` | No new bridge method. |
| Global actions | `back`, `home`, `recentApps` | `back`, `home`, `recentApps` | No new bridge method. |
| Click by text shortcut | `accessibility.clickText(text, options)` | `clickText` | Optional wrapper; not required for S11-16 MVP. |
| Find by text shortcut | `accessibility.findByText(text, options)` | `findByText` | Optional wrapper; not required for S11-16 MVP. |

## JavaScript Facade Gaps

| Gap | Required for S11-16 | Notes |
| --- | --- | --- |
| `RhinoSelectorHandle` object | Yes | Wrap frozen `accessibility.SelectorDescriptor`. |
| Immutable chain merge | Yes | `text("A").desc("B")` must not mutate the original `text("A")` handle. |
| Numeric timeout overload | Yes | `findOne(3000)` maps to `{ timeoutMs: 3000 }`. |
| `find()` alias | Yes | Maps to `accessibility.findAll`. |
| `exists()` wrapper | Optional | Maps to `(await findOne()) !== null`; can be added without bridge work. |
| `pickup()` / `detect()` | No | Deferred because Rhino can return raw `UiObject` or callback values. |
| `existsAll()` / `existsOne()` | No | Deferred until multi-selector wrapper semantics are designed. |
| live node handles | No | Current bridge returns frozen snapshots only. |
| automatic accessibility enablement | No | Existing provider policy remains fail-closed unless service is already available. |

## Required S11-16 Tests

- host test: selector chain returns new handles and frozen descriptors;
- host test: malformed builder arguments reject before bridge dispatch;
- host test: `findOne(number)` normalizes to `timeoutMs`;
- instrumentation test: `text("OK").findOne(timeout)` returns a JSON snapshot or stable provider-denied error;
- instrumentation test: `text("OK").click()` uses selector bridge path and does not leak `AccessibilityNodeInfo`;
- instrumentation test: coordinate `click(x, y)`, `back()`, and `home()` preserve Promise contracts;
- regression check: `require("autojs6:compat")` still does not install selector globals by default.
