# Compose UI F.1 TSX integration

Date: 2026-10-04. Host 6.8.0 / 5319, isolated `composeUiSpike` debug application.
The source remains on the dedicated Compose integration branch. The main host
checkout and its concurrent codegen work are outside this change.

The existing classic TSX factory ABI routes the 29 actual Compose node factories,
their aliases, exact catalog names and `compose:Name` names to the existing graph
construction path. Lowercase XML tags retain their original renderer. Factories
are matched by current-runtime identity; arbitrary functions are never executed
as TSX components. Frozen V1 wire objects, AARs and renderer dependencies do not
change. Ordinary JavaScript Compose use still has minimum host build 5316.

Neutral fragments keep raw values until a domain consumes them. They preserve
script ownership, source order, array cycle checks and cumulative depth/input
budgets. Compose roots require one expanded node, slots accept zero or one, and
container children use existing component rules. Numbers/true retain XML scalar
semantics but are rejected as Compose children. The frozen lazy-child limit is
10000, distinct from the 5000-node limit; neutral input accounting includes
containers and ignored values and is bounded at 20001 entries. Tests exercise
the exact limit and the next value, without changing contract constants.

Invalid mixed trees fail before replacing the accepted UI or callback table.
The `__autojs6Tsx` and `ui.layout` bridges preserve typed Compose exceptions.
Direct `ui.layout(ComposeNode)` and a fragment containing that node also reject
before inflating or replacing the old XML page.

The first device run exposed a real conversion issue: `NativeObject` implements
`Map`, and generic JS unwrapping copied a Compose node into an ordinary object.
The TSX-specific unwrap now preserves node/array identity and only unwraps bounded
Java wrappers and concatenated strings. The new JVM tests use the actual Rhino
return bridge, and the unchanged strict device assertions pass after the fix.

Validation completed before the implementation commit:

- Host Compose loader/session/runtime and XML TSX JVM selection: 173 tests, zero
  failures; frozen Compose API: 16 tests, zero failures.
- Catalog generator/checker: 16 Python tests; real catalog projections match
  30 components / 114 properties and 29 JSX intrinsic declarations.
- `assembleAppDebug`, `assembleAppDebugAndroidTest` and the debug native alignment
  gate pass. Build number/time auto-increment is disabled.
- Owned API 35 x86_64 AVD: six `ComposeTsxDeviceTest` cases pass with actual `.tsx`
  files, the installed TypeScript Engine 0.6.4 / 84 and signed Compose UI 1.1.0 /
  27 renderer. The compiler is reached through the normal client and execution
  entry, with the real generated Rhino declarations and no test type stubs.
- Device coverage includes factory aliases and namespace tags, fragments/slots,
  native clicks, state/ref/key reuse, mixed-render recovery, three rejected XML
  replacement paths, single-root/slot limits, unregistered functions, the non-UI
  mount error and an actual non-UI floating window that updates and closes.

Commands used (both build auto-increment properties are disabled):

```text
:app:testAppDebugUnitTest
  --tests org.autojs.autojs.core.plugin.compose.*
  --tests org.autojs.autojs.runtime.api.augment.compose.*
  --tests org.autojs.autojs.runtime.api.augment.ui.TypeScriptTsxRuntimeTest
  --tests org.autojs.autojs.*ComposeUiSharedClasspathTest
:plugin-api:compose-ui-api:testDebugUnitTest
:app:assembleAppDebug :app:assembleAppDebugAndroidTest
```

These commands also use `-PcomposeUiSpike=true`. Device logs and detailed Gradle
outputs are ignored under `app/build/compose-f1-*`. Final signed compiler
validation, companion source commits, artifact digests and owned-device cleanup
are recorded in the Compose UI plugin's `docs/dev/p7-tsx-evidence.md`.

Full host JVM/lint, the full Android matrix, packaged-app TSX execution, fresh
comparable timing/PSS/scroll observations and remote CI are not claimed here.
F.2 XML/Compose embedding remains the next roadmap item. No push or publication.
