# Phase 10 Scope

Phase 10 is Real-World Project Runtime & Automation Activation. It advances
packaged APK parity, live-provider evidence, safe package management,
long-running execution, limited concurrency, worker/WASM policy, developer
experience, crash evidence, and adapter readiness without changing the default
Node runtime.

## Runtime Freeze

- Default runtime slot: `node24_5`
- Default runtime version: `24.5.0`
- Node expected prefix: `v24.5.0`
- Node 24.17 status: `waiting_for_community_fork`
- Node 24.17 promotion: not allowed in Phase 10

The freeze is enforced by `:app:verifyNodePhase10RuntimeFreeze`, which checks
the recorded per-ABI `libnode.so` SHA-256 values, the runtime build lock,
`NodeRuntimeCapabilities`, and the moduleSources budgets.

## Non-Goals

Phase 10 does not compile, replace, or promote Node 24.17 artifacts. It also
does not enable native addons, unrestricted `child_process`, raw network
modules, private Node bindings, `process.dlopen`, unrestricted Java/Android
interop, production Inspector, raw `node:wasi`, or broader scoped-fs access.

The moduleSources global budget remains:

- max module count: `64`
- max single module bytes: `262144`
- max total moduleSources bytes: `524288`

## First-Wave Scope

The first wave covers S10-01 through S10-10:

- runtime freeze
- runtime capability truth report
- focused gate matrix
- packaged APK fixture cache-key evidence
- unresolved skip audit
- packaged runtime descriptor compatibility evidence
- packaged template synchronization checks
- packaged ESM full path
- packaged local dynamic import
- packaged source map and module graph evidence

`verifyNodePackagedParityGate` remains the device-backed proof for packaged APK
execution. Host reports are evidence gates, not substitutes for install and
launch validation.
