# typescript-smoke

TypeScript smoke project for the checked-in AutoJs6 Safe Node Profile declarations.

Run the type check from the repository root:

```powershell
tsc -p sample/nodejs/typescript-smoke/tsconfig.json --pretty false
```

The runtime `main.cjs` is intentionally small; this sample exists to keep bridge module declarations aligned with the stable CommonJS API surface.

This declaration gate is independent from runtime compilation. Executable
`.ts/.mts/.cts` samples use the AutoJs6 TypeScript Compiler plugin v0.6.0 or
newer before the generated JavaScript is dispatched to Node.
