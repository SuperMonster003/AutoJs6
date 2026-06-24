# P13-11 Pro Entry Directives

Status: partial

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

## Not Promoted Yet

- Full Activity lifecycle launch evidence is not complete.
- Back/Recents behavior for packaged Node UI entries is not complete.
- Concrete UI-thread dispatch semantics are not complete.
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

The gate is expected to report `partial` with zero policy violations until the
remaining Activity lifecycle, Back/Recents, and UI-thread dispatch evidence
lands.
