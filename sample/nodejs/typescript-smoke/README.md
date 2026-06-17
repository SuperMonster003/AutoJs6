# typescript-smoke

TypeScript smoke project for the checked-in AutoJs6 Safe Node Profile declarations.

Run the type check from the repository root:

```powershell
tsc -p sample/nodejs/typescript-smoke/tsconfig.json --pretty false
```

The runtime `main.cjs` is intentionally small; this sample exists to keep bridge module declarations aligned with the stable CommonJS API surface.
