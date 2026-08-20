# AutoJs6 Node Project Wizard

`tools/nodejs/project/autojs6-node-project.js` is a host-side helper for
creating and validating Safe Node Profile project directories. It does not run
inside Android and does not loosen runtime policy.
For the current v1.2 feature status behind these templates, see
[COMPATIBILITY_PROFILE.md](COMPATIBILITY_PROFILE.md).

## Create

List available templates:

```powershell
node tools/nodejs/project/autojs6-node-project.js templates
```

Create a project:

```powershell
node tools/nodejs/project/autojs6-node-project.js create `
  --template commonjs-app `
  --name my-node-app `
  --out D:\work\my-node-app
```

Available templates:

- `commonjs-app`: minimal CommonJS app.
- `esm-app`: stable ESM app using `.mjs`.
- `automation-script`: starter with `toast` and `app` bridge metadata.
- `screenshot-ocr-script`: starter with `image`, `screen_capture`, and `ocr`
  bridge metadata.
- `network-fetch-script`: starter with controlled `fetch` and `network`
  bridge metadata.
- `typescript-cjs`: lightweight TypeScript CommonJS starter using `.cts`.
- `typescript-esm`: lightweight TypeScript ESM starter using `.mts`.
- `typescript-automation`: TypeScript bridge starter with `toast` and `app`
  metadata.
- `typescript-ocr`: TypeScript screen capture plus OCR bridge starter.
- `typescript-long-running-task`: TypeScript long-running task starter with
  explicit signal cleanup.
- `compiled-tsx`: build-time TSX starter that runs generated JavaScript only.

Each template writes:

- `project.json`
- `package.json`
- an entry such as `main.cjs`, `main.mjs`, `main.cts`, `main.mts`, or `main.ts`
- `README.md`

Some templates also write build-time support files. For example,
`compiled-tsx` writes `src/view.tsx`, `tsconfig.json`, and a prebuilt
`dist/view.js` entry target. Runtime TSX is still unsupported; TSX must be
compiled on the desktop/build stage.

## Validate

Validate a project before running or packaging it:

```powershell
node tools/nodejs/project/autojs6-node-project.js validate D:\work\my-node-app
```

Use `--json` for CI or IDE integration:

```powershell
node tools/nodejs/project/autojs6-node-project.js validate D:\work\my-node-app --json
```

The JSON report uses schema `autojs6-node-project-validation-v1` and returns
`ok`, `issueCount`, an `issues` array, and a `compatibility` object. Each issue has:

- `severity`
- `code`
- `file`
- `message`
- `hint`

The compatibility object uses schema
`autojs6-node-dependency-compatibility-v1` and classifies the installed project
as one of:

- `compatible`
- `compatible with capability`
- `partial`
- `unsupported`
- `unsafe`

It records the entry format, required Node builtins, declared bridge
capabilities, capability requirements, package format/export-condition usage,
expected packaged graph size, and signals used by the APK builder preflight
gate. `unsupported` and `unsafe` signals are surfaced as validator issues.

## Validator Coverage

The validator checks:

- `project.json` exists, parses, and declares `type` or `projectType` as
  `node`.
- `package.json` exists, parses, has a package name, and uses only supported
  `type` values.
- The declared entry file exists, stays inside the project root, and uses a
  supported `.js`, `.cjs`, `.mjs`, `.ts`, `.cts`, or `.mts` extension. `.tsx`
  entries are rejected with an explicit diagnostic.
- Bridge permission arrays are JSON string arrays and use known Safe Node
  Profile capabilities or explicit `plugin.<id>` capabilities.
- `node_modules` dependency bundles do not contain `.node` files, common native
  library/object files, `binding.gyp`, unsafe lifecycle scripts, symlinks, or
  generated `.bin` shims.
- Source analysis classifies ESM/CJS entry format, required builtins, raw
  network, `child_process`, `worker_threads`, fs literal path escapes, and
  bridge capability requirements before packaging.

Common actionable error codes:

- `PROJECT_FILE_MISSING`
- `PROJECT_JSON_TYPE`
- `PACKAGE_NAME_INVALID`
- `PACKAGE_TYPE_UNSUPPORTED`
- `ENTRY_DECLARATION_MISSING`
- `ENTRY_FILE_MISSING`
- `ENTRY_EXTENSION_UNSUPPORTED`
- `ENTRY_TYPESCRIPT_TSX_UNSUPPORTED`
- `ENTRY_PATH_UNSAFE`
- `PERMISSIONS_SHAPE`
- `UNKNOWN_PERMISSION`
- `NATIVE_ADDON_DISABLED`
- `NODE_GYP_ARTIFACT_UNSUPPORTED`
- `DEPENDENCY_LIFECYCLE_SCRIPT_UNSUPPORTED`
- `DEPENDENCY_SYMLINK_UNSUPPORTED`
- `DEPENDENCY_BIN_ENTRY_UNSUPPORTED`
- `DEPENDENCY_BUNDLE_TOO_LARGE`
- `CAPABILITY_DECLARATION_MISSING`
- `FS_OUTSIDE_SCOPE`
- `UNSUPPORTED_PACKAGE_MANAGER_LAYOUT`

## Dependency Manager

Lockfile-based dependency installation remains a host-side project action:

```powershell
.\gradlew.bat --console=plain :app:manageNodeDependencies "-Pautojs.nodejs.dependencyManager.projectRoot=D:\work\my-node-app"
```

The action requires `package-lock.json`, runs desktop `npm ci` with lifecycle
scripts ignored by default, validates and sanitizes the installed tree,
generates SBOM-lite and compatibility reports, updates the dependency cache,
and writes an APK-ready `assets/project` layout. It does not enable runtime
package installation on Android.

## Verification

The checked-in Gradle gate exercises every template and representative invalid
projects for missing entries, invalid package names, unknown permissions,
unsupported TSX entries, and unsafe dependency bundles. It also verifies every
template has a non-blocking compatibility status, checks the analyzed entry
format, and exercises source-level unsupported/unsafe analyzer fixtures:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeProjectWizard
```

Reports are written to:

- `app/build/reports/nodejs/project-wizard.json`
- `app/build/reports/nodejs/project-wizard.md`
