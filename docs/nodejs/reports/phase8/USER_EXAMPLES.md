# Phase 8 User Examples

S8-49 expands the checked-in `sample/nodejs` catalog with Phase 8 user-facing
examples. These examples are static-verified by `verifyNodeExamples` and
included in the examples report. They are not added to the default
`NodeExampleProjectInstrumentationTest` runtime smoke list.

## Added Examples

| Example | Status | Purpose |
| --- | --- | --- |
| `require-esm` | partial | CommonJS entry loading a local ESM module with `require()` |
| `http-client-compat` | experimental | Controlled HTTP client shape and default-denied network behavior |
| `filehandle-advanced` | partial | Scoped `fs/promises.open()` FileHandle lifecycle |
| `fs-watch` | partial | Scoped watcher creation, file update, and close lifecycle |
| `compile-cache` | partial | CommonJS module cache and optional compile-cache probe |
| `inspector-debug` | experimental | Release inspector disabled-state probe |
| `cpu-profile` | experimental | Design-gated CPU profiling placeholder |
| `hardened-sandbox` | experimental | Design-gated isolated-process sandbox placeholder |

## Verification

```powershell
.\gradlew.bat --console=plain :app:verifyNodeExamples
```

The current catalog contains 33 examples after this expansion.
