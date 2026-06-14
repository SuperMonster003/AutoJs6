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

For Rhino-to-Node migration guidance, see [MIGRATION_FROM_RHINO.md](MIGRATION_FROM_RHINO.md).

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

## Smoke Coverage

Instrumentation smoke currently runs:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeExampleProjectInstrumentationTest"
```

The smoke test copies examples from `sample/nodejs` into the device cache and runs:

- `hello-node`
- `toast-app`

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
