# P5.3 performance and footprint evidence

Date: 2026-10-03. Measured implementation: host commit
`472f52e39b151be10920fe774958e4aed9d8b808`, AutoJs6 6.8.0 / 5317 with the isolated debug host,
and minified Compose UI release 1.0.0 / 19. Both accepted runs passed exact sample-set,
uniqueness, process-identity and PSS-pairing validation: 118 records per device, 236 total.
The compact [performance summary](p5-performance-summary.json) records the main values and method.
The maintainer approved the first review rules on 2026-10-03. This remains a local
preview with a manual review requirement; the measurements do not imply public release.

## Scope and endpoints

The representatives are an Android 7.0 API24 x86 AVD (32-bit, observed 60 Hz) and a Xiaomi Pad
23046RP50C running API35 / HyperOS OS2.0.9.0.VMYCNXM (arm64, observed 144 Hz). These measurements
do not replace the separate six-device functional matrix or a release-host benchmark.

- Cold: 3 fresh host processes, one first Compose mount each. APK, page, filesystem and dex caches were preserved.
- Warm: 2 excluded same-process warmups, then 10 new-engine mounts or 10 updates per operation and surface.
- Lists: 10 traces of 12 real touch swipes, nominally 360 ms each, with 120 ms pauses and a 750 ms fling tail.
- PSS: 9 stages in one separate fresh process, 5 readings per stage after 500 ms settling, 100 ms apart; no forced GC or screenshots.

The primary clock starts immediately before `compose.mount`, or before the first state assignment
in the actual button callback. It ends after visible accessibility text matches, two public
Choreographer callbacks pass, and a screenshot pixel confirms the corresponding 32 dp swatch.
Each mount has a distinct palette; both +1 and +100 state changes change the color. Screenshots
are inspected in memory and never saved.

This is an **instrumented visibility confirmation upper bound**, not a hardware presentation
timestamp or pure Compose rendering latency. It includes 8 ms polling sleeps, accessible-tree
queries, two display callbacks, screenshot capture and any window transitions. Concurrent costs
cannot be simply subtracted to infer pure renderer time. `mountCallNs` and the earlier semantics
endpoint are retained separately. Device thermal/governor/animation policy was not controlled;
the distinct-device sweeps overlap in wall time, and AVD results describe this local environment.

## Timing results

Cells are median / p90 in ms. Percentiles use linear interpolation at `(n - 1) * 0.9`.
The 3 cold observations are descriptive only and do not establish a stable tail or gate.

| Operation, mount/state write to pixel confirmation | Samples per device | API24 AVD | API35 Pad |
| --- | --- | --- | --- |
| Cold UI mount | 3 | 1606.83 / 1674.91 | 485.89 / 489.83 |
| Warm UI mount | 10 | 491.62 / 498.91 | 235.27 / 286.96 |
| UI single state update | 10 | 75.96 / 86.25 | 112.19 / 124.45 |
| Floaty single state update | 10 | 72.49 / 82.66 | 77.03 / 137.14 |
| UI 100 assignments in one callback | 10 | 72.84 / 75.89 | 94.69 / 105.11 |
| Floaty 100 assignments in one callback | 10 | 72.30 / 81.41 | 151.60 / 157.24 |
| Warm 1000-item LazyColumn mount | 10 | 1061.52 / 1392.49 | 876.86 / 936.81 |

For context, the earlier observed-text endpoint for UI updates was 33.61 / 41.01 ms on API24
and 49.64 / 59.01 ms on API35. Script-launch-to-confirmation is wider than mounting: cold
3614.99 / 3633.74 ms and warm 635.13 / 654.11 ms on API24, versus cold 1604.01 / 1609.65 ms
and warm 473.79 / 594.57 ms on API35. This wider boundary includes the public execution route
and Rhino startup. The JSON preserves observed-text results for every timing group.

Observation overhead medians are material, as the following selected groups illustrate:

| API | Operation | Accumulated tree-query ms | Two display callbacks ms | Accumulated screenshot ms |
| --- | --- | --- | --- | --- |
| 24 | UI update | 41.33 | 21.49 | 15.76 |
| 24 | Floaty update | 27.56 | 24.82 | 18.44 |
| 24 | 1000-item mount | 536.66 | 22.39 | 91.21 |
| 35 | UI update | 63.59 | 12.31 | 37.51 |
| 35 | Floaty update | 26.80 | 10.95 | 31.41 |
| 35 | 1000-item mount | 190.94 | 10.03 | 72.64 |

All 40 single-update and 40 batch samples across both devices produced exactly one script render
by confirmation. The batch is 100 assignments in one native callback under existing callback
batching, not 100 scheduler turns. The assignment-loop median / p90 was UI 0.38 / 2.17 ms and
floaty 0.18 / 0.21 ms on API24, versus UI 1.61 / 3.17 ms and floaty 1.49 / 1.79 ms on API35.

## List frame timing

No accessibility polling or screenshots occur inside the scroll trace. Its callback updates a
test atomic, not Compose state. Statistics below pool recorded frames across all ten traces.
[FrameMetrics TOTAL_DURATION](https://developer.android.com/reference/android/view/FrameMetrics)
measures rendering and issuing to the display subsystem; the callback's
[drop count](https://developer.android.com/reference/android/view/Window.OnFrameMetricsAvailableListener)
counts lost metric reports.

| Observation | API24 AVD | API35 Pad |
| --- | --- | --- |
| Measured non-first-draw frames | 3586 | 7198 |
| Render-and-issue median / p90 | 14.29 / 35.98 ms | 4.49 / 6.16 ms |
| Longer than initial display period | 1761 / 3586, 49.11% | 88 / 7198, 1.22% |
| Estimated excess display periods, summed | 2396 | 90 |
| System-deadline misses | Unavailable | 3 / 7198, 0.0417% |
| Lost reports / collection overflow | 0 / 0 | 0 / 0 |
| Actual presentation drops | Not measured | Not measured |

Period excess estimates rendering jank and must not be labeled measured presentation drops.
API35's system deadline is the primary budget observation; refresh rate is only a snapshot at
trace start. API24 per-trace period-excess ratios ranged from
0.00% to 91.57%, so this AVD series does not establish a stable jank gate.

## PSS and artifact size

[Debug.MemoryInfo](https://developer.android.com/reference/android/os/Debug.MemoryInfo) reports
whole-process PSS. Values below are median MiB (delta from the same process's empty native UI
engine). Stage medians are paired by run and PID before aggregation; missing baselines are rejected.
The committed summary omits device serials and process/run identifiers; ignored originals retain them for pairing audits.

| Stage | API24 MiB (delta) | API35 MiB (delta) |
| --- | --- | --- |
| empty_native_ui_engine | 117.39 (+0.00) | 299.33 (+0.00) |
| first_compose_ui | 131.26 (+13.87) | 384.62 (+85.29) |
| warm_compose_floaty | 133.82 (+16.43) | 314.69 (+15.35) |
| warm_compose_1000_item_list | 160.64 (+43.25) | 351.33 (+51.99) |
| after_list_close | 155.07 (+37.68) | 270.06 (-29.28) |

These totals include engine allocations, retained classloader/JIT/native state and GC/runtime
noise. API35's negative post-list delta illustrates why one process sequence is not a retained-leak
measurement. Five nearby readings are not five independent process samples. All nine stages are
preserved in the compact summary, without a strict PSS threshold.

Measured plugin file: `autojs6-plugin-compose-ui-v1.0.0-5b2f63ce.apk`.
APK size: **2375069 B (2.265 MiB)**.
One `classes.dex`: 6167508 uncompressed bytes,
**7143 class definitions / 44721 method references**.
The latter counts DEX `method_ids`, not unique method definitions.
SHA-256: `317316ff9ab160d4f235e667a219aac6496f9e431bc6b5a80c2977a250e62b4f`; CRC32: `5b2f63ce`.

## Snapshot comparison and optimization decision

Initial construction of 1000 script nodes is a material cost: render-callback construction
median / p90 was **641.83 / 801.10 ms on API24** and **565.80 / 599.04 ms on API35**.
This interval includes Rhino calls and node factory/validation work; it is not a CPU-profile
attribution of every mount phase.

The roadmap's first proposed optimization is already present. Host `TreeReconciler.diff`
compares property, modifier and callback snapshots and emits `SetProps` only when they differ.
`ComposeSession.flush` accepts an empty patch list without calling the renderer.
`ComposeNodeGraph.reconcileImpl` retains keyed handles and unchanged callback identities.
These checks suppress unchanged native operations while still traversing submitted trees;
fresh callback objects remain real changes.

An initial 1000-node mount has no previous snapshot, so unchanged-property suppression cannot
avoid first construction or initial `SetRoot`. The measured scrolling does not rerun the JS
render, and small-tree batches coalesced to one render. This campaign does not establish that
repeated unchanged large-tree rebuilds dominate updates, and does not justify an F.6 API change
or speculative production optimization. No production performance optimization was added.

## Q5 first review rules, approved 2026-10-03

The maintainer accepted these values as the first review rules. Future p90 review
budgets use current p90 * 1.5 rounded upward to 5 ms, only under the same device,
workload, build mode, warmups, sample count and observation method. Record both
the host and plugin versions/commits when comparing changes. These values trigger
review; they are not general Android latency promises or automatic release approval.

| Operation | API24 review ms | API35 review ms |
| --- | --- | --- |
| Warm UI mount | 750 | 435 |
| UI single state update | 130 | 190 |
| Floaty single state update | 125 | 210 |
| UI 100 assignments in one callback | 115 | 160 |
| Floaty 100 assignments in one callback | 125 | 240 |
| Warm 1000-item LazyColumn mount | 2090 | 1410 |

Artifact review budgets are **2,612,576 APK bytes** and **49,194 DEX method references**,
the ceiling of this baseline plus 10%. Dependency changes require explicit baseline review.
When a result exceeds any review budget, repeat the measurement or provide an
auditable explanation and evidence, then obtain the maintainer's decision on
acceptance. Preserve the previous baseline and the exceedance until that decision;
do not silently widen limits or select only favorable reruns. Apply the same review
to reasonable feature/dependency growth. The Compose UI plugin AGENTS.md section14 records this requirement.

Cold timing, PSS and all scroll-frame metrics remain observation-only until more
stability evidence supports a limit. Batch coalescing remains a functional observation
of one render for one callback's 100 assignments. The collector's automatically
calculated candidate values do not replace these approved rules or the required
maintainer decision for a breach.

## Provenance and reproduction

Only the final sweeps are accepted. The earlier API35 trial was discarded: a fixed initial
swatch could match an old Activity snapshot, and `stop` followed by `close` removed its frame
listener twice, raising an Android exception. The accepted sweeps reran after per-mount palettes
and idempotent listener removal. Trial data is not mixed into the baseline.

| Raw record set, kept in ignored build artifacts | Records | SHA-256 |
| --- | --- | --- |
| api24_x86_avd | 118 | `49a929b928c0271f269858b4c23efb98a0ca42a95a4f863830d03f6931d7682a` |
| api35_xiaomi_pad | 118 | `fb6413d32b2beeffdbaae6cbcb497f7a58bcc1915e3442f554194f80ef0f09de` |

The committed JSON contains device models, APIs, ABIs, source hashes and the main numerical
summary, without device serials, run/PIDs, local absolute paths or per-frame logs. The originals
remain under the host's ignored `build/compose-performance/p5-final-api24/` and
`p5-final-api35/` directories. A later final host APK with an unrelated inrt registration fix is
not retroactively labeled measured: these records stay scoped to commit `472f52e39b`.
A change to the Compose timing path would require a new measurement.

From the host checkout, install the isolated host/test APKs and matching release plugin,
unlock the chosen device and grant the isolated host overlay permission:

```powershell
py -B -X utf8 .python/compose_performance.py run --serial <serial> --output-dir build/compose-performance/<run-directory> --cold-samples 3 --samples 10 --warmups 2 --scroll-swipes 12
py -B -X utf8 .python/compose_performance.py summarize build/compose-performance/p5-final-api24/*-records.json build/compose-performance/p5-final-api35/*-records.json --output build/compose-performance/p5-summary.json --plugin-apk <measured-plugin-release.apk>
py -B -X utf8 -m unittest discover -s .python -p test_compose_performance.py -v
```

The 16 analyzer tests cover incomplete/duplicate sampling, process pairing, timing endpoints,
metric-report loss versus rendering estimates, negative PSS deltas and multidex counts.
The collector requires opt-in, restores plugin enablement and UiAutomation flags, and closes only
its owned engines. Records contain synthetic benchmark values, versions and measurements, not
user script text or saved screenshots.
