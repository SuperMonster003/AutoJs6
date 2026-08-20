# AutoJs6 Node.js Quick Start

This page is the short entry point for the AutoJs6 Safe Node Profile. For the complete release boundary, read
[COMPATIBILITY_PROFILE.md](COMPATIBILITY_PROFILE.md) and
[releases/NODE_PROFILE_V1_STABLE_SURFACE.md](releases/NODE_PROFILE_V1_STABLE_SURFACE.md).
For the current v1.1 user-facing draft, read
[NODE_PROFILE_V1_1_USER_DRAFT.md](NODE_PROFILE_V1_1_USER_DRAFT.md).

The default main-app release is `nodePlugin` with the required external
`plugin` backend. Install, enable, and authorize an ABI-compatible AutoJs6 Node
runtime plugin before running Node entries. Self-contained `nodeFull` and
`nodeMini` builds and exported packaged apps continue to use Embedded Node.
There is no silent runtime change when the required plugin is unavailable.

## Minimal Script

Use a Node directive at the top of the script:

```js
"nodejs";

console.log(process.version);
```

The `"node";` directive is also accepted.

## File Entry Modes

Stable Node routing accepts:

- `.node.js`
- `.cjs`
- `.mjs` as a Node route
- `.ts`, `.mts`, and `.cts` in the v1.2 lightweight TypeScript profile
- `project.json` with `type: "node"` or `projectType: "node"`
- encrypted script metadata that marks the script as a Node entry

`.mjs` preserves user intent and routes to Node. In Node Profile v1.2, `.mjs` runs through the stable partial ESM path by
default. CommonJS remains the widest-compatibility path. Dynamic `import()` is available only through the local-only
profile for relative modules, allowed builtins, and scoped packages.

`.mts` is always ESM, `.cts` is always CommonJS, and `.ts` follows the nearest
`package.json` `type`. AutoJs6 lightweight TypeScript strips erasable type
syntax only; it does not type-check, read `tsconfig.json`, compile TSX, or
transform enums/decorators/parameter properties.

## Project Shape

```text
project/
  project.json
  package.json
  main.cjs
  node_modules/
```

Example `project.json`:

```json
{
  "type": "node",
  "main": "main.cjs",
  "node": {
    "timeoutMs": 30000,
    "permissions": ["toast", "app"]
  }
}
```

Omitting `node.backend` inherits the release-flavor policy. Use an explicit
backend only for a documented diagnostic or self-contained build; the default
`nodePlugin` flavor requires `plugin`, while `pluginOptional` is valid only for
`nodeFull`/`nodeMini` and can fall back only before plugin dispatch.

When `node.permissions` is present, undeclared bridge capabilities reject before dispatch.

To generate a valid starter project or validate an existing one, use the
host-side project wizard:

```powershell
node tools/nodejs/project/autojs6-node-project.js create --template commonjs-app --out D:\work\my-node-app
node tools/nodejs/project/autojs6-node-project.js validate D:\work\my-node-app
```

See [PROJECT_WIZARD.md](PROJECT_WIZARD.md) for all templates and validator
error codes.

## CommonJS And Dependencies

The stable v1 path is CommonJS-first:

- `require()`
- local `.js`, `.cjs`, and `.json` modules
- `node_modules`
- scoped packages
- package `main`, `exports`, `imports`, and self-reference

Dependencies must be vendored or bundled at build time. Runtime `npm install`, lifecycle scripts, postinstall scripts,
native addons, and unchecked `.bin` execution are outside the Safe Node Profile.

## AutoJs Bridge Example

```js
"nodejs";

const { showToast } = require("toast");
const app = require("app");

await showToast(`package: ${app.packageName}`);
```

Bridge calls are Promise-based and must remain asynchronous from JavaScript's perspective.

## TypeScript

Use the project wizard for lightweight runtime TypeScript starters:

```powershell
node tools/nodejs/project/autojs6-node-project.js create --template typescript-cjs --out D:\work\my-ts-cjs
node tools/nodejs/project/autojs6-node-project.js create --template typescript-esm --out D:\work\my-ts-esm
```

Runtime-supported extensions are `.ts`, `.mts`, and `.cts`. `.tsx` is a
build-time-only path; use the `compiled-tsx` template when TSX should compile
to JavaScript before packaging.

TypeScript declarations for AutoJs6 bridge modules live under [types/autojs6-node](types/autojs6-node). See
`sample/nodejs/typescript-smoke` for type-check wiring and `sample/nodejs/typescript-cjs`,
`sample/nodejs/typescript-esm`, and `sample/nodejs/packaged-typescript` for runtime examples.

## Scoped Filesystem

Filesystem access is scoped to the script working directory. Absolute paths are
allowed only when they still resolve inside that scoped root. NUL bytes, `..`
escapes, symlink escapes, realpath escapes, absolute paths outside the scoped
root, and `/proc`, `/sys`, or `/dev` access are rejected.

With the external backend, this scope is mirrored per request into a
plugin-private workspace. Only request-prepared sources and explicitly mapped
file inputs are archived; files merely present elsewhere in the host sandbox
are not discovered by a recursive scan. A successful run writes verified
outputs back and can delete only explicit input files named by the final
tombstone manifest. A cancelled, lost, or invalid post-dispatch commit is never
replayed automatically.

## What To Read Next

- v1.1 feature draft: [NODE_PROFILE_V1_1_USER_DRAFT.md](NODE_PROFILE_V1_1_USER_DRAFT.md)
- Examples: [EXAMPLES.md](EXAMPLES.md)
- Project wizard and validator: [PROJECT_WIZARD.md](PROJECT_WIZARD.md)
- Rhino migration: [MIGRATION_FROM_RHINO.md](MIGRATION_FROM_RHINO.md)
- Error catalog: [ERROR_CATALOG.md](ERROR_CATALOG.md)
- Known issues: [releases/NODE_PROFILE_V1_KNOWN_ISSUES.md](releases/NODE_PROFILE_V1_KNOWN_ISSUES.md)
- GA release notes draft: [releases/NODE_PROFILE_V1_GA.md](releases/NODE_PROFILE_V1_GA.md)
- Testing and device gates: [TESTING.md](TESTING.md)
- R5 three-ABI acceptance evidence: [R5_ACCEPTANCE_SAMPLING.md](R5_ACCEPTANCE_SAMPLING.md)
