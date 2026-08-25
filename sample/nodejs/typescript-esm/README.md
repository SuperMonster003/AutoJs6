# typescript-esm

TypeScript ESM fixture for the complete host compiler pipeline.

Install and enable the AutoJs6 TypeScript Compiler plugin v0.6.0 or newer, then
launch this project from AutoJs6. The host compiles `main.mts`, dispatches the
generated ESM snapshot to the Node Runtime plugin, and retains the source map
for TypeScript stack locations. Direct raw `.mts` dispatch remains fail-closed.
