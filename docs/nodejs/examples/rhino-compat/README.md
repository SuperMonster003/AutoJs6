# Rhino Compat Migration Examples

Status: S11-27 static example set. These examples document common Rhino to
Embedded Node compatibility rewrites. They are not a runtime smoke suite; the
gate verifies that every checked-in snippet compiles and that the Node examples
use the expected `autojs6:compat` surface.

## Layout

Each example has two files:

- `*.rhino.js`: the legacy Rhino style.
- `*.node.cjs`: the Embedded Node compatibility style.

The suite manifest is [manifest.json](manifest.json). It records the covered
API family and source pair for each example.

## Examples

| ID | Rhino APIs | Node compat APIs |
| --- | --- | --- |
| `toast` | `toast()`, `toastLog()` | `compat.toast()`, `compat.toastLog()` |
| `files` | `files.write()`, `files.read()`, `files.remove()` | `compat.files.write()`, `compat.files.read()`, `compat.files.remove()` |
| `storage` | `storages.create()` | `compat.storages.create()` |
| `selector` | `text(...).findOne().click()` | `compat.text(...).findOne()`, `compat.text(...).click()` |
| `image` | `images.read()`, `images.clip()`, `images.findImage()` | `compat.images.read()`, `compat.images.clip()`, `compat.images.findImage()` |
| `ocr` | `requestScreenCapture()`, `captureScreen()`, `ocr.recognizeText()` | `compat.requestScreenCapture()`, `compat.captureScreen()`, `compat.ocr.recognizeText()` |
| `app-device` | `app.launchPackage()`, `device.*` | `compat.app.launchPackage()`, `compat.device.*` |
| `shell` | `shell()` | `compat.shell()` |

## Verification

Run:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeRhinoCompatExamples
```

The gate checks:

- `manifest.json` schema and duplicate ids;
- all Rhino and Node snippet files exist;
- every required coverage family is present;
- Node snippets start with `"nodejs";` and use `require("autojs6:compat")`;
- Rhino and Node snippets pass `node --check` syntax compilation;
- the migration guide and `EXAMPLES.md` link to this directory.

## Runtime Notes

These examples focus on source migration. Android-backed calls still depend on
the same provider, permission, and bridge-capability rules described in
[MIGRATION_FROM_RHINO.md](../../MIGRATION_FROM_RHINO.md). Calls that return
Promises in Node must be awaited, even when the Rhino API looked synchronous.
