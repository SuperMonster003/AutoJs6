# packaged-typescript

Packaged TypeScript module-graph fixture for the compiler-to-Node snapshot path.

The `.mts` entry imports a local `.ts` module and dynamically imports a local
`.mts` module. AutoJs6 packages or snapshots the project, the TypeScript
Compiler plugin emits JavaScript plus source maps, and the Node Runtime plugin
maps the computed TypeScript specifier only within that closed snapshot.

The sample requires the TypeScript Compiler plugin v0.6.0 or newer. A missing,
ambiguous, late-created, or escaping TypeScript target fails with a stable
snapshot/path error instead of falling back to raw source.
