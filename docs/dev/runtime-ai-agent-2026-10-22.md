# Runtime AI Agent coverage - 2026-10-22

This batch extends the native `ai.agent` API to Node.js, Node.js Workers and Bun. Implementation, documentation, declarations and completion are synchronized. Xiaomi physical acceptance passes 58 cases, including 12 independently signed application launches and nine positive main/Worker results. The fresh original device state has been restored and verified. Redmi was not operated on.

## API and permissions

- The nine registered roots are `run`, `create`, `get`, `list`, `catalog`, `presets`, `status`, `context` and `result`.
- `AgentRun` provides `on`, `once`, `off`, `respond`, `confirm`, `cancel` and asynchronous `join`, plus task metadata and a shared result Promise. `run` and `get` return Promises. A `join` timeout does not cancel its task.
- `hasAccess`, `requestAccess` and `close` are portable runtime lifecycle helpers. Node uses `require('ai')`, `autojs6:ai` or explicit `$autojs.ai`; Bun uses `host.ai.agent`.
- Node requires `ai.agent`; the existing `ai` permission does not imply it. Bun requires the separate default-off `agent.control` grant, independent of `ai.control`.
- Registered Node/Bun scripts and Node Workers use the actual execution context for `context` and `result`. They cannot report a result for an unrelated script execution. The last explicit report wins.

The bridge reuses the native Agent registry, argument validation, model loop, tool policy and budgets. It does not emulate Rhino synchronous behavior. Bun's Agent polling is exempt from the ordinary cumulative call count, while permissions, payload bounds, concurrency and per-call deadlines remain enforced. The Bun native binary and compatibility matrix are unchanged.

## Packaged applications

Independently signed generated applications delegate Agent operations to the installed original AutoJs6 application. 3-Stove Agent retains its existing protected service and its dependency on the original host's model/device brokers.

The original host exposes a normal-permission entry point with explicit per-application consent. Identity includes package, UID, first-install time and current signers. Task access is scoped to the application's own persisted task IDs. The Plugin Center provides permission management. Revocation or an installation identity change cancels owned active tasks, including detached tasks. These installation-bound records are excluded from settings import/export.

Provider and consent components belong only to the original application flavor. The Runtime Kit contains the client and entry permission, without exposing a second provider. Registered-script context and result remain local to the executing generated application.

## Ownership and transport

Each runtime client has its own native owner. Worker termination closes its clients without closing the parent client. Ordinary owned runs cancel when their owner exits; detached runs survive ordinary owner exit. Explicit cancellation of a pending admission cancels even a detached run. Borrowed `get` handles do not cancel the original task on close.

UTF-8 JSON packets use bounded queues and chunking. Result acknowledgment retains newly admitted task handles until delivery is confirmed. Queue overflow preserves its terminal failure while releasing resources. Repeated/concurrent `get` calls reuse the live JavaScript facade and release redundant observers. Authorization timeout, revocation, late completion and runtime exit clean pending UI actions and IPC clients.

## Local verification

- Host JVM: 5,291 cases, 5,265 passed and 26 skipped, zero failures.
- Node: 159 modern tests plus seven legacy standalone programs passed. Bun: 86 SDK tests passed; a subsequent shared-facade synchronization fix passed five focused AI/Agent cases.
- Node/Bun Python checks and the unchanged Bun compatibility matrix check passed. The Node native source passed a syntax check using the actual current NDK compile command.
- Runtime declaration assets: 79. TypeScript 5.1.3 and 6.0.3 reported no diagnostics. Ace passed 1,172 runtime queries.
- Agent coverage gate: nine roots, seven run methods and three helpers. CI invokes this gate.
- Documentation generation and Offline verification passed with 169 modules and 7,348 search entries. Final Offline debug and release APKs have been rebuilt and their packaged provenance verified.

The broader augment audit examined 1,361 explicit exports and 1,596 paths. Its remaining seven failures are the existing `memory`/`$memory` static Ace surface gaps. The host's complete multilingual changelog check also retains inherited gaps: 20 feature, 17 fix and seven improvement entries. The new Agent entry exists in every language and canonical generated output matches its sources. Neither inherited gate is reported as passing.

## Device scope and evidence

Xiaomi `968e9f18`, Android 15 / API 35 / arm64 / 4 KB is the sole device assigned to this batch. Redmi is preserved at the user's request and receives no operations.

Fresh Xiaomi backups include 11 original APKs, settings, and private state for AI (12 files), Agent (13 files) and the original host (17 files). Test preparation checks their hashes again before installation. Existing local models are reused. No fake-host APK or 3-Stove custom runner is installed on the physical device.

Evidence is retained under `.bak/runtime-ai-agent-20261022`. Immutable APKs bind source commits, signatures and SHA-256 hashes. The accepted Runtime Kit and generated applications must be tied to the same frozen source and host identity. Build processes run serially with two logical processors, BelowNormal priority, one Gradle worker and in-process Kotlin compilation.

The accepted suites are host Agent 8, Node Runtime 34, Bun Runtime 13, Ace 1, APK production 1 and packaged application validation 1. Node main, Bun and Node Worker each complete 27 assertions against the actual local Gemma 4 E2B model, including terminal events, task history and cancellation. CJS/ESM/nested Worker cleanup, detached task ownership and registered Node/Bun/Worker context/result isolation are exercised; the registered result transports 9,000 UTF-16 code units of Unicode data.

The two independently signed applications complete denial, first grant, retained grant after restart, active detached-task revocation, denial after revocation and regrant. Nine positive main/Worker program results complete real model calls. Both generated applications declare the normal Agent entry permission and omit microphone permission. Their DEX hashes match the retained Runtime Kit, and their signing certificates differ from the host's certificate. The Node/Worker application cannot access the Bun application's task history.

All eight actual Ace screenshots were inspected for Node/Bun `run`, `join`, input event `requestId` and budget `maxSteps`. The completion documentation popups show the expected types and fit the viewport. The separate enclosing-call signature-help box retains its existing horizontal scrolling for long signatures. Preferences and rotation are restored by the Ace fixture.

The first host attempt passed seven of eight cases. The Bun model response omitted completion evidence, so the unchanged native `EVIDENCE_MISSING` rule correctly downgraded it to `partial`. Test-only commit `eb2290f24cfd04c1298af669f7f8bb898e3d7fb3` clarifies that the produced greeting is the deliverable and must be included in `done.evidence`, and explicitly checks that evidence is present. The new test APK is `91df5304a1ed...`; the production host, native Agent and Runtime Kit are unchanged. The subsequent complete host suite passes eight of eight. The failed attempt remains in the evidence and is not included in the 58 accepted cases. There were 66 executed case attempts in total.

Every admitted task carries the unique batch marker. All 36 task IDs, including the failed first attempt and cancelled tasks, were reconciled before restoration. `xiaomi/accepted-evidence.json`, `owned-agent-runs.json`, `ace-visual-review.json` and `finalization.json` retain the source/hash-bound results.

## Frozen local artifacts

All APK builds and the Runtime Kit export are complete. Each APK has an immutable copy with SHA-256, signer and source commit in `artifact-map.json`. The source worktree remained unchanged during each accepted build.

| Component | APK version | Source commit | SHA-256 prefix |
| --- | --- | --- | --- |
| AutoJs6 host | 6.8.0 / 5850 | `28d7849da2575b1e2e70509b939da723c7de7eca` | `bd47f56b5e095d9e` |
| Node Runtime (release) | 1.32.0 / 292 | `aebca6e33b87eda064df5f6b6e0fbb114a565d00` | `e8519798af0c78a5` |
| Bun Runtime | 0.21.0 / 296 | `8bf0350cd60d5dbca75e8a0dacd9f328e7a9afb8` | `19b38b6998ce6744` |
| 3-Stove Agent | 1.3.0 / 426 | `56f2a0bd25837d29e893617ae4b06864e271f467` | `af165761bac64d0a` |
| Ace Editor | 2.42.0 / 328 | `0cbcb3cbaa0db4f248e862d600cd4b3e94b2bacc` | `b22c58a313c86940` |
| Offline Docs | 6.8.40 / 202 | `cd0d31b9159c1963bcebab0daf5a05a49e1f0fee` | `0e003ae0dfc0019b` |
| APK Builder Template | 1.2.26+autojs6-6.8.0 / 585001 | `46255752095efb717e699ee847213733e18634f1` | `f602a387af137dbb` |

Builder's repository version is 1.2.26 / build 111, with per-host sequence 1 and Android versionCode 585001. Documentation revision 164 is committed at `12508b346bb2686dd0d8cd17b3992ada7c182a1f`; generated source provenance is `c4999573cda259001c21d3f6324bb2894778fd3b`. TypeScript Declarations 4.80.0 is committed at `c5995a3b47ba69263a55d4fa90f67a2e402d2ec0`.

The retained arm64 Kit is `autojs6-runtime-v6.8.0+5850-28d7849da2575b1e2e70509b939da723c7de7eca-arm64-v8a`. It is stored in the Builder repository under `.bak/runtime-ai-agent-20261022/kit-5850`. Its template SHA-256 is `df0ce254b67fecdbc635841d835b9225676d2aafdbc27e18620a5bba09b0f0b4`. A first export with the default `local` source marker was rejected before packaging; the accepted export explicitly supplies its frozen source SHA.

The unchanged AI provider is 1.5.0 / build 299, source `687d52c3d7ffe1f28678ef4f8f746ed2cdce1510`, reused from the prior immutable AI core evidence.

## Complete local JVM totals

| Suite | Passed | Skipped | Failed |
| --- | ---: | ---: | ---: |
| Host | 5265 | 26 | 0 |
| Node release | 96 | 0 | 0 |
| Bun | 60 | 0 | 0 |
| Agent | 683 | 1 | 0 |
| Ace | 187 | 0 | 0 |
| Offline Docs | 2 | 0 | 0 |
| Builder | 40 | 0 | 0 |

Total: 6,360 cases, 6,333 passed and 27 skipped, zero failures/errors. Node's release-test configuration exposes `testReleaseUnitTest`; the initial debug task selection was corrected using the actual Gradle task list. All six plugin builds completed their configured lint, signature and artifact checks. Static 16 KB alignment checks do not claim 16 KB device execution.

## Xiaomi handoff and restoration

The user initially requested waiting for the other session's entire work to finish. After a quiet interval, the frozen cohort was installed, but external testing restarted before the first API test. That preliminary installation was fully backed out to the 13:58 baseline (original host build 5781), with zero API tests. The actual device owner was subsequently monitored without further device operations; neither idle process gaps nor an `end_turn` acknowledgment was treated as release.

The user then explicitly released Xiaomi and requested stopping the scheduled monitor. Automation `xiaomi-ai-agent` was paused. A new backup was captured after that handoff, including all 11 original APKs, permissions/settings/volumes and AI/Agent/host private files. Earlier snapshots and the preliminary backout evidence remain under `xiaomi-before-external-20261022-*`.

After final acceptance, all fixture files were removed or restored to their baseline contents, generated applications and the newly installed Bun test package were removed, and all 11 original APK hashes were verified. The temporary same-version/same-signer Node Debug restoration bridge retained the UID and was not used for runtime acceptance. Agent's 13, host's 17 and AI's 12 baseline private files were restored; both existing model binaries match their original SHA-256 values. Private restoration preceded final accessibility reactivation. The originally enabled host service is bound, and permissions, system settings, volumes and app-op modes match the new baseline. Original APK/private backups are retained.

This batch has issued no Redmi device operations. Parallel sessions continue changing the primary host and documentation workspaces; these changes must be preserved during integration. Their current source or freshly rebuilt APKs are not the frozen artifacts above.

All commits and artifacts are local. Nothing has been pushed, tagged or published. The accepted production source is the fixed artifact source in the table, independently of later documentation, test-only and parallel integration commits.
