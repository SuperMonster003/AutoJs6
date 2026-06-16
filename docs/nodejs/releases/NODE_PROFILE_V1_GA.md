# AutoJs6 Node Profile v1 GA Release Notes

Status: draft. This document is prepared for GA but must not be treated as a publication record until the final GA gate
and tag are approved.

Target profile: AutoJs6 Safe Node Profile v1

GA candidate gate commit: `7e047953`

Final GA commit/tag: `TBD`

## Headline

AutoJs6 Node Profile v1 adds a controlled embedded Node.js runtime for Android automation scripts. The profile focuses on
CommonJS, scoped filesystem access, restricted builtins, asynchronous AutoJs bridge modules, packaged APK support, and
diagnosable release gates.

It is not full desktop Node.js.

## Supported Entry Modes

- `"nodejs";` and `"node";` directives
- `.node.js`
- `.cjs`
- `.mjs` as a Node route
- `project.json` with `type: "node"` or `projectType: "node"`
- packaged APK Node entries
- encrypted script metadata carrying the Node execution flag

Default `.mjs` behavior routes to Node and rejects ESM unless the explicit experimental ESM gate is enabled.

## Stable Runtime Surface

See [NODE_PROFILE_V1_STABLE_SURFACE.md](NODE_PROFILE_V1_STABLE_SURFACE.md) for the frozen v1 surface.

Highlights:

- embedded backend is the default
- `rhino`, `native`, `embedded`, and `auto` backend switches are retained
- native one-shot backend remains as a regression oracle
- CommonJS resolver supports `node_modules`, package `main`, `exports`, `imports`, and self-reference
- scoped filesystem policy remains enforced
- packaged APK native library injection and manifest verification are release-gated

## Stable And Partial Builtins

Stable low-risk surfaces include:

- `path`
- `util`
- `events`
- `assert`
- `querystring`
- `string_decoder`
- `timers`
- `buffer`
- `console`
- safe `process` identity and event subset

Partial or limited wrappers include:

- `fs/promises`
- `stream`
- `zlib`
- `crypto`
- `os`
- `url`
- `module`
- network-shaped modules that provide stable denial behavior rather than raw network authority

The complete list is in [../COMPATIBILITY_PROFILE.md](../COMPATIBILITY_PROFILE.md).

## AutoJs Bridge Modules

Node bridge calls are Promise-based and asynchronous. Stable module routing and API shapes cover:

- `toast`
- `app`
- `engines`
- `dialogs`
- `accessibility`
- `media_projection`
- `image` and `images`
- `clipboard`
- `device`
- `shell`
- `fetch`
- `websocket`
- `work_manager`

Some Android-backed providers remain partial or reject in normal builds. See
[NODE_PROFILE_V1_KNOWN_ISSUES.md](NODE_PROFILE_V1_KNOWN_ISSUES.md).

## Packaged APK Support

Packaged Node APKs verify:

- `libnode.so`
- `libautojs6-node.so`
- `libc++_shared.so`
- embedded service manifest entry
- foreground-service permissions and `specialUse` policy
- ABI pruning
- entry script budget metadata: max `524288` bytes
- moduleSources budget metadata: max `64` modules, `262144` bytes per module, `524288` bytes total
- packaged smoke output

Phase 4 keeps legacy unversioned JNI entry points compatible for stale packaged templates while current app builds call
versioned V2 JNI symbols.

## Release Flavor And ABI Strategy

Release flavors:

- `nodeFull`: full Embedded Node runtime with `arm64-v8a`, `armeabi-v7a`, and `x86_64`
- `nodeMini`: explicitly selected smaller ABI set
- `rhinoOnly`: no embedded Node native libraries

`x86` remains unsupported and must fail fast.

Release size review is tracked by `:app:nativeNodeAbiSizeReport`, which writes `app/build/reports/nodejs/abi-size-report.md` with APK artifact size, packaged template size, per-ABI native library payload, and `nodeFull` / `nodeMini` / `rhinoOnly` payload comparison.

## Experimental Features

The following remain default-off:

- ESM
- dynamic import
- worker threads
- controlled live network provider
- WebSocket live provider
- Java/Android interop allowlist
- Rhino compatibility shim
- long-running Node service mode
- scheduled/background Node execution

Promotion requirements are documented in [../EXPERIMENTAL_PROMOTION_POLICY.md](../EXPERIMENTAL_PROMOTION_POLICY.md).

## Disabled Features

The following remain disabled in normal v1 builds:

- native `.node` addons
- unrestricted `child_process`
- unrestricted raw `http`, `https`, `net`, `tls`, `dns`, and `dgram`
- unrestricted Java or Android object access
- unrestricted `worker_threads`
- unrestricted `vm`
- inspector/debugger server
- `process.binding`
- `process.dlopen`
- `process._linkedBinding`
- `process.getBuiltinModule`

## Migration Links

- Quick start: [../QUICK_START.md](../QUICK_START.md)
- Rhino migration: [../MIGRATION_FROM_RHINO.md](../MIGRATION_FROM_RHINO.md)
- Pro/AutoX compatibility notes: [../COMPATIBILITY_NOTES_PRO_AUTOX.md](../COMPATIBILITY_NOTES_PRO_AUTOX.md)
- Examples: [../EXAMPLES.md](../EXAMPLES.md)

## Troubleshooting Links

- Known issues: [NODE_PROFILE_V1_KNOWN_ISSUES.md](NODE_PROFILE_V1_KNOWN_ISSUES.md)
- Testing: [../TESTING.md](../TESTING.md)
- Release checklist: [../RELEASE_CHECKLIST.md](../RELEASE_CHECKLIST.md)
- Release identity: [NODE_PROFILE_V1_RELEASE_IDENTITY.md](NODE_PROFILE_V1_RELEASE_IDENTITY.md)
- Tag dry run: [NODE_PROFILE_V1_TAG_DRY_RUN.md](NODE_PROFILE_V1_TAG_DRY_RUN.md)
- Maintenance plan: [../MAINTENANCE_PLAN.md](../MAINTENANCE_PLAN.md)
- Native crash diagnostics: [../NATIVE_CRASH_DIAGNOSTICS.md](../NATIVE_CRASH_DIAGNOSTICS.md)
- Flake policy: [../FLAKE_POLICY.md](../FLAKE_POLICY.md)

## Sony GA Gate Status

Current Phase 4 Sony XQ-AT72 status:

- selected primary device: Sony XQ-AT72 `QV710AF65F`
- selected device profile: `sony`
- default public package identity: `org.autojs.autojs6`
- side-by-side test identity: `org.autojs.autojs6.nodejs` via `-Pautojs.nodejs.identity=nodejsTest`
- release identity decision: production identity selected; final production-identity gate pending; see [NODE_PROFILE_V1_RELEASE_IDENTITY.md](NODE_PROFILE_V1_RELEASE_IDENTITY.md)
- Task 128 test-identity GA candidate gate: passed on 2026-06-16
- command: `:app:verifyNodeReleaseGate "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.fuzzerBudget=normal"`
- identity under test: `org.autojs.autojs6.nodejs` / `AutoJs6 Node`
- duration: `2h 5m 30s`
- Android test XML: `310` tests, `0` failures, `0` errors, `2` expected skips
- packaged smoke aggregate: ready
- memory stress: `7` scenarios, `0` failures
- focused normal-budget fuzzer sweep: passed before the aggregate gate
- crash buffer after the final aggregate: clean for foreground-service startup, fatal exception, request spool, and embedded service crash markers

The expected skips are `disabledBuildGateReturnsActionableMessage`, because this build has embedded script execution
enabled, and packaged experimental ESM smoke, because the GA profile keeps ESM and dynamic import default-off. Worker
stress remains outside the default full profile unless `autojs.nodejs.worker_threads.experimental=true`.

An earlier Sony full-profile attempt was invalidated because unrelated `org.autojs.autojs6.ace` activity overlapped the
packaged smoke marker window. The final aggregate pass above is the valid GA candidate gate evidence.

Evidence is stored under `docs/nodejs/reports/phase4/sony-ga-candidate/`. Public GA still requires the final
production-identity gate under `org.autojs.autojs6`.

## Generated Artifacts

Expected GA artifact set:

- `build/reports/nodejs/compatibility-profile.json`
- `build/reports/nodejs/compatibility-profile.md`
- `build/reports/nodejs/release-manifest.json`
- `build/reports/nodejs/release-manifest.md`
- native dependency report
- native symbol archive report
- ABI size report: `app/build/reports/nodejs/abi-size-report.md`
- Node Doctor text and JSON reports
- packaged APK smoke host report
- memory stress report
- performance report when lifecycle or native-load behavior changes
- dependency bundler manifest and rejection report

Task 129 regenerated the pre-tag artifact set on 2026-06-16 from commit `7e047953`; see
[NODE_PROFILE_V1_GA_CHECKLIST.md](NODE_PROFILE_V1_GA_CHECKLIST.md). The final public tag must regenerate the release
manifest from the approved final version state.

## Publication Checklist

Before tagging GA:

- complete [NODE_PROFILE_V1_GA_CHECKLIST.md](NODE_PROFILE_V1_GA_CHECKLIST.md)
- final commit and tag replace the `TBD` placeholders
- [NODE_PROFILE_V1_STABLE_SURFACE.md](NODE_PROFILE_V1_STABLE_SURFACE.md) matches the generated compatibility profile
- [NODE_PROFILE_V1_KNOWN_ISSUES.md](NODE_PROFILE_V1_KNOWN_ISSUES.md) is current
- full Sony production-identity GA gate result is attached
- release manifest is generated
- no experimental feature is promoted without the documented gates
