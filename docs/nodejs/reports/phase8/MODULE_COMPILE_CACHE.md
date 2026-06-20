# S8-33 Module Compile Cache

Status: complete

S8-33 enables Node's module compile cache internally for embedded script
executions while keeping the Safe Node Profile loader boundary unchanged.
AutoJs6 calls the real internal `node:module.enableCompileCache(cacheDir)` from
the bootstrap before user code starts, but the user-visible
`require("module")` / `require("node:module")` wrapper still exposes only the
safe subset.

## Runtime Policy

The compile-cache request is prepared on the Kotlin side after TypeScript
stripping and module source budget validation. The cache key includes:

- Node/header/runtime slot identity
- Android ABI list and SDK level
- AutoJs6 runtime profile version
- CJS, ESM, dynamic-import, and moduleSources loader versions
- effective feature flags
- entry source hash
- sorted `moduleSources` and runtime module source hashes

The cache directory is app-private only:

```text
<app code cache>/node-compile-cache/<runtime-key>/<project-key>
```

The project key is scoped by working directory or source parent. The content
key is reported for diagnostics. Incompatible runtime or loader upgrades create
a new runtime key, so old cache directories are naturally bypassed and later
pruned by the app-private quota policy.

Current quota:

- max total compile-cache bytes: `16777216`
- max retained project directories: `32`

Cache setup and enablement are fail-soft. Directory preparation errors disable
the compile-cache request for that execution; Node API failures are recorded in
diagnostics; script correctness must not depend on cache success.

## Safe Node Boundary

Compile cache is an internal runtime optimization/diagnostic surface, not a
script authority:

- `require("node:module").enableCompileCache` remains absent
- `require("node:module").flushCompileCache` remains absent
- `require("node:module").constants` remains absent
- `process.env.AUTOJS6_NODE_COMPILE_CACHE_*` is filtered from user scripts
- `process.env.NODE_COMPILE_CACHE` and
  `process.env.NODE_DISABLE_COMPILE_CACHE` are filtered from user scripts
- user-supplied env values cannot redirect or disable the internal cache

AutoJs6 still compiles user-managed CJS/ESM/moduleSources through its own
scoped bootstrap loader. Node's built-in compile-cache API does not expose
per-module hit/miss counters for this custom loader path, so diagnostics report
`hitMissStatus=unavailable_custom_loader` instead of inventing hit/miss data.

## Diagnostics

Embedded script `nativeValues` now include:

- `embedded_script.compile_cache.requested`
- `embedded_script.compile_cache.enabled`
- `embedded_script.compile_cache.status`
- `embedded_script.compile_cache.status_code`
- `embedded_script.compile_cache.directory`
- `embedded_script.compile_cache.key`
- `embedded_script.compile_cache.runtime_key`
- `embedded_script.compile_cache.project_key`
- `embedded_script.compile_cache.loader`
- `embedded_script.compile_cache.schema`
- `embedded_script.compile_cache.message`
- `embedded_script.compile_cache.v8_version`
- `embedded_script.compile_cache.flush_supported`
- `embedded_script.compile_cache.flush_attempted`
- `embedded_script.compile_cache.flushed`
- `embedded_script.compile_cache.custom_loader`
- `embedded_script.compile_cache.hit_count`
- `embedded_script.compile_cache.miss_count`
- `embedded_script.compile_cache.hit_miss_status`

`require("autojs6:profile").compileCache` exposes a frozen diagnostic snapshot
with the same policy state plus `appPrivateOnly=true` and
`userControlExposed=false`.

The Node Doctor device report includes a `compileCache` section with the
schema, default status, app-private base directory, quota, diagnostic prefix,
and hit/miss status note.

## Gate

Focused gate:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeModuleCompileCacheInstrumentationTest,org.autojs.autojs.ui.settings.NodeDoctorReportGeneratorInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

Static profile gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeCompatibilityProfile
```

## Evidence

Verified on Sony XQ-AT72 `QV710AF65F` on 2026-06-20:

- `:app:compileAppDebugKotlin`
- `:app:compileAppDebugAndroidTestKotlin`
- focused `NodeModuleCompileCacheInstrumentationTest` plus
  `NodeDoctorReportGeneratorInstrumentationTest` finished 3/3 successfully
- `:app:verifyNodeCompatibilityProfile`
- `git diff --check`
- `git diff -- version.properties` produced no diff

## Security Boundary

S8-33 does not promote loader authority:

- module compile-cache enable/flush controls are not script-visible
- cache location stays below app-private code cache
- user env cannot redirect the cache outside app-private storage
- user env cannot set `NODE_DISABLE_COMPILE_CACHE` for the internal enable call
- failures degrade to diagnostics without changing script results
- native addon, `process.dlopen`, `process.binding`, custom loaders, `vm`,
  inspector, raw network, and unrestricted child process policies are unchanged
