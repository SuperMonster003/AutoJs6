# Rhino UI Compat Smoke

Status: P12-10 evidence gate for the constrained `compat.ui` facade.

## Scope

P12-10 verifies the smallest safe path for Rhino UI migration through
`require("autojs6:compat").ui`. The facade still maps to the existing
`require("ui")` JSON provider and does not expose Rhino raw `View`,
`Activity`, `NativeView`, `ui.run()`, or `ui.post()` semantics.

The covered path is show/update/batchUpdate/event/close:

- `compat.ui.showLayout(json)` creates an opaque frozen `UiHandle`.
- `UiHandle.on("click", callback)` receives JSON event snapshots on the Node
  event loop through bounded `drainEvents`.
- `UiHandle.update(patch)` and `UiHandle.batchUpdate(patches)` send JSON-only
  mutations.
- `UiHandle.close()` releases the provider-owned UI session.

## Evidence

- Fake-transport instrumentation:
  `app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt`
  covers `compat.ui.showLayout`, `compat.ui.update`,
  `compat.ui.batchUpdate`, `compat.ui.close`, a click callback, and
  `bridge.conformance.pending=0`.
- Packaged Rhino compat smoke:
  `app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt`
  now exercises `compat.ui` through the packaged fake bridge transport and
  requires `packaged.rhino_compat.compat_ui=true` plus
  `packaged.rhino_compat.pending=0`.
- Provider cleanup:
  `app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeResourceLeakStressInstrumentationTest.kt`
  verifies the Android provider creates a `UI_SESSION`, closes it with
  `ui.close`, and reports zero active bridge resources after each loop and
  after provider destroy.
- Protocol cleanup:
  `app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt`
  registers UI sessions as `NodeBridgeResourceType.UI_SESSION`, releases them
  on `ui.close`, and closes all owned resources on provider destroy.

## Packaged Boundary

The packaged smoke intentionally uses the deterministic fake bridge transport.
It proves the packaged loader, permissions, `compat.ui` facade routing, marker
collection, and pending-call cleanup, but it is not a live Activity UI smoke.

Live packaged UI remains bounded by the normal Node execution policy. One-shot
and interactive session launches can own `ui` resources; scheduled/background
execution does not list `ui` as an allowed provider while packaged live UI is
not promoted as a background surface.

Connected live packaged UI was not rerun in this pass. skip reason: the
available device already had `org.autojs.autojs6` installed with a
signature mismatch, and this task did not uninstall or replace the user's
installed app.

## Gate

Host gate: `verifyNodeRhinoCompatUiSmoke`.

Suggested local command:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeRhinoCompatUiSmoke
```
