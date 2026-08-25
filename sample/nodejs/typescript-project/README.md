# typescript-project

Phase 10 CommonJS TypeScript project using the host compiler pipeline.

Install and enable the AutoJs6 TypeScript Compiler plugin v0.6.0 or newer. The
host compiles `main.cts` before Node dispatch and preserves its source map; the
Node Runtime plugin never executes the raw TypeScript fallback path.
