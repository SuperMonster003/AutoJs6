# AutoJs6 Node Project Wizard

`tools/nodejs/project/autojs6-node-project.js` is a host-side helper for
creating and validating Safe Node Profile project directories. It does not run
inside Android and does not loosen runtime policy.
For the current v1.1 feature status behind these templates, see
[NODE_PROFILE_V1_1_USER_DRAFT.md](NODE_PROFILE_V1_1_USER_DRAFT.md).

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
- `esm-app`: partial ESM app using `.mjs`.
- `automation-script`: starter with `toast` and `app` bridge metadata.
- `screenshot-ocr-script`: starter with `image`, `screen_capture`, and `ocr`
  bridge metadata.
- `network-fetch-script`: starter with controlled `fetch` and `network`
  bridge metadata.

Each template writes:

- `project.json`
- `package.json`
- `main.cjs` or `main.mjs`
- `README.md`

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
`ok`, `issueCount`, and an `issues` array. Each issue has:

- `severity`
- `code`
- `file`
- `message`
- `hint`

## Validator Coverage

The validator checks:

- `project.json` exists, parses, and declares `type` or `projectType` as
  `node`.
- `package.json` exists, parses, has a package name, and uses only supported
  `type` values.
- The declared entry file exists, stays inside the project root, and uses a
  supported `.js`, `.cjs`, or `.mjs` extension.
- Bridge permission arrays are JSON string arrays and use known Safe Node
  Profile capabilities or explicit `plugin.<id>` capabilities.
- `node_modules` dependency bundles do not contain `.node` files, common native
  library/object files, `binding.gyp`, unsafe lifecycle scripts, symlinks, or
  generated `.bin` shims.

Common actionable error codes:

- `PROJECT_FILE_MISSING`
- `PROJECT_JSON_TYPE`
- `PACKAGE_NAME_INVALID`
- `PACKAGE_TYPE_UNSUPPORTED`
- `ENTRY_DECLARATION_MISSING`
- `ENTRY_FILE_MISSING`
- `ENTRY_EXTENSION_UNSUPPORTED`
- `ENTRY_PATH_UNSAFE`
- `PERMISSIONS_SHAPE`
- `UNKNOWN_PERMISSION`
- `NATIVE_ADDON_DISABLED`
- `NODE_GYP_ARTIFACT_UNSUPPORTED`
- `DEPENDENCY_LIFECYCLE_SCRIPT_UNSUPPORTED`
- `DEPENDENCY_SYMLINK_UNSUPPORTED`
- `DEPENDENCY_BIN_ENTRY_UNSUPPORTED`
- `DEPENDENCY_BUNDLE_TOO_LARGE`

## Verification

The checked-in Gradle gate exercises every template and representative invalid
projects for missing entries, invalid package names, unknown permissions, and
unsafe dependency bundles:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeProjectWizard
```

Reports are written to:

- `app/build/reports/nodejs/project-wizard.json`
- `app/build/reports/nodejs/project-wizard.md`
