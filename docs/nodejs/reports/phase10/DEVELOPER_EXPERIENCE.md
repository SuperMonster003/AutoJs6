# Phase 10 Developer Experience

S10-43 through S10-46 move the modern-project developer experience into a
focused Phase 10 gate without widening the Safe Node Profile runtime boundary.

## Gate

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase10DeveloperExperience
.\gradlew.bat --console=plain :app:verifyNodeDeveloperExperienceGate "-Pautojs.nodejs.test.deviceProfile=sony"
```

`verifyNodePhase10DeveloperExperience` writes:

- `build/reports/nodejs/phase10-developer-experience.json`
- `build/reports/nodejs/phase10-developer-experience.md`

## Decisions

- TypeScript build-stage pipeline: implemented for `.ts`, `.mts`, and `.cts`.
- Runtime TypeScript remains the existing lightweight erasable-syntax profile.
- TypeScript compiler plugins and package lifecycle scripts remain disabled.
- Source maps remain scoped diagnostics only and do not weaken filesystem policy.
- The embedded `node:test` subset remains single-runtime and does not use
  `child_process` isolation.
- Watch/hot reload is recorded as a debug-only host workflow. Packaged and
  release hot reload remain disabled.

## S10-43 TypeScript Build Pipeline

The Phase 10 verifier builds a temporary host fixture under
`build/phase10-developer-experience-fixture` and records:

- `.cts` to `.cjs` output;
- `.mts` to `.mjs` output;
- `.ts` helper source handling;
- source-map file generation;
- incremental cache hit on a second identical build;
- cache invalidation when a source changes;
- execution of the generated CommonJS and ESM outputs with host Node.

The build-stage verifier rejects package lifecycle scripts, compiler plugins,
native `.node` files, runtime `.tsx`, and TypeScript syntax that requires code
generation.

## S10-44 Source Map And Stack Trace v2

Runtime stack mapping stays covered by
`NodeSourceMapStackTraceInstrumentationTest`:

- inline source maps;
- scoped external source maps;
- denial for source maps outside the working directory.

The Phase 10 developer-experience report adds build-stage TypeScript source-map
evidence for generated CJS and ESM output.

## S10-45 Embedded Test Runner

The focused device gate runs the existing embedded `node:test` subset coverage:

- TAP reporter output;
- `test`, `describe`, `it`, `before`, `after`, `beforeEach`, `afterEach`;
- skip and todo;
- timeout failure;
- assertion failure;
- late async activity failure;
- ESM import of the subset.

`node:test/reporters`, file discovery, coverage, watch mode, and
process-isolated test execution remain unsupported.

## S10-46 Watch / Hot Reload

The current Phase 10 state is debug-only:

- graph invalidation and restart policy are recorded by the host verifier;
- plugin hot reload remains explicit through
  `plugins.reload(id, { debug: true })`;
- calls without `debug: true` remain denied;
- packaged APK and release hot reload remain disabled.

This does not promote background Android hot reload or packaged release reload.
