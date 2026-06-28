# P13-11 Pro Entry Directives

Status: ready

P13-11 starts the Auto.js Pro-style Node entry directive layer for
`"nodejs ui"` and `"nodejs ui-thread"`. The landed scope formalizes parsing,
metadata propagation, diagnostics, and package metadata defaults. It does not
yet promote full Activity lifecycle or concrete UI-thread execution parity.

## Landed

- Runtime parser:
  `app/src/main/java/org/autojs/autojs/script/NodeEntryDirective.kt`
- Existing source directive parser coverage:
  `app/src/main/java/org/autojs/autojs/script/JavaScriptSource.java`
- Project launch metadata:
  `NodeProjectLaunchOptions.entryDirective`
- Descriptor field:
  `.autojs6-node-launch.json` `entryDirective`
- Packaged config field:
  `project.json` `node.entryDirective`
- Native/debug diagnostics:
  `embedded_project_runner.entry_directive`
- Focused instrumentation:
  `EmbeddedScriptMvpInstrumentationTest#proStyleNodeEntryDirectivesParseToStableMetadata`
- Host verifier:
  `tools/nodejs/phase13/verify-pro-entry-directives.js`
- Gradle gate:
  `:app:verifyNodePhase13ProEntryDirectives`
- Output:
  `build/reports/nodejs/phase13-pro-entry-directives.json`
- Output:
  `build/reports/nodejs/phase13-pro-entry-directives.md`

## Current Capability

The source parser recognizes:

- `"nodejs ui"`
- `"nodejs ui-thread"`
- aliases split by whitespace, comma, semicolon, or pipe

`NodeEntryDirective` normalizes the directive into canonical strings and exposes
stable `requiresUiActivity` and `requiresUiThread` diagnostics. Node project
launches carry the directive through descriptor parsing, source metadata,
project request construction, debug descriptors, native values, failure markers,
and packaged project config defaults.

Packaged long-running service dispatch explicitly rejects UI entry directives
for now. This prevents a project from combining the incomplete UI Activity
lifecycle path with the foreground-service long-running path.

P13-11 UI-thread dispatch policy: `"nodejs ui-thread"` is a compatibility
intent and scheduling constraint, not permission to run the V8 isolate on the
Android main thread. Node JavaScript continues to run in the controlled embedded
runtime; Activity-owned providers such as the future UI bridge must marshal UI
mutations, callbacks, and lifecycle events onto the Android main looper. Direct
synchronous Rhino-style UI-thread execution, raw `Activity` / `View` access,
and blocking calls from JavaScript into the main looper remain unpromoted.
Promotion requires Activity lifecycle and Back/Recents evidence plus provider
tests showing bounded asynchronous main-thread dispatch and cleanup.

## Closure

P13-11 v1.1 closes the metadata directive scope. Activity lifecycle,
Back/Recents, packaged UI entry, relaunch/crash recovery, and concrete
provider-owned UI behavior remain P13-17 / future packaged UI promotion work,
not entry directive metadata blockers.
- This does not promote `require("ui")`; the existing provider remains a
  separate declarative bridge surface.
- `$autojs`, Java interop, Rhino compatibility helpers, and
  `importClass`/`importPackage` and `JavaAdapter` now have P13-15/P13-16
  stable-denial decisions, while actual Java listener/provider parity remains
  later work.

## Verification Notes

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase13ProEntryDirectives
```

The gate is expected to report `ready` with zero policy violations for the
P13-11 metadata directive scope. Activity lifecycle and Back/Recents packaged
evidence remain under P13-17 / future packaged UI promotion.
