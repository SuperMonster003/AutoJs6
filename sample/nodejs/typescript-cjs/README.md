# typescript-cjs

TypeScript CommonJS fixture for the complete host compiler pipeline.

Install and enable the AutoJs6 TypeScript Compiler plugin v0.6.0 or newer, then
launch this project from AutoJs6. The host compiles `main.cts`, sends the
generated CommonJS and source map to the Node Runtime plugin, and maps runtime
errors back to the original TypeScript. Sending this raw file directly to the
Node Runtime plugin remains intentionally rejected with
`ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`.
