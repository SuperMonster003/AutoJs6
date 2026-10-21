# Runtime Flow core delivery - 2026-10-21

Status: complete locally. Host implementation, shared Node/Bun/Worker facade, reference documentation, declarations, Ace completion, physical-device and actual packaged-application validation, restoration and temporary-workspace cleanup are complete. Nothing was pushed, tagged or published.

## Delivered contract

This batch delivers **22 of the 52 registered Flow root method names**, plus the Flow constructor, FlowError, state inspection and core chains. The count does not establish complete Flow or Rhino parity.

- Roots: wait/waitAsync/waitUntil/waitWhile, whenPresent/repeatUntil, waitForStable/waitForVisible/waitForHidden/waitForGone, waitForActivity/waitForPackage, sleep/delay, run/of, all/race/any, cancelAll/defaults/trace.
- Chains: Promise adoption, then/catch/else/finally/map/run/filter/peek, waits, sleep/delay, timeout/retry, whenPresent/repeatUntil, cancel and toPromise. Chains can fork; cancelling one branch preserves a live sibling.
- Node main threads and CJS/ESM/nested Workers use flow/autojs6:flow and explicit $autojs.flow. Bun uses host.flow. Callbacks and timers stay in the importing JS runtime. AsyncLocalStorage preserves child ownership across await; late children of cancelled callbacks are cancelled immediately.
- Stateless host queries return plain node snapshots and observed foreground components. Flow retains no Android node handles across responses. Malformed fields and regexes reject instead of broadening a query.
- Local callbacks/combinators need no host grant. UI queries require Node accessibility or Bun's existing, default-off accessibility.control and the calling application's bound Android service. Unavailable services cannot make hidden waits succeed. Recovery does not grant permissions or open Settings.
- Bun retains the ordinary 1024-call budget, four concurrent transport calls, size limits and per-call deadlines. One UI sample uses at least start/poll. Native Bun bytes and its Android/ABI matrix were not changed.

The precise overloads, defaults, errors and portable limits are in Documentation `api/runtimeApis.md`, section "Flow 异步链与等待核心". Synchronous Rhino terminals, injected global aliases, live Java UiObject behavior, within/scope, advanced toolkit actions and event waits remain outside this batch.

The remaining 30 registered root names are: `waitThenClick`, `clickWait`, `waitThenClickBounds`, `clickBoundsWait`, `waitThenLongClick`, `waitForStableThenClick`, `clickWhenStable`, `waitForStableThenClickBounds`, `clickBoundsWhenStable`, `clickWhenStableAfter`, `clickBoundsWhenStableAfter`, `smartClick`, `smartClickBounds`, `clickIfExists`, `clickBoundsIfExists`, `clickAny`, `clickBoundsAny`, `findAny`, `waitForIdle`, `waitForEvent`, `waitForToast`, `waitForNotification`, `scrollUntil`, `typeInto`, `dismissPopups`, `collectList`, `launchAndWait`, `backUntil`, `backToApp`, `toggle`. AI and other previously identified runtime differences also remain separate audit work.

## Accepted source and artifact identities

| Component | Version / Android build | Source commit |
| --- | --- | --- |
| Host / Runtime Kit | 6.8.0 / 5741 | `643c6de26c87f836fdb4cdd8a9359481ed4678c7` |
| Host instrumentation | instrumentation APK | `e76b457e5773ff08d8b3cd1e8c438206492880c1` |
| Node.js Runtime | 1.28.0 / 282 | `1648991cbeef4e43e262ab2e18981f43038bcc81` |
| Bun Runtime | 0.17.0 / 283 | `4dd3cfeb2418c6810db3c330ec5ece82404b5343` |
| Ace Editor | 2.37.0 / 318 | `ed523116af3a60e3e2efabb66a5bac0b2c47f993` |
| Offline Docs | 6.8.35 / 197 | `82dba3f53271316b8b0224bd52f49b2a5af3e116` |
| APK Builder Template | 1.2.22+autojs6-6.8.0 / 574101 | `753aaae8fba55053ed7800908d19c4873ab1be52` |
| Documentation | revision 156 | `2a499dac3e8b63ce623135db5290b9754832f05f` |
| TypeScript Declarations | 4.75.0 | `ff128fc69117472d510deecad5ff45d9c7dca8b5` |

The Builder repository build is 100; its paired Android code is 574101. The host test commit e76b457e5 only waits for the final await observer to settle before asserting zero pending nodes. The production host and Runtime Kit remain bound to 643c6de26. Later integration/report commits are not new APK acceptance identities.

Runtime Kit:

```text
autojs6-runtime-v6.8.0+5741-643c6de26c87f836fdb4cdd8a9359481ed4678c7-arm64-v8a
```

Preserved directory: `AutoJs6-Plugin-APK-Builder-Template/.bak/runtime-flow-core-20261021/kit-5741`.

Template SHA-256: `acc3f6db632cb0627c5f32fbb333f37ed1f11b2df6be454a9ef8cd964328e0ee`.

Every component APK is frozen under `.bak/runtime-flow-core-20261021/artifacts/`. `artifact-map.json` records source, hash, certificate, version and original build output. Entries ending in before-activity-fix, before-async-fix or before-final-host-types are historical candidates, not the final cohort.

## Local verification

| Check | Accepted result |
| --- | --- |
| Node API tests | 116 passed, plus seven legacy standalone programs |
| Bun SDK tests | 76 passed |
| JVM across host, Node, Bun, Ace, Offline and Builder | 5454 cases: 5428 passed, 26 skipped, zero failures/errors |
| Host portion of the JVM total | 5069 cases, 26 skipped, zero failures/errors |
| Runtime declaration synchronization | 78 files matched |
| TypeScript | 5.1.3 and 6.0.3, strict runtime checks, zero diagnostics |
| Ace runtime queries | 985 passed across 11 runtime-routing cases |
| Flow contract gate | 22 root names, shared factory/types and host routes matched |
| Full augment documentation/type/completion gate | 1356 explicit exports, 1589 queried paths, zero failures |

The augment gate counts documentation/declaration/completion paths; it is not Node/Bun implementation coverage. The final generator also refreshed the host's ActivityWindowIdentity declaration and the already-committed baseline's memory/pref symbols; no unrelated uncommitted source was imported.

Node's C++ passed the exact NDK syntax command from its current compile database. Applicable Debug, instrumentation, JVM, lint and signed release-collection tasks passed for the changed companion packages; host Debug/instrumentation and INRT Release/Runtime Kit builds passed. Signed local collections were not published. Device runtime-plugin tests used the recorded Debug cohort; generated apps used the actual INRT Release template. This batch adds no new ABI or 16 KB execution acceptance.

Python checks include Node generators (2), Bun generators/contracts (38), Ace asset/repository checks (7), Builder scripts (28) and host API-coverage tools (8). JUnit XML and their hashes are archived in `junit/` and `junit-artifacts.json`.

## Physical and packaged validation

| Device | Android API | ABI / page size | Final cases | Packaged launches / results | Original APKs restored |
| --- | --- | --- | --- | --- | --- |
| 22120RN86C / `bek749scrwv4wo8h` | 33 | arm64-v8a / 4096 bytes | 56 | 4 / 6 | 6 |
| 23046RP50C / `968e9f18` | 35 | arm64-v8a / 4096 bytes | 56 | 4 / 6 | 8 |

Each device's final 56 cases comprise host Flow (6), Node npm/conformance (34), Bun Binder/runtime (13), final Ace Flow popup (1), APK production (1) and packaged fresh/restart validation (1). Total: **112 final physical cases, eight packaged launches and twelve program results**. The initial Redmi Ace build 317 full 20-case run remains archived separately and is not added to this final total.

The host programs each execute 25 core assertions in Node main, Bun and Worker, then query a real visible fixture, stable bounds, an absent target, foreground package and Activity. Separate cases cover unavailable services, independent Bun grant rejection through both facade and raw HTTP, CJS/ESM/nested Worker termination, preserved parent defaults and zero pending nodes after observer settlement.

Both independently signed generated applications were built on each device through the real Builder. Bun executes one program per launch; Node executes main and Worker programs. Fresh startup and force-stop/relaunch both pass. The apps temporarily enable their own Android accessibility service and query their own visible UI. Returned package identities and runtime code 5741 match the installed generated app, all runtime DEX files match the preserved template, and application certificates differ from the host certificate. No microphone permission is requested. Eight final Ace screenshots were individually inspected; completion entries and their typed documentation are visible.

## Failures found and corrected

1. The first host 5738 device run failed three Activity waits: subsequent android.widget.FrameLayout window-state events overwrote the legacy latestActivity value. ActivityWindowIdentity now validates declared Activities and retains a same-package Activity through container/dialog events, while a package change cannot borrow the old Activity. Portable queries use this immutable observation; existing legacy getters keep their behavior. The regression and both final device cohorts pass.
2. A negative-control test reproduced a Flow created after an async callback's await remaining pending after its parent was cancelled. The shared facade now carries ownership through async context, including Workers and Bun. Local positive controls and the 25-assertion real-runtime programs verify the correction.
3. A pending counter read immediately after native await included the await continuation itself. The device fixture now yields one timer turn and asserts zero; no production cancellation rule or acceptance threshold was weakened. The host instrumentation APK has its separate exact source identity above.
4. The broader gate exposed two generator portability issues: CRLF source parsing in the view coverage tool and Bun regeneration losing its Android URI adapter/type mapping. The tools now normalize newlines and preserve the existing Bun-specific mapping. Flow's two wire queries were also added to the existing exact method-count assertions without relaxing forbidden-route checks.
5. The first Runtime Kit build had pre-commit source metadata. It was retained only as a rejected metadata record, then regenerated with an explicit exact SHA. No final acceptance uses that identity or host 5738.

## Restoration and workspace preservation

Redmi was completed first. Its six original APKs were restored, including host build 5352, SHA-256 `da00c5b7406572a9f66710b376a890c92a34c41854ac95d19f918f17a9041dba`. Its originally enabled host accessibility service was rebound.

Only after Redmi restoration and fresh idle checks was Xiaomi backed up. Its eight originals were restored, including host build 5679, SHA-256 `0e709c94f91c779534b811aaadba3ce89aeac14f34b9b0b28215fe7dc62b1f2b`. Its host accessibility service was originally disabled and remains disabled; the original AutoJs4/Macaw service list was preserved. No earlier Speech/Mail snapshot was used to overwrite this takeover state.

Both devices passed original APK-hash, primary-user permission/flag, tracked settings/volume/app-op, Bun-grant and plugin-selection checks. Owned external files and generated apps were removed; original Ace evidence was restored. Backup APKs and recovery receipts remain in the ignored evidence directory. Do not rerun the old collection/restoration helpers on the restored installations.

The source branch was integrated with a two-parent merge `f27cced61900753e08a4cde0a34bdfc2e97c126e` using separate index and working-draft merges. The pre-existing Chinese changelog draft and four memory reports were preserved. The seven companion workspaces are clean.

Temporary `aj6-flow` was removed. Its copied signing/local inputs were removed, 13 newly created Worker test directories were archived under the Node repository's `.bak/runtime-flow-core-20261021/worker-test-archives`, and the pre-existing worker-host-tests directory was retained. Only the proven owned, idle Gradle daemon(s) [37452] were stopped; no global ADB/Gradle shutdown was used.

## Evidence and next audit

Evidence root: `.bak/runtime-flow-core-20261021/`. Main entries: `final-summary.json`, `artifact-map.json`, `runtime-kit.json`, `flow-scope.json`, `all-jvm-totals.json`, `visual-evidence.json`, each device's `accepted-evidence.json`/`finalization.json`, `integration.json` and `cleanup.json`.

Continue with the 30 remaining Flow toolkit/event root names and their chain counterparts, or the dedicated AI surface. Keep synchronous Rhino globals, selector/prototype behavior and other remaining differences explicit. Refer back to the [overall audit](runtime-api-coverage-2026-10-17.md).
