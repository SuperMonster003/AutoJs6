# AutoJs6 Node.js Maintenance Plan

Last reviewed: 2026-08-21.

## Invariants

- The host APK contains no libnode or Node JNI bridge.
- Node-marked scripts require the external runtime plugin and never fall back to Rhino.
- The plugin repository is authoritative for nodejs-api.
- Host and plugin capability manifests remain independent but aligned.
- Host and plugin example trees remain byte-identical.
- Node-specific cross-repository guards remain manual and require an explicit plugin
  root when repositories are not siblings.
- User script contents, environment secrets, and private paths are excluded from
  Node.js Doctor reports.

## Change Routing

Runtime/native/module-loader changes belong in the plugin. Android API dispatch,
permissions, trust, workspace transfer, and host routing belong in AutoJs6. Contract
changes start in the plugin API module and are mirrored into the host. A change that
crosses the boundary must include both repository revisions in its verification note.

## Routine Work

- Run host unit/compile checks for every host change.
- Run contract, capability, and example guards for cross-repository changes.
- Run real-plugin instrumentation for routing, contract, workspace, or bridge changes.
- Inspect the host APK for accidental libnode reintroduction before release.
- Keep [Quick start](QUICK_START.md), [Testing](TESTING.md), and the plugin
  **docs/HOST-API.md** current.

See [Post-GA smoke](POST_GA_SMOKE.md) and [Release checklist](RELEASE_CHECKLIST.md).
