# Embedded Node Example Projects

Example projects live under:

```text
sample/nodejs/
```

Each example contains:

- `README.md`
- `project.json`
- `package.json`
- `main.cjs`
- `expected-output.txt`

The suite manifest lives at `sample/nodejs/examples.json`. It records each example status, tags, whether it is included in
main-app smoke, and whether it is packaged-compatible.

For Rhino-to-Node migration guidance, see [MIGRATION_FROM_RHINO.md](MIGRATION_FROM_RHINO.md). For behavior differences from Auto.js Pro 9 and AutoX.js v7, see [COMPATIBILITY_NOTES_PRO_AUTOX.md](COMPATIBILITY_NOTES_PRO_AUTOX.md).

For the smallest stable entry-point shape, see [QUICK_START.md](QUICK_START.md). For current release limitations and
workarounds, see [releases/NODE_PROFILE_V1_KNOWN_ISSUES.md](releases/NODE_PROFILE_V1_KNOWN_ISSUES.md).

## Examples

- `hello-node`: minimal Node entry script and version smoke.
- `cjs-package`: local CommonJS helper plus a checked-in `node_modules` package.
- `fs-scoped`: synchronous scoped filesystem read/write.
- `fs-promises`: partial scoped `fs/promises` read/write.
- `npm-pure-js-cjs`: pure JavaScript CommonJS npm-style packages without `npm install`.
- `toast-app`: `toast` and `app` AutoJs bridge modules.
- `accessibility-click-text`: accessibility status and text click flow.
- `screenshot-find-image`: screenshot/image flow that exits cleanly when screen capture permission is unavailable.
- `packaged-node-apk`: packaged APK smoke entry point.
- `disabled-features-demo`: Safe Node Profile denied-capability examples.
- `typescript-smoke`: compile-time smoke for the checked-in AutoJs6 Node bridge declarations.

## Smoke Coverage

Instrumentation smoke currently runs:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeExamples
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeExampleProjectInstrumentationTest"
```

`verifyNodeExamples` validates the checked-in manifest, required files, `project.json`, `package.json`, Node directives,
PASS markers, and writes:

- `app/build/reports/nodejs/examples.json`
- `app/build/reports/nodejs/examples.md`

`verifyAutoJs6NodeTypeDeclarations` validates the checked-in TypeScript declaration set under
`docs/nodejs/types/autojs6-node/` and the `typescript-smoke` sample wiring. If `tsc` is available locally, run:

```powershell
.\gradlew.bat --console=plain :app:typeCheckNodeTypescriptSmoke
```

The instrumentation smoke copies examples from `sample/nodejs` into the device cache and runs:

- `hello-node`
- `cjs-package`
- `fs-scoped`
- `fs-promises`
- `npm-pure-js-cjs`
- `toast-app`
- `accessibility-click-text`
- `screenshot-find-image`
- `disabled-features-demo`

`typescript-smoke` is type-check only and is not part of the device instrumentation smoke list.

The examples are also added to the `androidTest` asset source set so tests exercise the checked-in sample files directly.

## Runtime Notes

The examples target AutoJs6 Safe Node Profile. They avoid:

- ESM by default
- `npm install`
- native addons
- `child_process`
- raw Node network modules
- unrestricted filesystem access

Examples that use Android capabilities such as accessibility or media projection are written to handle unavailable permissions without crashing.

Node bridge examples may declare the capability surface in `project.json`:

```json
{
  "type": "node",
  "main": "main.cjs",
  "node": {
    "permissions": ["toast", "app.launch", "accessibility", "screen_capture"]
  }
}
```

When `node.permissions` is present, undeclared bridge calls reject before dispatch with `ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED`. Older examples without the field keep compatibility mode.
