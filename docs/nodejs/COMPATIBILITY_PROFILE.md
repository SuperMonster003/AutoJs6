# AutoJs6 Node.js Compatibility Profile

Last reviewed: 2026-08-26.

## Runtime and Delivery

- Node.js version: 24.5.0, supplied by the external runtime plugin.
- Node runtime topology: required external plugin; no selectable host Node backend.
- Ordinary non-Node JavaScript: source-routed to Rhino independently of Node.
- Host APK: no libnode and no Node JNI bridge.
- Node entries: the "nodejs" directive, .node.js, .cjs, .mjs, .cts, .mts, and
  Node project metadata.
- Missing plugin: explicit failure; no Rhino fallback for Node-marked scripts.
- Exported APKs: Node runtime bundling is not supported.

## Language and Modules

The plugin owns CommonJS/ESM resolution, local dynamic import, light TypeScript
stripping, Node builtins, worker_threads, child_process policy, and Java-interoperability
policy. The host supplies bounded module-source providers only when a selected
candidate needs host-side materialization or decryption.

## AutoJs6 APIs

Script-facing AutoJs6 modules use the live host bridge. Capabilities must be declared
when a project manifest is present, and Android permissions/provider health are
checked again by the host. Values crossing Binder are bounded and JSON-safe; raw
Context, Activity, View, Bitmap, cursor, shell process, native pointer, and arbitrary
Java objects never cross.

The exact module/method inventory lives in the runtime plugin's **docs/HOST-API.md**.
Type declarations live in the runtime plugin under
[docs/nodejs/types/autojs6-node](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/tree/master/docs/nodejs/types/autojs6-node).

## Intentionally Unsupported or Restricted

- Native addons and arbitrary shared-library loading.
- Inspector/private binding access and unrestricted reflection.
- Unbounded process, worker, network, file-watch, or resource creation.
- Filesystem access outside the request workspace/sandbox policy.
- Nested Node launches through engines.execScript and engines.execScriptFile.
- Silent backend fallback.

See [Security model](SECURITY_MODEL.md) for boundary details and
[Rhino migration](MIGRATION_FROM_RHINO.md) for source changes.
